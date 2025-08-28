package de.protos.etrice.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.CacheableTask;

/**
 * Generates an eclipse modelpath file.
 */
@CacheableTask // TODO: test if caching is worth here (writeModelpath is very simple, but the task can be very repetitive in some situations)
public class EclipseModelpathTask extends DefaultTask {
	
	private final SetProperty<String> srcDirs;
	private final SetProperty<String> projects;
	private final RegularFileProperty modelpathFile;
	
	@Inject
	public EclipseModelpathTask(ObjectFactory objects) {
		srcDirs = objects.setProperty(String.class);
		projects = objects.setProperty(String.class);
		modelpathFile = objects.fileProperty();
	}
	
	/**
	 * @return the source directories for the eclipse modelpath
	 */
	@Input
	public SetProperty<String> getSrcDirs() {
		return srcDirs;
	}
	
	/**
	 * @return the project dependencies for the eclipse modelpath
	 */
	@Input
	public SetProperty<String> getProjects() {
		return projects;
	}
	
	/**
	 * @return the location for the eclipse modelpath file
	 */
	@OutputFile
	public RegularFileProperty getModelpathFile() {
		return modelpathFile;
	}
	
	@TaskAction
	protected void writeModelpath() {
		List<String> eclipseModelpath = Stream.concat(
				srcDirs.get().stream().map(srcDir -> "srcDir " + srcDir),
				projects.get().stream().map(project -> "project " + project)
			).collect(Collectors.toList());
		
		try {
			Files.write(modelpathFile.get().getAsFile().toPath(), eclipseModelpath);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
}
