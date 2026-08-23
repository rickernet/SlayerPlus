package com.slayerplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class TaskStrategy {
  public enum CombatStyle {
    MELEE("melee"),
    RANGED("ranged"),
    MAGIC("magic"),
    HYBRID("hybrid");
    private final String label;

    CombatStyle(String label) {
      this.label = label;
    }

    public String getLabel() {
      return label;
    }
  }

  public enum ArmourFocus {
    DAMAGE,
    PRAYER,
    DEFENCE,
    MAGIC_DEFENCE,
    HYBRID
  }

  public enum CostPolicy {
    EFFICIENT,
    MAX_DPS,
    LOW_RISK
  }

  public enum DamageProfile {
    FIXED,
    ZERO_WHILE_PROTECTED,
    ZERO_WHILE_SAFESPOTTING,
    ESTIMATED_PER_KILL,
    BOSS_MECHANICS
  }

  public enum MethodTag {
    BURST_BARRAGE,
    CANNON,
    VENATOR,
    SAFESPOT,
    AUTOMATIC_STYLE_LOCKED,
    PREFERENCE_ONLY_ALTERNATIVE,
    MULTI_COMBAT,
    REQUIRED_SPECIAL_GEAR,
    BOSS,
    WILDERNESS,
    TURAEL_POINT_BOOST,
    CHINNING
  }

  private final CombatStyle combatStyle;
  private final ArmourFocus armourFocus;
  private final CostPolicy costPolicy;
  private final String method;
  private final String rationale;
  private final String selectionNote;
  private final String reviewDate;
  private final List<String> weaponPriorities;
  private final List<String> maxDpsWeaponPriorities;
  private final List<String> efficientWeaponPriorities;
  private final List<String> optionalItemPriorities;
  private final Set<MethodTag> methodTags;
  private final int prayerPotionSlots;
  private final int foodSlots;
  private final DamageProfile damageProfile;
  private final double expectedDamagePerKill;
  private final int minimumFoodSlots;
  private final int inventoryTargetSlots;
  private final boolean runePouch;
  private final boolean antivenom;
  private final boolean stamina;
  private final boolean strictWeaponProfile;
  private final boolean boss;
  private final boolean reviewed;

  private TaskStrategy(Builder builder) {
    combatStyle = builder.combatStyle;
    armourFocus = builder.armourFocus;
    costPolicy = builder.costPolicy;
    method = safe(builder.method, "Use a task-appropriate setup");
    rationale = safe(builder.rationale, "Task-specific combat profile");
    selectionNote = safe(builder.selectionNote, "");
    reviewDate = safe(builder.reviewDate, "");
    weaponPriorities = immutable(builder.weaponPriorities);
    maxDpsWeaponPriorities = immutable(builder.maxDpsWeaponPriorities);
    efficientWeaponPriorities = immutable(builder.efficientWeaponPriorities);
    optionalItemPriorities = immutable(builder.optionalItemPriorities);
    EnumSet<MethodTag> resolvedTags =
        builder.methodTags.isEmpty()
            ? EnumSet.noneOf(MethodTag.class)
            : EnumSet.copyOf(builder.methodTags);
    if (builder.boss) {
      resolvedTags.add(MethodTag.BOSS);
    }
    methodTags = immutableTags(resolvedTags);
    prayerPotionSlots = clamp(builder.prayerPotionSlots, 0, 12);
    foodSlots = clamp(builder.foodSlots, 0, 20);
    damageProfile = builder.damageProfile;
    expectedDamagePerKill = Math.max(0.0, builder.expectedDamagePerKill);
    minimumFoodSlots = clamp(builder.minimumFoodSlots, 0, foodSlots);
    inventoryTargetSlots = clamp(builder.inventoryTargetSlots, 0, 28);
    runePouch = builder.runePouch;
    antivenom = builder.antivenom;
    stamina = builder.stamina;
    strictWeaponProfile = builder.strictWeaponProfile;
    boss = builder.boss;
    reviewed = builder.reviewed;
  }

  public static Builder builder(CombatStyle combatStyle, String method) {
    return new Builder(combatStyle, method);
  }

  public CombatStyle getCombatStyle() {
    return combatStyle;
  }

  public ArmourFocus getArmourFocus() {
    return armourFocus;
  }

  public CostPolicy getCostPolicy() {
    return costPolicy;
  }

  public String getMethod() {
    return method;
  }

  public String getRationale() {
    StringBuilder text = new StringBuilder(rationale);
    if (!selectionNote.isEmpty()) {
      text.append(' ').append(selectionNote);
    }
    if (reviewed && !reviewDate.isEmpty()) {
      text.append(" Reviewed ").append(reviewDate).append('.');
    }
    return text.toString();
  }

  public String getSelectionNote() {
    return selectionNote;
  }

  public String getReviewDate() {
    return reviewDate;
  }

  public List<String> getWeaponPriorities() {
    return weaponPriorities;
  }

  public List<String> getWeaponPrioritiesForPolicy() {
    if (costPolicy == CostPolicy.MAX_DPS && !maxDpsWeaponPriorities.isEmpty()) {
      return maxDpsWeaponPriorities;
    }
    if (costPolicy == CostPolicy.EFFICIENT && !efficientWeaponPriorities.isEmpty()) {
      return efficientWeaponPriorities;
    }
    return weaponPriorities;
  }

  public List<String> getMaxDpsWeaponPriorities() {
    return maxDpsWeaponPriorities;
  }

  public List<String> getEfficientWeaponPriorities() {
    return efficientWeaponPriorities;
  }

  public List<String> getOptionalItemPriorities() {
    return optionalItemPriorities;
  }

  public Set<MethodTag> getMethodTags() {
    return methodTags;
  }

  public boolean hasTag(MethodTag tag) {
    return tag != null && methodTags.contains(tag);
  }

  public int getPrayerPotionSlots() {
    return prayerPotionSlots;
  }

  public int getFoodSlots() {
    return foodSlots;
  }

  public DamageProfile getDamageProfile() {
    return damageProfile;
  }

  public double getExpectedDamagePerKill() {
    return expectedDamagePerKill;
  }

  public int getMinimumFoodSlots() {
    return minimumFoodSlots;
  }

  public int getInventoryTargetSlots() {
    return inventoryTargetSlots;
  }

  public boolean fillsInventoryToTarget() {
    return inventoryTargetSlots > 0;
  }

  public int getRecommendedFoodSlots(int remainingKills) {
    switch (damageProfile) {
      case ZERO_WHILE_PROTECTED:
      case ZERO_WHILE_SAFESPOTTING:
        return 0;
      case ESTIMATED_PER_KILL:
        int kills = Math.max(1, remainingKills);
        int estimated = (int) Math.ceil((expectedDamagePerKill * kills) / 20.0);
        return clamp(Math.max(minimumFoodSlots, estimated), 0, foodSlots);
      case BOSS_MECHANICS:
      case FIXED:
      default:
        return foodSlots;
    }
  }

  public boolean needsRunePouch() {
    return runePouch;
  }

  public boolean needsAntivenom() {
    return antivenom;
  }

  public boolean needsStamina() {
    return stamina;
  }

  public boolean isStrictWeaponProfile() {
    return strictWeaponProfile;
  }

  public boolean isBoss() {
    return boss;
  }

  public boolean isReviewed() {
    return reviewed;
  }

  public TaskStrategy withAdditionalTags(MethodTag... tags) {
    Builder copy =
        builder(combatStyle, method)
            .armourFocus(armourFocus)
            .costPolicy(costPolicy)
            .rationale(rationale)
            .selectionNote(selectionNote)
            .prayerPotionSlots(prayerPotionSlots)
            .foodSlots(foodSlots)
            .damageProfile(damageProfile)
            .expectedDamagePerKill(expectedDamagePerKill)
            .minimumFoodSlots(minimumFoodSlots)
            .inventoryTargetSlots(inventoryTargetSlots)
            .runePouch(runePouch)
            .antivenom(antivenom)
            .stamina(stamina)
            .strictWeaponProfile(strictWeaponProfile)
            .boss(boss);
    copy.weapons(weaponPriorities.toArray(new String[0]));
    copy.maxDpsWeapons(maxDpsWeaponPriorities.toArray(new String[0]));
    copy.efficientWeapons(efficientWeaponPriorities.toArray(new String[0]));
    copy.optionalItems(optionalItemPriorities.toArray(new String[0]));
    copy.tags(methodTags.toArray(new MethodTag[0]));
    copy.tags(tags);
    if (reviewed) {
      copy.reviewed(reviewDate);
    }
    return copy.build();
  }

  public TaskStrategy asReviewed(String date) {
    if (reviewed && safe(date, "").equals(reviewDate)) {
      return this;
    }
    Builder copy =
        builder(combatStyle, method)
            .armourFocus(armourFocus)
            .costPolicy(costPolicy)
            .rationale(rationale)
            .selectionNote(selectionNote)
            .prayerPotionSlots(prayerPotionSlots)
            .foodSlots(foodSlots)
            .damageProfile(damageProfile)
            .expectedDamagePerKill(expectedDamagePerKill)
            .minimumFoodSlots(minimumFoodSlots)
            .inventoryTargetSlots(inventoryTargetSlots)
            .runePouch(runePouch)
            .antivenom(antivenom)
            .stamina(stamina)
            .strictWeaponProfile(strictWeaponProfile)
            .boss(boss)
            .reviewed(date);
    copy.weapons(weaponPriorities.toArray(new String[0]));
    copy.maxDpsWeapons(maxDpsWeaponPriorities.toArray(new String[0]));
    copy.efficientWeapons(efficientWeaponPriorities.toArray(new String[0]));
    copy.optionalItems(optionalItemPriorities.toArray(new String[0]));
    copy.tags(methodTags.toArray(new MethodTag[0]));
    return copy.build();
  }

  public TaskStrategy withLoadoutPolicy(ArmourFocus focus, CostPolicy policy) {
    ArmourFocus resolvedFocus = focus == null ? armourFocus : focus;
    CostPolicy resolvedPolicy = policy == null ? costPolicy : policy;
    if (resolvedFocus == armourFocus && resolvedPolicy == costPolicy) {
      return this;
    }
    Builder copy =
        builder(combatStyle, method)
            .armourFocus(resolvedFocus)
            .costPolicy(resolvedPolicy)
            .rationale(rationale)
            .selectionNote(selectionNote)
            .prayerPotionSlots(prayerPotionSlots)
            .foodSlots(foodSlots)
            .damageProfile(damageProfile)
            .expectedDamagePerKill(expectedDamagePerKill)
            .minimumFoodSlots(minimumFoodSlots)
            .inventoryTargetSlots(inventoryTargetSlots)
            .runePouch(runePouch)
            .antivenom(antivenom)
            .stamina(stamina)
            .strictWeaponProfile(strictWeaponProfile)
            .boss(boss);
    copy.weapons(weaponPriorities.toArray(new String[0]));
    copy.maxDpsWeapons(maxDpsWeaponPriorities.toArray(new String[0]));
    copy.efficientWeapons(efficientWeaponPriorities.toArray(new String[0]));
    copy.optionalItems(optionalItemPriorities.toArray(new String[0]));
    copy.tags(methodTags.toArray(new MethodTag[0]));
    if (reviewed) {
      copy.reviewed(reviewDate);
    }
    return copy.build();
  }

  public TaskStrategy withSelectionNote(String note) {
    Builder copy =
        builder(combatStyle, method)
            .armourFocus(armourFocus)
            .costPolicy(costPolicy)
            .rationale(rationale)
            .selectionNote(note)
            .prayerPotionSlots(prayerPotionSlots)
            .foodSlots(foodSlots)
            .damageProfile(damageProfile)
            .expectedDamagePerKill(expectedDamagePerKill)
            .minimumFoodSlots(minimumFoodSlots)
            .inventoryTargetSlots(inventoryTargetSlots)
            .runePouch(runePouch)
            .antivenom(antivenom)
            .stamina(stamina)
            .strictWeaponProfile(strictWeaponProfile)
            .boss(boss);
    copy.weapons(weaponPriorities.toArray(new String[0]));
    copy.maxDpsWeapons(maxDpsWeaponPriorities.toArray(new String[0]));
    copy.efficientWeapons(efficientWeaponPriorities.toArray(new String[0]));
    copy.optionalItems(optionalItemPriorities.toArray(new String[0]));
    copy.tags(methodTags.toArray(new MethodTag[0]));
    if (reviewed) {
      copy.reviewed(reviewDate);
    }
    return copy.build();
  }

  private static List<String> immutable(List<String> source) {
    return Collections.unmodifiableList(new ArrayList<>(source));
  }

  private static Set<MethodTag> immutableTags(Set<MethodTag> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(EnumSet.copyOf(source));
  }

  private static int clamp(int value, int minimum, int maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }

  private static String safe(String value, String fallback) {
    return value == null || value.trim().isEmpty() ? fallback : value.trim();
  }

  public static final class Builder {
    private final CombatStyle combatStyle;
    private final String method;
    private ArmourFocus armourFocus = ArmourFocus.DAMAGE;
    private CostPolicy costPolicy = CostPolicy.EFFICIENT;
    private String rationale = "Task-specific combat profile";
    private String selectionNote = "";
    private String reviewDate = "";
    private final List<String> weaponPriorities = new ArrayList<>();
    private final List<String> maxDpsWeaponPriorities = new ArrayList<>();
    private final List<String> efficientWeaponPriorities = new ArrayList<>();
    private final List<String> optionalItemPriorities = new ArrayList<>();
    private final EnumSet<MethodTag> methodTags = EnumSet.noneOf(MethodTag.class);
    private int prayerPotionSlots = 3;
    private int foodSlots = 8;
    private DamageProfile damageProfile = DamageProfile.FIXED;
    private double expectedDamagePerKill;
    private int minimumFoodSlots;
    private int inventoryTargetSlots;
    private boolean runePouch;
    private boolean antivenom;
    private boolean stamina;
    private boolean strictWeaponProfile;
    private boolean boss;
    private boolean reviewed;

    private Builder(CombatStyle combatStyle, String method) {
      this.combatStyle = combatStyle == null ? CombatStyle.MELEE : combatStyle;
      this.method = method;
    }

    public Builder armourFocus(ArmourFocus value) {
      armourFocus = value == null ? ArmourFocus.DAMAGE : value;
      return this;
    }

    public Builder costPolicy(CostPolicy value) {
      costPolicy = value == null ? CostPolicy.EFFICIENT : value;
      return this;
    }

    public Builder rationale(String value) {
      rationale = value;
      return this;
    }

    public Builder selectionNote(String value) {
      selectionNote = value;
      return this;
    }

    public Builder weapons(String... values) {
      addAll(weaponPriorities, values);
      return this;
    }

    public Builder maxDpsWeapons(String... values) {
      addAll(maxDpsWeaponPriorities, values);
      return this;
    }

    public Builder efficientWeapons(String... values) {
      addAll(efficientWeaponPriorities, values);
      return this;
    }

    public Builder optionalItems(String... values) {
      addAll(optionalItemPriorities, values);
      return this;
    }

    public Builder tags(MethodTag... values) {
      if (values != null) {
        for (MethodTag value : values) {
          if (value != null) methodTags.add(value);
        }
      }
      return this;
    }

    public Builder prayerPotionSlots(int value) {
      prayerPotionSlots = value;
      return this;
    }

    public Builder foodSlots(int value) {
      foodSlots = value;
      return this;
    }

    public Builder damageProfile(DamageProfile value) {
      damageProfile = value == null ? DamageProfile.FIXED : value;
      return this;
    }

    public Builder expectedDamagePerKill(double value) {
      expectedDamagePerKill = Math.max(0.0, value);
      return this;
    }

    public Builder minimumFoodSlots(int value) {
      minimumFoodSlots = value;
      return this;
    }

    public Builder inventoryTargetSlots(int value) {
      inventoryTargetSlots = value;
      return this;
    }

    public Builder fillRemainingInventoryWithFood() {
      inventoryTargetSlots = 28;
      return this;
    }

    public Builder zeroDamageWhileProtected() {
      damageProfile = DamageProfile.ZERO_WHILE_PROTECTED;
      expectedDamagePerKill = 0.0;
      minimumFoodSlots = 0;
      foodSlots = 0;
      return this;
    }

    public Builder zeroDamageWhileSafespotted() {
      damageProfile = DamageProfile.ZERO_WHILE_SAFESPOTTING;
      expectedDamagePerKill = 0.0;
      minimumFoodSlots = 0;
      foodSlots = 0;
      return this;
    }

    public Builder runePouch(boolean value) {
      runePouch = value;
      return this;
    }

    public Builder antivenom(boolean value) {
      antivenom = value;
      return this;
    }

    public Builder stamina(boolean value) {
      stamina = value;
      return this;
    }

    public Builder strictWeaponProfile(boolean value) {
      strictWeaponProfile = value;
      return this;
    }

    public Builder boss(boolean value) {
      boss = value;
      return this;
    }

    public Builder reviewed(String date) {
      reviewed = true;
      reviewDate = date;
      return this;
    }

    public TaskStrategy build() {
      return new TaskStrategy(this);
    }

    private static void addAll(List<String> destination, String... values) {
      if (values == null) return;
      for (String value : values) {
        if (value != null && !value.trim().isEmpty()) destination.add(value.trim());
      }
    }
  }
}
