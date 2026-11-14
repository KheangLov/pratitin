"""
Synthesises the Love tab's sound theme (all original, generated from scratch):

  love_music.ogg      32 s seamless music-box lullaby in F major (8 bars, 60 bpm) over a soft pad
  love_tap.ogg        double heartbeat + sparkle, for tapping the heart
  love_chime.ogg      bell arpeggio, used as the anniversary notification sound
  love_celebrate.ogg  shimmering ascending glissando, for saving and milestones

Usage:  python3 tools/make_love_sounds.py app/src/main/res/raw
Needs numpy, scipy and ffmpeg (libvorbis).
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile
from scipy.signal import fftconvolve, butter, sosfilt

SR = 44100
rng = np.random.default_rng(14)


def note(n):
    """MIDI note -> Hz."""
    return 440.0 * 2 ** ((n - 69) / 12)


NAMES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def m(name):
    """'Bb5' -> MIDI."""
    base = NAMES[name[0]]
    i = 1
    if name[i] == "b":
        base -= 1; i += 1
    elif name[i] == "#":
        base += 1; i += 1
    return base + 12 * (int(name[i:]) + 1)


def bell(freq, dur=2.2, amp=0.3, bright=1.0):
    """Music-box tine: inharmonic partials, instant attack, exponential decay."""
    t = np.arange(int(dur * SR)) / SR
    partials = [(1.0, 1.0, 1.0), (2.0, 0.32 * bright, 1.8), (3.01, 0.10 * bright, 2.8), (4.17, 0.05 * bright, 4.0), (5.43, 0.02 * bright, 5.5)]
    out = np.zeros_like(t)
    for ratio, a, dk in partials:
        f = freq * ratio
        if f > SR / 2.2:
            continue
        out += a * np.sin(2 * np.pi * f * t) * np.exp(-t * dk * 1.6)
    attack = np.minimum(1.0, t / 0.003)
    return amp * out * attack


def pad(freqs, dur, amp=0.05):
    t = np.arange(int(dur * SR)) / SR
    out = np.zeros_like(t)
    for f in freqs:
        for det in (-0.12, 0.0, 0.12):  # slight chorus
            ff = f * 2 ** (det / 12)
            vib = 1 + 0.002 * np.sin(2 * np.pi * 4.3 * t + rng.uniform(0, 6))
            out += np.sin(2 * np.pi * ff * vib * t) + 0.18 * np.sin(4 * np.pi * ff * vib * t)
    env = np.minimum(1.0, t / 1.2) * np.minimum(1.0, (dur - t) / 1.2)
    return amp * out * env / (len(freqs) * 3)


def reverb_ir(seconds=2.8, decay=3.2):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-t * decay)
    sos = butter(2, 5200, "low", fs=SR, output="sos")  # darker tail
    ir = sosfilt(sos, ir)
    ir[0] = 0
    return ir / np.sqrt(np.sum(ir ** 2))


IR = reverb_ir()


def wet(x, mix=0.28):
    w = fftconvolve(x, IR)[: len(x) + len(IR) - 1]
    dry = np.concatenate([x, np.zeros(len(IR) - 1)])
    return dry * (1 - mix) + w * mix


def place(buf, sig, at):
    i = int(at * SR)
    end = min(len(buf), i + len(sig))
    buf[i:end] += sig[: end - i]


def save(name, x, outdir, quality=5):
    x = x / (np.max(np.abs(x)) + 1e-9) * 0.89
    # gentle fade-out on the last 8 ms to avoid clicks
    x[-int(0.008 * SR):] *= np.linspace(1, 0, int(0.008 * SR))
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        wavfile.write(f.name, SR, (x * 32767).astype(np.int16))
        tmp = f.name
    out = os.path.join(outdir, name)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", str(quality), out], check=True)
    os.unlink(tmp)
    print(f"{name}: {os.path.getsize(out) // 1024} KB")


# ------------------------------------------------------------------ the lullaby
def lullaby():
    bpm = 60.0
    beat = 60.0 / bpm
    bars = 8
    length = bars * 4 * beat  # 32 s
    tail = 4.0
    buf = np.zeros(int((length + tail) * SR))

    # (melody notes as (name, beats)) per bar; original tune
    melody = [
        [("A5", 1), ("C6", 1), ("F6", 2)],
        [("E6", 1.5), ("D6", 0.5), ("C6", 2)],
        [("D6", 1), ("C6", 1), ("Bb5", 1), ("A5", 1)],
        [("G5", 3), ("C6", 1)],
        [("A5", 1), ("C6", 1), ("F6", 1), ("G6", 1)],
        [("A6", 1.5), ("G6", 0.5), ("F6", 2)],
        [("D6", 1), ("F6", 1), ("E6", 1), ("D6", 1)],
        [("C6", 2), ("Bb5", 1), ("G5", 1)],
    ]
    chords = [
        ["F3", "C4", "F4", "A4"], ["A2", "E3", "A3", "C4"], ["Bb2", "F3", "Bb3", "D4"], ["C3", "G3", "C4", "E4"],
        ["F3", "C4", "F4", "A4"], ["D3", "A3", "D4", "F4"], ["G2", "D3", "G3", "Bb3"], ["C3", "G3", "C4", "E4"],
    ]
    for b in range(bars):
        t0 = b * 4 * beat
        # melody, with a tiny humanised timing/velocity wobble
        t = t0
        for name, beats in melody[b]:
            place(buf, bell(note(m(name)), 2.6, 0.30 * rng.uniform(0.9, 1.05)), t + rng.uniform(-0.006, 0.006))
            # faint octave shimmer on long notes
            if beats >= 2:
                place(buf, bell(note(m(name) + 12), 1.4, 0.05, 0.6), t + 0.01)
            t += beats * beat
        # broken-chord accompaniment in eighths: 1 5 8 10 8 5 8 5
        ch = [note(m(n)) for n in chords[b]]
        pattern = [0, 1, 2, 3, 2, 1, 2, 1]
        for i, idx in enumerate(pattern):
            place(buf, bell(ch[idx], 1.6, 0.085 if i % 2 == 0 else 0.065, 0.5), t0 + i * beat / 2)
        # pad, overlapping slightly into the next bar
        place(buf, pad([note(m(n)) for n in chords[b][1:]], 4 * beat + 1.2), t0 - 0.2 if b else t0)

    x = wet(buf, 0.32)
    # seamless loop: fold everything after the loop point back onto the start
    n = int(length * SR)
    loop = x[:n].copy()
    rest = x[n:]
    loop[: len(rest)] += rest
    return loop


def tap():
    buf = np.zeros(int(1.6 * SR))
    for k, at in enumerate((0.0, 0.2)):
        d = 0.16
        t = np.arange(int(d * SR)) / SR
        f = 62 * np.exp(-t * 6) + 38
        thump = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 26) * (1.0 if k == 0 else 0.75)
        place(buf, thump * 0.9, at)
    place(buf, bell(note(m("C7")), 1.2, 0.10), 0.34)
    place(buf, bell(note(m("E7")), 1.1, 0.08), 0.40)
    place(buf, bell(note(m("A7")), 1.0, 0.05), 0.46)
    return wet(buf, 0.22)


def chime():
    buf = np.zeros(int(3.0 * SR))
    for i, n in enumerate(["F5", "A5", "C6", "F6"]):
        place(buf, bell(note(m(n)), 2.4, 0.32 if i < 3 else 0.38), i * 0.13)
    place(buf, bell(note(m("A6")), 2.0, 0.10, 0.5), 0.52)
    return wet(buf, 0.3)


def celebrate():
    buf = np.zeros(int(3.2 * SR))
    scale = ["F5", "G5", "A5", "C6", "D6", "F6", "G6", "A6", "C7", "D7", "F7"]
    for i, n in enumerate(scale):
        place(buf, bell(note(m(n)), 1.8, 0.16 + 0.01 * i, 0.8), i * 0.055)
    for n in ["F6", "A6", "C7"]:
        place(buf, bell(note(m(n)), 2.4, 0.18), 0.68)
    # airy shimmer
    t = np.arange(int(1.8 * SR)) / SR
    sh = rng.standard_normal(len(t)) * np.exp(-t * 2.5) * 0.04
    sos = butter(2, [6000, 12000], "band", fs=SR, output="sos")
    place(buf, sosfilt(sos, sh), 0.6)
    return wet(buf, 0.3)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw"
    os.makedirs(out, exist_ok=True)
    save("love_music.ogg", lullaby(), out, quality=3)
    save("love_tap.ogg", tap(), out)
    save("love_chime.ogg", chime(), out)
    save("love_celebrate.ogg", celebrate(), out)
