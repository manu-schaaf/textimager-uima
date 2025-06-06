package textimager.uima.io.abby;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.languagetool.LanguageToolSegmenter;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.pipeline.SimplePipeline;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.XMLSerializer;
import org.texttechnologylab.utilities.helper.FileUtils;
import org.texttechnologylab.utilities.helper.StringUtils;
import org.xml.sax.SAXException;
import textimager.uima.io.abby.utility.XMIWriter;

import javax.xml.transform.OutputKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;

public class TTLabBioFIDPreProcessing_bak {

    public static void _main(String[] args) throws IOException {

        String pathIn = args[0];
        String pathOut = args[1];
        String pathTemp = args[2];

        Set<String> outSet = FileUtils.getFiles(pathOut, ".gz").stream().map(f->f.getName()).collect(Collectors.toSet());

//        Set<File> folderSet = new HashSet<>(0);
//
//        File overViewFile = new File(pathIn);
//        for (File file : overViewFile.listFiles()) {
//            if(file.isDirectory()){
//                folderSet.add(file);
//            }
//        }
//
//        try (Stream<Path> paths = Files.walk(Paths.get(pathIn))) {
//            paths.filter(Files::isDirectory)
//                    .forEach(f->{
//                        folderSet.add(f.toFile());
//                    });
//        }

//        folderSet.forEach(folder-> {
//
//            File nFolder = new File(pathOut + "/" + folder.getName());
//            if (!nFolder.exists()) {
//                nFolder.mkdir();
//            }
//
//            try {
                FileUtils.getFiles(pathIn, ".gz").stream()
//                        .filter(f -> !outSet.contains(f.getName()))
                        //                .filter(f->f.getName().equals("90532.gz"))
                        .forEach(f -> {
                            try {
                                System.out.println("Processing: " + f.getName());

                                //				File tFile = TempFileHandler.getTempFile(f.getName().replace(".gz", ""),".xml");

//                                File tFile = f;//new File(pathTemp + f.getName().replace(".xml", "") + ".xml");
                                File nFile = new File(pathTemp + "n" + f.getName().replace(".xml", "") + ".xmi");
                                File tmpFile = new File(pathTemp + "n" + f.getName().replace(".gz", "") + ".xml");

//                                File targetFile = new File(pathOut + tFile.getName().replace(".gz", "") + "xmi.gz");
//                                if (!targetFile.exists()) {
                                if (true) {
                                    String filename = decompressGZ(Paths.get(f.getAbsolutePath()), Paths.get(tmpFile.getAbsolutePath()));
                                    File targetFile = new File(pathOut + filename.replace(".xml", "") + ".xmi.gz");
//                                    String filename = GzipUtils.getUnCompressedFilename("a_gunzipped_file.gz");
//                                    String filename = GzipUtils.getUncompressedFilename(f.getName());
//                                    System.out.println(filename);
//                                    CollectionReader reader = CollectionReaderFactory.createReader(DocumentReader.class,
//                                            DocumentReader.PARAM_SOURCE_LOCATION, tFile.getPath());
                                    CollectionReader reader = CollectionReaderFactory.createReader(DocumentReaderMultiPage.class,
                                            DocumentReader.PARAM_SOURCE_LOCATION, tmpFile.getPath());

                                    AnalysisEngineDescription languageTool = createEngineDescription(LanguageToolSegmenter.class);

                                    AnalysisEngineDescription writer = createEngineDescription(XMIWriter.class,
                                            XMIWriter.PARAM_PRETTY_PRINT, true,
                                            XMIWriter.PARAM_SINGULAR_TARGET, true,
                                            XMIWriter.PARAM_PRETTY_PRINT, true,
                                            XMIWriter.PARAM_TARGET_LOCATION, nFile.getPath());

                                    SimplePipeline.runPipeline(reader, writer);

                                    String sContent = StringUtils.getContent(nFile);

                                    int iPlace = sContent.indexOf("</xmi:XMI>");
//                                    System.out.println(iPlace);
                                    sContent = sContent.substring(0, iPlace + 10);

                                    StringUtils.writeContent(sContent, nFile);

                                    JCas test = JCasFactory.createJCas();
                                    CasIOUtil.readXmi(test, nFile);

                                    DocumentMetaData dmd = null;

                                    if(JCasUtil.select(test, DocumentMetaData.class).size()==0){
                                        dmd = DocumentMetaData.create(test);
                                    }
                                    else{
                                        dmd = DocumentMetaData.get(test);
                                    }

                                    dmd.setDocumentId(f.getName());
                                    dmd.setDocumentTitle(f.getName());

                                    SimplePipeline.runPipeline(test, languageTool);

//                                    CasIOUtil.writeXmi(test, nFile);

                                    Path outputXmi = nFile.toPath();
                                    try (OutputStream outputStream = Files.newOutputStream(outputXmi)) {
                                        XMLSerializer xmlSerializer = new XMLSerializer(outputStream, true);
                                        xmlSerializer.setOutputProperty(OutputKeys.VERSION, "1.0");
                                        xmlSerializer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.toString());
                                        XmiCasSerializer xmiCasSerializer = new XmiCasSerializer(null);
                                        xmiCasSerializer.serialize(test.getCas(), xmlSerializer.getContentHandler());
                                    } catch (SAXException e) {
                                        e.printStackTrace();
                                    }
                                    compressGZ(Paths.get(nFile.getAbsolutePath()), Paths.get(targetFile.getAbsolutePath()));

                                    nFile.delete();
                                    tmpFile.delete();
                                }
                            } catch (UIMAException | IOException e) {
                                e.printStackTrace();
                            }
                        });
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        });

    }


    public static String decompressGZ(Path input, Path output) throws IOException {

        String filename = "";
        try (
              GzipCompressorInputStream gis =
                      new GzipCompressorInputStream(
                              new FileInputStream(input.toFile())
                      )
        ) {
            filename = gis.getMetaData().getFilename();
            System.out.println(filename);
            Files.copy(gis, output);
        }

        return filename;
//        try (GZIPInputStream gis = new GZIPInputStream(
//                new FileInputStream(input.toFile()))) {
//            Files.copy(gis, output);
//
//        }

    }

    public static void compressGZ(Path input, Path output) throws IOException {

        try (GZIPOutputStream gos = new GZIPOutputStream(
                new FileOutputStream(output.toFile()))) {

            Files.copy(input, gos);

        }

    }

}
