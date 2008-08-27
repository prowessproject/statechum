/** Copyright (c) 2006, 2007, 2008 Neil Walkinshaw and Kirill Bogdanov

This file is part of StateChum.

statechum is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

StateChum is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with StateChum.  If not, see <http://www.gnu.org/licenses/>.
*/
package statechum.analysis.learning.experiments;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.swing.Action;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import statechum.analysis.learning.PairScore;
import statechum.analysis.learning.StatePair;
import statechum.analysis.learning.Visualiser.graphAction;
import statechum.analysis.learning.observers.Learner;
import statechum.analysis.learning.observers.LearnerSimulator;
import statechum.analysis.learning.observers.ProgressDecorator;
import statechum.analysis.learning.observers.RecordProgressDecorator;
import statechum.analysis.learning.observers.ProgressDecorator.ELEM_KINDS;
import statechum.analysis.learning.observers.ProgressDecorator.LearnerEvaluationConfiguration;
import statechum.analysis.learning.rpnicore.LearnerGraph;

/** Goes through learner log files and compresses them using GD.
 * 
 * @author kirill
 */
public class Test_LearnerLogCompress {

	public void updateProgressBar(final boolean indeterminate, final int bar, final String value)
	{
		SwingUtilities.invokeLater(new Runnable() { public void run() {
			progressBar.setIndeterminate(indeterminate);
			progressBar.setString(value);
			if (bar>=0) progressBar.setValue(bar);
		}});
	}
	
	/** Used to unroll a deep stack. */
	protected static class ComputationAbortedException extends IllegalArgumentException
	{

		/**
		 * Used for serialisation.
		 */
		private static final long serialVersionUID = -772593930068054651L;
		
	}
	
	/** Counts the number of graphs - it is this number which determines the time 
	 * taken to perform the compression,
	 * both on the GD and ZIP parts.
	 */
	protected int graphNumber = 0;
	
	/** Display progress every so many graphs read, during counting. */
	final int modValueGraphCounter = 10;
	
	/** Computes the number of "MERGEANDDETERMINIZE" entries in the ZIP file.
	 * 
	 *  @param fileName name of the file to process
	 *  @throws ComputationAbortedException if a user decided to abort computation.
	 * */
	protected int computeGraphNumber(File fileName) throws IOException
	{
		ZipInputStream inputZip = new ZipInputStream(new java.io.BufferedInputStream(new java.io.FileInputStream(fileName)));
		ZipEntry entry = inputZip.getNextEntry();
		while(entry != null)
		{
			if (entry.getName().contains(ELEM_KINDS.ELEM_MERGEANDDETERMINIZE.name()))
			{
				++graphNumber;if ((graphNumber % modValueGraphCounter) == 0) updateProgressBar(true, -1, "found: "+graphNumber+" graphs");
				if (computationAborted) throw new ComputationAbortedException();
			}
			entry = inputZip.getNextEntry();
		}
		return graphNumber;
	}
	
	/** This one prints dots for every 0.1%. */
	protected class DummyWithProgressIndicator extends DummyLearner
	{
		private int graphsSoFar = 0, totalGraphs = 0;
		private long prevTime = new Date().getTime();
		
		/* (non-Javadoc)
		 * @see statechum.analysis.learning.experiments.Test_LearnerLogCompress.DummyLearner#MergeAndDeterminize(statechum.analysis.learning.rpnicore.LearnerGraph, statechum.analysis.learning.StatePair)
		 */
		@Override
		public LearnerGraph MergeAndDeterminize(LearnerGraph original,	StatePair pair) {
			++graphsSoFar;
			int percent = -1;
			if ( totalGraphs > 0 ) 
			{
				percent = (int)(1000.*graphsSoFar/totalGraphs);
				long currentTime = new Date().getTime();
				long left = totalGraphs*(currentTime-prevTime)/(graphsSoFar*1000);
				long hrs = left/3600;
				long mins = (left%3600)/60;
				if (percent >=0 && (percent % 1) == 0) updateProgressBar(false, percent/10, "compressing graph "+graphsSoFar+" out of "+totalGraphs+
						" remaining "+hrs+":"+RecordProgressDecorator.intToString((int)mins,2)+"hr");
			}
			return decoratedLearner.MergeAndDeterminize(original, pair);
		}

		/** Creates a new instance of this progress-computing observer.
		 * 
		 * @param learner an instance of a simulator
		 * @param totalG the total number of graphs computed by the dummy above.
		 */
		public DummyWithProgressIndicator(Learner learner, int totalG) 
		{
			super(learner);totalGraphs=totalG;
		}
		
