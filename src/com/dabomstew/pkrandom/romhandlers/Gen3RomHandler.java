package com.dabomstew.pkrandom.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  Gen3RomHandler.java - randomizer handler for R/S/E/FR/LG.             --*/
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

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.dabomstew.pkrandom.*;
import com.dabomstew.pkrandom.constants.*;
import com.dabomstew.pkrandom.pokemon.*;

public class Gen3RomHandler extends AbstractGBRomHandler {

    public static class Factory extends RomHandler.Factory {

        @Override
        public Gen3RomHandler create(Random random, PrintStream logStream) {
            return new Gen3RomHandler(random);
        }

        public boolean isLoadable(String filename) {
            byte[] loaded = loadFilePartial(filename, 0x100000);
            // nope
            return loaded.length != 0 && detectRomInner(loaded);
        }
    }

    public Gen3RomHandler(Random random) {
        super(random);
    }

    private static class RomEntry {
        private String name;
        private String romCode;
        private String tableFile;
        private int version;
        private int romType;
        private boolean copyStaticPokemon;
        private Map<String, Integer> entries = new HashMap<>();
        private Map<String, int[]> arrayEntries = new HashMap<>();
        private Map<String, String> strings = new HashMap<>();
        private List<StaticPokemon> staticPokemon = new ArrayList<>();
        private List<StaticPokemon> roamingPokemon = new ArrayList<>();
        private Map<String, String> codeTweaks = new HashMap<String, String>();
        private long expectedCRC32 = -1;

        public RomEntry() {

        }

