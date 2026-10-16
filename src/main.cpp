#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <Geode/binding/CCMenuItemSpriteExtra.hpp>
#include <Geode/binding/ButtonSprite.hpp>

#include <algorithm>
#include <array>
#include <cmath>
#include <string>

using namespace geode::prelude;

namespace {
constexpr float kAvatarScale = 1.55f;
constexpr float kAvatarYOffset = 10.0f;
constexpr float kSuperSpeedCap = 10.5f;
constexpr float kExtraSuperAccel = 16.0f;
constexpr float kTrailInterval = 0.055f;

std::string frameName(int frame, bool superForm) {
    frame = std::clamp(frame, 0, 9);
    if (superForm) {
        switch (frame) {
            case 0: return "super_idle.png"_spr;
            case 1: return "super_run1.png"_spr;
            case 2: return "super_run2.png"_spr;
            case 3: return "super_run3.png"_spr;
            case 4: return "super_run4.png"_spr;
            case 5: return "super_jump_up.png"_spr;
            case 6: return "super_jump_down.png"_spr;
            case 7: return "super_fall.png"_spr;
            case 8: return "super_land.png"_spr;
            default: return "super_death.png"_spr;
        }
    }
    switch (frame) {
        case 0: return "sonic_idle.png"_spr;
        case 1: return "sonic_run1.png"_spr;
        case 2: return "sonic_run2.png"_spr;
        case 3: return "sonic_run3.png"_spr;
        case 4: return "sonic_run4.png"_spr;
        case 5: return "sonic_jump_up.png"_spr;
        case 6: return "sonic_jump_down.png"_spr;
        case 7: return "sonic_fall.png"_spr;
        case 8: return "sonic_land.png"_spr;
        default: return "sonic_death.png"_spr;
    }
}

CCTexture2D* frameTexture(int frame, bool superForm) {
    auto name = frameName(frame, superForm);
    return CCTextureCache::sharedTextureCache()->addImage(name.c_str(), false);
}

void applyFrame(CCSprite* sprite, int frame, bool superForm) {
    if (!sprite) return;
    if (auto tex = frameTexture(frame, superForm)) {
        sprite->setTexture(tex);
    }
}

ccColor3B auraColor(bool superForm) {
    return superForm ? ccc3(255, 215, 45) : ccc3(80, 175, 255);
}
}

class $modify(SonicPlayLayer, PlayLayer) {
    struct Fields {
        CCSprite* avatar = nullptr;
        CCSprite* glow = nullptr;
        CCMenu* uiMenu = nullptr;
        CCMenuItemSpriteExtra* superButton = nullptr;
        std::array<CCSprite*, 6> trails{};
        bool superForm = false;
        bool wasOnGround = true;
        int frame = 0;
        int trailCursor = 0;
        float runClock = 0.f;
        float trailClock = 0.f;
        float landTimer = 0.f;
    };

    bool init(GJGameLevel* level, bool useReplay, bool dontCreateObjects) {
        if (!PlayLayer::init(level, useReplay, dontCreateObjects)) return false;
        if (!m_player1) return true;
        createAvatar();
        createSuperButton();
        setSuper(false, false);
        hideNativePlayer();
        return true;
    }

    void createAvatar() {
        auto parent = m_player1->getParent();
        if (!parent) parent = this;
        auto idlePath = frameName(0, false);

        m_fields->glow = CCSprite::create(idlePath.c_str());
        if (m_fields->glow) {
            m_fields->glow->setScale(kAvatarScale * 1.11f);
            m_fields->glow->setColor(auraColor(false));
            m_fields->glow->setOpacity(55);
            parent->addChild(m_fields->glow, m_player1->getZOrder() + 2);
        }

        m_fields->avatar = CCSprite::create(idlePath.c_str());
        if (m_fields->avatar) {
            m_fields->avatar->setScale(kAvatarScale);
            parent->addChild(m_fields->avatar, m_player1->getZOrder() + 3);
        }

        for (auto& trail : m_fields->trails) {
            trail = CCSprite::create(idlePath.c_str());
            if (!trail) continue;
            trail->setVisible(false);
            trail->setScale(kAvatarScale * 0.94f);
            parent->addChild(trail, m_player1->getZOrder() + 1);
        }
        syncAvatarPosition();
    }

