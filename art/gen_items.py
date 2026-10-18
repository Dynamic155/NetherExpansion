"""Generates textures and copied vanilla assets for the Underworld Watcher's drops.

Run from the project root:  python art/gen_items.py

- Umbral Netherite gear, the Umbral upgrade template, Umbral bricks and the Umbral lantern are recolored from
  vanilla textures (netherite gear, the netherite template, polished blackstone and the soul lantern).
- Eye of the Deep, Umbral Shard, Watcher Sigil and Veilstep Charm are drawn here.
- The Umbral brick blocks and lantern reuse vanilla's blockstates, models, loot tables and recipes for the
  polished blackstone bricks and the soul lantern, with the IDs swapped.
"""
import glob
import importlib.util
import json
import math
import os
import re
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src/main/resources"
ASSETS = RESOURCES / "assets/netherexpansion"
DATA = RESOURCES / "data/netherexpansion"

# PNG reading and writing are shared with the Watcher's generator
_spec = importlib.util.spec_from_file_location("watcher_art", Path(__file__).with_name("gen_underworld_watcher.py"))
_watcher = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_watcher)
read_png, write_png, hex_rgb = _watcher.read_png, _watcher.write_png, _watcher.hex_rgb


def minecraft_jar():
    jars = glob.glob(os.path.expanduser(
        "~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.3/*.jar"))
    assert jars, "Minecraft 26.3 jar not found; run a Gradle build first"
    return ZipFile(jars[0])


JAR = minecraft_jar()


def vanilla_png(path):
    return read_png(JAR.read(f"assets/minecraft/textures/{path}.png"))

# ---------------------------------------------------------------------------
# Recoloring
# ---------------------------------------------------------------------------


def luminance(px):
    return (0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]) / 255


def saturation(px):
    high, low = max(px[:3]), min(px[:3])
    return 0 if high == 0 else (high - low) / high


def hue(px):
    r, g, b = (c / 255 for c in px[:3])
    high, low = max(r, g, b), min(r, g, b)
    if high == low:
        return 0.0
    if high == r:
        h = (g - b) / (high - low) % 6
    elif high == g:
        h = (b - r) / (high - low) + 2
    else:
        h = (r - g) / (high - low) + 4
    return h * 60


def gradient(stops, t):
    t = max(0.0, min(1.0, t))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return tuple(int(a + (b - a) * f) for a, b in zip(c0, c1))
    return stops[-1][1]


def stops(*pairs):
    return [(t, hex_rgb(c)) for t, c in pairs]


# Umbral metal: violet-black with lilac highlights
UMBRAL = stops((0.0, "#08050e"), (0.25, "#1a1028"), (0.5, "#33204f"), (0.75, "#5e3f8f"), (1.0, "#b693e6"))
# Umbral stone: darker and flatter, for the bricks
UMBRAL_STONE = stops((0.0, "#07050b"), (0.3, "#150e20"), (0.6, "#251a36"), (1.0, "#4a3866"))
# Violet flame for the lantern
VIOLET_FLAME = stops((0.0, "#3a1470"), (0.5, "#9a4cff"), (1.0, "#f2e2ff"))


def is_wood(px):
    """Tool handles: netherite tools have dark red-brown ones."""
    h = hue(px)
    return saturation(px) >= 0.24 and (h >= 340 or h <= 20)


def handle(pixels):
    """Keeps the red-brown handle of a 16x16 netherite tool: only in the lower-left half, where handles run."""
    size = len(pixels)

    def keep(px, x, y):
        return x - y < size // 8 and is_wood(px)
    return keep


def recolor(pixels, ramp, keep=lambda px, x, y: False, stretch=(0.0, 1.0)):
    lo, hi = stretch
    out = []
    for y, row in enumerate(pixels):
        new = []
        for x, px in enumerate(row):
            if px[3] == 0 or keep(px, x, y):
                new.append(px)
            else:
                t = (luminance(px) - lo) / (hi - lo) if hi > lo else 0
                new.append((*gradient(ramp, t), px[3]))
        out.append(new)
    return out


def lum_range(pixels, keep=lambda px, x, y: False):
    values = [luminance(px) for y, row in enumerate(pixels) for x, px in enumerate(row) if px[3] and not keep(px, x, y)]
    return (min(values), max(values)) if values else (0.0, 1.0)


