package com.dabomstew.pkrandom.pokemon;

/*----------------------------------------------------------------------------*/
/*--  StaticEncounter.java - stores a static encounter in Gen 6+            --*/
/*--                                                                        --*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
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

public class StaticEncounter {
    public Pokemon pkmn;
    public int forme = 0;
    public int level;
    public int maxLevel = 0;
    public int heldItem;
    public boolean isEgg = false;
    public boolean resetMoves = false;

    // In the games, sometimes what is logically an encounter or set of encounters with one specific Pokemon
    // can actually consist of multiple encounters internally. This can happen because:
    // - The same Pokemon appears in multiple locations (e.g., Reshiram/Zekrom in BW1, Giratina in Pt)
    // - The same Pokemon appears at different levels depending on game progression (e.g., Volcarona in BW2)
    // - Rebattling a Pokemon actually is different encounter entirely (e.g., Xerneas/Yveltal in XY)
    // This list tracks encounters that should logically have the same species and forme, but *may* have
    // differences in other properties like level.
    public List<StaticEncounter> linkedEncounters;

    public StaticEncounter() {
        this.linkedEncounters = new ArrayList<>();
    }


    public String toString() {
        if (isEgg) {
            return pkmn.fullName() + " (egg)";
        }
        else{
            return pkmn.fullName();
        }
    }

}
