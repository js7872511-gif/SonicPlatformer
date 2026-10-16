#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <Geode/binding/CCMenuItemSpriteExtra.hpp>
#include <Geode/binding/ButtonSprite.hpp>
#include <array>
#include <algorithm>
#include <cmath>
using namespace geode::prelude;

namespace {
constexpr float PI = 3.14159265359f;
constexpr float H = 58.f;
constexpr float YOFF = 12.f;
constexpr float TRANSFORM_TIME = 3.f;
constexpr float SUPER_MAX = 11.75f;
constexpr float SUPER_ACCEL = 18.f;

std::string texName(bool super, bool spin) {
    if (spin) return super ? "super_spin.png"_spr : "sonic_spin.png"_spr;
    return super ? "super_idle.png"_spr : "sonic_idle.png"_spr;
}
CCTexture2D* tex(bool super, bool spin) {
    auto n = texName(super, spin);
    auto* t = CCTextureCache::sharedTextureCache()->addImage(n.c_str(), false);
    if (t) t->setAntiAliasTexParameters();
    return t;
}
float fit(CCSprite* s, float mul = 1.f) {
    if (!s || s->getContentSize().height < 1.f) return 1.f;
    return H / s->getContentSize().height * mul;
}
ccColor3B aura(bool super) { return super ? ccc3(255,215,45) : ccc3(75,170,255); }
const std::array<ccColor3B,7> EM = {
    ccc3(255,55,55), ccc3(255,220,45), ccc3(55,115,255),
    ccc3(65,235,255), ccc3(70,235,95), ccc3(235,75,255), ccc3(245,245,255)
};
}

class $modify(SonicPlayLayer, PlayLayer) {
    struct Fields {
        CCSprite *avatar=nullptr,*shadow=nullptr,*glow=nullptr,*outer=nullptr;
        CCMenuItemSpriteExtra* superBtn=nullptr;
        CCSprite *bubble=nullptr;
        CCLabelBMFont* bubbleText=nullptr;
        CCLayerColor* flash=nullptr;
        std::array<CCSprite*,8> trails{};
        std::array<CCSprite*,7> emeralds{};
        std::array<CCSprite*,8> rings{};
        bool super=false, transforming=false, spin=false, forcedShip=false, wasShip=false;
        int trailIndex=0;
        float runT=0, trailT=0, transT=0, spinA=0, hitFx=0;
    };

    bool init(GJGameLevel* level, bool replay, bool dontCreate) {
        if (!PlayLayer::init(level,replay,dontCreate)) return false;
        if (!m_player1) return true;
        makeAvatar();
        makeButton();
        makeFx();
        setSuper(false,false);
        hideNative();
        sync(0);
        return true;
    }

    void addLayer(CCSprite*& out, CCNode* p, int z, float m, GLubyte a, ccColor3B c) {
        auto n=texName(false,false);
        out=CCSprite::create(n.c_str());
        if (!out) return;
        out->setColor(c); out->setOpacity(a); out->setScale(fit(out,m)); p->addChild(out,z);
    }

    void makeAvatar() {
        auto* p=m_player1->getParent(); if(!p)p=this;
        int z=m_player1->getZOrder();
        addLayer(m_fields->shadow,p,z+1,1.03f,95,ccc3(8,12,24));
        addLayer(m_fields->outer,p,z+2,1.30f,35,aura(false));
        addLayer(m_fields->glow,p,z+3,1.13f,75,aura(false));
        auto n=texName(false,false);
        m_fields->avatar=CCSprite::create(n.c_str());
        if(m_fields->avatar){m_fields->avatar->setScale(fit(m_fields->avatar));p->addChild(m_fields->avatar,z+4);}
        for(auto& t:m_fields->trails){
            t=CCSprite::create(n.c_str());
            if(t){t->setVisible(false);t->setScale(fit(t,.98f));p->addChild(t,z);}
        }
    }

    void makeButton() {
        auto sz=CCDirector::sharedDirector()->getWinSize();
        auto* bs=ButtonSprite::create("SUPER","goldFont.fnt","GJ_button_01.png",.82f);
        if(!bs)return;
        bs->setScale(.78f);
        auto* menu=CCMenu::create(); menu->setPosition({0,0}); addChild(menu,10000);
        auto* b=CCMenuItemSpriteExtra::create(bs,this,menu_selector(SonicPlayLayer::onSuper));
        b->setPosition({sz.width-67.f,62.f}); menu->addChild(b); m_fields->superBtn=b;
    }

