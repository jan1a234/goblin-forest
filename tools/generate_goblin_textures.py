#!/usr/bin/env python3
"""Erzeugt die Goblin-Texturen (64x64, Piglin-Modell-Layout) für alle Einheitentypen und beide Clans.

Aufruf aus dem Repo-Wurzelverzeichnis:  python3 tools/generate_goblin_textures.py
Benötigt Pillow (pip install pillow). Die Ergebnisse liegen unter
src/client/resources/assets/goblinforest/textures/entity/goblin/<einheit>_<clan>.png
"""
import os
import random
from PIL import Image

OUT = "src/client/resources/assets/goblinforest/textures/entity/goblin"

SKIN = (122, 163, 58)
SKIN_DARK = (92, 128, 40)
SKIN_LIGHT = (150, 190, 80)
EYE = (250, 214, 40)
PUPIL = (40, 20, 10)
TOOTH = (238, 232, 205)
EAR_INNER = (150, 110, 70)
LEATHER = (110, 72, 40)
LEATHER_DARK = (78, 50, 28)
CLOTH_DARK = (40, 38, 48)
METAL = (150, 152, 160)
METAL_DARK = (100, 102, 110)
BONE = (226, 216, 186)
FUR = (128, 124, 118)
FUR_DARK = (88, 84, 80)
ROBE = (62, 40, 70)
TROLL = (96, 128, 108)
TROLL_DARK = (70, 98, 80)
GOGGLE = (120, 200, 230)
GOLD = (232, 186, 52)
GOLD_DARK = (168, 124, 28)

TEAMS = {
    "red": ((176, 40, 32), (120, 24, 20), (226, 90, 70)),
    "green": ((24, 150, 100), (14, 100, 66), (90, 210, 150)),
}


