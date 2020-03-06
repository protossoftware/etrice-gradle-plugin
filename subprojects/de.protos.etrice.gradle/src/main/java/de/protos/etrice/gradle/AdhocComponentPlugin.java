package de.protos.etrice.gradle;

import javax.inject.Inject;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;

/**
 * Defines an adhoc component with variants for the project.
 */
public class AdhocComponentPlugin implements Plugin<Project> {

	public static final String ADHOC_COMPONENT_NAME = "adhoc";
	
	private final SoftwareComponentFactory softwareComponentFactory;
	
	@Inject
	public AdhocComponentPlugin(SoftwareComponentFactory softwareComponentFactory) {
		this.softwareComponentFactory = softwareComponentFactory;
	}
	
	@Override
	public void apply(Project project) {
		AdhocComponentWithVariants adhocComponent = softwareComponentFactory.adhoc(ADHOC_COMPONENT_NAME);
		project.getComponents().add(adhocComponent);
	}

}
