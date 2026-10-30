import unittest
from patch_chaos_hud import patch_hud_text


class ChaosHudPatchTests(unittest.TestCase):
    def test_signed_lives_and_berserker_inf(self):
        src='''\t\t// Lives (not visible in Time Attack or with infinite lives setting)\n\t\tif (!Game.isTimeAttack() && !Game.getSetting(SETTING_INFINITE_LIVES))\n\t\t{\n\t\t\tbool useMobilePosition = HUD.useMobileLivesDisplay()\n\t\t\tu16 px = useMobilePosition ? (getScreenWidth() - baseX - 0x30) : baseX\n\t\t\tu16 py = useMobilePosition ? baseY : 200\n\n\t\t\tHUD.drawSprite(getCharacterLivesIcon(getMainCharacter()), px, py, renderQueue)\n\t\t\t++renderQueue\n\n\t\t\tif (lives_counter >= 10)\n\t\t\t\tHUD.drawSmallDigit(u16((lives_counter / 10) % 10), px + 0x20, py + 8, renderQueue)\n\t\t\tHUD.drawSmallDigit(u16(lives_counter % 10), px + 0x28, py + 8, renderQueue)\n\t\t}\n'''
        out=patch_hud_text(src)
        self.assertIn('3 - s16(ChaosProgression.deathCount())',out)
        self.assertIn('"INF"',out)
        self.assertIn('chaos.save_bank == 0',out)


if __name__=='__main__':
    unittest.main()
