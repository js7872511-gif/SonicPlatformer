import unittest
from patch_universal_buttons import patch_header_text, patch_cpp_text, patch_application_text


class UniversalButtonsPatchTests(unittest.TestCase):
    def test_header_declares_generic_mod_button_builder(self):
        src = """class TouchControlsOverlay {
private:
\tvoid buildPointButton(const Vec2f& center, float radius, float priority, InputManager::Control& control, InputManager::Control* control2);
\tvoid buildRectangularButton(const Vec2f& center, const Vec2f& halfExtend, const char* spriteKey, InputManager::Control* control, ConfigMode::State reactToState, TouchArea::SpecialType specialType = TouchArea::SpecialType::NONE, float radius = 0.35f);
};"""
        out = patch_header_text(src)
        self.assertIn("buildChaosModButtons", out)
        self.assertIn("resolveChaosModControl", out)

    def stable_cpp_fixture(self):
        return """#include \"oxygen/application/overlays/TouchControlsOverlay.h\"
#include \"oxygen/application/Application.h\"
#include \"oxygen/application/EngineMain.h\"

void TouchControlsOverlay::buildTouchControls()
{
\tInputManager::ControllerScheme& controller = mInputManager->accessController(0);
\tConfiguration& config = Configuration::instance();

\tmTouchAreas.clear();
\tmVisualElements.clear();

\tbuildRectangularButton(mSetup.mStartButtonCenter, Vec2f(0.18f, 0.06f), \"touch_overlay_start\", &controller.Start, ConfigMode::State::MOVING_START);
\tbuildRectangularButton(mSetup.mGameRecButtonCenter, Vec2f(0.15f, 0.06f), \"touch_overlay_rec\", nullptr, ConfigMode::State::MOVING_GAMEREC, TouchArea::SpecialType::GAMEREC, 0.1f);

\tmLastScreenSize = FTX::screenSize();
}

void TouchControlsOverlay::buildPointButton(const Vec2f& center, float radius, float priority, InputManager::Control& control, InputManager::Control* control2)
{"""

    def test_cpp_reads_optional_sidecar_from_all_active_mods(self):
        out = patch_cpp_text(self.stable_cpp_fixture())
        self.assertIn('#include "oxygen/application/EngineMain.h"', out)
        self.assertIn('#include "oxygen/application/modding/ModManager.h"', out)
        self.assertNotIn('#include "oxygen/engine/modding/ModManager.h"', out)
        self.assertIn('chaos-buttons.json', out)
        self.assertIn('ModManager::instance().getActiveMods()', out)
        self.assertIn('Controller', out)
        self.assertIn('Control', out)
        self.assertIn('Sprite', out)
        self.assertIn('buildChaosModButtons();', out)
        self.assertNotIn('emerald', out.lower())
        self.assertNotIn('water', out.lower())

    def test_mod_change_rebuilds_touch_layout(self):
        src = """void Application::onActiveModsChanged()
{
\tcheckActiveModsUsedFeatures();
}"""
        out = patch_application_text(src)
        self.assertIn("mTouchControlsOverlay->buildTouchControls()", out)

    def test_controller_zero_and_secondary_controllers_supported(self):
        out = patch_cpp_text(self.stable_cpp_fixture())
        self.assertIn("controllerIndex > 2", out)
        self.assertIn("accessController(controllerIndex)", out)
        self.assertIn("accessController(3)", out)
        self.assertIn("&chaosCore.A", out)
        self.assertIn("&chaosCore.B", out)
        self.assertIn("&chaosCore.X", out)
        self.assertIn("&chaosCore.Y", out)
        for name in ["A","B","X","Y","L","R","Start","Up","Down","Left","Right"]:
            self.assertIn(f'controlName == "{name}"', out)


if __name__ == "__main__":
    unittest.main()
