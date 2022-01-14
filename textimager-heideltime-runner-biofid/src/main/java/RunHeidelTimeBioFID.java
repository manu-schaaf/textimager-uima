import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.unihd.dbs.uima.annotator.heideltime.HeidelTime;
import de.unihd.dbs.uima.types.heideltime.Token;
import org.apache.commons.lang3.StringUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.cas.impl.XmiSerializationSharedData;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.AnnotationFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.factory.UimaContextFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.dkpro.core.io.text.TextReader;
import org.dkpro.core.io.xmi.XmiReader;
import org.hucompute.textimager.uima.io.tei.TeiReaderTTLab;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.file.Paths;
import java.util.Arrays;

public class RunHeidelTimeBioFID {
    final static String btPath = "D:/Projects/BioFID/Bundestag/";

    public static void main(String[] args) throws FileNotFoundException {
        if (args.length < 3) {
            throw new RuntimeException("Not enough arguments!");
        }

        String path = args[0];
//        String pattern = "output_default/*.xml";
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

            JCas jCas = JCasFactory.createJCas();
            while (reader.hasNext()) {
                try {
                    reader.getNext(jCas.getCas());
                    jCas.setDocumentLanguage("de");
                    DocumentMetaData meta = DocumentMetaData.get(jCas);
                    meta.setLanguage("de");

                    final JCas heidelTimeDefaultView = jCas.getView("heidelTimeDefault");
                    final JCas heidelTimeBioFIDView = JCasUtil.getView(jCas, "heidelTimeBioFID", true);
                    heidelTimeBioFIDView.removeAllIncludingSubtypes(Annotation.type);

                    heidelTimeBioFIDView.setDocumentLanguage("de");
                    heidelTimeBioFIDView.setDocumentText(jCas.getDocumentText());

                    final TypeSystem typeSystem = jCas.getTypeSystem();
                    for (Class<? extends Annotation> aClass : Arrays.asList(
                            de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token.class,
                            de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence.class,
                            de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS.class,
                            de.unihd.dbs.uima.types.heideltime.Sentence.class
                    )) {
                        JCasUtil.select(heidelTimeDefaultView, aClass).forEach(annotation -> {
                            Annotation copiedAnnotation = AnnotationFactory.createAnnotation(heidelTimeBioFIDView, annotation.getBegin(), annotation.getEnd(), annotation.getClass());
                            heidelTimeBioFIDView.addFsToIndexes(copiedAnnotation);
                        });
                    }

                    JCasUtil.select(heidelTimeDefaultView, Token.class).forEach(
                            token -> {
                                Token copiedAnnotation = AnnotationFactory.createAnnotation(heidelTimeBioFIDView, token.getBegin(), token.getEnd(), token.getClass());
                                copiedAnnotation.setPos(token.getPos());
                                heidelTimeBioFIDView.addFsToIndexes(copiedAnnotation);
                            }
                    );

                    heidelTime.process(heidelTimeBioFIDView);

                    XmiSerializationSharedData xmiSerializationSharedData = new XmiSerializationSharedData();
                    String outputFilePath = Paths.get(output_path, meta.getDocumentId()).toString();
                    outputFilePath = cleanExtension(outputFilePath);
                    outputFilePath = StringUtils.appendIfMissing(outputFilePath, ".xmi");
                    try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(outputFilePath))) {
                        XmiCasSerializer.serialize(jCas.getCas(), typeSystem, stream, true, xmiSerializationSharedData);
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
}
