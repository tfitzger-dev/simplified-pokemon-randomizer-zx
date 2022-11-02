package com.dabomstew.pkrandom;

/*----------------------------------------------------------------------------*/
/*--  Settings.java - encapsulates a configuration of settings used by the  --*/
/*--                  randomizer to determine how to randomize the          --*/
/*--                  target game.                                          --*/
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.zip.CRC32;

import com.dabomstew.pkrandom.pokemon.ExpCurve;
import com.dabomstew.pkrandom.pokemon.Pokemon;
import com.dabomstew.pkrandom.romhandlers.Gen1RomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen2RomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen3RomHandler;
import com.dabomstew.pkrandom.romhandlers.RomHandler;

public class Settings {

    public static final int VERSION = 321;

    public static final int LENGTH_OF_SETTINGS_DATA = 51;

    private String romName;
    private int currentMiscTweaks;

    private boolean changeImpossibleEvolutions;
    private boolean makeEvolutionsEasier;
    private boolean removeTimeBasedEvolutions;
    private boolean raceMode;
    private boolean limitPokemon;
    private boolean banIrregularAltFormes;
    private boolean dualTypeOnly;

    public enum BaseStatisticsMod {
        UNCHANGED, SHUFFLE, RANDOM,
    }

    public enum ExpCurveMod {
        LEGENDARIES, STRONG_LEGENDARIES, ALL
    }

    private BaseStatisticsMod baseStatisticsMod = BaseStatisticsMod.UNCHANGED;
    private boolean baseStatsFollowEvolutions;
    private boolean baseStatsFollowMegaEvolutions;
    private boolean assignEvoStatsRandomly;
    private boolean updateBaseStats;
    private int updateBaseStatsToGeneration;
    private boolean standardizeEXPCurves;
    private ExpCurve selectedEXPCurve;
    private ExpCurveMod expCurveMod = ExpCurveMod.LEGENDARIES;

    public enum AbilitiesMod {
        UNCHANGED, RANDOMIZE
    }

    private AbilitiesMod abilitiesMod = AbilitiesMod.UNCHANGED;
    private boolean allowWonderGuard = true;
    private boolean abilitiesFollowEvolutions;
    private boolean abilitiesFollowMegaEvolutions;
    private boolean banTrappingAbilities;
    private boolean banNegativeAbilities;
    private boolean banBadAbilities;
    private boolean weighDuplicateAbilitiesTogether;
    private boolean ensureTwoAbilities;

    public enum StartersMod {
        UNCHANGED, CUSTOM, COMPLETELY_RANDOM, RANDOM_WITH_TWO_EVOLUTIONS
    }

    private StartersMod startersMod = StartersMod.UNCHANGED;
    private boolean allowStarterAltFormes;

    // index in the rom's list of pokemon
    // offset from the dropdown index from RandomizerGUI by 1
    private int[] customStarters = new int[3];
    private boolean randomizeStartersHeldItems;
    private boolean limitMainGameLegendaries;
    private boolean limit600;
    private boolean banBadRandomStarterHeldItems;

    public enum TypesMod {
        UNCHANGED, RANDOM_FOLLOW_EVOLUTIONS, COMPLETELY_RANDOM
    }

    private TypesMod typesMod = TypesMod.UNCHANGED;

    private boolean typesFollowMegaEvolutions;

    // Evolutions
    public enum EvolutionsMod {
        UNCHANGED, RANDOM, RANDOM_EVERY_LEVEL
    }

    private EvolutionsMod evolutionsMod = EvolutionsMod.UNCHANGED;
    private boolean evosSimilarStrength;
    private boolean evosSameTyping;
    private boolean evosMaxThreeStages;
    private boolean evosForceChange;
    private boolean evosAllowAltFormes;

    // Move data
    private boolean randomizeMovePowers;
    private boolean randomizeMoveAccuracies;
    private boolean randomizeMovePPs;
    private boolean randomizeMoveTypes;
    private boolean randomizeMoveCategory;
    private boolean updateMoves;
    private int updateMovesToGeneration;
    private boolean updateMovesLegacy;

    public enum MovesetsMod {
        UNCHANGED, RANDOM_PREFER_SAME_TYPE, COMPLETELY_RANDOM, METRONOME_ONLY
    }

    private MovesetsMod movesetsMod = MovesetsMod.UNCHANGED;
    private boolean startWithGuaranteedMoves;
    private int guaranteedMoveCount = 2;
    private boolean reorderDamagingMoves;
    private boolean movesetsForceGoodDamaging;
    private int movesetsGoodDamagingPercent = 0;
    private boolean blockBrokenMovesetMoves;
    private boolean evolutionMovesForAll;

    public enum TrainersMod {
        UNCHANGED, RANDOM, DISTRIBUTED, MAINPLAYTHROUGH, TYPE_THEMED, TYPE_THEMED_ELITE4_GYMS
    }

    private TrainersMod trainersMod = TrainersMod.UNCHANGED;
    private boolean rivalCarriesStarterThroughout;
    private boolean trainersUsePokemonOfSimilarStrength;
    private boolean trainersMatchTypingDistribution;
    private boolean trainersBlockLegendaries = true;
    private boolean trainersBlockEarlyWonderGuard = true;
    private boolean randomizeTrainerNames;
    private boolean randomizeTrainerClassNames;
    private boolean trainersForceFullyEvolved;
    private int trainersForceFullyEvolvedLevel = 30;
    private boolean trainersLevelModified;
    private int trainersLevelModifier = 0; // -50 ~ 50
    private int eliteFourUniquePokemonNumber = 0; // 0 ~ 2
    private boolean allowTrainerAlternateFormes;
    private boolean swapTrainerMegaEvos;
    private int additionalBossTrainerPokemon = 0;
    private int additionalImportantTrainerPokemon = 0;
    private int additionalRegularTrainerPokemon = 0;
    private boolean randomizeHeldItemsForBossTrainerPokemon;
    private boolean randomizeHeldItemsForImportantTrainerPokemon;
    private boolean randomizeHeldItemsForRegularTrainerPokemon;
    private boolean consumableItemsOnlyForTrainerPokemon;
    private boolean sensibleItemsOnlyForTrainerPokemon;
    private boolean highestLevelOnlyGetsItemsForTrainerPokemon;
    private boolean doubleBattleMode;
    private boolean shinyChance;
    private boolean betterTrainerMovesets;

    public enum WildPokemonMod {
        UNCHANGED, RANDOM, AREA_MAPPING, GLOBAL_MAPPING
    }

    public enum WildPokemonRestrictionMod {
        NONE, SIMILAR_STRENGTH, CATCH_EM_ALL, TYPE_THEME_AREAS
    }

    private WildPokemonMod wildPokemonMod = WildPokemonMod.UNCHANGED;
    private WildPokemonRestrictionMod wildPokemonRestrictionMod = WildPokemonRestrictionMod.NONE;
    private boolean useTimeBasedEncounters;
    private boolean blockWildLegendaries = true;
    private boolean useMinimumCatchRate;
    private int minimumCatchRateLevel = 1;
    private boolean randomizeWildPokemonHeldItems;
    private boolean banBadRandomWildPokemonHeldItems;
    private boolean balanceShakingGrass;
    private boolean wildLevelsModified;
    private int wildLevelModifier = 0;
    private boolean allowWildAltFormes;

    public enum StaticPokemonMod {
        UNCHANGED, RANDOM_MATCHING, COMPLETELY_RANDOM, SIMILAR_STRENGTH
    }

    private StaticPokemonMod staticPokemonMod = StaticPokemonMod.UNCHANGED;

    private boolean allowStaticAltFormes;
    private boolean swapStaticMegaEvos;
    private boolean staticLevelModified;
    private int staticLevelModifier = 0; // -50 ~ 50
    private boolean correctStaticMusic;

    public enum TotemPokemonMod {
        UNCHANGED, RANDOM, SIMILAR_STRENGTH
    }

    public enum AllyPokemonMod {
        UNCHANGED, RANDOM, SIMILAR_STRENGTH
    }

    public enum AuraMod {
        UNCHANGED, RANDOM, SAME_STRENGTH
    }

    private TotemPokemonMod totemPokemonMod = TotemPokemonMod.UNCHANGED;
    private AllyPokemonMod allyPokemonMod = AllyPokemonMod.UNCHANGED;
    private AuraMod auraMod = AuraMod.UNCHANGED;
    private boolean randomizeTotemHeldItems;
    private boolean totemLevelsModified;
    private int totemLevelModifier = 0;
    private boolean allowTotemAltFormes;

