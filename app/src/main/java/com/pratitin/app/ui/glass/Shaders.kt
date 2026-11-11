package com.pratitin.app.ui.glass

import org.intellij.lang.annotations.Language

/**
 * AGSL shaders (Android 13+). They run per-pixel on the GPU inside RenderEffects,
 * so they cost nothing on the UI thread.
 */
internal object Shaders {

    /**
     * Liquid glass: a thick lens. Near the rim the backdrop is bent outward along the
     * SDF normal (refraction), split slightly per channel (dispersion), and lit by a
     * specular rim from the upper-left. The centre stays undistorted for legibility.
     */
    @Language("AGSL")
    const val LIQUID_GLASS = """
        uniform shader content;
        uniform float2 size;
        uniform float radius;
        uniform float thickness;
        uniform float refraction;
        uniform float dispersion;

        float sdRoundRect(float2 p, float2 halfSize, float r) {
            float2 q = abs(p) - halfSize + r;
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
        }

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 p = coord - halfSize;
            float r = min(radius, min(halfSize.x, halfSize.y));
            float d = sdRoundRect(p, halfSize, r);            // < 0 inside

            // SDF gradient → surface normal
            float e = 1.0;
            float2 n = float2(
                sdRoundRect(p + float2(e, 0), halfSize, r) - sdRoundRect(p - float2(e, 0), halfSize, r),
                sdRoundRect(p + float2(0, e), halfSize, r) - sdRoundRect(p - float2(0, e), halfSize, r));
            n = normalize(n + 1e-5);

            // 0 in the flat centre → 1 at the rim; cubic gives a convex lens profile
            float rim = clamp(1.0 + d / thickness, 0.0, 1.0);
            float bend = rim * rim * rim * refraction;
            float2 off = -n * bend;

            half4 c;
            c.r = content.eval(coord + off * (1.0 + dispersion)).r;
            c.g = content.eval(coord + off).g;
            c.b = content.eval(coord + off * (1.0 - dispersion)).b;
            c.a = 1.0;

            // specular rim light from the top-left, plus a faint bottom-right bounce
            float2 light = normalize(float2(-0.55, -0.85));
            float spec = pow(rim, 6.0) * max(dot(n, light), 0.0);
            float bounce = pow(rim, 8.0) * max(dot(n, -light), 0.0) * 0.35;
            c.rgb += half3(spec * 0.55 + bounce * 0.25);
            return c;
        }
    """

    /**
     * Aurora sky over Angkor: slow domain-warped noise mixed through the dusk palette,
     * with a warm glow where the sun has just set behind the towers.
     */
    @Language("AGSL")
    const val AURORA = """
        uniform float2 size;
        uniform float time;
        uniform half4 c0;   // deep night
        uniform half4 c1;   // lacquer
        uniform half4 c2;   // gold glow
        uniform half4 c3;   // lotus / jade accent

        float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
        float noise(float2 p) {
            float2 i = floor(p); float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + float2(1, 0)), u.x),
                       mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), u.x), u.y);
        }
        float fbm(float2 p) {
            float v = 0.0; float a = 0.5;
            for (int i = 0; i < 4; i++) { v += a * noise(p); p *= 2.03; a *= 0.5; }
            return v;
        }

        half4 main(float2 coord) {
            float2 uv = coord / size;
            float2 p = float2(uv.x * size.x / size.y, uv.y) * 2.2;
            float t = time * 0.035;
            float2 q = float2(fbm(p + t), fbm(p + float2(5.2, 1.3) - t));
            float f = fbm(p + 2.4 * q + float2(t * 0.6, -t));

            half3 col = c0.rgb;
            col = mix(col, c1.rgb, smoothstep(0.35, 0.9, f) * 0.85);
            col = mix(col, c3.rgb, smoothstep(0.55, 1.0, q.y) * 0.22 * (1.0 - uv.y));
            // horizon glow behind the temple silhouette
            float horizon = exp(-pow((uv.y - 0.86) * 4.2, 2.0)) * (0.6 + 0.4 * sin(uv.x * 3.0 + t * 3.0));
            col = mix(col, c2.rgb, clamp(horizon * 0.55, 0.0, 1.0));
            // vignette
            float v = smoothstep(1.25, 0.25, length(uv - float2(0.5, 0.45)));
            col *= 0.55 + 0.45 * v;
            // film grain, very subtle, avoids gradient banding on OLED
            col += (hash(coord + time) - 0.5) * 0.018;
            return half4(col, 1.0);
        }
    """
}
