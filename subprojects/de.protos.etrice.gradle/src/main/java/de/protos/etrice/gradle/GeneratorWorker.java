package de.protos.etrice.gradle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Optional;

import com.google.inject.Module;

import org.eclipse.etrice.generator.base.GeneratorApplication;
import org.gradle.api.UncheckedIOException;
import org.gradle.workers.WorkAction;

/**
 * Gradle worker implementation for eTrice generators.
 * Generator applications are cached and reused.
 */
public abstract class GeneratorWorker implements WorkAction<GeneratorParameters> {

	@Deprecated private static final String MODULE_CLASS_NAME_LOCATION = "META-INF/generators/";
	private static final HashMap<String, GeneratorApplication> CACHE = new HashMap<>();
	
	/**
	 * Runs the generator with the passed arguments.
	 */
	@Override
	public void execute() {
		String name = getParameters().getModule().get();
		String[] args = getParameters().getArgs().get();
		CACHE.computeIfAbsent(name, n -> {
			Module module = createGeneratorModule(n);
			return GeneratorApplication.create(module);
		}).run(args);
	}
	
	/**
	 * Tries to locate and instantiate the module of a generator by its name.
	 * 
	 * @param name the module name of the generator
	 * @return the module for the generator
	 * @throws IllegalArgumentException if the generator module could not be located
	 * 
	 * @deprecated The resolution of the generator module is now handled directly by
	 * {@link GeneratorApplication#create(String)} in eTrice. We still keep this
	 * functionality here to retain backwards compatibility to older eTrice versions.
	 */
	@Deprecated
	private static Module createGeneratorModule(String name) {
		// Try to read the generator module file to determine the module class name.
		// If no such file is present, interpret the name itself as module class name to retain backwards compatibility.
		try(InputStream in = GeneratorWorker.class.getClassLoader().getResourceAsStream(MODULE_CLASS_NAME_LOCATION + name)) {
			String moduleClassName = Optional.ofNullable(in)
				.map(is -> new BufferedReader(new InputStreamReader(is)))
				.flatMap(reader -> reader.lines().findFirst())
				.orElse(name);
			return (Module) Class.forName(moduleClassName).getDeclaredConstructor().newInstance();
		}
		catch(ClassNotFoundException e) {
			throw new IllegalArgumentException("could not find generator with module name " + name, e);
		}
		catch(IOException e) {
			throw new UncheckedIOException(e);
		}
		catch(InstantiationException | IllegalAccessException | InvocationTargetException |	NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}
}
