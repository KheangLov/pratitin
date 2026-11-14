"""
Modern, flag-inspired Angkor Wat for Pratitin's background.

Takes its cues from the Cambodian flag — royal blue above and below, a bold crimson band,
a white Angkor Wat at the centre — and renders them as a flat, geometric scene:

  back layer   crimson band with pinstripes and a soft glow, a white three-tower Angkor Wat
               with cut-out tiers, colonnades and a central stair, and a blue moat with a
               striped reflection
  front layer  flat navy sugar palms and two white lotus flowers in the water

The sky stays transparent so the app's animated (now royal-blue) sky shows through.
Two variants: 'dusk' (deep royal blue, rich crimson) and 'day' (brighter blue, coral red).

Usage:  python3 tools/render_flag_angkor.py app/src/main/res/drawable-nodpi
"""
import math
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1440, 960
SS = 3
HZ = int(H * 0.66)       # waterline (temple base)
BT = int(H * 0.27)       # top of the crimson band
CX = W * 0.5
U = W / 100.0

THEMES = {
    "dusk": dict(
        red_c=(214, 22, 48), red_e=(132, 8, 30), white=(255, 250, 240), gold=(246, 206, 120),
        water_top=(12, 44, 150), water_bot=(5, 18, 72), palm=(4, 12, 48), lotus=(255, 246, 236), line=(255, 255, 255),
    ),
    "day": dict(
        red_c=(236, 58, 76), red_e=(186, 28, 50), white=(255, 255, 255), gold=(232, 182, 83),
        water_top=(52, 108, 222), water_bot=(28, 66, 168), palm=(14, 40, 110), lotus=(255, 255, 255), line=(255, 255, 255),
    ),
}


def canvas():
    return Image.new("L", (W * SS, H * SS), 0)


def to_mask(im):
    return np.asarray(im.resize((W, H), Image.LANCZOS), np.float32) / 255.0


def P(pts):
    return [(x * SS, y * SS) for x, y in pts]


# ------------------------------------------------------------------ temple geometry

def tower(d, cx, base, w, h, cut):
    """Lotus-bud prasat: a body block, then an ogive bud. Tier lines are drawn into `cut`."""
    body_h = h * 0.26
    d.rectangle(P([(cx - w * 0.5, base - body_h), (cx + w * 0.5, base)]), fill=255)
    # porch steps on the body
    d.rectangle(P([(cx - w * 0.62, base - body_h * 0.35), (cx + w * 0.62, base)]), fill=255)
    bud_base = base - body_h
    bud_h = h - body_h
    left = []
    n = 40
    for i in range(n + 1):
        s = i / n
        half = w * 0.5 * (1 - s ** 1.7) ** 0.75 * (1.0 + 0.1 * math.sin(math.pi * s))
        left.append((cx - half, bud_base - s * bud_h))
    right = [(2 * cx - x, y) for x, y in reversed(left)]
    d.polygon(P(left + right), fill=255)
    # finial
    d.polygon(P([(cx - w * 0.04, bud_base - bud_h), (cx + w * 0.04, bud_base - bud_h), (cx, bud_base - bud_h - h * 0.06)]), fill=255)
    # tier cut-lines across the bud and a doorway in the body
    cd = ImageDraw.Draw(cut)
    for k in range(1, 9):
        y = bud_base - bud_h * (k / 9.5)
        s = k / 9.5
        half = w * 0.5 * (1 - s ** 1.7) ** 0.75 * (1.0 + 0.1 * math.sin(math.pi * s))
        cd.line(P([(cx - half * 0.86, y), (cx + half * 0.86, y)]), fill=255, width=int(0.28 * U * SS))
    cd.rectangle(P([(cx - w * 0.11, base - body_h * 0.82), (cx + w * 0.11, base - body_h * 0.35)]), fill=255)


