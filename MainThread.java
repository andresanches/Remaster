public final class MainThread extends Thread {
  Cartridge cart;
  MemoryManager memory;
  VDP vdp;
  Ports ports;
  EZ80 z80;
  Screen screen;
  PSG psg;
  
  VRAMViewer vramviewer;
  CRAMViewer cramviewer;
  Debugger debugger;
  
  boolean running;

  public MainThread(Screen screen, Cartridge cart, MemoryManager memory, VDP vdp, PSG psg, Ports ports, Joystick joy, EZ80 z80, Debugger debugger, VRAMViewer vramviewer, CRAMViewer cramviewer) {
  	this.cart = cart;    
    this.memory = memory;
    this.screen = screen;
    this.vdp = vdp;
    this.psg = psg;
    this.ports = ports;
    this.z80 = z80;
    this.vramviewer = vramviewer;
    this.cramviewer = cramviewer;
    this.debugger = debugger;

    running = false;
    
    setPriority(Thread.NORM_PRIORITY);
  }

  public void SMS_reset() {
    memory.reset();
    vdp.reset();
    psg.reset();
    z80.reset();

    memory.loadFromCartridge();

    z80.sp = 0xDFF0;   // The SMS bios sets these values at power up.
    z80.flagreg = EZ80.FLAG_ZERO;
    z80.ix = 0x0000;
    z80.iy = 0x0000;
    
    screen.clearBuffer();
    screen.drawScreen(0, 0);
  }

  public void stopEmulation() {
    running = false;
  }

  public final void run() {
    System.out.println("EMULATOR: Starting main thread.");

    int drawframe = 0, drawline = 0;
    running = true;

    while(running) {
        if(vdp.scanline < 192) {
            z80.execute(219);

        	if(--vdp.hintcounter < 0) {  // Line interrupt
        		if((vdp.regs[0] & 0x10) != 0)
            	    z80.setIRQ();
        	    vdp.hintcounter = vdp.regs[10];
            }

            if(drawframe == 0) vdp.renderLine();
         	z80.execute(9);
        }
        
        else if(vdp.scanline == 193) {
            vdp.regs[9] = vdp.vscroll_buf;  // updates V Scroll register with buffered value
            z80.execute(219);

            vdp.status |= 0x80;   // Enable frame interrupt pending flag in vdp
            vdp.hintcounter = vdp.regs[10];

       	  	if((vdp.regs[01] & 0x20) != 0) { // Frame interrupt
       	  		z80.setIRQ();
       	  	}

       	  	z80.execute(9);
        }
        else {   // Vblank
          z80.execute(228);
        }
        if(vdp.scanline++ == 262) {
        	vdp.regs[9] = vdp.vscroll_buf;  // updates V Scroll register
        	vdp.scanline = 0;
        	
            if(debugger.enabled) z80.updateDebugger();
            
            sync(); // crappy audio synchronization and frame skipping technique
	        psg.output();

	        if(drawframe == 0) {
  	          if(vramviewer.enabled) vramviewer.update();
  	          if(cramviewer.enabled) cramviewer.update();

  	          if((vdp.regs[1] & 0x40) == 0x40) {
  	          	screen.drawScreen(0, 0);
  	          }
  	          else {
  	          	screen.clearBuffer();
  	          	screen.drawScreen(0, 0);
  	          }
            }
       	  	drawframe = (drawframe + 1) % vdp.frameskip;
        }
    }
  }
  
  public void sync() {
	if(!psg.enabled || !psg.crappy_sync) return;
		
	int available = psg.line.available();
	// hold if there's too much data stored in the audio line buffer.
	if(available == psg.bufsiz) vdp.frameskip++;
	else {
		while(available < (psg.bufsiz - (psg.bufferLength << 2))) available = psg.line.available();
		if(vdp.frameskip > 1) vdp.frameskip--;
	}
  }
  
  public void exit() {
  	memory.dumpMemory();
  	vdp.dumpMemory();
  }
}
