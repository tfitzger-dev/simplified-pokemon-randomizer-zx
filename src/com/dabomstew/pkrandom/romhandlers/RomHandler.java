package com.dabomstew.pkrandom.romhandlers;

/*----------------------------------------------------------------------------*/
/*--  RomHandler.java - defines the functionality that each randomization   --*/
/*--                    handler must implement.                             --*/
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

import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.dabomstew.pkrandom.MiscTweak;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.pokemon.*;

public interface RomHandler {

    abstract class Factory {
        public RomHandler create(Random random) {
            return create(random, null);
        }

        public abstract RomHandler create(Random random, PrintStream log);

        public abstract boolean isLoadable(String filename);
    }

    // =======================
    // Basic load/save methods
    // =======================

    boolean loadRom(String filename);

    boolean saveRomFile(String filename, long seed);

    String loadedFilename();

    // =============================================================
    // Methods relating to game updates for the 3DS and Switch games
    // =============================================================

    boolean loadGameUpdate(String filename);

    // ===========
    // Log methods
    // ===========

    boolean isRomValid();

    // ======================================================
    // Methods for retrieving a list of Pokemon objects.
    // Note that for many of these lists, index 0 is null.
    // Instead, you use index on the species' National Dex ID
    // ======================================================

    List<Pokemon> getPokemon();

    List<Pokemon> getPokemonInclFormes();

    // ==================================
    // Methods to set up Gen Restrictions
    // ==================================

    void setPokemonPool(Settings settings);

    // ===============
    // Starter Pokemon
    // ===============

    List<Pokemon> getStarters();

    boolean setStarters(List<Pokemon> newStarters);

    int starterCount();

    void randomizeBasicTwoEvosStarters(Settings settings);

    // =======================
    // Pokemon Base Statistics
    // =======================

    // ====================================
    // Methods for selecting random Pokemon
    // ====================================

    // Give a random Pokemon who's in this game
    Pokemon randomPokemon();

    Pokemon randomPokemonInclFormes();

    // Give a random non-legendary Pokemon who's in this game
    // Business rules for who's legendary are in Pokemon class
    Pokemon randomNonLegendaryPokemon();

    // Give a random Pokemon who has 2 evolution stages
    // Should make a good starter Pokemon
    Pokemon random2EvosPokemon(boolean allowAltFormes);

    // =============
    // Pokemon Types
    // =============

    // return a random type valid in this game.
    Type randomType();

    boolean typeInGame(Type type);

    // =================
    // Pokemon Abilities
    // =================

    int abilitiesPerPokemon();

    // ============
    // Wild Pokemon
    // ============

    List<EncounterSet> getEncounters(boolean useTimeOfDay);

    void setEncounters(boolean useTimeOfDay, List<EncounterSet> encounters);

    void randomEncounters(Settings settings);

    void area1to1Encounters(Settings settings);

    boolean hasTimeBasedEncounters();

    List<Pokemon> bannedForWildEncounters();

    // ===============
    // Trainer Pokemon
    // ===============

    List<Trainer> getTrainers();

    List<Integer> getMainPlaythroughTrainers();

    void setTrainers(List<Trainer> trainerData, boolean doubleBattleMode);

    void randomizeTrainerPokes(Settings settings);

    void rivalCarriesStarter();

    // =========
    // Move Data
    // =========

    boolean hasPhysicalSpecialSplit();

    // return all the moves valid in this game.
    List<Move> getMoves();

    // ================
    // Pokemon Movesets
    // ================

    Map<Integer, List<MoveLearnt>> getMovesLearnt();

    boolean supportsFourStartingMoves();

    // ==============
    // Static Pokemon
    // ==============

    List<StaticEncounter> getStaticPokemon();

    boolean setStaticPokemon(List<StaticEncounter> staticPokemon);

    void randomizeStaticPokemon(Settings settings);

    boolean canChangeStaticPokemon();

    List<Pokemon> bannedForStaticPokemon();

    boolean hasMainGameLegendaries();

    List<Integer> getMainGameLegendaries();

    // =============
    // Totem Pokemon
    // =============

    // =========
    // TMs & HMs
    // =========

    // ===========
    // Move Tutors
    // ===========

    boolean hasMoveTutors();

    // =============
    // Trainer Names
    // =============


    // ===============
    // Trainer Classes
    // ===============

    List<String> getTrainerClassNames();

    // =====
    // Items
    // =====

    ItemList getNonBadItems();

    // ===========
    // Field Items
    // ===========

    // TMs on the field

    // Everything else

    // Randomizer methods

    // ============
    // Special Shops
    // =============

    boolean hasShopRandomization();

    // ============
    // Pickup Items
    // ============

    // ==============
    // In-Game Trades
    // ==============

    // ==================
    // Pokemon Evolutions
    // ==================

    void removeImpossibleEvolutions(Settings settings);

    void condenseLevelEvolutions(int maxLevel, int maxIntermediateLevel);

    void makeEvolutionsEasier(Settings settings);

    void removeTimeBasedEvolutions();

    // ==================================
    // (Mostly) unchanging lists of moves
    // ==================================


    // ====
    // Misc
    // ====

    boolean isYellow();

    String getDefaultExtension();

    int internalStringLength(String string);

    void randomizeIntroPokemon();

    int generationOfPokemon();

    void writeCheckValueToROM(int value);

    // ===========
    // code tweaks
    // ===========

    int miscTweaksAvailable();

    void applyMiscTweaks(Settings settings);

    void applyMiscTweak(MiscTweak tweak);

    // ==========================
    // Misc forme-related methods
    // ==========================

    List<Pokemon> getAbilityDependentFormes();

    List<Pokemon> getBannedFormesForPlayerPokemon();

}