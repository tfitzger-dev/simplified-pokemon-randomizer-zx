package com.dabomstew.pkrandom.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  Gen1RomHandler.java - randomizer handler for R/B/Y.                   --*/
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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dabomstew.pkrandom.*;
import com.dabomstew.pkrandom.constants.*;
import com.dabomstew.pkrandom.pokemon.*;

public class Gen1RomHandler extends AbstractGBCRomHandler {

    public static class Factory extends RomHandler.Factory {

        @Override
        public Gen1RomHandler create(Random random, PrintStream logStream) {
            return new Gen1RomHandler(random, logStream);
        }

        public boolean isLoadable(String filename) {
            long fileLength = new File(filename).length();
            if (fileLength > 8 * 1024 * 1024) {
                return false;
            }
            byte[] loaded = loadFilePartial(filename, 0x1000);
            // nope
            return loaded.length != 0 && detectRomInner(loaded, (int) fileLength);
        }
    }

    public Gen1RomHandler(Random random, PrintStream logStream) {
        super(random, logStream);
    }

    // Important RBY Data Structures

    private int[] pokeNumToRBYTable;
    private int[] pokeRBYToNumTable;
    private int pokedexCount;

    private Type idToType(int value) {
        Type type = null;
        if (Gen1Constants.typeTable[value] != null) {
            type =  Gen1Constants.typeTable[value];
        }
        return type;
    }

    private byte typeToByte(Type type) {
        return Gen1Constants.typeToByte(type);
    }

    private static class RomEntry {
        private String name;
        private String romName;
        private int version, nonJapanese;
        private String extraTableFile;
        private boolean isYellow;
        private long expectedCRC32 = -1;
        private int crcInHeader = -1;
        private Map<String, String> tweakFiles = new HashMap<>();
        private Map<String, Integer> entries = new HashMap<>();
        private Map<String, int[]> arrayEntries = new HashMap<>();
        private List<StaticPokemon> staticPokemon = new ArrayList<>();
        private int[] ghostMarowakOffsets = new int[0];
        private Map<Type, Integer> extraTypeReverse = new HashMap<>();

        private int getValue(String key) {
            if (!entries.containsKey(key)) {
                entries.put(key, 0);
            }
            return entries.get(key);
        }
    }

    private static List<RomEntry> roms;

    static {
        loadROMInfo();
    }

    private static void loadROMInfo() {
        roms = new ArrayList<>();
        RomEntry current = null;
        try {
            Scanner sc = new Scanner(FileFunctions.openConfig("gen1_offsets.ini"), "UTF-8");
            while (sc.hasNextLine()) {
                String q = sc.nextLine().trim();
                if (q.contains("//")) {
                    q = q.substring(0, q.indexOf("//")).trim();
                }
                if (!q.isEmpty()) {
                    if (q.startsWith("[") && q.endsWith("]")) {
                        // New rom
                        current = new RomEntry();
                        current.name = q.substring(1, q.length() - 1);
                        roms.add(current);
                    } else {
                        String[] r = q.split("=", 2);
                        r[1] = r[1].trim();
                        r[0] = r[0].trim();
                        // Static Pokemon?
                        if (r[0].equals("StaticPokemon{}")) {
                            current.staticPokemon.add(parseStaticPokemon(r[1]));
                        } else if (r[0].equals("StaticPokemonGhostMarowak{}")) {
                            StaticPokemon ghostMarowak = parseStaticPokemon(r[1]);
                            current.staticPokemon.add(ghostMarowak);
                            current.ghostMarowakOffsets = ghostMarowak.speciesOffsets;
                        } else if (r[0].equals("TMText[]")) {
                        } else if (r[0].equals("Game")) {
                            current.romName = r[1];
                        } else if (r[0].equals("Version")) {
                            current.version = parseRIInt(r[1]);
                        } else if (r[0].equals("NonJapanese")) {
                            current.nonJapanese = parseRIInt(r[1]);
                        } else if (r[0].equals("Type")) {
                            current.isYellow = r[1].equalsIgnoreCase("Yellow");
                        } else if (r[0].equals("ExtraTableFile")) {
                            current.extraTableFile = r[1];
                        } else if (r[0].equals("CRCInHeader")) {
                            current.crcInHeader = parseRIInt(r[1]);
                        } else if (r[0].equals("CRC32")) {
                            current.expectedCRC32 = parseRILong("0x" + r[1]);
                        } else if (r[0].endsWith("Tweak")) {
                            current.tweakFiles.put(r[0], r[1]);
                        } else if (r[0].equals("ExtraTypes")) {
                        } else if (r[0].equals("CopyFrom")) {
                            for (RomEntry otherEntry : roms) {
                                if (r[1].equalsIgnoreCase(otherEntry.name)) {
                                    // copy from here
                                    boolean cSP = (current.getValue("CopyStaticPokemon") == 1);
                                    boolean cTT = (current.getValue("CopyTMText") == 1);
                                    current.arrayEntries.putAll(otherEntry.arrayEntries);
                                    current.entries.putAll(otherEntry.entries);
                                    if (cSP) {
                                        current.staticPokemon.addAll(otherEntry.staticPokemon);
                                        current.ghostMarowakOffsets = otherEntry.ghostMarowakOffsets;
                                        current.entries.put("StaticPokemonSupport", 1);
                                    } else {
                                        current.entries.put("StaticPokemonSupport", 0);
                                    }
                                    if (cTT) {
                                    }
                                    current.extraTableFile = otherEntry.extraTableFile;
                                }
                            }
                        } else {
                            if (r[1].startsWith("[") && r[1].endsWith("]")) {
                                String[] offsets = r[1].substring(1, r[1].length() - 1).split(",");
                                {
                                    int[] offs = new int[offsets.length];
                                    int c = 0;
                                    for (String off : offsets) {
                                        offs[c++] = parseRIInt(off);
                                    }
                                    current.arrayEntries.put(r[0], offs);
                                }

                            } else {
                                int offs = parseRIInt(r[1]);
                                current.entries.put(r[0], offs);
                            }
                        }
                    }
                }
            }
            sc.close();
        } catch (FileNotFoundException e) {
            System.err.println("File not found!");
        }

    }

    private static StaticPokemon parseStaticPokemon(String staticPokemonString) {
        StaticPokemon sp = new StaticPokemon();
        String pattern = "[A-z]+=\\[(0x[0-9a-fA-F]+,?\\s?)+]";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(staticPokemonString);
        while (m.find()) {
            String[] segments = m.group().split("=");
            String[] romOffsets = segments[1].substring(1, segments[1].length() - 1).split(",");
            int[] offsets = new int [romOffsets.length];
            for (int i = 0; i < offsets.length; i++) {
                offsets[i] = parseRIInt(romOffsets[i]);
            }
            switch (segments[0]) {
                case "Species":
                    sp.speciesOffsets = offsets;
                    break;
                case "Level":
                    sp.levelOffsets = offsets;
                    break;
            }
        }
        return sp;
    }

