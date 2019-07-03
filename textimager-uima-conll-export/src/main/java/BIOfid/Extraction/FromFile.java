package BIOfid.Extraction;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.commons.io.FileUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.CasIOUtils;
import org.texttechnologielab.annotation.type.Fingerprint;
import org.texttechnologylab.annotation.NamedEntity;
import org.texttechnologylab.annotation.type.QuickTreeNode;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.uima.fit.util.JCasUtil.select;

/**
 * Created on 28.01.2019.
 */
public class FromFile {
	public static void main(String[] args) {
		try {
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					ConllBIO2003Writer.class,
					ConllBIO2003Writer.PARAM_TARGET_LOCATION, "/home/stud_homes/s3676959/Documents/BioFID/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/out/TAF/conll",
					ConllBIO2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_STRATEGY_INDEX, 3,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, false,
					ConllBIO2003Writer.PARAM_EXPORT_RAW, false,
					ConllBIO2003Writer.PARAM_TARGET_ENCODING, "UTF-8",
					ConllBIO2003Writer.PARAM_USE_TTLAB_TYPESYSTEM, true
//					ConllBIO2003Writer.PARAM_EXPORT_RAW_ONLY, false,
//					ConllBIO2003Writer.PARAM_RAW_TARGET_LOCATION, "/home/s3676959/Documents/BIOfid/data/EOS/eos_plain_txt/"
			);
			
			AtomicInteger a = new AtomicInteger(0);
			String inPath = "/home/stud_homes/s3676959/Documents/BioFID/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/out/TAF/xmi/";
			try {
				File dir = new File(inPath);
				if (!dir.isDirectory())
					return;
				File[] files = Objects.requireNonNull(dir.listFiles());
				System.out.printf("Found %d files!\n", files.length);
				Arrays.stream(files).sequential().forEach(file -> {
					JCas jCas;
					try {
						jCas = JCasFactory.createJCas();
						CasIOUtils.load(FileUtils.openInputStream(file), null, jCas.getCas(), true);
						String documentName = file.getName();
						String documentId = documentName.replaceAll(".xmi", "");
						
						if (select(jCas, DocumentMetaData.class).size() == 0) {
							DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
							documentMetaData.setDocumentId(documentId);
							documentMetaData.setDocumentUri(documentName);
							documentMetaData.setDocumentTitle(documentId);
						}
						
						AtomicInteger fingerprints = new AtomicInteger(0);
						AtomicInteger qtn = new AtomicInteger(0);
						AtomicInteger nes = new AtomicInteger(0);
						jCas.getViewIterator().forEachRemaining(lCas -> {
							fingerprints.getAndAdd(select(lCas, Fingerprint.class).size());
							qtn.getAndAdd(select(lCas, QuickTreeNode.class).size());
							nes.getAndAdd(select(lCas, NamedEntity.class).size());
						});
						if (fingerprints.get() > 0 || nes.get() > 0) {
//							System.out.printf(" File %s has %d fingerprinted annotations, %d QuickTreeNodes and %d NamedEntities.\n", documentName, fingerprints.get(), qtn.get(), nes.get());
//						System.out.printf("\rProcessing file %d/%d", a.incrementAndGet(), files.length);
							conllEngine.process(jCas);
						}
						
					} catch (UIMAException | IOException e) {
						e.printStackTrace();
					}
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("\nDone");
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
}
