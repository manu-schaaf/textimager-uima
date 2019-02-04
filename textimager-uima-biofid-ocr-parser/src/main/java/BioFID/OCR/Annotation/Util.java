package BioFID.OCR.Annotation;

import BioFID.OCR.ExportHandler;
import com.google.common.base.Strings;
import org.apache.commons.compress.utils.Charsets;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXException;

import javax.xml.parsers.SAXParser;
import java.io.IOException;
import java.io.InputStream;

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
	
	@NotNull
	public static ExportHandler getExportHandler(SAXParser saxParser, String pagePath, Integer pCharLeftMax, Integer pBlockTopMin) throws SAXException, IOException {
		ExportHandler exportHandler = new ExportHandler();
		exportHandler.charLeftMax = pCharLeftMax;
		exportHandler.blockTopMin = pBlockTopMin;
		InputStream inputStream = IOUtils.toInputStream(pagePath, Charsets.UTF_8);
		saxParser.parse(inputStream, exportHandler);
		return exportHandler;
	}
}