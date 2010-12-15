/* Copyright (c) 2006, 2007, 2008 Neil Walkinshaw and Kirill Bogdanov
 * 
 * This file is part of StateChum.
 * 
 * StateChum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * StateChum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with StateChum.  If not, see <http://www.gnu.org/licenses/>.
 */

package statechum.analysis.learning.rpnicore;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import statechum.Configuration;
import statechum.Configuration.GDScoreComputationAlgorithmEnum;
import statechum.Configuration.GDScoreComputationEnum;
import statechum.Helper;
import statechum.JUConstants;
import statechum.DeterministicDirectedSparseGraph.CmpVertex;
import statechum.analysis.learning.PairScore;
import statechum.analysis.learning.rpnicore.GD.ChangesRecorder;
import statechum.analysis.learning.rpnicore.WMethod.DifferentFSMException;
import statechum.analysis.learning.rpnicore.WMethod.VERTEX_COMPARISON_KIND;

/**
 * @author kirill
 *
 */
@RunWith(Parameterized.class)
public class TestGD_ExistingGraphs {
	protected java.util.Map<CmpVertex,CmpVertex> newToOrig = null;

	/** Number of threads to use. */
	protected final int threadNumber, pairsToAdd;

	Configuration config = null;

	@Parameters
	public static Collection<Object[]> data() 
	{
		Collection<Object []> result = new LinkedList<Object []>();
		final String testFilePath = "resources/TestGraphs/75-6/";
		File path = new File(testFilePath);assert path.isDirectory();
		File files [] = path.listFiles(new FilenameFilter()
		{
			@Override 
			public boolean accept(@SuppressWarnings("unused") File dir, String name) 
			{
				return name.startsWith("N_");
			}});
		
		for(int fileNum = 0;fileNum < files.length;++fileNum)
			for(int threadNo=1;threadNo<8;++threadNo)
				for(double ratio:new double[]{0.5,0.68,0.9,-1})
					for(int pairs:new int[]{0,5,10})
						result.add(new Object[]{new Integer(threadNo), new Integer(pairs),ratio,
							files[fileNum].getAbsolutePath(), 
							files[(fileNum+1)%files.length].getAbsolutePath()
							});
		
		return result;
	}

	final String fileNameA,fileNameB;
	
	/** Positive value is the ratio of low-to-high above which key pairs are considered ok;
	 * negative value means that we set <em>setGdMaxNumberOfStatesInCrossProduct</em>
	 * to a low value and check that the outcome works.
	 */
	double low_to_high_ratio = -1;
	
	/** Creates the test class with the number of threads to create as an argument. */
	public TestGD_ExistingGraphs(int th, int pairs,double ratio, String fileA, String fileB)
	{
		threadNumber = th;fileNameA=fileA;fileNameB=fileB;low_to_high_ratio=ratio;pairsToAdd=pairs;
	}
	
	public static String parametersToString(Integer th, Integer pairs, Double ratio, String fileA, String fileB)
	{
		return "threads: "+th+", extra pairs: "+pairs+", ratio: "+ratio+", "+fileA+" v.s. "+fileB;
	}
	
	@Before
	public final void beforeTest()
	{
		newToOrig = new java.util.TreeMap<CmpVertex,CmpVertex>();
		config = Configuration.getDefaultConfiguration().copy();config.setGdFailOnDuplicateNames(false);
		config.setGdKeyPairThreshold(.75);
		if (low_to_high_ratio > 0)
			config.setGdLowToHighRatio(low_to_high_ratio);
		else
		{
			config.setGdLowToHighRatio(0.5);
			config.setGdMaxNumberOfStatesInCrossProduct(10);
		}
		config.setAttenuationK(0.95);
	}
	
	protected String testDetails()
	{
		return fileNameA+"-"+fileNameB+" ["+threadNumber+" threads] ";
	}
	
	private void addColourAndIncompatiblesRandomly(LearnerGraph gr,Random amberRnd)
	{
		for(int i=0;i<gr.getStateNumber()/3;++i)
		{
			gr.pathroutines.pickRandomState(amberRnd).setColour(JUConstants.AMBER);
			gr.pathroutines.pickRandomState(amberRnd).setColour(JUConstants.BLUE);
		}
		
		for(int i=0;i<gr.getStateNumber()/6;++i)
		{
			CmpVertex a = gr.pathroutines.pickRandomState(amberRnd), b = gr.pathroutines.pickRandomState(amberRnd);
			gr.addToCompatibility(a, b, JUConstants.PAIRCOMPATIBILITY.INCOMPATIBLE);
		}
		for(int i=0;i<gr.getStateNumber()/6;++i)
		{
			CmpVertex a = gr.pathroutines.pickRandomState(amberRnd), b = gr.pathroutines.pickRandomState(amberRnd);
			gr.addToCompatibility(a, b, JUConstants.PAIRCOMPATIBILITY.MERGED);
		}
	}
	
