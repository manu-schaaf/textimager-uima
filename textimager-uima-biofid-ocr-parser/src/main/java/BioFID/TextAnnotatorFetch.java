package BioFID;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.http.client.HttpResponseException;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.jcas.JCas;
import org.hucompute.utilities.helper.RESTUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import static BioFID.Util.writeToFile;

public class TextAnnotatorFetch {
	
	static final String textannotator = "http://141.2.108.253:50555/";
	static final String mongoDB = "https://resources.hucompute.org/mongo/";
	static final String sRepository = "14393";
	
	static String sSession;
	static String conllLocation;
	static String XMILocation;
	static String textLocation;
	
	public static void main(String[] args) throws MissingArgumentException {
		ImmutableList<String> params = ImmutableList.copyOf(args);
		
		int index;
		index = Integer.max(params.indexOf("-s"), params.indexOf("--session"));
		if (index > -1) {
			sSession = params.get(index + 1);
		} else {
			throw new MissingArgumentException("Missing --session");
		}
		
		index = params.indexOf("--conll");
		if (index > -1) {
			conllLocation = params.get(index + 1);
		} else {
			throw new MissingArgumentException("Missing --conll");
		}
		
		index = params.indexOf("--xmi");
		if (index > -1) {
			XMILocation = params.get(index + 1);
		} else {
			throw new MissingArgumentException("Missing --xmi");
		}
		
		index = params.indexOf("--text");
		if (index > -1) {
			textLocation = params.get(index + 1);
		} else {
			throw new MissingArgumentException("Missing --text");
		}
		
		index = params.indexOf("--threads");
		final int pThreads = index > -1 ? Integer.parseInt(params.get(index + 1)) : 4;
		
		String requestURL = textannotator + "documents/" + sRepository;
		final JSONObject remoteFiles = RESTUtils.getObjectFromRest(requestURL, sSession);
		
		if (remoteFiles.getBoolean("success")) {
			final JSONArray rArray = remoteFiles.getJSONArray("result");
			
			try {
				final ForkJoinPool forkJoinPool = new ForkJoinPool(pThreads);
				
				System.out.printf("Running TextAnnotatorFetch in parallel with %d threads for %d files\n", forkJoinPool.getParallelism(), rArray.length());
				
				final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
						ConllBIO2003Writer.class,
						ConllBIO2003Writer.PARAM_TARGET_LOCATION, conllLocation,
						ConllBIO2003Writer.PARAM_CONLL_STRATEGY, 2,
						ConllBIO2003Writer.PARAM_OVERWRITE, true,
						ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, true);
				
				int[] count = {0};
				forkJoinPool.submit(() -> IntStream.range(0, rArray.length()).parallel().forEach(a -> {
							try {
								String documentURI = mongoDB + rArray.get(a).toString();
								JSONObject documentJSON = RESTUtils.getObjectFromRest(documentURI, sSession);
								
								if (documentJSON.getBoolean("success")) {
									String documentName = documentJSON.getJSONObject("result").getString("name");
									String cleanDocumentName = documentName.replaceFirst("[^_]*_(\\d+)_.*", "$1");
									
									// Download XMI
									URL casURL = new URL(textannotator + "cas/" + rArray.get(a).toString() + "?session=" + sSession);
									File utf8File = Paths.get(XMILocation, cleanDocumentName + ".xmi").toFile();
									
									PrintWriter printWriter = new PrintWriter(Files.newWriter(utf8File, StandardCharsets.UTF_8));
									BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(casURL.openStream()));
									bufferedReader.lines().forEachOrdered(printWriter::println);
									bufferedReader.close();
									printWriter.close();
									
									// Process XMI & write conll
									JCas jCas = JCasFactory.createJCas();
									CasIOUtil.readXmi(jCas, utf8File);
									
									DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
									documentMetaData.setDocumentId(cleanDocumentName);
									documentMetaData.setDocumentUri(documentURI);
									documentMetaData.setDocumentTitle(documentName);
									
									conllEngine.process(jCas);
									
									// Write raw text
									if (jCas.getDocumentText() == null || jCas.getDocumentText().isEmpty())
										return;
									
									String content = jCas.getDocumentText();
									writeToFile(Paths.get(textLocation, cleanDocumentName + ".txt"), content);
								} else {
									throw new HttpResponseException(400, String.format("Request to '%s' failed! Response: %s", documentURI, documentJSON.toString()));
								}
							} catch (HttpResponseException httpE) {
								System.err.println(httpE.getMessage());
							} catch (IOException ioE) {
								System.err.println("Could not write UTF-8 file!");
								ioE.printStackTrace();
							} catch (Exception e) {
								e.printStackTrace();
							}
							System.out.printf("\rFile %d/%d (running threads: %d, pool size: %d)",
									count[0]++, rArray.length(), forkJoinPool.getRunningThreadCount(), forkJoinPool.getPoolSize());
						})
				).get();
			} catch (UIMAException | InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		} else {
			System.err.println(remoteFiles);
		}
	}
}
