package BIOfid.Extraction;

import BIOfid.ConllFeature.ConllFeatures;
import com.google.common.base.Strings;
import de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.parameter.ComponentParameters;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.jcas.JCas;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import static org.apache.uima.fit.util.JCasUtil.select;
import static org.apache.uima.fit.util.JCasUtil.selectCovered;

public class ConllBIO2003Writer extends JCasAnnotator_ImplBase {
	
	// Start of AnalysisComponent parameters
	
	/**
	 * Character encoding of the output data.
	 */
	private static final String UNUSED = "_";
	public static final String PARAM_TARGET_LOCATION = ComponentParameters.PARAM_TARGET_LOCATION;
	@ConfigurationParameter(name = PARAM_TARGET_LOCATION, mandatory = true, defaultValue = ComponentParameters.PARAM_TARGET_LOCATION)
	private String targetLocation;
	
	public static final String PARAM_TARGET_ENCODING = ComponentParameters.PARAM_TARGET_ENCODING;
	@ConfigurationParameter(name = PARAM_TARGET_ENCODING, mandatory = true, defaultValue = ComponentParameters.DEFAULT_ENCODING)
	private String targetEncoding;
	
	public static final String PARAM_OVERWRITE = "targetOverwrite";
	@ConfigurationParameter(name = PARAM_OVERWRITE, mandatory = true, defaultValue = "true")
	private Boolean targetOverwrite;
	
	public static final String PARAM_FILENAME_EXTENSION = ComponentParameters.PARAM_FILENAME_EXTENSION;
	@ConfigurationParameter(name = PARAM_FILENAME_EXTENSION, mandatory = true, defaultValue = ".conll")
	private String filenameSuffix;
	
	public static final String PARAM_WRITE_POS = ComponentParameters.PARAM_WRITE_POS;
	@ConfigurationParameter(name = PARAM_WRITE_POS, mandatory = true, defaultValue = "true")
	private boolean writePos;
	
	public static final String PARAM_WRITE_CHUNK = ComponentParameters.PARAM_WRITE_CHUNK;
	@ConfigurationParameter(name = PARAM_WRITE_CHUNK, mandatory = true, defaultValue = "true")
	private boolean writeChunk;
	
	public static final String PARAM_WRITE_NAMED_ENTITY = ComponentParameters.PARAM_WRITE_NAMED_ENTITY;
	@ConfigurationParameter(name = PARAM_WRITE_NAMED_ENTITY, mandatory = true, defaultValue = "true")
	private boolean writeNamedEntity;
	
	/**
	 * The number of desired NE columns
	 */
	public static final String PARAM_NAMED_ENTITY_COLUMNS = "pNamedEntityColumns";
	@ConfigurationParameter(name = PARAM_NAMED_ENTITY_COLUMNS, defaultValue = "1")
	private Integer pNamedEntityColumns;
	
	public static final String PARAM_CONLL_SEPARATOR = "pConllSeparator";
	@ConfigurationParameter(name = PARAM_CONLL_SEPARATOR, defaultValue = " ")
	private String pConllSeparator;
	
	public static final String PARAM_STRATEGY_INDEX = "pEncoderStrategyIndex";
	@ConfigurationParameter(name = PARAM_STRATEGY_INDEX, defaultValue = "0")
	private Integer pEncoderStrategyIndex;
	
	public static final String PARAM_FILTER_FINGERPRINTED = "pFilterFingerprinted";
	@ConfigurationParameter(name = PARAM_FILTER_FINGERPRINTED, defaultValue = "true")
	private Boolean pFilterFingerprinted;
	
	/**
	 * If true, the raw document text will also be exported.
	 */
	public static final String PARAM_EXPORT_RAW = "pExportRaw";
	@ConfigurationParameter(name = PARAM_EXPORT_RAW, mandatory = false, defaultValue = "false")
	private Boolean pExportRaw;
	
	/**
	 * If true, only the raw document text will be exported.
	 */
	public static final String PARAM_EXPORT_RAW_ONLY = "pExportRawOnly";
	@ConfigurationParameter(name = PARAM_EXPORT_RAW_ONLY, mandatory = false, defaultValue = "false")
	private Boolean pExportRawOnly;
	
	/**
	 * The target location for the raw document text.
	 */
	public static final String PARAM_RAW_TARGET_LOCATION = "pRawTargetLocation";
	@ConfigurationParameter(name = PARAM_RAW_TARGET_LOCATION, mandatory = false)
	private String pRawTargetLocation;
	
