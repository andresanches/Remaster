import java.util.*;
import java.io.*;

public final class VDP {
  MemoryManager memory;
  Screen screen;
  byte[] vram;
  byte[] cram;
  int[] regs;
  int[] palette;
  int address;
  int status, latch, code, readbuf, vscroll_buf;
  boolean WaitAddress;
  int hintcounter, scanline;
  public int frameskip = 1, framecount;
  
  public VDP(Screen screen) {
  	this.screen = screen;
    vram = new byte[0x4000];
    cram = new byte[40];
    regs = new int[16];

    initPal(); // Setup BBGGRR -> 24bit RGB conversion values
    reset();
  }

  public void reset() {
    Arrays.fill(vram, (byte)0);
    Arrays.fill(cram, (byte)0);
    Arrays.fill(regs, 0);
    
    regs[2] = 0x0E;
    regs[5] = 0x7E;
    
    address = 0;
    status = 0;
    scanline = 0;
    latch = 0;
    code = 0;
    readbuf = 0;
    WaitAddress = false;
    hintcounter = regs[10];
    
    framecount = 0;
  }
  
  public final int data_port_read() {
    WaitAddress = false;

    int retval = readbuf;
    
    readbuf = vram[address];
    readbuf &= 0xFF;
    address++;
    address &= 0x3FFF;
    return retval;
  }
  
  public final int control_port_read() {
  	int retval = status;
    WaitAddress = false;
  	status = status & 0x1F;
    return(retval);
  }
  
  public final void data_port_write(int val) {
    WaitAddress = false;

    if(code == 3)
       cram[address & 0X1F] = (byte)(val & 0xFF);
    else {
        vram[(address & 0x3FFF)] = (byte)(val & 0xFF);
//        if(address < 0x2000) System.out.println("VRAM WRITE");
    }

    address = (address + 1) & 0x3FFF;
  }
  
  public final void control_port_write(int val) {
    if(WaitAddress) { // 2nd write to address port
      code = ((val >> 6) & 0x3); // updates code register

      switch(code) {
      	case 0: // Load readbuf with the byte contained in address and increment address
            address = (((val & 0x3F) << 8) | latch);
            readbuf = vram[(address++)] & 0xFF;
            address &= 0x3FFF; // Wrap address to zero when over 0x3FFF
      		break;

      	case 2: // VDP Register write
            register_write((val & 0xF), latch);
            break;

      	default: // if code is 1 or 3
            address = (((val & 0x3F) << 8) | latch);
      }
    }
    else // 1st write to control port
      latch = val & 0xFF;

    WaitAddress = !WaitAddress;
  }
  
  public final void register_write(int reg, int val) {
  	if(reg == 9) vscroll_buf = val; // register 9 will be updated at the end of the active frame put value in buffer
  	else regs[reg] = val & 0xFF;
  }
  
  public void dumpMemory() {
  	try {
  	  FileOutputStream memdump = new FileOutputStream(new File("vram.bin"));
	  memdump.write(vram);
	  memdump = null;
	  memdump = new FileOutputStream(new File("cram.bin"));
	  memdump.write(cram);
	  memdump = null;
	  
	  System.out.println("VIDEO MEMORY: dumped VRAM to VRAM.BIN and CRAM to CRAM.BIN");
  	}
    catch(IOException e) {
      System.out.println("MAIN MEMORY: Error dumping memory!");
    }
  }
  
  public void initPal() { // Calculate palette for conversion from BBRRGG to 24-bit RGB.
  	palette = new int[64]; // 64 possible colors
  	for(int i=0; i < 64; i++) {
  		palette[i]  = ((i & 3) * 80) << 16;        // Red value
  		palette[i] |= (((i >> 2) & 3) * 80) << 8; // Green value
  		palette[i] |= (((i >> 4) & 3) * 80);      // Blue value
  	}  	
  }
  
