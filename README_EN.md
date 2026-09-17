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
| ④ | Touhou Little Maid's blade | maid holds nothing; back slot draws a **flat icon**; attacking shows **no slash arc** and the blade never leaves its sheath |

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

> 🔴 **Important (fixed in 1.0.14): versions 1.0.5 – 1.0.13 crash on any left-click swing in a
> modpack WITHOUT Touhou Little Maid** (e.g. ATM10). The maid swing hook is attached to vanilla's
> swing, and it referenced a Touhou Little Maid class — when that class is absent the game fails at
> **class-loading** time, which `try/catch` cannot catch. **Update to the latest version** (fixed in 1.0.14); just swap the jar,
> no config change needed.

Drop `YES_SB-1.0.16.jar` into `.minecraft/mods/`
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
   Three things were lost: the maid's hand render branch, the back-slot render branch, and the
   `swing()` override that gives the maid a **slash arc and a drawn blade** when she attacks.
   SlashBlade's "drawn" pose is only 5 ticks of constant-speed spin written as a **binary** switch
   (full transform inside the window, gone the instant it ends), so it used to pop in and out.
   Since 1.0.16 the mod eases **both ends** (default 1 tick = 50 ms) by slerping from the sheathed
   pose to the drawn pose — **the middle of the window is bit-identical to upstream**, only the two
   ends are rounded off. Option `maidBladeEaseTicks` (hot-reloaded, default `1.0`; `0` restores
   upstream exactly). This is a deliberate **polish this mod adds**, and it only removes the pop:
   the blade still performs upstream's constant-speed spin — a full sword-skill animation set for
   the maid is out of scope.

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
- **④ Maid swing restored too.** Upstream hooked `EntityMaid#swing`; that override does not
  exist in the published 1.21.1 jar, so this mod hooks vanilla
  `LivingEntity#swing(InteractionHand)` instead and checks "is this a maid holding a blade on
  the attack task". `swing()` is the one method that runs on **both** sides, which is exactly
  why upstream used it — the two side effects belong to different sides:

  | Effect | Side that matters | Mechanism |
  |---|---|---|
  | Slash arc | **server** | spawns the effect entity; vanilla entity tracking syncs it to clients |
  | Blade drawn from sheath | **client** | writes `lastActionTime` on the client's own ItemStack |

  Note that SlashBlade's `AttackManager.doSlash(...)` returns `null` immediately on the
  client (true for both 1.9.65 and 2.0.7), so the arc can only come from the server. It is
  a **yield-guarded** injection: if upstream ever adds the override back, this one disables
  itself rather than firing twice.
- **Failure-soft everywhere.** All targeted injections require the target mod to be
  loaded *and* a matching YSM version prefix (default `2.6.`); the mixin configs use
  `required=false` with `defaultRequire=0`. Any mismatch means "feature does nothing",
  **never a crash**.

## 5. Configuration

`config/yes_sb.properties` is generated on first launch.
**Almost every key hot-reloads — save the file and it applies in about 2 seconds, no restart. The one exception is `firstPersonIrisHack` (see §6): it needs a game restart.**

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
| `maidSlashBlade` | `true` | restore TLM's maid blade render branches |
| `maidSlashBladeAttack` | `true` | restore the maid's swing (server-side arc + client-side sheath timestamp); separate from the row above so the two paths can be told apart |
| `debugLog` | `false` | **troubleshooting switch** (zero cost while off). When reporting an issue, turn it on once and attach every `[YES-SB]` line — this mod mostly restores things whose absence is invisible, so those lines are usually the only evidence available |

Scale reference: SlashBlade itself uses `0.003125` for a blade *lying on a rack or in a
display frame*, and `0.0095` for one *held in hand*. The default `0.0062` is the geometric
compromise (`0.0095 × 218 / 332.7`) for the contexts this mod draws in.

**First person is *not* taken over by default.** YSM never intercepted the first-person path;
1.20.1's look there is SlashBlade's own `BladeFirstPersonRender`. `auto` asks the vanilla
conditions first and only falls back when vanilla genuinely cannot draw it.

The full Chinese configuration reference — every key, one row each — is in
[README.md §五](README.md).

## 6. Known limitations

