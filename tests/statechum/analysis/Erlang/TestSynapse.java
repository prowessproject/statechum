package statechum.analysis.Erlang;

import static statechum.Helper.checkForCorrectException;

import java.io.File;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ericsson.otp.erlang.OtpErlangAtom;
import com.ericsson.otp.erlang.OtpErlangObject;
import com.ericsson.otp.erlang.OtpErlangPid;
import com.ericsson.otp.erlang.OtpErlangTuple;

import statechum.Configuration;
import statechum.DeterministicDirectedSparseGraph.VertID;
import statechum.GlobalConfiguration;
import statechum.GlobalConfiguration.G_PROPERTIES;
import statechum.Helper.whatToRun;
import statechum.analysis.Erlang.Synapse.StatechumProcess;
import statechum.analysis.learning.experiments.ExperimentRunner;
import statechum.analysis.learning.experiments.mutation.DiffExperiments.MachineGenerator;
import statechum.analysis.learning.linear.DifferenceVisualiser;
import statechum.analysis.learning.rpnicore.AMEquivalenceClass.IncompatibleStatesException;
import statechum.analysis.learning.rpnicore.LearnerGraph;
import statechum.analysis.learning.rpnicore.LearnerGraphND;
import statechum.analysis.learning.rpnicore.Transform.ConvertALabel;
import statechum.analysis.learning.rpnicore.WMethod;
import statechum.analysis.learning.rpnicore.WMethod.DifferentFSMException;

public class TestSynapse {

	protected ErlangRuntime erlRuntime = null, erlPingRuntime = null;
	
	/** This one is used to number work directories so that different tests do not affect each other. Unfortunately, the numbering is sequential hence it is not known which test corresponds to which number. */
	protected static int number = 0;
	
	/** URL of the writable directory to be used for tests. */
	public File testDir = null;
	
	/** URL of the locker example. */
	public final File erlangLocker = new File(GlobalConfiguration.getConfiguration().getProperty(G_PROPERTIES.PATH_ERLANGEXAMPLES),"locker"+File.separator+"locker.erl");
	
	protected ErlangRunner runner, pingRunner;
	
	File output = null;

	@Before
	public void beforeTest()
	{
		erlRuntime = new ErlangRuntime();erlRuntime.setTimeout(500);erlRuntime.startRunner();runner = erlRuntime.createNewRunner();
		erlPingRuntime = new ErlangRuntime();erlPingRuntime.setTimeout(500);erlPingRuntime.startRunner();pingRunner = erlPingRuntime.createNewRunner();
		
		testDir = new File(GlobalConfiguration.getConfiguration().getProperty(G_PROPERTIES.TEMP),"__TestErlangRunner__"+(number++));
		output = new File(testDir,"test.erl");
		createTestDir();
	}
	
	public void createTestDir()
	{
		if (!testDir.isDirectory())
		{
			Assert.assertTrue("could not create "+testDir.getAbsolutePath(),testDir.mkdir());
		}
	}

	@After
	public void afterTest()
	{
		if (erlRuntime != null) erlRuntime.killErlang();if (erlPingRuntime != null) erlPingRuntime.killErlang();
		zapTestDir();
	}
	
	public void zapTestDir()
	{
		ExperimentRunner.zapDir(testDir);
	}

	public TestSynapse() {
		
	}
	
	@Test
	public void testEunitSynapseLauncherTests()
	{
		Assert.assertEquals("'ok'",ErlangLabel.dumpErlangObject(runner.evaluateString("testsynapselauncher:test()")));		
	}
	
	@Test
	public void testEunitSynapseTests()
	{
		startSynapse();
		Assert.assertEquals("'ok'",ErlangLabel.dumpErlangObject(runner.evaluateString("testsynapse:test()")));		
	}
	
	/** No statechum running */
	@Test
	public void testFindSynapseFailure()
	{
		Assert.assertEquals(new OtpErlangAtom("not_started"),runner.evaluateString("synapselauncher:find_statechum()"));
	}

