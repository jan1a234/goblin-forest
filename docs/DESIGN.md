# Goblin Forest – Design-Dokument

Fabric-Mod für **Minecraft Java 26.2**. 1-gegen-1-PvP auf einem Server, gespielt aus der Third-Person-Perspektive.
Eigene Variante, inspiriert vom Browser-Strategiespiel *Clan Wars: Goblin Forest* (Flash, 2010).

Dieses Dokument ist die Grundlage für alle weiteren Arbeitsschritte (Mod-Gerüst, Kern-Gameplay, Feinschliff).
Markierungen: **[MVP]** = nötig für das erste spielbare Match zu zweit, **[Später]** = Ausbaustufe danach.

---

## 1. Das Original in Kürze

*Clan Wars: Goblin Forest* ist ein seitlich scrollendes „Lane-Battle“-Strategiespiel (ähnlich *Age of War*):

| Element | Im Original |
|---|---|
| Ziel | Die feindliche Festung (Stronghold) zerstören, die eigene verteidigen. Beide Clans sind durch einen Fluss getrennt. |
| Einheiten | Werden per Knopfleiste unten links rekrutiert und laufen automatisch zum Gegner: **Sklaven** (billiges Kanonenfutter, stapelbar), **Krieger** (Nahkampf), **Bogenschützen** (Fernkampf), **Assassinen** (Spezialisten). |
| Wirtschaft | **Gold** gibt es für getötete Einheiten, und zwar auch für eigene Verluste. Das Kopfgeld steigt, je näher am feindlichen Stronghold gekämpft wird. Dadurch lohnt sich Angriff mehr als Einigeln. |
| Anzeigen | Oben links: **Gold, Erfahrung, Ruf (Reputation), Bevölkerung**. |
| Bevölkerungslimit | Begrenzt die Anzahl gleichzeitiger Einheiten. |
| Upgrades | Rüstung, Reichweite, Angriffskraft der Truppen; **Turm** und **Kanone** am Stronghold; **Zauber** werden stufenweise stärker. |
| Held (Warchief) | Wird mit genug Erfahrung beschworen, kann angreifen oder sich zurückziehen. Fähigkeiten: **Blutrausch**, **Kampfstampfer (Battle Slam)**, **Raserei (Rage, lädt sich automatisch auf)**. |
| Strategie | Typisch ist frühes Überrennen („zergen“), bevor der Gegner durch Upgrades zu stark wird; der Turm ist die letzte Verteidigung. |

