# ============================================================================
#  YES-SB icon generator
#
#  NOTE: this file MUST stay pure ASCII.
#  Windows PowerShell 5.1 reads .ps1 files as ANSI, so any non-ASCII byte
#  (Chinese comment, CJK punctuation, smart quote) breaks parsing.
#  Same rule as build.ps1 in the project root.
#
#  Output: ..\icon\yes_sb_icon_{1024,512,256,128}.png
#  Pure GDI+, no external assets, no network, no third-party artwork.
#
#  GDI+ transform semantics used below (verified empirically, they are NOT
#  obvious):
#    TranslateTransform(T) then RotateTransform(a)  ->  screen = R(a)*p + T
#    so "rotate about the origin, then translate" is the natural reading.
#    Graphics.TransformPoints('Device','World', p) is the FORWARD mapping;
#    passing ('World','Device') returns the inverse.
# ============================================================================

Add-Type -AssemblyName System.Drawing

$OutDir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\icon'))
if (-not (Test-Path -LiteralPath $OutDir)) { New-Item -ItemType Directory -Path $OutDir | Out-Null }

$S = 1024
$bmp = New-Object System.Drawing.Bitmap($S, $S, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)

# ---------------------------------------------------------------- palette ---
$cBgA     = [System.Drawing.Color]::FromArgb(255, 27, 36, 68)
$cBgB     = [System.Drawing.Color]::FromArgb(255, 14, 20, 40)
$cBgC     = [System.Drawing.Color]::FromArgb(255, 5, 7, 14)
$cGlow    = [System.Drawing.Color]::FromArgb(255, 46, 198, 214)
$cGoldLo  = [System.Drawing.Color]::FromArgb(255, 150, 104, 24)
$cGoldHi  = [System.Drawing.Color]::FromArgb(255, 255, 214, 108)
$cSteelLo = [System.Drawing.Color]::FromArgb(255, 150, 168, 190)
$cSteelMd = [System.Drawing.Color]::FromArgb(255, 226, 234, 245)
$cSteelHi = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
$cDark    = [System.Drawing.Color]::FromArgb(255, 48, 62, 96)
$cDark2   = [System.Drawing.Color]::FromArgb(255, 70, 90, 134)

# ---------------------------------------------------------------- helpers ---
function RRect([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

function VGrad([System.Drawing.Rectangle]$rect, [System.Drawing.Color]$c1, [System.Drawing.Color]$c2, [System.Drawing.Color]$c3, [float]$angle, [float]$mid = 0.55) {
    $b = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $c1, $c2, $angle)
    $blend = New-Object System.Drawing.Drawing2D.ColorBlend(3)
    $blend.Colors    = @($c1, $c2, $c3)
    $blend.Positions = @([float]0, [float]$mid, [float]1)
    $b.InterpolationColors = $blend
    return $b
}

function Solid([int]$a, [System.Drawing.Color]$c) {
    return (New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb($a, $c.R, $c.G, $c.B)))
}

# ============================================================== 1. geometry ==
# The whole sword is built on ONE curved centreline: blade, guard and handle all
# reference it, so the tsuka continues the blade's sori instead of sitting in a
# separate vertical frame (which left a ~10 deg kink at the guard).
#
#   BladeX(t) = 2(1-t)t*BOW      bowed LEFT -> mune is the convex side
#   BladeY(t) = Y0 - (Y0-Y1)*t   linear, because the control point sits at the
#                                Y midpoint and cancels the quadratic term.
$BL_Y0  = -34.0     # blade root (just above the habaki)
$BL_Y1  = -600.0    # blade tip          -> blade length 566
$BL_HW  = 23.0      # blade half width   -> long:wide ~ 12:1
$BL_BOW = -52.0     # centreline control offset -> sori
$KIS    = 0.86      # t at which the kissaki begins

