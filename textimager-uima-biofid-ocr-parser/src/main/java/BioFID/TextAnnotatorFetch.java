package BioFID;

import com.google.common.io.Files;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
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

public class TextAnnotatorFetch {
	
	public static void main(String[] args) {
		final String textannotator = "http://141.2.108.253:50555/";
		final String mongoDB = "https://resources.hucompute.org/mongo/";
		final String sSession = "49C4CD978B91B2ECC31CACC30AD98460.jvm1";
		final String sRepository = "14393";
		
		String requestURL = textannotator + "documents/" + sRepository;
		System.out.printf("GET %s?session=%s\n", requestURL, sSession); // TODO: remove
		final JSONObject remoteFiles = RESTUtils.getObjectFromRest(requestURL, sSession);
		
		System.out.println(remoteFiles);
		
		if (remoteFiles.getBoolean("success")) {
			JSONArray rArray = remoteFiles.getJSONArray("result");
			
			try {
				final AnalysisEngine conllEngine = AnalysisEngineFactory.createEngine(
						ConllBIO2003Writer.class,
						ConllBIO2003Writer.PARAM_TARGET_LOCATION, "/resources/public/stoeckel/BioFID/BioFID_NER_annotated_conll_3_18.02/",
						ConllBIO2003Writer.PARAM_OVERWRITE, true,
						ConllBIO2003Writer.PARAM_FILTER_FINGERPRINTED, true);
				
				ForkJoinPool forkJoinPool = new ForkJoinPool(4);

//				IntStream.range(0, rArray.length()).sequential().forEachOrdered(a -> {
				forkJoinPool.submit(() -> IntStream.range(0, rArray.length()).parallel().forEachOrdered(a -> {
							try {
//						                https://resources.hucompute.org/document/16205?session=49C4CD978B91B2ECC31CACC30AD98460.jvm1
								String documentURI = mongoDB + rArray.get(a).toString();
								System.out.printf("GET %s?session=%s\n", documentURI, sSession); // TODO: remove
								JSONObject documentJSON = RESTUtils.getObjectFromRest(documentURI, sSession);
								
								if (documentJSON.getBoolean("success")) {
									String documentName = documentJSON.getJSONObject("result").getString("name");
									String cleanDocumentName = documentName.replaceFirst("[^_]*_(\\d+)_.*", "$1");
									
									URL casURL = new URL(textannotator + "cas/" + rArray.get(a).toString() + "?session=" + sSession);
									File utf8File = Paths.get("/resources/public/stoeckel/BioFID/BioFID_XMI_annotated_18.02/", cleanDocumentName + ".xmi").toFile();
									
									PrintWriter printWriter = new PrintWriter(Files.newWriter(utf8File, StandardCharsets.UTF_8));
									BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(casURL.openStream(), StandardCharsets.ISO_8859_1));
									bufferedReader.lines().forEachOrdered(printWriter::println);
									bufferedReader.close();
									printWriter.close();
									
									JCas jCas = JCasFactory.createJCas();
									CasIOUtil.readXmi(jCas, utf8File);
									
									DocumentMetaData documentMetaData = DocumentMetaData.create(jCas);
									documentMetaData.setDocumentId(cleanDocumentName);
									documentMetaData.setDocumentUri(documentURI);
									documentMetaData.setDocumentTitle(documentName);
									
									conllEngine.process(jCas);
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
							System.out.printf("File %d/%d\n", a, rArray.length());
						})
				).get();
			} catch (UIMAException | InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
	}
}
