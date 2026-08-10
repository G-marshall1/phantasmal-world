#!/usr/bin/env python3
"""Builds the per-tool icon strip (pso-tool-icons.png), one cell per ToolType in
Kotlin declaration order plus a technique disk and a trade-trophy cell at the end.

Cells prefer the game's real item-reader plates (the hex icons the wiki hosts,
mirrored under wiki-item-icons/) -- the same art PSO's own item reader shows.
The wiki carries no plates for the materials, grinders, disks or trade trophies,
so those cells fall back to pixel glyphs drawn in the HUD sheet's style.

Strip cells are 64px for the plates' sake; the runtime's toolIconStyle scales by
CSS background-size, so the Kotlin side needs no knowledge of the source size.
"""
import os
from PIL import Image, ImageDraw

CELL = 16
OUT_CELL = 64
WIKI_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "wiki-item-icons")


def rgba(c):
    return tuple(c[:3]) + (255,)


def shade(c, f):
    return tuple(min(255, max(0, int(v * f))) for v in c[:3])


def px(d, x, y, c):
    if 0 <= x < CELL and 0 <= y < CELL:
        d.point((x, y), rgba(c))


def rect(d, x0, y0, x1, y1, c):
    d.rectangle((x0, y0, x1, y1), fill=rgba(c))


def outline_rect(d, x0, y0, x1, y1, c):
    d.rectangle((x0, y0, x1, y1), outline=rgba(shade(c, 0.45)))


def canister(d, body, pips):
    """A medic canister: flat cap, cylindrical body, tier pips."""
    rect(d, 5, 2, 10, 3, shade(body, 0.55))          # cap
    rect(d, 4, 4, 11, 13, body)                       # body
    rect(d, 4, 4, 4, 13, shade(body, 0.7))            # left shade
    rect(d, 11, 4, 11, 13, shade(body, 0.55))         # right shade
    rect(d, 6, 5, 6, 12, shade(body, 1.45))           # highlight
    rect(d, 4, 8, 11, 9, (240, 240, 240))             # label band
    start = 8 - pips  # centre 1..3 pips on the label
    for i in range(pips):
        rect(d, start + i * 2, 8, start + i * 2, 9, shade(body, 0.5))
    outline_rect(d, 4, 4, 11, 13, body)


def flask(d, body, pips):
    """A TP flask: narrow neck, round-shouldered bulb."""
    rect(d, 6, 1, 9, 2, shade(body, 0.55))            # stopper
    rect(d, 7, 3, 8, 5, shade(body, 0.8))             # neck
    rect(d, 5, 6, 10, 7, body)                        # shoulders
    rect(d, 4, 8, 11, 13, body)                       # bulb
    rect(d, 4, 8, 4, 13, shade(body, 0.7))
    rect(d, 11, 8, 11, 13, shade(body, 0.55))
    rect(d, 6, 9, 6, 12, shade(body, 1.5))            # glass shine
    start = 8 - pips
    for i in range(pips):
        rect(d, start + i * 2, 11, start + i * 2, 12, (240, 240, 240))
    outline_rect(d, 4, 8, 11, 13, body)


def cure_vial(d, body):
    """A status-cure vial marked with the white cross."""
    rect(d, 6, 1, 9, 2, shade(body, 0.55))
    rect(d, 5, 3, 10, 13, body)
    rect(d, 5, 3, 5, 13, shade(body, 0.7))
    rect(d, 10, 3, 10, 13, shade(body, 0.55))
    rect(d, 7, 5, 8, 11, (250, 250, 250))             # cross vertical
    rect(d, 5, 7, 10, 9, (250, 250, 250))             # cross horizontal
    outline_rect(d, 5, 3, 10, 13, body)


def sun(d):
    c = (255, 150, 40)
    rect(d, 6, 6, 9, 9, c)
    rect(d, 5, 7, 10, 8, c)
    rect(d, 7, 7, 8, 8, (255, 230, 150))
    for x0, y0, x1, y1 in ((7, 1, 8, 3), (7, 12, 8, 14), (1, 7, 3, 8), (12, 7, 14, 8)):
        rect(d, x0, y0, x1, y1, c)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12), (4, 4), (11, 4), (4, 11), (11, 11)):
        px(d, x, y, c)


def crescent(d):
    c = (190, 215, 255)
    d.ellipse((2, 2, 13, 13), fill=rgba(c))
    d.ellipse((5, 1, 15, 11), fill=(0, 0, 0, 0))
    px(d, 4, 4, shade(c, 1.2))


def star(d):
    c = (255, 225, 90)
    d.polygon([(7.5, 0), (9, 6), (15, 7.5), (9, 9), (7.5, 15), (6, 9), (0, 7.5), (6, 6)], fill=rgba(c))
    rect(d, 7, 7, 8, 8, (255, 250, 220))


def telepipe(d):
    c = (90, 230, 130)
    d.ellipse((2, 11, 13, 15), fill=rgba(shade(c, 0.55)))   # base ring
    rect(d, 6, 1, 9, 12, c)                           # the pipe column
    rect(d, 6, 1, 6, 12, shade(c, 0.7))
    rect(d, 8, 2, 8, 11, shade(c, 1.4))               # inner glow
    d.ellipse((5, 0, 10, 2), fill=rgba(shade(c, 1.5)))


