"""Partner animals: each is a list of shapes per stage, drawn in a 100x100 box standing on y=93."""
import math

def ell(cx, cy, rx, ry):
    return f"M{cx-rx:.2f},{cy:.2f}A{rx:.2f},{ry:.2f} 0 1 0 {cx+rx:.2f},{cy:.2f}A{rx:.2f},{ry:.2f} 0 1 0 {cx-rx:.2f},{cy:.2f}Z"

INK = '#2B2024'; WHITE = '#FFFFFF'; BLUSH = '#FF7FA0'; BAND = '#E63946'; BAND2 = '#C1121F'

class Art:
    def __init__(self): self.s = []
    def f(self, d, c, a=1.0): self.s.append(('f', d, c, a, 0)); return self
    def st(self, d, c, w, a=1.0): self.s.append(('s', d, c, a, w)); return self
    def e(self, cx, cy, rx, ry, c, a=1.0): self.s.append(('e', (cx, cy, rx, ry), c, a, 0)); return self

def shadow(A): A.e(50, 93, 22, 3.6, '#000000', 0.22)
def aura(A, c): A.e(50, 62, 40, 36, c, 0.13); A.e(50, 62, 33, 30, c, 0.15)

def chibi_body(A, main, light, dark, arms=True, belly=True):
    A.e(50, 76, 17, 15, main)
    if belly: A.e(50, 79, 10, 10, light)
    A.e(42, 90, 6.5, 3.6, dark); A.e(58, 90, 6.5, 3.6, dark)
    if arms:
        A.e(35.5, 75, 4.5, 6, main); A.e(64.5, 75, 4.5, 6, main)

def head(A, main, cy=48, rx=24, ry=21): A.e(50, cy, rx, ry, main)

def headband(A, cy=48, rx=24, ry=21, y1=37.5, y2=42.5):
    def hx(y): return rx * math.sqrt(max(0, 1 - ((y - cy) / ry) ** 2))
    a, b = hx(y1), hx(y2)
    A.f(f"M{50-a:.2f},{y1}Q50,{y1-3.5} {50+a:.2f},{y1}L{50+b:.2f},{y2}Q50,{y2-3.5} {50-b:.2f},{y2}Z", BAND)
    kx, ky = 50 + b - 1, (y1 + y2) / 2
    A.f(f"M{kx:.2f},{ky-1}Q{kx+9:.2f},{ky-8} {kx+14:.2f},{ky-5}Q{kx+8:.2f},{ky} {kx+1:.2f},{ky+2}Z", BAND)
    A.f(f"M{kx:.2f},{ky+1}Q{kx+8:.2f},{ky+3} {kx+11:.2f},{ky+10}Q{kx+5:.2f},{ky+7} {kx:.2f},{ky+3}Z", BAND2)

def eyes(A, y=50, dx=9, style='round', iris=None):
    for sx in (-1, 1):
        x = 50 + sx * dx
        if style == 'cat':
            A.e(x, y, 4.6, 5.4, iris); A.e(x, y, 1.4, 4.4, INK); A.e(x + 1.6, y - 2, 1.2, 1.2, WHITE)
        elif style == 'sharp':
            A.e(x, y, 3.8, 4.6, INK); A.e(x + 1.2, y - 1.6, 1.4, 1.4, WHITE)
            A.f(f"M{x - sx*5:.2f},{y-7.5}L{x + sx*3.8:.2f},{y-5.2}L{x + sx*3.5:.2f},{y-3.8}L{x - sx*5.2:.2f},{y-6}Z", INK)
        else:
            A.e(x, y, 4, 5, INK); A.e(x + 1.3, y - 1.7, 1.5, 1.5, WHITE); A.e(x - 1.2, y + 1.9, 0.8, 0.8, WHITE)

def cheeks(A, y=57, dx=15): A.e(50 - dx, y, 3.8, 2.4, BLUSH, 0.55); A.e(50 + dx, y, 3.8, 2.4, BLUSH, 0.55)

def muzzle(A, light, y=58, rx=10, ry=7): A.e(50, y, rx, ry, light)

