package com.slayerplus;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class ResourceTable {
  private ResourceTable() {}

  static List<String[]> rows(String name, int... columns) {
    return rows(name, false, columns);
  }

  static List<String[]> rowsWithEscapedDelimiters(String name, int... columns) {
    return rows(name, true, columns);
  }

  private static List<String[]> rows(String name, boolean decode, int... columns) {
    String path = "/com/slayerplus/" + name;
    InputStream input = ResourceTable.class.getResourceAsStream(path);
    if (input == null) {
      throw new IllegalStateException("Missing SlayerPlus resource: " + path);
    }
    List<String[]> rows = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isEmpty() || line.charAt(0) == '#') {
          continue;
        }
        String[] row = line.split("\\t", -1);
        boolean valid = columns.length == 0;
        for (int columnsInShape : columns) {
          valid |= row.length == columnsInShape;
        }
        if (!valid) {
          throw new IllegalStateException("Invalid " + name + " row: " + line);
        }
        if (decode) {
          for (int index = 0; index < row.length; index++) {
            row[index] = restoreEscapedDelimiters(row[index]);
          }
        }
        rows.add(row);
      }
    } catch (IOException ex) {
      throw new IllegalStateException("Unable to read SlayerPlus resource: " + path, ex);
    }
    return Collections.unmodifiableList(rows);
  }

  private static String restoreEscapedDelimiters(String value) {
    return value.replace("%7C", "|").replace("%0A", "\n").replace("%09", "\t").replace("%25", "%");
  }
}
