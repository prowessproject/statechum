/*
 * Copyright (c) 2006, 2007, 2008 Neil Walkinshaw and Kirill Bogdanov
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

package statechum;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import statechum.Configuration.IDMode;
import statechum.Configuration.LEARNER;
import statechum.Configuration.QuestionGeneratorKind;
import statechum.Configuration.ScoreMode;
import statechum.Helper.whatToRun;
import statechum.analysis.learning.rpnicore.LearnerGraph;
import static statechum.analysis.learning.rpnicore.TestFSMAlgo.equalityTestingHelper;

public class TestConfiguration {
	public TestConfiguration()
	{
		mainConfiguration = Configuration.getDefaultConfiguration();
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
		LearnerGraph.testMode=true;

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

	@Test
	public void testConfigurationEquals()
	{
		Configuration confA = new Configuration(), confB = new Configuration(),
			confC = new Configuration(), confD = new Configuration();
		confA.setBumpPositives(true);confA.setLearnerIdMode(Configuration.IDMode.NONE);confA.setDefaultInitialPTAName("test");
		confB.setBumpPositives(true);confB.setLearnerIdMode(Configuration.IDMode.NONE);confB.setDefaultInitialPTAName("test");
		confC.setBumpPositives(true);confC.setLearnerIdMode(Configuration.IDMode.NONE);confC.setDefaultInitialPTAName("a");
		confD.setLearnerIdMode(Configuration.IDMode.POSITIVE_NEGATIVE);
		equalityTestingHelper(confA, confB, confC, confD);
		confC.setDefaultInitialPTAName("test");confA.setDefaultInitialPTAName("b");
		equalityTestingHelper(confC, confB, confA, confD);
	}
	
	@Test
	public void testConfigurationClone()
	{
		Configuration confA = new Configuration(),
		confC = new Configuration(), confD = new Configuration();
		confA.setBumpPositives(true);confA.setLearnerIdMode(Configuration.IDMode.NONE);confA.setDefaultInitialPTAName("test");
		confC.setBumpPositives(true);confC.setLearnerIdMode(Configuration.IDMode.NONE);confC.setDefaultInitialPTAName("a");
		Configuration confClone = confA.copy();
		equalityTestingHelper(confA, confClone, confC, confD);
		
		confClone.setDefaultInitialPTAName("avalue");// mess up the clone
		equalityTestingHelper(confA, confA, confClone, confD);
		equalityTestingHelper(confA.copy(), confA, confClone, confD);
		
		confA.setDefaultInitialPTAName("avalue");// mess up the original the same way as the clone was messed up
		equalityTestingHelper(confA, confClone, confC, confD);
	}

	/** An obvious problem with Configuration is forgetting to include all 
	 * the necessary variables in equals and hashcode methods. This one
	 * checks that each change to instance variables affects the response from 
	 * those methods. This is accomplished by collecting all fields, identifying
	 * the corresponding setter methods and choosing two values for each
	 * of those fields. Subsequently, I can assign the first value to all fields
	 * and go through the fields setting the second one and checking that 
	 * this affects hashCode and equals. 
	 */ 
	@Test
	public void testConfigurationUsesAllItsVariables()
	{
		class MethodAndArgs {
			public MethodAndArgs(Method m, Field f, Object a, Object b)
			{
				method=m;Arg=a;AlternativeArg=b;field = f;
			}
			Field field;
			Method method;Object Arg, AlternativeArg;
		}

		List<MethodAndArgs> MethodsArgs=new LinkedList<MethodAndArgs>();
		for(Field var:Configuration.class.getDeclaredFields())
		{
			if (var.getType() != Configuration.class && 
					var.getName() != "$VRc"// added by eclemma (coverage analysis)
						&& !java.lang.reflect.Modifier.isFinal(var.getModifiers())
						)
			{
				String varName = var.getName();
				Method setter = Configuration.getMethod(Configuration.GETMETHOD_KIND.FIELD_SET, var);
				Object valueA = null, valueB = null;
				if (var.getType().equals(Boolean.class) || var.getType().equals(boolean.class))
				{
					valueA = new Boolean(true);valueB=new Boolean(false);
				}
				else
					if (var.getType().equals(Double.class) || var.getType().equals(double.class))
					{
						valueA = new Double(0.4);valueB=new Double(0.5);// note that we have to choose values which fall within the allowed range of values
					}
					else
					if (var.getType().equals(String.class))
					{
						valueA = varName+", value A";valueB=varName+", value B";
					}
					else
						if (var.getType().equals(IDMode.class))
						{
							valueA = IDMode.POSITIVE_NEGATIVE;valueB=IDMode.POSITIVE_ONLY;
						}
						else
							if (var.getType().equals(ScoreMode.class))
							{
								valueA = ScoreMode.KTAILS;valueB=ScoreMode.COMPATIBILITY;
							}
							else
								if (var.getType().equals(QuestionGeneratorKind.class))
								{
										valueA = QuestionGeneratorKind.CONVENTIONAL;valueB=QuestionGeneratorKind.SYMMETRIC;
								}
								else
									if (var.getType().equals(LEARNER.class))
									{
											valueA = LEARNER.LEARNER_BLUEFRINGE_MAY2008;valueB=LEARNER.LEARNER_BLUEAMBER_MAY2008;
									}
									else
									if (var.getType().equals(Integer.class) || var.getType().equals(int.class))
									{
										valueA = varName.hashCode();valueB=setter.hashCode();// just some integers likely to be different from each other between different variables.
									}
									else
										throw new IllegalArgumentException("A field "+var+" of Configuration has an unsupported type "+var.getType());
				
				MethodsArgs.add(new MethodAndArgs(setter,var,valueA,valueB));
			}
		}
		
		try {
			// Now check that hashCode and equals are affected by values of different fields.
			for(MethodAndArgs currentMethod:MethodsArgs)
			{
				Configuration 
					configA = Configuration.getDefaultConfiguration().copy(),
					configB = Configuration.getDefaultConfiguration().copy();
				for(MethodAndArgs orig:MethodsArgs)
				{
					orig.method.invoke(configA, new Object[]{orig.Arg});
					orig.method.invoke(configB, new Object[]{orig.Arg});
				}
				Assert.assertEquals(configB, configA);
				
				// now test that we can serialise these
				{
					org.w3c.dom.Element xmlB = configB.writeXML(doc),xmlA=configA.writeXML(doc);
					Configuration loadedB=new Configuration();loadedB.readXML(xmlB);Configuration loadedA=new Configuration();loadedA.readXML(xmlA);
					Assert.assertEquals(loadedB, loadedA);
					Assert.assertEquals(loadedB, configA);
				}

				currentMethod.method.invoke(configB, new Object[]{currentMethod.AlternativeArg});
				String errMsg = "configurations differ: field "+currentMethod.field+" is not in use for ";
				Assert.assertFalse(errMsg+"equals",configB.equals(configA));
				Assert.assertFalse(errMsg+"equals",configA.equals(configB));
				
				{
					org.w3c.dom.Element xmlB = configB.writeXML(doc),xmlA=configA.writeXML(doc);
					Configuration loadedB=new Configuration();loadedB.readXML(xmlB);Configuration loadedA=new Configuration();loadedA.readXML(xmlA);
					Assert.assertEquals(loadedA, configA);
					Assert.assertFalse(errMsg+"equals",loadedB.equals(loadedA));
					Assert.assertFalse(errMsg+"equals",loadedB.equals(configA));
					Assert.assertFalse(errMsg+"equals",loadedA.equals(loadedB));
				}
				
				Assert.assertTrue(errMsg+"hashCode",configA.hashCode() != configB.hashCode());
			}
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	/** Wrong tag. */
	@Test(expected=IllegalArgumentException.class)
	public void testSerialisationFailure1()
	{
		new Configuration().readXML(doc.createElement("test"));
	}
	
	/** No data to load. */
	@Test
	public void testSerialisationEmpty()
	{
		Configuration cnf = new Configuration();cnf.readXML(doc.createElement(Configuration.configXMLTag));
		Assert.assertEquals(new Configuration(), cnf);
	}
	
	/** Old data should not affect us. */
	@Test
	public void testSerialisationOldJunk_normal()
	{
		org.w3c.dom.Element elem = new Configuration().writeXML(doc), oldData = doc.createElement(Configuration.configVarTag);
		oldData.setAttribute(Configuration.configVarAttrName, "old_junk");
		oldData.setAttribute(Configuration.configVarAttrValue, "junk");
		
		elem.appendChild(oldData);
		Configuration cnf = new Configuration();cnf.readXML(elem);
		Assert.assertEquals(new Configuration(), cnf);
	}
	
	/** Old data causes an exception to be thrown. */
	@Test
	public void testSerialisationOldJunk_strict()
	{
		final org.w3c.dom.Element elem = new Configuration().writeXML(doc), oldData = doc.createElement(Configuration.configVarTag);
		oldData.setAttribute(Configuration.configVarAttrName, "old_junk");
		oldData.setAttribute(Configuration.configVarAttrValue, "junk");
		
		elem.appendChild(oldData);
		statechum.Helper.checkForCorrectException(new whatToRun() { public void run() {
			new Configuration().readXML(elem,true);
		}}, IllegalArgumentException.class,"cannot deserialise unknown field");
	}
	

	/** Unexpected tag. */
	@Test
	public void testSerialisationFailure2()
	{
		final org.w3c.dom.Element cnf = new Configuration().writeXML(doc);
		cnf.appendChild(doc.createElement("junk"));
		statechum.Helper.checkForCorrectException(new whatToRun() { public void run() {
			new Configuration().readXML(cnf);
		}},IllegalArgumentException.class,"unexpected element");
	}
		
	/** Text elements are ignored. */
	@Test
	public void testSerialisationFailure3a()
	{
		final org.w3c.dom.Element cnf = new Configuration().writeXML(doc);
		cnf.appendChild(doc.createTextNode(Configuration.configVarTag));
		Configuration c = new Configuration();c.readXML(cnf);
		Assert.assertEquals(new Configuration(),c);
	}
	
	/** Unexpected type of an element. */
	@Test
	public void testSerialisationFailure3b()
	{
		final org.w3c.dom.Element cnf = new Configuration().writeXML(doc);
		cnf.appendChild(doc.createComment(Configuration.configVarTag));
		statechum.Helper.checkForCorrectException(new statechum.Helper.whatToRun() { public void run() {
			new Configuration().readXML(cnf);
		}},IllegalArgumentException.class,"unexpected element");
	}
	
	/** Unexpected element. */
	@Test
	public void testSerialisationFailure4()
	{
		statechum.Helper.checkForCorrectException(new statechum.Helper.whatToRun() { public void run() {
			new Configuration().readXML(doc.createTextNode(Configuration.configXMLTag));
		}},IllegalArgumentException.class,"invalid node type passed to readXML");
	}
		
	/** Unexpected type of an element. */
	@Test
	public void testSerialisationFailure5()
	{
		statechum.Helper.checkForCorrectException(new statechum.Helper.whatToRun() { public void run() {
			new Configuration().readXML(doc.createElement("junk"));
		}},IllegalArgumentException.class,"configuration cannot be loaded from element");
	}
}
