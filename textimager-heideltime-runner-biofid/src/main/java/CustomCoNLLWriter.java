/*
 * Copyright 2016
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.unihd.dbs.uima.types.heideltime.Timex3;
import eu.openminted.share.annotations.api.DocumentationResource;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.descriptor.MimeTypeCapability;
import org.apache.uima.fit.descriptor.ResourceMetaData;
import org.apache.uima.fit.descriptor.TypeCapability;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.dkpro.core.api.io.JCasFileWriter_ImplBase;
import org.dkpro.core.api.parameter.ComponentParameters;
import org.dkpro.core.api.parameter.MimeTypes;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.uima.fit.util.JCasUtil.select;

/**
 * Custom CoNLL-like, tab-separated format writer for evaluating HeidelTime Timex3 annotations.
 */
@ResourceMetaData(name = "Custom CoNLL writer")
@DocumentationResource("${docbase}/format-reference.html#format-${command}")
@MimeTypeCapability({MimeTypes.TEXT_X_CONLL_2003})
@TypeCapability(
        inputs = {
                "de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData",
                "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence",
                "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token",
                "de.tudarmstadt.ukp.dkpro.core.api.syntax.type.chunk.Chunk",
                "de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity"})
public class CustomCoNLLWriter extends JCasFileWriter_ImplBase {
    private static final String UNUSED = "_";

    /**
     * Character encoding of the output data.
     */
    public static final String PARAM_TARGET_ENCODING = ComponentParameters.PARAM_TARGET_ENCODING;
    @ConfigurationParameter(name = PARAM_TARGET_ENCODING, mandatory = true,
            defaultValue = ComponentParameters.DEFAULT_ENCODING)
    private String targetEncoding;

    /**
     * Use this filename extension.
     */
    public static final String PARAM_FILENAME_EXTENSION =
            ComponentParameters.PARAM_FILENAME_EXTENSION;
    @ConfigurationParameter(name = PARAM_FILENAME_EXTENSION, mandatory = true, defaultValue = ".conll")
    private String filenameSuffix;

    /**
     * Write chunking information.
     */
    public static final String PARAM_WRITE_CHUNK = ComponentParameters.PARAM_WRITE_CHUNK;
    @ConfigurationParameter(name = PARAM_WRITE_CHUNK, mandatory = true, defaultValue = "true")
    private boolean writeChunk;

    /**
     * Write named entity information.
     */
    public static final String PARAM_WRITE_NAMED_ENTITY =
            ComponentParameters.PARAM_WRITE_NAMED_ENTITY;
    @ConfigurationParameter(name = PARAM_WRITE_NAMED_ENTITY, mandatory = true, defaultValue = "true")
    private boolean writeNamedEntity;

    /**
     * Write text covered by the token instead of the token form.
     */
    public static final String PARAM_WRITE_COVERED_TEXT =
            ComponentParameters.PARAM_WRITE_COVERED_TEXT;
    @ConfigurationParameter(name = PARAM_WRITE_COVERED_TEXT, mandatory = true, defaultValue = "true")
    private boolean writeCovered;

    /**
     * Skip sentences without annotations.
     */
    public static final String PARAM_SKIP_EMPTY_SENTENCES = "skipEmptySentences";
    @ConfigurationParameter(name = PARAM_SKIP_EMPTY_SENTENCES, mandatory = true, defaultValue = "true")
    private boolean skipEmptySentences;


    @Override
    public void process(JCas aJCas)
            throws AnalysisEngineProcessException {
        PrintWriter out = null;
        try {
            out = new PrintWriter(new OutputStreamWriter(getOutputStream(aJCas, filenameSuffix), targetEncoding));
            convert(aJCas, out);
        } catch (Exception e) {
            throw new AnalysisEngineProcessException(e);
        } finally {
            closeQuietly(out);
        }
    }

    private void convert(JCas jCas, PrintWriter aOut) throws CASException {
        final JCas heidelTimeDefaultView = jCas.getView("heidelTimeDefault");
        final JCas heidelTimeBioFIDView = jCas.getView("heidelTimeBioFID");

        ArrayList<Token> tokensDefault = new ArrayList<>(JCasUtil.select(heidelTimeDefaultView, Token.class));
        ArrayList<Token> tokensBioFID = new ArrayList<>(JCasUtil.select(heidelTimeBioFIDView, Token.class));

        // Create indices for Timex3 on the tokens they cover for both views
        Map<Token, Collection<Timex3>> timexIndexDefault = JCasUtil.indexCovering(heidelTimeDefaultView, Token.class, Timex3.class);
        Map<Token, Collection<Timex3>> timexIndexBioFID = JCasUtil.indexCovering(heidelTimeBioFIDView, Token.class, Timex3.class);

        for (Sentence sentence : select(heidelTimeDefaultView, Sentence.class)) {
            boolean anyAnnotation = false;

            // Get the token indices for all tokens within the current sentence
            Integer[] tokenIndices = JCasUtil.selectCovered(Token.class, sentence)
                    .stream()
                    .map(tokensDefault::indexOf)
                    .toArray(Integer[]::new);

            ArrayList<Row> rows = new ArrayList<>();
            for (Integer tokenIndex : tokenIndices) {
                Row row = new Row();

                row.token = tokensDefault.get(tokenIndex);

                Token tokenDefault = tokensDefault.get(tokenIndex);
                if (timexIndexDefault.get(tokenDefault).size() > 0) {
                    row.original = new ArrayList<>(timexIndexDefault.get(tokenDefault)).get(0);
                }

                Token tokenBioFID = tokensBioFID.get(tokenIndex);
                if (timexIndexBioFID.get(tokenBioFID).size() > 0) {
                    row.biofid = new ArrayList<>(timexIndexBioFID.get(tokenBioFID)).get(0);
                }

                rows.add(row);

                if (row.biofid != null || row.original != null) {
                    anyAnnotation = true;
                }
            }

            // Skip this sentence if there are no annotations in it.
            if (skipEmptySentences && !anyAnnotation) continue;

            for (Row row : rows) {
                String form = row.token.getCoveredText();

                String annotationDefault = "O";
                String ruleString = "_";
                if (row.original != null) {
                    annotationDefault = encode(row.token, row.original);
                    ruleString = row.original.getFoundByRule();
                }

                String annotationBioFID = "O";
                if (row.biofid != null) {
                    annotationBioFID = encode(row.token, row.biofid);
                    ruleString = row.biofid.getFoundByRule();
                }

                aOut.printf("%s\t%s\t%s\t%s\n", form, annotationDefault, annotationBioFID, ruleString);
            }

            aOut.println();
        }
    }

    /**
     * Encode the given {@link Timex3} annotation using the BIOES annotation scheme.
     *
     * @param token  The current token covered by the given {@link Timex3} annotation.
     * @param timex3 The current annotation that covers the given token.
     * @return A BIOES scheme formatted string of the {@link Timex3#getTimexType() timexType}.
     */
    private static String encode(Token token, Timex3 timex3) {
        if (token.getBegin() == timex3.getBegin() && token.getEnd() == timex3.getEnd()) {
            return String.format("S-%s", timex3.getTimexType());
        } else if (token.getBegin() == timex3.getBegin()) {
            return String.format("B-%s", timex3.getTimexType());
        } else if (token.getEnd() == timex3.getEnd()) {
            return String.format("E-%s", timex3.getTimexType());
        } else {
            return String.format("I-%s", timex3.getTimexType());
        }
    }

    private static final class Row {
        Token token;
        Timex3 original = null;
        Timex3 biofid = null;
    }
}
