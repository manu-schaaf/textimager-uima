package BIOfid.Run;

import BIOfid.Extraction.ConllBIO2003Writer;
import de.tudarmstadt.ukp.dkpro.core.io.xmi.XmiReader;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.biofid.agreement.engine.TTLabUnitizingIAACollectionProcessingEngine;
import org.texttechnologylab.annotation.AbstractNamedEntity;
import org.texttechnologylab.annotation.NamedEntity;
import org.texttechnologylab.utilities.uima.reader.TextAnnotatorRepositoryCollectionReader;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created on 12.12.19.
 */
public class BIOfidColumnDatasetExtract {
	public static void main(String[] args) {
		Options options = new Options();
		options.addOption("h", "Print this message.");
		
		Option xmiSourceLocationOption = new Option("i", "input", true,
				"XMI file source path or path to write the XMIs to if fetching from the TextAnnotator. Required.");
		xmiSourceLocationOption.setRequired(true);
		options.addOption(xmiSourceLocationOption);
		
		Option fetchFromTaOption = new Option("ta", true,
				"Fetch XMIs from the TextAnnotator. " +
						"Takes exactly two arguments: REPOSITORY SESSION_ID");
		fetchFromTaOption.setRequired(false);
		fetchFromTaOption.setArgs(2);
		options.addOption(fetchFromTaOption);
		
		Option conllTargetLocationOption = new Option("o", "out", true,
				"CoNLL file output directory. Required.");
		conllTargetLocationOption.setRequired(true);
		options.addOption(conllTargetLocationOption);
		
		Option annotatorListOption = new Option("ids", true,
				"List of annotator ids, whitelisted by default. Required.");
		annotatorListOption.setRequired(true);
		annotatorListOption.setArgs(-2);
		options.addOption(annotatorListOption);
		
		Option blacklistAnnotatorsOption = new Option("blacklist", false,
				"If set, blacklist the given annotators instead of whitelisting them.");
		blacklistAnnotatorsOption.setRequired(false);
		options.addOption(blacklistAnnotatorsOption);
		
		Option iaaOption = new Option("iaa", "agreement", true,
				"Minimum inter-annotator agreement value for each category, per file.");
		iaaOption.setRequired(false);
		iaaOption.setType(Float.class);
		options.addOption(iaaOption);
		
		Option datasetOption = new Option("dataset", true,
				"If set, create a test/training dataset. " +
						"The option takes the target path as the first argument and " +
						"ids that belong to the test spilt for all remaining arguments. " +
						"Takes at least two arguments: DATASET_PATH [TEST_FILE_ID]");
		datasetOption.setRequired(false);
		datasetOption.setArgs(-2);
		options.addOption(datasetOption);
		
		try {
			DefaultParser defaultParser = new DefaultParser();
			CommandLine commandLine = defaultParser.parse(options, args);
			
			String sourceLocation = commandLine.getOptionValue("i");
			String conllTargetLocation = commandLine.getOptionValue("o");
			String[] annotatorWhitelist = commandLine.getOptionValues("a");
			Boolean whitelist = !commandLine.hasOption("blacklist");
			float minIaaScore = Float.parseFloat(commandLine.getOptionValue("iaa", "-1.0"));
			
			CollectionReader reader;
			if (commandLine.hasOption("ta")) {
				String[] taArguments = commandLine.getOptionValues("ta");
				if (taArguments.length != 2) {
					throw new IllegalArgumentException(""); // FIXME
				}
				reader = CollectionReaderFactory.createReader(TextAnnotatorRepositoryCollectionReader.class,
						TextAnnotatorRepositoryCollectionReader.PARAM_SOURCE_LOCATION, sourceLocation,
						TextAnnotatorRepositoryCollectionReader.PARAM_DOCUMENTS_REPOSITORY, taArguments[0],
						TextAnnotatorRepositoryCollectionReader.PARAM_SESSION_ID, taArguments[1]
//						, TextAnnotatorRepositoryCollectionReader.PARAM_FORCE_RESERIALIZE, false
//						TextAnnotatorRepositoryCollectionReader.PARAM_TARGET_LOCATION, "/home/stud_homes/s3676959/Data/BIOfid/Annotated/txt/",
//						TextAnnotatorRepositoryCollectionReader.PARAM_DOCUMENTS_REPOSITORY, "19147",
//						TextAnnotatorRepositoryCollectionReader.PARAM_SESSION_ID, "3B1A1380E80D4F8C2682246EB9F7B0C7"
				);
			} else {
				reader = CollectionReaderFactory.createReader(XmiReader.class,
						XmiReader.PARAM_PATTERNS, "[+]**.xmi",
						XmiReader.PARAM_SOURCE_LOCATION, sourceLocation,
						XmiReader.PARAM_LENIENT, true
				);
			}
			
			final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
					ConllBIO2003Writer.class,
					ConllBIO2003Writer.PARAM_TARGET_LOCATION, conllTargetLocation,
					ConllBIO2003Writer.PARAM_OVERWRITE, true,
					ConllBIO2003Writer.PARAM_USE_TTLAB_TYPESYSTEM, true,
					ConllBIO2003Writer.PARAM_STRATEGY_INDEX, 1,
					ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, true,
					ConllBIO2003Writer.PARAM_ANNOTATOR_LIST, annotatorWhitelist,
					ConllBIO2003Writer.PARAM_ANNOTATOR_RELATION, whitelist,
					ConllBIO2003Writer.PARAM_MIN_VIEWS, 1,
					ConllBIO2003Writer.PARAM_FILTER_BY_AGREEMENT, minIaaScore,
					ConllBIO2003Writer.PARAM_FILTER_EMPTY_SENTENCES, true);
			
			if (minIaaScore > 0.0) {
				final AnalysisEngine agreementEngine = AnalysisEngineFactory.createEngine(TTLabUnitizingIAACollectionProcessingEngine.class,
						TTLabUnitizingIAACollectionProcessingEngine.PARAM_ANNOTATOR_LIST, annotatorWhitelist,
						TTLabUnitizingIAACollectionProcessingEngine.PARAM_ANNOTATOR_RELATION, whitelist,
						TTLabUnitizingIAACollectionProcessingEngine.PARAM_MULTI_CAS_HANDLING, TTLabUnitizingIAACollectionProcessingEngine.SEPARATE,
						TTLabUnitizingIAACollectionProcessingEngine.PARAM_PRINT_STATS, false,
						TTLabUnitizingIAACollectionProcessingEngine.PARAM_MIN_VIEWS, 2,
						TTLabUnitizingIAACollectionProcessingEngine.PARAM_MIN_ANNOTATIONS, 0,
						TTLabUnitizingIAACollectionProcessingEngine.PARAM_ANNOTATE_DOCUMENT, true,
						TTLabUnitizingIAACollectionProcessingEngine.PARAM_ANNOTATION_CLASSES, new String[]{NamedEntity.class.getName(), AbstractNamedEntity.class.getName()}
				);
				
				SimplePipeline.runPipeline(reader, agreementEngine, conllEngine);
			} else {
				SimplePipeline.runPipeline(reader, conllEngine);
			}
			
			if (commandLine.hasOption("dataset")) {
				String[] datasetValues = commandLine.getOptionValues("dataset");
				String datasetDir = Paths.get(datasetValues[0]).toString();
				
				HashSet<String> testIdSet = Arrays.stream(datasetValues).skip(1)
						.map(s -> StringUtils.substringBeforeLast(s, "."))
						.collect(Collectors.toCollection(HashSet::new));
				
				HashMap<String, File> idFileMap = Arrays.stream(Objects.requireNonNull(Paths.get(conllTargetLocation).toFile().listFiles()))
						.filter(File::isFile)
						.filter(f -> StringUtils.endsWith(f.getName(), ".conll"))
						.collect(Collectors.toMap(
								k -> StringUtils.substringBeforeLast(k.getName(), "."),
								Function.identity(),
								(u, v) -> u, HashMap::new)
						);
				
				// Concatenate and print test files
				Path testPath = Paths.get(datasetDir, "test.conll");
				try (PrintWriter pw = new PrintWriter(Files.newOutputStream(testPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
					for (String id : testIdSet) {
						pw.write(FileUtils.readFileToString(idFileMap.get(id), StandardCharsets.UTF_8));
					}
				}
				System.out.println(String.format("Concatenated %d test files.", testIdSet.size()));
				
				// Concatenate and print training files
				Path trainPath = Paths.get(datasetDir, "train.conll");
				try (PrintWriter pw = new PrintWriter(Files.newOutputStream(trainPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
					idFileMap.keySet().removeAll(testIdSet);
					for (File file : idFileMap.values()) {
						pw.write(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
					}
				}
				System.out.println(String.format("Concatenated %d training files.", idFileMap.size()));
			}
		} catch (UIMAException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			if (!Arrays.asList(args).contains("-h"))
				System.err.println(e.getMessage());
			printHelp(options);
		}
	}
	
	private static void printHelp(Options options) {
		HelpFormatter helpFormatter = new HelpFormatter();
		helpFormatter.printHelp(
				"java -cp $CLASSPATH BIOfid.Run.BIOfidColumnDatasetExtract -i $XMI_PATH -o $CONLL_PATH -ids [$ANNOTATOR_IDS] [args]",
				"Reads a list of XMIs or fetch a repository from the TextAnnotator and convert them to CoNLL BIO format. " +
						"Optionally creates a training/test dataset from the extracted CoNLL files.",
				options,
				"Example arguments:"
		);
		System.out.println("\t-i $XMI_PATH -o $CONLL_PATH -ids 305236 305235 -iaa 0.6 -dataset $DATASET_PATH 3720448");
		System.out.println("\t-i $XMI_PATH -o $CONLL_PATH -ids 305236 305235 -iaa 0.6 -dataset $DATASET_PATH 3720448 -ta 19147 $SESSION_ID"
		);
	}
}
