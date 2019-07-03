package BIOfid.Extraction;

import BIOfid.ConllFeature.ConllFeatures;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.util.CasCopier;
import org.dkpro.statistics.agreement.unitizing.IUnitizingAnnotationUnit;
import org.dkpro.statistics.agreement.unitizing.KrippendorffAlphaUnitizingAgreement;
import org.dkpro.statistics.agreement.unitizing.UnitizingAnnotationStudy;
import org.texttechnologielab.annotation.type.Fingerprint;
import org.texttechnologylab.annotation.AbstractNamedEntity;
import org.texttechnologylab.annotation.NamedEntity;
import org.texttechnologylab.annotation.type.Other;
import org.texttechnologylab.annotation.type.Taxon;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.apache.uima.fit.util.JCasUtil.indexCovered;
import static org.apache.uima.fit.util.JCasUtil.select;

public class TTLabHierarchicalBioEncoder extends GenericBioEncoder<Annotation> {
	
	public UnitizingAnnotationStudy annotationStudy;
	
	/**
	 * DKProHierarchicalBioEncoder that filters for fingerprinted annotations and includes all {@link Taxon} annotations by default
	 * <p>See {@link TTLabHierarchicalBioEncoder#TTLabHierarchicalBioEncoder(JCas, boolean, ArrayList)}.
	 *
	 * @param jCas The JCas to process.
	 */
	public TTLabHierarchicalBioEncoder(JCas jCas) {
		this(jCas, true, Lists.newArrayList(Taxon.class));
	}
	
	/**
	 * DKProHierarchicalBioEncoder that includes all {@link Taxon} annotations by default
	 * <p>See {@link TTLabHierarchicalBioEncoder#TTLabHierarchicalBioEncoder(JCas, boolean, ArrayList)}.
	 *
	 * @param jCas                 The JCas to process.
	 * @param pFilterFingerprinted If true, only fingerprinted {@link NamedEntity NamedEntities} are processed.
	 */
	public TTLabHierarchicalBioEncoder(JCas jCas, boolean pFilterFingerprinted) {
		this(jCas, pFilterFingerprinted, Lists.newArrayList(Taxon.class));
	}
	
	/**
	 * An encoder for the BIO-/IOB2-format that can handle an arbitrary number of stacked annotations.
	 *
	 * @param jCas                 The JCas to process.
	 * @param pFilterFingerprinted If true, only fingerprinted {@link NamedEntity NamedEntities} are processed.
	 * @param forceAnnotations     Include all annotations of these classes.
	 */
	public TTLabHierarchicalBioEncoder(JCas jCas, boolean pFilterFingerprinted, ArrayList<Class<? extends Annotation>> forceAnnotations) {
		super(jCas, pFilterFingerprinted, forceAnnotations);
		this.type = Annotation.class;
		this.build();
	}
	