    private static int parseRIInt(String off) {
        int radix = 10;
        off = off.trim().toLowerCase();
        if (off.startsWith("0x") || off.startsWith("&h")) {
            radix = 16;
            off = off.substring(2);
        }
        try {
            return Integer.parseInt(off, radix);
        } catch (NumberFormatException ex) {
            System.err.println("invalid base " + radix + "number " + off);
            return 0;
        }
    }

    private static long parseRILong(String off) {
        int radix = 10;
        off = off.trim().toLowerCase();
        if (off.startsWith("0x") || off.startsWith("&h")) {
            radix = 16;
            off = off.substring(2);
        }
        try {
            return Long.parseLong(off, radix);
        } catch (NumberFormatException ex) {
            System.err.println("invalid base " + radix + "number " + off);
            return 0;
        }
    }

    // This ROM's data
    private Pokemon[] pokes;
    private List<Pokemon> pokemonList;
    private RomEntry romEntry;
    private String[] mapNames;
    private SubMap[] maps;
    private long actualCRC32;

    public static boolean detectRomInner(byte[] rom, int romSize) {
        // size check
        return romSize >= GBConstants.minRomSize && romSize <= GBConstants.maxRomSize && checkRomEntry(rom) != null;
    }

    @Override
    public void loadedRom() {
        romEntry = checkRomEntry(this.rom);
        pokeNumToRBYTable = new int[256];
        pokeRBYToNumTable = new int[256];
        maps = new SubMap[256];
        clearTextTables();
        readTextTable("gameboy_jpn");
        if (romEntry.extraTableFile != null && !romEntry.extraTableFile.equalsIgnoreCase("none")) {
            readTextTable(romEntry.extraTableFile);
        }
        loadPokedexOrder();
        loadPokemonStats();
        pokemonList = Arrays.asList(pokes);
        preloadMaps();
        loadMapNames();
        actualCRC32 = FileFunctions.getCRC32(rom);
    }

    private void loadPokedexOrder() {
        int pkmnCount = romEntry.getValue("InternalPokemonCount");
        int orderOffset = romEntry.getValue("PokedexOrder");
        pokedexCount = 0;
        for (int i = 1; i <= pkmnCount; i++) {
            int pokedexNum = rom[orderOffset + i - 1] & 0xFF;
            pokeRBYToNumTable[i] = pokedexNum;
            if (pokedexNum != 0 && pokeNumToRBYTable[pokedexNum] == 0) {
                pokeNumToRBYTable[pokedexNum] = i;
            }
            pokedexCount = Math.max(pokedexCount, pokedexNum);
        }
    }

    private static RomEntry checkRomEntry(byte[] rom) {
        int version = rom[GBConstants.versionOffset] & 0xFF;
        int nonjap = rom[GBConstants.jpFlagOffset] & 0xFF;
        // Check for specific CRC first
        // Now check for non-specific-CRC entries
        for (RomEntry re : roms) {
            if (romSig(rom, re.romName) && re.version == version && re.nonJapanese == nonjap && re.crcInHeader == -1) {
                return re;
            }
        }
        // Not found
        return null;
    }

    @Override
    public void savingRom() {
        savePokemonStats();
    }

    @Override
    public Map<Integer, List<MoveLearnt>> getMovesLearnt() {
        return Collections.emptyMap();
    }

    private void loadPokemonStats() {
        pokes = new Gen1Pokemon[pokedexCount + 1];
        // Fetch our names
        String[] pokeNames = readPokemonNames();
        // Get base stats
        int pokeStatsOffset = romEntry.getValue("PokemonStatsOffset");
        for (int i = 1; i <= pokedexCount; i++) {
            pokes[i] = new Gen1Pokemon();
            pokes[i].number = i;
            if (i != Species.mew || romEntry.isYellow) {
                loadBasicPokeStats(pokes[i], pokeStatsOffset + (i - 1) * Gen1Constants.baseStatsEntrySize);
            }
            // Name?
            pokes[i].name = pokeNames[pokeNumToRBYTable[i]];
        }

        // Mew override for R/B
        if (!romEntry.isYellow) {
            loadBasicPokeStats(pokes[Species.mew], romEntry.getValue("MewStatsOffset"));
        }

        // Evolutions
        populateEvolutions();

    }

    private void savePokemonStats() {
        // Write pokemon names
        int offs = romEntry.getValue("PokemonNamesOffset");
        int nameLength = romEntry.getValue("PokemonNamesLength");
        for (int i = 1; i <= pokedexCount; i++) {
            int rbynum = pokeNumToRBYTable[i];
            int stringOffset = offs + (rbynum - 1) * nameLength;
            writeFixedLengthString(pokes[i].name, stringOffset, nameLength);
        }
        // Write pokemon stats
        int pokeStatsOffset = romEntry.getValue("PokemonStatsOffset");
        for (int i = 1; i <= pokedexCount; i++) {
            if (i == Species.mew) {
                continue;
            }
            saveBasicPokeStats(pokes[i], pokeStatsOffset + (i - 1) * Gen1Constants.baseStatsEntrySize);
        }
        // Write MEW
        int mewOffset = romEntry.isYellow ? pokeStatsOffset + (Species.mew - 1)
                * Gen1Constants.baseStatsEntrySize : romEntry.getValue("MewStatsOffset");
        saveBasicPokeStats(pokes[Species.mew], mewOffset);

        // Write evolutions
        writeEvosAndMovesLearnt(null);
    }

    private void loadBasicPokeStats(Pokemon pkmn, int offset) {
        pkmn.hp = rom[offset + Gen1Constants.bsHPOffset] & 0xFF;
        pkmn.attack = rom[offset + Gen1Constants.bsAttackOffset] & 0xFF;
        pkmn.defense = rom[offset + Gen1Constants.bsDefenseOffset] & 0xFF;
        pkmn.speed = rom[offset + Gen1Constants.bsSpeedOffset] & 0xFF;
        pkmn.special = rom[offset + Gen1Constants.bsSpecialOffset] & 0xFF;
        // Type
        pkmn.primaryType = idToType(rom[offset + Gen1Constants.bsPrimaryTypeOffset] & 0xFF);
        pkmn.secondaryType = idToType(rom[offset + Gen1Constants.bsSecondaryTypeOffset] & 0xFF);
        // Only one type?
        if (pkmn.secondaryType == pkmn.primaryType) {
            pkmn.secondaryType = null;
        }

        pkmn.catchRate = rom[offset + Gen1Constants.bsCatchRateOffset] & 0xFF;
        pkmn.expYield = rom[offset + Gen1Constants.bsExpYieldOffset] & 0xFF;
        pkmn.growthCurve = ExpCurve.fromByte(rom[offset + Gen1Constants.bsGrowthCurveOffset]);
        pkmn.frontSpritePointer = readWord(offset + Gen1Constants.bsFrontSpriteOffset);

        pkmn.guaranteedHeldItem = -1;
        pkmn.commonHeldItem = -1;
        pkmn.rareHeldItem = -1;
        pkmn.darkGrassHeldItem = -1;
    }

