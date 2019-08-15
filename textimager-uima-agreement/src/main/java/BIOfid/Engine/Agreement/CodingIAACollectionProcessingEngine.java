package BIOfid.Engine.Agreement;

import BIOfid.Utility.CountMap;
import BIOfid.Utility.IndexingMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.commons.lang3.StringUtils;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.dkpro.statistics.agreement.IAgreementMeasure;
import org.dkpro.statistics.agreement.ICategorySpecificAgreement;
import org.dkpro.statistics.agreement.coding.*;
import org.dkpro.statistics.agreement.distance.NominalDistanceFunction;
import org.texttechnologielab.annotation.type.Fingerprint;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.uima.fit.util.JCasUtil.indexCovering;

/**
 * Inter-annotator agreement engine using a {@link CodingAnnotationStudy CodingAnnotationStudy} and
 * {@link ICategorySpecificAgreement ICategorySpecificAgreement} measure.
 * <p/>
 * Creates one {@link CodingAnnotationStudy CodingAnnotationStudy} in total for which the given agreement measure is computed.U
 * <p/>
 *
 * @see CohenKappaAgreement
 * @see FleissKappaAgreement
 * @see KrippendorffAlphaAgreement
 * @see PercentageAgreement
 */
public class CodingIAACollectionProcessingEngine extends AbstractIAAEngine {
	TreeSet<String> categories = new TreeSet<>();
	Integer maxCasIndex = 0;
	HashMap<Integer, HashMap<String, HashMap<Integer, Set<String>>>> perCasStudies = new HashMap<>();
	HashMap<Integer, Integer> perCasTokenCount = new HashMap<>();
	LinkedHashSet<String> annotatorList = new LinkedHashSet<>();
	
	/**
	 * Parameter for the {@link SetSelectionStrategy SetSelectionStrategy} to use.<br>
	 * Default: {@link SetSelectionStrategy#MAX}.<br>
	 * Choices: <ul>
	 * <li>{@link SetSelectionStrategy#ALL}
	 * <li>{@link SetSelectionStrategy#MAX}
	 * </ul>
	 */
	public static final String PARAM_SET_SELECTION_STRATEGY = "pSetSelectionStrategy";
	@ConfigurationParameter(
			name = PARAM_SET_SELECTION_STRATEGY,
			defaultValue = "MAX",
			description = "Parameter for the SetSelectionStrategy to use."
	)
	String pSetSelectionStrategy;
	
	// Agreement measure choices
	/**
	 * Paramter string for {@link CodingIAACollectionProcessingEngine#CohenKappaAgreement}.
	 *
	 * @see CohenKappaAgreement
	 */
	public final static String CohenKappaAgreement = "CohenKappaAgreement";
	
	/**
	 * Paramter string for {@link CodingIAACollectionProcessingEngine#FleissKappaAgreement}.
	 *
	 * @see FleissKappaAgreement
	 */
	public final static String FleissKappaAgreement = "FleissKappaAgreement";
	
	/**
	 * Paramter string for {@link CodingIAACollectionProcessingEngine#PercentageAgreement}.
	 *
	 * @see PercentageAgreement
	 */
	public final static String PercentageAgreement = "PercentageAgreement";
	
	/**
	 * Paramter string for {@link CodingIAACollectionProcessingEngine#KrippendorffAlphaAgreement}.
	 *
	 * @see KrippendorffAlphaAgreement
	 */
	public final static String KrippendorffAlphaAgreement = "KrippendorffAlphaAgreement";
	
