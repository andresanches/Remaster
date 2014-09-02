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
  
  MenuBar menubar;
  Menu file;
  Menu emulator;
  Menu display;
  Menu sound;
  Menu help;
  AboutFrame aboutFrame;
  
  static String APPNAME = "Remaster v0.01";
  
  public Remaster() {
    super(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration());
    this.remaster = this;
    
    System.out.println(Remaster.APPNAME);

    setLayout(new BorderLayout());
    setTitle(Remaster.APPNAME);
    setLocation(100, 50);
    setSize(264,238);

    // create drawing surface panel and add it to the main frame
    drawsurface = new DrawSurface(this);
    screen = new Screen(drawsurface, 256, 192);
    screen.clearBuffer();
    add(drawsurface, BorderLayout.CENTER);
    
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
    addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { exit(); } } );
}
  
  public void startEmulation() {
    setTitle(Remaster.APPNAME + " - " + cart.getFileName());
    
    // Enable menu items
    for(int i=0; i < file.getItemCount(); i++)     file.getItem(i).enable();
    for(int i=0; i < emulator.getItemCount(); i++) emulator.getItem(i).enable();
    
    if(mainloop != null) { mainloop.stopEmulation(); mainloop = null; }

    mainloop = new MainThread(screen, cart, memory, vdp, psg, ports, joy, z80, debugger, vramviewer, cramviewer);
    mainloop.SMS_reset();
    
    mainloop.start();   // Start emulation
  }
  
  public void exit() {
  	if(mainloop != null) {
  		mainloop.stopEmulation();
  		//memory.dumpMemory();
  		//vdp.dumpMemory();
  	}
  	System.exit(0);
  }
  
  public void paint(Graphics g) {
  	if(mainloop != null) screen.drawScreen(0, 0);
  	else {
  		drawsurface.getGraphics().setColor(new Color(0));
  		drawsurface.getGraphics().fillRect(0, 0, drawsurface.getWidth(), drawsurface.getHeight());
  		drawsurface.repaint();
  	}
  }
  
  public MenuBar setup_MenuBar() {
  	MenuBar menubar = new MenuBar();
  	
    file = new Menu("Arquivo"); // Set up File menu
    MenuItem openRom  = new MenuItem("Abrir ROM");
    openRom.setShortcut(new MenuShortcut(KeyEvent.VK_O));
    MenuItem closeRom = new MenuItem("Fechar ROM");
    closeRom.setShortcut(new MenuShortcut(KeyEvent.VK_C));
    closeRom.disable();
    MenuItem exit =     new MenuItem("Sair");
    exit.setShortcut(new MenuShortcut(KeyEvent.VK_X));
    file.add(openRom);
    file.add(closeRom);
    file.addSeparator();
    file.add(exit);
    menubar.add(file);
    
    emulator = new Menu("Emulação"); // Set up Emulator menu
    CheckboxMenuItem pauseresume = new CheckboxMenuItem("Pause");
    pauseresume.disable();
    CheckboxMenuItem vramview =    new CheckboxMenuItem("Habilitar visualizador de tiles (VRAM)");
    vramview.disable();
    CheckboxMenuItem cramview =    new CheckboxMenuItem("Habilitar visualizador da paleta de cores (CRAM)");
    cramview.disable();
    CheckboxMenuItem debug =       new CheckboxMenuItem("Habilitar Debugger");
    debug.disable();
    emulator.add(pauseresume);
    emulator.addSeparator();
    emulator.add(vramview);
    emulator.add(cramview);
    emulator.addSeparator();
    emulator.add(debug);
    menubar.add(emulator);
    
    display = new Menu("Tela");
    MenuItem fullscreen = new MenuItem("Tela cheia");
    fullscreen.setShortcut(new MenuShortcut(KeyEvent.VK_ENTER));
    MenuItem originalsize = new MenuItem("Tamanho original");
    originalsize.setShortcut(new MenuShortcut(KeyEvent.VK_1));
    MenuItem doublesize = new MenuItem("Tamanho dobrado");
    doublesize.setShortcut(new MenuShortcut(KeyEvent.VK_2));
    display.add(fullscreen);
    display.addSeparator();
    display.add(originalsize);
    display.add(doublesize);
    menubar.add(display);
    
    sound = new Menu("Som");
    CheckboxMenuItem enablesound = new CheckboxMenuItem("Ligar/desligar som");
    enablesound.setShortcut(new MenuShortcut(KeyEvent.VK_S));
    enablesound.setState(psg.enabled);
    CheckboxMenuItem crappysync = new CheckboxMenuItem("Sincronizar som (experimental)");
    crappysync.setState(psg.crappy_sync);
    CheckboxMenuItem soundchan0 = new CheckboxMenuItem("Ligar/Desligar canal 0");
    soundchan0.setState(psg.chan0);
    CheckboxMenuItem soundchan1 = new CheckboxMenuItem("Ligar/Desligar canal 0");
    soundchan1.setState(psg.chan1);
    CheckboxMenuItem soundchan2 = new CheckboxMenuItem("Ligar/Desligar canal 0");
    soundchan2.setState(psg.chan2);
    sound.add(enablesound);
    sound.add(crappysync);
    sound.addSeparator();
    sound.add(soundchan0);
    sound.add(soundchan1);
    sound.add(soundchan2);  
    menubar.add(sound);
    
    help = new Menu("Ajuda"); // Set up help menu
    MenuItem about = new MenuItem("Sobre");
    help.add(about);
    menubar.add(help);
    
    // File Menu
    openRom.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
    	if(z80 != null) z80.msg.setVisible(false);
    	cart.unload();
    	cart.showOpenDialog(remaster);
        if(cart.isLoaded()) { emulator.getItem(0).setLabel("Pause emulation"); startEmulation(); } 
        drawsurface.requestFocus(); } } );
    closeRom.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) {
    	cart.unload();
    	screen.clearBuffer();
    	if(mainloop != null) { mainloop.stopEmulation(); mainloop = null; }
    	setTitle(Remaster.APPNAME);
    	drawsurface.requestFocus(); } } );
    exit.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { exit(); } } );
    // Emulation menu
    pauseresume.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) {
		if(mainloop != null) { mainloop.stopEmulation(); mainloop = null; drawsurface.requestFocus(); }
		else { mainloop = new MainThread(screen, cart, memory, vdp, psg, ports, joy, z80, debugger, vramviewer, cramviewer); mainloop.start(); emulator.getItem(0).setLabel("Pausar emulação"); drawsurface.requestFocus(); } } } );
    vramview.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) {
    	if(vramviewer.enabled) { vramviewer.toggleEnabled(); drawsurface.requestFocus(); }
    	else { vramviewer.toggleEnabled(); drawsurface.requestFocus(); } } } );
    cramview.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) {
    	if(cramviewer.enabled) { cramviewer.toggleEnabled(); drawsurface.requestFocus(); }
    	else { cramviewer.toggleEnabled(); drawsurface.requestFocus(); } } } );
    debug.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) {
    	if(debugger.enabled) { debugger.toggleEnabled(); drawsurface.requestFocus(); }
    	else { debugger.toggleEnabled(); drawsurface.requestFocus(); } } } );
    // Display menu
    fullscreen.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { screen.toggleFullScreen(remaster);	} } );
    originalsize.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { setSize(264, 238); validate(); } } );
    doublesize.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { setSize(520, 430); validate(); } } );
    // Sound menu
    enablesound.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) { psg.enabled = !psg.enabled; } } );
    crappysync.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) { psg.crappy_sync = !psg.crappy_sync; } } );
    soundchan0.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) { psg.chan0 = !psg.chan0; } } );    
    soundchan1.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) { psg.chan1 = !psg.chan1; } } );    
    soundchan2.addItemListener(new ItemListener() { public void itemStateChanged(ItemEvent e) { psg.chan2 = !psg.chan2; } } );
    // Help menu
    about.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { aboutFrame.setVisible(true); } } );
    
    return menubar;
  }
  
  public static void main(String args[])
  {
    new Remaster();
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
		//scrollpanel.setBackground(new Color(0, 0, 0));
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
		text.append("\n  - Barra de espaço: Interrompe/continua execução;");
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