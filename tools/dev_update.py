#!/usr/bin/env python3
"""Publica una versión de QuietDLP en el origen LOCAL de actualizaciones. Solo para desarrollo.

La app (en su versión de depuración) busca versiones nuevas en http://127.0.0.1:8765. Este script:
  1) copia el APK a dist/serve/QuietDLP.apk
  2) escribe dist/serve/release.json con el mismo formato que usa GitHub Releases (así se prueba el mismo código)
  3) con --serve, abre un servidor HTTP SOLO en 127.0.0.1 (nadie más en la red puede entrar)

Uso:
    python tools/dev_update.py app/build/outputs/apk/debug/app-debug.apk --notes "Qué cambia" --serve
    adb reverse tcp:8765 tcp:8765      # acerca el servidor al móvil por el cable USB

Después, en el móvil: Ajustes > Buscar ahora (o se avisa sola al abrir la app).
"""
import argparse
import hashlib
import http.server
import json
import os
import re
import shutil
import subprocess
import sys
from functools import partial

PORT = 8765
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SERVE = os.path.join(ROOT, "dist", "serve")


def find_aapt2():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or os.path.join(
        os.path.expanduser("~"), "AppData", "Local", "Android", "Sdk")
    tools = os.path.join(sdk, "build-tools")
    for v in sorted(os.listdir(tools), reverse=True):
        for exe in ("aapt2.exe", "aapt2"):
            p = os.path.join(tools, v, exe)
            if os.path.exists(p):
                return p
    sys.exit("No encuentro aapt2 en el SDK de Android")


def apk_version(apk):
    out = subprocess.run([find_aapt2(), "dump", "badging", apk], capture_output=True, text=True).stdout
    name = re.search(r"versionName='([^']+)'", out)
    code = re.search(r"versionCode='(\d+)'", out)
    pkg = re.search(r"package: name='([^']+)'", out)
    if not (name and code and pkg):
        sys.exit("No pude leer la versión del APK")
    return pkg.group(1), name.group(1), int(code.group(1))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("apk")
    ap.add_argument("--notes", default="")
    ap.add_argument("--serve", action="store_true")
    a = ap.parse_args()

    pkg, version, code = apk_version(a.apk)
    os.makedirs(SERVE, exist_ok=True)
    dest = os.path.join(SERVE, "QuietDLP.apk")
    shutil.copyfile(a.apk, dest)
    sha = hashlib.sha256(open(dest, "rb").read()).hexdigest()
    release = {
        "tag_name": "v" + version,
        "draft": False,
        "prerelease": False,
        "body": a.notes,
        "assets": [{
            "name": "QuietDLP.apk",
            "browser_download_url": "http://127.0.0.1:%d/QuietDLP.apk" % PORT,
            "size": os.path.getsize(dest),
            "digest": "sha256:" + sha,
        }],
    }
    with open(os.path.join(SERVE, "release.json"), "w", encoding="utf-8") as fh:
        json.dump(release, fh, indent=1, ensure_ascii=False)
    print("Publicado en local: %s versión %s (código %d), %d bytes, sha256 %s…" % (
        pkg, version, code, release["assets"][0]["size"], sha[:12]))

    if a.serve:
        handler = partial(http.server.SimpleHTTPRequestHandler, directory=SERVE)
        srv = http.server.ThreadingHTTPServer(("127.0.0.1", PORT), handler)
        print("Sirviendo en http://127.0.0.1:%d (solo este PC). Ctrl+C para parar." % PORT)
        try:
            srv.serve_forever()
        except KeyboardInterrupt:
            pass


if __name__ == "__main__":
    main()
