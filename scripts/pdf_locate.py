#!/usr/bin/env python3
"""Locate report content by page, and list every figure with its page.

Run with the project venv's python (pymupdf lives there):
    python
        scripts/pdf_locate.py
"""
import os
import re
import sys

import fitz

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PDF = os.path.join(ROOT, "report", "main.pdf")

TARGETS = [
    "Switching the AC interface",
    "production (Electrostatics",
    "Refining the",
    "comes to rest at",
    "footprint is only reached",
    "crosses half of",
    "The Ex2 device as COMSOL built it",
    "The Ex4 device as COMSOL built it",
    "seen from above",
    "coplanar",
    "moves the final height",
    "The Ex1 device as COMSOL built it",
    "velocity magnitude in the same window",
    "concentration field at the inlet",
]


def canon(s):
    """Undo the PDF's ligatures and collapse whitespace."""
    s = s.replace("\ufb01", "fi").replace("\ufb02", "fl")
    s = s.replace("\u2013", "-").replace("\u2014", "--")
    return re.sub(r"\s+", " ", s)


def main():
    doc = fitz.open(PDF)
    pages = [canon(p.get_text()) for p in doc]

    print("pages: %d" % doc.page_count)
    print("\n--- target locations ---")
    for t in TARGETS:
        hits = [i + 1 for i, p in enumerate(pages) if t in p]
        print("  %-42s %s" % (t, hits or "NOT FOUND"))

    print("\n--- every figure caption, with page ---")
    for i, p in enumerate(pages):
        for m in re.finditer(r"Figure (\d+):\s*([^|]{0,72})", p):
            print("  p%-3d Fig %-3s %s" % (i + 1, m.group(1), m.group(2).strip()[:70]))

    print("\n--- section headings by page ---")
    for i, p in enumerate(pages):
        for m in re.finditer(r"\b(\d+(?:\.\d+)*)\s+([A-Z][^.]{4,60}?)(?=\s|\.|\s[A-Z])", p):
            pass
        # crude: lines that look like headings
    for i, page in enumerate(doc):
        for blk in page.get_text("dict")["blocks"]:
            for line in blk.get("lines", []):
                for sp in line.get("spans", []):
                    t = sp["text"].strip()
                    if sp["size"] > 11.5 and len(t) > 3:
                        print("  p%-3d size=%.1f  %s" % (i + 1, sp["size"], t[:66]))
                        break
    doc.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
