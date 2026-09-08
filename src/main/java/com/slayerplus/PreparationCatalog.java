package com.slayerplus;

import java.util.*;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;

public final class PreparationCatalog {
  private static final int SPELLBOOK_VARBIT = VarbitID.SPELLBOOK;
  private static final int STANDARD_SPELLBOOK = 0;
  private static final int ANCIENT_SPELLBOOK = 1;
  private static final int LUNAR_SPELLBOOK = 2;
  private static final int ARCEUUS_SPELLBOOK = 3;
  private static final int ICE_BURST_LEVEL = 70;
  private static final int ICE_BARRAGE_LEVEL = 94;
  private static final int BOOK_OF_THE_DEAD = ItemID.BOOK_OF_THE_DEAD;
  private static final int AIR_RUNE = ItemID.AIRRUNE;
  private static final int WATER_RUNE = ItemID.WATERRUNE;
  private static final int EARTH_RUNE = ItemID.EARTHRUNE;
  private static final int MIND_RUNE = ItemID.MINDRUNE;
  private static final int FIRE_RUNE = ItemID.FIRERUNE;
  private static final int CHAOS_RUNE = ItemID.CHAOSRUNE;
  private static final int DEATH_RUNE = ItemID.DEATHRUNE;
  private static final int BLOOD_RUNE = ItemID.BLOODRUNE;
  private static final int COSMIC_RUNE = ItemID.COSMICRUNE;
  private static final int SOUL_RUNE = ItemID.SOULRUNE;
  private static final int WRATH_RUNE = ItemID.WRATHRUNE;
  private static final int MIST_RUNE = ItemID.MISTRUNE;
  private static final int DUST_RUNE = ItemID.DUSTRUNE;
  private static final int SMOKE_RUNE = ItemID.SMOKERUNE;
  private static final int MUD_RUNE = ItemID.MUDRUNE;
  private static final int STEAM_RUNE = ItemID.STEAMRUNE;
  private static final int LAVA_RUNE = ItemID.LAVARUNE;
  private static final int AETHER_RUNE = ItemID.AETHERRUNE;
  private static final int[] RUNE_TYPE_VARBITS = {
    VarbitID.RUNE_POUCH_TYPE_1,
    VarbitID.RUNE_POUCH_TYPE_2,
    VarbitID.RUNE_POUCH_TYPE_3,
    VarbitID.RUNE_POUCH_TYPE_4,
    VarbitID.RUNE_POUCH_TYPE_5,
    VarbitID.RUNE_POUCH_TYPE_6
  };
  private static final int[] RUNE_QUANTITY_VARBITS = {
    VarbitID.RUNE_POUCH_QUANTITY_1,
    VarbitID.RUNE_POUCH_QUANTITY_2,
    VarbitID.RUNE_POUCH_QUANTITY_3,
    VarbitID.RUNE_POUCH_QUANTITY_4,
    VarbitID.RUNE_POUCH_QUANTITY_5,
    VarbitID.RUNE_POUCH_QUANTITY_6
  };
  private static final int[] RUNE_POUCH_IDS = {
    ItemID.DIVINE_RUNE_POUCH,
    ItemID.DIVINE_RUNE_POUCH_TROUVER,
    ItemID.BH_RUNE_POUCH,
    ItemID.BH_RUNE_POUCH_TROUVER
  };
  private static final int[] DIVINE_RUNE_POUCH_IDS = {
    ItemID.DIVINE_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER
  };

  private PreparationCatalog() {}

  public static PreparationPlan resolve(
      String encounter,
      String location,
      TaskStrategy strategy,
      Client client,
      ItemContainer inventory,
      ItemContainer equipment,
      Map<Integer, Integer> bank) {
    return resolve(encounter, location, strategy, client, inventory, equipment, bank, -1);
  }

  static PreparationPlan resolve(
      String encounter,
      String location,
      TaskStrategy strategy,
      Client client,
      ItemContainer inventory,
      ItemContainer equipment,
      Map<Integer, Integer> bank,
      int weaponId) {
    if (strategy == null) {
      return PreparationPlan.none();
    }
    MethodRules rules = SlayerMethodRuleCatalog.resolve(encounter, location, strategy, weaponId);
    if (rules.getSpellbook() == MethodRules.Spellbook.ARCEUUS
        && (rules.usesThralls() || rules.usesDeathCharge())) {
      return resolveArceuus(rules, client, inventory, equipment, bank);
    }
    if (rules.getSpellbook() == MethodRules.Spellbook.ANCIENT) {
      return resolveAncients(rules, client, inventory, equipment, bank);
    }
    if (rules.requiresRunePouch()
        || !rules.getPouchRunes().isEmpty()
        || (rules.getSpellbook() != MethodRules.Spellbook.NONE
            && rules.getSpellbook() != MethodRules.Spellbook.STRATEGY_DEFINED)) {
      return resolveRuleRuneReferences(rules, client, inventory, equipment, bank);
    }
    return PreparationPlan.none();
  }

