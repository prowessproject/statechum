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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import statechum.Configuration;
import statechum.DeterministicDirectedSparseGraph;
import statechum.JUConstants;
import statechum.DeterministicDirectedSparseGraph.CmpVertex;
import statechum.DeterministicDirectedSparseGraph.VertexID;
import statechum.analysis.learning.rpnicore.GD.ChangesRecorder;
import statechum.analysis.learning.rpnicore.GD.LearnerGraphMutator;
import statechum.analysis.learning.rpnicore.WMethod.VERTEX_COMPARISON_KIND;


/**
 * @author kirill
 *
 */
@RunWith(Parameterized.class)
public class TestGD_MultipleCasesOfRenaming {

	/** Number of threads to use. */
	protected final int threadNumber;

	@Parameters
	public static Collection<Object[]> data() 
	{
		Collection<Object []> result = new LinkedList<Object []>();
		for(int i=1;i<8;++i)
			for(String stateC:new String[]{"C","A","F","S"})
				result.add(new Object[]{new Integer(i), stateC});
		
		return result;
	}


	/** The vertex which is different between different tests. */
	private final String stateC;
	
	public static String parametersToString(Integer threads,String stateC)
	{
		return stateC+" and "+threads+" threads";
	}
	
	/** Creates the test class with the number of threads to create as an argument. */
	public TestGD_MultipleCasesOfRenaming(int th,String C)
	{
		threadNumber = th;stateC=C;
	}
	
	@Test
	public final void testGD_nondetA()
	{
		Configuration config = Configuration.getDefaultConfiguration();
		final LearnerGraphND 
			grA=new LearnerGraphND(TestFSMAlgo.buildGraph("A-a->C-u->C-c->F\nC-c->G\nC-c->A\nC-b->A\n"+
				"G-b->A\nG-a->C\nG-b->F\n"+
				"F-a->A\nF-a->C\nF-a->G\n"
				, "TestGD_MultipleCasesOfRenamingA"),config), 
			grB = new LearnerGraphND(TestFSMAlgo.buildGraph(
				"B-a->D-u->D-b->"+stateC+"-b->D\n"+stateC+"-b->A\n"+stateC+"-b->G\n"+stateC+"-b->E\n", "TestGD_MultipleCasesOfRenamingB_"+stateC),config);
		
		String [] expectedDuplicates = (stateC.equals("C"))? new String[]{ stateC,"A" }:new String[]{"A"};
		runTest(grA,grB, "D", expectedDuplicates);
	}

	/** Clashes between disconnected vertices and the original ones. */
	@Test
	public final void testGD_nondetB()
	{
		Configuration config = Configuration.getDefaultConfiguration();
		final LearnerGraphND 
			grA=new LearnerGraphND(TestFSMAlgo.buildGraph("A-a->C-u->C-c->F\nC-c->G\nC-c->A\nC-b->A\n"+
				"G-b->A\nG-a->C\nG-b->F\n"+
				"F-a->A\nF-a->C\nF-a->G\n"
				, "TestGD_MultipleCasesOfRenamingA"),config), 
			grB = new LearnerGraphND(TestFSMAlgo.buildGraph(
				"B-a->D-u->D-b->"+stateC+"-b->D\n"+stateC+"-b->A\n"+stateC+"-b->G\n", "TestGD_MultipleCasesOfRenamingB_"+stateC),config);
		CmpVertex disconnectedA1 = AbstractLearnerGraph.generateNewCmpVertex(VertexID.parseID("T"), config),
			disconnectedA2 = AbstractLearnerGraph.generateNewCmpVertex(VertexID.parseID("U"), config),
			disconnectedB2 = AbstractLearnerGraph.generateNewCmpVertex(VertexID.parseID("U"), config),
			disconnectedB3 = AbstractLearnerGraph.generateNewCmpVertex(VertexID.parseID("E"), config);
		disconnectedA1.setColour(JUConstants.BLUE);disconnectedA2.setColour(JUConstants.AMBER);
		disconnectedB2.setHighlight(true);disconnectedB3.setDepth(5);
		grA.transitionMatrix.put(disconnectedA1,grA.createNewRow());grA.transitionMatrix.put(disconnectedA2,grA.createNewRow());
		grB.transitionMatrix.put(disconnectedB2,grB.createNewRow());grB.transitionMatrix.put(disconnectedB3,grB.createNewRow());
		
		String [] expectedDuplicates = (stateC.equals("C"))? new String[]{ stateC,"A" }:new String[]{"A"};
		LearnerGraphND outcome = runTest(grA,grB, "D", expectedDuplicates);
		Assert.assertNull(outcome.findVertex(disconnectedA1.getID()));
		Assert.assertTrue(DeterministicDirectedSparseGraph.deepEquals(disconnectedB2, outcome.findVertex(disconnectedB2.getID())));
		Assert.assertTrue(DeterministicDirectedSparseGraph.deepEquals(disconnectedB3, outcome.findVertex(disconnectedB3.getID())));
	}
	
