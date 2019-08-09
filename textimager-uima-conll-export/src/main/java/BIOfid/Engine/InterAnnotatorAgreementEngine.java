package BIOfid.Engine;

import BIOfid.Utility.CountMap;
import BIOfid.Utility.IndexingMap;
import com.google.common.collect.ImmutableSet;
import org.apache.uima.UimaContext;
import org.apache.uima.fit.component.JCasConsumer_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.internal.ExtendedLogger;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.dkpro.statistics.agreement.ICategorySpecificAgreement;
import org.jetbrains.annotations.NotNull;
import org.texttechnologielab.annotation.type.Fingerprint;

import java.util.*;

public abstract class InterAnnotatorAgreementEngine extends JCasConsumer_ImplBase {
	
	/**
	 * An array of fully qualified names of classes, that extend {@link Annotation},
	 * which are to be considered in the agreement computation.
	 * Adding classes which extend other given classes might return unexpected results.
	 */
	public static final String PARAM_ANNOTATION_CLASSES = "pAnnotationClasses";
	@ConfigurationParameter(name = PARAM_ANNOTATION_CLASSES, mandatory = false)
	protected String[] pAnnotationClasses;
	
	public static final String PARAM_EXCLUDE_ANNOTATORS = "pExcludedAnnotators";
	@ConfigurationParameter(name = PARAM_EXCLUDE_ANNOTATORS, mandatory = false)
	protected String[] pExcludedAnnotators;
	
	/**
	 * The minimal number of views in each CAS.
	 */
	public static final String PARAM_MIN_VIEWS = "pDiscardSingleView";
	@ConfigurationParameter(
			name = PARAM_MIN_VIEWS,
			mandatory = false,
			defaultValue = "2"
	)
	protected Integer pMinViews;
	
	/**
	 * If true, only consider annotations coverd by a {@link Fingerprint}.
	 */
	public static final String PARAM_FILTER_FINGERPRINTED = "pFilterFingerprinted";
	@ConfigurationParameter(
			name = PARAM_FILTER_FINGERPRINTED,
			defaultValue = "true"
	)
	protected Boolean pFilterFingerprinted;
	
	/**
	 * If true, print annotation statistics
	 */
	public static final String PARAM_PRINT_STATS = "pPrintStatistics";
	@ConfigurationParameter(
			name = PARAM_PRINT_STATS,
			mandatory = false,
			defaultValue = "true"
	)
	protected Boolean pPrintStatistics;
	
	protected IndexingMap<String> annotatorMap = new IndexingMap<>();
	protected ImmutableSet<Class<? extends Annotation>> annotationClasses = ImmutableSet.of(Annotation.class);
	protected ImmutableSet<String> excludedAnnotators = ImmutableSet.of();
	protected ExtendedLogger logger;
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
		logger = getLogger();
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
					logger.warn(e.getMessage());
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
		logger.info("Computing inter-annotator agreement for subclasses of " + annotationClasses.toString());
		if (!excludedAnnotators.isEmpty()) {
			logger.info("Excluding annotators with ids: " + excludedAnnotators.toString());
		}
	}
	
	/**
	 * Create a set of annotations, that are overlapped by another annotation
	 *
	 * @param viewCas     The cas containing the annotations.
	 * @param aClass      The class of the overlapped annotations.
	 * @param annotations A collection of all annotations.
	 * @return A HashSet of all annotations that are overlapped by any other annotation.
	 */
	@NotNull
	protected HashSet<Annotation> getOverlappedAnnotations(JCas viewCas, Class<? extends Annotation> aClass, Collection<? extends Annotation> annotations) {
		HashSet<Annotation> overlappedAnnotations = new HashSet<>();
		for (Annotation annotation : annotations) {
			JCasUtil.subiterate(viewCas, aClass, annotation, false, false).forEach(overlappedAnnotations::add);
		}
		return overlappedAnnotations;
	}
	
	protected void printStudyResults(ICategorySpecificAgreement agreement, CountMap<Object> categoryCount, HashMap<String, CountMap<Object>> annotatorCategoryCount, TreeSet<String> categories, Collection<String> annotators) {
		for (String category : categories) {
			System.out.printf("%s\t%d\t%f\n", category, categoryCount.get(category), agreement.calculateCategoryAgreement(category));
		}
		System.out.println();
		
		// Print annotation statistics for each annotator and all categories
		if (pPrintStatistics) {
			System.out.print("Annotation statistics:\nAnnotator");
			for (String annotator : annotators) {
				System.out.printf("\t#%s", annotator);
			}
			System.out.println();
			
			System.out.print("Total");
			for (String annotator : annotators) {
				Optional<Integer> optionalInteger = annotatorCategoryCount.get(annotator).values().stream().reduce(Integer::sum);
				if (optionalInteger.isPresent()) {
					Integer total = optionalInteger.get();
					System.out.printf("\t%d", total);
				}
			}
			System.out.println();
			
			for (String category : categories) {
				System.out.printf("%s", category);
				for (String annotator : annotators) {
					System.out.printf("\t%d", annotatorCategoryCount.get(annotator).get(category));
				}
				System.out.println();
			}
		}
	}
}
