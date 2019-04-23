package org.hucompute.textimager.biofid;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.apache.commons.cli.*;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * Created on 18.04.2019.
 */
public class TagTaxa {
	public static void main(String[] args) {
		Options options = new Options();

		options.addOption("h", "help", false, "Print this message.");
		Option inputOption = new Option("i", "input", true, "Input root paths.");
		inputOption.setArgs(Option.UNLIMITED_VALUES);
		options.addOption(inputOption);
		options.addOption("o", "output", true, "Output path.");
		options.addOption("t", "taxa", true, "Taxa list path.");
		options.addOption("m", "minlength", true, "Taxa minimum length. Default: 5.");
		options.addOption("l", "lowercase", false, "Optional, if true use lowercase.");

		try {
			CommandLineParser parser = new DefaultParser();
			CommandLine cmd = parser.parse(options, args);

			if (cmd.hasOption("h")) {
				printUsage(options);
				return;
			}

			ArrayList<String> inputLocations = Lists.newArrayList(cmd.getOptionValue("i"));
			String taxaLocation = cmd.getOptionValue("t");
			String outputLocation = cmd.getOptionValue("o");
			Boolean useLowerCase = Boolean.valueOf(cmd.getOptionValue("l"));

			final AnalysisEngine naiveTaggerEngine = AnalysisEngineFactory.createEngine(AnalysisEngineFactory.createEngineDescription(NaiveStringbasedTaxonTagger.class,
					NaiveStringbasedTaxonTagger.PARAM_SOURCE_LOCATION, taxaLocation,
					NaiveStringbasedTaxonTagger.PARAM_USE_LOWERCASE, useLowerCase),
					NaiveStringbasedTaxonTagger.PARAM_MIN_LENGTH, 5);

			for (String inputLocation : inputLocations) {
				for (File file : Files.fileTraverser().breadthFirst(new File(inputLocation))) {
					try {
						JCas jCas = JCasFactory.createJCas();
						CasIOUtil.readXmi(jCas, file);

						SimplePipeline.runPipeline(jCas, naiveTaggerEngine);
						XmiCasSerializer.serialize(jCas.getCas(), new FileOutputStream(Paths.get(outputLocation, file.getName()).toFile()));
					} catch (UIMAException | IOException | SAXException e) {
						e.printStackTrace();
					}
				}
			}
		} catch (ParseException | ResourceInitializationException e) {
			e.printStackTrace();
		}
	}

	private static void printUsage(Options options) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("java -cp $CP org.hucompute.textimager.biofid.TagTaxa",
				"TODO", //TODO
				options,
				"",
				true);
	}
}
