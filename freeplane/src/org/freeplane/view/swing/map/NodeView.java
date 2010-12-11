/*
 *  Freeplane - mind map editor
 *  Copyright (C) 2008 Joerg Mueller, Daniel Polansky, Christian Foltin, Dimitry Polivaev
 *
 *  This file is modified by Dimitry Polivaev in 2008.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.freeplane.view.swing.map;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetListener;
import java.awt.event.MouseEvent;
import java.util.LinkedList;
import java.util.ListIterator;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.tree.TreeNode;

import org.freeplane.core.controller.Controller;
import org.freeplane.core.resources.ResourceController;
import org.freeplane.core.ui.AMouseListener;
import org.freeplane.core.ui.DelayedMouseListener;
import org.freeplane.core.ui.IUserInputListenerFactory;
import org.freeplane.core.ui.components.UITools;
import org.freeplane.core.util.LogUtils;
import org.freeplane.core.util.TextUtils;
import org.freeplane.features.common.attribute.AttributeController;
import org.freeplane.features.common.attribute.NodeAttributeTableModel;
import org.freeplane.features.common.cloud.CloudController;
import org.freeplane.features.common.cloud.CloudModel;
import org.freeplane.features.common.edge.EdgeController;
import org.freeplane.features.common.edge.EdgeStyle;
import org.freeplane.features.common.icon.HierarchicalIcons;
import org.freeplane.features.common.map.INodeView;
import org.freeplane.features.common.map.MapChangeEvent;
import org.freeplane.features.common.map.MapController;
import org.freeplane.features.common.map.ModeController;
import org.freeplane.features.common.map.NodeChangeEvent;
import org.freeplane.features.common.map.NodeModel;
import org.freeplane.features.common.map.NodeModel.NodeChangeType;
import org.freeplane.features.common.nodelocation.LocationModel;
import org.freeplane.features.common.nodestyle.NodeStyleController;
import org.freeplane.features.common.note.NoteModel;
import org.freeplane.features.common.styles.MapViewLayout;
import org.freeplane.features.common.text.DetailTextModel;
import org.freeplane.features.common.text.TextController;
import org.freeplane.features.mindmapmode.text.MTextController;
import org.freeplane.view.swing.map.MapView.PaintingMode;
import org.freeplane.view.swing.map.attribute.AttributeView;
import org.freeplane.view.swing.map.cloud.CloudView;
import org.freeplane.view.swing.map.cloud.CloudViewFactory;
import org.freeplane.view.swing.map.edge.EdgeView;
import org.freeplane.view.swing.map.edge.EdgeViewFactory;
import org.freeplane.view.swing.ui.DefaultMapMouseListener;
import org.freeplane.view.swing.ui.DefaultMapMouseReceiver;

/**
 * This class represents a single Node of a MindMap (in analogy to
 * TreeCellRenderer).
 */
public class NodeView extends JComponent implements INodeView {
	final static int ALIGN_BOTTOM = -1;
	final static int ALIGN_CENTER = 0;
	final static int ALIGN_TOP = 1;
	protected final static Color dragColor = Color.lightGray;
	public final static int DRAGGED_OVER_NO = 0;
	public final static int DRAGGED_OVER_SIBLING = 2;
	public final static int DRAGGED_OVER_SON = 1;
	/** For RootNodeView. */
	public final static int DRAGGED_OVER_SON_LEFT = 3;
	static private int FOLDING_SYMBOL_WIDTH = -1;
	public static final String RESOURCES_SHOW_NODE_TOOLTIPS = "show_node_tooltips";
	private static final long serialVersionUID = 1L;
	public final static int SHIFT = -2;
	static final int SPACE_AROUND = 50;
	private static final int MAIN_VIEWER_POSITION = 1;
	private static final int DETAIL_VIEWER_POSITION = 2;
	private static final int NOTE_VIEWER_POSITION = 10;
	private static final boolean DONT_MARK_FORMULAS = Controller.getCurrentController().getResourceController()
	    .getBooleanProperty("formula_dont_mark_formulas");;

	/**
	 * Determines to a given color a color, that is the best contrary color. It
	 * is different from {@link #getAntiColor2}.
	 *
	 * @since PPS 1.1.1
	 */
	protected static Color getAntiColor1(final Color c) {
		final float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
		hsb[0] += 0.40;
		if (hsb[0] > 1) {
			hsb[0]--;
		}
		hsb[1] = 1;
		hsb[2] = 0.7f;
		return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
	}

	/**
	 * Determines to a given color a color, that is the best contrary color. It
	 * is different from {@link #getAntiColor1}.
	 *
	 * @since PPS 1.1.1
	 */
	protected static Color getAntiColor2(final Color c) {
		final float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
		hsb[0] -= 0.40;
		if (hsb[0] < 0) {
			hsb[0]++;
		}
		hsb[1] = 1;
		hsb[2] = (float) 0.8;
		return Color.getHSBColor(hsb[0], hsb[1], hsb[2]);
	}

	private AttributeView attributeView;
	private JComponent contentPane;
	private MainView mainView;
	private final MapView map;
	private int maxToolTipWidth;
	private NodeModel model;
	private NodeMotionListenerView motionListenerView;
	private NodeView preferredChild;

	protected NodeView(final NodeModel model, final int position, final MapView map, final Container parent) {
		setFocusCycleRoot(true);
		this.model = model;
		this.map = map;
		final TreeNode parentNode = model.getParent();
		final int index = parentNode == null ? 0 : parentNode.getIndex(model);
		createAttributeView();
		parent.add(this, index);
		if (!model.isRoot()) {
			motionListenerView = new NodeMotionListenerView(this);
			map.add(motionListenerView, map.getComponentCount() - 1);
		}
	}


	

	@SuppressWarnings("serial")
    static final class DetailsView extends ZoomableLabel {
	    public DetailsView() {
	        super();
       }


		@Override
	    public Dimension getPreferredSize() {
			final NodeView nodeView = getNodeView();
			if(nodeView == null){
				return super.getPreferredSize();
			}
	    	int mainW = nodeView.getMainView().getPreferredSize().width;
	    	final Dimension ownPrefSize = new Dimension(super.getPreferredSize());
	    	if(ownPrefSize.width < mainW){
	    		ownPrefSize.width = mainW;
	    	}
	    	return ownPrefSize;
	    }
    }

