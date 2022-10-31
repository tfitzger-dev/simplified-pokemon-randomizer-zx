package com.dabomstew.pkrandom;

/*----------------------------------------------------------------------------*/
/*--  RandomSource.java - functions as a centralized source of randomness   --*/
/*--                      to allow the same seed to produce the same random --*/
/*--                      ROM consistently.                                 --*/
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

import java.security.SecureRandom;
import java.util.Random;

public class RandomSource {

    private static Random source = new Random();
    private static Random cosmeticSource = new Random();
    private static Random instance = new RandomSourceInstance();

    public static void seed(long seed) {
        source.setSeed(seed);
        cosmeticSource.setSeed(seed);
    }

    public static int nextInt(int size) {
        return source.nextInt(size);
    }

    public static long pickSeed() {
        long value = 0;
        byte[] by = SecureRandom.getSeed(6);
        for (int i = 0; i < by.length; i++) {
            value |= ((long) by[i] & 0xffL) << (8 * i);
        }
        return value;
    }

    public static Random instance() {
        return instance;
    }

    private static class RandomSourceInstance extends Random {

        /**
         * 
         */
        private static final long serialVersionUID = -4876737183441746322L;

        @Override
        public synchronized void setSeed(long seed) {
            RandomSource.seed(seed);
        }


        @Override
        public int nextInt(int n) {
            return RandomSource.nextInt(n);
        }

    }
}