def umbral_metal(pixels, keep=lambda px, x, y: False):
    return recolor(pixels, UMBRAL, keep, lum_range(pixels, keep))


def soul_to_violet(pixels):
    """Soul lantern: the cyan flame turns violet, the iron cage turns violet-black."""
    out = []
    lo, hi = lum_range(pixels)
    for row in pixels:
        new = []
        for px in row:
            if px[3] == 0:
                new.append(px)
            elif saturation(px) > 0.3 and 150 <= hue(px) <= 220:
                new.append((*gradient(VIOLET_FLAME, luminance(px) * 1.2), px[3]))
            else:
                new.append((*gradient(UMBRAL, (luminance(px) - lo) / (hi - lo)), px[3]))
        out.append(new)
    return out

# ---------------------------------------------------------------------------
# Drawn items (16x16)
# ---------------------------------------------------------------------------


def blank():
    return [[(0, 0, 0, 0)] * 16 for _ in range(16)]


def put(img, x, y, color, alpha=255):
    if 0 <= x < 16 and 0 <= y < 16:
        img[y][x] = (*hex_rgb(color), alpha)


def eye_of_the_deep():
    """An obsidian orb, lit from the top left, with the Watcher's vertical eye at its core."""
    img = blank()
    shades = ["#05030a", "#0d0818", "#160e26", "#221538", "#33204f", "#4a3270"]
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            if d > 6.6:
                continue
            if d > 5.9:
                put(img, x, y, "#030207")
                continue
            light = 0.55 - (dx + dy) / 14 + (0.15 if d < 3 else 0)
            put(img, x, y, shades[max(0, min(len(shades) - 1, int(light * len(shades))))])
    # The slit eye: hot core, violet edges, a faint glow around it
    for y, row in zip(range(4, 12), ["d", "c", "b", "a", "a", "b", "c", "d"]):
        put(img, 7, y, {"a": "#ffffff", "b": "#f0d8ff", "c": "#c48cff", "d": "#7a3ad0"}[row])
        put(img, 8, y, {"a": "#f0d8ff", "b": "#c48cff", "c": "#9a5ae8", "d": "#5a2a9a"}[row])
    for y in (5, 6, 9, 10):
        put(img, 6, y, "#4a2580")
        put(img, 9, y, "#4a2580")
    put(img, 4, 4, "#8a6ac0")  # glint
    put(img, 5, 3, "#5e4690")
    return img


def umbral_shard():
    """A jagged violet crystal shard, pointing up and to the right."""
    rows = [
        "................",
        "............aa..",
        "...........aeda.",
        "..........aedda.",
        ".........adddca.",
        "........adddcca.",
        ".......adddccba.",
        "......adddccba..",
        ".....adddccba...",
        "....aeddccba....",
        "...aeddccba.....",
        "..aedccbba......",
        "..adccbba.......",
        "..acbbaa........",
        "...aaa..........",
        "................",
    ]
    palette = {"a": "#120a1e", "b": "#2e1a4c", "c": "#4f2f86", "d": "#8a5ad0", "e": "#e2ccff"}
    img = blank()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                put(img, x, y, palette[ch])
    return img


def watcher_sigil():
    """A charred stone medallion with a basalt rim and an ember-lit eye glyph."""
    img = blank()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 7.0:
                continue
            if d > 6.2:
                put(img, x, y, "#121010")
            elif d > 5.0:
                put(img, x, y, "#4a4a54" if (x + y) % 3 else "#34343b")
            else:
                put(img, x, y, "#201c1a" if (x * 7 + y * 3) % 5 else "#171413")
    # The eye glyph: an almond outline with a vertical slit
    for x, y in [(5, 7), (5, 8), (6, 6), (6, 9), (7, 5), (8, 5), (7, 10), (8, 10), (9, 6), (9, 9), (10, 7), (10, 8)]:
        put(img, x, y, "#a8480f")
    for y in range(6, 10):
        put(img, 7, y, "#f08a2c" if y in (7, 8) else "#c0601a")
        put(img, 8, y, "#ffb060" if y in (7, 8) else "#c0601a")
    put(img, 4, 3, "#6a6a76")  # rim glint
    return img


