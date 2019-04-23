package org.hucompute.textimager.biofid;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasIOUtils;
import org.jetbrains.annotations.NotNull;
import org.texttechnologylab.annotation.type.Taxon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public class EvaluateWithAnnotated {
	//	/resources/public/ahmed/BIOfid/BioFID_XMI_TI_16.04/
	public static void main(String[] args) throws ResourceInitializationException {
		
		final AnalysisEngine naiveTaggerEngine = AnalysisEngineFactory.createEngine(AnalysisEngineFactory.createEngineDescription(NaiveStringbasedTaxonTagger.class,
				NaiveStringbasedTaxonTagger.PARAM_SOURCE_LOCATION, "/resources/public/stoeckel/BioFID/taxa.txt",
				NaiveStringbasedTaxonTagger.PARAM_USE_LOWERCASE, true));
		
		boolean lastLineWasEmpty = true;
		try (PrintWriter conllWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream("/home/s3676959/Documents/taxonEval.conll"), StandardCharsets.UTF_8))) {
			Iterable<File> files = Files.fileTraverser().breadthFirst(new File("/resources/public/ahmed/BIOfid/BioFID_XMI_TI_16.04/"));
			for (File file : Streams.stream(files).filter(File::isFile).collect(Collectors.toList())) {
				try {
					JCas aJCas = JCasFactory.createJCas();
					CasIOUtils.load(java.nio.file.Files.newInputStream(file.toPath()), null, aJCas.getCas(), true);
					
					JCas bJCas = JCasFactory.createJCas();
					bJCas.setDocumentText(aJCas.getDocumentText());
					
					transferTokens(aJCas, bJCas);
					
					ArrayList<Sentence> aSentences = transferSentences(aJCas, bJCas);
					ArrayList<Sentence> bSentences = Lists.newArrayList(JCasUtil.select(bJCas, Sentence.class));
					
					SimplePipeline.runPipeline(bJCas, naiveTaggerEngine);
					
					Map<Sentence, Collection<Taxon>> aCoveredTaxa = JCasUtil.indexCovered(aJCas, Sentence.class, Taxon.class);
					
					Map<Token, Collection<Taxon>> aTokenCovering = JCasUtil.indexCovering(aJCas, Token.class, Taxon.class);
					Map<Token, Collection<Taxon>> bTokenCovering = JCasUtil.indexCovering(bJCas, Token.class, Taxon.class);
					
					Map<Sentence, Collection<Token>> aSentenceCovering = JCasUtil.indexCovered(aJCas, Sentence.class, Token.class);
					Map<Sentence, Collection<Token>> bSentenceCovering = JCasUtil.indexCovered(bJCas, Sentence.class, Token.class);
					
					for (int i = 0; i < aSentences.size(); i++) {
						if (aCoveredTaxa.getOrDefault(aSentences.get(i), new ArrayList<>()).isEmpty()) {
							continue;
						}
						
						ArrayList<Token> aTokens = Lists.newArrayList(aSentenceCovering.get(aSentences.get(i)));
						ArrayList<Token> bTokens = Lists.newArrayList(bSentenceCovering.get(bSentences.get(i)));
						
						for (int j = 0; j < aTokens.size(); j++) {
							Token aToken = aTokens.get(j);
							Token bToken = bTokens.get(j);
							
							boolean a = aTokenCovering.getOrDefault(aToken, null) != null;
							boolean b = bTokenCovering.getOrDefault(bToken, null) != null;
							
							boolean aBegin = true;
							if (a) {
								aBegin = Lists.newArrayList(aTokenCovering.get(aToken)).get(0).getBegin() == aToken.getBegin();
							}
							
							boolean bBegin = true;
							if (b) {
								bBegin = Lists.newArrayList(bTokenCovering.get(bToken)).get(0).getBegin() == bToken.getBegin();
							}
							
							if (a || b) {
								lastLineWasEmpty = false;
								conllWriter.println(String.format("%s\t%s\t%s", aToken.getCoveredText(), getIOB(aBegin, a ? "TAX" : "O"), getIOB(bBegin, b ? "TAX" : "O")));
							}
						}
						if (!lastLineWasEmpty)
							conllWriter.println();
						lastLineWasEmpty = true;
					}
				} catch (UIMAException | IOException e) {
					e.printStackTrace();
				}
				if (!lastLineWasEmpty)
					conllWriter.println();
				lastLineWasEmpty = true;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
	}
	
	@NotNull
	public static ArrayList<Token> transferTokens(JCas aJCas, JCas bJCas) {
		ArrayList<Token> aTokens = Lists.newArrayList(JCasUtil.select(aJCas, Token.class));
		for (Token aToken : aTokens) {
			Token bToken = new Token(bJCas, aToken.getBegin(), aToken.getEnd());
			bJCas.addFsToIndexes(bToken);
		}
		return aTokens;
	}
	
	@NotNull
	public static ArrayList<Sentence> transferSentences(JCas aJCas, JCas bJCas) {
		ArrayList<Sentence> aSentences = Lists.newArrayList(JCasUtil.select(aJCas, Sentence.class));
		for (Sentence aSentence : aSentences) {
			Sentence bSentence = new Sentence(bJCas, aSentence.getBegin(), aSentence.getEnd());
			bJCas.addFsToIndexes(bSentence);
		}
		return aSentences;
	}
	
	static String getIOB(boolean begin, String label) {
		return begin ? "B-" + label : "I-" + label;
	}
}
