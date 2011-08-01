package org.freeplane.plugin.workspace.config.actions;

import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.FileChannel;

import javax.swing.JFileChooser;
import javax.swing.tree.DefaultMutableTreeNode;

import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.IndexedTree;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.workspace.WorkspaceController;
import org.freeplane.plugin.workspace.WorkspacePreferences;
import org.freeplane.plugin.workspace.config.node.FilesystemFolderNode;

public class AddExistingFilesystemFolderAction extends AWorkspaceAction {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public AddExistingFilesystemFolderAction() {
		super("AddExistingFilesystemFolderAction");
	}

	public void actionPerformed(final ActionEvent e) {
		System.out.println("AddExistingFilesystemFolderAction: " + e.getActionCommand() + " : " + e.getID());
		
		JFileChooser fileChooser = new JFileChooser();		
		fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		ResourceController resourceController = ResourceController.getResourceController();
		String currentLocation = resourceController.getProperty(WorkspacePreferences.WORKSPACE_LOCATION);
		fileChooser.setSelectedFile(new File(currentLocation));

		int retVal = fileChooser.showOpenDialog(UITools.getFrame());
		System.out.println("RETVAL: "+retVal+" : "+JFileChooser.APPROVE_OPTION);
		if (retVal == JFileChooser.APPROVE_OPTION) {
			createNode(e, fileChooser.getSelectedFile(), currentLocation);
		}
		
		WorkspaceController.getCurrentWorkspaceController().refreshWorkspace();
	}

	private void createNode(final ActionEvent e, final File path, String currentLocation) {
		String temp = currentLocation + File.separator + "workspace_temp.xml";
		String config = currentLocation + File.separator + "workspace.xml";
		
		DefaultMutableTreeNode treeNode = this.getNodeFromActionEvent(e);
		IndexedTree tree = WorkspaceController.getCurrentWorkspaceController().getTree();
		
		FilesystemFolderNode node = new FilesystemFolderNode(path.getPath().replace(File.separator, ""));
		String name = path.getName();
		
		node.setName(name==null? "folder" : name);
				
		if(path!=null) {
			LogUtils.info("FilesystemPath: "+path);
			URL url = null;
			try {
				url = path.toURL();				
			}
			catch (MalformedURLException ex) {
				ex.printStackTrace();
			}
			node.setFolderPath(url);
		}
		
		Object key = tree.getKeyByUserObject(treeNode.getUserObject());
		tree.addElement(key, node, IndexedTree.AS_CHILD);
		
		WorkspaceController.getCurrentWorkspaceController().getViewModel().reload(treeNode);
		
		try {
			WorkspaceController.getCurrentWorkspaceController().saveConfigurationAsXML(new FileWriter(temp));
			
			FileChannel from = new FileInputStream(temp).getChannel();
			FileChannel to = new FileOutputStream(config).getChannel();

			to.transferFrom(from, 0, from.size());
		}
		catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

}