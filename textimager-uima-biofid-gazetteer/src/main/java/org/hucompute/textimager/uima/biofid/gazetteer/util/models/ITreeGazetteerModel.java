package org.biofid.gazetteer.models;

import org.biofid.gazetteer.tree.ITreeNode;

public interface ITreeGazetteerModel extends IGazetteerModel {
	
	ITreeNode getTree();
}
