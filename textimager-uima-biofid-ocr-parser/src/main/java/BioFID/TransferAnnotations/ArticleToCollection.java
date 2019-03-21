package BioFID.TransferAnnotations;

import BioFID.AbstractRunner;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

/**
 * @author Manuel Stoeckel
 * Created on 21.03.19
 */
public class ArticleToCollection extends AbstractRunner {
	static String sFromXMI;
	static String sToXMI;
	static String sOutputPath;
	static String sFileAtlas;
	
	public static void main(String[] args) {
		try {
			getParams(args);
			int index;
			
			index = params.indexOf("-f");
			if (index > -1) {
				sFromXMI = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing required argument -f!");
			}
			
			index = params.indexOf("-t");
			if (index > -1) {
				sToXMI = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing required argument -t!");
			}
			
			index = params.indexOf("-a");
			if (index > -1) {
				sFileAtlas = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing required argument -a!");
			}
			
			index = params.indexOf("-o");
			if (index > -1) {
				sOutputPath = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing required argument -o!");
			}
			
			// Assert Inputs
//			File fromXmiDir = new File(sFromXMI);
//			assert fromXmiDir.isDirectory();
//
//			File toXmiDir = new File(sToXMI);
//			assert toXmiDir.isDirectory();
			
			File fileAtlasFile = new File(sFileAtlas);
			assert fileAtlasFile.isFile();
			
			// Create Output Dir
//			File outputDir = new File(sOutputPath);
//			if (!outputDir.exists())
//				outputDir.mkdirs();
			
			// Create FileAtlas Lookup Map
			HashMap<String, String> fileAtlas = new HashMap<>();
			try {
				String currentPrefix = "";
				List<String> lines = Files.readLines(fileAtlasFile, StandardCharsets.UTF_8);
				for (String line : lines) {
					String[] split = line.split("\t");
					
					if (split.length < 2)
						continue;
					
					if (!StringUtils.isNumeric(split[0])) {
						// Level 0 Case
						currentPrefix = split[1];
					} else {
						String documentId = split[0];
						String documentPath = split[1];
						String collectionId = documentPath.replace(currentPrefix, "")
								.replaceFirst("^/", "")
								.split("/")[0];
						
						fileAtlas.put(documentId, collectionId);
					}
				}
				
				System.out.println(fileAtlas);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		} catch (MissingArgumentException e) {
			System.err.println(e.getMessage());
		}
	}
}
