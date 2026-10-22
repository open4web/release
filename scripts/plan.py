#!/usr/bin/env python3
"""决定本次要构建哪些应用、用什么版本号，输出 GitHub Actions 的 matrix。

环境变量：
  EVENT     github.event_name（push / workflow_dispatch / repository_dispatch）
  REF_NAME  tag 名（push 时，如 x6-v1.2.3）
  IN_APP    all / x5 / x6（手动或 repository_dispatch）
  IN_VER    版本号，留空则在该应用最新 tag 上 patch+1，首次为 1.0.0
"""
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEMVER = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")


def die(msg):
    print(f"::error::{msg}", file=sys.stderr)
    sys.exit(1)


def known_apps():
    d = os.path.join(ROOT, "apps")
    return sorted(k for k in os.listdir(d) if os.path.isfile(os.path.join(d, k, "app.json")))


def tags_for(app):
    out = subprocess.run(["git", "tag", "-l", f"{app}-v*"], cwd=ROOT, capture_output=True, text=True, check=True).stdout
    vers = []
    for t in out.split():
        m = SEMVER.match(t[len(app) + 2:])
        if m:
            vers.append(tuple(int(x) for x in m.groups()))
    return sorted(vers)


def entry(app, version):
    cfg = json.load(open(os.path.join(ROOT, "apps", app, "app.json"), encoding="utf-8"))
    return {"app": app, "version": version, "tag": f"{app}-v{version}", "slug": cfg["slug"], "name": cfg["name"]}


def main():
    event = os.environ.get("EVENT", "")
    apps = known_apps()
    items = []
    if event == "push":
        ref = os.environ.get("REF_NAME", "")
        m = re.match(r"^([a-z0-9]+)-v(\d+\.\d+\.\d+)$", ref)
        if not m or m.group(1) not in apps:
            die(f"无法识别的 tag：{ref}（格式应为 x6-v1.2.3）")
        items.append(entry(m.group(1), m.group(2)))
    else:
        want = (os.environ.get("IN_APP") or "all").strip()
        targets = apps if want in ("", "all") else [want]
        for a in targets:
            if a not in apps:
                die(f"未知应用：{a}（可选：{', '.join(apps)}）")
        ver = (os.environ.get("IN_VER") or "").strip().lstrip("v")
        if ver and not SEMVER.match(ver):
            die(f"版本号格式错误：{ver}（应为 1.2.3）")
        for a in targets:
            existing = tags_for(a)
            if ver:
                if tuple(int(x) for x in ver.split(".")) in existing:
                    die(f"{a}-v{ver} 已存在，请换一个版本号")
                v = ver
            elif existing:
                ma, mi, pa = existing[-1]
                v = f"{ma}.{mi}.{pa + 1}"
            else:
                v = "1.0.0"
            items.append(entry(a, v))
    print("matrix=" + json.dumps({"include": items}, ensure_ascii=False))
    for it in items:
        print(f"计划构建 {it['name']}（{it['app']}）{it['tag']}", file=sys.stderr)


if __name__ == "__main__":
    main()
