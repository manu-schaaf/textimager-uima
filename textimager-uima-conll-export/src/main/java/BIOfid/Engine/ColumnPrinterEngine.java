package BIOfid.Engine;

import BIOfid.Utility.IndexingMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.texttechnologielab.annotation.type.Fingerprint;
import org.texttechnologylab.annotation.AbstractNamedEntity;
import org.texttechnologylab.annotation.NamedEntity;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.uima.fit.util.JCasUtil.indexCovered;
import static org.apache.uima.fit.util.JCasUtil.select;

public class ColumnPrinterEngine extends JCasAnnotator_ImplBase {
	/**
	 * If true, only consider annotations coverd by a {@link Fingerprint}.
	 */
	public static final String PARAM_FILTER_FINGERPRINTED = "pFilterFingerprinted";
	@ConfigurationParameter(name = PARAM_FILTER_FINGERPRINTED, defaultValue = "true")
	private Boolean pFilterFingerprinted;
	private PrintWriter printWriter;
	
	@Override
	public void initialize(UimaContext context) throws ResourceInitializationException {
		super.initialize(context);
		try {
			printWriter = new PrintWriter(FileUtils.openOutputStream(new File("/home/stud_homes/s3676959/Projects/BioFID/temp.txt")));
		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		}
	}
	
	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {
		try {
			LinkedHashSet<String> viewNames = Streams.stream(jCas.getViewIterator())
					.map(JCas::getViewName)
					.collect(Collectors.toCollection(LinkedHashSet::new));
			
			if (viewNames.size() >= 2) {
				HashMap<String, HashMap<Integer, ArrayList<String>>> viewHierarchyMap = new HashMap<>();
				
				for (String viewName : viewNames) {
					JCas viewCas = jCas.getView(viewName);
					HashMap<Integer, ArrayList<String>> neMap = new HashMap<>();
					IndexingMap<Token> tokenIndexingMap = new IndexingMap<>();
					ArrayList<Token> vTokens = new ArrayList<>(JCasUtil.select(viewCas, Token.class));
					vTokens.forEach(tokenIndexingMap::add);
					
					HashSet<TOP> fingerprinted = select(viewCas, Fingerprint.class).stream()
							.distinct()
							.map(Fingerprint::getReference)
							.collect(Collectors.toCollection(HashSet::new));
					
					for (Class<? extends Annotation> type : Lists.newArrayList(NamedEntity.class, AbstractNamedEntity.class)) {
						Map<Annotation, Collection<Token>> neIndex = indexCovered(viewCas, type, Token.class);
						for (Map.Entry<Annotation, Collection<Token>> entry : neIndex.entrySet()) {
							Annotation ne = entry.getKey();
							if (!pFilterFingerprinted || fingerprinted.contains(ne))
								for (Token token : entry.getValue()) {
									Integer index = tokenIndexingMap.get(token);
									ArrayList<String> nes = neMap.getOrDefault(index, new ArrayList<>());
									nes.add(ne.getType().getShortName());
									neMap.put(index, nes);
								}
						}
					}
					
					for (int i = 0; i < vTokens.size(); i++) {
						if (!neMap.containsKey(i)) {
							neMap.put(i, Lists.newArrayList("O"));
						}
					}
					viewHierarchyMap.put(viewName, neMap);
				}

//				if (select(jCas, DocumentMetaData.class).size() > 0) {
				printWriter.printf("#%s", new DocumentMetaData(jCas).getDocumentId());
//				} else {
//					printWriter.printf("#??????");
//				}
				for (String name : viewNames) {
					String strippedName = StringUtils.substringAfterLast(name.trim(), "/");
					printWriter.printf("\t%s", !strippedName.equals("") ? strippedName : name);
				}
				printWriter.println();
				
				ArrayList<Token> tokens = new ArrayList<>(JCasUtil.select(jCas, Token.class));
				for (int i = 0; i < tokens.size(); i++) {
					Token tToken = tokens.get(i);
					printWriter.printf("%s", tToken.getCoveredText());
					for (String name : viewNames) {
						HashMap<Integer, ArrayList<String>> tokenArrayListHashMap = viewHierarchyMap.get(name);
						printWriter.printf("\t%s", tokenArrayListHashMap.get(i));
					}
					printWriter.println();
				}
				printWriter.println("\n");
			}
		} catch (CASException e) {
			e.printStackTrace();
		}
	}
	
	
}
