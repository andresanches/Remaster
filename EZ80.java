/*
 * @author André Luiz Veltroni Sanches - alvs
 *
 * Notes: undocumented opcodes are marked with a * character.
 *        DAA table borrowed from Chris White's JavaGear
 *        which is available at http://www.javagear.co.uk
 */

public final class EZ80 {
  public int a, b, c, d, e, h, l;                   // General Purpose Registers.
  public int a_, f_, b_, c_, d_, e_, h_, l_;        // Shadow Registers.
  public int ix, iy;                                // Index Registers.
  public int flagreg, r, sp, pc;                    // Flags & Refresh register, Stack Pointer and Program Counter.
  public boolean iff1, iff2;                	    // Interrupt flip-flops and interrupt line register.
  public boolean irq;                               // IRQ line
  public int im;                                    // Interrupt mode.
  public int[] daa;									// precalculated Decimal Adjust Accumulator table
  public boolean[] parity;                          // precalculated parity table

  public static final int FLAG_SIGN   = 0x80;
  public static final int FLAG_ZERO   = 0x40;
  public static final int FLAG_BIT5   = 0x20;
  public static final int FLAG_HCARRY = 0x10;
  public static final int FLAG_BIT3   = 0x08;
  public static final int FLAG_PV     = 0x04;
  public static final int FLAG_NEG    = 0x02;
  public static final int FLAG_CARRY  = 0x01;

  public boolean EIDI_Last;                         // "True" if the last executed instruction was DI or EI.
  public boolean halt;                              // for HALT instruction
  public int counter;                               // Cycle Counter

  private MemoryManager memory;
  private VDP vdp;
  private Ports ports;
  private Debugger debugger;
  private int opcode = 0;
  
  private Mnemonic opcodes;
  public Messages msg;

  public EZ80(MemoryManager memory, Ports ports, VDP vdp, Debugger debugger) {
    this.memory = memory;
    this.debugger = debugger;
    this.vdp = vdp;
    this.ports = ports;
    
    generateParityTable();
    generateDAATable();
    reset();
    
    opcodes = new Mnemonic();
    msg = new Messages();
  }

  public void reset() {
    a = 0xFF; b = 0xFF; c = 0xFF; d = 0xFF; e = 0xFF; h = 0xFF; l = 0xFF;
    a_ = 0xFF; f_ = 0xFF; b_ = 0xFF; c_ = 0xFF; d_ = 0xFF; e_ = 0xFF; h_ = 0xFF; l_ = 0xFF;
    ix = 0xFFFF;
    iy = 0xFFFF;
    flagreg = 0xFF;
    r = 0;
    sp = 0xFF;
    pc = 0;

    counter = 0;
    EIDI_Last = false;
    halt = false;
    iff1 = false;
    iff2 = false;
  }

  public final void push(int value) {
  	sp--;
    memory.writebyte(sp, value >> 8);
  	sp--;
    memory.writebyte(sp, value);
  }

  public final int pop() {
    int val;
    val = memory.readbyte(sp);
    sp++;
    val |= (memory.readbyte(sp) << 8);
    sp++;
    return val;
  }
  
  public final void setIRQ() {
  	irq = true;
  }

  public final void interrupt() {
    if((EIDI_Last == true) || (iff1 == false)) return;

  	if(halt) {
    	halt = false;
    	pc++;
    }
    
    int temp = r;
    temp++;
    r = (r & 0x80) | (temp & 0x7F);     // increment refresh register preserving bit 7.
    iff1 = false;
    //iff2 = false;
    irq  = false;

    switch(im) {
      case 0:
      case 1:
      	//System.out.println("INTERRUPT EXECUTED");
      	push(pc);
        pc = 0x38;
        counter -= 13;
        break;
      default:	System.out.println("Interrupt mode 2 not yet implemented.");
    }
  }

  public final void execute(int iperiod) {
    counter += iperiod;

    while(counter > 0) {
      if(irq) interrupt(); // Execute interrupt
      
/*      // ----------- THIS SECTION IS ONLY FOR DEBUGGING ----------
      String op = null; // USED IN THE DEBUGGING SECTION BELOW

      opcode = memory.readbyte(pc);
      switch(opcode) {
      	case 0xCB:
      	    op = opcodes.getCB(memory.readbyte(pc+1));
      		break;
      	case 0xED:
      		op = opcodes.getED(memory.readbyte(pc+1));
      		break;
      	case 0xDD:
            if(memory.readbyte(pc+1) == 0xCB) {
                op = Integer.toHexString(opcode).toUpperCase();
                op += "CB";
                op += " " + Integer.toHexString(memory.readbyte(pc+2)).toUpperCase();
          		op += " " + Integer.toHexString(memory.readbyte(pc+3)).toUpperCase();
            }
            else {
          		op = opcodes.getIndex(memory.readbyte(pc+1));
            }
      		break;
      	case 0xFD:
            if(memory.readbyte(pc+1) == 0xCB) {
                op = Integer.toHexString(opcode).toUpperCase();
                op += "CB";
                op += " " + Integer.toHexString(memory.readbyte(pc+2)).toUpperCase();
          		op += " " + Integer.toHexString(memory.readbyte(pc+3)).toUpperCase();
            }
            else {
          		op = opcodes.getIndex(memory.readbyte(pc+1));
            }
     		break;
     		
     		default: op = opcodes.getOP(opcode);
      }

      //System.out.println("PC=$" + Integer.toHexString(pc) + " " + op);
      System.out.println(op);
      // --------------- END OF DEBUGGING PART --------------
*/
      exec_opcode(memory.readbyte(pc));
    }
    //updateDebugger();
  }