	/** Invalid options before launching. */
	@Test
	public void testRunSynapseLaunchFailure0()
	{
		String java = (System.getProperty("java.home")+File.separator+"bin/java").replace(File.separatorChar,'/');
		Assert.assertTrue(
				runner.evaluateString("process_flag(trap_exit, true),"+
						"spawn_link(fun() -> synapselauncher:startStatechum([{'Java','"+java+"'},{'JavaOptionsList',[{'-DOtpConnection.trace','0'}] },{'AccumulateOutput','false'},[{'pp','qq'}] ]) end)," // this will fail if we cannot start Erlang
						+ "Response = receive Arg -> Arg end,"
						+ "process_flag(trap_exit, false),Response").toString().contains("Tuple is not key-value pair"));
	}

	/** Invalid executable */
	@Test
	public void testRunSynapseLaunchFailure1()
	{
		Assert.assertTrue(
			runner.evaluateString("process_flag(trap_exit, true),"+
					"spawn_link(fun() -> synapselauncher:startStatechum([{'Java','aa'},{'JavaOptionsList',[{'-DOtpConnection.trace','0'}] },{'AccumulateOutput','false'} ]) end)," // this will fail if we cannot start Erlang
					+ "Response = receive Arg -> Arg end,"
					+ "process_flag(trap_exit, false),Response").toString().contains("spawn_executable,aa"));
	}

	/** Invalid options to executable */
	@Test
	public void testRunSynapseLaunchFailure2()
	{
		String java = (System.getProperty("java.home")+File.separator+"bin/java").replace(File.separatorChar,'/');
		Assert.assertTrue(
				runner.evaluateString("process_flag(trap_exit, true),"+
						"spawn_link(fun() -> synapselauncher:startStatechum([{'Java','"+java+"'},{'JavaOptionsList',[{'-DOtpConnection.trace','0'},{'pp','qq'}] },{'AccumulateOutput','false'} ]) end)," // this will fail if we cannot start Erlang
						+ "Response = receive Arg -> Arg end,"
						+ "process_flag(trap_exit, false),Response").toString().contains("Timeout waiting for node"));
	}
	
	@Test
	public void testRunSynapseLaunchFailure3()
	{
		String java = (System.getProperty("java.home")+File.separator+"bin/java").replace(File.separatorChar,'/');
		String response = runner.evaluateString("process_flag(trap_exit, true),"+
				"spawn_link(fun() -> synapselauncher:startStatechum([{'Java','"+java+"'},{'JavaOptionsList',[{'-DOtpConnection.trace','0'},{'-DSYNAPSE_TERMINATE','true'}] },{'AccumulateOutput','false'} ]) end)," // this will fail if we cannot start Erlang
				+ "Response = receive Arg -> Arg end,"
				+ "process_flag(trap_exit, false),Response").toString();
		Assert.assertTrue(
				response.contains("Timeout waiting for echo response"));
	}

	@Test
	public void testRunSynapse1()
	{
		String java = (System.getProperty("java.home")+File.separator+"bin/java").replace(File.separatorChar,'/');
		String response = ErlangLabel.dumpErlangObject(runner.evaluateString("synapselauncher:startStatechum([{'Java','"+java+"'},{'JavaOptionsList',[{'-DOtpConnection.trace','0'}] },{'AccumulateOutput','true'}]),"
				+ "synapselauncher:find_statechum()!terminate," //io:format(\"waiting for response~n\"),"
				+ "receive Arg -> Arg end"));
		Assert.assertTrue(response.contains("Synapse started"));Assert.assertTrue(response.contains("Synapse terminated"));
	}
	
	/** Deterministic case. */
	@Test
	public void testParseAutomata1() throws IncompatibleStatesException
	{
		ConvertALabel converter = null;
		Configuration config = Configuration.getDefaultConfiguration().copy();
		Random rnd=new Random(0);
		for(int states=1;states < 100;states++)
		{
			final int alphabet = 2*states;
			MachineGenerator mg = new MachineGenerator(states, 400 , (int)Math.round((double)states/5));mg.setGenerateConnected(true);
			LearnerGraph graph = mg.nextMachine(alphabet,-states, config, converter).pathroutines.buildDeterministicGraph();
						
			LearnerGraph parsedOutcome = new LearnerGraph(config);
			StatechumProcess.parseStatemachine(StatechumProcess.constructFSM(graph), parsedOutcome, converter,true);
			DifferentFSMException diffException = WMethod.checkM(graph, parsedOutcome);
			Assert.assertNull(diffException);

			Map<VertID,VertID> map = new TreeMap<VertID,VertID>();
			for(int i=0;i<20;++i) map.put(graph.pathroutines.pickRandomState(rnd),graph.pathroutines.pickRandomState(rnd));
			Map<VertID,VertID> mapOutcome = StatechumProcess.parseMap(StatechumProcess.mapToObject(map));
			Assert.assertEquals(map,mapOutcome);
		}
	}
	
