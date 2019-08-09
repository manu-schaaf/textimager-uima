package BIOfid.Extraction;

import BIOfid.Engine.*;
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
		String xmiPath = "/home/stud_homes/s3676959/Documents/BioFID/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/out/TAF/xmi/";
		String txtPath = "/home/stud_homes/s3676959/Documents/BioFID/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/out/TAF/txt/";
		try {
			CollectionReader collection = CollectionReaderFactory.createReader(
					TextAnnotatorRepositoryCollectionReader.class,
					TextAnnotatorRepositoryCollectionReader.PARAM_SOURCE_LOCATION, xmiPath,
					TextAnnotatorRepositoryCollectionReader.PARAM_TEXT_LOCATION, txtPath,
					TextAnnotatorRepositoryCollectionReader.PARAM_SESSION_ID, "711D7EC80B746B5B76C20AB7955DB7AD.jvm1",
					TextAnnotatorRepositoryCollectionReader.PARAM_FORCE_RESERIALIZE, true
			);
//			CollectionReader collection = CollectionReaderFactory.createReader(
//					XmiCollectionReader.class,
//					XmiCollectionReader.PARAM_SOURCE_LOCATION, xmiPath
//			);
			
			AggregateBuilder ab = new AggregateBuilder();
			ab.add(AnalysisEngineFactory.createEngineDescription(
					ColumnPrinterEngine.class,
					ColumnPrinterEngine.PARAM_FILTER_FINGERPRINTED, true
			));

			String[] annotationClasses = {NamedEntity.class.getName(), AbstractNamedEntity.class.getName()};
			String[] excludedAnnotators = {"302902", "303228"};
//			ab.add(AnalysisEngineFactory.createEngineDescription(
//					UnitizingInterAnnotatorAgreementEngine.class,
//					UnitizingInterAnnotatorAgreementEngine.PARAM_ANNOTATION_CLASSES, annotationClasses,
//					UnitizingInterAnnotatorAgreementEngine.PARAM_EXCLUDE_ANNOTATORS, excludedAnnotators,
//					UnitizingInterAnnotatorAgreementEngine.PARAM_MIN_VIEWS, 2,
//					UnitizingInterAnnotatorAgreementEngine.PARAM_FILTER_FINGERPRINTED, false
//			));
			ab.add(AnalysisEngineFactory.createEngineDescription(
					CodingInterAnnotatorAgreementEngine.class,
					CodingInterAnnotatorAgreementEngine.PARAM_ANNOTATION_CLASSES, annotationClasses,
					CodingInterAnnotatorAgreementEngine.PARAM_EXCLUDE_ANNOTATORS, excludedAnnotators,
					CodingInterAnnotatorAgreementEngine.PARAM_MIN_VIEWS, 2,
					CodingInterAnnotatorAgreementEngine.PARAM_FILTER_FINGERPRINTED, false
			));
			SimplePipeline.runPipeline(collection, ab.createAggregate());
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("\nDone");
	}
}
