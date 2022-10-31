package com.dabomstew.pkrandom;

/*----------------------------------------------------------------------------*/
/*--  RomFunctions.java - contains functions useful throughout the program. --*/
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.dabomstew.pkrandom.pokemon.Evolution;
import com.dabomstew.pkrandom.pokemon.MoveLearnt;
import com.dabomstew.pkrandom.pokemon.Pokemon;
import com.dabomstew.pkrandom.romhandlers.RomHandler;

public class RomFunctions {

    /**
     * Get the 4 moves known by a Pokemon at a particular level.
     * 
     * @param pkmn Pokemon index to get moves for.
     * @param movesets Map of Pokemon indices mapped to movesets.
     * @param level Level to get at.
     * @return Array with move indices.
     */
    public static int[] getMovesAtLevel(int pkmn, Map<Integer, List<MoveLearnt>> movesets, int level) {
        return getMovesAtLevel(pkmn, movesets, level, 0);
    }

    public static int[] getMovesAtLevel(int pkmn, Map<Integer, List<MoveLearnt>> movesets, int level, int emptyValue) {
        int[] curMoves = new int[4];

        if (emptyValue != 0) {
            Arrays.fill(curMoves, emptyValue);
        }

        int moveCount = 0;
        List<MoveLearnt> movepool = movesets.get(pkmn);
        for (MoveLearnt ml : movepool) {
            if (ml.level > level) {
                // we're done
                break;
            }

            boolean alreadyKnownMove = false;
            for (int i = 0; i < moveCount; i++) {
                if (curMoves[i] == ml.move) {
                    alreadyKnownMove = true;
                    break;
                }
            }

            if (!alreadyKnownMove) {
                // add this move to the moveset
                if (moveCount == 4) {
                    // shift moves up and add to last slot
                    System.arraycopy(curMoves, 1, curMoves, 0, 3);
                    curMoves[3] = ml.move;
                } else {
                    // add to next available slot
                    curMoves[moveCount++] = ml.move;
                }
            }
        }

        return curMoves;
    }

    public static List<Integer> search(byte[] haystack, byte[] needle) {
        return search(haystack, 0, haystack.length, needle);
    }

    public static List<Integer> search(byte[] haystack, int beginOffset, int endOffset, byte[] needle) {
        int currentMatchStart = beginOffset;
        int currentCharacterPosition = 0;

        int needleSize = needle.length;

        int[] toFillTable = buildKMPSearchTable(needle);
        List<Integer> results = new ArrayList<>();

        while ((currentMatchStart + currentCharacterPosition) < endOffset) {

            if (needle[currentCharacterPosition] == (haystack[currentCharacterPosition + currentMatchStart])) {
                currentCharacterPosition = currentCharacterPosition + 1;

                if (currentCharacterPosition == (needleSize)) {
                    results.add(currentMatchStart);
                    currentCharacterPosition = 0;
                    currentMatchStart = currentMatchStart + needleSize;

                }

            } else {
                currentMatchStart = currentMatchStart + currentCharacterPosition
                        - toFillTable[currentCharacterPosition];

                if (toFillTable[currentCharacterPosition] > -1) {
                    currentCharacterPosition = toFillTable[currentCharacterPosition];
                }

                else {
                    currentCharacterPosition = 0;

                }

            }
        }
        return results;
    }

    private static int[] buildKMPSearchTable(byte[] needle) {
        int[] stable = new int[needle.length];
        int pos = 2;
        int j = 0;
        stable[0] = -1;
        stable[1] = 0;
        while (pos < needle.length) {
            if (needle[pos - 1] == needle[j]) {
                stable[pos] = j + 1;
                pos++;
                j++;
            } else if (j > 0) {
                j = stable[j];
            } else {
                stable[pos] = 0;
                pos++;
            }
        }
        return stable;
    }

}
