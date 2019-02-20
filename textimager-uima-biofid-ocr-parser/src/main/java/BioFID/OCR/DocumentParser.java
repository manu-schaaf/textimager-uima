package BioFID.OCR;

import BioFID.OCR.Annotation.*;
import BioFID.Util;
import com.google.common.collect.ImmutableList;
import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.Anomaly;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.SegmenterBase;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Document;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.uima.UIMA_UnsupportedOperationException;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.texttechnologylab.annotation.ocr.OCRLine;
import org.texttechnologylab.annotation.ocr.OCRToken;
import org.texttechnologylab.annotation.ocr.OCRpage;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

import static BioFID.Util.inDict;
import static BioFID.Util.loadDict;

//import org.languagetool.JLanguageTool;

public class DocumentParser extends SegmenterBase {

	public static final String INPUT_PATHS = "pInputPaths";
	public static final String PARAM_DICT_PATH = "pDictPath";
	public static final String PARAM_MIN_TOKEN_CONFIDENCE = "pMinTokenConfidence";
	public static final String PARAM_USE_LANGUAGE_TOOL = "pUseLanguageTool";
	public static final String PARAM_CHAR_LEFT_MAX = "pCharLeftMax";
	public static final String PARAM_BLOCK_TOP_MIN = "pBlockTopMin";
	public static final String PARAM_MULTI_DOC = "pMultiDocArr";
	@ConfigurationParameter(name = INPUT_PATHS, mandatory = true)
	protected String[] pInputPaths;
	@ConfigurationParameter(name = PARAM_DICT_PATH, mandatory = false)
	protected String pDictPath;
	@ConfigurationParameter(name = PARAM_MIN_TOKEN_CONFIDENCE, mandatory = false, defaultValue = "80")
	protected Integer pMinTokenConfidence;
	@ConfigurationParameter(name = PARAM_USE_LANGUAGE_TOOL, mandatory = false, defaultValue = "false")
	protected Boolean pUseLanguageTool;
	@ConfigurationParameter(name = PARAM_CHAR_LEFT_MAX, mandatory = false, defaultValue = "99999")
	protected Integer pCharLeftMax;
	@ConfigurationParameter(name = PARAM_BLOCK_TOP_MIN, mandatory = false, defaultValue = "300")
	protected Integer pBlockTopMin;
	@ConfigurationParameter(name = PARAM_MULTI_DOC, mandatory = false)
	protected String[] pMultiDocArr;