    public enum TMsMod {
        UNCHANGED, RANDOM
    }

    private TMsMod tmsMod = TMsMod.UNCHANGED;
    private boolean tmLevelUpMoveSanity;
    private boolean keepFieldMoveTMs;
    private boolean fullHMCompat;
    private boolean tmsForceGoodDamaging;
    private int tmsGoodDamagingPercent = 0;
    private boolean blockBrokenTMMoves;
    private boolean tmsFollowEvolutions;

    public enum TMsHMsCompatibilityMod {
        UNCHANGED, RANDOM_PREFER_TYPE, COMPLETELY_RANDOM, FULL
    }

    private TMsHMsCompatibilityMod tmsHmsCompatibilityMod = TMsHMsCompatibilityMod.UNCHANGED;

    public enum MoveTutorMovesMod {
        UNCHANGED, RANDOM
    }

    private MoveTutorMovesMod moveTutorMovesMod = MoveTutorMovesMod.UNCHANGED;
    private boolean tutorLevelUpMoveSanity;
    private boolean keepFieldMoveTutors;
    private boolean tutorsForceGoodDamaging;
    private int tutorsGoodDamagingPercent = 0;
    private boolean blockBrokenTutorMoves;
    private boolean tutorFollowEvolutions;

    public enum MoveTutorsCompatibilityMod {
        UNCHANGED, RANDOM_PREFER_TYPE, COMPLETELY_RANDOM, FULL
    }

    private MoveTutorsCompatibilityMod moveTutorsCompatibilityMod = MoveTutorsCompatibilityMod.UNCHANGED;

    public enum InGameTradesMod {
        UNCHANGED, RANDOMIZE_GIVEN, RANDOMIZE_GIVEN_AND_REQUESTED
    }

    private InGameTradesMod inGameTradesMod = InGameTradesMod.UNCHANGED;
    private boolean randomizeInGameTradesNicknames;
    private boolean randomizeInGameTradesOTs;
    private boolean randomizeInGameTradesIVs;
    private boolean randomizeInGameTradesItems;

    public enum FieldItemsMod {
        UNCHANGED, SHUFFLE, RANDOM, RANDOM_EVEN
    }

    private FieldItemsMod fieldItemsMod = FieldItemsMod.UNCHANGED;
    private boolean banBadRandomFieldItems;

    public enum ShopItemsMod {
        UNCHANGED, SHUFFLE, RANDOM
    }

    private ShopItemsMod shopItemsMod = ShopItemsMod.UNCHANGED;
    private boolean banBadRandomShopItems;
    private boolean banRegularShopItems;
    private boolean banOPShopItems;
    private boolean balanceShopPrices;
    private boolean guaranteeEvolutionItems;
    private boolean guaranteeXItems;

    public enum PickupItemsMod {
        UNCHANGED, RANDOM
    }

    private PickupItemsMod pickupItemsMod = PickupItemsMod.UNCHANGED;
    private boolean banBadRandomPickupItems;

    // to and from strings etc
    public void write(FileOutputStream out) throws IOException {
        byte[] settings = toString().getBytes("UTF-8");
        ByteBuffer buf = ByteBuffer.allocate(settings.length + 8);
        buf.putInt(VERSION);
        buf.putInt(settings.length);
        buf.put(settings);
        out.write(buf.array());
    }

    public static Settings read(FileInputStream in) throws IOException, UnsupportedOperationException {
        byte[] versionBytes = new byte[4];
        byte[] lengthBytes = new byte[4];
        int nread = in.read(versionBytes);
        if (nread < 4) {
            throw new UnsupportedOperationException("Error reading version number from settings string.");
        }
        int version = ByteBuffer.wrap(versionBytes).getInt();
        if (((version >> 24) & 0xFF) > 0 && ((version >> 24) & 0xFF) <= 172) {
            throw new UnsupportedOperationException("The settings file is too old to update. Please download v4.0.2 of the randomizer (or earlier) to update it.");
        }
        if (version > VERSION) {
            throw new UnsupportedOperationException("Cannot read settings from a newer version of the randomizer.");
        }
        nread = in.read(lengthBytes);
        if (nread < 4) {
            throw new UnsupportedOperationException("Error reading settings length from settings string.");
        }
        int length = ByteBuffer.wrap(lengthBytes).getInt();
        byte[] buffer = FileFunctions.readFullyIntoBuffer(in, length);
        String settings = new String(buffer, "UTF-8");

        Settings settingsObj = fromString(settings);
        return settingsObj;
    }

    @Override
    public String toString() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 0: general options #1 + trainer/class names
        out.write(makeByteSelected(changeImpossibleEvolutions, updateMoves, updateMovesLegacy, randomizeTrainerNames,
                randomizeTrainerClassNames, makeEvolutionsEasier, removeTimeBasedEvolutions));

        // 1: pokemon base stats & abilities
        out.write(makeByteSelected(baseStatsFollowEvolutions, baseStatisticsMod == BaseStatisticsMod.RANDOM,
                baseStatisticsMod == BaseStatisticsMod.SHUFFLE, baseStatisticsMod == BaseStatisticsMod.UNCHANGED,
                standardizeEXPCurves, updateBaseStats, baseStatsFollowMegaEvolutions, assignEvoStatsRandomly));

        // 2: pokemon types & more general options
        out.write(makeByteSelected(typesMod == TypesMod.RANDOM_FOLLOW_EVOLUTIONS,
                typesMod == TypesMod.COMPLETELY_RANDOM, typesMod == TypesMod.UNCHANGED, raceMode, false,
                limitPokemon, typesFollowMegaEvolutions, dualTypeOnly));

        // 3: v171: changed to the abilities byte
        out.write(makeByteSelected(abilitiesMod == AbilitiesMod.UNCHANGED, abilitiesMod == AbilitiesMod.RANDOMIZE,
                allowWonderGuard, abilitiesFollowEvolutions, banTrappingAbilities, banNegativeAbilities, banBadAbilities,
                abilitiesFollowMegaEvolutions));

        // 4: starter pokemon stuff
        out.write(makeByteSelected(startersMod == StartersMod.CUSTOM, startersMod == StartersMod.COMPLETELY_RANDOM,
                startersMod == StartersMod.UNCHANGED, startersMod == StartersMod.RANDOM_WITH_TWO_EVOLUTIONS,
                randomizeStartersHeldItems, banBadRandomStarterHeldItems, allowStarterAltFormes));

        // 5 - 10: dropdowns
        write2ByteInt(out, customStarters[0] - 1);
        write2ByteInt(out, customStarters[1] - 1);
        write2ByteInt(out, customStarters[2] - 1);

        // 11 movesets
        out.write(makeByteSelected(movesetsMod == MovesetsMod.COMPLETELY_RANDOM,
                movesetsMod == MovesetsMod.RANDOM_PREFER_SAME_TYPE, movesetsMod == MovesetsMod.UNCHANGED,
                movesetsMod == MovesetsMod.METRONOME_ONLY, startWithGuaranteedMoves, reorderDamagingMoves)
                | ((guaranteedMoveCount - 2) << 6));

        // 12 movesets good damaging
        out.write((movesetsForceGoodDamaging ? 0x80 : 0) | movesetsGoodDamagingPercent);

        // 13 trainer pokemon
        out.write(makeByteSelected(trainersMod == TrainersMod.UNCHANGED,
                trainersMod == TrainersMod.RANDOM,
                trainersMod == TrainersMod.DISTRIBUTED,
                trainersMod == TrainersMod.MAINPLAYTHROUGH,
                trainersMod == TrainersMod.TYPE_THEMED,
                trainersMod == TrainersMod.TYPE_THEMED_ELITE4_GYMS));
        
        // 14 trainer pokemon force evolutions
        out.write((trainersForceFullyEvolved ? 0x80 : 0) | trainersForceFullyEvolvedLevel);

        // 15 wild pokemon
        out.write(makeByteSelected(wildPokemonRestrictionMod == WildPokemonRestrictionMod.CATCH_EM_ALL,
                wildPokemonMod == WildPokemonMod.AREA_MAPPING,
                wildPokemonRestrictionMod == WildPokemonRestrictionMod.NONE,
                wildPokemonRestrictionMod == WildPokemonRestrictionMod.TYPE_THEME_AREAS,
                wildPokemonMod == WildPokemonMod.GLOBAL_MAPPING, wildPokemonMod == WildPokemonMod.RANDOM,
                wildPokemonMod == WildPokemonMod.UNCHANGED, useTimeBasedEncounters));