# Feature positions, given as Y along the centreline.
$Y_HANDLE_TOP = 24.0     # top of the tsuka (under the tsuba)
$Y_HANDLE_END = 229.0    # bottom of the tsuka          -> tsuka chord ~213
$Y_KASHIRA    = 255.0    # centre of the pommel
$Y_TSUBA      = 16.8     # centre of the guard
$Y_HABAKI     = -18.6    # centre of the collar
$RING_R       = 160.0    # radius of the "restore" ring

function BladeX([double]$t) { return 2.0 * (1.0 - $t) * $t * $BL_BOW }
function BladeY([double]$t) { return $BL_Y0 + ($BL_Y0 - $BL_Y1) * $t * -1.0 }
function BladeDX([double]$t) { return 2.0 * $BL_BOW * (1.0 - 2.0 * $t) }
function BladeDY([double]$t) { return ($BL_Y1 - $BL_Y0) }
function BladeAngle([double]$t) {
    return [math]::Atan2((BladeDX $t), -(BladeDY $t)) * 180.0 / [math]::PI
}
function BladeT([double]$y) { return ($BL_Y0 - $y) / ($BL_Y0 - $BL_Y1) }
# $sign -1 = mune (back), +1 = ha (edge). The exponent decides how early that
# side converges, which is what makes the kissaki asymmetric.
function BladeOff([double]$t, [double]$sign) {
    if ($t -le $KIS) { return $sign * $BL_HW }
    $s = ($t - $KIS) / (1.0 - $KIS)
    $e = 0.6
    if ($sign -gt 0) { $e = 1.8 }
    return $sign * $BL_HW * (1.0 - [math]::Pow($s, $e))
}

$tH0 = BladeT $Y_HANDLE_TOP
$tH1 = BladeT $Y_HANDLE_END
$tK  = BladeT $Y_KASHIRA
$tTsu = BladeT $Y_TSUBA
$tHab = BladeT $Y_HABAKI
$hMid = ($tH0 + $tH1) / 2.0

# Half length along the blade axis: the CHORD between the two ends, not the Y
# difference. The centreline is curved, so the Y span is shorter than the chord
# and using it leaves the handle visibly short of the kashira.
$hHalf = 0.5 * [math]::Sqrt([math]::Pow((BladeY $tH1) - (BladeY $tH0), 2.0) + [math]::Pow((BladeX $tH1) - (BladeX $tH0), 2.0))
$bladeLen  = $BL_Y0 - $BL_Y1
$handleLen = $hHalf * 2.0

# ========================================================== 2. auto framing ==
# Collect the extremes of everything about to be drawn, rotate them by the fixed
# 45 deg, and derive the scale AND the centring offset from that bounding box.
# Hand-computing these offsets silently went stale every time a dimension
# changed, so they are derived now.
$rotDeg = 45.0
$rotRad = $rotDeg * [math]::PI / 180.0
$rc = [math]::Cos($rotRad)
$rs = [math]::Sin($rotRad)

$ext = New-Object 'System.Collections.Generic.List[System.Drawing.PointF]'
function AddExt([double]$x, [double]$y) {
    $ext.Add((New-Object System.Drawing.PointF([float]($rc * $x - $rs * $y), [float]($rs * $x + $rc * $y))))
}

$NS = 60
for ($i = 0; $i -le $NS; $i++) {
    $t = $i / $NS
    AddExt ((BladeX $t) - $BL_HW) (BladeY $t)
    AddExt ((BladeX $t) + $BL_HW) (BladeY $t)
}
foreach ($pair in @(@($tH0, 26.0), @($tH1, 23.0))) {
    AddExt ((BladeX $pair[0]) - $pair[1]) (BladeY $pair[0])
    AddExt ((BladeX $pair[0]) + $pair[1]) (BladeY $pair[0])
}
AddExt ((BladeX $tK) - 32.0) ((BladeY $tK) - 32.0); AddExt ((BladeX $tK) + 32.0) ((BladeY $tK) + 32.0)
AddExt ((BladeX $tTsu) - 70.0) ((BladeY $tTsu) - 26.0); AddExt ((BladeX $tTsu) + 70.0) ((BladeY $tTsu) + 26.0)
AddExt ((BladeX $tHab) - 34.0) ((BladeY $tHab) - 26.0); AddExt ((BladeX $tHab) + 34.0) ((BladeY $tHab) + 26.0)
$ringCx = BladeX $tTsu
$ringCy = BladeY $tTsu
AddExt ($ringCx - $RING_R) ($ringCy - $RING_R); AddExt ($ringCx + $RING_R) ($ringCy + $RING_R)