def gallery(d, cut, cx, base, width, height, roof, slits=True, step=2.2):
    """A gallery block with a hipped roof; colonnade slits cut into its face."""
    d.rectangle(P([(cx - width / 2, base - height), (cx + width / 2, base)]), fill=255)
    d.polygon(P([(cx - width / 2 - 0.6 * U, base - height), (cx + width / 2 + 0.6 * U, base - height),
                 (cx + width / 2 - roof * 1.4, base - height - roof), (cx - width / 2 + roof * 1.4, base - height - roof)]), fill=255)
    cd = ImageDraw.Draw(cut)
    # eave line
    cd.line(P([(cx - width / 2 + 0.3 * U, base - height + 0.35 * U), (cx + width / 2 - 0.3 * U, base - height + 0.35 * U)]), fill=255, width=int(0.22 * U * SS))
    if slits:
        x = cx - width / 2 + step * U * 0.75
        while x < cx + width / 2 - step * U * 0.5:
            if abs(x - cx) > 3.2 * U:  # leave room for the stair
                cd.rectangle(P([(x - 0.28 * U, base - height + 1.0 * U), (x + 0.28 * U, base - 0.7 * U)]), fill=255)
            x += step * U


def temple_masks():
    body_im, cut_im = canvas(), canvas()
    d = ImageDraw.Draw(body_im)
    # terraces
    d.rectangle(P([(CX - 40 * U, HZ - 1.6 * U), (CX + 40 * U, HZ)]), fill=255)
    d.rectangle(P([(CX - 35 * U, HZ - 3.2 * U), (CX + 35 * U, HZ - 1.6 * U)]), fill=255)
    # galleries, stepping up
    gallery(d, cut_im, CX, HZ - 3.2 * U, 62 * U, 4.6 * U, 1.4 * U)
    g2 = HZ - 3.2 * U - 4.6 * U - 1.4 * U
    gallery(d, cut_im, CX, g2, 46 * U, 4.2 * U, 1.3 * U, step=2.0)
    g3 = g2 - 4.2 * U - 1.3 * U
    gallery(d, cut_im, CX, g3, 32 * U, 3.6 * U, 1.1 * U, step=1.8)
    top = g3 - 3.6 * U - 1.1 * U
    # corner pavilions on the outer gallery ends
    for sx in (-1, 1):
        tower(d, CX + sx * 29 * U, HZ - 3.2 * U - 4.6 * U, 4.2 * U, 9.0 * U, cut_im)
    # the three towers of the flag: a tall centre and two flanking
    for sx in (-1, 1):
        tower(d, CX + sx * 12.5 * U, top + 0.4 * U, 6.6 * U, 17.5 * U, cut_im)
    tower(d, CX, top + 0.4 * U, 8.6 * U, 23.5 * U, cut_im)
    # central stair rising to the top gallery
    d.polygon(P([(CX - 4.8 * U, HZ), (CX + 4.8 * U, HZ), (CX + 2.6 * U, top + 0.4 * U), (CX - 2.6 * U, top + 0.4 * U)]), fill=255)
    cd = ImageDraw.Draw(cut_im)
    y = HZ - 0.9 * U
    while y > top + 1.2 * U:
        f = (HZ - y) / (HZ - top)
        half = 4.8 * U - (4.8 - 2.6) * U * f
        cd.line(P([(CX - half * 0.8, y), (CX + half * 0.8, y)]), fill=255, width=int(0.16 * U * SS))
        y -= 1.1 * U
    # terrace edge lines
    cd.line(P([(CX - 39.4 * U, HZ - 1.6 * U), (CX + 39.4 * U, HZ - 1.6 * U)]), fill=255, width=int(0.2 * U * SS))
    body = to_mask(body_im)
    cut = to_mask(cut_im) * body
    return body, cut


# ------------------------------------------------------------------ layers

def lerp(a, b, t):
    t = np.asarray(t, np.float32)[..., None]
    return np.asarray(a, np.float32) * (1 - t) + np.asarray(b, np.float32) * t


def over(dst_rgb, dst_a, rgb, a):
    """Straight-alpha 'over' on float arrays."""
    a = a[..., None] if a.ndim == 2 else a
    out_a = a[..., 0] + dst_a * (1 - a[..., 0])
    out_rgb = (rgb * a + dst_rgb * dst_a[..., None] * (1 - a)) / np.maximum(out_a[..., None], 1e-6)
    return out_rgb, out_a


