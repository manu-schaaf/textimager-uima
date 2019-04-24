package org.hucompute.textimager.biofid;

import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.google.common.io.Files;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProvider;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.SegmenterBase;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.util.Combinations;
import org.apache.commons.math3.util.Pair;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.impl.FeatureImpl;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.jetbrains.annotations.NotNull;
import org.texttechnologylab.annotation.type.Taxon;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
	protected String sourceLocation;
	
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
	private HashSet<String> skipGramSet;
	private LinkedHashMap<String, String> dataTaxonMap;
	private LinkedHashMap<String, String> taxonLookup;
	
	final AtomicInteger atomicInteger = new AtomicInteger(0);
	
	Pattern nonTokenCharacterClass = Pattern.compile("[^\\p{Alpha}\\-\\s]+", Pattern.UNICODE_CHARACTER_CLASS);
	
	
	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		namedEntityMappingProvider = new MappingProvider();
		namedEntityMappingProvider.setDefault(MappingProvider.LOCATION, "classpath:/org/hucompute/textimager/biofid/lib/ner-default.map");
		namedEntityMappingProvider.setDefault(MappingProvider.BASE_TYPE, NamedEntity.class.getName());
		namedEntityMappingProvider.setOverride(MappingProvider.LANGUAGE, "de");
		
		try {
			dataTaxonMap = NaiveStringbasedTaxonTagger.loadTaxaMap(sourceLocation);
			HashMap<String, List<String>> skipGramMap = dataTaxonMap.keySet().stream()
					.map(cs -> nonTokenCharacterClass.matcher(cs).replaceAll(""))
					.map(String::trim)
					.collect(Collectors.toMap(
							Function.identity(),
							NaiveStringbasedTaxonTagger::getSkipGramList,
							(u, v) -> v,
							HashMap::new));
			taxonLookup = skipGramMap.entrySet().stream()
					.flatMap(entry -> entry.getValue().stream().map(val -> new Pair<>(entry.getKey(), val)))
					.collect(Collectors.toMap(
							Pair::getSecond,
							Pair::getFirst,
							(u, v) -> null,
							LinkedHashMap::new));
			skipGramSet = skipGramMap.values().stream()
					.flatMap(List::stream)
					.filter(s -> !Strings.isNullOrEmpty(s))
					.filter(s -> s.length() >= pMinLength)
					.collect(Collectors.toCollection(HashSet::new));
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
		
		skipGramSet.stream()
				.parallel()
				.forEach(skipGram -> findTaxa(aJCas, tokens, tokenStrings, skipGram));
		
		System.out.printf("Tagged %d taxa.\n", atomicInteger.intValue());
	}


//	@Override
//	protected void process(JCas aJCas, String text, int zoneBegin) throws AnalysisEngineProcessException {
//			POSModel model;
//			if (pTrainModel) {
//
//				model = NaiveSkipGramModel.trainModel(dataTaxonMap, modelLocation, language, pUseLowercase);
//			} else {
//				model = NaiveSkipGramModel.loadModel(modelLocation);
//			}
//
//			Map<Sentence, Collection<Token>> sentenceTokenIndex = JCasUtil.indexCovered(aJCas, Sentence.class, Token.class);
//			POSTaggerME tagger = new POSTaggerME(model);
//
//			System.out.println("Tagging");
//			List<String[]> sentences = JCasUtil.select(aJCas, Sentence.class).stream()
//					.map(sentenceTokenIndex::get)
//					.map(c -> c.stream()
//							.map(Token::getCoveredText)
//							.map(pUseLowercase ? s -> s.toLowerCase(Locale.forLanguageTag(language)) : Function.identity())
//							.toArray(String[]::new))
//					.collect(Collectors.toList());
//			for (String[] sentence : sentences) {
//				Sequence[] sequences = tagger.topKSequences(sentence);
//				if (Arrays.stream(sequences).flatMap(s -> s.getOutcomes().stream()).anyMatch(s -> s.equals("tax"))) {
//					for (Sequence sequence : sequences) {
//						List<String> outcomes = sequence.getOutcomes();
//						System.out.println(IntStream.range(0, outcomes.size())
//								.mapToObj(i -> Pair.create(sentence[i], outcomes.get(i)))
//								.collect(Collectors.toList()));
//					}
//				}
//			}
//		System.out.println("Finished.");
//	}
	
	@NotNull
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
						taxon.setIdentifier(dataTaxonMap.get(taxonLookup.get(skipGram)));
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
	
	/**
	 * Load taxa from UTF-8 file, one taxon per line.
	 *
	 * @return ArrayList of taxa.
	 * @throws IOException if file is not found or an error occurs.
	 */
	public static LinkedHashMap<String, String> loadTaxaMap(String sourceLocation) throws IOException {
		System.out.println("Loading taxa..");
		try (BufferedReader bufferedReader = Files.newReader(new File(sourceLocation), StandardCharsets.UTF_8)) {
			return bufferedReader.lines()
					.map(String::trim)
					.filter(s -> !Strings.isNullOrEmpty(s))
					.map(pUseLowercase ? s -> s.toLowerCase(Locale.forLanguageTag(language)) : Function.identity())
					.collect(Collectors.toMap(
							s -> s.split("\t", 2)[0],
							s -> s.split("\t", 2)[1],
							(u, v) -> v,
							LinkedHashMap::new)
					);
		}
	}
	
	public static List<String> getSkipGramList(String pString) {
		return getSkipGrams(pString).collect(Collectors.toList());
	}
	
	/**
	 * Get a List of 1-skip-n-grams for the given string.
	 * The string itself is always the first element of the list.
	 * The string is split by whitespaces and all n over n-1 combinations are computed and added to the list.
	 * If there is only a single word in the given string, a singleton list with that word is returned.
	 *
	 * @param pString the target String.
	 * @return a List of Strings.
	 */
	public static Stream<String> getSkipGrams(String pString) {
		ImmutableList<String> words = ImmutableList.copyOf(pString.split("[\\s\n\\-]+"));
		if (words.size() < 3) {
			return Stream.of(pString);
		} else {
			Map<Integer, String> split = IntStream.range(0, words.size())
					.boxed()
					.collect(Collectors.toMap(Function.identity(), words::get));
			
			return Streams.stream(Iterators.concat(
					Iterators.singletonIterator(IntStream.range(0, split.size()).toArray()),    // the string itself
					new Combinations(split.size(), split.size() - 1).iterator()))               // the combinations
					.parallel()
					.map(ArrayUtils::toObject)
//					.map(arr -> {
//						Maps.newHashMap(split).keySet().retainAll(Sets.intersection(ImmutableSet.copyOf(arr), split.keySet()));
//						return String.join(" ", split.values());
//					}); // FIXME: Combinations Array as List key subset function
					.map(arr -> {
						ArrayList<String> strings = new ArrayList<>();
						for (int i : arr) {
							strings.add(split.get(i));
						}
						return String.join(" ", strings);
					});
		}
	}
	
}
