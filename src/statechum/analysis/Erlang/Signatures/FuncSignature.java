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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import statechum.Helper;
import statechum.Label;
import statechum.analysis.Erlang.ErlangRunner;
import statechum.analysis.Erlang.ErlangRunner.ERL;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangLong;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangString;
import com.ericsson.otp.erlang.OtpErlangTuple;

/** A signature for a function.
 *
 * Note: this is NOT a subclass of signature. 
 * Specifically has instantiateAllResults() and instantiateAllArgs() instead of instantiateAllAlts()
 *
 * @author ramsay
 */
public class FuncSignature implements Label {

    protected final String funcName, moduleName, fullFileName;
    /** A function can be defined with a variety of argument types, 
     * this one collects all of those offerered by typer. 
     */
    protected final List<List<Signature>> args;
    protected final Signature result;
    protected final int arity,lineNumber;
    
    /** Type constructor for a function which takes anything and returns anything - 
     * not supported since arity is not known.
    public FuncSignature() {}
	*/
    
    public String getName()
    {
    	return funcName;
    }
    
    public int getArity()
    {
    	return arity;
    }
    
    @Override
    public String toString()
    {
    	return getQualifiedName()+"/"+getArity();
    }
    
    public String getQualifiedName()
    {
    	return moduleName+":"+getName();
    }
    
    /** Represents an Erlang term from which details of this function can be reconstructed - this is 
     * currently only used to compare labels due to their immutability but can easily be 
     * used for serialisation.
     */
    private final String erlangTermForThisType;
    
    /** Used by the old parser. */
    public FuncSignature(String module,String func,List<List<Signature>> arguments, Signature res) {
    	moduleName = module;fullFileName = module+".erl";funcName = func;
    	args=buildImmutableCollection(arguments);
    	result=res;lineNumber=-1;
    	if (arguments.isEmpty()) throw new IllegalArgumentException("empty list of argument choices");
    	arity = arguments.get(0).size();
    	
    	erlangTermForThisType = buildFunctionSignatureAsString();
    }
    
    protected String buildFunctionSignatureAsString()
    {// {File, LineNo, F, A,fun_to_Statechum(erl_types:t_fun(ArgType, RetType),Info#info.recMap)}
    	StringBuffer resultHolder = new StringBuffer();
    	resultHolder.append("{\"");
    	resultHolder.append(fullFileName);resultHolder.append("\",");
    	resultHolder.append(lineNumber);resultHolder.append(',');
       	resultHolder.append(funcName);resultHolder.append(',');
       	resultHolder.append(arity);resultHolder.append(',');
		resultHolder.append("{'");resultHolder.append(Signature.getSigName(this));resultHolder.append("',[],[");
    	if (args.size() != 1)
		throw new IllegalArgumentException("only function declarations obtained from Erlang are supported");
		boolean innerFirst = true;
		for(Signature sig:args.get(0))
		{
			if (!innerFirst) resultHolder.append(',');else innerFirst = false;
			resultHolder.append(sig.toErlangTerm());
		}
		resultHolder.append("],");
		resultHolder.append(result.toErlangTerm());
		resultHolder.append("}}");
		return resultHolder.toString();
    }
    
    protected static <ELEM> List<List<ELEM>> buildImmutableCollection(List<List<ELEM>> collection)
    {
    	List<List<ELEM>> immutableListList = new ArrayList<List<ELEM>>(collection.size());
    	for(List<ELEM> list:collection)
    	{
    		List<ELEM> immutableList = new ArrayList<ELEM>(list.size());
    		immutableList.addAll(list);immutableListList.add(Collections.unmodifiableList(list));
    	}
    	return Collections.unmodifiableList(immutableListList);
    }
    
    protected static List<List<Signature>> LoadArgs(OtpErlangList ArgList)
    {// arguments
    	List<List<Signature>> result = new ArrayList<List<Signature>>(1);
        ArrayList<Signature> arguments = new ArrayList<Signature>(ArgList.arity());
        for(int i=0;i<ArgList.arity();++i)
        	arguments.add(Signature.buildFromType(ArgList.elementAt(i)));
        
        result.add(Collections.unmodifiableList(arguments));
        return Collections.unmodifiableList(result);
    }

