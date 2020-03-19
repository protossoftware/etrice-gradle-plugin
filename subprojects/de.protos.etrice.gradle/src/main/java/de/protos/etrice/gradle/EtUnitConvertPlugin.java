package de.protos.etrice.gradle;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;

/**
 * Sets up an extension to configure etunit convert tasks.
 */
public class EtUnitConvertPlugin implements Plugin<Project> {

	public static final String ETUNIT_CONVERTER_EXTENSION_NAME = "etunitConvert";
	
	public static final String ETUNIT_CONVERTER_CONFIGURATION_NAME = "etunitConverter";
	public static final String ETUNIT_CONVERTER_CLASSPATH_CONFIGURATION_NAME = "etunitConvertClasspath";
	
	private static final String ETUNIT_CONVERTER_DEFAULT_DEPENDENCY = "org.eclipse.etrice:org.eclipse.etrice.etunit.converter:3.0.2";
	
	@Override
	public void apply(Project project) {
		ObjectFactory objects = project.getObjects();
		ConfigurationContainer configurations = project.getConfigurations();
		DependencyHandler dependencies = project.getDependencies();
		TaskContainer tasks = project.getTasks();
		ExtensionContainer extensions = project.getExtensions();
		
		NamedDomainObjectProvider<Configuration> etunit = configurations.register(ETUNIT_CONVERTER_CONFIGURATION_NAME, c -> {
			c.setCanBeConsumed(false);
			c.setCanBeResolved(false);
			c.defaultDependencies(ds ->	ds.add(dependencies.create(ETUNIT_CONVERTER_DEFAULT_DEPENDENCY)));
		});
		
		NamedDomainObjectProvider<Configuration> etunitClasspath = configurations.register(ETUNIT_CONVERTER_CLASSPATH_CONFIGURATION_NAME, c -> {
			c.setCanBeConsumed(false);
			c.setCanBeResolved(true);
			c.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
			c.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.LIBRARY));
			c.getAttributes().attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.class, LibraryElements.JAR));
			c.extendsFrom(etunit.get());
		});
		
		NamedDomainObjectContainer<EtUnitConvertTask> etunitConverter = objects.domainObjectContainer(EtUnitConvertTask.class, name -> {
			return tasks.create(name, EtUnitConvertTask.class, t -> {
				t.getClasspath().from(etunitClasspath);
				t.include("**/*.etu");
			});
		});
		
		extensions.add(ETUNIT_CONVERTER_EXTENSION_NAME, etunitConverter);
	}

}
