package BioFID.OCR;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Streams;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.uima.UIMAException;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocumentFromFileHierachy extends DocumentHelper {
	
	public static void main(String[] args) {
		System.out.printf("Running DocumentFromFileHierachy with options: %s\n", Arrays.toString(args));
		String sFileRootPath = args[0];
		String sOutputPath = args[1];
		String sVocabularyPath = args[2];
		boolean bWriteRawText = args.length > 3 && (BooleanUtils.toBoolean(args[3]) || BooleanUtils.toBoolean(args[3], "1", "0"));
		
		Stream<File> fileStream = Streams.stream(com.google.common.io.Files.fileTraverser().depthFirstPreOrder(new File(sFileRootPath)));
		
		Map<File, Integer> dirDepthMap = fileStream.filter(File::isDirectory).collect(Collectors.toMap(Function.identity(), file -> file.toPath().relativize(Paths.get(sFileRootPath)).getNameCount() - 1));
		
		System.out.println(dirDepthMap);
		
		// Tiefe: Band = 1, Heft? = 2, Artikel = 3
		ImmutableList<File> documentParentDirs = ImmutableList.copyOf(dirDepthMap.entrySet().stream().filter(e -> e.getKey().isDirectory()).filter(e -> e.getValue() == 2).map(Map.Entry::getKey).collect(Collectors.toList()));
		
		System.out.println(documentParentDirs);
		
		final int[] count = {0};
		System.out.println("Starting document parsing..");
		
		documentParentDirs.parallelStream().forEach(documentParentDir -> { // FIXME: parallel
			File[] files = documentParentDir.listFiles();
			if (Objects.isNull(files) || files.length == 0) return;
			
			String documentId = documentParentDir.getName();
			ImmutableSortedSet<File> documentParts = ImmutableSortedSet.copyOf(files);
			
			System.out.printf("%d/%d Parsing document with id %s..\n", ++count[0], documentParentDirs.size(), documentId);
			
			try {
				ArrayList<String> pathList = new ArrayList<>();
				for (File documentPart : documentParts) {
					if (documentPart.isFile()) pathList.add(documentPart.getAbsolutePath());
				}
				
				processDocumentPathList(sOutputPath, sVocabularyPath, bWriteRawText, documentId, pathList);
			} catch (UIMAException e) {
				System.err.printf(
						"Caught UIMAException while parsing document %s!\n" +
								"%s\n" +
								"\t%s\n" +
								"Caused by: %s\n" +
								"\t%s\n",
						documentId,
						e.toString(),
						e.getStackTrace()[0].toString(),
						e.getCause().toString(),
						e.getCause().getStackTrace()[0].toString()
				);
			}
		});
		System.out.println("\nFinished parsing.");
	}
	
}
