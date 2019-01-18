import org.xml.sax.Attributes;

public class OCRLine extends OCRAnnotation {
	
	public final int baseline;
	public final int top;
	public final int bottom;
	public final int left;
	public final int right;
	
	public OCRFormat format; // FIXME: Evaluate whether there are lines with multiple <formatting> tags!
	
	public OCRLine(int baseline, int top, int bottom, int left, int right) {
		this.baseline = baseline;
		this.top = top;
		this.bottom = bottom;
		this.left = left;
		this.right = right;
	}
	
	public OCRLine(Attributes attributes) {
		this.baseline = parseInt(attributes.getValue("baseline"));
		this.top = parseInt(attributes.getValue("t"));
		this.bottom = parseInt(attributes.getValue("b"));
		this.left = parseInt(attributes.getValue("l"));
		this.right = parseInt(attributes.getValue("r"));
	}
}