  public static PreparationPlan resolve(
      String assignment,
      TaskVariant variant,
      Recommendation recommendation,
      Client client,
      ItemContainer inventory,
      ItemContainer equipment,
      Map<Integer, Integer> bank) {
    if (!isStandardSmokeDevilMagicMethod(assignment, variant, recommendation)) {
      return PreparationPlan.none();
    }
    return resolveLegacySmokeDevils(client, inventory, equipment, bank);
  }

  private static PreparationPlan resolveArceuus(
      MethodRules rules,
      Client client,
      ItemContainer inventory,
      ItemContainer equipment,
      Map<Integer, Integer> bank) {
    int magicLevel = client == null ? 0 : client.getRealSkillLevel(Skill.MAGIC);
    boolean correctBook =
        client != null && client.getVarbitValue(SPELLBOOK_VARBIT) == ARCEUUS_SPELLBOOK;
    boolean levelReady = magicLevel >= rules.getMinimumMagicLevel();
    int carriedPouchId = firstCarriedPouch(inventory, equipment, false);
    int ownedPouchId = firstOwnedPouch(inventory, equipment, bank, false);
    int layoutPouchId = ownedPouchId > 0 ? ownedPouchId : ItemID.BH_RUNE_POUCH;
    int pouchCapacity = pouchCapacity(carriedPouchId > 0 ? carriedPouchId : layoutPouchId);
    boolean bookReady =
        !rules.requiresBookOfDead()
            || contains(inventory, BOOK_OF_THE_DEAD)
            || contains(equipment, BOOK_OF_THE_DEAD);
    boolean pouchReady = carriedPouchId > 0;
    Map<Integer, Integer> pouch = readPouchContents(client);
    Map<Integer, Integer> ownedItems = ownedItemQuantities(pouch, inventory, equipment, bank);
    Map<Integer, Integer> carriedRunes = mergeCarriedRuneContents(pouch, inventory, equipment);
    RunePolicy.Resolution resolvedRunePackage =
        RunePolicy.resolve(rules.getPouchRunes(), ownedItems);
    List<String> missing = new ArrayList<>();
    List<String> overflowRunes = new ArrayList<>();
    List<RuneStatus> runeStatuses = new ArrayList<>();
    List<RunePolicy.ResolvedRune> runes = resolvedRunePackage.getRunes();
    for (int i = 0; i < runes.size(); i++) {
      RunePolicy.ResolvedRune rune = runes.get(i);
      boolean inPouch = i < pouchCapacity;
      int available =
          inPouch
              ? pouch.getOrDefault(rune.getItemId(), 0)
              : carriedRunes.getOrDefault(rune.getItemId(), 0);
      runeStatuses.add(new RuneStatus(rune.getName(), rune.getMinimumQuantity(), available));
      if (inPouch) {
        if (available < rune.getMinimumQuantity()) {
          missing.add(rune.getName());
        }
      } else {
        overflowRunes.add(rune.getName());
        if (available < rune.getMinimumQuantity()) {
          missing.add(rune.getName());
        }
      }
    }
    addUnownedRequirementStatuses(rules.getPouchRunes(), runes, pouch, runeStatuses);
    boolean ready =
        correctBook
            && levelReady
            && pouchReady
            && bookReady
            && missing.isEmpty()
            && resolvedRunePackage.getUnownedRequirements().isEmpty();
    List<String> warnings = new ArrayList<>();
    if (!correctBook) {
      warnings.add("Switch to the Arceuus spellbook");
    }
    if (!levelReady) {
      warnings.add(rules.getMinimumMagicLevel() + " Magic is required");
    }
    if (!pouchReady) {
      warnings.add("Withdraw a rune pouch");
    }
    if (!bookReady) {
      warnings.add("Bring Book of the dead");
    }
    if (!missing.isEmpty()) {
      warnings.add("Bring or load " + joinNames(missing));
    }
    if (!resolvedRunePackage.getUnownedRequirements().isEmpty()) {
      warnings.add(
          "No owned rune available for " + joinNames(resolvedRunePackage.getUnownedRequirements()));
    }
    List<Integer> tagIds = new ArrayList<>();
    tagIds.add(layoutPouchId);
    if (rules.requiresBookOfDead()) {
      tagIds.add(BOOK_OF_THE_DEAD);
    }
    for (RunePolicy.ResolvedRune rune : runes) {
      tagIds.add(rune.getItemId());
    }
    String utility = safeSpellName(rules.getPrimarySpell());
    String headline = ready ? "Arceuus ready • " + utility : "Preparation required • " + utility;
    String detail =
        ready ? "Spellbook, book, and rune pouch are ready." : String.join(". ", warnings) + ".";
    List<String> pouchNames = new ArrayList<>();
    for (int i = 0; i < runes.size() && i < pouchCapacity; i++) {
      pouchNames.add(runes.get(i).getName());
    }
    String bankNote;
    if (pouchNames.isEmpty()) {
      bankNote = "No owned rune package can currently cast " + utility + ".";
    } else {
      bankNote =
          "Load "
              + joinNames(pouchNames)
              + " into the rune pouch"
              + (overflowRunes.isEmpty()
                  ? "."
                  : "; carry " + joinNames(overflowRunes) + " in the inventory.")
              + (resolvedRunePackage.getUnownedRequirements().isEmpty()
                  ? ""
                  : " No owned rune is available for "
                      + joinNames(resolvedRunePackage.getUnownedRequirements())
                      + ".");
    }
    String routeWarning = ready ? "" : "Before routing: " + String.join("; ", warnings) + ".";
    return new PreparationPlan(
        true,
        ready,
        headline,
        detail,
        bankNote,
        routeWarning,
        tagIds,
        MethodRules.Spellbook.ARCEUUS,
        correctBook,
        levelReady,
        magicLevel,
        runeStatuses,
        utility,
        0,
        missing.isEmpty());
  }