		public Stack<PairScore> ChooseStatePairs(LearnerGraph graph) {
			if (computationAborted) throw new ComputationAbortedException();

			return decoratedLearner.ChooseStatePairs(graph);
		}
	}

	/** Contains a total number of graphs handled by all compressors on different threads. */
	protected AtomicInteger graphsProcessed = new AtomicInteger();
	
	/** This one only updates the global progress counter, expecting a separate thread to show
	 * the results. Very useful for multiple compressors working in parallel.
	 */
	protected class DummyWithDelayedProgressIndicator extends DummyLearner
	{
		/* (non-Javadoc)
		 * @see statechum.analysis.learning.experiments.Test_LearnerLogCompress.DummyLearner#MergeAndDeterminize(statechum.analysis.learning.rpnicore.LearnerGraph, statechum.analysis.learning.StatePair)
		 */
		@Override
		public LearnerGraph MergeAndDeterminize(LearnerGraph original,	StatePair pair) {
			graphsProcessed.addAndGet(1);
			return decoratedLearner.MergeAndDeterminize(original, pair);
		}

		/** Creates a new instance of this progress-computing observer.
		 * 
		 * @param learner an instance of a simulator
		 */
		public DummyWithDelayedProgressIndicator(Learner learner) 
		{
			super(learner);
		}
		
		public Stack<PairScore> ChooseStatePairs(LearnerGraph graph) {
			if (computationAborted) throw new ComputationAbortedException();// if a user hits ESC, all running threads will abort.

			return decoratedLearner.ChooseStatePairs(graph);
		}
	}

	/** Whether a user has aborted computation. */
	protected boolean computationAborted = false;
	
	/** Progress indicator. */
	protected JProgressBar progressBar = null;

	protected class FileCompressor implements Callable<String>
	{
		final File sourceFile;
		final int graphsInFile;
		
		public FileCompressor(File src, int number)
		{
			sourceFile = src;graphsInFile = number;
		}
		public String call() throws Exception 
		{
			final String targetFile = sourceFile.getAbsolutePath()+".gd";
			/*SwingUtilities.invokeLater(new Runnable() { public void run() {
				fromLabel.setText("<html><font color=green>From: "+file.toString());
				toLabel.setText("<html><font color=green>To: "+targetFile);
				frame.pack();
			}*/
			String outcome = null;
			if (!computationAborted)
				try
				{
					LearnerSimulator simulator = new LearnerSimulator(new java.io.FileInputStream(sourceFile),true);
					LearnerEvaluationConfiguration eval1 = simulator.readLearnerConstructionData();
					eval1.config.setGdFailOnDuplicateNames(false);eval1.graphNumber=graphsInFile;
					final org.w3c.dom.Element nextElement = simulator.expectNextElement(ELEM_KINDS.ELEM_INIT.name());
					final ProgressDecorator.InitialData initial = simulator.readInitialData(nextElement);
					simulator.setNextElement(nextElement);
					RecordProgressDecorator recorder = new RecordProgressDecorator(simulator,new java.io.FileOutputStream(
						targetFile),threadNumber,eval1.config,true);
					recorder.writeLearnerEvaluationData(eval1);
					DummyWithDelayedProgressIndicator progress = new DummyWithDelayedProgressIndicator(recorder);
					progress.learnMachine(initial.plus,initial.minus);// load the file provided and compress its contents into the other one.
					outcome = "compressed "+sourceFile+" to "+targetFile;
				}
				catch(ComputationAbortedException e)
				{
					computationAborted = true;
					new File(targetFile).delete();// remove result upon abort.
					outcome = "USER ABORT on "+sourceFile;
				}
				catch(Exception e)
				{
					new File(targetFile).delete();// remove result upon failure.
					outcome = "failure compressing "+sourceFile;
				}
		
			return outcome;
		}
	}

	protected int totalGraphNumber = 0;

	final static int threadNumber = ExperimentRunner.getCpuNumber();
	
