#!/usr/bin/env bash
set -euo pipefail

export PYTHONPATH="$PWD/chaos-edition${PYTHONPATH:+:$PYTHONPATH}"
python3 -m unittest discover -s chaos-edition/tests -v

sudo apt-get update
sudo apt-get install -y g++ cmake xvfb libgl1-mesa-dev libglu1-mesa-dev libasound2-dev libpulse-dev libxcomposite-dev libxxf86vm-dev libcurl4-openssl-dev

yes | sdkmanager --licenses >/dev/null || true
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'cmake;3.22.1' 'ndk;29.0.14206865'

git clone --depth 1 --branch v26.03.28.0-stable https://github.com/Eukaryot/sonic3air.git sonic3air-upstream
curl -L --fail --retry 3 -o official-sonic3air.apk https://github.com/Eukaryot/sonic3air/releases/download/v26.03.28.0-stable/sonic3air_game.apk
echo '915fbf525efadfff06ed27923f8aead490ff29723b87445fbf172e1a4b476838  official-sonic3air.apk' | sha256sum -c -

rm -rf extracted-apk
mkdir extracted-apk
unzip -q official-sonic3air.apk 'assets/*' -d extracted-apk
mkdir -p sonic3air-upstream/Oxygen/sonic3air/build/_android/app/src/main/assets
cp -a extracted-apk/assets/. sonic3air-upstream/Oxygen/sonic3air/build/_android/app/src/main/assets/
mkdir -p sonic3air-upstream/Oxygen/sonic3air/data
cp -a extracted-apk/assets/data/. sonic3air-upstream/Oxygen/sonic3air/data/

# Chaos Edition 3.0 patch order. Do not add the old Water/Emerald patchers here.
python3 chaos-edition/patch_save_banks.py sonic3air-upstream
python3 chaos-edition/patch_savebank_v30_cleanup.py sonic3air-upstream
python3 chaos-edition/patch_death_berserker.py sonic3air-upstream
python3 chaos-edition/patch_chaos_hud.py sonic3air-upstream
python3 chaos-edition/patch_universal_buttons.py sonic3air-upstream
python3 chaos-edition/patch_ci_rom_bypass.py sonic3air-upstream
python3 chaos-edition/patch_android_identity.py sonic3air-upstream

# Source assertions: prove the intended platform is present and rejected old core isn't applied.
grep -n 'global u8 chaos.save_bank' sonic3air-upstream/Oxygen/sonic3air/scripts/general/saveslots.lemon
grep -n 'ChaosProgression.updateSonic' sonic3air-upstream/Oxygen/sonic3air/scripts/maingame/character/character.lemon
grep -n 'chaosLives = 3' sonic3air-upstream/Oxygen/sonic3air/scripts/maingame/hud/hud.lemon
grep -n 'chaos-buttons.json' sonic3air-upstream/Oxygen/oxygenengine/source/oxygen/application/overlays/TouchControlsOverlay.cpp
grep -n 'accessController(3)' sonic3air-upstream/Oxygen/oxygenengine/source/oxygen/application/overlays/TouchControlsOverlay.cpp
grep -n 'com.chaosed.sonic3air.v30' sonic3air-upstream/Oxygen/sonic3air/build/_android/app/build.gradle
if grep -R -n -E 'WATER SONIC|HYPER WATER|Emerald power|comfortable layout v0.2' sonic3air-upstream/Oxygen/sonic3air/scripts sonic3air-upstream/Oxygen/oxygenengine/source/oxygen/application/overlays/TouchControlsOverlay.cpp; then
  echo 'Rejected legacy Chaos ability code leaked into 3.0 build' >&2
  exit 1
fi

# Build the Linux tool used to compile patched LemonScript.
pushd sonic3air-upstream/Oxygen/sonic3air
mkdir -p build/_cmake/build
pushd build/_cmake/build
cmake -DCMAKE_BUILD_TYPE=Release ..
cmake --build . -j 4
popd

test -x ./sonic3air_linux
rm -rf saves
mkdir -p saves
STOCK_SHA=$(sha256sum ../../../extracted-apk/assets/data/scripts.bin | awk '{print $1}')
rm -f data/scripts.bin
set +e
timeout 45s xvfb-run -a ./sonic3air_linux -dumpcppdefinitions -nativize 2>&1 | tee ../../../lemon-compiler-v30.log
STATUS=${PIPESTATUS[0]}
set -e
echo "compiler exit: $STATUS" | tee -a ../../../lemon-compiler-v30.log
if [ ! -s saves/scripts.bin ]; then
  echo 'patched scripts.bin was not generated' | tee -a ../../../lemon-compiler-v30.log
  exit 20
fi
PATCHED_SHA=$(sha256sum saves/scripts.bin | awk '{print $1}')
echo "stock:   $STOCK_SHA" | tee -a ../../../lemon-compiler-v30.log
echo "patched: $PATCHED_SHA" | tee -a ../../../lemon-compiler-v30.log
if [ "$STOCK_SHA" = "$PATCHED_SHA" ]; then
  echo 'patched scripts.bin unexpectedly matches stock' | tee -a ../../../lemon-compiler-v30.log
  exit 21
fi
cp saves/scripts.bin build/_android/app/src/main/assets/data/scripts.bin
echo "$PATCHED_SHA" > /tmp/patched_scripts_sha_v30.txt
popd

# Android release build.
pushd sonic3air-upstream/Oxygen/sonic3air/build/_android
chmod +x gradlew
ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/29.0.14206865" ./gradlew :app:assembleRelease --no-daemon --stacktrace
popd

UNSIGNED=$(find sonic3air-upstream/Oxygen/sonic3air/build/_android/app/build/outputs/apk/release -name '*.apk' | head -1)
test -n "$UNSIGNED"
keytool -genkeypair -v -keystore chaos-edition-v30.keystore -storepass chaosair30 -keypass chaosair30 -alias chaos-edition-v30 -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Chaos Edition 3.0, O=Private Build, C=US'
"$ANDROID_SDK_ROOT/build-tools/36.0.0/zipalign" -p -f 4 "$UNSIGNED" S3AIR_Chaos_Edition_3.0.apk
"$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner" sign --ks chaos-edition-v30.keystore --ks-key-alias chaos-edition-v30 --ks-pass pass:chaosair30 --key-pass pass:chaosair30 S3AIR_Chaos_Edition_3.0.apk
"$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner" verify --verbose --print-certs S3AIR_Chaos_Edition_3.0.apk
unzip -t S3AIR_Chaos_Edition_3.0.apk

rm -rf verify-apk-v30
mkdir verify-apk-v30
unzip -q S3AIR_Chaos_Edition_3.0.apk assets/data/scripts.bin -d verify-apk-v30
EXPECTED=$(cat /tmp/patched_scripts_sha_v30.txt)
ACTUAL=$(sha256sum verify-apk-v30/assets/data/scripts.bin | awk '{print $1}')
test "$EXPECTED" = "$ACTUAL"
sha256sum S3AIR_Chaos_Edition_3.0.apk > S3AIR_Chaos_Edition_3.0.apk.sha256

echo 'CHAOS EDITION 3.0 BUILD VERIFIED'
