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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import edu.uci.ics.jung.graph.impl.DirectedSparseGraph;
import edu.uci.ics.jung.utils.UserData;

import statechum.Configuration;
import statechum.GlobalConfiguration;
import statechum.JUConstants;
import statechum.DeterministicDirectedSparseGraph.CmpVertex;
import statechum.DeterministicDirectedSparseGraph.DeterministicEdge;
import statechum.DeterministicDirectedSparseGraph.DeterministicVertex;
import statechum.DeterministicDirectedSparseGraph.VertexID;
import statechum.analysis.learning.rpnicore.AMEquivalenceClass.IncompatibleStatesException;
import statechum.model.testset.PTASequenceEngine;
import statechum.model.testset.PTASequenceSetAutomaton;

public class AbstractPathRoutines<TARGET_TYPE,CACHE_TYPE extends CachedData<TARGET_TYPE,CACHE_TYPE>> 
{
	final AbstractLearnerGraph<TARGET_TYPE,CACHE_TYPE> coregraph;

	/** Associates this object to AbstractLearnerGraph it is using for data to operate on. 
	 * Important: the constructor should not access any data in AbstractLearnerGraph 
	 * because it is usually invoked during the construction phase of AbstractLearnerGraph 
	 * when no data is yet available.
	 */
	AbstractPathRoutines(AbstractLearnerGraph<TARGET_TYPE,CACHE_TYPE> computeStateScores) 
	{
		coregraph = computeStateScores;
	}

	public Set<String> computeAlphabet()
	{
		Set<String> result = new LinkedHashSet<String>();
		for(Entry<CmpVertex,Map<String,TARGET_TYPE>> entry:coregraph.transitionMatrix.entrySet())
			result.addAll(entry.getValue().keySet());
		return result;
	}


	/** Computes all possible shortest paths from the supplied source state to the supplied target state.
	 * If there are many paths of the same length, all of those paths are returned.
	 * 
	 * @param vertSource the source state
	 * @param vertTarget the target state
	 * @return sequences of inputs to follow all paths found.
	 */	
	Collection<List<String>> computePathsBetween(CmpVertex vertSource, CmpVertex vertTarget)
	{
		PTASequenceEngine engine = new PTASequenceEngine();engine.init(new PTASequenceSetAutomaton());
		PTASequenceEngine.SequenceSet initSet = engine.new SequenceSet();initSet.setIdentity(); 
		PTASequenceEngine.SequenceSet paths = engine.new SequenceSet();paths.setIdentity(); 
		computePathsSBetween(vertSource, vertTarget,initSet,paths);
		return engine.getData();
	}
	
	public Collection<List<String>> computePathsToRed(CmpVertex red)
	{
		return computePathsBetween(coregraph.init, red);
	}
	
	/** Computes all possible shortest paths from the supplied source state to the 
	 * supplied target state and returns a PTA corresponding to them. The easiest 
	 * way to record the numerous computed paths is by using PTATestSequenceEngine-derived classes;
	 * this also permits one to trace them in some automaton and junk irrelevant ones.
	 * 
	 * @param vertSource the source state
	 * @param vertTarget the target state
	 * @param pathsToVertSource PTA of paths to enter vertSource, can be initialised with identity 
	 * or obtained using PTATestSequenceEngine-related operations.
	 * @param nodes of a PTA corresponding to the entered states, to which resulting nodes will be added (this method 
	 * cannot create an empty instance of a sequenceSet (which is why it has to be passed one), perhaps for a reason).
	 */	
	public void computePathsSBetween(CmpVertex vertSource, CmpVertex vertTarget,
			PTASequenceEngine.SequenceSet pathsToVertSource,
			PTASequenceEngine.SequenceSet result)
	{
		if (!computePathsSBetweenBoolean(vertSource, vertTarget, pathsToVertSource, result))
			throw new IllegalArgumentException("path from state "+vertSource+" to state "+vertTarget+" was not found");
	}
	
