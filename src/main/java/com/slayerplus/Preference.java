package com.slayerplus;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

public final class Preference {
  private Preference() {}

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  public enum Workflow {
    NORMAL("Normal Slayer"),
    TURAEL_POINT_BOOST("Turael point boosting");
    private final String displayName;

    @Override
    public String toString() {
      return displayName;
    }
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  public enum BonusMaster {
    MAZCHNA("Mazchna", 2),
    VANNAKA("Vannaka", 3),
    CHAELDAR("Chaeldar", 4),
    DURADEL("Duradel", 5),
    NIEVE_STEVE("Nieve", 6),
    KONAR("Konar", 8);
    private final String displayName;
    private final int masterId;

    public int getMasterId() {
      return masterId;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  public enum CombatStyle {
    AUTOMATIC("Automatic"),
    PREFER_MELEE("Prefer Melee"),
    PREFER_RANGED("Prefer Ranged"),
    PREFER_MAGIC("Prefer Magic");
    private final String displayName;

    @Override
    public String toString() {
      return displayName;
    }
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  public enum Cannon {
    PREFER("Prefer"),
    ALLOW("Allow"),
    NEVER("Never");
    private final String displayName;

    @Override
    public String toString() {
      return displayName;
    }
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  public enum Burst {
    PREFER("Prefer"),
    ALLOW("Allow"),
    NEVER("Never");
    private final String displayName;

    @Override
    public String toString() {
      return displayName;
    }
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  public enum Shard {
    NO_PREFERENCE("No preference"),
    ANCIENT_SHARD("Ancient shard"),
    CRYSTAL_SHARD("Crystal shard"),
    ANCIENT_AND_CRYSTAL_SHARD("Ancient shard + crystal shard");
    private final String displayName;

    @Override
    public String toString() {
      return displayName;
    }
  }
}
