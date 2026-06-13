package com.aow2.core.campaign;

import com.aow2.common.model.Faction;

/**
 * Campaign episodes in Art of War 2: Online.
 * REF: campaign_guide.md - Episode 1 (Global Confederation) and Episode 2 (Liberation of Peru)
 */
public enum CampaignEpisode {
    /** Episode 1: Global Confederation - 14 missions playing as the Confederation faction. */
    GLOBAL_CONFEDERATION("Global Confederation", Faction.CONFEDERATION, 14),

    /** Episode 2: Liberation of Peru - 15 missions playing as the Resistance faction. */
    LIBERATION_OF_PERU("Liberation of Peru", Faction.RESISTANCE, 15),

    /** Custom missions loaded from mods. */
    CUSTOM_MISSIONS("Custom Missions", Faction.NEUTRAL, 0);

    private final String title;
    private final Faction faction;
    private final int missionCount;

    CampaignEpisode(String title, Faction faction, int missionCount) {
        this.title = title;
        this.faction = faction;
        this.missionCount = missionCount;
    }

    public String title() { return title; }
    public Faction faction() { return faction; }
    public int missionCount() { return missionCount; }
}
