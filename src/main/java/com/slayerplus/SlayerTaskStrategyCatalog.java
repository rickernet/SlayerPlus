package com.slayerplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SlayerTaskStrategyCatalog {

    private static final Map<String, Profiles> DATA = loadData();

    private static final Map<String, String> TASK_ALIASES = loadTaskAliases();

    private static final Map<String, Profiles> PROFILES = baseProfiles(DATA);

    private static final Set<String> COVERED_TASKS = TaskResearch.getReviewedTaskNames();

    private static final Set<String> REVIEWED_TASK_KEYS = TaskResearch.getReviewedTaskKeys();

    private SlayerTaskStrategyCatalog() {
    }

    public static TaskStrategy resolve(String taskName, Preference.Playstyle playstyle, Preference.Cannon cannonPreference, Preference.Burst burstPreference, Preference.CombatStyle combatPreference, String location, boolean wilderness) {
        var task = canonicalTaskKey(taskName);
        if (!REVIEWED_TASK_KEYS.contains(task))
            return unreviewed(taskName);
        if (wilderness) {
            return wildernessStrategyFor(taskName, task, playstyle, cannonPreference, burstPreference, combatPreference);
        }
        var base = PROFILES.get(task);
        if (base == null)
            return unreviewed(taskName);
        var profiles = profilesForLocation(task, normalize(location), base);
        TaskStrategy selected = null;
        if (combatPreference == Preference.CombatStyle.PREFER_MAGIC && profiles.magic == null) {
            var automatic = profiles.selectAutomatic(playstyle);
            if (automatic != null && !automatic.isBoss() && automatic.getCombatStyle() != TaskStrategy.CombatStyle.HYBRID) {
                var elemental = SlayerElementalWeaknessCatalog.preferenceStrategy(taskName, false);
                if (elemental != null) {
                    selected = elemental.withSelectionNote("Magic preference applied using the current Wiki-listed elemental weakness;" + " Automatic keeps the reviewed practical method.");
                }
            }
        }
        if (selected == null) {
            selected = profiles.select(playstyle, cannonPreference, burstPreference, combatPreference);
        }
        var audit = SlayerTaskTravelAuditCatalog.find(taskName);
        var cannonAudit = audit == null ? "" : normalize(audit.getCannon());
        if (selected != null && !selected.isBoss() && cannonPreference == Preference.Cannon.PREFER && !selected.hasTag(TaskStrategy.MethodTag.CANNON) && locationAllowsCannon(taskName, location) && cannonSupportsSelectedCombat(taskName, selected) && (cannonAudit.contains("recommended") || cannonAudit.contains("optional"))) {
            selected = selected.withAdditionalTags(TaskStrategy.MethodTag.CANNON).withSelectionNote("Cannon preference applied because the individually audited task location permits" + " a dwarf multicannon.");
        }
        return selected;
    }

    private static boolean cannonSupportsSelectedCombat(String taskName, TaskStrategy strategy) {
        return strategy != null && (!canonicalTaskKey(taskName).equals("greater demons") || strategy.getCombatStyle() != TaskStrategy.CombatStyle.MELEE);
    }

    private static boolean locationAllowsCannon(String taskName, String location) {
        var area = normalize(location);
        var task = canonicalTaskKey(taskName);
        if (task.equals("greater demons") && area.contains("karuulm"))
            return true;
        return !area.contains("catacombs") && !area.contains("slayer tower") && !area.contains("fremennik slayer dungeon") && !area.contains("karuulm") && !area.contains("kraken") && !area.contains("god wars") && !area.contains("mos le harmless");
    }

    public static TaskStrategy legacy(String method) {
        return unreviewed("Legacy recommendation");
    }

    public static boolean hasExplicitStrategy(String taskName) {
        return REVIEWED_TASK_KEYS.contains(canonicalTaskKey(taskName));
    }

    public static Set<String> getCoveredTaskNames() {
        return COVERED_TASKS;
    }

    public static int getCurrentTaskCount() {
        return REVIEWED_TASK_KEYS.size();
    }

    public static int getReviewedTaskCount() {
        return REVIEWED_TASK_KEYS.size();
    }

    public static int getReviewProgressPercent() {
        return 100;
    }

    public static List<String> getCurrentTaskNames() {
        return Collections.unmodifiableList(new ArrayList<>(COVERED_TASKS));
    }

    public static TaskResearch.Entry getResearchRecord(String taskName) {
        return TaskResearch.find(canonicalTaskKey(taskName));
    }

    public static List<String> getMissingCurrentTasks() {
        List<String> missing = new ArrayList<>();
        for (String task : COVERED_TASKS) {
            if (!PROFILES.containsKey(canonicalTaskKey(task)))
                missing.add(task);
        }
        return Collections.unmodifiableList(missing);
    }

    private static TaskStrategy wildernessStrategyFor(String taskName, String task, Preference.Playstyle playstyle, Preference.Cannon cannonPreference, Preference.Burst burstPreference, Preference.CombatStyle combatPreference) {
        var research = TaskResearch.find(taskName);
        if (research == null || !research.isWildernessReviewed())
            return unreviewedWilderness(taskName);
        var base = PROFILES.get(task);
        if (base != null) {
            var selected = base.select(playstyle, cannonPreference, burstPreference, combatPreference);
            if (selected != null && selected.hasTag(TaskStrategy.MethodTag.WILDERNESS) && selected.getCostPolicy() == TaskStrategy.CostPolicy.LOW_RISK) {
                return selected.withSelectionNote("Reviewed low-risk Wilderness profile applied.");
            }
        }
        var wilderness = DATA.get("wilderness:" + task);
        if (wilderness != null) {
            return wilderness.select(playstyle, cannonPreference, burstPreference, combatPreference).withSelectionNote("Krystilia assignment: individually reviewed LOW_RISK Wilderness branch applied.");
        }
        return unreviewedWilderness(taskName);
    }

    private static Profiles profilesForLocation(String task, String location, Profiles fallback) {
        if (task.equals("ankou")) {
            if (location.contains("stronghold slayer cave"))
                return data("location:ankou:stronghold_slayer_cave", fallback);
            if (location.contains("stronghold of security"))
                return data("location:ankou:stronghold_of_security", fallback);
        }
        if (task.equals("abyssal demons") && location.contains("slayer tower"))
            return data("location:abyssal_demons:slayer_tower", fallback);
        if (task.equals("bloodveld")) {
            if (location.contains("meiyerditch") || location.contains("buccaneer"))
                return data("location:bloodveld:multi_cannon", fallback);
            if (location.contains("iorwerth") || location.contains("stronghold slayer cave"))
                return data("location:bloodveld:single_cannon", fallback);
            if (location.contains("catacombs"))
                return data("location:bloodveld:catacombs", fallback);
            if (location.contains("slayer tower"))
                return data("location:bloodveld:slayer_tower", fallback);
            if (location.contains("god wars dungeon"))
                return data("location:bloodveld:god_wars", fallback);
        }
        if (task.equals("aberrant spectres")) {
            if (location.contains("stronghold slayer cave"))
                return data("location:aberrant_spectres:stronghold_slayer_cave", fallback);
            if (location.contains("catacombs"))
                return data("location:aberrant_spectres:catacombs", fallback);
        }
        if (task.equals("hellhounds")) {
            if (location.contains("stronghold slayer cave") || location.contains("taverley dungeon") || location.contains("karuulm slayer dungeon"))
                return data("location:hellhounds:cannon", fallback);
            if (location.contains("catacombs"))
                return data("location:hellhounds:catacombs", fallback);
        }
        if (task.equals("greater demons") && locationAllowsCannon(task, location))
            return data("location:greater_demons:cannon", fallback);
        if (task.equals("nechryael") && location.contains("iorwerth"))
            return data("location:nechryael:iorwerth", fallback);
        return fallback;
    }

    private static Profiles data(String key, Profiles fallback) {
        var profiles = DATA.get(key);
        return profiles == null ? fallback : profiles;
    }

    private static TaskStrategy unreviewed(String taskName) {
        return TaskStrategy.builder(TaskStrategy.CombatStyle.HYBRID, "Research pending — loadout disabled").rationale((taskName == null ? "This task" : taskName) + " has not yet passed individual strategy review. SlayerPlus will not invent a" + " broad equipment template.").prayerPotionSlots(0).foodSlots(0).strictWeaponProfile(true).build();
    }

    private static TaskStrategy unreviewedWilderness(String taskName) {
        return TaskStrategy.builder(TaskStrategy.CombatStyle.HYBRID, "Wilderness research pending — loadout disabled").costPolicy(TaskStrategy.CostPolicy.LOW_RISK).rationale((taskName == null ? "This task" : taskName) + " has no individually reviewed Krystilia profile. The normal-task profile is" + " never reused in the Wilderness.").tags(TaskStrategy.MethodTag.WILDERNESS).prayerPotionSlots(0).foodSlots(0).strictWeaponProfile(true).build();
    }

    private static Map<String, Profiles> loadData() {
        Map<String, TaskStrategy> strategies = new LinkedHashMap<>();
        Map<String, Profiles> profiles = new LinkedHashMap<>();
        var lineNumber = 0;
        try {
            for (String[] fields : ResourceTable.rows("slayer-task-strategies.tsv", 3, 26)) {
                lineNumber++;
                if (fields.length == 26 && "S".equals(fields[0])) {
                    var id = unescape(fields[1]);
                    if (strategies.put(id, parseStrategy(fields, lineNumber)) != null)
                        throw new IllegalStateException("Duplicate Slayer strategy id " + id);
                    continue;
                }
                if (fields.length == 3 && "P".equals(fields[0])) {
                    var key = unescape(fields[1]);
                    var group = new Profiles();
                    for (String mapping : fields[2].split(";", -1)) {
                        var separator = mapping.indexOf('=');
                        if (separator <= 0 || separator == mapping.length() - 1)
                            throw new IllegalStateException("Invalid Slayer profile mapping at row " + lineNumber);
                        var id = mapping.substring(separator + 1);
                        var strategy = strategies.get(id);
                        if (strategy == null)
                            throw new IllegalStateException("Unknown Slayer strategy id " + id + " at row " + lineNumber);
                        group.set(mapping.substring(0, separator), strategy);
                    }
                    if (profiles.put(key, group) != null)
                        throw new IllegalStateException("Duplicate Slayer profile " + key);
                    continue;
                }
                throw new IllegalStateException("Invalid Slayer strategy row " + lineNumber + ": expected an S or P record");
            }
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Unable to load reviewed Slayer strategies", ex);
        }
        for (Map.Entry<String, Profiles> entry : profiles.entrySet()) entry.getValue().validate(entry.getKey());
        return Collections.unmodifiableMap(profiles);
    }

    private static TaskStrategy parseStrategy(String[] fields, int lineNumber) {
        try {
            var builder = TaskStrategy.builder(TaskStrategy.CombatStyle.valueOf(unescape(fields[2])), unescape(fields[5])).armourFocus(TaskStrategy.ArmourFocus.valueOf(unescape(fields[3]))).costPolicy(TaskStrategy.CostPolicy.valueOf(unescape(fields[4]))).rationale(unescape(fields[6])).selectionNote(unescape(fields[7])).weapons(list(fields[9])).maxDpsWeapons(list(fields[10])).efficientWeapons(list(fields[11])).optionalItems(list(fields[12])).tags(tags(fields[13])).prayerPotionSlots(Integer.parseInt(fields[14])).foodSlots(Integer.parseInt(fields[15])).damageProfile(TaskStrategy.DamageProfile.valueOf(fields[16])).expectedDamagePerKill(Double.parseDouble(fields[17])).minimumFoodSlots(Integer.parseInt(fields[18])).inventoryTargetSlots(Integer.parseInt(fields[19])).runePouch(Boolean.parseBoolean(fields[20])).antivenom(Boolean.parseBoolean(fields[21])).stamina(Boolean.parseBoolean(fields[22])).strictWeaponProfile(Boolean.parseBoolean(fields[23])).boss(Boolean.parseBoolean(fields[24]));
            if (Boolean.parseBoolean(fields[25]))
                builder.reviewed(unescape(fields[8]));
            return builder.build();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Invalid Slayer strategy at row " + lineNumber, ex);
        }
    }

    private static String[] list(String encoded) {
        var value = unescape(encoded);
        return value.isEmpty() ? new String[0] : value.split("; ", -1);
    }

    private static TaskStrategy.MethodTag[] tags(String encoded) {
        var values = list(encoded);
        var tags = new TaskStrategy.MethodTag[values.length];
        for (var index = 0; index < values.length; index++) tags[index] = TaskStrategy.MethodTag.valueOf(values[index]);
        return tags;
    }

    private static String unescape(String encoded) {
        if (encoded == null || encoded.indexOf('\\') < 0)
            return encoded;
        var value = new StringBuilder(encoded.length());
        var escaped = false;
        for (var index = 0; index < encoded.length(); index++) {
            var character = encoded.charAt(index);
            if (!escaped && character == '\\') {
                escaped = true;
                continue;
            }
            if (escaped) {
                switch(character) {
                    case 't':
                        value.append('\t');
                        break;
                    case 'r':
                        value.append('\r');
                        break;
                    case 'n':
                        value.append('\n');
                        break;
                    case 'p':
                        value.append('|');
                        break;
                    default:
                        value.append(character);
                        break;
                }
                escaped = false;
            } else
                value.append(character);
        }
        if (escaped)
            value.append('\\');
        return value.toString();
    }

    private static Map<String, Profiles> baseProfiles(Map<String, Profiles> data) {
        final Map<String, Profiles> base = new HashMap<>();
        for (Map.Entry<String, Profiles> entry : data.entrySet()) {
            if (entry.getKey().startsWith("base:"))
                base.put(entry.getKey().substring(5), entry.getValue());
        }
        return Collections.unmodifiableMap(base);
    }

    private static String normalize(String value) {
        return SlayerText.encounter(value);
    }

    private static String canonicalTaskKey(String value) {
        var key = normalize(value);
        return TASK_ALIASES.getOrDefault(key, key);
    }

    private static Map<String, String> loadTaskAliases() {
        Map<String, String> aliases = new HashMap<>();
        for (String[] row : ResourceTable.rows("slayer-task-aliases.tsv", 2)) {
            var previous = aliases.put(normalize(row[0]), normalize(row[1]));
            if (previous != null) {
                throw new IllegalStateException("Duplicate task alias " + row[0]);
            }
        }
        return Collections.unmodifiableMap(aliases);
    }

    private static final class Profiles {

        private TaskStrategy balanced;

        private TaskStrategy fast;

        private TaskStrategy afk;

        private TaskStrategy profit;

        private TaskStrategy melee;

        private TaskStrategy ranged;

        private TaskStrategy magic;

        private TaskStrategy burst;

        private TaskStrategy nonBurst;

        private TaskStrategy cannon;

        private TaskStrategy nonCannon;

        private void set(String role, TaskStrategy value) {
            switch(role) {
                case "balanced":
                    balanced = value;
                    break;
                case "fast":
                    fast = value;
                    break;
                case "afk":
                    afk = value;
                    break;
                case "profit":
                    profit = value;
                    break;
                case "melee":
                    melee = value;
                    break;
                case "ranged":
                    ranged = value;
                    break;
                case "magic":
                    magic = value;
                    break;
                case "burst":
                    burst = value;
                    break;
                case "non_burst":
                    nonBurst = value;
                    break;
                case "cannon":
                    cannon = value;
                    break;
                case "non_cannon":
                    nonCannon = value;
                    break;
                default:
                    throw new IllegalStateException("Unknown strategy role " + role);
            }
        }

        private void validate(String key) {
            if (balanced == null || fast == null || afk == null || profit == null)
                throw new IllegalStateException("Incomplete Slayer strategy group " + key);
        }

        private TaskStrategy select(Preference.Playstyle playstyle, Preference.Cannon cannonPreference, Preference.Burst burstPreference, Preference.CombatStyle combatPreference) {
            var selected = selectCombatPreference(combatPreference);
            var explicitCombat = selected != null;
            var note = "";
            if (!explicitCombat)
                selected = selectAutomatic(playstyle);
            if (!explicitCombat && cannonPreference == Preference.Cannon.PREFER && cannon != null)
                selected = cannon;
            if (!explicitCombat && burstPreference == Preference.Burst.PREFER && burst != null)
                selected = burst;
            if (violatesEnabledPreferences(selected, cannonPreference, burstPreference)) {
                selected = safePreferenceFallback(selected, cannonPreference, burstPreference);
                note = "Disabled cannon/burst preferences were enforced with a reviewed compatible fallback.";
            }
            selected = applyPlaystyleLoadoutPolicy(selected, playstyle);
            var authoredNote = selected.getSelectionNote();
            if (note.isEmpty() && explicitCombat)
                note = combatPreference + " applied because this task has a reviewed viable " + selected.getCombatStyle().getLabel() + " setup.";
            if (note.isEmpty()) {
                note = "Automatic selected the reviewed " + selected.getCombatStyle().getLabel() + " method." + (selected.hasTag(TaskStrategy.MethodTag.BURST_BARRAGE) ? " Burst/Barrage preference permits this method." : "") + (selected.hasTag(TaskStrategy.MethodTag.CANNON) ? " Cannon preference permits this method." : "");
            }
            var generated = note + playstyleLoadoutNote(playstyle, selected);
            return selected.withSelectionNote(authoredNote == null || authoredNote.trim().isEmpty() ? generated : generated + " " + authoredNote.trim());
        }

        private TaskStrategy applyPlaystyleLoadoutPolicy(TaskStrategy strategy, Preference.Playstyle playstyle) {
            if (strategy == null)
                return null;
            if (strategy.getCostPolicy() == TaskStrategy.CostPolicy.LOW_RISK || strategy.hasTag(TaskStrategy.MethodTag.WILDERNESS))
                return strategy;
            var resolved = playstyle == null ? Preference.Playstyle.FAST_XP : playstyle;
            if (resolved == Preference.Playstyle.PROFIT) {
                return strategy.withLoadoutPolicy(strategy.getArmourFocus(), TaskStrategy.CostPolicy.EFFICIENT);
            }
            return strategy.withLoadoutPolicy(TaskStrategy.ArmourFocus.DAMAGE, TaskStrategy.CostPolicy.MAX_DPS);
        }

        private String playstyleLoadoutNote(Preference.Playstyle playstyle, TaskStrategy strategy) {
            if (strategy == null || strategy.getCostPolicy() == TaskStrategy.CostPolicy.LOW_RISK || strategy.hasTag(TaskStrategy.MethodTag.WILDERNESS))
                return "";
            var resolved = playstyle == null ? Preference.Playstyle.FAST_XP : playstyle;
            return resolved == Preference.Playstyle.PROFIT ? " Profit uses the efficient-cost equipment policy." : " Fast XP forces damage gear and MAX_DPS equipment without considering item or charge" + " cost.";
        }

        private TaskStrategy selectCombatPreference(Preference.CombatStyle preference) {
            if (preference == null || preference == Preference.CombatStyle.AUTOMATIC)
                return null;
            switch(preference) {
                case PREFER_MELEE:
                    return melee;
                case PREFER_RANGED:
                    return ranged;
                case PREFER_MAGIC:
                    return magic;
                default:
                    return null;
            }
        }

        private boolean violatesEnabledPreferences(TaskStrategy strategy, Preference.Cannon cannonPreference, Preference.Burst burstPreference) {
            return strategy != null && ((cannonPreference == Preference.Cannon.NEVER && strategy.hasTag(TaskStrategy.MethodTag.CANNON)) || (burstPreference == Preference.Burst.NEVER && strategy.hasTag(TaskStrategy.MethodTag.BURST_BARRAGE)));
        }

        private TaskStrategy safePreferenceFallback(TaskStrategy current, Preference.Cannon cannonPreference, Preference.Burst burstPreference) {
            TaskStrategy[] candidates = { current, nonCannon, nonBurst, melee, ranged, magic, balanced, fast, afk, profit, cannon, burst };
            for (TaskStrategy candidate : candidates) {
                if (candidate != null && !violatesEnabledPreferences(candidate, cannonPreference, burstPreference))
                    return candidate;
            }
            return current;
        }

        private TaskStrategy selectAutomatic(Preference.Playstyle playstyle) {
            return playstyle == Preference.Playstyle.PROFIT ? profit : fast;
        }
    }
}
