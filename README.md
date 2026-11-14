# ប្រតិទិន · Pratitin

A native Android calendar built on the **Khmer lunar calendar (ចន្ទគតិ)**. It combines:

- **Calendar**: Gregorian and Khmer lunar dates side by side, moon phases, Sila days (ថ្ងៃសីល), leap months and leap days
- **Holidays & smart breaks**: Cambodian public holidays from the official sub-decrees, Buddhist observances and global days, plus a planner that shows which leave days give the longest breaks ("Take 1 day → 6 days off")
- **Mood calendar**: one-tap mood logging, a year-in-pixels grid, a 30-day trend, and how your mood on Sila days compares
- **Life in weeks**: your whole life as a grid of weeks, with weeks tinted by the moods you logged

The design draws on Khmer heritage: the Cambodian countryside at moonrise (sugar palms, rice paddies, a wooden stilt house, a lotus pond; `tools/render_countryside.py`) under a royal-blue sky, kbach (ក្បាច់) ornament borders, lotus motifs, Moul display type, and a lacquer-and-gold palette.

---

## Build and run

1. Open the folder in **Android Studio** (Ladybug or newer).
2. Let Gradle sync. It downloads AGP 8.7.3, Kotlin 2.1.0 and Compose BOM 2024.12.01.
3. Run the `app` configuration on a device or emulator. It needs Android 8.0 (API 26) or newer.
   - **API 31+**: real background blur on glass surfaces
   - **API 33+**: liquid-glass refraction shader and the animated aurora sky

```bash
./gradlew :khmercalendar:test   # lunar engine tests (pure JVM, runs in seconds)
./gradlew :app:assembleDebug
```

> **Verification status:** the `khmercalendar` engine module was compiled and tested here. All tests pass (see *Accuracy* below). The Android `app` module was written carefully against the Compose 1.7 APIs, but **it could not be compiled here** because Google's Maven repository was unreachable. If your first sync shows a compile error, it will most likely be a small API-signature mismatch. Paste it back and I'll fix it.

---

## Accuracy of the lunar calendar

`khmercalendar/` is a dependency-free Kotlin port of the traditional **Chhankitek / Soriyatra Lerng Sak** reckoning. It follows the MIT-licensed [momentkh](https://github.com/ThyrithSor/momentkh) implementation and keeps the Khmer names for intermediate values (Aharkun, Avoman, Bodithey, Kromthupul…).

What it was checked against:

| Check | Result |
|---|---|
| 2,000 random date/times from 1900–2200, full Khmer string (day, month, animal year, Sak, BE) | **2000 / 2000 match** |
| Exact Moha Songkran moment, every year 1900–2300 | **401 / 401 match** |
| Visak Bochea, Royal Ploughing, Pchum Ben and Water Festival dates from the official 2023–2027 sub-decrees | **19 / 19 match** |
| Public holiday count: 2026 = 21, 2027 = 22 (sub-decree totals) | ✓ |

It handles **Adhikameas** (leap month, two Asadh months, as in 2026), **Chantrathimeas** (30-day Jesth), BE year change on ១រោច ពិសាខ, the animal year change at the exact Songkran minute, and the Sak change on ថ្ងៃឡើងស័ក.

### Holidays
- **Official** (marked ✓): 2024–2027, checked against the published sub-decrees, including the 2026 list where Visak Bochea falls on 1 May together with Labour Day.
- **Calculated** (marked ≈): later years. Lunar holidays come from the engine and fixed-date holidays use the current rules. Cambodia confirms each year by sub-decree, usually around September. Add the new year to `Holidays.OFFICIAL_YEARS` and adjust `OFFICIAL_NEW_YEAR_*` if needed.
- Meak Bochea is shown as an observance. It stopped being a day off in 2020.

---

## Architecture

```
khmercalendar/          pure Kotlin (no Android): engine, holidays, leave planner + tests
app/
  data/                 Settings (DataStore), MoodStore (in-memory map + AtomicFile)
  ui/glass/             Backdrop + glass() modifier, AGSL shaders
  ui/shape/             SquircleShape (Figma-style continuous corners)
  ui/heritage/          kbach borders, prasat skyline, lotus, moon glyph
  ui/background/        living aurora sky + Angkor moat
  ui/screens/           Today · Calendar · Holidays · Mood · Life · Day sheet · Settings
  widget/               Glance home-screen widget
```

### How the glass works
- `backdropSource` records the content behind the glass into a RenderNode-backed `GraphicsLayer` once per frame. It then blurs a **quarter-resolution** copy, which is 1/16 of the pixels and visually the same once blurred.
- Glass surfaces draw that blurred layer by reference. Scrolling or animation behind them updates on the RenderThread with **no recomposition**.
- `liquid = true` adds an AGSL lens shader on top: refraction at the rim along the surface normal, slight colour fringing, and a light highlight along the top-left edge.
- There are two layers: cards see through to the **sky**, while the tab bar and sheets see through to the **sky plus the cards**. A guard (`LocalEnclosingBackdrop`) stops a glass element from sampling a layer that contains itself.

### Performance choices
- **Month grid:** each month page is one `Canvas`. The 42 cells and 84 text layouts are cached in `drawWithCache`, and the selection highlight slides between cells in the draw phase only.
- **Life grid:** the ~4,000-week grid is a single static Canvas. The pulsing "you are here" marker has its own tiny layer, so the grid itself is never redrawn by the animation.
- **Animation reads:** scroll parallax, press scaling, entrance animations and bobbing are read in `graphicsLayer` or draw lambdas only, never during composition.
- **Precomputation:** lunar tables are built once per year and cached. A low-priority background thread warms the tables, holidays and planner at app start.
- **Data:** dates are packed `Int`s (`yyyymmdd`) throughout, which means no allocations and stable Compose inputs. A stability config marks the engine's data classes as stable.
- **Living sky:** the sky animates at 30 fps and stops when the user turns it off, or when the system's "Remove animations" setting is on.
- **Release build:** R8 shrinking and resource shrinking are on, plus a ProfileInstaller for baseline profiles.
- **One ambient clock:** every decorative loop (sky, moon glow, bobbing badge, lotus, hearts) reads one shared clock that ticks at ~30 fps and falls asleep after 25 s without a touch. `rememberInfiniteTransition` is not used for ambient motion: each one requests a frame on every vsync (up to 120 Hz) forever, and with a glass backdrop every frame re-renders and re-blurs the scene.
- **Strip-only content blur:** the shell's second backdrop (what the tab bar and sheets see through) blurs only the tab-bar band while no glass sheet is open, instead of the whole screen every frame.
- **Liquid lens only where it shows:** the AGSL refraction pass (one offscreen render per element per frame) is limited to the tab bar, hero cards and sheets; buttons and list cards use plain frost.
- **Entrances play once:** list items that scroll into view later appear instantly, and the fade uses `ModulateAlpha` (no offscreen buffer per card).
- **Off-main-thread audio:** songs are decoded and prepared on a background thread.
- **App baseline profile:** `app/src/main/baseline-prof.txt` AOT-compiles the app and engine packages at install.

---

## Credits & licences
- Fonts: **Moul** and **Kantumruy Pro**, SIL Open Font License (`app/src/main/assets/licenses/`)
- Lunar algorithm based on **momentkh** © 2018 ThyrithSor, MIT
- All ornaments, icons and the skyline are original vector drawings made in code