def nose(A, y=55, c=INK, w=3.2): A.f(f"M{50-w:.2f},{y}Q50,{y-1.6} {50+w:.2f},{y}Q{50+0.8:.2f},{y+3} 50,{y+3}Q{50-0.8:.2f},{y+3} {50-w:.2f},{y}Z", c)

def cat_mouth(A, y=58.5):
    A.st(f"M46.5,{y}Q48.3,{y+2.4} 50,{y}Q51.7,{y+2.4} 53.5,{y}", INK, 1.5)

def smile(A, y=59, w=3.5): A.st(f"M{50-w},{y}Q50,{y+3.4} {50+w},{y}", INK, 1.7)

def gem(A, c, y=73): A.f(f"M50,{y}L53,{y+4}L50,{y+8}L47,{y+4}Z", c)

def pointed_ears(A, main, inner, tip=None, spread=17, top=17, base=34, w=10):
    for sx in (-1, 1):
        bx = 50 + sx * spread
        A.f(f"M{bx - sx*w*0.2:.2f},{base+4}L{bx + sx*3:.2f},{top}L{bx - sx*w:.2f},{base}Z", main)
        A.f(f"M{bx - sx*w*0.05:.2f},{base+1}L{bx + sx*2:.2f},{top+6}L{bx - sx*w*0.75:.2f},{base-1}Z", inner)
        if tip: A.f(f"M{bx + sx*1.2:.2f},{top+5}L{bx + sx*3:.2f},{top}L{bx - sx*2.2:.2f},{top+4}Z", tip)

# ------------------------------------------------------------------ the animals

def fox(stage):  # Hinoko line (kitsune): gains tails as it grows
    A = Art(); main, dark, light, accent = '#FF8A3D', '#C2541B', '#FFF1DE', '#FFD166'
    shadow(A)
    if stage == 3: aura(A, accent)
    tails = stage
    for i in range(tails):
        ang = [0, -24, 24][i] if tails > 1 else 0
        cx, cy = 70, 74
        A.s.append(('g', ang, cx, cy))
        A.f("M62,82C80,86 92,74 88,58C86,50 80,48 76,52C80,60 78,70 64,74Z", main)
        A.f("M86,60C86,54 82,50 77,52C79,55 80,58 79,61C82,62 84,61 86,60Z", accent if stage >= 2 else light)
        A.s.append(('/g',))
    chibi_body(A, main, light, dark)
    pointed_ears(A, main, '#FFD9C4', dark, spread=15, top=16 if stage < 3 else 12, base=33)
    head(A, main)
    A.e(50, 57, 14, 9, light)
    headband(A)
    eyes(A, style='sharp' if stage >= 2 else 'round')
    nose(A, 55.5); cat_mouth(A, 58.5); cheeks(A)
    if stage >= 2: gem(A, accent)
    if stage == 3:
        for x in (42, 50, 58): A.f(f"M{x-2.5},31L{x},{24 if x==50 else 26}L{x+2.5},31Z", accent)
    return A

def otter(stage):  # Shizuku line
    A = Art(); main, dark, light, accent = '#8C6A52', '#5C4434', '#F3E3D1', '#7FD4FF'
    shadow(A)
    if stage == 3: aura(A, accent)
    A.f("M62,84C74,88 86,86 90,78C86,76 76,78 64,78Z", dark)  # flat tail
    chibi_body(A, main, light, dark)
    for sx in (-1, 1): A.e(50 + sx*17, 33, 4.5, 4.5, main); A.e(50 + sx*17, 33, 2.2, 2.2, light)
    head(A, main, ry=20)
    A.e(50, 57, 13, 8.5, light)
    headband(A)
    eyes(A, y=49.5, dx=9.5)
    A.e(50, 54.5, 3.6, 2.6, INK)
    for sx in (-1, 1):
        for dy in (-1.2, 1.2): A.st(f"M{50+sx*7},{56+dy}L{50+sx*16},{55+dy*2.4}", INK, 0.8, 0.6)
    smile(A, 58.6, 2.6); cheeks(A, 57, 16)
    # water drop crest
    A.f("M50,20C47,25 45,28 45,30.5C45,33.5 47.3,35.5 50,35.5C52.7,35.5 55,33.5 55,30.5C55,28 53,25 50,20Z", accent)
    A.e(48.4, 30.5, 1.2, 1.6, WHITE, 0.8)
    if stage >= 2:  # holds a shell
        A.f("M42,74C42,68 58,68 58,74L56,79L44,79Z", '#FFE3EE')
        for x in (45.5, 50, 54.5): A.st(f"M50,79L{x},70", '#E9A6C0', 0.8)
    if stage == 3:
        A.f("M30,40C24,34 26,24 34,22C32,28 36,32 38,34Z", accent, 0.9)
        A.f("M70,40C76,34 74,24 66,22C68,28 64,32 62,34Z", accent, 0.9)
    return A