    private void saveBasicPokeStats(Pokemon pkmn, int offset) {
        rom[offset + Gen1Constants.bsHPOffset] = (byte) pkmn.hp;
        rom[offset + Gen1Constants.bsAttackOffset] = (byte) pkmn.attack;
        rom[offset + Gen1Constants.bsDefenseOffset] = (byte) pkmn.defense;
        rom[offset + Gen1Constants.bsSpeedOffset] = (byte) pkmn.speed;
        rom[offset + Gen1Constants.bsSpecialOffset] = (byte) pkmn.special;
        rom[offset + Gen1Constants.bsPrimaryTypeOffset] = typeToByte(pkmn.primaryType);
        if (pkmn.secondaryType == null) {
            rom[offset + Gen1Constants.bsSecondaryTypeOffset] = rom[offset + Gen1Constants.bsPrimaryTypeOffset];
        } else {
            rom[offset + Gen1Constants.bsSecondaryTypeOffset] = typeToByte(pkmn.secondaryType);
        }
        rom[offset + Gen1Constants.bsCatchRateOffset] = (byte) pkmn.catchRate;
        rom[offset + Gen1Constants.bsGrowthCurveOffset] = pkmn.growthCurve.toByte();
        rom[offset + Gen1Constants.bsExpYieldOffset] = (byte) pkmn.expYield;
    }

    private String[] readPokemonNames() {
        int offs = romEntry.getValue("PokemonNamesOffset");
        int nameLength = romEntry.getValue("PokemonNamesLength");
        int pkmnCount = romEntry.getValue("InternalPokemonCount");
        String[] names = new String[pkmnCount + 1];
        for (int i = 1; i <= pkmnCount; i++) {
            names[i] = readFixedLengthString(offs + (i - 1) * nameLength, nameLength);
        }
        return names;
    }

    @Override
    public List<Pokemon> getStarters() {
        // Get the starters
        List<Pokemon> starters = new ArrayList<>();
        starters.add(pokes[pokeRBYToNumTable[rom[romEntry.arrayEntries.get("StarterOffsets1")[0]] & 0xFF]]);
        starters.add(pokes[pokeRBYToNumTable[rom[romEntry.arrayEntries.get("StarterOffsets2")[0]] & 0xFF]]);
        if (!romEntry.isYellow) {
            starters.add(pokes[pokeRBYToNumTable[rom[romEntry.arrayEntries.get("StarterOffsets3")[0]] & 0xFF]]);
        }
        return starters;
    }

    @Override
    public boolean setStarters(List<Pokemon> newStarters) {
        // Amount?
        int starterAmount = 2;
        if (!romEntry.isYellow) {
            starterAmount = 3;
        }

        // Patch starter bytes
        for (int i = 0; i < starterAmount; i++) {
            byte starter = (byte) pokeNumToRBYTable[newStarters.get(i).number];
            int[] offsets = romEntry.arrayEntries.get("StarterOffsets" + (i + 1));
            for (int offset : offsets) {
                rom[offset] = starter;
            }
        }

        // Special stuff for non-Yellow only

        if (!romEntry.isYellow) {

            // Starter text
            if (romEntry.getValue("CanChangeStarterText") > 0) {
                int[] starterTextOffsets = romEntry.arrayEntries.get("StarterTextOffsets");
                for (int i = 0; i < 3 && i < starterTextOffsets.length; i++) {
                    writeVariableLengthString(String.format("So! You want\\n%s?\\e", newStarters.get(i).name),
                            starterTextOffsets[i]);
                }
            }

            // Patch starter pokedex routine?
            // Can only do in 1M roms because of size concerns
            if (romEntry.getValue("PatchPokedex") > 0) {

                // Starter pokedex required RAM values
                // RAM offset => value
                // Allows for multiple starters in the same RAM byte
                Map<Integer, Integer> onValues = new TreeMap<>();
                for (int i = 0; i < 3; i++) {
                    int pkDexNum = newStarters.get(i).number;
                    int ramOffset = (pkDexNum - 1) / 8 + romEntry.getValue("PokedexRamOffset");
                    int bitShift = (pkDexNum - 1) % 8;
                    int writeValue = 1 << bitShift;
                    if (onValues.containsKey(ramOffset)) {
                        onValues.put(ramOffset, onValues.get(ramOffset) | writeValue);
                    } else {
                        onValues.put(ramOffset, writeValue);
                    }
                }

                // Starter pokedex offset/pointer calculations

                int pkDexOnOffset = romEntry.getValue("StarterPokedexOnOffset");
                int pkDexOffOffset = romEntry.getValue("StarterPokedexOffOffset");

                int sizeForOnRoutine = 5 * onValues.size() + 3;
                int writeOnRoutineTo = romEntry.getValue("StarterPokedexBranchOffset");
                int writeOffRoutineTo = writeOnRoutineTo + sizeForOnRoutine;
                int offsetForOnRoutine = makeGBPointer(writeOnRoutineTo);
                int offsetForOffRoutine = makeGBPointer(writeOffRoutineTo);
                int retOnOffset = makeGBPointer(pkDexOnOffset + 5);
                int retOffOffset = makeGBPointer(pkDexOffOffset + 4);

                // Starter pokedex
                // Branch to our new routine(s)

                // Turn bytes on
                rom[pkDexOnOffset] = GBConstants.gbZ80Jump;
                writeWord(pkDexOnOffset + 1, offsetForOnRoutine);
                rom[pkDexOnOffset + 3] = GBConstants.gbZ80Nop;
                rom[pkDexOnOffset + 4] = GBConstants.gbZ80Nop;

                // Turn bytes off
                rom[pkDexOffOffset] = GBConstants.gbZ80Jump;
                writeWord(pkDexOffOffset + 1, offsetForOffRoutine);
                rom[pkDexOffOffset + 3] = GBConstants.gbZ80Nop;

                // Put together the two scripts
                rom[writeOffRoutineTo] = GBConstants.gbZ80XorA;
                int turnOnOffset = writeOnRoutineTo;
                int turnOffOffset = writeOffRoutineTo + 1;
                for (int ramOffset : onValues.keySet()) {
                    int onValue = onValues.get(ramOffset);
                    // Turn on code
                    rom[turnOnOffset++] = GBConstants.gbZ80LdA;
                    rom[turnOnOffset++] = (byte) onValue;
                    // Turn on code for ram writing
                    rom[turnOnOffset++] = GBConstants.gbZ80LdAToFar;
                    rom[turnOnOffset++] = (byte) (ramOffset % 0x100);
                    rom[turnOnOffset++] = (byte) (ramOffset / 0x100);
                    // Turn off code for ram writing
                    rom[turnOffOffset++] = GBConstants.gbZ80LdAToFar;
                    rom[turnOffOffset++] = (byte) (ramOffset % 0x100);
                    rom[turnOffOffset++] = (byte) (ramOffset / 0x100);
                }
                // Jump back
                rom[turnOnOffset++] = GBConstants.gbZ80Jump;
                writeWord(turnOnOffset, retOnOffset);

                rom[turnOffOffset++] = GBConstants.gbZ80Jump;
                writeWord(turnOffOffset, retOffOffset);
            }

        }

        // If we're changing the player's starter for Yellow, then the player can't get the
        // Bulbasaur gift unless they randomly stumble into a Pikachu somewhere else. This is
        // because you need a certain amount of Pikachu happiness to acquire this gift, and
        // happiness only accumulates if you have a Pikachu. Instead, just patch out this check.
        if (romEntry.entries.containsKey("PikachuHappinessCheckOffset") && newStarters.get(0).number != Species.pikachu) {
            int offset = romEntry.getValue("PikachuHappinessCheckOffset");

            // The code looks like this:
            // ld a, [wPikachuHappiness]
            // cp 147
            // jr c, .asm_1cfb3    <- this is where "offset" is
            // Write two nops to patch out the jump
            rom[offset] =  GBConstants.gbZ80Nop;
            rom[offset + 1] =  GBConstants.gbZ80Nop;
        }

        return true;

    }

