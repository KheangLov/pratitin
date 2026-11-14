"""
SUPERSEDED by render_countryside.py (the rice-field scene now in the app).

Procedural Angkor Wat scene for Pratitin.

Renders the west facade of Angkor Wat at the reflecting pool — five lotus-bud towers in
quincunx, tiered galleries with colonnade openings, jungle canopy, sugar palms, mist and a
rippled reflection — as RGBA layers with transparent sky so the app's animated sky shows
through. Two lighting variants: 'dusk' (back-lit silhouette with gilded rim light) and
'day' (front-lit sandstone).
"""
import math, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

W, H = 1440, 960
SS = 2                      # supersampling for anti-aliased masks
HZ = int(H * 0.66)          # horizon / water line (temple base)
CX = W * 0.5
U = W / 100.0               # one "unit" = 1% of width
rng = np.random.default_rng(7)


def mask(draw_fn):
    """Render polygons via draw_fn(ImageDraw, scale) at SS×, return float mask [0,1] at 1×."""
    im = Image.new("L", (W * SS, H * SS), 0)
    d = ImageDraw.Draw(im)
    draw_fn(d, SS)
    im = im.resize((W, H), Image.LANCZOS)
    return np.asarray(im, dtype=np.float32) / 255.0


def poly(d, s, pts):
    d.polygon([(x * s, y * s) for x, y in pts], fill=255)


def rect(d, s, x0, y0, x1, y1):
    d.rectangle([x0 * s, y0 * s, x1 * s, y1 * s], fill=255)


