import java.awt.*;
import java.awt.event.*;

public final class Remaster extends Frame {
  Remaster remaster;
  MainThread mainloop;
  Cartridge cart;
  Joystick joy;
  MemoryManager memory;
  VDP vdp;
  PSG psg;
  Ports ports;
  EZ80 z80;
  Screen screen;
  Debugger debugger;
  DrawSurface drawsurface;

  VRAMViewer vramviewer;
  CRAMViewer cramviewer;
  
  boolean adjustingAspect = false;
  int lastCanvasW = 0, lastCanvasH = 0;
  
  MenuBar menubar;
  Menu file;
  Menu emulator;
  Menu display;
  Menu sound;
  Menu help;
  AboutFrame aboutFrame;
  
  Menu saveSlots;                                                        // submenu holding the six save state slots
  CheckboxMenuItem[] slotitems = new CheckboxMenuItem[SaveState.SLOTS];
  int savestate_slot = SaveState.DEFAULT_SLOT;                           // slot the save/load state commands work on
  boolean selectingSlot = false;                                         // guards against the slot exclusivity loop
  
  static String APPNAME = "Remaster v0.01";
  
  public Remaster() {
    super(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration());
    this.remaster = this;
    
    System.out.println(Remaster.APPNAME);

    setLayout(new BorderLayout());
    setTitle(Remaster.APPNAME);
    setLocation(100, 50);
    setSize(520, 430);

    // create drawing surface panel and add it to the main frame
    drawsurface = new DrawSurface(this);
    screen = new Screen(drawsurface, 256, 192);
    screen.clearBuffer();
    add(drawsurface, BorderLayout.CENTER);
    
    addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        if(adjustingAspect) return;
        if(screen != null && screen.fullscreen) return;  // the borderless fullscreen window owns the geometry
        Dimension fs = getSize();
        Insets in  = getInsets();
        int cw = fs.width  - in.left - in.right;   // drawing area size
        int ch = fs.height - in.top  - in.bottom;
        if(cw <= 0 || ch <= 0) return;
        
        int dcw = Math.abs(cw - lastCanvasW), dch = Math.abs(ch - lastCanvasH);
        lastCanvasW = cw; lastCanvasH = ch;
        if(dcw == 0 && dch == 0) return;  // window realized or size unchanged: nothing to do
        
        // Correct from whichever dimension the user dragged the most.
        int newCw, newCh;
        if(dcw >= dch) { newCw = cw;      newCh = Math.round(cw * 3f / 4f); }
        else           { newCh = ch;      newCw = Math.round(ch * 4f / 3f); }
        
        int fw = newCw + in.left + in.right, fh = newCh + in.top + in.bottom;
        if(fw != fs.width || fh != fs.height) {
          adjustingAspect = true;
          setSize(fw, fh);
          adjustingAspect = false;
        }
      }
    });
    
    // Set up emulation classes 
    joy = new Joystick();
    cart = new Cartridge();
    memory = new MemoryManager(cart);
    vdp = new VDP(screen);
    psg = new PSG();
    ports = new Ports(vdp, psg, joy);
    debugger = new Debugger();
    z80 = new EZ80(memory, ports, vdp, debugger);
    vramviewer = new VRAMViewer(vdp);
    cramviewer = new CRAMViewer(vdp);

    // create About Frame
    aboutFrame = new AboutFrame();
    // create and set up MenuBar
    menubar = setup_MenuBar();
    setMenuBar(menubar);
    setVisible(true);
    setWindowSize(2);
    
