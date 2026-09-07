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
    put(routes, 1, "Turael / Aya", 2931, 3536, 0, 2946, 3367, "games necklace");
    put(routes, 2, "Mazchna", 3510, 3509, 0, 3512, 3478, "eternal slayer ring", "slayer ring");
    put(
        routes,
        3,
        "Vannaka",
        3147,
        9913,
        0,
        3095,
        3494,
        "amulet of eternal glory",
        "amulet of glory");
    put(routes, 4, "Chaeldar", 2445, 4431, 0, 2383, 4459, "eternal slayer ring", "slayer ring");
    put(routes, 5, "Duradel", 2869, 2982, 1, 2852, 2955, "karamja gloves 4", "karamja gloves 3");
    put(routes, 6, "Nieve", 2432, 3423, 0, 2446, 3424, "eternal slayer ring", "slayer ring");
    put(
        routes,
        7,
        "Krystilia",
        3108,
        3516,
        0,
        3095,
        3494,
        "amulet of eternal glory",
        "amulet of glory");
    put(routes, 8, "Konar", 1308, 3786, 0, 1748, 3599, "rada s blessing 4", "rada s blessing 3");
    put(
        routes,
        9,
        "Spria",
        3085,
        3250,
        0,
        3093,
        3244,
        "amulet of eternal glory",
        "amulet of glory");
    put(routes, 10, "Mortimer", 2589, 8614, 0, 2586, 2260, "eternal slayer ring", "slayer ring");
    return Collections.unmodifiableMap(routes);
  }

  private static void put(
      Map<Integer, MasterRoute> routes,
      int id,
      String name,
      int x,
      int y,
      int plane,
      int bankX,
      int bankY,
      String... returnItemFamilies) {
    routes.put(
        id,
        new MasterRoute(
            id,
            name,
            new WorldPoint(x, y, plane),
            new WorldPoint(bankX, bankY, 0),
            returnItemFamilies));
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
