package BioFID.OCR.Annotation;

import org.apache.uima.jcas.JCas;
import org.texttechnologylab.annotation.ocr.OCRBlock;
import org.xml.sax.Attributes;

public class Block extends StructuralElement {
	
	public final blockTypeEnum blockType;
	public final String blockName;
	public boolean valid;
	
	public enum blockTypeEnum {
		Text, Table, Picture, Barcode
	}
	
	public Block(int top, int bottom, int left, int right, String blockType, String blockName) {
		super(top, bottom, left, right);
		this.blockType = blockTypeEnum.valueOf(blockType);
		this.blockName = blockName;
	}
	
	public Block(Attributes attributes) {
		super(attributes);
		this.blockType = blockTypeEnum.valueOf(attributes.getValue("blockType"));
		this.blockName = attributes.getValue("blockName");
	}
	
	@Override
	public OCRBlock wrap(JCas jCas, int offset) {
		OCRBlock ocrBlock = new OCRBlock(jCas, start + offset, end + offset);
		ocrBlock.setTop(top);
		ocrBlock.setBottom(bottom);
		ocrBlock.setLeft(left);
		ocrBlock.setRight(right);
		ocrBlock.setBlockType(blockType.toString());
		ocrBlock.setBlockName(blockName);
		ocrBlock.setValid(valid);
		return ocrBlock;
	}
}
