/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package statechum.analysis.learning;

import java.util.*;
import java.io.*;

import statechum.apps.ErlangQSMOracle;
import statechum.apps.QSMTool;

import java.awt.Frame;

import statechum.analysis.learning.experiments.ExperimentRunner;
import statechum.analysis.learning.experiments.ExperimentRunner.HandleProcessIO;
import statechum.analysis.learning.observers.ProgressDecorator.LearnerEvaluationConfiguration;
import statechum.Pair;
import statechum.Trace;
import statechum.analysis.learning.rpnicore.LTL_to_ba;
import statechum.analysis.learning.rpnicore.LearnerGraph;

/**
 *
 * @author ramsay
 */
public class ErlangOracleLearner extends RPNIUniversalLearner {

    public ErlangOracleLearner(Frame parent, LearnerEvaluationConfiguration evalCnf) {
        super(parent, evalCnf);
    }

    @Override
    public LearnerGraph learnMachine() {
        try {
            erlangProcess = Runtime.getRuntime().exec(new String[]{"erl"}, null, new File(ErlangQSMOracle.ErlangFolder));
            int response = erlangProcess.getInputStream().read();
            while (response != '>' && response != -1) {
                System.out.print((char) response);
                response = erlangProcess.getInputStream().read();
            }

        } catch (IOException e) {
            killErlang();
            return null;
        }

        LearnerGraph result = super.learnMachine();
        killErlang();
        return result;
    }
    Process erlangProcess = null;

    protected void killErlang() {
        if (erlangProcess != null) {
            try {
                erlangProcess.getOutputStream().write("halt().\n".getBytes());
                erlangProcess.getOutputStream().flush();
            } catch (IOException e1) {
                statechum.Helper.throwUnchecked("failed to communicate with Erlang process", e1);
            }
            ExperimentRunner.dumpStreams(erlangProcess, LTL_to_ba.timeBetweenHearbeats, new HandleProcessIO() {

                @Override
                public void OnHeartBeat() {// no prodding is done for a short-running converter.
                }

                @Override
                public void StdErr(@SuppressWarnings("unused") StringBuffer b) {
                    //System.err.print(b.toString());
                }

                @Override
                public void StdOut(@SuppressWarnings("unused") StringBuffer b) {
                    //System.err.print(b.toString());
                }
            });
            try {
                erlangProcess.waitFor();
            } catch (InterruptedException e) {
                statechum.Helper.throwUnchecked("wait for Erlang to terminate aborted", e);
            }
            erlangProcess = null;
        }
    }

