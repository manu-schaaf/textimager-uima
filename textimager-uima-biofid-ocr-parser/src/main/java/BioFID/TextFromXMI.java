package BioFID;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import org.apache.uima.UIMAException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.jcas.JCas;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

import static BioFID.Util.writeToFile;

/**
 * Created on 14.02.2019.
 */
public class TextFromXMI {

	public static void main(String[] args) {
		String sInputPath = args[0];
		String sOutputPath = args[1];

		try {
			ImmutableList<File> files = Files.fileTreeTraverser().breadthFirstTraversal(Paths.get(sInputPath).toFile()).filter(File::isFile).toList();
			int count = 1;
			for (File file : files) {
//				List<String> lines = Files.readLines(file, StandardCharsets.UTF_8);
				JCas jCas = JCasFactory.createJCas();
				CasIOUtil.readXmi(jCas, file);
				if (jCas.getDocumentText().isEmpty())
					continue;
				String outFileName = file.getName().replaceAll("\\.xm[il]", ".txt");
				System.out.printf("\r%d/%d Writing %s", count, files.size(), outFileName);
				writeToFile(Paths.get(sOutputPath, outFileName), jCas.getDocumentText());
				count++;
			}
		} catch (IOException | UIMAException e) {
			e.printStackTrace();
		}
	}

}
