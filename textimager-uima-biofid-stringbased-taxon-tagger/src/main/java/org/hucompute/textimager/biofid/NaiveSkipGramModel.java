package org.hucompute.textimager.biofid;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.util.Combinations;
import org.apache.commons.math3.util.Pair;
import org.apache.uima.util.UriUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URI;
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
	private LinkedHashSet<String> skipGramSet;
	private LinkedHashMap<String, HashSet<URI>> taxonUriMap;
	private LinkedHashMap<String, String> skipGramTaxonLookup;
	private HashMap<String, List<String>> taxonSkipGramMap;
	private static boolean getAllSkips;
	private static boolean splitHyphen;
	
	/**
	 * Create 1-skip-n-grams from each taxon in a file from a given list of files.
	 * Constructor overload for default language="de" and bAllSkips=false.
	 *
	 * @param aSourceLocations An array of UTF-8 file locations containing a list of one taxon and any number of URIs (comma
	 *                         or space separated) per line.
	 * @param bUseLowercase    If true, use lower cased skip-grams.
	 * @param dMinLength       The minimum skip-gram length. All skip-grams (and taxa) with a length lower than this will be
	 *                         omitted.
	 * @throws IOException
	 */
	public NaiveSkipGramModel(String[] aSourceLocations, Boolean bUseLowercase, double dMinLength) throws IOException {
		this(aSourceLocations, bUseLowercase, "de", dMinLength, false, true);
	}
	
	/**
	 * Create 1-skip-n-grams from each taxon in a file from a given list of files.
	 *
	 * @param aSourceLocations An array of UTF-8 file locations containing a list of one taxon and any number of URIs (comma
	 *                         or space separated) per line.
	 * @param bUseLowercase    If true, use lower cased skip-grams.
	 * @param sLanguage        The language to be used as locale for lower casing.
	 * @param dMinLength       The minimum skip-gram length. All skip-grams (and taxa) with a length lower than this will be
	 *                         omitted.
	 * @param bAllSkips        If true, get all m-skip-n-grams of length n > 2.
	 * @param bSplitHyphen     If true, taxon tokens will be split at hyphens.
	 * @throws IOException
	 */
	public NaiveSkipGramModel(String[] aSourceLocations, Boolean bUseLowercase, String sLanguage, double dMinLength, boolean bAllSkips, boolean bSplitHyphen) throws IOException {
		getAllSkips = bAllSkips;
		splitHyphen = bSplitHyphen;
		System.out.printf("%s: Loading taxa from %d files..\n", this.getClass().getSimpleName(), aSourceLocations.length);
		long startTime = System.currentTimeMillis();
		AtomicInteger duplicateKeys = new AtomicInteger(0);
		
		// Map: Taxon -> {URI}
		taxonUriMap = new LinkedHashMap<>();
		for (String sourceLocation : aSourceLocations) {
			NaiveSkipGramModel.loadTaxaMap(sourceLocation, bUseLowercase, sLanguage).forEach((taxon, uri) ->
					taxonUriMap.merge(taxon, uri, (uUri, vUri) -> {
						duplicateKeys.incrementAndGet();
						return new HashSet<>(SetUtils.union(uUri, vUri));
					}));
		}
		System.out.printf("%s: Loaded %d taxa from %d files.\n", this.getClass().getSimpleName(), taxonUriMap.size(), aSourceLocations.length);
		if (duplicateKeys.get() > 0)
			System.err.printf("%s: Merged %d duplicate taxa!\n", this.getClass().getSimpleName(), duplicateKeys.get());
		duplicateKeys.set(0);
		
		// Map: Taxon -> [Skip-Grams]
		taxonSkipGramMap = taxonUriMap.keySet().stream()
				.collect(Collectors.toMap(
						Function.identity(),
						NaiveSkipGramModel::getSkipGramsAsList,
						(u, v) -> v,
						HashMap::new));
		
		// Map: Skip-Gram -> Taxon
		skipGramTaxonLookup = taxonSkipGramMap.entrySet()
				.stream()
				.flatMap(entry -> entry.getValue().stream().map(val -> new Pair<>(entry.getKey(), val)))
				.collect(Collectors.toMap(
						Pair::getSecond,    // the skip-gram
						Pair::getFirst,     // the corresponding taxon
						(u, v) -> {
							// Drop duplicate skip-grams to ensure bijective skip-gram <-> taxon mapping.
							duplicateKeys.incrementAndGet();
							return null;
						},
						LinkedHashMap::new));
		System.err.printf("%s: Ignoring %d duplicate skip-grams!\n", this.getClass().getSimpleName(), duplicateKeys.get());
		
		// Ensure actual taxa are contained in skipGramTaxonLookup
		taxonUriMap.keySet().forEach(tax -> skipGramTaxonLookup.put(tax, tax));
		
		// Set: {Skip-Gram}
		skipGramSet = skipGramTaxonLookup.keySet().stream()
				.filter(s -> !Strings.isNullOrEmpty(s))
				.filter(s -> s.length() >= dMinLength)
				.sorted(Comparator.comparingInt(String::length).reversed())
				.collect(Collectors.toCollection(LinkedHashSet::new));
		
		System.out.printf("%s: Finished loading %d skip-grams from %d taxa in %dms.\n",
				this.getClass().getSimpleName(), skipGramSet.size(), taxonUriMap.size(), System.currentTimeMillis() - startTime);
	}
	
	/**
	 * Find this Skip-Grams taxon return its respective URI.
	 *
	 * @param skipGram the target Skip-Gram
	 * @return taxonUriMap.get(skipGramTaxonLookup.get ( skipGram))
	 */
	public Set<URI> getUriFromSkipGram(String skipGram) {
		return taxonUriMap.get(skipGramTaxonLookup.get(skipGram));
	}
	
	/**
	 * Get all skip-grams of the given taxon.
	 *
	 * @param taxon The taxon to get the skip-grams from
	 * @return A list of skip-grams.
	 */
	public List<String> getSkipGramsFromTaxon(String taxon) {
		return taxonSkipGramMap.get(taxon);
	}
	
	/**
	 * Load taxa from UTF-8 file, one taxon per line.
	 *
	 * @return ArrayList of taxa.
	 * @throws IOException if file is not found or an error occurs.
	 */
	private static LinkedHashMap<String, HashSet<URI>> loadTaxaMap(String sourceLocation, Boolean pUseLowercase, String language) throws IOException {
		try (BufferedReader bufferedReader = Files.newReader(new File(sourceLocation), StandardCharsets.UTF_8)) {
			return bufferedReader.lines()
					.filter(s -> !Strings.isNullOrEmpty(s))
					.map(pUseLowercase ? s -> s.toLowerCase(Locale.forLanguageTag(language)) : Function.identity())
					.collect(Collectors.toMap(
							s -> nonTokenCharacterClass.matcher(s.split("\t", 2)[0]).replaceAll("").trim(),
							s -> Arrays.stream(s.split("\t", 2)[1].split("[ ,]")).map(UriUtils::create).collect(Collectors.toCollection(HashSet::new)),
							(u, v) -> new HashSet<>(SetUtils.union(u, v)),
							LinkedHashMap::new)
					);
		}
	}
	
	/**
	 * Calls {@link this.getSkipGrams getSkipGrams(String)} and collects the result in a list.
	 *
	 * @param pString the target String.
	 * @return a List of Strings.
	 */
	private static List<String> getSkipGramsAsList(String pString) {
		return getSkipGrams(pString).collect(Collectors.toList());
	}
	
	/**
	 * Get a List of 1-skip-n-grams for the given string.
	 * The string itself is always the first element of the list.
	 * The string is split by whitespaces and all n over n-1 combinations are computed and added to the list.
	 * If there is only a single word in the given string, a singleton list with that word is returned.
	 *
	 * @param pString the target String.
	 * @return a Stream of Strings.
	 */
	private static Stream<String> getSkipGrams(String pString) {
		ImmutableList<String> words;
		if (splitHyphen) {
			words = ImmutableList.copyOf(pString.split("[\\s\n\\-]+"));
		} else {
			words = ImmutableList.copyOf(pString.split("[\\s\n]+"));
		}
		if (words.size() < 3) {
			return Stream.of(pString);
		} else {
			IntStream combinationRange;
			if (getAllSkips && words.size() > 3) {
				combinationRange = IntStream.range(2, words.size());
			} else {
				combinationRange = IntStream.of(words.size() - 1);
			}
			
			Stream<Integer[]> combinationsArraysStream = combinationRange
					.boxed()
					.map(i -> new Combinations(words.size(), i).iterator())
					.flatMap(Streams::stream)
					.map(ArrayUtils::toObject);
			
			
			return combinationsArraysStream
					.parallel()
					.map(combination -> {
						ArrayList<String> strings = new ArrayList<>();
						for (int index : combination) {
							strings.add(words.get(index));
						}
						return String.join(" ", strings);
					});
		}
	}
	
	/**
	 * Stream all previously created skip-grams sorted .
	 *
	 * @return A stream of strings by calling: this.skipGramSet.stream().
	 */
	Stream<String> stream() {
		return this.skipGramSet.stream();
	}
}
