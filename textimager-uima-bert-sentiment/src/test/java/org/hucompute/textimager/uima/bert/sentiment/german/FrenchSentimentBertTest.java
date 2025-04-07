package org.hucompute.textimager.uima.bert.sentiment.german;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.hucompute.textimager.uima.type.Sentiment;
import org.hucompute.textimager.uima.util.XmlFormatter;
import org.junit.Test;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;

public class FrenchSentimentBertTest {
	@Test
	public void testNlpTown() throws UIMAException {
		String[] sentences = new String[] {
				"J'adore ça!",
				"Je déteste ça!",
				"c'est ok"
		};

		JCas jCas = JCasFactory.createJCas();
		jCas.setDocumentLanguage("fr");

		StringBuilder sentence = new StringBuilder();
		for (String s : sentences) {
			Sentence anno = new Sentence(jCas, sentence.length(), sentence.length()+s.length());
			anno.addToIndexes();
			sentence.append(s).append(" ");
		};
		jCas.setDocumentText(sentence.toString());

		AnalysisEngineDescription bertSentiment = createEngineDescription(SentimentBert.class,
				SentimentBert.PARAM_SELECTION, "text,de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence",
				SentimentBert.PARAM_MODEL_NAME, "nlptown/bert-base-multilingual-uncased-sentiment",
				SentimentBert.PARAM_MODEL_MAX_LENGTH, 512,
				SentimentBert.PARAM_SENTIMENT_MAPPINGS, new String[] {
						"1 star;-1",
						"2 stars;-0.5",
						"3 stars;0",
						"4 stars;0.5",
						"5 stars;1",
				}
		);

		SimplePipeline.runPipeline(jCas, bertSentiment);

		System.out.println(XmlFormatter.getPrettyString(jCas));
		for (Sentiment sentiment : JCasUtil.select(jCas, Sentiment.class)) {
			System.out.println(sentiment.getCoveredText() + " -> " + sentiment.getSentiment());
		}
	}

	@Test
	public void testCardiffXlm() throws UIMAException {
		String[] sentences = new String[] {
				"J'adore ça!",
				"Je déteste ça!",
				"c'est ok"
		};

		JCas jCas = JCasFactory.createJCas();
		jCas.setDocumentLanguage("fr");

		StringBuilder sentence = new StringBuilder();
		for (String s : sentences) {
			Sentence anno = new Sentence(jCas, sentence.length(), sentence.length()+s.length());
			anno.addToIndexes();
			sentence.append(s).append(" ");
		};
		jCas.setDocumentText(sentence.toString());

		AnalysisEngineDescription bertSentiment = createEngineDescription(SentimentBert.class,
				SentimentBert.PARAM_SELECTION, "text,de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence",
				SentimentBert.PARAM_MODEL_NAME, "cardiffnlp/twitter-xlm-roberta-base-sentiment",
				SentimentBert.PARAM_MODEL_MAX_LENGTH, 512,
				SentimentBert.PARAM_SENTIMENT_MAPPINGS, new String[] {
						"Negative;-1",
						"Neutral;0",
						"Positive;1"
				}
		);

		SimplePipeline.runPipeline(jCas, bertSentiment);

		System.out.println(XmlFormatter.getPrettyString(jCas));
		for (Sentiment sentiment : JCasUtil.select(jCas, Sentiment.class)) {
			System.out.println(sentiment.getCoveredText() + " -> " + sentiment.getSentiment());
		}
	}
}

