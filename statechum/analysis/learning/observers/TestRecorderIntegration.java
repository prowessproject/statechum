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
package statechum.analysis.learning.observers;

import static statechum.analysis.learning.rpnicore.TestFSMAlgo.buildSet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import statechum.Configuration;
import statechum.Pair;
import statechum.Configuration.IDMode;
import statechum.analysis.learning.AbstractOracle;
import statechum.analysis.learning.RPNIBlueFringeLearner;
import statechum.analysis.learning.observers.ProgressDecorator.LearnerEvaluationConfiguration;
import statechum.analysis.learning.rpnicore.LearnerGraph;
import statechum.analysis.learning.rpnicore.TestFSMAlgo;
import statechum.analysis.learning.rpnicore.Transform;
import statechum.analysis.learning.rpnicore.WMethod;
import edu.uci.ics.jung.graph.impl.DirectedSparseGraph;

/**
 * @author kirill
 *
 */
@RunWith(Parameterized.class)
public class TestRecorderIntegration {
	
	@Parameters
	public static Collection<Object[]> data() 
	{
		Collection<Object []> result = new LinkedList<Object []>();
		for(boolean zip:new boolean[]{false,true})
			for(boolean logCompression:new boolean[]{false,true})
				for(boolean forceFallback:new boolean[]{false,true})
			result.add(new Object[]{new Boolean(zip),new Boolean(logCompression),new Boolean(forceFallback)});
		
		return result;
	}

	private final boolean useZip, useCompression, forceGDfallback;
	
	public TestRecorderIntegration(boolean zip,boolean logCompression, boolean forceFallback)
	{
		useZip=zip;useCompression=logCompression;forceGDfallback=forceFallback;
	}
	
	/** A modified version of a similar method from TestRpniLearner. This one
	 * checks that progress recording works correctly.
	 * 
	 * @param fsmString fsm to learn
	 * @param name its name
	 * @param plus positives
	 * @param minus negatives.
	 * @param useZip whether to use ZIP compression with the data stream. 
	 */
	protected void checkLearnerProgressRecording(String fsmString, String name,final String [][] plus, final String [][] minus)
	{
		Configuration testConfig = Configuration.getDefaultConfiguration().copy();
		testConfig.setGdFailOnDuplicateNames(false);
		if (forceGDfallback) testConfig.setGdMaxNumberOfStatesInCrossProduct(0);
		testConfig.setCompressLogs(useCompression);
		final DirectedSparseGraph g = TestFSMAlgo.buildGraph(fsmString, "sample FSM");
		final LearnerGraph expected = new LearnerGraph(g,testConfig);
		
		// now sanity checking on the plus and minus sets
		for(String [] path:plus)
			assert AbstractOracle.USER_ACCEPTED == expected.paths.tracePath(Arrays.asList(path));
		for(String [] path:minus)
			assert AbstractOracle.USER_ACCEPTED != expected.paths.tracePath(Arrays.asList(path));
		RPNIBlueFringeLearner l = new RPNIBlueFringeLearner(null,testConfig)
		{
			@Override
			public Pair<Integer,String> CheckWithEndUser(
					@SuppressWarnings("unused")	LearnerGraph model,
					List<String> question, 
					@SuppressWarnings("unused")	final Object [] moreOptions)
			{
				return new Pair<Integer,String>(expected.paths.tracePath(question),null);
			}
		};
		testConfig.setLearnerIdMode(IDMode.POSITIVE_NEGATIVE);
		ByteArrayOutputStream logStream = new ByteArrayOutputStream();
		RecordProgressDecorator recorder = new RecordProgressDecorator(l,logStream,1,testConfig,useZip);
		Collection<List<String>> testSet = new LinkedList<List<String>>();
		recorder.writeLearnerEvaluationData(new ProgressDecorator.LearnerEvaluationConfiguration(expected, testSet, testConfig, null));
		LearnerGraph learntStructureA = recorder.learnMachine(buildSet(plus), buildSet(minus));
		
		//System.out.println("compression rate: "+recorder.getCompressionRate());
		//System.out.println(logStream.toString()+"============");
		//System.out.println(logStream.toByteArray().length);
		LearnerGraph learntMachineNoRejects = Transform.removeRejectStates(learntStructureA,testConfig);
		WMethod.checkM(learntMachineNoRejects, expected);
		
		{// matching two simulators
			final LearnerSimulator 
				simulator = new LearnerSimulator(new ByteArrayInputStream(logStream.toByteArray()),useZip),
				simulator2 = new LearnerSimulator(new ByteArrayInputStream(logStream.toByteArray()),useZip);
			
			LearnerEvaluationConfiguration eval1 = simulator.readLearnerConstructionData();
			Assert.assertNull(WMethod.checkM(expected, eval1.graph));
			Assert.assertEquals(testSet, eval1.testSet);
			Assert.assertEquals(expected.config, testConfig);
			LearnerEvaluationConfiguration eval2 = simulator2.readLearnerConstructionData();
			Assert.assertNull(WMethod.checkM(expected, eval2.graph));
			Assert.assertEquals(testSet, eval2.testSet);
			Assert.assertEquals(expected.config, testConfig);
			
			new Test_LearnerComparator(simulator,simulator2).learnMachine(buildSet(plus), buildSet(minus));
		}

		{// now a simulator to a learner
			final LearnerSimulator simulator = new LearnerSimulator(new ByteArrayInputStream(logStream.toByteArray()),useZip);
			LearnerEvaluationConfiguration eval1 = simulator.readLearnerConstructionData();
			Assert.assertNull(WMethod.checkM(expected, eval1.graph));
			Assert.assertEquals(testSet, eval1.testSet);
			Assert.assertEquals(expected.config, testConfig);

			RPNIBlueFringeLearner learner2 = new RPNIBlueFringeLearner(null,expected.config)
			{
				@Override
				public Pair<Integer,String> CheckWithEndUser(
						@SuppressWarnings("unused")	LearnerGraph model,
						List<String> question, 
						@SuppressWarnings("unused")	final Object [] moreOptions)
				{
					return new Pair<Integer,String>(expected.paths.tracePath(question),null);
				}
			};
			new Test_LearnerComparator(learner2,simulator).learnMachine(buildSet(plus), buildSet(minus));
		}


		{// now two learners
			RPNIBlueFringeLearner learnerA = new RPNIBlueFringeLearner(null,testConfig)
			{
				@Override
				public Pair<Integer,String> CheckWithEndUser(
						@SuppressWarnings("unused")	LearnerGraph model,
						List<String> question, 
						@SuppressWarnings("unused")	final Object [] moreOptions)
				{
					return new Pair<Integer,String>(expected.paths.tracePath(question),null);
				}
			};
			RPNIBlueFringeLearner learnerB = new RPNIBlueFringeLearner(null,testConfig)
			{
				@Override
				public Pair<Integer,String> CheckWithEndUser(
						@SuppressWarnings("unused")	LearnerGraph model,
						List<String> question, 
						@SuppressWarnings("unused")	final Object [] moreOptions)
				{
					return new Pair<Integer,String>(expected.paths.tracePath(question),null);
				}
			};
			new Test_LearnerComparator(learnerA,learnerB).learnMachine(buildSet(plus), buildSet(minus));
		}

	}

