import com.google.common.base.Strings;

public class OCRAnnotation {
	
	public int start = 0;
	public int end = 0;
	
	int parseInt(String s) {
		return Strings.isNullOrEmpty(s) ? 0 : Integer.parseInt(s);
	}
	
	float parseFloat(String s) {
		return Strings.isNullOrEmpty(s) ? 0f : Float.parseFloat(s);
	}
	
	boolean parseBoolean(String s) {
		return !Strings.isNullOrEmpty(s) && Boolean.parseBoolean(s);
	}
}