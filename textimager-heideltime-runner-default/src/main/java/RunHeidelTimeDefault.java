import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.unihd.dbs.uima.annotator.heideltime.HeidelTime;
import de.unihd.dbs.uima.types.heideltime.Sentence;
import de.unihd.dbs.uima.types.heideltime.Token;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.util.Span;
import org.apache.commons.lang3.StringUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.cas.impl.XmiSerializationSharedData;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.factory.AnnotationFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.factory.UimaContextFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.dkpro.core.corenlp.CoreNlpSegmenter;
import org.dkpro.core.io.text.TextReader;
import org.dkpro.core.io.xmi.XmiReader;
import org.dkpro.core.matetools.MatePosTagger;
import org.hucompute.textimager.uima.io.tei.TeiReaderTTLab;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunHeidelTimeDefault {
    static final InputStream sentenceModelStream = RunHeidelTimeDefault.class.getResourceAsStream("opennlp-de-ud-gsd-sentence-1.0-1.9.3.bin");

    public static void main(String[] args) throws FileNotFoundException {
        if (args.length < 3) {
            throw new RuntimeException("Not enough arguments!");
        }

        String path = args[0];
//        String pattern = "WP**/*.xml";
        String pattern = args[1];
        String output_path = args[2];

        // Check if given path exists
        File filepath = Paths.get(path).toFile();
        if (!filepath.exists() || !filepath.isDirectory()) {
            throw new FileNotFoundException(String.format("The given path '%s' does not exist!", path));
        }

        try {

            Paths.get(output_path).toFile().mkdirs();

            final CollectionReader reader;
            if (pattern.endsWith(".xml") || pattern.endsWith(".xmi")) {
                reader = CollectionReaderFactory.createReader(
                        XmiReader.class,
                        XmiReader.PARAM_SOURCE_LOCATION, path,
                        XmiReader.PARAM_PATTERNS, pattern,
                        XmiReader.PARAM_LANGUAGE, "de",
                        XmiReader.PARAM_LENIENT, true
                );
            } else if (pattern.endsWith(".tei")) {
                reader = CollectionReaderFactory.createReader(
                        TeiReaderTTLab.class,
                        TeiReaderTTLab.PARAM_SOURCE_LOCATION, path,
                        TeiReaderTTLab.PARAM_PATTERNS, pattern,
                        TeiReaderTTLab.PARAM_LANGUAGE, "de"
                );
            } else if (pattern.endsWith(".txt")) {
                reader = CollectionReaderFactory.createReader(
                        TextReader.class,
                        TextReader.PARAM_SOURCE_LOCATION, path,
                        TextReader.PARAM_PATTERNS, pattern,
                        TextReader.PARAM_LANGUAGE, "de"
                );
            } else {
                throw new RuntimeException(String.format("Unrecognized input format pattern: '%s'!", pattern));
            }

            HeidelTime heidelTime = new HeidelTime();
            heidelTime.initialize(
                    UimaContextFactory.createUimaContext(
                            "Language", "german",
                            "locale", "de_DE",
                            "Type", "narrative",
                            "Date", true,
                            "Time", true,
                            "Duration", true,
                            "Set", true,
                            "Temponym", true,
                            "Debugging", false,
                            "ConvertDurations", true
                    )
            );

            SentenceDetectorME sentenceDetectorME = new SentenceDetectorME(new SentenceModel(Objects.requireNonNull(sentenceModelStream)));

            CoreNlpSegmenter tokenizer = new CoreNlpSegmenter();
            tokenizer.initialize(UimaContextFactory.createUimaContext(
                    CoreNlpSegmenter.PARAM_LANGUAGE, "de",
                    CoreNlpSegmenter.PARAM_WRITE_FORM, false,
                    CoreNlpSegmenter.PARAM_WRITE_TOKEN, true,
                    CoreNlpSegmenter.PARAM_WRITE_SENTENCE, false
            ));
            RangeSplitter rangeSplitter = new RangeSplitter();

            MatePosTagger matePosTagger = new MatePosTagger();
            matePosTagger.initialize(UimaContextFactory.createUimaContext(
                    MatePosTagger.PARAM_LANGUAGE, "de"
            ));

            JCas jCas = JCasFactory.createJCas();
            while (reader.hasNext()) {
                try {
                    reader.getNext(jCas.getCas());
                    String documentText = jCas.getDocumentText();
                    documentText = documentText
                            .replaceAll("[\f]", "")
                            .replaceAll("\\s+", " ");

                    DocumentMetaData oldMeta = DocumentMetaData.get(jCas);
                    HashMap<String, String> oldMetaBackup = new HashMap<>();
                    for (Feature feature : oldMeta.getType().getFeatures()) {
                        if (feature.getName().startsWith(DocumentMetaData.class.getName())) {
                            oldMetaBackup.put(feature.getShortName(), oldMeta.getFeatureValueAsString(feature));
                        }
                    }

                    jCas.reset();

                    jCas.setDocumentText(documentText);
                    jCas.setDocumentLanguage("de");

                    DocumentMetaData meta = DocumentMetaData.create(jCas);
                    meta.setDocumentId(oldMeta.getDocumentId());
                    meta.setLanguage("de");
                    meta.setDocumentUri(oldMetaBackup.get("documentUri"));
                    meta.setCollectionId(oldMetaBackup.get("collectionId"));
                    meta.setDocumentBaseUri(oldMetaBackup.get("documentBaseUri"));
                    meta.setIsLastSegment(Boolean.parseBoolean(oldMetaBackup.get("isLastSegment")));
                    meta.setDocumentId(cleanExtension(oldMetaBackup.get("documentId")));
                    meta.setDocumentTitle(cleanExtension(oldMetaBackup.get("documentTitle")));

                    JCas heidelTimeDefaultView = jCas.createView("heidelTimeDefault");
                    heidelTimeDefaultView.setDocumentLanguage("de");
                    heidelTimeDefaultView.setDocumentText(documentText);

                    for (Span span : sentenceDetectorME.sentPosDetect(documentText)) {
                        createAnnotation(heidelTimeDefaultView, span.getStart(), span.getEnd(), Sentence.class);
                        createAnnotation(heidelTimeDefaultView, span.getStart(), span.getEnd(), de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence.class);
                    }

                    tokenizer.process(heidelTimeDefaultView);
                    rangeSplitter.process(heidelTimeDefaultView);

                    matePosTagger.process(heidelTimeDefaultView);

                    JCasUtil.select(heidelTimeDefaultView, de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token.class).forEach(
                            token -> {
                                Token newToken = new Token(heidelTimeDefaultView, token.getBegin(), token.getEnd());
                                newToken.setPos(token.getPosValue());
                                heidelTimeDefaultView.addFsToIndexes(newToken);
                            }
                    );

                    heidelTime.process(heidelTimeDefaultView);

                    XmiSerializationSharedData xmiSerializationSharedData = new XmiSerializationSharedData();
                    String outputFilePath = Paths.get(output_path, meta.getDocumentId()).toString();
                    outputFilePath = cleanExtension(outputFilePath);
                    outputFilePath = StringUtils.appendIfMissing(outputFilePath, ".xmi");
                    try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(outputFilePath))) {
                        XmiCasSerializer.serialize(jCas.getCas(), jCas.getTypeSystem(), stream, true, xmiSerializationSharedData);
                    } catch (SAXException e) {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    jCas.reset();
                }
            }

        } catch (UIMAException | IOException e) {
            e.printStackTrace();
        }
    }

    private static String cleanExtension(String string) {
        string = StringUtils.removeEndIgnoreCase(string, ".xmi");
        string = StringUtils.removeEndIgnoreCase(string, ".xml");
        string = StringUtils.removeEndIgnoreCase(string, ".txt");
        string = StringUtils.removeEndIgnoreCase(string, ".tei");
        return string;
    }

    private static void createAnnotation(JCas jCas, int start, int end, Class<? extends Annotation> aClass) {
        jCas.addFsToIndexes(AnnotationFactory.createAnnotation(jCas, start, end, aClass));
    }

    private static class RangeSplitter extends JCasAnnotator_ImplBase {
        private final Pattern pattern = Pattern.compile("(\\d+)-(\\d+)");

        @Override
        public void process(JCas aJCas) throws AnalysisEngineProcessException {
            for (de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token originalToken : JCasUtil.select(aJCas, de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token.class)) {
                Matcher matcher = pattern.matcher(originalToken.getCoveredText());
                if (matcher.matches()) {
                    String firstGroup = matcher.group(1);

                    de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token firstRangePart = new de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token(
                            aJCas,
                            originalToken.getBegin(),
                            originalToken.getBegin() + firstGroup.length()
                    );
                    aJCas.addFsToIndexes(firstRangePart);

                    de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token hyphen = new de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token(
                            aJCas,
                            originalToken.getBegin() + firstGroup.length(),
                            originalToken.getBegin() + firstGroup.length() + 1
                    );
                    aJCas.addFsToIndexes(hyphen);

                    de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token secondRangePart = new de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token(
                            aJCas,
                            originalToken.getBegin() + firstGroup.length() + 1,
                            originalToken.getEnd()
                    );
                    aJCas.addFsToIndexes(secondRangePart);

                    aJCas.removeFsFromIndexes(originalToken);
                }
            }
        }
    }
}
