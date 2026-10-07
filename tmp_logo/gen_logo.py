from PIL import Image, ImageDraw
from pathlib import Path

ROOT = Path(r"G:\Gits\MoonrakerAndroid")
TMP = ROOT / "tmp_logo"
RES = ROOT / "app" / "src" / "main" / "res"


def draw_logo(size: int, pad_ratio: float = 0.18) -> Image.Image:
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    pad = size * pad_ratio
    scale = (size - 2 * pad) / 48.0

    def T(x: float, y: float):
        return (pad + x * scale, pad + y * scale)

    # Dot
    cx, cy, r = 24.0, 13.5, 3.7
    d.ellipse([*T(cx - r, cy - r), *T(cx + r, cy + r)], fill=(255, 255, 255, 255))

    # Body: open-book / winged mark tapering to a point
    body = [
        (11.5, 18.0),
        (11.5, 28.0),
        (13.0, 32.5),
        (17.5, 35.0),
        (24.0, 36.5),
        (30.5, 35.0),
        (35.0, 32.5),
        (36.5, 28.0),
        (36.5, 18.0),
        (31.0, 18.0),
        (28.5, 20.5),
        (26.0, 22.0),
        (24.0, 22.8),
        (22.0, 22.0),
        (19.5, 20.5),
        (17.0, 18.0),
    ]
    d.polygon([T(x, y) for x, y in body], fill=(255, 255, 255, 255))
    return im


def main() -> None:
    TMP.mkdir(parents=True, exist_ok=True)

    fg = draw_logo(432, pad_ratio=0.22)
    fg.save(TMP / "fg_432.png")

    preview = Image.new("RGBA", (512, 512), (26, 26, 26, 255))
    preview = Image.alpha_composite(preview, draw_logo(512, pad_ratio=0.20))
    preview.save(TMP / "preview.png")

    # Density PNGs for launcher foreground fallback / drawable
    densities = {
        "mdpi": 108,
        "hdpi": 162,
        "xhdpi": 216,
        "xxhdpi": 324,
        "xxxhdpi": 432,
    }
    for dens, size in densities.items():
        folder = RES / f"mipmap-{dens}"
        folder.mkdir(parents=True, exist_ok=True)
        draw_logo(size, pad_ratio=0.22).save(folder / "ic_launcher_foreground.png")

        # Full legacy launcher icon (circle on dark)
        legacy = Image.new("RGBA", (size, size), (26, 26, 26, 255))
        # Use larger pad so glyph fits circle
        mark = draw_logo(size, pad_ratio=0.20)
        Image.alpha_composite(legacy, mark).save(folder / "ic_launcher.png")

    # Vector-friendly header PNG in drawable
    (RES / "drawable").mkdir(parents=True, exist_ok=True)
    draw_logo(128, pad_ratio=0.06).save(RES / "drawable" / "ic_moonraker.png")
    print("generated")


if __name__ == "__main__":
    main()
