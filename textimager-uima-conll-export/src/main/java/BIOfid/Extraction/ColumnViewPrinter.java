package BIOfid.Extraction;

import BIOfid.Engine.ColumnPrinterEngine;
import BIOfid.Engine.InterAnnotatorAgreementEngine;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
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
public class ColumnViewPrinter {
	public static void main(String[] args) {
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
			ab.add(AnalysisEngineFactory.createEngineDescription(ColumnPrinterEngine.class,
					ColumnPrinterEngine.PARAM_FILTER_FINGERPRINTED, false));
			ab.add(AnalysisEngineFactory.createEngineDescription(
					InterAnnotatorAgreementEngine.class,
					InterAnnotatorAgreementEngine.PARAM_ANNOTATION_CLASSES, new String[]{NamedEntity.class.getName(), AbstractNamedEntity.class.getName()},
					InterAnnotatorAgreementEngine.PARAM_EXCLUDE_ANNOTATORS, new String[]{"302902"},
					InterAnnotatorAgreementEngine.PARAM_DISCARD_SINGLE_VIEW, false
			));
			SimplePipeline.runPipeline(collection, ab.createAggregate());
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("\nDone");
	}
}
