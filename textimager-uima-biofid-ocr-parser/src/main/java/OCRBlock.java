import org.xml.sax.Attributes;

public class OCRBlock {

    public final int blockTop;
    public final int blockBottom;
    public final int blockLeft;
    public final int blockRight;

    public final String blockType;
    public final String blockName;

    public OCRBlock(int blockTop, int blockBottom, int blockLeft, int blockRight, String blockType, String blockName) {
        this.blockTop = blockTop;
        this.blockBottom = blockBottom;
        this.blockLeft = blockLeft;
        this.blockRight = blockRight;
        this.blockType = blockType;
        this.blockName = blockName;
    }

    public OCRBlock(Attributes attributes) {
        this.blockTop = Integer.parseInt(attributes.getValue("t"));
        this.blockBottom = Integer.parseInt(attributes.getValue("b"));
        this.blockLeft = Integer.parseInt(attributes.getValue("l"));
        this.blockRight = Integer.parseInt(attributes.getValue("r"));
        this.blockType = attributes.getValue("blockType");
        this.blockName = attributes.getValue("blockName");
    }
}