	/** Computes all possible shortest paths from the supplied source state to the 
	 * supplied target state and returns a PTA corresponding to them. The easiest 
	 * way to record the numerous computed paths is by using PTATestSequenceEngine-derived classes;
	 * this also permits one to trace them in some automaton and junk irrelevant ones.
	 * 
	 * @param vertSource the source state
	 * @param vertTarget the target state
	 * @param pathsToVertSource PTA of paths to enter vertSource, can be initialised with identity 
	 * or obtained using PTATestSequenceEngine-related operations.
	 * @param nodes of a PTA corresponding to the entered states, to which resulting nodes will be added (this method 
	 * cannot create an empty instance of a sequenceSet (which is why it has to be passed one), perhaps for a reason).
	 * @return false if a path cannot be found and true otherwise.
	 */	
	public boolean computePathsSBetweenBoolean(CmpVertex vertSource, CmpVertex vertTarget,
			PTASequenceEngine.SequenceSet pathsToVertSource,
			PTASequenceEngine.SequenceSet result)
	{
		if (vertSource == null || vertTarget == null || pathsToVertSource == null)
			throw new IllegalArgumentException("null arguments to computePathsSBetween");
		if (GlobalConfiguration.getConfiguration().isAssertEnabled())
			if (!coregraph.learnerCache.getFlowgraph().containsKey(vertSource) || !coregraph.learnerCache.getFlowgraph().containsKey(vertTarget))
				throw new IllegalArgumentException("either source or target vertex is not in the graph");
		
		Set<CmpVertex> visitedStates = new HashSet<CmpVertex>();visitedStates.add(vertSource);
		
		// FIFO queue containing sequences of states labelling paths to states to be explored.
		// Important, after processing of each wave, we add a null, in order to know when
		// to stop when scanning to the end of the current wave when a path to the target state
		// has been found.
		Queue<List<CmpVertex>> currentExplorationPath = new LinkedList<List<CmpVertex>>();
		Queue<CmpVertex> currentExplorationState = new LinkedList<CmpVertex>();
		if (vertSource == vertTarget)
		{
			result.unite(pathsToVertSource);
			return true;// nothing to do, return paths to an initial state.
		}
		
		currentExplorationPath.add(new LinkedList<CmpVertex>());currentExplorationState.add(vertSource);
		currentExplorationPath.offer(null);currentExplorationState.offer(null);// mark the end of the first (singleton) wave.
		CmpVertex currentVert = null;List<CmpVertex> currentPath = null;
		boolean pathFound = false;
		while(!currentExplorationPath.isEmpty())
		{
			currentVert = currentExplorationState.remove();currentPath = currentExplorationPath.remove();
			if (currentVert == null)
			{// we got to the end of a wave
				if (pathFound)
					break;// if we got to the end of a wave and the target vertex has been found on some paths in this wave, stop scanning.
				else
					if (currentExplorationPath.isEmpty())
						break;// we are at the end of the last wave, stop looping.
					else
					{// mark the end of a wave.
						currentExplorationPath.offer(null);currentExplorationState.offer(null);
					}
			}
			else
			{
				visitedStates.add(currentVert);
				for(Entry<CmpVertex,Set<String>> entry:coregraph.learnerCache.getFlowgraph().get(currentVert).entrySet())
				{
					if (entry.getKey() == vertTarget)
					{// found the vertex we are looking for
						pathFound = true;
						// now we need to go through all our states in a path and update pathsToVertSource
						PTASequenceEngine.SequenceSet paths = pathsToVertSource;currentPath.add(vertTarget);CmpVertex curr = vertSource;
						// process vertices
						for(CmpVertex tgt:currentPath)
						{// ideally, I'd update one at a time and merge results, but it seems the same (set union) if I did it by building a set of inputs and did a cross with it.
							paths = paths.crossWithSet(coregraph.learnerCache.getFlowgraph().get(curr).get(tgt));
							curr = tgt;
						}
						result.unite( paths );// update the result.
					}
					else
						if (!visitedStates.contains(entry.getKey()))
						{
							List<CmpVertex> newPath = new LinkedList<CmpVertex>();newPath.addAll(currentPath);newPath.add(entry.getKey());
							currentExplorationPath.offer(newPath);currentExplorationState.offer(entry.getKey());
						}
				}
			}
		}

		return pathFound;
	}
	
