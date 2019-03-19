import BioFID.OCR.CollectionsFromFileHierarchy;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * Created on 20.02.2019.
 */
public class CollectionsFromFileHierarchyTest {

	@Test
	@DisplayName("Biodiversity Excerpt Test")
	public void testDocumentFromFileHierarchy() {
		CollectionsFromFileHierarchy.main(new String[]{
				"-i", "/home/stud_homes/s3676959/Dokumente/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/resources/Biodiversity/",
				"-o", "/home/stud_homes/s3676959/Dokumente/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/out/Biodiversity/",
				"-v", "/home/stud_homes/s3676959/Dokumente/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/resources/Leipzig40MT2010_lowered.5.vocab",
				"-d", "1",
				"-r", "/home/stud_homes/s3676959/Dokumente/textimager-uima/textimager-uima-biofid-ocr-parser/src/test/out/Biodiversity/",
				"--sortAlNum"
		});
	}
}
