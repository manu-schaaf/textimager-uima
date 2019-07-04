package BIOfid.Engine;

import BIOfid.Utility.CountMap;
import BIOfid.Utility.IndexingMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.component.JCasConsumer_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.dkpro.statistics.agreement.coding.CodingAnnotationStudy;
import org.dkpro.statistics.agreement.unitizing.IUnitizingAnnotationUnit;
import org.dkpro.statistics.agreement.unitizing.KrippendorffAlphaUnitizingAgreement;
import org.dkpro.statistics.agreement.unitizing.UnitizingAnnotationStudy;
import org.texttechnologielab.annotation.type.Fingerprint;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.uima.fit.util.JCasUtil.select;

public class InterAnnotatorAgreementEngine extends JCasConsumer_ImplBase {
	
	/**
	 * An array of fully qualified names of classes, that extend {@link Annotation},
	 * which are to be considered in the agreement computation.
	 * Adding classes which extend other given classes might return unexpected results.
	 */
	public static final String PARAM_ANNOTATION_CLASSES = "pAnnotationClasses";
	@ConfigurationParameter(name = PARAM_ANNOTATION_CLASSES, mandatory = false)
	private String[] pAnnotationClasses;
	
	public static final String PARAM_EXCLUDE_ANNOTATORS = "pExcludedAnnotators";
	@ConfigurationParameter(name = PARAM_EXCLUDE_ANNOTATORS, mandatory = false)
	private String[] pExcludedAnnotators;
	
	/**
	 * If set true, only JCas containing at least 2 views other than _InitialView will be processed.
	 */
	public static final String PARAM_DISCARD_SINGLE_VIEW = "pDiscardSingleView";
	@ConfigurationParameter(name = PARAM_DISCARD_SINGLE_VIEW, mandatory = false, defaultValue = "true")
	private Boolean pDiscardSingleView;
	
	/**
	 * If true, only consider annotations coverd by a {@link Fingerprint}.
	 */
	public static final String PARAM_FILTER_FINGERPRINTED = "pFilterFingerprinted";
	@ConfigurationParameter(name = PARAM_FILTER_FINGERPRINTED, defaultValue = "true")
	private Boolean pFilterFingerprinted;
	
	private TreeSet<String> categories = new TreeSet<>();
	private AtomicInteger documentOffset = new AtomicInteger(0);
	
	private ImmutableSet<Class<? extends Annotation>> annotationClasses = ImmutableSet.of(Annotation.class);
	private ArrayList<ImmutablePair<Integer, Iterable<IUnitizingAnnotationUnit>>> annotationStudies = new ArrayList<>();
	private IndexingMap<String> annotatorMap = new IndexingMap<>();
	private ImmutableSet<String> excludedAnnotators = ImmutableSet.of();
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
		ArrayList<Class<? extends Annotation>> classArrayList = new ArrayList<>();
		
		// If class names were passed as parameters, update the annotationClasses set
		if (pAnnotationClasses != null && pAnnotationClasses.length > 0) {
			for (String pAnnotationClass : pAnnotationClasses) {
				// Get a class from its class name and cast it to Class<? extends Annotation>
				try {
					Class<?> aClass = Class.forName(pAnnotationClass);
					if (Annotation.class.isAssignableFrom(aClass)) {
						classArrayList.add((Class<? extends Annotation>) aClass);
					}
				} catch (ClassNotFoundException e) {
					getLogger().warn(e.getMessage());
				}
			}
			// If any class could be found, update the set
			if (classArrayList.size() > 0)
				this.annotationClasses = ImmutableSet.copyOf(classArrayList);
		}
		
		// Set the list of excluded annotators
		if (pExcludedAnnotators != null && pExcludedAnnotators.length > 0) {
			excludedAnnotators = ImmutableSet.copyOf(pExcludedAnnotators);
		}
		getLogger().info("Computing inter-annotator agreement for subclasses of " + annotationClasses.toString());
		if (!excludedAnnotators.isEmpty()) {
			getLogger().info("Excluding annotators with ids: " + excludedAnnotators.toString());
		}
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
			if (pDiscardSingleView && views < 2)
				return;
			
			// Initialize study
			int documentLength = JCasUtil.select(jCas, Token.class).size();
			UnitizingAnnotationStudy perCasStudy = new UnitizingAnnotationStudy((int) views, documentLength);
			CodingAnnotationStudy codingAnnotationStudy = new CodingAnnotationStudy((int) views);
			
