import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;

public final class Debugger extends JFrame
{
  /* CPU Registers */
  public Label regA = new Label();
  public Label regB = new Label();
  public Label regC = new Label();
  public Label regD = new Label();
  public Label regE = new Label();
  public Label regHL = new Label();
  public Label regA_ = new Label();
  public Label regB_ = new Label();
  public Label regC_ = new Label();
  public Label regD_ = new Label();
  public Label regE_ = new Label();
  public Label regHL_ = new Label();
  public Label regIX = new Label();
  public Label regIY = new Label();
  public Label regF = new Label();
  public Label regR = new Label();
  public Label regSP = new Label();
  public Label regPC = new Label();
  public Label regOpcode = new Label();
  public Label flagIFF1 = new Label();
  public Label flagIFF2 = new Label();
  public Label EILast = new Label();
  public Label flagI = new Label();
  public Label flagIM = new Label();
  public Label flagIRQ = new Label();
  public Label counter = new Label();

  public Label s_regA     = new Label("A = $");
  public Label s_regB     = new Label("B = $");
  public Label s_regC     = new Label("C = $");
  public Label s_regD     = new Label("D = $");
  public Label s_regE     = new Label("E = $");
  public Label s_regHL     = new Label("HL = $");
  public Label s_regA_    = new Label("A' = $");
  public Label s_regB_    = new Label("B' = $");
  public Label s_regC_    = new Label("C' = $");
  public Label s_regD_    = new Label("D' = $");
  public Label s_regE_    = new Label("E' = $");
  public Label s_regHL_    = new Label("HL' = $");
  public Label s_regIX    = new Label("IX = $");
  public Label s_regIY    = new Label("IY = $");
  public Label s_regF     = new Label("F = ");
  public Label s_regR     = new Label("R = ");
  public Label s_regSP    = new Label("SP = $");
  public Label s_regPC    = new Label("PC = $");
  public Label s_regOpcode = new Label("Opcode = $");
  public Label s_flagIFF1 = new Label("IFF1 = ");
  public Label s_flagIFF2 = new Label("IFF2 = ");
  public Label s_EILast = new Label("EI_Last = ");
  public Label s_flagIM   = new Label("Interrupt Mode: ");
  public Label s_flagIRQ  = new Label("IRQ Line: ");
  public Label s_counter  = new Label("Counter: ");

  /* VDP Registers */
  public Label VDPReg00 = new Label();
  public Label VDPReg01 = new Label();
  public Label VDPReg02 = new Label();
  public Label VDPReg03 = new Label();
  public Label VDPReg04 = new Label();
  public Label VDPReg05 = new Label();
  public Label VDPReg06 = new Label();
  public Label VDPReg07 = new Label();
  public Label VDPReg08 = new Label();
  public Label VDPReg09 = new Label();
  public Label VDPReg10 = new Label();
  public Label VDPStatus = new Label();
  public Label VDPAddress = new Label();
  public Label VDPWaitAddress = new Label();
  public Label VDPLatch = new Label();
  public Label VDPScanline = new Label();

  public Label s_VDPReg00 = new Label("#00: $");
  public Label s_VDPReg01 = new Label("#01: $");
  public Label s_VDPReg02 = new Label("#02: $");
  public Label s_VDPReg03 = new Label("#03: $");
  public Label s_VDPReg04 = new Label("#04: $");
  public Label s_VDPReg05 = new Label("#05: $");
  public Label s_VDPReg06 = new Label("#06: $");
  public Label s_VDPReg07 = new Label("#07: $");
  public Label s_VDPReg08 = new Label("#08: $");
  public Label s_VDPReg09 = new Label("#09: $");
  public Label s_VDPReg10 = new Label("#10: $");
  public Label s_VDPStatus = new Label("Status = ");
  public Label s_VDPAddress = new Label("Address: $");
  public Label s_VDPWaitAddress = new Label("Waiting address: ");
  public Label s_VDPLatch = new Label("Latch: $");
  public Label s_VDPScanline = new Label("Scanline: $");

  JPanel panel_CPU, p0, p1, p2, panel_VDP;
  
  boolean enabled = false;

