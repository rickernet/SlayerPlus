package com.slayerplus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.gameval.ItemID;

public final class SlayerMethodRuleCatalog {

    private static final Map<String, String> BOSS_METHODS = loadBossMethods();

    private SlayerMethodRuleCatalog() {
    }

    public static MethodRules resolve(String taskName, String location, TaskStrategy strategy) {
        var task = normalize(taskName);
        var rules = defaults(taskName, location, strategy);
        applyBankTagLoadoutRules(task, location, rules, strategy);
        if (task.contains("thermonuclear smoke devil")) {
            applyThermonuclearRules(rules, strategy);
        } else if (task.equals("smoke devils") || task.equals("smoke devil")) {
            applyRegularSmokeDevilRules(rules, strategy);
        }
        if ((task.equals("cave kraken") || task.equals("cave krakens")) && (strategy == null || !strategy.isBoss())) {
            rules.taskMethod("Disturb a regular whirlpool, keep Protect from Magic active, and use the selected" + " powered Magic weapon.").damageControl(MethodRules.DamageControl.PRAYER_PROTECTED).restore("prayer potion", "super restore").restoreSlots(5).foodSlots(2).reservedLootSlots(16);
        }
        if (task.equals("dark beast") || task.equals("dark beasts")) {
            var cannonMethod = strategy != null && strategy.hasTag(TaskStrategy.MethodTag.CANNON);
            rules.damageControl(MethodRules.DamageControl.DIRECT_DAMAGE).restore("prayer potion", "super restore").restoreSlots(cannonMethod ? 5 : 4).foodSlots(cannonMethod ? 3 : 2).reservedLootSlots(cannonMethod ? 8 : 12);
        }
        applyResearchedSpellbookRules(rules, strategy);
        applyWikiArceuusUtilityRules(task, rules, strategy);
        var result = rules.build();
        result.validateFor(taskName, strategy);
        return result;
    }

    private static MethodRules.Builder defaults(String taskName, String location, TaskStrategy strategy) {
        var reviewed = strategy != null && strategy.isReviewed();
        var research = SlayerTaskStrategyCatalog.getResearchRecord(taskName);
        var reviewDate = research == null ? "catalog migration" : safe(research.getReviewDate());
        var damageControl = resolveDamageControl(strategy);
        var layout = resolveLayoutProfile(location, strategy, damageControl);
        var rules = MethodRules.builder().coverageKey(buildCoverageKey(taskName, location, strategy)).taskMethod(resolveConciseTaskMethod(taskName, strategy)).combatStyle(strategy == null ? null : strategy.getCombatStyle()).research(wikiSource(taskName, strategy), reviewDate, reviewed).damageControl(damageControl).layout(layout);
        var restoreResearch = strategy == null ? "" : normalize(strategy.getMethod() + " " + strategy.getRationale());
        if (restoreResearch.contains("super restore")) {
            rules.restore("super restore", "prayer potion");
        } else if (restoreResearch.contains("prayer potion")) {
            rules.restore("prayer potion", "super restore");
        } else if (SlayerBossInventoryCatalog.getReviewedBossKeys().contains(SlayerBossInventoryCatalog.canonicalBossKey(taskName))) {
            rules.restore("super restore", "prayer potion");
        } else {
            rules.restore("prayer potion", "super restore");
        }
        if (strategy == null) {
            return rules;
        }
        if (damageControl == MethodRules.DamageControl.PRAYER_PROTECTED || damageControl == MethodRules.DamageControl.SAFESPOT || damageControl == MethodRules.DamageControl.FREEZE_SAFESPOT) {
            rules.foodSlots(0);
        }
        if (strategy.hasTag(TaskStrategy.MethodTag.CANNON)) {
            rules.cannon(true, auditedCannonballQuantity(taskName));
        }
        if (strategy.hasTag(TaskStrategy.MethodTag.BURST_BARRAGE)) {
            var dustDevilMethod = normalize(taskName).equals("dust devils");
            var nechryaelMethod = normalize(taskName).equals("nechryael");
            var tzhaarBloodMethod = normalize(taskName).equals("tzhaar");
            var dustDevilProfitBurst = dustDevilMethod && strategy.getCostPolicy() == TaskStrategy.CostPolicy.EFFICIENT;
            rules.layout(MethodRules.LayoutProfile.BARRAGE).spell(MethodRules.Spellbook.ANCIENT, tzhaarBloodMethod ? "Blood Barrage" : dustDevilProfitBurst ? "Ice Burst" : "Ice Barrage or Ice Burst").requiredItem(tzhaarBloodMethod ? "Fire runes" : "Water runes", 5000, MethodRules.InventoryGroup.RUNES_AMMO, tzhaarBloodMethod ? "fire rune" : "water rune");
            if (dustDevilProfitBurst) {
                rules.requiredItem("Chaos runes", 4000, MethodRules.InventoryGroup.RUNES_AMMO, "chaos rune");
            } else {
                rules.requiredItem("Blood runes", 2000, MethodRules.InventoryGroup.RUNES_AMMO, "blood rune");
            }
            rules.requiredItem("Death runes", 4000, MethodRules.InventoryGroup.RUNES_AMMO, "death rune");
            rules.ownedOnlyItem(PotionPolicy.GOADING_DISPLAY, 1, MethodRules.InventoryGroup.UTILITY, PotionPolicy.goadingAlternatives());
            rules.requiredItem("Tagging darts/knives", 100, MethodRules.InventoryGroup.UTILITY, "mithril dart", "steel dart", "iron dart", "bronze dart", "adamant dart", "mithril knife", "steel knife", "iron knife", "bronze knife", "adamant knife");
        }
        if (strategy.fillsInventoryToTarget()) {
            rules.inventoryTarget(strategy.getInventoryTargetSlots());
            if (!preventsExpectedDamage(damageControl)) {
                rules.fillRemainingWithFood();
            }
        }
        return rules;
    }

