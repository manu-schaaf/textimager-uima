package org.hucompute.textimager.biofid;

import com.google.common.base.Strings;
import com.google.common.collect.*;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProvider;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.SegmenterBase;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.commons.math3.util.Pair;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.texttechnologylab.annotation.type.Taxon;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NaiveStringbasedTaxonTagger extends SegmenterBase {
	
	/**
	 * Text and model language. Default is "de".
	 */
	public static final String PARAM_LANGUAGE = ComponentParameters.PARAM_LANGUAGE;
	@ConfigurationParameter(name = PARAM_LANGUAGE, mandatory = false, defaultValue = "de")
	protected static String language;
	
	/**
	 * Location from which the taxon data is read.
	 */
	public static final String PARAM_SOURCE_LOCATION = ComponentParameters.PARAM_SOURCE_LOCATION;
	@ConfigurationParameter(name = PARAM_SOURCE_LOCATION, mandatory = false)
	protected String[] sourceLocations;
	
	/**
	 * Minimum skip-gram string length
	 */
	public static final String PARAM_MIN_LENGTH = "pMinLength";
	@ConfigurationParameter(name = PARAM_MIN_LENGTH, mandatory = false, defaultValue = "5")
	protected Integer pMinLength;
	
	/**
	 * Boolean, if true use lower case.
	 */
	public static final String PARAM_USE_LOWERCASE = "pUseLowercase";
	@ConfigurationParameter(name = PARAM_USE_LOWERCASE, mandatory = false, defaultValue = "false")
	protected static Boolean pUseLowercase;
	
	private MappingProvider namedEntityMappingProvider;
	
	private final AtomicInteger atomicInteger = new AtomicInteger(0);
	private NaiveSkipGramModel naiveSkipGramModel;
	
	
	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		namedEntityMappingProvider = new MappingProvider();
		namedEntityMappingProvider.setDefault(MappingProvider.LOCATION, "classpath:/org/hucompute/textimager/biofid/lib/ner-default.map");
		namedEntityMappingProvider.setDefault(MappingProvider.BASE_TYPE, NamedEntity.class.getName());
		namedEntityMappingProvider.setOverride(MappingProvider.LANGUAGE, "de");
		
		try {
			naiveSkipGramModel = new NaiveSkipGramModel(sourceLocations, pUseLowercase, language, pMinLength);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		process(aJCas, aJCas.getDocumentText(), 0);
	}
	
	@Override
	protected void process(JCas aJCas, String text, int zoneBegin) throws AnalysisEngineProcessException {
		namedEntityMappingProvider.configure(aJCas.getCas());
		
		atomicInteger.set(0);
		ArrayList<Token> tokens = Lists.newArrayList(JCasUtil.select(aJCas, Token.class));
		ArrayList<String> tokenStrings = tokens.stream()
				.map(Token::getCoveredText)
				.map(pUseLowercase ? s -> s.toLowerCase(Locale.forLanguageTag(language)) : Function.identity())
				.collect(Collectors.toCollection(ArrayList::new));
		
		naiveSkipGramModel.skipGramSet.stream()
				.parallel()
				.forEach(skipGram -> findTaxa(aJCas, tokens, tokenStrings, skipGram));

//		System.out.printf("\rTagged %d taxa.", atomicInteger.intValue());
	}
	
	private void findTaxa(JCas aJCas, final ArrayList<Token> tokens, final ArrayList<String> tokenStrings, String skipGram) {
		int index;
		String[] skipGramSplit = skipGram.split(" ");
		List<String> subList = tokenStrings.subList(0, tokenStrings.size());
		int currOffset = 0;
		do {
			index = subList.indexOf(skipGramSplit[0]);
			if (index > -1) {
				boolean b = true;
				int j = 0;
				try {
					if (skipGramSplit.length > 1) {
						for (int i = 1; i < skipGramSplit.length; i++) {
							if (index + i >= subList.size())
								break;
							b &= subList.get(index + i).equals(skipGramSplit[i]);
							j = i;
						}
					}
					
					if (b) {
						Token fromToken = tokens.get(currOffset + index);
						Token toToken = tokens.get(currOffset + index + j);
						Taxon taxon = new Taxon(aJCas, fromToken.getBegin(), toToken.getEnd());
						taxon.setIdentifier(naiveSkipGramModel.getUriFromSkipGram(skipGram).stream().map(URI::toString).collect(Collectors.joining(",")));
						aJCas.addFsToIndexes(taxon);
						atomicInteger.incrementAndGet();
					}
					
					currOffset += index + j + 1;
					if (j >= subList.size() || currOffset >= tokenStrings.size())
						break;
					
					subList = tokenStrings.subList(currOffset, tokenStrings.size());
				} catch (IndexOutOfBoundsException e) {
					throw e;
				}
			}
		} while (index > -1);
	}
	
}
