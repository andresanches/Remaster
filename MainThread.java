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

  FrameClock frameClock;
  int currentSpeed;

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

    frameClock = new FrameClock(60);
    currentSpeed = vdp.speed;
    if(currentSpeed < 1) currentSpeed = 1;
    if(currentSpeed > 4) currentSpeed = 4;
    frameClock.setSpeed(currentSpeed);

    running = false;
    
    setPriority(Thread.NORM_PRIORITY);
  }

  public void SMS_reset() {
    memory.reset();
    vdp.reset();
    psg.reset();
    z80.reset();

    memory.loadFromCartridge();
    memory.loadCartRam();

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

    frameClock.reset();

    int drawframe = 0, drawline = 0, ram_save_frames = 0, audioCounter = 0;
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

        	if(++ram_save_frames >= 300) { // flush battery-backed RAM to disk every ~5 seconds
        	  memory.saveCartRam();          // does nothing if the game has no SRAM or wrote nothing
        	  ram_save_frames = 0;
        	}
        	
            if(debugger.enabled) z80.updateDebugger();
            
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
            if(currentSpeed != vdp.speed) {
              currentSpeed = vdp.speed;
              if(currentSpeed < 1) currentSpeed = 1;
              if(currentSpeed > 4) currentSpeed = 4;
              frameClock.setSpeed(currentSpeed);
              audioCounter = 0;
            }

            if(audioCounter == 0) psg.output();
            audioCounter = (audioCounter + 1) % currentSpeed;
            sync();
        }
    }
  }
  
  public void sync() {
    frameClock.endFrameAndSleep();
  }
  
  public void exit() {
  	memory.dumpMemory();
  	vdp.dumpMemory();
  }
}