	private HashSet<String> dict;

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {

		try {
			dict = loadDict(pDictPath);
//			JLanguageTool langTool = new JLanguageTool(new org.languagetool.language.GermanyGerman()); // FIXME
			SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
			SAXParser saxParser = saxParserFactory.newSAXParser();

			final HashMap<String, ExportHandler> pages = new HashMap<>(pInputPaths.length);
			for (String pagePath : pInputPaths) {
				pages.put(pagePath, Util.getExportHandler(saxParser, pagePath, pCharLeftMax, pBlockTopMin));
			}
			/// Check if any of the files contains more than one document. FIXME: implement multi page documents
			if (pages.values().stream().anyMatch(page -> page.pages.size() > 1)) {
				throw new UIMA_UnsupportedOperationException(new NotImplementedException("Input documents may not contain more than one page."));
			}

			final StringBuilder text = new StringBuilder();
			for (String pagePath : pInputPaths) {
				text.append(pages.get(pagePath).tokens.stream().map(Token::getTokenString).collect(Collectors.joining("")));
			}

			aJCas.setDocumentText(text.toString());

			int lastOffset = 0;
			int lastDocumentOffset = 0;
			Document lastDocument = null;
			String lastDocumentParent = null;

			ImmutableList<String> multiDocumentPaths;
			if (Objects.nonNull(pMultiDocArr)) {
				multiDocumentPaths = ImmutableList.copyOf(pMultiDocArr);
			} else {
				multiDocumentPaths = ImmutableList.of();
			}

			for (int i = 0; i < pInputPaths.length; i++) {
				String inputPath = pInputPaths[i];
				ExportHandler exportHandler = pages.get(inputPath);
				String pageId = Paths.get(inputPath).getFileName().toString();

				Page page = exportHandler.pages.get(0);
				page.pageId = pageId;
				page.pageNumber = i;
				OCRpage ocrPage = page.wrap(aJCas, lastOffset);
				aJCas.addFsToIndexes(ocrPage);

				for (Block block : exportHandler.blocks) {
					aJCas.addFsToIndexes(block.wrap(aJCas, lastOffset));
				}
				for (Paragraph paragraph : exportHandler.paragraphs) {
					aJCas.addFsToIndexes(paragraph.wrap(aJCas, lastOffset));
				}
				for (Line line : exportHandler.lines) {
					OCRLine ocrLine = line.wrap(aJCas, lastOffset);
					aJCas.addFsToIndexes(ocrLine);
				}
				for (Token token : exportHandler.tokens) {
					if (token.isSpace())
						continue;

					OCRToken ocrToken = token.wrap(aJCas, lastOffset);
					aJCas.addFsToIndexes(ocrToken);

					for (OCRToken subtoken : token.wrapSubtokens(aJCas, lastOffset)) {
						aJCas.addFsToIndexes(subtoken);
					}

					boolean inDict = inDict(token.getTokenString(), dict);
					if (!inDict && (token.getAverageCharConfidence() < pMinTokenConfidence || !(token.isWordNormal || token.isWordFromDictionary || token.isWordNumeric))) {
						Anomaly anomaly = new Anomaly(aJCas, token.start, token.end);
						anomaly.setDescription(String.format("AvgTokenConfidence:%f, isWordNormal:%b, isWordFromDictionary:%b, inDict:%b, isWordNumeric:%b, suspiciousChars:%d",
								token.getAverageCharConfidence(), token.isWordNormal, token.isWordFromDictionary, inDict, token.isWordNumeric, token.suspiciousChars));
						aJCas.addFsToIndexes(anomaly);
					}
//					else if (false && token.containsHyphen() || token.subTokenStrings().size() > 1) { // FIXME
//						NamedEntity annotation = new NamedEntity(aJCas, token.start, token.end);
//						annotation.setValue(String.format("AvgTokenConfidence:%f, isWordNormal:%b, isWordFromDictionary:%b, inDict:%b, isWordNumeric:%b, suspiciousChars:%d, containsHyphen:%b, subTokens:%s",
//								token.getAverageCharConfidence(), token.isWordNormal, token.isWordFromDictionary, inDict, token.isWordNumeric, token.suspiciousChars, token.containsHyphen(), token.subTokenStrings()));
//						aJCas.addFsToIndexes(annotation);
//					}
				}

				lastOffset = ocrPage.getEnd();

				// FIXME: could produce a bug, if there are files above the document level and they are parsed after the documents.
				String currentDocumentParent = Paths.get(inputPath).getParent().toString();
				if (!currentDocumentParent.equals(lastDocumentParent)) {
					if (Objects.nonNull(lastDocument)) {
						lastDocument.setEnd(lastOffset);
						aJCas.addFsToIndexes(lastDocument);
						lastDocumentOffset = lastOffset;
					}
					if (multiDocumentPaths.indexOf(currentDocumentParent) > -1) {
						lastDocument = new Document(aJCas);
						lastDocument.setBegin(lastDocumentOffset);
					}
					lastDocumentParent = currentDocumentParent;
				}
			}
			if (Objects.nonNull(lastDocument)) {
				lastDocument.setEnd(lastOffset);
				aJCas.addFsToIndexes(lastDocument);
			}

			if (pUseLanguageTool) {
//				languageToolSpellcheck(aJCas, langTool, text);
			}

		} catch (SAXException | ParserConfigurationException | IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void process(JCas aJCas, String text, int zoneBegin) throws AnalysisEngineProcessException {

	}
}
