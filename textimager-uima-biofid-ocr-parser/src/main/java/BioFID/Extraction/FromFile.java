package BioFID.Extraction;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.jcas.JCas;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Created on 28.01.2019.
 */
public class FromFile {
	public static void main(String[] args) {
		try {
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					ConllBIO2003Writer.class,
					ConllBIO2003Writer.PARAM_TARGET_LOCATION, "/home/s3676959/Documents/BioFID/data/NER/annotated_conll/",
					ConllBIO2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_STRATEGY_INDEX, 1,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, true,
					ConllBIO2003Writer.PARAM_EXPORT_RAW, true,
					ConllBIO2003Writer.PARAM_EXPORT_RAW_ONLY, false,
					ConllBIO2003Writer.PARAM_RAW_TARGET_LOCATION, "/home/s3676959/Documents/BioFID/data/EOS/eos_plain_txt/"
			);
			
			int[] a = {0};
			String inPath = "/home/s3676959/Documents/BioFID/data/NER/annotated_xmi/";
			try {
				File dir = new File(inPath);
				if (!dir.isDirectory())
					return;
				Arrays.stream(Objects.requireNonNull(dir.listFiles())).parallel().forEach(utf8File -> {
					JCas jCas = null;
					try {
						jCas = JCasFactory.createJCas();
						CasIOUtil.readXmi(jCas, utf8File);
						
						DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
						documentMetaData.setDocumentId(utf8File.getName().replaceAll(".xmi", ""));
						documentMetaData.setDocumentUri(utf8File.getName().replaceAll(".xmi", ""));
						
						conllEngine.process(jCas);
						System.out.printf("\r File %d/%d", a[0]++, Objects.requireNonNull(dir.listFiles()).length);
					} catch (UIMAException | IOException e) {
						e.printStackTrace();
					}
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
}