	/** Graph B is slightly different now, hence duplicates are different too. */
	@Test
	public final void testGD_nondetC()
	{
		Configuration config = Configuration.getDefaultConfiguration().copy();
		final LearnerGraphND 
			grA=new LearnerGraphND(TestFSMAlgo.buildGraph("A-a->C-u->C-c->F\nC-c->G\nC-c->A\nC-b->A\n"+
				"G-b->A\nG-a->C\nG-b->F\n"+
				"F-a->A\nF-a->C\nF-a->G\n"
				, "TestGD_MultipleCasesOfRenamingA"),config), 
			grB = new LearnerGraphND(TestFSMAlgo.buildGraph(
				"B-a->"+stateC+"-u->"+stateC+"-b->F-b->D\nF-b->A\nF-b->G\nF-b->E\n", "TestGD_MultipleCasesOfRenamingB_"+stateC),config);

		String [] expectedDuplicates = (!stateC.equals("A"))? new String[]{ "A" }:new String[]{};
		runTest(grA,grB, stateC,expectedDuplicates);
	}
	
	private LearnerGraphND runTest(LearnerGraphND grA, LearnerGraphND grB, String secondStateInKeyPair, String [] duplicatesExpectedString)
	{
		Configuration config = Configuration.getDefaultConfiguration().copy();config.setGdFailOnDuplicateNames(false);config.setGdKeyPairThreshold(.1);
		GD<List<CmpVertex>,List<CmpVertex>,LearnerGraphNDCachedData,LearnerGraphNDCachedData> gd = new GD<List<CmpVertex>,List<CmpVertex>,LearnerGraphNDCachedData,LearnerGraphNDCachedData>();
		gd.init(grA, grB, threadNumber,config);gd.identifyKeyPairs();
		ChangesRecorder recorder = new ChangesRecorder(null);
		gd.makeSteps(recorder);

		Assert.assertEquals(2,gd.aTOb.size());
		Set<CmpVertex> keyPairsLeft = new TreeSet<CmpVertex>(),keyPairsRight = new TreeSet<CmpVertex>();
		keyPairsLeft.addAll(Arrays.asList(new CmpVertex[]{grA.findVertex(VertexID.parseID("A")),grA.findVertex(VertexID.parseID("C"))}));
		keyPairsRight.addAll(Arrays.asList(new CmpVertex[]{gd.origToNewB.get(grB.findVertex(VertexID.parseID("B"))),gd.origToNewB.get(grB.findVertex(VertexID.parseID(secondStateInKeyPair)))}));
		Assert.assertEquals(keyPairsLeft, gd.aTOb.keySet());
		Set<CmpVertex> actual = new TreeSet<CmpVertex>();actual.addAll(gd.aTOb.values());Assert.assertEquals(keyPairsRight, actual);
		Set<CmpVertex> duplicatesExpected = new TreeSet<CmpVertex>();
		for(String dup:duplicatesExpectedString) duplicatesExpected.add(gd.origToNewB.get(grB.findVertex(VertexID.parseID(dup))));
		
		Assert.assertEquals(duplicatesExpected,gd.duplicates); 
		Configuration cloneConfig = config.copy();cloneConfig.setLearnerCloneGraph(true);
		LearnerGraphND graph = new LearnerGraphND(cloneConfig);AbstractLearnerGraph.copyGraphs(grA, graph);
		ChangesRecorder.applyGD(graph, recorder.writeGD(TestGD.createDoc()));
		LearnerGraphND outcome = new LearnerGraphND(config);AbstractLearnerGraph.copyGraphs(graph, outcome);
		Assert.assertNull(WMethod.checkM(grB, graph));

		// Now do the same as above, but renumber states to match grB
		AbstractLearnerGraph.copyGraphs(grA, graph);
		Configuration configMut = Configuration.getDefaultConfiguration().copy();config.setLearnerCloneGraph(false);
		LearnerGraphMutator<List<CmpVertex>,LearnerGraphNDCachedData> graphPatcher = new LearnerGraphMutator<List<CmpVertex>,LearnerGraphNDCachedData>(graph,configMut,null);
		ChangesRecorder.loadDiff(graphPatcher, recorder.writeGD(TestGD.createDoc()));
		graphPatcher.removeDanglingStates();
		LearnerGraphND result = new LearnerGraphND(configMut);
		graphPatcher.relabel(result);
		Assert.assertNull(WMethod.checkM_and_colours(grB, result,VERTEX_COMPARISON_KIND.DEEP));
		return outcome;
	}
	
