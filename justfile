run variant="release": stop (build variant)
    adb push app/build/outputs/apk/{{ variant }}/app-{{ variant }}*.ash /data/local/tmp/friston3.sh
    adb shell chmod +x /data/local/tmp/friston3.sh
    adb shell su -c /data/local/tmp/friston3.sh

stop:
    adb shell su -c killall Friston-3 || true

play: stop
    #!/usr/bin/env bash
    FILE=$(adb shell ls -t /data/local/tmp/Friston-3/*.aac | head -1 | tr -d '\r')
    [ -z "$FILE" ] && echo "No recording found" && exit 1
    adb pull "$FILE" .
    ffplay "$(basename "$FILE")"

build variant="release":
    ./gradlew :app:assemble{{ capitalize(variant) }}

clean: stop
    ./gradlew clean
    adb shell rm -rf /data/local/tmp/Friston-3/
    rm -f *.aac