	/** Computes all possible shortest paths from the supplied source state to the 
	 * all states in the graph.<p> Returns a map from a state to the corresponding PTA. The easiest 
	 * way to record the numerous computed paths is by using PTATestSequenceEngine-derived classes;
	 * this also permits one to trace them in some automaton and junk irrelevant ones.
	 * 
	 * @param vertSource the source state
	 * @param pathsToVertSource PTA of paths to enter vertSource, can be initialised with identity 
	 * or obtained using PTATestSequenceEngine-related operations.
	 * @param nodes of a PTA corresponding to the entered states, to which resulting nodes will be added (this method 
	 * cannot create an empty instance of a sequenceSet (which is why it has to be passed one), perhaps for a reason).
	 * @return the map from states to PTAs of shortest paths to them. States which cannot be reached are not included in the map.
	 */	
	public Map<CmpVertex,PTASequenceEngine.SequenceSet> computePathsSBetween_All(CmpVertex vertSource, PTASequenceEngine engine,
			PTASequenceEngine.SequenceSet pathsToVertSource)
	{
		if (vertSource == null || pathsToVertSource == null)
			throw new IllegalArgumentException("null arguments to computePathsSBetween");
		if (GlobalConfiguration.getConfiguration().isAssertEnabled())
			if (!coregraph.learnerCache.getFlowgraph().containsKey(vertSource))
				throw new IllegalArgumentException("either source or target vertex is not in the graph");
		
		Set<CmpVertex> visitedStates = new HashSet<CmpVertex>();visitedStates.add(vertSource);
		
		// FIFO queue containing sequences of states labelling paths to states to be explored.
		// Important, after processing of each wave, we add a null, in order to know when
		// to stop when scanning to the end of the current wave when a path to the target state
		// has been found.
		Queue<List<CmpVertex>> currentExplorationPath = new LinkedList<List<CmpVertex>>();
		Queue<CmpVertex> currentExplorationState = new LinkedList<CmpVertex>();
		
		Map<CmpVertex,PTASequenceEngine.SequenceSet> stateToPathMap = new HashMap<CmpVertex,PTASequenceEngine.SequenceSet>();
		Map<CmpVertex,Integer> stateToDepthMap = new HashMap<CmpVertex,Integer>();
		
		currentExplorationPath.add(new LinkedList<CmpVertex>());currentExplorationState.add(vertSource);
		stateToPathMap.put(vertSource,pathsToVertSource);stateToDepthMap.put(vertSource,0);
		
		CmpVertex currentVert = null;List<CmpVertex> currentPath = null;
		while(!currentExplorationPath.isEmpty())
		{
			currentVert = currentExplorationState.remove();currentPath = currentExplorationPath.remove();
			visitedStates.add(currentVert);
			for(Entry<CmpVertex,Set<String>> entry:coregraph.learnerCache.getFlowgraph().get(currentVert).entrySet())
			{
				CmpVertex nextState = entry.getKey();
				if (nextState.getColour() != JUConstants.AMBER)
				{// Amber states are there only for state separation, not to be explored in question asking.

					Integer existingDepth = stateToDepthMap.get(nextState);
					int currentdepth = currentPath.size()+1;
					int existingdepth = existingDepth == null? currentdepth:existingDepth.intValue();
					assert existingdepth <= currentPath.size()+1;
					if (existingdepth == currentdepth)
					{// the path was found in the course of the current wave rather than earlier
							
						PTASequenceEngine.SequenceSet sequenceset = stateToPathMap.get(nextState);
						if (sequenceset == null)
						{// no path to the next state has yet been recorded hence create an empty collection
							sequenceset = engine.new SequenceSet();stateToPathMap.put(nextState,sequenceset);stateToDepthMap.put(nextState,currentdepth);
						}
						// now we need to go through all our states in a path and update pathsToVertSource
						PTASequenceEngine.SequenceSet paths = pathsToVertSource;CmpVertex curr = vertSource;
						// process vertices
						for(CmpVertex tgt:currentPath)
						{// ideally, I'd update one at a time and merge results, but it seems the same (set union) if I did it by building a set of inputs and did a cross with it.
							paths = paths.crossWithSet(coregraph.learnerCache.getFlowgraph().get(curr).get(tgt));
							curr = tgt;
						}
						paths = paths.crossWithSet(coregraph.learnerCache.getFlowgraph().get(curr).get(nextState));
						sequenceset.unite( paths );// update the collection of paths leading to the next state.
					}
				
					if (!visitedStates.contains(nextState))
					{
						List<CmpVertex> newPath = new LinkedList<CmpVertex>();newPath.addAll(currentPath);newPath.add(nextState);
						currentExplorationPath.offer(newPath);currentExplorationState.offer(nextState);
					}
				}		
			}
		}

		return stateToPathMap;
	}

	/** Builds a Jung graph corresponding to the state machine stored in transitionMatrix.
	 * Note that all states in our transition diagram (transitionMatrix) have Jung vertices associated with them (CmpVertex).
	 * 
	 * @return constructed graph.
	 */
	public DirectedSparseGraph getGraph()
	{
		return getGraph(coregraph.getNameNotNull());
	}
	
	/** Builds a Jung graph corresponding to the state machine stored in transitionMatrix.
	 * Note that all states in our transition diagram (transitionMatrix) have Jung vertices associated with them (DeterministicVertex).
	 * The fact that we need to return a Jung graph implies that all nodes are always cloned;
	 * this way we do not have to check if we've been asked to keep the original nodes and
	 * keep them unless they were StringVertices.
	 * 
	 * @param the name to give to the graph to be built.
	 * @return constructed graph.
	 */
	public DirectedSparseGraph getGraph(String name)
	{
		DirectedSparseGraph result = null;
		Configuration cloneConfig = coregraph.config.copy();cloneConfig.setLearnerUseStrings(false);cloneConfig.setLearnerCloneGraph(true);
		synchronized (AbstractLearnerGraph.syncObj) 
		{
			result = new DirectedSparseGraph();
			if (name != null)
				result.setUserDatum(JUConstants.TITLE, name,UserData.SHARED);

			Map<CmpVertex,DeterministicVertex> oldToNew = new HashMap<CmpVertex,DeterministicVertex>();
			// add states
			for(Entry<CmpVertex,Map<CmpVertex,Set<String>>> entry:coregraph.learnerCache.getFlowgraph().entrySet())
			{
				CmpVertex source = entry.getKey();
				DeterministicVertex vert = (DeterministicVertex)AbstractLearnerGraph.cloneCmpVertex(source,cloneConfig);
				if (coregraph.init == source)
					vert.addUserDatum(JUConstants.INITIAL, true, UserData.SHARED);
				result.addVertex(vert);
				oldToNew.put(source,vert);
			}
			
			// now add transitions
			for(Entry<CmpVertex,Map<CmpVertex,Set<String>>> entry:coregraph.learnerCache.getFlowgraph().entrySet())
			{
				DeterministicVertex source = oldToNew.get(entry.getKey());
				for(Entry<CmpVertex,Set<String>> tgtEntry:entry.getValue().entrySet())
				{
					CmpVertex targetOld = tgtEntry.getKey();
					assert coregraph.findVertex(targetOld.getID()) == targetOld : "was looking for vertex with name "+targetOld.getID()+", got "+coregraph.findVertex(targetOld.getID());
					DeterministicVertex target = oldToNew.get(targetOld);
					DeterministicEdge e = new DeterministicEdge(source,target);
					e.addUserDatum(JUConstants.LABEL, tgtEntry.getValue(), UserData.CLONE);
					result.addEdge(e);
				}
			}
		}
		return result;
	}

