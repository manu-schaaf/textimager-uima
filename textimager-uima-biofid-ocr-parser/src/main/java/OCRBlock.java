import org.xml.sax.Attributes;

public class OCRBlock extends OCRAnnotation {
    
    public final int top;
    public final int bottom;
    public final int left;
    public final int right;
    public final blockTypeEnum blockType;
    public final String blockName;
    public boolean valid;
    
    public enum blockTypeEnum {
        Text, Table, Picture, Barcode
    }
    
    public OCRBlock(int top, int bottom, int left, int right, String blockType, String blockName) {
        this.top = top;
        this.bottom = bottom;
        this.left = left;
        this.right = right;
        this.blockType = blockTypeEnum.valueOf(blockType);
        this.blockName = blockName;
    }

    public OCRBlock(Attributes attributes) {
        this.top = parseInt(attributes.getValue("t"));
        this.bottom = parseInt(attributes.getValue("b"));
        this.left = parseInt(attributes.getValue("l"));
        this.right = parseInt(attributes.getValue("r"));
        this.blockType = blockTypeEnum.valueOf(attributes.getValue("blockType"));
        this.blockName = attributes.getValue("blockName");
    }
}