    static int auditedCannonballQuantity(String taskName) {
        var task = normalize(taskName);
        if (matches(task, "smoke devils", "bloodveld", "araxytes")) {
            return 2000;
        }
        if (matches(task, "dagannoth", "kalphites", "suqahs", "trolls", "warped creatures", "gryphons", "scabarites")) {
            return 1500;
        }
        return 1000;
    }

    private static void applyBankTagLoadoutRules(String task, String location, MethodRules.Builder rules, TaskStrategy strategy) {
        if (strategy == null) {
            return;
        }
        rules.inventoryTarget(28);
        if (SlayerBossInventoryCatalog.getReviewedBossKeys().contains(SlayerBossInventoryCatalog.canonicalBossKey(task))) {
            applyBossBankTagRules(task, rules, strategy);
            return;
        }
        if (strategy.hasTag(TaskStrategy.MethodTag.TURAEL_POINT_BOOST)) {
            applyTuraelBoostRules(task, rules, strategy);
            return;
        }
        if (isWildernessEncounter(task, location, strategy)) {
            rules.restore("blighted super restore", "super restore").food("Blighted high-healing food", "blighted anglerfish", "blighted manta ray", "blighted karambwan", "anglerfish", "manta ray", "cooked karambwan").reservedLootSlots(8).restoreSlots(3).foodSlots(preventsExpectedDamage(resolveDamageControl(strategy)) ? 0 : 6).requiredItem("Looting bag", 1, MethodRules.InventoryGroup.UTILITY, "looting bag").requiredItem("Level-30 Wilderness escape", 1, MethodRules.InventoryGroup.UTILITY, "royal seed pod", "seed pod", "dragonstone teleport scroll", "amulet of glory", "ring of wealth");
            if (isDragonEncounter(task)) {
                rules.requiredSlots(strategy.getCombatStyle() == TaskStrategy.CombatStyle.MELEE ? PotionPolicy.EXTENDED_SUPER_ANTIFIRE_DISPLAY : PotionPolicy.EXTENDED_ANTIFIRE_DISPLAY, 1, MethodRules.InventoryGroup.PROTECTION, strategy.getCombatStyle() == TaskStrategy.CombatStyle.MELEE ? PotionPolicy.potionOnlyAntifireAlternatives() : PotionPolicy.shieldedAntifireAlternatives());
            }
            applyGodWarsProtection(task, location, rules);
            return;
        }
        applyGodWarsProtection(task, location, rules);
        if (task.equals("ankou")) {
            applyAnkouBankTagRules(location, rules, strategy);
            return;
        }
        if (strategy.hasTag(TaskStrategy.MethodTag.BURST_BARRAGE)) {
            rules.reservedLootSlots(8).restoreSlots(5).foodSlots(0);
            return;
        }
        if (task.equals("bloodveld") || task.equals("bloodvelds")) {
            applyBloodveldBankTagRules(location, rules, strategy);
            return;
        }
        if (task.equals("kalphite") || task.equals("kalphites")) {
            SlayerRegularInventoryAuditCatalog.apply(task, rules, strategy);
            return;
        }
        if (strategy.hasTag(TaskStrategy.MethodTag.CANNON)) {
            rules.reservedLootSlots(8).restoreSlots(3).foodSlots(preventsExpectedDamage(resolveDamageControl(strategy)) ? 0 : 6);
            return;
        }
        if (task.equals("skeletal wyvern") || task.equals("skeletal wyverns")) {
            SlayerRegularInventoryAuditCatalog.apply(task, rules, strategy);
            return;
        }
        if (task.equals("fossil island wyvern") || task.equals("fossil island wyverns")) {
            SlayerRegularInventoryAuditCatalog.apply(task, rules, strategy);
            return;
        }
        if (isDragonEncounter(task)) {
            var melee = strategy.getCombatStyle() == TaskStrategy.CombatStyle.MELEE;
            var reviewedChromatic = matches(task, "blue dragon", "blue dragons", "black dragon", "black dragons", "green dragon", "green dragons", "red dragon", "red dragons");
            var metal = isMetalDragonTask(task);
            if (reviewedChromatic || metal) {
                rules.requiredSlots(melee ? PotionPolicy.EXTENDED_SUPER_ANTIFIRE_DISPLAY : PotionPolicy.EXTENDED_ANTIFIRE_DISPLAY, metal ? 2 : 1, MethodRules.InventoryGroup.PROTECTION, melee ? PotionPolicy.potionOnlyAntifireAlternatives() : PotionPolicy.shieldedAntifireAlternatives());
            }
            if (task.equals("blue dragon") || task.equals("blue dragons")) {
                var safespot = strategy != null && (strategy.getCombatStyle() == TaskStrategy.CombatStyle.RANGED || strategy.getCombatStyle() == TaskStrategy.CombatStyle.MAGIC);
                rules.reservedLootSlots(safespot ? 24 : 12).restoreSlots(0).foodSlots(safespot ? 0 : 6);
                return;
            }
            if (metal) {
                var lithkren = normalize(location).contains("lithkren");
                rules.reservedLootSlots(lithkren ? 6 : 12).restoreSlots(lithkren ? 4 : melee ? 4 : 0).foodSlots(lithkren ? 8 : melee ? 4 : 0).includeStyleBoost(true);
                if (lithkren) {
                    rules.requiredItem("Insulated boots", 1, MethodRules.InventoryGroup.PROTECTION, "insulated boots").requiredItem("Poison protection", 1, MethodRules.InventoryGroup.PROTECTION, "antidote plus plus", "antidote plus", "superantipoison", "antipoison");
                }
                return;
            }
            rules.reservedLootSlots(10).restoreSlots(3).foodSlots(preventsExpectedDamage(resolveDamageControl(strategy)) ? 0 : 6);
            return;
        }
        SlayerRegularInventoryAuditCatalog.apply(task, rules, strategy);
    }

