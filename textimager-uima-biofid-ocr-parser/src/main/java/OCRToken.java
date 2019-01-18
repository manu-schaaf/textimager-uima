import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class OCRToken extends OCRAnnotation {
	
	private ArrayList<ArrayList<String>> subTokenList;
	private ArrayList<String> charList;
	private ArrayList<Attributes> charAttributeList = new ArrayList<>(); // TODO: adjust to List<List> token structure
	
	public boolean isWordFromDictionary = false;
	public boolean isWordNormal = false;
	public boolean isWordNumeric = false;
	
	public int suspiciousChars = 0;
	
	private boolean containsHyphen = false;
	
	public OCRToken() {
		charList = new ArrayList<>();
		
		subTokenList = new ArrayList<>();
		subTokenList.add(charList);
	}
	
	public void addSubToken() {
		charList = new ArrayList<>();
		subTokenList.add(charList);
	}
	
	public ArrayList<String> subTokenStrings() {
		return subTokenList.stream().map(cl -> String.join("", cl)).collect(Collectors.toCollection(ArrayList::new));
	}
	
	public void addChar(String pChar) {
		charList.add(pChar);
	}
	
	public void addChar(int pCharPos, String pChar) {
		charList.add(pCharPos, pChar);
	}
	
	public void removeLastChar() {
		if (!charList.isEmpty())
			charList.remove(charList.size() - 1);
	}
	
	public void addCharAttributes(Attributes pCharAttributes) {
		charAttributeList.add(pCharAttributes);
		this.processAttributes(pCharAttributes);
	}
	
	public void addCharAttributes(int pCharPos, Attributes pCharAttributes) {
		charAttributeList.add(pCharPos, pCharAttributes);
		this.processAttributes(pCharAttributes);
	}
	
	public String getTokenString() {
		return subTokenList.stream().map(cl -> String.join("", cl)).collect(Collectors.joining(""));
	}
	
	public double getAverageCharConfidence() {
		Mean mean = new Mean();
		for (Attributes attributes : charAttributeList) {
			String charConfidence = attributes.getValue("charConfidence");
			if (charConfidence != null)
				mean.increment(Double.parseDouble(charConfidence));
		}
		return mean.getResult();
	}
	
	private void processAttributes(Attributes pAttributes) {
		if (parseBoolean(pAttributes.getValue("suspicious")))
			suspiciousChars++;
		
		if (parseBoolean(pAttributes.getValue("wordFromDictionary")))
			isWordFromDictionary = true;
		
		if (parseBoolean(pAttributes.getValue("wordNormal")))
			isWordNormal = true;
		
		if (parseBoolean(pAttributes.getValue("wordNumeric")))
			isWordNumeric = true;
	}
	
	public void setStartEnd(int pStart, int pEnd) {
		this.start = pStart;
		this.end = pEnd;
	}
	
	public int length() {
		return charList.size();
	}
	
	public boolean isSpace() {
		return charList != null && charList.size() == 1 && charList.get(0).equals(" ");
	}
	
	public void setContainsHyphen() {
		this.containsHyphen = true;
	}
	
	public boolean containsHyphen() {
		return containsHyphen;
	}
}
