import unittest
from patch_savebank_v30_cleanup import patch_saveslots_text, patch_character_text


class SaveBankV30CleanupTests(unittest.TestCase):
    def test_completed_slots_start_with_three_engine_lives_not_fixed_99(self):
        src='''// Chaos Edition v0.3: two banks of eight save slots.\n\tu8[slotAddress + 0x08] = 99\n\tu8[slotAddress + 0x09] = 99\n\tu8[slotAddress + 0x08] = 99\n'''
        out=patch_saveslots_text(src)
        self.assertIn('Chaos Edition 3.0', out)
        self.assertEqual(out.count('u8[slotAddress + 0x08] = 3'),2)
        self.assertNotIn('infinite lives/rings', out)

    def test_removes_old_per_frame_infinite_resource_enforcement(self):
        src='''function void Character.BaseUpdate.Sonic()\n{\n#if STANDALONE\n\tChaosSaveBank.enforceInfiniteResources()\n#endif\n}\n'''
        out=patch_character_text(src)
        self.assertNotIn('enforceInfiniteResources()', out)


if __name__=='__main__':
    unittest.main()
