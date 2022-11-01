package com.dabomstew.pkrandom.pokemon;

public class EvolutionUpdate implements Comparable<EvolutionUpdate> {

    private Pokemon from, to;
    private String fromName, toName;
    private EvolutionType type;
    private String extraInfo;
    private boolean condensed;
    private boolean additional;


    public EvolutionUpdate(Pokemon from, Pokemon to, EvolutionType type, String extraInfo, boolean condensed, boolean additional) {
        this.from = from;
        this.to = to;
        fromName = from.fullName();
        toName = to.fullName();
        this.type = type;
        this.extraInfo = extraInfo;
        this.condensed = condensed;
        this.additional = additional;
    }

    @Override
    public int compareTo(EvolutionUpdate o) {
        if (this.from.number > o.from.number) {
            return 1;
        } else return Integer.compare(this.to.number, o.to.number);
    }
}
