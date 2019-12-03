package BIOfid.BioEncoder;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import org.apache.commons.collections4.bidimap.DualLinkedHashBidiMap;
import org.apache.uima.cas.CASException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.util.CasCopier;
import org.texttechnologielab.annotation.type.Fingerprint;

import java.util.ArrayList;

import static org.apache.uima.fit.util.JCasUtil.select;

public class DKProHierarchicalBioEncoder extends GenericBioEncoder<NamedEntity> {
	
	/**
	 * DKProHierarchicalBioEncoder that filters for fingerprinted annotations and includes all {@link NamedEntity} annotations by default
	 * <p>See {@link DKProHierarchicalBioEncoder#DKProHierarchicalBioEncoder(JCas, boolean, ArrayList, ImmutableSet, Boolean)}.
	 *
	 * @param jCas The JCas to process.
	 */
	public DKProHierarchicalBioEncoder(JCas jCas, boolean pFilterFingerprinted) {
		this(jCas, pFilterFingerprinted, Lists.newArrayList(NamedEntity.class), ImmutableSet.of(), false);
	}
	
	/**
	 * DKProHierarchicalBioEncoder that includes all {@link NamedEntity} annotations by default
	 * <p>See {@link DKProHierarchicalBioEncoder#DKProHierarchicalBioEncoder(JCas, boolean, ArrayList, ImmutableSet, Boolean)}.
	 *
	 * @param jCas                 The JCas to process.
	 * @param pFilterFingerprinted If true, only fingerprinted {@link NamedEntity NamedEntities} are processed.
	 */
	public DKProHierarchicalBioEncoder(JCas jCas, boolean pFilterFingerprinted, ImmutableSet<String> annotatorList, Boolean annotatorRelation) {
		this(jCas, pFilterFingerprinted, Lists.newArrayList(NamedEntity.class), annotatorList, annotatorRelation);
	}
	
	/**
	 * An encoder for the BIO-/IOB2-format that can handle an arbitrary number of stacked annotations.
	 *
	 * @param jCas                 The JCas to process.
	 * @param pFilterFingerprinted If true, only fingerprinted {@link NamedEntity NamedEntities} are processed.
	 * @param forceAnnotations     Include all annotations of these classes.
	 */
	public DKProHierarchicalBioEncoder(JCas jCas, boolean pFilterFingerprinted, ArrayList<Class<? extends Annotation>> forceAnnotations, ImmutableSet<String> annotatorList, Boolean annotatorRelation) {
		super(jCas, pFilterFingerprinted, forceAnnotations, annotatorList, annotatorRelation);
		this.type = NamedEntity.class;
		this.build();
	}
	
	@Override
	void mergeViews(JCas mergedCas) throws CASException {
		CasCopier.copyCas(jCas.getCas(), mergedCas.getCas(), true, true);
		
		jCas.getViewIterator().forEachRemaining(viewCas -> {
			if (annotatorRelation == annotatorSet.contains(viewCas.getViewName())) {
				DualLinkedHashBidiMap<TOP, TOP> addressMap = new DualLinkedHashBidiMap<>();
				for (Annotation oAnnotation : select(viewCas, NamedEntity.class)) {
					Annotation nAnnotation = (Annotation) mergedCas.getCas().createAnnotation(oAnnotation.getType(), oAnnotation.getBegin(), oAnnotation.getEnd());
					((NamedEntity) nAnnotation).setValue(((NamedEntity) oAnnotation).getValue());
					((NamedEntity) nAnnotation).setIdentifier(((NamedEntity) oAnnotation).getIdentifier());
					
					addressMap.put(oAnnotation, nAnnotation);
					nAnnotation.addToIndexes();
				}
				
				for (Fingerprint oFingerprint : select(viewCas, Fingerprint.class)) {
					Fingerprint nFingerprint = new Fingerprint(mergedCas);
					nFingerprint.setReference(addressMap.get(oFingerprint.getReference()));
					nFingerprint.setCreate(oFingerprint.getCreate());
					nFingerprint.setUser(oFingerprint.getUser());
					
					nFingerprint.addToIndexes();
				}
			}
		});
	}
	
}