	static <TARGET_TYPE,CACHE_TYPE extends CachedData<TARGET_TYPE,CACHE_TYPE>> void addPairsRandomly(final GD<TARGET_TYPE,TARGET_TYPE,CACHE_TYPE,CACHE_TYPE> gd, int pairsToAdd)
	{
		Set<CmpVertex> usedA = new TreeSet<CmpVertex>(), usedB = new TreeSet<CmpVertex>();
		usedA.addAll(gd.statesOfA);usedB.addAll(gd.statesOfB);
		for(PairScore ps:gd.frontWave)
		{
			usedA.remove(ps.firstElem);usedB.remove(ps.secondElem);
		}
		if (usedA.isEmpty() || usedB.isEmpty()) return;
		ArrayList<CmpVertex> usedA_array=new ArrayList<CmpVertex>(usedA),usedB_array=new ArrayList<CmpVertex>(usedB);
		Random rnd=new Random(0);
		Iterator<Integer> 
			itA=TestGD.chooseRandomly(rnd, usedA_array.size(), pairsToAdd).iterator(),
			itB=TestGD.chooseRandomly(rnd, usedB_array.size(), pairsToAdd).iterator();
		for(int cnt=0;cnt<pairsToAdd;++cnt)
			gd.frontWave.add(new PairScore(usedA_array.get(itA.next()), usedB_array.get(itB.next()), 1, 0));
	}
	
	
	public final void runPatch(String fileA, String fileB)
	{
		try
		{
			LearnerGraph grA = new LearnerGraph(config);AbstractPersistence.loadGraph(fileA, grA);addColourAndIncompatiblesRandomly(grA, new Random(0));
			LearnerGraph grB = new LearnerGraph(config);AbstractPersistence.loadGraph(fileB, grB);addColourAndIncompatiblesRandomly(grB, new Random(1));
			GD<CmpVertex,CmpVertex,LearnerGraphCachedData,LearnerGraphCachedData> gd = new GD<CmpVertex,CmpVertex,LearnerGraphCachedData,LearnerGraphCachedData>();
			LearnerGraph graph = new LearnerGraph(config);AbstractPersistence.loadGraph(fileA, graph);addColourAndIncompatiblesRandomly(graph, new Random(0));
			LearnerGraph outcome = new LearnerGraph(config);
			ChangesRecorder patcher = new ChangesRecorder(null);
			//gd.computeGD(grA, grB, threadNumber, patcher,config);
			
			gd.init(grA, grB, threadNumber,config);
			gd.identifyKeyPairs();
			addPairsRandomly(gd,pairsToAdd);
			gd.makeSteps();
			gd.computeDifference(patcher);

			ChangesRecorder.applyGD_WithRelabelling(graph, patcher.writeGD(TestGD.createDoc()),outcome);
			Assert.assertNull(testDetails(),WMethod.checkM(grB,graph));
			Assert.assertEquals(testDetails(),grB.getStateNumber(),graph.getStateNumber());
			DifferentFSMException ex= WMethod.checkM_and_colours(grB,outcome,VERTEX_COMPARISON_KIND.DEEP);
			Assert.assertNull(testDetails()+ex,WMethod.checkM_and_colours(grB,outcome,VERTEX_COMPARISON_KIND.DEEP));Assert.assertEquals(grB.getStateNumber(),graph.getStateNumber());
			Assert.assertEquals(grB,outcome);
		}
		catch(IOException ex)
		{
			Helper.throwUnchecked("failed to load a file", ex);
		}
	}

	@Test
	public final void testGD_AB_linearRH()
	{
		config.setGdScoreComputation(GDScoreComputationEnum.GD_RH);config.setGdScoreComputationAlgorithm(GDScoreComputationAlgorithmEnum.SCORE_LINEAR);
		runPatch(fileNameA, fileNameB);
	}
	
	@Test
	public final void testGD_BA_linearRH()
	{
		config.setGdScoreComputation(GDScoreComputationEnum.GD_RH);config.setGdScoreComputationAlgorithm(GDScoreComputationAlgorithmEnum.SCORE_LINEAR);
		runPatch(fileNameB, fileNameA);
	}
	
