package BIOfid.Extraction;

import BIOfid.Utility.CountMap;
import com.google.common.collect.Lists;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.texttechnologielab.annotation.type.Fingerprint;
import org.texttechnologylab.annotation.NamedEntity;
import org.texttechnologylab.annotation.type.Taxon;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.apache.uima.fit.util.JCasUtil.*;

public class TTLabSpecifiedBioEncoder extends GenericBioEncoder<NamedEntity> {
	Class<NamedEntity> type = NamedEntity.class;
	/**
	 * Set false to use IOB-1 format.
	 */
	public boolean useIOB2; // FIXME
	TreeMap<Integer, TreeSet<NamedEntity>> namedEntityByRank;
	ArrayList<Integer> maxCoverageOrder;
	
	Comparator<Annotation> beginComparator = Comparator.comparingInt(Annotation::getBegin);
	Comparator<Annotation> hierachialComparator = new Comparator<Annotation>() {
		@Override
		public int compare(Annotation o1, Annotation o2) {
			int cmp = Integer.compare(namedEntityHierachy.get(o1), namedEntityHierachy.get(o2));
			cmp = cmp != 0 ? cmp : Integer.compare(o1.getBegin(), o2.getBegin());
			return cmp;
		}
	};
	
	/**
	 * DKProHierarchicalBioEncoder that filters for fingerprinted annotations and includes all {@link Taxon} annotations by default
	 * <p>See {@link TTLabHierarchicalBioEncoder#TTLabHierarchicalBioEncoder(JCas, boolean, ArrayList)}.
	 *
	 * @param jCas The JCas to process.
	 */
	public TTLabSpecifiedBioEncoder(JCas jCas) {
		this(jCas, true, Lists.newArrayList(Taxon.class));
	}
	
	/**
	 * DKProHierarchicalBioEncoder that includes all {@link Taxon} annotations by default
	 * <p>See {@link TTLabHierarchicalBioEncoder#TTLabHierarchicalBioEncoder(JCas, boolean, ArrayList)}.
	 *
	 * @param jCas                 The JCas to process.
	 * @param pFilterFingerprinted If true, only fingerprinted {@link NamedEntity NamedEntities} are processed.
	 */
	public TTLabSpecifiedBioEncoder(JCas jCas, boolean pFilterFingerprinted) {
		this(jCas, pFilterFingerprinted, Lists.newArrayList(Taxon.class));
	}
	