	/** Numerous methods using this class expect to be able to interpret the state 
	 * machine as a flowgraph, this method builds one.
	 */
	public Map<CmpVertex,Map<CmpVertex,Set<String>>> getFlowgraph()
	{
		Map<CmpVertex,Map<CmpVertex,Set<String>>> result = new TreeMap<CmpVertex,Map<CmpVertex,Set<String>>>();
		for(Entry<CmpVertex,Map<String,TARGET_TYPE>> entry:coregraph.transitionMatrix.entrySet())
		{
			Map<CmpVertex,Set<String>> targetStateToEdgeLabels = result.get(entry.getKey());
			if (targetStateToEdgeLabels == null)
			{
				targetStateToEdgeLabels = new TreeMap<CmpVertex,Set<String>>();
				result.put(entry.getKey(), targetStateToEdgeLabels);
			}
			
			for(Entry<String,TARGET_TYPE> sv:entry.getValue().entrySet())
				for(CmpVertex target:coregraph.getTargets(sv.getValue()))
				{
					Set<String> labels = targetStateToEdgeLabels.get(target);
					if (labels != null)
						// there is an edge already with the same target state from the current vertice, update the label on it
						labels.add(sv.getKey());
					else
					{// add a new edge
						labels = new HashSet<String>();labels.add(sv.getKey());
						targetStateToEdgeLabels.put(target, labels);
					}
				}
		}
		
		return result;
	}

	public int countEdges()
	{
		Iterator<Map<String,TARGET_TYPE>> outIt = coregraph.transitionMatrix.values().iterator();
		int counter = 0;
		while(outIt.hasNext()){
			for(Entry<String,TARGET_TYPE> sv:outIt.next().entrySet())
				counter = counter + coregraph.getTargets(sv.getValue()).size();
		}
		return counter;
	}


	/** Inverts states' acceptance conditions. */
	public void invertStates()
	{
		for(CmpVertex vertex:coregraph.transitionMatrix.keySet())
			vertex.setAccept(!vertex.isAccept());
	}
	
	/** Useful where we aim to check that the learnt machine is the same as 
	 * original. To prevent erroneous mergers, negative information is supplied,
	 * which is incorporated into the final machine. This way, even if the
	 * original machine does not have reject-states, the outcome of merging
	 * will have them. Transitions to those negative states are obviously only
	 * added where there are no transitions in the original one, so if we take 
	 * the original machine and add transitions from all states to reject states 
	 * for undefined inputs (called <em>completeGraph()</em>), the outcome 
	 * of learning will have a subset of transitions to reject-states.
	 *<p>
	 * Throws {@link IllegalArgumentException} if the initial state points to a reject-state. 
	 * This makes sure that the outcome is never an empty graph.
	 * 
	 * @param what an automaton which states are to be removed.
	 * @param config this method makes a copy of an automaton first, hence a configuration is needed.
	 * @return an automaton reduced in the described way.
	 */
	public static <TARGET_A_TYPE,TARGET_B_TYPE,
		CACHE_A_TYPE extends CachedData<TARGET_A_TYPE, CACHE_A_TYPE>,
		CACHE_B_TYPE extends CachedData<TARGET_B_TYPE, CACHE_B_TYPE>> 
		void removeRejectStates(AbstractLearnerGraph<TARGET_A_TYPE, CACHE_A_TYPE> what,
					AbstractLearnerGraph<TARGET_B_TYPE, CACHE_B_TYPE> result)
	{
		if (!what.init.isAccept()) throw new IllegalArgumentException("initial state cannot be a reject-state");
		AbstractLearnerGraph.copyGraphs(what, result);
		// Since we'd like to modify a transition matrix, we iterate through states of the original machine and modify the result.
		for(Entry<CmpVertex,Map<String,TARGET_A_TYPE>> entry:what.transitionMatrix.entrySet())
			if (!entry.getKey().isAccept()) result.transitionMatrix.remove(entry.getKey());// a copied state should be identical to the original one, so doing remove is appropriate 
			else
			{
				Map<String,TARGET_B_TYPE> row = result.transitionMatrix.get(entry.getKey());
				for(Entry<String,TARGET_A_TYPE> targetRow:entry.getValue().entrySet())
					for(CmpVertex target:what.getTargets(targetRow.getValue()))
						if (!target.isAccept()) result.removeTransition(row, targetRow.getKey(), target);
			}
		
	}
	
