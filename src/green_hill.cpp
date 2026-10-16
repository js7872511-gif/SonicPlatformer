#include <Geode/Geode.hpp>
#include <Geode/modify/MenuLayer.hpp>
#include <Geode/binding/CCMenuItemSpriteExtra.hpp>
#include <Geode/binding/ButtonSprite.hpp>
#include <array>
#include <algorithm>
#include <cmath>
#include <string>

using namespace geode::prelude;

namespace {
constexpr float kFloorY = 102.f;
constexpr float kPlayerX = 158.f;
constexpr float kZoneLength = 2500.f;
constexpr float kGravity = 720.f;
constexpr float kJumpVelocity = 315.f;
constexpr float kRunSpeed = 205.f;

CCLayerColor* rect(float w, float h, ccColor4B c) {
    return CCLayerColor::create(c, w, h);
}

std::string sonicTexture(bool superForm, std::string const& pose) {
    return Mod::get()->expandSpriteName((superForm ? "super_" : "sonic_") + pose + ".png");
}
}

class GreenHillsZoneLayer : public CCLayer {
protected:
    CCNode* m_world = nullptr;
    CCSprite* m_player = nullptr;
    CCSprite* m_aura = nullptr;
    CCLabelBMFont* m_ringLabel = nullptr;
    CCLabelBMFont* m_status = nullptr;
    CCMenuItemSpriteExtra* m_superButton = nullptr;

    float m_cameraX = 0.f;
    float m_speedX = 0.f;
    float m_playerY = kFloorY + 28.f;
    float m_velY = 0.f;
    float m_animTime = 0.f;
    float m_spin = 0.f;
    bool m_grounded = true;
    bool m_super = false;
    bool m_finished = false;
    int m_jumps = 0;
    int m_ringsCollected = 0;

    std::array<CCSprite*, 14> m_rings{};
    std::array<CCPoint, 14> m_ringPos{{
        {330.f, 133.f}, {390.f, 150.f}, {450.f, 133.f},
        {620.f, 133.f}, {680.f, 133.f}, {740.f, 133.f},
        {930.f, 155.f}, {990.f, 178.f}, {1050.f, 155.f},
        {1380.f, 133.f}, {1440.f, 133.f},
        {1760.f, 150.f}, {1840.f, 180.f}, {1920.f, 150.f}
    }};

public:
    static GreenHillsZoneLayer* create() {
        auto* ret = new GreenHillsZoneLayer();
        if (ret && ret->init()) {
            ret->autorelease();
            return ret;
        }
        CC_SAFE_DELETE(ret);
        return nullptr;
    }

    static CCScene* scene() {
        auto* scene = CCScene::create();
        scene->addChild(GreenHillsZoneLayer::create());
        return scene;
    }

    bool init() override {
        if (!CCLayer::init()) return false;

        auto win = CCDirector::sharedDirector()->getWinSize();

        auto* sky = rect(win.width, win.height, ccc4(70, 175, 245, 255));
        sky->setPosition({0.f, 0.f});
        addChild(sky, -50);

        // Distant hills / ocean stripes for a bright Green-Hills-inspired look.
        auto* sea = rect(win.width, 82.f, ccc4(40, 145, 225, 255));
        sea->setPosition({0.f, kFloorY + 28.f});
        addChild(sea, -45);

        for (int i = 0; i < 6; ++i) {
            auto* hill = rect(120.f, 90.f + (i % 2) * 35.f, ccc4(55, 185, 80, 255));
            hill->setPosition({i * 120.f - 30.f, kFloorY + 45.f});
            hill->setRotation(i % 2 ? -20.f : 20.f);
            addChild(hill, -44);
        }

        m_world = CCNode::create();
        addChild(m_world, 0);

        buildGround();
        buildDecorations();
        buildRings();
        buildGoal();
        buildPlayer();
        buildUI();

        scheduleUpdate();
        return true;
    }

