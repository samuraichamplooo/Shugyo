"""Shugyō launcher icon. Three designs: A = dojo hatchling mascot, B = 8-bit sword and book,
C = level-up shield. Usage: python3 gen.py A ../../app/src/main/res (also writes SVG previews to /tmp/icons)."""
import math, json, sys

def ell(cx, cy, rx, ry):
    return f"M{cx-rx:.2f},{cy:.2f}a{rx:.2f},{ry:.2f} 0,1 0,{2*rx:.2f},0a{rx:.2f},{ry:.2f} 0,1 0,{-2*rx:.2f},0Z"

def rect(x, y, w, h):
    return f"M{x:.2f},{y:.2f}h{w:.2f}v{h:.2f}h{-w:.2f}Z"

def star(cx, cy, R, r, n=5, rot=-90):
    pts = []
    for i in range(2*n):
        a = math.radians(rot + i*180/n)
        rr = R if i % 2 == 0 else r
        pts.append((cx + rr*math.cos(a), cy + rr*math.sin(a)))
    return "M" + "L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + "Z"

# ------------------------------------------------ A: Dojo hatchling mascot
A_bg = ("radial", 54, 42, 78, "#FF3DDBC6", "#FF0B7F78")
A = []
A.append((ell(54, 86, 19, 3.6), "#330A3F3C"))                       # ground shadow
A.append(("M37,45Q31,33 36,27Q42,33 47,40Z", "#FFFFB703"))            # horns behind
A.append(("M71,45Q77,33 72,27Q66,33 61,40Z", "#FFFFB703"))
A.append((ell(45, 85, 6.5, 3.4), "#FFF2D49B"))                        # feet
A.append((ell(63, 85, 6.5, 3.4), "#FFF2D49B"))
A.append((ell(54, 60, 24, 25), "#FFFFF1D0"))                          # body
A.append((ell(54, 71, 13.5, 10.5), "#FFFFF9EC"))                      # belly
A.append(("M34.8,45Q54,38.5 73.2,45L76.7,52Q54,45.5 31.3,52Z", "#FFE63946"))  # headband
A.append(("M75.5,46.5Q83,40.5 87.5,42.5Q83,47 77.5,50Z", "#FFE63946"))     # tails
A.append(("M76.5,49.5Q84,51.5 86.5,58Q81,55.5 76,51.5Z", "#FFC1121F"))
A.append((ell(76.5, 48.5, 3.4, 3.4), "#FFC1121F"))                    # knot
A.append(("M39.5,53.2L49.8,55.4L49.3,57.3L39.1,55Z", "#FF3D2C2E"))     # brows
A.append(("M68.5,53.2L58.2,55.4L58.7,57.3L68.9,55Z", "#FF3D2C2E"))
A.append((ell(45, 61.5, 4.3, 5.3), "#FF2B2024"))                      # eyes
A.append((ell(63, 61.5, 4.3, 5.3), "#FF2B2024"))
A.append((ell(46.5, 59.4, 1.5, 1.5), "#FFFFFFFF"))
A.append((ell(64.5, 59.4, 1.5, 1.5), "#FFFFFFFF"))
A.append((ell(38.6, 67.5, 3.6, 2.2), "#99FF8FA3"))                    # cheeks
A.append((ell(69.4, 67.5, 3.6, 2.2), "#99FF8FA3"))
A.append(("M50,67.6Q54,72.6 58,67.6Q54,70.2 50,67.6Z", "#FF3D2C2E"))   # grin
A_mono = [p for p in A if p[1] not in ()]

