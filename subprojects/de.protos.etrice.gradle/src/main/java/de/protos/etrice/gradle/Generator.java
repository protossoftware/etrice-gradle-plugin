package de.protos.etrice.gradle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;

/**
 * Class for loading and executing generators.
 */
public class Generator {
	
	private static final String GENERATOR_APPLICATION_CLASS_NAME = "org.eclipse.etrice.generator.base.GeneratorApplication";
	private static final String CREATE_GENERATOR_METHOD_NAME = "create";
	private static final String RUN_GENERATOR_METHOD_NAME = "run";
	private static final String MODULE_CLASS_NAME_LOCATION = "META-INF/generators/";
	
	private ClassLoader classLoader;
	private String module;
	private Method runMethod;
	private Object generatorApplication;
	
	/**
	 * Creates a new generator runner using the specified class loader and module.
	 * 
	 * @param classLoader the class loader used to load the generator classes
	 * @param module the symbolic name of the generator
	 */
	public Generator(ClassLoader classLoader, String module) {
		this.classLoader = classLoader;
		this.module = module;
		
		this.runMethod = null;
		this.generatorApplication = null;
	}
	
	/**
	 * Loads and runs the generator.
	 * 
	 * @param args Generator options
	 */
	public void run(String[] args) {
		// Set system property to prevent EMFReferenceCleaner threads because they introduce a class loader leak
		System.getProperties().putIfAbsent("org.eclipse.emf.common.util.ReferenceClearingQueue", "false");
		
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		Thread.currentThread().setContextClassLoader(classLoader);
		try {
			createGenerator();
			runGenerator(args);
		}
		finally {
			Thread.currentThread().setContextClassLoader(contextClassLoader);
		}
	}
	
	/**
	 * Loads and creates the generator application.
	 */
	private void createGenerator() {
		if(generatorApplication == null) {
			try {
				Class<?> generatorApplicationClass = classLoader.loadClass(GENERATOR_APPLICATION_CLASS_NAME);
				Method createGeneratorApplicationMethod = generatorApplicationClass.getDeclaredMethod(CREATE_GENERATOR_METHOD_NAME, String.class);
				runMethod = generatorApplicationClass.getDeclaredMethod(RUN_GENERATOR_METHOD_NAME, String[].class);
				
				String moduleClassName = lookupModuleClassName();
				generatorApplication = createGeneratorApplicationMethod.invoke(null, moduleClassName);
			}
			catch(InvocationTargetException e) {
				String msg = e.getCause() == null ? e.toString() : e.getCause().toString();
				throw new GradleException(msg, e);
			}
			catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException e) {
				throw new GradleException(e.toString(), e);
			}
		}
	}
	
	/**
	 * Tries to look up the module class name in the resources.
	 * 
	 * @return the module class name for the generator.
	 */
	private String lookupModuleClassName() {
		try(InputStream in = classLoader.getResourceAsStream(MODULE_CLASS_NAME_LOCATION + module)) {
			return Optional.ofNullable(in)
				.map(is -> new BufferedReader(new InputStreamReader(is)))
				.flatMap(reader -> reader.lines().findFirst())
				.orElse(module);
		}
		catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	/**
	 * Runs the generator with the passed arguments.
	 * 
	 * @param args generator arguments
	 */
	private void runGenerator(String[] args) {
		try {
			runMethod.invoke(generatorApplication, (Object) args);
		}
		catch(InvocationTargetException e) {
			String msg = e.getCause() == null ? e.toString() : e.getCause().toString();
			throw new GradleException(msg, e);
		}
		catch (IllegalAccessException | IllegalArgumentException e) {
			throw new GradleException(e.toString(), e);
		}
	}
	
}
