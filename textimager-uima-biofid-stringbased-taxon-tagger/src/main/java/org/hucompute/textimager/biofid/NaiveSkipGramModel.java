package org.hucompute.textimager.biofid;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
import opennlp.tools.postag.*;
import opennlp.tools.util.CollectionObjectStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;
import org.apache.commons.math3.util.Combinations;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class NaiveSkipGramModel {
	
	public static POSModel buildModel(String modelLocation, String sourceLocation, String language, boolean useLowercase) throws IOException {
		return trainModel(loadTaxaMap(sourceLocation), modelLocation, language, useLowercase);
	}
	
	/**
	 * Load taxa from UTF-8 file, one taxon per line.
	 *
	 * @return ArrayList of taxa.
	 * @throws IOException if file is not found or an error occurs.
	 */
	public static Map<String, String> loadTaxaMap(String sourceLocation) throws IOException {
		System.out.println("Loading taxa..");
		try (BufferedReader bufferedReader = Files.newReader(new File(sourceLocation), StandardCharsets.UTF_8)) {
			return bufferedReader.lines()
					.map(String::trim)
					.filter(s -> !Strings.isNullOrEmpty(s))
					.collect(Collectors.toMap(s -> s.split("\t", 2)[1], s -> s.split("\t", 2)[0]));
		}
	}
	
	/**
	 * Train a simple sequence-labeling model from the taxa skip-grams.
	 *
	 * @param taxaMap
	 * @param useLowercase
	 * @return
	 * @throws IOException
	 */
	public static POSModel trainModel(Map<String, String> taxaMap, String modelLocation, String language, boolean useLowercase) throws IOException {
		System.out.println("Building model..");
		
		Stream<String> taxaStream = taxaMap.values().stream()
				.map(useLowercase ? s -> s.toLowerCase(Locale.forLanguageTag(language)) : Function.identity())
				.flatMap(NaiveSkipGramModel::getSkipGrams);
		
		TrainingParameters parameters = TrainingParameters.defaultParams();
		parameters.put(TrainingParameters.ITERATIONS_PARAM, "100");
		
		List<String> taxaList = taxaStream.collect(Collectors.toList());
		ObjectStream<POSSample> sampleStream = new WordTagSampleStream(new CollectionObjectStream<>(taxaList));
		
		System.out.println("Training model..");
		POSModel model = POSTaggerME.train(language, sampleStream, parameters, new POSTaggerFactory());
		
		try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(modelLocation))) {
			System.out.println("Writing model..");
			model.serialize(modelOut);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return model;
	}
	
	public static POSModel loadModel(String modelLocation) throws IOException {
		System.out.println("Loading model..");
		try (InputStream modelIn = new FileInputStream(modelLocation)) {
			return new POSModel(modelIn);
		}
	}
	
	public List<String> getSkipGramsAsList(String pString) {
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
		ImmutableList<String> split = ImmutableList.copyOf(pString.split("[\\s\n\\-]+"));
		if (split.size() < 2) {
			return Stream.of(pString + "_TAX");
		} else {
			return Streams.stream(Iterators.concat(
					Iterators.singletonIterator(IntStream.range(0, split.size()).toArray()),    // the string itself
					new Combinations(split.size(), split.size() - 1).iterator()))               // the combinations
					.parallel()
					.map(arr -> {
						ArrayList<String> strings = new ArrayList<>();
						for (int i : arr) {
							strings.add(String.format("%s_TAX", split.get(i)));
						}
						return String.join(" ", strings);
					});
		}
	}
}
