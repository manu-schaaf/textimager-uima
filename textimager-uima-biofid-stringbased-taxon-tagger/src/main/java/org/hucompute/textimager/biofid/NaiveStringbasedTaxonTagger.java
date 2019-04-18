package org.hucompute.textimager.biofid;

import com.google.common.base.Strings;
import com.google.common.collect.*;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.resources.MappingProvider;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.SegmenterBase;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.util.Combinations;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.jetbrains.annotations.NotNull;
import org.texttechnologylab.annotation.type.Taxon;

import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
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
	protected String language;

	/**
	 * Location from which the taxon data is read.
	 */
	public static final String PARAM_SOURCE_LOCATION = ComponentParameters.PARAM_SOURCE_LOCATION;
	@ConfigurationParameter(name = PARAM_SOURCE_LOCATION, mandatory = false)
	protected String sourceLocation;

	/**
	 * Boolean, if true use lower case.
	 */
	public static final String PARAM_USE_LOWERCASE = "pUseLowercase";
	@ConfigurationParameter(name = PARAM_USE_LOWERCASE, mandatory = false, defaultValue = "false")
	protected Boolean pUseLowercase;

	private MappingProvider namedEntityMappingProvider;
	private Map<String, String> plainTaxaMap = null;
	private HashSet<String> skipGramSet = null;

	final AtomicInteger atomicInteger = new AtomicInteger(0);

	Pattern letterAndSpaceClass = Pattern.compile("[^\\p{Alpha}\\-\\s.,;!?]+", Pattern.UNICODE_CHARACTER_CLASS);


	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		namedEntityMappingProvider = new MappingProvider();
		namedEntityMappingProvider.setDefault(MappingProvider.LOCATION, "classpath:/org/hucompute/textimager/biofid/lib/ner-default.map");
		namedEntityMappingProvider.setDefault(MappingProvider.BASE_TYPE, NamedEntity.class.getName());
		namedEntityMappingProvider.setOverride(MappingProvider.LANGUAGE, "de");

		try {
			if (plainTaxaMap != null || skipGramSet != null)
				return;

			plainTaxaMap = NaiveSkipGramModel.loadTaxaMap(sourceLocation);
			skipGramSet = plainTaxaMap.values().stream()
					.map(pUseLowercase ? s -> s.toLowerCase(Locale.forLanguageTag(language)) : Function.identity())
					.flatMap(NaiveStringbasedTaxonTagger::getSkipGrams)
					.filter(s -> !Strings.isNullOrEmpty(s))
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

//		System.out.println("Tagging");

		atomicInteger.set(0);
		String docText = pUseLowercase ? aJCas.getDocumentText().toLowerCase(Locale.forLanguageTag(language)) : aJCas.getDocumentText();
		final String finalDocText = letterAndSpaceClass.matcher(docText).replaceAll("");
		skipGramSet.stream()
				.parallel()
				.forEach(skipGram -> findTaxa(aJCas, finalDocText, skipGram));

		System.out.printf("Tagged %d taxa.\n", atomicInteger.intValue());
	}


//	@Override
//	protected void process(JCas aJCas, String text, int zoneBegin) throws AnalysisEngineProcessException {
//			POSModel model;
//			if (pTrainModel) {
//
//				model = NaiveSkipGramModel.trainModel(plainTaxaMap, modelLocation, language, pUseLowercase);
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
	private void findTaxa(JCas aJCas, String docText, String skipGram) {
		int index = 0;
		do {
			index = docText.indexOf(skipGram, index);
			if (index > -1) {
				Taxon taxon = new Taxon(aJCas, index, index + skipGram.length());
				aJCas.addFsToIndexes(taxon);
				atomicInteger.incrementAndGet();

				index = index + skipGram.length();
				if (index >= docText.length())
					break;
			}
		} while (index > -1);
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
					.map(arr -> {
						Maps.newHashMap(split).keySet().retainAll(Sets.intersection(ImmutableSet.copyOf(arr), split.keySet()));
						return String.join(" ", split.values());
					});
//					.map(arr -> { // FIXME: Combinations Array as List key subset function
//						ArrayList<String> strings = new ArrayList<>();
//						for (int i : arr) {
//							strings.add(split.get(i));
//						}
//						return String.join(" ", strings);
//					});
		}
	}

}