    /** Intended for functions passed as arguments to other functions.
     * Typer only returns one type signature which generalises possible values. */
    public FuncSignature(OtpErlangList attributes,OtpErlangList ArgList, OtpErlangList Range) {
        super();
		if (attributes.arity() != 0) throw new IllegalArgumentException("FuncSignature does not accept attributes");
		arity = ArgList.arity();
        args = LoadArgs(ArgList);result = Signature.buildFromType(Range);
        moduleName="UNKNOWN";fullFileName=moduleName+".erl";funcName="ANONYMOUS";lineNumber=-1;

        erlangTermForThisType = buildFunctionSignatureAsString();
    }

    public static final OtpErlangAtom funcAtom = new OtpErlangAtom("Func"); 
    
    /** Used to take a response from a call to typer and turn it into a function-signature. */
    public FuncSignature(OtpErlangObject func) 
    {
    	super();
    	String extractedModuleName = null,extractedFuncName=null, extractedFileName = null;
    	int knownArity =-1, extractedLineNumber =-1;
    	OtpErlangList attributes = null,ArgList = null;OtpErlangObject Range = null;
    	try
    	{
    		OtpErlangTuple funcTuple = (OtpErlangTuple) func;
    		extractedFileName = ((OtpErlangString)funcTuple.elementAt(0)).stringValue();
	    	extractedModuleName = ErlangRunner.getName(new File( extractedFileName),ERL.MOD);
	    	extractedLineNumber = ((OtpErlangLong)funcTuple.elementAt(1)).intValue();
	    	extractedFuncName =((OtpErlangAtom)funcTuple.elementAt(2)).atomValue();
	    	knownArity = ((OtpErlangLong)funcTuple.elementAt(3)).intValue();
	    	OtpErlangTuple funcDecl = (OtpErlangTuple)funcTuple.elementAt(4);
	    	if (!funcDecl.elementAt(0).equals(funcAtom)) throw new IllegalArgumentException("Function tuple does not start with the "+funcAtom+" tag");
	    	attributes = (OtpErlangList)funcDecl.elementAt(1);
    		ArgList = (OtpErlangList)funcDecl.elementAt(2);
    		Range = funcDecl.elementAt(3);
    	}
    	catch(Exception ex)
    	{
    		Helper.throwUnchecked("Failed to parse the structure returned from statechum-typer", ex);
    	}
		if (attributes.arity() != 0) throw new IllegalArgumentException("FuncSignature does not accept attributes");
		arity = ArgList.arity();lineNumber=extractedLineNumber;moduleName=extractedModuleName;funcName=extractedFuncName;
		assert arity == knownArity;
        args = LoadArgs(ArgList);result = Signature.buildFromType(Range);
        fullFileName = extractedFileName;
        erlangTermForThisType = buildFunctionSignatureAsString();
    }
    
	public List<OtpErlangObject> instantiate() {
        // Just pick the first pattern for now...
        // This needs to be cleverererer
        assert !args.isEmpty() : "function "+funcName+" should have at least one type defined";
        List<Signature> sigToUse = args.get(0);
        List<OtpErlangObject> argInstance = new LinkedList<OtpErlangObject>();
        for (Signature s : sigToUse) {
        	argInstance.add(s.instantiate());
        }
        return argInstance;
    }

    public List<OtpErlangObject> instantiateAllResults() {
        return result.instantiateAllAlts();
    }

     public List<List<OtpErlangObject>> instantiateAllArgs() {
        LinkedList<List<OtpErlangObject>> res = new LinkedList<List<OtpErlangObject>>();

        for (List<Signature> a : args)
            res.addAll(Signature.computeCrossProduct(a));

        return res;
    }

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return toErlangTerm().hashCode();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		return toErlangTerm().equals( ((Label) obj).toErlangTerm() );
	}

	@Override
	public int compareTo(Label o) {
		return erlangTermForThisType.compareTo( ((FuncSignature)o).erlangTermForThisType);
	}

	@Override
	public String toErlangTerm() {
		return erlangTermForThisType;
	}
}