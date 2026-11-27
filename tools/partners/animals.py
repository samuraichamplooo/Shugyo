"""Partner animals. Each stage is its own design: baby (chibi), teen (taller, new gear), final (big, new colours).
Everything is drawn in a 100x100 box standing on y=93."""
import math, re

INK = '#2B2024'; WHITE = '#FFFFFF'; BLUSH = '#FF7FA0'; BAND = '#E63946'; BAND2 = '#C1121F'
NAVY = '#24304F'; GOLD = '#F4C542'; GOLD2 = '#C9961A'

def num(v):
    s = f"{v:.2f}".rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s

def tidy(d): return re.sub(r'-?\d+\.\d+', lambda m: num(float(m.group())), d)

class Art:
    def __init__(self): self.s = []
    def f(self, d, c, a=1.0): self.s.append(('f', tidy(d), c, a, 0)); return self
    def st(self, d, c, w, a=1.0): self.s.append(('s', tidy(d), c, a, w)); return self
    def e(self, cx, cy, rx, ry, c, a=1.0): self.s.append(('e', (cx, cy, rx, ry), c, a, 0)); return self
    def rot(self, ang, px, py): self.s.append(('g', ang, px, py))
    def end(self): self.s.append(('/g',))

class Geo:
    def __init__(s, stage):
        s.stage = stage
        s.hx, s.hy, s.hrx, s.hry = {1: (50, 48, 24, 21), 2: (50, 39, 19.5, 17.5), 3: (50, 29, 18, 16)}[stage]
        s.k = s.hrx / 24
        s.ey = s.hy + 2 * s.k; s.edx = 9 * s.k
        s.ny = s.hy + 7.5 * s.k; s.my = s.hy + 10.5 * s.k
        s.cy = s.hy + 9 * s.k; s.cdx = 15 * s.k
        s.neck = {1: 64, 2: 54, 3: 44}[stage]
        s.waist = {1: 80, 2: 75, 3: 72}[stage]
        s.hands = {1: ((35.5, 77), (64.5, 77)), 2: ((33.5, 73), (66.5, 73)), 3: ((26.5, 68), (73.5, 68))}[stage]
        s.hip = {1: (60, 82), 2: (58, 79), 3: (58, 74)}[stage]
        s.chest = {1: (50, 76), 2: (50, 67), 3: (50, 59)}[stage]

def shadow(A, w=22): A.e(50, 93, w, 3.6, '#000000', 0.22)
def aura(A, c, cy=56, r=1.0): A.e(50, cy, 42 * r, 38 * r, c, 0.12); A.e(50, cy, 34 * r, 31 * r, c, 0.14)

def body(A, G, main, light, dark, legc=None, armc=None, belly=True, arms=True, legs=True, torso=None):
    legc = legc or main; armc = armc or main; torso = torso or main
    if G.stage == 1:
        A.e(50, 76, 17, 15, torso)
        if belly: A.e(50, 79, 10, 10, light)
        if legs: A.e(42, 90, 6.5, 3.6, dark); A.e(58, 90, 6.5, 3.6, dark)
        if arms: A.e(35.5, 75, 4.5, 6, armc); A.e(64.5, 75, 4.5, 6, armc)
    elif G.stage == 2:
        if legs:
            A.st("M44,76L43,87.5", legc, 8.5); A.st("M56,76L57,87.5", legc, 8.5)
            A.e(42.5, 90, 6, 3.3, dark); A.e(57.5, 90, 6, 3.3, dark)
        A.e(50, 67, 14, 15.5, torso)
        if belly: A.e(50, 70, 8.5, 10.5, light)
        if arms:
            A.st("M38.5,59Q34,65 33.5,72", armc, 6.5); A.st("M61.5,59Q66,65 66.5,72", armc, 6.5)
            A.e(33.5, 73, 3.9, 3.9, armc); A.e(66.5, 73, 3.9, 3.9, armc)
    else:
        if legs:
            A.st("M42.5,74L41,86.5", legc, 11); A.st("M57.5,74L59,86.5", legc, 11)
            A.e(40, 90, 8, 3.9, dark); A.e(60, 90, 8, 3.9, dark)
        A.f("M33,50C33,40 67,40 67,50C67,62 63,73 58,79L42,79C37,73 33,62 33,50Z", torso)
        if belly: A.e(50, 60, 10.5, 13.5, light)
        if arms:
            A.st("M36,47C29,52 26.5,59 26.5,66", armc, 8.5); A.st("M64,47C71,52 73.5,59 73.5,66", armc, 8.5)
            A.e(26.5, 68, 5.2, 5.2, armc); A.e(73.5, 68, 5.2, 5.2, armc)

def head(A, G, c, rx=1.0, ry=1.0): A.e(G.hx, G.hy, G.hrx * rx, G.hry * ry, c)

def lerp(p, q, t): return (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)

def ears(A, G, main, inner, tip=None, height=1.0, spread=0.67, width=0.42, lean=3.0, tuft=None):
    k = G.k; base = G.hy - G.hry * 0.62; top = base - G.hry * 0.85 * height
    sp = G.hrx * spread; w = G.hrx * width
    for sx in (-1, 1):
        bx = 50 + sx * sp
        T = (bx + sx * lean * k, top); P1 = (bx - sx * w * 0.2, base + 4 * k); P2 = (bx - sx * w, base)
        A.f(f"M{P1[0]},{P1[1]}L{T[0]},{T[1]}L{P2[0]},{P2[1]}Z", main)
        I1 = lerp(P1, T, 0.12); I2 = lerp(P2, T, 0.12); IT = lerp(T, ((P1[0] + P2[0]) / 2, (P1[1] + P2[1]) / 2), 0.25)
        A.f(f"M{I1[0]},{I1[1]}L{IT[0]},{IT[1]}L{I2[0]},{I2[1]}Z", inner)
        if tip:
            a = lerp(T, P1, 0.3); b = lerp(T, P2, 0.3)
            A.f(f"M{a[0]},{a[1]}L{T[0]},{T[1]}L{b[0]},{b[1]}Z", tip)
        if tuft:
            A.st(f"M{T[0]},{T[1]}L{T[0] + sx * 2.5 * k},{T[1] - 5 * k}", tuft, 1.6 * k)

def round_ears(A, G, main, inner, r=4.5, dx=17, dy=-15):
    k = G.k
    for sx in (-1, 1): A.e(50 + sx * dx * k, G.hy + dy * k, r * k, r * k, main); A.e(50 + sx * dx * k, G.hy + dy * k, r * 0.5 * k, r * 0.5 * k, inner)

def eyes(A, G, style='round', iris='#FFD34D', lid=None, dx=None, y=None, size=1.0):
    k = G.k * size; dx = dx if dx is not None else G.edx; y = y if y is not None else G.ey
    for sx in (-1, 1):
        x = 50 + sx * dx
        if style == 'round':
            A.e(x, y, 4 * k, 5 * k, INK); A.e(x + 1.3 * k, y - 1.7 * k, 1.5 * k, 1.5 * k, WHITE); A.e(x - 1.2 * k, y + 1.9 * k, 0.8 * k, 0.8 * k, WHITE)
        elif style == 'sharp':
            A.e(x, y, 3.8 * k, 4.6 * k, INK); A.e(x + 1.2 * k, y - 1.6 * k, 1.4 * k, 1.4 * k, WHITE)
            A.f(f"M{x - sx*5*k},{y-7.5*k}L{x + sx*3.8*k},{y-5.2*k}L{x + sx*3.5*k},{y-3.8*k}L{x - sx*5.2*k},{y-6*k}Z", INK)
        elif style == 'cat':
            A.e(x, y, 4.6 * k, 5.4 * k, iris); A.e(x, y, 1.4 * k, 4.4 * k, INK); A.e(x + 1.6 * k, y - 2 * k, 1.2 * k, 1.2 * k, WHITE)
        elif style in ('fierce', 'fiercecat'):
            A.e(x, y, 4.2 * k, 4.6 * k, INK)
            A.e(x, y + 0.6 * k, 3.1 * k, 3.6 * k, iris)
            if style == 'fiercecat': A.e(x, y + 0.6 * k, 0.9 * k, 3.0 * k, INK)
            else: A.e(x, y + 0.8 * k, 1.6 * k, 2.2 * k, INK)
            A.e(x + 1.2 * k, y - 0.6 * k, 1.0 * k, 1.0 * k, WHITE)
            # a slanted lid makes the look determined
            A.f(f"M{x - sx*5*k},{y-7*k}L{x + sx*5*k},{y-7*k}L{x + sx*5*k},{y-4.2*k}L{x - sx*5*k},{y-1.2*k}Z", lid)
            A.st(f"M{x - sx*4.6*k},{y-1.4*k}L{x + sx*4.8*k},{y-4.4*k}", INK, 1.6 * k)

