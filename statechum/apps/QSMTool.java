/*Copyright (c) 2006, 2007, 2008 Neil Walkinshaw and Kirill Bogdanov
 
This file is part of StateChum

StateChum is free software: you can redistribute it and/or modify
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

package statechum.apps;

/**
 * Takes a text file, structured as follows:
 * 
 * first line: either "active" or "passive" followed by \n
 * following lines:
 * strings that belong to the target machine:
 * + function1, function2...
 * + function1, function3...
 * and optionally strings that do NOT belong to the target machine:
 * -function1, function4
 * @author nw
 *
 */

import java.io.*;
import java.util.*;

import statechum.Configuration;
import statechum.analysis.learning.PickNegativesVisualiser;
import statechum.analysis.learning.RPNIUniversalLearner;
import statechum.analysis.learning.Visualiser;
import statechum.analysis.learning.observers.Learner;
import statechum.analysis.learning.rpnicore.LTL_to_ba;
import statechum.analysis.learning.rpnicore.LearnerGraph;

public class QSMTool 
{
	protected int k = -1;
	
	/** Learner configuration to be set. */
	protected Configuration config = Configuration.getDefaultConfiguration().copy();
	protected Set<List<String>> sPlus = new HashSet<List<String>>();
	protected Set<List<String>> sMinus = new HashSet<List<String>>();
	protected Set<String> ltl = null;
	protected boolean active = true;
	protected boolean showLTL = false;
	
	public static void main(String[] args) 
	{
		QSMTool tool = new QSMTool();tool.loadConfig(args[0]);
		if (tool.showLTL)
		{
			Learner l = new RPNIUniversalLearner(null,tool.ltl,tool.config);
			LTL_to_ba ba = new LTL_to_ba(tool.config);ba.ltlToBA(tool.ltl, l.init(tool.sPlus, tool.sMinus));
			Visualiser.updateFrame(ba.augmentGraph(new LearnerGraph(tool.config)), null);
		}
		else
			tool.runExperiment();
	}
	
	public void loadConfig(String inputFileName)
	{
		try {
			loadConfig(new FileReader(inputFileName));
		} catch (FileNotFoundException e) {
			statechum.Helper.throwUnchecked("could not open a file with initial data", e);
		}
	}
	
	public void loadConfig(Reader inputData)
	{
		String AutoName = System.getProperty(statechum.GlobalConfiguration.ENV_PROPERTIES.VIZ_AUTOFILENAME.name());
		if (AutoName != null) config.setAutoAnswerFileName(AutoName);
	
		BufferedReader in = null;
		try {
			in = new BufferedReader(inputData);
			String fileString;
			while ((fileString = in.readLine()) != null) {
				process(fileString);
			}
		
		} catch (IOException e) {
			statechum.Helper.throwUnchecked("failed to read learner initial data", e);
		}
		finally
		{
			if (in != null)
				try { in.close(); } catch (IOException e) 
				{// ignored.
				}
		}
	}
	
	public void runExperiment()
	{
		setSimpleConfiguration(config, active, k);
		if(ltl!=null){
			if(!ltl.isEmpty())
				config.setUseSpin(true);
		}
		PickNegativesVisualiser pnv = new PickNegativesVisualiser();
		pnv.construct(sPlus, sMinus, ltl, config);
		
		pnv.startLearner(null);
		// new PickNegativesVisualiser(new
		// SootCallGraphOracle()).construct(sPlus, sMinus,null, active);
		//config.setMinCertaintyThreshold(1);
		//config.setQuestionPathUnionLimit(1);
	}

	public static void setSimpleConfiguration(Configuration config,final boolean active, final int k)
	{
		if(!active){
			config.setKlimit(k);
			config.setAskQuestions(false); 
			if(k>=0)
				config.setLearnerScoreMode(Configuration.ScoreMode.KTAILS);
		}
		else
			config.setKlimit(-1);
		config.setDebugMode(true);
	}
	
	private boolean isCmdWithArgs(String arg,String cmd)
	{
		if (arg.equals(cmd))
			throw new IllegalArgumentException("Argument required for command "+cmd);
		return arg.startsWith(cmd);
	}
	
	public void process(String lineOfText) 
	{
		String fileString = lineOfText.trim();
		if (fileString.length() == 0)
			return;// ignore empty lines.
		if (isCmdWithArgs(fileString,cmdPositive))
			sPlus.add(tokeniseInput(fileString.substring(cmdPositive.length()+1)));
		else if (isCmdWithArgs(fileString,cmdNegative))
			sMinus.add(tokeniseInput(fileString.substring(cmdPositive.length()+1)));
		else if (isCmdWithArgs(fileString,cmdLTL))
		{
			if (ltl == null) ltl=new TreeSet<String>();
			ltl.add(fileString.substring(cmdLTL.length()+1));
		}
		else if (isCmdWithArgs(fileString,cmdK))
		{
			String value = fileString.substring(cmdK.length()+1).trim();
			k = Integer.valueOf(value);
		}
		else if(fileString.startsWith(cmdTextOutput))
			config.setGenerateTextOutput(true);
		else if(fileString.startsWith(cmdDotOutput))
			config.setGenerateDotOutput(true);
		else
			if (fileString.startsWith(cmdPassive))
				active = false;
			else
				if (isCmdWithArgs(fileString,cmdConfig))
				{
					List<String> values= tokeniseInput(fileString.substring(cmdConfig.length()+1));
					if (values.size() != 2)
						throw new IllegalArgumentException("invalid configuration option "+fileString);
					
					config.assignValue(values.get(0),values.get(1),true);
				}
				else
				if (fileString.startsWith(cmdComment))
				{// do nothing
				}
				else
				if (fileString.startsWith(cmdShowLTL))
				{
					showLTL = true;
				}
				else
					throw new IllegalArgumentException("invalid command "+fileString);
	}

	public static final String 
		cmdLTL = "ltl", 
		cmdK = "k", 
		cmdPositive="+", 
		cmdNegative="-", 
		cmdConfig="config",
		cmdTextOutput = "textoutput", 
		cmdDotOutput="dotoutput",
		cmdComment="#",
		cmdPassive="passive",
		cmdShowLTL="showltl";
	
	private static List<String> tokeniseInput(String str)
	{
		StringTokenizer tokenizer = new StringTokenizer(str);
		List<String> sequence = new ArrayList<String>();
		while (tokenizer.hasMoreTokens())
			sequence.add(tokenizer.nextToken());
		assert !sequence.isEmpty();
		return sequence;
	}

}
