package de.protos.etrice.gradle;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

/**
 * Converts etunit reports to xml test reports. 
 */
public class EtUnitConvertTask extends SourceTask {
	
	private static final String ETUNIT_CONVERTER_MAIN = "org.eclipse.etrice.etunit.converter.EtUnitReportConverter";
	
	private ExecOperations execOperations;
	
	private ConfigurableFileCollection classpath;
	private ListProperty<String> options;
	
	@Inject
	public EtUnitConvertTask(ExecOperations execOperations) {
		this.execOperations = execOperations;
		
		ObjectFactory objects = getProject().getObjects();
		classpath = objects.fileCollection();
		options = objects.listProperty(String.class);
	}
	
	/**
	 * @return the classpath that contains the etunit converter
	 */
	@InputFiles
	public ConfigurableFileCollection getClasspath() {
		return classpath;
	}
	
	/**
	 * @return All converter options
	 */
	@Input
	public ListProperty<String> getOptions() {
		return options;
	}
	
	/**
	 * @return A list of files representing the converted test reports
	 */
	@OutputFiles
	public Set<File> getXmlFiles() {
		return getSource().getFiles().stream()
			.map(file -> replaceFileExtension(file.toPath(), "xml").toFile())
			.collect(Collectors.toSet());
	}
	
	/**
	 * Converts the specified files using the eTrice EtUnitReportConverter class.
	 */
	@TaskAction
	protected void convert() {
		execOperations.javaexec(spec -> {
			spec.classpath(classpath);
			spec.args(options.get());
			spec.args(getSource());
			spec.setMain(ETUNIT_CONVERTER_MAIN);
			spec.setIgnoreExitValue(false);
		});
	}
	
	/**
	 * Replaces the file extension with a specified new extension.
	 * 
	 * @param path the path to replace the extension of
	 * @param extension the name of the new extension
	 * @return a new path with the replaced file extension
	 */
	private static Path replaceFileExtension(Path path, String extension) {
		Path fileName = path.getFileName();
		if(fileName == null)
			return path;
		String name = fileName.toString();
		int index = name.lastIndexOf('.');
		if(index != -1)
			return path.resolveSibling(name.substring(0, index + 1) + extension);
		else
			return path.resolveSibling(name + '.' + extension);
	}
}
