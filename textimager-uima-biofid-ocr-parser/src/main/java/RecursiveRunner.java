import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.chunk.Chunk;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.util.JCasUtil.select;
import static org.apache.uima.fit.util.JCasUtil.selectCovered;

public class RecursiveRunner {
	
	public static void main(String[] args) {
		String basePath = "~/Documents/Biodiversität_OCR_Lieferung_1/9031458/";
		String outPath = "~/Documents/out/9031458/";

//		boolean keepFolderStructure = true;
		
		try {
			ImmutableList<File> files = Files.fileTreeTraverser().postOrderTraversal(new File(basePath)).toList();
			System.out.printf("Traversing %d elements..\n\n\n", files.size());
			System.out.flush();
			for (File file : files) {
				if (file.isDirectory())
					continue;
				Path relativePath = Paths.get(file.getAbsolutePath().substring(basePath.length()));
				Path finalPath = Paths.get(outPath, relativePath.toString().replaceAll("\\.xml", ".txt"));
				finalPath.getParent().toFile().mkdirs();
				
				String content = exampleOutput(file);
				writeToFile(finalPath.toFile(), content);
			}
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	private static String exampleOutput(File file) throws UIMAException {
		// Input
		String xml = "";
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			xml = br.lines().collect(Collectors.joining("\n"));
			
			// Create a new Engine Description.
			AnalysisEngineDescription pageParser = createEngineDescription(BioFIDOCRPageParser.class,
					BioFIDOCRPageParser.INPUT_XML, xml,
					BioFIDOCRPageParser.PARAM_MIN_TOKEN_CONFIDENCE, 90,
					BioFIDOCRPageParser.PARAM_BLOCK_TOP_MIN, 400,
					BioFIDOCRPageParser.PARAM_DICT_PATH, "~/Documents/BioFID/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/resources/Leipzig40MT2010_lowered.5.vocab");
			
			// Create a new JCas - "Holder"-Class for Annotation.
			JCas inputCas = JCasFactory.createJCas();
			
			// Pipeline
			SimplePipeline.runPipeline(inputCas, pageParser);
			
			StringBuilder finalText = new StringBuilder();
			
			final int[] tokenCount = {0};
			for (Chunk block : select(inputCas, Chunk.class)) {
				if (block.getChunkValue().equals("true")) {
					selectCovered(inputCas, Token.class, block).stream().map(Token::getText).forEachOrdered(str ->
					{
						finalText.append(str);
						if (!str.equals(" ")) tokenCount[0]++;
					});
				}
			}
			System.out.printf("File '%s' length: %d, token count:%d\n", file.getName().replaceAll("\\.xml", ".txt"), xml.length(), tokenCount[0]);
			System.out.flush();
			return finalText.toString();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}
	
	static private void writeToFile(File targetFile, String content) {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(targetFile))) {
			bw.write(content);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