	public static void main(final String [] args)
	{
		final String dir = args[0];
		final Test_LearnerLogCompress compressor = new Test_LearnerLogCompress();
		final JFrame frame = new JFrame("Log compressing program");
		compressor.progressBar = new JProgressBar(0, 100);
		compressor.progressBar.setValue(0);
		compressor.progressBar.setStringPainted(true);
		Image icon = Toolkit.getDefaultToolkit().getImage("resources"+File.separator+"icon.jpg");if (icon != null) frame.setIconImage(icon);
		frame.setLayout(new java.awt.GridLayout(2,0));

		final List<File> filesToProcess = new LinkedList<File>();
		
		for(final File file:new File(dir).listFiles(new FileFilter() {
			public boolean accept(File pathname) {
				return pathname.isFile() && pathname.canRead() &&
					pathname.getAbsolutePath().endsWith(".xml");
			}}))
				filesToProcess.add(file);
		
		final JLabel descrLabel = new JLabel("<html><font color=green>Processing "+filesToProcess.size()+" files from "+dir);
		frame.getContentPane().add(descrLabel);
		frame.getContentPane().add(compressor.progressBar);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		
		/** Key bindings. */
		final Map<Integer,Action> keyToActionMap = new TreeMap<Integer, Action>();

		final Thread workerThread = new Thread(new Runnable() { public void run() {
			try {
				/** The runner of computational threads. */
				final ExecutorService executorService = Executors.newFixedThreadPool(threadNumber);

				/** Stores results of execution of evaluators. */
				final List<Future<String>> results = new LinkedList<Future<String>>();;
				
				/** Stores tasks to complete. */
				final CompletionService<String> runner = new ExecutorCompletionService<String>(executorService);
				
				// First, we compute what we need to process 
				final Map<File,Integer> graphSizes = new TreeMap<File,Integer>();
				for(File file:filesToProcess)
					graphSizes.put(file, compressor.computeGraphNumber(file));

				SwingUtilities.invokeLater(new Runnable() { public void run() {
					descrLabel.setText("<html><font color=green>Processing "+compressor.graphNumber+" graphs from "+dir);
					frame.pack();
				}});
				
				Thread updaterThread = new Thread(new Runnable() { 
					private final long prevTime = new Date().getTime();
					
					public void run() {
					try
					{
						while(!compressor.computationAborted)
						{
							synchronized (this) {
								wait(3000);
							}
							int graphsSoFar = compressor.graphsProcessed.get();
							if (compressor.graphNumber > 0 && graphsSoFar > 0)
							{
								int percent = (int)(1000.*graphsSoFar/compressor.graphNumber);
								long currentTime = new Date().getTime();
								long left = compressor.graphNumber*(currentTime-prevTime)/(graphsSoFar*1000);
								long hrs = left/3600;
								long mins = (left%3600)/60;
								if (percent >=0 && (percent % 1) == 0) compressor.updateProgressBar(false, percent/10, "compressing graph "+graphsSoFar+" out of "+compressor.graphNumber+
										" remaining "+hrs+":"+RecordProgressDecorator.intToString((int)mins,2)+"hr");
							}
						}
					}
					catch(InterruptedException e)
					{// assume we've been asked to terminate and do nothing.
						
					}
				}});updaterThread.start();
				
				// Now, we populate the collection of worker threads.
				for(Entry<File,Integer> entry:graphSizes.entrySet())
					results.add(runner.submit(compressor.new FileCompressor(entry.getKey(),entry.getValue())));

				// now wait for results.
				for(Future<String> computationOutcome:results)
					try {
						String result = computationOutcome.get();
						if (result != null) System.out.println("RESULT: "+result);
					} catch (Exception e) { 
						//System.out.println("FAILED");
						//e.printStackTrace();
					}
					finally
					{
						updaterThread.interrupt();
						executorService.shutdown();
					}
			} 
			catch(ComputationAbortedException ex)
			{
				System.out.println("Aborted");
			}
			catch (Exception e) {
				e.printStackTrace();
			}
			finally
			{
				frame.setVisible(false);frame.dispose();
			}
		}},"worker");

		keyToActionMap.put(KeyEvent.VK_ESCAPE, new graphAction("terminate", "terminates this program") {
			/**
			 * ID for serialisation.
			 */
			private static final long serialVersionUID = -2461712095106080046L;

			/** Serial number. */

			public void actionPerformed(@SuppressWarnings("unused")	ActionEvent e) 
			{
				frame.setVisible(false);frame.dispose();compressor.computationAborted = true;
			}
		});
		frame.addKeyListener(new KeyListener() {

			public void keyPressed(KeyEvent arg0) {
				Action act = keyToActionMap.get(arg0.getKeyCode());
				if (act != null)
					act.actionPerformed(null);
			}

			public void keyReleased(@SuppressWarnings("unused") KeyEvent arg0) 
			{
			}

			public void keyTyped(@SuppressWarnings("unused") KeyEvent key) 
			{
			}
			
		});
		frame.setVisible(true);
		
		workerThread.start();
	}
}