Quellen: [bubblebox.com](https://www.bubblebox.com/play/1777/clan-wars-goblin-forest.htm), [ageofwargame.io](https://ageofwargame.io/clan-wars-goblin-forest), [girlgames.com](https://www.girlgames.com/clan-wars-goblin-forest.html), [flashghetto.com](https://flashghetto.com/games/clan_wars_goblin_forest), [Kongregate-Kommentare](https://www.kongregate.com/en/games/ffgameplayer/clan-wars-goblins-forest/comments).
Genaue Zahlenwerte des Originals sind öffentlich nicht dokumentiert; alle Werte unten sind eigene Startwerte fürs Balancing.

## 2. Unsere Variante: Grundidee

Jeder Spieler **ist** sein Goblin-Häuptling (Warchief) und steht selbst auf dem Schlachtfeld, in Third-Person.
Er rekrutiert Soldaten, die automatisch über die Lanes zur feindlichen Festung marschieren, kauft Upgrades, wirkt Zauber
und kämpft mit eigenen Heldenfähigkeiten mit. Gegenüber dem Original kommen drei Dinge dazu:

1. **Soldaten leveln einzeln** (Veteranen-System, Abschnitt 5.2), zusätzlich zu den globalen Upgrades.
2. **3D-Schlachtfeld mit mehreren Lanes** und Wald dazwischen (Abschnitt 4).
3. **Der Held ist der Spieler selbst**, mit eigenem Level und Fähigkeitenbaum (Abschnitt 6).

## 3. Match-Ablauf

| Phase | Ablauf | Stufe |
|---|---|---|
| Lobby | `/gf join` (oder `/gf join rot|gruen`), `/gf start` startet bei 2 Spielern. Admin: `/gf stop`, `/gf reset`. | [MVP] |
| Aufbau | Arena wird in einer eigenen Dimension `goblinforest:arena` (leere Void-Welt) gebaut, Spieler werden teleportiert, Inventar wird gesichert und durch die Match-Ausrüstung ersetzt. Die normale Welt des Servers bleibt unberührt. | [MVP] |
| Countdown | 10 s, beide Spieler sind in ihrer Festung eingefroren. | [MVP] |
| Kampf | Echtzeit. Startgold 150, passives Einkommen +2 Gold/s. | [MVP] |
| Sudden Death | Ab Minute 25 verlieren beide Festungen 0,5 % HP pro Sekunde, damit kein Match endlos dauert. | [Später] |
| Ende | Festungskern auf 0 HP: Sieg-/Niederlage-Titel, Statistik im Chat (Kills, Gold, höchstes Einheitenlevel), nach 15 s Rückteleport und Inventar-Wiederherstellung. | [MVP] |
| Weitere Modi | Best-of-3, 2v2, Match gegen KI-Clan. | [Später] |

## 4. Arena

- Länge ca. 160 Blöcke zwischen den Festungen, Breite ca. 60 Blöcke.
- **[MVP] Eine Haupt-Lane** (8 Blöcke breit) mit einem **Fluss in der Mitte** und einer Brücke (Engpass, wie im Original der Fluss).
- **[Später] Drei Lanes** (links, Mitte, rechts), dazwischen dichter Goblin-Wald, durch den nur Spieler und Assassinen abkürzen können. Beim Rekrutieren wählt man die Lane.
- Lanes sind intern Listen von Wegpunkten (Daten, nicht hartcodiert), damit der Ausbau auf drei Lanes keinen Umbau erfordert.
- Arena wird per Code aus Blöcken generiert ([MVP]); später als gestaltete `.nbt`-Strukturvorlage mit Deko ([Später]).
- Die Umgebung ist unzerstörbar (Abbauen und Platzieren in der Arena-Dimension gesperrt).

### Festung (Stronghold)
| Teil | Funktion | Stufe |
|---|---|---|
| Festungskern | 3000 HP. Fällt er, ist das Match verloren. Wird als Block-Entity mit Lebensbalken über dem Tor dargestellt. | [MVP] |
| Turm | Schießt automatisch Pfeile auf Gegner in 24 Blöcken Reichweite (15 Schaden, 1 Schuss/s). Upgradebar. | [MVP] |
| Kanone | Flächenschaden (40 Schaden, Radius 3, alle 4 s) auf Gruppen vor dem Tor. Muss erst gekauft werden. | [Später] |
| Kaserne | Spawnpunkt der Einheiten am Tor. | [MVP] |

## 5. Einheiten

### 5.1 Einheitentypen

Alle Einheiten sind eigene Entities (`PathfinderMob`), folgen den Lane-Wegpunkten, greifen das nächste Ziel in Sichtweite an
(Einheiten, Spieler, Turm, Festung) und laufen danach weiter.

| Einheit | Rolle | Gold | Pop. | HP | Schaden | Reichweite | Tempo | Besonderheit | Stufe |
|---|---|---|---|---|---|---|---|---|---|
| Sklave | Kanonenfutter | 15 | 1 | 40 | 4 | Nahkampf | normal | Billig, kommt in 3er-Gruppen (Kosten für die Gruppe: 40) | [MVP] |
| Krieger | Frontlinie | 40 | 2 | 110 | 10 | Nahkampf | normal | Kann blocken: −30 % Schaden von vorne | [MVP] |
| Bogenschütze | Fernkampf | 50 | 2 | 60 | 9 | 14 Blöcke | normal | Bleibt hinter der Frontlinie stehen | [MVP] |
| Assassine | Flankierer | 80 | 3 | 70 | 22 | Nahkampf | schnell | Greift bevorzugt Fernkämpfer und den gegnerischen Spieler an; erster Treffer aus Unsichtbarkeit doppelt | [MVP] |
| Schamane | Unterstützung | 90 | 3 | 60 | 6 | 10 Blöcke | langsam | Heilt Verbündete in 6 Blöcken um 8 HP/2 s | [Später] |
| Troll | Tank | 150 | 5 | 400 | 25 | Nahkampf | langsam | Reißt Gegner um, +50 % Schaden gegen Gebäude | [Später] |
| Wolfsreiter | Kavallerie | 120 | 4 | 160 | 16 | Nahkampf | sehr schnell | Ansturm: erster Treffer mit Rückstoß | [Später] |
| Katapult | Belagerung | 200 | 5 | 150 | 60 | 22 Blöcke | langsam | Greift nur Gebäude an, Flächenschaden | [Später] |

Freischaltung über **Ruf** (Abschnitt 7): Sklave, Krieger und Bogenschütze sind ab Start verfügbar, die Assassine ab Ruf 1, weitere Typen ab höheren Rufstufen.

### 5.2 Soldaten leveln (Veteranen-System) [MVP]

Jede einzelne Einheit sammelt Erfahrung: **+10 EP pro Kill, +1 EP pro 10 verursachtem Schaden, +5 EP für Treffer auf Gebäude**.

| Level | Titel | EP benötigt | Bonus (kumulativ) | Darstellung |
|---|---|---|---|---|
| 1 | Rekrut | 0 | – | – |
| 2 | Kämpfer | 20 | +10 % HP, +10 % Schaden | 1 Stern über dem Kopf |
| 3 | Veteran | 60 | +25 % HP, +20 % Schaden | 2 Sterne, Lederrüstung in Clanfarbe |
| 4 | Elite | 140 | +45 % HP, +35 % Schaden, +10 % Tempo | 3 Sterne, Eisenhelm |
| 5 | Champion | 300 | +70 % HP, +50 % Schaden, Spezialfähigkeit (siehe unten) | goldene Sterne, leuchtender Umriss |

Champion-Fähigkeiten: Sklave explodiert beim Tod (20 Flächenschaden), Krieger bekommt eine Aura (+10 % Rüstung für Nachbarn),
Bogenschütze schießt Mehrfachpfeile (3 Ziele), Assassine wird nach jedem Kill kurz wieder unsichtbar. **[Später]** für die Champion-Fähigkeiten, Level 1–4 sind [MVP].

Beim Level-Aufstieg wird die Einheit voll geheilt. Getötete Veteranen bringen dem Gegner mehr Kopfgeld (+25 % pro Level über 1).

### 5.3 Globale Upgrades pro Einheitentyp

Wie im Original wirken diese auf alle Einheiten eines Typs, auch auf bereits lebende. Bezahlt mit Gold.

| Upgrade | Wirkung pro Stufe | Stufen | Kosten (Stufe 1/2/3/4/5) | Stufe |
|---|---|---|---|---|
| Rüstung | −8 % erlittener Schaden | 5 | 100 / 200 / 350 / 550 / 800 | [MVP] |
| Angriff | +10 % Schaden | 5 | 100 / 200 / 350 / 550 / 800 | [MVP] |
| Reichweite (nur Fernkampf) | +2 Blöcke | 3 | 150 / 300 / 500 | [MVP] |
| Ausbildung | Neue Einheiten starten mit +1 Level | 2 | 400 / 900 | [Später] |
| Ausdauer | +10 % Lauftempo | 3 | 120 / 250 / 400 | [Später] |

### 5.4 Befehle (Haltung) [MVP]

Wie die Angriff/Rückzug-Befehle des Originals, gilt für alle eigenen Einheiten:
- **Vorrücken** (Standard): Lane bis zur feindlichen Festung ablaufen.
- **Halten**: an der aktuellen Position bzw. am gesetzten Sammelbanner stehen bleiben und verteidigen.
- **Rückzug**: zurück zur eigenen Festung; dort werden Einheiten langsam geheilt (+2 % HP/s).

## 6. Der Warchief (Spieler)

Der Spieler läuft selbst als Goblin-Häuptling über das Feld. Er hat 200 HP, eine Clan-Waffe (Axt, 12 Schaden)
und darf den gegnerischen Spieler direkt angreifen. Stirbt er, respawnt er nach **8 s + 1 s pro Helden-Level** in der eigenen Festung,
und der Gegner erhält 100 Gold Kopfgeld.

| Element | Beschreibung | Stufe |
|---|---|---|
| Helden-Level 1–10 | Erfahrung aus allen Kills der eigenen Armee (25 %) und eigenen Kills (100 %). Pro Level +15 HP, +1 Schaden. | [MVP] |
| Blutrausch (Taste R) | Eigene Einheiten in 12 Blöcken: +30 % Angriffstempo für 8 s. Abklingzeit 30 s. | [MVP] |
| Kampfstampfer (Taste F) | Flächenschlag, 30 Schaden in 5 Blöcken, schleudert Gegner zurück. Abklingzeit 15 s. | [MVP] |
| Raserei (Taste V) | Lädt sich durch erlittenen und verursachten Schaden auf. Voll: 10 s doppelter Schaden, +30 % Tempo, Lebensraub 20 %. | [Später] |
| Fähigkeitspunkte | Pro Helden-Level 1 Punkt zum Verstärken einer Fähigkeit (je 3 Ränge). | [Später] |

## 7. Wirtschaft und Ressourcen

Anzeige oben links wie im Original: **Gold, Erfahrung, Ruf, Bevölkerung**.

| Ressource | Quelle | Verwendung | Stufe |
|---|---|---|---|
| Gold | Passiv +2/s. **Kopfgeld** für jeden Kill: Grundwert = 50 % der Einheitenkosten, multipliziert mit dem **Frontfaktor** 1,0 (eigene Hälfte) bis 2,0 (direkt vor der feindlichen Festung). Wie im Original bekommt man auch für **eigene Verluste** 25 % des Kopfgelds, wenn sie in der feindlichen Hälfte fallen. | Einheiten, Upgrades, Zauber | [MVP] |
| Erfahrung (Clan) | Alle Kills der eigenen Seite. | Helden-Level (Abschnitt 6) | [MVP] |
| Ruf | +1 Rufpunkt pro 400 Clan-Erfahrung, +1 für zerstörten feindlichen Turm. | Schaltet Einheitentypen, Zauber und Festungsausbauten frei (Rufstufen 0–5). | [MVP] |
| Bevölkerung | Limit 20 zu Beginn. | Ausbau „Goblinhütten“: +10 pro Stufe (3 Stufen; 150 / 300 / 500 Gold). | [MVP] |
| Goldmine | Festungsausbau: +1 Gold/s pro Stufe (3 Stufen; 200 / 400 / 700). | Langfristige Wirtschaft gegen frühes Zergen abwägen. | [Später] |

## 8. Zauber

Werden vom Spieler mit Blick auf eine Stelle gewirkt (Raycast bis 40 Blöcke). Kosten Gold, haben Abklingzeiten und werden über Rufstufen und Gold stärker (Stufe 1–3).

| Zauber | Wirkung (Stufe 1) | Gold | Abklingzeit | Ruf nötig | Stufe |
|---|---|---|---|---|---|
| Feuerball | 50 Flächenschaden, Radius 3 | 60 | 12 s | 0 | [MVP] |
| Heilende Pilze | Heilt eigene Einheiten im Radius 6 um 60 HP | 80 | 25 s | 1 | [MVP] |
| Wurzelfessel | Gegner im Radius 5 können sich 4 s nicht bewegen | 90 | 30 s | 2 | [Später] |
| Blitzsturm | 5 Blitze auf zufällige Gegner im Radius 8, je 45 Schaden | 150 | 45 s | 3 | [Später] |
| Meteor | 300 Schaden im Radius 5, auch gegen Gebäude | 300 | 90 s | 5 | [Später] |

## 9. Festungsausbauten

| Ausbau | Wirkung pro Stufe | Stufen / Kosten | Stufe |
|---|---|---|---|
| Turm | +5 Schaden, +2 Reichweite | 3 / 150, 300, 500 | [MVP] |
| Mauern | +500 Festungs-HP | 3 / 200, 400, 650 | [MVP] |
| Kanone | Kaufen, dann +15 Schaden, −0,5 s Nachladezeit | 1+3 / 250, 200, 350, 500 | [Später] |
| Goblinhütten | +10 Bevölkerung | 3 / 150, 300, 500 | [MVP] |
| Goldmine | +1 Gold/s | 3 / 200, 400, 700 | [Später] |

## 10. Steuerung und Kamera

- **Third-Person erzwungen** während des Matches, Kamera hinter dem Spieler. [MVP] nutzt die normale Third-Person-Kamera (F5), [Später] Abstand per Mausrad 4–12 Blöcke einstellbar.
- **Kommandoansicht** (Taste Tab): Kamera fährt hoch über das Schlachtfeld (Vogelperspektive), Spieler bleibt stehen. [Später]
- **Kriegsmenü** (Taste B): Bildschirm mit Knöpfen wie die Leiste im Original, Tabs *Einheiten / Upgrades / Festung / Zauber*, zeigt Kosten, Stufe und gesperrte Einträge. [MVP]
- **Schnelltasten** (frei belegbar über die Minecraft-Tastenbelegung):
  - Einheiten rekrutieren: Num1–Num4 bzw. Z / X / C / G
  - Haltung: H (Vorrücken → Halten → Rückzug durchschalten)
  - Fähigkeiten: R / F / V; Zauber: Q-Taste + Mausklick
- **HUD** [MVP]: oben links Gold / Erfahrung / Ruf / Bevölkerung, oben Mitte beide Festungs-Lebensbalken, unten rechts Abklingzeiten, Lebensbalken über Einheiten (mit Level-Sternen).
- Die normale Hotbar und das Inventar sind im Match gesperrt (nur die Clan-Waffe).

## 11. Technischer Rahmen

### 11.1 Versionen (Stand 04.10.2026, aus dem offiziellen [Fabric-Example-Mod, Branch 26.2](https://github.com/FabricMC/fabric-example-mod/tree/26.2) und [fabricmc.net](https://fabricmc.net/2026/06/15/262.html))

| Komponente | Version |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.2 |
| Fabric Loom | 1.18-SNAPSHOT, Plugin-ID `net.fabricmc.fabric-loom` |
| Gradle | 9.7.1 |
| Java | 25 (`options.release = 25`) |
| Mappings | keine: Minecraft ist seit 26.1 unverschleiert, es gibt keinen `mappings`-Eintrag und kein Remapping mehr; Abhängigkeiten über `implementation` statt `modImplementation`. |

Hinweise für 26.2: Bildschirm-Methoden liegen jetzt unter `Minecraft.getInstance().gui` statt direkt auf `Minecraft`;
Rendering nur über die Blaze3D-API (es gibt ein experimentelles Vulkan-Backend, rohe OpenGL-Aufrufe brechen).
Vor dem Bau die Versionen auf [fabricmc.net/develop](https://fabricmc.net/develop/) erneut prüfen.

### 11.2 Projektstruktur

- Mod-ID `goblinforest`, Paket `io.github.jan1a234.goblinforest`.
- Getrennte Source-Sets `main` (Server + gemeinsam) und `client` (`splitEnvironmentSourceSets()`).
- **Server-autoritativ**: Gold, Kämpfe, Spawns und Upgrades werden nur auf dem Server berechnet. Der Client sendet nur Wünsche.
- Netzwerk über Fabric-Payloads:
  - Client → Server: `RecruitUnit(typ, lane)`, `BuyUpgrade(id)`, `CastSpell(id, zielposition)`, `UseAbility(id)`, `SetStance(haltung)`
  - Server → Client: `TeamStateSync` (Ressourcen, Upgrade-Stufen, Abklingzeiten, Festungs-HP), `MatchStateSync` (Phase, Timer, Ergebnis)
- Kernklassen: `Match` (Zustandsmaschine Lobby → Aufbau → Countdown → Kampf → Ende), `TeamState`, `LaneDefinition`, `GoblinUnitEntity` (Basisklasse) mit Unterklassen je Typ, `UnitLeveling`, `UpgradeRegistry`, `SpellRegistry`, `ArenaBuilder`, `StrongholdBlockEntity`.
- **Balancing-Werte in einer Datei** `data/goblinforest/balance.json` (Datapack-Ressource), damit Zahlen ohne Neukompilieren angepasst werden können.
- Modelle: [MVP] vanilla-artige humanoide Modelle (kleiner skaliert) mit eigenen Goblin-Texturen in Clanfarben, keine Fremd-Mods außer Fabric API. [Später] eigene Modelle und Animationen.
- Die Mod muss auf **Server und beiden Clients** installiert sein (zusammen mit Fabric API).

### 11.3 Build und Auslieferung

GitHub Actions baut bei jedem Push die `.jar` (Java 25, `./gradlew build`); ein Git-Tag `v*` erzeugt ein GitHub-Release mit der `.jar` als Download.

## 12. Umfang des ersten spielbaren Matches (MVP)

Damit zwei Spieler ein komplettes Match spielen können, wird genau das gebaut:

1. Befehle `/gf join|start|stop|reset`, Arena-Dimension mit einer Lane, Fluss und Brücke.
2. Festung mit Kern, Turm, Kaserne; Sieg/Niederlage und Rückkehr in die normale Welt.
3. Einheiten Sklave, Krieger, Bogenschütze, Assassine mit Lane-KI und Zielwahl.
4. Soldaten-Level 1–4 mit Sternen und Boni.
5. Globale Upgrades Rüstung, Angriff, Reichweite; Festungsausbauten Turm, Mauern, Goblinhütten.
6. Gold mit Frontfaktor-Kopfgeld, Clan-Erfahrung, Ruf, Bevölkerungslimit.
7. Warchief mit Level 1–10, Blutrausch, Kampfstampfer; Zauber Feuerball und Heilende Pilze.
8. Haltungen Vorrücken / Halten / Rückzug.
9. Kriegsmenü (B), Schnelltasten, HUD, erzwungene Third-Person-Kamera.

Alles mit **[Später]** folgt danach in dieser Reihenfolge: drei Lanes → Schamane, Troll, Wolfsreiter, Katapult → Champion-Fähigkeiten und Raserei → Kanone, Goldmine, weitere Zauber → Kommandoansicht → eigene Modelle/Animationen und Sounds → Sudden Death, Best-of-3, KI-Gegner.