    void buildGround() {
        auto* dirt = rect(kZoneLength + 500.f, 110.f, ccc4(170, 98, 38, 255));
        dirt->setPosition({0.f, 0.f});
        m_world->addChild(dirt, -5);

        // Checkerboard dirt blocks.
        for (int x = 0; x < 95; ++x) {
            for (int y = 0; y < 3; ++y) {
                auto color = ((x + y) % 2 == 0)
                    ? ccc4(198, 126, 55, 255)
                    : ccc4(120, 68, 34, 255);
                auto* tile = rect(32.f, 32.f, color);
                tile->setPosition({x * 32.f, y * 32.f});
                m_world->addChild(tile, -4);
            }
        }

        auto* grass = rect(kZoneLength + 500.f, 14.f, ccc4(45, 220, 65, 255));
        grass->setPosition({0.f, kFloorY - 7.f});
        m_world->addChild(grass, -2);

        auto* grassTop = rect(kZoneLength + 500.f, 5.f, ccc4(125, 255, 75, 255));
        grassTop->setPosition({0.f, kFloorY + 7.f});
        m_world->addChild(grassTop, -1);
    }

    void palm(float x) {
        auto* trunk = rect(13.f, 95.f, ccc4(145, 82, 38, 255));
        trunk->setPosition({x, kFloorY + 8.f});
        m_world->addChild(trunk, -1);

        for (int i = 0; i < 5; ++i) {
            auto* leaf = rect(56.f, 12.f, ccc4(45, 205, 75, 255));
            leaf->setAnchorPoint({0.f, .5f});
            leaf->setPosition({x + 6.f, kFloorY + 98.f});
            leaf->setRotation(-70.f + i * 35.f);
            m_world->addChild(leaf, -1);
        }
    }

    void flower(float x, ccColor4B color) {
        auto* stem = rect(4.f, 27.f, ccc4(35, 180, 55, 255));
        stem->setPosition({x, kFloorY + 8.f});
        m_world->addChild(stem, -1);
        auto* bloom = rect(16.f, 16.f, color);
        bloom->setPosition({x - 6.f, kFloorY + 31.f});
        m_world->addChild(bloom, -1);
    }

    void buildDecorations() {
        palm(515.f);
        palm(1260.f);
        palm(2140.f);
        flower(270.f, ccc4(255, 230, 40, 255));
        flower(790.f, ccc4(255, 80, 170, 255));
        flower(1560.f, ccc4(255, 230, 40, 255));
        flower(2030.f, ccc4(255, 80, 170, 255));

        // Small checker platforms for the zone look.
        for (float x : {860.f, 1160.f, 1650.f, 1980.f}) {
            for (int i = 0; i < 4; ++i) {
                auto* b = rect(32.f, 24.f, (i % 2) ? ccc4(118, 67, 35, 255) : ccc4(196, 126, 55, 255));
                b->setPosition({x + i * 32.f, kFloorY + 70.f});
                m_world->addChild(b, -1);
            }
            auto* top = rect(128.f, 8.f, ccc4(60, 220, 65, 255));
            top->setPosition({x, kFloorY + 94.f});
            m_world->addChild(top, 0);
        }
    }

    void buildRings() {
        for (size_t i = 0; i < m_rings.size(); ++i) {
            auto* r = CCSprite::create("ring.png"_spr);
            if (!r) continue;
            r->setPosition(m_ringPos[i]);
            r->setScale(.23f);
            m_world->addChild(r, 3);
            m_rings[i] = r;
        }
    }

    void buildGoal() {
        auto* pole = rect(9.f, 120.f, ccc4(220, 220, 220, 255));
        pole->setPosition({kZoneLength, kFloorY + 8.f});
        m_world->addChild(pole, 1);

        auto* sign = rect(110.f, 58.f, ccc4(25, 90, 220, 255));
        sign->setPosition({kZoneLength - 50.f, kFloorY + 100.f});
        m_world->addChild(sign, 2);

        auto* label = CCLabelBMFont::create("GOAL", "goldFont.fnt");
        label->setScale(.55f);
        label->setPosition({kZoneLength + 5.f, kFloorY + 129.f});
        m_world->addChild(label, 3);
    }

    float playerScale(CCSprite* s) {
        if (!s || s->getContentSize().height < 1.f) return 1.f;
        return 64.f / s->getContentSize().height;
    }