$bx0 = 1e9; $by0 = 1e9; $bx1 = -1e9; $by1 = -1e9
foreach ($q in $ext) {
    if ($q.X -lt $bx0) { $bx0 = $q.X }
    if ($q.X -gt $bx1) { $bx1 = $q.X }
    if ($q.Y -lt $by0) { $by0 = $q.Y }
    if ($q.Y -gt $by1) { $by1 = $q.Y }
}
$bw = $bx1 - $bx0
$bh = $by1 - $by0
$FILL = 0.84
$sc  = [math]::Min(($S * $FILL) / $bw, ($S * $FILL) / $bh)
$bcx = ($bx0 + $bx1) / 2.0
$bcy = ($by0 + $by1) / 2.0

# screen = sc * R45(p) + T  (Translate, then Rotate, then Scale)
$g.TranslateTransform([float]($S / 2.0 - $sc * $bcx), [float]($S / 2.0 - $sc * $bcy))
$g.RotateTransform([float]$rotDeg)
$g.ScaleTransform([float]$sc, [float]$sc)

Write-Host ("geometry: blade {0:N0} / handle {1:N0}  ratio {2:N2}:1  scale {3:N3}" -f $bladeLen, $handleLen, ($bladeLen / $handleLen), $sc)

# =========================================================== 3. background ===
# Drawn with the transform reset, so background art is independent of framing.
$bgState = $g.Save()
$g.ResetTransform()

$bgPath  = RRect 0 0 $S $S 200
$bgBrush = VGrad (New-Object System.Drawing.Rectangle(0, 0, $S, $S)) $cBgA $cBgB $cBgC ([float]55)
$g.FillPath($bgBrush, $bgPath)

# Subtle corner glow. Radial gradient is faked with stacked ellipses because
# PathGradientBrush demands SurroundColors.Length == path PointCount.
$glowCx = 742.0; $glowCy = 268.0; $glowR = 400.0; $glowN = 80
for ($i = 0; $i -lt $glowN; $i++) {
    $frac = $i / ($glowN - 1)
    $rr = $glowR * (1.0 - $frac)
    if ($rr -lt 1) { continue }
    $al = [int](1.6 * (1.0 - $frac) + 0.8)
    $br = Solid $al $cGlow
    $g.FillEllipse($br, [float]($glowCx - $rr), [float]($glowCy - $rr), [float]($rr * 2), [float]($rr * 2))
    $br.Dispose()
}

# faint pixel grid (Minecraft cue), clipped to the rounded background
$gridState = $g.Save()
$g.SetClip($bgPath)
$gridPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(9, 255, 255, 255), [float]2)
for ($x = 0; $x -le $S; $x += 64) { $g.DrawLine($gridPen, [float]$x, [float]0, [float]$x, [float]$S) }
for ($y = 0; $y -le $S; $y += 64) { $g.DrawLine($gridPen, [float]0, [float]$y, [float]$S, [float]$y) }
$g.Restore($gridState)
$gridPen.Dispose()

# inner rim light
$rimPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(30, 160, 210, 255), [float]3)
$g.DrawPath($rimPen, (RRect 6 6 ($S - 12) ($S - 12) 196))
$rimPen.Dispose()
$g.Restore($bgState)

