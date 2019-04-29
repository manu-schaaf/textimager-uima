package org.hucompute.textimager.biofid;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.util.Combinations;
import org.apache.commons.math3.util.Pair;

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

public class NaiveSkipGramModel {
	
	private static Pattern nonTokenCharacterClass = Pattern.compile("[^\\p{Alpha}\\- ]+", Pattern.UNICODE_CHARACTER_CLASS);
	public HashSet<String> skipGramSet;
	private LinkedHashMap<String, String> taxonUriMap;
	private LinkedHashMap<String, String> taxonLookup;
	
	public NaiveSkipGramModel(String sourceLocation, Boolean pUseLowercase, String language, double pMinLength) throws IOException {
		System.out.printf("%s: Loading taxa..\n", this.getClass().getName());
		long startTime = System.currentTimeMillis();
		AtomicInteger duplicateKeys = new AtomicInteger(0);
		
		// Taxon -> URI
		taxonUriMap = NaiveSkipGramModel.loadTaxaMap(sourceLocation, pUseLowercase, language);
		
		// Taxon -> [Skip-Grams]
		HashMap<String, List<String>> skipGramMap = taxonUriMap.keySet().stream()
				.collect(Collectors.toMap(
						Function.identity(),
						NaiveSkipGramModel::getSkipGramList,
						(u, v) -> {
							duplicateKeys.incrementAndGet();
							return v;
						},
						HashMap::new));
		if (duplicateKeys.get() > 0)
			System.err.printf("%s: Found %d duplicate taxa!\n", this.getClass().getName(), duplicateKeys.get());
		duplicateKeys.set(0);
		
		// Skip-Gram -> Taxon
		taxonLookup = skipGramMap.entrySet().stream()
				.flatMap(entry -> entry.getValue().stream().map(val -> new Pair<>(entry.getKey(), val)))
				.collect(Collectors.toMap(
						Pair::getSecond,
						Pair::getFirst,
						(u, v) -> { // Drop duplicate skip-grams to ensure bijective skip-gram <-> taxon mapping.
							duplicateKeys.incrementAndGet();
							return null;
						},
						LinkedHashMap::new));
		System.err.printf("%s: Ignoring %d duplicate skip-grams!\n", this.getClass().getName(), duplicateKeys.get());
		
		// Ensure actual taxa are contained in taxonLookup
		taxonUriMap.keySet().forEach(tax -> taxonLookup.put(tax, tax));
		
		// {Skip-Gram}
		skipGramSet = taxonLookup.keySet().stream()
				.filter(s -> !Strings.isNullOrEmpty(s))
				.filter(s -> s.length() >= pMinLength)
				.collect(Collectors.toCollection(HashSet::new));
		
		System.out.printf("%s: Finished loading %d skip-grams from %d taxa in %d ms.\n",
				this.getClass().getName(), skipGramSet.size(), taxonUriMap.size(), System.currentTimeMillis() - startTime);
	}
	
	/**
	 * Find this Skip-Grams taxon return its respective URI.
	 *
	 * @param skipGram the target Skip-Gram
	 * @return taxonUriMap.get(taxonLookup.get ( skipGram))
	 */
	public String getUriFromSkipGram(String skipGram) {
		return taxonUriMap.get(taxonLookup.get(skipGram));
	}
	
	/**
	 * Load taxa from UTF-8 file, one taxon per line.
	 *
	 * @return ArrayList of taxa.
	 * @throws IOException if file is not found or an error occurs.
	 */
	private static LinkedHashMap<String, String> loadTaxaMap(String sourceLocation, Boolean pUseLowercase, String language) throws IOException {
		try (BufferedReader bufferedReader = Files.newReader(new File(sourceLocation), StandardCharsets.UTF_8)) {
			return bufferedReader.lines()
					.filter(s -> !Strings.isNullOrEmpty(s))
					.map(pUseLowercase ? s -> s.toLowerCase(Locale.forLanguageTag(language)) : Function.identity())
					.collect(Collectors.toMap(
							s -> nonTokenCharacterClass.matcher(s.split("\t", 2)[0]).replaceAll("").trim(),
							s -> s.split("\t", 2)[1],
							(u, v) -> v,
							LinkedHashMap::new)
					);
		}
	}
	
	private static List<String> getSkipGramList(String pString) {
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
	private static Stream<String> getSkipGrams(String pString) {
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
