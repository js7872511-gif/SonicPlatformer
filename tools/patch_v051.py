from pathlib import Path

p = Path("src/main.cpp")
s = p.read_text()

# Scale the concept art down from v0.5 while keeping it clearly larger than the old prototype.
s = s.replace("constexpr float H = 96.f;", "constexpr float H = 72.f;")
s = s.replace("constexpr float YOFF = 14.f;", "constexpr float YOFF = 10.f;")

# Raise Super Sonic slightly so his larger quills / aura don't visually sink into the floor.
s = s.replace(
    "    CCPoint pos(){auto p=m_player1->getPosition();p.y+=YOFF;return p;}",
    "    CCPoint pos(){auto p=m_player1->getPosition();p.y+=YOFF+(m_fields->super?3.f:0.f);return p;}"
)

# When Super Sonic turns on and we switch to hidden ship physics, lift him out of the floor
# before applying ship physics. This prevents the immortal ship collider from starting embedded.
old_super_start = """        if(on&&!m_fields->super){m_fields->wasShip=m_player1->m_isShip;m_fields->forcedShip=!m_fields->wasShip;if(m_fields->forcedShip)m_player1->toggleFlyMode(true,false);}"""
new_super_start = """        if(on&&!m_fields->super){
            m_fields->wasShip=m_player1->m_isShip;
            m_fields->forcedShip=!m_fields->wasShip;
            if(m_fields->forcedShip){
                if(m_player1->m_isOnGround){
                    auto lift=m_player1->getPosition();
                    lift.y += 12.f;
                    m_player1->setPosition(lift);
                }
                m_player1->toggleFlyMode(true,false);
                m_player1->setYVelocity(m_player1->m_isUpsideDown ? -6.5 : 6.5, 0);
            }
        }"""
assert old_super_start in s
s = s.replace(old_super_start, new_super_start, 1)

# Add one mid-air jump for the player. PlayerObject::pushButton is used so this works with
# mobile taps and QOLMod's platformer controls without needing a separate on-screen button.
double_jump = r'''

class $modify(SonicDoubleJumpPlayer, PlayerObject) {
    struct Fields {
        bool canDoubleJump = true;
    };

    bool pushButton(PlayerButton button) {
        bool airborneBefore = !m_isOnGround;
        bool eligible =
            button == PlayerButton::Jump &&
            airborneBefore &&
            !m_isShip &&
            !m_isBird &&
            !m_isDart &&
            !m_isSwing &&
            !m_isDead &&
            m_fields->canDoubleJump;

        bool result = PlayerObject::pushButton(button);

        if (eligible) {
            m_fields->canDoubleJump = false;
            double jumpVelocity = m_isUpsideDown ? -13.5 : 13.5;
            setYVelocity(jumpVelocity, 0);
            animatePlatformerJump(1.f);
            return true;
        }
        return result;
    }

    void update(float dt) {
        PlayerObject::update(dt);
        if (m_isOnGround && !m_isDead) {
            m_fields->canDoubleJump = true;
        }
    }
};
'''

assert "class $modify(SonicDoubleJumpPlayer, PlayerObject)" not in s
s += double_jump

p.write_text(s)
print("Applied Sonic Platformer v0.5.1 size, double-jump and Super ground-fix patch")
