package com.dabomstew.pkrandom.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  AbstractGBRomHandler.java - a base class for GB/GBA rom handlers      --*/
/*--                              which standardises common GB(A) functions.--*/
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

import com.dabomstew.pkrandom.FileFunctions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

public abstract class AbstractGBRomHandler extends AbstractRomHandler {

    protected byte[] rom;
    protected byte[] originalRom;

    public AbstractGBRomHandler(Random random) {
        super(random);
    }

    @Override
    public boolean loadRom(String filename) {
        byte[] loaded = loadFile(filename);
        this.rom = loaded;
        this.originalRom = new byte[rom.length];
        System.arraycopy(rom, 0, originalRom, 0, rom.length);
        loadedRom();
        return true;
    }

    @Override
    public boolean saveRomFile(String filename, long seed) {
        savingRom();
        try {
            FileOutputStream fos = new FileOutputStream(filename);
            fos.write(rom);
            fos.close();
            return true;
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public abstract void loadedRom();

    public abstract void savingRom();

    protected static byte[] loadFile(String filename) {
        try {
            return FileFunctions.readFileFullyIntoBuffer(filename);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    protected static byte[] loadFilePartial(String filename, int maxBytes) {
        try {
            File fh = new File(filename);
            long fileSize = fh.length();
            FileInputStream fis = new FileInputStream(filename);
            byte[] file = FileFunctions.readFullyIntoBuffer(fis, Math.min((int) fileSize, maxBytes));
            fis.close();
            return file;
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    protected int readWord(int offset) {
        return readWord(rom, offset);
    }

    protected int readWord(byte[] data, int offset) {
        return (data[offset] & 0xFF) + ((data[offset + 1] & 0xFF) << 8);
    }

    protected void writeWord(int offset, int value) {
        writeWord(rom, offset, value);
    }

    protected void writeWord(byte[] data, int offset, int value) {
        data[offset] = (byte) (value % 0x100);
        data[offset + 1] = (byte) ((value / 0x100) % 0x100);
    }

}
