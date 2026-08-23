package com.slayerplus;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.EquipmentInventorySlot;

public final class SlayerEquipmentAuditCatalog {

    private static final Map<String, List<String>> LISTS = loadLists();

    private static final Map<String, Map<EquipmentInventorySlot, List<String>>> PROFILES = loadProfiles();

    private SlayerEquipmentAuditCatalog() {
    }

    public static List<String> priorities(String taskName, TaskStrategy strategy, EquipmentInventorySlot slot, String selectedWeaponName) {
        if (strategy == null || slot == null) {
            return Collections.emptyList();
        }
        var style = strategy.getCombatStyle();
        var focus = strategy.getArmourFocus();
        var task = normalize(taskName);
        var weapon = normalize(selectedWeaponName);
        var prayer = focus == TaskStrategy.ArmourFocus.PRAYER;
        var magicDefence = focus == TaskStrategy.ArmourFocus.MAGIC_DEFENCE;
        var wilderness = strategy.getCostPolicy() == TaskStrategy.CostPolicy.LOW_RISK || strategy.hasTag(TaskStrategy.MethodTag.WILDERNESS);
        if (slot == EquipmentInventorySlot.HEAD) {
            if (task.contains("shellbane gryphon")) {
                return list("e039");
            }
            if ((task.equals("blue dragon") || task.equals("blue dragons")) && style == TaskStrategy.CombatStyle.RANGED) {
                return list("e040");
            }
            return style == TaskStrategy.CombatStyle.MELEE ? list("e041") : list("e042");
        }
        if (task.equals("royal titans")) {
            return royalTitans(slot);
        }
        if (task.equals("araxxor")) {
            return araxxor(slot, weapon);
        }
        if (slot == EquipmentInventorySlot.AMULET && task.contains("banshee")) {
            return list("e043");
        }
        if ((task.equals("blue dragon") || task.equals("blue dragons")) && style == TaskStrategy.CombatStyle.RANGED && slot == EquipmentInventorySlot.SHIELD) {
            return list("e044");
        }
        if ((task.equals("skeletal wyvern") || task.equals("skeletal wyverns")) && slot == EquipmentInventorySlot.SHIELD) {
            if (style == TaskStrategy.CombatStyle.RANGED) {
                return list("e045");
            }
            if (style == TaskStrategy.CombatStyle.MAGIC) {
                return list("e046");
            }
            return list("e047");
        }
        if (task.equals("barrows brothers")) {
            return slot == EquipmentInventorySlot.HEAD ? list("e048") : profile("barrows", slot);
        }
        if (task.equals("k ril tsutsaroth") && style == TaskStrategy.CombatStyle.RANGED) {
            if (weapon.contains("bow of faerdhinen")) {
                if (slot == EquipmentInventorySlot.BODY) {
                    return list("e053");
                }
                if (slot == EquipmentInventorySlot.LEGS) {
                    return list("e055");
                }
            }
            return profile("kril-ranged", slot);
        }
        if (task.equals("k ril tsutsaroth") && style == TaskStrategy.CombatStyle.MELEE) {
            return profile("kril-melee", slot);
        }
        if (task.equals("grotesque guardians") || task.equals("the grotesque guardians")) {
            return profile("grotesque", slot);
        }
        if (task.equals("tormented demons")) {
            return profile("tormented", slot);
        }
        if (task.equals("whisperer") || task.equals("the whisperer")) {
            if (slot == EquipmentInventorySlot.RING && weapon.contains("tumeken s shadow")) {
                return list("e086");
            }
            return profile("whisperer", slot);
        }
        if (task.equals("kalphite queen") || task.equals("the kalphite queen")) {
            return profile(style == TaskStrategy.CombatStyle.MAGIC ? "kalphite-magic" : "kalphite-melee", slot);
        }
        if (task.contains("shellbane gryphon")) {
            switch(slot) {
                case BODY:
                    return list("e100");
                case LEGS:
                    return list("e101");
                case SHIELD:
                    return list("e102");
                case BOOTS:
                    return list("e103");
                default:
                    break;
            }
        }
        if (task.equals("gryphon") || task.equals("gryphons")) {
            switch(slot) {
                case HEAD:
                    return list("e039");
                case BODY:
                    return list("e104");
                case LEGS:
                    return list("e105");
                case BOOTS:
                    return list("e106");
                default:
                    break;
            }
        }
        if (task.contains("maggot king")) {
            return profile("maggot", slot);
        }
        if (wilderness) {
            return wilderness(style, slot);
        }
        if (slot == EquipmentInventorySlot.GLOVES && strategy.isBoss()) {
            return bossGloves(style);
        }
        switch(style) {
            case RANGED:
                return ranged(slot, weapon, prayer);
            case MAGIC:
                return magic(slot, prayer, magicDefence, taskName);
            case HYBRID:
                return hybrid(slot);
            case MELEE:
            default:
                return melee(slot, prayer, magicDefence);
        }
    }

    private static List<String> bossGloves(TaskStrategy.CombatStyle style) {
        switch(style) {
            case RANGED:
                return list("e110");
            case MAGIC:
                return list("e111");
            case HYBRID:
                return list("e112");
            case MELEE:
            default:
                return list("e113");
        }
    }

    public static List<String> weaponProgression(TaskStrategy strategy) {
        if (strategy == null) {
            return Collections.emptyList();
        }
        var wilderness = strategy.getCostPolicy() == TaskStrategy.CostPolicy.LOW_RISK || strategy.hasTag(TaskStrategy.MethodTag.WILDERNESS);
        if (wilderness) {
            switch(strategy.getCombatStyle()) {
                case MAGIC:
                    return list("e114");
                case RANGED:
                    return list("e115");
                case MELEE:
                default:
                    return list("e116");
            }
        }
        switch(strategy.getCombatStyle()) {
            case RANGED:
                return list("e117");
            case MAGIC:
                return list("e118");
            case HYBRID:
                return list("e119");
            case MELEE:
            default:
                return list("e120");
        }
    }

