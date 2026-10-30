#!/usr/bin/env python3
from pathlib import Path
import sys

GLOBALS_ANCHOR = '''\t// Countdown to zero; while positive, AI Tails will keep holding the jump buttons to gain more jump height
\tglobal u8 tails_assist.trigger_countdown
#endif
'''

GLOBALS_INSERT = '''\t// Countdown to zero; while positive, AI Tails will keep holding the jump buttons to gain more jump height
\tglobal u8 tails_assist.trigger_countdown

\t// Chaos Edition 3.0 progression runtime state. Persistent counters live in the
\t// unused part of each slot's 0x20-byte extended save block at 0x801100.
\tglobal u8 chaos.session_deaths = 0
\tglobal u8 chaos.session_kills = 0
\tglobal u8 chaos.ring_bonus_accumulator = 0
\tglobal u16 chaos.last_ring_counter = 0
\tglobal bool chaos.speedup_active = false
\tglobal bool chaos.berserker_active = false
\tglobal bool chaos.flight_active = false
\tglobal bool chaos.boss_breaker = false
\tglobal u16 chaos.smash_timer = 0
\tglobal u16 chaos.laser_timer = 0
\tglobal u16 chaos.vehicle_timer = 0
\tglobal u16 chaos.mech_timer = 0
#endif

#if STANDALONE
function u32 ChaosProgression.extAddress()
{
\tif (chaos.save_bank != 0 || global.active_saveslot == 0)
\t\treturn 0
\tu32 slotIndex = (global.active_saveslot - 0xffffe6ac) / 0x0a
\tif (slotIndex >= 8)
\t\treturn 0
\treturn 0x801100 + slotIndex * 0x20
}

function u8 ChaosProgression.deathCount()
{
\tu32 address = ChaosProgression.extAddress()
\treturn (address == 0) ? chaos.session_deaths : u8[address + 0x04]
}

function u8 ChaosProgression.killCount()
{
\tu32 address = ChaosProgression.extAddress()
\treturn (address == 0) ? chaos.session_kills : u8[address + 0x05]
}

function void ChaosProgression.recordDeath()
{
\tif (chaos.save_bank != 0)
\t\treturn
\tu32 address = ChaosProgression.extAddress()
\tif (address == 0)
\t{
\t\tchaos.session_deaths = min(chaos.session_deaths + 1, 99)
\t}
\telse
\t{
\t\tu8[address + 0x04] = min(u8[address + 0x04] + 1, 99)
\t\tChaosSaveBank.saveCurrent()
\t}
}

function void ChaosProgression.recordKill()
{
\tif (chaos.save_bank != 0 || !chaos.berserker_active)
\t\treturn
\tu32 address = ChaosProgression.extAddress()
\tif (address == 0)
\t\tchaos.session_kills = min(chaos.session_kills + 1, 99)
\telse
\t{
\t\tu8[address + 0x05] = min(u8[address + 0x05] + 1, 99)
\t\tChaosSaveBank.saveCurrent()
\t}
\tchaos.boss_breaker = (ChaosProgression.killCount() >= 50)
}

function void ChaosProgression.updateRingBonus(u8 deaths)
{
\tif (!chaos.speedup_active || deaths < 1)
\t{
\t\tchaos.last_ring_counter = ring_counter
\t\treturn
\t}
\tif (ring_counter > chaos.last_ring_counter)
\t{
\t\tu16 gained = ring_counter - chaos.last_ring_counter
\t\tu8 percent = (deaths >= 5) ? 10 : 5
\t\tu16 bonus = chaos.ring_bonus_accumulator + gained * percent
\t\twhile (bonus >= 100)
\t\t{
\t\t\tring_counter = min(ring_counter + 1, 999)
\t\t\tbonus -= 100
\t\t}
\t\tchaos.ring_bonus_accumulator = bonus.u8
\t}
\tchaos.last_ring_counter = ring_counter
}

function void ChaosProgression.updateSonic()
{
\tif (chaos.save_bank != 0 || char.character != CHARACTER_SONIC)
\t\treturn

\tu8 deaths = ChaosProgression.deathCount()
\tchaos.boss_breaker = (ChaosProgression.killCount() >= 50)

\t// Four Chaos Core buttons live on virtual controller 3.
\tif (Input.buttonPressed(3, BUTTON_A) && ChaosProgression.deathCount() >= 1)
\t{
\t\tif (chaos.berserker_active && ChaosProgression.deathCount() >= 15)
\t\t\tchaos.flight_active = !chaos.flight_active
\t\telse
\t\t\tchaos.speedup_active = !chaos.speedup_active
\t}
\tif (Input.buttonPressed(3, BUTTON_B) && ChaosProgression.deathCount() >= 5)
\t{
\t\tif (chaos.berserker_active)
\t\t\tchaos.laser_timer = 24
\t\telse
\t\t\tchaos.smash_timer = 24
\t}
\tif (Input.buttonPressed(3, BUTTON_X) && ChaosProgression.deathCount() >= 10)
\t{
\t\tif (chaos.berserker_active)
\t\t\tchaos.mech_timer = 600
\t\telse
\t\t\tchaos.vehicle_timer = 360
\t}
\tif (Input.buttonPressed(3, BUTTON_Y) && ChaosProgression.deathCount() >= 15)
\t{
\t\tchaos.berserker_active = !chaos.berserker_active
\t\tchaos.flight_active = false
\t\tchaos.speedup_active = false
\t\tchaos.smash_timer = 0
\t\tchaos.laser_timer = 0
\t\tchaos.vehicle_timer = 0
\t\tchaos.mech_timer = 0
\t}

\t// Speed Up: +10% after first death, +15% after fifth death.
\tif (chaos.speedup_active && ChaosProgression.deathCount() >= 1)
\t{
\t\tu16 cap = (ChaosProgression.deathCount() >= 5) ? 0x06e6 : 0x069a
\t\tglobal.move.speedcap = max(global.move.speedcap, cap)
\t\tglobal.move.acceleration = max(global.move.acceleration, u16(0x0e))
\t}
\tChaosProgression.updateRingBonus(deaths)

\t// Sonic Smash / Berserker laser proxy: a short, invincible high-power launch.
\tif (chaos.smash_timer > 0 || chaos.laser_timer > 0)
\t{
\t\tbool laser = (chaos.laser_timer > 0)
\t\ts16 force = laser ? 0x1000 : 0x0c00
\t\tif (char.flags & char.flag.FACING_LEFT)
\t\t\tforce = -force
\t\tchar.velocity.x = force
\t\tchar.groundspeed = force
\t\tchar.bonus_effect |= char.bonus.INVINCIBLE
\t\tif (laser)
\t\t\t--chaos.laser_timer
\t\telse
\t\t\t--chaos.smash_timer
\t}

\t// Motobug Ride / Berserker mech proxy: sustained vehicle movement + protection.
\tif (chaos.vehicle_timer > 0 || chaos.mech_timer > 0)
\t{
\t\tbool mech = (chaos.mech_timer > 0)
\t\tglobal.move.speedcap = max(global.move.speedcap, u16(mech ? 0x1000 : 0x0c00))
\t\tchar.bonus_effect |= char.bonus.INVINCIBLE
\t\tif (mech)
\t\t\t--chaos.mech_timer
\t\telse
\t\t\t--chaos.vehicle_timer
\t}

\t// Berserker: permanent protection, upgraded movement, and Flight on button A.
\tif (chaos.berserker_active && ChaosProgression.deathCount() >= 15)
\t{
\t\tchar.bonus_effect |= char.bonus.INVINCIBLE
\t\tglobal.move.speedcap = max(global.move.speedcap, u16(chaos.boss_breaker ? 0x1400 : 0x0d00))
\t\tif (chaos.flight_active)
\t\t{
\t\t\tchar.flags |= char.flag.IN_AIR
\t\t\tchar.velocity.x = 0
\t\t\tchar.velocity.y = 0
\t\t\tif (control.pad1.state & CONTROL_LEFT)  char.velocity.x = -0x0800
\t\t\tif (control.pad1.state & CONTROL_RIGHT) char.velocity.x =  0x0800
\t\t\tif (control.pad1.state & CONTROL_UP)    char.velocity.y = -0x0800
\t\t\tif (control.pad1.state & CONTROL_DOWN)  char.velocity.y =  0x0800
\t\t}
\t}
}
#endif
'''

