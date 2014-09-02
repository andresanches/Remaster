import java.awt.*;
import java.awt.event.*;

public final class DrawSurface extends Panel {
	Remaster parent;
	
	DrawSurface(Remaster p) {
		super();
		
		this.parent = p;
		
	    addKeyListener(new KeyAdapter() {
	        public void keyPressed(KeyEvent e) {
	        	switch(e.getKeyCode()) {
	        		case 37:   // Key LEFT
	        			parent.joy.byte1 &= ~Joystick.JOY1_LEFT;
	        			break;
	        		case 39:   // Key RIGHT
	        			parent.joy.byte1 &= ~Joystick.JOY1_RIGHT;
	        			break;
	        		case 38:   // Key UP
	        			parent.joy.byte1 &= ~Joystick.JOY1_UP;
	        			break;
	        		case 40:   // Key DOWN
	        			parent.joy.byte1 &= ~Joystick.JOY1_DOWN;
	        			break;
	        		case 90:   // Key Z
	        			//System.out.println("Fire B");
	        			parent.joy.byte1 &= ~Joystick.JOY1_FIREB;
	        			break;
	        		case 88:   // Key X
	        			parent.joy.byte1 &= ~Joystick.JOY1_FIREA;
	        			break;
	        		case 27:   // ESC: Soft reset
	        			parent.joy.byte2 &= ~Joystick.RESET;
	        			break;
	        		case 32:   // SPACE BAR: Hard pause
	        			if(parent.mainloop != null) {
	        				parent.mainloop.stopEmulation();
	        				parent.mainloop = null;
	        			}
	        			else {
	        				parent.mainloop = new MainThread(parent.screen, parent.cart, parent.memory, parent.vdp, parent.psg, parent.ports, parent.joy, parent.z80, parent.debugger, parent.vramviewer, parent.cramviewer);
	        			    parent.mainloop.start();
	        			}
	        			break;
	        		case 107:  // Numerical PLUS key: Increase frame skip
	        			if(parent.vdp != null) parent.vdp.frameskip++;
	        			break;
	        		case 109:  // Numerical MINUS key: Decrease frame skip
	        			if(parent.vdp != null && parent.vdp.frameskip > 1) parent.vdp.frameskip--;
	        			break;
	        		case 119:  // F8 key - toggle crappy sound synchronization on/off
	        				parent.psg.crappy_sync = !parent.psg.crappy_sync;
	        			break;
	        		default:
	        			//if(parent.z80 != null) parent.z80.msg.println("Key pressed = " + e.getKeyCode());
	        	}
	        }
	      
	        public void keyReleased(KeyEvent e) {
	        	switch(e.getKeyCode()) {
	        		case 37:   // Key LEFT
	        			parent.joy.byte1 |= Joystick.JOY1_LEFT;
	        			break;
	        		case 39:   // Key RIGHT
	        			parent.joy.byte1 |= Joystick.JOY1_RIGHT;
	        			break;
	        		case 38:   // Key UP
	        			parent.joy.byte1 |= Joystick.JOY1_UP;
	        			break;
	        		case 40:   // Key DOWN
	        			parent.joy.byte1 |= Joystick.JOY1_DOWN;
	        			break;
	        		case 90:   // Key Z
	        			parent.joy.byte1 |= Joystick.JOY1_FIREB;
	        			break;
	        		case 88:   // Key X
	        			parent.joy.byte1 |= Joystick.JOY1_FIREA;
	        			break;
	        		case 27:   // Key ESC - Soft reset
	        			parent.joy.byte2 |= Joystick.RESET;
	        			break;
	        	}
	        }
	    } ); // End of KeyListener definition
	}
	
	public void paint(Graphics g) {
		parent.screen.drawScreen(0, 0);
	}

	public void paintAll(Graphics g) {}
	public void repaint() {}
	public void repaint(long tm, int x, int y, int width, int height){}
	public void repaint(int x, int y, int width, int height){}
	
}