def veilstep_charm():
    """A dark iron ring holding a small umbral shard, on a short chain."""
    img = blank()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 8.5)
            if 4.4 < d < 6.2:
                light = (x + y) < 15
                put(img, x, y, "#5e3f8f" if light else "#33204f")
            elif 3.6 < d <= 4.4:
                put(img, x, y, "#1a1028")
    for x, y, c in [(7, 6, "#8a5ad0"), (8, 6, "#e2ccff"), (7, 7, "#4f2f86"), (8, 7, "#8a5ad0"), (6, 8, "#4f2f86"),
                    (7, 8, "#8a5ad0"), (8, 8, "#4f2f86"), (9, 8, "#2e1a4c"), (7, 9, "#2e1a4c"), (8, 9, "#4f2f86"),
                    (7, 10, "#2e1a4c"), (8, 10, "#2e1a4c")]:
        put(img, x, y, c)
    for y in range(0, 3):
        put(img, 7 + (y % 2), y, "#4a4a54")  # chain
    return img

# ---------------------------------------------------------------------------
# Vanilla assets copied for the Umbral blocks
# ---------------------------------------------------------------------------

RENAMES = [
    ("chiseled_polished_blackstone", "chiseled_umbral_bricks"),
    ("polished_blackstone_bricks", "umbral_bricks"),
    ("polished_blackstone_brick", "umbral_brick"),
    ("soul_lantern", "umbral_lantern"),
]


def rename(text):
    for old, new in RENAMES:
        text = re.sub(rf"minecraft:((?:block|item)/)?{old}", rf"netherexpansion:\g<1>{new}", text)
    return text


def copy_vanilla(source, target):
    text = JAR.read(source).decode()
    target.parent.mkdir(parents=True, exist_ok=True)
    data = json.loads(rename(text))
    with open(target, "w", newline="\n") as f:
        json.dump(data, f, indent="\t")
        f.write("\n")


def copy_block_assets():
    blocks = {
        "polished_blackstone_bricks": "umbral_bricks",
        "polished_blackstone_brick_stairs": "umbral_brick_stairs",
        "polished_blackstone_brick_slab": "umbral_brick_slab",
        "polished_blackstone_brick_wall": "umbral_brick_wall",
        "chiseled_polished_blackstone": "chiseled_umbral_bricks",
        "soul_lantern": "umbral_lantern",
    }
    models = [n for n in JAR.namelist() if n.startswith("assets/minecraft/models/block/")]
    for old, new in blocks.items():
        copy_vanilla(f"assets/minecraft/blockstates/{old}.json", ASSETS / f"blockstates/{new}.json")
        copy_vanilla(f"assets/minecraft/items/{old}.json", ASSETS / f"items/{new}.json")
        copy_vanilla(f"data/minecraft/loot_table/blocks/{old}.json", DATA / f"loot_table/blocks/{new}.json")
        for model in models:
            name = model.rsplit("/", 1)[1][:-5]
            if name == old or (name.startswith(old + "_") and old != "polished_blackstone_bricks"):
                copy_vanilla(model, ASSETS / f"models/block/{rename('minecraft:' + name).split(':')[1]}.json")
    copy_vanilla("assets/minecraft/models/item/soul_lantern.json", ASSETS / "models/item/umbral_lantern.json")
    for recipe in ["polished_blackstone_brick_stairs", "polished_blackstone_brick_slab", "polished_blackstone_brick_wall",
                   "polished_blackstone_brick_stairs_from_polished_blackstone_bricks_stonecutting",
                   "polished_blackstone_brick_slab_from_polished_blackstone_bricks_stonecutting",
                   "polished_blackstone_brick_wall_from_polished_blackstone_bricks_stonecutting"]:
        target = recipe.replace("polished_blackstone_bricks", "umbral_bricks").replace("polished_blackstone_brick", "umbral_brick")
        copy_vanilla(f"data/minecraft/recipe/{recipe}.json", DATA / f"recipe/{target}.json")

# ---------------------------------------------------------------------------
# Data: item models, equipment, recipes, tags, loot and lang
# ---------------------------------------------------------------------------

NS = "netherexpansion"
GEAR_TOOLS = ["sword", "pickaxe", "axe", "shovel", "hoe", "spear"]
GEAR_ARMOR = ["helmet", "chestplate", "leggings", "boots"]
FLAT_ITEMS = ["eye_of_the_deep", "umbral_shard", "watcher_sigil", "veilstep_charm", "umbral_upgrade_smithing_template"]


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", newline="\n") as f:
        json.dump(data, f, indent="\t")
        f.write("\n")