	@Test
	public final void testGD_AB_walkRH()
	{
		config.setGdScoreComputation(GDScoreComputationEnum.GD_RH);config.setGdScoreComputationAlgorithm(GDScoreComputationAlgorithmEnum.SCORE_RANDOMPATHS);
		config.setGdScoreComputationAlgorithm_RandomWalk_NumberOfSequences(100);
		runPatch(fileNameA, fileNameB);
	}
	
	@Test
	public final void testGD_BA_walkRH()
	{
		config.setGdScoreComputation(GDScoreComputationEnum.GD_RH);config.setGdScoreComputationAlgorithm(GDScoreComputationAlgorithmEnum.SCORE_RANDOMPATHS);
		config.setGdScoreComputationAlgorithm_RandomWalk_NumberOfSequences(100);
		runPatch(fileNameB, fileNameA);
	}
	
	@Test
	public final void testGD_AB_walk()
	{
		config.setGdScoreComputation(GDScoreComputationEnum.GD_DIRECT);config.setGdScoreComputationAlgorithm(GDScoreComputationAlgorithmEnum.SCORE_RANDOMPATHS);
		config.setGdScoreComputationAlgorithm_RandomWalk_NumberOfSequences(100);
		runPatch(fileNameA, fileNameB);
	}
	
	@Test
	public final void testGD_BA_walk()
	{
		config.setGdScoreComputation(GDScoreComputationEnum.GD_DIRECT);config.setGdScoreComputationAlgorithm(GDScoreComputationAlgorithmEnum.SCORE_RANDOMPATHS);
		config.setGdScoreComputationAlgorithm_RandomWalk_NumberOfSequences(100);
		runPatch(fileNameB, fileNameA);
	}
	
	@Test
	public final void testGD_AB_testsetRH()
	{
		config.setGdScoreComputation(GDScoreComputationEnum.GD_RH);config.setGdScoreComputationAlgorithm(GDScoreComputationAlgorithmEnum.SCORE_TESTSET);
		runPatch(fileNameA, fileNameB);
	}
	
	@Test
	public final void testGD_BA_testsetRH()
	{
		config.setGdScoreComputation(GDScoreComputationEnum.GD_RH);config.setGdScoreComputationAlgorithm(GDScoreComputationAlgorithmEnum.SCORE_TESTSET);
		runPatch(fileNameB, fileNameA);
	}
	
	@Test
	public final void testGD_AB_testset()
	{
		config.setGdScoreComputation(GDScoreComputationEnum.GD_DIRECT);config.setGdScoreComputationAlgorithm(GDScoreComputationAlgorithmEnum.SCORE_TESTSET);
		runPatch(fileNameA, fileNameB);
	}
	
	@Test
	public final void testGD_BA_testset()
	{
		config.setGdScoreComputation(GDScoreComputationEnum.GD_DIRECT);config.setGdScoreComputationAlgorithm(GDScoreComputationAlgorithmEnum.SCORE_TESTSET);
		runPatch(fileNameB, fileNameA);
	}

	@Test
	public final void testGD_AA()
	{
		try
		{
			LearnerGraph grA = new LearnerGraph(config);AbstractPersistence.loadGraph(fileNameA,grA);
			LearnerGraph grB = new LearnerGraph(config);AbstractPersistence.loadGraph(fileNameA,grB);
			LearnerGraph graph = new LearnerGraph(config);AbstractPersistence.loadGraph(fileNameA,graph);
			GD<CmpVertex,CmpVertex,LearnerGraphCachedData,LearnerGraphCachedData> gd = new GD<CmpVertex,CmpVertex,LearnerGraphCachedData,LearnerGraphCachedData>();
			LearnerGraph outcome = new LearnerGraph(config);
			ChangesRecorder.applyGD_WithRelabelling(graph, gd.computeGDToXML(grA, grB, threadNumber, TestGD.createDoc(),null,config),outcome);
			Assert.assertNull(testDetails(),WMethod.checkM(grB,graph));Assert.assertEquals(grB.getStateNumber(),graph.getStateNumber());
			Assert.assertNull(testDetails(),WMethod.checkM_and_colours(grB,outcome,VERTEX_COMPARISON_KIND.DEEP));Assert.assertEquals(grB.getStateNumber(),graph.getStateNumber());
		}
		catch(IOException ex)
		{
			Helper.throwUnchecked("failed to load a file", ex);
		}
	}
	
}
