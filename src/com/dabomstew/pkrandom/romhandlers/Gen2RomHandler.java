package com.dabomstew.pkrandom.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  Gen2RomHandler.java - randomizer handler for G/S/C.                   --*/
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.dabomstew.pkrandom.*;
import com.dabomstew.pkrandom.constants.*;
import com.dabomstew.pkrandom.pokemon.*;

public class Gen2RomHandler extends AbstractGBCRomHandler {

    public static class Factory extends RomHandler.Factory {

        @Override
        public Gen2RomHandler create(Random random, PrintStream logStream) {
            return new Gen2RomHandler(random);
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

    public Gen2RomHandler(Random random) {
        super(random);
    }

    private static class RomEntry {
        private String name;
        private String romCode;
        private int version, nonJapanese;
        private String extraTableFile;
        private boolean isCrystal;
        private int crcInHeader = -1;
        private Map<String, String> codeTweaks = new HashMap<>();
        private Map<String, Integer> entries = new HashMap<>();
        private Map<String, int[]> arrayEntries = new HashMap<>();
        private Map<String, String> strings = new HashMap<>();
        private List<StaticPokemon> staticPokemon = new ArrayList<>();

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
            Scanner sc = new Scanner(FileFunctions.openConfig("gen2_offsets.ini"), "UTF-8");
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
                        if (r.length == 1) {
                        }
                        if (r[1].endsWith("\r\n")) {
                        }
                        r[1] = r[1].trim();
                        r[0] = r[0].trim();
                        if (r[0].equals("StaticPokemon{}")) {
                            current.staticPokemon.add(parseStaticPokemon(r[1], false));
                        } else if (r[0].equals("StaticPokemonGameCorner{}")) {
                            current.staticPokemon.add(parseStaticPokemon(r[1], true));
                        } else if (r[0].equals("TMText[]")) {
                        } else if (r[0].equals("Game")) {
                            current.romCode = r[1];
                        } else if (r[0].equals("Version")) {
                            current.version = parseRIInt(r[1]);
                        } else if (r[0].equals("NonJapanese")) {
                            current.nonJapanese = parseRIInt(r[1]);
                        } else if (r[0].equals("Type")) {
                            current.isCrystal = r[1].equalsIgnoreCase("Crystal");
                        } else if (r[0].equals("ExtraTableFile")) {
                            current.extraTableFile = r[1];
                        } else if (r[0].equals("CRCInHeader")) {
                        } else if (r[0].equals("CRC32")) {
                        } else if (r[0].endsWith("Tweak")) {
                            current.codeTweaks.put(r[0], r[1]);
                        } else if (r[0].equals("CopyFrom")) {
                            for (RomEntry otherEntry : roms) {
                                if (r[1].equalsIgnoreCase(otherEntry.name)) {
                                    // copy from here
                                    boolean cSP = (current.getValue("CopyStaticPokemon") == 1);
                                    boolean cTT = (current.getValue("CopyTMText") == 1);
                                    current.arrayEntries.putAll(otherEntry.arrayEntries);
                                    current.entries.putAll(otherEntry.entries);
                                    current.strings.putAll(otherEntry.strings);
                                    if (cSP) {
                                        current.staticPokemon.addAll(otherEntry.staticPokemon);
                                        current.entries.put("StaticPokemonSupport", 1);
                                    } else {
                                        current.entries.put("StaticPokemonSupport", 0);
                                        current.entries.remove("StaticPokemonOddEggOffset");
                                        current.entries.remove("StaticPokemonOddEggDataSize");
                                    }
                                    if (cTT) {
                                    }
                                    current.extraTableFile = otherEntry.extraTableFile;
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

    private static StaticPokemon parseStaticPokemon(String staticPokemonString, boolean isGameCorner) {
        StaticPokemon sp;
        if (isGameCorner) {
            sp = new StaticPokemonGameCorner();
        } else {
            sp = new StaticPokemon();
        }
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

/*    private static long parseRILong(String off) {
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
    }*/

    // This ROM's data
    private Pokemon[] pokes;
    private List<Pokemon> pokemonList;
    private RomEntry romEntry;
    private Move[] moves;
    private boolean havePatchedFleeing;
    private String[] itemNames;
    private List<Integer> itemOffs;
    private String[][] mapNames;
    private String[] landmarkNames;

    private static boolean detectRomInner(byte[] rom, int romSize) {
        // size check
        return romSize >= GBConstants.minRomSize && romSize <= GBConstants.maxRomSize && checkRomEntry(rom) != null;
    }

    @Override
    public void loadedRom() {
        romEntry = checkRomEntry(this.rom);
        clearTextTables();
        readTextTable("gameboy_jpn");
        if (romEntry.extraTableFile != null && !romEntry.extraTableFile.equalsIgnoreCase("none")) {
            readTextTable(romEntry.extraTableFile);
        }

        havePatchedFleeing = false;
        loadPokemonStats();
        pokemonList = Arrays.asList(pokes);
        loadMoves();
        loadLandmarkNames();
        preprocessMaps();
        loadItemNames();
    }

    private static RomEntry checkRomEntry(byte[] rom) {
        int version = rom[GBConstants.versionOffset] & 0xFF;
        int nonjap = rom[GBConstants.jpFlagOffset] & 0xFF;
        RomEntry r = null;
        // Now check for non-specific-CRC entries
        for (RomEntry re : roms) {
            if (romCode(rom, re.romCode) && re.version == version && re.nonJapanese == nonjap && re.crcInHeader == -1) {
                r = re;
                break;
            }
        }
        // Not found
        return r;
    }

    @Override
    public void savingRom() {
        savePokemonStats();
        saveMoves();
    }

    private void loadPokemonStats() {
        pokes = new Pokemon[Gen2Constants.pokemonCount + 1];
        // Fetch our names
        String[] pokeNames = readPokemonNames();
        int offs = romEntry.getValue("PokemonStatsOffset");
        // Get base stats
        for (int i = 1; i <= Gen2Constants.pokemonCount; i++) {
            pokes[i] = new Pokemon();
            pokes[i].number = i;
            loadBasicPokeStats(pokes[i], offs + (i - 1) * Gen2Constants.baseStatsEntrySize);
            // Name?
            pokes[i].name = pokeNames[i];
        }

        // Get evolutions
        populateEvolutions();

    }

    private void savePokemonStats() {
        // Write pokemon names
        int offs = romEntry.getValue("PokemonNamesOffset");
        int len = romEntry.getValue("PokemonNamesLength");
        for (int i = 1; i <= Gen2Constants.pokemonCount; i++) {
            int stringOffset = offs + (i - 1) * len;
            writeFixedLengthString(pokes[i].name, stringOffset, len);
        }
        // Write pokemon stats
        int offs2 = romEntry.getValue("PokemonStatsOffset");
        for (int i = 1; i <= Gen2Constants.pokemonCount; i++) {
            saveBasicPokeStats(pokes[i], offs2 + (i - 1) * Gen2Constants.baseStatsEntrySize);
        }
        // Write evolutions
        writeEvosAndMovesLearnt(null);
    }

    private String[] readMoveNames() {
        int offset = romEntry.getValue("MoveNamesOffset");
        String[] moveNames = new String[Gen2Constants.moveCount + 1];
        for (int i = 1; i <= Gen2Constants.moveCount; i++) {
            moveNames[i] = readVariableLengthString(offset);
            offset += lengthOfStringAt(offset, false) + 1;
        }
        return moveNames;
    }

    private void loadMoves() {
        moves = new Move[Gen2Constants.moveCount + 1];
        String[] moveNames = readMoveNames();
        int offs = romEntry.getValue("MoveDataOffset");
        for (int i = 1; i <= Gen2Constants.moveCount; i++) {
            moves[i] = new Move();
            moves[i].name = moveNames[i];
            moves[i].number = i;
            moves[i].internalId = i;
            moves[i].effectIndex = rom[offs + (i - 1) * 7 + 1] & 0xFF;
            moves[i].hitratio = ((rom[offs + (i - 1) * 7 + 4] & 0xFF)) / 255.0 * 100;
            moves[i].power = rom[offs + (i - 1) * 7 + 2] & 0xFF;
            moves[i].pp = rom[offs + (i - 1) * 7 + 5] & 0xFF;
            moves[i].type = Gen2Constants.typeTable[rom[offs + (i - 1) * 7 + 3]];

            if (i == Moves.tripleKick) {
                moves[i].hitCount = 2.71; // this assumes the first hit lands
            }

            // Values taken from effect_priorities.asm from the Gen 2 disassemblies.
            if (moves[i].effectIndex == Gen2Constants.priorityHitEffectIndex) {
                moves[i].priority = 2;
            } else if (moves[i].effectIndex == Gen2Constants.protectEffectIndex ||
                       moves[i].effectIndex == Gen2Constants.endureEffectIndex) {
                moves[i].priority = 3;
            } else if (moves[i].effectIndex == Gen2Constants.forceSwitchEffectIndex ||
                       moves[i].effectIndex == Gen2Constants.counterEffectIndex ||
                       moves[i].effectIndex == Gen2Constants.mirrorCoatEffectIndex) {
                moves[i].priority = 0;
            } else {
                moves[i].priority = 1;
            }

            double secondaryEffectChance = ((rom[offs + (i - 1) * 7 + 6] & 0xFF)) / 255.0 * 100;
            loadStatChangesFromEffect(moves[i], secondaryEffectChance);
            loadStatusFromEffect(moves[i], secondaryEffectChance);
            loadMiscMoveInfoFromEffect(moves[i], secondaryEffectChance);
        }
    }

    private void loadStatChangesFromEffect(Move move, double secondaryEffectChance) {
        switch (move.effectIndex) {
            case Gen2Constants.noDamageAtkPlusOneEffect:
            case Gen2Constants.damageUserAtkPlusOneEffect:
                move.statChanges[0].type = StatChangeType.ATTACK;
                move.statChanges[0].stages = 1;
                break;
            case Gen2Constants.noDamageDefPlusOneEffect:
            case Gen2Constants.damageUserDefPlusOneEffect:
            case Gen2Constants.defenseCurlEffect:
                move.statChanges[0].type = StatChangeType.DEFENSE;
                move.statChanges[0].stages = 1;
                break;
            case Gen2Constants.noDamageSpAtkPlusOneEffect:
                move.statChanges[0].type = StatChangeType.SPECIAL_ATTACK;
                move.statChanges[0].stages = 1;
                break;
            case Gen2Constants.noDamageEvasionPlusOneEffect:
                move.statChanges[0].type = StatChangeType.EVASION;
                move.statChanges[0].stages = 1;
                break;
            case Gen2Constants.noDamageAtkMinusOneEffect:
            case Gen2Constants.damageAtkMinusOneEffect:
                move.statChanges[0].type = StatChangeType.ATTACK;
                move.statChanges[0].stages = -1;
                break;
            case Gen2Constants.noDamageDefMinusOneEffect:
            case Gen2Constants.damageDefMinusOneEffect:
                move.statChanges[0].type = StatChangeType.DEFENSE;
                move.statChanges[0].stages = -1;
                break;
            case Gen2Constants.noDamageSpeMinusOneEffect:
            case Gen2Constants.damageSpeMinusOneEffect:
                move.statChanges[0].type = StatChangeType.SPEED;
                move.statChanges[0].stages = -1;
                break;
            case Gen2Constants.noDamageAccuracyMinusOneEffect:
            case Gen2Constants.damageAccuracyMinusOneEffect:
                move.statChanges[0].type = StatChangeType.ACCURACY;
                move.statChanges[0].stages = -1;
                break;
            case Gen2Constants.noDamageEvasionMinusOneEffect:
                move.statChanges[0].type = StatChangeType.EVASION;
                move.statChanges[0].stages = -1;
                break;
            case Gen2Constants.noDamageAtkPlusTwoEffect:
            case Gen2Constants.swaggerEffect:
                move.statChanges[0].type = StatChangeType.ATTACK;
                move.statChanges[0].stages = 2;
                break;
            case Gen2Constants.noDamageDefPlusTwoEffect:
                move.statChanges[0].type = StatChangeType.DEFENSE;
                move.statChanges[0].stages = 2;
                break;
            case Gen2Constants.noDamageSpePlusTwoEffect:
                move.statChanges[0].type = StatChangeType.SPEED;
                move.statChanges[0].stages = 2;
                break;
            case Gen2Constants.noDamageSpDefPlusTwoEffect:
                move.statChanges[0].type = StatChangeType.SPECIAL_DEFENSE;
                move.statChanges[0].stages = 2;
                break;
            case Gen2Constants.noDamageAtkMinusTwoEffect:
                move.statChanges[0].type = StatChangeType.ATTACK;
                move.statChanges[0].stages = -2;
                break;
            case Gen2Constants.noDamageDefMinusTwoEffect:
                move.statChanges[0].type = StatChangeType.DEFENSE;
                move.statChanges[0].stages = -2;
                break;
            case Gen2Constants.noDamageSpeMinusTwoEffect:
                move.statChanges[0].type = StatChangeType.SPEED;
                move.statChanges[0].stages = -2;
                break;
            case Gen2Constants.damageSpDefMinusOneEffect:
                move.statChanges[0].type = StatChangeType.SPECIAL_DEFENSE;
                move.statChanges[0].stages = -1;
                break;
            case Gen2Constants.damageUserAllPlusOneEffect:
                move.statChanges[0].type = StatChangeType.ALL;
                move.statChanges[0].stages = 1;
                break;
            default:
                // Move does not have a stat-changing effect
                return;
        }

        switch (move.effectIndex) {
            case Gen2Constants.noDamageAtkPlusOneEffect:
            case Gen2Constants.noDamageDefPlusOneEffect:
            case Gen2Constants.noDamageSpAtkPlusOneEffect:
            case Gen2Constants.noDamageEvasionPlusOneEffect:
            case Gen2Constants.noDamageAtkMinusOneEffect:
            case Gen2Constants.noDamageDefMinusOneEffect:
            case Gen2Constants.noDamageSpeMinusOneEffect:
            case Gen2Constants.noDamageAccuracyMinusOneEffect:
            case Gen2Constants.noDamageEvasionMinusOneEffect:
            case Gen2Constants.noDamageAtkPlusTwoEffect:
            case Gen2Constants.noDamageDefPlusTwoEffect:
            case Gen2Constants.noDamageSpePlusTwoEffect:
            case Gen2Constants.noDamageSpDefPlusTwoEffect:
            case Gen2Constants.noDamageAtkMinusTwoEffect:
            case Gen2Constants.noDamageDefMinusTwoEffect:
            case Gen2Constants.noDamageSpeMinusTwoEffect:
            case Gen2Constants.noDamageSpDefMinusTwoEffect:
            case Gen2Constants.swaggerEffect:
            case Gen2Constants.defenseCurlEffect:
                if (move.statChanges[0].stages < 0 || move.effectIndex == Gen2Constants.swaggerEffect) {
                    move.statChangeMoveType = StatChangeMoveType.NO_DAMAGE_TARGET;
                } else {
                    move.statChangeMoveType = StatChangeMoveType.NO_DAMAGE_USER;
                }
                break;

            case Gen2Constants.damageAtkMinusOneEffect:
            case Gen2Constants.damageDefMinusOneEffect:
            case Gen2Constants.damageSpeMinusOneEffect:
            case Gen2Constants.damageSpDefMinusOneEffect:
            case Gen2Constants.damageAccuracyMinusOneEffect:
                move.statChangeMoveType = StatChangeMoveType.DAMAGE_TARGET;
                break;

            case Gen2Constants.damageUserDefPlusOneEffect:
            case Gen2Constants.damageUserAtkPlusOneEffect:
            case Gen2Constants.damageUserAllPlusOneEffect:
                move.statChangeMoveType = StatChangeMoveType.DAMAGE_USER;
                break;
        }

        if (move.statChangeMoveType == StatChangeMoveType.DAMAGE_TARGET || move.statChangeMoveType == StatChangeMoveType.DAMAGE_USER) {
            for (int i = 0; i < move.statChanges.length; i++) {
                if (move.statChanges[i].type != StatChangeType.NONE) {
                    move.statChanges[i].percentChance = secondaryEffectChance;
                }
            }
        }
    }

    private void loadStatusFromEffect(Move move, double secondaryEffectChance) {
        switch (move.effectIndex) {
            case Gen2Constants.noDamageSleepEffect:
            case Gen2Constants.toxicEffect:
            case Gen2Constants.noDamageConfusionEffect:
            case Gen2Constants.noDamagePoisonEffect:
            case Gen2Constants.noDamageParalyzeEffect:
            case Gen2Constants.swaggerEffect:
                move.statusMoveType = StatusMoveType.NO_DAMAGE;
                break;

            case Gen2Constants.damagePoisonEffect:
            case Gen2Constants.damageBurnEffect:
            case Gen2Constants.damageFreezeEffect:
            case Gen2Constants.damageParalyzeEffect:
            case Gen2Constants.damageConfusionEffect:
            case Gen2Constants.twineedleEffect:
            case Gen2Constants.damageBurnAndThawUserEffect:
            case Gen2Constants.thunderEffect:
                move.statusMoveType = StatusMoveType.DAMAGE;
                break;

            default:
                // Move does not have a status effect
                return;
        }

        switch (move.effectIndex) {
            case Gen2Constants.noDamageSleepEffect:
                move.statusType = StatusType.SLEEP;
                break;
            case Gen2Constants.damagePoisonEffect:
            case Gen2Constants.noDamagePoisonEffect:
            case Gen2Constants.twineedleEffect:
                move.statusType = StatusType.POISON;
                break;
            case Gen2Constants.damageBurnEffect:
            case Gen2Constants.damageBurnAndThawUserEffect:
                move.statusType = StatusType.BURN;
                break;
            case Gen2Constants.damageFreezeEffect:
                move.statusType = StatusType.FREEZE;
                break;
            case Gen2Constants.damageParalyzeEffect:
            case Gen2Constants.noDamageParalyzeEffect:
            case Gen2Constants.thunderEffect:
                move.statusType = StatusType.PARALYZE;
                break;
            case Gen2Constants.toxicEffect:
                move.statusType = StatusType.TOXIC_POISON;
                break;
            case Gen2Constants.noDamageConfusionEffect:
            case Gen2Constants.damageConfusionEffect:
            case Gen2Constants.swaggerEffect:
                move.statusType = StatusType.CONFUSION;
                break;
        }

        if (move.statusMoveType == StatusMoveType.DAMAGE) {
            move.statusPercentChance = secondaryEffectChance;
        }
    }

    private void loadMiscMoveInfoFromEffect(Move move, double secondaryEffectChance) {
        switch (move.effectIndex) {
            case Gen2Constants.flinchEffect:
            case Gen2Constants.snoreEffect:
            case Gen2Constants.twisterEffect:
            case Gen2Constants.stompEffect:
                move.flinchPercentChance = secondaryEffectChance;
                break;

            case Gen2Constants.damageAbsorbEffect:
            case Gen2Constants.dreamEaterEffect:
                move.absorbPercent = 50;
                break;

            case Gen2Constants.damageRecoilEffect:
                move.recoilPercent = 25;
                break;

            case Gen2Constants.flailAndReversalEffect:
            case Gen2Constants.futureSightEffect:
                move.criticalChance = CriticalChance.NONE;
                break;

            case Gen2Constants.bindingEffect:
            case Gen2Constants.trappingEffect:
                move.isTrapMove = true;
                break;

            case Gen2Constants.razorWindEffect:
            case Gen2Constants.skyAttackEffect:
            case Gen2Constants.skullBashEffect:
            case Gen2Constants.solarbeamEffect:
            case Gen2Constants.semiInvulnerableEffect:
                move.isChargeMove = true;
                break;

            case Gen2Constants.hyperBeamEffect:
                move.isRechargeMove = true;
                break;
        }

        if (Gen2Constants.increasedCritMoves.contains(move.number)) {
            move.criticalChance = CriticalChance.INCREASED;
        }
    }

    private void saveMoves() {
        int offs = romEntry.getValue("MoveDataOffset");
        for (int i = 1; i <= 251; i++) {
            rom[offs + (i - 1) * 7 + 1] = (byte) moves[i].effectIndex;
            rom[offs + (i - 1) * 7 + 2] = (byte) moves[i].power;
            rom[offs + (i - 1) * 7 + 3] = Gen2Constants.typeToByte(moves[i].type);
            int hitratio = (int) Math.round(moves[i].hitratio * 2.55);
            rom[offs + (i - 1) * 7 + 4] = (byte) hitratio;
            rom[offs + (i - 1) * 7 + 5] = (byte) moves[i].pp;
        }
    }

    public List<Move> getMoves() {
        return Arrays.asList(moves);
    }

    private void loadBasicPokeStats(Pokemon pkmn, int offset) {
        pkmn.hp = rom[offset + Gen2Constants.bsHPOffset] & 0xFF;
        pkmn.attack = rom[offset + Gen2Constants.bsAttackOffset] & 0xFF;
        pkmn.defense = rom[offset + Gen2Constants.bsDefenseOffset] & 0xFF;
        pkmn.speed = rom[offset + Gen2Constants.bsSpeedOffset] & 0xFF;
        pkmn.spatk = rom[offset + Gen2Constants.bsSpAtkOffset] & 0xFF;
        pkmn.spdef = rom[offset + Gen2Constants.bsSpDefOffset] & 0xFF;
        // Type
        pkmn.primaryType = Gen2Constants.typeTable[rom[offset + Gen2Constants.bsPrimaryTypeOffset] & 0xFF];
        pkmn.secondaryType = Gen2Constants.typeTable[rom[offset + Gen2Constants.bsSecondaryTypeOffset] & 0xFF];
        // Only one type?
        if (pkmn.secondaryType == pkmn.primaryType) {
            pkmn.secondaryType = null;
        }
        pkmn.catchRate = rom[offset + Gen2Constants.bsCatchRateOffset] & 0xFF;
        pkmn.guaranteedHeldItem = -1;
        pkmn.commonHeldItem = rom[offset + Gen2Constants.bsCommonHeldItemOffset] & 0xFF;
        pkmn.rareHeldItem = rom[offset + Gen2Constants.bsRareHeldItemOffset] & 0xFF;
        pkmn.darkGrassHeldItem = -1;
        pkmn.growthCurve = ExpCurve.fromByte(rom[offset + Gen2Constants.bsGrowthCurveOffset]);
        pkmn.picDimensions = rom[offset + Gen2Constants.bsPicDimensionsOffset] & 0xFF;

    }

    private void saveBasicPokeStats(Pokemon pkmn, int offset) {
        rom[offset + Gen2Constants.bsHPOffset] = (byte) pkmn.hp;
        rom[offset + Gen2Constants.bsAttackOffset] = (byte) pkmn.attack;
        rom[offset + Gen2Constants.bsDefenseOffset] = (byte) pkmn.defense;
        rom[offset + Gen2Constants.bsSpeedOffset] = (byte) pkmn.speed;
        rom[offset + Gen2Constants.bsSpAtkOffset] = (byte) pkmn.spatk;
        rom[offset + Gen2Constants.bsSpDefOffset] = (byte) pkmn.spdef;
        rom[offset + Gen2Constants.bsPrimaryTypeOffset] = Gen2Constants.typeToByte(pkmn.primaryType);
        if (pkmn.secondaryType == null) {
            rom[offset + Gen2Constants.bsSecondaryTypeOffset] = rom[offset + Gen2Constants.bsPrimaryTypeOffset];
        } else {
            rom[offset + Gen2Constants.bsSecondaryTypeOffset] = Gen2Constants.typeToByte(pkmn.secondaryType);
        }
        rom[offset + Gen2Constants.bsCatchRateOffset] = (byte) pkmn.catchRate;

        rom[offset + Gen2Constants.bsCommonHeldItemOffset] = (byte) pkmn.commonHeldItem;
        rom[offset + Gen2Constants.bsRareHeldItemOffset] = (byte) pkmn.rareHeldItem;
        rom[offset + Gen2Constants.bsGrowthCurveOffset] = pkmn.growthCurve.toByte();
    }

    private String[] readPokemonNames() {
        int offs = romEntry.getValue("PokemonNamesOffset");
        int len = romEntry.getValue("PokemonNamesLength");
        String[] names = new String[Gen2Constants.pokemonCount + 1];
        for (int i = 1; i <= Gen2Constants.pokemonCount; i++) {
            names[i] = readFixedLengthString(offs + (i - 1) * len, len);
        }
        return names;
    }

    @Override
    public List<Pokemon> getStarters() {
        // Get the starters
        List<Pokemon> starters = new ArrayList<>();
        starters.add(pokes[rom[romEntry.arrayEntries.get("StarterOffsets1")[0]] & 0xFF]);
        starters.add(pokes[rom[romEntry.arrayEntries.get("StarterOffsets2")[0]] & 0xFF]);
        starters.add(pokes[rom[romEntry.arrayEntries.get("StarterOffsets3")[0]] & 0xFF]);
        return starters;
    }

    @Override
    public boolean setStarters(List<Pokemon> newStarters) {
        // Actually write

        for (int i = 0; i < 3; i++) {
            byte starter = (byte) newStarters.get(i).number;
            int[] offsets = romEntry.arrayEntries.get("StarterOffsets" + (i + 1));
            for (int offset : offsets) {
                rom[offset] = starter;
            }
        }

        // Attempt to replace text
        if (romEntry.getValue("CanChangeStarterText") > 0) {
            int[] starterTextOffsets = romEntry.arrayEntries.get("StarterTextOffsets");
            for (int i = 0; i < 3 && i < starterTextOffsets.length; i++) {
                writeVariableLengthString(String.format("%s?\\e", newStarters.get(i).name), starterTextOffsets[i]);
            }
        }
        return true;
    }

    @Override
    public int starterCount() {
        return 3;
    }

    @Override
    public List<EncounterSet> getEncounters(boolean useTimeOfDay) {
        int offset = romEntry.getValue("WildPokemonOffset");
        List<EncounterSet> areas = new ArrayList<>();
        offset = readLandEncounters(offset, areas); // Johto
        offset = readSeaEncounters(offset, areas); // Johto
        offset = readLandEncounters(offset, areas); // Kanto
        offset = readSeaEncounters(offset, areas); // Kanto
        offset = readLandEncounters(offset, areas); // Specials
        offset = readSeaEncounters(offset, areas); // Specials

        // Fishing Data
        offset = romEntry.getValue("FishingWildsOffset");
        int rootOffset = offset;
        for (int k = 0; k < Gen2Constants.fishingGroupCount; k++) {
            EncounterSet es = new EncounterSet();
            es.displayName = "Fishing Group " + (k + 1);
            for (int i = 0; i < Gen2Constants.pokesPerFishingGroup; i++) {
                offset++;
                int pokeNum = rom[offset++] & 0xFF;
                int level = rom[offset++] & 0xFF;
                if (pokeNum == 0) {
                    if (!useTimeOfDay) {
                        // read the encounter they put here for DAY
                        int specialOffset = rootOffset + Gen2Constants.fishingGroupEntryLength
                                * Gen2Constants.pokesPerFishingGroup * Gen2Constants.fishingGroupCount + level * 4 + 2;
                        Encounter enc = new Encounter();
                        enc.pokemon = pokes[rom[specialOffset] & 0xFF];
                        enc.level = rom[specialOffset + 1] & 0xFF;
                        es.encounters.add(enc);
                    }
                    // else will be handled by code below
                } else {
                    Encounter enc = new Encounter();
                    enc.pokemon = pokes[pokeNum];
                    enc.level = level;
                    es.encounters.add(enc);
                }
            }
            areas.add(es);
        }

        // Headbutt Data
        offset = romEntry.getValue("HeadbuttWildsOffset");
        int limit = romEntry.getValue("HeadbuttTableSize");
        for (int i = 0; i < limit; i++) {
            EncounterSet es = new EncounterSet();
            es.displayName = "Headbutt Trees Set " + (i + 1);
            while ((rom[offset] & 0xFF) != 0xFF) {
                offset++;
                int pokeNum = rom[offset++] & 0xFF;
                int level = rom[offset++] & 0xFF;
                Encounter enc = new Encounter();
                enc.pokemon = pokes[pokeNum];
                enc.level = level;
                es.encounters.add(enc);
            }
            offset++;
            areas.add(es);
        }

        // Bug Catching Contest Data
        offset = romEntry.getValue("BCCWildsOffset");
        EncounterSet bccES = new EncounterSet();
        bccES.displayName = "Bug Catching Contest";
        while ((rom[offset] & 0xFF) != 0xFF) {
            offset++;
            Encounter enc = new Encounter();
            enc.pokemon = pokes[rom[offset++] & 0xFF];
            enc.level = rom[offset++] & 0xFF;
            enc.maxLevel = rom[offset++] & 0xFF;
            bccES.encounters.add(enc);
        }
        // Unown is banned for Bug Catching Contest (5/8/2016)
        bccES.bannedPokemon.add(pokes[Species.unown]);
        areas.add(bccES);

        return areas;
    }

    private int readLandEncounters(int offset, List<EncounterSet> areas) {
        while ((rom[offset] & 0xFF) != 0xFF) {
            int mapBank = rom[offset] & 0xFF;
            int mapNumber = rom[offset + 1] & 0xFF;
            String mapName = mapNames[mapBank][mapNumber];
            {
                // Use Day only
                EncounterSet encset = new EncounterSet();
                encset.rate = rom[offset + 3] & 0xFF;
                encset.displayName = mapName + " Grass/Cave";
                for (int j = 0; j < Gen2Constants.landEncounterSlots; j++) {
                    Encounter enc = new Encounter();
                    enc.level = rom[offset + 5 + Gen2Constants.landEncounterSlots * 2 + (j * 2)] & 0xFF;
                    enc.maxLevel = 0;
                    enc.pokemon = pokes[rom[offset + 5 + Gen2Constants.landEncounterSlots * 2 + (j * 2) + 1] & 0xFF];
                    encset.encounters.add(enc);
                }
                areas.add(encset);
            }
            offset += 5 + 6 * Gen2Constants.landEncounterSlots;
        }
        return offset + 1;
    }

    private int readSeaEncounters(int offset, List<EncounterSet> areas) {
        while ((rom[offset] & 0xFF) != 0xFF) {
            int mapBank = rom[offset] & 0xFF;
            int mapNumber = rom[offset + 1] & 0xFF;
            String mapName = mapNames[mapBank][mapNumber];
            EncounterSet encset = new EncounterSet();
            encset.rate = rom[offset + 2] & 0xFF;
            encset.displayName = mapName + " Surfing";
            for (int j = 0; j < Gen2Constants.seaEncounterSlots; j++) {
                Encounter enc = new Encounter();
                enc.level = rom[offset + 3 + (j * 2)] & 0xFF;
                enc.maxLevel = 0;
                enc.pokemon = pokes[rom[offset + 3 + (j * 2) + 1] & 0xFF];
                encset.encounters.add(enc);
            }
            areas.add(encset);
            offset += 3 + Gen2Constants.seaEncounterSlots * 2;
        }
        return offset + 1;
    }

    @Override
    public void setEncounters(boolean useTimeOfDay, List<EncounterSet> encounters) {
        int offset = romEntry.getValue("WildPokemonOffset");
        Iterator<EncounterSet> areas = encounters.iterator();
        offset = writeLandEncounters(offset, areas); // Johto
        offset = writeSeaEncounters(offset, areas); // Johto
        offset = writeLandEncounters(offset, areas); // Kanto
        offset = writeSeaEncounters(offset, areas); // Kanto
        offset = writeLandEncounters(offset, areas); // Specials
        offset = writeSeaEncounters(offset, areas); // Specials

        // Fishing Data
        offset = romEntry.getValue("FishingWildsOffset");
        for (int k = 0; k < Gen2Constants.fishingGroupCount; k++) {
            EncounterSet es = areas.next();
            Iterator<Encounter> encs = es.encounters.iterator();
            for (int i = 0; i < Gen2Constants.pokesPerFishingGroup; i++) {
                offset++;
                if (rom[offset] == 0) {
                    if (!useTimeOfDay) {
                        // overwrite with a static encounter
                        Encounter enc = encs.next();
                        rom[offset++] = (byte) enc.pokemon.number;
                        rom[offset++] = (byte) enc.level;
                    }
                } else {
                    Encounter enc = encs.next();
                    rom[offset++] = (byte) enc.pokemon.number;
                    rom[offset++] = (byte) enc.level;
                }
            }
        }

        // Headbutt Data
        offset = romEntry.getValue("HeadbuttWildsOffset");
        int limit = romEntry.getValue("HeadbuttTableSize");
        for (int i = 0; i < limit; i++) {
            EncounterSet es = areas.next();
            Iterator<Encounter> encs = es.encounters.iterator();
            while ((rom[offset] & 0xFF) != 0xFF) {
                Encounter enc = encs.next();
                offset++;
                rom[offset++] = (byte) enc.pokemon.number;
                rom[offset++] = (byte) enc.level;
            }
            offset++;
        }

        // Bug Catching Contest Data
        offset = romEntry.getValue("BCCWildsOffset");
        EncounterSet bccES = areas.next();
        Iterator<Encounter> bccEncs = bccES.encounters.iterator();
        while ((rom[offset] & 0xFF) != 0xFF) {
            offset++;
            Encounter enc = bccEncs.next();
            rom[offset++] = (byte) enc.pokemon.number;
            rom[offset++] = (byte) enc.level;
            rom[offset++] = (byte) enc.maxLevel;
        }

    }

    private int writeLandEncounters(int offset, Iterator<EncounterSet> areas) {
        while ((rom[offset] & 0xFF) != 0xFF) {
            {
                // Write the set to all 3 equally
                EncounterSet encset = areas.next();
                for (int i = 0; i < 3; i++) {
                    Iterator<Encounter> encountersHere = encset.encounters.iterator();
                    for (int j = 0; j < Gen2Constants.landEncounterSlots; j++) {
                        Encounter enc = encountersHere.next();
                        rom[offset + 5 + (i * Gen2Constants.landEncounterSlots * 2) + (j * 2)] = (byte) enc.level;
                        rom[offset + 5 + (i * Gen2Constants.landEncounterSlots * 2) + (j * 2) + 1] = (byte) enc.pokemon.number;
                    }
                }
            }
            offset += 5 + 6 * Gen2Constants.landEncounterSlots;
        }
        return offset + 1;
    }

    private int writeSeaEncounters(int offset, Iterator<EncounterSet> areas) {
        while ((rom[offset] & 0xFF) != 0xFF) {
            EncounterSet encset = areas.next();
            Iterator<Encounter> encountersHere = encset.encounters.iterator();
            for (int j = 0; j < Gen2Constants.seaEncounterSlots; j++) {
                Encounter enc = encountersHere.next();
                rom[offset + 3 + (j * 2)] = (byte) enc.level;
                rom[offset + 3 + (j * 2) + 1] = (byte) enc.pokemon.number;
            }
            offset += 3 + Gen2Constants.seaEncounterSlots * 2;
        }
        return offset + 1;
    }

    @Override
    public List<Trainer> getTrainers() {
        int traineroffset = romEntry.getValue("TrainerDataTableOffset");
        int traineramount = romEntry.getValue("TrainerClassAmount");
        int[] trainerclasslimits = romEntry.arrayEntries.get("TrainerDataClassCounts");

        int[] pointers = new int[traineramount];
        for (int i = 0; i < traineramount; i++) {
            int pointer = readWord(traineroffset + i * 2);
            pointers[i] = calculateOffset(bankOf(traineroffset), pointer);
        }

        List<String> tcnames = this.getTrainerClassNames();

        List<Trainer> allTrainers = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < traineramount; i++) {
            int offs = pointers[i];
            int limit = trainerclasslimits[i];
            for (int trnum = 0; trnum < limit; trnum++) {
                index++;
                Trainer tr = new Trainer();
                tr.offset = offs;
                tr.index = index;
                tr.trainerclass = i;
                String name = readVariableLengthString(offs);
                tr.name = name;
                tr.fullDisplayName = tcnames.get(i) + " " + name;
                offs += lengthOfStringAt(offs, false) + 1;
                int dataType = rom[offs] & 0xFF;
                tr.poketype = dataType;
                offs++;
                while ((rom[offs] & 0xFF) != 0xFF) {
                    TrainerPokemon tp = new TrainerPokemon();
                    tp.level = rom[offs] & 0xFF;
                    tp.pokemon = pokes[rom[offs + 1] & 0xFF];
                    offs += 2;
                    if ((dataType & 2) == 2) {
                        tp.heldItem = rom[offs] & 0xFF;
                        offs++;
                    }
                    if ((dataType & 1) == 1) {
                        for (int move = 0; move < 4; move++) {
                            tp.moves[move] = rom[offs + move] & 0xFF;
                        }
                        offs += 4;
                    }
                    tr.pokemon.add(tp);
                }
                allTrainers.add(tr);
                offs++;
            }
        }

        Gen2Constants.universalTrainerTags(allTrainers);
        if (romEntry.isCrystal) {
            Gen2Constants.crystalTags(allTrainers);
        } else {
            Gen2Constants.goldSilverTags(allTrainers);
        }

        return allTrainers;
    }

    @Override
    public List<Integer> getMainPlaythroughTrainers() {
        return new ArrayList<>(); // Not implemented
    }

    @Override
    public void setTrainers(List<Trainer> trainerData, boolean doubleBattleMode) {
        int traineroffset = romEntry.getValue("TrainerDataTableOffset");
        int traineramount = romEntry.getValue("TrainerClassAmount");
        int[] trainerclasslimits = romEntry.arrayEntries.get("TrainerDataClassCounts");

        int[] pointers = new int[traineramount];
        for (int i = 0; i < traineramount; i++) {
            int pointer = readWord(traineroffset + i * 2);
            pointers[i] = calculateOffset(bankOf(traineroffset), pointer);
        }

        // Get current movesets in case we need to reset them for certain
        // trainer mons.
        Map<Integer, List<MoveLearnt>> movesets = this.getMovesLearnt();

        Iterator<Trainer> allTrainers = trainerData.iterator();
        for (int i = 0; i < traineramount; i++) {
            int offs = pointers[i];
            int limit = trainerclasslimits[i];
            for (int trnum = 0; trnum < limit; trnum++) {
                Trainer tr = allTrainers.next();
                // Write their name
                int trnamelen = internalStringLength(tr.name);
                writeFixedLengthString(tr.name, offs, trnamelen + 1);
                offs += trnamelen + 1;
                // Write out new trainer data
                rom[offs++] = (byte) tr.poketype;
                Iterator<TrainerPokemon> tPokes = tr.pokemon.iterator();
                for (int tpnum = 0; tpnum < tr.pokemon.size(); tpnum++) {
                    TrainerPokemon tp = tPokes.next();
                    rom[offs] = (byte) tp.level;
                    rom[offs + 1] = (byte) tp.pokemon.number;
                    offs += 2;
                    if (tr.pokemonHaveItems()) {
                        rom[offs] = (byte) tp.heldItem;
                        offs++;
                    }
                    if (tr.pokemonHaveCustomMoves()) {
                        if (tp.resetMoves) {
                            int[] pokeMoves = RomFunctions.getMovesAtLevel(tp.pokemon.number, movesets, tp.level);
                            for (int m = 0; m < 4; m++) {
                                rom[offs + m] = (byte) pokeMoves[m];
                            }
                        } else {
                            rom[offs] = (byte) tp.moves[0];
                            rom[offs + 1] = (byte) tp.moves[1];
                            rom[offs + 2] = (byte) tp.moves[2];
                            rom[offs + 3] = (byte) tp.moves[3];
                        }
                        offs += 4;
                    }
                }
                rom[offs] = (byte) 0xFF;
                offs++;
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

    @Override
    public Map<Integer, List<MoveLearnt>> getMovesLearnt() {
        Map<Integer, List<MoveLearnt>> movesets = new TreeMap<>();
        int pointersOffset = romEntry.getValue("PokemonMovesetsTableOffset");
        for (int i = 1; i <= Gen2Constants.pokemonCount; i++) {
            int pointer = readWord(pointersOffset + (i - 1) * 2);
            int realPointer = calculateOffset(bankOf(pointersOffset), pointer);
            Pokemon pkmn = pokes[i];
            // Skip over evolution data
            while (rom[realPointer] != 0) {
                if (rom[realPointer] == 5) {
                    realPointer += 4;
                } else {
                    realPointer += 3;
                }
            }
            List<MoveLearnt> ourMoves = new ArrayList<>();
            realPointer++;
            while (rom[realPointer] != 0) {
                MoveLearnt learnt = new MoveLearnt();
                learnt.level = rom[realPointer] & 0xFF;
                learnt.move = rom[realPointer + 1] & 0xFF;
                ourMoves.add(learnt);
                realPointer += 2;
            }
            movesets.put(pkmn.number, ourMoves);
        }
        return movesets;
    }

    private static class StaticPokemon {
        protected int[] speciesOffsets;
        protected int[] levelOffsets;

        public StaticPokemon() {
            this.speciesOffsets = new int[0];
            this.levelOffsets = new int[0];
        }

        public Pokemon getPokemon(Gen2RomHandler rh) {
            return rh.pokes[rh.rom[speciesOffsets[0]] & 0xFF];
        }

        public void setPokemon(Gen2RomHandler rh, Pokemon pkmn) {
            for (int offset : speciesOffsets) {
                rh.rom[offset] = (byte) pkmn.number;
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

    private static class StaticPokemonGameCorner extends StaticPokemon {
        @Override
        public void setPokemon(Gen2RomHandler rh, Pokemon pkmn) {
            // Last offset is a pointer to the name
            int offsetSize = speciesOffsets.length;
            for (int i = 0; i < offsetSize - 1; i++) {
                rh.rom[speciesOffsets[i]] = (byte) pkmn.number;
            }
            rh.writePaddedPokemonName(pkmn.name, rh.romEntry.getValue("GameCornerPokemonNameLength"),
                    speciesOffsets[offsetSize - 1]);
        }
    }

    @Override
    public List<StaticEncounter> getStaticPokemon() {
        List<StaticEncounter> statics = new ArrayList<>();
        int[] staticEggOffsets = new int[0];
        if (romEntry.arrayEntries.containsKey("StaticEggPokemonOffsets")) {
            staticEggOffsets = romEntry.arrayEntries.get("StaticEggPokemonOffsets");
        }
        if (romEntry.getValue("StaticPokemonSupport") > 0) {
            for (int i = 0; i < romEntry.staticPokemon.size(); i++) {
                int currentOffset = i;
                StaticPokemon sp = romEntry.staticPokemon.get(i);
                StaticEncounter se = new StaticEncounter();
                se.pkmn = sp.getPokemon(this);
                se.level = sp.getLevel(rom, 0);
                se.isEgg = Arrays.stream(staticEggOffsets).anyMatch(x-> x == currentOffset);
                statics.add(se);
            }
        }
        if (romEntry.getValue("StaticPokemonOddEggOffset") > 0) {
            int oeOffset = romEntry.getValue("StaticPokemonOddEggOffset");
            int oeSize = romEntry.getValue("StaticPokemonOddEggDataSize");
            for (int i = 0; i < Gen2Constants.oddEggPokemonCount; i++) {
                StaticEncounter se = new StaticEncounter();
                se.pkmn = pokes[rom[oeOffset + i * oeSize] & 0xFF];
                se.isEgg = true;
                statics.add(se);
            }
        }
        return statics;
    }

    @Override
    public boolean setStaticPokemon(List<StaticEncounter> staticPokemon) {
        if (!havePatchedFleeing) {
            patchFleeing();
        }

        Iterator<StaticEncounter> statics = staticPokemon.iterator();
        for (int i = 0; i < romEntry.staticPokemon.size(); i++) {
            StaticEncounter currentStatic = statics.next();
            StaticPokemon sp = romEntry.staticPokemon.get(i);
            sp.setPokemon(this, currentStatic.pkmn);
            sp.setLevel(rom, currentStatic.level, 0);
        }

        if (romEntry.getValue("StaticPokemonOddEggOffset") > 0) {
            int oeOffset = romEntry.getValue("StaticPokemonOddEggOffset");
            int oeSize = romEntry.getValue("StaticPokemonOddEggDataSize");
            for (int i = 0; i < Gen2Constants.oddEggPokemonCount; i++) {
                int oddEggPokemonNumber = statics.next().pkmn.number;
                rom[oeOffset + i * oeSize] = (byte) oddEggPokemonNumber;
                setMovesForOddEggPokemon(oddEggPokemonNumber, oeOffset + i * oeSize);
            }
        }

        return true;
    }

    // This method depends on movesets being randomized before static Pokemon. This is currently true,
    // but may not *always* be true, so take care.
    private void setMovesForOddEggPokemon(int oddEggPokemonNumber, int oddEggPokemonOffset) {
        // Determine the level 5 moveset, minus Dizzy Punch
        Map<Integer, List<MoveLearnt>> movesets = this.getMovesLearnt();
        List<Move> moves = this.getMoves();
        List<MoveLearnt> moveset = movesets.get(oddEggPokemonNumber);
        Queue<Integer> level5Moveset = new LinkedList<>();
        int currentMoveIndex = 0;
        while (moveset.size() > currentMoveIndex && moveset.get(currentMoveIndex).level <= 5) {
            if (level5Moveset.size() == 4) {
                level5Moveset.remove();
            }
            level5Moveset.add(moveset.get(currentMoveIndex).move);
            currentMoveIndex++;
        }

        // Now add Dizzy Punch and write the moveset and PP
        if (level5Moveset.size() == 4) {
            level5Moveset.remove();
        }
        level5Moveset.add(Moves.dizzyPunch);
        for (int i = 0; i < 4; i++) {
            int move = 0;
            int pp = 0;
            if (level5Moveset.size() > 0) {
                move = level5Moveset.remove();
                pp = moves.get(move).pp; // This assumes the ordering of moves matches the internal order
            }
            rom[oddEggPokemonOffset + 2 + i] = (byte) move;
            rom[oddEggPokemonOffset + 23 + i] = (byte) pp;
        }
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

    private void writePaddedPokemonName(String name, int length, int offset) {
        String paddedName = String.format("%-" + length + "s", name);
        byte[] rawData = translateString(paddedName);
        System.arraycopy(rawData, 0, rom, offset, length);
    }

    private static int find(byte[] haystack, String hexString) {
        byte[] searchFor = new byte[hexString.length() / 2];
        for (int i = 0; i < searchFor.length; i++) {
            searchFor[i] = (byte) Integer.parseInt(hexString.substring(i * 2, i * 2 + 2), 16);
        }
        List<Integer> found = RomFunctions.search(haystack, searchFor);
        return found.get(0);

    }

    private void populateEvolutions() {
        for (Pokemon pkmn : pokes) {
            if (pkmn != null) {
                pkmn.evolutionsFrom.clear();
                pkmn.evolutionsTo.clear();
            }
        }

        int pointersOffset = romEntry.getValue("PokemonMovesetsTableOffset");
        for (int i = 1; i <= Gen2Constants.pokemonCount; i++) {
            int pointer = readWord(pointersOffset + (i - 1) * 2);
            int realPointer = calculateOffset(bankOf(pointersOffset), pointer);
            Pokemon pkmn = pokes[i];
            while (rom[realPointer] != 0) {
                int method = rom[realPointer] & 0xFF;
                int otherPoke = rom[realPointer + 2 + (method == 5 ? 1 : 0)] & 0xFF;
                EvolutionType type = EvolutionType.fromIndex(2, method);
                int extraInfo = 0;
                if (type == EvolutionType.TRADE) {
                    int itemNeeded = rom[realPointer + 1] & 0xFF;
                    if (itemNeeded != 0xFF) {
                        type = EvolutionType.TRADE_ITEM;
                        extraInfo = itemNeeded;
                    }
                } else if (type == EvolutionType.LEVEL_ATTACK_HIGHER) {
                    int tyrogueCond = rom[realPointer + 2] & 0xFF;
                    if (tyrogueCond == 2) {
                        type = EvolutionType.LEVEL_DEFENSE_HIGHER;
                    } else if (tyrogueCond == 3) {
                        type = EvolutionType.LEVEL_ATK_DEF_SAME;
                    }
                    extraInfo = rom[realPointer + 1] & 0xFF;
                } else if (type == EvolutionType.HAPPINESS) {
                    int happCond = rom[realPointer + 1] & 0xFF;
                    if (happCond == 2) {
                        type = EvolutionType.HAPPINESS_DAY;
                    } else if (happCond == 3) {
                        type = EvolutionType.HAPPINESS_NIGHT;
                    }
                } else {
                    extraInfo = rom[realPointer + 1] & 0xFF;
                }
                Evolution evo = new Evolution(pokes[i], pokes[otherPoke], true, type, extraInfo);
                if (!pkmn.evolutionsFrom.contains(evo)) {
                    pkmn.evolutionsFrom.add(evo);
                    pokes[otherPoke].evolutionsTo.add(evo);
                }
                realPointer += (method == 5 ? 4 : 3);
            }
            // split evos don't carry stats
            if (pkmn.evolutionsFrom.size() > 1) {
                for (Evolution e : pkmn.evolutionsFrom) {
                    e.carryStats = false;
                }
            }
        }
    }

    @Override
    public void removeImpossibleEvolutions(Settings settings) {
        // no move evos, so no need to check for those
        for (Pokemon pkmn : pokes) {
            if (pkmn != null) {
                for (Evolution evol : pkmn.evolutionsFrom) {
                    if (evol.type == EvolutionType.TRADE || evol.type == EvolutionType.TRADE_ITEM) {
                        // change
                        if (evol.from.number == Species.slowpoke) {
                            // Slowpoke: Make water stone => Slowking
                            evol.type = EvolutionType.STONE;
                            evol.extraInfo = Gen2Items.waterStone;
                            addEvoUpdateStone(impossibleEvolutionUpdates, evol);
                        } else if (evol.from.number == Species.seadra) {
                            // Seadra: level 40
                            evol.type = EvolutionType.LEVEL;
                            evol.extraInfo = 40; // level
                            addEvoUpdateLevel(impossibleEvolutionUpdates, evol);
                        } else if (evol.from.number == Species.poliwhirl || evol.type == EvolutionType.TRADE) {
                            // Poliwhirl or any of the original 4 trade evos
                            // Level 37
                            evol.type = EvolutionType.LEVEL;
                            evol.extraInfo = 37; // level
                            addEvoUpdateLevel(impossibleEvolutionUpdates, evol);
                        } else {
                            // A new trade evo of a single stage Pokemon
                            // level 30
                            evol.type = EvolutionType.LEVEL;
                            evol.extraInfo = 30; // level
                            addEvoUpdateLevel(impossibleEvolutionUpdates, evol);
                        }
                    }
                }
            }
        }

    }

    @Override
    public void makeEvolutionsEasier(Settings settings) {
        // Reduce the amount of happiness required to evolve.
        int offset = find(rom, Gen2Constants.friendshipValueForEvoLocator);
        if (offset > 0) {
            // The thing we're looking at is actually one byte before what we
            // want to change; this makes it work in both G/S and Crystal.
            offset++;

            // Amount of required happiness for all happiness evolutions.
            if (rom[offset] == (byte)220) {
                rom[offset] = (byte)160;
            }
        }
    }

    @Override
    public void removeTimeBasedEvolutions() {
        for (Pokemon pkmn : pokes) {
            if (pkmn != null) {
                for (Evolution evol : pkmn.evolutionsFrom) {
                    // In Gen 2, only Eevee has a time-based evolution.
                    if (evol.type == EvolutionType.HAPPINESS_DAY) {
                        // Eevee: Make sun stone => Espeon
                        evol.type = EvolutionType.STONE;
                        evol.extraInfo = Gen2Items.sunStone;
                        addEvoUpdateStone(timeBasedEvolutionUpdates, evol);
                    } else if (evol.type == EvolutionType.HAPPINESS_NIGHT) {
                        // Eevee: Make moon stone => Umbreon
                        evol.type = EvolutionType.STONE;
                        evol.extraInfo = Gen2Items.moonStone;
                        addEvoUpdateStone(timeBasedEvolutionUpdates, evol);
                    }
                }
            }
        }

    }

    @Override
    public List<String> getTrainerClassNames() {
        int amount = romEntry.getValue("TrainerClassAmount");
        int offset = romEntry.getValue("TrainerClassNamesOffset");
        List<String> trainerClassNames = new ArrayList<>();
        for (int j = 0; j < amount; j++) {
            String name = readVariableLengthString(offset);
            offset += lengthOfStringAt(offset, false) + 1;
            trainerClassNames.add(name);
        }
        return trainerClassNames;
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
        if (romEntry.codeTweaks.get("BWXPTweak") != null) {
            available |= MiscTweak.BW_EXP_PATCH.getValue();
        }
        if (romEntry.getValue("TextDelayFunctionOffset") != 0) {
            available |= MiscTweak.FASTEST_TEXT.getValue();
        }
        if (romEntry.arrayEntries.containsKey("CatchingTutorialOffsets")) {
            available |= MiscTweak.RANDOMIZE_CATCHING_TUTORIAL.getValue();
        }
        available |= MiscTweak.BAN_LUCKY_EGG.getValue();
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
        // Intro sprite

        // Pick a pokemon
        int pokemon = this.random.nextInt(Gen2Constants.pokemonCount) + 1;
        while (pokemon == Species.unown) {
            // Unown is banned
            pokemon = this.random.nextInt(Gen2Constants.pokemonCount) + 1;
        }

        rom[romEntry.getValue("IntroSpriteOffset")] = (byte) pokemon;
        rom[romEntry.getValue("IntroCryOffset")] = (byte) pokemon;

    }

    private void loadItemNames() {
        itemNames = new String[256];
        itemNames[0] = "glitch";
        // trying to emulate pretty much what the game does here
        // normal items
        int origOffset = romEntry.getValue("ItemNamesOffset");
        int itemNameOffset = origOffset;
        for (int index = 1; index <= 0x100; index++) {
            int startOfText = itemNameOffset;
            while ((rom[itemNameOffset] & 0xFF) != GBConstants.stringTerminator) {
                itemNameOffset++;
            }
            itemNameOffset++;
            itemNames[index % 256] = readFixedLengthString(startOfText, 20);
        }
    }

    private void patchFleeing() {
        havePatchedFleeing = true;
        int offset = romEntry.getValue("FleeingDataOffset");
        rom[offset] = (byte) 0xFF;
        rom[offset + Gen2Constants.fleeingSetTwoOffset] = (byte) 0xFF;
        rom[offset + Gen2Constants.fleeingSetThreeOffset] = (byte) 0xFF;
    }

    private void loadLandmarkNames() {

        int lmOffset = romEntry.getValue("LandmarkTableOffset");
        int lmBank = bankOf(lmOffset);
        int lmCount = romEntry.getValue("LandmarkCount");

        landmarkNames = new String[lmCount];

        for (int i = 0; i < lmCount; i++) {
            int lmNameOffset = calculateOffset(lmBank, readWord(lmOffset + i * 4 + 2));
            landmarkNames[i] = readVariableLengthString(lmNameOffset).replace("\\x1F", " ");
        }

    }

    private void preprocessMaps() {
        itemOffs = new ArrayList<>();

        int mhOffset = romEntry.getValue("MapHeaders");
        int mapGroupCount = Gen2Constants.mapGroupCount;
        int mapsInLastGroup = Gen2Constants.mapsInLastGroup;
        int mhBank = bankOf(mhOffset);
        mapNames = new String[mapGroupCount + 1][100];

        int[] groupOffsets = new int[mapGroupCount];
        for (int i = 0; i < mapGroupCount; i++) {
            groupOffsets[i] = calculateOffset(mhBank, readWord(mhOffset + i * 2));
        }

        // Read maps
        for (int mg = 0; mg < mapGroupCount; mg++) {
            int offset = groupOffsets[mg];
            int maxOffset = (mg == mapGroupCount - 1) ? (mhBank + 1) * GBConstants.bankSize : groupOffsets[mg + 1];
            int map = 0;
            int maxMap = (mg == mapGroupCount - 1) ? mapsInLastGroup : Integer.MAX_VALUE;
            while (offset < maxOffset && map < maxMap) {
                processMapAt(offset, mg + 1, map + 1);
                offset += 9;
                map++;
            }
        }
    }

    private void processMapAt(int offset, int mapBank, int mapNumber) {

        // second map header
        int smhBank = rom[offset] & 0xFF;
        int smhPointer = readWord(offset + 3);
        int smhOffset = calculateOffset(smhBank, smhPointer);

        // map name
        int mapLandmark = rom[offset + 5] & 0xFF;
        mapNames[mapBank][mapNumber] = landmarkNames[mapLandmark];

        // event header
        // event header is in same bank as script header
        int ehBank = rom[smhOffset + 6] & 0xFF;
        int ehPointer = readWord(smhOffset + 9);
        int ehOffset = calculateOffset(ehBank, ehPointer);

        // skip over filler
        ehOffset += 2;

        // warps
        int warpCount = rom[ehOffset++] & 0xFF;
        // warps are skipped
        ehOffset += warpCount * 5;

        // xy triggers
        int triggerCount = rom[ehOffset++] & 0xFF;
        // xy triggers are skipped
        ehOffset += triggerCount * 8;

        // signposts
        int signpostCount = rom[ehOffset++] & 0xFF;
        // we do care about these
        for (int sp = 0; sp < signpostCount; sp++) {
            // type=7 are hidden items
            int spType = rom[ehOffset + sp * 5 + 2] & 0xFF;
            if (spType == 7) {
                // get event pointer
                int spPointer = readWord(ehOffset + sp * 5 + 3);
                int spOffset = calculateOffset(ehBank, spPointer);
                // item is at spOffset+2 (first two bytes are the flag id)
                itemOffs.add(spOffset + 2);
            }
        }
        // now skip past them
        ehOffset += signpostCount * 5;

        // visible objects/people
        int peopleCount = rom[ehOffset++] & 0xFF;
        // we also care about these
        for (int p = 0; p < peopleCount; p++) {
            // color_function & 1 = 1 if itemball
            int pColorFunction = rom[ehOffset + p * 13 + 7];
            if ((pColorFunction & 1) == 1) {
                // get event pointer
                int pPointer = readWord(ehOffset + p * 13 + 9);
                int pOffset = calculateOffset(ehBank, pPointer);
                // item is at the pOffset for non-hidden items
                itemOffs.add(pOffset);
            }
        }

    }

    @Override
    public int generationOfPokemon() {
        return 2;
    }

    private void writeEvosAndMovesLearnt(Map<Integer, List<MoveLearnt>> movesets) {
        // this assumes that the evo/attack pointers & data
        // are at the end of the bank
        // which, in every clean G/S/C rom supported, they are
        // specify null to either argument to copy old values
        int movesEvosStart = romEntry.getValue("PokemonMovesetsTableOffset");
        int movesEvosBank = bankOf(movesEvosStart);
        byte[] pointerTable = new byte[Gen2Constants.pokemonCount * 2];
        int startOfNextBank;

        startOfNextBank = ((movesEvosStart / GBConstants.bankSize) + 1) * GBConstants.bankSize;

        int dataBlockSize = startOfNextBank - (movesEvosStart + pointerTable.length);
        int dataBlockOffset = movesEvosStart + pointerTable.length;
        byte[] dataBlock = new byte[dataBlockSize];
        int offsetInData = 0;
        for (int i = 1; i <= Gen2Constants.pokemonCount; i++) {
            // determine pointer
            int oldDataOffset = calculateOffset(movesEvosBank, readWord(movesEvosStart + (i - 1) * 2));
            int offsetStart = dataBlockOffset + offsetInData;
            boolean evoWritten = false;

            for (Evolution evo : pokes[i].evolutionsFrom) {
                // write evos
                dataBlock[offsetInData++] = (byte) evo.type.toIndex(2);
                if (evo.type == EvolutionType.LEVEL || evo.type == EvolutionType.STONE
                        || evo.type == EvolutionType.TRADE_ITEM) {
                    // simple types
                    dataBlock[offsetInData++] = (byte) evo.extraInfo;
                } else if (evo.type == EvolutionType.TRADE) {
                    // non-item trade
                    dataBlock[offsetInData++] = (byte) 0xFF;
                } else if (evo.type == EvolutionType.HAPPINESS) {
                    // cond 01
                    dataBlock[offsetInData++] = 0x01;
                } else if (evo.type == EvolutionType.HAPPINESS_DAY) {
                    // cond 02
                    dataBlock[offsetInData++] = 0x02;
                } else if (evo.type == EvolutionType.HAPPINESS_NIGHT) {
                    // cond 03
                    dataBlock[offsetInData++] = 0x03;
                } else if (evo.type == EvolutionType.LEVEL_ATTACK_HIGHER) {
                    dataBlock[offsetInData++] = (byte) evo.extraInfo;
                    dataBlock[offsetInData++] = 0x01;
                } else if (evo.type == EvolutionType.LEVEL_DEFENSE_HIGHER) {
                    dataBlock[offsetInData++] = (byte) evo.extraInfo;
                    dataBlock[offsetInData++] = 0x02;
                } else if (evo.type == EvolutionType.LEVEL_ATK_DEF_SAME) {
                    dataBlock[offsetInData++] = (byte) evo.extraInfo;
                    dataBlock[offsetInData++] = 0x03;
                }
                dataBlock[offsetInData++] = (byte) evo.to.number;
                evoWritten = true;
            }

            // can we reuse a terminator?
            if (!evoWritten && offsetStart != dataBlockOffset) {
                // reuse last pokemon's move terminator for our evos
                offsetStart -= 1;
            } else {
                // write a terminator
                dataBlock[offsetInData++] = 0x00;
            }
            // write table entry now that we're sure of its location
            int pointerNow = makeGBPointer(offsetStart);
            writeWord(pointerTable, (i - 1) * 2, pointerNow);
            // moveset
            if (movesets == null) {
                // copy old
                int movesOffset = oldDataOffset;
                // move past evos
                while (rom[movesOffset] != 0x00) {
                    int method = rom[movesOffset] & 0xFF;
                    movesOffset += (method == 5) ? 4 : 3;
                }
                movesOffset++;
                // copy moves
                while (rom[movesOffset] != 0x00) {
                    dataBlock[offsetInData++] = rom[movesOffset++];
                    dataBlock[offsetInData++] = rom[movesOffset++];
                }
            }
            // terminator
            dataBlock[offsetInData++] = 0x00;
        }
        // write new data
        System.arraycopy(pointerTable, 0, rom, movesEvosStart, pointerTable.length);
        System.arraycopy(dataBlock, 0, rom, dataBlockOffset, dataBlock.length);
    }

    @Override
    public void writeCheckValueToROM(int value) {
/*        if (romEntry.getValue("CheckValueOffset") > 0) {
            int cvOffset = romEntry.getValue("CheckValueOffset");
            for (int i = 0; i < 4; i++) {
                rom[cvOffset + i] = (byte) ((value >> (3 - i) * 8) & 0xFF);
            }
        }*/
    }
}