def box_faces(u, v, w, h, d):
    """Liefert die UV-Rechtecke eines Würfels (Minecraft-Layout) als dict: name -> (x0, y0, x1, y1)."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def fill(img, rect, color, noise=0, rng=None):
    x0, y0, x1, y1 = rect
    for x in range(x0, x1):
        for y in range(y0, y1):
            c = color
            if noise and rng:
                n = rng.randint(-noise, noise)
                c = tuple(max(0, min(255, ch + n)) for ch in color)
            img.putpixel((x, y), c + (255,))


def paint_box(img, u, v, w, h, d, color, noise, rng):
    for rect in box_faces(u, v, w, h, d).values():
        fill(img, rect, color, noise, rng)


def skin(unit):
    return (TROLL, TROLL_DARK) if unit == "troll" else (SKIN, SKIN_DARK)


def head(img, rng, unit, team):
    main, dark, light = TEAMS[team]
    SKIN, SKIN_DARK = skin(unit)
    # Kopf 10x8x8 bei (0,0)
    paint_box(img, 0, 0, 10, 8, 8, SKIN, 8, rng)
    f = box_faces(0, 0, 10, 8, 8)["front"]
    fx, fy = f[0], f[1]
    # Brauen, Augen, Mund
    for x in range(fx + 1, fx + 9):
        img.putpixel((x, fy + 2), SKIN_DARK + (255,))
    for ex in (fx + 2, fx + 6):
        img.putpixel((ex, fy + 3), EYE + (255,))
        img.putpixel((ex + 1, fy + 3), PUPIL + (255,))
    for x in range(fx + 2, fx + 8):
        img.putpixel((x, fy + 6), (60, 40, 30, 255))
    # Haare oben bzw. Kopfbedeckung je Einheit
    top = box_faces(0, 0, 10, 8, 8)["top"]
    if unit == "assassin":
        # Kapuze und Maske
        for name, rect in box_faces(0, 0, 10, 8, 8).items():
            if name != "front":
                fill(img, rect, CLOTH_DARK, 6, rng)
        fill(img, (fx, fy, fx + 10, fy + 2), CLOTH_DARK, 6, rng)
        fill(img, (fx, fy + 5, fx + 10, fy + 8), CLOTH_DARK, 6, rng)
        fill(img, (fx, fy + 4, fx + 10, fy + 5), main, 0, rng)
    elif unit == "archer":
        fill(img, top, main, 6, rng)
        for name in ("right", "left", "back"):
            r = box_faces(0, 0, 10, 8, 8)[name]
            fill(img, (r[0], r[1], r[2], r[1] + 3), main, 6, rng)
    elif unit == "warrior":
        fill(img, top, METAL_DARK, 10, rng)
        for name in ("right", "left", "back"):
            r = box_faces(0, 0, 10, 8, 8)[name]
            fill(img, (r[0], r[1], r[2], r[1] + 2), METAL_DARK, 10, rng)
    elif unit == "shaman":
        # Knochen-Kopfschmuck mit Clanband
        fill(img, top, BONE, 8, rng)
        for name in ("right", "left", "back"):
            r = box_faces(0, 0, 10, 8, 8)[name]
            fill(img, (r[0], r[1], r[2], r[1] + 2), BONE, 8, rng)
            fill(img, (r[0], r[1] + 2, r[2], r[1] + 3), main, 0, rng)
        fill(img, (fx, fy, fx + 10, fy + 1), main, 0, rng)
        for x in range(fx + 1, fx + 9, 2):
            img.putpixel((x, fy + 1), BONE + (255,))
        # Kriegsbemalung unter den Augen
        for ex in (fx + 2, fx + 6):
            img.putpixel((ex, fy + 4), light + (255,))
            img.putpixel((ex + 1, fy + 5), light + (255,))
    elif unit == "wolfRider":
        # Wolfsfell über Kopf und Nacken
        fill(img, top, FUR, 12, rng)
        for name in ("right", "left", "back"):
            r = box_faces(0, 0, 10, 8, 8)[name]
            fill(img, (r[0], r[1], r[2], r[1] + 4), FUR, 12, rng)
        fill(img, (fx, fy, fx + 10, fy + 2), FUR_DARK, 8, rng)
        img.putpixel((fx + 1, fy + 1), TOOTH + (255,))
        img.putpixel((fx + 8, fy + 1), TOOTH + (255,))
    elif unit == "troll":
        # wulstige Stirn, Narbe, Haarbüschel in Clanfarbe
        for x in range(fx, fx + 10):
            img.putpixel((x, fy + 2), TROLL_DARK + (255,))
            img.putpixel((x, fy + 1), TROLL_DARK + (255,))
        for y in range(fy + 1, fy + 6):
            img.putpixel((fx + 7, y), (150, 90, 80, 255))
        fill(img, (top[0] + 3, top[1] + 2, top[0] + 7, top[1] + 6), dark, 6, rng)
    elif unit == "catapult":
        # Kappe in Clanfarbe und Schutzbrille
        fill(img, top, main, 8, rng)
        for name in ("right", "left", "back"):
            r = box_faces(0, 0, 10, 8, 8)[name]
            fill(img, (r[0], r[1], r[2], r[1] + 2), main, 8, rng)
            fill(img, (r[0], r[1] + 3, r[2], r[1] + 4), LEATHER_DARK, 0, rng)
        fill(img, (fx, fy + 3, fx + 10, fy + 4), LEATHER_DARK, 0, rng)
        for ex in (fx + 2, fx + 6):
            fill(img, (ex, fy + 3, ex + 2, fy + 4), GOGGLE, 0, rng)
    elif unit == "chieftain":
        # Goldkrone mit Zacken, Kriegsbemalung in Clanfarbe, Federbusch hinten
        faces = box_faces(0, 0, 10, 8, 8)
        fill(img, top, dark, 6, rng)
        for name in ("right", "left", "back"):
            r = faces[name]
            fill(img, (r[0], r[1], r[2], r[1] + 2), GOLD, 10, rng)
            for x in range(r[0], r[2], 2):
                img.putpixel((x, r[1]), GOLD_DARK + (255,))
        fill(img, (fx, fy, fx + 10, fy + 2), GOLD, 10, rng)
        for x in range(fx, fx + 10, 3):
            img.putpixel((x, fy), (200, 40, 40, 255))
        fill(img, (faces["back"][0] + 3, faces["back"][1] + 2, faces["back"][0] + 7, faces["back"][1] + 6), main, 6, rng)
        for ex in (fx + 2, fx + 6):
            img.putpixel((ex, fy + 4), main + (255,))
            img.putpixel((ex + 1, fy + 4), main + (255,))
        img.putpixel((fx + 4, fy + 5), light + (255,))
        img.putpixel((fx + 5, fy + 5), light + (255,))
    else:
        fill(img, top, (60, 70, 30), 10, rng)
    # Nase 4x4x1 bei (31,1)
    paint_box(img, 31, 1, 4, 4, 1, SKIN_DARK, 6, rng)
    # Hauer 1x2x1 bei (2,4) und (2,0)
    paint_box(img, 2, 4, 1, 2, 1, TOOTH, 0, rng)
    paint_box(img, 2, 0, 1, 2, 1, TOOTH, 0, rng)
    # Ohren 1x5x4 bei (51,6) und (39,6)
    for u in (51, 39):
        paint_box(img, u, 6, 1, 5, 4, SKIN, 6, rng)
        faces = box_faces(u, 6, 1, 5, 4)
        fill(img, faces["right"], EAR_INNER, 6, rng)
        fill(img, faces["left"], EAR_INNER, 6, rng)


def body(img, rng, unit, team):
    main, dark, light = TEAMS[team]
    SKIN, SKIN_DARK = skin(unit)
    # Körper 8x12x4 bei (16,16)
    if unit == "slave":
        paint_box(img, 16, 16, 8, 12, 4, SKIN, 8, rng)
        # Lendenschurz und Strick in Clanfarbe
        for name, rect in box_faces(16, 16, 8, 12, 4).items():
            if name in ("top", "bottom"):
                continue
            x0, y0, x1, y1 = rect
            fill(img, (x0, y1 - 3, x1, y1), LEATHER, 10, rng)
            fill(img, (x0, y1 - 4, x1, y1 - 3), main, 0, rng)
    elif unit == "assassin":
        paint_box(img, 16, 16, 8, 12, 4, CLOTH_DARK, 6, rng)
        for name, rect in box_faces(16, 16, 8, 12, 4).items():
            if name in ("front", "back"):
                x0, y0, x1, y1 = rect
                for i in range(y1 - y0):
                    x = x0 + (i * (x1 - x0)) // (y1 - y0)
                    fill(img, (x, y0 + i, min(x + 2, x1), y0 + i + 1), main, 0, rng)
    elif unit == "shaman":
        # Robe mit Clansaum und Knochenkette
        paint_box(img, 16, 16, 8, 12, 4, ROBE, 8, rng)
        for name, rect in box_faces(16, 16, 8, 12, 4).items():
            if name in ("top", "bottom"):
                continue
            x0, y0, x1, y1 = rect
            fill(img, (x0, y1 - 2, x1, y1), main, 4, rng)
        f = box_faces(16, 16, 8, 12, 4)["front"]
        for i, x in enumerate(range(f[0] + 1, f[2] - 1)):
            img.putpixel((x, f[1] + 2 + (1 if 1 < i < 4 else 0)), BONE + (255,))
        fill(img, (f[0] + 3, f[1] + 4, f[0] + 5, f[1] + 6), BONE, 0, rng)
    elif unit == "troll":
        # nackter Oberkörper mit Clan-Lendenschurz und Narben
        paint_box(img, 16, 16, 8, 12, 4, SKIN, 10, rng)
        for name, rect in box_faces(16, 16, 8, 12, 4).items():
            if name in ("top", "bottom"):
                continue
            x0, y0, x1, y1 = rect
            fill(img, (x0, y1 - 4, x1, y1), main, 8, rng)
            fill(img, (x0, y1 - 5, x1, y1 - 4), LEATHER_DARK, 0, rng)
        f = box_faces(16, 16, 8, 12, 4)["front"]
        for i in range(4):
            img.putpixel((f[0] + 2 + i, f[1] + 2 + i), (150, 90, 80, 255))
            img.putpixel((f[0] + 5 - i // 2, f[1] + 4 + i), TROLL_DARK + (255,))
    elif unit == "chieftain":
        # Brustpanzer mit Goldbeschlägen, Umhang in Clanfarbe, breiter Gürtel mit Schnalle
        paint_box(img, 16, 16, 8, 12, 4, METAL_DARK, 10, rng)
        faces = box_faces(16, 16, 8, 12, 4)
        for name, rect in faces.items():
            if name in ("top", "bottom"):
                continue
            x0, y0, x1, y1 = rect
            fill(img, (x0, y0 + 8, x1, y0 + 10), LEATHER_DARK, 4, rng)
            fill(img, (x0, y1 - 2, x1, y1), main, 6, rng)
        fill(img, faces["back"], main, 8, rng)
        fill(img, faces["top"], main, 6, rng)
        f = faces["front"]
        fill(img, (f[0] + 1, f[1] + 1, f[2] - 1, f[1] + 7), METAL, 12, rng)
        for x in range(f[0] + 1, f[2] - 1):
            img.putpixel((x, f[1] + 1), GOLD + (255,))
        fill(img, (f[0] + 3, f[1] + 3, f[0] + 5, f[1] + 5), GOLD, 0, rng)
        fill(img, (f[0] + 3, f[1] + 8, f[0] + 5, f[1] + 10), GOLD, 0, rng)
    else:
        paint_box(img, 16, 16, 8, 12, 4, main, 10, rng)
        for name, rect in box_faces(16, 16, 8, 12, 4).items():
            if name in ("top", "bottom"):
                continue
            x0, y0, x1, y1 = rect
            fill(img, (x0, y0 + 8, x1, y0 + 9), LEATHER_DARK, 0, rng)
            fill(img, (x0, y0, x1, y0 + 1), dark, 0, rng)
        if unit == "warrior":
            f = box_faces(16, 16, 8, 12, 4)["front"]
            fill(img, (f[0] + 1, f[1] + 1, f[2] - 1, f[1] + 7), METAL, 12, rng)
            fill(img, (f[0] + 3, f[1] + 2, f[0] + 5, f[1] + 6), light, 0, rng)
        if unit == "wolfRider":
            # Fellumhang auf Schultern und Rücken
            for name, rect in box_faces(16, 16, 8, 12, 4).items():
                if name == "bottom":
                    continue
                x0, y0, x1, y1 = rect
                if name == "back":
                    fill(img, (x0, y0, x1, y0 + 9), FUR, 12, rng)
                else:
                    fill(img, (x0, y0, x1, min(y1, y0 + 3)), FUR, 12, rng)
        if unit == "catapult":
            # Lederschürze mit Werkzeugtasche
            f = box_faces(16, 16, 8, 12, 4)["front"]
            fill(img, (f[0] + 1, f[1] + 2, f[2] - 1, f[3]), LEATHER, 8, rng)
            fill(img, (f[0] + 2, f[1] + 6, f[0] + 6, f[1] + 8), LEATHER_DARK, 0, rng)
            img.putpixel((f[0] + 3, f[1] + 5), METAL + (255,))
        if unit == "archer":
            # Köcherriemen quer über die Brust
            f = box_faces(16, 16, 8, 12, 4)["front"]
            for i in range(8):
                img.putpixel((f[0] + i, f[1] + 7 - i), LEATHER_DARK + (255,))
    # Arme 4x12x4 bei (40,16) und (32,48)
    for u, v in ((40, 16), (32, 48)):
        if unit == "assassin":
            paint_box(img, u, v, 4, 12, 4, CLOTH_DARK, 6, rng)
        elif unit == "shaman":
            paint_box(img, u, v, 4, 12, 4, ROBE, 8, rng)
            for name, rect in box_faces(u, v, 4, 12, 4).items():
                if name not in ("top", "bottom"):
                    x0, y0, x1, y1 = rect
                    fill(img, (x0, y1 - 4, x1, y1), SKIN, 8, rng)
                    fill(img, (x0, y1 - 5, x1, y1 - 4), BONE, 0, rng)
        elif unit == "troll":
            paint_box(img, u, v, 4, 12, 4, SKIN, 10, rng)
            for name, rect in box_faces(u, v, 4, 12, 4).items():
                if name not in ("top", "bottom"):
                    x0, y0, x1, y1 = rect
                    fill(img, (x0, y0 + 7, x1, y0 + 9), METAL_DARK, 8, rng)
        elif unit == "wolfRider":
            paint_box(img, u, v, 4, 12, 4, SKIN, 8, rng)
            for name, rect in box_faces(u, v, 4, 12, 4).items():
                if name == "bottom":
                    continue
                x0, y0, x1, y1 = rect
                fill(img, (x0, y0, x1, min(y1, y0 + 4)), FUR, 10, rng)
                if name != "top":
                    fill(img, (x0, y1 - 5, x1, y1 - 1), LEATHER, 8, rng)
                    fill(img, (x0, y1 - 3, x1, y1 - 2), main, 0, rng)
        elif unit == "chieftain":
            paint_box(img, u, v, 4, 12, 4, SKIN, 8, rng)
            for name, rect in box_faces(u, v, 4, 12, 4).items():
                if name == "bottom":
                    continue
                if name == "top":
                    fill(img, rect, METAL, 10, rng)
                    continue
                x0, y0, x1, y1 = rect
                fill(img, (x0, y0, x1, y0 + 4), METAL, 10, rng)
                fill(img, (x0, y0 + 4, x1, y0 + 5), GOLD_DARK, 0, rng)
                fill(img, (x0, y1 - 5, x1, y1 - 1), GOLD, 8, rng)
        elif unit in ("warrior", "archer", "catapult"):
            paint_box(img, u, v, 4, 12, 4, SKIN, 8, rng)
            for name, rect in box_faces(u, v, 4, 12, 4).items():
                if name in ("top",):
                    fill(img, rect, main, 6, rng)
                    continue
                if name == "bottom":
                    continue
                x0, y0, x1, y1 = rect
                fill(img, (x0, y0, x1, y0 + 4), main, 8, rng)
                fill(img, (x0, y1 - 4, x1, y1 - 1), LEATHER if unit == "archer" else METAL_DARK, 8, rng)
        else:
            paint_box(img, u, v, 4, 12, 4, SKIN, 8, rng)
            for name, rect in box_faces(u, v, 4, 12, 4).items():
                if name not in ("top", "bottom"):
                    x0, y0, x1, y1 = rect
                    fill(img, (x0, y0 + 5, x1, y0 + 6), main, 0, rng)
    # Beine 4x12x4 bei (0,16) und (16,48)
    for u, v in ((0, 16), (16, 48)):
        legs = {"assassin": CLOTH_DARK, "slave": SKIN, "troll": SKIN, "shaman": ROBE, "wolfRider": FUR_DARK}.get(unit, LEATHER)
        paint_box(img, u, v, 4, 12, 4, legs, 8, rng)
        for name, rect in box_faces(u, v, 4, 12, 4).items():
            if name not in ("top", "bottom"):
                x0, y0, x1, y1 = rect
                fill(img, (x0, y1 - 3, x1, y1), LEATHER_DARK, 6, rng)
                if unit == "slave":
                    fill(img, (x0, y0, x1, y0 + 3), LEATHER, 8, rng)


def make(unit, team):
    rng = random.Random(f"{unit}-{team}")
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    head(img, rng, unit, team)
    body(img, rng, unit, team)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for unit in ("slave", "warrior", "archer", "assassin", "shaman", "wolfRider", "troll", "catapult", "chieftain"):
        for team in TEAMS:
            path = os.path.join(OUT, f"{unit.lower()}_{team}.png")
            make(unit, team).save(path)
            print("geschrieben:", path)


if __name__ == "__main__":
    main()