    void buildPlayer() {
        m_aura = CCSprite::create("sonic_idle.png"_spr);
        if (m_aura) {
            m_aura->setColor(ccc3(80, 180, 255));
            m_aura->setOpacity(55);
            m_aura->setScale(playerScale(m_aura) * 1.18f);
            m_aura->setPosition({kPlayerX, m_playerY});
            addChild(m_aura, 9);
        }

        m_player = CCSprite::create("sonic_idle.png"_spr);
        if (m_player) {
            m_player->setScale(playerScale(m_player));
            m_player->setPosition({kPlayerX, m_playerY});
            addChild(m_player, 10);
        }
    }

    CCMenuItemSpriteExtra* addButton(CCMenu* menu, char const* text, CCPoint p, SEL_MenuHandler cb, float scale = .62f) {
        auto* sprite = ButtonSprite::create(text, "bigFont.fnt", "GJ_button_01.png", .72f);
        sprite->setScale(scale);
        auto* item = CCMenuItemSpriteExtra::create(sprite, this, cb);
        item->setPosition(p);
        menu->addChild(item);
        return item;
    }

    void buildUI() {
        auto win = CCDirector::sharedDirector()->getWinSize();

        auto* title = CCLabelBMFont::create("GREEN HILLS ZONE", "goldFont.fnt");
        title->setScale(.7f);
        title->setPosition({win.width / 2.f, win.height - 28.f});
        addChild(title, 50);

        auto* act = CCLabelBMFont::create("BONUS ACT 1", "bigFont.fnt");
        act->setScale(.33f);
        act->setPosition({win.width / 2.f, win.height - 50.f});
        addChild(act, 50);

        m_ringLabel = CCLabelBMFont::create("RINGS 0", "goldFont.fnt");
        m_ringLabel->setScale(.42f);
        m_ringLabel->setAnchorPoint({0.f, .5f});
        m_ringLabel->setPosition({18.f, win.height - 26.f});
        addChild(m_ringLabel, 50);

        m_status = CCLabelBMFont::create("", "bigFont.fnt");
        m_status->setScale(.58f);
        m_status->setPosition({win.width / 2.f, win.height / 2.f + 70.f});
        addChild(m_status, 60);

        auto* menu = CCMenu::create();
        menu->setPosition({0.f, 0.f});
        addChild(menu, 100);

        addButton(menu, "<", {55.f, 48.f}, menu_selector(GreenHillsZoneLayer::onLeft), .72f);
        addButton(menu, ">", {125.f, 48.f}, menu_selector(GreenHillsZoneLayer::onRight), .72f);
        addButton(menu, "JUMP", {win.width - 175.f, 48.f}, menu_selector(GreenHillsZoneLayer::onJump), .58f);
        m_superButton = addButton(menu, "SUPER", {win.width - 72.f, 48.f}, menu_selector(GreenHillsZoneLayer::onSuper), .58f);
        addButton(menu, "X", {win.width - 35.f, win.height - 28.f}, menu_selector(GreenHillsZoneLayer::onBack), .45f);
    }

    void setTexture(std::string const& pose) {
        if (!m_player) return;
        auto name = sonicTexture(m_super, pose);
        auto* tex = CCTextureCache::sharedTextureCache()->addImage(name.c_str(), false);
        if (!tex) return;
        tex->setAntiAliasTexParameters();
        m_player->setTexture(tex);
        m_player->setScale(playerScale(m_player));
        if (m_aura) {
            m_aura->setTexture(tex);
            m_aura->setScale(playerScale(m_aura) * (m_super ? 1.28f : 1.18f));
        }
    }

    void onLeft(CCObject*) {
        if (m_finished) return;
        m_speedX = -kRunSpeed;
        if (m_player) m_player->setFlipX(true);
        if (m_aura) m_aura->setFlipX(true);
    }

    void onRight(CCObject*) {
        if (m_finished) return;
        m_speedX = kRunSpeed;
        if (m_player) m_player->setFlipX(false);
        if (m_aura) m_aura->setFlipX(false);
    }