	/** Non-deterministic case. */
	@Test
	public void testParseAutomata2()
	{
		ConvertALabel converter = null;
		Configuration config = Configuration.getDefaultConfiguration().copy();config.setGdFailOnDuplicateNames(false);
		Random rnd=new Random(0);
		LearnerGraphND previous = null;
		
		for(int states=1;states < 100;states++)
		{
			final int alphabet = 2*states;
			MachineGenerator mg = new MachineGenerator(states, 400 , (int)Math.round((double)states/5));mg.setGenerateConnected(true);
			LearnerGraphND graph = mg.nextMachine(alphabet,-states, config, converter);
			
			LearnerGraphND parsedOutcome = new LearnerGraphND(config);
			StatechumProcess.parseStatemachine(StatechumProcess.constructFSM(graph), parsedOutcome, converter,true);
			DifferentFSMException diffException = WMethod.checkM(graph, parsedOutcome);
			Assert.assertNull(diffException);

			Map<VertID,VertID> map = new TreeMap<VertID,VertID>();
			for(int i=0;i<20;++i) map.put(graph.pathroutines.pickRandomState(rnd),graph.pathroutines.pickRandomState(rnd));
			Map<VertID,VertID> mapOutcome = StatechumProcess.parseMap(StatechumProcess.mapToObject(map));
			Assert.assertEquals(map,mapOutcome);
			
			if (previous != null)
			{
				LearnerGraphND shouldBeLikePrevious = new LearnerGraphND(graph,config);
				OtpErlangObject difference = DifferenceVisualiser.ChangesToGraph.computeGD(graph, previous, config);
				DifferenceVisualiser.ChangesToGraph.load(difference).applyDiff(shouldBeLikePrevious, config);
				DifferentFSMException ex = WMethod.checkM(previous, shouldBeLikePrevious);
				Assert.assertNull(ex);
			}
			previous = graph;
		}
	}
	
	@Test
	public void testRunSynapse2a() throws InterruptedException
	{
		String java = (System.getProperty("java.home")+File.separator+"bin/java").replace(File.separatorChar,'/');
		String synapseNode = ErlangLabel.dumpErlangObject(runner.evaluateString("synapselauncher:startStatechum([{'Java','"+java+"'},{'JavaOptionsList',[{'-DOtpConnection.trace','0'}] },{'AccumulateOutput','false'}]),"
				+ "Ref=make_ref(),"
				+ "synapselauncher:find_statechum()!{self(),Ref,getNodeName},"
				+ "receive {Ref,ok,Value} -> "
				+ "Value end"));
		Assert.assertTrue(pingNode(synapseNode));
		
		erlRuntime.killErlang();
		int i=0;
		while(pingNode(synapseNode) && i < 100) // wait for Synapse to terminate
		{
			Thread.sleep(100);
			++i;
		}
		Assert.assertFalse(pingNode(synapseNode));// ensure Synapse has terminated
	}
	
	/** Tests that termination of a parent Erlang process kills Java. 
	 * @throws InterruptedException */
	@Test
	public void testRunSynapse2b() throws InterruptedException
	{
		String java = (System.getProperty("java.home")+File.separator+"bin/java").replace(File.separatorChar,'/');
		OtpErlangTuple pid_node = (OtpErlangTuple)runner.evaluateString("OurPid=self(),Pid = spawn(fun () -> synapselauncher:startStatechum([{'Java','"+java+"'},{'JavaOptionsList',[{'-DOtpConnection.trace','0'}] },{'AccumulateOutput','false'}]),OurPid!ok,receive stop -> ok end end),"
				+ "receive ok -> ok end,"
				+ "Ref=make_ref(),"
				+ "synapselauncher:find_statechum()!{self(),Ref,getNodeName},"
				+ "receive {Ref,ok,Value} -> "
				+ "{Pid,Value} end");
		Assert.assertEquals(2,pid_node.arity());
		String synapseNode = ErlangLabel.dumpErlangObject(pid_node.elementAt(1));
		OtpErlangPid pid = (OtpErlangPid)pid_node.elementAt(0);// PID of the parent process
		
		// Check that Synapse is running.
		Assert.assertTrue(pingNode(synapseNode));
		
		// Terminate the parent process
		runner.thisMbox.send(pid, new OtpErlangAtom("stop"));// send message to parent process asking it to terminate
		
		int i=0;
		while(pingNode(synapseNode) && i < 100) // wait for Synapse to terminate
		{
			Thread.sleep(100);
			++i;
		}
		Assert.assertFalse(pingNode(synapseNode));// ensure Synapse has terminated
	}
	
