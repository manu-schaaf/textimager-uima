package BIOfid.Extraction;

import BIOfid.Engine.ConllBIO2003Writer;
import BIOfid.Engine.Agreement.UnitizingInterAnnotatorAgreementCollectionProcessingEngine;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.texttechnologylab.annotation.AbstractNamedEntity;
import org.texttechnologylab.annotation.NamedEntity;

/**
 * Created on 28.01.2019.
 */
public class ExtractConllFromFile {
	public static void main(String[] args) {
		try {
			final AnalysisEngineDescription conllEngineDescription = AnalysisEngineFactory.createEngineDescription(
					ConllBIO2003Writer.class,
					ConllBIO2003Writer.PARAM_TARGET_LOCATION, "/home/stud_homes/s3676959/Documents/BioFID/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/out/TAF/conll",
					ConllBIO2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_STRATEGY_INDEX, 3,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, true,
					ConllBIO2003Writer.PARAM_EXPORT_RAW, false,
					ConllBIO2003Writer.PARAM_TARGET_ENCODING, "UTF-8",
					ConllBIO2003Writer.PARAM_USE_TTLAB_TYPESYSTEM, true
//					ConllBIO2003Writer.PARAM_EXPORT_RAW_ONLY, false,
//					ConllBIO2003Writer.PARAM_RAW_TARGET_LOCATION, "/home/s3676959/Documents/BIOfid/data/EOS/eos_plain_txt/"
			);
			
			String inPath = "/home/stud_homes/s3676959/Documents/BioFID/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/out/TAF/xmi/";
			try {
				
				
				CollectionReader collection = CollectionReaderFactory.createReader(
						XmiReader.class,
						XmiReader.PARAM_SOURCE_LOCATION, inPath,
						XmiReader.PARAM_PATTERNS, "*.xmi",
						XmiReader.PARAM_LENIENT, true,
						XmiReader.PARAM_LOG_FREQ, 0 // FIXME
				);
				
				AggregateBuilder ab = new AggregateBuilder();
				ab.add(conllEngineDescription);
				ab.add(AnalysisEngineFactory.createEngineDescription(
						UnitizingInterAnnotatorAgreementCollectionProcessingEngine.class,
						UnitizingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_ANNOTATION_CLASSES, new String[]{NamedEntity.class.getName(), AbstractNamedEntity.class.getName()},
//						InterAnnotatorAgreementEngine.PARAM_EXCLUDE_ANNOTATORS, new String[]{"302902"},
						UnitizingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_MIN_VIEWS, 2
//						InterAnnotatorAgreementEngine.PARAM_TARGET_LOCATION, "test",
//						InterAnnotatorAgreementEngine.PARAM_OVERWRITE, true)
				));
				SimplePipeline.runPipeline(collection, ab.createAggregate());
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("\nDone");
		} catch (UIMAException e) {
			e.printStackTrace();
		}
	}
}
