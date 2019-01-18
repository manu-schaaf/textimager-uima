import org.xml.sax.Attributes;

public class OCRFormat extends OCRAnnotation {
	public String lang;
	public String ff;
	public float fs;
	public boolean bold;
	public boolean italic;
	public boolean subscript;
	public boolean superscript;
	public boolean smallcaps;
	public boolean underline;
	public boolean strikeout;
	
	public OCRFormat(String lang, String ff, float fs) {
		this.lang = lang;
		this.ff = ff;
		this.fs = fs;
	}
	
	public OCRFormat(Attributes attributes) {
		this.lang = attributes.getValue("lang");
		this.ff = attributes.getValue("ff");
		this.fs = parseFloat(attributes.getValue("fs"));
		this.bold = parseBoolean(attributes.getValue("bold"));
		this.italic = parseBoolean(attributes.getValue("italic"));
		this.subscript = parseBoolean(attributes.getValue("subscript"));
		this.superscript = parseBoolean(attributes.getValue("superscript"));
		this.smallcaps = parseBoolean(attributes.getValue("smallcaps"));
		this.underline = parseBoolean(attributes.getValue("underline"));
		this.strikeout = parseBoolean(attributes.getValue("strikeout"));
	}
}