        // 16 wild pokemon 2
        out.write(makeByteSelected(useMinimumCatchRate, blockWildLegendaries,
                wildPokemonRestrictionMod == WildPokemonRestrictionMod.SIMILAR_STRENGTH, randomizeWildPokemonHeldItems,
                banBadRandomWildPokemonHeldItems, false, false, balanceShakingGrass));

        // 17 static pokemon
        out.write(makeByteSelected(staticPokemonMod == StaticPokemonMod.UNCHANGED,
                staticPokemonMod == StaticPokemonMod.RANDOM_MATCHING,
                staticPokemonMod == StaticPokemonMod.COMPLETELY_RANDOM,
                staticPokemonMod == StaticPokemonMod.SIMILAR_STRENGTH,
                limitMainGameLegendaries, limit600, allowStaticAltFormes, swapStaticMegaEvos));

        // 18 tm randomization
        out.write(makeByteSelected(tmsHmsCompatibilityMod == TMsHMsCompatibilityMod.COMPLETELY_RANDOM,
                tmsHmsCompatibilityMod == TMsHMsCompatibilityMod.RANDOM_PREFER_TYPE,
                tmsHmsCompatibilityMod == TMsHMsCompatibilityMod.UNCHANGED, tmsMod == TMsMod.RANDOM,
                tmsMod == TMsMod.UNCHANGED, tmLevelUpMoveSanity, keepFieldMoveTMs,
                tmsHmsCompatibilityMod == TMsHMsCompatibilityMod.FULL));

        // 19 tms part 2
        out.write(makeByteSelected(fullHMCompat, tmsFollowEvolutions, tutorFollowEvolutions));

        // 20 tms good damaging
        out.write((tmsForceGoodDamaging ? 0x80 : 0) | tmsGoodDamagingPercent);

        // 21 move tutor randomization
        out.write(makeByteSelected(moveTutorsCompatibilityMod == MoveTutorsCompatibilityMod.COMPLETELY_RANDOM,
                moveTutorsCompatibilityMod == MoveTutorsCompatibilityMod.RANDOM_PREFER_TYPE,
                moveTutorsCompatibilityMod == MoveTutorsCompatibilityMod.UNCHANGED,
                moveTutorMovesMod == MoveTutorMovesMod.RANDOM, moveTutorMovesMod == MoveTutorMovesMod.UNCHANGED,
                tutorLevelUpMoveSanity, keepFieldMoveTutors,
                moveTutorsCompatibilityMod == MoveTutorsCompatibilityMod.FULL));

        // 22 tutors good damaging
        out.write((tutorsForceGoodDamaging ? 0x80 : 0) | tutorsGoodDamagingPercent);

        // 23 in game trades
        out.write(makeByteSelected(inGameTradesMod == InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED,
                inGameTradesMod == InGameTradesMod.RANDOMIZE_GIVEN, randomizeInGameTradesItems,
                randomizeInGameTradesIVs, randomizeInGameTradesNicknames, randomizeInGameTradesOTs,
                inGameTradesMod == InGameTradesMod.UNCHANGED));

        // 24 field items
        out.write(makeByteSelected(fieldItemsMod == FieldItemsMod.RANDOM, fieldItemsMod == FieldItemsMod.SHUFFLE,
                fieldItemsMod == FieldItemsMod.UNCHANGED, banBadRandomFieldItems, fieldItemsMod == FieldItemsMod.RANDOM_EVEN));

        // 25 move randomizers
        // + static music
        out.write(makeByteSelected(randomizeMovePowers, randomizeMoveAccuracies, randomizeMovePPs, randomizeMoveTypes,
                randomizeMoveCategory, correctStaticMusic));

        // 26 evolutions
        out.write(makeByteSelected(evolutionsMod == EvolutionsMod.UNCHANGED, evolutionsMod == EvolutionsMod.RANDOM,
                evosSimilarStrength, evosSameTyping, evosMaxThreeStages, evosForceChange, evosAllowAltFormes,
                evolutionsMod == EvolutionsMod.RANDOM_EVERY_LEVEL));
        
        // 27 pokemon trainer misc
        out.write(makeByteSelected(trainersUsePokemonOfSimilarStrength, 
                rivalCarriesStarterThroughout,
                trainersMatchTypingDistribution,
                trainersBlockLegendaries,
                trainersBlockEarlyWonderGuard,
                swapTrainerMegaEvos,
                shinyChance,
                betterTrainerMovesets));

        // 28 - 31: pokemon restrictions
        try {
            {
                writeFullInt(out, 0);
            }
        } catch (IOException e) {
            e.printStackTrace(); // better than nothing
        }

        // 32 - 35: misc tweaks
        try {
            writeFullInt(out, currentMiscTweaks);
        } catch (IOException e) {
            e.printStackTrace(); // better than nothing
        }

        // 36 trainer pokemon level modifier
        out.write((trainersLevelModified ? 0x80 : 0) | (trainersLevelModifier+50));

        // 37 shop items
        out.write(makeByteSelected(shopItemsMod == ShopItemsMod.RANDOM, shopItemsMod == ShopItemsMod.SHUFFLE,
                shopItemsMod == ShopItemsMod.UNCHANGED, banBadRandomShopItems, banRegularShopItems, banOPShopItems,
                balanceShopPrices, guaranteeEvolutionItems));

        // 38 wild level modifier
        out.write((wildLevelsModified ? 0x80 : 0) | (wildLevelModifier+50));

        // 39 EXP curve mod, block broken moves, alt forme stuff
        out.write(makeByteSelected(
                expCurveMod == ExpCurveMod.LEGENDARIES,
                expCurveMod == ExpCurveMod.STRONG_LEGENDARIES,
                expCurveMod == ExpCurveMod.ALL,
                blockBrokenMovesetMoves,
                blockBrokenTMMoves,
                blockBrokenTutorMoves,
                allowTrainerAlternateFormes,
                allowWildAltFormes));

        // 40 Double Battle Mode, Additional Boss/Important Trainer Pokemon, Weigh Duplicate Abilities
        out.write((doubleBattleMode ? 0x1 : 0) |
                (additionalBossTrainerPokemon << 1) |
                (additionalImportantTrainerPokemon << 4) |
                (weighDuplicateAbilitiesTogether ? 0x80 : 0));

        // 41 Additional Regular Trainer Pokemon, Aura modification, evolution moves, guarantee X items
        out.write(additionalRegularTrainerPokemon |
                ((auraMod == AuraMod.UNCHANGED) ? 0x8 : 0) |
                ((auraMod == AuraMod.RANDOM) ? 0x10 : 0) |
                ((auraMod == AuraMod.SAME_STRENGTH) ? 0x20 : 0) |
                (evolutionMovesForAll ? 0x40 : 0) |
                (guaranteeXItems ? 0x80 : 0));

        // 42 Totem Pokemon settings
        out.write(makeByteSelected(
                totemPokemonMod == TotemPokemonMod.UNCHANGED,
                totemPokemonMod == TotemPokemonMod.RANDOM,
                totemPokemonMod == TotemPokemonMod.SIMILAR_STRENGTH,
                allyPokemonMod == AllyPokemonMod.UNCHANGED,
                allyPokemonMod == AllyPokemonMod.RANDOM,
                allyPokemonMod == AllyPokemonMod.SIMILAR_STRENGTH,
                randomizeTotemHeldItems,
                allowTotemAltFormes));

        // 43 Totem level modifier
        out.write((totemLevelsModified ? 0x80 : 0) | (totemLevelModifier+50));

        // 44 - 45: These two get a byte each for future proofing
        out.write(updateBaseStatsToGeneration);
        out.write(updateMovesToGeneration);

        // 46 Selected EXP curve
        out.write(selectedEXPCurve.toByte());

        // 47 Static level modifier
        out.write((staticLevelModified ? 0x80 : 0) | (staticLevelModifier+50));

        // 48 trainer pokemon held items / pokemon ensure two abilities
        out.write(makeByteSelected(randomizeHeldItemsForBossTrainerPokemon,
                randomizeHeldItemsForImportantTrainerPokemon,
                randomizeHeldItemsForRegularTrainerPokemon,
                consumableItemsOnlyForTrainerPokemon,
                sensibleItemsOnlyForTrainerPokemon,
                highestLevelOnlyGetsItemsForTrainerPokemon,
                ensureTwoAbilities));