    @Override
    public int starterCount() {
        return isYellow() ? 2 : 3;
    }

    @Override
    public List<EncounterSet> getEncounters(boolean useTimeOfDay) {
        List<EncounterSet> encounters = new ArrayList<>();

        Pokemon ghostMarowak = pokes[Species.marowak];
        if (canChangeStaticPokemon()) {
            ghostMarowak = pokes[pokeRBYToNumTable[rom[romEntry.ghostMarowakOffsets[0]] & 0xFF]];
        }

        // grass & water
        List<Integer> usedOffsets = new ArrayList<>();
        int tableOffset = romEntry.getValue("WildPokemonTableOffset");
        int tableBank = bankOf(tableOffset);
        int mapID = -1;

        while (readWord(tableOffset) != Gen1Constants.encounterTableEnd) {
            mapID++;
            int offset = calculateOffset(tableBank, readWord(tableOffset));
            int rootOffset = offset;
            if (!usedOffsets.contains(offset)) {
                usedOffsets.add(offset);
                // grass and water are exactly the same
                for (int a = 0; a < 2; a++) {
                    int rate = rom[offset++] & 0xFF;
                    if (rate > 0) {
                        // there is data here
                        EncounterSet thisSet = new EncounterSet();
                        thisSet.rate = rate;
                        thisSet.offset = rootOffset;
                        thisSet.displayName = (a == 1 ? "Surfing" : "Grass/Cave") + " on " + mapNames[mapID];
                        if (mapID >= Gen1Constants.towerMapsStartIndex && mapID <= Gen1Constants.towerMapsEndIndex) {
                            thisSet.bannedPokemon.add(ghostMarowak);
                        }
                        for (int slot = 0; slot < Gen1Constants.encounterTableSize; slot++) {
                            Encounter enc = new Encounter();
                            enc.level = rom[offset] & 0xFF;
                            enc.pokemon = pokes[pokeRBYToNumTable[rom[offset + 1] & 0xFF]];
                            thisSet.encounters.add(enc);
                            offset += 2;
                        }
                        encounters.add(thisSet);
                    }
                }
            } else {
                for (EncounterSet es : encounters) {
                    if (es.offset == offset) {
                        es.displayName += ", " + mapNames[mapID];
                    }
                }
            }
            tableOffset += 2;
        }

        // old rod
        int oldRodOffset = romEntry.getValue("OldRodOffset");
        EncounterSet oldRodSet = new EncounterSet();
        oldRodSet.displayName = "Old Rod Fishing";
        Encounter oldRodEnc = new Encounter();
        oldRodEnc.level = rom[oldRodOffset + 2] & 0xFF;
        oldRodEnc.pokemon = pokes[pokeRBYToNumTable[rom[oldRodOffset + 1] & 0xFF]];
        oldRodSet.encounters.add(oldRodEnc);
        oldRodSet.bannedPokemon.add(ghostMarowak);
        encounters.add(oldRodSet);

        // good rod
        int goodRodOffset = romEntry.getValue("GoodRodOffset");
        EncounterSet goodRodSet = new EncounterSet();
        goodRodSet.displayName = "Good Rod Fishing";
        for (int grSlot = 0; grSlot < 2; grSlot++) {
            Encounter enc = new Encounter();
            enc.level = rom[goodRodOffset + grSlot * 2] & 0xFF;
            enc.pokemon = pokes[pokeRBYToNumTable[rom[goodRodOffset + grSlot * 2 + 1] & 0xFF]];
            goodRodSet.encounters.add(enc);
        }
        goodRodSet.bannedPokemon.add(ghostMarowak);
        encounters.add(goodRodSet);

        // super rod
        if (romEntry.isYellow) {
            int superRodOffset = romEntry.getValue("SuperRodTableOffset");
            while ((rom[superRodOffset] & 0xFF) != 0xFF) {
                int map = rom[superRodOffset++] & 0xFF;
                EncounterSet thisSet = new EncounterSet();
                thisSet.displayName = "Super Rod Fishing on " + mapNames[map];
                for (int encN = 0; encN < Gen1Constants.yellowSuperRodTableSize; encN++) {
                    Encounter enc = new Encounter();
                    enc.level = rom[superRodOffset + 1] & 0xFF;
                    enc.pokemon = pokes[pokeRBYToNumTable[rom[superRodOffset] & 0xFF]];
                    thisSet.encounters.add(enc);
                    superRodOffset += 2;
                }
                thisSet.bannedPokemon.add(ghostMarowak);
                encounters.add(thisSet);
            }
        } else {
            // red/blue
            int superRodOffset = romEntry.getValue("SuperRodTableOffset");
            int superRodBank = bankOf(superRodOffset);
            List<Integer> usedSROffsets = new ArrayList<>();
            while ((rom[superRodOffset] & 0xFF) != 0xFF) {
                int map = rom[superRodOffset++] & 0xFF;
                int setOffset = calculateOffset(superRodBank, readWord(superRodOffset));
                superRodOffset += 2;
                if (!usedSROffsets.contains(setOffset)) {
                    usedSROffsets.add(setOffset);
                    EncounterSet thisSet = new EncounterSet();
                    thisSet.displayName = "Super Rod Fishing on " + mapNames[map];
                    thisSet.offset = setOffset;
                    int pokesInSet = rom[setOffset++] & 0xFF;
                    for (int encN = 0; encN < pokesInSet; encN++) {
                        Encounter enc = new Encounter();
                        enc.level = rom[setOffset] & 0xFF;
                        enc.pokemon = pokes[pokeRBYToNumTable[rom[setOffset + 1] & 0xFF]];
                        thisSet.encounters.add(enc);
                        setOffset += 2;
                    }
                    thisSet.bannedPokemon.add(ghostMarowak);
                    encounters.add(thisSet);
                } else {
                    for (EncounterSet es : encounters) {
                        if (es.offset == setOffset) {
                            es.displayName += ", " + mapNames[map];
                        }
                    }
                }
            }
        }

        return encounters;
    }

