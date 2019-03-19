package BioFID.OCR;

import BioFID.AbstractRunner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.Anomaly;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.texttechnologylab.annotation.ocr.OCRDocument;
import org.texttechnologylab.annotation.ocr.OCRToken;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.util.JCasUtil.*;

public abstract class AbstractDocumentParser extends AbstractRunner {

    protected static void processDocumentPathList(String sOutputPath, String sVocabularyPath, String sRawPath, String documentId, ArrayList<String> pathList) throws UIMAException {
        processDocumentPathList(sOutputPath, sVocabularyPath, sRawPath, documentId, pathList, false, null);
    }

    protected static void processDocumentPathList(String sOutputPath, String sVocabularyPath, String sRawPath, String documentId, ArrayList<String> pathList, boolean bMultiDoc, @Nullable File collectionRootDir) throws UIMAException {
        AnalysisEngineDescription documentParser = createEngineDescription(CollectionProcessEngine.class,
                CollectionProcessEngine.INPUT_PATHS, pathList.toArray(new String[0]),
                CollectionProcessEngine.COLLECTION_ROOT_DIR, Objects.nonNull(collectionRootDir) ? collectionRootDir.toString() : "",
                CollectionProcessEngine.PARAM_MIN_TOKEN_CONFIDENCE, 75,
                CollectionProcessEngine.PARAM_BLOCK_TOP_MIN, 0,
                CollectionProcessEngine.PARAM_DICT_PATH, sVocabularyPath,
                CollectionProcessEngine.PARAM_MULTI_DOC, bMultiDoc);

        JCas jCas = JCasFactory.createJCas();

        DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
        documentMetaData.setDocumentId(documentId); // FIXME: migrate to collectionId


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


    public static String getValidText(JCas jCas) {
//		ImmutableMap<OCRBlock, Collection<OCRToken>> blockCovered = ImmutableMap.copyOf(indexCovered(jCas, OCRBlock.class, OCRToken.class));
//
//		ImmutableSet<OCRToken> tokenCovering = ImmutableSet.copyOf(indexCovering(jCas, OCRToken.class, OCRToken.class).keySet());
//		ImmutableSet<OCRToken> anomalies = ImmutableSet.copyOf(indexCovered(jCas, Anomaly.class, OCRToken.class).values().stream().flatMap(Collection::stream).collect(Collectors.toSet()));
//
//		StringBuilder retStringBuilder = new StringBuilder();
//
//		for (OCRBlock ocrBlock : select(jCas, OCRBlock.class)) {
//			if (ocrBlock.getValid()) {
//				if (!blockCovered.containsKey(ocrBlock) || blockCovered.get(ocrBlock) == null || blockCovered.get(ocrBlock).isEmpty())
//					continue;
//				for (OCRToken ocrToken : blockCovered.get(ocrBlock)) {
//					if (tokenCovering.contains(ocrToken)) continue;
////					if (!ocrToken.getCoveredText().equals(" ") && (anomalies.contains(ocrToken) || tokenCovering.contains(ocrToken))) continue;
//					retStringBuilder.append(ocrToken.getCoveredText()).append(" ");
//				}
//			}
//		}

//		StringBuilder debugStringBuilder = new StringBuilder();
//		for (OCRBlock ocrBlock : select(jCas, OCRBlock.class)) {
//			debugStringBuilder.append(String.format("<OCRBlock valid:%b, type:%s, top:%d, bottom:%d>\n", ocrBlock.getValid(), ocrBlock.getBlockType(), ocrBlock.getTop(), ocrBlock.getBottom()));
//			if (!blockCovered.containsKey(ocrBlock) || blockCovered.get(ocrBlock) == null || blockCovered.get(ocrBlock).isEmpty())
//				continue;
//			for (OCRToken ocrToken : blockCovered.get(ocrBlock)) {
//				if (tokenCovering.contains(ocrToken)) continue;
////					if (!ocrToken.getCoveredText().equals(" ") && (anomalies.contains(ocrToken) || tokenCovering.contains(ocrToken))) continue;
//				debugStringBuilder.append(ocrToken.getCoveredText());
//			}
//			debugStringBuilder.append("\n</OCRBlock>\n");
//		}
//		System.out.println(debugStringBuilder.toString());

        ArrayList<OCRDocument> ocrDocuments = new ArrayList<>(select(jCas, OCRDocument.class));
//        ocrDocuments.removeAll(indexCovered(jCas, OCRDocument.class, OCRDocument.class).keySet());

        ImmutableMap<OCRDocument, Collection<OCRToken>> documentCoveringToken = ImmutableMap.copyOf(indexCovered(jCas, OCRDocument.class, OCRToken.class));

        ImmutableSet<OCRToken> tokenCovering = ImmutableSet.copyOf(indexCovering(jCas, OCRToken.class, OCRToken.class).keySet());
        ImmutableSet<OCRToken> anomalies = ImmutableSet.copyOf(indexCovered(jCas, Anomaly.class, OCRToken.class).entrySet().stream()
                .filter(entry -> entry.getKey().getCategory().equals("BioFID_Garbage_Line_Anomaly"))
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet()));

        StringBuilder retStringBuilder = new StringBuilder();

        for (OCRDocument ocrDocument : ocrDocuments) {
            if (!documentCoveringToken.containsKey(ocrDocument) || ocrDocument.getCoveredText().isEmpty())
                continue;
            for (OCRToken ocrToken : documentCoveringToken.get(ocrDocument)) {
                if (tokenCovering.contains(ocrToken)
//                        || anomalies.contains(ocrToken)
                ) continue;
                retStringBuilder.append(ocrToken.getCoveredText()).append(" ");
            }
        }

        return retStringBuilder.toString();
    }

}