	static private class ArrowIcon implements Icon{
		final private boolean down;
		final private Color color;
		final private static int ARROW_HEIGTH = 5;
		final private static int ARROW_HALF_WIDTH = 4;
		final private static int ICON_HEIGTH = ARROW_HEIGTH + 2;
		final private static int ICON_WIDTH = 1 + ARROW_HALF_WIDTH * 2 + 1;
		

		public ArrowIcon(Color color, boolean down) {
	        super();
	        this.down = down;
	        this.color = color;
        }

		public int getIconHeight() {
			return ICON_HEIGTH; 
        }

		public int getIconWidth() {
			return ICON_WIDTH; 
        }

		public void paintIcon(Component c, Graphics g, int x, int y) {
			int[]   xs = new int[3];
			int[]   ys = new int[3];
			
			xs[0] = 1 + ARROW_HALF_WIDTH;
			xs[1] = 1;
			xs[2] = xs[0] + ARROW_HALF_WIDTH;
			if(down){
				ys[0] = 1 + ARROW_HEIGTH;
				ys[1] = ys[2] = 1;
			}
			else{
				ys[0] = 1;
				ys[1] = ys[2] = 1 + ARROW_HEIGTH;
			}
			final Color oldColor = g.getColor();
			g.setColor(color);
			g.drawPolygon(xs, ys, 3); 
			// Little trick to make the arrows of equal size
			g.fillPolygon(xs, ys, 3);
			g.setColor(oldColor);
        }
		
	}

	private void updateDetails() {
		final DetailTextModel detailText = DetailTextModel.getDetailText(model);
		if (detailText == null) {
			removeContent(DETAIL_VIEWER_POSITION);
			return;
		}
		DetailsView detailContent = (DetailsView) getContent(DETAIL_VIEWER_POSITION);
		if (detailContent == null) {
			detailContent = createDetailView();
			addContent(detailContent, DETAIL_VIEWER_POSITION);
		}
		Color arrowColor = getArrowColor();
		if (detailText.isHidden()) {
			detailContent.updateText("");
			detailContent.setIcon(new ArrowIcon(arrowColor, true));
		}
		else {
			detailContent.updateText(detailText.getHtml());
			detailContent.setFont(mainView.getFont());
			detailContent.setForeground(mainView.getForeground());
			detailContent.setBackground(mainView.getBackground());
			detailContent.setIcon(new ArrowIcon(arrowColor, false));
		}
	}

	private Color getArrowColor() {
		final Color backgroundColor = getBackgroundColor();
	    return UITools.getTextColorForBackground(backgroundColor);
    }

	private DetailsView createDetailView() {
	    DetailsView detailContent =  new DetailsView();
	    final DefaultMapMouseReceiver mouseReceiver = new DefaultMapMouseReceiver();
	    final DefaultMapMouseListener mouseListener = new DefaultMapMouseListener(mouseReceiver);
	    detailContent.addMouseMotionListener(mouseListener);
	    detailContent.addMouseListener(new DelayedMouseListener(new AMouseListener() {
	    
	    	@Override
	        public void mousePressed(MouseEvent e) {
	    		mouseReceiver.mousePressed(e);
	        }
	    
	    	@Override
	        public void mouseReleased(MouseEvent e) {
	    		mouseReceiver.mouseReleased(e);
	        }
	    
	    	@Override
	        public void mouseClicked(MouseEvent e) {
	    		final NodeModel model = NodeView.this.getModel();
	    		TextController controller = TextController.getController();
	    		switch(e.getClickCount()){
	    			case 1:
	    				controller.setDetailsHidden(model, ! DetailTextModel.getDetailText(model).isHidden());
	    				break;
	    			case 2:
	    				if(controller instanceof MTextController){
	    					((MTextController) controller).editDetails(model);
	    				}
	    				break;
	    		}
	        }
	    	
	    }, 2, MouseEvent.BUTTON1));
	    return detailContent;
    }

	void addDragListener(final DragGestureListener dgl) {
		if (dgl == null) {
			return;
		}
		final DragSource dragSource = DragSource.getDefaultDragSource();
		dragSource.createDefaultDragGestureRecognizer(getMainView(), DnDConstants.ACTION_COPY
		        | DnDConstants.ACTION_MOVE | DnDConstants.ACTION_LINK, dgl);
	}

	void addDropListener(final DropTargetListener dtl) {
		if (dtl == null) {
			return;
		}
		final DropTarget dropTarget = new DropTarget(getMainView(), dtl);
		dropTarget.setActive(true);
	}

	private int calcShiftY(final LocationModel locationModel) {
		try {
			final NodeModel parent = model.getParentNode();
			return locationModel.getShiftY() + (getMap().getModeController().hasOneVisibleChild(parent) ? SHIFT : 0);
		}
		catch (final NullPointerException e) {
			return 0;
		}
	}

	@Override
	public boolean contains(final int x, final int y) {
		//		if (!isValid()) {
		//			return false;
		//		}
		final int space = getMap().getZoomed(NodeView.SPACE_AROUND) - 2 * getZoomedFoldingSymbolHalfWidth();
		return (x >= space) && (x < getWidth() - space) && (y >= space) && (y < getHeight() - space);
	}

	protected void convertPointToMap(final Point p) {
		UITools.convertPointToAncestor(this, p, getMap());
	}

	public void createAttributeView() {
		if (attributeView == null && NodeAttributeTableModel.getModel(model).getNode() != null) {
			attributeView = new AttributeView(this);
		}
	}

	public boolean focused() {
		return mainView.hasFocus();
	}

	/**
	 */
	public AttributeView getAttributeView() {
		if (attributeView == null) {
			AttributeController.getController(getMap().getModeController()).createAttributeTableModel(model);
			attributeView = new AttributeView(this);
		}
		return attributeView;
	}