	/** Computes an alphabet of a given graph and adds transitions to a 
	 * reject state from all states A and inputs a from which there is no B such that A-a->B
	 * (A-a-#REJECT) gets added. Note: (1) such transitions are even added to reject vertices.
	 * (2) if such a vertex already exists, an IllegalArgumentException is thown.
	 * 
	 * @param reject the name of the reject state, to be added to the graph. No transitions are added from this state.
	 * @return true if any transitions have been added
	 */   
	public boolean completeGraph(VertexID reject)
	{
		if (coregraph.findVertex(reject) != null)
			throw new IllegalArgumentException("reject vertex named "+reject+" already exists");
		
		CmpVertex rejectVertex = null;
		
		// first pass - computing an alphabet
		Set<String> alphabet = coregraph.learnerCache.getAlphabet();
		
		// second pass - checking if any transitions need to be added and adding them.
		for(Entry<CmpVertex,Map<String,TARGET_TYPE>> entry:coregraph.transitionMatrix.entrySet())
		{
			Set<String> labelsToRejectState = new HashSet<String>();
			labelsToRejectState.addAll(alphabet);labelsToRejectState.removeAll(entry.getValue().keySet());
			if (!labelsToRejectState.isEmpty())
			{
				if (rejectVertex == null)
				{
					rejectVertex = AbstractLearnerGraph.generateNewCmpVertex(reject,coregraph.config);rejectVertex.setAccept(false);
				}
				for(String rejLabel:labelsToRejectState)
					coregraph.addTransition(entry.getValue(), rejLabel, rejectVertex);
			}
		}

		if (rejectVertex != null)
			coregraph.transitionMatrix.put(rejectVertex,coregraph.createNewRow());
		
		coregraph.learnerCache.invalidate();
		return rejectVertex != null;
	}

	/** For each input where there is no transition from a state,
	 * this function will add a transition to an inf-amber-coloured reject-state.
	 */  
	public static <TARGET_A_TYPE,TARGET_B_TYPE,
		CACHE_A_TYPE extends CachedData<TARGET_A_TYPE, CACHE_A_TYPE>,
		CACHE_B_TYPE extends CachedData<TARGET_B_TYPE, CACHE_B_TYPE>> 
		void completeMatrix(AbstractLearnerGraph<TARGET_A_TYPE, CACHE_A_TYPE> what,
				AbstractLearnerGraph<TARGET_B_TYPE, CACHE_B_TYPE> result)
	{
		AbstractLearnerGraph.copyGraphs(what, result);
		VertexID rejectID = result.nextID(false);
		result.pathroutines.completeGraph(rejectID);
		result.findVertex(rejectID).setColour(LearnerGraphND.ltlColour);
		result.findVertex(rejectID).setAccept(false);
	}

	/** 
	 * Relabels graph, keeping NrToKeep original labels. All new ones are generated with
	 * prefix PrefixNew.
	 * 
	 * @param g graph to transform.
	 * @param argNrToKeep number of labels to keep.
	 * @param PrefixNew prefix of new labels.
	 * @throws IllegalArgumentException if PrefixNew is a prefix of an existing vertex. The graph supplied is destroyed in this case.
	 */
	public static <TARGET_TYPE,CACHE_TYPE extends CachedData<TARGET_TYPE,CACHE_TYPE>> 
		void relabel(AbstractLearnerGraph<TARGET_TYPE, CACHE_TYPE> g, int argNrToKeep, String PrefixNew)
	{
		int NrToKeep = argNrToKeep;
		Map<String,String> fromTo = new TreeMap<String,String>();
		int newLabelCnt = 0;
		Map<CmpVertex,Map<String,TARGET_TYPE>> newMatrix = g.createNewTransitionMatrix();
		for(Entry<CmpVertex,Map<String,TARGET_TYPE>> entry:g.transitionMatrix.entrySet())
		{
			Map<String,TARGET_TYPE> newRow = g.createNewRow();
			for(Entry<String,TARGET_TYPE> transition:entry.getValue().entrySet())
			{
				if (NrToKeep > 0 && !fromTo.containsKey(transition.getKey()))
				{
					NrToKeep--;fromTo.put(transition.getKey(),transition.getKey());// keep the label and reduce the counter.
				}
				else
					if (!fromTo.containsKey(transition.getKey()))
					{
						if(transition.getKey().startsWith(PrefixNew))
							throw new IllegalArgumentException("there is already a transition with prefix "+PrefixNew+" in the supplied graph");
						fromTo.put(transition.getKey(), PrefixNew+newLabelCnt++);
					}
				newRow.put(fromTo.get(transition.getKey()), transition.getValue());
			}
			newMatrix.put(entry.getKey(), newRow);
		}
		g.transitionMatrix = newMatrix;g.learnerCache.invalidate();
	}
	