def cheeks(A, G, a=0.55): A.e(50 - G.cdx, G.cy, 3.8 * G.k, 2.4 * G.k, BLUSH, a); A.e(50 + G.cdx, G.cy, 3.8 * G.k, 2.4 * G.k, BLUSH, a)
def muzzle(A, G, c, w=14, h=9, dy=9): A.e(50, G.hy + dy * G.k, w * G.k, h * G.k, c)
def nose(A, G, c=INK, w=3.2, dy=0):
    k = G.k; y = G.ny + dy * k; w = w * k
    A.f(f"M{50-w},{y}Q50,{y-1.6*k} {50+w},{y}Q{50+0.8*k},{y+3*k} 50,{y+3*k}Q{50-0.8*k},{y+3*k} {50-w},{y}Z", c)
def tri_nose(A, G, c):
    k = G.k; y = G.ny - 0.5 * k
    A.f(f"M{50-1.8*k},{y}L{50+1.8*k},{y}L50,{y+2.2*k}Z", c)
def cat_mouth(A, G, dy=0, c=INK):
    k = G.k; y = G.my + dy * k
    A.st(f"M{50-3.5*k},{y}Q{50-1.7*k},{y+2.4*k} 50,{y}Q{50+1.7*k},{y+2.4*k} {50+3.5*k},{y}", c, 1.5 * k)
def smile(A, G, w=3.5, dy=0.5): k = G.k; y = G.my + dy * k; A.st(f"M{50-w*k},{y}Q50,{y+3.4*k} {50+w*k},{y}", INK, 1.7 * k)
def grin(A, G, w=5, dy=0, fangs=True):
    k = G.k; y = G.my + dy * k
    A.f(f"M{50-w*k},{y}Q50,{y+7*k} {50+w*k},{y}Z", '#7A1F2B')
    if fangs:
        for sx in (-1, 1): A.f(f"M{50+sx*(w-2.2)*k},{y+0.2*k}L{50+sx*(w-0.8)*k},{y+0.2*k}L{50+sx*(w-1.4)*k},{y+2.6*k}Z", WHITE)
    A.st(f"M{50-w*k},{y}Q50,{y+1*k} {50+w*k},{y}", INK, 1.2 * k)
def whiskers(A, G, c=INK, a=0.6):
    k = G.k
    for sx in (-1, 1):
        for dy in (-1.4, 1.4): A.st(f"M{50+sx*7*k},{G.ny+1*k+dy*k}L{50+sx*17*k},{G.ny+dy*2.3*k}", c, 0.8 * k, a)

def headband(A, G, c=BAND, c2=BAND2, tails=1.0):
    cy, rx, ry = G.hy, G.hrx, G.hry
    y1 = cy - ry * 0.5; y2 = y1 + ry * 0.24
    def hx(y): return rx * math.sqrt(max(0, 1 - ((y - cy) / ry) ** 2))
    a, b = hx(y1), hx(y2)
    A.f(f"M{50-a},{y1}Q50,{y1-3.5*G.k} {50+a},{y1}L{50+b},{y2}Q50,{y2-3.5*G.k} {50-b},{y2}Z", c)
    kx, ky = 50 + b - 1, (y1 + y2) / 2; t = tails * G.k
    A.f(f"M{kx},{ky-1}Q{kx+9*t},{ky-8*t} {kx+14*t},{ky-5*t}Q{kx+8*t},{ky} {kx+1},{ky+2}Z", c)
    A.f(f"M{kx},{ky+1}Q{kx+8*t},{ky+3*t} {kx+11*t},{ky+10*t}Q{kx+5*t},{ky+7*t} {kx},{ky+3}Z", c2)
    return y1, y2

def hachigane(A, G, emblem=None, band=NAVY, plate=GOLD):
    y1, y2 = headband(A, G, band, '#151C30', tails=1.3)
    k = G.k; m = (y1 + y2) / 2
    A.f(f"M{50-7*k},{y1-1.6*k}L{50+7*k},{y1-1.6*k}L{50+6.2*k},{y2+0.6*k}L{50-6.2*k},{y2+0.6*k}Z", plate)
    A.st(f"M{50-7*k},{y1-1.6*k}L{50+7*k},{y1-1.6*k}", '#FFF2B3', 0.8 * k, 0.9)
    if emblem: emblem(A, 50, m - 0.4 * k, 2.4 * k)

def gem(A, x, y, s, c): A.f(f"M{x},{y-s}L{x+s*0.75},{y}L{x},{y+s}L{x-s*0.75},{y}Z", c); A.f(f"M{x},{y-s}L{x+s*0.75},{y}L{x},{y}Z", WHITE, 0.45)

def wisp(A, x, y, s, c, inner=None):
    A.f(f"M{x},{y-s*1.7}C{x+s*0.3},{y-s*0.9} {x+s},{y-s*0.5} {x+s},{y+s*0.2}C{x+s},{y+s*0.8} {x+s*0.5},{y+s} {x},{y+s}C{x-s*0.5},{y+s} {x-s},{y+s*0.8} {x-s},{y+s*0.2}C{x-s},{y-s*0.5} {x-s*0.3},{y-s*0.9} {x},{y-s*1.7}Z", c)
    A.e(x, y + s * 0.3, s * 0.5, s * 0.55, inner or WHITE, 0.85)

def bolt(A, x, y, s, c):
    A.f(f"M{x+s*0.2},{y-s}L{x-s*0.5},{y+s*0.1}L{x},{y+s*0.1}L{x-s*0.3},{y+s}L{x+s*0.55},{y-s*0.15}L{x+s*0.05},{y-s*0.15}Z", c)

def star(A, x, y, s, c, a=1.0):
    A.f(f"M{x},{y-s}Q{x+s*0.18},{y-s*0.18} {x+s},{y}Q{x+s*0.18},{y+s*0.18} {x},{y+s}Q{x-s*0.18},{y+s*0.18} {x-s},{y}Q{x-s*0.18},{y-s*0.18} {x},{y-s}Z", c, a)

# ================================================================ FOX: kit → 3 tails → nine-tailed white kitsune

def fox_tail(A, px, py, ang, L, c, tip, w=8, flame=None, outline=None):
    A.rot(ang, px, py)
    d = f"M{px-w*0.45},{py}C{px-w*1.3},{py-L*0.35} {px-w*1.1},{py-L*0.85} {px},{py-L}C{px+w*1.1},{py-L*0.85} {px+w*1.3},{py-L*0.35} {px+w*0.45},{py}Z"
    A.f(d, c)
    if outline: A.st(d, outline, 1.1)
    A.f(f"M{px-w*0.97},{py-L*0.66}C{px-w*0.8},{py-L*0.86} {px-w*0.4},{py-L*0.96} {px},{py-L}C{px+w*0.4},{py-L*0.96} {px+w*0.8},{py-L*0.86} {px+w*0.97},{py-L*0.66}Q{px},{py-L*0.56} {px-w*0.97},{py-L*0.66}Z", tip)
    if flame:
        A.f(f"M{px-w*0.55},{py-L*0.93}Q{px-w*0.5},{py-L-5} {px},{py-L-9}Q{px+w*0.5},{py-L-5} {px+w*0.55},{py-L*0.93}Q{px},{py-L*0.98} {px-w*0.55},{py-L*0.93}Z", flame)
    A.end()

