package de.protos.etrice.gradle;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JvmEcosystemPlugin;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;

/**
 * Sets up basic configurations and tasks to generate code from models.
 */
public class ETriceBasePlugin implements Plugin<Project> {

	public static final String MODEL_EXTENSION_NAME = "modelSet";
	
	public static final String GENERATOR_CONFIGURATION_NAME = "generator";
	public static final String GENERATE_CLASSPATH_CONFIGURATION_NAME = "generateClasspath";
	public static final String MODELPATH_CONFIGURATION_NAME = "modelpath";
	public static final String MODELPATH_DIR_CONFIGURATION_NAME = "modelpathDir";
	public static final String MODELPATH_ZIP_CONFIGURATION_NAME = "modelpathZip";
	public static final String GENERATE_MODELPATH_CONFIGURATION_NAME = "generateModelpath";
	
	public static final String GENERATE_TASK_NAME = "generate";
	public static final String ZIP_MODEL_TASK_NAME = "zipModel";
	public static final String ECLIPSE_MODELPATH_TASK_NAME = "eclipseModelpath";
	
	public static final String LIBRARY_ELEMENTS_MODEL_DIR = "model-dir";
	public static final String LIBRARY_ELEMENTS_MODEL_ZIP = "model-zip";
	
	@Override
	public void apply(Project project) {
		final PluginContainer plugins = project.getPlugins();
		final ConfigurationContainer configurations = project.getConfigurations();
		final DependencyHandler dependencies = project.getDependencies();
		final TaskContainer tasks = project.getTasks();
		final ProjectLayout layout = project.getLayout();
		final ObjectFactory objects = project.getObjects();
		
		plugins.apply(BasePlugin.class);
		plugins.apply(JvmEcosystemPlugin.class);
		plugins.apply(AdhocComponentPlugin.class);
		
		dependencies.getAttributesSchema().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, strategy -> {
				strategy.getCompatibilityRules().add(LibraryElementsCompatiblityRule.class);
				strategy.getDisambiguationRules().add(LibraryElementsDisambiguationRule.class, actionConfiguration -> {
					actionConfiguration.params(
						objects.named(LibraryElements.class, LIBRARY_ELEMENTS_MODEL_DIR),
						objects.named(LibraryElements.class, LIBRARY_ELEMENTS_MODEL_ZIP),
						objects.named(LibraryElements.class, LibraryElements.JAR)
					);
				});
			});
		
