#!/usr/bin/env python3
from pathlib import Path
import sys

HEADER_ANCHOR = "\tvoid buildPointButton(const Vec2f& center, float radius, float priority, InputManager::Control& control, InputManager::Control* control2);\n"
HEADER_INSERT = (
    HEADER_ANCHOR
    + "\tvoid buildChaosModButtons();\n"
    + "\tInputManager::Control* resolveChaosModControl(uint32 controllerIndex, const std::string& controlName);\n"
)

CPP_INCLUDE_ANCHOR = '#include "oxygen/engine/EngineMain.h"\n'
CPP_INCLUDE_INSERT = (
    CPP_INCLUDE_ANCHOR
    + '#include "oxygen/engine/modding/ModManager.h"\n'
    + '#include "oxygen/helper/JsonHelper.h"\n'
)

CPP_BUILD_ANCHOR = (
    '\tbuildRectangularButton(mSetup.mGameRecButtonCenter, Vec2f(0.15f, 0.06f), '
    '"touch_overlay_rec", nullptr, ConfigMode::State::MOVING_GAMEREC, '
    'TouchArea::SpecialType::GAMEREC, 0.1f);\n\n'
    '\tmLastScreenSize = FTX::screenSize();'
)
CPP_BUILD_REPLACEMENT = (
    '\tbuildRectangularButton(mSetup.mGameRecButtonCenter, Vec2f(0.15f, 0.06f), '
    '"touch_overlay_rec", nullptr, ConfigMode::State::MOVING_GAMEREC, '
    'TouchArea::SpecialType::GAMEREC, 0.1f);\n\n'
    '\t// Chaos Edition 3.0 core buttons use controller 3; ordinary mods use 0..2.\n'
    '\tInputManager::ControllerScheme& chaosCore = mInputManager->accessController(3);\n'
    '\tbuildRectangularButton(Vec2f(-0.42f, -0.68f), Vec2f(0.11f, 0.11f), "touch_overlay_A", &chaosCore.A, ConfigMode::State::TOUCH_UP, TouchArea::SpecialType::NONE, 0.05f);\n'
    '\tbuildRectangularButton(Vec2f(-0.14f, -0.68f), Vec2f(0.11f, 0.11f), "touch_overlay_B", &chaosCore.B, ConfigMode::State::TOUCH_UP, TouchArea::SpecialType::NONE, 0.05f);\n'
    '\tbuildRectangularButton(Vec2f( 0.14f, -0.68f), Vec2f(0.11f, 0.11f), "touch_overlay_X", &chaosCore.X, ConfigMode::State::TOUCH_UP, TouchArea::SpecialType::NONE, 0.05f);\n'
    '\tbuildRectangularButton(Vec2f( 0.42f, -0.68f), Vec2f(0.11f, 0.11f), "touch_overlay_Y", &chaosCore.Y, ConfigMode::State::TOUCH_UP, TouchArea::SpecialType::NONE, 0.05f);\n\n'
    '\t// Optional extra buttons declared by ordinary mods.\n'
    '\tbuildChaosModButtons();\n\n'
    '\tmLastScreenSize = FTX::screenSize();'
)

CPP_METHOD_ANCHOR = (
    "void TouchControlsOverlay::buildPointButton(const Vec2f& center, float radius, "
    "float priority, InputManager::Control& control, InputManager::Control* control2)\n{"
)