def fox(stage):
    G = Geo(stage); A = Art()
    if stage == 1:
        main, dark, light, accent = '#FF8A3D', '#C2541B', '#FFF1DE', '#FFD166'
        shadow(A); fox_tail(A, 60, 82, 62, 28, main, light)
        body(A, G, main, light, dark)
        ears(A, G, main, '#FFD9C4', dark, height=1.0)
        head(A, G, main); muzzle(A, G, light)
        headband(A, G); eyes(A, G); nose(A, G); cat_mouth(A, G); cheeks(A, G)
    elif stage == 2:
        main, dark, light, accent = '#F2622E', '#3E1D14', '#FFF1DE', '#FFC145'
        shadow(A)
        for ang in (34, 60, 86): fox_tail(A, 57, 80, ang, 36, main, accent, w=8.5)
        body(A, G, main, light, dark, legc='#4A2318', armc='#4A2318')
        ears(A, G, main, '#FFD9C4', '#3E1D14', height=1.45, spread=0.62, width=0.45)
        head(A, G, main, rx=1.03); muzzle(A, G, light, w=15, h=9.5)
        headband(A, G, tails=1.3); eyes(A, G, 'sharp'); nose(A, G); cat_mouth(A, G); cheeks(A, G, 0.4)
        wisp(A, 67, 64, 3.6, '#FFB830', '#FF5E2E')
    else:
        main, dark, light = '#FFF7EC', '#D62839', '#FFFFFF'
        shadow(A, 26); aura(A, '#FF9F43', 52)
        for i in range(9): fox_tail(A, 50, 68, -96 + i * 24, 46, '#FFEBD6', '#FF9A4D', w=7.6, flame='#E63946', outline='#E9A574')
        body(A, G, main, light, dark, legc=main)
        A.st("M36,71Q50,76 64,71", '#D62839', 2.6); A.e(50, 74.5, 2, 2, GOLD)
        A.st("M50,75L48,81", '#D62839', 1.6); A.st("M50,75L52,81", '#D62839', 1.6)
        for sx in (-1, 1): A.st(f"M{50+sx*24.5},58L{50+sx*28.5},60", '#D62839', 1.6)
        ears(A, G, main, '#FF9AA8', '#D62839', height=1.6, spread=0.6, width=0.45)
        head(A, G, main, rx=1.05); muzzle(A, G, light, w=15, h=9.5)
        A.f(f"M50,{G.hy-12}C48,{G.hy-9} 47,{G.hy-7} 50,{G.hy-4}C53,{G.hy-7} 52,{G.hy-9} 50,{G.hy-12}Z", '#D62839')
        eyes(A, G, 'fierce', iris='#FFB000', lid=main)
        for sx in (-1, 1): A.st(f"M{50+sx*10.5},{G.ey+4.2}L{50+sx*14.5},{G.ey+2.4}", '#D62839', 1.5)
        nose(A, G); cat_mouth(A, G)
        wisp(A, 24, 16, 3.6, '#7FD4FF'); wisp(A, 78, 12, 3.2, '#7FD4FF'); wisp(A, 12, 64, 2.8, '#7FD4FF')
    return A

# ================================================================ OTTER: pup → scarfed river otter → wave guardian

def otter(stage):
    G = Geo(stage); A = Art()
    if stage == 1:
        main, dark, light, accent = '#8C6A52', '#5C4434', '#F3E3D1', '#7FD4FF'
        shadow(A); A.f("M62,84C74,88 86,86 90,78C86,76 76,78 64,78Z", dark)
        body(A, G, main, light, dark)
        round_ears(A, G, main, light)
        head(A, G, main, ry=0.95); muzzle(A, G, light, w=13, h=8.5)
        headband(A, G); eyes(A, G); A.e(50, G.ny - 0.8, 3.6, 2.6, INK); whiskers(A, G); smile(A, G, 2.6); cheeks(A, G)
        A.f("M50,20C47,25 45,28 45,30.5C45,33.5 47.3,35.5 50,35.5C52.7,35.5 55,33.5 55,30.5C55,28 53,25 50,20Z", accent)
    elif stage == 2:
        main, dark, light, accent = '#6E4E3A', '#3E2A1E', '#F6EBDD', '#2F80ED'
        shadow(A)
        A.f("M58,84C72,90 88,88 94,76C88,74 74,78 60,78Z", dark)
        body(A, G, main, light, dark)
        # long blue scarf
        A.f("M34,53C42,58 58,58 66,53L67,58C58,63 42,63 33,58Z", accent)
        A.f("M62,57C70,58 80,54 88,48L90,52C82,60 72,64 62,62Z", accent); A.f("M80,51L90,48L88,54Z", '#1B5FC1')
        # a scallop shell held to the chest
        A.f("M42,70C42,63 58,63 58,70L55.5,74L44.5,74Z", '#FFE3EE')
        for x in (45.5, 50, 54.5): A.st(f"M50,74L{x},65.5", '#E9A6C0', 0.8)
        A.e(44, 71, 3.4, 3.4, main); A.e(56, 71, 3.4, 3.4, main)
        round_ears(A, G, main, light)
        head(A, G, main, ry=0.95); A.e(50, G.hy + 3, G.hrx * 0.82, G.hry * 0.7, light)
        muzzle(A, G, '#FFFFFF', w=12, h=8)
        headband(A, G, tails=1.3); eyes(A, G, 'sharp'); A.e(50, G.ny - 0.6, 3.0, 2.2, INK); whiskers(A, G); smile(A, G, 2.6); cheeks(A, G, 0.4)
        gem(A, 50, G.hy - 11.5, 3, '#7FD4FF')
    else:
        main, dark, light, accent = '#36536E', '#1C3247', '#EEF5FA', '#5CE1E6'
        shadow(A, 28)
        # the great wave arcing behind
        A.st("M16,90C8,58 22,22 54,14C78,8 94,24 90,40C87,50 76,50 74,42C72,36 78,32 82,36", '#2F80ED', 10)
        A.st("M10,80C4,48 24,10 56,6C82,4 98,24 95,40", WHITE, 2.2, 0.85)
        for (x, y, r) in [(8, 62, 2.4), (14, 36, 2.2), (30, 14, 2.2), (72, 4, 2), (97, 32, 2)]: A.e(x, y, r, r, WHITE, 0.9)
        A.f("M60,80C76,88 92,86 96,74C90,72 74,76 62,74Z", dark)
        body(A, G, main, light, dark)
        for sx in (-1, 1): A.f(f"M{50+sx*25},56L{50+sx*33},60L{50+sx*27},63Z", accent)
        # coral trident
        A.st("M74,90L74,26", '#FF7F6B', 2.6)
        A.st("M66,34L82,34", '#FF7F6B', 2.6)
        A.st("M66,34L66,24", '#FF7F6B', 2.4); A.st("M82,34L82,24", '#FF7F6B', 2.4)
        for x, top in ((66, 18), (74, 14), (82, 18)): A.f(f"M{x-2.4},{top+7}L{x},{top}L{x+2.4},{top+7}Z", '#FF7F6B')
        A.e(73.5, 68, 5.2, 5.2, main)
        round_ears(A, G, main, light, dy=-14)
        head(A, G, main); A.e(50, G.hy + 3, G.hrx * 0.84, G.hry * 0.68, light)
        hachigane(A, G, emblem=lambda A, x, y, s: A.f(f"M{x},{y-s*1.2}C{x-s*0.6},{y-s*0.2} {x-s*0.8},{y+s*0.2} {x-s*0.8},{y+s*0.5}C{x-s*0.8},{y+s} {x+s*0.8},{y+s} {x+s*0.8},{y+s*0.5}C{x+s*0.8},{y+s*0.2} {x+s*0.6},{y-s*0.2} {x},{y-s*1.2}Z", '#2F80ED'))
        eyes(A, G, 'fierce', iris='#3FE0E8', lid=light)
        A.e(50, G.ny - 0.6, 2.8, 2.0, INK); whiskers(A, G, '#6A7F92', 0.8); smile(A, G, 2.6)
    return A

# ================================================================ TANUKI: leaf kit → teapot tanuki (Bunbuku) → shapeshifter master

