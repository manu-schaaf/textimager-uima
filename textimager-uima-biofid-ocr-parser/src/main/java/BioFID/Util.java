package BioFID;

import BioFID.OCR.ExportHandler;
import com.google.common.base.Strings;
import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.SpellingAnomaly;
import org.apache.uima.jcas.JCas;
import org.jetbrains.annotations.NotNull;
import org.languagetool.JLanguageTool;
import org.languagetool.rules.RuleMatch;
import org.xml.sax.SAXException;

import javax.xml.parsers.SAXParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

abstract public class Util {
	public static int parseInt(String s) {
		return Strings.isNullOrEmpty(s) ? 0 : Integer.parseInt(s);
	}

	public static float parseFloat(String s) {
		return Strings.isNullOrEmpty(s) ? 0f : Float.parseFloat(s);
	}

	public static boolean parseBoolean(String s) {
		return !Strings.isNullOrEmpty(s) && Boolean.parseBoolean(s);
	}

	@NotNull
	public static ExportHandler getExportHandler(SAXParser saxParser, String pagePath, Integer pCharLeftMax, Integer pBlockTopMin) throws SAXException, IOException {
		ExportHandler exportHandler = new ExportHandler();
		exportHandler.charLeftMax = pCharLeftMax;
		exportHandler.blockTopMin = pBlockTopMin;
		InputStream inputStream = Files.newInputStream(Paths.get(pagePath), StandardOpenOption.READ);
		saxParser.parse(inputStream, exportHandler);
		return exportHandler;
	}

	public static void languageToolSpellcheck(JCas aJCas, JLanguageTool langTool, StringBuilder text) throws IOException {
		List<RuleMatch> ruleMatches = langTool.check(text.toString(), false, JLanguageTool.ParagraphHandling.NORMAL);
		for (RuleMatch ruleMatch : ruleMatches) {
			SpellingAnomaly spellingAnomaly = new SpellingAnomaly(aJCas, ruleMatch.getFromPos(), ruleMatch.getToPos());
			spellingAnomaly.setDescription(String.format("Message:%s, SuggestedReplacements:%s",
					ruleMatch.getMessage(), ruleMatch.getSuggestedReplacements()));
			aJCas.addFsToIndexes(spellingAnomaly);
		}
	}


	public static HashSet<String> loadDict(String pDictPath) throws IOException {
		HashSet<String> dict = new HashSet<>();
		if (pDictPath != null) {
			try (BufferedReader br = new BufferedReader(new FileReader(new File(pDictPath)))) {
				dict = br.lines().collect(Collectors.toCollection(HashSet::new));
			}
		}
		return dict;
	}

	public static boolean inDict(String token, HashSet<String> dict) {
		return inDict(token, dict, true);
	}

	public static boolean inDict(String token, HashSet<String> dict, boolean lowerCase) {
		Pattern pattern = Pattern.compile("[^-\\p{Alnum}]", Pattern.UNICODE_CHARACTER_CLASS);
		String word = pattern.matcher(token).replaceAll("");
		word = lowerCase ? word.toLowerCase() : word;
		return dict != null && !word.isEmpty() && dict.contains(word);
	}

	public static void writeToFile(Path targetFilePath, String content) {
		try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(targetFilePath), StandardCharsets.UTF_8))) {
			pw.print(content);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void writeToFile(Path targetFilePath, Iterable<String> lines) {
		try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(targetFilePath), StandardCharsets.UTF_8))) {
			for (String line : lines) {
				pw.println(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}