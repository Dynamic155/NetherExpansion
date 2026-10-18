"""Generates the Underworld Watcher's GeckoLib model, animations and textures.

Run from the project root:  python art/gen_underworld_watcher.py

Outputs (under src/main/resources/assets/netherexpansion):
  geckolib/models/entity/underworld_watcher.geo.json
  geckolib/animations/entity/underworld_watcher.animation.json
  textures/entity/underworld_watcher[_<eye>].png and matching _glowmask.png files
  textures/item/underworld_watcher_spawn_egg.png (recolored from the vanilla enderman egg)

The model faces -Z. Units are pixels (16 = one block); the Watcher is 51 px (about 3.2 blocks) tall.
"""
import glob
import json
import os
import random
import struct
import sys
import zlib
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/netherexpansion"
SEED = 3203
TEX = 64

# ---------------------------------------------------------------------------
# Model: bones (name, parent, pivot, rest rotation) and cubes (bone, origin, size, material)
# ---------------------------------------------------------------------------

BONES = [
    ("root", None, [0, 0, 0], None),
    ("body", "root", [0, 24, 0], None),
    ("chest", "body", [0, 33, 0], None),
    ("head", "chest", [0, 43, 0], None),
    ("eye", "head", [0, 47.5, -3.4], None),
    # The mask halves hinge at their outer edges; the renderer swings them open during the Unveiling,
    # revealing three smaller eyes (hidden until then)
    ("mask_left", "head", [-3, 47.5, -3.5], None),
    ("mask_right", "head", [3, 47.5, -3.5], None),
    ("eyes_split", "head", [0, 47, -2.9], None),
    # Crooked shoulders: the right one sits higher and tilts out, the left one droops.
    ("right_arm", "chest", [5, 40, 0], [0, 0, -4]),
    ("right_forearm", "right_arm", [5, 28, 0], None),
    ("right_hand", "right_forearm", [5, 17, 0], None),
    ("left_arm", "chest", [-5, 39, 0], [0, 0, 7]),
    ("left_forearm", "left_arm", [-5, 27, 0], None),
    ("left_hand", "left_forearm", [-5, 16, 0], None),
    ("right_leg", "root", [2.5, 24, 0], None),
    ("right_shin", "right_leg", [2.5, 13, 0], None),
    ("left_leg", "root", [-2.5, 24, 0], None),
    ("left_shin", "left_leg", [-2.5, 13, 0], None),
]

CUBES = [
    # Legs: long and thin, feet barely touching the ground
    ("right_leg", [1, 13, -1.5], [3, 11, 3], "stone"),
    ("right_leg", [1, 11, -2.5], [3, 2, 1], "basalt"),
    ("right_shin", [1.5, 1, -1], [2, 12, 2], "bone"),
    ("right_shin", [1, 0, -3.5], [3, 1, 5], "stone"),
    ("left_leg", [-4, 13, -1.5], [3, 11, 3], "stone"),
    ("left_leg", [-4, 11, -2.5], [3, 2, 1], "basalt"),
    ("left_shin", [-3.5, 1, -1], [2, 12, 2], "bone"),
    ("left_shin", [-4, 0, -3.5], [3, 1, 5], "stone"),
    # Pelvis and spine
    ("body", [-3, 24, -1.5], [6, 3, 3], "bone"),
    ("body", [-2, 27, -1.5], [4, 6, 3], "stone"),
    ("body", [-0.5, 28, 1.5], [1, 4, 1], "bone"),
    # Narrow chest with an off-center basalt plate and spine ridges
    ("chest", [-4, 33, -2], [8, 8, 4], "stone"),
    ("chest", [-3, 35, -3], [5, 5, 1], "basalt"),
    ("chest", [-0.5, 34, 2], [1, 2, 1], "bone"),
    ("chest", [-0.5, 37, 2], [1, 2, 1], "bone"),
    ("chest", [3, 40, -2.5], [4, 2, 5], "basalt"),
    ("chest", [-6, 39, -2.5], [3, 2, 5], "basalt"),
    ("chest", [-1, 41, -1], [2, 2, 2], "bone"),
    # Head: charred skull behind a cracked obsidian mask with one vertical eye
    ("head", [-3, 43, -2.5], [6, 8, 5], "stone"),
    ("mask_left", [-3, 44, -3.5], [3, 7, 1], "mask"),
    ("mask_right", [0, 44, -3.5], [3, 7, 1], "mask"),
    ("eye", [-0.5, 46, -3.9], [1, 3, 1], "eye"),
    ("eyes_split", [-0.5, 48, -2.9], [1, 1, 1], "eye"),
    ("eyes_split", [-2, 46, -2.9], [1, 1, 1], "eye"),
    ("eyes_split", [1, 46, -2.9], [1, 1, 1], "eye"),
    # Right arm, reaching almost to the knee, with elongated fingers
    ("right_arm", [4, 28, -1], [2, 12, 2], "stone"),
    ("right_forearm", [4, 17, -1], [2, 11, 2], "bone"),
    ("right_forearm", [4.5, 25, 1], [1, 2, 1], "basalt"),
    ("right_hand", [3.5, 14, -1], [3, 3, 2], "stone"),
    ("right_hand", [3.5, 9, -1.5], [1, 5, 1], "bone"),
    ("right_hand", [4.5, 8, -1.5], [1, 6, 1], "bone"),
    ("right_hand", [5.5, 9, -1.5], [1, 5, 1], "bone"),
    ("right_hand", [4.5, 11, 0], [1, 3, 1], "bone"),
    # Left arm, hanging slightly lower
    ("left_arm", [-6, 27, -1], [2, 12, 2], "stone"),
    ("left_forearm", [-6, 16, -1], [2, 11, 2], "bone"),
    ("left_forearm", [-5.5, 24, 1], [1, 2, 1], "basalt"),
    ("left_hand", [-6.5, 13, -1], [3, 3, 2], "stone"),
    ("left_hand", [-6.5, 8, -1.5], [1, 5, 1], "bone"),
    ("left_hand", [-5.5, 7, -1.5], [1, 6, 1], "bone"),
    ("left_hand", [-4.5, 8, -1.5], [1, 5, 1], "bone"),
    ("left_hand", [-5.5, 10, 0], [1, 3, 1], "bone"),
]


