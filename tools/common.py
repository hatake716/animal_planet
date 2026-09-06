"""データ生成スクリプト共通: HTTP(標準ライブラリのみ)・再試行・並列。"""
import json
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

UA = "animal-planet-data/0.1 (https://github.com/hatake716/animal_planet; acesmash@gmail.com)"


def get(url, params=None, headers=None, timeout=120, retries=5, binary=False):
    if params:
        url = url + ("&" if "?" in url else "?") + urllib.parse.urlencode(params)
    h = {"User-Agent": UA}
    if headers:
        h.update(headers)
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=h)
            with urllib.request.urlopen(req, timeout=timeout) as r:
                data = r.read()
                return data if binary else data.decode("utf-8")
        except Exception as e:  # noqa: BLE001
            last = e
            code = getattr(e, "code", None)
            if code == 404:
                return None
            wait = 3 * (attempt + 1) if code != 429 else 15 * (attempt + 1)
            print(f"  retry {attempt + 1}/{retries} {url[:100]} ({e})", file=sys.stderr)
            time.sleep(wait)
    raise RuntimeError(f"GET failed: {url} ({last})")


def get_json(url, params=None, headers=None, timeout=120, retries=5):
    t = get(url, params, headers, timeout, retries)
    return None if t is None else json.loads(t)


def load(path, default=None):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return default


def save(path, obj):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
    import os
    os.replace(tmp, path)


def pmap(fn, items, workers=4):
    with ThreadPoolExecutor(max_workers=workers) as ex:
        return list(ex.map(fn, items))


def chunks(xs, n):
    for i in range(0, len(xs), n):
        yield xs[i:i + n]
