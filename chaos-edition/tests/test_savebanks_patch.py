import importlib.util
import pathlib
import unittest

PATCH_PATH = pathlib.Path(__file__).resolve().parents[1] / "patch_save_banks.py"


class SaveBankPatchTests(unittest.TestCase):
    def load_module(self):
        spec = importlib.util.spec_from_file_location("patch_save_banks", PATCH_PATH)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        return mod

    def test_saveslots_patch_adds_two_banks_and_resource_enforcement(self):
        mod = self.load_module()
        src = '''function void InitSaveSlots()\n{\n#if STANDALONE\n\t// Read data select saves persistent data\n\t{\n\t\tzeroMemory(0xffffe6ac, 0x50)\n\t}\n#else\n#endif\n}\n\nfunction void SaveGameSlot()\n{\n#if STANDALONE\n\tif (!invalidOrDefault)\n\t{\n\t\tSystem.savePersistentData(0xffffe6ac, 0x50, "s3air_saveslots", "SRAM_Saveslots", false)\n\t\tSystem.savePersistentData(0x801100, 0x100, "s3air_saveslots", "SRAM_SaveslotsExt", false)\n\t}\n#else\n#endif\n}\n'''
        out = mod.patch_saveslots_text(src, relaxed=True)
        self.assertIn("global u8 chaos.save_bank", out)
        self.assertIn("chaos_saveslots_100", out)
        self.assertIn("chaos_saveslots_classic", out)
        self.assertIn("u16[slotAddress + 0x06] = 0x5555", out)
        self.assertIn("ring_counter = 999", out)
        self.assertIn("lives_counter = 99", out)
        self.assertIn("ChaosSaveBank.saveCurrent()", out)

    def test_dataselect_patch_switches_bank_at_edges_and_draws_label(self):
        mod = self.load_module()
        src = '''constant u16 DataSelect.TextTiles.targetInVRAM = 0xac40\n\nfunction void fn00d1fa()\n{\n\tif (control.pad1.pressed & CONTROL_LEFT)\n\t{\n\t\tif (u16[0xffffeee4] == 0 || dataselect.slot_selected != 1)\n\t\t{\n\t\t\tif (dataselect.slot_selected != 0)\n\t\t\t{\n\t\t\t\t--dataselect.slot_selected\n\t\t\t}\n\t\t}\n\t}\n\telse\n\tif (control.pad1.pressed & CONTROL_RIGHT)\n\t{\n\t\tif (dataselect.slot_selected != 9)\n\t\t{\n\t\t\t++dataselect.slot_selected\n\t\t}\n\t}\n}\n\n#if STANDALONE\nfunction void DataSelect.drawCustomPlanes()\n{\n\tRenderer.drawCustomSprite("bg_dataselect", 0, 0, 0, 0, 0)\n}\n#endif\n'''
        out = mod.patch_dataselect_text(src, relaxed=True)
        self.assertIn("DataSelect.switchChaosSaveBank", out)
        self.assertIn("dataselect.slot_selected == 1 && chaos.save_bank == 1", out)
        self.assertIn("dataselect.slot_selected == 8 && chaos.save_bank == 0", out)
        self.assertIn("CHAOS SAVES 1-8", out)
        self.assertIn("CLASSIC SAVES 9-16", out)
        self.assertIn("ChaosSaveBank.refreshDataSelectObjects()", out)

    def test_character_patch_enforces_resources_for_all_main_character_updates(self):
        mod = self.load_module()
        src = '''function void Character.BaseUpdate.Sonic()\n{\n}\nfunction void Character.BaseUpdate.Tails()\n{\n}\nfunction void Character.BaseUpdate.Knuckles()\n{\n}\n'''
        out = mod.patch_character_text(src)
        self.assertEqual(out.count("ChaosSaveBank.enforceInfiniteResources()"), 3)


if __name__ == "__main__":
    unittest.main()
