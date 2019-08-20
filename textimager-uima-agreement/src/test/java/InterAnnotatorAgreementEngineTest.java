import BIOfid.Engine.Agreement.CodingIAACollectionProcessingEngine;
import BIOfid.Engine.Agreement.SetSelectionStrategy;
import BIOfid.Engine.ColumnPrinterEngine;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.AggregateBuilder;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.junit.jupiter.api.Test;
import org.texttechnologylab.annotation.AbstractNamedEntity;
import org.texttechnologylab.annotation.NamedEntity;

/**
 * Created on 28.01.2019.
 */
public class InterAnnotatorAgreementEngineTest {
	@Test
	public void testAnnotatorAgreement() {
		try {
//			String[] annotators = {"303228", "22442"};
//			String xmiPath = "src/test/resources/";
			String[] annotators = {"305236", "305235"};
			String xmiPath = "src/test/out/xmi/";
//			String txtPath = "src/test/out/txt/";
//			CollectionReader collection = CollectionReaderFactory.createReader(
//					TextAnnotatorRepositoryCollectionReader.class,
//					TextAnnotatorRepositoryCollectionReader.PARAM_SOURCE_LOCATION, xmiPath,
//					TextAnnotatorRepositoryCollectionReader.PARAM_TARGET_LOCATION, txtPath,
//					TextAnnotatorRepositoryCollectionReader.PARAM_SESSION_ID, "711D7EC80B746B5B76C20AB7955DB7AD", // FIXME: add session id here
//					TextAnnotatorRepositoryCollectionReader.PARAM_FORCE_RESERIALIZE, true
//					, XmiReader.PARAM_LOG_FREQ, -1
//			);
			CollectionReader collection = CollectionReaderFactory.createReader(
					XmiReader.class,
					XmiReader.PARAM_PATTERNS, "[+]3713524.xmi",
					XmiReader.PARAM_SOURCE_LOCATION, xmiPath,
					XmiReader.PARAM_LOG_FREQ, -1
			);
			
			AggregateBuilder ab = new AggregateBuilder();
			
			// Test parameters
			boolean filterFingerprinted = false;
			String[] annotationClasses = {NamedEntity.class.getName(), AbstractNamedEntity.class.getName()};
			
			ab.add(AnalysisEngineFactory.createEngineDescription(
					ColumnPrinterEngine.class,
					ColumnPrinterEngine.PARAM_TARGET_LOCATION, "/tmp/ttemp.txt",
					ColumnPrinterEngine.PARAM_MIN_VIEWS, 2,
					ColumnPrinterEngine.PARAM_ANNOTATOR_LIST, annotators,
					ColumnPrinterEngine.PARAM_ANNOTATOR_RELATION, ColumnPrinterEngine.WHITELIST,
					ColumnPrinterEngine.PARAM_FILTER_FINGERPRINTED, filterFingerprinted
			));

//			ab.add(AnalysisEngineFactory.createEngineDescription(
//					TTLabUnitizingIAACollectionProcessingEngine.class,
//					TTLabUnitizingIAACollectionProcessingEngine.PARAM_ANNOTATION_CLASSES, annotationClasses,
//					TTLabUnitizingIAACollectionProcessingEngine.PARAM_INCLUDE_FLAGS, new String[]{TTLabUnitizingIAACollectionProcessingEngine.METAPHOR, TTLabUnitizingIAACollectionProcessingEngine.METONYM},
//					TTLabUnitizingIAACollectionProcessingEngine.PARAM_MIN_VIEWS, 2,
//					TTLabUnitizingIAACollectionProcessingEngine.PARAM_ANNOTATOR_LIST, annotators,
//					TTLabUnitizingIAACollectionProcessingEngine.PARAM_ANNOTATOR_RELATION, UnitizingIAACollectionProcessingEngine.WHITELIST,
//					TTLabUnitizingIAACollectionProcessingEngine.PARAM_FILTER_FINGERPRINTED, filterFingerprinted,
//					TTLabUnitizingIAACollectionProcessingEngine.PARAM_MULTI_CAS_HANDLING, TTLabUnitizingIAACollectionProcessingEngine.COMBINED
//			));
			ab.add(AnalysisEngineFactory.createEngineDescription(
					CodingIAACollectionProcessingEngine.class,
					CodingIAACollectionProcessingEngine.PARAM_ANNOTATION_CLASSES, annotationClasses,
					CodingIAACollectionProcessingEngine.PARAM_MIN_VIEWS, 2,
					CodingIAACollectionProcessingEngine.PARAM_ANNOTATOR_LIST, annotators,
					CodingIAACollectionProcessingEngine.PARAM_ANNOTATOR_RELATION, CodingIAACollectionProcessingEngine.WHITELIST,
					CodingIAACollectionProcessingEngine.PARAM_FILTER_FINGERPRINTED, filterFingerprinted,
					CodingIAACollectionProcessingEngine.PARAM_AGREEMENT_MEASURE, CodingIAACollectionProcessingEngine.KrippendorffAlphaAgreement,
					CodingIAACollectionProcessingEngine.PARAM_SET_SELECTION_STRATEGY, SetSelectionStrategy.ALL,
					CodingIAACollectionProcessingEngine.PARAM_MULTI_CAS_HANDLING, CodingIAACollectionProcessingEngine.SEPARATE,
					CodingIAACollectionProcessingEngine.PARAM_ANNOTATE, true
			));
//			ab.add(AnalysisEngineFactory.createEngineDescription(
//					CodingIAACollectionProcessingEngine.class,
//					CodingIAACollectionProcessingEngine.PARAM_ANNOTATION_CLASSES, annotationClasses,
//					CodingIAACollectionProcessingEngine.PARAM_MIN_VIEWS, 2,
//					CodingIAACollectionProcessingEngine.PARAM_ANNOTATOR_LIST, annotators,
//					CodingIAACollectionProcessingEngine.PARAM_ANNOTATOR_RELATION, CodingIAACollectionProcessingEngine.WHITELIST,
//					CodingIAACollectionProcessingEngine.PARAM_FILTER_FINGERPRINTED, filterFingerprinted,
//					CodingIAACollectionProcessingEngine.PARAM_AGREEMENT_MEASURE, CodingIAACollectionProcessingEngine.KrippendorffAlphaAgreement,
//					CodingIAACollectionProcessingEngine.PARAM_SET_SELECTION_STRATEGY, SetSelectionStrategy.MAX
//			));
			
			SimplePipeline.runPipeline(collection, ab.createAggregate());
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("\nDone");
	}
}
