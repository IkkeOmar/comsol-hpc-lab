#!/usr/bin/env python3
"""Crop a region of the built PDF to PNG at high resolution, for close inspection.

Usage:
    python scripts/pdf_crop.py <page_no> <x0> <y0> <x1> <y1> <out.png> [dpi]

Coordinates are PDF points (1/72 in), origin top-left.
Run with the project venv's python (pymupdf lives there).
"""
import os
import sys

import fitz

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PDF = os.path.join(ROOT, "report", "main.pdf")


def main():
    if len(sys.argv) < 7:
        print(__doc__)
        return 2
    page_no = int(sys.argv[1])
    x0, y0, x1, y1 = (float(v) for v in sys.argv[2:6])
    out = sys.argv[6]
    dpi = int(sys.argv[7]) if len(sys.argv) > 7 else 300

    doc = fitz.open(PDF)
    page = doc[page_no - 1]
    clip = fitz.Rect(x0, y0, x1, y1)
    pix = page.get_pixmap(dpi=dpi, clip=clip)
    out_path = out if os.path.isabs(out) else os.path.join(ROOT, out)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    pix.save(out_path)
    print("wrote %s  (%dx%d px, page %d of %d, page size %s)"
          % (out_path, pix.width, pix.height, page_no, doc.page_count,
             tuple(round(v) for v in page.rect[2:])))
    doc.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
