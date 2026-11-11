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

# navigation: book -> chapter -> verse picker, then a typed reference, then a swipe
tap_text 'الأسفار والإصحاحات' center
shot 3a_picker_books
tap_text '^رومية' center
shot 3b_picker_chapters
tap_text '^٨$' center
shot 3c_picker_verses
tap_text '^٢٨$' center
sleep 2
shot 3d_romans_8_28
tap_text '^بحث$' center
adb shell input text 'John%s3:16'; adb shell input keyevent KEYCODE_ENTER; sleep 4
shot 3e_reference_search
tap_text 'يوحنا ٣:١٦' center
sleep 2
shot 3f_john_3_16
adb shell input swipe 200 1200 900 1200 300
sleep 3
shot 3g_after_swipe
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell input keyevent KEYCODE_BACK; sleep 2
shot 3h_after_back

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

# a rare-word card in three languages (the app opens the word panel for H5390 when started with this extra)
adb shell am start -S -n $PKG/.MainActivity --es sheetStrong H5390
sleep 4
sleep 6
shot 12_card_three_languages
adb shell input swipe 540 1900 540 600 400
sleep 2
shot 13_card_scrolled

echo "=====LOGCAT"
adb logcat -d -s AndroidRuntime:E $PKG:* System.err:W | tail -200
adb shell pidof $PKG && echo "APP ALIVE" || echo "APP DEAD"
