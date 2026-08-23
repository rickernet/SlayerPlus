package com.slayerplus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MethodRules {
  public static final int MAX_TASK_METHOD_LENGTH = 220;

  public enum DamageControl {
    STRATEGY_DEFINED,
    DIRECT_DAMAGE,
    PARTIAL_MITIGATION,
    PRAYER_PROTECTED,
    SAFESPOT,
    FREEZE_SAFESPOT,
    STEP_UNDER,
    BOSS_MECHANICS
  }

  public enum DosePolicy {
    FULLEST_OWNED
  }

  public enum LayoutProfile {
    STANDARD,
    PRAYER_TASK,
    BARRAGE,
    FREEZE_MAGIC,
    CANNON,
    BOSS,
    HYBRID_BOSS,
    WILDERNESS
  }

  public enum Spellbook {
    STRATEGY_DEFINED,
    NONE,
    STANDARD,
    ANCIENT,
    ARCEUUS,
    LUNAR
  }

  public enum UtilityRecommendation {
    NONE,
    OPTIONAL,
    RECOMMENDED
  }

  public enum InventoryGroup {
    TRAVEL,
    UTILITY,
    SWITCH,
    RUNES_AMMO,
    BOOST,
    PROTECTION,
    RESTORE,
    FOOD,
    OTHER
  }

  public static final class RequiredItem {
    private final String displayName;
    private final int quantity;
    private final int slotCount;
    private final InventoryGroup group;
    private final boolean ownedOnly;
    private final List<String> alternatives;

    private RequiredItem(
        String displayName,
        int quantity,
        int slotCount,
        InventoryGroup group,
        boolean ownedOnly,
        String... alternatives) {
      this.displayName = clean(displayName);
      this.quantity = Math.max(1, quantity);
      this.slotCount = Math.max(1, slotCount);
      this.group = group == null ? InventoryGroup.OTHER : group;
      this.ownedOnly = ownedOnly;
      List<String> values = new ArrayList<>();
      if (alternatives != null) {
        for (String alternative : alternatives) {
          String value = clean(alternative);
          if (!value.isEmpty()) {
            values.add(value);
          }
        }
      }
      this.alternatives = Collections.unmodifiableList(values);
    }

    public String getDisplayName() {
      return displayName;
    }

    public int getQuantity() {
      return quantity;
    }

    public int getSlotCount() {
      return slotCount;
    }

    public InventoryGroup getGroup() {
      return group;
    }

    public boolean isOwnedOnly() {
      return ownedOnly;
    }

    public List<String> getAlternatives() {
      return alternatives;
    }
  }

  public static final class PouchRuneRequirement {
    private final int itemId;
    private final String name;
    private final int minimumQuantity;

    private PouchRuneRequirement(int itemId, String name, int minimumQuantity) {
      this.itemId = itemId;
      this.name = clean(name);
      this.minimumQuantity = Math.max(1, minimumQuantity);
    }

    public int getItemId() {
      return itemId;
    }

    public String getName() {
      return name;
    }

    public int getMinimumQuantity() {
      return minimumQuantity;
    }
  }

  private final String coverageKey;
  private final String taskMethod;
  private final String researchSource;
  private final String reviewDate;
  private final boolean reviewed;
  private final TaskStrategy.CombatStyle combatStyle;
  private final Spellbook spellbook;
  private final String primarySpell;
  private final UtilityRecommendation thrallRecommendation;
  private final UtilityRecommendation deathChargeRecommendation;
  private final boolean wardOfArceuus;
  private final boolean requireBookOfDead;
  private final boolean requireRunePouch;
  private final boolean requireDivineRunePouch;
  private final int minimumMagicLevel;
  private final List<PouchRuneRequirement> pouchRunes;
  private final boolean cannonMethod;
  private final int cannonballQuantity;
  private final DamageControl damageControl;
  private final DosePolicy dosePolicy;
  private final LayoutProfile layoutProfile;
  private final String primaryRestoreFamily;
  private final String fallbackRestoreFamily;
  private final boolean allowRestoreFallback;
  private final int restoreSlotsOverride;
  private final int foodSlotsOverride;
  private final String foodDisplayName;
  private final List<String> foodAlternatives;
  private final boolean fillRemainingWithRestore;
  private final boolean fillRemainingWithFood;
  private final boolean includeStyleBoost;
  private final boolean includeRunePouchForMagic;
  private final int inventoryTarget;
  private final int reservedLootSlots;
  private final List<RequiredItem> requiredItems;

  private MethodRules(Builder builder) {
    coverageKey = clean(builder.coverageKey);
    taskMethod = clean(builder.taskMethod);
    researchSource = clean(builder.researchSource);
    reviewDate = clean(builder.reviewDate);
    reviewed = builder.reviewed;
    combatStyle = builder.combatStyle;
    spellbook = builder.spellbook;
    primarySpell = clean(builder.primarySpell);
    thrallRecommendation = builder.thrallRecommendation;
    deathChargeRecommendation = builder.deathChargeRecommendation;
    wardOfArceuus = builder.wardOfArceuus;
    requireBookOfDead = builder.requireBookOfDead;
    requireRunePouch = builder.requireRunePouch;
    requireDivineRunePouch = builder.requireDivineRunePouch;
    minimumMagicLevel = Math.max(0, builder.minimumMagicLevel);
    pouchRunes = Collections.unmodifiableList(new ArrayList<>(builder.pouchRunes));
    cannonMethod = builder.cannonMethod;
    cannonballQuantity = Math.max(0, builder.cannonballQuantity);
    damageControl = builder.damageControl;
    dosePolicy = builder.dosePolicy;
    layoutProfile = builder.layoutProfile;
    primaryRestoreFamily = clean(builder.primaryRestoreFamily);
    fallbackRestoreFamily = clean(builder.fallbackRestoreFamily);
    allowRestoreFallback = builder.allowRestoreFallback;
    restoreSlotsOverride = builder.restoreSlotsOverride;
    foodSlotsOverride = builder.foodSlotsOverride;
    foodDisplayName = clean(builder.foodDisplayName);
    foodAlternatives = Collections.unmodifiableList(new ArrayList<>(builder.foodAlternatives));
    fillRemainingWithRestore = builder.fillRemainingWithRestore;
    fillRemainingWithFood = builder.fillRemainingWithFood;
    includeStyleBoost = builder.includeStyleBoost;
    includeRunePouchForMagic = builder.includeRunePouchForMagic;
    inventoryTarget = clamp(builder.inventoryTarget, 0, 28);
    reservedLootSlots = clamp(builder.reservedLootSlots, 0, 28);
    requiredItems = Collections.unmodifiableList(new ArrayList<>(builder.requiredItems));
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getCoverageKey() {
    return coverageKey;
  }

  public String getTaskMethod() {
    return taskMethod;
  }

  public String getResearchSource() {
    return researchSource;
  }

  public String getReviewDate() {
    return reviewDate;
  }

  public boolean isReviewed() {
    return reviewed;
  }

  public TaskStrategy.CombatStyle getCombatStyle() {
    return combatStyle;
  }

  public Spellbook getSpellbook() {
    return spellbook;
  }

  public String getPrimarySpell() {
    return primarySpell;
  }

  public UtilityRecommendation getThrallRecommendation() {
    return thrallRecommendation;
  }

  public UtilityRecommendation getDeathChargeRecommendation() {
    return deathChargeRecommendation;
  }

  public boolean usesThralls() {
    return thrallRecommendation == UtilityRecommendation.RECOMMENDED;
  }

  public boolean usesDeathCharge() {
    return deathChargeRecommendation == UtilityRecommendation.RECOMMENDED;
  }

  public boolean usesWardOfArceuus() {
    return wardOfArceuus;
  }

  public boolean requiresBookOfDead() {
    return requireBookOfDead;
  }

  public boolean requiresRunePouch() {
    return requireRunePouch;
  }

  public boolean requiresDivineRunePouch() {
    return requireDivineRunePouch;
  }

  public int getMinimumMagicLevel() {
    return minimumMagicLevel;
  }

  public List<PouchRuneRequirement> getPouchRunes() {
    return pouchRunes;
  }

  public boolean usesCannon() {
    return cannonMethod;
  }

  public int getCannonballQuantity() {
    return cannonballQuantity;
  }

  public DamageControl getDamageControl() {
    return damageControl;
  }

  public DosePolicy getDosePolicy() {
    return dosePolicy;
  }

  public LayoutProfile getLayoutProfile() {
    return layoutProfile;
  }

  public String getPrimaryRestoreFamily() {
    return primaryRestoreFamily;
  }

  public String getFallbackRestoreFamily() {
    return fallbackRestoreFamily;
  }

  public boolean allowsRestoreFallback() {
    return allowRestoreFallback;
  }

  public boolean hasRestorePolicy() {
    return !primaryRestoreFamily.isEmpty();
  }

  public String getFoodDisplayName() {
    return foodDisplayName;
  }

  public List<String> getFoodAlternatives() {
    return foodAlternatives;
  }

  public boolean fillsRemainingWithRestore() {
    return fillRemainingWithRestore;
  }

  public boolean fillsRemainingWithFood() {
    return fillRemainingWithFood;
  }

  public boolean includesStyleBoost() {
    return includeStyleBoost;
  }

  public boolean includesRunePouchForMagic() {
    return includeRunePouchForMagic;
  }

  public int getInventoryTarget() {
    return inventoryTarget;
  }

  public int getReservedLootSlots() {
    return reservedLootSlots;
  }

  public List<RequiredItem> getRequiredItems() {
    return requiredItems;
  }

  public boolean preventsExpectedDamageWhenExecuted() {
    return damageControl == DamageControl.PRAYER_PROTECTED
        || damageControl == DamageControl.SAFESPOT
        || damageControl == DamageControl.FREEZE_SAFESPOT;
  }

  public int resolveRestoreSlots(TaskStrategy strategy) {
    if (restoreSlotsOverride >= 0) {
      return restoreSlotsOverride;
    }
    return strategy == null ? 0 : Math.max(0, strategy.getPrayerPotionSlots());
  }

  public int resolveFoodSlots(TaskStrategy strategy, int strategyFoodSlots) {
    if (foodSlotsOverride >= 0) {
      return foodSlotsOverride;
    }
    if (preventsExpectedDamageWhenExecuted()) {
      return 0;
    }
    return Math.max(0, strategyFoodSlots);
  }

  public void validateFor(String encounterName, TaskStrategy strategy) {
    String encounter =
        clean(encounterName).isEmpty() ? "Unknown Slayer encounter" : clean(encounterName);
    if (!reviewed) {
      throw new IllegalStateException("Unreviewed Slayer method rules: " + encounter);
    }
    if (coverageKey.isEmpty()) {
      throw new IllegalStateException("Slayer method has no coverage key: " + encounter);
    }
    if (combatStyle == null) {
      throw new IllegalStateException("Slayer method has no combat style: " + encounter);
    }
    if (taskMethod.isEmpty()) {
      throw new IllegalStateException("Slayer method has no Task Method text: " + encounter);
    }
    if (taskMethod.length() > MAX_TASK_METHOD_LENGTH) {
      throw new IllegalStateException(
          "Task Method is too long for " + encounter + ": " + taskMethod.length() + " characters");
    }
    if (researchSource.isEmpty()) {
      throw new IllegalStateException("Slayer method has no research source: " + encounter);
    }
    if (reviewDate.isEmpty()) {
      throw new IllegalStateException("Slayer method has no review date: " + encounter);
    }
    if (fillRemainingWithFood && fillRemainingWithRestore) {
      throw new IllegalStateException("Conflicting inventory-fill rules: " + encounter);
    }
    if (spellbook == Spellbook.ANCIENT && primarySpell.isEmpty()) {
      throw new IllegalStateException("Ancient spell method has no spell name: " + encounter);
    }
    if (!pouchRunes.isEmpty()
        && (spellbook == Spellbook.STRATEGY_DEFINED || spellbook == Spellbook.NONE)) {
      throw new IllegalStateException("Rune-pouch method has no selected spellbook: " + encounter);
    }
    if ((usesThralls() || usesDeathCharge() || usesWardOfArceuus())
        && spellbook != Spellbook.ARCEUUS) {
      throw new IllegalStateException(
          "Arceuus utility method is not on Arceuus spellbook: " + encounter);
    }
    if (usesThralls() && !requireBookOfDead) {
      throw new IllegalStateException(
          "Thrall method does not require Book of the dead: " + encounter);
    }
    if ((usesThralls() || usesDeathCharge() || usesWardOfArceuus()) && !requireRunePouch) {
      throw new IllegalStateException(
          "Arceuus utility method does not require a rune pouch: " + encounter);
    }
    if (usesThralls() && minimumMagicLevel < 76) {
      throw new IllegalStateException(
          "Greater Thrall method has an invalid Magic requirement: " + encounter);
    }
    if (usesDeathCharge() && minimumMagicLevel < 80) {
      throw new IllegalStateException(
          "Death Charge method has an invalid Magic requirement: " + encounter);
    }
    if (usesWardOfArceuus() && (spellbook != Spellbook.ARCEUUS || minimumMagicLevel < 73)) {
      throw new IllegalStateException(
          "Ward of Arceuus method has an invalid spellbook/Magic requirement: " + encounter);
    }
    if ((usesThralls() || usesDeathCharge() || usesWardOfArceuus()) && pouchRunes.isEmpty()) {
      throw new IllegalStateException(
          "Arceuus utility method has no rune-pouch package: " + encounter);
    }
    if (cannonMethod && cannonballQuantity <= 0) {
      throw new IllegalStateException("Cannon method has no cannonball quantity: " + encounter);
    }
    if ((resolveRestoreSlots(strategy) > 0 || fillRemainingWithRestore)
        && primaryRestoreFamily.isEmpty()) {
      throw new IllegalStateException("Restore slots have no potion family: " + encounter);
    }
    if (allowRestoreFallback && fallbackRestoreFamily.isEmpty()) {
      throw new IllegalStateException(
          "Restore fallback is enabled without a fallback family: " + encounter);
    }
    if ((resolveFoodSlots(strategy, strategy == null ? 0 : strategy.getFoodSlots()) > 0
            || fillRemainingWithFood)
        && (foodDisplayName.isEmpty() || foodAlternatives.isEmpty())) {
      throw new IllegalStateException("Food slots have no researched food family: " + encounter);
    }
    int requiredSlotCount = 0;
    for (RequiredItem requiredItem : requiredItems) {
      if (requiredItem.getDisplayName().isEmpty()) {
        throw new IllegalStateException("Blank required item in Slayer rules: " + encounter);
      }
      if (requiredItem.getAlternatives().isEmpty()) {
        throw new IllegalStateException(
            "Required Slayer inventory item has no resolvable item family: "
                + encounter
                + " -> "
                + requiredItem.getDisplayName());
      }
      requiredSlotCount += requiredItem.getSlotCount();
    }
    if (requiredSlotCount > 28) {
      throw new IllegalStateException(
          "Required Slayer utility items exceed the 4x7 inventory: "
              + encounter
              + " -> "
              + requiredSlotCount
              + " slots");
    }
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private static int clamp(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  public static final class Builder {
    private String coverageKey;
    private String taskMethod;
    private String researchSource;
    private String reviewDate;
    private boolean reviewed;
    private TaskStrategy.CombatStyle combatStyle;
    private Spellbook spellbook = Spellbook.STRATEGY_DEFINED;
    private String primarySpell;
    private UtilityRecommendation thrallRecommendation = UtilityRecommendation.NONE;
    private UtilityRecommendation deathChargeRecommendation = UtilityRecommendation.NONE;
    private boolean wardOfArceuus;
    private boolean requireBookOfDead;
    private boolean requireRunePouch;
    private boolean requireDivineRunePouch;
    private int minimumMagicLevel;
    private final List<PouchRuneRequirement> pouchRunes = new ArrayList<>();
    private boolean cannonMethod;
    private int cannonballQuantity;
    private DamageControl damageControl = DamageControl.STRATEGY_DEFINED;
    private DosePolicy dosePolicy = DosePolicy.FULLEST_OWNED;
    private LayoutProfile layoutProfile = LayoutProfile.STANDARD;
    private String primaryRestoreFamily;
    private String fallbackRestoreFamily;
    private boolean allowRestoreFallback;
    private int restoreSlotsOverride = -1;
    private int foodSlotsOverride = -1;
    private String foodDisplayName = "High-healing food";
    private final List<String> foodAlternatives =
        new ArrayList<>(
            Arrays.asList(
                "anglerfish",
                "manta ray",
                "dark crab",
                "moonlight antelope",
                "shark",
                "sea turtle",
                "cooked karambwan",
                "monkfish"));
    private boolean fillRemainingWithRestore;
    private boolean fillRemainingWithFood;
    private boolean includeStyleBoost = true;
    private boolean includeRunePouchForMagic = false;
    private int inventoryTarget;
    private int reservedLootSlots;
    private final List<RequiredItem> requiredItems = new ArrayList<>();

    private Builder() {}

    public Builder coverageKey(String value) {
      coverageKey = value;
      return this;
    }

    public Builder taskMethod(String value) {
      taskMethod = value;
      return this;
    }

    public Builder appendTaskMethod(String value) {
      String suffix = clean(value);
      if (suffix.isEmpty()) {
        return this;
      }
      String base = clean(taskMethod);
      if (base.isEmpty()) {
        taskMethod = suffix;
        return this;
      }
      if (!(base.endsWith(".") || base.endsWith("!") || base.endsWith("?"))) {
        base += ".";
      }
      String combined = base + " " + suffix;
      if (combined.length() <= MAX_TASK_METHOD_LENGTH) {
        taskMethod = combined;
        return this;
      }
      int maximumBase = MAX_TASK_METHOD_LENGTH - suffix.length() - 1;
      if (maximumBase <= 0) {
        taskMethod = suffix.substring(0, Math.min(MAX_TASK_METHOD_LENGTH, suffix.length())).trim();
        return this;
      }
      int cut = base.lastIndexOf(' ', maximumBase);
      if (cut < 40) {
        cut = Math.min(maximumBase, base.length());
      }
      base = base.substring(0, cut).trim();
      if (!(base.endsWith(".") || base.endsWith("!") || base.endsWith("?"))) {
        base += ".";
      }
      taskMethod = (base + " " + suffix).trim();
      if (taskMethod.length() > MAX_TASK_METHOD_LENGTH) {
        taskMethod = taskMethod.substring(0, MAX_TASK_METHOD_LENGTH).trim();
      }
      return this;
    }

    public Builder research(String source, String date, boolean isReviewed) {
      researchSource = source;
      reviewDate = date;
      reviewed = isReviewed;
      return this;
    }

    public Builder combatStyle(TaskStrategy.CombatStyle value) {
      combatStyle = value;
      return this;
    }

    public Builder spell(Spellbook value, String spellName) {
      spellbook = value == null ? Spellbook.STRATEGY_DEFINED : value;
      primarySpell = spellName;
      return this;
    }

    public Builder thralls(UtilityRecommendation recommendation) {
      thrallRecommendation = recommendation == null ? UtilityRecommendation.NONE : recommendation;
      return this;
    }

    public Builder deathCharge(UtilityRecommendation recommendation) {
      deathChargeRecommendation =
          recommendation == null ? UtilityRecommendation.NONE : recommendation;
      return this;
    }

    public Builder arceuusUtility(
        boolean useThralls, boolean useDeathCharge, boolean useWardOfArceuus) {
      thrallRecommendation =
          useThralls ? UtilityRecommendation.RECOMMENDED : UtilityRecommendation.NONE;
      deathChargeRecommendation =
          useDeathCharge ? UtilityRecommendation.RECOMMENDED : UtilityRecommendation.NONE;
      wardOfArceuus = useWardOfArceuus;
      return this;
    }

    public Builder cannon(boolean enabled, int cannonballs) {
      cannonMethod = enabled;
      cannonballQuantity = enabled ? Math.max(1, cannonballs) : 0;
      return this;
    }

    public Builder damageControl(DamageControl value) {
      damageControl = value == null ? DamageControl.STRATEGY_DEFINED : value;
      return this;
    }

    public Builder layout(LayoutProfile value) {
      layoutProfile = value == null ? LayoutProfile.STANDARD : value;
      return this;
    }

    public Builder restore(String primaryFamily, String fallbackFamily) {
      primaryRestoreFamily = primaryFamily;
      fallbackRestoreFamily = fallbackFamily;
      allowRestoreFallback = fallbackFamily != null && !fallbackFamily.trim().isEmpty();
      return this;
    }

    public Builder noRestoreFallback() {
      fallbackRestoreFamily = "";
      allowRestoreFallback = false;
      return this;
    }

    public Builder restoreSlots(int value) {
      restoreSlotsOverride = Math.max(0, value);
      return this;
    }

    public Builder foodSlots(int value) {
      foodSlotsOverride = Math.max(0, value);
      return this;
    }

    public Builder food(String displayName, String... alternatives) {
      foodDisplayName = clean(displayName);
      foodAlternatives.clear();
      if (alternatives != null) {
        for (String alternative : alternatives) {
          String value = clean(alternative);
          if (!value.isEmpty()) {
            foodAlternatives.add(value);
          }
        }
      }
      return this;
    }

    public Builder noInventoryFill() {
      fillRemainingWithFood = false;
      fillRemainingWithRestore = false;
      return this;
    }

    public Builder runePouch(boolean required) {
      requireRunePouch = required;
      return this;
    }

    public Builder pouchRune(int itemId, String name, int minimumQuantity) {
      pouchRunes.add(new PouchRuneRequirement(itemId, name, minimumQuantity));
      return this;
    }

    public Builder fillRemainingWithRestore() {
      fillRemainingWithRestore = true;
      fillRemainingWithFood = false;
      return this;
    }

    public Builder fillRemainingWithFood() {
      fillRemainingWithFood = true;
      fillRemainingWithRestore = false;
      return this;
    }

    public Builder includeStyleBoost(boolean value) {
      includeStyleBoost = value;
      return this;
    }

    public Builder includeRunePouchForMagic(boolean value) {
      includeRunePouchForMagic = value;
      return this;
    }

    public Builder inventoryTarget(int value) {
      inventoryTarget = value;
      return this;
    }

    public Builder reservedLootSlots(int value) {
      reservedLootSlots = Math.max(0, value);
      return this;
    }

    public Builder requiredItem(
        String displayName, int quantity, InventoryGroup group, String... alternatives) {
      requiredItems.add(new RequiredItem(displayName, quantity, 1, group, false, alternatives));
      return this;
    }

    public Builder ownedOnlyItem(
        String displayName, int quantity, InventoryGroup group, String... alternatives) {
      requiredItems.add(new RequiredItem(displayName, quantity, 1, group, true, alternatives));
      return this;
    }

    public Builder requiredSlots(
        String displayName, int slotCount, InventoryGroup group, String... alternatives) {
      requiredItems.add(
          new RequiredItem(displayName, 1, Math.max(1, slotCount), group, false, alternatives));
      return this;
    }

    public MethodRules build() {
      prepareArceuusPackage();
      return new MethodRules(this);
    }

    private void prepareArceuusPackage() {
      boolean thralls = thrallRecommendation == UtilityRecommendation.RECOMMENDED;
      boolean deathCharge = deathChargeRecommendation == UtilityRecommendation.RECOMMENDED;
      boolean ward = wardOfArceuus;
      if (!thralls && !deathCharge && !ward) {
        return;
      }
      spellbook = Spellbook.ARCEUUS;
      requireRunePouch = true;
      requireBookOfDead = thralls;
      requireDivineRunePouch = false;
      minimumMagicLevel = deathCharge ? 80 : thralls ? 76 : 73;
      if (thralls && deathCharge && ward) {
        primarySpell = "Resurrect Greater Ghost + Death Charge + Ward of Arceuus";
      } else if (thralls && deathCharge) {
        primarySpell = "Resurrect Greater Ghost + Death Charge";
      } else if (thralls && ward) {
        primarySpell = "Resurrect Greater Ghost + Ward of Arceuus";
      } else if (deathCharge && ward) {
        primarySpell = "Death Charge + Ward of Arceuus";
      } else if (thralls) {
        primarySpell = "Resurrect Greater Ghost";
      } else if (deathCharge) {
        primarySpell = "Death Charge";
      } else {
        primarySpell = "Ward of Arceuus";
      }
      pouchRunes.clear();
      if (thralls && deathCharge) {
        boolean tormentedDemonCombatMagic =
            coverageKey.toLowerCase(java.util.Locale.ENGLISH).contains("tormented demon");
        addPouchRune(554, "Fire", tormentedDemonCombatMagic ? 1000 : 10);
        if (ward) {
          addPouchRune(30843, "Aether", tormentedDemonCombatMagic ? 1000 : 6);
        } else {
          addPouchRune(564, "Cosmic", tormentedDemonCombatMagic ? 500 : 1);
          addPouchRune(566, "Soul", tormentedDemonCombatMagic ? 500 : 1);
        }
        addPouchRune(565, "Blood", tormentedDemonCombatMagic ? 500 : 6);
        addPouchRune(560, "Death", tormentedDemonCombatMagic ? 500 : 1);
        if (ward) {
          addPouchRune(561, "Nature", 2);
        }
        return;
      }
      if (thralls) {
        addPouchRune(554, "Fire", 10);
        addPouchRune(565, "Blood", 5);
        if (ward) {
          addPouchRune(30843, "Aether", 5);
          addPouchRune(561, "Nature", 2);
        } else {
          addPouchRune(564, "Cosmic", 1);
        }
        return;
      }
      if (deathCharge) {
        addPouchRune(565, "Blood", 1);
        addPouchRune(560, "Death", 1);
        if (ward) {
          addPouchRune(30843, "Aether", 5);
          addPouchRune(561, "Nature", 2);
        } else {
          addPouchRune(566, "Soul", 1);
        }
        return;
      }
      addPouchRune(566, "Soul", 4);
      addPouchRune(561, "Nature", 2);
      addPouchRune(564, "Cosmic", 1);
    }

    private void addPouchRune(int itemId, String name, int minimumQuantity) {
      pouchRunes.add(new PouchRuneRequirement(itemId, name, minimumQuantity));
    }
  }
}
