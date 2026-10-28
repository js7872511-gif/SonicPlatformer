#!/usr/bin/env python3
from pathlib import Path
import sys

SAVE_HELPERS = r'''
#if STANDALONE
// -----------------------------------------------------------------------------
// Chaos Edition v0.3: two banks of eight save slots.
//   bank 0 = eight always-complete Chaos saves with infinite lives/rings
//   bank 1 = eight ordinary replay saves with normal progression
// -----------------------------------------------------------------------------
global u8 chaos.save_bank

function void ChaosSaveBank.seedChaosDefaults()
{
	zeroMemory(0xffffe6ac, 0x50)
	zeroMemory(0x801100, 0x100)
	for (u32 slot = 0; slot < 8; ++slot)
	{
		u32 slotAddress = 0xffffe6ac + slot * 0x0a
		u8[slotAddress + 0x00] = 2
		u8[slotAddress + 0x01] = u8(slot + 1)
		u8[slotAddress + 0x02] = u8((slot % 4) << 4)
		u8[slotAddress + 0x03] = 0x0e
		u16[slotAddress + 0x04] = 0
		u16[slotAddress + 0x06] = 0x5555
		u8[slotAddress + 0x08] = 99
		u8[slotAddress + 0x09] = 99
	}
}

function void ChaosSaveBank.normalizeChaosDefaults()
{
	for (u32 slot = 0; slot < 8; ++slot)
	{
		u32 slotAddress = 0xffffe6ac + slot * 0x0a
		u8[slotAddress + 0x00] = 2
		if (u8[slotAddress + 0x01] == 0)
			u8[slotAddress + 0x01] = u8(slot + 1)
		u8[slotAddress + 0x03] = 0x0e
		u16[slotAddress + 0x04] = 0
		u16[slotAddress + 0x06] = 0x5555
		u8[slotAddress + 0x08] = 99
		u8[slotAddress + 0x09] = 99
	}
}

function void ChaosSaveBank.seedClassicDefaults()
{
	zeroMemory(0xffffe6ac, 0x50)
	zeroMemory(0x801100, 0x100)
	for (u32 slot = 0; slot < 8; ++slot)
		u8[0xffffe6ac + slot * 0x0a] = 0x80
}

function void ChaosSaveBank.loadCurrent()
{
	if (chaos.save_bank == 0)
	{
		ChaosSaveBank.seedChaosDefaults()
		loadAndMigratePersistentData(0xffffe6ac, 0x50, "chaos_saveslots_100", "Chaos_Saveslots_100", false)
		loadAndMigratePersistentData(0x801100, 0x100, "chaos_saveslots_100", "Chaos_SaveslotsExt_100", false)
		ChaosSaveBank.normalizeChaosDefaults()
	}
	else
	{
		ChaosSaveBank.seedClassicDefaults()
		loadAndMigratePersistentData(0xffffe6ac, 0x50, "chaos_saveslots_classic", "Chaos_Saveslots_Classic", false)
		loadAndMigratePersistentData(0x801100, 0x100, "chaos_saveslots_classic", "Chaos_SaveslotsExt_Classic", false)
	}
}

function void ChaosSaveBank.saveCurrent()
{
	if (chaos.save_bank == 0)
	{
		ChaosSaveBank.normalizeChaosDefaults()
		System.savePersistentData(0xffffe6ac, 0x50, "chaos_saveslots_100", "Chaos_Saveslots_100", false)
		System.savePersistentData(0x801100, 0x100, "chaos_saveslots_100", "Chaos_SaveslotsExt_100", false)
	}
	else
	{
		System.savePersistentData(0xffffe6ac, 0x50, "chaos_saveslots_classic", "Chaos_Saveslots_Classic", false)
		System.savePersistentData(0x801100, 0x100, "chaos_saveslots_classic", "Chaos_SaveslotsExt_Classic", false)
	}
}

function void ChaosSaveBank.enforceInfiniteResources()
{
	if (chaos.save_bank == 0 && global.active_saveslot != 0)
	{
		lives_counter = 99
		ring_counter = 999
		hud.dirty.rings |= 0x80
	}
}
#endif

'''