    private static void applyTuraelBoostRules(String task, MethodRules.Builder rules, TaskStrategy strategy) {
        var boostEntry = TuraelBoost.find(task);
        var cannon = strategy.hasTag(TaskStrategy.MethodTag.CANNON) && boostEntry != null && boostEntry.supportsCannon();
        rules.restore("prayer potion", "super restore").restoreSlots(0).foodSlots(2).reservedLootSlots(cannon ? 12 : 20).includeStyleBoost(true);
        if (cannon) {
            rules.cannon(true, 300);
        } else {
            rules.cannon(false, 0);
        }
        if (matches(task, "cave bug", "cave bugs", "cave slime", "cave slimes")) {
            rules.requiredItem("Safe light source", 1, MethodRules.InventoryGroup.UTILITY, "bruma torch", "bullseye lantern", "emerald lantern", "sapphire lantern", "oil lantern", "candle lantern");
        }
        if (matches(task, "cave crawler", "cave crawlers", "cave slime", "cave slimes")) {
            rules.requiredItem("Poison protection", 1, MethodRules.InventoryGroup.PROTECTION, "antidote plus plus", "antidote plus", "superantipoison", "antipoison");
        }
        if (matches(task, "lizard", "lizards")) {
            rules.requiredItem("Ice coolers", 30, MethodRules.InventoryGroup.UTILITY, "ice cooler").requiredItem("Desert heat protection", 1, MethodRules.InventoryGroup.PROTECTION, "circlet of water", "desert amulet 4", "waterskin 4", "waterskin 3", "waterskin 2", "waterskin 1");
        }
        if (matches(task, "skeleton", "skeletons")) {
            rules.requiredItem("Rope", 1, MethodRules.InventoryGroup.UTILITY, "rope");
        }
    }