    @Override
    public void setEncounters(boolean useTimeOfDay, List<EncounterSet> encounters) {
        Iterator<EncounterSet> encsetit = encounters.iterator();

        // grass & water
        List<Integer> usedOffsets = new ArrayList<>();
        int tableOffset = romEntry.getValue("WildPokemonTableOffset");
        int tableBank = bankOf(tableOffset);

        while (readWord(tableOffset) != Gen1Constants.encounterTableEnd) {
            int offset = calculateOffset(tableBank, readWord(tableOffset));
            if (!usedOffsets.contains(offset)) {
                usedOffsets.add(offset);
                // grass and water are exactly the same
                for (int a = 0; a < 2; a++) {
                    int rate = rom[offset++] & 0xFF;
                    if (rate > 0) {
                        // there is data here
                        EncounterSet thisSet = encsetit.next();
                        for (int slot = 0; slot < Gen1Constants.encounterTableSize; slot++) {
                            Encounter enc = thisSet.encounters.get(slot);
                            rom[offset] = (byte) enc.level;
                            rom[offset + 1] = (byte) pokeNumToRBYTable[enc.pokemon.number];
                            offset += 2;
                        }
                    }
                }
            }
            tableOffset += 2;
        }

        // old rod
        int oldRodOffset = romEntry.getValue("OldRodOffset");
        EncounterSet oldRodSet = encsetit.next();
        Encounter oldRodEnc = oldRodSet.encounters.get(0);
        rom[oldRodOffset + 2] = (byte) oldRodEnc.level;
        rom[oldRodOffset + 1] = (byte) pokeNumToRBYTable[oldRodEnc.pokemon.number];

        // good rod
        int goodRodOffset = romEntry.getValue("GoodRodOffset");
        EncounterSet goodRodSet = encsetit.next();
        for (int grSlot = 0; grSlot < 2; grSlot++) {
            Encounter enc = goodRodSet.encounters.get(grSlot);
            rom[goodRodOffset + grSlot * 2] = (byte) enc.level;
            rom[goodRodOffset + grSlot * 2 + 1] = (byte) pokeNumToRBYTable[enc.pokemon.number];
        }

        // super rod
        if (romEntry.isYellow) {
            int superRodOffset = romEntry.getValue("SuperRodTableOffset");
            while ((rom[superRodOffset] & 0xFF) != 0xFF) {
                superRodOffset++;
                EncounterSet thisSet = encsetit.next();
                for (int encN = 0; encN < Gen1Constants.yellowSuperRodTableSize; encN++) {
                    Encounter enc = thisSet.encounters.get(encN);
                    rom[superRodOffset + 1] = (byte) enc.level;
                    rom[superRodOffset] = (byte) pokeNumToRBYTable[enc.pokemon.number];
                    superRodOffset += 2;
                }
            }
        } else {
            // red/blue
            int superRodOffset = romEntry.getValue("SuperRodTableOffset");
            int superRodBank = bankOf(superRodOffset);
            List<Integer> usedSROffsets = new ArrayList<>();
            while ((rom[superRodOffset] & 0xFF) != 0xFF) {
                superRodOffset++;
                int setOffset = calculateOffset(superRodBank, readWord(superRodOffset));
                superRodOffset += 2;
                if (!usedSROffsets.contains(setOffset)) {
                    usedSROffsets.add(setOffset);
                    int pokesInSet = rom[setOffset++] & 0xFF;
                    EncounterSet thisSet = encsetit.next();
                    for (int encN = 0; encN < pokesInSet; encN++) {
                        Encounter enc = thisSet.encounters.get(encN);
                        rom[setOffset] = (byte) enc.level;
                        rom[setOffset + 1] = (byte) pokeNumToRBYTable[enc.pokemon.number];
                        setOffset += 2;
                    }
                }
            }
        }
    }

    @Override
    public List<Pokemon> getPokemon() {
        return pokemonList;
    }

    @Override
    public List<Pokemon> getPokemonInclFormes() {
        return pokemonList;
    }

    public List<Trainer> getTrainers() {
        int traineroffset = romEntry.getValue("TrainerDataTableOffset");
        int traineramount = Gen1Constants.trainerClassCount;
        int[] trainerclasslimits = romEntry.arrayEntries.get("TrainerDataClassCounts");

        int[] pointers = new int[traineramount + 1];
        for (int i = 1; i <= traineramount; i++) {
            int tPointer = readWord(traineroffset + (i - 1) * 2);
            pointers[i] = calculateOffset(bankOf(traineroffset), tPointer);
        }

        List<String> tcnames = getTrainerClassesForText();

        List<Trainer> allTrainers = new ArrayList<>();
        int index = 0;
        for (int i = 1; i <= traineramount; i++) {
            int offs = pointers[i];
            int limit = trainerclasslimits[i];
            String tcname = tcnames.get(i - 1);
            for (int trnum = 0; trnum < limit; trnum++) {
                index++;
                Trainer tr = new Trainer();
                tr.offset = offs;
                tr.index = index;
                tr.trainerclass = i;
                tr.fullDisplayName = tcname;
                int dataType = rom[offs] & 0xFF;
                if (dataType == 0xFF) {
                    // "Special" trainer
                    tr.poketype = 1;
                    offs++;
                    while (rom[offs] != 0x0) {
                        TrainerPokemon tpk = new TrainerPokemon();
                        tpk.level = rom[offs] & 0xFF;
                        tpk.pokemon = pokes[pokeRBYToNumTable[rom[offs + 1] & 0xFF]];
                        tr.pokemon.add(tpk);
                        offs += 2;
                    }
                } else {
                    tr.poketype = 0;
                    offs++;
                    while (rom[offs] != 0x0) {
                        TrainerPokemon tpk = new TrainerPokemon();
                        tpk.level = dataType;
                        tpk.pokemon = pokes[pokeRBYToNumTable[rom[offs] & 0xFF]];
                        tr.pokemon.add(tpk);
                        offs++;
                    }
                }
                offs++;
                allTrainers.add(tr);
            }
        }
        Gen1Constants.tagTrainersUniversal(allTrainers);
        if (romEntry.isYellow) {
            Gen1Constants.tagTrainersYellow(allTrainers);
        } else {
            Gen1Constants.tagTrainersRB(allTrainers);
        }
        return allTrainers;
    }

    @Override
    public List<Integer> getMainPlaythroughTrainers() {
        return new ArrayList<>(); // Not implemented
    }