	/**
	 * Parameter for the agreement measure the to use.<br>
	 * Default: {@link CodingIAACollectionProcessingEngine#KrippendorffAlphaAgreement CodingInterAnnotatorAgreementEngine.KrippendorffAlphaAgreement}.<br>
	 * Choices:
	 * <ul>
	 * <li>{@link CodingIAACollectionProcessingEngine#KrippendorffAlphaAgreement CodingInterAnnotatorAgreementEngine.KrippendorffAlphaAgreement}
	 * <li>{@link CodingIAACollectionProcessingEngine#FleissKappaAgreement CodingInterAnnotatorAgreementEngine.FleissKappaAgreement}
	 * <li>{@link CodingIAACollectionProcessingEngine#CohenKappaAgreement CodingInterAnnotatorAgreementEngine.CohenKappaAgreement}
	 * <li>{@link CodingIAACollectionProcessingEngine#PercentageAgreement CodingInterAnnotatorAgreementEngine.PercentageAgreement}
	 * </ul>
	 * <p>
	 *
	 * @see CohenKappaAgreement
	 * @see FleissKappaAgreement
	 * @see KrippendorffAlphaAgreement
	 * @see PercentageAgreement
	 */
	public static final String PARAM_AGREEMENT_MEASURE = "pAgreementMeasure";
	@ConfigurationParameter(
			name = PARAM_AGREEMENT_MEASURE,
			defaultValue = KrippendorffAlphaAgreement,
			description = "Parameter for the agreement measure the to use."
	)
	String pAgreementMeasure;
	
	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {
		try {
			// Ensure document has SOFA string
			if (jCas.getDocumentText() == null || jCas.getDocumentText().isEmpty())
				return;
			
			// If PARAM_DISCARD_SINGLE_VIEW was set, ensure there are multiple views other than _InitialView
			long views = Streams.stream(jCas.getViewIterator())
					.map(view -> StringUtils.substringAfterLast(view.getViewName().trim(), "/"))
					.filter(StringUtils::isNotEmpty)
					.count();
			if (views < pMinViews)
				return;
			
			// Count all not sub-tokens
			int tokenCount = (int) JCasUtil.select(jCas, Token.class).stream()
					.filter(((Predicate<Token>)
							indexCovering(jCas, Token.class, Token.class).entrySet().stream()
									.filter(entry -> !entry.getValue().isEmpty())
									.map(Map.Entry::getKey)
									.collect(Collectors.toCollection(HashSet::new))::contains)
							.negate())
					.count();
			perCasTokenCount.put(maxCasIndex, tokenCount);
			
			// Iterate over all views
			HashMap<String, HashMap<Integer, Set<String>>> perViewAnnotationMap = new HashMap<>();
			jCas.getViewIterator().forEachRemaining(viewCas -> {
				// Split user id from view name and get annotator index for this id. Discards "_InitialView"
				String viewName = StringUtils.substringAfterLast(viewCas.getViewName().trim(), "/");
				// Check for empty view name and correct listing
				// If whitelisting (true), the name must be in the set; if blacklisting (false), it must not be in the set
				if (StringUtils.isEmpty(viewName) || !(pWhitelisting == listedAnnotators.contains(viewName)))
					return;
				annotatorList.add(viewName);
				
				// Get all fingerprinted annotations
				HashSet<TOP> fingerprinted = new HashSet<>(JCasUtil.select(viewCas, Fingerprint.class).stream()
						.map(Fingerprint::getReference)
						.collect(Collectors.toCollection(HashSet::new)));
				
				// Create an index for the token, that are not part of sub-token
				HashSet<Token> coveredTokens = indexCovering(viewCas, Token.class, Token.class).entrySet().stream()
						.filter(entry -> !entry.getValue().isEmpty())
						.map(Map.Entry::getKey)
						.collect(Collectors.toCollection(HashSet::new));
				
				// Create an index for the tokens
				IndexingMap<Token> tokenIndexingMap = new IndexingMap<>();
				JCasUtil.select(viewCas, Token.class).stream()
						.sequential()
						.filter(((Predicate<Token>) coveredTokens::contains).negate())
						.forEachOrdered(tokenIndexingMap::add);
				
				if (tokenIndexingMap.size() != tokenCount) {
					logger.error("The number of tokens in this view does not match with the number of tokens in the default view!");
					return;
				}
				
				// Create a map which holds all annotation sets over all covered tokens (by index)
				HashMap<Integer, Set<String>> currentViewAnnotationMap = new HashMap<>();
				perViewAnnotationMap.put(viewName, currentViewAnnotationMap);
				
				// Add all annotations of each given class over each token to
				for (Class<? extends Annotation> annotationClass : annotationClasses) {
					Map<Token, Collection<Annotation>> annotationCoveringTokenIndex = indexCovering(viewCas, Token.class, annotationClass);
					for (Token token : tokenIndexingMap.keySet()) {
						if (!coveredTokens.contains(token)) {
							Integer index = tokenIndexingMap.get(token);
							for (Annotation annotation : annotationCoveringTokenIndex.get(token)) {
								// Check pFilterFingerprinted -> fingerprinted::contains
								if (!pFilterFingerprinted || fingerprinted.contains(annotation)) {
									String category = getCatgoryName(annotation);
									
									Set<String> categorySet = currentViewAnnotationMap.getOrDefault(index, new HashSet<>());
									categorySet.add(category);
									currentViewAnnotationMap.put(index, categorySet);
								}
							}
						}
					}
				}
			});
			
			// After all views have been processed, add the perViewAnnotationMap to perCasStudies
			perCasStudies.put(maxCasIndex, perViewAnnotationMap); // FIXME: Refactor this with the token count into an object?
			
			// If pAggregationMethod is SEPARATE or BOTH, compute agreement for this CAS only
			switch (pMultiCasHandling) {
				case SEPARATE:
				case BOTH:
					aggregateSeparate(jCas, maxCasIndex, perViewAnnotationMap);
					break;
			}
			maxCasIndex += 1;
		} catch (CASException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void collectionProcessComplete() throws AnalysisEngineProcessException {
		super.collectionProcessComplete();
		
		// Create a global study from all items
		if (annotatorList.size() > 1) {
			switch (pMultiCasHandling) {
				case SEPARATE:
					return;
				case BOTH:
				case COMBINED:
				default:
					aggregateCollect();
					break;
			}
		}
	}
	
	private void aggregateSeparate(JCas jCas, Integer casIndex, HashMap<String, HashMap<Integer, Set<String>>> perCasStudy) {
		CountMap<String> globalCategoryCount = new CountMap<>();
		HashMap<String, CountMap<String>> annotatorCategoryCount = new HashMap<>();
		// Initialize a CountMap for each annotator
		for (String annotator : annotatorList) {
			annotatorCategoryCount.put(annotator, new CountMap<>());
		}
		
		SetCodingAnnotationStudy codingAnnotationStudy = new SetCodingAnnotationStudy(annotatorList.size(), SetSelectionStrategy.valueOf(pSetSelectionStrategy));
		CountMap<String> globalCategoryOverlap = new CountMap<>();
		for (int tokenIndex = 0; tokenIndex < perCasTokenCount.get(casIndex); tokenIndex++) {
			// Holds the sets of annotations for this token per annotator
			ArrayList<Set<String>> perTokenAnnotations = new ArrayList<>();
			CountMap<String> categoryOverlap = new CountMap<>();
			
			// Bool to check if any annotator has an annotation over the current token
			boolean any = false;
			
			// Get all annotations over the current token by index
			for (String annotatorName : annotatorList) {
				Set<String> category = perCasStudy
						.getOrDefault(annotatorName, new HashMap<>())
						.getOrDefault(tokenIndex, ImmutableSet.of(""));
				perTokenAnnotations.add(category);
				
				if (!category.contains("")) {
					any = true;
					categories.addAll(category);
					
					// Statistics
					globalCategoryCount.incAll(category);
					annotatorCategoryCount.get(annotatorName).incAll(category);
					categoryOverlap.incAll(category);
				}
			}
			if (any) {
				// Add the annotations to the study
				codingAnnotationStudy.addItemSetsAsArray(perTokenAnnotations.toArray(new Set[0]));
				
				// Increase the overlap count for each category with more than one vote
				categoryOverlap.forEach((o, integer) -> {
					if (integer > 1) globalCategoryOverlap.inc(o);
				});
			}
		}
		
		
		// Compute agreement
		IAgreementMeasure agreement = calcualteAgreement(codingAnnotationStudy, globalCategoryCount, annotatorCategoryCount, globalCategoryOverlap);
		
		// Print the agreement for all categories
		System.out.printf("\n%s - %s - %s\n" +
						"Inter-annotator agreement for %d annotators: %s\n" +
						"Category\tCount\tAgreement\n" +
						"Overall\t%d\t%f\n",
				pAgreementMeasure, pSetSelectionStrategy, DocumentMetaData.get(jCas).getDocumentTitle(),
				annotatorList.size(), annotatorList.toString(),
				codingAnnotationStudy.getUnitCount(), agreement.calculateAgreement());
		printStudyResultsAndStatistics((ICategorySpecificAgreement) agreement, globalCategoryCount, annotatorCategoryCount, categories, annotatorList);
		
		if (pPrintStatistics) {
			printCategoryOverlap(globalCategoryOverlap);
		}
	}
	
	private void aggregateCollect() {
		CountMap<String> globalCategoryCount = new CountMap<>();
		HashMap<String, CountMap<String>> annotatorCategoryCount = new HashMap<>();
		// Initialize a CountMap for each annotator
		for (String annotator : annotatorList) {
			annotatorCategoryCount.put(annotator, new CountMap<>());
		}
		
		SetCodingAnnotationStudy codingAnnotationStudy = new SetCodingAnnotationStudy(annotatorList.size(), SetSelectionStrategy.valueOf(pSetSelectionStrategy));
		CountMap<String> globalCategoryOverlap = new CountMap<>();
		for (int casIndex = 0; casIndex < maxCasIndex; casIndex++) {
			if (perCasStudies.containsKey(casIndex) && perCasTokenCount.containsKey(casIndex)) { // FIXME: remove this check, as it is unnecessary
				HashMap<String, HashMap<Integer, Set<String>>> perCasStudy = perCasStudies.get(casIndex);
				for (int tokenIndex = 0; tokenIndex < perCasTokenCount.get(casIndex); tokenIndex++) {
					// Holds the sets of annotations for this token per annotator
					ArrayList<Set<String>> perTokenAnnotations = new ArrayList<>();
					CountMap<String> categoryOverlap = new CountMap<>();
					
					// Bool to check if any annotator has an annotation over the current token
					boolean any = false;
					
					// Get all annotations over the current token by index
					for (String annotatorName : annotatorList) {
						Set<String> category = perCasStudy
								.getOrDefault(annotatorName, new HashMap<>())
								.getOrDefault(tokenIndex, ImmutableSet.of(""));
						perTokenAnnotations.add(category);
						
						if (!category.contains("")) {
							any = true;
							categories.addAll(category);
							
							// Statistics
							globalCategoryCount.incAll(category);
							annotatorCategoryCount.get(annotatorName).incAll(category);
							categoryOverlap.incAll(category);
						}
					}
					if (any) {
						// Add the annotations to the study
						codingAnnotationStudy.addItemSetsAsArray(perTokenAnnotations.toArray(new Set[0]));
						
						// Increase the overlap count for each category with more than one vote
						categoryOverlap.forEach((o, integer) -> {
							if (integer > 1) globalCategoryOverlap.inc(o);
						});
					}
				}
			}
		}
		
		// Compute agreement
		IAgreementMeasure agreement = calcualteAgreement(codingAnnotationStudy, globalCategoryCount, annotatorCategoryCount, globalCategoryOverlap);
		
		// Print the agreement for all categories
		System.out.printf("\n" + pAgreementMeasure + " - " + pSetSelectionStrategy + "\nInter-annotator agreement for %d annotators: %s\n" +
						"Category\tCount\tAgreement\n" +
						"Overall\t%d\t%f\n",
				annotatorList.size(), annotatorList.toString(), codingAnnotationStudy.getUnitCount(), agreement.calculateAgreement());
		printStudyResultsAndStatistics((ICategorySpecificAgreement) agreement, globalCategoryCount, annotatorCategoryCount, categories, annotatorList);
		
		if (pPrintStatistics) {
			printCategoryOverlap(globalCategoryOverlap);
		}
	}
	
	/**
	 * Calculate the agreement for the given study and print the resulting statistics.
	 *
	 * @param codingAnnotationStudy
	 * @param globalCategoryCount
	 * @param annotatorCategoryCount
	 * @param globalCategoryOverlap
	 */
	IAgreementMeasure calcualteAgreement(SetCodingAnnotationStudy codingAnnotationStudy, CountMap<String> globalCategoryCount, HashMap<String, CountMap<String>> annotatorCategoryCount, CountMap<String> globalCategoryOverlap) {
		// Choose the agreement measure method
		IAgreementMeasure agreement;
		switch (pAgreementMeasure) {
			case "CohenKappaAgreement":
				if (codingAnnotationStudy.getRaterCount() != 2) {
					throw new UnsupportedOperationException(String.format("CohenKappaAgreement only supports exactly 2 annotators, not %d!", codingAnnotationStudy.getRaterCount()));
				}
				agreement = new CohenKappaAgreement(codingAnnotationStudy);
				break;
			case "FleissKappaAgreement":
				agreement = new FleissKappaAgreement(codingAnnotationStudy);
				break;
			case "PercentageAgreement":
				agreement = new PercentageAgreement(codingAnnotationStudy);
				break;
			case "KrippendorffAlphaAgreement":
			default:
				agreement = new KrippendorffAlphaAgreement(codingAnnotationStudy, new NominalDistanceFunction());
				break;
		}
		
		return agreement;
	}
	
	private void printCategoryOverlap(CountMap<String> globalCategoryOverlap) {
		System.out.print("\nInter-annotator category overlap\nCategory\tCount\n");
		Optional<Integer> totalOverlap = globalCategoryOverlap.values().stream().reduce(Integer::sum);
		System.out.printf("Total\t%d\n", totalOverlap.orElse(0));
		for (String category : categories) {
			System.out.printf("%s\t%d\n", category, globalCategoryOverlap.getOrDefault(category, 0));
		}
	}
	
}
