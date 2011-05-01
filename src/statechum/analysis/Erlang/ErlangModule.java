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
package statechum.analysis.Erlang;

import statechum.Helper;
import statechum.ProgressIndicator;
import statechum.analysis.Erlang.ErlangRunner.ERL;
import statechum.analysis.Erlang.Signatures.FuncSignature;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Assert;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangList;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangString;
import com.ericsson.otp.erlang.OtpErlangTuple;

/** Represents an Erlang module
 *
 * @author ramsay
 */
public class ErlangModule {

	
	public final File sourceFolder;
    public final String name;
    public final OTPBehaviour behaviour;
    public final Map<String, FuncSignature> sigs;

    protected ErlangModule(final File f) throws IOException 
    {
    	name = ErlangRunner.getName(f,ERL.MOD);
    	sourceFolder = f.getParentFile();
        ProgressIndicator progress = new ProgressIndicator(name, 5);
        // launch Erlang by calling a test method. 
        ErlangRunner.getRunner().call(new OtpErlangObject[]{new OtpErlangAtom("echo2Tuple"),new OtpErlangAtom("aaa")},"echo2Tuple");
        progress.next();// 1
        
        // Compile and typecheck the module...
        ErlangRunner.compileErl(f,ErlangRunner.getRunner());
        progress.next();// 2
        sigs = new TreeMap<String, FuncSignature>();
        
        File pltFile = new File(ErlangRunner.getName(f,ERL.PLT));
        
        // Almost the same arguments for dialyzer and typer, the first argument determines which of the two to run.
        OtpErlangObject otpArgs[] = new OtpErlangObject[]{
        		null, // either Dialyzer or typer

        		new OtpErlangList(new OtpErlangObject[]{new OtpErlangString(ErlangRunner.getName(f, ERL.BEAM))}),
				new OtpErlangString(ErlangRunner.getName(f, ERL.PLT)),
				new OtpErlangList(new OtpErlangObject[]{new OtpErlangString(ErlangRunner.getName(f, ERL.ERL))}),
				new OtpErlangAtom("types")        
        };
        
        if (!pltFile.canRead() || f.lastModified() > pltFile.lastModified())
        {// rebuild the PLT file since the source was modified or the plt file does not exist
        	pltFile.delete();
        	otpArgs[0]= new OtpErlangAtom("dialyzer");
   		 	ErlangRunner.getRunner().call(otpArgs,"Could not run dialyzer");
        }
        progress.next();// 3
        
        // Typer always has to be run
        otpArgs[0]= new OtpErlangAtom("typer");
		OtpErlangTuple response = ErlangRunner.getRunner().call(otpArgs,"Could not run typer");
        progress.next();// 4
       
        OtpErlangList analysisResults = (OtpErlangList)response.elementAt(1);
        Assert.assertEquals(1,analysisResults.arity());
        OtpErlangTuple fileDetails = (OtpErlangTuple)analysisResults.elementAt(0);
        OtpErlangList typeInformation = (OtpErlangList) fileDetails.elementAt(3);
        for(int i=0;i<typeInformation.arity();++i)
        {
        	FuncSignature s = new FuncSignature(typeInformation.elementAt(i));
        	sigs.put(s.getQualifiedName(), s);
        }
        
        behaviour = OTPBehaviour.obtainDeclaredBehaviour(f, this);
        progress.next();// 5
    }

    private static Collection<String> seekUsages(String funcName, File f) {
        Collection<String> result = new ArrayList<String>();

        // Open the Erlang source files...
        try {
            BufferedReader input = new BufferedReader(new FileReader(f));
            String line = null;
            while ((line = input.readLine()) != null) {
                // Look for calls to this func
                int ptr = line.indexOf(funcName + "(");
                while (ptr >= 0) {
                    System.out.println("Got call to " + funcName + " on line \"" + line + "\"");
                    int depth = 1;
                    ptr += (funcName + "(").length();
                    int start = ptr;
                    while ((depth > 0)&&(ptr <= line.length())) {
                        if((ptr == line.length())&&(depth > 0)) {
                            String newLine = input.readLine();
                            if(newLine != null) {
                                line += newLine;
                            }
                        }
                        // Allow for () in the argstring itself...
                        if (line.charAt(ptr) == '(') {
                            depth++;
                        } else if (line.charAt(ptr) == ')') {
                            depth--;
                        }
                        ptr++;
                    }
                    ptr--;
                    // Add to argument string to the result list
                    result.add(line.substring(start, ptr));
                    System.out.println("\t" + line.substring(start, ptr));
                    line = line.substring(ptr);
                    ptr = line.indexOf(funcName + "(");
                    // Loop for more occurences on this line
                }
            }
            input.close();
        } catch (IOException e) {
           Helper.throwUnchecked("read error", e);
        }

        return result;
    }

    public String getName() 
    {
    	assert name != null;
        return name;
    }

    @Override
    public String toString() {
        return getName() + " [" + behaviour.toString() + "] (" + behaviour.dependencies.size() + " dependecies)";
    }

    protected final static Map<String,ErlangModule> modulesRegistry = new TreeMap<String,ErlangModule>();

    public static ErlangModule loadModule(File module) throws IOException
    {
    	ErlangModule mod = new ErlangModule(module);
    	if (modulesRegistry.containsKey(mod.getName()))
    		throw new IllegalArgumentException("module "+mod.getName()+" is already loaded");
    	modulesRegistry.put(mod.getName(), mod);
    	return mod;
    }
    
    /** Finds the respective module - it cannot load one because this is usually called
     * using module name from a configuration which does not have a corresponding file name. */
	public static ErlangModule findModule(String erlangModuleName) {
		ErlangModule result = null;
		if (erlangModuleName != null) result = modulesRegistry.get(erlangModuleName);
		return result;
	}

}
