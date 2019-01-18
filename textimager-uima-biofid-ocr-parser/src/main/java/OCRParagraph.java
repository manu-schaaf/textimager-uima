import com.google.common.base.Strings;
import org.xml.sax.Attributes;

public class OCRParagraph extends OCRAnnotation {
	
	public final int leftIndent;
	public final int rightIndent;
	public final int startIndent;
	public final int lineSpacing;
	public final alignment align;
	
	private enum alignment {
		Left, Center, Right, Justified
	}
	
	public OCRParagraph(int leftIndent, int rightIndent, int startIndent, int lineSpacing, String align) {
		this.leftIndent = leftIndent;
		this.rightIndent = rightIndent;
		this.startIndent = startIndent;
		this.lineSpacing = lineSpacing;
		this.align = alignment.valueOf(align);
	}
	
	public OCRParagraph(Attributes attributes) {
		this.leftIndent = parseInt(attributes.getValue("leftIndent"));
		this.rightIndent = parseInt(attributes.getValue("rightIndent"));
		this.startIndent = parseInt(attributes.getValue("startIndent"));
		this.lineSpacing = parseInt(attributes.getValue("lineSpacing"));
		this.align = Strings.isNullOrEmpty(attributes.getValue("align"))
				? alignment.Left
				: alignment.valueOf(attributes.getValue("align"));
	}
}
