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
package statechum.analysis.learning.rpnicore;

import static org.junit.Assert.assertTrue;
import static statechum.analysis.learning.TestFSMAlgo.buildGraph;
import statechum.DeterministicDirectedSparseGraph.VertexID;
import java.util.Collection;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import statechum.Configuration;
import statechum.JUConstants;
import statechum.StringVertex;
import statechum.analysis.learning.TestFSMAlgo;
import edu.uci.ics.jung.graph.Vertex;
import edu.uci.ics.jung.graph.impl.DirectedSparseGraph;
import edu.uci.ics.jung.graph.impl.DirectedSparseVertex;
import edu.uci.ics.jung.utils.UserData;

@RunWith(Parameterized.class)
public class TestGraphConstructionWithDifferentConf {
	public TestGraphConstructionWithDifferentConf(Configuration conf) {
		mainConfiguration = conf;
	}
	
	@Parameters
	public static Collection<Object[]> data() 
	{
		return Configuration.configurationsForTesting();
	}

	/** Make sure that whatever changes a test have made to the 
	 * configuration, next test is not affected.
	 */
	@Before
	public void beforeTest()
	{
		
		LearnerGraph.testMode = true;		
		config = (Configuration)mainConfiguration.clone();
		config.setAllowedToCloneNonCmpVertex(true);
		differentA = new LearnerGraph(buildGraph("Q-a->A-b->B", "testFSMStructureEquals2"),config);
		differentB = new LearnerGraph(buildGraph("A-b->A-a->B", "testFSMStructureEquals2"),config);
	}

	/** The configuration to use when running tests. */
	private Configuration config = null, mainConfiguration = null;

	/** Used as arguments to equalityTestingHelper. */
	private LearnerGraph differentA = null, differentB = null;

	@Test
	public final void testFSMStructureEquals1a()
	{
		LearnerGraph a=new LearnerGraph(config),b=new LearnerGraph(config);
		TestFSMAlgo.equalityTestingHelper(a,b,differentA,differentB);

		Assert.assertFalse(a.equals(null));
		Assert.assertFalse(a.equals("hello"));
		config.setDefaultInitialPTAName("B");
		b = new LearnerGraph(config);Assert.assertFalse(a.equals(b));
	}
	
	@Test
	public final void testFSMStructureEquals2a()
	{
		LearnerGraph a=new LearnerGraph(buildGraph("A-a->A-b->B\nB-b->B", "testFSMStructureEquals2a"),config);
		LearnerGraph b=new LearnerGraph(buildGraph("A-a->A-b->B\nB-b->B", "testFSMStructureEquals2a"),config);
		TestFSMAlgo.equalityTestingHelper(a,b,differentA,differentB);
	}
	
	/** Tests that reject states do not mess up anything. */
	@Test
	public final void testFSMStructureEquals2b()
	{
		LearnerGraph a=new LearnerGraph(buildGraph("A-a->A-b->B\nA-c-#C\nB-b->B", "testFSMStructureEquals2b"),config);
		LearnerGraph b=new LearnerGraph(buildGraph("A-a->A-b->B\nA-c-#C\nB-b->B", "testFSMStructureEquals2b"),config);
		TestFSMAlgo.equalityTestingHelper(a,b,differentA,differentB);
	}
	
	/** Tests that different types of states make a difference on the outcome of a comparison. */
	@Test
	public final void testFSMStructureEquals2c()
	{
		LearnerGraph a=new LearnerGraph(buildGraph("A-a->A-b->B\nA-c->C\nB-b->B", "testFSMStructureEquals2c"),config);
		LearnerGraph b=new LearnerGraph(buildGraph("A-a->A-b->B\nA-c-#C\nB-b->B", "testFSMStructureEquals2c"),config);
		TestFSMAlgo.equalityTestingHelper(a,a,b,differentB);
	}
	
	/** Tests that state colour does not affect a comparison. */
	@Test
	public final void testFSMStructureEquals2d()
	{
		LearnerGraph a=new LearnerGraph(buildGraph("A-a->A-b->B\nA-c-#C\nB-b->B", "testFSMStructureEquals2d"),config);
		LearnerGraph b=new LearnerGraph(buildGraph("A-a->A-b->B\nA-c-#C\nB-b->B", "testFSMStructureEquals2d"),config);
		a.findVertex("B").setColour(JUConstants.RED);
		a.findVertex("A").setHighlight(true);
		TestFSMAlgo.equalityTestingHelper(a,b,differentA,differentB);
	}
	
