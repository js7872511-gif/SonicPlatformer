#pragma once

namespace reborn {

enum SuperVisualState {
    SUPER_VISUAL_NOT_READY = 0,
    SUPER_VISUAL_HOOK_READY = 1,
    SUPER_VISUAL_GOLD_APPLIED = 2,
    SUPER_VISUAL_NORMAL_RESTORED = 3,
    SUPER_VISUAL_FAILED = -1,
    SUPER_VISUAL_NO_RENDERERS = -2,
};

bool install_super_visual_bridge();
bool super_visual_bridge_ready();
void request_super_visual(bool enabled);
int super_visual_state();

}  // namespace reborn
