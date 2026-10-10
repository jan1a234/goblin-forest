# Goblin Forest

Fabric-Mod für Minecraft Java 26.2: 1-gegen-1-Lane-Strategie mit Goblin-Clans, inspiriert von *Clan Wars: Goblin Forest*.
Das Spieldesign steht in [docs/DESIGN.md](docs/DESIGN.md).

## Installieren

1. Die neueste `goblinforest-….jar` unter [Releases](https://github.com/jan1a234/goblin-forest/releases) herunterladen.
   Das Release **dev** ist immer der neueste Stand von `main`, Releases mit `v` davor (z. B. `v1.0.0`) sind fertige Versionen.
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

**Du bist der Feldherr, nicht der Häuptling.** Im Match siehst du das Schlachtfeld nur von oben (Draufsicht), deine eigene
Festung liegt immer links im Bild. Deine Spielfigur ist dabei unsichtbar und unverwundbar über dem Feld geparkt.
Der **Häuptling** ist eine eigene, besonders starke Einheit mit goldenem Helm: Du schickst ihn wie eine Einheit aufs
Schlachtfeld (goldener Helm in der Leiste unten links oder Taste **Q**), dort läuft er mit der Armee die Lane entlang.
Seine Fähigkeiten löst du aus der Ferne aus. Fällt er, steht er nach einer Wartezeit in der Festung wieder auf
(je höher seine Stufe, desto länger) und zieht von selbst wieder los, wenn du ihn losgeschickt hattest.
Bis du ihn zum ersten Mal losschickst, bewacht er die Festung.

Alleine ausprobieren: `/gf start practice` (der zweite Clan bleibt leer).

**Gegen die KI:** `/gf start ki` (oder `ki leicht`, `ki normal`, `ki schwer`). Die KI führt einen eigenen Clan, rekrutiert eine
gemischte Armee (ab „normal“ gezielt gegen deine), baut ihre Festung aus und wirkt Zauber. Ihr Häuptling zieht wie deiner in die Schlacht,
sie setzt seine Fähigkeiten ein und ruft ihn zurück, wenn er zu schwach wird. Je nach Stufe bekommt sie mehr oder weniger Gold. Mehrere Spieler können zusammen gegen die KI antreten.

**Best of 3 oder 5:** `/gf start bo3` bzw. `/gf start bo5`. Jede Runde beginnt mit frischer Arena; zwischen den Runden bleiben
alle in der Arena. Kombinierbar, z. B. `/gf start ki schwer bo3`.
Notfall: `/gf stop` bricht ein Match ab, `/gf reset` (Operatoren) holt alle Spieler zurück. `/gf help` zeigt alle Befehle.

### Steuerung

Alles geht mit der Maus über die **Befehlsleiste unten links**:

- **Obere Reihe:** die acht Einheiten (Sklave bis Katapult), daneben die Haltung der Armee und das Sammelbanner.
  Ein Klick auf eine Einheit rekrutiert sie sofort. Unter jedem Feld steht der Preis in Gold; graue Felder sind zu teuer
  oder noch gesperrt („R2“ = ab Ruf 2).
- **Untere Reihe:** die fünf Wunder (Feuerball, Heilende Pilze, Wurzelfessel, Blitzsturm, Meteor), der **goldene Helm**
  für den Häuptling, seine drei Fähigkeiten und das Buch fürs Kriegsmenü.
- **Wunder wirken:** auf das Wunder klicken, dann auf die Stelle im Feld, wo es einschlagen soll. Ein farbiger Kreis zeigt
  vorher, wo es trifft. **Rechtsklick** oder **Esc** bricht ab.
- Fährt die Maus über ein Feld, erklärt ein Tooltip, was es tut und warum es gerade nicht geht.
- **Unten rechts** steht der Zustand des Häuptlings (in der Festung, auf dem Feld oder gefallen) mit Leben, Erfahrung und
  Raserei. Ein Klick darauf schwenkt die Kamera zu ihm.

Kamera:

| Eingabe | Wirkung |
|---|---|
| **W/A/S/D** oder **Pfeiltasten** | Kamera schwenken (mit **Strg** doppelt so schnell) |
| Linke Maustaste ins Feld drücken und **ziehen** | Kamera schieben |
| **Mausrad** (auch Zwei-Finger-Wischen auf dem Touchpad) oder **+** / **-** | Zoomen |
| **Leertaste** | Kamera zum eigenen Häuptling |

Wer lieber Tasten benutzt (alle in den Steuerungsoptionen unter „Goblin Forest" änderbar):

| Taste | Wirkung |
|---|---|
| **Q** | Häuptling losschicken oder in die Festung zurückrufen |
| **B** | Kriegsmenü: Einheiten, Upgrades, Festung, Zauber, Häuptling |
| **Z / X / C / V** | Sklaven / Krieger / Bogenschütze / Assassine rekrutieren |
| **U / I / O / M** | Schamane / Wolfsreiter / Troll / Katapult rekrutieren (ab Ruf 2, 3, 4) |
| **H** | Haltung wechseln: Vorrücken → Halten → Rückzug |
| **N** | Sammelbanner wählen, dann ins Feld klicken; bei „Halten“ sammelt sich die Armee dort |
| **1 – 5** (auch **J** / **K**) | Wunder wählen, dann ins Feld klicken |
| **6 / 7 / 8** (auch **R** / **G** / **Y**) | Blutrausch / Kampfstampfer / Raserei des Häuptlings |
| **9** | wie Q |
| **T** | Chat |
| **Esc** | Wunder abbrechen, sonst Pausemenü |

Gold kommt passiv und als Kopfgeld für getötete Goblins (mehr, je weiter vorne der Kill passiert). Kills bringen Clan-Erfahrung,
daraus wird **Ruf** (Stufe 0 bis 5), der neue Einheiten, Zauber und höhere Upgrades freischaltet. Jeder Goblin sammelt eigene
Erfahrung und steigt bis zum Champion (5 Sterne) auf; Champions bekommen eine Spezialfähigkeit (z. B. explodierende Sklaven,
Mehrfachschuss der Bogenschützen). Der Häuptling erreicht Stufe 10, wird mit jeder Stufe stärker und bekommt pro Stufe einen Fähigkeitspunkt.

Die Festung lässt sich ausbauen: Turm, Mauern, Hütten (mehr Armee), eine **Kanone** auf der Mauer und eine **Goldmine** im Hof.
Ein Match läuft ohne Zeitlimit, bis eine Festung fällt. Wer ein Ende erzwingen will, startet mit `/gf start sd`
(**Sudden Death**, kombinierbar z. B. mit `ki` oder `bo3`): Nach 25 Minuten verlieren dann beide Festungskerne langsam Leben.

Während der Schlacht spielen eigene Kriegstrommeln (Lautstärke über den Musik-Regler). Für die Draufsicht lohnt sich eine
Sichtweite von mindestens 10 Chunks, damit die ganze Arena zu sehen ist.

Für Server-Betreiber: Die Mod legt die Dimension `goblinforest:arena` per Datenpaket an. Sie erscheint automatisch,
auch in bestehenden Welten, sobald die Mod installiert ist.

## Selbst bauen

```
./gradlew build
```

Die Jar liegt danach in `build/libs/`. Benötigt Java 25.

Getestet wird bei jedem Build auf zwei Arten: `MatchGameTest` spielt auf einem echten Server ein komplettes Match ohne Spieler
durch (alle Einheiten, Zauber, Ausbauten, KI, Best-of-Serie), und `ClientMatchGameTest` startet einen echten Minecraft-Client,
spielt ein Match gegen die KI an und macht Bildschirmfotos von Draufsicht, Befehlsleiste, Armee und Kriegsmenü.
Die neuesten Bildschirmfotos liegen im Branch [`ci-screenshots`](https://github.com/jan1a234/goblin-forest/tree/ci-screenshots).

## Release erstellen

GitHub Actions baut bei jedem Push und Pull Request automatisch (`.github/workflows/build.yml`).

- Jeder Push auf `main` ersetzt das Vorab-Release **dev** durch die neue Jar.
- Ein richtiges Release entsteht am einfachsten auf GitHub unter *Actions → build → Run workflow*: dort die Versionsnummer
  eintragen (z. B. `1.1.0`), dann werden Tag `v1.1.0` und Release mit der Jar angelegt. Liegt eine Datei
  `docs/release-notes/v1.1.0.md` bei, wird sie als Beschreibung verwendet.
- Alternativ geht auch ein Git-Tag: `git tag v1.1.0 && git push origin v1.1.0`.

## Aufbau des Codes

| Ort | Inhalt |
|---|---|
| `src/main/java/.../goblinforest/GoblinForest.java` | Einstiegspunkt (Server und gemeinsam), lädt das Balancing, registriert Befehle |
| `.../config/` | `Balance` (alle Spielwerte als Records) und `BalanceLoader` |
| `.../unit/` | `GoblinUnit` (Einheit mit Lane-KI, auch der Häuptling), `GoblinArrow`, `UnitCombat` und `UnitLeveling` (reine Kampf- und Veteranenwerte) |
| `.../game/` | `Match` (Ablauf eines Matches), `MatchManager` (Lobby, Ereignisse), `TeamState` (Gold, Ruf, Upgrades), `AiCommander` (KI-Clan), `PlayerBackup` |
| `.../arena/` | `ArenaLayout` (Koordinaten), `ArenaBlueprint` (Bauplan als reine Logik), `ArenaBuilder` (setzt Blöcke) |
| `.../hero/`, `.../spell/`, `.../upgrade/` | Häuptlingsstufen, Fähigkeiten, Zauber und Upgrade-Schlüssel |
| `.../net/` | Netzwerkpakete zwischen Server und Client |
| `.../command/` | Der Befehl `/gf` |
| `src/client/java/.../client/` | Goblin-Modell, `MatchHud` (HUD und Befehlsleiste), `CommandScreen` (Maus- und Tastensteuerung), `CommandView` (Draufsicht), Kriegsmenü |
| `src/main/resources/data/goblinforest/balance.json` | Alle Zahlenwerte (Kosten, HP, Schaden, EP-Schwellen …) |
| `src/test/java/` | Unit-Tests für die reine Spiellogik |
| `src/gametest/java/` | Gametest: ein komplettes Match ohne Spieler auf einem echten Minecraft-Server (läuft bei `./gradlew build`) |

Grundsätze: Mod-ID `goblinforest`, Paket `io.github.jan1a234.goblinforest`, alles Spielrelevante wird nur auf dem Server berechnet,
Zahlenwerte gehören in `balance.json` statt in den Code.
