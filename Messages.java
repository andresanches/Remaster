import java.awt.*;
import java.awt.event.*;

public final class Messages extends Frame {
	TextArea msg;
	
	public Messages() {
		super("Remaster - Mensagens");
		setSize(400, 400);
		setLocation(300, 50);
		msg = new TextArea();
		setLayout(new BorderLayout());
		add(msg, BorderLayout.CENTER);
		
	    addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { setVisible(false); } } );
	}
	
	public void println(String s) {
		msg.append(s + "\n");
	}
}
