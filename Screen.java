import java.awt.*;
import java.awt.image.*;
import java.util.Arrays;

public final class Screen {
  int           width, height, numPixels;
  Component     drawsurface;
  BufferedImage screen;
  int           fpixels[];  // for pixel color comparing
  Graphics2D    g, g2;
  Window        win;
  boolean       fullscreen;
  
  public Screen(Component drawsurface, int width, int height) {
    this.drawsurface = drawsurface;
    this.width     = width;
    this.height    = height;
    this.numPixels = this.width * this.height;
    fpixels = new int[numPixels];
    Arrays.fill(fpixels, 0xFFFFFF);
    fullscreen = false;
    
    // Standard way to create a buffered image
    //this.screen = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);
    
    // Create a buffered image compatible with the current graphics
    // environment color model. Should be faster but turns out that
    // there's no much difference, so it's here just for future reference.
    this.screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(width, height);

    this.screen.getGraphics().setColor(new Color(0));

    g2 = (Graphics2D)screen.getGraphics();
    g2.setBackground(new Color(0, 0, 0));
  }
  
  public void setDrawsurface(Component d) {
  	this.drawsurface = d;
  }

  public final void setPixel(int x, int y, int color) {
  	int location = x + (y * width);
  	if((x > 0) && (x < width) && (y > 0) && (y < height) && (fpixels[location] != color)) { // clip and avoid calling setRGB for repeated pixel colours
  	  screen.setRGB(x, y, color);
  	  fpixels[location] = color;
  	}
  }
  
  public final void clearBuffer() {
	g2.setColor(new Color(0));
	g2.fillRect(0, 0, width, height);
	Arrays.fill(fpixels, 0);
  }

  public final void fillRect(int x, int y, int width, int height, int color) {
  	for(int j=y; j < y+height; j++)
  		for(int i=x; i < x+width; i++)
  			setPixel(i, j, color);
  }

  public final void drawScreen(int x, int y) {
  	if(g == null) g = (Graphics2D)drawsurface.getGraphics();
  	
	if(!fullscreen) g.drawImage(this.screen, x, y, drawsurface.getWidth(), drawsurface.getHeight(), drawsurface);
  	//if(!fullscreen) g.drawImage(this.screen, x, y, width, height, drawsurface);
	else g.drawImage(this.screen, 32, 0, drawsurface);
  }
  
  public void toggleFullScreen(Remaster frame) {
  	g = null;
  	GraphicsDevice sd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
  	if(!fullscreen) {
	    win = new Window(frame);
		win.addKeyListener(frame.drawsurface.getKeyListeners()[0]);
		win.setBackground(new Color(0));
		drawsurface = win;
		sd.setFullScreenWindow(win);
		win.requestFocus();
		
	  	DisplayMode[] available = sd.getDisplayModes();
	  	for(int i=0; i < available.length; i++) {
	  		if(available[i].getWidth() == 320 && available[i].getHeight() == 200 && available[i].getBitDepth() == 16 && available[i].getRefreshRate() == 60) {
	  			sd.setDisplayMode(available[i]);
	  			break;
	  		}
	  	}
	  	fullscreen = true;
  	}
  	else {
  		sd.setFullScreenWindow(null);
  		if(win != null) win = null;
  		drawsurface = frame.drawsurface;
  		fullscreen = false;
    }
  }
}