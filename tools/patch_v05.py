from pathlib import Path
import re

p = Path("src/main.cpp")
s = p.read_text()

s = s.replace("constexpr float H = 58.f;", "constexpr float H = 96.f;")
s = s.replace("constexpr float YOFF = 12.f;", "constexpr float YOFF = 14.f;")

old_fields = """        bool super=false, transforming=false, spin=false, forcedShip=false, wasShip=false;
        int trailIndex=0;
        float runT=0, trailT=0, transT=0, spinA=0, hitFx=0;"""
new_fields = """        bool super=false, transforming=false, spin=false, forcedShip=false, wasShip=false;
        bool wasGround=true;
        int trailIndex=0;
        int pose=-1;
        float runT=0, trailT=0, transT=0, spinA=0, hitFx=0, landT=0;"""
assert old_fields in s
s = s.replace(old_fields, new_fields, 1)

anchor = "    void switchSpin(bool spinning){if(spinning!=m_fields->spin)refresh(spinning);}\n"
assert anchor in s
pose_helpers = r'''

    std::string poseName(int pose) {
        const char* stem = m_fields->super ? "super_" : "sonic_";
        const char* suffix = "idle.png";
        switch(pose) {
            case 1: suffix="run1.png"; break;
            case 2: suffix="run2.png"; break;
            case 3: suffix="run3.png"; break;
            case 4: suffix="run4.png"; break;
            case 5: suffix="spin.png"; break;
            case 6: suffix="fall.png"; break;
            case 7: suffix="land.png"; break;
            case 8: suffix="death.png"; break;
            case 9: suffix="run4.png"; break;
            default: suffix="idle.png"; break;
        }
        std::string n = stem;
        n += suffix;
        return Mod::get()->expandSpriteName(n);
    }

    CCTexture2D* poseTexture(int pose) {
        auto n = poseName(pose);
        auto* t = CCTextureCache::sharedTextureCache()->addImage(n.c_str(), false);
        if(t) t->setAntiAliasTexParameters();
        return t;
    }

    void setPose(int pose) {
        if(m_fields->pose == pose) return;
        auto* t = poseTexture(pose);
        if(!t) return;
        m_fields->pose = pose;
        m_fields->spin = (pose == 5);
        for(auto* spr : {m_fields->shadow,m_fields->outer,m_fields->glow,m_fields->avatar}) {
            if(spr) spr->setTexture(t);
        }
        bool spinning = pose == 5;
        if(m_fields->shadow)m_fields->shadow->setScale(fit(m_fields->shadow,spinning?.92f:1.03f));
        if(m_fields->outer)m_fields->outer->setScale(fit(m_fields->outer,spinning?1.20f:(m_fields->super?1.45f:1.30f)));
        if(m_fields->glow)m_fields->glow->setScale(fit(m_fields->glow,spinning?1.08f:(m_fields->super?1.22f:1.13f)));
        if(m_fields->avatar)m_fields->avatar->setScale(fit(m_fields->avatar,spinning?.94f:1.f));
    }
'''
s = s.replace(anchor, anchor + pose_helpers, 1)

old_hide = "        if(m_player1->m_iconSpriteWhitener)m_player1->m_iconSpriteWhitener->setVisible(false);"
new_hide = old_hide + "\n        if(m_player1->m_vehicleSprite)m_player1->m_vehicleSprite->setVisible(false);"
assert old_hide in s
s = s.replace(old_hide, new_hide, 1)