	/** Tests that termination of a process linked to a worker kill the worker.
	 * @throws InterruptedException */
	@Test
	public void testRunSynapse2c() throws InterruptedException
	{
		String java = (System.getProperty("java.home")+File.separator+"bin/java").replace(File.separatorChar,'/');
		runner.evaluateString("synapselauncher:startStatechum([{'Java','"+java+"'},{'JavaOptionsList',[{'-DOtpConnection.trace','0'}] },{'AccumulateOutput','true'}]),"+
				"OurPid=self(),Ref=make_ref(),Pid = spawn(fun () -> synapselauncher:find_statechum()!{self(),Ref,getStatechumWorker},receive {Ref,WorkerPid} -> "
				+"WorkerPid!{Ref,echo},receive {Ref,workerok} ->ok,throw(worker_parent_failed)" // check that worker is ok and then make an abnormal termination. Nnow the worker should terminate and this is to appear on standard output.
				+" end end end)" //
				);
		Thread.sleep(500);// This gives time to Erlang to propagate an error to the Java node.
		String response = ErlangLabel.dumpErlangObject(runner.evaluateString("synapselauncher:find_statechum()!terminate," //io:format(\"waiting for response~n\"),"
				+ "receive Arg -> Arg end"));
		Assert.assertTrue(response.contains("Node exited com.ericsson.otp.erlang.OtpErlangExit: {{nocatch,worker_parent_failed}"));
	}
	
	/** Pings a node provided.
	 *  The argument should have quotes around it unless it can be parsed as an atom by Erlang.
	 *  
	 * @param node node name
	 * @return true if ping returned success, false otherwise.
	 */
	protected boolean pingNode(String node)
	{// we cannot ping Java nodes from Java, it seems but can ping them from Erlang. Do this here. 
		return Boolean.parseBoolean( ((OtpErlangAtom)pingRunner.evaluateString("case net_adm:ping("+node.replace("-","\\-")+") of pong -> true; pang -> false end")).atomValue());
	}
	
	@Test
	public void testRunSynapse3() throws InterruptedException
	{// ,{'Cookie','"+ErlangNode.getErlangNode().getNode().cookie()+"'}
		String java = (System.getProperty("java.home")+File.separator+"bin/java").replace(File.separatorChar,'/');
		String synapseNode = ErlangLabel.dumpErlangObject(runner.evaluateString("synapselauncher:startStatechum([{'Java','"+java+"'},{'JavaOptionsList',[{'-DOtpConnection.trace','0'}] },{'AccumulateOutput','true'}]),"
				+ "Ref=make_ref(),"
				+ "synapselauncher:find_statechum()!{self(),Ref,getNodeName},"
				+ "receive {Ref,ok,Value} -> "
				+ "Value end"));
		Assert.assertTrue(pingNode(synapseNode));
		
		String response = ErlangLabel.dumpErlangObject(runner.evaluateString("synapselauncher:find_statechum()!terminate," //io:format(\"waiting for response~n\"),"
				+ "receive Arg -> Arg end"));
		Assert.assertTrue(response.contains("Synapse started"));Assert.assertTrue(response.contains("Synapse terminated"));
		int i=0;
		while(pingNode(synapseNode) && i < 100) // wait for Synapse to terminate
		{
			Thread.sleep(100);
			++i;
		}
		Assert.assertFalse(pingNode(synapseNode));// ensure Synapse has terminated
	}
	
	
	/** Starts Synapse and returns the associated pid. */
	public OtpErlangPid startSynapse()
	{
		return startSynapse(false);
	}
	