- **First-person blade with shaders — the other half (fixed in 1.0.15).** With shaders on, the blade
  used to be **locked horizontally** (turning the view left/right did not move it) while it **moved
  vertically in the opposite direction** as you pitched. Cause: SlashBlade ships an "Iris
  approximation" that applies two extra rotations to *approximate* cancelling the matrix Iris injects
  into `ModelViewMat` — `rotY(yaw+180)` cancels the later `rotY(180−yaw)` exactly (a full 360°, so
  **yaw is wiped out**), while `rotX(+xRot)` does **not** cancel the later `rotX(−clamp)` (an extra
  pitch). Since 1.0.12 this mod handles `ModelViewMat` exactly, so that approximation became a plain
  error. It is now disabled by **hiding "iris is loaded" from that one SlashBlade class in its
  constructor** — the target is NeoForge's own `ModList`, so it is decoupled from the shape of that
  branch, and **no Iris / shaderpack / third-party file is touched**. Option `firstPersonIrisHack`
  (default `auto`); ⚠️ **this one needs a game restart** — see §5.
- **View bobbing for the first-person blade is an enhancement, not a restoration (added in 1.0.13).**
  Vanilla's first-person hand sways with your footsteps, but SlashBlade's first-person renderer clears
  the pose stack — the sway lives *inside* the matrix it wipes, so the hand moved and the blade did not.
  This mod now applies the same sway to the blade by **calling vanilla's own `GameRenderer#bobView`**
  (via an `@Invoker` mixin) rather than re-implementing the maths, so it is **pixel-identical** to the
  hand and follows Minecraft automatically if the algorithm changes. It is gated on the game's built-in
  **View Bobbing** option, and sits right after the pose basis
  (`ModelViewMat⁻¹ × camera rotation × sway`), so the whole blade sways rather than rotating about its own
  origin, and the result is identical with and without shaders.
  Option `firstPersonBladeBob` (default `true`, hot-reloaded).
  **1.20.1 does not have this effect** (it clears the pose stack too) — set it to `false` if you want the
  reference behaviour. The invoker lives in its own mixin class *and* its own mixin config
  (`yessb.bob.mixins.json`, soft-fail), so if it ever stops matching, only this feature is lost.
- **First-person blade with shaders (fixed in 1.0.12).** With *any* shader pack enabled,
  the first-person blade used to land in the wrong place and drift as you walked.
  The cause is the pose *basis*: a vertex ends up as `ModelViewMat × poseStack`, and without
  shaders `ModelViewMat` equals the camera rotation while the pose stack starts at its inverse.
  SlashBlade's first-person renderer clears the pose stack (`pose().identity()`), which leaves
  exactly that camera rotation — and that is what makes the blade follow your view.
  Iris replaces `ModelViewMat` with its own view-bob matrix, so clearing the pose leaves the
  *bobbing* instead. The fix uses `ModelViewMat⁻¹ × camera rotation` as the basis, which is the
  identity when no shader is active — so the unshaded behaviour is bit-identical.
  It does **not** detect shader packs, so it holds for any of them.
  (Aside: the "sway" seen with shaders *was* the bug — the hand's view-bob leaking onto the blade.)
- **The player's blade does not track YSM's animation (unresolved).** The blade is positioned
  from the *vanilla* player model parts, while the character on screen is a *YSM* model playing
  *YSM* animations. Two animation systems, so they cannot agree. Fixing it properly requires
  binding the blade to YSM's own locator bones — deliberately **not done** (see below).
- **The maid's slash arc and drawn blade are restored (1.0.5); her *combo state* is not.**
  The arc and the sheath animation were a port omission and are now back (see §4).
  Getting the maid into a SlashBlade **combo state** (so she plays blade skills rather than a
  plain swing) is still out of scope — use a dedicated mod for it:
  [TLM: True POWER](https://modrinth.com/mod/true-power-of-maid).
  This mod only guarantees that the **trigger hook works**; when True POWER drives the maid
  into a combo state, the animations play.
- **On a dedicated server without this mod installed**, a maid will draw her blade but show
  **no slash arc** — the arc is spawned server-side. In single-player the integrated server
  shares the same JVM and classes, so both halves work.
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
| This mod | `YES_SB-1.0.16.jar` | 150,718 B | `194834F0A969D2B4` |

> This mod's jar entries carry **build timestamps**, so its hash changes on every rebuild
> even with identical sources — it identifies one specific build, not a constant.
> The three third-party jars above are the stable reference. When reporting an issue,
> please include this table; it rules out version mismatches immediately.

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
- **Touhou Little Maid**: the three missing pieces are a confirmed port omission
  (source present, published jar contains zero references, and the `swing` override is simply
  absent). This mod simply puts them back;
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