CPP_METHODS = r'''InputManager::Control* TouchControlsOverlay::resolveChaosModControl(uint32 controllerIndex, const std::string& controlName)
{
	if (controllerIndex > 2)
		return nullptr;

	InputManager::ControllerScheme& controller = mInputManager->accessController(controllerIndex);
	if (controlName == "A") return &controller.A;
	if (controlName == "B") return &controller.B;
	if (controlName == "X") return &controller.X;
	if (controlName == "Y") return &controller.Y;
	if (controlName == "L") return &controller.L;
	if (controlName == "R") return &controller.R;
	if (controlName == "Start") return &controller.Start;
	if (controlName == "Up") return &controller.Up;
	if (controlName == "Down") return &controller.Down;
	if (controlName == "Left") return &controller.Left;
	if (controlName == "Right") return &controller.Right;
	return nullptr;
}

void TouchControlsOverlay::buildChaosModButtons()
{
	size_t totalButtons = 0;
	for (const Mod* mod : ModManager::instance().getActiveMods())
	{
		if (totalButtons >= 32)
			break;

		const std::wstring buttonsPath = mod->mFullPath + L"chaos-buttons.json";
		if (!FTX::FileSystem->exists(buttonsPath))
			continue;

		const Json::Value root = JsonHelper::loadFile(buttonsPath);
		const Json::Value buttons = root["Buttons"];
		if (!buttons.isArray())
			continue;

		const Json::ArrayIndex count = std::min<Json::ArrayIndex>(buttons.size(), 16);
		for (Json::ArrayIndex index = 0; index < count && totalButtons < 32; ++index)
		{
			const Json::Value& button = buttons[index];
			if (!button.isObject())
				continue;

			uint32 controllerIndex = 1;
			if (button["Controller"].isUInt())
				controllerIndex = button["Controller"].asUInt();
			if (controllerIndex > 2)
				continue;

			std::string controlName = "A";
			if (button["Control"].isString())
				controlName = button["Control"].asString();
			InputManager::Control* control = resolveChaosModControl(controllerIndex, controlName);
			if (nullptr == control)
				continue;

			float x = button["X"].isNumeric() ? button["X"].asFloat() : (0.55f + 0.26f * (float)(totalButtons % 4));
			float y = button["Y"].isNumeric() ? button["Y"].asFloat() : (-0.20f + 0.26f * (float)(totalButtons / 4));
			float size = button["Size"].isNumeric() ? button["Size"].asFloat() : 0.13f;
			x = std::clamp(x, -1.65f, 1.65f);
			y = std::clamp(y, -0.95f, 0.95f);
			size = std::clamp(size, 0.06f, 0.25f);

			std::string spriteKey;
			if (button["Sprite"].isString())
				spriteKey = button["Sprite"].asString();
			if (spriteKey.empty())
			{
				if (controlName == "Start") spriteKey = "touch_overlay_start";
				else if (controlName == "Up") spriteKey = "touch_overlay_up";
				else if (controlName == "Down") spriteKey = "touch_overlay_down";
				else if (controlName == "Left") spriteKey = "touch_overlay_left";
				else if (controlName == "Right") spriteKey = "touch_overlay_right";
				else spriteKey = "touch_overlay_" + controlName;
			}

			buildRectangularButton(Vec2f(x, y), Vec2f(size, size), spriteKey.c_str(), control,
				ConfigMode::State::TOUCH_UP, TouchArea::SpecialType::NONE, size * 0.4f);
			++totalButtons;
		}
	}
}

'''

APP_ANCHOR = """void Application::onActiveModsChanged()
{
	checkActiveModsUsedFeatures();
}"""
APP_REPLACEMENT = """void Application::onActiveModsChanged()
{
	checkActiveModsUsedFeatures();
	if (nullptr != mTouchControlsOverlay)
		mTouchControlsOverlay->buildTouchControls();
}"""


def _replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)


def patch_header_text(text: str) -> str:
    text = text.replace("\r\n", "\n")
    if "buildChaosModButtons" in text:
        return text
    return _replace_once(text, HEADER_ANCHOR, HEADER_INSERT, "TouchControlsOverlay.h builder anchor")


def patch_cpp_text(text: str) -> str:
    text = text.replace("\r\n", "\n")
    if "buildChaosModButtons()" in text and "chaos-buttons.json" in text:
        return text
    text = _replace_once(text, CPP_INCLUDE_ANCHOR, CPP_INCLUDE_INSERT, "TouchControlsOverlay.cpp include anchor")
    text = _replace_once(text, CPP_BUILD_ANCHOR, CPP_BUILD_REPLACEMENT, "TouchControlsOverlay.cpp build anchor")
    text = _replace_once(text, CPP_METHOD_ANCHOR, CPP_METHODS + CPP_METHOD_ANCHOR, "TouchControlsOverlay.cpp method anchor")
    return text


def patch_application_text(text: str) -> str:
    text = text.replace("\r\n", "\n")
    if "mTouchControlsOverlay->buildTouchControls()" in text:
        return text
    return _replace_once(text, APP_ANCHOR, APP_REPLACEMENT, "Application::onActiveModsChanged")


def patch_tree(root: Path) -> None:
    header = root / "Oxygen/oxygenengine/source/oxygen/application/overlays/TouchControlsOverlay.h"
    cpp = root / "Oxygen/oxygenengine/source/oxygen/application/overlays/TouchControlsOverlay.cpp"
    app = root / "Oxygen/oxygenengine/source/oxygen/application/Application.cpp"
    header.write_text(patch_header_text(header.read_text(encoding="utf-8")), encoding="utf-8")
    cpp.write_text(patch_cpp_text(cpp.read_text(encoding="utf-8")), encoding="utf-8")
    app.write_text(patch_application_text(app.read_text(encoding="utf-8")), encoding="utf-8")


if __name__ == "__main__":
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("sonic3air-upstream")
    patch_tree(root)
    print("patched Chaos Edition 3.0 universal mod touch buttons")