# ------------------------------------------------ B: 8-bit sword and book (handheld-console greens)
B_bg = ("solid", "#FF9BBC0F")
sprite = [
    ".......DD.......",
    "......DLLD......",
    "......DLdD......",
    "......DLdD......",
    "......DLdD......",
    "......DLdD......",
    "...DDDDDDDDDD...",
    "...DllllllllD...",
    "...DDDDDDDDDD...",
    ".DDDD..Dd..DDDD.",
    "DLLLLDDDdDDLLLLD",
    "DLddLLLDDLLLddLD",
    "DLLLLLLDDLLLLLLD",
    "DLddLLLDDLLLddLD",
    "DLLLLLLDDLLLLLLD",
    "DDDDDDDDDDDDDDDD",
]
pal = {"D": "#FF0F380F", "d": "#FF306230", "l": "#FF8BAC0F", "L": "#FFE0F8D0"}
px = 3.4
x0 = 54 - 8*px
y0 = 54 - 8*px + 1
B = []
# soft drop shadow
for color_key in ["D", "d", "l", "L"]:
    parts = []
    for r, row in enumerate(sprite):
        for c, ch in enumerate(row):
            if ch == color_key:
                parts.append(rect(x0 + c*px, y0 + r*px, px + 0.02, px + 0.02))
    if parts:
        B.append(("".join(parts), pal[color_key]))
shadow = "".join(rect(x0 + c*px + px*0.6, y0 + r*px + px*0.6, px, px)
                 for r, row in enumerate(sprite) for c, ch in enumerate(row) if ch != ".")
B.insert(0, (shadow, "#5530622F"))

# ------------------------------------------------ C: level-up shield with a book and a star
C_bg = ("linear", 20, 18, 92, 96, "#FF4361EE", "#FF7209B7")
shield = "M54,22L82,31V55C82,72 70,82 54,89C38,82 26,72 26,55V31Z"
inner = "M54,26.5L78,34.2V55C78,69.5 67.8,78.2 54,84.5C40.2,78.2 30,69.5 30,55V34.2Z"
C = []
C.append((shield, "#FFE09F1F"))
C.append((inner, "#FFFFD166"))
C.append(("M34.5,58.5L54,61.5L73.5,58.5V75.5L54,78.5L34.5,75.5Z", "#FF5A189A"))   # cover
C.append(("M36.5,57.5C42.5,54.5 49,54.5 53.4,57.8V74.6C49,71.5 42.5,71.5 36.5,73.8Z", "#FFFFFFFF"))
C.append(("M54.6,57.8C59,54.5 65.5,54.5 71.5,57.5V73.8C65.5,71.5 59,71.5 54.6,74.6Z", "#FFF1ECFF"))
C.append((star(54, 43.5, 9.5, 4.2), "#FFFFFFFF"))
C.append((star(54, 43.5, 9.5, 4.2) , "#00000000"))

designs = {"A": (A_bg, A), "B": (B_bg, B), "C": (C_bg, C)}

def svg_color(c):
    a = int(c[1:3], 16) / 255
    return f"#{c[3:]}", a

def to_svg(bg, paths, size=320, mask="squircle"):
    defs = ""
    if bg[0] == "radial":
        _, cx, cy, r, c1, c2 = bg
        defs = f'<radialGradient id="g" cx="{cx}" cy="{cy}" r="{r}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#{c1[3:]}"/><stop offset="1" stop-color="#{c2[3:]}"/></radialGradient>'
        fill = "url(#g)"
    elif bg[0] == "linear":
        _, x1, y1, x2, y2, c1, c2 = bg
        defs = f'<linearGradient id="g" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#{c1[3:]}"/><stop offset="1" stop-color="#{c2[3:]}"/></linearGradient>'
        fill = "url(#g)"
    else:
        fill = "#" + bg[1][3:]
    tf = 'transform="translate(18,18) scale(0.6667)"'
    clip = (f'<circle {tf} cx="54" cy="54" r="54"/>' if mask == "circle" else
            f'<path {tf} d="M54,0C10,0 0,10 0,54S10,108 54,108 108,98 108,54 98,0 54,0Z"/>')
    body = "".join(f'<path fill="{svg_color(c)[0]}" fill-opacity="{svg_color(c)[1]:.3f}" d="{d}"/>' for d, c in paths)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="18 18 72 72">'
            f'<defs>{defs}<clipPath id="m">{clip}</clipPath></defs><g clip-path="url(#m)">'
            f'<rect x="0" y="0" width="108" height="108" fill="{fill}"/>{body}</g></svg>')