def net_size(size):
    w, h, d = size
    return 2 * (w + d), d + h


def pack(cubes, width):
    """Shelf-packs each cube's box UV net. Returns a list of (u, v) offsets in cube order."""
    order = sorted(range(len(cubes)), key=lambda i: -net_size(cubes[i][2])[1])
    offsets, x, y, shelf = [None] * len(cubes), 0, 0, 0
    for i in order:
        nw, nh = net_size(cubes[i][2])
        if x + nw > width:
            x, y, shelf = 0, y + shelf, 0
        offsets[i] = (x, y)
        x, shelf = x + nw, max(shelf, nh)
    assert y + shelf <= width, f"UV layout needs {y + shelf}px, texture is {width}px"
    return offsets


def face_rects(uv, size):
    """Box UV face rectangles (x, y, w, h) for a cube, Bedrock layout."""
    (u, v), (w, h, d) = uv, size
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + d + w + d, v + d, w, h),
    }


def geometry(offsets):
    bones = []
    for name, parent, pivot, rotation in BONES:
        bone = {"name": name, "pivot": pivot}
        if parent:
            bone["parent"] = parent
        if rotation:
            bone["rotation"] = rotation
        cubes = [{"origin": c[1], "size": c[2], "uv": list(offsets[i])}
                 for i, c in enumerate(CUBES) if c[0] == name]
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.underworld_watcher",
                "texture_width": TEX,
                "texture_height": TEX,
                "visible_bounds_width": 3,
                "visible_bounds_height": 4,
                "visible_bounds_offset": [0, 1.75, 0],
            },
            "bones": bones,
        }],
    }

# ---------------------------------------------------------------------------
# Textures
# ---------------------------------------------------------------------------


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


STONE = [hex_rgb(c) for c in ("#121010", "#171413", "#1b1817", "#201c1a", "#25201d")]
BASALT = [hex_rgb(c) for c in ("#26262b", "#2d2d33", "#34343b", "#3c3c44")]
BASALT_EDGE = hex_rgb("#4a4a54")
BONE = [hex_rgb(c) for c in ("#2a241f", "#322b25", "#3a322a", "#433a30")]
MASK = [hex_rgb(c) for c in ("#0c0814", "#120c1e", "#181028", "#1f1534")]
MASK_SHEEN = hex_rgb("#33215a")
MASK_CRACK = hex_rgb("#030205")
EMBER_DARK = hex_rgb("#4a1808")
EMBER_GLOW = hex_rgb("#7a2c0e")
MASK_GLINT = hex_rgb("#3d1f66")

# Eye colors per behavior: (edge, core) in the base texture and the glowmask
EYES = {
    "": {"base": ("#a8480f", "#f08a2c"), "glow": ("#7a3008", "#c0601a")},           # dim orange: watching
    "_hunting": {"base": ("#e0201a", "#ff7a66"), "glow": ("#ff2418", "#ff9a88")},   # bright red: hunting
    "_charging": {"base": ("#b996f0", "#efe2ff"), "glow": ("#c4a2ff", "#f6eeff")},  # pale violet: strongest attack
    "_flash": {"base": ("#f2f0ff", "#ffffff"), "glow": ("#f6f4ff", "#ffffff")},     # white: Dread Pulse release
    "_dark": {"base": ("#1a1212", "#241818"), "glow": None},                        # dead
}


def paint(offsets):
    rng = random.Random(SEED)
    base = [[(0, 0, 0, 0)] * TEX for _ in range(TEX)]
    glow = [[(0, 0, 0, 0)] * TEX for _ in range(TEX)]
    eye_pixels = []  # (x, y, is_core)

    def put(grid, x, y, rgb):
        grid[y][x] = (*rgb, 255)

    for i, (_, _, size, material) in enumerate(CUBES):
        for face, (fx, fy, fw, fh) in face_rects(offsets[i], size).items():
            for y in range(fy, fy + fh):
                for x in range(fx, fx + fw):
                    lx, ly = x - fx, y - fy
                    if material == "stone":
                        c = rng.choice(STONE)
                    elif material == "basalt":
                        # Vertical column striations, lighter top edge
                        c = BASALT[(lx * 7 + rng.randint(0, 1)) % len(BASALT)]
                        if ly == 0 and face not in ("up", "down"):
                            c = BASALT_EDGE
                    elif material == "bone":
                        c = BONE[min(len(BONE) - 1, int(rng.random() * len(BONE)))]
                        if rng.random() < 0.08:
                            c = STONE[0]
                    elif material == "mask":
                        c = rng.choice(MASK)
                        if rng.random() < 0.05:
                            c = MASK_SHEEN
                    else:  # eye
                        core = face == "north" and ly == fh // 2
                        eye_pixels.append((x, y, core))
                        c = (0, 0, 0)
                    put(base, x, y, c)

            # Faint ember cracks on larger stone faces
            if material == "stone" and fw * fh >= 12 and rng.random() < 0.55:
                x, y = fx + rng.randrange(fw), fy + rng.randrange(fh)
                for _ in range(rng.randint(2, 4)):
                    put(base, x, y, EMBER_DARK)
                    put(glow, x, y, EMBER_GLOW)
                    x = min(fx + fw - 1, max(fx, x + rng.choice((-1, 0, 1))))
                    y = min(fy + fh - 1, max(fy, y + rng.choice((1, 1, 0))))

            # Cracks across the mask's face, with a couple of violet glints
            if material == "mask" and face == "north":
                for start in ((fx + 1, fy), (fx + fw - 2, fy + 1)):
                    x, y = start
                    while fy <= y < fy + fh and fx <= x < fx + fw:
                        put(base, x, y, MASK_CRACK)
                        x += rng.choice((-1, 0, 1))
                        y += 1
                for _ in range(2):
                    gx, gy = fx + rng.randrange(fw), fy + rng.randrange(fh)
                    put(glow, gx, gy, MASK_GLINT)
    return base, glow, eye_pixels