# ---------------------------------------------------------------- noise
def fbm(shape, scale, octaves=5, seed=0):
    r = np.random.default_rng(seed)
    out = np.zeros(shape, np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        sy = max(2, int(shape[0] / scale * 2 ** o))
        sx = max(2, int(shape[1] / scale * 2 ** o))
        g = r.random((sy, sx)).astype(np.float32)
        g = np.asarray(Image.fromarray((g * 255).astype(np.uint8)).resize((shape[1], shape[0]), Image.BICUBIC), np.float32) / 255
        out += amp * g
        tot += amp
        amp *= 0.5
    return out / tot


# ---------------------------------------------------------------- tower geometry
def tower_outline(cx, base, w, h, tiers=11):
    """Lotus-bud prasat: sanctuary body with porch, then corbelled tiers with antefix
    serrations, narrowing into an ogival bud and finial. Returns left half points
    (bottom→top) mirrored into a closed polygon, plus tier line y positions."""
    left = []
    body_h = h * 0.26
    # plinth steps
    left += [(-w * 0.62, 0), (-w * 0.62, -h * 0.025), (-w * 0.56, -h * 0.025), (-w * 0.56, -h * 0.05),
             (-w * 0.52, -h * 0.05)]
    # sanctuary body with pilasters and a cornice
    left += [(-w * 0.52, -body_h * 0.82), (-w * 0.58, -body_h * 0.86), (-w * 0.58, -body_h * 0.93),
             (-w * 0.5, -body_h * 0.95), (-w * 0.5, -body_h)]
    tier_y = []
    y0 = body_h
    bud_h = h * 0.70
    for i in range(tiers):
        t0 = i / tiers
        t1 = (i + 1) / tiers
        # ogive profile: slight swell then sharp taper (lotus bud)
        prof = lambda t: (1 - t ** 1.35) ** 0.78 * (1 + 0.13 * math.sin(math.pi * t * 0.82))
        wa = w * 0.5 * prof(t0)
        wb = w * 0.5 * prof(t1)
        ya = y0 + bud_h * t0
        yb = y0 + bud_h * t1
        th = yb - ya
        # antefix: small flame-shaped protrusion at the tier corner
        left += [(-wa * 1.0, -ya), (-wa * 1.05, -(ya + th * 0.22)), (-wa * 1.02, -(ya + th * 0.45)),
                 (-wa * 0.98, -(ya + th * 0.55)), (-(wa * 0.55 + wb * 0.45), -(ya + th * 0.9)), (-wb * 1.01, -yb)]
        tier_y.append(ya + th * 0.55)
    top = y0 + bud_h
    left += [(-w * 0.035, -top), (-w * 0.02, -(top + h * 0.035)), (0, -h)]
    pts = [(cx + x, base + y) for x, y in left]
    pts += [(cx - x, base + y) for x, y in reversed(left[:-1])]
    return pts, [base - y for y in tier_y], base - y0, base - h


def gallery_pts(x0, x1, base, h, steps=3, roof=0.55):
    """A long gallery with a stepped (corbelled) roof."""
    pts = [(x0, base), (x0, base - h * (1 - roof))]
    for k in range(steps):
        dy = h * roof / steps
        inset = (k + 1) * h * 0.18
        pts += [(x0 + inset - h * 0.06, base - h * (1 - roof) - dy * k), (x0 + inset, base - h * (1 - roof) - dy * (k + 1))]
    pts += [(x1 - steps * h * 0.18, base - h), ]
    for k in reversed(range(steps)):
        dy = h * roof / steps
        inset = (k + 1) * h * 0.18
        pts += [(x1 - inset, base - h * (1 - roof) - dy * (k + 1)), (x1 - inset + h * 0.06, base - h * (1 - roof) - dy * k)]
    pts += [(x1, base - h * (1 - roof)), (x1, base)]
    return pts


# ---------------------------------------------------------------- scene description
def temple_shapes():
    """Returns list of (name, depth, polygon list) — depth 0 = farthest."""
    shapes = []
    # outer (third) gallery: long, low, across the whole facade
    g3 = gallery_pts(CX - 40 * U, CX + 40 * U, HZ, 3.2 * U, steps=3)
    shapes.append(("g3", 0.55, [g3]))
    # gopura pavilions at the ends of the outer gallery
    for sx in (-1, 1):
        p, _, _, _ = tower_outline(CX + sx * 38.5 * U, HZ - 2.6 * U, 3.6 * U, 6.5 * U, tiers=4)
        shapes.append(("gop", 0.55, [p]))
    # second gallery level
    g2 = gallery_pts(CX - 27 * U, CX + 27 * U, HZ - 2.4 * U, 4.6 * U, steps=3)
    shapes.append(("g2", 0.45, [g2]))
    # central terrace (Bakan) — tall stepped base
    bakan = [(CX - 15 * U, HZ - 6.6 * U), (CX - 15 * U, HZ - 9.8 * U), (CX - 14 * U, HZ - 9.8 * U), (CX - 14 * U, HZ - 12.6 * U),
             (CX + 14 * U, HZ - 12.6 * U), (CX + 14 * U, HZ - 9.8 * U), (CX + 15 * U, HZ - 9.8 * U), (CX + 15 * U, HZ - 6.6 * U)]
    shapes.append(("bakan", 0.35, [bakan]))
    g1 = gallery_pts(CX - 14.5 * U, CX + 14.5 * U, HZ - 12.4 * U, 3.0 * U, steps=2)
    shapes.append(("g1", 0.3, [g1]))
    return shapes


TOWERS = [
    # (dx in U, base offset in U above horizon, width U, height U, depth)
    (-7.4, 12.4, 5.7, 15.8, 0.2),   # rear corner pair (appear closer to centre)
    (7.4, 12.4, 5.7, 15.8, 0.2),
    (-13.4, 12.2, 6.1, 17.0, 0.4),  # front corner pair
    (13.4, 12.2, 6.1, 17.0, 0.4),
    (0.0, 12.6, 7.9, 24.0, 0.1),    # central sanctuary
]


def jungle(depth_seed, y_base, x_ranges, height, bumpiness):
    """Tree canopy: a lumpy crown line built from crowns, each crown a cluster of many
    small leaf-blobs, so the edge reads as foliage rather than smooth ellipses."""
    def fn(d, s):
        r = np.random.default_rng(depth_seed)
        for (xa, xb) in x_ranges:
            x = xa
            while x < xb:
                crown = height * (0.45 + 0.55 * r.random()) * bumpiness
                top = y_base - height * (0.45 + 0.75 * r.random())
                d.rectangle([(x - crown * 0.8) * s, (top + crown * 0.4) * s, (x + crown * 0.8) * s, y_base * s], fill=255)
                for _ in range(int(520 * bumpiness)):
                    a = r.uniform(0, 2 * math.pi)
                    rr = crown * math.sqrt(r.random())
                    bx = x + math.cos(a) * rr
                    by = top + crown * 0.45 + math.sin(a) * rr * 0.7
                    br = crown * r.uniform(0.025, 0.065)
                    d.ellipse([(bx - br) * s, (by - br) * s, (bx + br) * s, (by + br) * s], fill=255)
                x += crown * (0.7 + 0.6 * r.random())
    return mask(fn)


def palm(d, s, x, base, h, lean, r):
    """Sugar palm (ដើមត្នោត): slender trunk and a round fan crown of stiff fronds."""
    tx = x + lean * h
    ty = base - h
    # trunk with slight curve, tapering
    n = 30
    for i in range(n):
        t0, t1 = i / n, (i + 1) / n
        x0 = x + lean * h * (t0 ** 1.3); x1 = x + lean * h * (t1 ** 1.3)
        y0 = base - h * t0; y1 = base - h * t1
        w0 = 0.55 * U * (1 - 0.35 * t0); w1 = 0.55 * U * (1 - 0.35 * t1)
        d.polygon([((x0 - w0 / 2) * s, y0 * s), ((x0 + w0 / 2) * s, y0 * s), ((x1 + w1 / 2) * s, y1 * s), ((x1 - w1 / 2) * s, y1 * s)], fill=255)
    # crown: ~28 palmate leaves, each a long petiole ending in a fan of ~16 thin leaflets
    lw = max(1, int(0.14 * U * s))
    for k in range(28):
        a = -math.pi / 2 + r.uniform(-1.65, 1.65)
        L1 = r.uniform(1.6, 2.6) * U
        px = tx + math.cos(a) * L1
        py = ty + math.sin(a) * L1 * 0.85 + (0.6 * U if math.sin(a) > -0.3 else 0)
        d.line([(tx * s, ty * s), (px * s, py * s)], fill=255, width=lw)
        n_leaf = 16
        fan = r.uniform(0.9, 1.3)
        for j2 in range(n_leaf):
            b = a + (j2 / (n_leaf - 1) - 0.5) * fan
            L2 = r.uniform(1.8, 2.8) * U
            mx = px + math.cos(b) * L2 * 0.55
            my = py + math.sin(b) * L2 * 0.55
            ex = px + math.cos(b) * L2
            ey = py + math.sin(b) * L2 + 0.5 * U * (1 + math.cos(b) ** 2) * 0.6   # droop
            d.line([(px * s, py * s), (mx * s, my * s), (ex * s, ey * s)], fill=255, width=lw, joint="curve")
    d.ellipse([(tx - 0.8 * U) * s, (ty - 0.6 * U) * s, (tx + 0.8 * U) * s, (ty + 0.8 * U) * s], fill=255)
    # hanging dead fronds under the crown (characteristic of thnot palms)
    for k in range(6):
        a = math.pi / 2 + r.uniform(-0.9, 0.9)
        L = r.uniform(1.2, 2.2) * U
        d.line([(tx * s, ty * s), ((tx + math.cos(a) * L * 0.5) * s, (ty + math.sin(a) * L) * s)], fill=255, width=lw * 2)


# ---------------------------------------------------------------- rendering
def lerp(a, b, t):
    return a + (b - a) * t


def to_rgb(hexs):
    hexs = hexs.lstrip("#")
    return np.array([int(hexs[i:i + 2], 16) / 255 for i in (0, 2, 4)], np.float32)


def render(variant):
    dusk = variant == "dusk"
    rgb = np.zeros((H, W, 3), np.float32)
    alpha = np.zeros((H, W), np.float32)

    def over(color_img, a):
        nonlocal rgb, alpha
        a = np.clip(a, 0, 1)[..., None]
        rgb = color_img * a + rgb * (1 - a)
        alpha = a[..., 0] + alpha * (1 - a[..., 0])

    if dusk:
        haze = to_rgb("#6b3a4a"); stone = to_rgb("#120b16"); rim = to_rgb("#ffcf7a"); far_stone = to_rgb("#3a2232")
        jungle_far = to_rgb("#2a1a28"); jungle_near = to_rgb("#0d0810"); water = to_rgb("#1a0f1c"); sun = to_rgb("#ffd68a")
    else:
        haze = to_rgb("#d9c3a6"); stone = to_rgb("#a8876a"); rim = to_rgb("#fff1d6"); far_stone = to_rgb("#bfa384")
        jungle_far = to_rgb("#7d8a6a"); jungle_near = to_rgb("#3f4a35"); water = to_rgb("#6f8c8f"); sun = to_rgb("#fff4dc")

    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    sun_xy = (CX + 6 * U, HZ - 11 * U)

    # --- sun glow (semi-transparent so the app's sky shows through)
    d2 = ((xx - sun_xy[0]) ** 2 + ((yy - sun_xy[1]) * 1.15) ** 2) ** 0.5
    glow = np.exp(-d2 / (22 * U)) * (0.55 if dusk else 0.35) + np.exp(-d2 / (6 * U)) * 0.35
    glow *= (yy < HZ)
    disc = np.clip((2.4 * U - d2) / 2, 0, 1) * (1.0 if dusk else 0.8)
    over(np.broadcast_to(sun, (H, W, 3)), glow * 0.9)
    over(np.broadcast_to(sun * 1.05, (H, W, 3)), disc)

    # --- far jungle at the horizon edges (atmospheric)
    jf = jungle(11, HZ, [(-2 * U, 30 * U), (70 * U, 102 * U)], 5.5 * U, 1.0)
    over(np.broadcast_to(lerp(jungle_far, haze, 0.35), (H, W, 3)), jf * 0.95)

    # --- temple
    noise = fbm((H, W), 40, 5, seed=3)
    fine = fbm((H, W), 6, 3, seed=5)
    temple_alpha = np.zeros((H, W), np.float32)
    shapes = temple_shapes()
    layers = []
    for name, depth, polys in shapes:
        m = mask(lambda d, s, polys=polys: [poly(d, s, p) for p in polys])
        layers.append((depth, m, None))
    tier_lines = []
    for (dx, boff, tw, th, depth) in TOWERS:
        pts, tiers, body_top, tip = tower_outline(CX + dx * U, HZ - boff * U, tw * U, th * U)
        m = mask(lambda d, s, pts=pts: poly(d, s, pts))
        layers.append((depth, m, (CX + dx * U, tw * U, tiers, body_top)))

    # paint far → near (higher depth value = nearer)
    for depth, m, tower in sorted(layers, key=lambda l: l[0]):
        base = lerp(stone, far_stone, (1 - depth) * (0.55 if dusk else 0.35))
        col = np.broadcast_to(base, (H, W, 3)).copy()
        if tower is not None:
            tcx, tw, tiers, body_top = tower
            rel = np.clip((xx - tcx) / (tw * 0.6), -1, 1)
            if dusk:
                # back-lit: right edge (toward the sun) picks up warm light
                col += (rim * 0.10) * np.clip(rel, 0, 1)[..., None] ** 3
            else:
                # front-lit from the upper left: rounded shading across the bud
                shade = 1.0 + 0.22 * (-rel) - 0.12 * rel ** 2
                col *= shade[..., None]
            # tier bands: dark shadow line under each corbel + light ledge
            band = np.zeros((H, W), np.float32)
            for ty in tiers:
                band += np.exp(-((yy - ty) / 1.1) ** 2) * 0.8
                band -= np.exp(-((yy - (ty - 2.5)) / 0.9) ** 2) * 0.3
            col *= (1 - (0.16 if dusk else 0.26) * np.clip(band, -1, 1))[..., None]
            # niches / doorway on the sanctuary body
            door = (np.abs(xx - tcx) < tw * 0.09) & (yy > body_top + 4) & (yy < body_top + tw * 0.55)
            col[door] *= 0.62 if not dusk else 0.6
            if dusk:
                col[door] = lerp(col[door], sun * 0.5, 0.12)
        else:
            # galleries: colonnade openings — the sky glows through in dusk, deep shade in day
            period = 1.15 * U
            cols = ((xx % period) < period * 0.42)
            open_band = cols & (m > 0.5)
            ys = np.where(m > 0.5, yy, np.nan)
        # carved texture
        tex = 1 + (noise - 0.5) * (0.22 if not dusk else 0.16) + (fine - 0.5) * (0.12 if not dusk else 0.08)
        col *= tex[..., None]
        over(col, m)
        temple_alpha = np.maximum(temple_alpha, m)

    # colonnade openings on galleries: thin bright slits just below roof lines
    slits = np.zeros((H, W), np.float32)
    for (x0, x1, top, bot) in [(CX - 39.5 * U, CX + 39.5 * U, HZ - 1.9 * U, HZ - 0.5 * U),
                               (CX - 26.5 * U, CX + 26.5 * U, HZ - 4.5 * U, HZ - 2.9 * U),
                               (CX - 14 * U, CX + 14 * U, HZ - 14.2 * U, HZ - 12.9 * U)]:
        period = 1.1 * U
        inside = (xx > x0) & (xx < x1) & (yy > top) & (yy < bot)
        slits += (inside & ((xx % period) < period * 0.38)).astype(np.float32)
    slits = ndimage.gaussian_filter(slits, 0.6)
    if dusk:
        over(np.broadcast_to(lerp(sun, haze, 0.6) * 0.55, (H, W, 3)), slits * 0.28)
    else:
        over(np.broadcast_to(stone * 0.45, (H, W, 3)), slits * 0.55)

    # rim light along silhouette edges facing the sun
    if dusk:
        sil = temple_alpha
        sx, sy = sun_xy
        # direction-weighted edge: mask minus mask shifted away from the sun
        shifted = ndimage.shift(sil, (1.2, -2.2), order=1, mode="constant")
        edge = np.clip(sil - shifted, 0, 1)
        dist_sun = np.exp(-(((xx - sx) ** 2 + (yy - sy) ** 2) ** 0.5) / (26 * U))
        edge = ndimage.gaussian_filter(edge, 1.1) * (0.12 + 0.75 * dist_sun)
        over(np.broadcast_to(lerp(rim, sun, 0.4), (H, W, 3)), np.clip(edge * 1.4, 0, 0.85) * sil)
        # soft glow halo around the towers (light wrapping)
        halo = ndimage.gaussian_filter(sil, 9) - sil * 0.0
        halo = np.clip(halo - sil, 0, 1) * dist_sun * 0.55 * (yy < HZ)
        over(np.broadcast_to(sun, (H, W, 3)), halo)

    # --- mist band at the base of the temple
    mist = np.exp(-((yy - (HZ - 2.2 * U)) / (2.4 * U)) ** 2) * (0.55 + 0.45 * fbm((H, W), 120, 4, seed=9))
    over(np.broadcast_to(lerp(haze, sun, 0.25), (H, W, 3)), mist * (0.42 if dusk else 0.35) * (yy < HZ + 2))

    # --- nearer jungle on the flanks (in front of outer gallery ends)
    jn = jungle(21, HZ + 0.4 * U, [(-3 * U, 14 * U), (88 * U, 103 * U)], 7.5 * U, 1.1)
    over(np.broadcast_to(jungle_near, (H, W, 3)) * (1 + (noise[..., None] - 0.5) * 0.3), jn)

    # --- water: mirrored reflection with ripples
    above_rgb = rgb.copy(); above_a = alpha.copy()
    wy = np.arange(HZ, H)
    src_y = np.clip(2 * HZ - wy, 0, HZ - 1)
    depth_t = (wy - HZ) / (H - HZ)
    ripple_n = fbm((H - HZ, W), 30, 4, seed=13)
    amp = (0.4 + 7.0 * depth_t ** 1.3)[:, None]
    dx = (np.sin(wy[:, None] * 0.55 + ripple_n * 9) * amp).astype(np.float32)
    src_x = np.clip(np.arange(W)[None, :] + dx, 0, W - 1).astype(np.int32)
    refl_rgb = above_rgb[src_y[:, None], src_x]
    refl_a = above_a[src_y[:, None], src_x]
    water_base = np.broadcast_to(water, (H - HZ, W, 3))
    fade = (1 - depth_t[:, None] * 0.55)
    refl_mix = refl_rgb * (0.62 if dusk else 0.55) + water_base * (0.38 if dusk else 0.45)
    water_rgb = refl_mix * refl_a[..., None] * fade[..., None] + water_base * (1 - refl_a[..., None] * fade[..., None])
    # sun glitter column
    glit = np.exp(-((np.arange(W)[None, :] - sun_xy[0]) / (5 * U)) ** 2) * (np.sin(wy[:, None] * 1.7 + ripple_n * 14) > 0.75)
    water_rgb = water_rgb + sun * glit[..., None] * (0.55 if dusk else 0.35) * (1 - depth_t[:, None] * 0.6)[..., None]
    # horizon highlight line
    water_rgb[:2] = water_rgb[:2] * 0.8 + sun * 0.2
    rgb[HZ:] = water_rgb
    alpha[HZ:] = 1.0
    # lightly blur the reflection (water is never a perfect mirror)
    rgb[HZ + 2:] = ndimage.gaussian_filter(rgb[HZ + 2:], (0.8, 1.6, 0))

    # darken toward the bottom (vignette into the app UI)
    vig = np.clip((yy - HZ) / (H - HZ), 0, 1) ** 1.6 * (0.55 if dusk else 0.25)
    rgb *= (1 - vig)[..., None]

    back = np.dstack([np.clip(rgb, 0, 1), np.clip(alpha, 0, 1)])

    # ---------------- front layer: sugar palms + lotus-leaf bank
    def palms_fn(d, s):
        r = np.random.default_rng(42)
        for (x, hgt, lean) in [(6 * U, 30 * U, 0.05), (11.5 * U, 24 * U, -0.03), (2.5 * U, 21 * U, 0.08),
                               (93 * U, 28 * U, -0.06), (97.5 * U, 22 * U, 0.02)]:
            palm(d, s, x, HZ + 1.5 * U, hgt, lean, r)
    pm = mask(palms_fn)

    def bank_fn(d, s):
        r = np.random.default_rng(5)
        # lotus leaves floating near the bottom
        for _ in range(26):
            x = r.uniform(-2, 102) * U
            y = H - r.uniform(0.5, 9) * U
            rx = r.uniform(1.8, 4.2) * U
            ry = rx * r.uniform(0.18, 0.3)
            d.ellipse([(x - rx) * s, (y - ry) * s, (x + rx) * s, (y + ry) * s], fill=255)
        # reeds / grasses at the bottom corners
        for _ in range(160):
            side = r.random() < 0.5
            x = (r.uniform(-1, 16) if side else r.uniform(84, 101)) * U
            hgt = r.uniform(3, 11) * U
            lean = r.uniform(-1.2, 1.2) * U
            w = r.uniform(0.15, 0.4) * U
            d.polygon([((x - w) * s, H * s), ((x + w) * s, H * s), ((x + lean) * s, (H - hgt) * s)], fill=255)
    bm = mask(bank_fn)
    front_rgb = np.zeros((H, W, 3), np.float32)
    front_a = np.clip(pm + bm, 0, 1)
    fcol = jungle_near * (0.85 if dusk else 0.8)
    front_rgb[:] = fcol
    # palm reflection in water (mirrored, rippled, faint)
    pr_src = pm[src_y[:, None], src_x] * (1 - depth_t[:, None] * 0.7) * 0.45
    front_a[HZ:] = np.maximum(front_a[HZ:], pr_src)
    if dusk:
        # rim on palm fronds near the sun side
        sh = ndimage.shift(pm, (1.5, -1.5), order=1)
        e = np.clip(pm - sh, 0, 1) * 0.5
        front_rgb = front_rgb * (1 - e[..., None]) + rim * e[..., None]
    else:
        ftex = 1 + (fbm((H, W), 8, 3, seed=17) - 0.5) * 0.35
        front_rgb *= ftex[..., None]
    front = np.dstack([np.clip(front_rgb, 0, 1), front_a])
    return back, front


def save(arr, path):
    img = Image.fromarray((arr * 255).astype(np.uint8), "RGBA")
    img.save(path, "WEBP", quality=88, method=6)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    for v in ("dusk", "day"):
        back, front = render(v)
        save(back, f"{out}/angkor_back_{v}.webp")
        save(front, f"{out}/angkor_front_{v}.webp")
        # preview over a representative sky
        sky = np.zeros((H, W, 3), np.float32)
        t = np.linspace(0, 1, H)[:, None, None]
        if v == "dusk":
            sky[:] = to_rgb("#0b0818") * (1 - t) + to_rgb("#5a1f2e") * t
        else:
            sky[:] = to_rgb("#f2e3cc") * (1 - t) + to_rgb("#e8c49a") * t
        comp = sky * (1 - back[..., 3:]) + back[..., :3] * back[..., 3:]
        comp = comp * (1 - front[..., 3:]) + front[..., :3] * front[..., 3:]
        Image.fromarray((np.clip(comp, 0, 1) * 255).astype(np.uint8)).save(f"{out}/preview_{v}.png")
        print("rendered", v)