def tanuki(stage):  # Konoha line
    A = Art(); main, dark, light, accent = '#9C8A78', '#4E4239', '#EFE6D8', '#59C173'
    shadow(A)
    if stage == 3: aura(A, accent)
    A.f("M62,82C78,90 92,80 88,66C86,60 80,60 78,64C80,72 74,78 62,76Z", main)
    for k in range(3):
        A.st(f"M{76+k*3.5},{64+k*5}C{80+k*3},{66+k*5} {84+k*2},{66+k*4} {86},{63+k*5}", dark, 2.2)
    chibi_body(A, main, light, dark)
    A.e(50, 79, 12, 11.5, light)
    for sx in (-1, 1): A.e(50 + sx*15, 31, 6, 6, dark); A.e(50 + sx*15, 31.5, 3, 3, main)
    head(A, main)
    # the mask
    A.f("M30,50C30,44 36,42 42,46C46,49 46,54 42,56C36,58 30,56 30,50Z", dark)
    A.f("M70,50C70,44 64,42 58,46C54,49 54,54 58,56C64,58 70,56 70,50Z", dark)
    A.e(50, 58, 9, 6.5, light)
    headband(A)
    for sx in (-1, 1):
        x = 50 + sx*9
        A.e(x, 50, 3.8, 4.6, WHITE); A.e(x, 50.3, 2.9, 3.7, INK); A.e(x + 1, 49, 1.1, 1.1, WHITE)
    nose(A, 55.5, w=2.8); smile(A, 59, 2.8); cheeks(A, 58, 16)
    # the leaf on its head (a hat at the top stage)
    if stage == 3:
        A.f("M24,32C34,20 66,20 76,32C66,30 34,30 24,32Z", '#D9B26A')
        A.f("M38,26C42,16 58,16 62,26Z", '#C79A4A')
    lw = [1.0, 1.25, 1.1][stage - 1]
    A.f(f"M50,{27 - 4*lw:.1f}C{50 - 9*lw:.1f},{22 - 10*lw:.1f} {50 + 2*lw:.1f},{22 - 16*lw:.1f} {50 + 10*lw:.1f},{22 - 14*lw:.1f}C{50 + 9*lw:.1f},{22 - 6*lw:.1f} {50 + 4*lw:.1f},{25 - 3*lw:.1f} 50,{27 - 4*lw:.1f}Z", accent)
    A.st(f"M50,{27 - 4*lw:.1f}L{50 + 8*lw:.1f},{22 - 12*lw:.1f}", '#2E7D45', 0.9)
    if stage >= 2: gem(A, accent)
    return A

