from pathlib import Path

p = Path("src/main.cpp")
s = p.read_text()

# Raise both forms so their feet sit on top of the platform instead of inside it.
s = s.replace("constexpr float YOFF = 10.f;", "constexpr float YOFF = 16.f;")
s = s.replace(
    "CCPoint pos(){auto p=m_player1->getPosition();p.y+=YOFF+(m_fields->super?3.f:0.f);return p;}",
    "CCPoint pos(){auto p=m_player1->getPosition();p.y+=YOFF+(m_fields->super?6.f:0.f);return p;}"
)

# IMPORTANT: transforming into Super no longer forces ship physics immediately.
# That was the reason tapping while Super could push the player into the floor.
old = """        if(on&&!m_fields->super){
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
        }
        else if(!on&&m_fields->super&&m_fields->forcedShip){m_player1->toggleFlyMode(false,false);m_fields->forcedShip=false;}"""
new = """        if(on&&!m_fields->super){
            m_fields->wasShip=m_player1->m_isShip;
            m_fields->forcedShip=false;
        }
        else if(!on&&m_fields->super){
            m_fields->forcedShip=false;
        }"""
assert old in s, "v0.5.1 Super block not found"
s = s.replace(old, new, 1)

p.write_text(s)
print("Applied Sonic Platformer v0.5.2 ground alignment + no forced ship-on-transform fix")