	/** Adds all states and transitions from graph <em>what</em> to graph <em>g</em>.
	 * Very useful for renumbering nodes on graphs loaded from GraphML and such, because
	 * numerical node IDs can be useful. The current implementation does not require this
	 * because it is easy to build a map from existing identifiers to numbers.
	 * <em>WMethod.buildStateToIntegerMap()</em> does exactly this and the outcome is cached
	 * and used by <em>vertexToInt</em> and <em>vertexToIntNR</em>.
	 * <p>
	 * An example of using this method to renumber vertices is shown below:
	 * <pre>
	 * LearnerGraph grTmp = new LearnerGraph(g.config);
	 * CmpVertex newInit = addToGraph(grTmp,g);StatePair whatToMerge = new StatePair(grTmp.init,newInit);
	 * LinkedList<Collection<CmpVertex>> collectionOfVerticesToMerge = new LinkedList<Collection<CmpVertex>>();
	 * grTmp.pairscores.computePairCompatibilityScore_general(whatToMerge,collectionOfVerticesToMerge);
	 * LearnerGraph result = MergeStates.mergeAndDeterminize_general(grTmp, whatToMerge,collectionOfVerticesToMerge);
	 * WMethod.computeWSet(result);
	 * </pre>
	 * @param g target into which to merge what
	 * @param what graph to merge into g.
	 * @param argWhatToG maps original vertices to those included in the graph <em>g</em>.
	 * @return vertex in g corresponding to the initial vertex in what 
	 */ 
	public static <TARGET_A_TYPE,TARGET_B_TYPE,
	CACHE_A_TYPE extends CachedData<TARGET_A_TYPE, CACHE_A_TYPE>,
	CACHE_B_TYPE extends CachedData<TARGET_B_TYPE, CACHE_B_TYPE>> 
		CmpVertex addToGraph(AbstractLearnerGraph<TARGET_A_TYPE, CACHE_A_TYPE> g,
				AbstractLearnerGraph<TARGET_B_TYPE, CACHE_B_TYPE> what, Map<CmpVertex,CmpVertex> argWhatToG)
	{
		Map<CmpVertex,CmpVertex> whatToG = argWhatToG;
		if (whatToG == null) whatToG = new TreeMap<CmpVertex,CmpVertex>();else whatToG.clear();
		for(Entry<CmpVertex,Map<String,TARGET_B_TYPE>> entry:what.transitionMatrix.entrySet())
		{// The idea is to number the new states rather than to clone vertices.
		 // This way, new states get numerical IDs rather than retain the original (potentially text) IDs.
			CmpVertex newVert = g.copyVertexUnderDifferentName(entry.getKey());
			//newVert.setOrigState(entry.getKey().getID());
			whatToG.put(entry.getKey(),newVert);
		}

		AbstractLearnerGraph.addAndRelabelGraphs(what, whatToG, g);
		return whatToG.get(what.init);
	}

	/** Changes states labels on a graph to their numerical equivalents.
	 * 
	 * @param what graph to convert
	 * @param result where to store the result of conversion.
	 */
	public static <TARGET_A_TYPE,TARGET_B_TYPE,
	CACHE_A_TYPE extends CachedData<TARGET_A_TYPE, CACHE_A_TYPE>,
	CACHE_B_TYPE extends CachedData<TARGET_B_TYPE, CACHE_B_TYPE>> 
		void convertToNumerical(AbstractLearnerGraph<TARGET_A_TYPE, CACHE_A_TYPE> what,
			AbstractLearnerGraph<TARGET_B_TYPE, CACHE_B_TYPE> result)
	{
		result.initEmpty();
		result.init = addToGraph(result, what, null);if (what.getName() != null) result.setName(what.getName());
	}

	/** Makes sure that the transition matrix is (mostly) consistent. 
	 * Only used for testing.
	 * 
	 * @param reference the graph used to find vertices corresponding to identifiers.
	 */
	public <TARGET_A_TYPE,CACHE_A_TYPE extends CachedData<TARGET_A_TYPE,CACHE_A_TYPE>> void checkConsistency(
			AbstractLearnerGraph<TARGET_A_TYPE,CACHE_A_TYPE> reference)
	{
		if (GlobalConfiguration.getConfiguration().isAssertEnabled())
		{
			if (coregraph.transitionMatrix.isEmpty())
			{
				if (coregraph.init != null) 
					throw new IllegalArgumentException("an empty matrix must correspond to a null initial state");
			}
			else
			{
				if (coregraph.findVertex(coregraph.init.getID()) != coregraph.init) 
					throw new IllegalArgumentException("initial state is not in a graph");
				if (reference.findVertex(coregraph.init.getID()) != coregraph.init)
					throw new IllegalArgumentException("initial state is not in a reference graph");
				
				for(Entry<CmpVertex,Map<String,TARGET_TYPE>> entry:coregraph.transitionMatrix.entrySet())
				{
					if (entry.getValue() == null) throw new IllegalArgumentException("null target states");
					if (coregraph.findVertex(entry.getKey().getID()) != entry.getKey()) throw new IllegalArgumentException("duplicate state "+entry.getKey());
					if (reference.findVertex(entry.getKey().getID()) != entry.getKey()) throw new IllegalArgumentException("duplicate state "+entry.getKey());
					
					for(Entry<String,TARGET_TYPE> transition:entry.getValue().entrySet())
					{
						if (coregraph.getTargets(transition.getValue()).isEmpty()) throw new IllegalArgumentException("empty set of target states");
						for(CmpVertex targetState:coregraph.getTargets(transition.getValue()))
						{
							if (coregraph.findVertex(targetState.getID()) != targetState) throw new IllegalArgumentException("duplicate state "+entry.getKey());
							if (reference.findVertex(targetState.getID()) != targetState) throw new IllegalArgumentException("duplicate state "+entry.getKey());
						}
					}
				}
			}
		}
	}
	