def tanuki_mask(A, G, c):
    k = G.k; y = G.ey
    for sx in (-1, 1):
        x0 = 50 + sx * 20 * k
        A.f(f"M{x0},{y}C{x0},{y-6*k} {50+sx*14*k},{y-8*k} {50+sx*8*k},{y-4*k}C{50+sx*4*k},{y-1*k} {50+sx*4*k},{y+4*k} {50+sx*8*k},{y+6*k}C{50+sx*14*k},{y+8*k} {x0},{y+6*k} {x0},{y}Z", c)

def striped_tail(A, px, py, c, stripe, s=1.0, flip=1):
    A.f(f"M{px},{py}C{px+16*s*flip},{py+8*s} {px+30*s*flip},{py-2*s} {px+26*s*flip},{py-16*s}C{px+24*s*flip},{py-22*s} {px+18*s*flip},{py-22*s} {px+16*s*flip},{py-18*s}C{px+18*s*flip},{py-10*s} {px+12*s*flip},{py-4*s} {px},{py-6*s}Z", c)
    for t in (0.35, 0.6, 0.85):
        cx = px + (8 + 18 * t) * s * flip; cy = py - (2 + 14 * t) * s
        A.st(f"M{cx-4*s*flip},{cy-1*s}Q{cx},{cy+3*s} {cx+5*s*flip},{cy-2*s}", stripe, 2.4 * s)

def leaf(A, x, y, s, c, vein='#2E7D45', ang=0):
    if ang: A.rot(ang, x, y)
    A.f(f"M{x},{y}C{x-s*0.9},{y-s*0.6} {x-s*0.5},{y-s*1.6} {x+s*0.4},{y-s*1.9}C{x+s*0.7},{y-s*1.1} {x+s*0.5},{y-s*0.4} {x},{y}Z", c)
    A.st(f"M{x},{y}L{x+s*0.3},{y-s*1.6}", vein, max(0.7, s * 0.08))
    if ang: A.end()

def tanuki(stage):
    G = Geo(stage); A = Art()
    if stage == 1:
        main, dark, light, accent = '#9C8A78', '#4E4239', '#EFE6D8', '#59C173'
        shadow(A); striped_tail(A, 62, 82, main, dark, 0.9)
        body(A, G, main, light, dark); A.e(50, 79, 12, 11.5, light)
        round_ears(A, G, dark, main, r=6, dx=15, dy=-17)
        head(A, G, main); tanuki_mask(A, G, dark); muzzle(A, G, light, w=9, h=6.5, dy=10)
        headband(A, G)
        for sx in (-1, 1): A.e(50 + sx * 9, 50, 3.8, 4.6, WHITE); A.e(50 + sx * 9, 50.3, 2.9, 3.7, INK); A.e(50 + sx * 9 + 1, 49, 1.1, 1.1, WHITE)
        nose(A, G, w=2.8); smile(A, G, 2.8); cheeks(A, G)
        leaf(A, 50, 26, 9, accent)
    elif stage == 2:
        main, dark, light, accent = '#8E7A66', '#3F342C', '#EFE6D8', '#59C173'
        iron, iron2, iron3 = '#3E6460', '#2A4744', '#6E9A94'
        shadow(A, 26); striped_tail(A, 64, 80, main, dark, 1.05)
        # legs and arms poke out of a cast-iron teapot
        A.st("M44,78L43,87.5", dark, 8.5); A.st("M56,78L57,87.5", dark, 8.5)
        A.e(42.5, 90, 6, 3.3, dark); A.e(57.5, 90, 6, 3.3, dark)
        A.st("M34,64Q28,68 28,74", dark, 6.5); A.e(28, 75, 4, 4, dark)
        A.f("M33,66C28,64 22,58 20,52L25,50.5C27,56 30,59 34,60Z", iron)  # spout
        A.st("M66,62Q72,66 72,72", dark, 6.5); A.e(72, 73, 4, 4, dark)
        A.e(50, 68, 18.5, 15, iron)
        A.st("M31.8,63Q50,68 68.2,63", iron2, 2.2)
        for (x, y) in [(40, 72), (47, 75), (54, 75), (61, 72), (44, 79), (56, 79), (50, 81)]: A.e(x, y, 1.6, 1.6, iron3)
        A.e(50, 54.5, 13, 3.2, iron2)
        round_ears(A, G, dark, main, r=6, dx=15, dy=-16)
        head(A, G, main); tanuki_mask(A, G, dark); muzzle(A, G, light, w=9, h=6.5, dy=10)
        headband(A, G, tails=1.3)
        k = G.k
        for sx in (-1, 1): x = 50 + sx * G.edx; A.e(x, G.ey, 3.8 * k, 4.6 * k, WHITE); A.e(x, G.ey + 0.3, 2.9 * k, 3.7 * k, INK); A.e(x + 1, G.ey - 1, 1.1, 1.1, WHITE)
        nose(A, G, w=2.8); grin(A, G, 3.6, 0.5, fangs=False); cheeks(A, G, 0.5)
        leaf(A, 50, G.hy - 15, 8, accent, ang=-20); leaf(A, 22, 50, 5, accent, ang=-60)
    else:
        main, dark, light, accent = '#7A6552', '#33291F', '#F1E6D2', '#3FAE5C'
        shadow(A, 30); aura(A, accent, 52)
        # a leaf cloak behind
        A.f("M30,44C14,58 10,78 18,90L82,90C90,78 86,58 70,44Z", accent)
        for x in (24, 36, 50, 64, 76): A.st(f"M50,46L{x},88", '#2E7D45', 1.1, 0.8)
        striped_tail(A, 62, 76, main, dark, 1.35)
        body(A, G, main, light, dark, legc=dark, armc=dark)
        A.e(50, 62, 14, 15, light)  # the big drum belly
        A.st("M44,62C44,56 56,56 56,62C56,67 47,67 47,62C47,59 53,59 53,62", '#B59B74', 1.6)
        # a giant leaf fan in one hand
        leaf(A, 76, 66, 17, '#59C173', ang=20); A.e(73.5, 68, 5.2, 5.2, dark)
        round_ears(A, G, dark, main, r=5.5, dx=15, dy=-15)
        head(A, G, main); tanuki_mask(A, G, dark); muzzle(A, G, light, w=9, h=6.5, dy=10)
        eyes(A, G, 'fierce', iris='#F4C542', lid=dark)
        nose(A, G, w=2.8); grin(A, G, 4.2, 0.5)
        # straw kasa hat
        A.f("M18,19C30,8 70,8 82,19C70,17 30,17 18,19Z", '#D9B26A')
        A.f("M34,14C40,2 60,2 66,14C58,12 42,12 34,14Z", '#C79A4A')
        A.st("M30,17L70,17", '#A07A36', 1.2)
        leaf(A, 56, 7, 7, accent, ang=25)
    return A

# ================================================================ DRAGON: hatchling → young drake → crimson-and-gold great dragon

def dragon_wings(A, s, mem, rib, by=64, spread=12):
    for sx in (-1, 1):
        bx = 50 + sx * spread
        pts = [(bx, by), (bx + sx * 14 * s, by - 26 * s), (bx + sx * 30 * s, by - 30 * s), (bx + sx * 38 * s, by - 18 * s)]
        A.f(f"M{bx},{by}C{bx + sx*14*s},{by - 24*s} {bx + sx*30*s},{by - 30*s} {bx + sx*38*s},{by - 18*s}"
            f"C{bx + sx*32*s},{by - 14*s} {bx + sx*32*s},{by - 6*s} {bx + sx*28*s},{by - 2*s}"
            f"C{bx + sx*24*s},{by - 6*s} {bx + sx*20*s},{by - 2*s} {bx + sx*18*s},{by + 4*s}"
            f"C{bx + sx*14*s},{by} {bx + sx*8*s},{by + 2*s} {bx},{by + 8}Z", mem)
        A.st(f"M{bx},{by}C{bx + sx*14*s},{by - 24*s} {bx + sx*30*s},{by - 30*s} {bx + sx*38*s},{by - 18*s}", rib, 2.4 * s)
        for (tx, ty) in [(28, -2), (18, 4)]: A.st(f"M{bx + sx*24*s},{by - 26*s}L{bx + sx*tx*s},{by + ty*s}", rib, 1.4 * s)

