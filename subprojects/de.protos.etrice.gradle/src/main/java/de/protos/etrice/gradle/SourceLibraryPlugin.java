package de.protos.etrice.gradle;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.TaskContainer;

/**
 * Sets up tasks and configurations to download and extract source archives.
 */
public class SourceLibraryPlugin implements Plugin<Project> {
	
	public static final String SOURCE_LIBRARY_CONFIGURATION_NAME = "sourceLibrary";
	public static final String UNZIP_SOURCE_CONFIGURATION_NAME = "unzipSource";
	
	public static final String UNZIP_SOURCE_TASK_NAME = "unzipSource";
	
	public static final String LIBRARY_ELEMENTS_SOURCE_ZIP = "source-zip";
	
	@Override
	public void apply(Project project) {
		final ConfigurationContainer configurations = project.getConfigurations();
		final TaskContainer tasks = project.getTasks();
		final ProjectLayout layout = project.getLayout();
		final ObjectFactory objects = project.getObjects();
		
		NamedDomainObjectProvider<Configuration> sourceLibrary = configurations.register(SOURCE_LIBRARY_CONFIGURATION_NAME, c -> {
			c.setCanBeConsumed(false);
			c.setCanBeResolved(false);
			c.setVisible(false);
			c.setTransitive(false);
		});
		
		NamedDomainObjectProvider<Configuration> unzipSource = configurations.register(UNZIP_SOURCE_CONFIGURATION_NAME, c -> {
			c.setCanBeConsumed(false);
			c.setCanBeResolved(true);
			c.setVisible(false);
			c.setTransitive(false);
			c.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LIBRARY_ELEMENTS_SOURCE_ZIP));
			c.extendsFrom(sourceLibrary.get());
		});
		
		tasks.register(UNZIP_SOURCE_TASK_NAME, UnzipTask.class, t -> {
			t.source(unzipSource);
			t.getDestination().set(layout.getBuildDirectory().dir("sourcelib"));
		});
	}
	
}
