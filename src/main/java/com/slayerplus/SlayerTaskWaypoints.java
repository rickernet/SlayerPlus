package com.slayerplus;
import java.util.*;
import net.runelite.api.coords.WorldPoint;
final class SlayerTaskWaypoints{private static final Map<String,WorldPoint>WAYPOINTS=load();
private static final Map<String,WorldPoint>MONSTER_WAYPOINTS=loadMonster();
private SlayerTaskWaypoints(){}static WorldPoint find(String creature,String location){WorldPoint specific=creature==null?null:MONSTER_WAYPOINTS.get(singular(normalize(creature))+"|"+normalize(location));
return specific!=null?specific:find(location);
}static WorldPoint find(String location){return location==null?null:WAYPOINTS.get(normalize(location));
}private static String normalize(String value){return value==null?"":value.trim().toLowerCase(Locale.ENGLISH);
}private static String singular(String value){if(value.equals("jellies")){return "jelly";
}return value.length()>3&&value.endsWith("s")&&!value.endsWith("ss")?value.substring(0,value.length()-1):value;
}private static Map<String,WorldPoint>load(){Map<String,WorldPoint>map=new HashMap<>();
for(String[]row:ResourceTable.rows("slayer-task-waypoints.tsv",4)){map.put(normalize(row[0]),new WorldPoint(Integer.parseInt(row[1]),Integer.parseInt(row[2]),Integer.parseInt(row[3])));
}return Collections.unmodifiableMap(map);
}private static Map<String,WorldPoint>loadMonster(){Map<String,WorldPoint>map=new HashMap<>();
for(String[]row:ResourceTable.rows("slayer-task-monster-waypoints.tsv",5)){map.put(singular(normalize(row[0]))+"|"+normalize(row[1]),new WorldPoint(Integer.parseInt(row[2]),Integer.parseInt(row[3]),Integer.parseInt(row[4])));
}return Collections.unmodifiableMap(map);
}}
