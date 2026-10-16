#include <Geode/Geode.hpp>
#include <Geode/modify/PlayLayer.hpp>
#include <Geode/binding/CCMenuItemSpriteExtra.hpp>

#include <algorithm>
#include <cmath>
#include <string>

using namespace geode::prelude;

namespace {
constexpr float kAvatarScale = 0.46f;
constexpr float kRunFrameTime = 0.075f;
constexpr float kSuperSpeedCap = 8.5f;
constexpr float kExtraSuperAccel = 11.0f;

std::string frameName(bool superForm, std::string const& state) {
    return (superForm ? "super_" : "sonic_") + state + ".png";
}
}

class $modify(SonicPlayLayer, PlayLayer) {
    struct Fields {
        CCSprite* avatar = nullptr;
        CCSprite* aura = nullptr;
        CCMenu* uiMenu = nullptr;
        CCMenuItemSpriteExtra* superButton = nullptr;
        CCLabelBMFont* superLabel = nullptr;

        bool superForm = false;
        bool wasAirborne = false;
        float previousY = 0.f;
        float animationTimer = 0.f;
        float landTimer = 0.f;
        int runFrame = 0;
        std::string shownFrame;
    };

    bool init(GJGameLevel* level, bool useReplay, bool dontCreateObjects) {
        if (!PlayLayer::init(level, useReplay, dontCreateObjects)) return false;
        if (!m_player1) return true;

        m_fields->previousY = m_player1->getPositionY();
        createAvatar();
        createSuperButton();
        applyForm(false, false);
        return true;
    }

    void createAvatar() {
        auto parent = m_player1->getParent();
        if (!parent) parent = this;

        m_fields->aura = CCSprite::create("super_aura.png"_spr);
        if (m_fields->aura) {
            m_fields->aura->setScale(0.52f);
            m_fields->aura->setOpacity(0);
            m_fields->aura->setBlendFunc({GL_SRC_ALPHA, GL_ONE});
            parent->addChild(m_fields->aura, m_player1->getZOrder() + 1);

            auto pulse = CCRepeatForever::create(CCSequence::create(
                CCEaseSineInOut::create(CCScaleTo::create(0.35f, 0.58f)),
                CCEaseSineInOut::create(CCScaleTo::create(0.35f, 0.50f)),
                nullptr
            ));
            m_fields->aura->runAction(pulse);
        }

        replaceAvatarSprite("idle");

        // Physics stay on the real Geometry Dash PlayerObject. We only hide its art.
        m_player1->setVisible(false);
    }

    void createSuperButton() {
        auto size = CCDirector::sharedDirector()->getWinSize();

        auto buttonBg = CCSprite::createWithSpriteFrameName("GJ_button_05.png");
        if (!buttonBg) return;
        buttonBg->setScale(0.78f);

        if (auto icon = CCSprite::create("super_button.png"_spr)) {
            icon->setPosition({buttonBg->getContentSize().width / 2.f, buttonBg->getContentSize().height / 2.f});
            icon->setScale(0.55f);
            buttonBg->addChild(icon);
        }

        auto menu = CCMenu::create();
        menu->setPosition({0.f, 0.f});
        menu->setZOrder(10000);
        this->addChild(menu);
        m_fields->uiMenu = menu;

        auto button = CCMenuItemSpriteExtra::create(
            buttonBg,
            this,
            menu_selector(SonicPlayLayer::onToggleSuper)
        );
        button->setPosition({size.width - 55.f, 64.f});
        button->setID("super-button"_spr);
        menu->addChild(button);
        m_fields->superButton = button;

        auto label = CCLabelBMFont::create("SUPER", "bigFont.fnt");
        label->setScale(0.30f);
        label->setPosition({buttonBg->getContentSize().width / 2.f, -13.f});
        buttonBg->addChild(label);
        m_fields->superLabel = label;
    }

    void onToggleSuper(CCObject*) {
        applyForm(!m_fields->superForm, true);
    }

