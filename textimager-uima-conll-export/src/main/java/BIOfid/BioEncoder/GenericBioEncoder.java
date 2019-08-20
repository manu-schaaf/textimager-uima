package BIOfid.BioEncoder;

import BIOfid.ConllFeature.ConllFeatures;
import biofid.utility.CountMap;
import com.google.common.collect.Lists;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.util.CasCopier;
import org.texttechnologielab.annotation.type.Fingerprint;
import org.texttechnologylab.annotation.AbstractNamedEntity;
import org.texttechnologylab.annotation.NamedEntity;
import org.texttechnologylab.annotation.type.Other;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.apache.uima.fit.util.JCasUtil.*;

public abstract class GenericBioEncoder<T extends Annotation> {
	final HashMap<Token, ArrayList<ConllFeatures>> hierachialTokenNamedEntityMap;
	final CountMap<T> namedEntityHierachy;
	final boolean filterFingerprinted;
	final JCas jCas;
	final ArrayList<Class<? extends Annotation>> forceAnnotations;
	final TreeMap<Integer, Token> tokenIndexMap;
	
	Class<T> type;
	/**
	 * Set false to use IOB-1 format.
	 */
	boolean useIOB2; // FIXME
	TreeMap<Integer, TreeSet<T>> namedEntityByRank;
	ArrayList<Integer> maxCoverageOrder;
	public LinkedHashMap<Integer, Long> coverageCount;
	
	Comparator<Annotation> beginComparator = Comparator.comparingInt(Annotation::getBegin);
	private Comparator<Annotation> hierachialComparator = new Comparator<Annotation>() {
		@Override
		public int compare(Annotation o1, Annotation o2) {
			int cmp = Integer.compare(namedEntityHierachy.get(o1), namedEntityHierachy.get(o2));
			cmp = cmp != 0 ? cmp : Integer.compare(o1.getBegin(), o2.getBegin());
			return cmp;
		}
	};
	
	protected GenericBioEncoder(JCas jCas, boolean pFilterFingerprinted) {
		this(jCas, false, new ArrayList<>());
	}
	