	@Test
	public final void testFSMStructureEquals3()
	{
		LearnerGraph a=new LearnerGraph(buildGraph("A-a->A-b->B\nB-b->B", "testFSMStructureEquals3"),config);
		LearnerGraph b=new LearnerGraph(buildGraph("A-a->A-b->B\nB-b->B", "testFSMStructureEquals3"),config);
		
		b.initEmpty();
		TestFSMAlgo.equalityTestingHelper(a,a,b,differentB);
	}
	
	@Test
	public final void testFSMStructureEquals4()
	{
		LearnerGraph a=new LearnerGraph(buildGraph("A-a->A-b->B\nB-b->B", "testFSMStructureEquals4"),config);
		LearnerGraph b=new LearnerGraph(buildGraph("A-a->A-b->B\nB-b->B", "testFSMStructureEquals4"),config);
		
		b.initPTA();
		TestFSMAlgo.equalityTestingHelper(a,a,b,differentB);
	}
	
	@Test
	public final void testFSMStructureEquals5()
	{
		LearnerGraph a=new LearnerGraph(buildGraph("A-a->A-b->B\nB-b->B", "testFSMStructureEquals6"),config);
		LearnerGraph b=new LearnerGraph(buildGraph("A-a->A-b->B\nB-b->B", "testFSMStructureEquals6"),config);
		
		b.init=new StringVertex("B");
		TestFSMAlgo.equalityTestingHelper(a,a,b,differentB);
	}

	/** Tests that clones are faithful replicas. */
	@Test
	public final void testFSMStructureClone1()
	{
		LearnerGraph a=new LearnerGraph(buildGraph("A-a->A-b->B\nB-b->B", "testFSMStructureClone1"),config);
		LearnerGraph b=new LearnerGraph(buildGraph("A-a->A-b->B\nB-b->B", "testFSMStructureClone1"),config);
		LearnerGraph bClone = b.copy(b.config);
		TestFSMAlgo.equalityTestingHelper(a,bClone,differentA, differentB);
		TestFSMAlgo.equalityTestingHelper(b,bClone,differentA, differentB);
		bClone.initPTA();
		TestFSMAlgo.equalityTestingHelper(a,b,bClone,differentB);
	}

	/** Tests that clones are faithful replicas. */
	@Test
	public final void testFSMStructureClone2()
	{
		LearnerGraph a=new LearnerGraph(buildGraph("A-a->A-b->B\nB-b->B", "testFSMStructureClone2"),config);
		LearnerGraph b=new LearnerGraph(buildGraph("A-a->A-b->B\nB-b->B", "testFSMStructureClone2"),config);
		LearnerGraph bClone = b.copy(b.config);
		b.initPTA();
		TestFSMAlgo.equalityTestingHelper(a,bClone,b,differentB);
	}

	@Test
	public void testGraphConstructionFail1a()
	{
		boolean exceptionThrown = false;
		try
		{
			buildGraph("A--a-->B<-b-CONFL\nA-b->A-c->A\nB-d->B-p-#CONFL","testGraphConstructionFail1a");
		}
		catch(IllegalArgumentException e)
		{
			assertTrue("correct exception not thrown",e.getMessage().contains("conflicting") && e.getMessage().contains("CONFL"));
			exceptionThrown = true;
		}
		
		assertTrue("exception not thrown",exceptionThrown);
	}
	
	@Test
	public void testGraphConstructionFail1b()
	{
		boolean exceptionThrown = false;
		try
		{
			buildGraph("A--a-->CONFL-b-#CONFL","testGraphConstructionFail1b");
		}
		catch(IllegalArgumentException e)
		{
			assertTrue("correct exception not thrown",e.getMessage().contains("conflicting") && e.getMessage().contains("CONFL"));
			exceptionThrown = true;
		}
		
		assertTrue("exception not thrown",exceptionThrown);
	}
	
	/** Checks if adding a vertex to a graph causes an exception to be thrown. */
	public void checkWithVertex(Vertex v,String expectedExceptionString, String testName)
	{
		final DirectedSparseGraph g = buildGraph("A--a-->B<-b-CONFL\nA-b->A-c->A\nB-d->B-p->CONFL",testName);
		new LearnerGraph(g,config);// without the vertex being added, everything should be fine.
		g.addVertex(v);// add the vertex
		
		try
		{
			new LearnerGraph(g,config);// now getGraphData should choke.
			Assert.fail("exception not thrown");
		}
		catch(IllegalArgumentException e)
		{
			assertTrue("correct exception not thrown: expected "+expectedExceptionString+" got "+e.getMessage(),e.getMessage().contains(expectedExceptionString) );
		}
		
	}
	
