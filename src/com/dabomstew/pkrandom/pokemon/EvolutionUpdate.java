package com.dabomstew.pkrandom.pokemon;

public class EvolutionUpdate implements Comparable<EvolutionUpdate> {

    private Pokemon from, to;


    public EvolutionUpdate(Pokemon from, Pokemon to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public int compareTo(EvolutionUpdate o) {
        if (this.from.number > o.from.number) {
            return 1;
        } else return Integer.compare(this.to.number, o.to.number);
    }
}
