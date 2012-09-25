/* Copyright (c) 2006, 2007, 2008 Neil Walkinshaw and Kirill Bogdanov
 * 
 * This file is part of StateChum
 * 
 * StateChum is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * StateChum is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * StateChum. If not, see <http://www.gnu.org/licenses/>.
 */ 
package statechum.analysis.learning.rpnicore;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import statechum.Configuration;
import statechum.analysis.learning.rpnicore.WMethod.DifferentFSMException;
import edu.uci.ics.jung.graph.impl.DirectedSparseGraph;
import static statechum.analysis.learning.rpnicore.FsmParser.buildGraph;
import static statechum.analysis.learning.rpnicore.FsmParser.buildLearnerGraph;

public class TestEquivalenceChecking {
	public TestEquivalenceChecking()
	{
		mainConfiguration = Configuration.getDefaultConfiguration().copy();
		mainConfiguration.setAllowedToCloneNonCmpVertex(true);
	}
	
	org.w3c.dom.Document doc = null;
	
	/** Make sure that whatever changes a test have made to the 
	 * configuration, next test is not affected.
	 */
	@Before
	public void beforeTest()
	{
		config = mainConfiguration.copy();

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try
		{
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);factory.setValidating(false);// we do not have a schema to validate against-this does not seem necessary for the simple data format we are considering here.
			doc = factory.newDocumentBuilder().newDocument();
		}
		catch(ParserConfigurationException e)
		{
			statechum.Helper.throwUnchecked("failed to construct DOM document",e);
		}
	}

	/** The configuration to use when running tests. */
	Configuration config = null, mainConfiguration = null;

	/** Checks if the passed graph is isomorphic to the provided fsm
	 * 
	 * @param g graph to check
	 * @param fsm the string representation of the machine which the graph should be isomorphic to
	 */
	public void checkEq(DirectedSparseGraph g, String fsm)
	{
		DirectedSparseGraph expectedGraph = buildGraph(fsm,"expected graph",config);
		final LearnerGraph graph = new LearnerGraph(g,Configuration.getDefaultConfiguration());
		final LearnerGraph expected = new LearnerGraph(expectedGraph,Configuration.getDefaultConfiguration());
		
		assertEquals("incorrect data",true,expected.equals(graph));
	}

	@Test
	public void testCheckEq()
	{
		DirectedSparseGraph g=buildGraph("P-a->Q_State-b->P-c->P","testCheckEq",config);
		checkEq(g,"P-c->P<-b-Q_State<-a-P");
	}
	
	/** Verifies the equivalence of a supplied graph to the supplied machine. */
	public static void checkM_ND(String fsm,String g,Configuration conf)
	{
		final LearnerGraphND graph = new LearnerGraphND(buildGraph(g,"expected graph",conf),conf);
		final LearnerGraphND expected = new LearnerGraphND(buildGraph(fsm,"expected graph",conf),conf);
		DifferentFSMException 
			ex1 = WMethod.checkM(expected,graph),
			ex2 = WMethod.checkM(graph,expected);
		if (ex1 != null)
			Assert.assertNotNull(ex2);
		else
			Assert.assertNull(ex2);
		Assert.assertNull(ex1==null?"":ex1.toString(),ex1);
	}

	/** Verifies the equivalence of a supplied graph to the supplied machine.
	 */
	public static void checkM(String fsm,DirectedSparseGraph g,Configuration conf)
	{
		final LearnerGraph graph = new LearnerGraph(g,conf);
		final LearnerGraph expected = new LearnerGraph(buildGraph(fsm,"expected graph",conf),conf);
		
		DifferentFSMException 
			ex1 = WMethod.checkM(expected,graph),
			ex2 = WMethod.checkM(graph,expected);
		
		if (ex1 != null)
			Assert.assertNotNull(ex2);
		else
			Assert.assertNull(ex2);

		if (ex1 != null)
			throw ex1;
	}
	
	/** Verifies the equivalence of a supplied graph to the supplied machine.
	 */
	public static void checkM(String fsm,String g,Configuration conf)
	{
		final DirectedSparseGraph graph = buildGraph(g,"actual graph",conf);
		checkM(fsm,graph,conf);
	}
	
	/** Verifies the reduction relation between two graphs.
	 */
	public static void checkReduction(String fsm,String g,Configuration conf)
	{
		final LearnerGraph graph = new LearnerGraph(buildGraph(g,"actual graph",conf),conf);
		final LearnerGraph expected = buildLearnerGraph(fsm,"expected graph",conf);
		
		DifferentFSMException 
			exReduction = WMethod.checkReduction(expected, expected.init, graph, graph.init);
		
		if (exReduction != null)
		{// if not a reduction, cannot be an equivalence
			DifferentFSMException 
				ex1 = WMethod.checkM(expected,graph),
				ex2 = WMethod.checkM(graph,expected);
			Assert.assertNotNull(ex1);
			Assert.assertNotNull(ex2);
		}

		if (exReduction != null)
			throw exReduction;
	}
	
	@Test
	public void testCheckM1()
	{
		String graphA = "B-a->C-b->D",graphB="A-a->B-b->C";
		checkM(graphA,graphB,config);checkReduction(graphA, graphB, config);
	}
	
	@Test
	public void testCheckM2()
	{
		String graphA = "B-a->C-b->D\nB-b-#REJ\nD-d-#REJ",graphB = "A-a->B-b->C-d-#F#-b-A";
		checkM(graphA,graphB,config);checkReduction(graphA, graphB, config);
	}

	@Test
	public void testCheckM3()
	{
		String another  = "A-a->B-b->C\nC-b-#REJ\nA-d-#REJ";
		String expected = "A-a->B-b->C-b-#F#-d-A";
		
		String graphB = another.replace('A', 'Q').replace('B', 'G').replace('C', 'A');
		checkM(expected,graphB, config);checkReduction(expected,graphB, config);
	}

	/** multiple reject states. */
	@Test
	public void testCheckM4()
	{
		String another  = "A-a->B-b->C\nC-b-#REJ\nA-d-#REJ\nA-b-#REJ2\nB-a-#REJ2\nB-c-#REJ3";
		String expected = "A-a->B-b->C-b-#F#-d-A-b-#R\nB-a-#R\nU#-c-B";
		
		String graphB = another.replace('A', 'Q').replace('B', 'G').replace('C', 'A');
		checkM(expected,graphB, config);checkReduction(expected,graphB, config);
	}

	/** multiple reject states and a non-deterministic graph. */
	@Test
	public void testCheckM4_ND()
	{
		String another  = "A-a->B-b->C\nC-b-#REJ\nA-d-#REJ\nA-b-#REJ2\nB-a-#REJ2\nB-c-#REJ3\n"+
			"A-d-#REJ\nB-c-#REJ2";
		String expected = "A-a->B-b->C-b-#F#-d-A-b-#R\nB-a-#R\nU#-c-B\n"+
			"A-d-#F\nB-c-#R";
		checkM_ND(expected,another.replace('A', 'Q').replace('B', 'G').replace('C', 'A'), config);
	}

	@Test
	public void testCheckM5()
	{
		String graphA = "S-a->U<-b-U\nQ<-a-U", graphB = "A-a->B-b->B-a->C";
		checkM(graphA,graphB,config);checkReduction(graphA, graphB, config);
	}

	@Test
	public void testCheckM6()
	{
		final LearnerGraph graph = buildLearnerGraph("A-a->B-b->B-a->C", "testCheck6",config);
		final LearnerGraph expected = buildLearnerGraph("U<-b-U\nQ<-a-U<-a-S","expected graph",config);
		Assert.assertNull(WMethod.checkM(graph,graph.findVertex("A"),expected,expected.findVertex("S"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNull(WMethod.checkM(graph,graph.findVertex("B"),expected,expected.findVertex("U"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNull(WMethod.checkM(graph,graph.findVertex("C"),expected,expected.findVertex("Q"),WMethod.VERTEX_COMPARISON_KIND.NONE));

		Assert.assertNull(WMethod.checkReduction(graph,graph.findVertex("A"),expected,expected.findVertex("S")));
		Assert.assertNull(WMethod.checkReduction(graph,graph.findVertex("B"),expected,expected.findVertex("U")));
		Assert.assertNull(WMethod.checkReduction(graph,graph.findVertex("C"),expected,expected.findVertex("Q")));
	}

	@Test
	public final void testCheckM_multipleEq1() // equivalent states
	{
		final LearnerGraph graph = buildLearnerGraph("S-a->A\nS-b->B\nS-c->C\nS-d->D\nS-e->E\nS-f->F\nS-h->H-d->H\nA-a->A1-b->A2-a->K1-a->K1\nB-a->B1-b->B2-b->K1\nC-a->C1-b->C2-a->K2-b->K2\nD-a->D1-b->D2-b->K2\nE-a->E1-b->E2-a->K3-c->K3\nF-a->F1-b->F2-b->K3","testCheckM_multipleEq1",config);
		Assert.assertNull(WMethod.checkM(graph,graph.findVertex("D"),graph,graph.findVertex("C2"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNull(WMethod.checkM(graph,graph.findVertex("C2"),graph,graph.findVertex("D"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		
		Assert.assertNull(WMethod.checkM(graph,graph.findVertex("D1"),graph,graph.findVertex("D2"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNull(WMethod.checkM(graph,graph.findVertex("D2"),graph,graph.findVertex("D1"),WMethod.VERTEX_COMPARISON_KIND.NONE));

		Assert.assertNull(WMethod.checkM(graph,graph.findVertex("D2"),graph,graph.findVertex("K2"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNull(WMethod.checkM(graph,graph.findVertex("K2"),graph,graph.findVertex("D2"),WMethod.VERTEX_COMPARISON_KIND.NONE));

		Assert.assertNotNull(WMethod.checkM(graph,graph.findVertex("D2"),graph,graph.findVertex("A1"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNotNull(WMethod.checkM(graph,graph.findVertex("A1"),graph,graph.findVertex("D2"),WMethod.VERTEX_COMPARISON_KIND.NONE));

		Assert.assertNotNull(WMethod.checkM(graph,graph.findVertex("D2"),graph,graph.findVertex("F1"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNotNull(WMethod.checkM(graph,graph.findVertex("F1"),graph,graph.findVertex("D2"),WMethod.VERTEX_COMPARISON_KIND.NONE));

		Assert.assertNull(WMethod.checkReduction(graph,graph.findVertex("D"),graph,graph.findVertex("C2")));
		Assert.assertNull(WMethod.checkReduction(graph,graph.findVertex("C2"),graph,graph.findVertex("D")));
		
		Assert.assertNull(WMethod.checkReduction(graph,graph.findVertex("D1"),graph,graph.findVertex("D2")));
		Assert.assertNull(WMethod.checkReduction(graph,graph.findVertex("D2"),graph,graph.findVertex("D1")));

		Assert.assertNull(WMethod.checkReduction(graph,graph.findVertex("D2"),graph,graph.findVertex("K2")));
		Assert.assertNull(WMethod.checkReduction(graph,graph.findVertex("K2"),graph,graph.findVertex("D2")));

		Assert.assertNotNull(WMethod.checkReduction(graph,graph.findVertex("D2"),graph,graph.findVertex("A1")));
		Assert.assertNotNull(WMethod.checkReduction(graph,graph.findVertex("A1"),graph,graph.findVertex("D2")));

		Assert.assertNotNull(WMethod.checkReduction(graph,graph.findVertex("D2"),graph,graph.findVertex("F1")));
		Assert.assertNotNull(WMethod.checkReduction(graph,graph.findVertex("F1"),graph,graph.findVertex("D2")));
	}

	@Test
	public final void testCheckM_multipleEq2() // equivalent states
	{
		final DirectedSparseGraph g = buildGraph("S-a->A-a->D-a->D-b->A-b->B-a->D\nB-b->C-a->D\nC-b->D\nS-b->N-a->N-b->N","testCheckM_multipleEq2",config);
		final LearnerGraph graph = new LearnerGraph(g,Configuration.getDefaultConfiguration());
		List<String> states = Arrays.asList(new String[]{"S","A","B","C","D","N"});
		for(String stA:states)
			for(String stB:states)
			{
				Assert.assertNull("states "+stA+"and "+stB+" should be equivalent",
						WMethod.checkM(graph,graph.findVertex(stA),graph,graph.findVertex(stB),WMethod.VERTEX_COMPARISON_KIND.NONE));
				Assert.assertNull("states "+stA+"and "+stB+" should be equivalent",
						WMethod.checkReduction(graph,graph.findVertex(stA),graph,graph.findVertex(stB)));
			}
	}
	
	@Test
	public final void testCheckM_multipleEq3() // equivalent states
	{
		final DirectedSparseGraph g = buildGraph("S-a->A-a->D-a->D-b->A-b->B-a->D\nB-b->C-a->D\nC-b->D\nS-b->N-a->M-a->N\nN-b->M-b->N","testCheckM_multipleEq3",config);
		final LearnerGraph graph = new LearnerGraph(g,Configuration.getDefaultConfiguration());
		List<String> states = Arrays.asList(new String[]{"S","A","B","C","D","N","M"});
		for(String stA:states)
			for(String stB:states)
			{
				Assert.assertNull("states "+stA+"and "+stB+" should be equivalent",
						WMethod.checkM(graph,graph.findVertex(stA),graph,graph.findVertex(stB),WMethod.VERTEX_COMPARISON_KIND.NONE));
				Assert.assertNull("states "+stA+"and "+stB+" should be equivalent",
						WMethod.checkReduction(graph,graph.findVertex(stA),graph,graph.findVertex(stB)));
			}
	}
	
	@Test
	public final void testCheckM_multipleEq4() // non-equivalent states
	{
		final DirectedSparseGraph g = buildGraph("A-a->B-a->C-a->A-b->C-b->B","testCheckM_multipleEq4",config);
		final LearnerGraph graph = new LearnerGraph(g,Configuration.getDefaultConfiguration());
		List<String> states = Arrays.asList(new String[]{"A","B","C"});
		for(String stA:states)
			for(String stB:states)
				if (stA.equals(stB))
				{
					Assert.assertNull("states "+stA+" and "+stB+" should be equivalent",
							WMethod.checkM(graph,graph.findVertex(stA),graph,graph.findVertex(stB),WMethod.VERTEX_COMPARISON_KIND.NONE));
					Assert.assertNull("states "+stA+" and "+stB+" should be equivalent",
							WMethod.checkReduction(graph,graph.findVertex(stA),graph,graph.findVertex(stB)));
				}
				else
				{
					Assert.assertNotNull("states "+stA+" and "+stB+" should not be equivalent",
							WMethod.checkM(graph,graph.findVertex(stA),graph,graph.findVertex(stB),WMethod.VERTEX_COMPARISON_KIND.NONE));
					Assert.assertNotNull("states "+stA+" and "+stB+" should not be equivalent",
							WMethod.checkReduction(graph,graph.findVertex(stA),graph,graph.findVertex(stB)));
				}
	}
	
	@Test
	public void testCheckM6_f1()
	{
		final LearnerGraph graph = buildLearnerGraph("A-a->B-b->B-a->C", "testCheck6", Configuration.getDefaultConfiguration());
		final LearnerGraph expected = buildLearnerGraph("U<-b-U\nQ<-a-U<-a-S","expected graph",Configuration.getDefaultConfiguration());
		Assert.assertNull(WMethod.checkM(graph,graph.findVertex("A"),graph,graph.findVertex("A"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNull(WMethod.checkM(graph,graph.findVertex("B"),graph,graph.findVertex("B"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNull(WMethod.checkM(graph,graph.findVertex("C"),graph,graph.findVertex("C"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNull(WMethod.checkM(expected,expected.findVertex("Q"),expected,expected.findVertex("Q"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNull(WMethod.checkM(expected,expected.findVertex("S"),expected,expected.findVertex("S"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		
		Assert.assertNotNull(WMethod.checkM(graph,graph.findVertex("A"),expected,expected.findVertex("Q"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNotNull(WMethod.checkM(graph,graph.findVertex("A"),expected,expected.findVertex("U"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNotNull(WMethod.checkM(graph,graph.findVertex("B"),expected,expected.findVertex("Q"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNotNull(WMethod.checkM(graph,graph.findVertex("B"),expected,expected.findVertex("S"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNotNull(WMethod.checkM(graph,graph.findVertex("C"),expected,expected.findVertex("U"),WMethod.VERTEX_COMPARISON_KIND.NONE));
		Assert.assertNotNull(WMethod.checkM(graph,graph.findVertex("C"),expected,expected.findVertex("S"),WMethod.VERTEX_COMPARISON_KIND.NONE));

		Assert.assertNull(WMethod.checkReduction(graph,graph.findVertex("A"),graph,graph.findVertex("A")));
		Assert.assertNull(WMethod.checkReduction(graph,graph.findVertex("B"),graph,graph.findVertex("B")));
		Assert.assertNull(WMethod.checkReduction(graph,graph.findVertex("C"),graph,graph.findVertex("C")));
		Assert.assertNull(WMethod.checkReduction(expected,expected.findVertex("Q"),expected,expected.findVertex("Q")));
		Assert.assertNull(WMethod.checkReduction(expected,expected.findVertex("S"),expected,expected.findVertex("S")));
		
		// Some of the reductions hold even though equivalences do not
		Assert.assertNull	(WMethod.checkReduction(graph,graph.findVertex("A"),expected,expected.findVertex("Q")));
		Assert.assertNotNull(WMethod.checkReduction(expected,expected.findVertex("Q"),graph,graph.findVertex("A")));

		Assert.assertNotNull(WMethod.checkReduction(graph,graph.findVertex("A"),expected,expected.findVertex("U")));
		Assert.assertNotNull(WMethod.checkReduction(expected,expected.findVertex("U"),graph,graph.findVertex("A")));

		Assert.assertNull	(WMethod.checkReduction(graph,graph.findVertex("B"),expected,expected.findVertex("Q")));
		Assert.assertNotNull(WMethod.checkReduction(expected,expected.findVertex("Q"),graph,graph.findVertex("B")));
		
		Assert.assertNotNull(WMethod.checkReduction(graph,graph.findVertex("B"),expected,expected.findVertex("S")));
		Assert.assertNotNull(WMethod.checkReduction(expected,expected.findVertex("S"),graph,graph.findVertex("B")));

		Assert.assertNotNull(WMethod.checkReduction(graph,graph.findVertex("C"),expected,expected.findVertex("U")));
		Assert.assertNull	(WMethod.checkReduction(expected,expected.findVertex("U"),graph,graph.findVertex("C")));

		Assert.assertNotNull(WMethod.checkReduction(graph,graph.findVertex("C"),expected,expected.findVertex("S")));
		Assert.assertNull	(WMethod.checkReduction(expected,expected.findVertex("S"),graph,graph.findVertex("C")));
	}
	

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD1a()
	{
		checkM("B-a->C-b->B","A-a->B-b->C", config);
	}

	public void testCheckMD1b()
	{
		checkReduction("B-a->C-b->B","A-a->B-b->C", config);
	}

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD1c()
	{
		checkReduction("A-a->B-b->C","B-a->C-b->B", config);
	}

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD2a() // different reject states
	{
		checkM("B-a->C-b-#D","A-a->B-b->C", config);
	}

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD2b() // different reject states
	{
		checkReduction("B-a->C-b-#D","A-a->B-b->C", config);
	}

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD2c() // different reject states
	{
		checkReduction("A-a->B-b->C", "B-a->C-b-#D", config);
	}

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD3a() // missing transition
	{
		checkM("B-a->C-b->D","A-a->B-b->C\nA-b->B", config);
	}

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD3b() // missing transition
	{
		checkReduction("B-a->C-b->D","A-a->B-b->C\nA-b->B", config);
	}

	@Test
	public void testCheckMD3c() // missing transition
	{
		checkReduction("A-a->B-b->C\nA-b->B", "B-a->C-b->D", config);
	}

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD4() // extra transition
	{
		checkM("B-a->C-b->D\nB-b->C","A-a->B-b->C", config);
	}

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD5a() // missing transition
	{
		checkM("B-a->C-b->D","A-a->B-b->C\nB-c->B", config);
	}

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD5b() // missing transition
	{
		checkReduction("B-a->C-b->D","A-a->B-b->C\nB-c->B", config);
	}

	@Test
	public void testCheckMD5c() // missing transition
	{
		checkReduction("A-a->B-b->C\nB-c->B", "B-a->C-b->D",config);
	}

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD6() // extra transition
	{
		checkM("B-a->C-b->D\nC-c->C","A-a->B-b->C", config);
	}

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD7a() // swapped transitions
	{
		String another  = "A-a->B-b->C\nC-b-#REJ\nA-d-#REJ";
		String expected = "A-a->B-b->C-d-#F#-b-A";
		checkM(expected,another.replace('A', 'Q').replace('B', 'G').replace('C', 'A'), config);
	}

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD7b() // swapped transitions
	{
		String another  = "A-a->B-b->C\nC-b-#REJ\nA-d-#REJ";
		String expected = "A-a->B-b->C-d-#F#-b-A";
		checkReduction(expected,another, config);
	}

	@Test(expected = DifferentFSMException.class)
	public void testCheckMD7c() // swapped transitions
	{
		String another  = "A-a->B-b->C\nC-b-#REJ\nA-d-#REJ";
		String expected = "A-a->B-b->C-d-#F#-b-A";
		checkReduction(another, expected, config);
	}

	/** Tests the correctness of handling of the association of pairs, first with simple graphs and no pairs. */
	@Test
	public void testPair1()
	{
		checkM("A-a->B-a->C-a->D-a->A","A-a->B-a->A", config);
	}
	
	/** Tests the correctness of handling of the association of pairs, first with simple graphs and no pairs. */
	@Test(expected = DifferentFSMException.class)
	public void testPair2()
	{
		checkM("A-a->B-a->C / A=INCOMPATIBLE=B","A-a->B-a->C", config);
	}
	
	/** Tests the correctness of handling of the association of pairs, first with simple graphs and no pairs. */
	@Test
	public void testPair3()
	{
		checkM("A-a->B-a->C / A=INCOMPATIBLE=B","A-a->B-a->C/ A=INCOMPATIBLE=B", config);
	}
	
	/** Tests the correctness of handling of the association of pairs. */
	@Test(expected = DifferentFSMException.class)
	public void testPair4()
	{
		checkM("A-a->B-a->C / A=INCOMPATIBLE=B","A-a->B-a->C/ A=THEN=B", config);
	}
	
	/** Tests the correctness of handling of the association of pairs. */
	@Test
	public void testPair5()
	{
		checkM("A-a->A-c->B-b->B / A=INCOMPATIBLE=B",
				"A1-a->A2-a->A3 / A1-c->B1-b->B1 / A2-c->B2-b->B2 / A3-a->A3-c->B3-b->B3 "+
					" / A1=INCOMPATIBLE = B1 / A2 = INCOMPATIBLE = B1 / A3 = INCOMPATIBLE = B1"+
					" / A1=INCOMPATIBLE = B2 / A2 = INCOMPATIBLE = B2 / A3 = INCOMPATIBLE = B2"+
					" / A1=INCOMPATIBLE = B3 / A2 = INCOMPATIBLE = B3 / A3 = INCOMPATIBLE = B3", config);
	}
	
	/** Tests the correctness of handling of the association of pairs. */
	@Test(expected = DifferentFSMException.class)
	public void testPair6()
	{
		checkM("A-a->A-c->B-b->B / A=INCOMPATIBLE=B",
				"A1-a->A2-a->A3 / A1-c->B1-b->B1 / A2-c->B2-b->B2 / A3-a->A3-c->B3-b->B3 "+
					" / A1=INCOMPATIBLE = B1 / A2 = INCOMPATIBLE = B1 / A3 = INCOMPATIBLE = B1"+
					" / A1=INCOMPATIBLE = B2 / A2 = THEN = B2 / A3 = INCOMPATIBLE = B2"+
					" / A1=INCOMPATIBLE = B3 / A2 = INCOMPATIBLE = B3 / A3 = INCOMPATIBLE = B3", config);
	}
	
	/** Tests the correctness of handling of the association of pairs. */
	@Test(expected = DifferentFSMException.class)
	public void testPair7a()
	{
		checkM("A-a->A-c->B-b->B / A=INCOMPATIBLE=B",
				"A1-a->A2-a->A3 / A1-c->B1-b->B1 / A2-c->B2-b->B2 / A3-a->A3-c->B3-b->B3 "+
					" / A1=INCOMPATIBLE = B1 / A2 = INCOMPATIBLE = B1 / A3 = INCOMPATIBLE = B1"+
					" / A1=INCOMPATIBLE = B2 / A2 = INCOMPATIBLE = B2 / A3 = INCOMPATIBLE = B2"+
					" / A1=INCOMPATIBLE = B3 / A2 = INCOMPATIBLE = B3 / A3 = THEN = B3",  config);
	}
	
	/** Tests the correctness of handling of the association of pairs. */
	@Test(expected = DifferentFSMException.class)
	public void testPair7b1()
	{
		checkM("A-a->A / B-b->B / A=INCOMPATIBLE=B",
				"A1-a->A2-a->A3 / B1-b->B1 / B2-b->B2 / A3-a->A3 / B3-b->B3 "+
					" / A1=INCOMPATIBLE = B1 / A2 = INCOMPATIBLE = B1 / A3 = INCOMPATIBLE = B1"+
					" / A1=INCOMPATIBLE = B2 / A2 = INCOMPATIBLE = B2 / A3 = INCOMPATIBLE = B2"+
					" / A1=INCOMPATIBLE = B3 / A2 = INCOMPATIBLE = B3", config);
	}
	
	/** Tests the correctness of handling of the association of pairs. */
	@Test(expected = DifferentFSMException.class)
	public void testPair7b2()
	{
		checkM("A-a->A-c->B-b->B / A=INCOMPATIBLE=B",
				"A1-a->A2-a->A3 / A1-c->B1-b->B1 / A2-c->B2-b->B2 / A3-a->A3-c->B3-b->B3 "+
					" / A1=INCOMPATIBLE = B1 / A2 = INCOMPATIBLE = B1 / A3 = INCOMPATIBLE = B1"+
					" / A1=INCOMPATIBLE = B2 / A2 = INCOMPATIBLE = B2 / A3 = INCOMPATIBLE = B2"+
					" / A1=INCOMPATIBLE = B3 / A2 = INCOMPATIBLE = B3", config);
	}
	
	/** Tests the correctness of handling of the association of pairs. */
	@Test(expected = DifferentFSMException.class)
	public void testPair7c()
	{
		checkM("A-a->A-c->B-b->B / A=INCOMPATIBLE=B",
				"A1-a->A2-a->A3 / A1-c->B1-b->B1 / A2-c->B2-b->B2 / A3-a->A3-c->B3-b->B3 "+
					" / A1=INCOMPATIBLE = B1 / A2 = INCOMPATIBLE = B1 / A3 = INCOMPATIBLE = B1"+
					" / A1=INCOMPATIBLE = B2 / A3 = INCOMPATIBLE = B2"+
					" / A1=INCOMPATIBLE = B3 / A2 = INCOMPATIBLE = B3 / A3 = THEN = B3", config);
	}
	
	@Test
	public void testPair8()
	{
		checkM("P-a->A-a->C-a->C / A=INCOMPATIBLE=P / C=INCOMPATIBLE=P",
				"Q-a->B-a->B / B = INCOMPATIBLE = Q", config);
	}
	
	@Test
	public void testPair9()
	{
		checkM("R-b->P-a->A-a->C-a->C / A=INCOMPATIBLE=P / C=INCOMPATIBLE=P / P = THEN = R",
				"S-b->Q-a->B-a->B / B = INCOMPATIBLE = Q / S = THEN = Q", config);
	}
	
	@Test(expected = DifferentFSMException.class)
	public void testPair10()
	{
		checkM("R-b->P-a->A-a->C-a->C / A=INCOMPATIBLE=P / C=INCOMPATIBLE=P / P = INCOMPATIBLE = R",
				"S-b->Q-a->B-a->B / B = INCOMPATIBLE = Q / S = THEN = Q", config);
	}
	

}
