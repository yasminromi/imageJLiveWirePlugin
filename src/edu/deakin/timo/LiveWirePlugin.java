/*
 Image/J Plugins
 Copyright (C) 2012 - 2014 Timo Rantalainen
 Author's email: tjrantal at gmail dot com
 The code is licensed under GPL 3.0 or newer
 */
package	edu.deakin.timo;

import ij.*;
import ij.gui.*;
import ij.plugin.*;
import ij.plugin.frame.RoiManager;
import ij.measure.*;	/*For obtaining pixel dimensions from original stack...*/
import ij.gui.*;			/*For creating the output stack images*/
import ij.process.*;		/*For setting output stack image properties*/
import ij.io.*;			/*For setting image voxel dimensions...*/
import java.util.*;				/*For enumeration*/
import java.awt.*;
import java.awt.event.*;		/**MouseListener*/
import edu.deakin.timo.liveWireEngine.*;	/**Live wire implementation*/

/*
	LiveWire ImageJ plug-in modified from ivus snakes (http://ivussnakes.sourceforge.net/) ImageJ plugin 
	Changed the implementation back to the one suggested in Barret & Mortensen 1997.
	Interactive live-wire boundary extraction. Medical Image Analysis (1996/7) volume 1, number 4, pp 331-341.
 */

public class LiveWirePlugin implements PlugIn, MouseListener, MouseMotionListener, KeyListener {
	ImageCanvas canvas;
	ImagePlus imp;
	PolygonRoi roi;
	Polygon polygon;
	ArrayList<Polygon> polygons;
	RoiManager rMan;
	Overlay over;
	int width;
	int height;
	LiveWireCosts lwc;
	
	/**Implement the PlugIn interface*/
    public void run(String arg) {
        imp = WindowManager.getCurrentImage();
        /*Check that an image was open*/
		if (imp == null) {
            IJ.noImage();
            return;
        }
		/*Get image size and stack depth*/
		width = imp.getWidth();
		height = imp.getHeight();

		init();		
		/**Pop up Roi Manager*/
		rMan = new RoiManager();

		/*Register listener*/
		ImageWindow win = imp.getWindow();
		canvas = win.getCanvas();
		canvas.addMouseListener(this);
		canvas.addMouseMotionListener(this);
		canvas.addKeyListener(this);

		
		
		
		
		
		

    }
	
	
	protected void init(){
		/*Init polygon stack for history*/
		polygons = new ArrayList<Polygon>();
		/*Init livewire*/
		double[][] pixels = new double[width][height];
		short[] tempPointer = (short[]) imp.getProcessor().getPixels();	
		for (int r = 0;r<height;++r){
			for (int c = 0;c<width;++c){
				pixels[c][r] = (double) tempPointer[c+r*width];
			}
		}
		lwc = new LiveWireCosts(pixels);
		polygon = new Polygon();
		
		/**Add overlay*/
		if (imp.getOverlay() == null){
			over = new Overlay();
			imp.setOverlay(over);
		}else{
			over = imp.getOverlay();
		}
		
	}
	
	/*Implement the MouseListener, and MouseMotionListener interfaces*/
	public void mousePressed(MouseEvent e) {
		/*If alt is pressed when a button is clicked, disconnect listeners, i.e. stop the plugin*/
		if (e.getClickCount() > 1){
			//Do not remove the last point, simply connect last point, and initial point
			//IJ.log("Add point "+polygon.npoints);
			polygon.addPoint(polygon.xpoints[0],polygon.ypoints[0]);
			
			//IJ.log("Appended, length after "+polygon.npoints);
			roi = new PolygonRoi(polygon,Roi.POLYGON);
			
			
			/*Add the roi to an overlay, and set the overlay active*/
			//IJ.log("Add ROI");
			imp.setRoi(roi,true);
			//IJ.log("Add overlay");
			over.add(roi);
			//imp.setOverlay(over);
			
			/**Add the segmented area to the roiManager*/
			//IJ.log("Add rMan");
			rMan.addRoi(roi);
			
			/**Reset polygons*/
			//IJ.log("REset");
			init();
			//IJ.log("All done");
			/**Remove listeners*/
			//IJ.log("Start removing listeners");
			//((ImageCanvas) e.getSource()).removeMouseListener(this);
			//((ImageCanvas) e.getSource()).removeMouseMotionListener(this);
			//IJ.log("All done");
		}
		
		
		//IJ.log("Right button: "+((e.getModifiers()&Event.META_MASK)!=0));
	}