	private Color getBackgroundColor() {
		final Color cloudColor = CloudController.getController(getMap().getModeController()).getColor(model);
		if (cloudColor != null) {
			return cloudColor;
		}
		if (isRoot()) {
			return getMap().getBackground();
		}
		return getParentView().getBackgroundColor();
	}

	/**
	 * This method returns the NodeViews that are children of this node.
	 */
	public LinkedList<NodeView> getChildrenViews() {
		final LinkedList<NodeView> childrenViews = new LinkedList<NodeView>();
		final Component[] components = getComponents();
		for (int i = 0; i < components.length; i++) {
			if (!(components[i] instanceof NodeView)) {
				continue;
			}
			final NodeView view = (NodeView) components[i];
			childrenViews.add(view);
		}
		return childrenViews;
	}

	public JComponent getContent() {
		return contentPane == null ? mainView : contentPane;
	}

	private Container getContentPane() {
		if (contentPane == null) {
			contentPane = NodeViewFactory.getInstance().newContentPane(this);
			final int index = getComponentCount() - 1;
			remove(index);
			contentPane.add(mainView);
			mainView.putClientProperty("NODE_VIEW_CONTENT_POSITION", MAIN_VIEWER_POSITION);
			add(contentPane, index);
		}
		return contentPane;
	}

	/**
	 * Returns the coordinates occupied by the node and its children as a vector
	 * of four point per node.
	 */
	public void getCoordinates(final LinkedList<Point> inList) {
		getCoordinates(inList, 0, false, 0, 0);
	}

	private void getCoordinates(final LinkedList<Point> inList, int additionalDistanceForConvexHull,
	                            final boolean byChildren, final int transX, final int transY) {
		if (!isVisible()) {
			return;
		}
		if (isContentVisible()) {
			if (byChildren) {
				final ModeController modeController = getMap().getModeController();
				final CloudController cloudController = CloudController.getController(modeController);
				final CloudModel cloud = cloudController.getCloud(getModel());
				if (cloud != null) {
					additionalDistanceForConvexHull += CloudView.getAdditionalHeigth(cloud, this) / 5;
				}
			}
			final int x = transX + getContent().getX() - getDeltaX();
			final int y = transY + getContent().getY() - getDeltaY();
			final int width = mainView.getMainViewWidthWithFoldingMark();
			final int heightWithFoldingMark = mainView.getMainViewHeightWithFoldingMark();
			final int height = Math.max(heightWithFoldingMark, getContent().getHeight());
			inList.addLast(new Point(-additionalDistanceForConvexHull + x, -additionalDistanceForConvexHull + y));
			inList
			    .addLast(new Point(-additionalDistanceForConvexHull + x, additionalDistanceForConvexHull + y + height));
			inList.addLast(new Point(additionalDistanceForConvexHull + x + width, additionalDistanceForConvexHull + y
			        + height));
			inList
			    .addLast(new Point(additionalDistanceForConvexHull + x + width, -additionalDistanceForConvexHull + y));
		}
		for (final NodeView child : getChildrenViews()) {
			child.getCoordinates(inList, additionalDistanceForConvexHull, true, transX + child.getX(), transY
			        + child.getY());
		}
	}

	/** get x coordinate including folding symbol */
	public int getDeltaX() {
		return mainView.getDeltaX();
	}

	/** get y coordinate including folding symbol */
	public int getDeltaY() {
		return mainView.getDeltaY();
	}

	/**
	 * @param startAfter
	 */
	NodeView getFirst(Component startAfter, final boolean leftOnly, final boolean rightOnly) {
		final Component[] components = getComponents();
		for (int i = 0; i < components.length; i++) {
			if (startAfter != null) {
				if (components[i] == startAfter) {
					startAfter = null;
				}
				continue;
			}
			if (!(components[i] instanceof NodeView)) {
				continue;
			}
			final NodeView view = (NodeView) components[i];
			if (leftOnly && !view.isLeft() || rightOnly && view.isLeft()) {
				continue;
			}
			if (view.isContentVisible()) {
				return view;
			}
			final NodeView child = view.getFirst(null, leftOnly, rightOnly);
			if (child != null) {
				return child;
			}
		}
		return null;
	}

	public int getHGap() {
		return map.getZoomed(LocationModel.getModel(model).getHGap());
	}

	Rectangle getInnerBounds() {
		final int space = getMap().getZoomed(NodeView.SPACE_AROUND);
		return new Rectangle(space, space, getWidth() - 2 * space, getHeight() - 2 * space);
	}

	private NodeView getLast(Component startBefore, final boolean leftOnly, final boolean rightOnly) {
		final Component[] components = getComponents();
		for (int i = components.length - 1; i >= 0; i--) {
			if (startBefore != null) {
				if (components[i] == startBefore) {
					startBefore = null;
				}
				continue;
			}
			if (!(components[i] instanceof NodeView)) {
				continue;
			}
			final NodeView view = (NodeView) components[i];
			if (leftOnly && !view.isLeft() || rightOnly && view.isLeft()) {
				continue;
			}
			if (view.isContentVisible()) {
				return view;
			}
			final NodeView child = view.getLast(null, leftOnly, rightOnly);
			if (child != null) {
				return child;
			}
		}
		return null;
	}

	LinkedList<NodeView> getLeft(final boolean onlyVisible) {
		final LinkedList<NodeView> left = new LinkedList<NodeView>();
		for (final NodeView node : getChildrenViews()) {
			if (node == null) {
				continue;
			}
			if (node.isLeft()) {
				left.add(node);
			}
		}
		return left;
	}

