package BioFID.OCR.Annotation;

import org.xml.sax.Attributes;

public class Page extends OCRAnnotation {
	private Integer width;
	private Integer height;
	private Integer resolution;
	private boolean originalCoords;
	
	public String pageId;
	
	public Page(Attributes attributes) {
		this.width = Util.parseInt(attributes.getValue("width"));
		this.height = Util.parseInt(attributes.getValue("height"));
		this.resolution = Util.parseInt(attributes.getValue("resolution"));
		this.originalCoords = Util.parseBoolean(attributes.getValue("originalCoords"));
	}
}