DATASELECT_HELPERS = r'''
#if STANDALONE
constant string ChaosSaveBank.MENU_FONT = "oxyfont_regular:gradient:outline(0x000000ff,1,false):shadow(1,1,0.5f,0.8f)"

function void ChaosSaveBank.refreshDataSelectObjects()
{
	for (u32 slot = 0; slot < 8; ++slot)
	{
		u32 slotAddress = 0xffffe6ac + slot * 0x0a
		u32 objectAddress = 0xffffb128 + slot * 0x4a
		u32[objectAddress + 0x30] = slotAddress
		u16[objectAddress + 0x34] = u8[slotAddress + 0x02] >> 4
		u16[objectAddress + 0x36] = u8[slotAddress + 0x03]
		u16[objectAddress + 0x38] = 0
		u8[objectAddress + 0x3a] = u8[slotAddress + 0x03]
		u8[objectAddress + 0x3b] = u8[slotAddress] & 0x03
		u8[objectAddress + 0x3e] = u8[slotAddress + 0x08]
		u8[objectAddress + 0x3f] = u8[slotAddress + 0x09]
	}
}

function void DataSelect.switchChaosSaveBank(u8 newBank)
{
	if (newBank == chaos.save_bank)
		return
	ChaosSaveBank.saveCurrent()
	chaos.save_bank = newBank
	ChaosSaveBank.loadCurrent()
	global.active_saveslot = 0
	ChaosSaveBank.refreshDataSelectObjects()
}
#endif

'''


def replace_once(text, old, new, label):
    if old not in text:
        raise RuntimeError(f"anchor not found: {label}")
    return text.replace(old, new, 1)


def patch_saveslots_text(text: str, relaxed: bool = False) -> str:
    marker = "//# address-hook(0x00c154) end(0x00c260)\nfunction void InitSaveSlots()"
    if marker not in text and relaxed:
        marker = "function void InitSaveSlots()"
    text = replace_once(text, marker, SAVE_HELPERS + marker, "InitSaveSlots declaration")

    start = text.find("\t// Read data select saves persistent data")
    end = text.find("\n#else", start)
    if start < 0 or end < 0:
        raise RuntimeError("data select persistence block not found")
    replacement = "\t// Chaos Edition save bank setup\n\tchaos.save_bank = 0\n\tChaosSaveBank.loadCurrent()\n"
    text = text[:start] + replacement + text[end:]

    old_save = ('\t\tSystem.savePersistentData(0xffffe6ac, 0x50, "s3air_saveslots", "SRAM_Saveslots", false)\n'
                '\t\tSystem.savePersistentData(0x801100, 0x100, "s3air_saveslots", "SRAM_SaveslotsExt", false)')
    text = replace_once(text, old_save, "\t\tChaosSaveBank.saveCurrent()", "SaveGameSlot persistence calls")
    return text


