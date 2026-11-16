import java.awt.*;
import java.awt.image.*;
import java.util.Arrays;

public final class Screen {
  int           width, height, numPixels;
  Component     drawsurface;
  BufferedImage screen;
  int           pixels[];  // framebuffer storage, the VDP writes pixels directly here
  Rectangle     windowedBounds;  // window bounds to restore when leaving fullscreen
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
          else {
            // Borderless fullscreen: scale the 4:3 picture to fit the whole
            // screen and center it. The black fill underneath becomes the
            // letterbox bars (sidebars on a modern 16:9 panel).
            int sw = drawsurface.getWidth(), sh = drawsurface.getHeight();
            double s = Math.min((double)sw / width, (double)sh / height);
            int dw = (int)Math.round(width * s), dh = (int)Math.round(height * s);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, sw, sh);
            g.drawImage(this.screen, (sw - dw) / 2, (sh - dh) / 2, dw, dh, null);
          }
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
  // class. Drawing surfaces here are Canvas subclasses (DrawSurface, the CRAM
  // viewer's swatch); the Window case is kept for robustness.
  private final BufferStrategy getStrategy() {
    if(drawsurface instanceof Window) return ((Window)drawsurface).getBufferStrategy();
    if(drawsurface instanceof Canvas) return ((Canvas)drawsurface).getBufferStrategy();
    return null;
  }
  
  private final void createStrategy() {
    if(drawsurface instanceof Window)      ((Window)drawsurface).createBufferStrategy(2);
    else if(drawsurface instanceof Canvas) ((Canvas)drawsurface).createBufferStrategy(2);
  }
  
  // Pseudo-fullscreen: instead of Full-Screen Exclusive Mode (setFullScreenWindow
  // + setDisplayMode, a 2003-era Windows API that modern macOS mostly ignores or
  // mishandles), the main window itself turns borderless and maximized, covering
  // the screen so it looks like fullscreen. It keeps the desktop resolution,
  // works on every platform, and stays the same window (same Canvas, same key
  // listeners), so there is no second window to hand focus or strategies to.
  public void toggleFullScreen(Remaster frame) {
    if(!fullscreen) {
      windowedBounds = new Rectangle(frame.getLocation(), frame.getSize());
      fullscreen = true;   // tell drawScreen() and the aspect listener before the resize storm
      frame.dispose();     // setUndecorated() only works on a hidden frame; this also
                           // destroys the BufferStrategy, recreated right after showing
      frame.setUndecorated(true);
      frame.setAlwaysOnTop(true);
      frame.setExtendedState(Frame.MAXIMIZED_BOTH);
      frame.setVisible(true);
      tryCreateStrategy();
      drawsurface.requestFocus();
    }
    else {
      frame.dispose();
      frame.setUndecorated(false);
      frame.setAlwaysOnTop(false);
      frame.setExtendedState(Frame.NORMAL);
      if(windowedBounds != null) frame.setBounds(windowedBounds);
      fullscreen = false;
      frame.setVisible(true);
      // some platforms drop the menu bar while the frame is undecorated
      if(frame.getMenuBar() == null && frame.menubar != null) frame.setMenuBar(frame.menubar);
      tryCreateStrategy();
      drawsurface.requestFocus();
    }
  }
}
