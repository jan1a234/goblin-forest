# Goblin Forest

Fabric-Mod für Minecraft Java 26.2: 1-gegen-1-Lane-Strategie mit Goblin-Clans, inspiriert von *Clan Wars: Goblin Forest*.
Das Spieldesign steht in [docs/DESIGN.md](docs/DESIGN.md).

## Installieren

1. Die neueste `goblinforest-….jar` unter [Releases](https://github.com/jan1a234/goblin-forest/releases) herunterladen.
   Das Release **dev** ist immer der neueste Stand von `main`, Releases mit `v` davor (z. B. `v0.1.0`) sind fertige Versionen.
2. Die Jar zusammen mit der passenden [Fabric API](https://modrinth.com/mod/fabric-api) (für 26.2) in den `mods`-Ordner legen,
   und zwar **auf dem Server und bei beiden Spielern**.
3. Voraussetzungen: Minecraft Java 26.2, Fabric Loader 0.19.5 oder neuer, Java 25.

Test im Spiel: `/gf` zeigt die installierte Version.

## Selbst bauen

```
./gradlew build
```

Die Jar liegt danach in `build/libs/`. Benötigt Java 25.

## Release erstellen

GitHub Actions baut bei jedem Push und Pull Request automatisch (`.github/workflows/build.yml`).

- Jeder Push auf `main` ersetzt das Vorab-Release **dev** durch die neue Jar.
- Ein Git-Tag `v<Version>` erstellt ein richtiges Release, zum Beispiel:
  `git tag v0.1.0 && git push origin v0.1.0`, oder auf GitHub unter *Releases → Draft a new release* einen neuen Tag `v0.1.0` anlegen.

## Aufbau des Codes

| Ort | Inhalt |
|---|---|
| `src/main/java/.../goblinforest/GoblinForest.java` | Einstiegspunkt (Server und gemeinsam), lädt das Balancing, registriert Befehle |
| `.../config/` | `Balance` (alle Spielwerte als Records) und `BalanceLoader` |
| `.../unit/` | `UnitType` (Soldatentypen) und `UnitLeveling` (Veteranen-System) |
| `.../game/` | `MatchPhase`, `TeamColor`; hier entstehen `Match`, `TeamState`, Arena und Festung |
| `.../command/` | Der Befehl `/gf` |
| `src/client/java/.../client/` | Client-Einstiegspunkt; HUD, Kriegsmenü, Tasten und Kamera |
| `src/main/resources/data/goblinforest/balance.json` | Alle Zahlenwerte (Kosten, HP, Schaden, EP-Schwellen …) |
| `src/test/java/` | Unit-Tests für die reine Spiellogik |

Grundsätze: Mod-ID `goblinforest`, Paket `io.github.jan1a234.goblinforest`, alles Spielrelevante wird nur auf dem Server berechnet,
Zahlenwerte gehören in `balance.json` statt in den Code.
