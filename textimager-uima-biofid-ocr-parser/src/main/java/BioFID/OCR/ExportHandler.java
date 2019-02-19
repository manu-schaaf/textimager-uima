package BioFID.OCR;

import BioFID.OCR.Annotation.*;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;

import static BioFID.OCR.Annotation.Block.blockTypeEnum;

public class ExportHandler extends DefaultHandler {
	// Pages
	public ArrayList<Page> pages = new ArrayList<>();
	private Page currPage = null;
	
	// Block
	public ArrayList<Block> blocks = new ArrayList<>();
	private Block currBlock = null;
	
	// Paragraphs
	public ArrayList<Paragraph> paragraphs = new ArrayList<>();
	private Paragraph currParagraph = null;
	
	// Lines
	public ArrayList<Line> lines = new ArrayList<>();
	private Line currLine = null;
	
	// Token
	public ArrayList<Token> tokens = new ArrayList<>();
	private Token currToken = null;
	public int blockTopMin = 0;
	public int charLeftMax = Integer.MAX_VALUE;
	
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
				currPage = new Page(attributes);
				currPage.start = totalChars;
				pages.add(currPage);
				break;
			case "block": {
				currBlock = new Block(attributes);
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
				currParagraph = new Paragraph(attributes);
				currParagraph.start = totalChars;
				paragraphs.add(currParagraph);
				break;
			case "line":
				currLine = new Line(attributes);
				currLine.start = totalChars;
				lines.add(currLine);
				
				inLine = true;
				break;
			case "formatting":
				if (currLine != null)
					currLine.OCRFormat = new Format(attributes);
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
				
				if (currToken == null || (((wordStart != null && wordStart.equals("true")) || forceNewToken) && !lastTokenWasHyphen)) {
					createToken();
				}
				
				currCharAttributes = attributes; // TODO: use char obj
				character = true; // TODO: use char obj
				
				Char ocrChar = new Char(attributes);
				characterIsAllowed = charObeysRules(ocrChar);
				break;
		}
	}
	
	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		switch (qName) {
			case "page":
				setEnd(currPage);
				break;
			case "block":
				addSpace();
				setEnd(currBlock);
				break;
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
	
	private void setEnd(Annotation Annotation) {
		if (Annotation != null)
			Annotation.end = totalChars;
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
					currToken.addChar(curr_char); // FIXME: random bug.
					currToken.addCharAttributes(currCharAttributes);
					totalChars++;
				}
			}
			character = false;
		}
	}
	
	private void addSpace() {
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
	private void createToken() {
		if (currToken == null) {
			createNewToken();
		} else if (forceNewToken || currToken.isSpace()) {
			currToken.end = totalChars;
			createNewToken();
		} else {
			currToken.addSubToken();
		}
	}
	
	private void createNewToken() {
		currToken = new Token();
		currToken.start = totalChars;
		tokens.add(currToken);
		
		forceNewToken = false;
	}
	
	/**
	 * Check if the current Block obeys the rules given for this type of article.
	 * TODO: dynamic rules from file
	 *
	 * @param OCRBlock BioFID.OCR.Annotation.Block
	 * @return boolean True if the current Block is not null and obeys all rules.
	 */
	private boolean blockObeysRules(Block OCRBlock) {
		return OCRBlock != null && OCRBlock.blockType == blockTypeEnum.Text && OCRBlock.top >= blockTopMin;
	}
	
	private boolean charObeysRules(Char ocrChar) {
		return ocrChar != null && ocrChar.left <= charLeftMax;
	}
}
