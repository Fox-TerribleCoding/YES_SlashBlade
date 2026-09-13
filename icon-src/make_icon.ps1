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

# =========================================================== 1. background ===
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

# ====================================================== 2. katana (local) ====
# Local space: blade points UP (-Y), handle DOWN (+Y).
#   y = -480  blade tip          y =  -34  blade root
#   y =  +24  tsuba              y = +338  kashira (pommel)
# rotate 45 deg clockwise -> blade points up-right.
# offset compensates for the shape centre, which is NOT local (0,0): the whole
# sword is built on a curved centreline, so it sweeps right at the handle end.
$g.TranslateTransform([float]430.9, [float]579.2)
$g.RotateTransform([float]45)

# ---- restore ring ----------------------------------------------------------
# Open cyan ring around the guard: reads as "put back / repair".
# Drawn behind the katana; the gap is aligned with the handle so the handle
# passes out through it instead of crossing the arc.
$cRing = 9.8; $cRingY = 16.8; $rRing = 160.0
$ringPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(140, 92, 236, 226), [float]14)
$ringPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$ringPen.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
$g.DrawArc($ringPen, [float]($cRing - $rRing), [float]($cRingY - $rRing), [float]($rRing * 2), [float]($rRing * 2), [float]104, [float]316)
$ringPen.Dispose()

# ---- blade silhouette ------------------------------------------------------
# Katana profile, generated by walking a curved centreline so the two sides stay
# a fixed distance apart (hand-placed beziers previously bulged into a leaf).
#
#   sori   : the centreline is a quadratic bezier bowed LEFT. The mune (back) is
#            therefore the convex side and the ha (edge) the concave one, which
#            is the correct katana geometry for "tip up, edge right".
#   width  : constant half-width, so the blade stays slender (long:wide ~ 11:1).
#            A dagger look comes from a stubby blade far more than from the tip.
#   kissaki: the mune starts converging at $KIS while the ha stays wide and only
#            then collapses, so the point ends up biased toward the back.
$BL_Y0  = -34.0     # blade root (just above the habaki)
$BL_Y1  = -530.0    # blade tip
$BL_HW  = 23.0      # half width
$BL_BOW = -46.0     # centreline control offset -> sori
$KIS    = 0.86      # t at which the kissaki begins

function BladeX([double]$t) {
    $mt = 1.0 - $t
    return 2.0 * $mt * $t * $BL_BOW
}
function BladeY([double]$t) {
    $mt = 1.0 - $t
    $ym = ($BL_Y0 + $BL_Y1) / 2.0
    return $mt * $mt * $BL_Y0 + 2.0 * $mt * $t * $ym + $t * $t * $BL_Y1
}
# dX/dt and dY/dt of the centreline. dY/dt is constant because the control point
# sits exactly at the midpoint of Y0..Y1, which cancels the quadratic term.
function BladeDX([double]$t) { return 2.0 * $BL_BOW * (1.0 - 2.0 * $t) }
function BladeDY([double]$t) { return ($BL_Y1 - $BL_Y0) }
# Angle of the blade axis away from straight-up, in degrees, clockwise positive
# (GDI+ convention). Used to tilt the guard / collar / handle blocks so they stay
# square to the blade instead of sitting in a fixed vertical frame.
function BladeAngle([double]$t) {
    return [math]::Atan2((BladeDX $t), -(BladeDY $t)) * 180.0 / [math]::PI
}
# t for a given y along the centreline (the relation is linear, see BladeDY)
function BladeT([double]$y) { return ($BL_Y0 - $y) / ($BL_Y1 - $BL_Y0) }
# $sign -1 = mune, +1 = ha. Exponent chooses how early that side converges.
function BladeOff([double]$t, [double]$sign) {
    if ($t -le $KIS) { return $sign * $BL_HW }
    $s = ($t - $KIS) / (1.0 - $KIS)
    $e = 0.6
    if ($sign -gt 0) { $e = 1.8 }
    return $sign * $BL_HW * (1.0 - [math]::Pow($s, $e))
}

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
$bladeBrush = VGrad (New-Object System.Drawing.Rectangle(-45, -530, 72, 496)) $cSteelLo $cSteelMd $cSteelHi ([float]0) ([float]0.30)
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
$ridge = BladeLine -9.0 0.0 0.95 40
$g.DrawPath($ridgePen, $ridge)
$ridge.Dispose(); $ridgePen.Dispose()

# hamon: bright line along the cutting edge
$hamonPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(220, 255, 255, 255), [float]7)
$hamonPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$hamonPen.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
$hamon = BladeLine 14.0 0.0 0.93 40
$g.DrawPath($hamonPen, $hamon)
$hamon.Dispose(); $hamonPen.Dispose()

# outline for definition against the dark background
$outlinePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(130, 10, 15, 26), [float]4)
$g.DrawPath($outlinePen, $blade)
$outlinePen.Dispose()

