package de.protos.etrice.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Generates an eclipse modelpath file.
 */
public class EclipseModelpathTask extends DefaultTask {
	
	private final ConfigurableFileCollection srcDirs;
	private final ListProperty<String> projects;
	private final DirectoryProperty eclipseProjectDirectory;
	
	public EclipseModelpathTask() {
		ObjectFactory objects = getProject().getObjects();
		srcDirs = objects.fileCollection();
		projects = objects.listProperty(String.class);
		eclipseProjectDirectory = objects.directoryProperty();
	}
	
	/**
	 * @return the source directories for the eclipse modelpath
	 */
	// This should be @Input because we don't want to execute this task when a file inside the directories changes.
	// However @Input is deprecated for FileCollections?
	@InputFiles
	public ConfigurableFileCollection getSrcDirs() {
		return srcDirs;
	}
	
	/**
	 * @return the project dependencies for the eclipse modelpath
	 */
	@Input
	public ListProperty<String> getProjects() {
		return projects;
	}
	
	/**
	 * @return the eclipse project location
	 */
	@InputDirectory
	public DirectoryProperty getEclipseProjectDirectory() {
		return eclipseProjectDirectory;
	}
	
	/**
	 * @return the location of the eclipse modelpath file
	 */
	@OutputFile
	public Provider<RegularFile> getModelpathFile() {
		return eclipseProjectDirectory.file("modelpath");
	}
	
	@TaskAction
	protected void writeModelpath() {
		Path projectPath = eclipseProjectDirectory.get().getAsFile().toPath();
		
		List<String> eclipseModelpath = Stream.concat(
				srcDirs.getFiles().stream()
					.map(File::toPath)
					.filter(path -> path.startsWith(projectPath))
					.map(path -> projectPath.relativize(path))
					.map(srcDir -> "srcDir " + srcDir),
				projects.get().stream()
					.map(project -> "project " + project)
			).collect(Collectors.toList());
		
		try {
			Files.write(projectPath.resolve("modelpath"), eclipseModelpath);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
}
