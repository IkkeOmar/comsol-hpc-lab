"""
build_ex4.py - produce the QUICK (API smoke test) and FULL (production)
variants of the COMSOL Java model.

The COMSOL security manager blocks System.getenv AND System.getProperty, so
the run mode has to be baked into the source as a compile-time constant.
This script rewrites the __QUICK_MARKER__ line for each variant and calls
comsolcompile.

Usage:  python build_ex4.py [--quick|--full]
"""
from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

WORK = Path(r"<project>")
SRC = WORK / "comsol" / "Ex4_FFF_new.java"
COMPILER = r"C:\Program Files\COMSOL\COMSOL64\Multiphysics\bin\win64\comsolcompile.exe"

MARKER = "    static final boolean QUICK = false;  // __QUICK_MARKER__"


def build(quick: bool, out_name: str) -> Path:
    text = SRC.read_text(encoding="utf-8")
    if MARKER not in text:
        raise SystemExit("MARKER NOT FOUND in " + str(SRC))
    new = text.replace(
        MARKER,
        f"    static final boolean QUICK = {'true' if quick else 'false'};  // __QUICK_MARKER__",
    )
    target = WORK / "comsol" / out_name
    target.write_text(new, encoding="utf-8")
    # comsolcompile names the class after the file, so the public class name
    # must match the file name.
    cls = out_name[:-5]  # strip .java
    new = new.replace("public class Ex4_FFF {", f"public class {cls} {{")
    target.write_text(new, encoding="utf-8")

    r = subprocess.run(
        [COMPILER, str(target)],
        capture_output=True, text=True,
        env={**__import__("os").environ, "MSYS2_ARG_CONV_EXCL": "*"},
    )
    print(f"[build] {out_name}: compile rc={r.returncode}")
    if r.stdout.strip():
        print(r.stdout[-3000:])
    if r.stderr.strip():
        print(r.stderr[-3000:])
    if r.returncode != 0:
        raise SystemExit("compile failed")
    return target


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "--full"
    if mode == "--quick":
        build(True, "Ex4_FFF_quick.java")
    else:
        build(False, "Ex4_FFF_prod.java")
