import unittest
from patch_death_berserker import patch_character_text, patch_enemy_text


class DeathBerserkerPatchTests(unittest.TestCase):
    def test_character_patch_adds_thresholds_and_persistence(self):
        src = '''#if STANDALONE
\t// Positive if Sonic's fast run animation is active
\tglobal u8 sonic.fastrunanim.timer

\t// Countdown to zero; while positive, AI Tails will keep holding the jump buttons to gain more jump height
\tglobal u8 tails_assist.trigger_countdown
#endif

function void Character.BaseUpdate.Sonic()
{
\tA4 = addressof(global.move.speedcap)
'''
        out = patch_character_text(src)
        self.assertIn('0x801100', out)
        self.assertIn('deathCount() >= 1', out)
        self.assertIn('deathCount() >= 5', out)
        self.assertIn('deathCount() >= 10', out)
        self.assertIn('deathCount() >= 15', out)
        self.assertIn('killCount() >= 50', out)
        self.assertIn('Input.buttonPressed(3, BUTTON_A)', out)
        self.assertIn('Input.buttonPressed(3, BUTTON_B)', out)
        self.assertIn('Input.buttonPressed(3, BUTTON_X)', out)
        self.assertIn('Input.buttonPressed(3, BUTTON_Y)', out)
        self.assertIn('chaos.save_bank != 0', out)

    def test_death_block_records_chaos_death_without_gameover(self):
        src = '''#if STANDALONE
\t\t\tif (!Game.getSetting(SETTING_INFINITE_LIVES))
#endif
\t\t\t{
\t\t\t\t--lives_counter
\t\t\t}
\t\t\t++hud.dirty.lives

\t\t\tbool lostGame = false
\t\t\tif (lives_counter == 0)
'''
        out = patch_character_text(src)
        self.assertIn('ChaosProgression.recordDeath()', out)
        self.assertIn('lives_counter = 1', out)
        self.assertIn('chaos.save_bank != 0 && lives_counter == 0', out)

    def test_enemy_score_hook_tracks_berserker_kills(self):
        src = '''function void Enemy.addScore(u32 enemyAddress, u32 scoreLookup)
{
\tD0 = 0
}'''
        out = patch_enemy_text(src)
        self.assertIn('ChaosProgression.recordKill()', out)
        self.assertIn('chaos.berserker_active', out)


if __name__ == '__main__':
    unittest.main()