	/**
	 * Returns the Point where the Links should arrive the Node.
	 */
	public Point getLinkPoint(final Point declination) {
		int x, y;
		Point linkPoint;
		if (declination != null) {
			x = getMap().getZoomed(declination.x);
			y = getMap().getZoomed(declination.y);
		}
		else {
			x = 1;
			y = 0;
		}
		if (isLeft()) {
			x = -x;
		}
		if (y != 0) {
			final double ctgRect = Math.abs((double) getContent().getWidth() / getContent().getHeight());
			final double ctgLine = Math.abs((double) x / y);
			int absLinkX, absLinkY;
			if (ctgRect > ctgLine) {
				absLinkX = Math.abs(x * getContent().getHeight() / (2 * y));
				absLinkY = getContent().getHeight() / 2;
			}
			else {
				absLinkX = getContent().getWidth() / 2;
				absLinkY = Math.abs(y * getContent().getWidth() / (2 * x));
			}
			linkPoint = new Point(getContent().getWidth() / 2 + (x > 0 ? absLinkX : -absLinkX), getContent()
			    .getHeight()
			        / 2 + (y > 0 ? absLinkY : -absLinkY));
		}
		else {
			linkPoint = new Point((x > 0 ? getContent().getWidth() : 0), (getContent().getHeight() / 2));
		}
		linkPoint.translate(getContent().getX(), getContent().getY());
		convertPointToMap(linkPoint);
		return linkPoint;
	}

	public MainView getMainView() {
		return mainView;
	}

	/**
	 * Returns the Point where the InEdge should arrive the Node.
	 */
	public Point getMainViewInPoint() {
		final INodeViewLayout layoutManager = (INodeViewLayout) getLayout();
		final Point in = layoutManager.getMainViewInPoint(this);
		return in;
	}

	/**
	 * Returns the point the edge should start given the point of the child node
	 * that should be connected.
	 *
	 * @param targetView
	 */
	public Point getMainViewOutPoint(final NodeView targetView, final Point destinationPoint) {
		final INodeViewLayout layoutManager = (INodeViewLayout) getLayout();
		final Point out = layoutManager.getMainViewOutPoint(this, targetView, destinationPoint);
		return out;
	}

	public MapView getMap() {
		return map;
	}

	public int getMaxToolTipWidth() {
		if (maxToolTipWidth == 0) {
			try {
				maxToolTipWidth = ResourceController.getResourceController().getIntProperty(
				    "toolTipManager.max_tooltip_width", 600);
			}
			catch (final NumberFormatException e) {
				maxToolTipWidth = 600;
			}
		}
		return maxToolTipWidth;
	}

	public NodeModel getModel() {
		return model;
	}

	public NodeMotionListenerView getMotionListenerView() {
		return motionListenerView;
	}

	protected NodeView getNextPage() {
		if (getModel().isRoot()) {
			return this;
		}
		NodeView sibling = getNextVisibleSibling();
		if (sibling == this) {
			return this;
		}
		NodeView nextSibling = sibling.getNextVisibleSibling();
		while (nextSibling != sibling && sibling.getParentView() == nextSibling.getParentView()) {
			sibling = nextSibling;
			nextSibling = nextSibling.getNextVisibleSibling();
		}
		return sibling;
	}

	protected NodeView getNextSiblingSingle() {
		LinkedList<NodeView> v = null;
		if (getParentView().getModel().isRoot()) {
			if (this.isLeft()) {
				v = (getParentView()).getLeft(true);
			}
			else {
				v = (getParentView()).getRight(true);
			}
		}
		else {
			v = getParentView().getChildrenViews();
		}
		final int index = v.indexOf(this);
		for (int i = index + 1; i < v.size(); i++) {
			final NodeView nextView = (NodeView) v.get(i);
			if (nextView.isContentVisible()) {
				return nextView;
			}
			else {
				final NodeView first = nextView.getFirst(null, false, false);
				if (first != null) {
					return first;
				}
			}
		}
		return this;
	}

	protected NodeView getNextVisibleSibling() {
		NodeView sibling;
		NodeView lastSibling = this;
		for (sibling = this; !sibling.getModel().isRoot(); sibling = sibling.getParentView()) {
			lastSibling = sibling;
			sibling = sibling.getNextSiblingSingle();
			if (sibling != lastSibling) {
				break;
			}
		}
		while (sibling.getModel().getNodeLevel(false) < getMap().getSiblingMaxLevel()) {
			final NodeView first = sibling.getFirst(sibling.isRoot() ? lastSibling : null, this.isLeft(), !this
			    .isLeft());
			if (first == null) {
				break;
			}
			sibling = first;
		}
		if (sibling.isRoot()) {
			return this;
		}
		return sibling;
	}

	public NodeView getParentView() {
		final Container parent = getParent();
		if (parent instanceof NodeView) {
			return (NodeView) parent;
		}
		return null;
	}

	public NodeView getPreferredVisibleChild(final boolean getUpper, final boolean left) {
		if (getModel().isLeaf()) {
			return null;
		}
		if (getUpper) {
			preferredChild = null;
		}
		if (preferredChild != null && (left == preferredChild.isLeft()) && preferredChild.getParent() == this) {
			if (preferredChild.isContentVisible()) {
				return preferredChild;
			}
			else {
				final NodeView newSelected = preferredChild.getPreferredVisibleChild(getUpper, left);
				if (newSelected != null) {
					return newSelected;
				}
			}
		}
		int yGap = Integer.MAX_VALUE;
		final NodeView baseComponent;
		if (isContentVisible()) {
			baseComponent = this;
		}
		else {
			baseComponent = getVisibleParentView();
		}
		final int ownY = baseComponent.getContent().getY() + baseComponent.getContent().getHeight() / 2;
		NodeView newSelected = null;
		for (int i = 0; i < getComponentCount(); i++) {
			final Component c = getComponent(i);
			if (!(c instanceof NodeView)) {
				continue;
			}
			NodeView childView = (NodeView) c;
			if (!(childView.isLeft() == left)) {
				continue;
			}
			if (!childView.isContentVisible()) {
				childView = childView.getPreferredVisibleChild(getUpper, left);
				if (childView == null) {
					continue;
				}
			}
			final Point childPoint = new Point(0, childView.getContent().getHeight() / 2);
			UITools.convertPointToAncestor(childView.getContent(), childPoint, baseComponent);
			if (getUpper) {
				return childView;
			}
			final int gapToChild = Math.abs(childPoint.y - ownY);
			if (gapToChild < yGap) {
				newSelected = childView;
				preferredChild = (NodeView) c;
				yGap = gapToChild;
			}
			else {
				break;
			}
		}
		return newSelected;
	}