  public final void exec_opcode(int opcode) {
    int aux, addr, f;
    f = flagreg;
    int temp = r;
    temp++;
    r = (r & 0x80) | (temp & 0x7F);     // increment refresh register preserving bit 7.
    EIDI_Last = false;

    switch(opcode) {
      case 0x00:           // NOP - 4 cycles.
        counter -= 4;
        pc++;
        break;

      case 0x01:           // LD BC, nn - 10 cycles
        b = memory.readbyte(pc+2);
        c = memory.readbyte(pc+1);
        counter -= 10;
        pc += 3;
        break;

      case 0x02:           // LD (BC), A - 7 cycles
        memory.writebyte((b << 8) | c, a);
        counter -= 7;
        pc++;
        break;

      case 0x03:           // INC BC - 6 cycles
      	c++;
      	if(c > 0xFF) {
      		c ^= c;
      		b = (b + 1) & 0xFF;
      	}
      	
      	counter -= 6;
        pc++;
        break;

      case 0x04:           // INC B - 4 cycles
        if((b & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        b = ((b + 1) & 0xFF);

        if(b == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(b == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((b & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }

        }

        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x05:           // DEC B - 4 cycles
      	if(b == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
        b = ((b - 1) & 0xFF);
        
        if((b & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        if(b==0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((b & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        f |= FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x06:           // LD B, n - 7 cycles
        b = memory.readbyte(pc+1);
        counter -= 7;
        pc += 2;
        break;

      case 0x07:           // RLCA - 4 cycles
        a = a << 1;
        
        if((a & 0x100) != 0) {
          a |= 0x01;
          f |= FLAG_CARRY;
        }
        else {
          a &= ~0x01;
          f &= ~FLAG_CARRY;
        }
        
        a &= 0xFF;

        f &= ~(FLAG_HCARRY | FLAG_NEG);
        counter -= 4;
        pc++;
        break;

      case 0x08:           // EX AF, AF' - 4 cycles
        aux = a;
        a = a_;
        a_ = aux;
        aux = f;
        f = f_;
        f_ = aux;
        pc++;
        counter -= 4;
        break;

      case 0x09:           // ADD HL, BC - 11 cycles
      	aux = ((h << 8) | l) & 0xFFF;
      	aux += ((b << 8) | c) & 0xFFF;
      	if(aux > 0xFFF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	aux = (h << 8) | l;
      	aux += ((b << 8) | c);
      	
        if(aux > 0xFFFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((aux & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        aux &= 0xFFFF;
        
        if(aux == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        h = (aux >> 8) & 0xFF;
        l = aux & 0xFF;

        f &= ~FLAG_NEG;
        counter -= 11;
        pc++;
        break;

      case 0x0A:           // LD A, (BC) - 7 cycles
        a = memory.readbyte((b << 8) | c);
        counter -= 7;
        pc++;
        break;

      case 0x0B:           // DEC BC - 6 cycles
        c--;
        if(c < 0) {
        	c = 0xFF;
        	b--;
        	if(b < 0) b = 0xFF;
        }
        counter -= 6;
        pc++;
        break;

      case 0x0C:           // INC C - 4 cycles
        if((c & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        c = ((c + 1) & 0xFF);

        if(c == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(c == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((c & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }

        }

        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x0D:           // DEC C - 4 cycles
      	if(c == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
        c = ((c - 1) & 0xFF);

        if((c & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        if(c==0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((c & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        f |= FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x0E:           // LD C, n - 7 cycles
        c = memory.readbyte(pc+1);
        counter -= 7;
        pc += 2;
        break;

      case 0x0F:           // RRCA - 4 cycles
      	if((a & 0x1) != 0) {
      		a |= 0x100;
      		f |= FLAG_CARRY;
      	}
      	else {
      		a &= 0xFF;
      		f &= ~FLAG_CARRY;
      	}
      	
      	a = (a >> 1) & 0xFF;

        f &= ~(FLAG_HCARRY | FLAG_NEG);
        counter -= 4;
        pc++;
        break;

      case 0x10:           // DJNZ PC+d - 13/8 cycles
        b = ((b - 1) & 0xFF);

        if(b != 0) {
          pc += memory.readsigned(pc+1);
          pc &= 0xFFFF;
          counter -= 13;
        }
        else counter -= 8;
        pc += 2;
        break;

      case 0x11:            // LD DE, nn - 10 cycles
        d = memory.readbyte(pc+2);
        e = memory.readbyte(pc+1);
        counter -= 10;
        pc += 3;
        break;

      case 0x12:            // LD (DE), A - 7 cycles
        memory.writebyte(((d << 8) | e), (a & 0xFF));
        counter -= 7;
        pc++;
        break;

      case 0x13:            // INC DE - 6 cycles
      	e++;
      	if(e > 0xFF) {
      		e ^= e;
      		d = (d + 1) & 0xFF;
      	}
      	
        counter -= 6;
        pc++;
        break;

      case 0x14:            // INC D - 4 cycles
        if((d & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        d = ((d + 1) & 0xFF);

        if(d == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(d == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((d & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }

        }

        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x15:           // DEC D - 4 cycles
      	if(d == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
        d = ((d - 1) & 0xFF);

        if((d & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        if(d==0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((d & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        f |= FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x16:            // LD D, n - 7 cycles
        d = memory.readbyte(pc+1);
        counter -= 7;
        pc += 2;
        break;
      
      case 0x17:            // RLA - 4 cycles
        a = a << 1;

        if((f & FLAG_CARRY) != 0) a |= 0x1;
        else a &= ~0x1;

        if((a & 0x100) != 0) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        a &= 0xFF;

        f &= ~(FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
      	break;

      case 0x18:            // JR PC+d - 12 cycles
        pc += memory.readsigned(pc+1);
        counter -= 12;
        pc += 2;
        pc &= 0xFFFF;
        break;

      case 0x19:           // ADD HL, DE - 11 cycles
      	aux = ((h << 8) | l) & 0xFFF;
      	aux += ((d << 8) | e) & 0xFFF;
      	if(aux > 0xFFF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	aux = (h << 8) | l;
      	aux += ((d << 8) | e);
      	
        if(aux > 0xFFFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((aux & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        aux &= 0xFFFF;
        
        if(aux == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        h = (aux >> 8) & 0xFF;
        l = aux & 0xFF;

        f &= ~FLAG_NEG;
        counter -= 11;
        pc++;
        break;

      case 0x1A:           // LD A, (DE) - 7 cycles
        a = memory.readbyte((d << 8) | e);
        counter -= 7;
        pc++;
        break;

      case 0x1B:           // DEC DE - 6 cycles
        e--;
        if(e < 0) {
        	e = 0xFF;
        	d--;
        	if(d < 0) d = 0xFF;
        }

        counter -= 6;
        pc++;
        break;

      case 0x1C:           // INC E - 4 cycles
        if((e & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

      	e = (e + 1) & 0xFF;

        if(e == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(e == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((e & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }

        }

        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x1D:           // DEC E - 4 cycles
      	if(e == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
        e = ((e - 1) & 0xFF);

        if((e & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        if(e==0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((e & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        f |= FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x1E:           // LD E, n - 7 cycles
        e = memory.readbyte(pc+1);
        counter -= 7;
        pc += 2;
        break;

      case 0x1F:           // RRA - 4 cycles
        if((f & FLAG_CARRY) != 0) a |= 0x100;
        else a &= ~0x100;

        if((a & 0x01) != 0) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;

        a = (a >> 1) & 0xFF;

        f &= ~(FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0x20:           // JR NZ, PC+d - 12/7 cycles
        if((f & FLAG_ZERO) == 0) {
          pc += memory.readsigned(pc+1);
          counter -= 12;
        }
        else counter -= 7;
        pc += 2;
        break;

      case 0x21:           // LD HL, nn - 10 cycles
        h = memory.readbyte(pc+2);
        l = memory.readbyte(pc+1);
        counter -= 10;
        pc += 3;
        break;
        
      case 0x22:           // LD (NN), HL - 20 cycles
      	aux = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
      	memory.writebyte(aux+1, h & 0xFF);
      	memory.writebyte(aux,   l & 0xFF);
      	counter -= 20;
      	pc += 3;
      	break;

      case 0x23:           // INC HL - 6 cycles
      	l++;
      	if(l > 0xFF) {
      		l ^= l;
      		h = (h + 1) & 0xFF;
      	}
     	
        counter -= 6;
        pc++;
        break;

      case 0x24:           // INC H - 4 cycles
        if((h & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        h = ((h + 1) & 0xFF);

        if(h == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(h == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((h & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }

        }

        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x25:           // DEC H - 4 cycles
      	if(h == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
        h = ((h - 1) & 0xFF);

        if((h & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        if(h==0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((h & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        f |= FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x26:           // LD H, n - 7 cycles
        h = memory.readbyte(pc+1);
        counter -= 7;
        pc += 2;
        break;
      
      case 0x27:           // DAA - 4 cycles
      	if((f & FLAG_NEG) != 0)		a |= 0x100;
      	if((f & FLAG_CARRY) != 0)	a |= 0x200;
      	if((f & FLAG_HCARRY) != 0)	a |= 0x400;
      	a = daa[a];
      	
      	if(a > 0xFF) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	a &= 0xFF;
      	
      	if(parity[a]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((a & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;

      	counter -= 4;
      	pc++;
      	break;
        
      case 0x28:           // JR Z, PC+d - 12/7 cycles
     	if((f & FLAG_ZERO) != 0) {
      	  pc += memory.readsigned(pc+1);
          counter -= 12;
      	}
      	else counter -= 7;
      	pc += 2;
        pc &= 0xFFFF;
      	break;
      
      case 0x29:           // ADD HL, HL - 11 cycles
      	aux = ((h << 8) | l) & 0xFFF;
      	aux <<= 1;
      	if(aux > 0xFFF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	aux = (h << 8) | l;
      	aux <<= 1;
      	
        if(aux > 0xFFFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((aux & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        aux &= 0xFFFF;
        
        if(aux == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        h = (aux >> 8) & 0xFF;
        l = aux & 0xFF;

        f &= ~FLAG_NEG;
        counter -= 11;
        pc++;
        break;

      case 0x2A:           // LD HL, (NN) - 16 cycles
      	addr = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
        h = memory.readbyte(addr + 1);
        l = memory.readbyte(addr);
      	counter -= 16;
      	pc += 3;
      	break;
      	
      case 0x2B:           // DEC HL - 6 cycles
        l--;
        if(l < 0) {
        	l = 0xFF;
        	h--;
        	if(h < 0) h = 0xFF;
        }
        
        counter -= 6;
        pc++;
        break;

      case 0x2C:           // INC L - 4 cycles
        if((l & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        l = ((l + 1) & 0xFF);

        if(l == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(l == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((l & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }
        }

        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x2D:           // DEC L - 4 cycles
      	if(l == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
        l = ((l - 1) & 0xFF);

        if((l & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        if(l==0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((l & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        f |= FLAG_NEG;
        counter -= 4;
        pc++;
        break;
        
      case 0x2E:           // LD L, n - 7 cycles
        l = memory.readbyte(pc+1);
        counter -= 7;
        pc += 2;
        break;

      case 0x2F:           // CPL - 4 cycles
      	a = (~a) & 0xFF;

      	f |= (FLAG_NEG | FLAG_HCARRY);
      	counter -= 4;
      	pc++;
      	break;

      case 0x30:           // JR NC, PC+d - 12/7 cycles
        if((f & FLAG_CARRY) == 0) {
          pc += memory.readsigned(pc+1);
          pc &= 0xFFFF;
          counter -= 12;
        }
        else counter -= 7;
        pc += 2;
        break;
      	
      case 0x31:           // LD SP, nn - 10 cycles
        sp = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
        counter -= 10;
        pc += 3;
        break;

      case 0x32:           // LD (NN), A - 13 cycles
        memory.writebyte((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1), a);
        counter -= 13;
        pc += 3;
        break;

      case 0x33:           // INC SP - 6 cycles
        sp = ((sp + 1) & 0xFFFF);

        counter -= 6;
        pc++;
        break;
        
      case 0x34:          // INC (HL) - 11 cycles
      	addr = (h << 8) | l;
        aux = memory.readbyte(addr);

        if((aux & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        aux = (aux + 1) & 0xFF;
        memory.writebyte(addr, aux);
        
        if(aux == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(aux == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((aux & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }
        }

        f &= ~FLAG_NEG;
      	counter -= 11;
      	pc++;
      	break;
        
      case 0x35:          // DEC (HL) - 11 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);

      	if(aux == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
        aux = ((aux - 1) & 0xFF);
        
        if((aux & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        if(aux == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((aux & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        memory.writebyte(addr, aux);

        f |= FLAG_NEG;
        counter -= 11;
        pc++;
      	break;

      case 0x36:           // LD (HL), n - 10 cycles
        memory.writebyte(((h << 8) | l), memory.readbyte(pc+1));
        counter -= 10;
        pc += 2;
        break;
      
      case 0x37:           // SCF - 4 cycles
      	f |= FLAG_CARRY;
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 4;
      	pc++;
      	break;

      case 0x38:           // JR C, PC+d - 12/7 cycles
        if((f & FLAG_CARRY) != 0) {
          pc += memory.readsigned(pc+1);
          counter -= 12;
        }
        else counter -= 7;
        pc += 2;
        break;

      case 0x39:           // ADD HL, SP - 11 cycles
      	aux = ((h << 8) | l) & 0xFFF;
      	aux += sp & 0xFFF;
      	if(aux > 0xFFF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	aux = (h << 8) | l;
      	aux += sp;
      	
        if(aux > 0xFFFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((aux & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        aux &= 0xFFFF;
        
        if(aux == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        h = (aux >> 8) & 0xFF;
        l = aux & 0xFF;

        f &= ~FLAG_NEG;
        counter -= 11;
        pc++;
        break;

      case 0x3A:           // LD A, (nn) - 13 cycles
        a = memory.readbyte((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
        counter -= 13;
        pc += 3;
        break;
        
      case 0x3B:           // DEC SP - 6 cycles
      	sp = (sp - 1) & 0xFFFF;
      	
        counter -= 6;
        pc++;
        break;
        
      case 0x3C:           // INC A - 4 cycles
        if((a & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        a = ((a + 1) & 0xFF);

        if(a == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(a == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((a & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }
        }

        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x3D:           // DEC A - 4 cycles
      	if(a == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
        a = ((a - 1) & 0xFF);

        if((a & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        if(a==0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        f |= FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x3E:           // LD A, n - 7 cycles
        a = memory.readbyte(pc+1);
        counter -= 7;
        pc += 2;
        break;

      case 0x3F:           // CCF - 4 cycles
        if((f & FLAG_CARRY) != 0) {
          f &= ~FLAG_CARRY;
          f |= FLAG_HCARRY;
        }
        else {
          f |= FLAG_CARRY;
          f &= ~FLAG_HCARRY;
        }
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x40:           // LD B, B - 4 cycles
        counter -= 4;
        pc++;
        break;

      case 0x41:           // LD B, C - 4 cycles
        b = c;
        counter -= 4;
        pc++;
        break;

      case 0x42:           // LD B, D - 4 cycles
        b = d;
        counter -= 4;
        pc++;
        break;

      case 0x43:           // LD B, E - 4 cycles
        b = e;
        counter -= 4;
        pc++;
        break;

      case 0x44:           // LD B, H - 4 cycles
        b = h;
        counter -= 4;
        pc++;
        break;

      case 0x45:           // LD B, L - 4 cycles
        b = l;
        counter -= 4;
        pc++;
        break;

      case 0x46:           // LD B, (HL) - 7 cycles
        b = memory.readbyte((h << 8) | l);
        counter -= 7;
        pc++;
        break;

      case 0x47:           // LD B, A - 4 cycles
        b = a;
        counter -= 4;
        pc++;
        break;

      case 0x48:           // LD C, B - 4 cycles
        c = b;
        counter -= 4;
        pc++;
        break;

      case 0x49:           // LD C, C - 4 cycles
        counter -= 4;
        pc++;
        break;

      case 0x4A:           // LD C, D - 4 cycles
        c = d;
        counter -= 4;
        pc++;
        break;

      case 0x4B:           // LD C, E - 4 cycles
        c = e;
        counter -= 4;
        pc++;
        break;

      case 0x4C:           // LD C, H - 4 cycles
        c = h;
        counter -= 4;
        pc++;
        break;

      case 0x4D:           // LD C, L - 4 cycles
        c = l;
        counter -= 4;
        pc++;
        break;

      case 0x4E:           // LD C, (HL) - 7 cycles
        c = memory.readbyte((h << 8) | l);
        counter -= 7;
        pc++;
        break;

      case 0x4F:           // LD C, A - 4 cycles
        c = a;
        counter -= 4;
        pc++;
        break;

      case 0x50:           // LD D, B - 4 cycles
        d = b;
        counter -= 4;
        pc++;
        break;

      case 0x51:           // LD D, C - 4 cycles
        d = c;
        counter -= 4;
        pc++;
        break;

      case 0x52:           // LD D, D - 4 cycles
        counter -= 4;
        pc++;
        break;

      case 0x53:           // LD D, E - 4 cycles
        d = e;
        counter -= 4;
        pc++;
        break;

      case 0x54:           // LD D, H - 4 cycles
        d = h;
        counter -= 4;
        pc++;
        break;

      case 0x55:           // LD D, L - 4 cycles
        d = l;
        counter -= 4;
        pc++;
        break;

      case 0x56:           // LD D, (HL) - 7 cycles
        d = memory.readbyte((h << 8) | l);
        counter -= 7;
        pc++;
        break;

      case 0x57:           // LD D, A - 4 cycles
        d = a;
        counter -= 4;
        pc++;
        break;

      case 0x58:           // LD E, B - 4 cycles
        e = b;
        counter -= 4;
        pc++;
        break;

      case 0x59:           // LD E, C - 4 cycles
        e = c;
        counter -= 4;
        pc++;
        break;

      case 0x5A:           // LD E, D - 4 cycles
        e = d;
        counter -= 4;
        pc++;
        break;

      case 0x5B:           // LD E, E - 4 cycles
        counter -= 4;
        pc++;
        break;

      case 0x5C:           // LD E, H - 4 cycles
        e = h;
        counter -= 4;
        pc++;
        break;

      case 0x5D:           // LD E, L - 4 cycles
        e = l;
        counter -= 4;
        pc++;
        break;

      case 0x5E:           // LD E, (HL) - 7 cycles
        e = memory.readbyte((h << 8) | l);
        counter -= 7;
        pc++;
        break;

      case 0x5F:           // LD E, A - 4 cycles
        e = a;
        counter -= 4;
        pc++;
        break;

      case 0x60:           // LD H, B - 4 cycles
        h = b;
        counter -= 4;
        pc++;
        break;

      case 0x61:           // LD H, C - 4 cycles
        h = c;
        counter -= 4;
        pc++;
        break;

      case 0x62:           // LD H, D - 4 cycles
        h = d;
        counter -= 4;
        pc++;
        break;

      case 0x63:           // LD H, E - 4 cycles
        h = e;
        counter -= 4;
        pc++;
        break;

      case 0x64:           // LD H, H - 4 cycles
        counter -= 4;
        pc++;
        break;

      case 0x65:           // LD H, L - 4 cycles
        h = l;
        counter -= 4;
        pc++;
        break;
                                 
      case 0x66:           // LD H, (HL) - 7 cycles
        h = memory.readbyte((h << 8) | l);
        counter -= 7;
        pc++;
        break;

      case 0x67:           // LD H, A - 4 cycles
        h = a;
        counter -= 4;
        pc++;
        break;

      case 0x68:           // LD L, B - 4 cycles
        l = b;
        counter -= 4;
        pc++;
        break;

      case 0x69:           // LD L, C - 4 cycles
        l = c;
        counter -= 4;
        pc++;
        break;

      case 0x6A:           // LD L, D - 4 cycles
        l = d;
        counter -= 4;
        pc++;
        break;

      case 0x6B:           // LD L, E - 4 cycles
        l = e;
        counter -= 4;
        pc++;
        break;

      case 0x6C:           // LD L, H - 4 cycles
        l = h;
        counter -= 4;
        pc++;
        break;

      case 0x6D:           // LD L, L - 4 cycles
        counter -= 4;
        pc++;
        break;

      case 0x6E:           // LD L, (HL) - 7 cycles
        l = memory.readbyte((h << 8) | l);
        counter -= 7;
        pc++;
        break;

      case 0x6F:           // LD L, A - 4 cycles
        l = a;
        counter -= 4;
        pc++;
        break;

      case 0x70:           // LD (HL), B - 7 cycles
        memory.writebyte(((h << 8) | l), b);
        counter -= 7;
        pc++;
        break;

      case 0x71:           // LD (HL), C - 7 cycles
        memory.writebyte(((h << 8) | l), c);
        counter -= 7;
        pc++;
        break;

      case 0x72:           // LD (HL), D - 7 cycles
        memory.writebyte(((h << 8) | l), d);
        counter -= 7;
        pc++;
        break;

      case 0x73:           // LD (HL), E - 7 cycles
        memory.writebyte(((h << 8) | l), e);
        counter -= 7;
        pc++;
        break;

      case 0x74:           // LD (HL), H - 7 cycles
        memory.writebyte(((h << 8) | l), h);
        counter -= 7;
        pc++;
        break;

      case 0x75:           // LD (HL), L - 7 cycles
        memory.writebyte(((h << 8) | l), l);
        counter -= 7;
        pc++;
        break;
        
      case 0x76:           // HALT - 4 cycles
      	halt = true;
      	counter -= 4;
      	break;

      case 0x77:           // LD (HL), A - 7 cycles
        memory.writebyte(((h << 8) | l), a);
        counter -= 7;
        pc++;
        break;

      case 0x78:           // LD A, B - 4 cycles
        a = b;
        counter -= 4;
        pc++;
        break;

      case 0x79:           // LD A, C - 4 cycles
        a = c;
        counter -= 4;
        pc++;
        break;

      case 0x7A:           // LD A, D - 4 cycles
        a = d;
        counter -= 4;
        pc++;
        break;

      case 0x7B:           // LD A, E - 4 cycles
        a = e;
        counter -= 4;
        pc++;
        break;

      case 0x7C:           // LD A, H - 4 cycles
        a = h;
        counter -= 4;
        pc++;
        break;

      case 0x7D:           // LD A, L - 4 cycles
        a = l;
        counter -= 4;
        pc++;
        break;

      case 0x7E:           // LD A, (HL) - 7 cycles
        a = memory.readbyte((h << 8) | l);
        counter -= 7;
        pc++;
        break;

      case 0x7F:           // LD A, A - 4 cycles
        counter -= 4;
        pc++;
        break;

      case 0x80:           // ADD A, B - 4 cycles
      	if((a & 0xF) + (b & 0xF) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
        a += b;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;

        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x81:           // ADD A, C - 4 cycles
      	if((a & 0xF) + (c & 0xF) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
        a += c;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;

        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x82:           // ADD A, D - 4 cycles
      	if((a & 0xF) + (d & 0xF) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
        a += d;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;

        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x83:           // ADD A, E - 4 cycles
      	if((a & 0xF) + (e & 0xF) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
        a += e;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;

        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x84:           // ADD A, H - 4 cycles
      	if((a & 0xF) + (h & 0xF) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
        a += h;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;

        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x85:           // ADD A, L - 4 cycles
      	if((a & 0xF) + (l & 0xF) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
        a += l;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;

        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x86:           // ADD A, (HL) - 7 cycles
        aux = memory.readbyte((h << 8) | l);

      	if((a & 0xF) + (aux & 0xF) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	a += aux;
 
        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;

        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        f &= ~FLAG_NEG;
        counter -= 7;
        pc++;
        break;

      case 0x87:           // ADD A, A - 4 cycles
      	if(((a & 0xF) << 1) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a = a << 1;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;

        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x88:           // ADC A, B - 4 cycles
      	if(((a & 0xF) + (b & 0xF) + (((f & FLAG_CARRY) != 0) ? 1 : 0)) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

        a += b;
        if((f & FLAG_CARRY) != 0) a++;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;
        
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x89:           // ADC A, C - 4 cycles
      	if(((a & 0xF) + (c & 0xF) + (((f & FLAG_CARRY) != 0) ? 1 : 0)) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

        a += c;
        if((f & FLAG_CARRY) != 0) a++;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;
        
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x8A:           // ADC A, D - 4 cycles
      	if(((a & 0xF) + (d & 0xF) + (((f & FLAG_CARRY) != 0) ? 1 : 0)) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

        a += d;
        if((f & FLAG_CARRY) != 0) a++;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;
        
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x8B:           // ADC A, E - 4 cycles
      	if(((a & 0xF) + (e & 0xF) + (((f & FLAG_CARRY) != 0) ? 1 : 0)) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

        a += e;
        if((f & FLAG_CARRY) != 0) a++;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;
        
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x8C:           // ADC A, H - 4 cycles
      	if(((a & 0xF) + (h & 0xF) + (((f & FLAG_CARRY) != 0) ? 1 : 0)) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

        a += h;
        if((f & FLAG_CARRY) != 0) a++;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;
        
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x8D:           // ADC A, L - 4 cycles
      	if(((a & 0xF) + (l & 0xF) + (((f & FLAG_CARRY) != 0) ? 1 : 0)) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

        a += l;
        if((f & FLAG_CARRY) != 0) a++;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;
        
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x8E:           // ADC A, (HL) - 7 cycles
        aux = memory.readbyte((h << 8) | l);

        if(((a & 0xF) + (aux & 0xF) + (((f & FLAG_CARRY) != 0) ? 1 : 0)) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

        a += aux;
        if((f & FLAG_CARRY) != 0) a++;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;
        
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        f &= ~FLAG_NEG;
        counter -= 7;
        pc++;
        break;

      case 0x8F:           // ADC A, A - 4 cycles
      	if(((a & 0xF) + (a & 0xF) + (((f & FLAG_CARRY) != 0) ? 1 : 0)) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

        a = a << 1;
        if((f & FLAG_CARRY) != 0) a++;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;
        
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;
        
      case 0x90:           // SUB B - 4 cycles
      	if((a & 0xF) < (b & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	a -= b;

      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;

      case 0x91:           // SUB C - 4 cycles
      	if((a & 0xF) < (c & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= c;

      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;

      case 0x92:           // SUB D - 4 cycles
      	if((a & 0xF) < (d & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= d;

      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;

      case 0x93:           // SUB E - 4 cycles
      	if((a & 0xF) < (e & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= e;

      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;

      case 0x94:           // SUB H - 4 cycles
      	if((a & 0xF) < (h & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= h;

      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;
      	
      case 0x95:           // SUB L - 4 cycles
      	if((a & 0xF) < (l & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= l;

      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;
      	
      case 0x96:           // SUB (HL) - 7 cycles
      	aux = memory.readbyte((h << 8) | l);

      	if((a & 0xF) < (aux & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= aux;
      	
      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((aux > 0x7F) || aux < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 7;
      	pc++;
      	break;

      case 0x97:           // SUB A - 4 cycles
      	a = 0;
      	f &= ~(FLAG_CARRY | FLAG_HCARRY | FLAG_PV | FLAG_SIGN);
        f |= FLAG_ZERO | FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;

      case 0x98:           // SBC A, B - 4 cycles
      	if((a & 0xF) < ((b & 0xF) - ((f & FLAG_CARRY) != 0 ? 1 : 0))) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= b;
      	if((f & FLAG_CARRY) != 0) a--;
      	
      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) != 0) f|= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	      	
      	f |= FLAG_NEG;
      	
      	counter -= 4;
      	pc++;
      	break;
      	
      case 0x99:           // SBC A, C - 4 cycles
      	if((a & 0xF) < ((c & 0xF) - ((f & FLAG_CARRY) != 0 ? 1 : 0))) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= c;
      	if((f & FLAG_CARRY) != 0) a--;
      	
      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) != 0) f|= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	      	
      	f |= FLAG_NEG;
      	
      	counter -= 4;
      	pc++;
      	break;

      case 0x9A:           // SBC A, D - 4 cycles
      	if((a & 0xF) < ((d & 0xF) - ((f & FLAG_CARRY) != 0 ? 1 : 0))) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= d;
      	if((f & FLAG_CARRY) != 0) a--;
      	
      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) != 0) f|= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	      	
      	f |= FLAG_NEG;
      	
      	counter -= 4;
      	pc++;
      	break;
      
      case 0x9B:           // SBC A, E - 4 cycles
      	if((a & 0xF) < ((e & 0xF) - ((f & FLAG_CARRY) != 0 ? 1 : 0))) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= e;
      	if((f & FLAG_CARRY) != 0) a--;
      	
      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) != 0) f|= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	      	
      	f |= FLAG_NEG;
      	
      	counter -= 4;
      	pc++;
      	break;
      
      case 0x9C:           // SBC A, H - 4 cycles
      	if((a & 0xF) < ((h & 0xF) - ((f & FLAG_CARRY) != 0 ? 1 : 0))) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= h;
      	if((f & FLAG_CARRY) != 0) a--;
      	
      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) != 0) f|= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	      	
      	f |= FLAG_NEG;
      	
      	counter -= 4;
      	pc++;
      	break;

      case 0x9D:           // SBC A, L - 4 cycles
      	if((a & 0xF) < ((l & 0xF) - ((f & FLAG_CARRY) != 0 ? 1 : 0))) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= l;
      	if((f & FLAG_CARRY) != 0) a--;
      	
      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) != 0) f|= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	      	
      	f |= FLAG_NEG;
      	
      	counter -= 4;
      	pc++;
      	break;
      	
      case 0x9E:           // SBC A, (HL) - 7 cycles
      	aux = memory.readbyte((h << 8) | l);
      	if((a & 0xF) < ((aux & 0xF) - ((f & FLAG_CARRY) != 0 ? 1 : 0))) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= aux;
      	if((f & FLAG_CARRY) != 0) a--;
      	
      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) != 0) f|= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	      	
      	f |= FLAG_NEG;
      	
      	counter -= 7;
      	pc++;
      	break;
      	
      case 0x9F:           // SBC A, A - 4 cycles
      	if((a & 0xF) < ((a & 0xF) - ((f & FLAG_CARRY) != 0 ? 1 : 0))) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a ^= a;
      	if((f & FLAG_CARRY) != 0) a--;
      	
      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	a &= 0xFF;
      	
      	if((a & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	      	
      	f |= FLAG_NEG;
      	
      	counter -= 4;
      	pc++;
      	break;
      	
      case 0xA0:           // AND B - 4 cycles
        a &= b;
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if(parity[a & 0xFF] == true) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_NEG | FLAG_CARRY);
        f |= FLAG_HCARRY;
        counter -= 4;
        pc++;
        break;

      case 0xA1:           // AND C - 4 cycles
        a &= c;
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_NEG | FLAG_CARRY);
        f |= FLAG_HCARRY;
        counter -= 4;
        pc++;
        break;

      case 0xA2:           // AND D - 4 cycles
        a &= d;
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if(parity[a & 0xFF] == true) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_NEG | FLAG_CARRY);
        f |= FLAG_HCARRY;
        counter -= 4;
        pc++;
        break;

      case 0xA3:           // AND E - 4 cycles
        a &= e;
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if(parity[a & 0xFF] == true) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_NEG | FLAG_CARRY);
        f |= FLAG_HCARRY;
        counter -= 4;
        pc++;
        break;

      case 0xA4:           // AND H - 4 cycles
        a &= h;
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if(parity[a & 0xFF] == true) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_NEG | FLAG_CARRY);
        f |= FLAG_HCARRY;
        counter -= 4;
        pc++;
        break;

      case 0xA5:           // AND L - 4 cycles
        a &= l;
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if(parity[a & 0xFF] == true) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_NEG | FLAG_CARRY);
        f |= FLAG_HCARRY;
        counter -= 4;
        pc++;
        break;

      case 0xA6:           // AND (HL) - 7 cycles
        a &= memory.readbyte((h << 8) | l);

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if(parity[a & 0xFF] == true) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_NEG | FLAG_CARRY);
        f |= FLAG_HCARRY;
        counter -= 7;
        pc++;
        break;

      case 0xA7:           // AND A - 4 cycles
        a = (a & 0xFF);

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if(parity[a & 0xFF] == true) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_NEG | FLAG_CARRY);
        f |= FLAG_HCARRY;
        counter -= 4;
        pc++;
        break;

      case 0xA8:           // XOR B - 4 cycles
        a ^= b;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0xA9:           // XOR C - 4 cycles
        a ^= c;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0xAA:           // XOR D - 4 cycles
        a ^= d;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0xAB:           // XOR E - 4 cycles
        a ^= e;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0xAC:           // XOR H - 4 cycles
        a ^= h;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0xAD:           // XOR L - 4 cycles
        a ^= l;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0xAE:           // XOR (HL) - 7 cycles
        a ^= memory.readbyte((h << 8) | l);
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 7;
        pc++;
        break;

      case 0xAF:           // XOR A - 4 cycles
        a ^= a;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0xB0:           // OR B - 4 cycles
        a |= b;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0xB1:           // OR C - 4 cycles
        a |= c;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0xB2:           // OR D - 4 cycles
        a |= d;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0xB3:           // OR E - 4 cycles
        a |= e;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0xB4:           // OR H - 4 cycles
        a |= h;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0xB5:           // OR L - 4 cycles
        a |= l;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;

      case 0xB6:           // OR (HL) - 7 cycles
        a |= memory.readbyte((h << 8) | l);
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 7;
        pc++;
        break;

      case 0xB7:           // OR A - 4 cycles
      	a |= a & 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 4;
        pc++;
        break;
      
      case 0xB8:           // CP B - 4 cycles
      	aux = (a & 0xFF) - (b & 0xFF);

      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((aux > 0x7F) || aux < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	aux = aux & 0xFF;
      	
      	if((aux & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;
      
      case 0xB9:           // CP C - 4 cycles
      	aux = (a & 0xFF) - (c & 0xFF);

      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((aux > 0x7F) || aux < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	aux = aux & 0xFF;
      	
      	if((aux & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;

      case 0xBA:           // CP D - 4 cycles
      	aux = (a & 0xFF) - (d & 0xFF);

      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((aux > 0x7F) || aux < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	aux = aux & 0xFF;
      	
      	if((aux & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;
        
      case 0xBB:           // CP E - 4 cycles
      	aux = (a & 0xFF) - (e & 0xFF);

      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((aux > 0x7F) || aux < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	aux = aux & 0xFF;
      	
      	if((aux & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;
        
      case 0xBC:           // CP H - 4 cycles
      	aux = (a & 0xFF) - (h & 0xFF);

      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((aux > 0x7F) || aux < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	aux = aux & 0xFF;
      	
      	if((aux & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;

      case 0xBD:           // CP L - 4 cycles
      	aux = (a & 0xFF) - (l & 0xFF);

      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((aux > 0x7F) || aux < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	aux = aux & 0xFF;
      	
      	if((aux & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;

      case 0xBE:           // CP (HL) - 7 cycles
      	aux = memory.readbyte((h << 8) | l);
      	aux = (a & 0xFF) - (aux & 0xFF);

      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((aux > 0x7F) || aux < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	aux = aux & 0xFF;
      	
      	if((aux & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 7;
      	pc++;
      	break;

      case 0xBF:           // CP A - 4 cycles
      	aux = (a & 0xFF) - (a & 0xFF);

      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((aux > 0x7F) || aux < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	aux = aux & 0xFF;
      	
      	if((aux & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 4;
      	pc++;
      	break;

      case 0xC0:           // RET NZ - 11/5 cycles
        if((f & FLAG_ZERO) == 0) {
            pc = pop();
            counter -= 11;
        }
        else {
            pc++;
            counter -= 5;
        }
        break;

      case 0xC1:           // POP BC - 10 cycles
        aux = pop();
        b = (aux >> 8) & 0xFF;
        c = aux & 0xFF;
        counter -= 10;
        pc++;
        break;

      case 0xC2:           // JP NZ, nn - 10 cycles
        if((f & FLAG_ZERO) == 0)
            pc = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
        else
            pc += 3;

        counter -= 10;
        break;

      case 0xC3:           // JP nn - 10 cycles
        pc = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
        counter -= 10;
        break;

      case 0xC4:           // CALL NZ, nn - 17/10 cycles
        if((f & FLAG_ZERO) == 0) {
            push(pc+3);
        	pc = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
        	counter -= 7;
        }
        else
        	pc += 3;
        counter -= 10;
        break;

      case 0xC5:           // PUSH BC - 11 cycles
        push((b << 8) | c);
        counter -= 11;
        pc++;
        break;
      
      case 0xC6:           // ADD A, n - 7 cycles
        aux = memory.readbyte(pc+1);

        if((a & 0xF) + (aux & 0xF) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
        a += aux;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;

        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        f &= ~FLAG_NEG;
        counter -= 7;
        pc += 2;
      	break;

      case 0xC7:           // RST 0h - 11 cycles
        push(pc+1);
        pc ^= pc;
        counter -= 11;
        break;

      case 0xC8:           // RET Z - 11/5 cycles
        if((f & FLAG_ZERO) != 0) {
          pc = pop();
          counter -= 11;
        }
        else {
          pc++;
          counter -= 5;
        }
        break;

      case 0xC9:           // RET - 10 cycles
        pc = pop();
        counter -= 10;
        break;

      case 0xCA:           // JP Z, nn - 10 cycles
        if((f & FLAG_ZERO) != 0)
          pc = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
        else
          pc += 3;
        
        counter -= 10;
        break;

      case 0xCB:           // CB PREFIXED OPCODES
        pc++;
        flagreg = f;
        exec_CB_opcode(memory.readbyte(pc));
        f = flagreg;
        break;

      case 0xCC:           // CALL Z, nn - 17/10 cycles
        if((f & FLAG_ZERO) != 0) {
            push(pc+3);
        	pc = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
        	counter -= 7;
        }
        else  pc += 3;
        counter -= 10;
        break;

      case 0xCD:           // CALL nn - 17 cycles
        push(pc+3);
        pc = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
        counter -= 17;
        break;
      
      case 0xCE:           // ADC A, n - 7 cycles
        aux = memory.readbyte(pc+1);
      	if(((a & 0xF) + (aux & 0xF) + (((f & FLAG_CARRY) != 0) ? 1 : 0)) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

        a += aux;
        if((f & FLAG_CARRY) != 0) a++;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        a &= 0xFF;
        
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        f &= ~FLAG_NEG;
        counter -= 7;
        pc += 2;
      	break;

      case 0xCF:           // RST 8h - 11 cycles
        push(pc+1);
        pc = 0x08;
        counter -= 11;
        break;

      case 0xD0:           // RET NC - 11/5 cycles
        if((f & FLAG_CARRY) == 0) {
            pc = pop();
            counter -= 11;
        }
        else {
            pc++;
            counter -= 5;
        }
        break;

      case 0xD1:           // POP DE - 10 cycles
        aux = pop();
        d = (aux >> 8) & 0xFF;
        e = (aux & 0xFF);
        counter -= 10;
        pc++;
        break;

      case 0xD2:           // JP NC, nn - 10 cycles
        if((f & FLAG_CARRY) == 0)
          pc = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
        else
          pc += 3;

        counter -= 10;
        break;

      case 0xD3:           // OUT (n), A - 11 cycles
      	ports.write(memory.readbyte(pc+1), a);

        counter -= 11;
        pc += 2;
        break;
        
      case 0xD4:           // CALL NC, nn - 17/10 cycles
        if((f & FLAG_CARRY) == 0) {
          push(pc+3);
          pc = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
          counter -= 7;
        }
        else pc += 3;
        counter -= 10;
        break;

      case 0xD5:           // PUSH DE - 11 cycles
        push((d << 8) | e);
        counter -= 11;
        pc++;
        break;

      case 0xD6:           // SUB n - 7 cycles
      	aux = memory.readbyte(pc+1);

      	if((a & 0xF) < (aux & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a = a - aux;

      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	a = a & 0xFF;
      	
      	if((a & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 7;
      	pc += 2;
      	break;
        
      case 0xD7:           // RST 10h - 11 cycles
        push(pc+1);
        pc = 0x10;
        counter -= 11;
        break;

      case 0xD8:           // RET C - 11/5 cycles
        if((f & FLAG_CARRY) != 0) {
            pc = pop();
            counter -= 11;
        }
        else {
            pc++;
            counter -= 5;
        }
        break;

      case 0xD9:           // EXX - 4 cycles
        aux = b; b = b_; b_ = aux;
        aux = c; c = c_; c_ = aux;
        
        aux = d; d = d_; d_ = aux;
        aux = e; e = e_; e_ = aux;
        
        aux = h; h = h_; h_ = aux;
        aux = l; l = l_; l_ = aux;
        
        counter -= 4;
        pc++;
        break;
      
      case 0xDA:           // JP C, nn - 10 cycles
        if((f & FLAG_CARRY) != 0)
          pc = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
        else
          pc += 3;

        counter -= 10;
        break;

      case 0xDB:           // IN A, (n) - 11 cycles
      	a = ports.read(memory.readbyte(pc+1));
      	counter -= 11;
        pc += 2;
        break;

      case 0xDC:           // CALL C, nn
        if((f & FLAG_CARRY) != 0) {
            push(pc+3);
        	pc = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
        	counter -= 7;
        }
        else pc += 3;
        counter -= 10;
        break;
        
      case 0xDD:           // DD PREFIXED OPCODES
        pc++;
        flagreg = f;
        exec_DD_opcode(memory.readbyte(pc));
        f = flagreg;
        break;
        
      case 0xDE:           // SBC A, n - 4 cycles
      	aux = memory.readbyte(pc+1);
      	
      	if((a & 0xF) < ((aux & 0xF) - ((f & FLAG_CARRY) != 0 ? 1 : 0))) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a -= aux;
      	if((f & FLAG_CARRY) != 0) a--;
      	
      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if(a < -128) f |= FLAG_PV;
        else f &= ~FLAG_PV;
		
      	a &= 0xFF;
      	
      	if((a & 0x80) != 0) f|= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	      	
      	f |= FLAG_NEG;
      	
      	counter -= 4;
      	pc += 2;
      	break;
      	
      case 0xDF:           // RST 18h - 11 cycles
        push(pc+1);
        pc = 0x18;
        counter -= 11;
        break;

      case 0xE0:           // RET PO - 11/5 cycles
        if((f & FLAG_PV) == 0) {
          pc = pop();
          counter -= 11;
        }
        else {
          pc++;
          counter -= 5;
        }
        break;

      case 0xE1:           // POP HL - 10 cycles
        aux = pop();
        h = (aux >> 8) & 0xFF;
        l = (aux & 0xFF);
        counter -= 10;
        pc++;
        break;

      case 0xE2:           // JP PO, nn - 10 cycles
        if((f & FLAG_PV) == 0)
          pc = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
        else
          pc += 3;

        counter -= 10;
        break;
        
      case 0xE3:           // EX (SP), HL - 19 cycles
        aux = l;
        l = memory.readbyte(sp);
        memory.writebyte(sp, aux);
        aux = h;
        h = memory.readbyte(sp + 1);
        memory.writebyte(sp + 1, aux);
        counter -= 19;
        pc++;
        break;

      case 0xE4:           // CALL PO, nn - 10/1 cycles
        if((f & FLAG_PV) == 0) {
          push(pc + 3);
          pc = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
          counter -= 7;
        }
        else pc += 3;
        counter -= 10;
        break;
        
      case 0xE5:           // PUSH HL - 11 cycles
        push((h << 8) | l);
        counter -= 11;
        pc++;
        break;

      case 0xE6:           // AND n - 7 cycles
        a &= memory.readbyte(pc+1);

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_NEG | FLAG_CARRY);
        f |= FLAG_HCARRY;
        counter -= 7;
        pc += 2;
        break;

      case 0xE7:           // RST 20h - 11 cycles
        push(pc+1);
        pc = 0x20;
        counter -= 11;
        break;

      case 0xE8:           // RET PE - 11/5 cycles
        if((f & FLAG_PV) != 0) {
          pc = pop();
          counter -= 11;
        }
        else {
          pc++;
          counter -= 5;
        }
        break;

      case 0xE9:           // JP HL - 4 cycles
        pc = (h << 8) | l;
        counter -= 4;
        break;
      
      case 0xEA:           // JP PE, nn - 10 cycles
        if((f & FLAG_PV) != 0)
          pc = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
        else
          pc += 3;

        counter -= 10;
        break;

      case 0xEB:           // EX DE, HL - 4 cycles
        aux = e;
        e = l;
        l = aux;
        aux = d;
        d = h;
        h = aux;
        counter -= 4;
        pc++;
        break;

      case 0xEC:           // CALL PE, nn - 10/1 cycles
        if((f & FLAG_PV) != 0) {
          push(pc+3);
          pc = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
          counter -= 7;
        }
        else pc += 3;
        counter -= 10;
        break;
        
      case 0xED:           // ED PREFIXED OPCODES
        pc++;
        flagreg = f;
        exec_ED_opcode(memory.readbyte(pc));
        f = flagreg;
        break;
      
      case 0xEE:           // XOR n - 7 cycles
      	a ^= memory.readbyte(pc+1);
      	a &= 0xFF;

      	if((a & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[a & 0xFF]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_CARRY | FLAG_NEG);
      	
      	counter -= 7;
      	pc += 2;
      	break;

      case 0xEF:           // RST 28h - 11 cycles
        push(pc+1);
        pc = 0x28;
        counter -= 11;
        break;

      case 0xF0:           // RET P - 11/5 cycles
        if((f & FLAG_SIGN) == 0) {
            pc = pop();
            counter -= 11;
        }
        else {
            pc++;
            counter -= 5;
        }
        break;

      case 0xF1:           // POP AF - 10 cycles
        aux = pop();
        a = (aux >> 8) & 0xFF;
        f = (aux & 0xFF);
        counter -= 10;
        pc++;
        break;

      case 0xF2:           // JP P, nn - 10 cycles
        if((f & FLAG_SIGN) == 0)
          pc = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
        else
          pc += 3;

        counter -= 10;
        break;

      case 0xF3:           // DI - 4 cycles
        iff1 = false;
        //iff2 = false;
        EIDI_Last = true;
        counter -= 4;
        pc++;
        break;

      case 0xF4:           // CALL P, nn - 17/10 cycles
        if((f & FLAG_SIGN) == 0) {
            push(pc+3);
        	pc = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
        	counter -= 7;
        }
        else
        	pc += 3;
        counter -= 10;
        break;

      case 0xF5:           // PUSH AF - 11 cycles
        push(((a & 0xFF) << 8) | (f & 0xFF));
        counter -= 11;
        pc++;
        break;
        
      case 0xF6:           // OR n - 7 cycles
      	a |= memory.readbyte(pc+1);
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
      	counter -= 7;
      	pc += 2;
      	break;
      
      case 0xF7:           // RST 30h - 11 cycles
        push(pc+1);
        pc = 0x30;
        counter -= 11;
        break;

      case 0xF8:           // RET M - 11/5 cycles
        if((f & FLAG_SIGN) != 0) {
            pc = pop();
            counter -= 11;
        }
        else {
            pc++;
            counter -= 5;
        }
        break;
        
      case 0xF9:           // LD SP, HL - 20 cycles
      	sp = (h << 8) | l;
      	counter -= 20;
      	pc++;
      	break;
        
      case 0xFA:           // JP M, nn - 10 cycles
        if((f & FLAG_SIGN) != 0)
          pc = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
        else
          pc += 3;

        counter -= 10;
        break;

      case 0xFB:           // EI - 4 cycles
        iff1 = true;
        //iff2 = true;
        EIDI_Last = true;
        counter -= 4;
        pc++;
        break;
        
      case 0xFC:           // CALL M, nn - 17/10 cycles
        if((f & FLAG_SIGN) != 0) {
            push(pc+3);
        	pc = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
        	counter -= 7;
        }
        else pc += 3;
        counter -= 10;
        break;

      case 0xFD:           // FD PREFIXED OPCODES
        pc++;
        flagreg = f;
        exec_FD_opcode(memory.readbyte(pc));
        f = flagreg;
        break;
      
      case 0xFE:           // CP n - 7 cycles
      	aux = memory.readbyte(pc+1);
      	aux = (a & 0xFF) - (aux & 0xFF);

      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((aux > 0x7F) || aux < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	aux = aux & 0xFF;
      	
      	if((aux & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 7;
      	pc += 2;
      	break;

      case 0xFF:           // RST 38h - 11 cycles
        push(pc+1);
        pc = 0x38;
        counter -= 11;
        break;

      default:             // Uninmplemented opcode
      	if(!msg.isVisible()) msg.setVisible(true);
        msg.println("PC = $" + Integer.toHexString(pc) + "  0x" + Integer.toHexString(opcode).toUpperCase() + ": Unknown opcode");
        //memory.dumpMemory();
        //vdp.dumpMemory();
        pc++;
    }
    
    flagreg = f;
  }

  public final void exec_CB_opcode(int opcode)
  {
    int aux, addr, f;
    f = flagreg;

    switch(opcode)
    {
      case 0x00:           // RLC B - 8 cycles
      	b = b << 1;
      	if((b & 0x100) != 0) {
      		b |= 0x1;
      		f |= FLAG_CARRY;
      	}
      	else {
      		b &= 0xFE;
      		f &= ~FLAG_CARRY;
      	}
      	b &= 0xFF;
      	
      	if(parity[b]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	if((b & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(b == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      	
      case 0x01:           // RLC C - 8 cycles
      	c = c << 1;
      	if((c & 0x100) != 0) {
      		c |= 1;
      		f |= FLAG_CARRY;
      	}
      	else {
      		c &= ~0x1;
      		f &= ~FLAG_CARRY;
      	}
      	c &= 0xFF;
      	
      	if(parity[c]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	if((c & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(c == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      	
      case 0x02:           // RLC D - 8 cycles
      	d = d << 1;
      	if((d & 0x100) != 0) {
      		d |= 1;
      		f |= FLAG_CARRY;
      	}
      	else {
      		d &= ~0x1;
      		f &= ~FLAG_CARRY;
      	}
      	d &= 0xFF;
      	
      	if(parity[d]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	if((d & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(d == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      	
      case 0x03:           // RLC E - 8 cycles
      	e = e << 1;
      	if((e & 0x100) != 0) {
      		e |= 1;
      		f |= FLAG_CARRY;
      	}
      	else {
      		e &= ~0x1;
      		f &= ~FLAG_CARRY;
      	}
      	e &= 0xFF;
      	
      	if(parity[e]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	if((e & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(e == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      	
      case 0x04:           // RLC H - 8 cycles
      	h = h << 1;
      	if((h & 0x100) != 0) {
      		h |= 1;
      		f |= FLAG_CARRY;
      	}
      	else {
      		h &= ~0x1;
      		f &= ~FLAG_CARRY;
      	}
      	h &= 0xFF;
      	
      	if(parity[h]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	if((h & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(h == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      	
      case 0x05:           // RLC L - 8 cycles
      	l = l << 1;
      	if((l & 0x100) != 0) {
      		l |= 1;
      		f |= FLAG_CARRY;
      	}
      	else {
      		l &= ~0x1;
      		f &= ~FLAG_CARRY;
      	}
      	l &= 0xFF;
      	
      	if(parity[l]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	if((l & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(l == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      	
      case 0x06:           // RLC (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
      	aux = aux << 1;
      	if((aux & 0x100) != 0) {
      		aux |= 1;
      		f |= FLAG_CARRY;
      	}
      	else {
      		aux &= 0xFE;
      		f &= ~FLAG_CARRY;
      	}
      	aux &= 0xFF;
      	memory.writebyte(addr, aux);
      	
      	if(parity[aux]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	if((aux & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 15;
      	pc++;
      	break;

      case 0x07:           // RLC A - 8 cycles
      	a = a << 1;
      	if((a & 0x100) != 0) {
      		a |= 1;
      		f |= FLAG_CARRY;
      	}
      	else {
      		a &= 0xFE;
      		f &= ~FLAG_CARRY;
      	}
      	a &= 0xFF;
      	
      	if(parity[a & 0xFF]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	if((a & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 7;
      	pc++;
      	break;
      	
      case 0x08:           // RRC B - 8 cycles
      	if((b & 0x1) != 0) {
      		b |= 0x100;
      		f |= FLAG_CARRY | FLAG_SIGN;
      	}
      	else {
      		f &= ~(FLAG_CARRY | FLAG_SIGN);
      	}
      	b = (b >> 1) & 0xFF;
      	
      	if(b == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[b]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
        pc++;
      	break;
      	
      case 0x09:           // RRC C - 8 cycles
      	if((c & 0x1) != 0) {
      		c |= 0x100;
      		f |= FLAG_CARRY | FLAG_SIGN;
      	}
      	else {
      		f &= ~(FLAG_CARRY | FLAG_SIGN);
      	}
      	c = (c >> 1) & 0xFF;
      	
      	if(c == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[c]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
        pc++;
      	break;
      	
      case 0x0A:           // RRC D - 8 cycles
      	if((d & 0x1) != 0) {
      		d |= 0x100;
      		f |= FLAG_CARRY | FLAG_SIGN;
      	}
      	else {
      		f &= ~(FLAG_CARRY | FLAG_SIGN);
      	}
      	d = (d >> 1) & 0xFF;
      	
      	if(d == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[d]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
        pc++;
      	break;

      case 0x0B:           // RRC E - 8 cycles
      	if((e & 0x1) != 0) {
      		e |= 0x100;
      		f |= FLAG_CARRY | FLAG_SIGN;
      	}
      	else {
      		f &= ~(FLAG_CARRY | FLAG_SIGN);
      	}
      	e = (e >> 1) & 0xFF;
      	
      	if(e == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[e]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
        pc++;
      	break;

      case 0x0C:           // RRC H - 8 cycles
      	if((h & 0x1) != 0) {
      		h |= 0x100;
      		f |= FLAG_CARRY | FLAG_SIGN;
      	}
      	else {
      		f &= ~(FLAG_CARRY | FLAG_SIGN);
      	}
      	h = (h >> 1) & 0xFF;
      	
      	if(h == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[h]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
        pc++;
      	break;

      case 0x0D:           // RRC L - 8 cycles
      	if((l & 0x1) != 0) {
      		l |= 0x100;
      		f |= FLAG_CARRY | FLAG_SIGN;
      	}
      	else {
      		f &= ~(FLAG_CARRY | FLAG_SIGN);
      	}
      	l = (l >> 1) & 0xFF;
      	
      	if(l == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[l]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
        pc++;
      	break;

      case 0x0E:           // RRC (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);

      	if((aux & 0x1) != 0) {
      		aux |= 0x100;
      		f |= FLAG_CARRY | FLAG_SIGN;
      	}
      	else {
      		f &= ~(FLAG_CARRY | FLAG_SIGN);
      	}
      	aux = (aux >> 1) & 0xFF;
      	memory.writebyte(addr, aux);
      	
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[aux]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 15;
        pc++;
      	break;

      case 0x0F:           // RRC A - 8 cycles
      	if((a & 0x1) != 0) {
      		a |= 0x100;
      		f |= FLAG_CARRY | FLAG_SIGN;
      	}
      	else {
      		f &= ~(FLAG_CARRY | FLAG_SIGN);
      	}
      	a = (a >> 1) & 0xFF;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[a]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
        pc++;
      	break;

      case 0x10:           // RL B - 8 cycles
      	b = b << 1;
      	
      	if((f & FLAG_CARRY) != 0) b |= 1;
      	
      	if((b & 0x100) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	b &= 0xFF;
      	
      	if((b & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(b == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[b]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x11:           // RL C - 8 cycles
      	c = c << 1;
      	
      	if((f & FLAG_CARRY) != 0) c |= 0x1;
      	
      	if((c & 0x100) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	c &= 0xFF;
      	
      	if((c & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(c == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[c]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x12:           // RL D - 8 cycles
      	d = d << 1;
      	
      	if((f & FLAG_CARRY) != 0) d |= 1;
      	
      	if((d & 0x100) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	d &= 0xFF;
      	
      	if((d & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(d == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[d]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x13:           // RL E - 8 cycles
      	e = e << 1;
      	
      	if((f & FLAG_CARRY) != 0) e |= 1;
      	
      	if((e & 0x100) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	e &= 0xFF;
      	
      	if((e & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(e == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[e]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x14:           // RL H - 8 cycles
      	h = h << 1;
      	
      	if((f & FLAG_CARRY) != 0) h |= 1;
      	
      	if((h & 0x100) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	h &= 0xFF;
      	
      	if((h & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(h == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[h]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x15:           // RL L - 8 cycles
      	l = l << 1;
      	
      	if((f & FLAG_CARRY) != 0) l |= 1;
      	
      	if((l & 0x100) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	l &= 0xFF;
      	
      	if((l & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(l == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[l]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x16:           // RL (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
      	aux = aux << 1;
      	
      	if((f & FLAG_CARRY) != 0) aux |= 1;
      	
      	if((aux & 0x100) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	aux &= 0xFF;
      	memory.writebyte(addr, aux);
      	
      	if((aux & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[aux]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 15;
      	pc++;
      	break;
      
      case 0x17:           // RL A - 8 cycles
      	a = a << 1;
      	
      	if((f & FLAG_CARRY) != 0) a |= 1;
      	
      	if((a & 0x100) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	a &= 0xFF;
      	
      	if((a & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[a & 0xFF]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x18:           // RR B - 8 cycles
        if((f & FLAG_CARRY) != 0) {
        	b |= 0x100;
        	f |= FLAG_SIGN;
        }
        else f &= ~FLAG_SIGN;

        if((b & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	b = (b >> 1) & 0xFF;
      	
      	if(b == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[b]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x19:           // RR C - 8 cycles
        if((f & FLAG_CARRY) != 0) {
        	c |= 0x100;
        	f |= FLAG_SIGN;
        }
        else f &= ~FLAG_SIGN;

        if((c & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	c = (c >> 1) & 0xFF;
      	
      	if(c == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[c]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x1A:           // RR D - 8 cycles
        if((f & FLAG_CARRY) != 0) {
        	d |= 0x100;
        	f |= FLAG_SIGN;
        }
        else f &= ~FLAG_SIGN;

        if((d & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	d = (d >> 1) & 0xFF;
      	
      	if(d == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[d]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x1B:           // RR E - 8 cycles
        if((f & FLAG_CARRY) != 0) {
        	e |= 0x100;
        	f |= FLAG_SIGN;
        }
        else f &= ~FLAG_SIGN;

        if((e & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	e = (e >> 1) & 0xFF;
      	
      	if(e == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[e]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x1C:           // RR H - 8 cycles
        if((f & FLAG_CARRY) != 0) {
        	h |= 0x100;
        	f |= FLAG_SIGN;
        }
        else f &= ~FLAG_SIGN;

        if((h & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	h = (h >> 1) & 0xFF;
      	
      	if(h == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[h]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x1D:           // RR L - 8 cycles
        if((f & FLAG_CARRY) != 0) {
        	l |= 0x100;
        	f |= FLAG_SIGN;
        }
        else f &= ~FLAG_SIGN;

        if((l & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	l = (l >> 1) & 0xFF;
      	
      	if(l == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[l]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x1E:           // RR (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
        if((f & FLAG_CARRY) != 0) {
        	aux |= 0x100;
        	f |= FLAG_SIGN;
        }
        else f &= ~FLAG_SIGN;

        if((aux & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	aux = (aux >> 1) & 0xFF;
      	memory.writebyte(addr, aux);
      	
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[aux]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 15;
      	pc++;
      	break;
      
      case 0x1F:           // RR A - 8 cycles
        if((f & FLAG_CARRY) != 0) {
        	a |= 0x100;
        	f |= FLAG_SIGN;
        }
        else f &= ~FLAG_SIGN;

        if((a & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	a = (a >> 1) & 0xFF;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[a]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x20:           // SLA B - 8 cycles
      	if((b & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	b = (b << 1) & 0xFE;
      	
      	if(b == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((b & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[b]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
      
      case 0x21:           // SLA C - 8 cycles
      	if((c & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	c = (c << 1) & 0xFF;
      	
      	if(c == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((c & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[c]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
      
      case 0x22:           // SLA D - 8 cycles
      	if((d & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	d = (d << 1) & 0xFF;
      	
      	if(d == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((d & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[d]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
      
      case 0x23:           // SLA E - 8 cycles
      	if((e & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	e = (e << 1) & 0xFE;
      	
      	if(e == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((e & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[e]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
      
      case 0x24:           // SLA H - 8 cycles
      	if((h & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	h = (h << 1) & 0xFF;
      	
      	if(h == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((h & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[h]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
      
      case 0x25:           // SLA L - 8 cycles
      	if((l & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	l = (l << 1) & 0xFF;
      	
      	if(l == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((l & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[l]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
      
      case 0x26:           // SLA (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
      	
      	if((aux & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	aux = (aux << 1) & 0xFE;
      	memory.writebyte(addr, aux);
      	
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((aux & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[aux]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 15;
      	pc++;      		
      	break;
      
      case 0x27:           // SLA A - 8 cycles
      	if((a & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	a = (a << 1) & 0xFE;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((a & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[a]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
      
      case 0x28:           // SRA B - 8 cycles
      	if((b & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	b = ((b >> 1) & 0x7F) | (b & 0x80);
     	
      	if(b == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((b & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[b]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
     
      case 0x29:           // SRA C - 8 cycles
      	if((c & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	c = ((c >> 1) & 0x7F) | (c & 0x80);
     	
      	if(c == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((c & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[c]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
     
      case 0x2A:           // SRA D - 8 cycles
      	if((d & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	d = ((d >> 1) & 0x7F) | (d & 0x80);
     	
      	if(d == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((d & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[d]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
     
      case 0x2B:           // SRA E - 8 cycles
      	if((e & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	e = ((e >> 1) & 0x7F) | (e & 0x80);
     	
      	if(e == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((e & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[e]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
     
      case 0x2C:           // SRA H - 8 cycles
      	if((h & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	h = ((h >> 1) & 0x7F) | (h & 0x80);
     	
      	if(h == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((h & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[h]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
     
      case 0x2D:           // SRA L - 8 cycles
      	if((l & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	l = ((l >> 1) & 0x7F) | (l & 0x80);
     	
      	if(l == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((l & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[l]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
     
      case 0x2E:           // SRA (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
      	if((aux & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	aux = ((aux >> 1) & 0x7F) | (aux & 0x80);
      	memory.writebyte(addr, aux);
     	
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((aux & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[aux]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 15;
      	pc++;      		
      	break;
     
      case 0x2F:           // SRA A - 8 cycles
      	if((a & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	a = ((a >> 1) & 0x7F) | (a & 0x80);
     	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((a & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[a]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
     
      case 0x30:           // SLL B - 8 cycles *
      	if((b & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	b = (b << 1) & 0xFE;
      	b |= 1;
      	
      	if(b == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((b & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[b]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
      
      case 0x31:           // SLL C - 8 cycles *
      	if((c & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	c = (c << 1) & 0xFE;
      	c |= 1;
      	
      	if(c == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((c & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[c]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
      
      case 0x32:           // SLL D - 8 cycles *
      	if((d & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	d = (d << 1) & 0xFE;
      	d |= 1;
      	
      	if(d == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((d & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[d]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
      
      case 0x33:           // SLL E - 8 cycles *
      	if((e & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	e = (e << 1) & 0xFE;
      	e |= 1;
      	
      	if(e == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((e & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[e]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
      
      case 0x34:           // SLL H - 8 cycles *
      	if((h & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	h = (h << 1) & 0xFE;
      	h |= 1;
      	
      	if(h == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((h & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[h]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
      
      case 0x35:           // SLL L - 8 cycles *
      	if((l & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	l = (l << 1) & 0xFE;
      	l |= 1;
      	
      	if(l == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((l & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[l]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;
      
      case 0x36:           // SLL (HL) - 15 cycles *
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
      	
      	if((aux & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	aux = (aux << 1) & 0xFE;
      	aux |= 1;
      	memory.writebyte(addr, aux);
      	
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((aux & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[aux]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 15;
      	pc++;      		
      	break;
      
      case 0x37:           // SLL A - 8 cycles *
      	if((a & 0x80) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	a = (a << 1) & 0xFE;
      	a |= 1;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((a & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(parity[a & 0xFF]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 8;
      	pc++;      		
      	break;

      case 0x38:           // SRL B - 8 cycles
      	if((b & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;

      	b = (b >> 1) & 0x7F;
      	
        if(b == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if(parity[b]) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        f &= ~(FLAG_SIGN | FLAG_HCARRY | FLAG_NEG);
        counter -= 8;
        pc++;
      	break;
      
      case 0x39:           // SRL C - 8 cycles
      	if((c & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	c = (c >> 1) & 0x7F;
      	
        if(c == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if(parity[c]) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        f &= ~(FLAG_SIGN | FLAG_HCARRY | FLAG_NEG);
        counter -= 8;
        pc++;
      	break;
      
      case 0x3A:           // SRL D - 8 cycles
      	if((d & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;

      	d = (d >> 1) & 0x7F;
      	
        if(d == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if(parity[d]) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        f &= ~(FLAG_SIGN | FLAG_HCARRY | FLAG_NEG);
        counter -= 8;
        pc++;
      	break;
      
      case 0x3B:           // SRL E - 8 cycles
      	if((e & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;

      	e = (e >> 1) & 0x7F;
      	
        if(e == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if(parity[e]) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        f &= ~(FLAG_SIGN | FLAG_HCARRY | FLAG_NEG);
        counter -= 8;
        pc++;
      	break;
      
      case 0x3C:           // SRL H - 8 cycles
      	if((h & 0x01) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	h = (h >> 1) & 0x7F;
      	
        if(h == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if(parity[h]) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        f &= ~(FLAG_SIGN | FLAG_HCARRY | FLAG_NEG);
        counter -= 8;
        pc++;
      	break;
      
      case 0x3D:           // SRL L - 8 cycles
      	if((l & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;

      	l = (l >> 1) & 0x7F;
      	
        if(l == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if(parity[l]) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        f &= ~(FLAG_SIGN | FLAG_HCARRY | FLAG_NEG);
        counter -= 8;
        pc++;
      	break;
      
      case 0x3E:           // SRL (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
      	
      	if((aux & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;

      	aux = (aux >> 1) & 0x7F;

      	memory.writebyte(addr, aux);
      	
        if(aux == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if(parity[aux]) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        f &= ~(FLAG_SIGN | FLAG_HCARRY | FLAG_NEG);
        counter -= 15;
        pc++;
      	break;
      
      case 0x3F:           // SRL A - 8 cycles
      	if((a & 0x1) != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	a = (a >> 1) & 0x7F;
      	
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        f &= ~(FLAG_SIGN | FLAG_HCARRY | FLAG_NEG);
        counter -= 8;
        pc++;
      	break;
      
      case 0x40:           // BIT 0, B - 8 cycles
       	if((b & 0x1) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x41:           // BIT 0, C - 8 cycles
       	if((c & 0x1) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x42:           // BIT 0, D - 8 cycles
       	if((d & 0x1) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;

        break;
      	
      case 0x43:           // BIT 0, E - 8 cycles
       	if((e & 0x1) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;

        break;
      	
      case 0x44:           // BIT 0, H - 8 cycles
       	if((h & 0x1) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;

        break;
      	
      case 0x45:           // BIT 0, L - 8 cycles
       	if((l & 0x1) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;

        break;

      case 0x46:           // BIT 0, (HL) - 12 cycles
      	aux = memory.readbyte((h << 8) | l);
       	if((aux & 0x1) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x47:           // BIT 0, A - 8 cycles
       	if((a & 0x1) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x48:           // BIT 1, B - 8 cycles
       	if((b & 0x2) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x49:           // BIT 1, C - 8 cycles
       	if((c & 0x2) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x4A:           // BIT 1, D - 8 cycles
       	if((d & 0x2) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x4B:           // BIT 1, E - 8 cycles
       	if((e & 0x2) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x4C:           // BIT 1, H - 8 cycles
       	if((h & 0x2) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x4D:           // BIT 1, L - 8 cycles
       	if((l & 0x2) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x4E:           // BIT 1, (HL) - 12 cycles
      	aux = memory.readbyte((h << 8) | l);
       	if((aux & 0x2) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 12;
      	pc++;
      	break;

      case 0x4F:           // BIT 1, A - 8 cycles
       	if((a & 0x2) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x50:           // BIT 2, B - 8 cycles
       	if((b & 0x4) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x51:           // BIT 2, C - 8 cycles
       	if((c & 0x4) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x52:           // BIT 2, D - 8 cycles
       	if((d & 0x4) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x53:           // BIT 2, E - 8 cycles
       	if((e & 0x4) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	f &= ~(FLAG_NEG | FLAG_HCARRY);
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x54:           // BIT 2, H - 8 cycles
       	if((h & 0x4) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x55:           // BIT 2, L - 8 cycles
       	if((l & 0x4) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
        break;
      	
      case 0x56:           // BIT 2, (HL) - 12 cycles
      	aux = memory.readbyte((h << 8) | l);
       	if((aux & 0x4) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 12;
      	pc++;
      	break;

      case 0x57:           // BIT 2, A - 8 cycles
       	if((a & 0x4) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x58:           // BIT 3, B - 8 cycles
      	if((b & 0x8) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x59:           // BIT 3, C - 8 cycles
      	if((c & 0x8) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x5A:           // BIT 3, D - 8 cycles
      	if((d & 0x8) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x5B:           // BIT 3, E - 8 cycles
      	if((e & 0x8) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x5C:           // BIT 3, H - 8 cycles
      	if((h & 0x8) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x5D:           // BIT 3, L - 8 cycles
      	if((l & 0x8) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x5E:           // BIT 3, (HL) - 12 cycles
      	aux = memory.readbyte((h << 8) | l);
      	if((aux & 0x8) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 12;
      	pc++;
      	break;

      case 0x5F:           // BIT 3, A - 8 cycles
      	if((a & 0x8) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x60:           // BIT 4, B - 8 cycles
      	if((b & 0x10) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x61:           // BIT 4, C - 8 cycles
      	if((c & 0x10) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x62:           // BIT 4, D - 8 cycles
      	if((d & 0x10) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x63:           // BIT 4, E - 8 cycles
      	if((e & 0x10) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x64:           // BIT 4, H - 8 cycles
      	if((h & 0x10) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x65:           // BIT 4, L - 8 cycles
      	if((l & 0x10) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x66:           // BIT 4, (HL) - 12 cycles
      	aux = memory.readbyte((h << 8) | l);
      	if((aux & 0x10) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 12;
      	pc++;
      	break;

      case 0x67:           // BIT 4, A - 8 cycles
      	if((a & 0x10) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x68:           // BIT 5, B - 8 cycles
      	if((b & 0x20) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x69:           // BIT 5, C - 8 cycles
      	if((c & 0x20) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x6A:           // BIT 5, D - 8 cycles
      	if((d & 0x20) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x6B:           // BIT 5, E - 8 cycles
      	if((e & 0x20) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x6C:           // BIT 5, H - 8 cycles
      	if((h & 0x20) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x6D:           // BIT 5, L - 8 cycles
      	if((l & 0x20) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x6E:           // BIT 5, (HL) - 12 cycles
      	aux = memory.readbyte((h << 8) | l);
      	if((aux & 0x20) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 12;
      	pc++;
      	break;

      case 0x6F:           // BIT 5, A - 8 cycles
      	if((a & 0x20) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x70:           // BIT 6, B - 8 cycles
      	if((b & 0x40) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x71:           // BIT 6, C - 8 cycles
      	if((c & 0x40) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x72:           // BIT 6, D - 8 cycles
      	if((d & 0x40) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x73:           // BIT 6, E - 8 cycles
      	if((e & 0x40) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x74:           // BIT 6, H - 8 cycles
      	if((h & 0x40) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x75:           // BIT 6, L - 8 cycles
      	if((l & 0x40) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x76:           // BIT 6, (HL) - 12 cycles
      	aux = memory.readbyte((h << 8) | l);
      	if((aux & 0x40) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 12;
      	pc++;
      	break;

      case 0x77:           // BIT 6, A - 8 cycles
      	if((a & 0x40) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x78:           // BIT 7, B - 8 cycles
      	if((b & 0x80) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
      	if((f & FLAG_ZERO) == 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
		
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x79:           // BIT 7, C - 8 cycles
      	if((c & 0x80) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
      	if((f & FLAG_ZERO) == 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
		
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x7A:           // BIT 7, D - 8 cycles
      	if((d & 0x80) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
      	if((f & FLAG_ZERO) == 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
		
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x7B:           // BIT 7, E - 8 cycles
      	if((e & 0x80) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
      	if((f & FLAG_ZERO) == 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
		
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x7C:           // BIT 7, H - 8 cycles
      	if((h & 0x80) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
      	if((f & FLAG_ZERO) == 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
		
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x7D:           // BIT 7, L - 8 cycles
      	if((l & 0x80) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
      	if((f & FLAG_ZERO) == 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
		
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;

      case 0x7E:           // BIT 7, (HL) - 12 cycles
      	aux = memory.readbyte((h << 8) | l);
      	if((aux & 0x80) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
      	if((f & FLAG_ZERO) == 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
		
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 12;
      	pc++;
      	break;

      case 0x7F:           // BIT 7, A - 8 cycles
      	if((a & 0x80) == 0) f |= FLAG_ZERO | FLAG_PV;
      	else f &= ~(FLAG_ZERO | FLAG_PV);
      	if((f & FLAG_ZERO) == 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
		
       	f |= FLAG_HCARRY;
      	f &= ~FLAG_NEG;
      	counter -= 8;
      	pc++;
      	break;
      
      case 0x80:           // RES 0, B - 8 cycles
        b &= ~0x01;
        counter -= 8;
        pc++;
        break;
      	
      case 0x81:           // RES 0, C - 8 cycles
        c &= ~0x01;
        counter -= 8;
        pc++;
        break;
      	
      case 0x82:           // RES 0, D - 8 cycles
        d &= ~0x01;
        counter -= 8;
        pc++;
        break;
      	
      case 0x83:           // RES 0, E - 8 cycles
        e &= ~0x01;
        counter -= 8;
        pc++;
        break;
      	
      case 0x84:           // RES 0, H - 8 cycles
        h &= ~0x01;
        counter -= 8;
        pc++;
        break;
      	
      case 0x85:           // RES 0, L - 8 cycles
        l &= ~0x01;
        counter -= 8;
        pc++;
        break;
      	
      case 0x86:           // RES 0, (HL) - 15 cycles
      	addr = (h << 8) | l;
        aux = memory.readbyte(addr);
      	aux &= ~0x01;
      	memory.writebyte(addr, aux);
        counter -= 15;
        pc++;
        break;
      	
      case 0x87:           // RES 0, A - 8 cycles
        a &= ~0x01;
        counter -= 8;
        pc++;
        break;
      	
      case 0x88:           // RES 1, B - 8 cycles
        b &= ~0x02;
        counter -= 8;
        pc++;
        break;

      case 0x89:           // RES 1, C - 8 cycles
        c &= ~0x02;
        counter -= 8;
        pc++;
        break;

      case 0x8A:           // RES 1, D - 8 cycles
        d &= ~0x02;
        counter -= 8;
        pc++;
        break;

      case 0x8B:           // RES 1, E - 8 cycles
        e &= ~0x02;
        counter -= 8;
        pc++;
        break;

      case 0x8C:           // RES 1, H - 8 cycles
        h &= ~0x02;
        counter -= 8;
        pc++;
        break;

      case 0x8D:           // RES 1, L - 8 cycles
        l &= ~0x02;
        counter -= 8;
        pc++;
        break;

      case 0x8E:           // RES 1, (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
      	aux &= ~0x02;
      	memory.writebyte(addr, aux);
      	counter -= 15;
      	pc++;
      	break;

      case 0x8F:           // RES 1, A - 8 cycles
        a &= ~0x02;
        counter -= 8;
        pc++;
        break;

      case 0x90:           // RES 2, B - 8 cycles
        b &= ~0x04;
        counter -= 8;
        pc++;
        break;

      case 0x91:           // RES 2, C - 8 cycles
        c &= ~0x04;
        counter -= 8;
        pc++;
        break;

      case 0x92:           // RES 2, D - 8 cycles
        d &= ~0x04;
        counter -= 8;
        pc++;
        break;

      case 0x93:           // RES 2, E - 8 cycles
        e &= ~0x04;
        counter -= 8;
        pc++;
        break;

      case 0x94:           // RES 2, H - 8 cycles
        h &= ~0x04;
        counter -= 8;
        pc++;
        break;

      case 0x95:           // RES 2, L - 8 cycles
        l &= ~0x04;
        counter -= 8;
        pc++;
        break;

      case 0x96:           // RES 2, (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
      	aux &= ~0x04;
      	memory.writebyte(addr, aux);
      	counter -= 15;
      	pc++;
      	break;

      case 0x97:           // RES 2, A - 8 cycles
        a &= ~0x04;
        counter -= 8;
        pc++;
        break;

      case 0x98:           // RES 3, B - 8 cycles
        b &= ~0x08;
        counter -= 8;
        pc++;
        break;

      case 0x99:           // RES 3, C - 8 cycles
        c &= ~0x08;
        counter -= 8;
        pc++;
        break;

      case 0x9A:           // RES 3, D - 8 cycles
        d &= ~0x08;
        counter -= 8;
        pc++;
        break;

      case 0x9B:           // RES 3, E - 8 cycles
        e &= ~0x08;
        counter -= 8;
        pc++;
        break;

      case 0x9C:           // RES 3, H - 8 cycles
        h &= ~0x08;
        counter -= 8;
        pc++;
        break;

      case 0x9D:           // RES 3, L - 8 cycles
        l &= ~0x08;
        counter -= 8;
        pc++;
        break;

      case 0x9E:           // RES 3, (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
      	aux &= ~0x08;
      	memory.writebyte(addr, aux);
      	counter -= 15;
      	pc++;
      	break;

      case 0x9F:           // RES 3, A - 8 cycles
        a &= ~0x08;
        counter -= 8;
        pc++;
        break;

      case 0xA0:           // RES 4, B - 8 cycles
        b &= ~0x10;
        counter -= 8;
        pc++;
        break;

      case 0xA1:           // RES 4, C - 8 cycles
        c &= ~0x10;
        counter -= 8;
        pc++;
        break;

      case 0xA2:           // RES 4, D - 8 cycles
        d &= ~0x10;
        counter -= 8;
        pc++;
        break;

      case 0xA3:           // RES 4, E - 8 cycles
        e &= ~0x10;
        counter -= 8;
        pc++;
        break;

      case 0xA4:           // RES 4, H - 8 cycles
        h &= ~0x10;
        counter -= 8;
        pc++;
        break;

      case 0xA5:           // RES 4, L - 8 cycles
        l &= ~0x10;
        counter -= 8;
        pc++;
        break;

      case 0xA6:           // RES 4, (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
      	aux &= ~0x10;
      	memory.writebyte(addr, aux);
      	counter -= 15;
      	pc++;
      	break;

      case 0xA7:           // RES 4, A - 8 cycles
        a &= ~0x10;
        counter -= 8;
        pc++;
        break;

      case 0xA8:           // RES 5, B - 8 cycles
        b &= ~0x20;
        counter -= 8;
        pc++;
        break;

      case 0xA9:           // RES 5, C - 8 cycles
        c &= ~0x20;
        counter -= 8;
        pc++;
        break;

      case 0xAA:           // RES 5, D - 8 cycles
        d &= ~0x20;
        counter -= 8;
        pc++;
        break;

      case 0xAB:           // RES 5, E - 8 cycles
        e &= ~0x20;
        counter -= 8;
        pc++;
        break;

      case 0xAC:           // RES 5, H - 8 cycles
        h &= ~0x20;
        counter -= 8;
        pc++;
        break;

      case 0xAD:           // RES 5, L - 8 cycles
        l &= ~0x20;
        counter -= 8;
        pc++;
        break;

      case 0xAE:           // RES 5, (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
      	aux &= ~0x20;
      	memory.writebyte(addr, aux);
      	counter -= 15;
      	pc++;
      	break;

      case 0xAF:           // RES 5, A - 8 cycles
        a &= ~0x20;
        counter -= 8;
        pc++;
        break;

      case 0xB0:           // RES 6, B - 8 cycles
        b &= ~0x40;
        counter -= 8;
        pc++;
        break;

      case 0xB1:           // RES 6, C - 8 cycles
        c &= ~0x40;
        counter -= 8;
        pc++;
        break;

      case 0xB2:           // RES 6, D - 8 cycles
        d &= ~0x40;
        counter -= 8;
        pc++;
        break;

      case 0xB3:           // RES 6, E - 8 cycles
        e &= ~0x40;
        counter -= 8;
        pc++;
        break;

      case 0xB4:           // RES 6, H - 8 cycles
        h &= ~0x40;
        counter -= 8;
        pc++;
        break;

      case 0xB5:           // RES 6, L - 8 cycles
        l &= ~0x40;
        counter -= 8;
        pc++;
        break;

      case 0xB6:           // RES 6, (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
      	aux &= ~0x40;
      	memory.writebyte(addr, aux);
      	counter -= 15;
      	pc++;
      	break;

      case 0xB7:           // RES 6, A - 8 cycles
        a &= ~0x40;
        counter -= 8;
        pc++;
        break;

      case 0xB8:           // RES 7, B - 8 cycles
        b &= ~0x80;
        counter -= 8;
        pc++;
        break;

      case 0xB9:           // RES 7, C - 8 cycles
        c &= ~0x80;
        counter -= 8;
        pc++;
        break;

      case 0xBA:           // RES 7, D - 8 cycles
        d &= ~0x80;
        counter -= 8;
        pc++;
        break;

      case 0xBB:           // RES 7, E - 8 cycles
        e &= ~0x80;
        counter -= 8;
        pc++;
        break;

      case 0xBC:           // RES 7, H - 8 cycles
        h &= ~0x80;
        counter -= 8;
        pc++;
        break;

      case 0xBD:           // RES 7, L - 8 cycles
        l &= ~0x80;
        counter -= 8;
        pc++;
        break;

      case 0xBE:           // RES 7, (HL) - 15 cycles
      	addr = (h << 8) | l;
      	aux = memory.readbyte(addr);
      	aux &= ~0x80;
      	memory.writebyte(addr, aux);
      	counter -= 15;
      	pc++;
      	break;

      case 0xBF:           // RES 7, A - 8 cycles
        a &= ~0x80;
        counter -= 8;
        pc++;
        break;

      case 0xC0:           // SET 0, B - 8 cycles
        b |= 0x01;
        counter -= 8;
        pc++;
        break;

      case 0xC1:           // SET 0, C - 8 cycles
        c |= 0x01;
        counter -= 8;
        pc++;
        break;

      case 0xC2:           // SET 0, D - 8 cycles
        d |= 0x01;
        counter -= 8;
        pc++;
        break;

      case 0xC3:           // SET 0, E - 8 cycles
        e |= 0x01;
        counter -= 8;
        pc++;
        break;

      case 0xC4:           // SET 0, H - 8 cycles
        h |= 0x01;
        counter -= 8;
        pc++;
        break;

      case 0xC5:           // SET 0, L - 8 cycles
        l |= 0x01;
        counter -= 8;
        pc++;
        break;

      case 0xC6:           // SET 0, (HL) - 15 cycles
      	addr = (h << 8) | l;
        aux = memory.readbyte(addr);
        aux |= 0x01;
        memory.writebyte(addr, aux);
        counter -= 15;
        pc++;
        break;

      case 0xC7:           // SET 1, A - 8 cycles
        a |= 0x02;
        counter -= 8;
        pc++;
        break;

      case 0xC8:           // SET 1, B - 8 cycles
        b |= 0x02;
        counter -= 8;
        pc++;
        break;

      case 0xC9:           // SET 1, C - 8 cycles
        c |= 0x02;
        counter -= 8;
        pc++;
        break;

      case 0xCA:           // SET 1, D - 8 cycles
        d |= 0x02;
        counter -= 8;
        pc++;
        break;

      case 0xCB:           // SET 1, E - 8 cycles
        e |= 0x02;
        counter -= 8;
        pc++;
        break;

      case 0xCC:           // SET 1, H - 8 cycles
        h |= 0x02;
        counter -= 8;
        pc++;
        break;

      case 0xCD:           // SET 1, L - 8 cycles
        l |= 0x02;
        counter -= 8;
        pc++;
        break;

      case 0xCE:           // SET 1, (HL) - 15 cycles
      	addr = (h << 8) | l;
        aux = memory.readbyte(addr);
        aux |= 0x02;
        memory.writebyte(addr, aux);
        counter -= 15;
        pc++;
        break;

      case 0xCF:           // SET 1, A - 8 cycles
        a |= 0x02;
        counter -= 8;
        pc++;
        break;

      case 0xD0:           // SET 2, B - 8 cycles
        b |= 0x04;
        counter -= 8;
        pc++;
        break;

      case 0xD1:           // SET 2, C - 8 cycles
        c |= 0x04;
        counter -= 8;
        pc++;
        break;

      case 0xD2:           // SET 2, D - 8 cycles
        d |= 0x04;
        counter -= 8;
        pc++;
        break;

      case 0xD3:           // SET 2, E - 8 cycles
        e |= 0x04;
        counter -= 8;
        pc++;
        break;

      case 0xD4:           // SET 2, H - 8 cycles
        h |= 0x04;
        counter -= 8;
        pc++;
        break;

      case 0xD5:           // SET 2, L - 8 cycles
        l |= 0x04;
        counter -= 8;
        pc++;
        break;

      case 0xD6:           // SET 2, (HL) - 15 cycles
      	addr = (h << 8) | l;
        aux = memory.readbyte(addr);
        aux |= 0x04;
        memory.writebyte(addr, aux);
        counter -= 15;
        pc++;
        break;

      case 0xD7:           // SET 2, A - 8 cycles
        a |= 0x04;
        counter -= 8;
        pc++;
        break;

      case 0xD8:           // SET 3, B - 8 cycles
        b |= 0x08;
        counter -= 8;
        pc++;
        break;

      case 0xD9:           // SET 3, C - 8 cycles
        c |= 0x08;
        counter -= 8;
        pc++;
        break;

      case 0xDA:           // SET 3, D - 8 cycles
        d |= 0x08;
        counter -= 8;
        pc++;
        break;

      case 0xDB:           // SET 3, E - 8 cycles
        e |= 0x08;
        counter -= 8;
        pc++;
        break;

      case 0xDC:           // SET 3, H - 8 cycles
        h |= 0x08;
        counter -= 8;
        pc++;
        break;

      case 0xDD:           // SET 3, L - 8 cycles
        l |= 0x08;
        counter -= 8;
        pc++;
        break;

      case 0xDE:           // SET 3, (HL) - 15 cycles
      	addr = (h << 8) | l;
        aux = memory.readbyte(addr);
        aux |= 0x08;
        memory.writebyte(addr, aux);
        counter -= 15;
        pc++;
        break;

      case 0xDF:           // SET 3, A - 8 cycles
        a |= 0x08;
        counter -= 8;
        pc++;
        break;

      case 0xE0:           // SET 4, B - 8 cycles
        b |= 0x10;
        counter -= 8;
        pc++;
        break;

      case 0xE1:           // SET 4, C - 8 cycles
        c |= 0x10;
        counter -= 8;
        pc++;
        break;

      case 0xE2:           // SET 4, D - 8 cycles
        d |= 0x10;
        counter -= 8;
        pc++;
        break;

      case 0xE3:           // SET 4, E - 8 cycles
        e |= 0x10;
        counter -= 8;
        pc++;
        break;

      case 0xE4:           // SET 4, H - 8 cycles
        h |= 0x10;
        counter -= 8;
        pc++;
        break;

      case 0xE5:           // SET 4, L - 8 cycles
        l |= 0x10;
        counter -= 8;
        pc++;
        break;

      case 0xE6:           // SET 4, (HL) - 15 cycles
      	addr = (h << 8) | l;
        aux = memory.readbyte(addr);
        aux |= 0x10;
        memory.writebyte(addr, aux);
        counter -= 15;
        pc++;
        break;

      case 0xE7:           // SET 4, A - 8 cycles
        a |= 0x10;
        counter -= 8;
        pc++;
        break;

      case 0xE8:           // SET 5, B - 8 cycles
        b |= 0x20;
        counter -= 8;
        pc++;
        break;

      case 0xE9:           // SET 5, C - 8 cycles
        c |= 0x20;
        counter -= 8;
        pc++;
        break;

      case 0xEA:           // SET 5, D - 8 cycles
        d |= 0x20;
        counter -= 8;
        pc++;
        break;

      case 0xEB:           // SET 5, E - 8 cycles
        e |= 0x20;
        counter -= 8;
        pc++;
        break;

      case 0xEC:           // SET 5, H - 8 cycles
        h |= 0x20;
        counter -= 8;
        pc++;
        break;

      case 0xED:           // SET 5, L - 8 cycles
        l |= 0x20;
        counter -= 8;
        pc++;
        break;

      case 0xEE:           // SET 5, (HL) - 15 cycles
      	addr = (h << 8) | l;
        aux = memory.readbyte(addr);
        aux |= 0x20;
        memory.writebyte(addr, aux);
        counter -= 15;
        pc++;
        break;

      case 0xEF:           // SET 5, A - 8 cycles
        a |= 0x20;
        counter -= 8;
        pc++;
        break;

      case 0xF0:           // SET 6, B - 8 cycles
        b |= 0x40;
        counter -= 8;
        pc++;
        break;

      case 0xF1:           // SET 6, C - 8 cycles
        c |= 0x40;
        counter -= 8;
        pc++;
        break;

      case 0xF2:           // SET 6, D - 8 cycles
        d |= 0x40;
        counter -= 8;
        pc++;
        break;

      case 0xF3:           // SET 6, E - 8 cycles
        e |= 0x40;
        counter -= 8;
        pc++;
        break;

      case 0xF4:           // SET 6, H - 8 cycles
        h |= 0x40;
        counter -= 8;
        pc++;
        break;

      case 0xF5:           // SET 6, L - 8 cycles
        l |= 0x40;
        counter -= 8;
        pc++;
        break;

      case 0xF6:           // SET 6, (HL) - 15 cycles
      	addr = (h << 8) | l;
        aux = memory.readbyte(addr);
        aux |= 0x40;
        memory.writebyte(addr, aux);
        counter -= 15;
        pc++;
        break;

      case 0xF7:           // SET 6, A - 8 cycles
        a |= 0x40;
        counter -= 8;
        pc++;
        break;

      case 0xF8:           // SET 7, B - 8 cycles
        b |= 0x80;
        counter -= 8;
        pc++;
        break;

      case 0xF9:           // SET 7, C - 8 cycles
        c |= 0x80;
        counter -= 8;
        pc++;
        break;

      case 0xFA:           // SET 7, D - 8 cycles
        d |= 0x80;
        counter -= 8;
        pc++;
        break;

      case 0xFB:           // SET 7, E - 8 cycles
        e |= 0x80;
        counter -= 8;
        pc++;
        break;

      case 0xFC:           // SET 7, H - 8 cycles
        h |= 0x80;
        counter -= 8;
        pc++;
        break;

      case 0xFD:           // SET 7, L - 8 cycles
        l |= 0x80;
        counter -= 8;
        pc++;
        break;

      case 0xFE:           // SET 7, (HL) - 15 cycles
      	addr = (h << 8) | l;
        aux = memory.readbyte(addr);
        aux |= 0x80;
        memory.writebyte(addr, aux);
        counter -= 15;
        pc++;
        break;

      case 0xFF:           // SET 7, A - 8 cycles
        a |= 0x80;
        counter -= 8;
        pc++;
        break;

      default:             // Uninmplemented opcode
      	if(!msg.isVisible()) msg.setVisible(true);
        msg.println("PC = $" + Integer.toHexString(pc) + "  0xCB" + Integer.toHexString(opcode).toUpperCase() + ": Unknown opcode");
        //memory.dumpMemory();
        //vdp.dumpMemory();
        //System.exit(0);
        pc++;
    }
    flagreg = f;
  }

  public final void exec_DD_opcode(int opcode)
  {
  	int aux = 0, f;
  	f = flagreg;
  	
    switch(opcode)
    {
      case 0x09:           // ADD IX, BC - 15 cycles
      	aux = ix & 0xFFF;
      	aux += ((b << 8) | c) & 0xFFF;
      	if(aux > 0xFFF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	ix += ((b << 8) | c);
      	
        if(ix > 0xFFFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if(ix > 0x7FFF) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        if((ix & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        ix &= 0xFFFF;
        
        if(ix == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        f &= ~FLAG_NEG;
        counter -= 15;
        pc++;
       	break;
          	
      case 0x19:           // ADD IX, DE - 15 cycles
      	aux = ix & 0xFFF;
      	aux += ((d << 8) | e) & 0xFFF;
      	if(aux > 0xFFF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	ix += ((d << 8) | e);
      	
        if(ix > 0xFFFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if(ix > 0x7FFF) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        if((ix & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        ix &= 0xFFFF;
        
        if(ix == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        f &= ~FLAG_NEG;
        counter -= 15;
        pc++;
      	break;
      	
      case 0x21:           // LD IX, nn - 14 cycles
        ix = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
        counter -= 14;
        pc += 3;
        break;
      
      case 0x22:           // LD (nn), IX - 20 cycles
      	aux = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
      	memory.writebyte(aux, (ix & 0xFF));
      	memory.writebyte(aux+1, ((ix >> 8) & 0xFF));
      	
      	counter -= 20;
      	pc += 3;
      	break;

      case 0x23:           // INC IX - 10 cycles
      	ix = (ix + 1) & 0xFFFF;
		counter -= 10;
		pc++;
      	break;
     
      case 0x24:           // INC IXH - 4 cycles *
      	aux = (ix >> 8) & 0xFF;
        if((aux & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        aux = ((aux + 1) & 0xFF);
        ix &= 0xFF;
        ix |= aux << 8;

        if(aux == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(aux == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((aux & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }
        }

        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x25:           // DEC IXH - 4 cycles *
      	aux = (ix >> 8) & 0xFF;
      	if(aux == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
        aux = ((aux - 1) & 0xFF);
        ix &= 0xFF;
        ix |= aux << 8;

        if((aux & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        if(aux==0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((aux & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        f |= FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x26:           // LD IXH, n - 7 cycles *
      	aux = memory.readbyte(pc+1) << 8;
      	ix &= 0xFF;
      	ix |= aux;
      	
      	counter -= 7;
      	pc += 2;
      	break;
      	
      case 0x29:           // ADD IX, IX - 15 cycles
      	aux = ix & 0xFFF;
      	aux <<= 1;
      	if(aux > 0xFFF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	ix <<= 1;
      	
        if(ix > 0xFFFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if(ix > 0x7FFF) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        if((ix & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        ix &= 0xFFFF;
        
        if(ix == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        f &= ~FLAG_NEG;
        counter -= 15;
        pc++;
      	break;
     
      case 0x2A:           // LD IX, (nn) - 20 cycles
      	aux = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
      	ix = memory.readbyte(aux + 1) << 8;
      	ix |= memory.readbyte(aux);
      	
      	counter -= 20;
      	pc += 3;
      	break;
      
      case 0x2B:           // DEC IX - 10 cycles
      	ix--;
      	ix &= 0xFFFF;
      	
      	counter -= 10;
      	pc++;
      	break;
      	
      case 0x2C:           // INC IXL - 4 cycles *
      	aux = ix & 0xFF;
        if((aux & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        aux = ((aux + 1) & 0xFF);
        ix &= 0xFF00;
        ix |= aux;

        if(aux == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(aux == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((aux & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }
        }

        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x2D:           // DEC IXL - 4 cycles *
      	aux = ix & 0xFF;
      	if(aux == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
        aux = ((aux - 1) & 0xFF);
        ix &= 0xFF00;
        ix |= aux;

        if((aux & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        if(aux==0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((aux & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        f |= FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x2E:           // LD IXL, n - 7 cycles ***
      	aux = memory.readbyte(pc+1);
      	ix &= 0xFF00;
      	ix |= aux;
      	
      	counter -= 7;
      	pc += 2;
      	break;
      
      case 0x34:           // INC (IX+d) - 23 cycles
      	aux = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);

        if((aux & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

      	aux = (aux + 1) & 0xFF;
      	memory.writebyte((ix + memory.readsigned(pc+1)) & 0xFFFF, aux);
      	
        if(aux == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(aux == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((aux & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }
        }

        f &= ~FLAG_NEG;
      	counter -= 23;
      	pc += 2;
      	break;
      	
      case 0x35:           // DEC (IX+d) - 23 cycles
      	aux = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);

      	if(aux == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	aux = (aux - 1) & 0xFF;

      	if((aux & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

      	memory.writebyte((ix + memory.readsigned(pc+1)) & 0xFFFF, aux);
      	
        if(aux == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((aux & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        f |= FLAG_NEG;
      	counter -= 23;
        pc += 2;
      	break;
      	
      case 0x36:           // LD (IX+d), n - 19 cycles
        memory.writebyte((ix + memory.readsigned(pc+1)) & 0xFFFF, memory.readbyte(pc+2));
        counter -= 19;
        pc += 3;
        break;
        
      case 0x39:           // ADD IX, SP - 15 cycles
      	aux = (ix & 0xFFF) + (sp & 0xFFF);
      	if(aux > 0xFFF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	ix += sp;
      	if(ix > 0xFFFF) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;

        if(ix > 0x7FFF) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        if((ix & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        ix &= 0xFFFF;
        
        if(ix == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
      	
      	f &= ~FLAG_NEG;
       	counter -= 15;
      	pc++;
      	break;
      	
      case 0x46:           // LD B, (IX+d) - 19 cycles
        b = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;
        
      case 0x4E:           // LD C, (IX+d) - 19 cycles
        c = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;
      
      case 0x56:           // LD D, (IX+d) - 19 cycles
        d = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;

      case 0x5E:           // LD E, (IX+d) - 19 cycles
        e = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;
        
      case 0x66:           // LD H, (IX+d) - 19 cycles
        h = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;
        
      case 0x67:           // LD IXH, A - 7 cycles *
      	ix &= 0xFF;
      	ix |= a << 8;
      	
      	counter -= 7;
      	pc++;
      	break;

      case 0x6E:           // LD L, (IX+d) - 19 cycles
        l = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;
      
      case 0x6F:           // LD IXL, A - 7 cycles *
      	ix &= 0xFF00;
      	ix |= (a & 0xFF);
      	counter -= 7;
      	pc++;
      	break;

      case 0x70:           // LD (IX+d), B - 19 cycles
        memory.writebyte((ix + memory.readsigned(pc+1)) & 0xFFFF, b & 0xFF);
        counter -= 19;
        pc += 2;
        break;

      case 0x71:           // LD (IX+d), C - 19 cycles
        memory.writebyte((ix + memory.readsigned(pc+1)) & 0xFFFF, c & 0xFF);
        counter -= 19;
        pc += 2;
        break;

      case 0x72:           // LD (IX+d), D - 19 cycles
        memory.writebyte((ix + memory.readsigned(pc+1)) & 0xFFFF, d & 0xFF);
        counter -= 19;
        pc += 2;
        break;

      case 0x73:           // LD (IX+d), E - 19 cycles
        memory.writebyte((ix + memory.readsigned(pc+1)) & 0xFFFF, e & 0xFF);
        counter -= 19;
        pc += 2;
        break;

      case 0x74:           // LD (IX+d), H - 19 cycles
        memory.writebyte((ix + memory.readsigned(pc+1)) & 0xFFFF, h & 0xFF);
        counter -= 19;
        pc += 2;
        break;

      case 0x75:           // LD (IX+d), L - 19 cycles
        memory.writebyte((ix + memory.readsigned(pc+1)) & 0xFFFF, l & 0xFF);
        counter -= 19;
        pc += 2;
        break;

      case 0x77:           // LD (IX+d), A - 19 cycles
        memory.writebyte((ix + memory.readsigned(pc+1)) & 0xFFFF, a & 0xFF);
        counter -= 19;
        pc += 2;
        break;
      
      case 0x7C:           // LD A, IXH - 7 cycles *
      	a = (ix >> 8) & 0xFF;
      	counter -= 7;
      	pc++;
      	break;
      	
      case 0x7D:           // LD A, IXL - 7 cycles *
      	a = ix & 0xFF;
      	counter -= 7;
      	pc++;
      	break;

      case 0x7E:           // LD A, (IX+d) - 19 cycles
      	a = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;
        
      case 0x86:           // ADD A, (IX+d) - 16 cycles
        aux = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);

      	if((a & 0xF) + (aux & 0xF) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	a += aux;
        
        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        a = (a & 0xFF);

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        f &= ~FLAG_NEG;
        counter -= 16;
        pc += 2;
        break;
      
      case 0x8E:           // ADC A, (IX+d) - 19 cycles
        aux = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);

      	if(((a & 0xF) + (aux & 0xF) + (((f & FLAG_CARRY) != 0) ? 1 : 0)) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a += aux;
        if((f & FLAG_CARRY) != 0) a++;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        a &= 0xFF;
        
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        f &= ~FLAG_NEG;
        counter -= 19;
        pc += 2;
      	break;
      
      case 0x96:           // SUB (IX+d) - 19 cycles
      	aux = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);
      	
      	if((a & 0xF) < (aux & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	a = (a & 0xFF) - aux;
      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	a &= 0xFF;
      	
      	if((a & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 19;
      	pc += 2;
      	break;
      	
      case 0x9E:           // SBC A, (IX+d) - 19 cycles
      	aux = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);

      	if((a & 0xF) < ((aux & 0xF) - ((f & FLAG_CARRY) != 0 ? 1 : 0))) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	a -= aux;
      	if((f & FLAG_CARRY) != 0) a--;
      	
      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	if(a < -128) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	a &= 0xFF;
      	
      	if((a & 0x80) != 0) f|= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	      	
      	f |= FLAG_NEG;
      	counter -= 19;
      	pc += 2;
      	break;
      	
      case 0xA6:           // AND (IX+d) - 19 cycles
        a &= memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if(parity[a & 0xFF] == true) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_NEG | FLAG_CARRY);
        f |= FLAG_HCARRY;
        counter -= 19;
        pc += 2;
      	break;
      	
      case 0xAE:           // XOR (IX+d) - 19 cycles
        a ^= memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
      	counter -= 19;
      	pc += 2;
      	break;
     
      case 0xB5:           // OR IXL - 7 cycles *
        a |= ix & 0xFF;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
      	counter -= 7;
      	pc++;
      	break;
      	
      case 0xB6:           // OR (IX+d) - 19 cycles
        a |= memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 19;
        pc += 2;
        break;

      case 0xBE:           // CP (IX+d) - 19 cycles
      	aux = memory.readbyte((ix + memory.readsigned(pc+1)) & 0xFFFF);
      	aux = (a & 0xFF) - (aux & 0xFF);

      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((aux > 0x7F) || aux < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	aux = aux & 0xFF;
      	
      	if((aux & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 19;
      	pc += 2;
      	break;
      
      case 0xCB:           // DD CB prefixed opcodes
      	pc += 2;
      	flagreg = f;
      	exec_DDCB_opcode(memory.readbyte(pc));
      	f = flagreg;
      	break;

      case 0xE1:           // POP IX - 14 cycles
        ix = pop();
        counter -= 14;
        pc++;
        break;
        
      case 0xE3:           // EX (SP), IX - 23 cycles
        aux = ix;
        ix = memory.readbyte(sp);
        ix |= memory.readbyte(sp+1) << 8;
        memory.writebyte(sp, aux & 0xFF);
        memory.writebyte(sp + 1, (aux >> 8) & 0xFF);
      	counter -= 23;
      	pc++;
      	break;

      case 0xE5:           // PUSH IX - 15 cycles
        push(ix);
        counter -= 15;
        pc++;
        break;
      
      case 0xE9:           // JP IX - 8 cycles
        pc = ix & 0xFFFF;
        counter -= 8;
      	break;
      
      case 0xF9:           // LD SP, IX - 10 states
        sp  = ix & 0xFFFF;
      	counter -= 10;
      	pc++;
      	break;

      default:             // Uninmplemented opcode
      	if(!msg.isVisible()) msg.setVisible(true);
        msg.println("PC = $" + Integer.toHexString(pc) + "  0xDD" + Integer.toHexString(opcode).toUpperCase() + ": Unknown opcode");
        //memory.dumpMemory();
        //vdp.dumpMemory();
        //System.exit(0);
       	pc++;
    }
    flagreg = f;
  }

  public final void exec_ED_opcode(int opcode)
  {
  	int aux = 0, f;
  	f = flagreg;
  	//this.opcode = (this.opcode << 8) | opcode;
  	
    switch(opcode)
    {
      case 0x40:           // IN B, (C) - 12 cycles
       	b = ports.read(c);
        	
       	if((b & 0x80) != 0) f |= FLAG_SIGN;
       	else f &= ~FLAG_SIGN;
       	
       	if(b == 0) f |= FLAG_ZERO;
       	else f &= ~FLAG_ZERO;
       	
       	if(parity[b]) f |= FLAG_PV;
       	else f &= ~FLAG_PV;
       	
       	f &= ~(FLAG_HCARRY | FLAG_NEG);
       	counter -= 12;
       	pc++;
       	break;
      
      case 0x41:           // OUT (C), B - 12 cycles
      	ports.write(c, b);
      	counter -= 12;
      	pc++;
      	break;

      case 0x42:           // SBC HL, BC - 15 cycles
      	aux = ((h << 8) | l) - ((b << 8) | c);
      	if((f & FLAG_CARRY) != 0) aux--;
      	
      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	if(aux < -32768) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	aux &= 0xFFFF;
      	
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((aux & 0x8000) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	h = (aux >> 8) & 0xFF;
      	l = aux & 0xFF;
      	
      	f |= FLAG_NEG;      	
      	counter -= 15;
      	pc++;
      	break;

      case 0x43:           // LD (NN), BC - 20 cycles
      	aux = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
        memory.writebyte(aux,   c & 0xFF);
        memory.writebyte(aux+1, b & 0xFF);
        counter -= 20;
        pc += 3;
        break;
        
      case 0x44:           // NEG - 8 cycles
      case 0x4C:           // *
      case 0x54:           // *
      case 0x5C:           // *
      case 0x64:           // *
      case 0x6C:           // *
      case 0x74:           // *
      case 0x7C:           // *
      	if(a != 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	if(a == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	a = (0 - a) & 0xFF;
      	
      	if((a & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
       	counter -= 8;
      	pc++;
      	break;
      	
      case 0x45:           // RETN - 10 cycles
      case 0x65:           // *
      	iff1 = iff2;
      	pc = pop();
      	counter -= 10;
      	break;
      	
      case 0x46:           // IM 0 - 8 cycles
      case 0x66:           // *
      	im = 0;
      	counter -= 8;
      	pc++;
      	break;
      	
      case 0x48:           // IN C, (C) - 12 cycles
       	c = ports.read(c);
        	
       	if((c & 0x80) != 0) f |= FLAG_SIGN;
       	else f &= ~FLAG_SIGN;
       	
       	if(c == 0) f |= FLAG_ZERO;
       	else f &= ~FLAG_ZERO;
       	
       	if(parity[c]) f |= FLAG_PV;
       	else f &= ~FLAG_PV;
       	
       	f &= ~(FLAG_HCARRY | FLAG_NEG);
       	counter -= 12;
       	pc++;
       	break;

      case 0x49:           // OUT (C), C - 12 cycles
      	ports.write(c, c);
      	counter -= 12;
      	pc++;
      	break;

      case 0x4A:           // ADC HL, BC - 15 cycles
      	aux = (h << 8) | l;
      	aux += (b << 8) | c;
      	if((f & FLAG_CARRY) != 0) aux++;
      	
        if(aux > 0xFFFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;

        if(aux > 0x7FFF) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        aux &= 0xFFFF;
        
        if(aux == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((aux & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        h = (aux >> 8) & 0xFF;
        l = aux & 0xFF;

        f &= ~FLAG_NEG;
        counter -= 15;
        pc++;
      	break;

      case 0x4B:           // LD BC, (nn) - 20 cycles
      	aux = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
      	b = memory.readbyte(aux + 1);
      	c = memory.readbyte(aux);
      	counter -= 20;
      	pc += 3;
      	break;
      	
      case 0x4D:           // RETI - 10 cycles
        pc = pop();
        iff1 = true;
        counter -= 10;
        break;

      case 0x50:           // IN D, (C) - 12 cycles
       	d = ports.read(c);
        	
       	if((d & 0x80) != 0) f |= FLAG_SIGN;
       	else f &= ~FLAG_SIGN;
       	
       	if(d == 0) f |= FLAG_ZERO;
       	else f &= ~FLAG_ZERO;
       	
       	if(parity[d]) f |= FLAG_PV;
       	else f &= ~FLAG_PV;
       	
       	f &= ~(FLAG_HCARRY | FLAG_NEG);
       	counter -= 12;
       	pc++;
       	break;

      case 0x51:           // OUT (C), D - 12 cycles
        ports.write(c, d);
        counter -= 12;
        pc++;
        break;
        
      case 0x52:           // SBC HL, DE - 15 cycles
      	aux = ((h << 8) | l) - ((d << 8) | e);
      	if((f & FLAG_CARRY) != 0) aux--;
      	
      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	if(aux < -0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	aux &= 0xFFFF;
      	
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if((aux & 0x8000) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	h = (aux >> 8) & 0xFF;
      	l = aux & 0xFF;
      	
      	f |= FLAG_NEG;
      	
      	counter -= 15;
      	pc++;
      	break;

      case 0x53:           // LD (NN), DE - 20 cycles
      	aux = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
        memory.writebyte(aux,   e);
        memory.writebyte(aux+1, d);
        counter -= 20;
        pc += 3;
        break;
        
      case 0x56:           // IM 1 - 8 cycles
        im = 1;
        counter -= 8;
        pc++;
        break;
      
      case 0x58:           // IN E, (C) - 12 cycles
       	e = ports.read(c);
        	
       	if((e & 0x80) != 0) f |= FLAG_SIGN;
       	else f &= ~FLAG_SIGN;
       	
       	if(e == 0) f |= FLAG_ZERO;
       	else f &= ~FLAG_ZERO;
       	
       	if(parity[e]) f |= FLAG_PV;
       	else f &= ~FLAG_PV;
       	
       	f &= ~(FLAG_HCARRY | FLAG_NEG);
       	counter -= 12;
       	pc++;
       	break;

      case 0x59:           // OUT (C), E - 12 cycles
        ports.write(c, e);
        counter -= 12;
        pc++;
        break;
        
      case 0x5A:           // ADC HL, DE - 15 cycles
      	aux = (h << 8) | l;
      	aux += (d << 8) | e;
      	if((f & FLAG_CARRY) != 0) aux++;
      	
        if(aux > 0xFFFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if(aux > 0x7FFF) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        aux &= 0xFFFF;
        
        if(aux == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((aux & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        h = (aux >> 8) & 0xFF;
        l = aux & 0xFF;

        f &= ~FLAG_NEG;
        counter -= 15;
        pc++;
      	break;

      case 0x5B:           // LD DE, (nn) - 20 cycles
      	aux = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
      	d = memory.readbyte(aux + 1);
      	e = memory.readbyte(aux);
      	counter -= 20;
      	pc += 3;
      	break;

      case 0x5F:           // LD A, R - 9 cycles
      	a = r & 0xFF;

      	if((a & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;

      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(iff2) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 9;
      	pc++;      	
      	break;
      
      case 0x60:           // IN H, (C) - 12 cycles
       	h = ports.read(c);
        	
       	if((h & 0x80) != 0) f |= FLAG_SIGN;
       	else f &= ~FLAG_SIGN;
       	
       	if(h == 0) f |= FLAG_ZERO;
       	else f &= ~FLAG_ZERO;
       	
       	if(parity[h]) f |= FLAG_PV;
       	else f &= ~FLAG_PV;
       	
       	f &= ~(FLAG_HCARRY | FLAG_NEG);
       	counter -= 12;
       	pc++;
       	break;

      case 0x61:           // OUT (C), H - 12 cycles
        ports.write(c, h);
        counter -= 12;
        pc++;
        break;
        
      case 0x67:           // RRD - 18 cycles
      	aux = memory.readbyte((h << 8) | l);
      	aux |= ((a & 0xF) << 8);
      	a &= 0xF0;
      	a |= aux & 0xF;
      	aux = aux >> 4;
      	aux &= 0xFF;
      	memory.writebyte((h << 8) | l, aux);
      	
      	if((a &= 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[a & 0xFF]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_NEG | FLAG_HCARRY);
      	counter -= 18;
      	pc++;
      	break;
      
      case 0x68:           // IN L, (C) - 12 cycles
       	l = ports.read(c);
        	
       	if((l & 0x80) != 0) f |= FLAG_SIGN;
       	else f &= ~FLAG_SIGN;
       	
       	if(l == 0) f |= FLAG_ZERO;
       	else f &= ~FLAG_ZERO;
       	
       	if(parity[l]) f |= FLAG_PV;
       	else f &= ~FLAG_PV;
       	
       	f &= ~(FLAG_HCARRY | FLAG_NEG);
       	counter -= 12;
       	pc++;
       	break;

      case 0x69:           // OUT (C), L - 12 cycles
        ports.write(c, l);
        counter -= 12;
        pc++;
        break;
        
      case 0x6A:           // ADC HL, HL - 15 cycles
      	aux = (h << 8) | l;
      	aux = aux << 1;
      	if((f & FLAG_CARRY) != 0) aux++;
      	
        if(aux > 0xFFFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;

        if(aux > 0x7FFF) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        aux &= 0xFFFF;
        
        if(aux == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((aux & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        h = (aux >> 8) & 0xFF;
        l = aux & 0xFF;

        f &= ~FLAG_NEG;
        counter -= 15;
        pc++;
      	break;
      
      case 0x6F:           // RLD - 18 cycles
      	aux = memory.readbyte((h << 8) | l);
      	aux = (aux & 0xFF) << 4;
      	aux |= (a & 0xF);
      	a &= 0xF0;
      	a |= (aux >> 8) & 0xF;
      	memory.writebyte((h << 8) | l, aux);
      	
      	if((a &= 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[a & 0xFF]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_NEG | FLAG_HCARRY);
      	counter -= 18;
        pc++;
      	break;

      case 0x71:           // OUT (C), 0 - 12 cycles ***
      	ports.write(c, 0);
      	counter -= 12;
      	pc++;
      	break;
	
      case 0x73:           // LD (NN), SP - 20 cycles
      	aux = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
        memory.writebyte(aux  , (sp & 0xFF));
        memory.writebyte(aux+1, ((sp >> 8) & 0xFF));
        counter -= 20;
        pc += 3;
        break;
        
      case 0x78:           // IN A, (C) - 12 cycles
      	a = ports.read(c);
      	
      	if((a & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	if(parity[a & 0xFF]) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	counter -= 12;
      	pc++;
      	break;
      
      case 0x79:           // OUT (C), A - 12 cycles
        ports.write(c, a);
        counter -= 12;
        pc++;
        break;
        
      case 0x7B:           // LD SP, (nn) - 10 cycles
      	aux = ((memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1));
        sp = memory.readbyte(aux+1) << 8;
        sp |= memory.readbyte(aux);
        counter -= 10;
        pc += 3;
        break;

      case 0xA0:           // LDI - 16 cycles
      	memory.writebyte((d << 8) | e, memory.readbyte((h << 8) | l));

      	e++;                          // increment DE
      	if(e > 0xFF) {
      		e ^= e;
      		d = (d + 1) & 0xFF;
      	}      	
      	l++;                          // increment HL
      	if(l > 0xFF) {
      		l ^= l;
      		h = (h + 1) & 0xFF;
      	}      	
      	c--;                          // decrement BC
      	if(c < 0) {
      		c = 0xFF;
      		b = (b - 1) & 0xFF;
      	}
      	if((b != 0) || (c != 0)) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_NEG | FLAG_HCARRY);
      	
      	counter -= 16;
      	pc++;
      	break;

      case 0xA1:           // CPI - 16 cycles
      	if((a & 0xF) < (memory.readbyte((h << 8) | l) & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	aux = (a - memory.readbyte((h << 8) | l)) & 0xFF;

      	if((aux & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	l++;              // increment HL
      	if(l > 0xFF) {
      		l ^= l;
      		h = (h + 1) & 0xFF;
      	}
      	c--;              // decrement BC
      	if(c < 0) {
      		c = 0xFF;
      		b = (b - 1) & 0xFF;
      	}

      	if((b != 0) || (c != 0)) f |= FLAG_PV; // flag set if BC-1 != 0
      	else f &= ~FLAG_PV;
      	
      	f |= FLAG_NEG;
      	
        counter -= 16;
        pc++;
        break;

      case 0xA2:           // INI - 16 cycles
      	memory.writebyte((h << 8) | l, ports.read(c));
      	l++;              // increment HL
      	if(l > 0xFF) {
      		l ^= l;
      		h = (h + 1) & 0xFF;
      	}
      	b = (b - 1) & 0xFF;
      	if(b == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	pc++;
      	break;
		
      case 0xA3:           // OUTI - 16 cycles
      	ports.write(c, memory.readbyte((h << 8) | l));

      	l++;              // increment HL
      	if(l > 0xFF) {
      		l ^= l;
      		h = (h + 1) & 0xFF;
      	}
        b = (b - 1) & 0xFF;
        if(b == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        f |= FLAG_NEG;
        counter -= 16;
        pc++;
        break;

      case 0xA8:           // LDD - 16 cycles
      	memory.writebyte((d << 8) | e, memory.readbyte((h << 8) | l));

      	e--;                          // decrement DE
      	if(e < 0) {
      		e = 0xFF;
      		d = (d - 1) & 0xFF;
      	}      	
      	l--;                          // decrement HL
      	if(l < 0) {
      		l = 0xFF;
      		h = (h - 1) & 0xFF;
      	}      	
      	c--;                          // decrement BC
      	if(c < 0) {
      		c = 0xFF;
      		b = (b - 1) & 0xFF;
      	}
      	if((b != 0) || (c != 0)) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f &= ~(FLAG_NEG | FLAG_HCARRY);
      	
      	counter -= 16;
      	pc++;
      	break;

      case 0xA9:           // CPD - 16 cycles
      	aux = (h << 8) | l;
      	if((a & 0xF) < (memory.readbyte(aux) & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	aux = (a - memory.readbyte(aux)) & 0xFF;

      	if((aux & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	aux = (h << 8) | l;
      	aux = (aux - 1) & 0xFFFF;
      	h = (aux >> 8) & 0xFF;
      	l = aux & 0xFF;
      	
      	aux = (b << 8) | c;
      	aux = (aux - 1) & 0xFFFF;
      	b = (aux >> 8) & 0xFF;
      	c = aux & 0xFF;

      	if(aux != 0) f |= FLAG_PV; // flag set if BC-1 != 0
      	else f &= ~FLAG_PV;
      	
      	f |= FLAG_NEG;
      	
        counter -= 16;
        pc++;
        break;

      case 0xAB:           // OUTD - 16 cycles
      	aux = (h << 8) | l;
      	ports.write(c, memory.readbyte(aux));

      	aux = (aux - 1) & 0xFFFF; // decrement HL
      	h = (aux >> 8) & 0xFF;
      	l = (aux & 0xFF);

        b = (b - 1) & 0xFF;
        if(b == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        f |= FLAG_NEG;
        counter -= 16;
        pc++;
        break;

      case 0xB0:           // LDIR - 21/16 cycles
      	//System.out.println("LDIR: BC=" + Integer.toHexString((b << 8) | c).toUpperCase() + " DE=" + Integer.toHexString((d << 8) | e).toUpperCase() + " HL=" + Integer.toHexString((h << 8) | l).toUpperCase() + " (DE)=" + memory.readbyte((d << 8) | e) + " (HL)=" + memory.readbyte(((h << 8) | l)));
      	memory.writebyte((d << 8) | e, memory.readbyte((h << 8) | l));

      	e++;                          // increment DE
      	if(e > 0xFF) {
      		e ^= e;
      		d = (d + 1) & 0xFF;
      	}      	
      	l++;                          // increment HL
      	if(l > 0xFF) {
      		l ^= l;
      		h = (h + 1) & 0xFF;
      	}      	
      	c--;                          // decrement BC
      	if(c < 0) {
      		c = 0xFF;
      		b = (b - 1) & 0xFF;
      	}
      	if((b == 0) && (c == 0)) { 
        	f &= ~FLAG_PV;
        	counter -= 16;
        	pc++;
      	}
        else {
        	f |= FLAG_PV;
            counter -= 21;
        	pc--;
        }
        
        f &= ~(FLAG_HCARRY | FLAG_NEG);
        
      	//System.out.println("      BC=" + Integer.toHexString((b << 8) | c).toUpperCase() + " DE=" + Integer.toHexString((d << 8) | e).toUpperCase() + " HL=" + Integer.toHexString((h << 8) | l).toUpperCase() + " (DE)=" + memory.readbyte(((d << 8) | e) - 1) + " (HL)=" + memory.readbyte(((h << 8) | l) - 1));
        break;
      
      case 0xB1:           // CPIR - 21/16 cycles
      	aux = (h << 8) | l;
      	if((a & 0xF) < (memory.readbyte(aux) & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	aux = (a - memory.readbyte(aux)) & 0xFF;

      	if((aux & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	aux = (h << 8) | l;
      	aux = (aux + 1) & 0xFFFF;
      	h = (aux >> 8) & 0xFF;
      	l = aux & 0xFF;
      	aux = (b << 8) | c;
      	aux--;
      	aux &= 0xFFFF;
      	b = (aux >> 8) & 0xFF;
      	c = aux & 0xFF;

      	if(aux != 0) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f |= FLAG_NEG;
      	
      	if(((f & FLAG_ZERO) != 0) || ((f & FLAG_PV) == 0)) {
      		counter -= 16;
      		pc++;
      	}
      	else {
      		counter -= 21;
      		pc--;
      	}
        break;
        
      case 0xB3:           // OTIR - 21/16 cycles
      	aux = (h << 8) | l;
      	ports.write(c, memory.readbyte(aux));
        aux = (aux + 1) & 0xFFFF;
        h = (aux >> 8) & 0xFF;
        l = aux & 0xFF;

        f |= FLAG_NEG;

        b = (b - 1) & 0xFF;
        if(b == 0) {
        	f |= FLAG_ZERO;
            counter -= 16;
        	pc++;
        }
        else {
        	f &= ~FLAG_ZERO;
            counter -= 21;
        	pc--;
        }
        break;
      
      case 0xB8:           // LDDR - 21/16 cycles
      	memory.writebyte((d << 8) | e, memory.readbyte((h << 8) | l));

      	e--;                          // decrement DE
      	if(e < 0) {
      		e = 0xFF;
      		d = (d - 1) & 0xFF;
      	}      	
      	l--;                          // decrement HL
      	if(l < 0) {
      		l = 0xFF;
      		h = (h - 1) & 0xFF;
      	}      	
      	c--;                          // decrement BC
      	if(c < 0) {
      		c = 0xFF;
      		b = (b - 1) & 0xFF;
      	}
      	if((b != 0) || (c != 0)) { 
        	f |= FLAG_PV;
        	counter -= 21;
        	pc--;
      	}
        else {
        	f &= ~FLAG_PV;
            counter -= 16;
        	pc++;
        }
      	
      	f &= ~(FLAG_HCARRY | FLAG_NEG);
      	break;

      case 0xB9:           // CPDR - 21/16 cycles
      	aux = (h << 8) | l;
      	if((a & 0xF) < (memory.readbyte(aux) & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	aux = (a - memory.readbyte(aux)) & 0xFF;

      	if((aux & 0x80) != 0) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	aux = (h << 8) | l;
      	aux = (aux - 1) & 0xFFFF;
      	h = (aux >> 8) & 0xFF;
      	l = aux & 0xFF;
      	aux = (b << 8) | c;
      	aux = (aux - 1) & 0xFFFF;
      	b = (aux >> 8) & 0xFF;
      	c = aux & 0xFF;

      	if(aux != 0) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	f |= FLAG_NEG;
      	
      	if(((f & FLAG_ZERO) != 0) || ((f & FLAG_PV) == 0)) {
      		counter -= 16;
      		pc++;
      	}
      	else {
      		counter -= 21;
      		pc--;
      	}
        break;
        
      case 0xBB:           // OTDR - 21/16 cycles
      	aux = (h << 8) | l;
      	ports.write(c, memory.readbyte(aux));
        aux = (aux - 1) & 0xFFFF;
        h = (aux >> 8) & 0xFF;
        l = aux & 0xFF;

        f |= FLAG_NEG;

        b = (b - 1) & 0xFF;
        if(b == 0) {
        	f |= FLAG_ZERO;
            counter -= 16;
        	pc++;
        }
        else {
        	f &= ~FLAG_ZERO;
            counter -= 21;
        	pc--;
        }
      	break;
        
      default:             // Uninmplemented opcode
      	if(!msg.isVisible()) msg.setVisible(true);
        msg.println("PC = $" + Integer.toHexString(pc) + "  0xED" + Integer.toHexString(opcode).toUpperCase() + ": Unknown opcode");
        //memory.dumpMemory();
        //vdp.dumpMemory();
        //System.exit(0);
    }
    flagreg = f;
  }

  public final void exec_FD_opcode(int opcode)
  {
  	int aux = 0, f;
  	f = flagreg;
  	
    switch(opcode)
    {
      case 0x09:           // ADD IY, BC - 15 cycles
      	aux = iy & 0xFFF;
      	aux += ((b << 8) | c) & 0xFFF;
      	if(aux > 0xFFF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	iy += ((b << 8) | c);
      	
        if(iy > 0xFFFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if(iy > 0x7FFF) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        if((iy & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        iy &= 0xFFFF;
        
        if(iy == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        f &= ~FLAG_NEG;
        counter -= 15;
        pc++;
       	break;
          	
      case 0x19:           // ADD IY, DE - 15 cycles
      	aux = iy & 0xFFF;
      	aux += ((d << 8) | e) & 0xFFF;
      	if(aux > 0xFFF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	iy += ((d << 8) | e);
      	
        if(iy > 0xFFFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if(iy > 0x7FFF) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        if((iy & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        iy &= 0xFFFF;
        
        if(iy == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        f &= ~FLAG_NEG;
        counter -= 15;
        pc++;
      	break;
      	
      case 0x21:           // LD IY, nn - 14 cycles
        iy = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
        counter -= 14;
        pc += 3;
        break;
      
      case 0x22:           // LD (nn), IY - 20 cycles
      	aux = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
      	memory.writebyte(aux, (iy & 0xFF));
      	memory.writebyte(aux+1, ((iy >> 8) & 0xFF));
      	
      	counter -= 20;
      	pc += 3;
      	break;

      case 0x23:           // INC IY - 10 cycles
      	iy = (iy + 1) & 0xFFFF;
		counter -= 10;
		pc++;
      	break;
     
      case 0x24:           // INC IYH - 4 cycles *
      	aux = (iy >> 8) & 0xFF;
        if((aux & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        aux = ((aux + 1) & 0xFF);
        iy &= 0xFF;
        iy |= aux << 8;

        if(aux == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(aux == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((aux & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }
        }

        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x25:           // DEC IYH - 4 cycles *
      	aux = (iy >> 8) & 0xFF;
      	if(aux == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
        aux = ((aux - 1) & 0xFF);
        iy &= 0xFF;
        iy |= aux << 8;

        if((aux & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        if(aux==0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((aux & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        f |= FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x26:           // LD IYH, n - 7 cycles *
      	aux = memory.readbyte(pc+1) << 8;
      	iy &= 0xFF;
      	iy |= aux;
      	
      	counter -= 7;
      	pc += 2;
      	break;
      	
      case 0x29:           // ADD IY, IY - 15 cycles
      	aux = iy & 0xFFF;
      	aux <<= 1;
      	if(aux > 0xFFF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	iy <<= 1;
      	
        if(iy > 0xFFFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if(iy > 0x7FFF) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
        if((iy & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        iy &= 0xFFFF;
        
        if(iy == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        f &= ~FLAG_NEG;
        counter -= 15;
        pc++;
      	break;
     
      case 0x2A:           // LD IY, (nn) - 20 cycles
      	aux = (memory.readbyte(pc+2) << 8) | memory.readbyte(pc+1);
      	iy = memory.readbyte(aux + 1) << 8;
      	iy |= memory.readbyte(aux);
      	
      	counter -= 20;
      	pc += 3;
      	break;
      
      case 0x2B:           // DEC IY - 10 cycles
      	iy--;
      	iy &= 0xFFFF;
      	
      	counter -= 10;
      	pc++;
      	break;
      	
      case 0x2C:           // INC IYL - 4 cycles *
      	aux = iy & 0xFF;
        if((aux & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        aux = ((aux + 1) & 0xFF);
        iy &= 0xFF00;
        iy |= aux;

        if(aux == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(aux == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((aux & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }
        }

        f &= ~FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x2D:           // DEC IYL - 4 cycles *
      	aux = iy & 0xFF;
      	if(aux == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
        aux = ((aux - 1) & 0xFF);
        iy &= 0xFF00;
        iy |= aux;

        if((aux & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

        if(aux==0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((aux & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        f |= FLAG_NEG;
        counter -= 4;
        pc++;
        break;

      case 0x2E:           // LD IYL, n - 7 cycles ***
      	aux = memory.readbyte(pc+1);
      	iy &= 0xFF00;
      	iy |= aux;
      	
      	counter -= 7;
      	pc += 2;
      	break;
      
      case 0x34:           // INC (IY+d) - 23 cycles
      	aux = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);

        if((aux & 0xF)+1 > 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

      	aux = (aux + 1) & 0xFF;
      	memory.writebyte((iy + memory.readsigned(pc+1)) & 0xFFFF, aux);
      	
        if(aux == 0) { f |= FLAG_ZERO; f &= ~(FLAG_SIGN | FLAG_PV); }
        else {
        	f &= ~FLAG_ZERO;

        	if(aux == 0x80) f |= FLAG_PV | FLAG_SIGN;
            else {
            	f &= ~FLAG_PV;
            	
                if((aux & 0x80) != 0) f |= FLAG_SIGN;
                else f &= ~FLAG_SIGN;
            }
        }

        f &= ~FLAG_NEG;
      	counter -= 23;
      	pc += 2;
      	break;
      	
      case 0x35:           // DEC (IY+d) - 23 cycles
      	aux = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);

      	if(aux == 0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;

      	aux = (aux - 1) & 0xFF;

      	if((aux & 0xF) == 0xF) f |= FLAG_HCARRY;
        else f &= ~FLAG_HCARRY;

      	memory.writebyte((iy + memory.readsigned(pc+1)) & 0xFFFF, aux);
      	
        if(aux == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((aux & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        f |= FLAG_NEG;
      	counter -= 23;
        pc += 2;
      	break;
      	
      case 0x36:           // LD (IY+d), n - 19 cycles
        memory.writebyte((iy + memory.readsigned(pc+1)) & 0xFFFF, memory.readbyte(pc+2));
        counter -= 19;
        pc += 3;
        break;
        
      case 0x39:           // ADD IY, SP - 15 cycles
      	aux = (iy & 0xFFF) + (sp & 0xFFF);
      	if(aux > 0xFFF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	iy += sp;
      	if(iy > 0xFFFF) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;

        if(iy > 0x7FFF) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        if((iy & 0x8000) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        
        iy &= 0xFFFF;
        
        if(iy == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
      	
      	f &= ~FLAG_NEG;
       	counter -= 15;
      	pc++;
      	break;
      	
      case 0x46:           // LD B, (IY+d) - 19 cycles
        b = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;
        
      case 0x4E:           // LD C, (IY+d) - 19 cycles
        c = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;
      
      case 0x56:           // LD D, (IY+d) - 19 cycles
        d = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;

      case 0x5E:           // LD E, (IY+d) - 19 cycles
        e = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;
        
      case 0x66:           // LD H, (IY+d) - 19 cycles
        h = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;
        
      case 0x67:           // LD IYH, A - 7 cycles *
      	iy &= 0xFF;
      	iy |= a << 8;
      	
      	counter -= 7;
      	pc++;
      	break;

      case 0x6E:           // LD L, (IY+d) - 19 cycles
        l = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;
      
      case 0x6F:           // LD IYL, A - 7 cycles *
      	iy &= 0xFF00;
      	iy |= (a & 0xFF);
      	counter -= 7;
      	pc++;
      	break;

      case 0x70:           // LD (IY+d), B - 19 cycles
        memory.writebyte((iy + memory.readsigned(pc+1)) & 0xFFFF, b & 0xFF);
        counter -= 19;
        pc += 2;
        break;

      case 0x71:           // LD (IY+d), C - 19 cycles
        memory.writebyte((iy + memory.readsigned(pc+1)) & 0xFFFF, c & 0xFF);
        counter -= 19;
        pc += 2;
        break;

      case 0x72:           // LD (IY+d), D - 19 cycles
        memory.writebyte((iy + memory.readsigned(pc+1)) & 0xFFFF, d & 0xFF);
        counter -= 19;
        pc += 2;
        break;

      case 0x73:           // LD (IY+d), E - 19 cycles
        memory.writebyte((iy + memory.readsigned(pc+1)) & 0xFFFF, e & 0xFF);
        counter -= 19;
        pc += 2;
        break;

      case 0x74:           // LD (IY+d), H - 19 cycles
        memory.writebyte((iy + memory.readsigned(pc+1)) & 0xFFFF, h & 0xFF);
        counter -= 19;
        pc += 2;
        break;

      case 0x75:           // LD (IY+d), L - 19 cycles
        memory.writebyte((iy + memory.readsigned(pc+1)) & 0xFFFF, l & 0xFF);
        counter -= 19;
        pc += 2;
        break;

      case 0x77:           // LD (IY+d), A - 19 cycles
        memory.writebyte((iy + memory.readsigned(pc+1)) & 0xFFFF, a & 0xFF);
        counter -= 19;
        pc += 2;
        break;
      
      case 0x7C:           // LD A, IYH - 7 cycles *
      	a = (iy >> 8) & 0xFF;
      	counter -= 7;
      	pc++;
      	break;
      	
      case 0x7D:           // LD A, IYL - 7 cycles *
      	a = iy & 0xFF;
      	counter -= 7;
      	pc++;
      	break;

      case 0x7E:           // LD A, (IY+d) - 19 cycles
      	a = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);
        counter -= 19;
        pc+=2;
        break;
        
      case 0x86:           // ADD A, (IY+d) - 16 cycles
        aux = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);

      	if((a & 0xF) + (aux & 0xF) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	a += aux;
        
        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        a = (a & 0xFF);

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        f &= ~FLAG_NEG;
        counter -= 16;
        pc += 2;
        break;
      
      case 0x8E:           // ADC A, (IY+d) - 19 cycles
        aux = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);

      	if(((a & 0xF) + (aux & 0xF) + (((f & FLAG_CARRY) != 0) ? 1 : 0)) > 0xF) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;

      	a += aux;
        if((f & FLAG_CARRY) != 0) a++;

        if(a > 0xFF) f |= FLAG_CARRY;
        else f &= ~FLAG_CARRY;
        
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        a &= 0xFF;
        
        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;
        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;
        f &= ~FLAG_NEG;
        counter -= 19;
        pc += 2;
      	break;
      
      case 0x96:           // SUB (IY+d) - 19 cycles
      	aux = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);
      	
      	if((a & 0xF) < (aux & 0xF)) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	a = (a & 0xFF) - aux;
      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((a > 0x7F) || a < -0x80) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	a &= 0xFF;
      	
      	if((a & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 19;
      	pc += 2;
      	break;
      	
      case 0x9E:           // SBC A, (IY+d) - 19 cycles
      	aux = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);

      	if((a & 0xF) < ((aux & 0xF) - ((f & FLAG_CARRY) != 0 ? 1 : 0))) f |= FLAG_HCARRY;
      	else f &= ~FLAG_HCARRY;
      	
      	a -= aux;
      	if((f & FLAG_CARRY) != 0) a--;
      	
      	if(a < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
      	if(a < -128) f |= FLAG_PV;
      	else f &= ~FLAG_PV;
      	
      	a &= 0xFF;
      	
      	if((a & 0x80) != 0) f|= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	
      	if(a == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	      	
      	f |= FLAG_NEG;
      	counter -= 19;
      	pc += 2;
      	break;
      	
      case 0xA6:           // AND (IY+d) - 19 cycles
        a &= memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if(parity[a & 0xFF] == true) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_NEG | FLAG_CARRY);
        f |= FLAG_HCARRY;
        counter -= 19;
        pc += 2;
      	break;
      	
      case 0xAE:           // XOR (IY+d) - 19 cycles
        a ^= memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);
        a &= 0xFF;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
      	counter -= 19;
      	pc += 2;
      	break;
     
      case 0xB5:           // OR IYL - 7 cycles *
        a |= iy & 0xFF;
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
      	counter -= 7;
      	pc++;
      	break;
      	
      case 0xB6:           // OR (IY+d) - 19 cycles
        a |= memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);
        a &= 0xFF;

        if(a == 0) f |= FLAG_ZERO;
        else f &= ~FLAG_ZERO;

        if((a & 0x80) != 0) f |= FLAG_SIGN;
        else f &= ~FLAG_SIGN;

        if(parity[a & 0xFF]) f |= FLAG_PV;
        else f &= ~FLAG_PV;

        f &= ~(FLAG_CARRY | FLAG_NEG | FLAG_HCARRY);
        counter -= 19;
        pc += 2;
        break;

      case 0xBE:           // CP (IY+d) - 19 cycles
      	aux = memory.readbyte((iy + memory.readsigned(pc+1)) & 0xFFFF);
      	aux = (a & 0xFF) - (aux & 0xFF);

      	if(aux < 0) f |= FLAG_CARRY;
      	else f &= ~FLAG_CARRY;
      	
        if((aux > 0x7F) || aux < -0x80) f |= FLAG_PV;
        else f &= ~FLAG_PV;
        
      	aux = aux & 0xFF;
      	
      	if((aux & 0x80) == 0x80) f |= FLAG_SIGN;
      	else f &= ~FLAG_SIGN;
      	if(aux == 0) f |= FLAG_ZERO;
      	else f &= ~FLAG_ZERO;
      	
      	f |= FLAG_NEG;
      	counter -= 19;
      	pc += 2;
      	break;
      
      case 0xCB:           // DD CB prefixed opcodes
      	pc += 2;
      	flagreg = f;
      	exec_FDCB_opcode(memory.readbyte(pc));
      	f = flagreg;
      	break;

      case 0xE1:           // POP IY - 14 cycles
        iy = pop();
        counter -= 14;
        pc++;
        break;
        
      case 0xE3:           // EX (SP), IY - 23 cycles
        aux = iy;
        iy = memory.readbyte(sp);
        iy |= memory.readbyte(sp+1) << 8;
        memory.writebyte(sp, aux & 0xFF);
        memory.writebyte(sp + 1, (aux >> 8) & 0xFF);
      	counter -= 23;
      	pc++;
      	break;

      case 0xE5:           // PUSH IY - 15 cycles
        push(iy);
        counter -= 15;
        pc++;
        break;
      
      case 0xE9:           // JP IY - 8 cycles
        pc = iy & 0xFFFF;
        counter -= 8;
      	break;
      
      case 0xF9:           // LD SP, IY - 10 states
        sp  = iy & 0xFFFF;
      	counter -= 10;
      	pc++;
      	break;

      default:             // Uninmplemented opcode
      	if(!msg.isVisible()) msg.setVisible(true);
        msg.println("PC = $" + Integer.toHexString(pc) + "  0xFD" + Integer.toHexString(opcode).toUpperCase() + ": Unknown opcode");
        //memory.dumpMemory();
        //vdp.dumpMemory();
        //System.exit(0);
       	pc++;
    }
    flagreg = f;
  }

  
  public final void exec_DDCB_opcode(int opcode)
  {
  	int aux = 0, f;
  	f = flagreg;
  	
    switch(opcode)
    {
    	case 0x06:           // RLC (IX+d) - 23 cycles
    		aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	aux = aux << 1;
          	if((aux & 0x100) != 0) {
          		aux |= 1;
          		f |= FLAG_CARRY;
          	}
          	else {
          		aux &= ~0x1;
          		f &= ~FLAG_CARRY;
          	}
          	aux &= 0xFF;
          	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;
          	
          	if((aux & 0x80) != 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 15;
          	pc++;
          	break;

        case 0x0E:           // RRC (IX+d) - 23 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);

          	if((aux & 0x1) != 0) {
          		aux |= 0x100;
          		f |= FLAG_CARRY | FLAG_SIGN;
          	}
          	else {
          		f &= ~(FLAG_CARRY | FLAG_SIGN);
          	}
          	aux = (aux >> 1) & 0xFF;
          	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;
          	
          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 23;
            pc++;
          	break;

        case 0x16:           // RL (IX+d) - 23 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	aux = aux << 1;
          	
          	if((f & FLAG_CARRY) != 0) aux |= 1;
          	else aux &= ~0x1;
          	
          	if((aux & 0x100) != 0) f |= FLAG_CARRY;
          	else f &= ~FLAG_CARRY;
          	
          	aux &= 0xFF;
          	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
          	if((aux & 0x80) != 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;
          	
          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 23;
          	pc++;
          	break;

        case 0x1E:           // RR (IX+d) - 23 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
            if((f & FLAG_CARRY) != 0) aux |= 0x100;
            else aux &= ~0x100;      	

            if((aux & 0x1) != 0) f |= FLAG_CARRY;
          	else f &= ~FLAG_CARRY;
          	
            aux = (aux >> 1) & 0xFF;
            memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
          	if((aux & 0x80) != 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;
          	
          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 23;
          	pc++;
          	break;

        case 0x26:           // SLA (IX+d) - 23 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	
          	if((aux & 0x80) != 0) f |= FLAG_CARRY;
          	else f &= ~FLAG_CARRY;
          	
          	aux = (aux << 1) & 0xFF;
          	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	if((aux & 0x80) != 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;

          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 23;
          	pc++;      		
          	break;
          
        case 0x2E:           // SRA (IX+d) - 23 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x1) != 0) f |= FLAG_CARRY;
          	else f &= ~FLAG_CARRY;
          	
          	aux = (aux >> 1) & 0xFF;
          	
          	if((aux & 0x40) != 0) aux |= 0x80;
          	else aux &= ~0x80;

          	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);

          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	if((aux & 0x80) != 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;

          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 23;
          	pc++;      		
          	break;
         
        case 0x36:           // SLL (IX+d) - 23 cycles *
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	
          	if((aux & 0x80) != 0) f |= FLAG_CARRY;
          	else f &= ~FLAG_CARRY;
          	
          	aux = (aux << 1) & 0xFE;
          	aux |= 1;
          	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	if((aux & 0x80) != 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;

          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 23;
          	pc++;      		
          	break;
          
        case 0x3E:           // SRL (IX+d) - 23 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	
          	if((aux & 0x1) != 0) f |= FLAG_CARRY;
          	else f &= ~FLAG_CARRY;
          	aux = (aux >> 1) & 0x7F;
          	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
            if(aux == 0) f |= FLAG_ZERO;
            else f &= ~FLAG_ZERO;
            
            if(parity[aux]) f |= FLAG_PV;
            else f &= ~FLAG_PV;
            
            f &= ~(FLAG_SIGN | FLAG_HCARRY | FLAG_NEG);
            counter -= 23;
            pc++;
          	break;
          
    	case 0x46:           // BIT 0,(IX+d) - 20 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x01) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;

        case 0x4E:           // BIT 1,(IX+d) - 20 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x02) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
        	break;

        case 0x56:           // BIT 2,(IX+d) - 20 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x04) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;
        
        case 0x5E:           // BIT 3,(IX+d) - 20 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x08) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;
        
        case 0x66:           // BIT 4,(IX+d) - 20 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x10) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;

        case 0x6E:           // BIT 5,(IX+d) - 20 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x20) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;

        case 0x76:           // BIT 6,(IX+d) - 20 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x40) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;

        case 0x7E:           // BIT 7,(IX+d) - 20 cycles
          	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x80) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	if((f & FLAG_ZERO) == 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;

        case 0x86:         // RES 0, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x01;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0x8E:         // RES 1, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x02;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0x96:         // RES 2, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x04;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0x9E:         // RES 3, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x08;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xA6:         // RES 4, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x10;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xAE:         // RES 5, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x20;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xB6:         // RES 6, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x40;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xBE:         // RES 7, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x80;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xC6:         // SET 0, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x01;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xCE:         // SET 1, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x02;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xD6:         // SET 2, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x04;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xDE:         // SET 3, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x08;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xE6:         // SET 4, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x10;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xEE:         // SET 5, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x20;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xF6:         // SET 6, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x40;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xFE:         // SET 7, (IX+d) - 23 cycles
        	aux = memory.readbyte((ix + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x80;
        	memory.writebyte((ix + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

      default:             // Uninmplemented opcode
        msg.println("PC = $" + Integer.toHexString(pc - 3) + "  0xDDCB nn " + Integer.toHexString(opcode).toUpperCase() + ": Unknown opcode");
        //memory.dumpMemory();
        //vdp.dumpMemory();
        //System.exit(0);
        pc++;
    }
    flagreg = f;
  }
  
  public final void exec_FDCB_opcode(int opcode)
  {
  	int aux = 0, f;
  	f = flagreg;
  	
    switch(opcode)
    {
    	case 0x06:           // RLC (IY+d) - 23 cycles
    		aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	aux = aux << 1;
          	if((aux & 0x100) != 0) {
          		aux |= 1;
          		f |= FLAG_CARRY;
          	}
          	else {
          		aux &= ~0x1;
          		f &= ~FLAG_CARRY;
          	}
          	aux &= 0xFF;
          	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;
          	
          	if((aux & 0x80) != 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 15;
          	pc++;
          	break;

        case 0x0E:           // RRC (IY+d) - 23 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);

          	if((aux & 0x1) != 0) {
          		aux |= 0x100;
          		f |= FLAG_CARRY | FLAG_SIGN;
          	}
          	else {
          		f &= ~(FLAG_CARRY | FLAG_SIGN);
          	}
          	aux = (aux >> 1) & 0xFF;
          	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;
          	
          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 23;
            pc++;
          	break;

        case 0x16:           // RL (IY+d) - 23 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	aux = aux << 1;
          	
          	if((f & FLAG_CARRY) != 0) aux |= 1;
          	else aux &= ~0x1;
          	
          	if((aux & 0x100) != 0) f |= FLAG_CARRY;
          	else f &= ~FLAG_CARRY;
          	
          	aux &= 0xFF;
          	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
          	if((aux & 0x80) != 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;
          	
          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 23;
          	pc++;
          	break;

        case 0x1E:           // RR (IY+d) - 23 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
            if((f & FLAG_CARRY) != 0) aux |= 0x100;
            else aux &= ~0x100;      	

            if((aux & 0x1) != 0) f |= FLAG_CARRY;
          	else f &= ~FLAG_CARRY;
          	
            aux = (aux >> 1) & 0xFF;
            memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
          	if((aux & 0x80) != 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;
          	
          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 23;
          	pc++;
          	break;

        case 0x26:           // SLA (IY+d) - 23 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	
          	if((aux & 0x80) != 0) f |= FLAG_CARRY;
          	else f &= ~FLAG_CARRY;
          	
          	aux = (aux << 1) & 0xFF;
          	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	if((aux & 0x80) != 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;

          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 23;
          	pc++;      		
          	break;
          
        case 0x2E:           // SRA (IY+d) - 23 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x1) != 0) f |= FLAG_CARRY;
          	else f &= ~FLAG_CARRY;
          	
          	aux = (aux >> 1) & 0xFF;
          	
          	if((aux & 0x40) != 0) aux |= 0x80;
          	else aux &= ~0x80;

          	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);

          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	if((aux & 0x80) != 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;

          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 23;
          	pc++;      		
          	break;
         
        case 0x36:           // SLL (IY+d) - 23 cycles *
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	
          	if((aux & 0x80) != 0) f |= FLAG_CARRY;
          	else f &= ~FLAG_CARRY;
          	
          	aux = (aux << 1) & 0xFE;
          	aux |= 1;
          	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
          	if(aux == 0) f |= FLAG_ZERO;
          	else f &= ~FLAG_ZERO;
          	
          	if((aux & 0x80) != 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	if(parity[aux]) f |= FLAG_PV;
          	else f &= ~FLAG_PV;

          	f &= ~(FLAG_HCARRY | FLAG_NEG);
          	counter -= 23;
          	pc++;      		
          	break;
          
        case 0x3E:           // SRL (IY+d) - 23 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	
          	if((aux & 0x1) != 0) f |= FLAG_CARRY;
          	else f &= ~FLAG_CARRY;
          	aux = (aux >> 1) & 0x7F;
          	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
          	
            if(aux == 0) f |= FLAG_ZERO;
            else f &= ~FLAG_ZERO;
            
            if(parity[aux]) f |= FLAG_PV;
            else f &= ~FLAG_PV;
            
            f &= ~(FLAG_SIGN | FLAG_HCARRY | FLAG_NEG);
            counter -= 23;
            pc++;
          	break;
          
    	case 0x46:           // BIT 0,(IY+d) - 20 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x01) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;

        case 0x4E:           // BIT 1,(IY+d) - 20 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x02) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
        	break;

        case 0x56:           // BIT 2,(IY+d) - 20 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x04) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;
        
        case 0x5E:           // BIT 3,(IY+d) - 20 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x08) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;
        
        case 0x66:           // BIT 4,(IY+d) - 20 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x10) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;

        case 0x6E:           // BIT 5,(IY+d) - 20 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x20) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;

        case 0x76:           // BIT 6,(IY+d) - 20 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x40) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;

        case 0x7E:           // BIT 7,(IY+d) - 20 cycles
          	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
          	if((aux & 0x80) == 0) f |= FLAG_ZERO | FLAG_PV;
    		else f &= ~(FLAG_ZERO | FLAG_PV);
          	if((f & FLAG_ZERO) == 0) f |= FLAG_SIGN;
          	else f &= ~FLAG_SIGN;
          	
          	f |= FLAG_HCARRY;
          	f &= ~FLAG_NEG;
          	
          	counter -= 20;
          	pc++;      	
          	break;

        case 0x86:         // RES 0, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x01;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0x8E:         // RES 1, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x02;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0x96:         // RES 2, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x04;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0x9E:         // RES 3, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x08;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xA6:         // RES 4, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x10;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xAE:         // RES 5, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x20;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xB6:         // RES 6, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x40;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xBE:         // RES 7, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux &= ~0x80;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xC6:         // SET 0, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x01;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xCE:         // SET 1, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x02;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xD6:         // SET 2, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x04;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xDE:         // SET 3, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x08;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xE6:         // SET 4, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x10;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xEE:         // SET 5, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x20;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xF6:         // SET 6, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x40;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

        case 0xFE:         // SET 7, (IY+d) - 23 cycles
        	aux = memory.readbyte((iy + memory.readsigned(pc-1)) & 0xFFFF);
        	aux |= 0x80;
        	memory.writebyte((iy + memory.readsigned(pc-1)) & 0xFFFF, aux);
        	
        	counter -= 23;
        	pc++;
        	break;

      default:             // Uninmplemented opcode
      	//if(!msg.isVisible()) msg.setVisible(true);
        //msg.println("PC = $" + Integer.toHexString(pc - 3) + "  0xFDCB nn " + Integer.toHexString(opcode).toUpperCase() + ": Unknown opcode");
        //memory.dumpMemory();
        //vdp.dumpMemory();
        //System.exit(0);
        pc++;
    }
    flagreg = f;
  }

  public void updateDebugger()
  {
    debugger.regA.setText(Integer.toHexString(a).toUpperCase());
    debugger.regB.setText(Integer.toHexString(b).toUpperCase());
    debugger.regC.setText(Integer.toHexString(c).toUpperCase());
    debugger.regD.setText(Integer.toHexString(d).toUpperCase());
    debugger.regE.setText(Integer.toHexString(e).toUpperCase());
    debugger.regHL.setText(Integer.toHexString(h).toUpperCase() + Integer.toHexString(l).toUpperCase());
    debugger.regA_.setText(Integer.toHexString(a_).toUpperCase());
    debugger.regB_.setText(Integer.toHexString(b_).toUpperCase());
    debugger.regC_.setText(Integer.toHexString(c_).toUpperCase());
    debugger.regD_.setText(Integer.toHexString(d_).toUpperCase());
    debugger.regE_.setText(Integer.toHexString(e_).toUpperCase());
    debugger.regHL_.setText(Integer.toHexString(h_).toUpperCase() + Integer.toHexString(l_).toUpperCase());
    debugger.regIX.setText(Integer.toHexString(ix).toUpperCase());
    debugger.regIY.setText(Integer.toHexString(iy).toUpperCase());
    debugger.regF.setText(Integer.toHexString(flagreg).toUpperCase());
    debugger.regR.setText(Integer.toHexString(r).toUpperCase());
    debugger.regSP.setText(Integer.toHexString(sp).toUpperCase());
    debugger.regOpcode.setText(Integer.toHexString(opcode).toUpperCase());
    debugger.regPC.setText(Integer.toHexString(pc).toUpperCase());
    debugger.flagIFF1.setText(Boolean.toString(iff1).toUpperCase());
    debugger.flagIFF2.setText(Boolean.toString(iff2).toUpperCase());
    debugger.EILast.setText(Boolean.toString(EIDI_Last));
    debugger.flagIM.setText(Integer.toHexString(im).toUpperCase());
    debugger.flagIRQ.setText(Boolean.toString(irq).toUpperCase());
    debugger.counter.setText(Integer.toString(counter));
    
    debugger.VDPReg00.setText(Integer.toHexString(vdp.regs[0]).toUpperCase());
    debugger.VDPReg01.setText(Integer.toHexString(vdp.regs[1]).toUpperCase());
    debugger.VDPReg02.setText(Integer.toHexString(vdp.regs[2]).toUpperCase());
    debugger.VDPReg03.setText(Integer.toHexString(vdp.regs[3]).toUpperCase());
    debugger.VDPReg04.setText(Integer.toHexString(vdp.regs[4]).toUpperCase());
    debugger.VDPReg05.setText(Integer.toHexString(vdp.regs[5]).toUpperCase());
    debugger.VDPReg06.setText(Integer.toHexString(vdp.regs[6]).toUpperCase());
    debugger.VDPReg07.setText(Integer.toHexString(vdp.regs[7]).toUpperCase());
    debugger.VDPReg08.setText(Integer.toHexString(vdp.regs[8]).toUpperCase());
    debugger.VDPReg09.setText(Integer.toHexString(vdp.regs[9]).toUpperCase());
    debugger.VDPReg10.setText(Integer.toHexString(vdp.regs[10]).toUpperCase());
    debugger.VDPStatus.setText(Integer.toBinaryString(vdp.status) + "b");
    debugger.VDPAddress.setText(Integer.toHexString(vdp.address).toUpperCase());
    debugger.VDPWaitAddress.setText(Boolean.toString(vdp.WaitAddress).toUpperCase());
    debugger.VDPLatch.setText(Integer.toHexString(vdp.latch).toUpperCase());
    debugger.VDPScanline.setText(Integer.toString(vdp.scanline).toUpperCase());
    
  }

  // DAA table generator, borrowed from JavaGear (thanks Chris White)
  private void generateDAATable() {
		daa = new int[0x800];

		final int NEGATIVE = 0x100;
		final int CARRY = 0x200;
		final int HALFCARRY = 0x400;

		for (int x = 0; x <= 0x7FF; x++) {
			int a = x & 0xFF;
			int highNibble = (a >> 4);
			int lowNibble  = (a & 0x0F);
			boolean carry = false;

			if ((x & NEGATIVE) == 0) { // ADD, ADC, INC
				if ((x & CARRY) == 0) {
					if ((x & HALFCARRY) == 0) {
						if ((highNibble <= 0x08) && (lowNibble <= 0x0F) && (lowNibble >= 0x0A)) // Line 2
							a+=0x06;
						else if ((highNibble <= 0x09) && (lowNibble <= 0x09)); // Line 1 of Page 236
						else if ((highNibble >= 0x0A) && (lowNibble <= 0x09)) { // Line 4
							a+=0x60;
							carry = true;
						}
						else if ((highNibble >= 0x09) && (lowNibble >= 0x0A)) { // Line 5
							a+=0x66;
							carry = true;
						}
					}
					else {// Half Carry is 1, Carry is 0, Negative is 0
						if ((highNibble <= 0x09) && (lowNibble <= 0x03)) // Line 3
							a+=0x06;
						else if ((highNibble >= 0x0A) && (lowNibble <= 0x03)) { // Line 6
							a+=0x66;
							carry = true;
						}
					}

				}
				else { // Flag Carry is 1
					if ((x & HALFCARRY) == 0) { // C = 1, HC = 0, N = 0
						if ((highNibble <= 0x02) && (lowNibble <= 0x09)) { // Line 7
							a+=0x60;
							carry = true;
						}
						else if ((highNibble <= 0x02) && (lowNibble >= 0x0A)) { // Line 8
							a+=0x66;
							carry = true;
						}
					}
					else { // Half Carry is 1
						if ((highNibble < 0x04) && (lowNibble < 0x04)) { // Line 9
							a+=0x66;
							carry = true;
						}
					}
				}
			}
			// Negative Flag is true SUB, SBC, DEC, NEG --------------------------------------
			else {
				if ((x & CARRY) == 0) {
					if ((x & HALFCARRY) == 0) {
						if ((highNibble < 0x0A) && (lowNibble < 0x0A) );  // Line 10
					}
					else { // Flag HalfCarry is 1, Carry = 0, Neg = 1
						if ((highNibble < 0x09) && (lowNibble > 0x05)) // Line 11
							a +=0xFA;
					}
				}
				else { // Flag Carry is 1
					if ((x & HALFCARRY) == 0) {
						if ((highNibble >= 0x07) && (lowNibble <= 0x09)) { // Line 12
							a+=0xA0;
							carry = true;
						}
					}
					else { // Flag HalfCarry is 1
						if ((highNibble >= 0x06) && (lowNibble >= 0x06)) { // Line 13
							a +=0x9A;
							carry = true;
						}
					}
				}
			}

			a &= 0xFF;
			if (carry) a|=0x100;

			daa[x] = a;
		} // end for
  }
  
  private void generateParityTable() {
  	parity = new boolean[256];
  	int result;
  	
  	for(int i=0; i < 256; i++) {
  		result = 0;
  		
  		result ^= i & 1;
  		result ^= (i >> 1) & 1;
  		result ^= (i >> 2) & 1;
  		result ^= (i >> 3) & 1;
  		result ^= (i >> 4) & 1;
  		result ^= (i >> 5) & 1;
  		result ^= (i >> 6) & 1;
  		result ^= (i >> 7) & 1;
  		
  		if(result == 1) parity[i] = false;
  		else parity[i] = true;
  	}
  }
}
    