    void applyForm(bool superForm, bool animate) {
        m_fields->superForm = superForm;
        m_fields->shownFrame.clear();

        if (m_fields->superLabel) {
            m_fields->superLabel->setString(superForm ? "NORMAL" : "SUPER");
        }
        if (m_fields->aura) {
            m_fields->aura->setOpacity(superForm ? 190 : 0);
        }

        replaceAvatarSprite("idle");

        if (animate && m_fields->avatar) {
            m_fields->avatar->stopAllActions();
            m_fields->avatar->setScale(kAvatarScale);
            m_fields->avatar->runAction(CCSequence::create(
                CCEaseBackOut::create(CCScaleTo::create(0.13f, kAvatarScale * 1.42f)),
                CCEaseSineInOut::create(CCScaleTo::create(0.13f, kAvatarScale)),
                nullptr
            ));

            if (m_fields->aura && superForm) {
                m_fields->aura->stopActionByTag(9921);
                auto flash = CCSequence::create(
                    CCFadeTo::create(0.05f, 255),
                    CCFadeTo::create(0.25f, 190),
                    nullptr
                );
                flash->setTag(9921);
                m_fields->aura->runAction(flash);
            }
        }
    }

    void replaceAvatarSprite(std::string const& state) {
        auto wanted = frameName(m_fields->superForm, state);
        if (wanted == m_fields->shownFrame && m_fields->avatar) return;

        auto parent = m_player1 ? m_player1->getParent() : nullptr;
        if (!parent) parent = this;

        CCPoint pos = m_player1 ? m_player1->getPosition() : CCPointZero;
        bool flip = m_fields->avatar ? m_fields->avatar->isFlipX() : false;

        if (m_fields->avatar) {
            m_fields->avatar->removeFromParentAndCleanup(true);
            m_fields->avatar = nullptr;
        }

        auto resourceName = std::string("chatgpt.sonic-platformer/") + wanted;
        auto avatar = CCSprite::create(resourceName.c_str());
        if (!avatar) return;

        avatar->setScale(kAvatarScale);
        avatar->setPosition(pos);
        avatar->setFlipX(flip);
        parent->addChild(avatar, m_player1 ? m_player1->getZOrder() + 3 : 3);
        m_fields->avatar = avatar;
        m_fields->shownFrame = wanted;
    }

    void postUpdate(float dt) {
        PlayLayer::postUpdate(dt);
        if (!m_player1 || !m_fields->avatar) return;

        auto pos = m_player1->getPosition();
        float dy = pos.y - m_fields->previousY;
        m_fields->previousY = pos.y;
        float vx = static_cast<float>(m_player1->m_platformerXVelocity);

        m_fields->avatar->setPosition(pos);
        if (m_fields->aura) m_fields->aura->setPosition(pos);

        if (vx < -0.05f) m_fields->avatar->setFlipX(true);
        else if (vx > 0.05f) m_fields->avatar->setFlipX(false);

        bool airborne = std::abs(dy) > 0.23f;
        if (m_fields->wasAirborne && !airborne) m_fields->landTimer = 0.10f;
        m_fields->wasAirborne = airborne;
        if (m_fields->landTimer > 0.f) m_fields->landTimer -= dt;

        std::string state = "idle";
        if (m_fields->landTimer > 0.f) {
            state = "land";
        } else if (dy > 0.23f) {
            state = "jump";
        } else if (dy < -0.23f) {
            state = "fall";
        } else if (std::abs(vx) > 0.10f) {
            m_fields->animationTimer += dt;
            if (m_fields->animationTimer >= kRunFrameTime) {
                m_fields->animationTimer = 0.f;
                m_fields->runFrame = (m_fields->runFrame + 1) % 4;
            }
            state = "run" + std::to_string(m_fields->runFrame + 1);
        } else {
            m_fields->animationTimer = 0.f;
            m_fields->runFrame = 0;
        }

        replaceAvatarSprite(state);
        if (m_fields->avatar) {
            m_fields->avatar->setPosition(pos);
            if (vx < -0.05f) m_fields->avatar->setFlipX(true);
            else if (vx > 0.05f) m_fields->avatar->setFlipX(false);
        }

        // Small, capped boost that only touches existing platformer movement.
        // It does not create or replace the left/right controls from QOLMod.
        if (m_fields->superForm && std::abs(vx) > 0.08f && std::abs(vx) < kSuperSpeedCap) {
            float sign = vx > 0.f ? 1.f : -1.f;
            float boosted = vx + sign * kExtraSuperAccel * dt;
            m_player1->m_platformerXVelocity = std::clamp(boosted, -kSuperSpeedCap, kSuperSpeedCap);
        }
    }

    void resetLevel() {
        PlayLayer::resetLevel();
        if (m_player1) {
            m_player1->setVisible(false);
            m_fields->previousY = m_player1->getPositionY();
        }
        m_fields->animationTimer = 0.f;
        m_fields->runFrame = 0;
        m_fields->landTimer = 0.f;
        m_fields->wasAirborne = false;
        applyForm(false, false);
    }
};
