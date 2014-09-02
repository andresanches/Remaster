import java.util.*;
import java.io.*; 

public final class MemoryManager
{
  private Cartridge cart;
  private EZ80 z80;
  
  byte[] memory;
  byte[] cartRam;
  
  FileOutputStream memdump;
  private boolean hasCartRAM;

  public MemoryManager(Cartridge cart) {
    this.cart = cart;

    memory = new byte[0x10000];  // 64k memory space
    cartRam = new byte[0x8000];  // 32k cartridge RAM
  }

  public void reset() {
    System.out.print("MEMORY: Resetting memory... ");
    Arrays.fill(memory, (byte) 0);
    hasCartRAM = false;
    System.out.println("OK");
  }
  
  public void writebyte(int addr, int value) {
  	//addr &= 0xFFFF;
  	//if(addr < 0x8000) return; // Prevent from writing to rom

    if((addr >= 0xC000) && (addr < 0xFDFC)) // Write to RAM
        memory[addr] = (byte)(value);

  	else if((addr >= 0xE000) && (addr < 0xFFFC)) // Mirror of RAM at $C000-$DFFF
    	  memory[(addr - 0x2000)] = (byte)(value);

  	else if(addr == 0xFFFC) { // RAM Select register
  		if((value & 0x8) != 0)
  			hasCartRAM = true;
  		memory[0xFFFC] = (byte)(value);
  	}

  	else if(addr == 0xFFFD) { // Page 0 ROM bank
  		System.arraycopy(cart.romData, ((value % cart.numPages) << 14) + 0x400, memory, 0x400, 15360);
  		memory[0xFFFD] = (byte)(value);
  	}

  	else if(addr == 0xFFFE) { // Page 1 ROM bank
  		System.arraycopy(cart.romData, (value % cart.numPages) << 14, memory, 0x4000, 16384);
  		memory[0xFFFE] = (byte)(value);
  	}

  	else if(addr == 0xFFFF) { // Page 2 ROM/Cart RAM bank
  		if(((memory[0xFFFC] & 0x8) == 0)) {// Page 2 mapped as ROM
  			System.arraycopy(cart.romData, (value % cart.numPages) << 14, memory, 0x8000, 16384);
  		}
  		memory[0xFFFF] = (byte)(value);
  	}

  	else if((addr >= 0x8000) && (addr < 0xC000) && ((memory[0xFFFC] & 0x8) != 0)) { // Write to Cartridge RAM
 	  cartRam[(addr - 0x8000) + (((memory[0xFFFC] >> 2) & 1) << 14)] = (byte)(value);
  	}
  }

  public int readbyte(int addr) {
  	addr &= 0xFFFF;
  	if((addr >= 0x8000) && (addr < 0xC000) && ((memory[0xFFFC] & 0x8) != 0)) { // Read from Cartridge ram
  		return cartRam[(addr - 0x8000) + (((memory[0xFFFC] >> 2) & 1) << 14)] & 0xFF;
  	}
  	else if((addr >= 0xE000) && (addr < 0xFFFC)) // Mirror of RAM at $C000-$DFFF
  		return memory[addr - 0x2000] & 0xFF;
  	else
  		return memory[addr] & 0xFF;
  }
  
  public int readsigned(int addr) {
  	if((addr >= 0xE000) && (addr < 0xFFFF)) // Mirror of RAM at $C000-$DFFF
  		return memory[addr - 0x2000];
  	else
  		return memory[addr];
  }

  public void loadFromCartridge() { // Load first 32k from rom
    System.out.print("MEMORY: Reading from cartridge... ");
    if(cart.getRomSize() > 32768)
    	System.arraycopy(cart.romData, 0, memory, 0, 32768);
    else
        System.arraycopy(cart.romData, 0, memory, 0, cart.getRomSize());
    System.out.println("OK");
  }
  
  public void dumpMemory() {
  	try {
	  memdump = new FileOutputStream(new File("memdump.bin"));
	  memdump.write(memory);
	  memdump.close();
	  System.out.println("MAIN MEMORY: dumped memory to MEMDUMP.DAT");
	  if(hasCartRAM) {
	    memdump = new FileOutputStream(new File("cartram.bin"));
	    memdump.write(cartRam);
	    memdump.close();
	    System.out.println("MAIN MEMORY: dumped Cartridge RAM to CARTRAM.BIN");
	  }
  	}
    catch(IOException e) {
      System.out.println("MAIN MEMORY: Error dumping memory!");
    }
  }
}