# ======================================================== 4. restore ring ====
# Open cyan ring around the guard: reads as "put back / repair". The gap is
# aligned with the handle so the handle exits through it.
$ringPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(140, 92, 236, 226), [float]14)
$ringPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$ringPen.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawArc($ringPen, [float]($ringCx - $RING_R), [float]($ringCy - $RING_R), [float]($RING_R * 2), [float]($RING_R * 2), [float]104, [float]316)
$ringPen.Dispose()

# ============================================================ 5. blade =======
# Generated by walking the centreline with a fixed perpendicular offset, so the
# two sides stay parallel. Hand-placed beziers previously pushed the mune and
# the ha the WRONG WAY apart and bulged the blade into a leaf shape.
$NP = 120
$bladePts = New-Object 'System.Collections.Generic.List[System.Drawing.PointF]'
for ($i = 0; $i -le $NP; $i++) {
    $t = $i / $NP
    $bladePts.Add((New-Object System.Drawing.PointF([float]((BladeX $t) + (BladeOff $t -1.0)), [float](BladeY $t))))
}
for ($i = $NP; $i -ge 0; $i--) {
    $t = $i / $NP
    $bladePts.Add((New-Object System.Drawing.PointF([float]((BladeX $t) + (BladeOff $t 1.0)), [float](BladeY $t))))
}
$blade = New-Object System.Drawing.Drawing2D.GraphicsPath
$blade.AddPolygon($bladePts.ToArray())

# soft cyan halo behind the blade (concentric wide low-alpha strokes)
for ($w = 150; $w -ge 40; $w -= 10) {
    $hp = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(5, $cGlow.R, $cGlow.G, $cGlow.B), [float]$w)
    $hp.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $g.DrawPath($hp, $blade)
    $hp.Dispose()
}

# drop shadow: local (+k,+k) maps to straight down on screen after the 45 deg turn
$shBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(55, 0, 0, 0))
$m1 = New-Object System.Drawing.Drawing2D.Matrix; $m1.Translate([float]16, [float]16)
$p1 = $blade.Clone(); $p1.Transform($m1); $g.FillPath($shBrush, $p1)
$shBrush.Dispose()
$shBrush2 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(70, 0, 0, 0))
$m2 = New-Object System.Drawing.Drawing2D.Matrix; $m2.Translate([float]8, [float]8)
$p2 = $blade.Clone(); $p2.Transform($m2); $g.FillPath($shBrush2, $p2)
$shBrush2.Dispose()
$p1.Dispose(); $p2.Dispose(); $m1.Dispose(); $m2.Dispose()

# mid stop pulled left on purpose: the darker half only covers the mune side, so
# the blade reads as single-edged instead of as a symmetrical double-edged one
$bladeBrush = VGrad (New-Object System.Drawing.Rectangle(-45, -600, 72, 566)) $cSteelLo $cSteelMd $cSteelHi ([float]0) ([float]0.30)
$g.FillPath($bladeBrush, $blade)

# helper: an offset line running along the blade, used for shinogi and hamon
function BladeLine([double]$off, [double]$t0, [double]$t1, [int]$n) {
    $pts = New-Object 'System.Collections.Generic.List[System.Drawing.PointF]'
    for ($i = 0; $i -le $n; $i++) {
        $t = $t0 + ($t1 - $t0) * ($i / $n)
        $pts.Add((New-Object System.Drawing.PointF([float]((BladeX $t) + $off), [float](BladeY $t))))
    }
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddLines($pts.ToArray())
    return $p
}

# shinogi ridge: the flat-to-ridge line, close to the mune
$ridgePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(55, 40, 58, 84), [float]4)
$ridge = BladeLine -9.0 0.0 0.95 50
$g.DrawPath($ridgePen, $ridge)
$ridge.Dispose(); $ridgePen.Dispose()

# hamon: bright line along the cutting edge
$hamonPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(220, 255, 255, 255), [float]7)
$hamonPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$hamonPen.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
$hamon = BladeLine 14.0 0.0 0.93 50
$g.DrawPath($hamonPen, $hamon)
$hamon.Dispose(); $hamonPen.Dispose()

