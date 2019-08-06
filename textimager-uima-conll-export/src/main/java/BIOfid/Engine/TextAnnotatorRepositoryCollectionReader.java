package BIOfid.Engine;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.HttpResponseException;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.component.CasCollectionReader_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.internal.ExtendedLogger;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasIOUtils;
import org.apache.uima.util.Progress;
import org.hucompute.utilities.helper.RESTUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.apache.uima.fit.util.JCasUtil.select;

public class TextAnnotatorRepositoryCollectionReader extends CasCollectionReader_ImplBase {
	ExtendedLogger logger = getLogger();
	
	public static final String PARAM_TEXT_ANNOTATOR_URL = "pTextAnnotatorUrl";
	@ConfigurationParameter(
			name = PARAM_TEXT_ANNOTATOR_URL,
			mandatory = false,
			defaultValue = "http://141.2.108.196:8080/"
	)
	private static String pTextAnnotatorUrl;
	
	public static final String PARAM_MONGO_DB_URL = "pMongoDb";
	@ConfigurationParameter(
			name = PARAM_MONGO_DB_URL,
			mandatory = false,
			defaultValue = "https://resources.hucompute.org/mongo/"
	)
	private static String pMongoDb;
	
	public static final String PARAM_REMOTE_THREADS = "pThreads";
	@ConfigurationParameter(
			name = PARAM_REMOTE_THREADS,
			mandatory = false,
			defaultValue = "8"
	)
	private static int pThreads;
	
	public static final String PARAM_DOCUMENTS_REPOSITORY = "pRepository";
	@ConfigurationParameter(
			name = PARAM_DOCUMENTS_REPOSITORY,
			mandatory = false,
			defaultValue = "14393"
	)
	private static String pRepository;
	
	public static final String PARAM_SESSION_ID = "pSessionId";
	@ConfigurationParameter(
			name = PARAM_SESSION_ID
	)
	private static String pSessionId;
	
	public static final String PARAM_TARGET_LOCATION = "sourceLocation";
	public static final String PARAM_SOURCE_LOCATION = "sourceLocation";
	public static final String PARAM_PATH = "sourceLocation";
	@ConfigurationParameter(
			name = "sourceLocation"
	)
	private static String sourceLocation;
	
	public static final String PARAM_TEXT_LOCATION = "textLocation";
	@ConfigurationParameter(
			name = PARAM_TEXT_LOCATION,
			mandatory = false,
			defaultValue = ""
	)
	private static String textLocation;

//	public static final String PARAM_FORCE_UTF8 = "forceUTF8";
//	@ConfigurationParameter(
//			name = PARAM_FORCE_UTF8,
//			mandatory = false,
//			defaultValue = "false"
//	)
//	private static Boolean forceUTF8;
	
	public static final String PARAM_FORCE_RESERIALIZE = "forceReserialize";
	@ConfigurationParameter(
			name = PARAM_FORCE_RESERIALIZE,
			mandatory = false,
			defaultValue = "false"
	)
	private static Boolean forceReserialize;
	
	private static ForkJoinTask<?> forkJoinTask;
	private static final ConcurrentLinkedDeque<Path> currentResources = new ConcurrentLinkedDeque<>();
	private ForkJoinPool remotePool;
	
	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		
		remotePool = new ForkJoinPool(pThreads);
		
		String requestURL = pTextAnnotatorUrl + "documents/" + pRepository;
		final JSONObject remoteFiles = RESTUtils.getObjectFromRest(requestURL, pSessionId);
		
