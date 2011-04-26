package org.junit.runners;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;


/** Licensed under CPL since it is almost verbatim from JUnit 4.5.
 * 
 * @author kirill
 *
 */
public class Parameterized extends Suite {
		/**
		 * Annotation for a method which provides parameters to be injected into the
		 * test class constructor by <code>Parameterized</code>
		 */
		@Retention(RetentionPolicy.RUNTIME)
		@Target(ElementType.METHOD)
		public static @interface Parameters {
		}

		private class TestClassRunnerForParameters extends
				BlockJUnit4ClassRunner {
			private final int fParameterSetNumber;

			private final List<Object[]> fParameterList;
			private final String fParameterDescr;
			
			TestClassRunnerForParameters(Class<?> type,
					List<Object[]> parameterList, int i) throws InitializationError {
				super(type);
				fParameterList= parameterList;
				fParameterSetNumber= i;
				final Object [] parameters = fParameterList.get(fParameterSetNumber);
				String name = convertParametersToString(type,parameters);
				fParameterDescr = name != null? name:parametersToStringDirectly(parameters);
			}

			private String parametersToStringDirectly(Object [] parameters)
			{
				StringBuffer buffer = new StringBuffer();
				boolean first = true;
				for(Object obj:parameters)
				{
					if (!first) buffer.append('_');else first = false;
					buffer.append(obj.toString());
				}
				return buffer.toString();
			}
			
			private String convertParametersToString(Class<?> type,Object [] parameters)
			{
				String result = null;
				Class<?> [] argTypes = new Class<?>[parameters.length];
				for(int i=0;i<parameters.length;++i) argTypes[i]=parameters[i].getClass();
				try {
					java.lang.reflect.Method converterMethod = type.getMethod("parametersToString", argTypes);
					if (converterMethod != null && java.lang.reflect.Modifier.isStatic(converterMethod.getModifiers()))
						result = converterMethod.invoke(null, parameters).toString();
				} catch (Exception e) {
					// cannot do this, explain why
					System.out.println("was looking for types "+Arrays.toString(argTypes));
					for(Method constr:type.getMethods())
						System.out.println(constr);
					
				}
				return result;
			}
			
			@Override
			public Object createTest() throws Exception {
				return getTestClass().getOnlyConstructor().newInstance(
						computeParams());
			}

			private Object[] computeParams() throws Exception {
				try {
					return fParameterList.get(fParameterSetNumber);
				} catch (ClassCastException e) {
					throw new Exception(String.format(
							"%s.%s() must return a Collection of arrays.",
							getTestClass().getName(), getParametersMethod(
									getTestClass()).getName()));
				}
			}

			@Override
			protected String getName() {
				return String.format("[%s,%s]", fParameterSetNumber,fParameterDescr);
			}

			@Override
			protected String testName(final FrameworkMethod method) {
				return String.format("%s[%s,%s]", method.getName(),
						fParameterSetNumber,fParameterDescr);
			}

			@Override
			protected void validateZeroArgConstructor(List<Throwable> errors) {
				// constructor can, nay, should have args.
			}

			@Override
			protected Statement classBlock(RunNotifier notifier) {
				return childrenInvoker(notifier);
			}
		}

		private final ArrayList<Runner> runners= new ArrayList<Runner>();

		/**
		 * Only called reflectively. Do not use programmatically.
		 */
		public Parameterized(Class<?> klass) throws Throwable {
			super(klass, Collections.<Runner>emptyList());
			List<Object[]> parametersList= getParametersList(getTestClass());
			for (int i= 0; i < parametersList.size(); i++)
				runners.add(new TestClassRunnerForParameters(getTestClass().getJavaClass(),
						parametersList, i));
		}

		@Override
		protected List<Runner> getChildren() {
			return runners;
		}

		@SuppressWarnings("unchecked")
		private List<Object[]> getParametersList(TestClass klass)
				throws Throwable {
			return (List<Object[]>) getParametersMethod(klass).invokeExplosively(
					null);
		}

		private FrameworkMethod getParametersMethod(TestClass testClass)
				throws Exception {
			List<FrameworkMethod> methods= testClass
					.getAnnotatedMethods(Parameters.class);
			for (FrameworkMethod each : methods) {
				int modifiers= each.getMethod().getModifiers();
				if (Modifier.isStatic(modifiers) && Modifier.isPublic(modifiers))
					return each;
			}

			throw new Exception("No public static parameters method on class "
					+ testClass.getName());
		}

}