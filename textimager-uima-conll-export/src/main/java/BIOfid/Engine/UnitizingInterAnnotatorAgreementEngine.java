package BIOfid.Engine;

import BIOfid.Utility.CountMap;
import BIOfid.Utility.IndexingMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.dkpro.statistics.agreement.coding.CodingAnnotationStudy;
import org.dkpro.statistics.agreement.unitizing.IUnitizingAnnotationUnit;
import org.dkpro.statistics.agreement.unitizing.KrippendorffAlphaUnitizingAgreement;
import org.dkpro.statistics.agreement.unitizing.UnitizingAnnotationStudy;
import org.texttechnologielab.annotation.type.Fingerprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.uima.fit.util.JCasUtil.select;

public class UnitizingInterAnnotatorAgreementEngine extends InterAnnotatorAgreementEngine {
	
	private TreeSet<String> categories = new TreeSet<>();
	private AtomicInteger documentOffset = new AtomicInteger(0);
	private ArrayList<ImmutablePair<Integer, Iterable<IUnitizingAnnotationUnit>>> annotationStudies = new ArrayList<>();
	private IndexingMap<String> annotatorMap = new IndexingMap<>();
	
	
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
				
				// Get all fingerprinted annotations
				HashSet<TOP> fingerprinted = new HashSet<>(select(viewCas, Fingerprint.class).stream()
						.map(Fingerprint::getReference)
						.collect(Collectors.toCollection(HashSet::new)));
				
				
				// Create an index for the tokens
				IndexingMap<Token> tokenIndexingMap = new IndexingMap<>();
				JCasUtil.select(viewCas, Token.class).forEach(tokenIndexingMap::add);
				
				// Select all annotations of all given types and add an annotation unit for each item
				for (Class<? extends Annotation> annotationClass : annotationClasses) {
					ArrayList<? extends Annotation> annotations;
					if (pFilterFingerprinted)
						annotations = select(viewCas, annotationClass).stream()
								.filter((Predicate<TOP>) fingerprinted::contains)
								.collect(Collectors.toCollection(ArrayList::new));
					else
						annotations = new ArrayList<>(select(viewCas, annotationClass));
					
					
					// Create a set of annotations, that are overlapped by another annotation
					HashSet<Annotation> overlappedAnnotations = getOverlappedAnnotations(viewCas, annotationClass, annotations);
					
					// Remove annotations, that are overlapped by an annotation of the same Type
					annotations.removeIf(overlappedAnnotations::contains);
					
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
			System.out.printf("\nKrippendorff-Alpha-Unitizing-Agreement\nInter-annotator agreement for %d annotators: %s\n" +
							"Category\tCount\tAgreement\n" +
							"Overall\t%d\t%f\n",
					annotatorMap.size(), annotatorMap.toString(), completeStudy.getUnitCount(), agreement.calculateAgreement());
			printStudyResults(agreement, categoryCount, annotatorCategoryCount, categories, annotatorMap.keySet());
		}
	}
	
}
