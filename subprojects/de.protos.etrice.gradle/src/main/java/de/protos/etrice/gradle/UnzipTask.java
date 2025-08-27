package de.protos.etrice.gradle;

import javax.inject.Inject;

import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

/**
 * Extracts files from archives.
 */
public abstract class UnzipTask extends SourceTask {
	
	private final FileSystemOperations fileSystemOperations;
	private final ArchiveOperations archiveOperations;

	private final DirectoryProperty destination;
	
	@Inject
	public UnzipTask(FileSystemOperations fileSystemOperations, ArchiveOperations archiveOperations, ObjectFactory objects) {
		this.fileSystemOperations = fileSystemOperations;
		this.archiveOperations = archiveOperations;

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
		fileSystemOperations.sync(syncSpec -> {
			getSource().forEach(file -> syncSpec.from(archiveOperations.zipTree(file)));
			syncSpec.into(destination.get().getAsFile());
		});
	}

}