def merge_json(path, updates):
    data = json.loads(path.read_text()) if path.exists() else {}
    data.update(updates)
    write_json(path, data)


def item_model(name, parent="minecraft:item/generated"):
    write_json(ASSETS / f"models/item/{name}.json", {"parent": parent, "textures": {"layer0": f"{NS}:item/{name}"}})
    write_json(ASSETS / f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})


def tag(path, values):
    write_json(path, {"replace": False, "values": values})


def write_data():
    # Item models
    for name in FLAT_ITEMS:
        item_model(name)
    for piece in ["sword", "pickaxe", "axe", "shovel", "hoe"]:
        item_model(f"umbral_netherite_{piece}", "minecraft:item/handheld")
    for piece in GEAR_ARMOR:
        item_model(f"umbral_netherite_{piece}")
    # The spear: flat in inventories, held like vanilla spears in hand
    write_json(ASSETS / "models/item/umbral_netherite_spear.json",
               {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/umbral_netherite_spear"}})
    write_json(ASSETS / "models/item/umbral_netherite_spear_in_hand.json",
               {"parent": "minecraft:item/spear_in_hand", "textures": {"layer0": f"{NS}:item/umbral_netherite_spear_in_hand"}})
    write_json(ASSETS / "items/umbral_netherite_spear.json", {
        "model": {
            "type": "minecraft:select", "property": "minecraft:display_context",
            "cases": [{"model": {"type": "minecraft:model", "model": f"{NS}:item/umbral_netherite_spear"},
                       "when": ["gui", "ground", "fixed", "on_shelf"]}],
            "fallback": {"type": "minecraft:model", "model": f"{NS}:item/umbral_netherite_spear_in_hand"},
        },
        "swap_animation_scale": 1.95,
    })
    # Worn armor
    layer = [{"texture": f"{NS}:umbral_netherite"}]
    write_json(ASSETS / "equipment/umbral_netherite.json",
               {"layers": {"humanoid": layer, "humanoid_baby": layer, "humanoid_leggings": layer}})

    # Recipes
    recipes = DATA / "recipe"
    for piece in GEAR_TOOLS + GEAR_ARMOR:
        write_json(recipes / f"umbral_netherite_{piece}_smithing.json", {
            "type": "minecraft:smithing_transform",
            "template": f"{NS}:umbral_upgrade_smithing_template",
            "base": f"minecraft:netherite_{piece}",
            "addition": f"{NS}:umbral_shard",
            "result": {"id": f"{NS}:umbral_netherite_{piece}"},
        })
    write_json(recipes / "umbral_upgrade_smithing_template.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": [" S ", "SNS", " S "],
        "key": {"S": f"{NS}:umbral_shard", "N": "minecraft:netherite_upgrade_smithing_template"},
        "result": {"id": f"{NS}:umbral_upgrade_smithing_template", "count": 1},
    })
    write_json(recipes / "veilstep_charm.json", {
        "type": "minecraft:crafting_shaped", "category": "equipment",
        "pattern": [" N ", "SPS", " S "],
        "key": {"N": "minecraft:iron_nugget", "S": f"{NS}:umbral_shard", "P": "minecraft:ender_pearl"},
        "result": {"id": f"{NS}:veilstep_charm", "count": 1},
    })
    write_json(recipes / "umbral_bricks.json", {
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": ["BBB", "BSB", "BBB"],
        "key": {"B": "minecraft:polished_blackstone_bricks", "S": f"{NS}:umbral_shard"},
        "result": {"id": f"{NS}:umbral_bricks", "count": 8},
    })
    write_json(recipes / "chiseled_umbral_bricks.json", {
        "type": "minecraft:crafting_shaped", "category": "building",
        "pattern": ["#", "#"], "key": {"#": f"{NS}:umbral_brick_slab"},
        "result": {"id": f"{NS}:chiseled_umbral_bricks", "count": 1},
    })
    write_json(recipes / "chiseled_umbral_bricks_from_umbral_bricks_stonecutting.json", {
        "type": "minecraft:stonecutting", "ingredient": f"{NS}:umbral_bricks",
        "result": {"id": f"{NS}:chiseled_umbral_bricks", "count": 1},
    })
    lantern = json.loads(JAR.read("data/minecraft/recipe/soul_lantern.json"))
    lantern["key"] = {k: (f"{NS}:umbral_shard" if v == "minecraft:soul_torch" else v) for k, v in lantern["key"].items()}
    lantern["result"]["id"] = f"{NS}:umbral_lantern"
    write_json(recipes / "umbral_lantern.json", lantern)

    # Tags
    mc_items = RESOURCES / "data/minecraft/tags/item"
    for piece, vanilla_tag in [("sword", "swords"), ("pickaxe", "pickaxes"), ("axe", "axes"), ("shovel", "shovels"),
                               ("hoe", "hoes"), ("spear", "spears"), ("helmet", "head_armor"), ("chestplate", "chest_armor"),
                               ("leggings", "leg_armor"), ("boots", "foot_armor")]:
        tag(mc_items / f"{vanilla_tag}.json", [f"{NS}:umbral_netherite_{piece}"])
    for kind in ["walls", "stairs", "slabs"]:
        block = {"walls": "umbral_brick_wall", "stairs": "umbral_brick_stairs", "slabs": "umbral_brick_slab"}[kind]
        tag(mc_items / f"{kind}.json", [f"{NS}:{block}"])
        tag(RESOURCES / f"data/minecraft/tags/block/{kind}.json", [f"{NS}:{block}"])
    tag(RESOURCES / "data/minecraft/tags/block/mineable/pickaxe.json",
        [f"{NS}:{b}" for b in ["umbral_bricks", "umbral_brick_stairs", "umbral_brick_slab", "umbral_brick_wall",
                               "chiseled_umbral_bricks", "umbral_lantern"]])
    tag(DATA / "tags/item/repairs_umbral_netherite.json", [f"{NS}:umbral_shard"])
    tag(DATA / "tags/item/umbral_armor.json", [f"{NS}:umbral_netherite_{p}" for p in GEAR_ARMOR])
    tag(DATA / "tags/item/umbral_weapons.json", [f"{NS}:umbral_netherite_{p}" for p in GEAR_TOOLS])
    tag(DATA / "tags/entity_type/underworld_mobs.json", [f"{NS}:underworld_watcher"])
    tag(DATA / "tags/entity_type/sigil_threats.json", [f"{NS}:underworld_watcher"] + [f"minecraft:{m}" for m in [
        "warden", "wither", "ravager", "piglin_brute", "elder_guardian", "evoker", "vindicator", "hoglin", "zoglin",
        "breeze", "creeper", "creaking"]])

    # The Watcher's loot: the Eye always, Umbral Shards often, the Sigil rarely
    write_json(DATA / "loot_table/entities/underworld_watcher.json", {
        "type": "minecraft:entity",
        "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:eye_of_the_deep"}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:umbral_shard", "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 3, "max": 5}},
                {"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                 "count": {"type": "minecraft:uniform", "min": 0, "max": 1}}]}],
             "conditions": [{"condition": "minecraft:killed_by_player"}, {"condition": "minecraft:random_chance", "chance": 0.65}]},
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:watcher_sigil"}],
             "conditions": [{"condition": "minecraft:killed_by_player"},
                            {"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                             "unenchanted_chance": 0.2, "enchanted_chance": {"type": "minecraft:linear", "base": 0.25, "per_level_above_first": 0.05}}]},
        ],
        "random_sequence": f"{NS}:entities/underworld_watcher",
    })

    # Text and sounds
    t = "item.netherexpansion."
    tpl = t + "smithing_template.umbral_upgrade."
    lang = {
        "itemGroup.netherexpansion": "Nether Expansion",
        t + "eye_of_the_deep": "Eye of the Deep",
        t + "eye_of_the_deep.lore1": "The Watcher's core.",
        t + "eye_of_the_deep.lore2": "It opens the way to the Underworld.",
        t + "eye_of_the_deep.pull": "The Eye stirs, but whatever it seeks lies beneath the Nether",
        t + "eye_of_the_deep.pull_nether": "The Eye pulls downward. Something waits beneath the Nether",
        t + "umbral_shard": "Umbral Shard",
        t + "umbral_shard.lore1": "A splinter of the Watcher's shadow.",
        t + "watcher_sigil": "Watcher Sigil",
        t + "watcher_sigil.lore1": "Carry it to be warned when something",
        t + "watcher_sigil.lore2": "dangerous closes in.",
        t + "veilstep_charm": "Veilstep Charm",
        t + "veilstep_charm.lore1": "Use to blink a short way ahead.",
        t + "umbral_upgrade_smithing_template": "Smithing Template",
        tpl + "applies_to": "Netherite Equipment",
        tpl + "ingredients": "Umbral Shard",
        tpl + "base_slot_description": "Add netherite armor, weapon, or tool",
        tpl + "additions_slot_description": "Add Umbral Shard",
        "upgrade.netherexpansion.umbral_upgrade": "Umbral Upgrade",
        "message.netherexpansion.sigil_warning": "The Watcher Sigil burns: %s to the %s, %s blocks away",
        "subtitles.netherexpansion.item.watcher_sigil.warning": "Watcher Sigil burns",
        "block.netherexpansion.umbral_bricks": "Umbral Bricks",
        "block.netherexpansion.umbral_brick_stairs": "Umbral Brick Stairs",
        "block.netherexpansion.umbral_brick_slab": "Umbral Brick Slab",
        "block.netherexpansion.umbral_brick_wall": "Umbral Brick Wall",
        "block.netherexpansion.chiseled_umbral_bricks": "Chiseled Umbral Bricks",
        "block.netherexpansion.umbral_lantern": "Umbral Lantern",
    }
    for piece in GEAR_TOOLS + GEAR_ARMOR:
        lang[t + f"umbral_netherite_{piece}"] = f"Umbral Netherite {piece.capitalize()}"
    for d, name in {"north": "north", "northeast": "northeast", "east": "east", "southeast": "southeast", "south": "south",
                    "southwest": "southwest", "west": "west", "northwest": "northwest"}.items():
        lang[f"direction.netherexpansion.{d}"] = name
    merge_json(ASSETS / "lang/en_us.json", lang)
    merge_json(ASSETS / "sounds.json", {"item.watcher_sigil.warning": {
        "subtitle": "subtitles.netherexpansion.item.watcher_sigil.warning",
        "sounds": [{"name": f"minecraft:block/amethyst/resonate{i}", "pitch": 1.3} for i in range(1, 5)]}})