	GenericBioEncoder(JCas jCas, boolean pFilterFingerprinted, ArrayList<Class<? extends Annotation>> forceAnnotations) {
		this.jCas = jCas;
		this.hierachialTokenNamedEntityMap = new HashMap<>();
		this.namedEntityHierachy = new CountMap<>();
		this.namedEntityByRank = new TreeMap<>();
		this.useIOB2 = true;
		this.filterFingerprinted = pFilterFingerprinted;
		this.forceAnnotations = forceAnnotations;
		this.tokenIndexMap = new TreeMap<>();
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
			
			final LinkedHashSet<T> namedEntities = new LinkedHashSet<>();
			if (filterFingerprinted) {
				// Get all fingerprinted TOPs
				HashSet<TOP> fingerprinted = select(mergedCas, Fingerprint.class).stream()
						.map(Fingerprint::getReference)
						.collect(Collectors.toCollection(HashSet::new));
				// Filter all NEs for fingerprinted ones
				// and the ones that are forced by forceAnnotations
				select(mergedCas, this.type).stream()
						.filter((TOP o) -> fingerprinted.contains(o) || forceAnnotations.contains(o.getClass()))
						.forEach(namedEntities::add);
			} else {
				namedEntities.addAll(select(mergedCas, this.type));
			}
			
			// Initialize the hierarchy
			namedEntities.forEach(key -> namedEntityHierachy.put(key, 0));
			
			// Iterate over all NEs that are being covered by another NE
			// and set their hierarchy level to their parents level + 1
			for (T parentNamedEntity : namedEntities) {
				JCasUtil.subiterate(mergedCas, type, parentNamedEntity, true, false)
						.forEach(childNamedEntity -> {
							if (namedEntities.contains(childNamedEntity))
								namedEntityHierachy.put(childNamedEntity, namedEntityHierachy.get(parentNamedEntity) + 1);
						});
			}
			
			// Put all NEs into a Map<Integer, TreeSet> by their rank, with all sets ordered by the begin of the entities
			namedEntityHierachy.forEach((ne, rank) -> {
				TreeSet<T> orderedTreeSetOfRank = namedEntityByRank.getOrDefault(rank, new TreeSet<>(beginComparator));
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
		} catch (CASException e) {
			e.printStackTrace();
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
	
	void mergeViews(JCas mergedCas) throws CASException {
		CasCopier.copyCas(jCas.getCas(), mergedCas.getCas(), true, true);
		
		jCas.getViewIterator().forEachRemaining(viewCas -> {
			for (T namedEntity : select(viewCas, type)) {
				Annotation nean = (Annotation) mergedCas.getCas().createAnnotation(namedEntity.getType(), namedEntity.getBegin(), namedEntity.getEnd());
				nean.addToIndexes();
			}
		});
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
		Map<T, Collection<Token>> tokenNeIndex = indexCovered(jCas, type, Token.class);
		LinkedHashSet<T> visitedEntities = new LinkedHashSet<>();
		ArrayList<TreeSet<T>> rankSets = Lists.newArrayList(namedEntityByRank.values());
		for (int i = 0; i < rankSets.size(); i++) {
			TreeSet<T> rankSet = rankSets.get(i);
			rankSet.removeAll(visitedEntities);
			
			// A set to collect all tokens, that have been covered by an annotation
			HashSet<Token> visitedTokens = new HashSet<>();
			
			for (T namedEntity : rankSet) {
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
				TreeSet<T> rankSetBDSearch = rankSets.get(j);
				rankSet.removeAll(visitedEntities);
				for (T namedEntity : rankSetBDSearch) {
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
	 * Compute the coverage for each hierarchy level and list the level indices sorted by their respective coverage.
	 */
	public void createMaxCoverageLookup() {
		Optional<ArrayList<ConllFeatures>> optionalArrayList = hierachialTokenNamedEntityMap.values().stream().findAny();
		maxCoverageOrder = new ArrayList<>();
		if (optionalArrayList.isPresent()) {
			int size = optionalArrayList.get().size();
			coverageCount = IntStream.range(0, size).boxed()
					.collect(Collectors.toMap(
							Function.identity(),
							i -> hierachialTokenNamedEntityMap.values().stream()
									.filter(l -> l.get(i).isOut())
									.count(),
							(u, v) -> u,
							LinkedHashMap::new));
			maxCoverageOrder.addAll(
					coverageCount.entrySet().stream().sequential()
							.sorted(Comparator.comparingLong(Map.Entry::getValue))
							.mapToInt(Map.Entry::getKey).boxed()
							.collect(Collectors.toList()));
			Collections.reverse(maxCoverageOrder);
		} else {
			maxCoverageOrder.add(0);
		}
	}
	
	/**
	 * Create a naive NE hierarchy by stacking NE annotations over tokens on top of each other.
	 * <p>
	 * Given a list of tokens and the rank of each Named Entity, iterate over all NEs by rank, sorted by their begin.
	 * This sorts top level annotations first, as longer annotations precede others in the iteration order returned by
	 * {@link JCasUtil#select(JCas, Class)}.
	 * </p><p>
	 * For each rank, get all token covered by a NE and add the BIO code to the tokens hierarchy in the
	 * {@link DKProHierarchicalBioEncoder#hierachialTokenNamedEntityMap}. At the end of each iteration over a rank, add an "O"
	 * to all not covered tokens.
	 * </p><p>
	 * This approach will <b>not</b> "fill" holes created by three or more annotations overlapping, ie. given:
	 * <pre>
	 * Token:       t1  t2  t3
	 * Entities:    A   AB  BC</pre>
	 * The corresponding ranks and the resulting hierarchy will be:
	 * <pre>
	 * Token:       t1  t2  t3
	 * Rank/Lvl 1:  A   A   O
	 * Rank/Lvl 2:  O   B   B
	 * Rank/Lvl 3:  O   O   C</pre>
	 * </p>
	 *
	 * @param jCas   The JCas containing the annotations.
	 * @param tokens A list of token to be considered.
	 * @see DKProHierarchicalBioEncoder#breadthFirstSearch(JCas, ArrayList) breadthFirstSearch(JCas, ArrayList)
	 */
	public void naiveStackingApproach(JCas jCas, ArrayList<Token> tokens) {
		Map<T, Collection<Token>> tokenNeIndex = indexCovered(jCas, this.type, Token.class);
		for (TreeSet<T> rankSet : namedEntityByRank.values()) {
			// A set to collect all tokens, that have been covered by an annotation
			HashSet<Token> rankCoveredTokens = new HashSet<>();
			
			for (T namedEntity : rankSet) {
				// Get all tokens covered by this NE
				Collection<Token> coveredTokens = tokenNeIndex.get(namedEntity);
				// Add this Named Entity to the tokens NE hierarchy
				for (Token coveredToken : coveredTokens) {
					ArrayList<ConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(coveredToken);
					namedEntityStringTreeMap.add(getFeatures(namedEntity, coveredToken));
				}
				rankCoveredTokens.addAll(coveredTokens);
			}
			
			// Iterate over all tokens, that have not been covered in this iteration
			// and fill their hierarchy with an "O". This levels all
			ArrayList<Token> notCoveredTokens = new ArrayList<>(tokens);
			notCoveredTokens.removeAll(rankCoveredTokens);
			for (Token notCoveredToken : notCoveredTokens) {
				ArrayList<ConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(notCoveredToken);
				namedEntityStringTreeMap.add(new ConllFeatures());
			}
		}
	}
	
	@Deprecated
	public void tokenInConflictApproach(JCas jCas, ArrayList<Token> tokens) {
		HashMap<Token, TreeSet<T>> tokenNeMap = new HashMap<>();
		tokens.forEach(token -> tokenNeMap.put(token, new TreeSet<>(hierachialComparator)));
		
		Map<T, Collection<Token>> tokenNeIndex = indexCovered(jCas, type, Token.class);
		tokenNeIndex.forEach((ne, tks) -> tks.forEach(tk -> tokenNeMap.get(tk).add(ne)));
		
		HashSet<T> usedEntities = new HashSet<>();
		
		Token curr_token = tokens.get(0);
		while (true) {
			TreeSet<T> treeSet = tokenNeMap.get(curr_token);
			treeSet.removeAll(usedEntities);
			if (treeSet.isEmpty()) {
				ArrayList<ConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(curr_token);
				namedEntityStringTreeMap.add(new ConllFeatures());
			} else {
				for (T namedEntity : treeSet) {
					if (usedEntities.contains(namedEntity)) continue;
					else usedEntities.add(namedEntity);
					for (Token coveredToken : tokenNeIndex.get(namedEntity)) { // FIXME: greift zurück, soll aber einen Konflikt finden!
						curr_token = coveredToken;
						ArrayList<ConllFeatures> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(curr_token);
						namedEntityStringTreeMap.add(getFeatures(namedEntity, curr_token));
					}
					
					break;
				}
			}
			if (tokens.indexOf(curr_token) == tokens.size() - 1) break;
			curr_token = selectSingleRelative(jCas, Token.class, curr_token, 1);
		}
	}
	
	/**
	 * Return the BIO-code of the given annotation over the given as a string.
	 *
	 * @param namedEntity
	 * @param token
	 * @return BIO-code of the annotation over the token as string.
	 */
	public ConllFeatures getFeatures(T namedEntity, Token token) {
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
		} else if (namedEntity instanceof de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity) {
			features.name(((de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity) namedEntity).getValue());
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
	
	public ArrayList<String> getFeatures(Token token) {
		return getFeatures(token, Strategy.byIndex(1));
	}
	
	public ArrayList<String> getFeatures(int index, int strategyIndex) {
		return getFeatures(tokenIndexMap.get(index), Strategy.byIndex(strategyIndex));
	}
	
	public ArrayList<String> getFeatures(Token token, int strategyIndex) {
		return getFeatures(token, Strategy.byIndex(strategyIndex));
	}
	
	public ArrayList<String> getFeatures(Token token, Strategy strategy) {
		ArrayList<String> retList = new ArrayList<>();
		
		ArrayList<ConllFeatures> neList;
		switch (strategy) {
			default:
			case TopFirstBottomUp: /// FIXME
			case TopDown:
				neList = new ArrayList<>(hierachialTokenNamedEntityMap.get(token));
				if (neList.isEmpty())
					break;
				retList.addAll(neList.get(0).build());
				break;
//			case TopDown:
//				retList = new ArrayList<>(hierachialTokenNamedEntityMap.get(token));
//				break;
			case BottomUp:
				retList = Lists.reverse(hierachialTokenNamedEntityMap.get(token)).get(0).build();
				break;
			case MaxCoverage:
				Integer index = maxCoverageOrder.get(0);
				try {
					retList = hierachialTokenNamedEntityMap.get(token).get(index).build();
				} catch (NullPointerException e) {
					e.printStackTrace();
				}
		}
		
		return retList;
	}
	
	enum Strategy {
		TopFirstBottomUp(0), TopDown(1), BottomUp(2), MaxCoverage(3);
		
		final int index;
		
		Strategy(int i) {
			index = i;
		}
		
		public static Strategy byIndex(int i) {
			for (Strategy strategy : Strategy.values()) {
				if (strategy.index == i) {
					return strategy;
				}
			}
			throw new IndexOutOfBoundsException(String.format("The strategy index %d is out of bounds!", i));
		}
	}
}
