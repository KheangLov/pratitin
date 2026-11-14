"""
Composes and renders the Love tab's songs — warm, intimate love songs for a couple who
have been together for years (all original, synthesised from scratch, stereo, seamless loops):

  love_song_stillyou.ogg   "Still You"                 neo-soul ballad: Rhodes, jazzy chords, vibraphone tune,
                                                       warm bass, soft brushed beat (70 bpm, 16 bars)
  love_song_slowdance.ogg  "Slow Dance in the Kitchen" lo-fi: dusty Rhodes, swung boom-bap, vinyl crackle (76 bpm)
  love_song_ourhome.ogg    "Our Little Home"           acoustic: Travis-picked nylon guitar, whistled melody (84 bpm)
  love_song_moonlight.ogg  "Moonlight on the Moat"     Khmer-flavoured: khim tremolo, bamboo flute, heartbeat skor

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


def finish(mix, loop_seconds, wet=0.55, lp=12000):
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
    x = np.stack([lowpass(x[:, 0], lp), lowpass(x[:, 1], lp)], axis=1)
    # same loudness for every song (-17 dBFS RMS), never above -1 dBFS peak
    rms = np.sqrt(np.mean(x ** 2))
    x = x * (10 ** (-18 / 20) / (rms + 1e-9))
    peak = np.max(np.abs(x))
    return x * (0.78 / peak) if peak > 0.78 else x  # headroom: Vorbis overshoots on transients


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


# ================================================================== more instruments

from scipy.signal import lfilter


def highpass(x, f, order=2):
    return sosfilt(butter(order, f, "high", fs=SR, output="sos"), x)


def rhodes(freq, dur, vel=0.55, ring=1.0, wow=0.0):
    """FM electric piano: 1:1 modulator for the warm bark, a fast-decaying tine 'ping'."""
    total = dur + ring
    t = np.arange(int(total * SR)) / SR
    f = freq * (1 + wow * np.sin(2 * np.pi * 0.55 * t + rng.uniform(0, 6)))
    ph = 2 * np.pi * np.cumsum(f) / SR
    idx = (0.5 + 1.9 * vel) * np.exp(-t * 4.0) + 0.3
    tone = np.sin(ph + idx * np.sin(ph))
    tine = np.sin(2 * np.pi * freq * 13.0 * t) * np.exp(-t * 38) * 0.16 * vel if freq * 13 < 15000 else 0
    tau = 2.3 * (261.6 / freq) ** 0.4
    env = 0.35 * np.exp(-t / (tau * 0.18)) + 0.65 * np.exp(-t / tau)
    damper = np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.12))
    return (tone * env + tine) * np.minimum(1.0, t / 0.003) * damper * vel * 0.5


def vibes(freq, dur, vel=0.5, ring=1.6, motor=True):
    t = np.arange(int((dur + ring) * SR)) / SR
    ph = 2 * np.pi * freq * t
    idx = 0.9 * np.exp(-t * 9)
    tone = np.sin(ph + idx * np.sin(4 * ph)) + 0.07 * np.sin(4 * ph) * np.exp(-t * 5)
    env = np.exp(-t / 2.4) * np.minimum(1.0, t / 0.002)
    trem = 1 - 0.26 * (0.5 + 0.5 * np.sin(2 * np.pi * 5.4 * t)) if motor else 1.0
    damper = np.where(t < dur + 0.25, 1.0, np.exp(-(t - dur - 0.25) / 0.25))
    return tone * env * trem * damper * vel * 0.4


def ebass(freq, dur, vel=0.7):
    t = np.arange(int((dur + 0.15) * SR)) / SR
    ph = 2 * np.pi * freq * t
    tone = np.sin(ph) + 0.35 * np.sin(2 * ph) + 0.1 * np.sin(3 * ph)
    env = np.minimum(1.0, t / 0.006) * (0.55 * np.exp(-t / 0.25) + 0.45 * np.exp(-t / 1.8)) * np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.04))
    return lowpass(tone * env, 800) * vel * 0.5


def _t(d):
    return np.arange(int(d * SR)) / SR


def kick(amp=0.6):
    t = _t(0.45)
    f = 48 + 70 * np.exp(-t * 28)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 7.5)
    click = highpass(rng.standard_normal(len(t)), 2000) * np.exp(-t * 300) * 0.12
    return (body + click) * amp


def rim(amp=0.2):
    t = _t(0.08)
    return (np.sin(2 * np.pi * 1700 * t) * np.exp(-t * 90) * 0.5 + bandpass(rng.standard_normal(len(t)), 2000, 6000) * np.exp(-t * 120)) * amp


def snare(amp=0.3, lofi=False):
    t = _t(0.3)
    x = bandpass(rng.standard_normal(len(t)), 1200, 7000) * np.exp(-t * 16) + np.sin(2 * np.pi * 185 * t) * np.exp(-t * 30) * 0.5
    return (lowpass(x, 4200) if lofi else x) * amp


def hat(amp=0.1):
    t = _t(0.06)
    return highpass(rng.standard_normal(len(t)), 7000) * np.exp(-t * 70) * amp


def shaker(amp=0.1):
    t = _t(0.12)
    return bandpass(rng.standard_normal(len(t)), 4500, 11000) * np.minimum(1.0, t / 0.02) * np.exp(-t * 28) * amp


def brush(amp=0.06, d=0.35):
    t = _t(d)
    return bandpass(rng.standard_normal(len(t)), 2000, 9000) * np.sin(np.pi * t / d) ** 2 * amp


def crackle(seconds, density=6.0, amp=0.05):
    n = int(seconds * SR)
    x = np.zeros(n)
    k = rng.poisson(density * seconds)
    pos = rng.integers(0, n, k)
    x[pos] = rng.choice([-1, 1], k) * rng.uniform(0.15, 1, k) ** 3
    hiss = lowpass(highpass(rng.standard_normal(n), 900), 6000) * 0.004
    return highpass(x, 1500) * amp + hiss


def whistle_line(notes, total, amp=0.16):
    """A whistled melody: one continuous tone gliding between notes, per-note swells."""
    n = int(total * SR)
    f = np.zeros(n); a = np.zeros(n)
    for st, du, fr in notes:
        i0, i1 = int(st * SR), min(n, int((st + du) * SR))
        f[i0:i1] = fr
        L = i1 - i0
        tt = np.arange(L) / SR
        env = np.minimum(1.0, tt / 0.045) * np.minimum(1.0, (du - tt) / 0.07) * (0.92 + 0.08 * np.minimum(1.0, tt / max(du, 0.1)))
        a[i0:i1] = np.maximum(a[i0:i1], env)
    # hold pitch through rests, glide ~35 ms between notes
    last = f[np.nonzero(f)[0][0]] if np.any(f) else 440.0
    for i in range(n):
        if f[i] == 0: f[i] = last
        else: last = f[i]
    k = np.exp(-1 / (0.035 * SR))
    logf = lfilter([1 - k], [1, -k], np.log(f), zi=[np.log(f[0]) * k])[0]
    t = np.arange(n) / SR
    vib = 1 + 0.0055 * np.sin(2 * np.pi * 5.6 * t)
    ph = 2 * np.pi * np.cumsum(np.exp(logf) * vib) / SR
    tone = np.sin(ph) + 0.03 * np.sin(2 * ph)
    breath = bandpass(rng.standard_normal(n), 1800, 5200) * 0.025
    return (tone + breath) * lowpass(a, 40) * amp


def nylon(freq, dur, amp=0.25):
    x = pluck(freq, dur, amp, bright=0.28, decay=0.9975)
    return x + 0.35 * bandpass(x, 90, 260)


def swing(i, beat, ratio):
    """Time of the i-th eighth note with swing (0.5 = straight)."""
    return (i // 2) * beat + (i % 2) * beat * ratio


def add_pan_lfo(mix, sig, at, rate=0.7, depth=0.55, send=0.3):
    """Stereo tremolo/autopan, phase-locked to song time (Rhodes signature)."""
    if at < 0: at += mix.loop
    i = int(round(at * SR))
    ph = 2 * np.pi * rate * (np.arange(len(sig)) + i) / SR
    gl = np.sqrt(0.5 * (1 + depth * np.sin(ph))); gr = np.sqrt(0.5 * (1 - depth * np.sin(ph)))
    for arr, g, s_arr in ((mix.L, gl, mix.sL), (mix.R, gr, mix.sR)):
        place(arr, sig * g, at); place(s_arr, sig * g * send, at)


# ================================================================== 1. Still You (neo-soul love ballad)

def still_you():
    bpm = 70.0; beat = 60 / bpm; bar = 4 * beat
    V = {
        "Fmaj9": ["A3", "C4", "E4", "G4"], "Em7": ["G3", "B3", "D4", "E4"], "A7b13": ["G3", "C#4", "F4"],
        "Dm9": ["F3", "A3", "C4", "E4"], "G13": ["F3", "B3", "E4", "A4"], "G7sus": ["F3", "C4", "D4", "G4"],
        "Cmaj9": ["E3", "G3", "B3", "D4"], "Am9": ["G3", "B3", "C4", "E4"],
    }
    R = {"Fmaj9": "F2", "Em7": "E2", "A7b13": "A2", "Dm9": "D2", "G13": "G2", "G7sus": "G2", "Cmaj9": "C2", "Am9": "A2"}
    A = [[("Fmaj9", 0, 4)], [("Em7", 0, 2), ("A7b13", 2, 2)], [("Dm9", 0, 4)], [("G13", 0, 4)],
         [("Em7", 0, 4)], [("A7b13", 0, 4)], [("Dm9", 0, 4)], [("G7sus", 0, 2), ("G13", 2, 2)]]
    B = A[:4] + [[("Cmaj9", 0, 4)], [("Am9", 0, 4)], [("Dm9", 0, 2), ("G13", 2, 2)], [("Cmaj9", 0, 4)]]
    prog = A + B
    melody = [
        [(None, 1), ("E5", .5), ("G5", .5), ("A5", 1.5), ("G5", .5)],
        [("G5", 1), ("E5", 1), ("F5", 1), ("E5", 1)],
        [("D5", .5), ("E5", .5), ("F5", 1), ("A5", 1.5), ("C6", .5)],
        [("B5", 2), ("A5", 1), ("G5", 1)],
        [("G5", 1), ("B5", 1), ("D6", 1), ("B5", 1)],
        [("C#6", 1.5), ("B5", .5), ("A5", 2)],
        [("F5", 1), ("A5", 1), ("C6", 1), ("E6", 1)],
        [("D6", 2), ("C6", 1), ("B5", 1)],
        [("A5", 1.5), ("G5", .5), ("E5", 1), ("C5", 1)],
        [("D5", 1), ("E5", 1), ("G5", 1), ("F5", 1)],
        [("E5", 2), ("D5", 1), ("C5", 1)],
        [("D5", 3), (None, 1)],
        [("E5", 1), ("G5", 1), ("B5", 1), ("D6", 1)],
        [("C6", 1.5), ("B5", .5), ("A5", 1), ("G5", 1)],
        [("F5", 1), ("A5", 1), ("B5", 1), ("A5", 1)],
        [("G5", 3), (None, 1)],
    ]
    bars = 16
    length = bars * bar
    mix = Mix(length)
    for b in range(bars):
        t0 = b * bar
        for name, off, ln in prog[b]:
            ts = t0 + off * beat
            notes = V[name]
            # Rhodes: strike, and a softer push on the "and" of 3 for long chords
            for i, n in enumerate(notes):
                add_pan_lfo(mix, rhodes(hz(n), (ln * beat * 0.95) if ln == 2 else 2.4 * beat, 0.5), hum(ts + i * 0.006, 0.006), send=0.3)
            if ln == 4:
                for i, n in enumerate(notes):
                    add_pan_lfo(mix, rhodes(hz(n), 1.3 * beat, 0.34), hum(ts + 2.5 * beat + i * 0.006, 0.006), send=0.3)
            # bass
            r = m(R[name])
            mix.add(ebass(hz(r), (1.5 if ln == 4 else 1.4) * beat, 0.75), hum(ts, 0.004), pan=-0.05, send=0.08)
            if ln == 4:
                mix.add(ebass(hz(r + 7), 0.4 * beat, 0.5), hum(ts + 2.5 * beat, 0.004), send=0.08)
                mix.add(ebass(hz(r), 0.9 * beat, 0.6), hum(ts + 3 * beat, 0.004), send=0.08)
            # a whisper of pad for warmth
            mix.add(strings([hz(n) for n in notes], ln * beat + 0.2, 0.010, attack=0.6, bright=1600), ts - 0.2, send=0.5)
        # drums: soft and close
        mix.add(kick(0.55), hum(t0, 0.003)); mix.add(kick(0.38), hum(t0 + 2.5 * beat, 0.004))
        mix.add(rim(0.18), hum(t0 + beat, 0.006), pan=0.15); mix.add(rim(0.2), hum(t0 + 3 * beat, 0.006), pan=0.15)
        for i in range(8):
            mix.add(shaker(0.07 if i % 2 == 0 else 0.1), hum(t0 + swing(i, beat, 0.6), 0.004), pan=0.35, send=0.15)
        mix.add(brush(0.04), t0 + 1.5 * beat, pan=-0.3); mix.add(brush(0.04), t0 + 3.5 * beat, pan=-0.3)
        # vibraphone melody
        t = t0
        for n, beats in melody[b]:
            if n is not None:
                mix.add(vibes(hz(n), beats * beat * 0.95, 0.55 if b < 8 else 0.62), hum(t, 0.008), pan=0.2, send=0.45)
            t += beats * beat
    return finish(mix, length, wet=0.45)


# ================================================================== 2. Slow Dance in the Kitchen (lo-fi)

def slow_dance():
    bpm = 76.0; beat = 60 / bpm; bar = 4 * beat
    V = {"Dm9": ["F3", "A3", "C4", "E4"], "G13": ["F3", "B3", "E4", "A4"], "Cmaj9": ["E3", "G3", "B3", "D4"], "A7b13": ["G3", "C#4", "F4"]}
    R = {"Dm9": "D2", "G13": "G2", "Cmaj9": "C2", "A7b13": "A2"}
    cyc = ["Dm9", "G13", "Cmaj9", "A7b13"]
    melody = [
        [(None, 1), ("A5", .5), ("C6", .5), ("E6", 1), ("D6", 1)],
        [("B5", 1.5), ("A5", .5), ("G5", 2)],
        [("E5", .5), ("G5", .5), ("B5", 1), ("A5", 1), ("G5", 1)],
        [("C#6", 1), ("A5", 1), ("F5", 2)],
        [(None, 1), ("F5", .5), ("A5", .5), ("C6", 1), ("E6", 1)],
        [("D6", 1), ("B5", 1), ("A5", .5), ("G5", 1.5)],
        [("E5", 1), ("G5", 1), ("D6", 2)],
        [("C#6", 2), ("A5", 2)],
    ]
    sw = 0.62
    bars = 16
    length = bars * bar
    mix = Mix(length)
    for b in range(bars):
        t0 = b * bar
        name = cyc[b % 4]
        notes = V[name]
        # dusty Rhodes: a slightly late, lazy roll + a push on the swung "and" of 2
        for i, n in enumerate(notes):
            add_pan_lfo(mix, rhodes(hz(n), 2.6 * beat, 0.48, wow=0.0025), hum(t0 + 0.02 + i * 0.012, 0.006), rate=0.5, send=0.25)
            add_pan_lfo(mix, rhodes(hz(n), 1.4 * beat, 0.33, wow=0.0025), hum(t0 + swing(5, beat, sw) + i * 0.01, 0.006), rate=0.5, send=0.25)
        r = m(R[name])
        mix.add(ebass(hz(r), 2.5 * beat, 0.8), hum(t0, 0.004), send=0.05)
        mix.add(ebass(hz(r + 7 if b % 2 == 0 else r + 12), 0.8 * beat, 0.55), hum(t0 + swing(5, beat, sw), 0.004), send=0.05)
        # boom-bap, laid back
        mix.add(kick(0.62), hum(t0, 0.003)); mix.add(kick(0.45), hum(t0 + swing(5, beat, sw), 0.004))
        if b % 2 == 1: mix.add(kick(0.3), hum(t0 + swing(7, beat, sw), 0.004))
        mix.add(snare(0.3, lofi=True), hum(t0 + beat + 0.015, 0.006), pan=0.05, send=0.2)
        mix.add(snare(0.32, lofi=True), hum(t0 + 3 * beat + 0.015, 0.006), pan=0.05, send=0.2)
        for i in range(8):
            mix.add(hat(0.06 if i % 2 else 0.085), hum(t0 + swing(i, beat, sw), 0.005), pan=0.3, send=0.05)
        # melody on a high Rhodes in the second half
        if b >= 8:
            t = t0
            for n, beats in melody[b - 8]:
                if n is not None:
                    add_pan_lfo(mix, rhodes(hz(n), beats * beat * 0.9, 0.5, wow=0.002), hum(t, 0.01), rate=0.5, depth=0.3, send=0.4)
                t += beats * beat
    # vinyl
    cr = crackle(length, 7, 0.06)
    mix.add(cr, 0, pan=-0.2, send=0); mix.add(crackle(length, 6, 0.05), 0, pan=0.2, send=0)
    return finish(mix, length, wet=0.35, lp=6200)


# ================================================================== 3. Our Little Home (acoustic, whistled)

def our_home():
    bpm = 84.0; beat = 60 / bpm; bar = 4 * beat
    # (bass1, bass2, [treble chord tones])
    C = {
        "G": ("G2", "D3", ["B3", "D4", "G4"]), "D/F#": ("F#2", "A2", ["A3", "D4", "F#4"]), "Em": ("E2", "B2", ["G3", "B3", "E4"]),
        "C": ("C3", "G2", ["G3", "C4", "E4"]), "D": ("D3", "A2", ["A3", "D4", "F#4"]),
    }
    prog = ["G", "D/F#", "Em", "C", "G", "D", "C", "D", "Em", "C", "G", "D", "C", "D/F#", "G", "G"]
    melody = [
        [("D5", 1), ("G5", 1), ("B5", 1.5), ("A5", .5)],
        [("A5", 2), ("F#5", 1), ("D5", 1)],
        [("E5", 1), ("G5", 1), ("B5", 1), ("G5", 1)],
        [("E5", 3), (None, 1)],
        [("D5", 1), ("G5", 1), ("B5", 1), ("D6", 1)],
        [("C6", 1.5), ("B5", .5), ("A5", 2)],
        [("G5", 1), ("E5", 1), ("C5", 1), ("E5", 1)],
        [("D5", 3), (None, 1)],
        [("B5", 1.5), ("A5", .5), ("G5", 1), ("B5", 1)],
        [("C6", 1), ("B5", 1), ("G5", 2)],
        [("D5", 1), ("G5", 1), ("A5", 1), ("B5", 1)],
        [("A5", 3), (None, 1)],
        [("E5", 1), ("G5", 1), ("C6", 1), ("B5", 1)],
        [("A5", 1), ("F#5", 1), ("D5", 1), ("A5", 1)],
        [("B5", 2), ("A5", 1), ("G5", 1)],
        [("G5", 3), (None, 1)],
    ]
    sw = 0.54
    bars = 16
    length = bars * bar
    mix = Mix(length)
    wnotes = []
    for b in range(bars):
        t0 = b * bar
        b1, b2, tr = C[prog[b]]
        # Travis picking: thumb alternates bass, fingers pick the chord tones in between
        pattern = [b1, tr[0], b2, tr[1], b1, tr[2], b2, tr[0]]
        for i, n in enumerate(pattern):
            at = t0 + swing(i, beat, sw)
            ring = bar - (at - t0) + 0.5
            v = 0.3 if i % 2 == 0 else 0.22
            mix.add(nylon(hz(n), ring, v), hum(at, 0.006), pan=-0.25 + 0.12 * (i % 3), send=0.3)
        # shaker from bar 5, a soft heartbeat kick on 1 and 3 in the second half
        if b >= 4:
            for i in range(8):
                mix.add(shaker(0.12 if i % 2 == 0 else 0.18), hum(t0 + swing(i, beat, sw), 0.004), pan=0.4, send=0.1)
        if b >= 8:
            mix.add(kick(0.35), hum(t0, 0.003)); mix.add(kick(0.25), hum(t0 + 2 * beat, 0.003))
        t = t0
        for n, beats in melody[b]:
            if n is not None:
                wnotes.append((t, beats * beat * 0.94, hz(n)))
                # a little glockenspiel sparkle doubling the tune in the second half
                if b >= 8:
                    mix.add(vibes(hz(m(n) + 12), beats * beat * 0.5, 0.2, ring=1.0, motor=False), hum(t, 0.006), pan=0.35, send=0.4)
            t += beats * beat
    mix.add(whistle_line(wnotes, length, 0.095), 0, pan=0.1, send=0.45)
    return finish(mix, length, wet=0.4)



if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/raw"
    os.makedirs(out, exist_ok=True)
    save("love_song_stillyou.ogg", still_you(), out)
    save("love_song_slowdance.ogg", slow_dance(), out)
    save("love_song_ourhome.ogg", our_home(), out)
    save("love_song_moonlight.ogg", moonlight(), out)