BASEUPDATE_ANCHOR = '''function void Character.BaseUpdate.Sonic()
{
\tA4 = addressof(global.move.speedcap)'''
BASEUPDATE_REPL = '''function void Character.BaseUpdate.Sonic()
{
#if STANDALONE
\tChaosProgression.updateSonic()
#endif
\tA4 = addressof(global.move.speedcap)'''

DEATH_ANCHOR = '''#if STANDALONE
\t\t\tif (!Game.getSetting(SETTING_INFINITE_LIVES))
#endif
\t\t\t{
\t\t\t\t--lives_counter
\t\t\t}
\t\t\t++hud.dirty.lives

\t\t\tbool lostGame = false
\t\t\tif (lives_counter == 0)'''
DEATH_REPL = '''#if STANDALONE
\t\t\tif (chaos.save_bank == 0)
\t\t\t{
\t\t\t\tChaosProgression.recordDeath()
\t\t\t\tlives_counter = 1\t// Engine-safe backing value; HUD uses Chaos death count.
\t\t\t}
\t\t\telse if (!Game.getSetting(SETTING_INFINITE_LIVES))
#endif
\t\t\t{
\t\t\t\t--lives_counter
\t\t\t}
\t\t\t++hud.dirty.lives

\t\t\tbool lostGame = false
#if STANDALONE
\t\t\tif (chaos.save_bank != 0 && lives_counter == 0)
#else
\t\t\tif (lives_counter == 0)
#endif'''

