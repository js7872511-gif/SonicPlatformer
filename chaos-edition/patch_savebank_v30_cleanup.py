#!/usr/bin/env python3
from pathlib import Path
import sys

RESOURCE_HOOK='''#if STANDALONE
\tChaosSaveBank.enforceInfiniteResources()
#endif
'''

def patch_saveslots_text(text:str)->str:
    text=text.replace('\r\n','\n')
    text=text.replace('Chaos Edition v0.3: two banks of eight save slots.','Chaos Edition 3.0: two banks of eight save slots.')
    text=text.replace('bank 0 = eight always-complete Chaos saves with infinite lives/rings','bank 0 = eight always-complete Chaos saves with death/Berserker progression')
    text=text.replace('u8[slotAddress + 0x08] = 99','u8[slotAddress + 0x08] = 3')
    return text

def patch_character_text(text:str)->str:
    text=text.replace('\r\n','\n')
    return text.replace(RESOURCE_HOOK,'')

def patch_tree(root:Path)->None:
    saves=root/'Oxygen/sonic3air/scripts/general/saveslots.lemon'
    char=root/'Oxygen/sonic3air/scripts/maingame/character/character.lemon'
    saves.write_text(patch_saveslots_text(saves.read_text(encoding='utf-8')),encoding='utf-8')
    char.write_text(patch_character_text(char.read_text(encoding='utf-8')),encoding='utf-8')

if __name__=='__main__':
    root=Path(sys.argv[1]) if len(sys.argv)>1 else Path('sonic3air-upstream')
    patch_tree(root)
    print('patched Chaos Edition 3.0 save-bank resource behavior')
