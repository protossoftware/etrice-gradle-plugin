package de.protos.etrice.gradle;

import org.gradle.api.artifacts.Configuration;
import org.gradle.util.GradleVersion;

/**
 * Compatibility helpers for supporting a range of Gradle versions.
 */
final class GradleCompat {

	private static final GradleVersion VISIBILITY_REMOVAL_VERSION = GradleVersion.version("9.0");

	private GradleCompat() {}

	/**
	 * Marks a configuration as invisible to prevent auto attachment of its artifacts to the
	 * archives configuration, see https://github.com/protossoftware/etrice-gradle-plugin/issues/4.
	 * The visibility property is deprecated and without effect since Gradle 9, where artifacts
	 * of configurations are not attached to archives anymore.
	 *
	 * @param configuration the configuration to mark as invisible
	 */
	static void setInvisible(Configuration configuration) {
		if(GradleVersion.current().compareTo(VISIBILITY_REMOVAL_VERSION) < 0) {
			hide(configuration);
		}
	}

	@SuppressWarnings("deprecation")
	private static void hide(Configuration configuration) {
		configuration.setVisible(false);
	}
}