        // 49 pickup item randomization
        out.write(makeByteSelected(pickupItemsMod == PickupItemsMod.RANDOM,
                pickupItemsMod == PickupItemsMod.UNCHANGED, banBadRandomPickupItems,
                banIrregularAltFormes));

        // 50 elite four unique pokemon (3 bits) + catch rate level (3 bits)
        out.write(eliteFourUniquePokemonNumber | ((minimumCatchRateLevel - 1) << 3));

        try {
            byte[] romName = this.romName.getBytes("US-ASCII");
            out.write(romName.length);
            out.write(romName);
        } catch (IOException e) {
            out.write(0);
        }

        byte[] current = out.toByteArray();
        CRC32 checksum = new CRC32();
        checksum.update(current);

        try {
            writeFullInt(out, (int) checksum.getValue());
            writeFullInt(out, FileFunctions.getFileChecksum(SysConstants.customNamesFile));
        } catch (IOException e) {
            e.printStackTrace(); // better than nothing
        }

        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    public static Settings fromString(String settingsString) throws UnsupportedEncodingException, IllegalArgumentException {
        byte[] data = Base64.getDecoder().decode(settingsString);
        checkChecksum(data);

        Settings settings = new Settings();

        // Restore the actual controls
        settings.setChangeImpossibleEvolutions(restoreState(data[0], 0));
        settings.setUpdateMoves(restoreState(data[0], 1));
        settings.setUpdateMovesLegacy(restoreState(data[0], 2));
        settings.setRandomizeTrainerNames(restoreState(data[0], 3));
        settings.setRandomizeTrainerClassNames(restoreState(data[0], 4));
        settings.setMakeEvolutionsEasier(restoreState(data[0], 5));
        settings.setRemoveTimeBasedEvolutions(restoreState(data[0], 6));

        settings.setBaseStatisticsMod(restoreEnum(BaseStatisticsMod.class, data[1], 3, // UNCHANGED
                2, // SHUFFLE
                1 // RANDOM
        ));
        settings.setStandardizeEXPCurves(restoreState(data[1], 4));
        settings.setBaseStatsFollowEvolutions(restoreState(data[1], 0));
        settings.setUpdateBaseStats(restoreState(data[1], 5));
        settings.setBaseStatsFollowMegaEvolutions(restoreState(data[1],6));
        settings.setAssignEvoStatsRandomly(restoreState(data[1],7));

        settings.setTypesMod(restoreEnum(TypesMod.class, data[2], 2, // UNCHANGED
                0, // RANDOM_FOLLOW_EVOLUTIONS
                1 // COMPLETELY_RANDOM
        ));
        settings.setRaceMode(restoreState(data[2], 3));
        settings.setBlockBrokenMoves(restoreState(data[2], 4));
        settings.setLimitPokemon(restoreState(data[2], 5));
        settings.setTypesFollowMegaEvolutions(restoreState(data[2],6));
        settings.setDualTypeOnly(restoreState(data[2], 7));
        settings.setAbilitiesMod(restoreEnum(AbilitiesMod.class, data[3], 0, // UNCHANGED
                1 // RANDOMIZE
        ));
        settings.setAllowWonderGuard(restoreState(data[3], 2));
        settings.setAbilitiesFollowEvolutions(restoreState(data[3], 3));
        settings.setBanTrappingAbilities(restoreState(data[3], 4));
        settings.setBanNegativeAbilities(restoreState(data[3], 5));
        settings.setBanBadAbilities(restoreState(data[3], 6));
        settings.setAbilitiesFollowMegaEvolutions(restoreState(data[3],7));

        settings.setStartersMod(restoreEnum(StartersMod.class, data[4], 2, // UNCHANGED
                0, // CUSTOM
                1, // COMPLETELY_RANDOM
                3 // RANDOM_WITH_TWO_EVOLUTIONS
        ));
        settings.setRandomizeStartersHeldItems(restoreState(data[4], 4));
        settings.setBanBadRandomStarterHeldItems(restoreState(data[4], 5));
        settings.setAllowStarterAltFormes(restoreState(data[4],6));

        settings.setCustomStarters(new int[] { FileFunctions.read2ByteInt(data, 5) + 1,
                FileFunctions.read2ByteInt(data, 7) + 1, FileFunctions.read2ByteInt(data, 9) + 1 });

        settings.setMovesetsMod(restoreEnum(MovesetsMod.class, data[11], 2, // UNCHANGED
                1, // RANDOM_PREFER_SAME_TYPE
                0, // COMPLETELY_RANDOM
                3 // METRONOME_ONLY
        ));
        settings.setStartWithGuaranteedMoves(restoreState(data[11], 4));
        settings.setReorderDamagingMoves(restoreState(data[11], 5));
        settings.setGuaranteedMoveCount(((data[11] & 0xC0) >> 6) + 2);

        settings.setMovesetsForceGoodDamaging(restoreState(data[12], 7));
        settings.setMovesetsGoodDamagingPercent(data[12] & 0x7F);

        // changed 160
        settings.setTrainersMod(restoreEnum(TrainersMod.class, data[13], 0, // UNCHANGED
                1, // RANDOM
                2, // DISTRIBUTED
                3, // MAINPLAYTHROUGH 
                4, // TYPE_THEMED
                5 // TYPE_THEMED_ELITE4_GYMS
        ));

        settings.setTrainersForceFullyEvolved(restoreState(data[14], 7));
        settings.setTrainersForceFullyEvolvedLevel(data[14] & 0x7F);

        settings.setWildPokemonMod(restoreEnum(WildPokemonMod.class, data[15], 6, // UNCHANGED
                5, // RANDOM
                1, // AREA_MAPPING
                4 // GLOBAL_MAPPING
        ));
        settings.setWildPokemonRestrictionMod(getEnum(WildPokemonRestrictionMod.class, restoreState(data[15], 2), // NONE
                restoreState(data[16], 2), // SIMILAR_STRENGTH
                restoreState(data[15], 0), // CATCH_EM_ALL
                restoreState(data[15], 3) // TYPE_THEME_AREAS
        ));
        settings.setUseTimeBasedEncounters(restoreState(data[15], 7));

        settings.setUseMinimumCatchRate(restoreState(data[16], 0));
        settings.setBlockWildLegendaries(restoreState(data[16], 1));
        settings.setRandomizeWildPokemonHeldItems(restoreState(data[16], 3));
        settings.setBanBadRandomWildPokemonHeldItems(restoreState(data[16], 4));
        settings.setBalanceShakingGrass(restoreState(data[16], 7));

        settings.setStaticPokemonMod(restoreEnum(StaticPokemonMod.class, data[17], 0, // UNCHANGED
                1, // RANDOM_MATCHING
                2, // COMPLETELY_RANDOM
                3  // SIMILAR_STRENGTH 
        ));
        
        settings.setLimitMainGameLegendaries(restoreState(data[17], 4));
        settings.setLimit600(restoreState(data[17], 5));
        settings.setAllowStaticAltFormes(restoreState(data[17], 6));
        settings.setSwapStaticMegaEvos(restoreState(data[17], 7));
        
        settings.setTmsMod(restoreEnum(TMsMod.class, data[18], 4, // UNCHANGED
                3 // RANDOM
        ));
        settings.setTmsHmsCompatibilityMod(restoreEnum(TMsHMsCompatibilityMod.class, data[18], 2, // UNCHANGED
                1, // RANDOM_PREFER_TYPE
                0, // COMPLETELY_RANDOM
                7 // FULL
        )); 
        settings.setTmLevelUpMoveSanity(restoreState(data[18], 5));
        settings.setKeepFieldMoveTMs(restoreState(data[18], 6));

        settings.setFullHMCompat(restoreState(data[19], 0));
        settings.setTmsFollowEvolutions(restoreState(data[19], 1));
        settings.setTutorFollowEvolutions(restoreState(data[19], 2));

        settings.setTmsForceGoodDamaging(restoreState(data[20], 7));
        settings.setTmsGoodDamagingPercent(data[20] & 0x7F);

        settings.setMoveTutorMovesMod(restoreEnum(MoveTutorMovesMod.class, data[21], 4, // UNCHANGED
                3 // RANDOM
        ));
        settings.setMoveTutorsCompatibilityMod(restoreEnum(MoveTutorsCompatibilityMod.class, data[21], 2, // UNCHANGED
                1, // RANDOM_PREFER_TYPE
                0, // COMPLETELY_RANDOM
                7 // FULL
        ));
        settings.setTutorLevelUpMoveSanity(restoreState(data[21], 5));
        settings.setKeepFieldMoveTutors(restoreState(data[21], 6));

        settings.setTutorsForceGoodDamaging(restoreState(data[22], 7));
        settings.setTutorsGoodDamagingPercent(data[22] & 0x7F);

        // new 150
        settings.setInGameTradesMod(restoreEnum(InGameTradesMod.class, data[23], 6, // UNCHANGED
                1, // RANDOMIZE_GIVEN
                0 // RANDOMIZE_GIVEN_AND_REQUESTED
        ));
        settings.setRandomizeInGameTradesItems(restoreState(data[23], 2));
        settings.setRandomizeInGameTradesIVs(restoreState(data[23], 3));
        settings.setRandomizeInGameTradesNicknames(restoreState(data[23], 4));
        settings.setRandomizeInGameTradesOTs(restoreState(data[23], 5));

        settings.setFieldItemsMod(restoreEnum(FieldItemsMod.class, data[24],  
                2,  // UNCHANGED
                1,  // SHUFFLE
                0,  // RANDOM
                4   // RANDOM_EVEN
        ));
        settings.setBanBadRandomFieldItems(restoreState(data[24], 3));

        // new 170
        settings.setRandomizeMovePowers(restoreState(data[25], 0));
        settings.setRandomizeMoveAccuracies(restoreState(data[25], 1));
        settings.setRandomizeMovePPs(restoreState(data[25], 2));
        settings.setRandomizeMoveTypes(restoreState(data[25], 3));
        settings.setRandomizeMoveCategory(restoreState(data[25], 4));
        settings.setCorrectStaticMusic(restoreState(data[25], 5));

        settings.setEvolutionsMod(restoreEnum(EvolutionsMod.class, data[26], 0, // UNCHANGED
                1, // RANDOM
                7 // RANDOM_EVERY_LEVEL
        ));
        settings.setEvosSimilarStrength(restoreState(data[26], 2));
        settings.setEvosSameTyping(restoreState(data[26], 3));
        settings.setEvosMaxThreeStages(restoreState(data[26], 4));
        settings.setEvosForceChange(restoreState(data[26], 5));
        settings.setEvosAllowAltFormes(restoreState(data[26],6));

        // new pokemon trainer misc
        settings.setTrainersUsePokemonOfSimilarStrength(restoreState(data[27], 0));
        settings.setRivalCarriesStarterThroughout(restoreState(data[27], 1));
        settings.setTrainersMatchTypingDistribution(restoreState(data[27], 2));
        settings.setTrainersBlockLegendaries(restoreState(data[27], 3));
        settings.setTrainersBlockEarlyWonderGuard(restoreState(data[27], 4));
        settings.setSwapTrainerMegaEvos(restoreState(data[27], 5));
        settings.setShinyChance(restoreState(data[27], 6));
        settings.setBetterTrainerMovesets(restoreState(data[27], 7));

        int codeTweaks = FileFunctions.readFullIntBigEndian(data, 32);

        settings.setCurrentMiscTweaks(codeTweaks);

        settings.setTrainersLevelModified(restoreState(data[36], 7));
        settings.setTrainersLevelModifier((data[36] & 0x7F) - 50);
        //settings.setTrainersLevelModifier((data[36] & 0x7F));
        settings.setShopItemsMod(restoreEnum(ShopItemsMod.class,data[37],
                2,
                1,
                0));
        settings.setBanBadRandomShopItems(restoreState(data[37],3));
        settings.setBanRegularShopItems(restoreState(data[37],4));
        settings.setBanOPShopItems(restoreState(data[37],5));
        settings.setBalanceShopPrices(restoreState(data[37],6));
        settings.setGuaranteeEvolutionItems(restoreState(data[37],7));

        settings.setWildLevelsModified(restoreState(data[38],7));
        settings.setWildLevelModifier((data[38] & 0x7F) - 50);

        settings.setExpCurveMod(restoreEnum(ExpCurveMod.class,data[39],0,1,2));

        settings.setBlockBrokenMovesetMoves(restoreState(data[39],3));
        settings.setBlockBrokenTMMoves(restoreState(data[39],4));
        settings.setBlockBrokenTutorMoves(restoreState(data[39],5));

        settings.setAllowTrainerAlternateFormes(restoreState(data[39],6));
        settings.setAllowWildAltFormes(restoreState(data[39],7));

        settings.setDoubleBattleMode(restoreState(data[40], 0));
        settings.setAdditionalBossTrainerPokemon((data[40] & 0xE) >> 1);
        settings.setAdditionalImportantTrainerPokemon((data[40] & 0x70) >> 4);
        settings.setWeighDuplicateAbilitiesTogether(restoreState(data[40], 7));

        settings.setAdditionalRegularTrainerPokemon((data[41] & 0x7));
        settings.setAuraMod(restoreEnum(AuraMod.class,data[41],3,4,5));
        settings.setEvolutionMovesForAll(restoreState(data[41],6));
        settings.setGuaranteeXItems(restoreState(data[41],7));

        settings.setTotemPokemonMod(restoreEnum(TotemPokemonMod.class,data[42],0,1,2));
        settings.setAllyPokemonMod(restoreEnum(AllyPokemonMod.class,data[42],3,4,5));
        settings.setRandomizeTotemHeldItems(restoreState(data[42],6));
        settings.setAllowTotemAltFormes(restoreState(data[42],7));
        settings.setTotemLevelsModified(restoreState(data[43],7));
        settings.setTotemLevelModifier((data[43] & 0x7F) - 50);

        settings.setUpdateBaseStatsToGeneration(data[44]);

        settings.setUpdateMovesToGeneration(data[45]);

        settings.setSelectedEXPCurve(ExpCurve.fromByte(data[46]));

        settings.setStaticLevelModified(restoreState(data[47],7));
        settings.setStaticLevelModifier((data[47] & 0x7F) - 50);

        settings.setRandomizeHeldItemsForBossTrainerPokemon(restoreState(data[48], 0));
        settings.setRandomizeHeldItemsForImportantTrainerPokemon(restoreState(data[48], 1));
        settings.setRandomizeHeldItemsForRegularTrainerPokemon(restoreState(data[48], 2));
        settings.setConsumableItemsOnlyForTrainers(restoreState(data[48], 3));
        settings.setSensibleItemsOnlyForTrainers(restoreState(data[48], 4));
        settings.setHighestLevelGetsItemsForTrainers(restoreState(data[48], 5));
        settings.setEnsureTwoAbilities(restoreState(data[48], 6));

        settings.setPickupItemsMod(restoreEnum(PickupItemsMod.class, data[49],
                1, // UNCHANGED
                0));       // RANDOMIZE
        settings.setBanBadRandomPickupItems(restoreState(data[49], 2));
        settings.setBanIrregularAltFormes(restoreState(data[49], 3));

        settings.setEliteFourUniquePokemonNumber(data[50] & 0x7);
        settings.setMinimumCatchRateLevel(((data[50] & 0x38) >> 3) + 1);

        int romNameLength = data[LENGTH_OF_SETTINGS_DATA] & 0xFF;
        String romName = new String(data, LENGTH_OF_SETTINGS_DATA + 1, romNameLength, "US-ASCII");
        settings.setRomName(romName);

        return settings;
    }

