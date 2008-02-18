/*Copyright (c) 2006, 2007, 2008 Neil Walkinshaw and Kirill Bogdanov
 
This file is part of statechum.

statechum is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Foobar is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Foobar.  If not, see <http://www.gnu.org/licenses/>.
*/ 

package statechum.xmachine.model.testset;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Makes it possible to construct a collection of sequences of strings where no
 * string is a prefix of another one.
 * 
 * @author Kirill
 */
public abstract class PrefixFreeCollection {

	public abstract Collection<List<String>> getData();

	/** Adds a sequence to this collection. */
	public abstract void addSequence(List<String> sequence);

	/**
	 * Returns true if what is a prefix of str.
	 * 
	 * @param str
	 *            string from a database
	 * @param what
	 *            what to check against <em>str</em>
	 * @return true if <em>what</em> is a prefix of <em>str</em>.
	 */
	public static boolean isPrefix(List<String> str, List<String> what) {
		if (what.size() > str.size())
			return false;
		Iterator<String> strIt = str.iterator(), whatIt = what.iterator();
		while (whatIt.hasNext() && strIt.hasNext())
			if (!strIt.next().equals(whatIt.next()))
				return false;

		return true;
	}

}