    public void setTrainers(List<Trainer> trainerData, boolean doubleBattleMode) {
        int traineroffset = romEntry.getValue("TrainerDataTableOffset");
        int traineramount = Gen1Constants.trainerClassCount;
        int[] trainerclasslimits = romEntry.arrayEntries.get("TrainerDataClassCounts");

        int[] pointers = new int[traineramount + 1];
        for (int i = 1; i <= traineramount; i++) {
            int tPointer = readWord(traineroffset + (i - 1) * 2);
            pointers[i] = calculateOffset(bankOf(traineroffset), tPointer);
        }

        Iterator<Trainer> allTrainers = trainerData.iterator();
        for (int i = 1; i <= traineramount; i++) {
            int offs = pointers[i];
            int limit = trainerclasslimits[i];
            for (int trnum = 0; trnum < limit; trnum++) {
                Trainer tr = allTrainers.next();
                Iterator<TrainerPokemon> tPokes = tr.pokemon.iterator();
                // Write their pokemon based on poketype
                if (tr.poketype == 0) {
                    // Regular trainer
                    int fixedLevel = tr.pokemon.get(0).level;
                    rom[offs] = (byte) fixedLevel;
                    offs++;
                    while (tPokes.hasNext()) {
                        TrainerPokemon tpk = tPokes.next();
                        rom[offs] = (byte) pokeNumToRBYTable[tpk.pokemon.number];
                        offs++;
                    }
                } else {
                    // Special trainer
                    rom[offs] = (byte) 0xFF;
                    offs++;
                    while (tPokes.hasNext()) {
                        TrainerPokemon tpk = tPokes.next();
                        rom[offs] = (byte) tpk.level;
                        rom[offs + 1] = (byte) pokeNumToRBYTable[tpk.pokemon.number];
                        offs += 2;
                    }
                }
                rom[offs] = 0;
                offs++;
            }
        }

        // Custom Moves AI Table
        // Zero it out entirely.
        rom[romEntry.getValue("ExtraTrainerMovesTableOffset")] = (byte) 0xFF;

        // Champion Rival overrides in Red/Blue
        if (!isYellow()) {
            // hacky relative offset (very likely to work but maybe not always)
            int champRivalJump = romEntry.getValue("GymLeaderMovesTableOffset")
                    - Gen1Constants.champRivalOffsetFromGymLeaderMoves;
            // nop out this jump
            rom[champRivalJump] = GBConstants.gbZ80Nop;
            rom[champRivalJump + 1] = GBConstants.gbZ80Nop;
        }

    }

    @Override
    public List<Move> getMoves() {
        return null;
    }

    @Override
    public boolean isYellow() {
        return romEntry.isYellow;
    }

    @Override
    public boolean typeInGame(Type type) {
        if (!type.isHackOnly && (type != Type.DARK && type != Type.STEEL && type != Type.FAIRY)) {
            return true;
        }
        return romEntry.extraTypeReverse.containsKey(type);
    }

    private static class StaticPokemon {
        protected int[] speciesOffsets;
        protected int[] levelOffsets;

        public StaticPokemon() {
            this.speciesOffsets = new int[0];
            this.levelOffsets = new int[0];
        }

        public Pokemon getPokemon(Gen1RomHandler rh) {
            return rh.pokes[rh.pokeRBYToNumTable[rh.rom[speciesOffsets[0]] & 0xFF]];
        }

        public void setPokemon(Gen1RomHandler rh, Pokemon pkmn) {
            for (int offset : speciesOffsets) {
                rh.rom[offset] = (byte) rh.pokeNumToRBYTable[pkmn.number];
            }
        }

        public int getLevel(byte[] rom, int i) {
            return rom[levelOffsets[i]];
        }

        public void setLevel(byte[] rom, int level, int i) {
            rom[levelOffsets[i]] = (byte) level;
        }
    }

    @Override
    public List<StaticEncounter> getStaticPokemon() {
        List<StaticEncounter> statics = new ArrayList<>();
        if (romEntry.getValue("StaticPokemonSupport") > 0) {
            for (StaticPokemon sp : romEntry.staticPokemon) {
                StaticEncounter se = new StaticEncounter();
                se.pkmn = sp.getPokemon(this);
                se.level = sp.getLevel(rom, 0);
                statics.add(se);
            }
        }
        return statics;
    }

    @Override
    public boolean setStaticPokemon(List<StaticEncounter> staticPokemon) {
        for (int i = 0; i < romEntry.staticPokemon.size(); i++) {
            StaticEncounter se = staticPokemon.get(i);
            StaticPokemon sp = romEntry.staticPokemon.get(i);
            sp.setPokemon(this, se.pkmn);
            sp.setLevel(rom, se.level, 0);
        }

        return true;
    }

    @Override
    public boolean canChangeStaticPokemon() {
        return (romEntry.getValue("StaticPokemonSupport") > 0);
    }

    @Override
    public boolean hasMainGameLegendaries() {
        return false;
    }

    @Override
    public List<Integer> getMainGameLegendaries() {
        return new ArrayList<>();
    }

    private void populateEvolutions() {
        for (Pokemon pkmn : pokes) {
            if (pkmn != null) {
                pkmn.evolutionsFrom.clear();
                pkmn.evolutionsTo.clear();
            }
        }

        int pointersOffset = romEntry.getValue("PokemonMovesetsTableOffset");

        int pkmnCount = romEntry.getValue("InternalPokemonCount");
        for (int i = 1; i <= pkmnCount; i++) {
            int pointer = readWord(pointersOffset + (i - 1) * 2);
            int realPointer = calculateOffset(bankOf(pointersOffset), pointer);
            if (pokeRBYToNumTable[i] != 0) {
                int thisPoke = pokeRBYToNumTable[i];
                Pokemon pkmn = pokes[thisPoke];
                while (rom[realPointer] != 0) {
                    int method = rom[realPointer];
                    EvolutionType type = EvolutionType.fromIndex(1, method);
                    int otherPoke = pokeRBYToNumTable[rom[realPointer + 2 + (type == EvolutionType.STONE ? 1 : 0)] & 0xFF];
                    int extraInfo = rom[realPointer + 1] & 0xFF;
                    Evolution evo = new Evolution(pkmn, pokes[otherPoke], true, type, extraInfo);
                    if (!pkmn.evolutionsFrom.contains(evo)) {
                        pkmn.evolutionsFrom.add(evo);
                        if (pokes[otherPoke] != null) {
                            pokes[otherPoke].evolutionsTo.add(evo);
                        }
                    }
                    realPointer += (type == EvolutionType.STONE ? 4 : 3);
                }
                // split evos don't carry stats
                if (pkmn.evolutionsFrom.size() > 1) {
                    for (Evolution e : pkmn.evolutionsFrom) {
                        e.carryStats = false;
                    }
                }
            }
        }
    }

    @Override
    public void removeImpossibleEvolutions(Settings settings) {
        // Gen 1: only regular trade evos
        // change them all to evolve at level 37
        for (Pokemon pkmn : pokes) {
            if (pkmn != null) {
                for (Evolution evo : pkmn.evolutionsFrom) {
                    if (evo.type == EvolutionType.TRADE) {
                        // change
                        evo.type = EvolutionType.LEVEL;
                        evo.extraInfo = 37;
                        addEvoUpdateLevel(impossibleEvolutionUpdates,evo);
                    }
                }
            }
        }
    }

    @Override
    public void makeEvolutionsEasier(Settings settings) {
        // No such thing
    }

    @Override
    public void removeTimeBasedEvolutions() {
        // No such thing
    }

