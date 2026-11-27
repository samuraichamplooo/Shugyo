"""Shugyō launcher icon: crossed katanas behind an open book.
Usage: python3 gen.py jade ../../app/src/main/res   (themes: jade, crimson, washi)
With no arguments it writes SVG previews to /tmp/icons2."""
import math, os, sys

def f(v): return f"{v:.2f}"

def poly(pts): return "M" + "L".join(f"{f(x)},{f(y)}" for x, y in pts) + "Z"

def ell(cx, cy, rx, ry):
    return f"M{f(cx-rx)},{f(cy)}a{f(rx)},{f(ry)} 0,1 0,{f(2*rx)},0a{f(rx)},{f(ry)} 0,1 0,{f(-2*rx)},0Z"

class Frame:
    """Sword coordinates: u along the sword from the pommel, v across it."""
    def __init__(self, pommel, angle_deg, mirror=False):
        a = math.radians(angle_deg)
        self.p = pommel
        self.d = (math.cos(a), math.sin(a))
        n = (-self.d[1], self.d[0])
        self.n = (-n[0], -n[1]) if mirror else n
    def pt(self, u, v):
        return (self.p[0] + self.d[0]*u + self.n[0]*v, self.p[1] + self.d[1]*u + self.n[1]*v)

def sword(fr, length, C):
    H, TSUBA, B0 = 10.6, 11.6, 14.6           # handle end, guard, blade start
    w = 4.5                                     # blade width at the collar
    def curve(u):                               # the katana's gentle curve (sori)
        t = max(0.0, (u - B0) / (length - B0))
        return 1.6 * t * t
    def width(u):
        t = (u - B0) / (length - B0)
        return w * (1 - 0.28 * t)
    paths = []
    # blade: back (mune) at +v, edge (ha) at -v
    us = [B0 + (length - 5 - B0) * i / 24 for i in range(25)]
    back = [fr.pt(u, curve(u) + width(u)/2) for u in us]
    edge = [fr.pt(u, curve(u) - width(u)/2) for u in reversed(us)]
    tip = fr.pt(length, curve(length) + width(length) * 0.3)
    paths.append((poly(back + [tip] + edge), C["blade"]))
    # bright edge (hamon) along the cutting side
    hl = [fr.pt(u, curve(u) - width(u)/2 + 1.3) for u in us]
    he = [fr.pt(u, curve(u) - width(u)/2) for u in reversed(us)]
    paths.append((poly(hl + [tip] + he), C["edge"]))
    # back shadow
    bs = [fr.pt(u, curve(u) + width(u)/2) for u in us]
    bi = [fr.pt(u, curve(u) + width(u)/2 - 1.0) for u in reversed(us)]
    paths.append((poly(bs + [tip] + bi), C["back"]))
    # collar (habaki)
    paths.append((poly([fr.pt(TSUBA + 1.2, 2.8), fr.pt(B0 + 0.6, 2.6), fr.pt(B0 + 0.6, -2.6), fr.pt(TSUBA + 1.2, -2.8)]), C["gold"]))
    # handle (tsuka) with its diamond wrap
    paths.append((poly([fr.pt(0.6, 3.4), fr.pt(H, 3.1), fr.pt(H, -3.1), fr.pt(0.6, -3.4)]), C["handle"]))
    d = []
    for k in range(3):
        c = 3.2 + k * 3.0
        d.append(poly([fr.pt(c - 1.25, 0), fr.pt(c, 2.4), fr.pt(c + 1.25, 0), fr.pt(c, -2.4)]))
    paths.append(("".join(d), C["wrap"]))
    # pommel cap (kashira)
    paths.append((poly([fr.pt(-0.8, 3.6), fr.pt(1.3, 3.6), fr.pt(1.3, -3.6), fr.pt(-0.8, -3.6)]), C["gold"]))
    # guard (tsuba): an oval across the sword
    gx, gy = fr.pt(TSUBA, 0)
    ang = math.degrees(math.atan2(fr.d[1], fr.d[0]))
    pts = []
    for i in range(36):
        t = 2 * math.pi * i / 36
        a, b = 1.6 * math.cos(t), 6.4 * math.sin(t)
        pts.append(fr.pt(TSUBA + a, b))
    paths.append((poly(pts), C["guard"]))
    return paths

def book(C, cx=54, top=57, w=27, h=19):
    L, R, B = cx - w/2, cx + w/2, top + h
    p = []
    p.append((poly([(L - 1.6, top + 3.5), (cx, top + 6.2), (R + 1.6, top + 3.5), (R + 1.6, B + 2.2), (cx, B + 5), (L - 1.6, B + 2.2)]), C["cover"]))
    lp = f"M{f(L)},{f(top+1.5)}C{f(L+7)},{f(top-1.8)} {f(cx-6)},{f(top-1.2)} {f(cx-0.5)},{f(top+2.4)}L{f(cx-0.5)},{f(B+1.8)}C{f(cx-6)},{f(B-1.6)} {f(L+7)},{f(B-2)} {f(L)},{f(B)}Z"
    rp = f"M{f(R)},{f(top+1.5)}C{f(R-7)},{f(top-1.8)} {f(cx+6)},{f(top-1.2)} {f(cx+0.5)},{f(top+2.4)}L{f(cx+0.5)},{f(B+1.8)}C{f(cx+6)},{f(B-1.6)} {f(R-7)},{f(B-2)} {f(R)},{f(B)}Z"
    p.append((lp, C["page"]))
    p.append((rp, C["page2"]))
    lines = []
    for i in range(4):
        y = top + 6 + i * 3.6
        lines.append(poly([(L + 3, y), (cx - 3.5, y + 0.8), (cx - 3.5, y + 1.55), (L + 3, y + 0.75)]))
        lines.append(poly([(R - 3, y), (cx + 3.5, y + 0.8), (cx + 3.5, y + 1.55), (R - 3, y + 0.75)]))
    p.append(("".join(lines), C["lines"]))
    p.append((poly([(cx - 0.7, top + 2.6), (cx + 0.7, top + 2.6), (cx + 0.7, B + 1.9), (cx - 0.7, B + 1.9)]), C["spine"]))
    p.append((poly([(cx + 2.2, B + 1.0), (cx + 4.6, B + 0.6), (cx + 4.6, B + 9.6), (cx + 3.4, B + 8.0), (cx + 2.2, B + 9.6)]), C["ribbon"]))
    return p