	protected NodeView getPreviousPage() {
		if (getModel().isRoot()) {
			return this;
		}
		NodeView sibling = getPreviousVisibleSibling();
		if (sibling == this) {
			return this;
		}
		NodeView previousSibling = sibling.getPreviousVisibleSibling();
		while (previousSibling != sibling && sibling.getParentView() == previousSibling.getParentView()) {
			sibling = previousSibling;
			previousSibling = previousSibling.getPreviousVisibleSibling();
		}
		return sibling;
	}

	protected NodeView getPreviousSiblingSingle() {
		LinkedList<NodeView> v = null;
		if (getParentView().getModel().isRoot()) {
			if (this.isLeft()) {
				v = (getParentView()).getLeft(true);
			}
			else {
				v = (getParentView()).getRight(true);
			}
		}
		else {
			v = getParentView().getChildrenViews();
		}
		final int index = v.indexOf(this);
		for (int i = index - 1; i >= 0; i--) {
			final NodeView nextView = (NodeView) v.get(i);
			if (nextView.isContentVisible()) {
				return nextView;
			}
			else {
				final NodeView last = nextView.getLast(null, false, false);
				if (last != null) {
					return last;
				}
			}
		}
		return this;
	}

	protected NodeView getPreviousVisibleSibling() {
		NodeView sibling;
		NodeView previousSibling = this;
		for (sibling = this; !sibling.getModel().isRoot(); sibling = sibling.getParentView()) {
			previousSibling = sibling;
			sibling = sibling.getPreviousSiblingSingle();
			if (sibling != previousSibling) {
				break;
			}
		}
		while (sibling.getModel().getNodeLevel(false) < getMap().getSiblingMaxLevel()) {
			final NodeView last = sibling.getLast(sibling.isRoot() ? previousSibling : null, this.isLeft(), !this
			    .isLeft());
			if (last == null) {
				break;
			}
			sibling = last;
		}
		if (sibling.isRoot()) {
			return this;
		}
		return sibling;
	}

	LinkedList<NodeView> getRight(final boolean onlyVisible) {
		final LinkedList<NodeView> right = new LinkedList<NodeView>();
		for (final NodeView node : getChildrenViews()) {
			if (node == null) {
				continue;
			}
			if (!node.isLeft()) {
				right.add(node);
			}
		}
		return right;
	}

	/**
	 * @return returns the color that should used to select the node.
	 */
	public Color getSelectedColor() {
		return MapView.standardSelectColor;
	}

	/**
		 * @return Returns the sHIFT.s
		 */
	public int getShift() {
		final LocationModel locationModel = LocationModel.getModel(model);
		return map.getZoomed(calcShiftY(locationModel));
	}

	protected LinkedList<NodeView> getSiblingViews() {
		return getParentView().getChildrenViews();
	}

	public Color getTextBackground() {
		final Color modelBackgroundColor = NodeStyleController.getController(getMap().getModeController())
		    .getBackgroundColor(model);
		if (modelBackgroundColor != null) {
			return modelBackgroundColor;
		}
		return getBackgroundColor();
	}

	public Color getTextColor() {
		final Color color = NodeStyleController.getController(getMap().getModeController()).getColor(model);
		return color;
	}

	public Font getTextFont() {
		return getMainView().getFont();
	}

	/**
	 * @return Returns the VGAP.
	 */
	public int getVGap() {
		return map.getZoomed(LocationModel.getModel(model).getVGap());
	}

	public NodeView getVisibleParentView() {
		final Container parent = getParent();
		if (!(parent instanceof NodeView)) {
			return null;
		}
		final NodeView parentView = (NodeView) parent;
		if (parentView.isContentVisible()) {
			return parentView;
		}
		return parentView.getVisibleParentView();
	}

	public int getZoomedFoldingSymbolHalfWidth() {
		if (NodeView.FOLDING_SYMBOL_WIDTH == -1) {
			NodeView.FOLDING_SYMBOL_WIDTH = ResourceController.getResourceController().getIntProperty(
			    "foldingsymbolwidth", 8);
		}
		final int preferredFoldingSymbolHalfWidth = (int) ((NodeView.FOLDING_SYMBOL_WIDTH * map.getZoom()) / 2);
		return preferredFoldingSymbolHalfWidth;
	}

	void insert() {
		final ListIterator<NodeModel> it = getMap().getModeController().getMapController().childrenFolded(getModel());
		while (it.hasNext()) {
			insert(it.next(), 0);
		}
	}

	/**
	 * Create views for the newNode and all his descendants, set their isLeft
	 * attribute according to this view.
	 */
	NodeView insert(final NodeModel newNode, final int position) {
		final NodeView newView = NodeViewFactory.getInstance().newNodeView(newNode, position, getMap(), this);
		newView.insert();
		return newView;
	}

	/* fc, 25.1.2004: Refactoring necessary: should call the model. */
	public boolean isChildOf(final NodeView myNodeView) {
		return getParentView() == myNodeView;
	}

	/**
	 */
	public boolean isContentVisible() {
		return getModel().isVisible();
	}

	/** Is the node left of root? */
	public boolean isLeft() {
		if (getMap().getLayoutType() == MapViewLayout.OUTLINE) {
			return false;
		}
		return getModel().isLeft();
	}

	public boolean isParentHidden() {
		final Container parent = getParent();
		if (!(parent instanceof NodeView)) {
			return false;
		}
		final NodeView parentView = (NodeView) parent;
		return !parentView.isContentVisible();
	}

	/* fc, 25.1.2004: Refactoring necessary: should call the model. */
	public boolean isParentOf(final NodeView myNodeView) {
		return (this == myNodeView.getParentView());
	}

	public boolean isRoot() {
		return getModel().isRoot();
	}

	public boolean isSelected() {
		return (getMap().isSelected(this));
	}

	/* fc, 25.1.2004: Refactoring necessary: should call the model. */
	public boolean isSiblingOf(final NodeView myNodeView) {
		return getParentView() == myNodeView.getParentView();
	}

	public void mapChanged(final MapChangeEvent event) {
	}

