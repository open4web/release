#!/usr/bin/env python3
"""发布完成后更新 apps/latest.json（x8 下载页在运行时读取它）。

用法：update-manifest.py <app> <version> <tag> <资产目录>
"""
import datetime
import hashlib
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = os.environ.get("GITHUB_REPOSITORY", "open4web/release")
BASE = f"https://github.com/{REPO}/releases/download"

KINDS = {
    "android": "-android.apk",
    "windowsSetup": "-windows-setup.exe",
    "windowsPortable": "-windows-portable.exe",
}


def sha256(p):
    h = hashlib.sha256()
    with open(p, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    app, version, tag, assets_dir = sys.argv[1:5]
    cfg = json.load(open(os.path.join(ROOT, "apps", app, "app.json"), encoding="utf-8"))
    path = os.path.join(ROOT, "apps", "latest.json")
    data = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {"apps": {}}
    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    assets = {}
    for kind, suffix in KINDS.items():
        name = cfg["slug"] + suffix
        p = os.path.join(assets_dir, name)
        if not os.path.exists(p):
            continue
        assets[kind] = {
            "name": name,
            "size": os.path.getsize(p),
            "sha256": sha256(p),
            "url": f"{BASE}/{app}-latest/{name}",
            "versionedUrl": f"{BASE}/{tag}/{name}",
        }
    data["apps"][app] = {
        "key": app,
        "name": cfg["name"],
        "description": cfg["description"],
        "webUrl": cfg["url"],
        "androidAppId": cfg["androidAppId"],
        "version": version,
        "tag": tag,
        "releasedAt": now,
        "releaseUrl": f"https://github.com/{REPO}/releases/tag/{tag}",
        "assets": assets,
    }
    data["updatedAt"] = now
    data["schema"] = 1
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2, sort_keys=False)
        f.write("\n")
    print(json.dumps(data["apps"][app], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
