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
				"-i", "src/test/resources/Biodiversity/",
				"-o", "src/test/out/Biodiversity/",
				"-v", "src/test/resources/Leipzig40MT2010_lowered.5.vocab",
				"-d", "1",
				"-r", "src/test/out/Biodiversity/",
				"--sortAlNum"
		});
	}
}