	public void nodeChanged(final NodeChangeEvent event) {
		final NodeModel node = event.getNode();
		// is node is deleted, skip the rest.
		if (!node.isRoot() && node.getParent() == null) {
			return;
		}
		final Object property = event.getProperty();
		if (property == NodeChangeType.FOLDING) {
			treeStructureChanged();
			return;
		}
		if (property.equals(NodeModel.NODE_ICON) || property.equals(HierarchicalIcons.ICONS)) {
			mainView.updateIcons(this);
			revalidate();
			return;
		}
		if (property.equals(NodeModel.NOTE_TEXT)) {
			updateNoteViewer();
			return;
		}
		update();
	}

	public void onNodeDeleted(final NodeModel parent, final NodeModel child, final int index) {
		getMap().resetShiftSelectionOrigin();
		if (getMap().getModeController().getMapController().isFolded(model)) {
			return;
		}
		final boolean preferredChildIsLeft = preferredChild != null && preferredChild.isLeft();
		final NodeView node = (NodeView) getComponent(index);
		if (node == preferredChild) {
			preferredChild = null;
			for (int j = index + 1; j < getComponentCount(); j++) {
				final Component c = getComponent(j);
				if (!(c instanceof NodeView)) {
					break;
				}
				final NodeView candidate = (NodeView) c;
				if (candidate.isVisible() && node.isLeft() == candidate.isLeft()) {
					preferredChild = candidate;
					break;
				}
			}
			if (preferredChild == null) {
				for (int j = index - 1; j >= 0; j--) {
					final Component c = getComponent(j);
					if (!(c instanceof NodeView)) {
						break;
					}
					final NodeView candidate = (NodeView) c;
					if (candidate.isVisible() && node.isLeft() == candidate.isLeft()) {
						preferredChild = candidate;
						break;
					}
				}
			}
		}
		(node).remove();
		final NodeView preferred = getPreferredVisibleChild(false, preferredChildIsLeft);
		if (preferred != null) {
			getMap().selectAsTheOnlyOneSelected(preferred);
		}
		else {
			getMap().selectAsTheOnlyOneSelected(this);
		}
		revalidate();
	}

	public void onNodeInserted(final NodeModel parent, final NodeModel child, final int index) {
		assert parent == model;
		if (getMap().getModeController().getMapController().isFolded(model)) {
			return;
		}
		insert(child, index);
		revalidate();
	}

	public void onNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
	                        final NodeModel child, final int newIndex) {
		onNodeDeleted(oldParent, child, oldIndex);
		onNodeInserted(newParent, child, newIndex);
	}

	public void onPreNodeDelete(final NodeModel oldParent, final NodeModel child, final int oldIndex) {
	}

	/*
	 * (non-Javadoc)
	 * @see javax.swing.JComponent#paint(java.awt.Graphics)
	 */
	@Override
	public void paint(final Graphics g) {
		final PaintingMode paintingMode = map.getPaintingMode();
		if (isContentVisible()) {
			final Graphics2D g2 = (Graphics2D) g;
			final ModeController modeController = map.getModeController();
			final Object renderingHint = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
			final boolean isRoot = isRoot();
			switch (paintingMode) {
				case CLOUDS:
				case ALL:
					modeController.getController().getViewController().setEdgesRenderingHint(g2);
					if (isRoot) {
						paintCloud(g);
					}
					paintCloudsAndEdges(g2);
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, renderingHint);
			}
			super.paint(g);
			switch (paintingMode) {
				case NODES:
				case ALL:
					g2.setStroke(BubbleMainView.DEF_STROKE);
					modeController.getController().getViewController().setEdgesRenderingHint(g2);
					paintDecoration(g2);
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, renderingHint);
			}
		}
		else {
			super.paint(g);
		}
