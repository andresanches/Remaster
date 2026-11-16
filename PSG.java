import javax.sound.sampled.*;

public final class PSG {
	int regs0, regs1, regs2, regs3;                 // channel data
	int vol0, vol1, vol2, vol3;                     // channel volume
	int polarity0, polarity1, polarity2, polarity3; // output polarity of each channel (1 or -1)
	int counter0, counter1, counter2, counter3;     // clock counter for each channel
	int noise_sr = 0x8000;                          // noise generator shift register (16-bit LFSR)
	int[] noise_freq = {0x10, 0x20, 0x40};          // noise counter reset values for the three fixed shift rates
	int clock = 3579545 / 16;                       // clock frequency (NTSC only)
	int tick  = (clock / 60) / 735;                 // how many ticks in a frame
	int current;                                    // currently latched channel
	boolean crappy_sync = true;                     // crappy sound synchronization method (experimental)

	AudioFormat format;
	SourceDataLine line;
	DataLine.Info info;
    byte[] buffer; // buffer of sound for each frame
    int sampleRate = 44100; // 44.1 KHz for sampling rate
    int bufferLength = 735; // Length is sampleRate / Frames_per_second (60 for NTSC)
    int bufsiz;
	public boolean enabled, chan0, chan1, chan2, chan3; // Enabled/disabled flags for sound and channels
	
	public PSG() {
		enabled = true; // enable sound ...
		chan0 = true; chan1 = true; chan2 = true; chan3 = true; // ... and all channels

		// Set up audio data line for output to speakers
		format = new AudioFormat(sampleRate, 8, 1, true, false);
		info = new DataLine.Info(SourceDataLine.class, format);
		if (!AudioSystem.isLineSupported(info)) {
			enabled = false;
			System.out.println("PSG ERROR: Audio format not supported, sound is DISABLED");
		}
		else {
			try {
				line = (SourceDataLine)AudioSystem.getLine(info);
				line.open(format);
				buffer = new byte[bufferLength];
				bufsiz = line.getBufferSize();
				line.start();
			} catch(LineUnavailableException e) { enabled = false; System.out.println("PSG: ERROR - no available audio line"); }
		}

		reset();
	}
	
	public void reset() {
		polarity0 = 1; polarity1 = 1; polarity2 = 1; polarity3 = 1;
		vol0 = 0; vol1 = 0; vol2 = 0; vol3 = 0;
		regs0 = 0; regs1 = 0; regs2 = 0; regs3 = 0;
		noise_sr = 0x8000;
	}

	public void toggleEnabled() {
		enabled = !enabled;
	}
	
	/* public final void write(int value)
	 *   Handles data write to PSG ports at address $7F and mirrored at $7E
	 */
	public final void write(int value) {
		if(!enabled) return;
		
		if((value & 0x80) != 0) {
			current = (value >> 4) & 0x7;
			switch(current) {
				case 0:    // channel 0 data
					regs0 &= ~0xF;
					regs0 = value & 0xF;
					break;
				case 1:    // channel 0 volume
					vol0 = ~value & 0xF;
					break;
				case 2:    // channel 1 data
					regs1 &= ~0xF;
					regs1 = value & 0xF;
					break;
				case 3:    // channel 1 volume
					vol1 = ~value & 0xF;
					break;
				case 4:    // channel 2 data
					regs2 &= ~0xF;
					regs2 = value & 0xF;
					break;
				case 5:    // channel 2 volume
					vol2 = ~value & 0xF;
					break;
				case 6:    // noise data
					regs3 = value & 0x7;
					noise_sr = 0x8000;
					break;
				case 7:    // noise volume
					vol3 = ~value & 0xF;
					break;
			}
		}
		else {
			switch(current) {
				case 0:    // channel 0 data
					regs0 &= 0xF;
					regs0 |= (value & 0x3F) << 4;
					break;
				case 1:    // channel 0 volume
					vol0 = ~value & 0xF;
					break;
				case 2:    // channel 1 data
					regs1 &= 0xF;
					regs1 |= (value & 0x3F) << 4;
					break;
				case 3:    // channel 1 volume
					vol1 = ~value & 0xF;
					break;
				case 4:    // channel 2 data
					regs2 &= 0xF;
					regs2 |= (value & 0x3F) << 4;
					break;
				case 5:    // channel 2 volume
					vol2 = ~value & 0xF;
					break;
				case 6:    // noise data
					regs3 = value & 0x7;
					noise_sr = 0x8000;
					break;
				case 7:    // noise volume
					vol3 = ~value & 0xF;
					break;
			}
		}
	}
	
	/* public final void output()
	 *   Outputs audio data to speakers.
	 */
	public final void output() {
		if(!enabled) return;
		
		for(int i=0; i < bufferLength; i++) {
			// channel 0 - square wave generator
			buffer[i] = 0;
			if(chan0) {
				buffer[i] = (byte)(vol0 * polarity0);
				counter0 -= tick;
				if(counter0 <= 0) { counter0 += regs0 & 0x3FF; polarity0 = (polarity0 == 1 ? -1 : 1); }
			}
			
			// channel 1 - square wave generator
			if(chan1) {
				buffer[i] += (byte)(vol1 * polarity1);
				counter1 -= tick;
				if(counter1 <= 0) { counter1 += regs1 & 0x3FF; polarity1 = (polarity1 == 1 ? -1 : 1); }
			}
			
			// channel 2 - square wave generator
			if(chan2) {
				buffer[i] += (byte)(vol2 * polarity2);
				counter2 -= tick;
				if(counter2 <= 0) { counter2 += regs2 & 0x3FF; polarity2 = (polarity2 == 1 ? -1 : 1); }
			}
			
			if(chan3) {
				buffer[i] += (byte)(vol3 * polarity3);
				counter3 -= tick;
				if(counter3 <= 0) {
					counter3 += ((regs3 & 0x3) == 0x3) ? (regs2 & 0x3FF) << 1 : noise_freq[regs3 & 0x3] << 1;
					int feedback = ((regs3 & 0x4) != 0) ? (noise_sr & 1) ^ ((noise_sr >> 3) & 1) : (noise_sr & 1);
					noise_sr = (noise_sr >> 1) | (feedback << 15);
					polarity3 = ((noise_sr & 1) == 1) ? 1 : -1;
				}
			}
		}
		
		line.write(buffer, 0, bufferLength); // output data to speakers
	}
}