    CCSprite* gem(CCNode* p, ccColor3B c) {
        auto* g=CCSprite::create("emerald.png"_spr); if(!g)return nullptr;
        g->setColor(c); g->setScale(.13f); g->setOpacity(0); g->setVisible(false);
        auto* halo=CCSprite::create("emerald.png"_spr);
        if(halo){halo->setColor(c);halo->setOpacity(55);halo->setScale(1.38f);
            auto s=g->getContentSize();halo->setPosition({s.width/2,s.height/2});g->addChild(halo,-1);}
        p->addChild(g,m_player1->getZOrder()+8); return g;
    }

    void makeFx() {
        auto* p=m_player1->getParent(); if(!p)p=this;
        for(size_t i=0;i<7;i++)m_fields->emeralds[i]=gem(p,EM[i]);
        for(auto& r:m_fields->rings){
            r=CCSprite::create("ring.png"_spr);
            if(r){r->setColor(ccc3(255,220,70));r->setScale(.18f);r->setVisible(false);r->setOpacity(0);p->addChild(r,m_player1->getZOrder()+7);}
        }
        m_fields->flash=CCLayerColor::create(ccc4(255,242,165,255));
        if(m_fields->flash){m_fields->flash->setOpacity(0);addChild(m_fields->flash,20000);}
    }

    void refresh(bool spinning) {
        m_fields->spin=spinning;
        auto* t=tex(m_fields->super,spinning); if(!t)return;
        for(auto* s:{m_fields->shadow,m_fields->outer,m_fields->glow,m_fields->avatar})if(s)s->setTexture(t);
        if(m_fields->shadow)m_fields->shadow->setScale(fit(m_fields->shadow,spinning?.92f:1.03f));
        if(m_fields->outer)m_fields->outer->setScale(fit(m_fields->outer,spinning?1.18f:1.30f));
        if(m_fields->glow)m_fields->glow->setScale(fit(m_fields->glow,spinning?1.05f:1.13f));
        if(m_fields->avatar)m_fields->avatar->setScale(fit(m_fields->avatar,spinning?.92f:1.f));
    }
    void switchSpin(bool spinning){if(spinning!=m_fields->spin)refresh(spinning);}

    void hideNative() {
        m_player1->setVisible(false);m_player1->setOpacity(0);m_player1->m_isHidden=true;
        if(m_player1->m_iconSprite)m_player1->m_iconSprite->setVisible(false);
        if(m_player1->m_iconSpriteSecondary)m_player1->m_iconSpriteSecondary->setVisible(false);
        if(m_player1->m_iconSpriteWhitener)m_player1->m_iconSpriteWhitener->setVisible(false);
    }
    CCPoint pos(){auto p=m_player1->getPosition();p.y+=YOFF;return p;}

    void sync(float dt) {
        auto p=pos();
        float vx=(float)m_player1->m_platformerXVelocity, vy=(float)m_player1->m_yVelocity;
        bool ship=m_player1->m_isShip, ground=m_player1->m_isOnGround;
        bool spinning=!ship&&!ground&&!m_fields->transforming;
        switchSpin(spinning);
        if(spinning){m_fields->spinA+=dt*(m_fields->super?980.f:820.f);if(m_fields->spinA>360)m_fields->spinA-=360;}
        else m_fields->spinA=0;

        bool flip=m_fields->avatar?m_fields->avatar->isFlipX():false;
        if(vx<-.05f)flip=true; else if(vx>.05f)flip=false;
        float lean=0,bob=0;
        if(ship){lean=std::clamp(vy*1.8f,-18.f,18.f);m_fields->runT+=dt;bob=std::sin(m_fields->runT*8.f)*1.8f;}
        else if(ground&&std::abs(vx)>.12f){m_fields->runT+=dt*std::clamp(std::abs(vx)*.18f,1.2f,3.f);bob=std::sin(m_fields->runT*11.f)*1.9f;lean=std::clamp(vx*-1.3f,-9.f,9.f);}
        else m_fields->runT+=dt;
        p.y+=bob;
        auto place=[&](CCSprite* s,float y,float rot){if(!s)return;s->setPosition({p.x,p.y+y});s->setFlipX(flip);s->setRotation(rot);};
        place(m_fields->avatar,0,spinning?m_fields->spinA:lean);
        place(m_fields->glow,0,spinning?m_fields->spinA:lean);
        place(m_fields->outer,0,spinning?-m_fields->spinA*.55f:lean*.7f);
        if(m_fields->shadow){m_fields->shadow->setPosition({p.x+(flip?3.f:-3.f),p.y-4});m_fields->shadow->setFlipX(flip);m_fields->shadow->setRotation(spinning?m_fields->spinA:lean);}
        if(m_fields->bubble&&m_fields->bubble->isVisible())m_fields->bubble->setPosition({p.x,p.y+67});
        if(m_fields->bubbleText&&m_fields->bubbleText->isVisible())m_fields->bubbleText->setPosition({p.x,p.y+67});
    }