def back_layer(th):
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    rgb = np.zeros((H, W, 3), np.float32)
    alpha = np.zeros((H, W), np.float32)

    # crimson band: brighter behind the temple, deeper at the edges and towards the top
    band = ((yy >= BT) & (yy < HZ)).astype(np.float32)
    radial = np.clip(1 - np.hypot((xx - CX) / (W * 0.55), (yy - HZ * 0.92) / (H * 0.45)), 0, 1)
    band_rgb = lerp(th["red_e"], th["red_c"], radial ** 0.8)
    # soft top edge (1–2 px AA) so the band sits crisply on the moving sky
    edge = np.clip((yy - BT + 1) / 2, 0, 1) * (yy < HZ)
    rgb, alpha = over(rgb, alpha, band_rgb, edge)
    # modern pinstripes: a white hairline at the band's top and a gold one just under it
    for y0, col, a in ((BT + 2, th["line"], 0.55), (BT + 10, th["gold"], 0.45)):
        m = np.exp(-((yy - y0) / 1.1) ** 2) * (xx > W * 0.04) * (xx < W * 0.96)
        rgb, alpha = over(rgb, alpha, np.broadcast_to(np.array(col, np.float32), rgb.shape), m * a * band)
    # thin geometric rays fanning from the temple (subtle, modern)
    ang = np.arctan2(HZ - yy, xx - CX)
    rays = (0.5 + 0.5 * np.cos(ang * 22)) ** 6 * band * np.clip((HZ - yy) / (HZ - BT), 0, 1) * 0.10
    rgb, alpha = over(rgb, alpha, np.broadcast_to(np.array(th["gold"], np.float32), rgb.shape), rays)

    body, cut = temple_masks()
    # halo behind the temple
    glow = np.asarray(Image.fromarray((body * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(28)), np.float32) / 255
    rgb, alpha = over(rgb, alpha, np.broadcast_to(np.array(th["white"], np.float32), rgb.shape), glow * 0.28 * band)
    # the temple: white, details cut through to the crimson behind
    temple_a = np.clip(body - cut * 0.92, 0, 1)
    shade = 1 - 0.07 * np.clip((yy - (HZ - 30 * U)) / (30 * U), 0, 1)  # a touch of depth low down
    t_rgb = np.asarray(th["white"], np.float32) * shade[..., None]
    rgb, alpha = over(rgb, alpha, t_rgb, temple_a)
    # gold rim on the right-hand edges (light from the left)
    rim = np.clip(body - np.roll(body, -2, axis=1), 0, 1)
    rgb, alpha = over(rgb, alpha, np.broadcast_to(np.array(th["gold"], np.float32), rgb.shape), rim * 0.55)

    # moat
    water = (yy >= HZ).astype(np.float32)
    w_rgb = lerp(th["water_top"], th["water_bot"], np.clip((yy - HZ) / (H - HZ), 0, 1))
    rgb, alpha = over(rgb, alpha, w_rgb, water)
    # reflection: mirrored band + temple, squashed, in horizontal stripes
    ry = (2 * HZ - yy) * 1.0
    src_y = np.clip(HZ - (yy - HZ) / 0.85, 0, H - 1).astype(int)
    fade = np.clip(1 - (yy - HZ) / ((H - HZ) * 0.95), 0, 1) * water
    stripes = 0.25 + 0.75 * (np.sin((yy - HZ) * 0.75 + np.sin(xx * 0.012) * 2.0) > 0.1)
    band_ref = ((src_y >= BT) & (src_y < HZ)).astype(np.float32)
    rgb, alpha = over(rgb, alpha, band_rgb[src_y, xx.astype(int)], band_ref * fade * stripes * 0.20)
    temple_ref = temple_a[src_y, xx.astype(int)]
    rgb, alpha = over(rgb, alpha, np.broadcast_to(np.array(th["white"], np.float32), rgb.shape), temple_ref * fade ** 1.4 * stripes * 0.34)
    # waterline
    wl = np.exp(-((yy - HZ - 1) / 1.2) ** 2) * (np.abs(xx - CX) < 44 * U)
    rgb, alpha = over(rgb, alpha, np.broadcast_to(np.array(th["line"], np.float32), rgb.shape), wl * 0.7)
    return rgb, alpha


def palm(d, x, base, h, lean, crown):
    top = (x + lean, base - h)
    # trunk: slim tapered quad with a slight bow
    left, right = [], []
    for i in range(21):
        s = i / 20
        cx = x + lean * s + math.sin(s * math.pi) * lean * 0.25
        hw = (0.55 - 0.25 * s) * U
        left.append((cx - hw, base - h * s))
        right.append((cx + hw, base - h * s))
    d.polygon(P(left + right[::-1]), fill=255)
    # fan crown: sugar-palm leaves as thin blades
    for k in range(15):
        a = math.radians(-180 + k * (180 / 14) + 6 * math.sin(k))
        l = crown * (0.85 + 0.25 * math.sin(k * 1.7))
        tip = (top[0] + math.cos(a) * l, top[1] + math.sin(a) * l * 0.85)
        nx, ny = -math.sin(a) * crown * 0.07, math.cos(a) * crown * 0.07
        d.polygon(P([(top[0] + nx, top[1] + ny), tip, (top[0] - nx, top[1] - ny)]), fill=255)
    d.ellipse(P([(top[0] - crown * 0.12, top[1] - crown * 0.1), (top[0] + crown * 0.12, top[1] + crown * 0.12)]), fill=255)


def lotus(d, cx, cy, r, tier):
    petals = {0: ((-70, 0.3, 0.66), (70, 0.3, 0.66)), 1: ((-38, 0.36, 0.86), (38, 0.36, 0.86)), 2: ((0, 0.42, 1.0),)}[tier]
    for a, sx, sy in petals:
        pts = []
        for i in range(25):
            t = i / 24 * 2 * math.pi
            px, py = math.sin(t) * r * sx, -(1 - math.cos(t)) * 0.5 * r * sy
            ra = math.radians(a)
            pts.append((cx + px * math.cos(ra) - py * math.sin(ra), cy + px * math.sin(ra) + py * math.cos(ra)))
        d.polygon(P(pts), fill=255)


def front_layer(th):
    palms_im, lot_im = canvas(), canvas()
    d = ImageDraw.Draw(palms_im)
    for x, h, lean, c in ((4 * U, 42 * U, 2.5 * U, 9 * U), (10.5 * U, 34 * U, -1.5 * U, 8 * U), (16 * U, 26 * U, 1.0 * U, 6.5 * U),
                          (96 * U, 44 * U, -2.5 * U, 9.5 * U), (89 * U, 31 * U, 1.8 * U, 7.5 * U)):
        palm(d, x, H * 0.80, h, lean, c)
    # bank under the palms
    d.ellipse(P([(-14 * U, H * 0.775), (30 * U, H * 1.06)]), fill=255)
    d.ellipse(P([(74 * U, H * 0.77), (114 * U, H * 1.06)]), fill=255)
    palms = to_mask(palms_im)
    rgb = np.zeros((H, W, 3), np.float32)
    a = np.zeros((H, W), np.float32)
    rgb, a = over(rgb, a, np.broadcast_to(np.array(th["palm"], np.float32), rgb.shape), palms)
    for tier, col in ((0, (240, 150, 175)), (1, (252, 200, 214)), (2, th["lotus"])):
        im = canvas(); ld = ImageDraw.Draw(im)
        lotus(ld, 36 * U, H * 0.93, 3.4 * U, tier)
        lotus(ld, 66 * U, H * 0.955, 2.6 * U, tier)
        rgb, a = over(rgb, a, np.broadcast_to(np.array(col, np.float32), rgb.shape), to_mask(im))
    # gold heart in each lotus
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    for cx, cy, r in ((36 * U, H * 0.93, 3.4 * U), (66 * U, H * 0.955, 2.6 * U)):
        m = np.exp(-(((xx - cx) / (r * 0.18)) ** 2 + ((yy - cy + r * 0.1) / (r * 0.12)) ** 2))
        rgb, a = over(rgb, a, np.broadcast_to(np.array(th["gold"], np.float32), rgb.shape), m * 0.9)
    return rgb, a


def save(rgb, a, path):
    out = np.dstack([np.clip(rgb, 0, 255), np.clip(a * 255, 0, 255)]).astype(np.uint8)
    Image.fromarray(out, "RGBA").save(path, "WEBP", quality=90, method=6)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res/drawable-nodpi"
    for name, th in THEMES.items():
        save(*back_layer(th), f"{out}/angkor_back_{name}.webp")
        save(*front_layer(th), f"{out}/angkor_front_{name}.webp")
        print("rendered", name)
