#!/usr/bin/env python3
from pathlib import Path
import sys

STARTUP_ANCHOR = '''	// First go through all mod directories recursively to gather all installed mods
	scanMods();

	// Now get the list of active mods
'''
STARTUP_REPL = '''	// First go through all mod directories recursively to gather all installed mods
	scanMods();

	// Chaos Edition 3.0: add the packaged core as an always-on synthetic mod.
	// It lives in APK assets under data/chaos_core and does not need to be copied
	// into the user's mods directory.
	{
		const std::wstring corePath = L"data/chaos_core/";
		const Json::Value root = JsonHelper::loadFile(corePath + L"mod.json");
		if (root.isObject())
		{
			Mod* core = new Mod();
			core->mUniqueID = "chaos-edition-core";
			core->mDirectoryName = L"#chaos-edition-core";
			core->mLocalDirectory = L"#chaos-edition-core";
			core->mFullPath = corePath;
			core->mLocalDirectoryHash = rmx::getMurmur2_64(core->mLocalDirectory);
			core->mState = Mod::State::ACTIVE;
			core->mDirty = false;
			core->loadFromJson(root);
			mAllMods.insert(mAllMods.begin(), core);
			mModsByLocalDirectoryHash[core->mLocalDirectoryHash] = core;
			mModsByIDHash[rmx::getMurmur2_64(core->mUniqueID)] = core;
			mActiveMods.emplace_back(core);
			RMX_LOG_INFO("Loaded built-in Chaos Edition 3.0 Core");
		}
	}

	// Now get the list of active mods
'''

DIRTY_ANCHOR = '''	for (Mod* existingMod : mAllMods)
	{
		existingMod->mDirty = true;
	}
'''
DIRTY_REPL = '''	for (Mod* existingMod : mAllMods)
	{
		// The built-in Chaos core is synthetic and will not appear in the user mods scan.
		existingMod->mDirty = (existingMod->mUniqueID != "chaos-edition-core");
	}
'''

SET_ACTIVE_ANCHOR = '''void ModManager::setActiveMods(const std::vector<Mod*>& newActiveModsList)
{
	// Reset flags
'''
SET_ACTIVE_REPL = '''void ModManager::setActiveMods(const std::vector<Mod*>& newActiveModsList)
{
	// Chaos Edition 3.0: the packaged core cannot be disabled from the normal mod list.
	std::vector<Mod*> effectiveMods = newActiveModsList;
	for (Mod* mod : mAllMods)
	{
		if (mod->mUniqueID == "chaos-edition-core")
		{
			if (std::find(effectiveMods.begin(), effectiveMods.end(), mod) == effectiveMods.end())
				effectiveMods.insert(effectiveMods.begin(), mod);
			break;
		}
	}

	// Reset flags
'''

MARK_ANCHOR = '''	// Mark the now active mods as such
	for (Mod* mod : newActiveModsList)
'''
MARK_REPL = '''	// Mark the now active mods as such
	for (Mod* mod : effectiveMods)
'''
ASSIGN_ANCHOR = '''	// Update list of active mods
	mActiveMods = newActiveModsList;
'''
ASSIGN_REPL = '''	// Update list of active mods
	mActiveMods = effectiveMods;
'''


def replace_once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {n}")
    return text.replace(old, new, 1)


def patch_text(text: str) -> str:
    text = text.replace("\r\n", "\n")
    if 'Loaded built-in Chaos Edition 3.0 Core' in text:
        return text
    text = replace_once(text, STARTUP_ANCHOR, STARTUP_REPL, "startup core injection")
    text = replace_once(text, DIRTY_ANCHOR, DIRTY_REPL, "rescan dirty handling")
    text = replace_once(text, SET_ACTIVE_ANCHOR, SET_ACTIVE_REPL, "setActiveMods setup")
    text = replace_once(text, MARK_ANCHOR, MARK_REPL, "setActiveMods effective loop")
    text = replace_once(text, ASSIGN_ANCHOR, ASSIGN_REPL, "setActiveMods assignment")
    return text


def patch_tree(root: Path):
    p = root / "Oxygen/oxygenengine/source/oxygen/application/modding/ModManager.cpp"
    p.write_text(patch_text(p.read_text(encoding="utf-8")), encoding="utf-8")


if __name__ == "__main__":
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("sonic3air-upstream")
    patch_tree(root)
    print("patched always-on packaged Chaos Core mod")
