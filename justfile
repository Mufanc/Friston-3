run variant="release": (build variant)
    adb push app/build/outputs/apk/{{ variant }}/app-{{ variant }}*.ash /data/local/tmp/friston3.sh
    adb shell chmod +x /data/local/tmp/friston3.sh
    adb shell /data/local/tmp/friston3.sh

build variant="release":
    ./gradlew :app:assemble{{ capitalize(variant) }}