	/**
	 * Target location. If this parameter is not set, data is written to stdout.
	 */
	public static final String PARAM_RAW_FILENAME_SUFFIX = "pRawFilenameSuffix";
	@ConfigurationParameter(name = PARAM_RAW_FILENAME_SUFFIX, mandatory = false, defaultValue = ".txt")
	private String pRawFilenameSuffix;
	public static final String PARAM_USE_TTLAB_TYPESYSTEM = "pUseTTLabTypesystem";
	@ConfigurationParameter(name = PARAM_USE_TTLAB_TYPESYSTEM, mandatory = false, defaultValue = "false")
	private Boolean pUseTTLabTypesystem;
	
	// End of AnalysisComponent parameters
	
	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		if (!pExportRawOnly) {
			
			try (PrintWriter conllWriter = getPrintWriter(aJCas, filenameSuffix)) {
				GenericBioEncoder hierarchicalBioEncoder;
				if (pUseTTLabTypesystem) {
					hierarchicalBioEncoder = new TTLabHierarchicalBioEncoder(aJCas, pFilterFingerprinted);
				} else {
					hierarchicalBioEncoder = new DKProHierarchicalBioEncoder(aJCas, pFilterFingerprinted);
				}
				
				for (Sentence sentence : select(aJCas, Sentence.class)) {
					HashMap<Token, Row> ctokens = new LinkedHashMap<>();
					
					// Tokens
					List<Token> tokens = selectCovered(Token.class, sentence);
					
					
					for (Token token : tokens) {
						Lemma lemma = token.getLemma();
						Row row = new Row();
						row.token = token;
						row.chunk = (lemma != null && !Strings.isNullOrEmpty(lemma.getValue())) ? lemma.getValue() : "--";
						row.ne = hierarchicalBioEncoder.getFeatures(token, pEncoderStrategyIndex);
						ctokens.put(row.token, row);
					}
					
					// Write sentence in CONLL 2006 format
					for (Row row : ctokens.values()) {
						String pos = UNUSED;
						if (writePos && (row.token.getPos() != null)) {
							POS posAnno = row.token.getPos();
							pos = posAnno.getPosValue();
						}
						
						String chunk = UNUSED;
						if (writeChunk && (row.chunk != null)) {
							chunk = row.chunk;
						}
						
						String namedEntityFeatures = UNUSED;
						if (writeNamedEntity && (row.ne != null)) {
							StringBuilder neBuilder = new StringBuilder();
							ArrayList<String> ne = row.ne; /// Fixme
							for (int i = 0; i < ne.size(); ) {
								String entry = ne.get(i);
								neBuilder.append(entry);
								if (++i < ne.size()) {
									neBuilder.append(pConllSeparator);
								}
							}
							namedEntityFeatures = neBuilder.toString();
						}
						
						conllWriter.printf("%s%s%s%s%s%s%s\n", row.token.getCoveredText(), pConllSeparator, pos, pConllSeparator, chunk, pConllSeparator, namedEntityFeatures);
					}
					conllWriter.println();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (pExportRaw || pExportRawOnly) {
			try (PrintWriter rawWriter = getRawPrintWriter(aJCas, pRawFilenameSuffix)) {
				rawWriter.print(aJCas.getDocumentText());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	@NotNull
	private PrintWriter getPrintWriter(JCas aJCas, String aExtension) throws IOException {
		String relativePath = getFileName(aJCas);
		Files.createDirectories(Paths.get(targetLocation));
		File file = new File(targetLocation, relativePath + aExtension);
		if (!targetOverwrite && file.exists()) {
			throw new IOException(String.format("File '%s' already exists!\n", file.getAbsolutePath()));
		}
		return new PrintWriter(new OutputStreamWriter(FileUtils.openOutputStream(file), targetEncoding));
	}
	
	private String getFileName(JCas aJCas) {
		DocumentMetaData meta = DocumentMetaData.get(aJCas);
		String path = meta.getDocumentId() == null || meta.getDocumentId().isEmpty() ? StringUtils.substringAfterLast(meta.getDocumentUri(), "/") : meta.getDocumentId();
		return path.replaceAll("\\.xmi", "");
	}
	
	@NotNull
	private PrintWriter getRawPrintWriter(JCas aJCas, String aExtension) throws IOException {
		if (pRawTargetLocation == null) {
			return new PrintWriter(new CloseShieldOutputStream(System.out));
		} else {
			Files.createDirectories(Paths.get(pRawTargetLocation));
			return new PrintWriter(getRawOutputStream(getFileName(aJCas), aExtension));
		}
	}
	
	private OutputStream getRawOutputStream(String aRelativePath, String aExtension) throws IOException {
		File outputFile = new File(pRawTargetLocation, aRelativePath + aExtension);
		
		File file = new File(outputFile.getAbsolutePath());
		if (!targetOverwrite && file.exists()) {
			throw new IOException(String.format("File '%s' already exists!\n", file.getAbsolutePath()));
		}
		return FileUtils.openOutputStream(file);
	}
	
	private static final class Row {
		Token token;
		String chunk;
		ArrayList<String> ne;
	}
	
	
}