def spade_tail(A, px, py, c, tip, s=1.0):
    A.f(f"M{px},{py}C{px+14*s},{py+6*s} {px+26*s},{py} {px+30*s},{py-12*s}L{px+33*s},{py-10*s}C{px+30*s},{py+4*s} {px+16*s},{py+10*s} {px},{py+6*s}Z", c)
    A.f(f"M{px+30*s},{py-12*s}L{px+28*s},{py-21*s}L{px+38*s},{py-15*s}L{px+34*s},{py-8*s}Z", tip)

def horns(A, G, c, size=1.0, sweep=1.0):
    k = G.k; y0 = G.hy - G.hry * 0.7
    for sx in (-1, 1):
        x0 = 50 + sx * 9 * k
        A.f(f"M{x0},{y0+2*k}C{x0 + sx*2*k},{y0 - 8*size*k} {x0 + sx*(7+8*sweep)*k},{y0 - 14*size*k} {x0 + sx*(11+12*sweep)*k},{y0 - 16*size*k}C{x0 + sx*(9+6*sweep)*k},{y0 - 9*size*k} {x0 + sx*8*k},{y0 - 3*size*k} {x0 + sx*7*k},{y0+3*k}Z", c)

def dragon(stage):
    G = Geo(stage); A = Art()
    if stage == 1:
        main, dark, light, accent = '#D7263D', '#8E1B2A', '#FFD59E', '#FFC145'
        shadow(A); dragon_wings(A, 0.5, dark, '#6B1220', by=70)
        spade_tail(A, 62, 84, main, accent, 0.85)
        body(A, G, main, light, dark)
        for y in (72, 77, 82): A.st(f"M44,{y}Q50,{y+2} 56,{y}", '#E8B97A', 0.9)
        horns(A, G, accent, 0.6, 0.2)
        head(A, G, main); muzzle(A, G, light, w=12, h=7.5)
        for x in (44, 50, 56): A.f(f"M{x-2.6},29.5L{x},24L{x+2.6},29.5Z", dark)
        headband(A, G); eyes(A, G)
        A.e(47.5, 56, 0.9, 0.8, INK); A.e(52.5, 56, 0.9, 0.8, INK); smile(A, G, 3.5); cheeks(A, G)
    elif stage == 2:
        main, dark, light, accent = '#C81D3A', '#5E0D1B', '#FFC46B', '#FFC145'
        shadow(A, 26); dragon_wings(A, 0.95, '#8E1B2A', '#4A0A15', by=62, spread=10)
        spade_tail(A, 60, 80, main, accent, 1.1)
        body(A, G, main, light, dark)
        for y in (63, 68, 73, 78): A.st(f"M43,{y}Q50,{y+2.4} 57,{y}", '#D9963A', 1.2)
        for (x, y) in G.hands:
            for d in (-2.4, 0, 2.4): A.f(f"M{x+d-0.9},{y+3}L{x+d},{y+6}L{x+d+0.9},{y+3}Z", WHITE)
        horns(A, G, accent, 1.2, 1.0)
        head(A, G, main)
        for x in (44, 50, 56): A.f(f"M{x-2.4},{G.hy-15.5}L{x},{G.hy-22 if x==50 else G.hy-20}L{x+2.4},{G.hy-15.5}Z", dark)
        A.e(50, G.ny + 1, 11 * G.k + 1, 7.5 * G.k, light)  # longer snout
        A.e(47.4, G.ny - 0.4, 0.9, 0.8, INK); A.e(52.6, G.ny - 0.4, 0.9, 0.8, INK)
        headband(A, G, tails=1.3); eyes(A, G, 'sharp'); grin(A, G, 4, 1.5)
        gem(A, 50, 58, 3, accent)
    else:
        main, dark, light, accent = '#5E0F1E', '#2E0710', '#F4C542', '#FFE08A'
        shadow(A, 30); aura(A, '#FF6B35', 54)
        dragon_wings(A, 1.3, '#B3122E', '#2E0710', by=56, spread=10)
        spade_tail(A, 58, 76, main, light, 1.25)
        body(A, G, main, light, dark)
        for y in (52, 58, 64, 70): A.st(f"M41,{y}Q50,{y+3} 59,{y}", '#B8860B', 1.4)
        A.e(50, 58, 6.5, 6.5, '#FF8A3D', 0.6); A.e(50, 58, 4.2, 4.2, accent); A.e(50, 58, 2.2, 2.2, WHITE)
        for (x, y) in G.hands:
            for d in (-3, 0, 3): A.f(f"M{x+d-1.1},{y+3.5}L{x+d},{y+7.5}L{x+d+1.1},{y+3.5}Z", light)
        for x in (40, 60): wisp(A, x, 86, 3, '#FF8A3D', '#FFE08A')
        horns(A, G, light, 1.7, 1.4)
        head(A, G, main)
        for x in (42, 50, 58): A.f(f"M{x-2.4},{G.hy-14.5}L{x},{G.hy-24 if x==50 else G.hy-20}L{x+2.4},{G.hy-14.5}Z", '#B3122E')
        A.e(50, G.ny + 1.2, 10.5, 7, '#7A1A2A')
        A.e(47.2, G.ny - 0.2, 1, 0.8, INK); A.e(52.8, G.ny - 0.2, 1, 0.8, INK)
        eyes(A, G, 'fierce', iris='#FFC145', lid=main); grin(A, G, 4.6, 1.5)
        for sx in (-1, 1): A.f(f"M{50+sx*17},{G.hy+2}L{50+sx*25},{G.hy-1}L{50+sx*18},{G.hy+6}Z", '#B3122E')
    return A

# ================================================================ CAT: kitten → masked ninja → two-tailed nekomata

