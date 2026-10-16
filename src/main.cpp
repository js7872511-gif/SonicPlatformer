#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <Geode/binding/CCMenuItemSpriteExtra.hpp>

#include <algorithm>
#include <cmath>

using namespace geode::prelude;

namespace {
constexpr float kSonicScale = 0.48f;
constexpr float kSuperSpeedCap = 8.5f;
constexpr float kExtraSuperAccel = 11.0f;
}

class $modify(SonicPlayLayer, PlayLayer) {
    struct Fields {
        CCSprite* avatar = nullptr;
        CCMenu* uiMenu = nullptr;
        CCMenuItemSpriteExtra* superButton = nullptr;
        CCLabelBMFont* superLabel = nullptr;
        bool superForm = false;
    };

    bool init(GJGameLevel* level, bool useReplay, bool dontCreateObjects) {
        if (!PlayLayer::init(level, useReplay, dontCreateObjects)) return false;
        if (!m_player1) return true;

        createAvatar();
        createSuperButton();
        setSuper(false, false);
        return true;
    }

    void createAvatar() {
        auto parent = m_player1->getParent();
        if (!parent) parent = this;

        m_fields->avatar = CCSprite::create("sonic_idle.png"_spr);
        if (m_fields->avatar) {
            m_fields->avatar->setScale(kSonicScale);
            m_fields->avatar->setPosition(m_player1->getPosition());
            parent->addChild(m_fields->avatar, m_player1->getZOrder() + 3);
            m_player1->setVisible(false);
        }
    }

    void createSuperButton() {
        auto size = CCDirector::sharedDirector()->getWinSize();
        auto bg = CCSprite::createWithSpriteFrameName("GJ_button_05.png");
        if (!bg) return;
        bg->setScale(0.82f);

        auto label = CCLabelBMFont::create("SUPER", "bigFont.fnt");
        label->setScale(0.32f);
        label->setPosition({bg->getContentSize().width / 2.f, bg->getContentSize().height / 2.f});
        bg->addChild(label);
        m_fields->superLabel = label;

        auto menu = CCMenu::create();
        menu->setPosition({0.f, 0.f});
        this->addChild(menu, 10000);
        m_fields->uiMenu = menu;

        auto button = CCMenuItemSpriteExtra::create(
            bg,
            this,
            menu_selector(SonicPlayLayer::onToggleSuper)
        );
        button->setPosition({size.width - 58.f, 62.f});
        menu->addChild(button);
        m_fields->superButton = button;
    }

    void onToggleSuper(CCObject*) {
        setSuper(!m_fields->superForm, true);
    }

    void setSuper(bool enabled, bool animate) {
        m_fields->superForm = enabled;
        if (m_fields->superLabel) {
            m_fields->superLabel->setString(enabled ? "NORMAL" : "SUPER");
        }

        if (!m_fields->avatar || !m_player1) return;

        auto pos = m_fields->avatar->getPosition();
        bool flip = m_fields->avatar->isFlipX();
        auto parent = m_fields->avatar->getParent();
        int z = m_fields->avatar->getZOrder();

        m_fields->avatar->removeFromParentAndCleanup(true);
        m_fields->avatar = CCSprite::create(enabled ? "super_idle.png"_spr : "sonic_idle.png"_spr);
        if (!m_fields->avatar) return;

        m_fields->avatar->setPosition(pos);
        m_fields->avatar->setFlipX(flip);
        m_fields->avatar->setScale(kSonicScale);
        parent->addChild(m_fields->avatar, z);

        if (animate) {
            m_fields->avatar->runAction(CCSequence::create(
                CCEaseBackOut::create(CCScaleTo::create(0.12f, kSonicScale * 1.40f)),
                CCEaseSineInOut::create(CCScaleTo::create(0.14f, kSonicScale)),
                nullptr
            ));
        }
    }

    void postUpdate(float dt) {
        PlayLayer::postUpdate(dt);
        if (!m_player1 || !m_fields->avatar) return;

        auto pos = m_player1->getPosition();
        float vx = static_cast<float>(m_player1->m_platformerXVelocity);

        m_fields->avatar->setPosition(pos);
        if (vx < -0.05f) m_fields->avatar->setFlipX(true);
        else if (vx > 0.05f) m_fields->avatar->setFlipX(false);

        if (m_fields->superForm && std::abs(vx) > 0.08f && std::abs(vx) < kSuperSpeedCap) {
            float sign = vx > 0.f ? 1.f : -1.f;
            float boosted = vx + sign * kExtraSuperAccel * dt;
            m_player1->m_platformerXVelocity = std::clamp(boosted, -kSuperSpeedCap, kSuperSpeedCap);
        }
    }

    void resetLevel() {
        PlayLayer::resetLevel();
        if (m_player1) m_player1->setVisible(false);
        setSuper(false, false);
    }
};