  private static PreparationPlan resolveAncients(
      MethodRules rules,
      Client client,
      ItemContainer inventory,
      ItemContainer equipment,
      Map<Integer, Integer> bank) {
    String spell = normalize(rules.getPrimarySpell());
    if (rules.getPouchRunes().isEmpty()
        && (spell.contains("ice barrage") || spell.contains("ice burst"))) {
      return resolveLegacySmokeDevils(client, inventory, equipment, bank);
    }
    return resolveRuleRuneReferences(rules, client, inventory, equipment, bank);
  }

  private static PreparationPlan resolveRuleRuneReferences(
      MethodRules rules,
      Client client,
      ItemContainer inventory,
      ItemContainer equipment,
      Map<Integer, Integer> bank) {
    String bookName = spellbookDisplayName(rules.getSpellbook());
    int expectedBook = spellbookVarbitValue(rules.getSpellbook());
    boolean correctBook =
        expectedBook < 0
            || (client != null && client.getVarbitValue(SPELLBOOK_VARBIT) == expectedBook);
    boolean pouchRequired = rules.requiresRunePouch() || !rules.getPouchRunes().isEmpty();
    int carriedPouchId = firstCarriedPouch(inventory, equipment, false);
    int ownedPouchId = firstOwnedPouch(inventory, equipment, bank, false);
    int capacity = pouchCapacity(carriedPouchId > 0 ? carriedPouchId : ownedPouchId);
    int referenceCapacity = capacity > 0 ? capacity : 3;
    boolean pouchReady = !pouchRequired || carriedPouchId > 0;
    Map<Integer, Integer> pouch = readPouchContents(client);
    Map<Integer, Integer> ownedItems = ownedItemQuantities(pouch, inventory, equipment, bank);
    List<Integer> tagIds = new ArrayList<>();
    Map<Integer, Integer> carriedRunes = mergeCarriedRuneContents(pouch, inventory, equipment);
    if (pouchRequired) {
      tagIds.add(ownedPouchId > 0 ? ownedPouchId : ItemID.BH_RUNE_POUCH);
    }
    List<String> runeNames = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    List<String> unownedRunes = new ArrayList<>();
    List<String> overflowRunes = new ArrayList<>();
    List<RuneStatus> runeStatuses = new ArrayList<>();
    if (!rules.getPouchRunes().isEmpty()) {
      RunePolicy.Resolution resolvedRunes = RunePolicy.resolve(rules.getPouchRunes(), ownedItems);
      unownedRunes.addAll(resolvedRunes.getUnownedRequirements());
      int runeIndex = 0;
      for (RunePolicy.ResolvedRune rune : resolvedRunes.getRunes()) {
        int available =
            (runeIndex < referenceCapacity ? pouch : carriedRunes)
                .getOrDefault(rune.getItemId(), 0);
        runeNames.add(rune.getName());
        runeStatuses.add(new RuneStatus(rune.getName(), rune.getMinimumQuantity(), available));
        if (runeIndex < referenceCapacity) {
          if (available < rune.getMinimumQuantity()) {
            missing.add(rune.getName());
          }
          tagIds.add(rune.getItemId());
        } else {
          overflowRunes.add(rune.getName());
          if (available < rune.getMinimumQuantity()) {
            missing.add(rune.getName());
          }
        }
        runeIndex++;
      }
      addUnownedRequirementStatuses(
          rules.getPouchRunes(), resolvedRunes.getRunes(), pouch, runeStatuses);
    } else {
      for (MethodRules.RequiredItem required : rules.getRequiredItems()) {
        if (required.getGroup() != MethodRules.InventoryGroup.RUNES) {
          continue;
        }
        int runeId = runeIdForName(required.getDisplayName());
        if (runeId <= 0) {
          continue;
        }
        String runeName = cleanRuneName(required.getDisplayName());
        int available = effectiveRuneQuantity(pouch, runeId);
        runeNames.add(runeName);
        runeStatuses.add(new RuneStatus(runeName, 1, available));
        if (runeNames.size() <= referenceCapacity) {
          if (available <= 0) {
            missing.add(runeName);
          }
          int ownedRuneId = preferredSatisfyingRuneId(ownedItems, runeId);
          if (ownedRuneId > 0) {
            tagIds.add(ownedRuneId);
          }
        } else {
          overflowRunes.add(runeName);
        }
      }
    }
    boolean explicitRunesReady = missing.isEmpty() && unownedRunes.isEmpty();
    boolean ready = correctBook && pouchReady && explicitRunesReady;
    List<String> warnings = new ArrayList<>();
    if (!correctBook) {
      warnings.add("Switch to " + bookName);
    }
    if (!pouchReady) {
      warnings.add("Withdraw a rune pouch");
    }
    if (!missing.isEmpty()) {
      warnings.add("Load " + joinNames(missing) + " into the rune pouch");
    }
    if (!unownedRunes.isEmpty()) {
      warnings.add("No owned rune available for " + joinNames(unownedRunes));
    }
    String spell = safeSpellName(rules.getPrimarySpell());
    String headline = bookName + " • " + spell;
    String detail;
    if (ready) {
      detail =
          runeNames.isEmpty()
              ? "Spellbook is ready; use the runes required by the selected spell."
              : "Spellbook and rune pouch are ready.";
    } else {
      detail = String.join(". ", warnings) + ".";
    }
    String bankNote;
    if (!runeNames.isEmpty()) {
      bankNote =
          "Load "
              + joinNames(runeNames)
              + " into the rune pouch"
              + (overflowRunes.isEmpty()
                  ? "."
                  : "; carry " + joinNames(overflowRunes) + " in the inventory.");
    } else if (pouchRequired) {
      bankNote = "Use the rune pouch for the runes required by " + spell + ".";
    } else {
      bankNote = "Use the runes required by " + spell + ".";
    }
    String routeWarning =
        ready || warnings.isEmpty() ? "" : "Before routing: " + String.join("; ", warnings) + ".";
    int magicLevel = client == null ? 0 : client.getRealSkillLevel(Skill.MAGIC);
    Map<Integer, Integer> carriedRuneContents =
        mergeCarriedRuneContents(pouch, inventory, equipment);
    int castsAvailable = calculateCastsAvailable(spell, carriedRuneContents);
    return new PreparationPlan(
        true,
        ready,
        headline,
        detail,
        bankNote,
        routeWarning,
        tagIds,
        rules.getSpellbook(),
        correctBook,
        true,
        magicLevel,
        runeStatuses,
        spell,
        castsAvailable,
        explicitRunesReady);
  }

