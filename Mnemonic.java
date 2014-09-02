/**
 * Mnemonic.java
 *
 * Z80 Opcode Mnemonics
 *
 * @author Copyright (C) 2002 Chris White
 * @version 17th March 2002
 * @see "JavaGear Final Project Report"
 */

 /*
    This file is part of JavaGear.

    JavaGear is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    JavaGear is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with JavaGear; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

public final class Mnemonic
{
	public Mnemonic()
	{}

	public String getOP(int opcode)
	{
			return opcodes[opcode&0xff];
	}

	public String getCB(int opcode)
	{
		return cbopcodes[opcode&0xff];
	}

	public String getED(int opcode)
	{
		return edopcodes[opcode&0xff];
	}

	public String getIndex(int opcode)
	{
		return indexopcodes[opcode&0xff];
	}

	private String[] opcodes =
	{
		"NOP",			"LD BC,nn",	"LD (BC),A",	"INC BC",	"INC B",		"DEC B",		"LD B, n",	"RLCA",
		"EX AF,AF",		"ADD HL,BC","LD A,(BC)",	"DEC BC",	"INC C",		"DEC C",		"LD C, n",	"RRCA",
		"DJNZ",			"LD DE,nn",	"LD (DE),A",	"INC DE",	"INC D",		"DEC D",		"LD D, n",	"RLA",
		"JR",			"ADD HL,DE","LD A,(DE)",	"DEC DE",	"INC E",		"DEC E",		"LD E, n",	"RRA",
		"JR NZ,(PC+e)",	"LD HL,nn",	"LD (nn),HL",	"INC HL",	"INC H",		"DEC H",		"LD H, n",	"DAA",
		"JR Z,(PC+e)",	"ADD HL,HL","LD HL,nn",		"DEC HL",	"INC L",		"DEC L",		"LD L, n",	"CPL",
		"JR NC,(PC+e)",	"LD SP,nn",	"LD (nn),A",	"INC SP",	"INC (HL)",		"DEC (HL)",		"LD (HL),", "SCF",
		"JR C,(PC+e)",	"ADD HL,SP","LD A,nn",		"DEC SP",	"INC A",		"DEC A",		"LD A, n",	"CCF",
		"LD B,B",		"LD B,C",	"LD B,D",		"LD B,E",	"LD B,H",		"LD B,L",		"LD B,(HL)","LD B,A",
		"LD C,B",		"LD C,C",	"LD C,D",		"LD C,E",	"LD C,H",		"LD C,L",		"LD C,(HL)","LD C,A",
		"LD D,B",		"LD D,C",	"LD D,D",		"LD D,E",	"LD D,H",		"LD D,L",		"LD D,(HL)","LD D,A",
		"LD E,B",		"LD E,C",	"LD E,D",		"LD E,E",	"LD E,H",		"LD E,L",		"LD E,(HL)","LD E,A",
		"LD H,B",		"LD H,C",	"LD H,D",		"LD H,E",	"LD H,H",		"LD H,L",		"LD H,(HL)","LD H,A",
		"LD L,B",		"LD L,C",	"LD L,D",		"LD L,E",	"LD L,H",		"LD L,L",		"LD L,(HL)","LD L,A",
		"LD (HL),B",	"LD (HL),C","LD (HL),D",	"LD (HL),E","LD (HL),H",	"LD (HL),L",	"HALT",		"LD (HL),A",
		"LD A,B",		"LD A,C",	"LD A,D",		"LD A,E",	"LD A,H",		"LD A,L",		"LD A,(HL)","LD A,A",
		"ADD A,B",		"ADD A,C",	"ADD A,D",		"ADD A,E",	"ADD A,H",		"ADD A,L",		"ADD A,(HL)","ADD A,A",
		"ADC A,B",		"ADC A,C",	"ADC A,D",		"ADC A,E",	"ADC A,H",		"ADC A,L",		"ADC A,(HL)","ADC A,A",
		"SUB B",		"SUB C",	"SUB D",		"SUB E",	"SUB H",		"SUB L",		"SUB (HL)",	"SUB A",
		"SBC A,B",		"SBC A,C",	"SBC A,D",		"SBC A,E",	"SBC A,H",		"SBC A,L",		"SBC A,(HL)","SBC A,A",
		"AND B",		"AND C",	"AND D",		"AND E",	"AND H",		"AND L",		"AND (HL)",	"AND A",
		"XOR B",		"XOR C",	"XOR D",		"XOR E",	"XOR H",		"XOR L",		"XOR (HL)",	"XOR A",
		"OR B",			"OR C",		"OR D",			"OR E",		"OR H",			"OR L",			"OR (HL)",	"OR A",
		"CP B",			"CP C",		"CP D",			"CP E",		"CP H",			"CP L",			"CP (HL)",	"CP A",
		"RET NZ",		"POP BC",	"JP NZ,(nn)",	"JP (nn)",	"CALL NZ,(nn)",	"PUSH BC",		"ADD A,",	"RST 0H",
		"RET Z",		"RET",		"JP Z,(nn)",	"CB OPCODE","CALL Z,(nn)",	"CALL (nn)",	"ADC A,",	"RST 8H",
		"RET NC",		"POP DE",	"JP NC,(nn)",	"OUT (n),A","CALL NC,(nn)",	"PUSH DE",		"SUB n",	"RST 10H",
		"RET C",		"EXX",		"JP C,(nn)",	"IN A,(n)",	"CALL C,(nn)",	"DD OPCODE",	"SBC A,",	"RST 18H",
		"RET PO",		"POP HL",	"JP PO,(nn)",	"EX (SP),HL","CALL PO,(nn)","PUSH HL",		"AND n",	"RST 20H",
		"RET PE",		"JP (HL)",	"JP PE,(nn)",	"EX DE,HL",	"CALL PE,(nn)",	"ED OPCODE",	"XOR n",	"RST 28H",
		"RET P",		"POP AF",	"JP P,(nn)",	"DI",		"CALL P,(nn)",	"PUSH AF",		"OR n",		"RST 30H",
		"RET M",		"LD SP,HL",	"JP M,(nn)",	"EI",		"CALL M,(nn)",	"FD OPCODE",	"CP n",		"RST 38H"
	};

	private String[] cbopcodes =
	{
		 "RLC B",	"RLC C",	"RLC D",	"RLC E",	"RLC H",	"RLC L",	"RLC (HL)",	"RLC A",
         "RRC B",	"RRC C",	"RRC D",	"RRC E",	"RRC H",	"RRC L",	"RRC (HL)",	"RRC A",
         "RL B",	"RL C",		"RL D",		"RL E",		"RL H",		"RL L",		"RL (HL)",	"RL A",
         "RR B",	"RR C",		"RR D",		"RR E",		"RR H",		"RR L",		"RR (HL)",	"RR A",
         "SLA B",	"SLA C",	"SLA D",	"SLA E",	"SLA H",	"SLA L",	"SLA (HL)",	"SLA A",
         "SRA B",	"SRA C",	"SRA D",	"SRA E",	"SRA H",	"SRA L",	"SRA (HL)",	"SRA A",
         "SLL B",	"SLL C",	"SLL D",	"SLL E",	"SLL H",	"SLL L",	"SLL (HL)",	"SLL A",
         "SRL B",	"SRL C",	"SRL D",	"SRL E",	"SRL H",	"SRL L",	"SRL (HL)",	"SRL A",
         "BIT 0,B",	"BIT 0,C",	"BIT 0,D",	"BIT 0,E",	"BIT 0,H",	"BIT 0,L",	"BIT 0,(HL)","BIT 0,A",
         "BIT 1,B",	"BIT 1,C",	"BIT 1,D",	"BIT 1,E",	"BIT 1,H",	"BIT 1,L",	"BIT 1,(HL)","BIT 1,A",
         "BIT 2,B",	"BIT 2,C",	"BIT 2,D",	"BIT 2,E",	"BIT 2,H",	"BIT 2,L",	"BIT 2,(HL)","BIT 2,A",
         "BIT 3,B",	"BIT 3,C",	"BIT 3,D",	"BIT 3,E",	"BIT 3,H",	"BIT 3,L",	"BIT 3,(HL)","BIT 3,A",
         "BIT 4,B",	"BIT 4,C",	"BIT 4,D",	"BIT 4,E",	"BIT 4,H",	"BIT 4,L",	"BIT 4,(HL)","BIT 4,A",
         "BIT 5,B",	"BIT 5,C",	"BIT 5,D",	"BIT 5,E",	"BIT 5,H",	"BIT 5,L",	"BIT 5,(HL)","BIT 5,A",
         "BIT 6,B",	"BIT 6,C",	"BIT 6,D",	"BIT 6,E",	"BIT 6,H",	"BIT 6,L",	"BIT 6,(HL)","BIT 6,A",
         "BIT 7,B",	"BIT 7,C",	"BIT 7,D",	"BIT 7,E",	"BIT 7,H",	"BIT 7,L",	"BIT 7,(HL)","BIT 7,A",
         "RES 0,B",	"RES 0,C",	"RES 0,D",	"RES 0,E",	"RES 0,H",	"RES 0,L",	"RES 0,(HL)","RES 0,A",
         "RES 1,B",	"RES 1,C",	"RES 1,D",	"RES 1,E",	"RES 1,H",	"RES 1,L",	"RES 1,(HL)","RES 1,A",
         "RES 2,B",	"RES 2,C",	"RES 2,D",	"RES 2,E",	"RES 2,H",	"RES 2,L",	"RES 2,(HL)","RES 2,A",
         "RES 3,B",	"RES 3,C",	"RES 3,D",	"RES 3,E",	"RES 3,H",	"RES 3,L",	"RES 3,(HL)","RES 3,A",
         "RES 4,B",	"RES 4,C",	"RES 4,D",	"RES 4,E",	"RES 4,H",	"RES 4,L",	"RES 4,(HL)","RES 4,A",
         "RES 5,B",	"RES 5,C",	"RES 5,D",	"RES 5,E",	"RES 5,H",	"RES 5,L",	"RES 5,(HL)","RES 5,A",
         "RES 6,B",	"RES 6,C",	"RES 6,D",	"RES 6,E",	"RES 6,H",	"RES 6,L",	"RES 6,(HL)","RES 6,A",
         "RES 7,B",	"RES 7,C",	"RES 7,D",	"RES 7,E",	"RES 7,H",	"RES 7,L",	"RES 7,(HL)","RES 7,A",
         "SET 0,B",	"SET 0,C",	"SET 0,D",	"SET 0,E",	"SET 0,H",	"SET 0,L",	"SET 0,(HL)","SET 0,A",
         "SET 1,B",	"SET 1,C",	"SET 1,D",	"SET 1,E",	"SET 1,H",	"SET 1,L",	"SET 1,(HL)","SET 1,A",
         "SET 2,B",	"SET 2,C",	"SET 2,D",	"SET 2,E",	"SET 2,H",	"SET 2,L",	"SET 2,(HL)","SET 2,A",
         "SET 3,B",	"SET 3,C",	"SET 3,D",	"SET 3,E",	"SET 3,H",	"SET 3,L",	"SET 3,(HL)","SET 3,A",
         "SET 4,B",	"SET 4,C",	"SET 4,D",	"SET 4,E",	"SET 4,H",	"SET 4,L",	"SET 4,(HL)","SET 4,A",
         "SET 5,B",	"SET 5,C",	"SET 5,D",	"SET 5,E",	"SET 5,H",	"SET 5,L",	"SET 5,(HL)","SET 5,A",
         "SET 6,B",	"SET 6,C",	"SET 6,D",	"SET 6,E",	"SET 6,H",	"SET 6,L",	"SET 6,(HL)","SET 6,A",
         "SET 7,B",	"SET 7,C",	"SET 7,D",	"SET 7,E",	"SET 7,H",	"SET 7,L",	"SET 7,(HL)","SET 7,A",

      };

	private String[] edopcodes =
	{
		 "?"       ,"?"        ,"?"        ,"?",		"?"  	,"?"   ,"?"   ,"?",
		 "?"       ,"?"        ,"?"        ,"?",		"?"  	,"?"   ,"?"   ,"?",
		 "?"       ,"?"        ,"?"        ,"?",		"?"  	,"?"   ,"?"   ,"?",
		 "?"       ,"?"        ,"?"        ,"?",		"?"  	,"?"   ,"?"   ,"?",
		 "?"       ,"?"        ,"?"        ,"?",		"?"  	,"?"   ,"?"   ,"?",
		 "?"       ,"?"        ,"?"        ,"?",        "?"  	,"?"   ,"?"   ,"?",
		 "?"       ,"?"        ,"?"        ,"?",        "?"  	,"?"   ,"?"   ,"?",
		 "?"       ,"?"        ,"?"        ,"?",        "?"  	,"?"   ,"?"   ,"?",
		 "IN B,(C)","OUT (C),B","SBC HL,BC","LD (nn),BC","NEG"	,"RETN","IM 0","LD I,A",
		 "IN C,(C)","OUT (C),C","ADC HL,BC","LD BC,(nn)","?"  	,"RETI","?"   ,"LD R,A",
		 "IN D,(C)","OUT (C),D","SBC HL,DE","LD (nn),DE","?"  	,"?"   ,"IM 1","LD A,I",
		 "IN E,(C)","OUT (C),E","ADC HL,DE","LD DE,(nn)","?"  	,"?"   ,"IM 2","LD A,R",
		 "IN H,(C)","OUT (C),H","SBC HL,HL","LD (nn),HL","?"  	,"?"   ,"?"   ,"RRD"   ,
		 "IN L,(C)","OUT (C),L","ADC HL,HL","LD HL,(nn)","?"  	,"?"   ,"?"   ,"RLD"   ,
		 "IN 0,(C)","OUT (C),0","SBC HL,SP","LD (nn),SP","?"  	,"?"   ,"?"   ,"?"     ,
		 "IN A,(C)","OUT (C),A","ADC HL,SP","LD SP,(nn)","?"  	,"?"   ,"?"   ,"?"     ,
		 "?"       ,"?"        ,"?"        ,"?"        ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "?"       ,"?"        ,"?"        ,"?"        ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "?"       ,"?"        ,"?"        ,"?"        ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "?"       ,"?"        ,"?"        ,"?"        ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "LDI"     ,"CPI"      ,"INI"      ,"OUTI"     ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "LDD"     ,"CPD"      ,"IND"      ,"OUTD"     ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "LDIR"    ,"CPIR"     ,"INIR"     ,"OTIR"     ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "LDDR"    ,"CPDR"     ,"INDR"     ,"OTDR"     ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "?"       ,"?"        ,"?"        ,"?"        ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "?"       ,"?"        ,"?"        ,"?"        ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "?"       ,"?"        ,"?"        ,"?"        ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "?"       ,"?"        ,"?"        ,"?"        ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "?"       ,"?"        ,"?"        ,"?"        ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "?"       ,"?"        ,"?"        ,"?"        ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "?"       ,"?"        ,"?"        ,"?"        ,"?"  	,"?"   ,"?"   ,"?"     ,
		 "?"       ,"?"        ,"?"        ,"?"        ,"?"  	,"?"   ,"?"   ,"?"
	};

	private String[] indexopcodes =
	{
		"?"			,"?"  		,"?"			,"?"        	,"?"       	,"?"       	,"?"      	,"?",
		"?"      	,"ADD I,BC" ,"?"       		,"?"       		,"?"       	,"?"       	,"?"      	,"?",
		"?"      	,"?"       	,"?"       		,"?"			,"?"       	,"?"       	,"?"      	,"?",
		"?"      	,"ADD I,DE" ,"?"       		,"?"       		,"?"       	,"?"       	,"?"      	,"?",
		"?"      	,"LD I,nn"	,"LD (nn),I"	,"INC I" 		,"INC Ih"  	,"DEC Ih"  	,"LD Ih,B"	,"?",
		"?"      	,"ADD I,I" 	,"LD I,(nn)"	,"DEC I" 		,"INC Il"  	,"DEC Il"  	,"LD Il,B"	,"?",
		"?"      	,"?"       	,"?"       		,"?"        	,"INC I+d"	,"DEC I+d"	,"LD I,B" 	,"?",
		"?"      	,"ADD I,SP"	,"?"       		,"?"        	,"?"       	,"?"       	,"?"       	,"?",
		"?"      	,"?"       	,"?"       		,"?"        	,"LD B,Ih" 	,"LD B,Il" 	,"LD B,I+d","?",
		"?"      	,"?"       	,"?"       		,"?"        	,"LD C,Ih" 	,"LD C,Il" 	,"LD C,I+d","?",
		"?"      	,"?"       	,"?"       		,"?"        	,"LD D,Ih" 	,"LD D,Il" 	,"LD D,I+d","?",
		"?"      	,"?"       	,"?"       		,"?"        	,"LD E,Ih" 	,"LD E,Il" 	,"LD E,I+d","?",
		"LD Ih,B"	,"LD Ih,C" 	,"LD Ih,D" 		,"LD Ih,E"  	,"LD Ih,H" 	,"LD Ih,L" 	,"LD H,I+d","LD Ih,A",
		"LD Il,B"	,"LD Il,C" 	,"LD Il,D" 		,"LD Il,E"  	,"LD Il,H" 	,"LD Il,L" 	,"LD L,I+d","LD Il,A",
		"LD I+d,B"	,"LD I+d,C" ,"LD I+d,D" 	,"LD I+d,E"		,"LD I+d,H"	,"LD I+d,L"	,"?"     	,"LD I+d,A",
		"?"      	,"?"       	,"?"       		,"?"        	,"LD A,Ih" 	,"LD A,Il" 	,"LD A,I+d","?",
		"?"      	,"?"       	,"?"       		,"?"        	,"ADD A,Ih"	,"ADD A,Il"	,"ADD A,I+d","?",
		"?"      	,"?"       	,"?"       		,"?"        	,"ADC A,Ih"	,"ADC A,Il"	,"ADC A,I+d","?",
		"?"      	,"?"       	,"?"       		,"?"        	,"SUB Ih"  	,"SUB Il"  	,"SUB I+d" ,"?",
		"?"      	,"?"       	,"?"       		,"?"        	,"SBC A,Ih"	,"SBC A,Il"	,"SBC A,I+d","?",
		"?"      	,"?"       	,"?"       		,"?"        	,"AND Ih"  	,"AND Il"  	,"AND I+d"	,"?",
		"?"      	,"?"       	,"?"       		,"?"        	,"XOR Ih"  	,"XOR Il"  	,"XOR I+d"	,"?",
		"?"      	,"?"       	,"?"       		,"?"        	,"OR Ih"   	,"OR Il"   	,"OR I+d" 	,"?",
		"?"      	,"?"       	,"?"       		,"?"        	,"CP Ih"   	,"CP Il"   	,"CP I+d" 	,"?",
		"?"      	,"?"       	,"?"       		,"?"        	,"?"       	,"?"       	,"?"      	,"?",
		"?"      	,"?"       	,"?"       		,"FD CB"    	,"?"       	,"?"       	,"?"      	,"?",
		"?"      	,"?"       	,"?"       		,"?"        	,"?"       	,"?"       	,"?"      	,"?",
		"?"      	,"?"       	,"?"       		,"?"        	,"?"       	,"?"       	,"?"      	,"?",
		"?"      	,"POP I"  	,"?"       		,"EX (SP),I"	,"?"       	,"PUSH I" 	,"?"      	,"?",
		"?"      	,"JP (I)" 	,"?"       		,"?"        	,"?"       	,"?"       	,"?"      	,"?",
		"?"      	,"?"       	,"?"       		,"?"        	,"?"       	,"?"       	,"?"      	,"?",
		"?"      	,"LD SP,I"	,"?"       		,"?"        	,"?"       	,"?"       	,"?"      	,"?"
	};
}