    private static boolean applyGodWarsProtection(String task, String location, MethodRules.Builder rules) {
        var area = normalize(location);
        if (!area.contains("god wars") || !(task.equals("aviansie") || task.equals("aviansies") || task.equals("spiritual creature") || task.equals("spiritual creatures"))) {
            return false;
        }
        rules.requiredItem("Armadyl protection", 1, MethodRules.InventoryGroup.PROTECTION, "honourable blessing", "armadyl pendant", "book of law", "armadyl bracers", "armadyl d hide boots", "armadyl d hide body", "armadyl chaps", "armadyl cloak").requiredItem("Zamorak protection", 1, MethodRules.InventoryGroup.PROTECTION, "unholy blessing", "unholy book", "zamorak bracers", "zamorak d hide boots", "zamorak d hide body", "zamorak chaps", "zamorak cloak");
        return true;
    }

    private static void applyBloodveldBankTagRules(String selectedLocation, MethodRules.Builder rules, TaskStrategy strategy) {
        var location = normalize(selectedLocation);
        var longPrayerTrip = location.contains("meiyerditch") || location.contains("buccaneer");
        var catacombs = location.contains("catacombs");
        var protectedMethod = preventsExpectedDamage(resolveDamageControl(strategy));
        rules.restore("prayer potion", "super restore").restoreSlots(longPrayerTrip ? 6 : catacombs ? 5 : protectedMethod && strategy.getArmourFocus() != TaskStrategy.ArmourFocus.PRAYER ? 0 : 4).foodSlots(protectedMethod ? 0 : 6).reservedLootSlots(strategy.hasTag(TaskStrategy.MethodTag.CANNON) ? 8 : 12).includeStyleBoost(true).spell(MethodRules.Spellbook.STANDARD, "High Level Alchemy").runePouch(true).pouchRune(ItemID.NATURERUNE, "Nature", 200).pouchRune(ItemID.FIRERUNE, "Fire", 1000).requiredItem("Ash sanctifier", 1, MethodRules.InventoryGroup.UTILITY, "ash sanctifier").requiredItem("Soul bearer", 1, MethodRules.InventoryGroup.UTILITY, "soul bearer");
        if (catacombs && strategy.hasTag(TaskStrategy.MethodTag.VENATOR)) {
            rules.requiredItem(PotionPolicy.GOADING_DISPLAY, 1, MethodRules.InventoryGroup.UTILITY, PotionPolicy.goadingAlternatives());
        }
    }

    private static void applyAnkouBankTagRules(String selectedLocation, MethodRules.Builder rules, TaskStrategy strategy) {
        var location = normalize(selectedLocation);
        var safespot = location.contains("stronghold of security");
        var cannon = strategy.hasTag(TaskStrategy.MethodTag.CANNON);
        var barrage = strategy.hasTag(TaskStrategy.MethodTag.BURST_BARRAGE);
        rules.restore("prayer potion", "super restore").restoreSlots(safespot ? 0 : cannon ? 3 : 5).foodSlots(barrage || safespot ? 0 : 2).reservedLootSlots(barrage || cannon ? 8 : safespot ? 20 : 14).includeStyleBoost(true);
    }

    private static void applyBossBankTagRules(String task, MethodRules.Builder rules, TaskStrategy strategy) {
        if (!SlayerBossInventoryCatalog.apply(task, rules, strategy)) {
            throw new IllegalStateException("Missing researched boss inventory policy: " + task);
        }
    }

    private static boolean isWildernessEncounter(String task, String location, TaskStrategy strategy) {
        return strategy != null && strategy.hasTag(TaskStrategy.MethodTag.WILDERNESS) || isWilderness(location) || matches(task, "callisto", "venenatis", "vet ion", "vet'ion", "scorpia", "chaos elemental", "the chaos elemental", "chaos fanatic", "the chaos fanatic", "crazy archaeologists", "revenants", "lava dragons", "green dragons", "mammoths", "rogues", "magic axes", "dark warriors", "earth warriors", "ents");
    }

    private static boolean isDragonEncounter(String task) {
        return task.contains("dragon") || task.contains("wyvern");
    }

    private static boolean isMetalDragonTask(String task) {
        return matches(task, "metal dragon", "metal dragons", "bronze dragon", "bronze dragons", "iron dragon", "iron dragons", "steel dragon", "steel dragons", "mithril dragon", "mithril dragons", "adamant dragon", "adamant dragons", "rune dragon", "rune dragons");
    }

