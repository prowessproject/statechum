package statechum.analysis.learning.experiments;

import edu.uci.ics.jung.graph.impl.*;
import edu.uci.ics.jung.graph.*;
import edu.uci.ics.jung.utils.*;
import edu.uci.ics.jung.statistics.*;
import edu.uci.ics.jung.algorithms.shortestpath.DijkstraDistance;

import java.util.*;

import statechum.JUConstants;
import statechum.analysis.learning.RPNIBlueFringeLearner;
import statechum.analysis.learning.TestFSMAlgo;
import statechum.analysis.learning.TestFSMAlgo.FSMStructure;
import statechum.xmachine.model.testset.*;

public class RandomPathGenerator {
	
	protected DirectedSparseGraph g;
	private PTASequenceSet sPlus;
	
	/** The random number generator passed in is used to generate walks; one can pass a mock in order to 
	 * produce walks devised by a tester. Note that the object will be modified in the course of walks thanks
	 * to java's Random being non-serialisable.
	 *  
	 * @param baseGraph
	 * @param randomGenerator
	 * @param extraToDiameter the length of paths will be diameter plus this value.
	 */ 
	public RandomPathGenerator(DirectedSparseGraph baseGraph, Random randomGenerator, int extraToDiameter) {
		pathRandomNumberGenerator = randomGenerator;
		//sPlus = new LinkedList<List<String>>();
		sPlus = new PTASequenceSet();
		g = baseGraph;
		DijkstraDistance dd = new DijkstraDistance(baseGraph);
		Collection<Double> distances = dd.getDistanceMap(RPNIBlueFringeLearner.findVertex(JUConstants.PROPERTY, "init", g)).values();
		ArrayList<Double> distancesList = new ArrayList<Double>(distances);
		Collections.sort(distancesList);
		int diameter = distancesList.get(distancesList.size()-1).intValue();
		int size = g.getEdges().size();
		this.populateRandomWalksC(4*size, diameter+extraToDiameter);
	}
	
	private Set<String> getOutgoingSymbols(Vertex v){
		Set<String> outSymbols = new HashSet<String>();
		Iterator<Edge> outEdges = v.getOutEdges().iterator();
		while (outEdges.hasNext()) {
			Edge e = outEdges.next();
			Set<String> labels = (Set<String>)e.getUserDatum(JUConstants.LABEL);
			outSymbols.addAll(labels);
		}
		return outSymbols;
	}
	
	private void populateRandomWalks(int number, int maxLength){
		int counter = 0;
		Set<String> doneStrings = new HashSet<String>();
		Vertex init = RPNIBlueFringeLearner.findVertex(JUConstants.PROPERTY, "init", g);
		while(counter<number){
			List<String> path = new LinkedList<String>();
			String s = "";
			Vertex current = init;
			for(int i=0;i<maxLength;i++){
				if(current.outDegree()==0)
					break;
				String currentString= pickRandom(current);
				s = s.concat(currentString);
				path.add(currentString);
				Vertex exists = RPNIBlueFringeLearner.getVertex(g, path);
				if(!doneStrings.contains(s)){
					sPlus.add(new ArrayList(path));
					doneStrings.add(s);
					counter++;
				}
				current = exists;
			}
		}
	}

	private void populateRandomWalksC(int number, int maxLength){
		int counter=0, unsucc = 0;
		FSMStructure fsm = WMethod.getGraphData(g);
		while(counter<number){
			List<String> path = new ArrayList<String>(maxLength);
			String current = fsm.init;
			if(unsucc>100)
				return;
			for(int i=0;i<maxLength;i++){
				Map<String,String> row = fsm.trans.get(current);
				if(row.isEmpty())
					break;
				String nextInput= (String)pickRandom(row.keySet());
				path.add(nextInput);
				
				//if(!sPlus.contains(path)){
				int oldSize = sPlus.size();	
				sPlus.add(new ArrayList<String>(path));
				if(sPlus.size()>oldSize){
					counter++;
					unsucc=0;
				}
				else
					unsucc++;
				current = row.get(nextInput);
			}
		}
	}

	private String pickRandom(Vertex v){
		Set<String> labels;
		Edge e = (Edge)pickRandom(v.getOutEdges());
		labels = (Set<String>)e.getUserDatum(JUConstants.LABEL);
		return pickRandom(labels).toString();
	}

	private final Random pathRandomNumberGenerator; 
	
	private Object pickRandom(Collection c){
		Object[] array = c.toArray();
		if(array.length==1)
			return array[0];
		else{
			int random = pathRandomNumberGenerator.nextInt(array.length);
			return array[random];
		}
	}

	public Collection<List<String>> getSPlus() {
		return sPlus;
	}
	
	public Collection<List<String>> getAllPaths(){
		Collection<List<String>> allPaths = new LinkedList<List<String>>();
		allPaths.addAll(sPlus.getData());
		return allPaths;
	}
	
	

}