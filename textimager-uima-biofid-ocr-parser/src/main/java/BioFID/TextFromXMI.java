package BioFID;

import BioFID.OCR.AbstractDocumentParser;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
import org.apache.uima.UIMAException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.jcas.JCas;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Stream;

import static BioFID.Util.writeToFile;

/**
 * Created on 14.02.2019.
 */
public class TextFromXMI extends AbstractDocumentParser {

	public static void main(String[] args) {
		String sInputPath = args[0];
		String sOutputPath = args[1];


		Stream<File> fileStream = Streams.stream(Files.fileTraverser().breadthFirst(Paths.get(sInputPath).toFile())).filter(File::isFile);
		final long size = fileStream.count();
		int[] count = {0};
//			for (File file : files) {
		ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
		try {
			forkJoinPool.submit(() ->
					fileStream.parallel().forEach(file -> {
						try {
							//				List<String> lines = Files.readLines(file, StandardCharsets.UTF_8);
							JCas jCas = JCasFactory.createJCas();
							CasIOUtil.readXmi(jCas, file);
							if (jCas.getDocumentText().isEmpty())
								return;

							String content = getValidText(jCas);

							String outFileName = file.getName().replaceAll("\\.xm[il]", ".txt");
							System.out.printf("\r%d/%d Writing %s (running threads: %d, pool size: %d)",
									count[0]++, size, outFileName, forkJoinPool.getRunningThreadCount(), forkJoinPool.getPoolSize());
							writeToFile(Paths.get(sOutputPath, outFileName), content);
						} catch (UIMAException | IOException e) {
							e.printStackTrace();
						}
					})).get();
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}

}
