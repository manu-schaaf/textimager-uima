package BioFID.OCR;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.uima.UIMAException;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocumentFromFileHierarchy extends DocumentHelper {
	
	private static String sFileRootPath;
	private static String sOutputPath;
	private static String sVocabularyPath;
	private static String sRawOutput;
	private static int bias = 1;
	
	public static void main(String[] args) {
		try {
			getParams(args);
			
			int index;
			index = Integer.max(params.indexOf("-i"), params.indexOf("--input"));
			if (index > -1) {
				sFileRootPath = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing --input!\n");
			}
			
			index = Integer.max(params.indexOf("-o"), params.indexOf("--output"));
			if (index > -1) {
				sOutputPath = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing --output!\n");
			}
			
			index = Integer.max(params.indexOf("-v"), params.indexOf("--vocab"));
			if (index > -1) {
				sVocabularyPath = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing --vocab!\n");
			}
			
			index = Integer.max(params.indexOf("--raw"), params.indexOf("-r"));
			if (index > -1) {
				sRawOutput = params.get(index + 1);
			}
			
			index = params.indexOf("--bias");
			if (index > -1) {
				bias = Integer.parseInt(params.get(index + 1));
			}
			
			System.out.printf("Running DocumentFromFileHierarchy with options: %s\n", Arrays.toString(args));
			
			Stream<File> fileStream = Streams.stream(Files.fileTraverser().depthFirstPreOrder(new File(sFileRootPath)));
			
			Map<File, Integer> dirDepthMap = fileStream.filter(File::isDirectory).collect(Collectors.toMap(Function.identity(), file -> file.toPath().relativize(Paths.get(sFileRootPath)).getNameCount() - bias));
			
			// Tiefe: Band = 1, Heft? = 2, Artikel = 3
			ImmutableList<File> documentParentDirs = ImmutableList.copyOf(dirDepthMap.entrySet().stream().filter(e -> e.getKey().isDirectory()).filter(e -> e.getValue() == 2).map(Map.Entry::getKey).collect(Collectors.toList()));
			
			final int[] count = {0};
			System.out.printf("Starting parsing %d documents..\n", documentParentDirs.size());
			
			ForkJoinPool forkJoinPool = new ForkJoinPool(64);
			
			forkJoinPool.submit(() -> documentParentDirs.parallelStream().forEach(documentParentDir -> {
				File[] files = documentParentDir.listFiles();
				if (Objects.isNull(files) || files.length == 0) return;
				
				String documentId = documentParentDir.getName();
				ImmutableSortedSet<File> documentParts = ImmutableSortedSet.copyOf(files);
				
				System.out.printf("\r%d/%d Parsing document with id %s..", ++count[0], documentParentDirs.size(), documentId);
				
				try {
					ArrayList<String> pathList = new ArrayList<>();
					for (File documentPart : documentParts) {
						if (documentPart.isFile()) pathList.add(documentPart.getAbsolutePath());
					}
					
					processDocumentPathList(sOutputPath, sVocabularyPath, sRawOutput, documentId, pathList);
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
			})).get();
			
			System.out.println("\nFinished parsing.");
		} catch (MissingArgumentException e) {
			System.err.println(e.getMessage());
			printUsage();
		}
	}
	
	
	private static void printUsage() {
		System.out.println("Process XML Abby FineReader exports from entire collections by traversing a file hierarchy. The root path should be the top folder of a collection (Band).\n" +
				"Usage: java -cp $classpaths DocumentFromFileHierarchy [args]\n" +
				"Arguments:\n" +
				"\t--input, -i\tInput root path.\n" +
				"\t--output, -o\tXMI Output path.\n" +
				"\t--vocab, -v\tVocabulary path.\n" +
				"\t--raw, -v\tOptional, raw output path.");
	}
}
