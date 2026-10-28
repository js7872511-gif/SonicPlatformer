from pathlib import Path

p = Path("sonic3air/Oxygen/oxygenengine/source/oxygen/application/overlays/TouchControlsOverlay.cpp")
s = p.read_text(encoding="utf-8")
old = '''\t// Form abilities. Water SLIP intentionally shares Rush's slot because
\t// the two forms are mutually exclusive.
\tbuildChaosButton(Vec2f(0.55f, -0.08f), 0.135f, 3);
\tbuildChaosButton(Vec2f(0.55f, -0.08f), 0.135f, 4);
\tbuildChaosButton(Vec2f(0.84f, -0.08f), 0.135f, 5);
\tbuildChaosButton(Vec2f(1.13f, -0.08f), 0.135f, 6);
'''
new = '''\t// Form abilities use distinct hitboxes even when only one form's visuals
\t// are shown. This prevents the touch-area priority picker from masking Rush.
\tbuildChaosButton(Vec2f(0.40f, -0.08f), 0.125f, 3);
\tbuildChaosButton(Vec2f(0.62f, -0.08f), 0.125f, 4);
\tbuildChaosButton(Vec2f(0.86f, -0.08f), 0.125f, 5);
\tbuildChaosButton(Vec2f(1.10f, -0.08f), 0.125f, 6);
'''
if s.count(old) != 1:
    raise RuntimeError(f"expected one ability hitbox block, found {s.count(old)}")
p.write_text(s.replace(old, new, 1), encoding="utf-8")
print("Separated Water Slip and Super ability hitboxes")
