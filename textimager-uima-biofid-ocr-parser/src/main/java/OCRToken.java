import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.xml.sax.Attributes;

import java.util.ArrayList;

public class OCRToken {

    private ArrayList<String> charList = new ArrayList<>();
    private ArrayList<Attributes> charAttributeList = new ArrayList<>();

    public int tokenStart = 0;
    public int tokenEnd = 0;

    public boolean isWordFromDictionary = false;
    public boolean isWordNormal = false;
    public boolean isWordNumeric = false;

    public int suspiciousChars = 0;

    public void addChar(String pChar) {
        charList.add(pChar);
    }

    public void addChar(int pCharPos, String pChar) {
        charList.add(pCharPos, pChar);
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
        return String.join("", charList);
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
        String suspicious = pAttributes.getValue("suspicious");
        if (suspicious != null && !suspicious.isEmpty()) {
            if (suspicious.equals("true"))
                suspiciousChars++;
        }

        String wordFromDictionary = pAttributes.getValue("wordFromDictionary");
        if (wordFromDictionary != null && !wordFromDictionary.isEmpty()) {
            if (wordFromDictionary.equals("true"))
                isWordFromDictionary = true;
        }

        String wordNormal = pAttributes.getValue("wordNormal");
        if (wordNormal != null && !wordNormal.isEmpty()) {
            if (wordNormal.equals("true"))
                isWordNormal = true;
        }

        String wordNumeric = pAttributes.getValue("wordNumeric");
        if (wordNumeric != null && !wordNumeric.isEmpty()) {
            if (wordNumeric.equals("true"))
                isWordNumeric = true;
        }
    }

    public void setStartEnd(int pStart, int pEnd) {
        this.tokenStart = pStart;
        this.tokenEnd = pEnd;
    }

    public int length() {
        return charList.size();
    }

    public boolean isSpace() {
        return charList != null && charList.size() == 1 && charList.get(0).equals(" ");
    }

}
