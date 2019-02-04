package BioFID.OCR.Annotation;

import org.xml.sax.Attributes;

public class Line extends OCRAnnotation {
	
	public final int baseline;
	public final int top;
	public final int bottom;
	public final int left;
	public final int right;
	
	public Format format; // FIXME: Evaluate whether there are lines with multiple <formatting> tags!
	
	public Line(int baseline, int top, int bottom, int left, int right) {
		this.baseline = baseline;
		this.top = top;
		this.bottom = bottom;
		this.left = left;
		this.right = right;
	}
	
	public Line(Attributes attributes) {
		this.baseline = Util.parseInt(attributes.getValue("baseline"));
		this.top = Util.parseInt(attributes.getValue("t"));
		this.bottom = Util.parseInt(attributes.getValue("b"));
		this.left = Util.parseInt(attributes.getValue("l"));
		this.right = Util.parseInt(attributes.getValue("r"));
	}
}
