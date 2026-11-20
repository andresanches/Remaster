import java.io.*;

/*
 * Save states: a snapshot of the whole emulated console (CPU registers, main
 * memory and cartridge RAM, VDP and PSG) written to disk so a game can be
 * resumed at any point, in any of six selectable slots.
 *
 * State files follow the same convention as the battery-backed RAM files:
 * "gamename.sms" -> "gamename.st1" through "gamename.st6", next to the ROM.
 * Each file starts with a header naming the ROM it was made from, so a state
 * is never restored into a different game.
 *
 * The joypad input lines (Joystick.byte1/byte2) are deliberately not saved:
 * they are the keys the user happens to be holding when the snapshot is taken,
 * not console state, and restoring them would leave buttons stuck down.
 * Emulator settings that are not part of the hardware (sound and channel
 * enable flags, speed multiplier, frameskip) are not saved either.
 */
public final class SaveState {
  public static final int SLOTS = 6;          // save slots offered to the user
  public static final int DEFAULT_SLOT = 1;   // slot in use until another is picked

  private static final String MAGIC = "REMSAVE";
  private static final int VERSION = 1;

  // "path/gamename.sms" -> "path/gamename.st<n>", next to the ROM file.
  public static File getStateFile(Cartridge cart, int slot) {
    String path = cart.getFullPath();
    if(path == null) return null;

    int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    int dot = path.lastIndexOf('.');
    if(dot > sep) path = path.substring(0, dot);
    return new File(path + ".st" + slot);
  }

  public static boolean isValidSlot(int slot) {
    return (slot >= 1) && (slot <= SLOTS);
  }

  public static boolean slotHasState(Cartridge cart, int slot) {
    if(!isValidSlot(slot)) return false;
    File stateFile = getStateFile(cart, slot);
    return (stateFile != null) && stateFile.isFile();
  }

  public static boolean save(Cartridge cart, MemoryManager memory, VDP vdp, PSG psg, EZ80 z80, int slot) {
    if(!isValidSlot(slot) || !cart.isLoaded() || cart.getFileName() == null) return false;

    File stateFile = getStateFile(cart, slot);
    if(stateFile == null) return false;

    DataOutputStream out = null;
    try {
      out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(stateFile)));
      out.writeUTF(MAGIC);
      out.writeInt(VERSION);
      out.writeUTF(cart.getFileName());  // a state only makes sense for the game it came from

      z80.saveState(out);
      memory.saveState(out);
      vdp.saveState(out);
      psg.saveState(out);
      out.flush();

      System.out.println("SAVESTATE: Saved " + cart.getFileName() + " state in slot " + slot + " (" + stateFile.getName() + ").");
      return true;
    }
    catch(IOException e) {
      System.out.println("SAVESTATE: Error saving state in slot " + slot + "!");
      return false;
    }
    finally {
      try { if(out != null) out.close(); }
      catch(IOException e) {}
    }
  }

  public static boolean load(Cartridge cart, MemoryManager memory, VDP vdp, PSG psg, EZ80 z80, int slot) {
    if(!isValidSlot(slot) || !cart.isLoaded() || cart.getFileName() == null) return false;

    File stateFile = getStateFile(cart, slot);
    if(stateFile == null || !stateFile.isFile()) {
      System.out.println("SAVESTATE: Slot " + slot + " is empty, nothing to restore.");
      return false;
    }

    // The whole file is read first: the header is checked before any of the
    // emulated hardware is touched, so a state from another ROM (or made by a
    // different version of this class) is rejected instead of half applied.
    byte[] data = new byte[(int)stateFile.length()];
    DataInputStream in = null;
    try {
      in = new DataInputStream(new BufferedInputStream(new FileInputStream(stateFile)));
      int read = 0;
      while(read < data.length) {
        int count = in.read(data, read, data.length - read);
        if(count <= 0) break;
        read += count;
      }
      in.close();
      in = new DataInputStream(new ByteArrayInputStream(data));

      String magic = in.readUTF();
      int version = in.readInt();
      String rom = in.readUTF();
      if(!magic.equals(MAGIC) || version != VERSION) {
        System.out.println("SAVESTATE: " + stateFile.getName() + " is not a Remaster save state (or uses an incompatible format).");
        return false;
      }
      if(!rom.equals(cart.getFileName())) {
        System.out.println("SAVESTATE: " + stateFile.getName() + " was saved from " + rom + ", not from " + cart.getFileName() + ".");
        return false;
      }

      z80.loadState(in);
      memory.loadState(in);
      vdp.loadState(in);
      psg.loadState(in);

      System.out.println("SAVESTATE: Restored " + cart.getFileName() + " state from slot " + slot + " (" + stateFile.getName() + ").");
      return true;
    }
    catch(IOException e) {
      System.out.println("SAVESTATE: Error reading " + stateFile.getName() + ", the save state is damaged!");
      return false;
    }
    finally {
      try { if(in != null) in.close(); }
      catch(IOException e) {}
    }
  }
}
