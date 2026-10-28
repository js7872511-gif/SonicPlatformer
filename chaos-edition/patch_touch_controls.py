#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('sonic3air')
path = root / 'Oxygen/oxygenengine/source/oxygen/application/overlays/TouchControlsOverlay.cpp'
text = path.read_text(encoding='utf-8')

anchor = '\tInputManager::ControllerScheme& controller = mInputManager->accessController(0);\n'
insert = anchor + '\t// Chaos Edition: reserve controller 2 for emerald buttons and controller 3 for form controls.\n\t// Player 1 controls remain completely untouched.\n\tInputManager::ControllerScheme& chaosEmeralds = mInputManager->accessController(1);\n\tInputManager::ControllerScheme& chaosForms = mInputManager->accessController(2);\n'
if anchor not in text:
    raise SystemExit('controller anchor not found')
text = text.replace(anchor, insert, 1)

anchor2 = '\tbuildRectangularButton(mSetup.mStartButtonCenter, Vec2f(0.18f, 0.06f), "touch_overlay_start", &controller.Start, ConfigMode::State::MOVING_START);\n'
custom = r'''
	// -----------------------------------------------------------------
	// Chaos Edition custom touch hitboxes.
	// These use secondary virtual controllers so they do not consume
	// A/B/X/Y/L/R/Start on player 1. Visuals are drawn by LemonScript.
	// -----------------------------------------------------------------
	const float chaosRadius = 0.115f;

	// Seven Chaos Emerald buttons -- always present along the lower center.
	buildPointButton(Vec2f(-0.66f, +0.78f), chaosRadius, 3.0f, chaosEmeralds.A,     nullptr); // red
	buildPointButton(Vec2f(-0.44f, +0.78f), chaosRadius, 3.0f, chaosEmeralds.B,     nullptr); // blue
	buildPointButton(Vec2f(-0.22f, +0.78f), chaosRadius, 3.0f, chaosEmeralds.X,     nullptr); // green
	buildPointButton(Vec2f(+0.00f, +0.78f), chaosRadius, 3.0f, chaosEmeralds.Y,     nullptr); // yellow
	buildPointButton(Vec2f(+0.22f, +0.78f), chaosRadius, 3.0f, chaosEmeralds.L,     nullptr); // purple
	buildPointButton(Vec2f(+0.44f, +0.78f), chaosRadius, 3.0f, chaosEmeralds.R,     nullptr); // cyan
	buildPointButton(Vec2f(+0.66f, +0.78f), chaosRadius, 3.0f, chaosEmeralds.Start, nullptr); // white

	// Form buttons. LemonScript decides which actions are valid/visible.
	buildPointButton(Vec2f(+1.20f, -0.48f), 0.14f, 3.2f, chaosForms.A, nullptr); // SUPER
	buildPointButton(Vec2f(+1.52f, -0.48f), 0.14f, 3.2f, chaosForms.B, nullptr); // WATER
	buildPointButton(Vec2f(+0.88f, -0.12f), 0.12f, 3.1f, chaosForms.X, nullptr); // SLIP
	buildPointButton(Vec2f(+1.12f, -0.12f), 0.12f, 3.1f, chaosForms.Y, nullptr); // SUPER RUSH
	buildPointButton(Vec2f(+1.36f, -0.12f), 0.12f, 3.1f, chaosForms.L, nullptr); // SKY BURST
	buildPointButton(Vec2f(+1.60f, -0.12f), 0.12f, 3.1f, chaosForms.R, nullptr); // CHAOS BURST

'''
if anchor2 not in text:
    raise SystemExit('start-button anchor not found')
text = text.replace(anchor2, custom + anchor2, 1)

path.write_text(text, encoding='utf-8')
print(f'patched {path}')