  public Debugger()
  {
    super("SMS Debugger - " + Remaster.APPNAME);
    setSize(500, 420);
    setLocation(0, 30);

	addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { setVisible(false); enabled = false; } } );

	getContentPane().setLayout(new GridLayout(1, 2));

    p0 = new JPanel();
    p1 = new JPanel();
    p2 = new JPanel();
    panel_VDP = new JPanel();
    panel_CPU = new JPanel();

    p0.setLayout(new GridLayout(1,2));
    p0.add(p1);
    p0.add(p2);
    panel_CPU.setLayout(new BorderLayout());
    panel_CPU.add(p0, BorderLayout.NORTH);
    p1.setLayout(new GridLayout(0,2));
    p2.setLayout(new GridLayout(0,2));
    panel_VDP.setLayout(new GridLayout(0,2));

    p1.add(s_regA);
    p1.add(regA);
    p1.add(s_regB);
    p1.add(regB);
    p1.add(s_regC);
    p1.add(regC);
    p1.add(s_regD);
    p1.add(regD);
    p1.add(s_regE);
    p1.add(regE);
    p1.add(s_regHL);
    p1.add(regHL);
    p1.add(s_regIX);
    p1.add(regIX);
    p1.add(s_regIY);
    p1.add(regIY);
    p1.add(s_regSP);
    p1.add(regSP);
    p1.add(s_flagIFF1);
    p1.add(flagIFF1);
    p1.add(s_flagIFF2);
    p1.add(flagIFF2);
    p1.add(s_EILast);
    p1.add(EILast);
    p1.add(s_flagIM);
    p1.add(flagIM);
    p1.add(s_flagIRQ);
    p1.add(flagIRQ);
    p1.add(s_regOpcode);
    p1.add(regOpcode);
    p1.add(s_regPC);
    p1.add(regPC);

    p2.add(s_regA_);
    p2.add(regA_);
    p2.add(s_regB_);
    p2.add(regB_);
    p2.add(s_regC_);
    p2.add(regC_);
    p2.add(s_regD_);
    p2.add(regD_);
    p2.add(s_regE_);
    p2.add(regE_);
    p2.add(s_regHL_);
    p2.add(regHL_);
    p2.add(s_counter);
    p2.add(counter);

    panel_VDP.add(s_VDPReg00);
    panel_VDP.add(VDPReg00);
    panel_VDP.add(s_VDPReg01);
    panel_VDP.add(VDPReg01);
    panel_VDP.add(s_VDPReg02);
    panel_VDP.add(VDPReg02);
    panel_VDP.add(s_VDPReg03);
    panel_VDP.add(VDPReg03);
    panel_VDP.add(s_VDPReg04);
    panel_VDP.add(VDPReg04);
    panel_VDP.add(s_VDPReg05);
    panel_VDP.add(VDPReg05);
    panel_VDP.add(s_VDPReg06);
    panel_VDP.add(VDPReg06);
    panel_VDP.add(s_VDPReg07);
    panel_VDP.add(VDPReg07);
    panel_VDP.add(s_VDPReg08);
    panel_VDP.add(VDPReg08);
    panel_VDP.add(s_VDPReg09);
    panel_VDP.add(VDPReg09);
    panel_VDP.add(s_VDPReg10);
    panel_VDP.add(VDPReg10);
    panel_VDP.add(s_VDPStatus);
    panel_VDP.add(VDPStatus);
    panel_VDP.add(s_VDPAddress);
    panel_VDP.add(VDPAddress);
    panel_VDP.add(s_VDPWaitAddress);
    panel_VDP.add(VDPWaitAddress);
    panel_VDP.add(s_VDPLatch);
    panel_VDP.add(VDPLatch);
    panel_VDP.add(s_VDPScanline);
    panel_VDP.add(VDPScanline);

    Border borda = BorderFactory.createEtchedBorder();
    panel_CPU.setBorder(BorderFactory.createTitledBorder(borda, "CPU:"));
    panel_VDP.setBorder(BorderFactory.createTitledBorder(borda, "VDP:"));

    getContentPane().add(panel_CPU);
    getContentPane().add(panel_VDP);
  }

  public void toggleEnabled() {
	enabled = !enabled;
	setVisible(enabled);
  }
  
  public static void main(String args[])
  {
    new Debugger();
  }
}
