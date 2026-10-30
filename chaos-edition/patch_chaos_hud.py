#!/usr/bin/env python3
from pathlib import Path
import sys

ANCHOR='''\t\t// Lives (not visible in Time Attack or with infinite lives setting)
\t\tif (!Game.isTimeAttack() && !Game.getSetting(SETTING_INFINITE_LIVES))
\t\t{
\t\t\tbool useMobilePosition = HUD.useMobileLivesDisplay()
\t\t\tu16 px = useMobilePosition ? (getScreenWidth() - baseX - 0x30) : baseX
\t\t\tu16 py = useMobilePosition ? baseY : 200

\t\t\tHUD.drawSprite(getCharacterLivesIcon(getMainCharacter()), px, py, renderQueue)
\t\t\t++renderQueue

\t\t\tif (lives_counter >= 10)
\t\t\t\tHUD.drawSmallDigit(u16((lives_counter / 10) % 10), px + 0x20, py + 8, renderQueue)
\t\t\tHUD.drawSmallDigit(u16(lives_counter % 10), px + 0x28, py + 8, renderQueue)
\t\t}'''

REPL='''\t\t// Lives. Chaos bank uses signed death-progression lives; Classic stays stock.
\t\tif (!Game.isTimeAttack() && (chaos.save_bank == 0 || !Game.getSetting(SETTING_INFINITE_LIVES)))
\t\t{
\t\t\tbool useMobilePosition = HUD.useMobileLivesDisplay()
\t\t\tu16 px = useMobilePosition ? (getScreenWidth() - baseX - 0x30) : baseX
\t\t\tu16 py = useMobilePosition ? baseY : 200

\t\t\tHUD.drawSprite(getCharacterLivesIcon(getMainCharacter()), px, py, renderQueue)
\t\t\t++renderQueue

\t\t\tif (chaos.save_bank == 0)
\t\t\t{
\t\t\t\tif (chaos.berserker_active)
\t\t\t\t\tRenderer.drawText("oxyfont_regular", px + 0x30, py + 4, "INF", 0xffffffff, 5, 0, renderQueue, false, true)
\t\t\t\telse
\t\t\t\t{
\t\t\t\t\ts16 chaosLives = 3 - s16(ChaosProgression.deathCount())
\t\t\t\t\tRenderer.drawText("oxyfont_regular", px + 0x30, py + 4, stringformat("%d", chaosLives), 0xffffffff, 5, 0, renderQueue, false, true)
\t\t\t\t}
\t\t\t}
\t\t\telse
\t\t\t{
\t\t\t\tif (lives_counter >= 10)
\t\t\t\t\tHUD.drawSmallDigit(u16((lives_counter / 10) % 10), px + 0x20, py + 8, renderQueue)
\t\t\t\tHUD.drawSmallDigit(u16(lives_counter % 10), px + 0x28, py + 8, renderQueue)
\t\t\t}
\t\t}'''

def patch_hud_text(text:str)->str:
    text=text.replace('\r\n','\n')
    if 'chaosLives = 3 - s16(ChaosProgression.deathCount())' in text:
        return text
    if text.count(ANCHOR)!=1:
        raise RuntimeError(f'HUD lives anchor expected 1 match, found {text.count(ANCHOR)}')
    return text.replace(ANCHOR,REPL,1)

def patch_tree(root:Path)->None:
    p=root/'Oxygen/sonic3air/scripts/maingame/hud/hud.lemon'
    p.write_text(patch_hud_text(p.read_text(encoding='utf-8')),encoding='utf-8')

if __name__=='__main__':
    root=Path(sys.argv[1]) if len(sys.argv)>1 else Path('sonic3air-upstream')
    patch_tree(root)
    print('patched Chaos Edition 3.0 signed lives HUD')