    void onJump(CCObject*) {
        if (m_finished) return;
        if (m_grounded || m_jumps < 2) {
            ++m_jumps;
            m_grounded = false;
            m_velY = kJumpVelocity * (m_super ? 1.12f : 1.f);
            setTexture("spin");
        }
    }

    void onSuper(CCObject*) {
        if (m_finished) return;
        m_super = !m_super;
        setTexture(m_grounded ? "idle" : "spin");
        if (m_aura) {
            m_aura->setColor(m_super ? ccc3(255, 220, 60) : ccc3(80, 180, 255));
            m_aura->setOpacity(m_super ? 110 : 55);
        }
    }

    void onBack(CCObject*) {
        CCDirector::sharedDirector()->popScene();
    }

    void update(float dt) override {
        if (m_finished) return;

        m_cameraX += m_speedX * dt;
        m_cameraX = std::clamp(m_cameraX, 0.f, kZoneLength - 120.f);
        m_speedX *= std::pow(.09f, dt);
        if (std::abs(m_speedX) < 7.f) m_speedX = 0.f;
        if (m_world) m_world->setPositionX(-m_cameraX);

        if (!m_grounded) {
            m_velY -= kGravity * dt;
            m_playerY += m_velY * dt;
            if (m_playerY <= kFloorY + 28.f) {
                m_playerY = kFloorY + 28.f;
                m_velY = 0.f;
                m_grounded = true;
                m_jumps = 0;
                setTexture(std::abs(m_speedX) > 12.f ? "run1" : "idle");
            }
        }

        m_animTime += dt;
        if (m_grounded) {
            m_spin = 0.f;
            if (std::abs(m_speedX) > 12.f) {
                int frame = 1 + (static_cast<int>(m_animTime * 11.f) % 4);
                setTexture("run" + std::to_string(frame));
            } else {
                setTexture("idle");
            }
        } else {
            m_spin += dt * (m_super ? 960.f : 820.f);
            if (m_player) m_player->setRotation(m_spin);
            if (m_aura) m_aura->setRotation(-m_spin * .55f);
        }

        if (m_grounded) {
            if (m_player) m_player->setRotation(0.f);
            if (m_aura) m_aura->setRotation(0.f);
        }

        if (m_player) m_player->setPosition({kPlayerX, m_playerY});
        if (m_aura) m_aura->setPosition({kPlayerX, m_playerY});

        float worldPlayerX = m_cameraX + kPlayerX;
        for (size_t i = 0; i < m_rings.size(); ++i) {
            auto* r = m_rings[i];
            if (!r || !r->isVisible()) continue;
            auto rp = m_ringPos[i];
            if (std::abs(rp.x - worldPlayerX) < 27.f && std::abs(rp.y - m_playerY) < 40.f) {
                r->setVisible(false);
                ++m_ringsCollected;
                if (m_ringLabel) {
                    m_ringLabel->setString(("RINGS " + std::to_string(m_ringsCollected)).c_str());
                }
            }
        }

        if (worldPlayerX >= kZoneLength - 30.f) {
            m_finished = true;
            if (m_status) {
                m_status->setString("ZONE CLEAR!");
                m_status->setScale(.75f);
            }
        }
    }
};

class $modify(SonicBonusMenuLayer, MenuLayer) {
    bool init() {
        if (!MenuLayer::init()) return false;

        auto win = CCDirector::sharedDirector()->getWinSize();
        auto* menu = CCMenu::create();
        menu->setPosition({0.f, 0.f});
        addChild(menu, 50);

        auto* sprite = ButtonSprite::create("GREEN HILLS", "goldFont.fnt", "GJ_button_01.png", .72f);
        sprite->setScale(.52f);
        auto* button = CCMenuItemSpriteExtra::create(
            sprite,
            this,
            menu_selector(SonicBonusMenuLayer::onGreenHills)
        );
        button->setPosition({win.width - 92.f, win.height - 82.f});
        menu->addChild(button);
        return true;
    }

    void onGreenHills(CCObject*) {
        auto* scene = GreenHillsZoneLayer::scene();
        CCDirector::sharedDirector()->pushScene(CCTransitionFade::create(.35f, scene));
    }
};