    void createSuperButton() {
        auto size = CCDirector::sharedDirector()->getWinSize();
        auto buttonSprite = ButtonSprite::create(
            "SUPER",
            "goldFont.fnt",
            "GJ_button_01.png",
            .82f
        );
        if (!buttonSprite) return;
        buttonSprite->setScale(.72f);

        auto menu = CCMenu::create();
        menu->setPosition({0.f, 0.f});
        this->addChild(menu, 10000);
        m_fields->uiMenu = menu;

        auto button = CCMenuItemSpriteExtra::create(
            buttonSprite,
            this,
            menu_selector(SonicPlayLayer::onToggleSuper)
        );
        button->setPosition({size.width - 66.f, 63.f});
        menu->addChild(button);
        m_fields->superButton = button;
    }

    void hideNativePlayer() {
        if (!m_player1) return;
        m_player1->setVisible(false);
        m_player1->setOpacity(0);
        m_player1->m_isHidden = true;
        if (m_player1->m_iconSprite) m_player1->m_iconSprite->setVisible(false);
        if (m_player1->m_iconSpriteSecondary) m_player1->m_iconSpriteSecondary->setVisible(false);
        if (m_player1->m_iconSpriteWhitener) m_player1->m_iconSpriteWhitener->setVisible(false);
    }

    void syncAvatarPosition() {
        if (!m_player1) return;
        auto p = m_player1->getPosition();
        p.y += kAvatarYOffset;
        if (m_fields->avatar) m_fields->avatar->setPosition(p);
        if (m_fields->glow) m_fields->glow->setPosition(p);
    }

    void updateFrame(int frame) {
        if (frame == m_fields->frame) return;
        m_fields->frame = frame;
        applyFrame(m_fields->avatar, frame, m_fields->superForm);
        applyFrame(m_fields->glow, frame, m_fields->superForm);
    }

    int chooseFrame(float dt) {
        if (!m_player1) return 0;
        float vx = static_cast<float>(m_player1->m_platformerXVelocity);
        float vy = static_cast<float>(m_player1->m_yVelocity);
        bool onGround = m_player1->m_isOnGround;

        if (!onGround) {
            m_fields->wasOnGround = false;
            m_fields->landTimer = 0.f;
            if (vy > 0.75f) return 5;
            if (vy < -3.0f) return 7;
            return 6;
        }

        if (!m_fields->wasOnGround) {
            m_fields->wasOnGround = true;
            m_fields->landTimer = .12f;
        }

        if (m_fields->landTimer > 0.f) {
            m_fields->landTimer -= dt;
            return 8;
        }

        if (std::abs(vx) > .08f) {
            float speedFactor = std::clamp(std::abs(vx) * .16f, .85f, 2.4f);
            m_fields->runClock += dt * speedFactor;
            return 1 + (static_cast<int>(m_fields->runClock / .075f) % 4);
        }

        m_fields->runClock = 0.f;
        return 0;
    }

    void updateFacing() {
        if (!m_player1) return;
        float vx = static_cast<float>(m_player1->m_platformerXVelocity);
        bool flip = m_fields->avatar ? m_fields->avatar->isFlipX() : false;
        if (vx < -.05f) flip = true;
        else if (vx > .05f) flip = false;
        if (m_fields->avatar) m_fields->avatar->setFlipX(flip);
        if (m_fields->glow) m_fields->glow->setFlipX(flip);
    }

