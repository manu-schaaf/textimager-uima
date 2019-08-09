package BIOfid.Engine;

import com.google.common.collect.Streams;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.CasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.internal.ExtendedLogger;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasIOUtils;
import org.apache.uima.util.Progress;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;

/**
 *
 */
public class XmiCollectionReader extends CasCollectionReader_ImplBase {
	ExtendedLogger logger = getLogger();
	
	public static final String PARAM_TARGET_LOCATION = "sourceLocation";
	public static final String PARAM_SOURCE_LOCATION = "sourceLocation";
	public static final String PARAM_PATH = "sourceLocation";
	@ConfigurationParameter(
			name = "sourceLocation",
			description = "Root path for files to process"
	)
	private static String sourceLocation;
	
	public static final String PARAM_NOT_RECURSIVE = "pNotRecursive";
	@ConfigurationParameter(
			name = "pNotRecursive",
			description = "Only process files in root folder.",
			defaultValue = "false"
	)
	private static boolean pNotRecursive;
	
	private static final ArrayDeque<Path> currentResources = new ArrayDeque<>();
	
	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		File rootDir = new File(sourceLocation);
		if (!rootDir.exists()) {
			throw new ResourceInitializationException(new FileNotFoundException("Directory " + sourceLocation + "could not be found!"));
		}
		if (!pNotRecursive) {
			Streams.stream(Files.fileTraverser().breadthFirst(rootDir))
					.filter(File::isFile)
					.filter(f -> f.getName().endsWith(".xmi"))
					.map(File::toPath)
					.forEach(currentResources::add);
		} else {
			Arrays.stream(Objects.requireNonNull(rootDir.listFiles()))
					.filter(File::isFile)
					.filter(f -> f.getName().endsWith(".xmi"))
					.map(File::toPath)
					.forEach(currentResources::add);
		}
	}
	
	@Override
	public void getNext(CAS aCAS) throws IOException, CollectionException {
		Path path = currentResources.pop();
		try (FileInputStream inputStream = FileUtils.openInputStream(path.toFile())) {
			CasIOUtils.load(inputStream, null, aCAS, true);
		} catch (Exception e) {
			logger.error("Error while opening file: " + path.toString());
			e.printStackTrace();
		}
	}
	
	@Override
	public void destroy() {
		super.destroy();
	}
	
	@Override
	public boolean hasNext() throws IOException, CollectionException {
		return !currentResources.isEmpty();
	}
	
	@Override
	public Progress[] getProgress() {
		return new Progress[0];
	}
}