    private static List<String> melee(EquipmentInventorySlot slot, boolean prayer, boolean magicDefence) {
        var overlay = magicDefence ? "melee-magic-defence" : prayer ? "melee-prayer" : null;
        var values = overlay == null ? null : profile(overlay, slot);
        if (values != null && !values.isEmpty()) {
            return values;
        }
        return profile("melee", slot);
    }

    private static List<String> ranged(EquipmentInventorySlot slot, String weapon, boolean prayer) {
        if (weapon.contains("bow of faerdhinen")) {
            if (slot == EquipmentInventorySlot.BODY) {
                return prepend("crystal body", profile("ranged", slot));
            }
            if (slot == EquipmentInventorySlot.LEGS) {
                return prepend("crystal legs", profile("ranged", slot));
            }
        }
        var values = prayer ? profile("ranged-prayer", slot) : null;
        if (values != null && !values.isEmpty()) {
            return values;
        }
        return profile("ranged", slot);
    }

    private static List<String> magic(EquipmentInventorySlot slot, boolean prayer, boolean magicDefence, String taskName) {
        var accuracySensitive = normalize(taskName).contains("smoke devil");
        var overlay = magicDefence ? "magic-defence" : prayer && !accuracySensitive ? "magic-prayer" : null;
        var values = overlay == null ? null : profile(overlay, slot);
        if (values != null && !values.isEmpty()) {
            return values;
        }
        return profile("magic", slot);
    }

    private static List<String> hybrid(EquipmentInventorySlot slot) {
        return profile("hybrid", slot);
    }

    private static List<String> royalTitans(EquipmentInventorySlot slot) {
        return profile("royal-titans", slot);
    }

    private static List<String> araxxor(EquipmentInventorySlot slot, String weapon) {
        var soulreaper = weapon.contains("soulreaper axe");
        if (soulreaper) {
            if (slot == EquipmentInventorySlot.BODY) {
                return list("e139");
            }
            if (slot == EquipmentInventorySlot.LEGS) {
                return list("e141");
            }
        }
        return profile("araxxor", slot);
    }

    private static List<String> wilderness(TaskStrategy.CombatStyle style, EquipmentInventorySlot slot) {
        var name = style == TaskStrategy.CombatStyle.MAGIC ? "wilderness-magic" : style == TaskStrategy.CombatStyle.MELEE ? "wilderness-melee" : "wilderness-ranged";
        return profile(name, slot);
    }

    public static Map<EquipmentInventorySlot, List<String>> auditedSlotsForRegression(String taskName, TaskStrategy strategy, String selectedWeaponName) {
        final Map<EquipmentInventorySlot, List<String>> result = new EnumMap<>(EquipmentInventorySlot.class);
        for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) {
            if (slot == EquipmentInventorySlot.WEAPON || slot == EquipmentInventorySlot.AMMO) {
                continue;
            }
            var values = priorities(taskName, strategy, slot, selectedWeaponName);
            if (!values.isEmpty()) {
                result.put(slot, values);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<String> prepend(String value, List<String> remaining) {
        var values = new String[remaining.size() + 1];
        values[0] = value;
        for (var i = 0; i < remaining.size(); i++) {
            values[i + 1] = remaining.get(i);
        }
        return values(values);
    }

    private static List<String> values(String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    private static List<String> list(String key) {
        var values = LISTS.get(key);
        if (values == null) {
            throw new IllegalStateException("Missing equipment list: " + key);
        }
        return values;
    }

    private static List<String> profile(String name, EquipmentInventorySlot slot) {
        var values = PROFILES.get(name);
        if (values == null) {
            throw new IllegalStateException("Missing equipment profile: " + name);
        }
        return values.getOrDefault(slot, Collections.emptyList());
    }

    private static Map<String, List<String>> loadLists() {
        Map<String, List<String>> lists = new LinkedHashMap<>();
        for (String[] row : ResourceTable.rows("slayer-equipment-lists.tsv", 2)) {
            lists.put(row[0], values(row[1].split("\\|", -1)));
        }
        return Collections.unmodifiableMap(lists);
    }

    private static Map<String, Map<EquipmentInventorySlot, List<String>>> loadProfiles() {
        EquipmentInventorySlot[] slots = { EquipmentInventorySlot.CAPE, EquipmentInventorySlot.AMULET, EquipmentInventorySlot.BODY, EquipmentInventorySlot.SHIELD, EquipmentInventorySlot.LEGS, EquipmentInventorySlot.GLOVES, EquipmentInventorySlot.BOOTS, EquipmentInventorySlot.RING, EquipmentInventorySlot.AMMO };
        Map<String, Map<EquipmentInventorySlot, List<String>>> profiles = new LinkedHashMap<>();
        for (String[] row : ResourceTable.rows("slayer-equipment-profiles.tsv", 10)) {
            Map<EquipmentInventorySlot, List<String>> values = new EnumMap<>(EquipmentInventorySlot.class);
            for (var i = 0; i < slots.length; i++) {
                if (!row[i + 1].isEmpty() && !row[i + 1].equals("-")) {
                    values.put(slots[i], list(row[i + 1]));
                }
            }
            profiles.put(row[0], Collections.unmodifiableMap(values));
        }
        return Collections.unmodifiableMap(profiles);
    }

    private static String normalize(String value) {
        return SlayerText.normalize(value);
    }
}
