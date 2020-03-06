package de.protos.etrice.gradle;

import java.io.File;
import java.util.Collection;

/**
 * Delegates to a static caching generator provider instance.
 * Enables reusing generator instances across multiple Gradle builds.
 * The implementation is threadsafe.
 */
public class GlobalGeneratorProvider {
	
	private static CachingGeneratorProvider provider = new CachingGeneratorProvider();
	
	/**
	 * @see CachingGeneratorProvider#getGenerator(Collection, String)
	 * 
	 * @param classpath the classpath of the generator
	 * @param module the symbolic name of the generator
	 * @return a generator instance
	 */
	public static Generator getGenerator(Collection<File> classpath, String module) {
		synchronized(provider) {
			return provider.getGenerator(classpath, module);
		}
	}
	
	/**
	 * @see CachingGeneratorProvider#clearCache()
	 */
	public static void clearCache() {
		synchronized(provider) {
			provider.clearCache();
		}
	}
	
}