def cat(stage):
    G = Geo(stage); A = Art()
    if stage == 1:
        main, dark, light, accent = '#3B3552', '#231F33', '#6E6690', '#B79CFF'
        shadow(A); A.st("M62,82C80,84 82,66 74,60C70,57 66,60 69,63", main, 5.5)
        body(A, G, main, light, dark)
        ears(A, G, main, '#FF9EC7', None, height=1.0)
        head(A, G, main); headband(A, G)
        eyes(A, G, 'cat', iris='#FFD34D'); tri_nose(A, G, '#FF9EC7'); cat_mouth(A, G, -1); whiskers(A, G, light, 0.8); cheeks(A, G)
    elif stage == 2:
        main, dark, light, accent = '#2A2638', '#17141F', '#4A4466', '#7A5CFF'
        shadow(A, 24)
        A.st("M60,80C84,86 92,62 82,50C78,46 72,48 74,54", main, 5)
        A.e(74.5, 53.5, 3, 3, accent)
        # a long scarf flying out behind
        A.f("M60,52C72,50 84,42 96,34L97,40C86,48 74,56 62,58Z", accent)
        A.f("M62,56C74,58 86,58 96,54L95,60C84,62 72,62 62,60Z", '#5B3FE0')
        body(A, G, main, light, dark, belly=False)
        A.f("M38,62L62,62L60,66L40,66Z", accent)  # sash
        # a shuriken
        sx, sy = 30, 70
        A.f(f"M{sx},{sy-7}L{sx+1.6},{sy-1.6}L{sx+7},{sy}L{sx+1.6},{sy+1.6}L{sx},{sy+7}L{sx-1.6},{sy+1.6}L{sx-7},{sy}L{sx-1.6},{sy-1.6}Z", '#C9D1E0')
        A.e(sx, sy, 1.4, 1.4, dark); A.e(33.5, 73, 3.9, 3.9, main)
        ears(A, G, main, '#C77DFF', None, height=1.35, spread=0.64)
        head(A, G, main)
        # ninja mask over the lower face
        k = G.k; y0 = G.ey + 3.6 * k; bot = G.hy + G.hry
        a = G.hrx * math.sqrt(1 - ((y0 - G.hy) / G.hry) ** 2)
        A.f(f"M{50-a},{y0}Q50,{y0-2.5*k} {50+a},{y0}C{50+a},{bot+2} {50-a},{bot+2} {50-a},{y0}Z", '#5B3FE0')
        A.st(f"M{50-a+2},{y0+1}Q50,{y0-1.4*k} {50+a-2},{y0+1}", '#7A5CFF', 1.2)
        headband(A, G, '#5B3FE0', '#3E28A8', tails=1.4)
        eyes(A, G, 'fiercecat', iris='#FFD34D', lid=main)
    else:
        main, dark, light, accent = '#3E3266', '#221A3D', '#5C4A8C', '#D2BFFF'
        shadow(A, 28); aura(A, '#9B6BFF', 50)
        # two great tails, each with a spirit flame
        A.st("M58,80C84,84 96,60 90,40C86,28 76,26 78,18", main, 7.5)
        A.st("M42,80C16,84 4,60 10,40C14,28 24,26 22,18", main, 7.5)
        wisp(A, 78, 13, 5, '#B79CFF', '#F0E6FF'); wisp(A, 22, 13, 5, '#B79CFF', '#F0E6FF')
        body(A, G, main, light, dark, belly=False)
        A.f("M36,46C42,54 58,54 64,46L60,56C54,60 46,60 40,56Z", '#7A66C2')  # fur ruff
        for sx in (-1, 1):
            A.st(f"M{50+sx*24},56L{50+sx*28},59", accent, 1.6); A.st(f"M{50+sx*25},62L{50+sx*29.5},64.5", accent, 1.6)
            A.st(f"M{50+sx*8},66L{50+sx*12},69", accent, 1.6)
        ears(A, G, main, '#C77DFF', None, height=1.7, spread=0.62, width=0.46, tuft='#B79CFF')
        head(A, G, main)
        hachigane(A, G, emblem=lambda A, x, y, s: A.f(f"M{x-s*0.2},{y-s}C{x-s*1.2},{y-s*0.5} {x-s*1.2},{y+s*0.8} {x-s*0.1},{y+s}C{x-s*0.6},{y+s*0.3} {x-s*0.6},{y-s*0.4} {x-s*0.2},{y-s}Z", '#5B3FE0'), plate='#C9D1E0')
        eyes(A, G, 'fiercecat', iris='#FFD34D', lid=main)
        tri_nose(A, G, '#C77DFF'); cat_mouth(A, G, -1, '#B79CFF')
        for sx in (-1, 1): A.st(f"M{50+sx*12},{G.ey+5}L{50+sx*16},{G.ey+3.2}", accent, 1.3)
        whiskers(A, G, accent, 0.8)
        wisp(A, 22, 74, 2.6, '#B79CFF', '#F0E6FF'); wisp(A, 80, 82, 2.2, '#B79CFF', '#F0E6FF')
    return A

# ================================================================ DOG: shiba pup → wind-runner shiba → stone guardian komainu

def dog(stage):
    G = Geo(stage); A = Art()
    if stage == 1:
        main, dark, light = '#E39B5A', '#9C5A2B', '#FFF4E4'
        shadow(A); A.st("M63,74C78,74 80,58 70,58C64,58 64,66 70,66", main, 6); A.e(70.5, 62.5, 2.4, 2.4, light)
        body(A, G, main, light, dark)
        ears(A, G, main, '#FFD9B8', None, height=0.85, width=0.38)
        head(A, G, main)
        A.f("M34,54C36,64 44,66 50,66C56,66 64,64 66,54C60,58 56,56 50,56C44,56 40,58 34,54Z", light)
        muzzle(A, G, light, w=9, h=6.5, dy=10)
        for sx in (-1, 1): A.e(50 + sx * 8, 42.5, 2.4, 1.5, light)
        headband(A, G); eyes(A, G); nose(A, G, w=3.4, dy=-0.3); smile(A, G, 3); cheeks(A, G)
    elif stage == 2:
        main, dark, light = '#D9853F', '#7A4420', '#FFF4E4'
        shadow(A, 25)
        for (y, x0, x1) in [(48, 6, 26), (60, 2, 24), (72, 8, 28)]: A.st(f"M{x0},{y}L{x1},{y}", WHITE, 2.2, 0.55)
        A.st("M62,72C82,72 86,50 74,48C66,46 64,58 72,58", main, 7); A.e(73, 53, 3, 3, light)
        body(A, G, main, light, dark)
        A.f("M36,52C42,58 58,58 64,52L66,57C58,64 42,64 34,57Z", BAND)
        A.f("M47,59L53,59L50,66Z", BAND2)
        ears(A, G, main, '#FFD9B8', None, height=1.2, spread=0.62, width=0.4, lean=4)
        head(A, G, main)
        k = G.k
        A.f(f"M{50-15*k},{G.hy+5*k}C{50-14*k},{G.hy+15*k} {50-6*k},{G.hy+17*k} 50,{G.hy+17*k}C{50+6*k},{G.hy+17*k} {50+14*k},{G.hy+15*k} {50+15*k},{G.hy+5*k}C{50+9*k},{G.hy+9*k} {50+5*k},{G.hy+7*k} 50,{G.hy+7*k}C{50-5*k},{G.hy+7*k} {50-9*k},{G.hy+9*k} {50-15*k},{G.hy+5*k}Z", light)
        muzzle(A, G, light, w=10, h=7, dy=10)
        for sx in (-1, 1): A.e(50 + sx * 7.5 * k, G.hy - 5 * k, 2.2 * k, 1.4 * k, light)
        headband(A, G, tails=1.4); eyes(A, G, 'sharp'); nose(A, G, w=3.4, dy=-0.3); grin(A, G, 3.6, 1, fangs=False); cheeks(A, G, 0.4)
    else:
        main, dark, light = '#8E9AA0', '#4C575D', '#C9D2D6'
        mane, mane2 = '#2FA58F', '#1D7566'
        shadow(A, 30); aura(A, '#FFC145', 52)
        # curly flame-like tail of stone curls
        for (x, y, r) in [(76, 70, 7), (82, 60, 6.5), (84, 49, 6), (80, 40, 5.5), (72, 36, 4.5)]: A.e(x, y, r, r, mane); A.e(x - r * 0.25, y + r * 0.2, r * 0.45, r * 0.45, mane2)
        body(A, G, main, light, dark, legc=main, arms=False)
        A.st("M36,47C29,52 26.5,59 26.5,66", main, 8.5); A.e(26.5, 68, 5.2, 5.2, main)
        for sx in (-1, 1):
            for y in (80, 84): A.e(50 + sx * 9.5, y, 2.6, 2.6, mane)
        # the guardian's ball
        A.e(77, 83, 7.5, 7.5, GOLD); A.st("M70.5,81C73.5,85.5 80.5,86.5 84.5,81", GOLD2, 1.4); A.e(74, 80.5, 2, 2, '#FFF2B3')
        A.st("M64,47C70,54 74,64 76,71", main, 8.5); A.e(76.5, 74, 5.4, 5.4, main)
        A.e(50, 47, 8, 4, mane)
        # curly mane all around the head
        for i in range(13):
            a = math.pi * (0.85 + i * 0.1077)
            x, y = 50 + 22 * math.cos(a), 31 + 19 * math.sin(a)
            A.e(x, y, 6, 6, mane); A.e(x + 1.2 * math.cos(a), y + 1.2 * math.sin(a), 3, 3, mane2)
        for sx in (-1, 1): A.e(50 + sx * 22, 44, 6, 6, mane); A.e(50 + sx * 21, 45, 3, 3, mane2)
        head(A, G, main)
        A.f(f"M50,{G.hy-16}L47,{G.hy-24}L50,{G.hy-28}L53,{G.hy-24}Z", GOLD)  # a single horn
        muzzle(A, G, light, w=12, h=8, dy=9)
        for sx in (-1, 1): A.st(f"M{50+sx*4},{G.ey-6}Q{50+sx*9},{G.ey-10} {50+sx*14},{G.ey-6}", mane, 2.6)
        eyes(A, G, 'fierce', iris='#FFC145', lid=main)
        nose(A, G, w=3.4, dy=-0.3); grin(A, G, 5.4, 1.2)
    return A