	/** These tests check the framework to verify that 
	 * the outcome of learning is correct. The machines for the "testLearnerRec..." 
	 * tests are copied verbatim from TestRpniLearner
	 */
	@Test
	public void testLearnerRec1()
	{
		checkLearnerProgressRecording("A-a->B<-a-A\nA-b->A","testLearner1",
				new String[][]{new String[]{"b","b","a"},new String[]{"b","a"},new String[]{"b"}}, 
				new String[][]{new String[]{"a","b"},new String[]{"a","a"}});
	}

	@Test
	public void testLearnerRec2a()
	{
		checkLearnerProgressRecording("A-a->B<-a-C-b->A\nA-b->C\nC-c->C\n","testLearner2a",
				new String[][]{new String[]{"b","b","a"},new String[]{"b","a"},new String[]{"b","c"}, new String[]{"b","c","c"}  }, 
				new String[][]{new String[]{"c"}});
	}

	@Test
	public void testLearnerRec2b()
	{
		checkLearnerProgressRecording("A-a->B<-a-C-b->A\nA-b->C\nC-c->C\n","testLearner2b",new String[][]{new String[]{"b","b","a"},new String[]{"b","a"},new String[]{"b","c"},new String[]{"b","c","c"}}, new String[][]{new String[]{"c"},new String[]{"b","b","c"}	});
	}

	@Test
	public void testLearnerRec3()
	{
		checkLearnerProgressRecording("A-text->B-text->B\nA-figure->C-figure->C\nB-figure->C\nC-text->B\nB-set_position->F\nF-edit->G\nG-finalize->A\nC-set_position->D\nD-set_dimensions->E-set_dimensions->E-figure->C\nE-text->B",
				"testLearner3",
				new String[][]{new String[]{"figure", "figure","set_position","set_dimensions","set_dimensions","set_dimensions","set_dimensions", "figure", "set_position", "set_dimensions"}, 
				new String[]{"figure", "figure","set_position","set_dimensions","set_dimensions","set_dimensions","text", "set_position", "edit"}, 
				new String[]{"text","text","set_position","edit","finalize","text"}, 
				new String[]{"text","figure","figure"}, 
				new String[]{"text","text","set_position","edit","finalize","figure"}}, 
				
				new String[][]{
				new String[]{"text","text","set_position","edit","finalize","set_dimensions"},
				new String[]{"text","text","set_position","edit","finalize","set_position"}
		});
		
	}
}