    private static void applyResearchedSpellbookRules(MethodRules.Builder rules, TaskStrategy strategy) {
        if (!selectedMethodRequiresStandardSpellbook(strategy)) {
            return;
        }
        var method = normalize(strategy.getMethod());
        if (method.contains("magic dart")) {
            rules.spell(MethodRules.Spellbook.STANDARD, "Magic Dart").runePouch(true).pouchRune(ItemID.MINDRUNE, "Mind", 4).pouchRune(ItemID.DEATHRUNE, "Death", 1);
            return;
        }
        if (mentionsWaterAndFireSpells(method)) {
            rules.spell(MethodRules.Spellbook.STANDARD, "Water / Fire spells").runePouch(true).pouchRune(ItemID.AIRRUNE, "Air", 5000).pouchRune(ItemID.WATERRUNE, "Water", 5000).pouchRune(ItemID.FIRERUNE, "Fire", 5000).pouchRune(ItemID.WRATHRUNE, "Wrath", 2000);
            return;
        }
        if (method.contains("water blast")) {
            rules.spell(MethodRules.Spellbook.STANDARD, "Water Blast").runePouch(true).pouchRune(ItemID.AIRRUNE, "Air", 5000).pouchRune(ItemID.WATERRUNE, "Water", 5000).pouchRune(ItemID.DEATHRUNE, "Death", 2000);
            return;
        }
        if (method.contains("water spell") || method.contains("water spells")) {
            rules.spell(MethodRules.Spellbook.STANDARD, "Water Surge or Water Wave").runePouch(true).pouchRune(ItemID.AIRRUNE, "Air", 5000).pouchRune(ItemID.WATERRUNE, "Water", 5000).pouchRune(ItemID.WRATHRUNE, "Wrath", 2000).pouchRune(ItemID.BLOODRUNE, "Blood", 2000);
            return;
        }
        if (method.contains("earth wave")) {
            rules.spell(MethodRules.Spellbook.STANDARD, "Earth Wave").runePouch(true).pouchRune(ItemID.AIRRUNE, "Air", 5000).pouchRune(ItemID.EARTHRUNE, "Earth", 5000).pouchRune(ItemID.BLOODRUNE, "Blood", 2000);
            return;
        }
        if (method.contains("earth spell") || method.contains("earth spells")) {
            rules.spell(MethodRules.Spellbook.STANDARD, "Earth Surge or Earth Wave").runePouch(true).pouchRune(ItemID.AIRRUNE, "Air", 5000).pouchRune(ItemID.EARTHRUNE, "Earth", 5000).pouchRune(ItemID.WRATHRUNE, "Wrath", 2000).pouchRune(ItemID.BLOODRUNE, "Blood", 2000);
            return;
        }
        if (method.contains("fire spell") || method.contains("fire spells")) {
            rules.spell(MethodRules.Spellbook.STANDARD, "Fire Surge or Fire Wave").runePouch(true).pouchRune(ItemID.AIRRUNE, "Air", 5000).pouchRune(ItemID.FIRERUNE, "Fire", 5000).pouchRune(ItemID.WRATHRUNE, "Wrath", 2000).pouchRune(ItemID.BLOODRUNE, "Blood", 2000);
            return;
        }
        if (method.contains("air spell") || method.contains("air spells") || method.contains("wind spell") || method.contains("wind spells")) {
            rules.spell(MethodRules.Spellbook.STANDARD, "Wind Surge or Wind Wave").runePouch(true).pouchRune(ItemID.AIRRUNE, "Air", 5000).pouchRune(ItemID.WRATHRUNE, "Wrath", 2000).pouchRune(ItemID.BLOODRUNE, "Blood", 2000);
        }
    }

    static boolean selectedMethodRequiresStandardSpellbook(TaskStrategy strategy) {
        if (strategy == null) {
            return false;
        }
        var method = normalize(strategy.getMethod());
        return method.contains("magic dart") || mentionsWaterAndFireSpells(method) || method.contains("water blast") || method.contains("water spell") || method.contains("earth wave") || method.contains("earth spell") || method.contains("fire spell") || method.contains("air spell") || method.contains("wind spell");
    }

    private static boolean mentionsWaterAndFireSpells(String method) {
        return method != null && method.contains("water") && method.contains("fire") && method.contains("spell");
    }

