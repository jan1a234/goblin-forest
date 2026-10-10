# Goblin Forest – Design-Dokument

Fabric-Mod für **Minecraft Java 26.2**. 1-gegen-1-PvP auf einem Server, gespielt in der Draufsicht wie im Original (ab v1.1).
Eigene Variante, inspiriert vom Browser-Strategiespiel *Clan Wars: Goblin Forest* (Flash, 2010).

Dieses Dokument ist die Grundlage für alle weiteren Arbeitsschritte (Mod-Gerüst, Kern-Gameplay, Feinschliff).
Markierungen: **[MVP]** = nötig für das erste spielbare Match zu zweit (v0.1), **[v1.0]** = mit dem Feinschliff umgesetzt, **[Später]** = noch offen.

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

Jeder Spieler ist der **Feldherr** seines Goblin-Clans und blickt wie im Original von oben auf das Schlachtfeld
(Draufsicht, Abschnitt 10). Er rekrutiert Soldaten, die automatisch über die Lanes zur feindlichen Festung marschieren,
kauft Upgrades, wirkt Zauber und schickt seinen **Häuptling** (Warchief) als besonders starke Heldeneinheit in die Schlacht.
Gegenüber dem Original kommen drei Dinge dazu:

1. **Soldaten leveln einzeln** (Veteranen-System, Abschnitt 5.2), zusätzlich zu den globalen Upgrades.
2. **3D-Schlachtfeld mit mehreren Lanes** und Wald dazwischen (Abschnitt 4).
3. **Der Häuptling ist eine eigene Einheit** mit Level 1–10 und Fähigkeitenbaum, die der Spieler aus der Ferne führt (Abschnitt 6).

> Änderung 10.10.2026 (Jan): Bis v1.0 war der Spieler selbst der Häuptling und lief in Third-Person über das Feld.
> Ab v1.1 ist der Spieler nur noch Feldherr in der Draufsicht; der Häuptling wird wie eine Einheit losgeschickt.

## 3. Match-Ablauf

| Phase | Ablauf | Stufe |
|---|---|---|
| Lobby | `/gf join` (oder `/gf join rot|gruen`), `/gf start` startet bei 2 Spielern. Admin: `/gf stop`, `/gf reset`. | [MVP] |
| Aufbau | Arena wird in einer eigenen Dimension `goblinforest:arena` (leere Void-Welt) gebaut, Spieler werden teleportiert, Inventar und Spielmodus werden gesichert. Die Spieler schweben danach unsichtbar und unverwundbar (Zuschauermodus) über ihrer Festung. Die normale Welt des Servers bleibt unberührt. | [MVP] |
| Countdown | 10 s; beide Häuptlinge stehen in ihrer Festung, die Draufsicht ist schon aktiv. | [MVP] |
| Kampf | Echtzeit. Startgold 150, passives Einkommen +2 Gold/s. | [MVP] |
| Sudden Death | Standardmäßig **aus**: Ein Match läuft, bis eine Festung fällt. Nur mit `/gf start sd` verlieren ab Minute 25 beide Festungen 0,5 % HP pro Sekunde. | [v1.1] |
| Ende | Festungskern auf 0 HP: Sieg-/Niederlage-Titel, Statistik im Chat (Kills, Gold, höchstes Einheitenlevel), nach 15 s Rückteleport und Inventar-Wiederherstellung. | [MVP] |
| Weitere Modi | Best-of-3/5 und Match gegen KI-Clan [v1.0] (Abschnitt 10a); 2v2 [Später]. | [v1.0] |

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
| Kanone | Flächenschaden (40 Schaden, Radius 3, alle 4 s) auf Gruppen vor dem Tor, 22 Blöcke Reichweite. Muss erst gekauft werden, jede weitere Stufe lädt schneller nach und trifft härter. | [v1.0] |
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
| Schamane | Unterstützung | 90 | 3 | 60 | 6 | 10 Blöcke | langsam | Heilt bis zu 5 verletzte Verbündete in 6 Blöcken um 8 HP alle 2 s, greift mit Flüchen an. Ruf 2 | [v1.0] |
| Troll | Tank | 150 | 5 | 480 | 26 | Nahkampf | langsam | Fast kein Rückstoß, Schläge treffen Nachbarn mit (50 %) und werfen zurück, +50 % Schaden gegen Gebäude. Ruf 3 | [v1.0] |
| Wolfsreiter | Kavallerie | 110 | 4 | 170 | 16 | Nahkampf | sehr schnell | Ansturm: erster Treffer +50 % Schaden mit Rückstoß, lädt in 6 s nach; jagt Fernkämpfer. Ruf 2 | [v1.0] |
| Katapult | Belagerung | 200 | 5 | 150 | 60 | 22 Blöcke | langsam | Greift nur Gebäude an, Splitter treffen Einheiten ringsum (50 %). Ruf 4 | [v1.0] |

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
Bogenschütze schießt Mehrfachpfeile (3 Ziele), Assassine wird nach jedem Kill kurz wieder unsichtbar, Schamane heilt 50 % mehr in größerem Umkreis,
Wolfsreiter lädt den Ansturm nach jedem Kill sofort neu, Troll regeneriert 1 % Leben pro Sekunde, Katapult feuert zwei Brocken. **[v1.0]**

