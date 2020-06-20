package de.protos.etrice.gradle;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.classpath.CachedClasspathTransformer.StandardTransform;

/**
 * Base task class for generator execution. 
 */
public class GenerateTask extends SourceTask {
	
	public static final String OPTION_GENDIR = "genDir";
	public static final String OPTION_MODELPATH = "modelpath";
	public static final String OPTION_CLEAN = "clean";
	public static final String OPTION_LOGLEVEL = "loglevel";
	
	private final CachedClasspathTransformer classpathTransformer;
	
	private final ConfigurableFileCollection classpath;
	private final Property<String> module;
	private final DirectoryProperty genDir;
	private final ConfigurableFileCollection modelpath;
	private final MapProperty<String, Object> options;
	
	private Generator generator;
	
	/**
	 * Creates a new task for a generator.
	 * 
	 * @param classpathTransformer Gradle classpath transformer
	 */
	@Inject
	public GenerateTask(CachedClasspathTransformer classpathTransformer, ObjectFactory objects) {
		this.classpathTransformer = classpathTransformer;
		
		this.classpath = objects.fileCollection();
		this.module = objects.property(String.class);
		this.genDir = objects.directoryProperty();
		this.modelpath = objects.fileCollection();
		this.options = objects.mapProperty(String.class, Object.class);
	}
	
	/**
	 * @return all files of the generator classpath
	 */
	@InputFiles
	public ConfigurableFileCollection getClasspath() {
		return classpath;
	}
	
	/**
	 * @return the symbolic name of the generator
	 */
	@Input
	public Property<String> getModule() {
		return module;
	}
	
	/**
	 * @return All generator options
	 */
	@Input
	public MapProperty<String, Object> getOptions() {
		return options;
	}
	
	/**
	 * @return Directory for generated source files
	 */
	@OutputDirectory
	public DirectoryProperty getGenDir() {
		return genDir;
	}
	
	/**
	 * @return the modelpath for the generator
	 */
	@InputFiles
	public ConfigurableFileCollection getModelpath() {
		return modelpath;
	}
	
	/**
	 * Executes the generator with the configured arguments.
	 */
	@TaskAction
	protected void generate() {
		String[] args = collectArguments();
		getGenerator().run(args);
	}
	
	/**
	 * Assembles the command line arguments for the generator using the source file collection and options map.
	 * 
	 * @return the command line arguments for the generator
	 */
	private String[] collectArguments() {
		LinkedList<String> args = new LinkedList<>();
		
		addArgument(args, OPTION_GENDIR, getGenDir().get().getAsFile());
		if(!getModelpath().isEmpty()) {
			addArgument(args, OPTION_MODELPATH, getModelpath().getAsPath());
		}
		
		for(Entry<String, Object> entry: getOptions().get().entrySet()) {
			addArgument(args, entry.getKey(), entry.getValue());
		}
		
		for(File f : getSource()) {
			args.add(f.getPath());
		}
		
		String[] argsArr = new String[args.size()];
		return args.toArray(argsArr);
	}
	
	/**
	 * Adds an option specified by its key and value to an argument list. 
	 * 
	 * @param args the argument list
	 * @param key the name of the option
	 * @param value the value of the option
	 */
	private static void addArgument(List<String> args, String key, Object value) {
		String option = "-" + key;
		
		if(value instanceof Boolean) {
			if((boolean) value) {
				args.add(option);
			}
		}
		else {
			args.add(option);
			if(value instanceof File) {
				args.add(((File) value).getAbsolutePath());
			}
			else {
				args.add(value.toString());
			}
		}
	}
	
	/**
	 * @return A generator instance for the configured generator
	 */
	@Internal
	protected Generator getGenerator() {
		if(generator == null) {
			// cache the classpath using the internal Gradle api to avoid file locks on original classpath files
			ClassPath cp = DefaultClassPath.of(getClasspath());
			ClassPath cachedCp =  classpathTransformer.transform(cp, StandardTransform.None);
			generator = GlobalGeneratorProvider.getGenerator(cachedCp.getAsFiles(), module.get());
		}
		return generator;
	}
}
