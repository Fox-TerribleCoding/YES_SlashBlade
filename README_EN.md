# YES-SB — Yes Steve Model × SlashBlade: Resharped compatibility fix

English version (this file) · [简体中文 / Chinese](README.md)

> ## ⚠️ Temporary patch — retires as soon as upstream fixes it
>
> The YSM author has been contacted and replied: **"I'll do it later — back when I did it,
> there was no 1.21 SlashBlade yet."** They also agreed that this mod may **stay until the
> official fix lands**. The runtime injection has been **approved by the YSM author**;
> this jar contains **no third-party code or assets**.
>
> When upstream ships its own SlashBlade integration, the corresponding parts of this mod
> are declared obsolete and may be pulled from CurseForge.

A standalone **client-side** compatibility mod for **Minecraft 1.21.1 / NeoForge**.
It restores the SlashBlade behaviour that Yes Steve Model already had on 1.20.1
but lost on 1.21.1.

| # | Fix | Symptom on 1.21.1 |
|---|---|---|
| ① | Third-person waist blade / sheath | blade & sheath are **not rendered at all** |
| ② | SlashBlade combo (skill) animations | only the plain swing animation plays |
| ③ | SlashBlade variants of main animations | idle / walk look identical to a vanilla sword |
| ④ | Touhou Little Maid's blade | maid holds nothing; back slot draws a **flat icon** |

Design rules: no third-party file is modified, no third-party code or assets are bundled
or redistributed, and no reflection into someone else's private state.

MIT licensed — see [LICENSE](LICENSE); third-party attributions in [NOTICE](NOTICE).

---

## 1. Requirements