	public void mouseReleased(MouseEvent e) {
		
		/**Ignore second and further clicks of a double click*/
		if (e.getClickCount() < 2){
			int screenX = e.getX();
			int screenY = e.getY();
			int x = canvas.offScreenX(screenX);
			int y = canvas.offScreenY(screenY);
			/*Backpedal polygon to the previous one*/
			if(polygons.size()>0 && ((e.getModifiersEx() & InputEvent.CTRL_MASK) != 0||  (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0)){
					//Get the previous polygon
					Polygon tempP = polygons.get(polygons.size()-1);
					int[] pX = new int[tempP.npoints];
					int[] pY = new int[tempP.npoints];
					for (int i = 0;i< tempP.npoints;++i){
						pX[i] = tempP.xpoints[i];
						pY[i] = tempP.ypoints[i];
					}
					polygon = new Polygon(pX,pY,pX.length);
					polygons.remove(polygons.size()-1);	//Remove the previous polygon
					roi = new PolygonRoi(polygon,Roi.POLYLINE);
					imp.setRoi(roi,true);
					lwc.setSeed(pX[pX.length-1],pY[pX.length-1]);
			}else{
				//Draw the latest Polygon
				if (polygon.npoints > 0){
					//Store a copy of the previous polygon
					int[] tX = new int[polygon.npoints];
					int[] tY = new int[polygon.npoints];
					for (int i = 0;i< polygon.npoints;++i){
						tX[i] = polygon.xpoints[i];
						tY[i] = polygon.ypoints[i];
					}
					polygons.add(new Polygon(tX,tY,tX.length));	//Store the previous polygon
					//Update the polygon
					if ((e.getModifiersEx() & InputEvent.SHIFT_MASK) != 0||  (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0){
						//Get a straight line
						polygon.addPoint(x,y);
					}else{
						//Get polygon from livewire
						int[][] fromSeedToCursor = lwc.returnPath(x,y);
						int[] pX = new int[polygon.npoints+fromSeedToCursor.length];
						int[] pY = new int[polygon.npoints+fromSeedToCursor.length];
						for (int i = 0;i< polygon.npoints;++i){
							pX[i] = polygon.xpoints[i];
							pY[i] = polygon.ypoints[i];
						}
						for (int i = 0;i< fromSeedToCursor.length;++i){
							pX[polygon.npoints+i] = fromSeedToCursor[i][0];
							pY[polygon.npoints+i] = fromSeedToCursor[i][1];
						}
						polygon = new Polygon(pX, pY, pX.length);
					}
					//Get, and set the ROI
					roi = new PolygonRoi(polygon,Roi.POLYLINE);
					imp.setRoi(roi,true);
				
				}else{
					polygon.addPoint(x,y);
					lwc.setSeed(x,y);
				}
				lwc.setSeed(x,y);
			
			}
		}
	}
	
	public void mouseDragged(MouseEvent e) {
	}


	public void mouseExited(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {}	
	public void mouseEntered(MouseEvent e) {}
	public void mouseMoved(MouseEvent e) {
		if (polygon.npoints > 0){
			int screenX = e.getX();
			int screenY = e.getY();
			int x = canvas.offScreenX(screenX);
			int y = canvas.offScreenY(screenY);
			int[] pX;
			int[] pY;
			if ((e.getModifiersEx() & InputEvent.SHIFT_MASK) != 0||  (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0){
				pX = new int[polygon.npoints+1];
				pY = new int[polygon.npoints+1];
				for (int i = 0;i< polygon.npoints;++i){
					pX[i] = polygon.xpoints[i];
					pY[i] = polygon.ypoints[i];
				}
				pX[polygon.npoints] = x;
				pY[polygon.npoints] = y;				
			} else {
				//Use livewire
				int[][] fromSeedToCursor = lwc.returnPath(x,y);
				//IJ.log("Return path length "+fromSeedToCursor.length);
				pX = new int[polygon.npoints+fromSeedToCursor.length];
				pY = new int[polygon.npoints+fromSeedToCursor.length];
				for (int i = 0;i< polygon.npoints;++i){
					pX[i] = polygon.xpoints[i];
					pY[i] = polygon.ypoints[i];
				}
				for (int i = 0;i< fromSeedToCursor.length;++i){
					pX[polygon.npoints+i] = fromSeedToCursor[i][0];
					pY[polygon.npoints+i] = fromSeedToCursor[i][1];
				}
			}
			imp.setRoi(new PolygonRoi(pX, pY, pX.length, Roi.POLYLINE),true);			
		}
	}
	
	/**Implement KeyListener*/
	/**Invoked when a key has been pressed.*/
	public void 	keyPressed(KeyEvent e){
		/**Shut down the plug-in*/
		if (e.getExtendedKeyCode() == KeyEvent.getExtendedKeyCodeForChar(KeyEvent.VK_Q) || e.getKeyChar() == 'q'){
			/**Remove listeners*/
			//IJ.log("Start removing listeners");
			((ImageCanvas) e.getSource()).removeMouseListener(this);
			((ImageCanvas) e.getSource()).removeMouseMotionListener(this);
			((ImageCanvas) e.getSource()).removeKeyListener(this);
			//IJ.log("All done");
		}
	}
	/**Invoked when a key has been released.*/
	public void 	keyReleased(KeyEvent e){
	
	}
	/**Invoked when a key has been typed.*/
	public void 	keyTyped(KeyEvent e){
	
	}

}
