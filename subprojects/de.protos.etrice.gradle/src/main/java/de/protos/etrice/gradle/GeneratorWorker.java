package de.protos.etrice.gradle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Optional;

import org.eclipse.etrice.generator.base.GeneratorApplication;
import org.eclipse.etrice.generator.base.GeneratorException;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.workers.WorkAction;

/**
 * Gradle worker implementation for eTrice generators.
 * Generator applications are cached and reused.
 */
public abstract class GeneratorWorker implements WorkAction<GeneratorParameters> {
	private static final String MODULE_CLASS_NAME_LOCATION = "META-INF/generators/";
	private static final HashMap<String, GeneratorApplication> CACHE = new HashMap<>();
	
	/**
	 * Runs the generator with the passed arguments.
	 */
	@Override
	public void execute() {
		try {
			String module = getParameters().getModule().get();
			String[] args = getParameters().getArgs().get();
			getGeneratorApplication(module).run(args);
		}
		catch(GeneratorException e) {
			throw new GradleException(e.toString(), e);
		}
	}
	
	/**
	 * Get a generator application by its module name.
	 * 
	 * @param module the module name of the generator
	 * @return the generator application instance
	 */
	private static GeneratorApplication getGeneratorApplication(String module) {
		return CACHE.computeIfAbsent(module, m -> {
			try {
				String moduleClassName = lookupModuleClassName(m);
				return GeneratorApplication.create(moduleClassName);
			}
			catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
				throw new GradleException(e.toString(), e);
			}
		});
	}
	
	/**
	 * Tries to look up the module class name for a generator.
	 * 
	 * @return the module class name for the generator.
	 */
	private static String lookupModuleClassName(String module) {
		try(InputStream in = GeneratorWorker.class.getClassLoader().getResourceAsStream(MODULE_CLASS_NAME_LOCATION + module)) {
			return Optional.ofNullable(in)
				.map(is -> new BufferedReader(new InputStreamReader(is)))
				.flatMap(reader -> reader.lines().findFirst())
				.orElse(module);
		}
		catch(IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
