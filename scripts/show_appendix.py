#!/usr/bin/env python3
"""Print the rendered provenance appendix from the built PDF.

Run with the project venv's python (pymupdf lives there):
    python
        scripts/show_appendix.py
"""
import os
import re
import sys

import fitz

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PDF = os.path.join(ROOT, "report", "main.pdf")
OUT = os.path.join(ROOT, "notes", "_final_pdf_text.txt")


def main():
    doc = fitz.open(PDF)
    txt = "\n".join(p.get_text() for p in doc)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(txt)
    print("pages: %d" % doc.page_count)

    i = txt.find("The COMSOL models that produced")
    if i < 0:
        print("*** the .mph provenance section is NOT in the PDF ***")
        return 1
    print("\n=== rendered .mph provenance section ===")
    print(re.sub(r"\s+", " ", txt[i:i + 1400]))

    print("\n=== every comsol/ path that appears in the rendered report ===")
    for m in sorted(set(re.findall(r"comsol/[A-Za-z0-9_/\.\-]+", txt))):
        print("   ", m)

    # The report groups digits with an SI thin space (30\,336\,324), which
    # extracts from the PDF as a plain space, so compare on spaces.
    flat = re.sub(r"\s+", " ", txt)
    print("\n=== the .mph sizes that must appear ===")
    for s in ["30 336 324 B", "701 782 749 B", "39 186 746 B", "113 809 B", "705 316 B"]:
        print("   %-18s %s" % (s, "present" if s in flat else "*** MISSING ***"))
    doc.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
