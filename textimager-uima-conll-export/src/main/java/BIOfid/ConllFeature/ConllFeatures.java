package BIOfid.ConllFeature;

import java.util.ArrayList;
import java.util.Arrays;

public class ConllFeatures extends ArrayList<String> {
	
	private String prependTag = "";
	
	public ConllFeatures() {
		super(Arrays.asList("O", "", ""));
	}
	
	public ConllFeatures(String initalElement) {
		super(Arrays.asList("", "", ""));
		this.name(initalElement);
	}
	
	public void name(String name) {
		this.set(0, name.replaceAll("([IB]-)*", ""));
	}
	
	public String name() {
		return this.get(0);
	}
	
	public void prependTag(String tag) {
		this.prependTag = tag;
	}
	
	public String prependTag() {
		return prependTag;
	}
	
	public boolean isNameInvalid() {
		return this.get(0) == null || this.get(0).isEmpty();
	}
	
	public void setAbstract(boolean b) {
		if (b)
			this.set(1, "<ABSTRACT>");
		else
			this.set(1, "<CONCRETE>");
	}
	
	public boolean setAbstract() {
		return this.get(1).equals("<ABSTRACT>");
	}
	
	public void setMetaphor(boolean b) {
		if (b)
			this.set(2, "<METAPHOR>");
		else
			this.set(2, "<DIRECT>");
		
	}
	
	public boolean setMetaphor() {
		return this.get(2).equals("<ABSTRACT>");
	}
	
	public ArrayList<String> build() {
		ArrayList<String> retList = new ArrayList<>();
		retList.add(this.prependTag + this.name());
		for (int i = 1; i < this.size(); i++) {
			if (this.get(i) != null && !this.get(i).isEmpty())
				retList.add(this.get(i));
		}
		return retList;
	}
	
	public boolean isOut() {
		return this.get(0) == null || this.get(0).isEmpty() || this.get(0).equals("O");
	}
}
