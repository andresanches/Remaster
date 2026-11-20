Remaster
========

SEGA Master System/Mark III emulator writtten 100% in Java with zero external dependencies.

This project was created as a partial requirement for the completion of my B.Sc. degree in Computer Science.

Running
=======

```
java -jar Remaster.jar              # then pick a ROM through Arquivo > Abrir ROM
java -jar Remaster.jar game.sms     # load and boot the ROM right away
```

Features
========

* All documented instructions in the Z80 microprocessor are emulated and pass all ZEXALL tests
* Full graphics chip emulation (VDP) with line-by-line rendering which reproduce with high fidelity certain scanline interrupt effects in games such as parallax scrolling
* PSG emulation: all 4 sound channels are emulated, albeit still inaccurate (speech is broken. No "get ready" in Afterburner)
* Battery-backed RAM (SRAM) save games: the save data of games such as Phantasy Star is restored when the ROM is loaded and saved automatically to a .bram file next to the ROM, on a timer and when the ROM is closed or the emulator quits
* Save states: F5 snapshots the whole emulated console (CPU, memory, VDP and PSG) and F7 puts it back, in any of six slots picked in the Emulação menu, slot 1 by default. Each slot is a file next to the ROM (gamename.st1 through gamename.st6), so one game can have six resume points, and a slot is only restorable in the game it was saved from
* A basic debugger displays CPU, VDP, and PSG register values in real time
* A basic Video RAM viewer allows for displaying visual tiles in real time
* A basic palette viewer allows for displaying the current active collors in real time
* Fixed-timestep frame pacing: frame pacing is always controlled by a monotonic clock instead of by the audio buffer, using a larger Java Sound output buffer
* Emulation speed options: 1x, 2x, 3x, and 4x target rates, corresponding to 60, 120, 180, and 240 Hz emulation timing
* All graphics, sound, and input systems are built using Java's standard library. No need for a convoluted build system.

Controls
========

* Keyboard arrows: directional pad
* Key X: button #1
* Key Z: button #2
* Key P: console Pause button (raises a non-maskable interrupt to $0066, as on real hardware)
* Space bar: Pause
* Key F5: save state in the selected slot
* Key F7: restore state from the selected slot
* Emulação > Slot do save state: choose which of the six slots F5 and F7 work on
* Numeric Plus / Numeric Minus: increase/decrease render frameskip
* Keys 1 / 2 / 3 / 4: set emulation speed to 1x, 2x, 3x, or 4x
* Emulação > Velocidade: choose 1x, 2x, 3x, or 4x emulation speed; selecting a speed adjusts render frameskip so display stays near 60 Hz

Observations
============

I learned Java while working in this project so the code is definitely not optimal but works at 100% speed on a 1 GHz Intel Celeron with 256 MB of RAM - the machine I used to do this work.

Copyright and acknowledgements
==============================

This project is no longer in active development but the code may be freely used for non-profit or learning purposes. If you do end up using this I kindly ask to drop me a line, it's always nice to know your work is appreciated.

A huge thanks go out to the following people:

* Marcelo Abreu (skewer) for the patience in teaching me so many basic concepts when I was so inexperienced. 
* gamer_boy from the PO.B.R.E rom hacking website (www.romhackers.org) for testing the emulator and all the motivation you provided while this work was being done.
* The people from the #emuroms IRC channel on the (now defunct) BRASNet network.
* And last but certainly not least thanks to the all the brilliant people at the smspower.org development forums without whom this would never have become a reality. Charles MacDonald, Richard Talbot-Watkins, Marat Fayzullin, Eric Quinn, Maxim, Omar Cornut (Bock), Heliophobe and everyone else who I might have forgotten at the moment.
