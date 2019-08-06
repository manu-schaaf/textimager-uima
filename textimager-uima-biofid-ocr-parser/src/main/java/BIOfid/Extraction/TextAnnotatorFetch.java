package BIOfid.Extraction;

import BIOfid.AbstractRunner;
import org.apache.commons.cli.MissingArgumentException;

public class TextAnnotatorFetch extends AbstractRunner {
	
	public static void main(String[] args) throws InterruptedException {
		try {
			getParams(args);
			
			String textannotator = "http://141.2.108.253:8080/";
			//	String textannotator = "http://141.2.108.253:50555/";
			String mongoDB = "https://resources.hucompute.org/mongo/";
			
			String sRepository = "14393";
			String sSession;
			String XMILocation;
			String textLocation;
			
			int index;
			index = Integer.max(params.indexOf("-s"), params.indexOf("--session"));
			if (index > -1) {
				sSession = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing --session!\n");
			}
			
			index = params.indexOf("--xmi");
			if (index > -1) {
				XMILocation = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing --xmi!\n");
			}
			
			index = params.indexOf("--text");
			if (index > -1) {
				textLocation = params.get(index + 1);
			} else {
				throw new MissingArgumentException("Missing --text!\n");
			}
			
			index = params.indexOf("--threads");
			final int pThreads = index > -1 ? Integer.parseInt(params.get(index + 1)) : 4;
			
			final boolean forceUTF8 = params.indexOf("--forceUTF8") > -1;
			final boolean reserialize = forceUTF8 & params.indexOf("--reserialize") > -1;
			
			index = params.indexOf("--repository");
			if (index > -1) {
				sRepository = params.get(index + 1);
			}
			
			// TODO
		} catch (MissingArgumentException e) {
			System.err.println(e.getMessage());
			printUsage();
		}
	}
	
	private static void printUsage() {
		System.out.println("Downloads and processes the current annotations from a Textannotator repository (14393 by default).\n" +
				"Usage: java -cp $classpaths TextAnnotatorFetch [args]\n" +
				"Arguments:\n" +
				"\t--session, -s\tValid Textannotator session string.\n" +
				"\t--xmi\t\t\tXMI-files download path.\n" +
				"\t--conll\t\t\tConll-files output path.\n" +
				"\t--text\t\t\tPlaintext files output path.\n" +
				"\t--threads N\t\tOptional, the number of threads to use.\n" +
				"\t--forceUTF8\t\tOptional, if set the XMIs will be dowloaded as a string and saved in UTF-8 format.\n" +
				"\t--reserialize\tOptional, use with --forceUTF8. If set the XMIs will be reserialized after download, adding additional metadata.\n" +
				"\t--repository\tOptional, the target repository. Default: 14393.\n" +
				"\t--strategyIndex\tThe conll stacking strategy index. 0=top-down-bottom-up, 1=top-down, 2=bottom-up. Default: 0.");
	}
}