        public RomEntry(RomEntry toCopy) {
            this.name = toCopy.name;
            this.romCode = toCopy.romCode;
            this.tableFile = toCopy.tableFile;
            this.version = toCopy.version;
            this.romType = toCopy.romType;
            this.copyStaticPokemon = toCopy.copyStaticPokemon;
            this.entries.putAll(toCopy.entries);
            this.arrayEntries.putAll(toCopy.arrayEntries);
            this.strings.putAll(toCopy.strings);
            this.staticPokemon.addAll(toCopy.staticPokemon);
            this.roamingPokemon.addAll(toCopy.roamingPokemon);
            this.codeTweaks.putAll(toCopy.codeTweaks);
            this.expectedCRC32 = toCopy.expectedCRC32;
        }

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
            Scanner sc = new Scanner(FileFunctions.openConfig("gen3_offsets.ini"), "UTF-8");
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
                        // Static Pokemon?
                        if (r[0].equals("StaticPokemon{}")) {
                            current.staticPokemon.add(parseStaticPokemon(r[1]));
                        } else if (r[0].equals("RoamingPokemon{}")) {
                            current.roamingPokemon.add(parseStaticPokemon(r[1]));
                        } else if (r[0].equals("TMText[]")) {
                            if (r[1].startsWith("[") && r[1].endsWith("]")) {
                            }
                        } else if (r[0].equals("MoveTutorText[]")) {
                            if (r[1].startsWith("[") && r[1].endsWith("]")) {
                            }
                        } else if (r[0].equals("Game")) {
                            current.romCode = r[1];
                        } else if (r[0].equals("Version")) {
                            current.version = parseRIInt(r[1]);
                        } else if (r[0].equals("Type")) {
                            if (r[1].equalsIgnoreCase("Ruby")) {
                                current.romType = Gen3Constants.RomType_Ruby;
                            } else if (r[1].equalsIgnoreCase("Sapp")) {
                                current.romType = Gen3Constants.RomType_Sapp;
                            } else if (r[1].equalsIgnoreCase("Em")) {
                                current.romType = Gen3Constants.RomType_Em;
                            } else if (r[1].equalsIgnoreCase("FRLG")) {
                                current.romType = Gen3Constants.RomType_FRLG;
                            } else {
                            }
                        } else if (r[0].equals("TableFile")) {
                            current.tableFile = r[1];
                        } else if (r[0].equals("CopyStaticPokemon")) {
                            int csp = parseRIInt(r[1]);
                            current.copyStaticPokemon = (csp > 0);
                        } else if (r[0].equals("CRC32")) {
                            current.expectedCRC32 = parseRILong("0x" + r[1]);
                        } else if (r[0].endsWith("Tweak")) {
                            current.codeTweaks.put(r[0], r[1]);
                        } else if (r[0].equals("CopyFrom")) {
                            for (RomEntry otherEntry : roms) {
                                if (r[1].equalsIgnoreCase(otherEntry.name)) {
                                    // copy from here
                                    current.arrayEntries.putAll(otherEntry.arrayEntries);
                                    current.entries.putAll(otherEntry.entries);
                                    current.strings.putAll(otherEntry.strings);
                                    boolean cTT = (current.getValue("CopyTMText") == 1);
                                    if (current.copyStaticPokemon) {
                                        current.staticPokemon.addAll(otherEntry.staticPokemon);
                                        current.roamingPokemon.addAll(otherEntry.roamingPokemon);
                                        current.entries.put("StaticPokemonSupport", 1);
                                    } else {
                                        current.entries.put("StaticPokemonSupport", 0);
                                    }
                                    if (cTT) {
                                        /*current.tmmtTexts.addAll(otherEntry.tmmtTexts);*/
                                    }
                                    current.tableFile = otherEntry.tableFile;
                                }
                            }
                        } else if (r[0].endsWith("Locator") || r[0].endsWith("Prefix")) {
                            current.strings.put(r[0], r[1]);
                        } else {
                            if (r[1].startsWith("[") && r[1].endsWith("]")) {
                                String[] offsets = r[1].substring(1, r[1].length() - 1).split(",");
                                if (offsets.length == 1 && offsets[0].trim().isEmpty()) {
                                    current.arrayEntries.put(r[0], new int[0]);
                                } else {
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

    private void loadTextTable(String filename) {
        try {
            Scanner sc = new Scanner(FileFunctions.openConfig(filename + ".tbl"), "UTF-8");
            while (sc.hasNextLine()) {
                String q = sc.nextLine();
                if (!q.trim().isEmpty()) {
                    String[] r = q.split("=", 2);
                    tb[Integer.parseInt(r[0], 16)] = r[1];
                    d.put(r[1], (byte) Integer.parseInt(r[0], 16));
                }
            }
            sc.close();
        } catch (FileNotFoundException e) {
            System.err.println("File not found!");
        }

    }

    // This ROM's data
    private Pokemon[] pokes, pokesInternal;
    private List<Pokemon> pokemonList;
    private int numRealPokemon;
    private RomEntry romEntry;
    private boolean havePatchedObedience;
    private String[] tb;
    public Map<String, Byte> d;
    private String[] abilityNames;
    private String[] itemNames;
    private boolean mapLoadingDone;
    private List<Integer> itemOffs;
    private String[][] mapNames;
    private boolean isRomHack;
    private int[] internalToPokedex, pokedexToInternal;
    private int pokedexCount;
    private String[] pokeNames;

    private static boolean detectRomInner(byte[] rom) {
        boolean match = false;
        for (RomEntry re : roms) {
            if (romCode(rom, re.romCode) && (rom[Gen3Constants.romVersionOffset] & 0xFF) == re.version) {
                match = true;
                break;
            }
        }
        return match;
    }

    @Override
    public void loadedRom() {
        for (RomEntry re : roms) {
            if (romCode(rom, re.romCode) && (rom[0xBC] & 0xFF) == re.version) {
                romEntry = new RomEntry(re); // clone so we can modify
                break;
            }
        }

        tb = new String[256];
        d = new HashMap<>();
        isRomHack = false;

        // Pokemon count stuff, needs to be available first
        List<Integer> pokedexOrderPrefixes = findMultiple(rom, Gen3Constants.pokedexOrderPointerPrefix);
        romEntry.entries.put("PokedexOrder", readPointer(pokedexOrderPrefixes.get(1) + 16));

        // Pokemon names offset
        if (romEntry.romType == Gen3Constants.RomType_Ruby || romEntry.romType == Gen3Constants.RomType_Sapp) {
            int baseNomOffset = find(rom, Gen3Constants.rsPokemonNamesPointerSuffix);
            romEntry.entries.put("PokemonNames", readPointer(baseNomOffset - 4));
            romEntry.entries.put(
                    "FrontSprites",
                    readPointer(findPointerPrefixAndSuffix(Gen3Constants.rsFrontSpritesPointerPrefix,
                            Gen3Constants.rsFrontSpritesPointerSuffix)));
            romEntry.entries.put(
                    "PokemonPalettes",
                    readPointer(findPointerPrefixAndSuffix(Gen3Constants.rsPokemonPalettesPointerPrefix,
                            Gen3Constants.rsPokemonPalettesPointerSuffix)));
        } else {
            romEntry.entries.put("PokemonNames", readPointer(Gen3Constants.efrlgPokemonNamesPointer));
            romEntry.entries.put("MoveNames", readPointer(Gen3Constants.efrlgMoveNamesPointer));
            romEntry.entries.put("AbilityNames", readPointer(Gen3Constants.efrlgAbilityNamesPointer));
            romEntry.entries.put("ItemData", readPointer(Gen3Constants.efrlgItemDataPointer));
            romEntry.entries.put("MoveData", readPointer(Gen3Constants.efrlgMoveDataPointer));
            romEntry.entries.put("PokemonStats", readPointer(Gen3Constants.efrlgPokemonStatsPointer));
            romEntry.entries.put("FrontSprites", readPointer(Gen3Constants.efrlgFrontSpritesPointer));
            romEntry.entries.put("PokemonPalettes", readPointer(Gen3Constants.efrlgPokemonPalettesPointer));
            romEntry.entries.put("MoveTutorCompatibility",
                    romEntry.getValue("MoveTutorData") + romEntry.getValue("MoveTutorMoves") * 2);
        }

        loadTextTable(romEntry.tableFile);


        loadPokemonNames();
        loadPokedex();
        loadPokemonStats();
        constructPokemonList();
        populateEvolutions();

        // Get wild Pokemon offset
        int baseWPOffset = findMultiple(rom, Gen3Constants.wildPokemonPointerPrefix).get(0);
        romEntry.entries.put("WildPokemon", readPointer(baseWPOffset + 12));

        // map banks
        int baseMapsOffset = findMultiple(rom, Gen3Constants.mapBanksPointerPrefix).get(0);
        romEntry.entries.put("MapHeaders", readPointer(baseMapsOffset + 12));
        this.determineMapBankSizes();

        // map labels
        if (romEntry.romType == Gen3Constants.RomType_FRLG) {
            int baseMLOffset = find(rom, Gen3Constants.frlgMapLabelsPointerPrefix);
            romEntry.entries.put("MapLabels", readPointer(baseMLOffset + 12));
        } else {
            int baseMLOffset = find(rom, Gen3Constants.rseMapLabelsPointerPrefix);
            romEntry.entries.put("MapLabels", readPointer(baseMLOffset + 12));
        }

        mapLoadingDone = false;
        loadAbilityNames();
        loadItemNames();
    }

    private int findPointerPrefixAndSuffix(String prefix, String suffix) {
        byte[] searchPref = new byte[prefix.length() / 2];
        for (int i = 0; i < searchPref.length; i++) {
            searchPref[i] = (byte) Integer.parseInt(prefix.substring(i * 2, i * 2 + 2), 16);
        }
        byte[] searchSuff = new byte[suffix.length() / 2];
        for (int i = 0; i < searchSuff.length; i++) {
            searchSuff[i] = (byte) Integer.parseInt(suffix.substring(i * 2, i * 2 + 2), 16);
        }
        if (searchPref.length >= searchSuff.length) {
            // Prefix first
            List<Integer> offsets = RomFunctions.search(rom, searchPref);
            return offsets.stream().findFirst().orElse(-1);
            /*for (int prefOffset : offsets) {
                int ptrOffset = prefOffset + searchPref.length;
                boolean suffixMatch = true;
                if (suffixMatch) {
                    ptr = ptrOffset;
                    break;
                }
            }*/
        } else {
            // Suffix first
            List<Integer> offsets = RomFunctions.search(rom, searchSuff);
            return offsets.stream().findFirst().orElse(-1);
/*            for (int suffOffset : offsets) {
                int ptrOffset = suffOffset - 4;
                boolean prefixMatch = true;
                for (int i = 0; i < searchPref.length; i++) {
                    if (rom[ptrOffset - searchPref.length + i] != searchPref[i]) {
                        prefixMatch = false;
                        break;
                    }
                }
                if (prefixMatch) {
                    return ptrOffset;
                }
            }
            return -1; // No match*/
        }
    }

    @Override
    public void savingRom() {
        savePokemonStats();
    }

    private void loadPokedex() {
        int pdOffset = romEntry.getValue("PokedexOrder");
        int numInternalPokes = romEntry.getValue("PokemonCount");
        int maxPokedex = 0;
        internalToPokedex = new int[numInternalPokes + 1];
        pokedexToInternal = new int[numInternalPokes + 1];
        for (int i = 1; i <= numInternalPokes; i++) {
            int dexEntry = readWord(rom, pdOffset + (i - 1) * 2);
            if (dexEntry != 0) {
                internalToPokedex[i] = dexEntry;
                // take the first pokemon only for each dex entry
                if (pokedexToInternal[dexEntry] == 0) {
                    pokedexToInternal[dexEntry] = i;
                }
                maxPokedex = Math.max(maxPokedex, dexEntry);
            }
        }
        if (maxPokedex == Gen3Constants.unhackedMaxPokedex) {
            // see if the slots between johto and hoenn are in use
            // old rom hacks use them instead of expanding pokes
            int usedSlots = 0;
            for (int i = 0; i < Gen3Constants.unhackedMaxPokedex - Gen3Constants.unhackedRealPokedex; i++) {
                int pokeSlot = Gen3Constants.hoennPokesStart + i;
                internalToPokedex[pokeSlot] = 0;
            }
            // remove the fake extra slots
            for (int i = usedSlots + 1; i <= Gen3Constants.unhackedMaxPokedex - Gen3Constants.unhackedRealPokedex; i++) {
                pokedexToInternal[Gen3Constants.unhackedRealPokedex + i] = 0;
            }
            this.pokedexCount = Gen3Constants.unhackedRealPokedex + usedSlots;
        }

    }

    private void constructPokemonList() {
        if (!this.isRomHack) {
            // simple behavior: all pokes in the dex are valid
            pokemonList = Arrays.asList(pokes);
        }
        numRealPokemon = pokemonList.size() - 1;

    }

    private void loadPokemonStats() {
        pokes = new Pokemon[this.pokedexCount + 1];
        int numInternalPokes = romEntry.getValue("PokemonCount");
        pokesInternal = new Pokemon[numInternalPokes + 1];
        int offs = romEntry.getValue("PokemonStats");
        for (int i = 1; i <= numInternalPokes; i++) {
            Pokemon pk = new Pokemon();
            pk.name = pokeNames[i];
            pk.number = internalToPokedex[i];
            if (pk.number != 0) {
                pokes[pk.number] = pk;
            }
            pokesInternal[i] = pk;
            int pkoffs = offs + i * Gen3Constants.baseStatsEntrySize;
            loadBasicPokeStats(pk, pkoffs);
        }

        // In these games, the alternate formes of Deoxys have hardcoded stats that are used 99% of the time;
        // the only times these hardcoded stats are ignored are during Link Battles. Since not many people
        // are using the randomizer to battle against others, let's just always use these stats.
        if (romEntry.romType == Gen3Constants.RomType_FRLG || romEntry.romType == Gen3Constants.RomType_Em) {
            String deoxysStatPrefix = romEntry.strings.get("DeoxysStatPrefix");
            int offset = find(deoxysStatPrefix);
            if (offset > 0) {
                offset += deoxysStatPrefix.length() / 2; // because it was a prefix
                Pokemon deoxys = pokes[Species.deoxys];
                deoxys.hp = readWord(offset);
                deoxys.attack = readWord(offset + 2);
                deoxys.defense = readWord(offset + 4);
                deoxys.speed = readWord(offset + 6);
                deoxys.spatk = readWord(offset + 8);
                deoxys.spdef = readWord(offset + 10);
            }
        }
    }

    private void savePokemonStats() {
        // Write pokemon names & stats
        int offs = romEntry.getValue("PokemonNames");
        int nameLen = romEntry.getValue("PokemonNameLength");
        int offs2 = romEntry.getValue("PokemonStats");
        int numInternalPokes = romEntry.getValue("PokemonCount");
        for (int i = 1; i <= numInternalPokes; i++) {
            Pokemon pk = pokesInternal[i];
            int stringOffset = offs + i * nameLen;
            writeFixedLengthString(pk.name, stringOffset, nameLen);
            saveBasicPokeStats(pk, offs2 + i * Gen3Constants.baseStatsEntrySize);
        }

        // Make sure to write to the hardcoded Deoxys stat location, since otherwise it will just have vanilla
        // stats no matter what settings the user selected.
        if (romEntry.romType == Gen3Constants.RomType_FRLG || romEntry.romType == Gen3Constants.RomType_Em) {
            String deoxysStatPrefix = romEntry.strings.get("DeoxysStatPrefix");
            int offset = find(deoxysStatPrefix);
            if (offset > 0) {
                offset += deoxysStatPrefix.length() / 2; // because it was a prefix
                Pokemon deoxys = pokes[Species.deoxys];
                writeWord(offset, deoxys.hp);
                writeWord(offset + 2, deoxys.attack);
                writeWord(offset + 4, deoxys.defense);
                writeWord(offset + 6, deoxys.speed);
                writeWord(offset + 8, deoxys.spatk);
                writeWord(offset + 10, deoxys.spdef);
            }
        }

        writeEvolutions();
    }

    private void loadBasicPokeStats(Pokemon pkmn, int offset) {
        pkmn.hp = rom[offset + Gen3Constants.bsHPOffset] & 0xFF;
        pkmn.attack = rom[offset + Gen3Constants.bsAttackOffset] & 0xFF;
        pkmn.defense = rom[offset + Gen3Constants.bsDefenseOffset] & 0xFF;
        pkmn.speed = rom[offset + Gen3Constants.bsSpeedOffset] & 0xFF;
        pkmn.spatk = rom[offset + Gen3Constants.bsSpAtkOffset] & 0xFF;
        pkmn.spdef = rom[offset + Gen3Constants.bsSpDefOffset] & 0xFF;
        // Type
        pkmn.primaryType = Gen3Constants.typeTable[rom[offset + Gen3Constants.bsPrimaryTypeOffset] & 0xFF];
        pkmn.secondaryType = Gen3Constants.typeTable[rom[offset + Gen3Constants.bsSecondaryTypeOffset] & 0xFF];
        // Only one type?
        if (pkmn.secondaryType == pkmn.primaryType) {
            pkmn.secondaryType = null;
        }
        pkmn.catchRate = rom[offset + Gen3Constants.bsCatchRateOffset] & 0xFF;
        pkmn.growthCurve = ExpCurve.fromByte(rom[offset + Gen3Constants.bsGrowthCurveOffset]);
        // Abilities
        pkmn.ability1 = rom[offset + Gen3Constants.bsAbility1Offset] & 0xFF;
        pkmn.ability2 = rom[offset + Gen3Constants.bsAbility2Offset] & 0xFF;

        // Held Items?
        int item1 = readWord(offset + Gen3Constants.bsCommonHeldItemOffset);
        int item2 = readWord(offset + Gen3Constants.bsRareHeldItemOffset);

        if (item1 == item2) {
            // guaranteed
            pkmn.guaranteedHeldItem = item1;
            pkmn.commonHeldItem = 0;
            pkmn.rareHeldItem = 0;
        } else {
            pkmn.guaranteedHeldItem = 0;
            pkmn.commonHeldItem = item1;
            pkmn.rareHeldItem = item2;
        }
        pkmn.darkGrassHeldItem = -1;

        pkmn.genderRatio = rom[offset + Gen3Constants.bsGenderRatioOffset] & 0xFF;
    }

    private void saveBasicPokeStats(Pokemon pkmn, int offset) {
        rom[offset + Gen3Constants.bsHPOffset] = (byte) pkmn.hp;
        rom[offset + Gen3Constants.bsAttackOffset] = (byte) pkmn.attack;
        rom[offset + Gen3Constants.bsDefenseOffset] = (byte) pkmn.defense;
        rom[offset + Gen3Constants.bsSpeedOffset] = (byte) pkmn.speed;
        rom[offset + Gen3Constants.bsSpAtkOffset] = (byte) pkmn.spatk;
        rom[offset + Gen3Constants.bsSpDefOffset] = (byte) pkmn.spdef;
        rom[offset + Gen3Constants.bsPrimaryTypeOffset] = Gen3Constants.typeToByte(pkmn.primaryType);
        if (pkmn.secondaryType == null) {
            rom[offset + Gen3Constants.bsSecondaryTypeOffset] = rom[offset + Gen3Constants.bsPrimaryTypeOffset];
        } else {
            rom[offset + Gen3Constants.bsSecondaryTypeOffset] = Gen3Constants.typeToByte(pkmn.secondaryType);
        }
        rom[offset + Gen3Constants.bsCatchRateOffset] = (byte) pkmn.catchRate;
        rom[offset + Gen3Constants.bsGrowthCurveOffset] = pkmn.growthCurve.toByte();

        rom[offset + Gen3Constants.bsAbility1Offset] = (byte) pkmn.ability1;
        if (pkmn.ability2 == 0) {
            // required to not break evos with random ability
            rom[offset + Gen3Constants.bsAbility2Offset] = (byte) pkmn.ability1;
        } else {
            rom[offset + Gen3Constants.bsAbility2Offset] = (byte) pkmn.ability2;
        }

        // Held items
        if (pkmn.guaranteedHeldItem > 0) {
            writeWord(offset + Gen3Constants.bsCommonHeldItemOffset, pkmn.guaranteedHeldItem);
            writeWord(offset + Gen3Constants.bsRareHeldItemOffset, pkmn.guaranteedHeldItem);
        } else {
            writeWord(offset + Gen3Constants.bsCommonHeldItemOffset, pkmn.commonHeldItem);
            writeWord(offset + Gen3Constants.bsRareHeldItemOffset, pkmn.rareHeldItem);
        }

        rom[offset + Gen3Constants.bsGenderRatioOffset] = (byte) pkmn.genderRatio;
    }

    private void loadPokemonNames() {
        int offs = romEntry.getValue("PokemonNames");
        int nameLen = romEntry.getValue("PokemonNameLength");
        int numInternalPokes = romEntry.getValue("PokemonCount");
        pokeNames = new String[numInternalPokes + 1];
        for (int i = 1; i <= numInternalPokes; i++) {
            pokeNames[i] = readFixedLengthString(offs + i * nameLen, nameLen);
        }
    }

    private String readString(int offset, int maxLength) {
        StringBuilder string = new StringBuilder();
        for (int c = 0; c < maxLength; c++) {
            int currChar = rom[offset + c] & 0xFF;
            if (tb[currChar] != null) {
                string.append(tb[currChar]);
            } else {
                if (currChar == Gen3Constants.textTerminator) {
                    break;
                } else if (currChar == Gen3Constants.textVariable) {
                    int nextChar = rom[offset + c + 1] & 0xFF;
                    string.append("\\v").append(String.format("%02X", nextChar));
                    c++;
                }
            }
        }
        return string.toString();
    }

    private byte[] translateString(String text) {
        List<Byte> data = new ArrayList<>();
        while (text.length() != 0) {
            int i = Math.max(0, 4 - text.length());
            if (text.charAt(0) == '\\' && text.charAt(1) == 'v') {
                data.add((byte) Gen3Constants.textVariable);
                data.add((byte) Integer.parseInt(text.substring(2, 4), 16));
                text = text.substring(4);
            } else {
                while (!(d.containsKey(text.substring(0, 4 - i)) || (i == 4))) {
                    i++;
                }
               {
                    data.add(d.get(text.substring(0, 4 - i)));
                    text = text.substring(4 - i);
                }
            }
        }
        byte[] ret = new byte[data.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = data.get(i);
        }
        return ret;
    }

    private String readFixedLengthString(int offset, int length) {
        return readString(offset, length);
    }

    private String readVariableLengthString(int offset) {
        return readString(offset, Integer.MAX_VALUE);
    }

    private void writeFixedLengthString(String str, int offset, int length) {
        byte[] translated = translateString(str);
        int len = Math.min(translated.length, length);
        System.arraycopy(translated, 0, rom, offset, len);
        if (len < length) {
            rom[offset + len] = (byte) Gen3Constants.textTerminator;
            len++;
        }
        while (len < length) {
            rom[offset + len] = 0;
            len++;
        }
    }

    private int lengthOfStringAt(int offset) {
        int len = 0;
        while ((rom[offset + (len++)] & 0xFF) != 0xFF) {
        }
        return len - 1;
    }

    private static boolean romCode(byte[] rom, String codeToCheck) {
        try {
            int sigOffset = Gen3Constants.romCodeOffset;
            byte[] sigBytes = codeToCheck.getBytes("US-ASCII");
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

    private int readPointer(int offset) {
        return readLong(offset) - 0x8000000;
    }

    private int readLong(int offset) {
        return (rom[offset] & 0xFF) + ((rom[offset + 1] & 0xFF) << 8) + ((rom[offset + 2] & 0xFF) << 16)
                + (((rom[offset + 3] & 0xFF)) << 24);
    }

    private void writePointer(int offset, int pointer) {
        writeLong(offset, pointer + 0x8000000);
    }

    private void writeLong(int offset, int value) {
        rom[offset] = (byte) (value & 0xFF);
        rom[offset + 1] = (byte) ((value >> 8) & 0xFF);
        rom[offset + 2] = (byte) ((value >> 16) & 0xFF);
        rom[offset + 3] = (byte) (((value >> 24) & 0xFF));
    }

    @Override
    public List<Pokemon> getStarters() {
        List<Pokemon> starters = new ArrayList<>();
        int baseOffset = romEntry.getValue("StarterPokemon");
        if (romEntry.romType == Gen3Constants.RomType_Ruby || romEntry.romType == Gen3Constants.RomType_Sapp
                || romEntry.romType == Gen3Constants.RomType_Em) {
            // do something
            Pokemon starter1 = pokesInternal[readWord(baseOffset)];
            Pokemon starter2 = pokesInternal[readWord(baseOffset + Gen3Constants.rseStarter2Offset)];
            Pokemon starter3 = pokesInternal[readWord(baseOffset + Gen3Constants.rseStarter3Offset)];
            starters.add(starter1);
            starters.add(starter2);
            starters.add(starter3);
        } else {
            // do something else
            Pokemon starter1 = pokesInternal[readWord(baseOffset)];
            Pokemon starter2 = pokesInternal[readWord(baseOffset + Gen3Constants.frlgStarter2Offset)];
            Pokemon starter3 = pokesInternal[readWord(baseOffset + Gen3Constants.frlgStarter3Offset)];
            starters.add(starter1);
            starters.add(starter2);
            starters.add(starter3);
        }
        return starters;
    }

    @Override
    public boolean setStarters(List<Pokemon> newStarters) {

        // Support Deoxys/Mew starters in E/FR/LG
        attemptObedienceEvolutionPatches();
        int baseOffset = romEntry.getValue("StarterPokemon");

        int starter0 = pokedexToInternal[newStarters.get(0).number];
        int starter1 = pokedexToInternal[newStarters.get(1).number];
        int starter2 = pokedexToInternal[newStarters.get(2).number];
        if (romEntry.romType == Gen3Constants.RomType_Ruby || romEntry.romType == Gen3Constants.RomType_Sapp
                || romEntry.romType == Gen3Constants.RomType_Em) {

            // US
            // order: 0, 1, 2
            writeWord(baseOffset, starter0);
            writeWord(baseOffset + Gen3Constants.rseStarter2Offset, starter1);
            writeWord(baseOffset + Gen3Constants.rseStarter3Offset, starter2);

        } else {
            // frlg:
            // order: 0, 1, 2
            writeWord(baseOffset, starter0);
            writeWord(baseOffset + Gen3Constants.frlgStarterRepeatOffset, starter1);

            writeWord(baseOffset + Gen3Constants.frlgStarter2Offset, starter1);
            writeWord(baseOffset + Gen3Constants.frlgStarter2Offset + Gen3Constants.frlgStarterRepeatOffset, starter2);

            writeWord(baseOffset + Gen3Constants.frlgStarter3Offset, starter2);
            writeWord(baseOffset + Gen3Constants.frlgStarter3Offset + Gen3Constants.frlgStarterRepeatOffset, starter0);

            if (romEntry.romCode.charAt(3) != 'J' && romEntry.romCode.charAt(3) != 'B') {
                // Update PROF. Oak's descriptions for each starter
                // First result for each STARTERNAME is the text we need
                List<Integer> bulbasaurFoundTexts = RomFunctions.search(rom, translateString(pokes[Gen3Constants.frlgBaseStarter1].name.toUpperCase()));
                List<Integer> charmanderFoundTexts = RomFunctions.search(rom, translateString(pokes[Gen3Constants.frlgBaseStarter2].name.toUpperCase()));
                List<Integer> squirtleFoundTexts = RomFunctions.search(rom, translateString(pokes[Gen3Constants.frlgBaseStarter3].name.toUpperCase()));
                writeFRLGStarterText(bulbasaurFoundTexts, newStarters.get(0), "you want to go with\\nthe ");
                writeFRLGStarterText(charmanderFoundTexts, newStarters.get(1), "you’re claiming the\\n");
                writeFRLGStarterText(squirtleFoundTexts, newStarters.get(2), "you’ve decided on the\\n");
            }
        }
        return true;

    }

    @Override
    public int starterCount() {
        return 3;
    }

    private void writeFRLGStarterText(List<Integer> foundTexts, Pokemon pkmn, String oakText) {
        if (foundTexts.size() > 0) {
            int offset = foundTexts.get(0);
            String pokeName = pkmn.name;
            String pokeType = pkmn.primaryType == null ? "???" : pkmn.primaryType.toString();
            if (pokeType.equals("NORMAL") && pkmn.secondaryType != null) {
                pokeType = pkmn.secondaryType.toString();
            }
            String speech = pokeName + " is your choice.\\pSo, \\v01, " + oakText + pokeType + " POKéMON " + pokeName
                    + "?";
            writeFixedLengthString(speech, offset, lengthOfStringAt(offset) + 1);
        }
    }

    @Override
    public List<EncounterSet> getEncounters(boolean useTimeOfDay) {
        if (!mapLoadingDone) {
            preprocessMaps();
            mapLoadingDone = true;
        }

        int startOffs = romEntry.getValue("WildPokemon");
        List<EncounterSet> encounterAreas = new ArrayList<>();
        Set<Integer> seenOffsets = new TreeSet<>();
        int offs = startOffs;
        while (true) {
            // Read pointers
            int bank = rom[offs] & 0xFF;
            int map = rom[offs + 1] & 0xFF;
            if (bank == 0xFF && map == 0xFF) {
                break;
            }

            String mapName = mapNames[bank][map];

            int grassPokes = readPointer(offs + 4);
            int waterPokes = readPointer(offs + 8);
            int treePokes = readPointer(offs + 12);
            int fishPokes = readPointer(offs + 16);

            // Add pokemanz
            if (grassPokes >= 0 && grassPokes < rom.length && rom[grassPokes] != 0
                    && !seenOffsets.contains(readPointer(grassPokes + 4))) {
                encounterAreas.add(readWildArea(grassPokes, Gen3Constants.grassSlots, mapName + " Grass/Cave"));
                seenOffsets.add(readPointer(grassPokes + 4));
            }
            if (waterPokes >= 0 && waterPokes < rom.length && rom[waterPokes] != 0
                    && !seenOffsets.contains(readPointer(waterPokes + 4))) {
                encounterAreas.add(readWildArea(waterPokes, Gen3Constants.surfingSlots, mapName + " Surfing"));
                seenOffsets.add(readPointer(waterPokes + 4));
            }
            if (treePokes >= 0 && treePokes < rom.length && rom[treePokes] != 0
                    && !seenOffsets.contains(readPointer(treePokes + 4))) {
                encounterAreas.add(readWildArea(treePokes, Gen3Constants.rockSmashSlots, mapName + " Rock Smash"));
                seenOffsets.add(readPointer(treePokes + 4));
            }
            if (fishPokes >= 0 && fishPokes < rom.length && rom[fishPokes] != 0
                    && !seenOffsets.contains(readPointer(fishPokes + 4))) {
                encounterAreas.add(readWildArea(fishPokes, Gen3Constants.fishingSlots, mapName + " Fishing"));
                seenOffsets.add(readPointer(fishPokes + 4));
            }

            offs += 20;
        }
        if (romEntry.arrayEntries.containsKey("BattleTrappersBanned")) {
            // Some encounter sets aren't allowed to have Pokemon
            // with Arena Trap, Shadow Tag etc.
            int[] bannedAreas = romEntry.arrayEntries.get("BattleTrappersBanned");
            Set<Pokemon> battleTrappers = new HashSet<>();
            for (Pokemon pk : getPokemon()) {
                if (hasBattleTrappingAbility(pk)) {
                    battleTrappers.add(pk);
                }
            }
            for (int areaIdx : bannedAreas) {
                encounterAreas.get(areaIdx).bannedPokemon.addAll(battleTrappers);
            }
        }
        return encounterAreas;
    }

    private boolean hasBattleTrappingAbility(Pokemon pokemon) {
        return pokemon != null
                && (GlobalConstants.battleTrappingAbilities.contains(pokemon.ability1) || GlobalConstants.battleTrappingAbilities
                        .contains(pokemon.ability2));
    }

    private EncounterSet readWildArea(int offset, int numOfEntries, String setName) {
        EncounterSet thisSet = new EncounterSet();
        thisSet.rate = rom[offset];
        thisSet.displayName = setName;
        // Grab the *real* pointer to data
        int dataOffset = readPointer(offset + 4);
        // Read the entries
        for (int i = 0; i < numOfEntries; i++) {
            // min, max, species, species
            Encounter enc = new Encounter();
            enc.level = rom[dataOffset + i * 4];
            enc.maxLevel = rom[dataOffset + i * 4 + 1];
            enc.pokemon = pokesInternal[readWord(dataOffset + i * 4 + 2)];

            thisSet.encounters.add(enc);
        }
        return thisSet;
    }

    @Override
    public void setEncounters(boolean useTimeOfDay, List<EncounterSet> encounters) {
        // Support Deoxys/Mew catches in E/FR/LG
        attemptObedienceEvolutionPatches();

        int startOffs = romEntry.getValue("WildPokemon");
        Iterator<EncounterSet> encounterAreas = encounters.iterator();
        Set<Integer> seenOffsets = new TreeSet<>();
        int offs = startOffs;
        while (true) {
            // Read pointers
            int bank = rom[offs] & 0xFF;
            int map = rom[offs + 1] & 0xFF;
            if (bank == 0xFF && map == 0xFF) {
                break;
            }

            int grassPokes = readPointer(offs + 4);
            int waterPokes = readPointer(offs + 8);
            int treePokes = readPointer(offs + 12);
            int fishPokes = readPointer(offs + 16);

            // Add pokemanz
            if (grassPokes >= 0 && grassPokes < rom.length && rom[grassPokes] != 0
                    && !seenOffsets.contains(readPointer(grassPokes + 4))) {
                writeWildArea(grassPokes, Gen3Constants.grassSlots, encounterAreas.next());
                seenOffsets.add(readPointer(grassPokes + 4));
            }
            if (waterPokes >= 0 && waterPokes < rom.length && rom[waterPokes] != 0
                    && !seenOffsets.contains(readPointer(waterPokes + 4))) {
                writeWildArea(waterPokes, Gen3Constants.surfingSlots, encounterAreas.next());
                seenOffsets.add(readPointer(waterPokes + 4));
            }
            if (treePokes >= 0 && treePokes < rom.length && rom[treePokes] != 0
                    && !seenOffsets.contains(readPointer(treePokes + 4))) {
                writeWildArea(treePokes, Gen3Constants.rockSmashSlots, encounterAreas.next());
                seenOffsets.add(readPointer(treePokes + 4));
            }
            if (fishPokes >= 0 && fishPokes < rom.length && rom[fishPokes] != 0
                    && !seenOffsets.contains(readPointer(fishPokes + 4))) {
                writeWildArea(fishPokes, Gen3Constants.fishingSlots, encounterAreas.next());
                seenOffsets.add(readPointer(fishPokes + 4));
            }

            offs += 20;
        }
    }

    @Override
    public List<Trainer> getTrainers() {
        int baseOffset = romEntry.getValue("TrainerData");
        int amount = romEntry.getValue("TrainerCount");
        int entryLen = romEntry.getValue("TrainerEntrySize");
        List<Trainer> theTrainers = new ArrayList<>();
        List<String> tcnames = this.getTrainerClassNames();
        for (int i = 1; i < amount; i++) {
            // Trainer entries are 40 bytes
            // Team flags; 1 byte; 0x01 = custom moves, 0x02 = held item
            // Class; 1 byte
            // Encounter Music and gender; 1 byte
            // Battle Sprite; 1 byte
            // Name; 12 bytes; 0xff terminated
            // Items; 2 bytes each, 4 item slots
            // Battle Mode; 1 byte; 0 means single, 1 means double.
            // 3 bytes not used
            // AI Flags; 1 byte
            // 3 bytes not used
            // Number of pokemon in team; 1 byte
            // 3 bytes not used
            // Pointer to pokemon; 4 bytes
            // https://github.com/pret/pokefirered/blob/3dce3407d5f9bca69d61b1cf1b314fb1e921d572/include/battle.h#L111
            int trOffset = baseOffset + i * entryLen;
            Trainer tr = new Trainer();
            tr.offset = trOffset;
            tr.index = i;
            int trainerclass = rom[trOffset + 1] & 0xFF;
            tr.trainerclass = (rom[trOffset + 2] & 0x80) > 0 ? 1 : 0;

            int pokeDataType = rom[trOffset] & 0xFF;
            int numPokes = rom[trOffset + (entryLen - 8)] & 0xFF;
            int pointerToPokes = readPointer(trOffset + (entryLen - 4));
            tr.poketype = pokeDataType;
            tr.name = this.readVariableLengthString(trOffset + 4);
            tr.fullDisplayName = tcnames.get(trainerclass) + " " + tr.name;
            // Pokemon structure data is like
            // IV IV LV SP SP
            // (HI HI)
            // (M1 M1 M2 M2 M3 M3 M4 M4)
            // IV is a "difficulty" level between 0 and 255 to represent 0 to 31 IVs.
            //     These IVs affect all attributes. For the vanilla games, the majority
            //     of trainers have 0 IVs; Elite Four members will have 31 IVs.
            // https://github.com/pret/pokeemerald/blob/6c38837b266c0dd36ccdd04559199282daa7a8a0/include/data.h#L22
            if (pokeDataType == 0) {
                // blocks of 8 bytes
                for (int poke = 0; poke < numPokes; poke++) {
                    TrainerPokemon thisPoke = new TrainerPokemon();
                    thisPoke.IVs = ((readWord(pointerToPokes + poke * 8) & 0xFF) * 31) / 255;
                    thisPoke.level = readWord(pointerToPokes + poke * 8 + 2);
                    thisPoke.pokemon = pokesInternal[readWord(pointerToPokes + poke * 8 + 4)];
                    tr.pokemon.add(thisPoke);
                }
            } else if (pokeDataType == 2) {
                // blocks of 8 bytes
                for (int poke = 0; poke < numPokes; poke++) {
                    TrainerPokemon thisPoke = new TrainerPokemon();
                    thisPoke.IVs = ((readWord(pointerToPokes + poke * 8) & 0xFF) * 31) / 255;
                    thisPoke.level = readWord(pointerToPokes + poke * 8 + 2);
                    thisPoke.pokemon = pokesInternal[readWord(pointerToPokes + poke * 8 + 4)];
                    thisPoke.heldItem = readWord(pointerToPokes + poke * 8 + 6);
                    tr.pokemon.add(thisPoke);
                }
            } else if (pokeDataType == 1) {
                // blocks of 16 bytes
                for (int poke = 0; poke < numPokes; poke++) {
                    TrainerPokemon thisPoke = new TrainerPokemon();
                    thisPoke.IVs = ((readWord(pointerToPokes + poke * 16) & 0xFF) * 31) / 255;
                    thisPoke.level = readWord(pointerToPokes + poke * 16 + 2);
                    thisPoke.pokemon = pokesInternal[readWord(pointerToPokes + poke * 16 + 4)];
                    for (int move = 0; move < 4; move++) {
                        thisPoke.moves[move] = readWord(pointerToPokes + poke * 16 + 6 + (move*2));
                    }
                    tr.pokemon.add(thisPoke);
                }
            } else if (pokeDataType == 3) {
                // blocks of 16 bytes
                for (int poke = 0; poke < numPokes; poke++) {
                    TrainerPokemon thisPoke = new TrainerPokemon();
                    thisPoke.IVs = ((readWord(pointerToPokes + poke * 16) & 0xFF) * 31) / 255;
                    thisPoke.level = readWord(pointerToPokes + poke * 16 + 2);
                    thisPoke.pokemon = pokesInternal[readWord(pointerToPokes + poke * 16 + 4)];
                    thisPoke.heldItem = readWord(pointerToPokes + poke * 16 + 6);
                    for (int move = 0; move < 4; move++) {
                        thisPoke.moves[move] = readWord(pointerToPokes + poke * 16 + 8 + (move*2));
                    }
                    tr.pokemon.add(thisPoke);
                }
            }
            theTrainers.add(tr);
        }

        if (romEntry.romType == Gen3Constants.RomType_Ruby || romEntry.romType == Gen3Constants.RomType_Sapp) {
            Gen3Constants.trainerTagsRS(theTrainers, romEntry.romType);
        } else if (romEntry.romType == Gen3Constants.RomType_Em) {
            Gen3Constants.trainerTagsE(theTrainers);
            Gen3Constants.setMultiBattleStatusEm(theTrainers);
        } else {
            Gen3Constants.trainerTagsFRLG(theTrainers);
        }
        return theTrainers;
    }

    @Override
    public List<Integer> getMainPlaythroughTrainers() {
        return new ArrayList<>(); // Not implemented
    }


    @Override
    public void setTrainers(List<Trainer> trainerData, boolean doubleBattleMode) {
        int baseOffset = romEntry.getValue("TrainerData");
        int amount = romEntry.getValue("TrainerCount");
        int entryLen = romEntry.getValue("TrainerEntrySize");
        Iterator<Trainer> theTrainers = trainerData.iterator();

        // Get current movesets in case we need to reset them for certain
        // trainer mons.
        Map<Integer, List<MoveLearnt>> movesets = this.getMovesLearnt();

        for (int i = 1; i < amount; i++) {
            int trOffset = baseOffset + i * entryLen;
            Trainer tr = theTrainers.next();
            // Do we need to repoint this trainer's data?
            int newPokeCount = tr.pokemon.size();

            // write out new data first...
            rom[trOffset] = (byte) tr.poketype;
            rom[trOffset + (entryLen - 8)] = (byte) newPokeCount;

            // now, do we need to repoint?
            int pointerToPokes = readPointer(trOffset + (entryLen - 4));


            Iterator<TrainerPokemon> pokes = tr.pokemon.iterator();

            // Write out Pokemon data!
            if (tr.pokemonHaveCustomMoves()) {
                // custom moves, blocks of 16 bytes
                for (int poke = 0; poke < newPokeCount; poke++) {
                    TrainerPokemon tp = pokes.next();
                    // Add 1 to offset integer division truncation
                    writeWord(pointerToPokes + poke * 16, Math.min(255, 1 + (tp.IVs * 255) / 31));
                    writeWord(pointerToPokes + poke * 16 + 2, tp.level);
                    writeWord(pointerToPokes + poke * 16 + 4, pokedexToInternal[tp.pokemon.number]);
                    int movesStart;
                    if (tr.pokemonHaveItems()) {
                        writeWord(pointerToPokes + poke * 16 + 6, tp.heldItem);
                        movesStart = 8;
                    } else {
                        movesStart = 6;
                        writeWord(pointerToPokes + poke * 16 + 14, 0);
                    }
                    if (tp.resetMoves) {
                        int[] pokeMoves = RomFunctions.getMovesAtLevel(tp.pokemon.number, movesets, tp.level);
                        for (int m = 0; m < 4; m++) {
                            writeWord(pointerToPokes + poke * 16 + movesStart + m * 2, pokeMoves[m]);
                        }
                    } else {
                        writeWord(pointerToPokes + poke * 16 + movesStart, tp.moves[0]);
                        writeWord(pointerToPokes + poke * 16 + movesStart + 2, tp.moves[1]);
                        writeWord(pointerToPokes + poke * 16 + movesStart + 4, tp.moves[2]);
                        writeWord(pointerToPokes + poke * 16 + movesStart + 6, tp.moves[3]);
                    }
                }
            } else {
                // no moves, blocks of 8 bytes
                for (int poke = 0; poke < newPokeCount; poke++) {
                    TrainerPokemon tp = pokes.next();
                    writeWord(pointerToPokes + poke * 8, Math.min(255, 1 + (tp.IVs * 255) / 31));
                    writeWord(pointerToPokes + poke * 8 + 2, tp.level);
                    writeWord(pointerToPokes + poke * 8 + 4, pokedexToInternal[tp.pokemon.number]);
                    if (tr.pokemonHaveItems()) {
                        writeWord(pointerToPokes + poke * 8 + 6, tp.heldItem);
                    } else {
                        writeWord(pointerToPokes + poke * 8 + 6, 0);
                    }
                }
            }
        }

    }

    private void writeWildArea(int offset, int numOfEntries, EncounterSet encounters) {
        // Grab the *real* pointer to data
        int dataOffset = readPointer(offset + 4);
        // Write the entries
        for (int i = 0; i < numOfEntries; i++) {
            Encounter enc = encounters.encounters.get(i);
            // min, max, species, species
            int levels = enc.level | (enc.maxLevel << 8);
            writeWord(dataOffset + i * 4, levels);
            writeWord(dataOffset + i * 4 + 2, pokedexToInternal[enc.pokemon.number]);
        }
    }

    @Override
    public List<Pokemon> getPokemon() {
        return pokemonList;
    }

    @Override
    public List<Pokemon> getPokemonInclFormes() {
        return pokemonList; // No alt formes for now, should include Deoxys formes in the future
    }

    @Override
    public Map<Integer, List<MoveLearnt>> getMovesLearnt() {
        Map<Integer, List<MoveLearnt>> movesets = new TreeMap<>();
        int baseOffset = romEntry.getValue("PokemonMovesets");
        for (int i = 1; i <= numRealPokemon; i++) {
            Pokemon pkmn = pokemonList.get(i);
            int offsToPtr = baseOffset + (pokedexToInternal[pkmn.number]) * 4;
            int moveDataLoc = readPointer(offsToPtr);
            List<MoveLearnt> moves = new ArrayList<>();
            {
                while ((rom[moveDataLoc] & 0xFF) != 0xFF || (rom[moveDataLoc + 1] & 0xFF) != 0xFF) {
                    int move = (rom[moveDataLoc] & 0xFF);
                    int level = (rom[moveDataLoc + 1] & 0xFE) >> 1;
                    if ((rom[moveDataLoc + 1] & 0x01) == 0x01) {
                        move += 0x100;
                    }
                    MoveLearnt ml = new MoveLearnt();
                    ml.level = level;
                    ml.move = move;
                    moves.add(ml);
                    moveDataLoc += 2;
                }
            }
            movesets.put(pkmn.number, moves);
        }
        return movesets;
    }

    private static class StaticPokemon {
        private int[] speciesOffsets;
        private int[] levelOffsets;

        public StaticPokemon() {
            this.speciesOffsets = new int[0];
            this.levelOffsets = new int[0];
        }

        public Pokemon getPokemon(Gen3RomHandler parent) {
            return parent.pokesInternal[parent.readWord(speciesOffsets[0])];
        }

        public void setPokemon(Gen3RomHandler parent, Pokemon pkmn) {
            int value = parent.pokedexToInternal[pkmn.number];
            for (int offset : speciesOffsets) {
                parent.writeWord(offset, value);
            }
        }

        public int getLevel(byte[] rom, int i) {
            if (levelOffsets.length <= i) {
                return 1;
            }
            return rom[levelOffsets[i]];
        }

        public void setLevel(byte[] rom, int level, int i) {
            if (levelOffsets.length > i) { // Might not have a level entry e.g., it's an egg
                rom[levelOffsets[i]] = (byte) level;
            }
        }
    }

    @Override
    public List<StaticEncounter> getStaticPokemon() {
        List<StaticEncounter> statics = new ArrayList<>();
        List<StaticPokemon> staticsHere = romEntry.staticPokemon;
        int[] staticEggOffsets = new int[0];
        if (romEntry.arrayEntries.containsKey("StaticEggPokemonOffsets")) {
            staticEggOffsets = romEntry.arrayEntries.get("StaticEggPokemonOffsets");
        }
        for (int i = 0; i < staticsHere.size(); i++) {
            int currentOffset = i;
            StaticPokemon staticPK = staticsHere.get(i);
            StaticEncounter se = new StaticEncounter();
            se.pkmn = staticPK.getPokemon(this);
            se.level = staticPK.getLevel(rom, 0);
            se.isEgg = Arrays.stream(staticEggOffsets).anyMatch(x-> x == currentOffset);
            statics.add(se);
        }

        if (romEntry.codeTweaks.get("StaticFirstBattleTweak") != null) {
            // Read in and randomize the static starting Poochyena/Zigzagoon fight in RSE
            int startingSpeciesOffset = romEntry.getValue("StaticFirstBattleSpeciesOffset");
            int species = readWord(startingSpeciesOffset);
            if (species == 0xFFFF) {
                // Patch hasn't been applied, so apply it first
                try {
                    FileFunctions.applyPatch(rom, romEntry.codeTweaks.get("StaticFirstBattleTweak"));
                    species = readWord(startingSpeciesOffset);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            Pokemon pkmn = pokesInternal[species];
            int startingLevelOffset = romEntry.getValue("StaticFirstBattleLevelOffset");
            int level = rom[startingLevelOffset];
            StaticEncounter se = new StaticEncounter();
            se.pkmn = pkmn;
            se.level = level;
            statics.add(se);
        } else if (romEntry.codeTweaks.get("GhostMarowakTweak") != null) {
            // Read in and randomize the static Ghost Marowak fight in FRLG
            int[] ghostMarowakOffsets = romEntry.arrayEntries.get("GhostMarowakSpeciesOffsets");
            int species = readWord(ghostMarowakOffsets[0]);
            if (species == 0xFFFF) {
                // Patch hasn't been applied, so apply it first
                try {
                    FileFunctions.applyPatch(rom, romEntry.codeTweaks.get("GhostMarowakTweak"));
                    species = readWord(ghostMarowakOffsets[0]);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            Pokemon pkmn = pokesInternal[species];
            int[] startingLevelOffsets = romEntry.arrayEntries.get("GhostMarowakLevelOffsets");
            int level = rom[startingLevelOffsets[0]];
            StaticEncounter se = new StaticEncounter();
            se.pkmn = pkmn;
            se.level = level;
            statics.add(se);
        }

        try {
            getRoamers(statics);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return statics;
    }

    @Override
    public boolean setStaticPokemon(List<StaticEncounter> staticPokemon) {
        // Support Deoxys/Mew gifts/catches in E/FR/LG
        attemptObedienceEvolutionPatches();

        List<StaticPokemon> staticsHere = romEntry.staticPokemon;

        for (int i = 0; i < staticsHere.size(); i++) {
            staticsHere.get(i).setPokemon(this, staticPokemon.get(i).pkmn);
            staticsHere.get(i).setLevel(rom, staticPokemon.get(i).level, 0);
        }

        if (romEntry.codeTweaks.get("StaticFirstBattleTweak") != null) {
            StaticEncounter startingFirstBattle = staticPokemon.get(romEntry.getValue("StaticFirstBattleOffset"));
            int startingSpeciesOffset = romEntry.getValue("StaticFirstBattleSpeciesOffset");
            writeWord(startingSpeciesOffset, pokedexToInternal[startingFirstBattle.pkmn.number]);
            int startingLevelOffset = romEntry.getValue("StaticFirstBattleLevelOffset");
            rom[startingLevelOffset] = (byte) startingFirstBattle.level;
        } else if (romEntry.codeTweaks.get("GhostMarowakTweak") != null) {
            StaticEncounter ghostMarowak = staticPokemon.get(romEntry.getValue("GhostMarowakOffset"));
            int[] ghostMarowakSpeciesOffsets = romEntry.arrayEntries.get("GhostMarowakSpeciesOffsets");
            for (int i = 0; i < ghostMarowakSpeciesOffsets.length; i++) {
                writeWord(ghostMarowakSpeciesOffsets[i], pokedexToInternal[ghostMarowak.pkmn.number]);
            }
            int[] ghostMarowakLevelOffsets = romEntry.arrayEntries.get("GhostMarowakLevelOffsets");
            for (int i = 0; i < ghostMarowakLevelOffsets.length; i++) {
                rom[ghostMarowakLevelOffsets[i]] = (byte) ghostMarowak.level;
            }

            // The code for creating Ghost Marowak tries to ensure the Pokemon is female. If the Pokemon
            // cannot be female (because they are always male or an indeterminate gender), then the game
            // will infinite loop trying and failing to make the Pokemon female. For Pokemon that cannot
            // be female, change the specified gender to something that actually works.
            int ghostMarowakGenderOffset = romEntry.getValue("GhostMarowakGenderOffset");
            if (ghostMarowak.pkmn.genderRatio == 0 || ghostMarowak.pkmn.genderRatio == 0xFF) {
                // 0x00 is 100% male, and 0xFF is indeterminate gender
                rom[ghostMarowakGenderOffset] = (byte) ghostMarowak.pkmn.genderRatio;
            }
        }

        setRoamers(staticPokemon);
        return true;
    }

    private void getRoamers(List<StaticEncounter> statics) throws IOException {
        if (romEntry.romType == Gen3Constants.RomType_Ruby) {
            int firstSpecies = readWord(rom, romEntry.roamingPokemon.get(0).speciesOffsets[0]);
            if (firstSpecies == 0) {
                // Before applying the patch, the first species offset will be pointing to
                // the lower bytes of 0x2000000, so when it reads a word, it will be 0.
                applyRubyRoamerPatch();
            }
            StaticPokemon roamer = romEntry.roamingPokemon.get(0);
            StaticEncounter se = new StaticEncounter();
            se.pkmn = roamer.getPokemon(this);
            se.level = roamer.getLevel(rom, 0);
            statics.add(se);
        } else if (romEntry.romType == Gen3Constants.RomType_Sapp) {
            StaticPokemon roamer = romEntry.roamingPokemon.get(0);
            StaticEncounter se = new StaticEncounter();
            se.pkmn = roamer.getPokemon(this);
            se.level = roamer.getLevel(rom, 0);
            statics.add(se);
        } else if (romEntry.romType == Gen3Constants.RomType_FRLG && romEntry.codeTweaks.get("RoamingPokemonTweak") != null) {
            int firstSpecies = readWord(rom, romEntry.roamingPokemon.get(0).speciesOffsets[0]);
            if (firstSpecies == 0xFFFF) {
                // This means that the IPS patch hasn't been applied yet, since the first species
                // ID location is free space.
                FileFunctions.applyPatch(rom, romEntry.codeTweaks.get("RoamingPokemonTweak"));
            }
            for (int i = 0; i < romEntry.roamingPokemon.size(); i++) {
                StaticPokemon roamer = romEntry.roamingPokemon.get(i);
                StaticEncounter se = new StaticEncounter();
                se.pkmn = roamer.getPokemon(this);
                se.level = roamer.getLevel(rom, 0);
                statics.add(se);
            }
        } else if (romEntry.romType == Gen3Constants.RomType_Em) {
            int firstSpecies = readWord(rom, romEntry.roamingPokemon.get(0).speciesOffsets[0]);
            if (firstSpecies >= pokesInternal.length) {
                // Before applying the patch, the first species offset is a pointer with a huge value.
                // Thus, this check is a good indicator that the patch needs to be applied.
                applyEmeraldRoamerPatch();
            }
            int[] southernIslandOffsets = romEntry.arrayEntries.get("StaticSouthernIslandOffsets");
            for (int i = 0; i < romEntry.roamingPokemon.size(); i++) {
                StaticPokemon roamer = romEntry.roamingPokemon.get(i);
                StaticEncounter se = new StaticEncounter();
                se.pkmn = roamer.getPokemon(this);
                se.level = roamer.getLevel(rom, 0);

                // Link each roamer to their respective Southern Island static encounter so that
                // they randomize to the same species.
                StaticEncounter southernIslandEncounter = statics.get(southernIslandOffsets[i]);
                southernIslandEncounter.linkedEncounters.add(se);
            }
        }
    }

    private void setRoamers(List<StaticEncounter> statics) {
        if (romEntry.romType == Gen3Constants.RomType_Ruby || romEntry.romType == Gen3Constants.RomType_Sapp) {
            StaticEncounter roamerEncounter = statics.get(statics.size() - 1);
            StaticPokemon roamer = romEntry.roamingPokemon.get(0);
            roamer.setPokemon(this, roamerEncounter.pkmn);
            for (int i = 0; i < roamer.levelOffsets.length; i++) {
                roamer.setLevel(rom, roamerEncounter.level, i);
            }
        } else if (romEntry.romType == Gen3Constants.RomType_FRLG && romEntry.codeTweaks.get("RoamingPokemonTweak") != null) {
            for (int i = 0; i < romEntry.roamingPokemon.size(); i++) {
                int offsetInStaticList = statics.size() - 3 + i;
                StaticEncounter roamerEncounter = statics.get(offsetInStaticList);
                StaticPokemon roamer = romEntry.roamingPokemon.get(i);
                roamer.setPokemon(this, roamerEncounter.pkmn);
                for (int j = 0; j < roamer.levelOffsets.length; j++) {
                    roamer.setLevel(rom, roamerEncounter.level, j);
                }
            }
        } else if (romEntry.romType == Gen3Constants.RomType_Em) {
            int[] southernIslandOffsets = romEntry.arrayEntries.get("StaticSouthernIslandOffsets");
            for (int i = 0; i < romEntry.roamingPokemon.size(); i++) {
                StaticEncounter southernIslandEncounter = statics.get(southernIslandOffsets[i]);
                StaticEncounter roamerEncounter = southernIslandEncounter.linkedEncounters.get(0);
                StaticPokemon roamer = romEntry.roamingPokemon.get(i);
                roamer.setPokemon(this, roamerEncounter.pkmn);
                for (int j = 0; j < roamer.levelOffsets.length; j++) {
                    roamer.setLevel(rom, roamerEncounter.level, j);
                }
            }
        }
    }

    private void applyRubyRoamerPatch() {
        int offset = romEntry.getValue("FindMapsWithMonFunctionStartOffset");

        // The constant 0x2000000 is actually in the function twice, so we'll replace the first instance
        // with Latios's ID. First, change the "ldr r2, [pc, #0x68]" near the start of the function to
        // "ldr r2, [pc, #0x15C]" so it points to the second usage of 0x2000000
        rom[offset + 22] = 0x57;

        // In the space formerly occupied by the first 0x2000000, write Latios's ID
        FileFunctions.writeFullInt(rom, offset + 128, pokedexToInternal[Species.latios]);

        // Where the original function computes Latios's ID by setting r0 to 0xCC << 1, just pc-relative
        // load our constant. We have four bytes of space to play with, and we need to make sure the offset
        // from the pc is 4-byte aligned; we need to nop for alignment and then perform the load.
        rom[offset + 12] = 0x00;
        rom[offset + 13] = 0x00;
        rom[offset + 14] = 0x1C;
        rom[offset + 15] = 0x48;

        offset = romEntry.getValue("CreateInitialRoamerMonFunctionStartOffset");

        // At the very end of the function, the game pops the lr from the stack and stores it in r0, then
        // it does "bx r0" to jump back to the caller, and then it has two bytes of padding afterwards. For
        // some reason, Ruby very rarely does "pop { pc }" even though that seemingly works fine. By doing
        // that, we only need one instruction to return to the caller, giving us four bytes to write
        // Latios's species ID.
        rom[offset + 182] = 0x00;
        rom[offset + 183] = (byte) 0xBD;
        FileFunctions.writeFullInt(rom, offset + 184, pokedexToInternal[Species.latios]);

        // Now write a pc-relative load to this new species ID constant over the original move and lsl. Similar
        // to before, we need to write a nop first for alignment, then pc-relative load into r6.
        rom[offset + 10] = 0x00;
        rom[offset + 11] = 0x00;
        rom[offset + 12] = 0x2A;
        rom[offset + 13] = 0x4E;
    }

    private void applyEmeraldRoamerPatch() {
        int offset = romEntry.getValue("CreateInitialRoamerMonFunctionStartOffset");

        // Latias's species ID is already a pc-relative loaded constant, but Latios's isn't. We need to make
        // some room for it; the constant 0x03005D8C is actually in the function twice, so we'll replace the first
        // instance with Latios's ID. First, change the "ldr r0, [pc, #0xC]" at the start of the function to
        // "ldr r0, [pc, #0x104]", so it points to the second usage of 0x03005D8C
        rom[offset + 14] = 0x41;

        // In the space formerly occupied by the first 0x03005D8C, write Latios's ID
        FileFunctions.writeFullInt(rom, offset + 28, pokedexToInternal[Species.latios]);

        // In the original function, we "lsl r0, r0, #0x10" then compare r0 to 0. The thing is, this left
        // shift doesn't actually matter, because 0 << 0x10 = 0, and [non-zero] << 0x10 = [non-zero].
        // Let's move the compare up to take its place and then load Latios's ID into r3 for use in another
        // branch later.
        rom[offset + 8] = 0x00;
        rom[offset + 9] = 0x28;
        rom[offset + 10] = 0x04;
        rom[offset + 11] = 0x4B;

        // Lastly, in the branch that normally does r2 = 0xCC << 0x1 to compute Latios's ID, just mov r3
        // into r2, since it was loaded with his ID with the above code.
        rom[offset + 48] = 0x1A;
        rom[offset + 49] = 0x46;
        rom[offset + 50] = 0x00;
        rom[offset + 51] = 0x00;
    }

    // For dynamic offsets later
    private int find(String hexString) {
        return find(rom, hexString);
    }

    private static int find(byte[] haystack, String hexString) {
        byte[] searchFor = new byte[hexString.length() / 2];
        for (int i = 0; i < searchFor.length; i++) {
            searchFor[i] = (byte) Integer.parseInt(hexString.substring(i * 2, i * 2 + 2), 16);
        }
        List<Integer> found = RomFunctions.search(haystack, searchFor);
        if (found.size() == 0) {
            return -1; // not found
        } else {
            return found.get(0);
        }
    }

    private static List<Integer> findMultiple(byte[] haystack, String hexString) {
        byte[] searchFor = new byte[hexString.length() / 2];
        for (int i = 0; i < searchFor.length; i++) {
            searchFor[i] = (byte) Integer.parseInt(hexString.substring(i * 2, i * 2 + 2), 16);
        }
        return RomFunctions.search(haystack, searchFor);
    }

    private void attemptObedienceEvolutionPatches() {
        if (havePatchedObedience) {
            return;
        }

        havePatchedObedience = true;
        // This routine *appears* to only exist in E/FR/LG...
        // Look for the deoxys part which is
        // MOVS R1, 0x19A
        // CMP R0, R1
        // BEQ <mew/deoxys case>
        // Hex is CD214900 8842 0FD0
        int deoxysObOffset = find(Gen3Constants.deoxysObeyCode);
        if (deoxysObOffset > 0) {
            // We found the deoxys check...
            // Replacing it with MOVS R1, 0x0 would work fine.
            // This would make it so species 0x0 (glitch only) would disobey.
            // But MOVS R1, 0x0 (the version I know) is 2-byte
            // So we just use it twice...
            // the equivalent of nop'ing the second time.
            rom[deoxysObOffset] = 0x00;
            rom[deoxysObOffset + 1] = Gen3Constants.gbaSetRxOpcode | Gen3Constants.gbaR1;
            rom[deoxysObOffset + 2] = 0x00;
            rom[deoxysObOffset + 3] = Gen3Constants.gbaSetRxOpcode | Gen3Constants.gbaR1;
            // Look for the mew check too... it's 0x16 ahead
            if (readWord(deoxysObOffset + Gen3Constants.mewObeyOffsetFromDeoxysObey) == (((Gen3Constants.gbaCmpRxOpcode | Gen3Constants.gbaR0) << 8) | (Species.mew))) {
                // Bingo, thats CMP R0, 0x97
                // change to CMP R0, 0x0
                writeWord(deoxysObOffset + Gen3Constants.mewObeyOffsetFromDeoxysObey,
                        (((Gen3Constants.gbaCmpRxOpcode | Gen3Constants.gbaR0) << 8) | (0)));
            }
        }

        // Look for evolutions too
        if (romEntry.romType == Gen3Constants.RomType_FRLG) {
            int evoJumpOffset = find(Gen3Constants.levelEvoKantoDexCheckCode);
            if (evoJumpOffset > 0) {
                // This currently compares species to 0x97 and then allows
                // evolution if it's <= that.
                // Allow it regardless by using an unconditional jump instead
                writeWord(evoJumpOffset, Gen3Constants.gbaNopOpcode);
                writeWord(evoJumpOffset + 2,
                        ((Gen3Constants.gbaUnconditionalJumpOpcode << 8) | (Gen3Constants.levelEvoKantoDexJumpAmount)));
            }

            int stoneJumpOffset = find(Gen3Constants.stoneEvoKantoDexCheckCode);
            if (stoneJumpOffset > 0) {
                // same as the above, but for stone evos
                writeWord(stoneJumpOffset, Gen3Constants.gbaNopOpcode);
                writeWord(stoneJumpOffset + 2,
                        ((Gen3Constants.gbaUnconditionalJumpOpcode << 8) | (Gen3Constants.stoneEvoKantoDexJumpAmount)));
            }
        }
    }


    private void populateEvolutions() {
        for (Pokemon pkmn : pokes) {
            if (pkmn != null) {
                pkmn.evolutionsFrom.clear();
                pkmn.evolutionsTo.clear();
            }
        }

        int baseOffset = romEntry.getValue("PokemonEvolutions");
        int numInternalPokes = romEntry.getValue("PokemonCount");
        for (int i = 1; i <= numRealPokemon; i++) {
            Pokemon pk = pokemonList.get(i);
            int idx = pokedexToInternal[pk.number];
            int evoOffset = baseOffset + (idx) * 0x28;
            for (int j = 0; j < 5; j++) {
                int method = readWord(evoOffset + j * 8);
                int evolvingTo = readWord(evoOffset + j * 8 + 4);
                if (method >= 1 && method <= Gen3Constants.evolutionMethodCount && evolvingTo >= 1
                        && evolvingTo <= numInternalPokes) {
                    int extraInfo = readWord(evoOffset + j * 8 + 2);
                    EvolutionType et = EvolutionType.fromIndex(3, method);
                    Evolution evo = new Evolution(pk, pokesInternal[evolvingTo], true, et, extraInfo);
                    if (!pk.evolutionsFrom.contains(evo)) {
                        pk.evolutionsFrom.add(evo);
                        pokesInternal[evolvingTo].evolutionsTo.add(evo);
                    }
                }
            }
            // Split evos shouldn't carry stats unless the evo is Nincada's
            // In that case, we should have Ninjask carry stats
            if (pk.evolutionsFrom.size() > 1) {
                for (Evolution e : pk.evolutionsFrom) {
                    if (e.type != EvolutionType.LEVEL_CREATE_EXTRA) {
                        e.carryStats = false;
                    }
                }
            }
        }
    }

    private void writeEvolutions() {
        int baseOffset = romEntry.getValue("PokemonEvolutions");
        for (int i = 1; i <= numRealPokemon; i++) {
            Pokemon pk = pokemonList.get(i);
            int idx = pokedexToInternal[pk.number];
            int evoOffset = baseOffset + (idx) * 0x28;
            int evosWritten = 0;
            for (Evolution evo : pk.evolutionsFrom) {
                writeWord(evoOffset, evo.type.toIndex(3));
                writeWord(evoOffset + 2, evo.extraInfo);
                writeWord(evoOffset + 4, pokedexToInternal[evo.to.number]);
                writeWord(evoOffset + 6, 0);
                evoOffset += 8;
                evosWritten++;
                if (evosWritten == 5) {
                    break;
                }
            }
            while (evosWritten < 5) {
                writeWord(evoOffset, 0);
                writeWord(evoOffset + 2, 0);
                writeWord(evoOffset + 4, 0);
                writeWord(evoOffset + 6, 0);
                evoOffset += 8;
                evosWritten++;
            }
        }
    }

    @Override
    public void removeImpossibleEvolutions(Settings settings) {
        attemptObedienceEvolutionPatches();

        // no move evos, so no need to check for those
        for (Pokemon pkmn : pokes) {
            if (pkmn != null) {
                for (Evolution evo : pkmn.evolutionsFrom) {
                    // Not trades, but impossible without trading
                    if (evo.type == EvolutionType.HAPPINESS_DAY && romEntry.romType == Gen3Constants.RomType_FRLG) {
                        // happiness day change to Sun Stone
                        evo.type = EvolutionType.STONE;
                        evo.extraInfo = Gen3Items.sunStone;
                        addEvoUpdateStone(impossibleEvolutionUpdates, evo);
                    }
                    if (evo.type == EvolutionType.HAPPINESS_NIGHT && romEntry.romType == Gen3Constants.RomType_FRLG) {
                        // happiness night change to Moon Stone
                        evo.type = EvolutionType.STONE;
                        evo.extraInfo = Gen3Items.moonStone;
                        addEvoUpdateStone(impossibleEvolutionUpdates, evo);
                    }
                    if (evo.type == EvolutionType.LEVEL_HIGH_BEAUTY && romEntry.romType == Gen3Constants.RomType_FRLG) {
                        // beauty change to level 35
                        evo.type = EvolutionType.LEVEL;
                        evo.extraInfo = 35;
                        addEvoUpdateLevel(impossibleEvolutionUpdates, evo);
                    }
                    // Pure Trade
                    if (evo.type == EvolutionType.TRADE) {
                        // Haunter, Machoke, Kadabra, Graveler
                        // Make it into level 37, we're done.
                        evo.type = EvolutionType.LEVEL;
                        evo.extraInfo = 37;
                        addEvoUpdateLevel(impossibleEvolutionUpdates, evo);
                    }
                    // Trade w/ Held Item
                    if (evo.type == EvolutionType.TRADE_ITEM) {
                        if (evo.from.number == Species.poliwhirl) {
                            // Poliwhirl: Lv 37
                            evo.type = EvolutionType.LEVEL;
                            evo.extraInfo = 37;
                            addEvoUpdateLevel(impossibleEvolutionUpdates, evo);
                        } else if (evo.from.number == Species.slowpoke) {
                            // Slowpoke: Water Stone
                            evo.type = EvolutionType.STONE;
                            evo.extraInfo = Gen3Items.waterStone;
                            addEvoUpdateStone(impossibleEvolutionUpdates, evo);
                        } else if (evo.from.number == Species.seadra) {
                            // Seadra: Lv 40
                            evo.type = EvolutionType.LEVEL;
                            evo.extraInfo = 40;
                            addEvoUpdateLevel(impossibleEvolutionUpdates, evo);
                        } else if (evo.from.number == Species.clamperl
                                && evo.extraInfo == Gen3Items.deepSeaTooth) {
                            // Clamperl -> Huntail: Lv30
                            evo.type = EvolutionType.LEVEL;
                            evo.extraInfo = 30;
                            addEvoUpdateLevel(impossibleEvolutionUpdates, evo);
                        } else if (evo.from.number == Species.clamperl
                                && evo.extraInfo == Gen3Items.deepSeaScale) {
                            // Clamperl -> Gorebyss: Water Stone
                            evo.type = EvolutionType.STONE;
                            evo.extraInfo = Gen3Items.waterStone;
                            addEvoUpdateStone(impossibleEvolutionUpdates, evo);
                        } else {
                            // Onix, Scyther or Porygon: Lv30
                            evo.type = EvolutionType.LEVEL;
                            evo.extraInfo = 30;
                            addEvoUpdateLevel(impossibleEvolutionUpdates, evo);
                        }
                    }
                }
            }
        }

    }

    @Override
    public void makeEvolutionsEasier(Settings settings) {
        // Reduce the amount of happiness required to evolve.
        int offset = find(rom, Gen3Constants.friendshipValueForEvoLocator);
        if (offset > 0) {
            // Amount of required happiness for HAPPINESS evolutions.
            if (rom[offset] == (byte)219) {
                rom[offset] = (byte)159;
            }
            // FRLG doesn't have code to handle time-based evolutions.
            if (romEntry.romType != Gen3Constants.RomType_FRLG) {
                // Amount of required happiness for HAPPINESS_DAY evolutions.
                if (rom[offset + 38] == (byte)219) {
                    rom[offset + 38] = (byte)159;
                }
                // Amount of required happiness for HAPPINESS_NIGHT evolutions.
                if (rom[offset + 66] == (byte)219) {
                    rom[offset + 66] = (byte)159;
                }
            }
        }
    }

    @Override
    public void removeTimeBasedEvolutions() {
        for (Pokemon pkmn : pokes) {
            if (pkmn != null) {
                for (Evolution evol : pkmn.evolutionsFrom) {
                    // In Gen 3, only Eevee has a time-based evolution.
                    if (evol.type == EvolutionType.HAPPINESS_DAY) {
                        // Eevee: Make sun stone => Espeon
                        evol.type = EvolutionType.STONE;
                        evol.extraInfo = Gen3Items.sunStone;
                        addEvoUpdateStone(timeBasedEvolutionUpdates, evol);
                    } else if (evol.type == EvolutionType.HAPPINESS_NIGHT) {
                        // Eevee: Make moon stone => Umbreon
                        evol.type = EvolutionType.STONE;
                        evol.extraInfo = Gen3Items.moonStone;
                        addEvoUpdateStone(timeBasedEvolutionUpdates, evol);
                    }
                }
            }
        }
    }

    @Override
    public List<String> getTrainerClassNames() {
        int baseOffset = romEntry.getValue("TrainerClassNames");
        int amount = romEntry.getValue("TrainerClassCount");
        int length = romEntry.getValue("TrainerClassNameLength");
        List<String> trainerClasses = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            trainerClasses.add(readVariableLengthString(baseOffset + i * length));
        }
        return trainerClasses;
    }

    @Override
    public boolean canChangeStaticPokemon() {
        return (romEntry.getValue("StaticPokemonSupport") > 0);
    }

    @Override
    public boolean hasMainGameLegendaries() {
        return romEntry.arrayEntries.get("MainGameLegendaries") != null;
    }

    @Override
    public List<Integer> getMainGameLegendaries() {
        if (this.hasMainGameLegendaries()) {
            return Arrays.stream(romEntry.arrayEntries.get("MainGameLegendaries")).boxed().collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    @Override
    public String getDefaultExtension() {
        return "gba";
    }

    @Override
    public int abilitiesPerPokemon() {
        return 2;
    }

    private void loadAbilityNames() {
        int nameoffs = romEntry.getValue("AbilityNames");
        int namelen = romEntry.getValue("AbilityNameLength");
        abilityNames = new String[Gen3Constants.highestAbilityIndex + 1];
        for (int i = 0; i <= Gen3Constants.highestAbilityIndex; i++) {
            abilityNames[i] = readFixedLengthString(nameoffs + namelen * i, namelen);
        }
    }

    @Override
    public int internalStringLength(String string) {
        return 0;
    }

    @Override
    public void randomizeIntroPokemon() {
        // FRLG
        if (romEntry.romType == Gen3Constants.RomType_FRLG) {
            // intro sprites : first 255 only due to size
            Pokemon introPk = randomPokemonLimited(255, false);
            if (introPk == null) {
                return;
            }
            int introPokemon = pokedexToInternal[introPk.number];
            int frontSprites = romEntry.getValue("FrontSprites");
            int palettes = romEntry.getValue("PokemonPalettes");

            rom[romEntry.getValue("IntroCryOffset")] = (byte) introPokemon;
            rom[romEntry.getValue("IntroOtherOffset")] = (byte) introPokemon;

            int spriteBase = romEntry.getValue("IntroSpriteOffset");
            writePointer(spriteBase, frontSprites + introPokemon * 8);
            writePointer(spriteBase + 4, palettes + introPokemon * 8);
        } else if (romEntry.romType == Gen3Constants.RomType_Ruby || romEntry.romType == Gen3Constants.RomType_Sapp) {
            // intro sprites : any pokemon in the range 0-510 except bulbasaur
            int introPokemon = pokedexToInternal[randomPokemon().number];
            while (introPokemon == 1 || introPokemon > 510) {
                introPokemon = pokedexToInternal[randomPokemon().number];
            }
            int frontSprites = romEntry.getValue("PokemonFrontSprites");
            int palettes = romEntry.getValue("PokemonNormalPalettes");
            int cryCommand = romEntry.getValue("IntroCryOffset");
            int otherCommand = romEntry.getValue("IntroOtherOffset");

            if (introPokemon > 255) {
                rom[cryCommand] = (byte) 0xFF;
                rom[cryCommand + 1] = Gen3Constants.gbaSetRxOpcode | Gen3Constants.gbaR0;

                rom[cryCommand + 2] = (byte) (introPokemon - 0xFF);
                rom[cryCommand + 3] = Gen3Constants.gbaAddRxOpcode | Gen3Constants.gbaR0;

                rom[otherCommand] = (byte) 0xFF;
                rom[otherCommand + 1] = Gen3Constants.gbaSetRxOpcode | Gen3Constants.gbaR4;

                rom[otherCommand + 2] = (byte) (introPokemon - 0xFF);
                rom[otherCommand + 3] = Gen3Constants.gbaAddRxOpcode | Gen3Constants.gbaR4;
            } else {
                rom[cryCommand] = (byte) introPokemon;
                rom[cryCommand + 1] = Gen3Constants.gbaSetRxOpcode | Gen3Constants.gbaR0;

                writeWord(cryCommand + 2, Gen3Constants.gbaNopOpcode);

                rom[otherCommand] = (byte) introPokemon;
                rom[otherCommand + 1] = Gen3Constants.gbaSetRxOpcode | Gen3Constants.gbaR4;

                writeWord(otherCommand + 2, Gen3Constants.gbaNopOpcode);
            }

            writePointer(romEntry.getValue("IntroSpriteOffset"), frontSprites + introPokemon * 8);
            writePointer(romEntry.getValue("IntroPaletteOffset"), palettes + introPokemon * 8);
        } else {
            // Emerald, intro sprite: any Pokemon.
            int introPokemon = pokedexToInternal[randomPokemon().number];
            writeWord(romEntry.getValue("IntroSpriteOffset"), introPokemon);
            writeWord(romEntry.getValue("IntroCryOffset"), introPokemon);
        }

    }

    private Pokemon randomPokemonLimited(int maxValue, boolean blockNonMales) {
        List<Pokemon> validPokemon = new ArrayList<>();
        for (Pokemon pk : this.mainPokemonList) {
            if (pokedexToInternal[pk.number] <= maxValue && (!blockNonMales || pk.genderRatio <= 0xFD)) {
                validPokemon.add(pk);
            }
        }
        return validPokemon.get(random.nextInt(validPokemon.size()));
    }

    private void determineMapBankSizes() {
        int mbpsOffset = romEntry.getValue("MapHeaders");
        List<Integer> mapBankOffsets = new ArrayList<>();

        int offset = mbpsOffset;

        // find map banks
        while (true) {
            int newMBOffset = readPointer(offset);
            if (newMBOffset < 0 || newMBOffset >= rom.length) {
                break;
            }
            mapBankOffsets.add(newMBOffset);
            offset += 4;
        }
        int bankCount = mapBankOffsets.size();
        int[] bankMapCounts = new int[bankCount];
        for (int bank = 0; bank < bankCount; bank++) {
            int baseBankOffset = mapBankOffsets.get(bank);
            int count = 0;
            offset = baseBankOffset;
            while (true) {
                boolean valid = true;
                for (int mbOffset : mapBankOffsets) {
                    if (baseBankOffset < mbOffset && offset >= mbOffset) {
                        valid = false;
                        break;
                    }
                }
                if (!valid) {
                    break;
                }
                if (baseBankOffset < mbpsOffset && offset >= mbpsOffset) {
                    break;
                }
                int newMapOffset = readPointer(offset);
                if (newMapOffset < 0 || newMapOffset >= rom.length) {
                    break;
                }
                count++;
                offset += 4;
            }
            bankMapCounts[bank] = count;
        }

        romEntry.entries.put("MapBankCount", bankCount);
        romEntry.arrayEntries.put("MapBankSizes", bankMapCounts);
    }

    private void preprocessMaps() {
        itemOffs = new ArrayList<>();
        int bankCount = romEntry.getValue("MapBankCount");
        int[] bankMapCounts = romEntry.arrayEntries.get("MapBankSizes");
        int itemBall = romEntry.getValue("ItemBallPic");
        mapNames = new String[bankCount][];
        int mbpsOffset = romEntry.getValue("MapHeaders");
        int mapLabels = romEntry.getValue("MapLabels");
        Map<Integer, String> mapLabelsM = new HashMap<>();
        for (int bank = 0; bank < bankCount; bank++) {
            int bankOffset = readPointer(mbpsOffset + bank * 4);
            mapNames[bank] = new String[bankMapCounts[bank]];
            for (int map = 0; map < bankMapCounts[bank]; map++) {
                int mhOffset = readPointer(bankOffset + map * 4);

                // map name
                int mapLabel = rom[mhOffset + 0x14] & 0xFF;
                if (mapLabelsM.containsKey(mapLabel)) {
                    mapNames[bank][map] = mapLabelsM.get(mapLabel);
                } else {
                    if (romEntry.romType == Gen3Constants.RomType_FRLG) {
                        mapNames[bank][map] = readVariableLengthString(readPointer(mapLabels
                                + (mapLabel - Gen3Constants.frlgMapLabelsStart) * 4));
                    } else {
                        mapNames[bank][map] = readVariableLengthString(readPointer(mapLabels + mapLabel * 8 + 4));
                    }
                    mapLabelsM.put(mapLabel, mapNames[bank][map]);
                }

                // events
                int eventOffset = readPointer(mhOffset + 4);
                if (eventOffset >= 0 && eventOffset < rom.length) {

                    int pCount = rom[eventOffset] & 0xFF;
                    int spCount = rom[eventOffset + 3] & 0xFF;

                    if (pCount > 0) {
                        int peopleOffset = readPointer(eventOffset + 4);
                        for (int p = 0; p < pCount; p++) {
                            int pSprite = rom[peopleOffset + p * 24 + 1];
                            if (pSprite == itemBall && readPointer(peopleOffset + p * 24 + 16) >= 0) {
                                // Get script and look inside
                                int scriptOffset = readPointer(peopleOffset + p * 24 + 16);
                                if (rom[scriptOffset] == 0x1A && rom[scriptOffset + 1] == 0x00
                                        && (rom[scriptOffset + 2] & 0xFF) == 0x80 && rom[scriptOffset + 5] == 0x1A
                                        && rom[scriptOffset + 6] == 0x01 && (rom[scriptOffset + 7] & 0xFF) == 0x80
                                        && rom[scriptOffset + 10] == 0x09
                                        && (rom[scriptOffset + 11] == 0x00 || rom[scriptOffset + 11] == 0x01)) {
                                    // item ball script
                                    itemOffs.add(scriptOffset + 3);
                                }
                            }
                        }

                    }

                    if (spCount > 0) {
                        int signpostsOffset = readPointer(eventOffset + 16);
                        for (int sp = 0; sp < spCount; sp++) {
                            int spType = rom[signpostsOffset + sp * 12 + 5];
                            if (spType >= 5 && spType <= 7) {
                                // hidden item
                                int itemHere = readWord(signpostsOffset + sp * 12 + 8);
                                if (itemHere != 0) {
                                    // itemid 0 is coins
                                    itemOffs.add(signpostsOffset + sp * 12 + 8);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void loadItemNames() {
        int nameoffs = romEntry.getValue("ItemData");
        int structlen = romEntry.getValue("ItemEntrySize");
        int maxcount = romEntry.getValue("ItemCount");
        itemNames = new String[maxcount + 1];
        for (int i = 0; i <= maxcount; i++) {
            itemNames[i] = readVariableLengthString(nameoffs + structlen * i);
        }
    }

    @Override
    public int generationOfPokemon() {
        return 3;
    }

    @Override
    public int miscTweaksAvailable() {
        int available = MiscTweak.LOWER_CASE_POKEMON_NAMES.getValue();
        available |= MiscTweak.NATIONAL_DEX_AT_START.getValue();
        available |= MiscTweak.UPDATE_TYPE_EFFECTIVENESS.getValue();
        if (romEntry.getValue("RunIndoorsTweakOffset") > 0) {
            available |= MiscTweak.RUNNING_SHOES_INDOORS.getValue();
        }
        if (romEntry.getValue("TextSpeedValuesOffset") > 0 || romEntry.codeTweaks.get("InstantTextTweak") != null) {
            available |= MiscTweak.FASTEST_TEXT.getValue();
        }
        if (romEntry.getValue("CatchingTutorialOpponentMonOffset") > 0) {
            available |= MiscTweak.RANDOMIZE_CATCHING_TUTORIAL.getValue();
        }
        if (romEntry.getValue("PCPotionOffset") != 0) {
            available |= MiscTweak.RANDOMIZE_PC_POTION.getValue();
        }
        available |= MiscTweak.BAN_LUCKY_EGG.getValue();
        available |= MiscTweak.RUN_WITHOUT_RUNNING_SHOES.getValue();
        if (romEntry.romType == Gen3Constants.RomType_FRLG) {
            available |= MiscTweak.BALANCE_STATIC_LEVELS.getValue();
        }
        return available;
    }

    @Override
    public void applyMiscTweak(MiscTweak tweak) {
        if (tweak == MiscTweak.RUNNING_SHOES_INDOORS) {
            applyRunningShoesIndoorsPatch();
        } else if (tweak == MiscTweak.FASTEST_TEXT) {
            applyFastestTextPatch();
        }
    }

    private void applyRunningShoesIndoorsPatch() {
        if (romEntry.getValue("RunIndoorsTweakOffset") != 0) {
            rom[romEntry.getValue("RunIndoorsTweakOffset")] = 0x00;
        }
    }

    private void applyFastestTextPatch() {
        if(romEntry.codeTweaks.get("InstantTextTweak") != null) {
            try {
                FileFunctions.applyPatch(rom, romEntry.codeTweaks.get("InstantTextTweak"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