for k, (bg, paths) in designs.items():
    open(f"/tmp/icons/{k}.svg", "w").write(to_svg(bg, paths))
    open(f"/tmp/icons/{k}_circle.svg", "w").write(to_svg(bg, paths, mask="circle"))
json.dump({k: {"bg": v[0], "paths": v[1]} for k, v in designs.items()}, open("/tmp/icons/designs.json", "w"))
print("ok")

# ------------------------------------------------ Android drawables
SCALE = {"A": (0.85, 54, 57), "B": (1.0, 54, 54), "C": (1.0, 54, 54)}

def vector_xml(paths, scale, fill_types=None):
    s, px_, py_ = scale
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- Generated by tools/icon/gen.py -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="108dp"', '    android:height="108dp"',
           '    android:viewportWidth="108"', '    android:viewportHeight="108">',
           f'    <group android:scaleX="{s}" android:scaleY="{s}" android:pivotX="{px_}" android:pivotY="{py_}">']
    for i, (d, c) in enumerate(paths):
        ft = (fill_types or {}).get(i)
        extra = f' android:fillType="{ft}"' if ft else ""
        out.append(f'        <path android:fillColor="{c}"{extra} android:pathData="{d}" />')
    out += ['    </group>', '</vector>']
    return "\n".join(out) + "\n"

def background_xml(bg):
    if bg[0] == "solid":
        return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="{bg[1]}" android:pathData="M0,0h108v108h-108z" />
</vector>
'''
    if bg[0] == "radial":
        _, cx, cy, r, c1, c2 = bg
        grad = f'android:type="radial" android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{r}"'
    else:
        _, x1, y1, x2, y2, c1, c2 = bg
        grad = f'android:type="linear" android:startX="{x1}" android:startY="{y1}" android:endX="{x2}" android:endY="{y2}"'
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient {grad}
                android:startColor="{c1}" android:endColor="{c2}" />
        </aapt:attr>
    </path>
</vector>
'''

def mono_A():
    # One color for themed icons: the silhouette, with the face cut out.
    face = (ell(54, 60, 24, 25) + ell(45, 61.5, 4.3, 5.3) + ell(63, 61.5, 4.3, 5.3)
            + "M39.5,53.2L49.8,55.4L49.3,57.3L39.1,55Z" + "M68.5,53.2L58.2,55.4L58.7,57.3L68.9,55Z"
            + "M50,67.6Q54,72.6 58,67.6Q54,70.2 50,67.6Z")
    paths = [("M37,45Q31,33 36,27Q42,33 47,40Z", "#FFFFFFFF"), ("M71,45Q77,33 72,27Q66,33 61,40Z", "#FFFFFFFF"),
             (ell(45, 85, 6.5, 3.4), "#FFFFFFFF"), (ell(63, 85, 6.5, 3.4), "#FFFFFFFF"),
             (face, "#FFFFFFFF"),
             ("M75.5,46.5Q83,40.5 87.5,42.5Q83,47 77.5,50Z", "#FFFFFFFF"),
             ("M76.5,49.5Q84,51.5 86.5,58Q81,55.5 76,51.5Z", "#FFFFFFFF")]
    return paths, {4: "evenOdd"}

if len(sys.argv) > 2:
    which, res = sys.argv[1], sys.argv[2]
    bg, paths = designs[which]
    open(f"{res}/drawable/ic_launcher_bg.xml", "w").write(background_xml(bg))
    open(f"{res}/drawable/ic_launcher_foreground.xml", "w").write(vector_xml([p for p in paths if p[1] != "#00000000"], SCALE[which]))
    if which == "A":
        mp, ft = mono_A()
    else:
        mp, ft = [(d, "#FFFFFFFF") for d, c in paths if not c.startswith("#00")], None
    open(f"{res}/drawable/ic_launcher_mono.xml", "w").write(vector_xml(mp, SCALE[which], ft))
    print("wrote", which)
