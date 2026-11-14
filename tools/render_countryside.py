"""
Cambodian countryside at dusk / by day, for Pratitin's background.

  back layer   rising moon (dusk) or soft sun (day), a pair of egrets, a distant tree line
               with small sugar palms, rice paddies in perspective (green rows and flooded
               fields that mirror the sky, earthen dikes) and a wooden Khmer stilt house
               with a warm lit window
  front layer  tall thnot (sugar) palms framing the scene, reeds and a lotus pond

Sky stays transparent so the app's animated sky shows through. Flat, modern shapes with
soft gradients. Two variants: 'dusk' and 'day'.

Usage:  python3 tools/render_countryside.py app/src/main/res/drawable-nodpi
"""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1440, 960
SS = 3
HZ = int(H * 0.60)
CX = W * 0.5
U = W / 100.0
VX, VY = W * 0.46, HZ - 40  # vanishing point of the paddy dikes

THEMES = {
    "dusk": dict(
        haze=(14, 34, 92), haze_far=(30, 56, 128), palm_far=(10, 24, 70),
        rice=[(18, 70, 74), (24, 88, 82), (14, 58, 66)], rice_row=(40, 118, 104),
        water_top=(40, 80, 170), water_bot=(14, 34, 104), dike=(22, 18, 48),
        house=(20, 12, 36), roof=(30, 14, 40), window=(255, 196, 102), rim=(250, 210, 140),
        moon=(255, 244, 214), glint=(255, 236, 190), bird=(250, 248, 240),
        palm=(5, 10, 34), leafpad=(10, 48, 58), lotus=[(232, 120, 160), (250, 180, 204), (255, 236, 244)], reed=(8, 22, 44),
    ),
    "day": dict(
        haze=(86, 140, 120), haze_far=(150, 190, 200), palm_far=(60, 112, 92),
        rice=[(96, 172, 72), (128, 196, 84), (78, 150, 64)], rice_row=(160, 214, 110),
        water_top=(150, 196, 244), water_bot=(92, 150, 220), dike=(170, 132, 86),
        house=(118, 74, 42), roof=(150, 52, 44), window=(60, 34, 20), rim=(255, 236, 190),
        moon=(255, 238, 190), glint=(255, 250, 225), bird=(255, 255, 255),
        palm=(30, 62, 44), leafpad=(46, 128, 84), lotus=[(226, 98, 142), (246, 160, 190), (255, 230, 240)], reed=(48, 96, 60),
    ),
}

rng = np.random.default_rng(11)
YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)


def canvas():
    return Image.new("L", (W * SS, H * SS), 0)


def to_mask(im, blur=0):
    im = im.resize((W, H), Image.LANCZOS)
    if blur:
        im = im.filter(ImageFilter.GaussianBlur(blur))
    return np.asarray(im, np.float32) / 255.0


def P(pts):
    return [(x * SS, y * SS) for x, y in pts]


def col(c):
    return np.broadcast_to(np.array(c, np.float32), (H, W, 3))


def over(rgb, a, src, m):
    m = np.clip(m, 0, 1)[..., None]
    out_a = m[..., 0] + a * (1 - m[..., 0])
    out = (src * m + rgb * a[..., None] * (1 - m)) / np.maximum(out_a[..., None], 1e-6)
    return out, out_a


def lerp(a, b, t):
    t = np.clip(np.asarray(t, np.float32), 0, 1)[..., None]
    return np.asarray(a, np.float32) * (1 - t) + np.asarray(b, np.float32) * t


# ------------------------------------------------------------------ shapes

