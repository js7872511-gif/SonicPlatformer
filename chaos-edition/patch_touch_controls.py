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
	// Chaos Edition custom touch hitboxes -- comfortable layout v0.2.
	// These use secondary virtual controllers so they do not consume
	// A/B/X/Y/L/R/Start on player 1. Visuals are drawn by LemonScript.
	// -----------------------------------------------------------------
	const float emeraldRadius = 0.10f;

	// Seven Chaos Emerald buttons in a compact 4+3 grid in the empty
	// bottom-center gap between the stock D-pad and face buttons.
	buildPointButton(Vec2f(-0.35f, +0.65f), emeraldRadius, 3.0f, chaosEmeralds.A,     nullptr); // red
	buildPointButton(Vec2f(-0.12f, +0.65f), emeraldRadius, 3.0f, chaosEmeralds.B,     nullptr); // blue
	buildPointButton(Vec2f(+0.12f, +0.65f), emeraldRadius, 3.0f, chaosEmeralds.X,     nullptr); // green
	buildPointButton(Vec2f(+0.35f, +0.65f), emeraldRadius, 3.0f, chaosEmeralds.Y,     nullptr); // yellow
	buildPointButton(Vec2f(-0.23f, +0.86f), emeraldRadius, 3.0f, chaosEmeralds.L,     nullptr); // purple
	buildPointButton(Vec2f(+0.00f, +0.86f), emeraldRadius, 3.0f, chaosEmeralds.R,     nullptr); // cyan
	buildPointButton(Vec2f(+0.23f, +0.86f), emeraldRadius, 3.0f, chaosEmeralds.Start, nullptr); // white

	// Three context-sensitive thumb slots just above the stock face buttons.
	// Slot 1: SUPER while normal, SLIP while Water, RUSH while Super.
	buildPointButton(Vec2f(+0.65f, -0.17f), 0.13f, 3.2f, chaosForms.A, &chaosForms.X);
	mTouchAreas.back().mControls.push_back(&chaosForms.Y);

	// Slot 2: WATER while normal/Water, SKY while Super.
	buildPointButton(Vec2f(+1.08f, -0.17f), 0.13f, 3.2f, chaosForms.B, &chaosForms.L);

	// Slot 3: CHAOS while Super.
	buildPointButton(Vec2f(+1.52f, -0.17f), 0.13f, 3.2f, chaosForms.R, nullptr);

'''
if anchor2 not in text:
    raise SystemExit('start-button anchor not found')
text = text.replace(anchor2, custom + anchor2, 1)

path.write_text(text, encoding='utf-8')
print(f'patched {path}')