  private static void addUnownedRequirementStatuses(
      List<MethodRules.RuneRequirement> requirements,
      List<RunePolicy.ResolvedRune> resolvedRunes,
      Map<Integer, Integer> pouch,
      List<RuneStatus> statuses) {
    if (requirements == null || statuses == null) {
      return;
    }
    for (MethodRules.RuneRequirement requirement : requirements) {
      boolean covered = false;
      if (resolvedRunes != null) {
        for (RunePolicy.ResolvedRune resolved : resolvedRunes) {
          if (RunePolicy.satisfies(resolved.getItemId(), requirement.getItemId())) {
            covered = true;
            break;
          }
        }
      }
      if (!covered) {
        statuses.add(
            new RuneStatus(
                requirement.getName(),
                requirement.getMinimumQuantity(),
                effectiveRuneQuantity(pouch, requirement.getItemId())));
      }
    }
  }

  private static int calculateCastsAvailable(String spellName, Map<Integer, Integer> carriedRunes) {
    String spell = normalize(spellName);
    if (spell.contains("blood barrage") && spell.contains("ice barrage")) {
      return Math.min(bloodBarrageCasts(carriedRunes), iceBarrageCasts(carriedRunes));
    }
    if (spell.contains("blood barrage")) {
      return bloodBarrageCasts(carriedRunes);
    }
    if (spell.contains("ice barrage")) {
      return iceBarrageCasts(carriedRunes);
    }
    if (spell.contains("ice burst")) {
      return iceBurstCasts(carriedRunes);
    }
    return 0;
  }

  private static int bloodBarrageCasts(Map<Integer, Integer> runes) {
    return castsFrom(runes, new int[][] {{SOUL_RUNE, 1}, {BLOOD_RUNE, 4}, {DEATH_RUNE, 4}});
  }

