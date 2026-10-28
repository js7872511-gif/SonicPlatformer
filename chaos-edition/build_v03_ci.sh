#!/usr/bin/env bash
set -euo pipefail

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

python3 chaos-edition/patch_touch_controls.py sonic3air-upstream
python3 chaos-edition/patch_save_banks.py sonic3air-upstream
python3 chaos-edition/patch_ci_rom_bypass.py sonic3air-upstream

grep -n 'comfortable layout v0.2' sonic3air-upstream/Oxygen/oxygenengine/source/oxygen/application/overlays/TouchControlsOverlay.cpp
grep -n 'global u8 chaos.save_bank' sonic3air-upstream/Oxygen/sonic3air/scripts/general/saveslots.lemon
grep -n 'Chaos Edition CI' sonic3air-upstream/Oxygen/oxygenengine/source/oxygen/resources/ResourcesCache.cpp

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
timeout 30s xvfb-run -a ./sonic3air_linux -dumpcppdefinitions -nativize 2>&1 | tee ../../../lemon-compiler.log
STATUS=${PIPESTATUS[0]}
set -e
echo "compiler exit: $STATUS" | tee -a ../../../lemon-compiler.log
test -s saves/scripts.bin
PATCHED_SHA=$(sha256sum saves/scripts.bin | awk '{print $1}')
echo "stock: $STOCK_SHA" | tee -a ../../../lemon-compiler.log
echo "patched: $PATCHED_SHA" | tee -a ../../../lemon-compiler.log
test "$STOCK_SHA" != "$PATCHED_SHA"
cp saves/scripts.bin build/_android/app/src/main/assets/data/scripts.bin
echo "$PATCHED_SHA" > /tmp/patched_scripts_sha.txt
popd

python3 chaos-edition/patch_android_identity.py sonic3air-upstream
grep -n 'com.chaosed.sonic3air.v3' sonic3air-upstream/Oxygen/sonic3air/build/_android/app/build.gradle

pushd sonic3air-upstream/Oxygen/sonic3air/build/_android
chmod +x gradlew
ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/29.0.14206865" ./gradlew :app:assembleRelease --no-daemon --stacktrace
popd

UNSIGNED=$(find sonic3air-upstream/Oxygen/sonic3air/build/_android/app/build/outputs/apk/release -name '*.apk' | head -1)
test -n "$UNSIGNED"
keytool -genkeypair -v -keystore chaos-edition.keystore -storepass chaosair -keypass chaosair -alias chaos-edition -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Chaos Edition v0.3, O=Private Build, C=US'
"$ANDROID_SDK_ROOT/build-tools/36.0.0/zipalign" -p -f 4 "$UNSIGNED" S3AIR_Chaos_Edition_v0.3.apk
"$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner" sign --ks chaos-edition.keystore --ks-key-alias chaos-edition --ks-pass pass:chaosair --key-pass pass:chaosair S3AIR_Chaos_Edition_v0.3.apk
"$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner" verify --verbose --print-certs S3AIR_Chaos_Edition_v0.3.apk
unzip -t S3AIR_Chaos_Edition_v0.3.apk
rm -rf verify-apk
mkdir verify-apk
unzip -q S3AIR_Chaos_Edition_v0.3.apk assets/data/scripts.bin -d verify-apk
EXPECTED=$(cat /tmp/patched_scripts_sha.txt)
ACTUAL=$(sha256sum verify-apk/assets/data/scripts.bin | awk '{print $1}')
test "$EXPECTED" = "$ACTUAL"
sha256sum S3AIR_Chaos_Edition_v0.3.apk > S3AIR_Chaos_Edition_v0.3.apk.sha256
