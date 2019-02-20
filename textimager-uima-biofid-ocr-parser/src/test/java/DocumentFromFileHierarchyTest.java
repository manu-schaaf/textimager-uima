import BioFID.OCR.DocumentFromFileHierarchy;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * Created on 20.02.2019.
 */
public class DocumentFromFileHierarchyTest {

	@Test
	@DisplayName("Biodiversity Excerpt Test")
	public void testDocumentFromFileHierarchy() {
		DocumentFromFileHierarchy.main(new String[]{
				"-i", "src/test/resources/Biodiversity/",
				"-o", "src/test/out/Biodiversity/",
				"-v", "src/test/resources/Leipzig40MT2010_lowered.5.vocab",
				"-d", "1",
				"--document-depth", "3",
				"-r", "src/test/out/Biodiversity/",
		});
	}
}