  private static int iceBarrageCasts(Map<Integer, Integer> runes) {
    return castsFrom(runes, new int[][] {{WATER_RUNE, 6}, {BLOOD_RUNE, 2}, {DEATH_RUNE, 4}});
  }

  private static int iceBurstCasts(Map<Integer, Integer> runes) {
    return castsFrom(runes, new int[][] {{WATER_RUNE, 4}, {CHAOS_RUNE, 4}, {DEATH_RUNE, 2}});
  }

  private static int castsFrom(Map<Integer, Integer> runes, int[][] costs) {
    if (runes == null || runes.isEmpty() || costs == null || costs.length == 0) {
      return 0;
    }
    int casts = Integer.MAX_VALUE;
    for (int[] cost : costs) {
      if (cost == null || cost.length < 2 || cost[1] <= 0) {
        continue;
      }
      casts = Math.min(casts, effectiveRuneQuantity(runes, cost[0]) / cost[1]);
    }
    return casts == Integer.MAX_VALUE ? 0 : Math.max(0, casts);
  }

  private static Map<Integer, Integer> mergeCarriedRuneContents(
      Map<Integer, Integer> pouch, ItemContainer inventory, ItemContainer equipment) {
    Map<Integer, Integer> contents = new LinkedHashMap<>();
    if (pouch != null) {
      contents.putAll(pouch);
    }
    mergeContainer(contents, inventory);
    mergeContainer(contents, equipment);
    return contents;
  }