	/**
	 * An encoder for the BIO-/IOB2-format that can handle an arbitrary number of stacked annotations.
	 *
	 * @param jCas                 The JCas to process.
	 * @param pFilterFingerprinted If true, only fingerprinted {@link NamedEntity NamedEntities} are processed.
	 * @param forceAnnotations     Include all annotations of these classes.
	 */
	public TTLabSpecifiedBioEncoder(JCas jCas, boolean pFilterFingerprinted, ArrayList<Class<? extends Annotation>> forceAnnotations) {
		super(jCas, pFilterFingerprinted, forceAnnotations);
		this.type = NamedEntity.class;
		this.build();
	}
	
	
	/**
	 * Builds the encoders indexes. Called by constructor.
	 */
	public void build() {
		final LinkedHashSet<NamedEntity> namedEntities = new LinkedHashSet<>();
		try {
			jCas.getViewIterator().forEachRemaining(viewCas -> {
				if (filterFingerprinted) {
					// Get all fingerprinted TOPs
					HashSet<TOP> fingerprinted = select(viewCas, Fingerprint.class).stream()
							.map(Fingerprint::getReference)
							.collect(Collectors.toCollection(HashSet::new));
					// Filter all NEs for fingerprinted ones
					// and the ones that are forced by forceAnnotations
					select(viewCas, this.type).stream()
							.filter((NamedEntity o) -> fingerprinted.contains(o) || forceAnnotations.contains(o.getClass()))
							.forEach(namedEntities::add);
				} else {
					namedEntities.addAll(select(viewCas, this.type));
				}
				
				// Initialize the hierarchy
				namedEntities.forEach(key -> namedEntityHierachy.put(key, 0));
				
				// Iterate over all NEs that are being covered by another NE
				// and set their hierarchy level to their parents level + 1
				for (NamedEntity parentNamedEntity : namedEntities) {
					JCasUtil.subiterate(viewCas, type, parentNamedEntity, true, false)
							.forEach(childNamedEntity -> {
								if (namedEntities.contains(childNamedEntity))
									namedEntityHierachy.put(childNamedEntity, namedEntityHierachy.get(parentNamedEntity) + 1);
							});
				}
//				boolean strict = false; // FIXME
//				for (org.texttechnologylab.annotation.NamedEntity parentNamedEntity : quickTreeNodes) {
////					JCasUtil.subiterate(viewCas, QuickTreeNode.class, parentNamedEntity, true, strict)
//					viewQuickTreeNodes.stream()
//							.filter(quickTreeNode -> quickTreeNode.getBegin() >= parentNamedEntity.getBegin()
//									&& (!strict || quickTreeNode.getEnd() <= parentNamedEntity.getEnd()))
//							.forEach(childNode -> {
//								if (quickTreeNodes.contains(childNode))
//									nodeHierachy.put(childNode, nodeHierachy.get(parentNamedEntity) + 1);
//							});
//				}
				
				// Put all NEs into a Map<Integer, TreeSet> by their rank, with all sets ordered by the begin of the entities
				namedEntityHierachy.forEach((ne, rank) -> {
					TreeSet<NamedEntity> orderedTreeSetOfRank = namedEntityByRank.getOrDefault(rank, new TreeSet<>(beginComparator));
					orderedTreeSetOfRank.add(ne);
					namedEntityByRank.put(rank, orderedTreeSetOfRank);
				});
			});
			
			// Create an empty list for all layers of NEs for each Token
			ArrayList<Token> tokens = new ArrayList<>(select(jCas, Token.class));
			for (Token token : tokens) {
				hierachialTokenNamedEntityMap.put(token, new ArrayList<>());
			}
			
			//		naiveStackingApproach(jCas, tokens); // TODO: parametrize the approach selection
			breadthFirstSearch(jCas, tokens);
			
			createMaxCoverageLookup();
		} catch (CASException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Compute the coverage for each hierarchy level and list the level indices sorted by their respective coverage.
	 */
	public void createMaxCoverageLookup() {
		Optional<ArrayList<String>> optionalArrayList = hierachialTokenNamedEntityMap.values().stream().findAny();
		if (optionalArrayList.isPresent()) {
			int size = optionalArrayList.get().size();
			maxCoverageOrder = IntStream.range(0, size).boxed()
					.map(i -> ImmutablePair.of(i,
							hierachialTokenNamedEntityMap.values().stream()
									.map(l -> !l.get(i).equals("O") ? 1 : 0)
									.reduce(0, Integer::sum)))
					.sorted(Comparator.comparingInt(ImmutablePair::getRight))
					.mapToInt(ImmutablePair::getLeft).boxed()
					.collect(Collectors.toCollection(ArrayList::new));
			Collections.reverse(maxCoverageOrder);
		} else {
			maxCoverageOrder = new ArrayList<>();
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
		Map<NamedEntity, Collection<Token>> tokenNeIndex = indexCovered(jCas, NamedEntity.class, Token.class);
		for (TreeSet<NamedEntity> rankSet : namedEntityByRank.values()) {
			// A set to collect all tokens, that have been covered by an annotation
			HashSet<Token> rankCoveredTokens = new HashSet<>();
			
			for (NamedEntity namedEntity : rankSet) {
				// Get all tokens covered by this NE
				Collection<Token> coveredTokens = tokenNeIndex.get(namedEntity);
				// Add this Named Entity to the tokens NE hierarchy
				for (Token coveredToken : coveredTokens) {
					ArrayList<String> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(coveredToken);
					namedEntityStringTreeMap.add(getBioCode(namedEntity, coveredToken));
				}
				rankCoveredTokens.addAll(coveredTokens);
			}
			
			// Iterate over all tokens, that have not been covered in this iteration
			// and fill their hierarchy with an "O". This levels all
			ArrayList<Token> notCoveredTokens = new ArrayList<>(tokens);
			notCoveredTokens.removeAll(rankCoveredTokens);
			for (Token notCoveredToken : notCoveredTokens) {
				ArrayList<String> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(notCoveredToken);
				namedEntityStringTreeMap.add("O");
			}
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
		Map<NamedEntity, Collection<Token>> tokenNeIndex = indexCovered(jCas, type, Token.class);
		LinkedHashSet<NamedEntity> visitedEntities = new LinkedHashSet<>();
		ArrayList<TreeSet<NamedEntity>> rankSets = Lists.newArrayList(namedEntityByRank.values());
		for (int i = 0; i < rankSets.size(); i++) {
			TreeSet<NamedEntity> rankSet = rankSets.get(i);
			rankSet.removeAll(visitedEntities);
			
			// A set to collect all tokens, that have been covered by an annotation
			HashSet<Token> visitedTokens = new HashSet<>();
			
			for (NamedEntity namedEntity : rankSet) {
				// Get all tokens covered by this NE
				Collection<Token> coveredTokens = tokenNeIndex.get(namedEntity);
				// If its not already covered, add this Named Entity to the tokens NE hierarchy
				for (Token coveredToken : coveredTokens) {
					ArrayList<String> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(coveredToken);
					namedEntityStringTreeMap.add(getBioCode(namedEntity, coveredToken));
				}
				visitedTokens.addAll(coveredTokens);
				visitedEntities.add(namedEntity);
			}
			
			// Run breadth-first search over all higher ranks for all remaining token
			for (int j = i + 1; j < rankSets.size(); j++) {
				TreeSet<NamedEntity> rankSetBDSearch = rankSets.get(j);
				rankSet.removeAll(visitedEntities);
				for (NamedEntity namedEntity : rankSetBDSearch) {
					// Get all tokens covered by this NE
					ArrayList<Token> coveredTokens = new ArrayList<>(tokenNeIndex.get(namedEntity));
					// Check if any covered token is already covered by another NE annotation
					if (!coveredTokens.isEmpty() && coveredTokens.stream().anyMatch(visitedTokens::contains))
						continue;
					// If its not already covered, add this Named Entity to the tokens NE hierarchy
					for (Token coveredToken : coveredTokens) {
						ArrayList<String> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(coveredToken);
						namedEntityStringTreeMap.add(getBioCode(namedEntity, coveredToken));
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
				ArrayList<String> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(notCoveredToken);
				namedEntityStringTreeMap.add("O");
			}
		}
		
		int lastIndex = rankSets.size() - 1;
		if (lastIndex >= 0 && hierachialTokenNamedEntityMap.values().stream().allMatch(l -> l.get(lastIndex).equals("O")))
			hierachialTokenNamedEntityMap.values().forEach(l -> l.remove(lastIndex));
	}
	
	@Deprecated
	public void tokenInConflictApproach(JCas jCas, ArrayList<Token> tokens) {
		HashMap<Token, TreeSet<NamedEntity>> tokenNeMap = new HashMap<>();
		tokens.forEach(token -> tokenNeMap.put(token, new TreeSet<>(hierachialComparator)));
		
		Map<NamedEntity, Collection<Token>> tokenNeIndex = indexCovered(jCas, type, Token.class);
		tokenNeIndex.forEach((ne, tks) -> tks.forEach(tk -> tokenNeMap.get(tk).add(ne)));
		
		HashSet<NamedEntity> usedEntities = new HashSet<>();
		
		Token curr_token = tokens.get(0);
		while (true) {
			TreeSet<NamedEntity> treeSet = tokenNeMap.get(curr_token);
			treeSet.removeAll(usedEntities);
			if (treeSet.isEmpty()) {
				ArrayList<String> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(curr_token);
				namedEntityStringTreeMap.add("O");
			} else {
				for (NamedEntity namedEntity : treeSet) {
					if (usedEntities.contains(namedEntity)) continue;
					else usedEntities.add(namedEntity);
					for (Token coveredToken : tokenNeIndex.get(namedEntity)) { // FIXME: greift zurück, soll aber einen Konflikt finden!
						curr_token = coveredToken;
						ArrayList<String> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(curr_token);
						namedEntityStringTreeMap.add(getBioCode(namedEntity, curr_token));
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
	public String getBioCode(NamedEntity namedEntity, Token token) {
		String value;
		if (namedEntity instanceof Taxon) {
			value = "TAX";
		} else {
			value = ((org.texttechnologylab.annotation.NamedEntity) namedEntity).getValue();
		}
//		else {
//			value = "UNK";
//		}
		if (namedEntity.getBegin() == token.getBegin() == useIOB2) {
			return "B-" + value.replaceAll("[IB\\-]*", ""); // FIXME
		} else {
			return "I-" + value.replaceAll("[IB\\-]*", ""); // FIXME
		}
	}
	
	public ArrayList<String> getTags(Token token) {
		return getTags(token, Strategy.byIndex(1));
	}
	
	public ArrayList<String> getTags(Token token, int strategyIndex) {
		return getTags(token, Strategy.byIndex(strategyIndex));
	}
	
	public ArrayList<String> getTags(Token token, Strategy strategy) {
		ArrayList<String> retList = new ArrayList<>();
		
		ArrayList<String> neList;
		switch (strategy) {
			default:
			case TopFirstBottomUp:
				neList = new ArrayList<>(hierachialTokenNamedEntityMap.get(token));
				if (neList.isEmpty())
					break;
				retList.add(neList.get(0));
				neList.remove(0);
				retList.addAll(Lists.reverse(neList));
				break;
			case TopDown:
				retList = new ArrayList<>(hierachialTokenNamedEntityMap.get(token));
				break;
			case BottomUp:
				retList = new ArrayList<>(Lists.reverse(hierachialTokenNamedEntityMap.get(token)));
				break;
			case MaxCoverage:
				retList = new ArrayList<>();
				for (Integer index : maxCoverageOrder) {
					retList.add(hierachialTokenNamedEntityMap.get(token).get(index));
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