    public void setRomName(String romName) {
        this.romName = romName;
    }


    public int getCurrentMiscTweaks() {
        return currentMiscTweaks;
    }

    public void setCurrentMiscTweaks(int currentMiscTweaks) {
        this.currentMiscTweaks = currentMiscTweaks;
    }

    public void setUpdateMoves(boolean updateMoves) {
        this.updateMoves = updateMoves;
    }

    public void setUpdateMovesLegacy(boolean updateMovesLegacy) {
        this.updateMovesLegacy = updateMovesLegacy;
    }

    public void setUpdateMovesToGeneration(int generation) {
        updateMovesToGeneration = generation;
    }

    public boolean isChangeImpossibleEvolutions() {
        return changeImpossibleEvolutions;
    }

    public void setDualTypeOnly(boolean dualTypeOnly){
        this.dualTypeOnly = dualTypeOnly;
    }

    public void setChangeImpossibleEvolutions(boolean changeImpossibleEvolutions) {
        this.changeImpossibleEvolutions = changeImpossibleEvolutions;
    }

    public boolean isMakeEvolutionsEasier() {
        return makeEvolutionsEasier;
    }

    public void setMakeEvolutionsEasier(boolean makeEvolutionsEasier) {
        this.makeEvolutionsEasier = makeEvolutionsEasier;
    }

