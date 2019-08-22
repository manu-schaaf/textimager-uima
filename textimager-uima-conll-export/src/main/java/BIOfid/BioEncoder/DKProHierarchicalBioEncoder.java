package BIOfid.BioEncoder;

import com.google.common.collect.Lists;
import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.texttechnologylab.annotation.type.Taxon;

import java.util.ArrayList;

public class DKProHierarchicalBioEncoder extends GenericBioEncoder<NamedEntity> {
	
	/**
	 * DKProHierarchicalBioEncoder that filters for fingerprinted annotations and includes all {@link Taxon} annotations by default
	 * <p>See {@link DKProHierarchicalBioEncoder#DKProHierarchicalBioEncoder(JCas, boolean, ArrayList)}.
	 *
	 * @param jCas The JCas to process.
	 */
	public DKProHierarchicalBioEncoder(JCas jCas) {
		this(jCas, true, Lists.newArrayList(Taxon.class));
	}
	
	/**
	 * DKProHierarchicalBioEncoder that includes all {@link Taxon} annotations by default
	 * <p>See {@link DKProHierarchicalBioEncoder#DKProHierarchicalBioEncoder(JCas, boolean, ArrayList)}.
	 *
	 * @param jCas                 The JCas to process.
	 * @param pFilterFingerprinted If true, only fingerprinted {@link NamedEntity NamedEntities} are processed.
	 */
	public DKProHierarchicalBioEncoder(JCas jCas, boolean pFilterFingerprinted) {
		this(jCas, pFilterFingerprinted, Lists.newArrayList(Taxon.class));
	}
	
	/**
	 * An encoder for the BIO-/IOB2-format that can handle an arbitrary number of stacked annotations.
	 *
	 * @param jCas                 The JCas to process.
	 * @param pFilterFingerprinted If true, only fingerprinted {@link NamedEntity NamedEntities} are processed.
	 * @param forceAnnotations     Include all annotations of these classes.
	 */
	public DKProHierarchicalBioEncoder(JCas jCas, boolean pFilterFingerprinted, ArrayList<Class<? extends Annotation>> forceAnnotations) {
		super(jCas, pFilterFingerprinted, forceAnnotations);
		this.type = NamedEntity.class;
		this.build();
	}
	
}
