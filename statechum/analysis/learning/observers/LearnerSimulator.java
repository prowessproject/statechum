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

package statechum.analysis.learning.observers;

import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Assert;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import statechum.JUConstants;
import statechum.Pair;
import statechum.analysis.learning.PairScore;
import statechum.analysis.learning.StatePair;
import statechum.analysis.learning.rpnicore.GD;
import statechum.analysis.learning.rpnicore.LearnerGraph;
import statechum.analysis.learning.rpnicore.Transform;
import statechum.analysis.learning.rpnicore.WMethod;
import statechum.model.testset.PTASequenceEngine;

/** An instance of this class behaves like a learner including calls to its decorators, 
 * but instead of running an experiment all data is retrieved from the supplied
 * XML file. This is useful in order to transform existing traces from a learner 
 * or make analysis of what it was doing, without actually re-running an experiment
 * which may be time-consuming. 
 *  
 * @author kirill
 */
public class LearnerSimulator extends ProgressDecorator implements Learner 
{
	protected int childOfTopElement =0;
	NodeList childElements = null;

	/** Graph compressor. */
	protected GraphSeries series = null;

	/** This method is aimed for loading an XML file with a fixed structure.
	 * Every time <em>expectNextElement</em> is called a caller expects that
	 * the next element in XML will have a specific tag and this method throws
	 * {@link IllegalArgumentException} if this is not the case.
	 * <p>Short description:
	 * extracts next element from the collection of children of the top-level one.
	 * Text nodes with whitespace are ignored.
	 * 
	 * @param name expected name, exception otherwise
	 * @return element
	 */ 
	public Element expectNextElement(String name)
	{
		org.w3c.dom.Node result = null;
		do
		{
			if (childOfTopElement >= childElements.getLength())
				throw new IllegalArgumentException("failed to find element called "+name);
			result = childElements.item(childOfTopElement++);
		}
		while(result.getNodeType() == org.w3c.dom.Node.TEXT_NODE);
		if (!name.equals(result.getNodeName()))
			throw new IllegalArgumentException("encountered "+result.getNodeName()+" instead of "+name);

		return (Element)result;
	}
	
	/** Loads the next element from XML file. Returns <em>null</em> if there are 
	 * no more elements.
	 * Text nodes are ignored.
	 */
	public Element getNextElement()
	{
		org.w3c.dom.Node result = null;
		do
		{
			result = childElements.item(childOfTopElement++);
		}
		while(childOfTopElement < childElements.getLength() &&
				result.getNodeType() == org.w3c.dom.Node.TEXT_NODE);
		
		if (childOfTopElement >= childElements.getLength())
			return null;
		
		return (Element)result;
	}
	
