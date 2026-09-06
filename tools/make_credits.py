#!/usr/bin/env python3
"""
credits.json(assemble.py の出力)から docs/CREDITS.md(写真のクレジット一覧)を生成する。
使い方: make_credits.py <credits.json> <docs/CREDITS.md>
"""
import sys
from collections import Counter
from common import load


def main():
    src, dst = sys.argv[1], sys.argv[2]
    credits = load(src)
    lic = Counter(c["license"] for c in credits)
    lines = [
        "# 写真のクレジット一覧",
        "",
        "アプリに同梱する写真はすべて Wikimedia Commons で自由ライセンス(CC0 / パブリックドメイン / CC BY / CC BY-SA)により",
        "公開されている画像を縮小したものです。各写真の作者・ライセンス・原典は次のとおりです(アプリ内の「写真の出典一覧」と同じ内容)。",
        "",
        "ライセンスの内訳: " + "、".join(f"{k} {v} 枚" for k, v in lic.most_common()),
        "",
        "| # | 種 | ファイル(Wikimedia Commons) | 作者 | ライセンス |",
        "|---:|---|---|---|---|",
    ]
    for c in sorted(credits, key=lambda c: c["id"]):
        url = c.get("url") or ("https://commons.wikimedia.org/wiki/File:" + c["file"].replace(" ", "_"))
        author = (c["author"] or "").replace("|", "／")
        lic_md = f"[{c['license']}]({c['licenseUrl']})" if c.get("licenseUrl") else c["license"]
        lines.append(f"| {c['id']} | {c['name']} | [{c['file']}]({url}) | {author} | {lic_md} |")
    with open(dst, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"{len(credits)} credits -> {dst}")


if __name__ == "__main__":
    main()
