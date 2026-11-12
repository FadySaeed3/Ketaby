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
adb shell input keyevent KEYCODE_BACK; sleep 2
shot 1b_home
sleep 1
tap_text 'كمّل من آخر مكان' center
sleep 2
tap_text '^١ ' firstword
sleep 3
shot 2_word_sheet
tap_text 'كل المواضع' center
shot 3_concordance
adb shell input keyevent KEYCODE_BACK; sleep 2

# navigation: book -> chapter -> verse picker, then a typed reference, then a swipe
tap_text 'الأسفار والإصحاحات' center
shot 3a_picker_books
tap_text '^التكوين$' center
sleep 2
shot 3b_picker_chapters
tap_text '^٣$' center
shot 3c_picker_verses
tap_text '^١٥$' center
sleep 2
shot 3d_genesis_3_15
tap_text '^بحث$' center
adb shell input text 'John%s3:16'; adb shell input keyevent KEYCODE_ENTER; sleep 4
shot 3e_reference_search
tap_text 'يوحنا ٣:١٦' center
sleep 2
shot 3f_john_3_16
adb shell input swipe 200 1200 900 1200 300
sleep 3
shot 3g_after_swipe
adb shell input keyevent KEYCODE_BACK; sleep 2
shot 3h_after_back
adb shell input keyevent KEYCODE_BACK; sleep 2
shot 3i_after_back2

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

# HALOT viewer with a stand-in PDF (same page count and size as the real one; the real file never leaves the phone)
python3 .github/fake_lexicon.py halot 3537 612 792 fake_halot.pdf
adb push fake_halot.pdf /data/local/tmp/fake_halot.pdf
adb shell run-as $PKG mkdir -p files/lex
adb shell "cat /data/local/tmp/fake_halot.pdf | run-as $PKG sh -c 'cat > files/lex/halot.pdf'"
adb shell am start -S -n $PKG/.MainActivity --es sheetStrong H5390
sleep 8
adb shell input swipe 540 2000 540 700 400; sleep 2
adb shell input swipe 540 2000 540 900 400; sleep 2
tap_text '^Open in HALOT$' center
sleep 5
shot 14_halot_viewer
# the viewer opened directly on a page, then zoomed twice with the top-bar + (animated transform)
adb shell am start -S -n $PKG/.MainActivity --es sheetStrong lex:halot:1200
sleep 10
shot 14a_viewer_page
tap_text '^\+$' center
sleep 2
tap_text '^\+$' center
sleep 4
shot 14b_viewer_zoomed
tap_text '^Home$' center
sleep 2
shot 15_home_english

# a common Greek word: Van Dyke renderings and the open lexicons (UBS, Moulton & Milligan, LSJ...)
adb shell am start -S -n $PKG/.MainActivity --es sheetStrong G0026
sleep 10
shot 16_greek_word_sheet
adb shell input swipe 540 2000 540 700 400; sleep 2
shot 17_greek_open_lexicons
adb shell input swipe 540 2000 540 700 400; sleep 2
shot 18_greek_open_lexicons_more

# concordance -> verse: which verses are on screen after opening each hit (Van Dyke + BSB + interlinear at this point)
adb logcat -c
adb shell am start -S -n $PKG/.MainActivity --es sheetStrong conc:G3056
sleep 10
shot 19_concordance_logos
adb shell input swipe 540 1900 540 700 400; sleep 2
for ref in 'Matthew 7:24$' 'Matthew 7:26$'; do
  tap_text "^$ref" center
  sleep 6
  shot "20_open_${ref//[^0-9]/_}"
  adb shell input keyevent KEYCODE_BACK; sleep 3
  shot "20b_back_${ref//[^0-9]/_}"
done
# a verse near the end of a chapter, with the English line under each verse: the verse must stay on screen
for spot in 40:7:28 40:5:47; do
  adb shell am start -S -n $PKG/.MainActivity --es sheetStrong read:$spot
  sleep 10
  shot "21_read_${spot//:/_}"
done
# Jesuit translation: first without a package (the import screen), then with a tiny fake package
adb shell am start -S -n $PKG/.MainActivity --es sheetStrong jesuit
sleep 10
shot 22_jesuit_setup
python3 .github/jesuit_fixture.py fx.ketaby
adb push fx.ketaby /data/local/tmp/fx.ketaby
adb shell chmod 644 /data/local/tmp/fx.ketaby
adb shell run-as $PKG mkdir -p files/tr
adb shell run-as $PKG cp /data/local/tmp/fx.ketaby files/tr/jesuit.ketaby || \
  adb shell "run-as $PKG sh -c 'cat > files/tr/jesuit.ketaby'" < fx.ketaby
adb shell run-as $PKG ls -l files/tr
adb shell am start -S -n $PKG/.MainActivity --es sheetStrong jesuit
sleep 10
shot 23_jesuit_books
tap_text '^التكوين$' center
sleep 2
shot 23b_jesuit_chapters
adb shell am start -S -n $PKG/.MainActivity --es sheetStrong jread:1:1:3
sleep 10
shot 24_jesuit_reader
tap_text 'Van Dyke|^فاندايك$' center
sleep 4
shot 25_vd_from_jesuit
tap_text '^(View|طريقة العرض)$' center
tap_text '(Jesuit under each verse|اليسوعية تحت كل آية)' center
sleep 4
shot 26_vd_with_jesuit_under
adb shell am start -S -n $PKG/.MainActivity --es sheetStrong jread:1:1:0
sleep 8
tap_text '^(Introduction|مقدمة السفر)$' center
shot 27_jesuit_intro
adb shell am start -S -n $PKG/.MainActivity --es sheetStrong read:1:2:1
sleep 10
shot 28_vd_gen2_under
tap_text '^(View|طريقة العرض)$' center
shot 29_vd_menu_with_jesuit

echo "=====READER LOG"
adb logcat -d -s KetabyReader:D | tail -80

echo "=====LOGCAT"
adb logcat -d -s AndroidRuntime:E $PKG:* System.err:W | tail -200
adb shell pidof $PKG && echo "APP ALIVE" || echo "APP DEAD"