Beim Level-Aufstieg wird die Einheit voll geheilt. Getötete Veteranen bringen dem Gegner mehr Kopfgeld (+25 % pro Level über 1).

### 5.3 Globale Upgrades pro Einheitentyp

Wie im Original wirken diese auf alle Einheiten eines Typs, auch auf bereits lebende. Bezahlt mit Gold.

| Upgrade | Wirkung pro Stufe | Stufen | Kosten (Stufe 1/2/3/4/5) | Stufe |
|---|---|---|---|---|
| Rüstung | −8 % erlittener Schaden | 5 | 100 / 200 / 350 / 550 / 800 | [MVP] |
| Angriff | +10 % Schaden | 5 | 100 / 200 / 350 / 550 / 800 | [MVP] |
| Reichweite (nur Fernkampf) | +2 Blöcke | 3 | 150 / 300 / 500 | [MVP] |
| Ausbildung (gilt für die ganze Armee) | Neue Einheiten starten mit +1 Level | 2 | 400 / 900 | [v1.0] |
| Ausdauer (gilt für die ganze Armee) | +10 % Lauftempo und Leben | 3 | 120 / 250 / 400 | [v1.0] |

### 5.4 Befehle (Haltung) [MVP]

Wie die Angriff/Rückzug-Befehle des Originals, gilt für alle eigenen Einheiten:
- **Vorrücken** (Standard): Lane bis zur feindlichen Festung ablaufen.
- **Halten**: an der aktuellen Position bzw. am gesetzten Sammelbanner stehen bleiben und verteidigen.
- **Rückzug**: zurück zur eigenen Festung; dort werden Einheiten langsam geheilt (+2 % HP/s).

## 6. Der Häuptling (Warchief)

Der Häuptling ist eine eigene Einheit (`UnitType.CHIEFTAIN`) mit goldenem Helm, Krone und Kriegsbemalung. Zu Rundenbeginn
steht er in der eigenen Festung und bewacht sie. Der Spieler schickt ihn mit dem Helm-Feld der Befehlsleiste (oder Taste Q)
aufs Schlachtfeld; dort folgt er wie die Soldaten der Lane-KI und der Haltung der Armee. Derselbe Klick ruft ihn zurück in die
Festung, wo er doppelt so schnell heilt. Er hat 320 HP (+40 pro Level) und 16 Schaden (+2,5 pro Level), trifft mit seiner
Axt auch Gegner neben dem Ziel (40 % Schaden im Umkreis von 2,2 Blöcken) und lässt sich kaum zurückstoßen.

Fällt er, erhält der Gegner 100 Gold Kopfgeld und Clan-Erfahrung. Nach **12 s + 2 s pro Level** steht er in der Festung wieder auf
und zieht von selbst wieder los, wenn er vorher losgeschickt war.

| Element | Beschreibung | Stufe |
|---|---|---|
| Häuptlings-Level 1–10 | Erfahrung aus allen Kills der eigenen Armee (25 %) und eigenen Kills (100 %). | [MVP] |
| Blutrausch (Taste R / 6) | Eigene Einheiten in 12 Blöcken um den Häuptling: +30 % Angriffstempo für 8 s. Abklingzeit 30 s. | [MVP] |
| Kampfstampfer (Taste G / 7) | Flächenschlag um den Häuptling, 30 Schaden in 5 Blöcken, schleudert Gegner zurück. Abklingzeit 15 s. | [MVP] |
| Raserei (Taste Y / 8) | Lädt sich durch erlittenen und verursachten Schaden auf. Voll: 10 s +60 % Schaden, +30 % Tempo, Lebensraub 20 % (je Rang +10 % Schaden, +2 s, +5 % Lebensraub). | [v1.0] |
| Fähigkeitspunkte | Pro Level 1 Punkt zum Verstärken einer Fähigkeit (je 3 Ränge), verteilt im Reiter „Häuptling“ des Kriegsmenüs. | [v1.0] |

Die Fähigkeiten löst der Spieler aus der Ferne aus (Feld in der Befehlsleiste oder Taste); sie wirken um den Häuptling herum.
Ist er gefallen, sind sie gesperrt.

## 7. Wirtschaft und Ressourcen

Anzeige oben links wie im Original: **Gold, Erfahrung, Ruf, Bevölkerung**.

