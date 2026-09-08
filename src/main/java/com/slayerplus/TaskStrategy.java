package com.slayerplus;

import java.util.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public final class TaskStrategy {
  @Getter
  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  public enum CombatStyle {
    MELEE("melee"),
    RANGED("ranged"),
    MAGIC("magic"),
    HYBRID("hybrid");
    private final String label;
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
    BARRAGE,
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

  private final CombatStyle style;
  private final ArmourFocus armourFocus;
  private final CostPolicy costPolicy;
  private final String method;
  private final String rationale;
  private final String selectionNote;
  private final String reviewDate;
  private final List<String> weapons;
  private final List<String> dpsWeapons;
  private final List<String> efficientWeaponPriorities;
  private final List<String> optionalItemPriorities;
  private final Set<MethodTag> methodTags;
  private final int prayers;
  private final int food;
  private final DamageProfile damageProfile;
  private final double damagePerKill;
  private final int minimumFoodSlots;
  private final int inventoryTargetSlots;
  private final boolean runePouch;
  private final boolean antivenom;
  private final boolean stamina;
  private final boolean strictWeaponProfile;
  private final boolean boss;
  private final boolean reviewed;

  private TaskStrategy(Builder builder) {
    style = builder.style;
    armourFocus = builder.armourFocus;
    costPolicy = builder.costPolicy;
    method = safe(builder.method, "Use a task-appropriate setup");
    rationale = safe(builder.rationale, "Task-specific combat profile");
    selectionNote = safe(builder.selectionNote, "");
    reviewDate = safe(builder.reviewDate, "");
    weapons = immutable(builder.weapons);
    dpsWeapons = immutable(builder.dpsWeapons);
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
    prayers = clamp(builder.prayers, 0, 12);
    food = clamp(builder.food, 0, 20);
    damageProfile = builder.damageProfile;
    damagePerKill = Math.max(0.0, builder.damagePerKill);
    minimumFoodSlots = clamp(builder.minimumFoodSlots, 0, food);
    inventoryTargetSlots = clamp(builder.inventoryTargetSlots, 0, 28);
    runePouch = builder.runePouch;
    antivenom = builder.antivenom;
    stamina = builder.stamina;
    strictWeaponProfile = builder.strictWeaponProfile;
    boss = builder.boss;
    reviewed = builder.reviewed;
  }

  public static Builder builder(CombatStyle style, String method) {
    return new Builder(style, method);
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

  public List<String> weapons() {
    if (costPolicy == CostPolicy.MAX_DPS && !dpsWeapons.isEmpty()) {
      return dpsWeapons;
    }
    if (costPolicy == CostPolicy.EFFICIENT && !efficientWeaponPriorities.isEmpty()) {
      return efficientWeaponPriorities;
    }
    return weapons;
  }

  public List<String> getWeaponPriorities() {
    return weapons;
  }

  public boolean hasTag(MethodTag tag) {
    return tag != null && methodTags.contains(tag);
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
        int estimated = (int) Math.ceil((damagePerKill * kills) / 20.0);
        return clamp(Math.max(minimumFoodSlots, estimated), 0, food);
      case BOSS_MECHANICS:
      case FIXED:
      default:
        return food;
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

  public TaskStrategy withAdditionalTags(MethodTag... tags) {
    Builder copy = copyBuilder(armourFocus, costPolicy, selectionNote);
    copy.tags(tags);
    return copy.build();
  }

  public TaskStrategy withLoadoutPolicy(ArmourFocus focus, CostPolicy policy) {
    ArmourFocus resolvedFocus = focus == null ? armourFocus : focus;
    CostPolicy resolvedPolicy = policy == null ? costPolicy : policy;
    if (resolvedFocus == armourFocus && resolvedPolicy == costPolicy) {
      return this;
    }
    return copyBuilder(resolvedFocus, resolvedPolicy, selectionNote).build();
  }

  public TaskStrategy withSelectionNote(String note) {
    return copyBuilder(armourFocus, costPolicy, note).build();
  }

  private Builder copyBuilder(ArmourFocus focus, CostPolicy policy, String note) {
    Builder copy =
        builder(style, method)
            .armourFocus(focus)
            .costPolicy(policy)
            .rationale(rationale)
            .selectionNote(note)
            .prayers(prayers)
            .food(food)
            .damageProfile(damageProfile)
            .damagePerKill(damagePerKill)
            .minimumFoodSlots(minimumFoodSlots)
            .inventoryTargetSlots(inventoryTargetSlots)
            .runePouch(runePouch)
            .antivenom(antivenom)
            .stamina(stamina)
            .strictWeaponProfile(strictWeaponProfile)
            .boss(boss);
    copy.weapons(weapons.toArray(new String[0]));
    copy.maxDpsWeapons(dpsWeapons.toArray(new String[0]));
    copy.efficientWeapons(efficientWeaponPriorities.toArray(new String[0]));
    copy.optionalItems(optionalItemPriorities.toArray(new String[0]));
    copy.tags(methodTags.toArray(new MethodTag[0]));
    if (reviewed) {
      copy.reviewed(reviewDate);
    }
    return copy;
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
    private final CombatStyle style;
    private final String method;
    private ArmourFocus armourFocus = ArmourFocus.DAMAGE;
    private CostPolicy costPolicy = CostPolicy.EFFICIENT;
    private String rationale = "Task-specific combat profile";
    private String selectionNote = "";
    private String reviewDate = "";
    private final List<String> weapons = new ArrayList<>();
    private final List<String> dpsWeapons = new ArrayList<>();
    private final List<String> efficientWeaponPriorities = new ArrayList<>();
    private final List<String> optionalItemPriorities = new ArrayList<>();
    private final EnumSet<MethodTag> methodTags = EnumSet.noneOf(MethodTag.class);
    private int prayers = 3;
    private int food = 8;
    private DamageProfile damageProfile = DamageProfile.FIXED;
    private double damagePerKill;
    private int minimumFoodSlots;
    private int inventoryTargetSlots;
    private boolean runePouch;
    private boolean antivenom;
    private boolean stamina;
    private boolean strictWeaponProfile;
    private boolean boss;
    private boolean reviewed;

    private Builder(CombatStyle style, String method) {
      this.style = style == null ? CombatStyle.MELEE : style;
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
      addAll(weapons, values);
      return this;
    }

    public Builder maxDpsWeapons(String... values) {
      addAll(dpsWeapons, values);
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
          if (value != null) {
            methodTags.add(value);
          }
        }
      }
      return this;
    }

    public Builder prayers(int value) {
      prayers = value;
      return this;
    }

    public Builder food(int value) {
      food = value;
      return this;
    }

    public Builder damageProfile(DamageProfile value) {
      damageProfile = value == null ? DamageProfile.FIXED : value;
      return this;
    }

    public Builder damagePerKill(double value) {
      damagePerKill = Math.max(0.0, value);
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
      if (values == null) {
        return;
      }
      for (String value : values) {
        if (value != null && !value.trim().isEmpty()) {
          destination.add(value.trim());
        }
      }
    }
  }
}
