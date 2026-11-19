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
  private boolean cartRamWritten;
  private boolean cartRamDirty;
  private int cartRamSaveSize;
  private int cartRamHighWater;
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
      int idx = (addr - 0x8000) + (((memory[0xFFFC] >> 2) & 1) << 14);
      cartRam[idx] = (byte)(value);
      cartRamWritten = true;
      cartRamDirty = true;
      if(idx > cartRamHighWater) cartRamHighWater = idx;
  	}
  }

  public int readbyte(int addr) {
  	addr &= 0xFFFF;
  	if((addr >= 0x8000) && (addr < 0xC000) && ((memory[0xFFFC] & 0x8) != 0)) { // Read from Cartridge ram
  		int ridx = (addr - 0x8000) + (((memory[0xFFFC] >> 2) & 1) << 14);
  		return cartRam[ridx] & 0xFF;
  	}
  	else if((addr >= 0xE000) && (addr < 0xFFFC)) // Mirror of RAM at $C000-$DFFF
  		return memory[addr - 0x2000] & 0xFF;
  	else
  		return memory[addr] & 0xFF;
  }
  
  public int readsigned(int addr) {
  	addr &= 0xFFFF;
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

  // Builds the battery RAM save file path for the currently loaded ROM:
  // "gamename.sms" -> "gamename.bram", next to the ROM file.
  private File getSaveFile() {
    String path = cart.getFullPath();
    if(path == null) return null;
    int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    int dot = path.lastIndexOf('.');
    if(dot > sep) path = path.substring(0, dot);
    return new File(path + ".bram");
  }

  public void loadCartRam() { // Restore battery-backed SRAM on ROM boot
    cartRamWritten = false;
    cartRamDirty = false;
    cartRamHighWater = 0;
    cartRamSaveSize = 0x2000; // the SMS SRAM is 8k by default
    Arrays.fill(cartRam, (byte)0);

    File saveFile = getSaveFile();
    if(saveFile == null || !saveFile.isFile()) return;

    FileInputStream saveStream = null;
    try {
      saveStream = new FileInputStream(saveFile);
      int size = Math.min((int)saveFile.length(), cartRam.length), read = 0;
      while(read < size) {
        int count = saveStream.read(cartRam, read, size - read);
        if(count <= 0) break;
        read += count;
      }
      cartRamSaveSize = Math.max((read + 0x1FFF) & ~0x1FFF, 0x2000); // keep save size, rounded up to 8k banks
      System.out.println("MEMORY: Restored " + read + " bytes of battery RAM from " + saveFile.getName());
    }
    catch(IOException e) {
      System.out.println("MEMORY: Error loading battery RAM save file!");
    }
    finally {
      try { if(saveStream != null) saveStream.close(); }
      catch(IOException e) {}
    }
  }

  public void saveCartRam() { // Flush battery-backed SRAM to disk. Does nothing for plain ROM carts.
    if(!cartRamWritten || !cartRamDirty) return;

    File saveFile = getSaveFile();
    if(saveFile == null) return;

    int size = cartRamSaveSize;
    if(cartRamHighWater + 1 > size) { // game wrote past the save size: grow it to cover the 32k RAM mapper
      size = Math.max(Math.min((cartRamHighWater + 0x2000) & ~0x1FFF, cartRam.length), 0x2000);
    }

    try {
      FileOutputStream saveStream = new FileOutputStream(saveFile);
      saveStream.write(cartRam, 0, size);
      saveStream.close();
      cartRamDirty = false;
      cartRamSaveSize = size;
      System.out.println("MEMORY: Saved battery RAM to " + saveFile.getName());
    }
    catch(IOException e) {
      System.out.println("MEMORY: Error saving battery RAM save file!");
    }
  }

  public void saveState(DataOutputStream out) throws IOException {
    out.write(memory);
    out.write(cartRam);

    out.writeBoolean(hasCartRAM);
    out.writeBoolean(cartRamWritten);
    out.writeBoolean(cartRamDirty);
    out.writeInt(cartRamSaveSize);
    out.writeInt(cartRamHighWater);
  }

  public void loadState(DataInputStream in) throws IOException {
    in.readFully(memory);
    in.readFully(cartRam);

    hasCartRAM = in.readBoolean();
    cartRamWritten = in.readBoolean();
    cartRamDirty = in.readBoolean();
    cartRamSaveSize = in.readInt();
    cartRamHighWater = in.readInt();
  }
}
