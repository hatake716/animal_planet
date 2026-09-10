#!/usr/bin/env bash
# Play 用プロモ動画の操作シナリオ。エミュレータ(1080x2400)を adb で操作する。
# 各シーンの境目で「MARK <経過秒> <名前>」を stderr に出し、後で字幕を合わせる。
# 前面のアプリが対象アプリでなくなったら即座に中断する(別アプリの映り込み防止)。
set -u
E="adb -s emulator-5560"
PKG=io.github.hatake716.endangeredglobe
START=$(date +%s.%N)
mark() {
  local cur
  cur=$($E shell "dumpsys window | grep mCurrentFocus")
  case "$cur" in
    *"$PKG"*) : ;;
    *) echo "ABORT: foreground is not $PKG at $1: $cur" >&2; exit 1 ;;
  esac
  printf 'MARK %s %s\n' "$(python3 -c "import time;print(f'{time.time()-$START:.2f}')")" "$1" >&2
}
tap() { $E shell input tap "$1" "$2"; }
swipe() { $E shell input swipe "$1" "$2" "$3" "$4" "$5"; }
pause() { sleep "$1"; }

# --- シーン1: 地球儀を回す(つかみ) -----------------------------------------
mark scene1_globe
pause 1.4
swipe 780 1200 380 1150 900          # 西へ回す
pause 1.2
swipe 700 1300 430 1080 900          # 少し斜めに
pause 1.5

# --- シーン2: 検索 → 種を選ぶ → 詳細カード ---------------------------------
mark scene2_search
tap 540 218                          # 検索バー
pause 0.7
$E shell input text "panda"
pause 1.4
tap 411 550                          # 検索結果のジャイアントパンダ
pause 3.4                            # 飛行アニメーション + カード表示
mark scene2_card
pause 2.8

# --- シーン3: 写真を全画面 ---------------------------------------------------
mark scene3_photo
tap 537 1092                         # 詳細カード内の写真
pause 3.0
$E shell input keyevent BACK
pause 1.2

# --- シーン4: 絞り込み(分類 × 生息地) --------------------------------------
mark scene4_filter
$E shell input keyevent BACK         # カードを閉じる
pause 0.8
tap 95 1875                          # 地球全体を表示(絞り込みの効果が見えるよう引く)
pause 2.6
tap 974 218                          # 絞り込みボタン
pause 1.5
tap 176 1475                         # 哺乳類
pause 0.5
tap 127 1822                         # 日本
pause 1.5
$E shell input keyevent BACK         # シートを閉じる
pause 2.4
mark scene4_filtered
pause 1.6

# --- シーン5: 一覧 -----------------------------------------------------------
mark scene5_prep
tap 974 218                          # 絞り込みを開く
pause 1.2
tap 940 1044                         # 全選択(絞り込み解除)
pause 0.6
$E shell input keyevent BACK
pause 1.8
mark scene5_list
tap 95 1434                          # 生物一覧
pause 2.4
swipe 540 1800 540 900 700           # スクロール
pause 1.5
swipe 540 1800 540 1000 700
pause 1.8
mark end