    private List<String> getTrainerClassesForText() {
        int[] offsets = romEntry.arrayEntries.get("TrainerClassNamesOffsets");
        List<String> tcNames = new ArrayList<>();
        int offset = offsets[offsets.length - 1];
        for (int j = 0; j < Gen1Constants.tclassesCounts[1]; j++) {
            String name = readVariableLengthString(offset);
            offset += lengthOfStringAt(offset, false) + 1;
            tcNames.add(name);
        }
        return tcNames;
    }

    @Override
    public List<String> getTrainerClassNames() {
        return Collections.emptyList();
    }

    @Override
    public String getDefaultExtension() {
        return "gbc";
    }

    @Override
    public int abilitiesPerPokemon() {
        return 0;
    }

    @Override
    public int internalStringLength(String string) {
        return translateString(string).length;
    }

    @Override
    public int miscTweaksAvailable() {
        int available = MiscTweak.LOWER_CASE_POKEMON_NAMES.getValue();
        available |= MiscTweak.UPDATE_TYPE_EFFECTIVENESS.getValue();

        if (romEntry.tweakFiles.get("BWXPTweak") != null) {
            available |= MiscTweak.BW_EXP_PATCH.getValue();
        }
        if (romEntry.tweakFiles.get("XAccNerfTweak") != null) {
            available |= MiscTweak.NERF_X_ACCURACY.getValue();
        }
        if (romEntry.tweakFiles.get("CritRateTweak") != null) {
            available |= MiscTweak.FIX_CRIT_RATE.getValue();
        }
        if (romEntry.getValue("TextDelayFunctionOffset") != 0) {
            available |= MiscTweak.FASTEST_TEXT.getValue();
        }
        if (romEntry.getValue("PCPotionOffset") != 0) {
            available |= MiscTweak.RANDOMIZE_PC_POTION.getValue();
        }
        if (romEntry.getValue("PikachuEvoJumpOffset") != 0) {
            available |= MiscTweak.ALLOW_PIKACHU_EVOLUTION.getValue();
        }
        if (romEntry.getValue("CatchingTutorialMonOffset") != 0) {
            available |= MiscTweak.RANDOMIZE_CATCHING_TUTORIAL.getValue();
        }

        return available;
    }

    @Override
    public void applyMiscTweak(MiscTweak tweak) {
        if (tweak == MiscTweak.FASTEST_TEXT) {
            applyFastestTextPatch();
        }
    }

    private void applyFastestTextPatch() {
        if (romEntry.getValue("TextDelayFunctionOffset") != 0) {
            rom[romEntry.getValue("TextDelayFunctionOffset")] = GBConstants.gbZ80Ret;
        }
    }

    @Override
    public void randomizeIntroPokemon() {
        // First off, intro Pokemon
        // 160 add yellow intro random
        int introPokemon = pokeNumToRBYTable[this.randomPokemon().number];
        rom[romEntry.getValue("IntroPokemonOffset")] = (byte) introPokemon;
        rom[romEntry.getValue("IntroCryOffset")] = (byte) introPokemon;

    }

    private static class SubMap {
        private int addr;
        private int bank;
        private MapHeader header;
        private Connection[] cons;
        private int n_cons;
        private int obj_addr;
        private List<Integer> itemOffsets;
    }

    private static class MapHeader {
        private int connect_byte; // u8
        // 10 bytes
    }

    private static class Connection {
        private int index; // u8
        // 11 bytes
    }

    private void preloadMaps() {
        int mapBanks = romEntry.getValue("MapBanks");
        int mapAddresses = romEntry.getValue("MapAddresses");

        preloadMap(mapBanks, mapAddresses, 0);
    }

    private void preloadMap(int mapBanks, int mapAddresses, int mapID) {

        if (maps[mapID] != null || mapID == 0xED || mapID == 0xFF) {
            return;
        }

        SubMap map = new SubMap();
        maps[mapID] = map;
        map.addr = calculateOffset(rom[mapBanks + mapID] & 0xFF, readWord(mapAddresses + mapID * 2));
        map.bank = bankOf(map.addr);

        map.header = new MapHeader();
        map.header.connect_byte = rom[map.addr + 9] & 0xFF;

        int cb = map.header.connect_byte;
        map.n_cons = ((cb & 8) >> 3) + ((cb & 4) >> 2) + ((cb & 2) >> 1) + (cb & 1);

        int cons_offset = map.addr + 10;

        map.cons = new Connection[map.n_cons];
        for (int i = 0; i < map.n_cons; i++) {
            int tcon_offs = cons_offset + i * 11;
            Connection con = new Connection();
            con.index = rom[tcon_offs] & 0xFF;
            map.cons[i] = con;
            preloadMap(mapBanks, mapAddresses, con.index);
        }
        map.obj_addr = calculateOffset(map.bank, readWord(cons_offset + map.n_cons * 11));

        // Read objects
        // +0 is the border tile (ignore)
        // +1 is warp count

        int n_warps = rom[map.obj_addr + 1] & 0xFF;
        int offs = map.obj_addr + 2;
        for (int i = 0; i < n_warps; i++) {
            // track this warp
            int to_map = rom[offs + 3] & 0xFF;
            preloadMap(mapBanks, mapAddresses, to_map);
            offs += 4;
        }

        // Now we're pointing to sign count
        int n_signs = rom[offs++] & 0xFF;
        offs += n_signs * 3;

        // Finally, entities, which contain the items
        map.itemOffsets = new ArrayList<>();
        int n_entities = rom[offs++] & 0xFF;
        for (int i = 0; i < n_entities; i++) {
            // Read text ID
            int tid = rom[offs + 5] & 0xFF;
            if ((tid & (1 << 6)) > 0) {
                // trainer
                offs += 8;
            } else if ((tid & (1 << 7)) > 0 && (rom[offs + 6] != 0x00)) {
                // item
                map.itemOffsets.add(offs + 6);
                offs += 7;
            } else {
                // generic
                offs += 6;
            }
        }
    }

    private void loadMapNames() {
        mapNames = new String[256];
        int mapNameTableOffset = romEntry.getValue("MapNameTableOffset");
        int mapNameBank = bankOf(mapNameTableOffset);
        // external names
        List<Integer> usedExternal = new ArrayList<>();
        for (int i = 0; i < 0x25; i++) {
            int externalOffset = calculateOffset(mapNameBank, readWord(mapNameTableOffset + 1));
            usedExternal.add(externalOffset);
            mapNames[i] = readVariableLengthString(externalOffset);
            mapNameTableOffset += 3;
        }

        // internal names
        int lastMaxMap = 0x25;
        Map<Integer, Integer> previousMapCounts = new HashMap<>();
        while ((rom[mapNameTableOffset] & 0xFF) != 0xFF) {
            int maxMap = rom[mapNameTableOffset] & 0xFF;
            int nameOffset = calculateOffset(mapNameBank, readWord(mapNameTableOffset + 2));
            String actualName = readVariableLengthString(nameOffset).trim();
            if (usedExternal.contains(nameOffset)) {
                for (int i = lastMaxMap; i < maxMap; i++) {
                    if (maps[i] != null) {
                        mapNames[i] = actualName + " (Building)";
                    }
                }
            } else {
                int mapCount = 0;
                if (previousMapCounts.containsKey(nameOffset)) {
                    mapCount = previousMapCounts.get(nameOffset);
                }
                for (int i = lastMaxMap; i < maxMap; i++) {
                    if (maps[i] != null) {
                        mapCount++;
                        mapNames[i] = actualName + " (" + mapCount + ")";
                    }
                }
                previousMapCounts.put(nameOffset, mapCount);
            }
            lastMaxMap = maxMap;
            mapNameTableOffset += 4;
        }
    }