    void trail(float dt) {
        for(auto* t:m_fields->trails)if(t&&t->isVisible()){
            int n=(int)t->getOpacity()-(int)(dt*620.f);
            if(n<=0){t->setOpacity(0);t->setVisible(false);}else{t->setOpacity((GLubyte)n);t->setScale(t->getScale()*(1.f+dt*.08f));}
        }
        m_fields->trailT+=dt;
        float vx=(float)m_player1->m_platformerXVelocity;
        if(m_fields->transforming||std::abs(vx)<=.10f||m_fields->trailT<.045f)return;
        m_fields->trailT=0;
        auto* t=m_fields->trails[m_fields->trailIndex++%m_fields->trails.size()]; if(!t)return;
        if(auto* tx=tex(m_fields->super,m_fields->spin))t->setTexture(tx);
        t->setPosition(m_fields->avatar->getPosition());t->setFlipX(m_fields->avatar->isFlipX());t->setRotation(m_fields->avatar->getRotation());
        t->setColor(aura(m_fields->super));t->setScale(fit(t,m_fields->spin?.90f:.98f));t->setOpacity(m_fields->super?155:95);t->setVisible(true);
    }

    void speech(bool on) {
        auto* p=m_player1->getParent();if(!p)p=this;
        if(on&&!m_fields->bubble){
            m_fields->bubble=CCSprite::create("square.png");
            if(m_fields->bubble){m_fields->bubble->setColor(ccc3(255,255,255));m_fields->bubble->setOpacity(245);m_fields->bubble->setScaleX(1.35f);m_fields->bubble->setScaleY(.56f);p->addChild(m_fields->bubble,m_player1->getZOrder()+15);}
            m_fields->bubbleText=CCLabelBMFont::create("I guess it's time\nto go super!","bigFont.fnt");
            if(m_fields->bubbleText){m_fields->bubbleText->setColor(ccc3(18,18,25));m_fields->bubbleText->setScale(.36f);p->addChild(m_fields->bubbleText,m_player1->getZOrder()+16);}
        }
        if(m_fields->bubble)m_fields->bubble->setVisible(on);
        if(m_fields->bubbleText)m_fields->bubbleText->setVisible(on);
    }
    void hideGems(){for(auto* g:m_fields->emeralds)if(g){g->setVisible(false);g->setOpacity(0);}}

    void orbit(float t) {
        auto c=pos();float stage=std::clamp((t-.75f)/1.55f,0.f,1.f),r=22+28*stage,base=t*(1.8f+4.5f*stage);
        for(size_t i=0;i<7;i++){auto* g=m_fields->emeralds[i];if(!g)continue;float a=base+i*(2*PI/7.f),w=std::sin(t*5+i)*3;
            g->setPosition({c.x+std::cos(a)*(r+w),c.y+std::sin(a)*(r*.72f+w)});g->setRotation(45-a*20);g->setVisible(t>=.75f);g->setOpacity((GLubyte)std::clamp(stage*255.f,0.f,255.f));g->setScale(.10f+.06f*stage);}
    }

    void ringBurst() {
        auto c=pos();
        for(size_t i=0;i<m_fields->rings.size();i++){auto* r=m_fields->rings[i];if(!r)continue;r->stopAllActions();r->setVisible(true);r->setOpacity(255);r->setScale(.16f);r->setPosition(c);
            float a=i*(2*PI/m_fields->rings.size());CCPoint off={std::cos(a)*56.f,std::sin(a)*38.f};
            r->runAction(CCSequence::create(CCSpawn::create(CCMoveBy::create(.48f,off),CCFadeOut::create(.48f),CCEaseSineOut::create(CCScaleTo::create(.48f,.34f)),nullptr),CCHide::create(),nullptr));}
    }