	@Test
	public void testGraphConstructionFail2()
	{
		DirectedSparseVertex v = new DirectedSparseVertex();
		v.addUserDatum(JUConstants.ACCEPTED, true, UserData.SHARED);v.addUserDatum(JUConstants.LABEL, new VertexID("B"), UserData.SHARED);
		checkWithVertex(v, "multiple", "testGraphConstructionFail2");
	}
	
	@Test
	public void testGraphConstructionFail3()
	{
		DirectedSparseVertex v = new DirectedSparseVertex();
		v.addUserDatum(JUConstants.ACCEPTED, true, UserData.SHARED);v.addUserDatum(JUConstants.LABEL, new VertexID("CONFL"), UserData.SHARED);
		checkWithVertex(v, "multiple", "testGraphConstructionFail3");
	}
	
	@Test
	public void testGraphConstructionFail4a()
	{
		DirectedSparseVertex v = new DirectedSparseVertex();
		v.addUserDatum(JUConstants.ACCEPTED, true, UserData.SHARED);v.addUserDatum(JUConstants.LABEL, new VertexID("Q"), UserData.SHARED);v.addUserDatum(JUConstants.INITIAL, true, UserData.SHARED);
		checkWithVertex(v, "labelled as initial states", "testGraphConstructionFail4a");
	}
	
	@Test
	public void testGraphConstructionFail4b()
	{
		DirectedSparseVertex v = new DirectedSparseVertex();
		v.addUserDatum(JUConstants.ACCEPTED, true, UserData.SHARED);v.addUserDatum(JUConstants.LABEL, new VertexID("Q"), UserData.SHARED);v.addUserDatum(JUConstants.INITIAL, new VertexID("aa"), UserData.SHARED);
		checkWithVertex(v, "invalid init property", "testGraphConstructionFail4b");
	}
	
	@Test
	public void testGraphConstructionFail5a()
	{
		DirectedSparseVertex v = new DirectedSparseVertex();
		v.addUserDatum(JUConstants.ACCEPTED, true, UserData.SHARED);v.addUserDatum(JUConstants.LABEL, new VertexID("Q"), UserData.SHARED);v.addUserDatum(JUConstants.INITIAL, "aa", UserData.SHARED);
		checkWithVertex(v, "invalid init property", "testGraphConstructionFail5a");
	}
	
	@Test
	public void testGraphConstructionFail5b()
	{
		DirectedSparseVertex v = new DirectedSparseVertex();
		v.addUserDatum(JUConstants.ACCEPTED, true, UserData.SHARED);v.addUserDatum(JUConstants.LABEL, new VertexID("Q"), UserData.SHARED);v.addUserDatum(JUConstants.INITIAL, false, UserData.SHARED);
		checkWithVertex(v, "invalid init property", "testGraphConstructionFail5b");
	}

	/** missing initial state in an empty graph. */
	@Test
	public void testGraphConstructionFail6() 
	{
		boolean exceptionThrown = false;
		try
		{
			new LearnerGraph(new DirectedSparseGraph(),config);// now getGraphData should choke.			
		}
		catch(IllegalArgumentException e)
		{
			final String expectedString = "missing initial";
			assertTrue("correct exception not thrown expected "+expectedString+" got "+e.getMessage(),e.getMessage().contains(expectedString) );
			exceptionThrown = true;
		}
		
		assertTrue("exception not thrown",exceptionThrown);
	}

	/** Unlabelled states. */
	@Test
	public final void testGraphConstructionFail7() 
	{
		DirectedSparseGraph g = new DirectedSparseGraph();
		DirectedSparseVertex init = new DirectedSparseVertex();
		init.addUserDatum(JUConstants.INITIAL, true, UserData.SHARED);
		init.addUserDatum(JUConstants.ACCEPTED, true, UserData.SHARED);g.addVertex(init);
		boolean exceptionThrown = false;
		try
		{
			new LearnerGraph(new DirectedSparseGraph(),config);// now getGraphData should choke.			
		}
		catch(IllegalArgumentException e)
		{
			final String expectedString = "missing initial";
			assertTrue("correct exception not thrown expected "+expectedString+" got "+e.getMessage(),e.getMessage().contains(expectedString) );
			exceptionThrown = true;
		}
		
		assertTrue("exception not thrown",exceptionThrown);
	}
}