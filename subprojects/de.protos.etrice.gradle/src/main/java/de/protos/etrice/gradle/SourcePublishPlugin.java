package de.protos.etrice.gradle;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;

/**
 * Sets up tasks and configurations to zip and upload source archives.
 */
public class SourcePublishPlugin implements Plugin<Project> {

	public static final String SOURCE_ZIP_CONFIGURATION_NAME = "sourceZip";
	
	public static final String ZIP_SOURCE_TASK_NAME = "zipSource";
	
	@Override
	public void apply(Project project) {
		final PluginContainer plugins = project.getPlugins();
		final ConfigurationContainer configurations = project.getConfigurations();
		final TaskContainer tasks = project.getTasks();
		final ProjectLayout layout = project.getLayout();
		final ObjectFactory objects = project.getObjects();
		
		plugins.apply(AdhocComponentPlugin.class);
		
		TaskProvider<Zip> zipSource = tasks.register(ZIP_SOURCE_TASK_NAME, Zip.class, t -> {
			t.getDestinationDirectory().set(layout.getBuildDirectory().dir("libs"));
			t.getArchiveClassifier().set("source");
			t.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
		});
		
		NamedDomainObjectProvider<Configuration> sourceZip = configurations.register(SOURCE_ZIP_CONFIGURATION_NAME, c -> {
			c.setCanBeConsumed(true);
			c.setCanBeResolved(false);
			c.setVisible(false);
			c.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, SourceLibraryPlugin.LIBRARY_ELEMENTS_SOURCE_ZIP));
			c.getOutgoing().artifact(zipSource);
		});
		
		project.getComponents().named(AdhocComponentPlugin.ADHOC_COMPONENT_NAME, AdhocComponentWithVariants.class,
			c -> c.addVariantsFromConfiguration(sourceZip.get(), unused -> {}));
	}

}