new_sync = r'''    void sync(float dt) {
        auto p=pos();
        float vx=(float)m_player1->m_platformerXVelocity, vy=(float)m_player1->m_yVelocity;
        bool ship=m_player1->m_isShip, ground=m_player1->m_isOnGround;

        if(ground && !m_fields->wasGround) m_fields->landT=.13f;
        m_fields->wasGround=ground;
        if(m_fields->landT>0) m_fields->landT-=dt;

        int wanted=0;
        if(m_fields->transforming) {
            wanted=0;
        } else if(ship) {
            wanted=9;
        } else if(!ground) {
            wanted=5;
        } else if(m_fields->landT>0) {
            wanted=7;
        } else if(std::abs(vx)>.12f) {
            m_fields->runT += dt * std::clamp(std::abs(vx)*.16f,1.0f,2.4f);
            wanted = 1 + ((int)(m_fields->runT*4.f) % 4);
        } else {
            m_fields->runT=0;
            wanted=0;
        }
        setPose(wanted);

        bool spinning=(wanted==5);
        if(spinning){m_fields->spinA+=dt*(m_fields->super?980.f:820.f);if(m_fields->spinA>360)m_fields->spinA-=360;}
        else m_fields->spinA=0;

        bool flip=m_fields->avatar?m_fields->avatar->isFlipX():false;
        if(vx<-.05f)flip=true; else if(vx>.05f)flip=false;
        float lean=0,bob=0;
        if(ship){lean=std::clamp(vy*1.8f,-18.f,18.f);m_fields->runT+=dt;bob=std::sin(m_fields->runT*8.f)*1.8f;}
        else if(ground&&std::abs(vx)>.12f){bob=std::sin(m_fields->runT*11.f)*1.6f;lean=std::clamp(vx*-1.1f,-8.f,8.f);}
        else if(!ground && !spinning){lean=std::clamp(vy*1.3f,-15.f,15.f);}
        p.y+=bob;

        auto place=[&](CCSprite* spr,float y,float rot){if(!spr)return;spr->setPosition({p.x,p.y+y});spr->setFlipX(flip);spr->setRotation(rot);};
        place(m_fields->avatar,0,spinning?m_fields->spinA:lean);
        place(m_fields->glow,0,spinning?m_fields->spinA:lean);
        place(m_fields->outer,0,spinning?-m_fields->spinA*.55f:lean*.7f);
        if(m_fields->shadow){m_fields->shadow->setPosition({p.x+(flip?3.f:-3.f),p.y-4});m_fields->shadow->setFlipX(flip);m_fields->shadow->setRotation(spinning?m_fields->spinA:lean);}
        if(m_fields->bubble&&m_fields->bubble->isVisible())m_fields->bubble->setPosition({p.x,p.y+67});
        if(m_fields->bubbleText&&m_fields->bubbleText->isVisible())m_fields->bubbleText->setPosition({p.x,p.y+67});
    }

'''
s, n = re.subn(r"    void sync\(float dt\) \{.*?\n    \}\n\n(?=    void trail\(float dt\))", new_sync, s, count=1, flags=re.S)
assert n == 1, n

old_trail = "        if(auto* tx=tex(m_fields->super,m_fields->spin))t->setTexture(tx);"
new_trail = "        if(auto* tx=poseTexture(m_fields->pose<0?0:m_fields->pose))t->setTexture(tx);"
assert old_trail in s
s = s.replace(old_trail, new_trail, 1)

old_super = "        m_fields->super=on;refresh(false);"
new_super = "        m_fields->super=on;m_fields->pose=-1;refresh(false);"
assert old_super in s
s = s.replace(old_super, new_super, 1)

old_death = "        PlayLayer::destroyPlayer(p,o);"
new_death = "        if(p==m_player1){m_fields->pose=-1;setPose(8);}\n        PlayLayer::destroyPlayer(p,o);"
assert old_death in s
s = s.replace(old_death, new_death, 1)

old_reset = "PlayLayer::resetLevel();m_fields->runT=m_fields->trailT=m_fields->transT=m_fields->spinA=m_fields->hitFx=0;m_fields->transforming=false;"
new_reset = "PlayLayer::resetLevel();m_fields->runT=m_fields->trailT=m_fields->transT=m_fields->spinA=m_fields->hitFx=m_fields->landT=0;m_fields->transforming=false;m_fields->pose=-1;m_fields->wasGround=true;"
assert old_reset in s
s = s.replace(old_reset, new_reset, 1)

p.write_text(s)
print("Applied Sonic Platformer v0.5 animation patch")
