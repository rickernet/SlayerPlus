package com.slayerplus;

import static com.slayerplus.SlayerText.normalize;

import java.util.*;

class SlayerTargetFootprintCatalog {
  private static final Set<String> MULTI_TILE_SCYTHE_TARGETS = loadMultiTileScytheTargets();

  private SlayerTargetFootprintCatalog() {}

  static boolean allowsWeapon(String assignment, String weapon) {
    String key = normalize(weapon);
    if (!key.contains("scythe of vitur")) {
      return true;
    }
    return MULTI_TILE_SCYTHE_TARGETS.contains(normalize(assignment));
  }

  static boolean isReviewedMultiTileScytheTarget(String assignment) {
    return MULTI_TILE_SCYTHE_TARGETS.contains(normalize(assignment));
  }

  private static Set<String> loadMultiTileScytheTargets() {
    Set<String> targets = new HashSet<>();
    for (String[] row : ResourceTable.rows("slayer-multitile-targets.tsv", 1)) {
      targets.add(normalize(row[0]));
    }
    return Collections.unmodifiableSet(targets);
  }
}
