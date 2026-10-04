#!/usr/bin/env python3
"""Erzeugt die eigenen Klänge des Mods (Kriegstrommeln, Münzen, Kriegshorn) per Synthese.

Alles ist selbst gerechnet, es werden keine fremden Aufnahmen verwendet. Benötigt numpy und ffmpeg
(mit libvorbis). Aufruf aus dem Repo-Wurzelverzeichnis:

    python3 tools/generate_sounds.py

Die Ergebnisse landen als .ogg in src/main/resources/assets/goblinforest/sounds/.
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "goblinforest", "sounds")


def env(n, attack=0.002, decay=0.2):
    t = np.arange(n) / SR
    return np.minimum(1, t / attack) * np.exp(-t / decay)


def drum(f0, f1, dur, decay, noise=0.0, seed=0):
    """Fell-Trommel: Sinus mit fallender Tonhöhe plus etwas gefiltertes Rauschen."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = f1 + (f0 - f1) * np.exp(-t * 25)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.001, decay)
    if noise:
        r = np.random.default_rng(seed).normal(0, 1, n)
        r = np.convolve(r, np.ones(8) / 8, "same")
        s += noise * r * env(n, 0.0005, decay * 0.25)
    return s


def slap(dur=0.12, seed=1):
    n = int(dur * SR)
    r = np.random.default_rng(seed).normal(0, 1, n)
    r = r - np.convolve(r, np.ones(30) / 30, "same")
    return r * env(n, 0.0005, 0.03) * 0.5


def shaker(dur=0.08, seed=2):
    n = int(dur * SR)
    r = np.diff(np.concatenate([[0], np.random.default_rng(seed).normal(0, 1, n)]))
    return r * env(n, 0.004, 0.02) * 0.12


def place(buf, snd, at, gain=1.0):
    i = int(at * SR)
    j = min(len(buf), i + len(snd))
    buf[i:j] += snd[: j - i] * gain


def war_drums():
    """Zwei Takte bei 104 BPM, nahtlos wiederholbar (der Server spielt sie alle 92 Ticks neu an)."""
    beat = 60 / 104
    bars = 2
    length = beat * 4 * bars
    buf = np.zeros(int(length * SR) + 1)
    kick = drum(120, 48, 0.6, 0.25, 0.3)
    tom = drum(200, 110, 0.4, 0.18, 0.4, 3)
    tom_low = drum(150, 80, 0.5, 0.22, 0.4, 4)
    for bar in range(bars):
        b0 = bar * 4 * beat
        for x in (0, 1.5, 2):
            place(buf, kick, b0 + x * beat)
        place(buf, slap(), b0 + beat, 0.8)
        place(buf, slap(seed=5), b0 + 3 * beat, 0.9)
        if bar == 0:
            place(buf, tom, b0 + 2.5 * beat, 0.6)
            place(buf, tom, b0 + 3.5 * beat, 0.5)
        else:
            for k, x in enumerate((3, 3.25, 3.5, 3.75)):
                place(buf, tom_low if k % 2 else tom, b0 + x * beat, 0.55 + 0.1 * k)
        for h in range(8):
            place(buf, shaker(seed=10 + h), b0 + h * 0.5 * beat, 1.0 if h % 2 else 0.6)
    buf = buf[: int(length * SR)]
    return buf / (np.max(np.abs(buf)) * 1.15)


def coins():
    """Zwei helle Pings, wie aufeinanderfallende Münzen."""
    n = int(0.45 * SR)
    t = np.arange(n) / SR

    def ping(f, d):
        return (np.sin(2 * np.pi * f * t) + 0.3 * np.sin(2 * np.pi * f * 2.76 * t)) * env(n, 0.001, d)

    first = ping(1568, 0.08) * 0.6
    second = np.zeros(n)
    i = int(0.07 * SR)
    second[i:] = ping(2093, 0.12)[: n - i] * 0.7
    out = first + second
    return out / (np.max(np.abs(out)) * 1.3)


def war_horn():
    """Tiefer Hornstoß mit Obertönen, leichtem Vibrato und An- und Abschwellen."""
    dur = 1.8
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = 110 * (1 + 0.02 * np.minimum(1, t / 0.3))
    ph = 2 * np.pi * np.cumsum(f) / SR
    h = sum(np.sin(k * ph) / k ** 1.1 for k in range(1, 9))
    vibrato = 1 + 0.01 * np.sin(2 * np.pi * 5 * t)
    swell = np.minimum(1, t / 0.25) * np.minimum(1, (dur - t) / 0.5)
    out = h * swell * vibrato
    return out / (np.max(np.abs(out)) * 1.2)


def save_ogg(name, samples):
    os.makedirs(OUT, exist_ok=True)
    data = (np.clip(samples, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, name + ".wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(data.tobytes())
        target = os.path.join(OUT, name + ".ogg")
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "4", target], check=True)
    print("geschrieben:", os.path.normpath(target))


if __name__ == "__main__":
    save_ogg("war_drums", war_drums())
    save_ogg("coins", coins())
    save_ogg("war_horn", war_horn())