def patch_dataselect_text(text: str, relaxed: bool = False) -> str:
    marker = "//# address-hook(0x00c006) end(0x00c028)\nfunction void fn00c006()"
    if marker not in text and relaxed:
        marker = "function void fn00d1fa()"
    text = replace_once(text, marker, DATASELECT_HELPERS + marker, "dataselect helper insertion")

    if relaxed:
        text = replace_once(
            text,
            "\tif (control.pad1.pressed & CONTROL_LEFT)\n\t{",
            "\tif (control.pad1.pressed & CONTROL_LEFT)\n\t{\n\t\tif (dataselect.slot_selected == 1 && chaos.save_bank == 1)\n\t\t\tDataSelect.switchChaosSaveBank(0)",
            "relaxed left edge",
        )
        text = replace_once(
            text,
            "\tif (control.pad1.pressed & CONTROL_RIGHT)\n\t{",
            "\tif (control.pad1.pressed & CONTROL_RIGHT)\n\t{\n\t\tif (dataselect.slot_selected == 8 && chaos.save_bank == 0)\n\t\t\tDataSelect.switchChaosSaveBank(1)",
            "relaxed right edge",
        )
    else:
        old_left = '''\t\t\tif (control.pad1.pressed & CONTROL_LEFT)\n\t\t\t{\n\t\t\t\tif (u16[0xffffeee4] == 0 || dataselect.slot_selected != 1)\n\t\t\t\t{\n\t\t\t\t\tif (dataselect.slot_selected != 0)\n\t\t\t\t\t{\n\t\t\t\t\t\t--dataselect.slot_selected\n\t\t\t\t\t\tu8 sfx = (u16[0xffffeee4] != 0) ? 0x7b : 0xb7\n\t\t\t\t\t\tplaySound(sfx)\n\t\t\t\t\t\tD0 = -8\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}'''
        new_left = '''\t\t\tif (control.pad1.pressed & CONTROL_LEFT)\n\t\t\t{\n\t\t\t#if STANDALONE\n\t\t\t\tif (u16[0xffffeee4] == 0 && dataselect.slot_selected == 1 && chaos.save_bank == 1)\n\t\t\t\t{\n\t\t\t\t\tDataSelect.switchChaosSaveBank(0)\n\t\t\t\t\tplaySound(0xb7)\n\t\t\t\t}\n\t\t\t\telse\n\t\t\t#endif\n\t\t\t\tif (u16[0xffffeee4] == 0 || dataselect.slot_selected != 1)\n\t\t\t\t{\n\t\t\t\t\tif (dataselect.slot_selected != 0)\n\t\t\t\t\t{\n\t\t\t\t\t\t--dataselect.slot_selected\n\t\t\t\t\t\tu8 sfx = (u16[0xffffeee4] != 0) ? 0x7b : 0xb7\n\t\t\t\t\t\tplaySound(sfx)\n\t\t\t\t\t\tD0 = -8\n\t\t\t\t\t}\n\t\t\t\t}\n\t\t\t}'''
        text = replace_once(text, old_left, new_left, "left bank switch")

        old_right = '''\t\t\tif (control.pad1.pressed & CONTROL_RIGHT)\n\t\t\t{\n\t\t\t\tif (dataselect.slot_selected != 9)\n\t\t\t\t{\n\t\t\t\t\t++dataselect.slot_selected\n\t\t\t\t\tu8 sfx = (u16[0xffffeee4] != 0) ? 0x7b : 0xb7\n\t\t\t\t\tplaySound(sfx)\n\t\t\t\t\tD0 = 8\n\t\t\t\t}\n\t\t\t}'''
        new_right = '''\t\t\tif (control.pad1.pressed & CONTROL_RIGHT)\n\t\t\t{\n\t\t\t#if STANDALONE\n\t\t\t\tif (u16[0xffffeee4] == 0 && dataselect.slot_selected == 8 && chaos.save_bank == 0)\n\t\t\t\t{\n\t\t\t\t\tDataSelect.switchChaosSaveBank(1)\n\t\t\t\t\tplaySound(0xb7)\n\t\t\t\t}\n\t\t\t\telse\n\t\t\t#endif\n\t\t\t\tif (dataselect.slot_selected != 9)\n\t\t\t\t{\n\t\t\t\t\t++dataselect.slot_selected\n\t\t\t\t\tu8 sfx = (u16[0xffffeee4] != 0) ? 0x7b : 0xb7\n\t\t\t\t\tplaySound(sfx)\n\t\t\t\t\tD0 = 8\n\t\t\t\t}\n\t\t\t}'''
        text = replace_once(text, old_right, new_right, "right bank switch")

    bg_anchor = 'Renderer.drawCustomSprite("bg_dataselect"'
    idx = text.find(bg_anchor)
    if idx < 0:
        raise RuntimeError("drawCustomPlanes background anchor not found")
    line_end = text.find("\n", idx)
    if line_end < 0:
        line_end = len(text)
    label_code = r'''

	if (chaos.save_bank == 0)
		Renderer.drawText(ChaosSaveBank.MENU_FONT, getScreenWidth() / 2, 8, "CHAOS SAVES 1-8  >", 0xffffffff, 5, 0, 0xf020, false, true)
	else
		Renderer.drawText(ChaosSaveBank.MENU_FONT, getScreenWidth() / 2, 8, "<  CLASSIC SAVES 9-16", 0xffffffff, 5, 0, 0xf020, false, true)
'''
    text = text[:line_end] + label_code + text[line_end:]
    return text


def patch_character_text(text: str) -> str:
    for name in ("Sonic", "Tails", "Knuckles"):
        anchor = f"function void Character.BaseUpdate.{name}()\n{{\n"
        replacement = anchor + "#if STANDALONE\n\tChaosSaveBank.enforceInfiniteResources()\n#endif\n"
        text = replace_once(text, anchor, replacement, f"Character.BaseUpdate.{name}")
    return text


def patch_tree(root: Path):
    saves = root / "Oxygen/sonic3air/scripts/general/saveslots.lemon"
    data = root / "Oxygen/sonic3air/scripts/menus/dataselect.lemon"
    char = root / "Oxygen/sonic3air/scripts/maingame/character/character.lemon"
    saves.write_text(patch_saveslots_text(saves.read_text(encoding="utf-8").replace("\r\n", "\n")), encoding="utf-8")
    data.write_text(patch_dataselect_text(data.read_text(encoding="utf-8").replace("\r\n", "\n")), encoding="utf-8")
    char.write_text(patch_character_text(char.read_text(encoding="utf-8").replace("\r\n", "\n")), encoding="utf-8")
    print("patched Chaos Edition v0.3 save banks + infinite Chaos resources")


if __name__ == "__main__":
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("sonic3air-upstream")
    patch_tree(root)