# outline for definition against the dark background
$outlinePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(130, 10, 15, 26), [float]4)
$g.DrawPath($outlinePen, $blade)
$outlinePen.Dispose()

# =========================================================== 6. tsuka ========
$hState = $g.Save()
$g.TranslateTransform([float](BladeX $hMid), [float](BladeY $hMid))
$g.RotateTransform([float](BladeAngle $hMid))

$handle = New-Object System.Drawing.Drawing2D.GraphicsPath
$handle.AddLine([float]-26, [float](-$hHalf), [float]26,  [float](-$hHalf))
$handle.AddLine([float]26,  [float](-$hHalf), [float]23,  [float]$hHalf)
$handle.AddLine([float]23,  [float]$hHalf,    [float]-23, [float]$hHalf)
$handle.AddLine([float]-23, [float]$hHalf,    [float]-26, [float](-$hHalf))
$handle.CloseFigure()
$g.FillPath((VGrad (New-Object System.Drawing.Rectangle(-26, [int](-$hHalf), 52, [int]($hHalf * 2.0))) $cDark2 $cDark $cDark2 ([float]0)), $handle)

# tsuka-ito: alternating wrap diamonds
$wrapPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(170, 118, 150, 205), [float]5)
$dTop = -$hHalf + 26
$dBot = $hHalf - 16
for ($yy = $dTop; $yy -le $dBot; $yy += 34) {
    $dm = New-Object System.Drawing.Drawing2D.GraphicsPath
    $dm.AddLine([float]-26, [float]$yy,          [float]0,  [float]($yy + 17))
    $dm.AddLine([float]0,   [float]($yy + 17),   [float]26, [float]$yy)
    $dm.AddLine([float]26,  [float]$yy,          [float]0,  [float]($yy - 17))
    $dm.AddLine([float]0,   [float]($yy - 17),   [float]-26, [float]$yy)
    $dm.CloseFigure()
    $g.DrawPath($wrapPen, $dm)
    $dm.Dispose()
}
$wrapPen.Dispose()
$g.DrawPath((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(140, 10, 14, 24), [float]4)), $handle)
$handle.Dispose()
$g.Restore($hState)

# ---- kashira (pommel) ------------------------------------------------------
# NOTE: must be its own save/restore frame. Nesting a second RotateTransform
# inside the handle frame prepends it, so the extra rotation happens about the
# local origin instead of about the handle position and flings the pommel
# sideways -- exactly the detached-pommel bug this replaced.
$kState = $g.Save()
$g.TranslateTransform([float](BladeX $tK), [float](BladeY $tK))
$g.RotateTransform([float](BladeAngle $tK))
$kashira = RRect -26 -17 52 34 11
$g.FillPath((VGrad (New-Object System.Drawing.Rectangle(-26, -17, 52, 34)) $cGoldLo $cGoldHi $cGoldLo ([float]0)), $kashira)
$g.DrawPath((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(150, 60, 40, 8), [float]3)), $kashira)
$kashira.Dispose()
$g.Restore($kState)

# ---- guard blocks, squared to the blade axis -------------------------------
# local (0,0) is placed on the centreline and the frame is rotated by the local
# tangent, so these stay perpendicular to the blade as the blade curves.
$guardState = $g.Save()
$g.TranslateTransform([float](BladeX $tTsu), [float](BladeY $tTsu))
$g.RotateTransform([float](BladeAngle $tTsu))
$tsuba = RRect -66 -14 132 28 12
$g.FillPath((VGrad (New-Object System.Drawing.Rectangle(-66, -14, 132, 28)) $cGoldLo $cGoldHi $cGoldLo ([float]0)), $tsuba)
$g.DrawPath((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(150, 60, 40, 8), [float]4)), $tsuba)
$tsuba.Dispose()
$g.Restore($guardState)