def doll(d):
    c = (255, 150, 190)
    d.ellipse((5, 1, 10, 6), fill=rgba(c))          # head
    d.polygon([(7.5, 6), (3, 14), (12, 14)], fill=rgba(c))  # gown
    px(d, 6, 3, (60, 40, 50))
    px(d, 9, 3, (60, 40, 50))
    rect(d, 7, 9, 8, 10, (255, 230, 240))


def gem(d, c):
    d.polygon([(7.5, 1), (13, 7.5), (7.5, 14), (2, 7.5)], fill=rgba(c))
    d.polygon([(7.5, 1), (13, 7.5), (2, 7.5)], fill=rgba(shade(c, 1.35)))
    px(d, 6, 4, shade(c, 1.8))
    px(d, 7, 3, shade(c, 1.8))


def gear(d, pips):
    c = (170, 175, 185)
    for x0, y0, x1, y1 in ((6, 1, 9, 3), (6, 10, 9, 12), (1, 5, 3, 8), (12, 5, 14, 8)):
        rect(d, x0, y0, x1, y1, shade(c, 0.8))
    d.ellipse((3, 2, 12, 11), fill=rgba(c))
    d.ellipse((5, 4, 10, 9), fill=rgba(shade(c, 0.4)))
    # Tier dots sit in the dark hub, where they read against the steel.
    start = 8 - pips
    for i in range(pips):
        rect(d, start + i * 2, 6, start + i * 2, 7, (255, 220, 90))


def disk(d):
    c = (170, 110, 240)
    d.ellipse((1, 2, 14, 13), fill=rgba(c))
    d.ellipse((6, 6, 9, 9), fill=(25, 20, 35, 255))
    d.arc((2, 3, 13, 12), 200, 290, fill=rgba(shade(c, 1.6)))


def wing(d):
    c = (255, 200, 110)
    for i, (x, y, l) in enumerate(((3, 3, 11), (5, 6, 8), (7, 9, 5))):
        d.polygon([(x, y), (x + l, y + 1), (x + 1, y + 3)], fill=rgba(shade(c, 1.0 - i * 0.15)))
    px(d, 3, 3, (255, 240, 200))


MATE = (95, 210, 110)
FLUID = (85, 150, 250)

# (wiki plate slug or None, drawn fallback) per cell, in ToolType declaration order,
# then the technique disk and trade-trophy cells.
CELLS = [
    ("monomate", None), ("dimate", None), ("trimate", None),
    ("monofluid", None), ("difluid", None), ("trifluid", None),
    ("antidote", None), ("antiparalysis", None),
    ("sol", None), ("moon", None), ("star", None),
    ("telepipe", None), ("scapedoll", None),
    (None, lambda d: gem(d, (225, 70, 70))),          # Power
    (None, lambda d: gem(d, (165, 85, 230))),         # Mind
    (None, lambda d: gem(d, (70, 200, 100))),         # HP
    (None, lambda d: gem(d, (60, 205, 190))),         # Evade
    (None, lambda d: gem(d, (70, 115, 230))),         # Def
    (None, lambda d: gem(d, (235, 205, 60))),         # Luck
    (None, lambda d: gem(d, (70, 190, 235))),         # TP
    (None, lambda d: gear(d, 1)), (None, lambda d: gear(d, 2)), (None, lambda d: gear(d, 3)),
    (None, disk), (None, wing),
]


def plate_cell(slug):
    """A wiki item-reader plate, alpha-trimmed and centred in an OUT_CELL square."""
    img = Image.open(os.path.join(WIKI_DIR, f"{slug}.png")).convert("RGBA")
    img = img.crop(img.getbbox())
    scale = (OUT_CELL - 2) / max(img.size)
    img = img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)
    cell = Image.new("RGBA", (OUT_CELL, OUT_CELL), (0, 0, 0, 0))
    cell.alpha_composite(img, ((OUT_CELL - img.width) // 2, (OUT_CELL - img.height) // 2))
    return cell


def drawn_cell(fn):
    """A 16px drawn glyph, upscaled nearest so it keeps its pixel-art edge."""
    cell = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
    fn(ImageDraw.Draw(cell))
    return cell.resize((OUT_CELL, OUT_CELL), Image.NEAREST)


strip = Image.new("RGBA", (OUT_CELL * len(CELLS), OUT_CELL), (0, 0, 0, 0))
for i, (slug, fn) in enumerate(CELLS):
    cell = plate_cell(slug) if slug else drawn_cell(fn)
    strip.alpha_composite(cell, (i * OUT_CELL, 0))

OUT = "web/mobileGame/src/jsMain/resources/assets/hud/pso-tool-icons.png"
strip.save(OUT)
print("cells:", len(CELLS), "->", OUT)

# Preview at 24px cells on the HUD's dark ground for visual inspection.
prev = Image.new("RGBA", (strip.width, OUT_CELL), (24, 28, 38, 255))
prev.alpha_composite(strip)
prev = prev.resize((len(CELLS) * 48, 48), Image.LANCZOS)
prev.save("preview_tool_icons.png")
