package com.dabomstew.pkrandom.pokemon;

/*----------------------------------------------------------------------------*/
/*--  Trainer.java - represents a Trainer's pokemon set/other details.      --*/
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

import java.util.ArrayList;
import java.util.List;

public class Trainer implements Comparable<Trainer> {
    public int offset;
    public int index;
    public List<TrainerPokemon> pokemon = new ArrayList<>();
    public String tag;
    // This value has some flags about the trainer's pokemon (e.g. if they have items or custom moves)
    public int poketype;
    public String name;
    public int trainerclass;
    public String fullDisplayName;
    public MultiBattleStatus multiBattleStatus = MultiBattleStatus.NEVER;


    @Override
    public int compareTo(Trainer o) {
        return index - o.index;
    }

    public boolean pokemonHaveItems() {
        // This flag seems consistent for all gens
        return (this.poketype & 2) == 2;
    }

    public boolean pokemonHaveCustomMoves() {
        // This flag seems consistent for all gens
        return (this.poketype & 1) == 1;
    }

    public enum MultiBattleStatus {
        NEVER, POTENTIAL, ALWAYS
    }
}