	/** Takes the recorded non-deterministic transition matrix and turns it into
	 * a deterministic one, at the obviously exponential cost. Vertices not reachable from the 
	 * initial state are ignored.
	 * 
	 * @return deterministic version of it.
	 * @throws IncompatibleStatesException if there are two paths corresponding to the same sequence of
	 * inputs where the first one leads to an accept state and the other one - to a reject one. 
	 */
	public LearnerGraph buildDeterministicGraph() throws IncompatibleStatesException
	{
		return buildDeterministicGraph(coregraph.init);
	}

	/** Takes the recorded non-deterministic transition matrix and turns it into
	 * a deterministic one, at the obviously exponential cost. Vertices not reachable from the 
	 * supplied state are ignored.
	 * 
	 * @param initialState the state to start from.
	 * @return deterministic version of it.
	 * @throws IncompatibleStatesException if there are two paths corresponding to the same sequence of
	 * inputs where the first one leads to an accept state and the other one - to a reject one. 
	 */
	public LearnerGraph buildDeterministicGraph(CmpVertex initialState) throws IncompatibleStatesException
	{
		
		/** Maps sets of target states to the corresponding known states. */
		Map<Set<CmpVertex>,AMEquivalenceClass<TARGET_TYPE,CACHE_TYPE>> equivalenceClasses = new HashMap<Set<CmpVertex>,AMEquivalenceClass<TARGET_TYPE,CACHE_TYPE>>();
		
		LearnerGraph result = new LearnerGraph(coregraph.config.copy());result.initEmpty();
		if (coregraph.transitionMatrix.isEmpty())
		{
			if (initialState != null) throw new IllegalArgumentException("non-null supplied state "+initialState);
			return result;
		}
		if (!coregraph.transitionMatrix.containsKey(initialState)) throw new IllegalArgumentException("the supplied state "+initialState+" is not in the graph");

		int eqClassNumber = 0;
		AMEquivalenceClass<TARGET_TYPE,CACHE_TYPE> initial = new AMEquivalenceClass<TARGET_TYPE,CACHE_TYPE>(eqClassNumber++,coregraph);initial.addFrom(initialState,null);
		initial.constructMergedVertex(result,true,false);
		result.init = initial.getMergedVertex();
		Queue<AMEquivalenceClass<TARGET_TYPE,CACHE_TYPE>> currentExplorationBoundary = new LinkedList<AMEquivalenceClass<TARGET_TYPE,CACHE_TYPE>>();// FIFO queue containing equivalence classes to be explored

		Map<String,AMEquivalenceClass<TARGET_TYPE,CACHE_TYPE>> inputToTargetClass = new HashMap<String,AMEquivalenceClass<TARGET_TYPE,CACHE_TYPE>>();
		currentExplorationBoundary.add(initial);equivalenceClasses.put(initial.getStates(),initial);
		while(!currentExplorationBoundary.isEmpty())
		{// Unlike PairScoreComputation, here all target states are merged in one go. This is why it does not seem to
		 // make sense expanding them into individual input-state pairs.
			AMEquivalenceClass<TARGET_TYPE,CACHE_TYPE> currentClass = currentExplorationBoundary.remove();
			//System.out.println("considering state "+currentClass+" orig "+currentClass.getRepresentative());
			
			inputToTargetClass.clear();
			for(CmpVertex vertex:currentClass.getStates())
			{
				for(Entry<String,TARGET_TYPE> transition:coregraph.transitionMatrix.get(vertex).entrySet())
				{
					AMEquivalenceClass<TARGET_TYPE,CACHE_TYPE> targets = inputToTargetClass.get(transition.getKey());
					if (targets == null)
					{
						targets = new AMEquivalenceClass<TARGET_TYPE,CACHE_TYPE>(eqClassNumber++,coregraph);
						inputToTargetClass.put(transition.getKey(),targets);
					}
					for(CmpVertex targetVertex:coregraph.getTargets(transition.getValue()))
						targets.addFrom(targetVertex,null);// the reason for adding sequentially is to make sure AMEquivalentClass figures out which state is to be chosen as a representative
				}
			}

			// Now I have iterated through all states in the current class and
			// assembled collections of states corresponding to destination classes.
			
			Map<String,CmpVertex> row = result.transitionMatrix.get(currentClass.getMergedVertex());
			// Now I need to iterate through those new classes and
			// 1. update the transition diagram.
			// 2. append those I've not yet seen to the exploration stack.
			for(Entry<String,AMEquivalenceClass<TARGET_TYPE,CACHE_TYPE>> transition:inputToTargetClass.entrySet())
			{
				AMEquivalenceClass<TARGET_TYPE,CACHE_TYPE> realTargetState = equivalenceClasses.get(transition.getValue().getStates());
				if (realTargetState == null)
				{// this is a new state
					realTargetState = transition.getValue();
					currentExplorationBoundary.offer(realTargetState);
					equivalenceClasses.put(realTargetState.getStates(),realTargetState);
					realTargetState.constructMergedVertex(result,true,false);
				}
								
				row.put(transition.getKey(), realTargetState.getMergedVertex());
			}
		}

		AMEquivalenceClass.populateCompatible(result, equivalenceClasses.values());
		result.setName("after making deterministic");
		return result;
	}