def thnot(d, x, base, h, crown, lean=0.0, detail=True):
    """Sugar palm: a straight, slightly tapering trunk and a round crown of fan leaves."""
    left, right = [], []
    for i in range(25):
        s = i / 24
        cx = x + lean * s
        hw = (0.62 - 0.22 * s) * U * (crown / (9 * U)) ** 0.5
        left.append((cx - hw, base - h * s)); right.append((cx + hw, base - h * s))
    d.polygon(P(left + right[::-1]), fill=255)
    tx, ty = x + lean, base - h
    # skirt of old leaves hanging under the crown
    if detail:
        for k in range(9):
            a = math.radians(60 + k * 7.5)
            L = crown * (0.55 + 0.15 * math.sin(k * 2.1))
            ex, ey = tx + math.cos(a) * L * 0.55 * (1 if k % 2 else -1), ty + math.sin(a) * L
            d.polygon(P([(tx - 0.3 * U, ty), (ex, ey), (ex + 0.6 * U, ey - 0.2 * U), (tx + 0.3 * U, ty)]), fill=255)
    # fan leaves: each a wedge of pleats radiating from its stalk
    n = 17 if detail else 11
    for k in range(n):
        a = math.radians(-195 + k * (210 / (n - 1)) + 5 * math.sin(k * 1.3))
        stalk = crown * 0.45
        fx, fy = tx + math.cos(a) * stalk, ty + math.sin(a) * stalk * 0.9
        d.line(P([(tx, ty), (fx, fy)]), fill=255, width=max(1, int(0.25 * U * SS)))
        r = crown * (0.48 + 0.08 * math.sin(k * 2.7))
        spread = math.radians(62)
        pts = [(fx, fy)]
        pleats = 9 if detail else 5
        for j in range(pleats * 2 + 1):
            aa = a - spread / 2 + spread * j / (pleats * 2)
            rr = r * (1.0 if j % 2 == 0 else 0.82)  # serrated fan edge
            pts.append((fx + math.cos(aa) * rr, fy + math.sin(aa) * rr * 0.92))
        d.polygon(P(pts), fill=255)
    d.ellipse(P([(tx - crown * 0.22, ty - crown * 0.2), (tx + crown * 0.22, ty + crown * 0.22)]), fill=255)


def stilt_house(d, win, x, base, w):
    """Khmer wooden house on stilts: steep gable roof with upturned ends, ladder, lit window."""
    stilt_h = w * 0.34
    body_h = w * 0.36
    roof_h = w * 0.52
    floor = base - stilt_h
    # stilts
    for k in range(5):
        sx = x - w * 0.44 + k * w * 0.22
        d.rectangle(P([(sx - 0.18 * U, floor), (sx + 0.18 * U, base)]), fill=255)
    # floor beam and walls
    d.rectangle(P([(x - w * 0.5, floor - 0.4 * U), (x + w * 0.5, floor + 0.3 * U)]), fill=255)
    d.rectangle(P([(x - w * 0.44, floor - body_h), (x + w * 0.44, floor)]), fill=255)
    # roof: steep main roof with a gable, eaves flaring out, horns at the ridge ends
    ridge = floor - body_h - roof_h
    d.polygon(P([(x - w * 0.62, floor - body_h + 0.6 * U), (x - w * 0.36, ridge), (x + w * 0.36, ridge), (x + w * 0.62, floor - body_h + 0.6 * U)]), fill=255)
    for sx in (-1, 1):
        hx = x + sx * w * 0.36
        d.polygon(P([(hx, ridge + 0.5 * U), (hx + sx * 1.6 * U, ridge - 1.3 * U), (hx + sx * 0.5 * U, ridge + 0.7 * U)]), fill=255)
    # porch + ladder
    d.rectangle(P([(x + w * 0.44, floor - 0.3 * U), (x + w * 0.66, floor + 0.3 * U)]), fill=255)
    for k in range(5):
        ly = floor + k * stilt_h / 5
        d.line(P([(x + w * 0.6, ly), (x + w * 0.72, ly + 0.4 * U)]), fill=255, width=int(0.18 * U * SS))
    d.line(P([(x + w * 0.58, floor), (x + w * 0.7, base)]), fill=255, width=int(0.2 * U * SS))
    d.line(P([(x + w * 0.66, floor), (x + w * 0.78, base)]), fill=255, width=int(0.2 * U * SS))
    # two windows and a door (into the window mask)
    wd = ImageDraw.Draw(win)
    for wx in (-0.24, 0.04):
        wd.rectangle(P([(x + w * wx, floor - body_h * 0.72), (x + w * (wx + 0.14), floor - body_h * 0.3)]), fill=255)
    wd.rectangle(P([(x + w * 0.27, floor - body_h * 0.82), (x + w * 0.39, floor)]), fill=255)
    return ridge


