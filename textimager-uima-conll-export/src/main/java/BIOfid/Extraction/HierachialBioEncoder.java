package BIOfid.Extraction;

import BIOfid.Utility.CountMap;
import com.google.common.collect.Lists;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.texttechnologielab.annotation.type.Fingerprint;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.uima.fit.util.JCasUtil.*;

public class HierachialBioEncoder {
	private final HashMap<Token, ArrayList<String>> hierachialTokenNamedEntityMap;
	private final CountMap<NamedEntity> namedEntityHierachy;
	private final boolean filterFingerprinted;
	private boolean useIOB2; // FIXME
	private TreeMap<Integer, TreeSet<NamedEntity>> namedEntityByRank;
	
	private Comparator<NamedEntity> beginComparator = Comparator.comparingInt(Annotation::getBegin);
	private Comparator<NamedEntity> hierachialComparator = new Comparator<NamedEntity>() {
		@Override
		public int compare(NamedEntity o1, NamedEntity o2) {
			int cmp = Integer.compare(namedEntityHierachy.get(o1), namedEntityHierachy.get(o2));
			cmp = cmp != 0 ? cmp : Integer.compare(o1.getBegin(), o2.getBegin());
			return cmp;
		}
	};
	
	
	private enum Strategy {
		TopFirstBottomUp(0), TopDown(1), BottomUp(2);
		
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
	
	// TODO: extend to dynamic classes
	public HierachialBioEncoder(JCas jCas) {
		this(jCas, true);
	}
	
	public HierachialBioEncoder(JCas jCas, boolean pFilterFingerprinted) {
		this.hierachialTokenNamedEntityMap = new HashMap<>();
		this.namedEntityHierachy = new CountMap<>();
		this.namedEntityByRank = new TreeMap<>();
		this.useIOB2 = true;
		this.filterFingerprinted = pFilterFingerprinted;
		
		final ArrayList<NamedEntity> namedEntities = new ArrayList<>();
		if (filterFingerprinted) {
			HashSet<TOP> fingerprinted = select(jCas, Fingerprint.class).stream().map(Fingerprint::getReference).collect(Collectors.toCollection(HashSet::new));
			select(jCas, NamedEntity.class).stream().filter(fingerprinted::contains).forEach(namedEntities::add);
//			CasUtil.indexCovered(jCas.getCas(), jCas.getCasType(Fingerprint.type), jCas.getCasType(NamedEntity.type)).values().forEach();
		} else {
			namedEntities.addAll(select(jCas, NamedEntity.class));
		}
		
		namedEntities.forEach(key -> namedEntityHierachy.put(key, 0));
		
		for (NamedEntity namedEntity : namedEntities) {
//			System.out.printf("%s <%s>\n", namedEntity.getCoveredText(), namedEntity.getValue());
			//						System.out.printf("\t%s <%s>\n", key.getCoveredText(), key.getValue());
			JCasUtil.subiterate(jCas, NamedEntity.class, namedEntity, true, false)
					.forEach(key -> {
						if (namedEntities.contains(key))
							namedEntityHierachy.put(key, namedEntityHierachy.get(namedEntity) + 1);
					});
		}

//		namedEntityHierachy.forEach((key, value) -> System.out.printf("%s %d\n", key.getCoveredText(), value)); // FIXME
		
		for (Map.Entry<NamedEntity, Integer> entry : namedEntityHierachy.entrySet()) {
			NamedEntity ne = entry.getKey();
			int rank = entry.getValue();
			
			TreeSet<NamedEntity> treeSet = namedEntityByRank.getOrDefault(rank, new TreeSet<>(beginComparator));
			treeSet.add(ne);
			namedEntityByRank.put(rank, treeSet);
		}
		
		ArrayList<Token> tokens = new ArrayList<>(select(jCas, Token.class));
		for (Token token : tokens) {
			hierachialTokenNamedEntityMap.put(token, new ArrayList<>());
		}
		
		stackingApproach(jCas, tokens);
//		tokenInConflictApproach(jCas, tokens);
	}
	
	private void stackingApproach(JCas jCas, ArrayList<Token> tokens) {
		Map<NamedEntity, Collection<Token>> tokenNeIndex = indexCovered(jCas, NamedEntity.class, Token.class);
		for (TreeSet<NamedEntity> rankSet : namedEntityByRank.values()) {
			HashSet<Token> rankCoveredTokens = new HashSet<>();
			
			for (NamedEntity namedEntity : rankSet) {
				ArrayList<Token> coveredTokens = new ArrayList<>(tokenNeIndex.get(namedEntity));
				if (!coveredTokens.isEmpty() && rankCoveredTokens.contains(coveredTokens.get(0)))
					continue;
				for (Token coveredToken : coveredTokens) {
					ArrayList<String> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(coveredToken);
					namedEntityStringTreeMap.add(getBioCode(namedEntity, coveredToken));
				}
				rankCoveredTokens.addAll(coveredTokens);
			}
			
			ArrayList<Token> notCoveredTokens = new ArrayList<>(tokens);
			notCoveredTokens.removeAll(rankCoveredTokens);
			for (Token notCoveredToken : notCoveredTokens) {
				ArrayList<String> namedEntityStringTreeMap = hierachialTokenNamedEntityMap.get(notCoveredToken);
				namedEntityStringTreeMap.add("O");
			}
		}
	}
	
	private void tokenInConflictApproach(JCas jCas, ArrayList<Token> tokens) {
		HashMap<Token, TreeSet<NamedEntity>> tokenNeMap = new HashMap<>();
		tokens.forEach(token -> tokenNeMap.put(token, new TreeSet<>(hierachialComparator)));
		
		Map<NamedEntity, Collection<Token>> tokenNeIndex = indexCovered(jCas, NamedEntity.class, Token.class);
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
	
	private String getBioCode(NamedEntity namedEntity, Token token) {
		if (namedEntity.getBegin() == token.getBegin() == useIOB2) {
			return "B-" + namedEntity.getValue().replaceAll("[IB\\-]*", ""); // FIXME
		} else {
			return "I-" + namedEntity.getValue().replaceAll("[IB\\-]*", ""); // FIXME
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
		}
		
		return retList;
	}
}