	/** Starts Synapse and returns the associated pid. */
	public OtpErlangPid startSynapse(boolean accumulateOutput)
	{
		String java = (System.getProperty("java.home")+File.separator+"bin/java").replace(File.separatorChar,'/');
		return (OtpErlangPid)runner.evaluateString("synapselauncher:startStatechum([{'Java','"+java+"'},{'JavaOptionsList',[{'-DOtpConnection.trace','0'},{'-DERLANGHOME','"+
				GlobalConfiguration.getConfiguration().getProperty(GlobalConfiguration.G_PROPERTIES.ERLANGHOME).replace('\\', '/')+"'}] },{'AccumulateOutput','" + new Boolean(accumulateOutput).toString().toLowerCase()+"'}]),"
				+ "Ref=make_ref(),"
				+ "synapselauncher:find_statechum()");		
	}
	
	/** Same as above but we do not accumulate output. 
	 * @throws InterruptedException */
	@Test
	public void testRunSynapse4() throws InterruptedException
	{
		String java = (System.getProperty("java.home")+File.separator+"bin/java").replace(File.separatorChar,'/');
		String synapseNode = ErlangLabel.dumpErlangObject(runner.evaluateString("synapselauncher:startStatechum([{'Java','"+java+"'},{'JavaOptionsList',[{'-DOtpConnection.trace','0'}] },{'AccumulateOutput','false'}]),"
				+ "Ref=make_ref(),"
				+ "synapselauncher:find_statechum()!{self(),Ref,getNodeName},"
				+ "receive {Ref,ok,Value} -> "
				+ "Value end"));
		Assert.assertTrue(pingNode(synapseNode));// check that Synapse is up
		
		checkForCorrectException(new whatToRun() { public @Override void run() {
			runner.evaluateString("synapselauncher:find_statechum()!terminate,"
				+ "receive Arg -> Arg end");
		}},IllegalArgumentException.class,"timeout waiting for a response");// when we do not collect output, no response is sent.
		
		int i=0;
		while(pingNode(synapseNode) && i < 100) // wait for Synapse to terminate
		{
			Thread.sleep(100);
			++i;
		}
		Assert.assertFalse(pingNode(synapseNode));// ensure Synapse has terminated
	}
	
	/** Start Synapse, send an invalid command. */
	@Test
	public void testRunSynapse5()
	{
		startSynapse();
		
		Assert.assertEquals("ok",runner.evaluateString("Ref=make_ref(),synapselauncher:find_statechum()!{self(),Ref,junk},receive {Ref,invalidcommand_or_missing_args} -> ok end").toString());
	}

	/** Start Synapse, send a command an invalid format, get no reponse. */
	@Test
	public void testRunSynapse6()
	{
		startSynapse();
		
		Assert.assertEquals("true",runner.evaluateString("Ref=make_ref(),synapselauncher:find_statechum()!{junk,self(),Ref,Ref},receive A -> false after 1000 -> true end").toString());
	}

	/** Start Synapse, start worker, check echo. */
	@Test
	public void testRunSynapse7()
	{
		startSynapse();
		
		Assert.assertEquals("ok",runner.evaluateString("Ref=make_ref(),synapselauncher:find_statechum()!{self(),Ref,getStatechumWorker},receive {Ref,Pid} -> ARef = make_ref(),Pid!{ARef,echo},receive {ARef,workerok} -> ok end end").toString());
	}
	
	/** Start Synapse, start worker, check invalid command. */
	@Test
	public void testRunSynapse8()
	{
		startSynapse();
		
		Assert.assertEquals("ok",runner.evaluateString("Ref=make_ref(),synapselauncher:find_statechum()!{self(),Ref,getStatechumWorker},receive {Ref,Pid} -> ARef = make_ref(),Pid!{ARef,junk},receive {ARef,invalidcommand_or_missing_args} -> ok end end").toString());
	}
	
	/** Start Synapse, start worker, stop worker. */
	@Test
	public void testRunSynapse9()
	{
		startSynapse(true);
		
		System.out.println(runner.evaluateString("Ref=make_ref(),synapselauncher:find_statechum()!{self(),Ref,getStatechumWorker},receive {Ref,Pid} -> ARef = make_ref(),Pid!{ARef,terminate},receive after 1000 -> ok end end"));
	}
}
