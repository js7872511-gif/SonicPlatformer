#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("sonic3air-upstream")

# In CI we never provide Sega ROM data. Instead, when normal ROM discovery fails,
# allocate a correctly sized zero-filled buffer so the emulator interfaces used
# by LemonScript compilation have valid addressable memory.
p = root / "Oxygen/oxygenengine/source/oxygen/resources/ResourcesCache.cpp"
s = p.read_text(encoding="utf-8").replace("\r\n", "\n")
old = '''	// If ROM was still not loaded, it's time to give up now...
	if (!loaded)
	{
		return false;
	}
'''
new = '''	// Chaos Edition CI compiler mode: no proprietary ROM data is bundled.
	// Give script compilation a correctly sized zero-filled address space.
	if (!loaded)
	{
		const size_t dummySize = gameProfile.mRomCheck.mSize > 0 ? gameProfile.mRomCheck.mSize : 0x400000;
		mRom.clear();
		mRom.resize(dummySize, 0);
		RMX_LOG_INFO("Chaos Edition CI: using zero-filled dummy ROM memory for script compilation");
		return true;
	}
'''
if old not in s:
    raise SystemExit("ResourcesCache ROM failure block not found")
p.write_text(s.replace(old, new, 1), encoding="utf-8")
print("patched zero-filled CI ROM memory")
