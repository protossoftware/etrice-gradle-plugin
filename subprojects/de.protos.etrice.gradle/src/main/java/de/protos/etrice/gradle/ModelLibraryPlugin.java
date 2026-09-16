package de.protos.etrice.gradle;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.TaskContainer;

/**
 * Sets up configurations and tasks to download and extract model archives.
 */
public class ModelLibraryPlugin implements Plugin<Project> {

	public static final String MODEL_LIBRARY_CONFIGURATION_NAME = "modelLibrary";
	public static final String UNZIP_MODEL_SOURCE_CONFIGURATION_NAME = "unzipModelSource";
	
	public static final String UNZIP_MODEL_TASK_NAME = "unzipModel";
	
	@Override
	public void apply(Project project) {
		PluginContainer plugins = project.getPlugins();
		ConfigurationContainer configurations = project.getConfigurations();
		TaskContainer tasks = project.getTasks();
		ProjectLayout layout = project.getLayout();
		ObjectFactory objects = project.getObjects();
		
		plugins.apply(ETriceBasePlugin.class);
		
		NamedDomainObjectProvider<Configuration> modelLibrary = configurations.register(MODEL_LIBRARY_CONFIGURATION_NAME, c -> {
			c.setCanBeConsumed(false);
			c.setCanBeResolved(false);
			c.setTransitive(false);
		});
		
		NamedDomainObjectProvider<Configuration> unzipModelSource = configurations.register(UNZIP_MODEL_SOURCE_CONFIGURATION_NAME, c -> {
			c.setCanBeConsumed(false);
			c.setCanBeResolved(true);
			c.setTransitive(false);
			c.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, ETriceBasePlugin.LIBRARY_ELEMENTS_MODEL_ZIP));
			c.extendsFrom(modelLibrary.get());
		});
		
		tasks.register(UNZIP_MODEL_TASK_NAME, UnzipTask.class, t -> {
			t.source(unzipModelSource);
			t.getDestination().set(layout.getBuildDirectory().dir("modellib"));
		});
	}
	
}
