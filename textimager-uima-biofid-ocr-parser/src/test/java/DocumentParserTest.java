import BioFID.OCR.DocumentParser;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.texttechnologylab.annotation.ocr.OCRBlock;
import org.texttechnologylab.annotation.ocr.OCRLine;
import org.texttechnologylab.annotation.ocr.OCRToken;
import org.texttechnologylab.annotation.ocr.OCRpage;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.util.JCasUtil.*;

@DisplayName("DocumentParser Test")
class DocumentParserTest {

	//	static final ImmutableList<String> documentIds = ImmutableList.of("9032259", "9031469", "9031472", "9031473", "9031474", "9031475", "9031476", "9031477", "9032261");
	static final ImmutableList<String> documentIds = ImmutableList.of("9699895", "9655813", "9655814", "9655815", "9655816", "9655817", "9655818", "9655819", "9655820", "9655821", "9655822", "9655823", "9655824", "9655825", "9655826", "9655827", "9655828", "9655829", "9655830", "9655831", "9655832", "9655833", "9655834", "9655835", "9655836", "9655837", "9655838", "9655839", "9655840", "9655841", "9655842", "9655843", "9655844");
	private static JCas jCas;

	@BeforeAll
	@DisplayName("Set Up")
	static void setUp() throws IOException, UIMAException {
		System.out.println("Getting JCas...");

		HashMap<String, String> fileAtlas = new HashMap<>();
		try (BufferedReader bufferedReader = Files.newReader(new File("/home/s3676959/Documents/Export/file_atlas.txt"), StandardCharsets.UTF_8)) {
			bufferedReader.lines().map(l -> l.split("\t")).forEach(arr -> fileAtlas.put(arr[0], arr[1]));
		}

		if (fileAtlas.isEmpty()) {
			throw new NullPointerException("Files could not be found!");
		}
		System.out.printf("Atlas size %d.\n", fileAtlas.size());

		ArrayList<String> pathList = new ArrayList<>();
		for (String documentId : documentIds) {
			String path = fileAtlas.getOrDefault(documentId, null);
			if (path != null && new File(path).isFile()) pathList.add(path);
		}

		System.out.println(pathList);

		AnalysisEngineDescription documentParser = createEngineDescription(DocumentParser.class,
				DocumentParser.INPUT_PATHS, pathList.toArray(new String[0]),
				DocumentParser.PARAM_MIN_TOKEN_CONFIDENCE, 90,
				DocumentParser.PARAM_DICT_PATH, "src/test/resources/Leipzig40MT2010_lowered.5.vocab");

		JCas inputCas = JCasFactory.createJCas();

		// Pipeline
		SimplePipeline.runPipeline(inputCas, documentParser);

		jCas = inputCas;

	}

	@Test
	@DisplayName("Test Serializing")
	void testSerializing() {
		try (FileOutputStream fileOutputStream = Files.newOutputStreamSupplier(new File("src/test/" + documentIds.get(0) + ".xmi")).getOutput()) {
			XmiCasSerializer.serialize(jCas.getCas(), fileOutputStream);
		} catch (SAXException | IOException e) {
			e.printStackTrace();
			assert false;
		}
	}

	@Nested
	@DisplayName("With parsed Document")
	class WithDocument {
		@Test
		@DisplayName("Test Pages")
		void testPages() {
			Map<OCRpage, Collection<OCRToken>> pageCovered = indexCovered(jCas, OCRpage.class, OCRToken.class);

			for (OCRpage ocrPage : select(jCas, OCRpage.class)) {
				System.out.printf("<OCRPage number:%d, id:%s, begin:%d, end:%d>\n", ocrPage.getPageNumber(), ocrPage.getPageId(), ocrPage.getBegin(), ocrPage.getEnd());
				for (OCRToken ocrToken : pageCovered.get(ocrPage)) {
					System.out.print(ocrToken.getCoveredText());
				}
				System.out.println("\n</OCRPage>");
			}
		}

		@Test
		@DisplayName("Test Blocks")
		void testBlocks() {
			Map<OCRBlock, Collection<OCRToken>> blockCovered = indexCovered(jCas, OCRBlock.class, OCRToken.class);

			for (OCRBlock ocrBlock : select(jCas, OCRBlock.class)) {
				System.out.printf("<OCRBlock valid:%b, begin:%d, end:%d>\n", ocrBlock.getValid(), ocrBlock.getBegin(), ocrBlock.getEnd());
				for (OCRToken ocrToken : blockCovered.get(ocrBlock)) {
					System.out.print(ocrToken.getCoveredText());
				}
				System.out.println("\n</OCRBlock>");
			}
		}

		@Test
		@DisplayName("Test Covered")
		void testCovered() {
			Map<OCRToken, Collection<OCRToken>> tokenCovering = indexCovering(jCas, OCRToken.class, OCRToken.class);

			for (OCRToken OCRToken : select(jCas, OCRToken.class)) {
				if (tokenCovering.keySet().contains(OCRToken)) {
					System.out.print("<covered>");
				}
				System.out.print(OCRToken.getCoveredText());
				if (tokenCovering.keySet().contains(OCRToken)) {
					System.out.print("</covered>");
				}
				System.out.println();
			}
		}

		@Test
		@DisplayName("Test Lines")
		void testLines() {
			Map<OCRLine, Collection<OCRToken>> linesCovered = indexCovered(jCas, OCRLine.class, OCRToken.class);

			for (OCRLine ocrLine : select(jCas, OCRLine.class)) {
				for (OCRToken ocrToken : linesCovered.get(ocrLine)) {
					System.out.print(ocrToken.getCoveredText());
				}
				System.out.println();
			}
		}
	}
}
