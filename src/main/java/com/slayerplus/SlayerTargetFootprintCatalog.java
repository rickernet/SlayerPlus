package com.slayerplus;
import static com.slayerplus.Text.text;
import java.util.*;
class SlayerTargetFootprintCatalog{private static final Set<String>MULTI_TILE_SCYTHE_TARGETS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList(text(945),text(946),text(520),text(521),text(517),"araxxor","cerberus","dark beast","dark beasts",text(947),text(948),"giant mole",text(268),text(269),"hellhound","hellhounds","kalphite queen",text(271),text(274),text(949),"sarachnis","skotizo",text(471),text(950),"vardorvis")));
private SlayerTargetFootprintCatalog(){}static boolean allowsWeapon(String assignment,String weapon){String key=normalize(weapon);
if(!key.contains(text(312))){return true;
}return MULTI_TILE_SCYTHE_TARGETS.contains(normalize(assignment));
}static boolean isReviewedMultiTileScytheTarget(String assignment){return MULTI_TILE_SCYTHE_TARGETS.contains(normalize(assignment));
}private static String normalize(String value){if(value==null){return "";
}return value.toLowerCase(Locale.ROOT).replace('\u2019','\'').replaceAll(text(9)," ").trim();
}}
