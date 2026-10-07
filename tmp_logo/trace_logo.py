"""Trace Moonraker favicon bright pixels into Android vector path data."""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw

fav = Image.open(Path(r"G:\Gits\MoonrakerAndroid\tmp_logo\favicon.png")).convert("RGBA")
w, h = fav.size
px = fav.load()

mask = [[0] * w for _ in range(h)]
for y in range(h):
    for x in range(w):
        r, g, b, a = px[x, y]
        if a > 200 and (r + g + b) / 3 > 200:
            mask[y][x] = 1


def map_full(x: float, y: float) -> tuple[float, float]:
    return x * (108 / 48), y * (108 / 48)


def segments(y: int) -> list[tuple[int, int]]:
    segs: list[tuple[int, int]] = []
    x = 0
    while x < w:
        if not mask[y][x]:
            x += 1
            continue
        start = x
        while x < w and mask[y][x]:
            x += 1
        segs.append((start, x - 1))
    return segs


# Circle from y 10..17
circle_pts = [(x, y) for y in range(10, 18) for x in range(w) if mask[y][x]]
cx = sum(p[0] for p in circle_pts) / len(circle_pts) + 0.5
cy = sum(p[1] for p in circle_pts) / len(circle_pts) + 0.5
r = math.sqrt(len(circle_pts) / math.pi)
CX, CY = map_full(cx, cy)
R = r * (108 / 48)

# Body outline with top notch:
# walk left outer top->bottom, bottom, right outer bottom->top,
# then right-top-inner into notch and out left-top-inner.
left_outer: list[tuple[float, float]] = []
right_outer: list[tuple[float, float]] = []
notch_right_inner: list[tuple[float, float]] = []
notch_left_inner: list[tuple[float, float]] = []

for y in range(18, 37):
    segs = segments(y)
    if not segs:
        continue
    if len(segs) == 1:
        a, b = segs[0]
        left_outer.append((a + 0.5, y + 0.5))
        right_outer.append((b + 0.5, y + 0.5))
    else:
        # two wings
        (a1, b1), (a2, b2) = segs[0], segs[-1]
        left_outer.append((a1 + 0.5, y + 0.5))
        right_outer.append((b2 + 0.5, y + 0.5))
        notch_left_inner.append((b1 + 0.5, y + 0.5))
        notch_right_inner.append((a2 + 0.5, y + 0.5))

# Polygon order
poly: list[tuple[float, float]] = []
poly.extend(left_outer)
poly.extend(reversed(right_outer))
# notch: from right wing top-inner down then reverse left-inner
if notch_right_inner:
    poly.extend(reversed(notch_right_inner))
if notch_left_inner:
    poly.extend(notch_left_inner)

parts: list[str] = []
for i, (x, y) in enumerate(poly):
    X, Y = map_full(x, y)
    parts.append(f"{'M' if i == 0 else 'L'}{X:.2f},{Y:.2f}")
body_path = " ".join(parts) + " Z"
print("CIRCLE", f"M{CX - R:.2f},{CY:.2f}a{R:.2f},{R:.2f} 0 1 1 {2 * R:.2f},0a{R:.2f},{R:.2f} 0 1 1 {-2 * R:.2f},0")
print("BODY")
print(body_path)

# Preview render
preview = Image.new("RGBA", (432, 432), (26, 26, 26, 255))
draw = ImageDraw.Draw(preview)
scale = 432 / 108


def P(x: float, y: float) -> tuple[float, float]:
    return x * scale, y * scale


draw.ellipse(
    [P(CX - R, CY - R), P(CX + R, CY + R)],
    fill=(255, 255, 255, 255),
)
draw.polygon([P(*map_full(x, y)) for x, y in poly], fill=(255, 255, 255, 255))
preview.save(Path(r"G:\Gits\MoonrakerAndroid\tmp_logo\preview_traced.png"))
print("saved preview_traced.png")
