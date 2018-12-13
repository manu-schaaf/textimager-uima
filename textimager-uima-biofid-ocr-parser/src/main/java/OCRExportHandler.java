import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;

public class OCRExportHandler extends DefaultHandler {
    private boolean character = false;

    private BaseToken currBaseToken = null;
    private int currTokenStart = 0;

    private int totalChars = 0;

    ArrayList<BaseToken> baseTokens = new ArrayList<>();

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

        if (qName.equals("charParams")) {
            String wordStart = attributes.getValue("wordStart");
            if (wordStart != null && wordStart.equals("true")) {
                if (currBaseToken != null) {
                    currBaseToken.setStartEnd(currTokenStart, totalChars);
                }

                currBaseToken = new BaseToken();
                baseTokens.add(currBaseToken);
                currBaseToken.addCharAttributes(attributes);

                currTokenStart = totalChars;
            }

            character = true;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        character = false;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (character) {
            String curr_char = new String(ch, start, length);
//            if (!curr_char.matches("")) {
                currBaseToken.addChar(curr_char);
                totalChars++;
//            }
            character = false;
        }
    }
}
