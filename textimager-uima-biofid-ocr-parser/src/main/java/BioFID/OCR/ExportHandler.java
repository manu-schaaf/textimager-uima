package BioFID;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;

public class OCRExportHandler extends DefaultHandler
{
	// Block
	public ArrayList<OCRBlock> blocks = new ArrayList<>();
	// Paragraphs
	public ArrayList<OCRParagraph> paragraphs = new ArrayList<>();
	// Lines
	public ArrayList<OCRLine> lines = new ArrayList<>();
	// Token
	public ArrayList<OCRToken> tokens = new ArrayList<>();
	public int blockTopMin = 300;
	public int charLeftMax = 1925;
	// Switches
	private boolean character = false;
	private boolean characterIsAllowed = false;
	private boolean forceNewToken = false;
	private boolean lastTokenWasSpace = false;
	private boolean lastTokenWasHyphen = false;
	private boolean inLine = false;
	private OCRBlock currBlock = null;
	private OCRParagraph currParagraph = null;

	// Formatting
//	private String currLang = null;
//	private String currFont = null;
//	private String currFontSize = null;
	private OCRLine currLine = null;
	private OCRToken currToken = null;

	private Attributes currCharAttributes = null;

	// Statistics
	private int totalChars = 0;


	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
	{
		switch (qName) {
			case "block": {
				currBlock = new OCRBlock(attributes);
				currBlock.start = totalChars;
				currBlock.valid = blockObeysRules(currBlock);
				blocks.add(currBlock);

				inLine = false;

				character = false;
				break;
			}
			case "text":
				break;
			case "par":
				currParagraph = new OCRParagraph(attributes);
				currParagraph.start = totalChars;
				paragraphs.add(currParagraph);
				break;
			case "line":
				currLine = new OCRLine(attributes);
				currLine.start = totalChars;
				lines.add(currLine);

				inLine = true;
				break;
			case "formatting":
				if (currLine != null)
					currLine.format = new OCRFormat(attributes);
//				String attr = attributes.getValue("lang");
//				currLang = Strings.isNullOrEmpty(attr) ? null : attr;
//
//				attr = attributes.getValue("ff");
//				currFont = Strings.isNullOrEmpty(attr) ? null : attr;
//
//				attr = attributes.getValue("fs");
//				currFontSize = Strings.isNullOrEmpty(attr) ? null : attr;
				break;
			case "charParams":
				String wordStart = attributes.getValue("wordStart");

				if (((wordStart != null && wordStart.equals("true")) || forceNewToken) && !lastTokenWasHyphen) {
					createToken();
				}

				currCharAttributes = attributes; // TODO: use char obj
				character = true; // TODO: use char obj

				OCRChar ocrChar = new OCRChar(attributes);
				characterIsAllowed = charObeysRules(ocrChar);
				break;
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException
	{
		switch (qName) {
			case "block": {
				addSpace();
				setEnd(currBlock);
				break;
			}
			case "text":
				addSpace();
				setEnd(currParagraph);
				setEnd(currLine);
				setEnd(currToken);
				break;
			case "par":
				addSpace();
				setEnd(currParagraph);
				break;
			case "line":
				/// Add space instead of linebreak
				addSpace();
				setEnd(currLine);
		}
		character = false;
	}

	private void setEnd(OCRAnnotation annotation)
	{
		if (annotation != null)
			annotation.end = totalChars;
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException
	{
		if (character && characterIsAllowed) {
			String curr_char = new String(ch, start, length);
			if (curr_char.matches("\\s+")) {
				addSpace();
			} else {
				/// Add a new subtoken if there has been a ¬ and it was followed by a character
				if (lastTokenWasHyphen && !lastTokenWasSpace) {
//                    currToken.removeLastChar();
//                    totalChars--;
					currToken.setContainsHyphen();
					currToken.addSubToken();
				}
				lastTokenWasSpace = false;

				/// The hyphen character ¬ does not contribute to the total character count
				if (curr_char.equals("¬")) {
					lastTokenWasHyphen = true;
				} else {
					lastTokenWasHyphen = false;
					currToken.addChar(curr_char);
					currToken.addCharAttributes(currCharAttributes);
					totalChars++;
				}
			}
			character = false;
		}
	}

	private void addSpace()
	{
		/// Do not add spaces if the preceding token is a space, the ¬ hyphenation character or there has not been any token
		if (lastTokenWasSpace || lastTokenWasHyphen || currToken == null)
			return;

		/// If the current token already contains characters, create a new token for the space
		if (currToken.length() > 0) {
			forceNewToken = true;
			createToken();
		}

		/// Add the space character and increase token count
		currToken.addChar(" ");
		totalChars++;
		forceNewToken = true;
		lastTokenWasSpace = true;
	}

	/**
	 *
	 */
	private void createToken()
	{
		if (currToken == null || forceNewToken || currToken.isSpace()) {
			if (currToken != null) {
				currToken.end = totalChars;
			}
			createNewToken();
		} else {
			if (currToken != null) {
				currToken.addSubToken();
			}
		}
	}

	private void createNewToken()
	{
		currToken = new OCRToken();
		currToken.start = totalChars;
		tokens.add(currToken);

		forceNewToken = false;
	}

	/**
	 * Check if the current block obeys the rules given for this type of article.
	 * TODO: dynamic rules from file
	 *
	 * @param ocrBlock BioFID.OCRBlock
	 * @return boolean True if the current block is not null and obeys all rules.
	 */
	private boolean blockObeysRules(OCRBlock ocrBlock)
	{
		return ocrBlock != null && ocrBlock.blockType == OCRBlock.blockTypeEnum.Text && ocrBlock.top >= blockTopMin;
	}

	private boolean charObeysRules(OCRChar ocrChar)
	{
		return ocrChar != null && ocrChar.left <= charLeftMax;
	}
}
