import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.Anomaly;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.SegmenterBase;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.commons.compress.utils.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;

public class BioFIDOCRPageParser extends SegmenterBase {

    public static final String INPUT_XML = "InputXML";
    @ConfigurationParameter(name = INPUT_XML, mandatory = true)
    protected String InputXML;

    public static final String PARAM_MIN_TOKEN_CONFIDENCE = "MinTokenConfidence";
    @ConfigurationParameter(name = PARAM_MIN_TOKEN_CONFIDENCE, mandatory = false, defaultValue = "80")
    protected Integer MinTokenConfidence;

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {

        try {
            SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
            SAXParser saxParser = saxParserFactory.newSAXParser();
            OCRExportHandler ocrExportHandler = new OCRExportHandler();
            InputStream inputStream = IOUtils.toInputStream(InputXML, Charsets.UTF_8);
            saxParser.parse(inputStream, ocrExportHandler);

            aJCas.setDocumentText(ocrExportHandler.baseTokens.stream().map(BaseToken::getTokenString).collect(Collectors.joining("")));

            for (BaseToken baseToken : ocrExportHandler.baseTokens) {
                aJCas.addFsToIndexes(new Token(aJCas, baseToken.tokenStart, baseToken.tokenEnd));
            }

            for (BaseToken baseToken : ocrExportHandler.baseTokens) {
                if (baseToken.getAverageCharConfidence() < MinTokenConfidence || !(baseToken.isWordNormal || baseToken.isWordFromDictionary || baseToken.isWordNumeric)) {
                    Anomaly anomaly = new Anomaly(aJCas, baseToken.tokenStart, baseToken.tokenEnd);
                    anomaly.setDescription(String.format("AvgTokenConfidence:%f, isWordNormal:%b, isWordFromDictionary:%b, isWordNumeric:%b",
                            baseToken.getAverageCharConfidence(), baseToken.isWordNormal, baseToken.isWordFromDictionary, baseToken.isWordNumeric));
                    aJCas.addFsToIndexes(anomaly);
                }
            }

        } catch (SAXException | ParserConfigurationException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void process(JCas aJCas, String text, int zoneBegin) throws AnalysisEngineProcessException {

    }
}
