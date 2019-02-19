package BioFID.OCR;

import BioFID.AbstractRunner;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.xml.sax.SAXException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import static BioFID.Util.getValidText;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;

abstract class DocumentHelper extends AbstractRunner {
	
	protected static void processDocumentPathList(String sOutputPath, String sVocabularyPath, String sRawPath, String documentId, ArrayList<String> pathList) throws UIMAException {
		AnalysisEngineDescription documentParser = createEngineDescription(DocumentParser.class,
				DocumentParser.INPUT_PATHS, pathList.toArray(new String[0]),
				DocumentParser.PARAM_MIN_TOKEN_CONFIDENCE, 75,
				DocumentParser.PARAM_BLOCK_TOP_MIN, 0,
				DocumentParser.PARAM_DICT_PATH, sVocabularyPath);
		
		JCas jCas = JCasFactory.createJCas();
		
		DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
		documentMetaData.setDocumentId(documentId);
		
		SimplePipeline.runPipeline(jCas, documentParser);
		
		try (FileOutputStream fileOutputStream = new FileOutputStream(Paths.get(sOutputPath, documentId + ".xmi").toFile())) {
			XmiCasSerializer.serialize(jCas.getCas(), fileOutputStream);
//						System.out.printf("\r%d/%d Wrote document %s.xmi", count, metadata.size(), documentId);
		} catch (SAXException | IOException e) {
			System.err.printf("Failed serialization of XMI for document %s!\n", documentId);
			e.printStackTrace();
		}
		
		if (!sRawPath.isEmpty()) {
			try (PrintWriter printWriter = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get(sOutputPath, documentId + ".txt")), StandardCharsets.UTF_8))) {
				printWriter.print(getValidText(jCas));
//							System.out.printf(", %s.txt", documentId);
			} catch (IOException e) {
				System.err.printf("Failed serialization of raw text for document %s!\n", documentId);
			}
		}
	}
	
}
