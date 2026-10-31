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
STOCK_SHA=$(sha256sum extracted-apk/assets/data/scripts.bin | awk '{print $1}')

# Keep an untouched source snapshot. Existing script patchers are used only to
# generate the built-in Chaos Core mod; the APK keeps the official scripts.bin.
rm -rf /tmp/s3air-stock-scripts-v30
cp -a sonic3air-upstream/Oxygen/sonic3air/scripts /tmp/s3air-stock-scripts-v30

# Generate the desired Chaos behavior in a temporary patched source tree.
# Do not add the old Water/Emerald patchers here.
python3 chaos-edition/patch_save_banks.py sonic3air-upstream
python3 chaos-edition/patch_savebank_v30_cleanup.py sonic3air-upstream
python3 chaos-edition/patch_death_berserker.py sonic3air-upstream
python3 chaos-edition/patch_chaos_hud.py sonic3air-upstream

# Turn every changed/new function into a normal, always-on S3AIR script mod.
rm -rf sonic3air-upstream/Oxygen/sonic3air/data/chaos_core
python3 chaos-edition/build_builtin_core.py \
  /tmp/s3air-stock-scripts-v30 \
  sonic3air-upstream/Oxygen/sonic3air/scripts \
  sonic3air-upstream/Oxygen/sonic3air/data/chaos_core
cp -a sonic3air-upstream/Oxygen/sonic3air/data/chaos_core \
  sonic3air-upstream/Oxygen/sonic3air/build/_android/app/src/main/assets/data/

# Engine/platform patches: built-in core loader + universal mod buttons.
python3 chaos-edition/patch_builtin_core.py sonic3air-upstream
python3 chaos-edition/patch_universal_buttons.py sonic3air-upstream
python3 chaos-edition/patch_android_identity.py sonic3air-upstream

# The ROM bypass is only for the Linux CI validation executable. Restore the
# real ResourcesCache before the Android build so the shipped APK keeps normal
# S3AIR ROM handling.
cp sonic3air-upstream/Oxygen/oxygenengine/source/oxygen/resources/ResourcesCache.cpp /tmp/ResourcesCache.cpp.v30.stock
python3 chaos-edition/patch_ci_rom_bypass.py sonic3air-upstream

# Assertions: platform + core are present; rejected legacy ability pack is absent.
grep -n 'ChaosProgression.updateSonic' sonic3air-upstream/Oxygen/sonic3air/data/chaos_core/scripts/main.lemon
grep -n 'DataSelect.switchChaosSaveBank' sonic3air-upstream/Oxygen/sonic3air/data/chaos_core/scripts/main.lemon
grep -n 'chaosLives = 3' sonic3air-upstream/Oxygen/sonic3air/data/chaos_core/scripts/main.lemon
grep -n 'ChaosProgression.recordDeath' sonic3air-upstream/Oxygen/sonic3air/data/chaos_core/scripts/main.lemon
grep -n 'chaos-edition-core' sonic3air-upstream/Oxygen/oxygenengine/source/oxygen/application/modding/ModManager.cpp
grep -n 'chaos-buttons.json' sonic3air-upstream/Oxygen/oxygenengine/source/oxygen/application/overlays/TouchControlsOverlay.cpp
grep -n 'accessController(3)' sonic3air-upstream/Oxygen/oxygenengine/source/oxygen/application/overlays/TouchControlsOverlay.cpp
grep -n 'com.chaosed.sonic3air.v30' sonic3air-upstream/Oxygen/sonic3air/build/_android/app/build.gradle
if grep -R -n -E 'WATER SONIC|HYPER WATER|Emerald power|comfortable layout v0.2' \
  sonic3air-upstream/Oxygen/sonic3air/data/chaos_core \
  sonic3air-upstream/Oxygen/oxygenengine/source/oxygen/application/overlays/TouchControlsOverlay.cpp; then
  echo 'Rejected legacy Chaos ability code leaked into 3.0 build' >&2
  exit 1
fi

# Build a Linux host executable and validate stock scripts.bin + the packaged
# Chaos Core mod together. We deliberately hide base source main.lemon so the
# engine loads the official compiled scripts first, then compiles only our mod.
pushd sonic3air-upstream/Oxygen/sonic3air
mkdir -p build/_cmake/build
pushd build/_cmake/build
cmake -DCMAKE_BUILD_TYPE=Release ..
cmake --build . -j 4
popd
test -x ./sonic3air_linux

mv scripts scripts_patched_v30
mkdir -p scripts/_reference
rm -rf saves
mkdir -p saves
set +e
timeout 45s xvfb-run -a ./sonic3air_linux -dumpcppdefinitions 2>&1 | tee ../../../lemon-compiler-v30.log
STATUS=${PIPESTATUS[0]}
set -e
echo "host validation exit: $STATUS" | tee -a ../../../lemon-compiler-v30.log
if [ "$STATUS" -ne 0 ]; then
  echo 'stock scripts + Chaos Core host validation failed' | tee -a ../../../lemon-compiler-v30.log
  exit 20
fi
grep -q 'Loaded built-in Chaos Edition 3.0 Core' ../../../lemon-compiler-v30.log
if grep -q -E 'Script compile error|Failed to load base scripts|Caught unhandled exception|Segmentation fault' ../../../lemon-compiler-v30.log; then
  echo 'Chaos Core LemonScript validation reported an error' | tee -a ../../../lemon-compiler-v30.log
  exit 21
fi

# Prove the base script binary remained the official one.
test "$(sha256sum data/scripts.bin | awk '{print $1}')" = "$STOCK_SHA"
popd

# Restore normal ROM handling for the Android APK.
cp /tmp/ResourcesCache.cpp.v30.stock sonic3air-upstream/Oxygen/oxygenengine/source/oxygen/resources/ResourcesCache.cpp

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

# Package verification: official base scripts + built-in core source must both be present.
rm -rf verify-apk-v30
mkdir verify-apk-v30
unzip -q S3AIR_Chaos_Edition_3.0.apk \
  assets/data/scripts.bin \
  assets/data/chaos_core/mod.json \
  assets/data/chaos_core/scripts/main.lemon \
  -d verify-apk-v30
test "$(sha256sum verify-apk-v30/assets/data/scripts.bin | awk '{print $1}')" = "$STOCK_SHA"
grep -q '"UniqueID": "chaos-edition-core"' verify-apk-v30/assets/data/chaos_core/mod.json
grep -q 'ChaosProgression.updateSonic' verify-apk-v30/assets/data/chaos_core/scripts/main.lemon
grep -q 'DataSelect.switchChaosSaveBank' verify-apk-v30/assets/data/chaos_core/scripts/main.lemon
sha256sum S3AIR_Chaos_Edition_3.0.apk > S3AIR_Chaos_Edition_3.0.apk.sha256

echo 'CHAOS EDITION 3.0 BUILD VERIFIED'