  // draws tile for the VRAM Viewer
  public final void drawTile(Screen screen, int x, int y, int tileindex) {
  	int index = 0, b0 = 0, b1 = 0, b2 = 0, b3 = 0;
	for(int j=0; j < 8; j++) {    // 8 pixels for each of the 8 tile line (8x8 tile)
	  index = ((tileindex << 5) + (j << 2));
	  b0 = vram[index]; b1 = vram[index+1]; b2 = vram[index+2]; b3 = vram[index+3];
	  screen.setPixel(x + 0, y + j, palette[cram[((((b3 >> 4) & 0x8) | ((b2 >> 5) & 0x4) | ((b1 >> 6) & 0x2) | ((b0 >> 7) & 0x1)) & 0xF)] & 0x3F]);
	  screen.setPixel(x + 1, y + j, palette[cram[((((b3 >> 3) & 0x8) | ((b2 >> 4) & 0x4) | ((b1 >> 5) & 0x2) | ((b0 >> 6) & 0x1)) & 0xF)] & 0x3F]);
	  screen.setPixel(x + 2, y + j, palette[cram[((((b3 >> 2) & 0x8) | ((b2 >> 3) & 0x4) | ((b1 >> 4) & 0x2) | ((b0 >> 5) & 0x1)) & 0xF)] & 0x3F]);
	  screen.setPixel(x + 3, y + j, palette[cram[((((b3 >> 1) & 0x8) | ((b2 >> 2) & 0x4) | ((b1 >> 3) & 0x2) | ((b0 >> 4) & 0x1)) & 0xF)] & 0x3F]);
	  screen.setPixel(x + 4, y + j, palette[cram[((((b3 >> 0) & 0x8) | ((b2 >> 1) & 0x4) | ((b1 >> 2) & 0x2) | ((b0 >> 3) & 0x1)) & 0xF)] & 0x3F]);
	  screen.setPixel(x + 5, y + j, palette[cram[((((b3 << 1) & 0x8) | ((b2 >> 0) & 0x4) | ((b1 >> 1) & 0x2) | ((b0 >> 2) & 0x1)) & 0xF)] & 0x3F]);
	  screen.setPixel(x + 6, y + j, palette[cram[((((b3 << 2) & 0x8) | ((b2 << 1) & 0x4) | ((b1 >> 0) & 0x2) | ((b0 >> 1) & 0x1)) & 0xF)] & 0x3F]);
	  screen.setPixel(x + 7, y + j, palette[cram[((((b3 << 3) & 0x8) | ((b2 << 2) & 0x4) | ((b1 << 1) & 0x2) | ((b0 >> 0) & 0x1)) & 0xF)] & 0x3F]);
	}
  }

