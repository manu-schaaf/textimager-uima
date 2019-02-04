package BioFID.OCR;

import com.google.common.base.Strings;

abstract public class Util {
	public static int parseInt(String s) {
		return Strings.isNullOrEmpty(s) ? 0 : Integer.parseInt(s);
	}
	
	public static float parseFloat(String s) {
		return Strings.isNullOrEmpty(s) ? 0f : Float.parseFloat(s);
	}
	
	public static boolean parseBoolean(String s) {
		return !Strings.isNullOrEmpty(s) && Boolean.parseBoolean(s);
	}
}