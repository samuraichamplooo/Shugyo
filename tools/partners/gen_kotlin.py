import os, sys; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from animals import ANIMALS, SCALE

def num(v):
    s = f"{v:.2f}".rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s

forms = []
for name, fn in ANIMALS:
    for stage in (1, 2, 3):
        A = fn(stage)
        rot = (0, 50, 93)
        lines = []
        for sh in A.s:
            if sh[0] == 'g': rot = (sh[1], sh[2], sh[3]); continue
            if sh[0] == '/g': rot = (0, 50, 93); continue
            if sh[0] == 'mark': continue
            kind, data, color, alpha, width = sh
            c = color.lstrip('#').upper()
            r = f"{num(rot[0])};{num(rot[1])};{num(rot[2])}"
            if kind == 'e':
                d = ",".join(num(v) for v in data)
            else:
                d = data
            assert ';' not in d and '$' not in d
            lines.append(f"{kind};{c};{num(alpha)};{num(width)};{r};{d}")
        forms.append((SCALE[stage], "\n".join(lines)))

out = ['package dev.shugyo.game', '',
       '// Made by tools/partners/animals.py — edit the drawings there, then run gen_kotlin.py.',
       '// Each line: kind (e oval, f fill, s stroke); colour; alpha; stroke width; rotation°; pivot x; pivot y; data.',
       '// Ovals: cx,cy,rx,ry. Others: SVG path data. Everything sits in a 100×100 box standing on y = 93.',
       '',
       'internal object PartnerData {',
       '    /** Three stages for each partner, in order. */',
       '    val scale = floatArrayOf(' + ", ".join(num(s) + 'f' for s, _ in forms) + ')',
       '',
       '    val forms = arrayOf(']
for i, (s, body) in enumerate(forms):
    out.append(f'        // {ANIMALS[i // 3][0]} stage {i % 3 + 1}')
    out.append('        """' + body + '""",')
out.append('    )')
out.append('}')
open(sys.argv[1], 'w').write("\n".join(out) + "\n")
print(len(forms), 'forms,', sum(len(b) for _, b in forms), 'chars; biggest', max(len(b) for _, b in forms))
