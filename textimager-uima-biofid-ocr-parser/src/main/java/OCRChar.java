import org.xml.sax.Attributes;

/**
 * Created on 21.01.2019.
 */
public class OCRChar extends OCRAnnotation
{
	public final int top;
	public final int bottom;
	public final int left;
	public final int right;

	public OCRChar(Attributes attributes)
	{
		this.top = parseInt(attributes.getValue("t"));
		this.bottom = parseInt(attributes.getValue("b"));
		this.left = parseInt(attributes.getValue("l"));
		this.right = parseInt(attributes.getValue("r"));
	}
}