    private static void applyWikiArceuusUtilityRules(String task, MethodRules.Builder rules, TaskStrategy strategy) {
        if (strategy == null || strategy.hasTag(TaskStrategy.MethodTag.BURST_BARRAGE) || task.contains("thermonuclear smoke devil") || selectedMethodRequiresStandardSpellbook(strategy)) {
            return;
        }
        var style = strategy.getCombatStyle();
        var thralls = false;
        var deathCharge = false;
        var ward = false;
        if ((task.equals("abyssal demons") || task.equals("abyssal demon")) && style == TaskStrategy.CombatStyle.MELEE) {
            thralls = true;
            deathCharge = true;
        } else if ((task.equals("nechryael") || task.equals("nechryaels")) && style == TaskStrategy.CombatStyle.MELEE) {
            thralls = true;
            deathCharge = true;
        } else if ((task.equals("trolls") || task.equals("troll")) && style == TaskStrategy.CombatStyle.MELEE) {
            thralls = true;
            deathCharge = true;
        }
        if (matches(task, "cerberus")) {
            thralls = true;
        } else if (matches(task, "amoxliatl", "araxxor", "sarachnis", "vardorvis", "grotesque guardians", "the grotesque guardians", "zulrah", "kalphite queen", "the kalphite queen")) {
            thralls = true;
            deathCharge = true;
        } else if (matches(task, "phantom muspah", "the phantom muspah")) {
            thralls = true;
            deathCharge = true;
        } else if (matches(task, "whisperer", "the whisperer")) {
            thralls = true;
        } else if (matches(task, "general graardor")) {
            thralls = true;
        } else if (matches(task, "alchemical hydra", "the alchemical hydra")) {
            thralls = true;
            deathCharge = true;
        } else if (matches(task, "tormented demons", "tormented demon")) {
            thralls = true;
            deathCharge = true;
        } else if (matches(task, "venenatis", "spindel")) {
            thralls = strategy.isBoss();
            deathCharge = strategy.isBoss();
        }
        if (!thralls) {
            return;
        }
        rules.taskMethod(withThrallNote(resolveConciseTaskMethod(task, strategy), deathCharge, ward)).arceuusUtility(thralls, deathCharge, ward).includeRunePouchForMagic(true).requiredItem("Book of the dead", 1, MethodRules.InventoryGroup.UTILITY, "book of the dead").requiredItem("Rune pouch", 1, MethodRules.InventoryGroup.UTILITY, "divine rune pouch", "rune pouch").requiredItem("Fire runes", 1000, MethodRules.InventoryGroup.RUNES_AMMO, "fire rune").requiredItem("Cosmic runes", 500, MethodRules.InventoryGroup.RUNES_AMMO, "cosmic rune").requiredItem("Blood runes", 500, MethodRules.InventoryGroup.RUNES_AMMO, "blood rune");
        if (deathCharge) {
            rules.requiredItem("Death runes", 500, MethodRules.InventoryGroup.RUNES_AMMO, "death rune").requiredItem("Soul runes", 500, MethodRules.InventoryGroup.RUNES_AMMO, "soul rune");
        }
        if (ward) {
        }
    }

    private static String withThrallNote(String method, boolean deathCharge, boolean ward) {
        var base = concise(method);
        if (base.endsWith(".")) {
            base = base.substring(0, base.length() - 1);
        }
        var suffix = ward ? "; keep a thrall active and use Ward of Arceuus/Death Charge when useful" : deathCharge ? "; keep a thrall active and use Death Charge" : "; keep a thrall active";
        return concise(base + suffix);
    }

    private static boolean matches(String value, String... names) {
        for (String name : names) {
            if (value.equals(normalize(name))) {
                return true;
            }
        }
        return false;
    }

    private static void applyRegularSmokeDevilRules(MethodRules.Builder rules, TaskStrategy strategy) {
        var style = strategy == null ? null : strategy.getCombatStyle();
        var barrage = strategy != null && strategy.hasTag(TaskStrategy.MethodTag.BURST_BARRAGE);
        if (style == TaskStrategy.CombatStyle.MAGIC && barrage) {
            rules.taskMethod("Keep Protect from Missiles active, stack the group with a cannon, then Ice Barrage" + " them.").layout(MethodRules.LayoutProfile.BARRAGE);
        } else if (style == TaskStrategy.CombatStyle.RANGED) {
            rules.taskMethod("Keep Protect from Missiles active and use the selected ranged setup; cannon where" + " allowed.");
        } else {
            rules.taskMethod("Keep Protect from Missiles active and use the selected setup; cannon where allowed.");
        }
        rules.damageControl(MethodRules.DamageControl.PRAYER_PROTECTED).restore("prayer potion", "super restore").foodSlots(0);
    }