	/**
	 * Builds the encoders indexes. Called by constructor.
	 */
	public void build() {
		try {
			if (jCas.getDocumentText() == null)
				return;
			
			final JCas mergedCas = JCasFactory.createJCas();
			mergeViews(mergedCas);
			
			final LinkedHashSet<Annotation> namedEntities = new LinkedHashSet<>();
			if (filterFingerprinted) {
				// Get all fingerprinted TOPs
				HashSet<TOP> fingerprinted = select(mergedCas, Fingerprint.class).stream()
						.map(Fingerprint::getReference)
						.collect(Collectors.toCollection(HashSet::new));
				// Filter all NEs for fingerprinted ones
				// and the ones that are forced by forceAnnotations
				select(mergedCas, Annotation.class).stream()
						.filter((Predicate<TOP>) fingerprinted::contains)
						.forEach(namedEntities::add);
			} else {
				namedEntities.addAll(select(mergedCas, NamedEntity.class));
				namedEntities.addAll(select(mergedCas, AbstractNamedEntity.class));
			}
			
			// Flatten the new view by removing identical duplicates
			final LinkedHashSet<Annotation> flattenedNamedEntities = new LinkedHashSet<>(namedEntities);
			for (Annotation parentNamedEntity : namedEntities) {
				JCasUtil.subiterate(mergedCas, type, parentNamedEntity, false, true)
						.forEach(childNamedEntity -> {
							if (flattenedNamedEntities.contains(childNamedEntity)
									&& parentNamedEntity.getBegin() == childNamedEntity.getBegin()
									&& parentNamedEntity.getEnd() == childNamedEntity.getEnd()
									&& parentNamedEntity.getType() == childNamedEntity.getType())
								flattenedNamedEntities.remove(childNamedEntity);
						});
			}
			
			// Initialize the hierarchy
			flattenedNamedEntities.forEach(key -> namedEntityHierachy.put(key, 0));
			
			// Iterate over all NEs that are being covered by another NE
			// and set their hierarchy level to their parents level + 1
			for (Annotation parentNamedEntity : flattenedNamedEntities) {
				JCasUtil.subiterate(mergedCas, type, parentNamedEntity, true, false)
						.forEach(childNamedEntity -> {
							if (flattenedNamedEntities.contains(childNamedEntity))
								namedEntityHierachy.put(childNamedEntity, namedEntityHierachy.get(parentNamedEntity) + 1);
						});
			}
			
			// Put all NEs into a Map<Integer, TreeSet> by their rank, with all sets ordered by the begin of the entities
			namedEntityHierachy.forEach((ne, rank) -> {
				TreeSet<Annotation> orderedTreeSetOfRank = namedEntityByRank.getOrDefault(rank, new TreeSet<>(beginComparator));
				orderedTreeSetOfRank.add(ne);
				namedEntityByRank.put(rank, orderedTreeSetOfRank);
			});
			
			// Create an empty list for all layers of NEs for each Token
			ArrayList<Token> tokens = new ArrayList<>(select(mergedCas, Token.class));
			for (int i = 0; i < tokens.size(); i++) {
				Token token = tokens.get(i);
				tokenIndexMap.put(i, token);
				hierachialTokenNamedEntityMap.put(token, new ArrayList<>());
			}
			
			if (namedEntityByRank.values().size() != 0) {
				// TODO: parametrize the approach selection
				breadthFirstSearch(mergedCas, tokens);
			} else {
				for (Token token : tokens) {
					ArrayList<ConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(token);
					namedEntityStringTreeMap.add(new ConllFeatures());
				}
			}
			
			createMaxCoverageLookup();
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	void mergeViews(JCas mergedCas) throws CASException {
		annotationStudy = new UnitizingAnnotationStudy(Iterators.size(jCas.getViewIterator()), jCas.getDocumentText().length());
		CasCopier.copyCas(jCas.getCas(), mergedCas.getCas(), true, true);
		
		final TreeSet<String> categories = new TreeSet<>();
		AtomicInteger annotatorCount = new AtomicInteger(0);
		jCas.getViewIterator().forEachRemaining(viewCas -> {
			annotatorCount.incrementAndGet();
			for (NamedEntity namedEntity : select(viewCas, NamedEntity.class)) {
				Annotation nean = (Annotation) mergedCas.getCas().createAnnotation(namedEntity.getType(), namedEntity.getBegin(), namedEntity.getEnd());
				((NamedEntity) nean).setValue(namedEntity.getValue());
				((NamedEntity) nean).setMetaphor(namedEntity.getMetaphor());
				nean.addToIndexes();
				
				annotationStudy.addUnit(namedEntity.getBegin(), namedEntity.getEnd() - namedEntity.getBegin(), annotatorCount.get(), namedEntity.getType().getShortName());
				categories.add(namedEntity.getType().getShortName());
			}
			
			for (AbstractNamedEntity namedEntity : select(viewCas, AbstractNamedEntity.class)) {
				Annotation nean = (Annotation) mergedCas.getCas().createAnnotation(namedEntity.getType(), namedEntity.getBegin(), namedEntity.getEnd());
				((AbstractNamedEntity) nean).setMetaphor(namedEntity.getMetaphor());
				nean.addToIndexes();
				
				annotationStudy.addUnit(namedEntity.getBegin(), namedEntity.getEnd() - namedEntity.getBegin(), annotatorCount.get(), namedEntity.getType().getShortName());
				categories.add(namedEntity.getType().getShortName());
			}
		});
		
		if (annotatorCount.get() > 1) {
			KrippendorffAlphaUnitizingAgreement agreement = new KrippendorffAlphaUnitizingAgreement(annotationStudy);
			System.out.printf("\n%s inter-annotator agreement:\n\tOverall agreement for %d units: %f", DocumentMetaData.get(jCas).getDocumentId(), annotationStudy.getUnitCount(), agreement.calculateAgreement());
			for (String category : categories) {
				System.out.printf("\n\t%s: %f", category, agreement.calculateCategoryAgreement(category));
			}
			System.out.println();
		}
	}
	
	
	/**
	 * Create a NE hierarchy by breadth-first search.
	 * <p>
	 * Given a list of tokens and the rank of each Named Entity, iterate over all NEs by rank, sorted by their begin.
	 * This sorts top level annotations first, as longer annotations precede others in the iteration order returned by
	 * {@link JCasUtil#select(JCas, Class)}.
	 * </p><p>
	 * For each rank, get all token covered by a NE and add the BIO code to the tokens hierarchy in the
	 * {@link DKProHierarchicalBioEncoder#hierachialTokenNamedEntityMap}. After each iteration, check all <i>higher</i> ranks
	 * for annotations, that cover annotations which are still unvisited in at this rank.
	 * At the end of each iteration over a rank, add an "O" to all not covered tokens.
	 * </p><p>
	 * This approach <b>will</b> "fill" holes created by three or more annotations overlapping, ie. given:
	 * <pre>
	 * Token:       t1  t2  t3
	 * Entities:    A   AB  BC</pre>
	 * The corresponding ranks will be:
	 * <pre>
	 * Token:       t1  t2  t3
	 * Rank 1:      A   A
	 * Rank 2:          B   B
	 * Rank 3:              C</pre>
	 * The resulting hierarchy will be:
	 * <pre>
	 * Token:       t1  t2  t3
	 * Level 1:     A   A   C
	 * Level 2:     B   B   O</pre>
	 * </p>
	 *
	 * @param jCas   The JCas containing the annotations.
	 * @param tokens A list of token to be considered.
	 * @see DKProHierarchicalBioEncoder#naiveStackingApproach(JCas, ArrayList) naiveStackingApproach(JCas, ArrayList)
	 */
	public void breadthFirstSearch(JCas jCas, ArrayList<Token> tokens) {
		Map<Annotation, Collection<Token>> tokenNeIndex = indexCovered(jCas, Annotation.class, Token.class);
		LinkedHashSet<Annotation> visitedEntities = new LinkedHashSet<>();
		ArrayList<TreeSet<Annotation>> rankSets = Lists.newArrayList(namedEntityByRank.values());
		for (int i = 0; i < rankSets.size(); i++) {
			TreeSet<Annotation> rankSet = rankSets.get(i);
			rankSet.removeAll(visitedEntities);
			
			// A set to collect all tokens, that have been covered by an annotation
			HashSet<Token> visitedTokens = new HashSet<>();
			
			for (Annotation namedEntity : rankSet) {
				// Get all tokens covered by this NE
				Collection<Token> coveredTokens = tokenNeIndex.get(namedEntity);
				// If its not already covered, add this Named Entity to the tokens NE hierarchy
				for (Token coveredToken : coveredTokens) {
					ArrayList<ConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(coveredToken);
					namedEntityStringTreeMap.add(getFeatures(namedEntity, coveredToken));
				}
				visitedTokens.addAll(coveredTokens);
				visitedEntities.add(namedEntity);
			}
			
			// Run breadth-first search over all higher ranks for all remaining token
			for (int j = i + 1; j < rankSets.size(); j++) {
				TreeSet<Annotation> rankSetBDSearch = rankSets.get(j);
				rankSet.removeAll(visitedEntities);
				for (Annotation namedEntity : rankSetBDSearch) {
					// Get all tokens covered by this NE
					ArrayList<Token> coveredTokens = new ArrayList<>(tokenNeIndex.get(namedEntity));
					// Check if any covered token is already covered by another NE annotation
					if (!coveredTokens.isEmpty() && coveredTokens.stream().anyMatch(visitedTokens::contains))
						continue;
					// If its not already covered, add this Named Entity to the tokens NE hierarchy
					for (Token coveredToken : coveredTokens) {
						ArrayList<ConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(coveredToken);
						namedEntityStringTreeMap.add(getFeatures(namedEntity, coveredToken));
					}
					visitedTokens.addAll(coveredTokens);
					visitedEntities.add(namedEntity);
				}
			}
			
			// Iterate over all tokens, that have not been covered in this iteration
			// and fill their hierarchy with an "O". This levels all
			ArrayList<Token> notCoveredTokens = new ArrayList<>(tokens);
			notCoveredTokens.removeAll(visitedTokens);
			for (Token notCoveredToken : notCoveredTokens) {
				ArrayList<ConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(notCoveredToken);
				namedEntityStringTreeMap.add(new ConllFeatures());
			}
		}
		
		int lastIndex = rankSets.size() - 1;
		if (lastIndex >= 0 && hierachialTokenNamedEntityMap.values().stream().allMatch(l -> l.get(lastIndex).equals("O")))
			hierachialTokenNamedEntityMap.values().forEach(l -> l.remove(lastIndex));
	}
	
	/**
	 * Return the BIO-code of the given annotation over the given as a string.
	 *
	 * @param namedEntity
	 * @param token
	 * @return BIO-code of the annotation over the token as string.
	 */
	public ConllFeatures getFeatures(Annotation namedEntity, Token token) {
		ConllFeatures features = new ConllFeatures();
		if (namedEntity instanceof org.texttechnologylab.annotation.AbstractNamedEntity) {
			features.name(namedEntity.getType().getShortName());
			
			AbstractNamedEntity ne = (AbstractNamedEntity) namedEntity;
			features.isAbstract(true);
			features.isMetaphor(ne.getMetaphor());
		} else if (namedEntity instanceof org.texttechnologylab.annotation.type.Other) {
			features.name(namedEntity.getType().getShortName());
			
			Other ne = (Other) namedEntity;
			features.isAbstract(ne.getValue() != null && !ne.getValue().isEmpty());
			features.isMetaphor(ne.getMetaphor());
		} else if (namedEntity instanceof org.texttechnologylab.annotation.NamedEntity) {
			features.name(namedEntity.getType().getShortName());
			
			NamedEntity ne = (NamedEntity) namedEntity;
			features.isAbstract(false);
			features.isMetaphor(ne.getMetaphor());
		} else if (namedEntity instanceof org.texttechnologielab.annotation.type.TexttechnologyNamedEntity) {
			features.name(((org.texttechnologielab.annotation.type.TexttechnologyNamedEntity) namedEntity).getValue());
		} else {
			features.name("<UNK>");
		}
		if (features.isNameInvalid())
			return new ConllFeatures("O");
		if (namedEntity.getBegin() == token.getBegin() == useIOB2) {
			features.prependTag("B-");
		} else {
			features.prependTag("I-");
		}
		return features;
	}
	
}