  public final void renderLine() {
  	int scan = scanline;
    int[] foreline = new int[320]; // Tiles with priority = 1
    int spr_x[] = new int[8], spr_y[] = new int[8], spr_index[] = new int[8]; // 8 sprite buffer

  	int baseAddress = (regs[2] & 0xE) << 10; // Name table base address in VRAM

  	// Scroll values
  	int startcol;
  	int finescrollH;
  	if(scan < 16 && (regs[0] & 0x40) != 0) {
  		startcol = 0;
  		finescrollH = 0;
  	}
  	else {
  		startcol = 32 - ((regs[8] & 0xF8) >> 3);
  		finescrollH = regs[8] & 0x7;
  	}

	if((regs[9] & 0xFF) > 223) regs[9] -= 224; // Vertical scroll wraps past 223
	
	int startrow = (regs[9] & 0xF8) >> 3;
	int finescrollV = regs[9] & 0x7;
	
	int i, x; // Column and X pixel position counter
	int col, row, cramval, p;

	i = (scan + finescrollV);
	int tileRow = i >> 3, tileLine = i & 0x7;

  	// Tile definition in nametable
  	int tileDef, tileIndex, tline, whichpal, priority, flipX, flipY;
  	
	// Cycle through background table
  	int regs0 = regs[0], regs2 = regs[2];
	for(i = 0, x = finescrollH; i < 32; i++, x += 8) {  // tile counter
  		// adjust vertical scroll values for columns 24 to 31
  		if((i > 23) && ((regs0 & 0x80) != 0)) {
  			tileRow = scan >> 3;
  			tileLine = scan & 7;
  			startrow = 0;
  		}
  		
  		// retrieve tile definition from name table
  		row = tileRow + startrow;
  		if(row > 27) row -= 28;
  		row &= (0xF | ((regs2 & 1) << 4)); // logically AND bit 5 from row with bit 1 of register 2
  	    row = row << 6;
  	    
  	    col = (((i << 1) + (startcol << 1)) & 0x3F);
  	    p = baseAddress + row + col;
  		tileDef  = vram[p] & 0xFF;
  		tileDef |= (vram[p + 1] & 0xFF) << 8;

  		// retrieve tile atributes
  		tileIndex = tileDef & 0x1FF;
  		priority  = tileDef & 0x1000; 
  		whichpal  = (tileDef >> 7) & 0x10;
  		flipY     = tileDef & 0x400;
  		flipX     = tileDef & 0x200;

  		if(flipY != 0) tileIndex = (tileIndex << 5) + (28 - (tileLine << 2));
  	    else           tileIndex = (tileIndex << 5) + (tileLine << 2);

  		// retrieve tile line from vram and store it in a 32bit local variable for faster access
  		tline = ((vram[tileIndex] & 0xFF) << 24) | ((vram[tileIndex + 1] & 0xFF) << 16) | ((vram[tileIndex + 2] & 0xFF) << 8) | (vram[tileIndex + 3] & 0xFF);
  		// START OF TILE LINE RENDERING CODE ------------------------------------------------------
  		// Render pixel 1 of 8
		cramval  = (tline >> 31) & 1;
		cramval |= (tline >> 22) & 2;
		cramval |= (tline >> 13) & 4;
		cramval |= (tline >>  4) & 8;
		cramval |= whichpal;
  		if(flipX != 0) if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 7, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 7] = cramval & 0x1F;
  		else           if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x] = cramval & 0x1F;
  		// Render pixel 2 of 8
  		cramval  = (tline >> 30) & 1;
  		cramval |= (tline >> 21) & 2;
  		cramval |= (tline >> 12) & 4;
  		cramval |= (tline >> 3) & 8;
  		cramval |= whichpal;
  		if(flipX != 0) if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 6, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 6] = cramval & 0x1F;
  		else           if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 1, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 1] = cramval & 0x1F;
  		// Render pixel 3 of 8
  		cramval  = (tline >> 29) & 1;
  		cramval |= (tline >> 20) & 2;
  		cramval |= (tline >> 11) & 4;
  		cramval |= (tline >> 2) & 8;
  		cramval |= whichpal;
  		if(flipX != 0) if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 5, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 5] = cramval & 0x1F;
  		else           if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 2, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 2] = cramval & 0x1F;
  		// Render pixel 4 of 8
  		cramval  = (tline >> 28) & 1;
  		cramval |= (tline >> 19) & 2;
  		cramval |= (tline >> 10) & 4;
  		cramval |= (tline >> 1) & 8;
  		cramval |= whichpal;
  		if(flipX != 0) if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 4, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 4] = cramval & 0x1F;
  		else           if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 3, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 3] = cramval & 0x1F;
  		// Render pixel 5 of 8
  		cramval  = (tline >> 27) & 1;
  		cramval |= (tline >> 18) & 2;
  		cramval |= (tline >>  9) & 4;
  		cramval |= tline & 8;
  		cramval |= whichpal;
  		if(flipX != 0) if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 3, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 3] = cramval & 0x1F;
  		else           if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 4, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 4] = cramval & 0x1F;
  		// Render pixel 6 of 8
  		cramval  = (tline >> 26) & 1;
  		cramval |= (tline >> 17) & 2;
  		cramval |= (tline >>  8) & 4;
  		cramval |= (tline <<  1) & 8;
  		cramval |= whichpal;
  		if(flipX != 0) if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 2, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 2] = cramval & 0x1F;
  		else           if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 5, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 5] = cramval & 0x1F;
  		// Render pixel 7 of 8 
  		cramval  = (tline >> 25) & 1;
  		cramval |= (tline >> 16) & 2;
  		cramval |= (tline >>  7) & 4;
  		cramval |= (tline <<  2) & 8;
  		cramval |= whichpal;
  		if(flipX != 0) if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 1, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 1] = cramval & 0x1F;
  		else           if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 6, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 6] = cramval & 0x1F;
  		// Render pixel 8 of 8
  		cramval  = (tline >> 24) & 1;
  		cramval |= (tline >> 15) & 2;
  		cramval |= (tline >>  6) & 4;
  		cramval |= (tline <<  3) & 8;
  		cramval |= whichpal;
  		if(flipX != 0) if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x] = cramval & 0x1F;
  		else           if(priority == 0 || (cramval & 0xF) == 0) screen.setPixel(x + 7, scan, palette[cram[cramval & 0x1F] & 0x3F]); else foreline[x + 7] = cramval & 0x1F;
  		// END OF TILE LINE RENDERING CODE -------------------------------------
  	}
	
	// START OF SPRITE LINE RENDERING CODE ---------------------------------
	baseAddress = (regs[5] & 0x7E) << 7;
	
	int maxIndex = 0, spr_size = 8, buf_count = 0;

	// check if sprite is 8x8 or 8x16
	if((regs[1] & 0x2) != 0) spr_size = 16;
	
	// Parse Sprite Attribute Table
	int y;
	int regs6 = regs[6];
	for(maxIndex = 0; (maxIndex < 64) && ((vram[baseAddress + maxIndex] & 0xFF) != 0xD0); maxIndex++) {
		y = vram[baseAddress + maxIndex] & 0xFF;
		y++; // Sprite y position is plus 1

		// Check if sprite is in the current scan
		if((y > scan - spr_size) && (y <= scan)) {
			if(buf_count < 8) { // check if 8 sprite buffer is full
				spr_y[buf_count] = y;
				spr_x[buf_count] = vram[baseAddress + 0x80 + (maxIndex << 1)] & 0xFF;
				// check if sprite is shifted left 8 pixels
				if((regs0 & 0x8)!= 0) spr_x[buf_count] -= 8;
				spr_index[buf_count] = vram[baseAddress + 0x81 + (maxIndex << 1)] & 0xFF;
				// ignore bit 0 from pattern index if sprite is 8x16
				if(spr_size == 16) spr_index[buf_count] &= 0xFE;
				// check "8th" bit of the pattern index from register $06
				spr_index[buf_count] |= (regs6 & 0x4) << 6;
				buf_count++;
			}
			else {
				status |= 0x40; // Set sprite overflow flag in status register
				break;          // Break the loop
			}
		}
	}
	
	// draw sprites currently stored in buffer
	for(i=buf_count-1; i >= 0; i--) {
		tileLine = scan - spr_y[i];
		tileLine = (spr_index[i] << 5) + (tileLine << 2);
		tline = ((vram[tileLine] & 0xFF) << 24) | ((vram[tileLine+1] & 0xFF) << 16) | ((vram[tileLine+2] & 0xFF) << 8) | (vram[tileLine+3] & 0xFF);
		// render pixel 1 of 8 in sprite line
		cramval  = (tline >> 31) & 1;
		cramval |= (tline >> 22) & 2;
		cramval |= (tline >> 13) & 4;
		cramval |= (tline >>  4) & 8;
		if(cramval != 0) {   // transparency test
			cramval |= 0x10; // selects sprite palette
			screen.setPixel(spr_x[i], scan, palette[cram[cramval & 0x1F] & 0x3F]);
		}
		// render pixel 2 of 8 in sprite line
  		cramval  = (tline >> 30) & 1;
  		cramval |= (tline >> 21) & 2;
  		cramval |= (tline >> 12) & 4;
  		cramval |= (tline >> 3) & 8;
		if(cramval != 0) {   // transparency test
			cramval |= 0x10; // selects sprite palette
			screen.setPixel(spr_x[i]+1, scan, palette[cram[cramval & 0x1F] & 0x3F]);
		}
		// render pixel 3 of 8 in sprite line
  		cramval  = (tline >> 29) & 1;
  		cramval |= (tline >> 20) & 2;
  		cramval |= (tline >> 11) & 4;
  		cramval |= (tline >> 2) & 8;
		if(cramval != 0) {   // transparency test
			cramval |= 0x10; // selects sprite palette
			screen.setPixel(spr_x[i]+2, scan, palette[cram[cramval & 0x1F] & 0x3F]);
		}
		// render pixel 4 of 8 in sprite line
  		cramval  = (tline >> 28) & 1;
  		cramval |= (tline >> 19) & 2;
  		cramval |= (tline >> 10) & 4;
  		cramval |= (tline >> 1) & 8;
		if(cramval != 0) {   // transparency test
			cramval |= 0x10; // selects sprite palette
			screen.setPixel(spr_x[i]+3, scan, palette[cram[cramval & 0x1F] & 0x3F]);
		}
		// render pixel 5 of 8 in sprite line
  		cramval  = (tline >> 27) & 1;
  		cramval |= (tline >> 18) & 2;
  		cramval |= (tline >>  9) & 4;
  		cramval |= tline & 8;
		if(cramval != 0) {   // transparency test
			cramval |= 0x10; // selects sprite palette
			screen.setPixel(spr_x[i]+4, scan, palette[cram[cramval & 0x1F] & 0x3F]);
		}
		// render pixel 6 of 8 in sprite line
  		cramval  = (tline >> 26) & 1;
  		cramval |= (tline >> 17) & 2;
  		cramval |= (tline >>  8) & 4;
  		cramval |= (tline <<  1) & 8;
		if(cramval != 0) {   // transparency test
			cramval |= 0x10; // selects sprite palette
			screen.setPixel(spr_x[i]+5, scan, palette[cram[cramval & 0x1F] & 0x3F]);
		}
		// render pixel 7 of 8 in sprite line
  		cramval  = (tline >> 25) & 1;
  		cramval |= (tline >> 16) & 2;
  		cramval |= (tline >>  7) & 4;
  		cramval |= (tline <<  2) & 8;
		if(cramval != 0) {   // transparency test
			cramval |= 0x10; // selects sprite palette
			screen.setPixel(spr_x[i]+6, scan, palette[cram[cramval & 0x1F] & 0x3F]);
		}
		// render pixel 8 of 8 in sprite line
  		cramval  = (tline >> 24) & 1;
  		cramval |= (tline >> 15) & 2;
  		cramval |= (tline >>  6) & 4;
  		cramval |= (tline <<  3) & 8;
		if(cramval != 0) {   // transparency test
			cramval |= 0x10; // selects sprite palette
			screen.setPixel(spr_x[i]+7, scan, palette[cram[cramval & 0x1F] & 0x3F]);
		}
	}
	// END OF SPRITE LINE RENDERING CODE ------------------------------
	
	// DRAW BACKGROUND WITH HIGH PRIORITY OVER SPRITES
	for(i=0; i < 256; i++) 
		if((foreline[i] & 0xF) != 0) screen.setPixel(i, scan, palette[cram[foreline[i]] & 0x3F]);
		
    	// blank first column
	if(((regs[0] & 0x20) != 0)) {
		int color = palette[cram[regs[7] & 0xF] & 0x3F]; // backdrop color
		screen.setPixel(0, scan, color);
		screen.setPixel(1, scan, color);
		screen.setPixel(2, scan, color);
		screen.setPixel(3, scan, color);
		screen.setPixel(4, scan, color);
		screen.setPixel(5, scan, color);
		screen.setPixel(6, scan, color);
		screen.setPixel(7, scan, color);
	}
  }
}