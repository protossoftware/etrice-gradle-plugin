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
import org.gradle.api.tasks.CacheableTask;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

/**
 * Base task class for generator execution.
 */
@CacheableTask // TODO: inspect how cacheable task interacts with SourceTask (where inputs have PathSensitivity.ABSOLUTE)
public abstract class GenerateTask extends SourceTask {
	
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
	
	/**
	 * Creates a new task for a generator.
	 * 
	 * @param executor Gradle worker executor
	 * @param objects Gradle object factory
	 */
	@Inject
	public GenerateTask(WorkerExecutor executor, ObjectFactory objects) {
		this.executor = executor;
		
		this.classpath = objects.fileCollection();
		this.module = objects.property(String.class);
		this.genDir = objects.directoryProperty();
		this.modelpath = objects.fileCollection();
		this.options = objects.mapProperty(String.class, Object.class);
	}
	
	/**
	 * @return all files of the generator classpath
	 */
	@org.gradle.api.tasks.Classpath
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
	@org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
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
		
		// Submit the request to a worker process that runs the generator.
 		WorkQueue queue = executor.processIsolation(spec -> {
			// Since Gradle version 7.6, the Gradle worker api copies the jars on the classpath to a cache to
			// avoid file locks on the actual files, see https://github.com/gradle/gradle/pull/21475.
			// This also results in a new worker process if the files on the classpath are modified because the
			// file paths of the transformed classpath change every time the actual files are modified.
 			spec.getClasspath().from(getClasspath());
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
