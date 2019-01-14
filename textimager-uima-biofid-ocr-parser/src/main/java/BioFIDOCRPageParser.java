import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.Anomaly;
import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.SpellingAnomaly;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.SegmenterBase;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.commons.compress.utils.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.languagetool.JLanguageTool;
import org.languagetool.rules.RuleMatch;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BioFIDOCRPageParser extends SegmenterBase {

    public static final String INPUT_XML = "InputXML";
    @ConfigurationParameter(name = INPUT_XML, mandatory = true)
    protected String InputXML;

    public static final String PARAM_DICT_PATH = "DictPath";
    @ConfigurationParameter(name = PARAM_DICT_PATH, mandatory = false)
    protected String DictPath;

    HashSet<String> dict;

    public static final String PARAM_MIN_TOKEN_CONFIDENCE = "MinTokenConfidence";
    @ConfigurationParameter(name = PARAM_MIN_TOKEN_CONFIDENCE, mandatory = false, defaultValue = "80")
    protected Integer MinTokenConfidence;

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {

        try {
            loadDict();
            JLanguageTool langTool = new JLanguageTool(new org.languagetool.language.GermanyGerman());

            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            SAXParser saxParser = saxParserFactory.newSAXParser();
            OCRExportHandler ocrExportHandler = new OCRExportHandler();
            InputStream inputStream = IOUtils.toInputStream(InputXML, Charsets.UTF_8);
            saxParser.parse(inputStream, ocrExportHandler);

            String text = ocrExportHandler.OCRTokens.stream().map(OCRToken::getTokenString).collect(Collectors.joining(""));
            aJCas.setDocumentText(text);

            for (OCRToken OCRToken : ocrExportHandler.OCRTokens) {
                aJCas.addFsToIndexes(new Token(aJCas, OCRToken.tokenStart, OCRToken.tokenEnd));
            }

            for (OCRToken OCRToken : ocrExportHandler.OCRTokens) {
                if (OCRToken.isSpace())
                    continue;
                boolean inDict = inDict(OCRToken.getTokenString());
                if (!inDict && (OCRToken.getAverageCharConfidence() < MinTokenConfidence || !(OCRToken.isWordNormal || OCRToken.isWordFromDictionary || OCRToken.isWordNumeric))) {
                    Anomaly anomaly = new Anomaly(aJCas, OCRToken.tokenStart, OCRToken.tokenEnd);
                    anomaly.setDescription(String.format("AvgTokenConfidence:%f, isWordNormal:%b, isWordFromDictionary:%b, inDict:%b, isWordNumeric:%b, suspiciousChars:%d",
                            OCRToken.getAverageCharConfidence(), OCRToken.isWordNormal, OCRToken.isWordFromDictionary, inDict, OCRToken.isWordNumeric, OCRToken.suspiciousChars));
                    aJCas.addFsToIndexes(anomaly);
                } else {
                    NamedEntity annotation = new NamedEntity(aJCas, OCRToken.tokenStart, OCRToken.tokenEnd);
                    annotation.setValue(String.format("AvgTokenConfidence:%f, isWordNormal:%b, isWordFromDictionary:%b, inDict:%b, isWordNumeric:%b, suspiciousChars:%d, subTokens:%s",
                            OCRToken.getAverageCharConfidence(), OCRToken.isWordNormal, OCRToken.isWordFromDictionary, inDict, OCRToken.isWordNumeric, OCRToken.suspiciousChars, OCRToken.subTokenStrings()));
                    aJCas.addFsToIndexes(annotation);
                }
            }

            List<RuleMatch> ruleMatches = langTool.check(text, true, JLanguageTool.ParagraphHandling.NORMAL);
            for (RuleMatch ruleMatch : ruleMatches) {
                SpellingAnomaly spellingAnomaly = new SpellingAnomaly(aJCas, ruleMatch.getFromPos(), ruleMatch.getToPos());
                spellingAnomaly.setDescription(String.format("Message:%s, SuggestedReplacements:%s",
                        ruleMatch.getMessage(), ruleMatch.getSuggestedReplacements()));
                aJCas.addFsToIndexes(spellingAnomaly);
            }

        } catch (SAXException | ParserConfigurationException | IOException e) {
            e.printStackTrace();
        }
    }

    void loadDict() {
        if (DictPath != null) {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(DictPath)))) {
                dict = br.lines().collect(Collectors.toCollection(HashSet::new));
            } catch (Exception e) {
                getLogger().error("Dict could not be loaded!");
            }
        }
    }

    boolean inDict(String token) {
        Pattern pattern = Pattern.compile("[^-\\p{Alnum}]", Pattern.UNICODE_CHARACTER_CLASS);
        String word = pattern.matcher(token).replaceAll("").toLowerCase(); // FIXME: toLowerCase
        return dict != null && !word.isEmpty() && dict.contains(word);
    }

    @Override
    protected void process(JCas aJCas, String text, int zoneBegin) throws AnalysisEngineProcessException {

    }
}
