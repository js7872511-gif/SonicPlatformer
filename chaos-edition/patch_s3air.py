from pathlib import Path

ROOT = Path("sonic3air")


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly 1 match, found {count}: {old[:80]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# -----------------------------------------------------------------------------
# TouchControlsOverlay: add 13 real touch hitboxes owned by the engine.
# Visuals are drawn by the LemonScript mod so they can appear/disappear by form.
# -----------------------------------------------------------------------------
h = ROOT / "Oxygen/oxygenengine/source/oxygen/application/overlays/TouchControlsOverlay.h"
replace_once(
    h,
    "\t\tSpecialType mSpecialType = SpecialType ::NONE;\n\t\tRectf mRect;",
    "\t\tSpecialType mSpecialType = SpecialType ::NONE;\n"
    "\t\tuint8 mCustomAction = 0;\t// Chaos Edition action ID, 0 = ordinary control\n"
    "\t\tRectf mRect;",
)
replace_once(
    h,
    "\tvoid enableConfigMode(bool enable);\n\n\tvirtual void initialize() override;",
    "\tvoid enableConfigMode(bool enable);\n"
    "\tinline uint8 getCustomAction() const { return mCustomAction; }\n\n"
    "\tvirtual void initialize() override;",
)
replace_once(
    h,
    "\tvoid buildPointButton(const Vec2f& center, float radius, float priority, InputManager::Control& control, InputManager::Control* control2);\n",
    "\tvoid buildPointButton(const Vec2f& center, float radius, float priority, InputManager::Control& control, InputManager::Control* control2);\n"
    "\tvoid buildChaosButton(const Vec2f& center, float radius, uint8 actionId);\n",
)
replace_once(
    h,
    "\tbool mGameRecPressed = false;\n\n\tConfigMode mConfigMode;",
    "\tbool mGameRecPressed = false;\n"
    "\tuint8 mCustomAction = 0;\n\n"
    "\tConfigMode mConfigMode;",
)

cpp = ROOT / "Oxygen/oxygenengine/source/oxygen/application/overlays/TouchControlsOverlay.cpp"
replace_once(
    cpp,
    "\tbuildRectangularButton(mSetup.mGameRecButtonCenter, Vec2f(0.15f, 0.06f), \"touch_overlay_rec\", nullptr, ConfigMode::State::MOVING_GAMEREC, TouchArea::SpecialType::GAMEREC, 0.1f);\n\n\tmLastScreenSize = FTX::screenSize();",
    "\tbuildRectangularButton(mSetup.mGameRecButtonCenter, Vec2f(0.15f, 0.06f), \"touch_overlay_rec\", nullptr, ConfigMode::State::MOVING_GAMEREC, TouchArea::SpecialType::GAMEREC, 0.1f);\n\n"
    "\t// Chaos Edition: true additional touch controls.\n"
    "\t// Seven emeralds across the upper center.\n"
    "\tbuildChaosButton(Vec2f(-0.66f, -0.70f), 0.105f,  7);\n"
    "\tbuildChaosButton(Vec2f(-0.44f, -0.70f), 0.105f,  8);\n"
    "\tbuildChaosButton(Vec2f(-0.22f, -0.70f), 0.105f,  9);\n"
    "\tbuildChaosButton(Vec2f( 0.00f, -0.70f), 0.105f, 10);\n"
    "\tbuildChaosButton(Vec2f( 0.22f, -0.70f), 0.105f, 11);\n"
    "\tbuildChaosButton(Vec2f( 0.44f, -0.70f), 0.105f, 12);\n"
    "\tbuildChaosButton(Vec2f( 0.66f, -0.70f), 0.105f, 13);\n"
    "\t// Transform buttons.\n"
    "\tbuildChaosButton(Vec2f(0.55f, -0.38f), 0.145f, 1);\n"
    "\tbuildChaosButton(Vec2f(0.88f, -0.38f), 0.145f, 2);\n"
    "\t// Form abilities. Water SLIP intentionally shares Rush's slot because\n"
    "\t// the two forms are mutually exclusive.\n"
    "\tbuildChaosButton(Vec2f(0.55f, -0.08f), 0.135f, 3);\n"
    "\tbuildChaosButton(Vec2f(0.55f, -0.08f), 0.135f, 4);\n"
    "\tbuildChaosButton(Vec2f(0.84f, -0.08f), 0.135f, 5);\n"
    "\tbuildChaosButton(Vec2f(1.13f, -0.08f), 0.135f, 6);\n\n"
    "\tmLastScreenSize = FTX::screenSize();",
)
replace_once(
    cpp,
    "void TouchControlsOverlay::buildPointButton(const Vec2f& center, float radius, float priority, InputManager::Control& control, InputManager::Control* control2)\n{",
    "void TouchControlsOverlay::buildChaosButton(const Vec2f& center, float radius, uint8 actionId)\n"
    "{\n"
    "\tTouchArea& touchArea = vectorAdd(mTouchAreas);\n"
    "\ttouchArea.mRect.set(center, Vec2f::ZERO);\n"
    "\ttouchArea.mRadius = radius;\n"
    "\ttouchArea.mPriority = 2.0f;\n"
    "\ttouchArea.mCustomAction = actionId;\n"
    "}\n\n"
    "void TouchControlsOverlay::buildPointButton(const Vec2f& center, float radius, float priority, InputManager::Control& control, InputManager::Control* control2)\n{",
)
replace_once(
    cpp,
    "\t// Update touch areas\n\tbool gameRecPressed = false;",
    "\t// Update touch areas\n"
    "\tmCustomAction = 0;\n"
    "\tbool gameRecPressed = false;",
)
replace_once(
    cpp,
    "\t\t\t\t\tdefault:\n\t\t\t\t\t{\n\t\t\t\t\t\tfor (InputManager::Control* control : touchArea->mControls)\n\t\t\t\t\t\t\tcontrol->mState = true;",
    "\t\t\t\t\tdefault:\n\t\t\t\t\t{\n"
    "\t\t\t\t\t\tif (touchArea->mCustomAction != 0)\n"
    "\t\t\t\t\t\t{\n"
    "\t\t\t\t\t\t\tmCustomAction = touchArea->mCustomAction;\n"
    "\t\t\t\t\t\t\tbreak;\n"
    "\t\t\t\t\t\t}\n"
    "\t\t\t\t\t\tfor (InputManager::Control* control : touchArea->mControls)\n"
    "\t\t\t\t\t\t\tcontrol->mState = true;",
)

# -----------------------------------------------------------------------------
# ControlsIn: carry the custom action through unused input bits 12..15.
# The stock game uses only bits 0..11. LemonScript can already read all 16 bits
# with Input.getController / Input.getControllerPrevious.
# -----------------------------------------------------------------------------
controls = ROOT / "Oxygen/oxygenengine/source/oxygen/application/input/ControlsIn.cpp"
replace_once(
    controls,
    '#include "oxygen/application/Configuration.h"\n',
    '#include "oxygen/application/Configuration.h"\n'
    '#include "oxygen/application/overlays/TouchControlsOverlay.h"\n',
)
replace_once(
    controls,
    "\t\t// Remove all flags from actual inputs that are still ignored\n\t\tmGamepad[padIndex].mCurrentInput &= ~mGamepad[padIndex].mIgnoreInput;\n\t}\n}",
    "\t\t// Remove all flags from actual inputs that are still ignored\n"
    "\t\tmGamepad[padIndex].mCurrentInput &= ~mGamepad[padIndex].mIgnoreInput;\n"
    "\t}\n\n"
    "\t// Chaos Edition custom touch action. Keep stock controls in the lower 12\n"
    "\t// bits and encode one independent action ID in the unused upper nibble.\n"
    "\tconst uint16 customAction = (uint16)(TouchControlsOverlay::instance().getCustomAction() & 0x0f);\n"
    "\tmGamepad[0].mCurrentInput = (mGamepad[0].mCurrentInput & 0x0fff) | (customAction << 12);\n"
    "}",
)

# First test build: compile only the user's phone architecture.
gradle = ROOT / "Oxygen/sonic3air/build/_android/app/build.gradle"
replace_once(
    gradle,
    "abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'",
    "abiFilters 'arm64-v8a'",
)

print("Chaos Edition source patch applied successfully")
