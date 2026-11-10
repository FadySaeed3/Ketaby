#!/usr/bin/env bash
# Smoke test on an emulator: launch, open a word, the concordance and the interlinear view.
set -x
mkdir -p shots
PKG=com.fadysaeed.ketaby
adb install -r apk/*.apk
adb logcat -c
adb shell am start -n $PKG/.MainActivity
sleep 75

shot() {
  adb exec-out screencap -p > "shots/$1.png"
  echo "=====SHOT $1 BEGIN"
  base64 -w 0 "shots/$1.png"; echo
  echo "=====SHOT $1 END"
}

# tap the node whose text matches a python regex; dx/dy pick the point inside its bounds
tap_text() {
  adb shell uiautomator dump /sdcard/u.xml >/dev/null
  adb pull /sdcard/u.xml u.xml >/dev/null
  python3 - "$1" "$2" <<'PY' > tap.txt
import re, sys
pat, where = sys.argv[1], sys.argv[2]
xml = open('u.xml', encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    t, x1, y1, x2, y2 = m.group(1), *map(int, m.groups()[1:])
    if re.search(pat, t):
        if where == 'firstword':
            print(x2 - 140, y1 + 40)
        else:
            print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
  echo "tap $1 -> $(cat tap.txt)"
  if [ -s tap.txt ]; then adb shell input tap $(cat tap.txt); fi
  sleep 4
}

shot 1_reader
tap_text '^١ ' firstword
shot 2_word_sheet
tap_text 'كل المواضع' center
shot 3_concordance
adb shell input keyevent KEYCODE_BACK; sleep 2
tap_text 'بين السطور' center
sleep 3
shot 4_interlinear
tap_text 'بين السطور' center
tap_text 'الإصحاح الجاي' center
shot 5_next_chapter

echo "=====LOGCAT"
adb logcat -d -s AndroidRuntime:E $PKG:* System.err:W | tail -200
adb shell pidof $PKG && echo "APP ALIVE" || echo "APP DEAD"