/*    // --------- Debug - instruction trace output stream -------
    java.io.PrintStream trace = null;
    try {
      trace = new java.io.PrintStream(new java.io.FileOutputStream("trace.log", false));
    }
    catch(java.io.FileNotFoundException e) {}
    System.setOut(trace);
    // --------- end of temporary code block -----------------
*/    
    // Setup action listeners
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) { exit(); }
      public void windowActivated(WindowEvent e) {
        if(getFocusOwner() != drawsurface) drawsurface.requestFocusInWindow();
      }
    });
    drawsurface.requestFocusInWindow();
}
  
  public void startEmulation() {
    setTitle(Remaster.APPNAME + " - " + cart.getFileName());
    
    for(int i=0; i < file.getItemCount(); i++)     file.getItem(i).setEnabled(true);
    for(int i=0; i < emulator.getItemCount(); i++) emulator.getItem(i).setEnabled(true);
    
    pauseEmulation();
    if(mainloop != null) return;

    mainloop = new MainThread(screen, cart, memory, vdp, psg, ports, joy, z80, debugger, vramviewer, cramviewer);
    mainloop.SMS_reset();
    
    mainloop.start();
  }

  public void saveGameState() { saveGameState(savestate_slot); }

  public void saveGameState(int slot) {
    if(cart == null || !cart.isLoaded()) return;

    boolean wasrunning = pauseEmulation();
    if(mainloop != null) return;

    SaveState.save(cart, memory, vdp, psg, z80, slot);
    if(wasrunning) resumeEmulation();
    drawsurface.requestFocus();
  }

  public void loadGameState() { loadGameState(savestate_slot); }

  public void loadGameState(int slot) {
    if(cart == null || !cart.isLoaded()) return;

    boolean wasrunning = pauseEmulation();
    if(mainloop != null) return;

    if(SaveState.load(cart, memory, vdp, psg, z80, slot)) {
      if(vramviewer.enabled) vramviewer.update();
      if(cramviewer.enabled) cramviewer.update();
      screen.drawScreen(0, 0);
    }
    if(wasrunning) resumeEmulation();
    drawsurface.requestFocus();
  }

  public boolean pauseEmulation() {
    if(mainloop == null) return false;

    MainThread emu = mainloop;
    emu.stopEmulation();
    try { emu.join(2000); }
    catch(InterruptedException e) {}

    if(emu.isAlive()) {
      System.out.println("EMULATOR: Emulation thread is taking too long to stop.");
      return false;
    }
    mainloop = null;
    return true;
  }

  public void resumeEmulation() {
    if(mainloop != null) return;

    mainloop = new MainThread(screen, cart, memory, vdp, psg, ports, joy, z80, debugger, vramviewer, cramviewer);
    mainloop.start();
  }

  public void selectSlot(int slot) {
    if(selectingSlot) return;

    selectingSlot = true;
    savestate_slot = slot;
    for(int i=0; i < slotitems.length; i++) slotitems[i].setState((i + 1) == slot);
    selectingSlot = false;

    System.out.println("SAVESTATE: Using save state slot " + slot + ".");
  }
  
  public void exit() {
  	pauseEmulation();
  	//memory.dumpMemory();
  	//vdp.dumpMemory();
  	memory.saveCartRam();
  	System.exit(0);
  }
  
  public void paint(Graphics g) {
  	if(mainloop != null) screen.drawScreen(0, 0);
  	else {
  		screen.clearBuffer();
  		screen.drawScreen(0, 0);
  	}
  }
  
  public MenuBar setup_MenuBar() {
  	MenuBar menubar = new MenuBar();
  	
    file = new Menu("Arquivo");
    MenuItem openRom  = new MenuItem("Abrir ROM");
    openRom.setShortcut(new MenuShortcut(KeyEvent.VK_O));
    MenuItem closeRom = new MenuItem("Fechar ROM");
    closeRom.setShortcut(new MenuShortcut(KeyEvent.VK_C));
    closeRom.setEnabled(false);
    MenuItem exit =     new MenuItem("Sair");
    exit.setShortcut(new MenuShortcut(KeyEvent.VK_X));
    file.add(openRom);
    file.add(closeRom);
    file.addSeparator();
    file.add(exit);
    menubar.add(file);
    
    emulator = new Menu("Emulação");
    CheckboxMenuItem pauseresume = new CheckboxMenuItem("Pause");
    pauseresume.setEnabled(false);
    MenuItem pausebutton = new MenuItem("Pressionar botão Pause");
    pausebutton.setShortcut(new MenuShortcut(KeyEvent.VK_P));
    pausebutton.setEnabled(false);
    CheckboxMenuItem vramview =    new CheckboxMenuItem("Habilitar visualizador de tiles (VRAM)");
    vramview.setEnabled(false);
    CheckboxMenuItem cramview =    new CheckboxMenuItem("Habilitar visualizador da paleta de cores (CRAM)");
    cramview.setEnabled(false);
    CheckboxMenuItem debug =       new CheckboxMenuItem("Habilitar Debugger");
    debug.setEnabled(false);
    emulator.add(pauseresume);
    emulator.add(pausebutton);
    emulator.addSeparator();
    emulator.add(vramview);
    emulator.add(cramview);
    emulator.addSeparator();
    emulator.add(debug);
    emulator.addSeparator();
    MenuItem savestate = new MenuItem("Salvar estado");
    savestate.setShortcut(new MenuShortcut(KeyEvent.VK_F5));
    savestate.setEnabled(false);
    MenuItem loadstate = new MenuItem("Carregar estado");
    loadstate.setShortcut(new MenuShortcut(KeyEvent.VK_F7));
    loadstate.setEnabled(false);
    emulator.add(savestate);
    emulator.add(loadstate);
    saveSlots = new Menu("Slot do save state");
    for(int i=0; i < SaveState.SLOTS; i++) {
      final CheckboxMenuItem slotitem = new CheckboxMenuItem("Slot " + (i + 1), (i + 1) == savestate_slot); // slot 1 is the default
      final int slot = i + 1;
      slotitem.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) { selectSlot(slot); } } );
      slotitems[i] = slotitem;
      saveSlots.add(slotitem);
    }
    emulator.add(saveSlots);
    menubar.add(emulator);
    
    display = new Menu("Tela");
    MenuItem fullscreen = new MenuItem("Tela cheia");
    fullscreen.setShortcut(new MenuShortcut(KeyEvent.VK_ENTER));
    MenuItem originalsize = new MenuItem("Tamanho original");
    originalsize.setShortcut(new MenuShortcut(KeyEvent.VK_1));
    MenuItem doublesize = new MenuItem("Tamanho dobrado");
    doublesize.setShortcut(new MenuShortcut(KeyEvent.VK_2));
    MenuItem triplesize = new MenuItem("Tamanho triplicado");
    triplesize.setShortcut(new MenuShortcut(KeyEvent.VK_3));
    MenuItem quadruplesize = new MenuItem("Tamanho quadruplicado");
    quadruplesize.setShortcut(new MenuShortcut(KeyEvent.VK_4));
    display.add(fullscreen);
    display.addSeparator();
    display.add(originalsize);
    display.add(doublesize);
    display.add(triplesize);
    display.add(quadruplesize);
    menubar.add(display);
    
    sound = new Menu("Som");
    CheckboxMenuItem enablesound = new CheckboxMenuItem("Ligar/desligar som");
    enablesound.setShortcut(new MenuShortcut(KeyEvent.VK_S));
    enablesound.setState(psg.enabled);
    CheckboxMenuItem crappysync = new CheckboxMenuItem("Sincronizar som (experimental)");
    crappysync.setState(psg.crappy_sync);
    CheckboxMenuItem soundchan0 = new CheckboxMenuItem("Ligar/Desligar canal 0");
    soundchan0.setState(psg.chan0);
    CheckboxMenuItem soundchan1 = new CheckboxMenuItem("Ligar/Desligar canal 1");
    soundchan1.setState(psg.chan1);
    CheckboxMenuItem soundchan2 = new CheckboxMenuItem("Ligar/Desligar canal 2");
    soundchan2.setState(psg.chan2);
    CheckboxMenuItem soundchan3 = new CheckboxMenuItem("Ligar/Desligar canal 3 (ruído)");
    soundchan3.setState(psg.chan3);
    sound.add(enablesound);
    sound.add(crappysync);
    sound.addSeparator();
    sound.add(soundchan0);
    sound.add(soundchan1);
    sound.add(soundchan2);
    sound.add(soundchan3);
    menubar.add(sound);
    
    help = new Menu("Ajuda"); // Set up help menu
    MenuItem about = new MenuItem("Sobre");
    help.add(about);
    menubar.add(help);
    
    // File Menu
    openRom.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
    	if(z80 != null) z80.msg.setVisible(false);
    	if(cart.isLoaded()) memory.saveCartRam();
    	cart.unload();
    	cart.showOpenDialog(remaster);
        if(cart.isLoaded()) { emulator.getItem(0).setLabel("Pause emulation"); startEmulation(); } 
        drawsurface.requestFocus(); } } );
    closeRom.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
    	pauseEmulation();
    	cart.unload();
    	screen.clearBuffer();
    	memory.saveCartRam();
    	setTitle(Remaster.APPNAME);
    	drawsurface.requestFocus(); } } );
    exit.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { exit(); } } );
    // Emulation menu
    pauseresume.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) {
		if(mainloop != null) { pauseEmulation(); drawsurface.requestFocus(); }
		else { resumeEmulation(); emulator.getItem(0).setLabel("Pausar emulação"); drawsurface.requestFocus(); } } } );
    pausebutton.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
    	if(z80 != null) z80.setNMI();
    	drawsurface.requestFocus(); } } );
    vramview.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) {
    	if(vramviewer.enabled) { vramviewer.toggleEnabled(); drawsurface.requestFocus(); }
    	else { vramviewer.toggleEnabled(); drawsurface.requestFocus(); } } } );
    cramview.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) {
    	if(cramviewer.enabled) { cramviewer.toggleEnabled(); drawsurface.requestFocus(); }
    	else { cramviewer.toggleEnabled(); drawsurface.requestFocus(); } } } );
    debug.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) {
    	if(debugger.enabled) { debugger.toggleEnabled(); drawsurface.requestFocus(); }
    	else { debugger.toggleEnabled(); drawsurface.requestFocus(); } } } );
    savestate.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { saveGameState(); } } );
    loadstate.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { loadGameState(); } } );
    // Display menu
    fullscreen.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { screen.toggleFullScreen(remaster);	} } );
    originalsize.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { setWindowSize(1); } } );
    doublesize.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { setWindowSize(2); } } );
    triplesize.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { setWindowSize(3); } } );
    quadruplesize.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { setWindowSize(4); } } );
    // Sound menu
    enablesound.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) { psg.enabled = !psg.enabled; } } );
    crappysync.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) { psg.crappy_sync = !psg.crappy_sync; } } );
    soundchan0.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) { psg.chan0 = !psg.chan0; } } );
    soundchan1.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) { psg.chan1 = !psg.chan1; } } );
    soundchan2.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) { psg.chan2 = !psg.chan2; } } );
    soundchan3.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) { psg.chan3 = !psg.chan3; } } );
    // Help menu
    about.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { aboutFrame.setVisible(true); } } );
    
    return menubar;
  }
  
  public void setWindowSize(int scale) {
  	Insets in = getInsets();   // exact canvas size = (256 x 192) * scale + window borders
  	setSize(in.left + in.right + 256 * scale, in.top + in.bottom + 192 * scale); validate();
  }
  
  public static void main(String args[])
  {
    Remaster remaster = new Remaster();

    if(args.length > 0) {
      if(remaster.cart.loadRom(args[0])) {
        remaster.emulator.getItem(0).setLabel("Pause emulation");
        remaster.startEmulation();
      }
      else {
        System.out.println("Usage: java Remaster [romfile.sms]");
      }
    }
  }
}