def icon(C):
    cx, cy = 54, 61
    ang = -36
    a = math.radians(ang)
    back_len, length = 31.0, 66
    pomA = (cx - math.cos(a) * back_len, cy - math.sin(a) * back_len)
    pomB = (cx + math.cos(a) * back_len, cy - math.sin(a) * back_len)
    frA = Frame(pomA, ang)
    frB = Frame(pomB, 180 - ang, mirror=True)
    paths = sword(frB, length, C) + sword(frA, length, C) + book(C)
    return paths

THEMES = {
    "jade": dict(bg=("radial", 54, 40, 80, "#FF23866D", "#FF07261F"), blade="#FFDCE3EA", edge="#FFFFFFFF", back="#FF8D99A6",
                 gold="#FFE3B341", guard="#FF2A2A2E", handle="#FF1D1A1C", wrap="#FFEBC155",
                 cover="#FFB3202A", page="#FFFFF6E2", page2="#FFF3E6C8", lines="#FFD9C69C", spine="#FFC9B387", ribbon="#FFE3B341"),
    "crimson": dict(bg=("radial", 54, 40, 80, "#FFC0392B", "#FF3B0A0D"), blade="#FFE4E9EE", edge="#FFFFFFFF", back="#FF8D99A6",
                 gold="#FFF2C14E", guard="#FF1D1A1C", handle="#FF141215", wrap="#FFF5CB62",
                 cover="#FF14213D", page="#FFFFF6E2", page2="#FFF3E6C8", lines="#FFD9C69C", spine="#FFC9B387", ribbon="#FFF2C14E"),
    "washi": dict(bg=("radial", 54, 40, 80, "#FFFBF1DA", "#FFE0C68E"), blade="#FF2B2B30", edge="#FF6B6F78", back="#FF15151A",
                 gold="#FFB8860B", guard="#FF15151A", handle="#FF15151A", wrap="#FFC9A227",
                 cover="#FFB3202A", page="#FFFFFFFF", page2="#FFF5F0E6", lines="#FFD8CDB5", spine="#FFBFAE8A", ribbon="#FFB3202A"),
}

def svg_color(c): return f"#{c[3:]}", int(c[1:3], 16) / 255

def to_svg(bg, paths, size=300, mask="squircle"):
    _, cx, cy, r, c1, c2 = bg
    defs = f'<radialGradient id="g" cx="{cx}" cy="{cy}" r="{r}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#{c1[3:]}"/><stop offset="1" stop-color="#{c2[3:]}"/></radialGradient>'
    tf = 'transform="translate(18,18) scale(0.6667)"'
    clip = (f'<circle {tf} cx="54" cy="54" r="54"/>' if mask == "circle" else
            f'<path {tf} d="M54,0C10,0 0,10 0,54S10,108 54,108 108,98 108,54 98,0 54,0Z"/>')
    body = "".join(f'<path fill="{svg_color(c)[0]}" fill-opacity="{svg_color(c)[1]:.3f}" d="{d}"/>' for d, c in paths)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="18 18 72 72">'
            f'<defs>{defs}<clipPath id="m">{clip}</clipPath></defs><g clip-path="url(#m)">'
            f'<rect x="0" y="0" width="108" height="108" fill="url(#g)"/>{body}</g></svg>')

if __name__ == "__main__" and len(sys.argv) <= 2:
    os.makedirs("/tmp/icons2", exist_ok=True)
    for name, C in THEMES.items():
        paths = icon(C)
        open(f"/tmp/icons2/{name}.svg", "w").write(to_svg(C["bg"], paths))
        open(f"/tmp/icons2/{name}_c.svg", "w").write(to_svg(C["bg"], paths, mask="circle"))
    print("ok")

# ------------------------------------------------ Android drawables
def vector_xml(paths):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Generated by tools/icon/gen.py: crossed katanas behind an open book. -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="108dp"', '    android:height="108dp"',
           '    android:viewportWidth="108"', '    android:viewportHeight="108">']
    for d, c in paths:
        out.append(f'    <path android:fillColor="{c}" android:pathData="{d}" />')
    out.append('</vector>')
    return "\n".join(out) + "\n"

def background_xml(bg):
    _, cx, cy, r, c1, c2 = bg
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{r}"
                android:startColor="{c1}" android:endColor="{c2}" />
        </aapt:attr>
    </path>
</vector>
'''

def write_android(theme, res):
    C = THEMES[theme]
    open(f"{res}/drawable/ic_launcher_bg.xml", "w").write(background_xml(C["bg"]))
    open(f"{res}/drawable/ic_launcher_foreground.xml", "w").write(vector_xml(icon(C)))
    # Themed (one-color) icon: the same shapes, without the small details.
    skip = {C["lines"], C["spine"], C["edge"], C["back"], C["wrap"]}
    mono = [(d, "#FFFFFFFF") for d, c in icon(C) if c not in skip]
    open(f"{res}/drawable/ic_launcher_mono.xml", "w").write(vector_xml(mono))

if len(sys.argv) > 2:
    write_android(sys.argv[1], sys.argv[2])
    print("wrote", sys.argv[1])
