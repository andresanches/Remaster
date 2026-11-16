import java.awt.*;
import java.io.*;

public final class Cartridge {
  private File romFile;
  private FileInputStream fileStream;
  private FileDialog openDialog;
  private String fileName, fullPath;
  public byte[] romData;
  public int romSize, numPages;
  private boolean romLoaded;

  public Cartridge() {
  	romLoaded = false;
  }

  public void showOpenDialog(Frame parent) {
    openDialog = new FileDialog(parent, "Abrir ROM", FileDialog.LOAD);
    openDialog.setDirectory("./");
    openDialog.setFile("*.sms;*.gg");
    openDialog.setVisible(true);

    fileName = openDialog.getFile();

    if(fileName != null && fileName.trim().length() != 0)
      loadRom(openDialog.getDirectory() + fileName);
    else
      romLoaded = false;

    openDialog = null;
  }

  public boolean loadRom(String path) {
    romFile = new File(path);
    fileName = romFile.getName();
    fullPath = path;
    romLoaded = false;

    if(!romFile.isFile()) {
      fileName = null;
      fullPath = null;
      System.err.println("CARTRIDGE ERROR: File not found.");
      return false;
    }

    try {
      System.out.println("CARTRIDGE: Loading rom " + fileName.toUpperCase() + "... ");
      fileStream = new FileInputStream(romFile);
      romSize = (int)romFile.length();
      if(romSize % 4096 != 0) { // Header detected. Skip it.
        System.out.print("CARTRIDGE: ROM Header Found. Skipping it... ");
        romSize -= 512;
        romData = new byte[romSize];
        numPages = romSize / 16384;
        try { fileStream.skip(512); System.out.println("OK"); }
        catch(IOException e) { System.err.println("CRITICAL I/O ERROR: file error"); System.exit(1); }
      }
      else {
        System.out.println("CARTRIDGE: ROM Header not found");
        romData = new byte[romSize];
        numPages = romSize / 16384;
      }

      try {
        fileStream.read(romData);
        romLoaded = true;
        System.out.println("CARTRIDGE: Successfully loaded " + Integer.toString(romSize) + " bytes.");
      }
      catch(IOException e) { System.err.println("CRITICAL I/O ERROR: File cannot be loaded!"); System.exit(1); }
    }
    catch(FileNotFoundException e) {
      fileName = null;
      fullPath = null;
      romLoaded = false;
      System.err.println("CARTRIDGE ERROR: File not found.");
    }

    try { fileStream.close(); }
    catch(Exception e) {}
    fileStream = null;

    return romLoaded;
  }

  public String getFileName() {
    return fileName;
  }

  public String getFullPath() {
    return fullPath;
  }

  public int getRomSize() {
    return romSize;
  }

  public boolean isLoaded() {
    return romLoaded;
  }
  
  public void unload() {
  	romLoaded = false;
  }
}
