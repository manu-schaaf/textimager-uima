
/* First created by JCasGen Wed Jan 22 14:27:43 CET 2020 */
package de.tudarmstadt.ukp.dkpro.core.api.ner.type;

import org.apache.uima.cas.Type;
import org.apache.uima.cas.impl.TypeImpl;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.JCasRegistry;

/**
 * Updated by JCasGen Wed Jan 22 14:27:43 CET 2020
 * @generated */
public class Date_Type extends NamedEntity_Type {
  /** @generated */
  @SuppressWarnings ("hiding")
  public final static int typeIndexID = Date.typeIndexID;
  /** @generated
     @modifiable */
  @SuppressWarnings ("hiding")
  public final static boolean featOkTst = JCasRegistry.getFeatOkTst("de.tudarmstadt.ukp.dkpro.core.api.ner.type.Date");



  /** initialize variables to correspond with Cas Type and Features
	 * @generated
	 * @param jcas JCas
	 * @param casType Type
	 */
  public Date_Type(JCas jcas, Type casType) {
    super(jcas, casType);
    casImpl.getFSClassRegistry().addGeneratorForType((TypeImpl)this.casType, getFSGenerator());

  }
}