	/** Loads an XML from the supplied reader and returns the <em>Document</em> corresponding
	 * to it.
	 * 
	 * @param inputReader the reader from which to load XML
	 * @return XML document.
	 */
	public static Document getDocumentOfXML(Reader inputReader)
	{
		Document result = null;
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try
		{
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);factory.setValidating(false);// we do not have a schema to validate against-this does not seem necessary for the simple data format we are considering here.
			result = factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(inputReader));
		}
		catch(Exception e)
		{
			statechum.Helper.throwUnchecked("failed to construct/load DOM document",e);
		}
		return result;
	}
	
	public LearnerSimulator(Reader inputReader) 
	{
		super(null);decoratedLearner=this;
		doc = getDocumentOfXML(inputReader);childElements = doc.getDocumentElement().getChildNodes();
	}
	
	protected Learner topLevelListener = this;
	
	/** Sets the highest listener to receive notification calls; 
	 * it is expected that listeners will propagate calls down the chain
	 * so that this learner eventually gets to execute its own methods.
	 * 
	 * @param top new top of the stack of listeners.
	 */
	public void setTopLevelListener(Learner top)
	{
		topLevelListener = top;
	}

	/** The element corresponding to the current method call. */
	protected Element currentElement = null;

	@Override
	public LearnerGraph learnMachine()
	{
		currentElement = expectNextElement(ELEM_KINDS.ELEM_INIT.name());
		LearnerGraph graph = null, temp = null, result = null;
		while(currentElement != null)
		{
			final String elemName = currentElement.getNodeName();
			if (result != null) // we already know the final graph but there are more elements to come
				throw new IllegalArgumentException("unexpected element "+elemName+" after the learner result is known");
			if (elemName.equals(ELEM_KINDS.ELEM_ANSWER.name()))
			{
				List<String> question = readInputSequence(new java.io.StringReader(currentElement.getAttribute(ELEM_KINDS.ATTR_QUESTION.name())),-1);
				Object outcome = topLevelListener.CheckWithEndUser(graph, question, null);
				assert outcome == expectedReturnValue;// yes, this should be b
			} else
				if (elemName.equals(ELEM_KINDS.ELEM_PAIRS.name()))
				{
					topLevelListener.ChooseStatePairs(graph);
				}
				else
					if (elemName.equals(ELEM_KINDS.ELEM_QUESTIONS.name()))
					{
						checkSingles(currentElement, childrenQuestions);
						topLevelListener.ComputeQuestions(readPair(graph, getElement(ELEM_KINDS.ELEM_PAIR.name())),graph,temp);
					}
					else if (elemName.equals(ELEM_KINDS.ELEM_MERGEANDDETERMINIZE.name()))
					{
						if (currentElement.getElementsByTagName(ELEM_KINDS.ELEM_PAIR.name()).getLength() != 1)
							throw new IllegalArgumentException("missing or duplicate pair");
						
						temp = topLevelListener.MergeAndDeterminize(graph, readPair(graph, getElement(ELEM_KINDS.ELEM_PAIR.name())));
					}
					else if (elemName.equals(Transform.graphmlNodeName) ||
							elemName.equals(GD.ChangesRecorder.gdGD))
					{
						String graphKind = currentElement.getAttribute(ELEM_KINDS.ATTR_GRAPHKIND.name());
						if (graphKind.equals(ELEM_KINDS.ATTR_LEARNINGOUTCOME.name()))
								result = series.readGraph(currentElement);
						else
							throw new IllegalArgumentException("unexpected kind of graph: "+graphKind);
					}
					else if (elemName.equals(ELEM_KINDS.ELEM_RESTART.name()))
					{
						if (!currentElement.hasAttribute(ELEM_KINDS.ATTR_KIND.name())) throw new IllegalArgumentException("absent KIND attribute on RESTART");
						String restartKind = currentElement.getAttribute(ELEM_KINDS.ATTR_KIND.name());
						RestartLearningEnum mode = Enum.valueOf(RestartLearningEnum.class, restartKind);
						topLevelListener.Restart(mode);
						if (mode == RestartLearningEnum.restartNONE)
							graph = temp;
						// if we are restarting, graph is unchanged.
					}
					else if (elemName.equals(ELEM_KINDS.ELEM_INIT.name()))
					{
						InitialData initial = readInitialData(currentElement);
						graph = topLevelListener.init(initial.plus,initial.minus);
					}
					else if (elemName.equals(ELEM_KINDS.ELEM_AUGMENTPTA.name()))
					{
						AugmentPTAData augmentData = readAugmentPTA(currentElement);
						topLevelListener.AugmentPTA(null, augmentData.kind, augmentData.sequence, augmentData.accept, augmentData.colour);
					}
				else throw new IllegalArgumentException("Unknown element in XML file "+elemName);
			currentElement = getNextElement();
		}
		
		return result;
	}

	/** Ideally, We'd like to detect whether decorators change our return values - 
	 * we cannot accommodate changes because we are only playing back rather 
	 * than doing learning. I think even user-abort should not be responded because
	 * our trace may include that one too, at least in principle. 
	 * Some changes can be easily detected,
	 * others, such as changes to mutable objects like graphs are perhaps not worth it.
	 */ 
	protected Object expectedReturnValue = null;
	
	/** Simulated check.
	 * @param g estimated graph, not loaded.
	 * @param question question loaded from XML
	 * @param options set to null by the simulator.
	 * @return value loaded from XML
	 */
	public Pair<Integer,String> CheckWithEndUser(@SuppressWarnings("unused") LearnerGraph g, 
			@SuppressWarnings("unused")	List<String> question, Object[] options) 
	{
		Integer failedPosition = Integer.valueOf(currentElement.getAttribute(ELEM_KINDS.ATTR_FAILEDPOS.name()));
		String ltlValue = null;
		if (currentElement.hasAttribute(ELEM_KINDS.ATTR_LTL.name())) ltlValue = currentElement.getAttribute(ELEM_KINDS.ATTR_LTL.name());
		Pair<Integer,String> returnValue = new Pair<Integer,String>(failedPosition,ltlValue);expectedReturnValue=returnValue;
		return returnValue;
	}

	/** Called by the simulator.
	 * 
	 * @param graph estimated graph
	 * @return loaded values from XML.
	 */
	public Stack<PairScore> ChooseStatePairs(LearnerGraph graph)
	{
		org.w3c.dom.NodeList Pairs = currentElement.getChildNodes();
		Stack<PairScore> result = new Stack<PairScore>();
		for(int i=0;i<Pairs.getLength();++i)
		{
			org.w3c.dom.Node pair = Pairs.item(i);
			if (pair.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE)
			{
				Element elem = (Element) pair;
				if (!elem.getNodeName().equals(ELEM_KINDS.ELEM_PAIR.name()))
						throw new IllegalArgumentException("unexpected node "+pair.getNodeName()+" among pairs");
				
				result.add(readPair(graph, elem));
			}
		}
		return result;
	}

	/** Called by the simulator.
	 * 
	 * @param pair loaded from XML.
	 * @param original estimated value.
	 * @param temp estimated value.
	 * @return loaded from XML.
	 */
	public Collection<List<String>> ComputeQuestions(@SuppressWarnings("unused") PairScore pair, 
			@SuppressWarnings("unused")	LearnerGraph original, @SuppressWarnings("unused") LearnerGraph temp)
	{
		return readSequenceList(getElement(ELEM_KINDS.ELEM_SEQ.name()),ELEM_KINDS.ATTR_QUESTIONS.name());
	}

	/** Extracts the child of the current element with the provided name. 
	 * 
	 * @param name the name of the element to retrieve
	 * @return loaded element
	 */
	public Element getElement(String name)
	{
		return (Element)currentElement.getElementsByTagName(name).item(0);
	}

	final static Set<String> childrenQuestions;
	
	static
	{
		childrenQuestions = new TreeSet<String>();childrenQuestions.addAll(Arrays.asList(new String[]{ELEM_KINDS.ELEM_PAIR.name(),ELEM_KINDS.ELEM_SEQ.name()}));
	}
	
	/** Loads the current learner input parameters and makes sure they match the supplied parameters.
	 * If possible, this also loads the configuration and uses it for all methods requiring a configuration. 
	 */
	public void handleLearnerEvaluationData(LearnerGraph fsm, Collection<List<String>> testSet,Collection<String> ltl)
	{
		Element evaluationData = expectNextElement(ELEM_KINDS.ELEM_EVALUATIONDATA.name());
		LearnerEvaluationConfiguration cnf = readLearnerEvaluationConfiguration(evaluationData);
		config = cnf.config;
		series = new GraphSeries(config);
		if (cnf.ltlSequences != null) Assert.assertEquals(cnf.ltlSequences,ltl);
		else Assert.assertNull(ltl);
		WMethod.checkM(fsm, cnf.graph);
		Collection<List<String>> A = new LinkedList<List<String>>(),B = new LinkedList<List<String>>();
		A.addAll(cnf.testSet);B.addAll(testSet);
		Assert.assertEquals(A,B);
	}

	/** Returns the graph stored in XML.
	 * 
	 * @param original graph to be processed, the simulator attempts to supply a relevant value, however it is not certain to be correct.
	 * @param pair the pair to be merged. Loaded from XML file.
	 * @return graph loaded from XML file.
	 */
	public LearnerGraph MergeAndDeterminize(LearnerGraph original, @SuppressWarnings("unused") StatePair pair) 
	{
		Element graphNode = getElement(GD.ChangesRecorder.gdGD);
		if (graphNode == null) graphNode = getElement(Transform.graphmlNodeName);
		if (graphNode == null) throw new IllegalArgumentException("failed to find a node with a graph");
		return series.readGraph(graphNode);
	}

	/** Does nothing in the simulator. 
	 * 
	 * @param mode value loaded from XML.
	 */
	public void Restart(@SuppressWarnings("unused")	RestartLearningEnum mode) 
	{
	}

	/** We deliberately avoid storing this so as to be able to change 
	 * the format of diagnostics without having to regenerate test data. 
	 */
	public String getResult() 
	{
		return null;
	}

	/** Both arguments and the return value are stored by the simulator.
	 *  
	 * @param plus value loaded from XML
	 * @param minus value loaded from XML
	 */
	public LearnerGraph init(Collection<List<String>> plus, Collection<List<String>> minus) 
	{
		InitialData initial = readInitialData(currentElement);// wastefully load the element once again - does not matter because this is done very infrequently
		return initial.graph;
	}

	/** Since the learner does not know that the answer should be, we cannot 
	 * easily reconstruct the PTAEngine which is expected to be parameterised
	 * by the automaton. For this reason, we only store the corresponding collections and the 
	 * expected sizes in the xml. If called, this method will throw unsupported exception.
	 */
	public LearnerGraph init(@SuppressWarnings("unused") PTASequenceEngine engine, 
			@SuppressWarnings("unused")	int plusSize, 
			@SuppressWarnings("unused")	int minusSize) 
	{
		throw new UnsupportedOperationException("only init with collections is supported");
	}

	/** Does nothing in the simulator.
	 * 
	 * @param pta is always null in the simulator.
	 * @param ptaKind loaded from XML.
	 * @param sequence loaded from XML.
	 * @param accepted loaded from XML.
	 * @param newColour loaded from XML.
	 */
	public void AugmentPTA(@SuppressWarnings("unused") LearnerGraph pta, 
			@SuppressWarnings("unused") RestartLearningEnum ptaKind, 
			@SuppressWarnings("unused") List<String> sequence, 
			@SuppressWarnings("unused") boolean accepted, 
			@SuppressWarnings("unused") JUConstants newColour) 
	{
		
	}

	/** Since this is a simulator, values of the collections passed are ignored.
	 */
	@Override
	public LearnerGraph learnMachine(@SuppressWarnings("unused") Collection<List<String>> plus, 
			@SuppressWarnings("unused")	Collection<List<String>> minus)
	{
		return learnMachine();
	}

	/** Since this is a simulator, values of the collections passed are ignored.
	 */
	@Override
	public LearnerGraph learnMachine(@SuppressWarnings("unused") PTASequenceEngine engine, 
			@SuppressWarnings("unused") int plusSize, 
			@SuppressWarnings("unused") int minusSize)
	{
		return learnMachine();
	}
}