	/** Computes a state cover (a collection of sequences to reach every state in this machine). */
	public List<List<String>> computeStateCover()
	{
		List<List<String>> outcome = new LinkedList<List<String>>();outcome.addAll(computeShortPathsToAllStates().values());
		return outcome;
	}
	
	/** Computes a mapping from every state to a shortest path to that state. The term
	 * "a shorest path" is supposed to mean that we are talking of one of the shortest pats.
	 *  
	 * @return map from states to paths reaching those states.
	 */
	public Map<CmpVertex,LinkedList<String>> computeShortPathsToAllStates()
	{
		return computeShortPathsToAllStates(coregraph.init);
	}
	
	/** Computes a mapping from every state to a shortest path to that state. The term
	 * "a shortest path" is supposed to mean that we are talking of one of the shortest pats.
	 * @param from the vertex to start from
	 * @return map from states to paths reaching those states.
	 */
	public Map<CmpVertex,LinkedList<String>> computeShortPathsToAllStates(CmpVertex from)
	{
		Map<CmpVertex,LinkedList<String>> stateToPath = new HashMap<CmpVertex,LinkedList<String>>();stateToPath.put(from, new LinkedList<String>());
		Queue<CmpVertex> fringe = new LinkedList<CmpVertex>();
		Set<CmpVertex> statesInFringe = new HashSet<CmpVertex>();// in order not to iterate through the list all the time.
		fringe.add(from);statesInFringe.add(from);
		while(!fringe.isEmpty())
		{
			CmpVertex currentState = fringe.remove();
			LinkedList<String> currentPath = stateToPath.get(currentState);
			Map<String,TARGET_TYPE> targets = coregraph.transitionMatrix.get(currentState);
			if(targets != null && !targets.isEmpty())
				for(Entry<String,TARGET_TYPE> labelstate:targets.entrySet())
					
				for(CmpVertex target:coregraph.getTargets(labelstate.getValue()))
				{
					if (!statesInFringe.contains(target))
					{
						LinkedList<String> newPath = (LinkedList<String>)currentPath.clone();newPath.add(labelstate.getKey());
						stateToPath.put(target,newPath);
						fringe.offer(target);
						statesInFringe.add(target);
					}
				}
		}
		return stateToPath;
	}

	/** Checks if the supplied FSM has unreachable states.
	 * 
	 * @return true if there are any unreachable states.
	 */
	public boolean checkUnreachableStates()
	{
		return computeStateCover().size() != coregraph.transitionMatrix.size();
	}

	/** Returns a state, randomly chosen according to the supplied random number generator. */
	public CmpVertex pickRandomState(Random rnd)
	{
		int nr = rnd.nextInt(coregraph.transitionMatrix.size());
		for(Entry<CmpVertex,Map<String,TARGET_TYPE>> entry:coregraph.transitionMatrix.entrySet())
			if (nr-- == 0)
				return entry.getKey();
		
		throw new IllegalArgumentException("something wrong with the graph - the expected state was not found");
	}

	/** Returns an ADL representation of this graph. */
	public String toADL()
	{
		StringBuffer result = new StringBuffer();
		result.append(coregraph.transitionMatrix.size());result.append(' ');result.append(countEdges());result.append('\n');
		
		for(Entry<CmpVertex,Map<String,TARGET_TYPE>> entry:coregraph.transitionMatrix.entrySet())
		{
			result.append(entry.getKey().getID());result.append(' ');result.append(coregraph.init == entry.getKey());
			result.append(' ');result.append(entry.getKey().isAccept());result.append('\n');
		}
		
		for(Entry<CmpVertex,Map<String,TARGET_TYPE>> entry:coregraph.transitionMatrix.entrySet())
		{
			for(Entry<String,TARGET_TYPE> transitionEntry:entry.getValue().entrySet())
			{
				List<CmpVertex> targetStates = new ArrayList<CmpVertex>(coregraph.getTargets(transitionEntry.getValue()));
				Collections.sort(targetStates);
				for(CmpVertex targetState:targetStates)
				{
					result.append(entry.getKey().getID());result.append(' ');result.append(targetState.getID());
					result.append(' ');result.append(transitionEntry.getKey());result.append('\n');
				}
			}
		}
		
		return result.toString();
	}
	

}
