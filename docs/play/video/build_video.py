#!/usr/bin/env python3
"""
録画(rec_raw.mp4)と marks.txt から、Play のプレビュー動画用の縦型ショート動画を組み立てる。

Play のガイドライン(support.google.com/googleplay/android-developer/answer/9866151)に沿う:
  - 実際のアプリ画面が大半を占めるようにする(タイトルは冒頭のみ)
  - 冒頭 10 秒以内に主要機能を見せる / 自動再生される 30 秒以内に収める
  - 「今すぐダウンロード」等の行動喚起、順位・評価・価格の表現を入れない
  - 縦動画の左右に黒帯を作らない(1080x1920 に上下トリミングして 9:16 に収める)
出力: promo_1080x1920.mp4(字幕焼き込み・無音)
"""
import json
import os
import re
import subprocess

S = os.path.dirname(os.path.abspath(__file__))
FONT = "/nix/store/f81jg2anzxk668xajfn11mhaqq1v1ppd-noto-fonts-cjk-sans-2.004/share/fonts/opentype/noto-cjk/NotoSansCJK-VF.otf.ttc"
SRC = os.path.join(S, "rec_cfr.mp4")   # 可変フレームレートのままだと -t が効かないので事前に 30fps へ正規化した版
OUT = os.path.join(S, "promo_1080x1920.mp4")
OFFSET = 2.5      # 録画開始から demo.sh 開始までの秒数
CROP_Y = 150      # 1080x2400 から 9:16 を切り出す上端(ステータスバーを落とす)
TITLE_DUR = 1.6


def marks():
    out = {}
    with open(os.path.join(S, "marks.txt"), encoding="utf-8") as f:
        for line in f:
            m = re.match(r"MARK ([\d.]+) (\S+)", line.strip())
            if m:
                out[m.group(2)] = float(m.group(1)) + OFFSET
    return out


def esc(text):
    return text.replace("\\", r"\\\\").replace(":", r"\:").replace("'", r"\\\'").replace(",", r"\,")


def drawtext(text, y, size=58, color="white"):
    """帯なしのテキスト。帯が必要なときは呼び出し側で drawbox を先に置く。"""
    return (f"drawtext=fontfile='{FONT}':text='{esc(text)}':fontsize={size}:"
            f"fontcolor={color}:x=(w-text_w)/2:y={y}")


def subtitle(text, size=56):
    """画面下端に敷いた帯の上に字幕を載せる(文字ごとに背景が切れないよう drawbox を使う)。"""
    band_h = 165
    # 背面のアプリ画面が透けないよう完全不透明の帯にする(半透明だと下の文字と混ざって読めない)
    return (f"drawbox=x=0:y=ih-{band_h}:w=iw:h={band_h}:color=0x0A1512:t=fill,"
            f"drawbox=x=0:y=ih-{band_h}:w=iw:h=3:color=0xFF8A65:t=fill,"
            + drawtext(text, f"h-{band_h}+({band_h}-text_h)/2", size))


def main():
    mk = marks()
    # (開始, 終了, 字幕) — 各シーンから見せ場だけを切り出して 30 秒以内に収める
    clips = [
        (mk["scene1_globe"] + 0.4, mk["scene1_globe"] + 5.6, "地球儀を回して、絶滅危惧種に出会う"),
        (mk["scene2_search"] + 0.3, mk["scene2_search"] + 4.6, "和名・学名で検索"),
        (mk["scene2_card"] - 1.6, mk["scene3_photo"] - 0.2, "写真・解説・生息地・主な脅威"),
        (mk["scene3_photo"] + 0.6, mk["scene3_photo"] + 3.6, "写真は全画面でも"),
        (mk["scene4_filter"] + 3.2, mk["scene4_filter"] + 7.0, "分類と生息地でしぼりこみ"),
        (mk["scene4_filtered"] - 0.6, mk["scene4_filtered"] + 2.4, "日本の哺乳類だけを表示"),
        (mk["scene5_list"] + 0.8, mk["scene5_list"] + 5.4, "1,063 種を一覧でも"),
    ]

    parts = []
    total = 0.0
    for i, (a, b, sub) in enumerate(clips):
        d = b - a
        total += d
        out = os.path.join(S, f"_clip{i}.mp4")
        vf = f"crop=1080:1920:0:{CROP_Y},{subtitle(sub)},format=yuv420p"
        subprocess.run([
            "ffmpeg", "-v", "error", "-y", "-ss", f"{a:.3f}", "-t", f"{d:.3f}", "-i", SRC,
            "-vf", vf, "-c:v", "libx264", "-preset", "slow", "-crf", "20",
            "-r", "30", "-an", out,
        ], check=True)
        parts.append(out)
        print(f"clip{i}: {a:6.2f}..{b:6.2f} ({d:4.2f}s) {sub}")
    print(f"body total {total:.2f}s + title {TITLE_DUR}s")

    # タイトル(冒頭)。アプリ名と収録数だけ。
    title = os.path.join(S, "_title.mp4")
    tf = ",".join([
        drawtext("地球儀で見る", "h/2-200", 70),
        drawtext("絶滅危惧種生物図鑑", "h/2-90", 86),
        drawtext("IUCN レッドリストの動物 1,063 種", "h/2+70", 44, "0xA5D6A7"),
        "format=yuv420p",
    ])
    subprocess.run([
        "ffmpeg", "-v", "error", "-y", "-f", "lavfi",
        "-i", f"color=0x061410:s=1080x1920:d={TITLE_DUR}:r=30",
        "-vf", tf, "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-an", title,
    ], check=True)

    # 連結(タイトル → 各クリップ)
    lst = os.path.join(S, "_concat.txt")
    with open(lst, "w") as f:
        for p in [title] + parts:
            f.write(f"file '{p}'\n")
    subprocess.run([
        "ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst,
        "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-pix_fmt", "yuv420p",
        "-movflags", "+faststart", "-an", OUT,
    ], check=True)

    info = subprocess.run(["ffprobe", "-v", "error", "-show_entries",
                           "format=duration,size:stream=width,height,r_frame_rate",
                           "-of", "json", OUT], capture_output=True, text=True).stdout
    d = json.loads(info)
    print("duration", d["format"]["duration"], "size", int(d["format"]["size"]) // 1024, "KB",
          d["streams"][0]["width"], "x", d["streams"][0]["height"])
    print("->", OUT)


if __name__ == "__main__":
    main()