    public boolean isRemoveTimeBasedEvolutions() {
        return removeTimeBasedEvolutions;
    }

    public void setRemoveTimeBasedEvolutions(boolean removeTimeBasedEvolutions) {
        this.removeTimeBasedEvolutions = removeTimeBasedEvolutions;
    }

    public void setEvosAllowAltFormes(boolean evosAllowAltFormes) {
        this.evosAllowAltFormes = evosAllowAltFormes;
    }

    public void setRaceMode(boolean raceMode) {
        this.raceMode = raceMode;
    }

    public void setBanIrregularAltFormes(boolean banIrregularAltFormes) {
        this.banIrregularAltFormes = banIrregularAltFormes;
    }

    public void setBlockBrokenMoves(boolean blockBrokenMoves) {
        blockBrokenMovesetMoves = blockBrokenMoves;
        blockBrokenTMMoves = blockBrokenMoves;
        blockBrokenTutorMoves = blockBrokenMoves;
    }

    public boolean isLimitPokemon() {
        return limitPokemon;
    }

    public void setLimitPokemon(boolean limitPokemon) {
        this.limitPokemon = limitPokemon;
    }

    private void setBaseStatisticsMod(BaseStatisticsMod baseStatisticsMod) {
        this.baseStatisticsMod = baseStatisticsMod;
    }

    public void setBaseStatsFollowEvolutions(boolean baseStatsFollowEvolutions) {
        this.baseStatsFollowEvolutions = baseStatsFollowEvolutions;
    }

    public void setBaseStatsFollowMegaEvolutions(boolean baseStatsFollowMegaEvolutions) {
        this.baseStatsFollowMegaEvolutions = baseStatsFollowMegaEvolutions;
    }

    public void setAssignEvoStatsRandomly(boolean assignEvoStatsRandomly) {
        this.assignEvoStatsRandomly = assignEvoStatsRandomly;
    }


    public void setStandardizeEXPCurves(boolean standardizeEXPCurves) {
        this.standardizeEXPCurves = standardizeEXPCurves;
    }

    private void setExpCurveMod(ExpCurveMod expCurveMod) {
        this.expCurveMod = expCurveMod;
    }

    public void setSelectedEXPCurve(ExpCurve expCurve) {
        this.selectedEXPCurve = expCurve;
    }

    public void setUpdateBaseStats(boolean updateBaseStats) {
        this.updateBaseStats = updateBaseStats;
    }

    public void setUpdateBaseStatsToGeneration(int generation) {
        this.updateBaseStatsToGeneration = generation;
    }

    private void setAbilitiesMod(AbilitiesMod abilitiesMod) {
        this.abilitiesMod = abilitiesMod;
    }

    public void setAllowWonderGuard(boolean allowWonderGuard) {
        this.allowWonderGuard = allowWonderGuard;
    }

    public void setAbilitiesFollowEvolutions(boolean abilitiesFollowEvolutions) {
        this.abilitiesFollowEvolutions = abilitiesFollowEvolutions;
    }

    public void setAbilitiesFollowMegaEvolutions(boolean abilitiesFollowMegaEvolutions) {
        this.abilitiesFollowMegaEvolutions = abilitiesFollowMegaEvolutions;
    }

    public void setBanTrappingAbilities(boolean banTrappingAbilities) {
        this.banTrappingAbilities = banTrappingAbilities;
    }

    public void setBanNegativeAbilities(boolean banNegativeAbilities) {
        this.banNegativeAbilities = banNegativeAbilities;
    }

    public void setBanBadAbilities(boolean banBadAbilities) {
        this.banBadAbilities = banBadAbilities;
    }

    public void setWeighDuplicateAbilitiesTogether(boolean weighDuplicateAbilitiesTogether) {
        this.weighDuplicateAbilitiesTogether = weighDuplicateAbilitiesTogether;
    }

    public void setEnsureTwoAbilities(boolean ensureTwoAbilities) {
        this.ensureTwoAbilities = ensureTwoAbilities;
    }

    public StartersMod getStartersMod() {
        return startersMod;
    }

    private void setStartersMod(StartersMod startersMod) {
        this.startersMod = startersMod;
    }

    public void setCustomStarters(int[] customStarters) {
        this.customStarters = customStarters;
    }

    public void setRandomizeStartersHeldItems(boolean randomizeStartersHeldItems) {
        this.randomizeStartersHeldItems = randomizeStartersHeldItems;
    }

    public void setBanBadRandomStarterHeldItems(boolean banBadRandomStarterHeldItems) {
        this.banBadRandomStarterHeldItems = banBadRandomStarterHeldItems;
    }

    public boolean isAllowStarterAltFormes() {
        return allowStarterAltFormes;
    }

    public void setAllowStarterAltFormes(boolean allowStarterAltFormes) {
        this.allowStarterAltFormes = allowStarterAltFormes;
    }


    private void setTypesMod(TypesMod typesMod) {
        this.typesMod = typesMod;
    }

    public void setTypesFollowMegaEvolutions(boolean typesFollowMegaEvolutions) {
        this.typesFollowMegaEvolutions = typesFollowMegaEvolutions;
    }

    private void setEvolutionsMod(EvolutionsMod evolutionsMod) {
        this.evolutionsMod = evolutionsMod;
    }

    public void setEvosSimilarStrength(boolean evosSimilarStrength) {
        this.evosSimilarStrength = evosSimilarStrength;
    }

    public void setEvosSameTyping(boolean evosSameTyping) {
        this.evosSameTyping = evosSameTyping;
    }

    public void setEvosMaxThreeStages(boolean evosMaxThreeStages) {
        this.evosMaxThreeStages = evosMaxThreeStages;
    }

    public void setEvosForceChange(boolean evosForceChange) {
        this.evosForceChange = evosForceChange;
    }

    public void setRandomizeMovePowers(boolean randomizeMovePowers) {
        this.randomizeMovePowers = randomizeMovePowers;
    }

    public void setRandomizeMoveAccuracies(boolean randomizeMoveAccuracies) {
        this.randomizeMoveAccuracies = randomizeMoveAccuracies;
    }

    public void setRandomizeMovePPs(boolean randomizeMovePPs) {
        this.randomizeMovePPs = randomizeMovePPs;
    }

    public void setRandomizeMoveTypes(boolean randomizeMoveTypes) {
        this.randomizeMoveTypes = randomizeMoveTypes;
    }

    public void setRandomizeMoveCategory(boolean randomizeMoveCategory) {
        this.randomizeMoveCategory = randomizeMoveCategory;
    }

    private void setMovesetsMod(MovesetsMod movesetsMod) {
        this.movesetsMod = movesetsMod;
    }

    public void setStartWithGuaranteedMoves(boolean startWithGuaranteedMoves) {
        this.startWithGuaranteedMoves = startWithGuaranteedMoves;
    }

    public void setGuaranteedMoveCount(int guaranteedMoveCount) {
        this.guaranteedMoveCount = guaranteedMoveCount;
    }

    public void setReorderDamagingMoves(boolean reorderDamagingMoves) {
        this.reorderDamagingMoves = reorderDamagingMoves;
    }

    public void setMovesetsForceGoodDamaging(boolean movesetsForceGoodDamaging) {
        this.movesetsForceGoodDamaging = movesetsForceGoodDamaging;
    }

    public void setMovesetsGoodDamagingPercent(int movesetsGoodDamagingPercent) {
        this.movesetsGoodDamagingPercent = movesetsGoodDamagingPercent;
    }

    public void setBlockBrokenMovesetMoves(boolean blockBrokenMovesetMoves) {
        this.blockBrokenMovesetMoves = blockBrokenMovesetMoves;
    }

    public void setEvolutionMovesForAll(boolean evolutionMovesForAll) {
        this.evolutionMovesForAll = evolutionMovesForAll;
    }

    public TrainersMod getTrainersMod() {
        return trainersMod;
    }

    private void setTrainersMod(TrainersMod trainersMod) {
        this.trainersMod = trainersMod;
    }

    public boolean isRivalCarriesStarterThroughout() {
        return rivalCarriesStarterThroughout;
    }

    public void setRivalCarriesStarterThroughout(boolean rivalCarriesStarterThroughout) {
        this.rivalCarriesStarterThroughout = rivalCarriesStarterThroughout;
    }