# ---- tsuka (handle) --------------------------------------------------------
# The tsuka must CONTINUE the blade's curve, not sit in a vertical frame: the
# nakago inside it follows the same arc. A straight vertical handle leaves a
# ~10 deg kink at the guard, which is what made the earlier versions look wrong.
# Built as a band along the same centreline, extended to t < 0.
$tH0 = -0.117     # handle top   (y =  24)
$tH1 = -0.660     # handle end   (y = 293)
$hMid = ($tH0 + $tH1) / 2.0
$hHalf = ((BladeY $tH0) - (BladeY $tH1)) / 2.0      # half length, along local Y

$hState = $g.Save()
$g.TranslateTransform([float](BladeX $hMid), [float](BladeY $hMid))
$g.RotateTransform([float](BladeAngle $hMid))

$handle = New-Object System.Drawing.Drawing2D.GraphicsPath
$handle.AddLine([float]-26, [float](-$hHalf), [float]26,  [float](-$hHalf))
$handle.AddLine([float]26,  [float](-$hHalf), [float]23,  [float]$hHalf)
$handle.AddLine([float]23,  [float]$hHalf,    [float]-23, [float]$hHalf)
$handle.AddLine([float]-23, [float]$hHalf,    [float]-26, [float](-$hHalf))
$handle.CloseFigure()
$g.FillPath((VGrad (New-Object System.Drawing.Rectangle(-26, [int](-$hHalf), 52, [int]($hHalf * 2))) $cDark2 $cDark $cDark2 ([float]0)), $handle)

# tsuka-ito: alternating wrap diamonds
$wrapPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(170, 118, 150, 205), [float]5)
$dTop = -$hHalf + 28
$dBot = $hHalf - 16
for ($yy = $dTop; $yy -le $dBot; $yy += 38) {
    $dm = New-Object System.Drawing.Drawing2D.GraphicsPath
    $dm.AddLine([float]-26, [float]$yy,          [float]0,  [float]($yy + 19))
    $dm.AddLine([float]0,   [float]($yy + 19),   [float]26, [float]$yy)
    $dm.AddLine([float]26,  [float]$yy,          [float]0,  [float]($yy - 19))
    $dm.AddLine([float]0,   [float]($yy - 19),   [float]-26, [float]$yy)
    $dm.CloseFigure()
    $g.DrawPath($wrapPen, $dm)
    $dm.Dispose()
}
$wrapPen.Dispose()
$g.DrawPath((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(140, 10, 14, 24), [float]4)), $handle)
$handle.Dispose()

# ---- kashira (pommel) ------------------------------------------------------
$tK = -0.680
$kashiraL = RRect -26 -17 52 34 11
$g.RotateTransform([float]((BladeAngle $tK) - (BladeAngle $hMid)))
$g.TranslateTransform([float]((BladeX $tK) - (BladeX $hMid)), [float]((BladeY $tK) - (BladeY $hMid)))
$g.FillPath((VGrad (New-Object System.Drawing.Rectangle(-26, -17, 52, 34)) $cGoldLo $cGoldHi $cGoldLo ([float]0)), $kashiraL)
$g.DrawPath((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(150, 60, 40, 8), [float]3)), $kashiraL)
$kashiraL.Dispose()
$g.Restore($hState)

# ---- guard blocks, squared to the blade axis -------------------------------
# Drawn last so the guard covers the joint between blade root and handle.
# local (0,0) is placed on the centreline and the frame is rotated by the local
# tangent, so these stay perpendicular to the blade as the blade curves.
$guardState = $g.Save()
$tTsu = -0.1025                                     # tsuba centre (y = 16.8)
$g.TranslateTransform([float](BladeX $tTsu), [float](BladeY $tTsu))
$g.RotateTransform([float](BladeAngle $tTsu))
$tsuba = RRect -66 -14 132 28 12
$g.FillPath((VGrad (New-Object System.Drawing.Rectangle(-66, -14, 132, 28)) $cGoldLo $cGoldHi $cGoldLo ([float]0)), $tsuba)
$g.DrawPath((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(150, 60, 40, 8), [float]4)), $tsuba)
$tsuba.Dispose()
$g.Restore($guardState)

$habState = $g.Save()
$tHab = -0.031                                      # habaki centre (y = -18.6)
$g.TranslateTransform([float](BladeX $tHab), [float](BladeY $tHab))
$g.RotateTransform([float](BladeAngle $tHab))
$habaki = RRect -30 -19 60 38 8
$g.FillPath((VGrad (New-Object System.Drawing.Rectangle(-30, -19, 60, 38)) $cGoldLo $cGoldHi $cGoldLo ([float]0)), $habaki)
$g.DrawPath((New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(140, 60, 40, 8), [float]3)), $habaki)
$habaki.Dispose()
$g.Restore($habState)

$blade.Dispose()

# ============================================================ 3. sparkles ===
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

Star 296 258 46 ([System.Drawing.Color]::FromArgb(240, 255, 255, 255))
Star 214 348 19 ([System.Drawing.Color]::FromArgb(170, 186, 246, 255))
Star 700 728 21 ([System.Drawing.Color]::FromArgb(150, 186, 246, 255))

# =============================================================== 4. output ===
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
