import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;

public class OCRExportHandler extends DefaultHandler {
    // Switches
    private boolean character = false;
    private boolean forceNewToken = false;
    private boolean lastTokenWasSpace = false;
    private boolean lastTokenWasHyphen = false;
    private boolean inLine = false;

    // Block
    private OCRBlock currOCRBlock = null;

    // Token
    public ArrayList<OCRToken> OCRTokens = new ArrayList<>();
    private OCRToken currOCRToken = null;
    private int currTokenStart = 0;

    private Attributes currTokenAttributes = null;

    // Statistics
    private int totalChars = 0;


    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        switch (qName) {
            case "block": {
                currOCRBlock = new OCRBlock(attributes);
                inLine = false;

                character = false;
                break;
            }
            case "line":
                if (inLine && blockObeysRules(currOCRBlock)) {
                    addSpace();
                }
                inLine = true;
                break;
            case "charParams": {
                if (currOCRBlock != null && blockObeysRules(currOCRBlock)) {
                    String wordStart = attributes.getValue("wordStart");

                    if (((wordStart != null && wordStart.equals("true")) || forceNewToken) && !lastTokenWasHyphen) {
                        createToken();
                    }

                    currTokenAttributes = attributes;
                    character = true;
                }
                break;
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        character = false;
        if (currOCRToken != null && qName.equals("text"))
            currOCRToken.setStartEnd(currTokenStart, totalChars);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (character) {
            String curr_char = new String(ch, start, length);
            if (curr_char.matches("\\s+")) {
                addSpace();
            } else {
                // Remove ¬ if it is followed by a character
                if (lastTokenWasHyphen && !lastTokenWasSpace) {
                    currOCRToken.removeLastChar();
                    currOCRToken.addSubToken();
                    totalChars--;
                }
                lastTokenWasSpace = false;
                lastTokenWasHyphen = curr_char.equals("¬");
                currOCRToken.addChar(curr_char);
                currOCRToken.addCharAttributes(currTokenAttributes);
                totalChars++;
            }
            character = false;
        }
    }

    private void addSpace() {
        /// Do not add spaces if the preceding token is a space, the ¬ hyphenation character or there has not been any token
        if (lastTokenWasSpace || lastTokenWasHyphen || currOCRToken == null)
            return;

        /// If the current token already contains characters, create a new token for the space
        if (currOCRToken.length() > 0) {
            forceNewToken = true;
            createToken();
        }

        /// Add the space character and increase token count
        currOCRToken.addChar(" ");
        totalChars++;
        forceNewToken = true;
        lastTokenWasSpace = true;
    }

    /**
     *
     */
    private void createToken() {
        if (currOCRToken == null || forceNewToken || currOCRToken.isSpace()) {
            if (currOCRToken != null) {
                currOCRToken.setStartEnd(currTokenStart, totalChars);
            }
            createNewToken();
        } else {
            if (currOCRToken != null) {
                currOCRToken.addSubToken();
            }
        }
    }

    private void createNewToken() {
        currOCRToken = new OCRToken();
        OCRTokens.add(currOCRToken);
        currTokenStart = totalChars;
        forceNewToken = false;
    }

    /**
     * Check if the current block obeys the rules given for this type of article.
     * TODO: dynamic rules from file
     *
     * @param ocrBlock
     * @return
     */
    private boolean blockObeysRules(OCRBlock ocrBlock) {
        return ocrBlock.blockTop > 300;
    }
}