    public boolean isTrainersUsePokemonOfSimilarStrength() {
        return trainersUsePokemonOfSimilarStrength;
    }

    public void setTrainersUsePokemonOfSimilarStrength(boolean trainersUsePokemonOfSimilarStrength) {
        this.trainersUsePokemonOfSimilarStrength = trainersUsePokemonOfSimilarStrength;
    }

    public void setTrainersMatchTypingDistribution(boolean trainersMatchTypingDistribution) {
        this.trainersMatchTypingDistribution = trainersMatchTypingDistribution;
    }

    public boolean isTrainersBlockLegendaries() {
        return trainersBlockLegendaries;
    }

    public void setTrainersBlockLegendaries(boolean trainersBlockLegendaries) {
        this.trainersBlockLegendaries = trainersBlockLegendaries;
    }


    public boolean isTrainersBlockEarlyWonderGuard() {
        return trainersBlockEarlyWonderGuard;
    }

    public void setTrainersBlockEarlyWonderGuard(boolean trainersBlockEarlyWonderGuard) {
        this.trainersBlockEarlyWonderGuard = trainersBlockEarlyWonderGuard;
    }

    public void setRandomizeTrainerNames(boolean randomizeTrainerNames) {
        this.randomizeTrainerNames = randomizeTrainerNames;
    }

    public void setRandomizeTrainerClassNames(boolean randomizeTrainerClassNames) {
        this.randomizeTrainerClassNames = randomizeTrainerClassNames;
    }

    public void setTrainersForceFullyEvolved(boolean trainersForceFullyEvolved) {
        this.trainersForceFullyEvolved = trainersForceFullyEvolved;
    }

    public void setTrainersForceFullyEvolvedLevel(int trainersForceFullyEvolvedLevel) {
        this.trainersForceFullyEvolvedLevel = trainersForceFullyEvolvedLevel;
    }

    public void setTrainersLevelModified(boolean trainersLevelModified) {
        this.trainersLevelModified = trainersLevelModified;
    }

    public void setTrainersLevelModifier(int trainersLevelModifier) {
        this.trainersLevelModifier = trainersLevelModifier;
    }

    public void setEliteFourUniquePokemonNumber(int eliteFourUniquePokemonNumber) {
        this.eliteFourUniquePokemonNumber = eliteFourUniquePokemonNumber;
    }


    public void setAllowTrainerAlternateFormes(boolean allowTrainerAlternateFormes) {
        this.allowTrainerAlternateFormes = allowTrainerAlternateFormes;
    }

    public void setSwapTrainerMegaEvos(boolean swapTrainerMegaEvos) {
        this.swapTrainerMegaEvos = swapTrainerMegaEvos;
    }

    public void setAdditionalBossTrainerPokemon(int additional) {
        this.additionalBossTrainerPokemon = additional;
    }

    public void setAdditionalImportantTrainerPokemon(int additional) {
        this.additionalImportantTrainerPokemon = additional;
    }

    public void setAdditionalRegularTrainerPokemon(int additional) {
        this.additionalRegularTrainerPokemon = additional;
    }

    public void setRandomizeHeldItemsForBossTrainerPokemon(boolean bossTrainers) {
        this.randomizeHeldItemsForBossTrainerPokemon = bossTrainers;
    }

    public void setRandomizeHeldItemsForImportantTrainerPokemon(boolean importantTrainers) {
        this.randomizeHeldItemsForImportantTrainerPokemon = importantTrainers;
    }

    public void setRandomizeHeldItemsForRegularTrainerPokemon(boolean regularTrainers) {
        this.randomizeHeldItemsForRegularTrainerPokemon = regularTrainers;
    }

    public void setConsumableItemsOnlyForTrainers(boolean consumableOnly) {
        this.consumableItemsOnlyForTrainerPokemon = consumableOnly;
    }

    public void setSensibleItemsOnlyForTrainers(boolean sensibleOnly) {
        this.sensibleItemsOnlyForTrainerPokemon = sensibleOnly;
    }

    public void setHighestLevelGetsItemsForTrainers(boolean highestOnly) {
        this.highestLevelOnlyGetsItemsForTrainerPokemon = highestOnly;
    }

    public void setDoubleBattleMode(boolean doubleBattleMode) {
        this.doubleBattleMode = doubleBattleMode;
    }

    public void setShinyChance(boolean shinyChance) {
        this.shinyChance = shinyChance;
    }

    public void setBetterTrainerMovesets(boolean betterTrainerMovesets) {
        this.betterTrainerMovesets = betterTrainerMovesets;
    }

    public WildPokemonMod getWildPokemonMod() {
        return wildPokemonMod;
    }

    private void setWildPokemonMod(WildPokemonMod wildPokemonMod) {
        this.wildPokemonMod = wildPokemonMod;
    }

    public WildPokemonRestrictionMod getWildPokemonRestrictionMod() {
        return wildPokemonRestrictionMod;
    }

    private void setWildPokemonRestrictionMod(WildPokemonRestrictionMod wildPokemonRestrictionMod) {
        this.wildPokemonRestrictionMod = wildPokemonRestrictionMod;
    }

    public boolean isUseTimeBasedEncounters() {
        return useTimeBasedEncounters;
    }

    public void setUseTimeBasedEncounters(boolean useTimeBasedEncounters) {
        this.useTimeBasedEncounters = useTimeBasedEncounters;
    }

    public boolean isBlockWildLegendaries() {
        return blockWildLegendaries;
    }

    public void setBlockWildLegendaries(boolean blockWildLegendaries) {
        this.blockWildLegendaries = blockWildLegendaries;
    }

    public void setUseMinimumCatchRate(boolean useMinimumCatchRate) {
        this.useMinimumCatchRate = useMinimumCatchRate;
    }

    public void setMinimumCatchRateLevel(int minimumCatchRateLevel) {
        this.minimumCatchRateLevel = minimumCatchRateLevel;
    }

    public void setRandomizeWildPokemonHeldItems(boolean randomizeWildPokemonHeldItems) {
        this.randomizeWildPokemonHeldItems = randomizeWildPokemonHeldItems;
    }

    public void setBanBadRandomWildPokemonHeldItems(boolean banBadRandomWildPokemonHeldItems) {
        this.banBadRandomWildPokemonHeldItems = banBadRandomWildPokemonHeldItems;
    }

    public void setBalanceShakingGrass(boolean balanceShakingGrass) {
        this.balanceShakingGrass = balanceShakingGrass;
    }

    public boolean isWildLevelsModified() {
        return wildLevelsModified;
    }

    public void setWildLevelsModified(boolean wildLevelsModified) {
        this.wildLevelsModified = wildLevelsModified;
    }

    public void setWildLevelModifier(int wildLevelModifier) {
        this.wildLevelModifier = wildLevelModifier;
    }

    public void setAllowWildAltFormes(boolean allowWildAltFormes) {
        this.allowWildAltFormes = allowWildAltFormes;
    }

    public StaticPokemonMod getStaticPokemonMod() {
        return staticPokemonMod;
    }

    private void setStaticPokemonMod(StaticPokemonMod staticPokemonMod) {
        this.staticPokemonMod = staticPokemonMod;
    }

    public boolean isLimitMainGameLegendaries() {
        return limitMainGameLegendaries;
    }

    public void setLimitMainGameLegendaries(boolean limitMainGameLegendaries) {
        this.limitMainGameLegendaries = limitMainGameLegendaries;
    }

    public void setLimit600(boolean limit600) {
        this.limit600 = limit600;
    }

    public boolean isAllowStaticAltFormes() {
        return allowStaticAltFormes;
    }

    public void setAllowStaticAltFormes(boolean allowStaticAltFormes) {
        this.allowStaticAltFormes = allowStaticAltFormes;
    }

    public void setSwapStaticMegaEvos(boolean swapStaticMegaEvos) {
        this.swapStaticMegaEvos = swapStaticMegaEvos;
    }

    public void setStaticLevelModified(boolean staticLevelModified) {
        this.staticLevelModified = staticLevelModified;
    }

    public void setStaticLevelModifier(int staticLevelModifier) {
        this.staticLevelModifier = staticLevelModifier;
    }

    public void setCorrectStaticMusic(boolean correctStaticMusic) {
        this.correctStaticMusic = correctStaticMusic;
    }


    private void setTotemPokemonMod(TotemPokemonMod totemPokemonMod) {
        this.totemPokemonMod = totemPokemonMod;
    }

