package BioFID.OCR;

import BioFID.OCR.Annotation.*;
import static BioFID.OCR.Annotation.OCRBlock.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;

public class ExportHandler extends DefaultHandler {
	// Pages
	public ArrayList<OCRPage> OCRPages = new ArrayList<>();
	private OCRPage currOCRPage = null;
	
	// OCRBlock
	public ArrayList<OCRBlock> OCRBlocks = new ArrayList<>();
	private OCRBlock currOCRBlock = null;
	
	// Paragraphs
	public ArrayList<OCRParagraph> OCRParagraphs = new ArrayList<>();
	private OCRParagraph currOCRParagraph = null;
	
	// Lines
	public ArrayList<OCRLine> OCRLines = new ArrayList<>();
	private OCRLine currOCRLine = null;
	
	// OCRToken
	public ArrayList<OCRToken> OCRTokens = new ArrayList<>();
	private OCRToken currOCRToken = null;
	public int blockTopMin = 300;
	public int charLeftMax = 1925;
	
	// Switches
	private boolean character = false;
	private boolean characterIsAllowed = false;
	private boolean forceNewToken = false;
	private boolean lastTokenWasSpace = false;
	private boolean lastTokenWasHyphen = false;
	private boolean inLine = false;
	
	// Formatting
//	private String currLang = null;
//	private String currFont = null;
//	private String currFontSize = null;
	
	private Attributes currCharAttributes = null;
	
	// Statistics
	private int totalChars = 0;
	
	
	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		switch (qName) {
			case "page":
				currOCRPage = new OCRPage(attributes);
				currOCRPage.start = totalChars;
				break;
			case "block": {
				currOCRBlock = new OCRBlock(attributes);
				currOCRBlock.start = totalChars;
				currOCRBlock.valid = blockObeysRules(currOCRBlock);
				OCRBlocks.add(currOCRBlock);
				
				inLine = false;
				
				character = false;
				break;
			}
			case "text":
				break;
			case "par":
				currOCRParagraph = new OCRParagraph(attributes);
				currOCRParagraph.start = totalChars;
				OCRParagraphs.add(currOCRParagraph);
				break;
			case "line":
				currOCRLine = new OCRLine(attributes);
				currOCRLine.start = totalChars;
				OCRLines.add(currOCRLine);
				
				inLine = true;
				break;
			case "formatting":
				if (currOCRLine != null)
					currOCRLine.OCRFormat = new OCRFormat(attributes);
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
	public void endElement(String uri, String localName, String qName) throws SAXException {
		switch (qName) {
			case "page":
				setEnd(currOCRPage);
				break;
			case "block":
				addSpace();
				setEnd(currOCRBlock);
				break;
			case "text":
				addSpace();
				setEnd(currOCRParagraph);
				setEnd(currOCRLine);
				setEnd(currOCRToken);
				break;
			case "par":
				addSpace();
				setEnd(currOCRParagraph);
				break;
			case "line":
				/// Add space instead of linebreak
				addSpace();
				setEnd(currOCRLine);
		}
		character = false;
	}
	
	private void setEnd(OCRAnnotation OCRAnnotation) {
		if (OCRAnnotation != null)
			OCRAnnotation.end = totalChars;
	}
	
	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		if (character && characterIsAllowed) {
			String curr_char = new String(ch, start, length);
			if (curr_char.matches("\\s+")) {
				addSpace();
			} else {
				/// Add a new subtoken if there has been a ¬ and it was followed by a character
				if (lastTokenWasHyphen && !lastTokenWasSpace) {
//                    currOCRToken.removeLastChar();
//                    totalChars--;
					currOCRToken.setContainsHyphen();
					currOCRToken.addSubToken();
				}
				lastTokenWasSpace = false;
				
				/// The hyphen character ¬ does not contribute to the total character count
				if (curr_char.equals("¬")) {
					lastTokenWasHyphen = true;
				} else {
					lastTokenWasHyphen = false;
					currOCRToken.addChar(curr_char);
					currOCRToken.addCharAttributes(currCharAttributes);
					totalChars++;
				}
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
				currOCRToken.end = totalChars;
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
		currOCRToken.start = totalChars;
		OCRTokens.add(currOCRToken);
		
		forceNewToken = false;
	}
	
	/**
	 * Check if the current OCRBlock obeys the rules given for this type of article.
	 * TODO: dynamic rules from file
	 *
	 * @param OCRBlock BioFID.OCR.OCRAnnotation.OCRBlock
	 * @return boolean True if the current OCRBlock is not null and obeys all rules.
	 */
	private boolean blockObeysRules(OCRBlock OCRBlock) {
		return OCRBlock != null && OCRBlock.blockType == blockTypeEnum.Text && OCRBlock.top >= blockTopMin;
	}
	
	private boolean charObeysRules(OCRChar ocrChar) {
		return ocrChar != null && ocrChar.left <= charLeftMax;
	}
}