def eye_variant(base, glow, eye_pixels, colors):
    b = [row[:] for row in base]
    g = [row[:] for row in glow]
    edge, core = (hex_rgb(c) for c in colors["base"])
    for x, y, is_core in eye_pixels:
        b[y][x] = (*(core if is_core else edge), 255)
    if colors["glow"]:
        gedge, gcore = (hex_rgb(c) for c in colors["glow"])
        for x, y, is_core in eye_pixels:
            g[y][x] = (*(gcore if is_core else gedge), 255)
    return b, g


def read_png(data):
    """Decode a PNG into rows of (r, g, b, a). Supports the formats Minecraft textures use."""
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
    pos, idat, palette, trns = 8, b"", None, None
    while pos < len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            width, height, depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert interlace == 0, "interlaced PNGs are not supported"
        elif kind == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif kind == b"tRNS":
            trns = body
        elif kind == b"IDAT":
            idat += body
        pos += 12 + length

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color_type]
    bits_per_pixel = channels * depth
    stride = (width * bits_per_pixel + 7) // 8
    step = max(1, bits_per_pixel // 8)
    raw = zlib.decompress(idat)

    rows, previous = [], bytearray(stride)
    for y in range(height):
        start = y * (stride + 1)
        kind, line = raw[start], bytearray(raw[start + 1:start + 1 + stride])
        for i in range(stride):
            a = line[i - step] if i >= step else 0
            b = previous[i]
            c = previous[i - step] if i >= step else 0
            if kind == 1:
                line[i] = (line[i] + a) & 0xFF
            elif kind == 2:
                line[i] = (line[i] + b) & 0xFF
            elif kind == 3:
                line[i] = (line[i] + (a + b) // 2) & 0xFF
            elif kind == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 0xFF
        previous = line

        if depth < 8:
            per_byte = 8 // depth
            mask = (1 << depth) - 1
            values = [(line[x // per_byte] >> (8 - depth * (x % per_byte + 1))) & mask for x in range(width)]
        else:
            values = None

        row = []
        for x in range(width):
            if color_type == 3:
                index = values[x] if values else line[x]
                alpha = trns[index] if trns and index < len(trns) else 255
                row.append((*palette[index], alpha))
            elif color_type == 6:
                row.append(tuple(line[x * 4:x * 4 + 4]))
            elif color_type == 2:
                rgb = tuple(line[x * 3:x * 3 + 3])
                # tRNS names one exact color that is fully transparent.
                transparent = trns and rgb == (trns[1], trns[3], trns[5])
                row.append((*rgb, 0 if transparent else 255))
            elif color_type == 4:
                g, alpha = line[x * 2], line[x * 2 + 1]
                row.append((g, g, g, alpha))
            else:
                sample = values[x] if values else line[x]
                g = sample * (255 // ((1 << depth) - 1)) if values else sample
                # tRNS names one exact gray value that is fully transparent.
                transparent = trns and sample == struct.unpack(">H", trns[:2])[0]
                row.append((g, g, g, 0 if transparent else 255))
        rows.append(row)
    return rows


def write_png(path, pixels):
    height, width = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + bytes(c for px in row for c in px) for row in pixels)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def spawn_egg():
    """Recolors the vanilla enderman egg: charcoal shell with ember spots."""
    jars = glob.glob(os.path.expanduser(
        "~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.3/*.jar"))
    assert jars, "Minecraft 26.3 jar not found; run a Gradle build first"
    with ZipFile(jars[0]) as jar:
        egg = read_png(jar.read("assets/minecraft/textures/item/enderman_spawn_egg.png"))
    out = []
    for row in egg:
        new = []
        for r, g, b, a in row:
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255
            spot = max(r, g, b) - min(r, g, b) > 60
            if a == 0:
                new.append((0, 0, 0, 0))
            elif spot:
                new.append((int(120 + 135 * lum), int(40 + 70 * lum), int(10 + 20 * lum), a))
            else:
                v = int(18 + 60 * lum)
                new.append((v, int(v * 0.92), int(v * 0.88), a))
        out.append(new)
    return out

# ---------------------------------------------------------------------------
# Particle sprites (8x8). Rows: "." clear, letters index the palette.
# ---------------------------------------------------------------------------

PARTICLE_SPRITES = {
    # Black ash flakes: irregular, with a faintly lighter edge
    "watcher_ash": ({"a": "#141110", "b": "#221d1a", "c": "#3a322c"}, [
        ["........", "...cb...", "..baab..", "..aaab..", "...aa...", "........", "........", "........"],
        ["........", "........", ".cbb....", "..aaab..", "...aaa..", "....ab..", "........", "........"],
        ["........", "....c...", "...ab...", "..aaa...", ".baa....", "..b.....", "........", "........"],
    ]),
    # Embers: a hot core that shrinks frame by frame
    "watcher_ember": ({"a": "#fff0b0", "b": "#ffb040", "c": "#ff6418", "d": "#b8280a"}, [
        ["........", "...dd...", "..dccd..", ".dcbbcd.", ".dcbacd.", "..dccd..", "...dd...", "........"],
        ["........", "........", "...cd...", "..cbbd..", "..dbac..", "...dc...", "........", "........"],
        ["........", "........", "........", "...cb...", "...bc...", "........", "........", "........"],
        ["........", "........", "........", "...d....", "....c...", "........", "........", "........"],
    ]),
    # Void motes: soft violet points that fade out
    "void_mote": ({"a": "#f4e6ff", "b": "#c48cff", "c": "#7a3ad0", "d": "#3d1a70"}, [
        ["........", "........", "...cc...", "..cbbc..", "..cbac..", "...cc...", "........", "........"],
        ["........", "........", "...d....", "..cbc...", "...bad..", "....d...", "........", "........"],
        ["........", "........", "........", "...cb...", "...dc...", "........", "........", "........"],
        ["........", "........", "........", "....d...", "...d....", "........", "........", "........"],
    ]),
    # Obsidian shards: angular, with a violet glint on one edge
    "obsidian_shard": ({"a": "#0c0814", "b": "#181028", "c": "#3a2560", "d": "#6a48a8"}, [
        ["........", "....d...", "...cb...", "..cbb...", "..bba...", ".bbaa...", "..aa....", "........"],
        ["........", ".dc.....", "..cbb...", "...bbb..", "....baa.", ".....aa.", "........", "........"],
        ["........", "........", "..dcbb..", ".cbbaa..", "..baaa..", "...a....", "........", "........"],
    ]),
    # Eye light: a white-violet spark for the Unveiling and the death
    "eye_light": ({"a": "#ffffff", "b": "#e8d8ff", "c": "#a070f0"}, [
        ["...c....", "...b....", "...b....", "cbbabbc.", "...b....", "...b....", "...c....", "........"],
        ["........", "...c....", "...b....", ".cbabc..", "...b....", "...c....", "........", "........"],
        ["........", "........", "...c....", "..cac...", "...c....", "........", "........", "........"],
        ["........", "........", "........", "...b....", "........", "........", "........", "........"],
    ]),
}


def particle_sprites():
    for name, (palette, frames) in PARTICLE_SPRITES.items():
        colors = {k: hex_rgb(v) for k, v in palette.items()}
        textures = []
        for i, rows in enumerate(frames):
            pixels = [[(*colors[ch], 255) if ch != "." else (0, 0, 0, 0) for ch in row] for row in rows]
            write_png(ASSETS / f"textures/particle/{name}_{i}.png", pixels)
            textures.append(f"netherexpansion:{name}_{i}")
        write_json(ASSETS / f"particles/{name}.json", {"textures": textures})

# ---------------------------------------------------------------------------
# Animations (Bedrock format; positive X rotation pitches forward, the model faces -Z)
# ---------------------------------------------------------------------------


def keys(*frames):
    """frames: (time, [x, y, z]) pairs -> Bedrock keyframe dict."""
    return {f"{t:.4f}".rstrip("0").rstrip(".") if t else "0.0": v for t, v in frames}


def cycle(length, values):
    """Evenly spaced keyframes over one loop; the first value is repeated at the end."""
    step = length / len(values)
    return keys(*[(i * step, v) for i, v in enumerate(values)], (length, values[0]))


def animations():
    a = {}
    a["idle"] = {
        "loop": True, "animation_length": 4.0,
        "bones": {
            "chest": {"rotation": cycle(4.0, [[0, 0, 0], [1.5, 0, 0]])},
            "head": {"rotation": cycle(4.0, [[0, -2, 0], [0, 2, 0]])},
            # The eye drifts on its own while the head stays still
            "eye": {"rotation": cycle(4.0, [[0, -10, 0], [3, 0, 0], [0, 10, 0], [-3, 0, 0]])},
            # An occasional finger twitch
            "right_hand": {"rotation": keys((0, [0, 0, 0]), (2.5, [0, 0, 0]), (2.55, [8, 0, 0]),
                                            (2.7, [0, 0, 0]), (4.0, [0, 0, 0]))},
        },
    }
    a["tilt"] = {
        "loop": "hold_on_last_frame", "animation_length": 1.2,
        "bones": {
            "head": {"rotation": keys((0, [0, 0, 0]), (1.2, [4, 0, -24]))},
            "chest": {"rotation": keys((0, [0, 0, 0]), (1.2, [5, 0, 2]))},
        },
    }
    a["watched"] = {
        "loop": True, "animation_length": 3.0,
        "bones": {
            "head": {"rotation": cycle(3.0, [[4, 0, -24], [4, 0, -26]])},
            "chest": {"rotation": cycle(3.0, [[5, 0, 2], [5.5, 0, 2]])},
            "eye": {"scale": cycle(3.0, [[1, 1, 1], [1, 1.15, 1]])},
        },
    }
    a["walk"] = {
        "loop": True, "animation_length": 1.6,
        "bones": {
            "body": {"position": cycle(1.6, [[0, 0.5, 0], [0, 1.5, 0], [0, 0.5, 0], [0, 1.5, 0]]),
                     "rotation": cycle(1.6, [[6, 0, 0], [6, 0, 0]])},
            "head": {"rotation": cycle(1.6, [[-6, 0, 0], [-6, 0, 0]])},
            "right_leg": {"rotation": cycle(1.6, [[-28, 0, 0], [28, 0, 0]])},
            "right_shin": {"rotation": cycle(1.6, [[10, 0, 0], [35, 0, 0], [5, 0, 0], [5, 0, 0]])},
            "left_leg": {"rotation": cycle(1.6, [[28, 0, 0], [-28, 0, 0]])},
            "left_shin": {"rotation": cycle(1.6, [[5, 0, 0], [5, 0, 0], [10, 0, 0], [35, 0, 0]])},
            "right_arm": {"rotation": cycle(1.6, [[8, 0, 0], [-8, 0, 0]])},
            "left_arm": {"rotation": cycle(1.6, [[-8, 0, 0], [8, 0, 0]])},
        },
    }
    # On all fours with jerky, spider-like steps: poses snap between holds
    a["sprint"] = {
        "loop": True, "animation_length": 0.6,
        "bones": {
            "body": {"rotation": keys((0, [70, 0, 0]), (0.15, [70, 0, 0]), (0.18, [74, 3, 0]),
                                      (0.3, [70, 0, 0]), (0.45, [70, 0, 0]), (0.48, [74, -3, 0]), (0.6, [70, 0, 0]))},
            "head": {"rotation": keys((0, [-62, 0, 0]), (0.6, [-62, 0, 0]))},
            "right_arm": {"rotation": keys((0, [-105, 0, 0]), (0.1, [-105, 0, 0]), (0.15, [-55, 0, 0]),
                                           (0.4, [-55, 0, 0]), (0.45, [-105, 0, 0]), (0.6, [-105, 0, 0]))},
            "left_arm": {"rotation": keys((0, [-55, 0, 0]), (0.1, [-55, 0, 0]), (0.15, [-105, 0, 0]),
                                          (0.4, [-105, 0, 0]), (0.45, [-55, 0, 0]), (0.6, [-55, 0, 0]))},
            "right_forearm": {"rotation": keys((0, [-15, 0, 0]), (0.6, [-15, 0, 0]))},
            "left_forearm": {"rotation": keys((0, [-15, 0, 0]), (0.6, [-15, 0, 0]))},
            "right_leg": {"rotation": cycle(0.6, [[-35, 0, 0], [35, 0, 0]])},
            "right_shin": {"rotation": cycle(0.6, [[20, 0, 0], [50, 0, 0], [10, 0, 0], [10, 0, 0]])},
            "left_leg": {"rotation": cycle(0.6, [[35, 0, 0], [-35, 0, 0]])},
            "left_shin": {"rotation": cycle(0.6, [[10, 0, 0], [10, 0, 0], [20, 0, 0], [50, 0, 0]])},
        },
    }
    # Rift Claw: wind up (0 to 0.45s), pause, slash at 0.6s (the hit lands on tick 12), recover
    a["claw"] = {
        "animation_length": 1.0,
        "bones": {
            "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.45, [40, 0, 30]), (0.55, [42, 0, 32]),
                                           (0.62, [-110, -40, -10]), (0.8, [-100, -35, -10]), (1.0, [0, 0, 0]))},
            "right_forearm": {"rotation": keys((0, [0, 0, 0]), (0.45, [-35, 0, 0]), (0.62, [-10, 0, 0]), (1.0, [0, 0, 0]))},
            "chest": {"rotation": keys((0, [0, 0, 0]), (0.45, [-4, 25, 0]), (0.55, [-4, 27, 0]),
                                       (0.62, [12, -30, 0]), (0.8, [10, -26, 0]), (1.0, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (0.45, [0, -20, 0]), (0.62, [0, 22, 0]), (1.0, [0, 0, 0]))},
        },
    }
    # Nether Rift: stretch tall and thin, then collapse into the ground (and the reverse)
    a["rift_out"] = {
        "loop": "hold_on_last_frame", "animation_length": 0.75,
        "bones": {
            "root": {
                "scale": keys((0, [1, 1, 1]), (0.3, [0.6, 1.5, 0.6]), (0.5, [0.15, 1.8, 0.15]), (0.75, [0.02, 0.02, 0.02])),
                "position": keys((0, [0, 0, 0]), (0.5, [0, 0, 0]), (0.75, [0, -12, 0])),
            },
        },
    }
    a["rift_in"] = {
        "animation_length": 0.75,
        "bones": {
            "root": {
                "scale": keys((0, [0.02, 0.02, 0.02]), (0.25, [0.15, 1.8, 0.15]), (0.45, [0.6, 1.5, 0.6]), (0.75, [1, 1, 1])),
                "position": keys((0, [0, -12, 0]), (0.25, [0, 0, 0]), (0.75, [0, 0, 0])),
            },
        },
    }
    # Dread Pulse: arch back while the eye swells with light (0 to 1.0s), hold through the white flash,
    # snap forward on the scream at 1.3s (tick 26), recover
    a["dread_pulse"] = {
        "animation_length": 2.25,
        "bones": {
            "chest": {"rotation": keys((0, [0, 0, 0]), (1.0, [-28, 0, 0]), (1.3, [-31, 0, 0]), (1.38, [32, 0, 0]),
                                       (1.6, [28, 0, 0]), (2.25, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (1.0, [-35, 0, 0]), (1.3, [-38, 0, 0]), (1.38, [25, 0, 0]),
                                      (1.6, [22, 0, 0]), (2.25, [0, 0, 0]))},
            "right_arm": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 0, 40]), (1.3, [0, 0, 42]), (1.38, [-30, 0, 10]),
                                           (1.6, [-28, 0, 10]), (2.25, [0, 0, 0]))},
            "left_arm": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 0, -40]), (1.3, [0, 0, -42]), (1.38, [-30, 0, -10]),
                                          (1.6, [-28, 0, -10]), (2.25, [0, 0, 0]))},
            "eye": {"scale": keys((0, [1, 1, 1]), (1.0, [1.9, 1.9, 1.9]), (1.3, [2.1, 2.1, 2.1]), (1.38, [1, 1, 1]),
                                  (2.25, [1, 1, 1]))},
        },
    }
    # Grasp of the Deep: raise both arms (0 to 0.65s), slam them into the ground at 0.75s (tick 15),
    # hold the crouch while the cracks glow and the tendrils erupt, recover
    a["grasp"] = {
        "animation_length": 1.6,
        "bones": {
            "body": {"rotation": keys((0, [0, 0, 0]), (0.55, [-10, 0, 0]), (0.65, [-12, 0, 0]), (0.75, [55, 0, 0]),
                                      (1.3, [55, 0, 0]), (1.6, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (0.75, [-45, 0, 0]), (1.3, [-45, 0, 0]), (1.6, [0, 0, 0]))},
            "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.55, [-165, 0, -15]), (0.65, [-170, 0, -15]),
                                           (0.75, [-75, 0, -8]), (1.3, [-75, 0, -8]), (1.6, [0, 0, 0]))},
            "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.55, [-165, 0, 15]), (0.65, [-170, 0, 15]),
                                          (0.75, [-75, 0, 8]), (1.3, [-75, 0, 8]), (1.6, [0, 0, 0]))},
            "right_leg": {"rotation": keys((0, [0, 0, 0]), (0.75, [-30, 0, 0]), (1.3, [-30, 0, 0]), (1.6, [0, 0, 0]))},
            "right_shin": {"rotation": keys((0, [0, 0, 0]), (0.75, [50, 0, 0]), (1.3, [50, 0, 0]), (1.6, [0, 0, 0]))},
            "left_leg": {"rotation": keys((0, [0, 0, 0]), (0.75, [-30, 0, 0]), (1.3, [-30, 0, 0]), (1.6, [0, 0, 0]))},
            "left_shin": {"rotation": keys((0, [0, 0, 0]), (0.75, [50, 0, 0]), (1.3, [50, 0, 0]), (1.6, [0, 0, 0]))},
            "root": {"position": keys((0, [0, 0, 0]), (0.75, [0, -3, 0]), (1.3, [0, -3, 0]), (1.6, [0, 0, 0]))},
        },
    }
    # The Unveiling (4s): stands upright and still, tilts its head back as the mask cracks (renderer opens it
    # from 1.0 to 2.0s), trembles, then jerks forward when the eye splits at 2.0s
    a["unveil"] = {
        "animation_length": 4.0,
        "bones": {
            "body": {"rotation": keys((0, [0, 0, 0]), (0.6, [-6, 0, 0]), (3.6, [-6, 0, 0]), (4.0, [0, 0, 0]))},
            "chest": {"rotation": keys((0, [0, 0, 0]), (0.6, [-5, 0, 0]), (3.6, [-5, 0, 0]), (4.0, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (1.0, [-18, 0, 0]), (1.2, [-18, 0, 3]), (1.4, [-18, 0, -3]),
                                      (1.6, [-18, 0, 3]), (1.8, [-18, 0, -3]), (2.0, [-18, 0, 0]), (2.08, [12, 0, 0]),
                                      (2.4, [4, 0, 0]), (4.0, [0, 0, 0]))},
            "right_arm": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 0, 18]), (2.0, [0, 0, 20]), (2.08, [-10, 0, 35]),
                                           (3.6, [-5, 0, 25]), (4.0, [0, 0, 0]))},
            "left_arm": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 0, -18]), (2.0, [0, 0, -20]), (2.08, [-10, 0, -35]),
                                          (3.6, [-5, 0, -25]), (4.0, [0, 0, 0]))},
        },
    }
    # False Sight (1.5s): arms out, the body shivers as if splitting apart (copies appear at 0.75s)
    a["false_sight"] = {
        "animation_length": 1.5,
        "bones": {
            "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, 4]), (0.4, [0, 0, -4]), (0.5, [0, 0, 5]),
                                      (0.6, [0, 0, -5]), (0.7, [0, 0, 6]), (0.78, [0, 0, -6]), (0.9, [0, 0, 0]), (1.5, [0, 0, 0]))},
            "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 0, 60]), (0.9, [0, 0, 62]), (1.5, [0, 0, 0]))},
            "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 0, -60]), (0.9, [0, 0, -62]), (1.5, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [-10, 0, 0]), (0.9, [-10, 0, 0]), (1.5, [0, 0, 0]))},
        },
    }
    # World Rend (2.2s): right claw overhead, then raked down through the ground at 0.7s (tick 14), hold
    a["world_rend"] = {
        "animation_length": 2.2,
        "bones": {
            "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-170, 0, -10]), (0.6, [-172, 0, -10]),
                                           (0.7, [-25, 0, -5]), (1.5, [-25, 0, -5]), (2.2, [0, 0, 0]))},
            "body": {"rotation": keys((0, [0, 0, 0]), (0.5, [-12, 0, 0]), (0.7, [42, 0, 0]), (1.5, [42, 0, 0]), (2.2, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (0.7, [-35, 0, 0]), (1.5, [-35, 0, 0]), (2.2, [0, 0, 0]))},
            "right_leg": {"rotation": keys((0, [0, 0, 0]), (0.7, [-25, 0, 0]), (1.5, [-25, 0, 0]), (2.2, [0, 0, 0]))},
            "right_shin": {"rotation": keys((0, [0, 0, 0]), (0.7, [40, 0, 0]), (1.5, [40, 0, 0]), (2.2, [0, 0, 0]))},
            "root": {"position": keys((0, [0, 0, 0]), (0.7, [0, -2, 0]), (1.5, [0, -2, 0]), (2.2, [0, 0, 0]))},
        },
    }
    # Unblinking (loops while charging): perfectly still and upright, staring; only a faint tremble
    a["unblinking"] = {
        "loop": True, "animation_length": 0.5,
        "bones": {
            "chest": {"rotation": keys((0, [-4, 0, 0]), (0.25, [-4, 0, 0.6]), (0.5, [-4, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 0, 0]))},
            "right_arm": {"rotation": keys((0, [15, 0, 12]), (0.5, [15, 0, 12]))},
            "left_arm": {"rotation": keys((0, [15, 0, -12]), (0.5, [15, 0, -12]))},
            "eyes_split": {"scale": keys((0, [1.2, 1.2, 1.2]), (0.25, [1.45, 1.45, 1.45]), (0.5, [1.2, 1.2, 1.2]))},
        },
    }
    a["unblinking_fire"] = {
        "animation_length": 0.6,
        "bones": {
            "head": {"rotation": keys((0, [0, 0, 0]), (0.05, [-16, 0, 0]), (0.3, [-10, 0, 0]), (0.6, [0, 0, 0]))},
            "chest": {"rotation": keys((0, [0, 0, 0]), (0.05, [-8, 0, 0]), (0.6, [0, 0, 0]))},
        },
    }
    # Death: freeze, eye goes dark (texture), then slump and sink while shards break off (particles)
    # Death (4.5s, ticks in brackets): the veil tears with violent spasms (0 to 1.5s, [0-30]), it arches back
    # with arms flung wide as light breaks out of it (1.5 to 2.5s, [30-50]), shatters at 2.5s [50], then what
    # is left crumples to its knees and sinks into the ground
    a["death"] = {
        "loop": "hold_on_last_frame", "animation_length": 4.5,
        "bones": {
            "head": {"rotation": keys((0, [0, 0, 0]), (0.1, [-25, 0, 15]), (0.25, [10, 0, -20]), (0.4, [-30, 0, 8]),
                                      (0.6, [5, 0, -25]), (0.8, [-20, 0, 20]), (1.0, [-10, 0, -10]), (1.2, [-28, 0, 12]),
                                      (1.5, [-40, 0, 0]), (2.5, [-48, 0, 0]), (2.7, [35, 0, 15]), (4.5, [40, 0, 18]))},
            "chest": {"rotation": keys((0, [0, 0, 0]), (0.15, [-12, 6, 0]), (0.35, [8, -8, 0]), (0.55, [-10, 10, 0]),
                                       (0.8, [6, -6, 0]), (1.1, [-8, 4, 0]), (1.5, [-25, 0, 0]), (2.5, [-30, 0, 0]),
                                       (2.7, [30, 0, -6]), (4.5, [35, 0, -8]))},
            "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.2, [-40, 0, 30]), (0.45, [10, 0, 5]), (0.7, [-60, 0, 40]),
                                           (1.0, [0, 0, 15]), (1.5, [-20, 0, 80]), (2.5, [-25, 0, 85]),
                                           (2.7, [15, 0, 10]), (4.5, [20, 0, 12]))},
            "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.25, [-50, 0, -35]), (0.5, [5, 0, -5]), (0.75, [-45, 0, -45]),
                                          (1.05, [0, 0, -15]), (1.5, [-20, 0, -80]), (2.5, [-25, 0, -85]),
                                          (2.7, [12, 0, -12]), (4.5, [18, 0, -14]))},
            "right_leg": {"rotation": keys((0, [0, 0, 0]), (2.5, [0, 0, 0]), (2.9, [-70, 0, 0]), (4.5, [-75, 0, 0]))},
            "right_shin": {"rotation": keys((0, [0, 0, 0]), (2.5, [0, 0, 0]), (2.9, [90, 0, 0]), (4.5, [95, 0, 0]))},
            "left_leg": {"rotation": keys((0, [0, 0, 0]), (2.5, [0, 0, 0]), (2.9, [-70, 0, 0]), (4.5, [-75, 0, 0]))},
            "left_shin": {"rotation": keys((0, [0, 0, 0]), (2.5, [0, 0, 0]), (2.9, [90, 0, 0]), (4.5, [95, 0, 0]))},
            "eyes_split": {"scale": keys((0, [1, 1, 1]), (1.5, [1.4, 1.4, 1.4]), (2.5, [2.2, 2.2, 2.2]), (2.55, [0, 0, 0]))},
            "eye": {"scale": keys((0, [1, 1, 1]), (1.5, [1.4, 1.4, 1.4]), (2.5, [2.2, 2.2, 2.2]), (2.55, [0, 0, 0]))},
            "root": {
                "position": keys((0, [0, 0, 0]), (2.5, [0, 0, 0]), (2.9, [0, -10, 0]), (4.5, [0, -34, 0])),
                "scale": keys((0, [1, 1, 1]), (3.8, [1, 1, 1]), (4.5, [0.6, 0.3, 0.6])),
            },
        },
    }
    # Abyssal Collapse (2.25s): hands spread low as the ring opens, rise and clench as it charges, and slam
    # together at 1.5s (tick 30) when it implodes
    a["collapse"] = {
        "animation_length": 2.25,
        "bones": {
            "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-20, 0, 55]), (1.3, [-150, 0, 40]), (1.4, [-155, 0, 38]),
                                           (1.5, [-60, 0, -15]), (1.9, [-55, 0, -15]), (2.25, [0, 0, 0]))},
            "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-20, 0, -55]), (1.3, [-150, 0, -40]), (1.4, [-155, 0, -38]),
                                          (1.5, [-60, 0, 15]), (1.9, [-55, 0, 15]), (2.25, [0, 0, 0]))},
            "chest": {"rotation": keys((0, [0, 0, 0]), (1.3, [-14, 0, 0]), (1.4, [-15, 0, 0]), (1.5, [20, 0, 0]),
                                       (1.9, [18, 0, 0]), (2.25, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (1.3, [-20, 0, 0]), (1.5, [15, 0, 0]), (2.25, [0, 0, 0]))},
            "root": {"position": keys((0, [0, 0, 0]), (1.3, [0, 2, 0]), (1.45, [0, 2.5, 0]), (1.5, [0, -1, 0]), (2.25, [0, 0, 0]))},
        },
    }
    # Shadow Step strike (0.6s): it snaps into existence (0 to 0.1s), cocks the claw, and slashes at 0.3s (tick 6)
    a["shadow_strike"] = {
        "animation_length": 0.6,
        "bones": {
            "root": {"scale": keys((0, [0.3, 1.4, 0.3]), (0.1, [1, 1, 1]), (0.6, [1, 1, 1]))},
            "right_arm": {"rotation": keys((0, [30, 0, 35]), (0.2, [40, 0, 35]), (0.3, [-105, -40, -10]),
                                           (0.45, [-95, -35, -10]), (0.6, [0, 0, 0]))},
            "chest": {"rotation": keys((0, [0, 25, 0]), (0.2, [-4, 28, 0]), (0.3, [12, -30, 0]), (0.6, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, -20, 0]), (0.3, [0, 20, 0]), (0.6, [0, 0, 0]))},
        },
    }
    # Shard Rain (2.5s): both arms raised to the sky, shaking, then flung down as the shards land at 1.5s
    a["shard_rain"] = {
        "animation_length": 2.5,
        "bones": {
            "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-175, 0, 20]), (0.6, [-172, 0, 24]), (0.8, [-176, 0, 18]),
                                           (1.0, [-172, 0, 24]), (1.2, [-176, 0, 18]), (1.5, [-30, 0, 30]),
                                           (2.0, [-25, 0, 25]), (2.5, [0, 0, 0]))},
            "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-175, 0, -20]), (0.6, [-172, 0, -24]), (0.8, [-176, 0, -18]),
                                          (1.0, [-172, 0, -24]), (1.2, [-176, 0, -18]), (1.5, [-30, 0, -30]),
                                          (2.0, [-25, 0, -25]), (2.5, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-40, 0, 0]), (1.4, [-42, 0, 0]), (1.5, [10, 0, 0]), (2.5, [0, 0, 0]))},
            "chest": {"rotation": keys((0, [0, 0, 0]), (0.4, [-12, 0, 0]), (1.4, [-12, 0, 0]), (1.5, [15, 0, 0]), (2.5, [0, 0, 0]))},
        },
    }
    # The execution (3.5s): the right hand lifts the player by the neck while the head tilts, curious, as it
    # did when it first watched them; a final squeeze at 3.2s
    a["execute"] = {
        "loop": "hold_on_last_frame", "animation_length": 3.5,
        "bones": {
            "right_arm": {"rotation": keys((0, [-80, 0, -8]), (0.3, [-95, 0, -10]), (1.5, [-112, 0, -10]),
                                           (3.1, [-115, 0, -10]), (3.25, [-118, 0, -6]), (3.5, [-116, 0, -8]))},
            "right_forearm": {"rotation": keys((0, [-10, 0, 0]), (1.5, [-20, 0, 0]), (3.5, [-20, 0, 0]))},
            "right_hand": {"rotation": keys((0, [0, 0, 0]), (3.1, [0, 0, 0]), (3.25, [-15, 0, 0]), (3.5, [-12, 0, 0]))},
            "chest": {"rotation": keys((0, [0, 0, 0]), (1.0, [-6, -10, 0]), (3.5, [-6, -10, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (1.2, [6, 0, -24]), (3.0, [8, 0, -28]), (3.25, [0, 0, -10]),
                                      (3.5, [4, 0, -18]))},
            "left_arm": {"rotation": keys((0, [0, 0, 0]), (1.0, [10, 0, -6]), (3.5, [10, 0, -6]))},
        },
    }
    return {"format_version": "1.8.0", "animations": a}

# ---------------------------------------------------------------------------


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent="\t")
        f.write("\n")


def main():
    offsets = pack(CUBES, TEX)
    write_json(ASSETS / "geckolib/models/entity/underworld_watcher.geo.json", geometry(offsets))
    write_json(ASSETS / "geckolib/animations/entity/underworld_watcher.animation.json", animations())

    base, glow, eye_pixels = paint(offsets)
    for suffix, colors in EYES.items():
        b, g = eye_variant(base, glow, eye_pixels, colors)
        write_png(ASSETS / f"textures/entity/underworld_watcher{suffix}.png", b)
        write_png(ASSETS / f"textures/entity/underworld_watcher{suffix}_glowmask.png", g)
    write_png(ASSETS / "textures/item/underworld_watcher_spawn_egg.png", spawn_egg())
    particle_sprites()
    print(f"Wrote model ({len(CUBES)} cubes), animations and {len(EYES)} texture variants to {ASSETS}")


if __name__ == "__main__":
    main()