def egret(d, x, y, s, flap):
    """A white egret in flight: two swept wings, a slender neck tucked in, legs trailing."""
    d.polygon(P([(x - 2.2 * s, y - flap * s), (x - 0.6 * s, y - 0.1 * s), (x, y + 0.15 * s), (x + 0.6 * s, y - 0.1 * s),
                 (x + 2.2 * s, y - flap * s * 0.9), (x + 0.5 * s, y + 0.35 * s), (x - 0.5 * s, y + 0.35 * s)]), fill=255)
    d.ellipse(P([(x - 0.35 * s, y - 0.05 * s), (x + 0.9 * s, y + 0.45 * s)]), fill=255)
    d.line(P([(x - 0.3 * s, y + 0.3 * s), (x - 1.5 * s, y + 0.55 * s)]), fill=255, width=max(1, int(0.12 * s * SS)))


def lotus_flower(d, cx, cy, r, tier):
    petals = {0: ((-70, 0.3, 0.66), (70, 0.3, 0.66)), 1: ((-38, 0.36, 0.86), (38, 0.36, 0.86)), 2: ((0, 0.42, 1.0),)}[tier]
    for a, sx, sy in petals:
        pts = []
        for i in range(25):
            t = i / 24 * 2 * math.pi
            px, py = math.sin(t) * r * sx, -(1 - math.cos(t)) * 0.5 * r * sy
            ra = math.radians(a)
            pts.append((cx + px * math.cos(ra) - py * math.sin(ra), cy + px * math.sin(ra) + py * math.cos(ra)))
        d.polygon(P(pts), fill=255)


def lotus_pad(d, cx, cy, r):
    # a round pad in perspective with a notch
    pts = []
    for i in range(48):
        t = math.radians(20 + i * (320 / 47))
        pts.append((cx + math.cos(t) * r, cy + math.sin(t) * r * 0.32))
    pts.append((cx, cy))
    d.polygon(P(pts), fill=255)


# ------------------------------------------------------------------ layers

