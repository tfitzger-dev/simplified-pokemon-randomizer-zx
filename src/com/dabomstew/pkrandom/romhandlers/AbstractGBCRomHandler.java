package com.dabomstew.pkrandom.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  AbstractGBCRomHandler.java - an extension of AbstractGBRomHandler     --*/
/*--                               used for Gen 1 and Gen 2.                --*/
/*--                                                                        --*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Originally part of "Universal Pokemon Randomizer" by Dabomstew        --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2020.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import com.dabomstew.pkrandom.FileFunctions;
import com.dabomstew.pkrandom.constants.GBConstants;

public abstract class AbstractGBCRomHandler extends AbstractGBRomHandler {

    private String[] tb;
    private Map<String, Byte> d;
    private int longestTableToken;

    public AbstractGBCRomHandler(Random random) {
        super(random);
    }

    protected void clearTextTables() {
        tb = new String[256];
        d = new HashMap<String, Byte>();
        longestTableToken = 0;
    }

    protected void readTextTable(String name) {
        try {
            Scanner sc = new Scanner(FileFunctions.openConfig(name + ".tbl"), "UTF-8");
            while (sc.hasNextLine()) {
                String q = sc.nextLine();
                if (!q.trim().isEmpty()) {
                    String[] r = q.split("=", 2);
                    int hexcode = Integer.parseInt(r[0], 16);
                    if (tb[hexcode] != null) {
                        String oldMatch = tb[hexcode];
                        tb[hexcode] = null;
                        if (d.get(oldMatch) == hexcode) {
                            d.remove(oldMatch);
                        }
                    }
                    tb[hexcode] = r[1];
                    longestTableToken = Math.max(longestTableToken, r[1].length());
                    d.put(r[1], (byte) hexcode);
                }
            }
            sc.close();
        } catch (FileNotFoundException e) {
        }

    }

    protected String readString(int offset, int maxLength) {
        StringBuilder string = new StringBuilder();
        for (int c = 0; c < maxLength; c++) {
            int currChar = rom[offset + c] & 0xFF;
            if (tb[currChar] != null) {
                string.append(tb[currChar]);
            } else {
                if (currChar == GBConstants.stringTerminator) {
                    break;
                } else {
                    string.append("\\x" + String.format("%02X", currChar));
                }
            }
        }
        return string.toString();
    }

    protected int lengthOfStringAt(int offset, boolean textEngineMode) {
        int len = 0;
        while (rom[offset + len] != GBConstants.stringTerminator
                && (!textEngineMode || (rom[offset + len] != GBConstants.stringPrintedTextEnd && rom[offset + len] != GBConstants.stringPrintedTextPromptEnd))) {
            len++;
        }
        return len;
    }

    protected byte[] translateString(String text) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (text.length() != 0) {
            int i = Math.max(0, longestTableToken - text.length());{
                while (!(d.containsKey(text.substring(0, longestTableToken - i)) || (i == longestTableToken))) {
                    i++;
                }
                {
                    baos.write(d.get(text.substring(0, longestTableToken - i)) & 0xFF);
                    text = text.substring(longestTableToken - i);
                }
            }
        }
        return baos.toByteArray();
    }

    protected String readFixedLengthString(int offset, int length) {
        return readString(offset, length);
    }

    // pads the length with terminators, so length should be at least str's len
    // + 1
    protected void writeFixedLengthString(String str, int offset, int length) {
        byte[] translated = translateString(str);
        int len = Math.min(translated.length, length);
        System.arraycopy(translated, 0, rom, offset, len);
        while (len < length) {
            rom[offset + len] = GBConstants.stringTerminator;
            len++;
        }
    }

    protected void writeVariableLengthString(String str, int offset) {
        byte[] translated = translateString(str);
        System.arraycopy(translated, 0, rom, offset, translated.length);
    }

    protected int makeGBPointer(int offset) {
        return (offset % GBConstants.bankSize) + GBConstants.bankSize;

    }

    protected int bankOf(int offset) {
        return (offset / GBConstants.bankSize);
    }

    protected int calculateOffset(int bank, int pointer) {
        return (pointer % GBConstants.bankSize) + bank * GBConstants.bankSize;

    }

    protected String readVariableLengthString(int offset) {
        return readString(offset, Integer.MAX_VALUE);
    }

    protected static boolean romSig(byte[] rom, String sig) {
        try {
            int sigOffset = GBConstants.romSigOffset;
            byte[] sigBytes = sig.getBytes("US-ASCII");
            for (int i = 0; i < sigBytes.length; i++) {
                if (rom[sigOffset + i] != sigBytes[i]) {
                    return false;
                }
            }
            return true;
        } catch (UnsupportedEncodingException ex) {
            return false;
        }

    }

    protected static boolean romCode(byte[] rom, String code) {
        try {
            int sigOffset = GBConstants.romCodeOffset;
            byte[] sigBytes = code.getBytes("US-ASCII");
            for (int i = 0; i < sigBytes.length; i++) {
                if (rom[sigOffset + i] != sigBytes[i]) {
                    return false;
                }
            }
            return true;
        } catch (UnsupportedEncodingException ex) {
            return false;
        }

    }

}
