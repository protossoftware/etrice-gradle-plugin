package de.protos.etrice.gradle;

import java.util.Collections;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.PluginContainer;

/**
 * Sets up a standard eTrice Java project.
 */
public class ETriceJavaPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		final PluginContainer plugins = project.getPlugins();
		final ExtensionContainer extensions = project.getExtensions();
		final ProjectLayout layout = project.getLayout();
		
		plugins.apply(ETriceBasePlugin.class);
		
		extensions.<NamedDomainObjectContainer<ModelSource>>configure(ETriceBasePlugin.MODEL_EXTENSION_NAME, modelSet ->
			modelSet.create("room", modelSource -> {
				modelSource.getSource().setSrcDirs(Collections.singletonList(layout.getProjectDirectory().dir("model")));
				modelSource.getSource().include("**/*.room", "**/*.etmap", "**/*.etphys");
				modelSource.getGenerateTask().configure(t -> {
					t.getModule().set("etrice-java");
				});
			})
		);
	}

}
