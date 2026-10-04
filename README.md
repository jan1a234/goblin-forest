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

## Spielen

1. Beide Spieler tippen `/gf join` (oder `/gf join rot` bzw. `/gf join gruen`, um sich einen Clan zu wünschen).
2. Einer tippt `/gf start`. Die Arena wird in einer eigenen Dimension gebaut (ein paar Sekunden), dann zählt ein Countdown herunter.
   Inventar, Position und Spielmodus werden vorher gesichert und nach dem Match automatisch zurückgegeben,
   auch wenn jemand zwischendurch rausfliegt oder der Server neu startet.
3. Ziel: den **Festungskern** des anderen Clans zerstören. Wer seinen Kern verliert, verliert das Match.

Alleine ausprobieren: `/gf start practice` (der zweite Clan bleibt leer).
Notfall: `/gf stop` bricht ein Match ab, `/gf reset` (Operatoren) holt alle Spieler zurück. `/gf help` zeigt alle Befehle.

### Steuerung

Die Kamera steht im Match fest in der Verfolgerperspektive. Alle Tasten lassen sich in den Steuerungsoptionen unter „Goblin Forest" ändern.

| Taste | Wirkung |
|---|---|
| **B** (oder Kompass in der Hotbar) | Kriegsmenü: Einheiten, Upgrades, Festung, Zauber |
| **Z / X / C / V** | Sklaven / Krieger / Bogenschütze / Assassine rekrutieren |
| **H** | Haltung wechseln: Vorrücken → Halten → Rückzug |
| **N** (oder Kriegshorn) | Sammelpunkt dort setzen, wo du hinschaust; die Armee hält dort |
| **R** / **G** | Blutrausch / Kampfstampfer (Häuptlingsfähigkeiten) |
| **J** / **K** | Feuerball / Heilende Pilze (kosten Gold) |
| Linksklick | Angreifen; auf Turm oder Festung des Gegners gehalten: Gebäude beschädigen |

Gold kommt passiv und als Kopfgeld für getötete Goblins (mehr, je weiter vorne der Kill passiert). Kills bringen Clan-Erfahrung,
daraus wird **Ruf** (Stufe 0 bis 5), der Assassinen, Heilzauber und höhere Upgrades freischaltet. Jeder Goblin sammelt eigene
Erfahrung und steigt bis zum Champion (5 Sterne) auf; der Häuptling selbst erreicht Stufe 10.

Für Server-Betreiber: Die Mod legt die Dimension `goblinforest:arena` per Datenpaket an. Sie erscheint automatisch,
auch in bestehenden Welten, sobald die Mod installiert ist.

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
| `.../unit/` | `GoblinUnit` (Einheit mit Lane-KI), `GoblinArrow`, `UnitCombat` und `UnitLeveling` (reine Kampf- und Veteranenwerte) |
| `.../game/` | `Match` (Ablauf eines Matches), `MatchManager` (Lobby, Ereignisse), `TeamState` (Gold, Ruf, Upgrades), `HeroKit`, `PlayerBackup` |
| `.../arena/` | `ArenaLayout` (Koordinaten), `ArenaBlueprint` (Bauplan als reine Logik), `ArenaBuilder` (setzt Blöcke) |
| `.../hero/`, `.../spell/`, `.../upgrade/` | Häuptlingsstufen, Fähigkeiten, Zauber und Upgrade-Schlüssel |
| `.../net/` | Netzwerkpakete zwischen Server und Client |
| `.../command/` | Der Befehl `/gf` |
| `src/client/java/.../client/` | Goblin-Modell, HUD, Kriegsmenü, Tasten und Kamera |
| `src/main/resources/data/goblinforest/balance.json` | Alle Zahlenwerte (Kosten, HP, Schaden, EP-Schwellen …) |
| `src/test/java/` | Unit-Tests für die reine Spiellogik |
| `src/gametest/java/` | Gametest: ein komplettes Match ohne Spieler auf einem echten Minecraft-Server (läuft bei `./gradlew build`) |

Grundsätze: Mod-ID `goblinforest`, Paket `io.github.jan1a234.goblinforest`, alles Spielrelevante wird nur auf dem Server berechnet,
Zahlenwerte gehören in `balance.json` statt in den Code.
