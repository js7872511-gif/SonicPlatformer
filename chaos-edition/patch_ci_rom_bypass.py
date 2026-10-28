#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("sonic3air-upstream")
p = root / "Oxygen/oxygenengine/source/oxygen/application/GameLoader.cpp"
s = p.read_text(encoding="utf-8").replace("\r\n", "\n")
old = '''#else
					RMX_ERROR("ROM could not be loaded!\\nAn original " + gameProfile.mRomInfos[0].mSteamGameName + " ROM must be added manually. See the Manual for details.\\n\\nThe application will now close.", );
					return UpdateResult::FAILURE;
'''
new = '''#else
					// Chaos Edition CI compiler mode: do not bundle a proprietary ROM.
					RMX_LOG_INFO("Chaos Edition CI: continuing without ROM for script compilation");
					mState = State::ROM_LOADED;
					return UpdateResult::CONTINUE_IMMEDIATE;
'''
if old not in s:
    raise SystemExit("Linux ROM failure block not found")
p.write_text(s.replace(old, new, 1), encoding="utf-8")
print("patched Linux CI ROM bypass")
