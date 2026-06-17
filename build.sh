#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"

if [[ ! -d "$SDK" ]]; then
  echo "Android SDK not found. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 1
fi

latest_dir() {
  local dir="$1"
  local found
  found="$(find "$dir" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -1 || true)"
  [[ -n "$found" ]] || return 1
  printf '%s\n' "$found"
}

BUILD_TOOLS="${BUILD_TOOLS:-$(latest_dir "$SDK/build-tools")}"
PLATFORM_DIR="${ANDROID_PLATFORM_DIR:-$(latest_dir "$SDK/platforms")}"
ANDROID_JAR="$PLATFORM_DIR/android.jar"

for tool in aapt2 d8 zipalign apksigner; do
  if [[ ! -x "$BUILD_TOOLS/$tool" ]]; then
    echo "Missing $tool in $BUILD_TOOLS" >&2
    exit 1
  fi
done

if [[ ! -f "$ANDROID_JAR" ]]; then
  echo "Missing android.jar in $PLATFORM_DIR" >&2
  exit 1
fi

OUT="$ROOT/build/local"
APK_UNSIGNED="$OUT/mcu-root-flasher-unsigned.apk"
APK_WITH_DEX="$OUT/mcu-root-flasher-withdex-unsigned.apk"
APK_ALIGNED="$OUT/mcu-root-flasher-aligned.apk"
APK_SIGNED="$ROOT/build/mcu-root-flasher.apk"
KEYSTORE="$ROOT/build/debug.keystore"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/apk" "$OUT/res" "$ROOT/build"

if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null
fi

if [[ -d "$ROOT/res" ]]; then
  "$BUILD_TOOLS/aapt2" compile \
    --dir "$ROOT/res" \
    -o "$OUT/res/resources.zip"
  RES_ARGS=("$OUT/res/resources.zip")
else
  RES_ARGS=()
fi

"$BUILD_TOOLS/aapt2" link \
  -o "$APK_UNSIGNED" \
  -I "$ANDROID_JAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  "${RES_ARGS[@]}"

SOURCES_FILE="$OUT/java-sources.txt"
find "$ROOT/src" -name '*.java' | sort > "$SOURCES_FILE"

javac \
  -encoding UTF-8 \
  -source 1.8 \
  -target 1.8 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$OUT/classes" \
  @"$SOURCES_FILE"

"$BUILD_TOOLS/d8" \
  --min-api 23 \
  --lib "$ANDROID_JAR" \
  --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class' | sort)

(
  cd "$OUT/apk"
  jar xf "$APK_UNSIGNED"
  cp "$OUT/dex/classes.dex" .
  jar cf "$APK_WITH_DEX" .
)

"$BUILD_TOOLS/zipalign" -f 4 "$APK_WITH_DEX" "$APK_ALIGNED"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$APK_SIGNED" \
  "$APK_ALIGNED"

"$BUILD_TOOLS/apksigner" verify "$APK_SIGNED"
echo "$APK_SIGNED"