    @Override
    public Pair<Integer, String> CheckWithEndUser(@SuppressWarnings("unused") LearnerGraph model, final List<String> question, final int expectedForNoRestart,
            final List<Boolean> consistentFacts, final Object[] moreOptions) {

        Iterator<String> it = question.iterator();
        //System.out.println("Question for " + erlangModule + ":" + erlangWrapperModule + " is:");
        String erlList = "[";
        while (it.hasNext()) {
            if (!erlList.equals("[")) {
                erlList += ",";
            }
            erlList += it.next();
        }
        erlList += "]";
        int failure = AbstractOracle.USER_CANCELLED;
        try {
            // Lets see if QSM is being silly and we already know the answer...
            failure = firstFailure(ErlangQSMOracle.ErlangFolder + "/" + ErlangQSMOracle.tracesFile, new Trace(question));
            if (failure != AbstractOracle.USER_ACCEPTED) {
                // We didn't find the answer in the existing traces file so lets extend it
                // OR we did find a negative answer but it might be based on a wildcard for the output, so lets try again anyway!
                String erlArgs = "tracer2:first_failure(" + ErlangQSMOracle.erlangWrapperModule + "," + ErlangQSMOracle.erlangModule + "," + erlList + ",\"" + ErlangQSMOracle.tracesFile + "\"," + ErlangOracleVisualiser.toErlangList(ErlangQSMOracle.erlangModules) + ")";
                System.out.println("Evaluating " + erlArgs + " in folder " + ErlangQSMOracle.ErlangFolder);
                erlangProcess.getOutputStream().write(erlArgs.getBytes());
                erlangProcess.getOutputStream().write('.');
                erlangProcess.getOutputStream().write('\n');
                erlangProcess.getOutputStream().flush();

                // now wait for a response.
                int response = erlangProcess.getInputStream().read();
                boolean finished = false;
                while (response != -1 && !finished) {
                    System.out.print((char) response);
                    response = erlangProcess.getInputStream().read();
                    if (response == '>') {
                        // If we get a promt lets see if it just sits there for a while...
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            ;
                        }
                        // We often get a space afterwards?
                        if (erlangProcess.getInputStream().available() >= 1) {
                            response = erlangProcess.getInputStream().read();
                        }
                        //System.out.println("Got prompt and '" + ((char) response) + "'");
                        if ((response == ' ') && (erlangProcess.getInputStream().available() <= 0)) {
                            finished = true;
                        }
                    }
                }

                if (response == -1) {
                    throw new IllegalArgumentException("end of input reached when reading Erlang output");
                }

                //ErlangQSMOracle.loadCoverageMaps(ErlangQSMOracle.ErlangFolder + "/" + outFile + ".covermap");
                ErlangQSMOracle.loadCoverageMaps();
                //(new File(ErlangQSMOracle.ErlangFolder + "/" + outFile + ".covermap")).delete();

                failure = firstFailure(ErlangQSMOracle.ErlangFolder + "/" + ErlangQSMOracle.tracesFile, new Trace(question));
                // We really should have found the answer now...
                if (failure == AbstractOracle.USER_TRACENOTFOUND) {
                    throw new RuntimeException("Errrr, answer not found even though we asked Erlang (" + question + ")...");
                }
            }
        } catch (IOException err) {
            statechum.Helper.throwUnchecked("failed to run Erlang", err);
        }
        System.out.println("<Erlang> " + question + " " + failure);
        return new Pair<Integer, String>(failure, null);
    }

    /** Evaluates the supplied command in Erlang environment.
     * @param  
     */
    public static void runErlang(String ErlangCommand) throws IOException, InterruptedException {
        Process erlangProcess = Runtime.getRuntime().exec(new String[]{"erl", "-eval", ErlangCommand + ",halt()."}, null, new File(ErlangQSMOracle.ErlangFolder));
        ExperimentRunner.dumpStreams(erlangProcess, LTL_to_ba.timeBetweenHearbeats, new HandleProcessIO() {

            @Override
            public void OnHeartBeat() {// no prodding is done for a short-running converter.
            }

            @Override
            public void StdErr(@SuppressWarnings("unused") StringBuffer b) {
                System.err.print(b.toString());
            }

            @Override
            public void StdOut(@SuppressWarnings("unused") StringBuffer b) {
                System.out.print(b.toString());
            }
        });
        erlangProcess.waitFor();
    }

    /** Returns -1 if the string is shown as accepted, returns -2 if it is not found, and returns the point at which it is rejected otherwise */
    protected int firstFailure(String file, Trace erlTrace) throws IOException {
        System.out.println("Seeking first failure for " + erlTrace);
        BufferedReader input = new BufferedReader(new FileReader(file));

        String line;
        int count = AbstractOracle.USER_TRACENOTFOUND;
        // We may find a short negative trace with a wildcard but we should look for longer positive traces
        // that may use a different instantiation
        int negativecount = AbstractOracle.USER_TRACENOTFOUND;
        while ((line = input.readLine()) != null && count == AbstractOracle.USER_TRACENOTFOUND) {
            String traceString = line.substring(1).trim();
            Trace traceFromFile;
            if (traceString.equals("")) {
                traceFromFile = new Trace();
            } else {
                traceFromFile = new Trace(QSMTool.tokeniseInput(traceString));
            }
            if (line.substring(0, 1).equals("-")) {
                if (traceFromFile.size() <= erlTrace.size()
                        && traceFromFile.isPrefix(erlTrace)) {
                    // We have to be careful not to pick a negative trace if there is a longer positive trace with a different instantiation of wildcards...
                    //System.out.println("                        - " + traceFromFile);
                    negativecount = traceFromFile.size() - 1;
                    //break;
                }
            } else {
                assert line.substring(0, 1).equals("+");

                if (traceFromFile.size() >= erlTrace.size() && traceFromFile.isPrefix(erlTrace)) {
                    //System.out.println("                        + " + traceFromFile);

                    // This is an accept line for our string.
                    count = AbstractOracle.USER_ACCEPTED;
                    break;
                }
            }
        }
        input.close();
        if (count != AbstractOracle.USER_TRACENOTFOUND) {
            // If we have a positive trace lets use that
            return count;
        } else {
            return negativecount;
        }
    }
}
