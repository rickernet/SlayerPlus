package com.slayerplus;
import java.util.*;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;
final class BankRoutes{private static final List<WorldPoint>BANKS=load();
static final String CONFIG_KEY="visitedBanksV1";
private static final int LIMIT=512;
private String profile;
private final Set<WorldPoint>visited=new LinkedHashSet<>();
void useProfile(String next,Supplier<String>saved){if(Objects.equals(profile,next)){return;
}profile=next;
visited.clear();
if(next==null){return;
}String value=saved.get();
if(value==null){return;
}for(String entry:value.split(";")){if(visited.size()>=LIMIT){break;
}String[]parts=entry.split(",");
if(parts.length!=3){continue;
}try{WorldPoint point=new WorldPoint(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));
if(valid(point)){visited.add(point);
}}catch(NumberFormatException ignored){}}
}
boolean remember(WorldPoint point,boolean instanced){if(profile==null||instanced||!valid(point)||visited.size()>=LIMIT){return false;
}for(WorldPoint known:visited){if(known.getPlane()==point.getPlane()&&chebyshev(known,point)<=8){return false;
}}return visited.add(point);
}
String serialize(){StringJoiner value=new StringJoiner(";");
for(WorldPoint point:visited){value.add(point.getX()+","+point.getY()+","+point.getPlane());
}return value.toString();
}
private static boolean valid(WorldPoint point){return point!=null&&point.getX()>0&&point.getX()<16384&&point.getY()>0&&point.getY()<16384&&point.getPlane()>=0&&point.getPlane()<4;
}
WorldPoint nearest(WorldPoint from){if(from==null){return null;
}WorldPoint best=null;
long bestDistance=Long.MAX_VALUE;
for(Collection<WorldPoint>banks:Arrays.asList(visited,BANKS)){for(WorldPoint bank:banks){long distance=chebyshev(from,bank)+(bank.getPlane()==from.getPlane()?0:1000);
if(distance<bestDistance){bestDistance=distance;
best=bank;
}}}return best;
}private static long chebyshev(WorldPoint a,WorldPoint b){return Math.max(Math.abs((long)a.getX()-b.getX()),Math.abs((long)a.getY()-b.getY()));
}private static List<WorldPoint>load(){List<WorldPoint>banks=new ArrayList<>();
for(String[]row:ResourceTable.rows("slayer-common-banks.tsv",4)){banks.add(new WorldPoint(Integer.parseInt(row[1]),Integer.parseInt(row[2]),Integer.parseInt(row[3])));
}return Collections.unmodifiableList(banks);
}}