		if (remoteFiles.getBoolean("success")) {
			final JSONArray rArray = remoteFiles.getJSONArray("result");
			System.out.printf("Running TextAnnotatorFetch in parallel with %d threads for %d files from repository '%s'\n", remotePool.getParallelism(), rArray.length(), pRepository);
			
			AtomicInteger count = new AtomicInteger(0);
			forkJoinTask = remotePool.submit(() -> IntStream.range(0, rArray.length()).parallel().forEach(a -> {
				try {
					count.incrementAndGet();
					String documentURI = pMongoDb + rArray.get(a).toString();
					JSONObject documentJSON = RESTUtils.getObjectFromRest(documentURI, pSessionId);
					
					if (documentJSON.getBoolean("success")) {
						String documentName = documentJSON.getJSONObject("result").getString("name");
						String cleanDocumentName = documentName
								.replaceFirst("[^_]*_(\\d+)_.*", "$1")
								.replaceAll("\\.[^.]+$", "");
						
						// Download XMI
						URL casURL = new URL(pTextAnnotatorUrl + "cas/" + rArray.get(a).toString() + "?session=" + pSessionId);
						
						// Process XMI & write conll
						JCas jCas = JCasFactory.createJCas();
						
						Path utf8Path = Paths.get(sourceLocation, cleanDocumentName + ".xmi");
						File utf8File = utf8Path.toFile();
						try {
							logger.info(String.format("Downloading file %s..", casURL.toString()));
							FileUtils.copyInputStreamToFile(casURL.openStream(), utf8File);
							logger.info(String.format("Downloaded file %s.", casURL.toString()));
						} catch (Exception ioE) {
							getLogger().warn("Could not write UTF-8 file!");
							System.err.println("Could not write UTF-8 file!");
							ioE.printStackTrace();
							return;
						}
						
						
						// Reserialize the UTF-8 forced JCas
						if (forceReserialize) {
							try (FileInputStream inputStream = FileUtils.openInputStream(utf8File)) {
								CasIOUtils.load(inputStream, null, jCas.getCas(), true);
							} catch (Exception ioE) {
								getLogger().warn("Could not read UTF-8 file!");
								System.err.println("Could not read UTF-8 file!");
								ioE.printStackTrace();
								return;
							}
							
							if (select(jCas, DocumentMetaData.class).size() == 0) {
								DocumentMetaData documentMetaData = new DocumentMetaData(jCas);
								documentMetaData.setDocumentId(cleanDocumentName);
								documentMetaData.setDocumentUri(documentURI);
								documentMetaData.setDocumentTitle(documentName);
							}
							
							try (FileOutputStream fs = new FileOutputStream(utf8File)) {
								XmiCasSerializer.serialize(jCas.getCas(), null, fs, true, null);
							} catch (Exception ioE) {
								getLogger().warn("Could not write UTF-8 file!");
								System.err.println("Could not write UTF-8 file!");
								ioE.printStackTrace();
								return;
							}
						}
						currentResources.add(utf8Path);
						
						// Write raw text
						if (jCas.getDocumentText() != null && !jCas.getDocumentText().isEmpty()) {
							String content = jCas.getDocumentText();
							try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(Paths.get(textLocation, cleanDocumentName + ".txt")), StandardCharsets.UTF_8))) {
								pw.print(content);
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					} else {
						throw new HttpResponseException(400, String.format("Request to '%s' failed! Response: %s", documentURI, documentJSON.toString()));
					}
				} catch (HttpResponseException httpE) {
					System.err.println(httpE.getMessage());
				} catch (Exception e) {
					e.printStackTrace();
				}
				logger.info(String.format("\rFile %d/%d (running remote threads: %d, remote pool size: %d)",
						count.get(), rArray.length(), remotePool.getRunningThreadCount(), remotePool.getPoolSize()));
			})).fork();
		} else {
			System.err.println(remoteFiles);
		}
	}
	
	@Override
	public void getNext(CAS aCAS) throws IOException, CollectionException {
		forkJoinTaskGet();
		Path path = currentResources.pop();
		try (FileInputStream inputStream = FileUtils.openInputStream(path.toFile())) {
			CasIOUtils.load(inputStream, null, aCAS, true);
		} catch (Exception e) {
			getLogger().error("Error while opening file: " + path.toString());
			System.err.println("Error while opening file: " + path.toString());
			e.printStackTrace();
		}
	}
	
	@Override
	public void destroy() {
		super.destroy();
		if (forkJoinTask != null && !forkJoinTask.isDone()) {
			forkJoinTask.cancel(true);
		}
		if (remotePool != null)
			remotePool.shutdownNow();
	}
	
	@Override
	public boolean hasNext() throws IOException, CollectionException {
		forkJoinTaskGet();
		return !currentResources.isEmpty();
	}
	
	private void forkJoinTaskGet() {
		try {
			while (currentResources.isEmpty() && !forkJoinTask.isDone()) {
				try {
					forkJoinTask.get(500, TimeUnit.MILLISECONDS);
				} catch (TimeoutException e) {
				
				}
			}
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public Progress[] getProgress() {
		return new Progress[0];
	}
}
