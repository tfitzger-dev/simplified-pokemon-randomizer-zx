package com.dabomstew.pkrandom;

/*----------------------------------------------------------------------------*/
/*--  Randomizer.java - Can randomize a file based on settings.             --*/
/*--                    Output varies by seed.                              --*/
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

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.*;

import com.dabomstew.pkrandom.pokemon.*;
import com.dabomstew.pkrandom.romhandlers.Gen1RomHandler;
import com.dabomstew.pkrandom.romhandlers.RomHandler;

// Can randomize a file based on settings. Output varies by seed.
public class Randomizer {

    private final Settings settings;
    private final RomHandler romHandler;

    public Randomizer(Settings settings, RomHandler romHandler) {
        this.settings = settings;
        this.romHandler = romHandler;
    }

    public int randomize(final String filename, final PrintStream log) {
        long seed = RandomSource.pickSeed();
        // long seed = 123456789;    // TESTING
        return randomize(filename, log, seed);
    }

    public int randomize(final String filename, final PrintStream log, long seed) {
        RandomSource.seed(seed);

        int checkValue = 0;

        log.println("Randomizer Version: EXTERMINATE");
        log.println("Random Seed: " + seed);
        log.println("Settings String: " + "Version.VERSION" + settings.toString());
        log.println();

        // All possible changes that can be logged
        boolean staticsChanged = false;

        // Limit Pokemon
        // 1. Set Pokemon pool according to limits (or lack thereof)
        // 2. If limited, remove evolutions that are outside of the pool

        romHandler.setPokemonPool(settings);

        // Move updates & data changes
        // 1. Update moves to a future generation
        // 2. Randomize move stats

        // Misc Tweaks
        if (settings.getCurrentMiscTweaks() != MiscTweak.NO_MISC_TWEAKS) {
            romHandler.applyMiscTweaks(settings);
        }

        for (Pokemon pkmn : romHandler.getPokemon()) {
            if (pkmn != null) {
                checkValue = addToCV(checkValue, pkmn.hp, pkmn.attack, pkmn.defense, pkmn.speed, pkmn.spatk,
                        pkmn.spdef, pkmn.ability1, pkmn.ability2, pkmn.ability3);
            }
        }

        // Trade evolutions removal
        if (settings.isChangeImpossibleEvolutions()) {
            romHandler.removeImpossibleEvolutions(settings);
        }

        // Easier evolutions
        if (settings.isMakeEvolutionsEasier()) {
            romHandler.condenseLevelEvolutions(40, 30);
            romHandler.makeEvolutionsEasier(settings);
        }

        // Remove time-based evolutions
        if (settings.isRemoveTimeBasedEvolutions()) {
            romHandler.removeTimeBasedEvolutions();
        }

        // Starter Pokemon
        // Applied after type to update the strings correctly based on new types
        switch(settings.getStartersMod()) {
            case RANDOM_WITH_TWO_EVOLUTIONS:
                romHandler.randomizeBasicTwoEvosStarters(settings);
                break;
            default:
                break;
        }

        switch(settings.getTrainersMod()) {
            case RANDOM:
            case DISTRIBUTED:
            case MAINPLAYTHROUGH:
            case TYPE_THEMED:
            case TYPE_THEMED_ELITE4_GYMS:
                romHandler.randomizeTrainerPokes(settings);
                break;
            default:
                break;
        }

        if (settings.getTrainersMod() != Settings.TrainersMod.UNCHANGED
                && settings.isRivalCarriesStarterThroughout()) {
            romHandler.rivalCarriesStarter();
        }

        List<Trainer> trainers = romHandler.getTrainers();
        for (Trainer t : trainers) {
            for (TrainerPokemon tpk : t.pokemon) {
                checkValue = addToCV(checkValue, tpk.level, tpk.pokemon.number);
            }
        }

        // Static Pokemon
        if (romHandler.canChangeStaticPokemon()) {
            List<StaticEncounter> oldStatics = romHandler.getStaticPokemon();
            if (settings.getStaticPokemonMod() != Settings.StaticPokemonMod.UNCHANGED) { // Legendary for L
                romHandler.randomizeStaticPokemon(settings);
                staticsChanged = true;
            }

            if (staticsChanged) {
                checkValue = logStaticPokemon(log, checkValue, oldStatics);
            }
        }

        switch (settings.getWildPokemonMod()) {
            case RANDOM:
                romHandler.randomEncounters(settings);
                break;
            case AREA_MAPPING:
                romHandler.area1to1Encounters(settings);
                break;
            default:
                break;
        }

        boolean useTimeBasedEncounters = settings.isUseTimeBasedEncounters() ||
                (settings.getWildPokemonMod() == Settings.WildPokemonMod.UNCHANGED && settings.isWildLevelsModified());
        List<EncounterSet> encounters = romHandler.getEncounters(useTimeBasedEncounters);
        for (EncounterSet es : encounters) {
            for (Encounter e : es.encounters) {
                checkValue = addToCV(checkValue, e.level, e.pokemon.number);
            }
        }

        // Test output for placement history
        // romHandler.renderPlacementHistory();

        // Intro Pokemon...
        romHandler.randomizeIntroPokemon();

        // Record check value?
        romHandler.writeCheckValueToROM(checkValue);

        // Save
        romHandler.saveRomFile(filename, seed);
        return checkValue;
    }

    private int logStaticPokemon(final PrintStream log, int checkValue, List<StaticEncounter> oldStatics) {

        List<StaticEncounter> newStatics = romHandler.getStaticPokemon();

        log.println("--Static Pokemon--");
        Map<String, Integer> seenPokemon = new TreeMap<>();
        for (int i = 0; i < oldStatics.size(); i++) {
            StaticEncounter oldP = oldStatics.get(i);
            StaticEncounter newP = newStatics.get(i);
            checkValue = addToCV(checkValue, newP.pkmn.number);
            String oldStaticString = oldP.toString();
            if (seenPokemon.containsKey(oldStaticString)) {
                int amount = seenPokemon.get(oldStaticString);
                log.print("(" + (++amount) + ")");
                seenPokemon.put(oldStaticString, amount);
            } else {
                seenPokemon.put(oldStaticString, 1);
            }
            log.println(" => " + newP.toString());
        }
        log.println();

        return checkValue;
    }

    
    private static int addToCV(int checkValue, int... values) {
        for (int value : values) {
            checkValue = Integer.rotateLeft(checkValue, 3);
            checkValue ^= value;
        }
        return checkValue;
    }
}