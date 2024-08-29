package de.protos.etrice.gradle;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.gradle.api.JavaVersion;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.classpath.CachedClasspathTransformer.StandardTransform;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

/**
 * Base task class for generator execution. 
 */
public class GenerateTask extends SourceTask {
	
	public static final String OPTION_GENDIR = "genDir";
	public static final String OPTION_MODELPATH = "modelpath";
	public static final String OPTION_CLEAN = "clean";
	public static final String OPTION_LOGLEVEL = "loglevel";
	
	private final ConfigurableFileCollection classpath;
	private final Property<String> module;
	private final DirectoryProperty genDir;
	private final ConfigurableFileCollection modelpath;
	private final MapProperty<String, Object> options;
	
	private final WorkerExecutor executor;
	private final CachedClasspathTransformer transformer;
	
	/**
	 * Creates a new task for a generator.
	 * 
	 * @param executor Gradle worker executor
	 * @param transformer Gradle cached classpath transformer
	 * @param objects Gradle object factory
	 */
	@Inject
	public GenerateTask(WorkerExecutor executor, CachedClasspathTransformer transformer, ObjectFactory objects) {
		this.executor = executor;
		this.transformer = transformer;
		
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
		// Assemble the command line arguments
		String[] args = collectArguments();
		
		// Copy the jars on the classpath to a cache to avoid file locks on the actual files.
		// This also results in a new worker process if the files on the classpath are modified because
		// the file paths of the transformed classpath change every time the actual files are modified.
		// Unfortunately the Gradle worker api doesn't take care of these issues and this
		// approach uses internal Gradle api.
		ClassPath cp = DefaultClassPath.of(getClasspath());
		ClassPath cachedCp =  transformer.transform(cp, StandardTransform.None);
		
		// Submit the request to a worker process that runs the generator.
		WorkQueue queue = executor.processIsolation(spec -> {
			spec.getClasspath().from(cachedCp.getAsFiles());
			spec.forkOptions(forkOptions -> {
				// Environment variables are not forwarded to worker processes by default,
				// see https://github.com/gradle/gradle/issues/8030.
				// This breaks for example Files.createTempFile and Files.createTempDirectory on Windows.
				// Therefore, we explicitly forward all environment variables to the worker process here.
				forkOptions.environment(System.getenv());
				// The following JVM flag allows to run older eTrice versions (which use Xtext 2.25) with Java 17+
				// and silences illegal reflective access warnings that appear since Java 9+.
				// The issue originates in old versions of guice which was updated in more recent Xtext versions, 
				// see https://github.com/google/guice/issues/1085.
				if(JavaVersion.current().isJava9Compatible()) {
					forkOptions.jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED");
				}
			});
		});
		queue.submit(GeneratorWorker.class, params -> {
			params.getModule().set(module);
			params.getArgs().set(args);
		});
		
		// Wait for the worker process to complete the code generation.
		// Otherwise, subsequent generate tasks spawn additional worker processes if this worker
		// process is still busy. This can lead to an excessive amount of worker processes.
		// Parallel execution of Gradle can still be utilized to run generate tasks of different
		// projects in parallel in separate worker processes.
		queue.await();
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
}