// g.drawRect(0, 0, getWidth(), getHeight());		
	}

	private void paintCloud(final Graphics g) {
		if (!isContentVisible()) {
			return;
		}
		final CloudModel cloudModel = CloudController.getController(getMap().getModeController()).getCloud(model);
		if (cloudModel == null) {
			return;
		}
		final CloudView cloud = new CloudViewFactory().createCloudView(cloudModel, this);
		cloud.paint(g);
	}

	private void paintCloudsAndEdges(final Graphics2D g) {
		for (int i = getComponentCount() - 1; i >= 0; i--) {
			final Component component = getComponent(i);
			if (!(component instanceof NodeView)) {
				continue;
			}
			final NodeView nodeView = (NodeView) component;
			if (nodeView.isContentVisible()) {
				final Point p = new Point();
				UITools.convertPointToAncestor(nodeView, p, this);
				g.translate(p.x, p.y);
				nodeView.paintCloud(g);
				g.translate(-p.x, -p.y);
				final EdgeView edge = EdgeViewFactory.getInstance().getEdge(nodeView);
				edge.paint(g);
			}
			else {
				nodeView.paintCloudsAndEdges(g);
			}
		}
	}

	void paintDecoration(final Graphics2D g) {
		mainView.paintDecoration(this, g);
		FoldingMarkType markType = foldingMarkType(getMap().getModeController().getMapController(), getModel());
		if (!markType.equals(FoldingMarkType.UNFOLDED)) {
			final Point out = getMainViewOutPoint(null, null);
			mainView.paintFoldingMark(this, g, out, markType.equals(FoldingMarkType.ITSELF_FOLDED));
		}
		if(mainView.isShortened()){
			final Point in = getMainViewInPoint();
			mainView.paintFoldingMark(this, g, in, true);
		}
	}

	private enum FoldingMarkType {
		UNFOLDED, ITSELF_FOLDED, UNVISIBLE_CHILDREN_FOLDED
	};

	static private FoldingMarkType foldingMarkType(MapController mapController, NodeModel model) {
		if (mapController.isFolded(model) && (model.isVisible() || model.getFilterInfo().isAncestor())) {
			return FoldingMarkType.ITSELF_FOLDED;
		}
		ListIterator<NodeModel> children = mapController.childrenUnfolded(model);
		while (children.hasNext()) {
			NodeModel child = children.next();
			if (!child.isVisible() && !FoldingMarkType.UNFOLDED.equals(foldingMarkType(mapController, child))) {
				return FoldingMarkType.UNVISIBLE_CHILDREN_FOLDED;
			}
		}
		return FoldingMarkType.UNFOLDED;
	}

	/**
	 * This is a bit problematic, because getChildrenViews() only works if model
	 * is not yet removed. (So do not _really_ delete the model before the view
	 * removed (it needs to stay in memory)
	 */
	void remove() {
		for (final ListIterator<NodeView> e = getChildrenViews().listIterator(); e.hasNext();) {
			e.next().remove();
		}
		if (isSelected()) {
			getMap().deselect(this);
		}
		getMap().getModeController().onViewRemoved(this);
		removeFromMap();
		if (attributeView != null) {
			attributeView.viewRemoved();
		}
		getModel().removeViewer(this);
	}

	protected void removeFromMap() {
		setFocusCycleRoot(false);
		getParent().remove(this);
		if (motionListenerView != null) {
			map.remove(motionListenerView);
		}
	}

	private void repaintEdge(final NodeView nodeView) {
		final Point inPoint = nodeView.getMainViewInPoint();
		UITools.convertPointToAncestor(nodeView.getMainView(), inPoint, this);
		final Point outPoint = getMainViewOutPoint(nodeView, inPoint);
		UITools.convertPointToAncestor(getMainView(), outPoint, this);
		final int x = Math.min(inPoint.x, outPoint.x);
		final int y = Math.min(inPoint.y, outPoint.y);
		final int w = Math.abs(inPoint.x - outPoint.x);
		final int h = Math.abs(inPoint.y - outPoint.y);
		final int EXTRA = 50;
		repaint(x - EXTRA, y - EXTRA, w + EXTRA * 2, h + EXTRA * 2);
	}

	void repaintSelected() {
		// return if main view was not set
		if(mainView == null){
			return;
		}
		// do not repaint removed nodes
		if (model.getParentNode() == null && !model.isRoot()) {
			return;
		}
		mainView.updateTextColor(this);
		if (EdgeController.getController(getMap().getModeController()).getStyle(model).equals(
		    EdgeStyle.EDGESTYLE_HIDDEN)) {
			final NodeView visibleParentView = getVisibleParentView();
			if (visibleParentView != null) {
				visibleParentView.repaintEdge(this);
			}
		}
		final JComponent content = getContent();
		final int EXTRA = 20;
		final int x = content.getX() - EXTRA;
		final int y = content.getY() - EXTRA;
		repaint(x, y, content.getWidth() + EXTRA * 2, content.getHeight() + EXTRA * 2);
	}

	@Override
	public boolean requestFocusInWindow() {
		return mainView.requestFocusInWindow();
	}

	@Override
	public void requestFocus() {
		if(mainView == null){
			return;
		}
		getMap().scrollNodeToVisible(this);
		mainView.requestFocus();
	}

	@Override
	public void setBounds(final int x, final int y, final int width, final int height) {
		super.setBounds(x, y, width, height);
		if (motionListenerView != null) {
			motionListenerView.invalidate();
		}
	}

	void setMainView(final MainView newMainView) {
		if (mainView != null) {
			final Container c = mainView.getParent();
			int i;
			for (i = c.getComponentCount() - 1; i >= 0 && mainView != c.getComponent(i); i--) {
				;
			}
			c.remove(i);
			c.add(newMainView, i);
		}
		else {
			add(newMainView);
		}
		mainView = newMainView;
		final IUserInputListenerFactory userInputListenerFactory = getMap().getModeController()
		    .getUserInputListenerFactory();
		mainView.addMouseListener(userInputListenerFactory.getNodeMouseMotionListener());
		mainView.addMouseMotionListener(userInputListenerFactory.getNodeMouseMotionListener());
		mainView.addKeyListener(userInputListenerFactory.getNodeKeyListener());
		addDragListener(userInputListenerFactory.getNodeDragListener());
		addDropListener(userInputListenerFactory.getNodeDropTargetListener());
	}

	protected void setModel(final NodeModel model) {
		this.model = model;
	}

	public void setPreferredChild(final NodeView view) {
		preferredChild = view;
		final Container parent = this.getParent();
		if (view == null) {
			return;
		}
		else if (parent instanceof NodeView) {
			((NodeView) parent).setPreferredChild(this);
		}
	}

	/**
	 */
	public void setText(final String string) {
		mainView.setText(string);
	}

	@Override
	public void setVisible(final boolean isVisible) {
		super.setVisible(isVisible);
		if (motionListenerView != null) {
			motionListenerView.setVisible(isVisible);
		}
	}

	void syncronizeAttributeView() {
		if (attributeView != null) {
			attributeView.syncronizeAttributeView();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see java.awt.Component#toString()
	 */
	@Override
	public String toString() {
		return getModel().toString() + ", " + super.toString();
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * javax.swing.event.TreeModelListener#treeStructureChanged(javax.swing.
	 * event.TreeModelEvent)
	 */
	private void treeStructureChanged() {
		getMap().resetShiftSelectionOrigin();
		for (final ListIterator<NodeView> i = getChildrenViews().listIterator(); i.hasNext();) {
			i.next().remove();
		}
		insert();
		if (map.getSelected() == null) {
			map.selectAsTheOnlyOneSelected(this);
		}
		map.revalidateSelecteds();
		revalidate();
	}

	void update() {
		updateStyle();
		if (!isContentVisible()) {
			mainView.setVisible(false);
			return;
		}
		mainView.setVisible(true);
		mainView.updateTextColor(this);
		mainView.updateFont(this);
		createAttributeView();
		if (attributeView != null) {
			attributeView.update();
		}
		updateDetails();
		if (contentPane != null) {
			final int componentCount = contentPane.getComponentCount();
			for (int i = 1; i < componentCount; i++) {
				final Component component = contentPane.getComponent(i);
				if (component instanceof JComponent) {
					((JComponent) component).revalidate();
				}
			}
		}
		updateShortener(getModel());
		mainView.updateText(getModel());
		mainView.updateIcons(this);
		updateToolTip();
		revalidate();
	}

	private void updateShortener(NodeModel nodeModel) {
		final ModeController modeController = getMap().getModeController();
		final TextController textController = TextController.getController(modeController);
		final boolean textShortened = textController.getIsShortened(nodeModel);
		final boolean componentsVisible = ! textShortened;
		setContentComponentVisible(componentsVisible);
	}

	private void setContentComponentVisible(final boolean componentsVisible) {
	    final Component[] components = getContentPane().getComponents();
		int index;
		for (index = 0; index < components.length; index++){
			final Component component = components[index];
			if(component == getMainView()){
				continue;
			}
			if(component.isVisible() != componentsVisible){
				component.setVisible(componentsVisible);
			}
		}
    }

	public void updateAll() {
		updateNoteViewer();
		update();
		invalidate();
		for (final NodeView child : getChildrenViews()) {
			child.updateAll();
		}
	}

	private void updateStyle() {
		final String shape = NodeStyleController.getController(getMap().getModeController()).getShape(model);
		if (mainView != null && (model.isRoot() || mainView.getStyle().equals(shape))) {
			return;
		}
		final MainView newMainView = NodeViewFactory.getInstance().newMainView(this);
		setMainView(newMainView);
		if (map.getSelected() == this) {
			requestFocus();
		}
	}

	/**
	 *
	 */
	/**
	 * Updates the tool tip of the node.
	 */
	private void updateToolTip() {
		final boolean areTooltipsDisplayed = ResourceController.getResourceController().getBooleanProperty(
		    NodeView.RESOURCES_SHOW_NODE_TOOLTIPS);
		updateToolTip(areTooltipsDisplayed);
	}

	private void updateToolTip(final boolean areTooltipsDisplayed) {
		if (!areTooltipsDisplayed) {
			mainView.setToolTipText(null);
			return;
		}
		final NodeModel nodeModel = getModel();
		mainView.setToolTipText(getTransformedText(nodeModel, nodeModel.getToolTip()));
	}

	private String getTransformedText(final NodeModel node, final String originalText) {
		final TextController textController = TextController.getController();
		try {
			final String text = textController.getTransformedText(originalText, node);
			if (!DONT_MARK_FORMULAS && text != originalText)
				return colorize(text, "green");
			else
				return text;
		}
		catch (Exception e) {
			LogUtils.warn(e.getMessage(), e);
			return colorize(TextUtils.format("MainView.errorUpdateText", originalText, e.getLocalizedMessage())
			    .replace("\n", "<br>"), "red");
		}
	}

	private String colorize(final String text, String color) {
		return "<span style=\"color:" + color + ";font-style:italic;\">" + text + "</span>";
	}

	void updateToolTipsRecursive() {
		final boolean areTooltipsDisplayed = ResourceController.getResourceController().getBooleanProperty(
		    NodeView.RESOURCES_SHOW_NODE_TOOLTIPS);
		updateToolTipsRecursive(areTooltipsDisplayed);
	}

	private void updateToolTipsRecursive(final boolean areTooltipsDisplayed) {
		updateToolTip(areTooltipsDisplayed);
		invalidate();
		for (final NodeView child : getChildrenViews()) {
			child.updateToolTipsRecursive(areTooltipsDisplayed);
		}
	}

	boolean useSelectionColors() {
		return isSelected() && !MapView.standardDrawRectangleForSelection && !map.isPrinting();
	}

	public void onPreNodeMoved(final NodeModel oldParent, final int oldIndex, final NodeModel newParent,
	                           final NodeModel child, final int newIndex) {
	}

	@Override
	protected void validateTree() {
		if(mainView == null){
			NodeViewFactory.getInstance().initNodeView(this);
		}
		super.validateTree();
	}

	public void addContent(JComponent component, int pos) {
		component.putClientProperty("NODE_VIEW_CONTENT_POSITION", pos);
		final Container contentPane = getContentPane();
		for (int i = 0; i < contentPane.getComponentCount(); i++) {
			JComponent content = (JComponent) contentPane.getComponent(i);
			if (pos < (Integer) content.getClientProperty("NODE_VIEW_CONTENT_POSITION")) {
				contentPane.add(component, i);
				return;
			}
		}
		contentPane.add(component);
	}

	public JComponent removeContent(int pos) {
		return removeContent(pos, true);
	}

	private JComponent removeContent(int pos, boolean remove) {
		if (contentPane == null)
			return null;
		for (int i = 0; i < contentPane.getComponentCount(); i++) {
			JComponent component = (JComponent) contentPane.getComponent(i);
			Integer contentPos = (Integer) component.getClientProperty("NODE_VIEW_CONTENT_POSITION");
			if (contentPos == null){
				continue;
			}
			if (contentPos == pos) {
				if (remove) {
					component.putClientProperty("NODE_VIEW_CONTENT_POSITION", null);
					contentPane.remove(i);
				}
				return component;
			}
			if (contentPos > pos) {
				return null;
			}
		}
		return null;
	}

	public JComponent getContent(int pos) {
		return removeContent(pos, false);
	}

	void updateNoteViewer() {
		ZoomableLabel note = (ZoomableLabel) getContent(NOTE_VIEWER_POSITION);
		String oldText = note != null ? note.getText() : null;
		String newText;
		if (getMap().showNotes()) {
			final TextController textController = TextController.getController();
			final String originalText = NoteModel.getNoteText(model);
			try {
				newText = textController.getTransformedTextNoThrow(originalText, model);
				if (!DONT_MARK_FORMULAS && newText != originalText)
					newText = colorize(newText, "green");
			}
			catch (Exception e) {
				newText = colorize(TextUtils.format("MainView.errorUpdateText", originalText, e.getLocalizedMessage())
				    .replace("\n", "<br>"), "red");
			}
		}
		else {
			newText = null;
		}
		if (oldText == null && newText == null) {
			return;
		}
		if (oldText != null && newText != null) {
			ZoomableLabel view = (ZoomableLabel) getContent(NOTE_VIEWER_POSITION);
			view.updateText(newText);
			return;
		}
		if (oldText == null && newText != null) {
			ZoomableLabel view = NodeViewFactory.getInstance().createNoteViewer();
			addContent(view, NOTE_VIEWER_POSITION);
			view.updateText(newText);
			return;
		}
		if (oldText != null && newText == null) {
			removeContent(NOTE_VIEWER_POSITION);
			return;
		}
	}
}