| Mod | Version | Note |
|---|---|---|
| [Yes Steve Model](https://modrinth.com/mod/yes-steve-model) | 2.6.x | the compensation only makes sense when it is present |
| [SlashBlade: Resharped](https://modrinth.com/mod/slashblade-resharped) | 2.x | rendering restoration calls its public `LayerMainBlade` |
| [Touhou Little Maid](https://modrinth.com/mod/touhou-little-maid) | 1.5.x | restores its maid blade render branches |

All three are **optional**: a missing mod just disables the corresponding part —
it never crashes. Nothing happens at all without YSM
(`[YES-SB] 未检测到 YSM：原版渲染本就正常，无需补偿。`).

## 2. Installation

Drop `YES_SB-1.0.1.jar` into `.minecraft/mods/`
(or `versions/<name>/mods/` when using version isolation).

## 3. Root causes (why the fix looks like this)

1. **The third-person blade is drawn by SlashBlade itself.** Its item model uses
   `parent: builtin/entity`, and `renderBlade()` returns immediately for *every*
   third-person context. The visible waist blade is a `LayerMainBlade` RenderLayer
   attached to the entity renderer.
2. **YSM 1.21.1 skips the vanilla renderer.** Inside `EntityRenderDispatcher#render`
   it wraps the `renderer.render(...)` call in a condition, so for entities it takes over,
   the vanilla renderer — and therefore the whole RenderLayer list — never runs.
3. **YSM's 2.6.5 SlashBlade module is an empty stub**: `isSlashBlade` is a constant `false`,
   the combo animation name is always `""`. The *call site* in YSM's animation resolver is
   intact, so that whole branch is dead code — that is exactly why combo animations never play.
4. **Touhou Little Maid dropped a package during the 1.21.1 port.** In its `1.20` and `1.21`
   branches `compat/slashblade` exists and `GeckoLayerMaidHeld.java` is byte-identical
   between the two, yet the published 1.21.1 jar contains **no reference to `slashblade` at all**.

## 4. How it is fixed

- **① Waist layer compensation ("check-in" method).** `EntityRenderDispatcher#render` HEAD
  records that a render started; `LivingEntityRenderer#render` HEAD marks it. On RETURN,
  if the vanilla renderer never ran, the mod recreates the vanilla transform environment
  and invokes SlashBlade's own `LayerMainBlade`. If the vanilla renderer *did* run
  (no YSM, or the entity is not taken over), not a single pixel is drawn — no double blades.
  This is also why the YSM paper-doll is fixed by the same hook.
- **②③ Value completion.** Only the missing return values are supplied, matching the rules of
  YSM's own open-source implementation (timeout window, `combo_a4_ex → combo_a4ex`
  normalisation, `standby` timeout `-553`, aerial judgement-cut variants). If the computed
  animation does not exist in the model pack, the mod returns **an empty string** instead,
  so YSM falls back to its normal swing logic instead of freezing the pose.
- **④ Maid branches restored verbatim** from TLM's own source, transform values unchanged.
- **Failure-soft everywhere.** All four targeted injections require the target mod to be
  loaded *and* a matching YSM version prefix (default `2.6.`); the mixin configs use
  `required=false` with `defaultRequire=0`. Any mismatch means "feature does nothing",
  **never a crash**.

## 5. Configuration

`config/yes_sb.properties` is generated on first launch.
**Every key hot-reloads — save the file and it applies in about 2 seconds, no restart.**

Most-used keys:

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | master switch; `false` is equivalent to uninstalling |
| `restoreThirdPerson` | `true` | restore the third-person waist blade |
| `thirdPersonScale` | `0.7` | compensation scale (matches the YSM model shrink) |
| `waistOffsetX/Y/Z` | `0` | extra offset for the waist layer (blocks, +Y = up, +Z = forward) |
| `handBladeScale` / `flatBladeScale` | `0.0062` | 3D blade scale in third-person hand / FIXED contexts |
| `firstPersonMode` | `auto` | `auto` / `off` / `model` / `icon` — see below |
| `slashbladeComboAnimations` | `true` | combo (skill) animation triggering |
| `slashbladeMainStateAnimations` | `true` | `slashblade:idle/walk/...` main-animation variants |
| `slashbladeAnimations` | `false` | item-classifier injection (`sword` → `slashblade`) — a behaviour change, off by default |
| `maidSlashBlade` | `true` | restore TLM's maid blade branches |
| `debugLog` | `false` | verbose diagnostics (including whether the model pack has a given animation) |

Scale reference: SlashBlade itself uses `0.003125` for a blade *lying on a rack or in a
display frame*, and `0.0095` for one *held in hand*. The default `0.0062` is the geometric
compromise (`0.0095 × 218 / 332.7`) for the contexts this mod draws in.

**First person is *not* taken over by default.** YSM never intercepted the first-person path;
1.20.1's look there is SlashBlade's own `BladeFirstPersonRender`. `auto` asks the vanilla
conditions first and only falls back when vanilla genuinely cannot draw it.

The full Chinese configuration reference — every key, one row each — is in
[README.md §五](README.md).

## 6. Known limitations

- **The player's blade does not track YSM's animation (unresolved).** The blade is positioned
  from the *vanilla* player model parts, while the character on screen is a *YSM* model playing
  *YSM* animations. Two animation systems, so they cannot agree. Fixing it properly requires
  binding the blade to YSM's own locator bones — deliberately **not done** (see below).
- **The maid's *attack logic* is not restored.** That is server-side AI behaviour, and
  it requires making this mod a both-sides mod, plus damage/knockback and combo-state
  considerations. It is **out of scope on purpose** — use a dedicated mod for it:
  [TLM: True POWER](https://modrinth.com/mod/true-power-of-maid).
  This mod only guarantees that the **trigger hook works**; when True POWER drives the maid
  into a combo state, the animations play.
- **Bone binding is not implemented.** YSM 2.6.5's 933 classes are obfuscated; a wrong
  injection crashes the game rather than silently doing nothing, and the current fixed-offset
  approach is sufficient. Two triggers would make it worth re-evaluating:
  (a) YSM shipping the unobfuscated `com.elfmcys.ysm.natives` interface, or
  (b) YSM implementing this integration itself — in which case this mod retires instead.
- **Version sensitivity.** All injected targets are third-party internals. After a YSM update
  the targets will most likely miss, and the affected feature silently disables itself.

## 7. Verified environment

| Component | File | Size | SHA256 (first 16) |
|---|---|---|---|
| YSM | `ysm-2.6.5-neoforge+mc1.21.1-release.jar` | 63,463,229 B | `B285C73D4EC010D9` |
| SlashBlade: Resharped | `SlashBladeResharped-2.0.7-1.21.1.jar` | 3,886,797 B | `C67653EC0D7E08A7` |
| Touhou Little Maid | `touhoulittlemaid-1.5.3-neoforge+mc1.21.1.jar` | 24,408,776 B | `F6DB04195820C850` |
| This mod | `YES_SB-1.0.1.jar` | 130,068 B | `56232F6BCD04CE32` |

NeoForge 21.1.x / Minecraft 1.21.1.
Verification method: the mod's own compiled classes were disassembled and every third-party
symbol was resolved against the target jars — **22/22 types and 34/34 members exist**, and the
combo-animation rules were checked line by line against YSM's open-source SlashBlade module.
Any failure report please include this table; it rules out version mismatches immediately.

## 8. Relationship with upstream

This is a **temporary patch**, not a replacement.

- **YSM**: the stub is not a deliberate removal — at the time (YSM 2.6.5), 1.21.1 had no
  SlashBlade release to adapt to. The author confirmed they **will implement it later**, and
  agreed this mod may stay until then. This mod's ②③ parts retire at that point.
- **Touhou Little Maid**: the two missing branches are a confirmed port omission
  (source present, published jar contains zero references). This mod simply puts them back;
  the maid part retires once upstream ships it.

Details, including the author's replies and the retirement commitments, are in
[README.md §七](README.md) (Chinese).

## 9. Building from source

No Gradle and no network required — only a local game directory containing Minecraft with
official mappings, NeoForge, Sponge Mixin, and the three mods in `mods/`:

```powershell
.\build.ps1 -Clean                          # defaults to the version directory T
.\build.ps1 -Clean -GameVersion X           # another version directory
.\build.ps1 -Clean -Install                 # also install into mods\
.\build.ps1 -Clean -Install -GameVersion T
```

Two notes for contributors:

1. **`build.ps1` must stay pure ASCII** — Windows PowerShell 5.1 reads it as ANSI and
   Chinese comments break parsing.
2. **Installation is an atomic replace** (write temp file, then `Move-Item`), and it refuses
   to install while the game is running. This is deliberate: `Copy-Item` truncates the target
   first, and a game starting at that moment would read a truncated jar
   (`NoClassDefFoundError`). Please do not change it back.

## 10. License

MIT — free to use, modify and redistribute with attribution.
See [NOTICE](NOTICE) for third-party attributions.