# ---------------------------------------------------------------------------


def main():
    tex = ASSETS / "textures"
    tools = ["sword", "pickaxe", "axe", "shovel", "hoe", "spear", "spear_in_hand"]
    for piece in tools + ["helmet", "chestplate", "leggings", "boots"]:
        art = vanilla_png(f"item/netherite_{piece}")
        keep = handle(art) if piece in tools else (lambda px, x, y: False)
        write_png(tex / f"item/umbral_netherite_{piece}.png", umbral_metal(art, keep))
    for layer in ["humanoid", "humanoid_leggings"]:
        write_png(tex / f"entity/equipment/{layer}/umbral_netherite.png", umbral_metal(vanilla_png(f"entity/equipment/{layer}/netherite")))
    write_png(tex / "item/umbral_upgrade_smithing_template.png", umbral_metal(vanilla_png("item/netherite_upgrade_smithing_template")))

    bricks = vanilla_png("block/polished_blackstone_bricks")
    write_png(tex / "block/umbral_bricks.png", recolor(bricks, UMBRAL_STONE, stretch=lum_range(bricks)))
    chiseled = vanilla_png("block/chiseled_polished_blackstone")
    write_png(tex / "block/chiseled_umbral_bricks.png", recolor(chiseled, UMBRAL_STONE, stretch=lum_range(chiseled)))
    write_png(tex / "block/umbral_lantern.png", soul_to_violet(vanilla_png("block/soul_lantern")))
    write_png(tex / "item/umbral_lantern.png", soul_to_violet(vanilla_png("item/soul_lantern")))

    write_png(tex / "item/eye_of_the_deep.png", eye_of_the_deep())
    write_png(tex / "item/umbral_shard.png", umbral_shard())
    write_png(tex / "item/watcher_sigil.png", watcher_sigil())
    write_png(tex / "item/veilstep_charm.png", veilstep_charm())

    copy_block_assets()
    write_data()
    print("Wrote textures, copied block assets, and wrote item models, recipes, tags, loot and lang")


if __name__ == "__main__":
    main()
