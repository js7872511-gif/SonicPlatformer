#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("sonic3air-upstream")
p = root / "Oxygen/sonic3air/build/_android/app/build.gradle"
s = p.read_text(encoding="utf-8")
s = s.replace('versionName "26.03.28.0"', 'versionName "26.03.28.0-chaos3"')
s = s.replace("abiFilters 'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'", "abiFilters 'arm64-v8a'")
marker = "defaultConfig {\n"
if 'applicationId "com.chaosed.sonic3air.v3"' not in s:
    if marker not in s:
        raise SystemExit("defaultConfig block not found")
    s = s.replace(marker, marker + '        applicationId "com.chaosed.sonic3air.v3"\n', 1)
p.write_text(s, encoding="utf-8")

res = root / "Oxygen/sonic3air/build/_android/app/src/main/res"
for f in res.rglob("*"):
    if f.is_file():
        try:
            text = f.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        if "Sonic 3 A.I.R." in text:
            f.write_text(text.replace("Sonic 3 A.I.R.", "S3AIR Chaos Edition v0.3"), encoding="utf-8")
print("patched Chaos Edition v0.3 Android identity")