    private static void applyThermonuclearRules(MethodRules.Builder rules, TaskStrategy strategy) {
        var style = strategy == null ? null : strategy.getCombatStyle();
        var authored = strategy == null ? "" : safe(strategy.getMethod());
        if (style == TaskStrategy.CombatStyle.MAGIC && (contains(authored, "blood barrage") || contains(authored, "blood burst"))) {
            rules.taskMethod("Use Blood Barrage for sustain; protection prayers do not reduce Thermy's typeless" + " attacks.").damageControl(MethodRules.DamageControl.DIRECT_DAMAGE).layout(MethodRules.LayoutProfile.BARRAGE).spell(MethodRules.Spellbook.ANCIENT, "Blood Barrage or Blood Burst").restore("super restore", "prayer potion").restoreSlots(4).foodSlots(4).reservedLootSlots(6);
            return;
        }
        if (style == TaskStrategy.CombatStyle.MAGIC) {
            rules.taskMethod("Freeze Thermy, move beyond its 8-tile range, then use Shadow on Longrange; refreeze" + " before it reaches you.").damageControl(MethodRules.DamageControl.FREEZE_SAFESPOT).layout(MethodRules.LayoutProfile.FREEZE_MAGIC).spell(MethodRules.Spellbook.ANCIENT, "Best available ice spell").restore("super restore", "prayer potion").restoreSlots(5).foodSlots(2).inventoryTarget(28).reservedLootSlots(8).runePouch(true).pouchRune(ItemID.WATERRUNE, "Water", 5000).pouchRune(ItemID.BLOODRUNE, "Blood", 2000).pouchRune(ItemID.DEATHRUNE, "Death", 4000).requiredItem("Ice ancient sceptre", 1, MethodRules.InventoryGroup.SWITCH, "ice ancient sceptre", "ancient sceptre");
            return;
        }
        if (style == TaskStrategy.CombatStyle.RANGED) {
            rules.taskMethod("Attack from range and step underneath between attacks when Thermy closes in;" + " protection prayers do not help.").damageControl(MethodRules.DamageControl.STEP_UNDER).layout(MethodRules.LayoutProfile.BOSS).restore("super restore", "prayer potion").restoreSlots(5).foodSlots(8).reservedLootSlots(5);
            return;
        }
        rules.taskMethod("Attack once, step underneath between attacks, then step back out when ready;" + " protection prayers do not help.").damageControl(MethodRules.DamageControl.STEP_UNDER).layout(MethodRules.LayoutProfile.BOSS).restore("super restore", "prayer potion").restoreSlots(5).foodSlots(8).reservedLootSlots(5);
    }

    private static MethodRules.DamageControl resolveDamageControl(TaskStrategy strategy) {
        if (strategy == null) {
            return MethodRules.DamageControl.STRATEGY_DEFINED;
        }
        if (strategy.getDamageProfile() == TaskStrategy.DamageProfile.ZERO_WHILE_PROTECTED) {
            return MethodRules.DamageControl.PRAYER_PROTECTED;
        }
        if (strategy.getDamageProfile() == TaskStrategy.DamageProfile.ZERO_WHILE_SAFESPOTTING) {
            return MethodRules.DamageControl.SAFESPOT;
        }
        if (strategy.hasTag(TaskStrategy.MethodTag.SAFESPOT)) {
            return MethodRules.DamageControl.SAFESPOT;
        }
        if (strategy.isBoss()) {
            return MethodRules.DamageControl.BOSS_MECHANICS;
        }
        return MethodRules.DamageControl.DIRECT_DAMAGE;
    }

    private static MethodRules.LayoutProfile resolveLayoutProfile(String location, TaskStrategy strategy, MethodRules.DamageControl damageControl) {
        if (isWilderness(location)) {
            return MethodRules.LayoutProfile.WILDERNESS;
        }
        if (strategy == null) {
            return MethodRules.LayoutProfile.STANDARD;
        }
        if (strategy.isBoss()) {
            return strategy.getCombatStyle() == TaskStrategy.CombatStyle.HYBRID ? MethodRules.LayoutProfile.HYBRID_BOSS : MethodRules.LayoutProfile.BOSS;
        }
        if (strategy.hasTag(TaskStrategy.MethodTag.BURST_BARRAGE)) {
            return MethodRules.LayoutProfile.BARRAGE;
        }
        if (strategy.hasTag(TaskStrategy.MethodTag.CANNON)) {
            return MethodRules.LayoutProfile.CANNON;
        }
        if (damageControl == MethodRules.DamageControl.PRAYER_PROTECTED) {
            return MethodRules.LayoutProfile.PRAYER_TASK;
        }
        return MethodRules.LayoutProfile.STANDARD;
    }

