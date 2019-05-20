package org.hucompute.textimager.biofid;

import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.UIMARuntimeException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasIOUtils;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.apache.commons.math3.util.FastMath.log10;

/**
 * Created on 18.04.2019.
 */
public class TagTaxa {
	public static void main(String[] args) {
		
		Option inputOption = new Option("i", "input", true, "Input root paths.");
		inputOption.setArgs(Option.UNLIMITED_VALUES);
		
		Option outputOption = new Option("o", "output", true, "Output path.");
		
		Option taxaOption = new Option("t", "taxa", true, "Taxa list path.");
		taxaOption.setArgs(Option.UNLIMITED_VALUES);
		
		Option minLen = new Option("m", "minlength", true, "Taxa minimum length. Default: 5.");
		minLen.setRequired(false);
		
		Options options = new Options();
		options.addOption("h", "help", false, "Print this message.");
		options.addOption(inputOption);
		options.addOption(outputOption);
		options.addOption(taxaOption);
		options.addOption(minLen);
		options.addOption("l", "lowercase", false, "Optional, if true use lowercase.");
		
		try {
			CommandLineParser parser = new DefaultParser();
			CommandLine cmd = parser.parse(options, args);
			
			if (cmd.hasOption("h")) {
				printUsage(options);
				return;
			}
			
			ArrayList<String> inputLocations = Lists.newArrayList(cmd.getOptionValues("i"));
			String[] taxaLocations = cmd.getOptionValues("t");
			String outputLocation = cmd.getOptionValue("o");
			Boolean useLowerCase = cmd.hasOption("l");
			Integer minLength = cmd.hasOption("m") ? Integer.valueOf(cmd.getOptionValue("m")) : 5;
			
			final AnalysisEngine naiveTaggerEngine = AnalysisEngineFactory.createEngine(
					AnalysisEngineFactory.createEngineDescription(NaiveStringbasedTaxonTagger.class,
							NaiveStringbasedTaxonTagger.PARAM_SOURCE_LOCATION, taxaLocations,
							NaiveStringbasedTaxonTagger.PARAM_USE_LOWERCASE, useLowerCase,
							NaiveStringbasedTaxonTagger.PARAM_MIN_LENGTH, minLength)
			);
			
			AtomicInteger count = new AtomicInteger(0);
			Stream<File> fileStream = Stream.empty();
			for (String inputLocation : inputLocations) {
				fileStream = Streams.concat(fileStream,
						Streams.stream(Files.fileTraverser().breadthFirst(new File(inputLocation)))
								.filter(File::isFile));
			}
			File[] files = fileStream.sorted(Comparator.comparing(FileUtils::sizeOf)).toArray(File[]::new);
			int allCount = files.length;
			for (File file : files) {
				// TODO: does not work...
				Path outFilePath = Paths.get(outputLocation, file.getName());
				try (FileLock fileLock = FileChannel.open(outFilePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW).tryLock()) {
					if (fileLock == null || !fileLock.isValid()) {
						System.out.printf("\rSkipped file %0" + (int) (log10(allCount) + 1) + "d/%d", count.incrementAndGet(), allCount);
						continue;
					}
					
					System.out.printf("\rRunning file %0" + (int) (log10(allCount) + 1) + "d/%d", count.incrementAndGet(), allCount);
					JCas jCas = JCasFactory.createJCas();
					CasIOUtils.load(java.nio.file.Files.newInputStream(file.toPath().toAbsolutePath()), null, jCas.getCas(), true);
					
					SimplePipeline.runPipeline(jCas, naiveTaggerEngine);
					XmiCasSerializer.serialize(jCas.getCas(), new FileOutputStream(outFilePath.toFile()));
				} catch (FileAlreadyExistsException | OverlappingFileLockException | ClosedChannelException e) {
					System.out.printf("\rSkipped file %0" + (int) (log10(allCount) + 1) + "d/%d", count.incrementAndGet(), allCount);
				} catch (UIMAException | UIMARuntimeException | SAXException | IOException | StringIndexOutOfBoundsException e) {
					System.err.printf("\rAn error occurred while writing to '%s', deleting file..\n", outFilePath.toString());
					outFilePath.toFile().delete();
					e.printStackTrace();
				}
			}
			System.out.println("Done.");
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
