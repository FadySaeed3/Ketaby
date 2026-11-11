#!/usr/bin/env bash
# Smoke test on an emulator: launch, open a word, the concordance and the interlinear view.
set -x
mkdir -p shots
PKG=com.fadysaeed.ketaby
adb install -r apk/*.apk
# the emulator's launcher keeps raising "isn't responding" dialogs that block taps; switch it off
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
adb shell settings put global hide_error_dialogs 1 || true
adb logcat -c
adb shell am start -n $PKG/.MainActivity
sleep 75

shot() {
  dismiss_anr
  adb exec-out screencap -p > "shots/$1.png"
  echo "=====SHOT $1 BEGIN"
  base64 -w 0 "shots/$1.png"; echo
  echo "=====SHOT $1 END"
}

# tap the node whose text matches a python regex; dx/dy pick the point inside its bounds
# close "X isn't responding" system dialogs that slow emulators sometimes raise
dismiss_anr() {
  for i in 1 2 3; do
    adb shell uiautomator dump /sdcard/u.xml >/dev/null
    adb pull /sdcard/u.xml u.xml >/dev/null
    grep -q "responding" u.xml || return 0
    xy=$(python3 -c "import re;x=open('u.xml',encoding='utf-8').read();m=re.search(r'text=\"Wait\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]',x);print((int(m[1])+int(m[3]))//2,(int(m[2])+int(m[4]))//2) if m else print('')")
    echo "ANR dialog, tapping Wait at $xy"
    [ -n "$xy" ] && adb shell input tap $xy
    sleep 3
  done
}

tap_text() {
  dismiss_anr
  adb shell uiautomator dump /sdcard/u.xml >/dev/null
  adb pull /sdcard/u.xml u.xml >/dev/null
  python3 - "$1" "$2" <<'PY' > tap.txt
import re, sys
pat, where = sys.argv[1], sys.argv[2]
xml = open('u.xml', encoding='utf-8').read()
for node in re.findall(r'<node [^>]*>', xml):
    a = dict(re.findall(r'([\w-]+)="([^"]*)"', node))
    t = (a.get('text') or a.get('content-desc') or '').replace('&#10;', ' ')
    b = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', a.get('bounds', ''))
    if not b:
        continue
    x1, y1, x2, y2 = map(int, b.groups())
    if re.search(pat, t):
        if where == 'firstword':
            print(x2 - 140, y1 + 40)
        elif where == 'leftword':
            print(x1 + 160, y1 + 30)
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
sleep 3
shot 2_word_sheet
tap_text 'كل المواضع' center
shot 3_concordance
adb shell input keyevent KEYCODE_BACK; sleep 2

# Van Dyke with KJV underneath, then tap an English word
tap_text '^طريقة العرض$' center
tap_text '^فاندايك وتحته KJV$' center
sleep 2
shot 4_parallel_kjv
tap_text '^In the beginning' leftword
sleep 2
shot 5_english_word
adb shell input keyevent KEYCODE_BACK; sleep 2

# switch to BSB and the interlinear view
tap_text '^طريقة العرض$' center
tap_text '^BSB$' center
tap_text '^طريقة العرض$' center
tap_text 'بين السطور' center
sleep 3
shot 6_bsb_interlinear

# English interface and explanations in all three languages
tap_text '^الإعدادات$' center
tap_text '^English$' center
sleep 2
tap_text '^All three$' center
shot 7_settings_english
adb shell input keyevent KEYCODE_BACK; sleep 2
shot 8_reader_english_ui

# Greek: search a Strong's number, open its concordance, then a verse
tap_text '^Search$' center
adb shell input text G3056; adb shell input keyevent KEYCODE_ENTER; sleep 4
shot 9_search
tap_text 'λόγος' center
shot 10_greek_concordance
tap_text '^Matthew ' center
sleep 3
shot 11_matthew

# a rare-word card in three languages: search H5390, open Song 1:2 from the concordance
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
tap_text '^Search$' center
adb shell input text H5390; adb shell input keyevent KEYCODE_ENTER; sleep 4
tap_text 'נְשִׁיקָה' center
tap_text 'kisses' center
sleep 3
adb shell uiautomator dump /sdcard/u.xml >/dev/null; adb pull /sdcard/u.xml u.xml >/dev/null
echo "=====NODES"; python3 -c "
import re;x=open('u.xml',encoding='utf-8').read()
for n in re.findall(r'<node [^>]*>',x)[:80]:
    a=dict(re.findall(r'([\w-]+)=\"([^\"]*)\"',n)); print(repr((a.get('text') or a.get('content-desc') or '')[:50]), a.get('class'), a.get('clickable'), a.get('bounds'))
"
tap_text '^kisses$' center
sleep 3
shot 12_card_three_languages

echo "=====LOGCAT"
adb logcat -d -s AndroidRuntime:E $PKG:* System.err:W | tail -200
adb shell pidof $PKG && echo "APP ALIVE" || echo "APP DEAD"
