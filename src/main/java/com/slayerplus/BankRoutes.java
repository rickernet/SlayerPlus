package com.slayerplus;
import java.util.*;
import net.runelite.api.coords.WorldPoint;
final class BankRoutes{private static final List<WorldPoint>BANKS=load();
private BankRoutes(){}static WorldPoint nearest(WorldPoint from){if(from==null||BANKS.isEmpty()){return null;
}WorldPoint best=null;
long bestDistance=Long.MAX_VALUE;
for(WorldPoint bank:BANKS){long distance=chebyshev(from,bank)+(bank.getPlane()==from.getPlane()?0:1000);
if(distance<bestDistance){bestDistance=distance;
best=bank;
}}return best;
}private static long chebyshev(WorldPoint a,WorldPoint b){return Math.max(Math.abs((long)a.getX()-b.getX()),Math.abs((long)a.getY()-b.getY()));
}private static List<WorldPoint>load(){List<WorldPoint>banks=new ArrayList<>();
for(String[]row:ResourceTable.rows("slayer-common-banks.tsv",4)){banks.add(new WorldPoint(Integer.parseInt(row[1]),Integer.parseInt(row[2]),Integer.parseInt(row[3])));
}return Collections.unmodifiableList(banks);
}}