def back_layer(th, dusk):
    rgb = np.zeros((H, W, 3), np.float32)
    a = np.zeros((H, W), np.float32)

    # moon / sun low over the fields, with a soft halo
    mx, my, mr = W * 0.30, HZ - 13 * U, 5.2 * U
    halo = np.exp(-(((XX - mx) ** 2 + (YY - my) ** 2) / (2 * (mr * 2.6) ** 2)))
    rgb, a = over(rgb, a, col(th["moon"]), halo * (0.35 if dusk else 0.45) * (YY < HZ))
    disc = np.clip(mr - np.hypot(XX - mx, YY - my) + 0.8, 0, 1)
    if dusk:
        rgb, a = over(rgb, a, col(th["moon"]), disc)
    else:
        core = np.exp(-(np.hypot(XX - mx, YY - my) / (mr * 0.9)) ** 2)
        rgb, a = over(rgb, a, col((255, 226, 150)), np.clip(core * 1.3, 0, 1) * (YY < HZ))
    if dusk:  # faint maria on the moon
        for ox, oy, rr in ((-1.2, -0.8, 1.6), (1.4, 0.6, 1.2), (0.2, 1.8, 0.9)):
            m = np.clip(rr * U - np.hypot(XX - mx - ox * U, YY - my - oy * U), 0, 1) * disc
            rgb, a = over(rgb, a, col((228, 214, 186)), m * 0.5)

    # a pair of egrets flying together
    birds = canvas(); bd = ImageDraw.Draw(birds)
    egret(bd, W * 0.60, HZ - 22 * U, 1.25 * U, 0.9)
    egret(bd, W * 0.645, HZ - 20.2 * U, 1.05 * U, 0.4)
    rgb, a = over(rgb, a, col(th["bird"]), to_mask(birds) * 0.95)

    # distant tree line with little palms, in two hazy depths
    for depth, colr, y0, amp, seed in ((0, th["haze_far"], HZ - 1.2 * U, 1.6, 3), (1, th["haze"], HZ + 0.2 * U, 2.4, 5)):
        im = canvas(); d = ImageDraw.Draw(im)
        r = np.random.default_rng(seed)
        x = -2 * U
        while x < W + 2 * U:
            rad = (0.7 + r.random() * 1.1) * amp * U * 0.6
            d.ellipse(P([(x - rad, y0 - rad * 1.6), (x + rad, y0 + rad)]), fill=255)
            x += rad * 1.2
        d.rectangle(P([(0, y0), (W, HZ + 2 * U)]), fill=255)
        if depth == 1:
            for px in (8, 19, 27, 41, 55, 63, 79, 88):
                thnot(d, px * U + r.random() * 2 * U, y0 + 0.5 * U, (5 + r.random() * 5) * U, (1.5 + r.random() * 0.8) * U, detail=False)
        rgb, a = over(rgb, a, col(colr), to_mask(im))

    # rice paddies in perspective
    fy = np.clip((YY - HZ) / (H - HZ), 0, 1)
    below = (YY >= HZ + 1.5 * U).astype(np.float32)
    # bands of fields toward the viewer, with gently wandering edges (hand-dug dikes)
    rowk = np.sqrt(fy) * 7.0 + 0.22 * np.sin(XX * 0.0042 + 1.3) + 0.1 * np.sin(XX * 0.011 + 0.4)
    slope = (XX - VX) / np.maximum(YY - VY, 1)     # perspective columns
    band = np.floor(rowk)
    colk = slope * 1.35 + 0.06 * np.sin(YY * 0.02 + band * 2.0) + np.sin(band * 7.13) * 0.5
    colid = np.floor(colk)
    cell_hash = (np.sin(band * 12.9898 + colid * 78.233) * 43758.5453) % 1.0
    flooded = cell_hash < 0.27
    # green rice with fine rows running toward the vanishing point
    pal = np.array(th["rice"], np.float32)
    rice = pal[np.clip((cell_hash * 3).astype(int), 0, 2)]
    rows = (0.5 + 0.5 * np.sin(slope * 140.0)) ** 3 * (0.25 + 0.75 * fy)
    rice = lerp(rice, th["rice_row"], rows * 0.55)
    # flooded paddies mirror the sky (and the moon / sun)
    water = lerp(th["water_top"], th["water_bot"], fy ** 0.7)
    glint = np.exp(-((XX - mx) / (2.6 * U * (0.6 + fy))) ** 2) * (0.35 + 0.65 * (np.sin(YY * 0.9) > 0.3))
    water = lerp(water, th["glint"], glint * (0.55 if dusk else 0.4))
    field = np.where(flooded[..., None], water, rice)
    rgb, a = over(rgb, a, field, below)
    # earthen dikes between fields
    fr = rowk - band; fc = colk - colid
    w_r = 0.022 + 0.035 * (1 - fy); w_c = 0.016 + 0.022 * fy
    dike = ((fr < w_r) | (fc < w_c)) & (below > 0)
    rgb, a = over(rgb, a, col(th["dike"]), dike.astype(np.float32) * 0.75)
    # mist along the horizon
    mist = np.exp(-((YY - (HZ + 2.5 * U)) / (2.2 * U)) ** 2)
    rgb, a = over(rgb, a, col(th["haze_far"]), mist * 0.35 * (a > 0))

    # the stilt house on a dike, with a warm window glow at dusk
    hs = canvas(); win = canvas()
    house_x, house_base, house_w = W * 0.70, HZ + 7.2 * U, 13 * U
    stilt_house(ImageDraw.Draw(hs), win, house_x, house_base, house_w)
    house = to_mask(hs)
    winm = to_mask(win) * house
    hcol = lerp(th["house"], th["house"], 0)
    roofmask = house * (YY < house_base - house_w * 0.34 - house_w * 0.36 + 0.6 * U)
    rgb, a = over(rgb, a, col(th["house"]), house)
    rgb, a = over(rgb, a, col(th["roof"]), roofmask)
    if dusk:
        glow = np.asarray(Image.fromarray((winm * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(14)), np.float32) / 255
        rgb, a = over(rgb, a, col(th["window"]), glow * 0.7)
        rgb, a = over(rgb, a, col(th["window"]), winm)
        # its reflection in the paddy below
        ry = np.clip(2 * house_base - YY, 0, H - 1).astype(int)
        refl = winm[ry, XX.astype(int)] * (YY > house_base) * np.exp(-(YY - house_base) / (6 * U))
        rgb, a = over(rgb, a, col(th["window"]), refl * 0.35 * (0.5 + 0.5 * (np.sin(YY * 1.1) > 0)))
        # moonlit rim on the roof
        rim = np.clip(house - np.roll(house, 2, axis=1), 0, 1) * (YY < house_base - house_w * 0.34)
        rgb, a = over(rgb, a, col(th["rim"]), rim * 0.6)
    else:
        rgb, a = over(rgb, a, col(th["window"]), winm * 0.85)
    return rgb, a


def front_layer(th, dusk):
    rgb = np.zeros((H, W, 3), np.float32)
    a = np.zeros((H, W), np.float32)
    im = canvas(); d = ImageDraw.Draw(im)
    for x, h, cr, lean in ((4.5, 50, 9.5, 1.2), (12.5, 40, 8.0, -0.8), (94.5, 54, 10.0, -1.4), (86.5, 37, 7.5, 0.9)):
        thnot(d, x * U, H * 0.97, h * U, cr * U, lean * U)
    # reeds at the water's edge
    r = np.random.default_rng(4)
    for base_x in list(np.linspace(0, 22, 14)) + list(np.linspace(78, 100, 14)):
        bx = base_x * U; hgt = (3 + r.random() * 4) * U; bend = (r.random() - 0.5) * 2.5 * U
        d.polygon(P([(bx - 0.25 * U, H), (bx + bend, H - hgt), (bx + 0.25 * U, H)]), fill=255)
    rgb, a = over(rgb, a, col(th["palm"]), to_mask(im))

    # lotus pond: pads and three flowers, bottom centre-left and right
    pads = canvas(); pd = ImageDraw.Draw(pads)
    for cx, cy, rr in ((30, 0.955, 3.4), (37, 0.985, 4.2), (25, 0.99, 2.8), (66, 0.965, 3.0), (72, 0.995, 3.8)):
        lotus_pad(pd, cx * U, H * cy, rr * U)
    rgb, a = over(rgb, a, col(th["leafpad"]), to_mask(pads))
    for tier, c in zip((0, 1, 2), th["lotus"]):
        fl = canvas(); fd = ImageDraw.Draw(fl)
        lotus_flower(fd, 33 * U, H * 0.955, 3.2 * U, tier)
        lotus_flower(fd, 69 * U, H * 0.965, 2.6 * U, tier)
        rgb, a = over(rgb, a, col(c), to_mask(fl))
    # a closed bud on a stem
    bud = canvas(); bdd = ImageDraw.Draw(bud)
    bdd.line(P([(41 * U, H), (41.5 * U, H * 0.93)]), fill=255, width=int(0.25 * U * SS))
    lotus_flower(bdd, 41.5 * U, H * 0.935, 1.6 * U, 2)
    rgb, a = over(rgb, a, col(th["lotus"][0]), to_mask(bud))
    return rgb, a


def save(rgb, a, path):
    out = np.dstack([np.clip(rgb, 0, 255), np.clip(a * 255, 0, 255)]).astype(np.uint8)
    Image.fromarray(out, "RGBA").save(path, "WEBP", quality=90, method=6)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/drawable-nodpi"
    for name, th in THEMES.items():
        dusk = name == "dusk"
        save(*back_layer(th, dusk), f"{out}/scene_back_{name}.webp")
        save(*front_layer(th, dusk), f"{out}/scene_front_{name}.webp")
        print("rendered", name)