# ================================================================ RABBIT: mochi bun → mallet-carrying Tsukimi → moon spirit Gekkō

def rabbit_ears(A, G, main, inner, length=1.0, droop=None, tip=None):
    k = G.k
    for sx in (-1, 1):
        bx = 50 + sx * 8 * k; y = G.hy - G.hry * 0.62; L = 26 * length * k
        if droop == sx:
            A.f(f"M{bx-5*k},{y}C{bx-6*k},{y-L*0.6} {bx+sx*2*k},{y-L*0.8} {bx+sx*10*k},{y-L*0.7}C{bx+sx*14*k},{y-L*0.55} {bx+sx*8*k},{y-L*0.45} {bx+5*k},{y}Z", main)
            A.f(f"M{bx-2.4*k},{y-1}C{bx-3*k},{y-L*0.5} {bx+sx*2*k},{y-L*0.66} {bx+sx*8*k},{y-L*0.62}C{bx+sx*9*k},{y-L*0.52} {bx+sx*5*k},{y-L*0.45} {bx+2.4*k},{y-1}Z", inner)
            continue
        A.f(f"M{bx-5*k},{y}C{bx-7*k},{y-L*0.62} {bx+sx*2*k-4*k},{y-L} {bx+sx*2*k},{y-L}C{bx+sx*2*k+4*k},{y-L} {bx+7*k},{y-L*0.62} {bx+5*k},{y}Z", main)
        A.f(f"M{bx-2.4*k},{y-1}C{bx-3.6*k},{y-L*0.55} {bx+sx*2*k-2*k},{y-L*0.85} {bx+sx*2*k},{y-L*0.85}C{bx+sx*2*k+2*k},{y-L*0.85} {bx+3.6*k},{y-L*0.55} {bx+2.4*k},{y-1}Z", inner)
        if tip: A.f(f"M{bx+sx*2*k-3.4*k},{y-L*0.82}C{bx+sx*2*k-2.6*k},{y-L*0.98} {bx+sx*2*k+2.6*k},{y-L*0.98} {bx+sx*2*k+3.4*k},{y-L*0.82}Q{bx+sx*2*k},{y-L*0.76} {bx+sx*2*k-3.4*k},{y-L*0.82}Z", tip)

def rabbit(stage):
    G = Geo(stage); A = Art()
    if stage == 1:
        main, dark, light = '#F5F7FF', '#B9C2D9', '#FFFFFF'
        shadow(A); A.e(66, 84, 6, 6, light)
        body(A, G, main, light, dark)
        rabbit_ears(A, G, main, '#FFC6DA', 1.0)
        head(A, G, main); headband(A, G)
        eyes(A, G); tri_nose(A, G, '#FF8FB8'); cat_mouth(A, G, -1.3); cheeks(A, G)
    elif stage == 2:
        main, dark, light, vest = '#F5F7FF', '#9AA6C4', '#FFFFFF', '#3D4F9F'
        shadow(A, 24); A.e(64, 82, 6, 6, light)
        body(A, G, main, light, dark, belly=False, torso=vest)
        A.f("M44,53L50,64L56,53L56,82L44,82Z", main, 1)
        A.st("M44,53L50,64L56,53", WHITE, 1.4)
        A.f("M36,70L64,70L63,74L37,74Z", '#E63946')
        A.st("M34,73L80,26", '#C79A4A', 3)
        A.rot(45, 80, 26); A.f("M72,19L88,19C90,19 90,33 88,33L72,33C70,33 70,19 72,19Z", '#A87B3A'); A.st("M74.5,19L74.5,33", '#8A6430', 1.2); A.st("M85.5,19L85.5,33", '#8A6430', 1.2); A.end()
        A.e(33.5, 73, 3.9, 3.9, main)
        rabbit_ears(A, G, main, '#FFC6DA', 1.3, droop=-1)
        head(A, G, main); headband(A, G, tails=1.3)
        eyes(A, G, 'sharp'); tri_nose(A, G, '#FF8FB8'); cat_mouth(A, G, -1.3); cheeks(A, G, 0.45)
    else:
        main, dark, light, accent = '#E6ECFF', '#8F9BD6', '#FFFFFF', '#FFD166'
        shadow(A, 18)
        # crescent moon behind
        A.f("M50,4C22,4 6,28 10,52C14,76 36,90 58,86C36,80 24,62 26,44C28,24 40,10 50,4Z", accent)
        A.e(50, 46, 44, 44, '#B9C4FF', 0.12)
        for (x, y, s) in [(82, 14, 3.2), (90, 34, 2.2), (78, 62, 2.6), (16, 84, 2), (88, 76, 2)]: star(A, x, y, s, '#FFF2B3')
        # floating: a flowing robe instead of feet
        A.f("M36,66C34,76 30,84 34,88C40,86 44,90 50,88C56,90 60,86 66,88C70,84 66,76 64,66Z", '#C9D1FF')
        A.st("M34,88C40,86 44,90 50,88C56,90 60,86 66,88", '#8F9BD6', 1.2)
        body(A, G, main, light, dark, legs=False)
        # celestial ribbon
        A.st("M18,70C24,52 40,62 50,56C60,50 74,60 84,44C88,38 92,40 94,44", '#D6B3FF', 3)
        rabbit_ears(A, G, main, '#D9C9FF', 1.55, tip=accent)
        head(A, G, main)
        A.f(f"M48,{G.hy-12}C44,{G.hy-10} 44,{G.hy-4} 48,{G.hy-2}C46,{G.hy-5} 46,{G.hy-9} 50,{G.hy-11}C49.4,{G.hy-11.6} 48.8,{G.hy-12} 48,{G.hy-12}Z", '#7A6BFF')
        eyes(A, G, 'fierce', iris='#9B8CFF', lid=main); tri_nose(A, G, '#FF8FB8'); cat_mouth(A, G, -1.3)
        for sx in (-1, 1): A.st(f"M{50+sx*11},{G.ey+4.6}L{50+sx*15},{G.ey+3.2}", '#9B8CFF', 1.4)
    return A

# ================================================================ EAGLE: fluffy chick → peregrine falcon → thunder eagle

