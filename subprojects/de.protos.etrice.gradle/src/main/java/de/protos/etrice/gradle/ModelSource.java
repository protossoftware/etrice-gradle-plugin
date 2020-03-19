package de.protos.etrice.gradle;

import javax.inject.Inject;

import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskProvider;

/**
 * Group of model files and generator options.
 */
public class ModelSource implements Named {
	
	private final String name;
	private final SourceDirectorySet source;
	private final TaskProvider<GenerateTask> generateTask;
	
	@Inject
	public ModelSource(String name,	SourceDirectorySet source, TaskProvider<GenerateTask> generateTask) {
		this.name = name;
		this.source = source;
		this.generateTask = generateTask;
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	/**
	 * @return the classpath of the generator
	 */
	public ConfigurableFileCollection getClasspath() {
		return generateTask.get().getClasspath();
	}
	
	/**
	 * @return the symbolic generator name
	 */
	public Property<String> getModule() {
		return generateTask.get().getModule();
	}
	
	/**
	 * @return the model directories
	 */
	public SourceDirectorySet getSource() {
		return source;
	}
	
	/**
	 * @return the destination for generated files
	 */
	public DirectoryProperty getGenDir() {
		return generateTask.get().getGenDir();
	}
	
	/**
	 * @return the modelpath that is passed to the generator
	 */
	public ConfigurableFileCollection getModelpath() {
		return generateTask.get().getModelpath();
	}
	
	/**
	 * @return the options that are passed to the generator
	 */
	public MapProperty<String, Object> getOptions() {
		return generateTask.get().getOptions();
	}
	
	/**
	 * Sets a generator option.
	 * 
	 * @param key the name of the option
	 * @param value the new value for the option
	 */
	public void option(String key, Object value) {
		getOptions().put(key, value);
	}
	
	/**
	 * Sets a boolean generator option to {@code true}.
	 * 
	 * @param key the name of the option
	 */
	public void option(String key) {
		option(key, true);
	}
	
	/**
	 * @return a provider for the associated generate task.
	 */
	public TaskProvider<GenerateTask> getGenerateTask() {
		return generateTask;
	}
	
}
