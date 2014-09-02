import java.awt.*;
import java.awt.event.*;

public final class CRAMViewer extends Frame {
	VDP vdp;
	Screen screen;
	Panel panel;
	boolean enabled = false;
	
	public CRAMViewer(VDP vdp) {
		super("SMS CRAM Viewer - " + Remaster.APPNAME);
		
		this.vdp = vdp;
		panel = new Panel();
		add(panel);

		screen = new Screen(panel, 512, 40);
		setResizable(false);
		setLocation(0,450);
		setSize(522, 69);
		
		addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { setVisible(false); enabled = false; } } );
	}
	
	public void toggleEnabled() {
		enabled = !enabled;
		setVisible(enabled);
	}
	
	public void update() {
		for(int i=0; i < 32; i++) {
			screen.fillRect(i << 4, 0, 16, 40, vdp.palette[vdp.cram[i] & 0x3F]);
		}
		screen.drawScreen(0, 0);
	}
	
	public void paint(Graphics g) {
		if(screen != null) screen.drawScreen(5, 23);
	  	else {
	  	  	g.setColor(Color.BLACK);
	  		g.fillRect(0, 0, this.getWidth(), this.getHeight());
	  	}
	}
}