  private static Map<Integer, Integer> ownedItemQuantities(
      Map<Integer, Integer> pouch,
      ItemContainer inventory,
      ItemContainer equipment,
      Map<Integer, Integer> bank) {
    Map<Integer, Integer> contents = new LinkedHashMap<>();
    if (bank != null) {
      for (Map.Entry<Integer, Integer> entry : bank.entrySet()) {
        if (entry.getKey() != null
            && entry.getKey() > 0
            && entry.getValue() != null
            && entry.getValue() > 0) {
          contents.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
      }
    }
    if (pouch != null) {
      for (Map.Entry<Integer, Integer> entry : pouch.entrySet()) {
        if (entry.getKey() != null
            && entry.getKey() > 0
            && entry.getValue() != null
            && entry.getValue() > 0) {
          contents.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
      }
    }
    mergeContainer(contents, inventory);
    mergeContainer(contents, equipment);
    return contents;
  }

  private static void mergeContainer(Map<Integer, Integer> contents, ItemContainer container) {
    if (contents == null || container == null) {
      return;
    }
    for (Item item : container.getItems()) {
      if (item == null || item.getId() <= 0 || item.getQuantity() <= 0) {
        continue;
      }
      contents.merge(item.getId(), item.getQuantity(), Integer::sum);
    }
  }

  private static int spellbookVarbitValue(MethodRules.Spellbook spellbook) {
    if (spellbook == MethodRules.Spellbook.STANDARD) {
      return STANDARD_SPELLBOOK;
    }
    if (spellbook == MethodRules.Spellbook.ANCIENT) {
      return ANCIENT_SPELLBOOK;
    }
    if (spellbook == MethodRules.Spellbook.LUNAR) {
      return LUNAR_SPELLBOOK;
    }
    if (spellbook == MethodRules.Spellbook.ARCEUUS) {
      return ARCEUUS_SPELLBOOK;
    }
    return -1;
  }

  private static String spellbookDisplayName(MethodRules.Spellbook spellbook) {
    if (spellbook == MethodRules.Spellbook.STANDARD) {
      return "Standard spellbook";
    }
    if (spellbook == MethodRules.Spellbook.ANCIENT) {
      return "Ancient Magicks";
    }
    if (spellbook == MethodRules.Spellbook.LUNAR) {
      return "Lunar spellbook";
    }
    if (spellbook == MethodRules.Spellbook.ARCEUUS) {
      return "Arceuus spellbook";
    }
    return "Spellbook";
  }

  private static String safeSpellName(String value) {
    return value == null || value.trim().isEmpty() ? "Selected spell" : value.trim();
  }

  private static int pouchCapacity(int pouchItemId) {
    for (int divineId : DIVINE_RUNE_POUCH_IDS) {
      if (pouchItemId == divineId) {
        return 4;
      }
    }
    return pouchItemId > 0 ? 3 : 0;
  }

  private static int runeIdForName(String value) {
    String name = normalize(value);
    if (name.contains("air rune")) {
      return AIR_RUNE;
    }
    if (name.contains("water rune")) {
      return WATER_RUNE;
    }
    if (name.contains("earth rune")) {
      return EARTH_RUNE;
    }
    if (name.contains("fire rune")) {
      return FIRE_RUNE;
    }
    if (name.contains("mind rune")) {
      return MIND_RUNE;
    }
    if (name.contains("chaos rune")) {
      return CHAOS_RUNE;
    }
    if (name.contains("death rune")) {
      return DEATH_RUNE;
    }
    if (name.contains("blood rune")) {
      return BLOOD_RUNE;
    }
    if (name.contains("cosmic rune")) {
      return COSMIC_RUNE;
    }
    if (name.contains("soul rune")) {
      return SOUL_RUNE;
    }
    if (name.contains("wrath rune")) {
      return WRATH_RUNE;
    }
    if (name.contains("aether rune")) {
      return AETHER_RUNE;
    }
    return -1;
  }

  private static int effectiveRuneQuantity(Map<Integer, Integer> contents, int requiredRuneId) {
    if (contents == null || contents.isEmpty()) {
      return 0;
    }
    int quantity = contents.getOrDefault(requiredRuneId, 0);
    for (int candidateId : combinationRuneIdsFor(requiredRuneId)) {
      quantity += contents.getOrDefault(candidateId, 0);
    }
    return quantity;
  }

  private static int preferredSatisfyingRuneId(Map<Integer, Integer> contents, int requiredRuneId) {
    if (contents != null && contents.getOrDefault(requiredRuneId, 0) > 0) {
      return requiredRuneId;
    }
    int bestId = -1;
    int bestQuantity = 0;
    if (contents != null) {
      for (int candidateId : combinationRuneIdsFor(requiredRuneId)) {
        int quantity = contents.getOrDefault(candidateId, 0);
        if (quantity > bestQuantity) {
          bestId = candidateId;
          bestQuantity = quantity;
        }
      }
    }
    return bestId;
  }

  private static int[] combinationRuneIdsFor(int requiredRuneId) {
    switch (requiredRuneId) {
      case AIR_RUNE:
        return new int[] {MIST_RUNE, DUST_RUNE, SMOKE_RUNE};
      case WATER_RUNE:
        return new int[] {MIST_RUNE, MUD_RUNE, STEAM_RUNE};
      case EARTH_RUNE:
        return new int[] {DUST_RUNE, MUD_RUNE, LAVA_RUNE};
      case FIRE_RUNE:
        return new int[] {SMOKE_RUNE, STEAM_RUNE, LAVA_RUNE};
      case COSMIC_RUNE:
      case SOUL_RUNE:
        return new int[] {AETHER_RUNE};
      default:
        return new int[0];
    }
  }

  private static String cleanRuneName(String value) {
    String normalized = normalize(value);
    if (normalized.endsWith(" runes")) {
      return normalized.substring(0, normalized.length() - 6);
    }
    if (normalized.endsWith(" rune")) {
      return normalized.substring(0, normalized.length() - 5);
    }
    return normalized;
  }

  private static PreparationPlan resolveLegacySmokeDevils(
      Client client, ItemContainer inventory, ItemContainer equipment, Map<Integer, Integer> bank) {
    int magicLevel = client == null ? 0 : client.getRealSkillLevel(Skill.MAGIC);
    boolean barrage = magicLevel >= ICE_BARRAGE_LEVEL;
    String spellName = barrage ? "Ice Barrage" : "Ice Burst";
    List<RuneRequirement> runes = barrage ? barrageRunes() : burstRunes();
    int carriedPouchId = firstCarriedPouch(inventory, equipment, false);
    int ownedPouchId = firstOwnedPouch(inventory, equipment, bank, false);
    int layoutPouchId = ownedPouchId > 0 ? ownedPouchId : ItemID.BH_RUNE_POUCH;
    Map<Integer, Integer> pouch = readPouchContents(client);
    Map<Integer, Integer> ownedItems = ownedItemQuantities(pouch, inventory, equipment, bank);
    List<String> missing = new ArrayList<>();
    for (RuneRequirement rune : runes) {
      if (effectiveRuneQuantity(pouch, rune.itemId) < rune.perCast) {
        missing.add(rune.name);
      }
    }
    boolean ancientBook =
        client != null && client.getVarbitValue(SPELLBOOK_VARBIT) == ANCIENT_SPELLBOOK;
    boolean levelReady = magicLevel >= ICE_BURST_LEVEL;
    boolean pouchOwned = carriedPouchId > 0;
    boolean pouchLoaded = missing.isEmpty();
    boolean ready = ancientBook && levelReady && pouchOwned && pouchLoaded;
    List<String> warnings = new ArrayList<>();
    if (!ancientBook) {
      warnings.add("Switch to Ancient Magicks");
    }
    if (!levelReady) {
      warnings.add("70 Magic is required for Ice Burst");
    }
    if (!pouchOwned) {
      warnings.add("Withdraw a rune pouch");
    }
    if (!missing.isEmpty()) {
      warnings.add("Load " + joinNames(missing) + " into the rune pouch");
    }
    List<Integer> tagIds = new ArrayList<>();
    tagIds.add(layoutPouchId);
    for (RuneRequirement rune : runes) {
      int ownedRuneId = preferredSatisfyingRuneId(ownedItems, rune.itemId);
      if (ownedRuneId > 0) {
        tagIds.add(ownedRuneId);
      }
    }
    String runeNames = joinRuneNames(runes);
    Map<Integer, Integer> carriedRuneContents =
        mergeCarriedRuneContents(pouch, inventory, equipment);
    int castsAvailable = calculateCastsAvailable(spellName, carriedRuneContents);
    List<RuneStatus> runeStatuses = new ArrayList<>();
    for (RuneRequirement rune : runes) {
      runeStatuses.add(
          new RuneStatus(rune.name, rune.perCast, effectiveRuneQuantity(pouch, rune.itemId)));
    }
    return new PreparationPlan(
        true,
        ready,
        ready ? "Ancient Magicks ready • " + spellName : "Preparation required • " + spellName,
        ready ? "Rune pouch contains " + runeNames + "." : String.join(". ", warnings) + ".",
        "Load " + runeNames + " into the rune pouch before leaving.",
        ready ? "" : "Before routing: " + String.join("; ", warnings) + ".",
        tagIds,
        MethodRules.Spellbook.ANCIENT,
        ancientBook,
        levelReady,
        magicLevel,
        runeStatuses,
        spellName,
        castsAvailable,
        pouchLoaded);
  }

  private static boolean isStandardSmokeDevilMagicMethod(
      String assignment, TaskVariant variant, Recommendation recommendation) {
    if (!normalize(assignment).equals("smoke devils")
        || (variant != null && variant.isBoss())
        || recommendation == null) {
      return false;
    }
    String method = normalize(recommendation.getMethod());
    return method.contains("barrage")
        || method.contains("burst")
        || method.contains("ancient magicks");
  }

  private static List<RuneRequirement> barrageRunes() {
    List<RuneRequirement> runes = new ArrayList<>();
    runes.add(new RuneRequirement(WATER_RUNE, "Water", 6));
    runes.add(new RuneRequirement(BLOOD_RUNE, "Blood", 2));
    runes.add(new RuneRequirement(DEATH_RUNE, "Death", 4));
    return Collections.unmodifiableList(runes);
  }

  private static List<RuneRequirement> burstRunes() {
    List<RuneRequirement> runes = new ArrayList<>();
    runes.add(new RuneRequirement(WATER_RUNE, "Water", 4));
    runes.add(new RuneRequirement(CHAOS_RUNE, "Chaos", 4));
    runes.add(new RuneRequirement(DEATH_RUNE, "Death", 2));
    return Collections.unmodifiableList(runes);
  }

  private static Map<Integer, Integer> readPouchContents(Client client) {
    if (client == null) {
      return Collections.emptyMap();
    }
    EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
    if (runeEnum == null) {
      return Collections.emptyMap();
    }
    Map<Integer, Integer> contents = new LinkedHashMap<>();
    for (int slot = 0; slot < RUNE_TYPE_VARBITS.length; slot++) {
      int pouchRuneType = client.getVarbitValue(RUNE_TYPE_VARBITS[slot]);
      int quantity = client.getVarbitValue(RUNE_QUANTITY_VARBITS[slot]);
      if (pouchRuneType <= 0 || quantity <= 0) {
        continue;
      }
      int runeItemId = runeEnum.getIntValue(pouchRuneType);
      if (runeItemId > 0) {
        contents.merge(runeItemId, quantity, Integer::sum);
      }
    }
    return contents;
  }

  private static int firstCarriedPouch(
      ItemContainer inventory, ItemContainer equipment, boolean divineOnly) {
    int[] candidates = divineOnly ? DIVINE_RUNE_POUCH_IDS : RUNE_POUCH_IDS;
    for (int pouchId : candidates) {
      if (contains(inventory, pouchId) || contains(equipment, pouchId)) {
        return pouchId;
      }
    }
    return -1;
  }

  private static int firstOwnedPouch(
      ItemContainer inventory,
      ItemContainer equipment,
      Map<Integer, Integer> bank,
      boolean divineOnly) {
    int[] candidates = divineOnly ? DIVINE_RUNE_POUCH_IDS : RUNE_POUCH_IDS;
    for (int pouchId : candidates) {
      if (contains(inventory, pouchId)
          || contains(equipment, pouchId)
          || bank != null && bank.getOrDefault(pouchId, 0) > 0) {
        return pouchId;
      }
    }
    return -1;
  }

  private static boolean contains(ItemContainer container, int itemId) {
    if (container == null) {
      return false;
    }
    for (Item item : container.getItems()) {
      if (item != null && item.getId() == itemId && item.getQuantity() > 0) {
        return true;
      }
    }
    return false;
  }

  private static String joinRuneNames(List<RuneRequirement> runes) {
    List<String> names = new ArrayList<>();
    for (RuneRequirement rune : runes) {
      names.add(rune.name);
    }
    return joinNames(names);
  }

  private static String joinNames(List<String> values) {
    if (values == null || values.isEmpty()) {
      return "";
    }
    if (values.size() == 1) {
      return values.get(0);
    }
    if (values.size() == 2) {
      return values.get(0) + " and " + values.get(1);
    }
    return String.join(", ", values.subList(0, values.size() - 1))
        + ", and "
        + values.get(values.size() - 1);
  }

  private static String normalize(String value) {
    return SlayerText.encounter(value);
  }

  private static final class RuneRequirement {
    private final int itemId;
    private final String name;
    private final int perCast;

    private RuneRequirement(int itemId, String name, int perCast) {
      this.itemId = itemId;
      this.name = name;
      this.perCast = Math.max(1, perCast);
    }
  }

  public static final class RuneStatus {
    @Getter private final String name;
    @Getter private final int required;
    @Getter private final int available;

    public RuneStatus(String name, int required, int available) {
      this.name = name == null ? "" : name.trim();
      this.required = Math.max(0, required);
      this.available = Math.max(0, available);
    }

    public boolean isReady() {
      return available >= required;
    }
  }

  @Getter
  public static final class PreparationPlan {
    private static final PreparationPlan NONE =
        new PreparationPlan(
            false, true, "", "", "", "", Collections.emptyList(), MethodRules.Spellbook.NONE);
    private final boolean active;
    private final boolean ready;
    private final String headline;
    private final String detail;
    private final String bankNote;
    private final String routeWarning;
    private final List<Integer> tagIds;
    private final MethodRules.Spellbook book;
    private final boolean spellbookReady;
    private final boolean levelReady;
    private final int magicLevel;
    private final List<RuneStatus> runeStatuses;
    private final String spellName;
    private final int castsAvailable;
    private final boolean runesReady;

    private PreparationPlan(
        boolean active,
        boolean ready,
        String headline,
        String detail,
        String bankNote,
        String routeWarning,
        List<Integer> tagIds,
        MethodRules.Spellbook book) {
      this(
          active,
          ready,
          headline,
          detail,
          bankNote,
          routeWarning,
          tagIds,
          book,
          ready,
          ready,
          0,
          Collections.emptyList(),
          headline,
          0,
          ready);
    }

    PreparationPlan(
        boolean active,
        boolean ready,
        String headline,
        String detail,
        String bankNote,
        String routeWarning,
        List<Integer> tagIds,
        MethodRules.Spellbook book,
        boolean spellbookReady,
        boolean levelReady,
        int magicLevel,
        List<RuneStatus> runeStatuses,
        String spellName,
        int castsAvailable,
        boolean runesReady) {
      this.active = active;
      this.ready = ready;
      this.headline = safe(headline);
      this.detail = safe(detail);
      this.bankNote = safe(bankNote);
      this.routeWarning = safe(routeWarning);
      this.tagIds = Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(tagIds)));
      this.book = book == null ? MethodRules.Spellbook.NONE : book;
      this.spellbookReady = spellbookReady;
      this.levelReady = levelReady;
      this.magicLevel = Math.max(0, magicLevel);
      this.runeStatuses =
          Collections.unmodifiableList(
              new ArrayList<>(runeStatuses == null ? Collections.emptyList() : runeStatuses));
      this.spellName = safe(spellName);
      this.castsAvailable = Math.max(0, castsAvailable);
      this.runesReady = runesReady;
    }

    public static PreparationPlan none() {
      return NONE;
    }

    public String spellbookName() {
      if (book == MethodRules.Spellbook.ANCIENT) {
        return "Ancient Magicks";
      }
      if (book == MethodRules.Spellbook.ARCEUUS) {
        return "Arceuus spellbook";
      }
      if (book == MethodRules.Spellbook.LUNAR) {
        return "Lunar spellbook";
      }
      if (book == MethodRules.Spellbook.STANDARD) {
        return "Standard spellbook";
      }
      return active ? "Spellbook" : "";
    }

    public String getEncounterName() {
      return "";
    }

    public int getRequiredMagicLevel() {
      String normalized = spellName.toLowerCase(Locale.ENGLISH);
      if (normalized.contains("ice barrage")) {
        return ICE_BARRAGE_LEVEL;
      }
      if (normalized.contains("ice burst")) {
        return ICE_BURST_LEVEL;
      }
      return 0;
    }

    private static String safe(String value) {
      return value == null ? "" : value.trim();
    }
  }
}
