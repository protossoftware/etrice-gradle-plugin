package de.protos.etrice.gradle;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * This class provides generator instances for a given classpath and generator name.
 * Once a generator is created, it is cached for reuse.
 * If the cache size is exceeded, the least used classpath is discarded.
 */
public class CachingGeneratorProvider {
	
	public static final int DEFAULT_CACHE_SIZE = 8;
	
	private int cacheSize;
	private ClassLoader parent;
	private LinkedList<GeneratorClasspath> classpaths;
	
	/**
	 * Creates a new instance with a default cache size of {@value #DEFAULT_CACHE_SIZE} and the system class loader as parent.
	 */
	public CachingGeneratorProvider() {
		this(DEFAULT_CACHE_SIZE, ClassLoader.getSystemClassLoader());
	}
	
	/**
	 * Creates a new generator provider.
	 * 
	 * @param cacheSize the size of the cache for class loaders.
	 * @param parent The parent to be used for new class loader instances
	 */
	public CachingGeneratorProvider(int cacheSize, ClassLoader parent) {
		if(cacheSize < 0) {
			throw new IllegalArgumentException("negative cache size");
		}
		
		this.cacheSize = cacheSize;
		this.parent = parent;
		
		classpaths = new LinkedList<>();
	}
	
	/**
	 * Provides a generator using the specified classpath.
	 * Tries to reuse already created generators.
	 * 
	 * @param classpath the classpath that contains the generator classes
	 * @param module the symbolic name of the generator
	 * @return a generator instance
	 */
	public Generator getGenerator(Collection<File> classpath, String module) {
		GeneratorClasspath generatorClasspath = getClasspath(classpath);
		Generator generator = generatorClasspath.getGenerator(module);
		return generator;
	}
	
	/**
	 * Clears all cached generators.
	 */
	public void clearCache() {
		classpaths.forEach(cp -> cp.close());
		classpaths.clear();
	}
	
	/**
	 * Returns a generator classpath that contains all requested files.
	 * Reinserts the generator classpath to move it to the front of the cache.
	 * 
	 * @param files the files on the classpath
	 * @return the generator classpath instance
	 */
	private GeneratorClasspath getClasspath(Collection<File> files) {
		// Search for a classpath that already contains all requested files.
		Iterator<GeneratorClasspath> iterator = classpaths.iterator();
		while(iterator.hasNext()) {
			GeneratorClasspath cp = iterator.next();
			if(cp.getFiles().containsAll(files)) {
				iterator.remove();
				classpaths.addFirst(cp);
				return cp;
			}
		}
		
		// Create new classpath that contains all files.
		GeneratorClasspath cp = new GeneratorClasspath(files, parent);
		classpaths.addFirst(cp);
		if(classpaths.size() > cacheSize) {
			classpaths.removeLast();
		}
		
		return cp;
	}
	
	/**
	 * Holds a class loader for a given generator classpath.
	 * Creates and caches generator instances using this class loader.
	 */
	private static class GeneratorClasspath implements AutoCloseable {
		
		private Set<File> files;
		private URLClassLoader classLoader;
		private HashMap<String, Generator> generators;
		
		/**
		 * Creates a new class loader using the specified files.
		 * 
		 * @param files the files on the classpath of the class loader
		 * @param parent the parent for the class loader
		 */
		public GeneratorClasspath(Collection<File> files, ClassLoader parent) {
			this.files = Collections.unmodifiableSet(new HashSet<>(files));
			this.classLoader = createClassLoader(files, parent);
			this.generators = new HashMap<>();
		}
		
		/**
		 * Provides a generator instance.
		 * 
		 * @param module the symbolic name of the generator
		 * @return the generator instance
		 */
		public Generator getGenerator(String module) {
			Generator generator = generators.get(module);
			if(generator == null) {
				generator = new Generator(classLoader, module);
				generators.put(module, generator);
			}
			return generator;
		}
		
		/**
		 * @return a set of all files on this classpath
		 */
		public Set<File> getFiles() {
			return files;
		}
		
		@Override
		public void close() {
			try {
				classLoader.close();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		
		/**
		 * Creates a new url class loader instance using the specified classpath.
		 * 
		 * @param files the files on the classpath of the new class loader
		 * @param parent the parent for the new class loader
		 * @return the new class loader
		 */
		private URLClassLoader createClassLoader(Iterable<File> files, ClassLoader parent) {
			URL[] urls = StreamSupport.stream(files.spliterator(), false).map(f -> {
				try {
					return f.toURI().toURL();
				}
				catch (MalformedURLException e) {
					throw new RuntimeException(e);
				}
			}).toArray(size -> new URL[size]);
			
			return new URLClassLoader(urls, parent);
		}
	}
	
}
