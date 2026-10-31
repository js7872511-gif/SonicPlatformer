#!/usr/bin/env python3
"""Build Chaos Edition Core as a normal LemonScript mod from patched-vs-stock source."""
from pathlib import Path
import json, re, shutil, sys

FILES = [
    "general/saveslots.lemon",
    "menus/dataselect.lemon",
    "maingame/character/character.lemon",
    "enemies/shared.lemon",
    "maingame/hud/hud.lemon",
]

GLOBALS = r'''//# script-feature-level(2)

// Chaos Edition 3.0 built-in core state
global u8 chaos.save_bank
global u8 chaos.session_deaths = 0
global u8 chaos.session_kills = 0
global u8 chaos.ring_bonus_accumulator = 0
global u16 chaos.last_ring_counter = 0
global bool chaos.speedup_active = false
global bool chaos.berserker_active = false
global bool chaos.flight_active = false
global bool chaos.boss_breaker = false
global u16 chaos.smash_timer = 0
global u16 chaos.laser_timer = 0
global u16 chaos.vehicle_timer = 0
global u16 chaos.mech_timer = 0

constant string ChaosSaveBank.MENU_FONT = "oxyfont_regular:gradient:outline(0x000000ff,1,false):shadow(1,1,0.5f,0.8f)"

'''

FUNC_RE = re.compile(r"(?m)^function\s+[^\n{]+\([^\n]*\)\s*\n\{")


def _find_close(text: str, open_pos: int) -> int:
    depth = 0
    i = open_pos
    quote = None
    line_comment = False
    block_comment = False
    while i < len(text):
        c = text[i]
        n = text[i+1] if i+1 < len(text) else ""
        if line_comment:
            if c == "\n": line_comment = False
            i += 1; continue
        if block_comment:
            if c == "*" and n == "/": block_comment = False; i += 2; continue
            i += 1; continue
        if quote:
            if c == "\\": i += 2; continue
            if c == quote: quote = None
            i += 1; continue
        if c == "/" and n == "/": line_comment = True; i += 2; continue
        if c == "/" and n == "*": block_comment = True; i += 2; continue
        if c in ('"', "'"): quote = c; i += 1; continue
        if c == "{": depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0: return i + 1
        i += 1
    raise ValueError("unclosed function body")


def functions(text: str):
    text = text.replace("\r\n", "\n")
    result = {}
    for m in FUNC_RE.finditer(text):
        header = m.group(0)
        decl = header[:header.find("\n")].strip()
        key = re.sub(r"\s+", " ", decl)
        open_pos = text.find("{", m.start(), m.end())
        end = _find_close(text, open_pos)
        start = m.start()
        # Preserve contiguous Lemon pragmas immediately above the function (address-hook etc.).
        cursor = start
        while cursor > 0:
            prev_end = cursor - 1
            prev_start = text.rfind("\n", 0, prev_end) + 1
            line = text[prev_start:prev_end+1].strip()
            if line.startswith("//#"):
                start = prev_start
                cursor = prev_start
            elif line == "":
                cursor = prev_start
            else:
                break
        result[key] = text[start:end].strip() + "\n"
    return result


def generate(stock_scripts: Path, patched_scripts: Path, out_dir: Path):
    blocks = []
    changed = []
    for rel in FILES:
        stock = functions((stock_scripts / rel).read_text(encoding="utf-8"))
        patched = functions((patched_scripts / rel).read_text(encoding="utf-8"))
        for key, body in patched.items():
            if key not in stock or body != stock[key]:
                blocks.append(body)
                changed.append(key)
    if not blocks:
        raise RuntimeError("no patched functions found for Chaos Core")

    scripts = out_dir / "scripts"
    scripts.mkdir(parents=True, exist_ok=True)
    (scripts / "main.lemon").write_text(
        "// Auto-generated Chaos Edition 3.0 built-in core.\n\n" + GLOBALS + "\n\n".join(blocks) + "\n",
        encoding="utf-8",
    )
    metadata = {
        "Metadata": {
            "Name": "Chaos Edition 3.0 Core",
            "UniqueID": "chaos-edition-core",
            "Author": "Chaos Edition Project",
            "Description": "Built-in save-bank and Death/Berserker platform core.",
            "ModVersion": "3.0.0.0",
            "GameVersion": "26.03.28.0",
        }
    }
    (out_dir / "mod.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    (out_dir / "generated-functions.txt").write_text("\n".join(changed) + "\n", encoding="utf-8")
    return changed


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("usage: build_builtin_core.py STOCK_SCRIPTS PATCHED_SCRIPTS OUT_DIR")
    changed = generate(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
    print(f"generated Chaos Core with {len(changed)} changed/new functions")
    for name in changed:
        print("  ", name)