def dragon(stage):  # Kaen line: horns and wings grow
    A = Art(); main, dark, light, accent = '#D7263D', '#8E1B2A', '#FFD59E', '#FFC145'
    shadow(A)
    if stage == 3: aura(A, accent)
    wing = [0.55, 0.85, 1.25][stage - 1]
    for sx in (-1, 1):
        bx, by = 50 + sx*12, 68
        A.f(f"M{bx:.1f},{by}C{bx + sx*14*wing:.1f},{by - 22*wing:.1f} {bx + sx*30*wing:.1f},{by - 22*wing:.1f} {bx + sx*34*wing:.1f},{by - 12*wing:.1f}"
            f"C{bx + sx*26*wing:.1f},{by - 10*wing:.1f} {bx + sx*24*wing:.1f},{by - 2*wing:.1f} {bx + sx*18*wing:.1f},{by + 2*wing:.1f}"
            f"C{bx + sx*14*wing:.1f},{by - 2*wing:.1f} {bx + sx*8*wing:.1f},{by:.1f} {bx:.1f},{by + 6:.1f}Z", dark)
    A.f("M62,84C76,90 88,84 90,72L94,68L92,76L96,78L88,80C84,88 72,90 62,88Z", main)  # tail with a spade
    chibi_body(A, main, light, dark)
    for y in (72, 77, 82): A.st(f"M44,{y}Q50,{y+2} 56,{y}", '#E8B97A', 0.9)
    horn = [0.7, 1.0, 1.35][stage - 1]
    for sx in (-1, 1):
        A.f(f"M{50+sx*10},{33}C{50+sx*12},{33-8*horn:.1f} {50+sx*16},{33-14*horn:.1f} {50+sx*20},{33-16*horn:.1f}C{50+sx*19},{33-9*horn:.1f} {50+sx*17},{33-3*horn:.1f} {50+sx*17},{36}Z", accent)
    head(A, main)
    for x in (44, 50, 56): A.f(f"M{x-2.6},29.5L{x},{24}L{x+2.6},29.5Z", dark)
    A.e(50, 58, 12, 7.5, light)
    headband(A)
    eyes(A, style='sharp')
    A.e(47.5, 56, 0.9, 0.8, INK); A.e(52.5, 56, 0.9, 0.8, INK)
    A.st("M45.5,59.5Q50,62.5 54.5,59.5", INK, 1.7)
    A.f("M52.6,60.4L54.2,59.8L53.6,62.4Z", WHITE)
    cheeks(A, 56, 16)
    if stage >= 2: gem(A, accent, 70)
    return A

def cat(stage):  # Kuro line: a ninja cat; two tails at the top (nekomata)
    A = Art(); main, dark, light, accent = '#3B3552', '#231F33', '#6E6690', '#B79CFF'
    shadow(A)
    if stage == 3: aura(A, accent)
    for i, ang in enumerate([0, 26] if stage == 3 else [0]):
        A.s.append(('g', -ang, 64, 80))
        A.st("M62,82C80,84 82,66 74,60C70,57 66,60 69,63", main, 5.5)
        if stage == 3: A.e(69, 62, 3.4, 3.4, accent, 0.9)
        A.s.append(('/g',))
    chibi_body(A, main, light, dark)
    pointed_ears(A, main, '#FF9EC7', None, spread=16, top=15, base=34)
    head(A, main)
    if stage >= 2:
        A.f("M30,62C40,68 60,68 70,62L72,68C60,74 40,74 28,68Z", '#7A5CFF')
        A.f("M64,66L74,76L70,78L62,70Z", '#5B3FE0')
    headband(A)
    eyes(A, y=50, style='cat', iris='#FFD34D')
    A.f("M48.2,55L51.8,55L50,57.2Z", '#FF9EC7')
    cat_mouth(A, 57.6)
    for sx in (-1, 1):
        for dy in (-1.5, 1.5): A.st(f"M{50+sx*7},{56.5+dy}L{50+sx*17},{55.5+dy*2}", light, 0.8, 0.8)
    cheeks(A, 57.5, 16)
    if stage >= 2: gem(A, accent, 73)
    return A

def dog(stage):  # Pochi line: a shiba; a guardian komainu at the top
    A = Art(); main, dark, light, accent = '#E39B5A', '#9C5A2B', '#FFF4E4', '#3FB6A8'
    shadow(A)
    if stage == 3: aura(A, '#FFC145')
    A.st("M63,74C78,74 80,58 70,58C64,58 64,66 70,66", main, 6)  # curled tail
    A.e(70.5, 62.5, 2.4, 2.4, light)
    chibi_body(A, main, light, dark)
    pointed_ears(A, main, '#FFD9B8', None, spread=16, top=19, base=34, w=9)
    if stage == 3:  # the guardian's curly mane
        for k in range(9):
            a = math.pi * (0.95 + k * 0.1375)
            x, y = 50 + 25*math.cos(a), 52 + 22*math.sin(a)
            A.e(x, y, 5.5, 5.5, '#FFC145')
    head(A, main)
    A.f("M34,54C36,64 44,66 50,66C56,66 64,64 66,54C60,58 56,56 50,56C44,56 40,58 34,54Z", light)  # urajiro
    A.e(50, 58, 9, 6.5, light)
    for sx in (-1, 1): A.e(50 + sx*8, 42.5, 2.4, 1.5, light)  # eyebrow dots
    headband(A, y1=35.5, y2=40)
    eyes(A, y=50)
    nose(A, 55.2, w=3.4); smile(A, 59.2, 3)
    if stage >= 2:
        A.f("M34,64C42,70 58,70 66,64L66,68C58,74 42,74 34,68Z", BAND)
        A.e(50, 71, 2.6, 2.6, '#FFD166')
    cheeks(A, 58, 16)
    return A

