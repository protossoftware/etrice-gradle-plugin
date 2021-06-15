package de.protos.etrice.gradle;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Provides functions to set up and build temporary Gradle projects. 
 */
public class GradleProjectBuilder {
	
	/** the base directory where new projects are created */
	private static final Path baseDir = Paths.get("build", "tmp").toAbsolutePath();
	
	/**
	 * Creates a new Gradle project in a temporary directory.
	 * 
	 * @param name the project name
	 * @param closure a closure to configure the project contents
	 * @return the path to the project directory
	 */
	public static GradleProjectBuilder build(String name, @DelegatesTo(value = GradleProjectBuilder.class, strategy = Closure.DELEGATE_FIRST) Closure<?> closure) throws IOException {
		Path projectDir = baseDir.resolve(name);
		delete(projectDir);
		GradleProjectBuilder builder = new GradleProjectBuilder(projectDir);
		builder.write("settings.gradle", "");
		closure.setDelegate(builder);
		closure.setResolveStrategy(Closure.DELEGATE_FIRST);
		closure.call();
		return builder;
	}
	
	/** the path to the project directory */
	public final Path projectDir;

	/**
	 * Creates a new project builder.
	 * 
	 * @param projectDir the path to the project directory
	 */
	private GradleProjectBuilder(Path projectDir) {
		this.projectDir = projectDir;
	}
	
	/**
	 * Tests whether a file or directory exists.
	 * 
	 * @param name the path relative to the project directory
	 * @return true if the file exists
	 */
	public boolean exists(String name) {
		return Files.exists(projectDir.resolve(name));
	}
	
	/**
	 * Deletes a file or directory.
	 * 
	 * @param name the path relative to the project directory
	 * @return true if the file was deleted
	 */
	public boolean delete(String name) throws IOException {
		Path path = projectDir.resolve(name);
		return delete(path);
	}
	
	/**
	 * Writes a file to the project directory.
	 * 
	 * @param name the file path relative to the project directory
	 * @param content the content that is written to the file
	 * @return the path to the file
	 */
	public Path write(String name, CharSequence content) throws IOException {
		Path path = projectDir.resolve(name);
		Files.createDirectories(path.getParent());
		Files.write(path, Collections.singletonList(content));
		return path;
	}
	
	/**
	 * Executes a Gradle build.
	 * 
	 * @param task the name of the task to execute
	 * @param closure a closure to evaluate the build result
	 * @return the result of the build execution
	 */
	public BuildResult gradle(String task, @DelegatesTo(value = BuildResult.class, strategy = Closure.DELEGATE_FIRST) Closure<?> closure) {
		BuildResult result = GradleRunner.create()
			.withPluginClasspath().withProjectDir(projectDir.toFile()).withArguments(task).build();
		closure.setDelegate(result);
		closure.setResolveStrategy(Closure.DELEGATE_FIRST);
		closure.call();
		return result;
	}
	
	/**
	 * Deletes a file or directory.
	 * 
	 * @param path the path of the file or directory to delete
	 */
	private static boolean delete(Path path) throws IOException {
		if(Files.exists(path)) {
			Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return super.visitFile(file, attrs);
				}
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Files.delete(dir);
					return super.postVisitDirectory(dir, exc);
				}
			});
			return true;
		}
		return false;
	}
}