		NamedDomainObjectProvider<Configuration> generator = configurations.register(GENERATOR_CONFIGURATION_NAME, c -> {
			c.setCanBeConsumed(false);
			c.setCanBeResolved(false);
			c.setVisible(false);
		});
		NamedDomainObjectProvider<Configuration> generatorClasspath = configurations.register(GENERATE_CLASSPATH_CONFIGURATION_NAME, c -> {
			c.setCanBeConsumed(false);
			c.setCanBeResolved(true);
			c.setVisible(false);
			c.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
			c.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
			c.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LibraryElements.JAR));
			c.extendsFrom(generator.get());
		});
		NamedDomainObjectProvider<Configuration> modelpath = configurations.register(MODELPATH_CONFIGURATION_NAME, c -> {
			c.setCanBeConsumed(false);
			c.setCanBeResolved(false);
			c.setVisible(false);
		});
		NamedDomainObjectProvider<Configuration> generateModelpath = configurations.register(GENERATE_MODELPATH_CONFIGURATION_NAME, c -> {
			c.setCanBeConsumed(false);
			c.setCanBeResolved(true);
			c.setVisible(false);
			c.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LIBRARY_ELEMENTS_MODEL_DIR));
			c.extendsFrom(modelpath.get());
		});
		
		NamedDomainObjectContainer<ModelSource> modelSet = objects.domainObjectContainer(ModelSource.class, name -> {
			SourceDirectorySet source = objects.sourceDirectorySet(name, name);
			
			String capName = capitalize(name);
			TaskProvider<GenerateTask> generate = tasks.register(GENERATE_TASK_NAME + capName, GenerateTask.class, t -> {
				t.getClasspath().from(generatorClasspath);
				t.setSource(source);
				t.getGenDir().set(layout.getBuildDirectory().dir("src-gen/" + name));
				t.getModelpath().from(source.getSourceDirectories(), generateModelpath);
				t.getOptions().put(GenerateTask.OPTION_CLEAN, true);
				t.getOptions().put(GenerateTask.OPTION_LOGLEVEL, "warning");
			});
			
			return objects.newInstance(ModelSource.class, name, source, generate);
		});
		project.getExtensions().add(MODEL_EXTENSION_NAME, modelSet);
		
		FileCollection allSrcDirs = project.files(project.provider(
			() -> modelSet.stream().map(modelSource -> modelSource.getSource().getSourceDirectories()).collect(Collectors.toList())));
		
		TaskProvider<Zip> zipModel = tasks.register(ZIP_MODEL_TASK_NAME, Zip.class, t -> {
			t.from(allSrcDirs);
			t.getDestinationDirectory().set(layout.getBuildDirectory().dir("libs"));
			t.getArchiveClassifier().set("model");
			t.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
		});
		tasks.register(ECLIPSE_MODELPATH_TASK_NAME, EclipseModelpathTask.class, t -> {
			// Create a new provider from the plain values to erase any unwanted task dependencies.
			t.getSrcDirs().value(project.provider(() -> getEclipseModelpathSrcDirs(layout.getProjectDirectory(), allSrcDirs)));
			t.getProjects().value(project.provider(() -> getEclipseModelpathProjects(modelpath.get())));
			t.getModelpathFile().set(layout.getProjectDirectory().file("modelpath"));
		});
		tasks.register(GENERATE_TASK_NAME, t -> {
			t.setDescription("Executes the generate task for each model source set");
			t.dependsOn(project.provider(() -> modelSet.stream().map(ms -> ms.getGenerateTask()).collect(Collectors.toList())));
		});
		
		configurations.register(MODELPATH_DIR_CONFIGURATION_NAME, c -> {
			c.setCanBeConsumed(true);
			c.setCanBeResolved(false);
			c.setVisible(false);
			c.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LIBRARY_ELEMENTS_MODEL_DIR));
			c.extendsFrom(modelpath.get());
			c.getDependencies().add(dependencies.create(allSrcDirs));
		});
		NamedDomainObjectProvider<Configuration> modelpathZip = configurations.register(MODELPATH_ZIP_CONFIGURATION_NAME, c -> {
			c.setCanBeConsumed(true);
			c.setCanBeResolved(false);
			// workaround to prevent auto attachment of model zipping task to archives configuration, see https://github.com/protossoftware/etrice-gradle-plugin/issues/4
			c.setVisible(false);
			c.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LIBRARY_ELEMENTS_MODEL_ZIP));
			c.extendsFrom(modelpath.get());
			c.getOutgoing().artifact(zipModel);
		});
		
		project.getComponents().named(AdhocComponentPlugin.ADHOC_COMPONENT_NAME, AdhocComponentWithVariants.class,
			c -> c.addVariantsFromConfiguration(modelpathZip.get(), unused -> {}));
	}
	
	/**
	 * Resolves the source directories relative to the project directory.
	 * 
	 * @param projectDir the project directory
	 * @param allSrcDirs the source directories
	 * @return a list of source directories relative to the project directory
	 */
	private Iterable<String> getEclipseModelpathSrcDirs(Directory projectDir, FileCollection allSrcDirs) {
		Path projectPath = projectDir.getAsFile().toPath();
		return allSrcDirs.getFiles().stream()
			.map(File::toPath)
			.filter(path -> path.startsWith(projectPath))
			.map(path -> toPathStringWithForwardSlashes(projectPath.relativize(path)))
			.collect(Collectors.toList());
	}
	
	/**
	 * Converts a path to a string that separates path segments by forward slashes.
	 * 
	 * @param path the path to convert
	 * @return the path string with forward slashes
	 */
	private static String toPathStringWithForwardSlashes(Path path) {
		return StreamSupport.stream(path.spliterator(), false).map(Path::toString).collect(Collectors.joining("/"));
	}
	
	/**
	 * Extracts the module names of the direct dependencies of a configuration.
	 * 
	 * @param modelpath the configuration to extract module names from 
	 * @return all module names listed as direct dependencies in the specified configuration
	 */
	private Iterable<String> getEclipseModelpathProjects(Configuration modelpath) {
		return modelpath.getAllDependencies().stream()
			.filter(ModuleDependency.class::isInstance)
			.map(Dependency::getName)
			.collect(Collectors.toList());
	}
	
	/**
	 * Compatibility rule for model elements.
	 * Zips that contain model files and jars are allowed on the modelpath.
	 */
	private static class LibraryElementsCompatiblityRule implements AttributeCompatibilityRule<LibraryElements> {
		@Inject
		private LibraryElementsCompatiblityRule() {}
		
		@Override
		public void execute(CompatibilityCheckDetails<LibraryElements> details) {
			String consumerValue = details.getConsumerValue().getName();
			String producerValue = details.getProducerValue().getName();
			if(LIBRARY_ELEMENTS_MODEL_DIR.equals(consumerValue)) {
				if(LIBRARY_ELEMENTS_MODEL_ZIP.equals(producerValue) || LibraryElements.JAR.equals(producerValue))
					details.compatible();
			}
			else if(LIBRARY_ELEMENTS_MODEL_ZIP.equals(consumerValue)) {
				if(LibraryElements.JAR.equals(producerValue))
					details.compatible();
			}
		}
	}
	
	/**
	 * Disambiguation rule for model elements.
	 * Prefer model directories over model zips over jars. 
	 */
	private static class LibraryElementsDisambiguationRule implements AttributeDisambiguationRule<LibraryElements> {
		private LibraryElements modelDir;
		private LibraryElements modelZip;
		private LibraryElements jar;
		
		@Inject
		private LibraryElementsDisambiguationRule(LibraryElements modelDir, LibraryElements modelZip, LibraryElements jar) {
			this.modelDir = modelDir;
			this.modelZip = modelZip;
			this.jar = jar;
		}
		
		@Override
		public void execute(MultipleCandidatesDetails<LibraryElements> details) {
			LibraryElements consumerValue = details.getConsumerValue();
			Set<LibraryElements> candidateValues = details.getCandidateValues();
			if(modelDir.equals(consumerValue)) {
				if(candidateValues.contains(modelDir))
					details.closestMatch(modelDir);
				else if(candidateValues.contains(modelZip))
					details.closestMatch(modelZip);
				else if(candidateValues.contains(jar))
					details.closestMatch(jar);
			}
			else if(modelZip.equals(consumerValue)) {
				if(candidateValues.contains(modelZip))
					details.closestMatch(modelZip);
				else if(candidateValues.contains(jar))
					details.closestMatch(jar);
			}
		}
	}
	
	private static String capitalize(String str) {
		if(str.isEmpty())
			return str;
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

}