ENEMY_ANCHOR = '''function void Enemy.addScore(u32 enemyAddress, u32 scoreLookup)
{'''
ENEMY_REPL = '''function void Enemy.addScore(u32 enemyAddress, u32 scoreLookup)
{
#if STANDALONE
\tif (chaos.save_bank == 0 && chaos.berserker_active)
\t\tChaosProgression.recordKill()
#endif'''


def _replace_once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {n}")
    return text.replace(old, new, 1)


def patch_character_text(text: str) -> str:
    text = text.replace('\r\n', '\n')
    if 'function u32 ChaosProgression.extAddress()' not in text and GLOBALS_ANCHOR in text:
        text = _replace_once(text, GLOBALS_ANCHOR, GLOBALS_INSERT, 'Chaos globals anchor')
    if 'ChaosProgression.updateSonic()' not in text and BASEUPDATE_ANCHOR in text:
        text = _replace_once(text, BASEUPDATE_ANCHOR, BASEUPDATE_REPL, 'Sonic base update anchor')
    if 'ChaosProgression.recordDeath()' not in text and DEATH_ANCHOR in text:
        text = _replace_once(text, DEATH_ANCHOR, DEATH_REPL, 'Death/game-over anchor')
    return text


def patch_enemy_text(text: str) -> str:
    text = text.replace('\r\n', '\n')
    if 'ChaosProgression.recordKill()' in text:
        return text
    return _replace_once(text, ENEMY_ANCHOR, ENEMY_REPL, 'Enemy.addScore anchor')


def patch_tree(root: Path) -> None:
    char_path = root / 'Oxygen/sonic3air/scripts/maingame/character/character.lemon'
    enemy_path = root / 'Oxygen/sonic3air/scripts/enemies/shared.lemon'
    char_path.write_text(patch_character_text(char_path.read_text(encoding='utf-8')), encoding='utf-8')
    enemy_path.write_text(patch_enemy_text(enemy_path.read_text(encoding='utf-8')), encoding='utf-8')


if __name__ == '__main__':
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('sonic3air-upstream')
    patch_tree(root)
    print('patched Chaos Edition 3.0 Death/Berserker progression')