$habState = $g.Save()
$g.TranslateTransform([float](BladeX $tHab), [float](BladeY $tHab))
$g.RotateTransform([float](BladeAngle $tHab))
$habaki = RRect -30 -19 60 38 8
$g.FillPath((VGrad (New-Object System.Drawing.Rectangle(-30, -19, 60, 38)) $cGoldLo $cGoldHi $cGoldLo ([float]0)), $habaki)
$g.DrawPath((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(140, 60, 40, 8), [float]3)), $habaki)
$habaki.Dispose()
$g.Restore($habState)

$blade.Dispose()

# ========================================================== 7. sparkles ======
# Screen space, but placed relative to the auto-derived bounding box so they
# follow the artwork when the framing changes.
$spState = $g.Save()
$g.ResetTransform()

function Star([float]$cx, [float]$cy, [float]$r, [System.Drawing.Color]$col) {
    $k = $r * 0.2
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddPolygon(@(
        (New-Object System.Drawing.PointF([float]$cx,        [float]($cy - $r))),
        (New-Object System.Drawing.PointF([float]($cx + $k), [float]($cy - $k))),
        (New-Object System.Drawing.PointF([float]($cx + $r), [float]$cy)),
        (New-Object System.Drawing.PointF([float]($cx + $k), [float]($cy + $k))),
        (New-Object System.Drawing.PointF([float]$cx,        [float]($cy + $r))),
        (New-Object System.Drawing.PointF([float]($cx - $k), [float]($cy + $k))),
        (New-Object System.Drawing.PointF([float]($cx - $r), [float]$cy)),
        (New-Object System.Drawing.PointF([float]($cx - $k), [float]($cy - $k)))
    ))
    $g.FillPath((New-Object System.Drawing.SolidBrush($col)), $p)
    $p.Dispose()
}

$sx0 = [float]($S / 2.0 - $sc * $bw / 2.0)
$sx1 = [float]($S / 2.0 + $sc * $bw / 2.0)
$sy0 = [float]($S / 2.0 - $sc * $bh / 2.0)
$sy1 = [float]($S / 2.0 + $sc * $bh / 2.0)
$sxw = $sx1 - $sx0
$syh = $sy1 - $sy0

Star ($sx0 + 0.19 * $sxw) ($sy0 + 0.17 * $syh) 46 ([System.Drawing.Color]::FromArgb(240, 255, 255, 255))
Star ($sx0 + 0.10 * $sxw) ($sy0 + 0.31 * $syh) 19 ([System.Drawing.Color]::FromArgb(170, 186, 246, 255))
Star ($sx0 + 0.86 * $sxw) ($sy0 + 0.87 * $syh) 21 ([System.Drawing.Color]::FromArgb(150, 186, 246, 255))

$g.Restore($spState)

# ============================================================= 8. output =====
$targets = @(
    @{ size = 1024; name = 'yes_sb_icon_1024.png' },
    @{ size = 512;  name = 'yes_sb_icon_512.png'  },
    @{ size = 256;  name = 'yes_sb_icon_256.png'  },
    @{ size = 128;  name = 'yes_sb_icon_128.png'  }
)

foreach ($t in $targets) {
    $n = [int]$t.size
    if ($n -eq $S) {
        $out = $bmp
    } else {
        $out = New-Object System.Drawing.Bitmap($n, $n, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $og = [System.Drawing.Graphics]::FromImage($out)
        $og.SmoothingMode     = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $og.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $og.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $og.Clear([System.Drawing.Color]::Transparent)
        $og.DrawImage($bmp, 0, 0, $n, $n)
        $og.Dispose()
    }
    $path = Join-Path $OutDir $t.name
    $out.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    if ($n -ne $S) { $out.Dispose() }
    Write-Host ("wrote {0}  ({1} bytes)" -f $path, (Get-Item -LiteralPath $path).Length)
}

$g.Dispose()
$bmp.Dispose()
Write-Host "done."