def eagle(stage):
    A = Art()
    if stage == 1:
        main, dark, light = '#E8D3A9', '#B8995F', '#FFF6E0'
        shadow(A)
        for sx in (-1, 1): A.f(f"M{50+sx*17},68C{50+sx*29},68 {50+sx*29},80 {50+sx*19},86Z", dark)
        A.f("M45,49C44,41 49,40 50,47C50,40 56,40 55,49Z", dark)
        A.e(50, 69, 24, 23, main); A.e(50, 76, 14, 12, light)
        A.e(42, 91.5, 4.5, 2.4, '#FFC23D'); A.e(58, 91.5, 4.5, 2.4, '#FFC23D')
        G = Geo(1); G.hy, G.hrx, G.hry = 69, 24, 23
        headband(A, G)
        for sx in (-1, 1): x = 50 + sx * 9; A.e(x, 66, 4, 5, INK); A.e(x + 1.3, 64.3, 1.5, 1.5, WHITE)
        A.f("M45.5,70.5L54.5,70.5L50,76.5Z", '#FFC23D'); A.e(35, 73, 3.8, 2.4, BLUSH, 0.55); A.e(65, 73, 3.8, 2.4, BLUSH, 0.55)
        return A
    G = Geo(stage)
    if stage == 2:
        slate, slate2, cream = '#56677D', '#34404F', '#F2EDE4'
        shadow(A, 22)
        A.f("M44,84L50,94L56,84Z", slate2)
        # folded pointed wings
        for sx in (-1, 1): A.f(f"M{50+sx*11},56C{50+sx*24},58 {50+sx*24},76 {50+sx*14},90C{50+sx*12},80 {50+sx*10},70 {50+sx*8},64Z", slate2)
        A.e(50, 69, 13.5, 16, slate)
        A.e(50, 71, 9, 12.5, cream)
        for y in (64, 69, 74, 79):
            for dx in (-4, 0, 4): A.st(f"M{50+dx-1.6},{y}L{50+dx},{y+1.4}L{50+dx+1.6},{y}", slate, 1.0)
        A.e(43, 90.5, 4.8, 2.6, '#FFC23D'); A.e(57, 90.5, 4.8, 2.6, '#FFC23D')
        head(A, G, slate2)
        A.f(f"M{50-17},{G.hy+3}C{50-16},{G.hy+16} {50+16},{G.hy+16} {50+17},{G.hy+3}C{50+12},{G.hy+6} {50+11},{G.hy+12} 50,{G.hy+12}C{50-11},{G.hy+12} {50-12},{G.hy+6} {50-17},{G.hy+3}Z", cream)
        for sx in (-1, 1): A.f(f"M{50+sx*7},{G.ey+3}C{50+sx*6},{G.ey+8} {50+sx*8},{G.ey+12} {50+sx*10},{G.ey+12}C{50+sx*11},{G.ey+9} {50+sx*10},{G.ey+5} {50+sx*9.5},{G.ey+3}Z", slate2)
        headband(A, G, tails=1.4)
        for sx in (-1, 1): A.e(50 + sx * G.edx, G.ey, 4.4, 4.8, '#FFC23D')
        eyes(A, G, 'sharp')
        A.f(f"M45.5,{G.ny-1.5}C45.5,{G.ny-4} 54.5,{G.ny-4} 54.5,{G.ny-1.5}C54.5,{G.ny+2} 52,{G.ny+4.5} 50,{G.ny+6}C49,{G.ny+3} 45.5,{G.ny+1} 45.5,{G.ny-1.5}Z", '#FFC23D')
        A.f(f"M50,{G.ny+6}C51,{G.ny+4} 52.5,{G.ny+2.5} 53.5,{G.ny+1}C53,{G.ny+3.5} 52,{G.ny+5} 50,{G.ny+6}Z", INK)
        return A
    main, dark, gold = '#6B3F1F', '#3D220F', '#F4C542'
    shadow(A, 30); aura(A, gold, 50)
    for sx in (-1, 1):
        A.f(f"M{50+sx*12},52C{50+sx*28},34 {50+sx*44},30 {50+sx*48},34C{50+sx*46},40 {50+sx*48},44 {50+sx*44},48"
            f"C{50+sx*46},52 {50+sx*44},56 {50+sx*40},58C{50+sx*42},62 {50+sx*38},66 {50+sx*34},66"
            f"C{50+sx*34},70 {50+sx*28},72 {50+sx*24},70C{50+sx*20},70 {50+sx*16},68 {50+sx*12},64Z", dark)
        for (x0, y0, x1, y1) in [(46, 34, 30, 46), (44, 48, 28, 54), (38, 60, 24, 60)]:
            A.st(f"M{50+sx*x0},{y0}L{50+sx*x1},{y1}", main, 2.2)
        bolt(A, 50 + sx * 30, 40, 6, gold)
    A.f("M42,80L50,94L58,80Z", dark); A.f("M46,80L50,90L54,80Z", '#F5F1E8')
    body(A, G, main, '#A8774F', dark, arms=False, legc='#FFC23D')
    A.st("M36,48C32,54 30,60 32,66", main, 8); A.st("M64,48C68,54 70,60 68,66", main, 8)
    for x0 in (40, 60):
        for d in (-3, 0, 3): A.f(f"M{x0+d-1},{91}L{x0+d},{94}L{x0+d+1},{91}Z", INK)
    # golden crest feathers
    for (ang, L) in [(-24, 14), (0, 17), (24, 14)]:
        A.rot(ang, 50, G.hy - 10)
        A.f(f"M47.5,{G.hy-10}C47,{G.hy-10-L*0.6} 49,{G.hy-10-L} 50,{G.hy-10-L}C51,{G.hy-10-L} 53,{G.hy-10-L*0.6} 52.5,{G.hy-10}Z", gold)
        A.end()
    head(A, G, '#F5F1E8')
    A.f(f"M{50-17},{G.hy+4}C{50-14},{G.hy+16} {50+14},{G.hy+16} {50+17},{G.hy+4}C{50+12},{G.hy+12} {50-12},{G.hy+12} {50-17},{G.hy+4}Z", '#DCD4C4')
    hachigane(A, G, emblem=lambda A, x, y, s: bolt(A, x, y, s * 1.1, NAVY))
    eyes(A, G, 'fierce', iris='#FFC23D', lid='#F5F1E8')
    A.f(f"M45,{G.ny-1.5}C45,{G.ny-4.5} 55,{G.ny-4.5} 55,{G.ny-1.5}C55,{G.ny+3} 52,{G.ny+6} 50,{G.ny+8}C49,{G.ny+4} 45,{G.ny+2} 45,{G.ny-1.5}Z", '#FFC23D')
    A.f(f"M50,{G.ny+8}C51.5,{G.ny+5} 53,{G.ny+3.5} 54.2,{G.ny+1.5}C53.6,{G.ny+4.5} 52.4,{G.ny+6.5} 50,{G.ny+8}Z", '#C98E1C')
    return A

ANIMALS = [
    ('Hinoko', fox), ('Shizuku', otter), ('Konoha', tanuki), ('Kaen', dragon),
    ('Kuro', cat), ('Pochi', dog), ('Mochi', rabbit), ('Hina', eagle),
]
NAMES = {'Hinoko': ['Hinoko', 'Kagaribi', 'Homura'], 'Shizuku': ['Shizuku', 'Seseragi', 'Ōnami'], 'Konoha': ['Konoha', 'Bunbuku', 'Mamedanuki'],
         'Kaen': ['Kaen', 'Enryū', 'Gōka'], 'Kuro': ['Kuro', 'Shinobi', 'Nekomata'], 'Pochi': ['Pochi', 'Hayate', 'Komainu'],
         'Mochi': ['Mochi', 'Tsukimi', 'Gekkō'], 'Hina': ['Hina', 'Hayabusa', 'Ōwashi']}
SCALE = {1: 0.82, 2: 0.93, 3: 1.0}

def to_svg_group(A, stage):
    out = []
    for sh in A.s:
        if sh[0] == 'g': out.append(f'<g transform="rotate({sh[1]} {sh[2]} {sh[3]})">')
        elif sh[0] == '/g': out.append('</g>')
        elif sh[0] == 'e': cx, cy, rx, ry = sh[1]; out.append(f'<ellipse cx="{cx}" cy="{cy}" rx="{rx}" ry="{ry}" fill="{sh[2]}" fill-opacity="{sh[3]}"/>')
        elif sh[0] == 'f': out.append(f'<path d="{sh[1]}" fill="{sh[2]}" fill-opacity="{sh[3]}"/>')
        elif sh[4] > 0: out.append(f'<path d="{sh[1]}" fill="none" stroke="{sh[2]}" stroke-opacity="{sh[3]}" stroke-width="{sh[4]}" stroke-linecap="round" stroke-linejoin="round"/>')
    s = SCALE[stage]
    return f'<g transform="translate(50,93) scale({s}) translate(-50,-93)">{"".join(out)}</g>'

def sheet(path, bg='#181614', cell=150, only=None):
    rows = [a for a in ANIMALS if not only or a[0] in only]
    parts = []
    for r, (n, fn) in enumerate(rows):
        for st in (1, 2, 3):
            g = to_svg_group(fn(st), st)
            x = (st - 1) * cell; y = r * cell
            parts.append(f'<g transform="translate({x},{y}) scale({cell/100})"><rect width="100" height="100" fill="{bg}" stroke="#333" stroke-width="0.3"/>{g}<text x="50" y="99" font-size="5.5" fill="#bbb" text-anchor="middle" font-family="sans-serif">{NAMES[n][st-1]}</text></g>')
    W = cell * 3; H = cell * len(rows)
    open(path, 'w').write(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}"><rect width="{W}" height="{H}" fill="#0E0D0C"/>{"".join(parts)}</svg>')

if __name__ == '__main__':
    import sys
    sheet('/tmp/p2/a.svg', only=['Hinoko', 'Shizuku', 'Konoha', 'Kaen'])
    sheet('/tmp/p2/b.svg', only=['Kuro', 'Pochi', 'Mochi', 'Hina'])
    print('ok')
