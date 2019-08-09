package BIOfid.Engine;

import BIOfid.Utility.CountMap;
import BIOfid.Utility.IndexingMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.commons.lang3.StringUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.dkpro.statistics.agreement.coding.CodingAnnotationStudy;
import org.dkpro.statistics.agreement.coding.KrippendorffAlphaAgreement;
import org.dkpro.statistics.agreement.distance.NominalDistanceFunction;
import org.texttechnologielab.annotation.type.Fingerprint;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.uima.fit.util.JCasUtil.indexCovering;
import static org.apache.uima.fit.util.JCasUtil.select;

public class CodingInterAnnotatorAgreementEngine extends InterAnnotatorAgreementEngine {
	
	private ImmutableSet<Class<? extends Annotation>> annotationClasses = ImmutableSet.of(Annotation.class);
	private TreeSet<String> categories = new TreeSet<>();
	private Integer maxCasIndex = 0;
	private HashMap<Integer, HashMap<String, HashMap<Integer, String>>> perCasStudies = new HashMap<>();
	private HashMap<Integer, Integer> perCasTokenCount = new HashMap<>();
	private LinkedHashSet<String> annotatorList = new LinkedHashSet<>();
	private ImmutableSet<String> excludedAnnotators = ImmutableSet.of();
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
	}
	
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
			
			maxCasIndex += 1;
			int tokenCount = select(jCas, Token.class).size();
			perCasTokenCount.put(maxCasIndex, tokenCount);
			
			// Iterate over all views
			HashMap<String, HashMap<Integer, String>> perViewAnnotations = new HashMap<>();
			jCas.getViewIterator().forEachRemaining(viewCas -> {
				// Split user id from view name and get annotator index for this id. Discards "_InitialView"
				String viewName = StringUtils.substringAfterLast(viewCas.getViewName().trim(), "/");
				// Check for empty view name and excluded annotators
				if (viewName.isEmpty() || excludedAnnotators.contains(viewName))
					return;
				HashMap<Integer, String> currentViewAnnotationMap = new HashMap<>();
				perViewAnnotations.put(viewName, currentViewAnnotationMap);
				annotatorList.add(viewName);
				
				// Get all fingerprinted annotations
				HashSet<TOP> fingerprinted = new HashSet<>(select(viewCas, Fingerprint.class).stream()
						.map(Fingerprint::getReference)
						.collect(Collectors.toCollection(HashSet::new)));
				
				// Create an index for the tokens
				IndexingMap<Token> tokenIndexingMap = new IndexingMap<>();
				JCasUtil.select(viewCas, Token.class).forEach(tokenIndexingMap::add);
				if (tokenIndexingMap.size() != tokenCount) {
					logger.error("The number of tokens in this view does not match with the number of tokens in the default view!");
					return;
				}
				Map<Token, Collection<Annotation>> tokenCoveringIndex = indexCovering(viewCas, Token.class, Annotation.class);
				
				HashMap<Class<? extends Annotation>, LinkedHashSet<? extends TOP>> annotationLookup = new HashMap<>();
				for (Class<? extends Annotation> annotationClass : annotationClasses) {
					annotationLookup.put(annotationClass, new LinkedHashSet<>(JCasUtil.select(viewCas, annotationClass)));
				}
				
				for (Token token : select(viewCas, Token.class)) {
					if (tokenCoveringIndex.get(token).isEmpty())
						continue;
					
					for (Class<? extends Annotation> annotationClass : annotationClasses) {
						ArrayList<Annotation> coveringAnnotations = new ArrayList<>(tokenCoveringIndex.get(token));
						coveringAnnotations.retainAll(annotationLookup.get(annotationClass));
						if (!coveringAnnotations.isEmpty() && (!pFilterFingerprinted || fingerprinted.contains(coveringAnnotations.get(0)))) {
							Annotation firstMatchingAnnotation = coveringAnnotations.get(0);
							String category = firstMatchingAnnotation.getType().getShortName();
							currentViewAnnotationMap.put(tokenIndexingMap.get(token), category);
							break;
						}
					}
				}
			});
			perCasStudies.put(maxCasIndex, perViewAnnotations);
		} catch (CASException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void collectionProcessComplete() throws AnalysisEngineProcessException {
		super.collectionProcessComplete();
		if (annotatorList.size() > 1) {
			CountMap<Object> categoryCount = new CountMap<>();
			
			HashMap<String, CountMap<Object>> annotatorCategoryCount = new HashMap<>();
			
			// Initialize a CountMap for each annotator
			for (String annotator : annotatorList) {
				annotatorCategoryCount.put(annotator, new CountMap<>());
			}
			
			CodingAnnotationStudy codingAnnotationStudy = new CodingAnnotationStudy(annotatorList.size());
			TreeSet<String> categories = this.categories;
			for (int casIndex = 0; casIndex < maxCasIndex; casIndex++) {
				if (perCasStudies.containsKey(casIndex)) {
					HashMap<String, HashMap<Integer, String>> perCasStudy = perCasStudies.get(casIndex);
					for (int tokenIndex = 0; tokenIndex < perCasTokenCount.get(casIndex); tokenIndex++) {
						ArrayList<String> perTokenAnnotations = new ArrayList<>();
						for (String annotatorName : annotatorList) {
							String category = perCasStudy.getOrDefault(annotatorName, new HashMap<>()).getOrDefault(tokenIndex, null);
							perTokenAnnotations.add(category);
							
							if (category != null) {
								categoryCount.inc(category);
								annotatorCategoryCount.get(annotatorName).inc(category);
								categories.add(category);
							}
						}
						codingAnnotationStudy.addItemAsArray(perTokenAnnotations.toArray(new String[0]));
					}
				}
			}
			// Compute and print the agreement for all categories
			KrippendorffAlphaAgreement agreement = new KrippendorffAlphaAgreement(codingAnnotationStudy, new NominalDistanceFunction());
			System.out.printf("\nKrippendorff-Alpha-Agreement\nInter-annotator agreement for %d annotators: %s\n" +
							"Category\tCount\tAgreement\n" +
							"Overall\t%d\t%f\n",
					annotatorList.size(), annotatorList.toString(), codingAnnotationStudy.getUnitCount(), agreement.calculateAgreement());
			printStudyResults(agreement, categoryCount, annotatorCategoryCount, categories, annotatorMap.keySet());
		}
	}
	
}
