"""
Composes and renders the Love tab's romantic songs (all original, synthesised from scratch,
stereo, each a seamless loop):

  love_song_serenade.ogg   "Piano Serenade"      slow 3/4 piano waltz in C over warm strings (66 bpm, 16 bars)
  love_song_moonlight.ogg  "Moonlight on the Moat" Khmer-flavoured pentatonic love song: khim tremolo,
                                                   bamboo-flute lead, soft skor heartbeat (70 bpm, 12 bars)
  love_song_forever.ogg    "Forever"             strings & harp ballad in D with a flute melody (64 bpm, 12 bars)

The music-box lullaby (love_song_musicbox.ogg) and the effects come from make_love_sounds.py.

Usage:  python3 tools/make_love_songs.py app/src/main/res/raw
Needs numpy, scipy and ffmpeg (libvorbis).
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile
from scipy.signal import butter, fftconvolve, sosfilt

SR = 44100
rng = np.random.default_rng(2611)

NAMES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def m(name):
    base = NAMES[name[0]]; i = 1
    if name[i] == "b": base -= 1; i += 1
    elif name[i] == "#": base += 1; i += 1
    return base + 12 * (int(name[i:]) + 1)


def hz(n):
    return 440.0 * 2 ** (((m(n) if isinstance(n, str) else n) - 69) / 12)


def lowpass(x, f, order=2):
    return sosfilt(butter(order, f, "low", fs=SR, output="sos"), x)


def bandpass(x, lo, hi):
    return sosfilt(butter(2, [lo, hi], "band", fs=SR, output="sos"), x)


# ================================================================== instruments (mono)

def piano(freq, dur, vel=0.7, ring=1.6):
    """Additive piano: inharmonic partials, hammer-position comb, three detuned strings,
    two-stage decay, a felt hammer thump, and a damper release after `dur` (+ring)."""
    total = dur + ring
    t = np.arange(int(total * SR)) / SR
    B = 0.00012 * (freq / 261.6) ** 1.2
    out = np.zeros_like(t)
    base_tau = 4.2 * (261.6 / freq) ** 0.55
    for n in range(1, 16):
        fn = n * freq * np.sqrt(1 + B * n * n)
        if fn > 10000:
            break
        a = (1.0 / n ** (1.55 - 0.6 * vel)) * (abs(np.sin(np.pi * n / 7.3)) + 0.08)
        ts = base_tau / (1 + 0.22 * (n - 1))
        tf = ts / 7
        env = 0.55 * np.exp(-t / tf) + 0.45 * np.exp(-t / ts)
        for cents, w in ((-0.9, 0.33), (0.0, 0.34), (1.1, 0.33)):
            f = fn * 2 ** (cents / 1200)
            out += a * w * env * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28))
    # felt hammer
    nlen = int(0.02 * SR)
    thump = bandpass(rng.standard_normal(nlen), 300, 2400) * np.exp(-np.arange(nlen) / (0.004 * SR))
    out[:nlen] += thump * 0.02 * vel
    attack = np.minimum(1.0, t / 0.0025)
    damper = np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.35))
    return out * attack * damper * vel * 0.5


def strings(freqs, dur, amp=0.05, attack=1.1, release=2.4, bright=2600):
    """Ensemble pad: five detuned band-limited saws per note, delayed vibrato, soft low-pass."""
    total = dur + release
    t = np.arange(int(total * SR)) / SR
    out = np.zeros_like(t)
    vib_depth = 0.0025 * np.minimum(1.0, np.maximum(0.0, t - 0.4) / 0.8)
    for f in freqs:
        for v in range(5):
            det = 2 ** (rng.uniform(-9, 9) / 1200)
            vib = 1 + vib_depth * np.sin(2 * np.pi * (5.0 + rng.uniform(-0.4, 0.4)) * t + rng.uniform(0, 6))
            phase = 2 * np.pi * np.cumsum(f * det * vib) / SR + rng.uniform(0, 6)
            for k in range(1, 11):
                if f * k > 7000:
                    break
                out += np.sin(k * phase) / k * (0.85 ** k)
    env = np.minimum(1.0, t / attack) * np.where(t < dur, 1.0, np.exp(-(t - dur) / (release / 3)))
    return lowpass(out, bright) * env * amp * 3.2 / (len(freqs) * 5)


def flute(freq, dur, amp=0.2, vib=True):
    """Breathy flute: near-sine tone, chiff, breath noise around the pitch, delayed vibrato."""
    rel = 0.25
    total = dur + rel
    t = np.arange(int(total * SR)) / SR
    depth = 0.006 * np.minimum(1.0, np.maximum(0.0, t - 0.25) / 0.5) if vib else 0
    ph = 2 * np.pi * np.cumsum(freq * (1 + depth * np.sin(2 * np.pi * 4.9 * t))) / SR
    tone = np.sin(ph) + 0.22 * np.sin(2 * ph) + 0.06 * np.sin(3 * ph)
    breath = bandpass(rng.standard_normal(len(t)), max(200, freq * 0.9), min(16000, freq * 1.6)) * 0.03
    chiff = bandpass(rng.standard_normal(len(t)), 1500, 5000) * np.exp(-t / 0.02) * 0.08
    env = np.minimum(1.0, t / 0.07) * np.where(t < dur, 1.0, np.exp(-(t - dur) / (rel / 4)))
    swell = 0.85 + 0.15 * np.minimum(1.0, t / max(dur, 0.1))
    return (tone + breath + chiff) * env * swell * amp


def pluck(freq, dur, amp=0.3, bright=0.55, decay=0.996):
    """Karplus–Strong string, computed one period at a time (vectorised)."""
    n = max(2, int(round(SR / freq)))
    periods = int(dur * SR / n) + 1
    buf = lowpass(rng.standard_normal(n) * 0.8, 1200 + 9000 * bright, 1)
    out = np.empty(periods * n)
    for i in range(periods):
        out[i * n:(i + 1) * n] = buf
        buf = decay * 0.5 * (buf + np.roll(buf, -1))
    out = out[: int(dur * SR)]
    t = np.arange(len(out)) / SR
    return out * amp * np.minimum(1.0, t / 0.002)


def harp(freq, dur, amp=0.25):
    x = pluck(freq, dur, amp, bright=0.35, decay=0.9985)
    return x + 0.25 * lowpass(x, 900)


def khim(freq, dur, amp=0.22, tremolo=True):
    """Khmer hammered dulcimer: bright paired strings, rapid tremolo strikes on long notes."""
    out = np.zeros(int((dur + 1.2) * SR))
    strikes = [0.0]
    if tremolo and dur > 0.6:
        step = 0.105
        strikes = list(np.arange(0, dur - 0.05, step))
    for i, at in enumerate(strikes):
        v = amp * (1.0 if i == 0 else 0.55 + 0.12 * np.sin(i * 1.7)) * (1 - 0.25 * at / max(dur, 0.1))
        s = pluck(freq, 1.2, v, bright=0.55, decay=0.9975) + pluck(freq * 1.003, 1.2, v * 0.45, bright=0.5, decay=0.997)
        place(out, s, at)
    return out


def skor(amp=0.5, low=True):
    """Soft Khmer barrel drum: pitch-dropping body + a little skin slap."""
    d = 0.6
    t = np.arange(int(d * SR)) / SR
    f = (95 if low else 150) * np.exp(-t * 5) + (55 if low else 95)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * (7 if low else 11))
    slap = bandpass(rng.standard_normal(len(t)), 800, 3500) * np.exp(-t * 60) * 0.15
    return (body + slap) * amp


# ================================================================== mixing

def place(buf, sig, at):
    i = int(round(at * SR))
    if i < 0:  # humanised timing nudged it before the start
        sig = sig[-i:]; i = 0
    if i >= len(buf):
        return
    end = min(len(buf), i + len(sig))
    buf[i:end] += sig[: end - i]


class Mix:
    """Stereo bus with constant-power panning and a stereo hall reverb send."""

    def __init__(self, loop_seconds, tail=6.0):
        self.loop = loop_seconds
        self.n = int((loop_seconds + tail) * SR)
        self.L = np.zeros(self.n); self.R = np.zeros(self.n)
        self.sL = np.zeros(self.n); self.sR = np.zeros(self.n)

    def add(self, sig, at, pan=0.0, send=0.3):
        if at < 0:  # starts before the loop point: play it at the end, finish() folds it round
            at += self.loop
        a = (pan + 1) * np.pi / 4
        for arr, g in ((self.L, np.cos(a)), (self.R, np.sin(a))):
            place(arr, sig * g, at)
        for arr, g in ((self.sL, np.cos(a) * send), (self.sR, np.sin(a) * send)):
            place(arr, sig * g, at)


def hall_ir(seed, seconds=3.6, decay=2.4):
    r = np.random.default_rng(seed)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    ir = r.standard_normal(n) * np.exp(-t * decay)
    # high frequencies die sooner: blend a darker copy in over time
    dark = lowpass(ir, 2800)
    w = np.minimum(1.0, t / 1.2)
    ir = ir * (1 - w) + dark * w
    pre = int(0.022 * SR)
    ir = np.concatenate([np.zeros(pre), ir])
    return ir / np.sqrt(np.sum(ir ** 2))


IR_L, IR_R = hall_ir(1), hall_ir(2)


def finish(mix, loop_seconds, wet=0.55):
    """Reverb, fold the tail back over the loop start, master, return stereo array."""
    L = mix.L + wet * fftconvolve(mix.sL, IR_L)[: mix.n]
    R = mix.R + wet * fftconvolve(mix.sR, IR_R)[: mix.n]
    n = int(loop_seconds * SR)
    out = []
    for ch in (L, R):
        loop = ch[:n].copy()
        tail = ch[n:]
        loop[: len(tail)] += tail
        out.append(loop)
    x = np.stack(out, axis=1)
    # gentle warmth: tame harsh highs, light bus glue, soft limit
    x = np.stack([lowpass(x[:, 0], 12000), lowpass(x[:, 1], 12000)], axis=1)
    # same loudness for every song (-17 dBFS RMS), never above -1 dBFS peak
    rms = np.sqrt(np.mean(x ** 2))
    x = x * (10 ** (-17 / 20) / (rms + 1e-9))
    peak = np.max(np.abs(x))
    return x * (0.89 / peak) if peak > 0.89 else x


def save(name, x, outdir, q=4):
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
        wavfile.write(f.name, SR, (x * 32767).astype(np.int16))
        tmp = f.name
    out = os.path.join(outdir, name)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-c:a", "libvorbis", "-q:a", str(q), out], check=True)
    os.unlink(tmp)
    print(f"{name}: {len(x) / SR:.1f}s, {os.path.getsize(out) // 1024} KB")


def hum(t, amt=0.012):
    """Humanised timing."""
    return t + rng.uniform(-amt, amt)


# ================================================================== 1. Piano Serenade

def serenade():
    bpm = 66.0; beat = 60 / bpm; bar = 3 * beat
    bars = 16
    length = bars * bar
    mix = Mix(length)
    # chord: (bass, [waltz chord tones], [string pad])
    chords = [
        ("C3", ["E4", "G4", "C5"], ["C4", "E4", "G4"]),
        ("A2", ["E4", "A4", "C5"], ["A3", "C4", "E4"]),
        ("F2", ["F4", "A4", "C5"], ["F3", "A3", "C4"]),
        ("G2", ["D4", "G4", "B4"], ["G3", "B3", "D4"]),
        ("C3", ["E4", "G4", "C5"], ["C4", "E4", "G4"]),
        ("E2", ["E4", "G4", "B4"], ["E3", "G3", "B3"]),
        ("F2", ["F4", "A4", "C5"], ["F3", "A3", "C4"]),
        ("G2", ["D4", "F4", "B4"], ["G3", "B3", "F4"]),
        ("A2", ["E4", "A4", "C5"], ["A3", "C4", "E4"]),
        ("E2", ["E4", "G4", "B4"], ["E3", "G3", "B3"]),
        ("F2", ["F4", "A4", "C5"], ["F3", "A3", "C4"]),
        ("C3", ["E4", "G4", "C5"], ["C4", "E4", "G4"]),
        ("D3", ["D4", "F4", "C5"], ["D3", "F3", "A3", "C4"]),
        ("G2", ["D4", "G4", "B4"], ["G3", "B3", "D4"]),
        ("C3", ["E4", "G4", "C5"], ["C4", "E4", "G4"]),
        ("G2", ["D4", "F4", "B4"], ["G3", "B3", "D4", "F4"]),
    ]
    # melody per bar: (note, beats); None = rest
    melody = [
        [("G4", 1), ("C5", 1), ("E5", 1)],
        [("E5", 2), ("D5", 1)],
        [("C5", 1), ("A4", 1), ("C5", 1)],
        [("D5", 3)],
        [("E5", 1), ("G5", 1), ("C6", 1)],
        [("B5", 2), ("G5", 1)],
        [("A5", 1.5), ("G5", 0.5), ("F5", 1)],
        [("G5", 2.5), ("F5", 0.5)],
        [("E5", 1), ("A5", 1), ("C6", 1)],
        [("B5", 1.5), ("A5", 0.5), ("G5", 1)],
        [("A5", 1), ("G5", 1), ("F5", 1)],
        [("E5", 2), ("C5", 1)],
        [("D5", 1), ("F5", 1), ("A5", 1)],
        [("G5", 1.5), ("F5", 0.5), ("D5", 1)],
        [("C5", 3)],
        [("D5", 2), ("B4", 1)],
    ]
    for b in range(bars):
        t0 = b * bar
        bass, tones, pad = chords[b]
        # left hand: low bass on 1 (with octave below for warmth), chord on 2 and 3
        mix.add(piano(hz(bass), bar * 0.95, 0.62), hum(t0), pan=-0.35, send=0.35)
        mix.add(piano(hz(m(bass) - 12), bar * 0.95, 0.35), hum(t0), pan=-0.4, send=0.35)
        for k in (1, 2):
            for i, n in enumerate(tones):
                mix.add(piano(hz(n), beat * 0.9, 0.34 - 0.04 * (k - 1)), hum(t0 + k * beat + i * 0.008), pan=-0.05 + 0.08 * i, send=0.35)
        # right hand melody with an octave-doubled sparkle on long notes
        t = t0
        for n, beats in melody[b]:
            v = 0.78 if beats >= 1.5 else 0.68
            # phrase shaping: lift towards the climax (bars 9–10)
            v *= 0.9 + 0.12 * np.exp(-((b - 9.0) / 3.5) ** 2)
            mix.add(piano(hz(n), beats * beat * 0.98, v, ring=2.2), hum(t, 0.008), pan=0.25, send=0.4)
            t += beats * beat
        # strings, entering softly on the second half
        amp = 0.018 if b < 8 else 0.03
        mix.add(strings([hz(n) for n in pad], bar + 0.3, amp, attack=0.45), t0 - 0.3, pan=0.0, send=0.55)
        # cello line under the second half
        if b >= 8:
            mix.add(strings([hz(bass)], bar + 0.2, 0.018, attack=0.6, bright=1400), t0, pan=-0.2, send=0.45)
    return finish(mix, length)


# ================================================================== 2. Moonlight on the Moat (Khmer-flavoured)

def moonlight():
    bpm = 70.0; beat = 60 / bpm; bar = 4 * beat
    bars = 12
    length = bars * bar
    mix = Mix(length)
    # G major pentatonic (G A B D E) melody, khim with tremolo on long notes; flute answers
    khim_mel = [
        [("D5", 1), ("E5", 1), ("G5", 2)],
        [("A5", 1), ("G5", 0.5), ("E5", 0.5), ("D5", 2)],
        [("B4", 1), ("D5", 1), ("E5", 1), ("D5", 1)],
        [("B4", 1.5), ("A4", 0.5), ("G4", 2)],
        [("D5", 1), ("E5", 1), ("G5", 1), ("A5", 1)],
        [("B5", 2), ("A5", 1), ("G5", 1)],
        [("E5", 1), ("G5", 1), ("A5", 0.5), ("G5", 0.5), ("E5", 1)],
        [("D5", 4)],
    ]
    flute_mel = [  # bars 8–11: the flute sings while the khim accompanies
        [("B5", 1.5), ("A5", 0.5), ("G5", 1), ("E5", 1)],
        [("G5", 1), ("A5", 1), ("B5", 2)],
        [("D6", 1.5), ("B5", 0.5), ("A5", 1), ("G5", 1)],
        [("A5", 3), ("D5", 1)],
    ]
    pads = [["G3", "B3", "D4"], ["E3", "G3", "B3"], ["C3", "E3", "G3"], ["D3", "G3", "A3"]]
    bass = ["G2", "E2", "C2", "D2"]
    for b in range(bars):
        t0 = b * bar
        ci = b % 4
        mix.add(strings([hz(n) for n in pads[ci]], bar + 0.3, 0.04, attack=0.5, bright=1800), t0 - 0.3, send=0.6)
        mix.add(harp(hz(bass[ci]), bar, 0.26), t0, pan=-0.3, send=0.4)
        # heartbeat skor: lub on 1, dub just after, soft tap on 3
        mix.add(skor(0.30), hum(t0, 0.004), pan=-0.1, send=0.25)
        mix.add(skor(0.20), hum(t0 + 0.28 * beat, 0.004), pan=-0.1, send=0.25)
        mix.add(skor(0.12, low=False), hum(t0 + 2 * beat, 0.004), pan=0.15, send=0.25)
        if b < 8:
            t = t0
            for n, beats in khim_mel[b]:
                mix.add(khim(hz(n), beats * beat), hum(t, 0.006), pan=0.2, send=0.35)
                t += beats * beat
        else:
            # khim broken chords under the flute
            tones = [hz(n) + 0 for n in pads[ci]]
            for i in range(8):
                f = tones[[0, 1, 2, 1][i % 4]] * 2
                mix.add(khim(f, beat / 2, 0.11, tremolo=False), hum(t0 + i * beat / 2, 0.006), pan=0.3, send=0.35)
            t = t0
            for n, beats in flute_mel[b - 8]:
                mix.add(flute(hz(n), beats * beat * 0.97, 0.16), hum(t, 0.01), pan=-0.15, send=0.5)
                t += beats * beat
    return finish(mix, length, wet=0.6)


# ================================================================== 3. Forever (strings & harp ballad)

def forever():
    bpm = 64.0; beat = 60 / bpm; bar = 4 * beat
    bars = 12
    length = bars * bar
    mix = Mix(length)
    prog = [  # D  A/C#  Bm  F#m/A  G  D/F#  Em  A ... then  G  A  Bm  A(sus)
        ("D3", ["D4", "F#4", "A4"]), ("C#3", ["C#4", "E4", "A4"]), ("B2", ["B3", "D4", "F#4"]), ("A2", ["A3", "C#4", "F#4"]),
        ("G2", ["G3", "B3", "D4"]), ("F#2", ["F#3", "A3", "D4"]), ("E2", ["E3", "G3", "B3"]), ("A2", ["A3", "C#4", "E4"]),
        ("G2", ["G3", "B3", "D4"]), ("A2", ["A3", "C#4", "E4"]), ("B2", ["B3", "D4", "F#4"]), ("A2", ["A3", "D4", "E4"]),
    ]
    melody = [
        [("F#5", 2), ("E5", 1), ("D5", 1)],
        [("E5", 3), ("A4", 1)],
        [("D5", 1), ("F#5", 1), ("B5", 1.5), ("A5", 0.5)],
        [("A5", 3), ("F#5", 1)],
        [("G5", 1.5), ("F#5", 0.5), ("E5", 1), ("D5", 1)],
        [("D5", 2), ("A4", 1), ("D5", 1)],
        [("E5", 1), ("G5", 1), ("B5", 1), ("A5", 1)],
        [("A5", 3), (None, 1)],
        [("B5", 1.5), ("A5", 0.5), ("G5", 1), ("B5", 1)],
        [("C#6", 1.5), ("B5", 0.5), ("A5", 1), ("E5", 1)],
        [("F#5", 1), ("D6", 2), ("C#6", 1)],
        [("A5", 3), ("E5", 1)],
    ]
    for b in range(bars):
        t0 = b * bar
        bass, ch = prog[b]
        swell = 0.045 + 0.02 * (b >= 8)
        mix.add(strings([hz(n) for n in ch], bar + 0.3, swell, attack=0.45), t0 - 0.3, send=0.6)
        mix.add(strings([hz(bass)], bar + 0.2, 0.035, attack=0.5, bright=1200), t0, pan=-0.25, send=0.45)
        # harp: rolling eighth-note arpeggio up and back
        arp = [m(bass) + 12] + [m(n) + 12 for n in ch] + [m(ch[0]) + 24]
        order = [0, 1, 2, 3, 4, 3, 2, 1]
        for i, idx in enumerate(order):
            mix.add(harp(hz(arp[idx]), beat * 2.5, 0.26 if i else 0.32), hum(t0 + i * beat / 2, 0.006), pan=0.35 - 0.08 * (idx - 2), send=0.45)
        t = t0
        for n, beats in melody[b]:
            if n is not None:
                mix.add(flute(hz(n), beats * beat * 0.96, 0.13), hum(t, 0.01), pan=-0.1, send=0.55)
            t += beats * beat
    return finish(mix, length, wet=0.6)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw"
    os.makedirs(out, exist_ok=True)
    save("love_song_serenade.ogg", serenade(), out)
    save("love_song_moonlight.ogg", moonlight(), out)
    save("love_song_forever.ogg", forever(), out)
