Remaster
========

SEGA Master System/Mark III emulator writtten 100% in Java. This project was created as a partial requirement for the completion of my B.Sc. degree in Computer Science.

Features
========

* All documented instructions in the Z80 microprocessor are emulated and pass all ZEXALL tests
* Full graphics chip emulation (VDP) with line-by-line rendering which reproduce with high fidelity certain scanline interrupt effects in games such as parallax scrolling
* Basic sound (PSG) emulation: the three square wave tone generators are implemented, the white noise generator was skipped because of time constraints
* A basic debugger displays CPU, VDP and PSG register values in real time
* A basic Video RAM viewer allows for displaying visual tiles in real time
* A basic palette viewer allows for displaying the current active collors in real time

Controls
========

* Keyboard arrows: directional pad
* Key X: button #1
* Key Z: button #2
* Space bar: Pause

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