    @Override
    public int generationOfPokemon() {
        return 1;
    }

    private void writeEvosAndMovesLearnt(Map<Integer, List<MoveLearnt>> movesets) {
        // we assume a few things here:
        // 1) evos & moves learnt are stored directly after their pointer table
        // 2) PokemonMovesetsExtraSpaceOffset is in the same bank, and
        // points to the start of the free space at the end of the bank
        // (if set to 0, disabled from being used)
        // 3) PokemonMovesetsDataSize is from the start of actual data to
        // the start of engine/battle/e_2.asm in pokered (aka code we can't
        // overwrite)
        // it appears that in yellow, this code is moved
        // so we can write the evos/movesets in one continuous block
        // until the end of the bank.
        // so for yellow, extraspace is disabled.
        // specify null to either argument to copy old values
        int movesEvosStart = romEntry.getValue("PokemonMovesetsTableOffset");
        int movesEvosBank = bankOf(movesEvosStart);
        int pkmnCount = romEntry.getValue("InternalPokemonCount");
        byte[] pointerTable = new byte[pkmnCount * 2];
        int mainDataBlockSize = romEntry.getValue("PokemonMovesetsDataSize");
        int mainDataBlockOffset = movesEvosStart + pointerTable.length;
        byte[] mainDataBlock = new byte[mainDataBlockSize];
        int offsetInMainData = 0;
        int extraSpaceOffset = romEntry.getValue("PokemonMovesetsExtraSpaceOffset");
        int extraSpaceBank = bankOf(extraSpaceOffset);
        boolean extraSpaceEnabled = false;
        byte[] extraDataBlock = null;
        int extraSpaceSize = 0;
        if (movesEvosBank == extraSpaceBank && extraSpaceOffset != 0) {
            extraSpaceEnabled = true;
            int startOfNextBank = ((extraSpaceOffset / GBConstants.bankSize) + 1) * GBConstants.bankSize;
            extraSpaceSize = startOfNextBank - extraSpaceOffset;
            extraDataBlock = new byte[extraSpaceSize];
        }
        int nullEntryPointer = -1;

        for (int i = 1; i <= pkmnCount; i++) {
            byte[] writeData = null;
            int oldDataOffset = calculateOffset(movesEvosBank, readWord(movesEvosStart + (i - 1) * 2));
            boolean setNullEntryPointerHere = false;
            if (pokeRBYToNumTable[i] == 0) {
                // null entry
                if (nullEntryPointer == -1) {
                    // make the null entry
                    writeData = new byte[] { 0, 0 };
                    setNullEntryPointerHere = true;
                } else {
                    writeWord(pointerTable, (i - 1) * 2, nullEntryPointer);
                }
            } else {
                int pokeNum = pokeRBYToNumTable[i];
                Pokemon pkmn = pokes[pokeNum];
                ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
                // Evolutions
                {
                    for (Evolution evo : pkmn.evolutionsFrom) {
                        // write evos for this poke
                        dataStream.write(evo.type.toIndex(1));
                        if (evo.type == EvolutionType.LEVEL) {
                            dataStream.write(evo.extraInfo); // min lvl
                        } else if (evo.type == EvolutionType.STONE) {
                            dataStream.write(evo.extraInfo); // stone item
                            dataStream.write(1); // minimum level
                        } else if (evo.type == EvolutionType.TRADE) {
                            dataStream.write(1); // minimum level
                        }
                        int pokeIndexTo = pokeNumToRBYTable[evo.to.number];
                        dataStream.write(pokeIndexTo); // species
                    }
                }
                // write terminator for evos
                dataStream.write(0);

                // Movesets
                if (movesets == null) {
                    // copy old
                    int movesOffset = oldDataOffset;
                    // move past evos
                    while (rom[movesOffset] != 0x00) {
                        int method = rom[movesOffset] & 0xFF;
                        movesOffset += (method == 2) ? 4 : 3;
                    }
                    movesOffset++;
                    // copy moves
                    while (rom[movesOffset] != 0x00) {
                        dataStream.write(rom[movesOffset++] & 0xFF);
                        dataStream.write(rom[movesOffset++] & 0xFF);
                    }
                }
                // terminator
                dataStream.write(0);

                // done, set writeData
                writeData = dataStream.toByteArray();
                try {
                    dataStream.close();
                } catch (IOException e) {
                }
            }

            // write data and set pointer?
            if (writeData != null) {
                int lengthToFit = writeData.length;
                int pointerToWrite = 0;
                // compression of leading & trailing 0s:
                // every entry ends in a 0 (end of move list).
                // if a block already has data in it, and the data
                // we want to write starts with a 0 (no evolutions)
                // we can compress it into the end of the last entry
                // this saves a decent amount of space overall.
                if ((offsetInMainData + lengthToFit <= mainDataBlockSize)
                        || (writeData[0] == 0 && offsetInMainData > 0 && offsetInMainData + lengthToFit == mainDataBlockSize + 1)) {
                    // place in main storage
                    if (writeData[0] == 0 && offsetInMainData > 0) {
                        int writtenDataOffset = mainDataBlockOffset + offsetInMainData - 1;
                        pointerToWrite = makeGBPointer(writtenDataOffset);
                        System.arraycopy(writeData, 1, mainDataBlock, offsetInMainData, lengthToFit - 1);
                        offsetInMainData += lengthToFit - 1;
                    } else {
                        int writtenDataOffset = mainDataBlockOffset + offsetInMainData;
                        pointerToWrite = makeGBPointer(writtenDataOffset);
                        System.arraycopy(writeData, 0, mainDataBlock, offsetInMainData, lengthToFit);
                        offsetInMainData += lengthToFit;
                    }
                }
                if (pointerToWrite >= 0) {
                    writeWord(pointerTable, (i - 1) * 2, pointerToWrite);
                    if (setNullEntryPointerHere) {
                        nullEntryPointer = pointerToWrite;
                    }
                }
            }
        }

        // Done, write final results to ROM
        System.arraycopy(pointerTable, 0, rom, movesEvosStart, pointerTable.length);
        System.arraycopy(mainDataBlock, 0, rom, mainDataBlockOffset, mainDataBlock.length);
        if (extraSpaceEnabled) {
            System.arraycopy(extraDataBlock, 0, rom, extraSpaceOffset, extraDataBlock.length);
        }
    }

}
