# SlayerPlus

SlayerPlus is a RuneLite Slayer-planning companion. It builds task-aware gear
and 4x7 inventory recommendations from the items a player owns, creates an
organized bank tag, and coordinates reviewed travel stages with the Shortest
Path plugin.

Created and maintained by **rickernet**.

## Features

- Detects the active Slayer assignment, master, streak, and points.
- Selects task and encounter variants, including supported Slayer bosses.
- Recommends progression-aware equipment, supplies, runes, and teleports.
- Creates an organized equipment and 4x7 inventory bank-tag layout.
- Guides bank, spellbook, transport, cave, dungeon, instance, task-area, and
  return-to-master stages without performing game actions for the player.
- Highlights the currently recommended menu text and inventory item.
- Tracks bracelet-of-slaughter and expeditious-bracelet charges.

## Routing dependency

Enable RuneLite's **Shortest Path** plugin before starting a guided Slayer
session. SlayerPlus supplies reviewed destinations and manual interaction
checkpoints; Shortest Path renders the walkable portions of each route.

## Development

The project targets Java 11.

```powershell
.\gradlew.bat test
.\gradlew.bat releaseRouteCheck
.\gradlew.bat run
```

`releaseRouteCheck` is the strict pre-release routing matrix and must pass
before publishing.

## Support

Report a bug in the [SlayerPlus Discord](https://discord.gg/WZCCPsTU67).

## License

SlayerPlus is available under the BSD 2-Clause License.