    private void setAllyPokemonMod(AllyPokemonMod allyPokemonMod) {
        this.allyPokemonMod = allyPokemonMod;
    }

    private void setAuraMod(AuraMod auraMod) {
        this.auraMod = auraMod;
    }

    public void setRandomizeTotemHeldItems(boolean randomizeTotemHeldItems) {
        this.randomizeTotemHeldItems = randomizeTotemHeldItems;
    }

    public void setTotemLevelsModified(boolean totemLevelsModified) {
        this.totemLevelsModified = totemLevelsModified;
    }

    public void setTotemLevelModifier(int totemLevelModifier) {
        this.totemLevelModifier = totemLevelModifier;
    }

    public void setAllowTotemAltFormes(boolean allowTotemAltFormes) {
        this.allowTotemAltFormes = allowTotemAltFormes;
    }

    private void setTmsMod(TMsMod tmsMod) {
        this.tmsMod = tmsMod;
    }

    public void setTmLevelUpMoveSanity(boolean tmLevelUpMoveSanity) {
        this.tmLevelUpMoveSanity = tmLevelUpMoveSanity;
    }

    public void setKeepFieldMoveTMs(boolean keepFieldMoveTMs) {
        this.keepFieldMoveTMs = keepFieldMoveTMs;
    }

    public void setFullHMCompat(boolean fullHMCompat) {
        this.fullHMCompat = fullHMCompat;
    }

    public void setTmsForceGoodDamaging(boolean tmsForceGoodDamaging) {
        this.tmsForceGoodDamaging = tmsForceGoodDamaging;
    }

    public void setTmsGoodDamagingPercent(int tmsGoodDamagingPercent) {
        this.tmsGoodDamagingPercent = tmsGoodDamagingPercent;
    }

    public void setBlockBrokenTMMoves(boolean blockBrokenTMMoves) {
        this.blockBrokenTMMoves = blockBrokenTMMoves;
    }

    private void setTmsHmsCompatibilityMod(TMsHMsCompatibilityMod tmsHmsCompatibilityMod) {
        this.tmsHmsCompatibilityMod = tmsHmsCompatibilityMod;
    }

    public void setTmsFollowEvolutions(boolean tmsFollowEvolutions) {
        this.tmsFollowEvolutions = tmsFollowEvolutions;
    }

    private void setMoveTutorMovesMod(MoveTutorMovesMod moveTutorMovesMod) {
        this.moveTutorMovesMod = moveTutorMovesMod;
    }

    public void setTutorLevelUpMoveSanity(boolean tutorLevelUpMoveSanity) {
        this.tutorLevelUpMoveSanity = tutorLevelUpMoveSanity;
    }

    public void setKeepFieldMoveTutors(boolean keepFieldMoveTutors) {
        this.keepFieldMoveTutors = keepFieldMoveTutors;
    }

    public void setTutorsForceGoodDamaging(boolean tutorsForceGoodDamaging) {
        this.tutorsForceGoodDamaging = tutorsForceGoodDamaging;
    }

    public void setTutorsGoodDamagingPercent(int tutorsGoodDamagingPercent) {
        this.tutorsGoodDamagingPercent = tutorsGoodDamagingPercent;
    }

    public void setBlockBrokenTutorMoves(boolean blockBrokenTutorMoves) {
        this.blockBrokenTutorMoves = blockBrokenTutorMoves;
    }

    private void setMoveTutorsCompatibilityMod(MoveTutorsCompatibilityMod moveTutorsCompatibilityMod) {
        this.moveTutorsCompatibilityMod = moveTutorsCompatibilityMod;
    }

    public void setTutorFollowEvolutions(boolean tutorFollowEvolutions) {
        this.tutorFollowEvolutions = tutorFollowEvolutions;
    }

    private void setInGameTradesMod(InGameTradesMod inGameTradesMod) {
        this.inGameTradesMod = inGameTradesMod;
    }

    public void setRandomizeInGameTradesNicknames(boolean randomizeInGameTradesNicknames) {
        this.randomizeInGameTradesNicknames = randomizeInGameTradesNicknames;
    }

    public void setRandomizeInGameTradesOTs(boolean randomizeInGameTradesOTs) {
        this.randomizeInGameTradesOTs = randomizeInGameTradesOTs;
    }

    public void setRandomizeInGameTradesIVs(boolean randomizeInGameTradesIVs) {
        this.randomizeInGameTradesIVs = randomizeInGameTradesIVs;
    }

    public void setRandomizeInGameTradesItems(boolean randomizeInGameTradesItems) {
        this.randomizeInGameTradesItems = randomizeInGameTradesItems;
    }

    private void setFieldItemsMod(FieldItemsMod fieldItemsMod) {
        this.fieldItemsMod = fieldItemsMod;
    }


    public void setBanBadRandomFieldItems(boolean banBadRandomFieldItems) {
        this.banBadRandomFieldItems = banBadRandomFieldItems;
    }

    private void setShopItemsMod(ShopItemsMod shopItemsMod) {
        this.shopItemsMod = shopItemsMod;
    }

    public void setBanBadRandomShopItems(boolean banBadRandomShopItems) {
        this.banBadRandomShopItems = banBadRandomShopItems;
    }

    public void setBanRegularShopItems(boolean banRegularShopItems) {
        this.banRegularShopItems = banRegularShopItems;
    }

    public void setBanOPShopItems(boolean banOPShopItems) {
        this.banOPShopItems = banOPShopItems;
    }

    public void setBalanceShopPrices(boolean balanceShopPrices) {
        this.balanceShopPrices = balanceShopPrices;
    }

    public void setGuaranteeEvolutionItems(boolean guaranteeEvolutionItems) {
        this.guaranteeEvolutionItems = guaranteeEvolutionItems;
    }

    public void setGuaranteeXItems(boolean guaranteeXItems) {
        this.guaranteeXItems = guaranteeXItems;
    }

    private void setPickupItemsMod(PickupItemsMod pickupItemsMod) {
        this.pickupItemsMod = pickupItemsMod;
    }

    public void setBanBadRandomPickupItems(boolean banBadRandomPickupItems) {
        this.banBadRandomPickupItems = banBadRandomPickupItems;
    }

    private static int makeByteSelected(boolean... bools) {
        if (bools.length > 8) {
            throw new IllegalArgumentException("Can't set more than 8 bits in a byte!");
        }

        int initial = 0;
        int state = 1;
        for (boolean b : bools) {
            initial |= b ? state : 0;
            state *= 2;
        }
        return initial;
    }

    private static boolean restoreState(byte b, int index) {
        if (index >= 8) {
            throw new IllegalArgumentException("Can't read more than 8 bits from a byte!");
        }

        int value = b & 0xFF;
        return ((value >> index) & 0x01) == 0x01;
    }

    private static void writeFullInt(ByteArrayOutputStream out, int value) throws IOException {
        byte[] crc = ByteBuffer.allocate(4).putInt(value).array();
        out.write(crc);
    }

    private static void write2ByteInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static <E extends Enum<E>> E restoreEnum(Class<E> clazz, byte b, int... indices) {
        boolean[] bools = new boolean[indices.length];
        int i = 0;
        for (int idx : indices) {
            bools[i] = restoreState(b, idx);
            i++;
        }
        return getEnum(clazz, bools);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E getEnum(Class<E> clazz, boolean... bools) {
        int index = getSetEnum(clazz.getSimpleName(), bools);
        try {
            return ((E[]) clazz.getMethod("values").invoke(null))[index];
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Unable to parse enum of type %s", clazz.getSimpleName()),
                    e);
        }
    }

    private static int getSetEnum(String type, boolean... bools) {
        int index = -1;
        for (int i = 0; i < bools.length; i++) {
            if (bools[i]) {
                if (index >= 0) {
                    throw new IllegalStateException(String.format("Only one value for %s may be chosen!", type));
                }
                index = i;
            }
        }
        // We have to return something, so return the default
        return index >= 0 ? index : 0;
    }

    private static void checkChecksum(byte[] data) {
        // Check the checksum
        ByteBuffer buf = ByteBuffer.allocate(4).put(data, data.length - 8, 4);
        buf.rewind();
        int crc = buf.getInt();

        CRC32 checksum = new CRC32();
        checksum.update(data, 0, data.length - 8);

        if ((int) checksum.getValue() != crc) {
            throw new IllegalArgumentException("Malformed input string");
        }
    }

}
