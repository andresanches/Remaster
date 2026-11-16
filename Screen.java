import java.awt.*;
import java.awt.image.*;
import java.util.Arrays;

public final class Screen {
  int           width, height, numPixels;
  Component     drawsurface;
  BufferedImage screen;
  int           pixels[];  // framebuffer storage, the VDP writes pixels directly here
  Window        win;
  boolean       fullscreen;
  
  public Screen(Component drawsurface, int width, int height) {
    this.drawsurface = drawsurface;
    this.width     = width;
    this.height    = height;
    this.numPixels = this.width * this.height;
    fullscreen = false;
    
    // TYPE_INT_RGB is backed by a plain int[], retrieved below through the
    // DataBufferInt, so the VDP writes pixels straight into the framebuffer:
    // no per-pixel setRGB calls and no color-comparing buffer needed. The
    // blit to the screen in drawScreen() is still accelerated by the Java 2D
    // pipeline (Metal/Direct3D/XRender), which is where compatibility images
    // actually helped.
    this.screen = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);
    this.pixels = ((DataBufferInt)this.screen.getRaster().getDataBuffer()).getData();
    Arrays.fill(pixels, 0);
  }
  
  public void setDrawsurface(Component d) {
  	this.drawsurface = d;
  }

  public final void setPixel(int x, int y, int color) {
  	if((x >= 0) && (x < width) && (y >= 0) && (y < height)) // clip
  	  pixels[x + (y * width)] = color;
  }
  
  public final void clearBuffer() {
	Arrays.fill(pixels, 0);
  }

  public final void fillRect(int x, int y, int width, int height, int color) {
  	for(int j=y; j < y+height; j++)
  		for(int i=x; i < x+width; i++)
  			setPixel(i, j, color);
  }

  // Active rendering: draw into the surface's back buffer and flip it (show),
  // instead of painting straight onto the component with getGraphics().
  // The contentsLost/contentsRestored loops deal with window resizes,
  // minimized windows and other buffer invalidations; tearing is gone because
  // the flip only happens on a complete frame.
  public final void drawScreen(int x, int y) {
    BufferStrategy bs = getStrategy();
    if(bs == null) { tryCreateStrategy(); return; }  // strategy will be ready by the next frame
    
    try {
      do {
        do {
          Graphics g = bs.getDrawGraphics();
          if(!fullscreen) g.drawImage(this.screen, x, y, drawsurface.getWidth(), drawsurface.getHeight(), null);
          else            g.drawImage(this.screen, 32, 0, null);  // 1:1, centered in the 320x200 display mode
          g.dispose();
        } while(bs.contentsRestored());
        bs.show();
      } while(bs.contentsLost());
    }
    catch(IllegalStateException | NullPointerException e) {
      // Resizing or re-creating a heavyweight component destroys its buffer
      // strategy at any moment (usually on the AWT thread, mid-blit for us).
      // The stale strategy object then throws instead of reporting lost
      // contents, so recreate it and skip this frame; the next one redraws.
      tryCreateStrategy();
    }
  }
  
  private final void tryCreateStrategy() {
    if(!drawsurface.isDisplayable()) return;
    try { createStrategy(); }
    catch(RuntimeException e) {}  // peer not ready/unsupported config: retry next frame
  }
  
  // createBufferStrategy/getBufferStrategy are public only on Window and Canvas
  // (they're protected in Component and in Panel), so dispatch on the surface's
  // class. Frame and Dialog are Window subclasses; drawing surfaces here are
  // Canvas subclasses or the fullscreen Window.
  private final BufferStrategy getStrategy() {
    if(drawsurface instanceof Window) return ((Window)drawsurface).getBufferStrategy();
    if(drawsurface instanceof Canvas) return ((Canvas)drawsurface).getBufferStrategy();
    return null;
  }
  
  private final void createStrategy() {
    if(drawsurface instanceof Window)      ((Window)drawsurface).createBufferStrategy(2);
    else if(drawsurface instanceof Canvas) ((Canvas)drawsurface).createBufferStrategy(2);
  }
  
  public void toggleFullScreen(Remaster frame) {
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
  		if(win != null) { win.dispose(); win = null; }  // release the native peer of the fullscreen window
  		drawsurface = frame.drawsurface;
  		fullscreen = false;
  		// a new BufferStrategy is created lazily on the next drawScreen()
    }
  }
}
