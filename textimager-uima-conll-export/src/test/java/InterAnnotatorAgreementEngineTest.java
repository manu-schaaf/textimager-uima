import BIOfid.Engine.Agreement.CodingInterAnnotatorAgreementCollectionProcessingEngine;
import BIOfid.Engine.Agreement.SetSelectionStrategy;
import BIOfid.Engine.Agreement.UnitizingInterAnnotatorAgreementCollectionProcessingEngine;
import BIOfid.Engine.ColumnPrinterEngine;
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
public class InterAnnotatorAgreementEngineTest {
	public static void main(String[] args) {
		try {
//			String[] annotators = {"303228", "22442"};
//			String xmiPath = "textimager-uima-conll-export/src/test/resources/";
			String[] annotators = {"305236", "305235"};
			String xmiPath = "textimager-uima-conll-export/src/test/out/xmi/";
			String txtPath = "textimager-uima-conll-export/src/test/out/txt/";
//			CollectionReader collection = CollectionReaderFactory.createReader(
//					TextAnnotatorRepositoryCollectionReader.class,
//					TextAnnotatorRepositoryCollectionReader.PARAM_SOURCE_LOCATION, xmiPath,
//					TextAnnotatorRepositoryCollectionReader.PARAM_TARGET_LOCATION, txtPath,
//					TextAnnotatorRepositoryCollectionReader.PARAM_SESSION_ID, "711D7EC80B746B5B76C20AB7955DB7AD", // FIXME: add session id here
//					TextAnnotatorRepositoryCollectionReader.PARAM_FORCE_RESERIALIZE, true
//			);
			CollectionReader collection = CollectionReaderFactory.createReader(
					XmiReader.class,
					XmiReader.PARAM_PATTERNS, "[+]*.xmi",
					XmiReader.PARAM_SOURCE_LOCATION, xmiPath
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
			
			ab.add(AnalysisEngineFactory.createEngineDescription(
					UnitizingInterAnnotatorAgreementCollectionProcessingEngine.class,
					UnitizingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_ANNOTATION_CLASSES, annotationClasses,
					UnitizingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_MIN_VIEWS, 2,
					UnitizingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_ANNOTATOR_LIST, annotators,
					UnitizingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_ANNOTATOR_RELATION, UnitizingInterAnnotatorAgreementCollectionProcessingEngine.WHITELIST,
					UnitizingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_FILTER_FINGERPRINTED, filterFingerprinted
			));
			ab.add(AnalysisEngineFactory.createEngineDescription(
					CodingInterAnnotatorAgreementCollectionProcessingEngine.class,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_ANNOTATION_CLASSES, annotationClasses,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_MIN_VIEWS, 2,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_ANNOTATOR_LIST, annotators,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_ANNOTATOR_RELATION, CodingInterAnnotatorAgreementCollectionProcessingEngine.WHITELIST,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_FILTER_FINGERPRINTED, filterFingerprinted,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_AGREEMENT_MEASURE, CodingInterAnnotatorAgreementCollectionProcessingEngine.KrippendorffAlphaAgreement,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_SET_SELECTION_STRATEGY, SetSelectionStrategy.ALL
			));
			ab.add(AnalysisEngineFactory.createEngineDescription(
					CodingInterAnnotatorAgreementCollectionProcessingEngine.class,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_ANNOTATION_CLASSES, annotationClasses,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_MIN_VIEWS, 2,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_ANNOTATOR_LIST, annotators,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_ANNOTATOR_RELATION, CodingInterAnnotatorAgreementCollectionProcessingEngine.WHITELIST,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_FILTER_FINGERPRINTED, filterFingerprinted,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_AGREEMENT_MEASURE, CodingInterAnnotatorAgreementCollectionProcessingEngine.KrippendorffAlphaAgreement,
					CodingInterAnnotatorAgreementCollectionProcessingEngine.PARAM_SET_SELECTION_STRATEGY, SetSelectionStrategy.MAX
			));
			
			SimplePipeline.runPipeline(collection, ab.createAggregate());
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("\nDone");
	}
}
