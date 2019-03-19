package org.hucompute.textimager.uima.TaxonRecognition;

import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.hucompute.textimager.uima.NeuralnetworkNER.TaxonRecognition;
import org.junit.Test;

import java.util.stream.Collectors;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngine;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit-tests for neuralnetwork NER tagging.
 *
 * @author Manuel Stoeckel
 */
public class TaxonRecognitionTest {

    private JCas getExampleJCas() throws UIMAException {
        String aText = "Häufiger findet sie sich auch noch zusammen mit dem Lauch-Hederich als Unterscheidungsart in einer besonderen Subassoziation des Chaerophylletum bulbosi Tx. der reichen Auenlandschaften.";
        JCas cas = JCasFactory.createText(aText);
        cas.setDocumentLanguage("de");

        int offset = 0;
        for (String s : aText.split(" ")) {
            Token t = new Token(cas, offset, offset + s.length());
            t.addToIndexes();
            offset = offset + 1 + s.length();
        }
        return cas;
    }

    private JCas getExampleJCas2() throws UIMAException {
        String aText = "Von Dr. VOLKART bei Rovio am Generoso und von Prof. CORRENS am Campolungopass.";
        JCas cas = JCasFactory.createText(aText);
        cas.setDocumentLanguage("de");

        int offset = 0;
        for (String s : aText.split(" ")) {
            Token t = new Token(cas, offset, offset + s.length());
            t.addToIndexes();
            offset = offset + 1 + s.length();
        }
        return cas;
    }

    @Test
    public void test_BioFID_first() throws UIMAException {
        AnalysisEngineDescription taxonRecognitionEngineDescription = createEngineDescription(TaxonRecognition.class,
                TaxonRecognition.PARAM_DOCKER_IMAGE, "textimager-taxon-recognition",
                TaxonRecognition.PARAM_MODEL_NAME, "BioFID",
//				TaxonRecognition.PARAM_REST_ENDPOINT, "http://sirao.hucompute.org:5001"
                TaxonRecognition.PARAM_REST_ENDPOINT, "http://localhost:5001"
        );

        AnalysisEngine taxonRecognitionEngine = createEngine(taxonRecognitionEngineDescription);

        try {
            // 1st Test
            JCas cas = getExampleJCas();

            SimplePipeline.runPipeline(cas, taxonRecognitionEngine);

            // Expected
            String[] ents = new String[]{"I-TAX"};
            String[] strings = new String[]{"Chaerophylletum bulbosi"};

            String[] nerEnts = JCasUtil.select(cas, NamedEntity.class).stream().map(NamedEntity::getValue).toArray(String[]::new);
            String[] nerStrings = JCasUtil.select(cas, NamedEntity.class).stream().map(NamedEntity::getCoveredText).toArray(String[]::new);

            // Assertions
            System.out.println(JCasUtil.select(cas, NamedEntity.class).stream().map(ne -> ne.toString() + "   coveredText: \"" + ne.getCoveredText() + "\"").collect(Collectors.joining(", ")));
            assertArrayEquals(ents, nerEnts);
            assertArrayEquals(strings, nerStrings);
        } finally {
            taxonRecognitionEngine.destroy();
        }

    }

    @Test
    public void test_BioFID_second() throws UIMAException {
        AnalysisEngineDescription taxonRecognitionEngineDescription = createEngineDescription(TaxonRecognition.class,
                TaxonRecognition.PARAM_DOCKER_IMAGE, "textimager-taxon-recognition",
                TaxonRecognition.PARAM_MODEL_NAME, "BioFID",
//				TaxonRecognition.PARAM_REST_ENDPOINT, "http://sirao.hucompute.org:5001"
                TaxonRecognition.PARAM_REST_ENDPOINT, "http://localhost:5001"
        );

        AnalysisEngine taxonRecognitionEngine = createEngine(taxonRecognitionEngineDescription);

        try {
            // 1st Test
            JCas cas = getExampleJCas2();

            SimplePipeline.runPipeline(cas, taxonRecognitionEngine);

            // Expected
            String[] ents = new String[]{"I-TAX"};
            String[] strings = new String[]{"Chaerophylletum bulbosi"};

            String[] nerEnts = JCasUtil.select(cas, NamedEntity.class).stream().map(NamedEntity::getValue).toArray(String[]::new);
            String[] nerStrings = JCasUtil.select(cas, NamedEntity.class).stream().map(NamedEntity::getCoveredText).toArray(String[]::new);

            // Assertions
            System.out.println(JCasUtil.select(cas, NamedEntity.class).stream().map(ne -> ne.toString() + "   coveredText: \"" + ne.getCoveredText() + "\"\n").collect(Collectors.joining(", ")));
            assertTrue(nerEnts.length > 0);
//            assertArrayEquals(ents, nerEnts);
            assertArrayEquals(strings, nerStrings);
        } finally {
            taxonRecognitionEngine.destroy();
        }

    }

}
