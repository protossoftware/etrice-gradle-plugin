package de.protos.etrice.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

/**
 * Extracts files from archives.
 */
public class UnzipTask extends SourceTask {
	
	private final DirectoryProperty destination;
	
	public UnzipTask() {
		ObjectFactory objects = getProject().getObjects();
		destination = objects.directoryProperty();
	}
	
	/**
	 * @return the destination directory for extracted files
	 */
	@OutputDirectory
	public DirectoryProperty getDestination() {
		return destination;
	}
	
	@TaskAction
	public void sync() {
		getProject().sync(copySpec -> {
			getSource().forEach(file -> copySpec.from(getProject().zipTree(file)));
			copySpec.into(destination);
		});
	}

}