    void startTransform() {
        if(m_fields->transforming||m_fields->super)return;
        m_fields->transforming=true;m_fields->transT=0;if(m_fields->superBtn)m_fields->superBtn->setEnabled(false);speech(true);hideGems();
    }
    void updateTransform(float dt) {
        if(!m_fields->transforming)return;
        m_fields->transT+=dt;float t=m_fields->transT;m_player1->m_platformerXVelocity=0;
        speech(t<1.f);if(t>=.75f)orbit(t);
        if(t>=2.22f){float q=std::clamp((t-2.22f)/.58f,0.f,1.f),p=std::sin(q*PI);
            if(m_fields->flash)m_fields->flash->setOpacity((GLubyte)(p*215));
            if(m_fields->glow){m_fields->glow->setColor(ccc3(255,225,70));m_fields->glow->setOpacity((GLubyte)(100+p*145));m_fields->glow->setScale(fit(m_fields->glow,1.12f+p*.34f));}
            if(m_fields->outer){m_fields->outer->setColor(ccc3(255,245,125));m_fields->outer->setOpacity((GLubyte)(55+p*140));m_fields->outer->setScale(fit(m_fields->outer,1.30f+p*.52f));}
        }
        if(t>=TRANSFORM_TIME){m_fields->transforming=false;speech(false);hideGems();if(m_fields->flash)m_fields->flash->setOpacity(0);setSuper(true,false);ringBurst();if(m_fields->superBtn)m_fields->superBtn->setEnabled(true);}
    }

    void onSuper(CCObject*) {
        if(m_fields->transforming)return;
        if(m_fields->super)setSuper(false,true);else startTransform();
    }

    void setSuper(bool on,bool anim) {
        if(on&&!m_fields->super){m_fields->wasShip=m_player1->m_isShip;m_fields->forcedShip=!m_fields->wasShip;if(m_fields->forcedShip)m_player1->toggleFlyMode(true,false);}
        else if(!on&&m_fields->super&&m_fields->forcedShip){m_player1->toggleFlyMode(false,false);m_fields->forcedShip=false;}
        m_fields->super=on;refresh(false);
        auto c=aura(on);
        if(m_fields->glow){m_fields->glow->setColor(c);m_fields->glow->setOpacity(on?145:75);m_fields->glow->setScale(fit(m_fields->glow,on?1.22f:1.13f));}
        if(m_fields->outer){m_fields->outer->setColor(on?ccc3(255,245,135):c);m_fields->outer->setOpacity(on?85:35);m_fields->outer->setScale(fit(m_fields->outer,on?1.45f:1.30f));}
        if(anim){ringBurst();if(m_fields->avatar){m_fields->avatar->stopAllActions();m_fields->avatar->runAction(CCSequence::create(CCEaseBackOut::create(CCScaleTo::create(.12f,fit(m_fields->avatar,1.38f))),CCEaseSineInOut::create(CCScaleTo::create(.18f,fit(m_fields->avatar))),nullptr));}}
    }

    void destroyPlayer(PlayerObject* p, GameObject* o) {
        if(m_fields->super&&p==m_player1){if(m_fields->hitFx<=0){m_fields->hitFx=.18f;ringBurst();if(m_fields->flash){m_fields->flash->stopAllActions();m_fields->flash->setOpacity(80);m_fields->flash->runAction(CCFadeTo::create(.12f,0));}}return;}
        PlayLayer::destroyPlayer(p,o);
    }

    void postUpdate(float dt) {
        PlayLayer::postUpdate(dt);if(!m_player1||!m_fields->avatar)return;hideNative();
        if(m_fields->hitFx>0)m_fields->hitFx-=dt;
        updateTransform(dt);sync(dt);trail(dt);
        float vx=(float)m_player1->m_platformerXVelocity;
        if(m_fields->super&&!m_fields->transforming&&std::abs(vx)>.05f&&std::abs(vx)<SUPER_MAX){float s=vx>0?1.f:-1.f;m_player1->m_platformerXVelocity=std::clamp(vx+s*SUPER_ACCEL*dt,-SUPER_MAX,SUPER_MAX);}
    }

    void resetLevel() {
        PlayLayer::resetLevel();m_fields->runT=m_fields->trailT=m_fields->transT=m_fields->spinA=m_fields->hitFx=0;m_fields->transforming=false;
        for(auto* t:m_fields->trails)if(t)t->setVisible(false);speech(false);hideGems();if(m_fields->flash)m_fields->flash->setOpacity(0);if(m_fields->superBtn)m_fields->superBtn->setEnabled(true);
        if(m_fields->super)setSuper(false,false);else refresh(false);hideNative();sync(0);
    }
};
