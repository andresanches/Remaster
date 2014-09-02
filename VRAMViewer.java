import java.awt.*;
import java.awt.event.*;
import java.io.*;

public final class VRAMViewer extends Frame {
	Screen screen;
	VDP vdp;
	boolean enabled = false;

    private File romFile;
	private FileInputStream fileStream;

	
	public VRAMViewer(VDP vdp) {   // Creates a 16x28 tile matrix to display the VRAM contents
		super("SMS VRAM Viewer - " + Remaster.APPNAME);
		
		this.vdp = vdp;

        setLocation(500, 5);
        setSize(266, 540);
        
        screen = new Screen(this, 128, 256);
		addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { setVisible(false); enabled = false; } } );
	}
	
	public void toggleEnabled() {
		enabled = !enabled;
		setVisible(enabled);
	}

	public void update() {
		for(int j=0; j < 28; j++)
			for(int i=0; i < 16; i++)
				vdp.drawTile(screen, i << 3, j << 3, (j << 4) + i);

		screen.drawScreen(5, 23);
	}
	
	public void paint(Graphics g) {
	  	if(screen != null) screen.drawScreen(5, 23);
	  	else {
	  	  	g.setColor(Color.BLACK);
	  		g.fillRect(0, 0, this.getWidth(), this.getHeight());
	  	}
	}
}