def rabbit(stage):  # Mochi line: the moon rabbit
    A = Art(); main, dark, light, accent = '#F5F7FF', '#B9C2D9', '#FFFFFF', '#9EC9FF'
    shadow(A)
    if stage == 3: aura(A, accent)
    A.e(66, 84, 6, 6, light)
    chibi_body(A, main, light, dark)
    ear = [1.0, 1.15, 1.3][stage - 1]
    for sx in (-1, 1):
        bx = 50 + sx*8
        A.f(f"M{bx - 5},{34}C{bx - 7},{34 - 16*ear:.1f} {bx + sx*2 - 4},{34 - 26*ear:.1f} {bx + sx*2},{34 - 26*ear:.1f}C{bx + sx*2 + 4},{34 - 26*ear:.1f} {bx + 7},{34 - 16*ear:.1f} {bx + 5},{34}Z", main)
        A.f(f"M{bx - 2.4},{33}C{bx - 3.6},{34 - 14*ear:.1f} {bx + sx*2 - 2},{34 - 22*ear:.1f} {bx + sx*2},{34 - 22*ear:.1f}C{bx + sx*2 + 2},{34 - 22*ear:.1f} {bx + 3.6},{34 - 14*ear:.1f} {bx + 2.4},{33}Z", '#FFC6DA')
    head(A, main)
    headband(A)
    eyes(A, y=50)
    A.f("M48.4,55L51.6,55L50,56.8Z", '#FF8FB8')
    cat_mouth(A, 57.2)
    cheeks(A, 56.5, 15)
    if stage >= 2:  # a little mochi mallet
        A.f("M64,62L68,60L76,80L72,82Z", '#C79A4A')
        A.f("M62,56C62,52 74,50 75,55C76,60 64,62 62,56Z", '#A87B3A')
    if stage == 3:
        A.f("M50,27C44,29 42,35 45,39C42,32 47,28 53,29C52,28 51,27 50,27Z", '#FFD166')
    if stage >= 2: gem(A, accent, 73)
    return A

def eagle(stage):  # Hina line: chick to great eagle
    if stage == 1:
        A = Art(); main, dark, light = '#E8D3A9', '#B8995F', '#FFF6E0'
        shadow(A)
        for sx in (-1, 1): A.f(f"M{50+sx*17},68C{50+sx*29},68 {50+sx*29},80 {50+sx*19},86Z", dark)
        A.f("M45,49C44,41 49,40 50,47C50,40 56,40 55,49Z", dark)
        A.e(50, 69, 24, 23, main)
        A.e(50, 76, 14, 12, light)
        A.e(42, 91.5, 4.5, 2.4, '#FFC23D'); A.e(58, 91.5, 4.5, 2.4, '#FFC23D')
        headband(A, cy=69, rx=24, ry=23, y1=55, y2=60)
        eyes(A, y=66, dx=9)
        A.f("M45.5,70.5L54.5,70.5L50,76.5Z", '#FFC23D')
        cheeks(A, 73, 15)
        return A
    A = Art(); main, dark, light, accent = '#7A4E2D', '#4E3018', '#FFFFFF', '#FFC23D'
    shadow(A)
    if stage == 3: aura(A, accent)
    wing = 1.0 if stage == 2 else 1.45
    for sx in (-1, 1):
        A.f(f"M{50+sx*12},64C{50+sx*24*wing:.1f},{58-10*wing:.1f} {50+sx*36*wing:.1f},{60-8*wing:.1f} {50+sx*40*wing:.1f},{66-6*wing:.1f}"
            f"C{50+sx*34*wing:.1f},{68:.1f} {50+sx*36*wing:.1f},{72:.1f} {50+sx*30*wing:.1f},{74:.1f}"
            f"C{50+sx*30*wing:.1f},{78} {50+sx*24*wing:.1f},{80} {50+sx*16},{80}Z", dark)
        if stage == 3:
            A.st(f"M{50+sx*28*wing:.1f},{62-6*wing:.1f}L{50+sx*24*wing:.1f},{68:.1f}L{50+sx*30*wing:.1f},{70:.1f}L{50+sx*26*wing:.1f},{76:.1f}", accent, 1.6)
    A.f("M44,86L50,94L56,86Z", dark)  # tail feathers
    A.e(50, 75, 16, 15, main)
    A.e(50, 79, 9, 9, '#A8774F')
    A.e(42, 90.5, 5, 2.8, accent); A.e(58, 90.5, 5, 2.8, accent)
    A.e(50, 48, 21, 19, light)  # white head
    A.f("M29,52C34,62 66,62 71,52C68,60 60,64 50,64C40,64 32,60 29,52Z", '#E9E4DA')
    headband(A, cy=48, rx=21, ry=19, y1=37, y2=41.5)
    eyes(A, y=49, dx=8.5, style='sharp')
    A.f("M45,53C45,50 55,50 55,53C55,57 52,60 50,62C50,59 46,57 45,53Z", accent)
    A.f("M50,62C51,60 53,58 54,56C53,59 52,61 50,62Z", '#C98E1C')
    if stage >= 2: gem(A, '#FFC23D', 72)
    return A