| Ressource | Quelle | Verwendung | Stufe |
|---|---|---|---|
| Gold | Passiv +2/s. **Kopfgeld** für jeden Kill: Grundwert = 50 % der Einheitenkosten, multipliziert mit dem **Frontfaktor** 1,0 (eigene Hälfte) bis 2,0 (direkt vor der feindlichen Festung). Wie im Original bekommt man auch für **eigene Verluste** 25 % des Kopfgelds, wenn sie in der feindlichen Hälfte fallen. | Einheiten, Upgrades, Zauber | [MVP] |
| Erfahrung (Clan) | Alle Kills der eigenen Seite. | Helden-Level (Abschnitt 6) | [MVP] |
| Ruf | +1 Rufpunkt pro 400 Clan-Erfahrung, +1 für zerstörten feindlichen Turm. | Schaltet Einheitentypen, Zauber und Festungsausbauten frei (Rufstufen 0–5). | [MVP] |
| Bevölkerung | Limit 20 zu Beginn. | Ausbau „Goblinhütten“: +10 pro Stufe (3 Stufen; 150 / 300 / 500 Gold). | [MVP] |
| Goldmine | Festungsausbau: +1 Gold/s pro Stufe (3 Stufen; 200 / 400 / 700), wächst sichtbar im Hof. | Langfristige Wirtschaft gegen frühes Zergen abwägen. | [v1.0] |

## 8. Zauber

Werden in der Draufsicht gewirkt: Zauber in der Befehlsleiste anklicken (oder Taste), dann die Zielstelle im Feld; ein Kreis am Boden zeigt den Wirkungsbereich. Kosten Gold, haben Abklingzeiten und werden über Rufstufen und Gold stärker (Stufe 1–3).

| Zauber | Wirkung (Stufe 1) | Gold | Abklingzeit | Ruf nötig | Stufe |
|---|---|---|---|---|---|
| Feuerball | 50 Flächenschaden, Radius 3 | 60 | 12 s | 0 | [MVP] |
| Heilende Pilze | Heilt eigene Einheiten im Radius 6 um 60 HP | 80 | 25 s | 1 | [MVP] |
| Wurzelfessel | Gegner im Radius 5 können sich 4 s nicht bewegen | 90 | 30 s | 2 | [v1.0] |
| Blitzsturm | 5 Blitze auf zufällige Gegner im Radius 8, je 45 Schaden | 150 | 45 s | 3 | [v1.0] |
| Meteor | 300 Schaden im Radius 5, auch gegen Gebäude | 300 | 90 s | 5 | [v1.0] |

## 9. Festungsausbauten

| Ausbau | Wirkung pro Stufe | Stufen / Kosten | Stufe |
|---|---|---|---|
| Turm | +5 Schaden, +2 Reichweite | 3 / 150, 300, 500 | [MVP] |
| Mauern | +500 Festungs-HP | 3 / 200, 400, 650 | [MVP] |
| Kanone | Steht auf der Frontmauer, 40 Flächenschaden (Radius 3) alle 4 s; je weitere Stufe +15 Schaden, −0,5 s Nachladezeit | 1+3 / 250, 200, 350, 500 | [v1.0] |
| Goblinhütten | +10 Bevölkerung | 3 / 150, 300, 500 | [MVP] |
| Goldmine | +1 Gold/s | 3 / 200, 400, 700 | [v1.0] |

## 10. Steuerung und Kamera

- **Nur Draufsicht** [v1.1]: Vom Countdown bis zum Matchende blickt die Kamera schräg von oben auf das Schlachtfeld, die eigene
  Festung liegt immer links. Es gibt keine Ich- oder Verfolgerperspektive und nichts zum Umschalten. Schwenken mit W/A/S/D oder
  den Pfeiltasten (Sprinttaste doppelt so schnell) oder durch Ziehen mit gedrückter linker Maustaste, zoomen mit dem Mausrad
  oder +/- (14 bis 80 Blöcke Abstand), Leertaste springt zum Häuptling. Geräusche hört man an der Bildmitte.
- Der Mauszeiger ist im Match immer frei (unsichtbarer Bildschirm `CommandScreen`). Alles ist per Maus bedienbar.
- **Befehlsleiste unten links** [v1.1]: obere Reihe die acht Einheiten, Haltung und Sammelbanner; untere Reihe die fünf Zauber,
  der Häuptling (losschicken/zurückrufen), seine drei Fähigkeiten und das Kriegsmenü. Jedes Feld zeigt Symbol, Taste, Preis bzw.
  Abklingzeit und ist grau, wenn Gold, Ruf oder Abklingzeit fehlen; der Tooltip nennt den Grund. Zauber und Sammelbanner warten
  nach dem Klick auf einen Klick ins Feld, Rechtsklick oder Esc bricht ab.