class AboutFrame extends Frame {
	ScrollPane scrollpanel;
	TextArea text;
	Button bt_ok;
	
	AboutFrame() {
		super("Remaster - Sobre o programa...");
		
		bt_ok = new Button("Ok");
		bt_ok.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { setVisible(false); } } );
		
		scrollpanel = new ScrollPane(ScrollPane.SCROLLBARS_AS_NEEDED);
		text = new TextArea();
		scrollpanel.add(text);
		
		text.append(Remaster.APPNAME + " - Emulador de SEGA Master System feito em Java.");
		text.append("\n--------------------------------------------------------------------------------------------");
		text.append("\nDesenvolvido em 2003-2004 por André Luiz Veltroni Sanches (alvs).");
		text.append("\nE-mail: andre.alvs@gmail.com");
		text.append("\nICQ: 69444232");
		text.append("\n");
		text.append("\n:: Colaboradores ::");
		text.append("\n--------------------------");
		text.append("\n  - Marcelo Abreu (skewer) - conceitos e técnicas.");
		text.append("\n  - gamer_boy - teste.");
		text.append("\n  - Todos do canal #emuroms na Brasnet (irc.brasnet.org)");
		text.append("\n  - Todos do forum de desenvolvimento SMSPower (www.smspower.org)");
		text.append("\n");
		text.append("\n:: Requisitos básicos ::");
		text.append("\n--------------------------------");
		text.append("\n  - Processador de 1 Ghz ou superior;");
		text.append("\n  - 128Mb de memória RAM;");
		text.append("\n  - Java Runtime Environment versão 1.3.2 ou superior;");
		text.append("\n");
		text.append("\n:: Como jogar ::");
		text.append("\n---------------------");
		text.append("\n  Os joysticks são emulados somente no teclado, na seguinte");
		text.append("\n  configuração:");
		text.append("\n  - Tecla Z: botão 2 do joystick 1;");
		text.append("\n  - Tecla X: botão 1 do joystick 1;");
		text.append("\n  - ESC: Reset;");
		text.append("\n  - Tecla P: Botão Pause do console (gera uma interrupção NMI);");
		text.append("\n  - Barra de espaço: Interrompe/continua execução;");
		text.append("\n  - Tecla F5: salva o estado do jogo no slot selecionado;");
		text.append("\n  - Tecla F7: restaura o estado do jogo do slot selecionado;");
		text.append("\n  - Menu Emulação > Slot do save state: escolhe um dos seis slots; o slot 1 é o padrão;");
		text.append("\n");
		text.append("\n:: Características das versões ::");
		text.append("\n--------------------------------------------");
		text.append("\n*** v0.01:");
		text.append("\n  - Melhoras na criação do buffer gráfico, compativel com as configurações");
		text.append("\n    atuais de cores do desktop.");
		text.append("\n  - Melhora no esquema de sincronização do som com a jogabilidade.");
		text.append("\n  - Versão final para a entrega do Trabalho de Conclusão de Curso.");
		text.append("\n");
		text.append("\n*** v0.00:");
		text.append("\n  - Atualmente, o Remaster emula somente o master system na versão");
		text.append("\n    NTSC 224x190.");
		text.append("\n  - Emulação do canal gerador de ruido branco ainda nao implementado");
		text.append("\n  - Compatibilidade de aproximadamente 60% dos jogos.");		
		text.append("\n  - Primeiro lançamento privado, entregue somente para beta-testers.");
		text.append("\n");
		
		this.setLayout(new BorderLayout());
		add(scrollpanel, BorderLayout.CENTER);
		add(bt_ok, BorderLayout.SOUTH);
		
		addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { setVisible(false); } } );
		
		setLocation((Toolkit.getDefaultToolkit().getScreenSize().width / 2) - 225, (Toolkit.getDefaultToolkit().getScreenSize().height / 2) - 175);
		setSize(450, 350);
		setResizable(false);
	}
}