ANIMALS = [
    ('Hinoko', fox), ('Shizuku', otter), ('Konoha', tanuki), ('Kaen', dragon),
    ('Kuro', cat), ('Pochi', dog), ('Mochi', rabbit), ('Hina', eagle),
]
SCALE = {1: 0.84, 2: 0.93, 3: 1.0}

def to_svg_group(A, stage):
    out = []
    for sh in A.s:
        if sh[0] == 'g': out.append(f'<g transform="rotate({sh[1]} {sh[2]} {sh[3]})">')
        elif sh[0] == '/g': out.append('</g>')
        elif sh[0] == 'mark': pass
        elif sh[0] == 'e': cx, cy, rx, ry = sh[1]; out.append(f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{sh[2]}" fill-opacity="{sh[3]}"/>')
        elif sh[0] == 'f': out.append(f'<path d="{sh[1]}" fill="{sh[2]}" fill-opacity="{sh[3]}"/>')
        else: out.append(f'<path d="{sh[1]}" fill="none" stroke="{sh[2]}" stroke-opacity="{sh[3]}" stroke-width="{sh[4]}" stroke-linecap="round" stroke-linejoin="round"/>')
    s = SCALE[stage]
    return f'<g transform="translate(50,93) scale({s}) translate(-50,-93)">{"".join(out)}</g>'

if __name__ == '__main__':
    cell = 130
    parts = []
    names3 = {'Hinoko': ['Hinoko','Kagaribi','Homura'], 'Shizuku': ['Shizuku','Seseragi','Ōnami'], 'Konoha': ['Konoha','Bunbuku','Mamedanuki'],
              'Kaen': ['Kaen','Enryū','Gōka'], 'Kuro': ['Kuro','Shinobi','Nekomata'], 'Pochi': ['Pochi','Hayate','Komainu'],
              'Mochi': ['Mochi','Tsukimi','Gekkō'], 'Hina': ['Hina','Hayabusa','Ōwashi']}
    for r, (n, fn) in enumerate(ANIMALS):
        for st in (1, 2, 3):
            g = to_svg_group(fn(st), st)
            x = (st - 1) * cell + (r % 2) * cell * 3.2; y = (r // 2) * cell
            parts.append(f'<g transform="translate({x},{y}) scale({cell/100})"><rect width="100" height="100" fill="#181614"/>{g}<text x="50" y="99" font-size="6" fill="#ccc" text-anchor="middle" font-family="sans-serif">{names3[n][st-1]}</text></g>')
    W = int(cell * 6.2); H = cell * 4
    open('/tmp/partners/sheet.svg', 'w').write(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}"><rect width="{W}" height="{H}" fill="#0E0D0C"/>{"".join(parts)}</svg>')
    print('ok')