	@Test
	public final void testGD_nondet_incompatibles()
	{
		Configuration config = Configuration.getDefaultConfiguration();
		final LearnerGraphND 
			grA=new LearnerGraphND(TestFSMAlgo.buildGraph("A-a->C-u->C-c->F\nC-c->G\nC-c->A\nC-b->A\n"+
				"G-b->A\nG-b->C\nG-b->F\n"+
				"F-a->A\nF-a->C\nF-a->G\n"
				, "TestGD_MultipleCasesOfRenamingA"),config), 
			grB = new LearnerGraphND(TestFSMAlgo.buildGraph(
				"B-a->D-u->D-b->"+stateC+"-bD->D\n"+stateC+"-bA->A\n"+stateC+"-bG->G\n"+stateC+"-bE->E\n", "TestGD_MultipleCasesOfRenamingB_"+stateC),config);
				
		// add incompatibles for A
		for(String []pair:new String[][]{
				new String[]{"A","C"},
				new String[]{"A","F"},
				new String[]{"C","G"},
				new String[]{"C","F"}
		})
		{
			CmpVertex a=grA.findVertex(pair[0]),b=grA.findVertex(pair[1]);
			Assert.assertNotNull(a);Assert.assertNotNull(b);
			grA.addToIncompatibles(a,b);
		}
		
		// add incompatibles for B
		for(String []pair:new String[][]{
				new String[]{"B","D"},
				new String[]{"B","E"},
				new String[]{stateC,"A"},
				new String[]{stateC,"G"},
				new String[]{stateC,"E"}
		})
		{
			CmpVertex a=grB.findVertex(pair[0]),b=grB.findVertex(pair[1]);
			Assert.assertNotNull("cannot find vertex "+pair[0]+" in grB",a);Assert.assertNotNull("cannot find vertex "+pair[1]+" in grB",b);
			if (!a.getID().equals(b.getID()))
				grB.addToIncompatibles(a,b);
		}

		String [] expectedDuplicates = (stateC.equals("C"))? new String[]{ stateC,"A" }:new String[]{"A"};
		runTest(grA,grB, "D",expectedDuplicates);
	}
}
