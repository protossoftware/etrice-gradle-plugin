package de.protos.etrice.gradle;

import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;

/**
 * The parameters of the {@link GeneratorWorker}.
 */
public interface GeneratorParameters extends WorkParameters {
	Property<String> getModule();
	Property<String[]> getArgs();
}