    void fadeTrails(float dt) {
        for (auto* trail : m_fields->trails) {
            if (!trail || !trail->isVisible()) continue;
            int next = static_cast<int>(trail->getOpacity()) - static_cast<int>(dt * 560.f);
            if (next <= 0) {
                trail->setOpacity(0);
                trail->setVisible(false);
            } else {
                trail->setOpacity(static_cast<GLubyte>(next));
            }
        }
    }

    void spawnTrail() {
        if (!m_fields->avatar) return;
        auto* trail = m_fields->trails[m_fields->trailCursor];
        m_fields->trailCursor = (m_fields->trailCursor + 1) % static_cast<int>(m_fields->trails.size());
        if (!trail) return;
        applyFrame(trail, m_fields->frame, m_fields->superForm);
        trail->setPosition(m_fields->avatar->getPosition());
        trail->setFlipX(m_fields->avatar->isFlipX());
        trail->setColor(auraColor(m_fields->superForm));
        trail->setScale(kAvatarScale * (m_fields->superForm ? 1.00f : .94f));
        trail->setOpacity(m_fields->superForm ? 145 : 80);
        trail->setVisible(true);
    }

    void onToggleSuper(CCObject*) {
        setSuper(!m_fields->superForm, true);
    }

    void setSuper(bool enabled, bool animate) {
        m_fields->superForm = enabled;
        applyFrame(m_fields->avatar, m_fields->frame, enabled);
        applyFrame(m_fields->glow, m_fields->frame, enabled);

        if (m_fields->glow) {
            m_fields->glow->setColor(auraColor(enabled));
            m_fields->glow->setOpacity(enabled ? 135 : 55);
            m_fields->glow->setScale(kAvatarScale * (enabled ? 1.18f : 1.11f));
        }

        if (animate && m_fields->avatar) {
            m_fields->avatar->stopAllActions();
            m_fields->avatar->runAction(CCSequence::create(
                CCEaseBackOut::create(CCScaleTo::create(.13f, kAvatarScale * 1.55f)),
                CCEaseSineInOut::create(CCScaleTo::create(.18f, kAvatarScale)),
                nullptr
            ));
            if (m_fields->glow) {
                m_fields->glow->stopAllActions();
                m_fields->glow->setOpacity(220);
                m_fields->glow->runAction(CCSequence::create(
                    CCEaseBackOut::create(CCScaleTo::create(.15f, kAvatarScale * 1.65f)),
                    CCEaseSineInOut::create(CCScaleTo::create(.20f, kAvatarScale * (enabled ? 1.18f : 1.11f))),
                    nullptr
                ));
            }
        }
    }

    void postUpdate(float dt) {
        PlayLayer::postUpdate(dt);
        if (!m_player1 || !m_fields->avatar) return;
        hideNativePlayer();
        syncAvatarPosition();
        updateFacing();
        updateFrame(chooseFrame(dt));

        float vx = static_cast<float>(m_player1->m_platformerXVelocity);
        fadeTrails(dt);
        m_fields->trailClock += dt;
        if (std::abs(vx) > .15f && m_fields->trailClock >= kTrailInterval) {
            m_fields->trailClock = 0.f;
            spawnTrail();
        }

        if (m_fields->superForm && std::abs(vx) > .08f && std::abs(vx) < kSuperSpeedCap) {
            float sign = vx > 0.f ? 1.f : -1.f;
            float boosted = vx + sign * kExtraSuperAccel * dt;
            m_player1->m_platformerXVelocity = std::clamp(boosted, -kSuperSpeedCap, kSuperSpeedCap);
        }
    }

    void resetLevel() {
        PlayLayer::resetLevel();
        m_fields->runClock = 0.f;
        m_fields->trailClock = 0.f;
        m_fields->landTimer = 0.f;
        m_fields->wasOnGround = true;
        m_fields->frame = 0;
        for (auto* trail : m_fields->trails) {
            if (trail) trail->setVisible(false);
        }
        setSuper(false, false);
        hideNativePlayer();
        syncAvatarPosition();
    }
};
