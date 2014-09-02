public final class Ports
{
  EZ80 z80;
  VDP vdp;
  PSG psg;
  Joystick joy;
  
  public Ports(VDP vdp, PSG psg, Joystick joy) {
  	this.vdp = vdp;
  	this.psg = psg;
  	this.joy = joy;
  }
  
  public final int read(int port) {
  	port &= 0xFF;
  	int retval = 0xFF;
    switch(port) {
    	case 0x7E: // H/V Counter FIXME: H Counter not implemented yet
    	case 0x7F:
    	  if(vdp.scanline > 0xDA)
    	  	retval = vdp.scanline - 5;
    	  else
    	    retval = vdp.scanline;
    	  break;

    	case 0xBE: // VDP Data Port
          retval = vdp.data_port_read();
          break;

    	case 0xBF: // VDP Control Port (mirrored at $BD)
        case 0xBD:
          retval = vdp.control_port_read();
          break;
          
        case 0xC0: // Joypad port 1
        case 0xDC: // mirror at $DC
          retval = joy.port1_read();
          break;

        case 0xC1: // Joypad port 2
        case 0xDD: // mirror at $DD
          retval = joy.port2_read();
          break;

        case 0xDE: // Unknown but often read ports
        case 0xDF:
          break;

        default:
          //System.out.println("PORTS READ: read from unknown port $" + Integer.toHexString(port).toUpperCase());
    }
    return (retval & 0xFF);
  }
  
  public final void write(int port, int value) {
    //port &= 0xFF;
  	
  	switch(port) {
      case 0x3F: // Automatic nationalisation
      	if((value & 0x20) != 0) joy.byte2 |= 0x40; // Nationalisation bit 1
      	else joy.byte2 &= ~0x40;
      	if((value & 0x80) != 0) joy.byte2 |= 0x80; // Nationalisation bit 2
      	else joy.byte2 &= ~0x80;
      	break;
      	
      case 0x7E: // PSG port write (mirrored at $7F)
      case 0x7F:
      	psg.write(value);
      	break;

      case 0xBE: // VDP Data Port
        vdp.data_port_write(value);
        break;

      case 0xBF:        // VDP Control Port (mirrored at $BD)
      case 0xBD:
        vdp.control_port_write(value);
        break;

      case 0xDE: // Unknown but often written ports
      case 0xDF:
      	break;

      default:
        //System.out.println("PORTS WRITE: write to unknown port $" + Integer.toHexString(port).toUpperCase());
    }
  }
}