			// Iterate over all views
			jCas.getViewIterator().forEachRemaining(viewCas -> {
				// Split user id from view name and get annotator index for this id. Discards "_InitialView"
				String viewName = StringUtils.substringAfterLast(viewCas.getViewName().trim(), "/");
				// Check for empty view name and excluded annotators
				if (viewName.isEmpty() || excludedAnnotators.contains(viewName))
					return;
				annotatorMap.add(viewName);
				
				HashSet<TOP> fingerprinted = new HashSet<>(select(viewCas, Fingerprint.class).stream()
						.map(Fingerprint::getReference)
						.collect(Collectors.toCollection(HashSet::new)));
				
				IndexingMap<Token> tokenIndexingMap = new IndexingMap<>();
				JCasUtil.select(viewCas, Token.class).forEach(tokenIndexingMap::add);
				
				// Select all annotations of all given types and add an annotation unit for each item
				for (Class<? extends Annotation> aClass : annotationClasses) {
					Collection<? extends Annotation> annotations;
					if (pFilterFingerprinted)
						annotations = select(viewCas, aClass).stream()
								.filter((Predicate<TOP>) fingerprinted::contains)
								.collect(Collectors.toList());
					else
						annotations = select(viewCas, aClass);
					
					for (Annotation annotation : annotations) {
						int begin = Integer.MAX_VALUE;
						int end = Integer.MIN_VALUE;
						ArrayList<Token> containedTokens = Lists.newArrayList(JCasUtil.subiterate(viewCas, Token.class, annotation, false, true));
						for (Token token : containedTokens) {
							int index = tokenIndexingMap.get(token);
							if (index < begin) {
								begin = index;
							} else if (index > end) {
								end = index;
							}
						}
						String category = annotation.getType().getShortName();
						perCasStudy.addUnit(
								begin,
								end - begin + 1,
								annotatorMap.get(viewName),
								category
						);
						categories.add(category);
					}
				}
			});
			
			// Store the collected annotations units and update the document offset for final evaluation
			annotationStudies.add(ImmutablePair.of(documentOffset.get(), perCasStudy.getUnits()));
			documentOffset.getAndAdd(documentLength);
		} catch (CASException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void collectionProcessComplete() throws AnalysisEngineProcessException {
		super.collectionProcessComplete();
		if (annotatorMap.size() > 1) {
			UnitizingAnnotationStudy completeStudy = new UnitizingAnnotationStudy(annotatorMap.size(), documentOffset.get());
			CountMap<Object> categoryCount = new CountMap<>();
			HashMap<String, CountMap<Object>> annotatorCategoryCount = new HashMap<>();
			
			// Initialize a CountMap for each annotator
			for (String annotator : annotatorMap.keySet()) {
				annotatorCategoryCount.put(annotator, new CountMap<>());
			}
			
			// Iterate over all previously collected studies
			for (ImmutablePair<Integer, Iterable<IUnitizingAnnotationUnit>> study : annotationStudies) {
				int studyOffset = study.getLeft();
				
				// Add all annotation units from the study with correct offset
				for (IUnitizingAnnotationUnit annotationUnit : study.getRight()) {
					int id = annotationUnit.getRaterIdx();
					long length = annotationUnit.getLength();
					long offset = annotationUnit.getEndOffset() - length;
					Object category = annotationUnit.getCategory();
					
					completeStudy.addUnit(studyOffset + offset, length, id, category);
					
					// Update category counts
					categoryCount.inc(category);
					annotatorCategoryCount.get(annotatorMap.getKey(id)).inc(category);
				}
			}
			
			// Compute and print the agreement for all categories
			KrippendorffAlphaUnitizingAgreement agreement = new KrippendorffAlphaUnitizingAgreement(completeStudy);
			System.out.printf("\nInter-annotator agreement for %d annotators: %s\n" +
							"Category\tCount\tAgreement\n" +
							"Overall\t%d\t%f\n",
					annotatorMap.size(), annotatorMap.toString(), completeStudy.getUnitCount(), agreement.calculateAgreement());
			for (String category : categories) {
				System.out.printf("%s\t%d\t%f\n", category, categoryCount.get(category), agreement.calculateCategoryAgreement(category));
			}
			System.out.println();
			
			// Print annotation statistics for each annotator and all categories
			System.out.print("Annotation statistics:\nCategory");
			for (String annotator : annotatorMap.keySet()) {
				System.out.printf("\t#%s", annotator);
			}
			System.out.println();
			
			System.out.print("Total");
			for (String annotator : annotatorMap.keySet()) {
				Integer total = annotatorCategoryCount.get(annotator).values().stream().reduce(Integer::sum).get();
				System.out.printf("\t%d", total);
			}
			System.out.println();
			
			for (String category : categories) {
				System.out.printf("%s", category);
				for (String annotator : annotatorMap.keySet()) {
					System.out.printf("\t%d", annotatorCategoryCount.get(annotator).get(category));
				}
				System.out.println();
			}
		}
	}
	
}
