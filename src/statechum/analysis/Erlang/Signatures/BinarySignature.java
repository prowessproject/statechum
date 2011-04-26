/* Copyright (c) 2011 The University of Sheffield.
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
 * 
 */
package statechum.analysis.Erlang.Signatures;

import com.ericsson.otp.erlang.OtpErlangBinary;
import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangObject;

/**
 *
 * @author ramsay
 */
public class BinarySignature extends Signature {

	public BinarySignature(OtpErlangList attributes)
	{
		super();
		if (attributes.arity() != 0) throw new IllegalArgumentException("BinarySignature does not accept attributes");
		erlangTermForThisType = erlangTypeToString(attributes,null);
	}
	
    @Override
	public OtpErlangObject instantiate() {
        return new OtpErlangBinary("<<\"wibble\">>");
    }
}
