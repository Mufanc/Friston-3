HOST_TAG := (if os() == "macos" { "darwin" } else { os() }) + "-x86_64"

TARGET     := "aarch64-linux-android"
TARGET_SDK := "35"

build variant="release": (build-java variant) (build-rust variant)

build-java variant="release":
    ./gradlew :core:assemble{{ capitalize(variant) }}

build-rust variant="release":
    cargo build \
        --manifest-path audioserver-patch/Cargo.toml \
        --target {{ TARGET }} \
        {{ if variant == "release" { "--release" } else { "" } }} \
        --config target.{{ TARGET }}.linker=\"$ANDROID_NDK/toolchains/llvm/prebuilt/{{ HOST_TAG }}/bin/{{ TARGET }}{{ TARGET_SDK }}-clang\"

package variant="release": (build variant)
    #!/usr/bin/env bash
    set -euo pipefail
    STAGING="build/module-staging"
    rm -rf "$STAGING" build/friston3-module.zip
    cp -r module "$STAGING"
    mkdir -p "$STAGING/bin"
    cp audioserver-patch/target/{{ TARGET }}/{{ variant }}/audioserver-patch "$STAGING/bin/"
    cp core/build/outputs/apk/{{ variant }}/core-{{ variant }}*.ash "$STAGING/bin/friston3.sh"
    (cd "$STAGING" && zip -r ../../build/friston3-module.zip .)
    rm -rf "$STAGING"

clean:
    ./gradlew clean
    cargo clean --manifest-path audioserver-patch/Cargo.toml
    rm -rf build/module-staging build/friston3-module.zip
