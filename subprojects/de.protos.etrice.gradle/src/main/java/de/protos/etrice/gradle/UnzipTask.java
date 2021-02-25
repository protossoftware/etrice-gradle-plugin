package de.protos.etrice.gradle;

import javax.inject.Inject;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

/**
 * Extracts files from archives.
 */
public class UnzipTask extends SourceTask {
	
	private final FileOperations fileOperations;

	private final DirectoryProperty destination;
	
	@Inject
	public UnzipTask(FileOperations fileOperations, ObjectFactory objects) {
		this.fileOperations = fileOperations;

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
	protected void sync() {
		fileOperations.sync(copySpec -> {
			getSource().forEach(file -> copySpec.from(fileOperations.zipTree(file)));
			copySpec.into(destination);
		});
	}

}
