public final class Joystick {
	public int byte1, byte2;
	public static final int JOY1_UP    = 0x01;
	public static final int JOY1_DOWN  = 0x02;
	public static final int JOY1_LEFT  = 0x04;
	public static final int JOY1_RIGHT = 0x08;
	public static final int JOY1_FIREA = 0x10;
	public static final int JOY1_FIREB = 0x20;
	public static final int JOY2_UP    = 0x40;
	public static final int JOY2_DOWN  = 0x80;
	public static final int JOY2_LEFT  = 0x01;
	public static final int JOY2_RIGHT = 0x02;
	public static final int JOY2_FIREA = 0x04;
	public static final int JOY2_FIREB = 0x08;
	public static final int RESET      = 0x10;
	
	public Joystick() {
		byte1 = 0xFF;
		byte2 = 0xFF;
	}
	
	public int port1_read() {
		return byte1;
	}
	public int port2_read() {
		return byte2;
	}
}
