package com.slayerplus;

import java.util.*;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

public final class MasterRoutes {
  private static final Map<Integer, MasterRoute> ROUTES = createRoutes();

  private MasterRoutes() {}

  public static MasterRoute find(int masterId) {
    return ROUTES.get(masterId);
  }

  public static String getName(int masterId) {
    MasterRoute route = find(masterId);
    if (route != null) {
      return route.getName();
    }
    return masterId > 0 ? "Master ID " + masterId : "Unknown";
  }

  private static Map<Integer, MasterRoute> createRoutes() {
    Map<Integer, MasterRoute> routes = new LinkedHashMap<>();
    for (String[] row : ResourceTable.rows("slayer-master-routes.tsv", 8)) {
      int id = Integer.parseInt(row[0]);
      MasterRoute previous =
          routes.put(
              id,
              new MasterRoute(
                  id,
                  row[1],
                  new WorldPoint(
                      Integer.parseInt(row[2]), Integer.parseInt(row[3]), Integer.parseInt(row[4])),
                  new WorldPoint(Integer.parseInt(row[5]), Integer.parseInt(row[6]), 0),
                  row[7].split("\\|", -1)));
      if (previous != null) {
        throw new IllegalStateException("Duplicate Slayer master route: " + id);
      }
    }
    return Collections.unmodifiableMap(routes);
  }

  @Getter
  public static final class MasterRoute {
    private final int id;
    private final String name;
    private final WorldPoint destination;
    private final WorldPoint bank;
    private final List<String> returnItemFamilies;

    private MasterRoute(
        int id,
        String name,
        WorldPoint destination,
        WorldPoint bank,
        String... returnItemFamilies) {
      this.id = id;
      this.name = name;
      this.destination = destination;
      this.bank = bank;
      this.returnItemFamilies =
          Collections.unmodifiableList(
              Arrays.asList(
                  returnItemFamilies == null ? new String[0] : returnItemFamilies.clone()));
    }
  }
}
