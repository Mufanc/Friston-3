run variant="release": stop (build variant)
    adb push app/build/outputs/apk/{{ variant }}/app-{{ variant }}*.ash /data/local/tmp/friston3.sh
    adb shell chmod +x /data/local/tmp/friston3.sh
    adb shell su -c /data/local/tmp/friston3.sh

stop:
    adb shell su -c killall Friston-3 || true

play: stop
    adb pull /data/misc/perfetto-traces/output.aac .
    ffplay output.aac

build variant="release":
    ./gradlew :app:assemble{{ capitalize(variant) }}

clean: stop
    ./gradlew clean
    adb shell rm -f /data/misc/perfetto-traces/output.aac
