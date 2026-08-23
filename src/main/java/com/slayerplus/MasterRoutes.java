package com.slayerplus;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    put(routes, 1, "Turael / Aya", 2931, 3536, 0, "games necklace");
    put(routes, 2, "Mazchna", 3510, 3509, 0, "eternal slayer ring", "slayer ring");
    put(routes, 3, "Vannaka", 3147, 9913, 0, "amulet of eternal glory", "amulet of glory");
    put(routes, 4, "Chaeldar", 2445, 4431, 0, "eternal slayer ring", "slayer ring");
    put(routes, 5, "Duradel", 2869, 2982, 1, "karamja gloves 4", "karamja gloves 3");
    put(routes, 6, "Nieve / Steve", 2432, 3423, 0, "eternal slayer ring", "slayer ring");
    put(routes, 7, "Krystilia", 3108, 3516, 0, "amulet of eternal glory", "amulet of glory");
    put(routes, 8, "Konar", 1308, 3786, 0, "rada s blessing 4", "rada s blessing 3");
    put(routes, 9, "Spria", 3085, 3250, 0, "amulet of eternal glory", "amulet of glory");
    put(routes, 10, "Mortimer", 2589, 8614, 0, "eternal slayer ring", "slayer ring");
    return Collections.unmodifiableMap(routes);
  }

  private static void put(
      Map<Integer, MasterRoute> routes,
      int id,
      String name,
      int x,
      int y,
      int plane,
      String... returnItemFamilies) {
    routes.put(id, new MasterRoute(id, name, new WorldPoint(x, y, plane), returnItemFamilies));
  }

  public static final class MasterRoute {
    private final int id;
    private final String name;
    private final WorldPoint destination;
    private final List<String> returnItemFamilies;

    private MasterRoute(int id, String name, WorldPoint destination, String... returnItemFamilies) {
      this.id = id;
      this.name = name;
      this.destination = destination;
      this.returnItemFamilies =
          Collections.unmodifiableList(
              Arrays.asList(
                  returnItemFamilies == null ? new String[0] : returnItemFamilies.clone()));
    }

    public int getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public WorldPoint getDestination() {
      return destination;
    }

    public List<String> getReturnItemFamilies() {
      return returnItemFamilies;
    }

    public String getReturnDestination(String itemFamily) {
      String family = itemFamily == null ? "" : itemFamily.trim().toLowerCase(Locale.ENGLISH);
      if (id == 5 && family.startsWith("karamja gloves 4")) {
        return "Slayer Master";
      }
      if (id == 5 && family.startsWith("karamja gloves 3")) {
        return "Gem Mine";
      }
      if (id == 8 && family.startsWith("rada s blessing")) {
        return "Mount Karuulm";
      }
      if (id == 10 && family.contains("slayer ring")) {
        return "Wyrmscraig Cavern";
      }
      return name;
    }
  }
}
