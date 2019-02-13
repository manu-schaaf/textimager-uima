package BioFID.OCR;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.texttechnologylab.annotation.ocr.OCRBlock;
import org.texttechnologylab.annotation.ocr.OCRToken;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.util.JCasUtil.indexCovered;
import static org.apache.uima.fit.util.JCasUtil.select;

public class DocumentFromMetadata {
	
	public static void main(String[] args) {
		String sMetadataPath = args[0];
		String sFileAtlasPath = args[1];
		String sOutputPath = args[2];
		String sVocabularyPath = args[3];
		boolean bWriteRawText = Boolean.parseBoolean(Strings.nullToEmpty(args[4]));
		
		try {
			ImmutableMap<String, String> fileAtlas = loadFileAtlas(Paths.get(sFileAtlasPath));
			ArrayList<ImmutableList<String>> metadata = loadMetadata(Paths.get(sMetadataPath));
			
			for (ImmutableList<String> documentParts : metadata) {
				ArrayList<String> pathList = new ArrayList<>();
				for (String documentId : documentParts) {
					String path = fileAtlas.getOrDefault(documentId, null);
					if (path != null && new File(path).isFile()) pathList.add(path);
				}
				
				String documentId = documentParts.get(0);
				AnalysisEngineDescription documentParser = createEngineDescription(DocumentParser.class,
						DocumentParser.INPUT_PATHS, pathList.toArray(new String[0]),
						DocumentParser.PARAM_MIN_TOKEN_CONFIDENCE, 90,
						DocumentParser.PARAM_DICT_PATH, sVocabularyPath);
				
				JCas jCas = JCasFactory.createJCas();
				
				DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
				documentMetaData.setDocumentId(documentId);
				
				SimplePipeline.runPipeline(jCas, documentParser);
				
				try (FileOutputStream fileOutputStream = com.google.common.io.Files.newOutputStreamSupplier(Paths.get(sOutputPath, documentId + ".xmi").toFile()).getOutput()) {
					XmiCasSerializer.serialize(jCas.getCas(), fileOutputStream);
				} catch (SAXException e) {
					// TODO: logging
					e.printStackTrace();
				}
				
				if (bWriteRawText) {
					try (BufferedWriter bufferedWriter = Files.newBufferedWriter(Paths.get(sOutputPath, documentId + ".txt"))) {
						Map<OCRBlock, Collection<OCRToken>> blockCovered = indexCovered(jCas, OCRBlock.class, OCRToken.class);
						for (OCRBlock ocrBlock : select(jCas, OCRBlock.class)) {
							if (ocrBlock.getValid()) {
								for (OCRToken ocrToken : blockCovered.get(ocrBlock)) {
									bufferedWriter.write(ocrToken.getCoveredText());
								}
							}
						}
					}
				}
			}
		} catch (IOException | UIMAException e) {
			e.printStackTrace();
		}
	}
	
	
	static private ArrayList<ImmutableList<String>> loadMetadata(Path pMetadataPath) throws IOException {
		ArrayList<ImmutableList<String>> metadata = new ArrayList<>();
		String[] metadataLines = Files.newBufferedReader(pMetadataPath, StandardCharsets.UTF_8).lines().toArray(String[]::new);
		
		ArrayList<String> currentMetaDocument = new ArrayList<>();
//		String currentMetaDocumentName;
		/// Skip first line
		for (int i = 1; i < metadataLines.length; i++) {
			String[] split = metadataLines[i].split("\t");
			if (!split[2].isEmpty()) {
//				currentMetaDocumentName = split[0];
				if (!currentMetaDocument.isEmpty())
					metadata.add(ImmutableList.copyOf(currentMetaDocument));
				currentMetaDocument = new ArrayList<>();
			}
			currentMetaDocument.add(split[0]);
		}
		
		return metadata;
	}
	
	static private ImmutableMap<String, String> loadFileAtlas(Path pFileAtlasPath) throws IOException, NullPointerException {
		HashMap<String, String> fileAtlas = new HashMap<>();
		try (BufferedReader bufferedReader = com.google.common.io.Files.newReader(pFileAtlasPath.toFile(), StandardCharsets.UTF_8)) {
			bufferedReader.lines().map(l -> l.split("\t")).forEach(arr -> fileAtlas.put(arr[0], arr[1]));
		}
		if (fileAtlas.isEmpty()) {
			throw new NullPointerException("Files could not be found!");
		}
		return ImmutableMap.copyOf(fileAtlas);
	}
}
