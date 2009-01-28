/* Copyright (c) 2006, 2007, 2008 Neil Walkinshaw and Kirill Bogdanov
 * 
 * This file is part of StateChum
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

package statechum;

public enum JUConstants {
	LABEL("label"),
	ACCEPTED("accepted"),
	TITLE("title"),
	INITIAL("init"),// (boolean) whether a vertex is an initial state
	HIGHLIGHT("highlight"),// (boolean) whether a vertex should be highlighted on a display
	ORIGSTATE("origstate"),// (VertexID) the name of a vertex in hard facts which corresponds to this vertex
	DEPTH("depth"),// (int) how far the original state (ORIGSTATE) was from the root state
	
	STATS("STATS"),
	COLOUR("colour"),
	NONE("none"),// used to denote absence of any colour rather than null in AMEquivalenceClass 
	RED("red"),BLUE("blue"),AMBER("amber"),GRAY("gray"),INF_AMBER("inf_amber"),
	JUNKVERTEX("junk"),// used for testing that searching for a property that does not exist returns a null vertex.
	EDGE("edge"),VERTEX("vertex"), // used for labelling vertices
	
	INCOMPATIBLE("incompatible"), // used to designate a pair of states as incompatible
	MERGED("merged") // used to designate a pair of states as those which have been 
		// merged before, hence we might wish to merge them without asking user any questions 
	 	// as long as the pair of states is compatible. Such as merger will have to be sandwiched
		// between checkers and the bottom of the stack where autoanswers and user answers lie.
	;
	private String stringRepresentation;
	
	public static final int intUNKNOWN = -1;
	public static final int intSTATEPAIR_INCOMPATIBLE = -20; // the integer representation of INCOMPATIBLE, used for storage
	public static final int intSTATEPAIR_MERGED = -21;// the integer representation of MERGED, used for storage

	JUConstants(String textualName)
	{
		stringRepresentation = textualName;
	}
	
	@Override
	public String toString()
	{
		return stringRepresentation;
	}
}