- **Kriegsmenü** (Taste B oder Buch in der Leiste): Tabs *Einheiten / Upgrades / Festung / Zauber* [MVP] und *Häuptling*
  (Fähigkeitsränge, Raserei, losschicken) [v1.0], zeigt Kosten, Stufe, gesperrte Einträge und Erklärungen als Tooltip.
- **Schnelltasten** (frei belegbar über die Minecraft-Tastenbelegung):
  - Einheiten rekrutieren: Z / X / C / V, dazu U / I / O / M für Schamane, Wolfsreiter, Troll und Katapult
  - Häuptling losschicken/zurückrufen: Q (oder 9); Haltung: H; Sammelbanner: N
  - Zauber: 1–5 (Feuerball und Pilze auch J / K); Fähigkeiten: 6–8 bzw. R / G / Y
  - Esc bricht einen gewählten Zauber ab, sonst Pausemenü; T öffnet den Chat
- **HUD**: oben Mitte beide Festungs-Lebensbalken und Spielzeit, darunter Runden, Sudden Death (nur wenn eingeschaltet) und freie
  Fähigkeitspunkte; oben links Gold, Ruf, Armee, Haltung und Gegner; unten links die Befehlsleiste; unten rechts der Häuptling
  (Zustand, Leben, Erfahrung, Raserei). Vanilla-Hotbar, Herzen und Fadenkreuz sind ausgeblendet. Die Leiste passt auch in die
  kleinste GUI (320 × 240); der Client-Test prüft, dass kein Feld abgeschnitten ist.

## 10a. KI-Gegner und Serien [v1.0]

- `/gf start ki [leicht|normal|schwer]`: ein Clan wird von der KI geführt (Klasse `AiCommander`). Sie trifft alle 1–3 s eine Entscheidung:
  Haltung (Rückzug, wenn deutlich unterlegen und der Feind in der eigenen Hälfte steht), Zauber auf die dichteste Gegnergruppe,
  Upgrades und Ausbauten nach Bedarf (Hütten bei voller Armee, Mauern bei angeschlagener Festung, Kanone bei Angriff, Goldmine)
  und Rekrutierung nach einer Soll-Mischung, die ab „normal“ auf die gegnerische Armee reagiert (Wolfsreiter gegen Fernkämpfer usw.).
- Die KI führt ihren Häuptling selbst: Sie schickt ihn los, sobald ihre Armee stark genug ist oder der Feind in ihrer Hälfte steht,
  ruft ihn ab „normal“ unter 30 % Leben zurück, setzt Kampfstampfer, Raserei und Blutrausch passend ein und verteilt
  Fähigkeitspunkte. Grundeinkommen: leicht ×0,75, normal ×1,05, schwer ×1,3 (+60 Startgold).
- `/gf start bo3` / `bo5`: Best-of-Serie. Nach jeder Runde 20 s Pause, dann neue Runde mit frisch gebauter Arena; Spieler bleiben
  dabei in der Arena, ihre Sicherung von vor der ersten Runde wird erst am Ende der Serie zurückgegeben. Das HUD zeigt den Stand.

## 10b. Klang [v1.0]

- Eigene, per Code synthetisierte Klänge (`tools/generate_sounds.py`, keine fremden Aufnahmen): **Kriegstrommeln** als Schlachtmusik
  (laufen über den Musik-Regler in Schleife, solange gekämpft wird; im Sudden Death schneller und lauter; die normale Minecraft-Musik
  pausiert so lange), **Kriegshorn** zum Schlachtbeginn und beim Sammelruf, **Münzklimpern** beim Kopfgeld.
- Countdown-, Kopfgeld- und Warnklänge hört nur der betroffene Spieler, nicht der Gegner nebenan.

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
- Modelle: [MVP] vanilla-artige humanoide Modelle (kleiner skaliert) mit eigenen Goblin-Texturen in Clanfarben, keine Fremd-Mods außer Fabric API. [v1.0] Wolfsreiter sitzen auf einem großen, dunklen Warg (Wolfsmodell), Katapulte schieben einen Werfer-Karren. [Später] komplett eigene Modelle und Animationen.
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
9. Kriegsmenü (B), Schnelltasten, HUD, Kamera (bis v1.0 Third-Person, ab v1.1 nur Draufsicht).

Mit dem Feinschliff (v1.0) dazugekommen: Schamane, Troll, Wolfsreiter, Katapult, Champion-Fähigkeiten und Raserei, Kanone, Goldmine, Wurzelfessel, Blitzsturm und Meteor, Kommandoansicht, eigene Klänge, Sudden Death, Best-of-Serien und KI-Gegner.
Mit v1.1: Häuptling als eigene Einheit, nur noch Draufsicht mit Maussteuerung und Befehlsleiste unten links, Sudden Death nur noch auf Wunsch. Noch offen (**[Später]**): drei Lanes, 2v2, gestaltete Arena-Vorlage und komplett eigene Modelle.