    private static String resolveConciseTaskMethod(String taskName, TaskStrategy strategy) {
        if (strategy != null && strategy.isBoss()) {
            var bossMethod = bossMethod(taskName, strategy);
            if (!bossMethod.isEmpty()) {
                return concise(bossMethod);
            }
        }
        if (strategy != null && !safe(strategy.getMethod()).isEmpty()) {
            return concise(strategy.getMethod());
        }
        return "Use the reviewed setup and follow the encounter's required protection and positioning.";
    }

    private static String bossMethod(String bossName, TaskStrategy strategy) {
        var boss = normalize(bossName);
        var style = strategy == null ? null : strategy.getCombatStyle();
        if (boss.equals("k ril tsutsaroth")) {
            return style == TaskStrategy.CombatStyle.RANGED ? "Bind and kite K'ril with Scorching bow, Protect from Missiles, then stack the" + " bodyguards and Blood Barrage them for healing." : "Use demonbane melee, Protect from Melee, and step under or reposition between" + " attacks.";
        }
        if (boss.equals("general graardor")) {
            return style == TaskStrategy.CombatStyle.RANGED ? "Use the selected door-altar or kite cycle, keep the tile rhythm, and clean up the" + " bodyguards." : "Protect from Melee, step under as required, and clean up the bodyguards after the" + " kill.";
        }
        return BOSS_METHODS.getOrDefault(boss, "");
    }

    private static Map<String, String> loadBossMethods() {
        Map<String, String> methods = new LinkedHashMap<>();
        for (String[] row : ResourceTable.rows("slayer-boss-method-guidance.tsv", 2)) {
            var previous = methods.put(normalize(row[0]), row[1]);
            if (previous != null) {
                throw new IllegalStateException("Duplicate boss method: " + row[0]);
            }
        }
        return Collections.unmodifiableMap(methods);
    }

    private static String concise(String value) {
        var clean = safe(value).replaceAll("\\s+", " ");
        if (clean.isEmpty()) {
            return clean;
        }
        var secondSentence = clean.indexOf(". ");
        if (secondSentence > 35 && secondSentence + 1 <= MethodRules.MAX_TASK_METHOD_LENGTH) {
            clean = clean.substring(0, secondSentence + 1);
        }
        if (clean.length() > MethodRules.MAX_TASK_METHOD_LENGTH) {
            var cut = clean.lastIndexOf(' ', MethodRules.MAX_TASK_METHOD_LENGTH - 1);
            if (cut < 80) {
                cut = MethodRules.MAX_TASK_METHOD_LENGTH - 1;
            }
            clean = clean.substring(0, cut).trim();
        }
        var last = clean.charAt(clean.length() - 1);
        return last == '.' || last == '!' || last == '?' ? clean : clean + ".";
    }

    private static String buildCoverageKey(String taskName, String location, TaskStrategy strategy) {
        return normalize(taskName) + "|" + normalize(location) + "|" + (strategy == null ? "none" : strategy.getCombatStyle().name()) + "|" + normalize(strategy == null ? "" : strategy.getMethod());
    }

    private static String wikiSource(String taskName, TaskStrategy strategy) {
        var name = safe(taskName).replaceFirst("(?i)^the\\s+", "");
        return strategy != null && strategy.isBoss() ? "OSRS Wiki: " + name + "/Strategies" : "OSRS Wiki: Slayer task/" + name;
    }

    private static boolean preventsExpectedDamage(MethodRules.DamageControl value) {
        return value == MethodRules.DamageControl.PRAYER_PROTECTED || value == MethodRules.DamageControl.SAFESPOT || value == MethodRules.DamageControl.FREEZE_SAFESPOT;
    }

    private static boolean isWilderness(String value) {
        var normalized = normalize(value);
        return normalized.contains("wilderness") || normalized.contains("revenant caves") || normalized.contains("deep wild");
    }

    private static boolean contains(String text, String part) {
        return safe(text).toLowerCase(Locale.ENGLISH).contains(safe(part).toLowerCase(Locale.ENGLISH));
    }

    private static String normalize(String value) {
        return SlayerText.encounter(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
