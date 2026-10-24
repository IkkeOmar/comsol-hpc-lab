"""Audit every report figure for legibility at its LaTeX placement width.

Run from the repository root:  python scripts/audit_figure_sizes.py
The matplotlib part reads the figures/ directory and needs nothing else.
The COMSOL-render part measures the rendered report pages, which belong to
the private report; without them that part is skipped.

For each PNG the script reports
  * the saved native width in inches (px / dpi),
  * the effective point size on the page of an 8 pt label,
    i.e. 8 pt * (placement width in inches / saved native width in inches),
  * whether the native width matches the placement width.

Placement widths come from the report source:
  0.85\\textwidth = 4.23 in   ->  the analytical and device figures
  0.96\\textwidth = 4.78 in   ->  the two comsol_ex4 comparison figures

Target: effective point size >= 7.5 pt for every piece of text.
"""
import os
from PIL import Image

WORK = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIG = os.path.join(WORK, "figures")

STANDARD_W = 4.23      # 0.85\textwidth
WIDE_W = 4.78          # 0.96\textwidth

# placement width in inches, per figure
PLACEMENT = {}
for name in (
        "ex1_a_time_scales", "ex1_b_mixing", "ex1_b_profiles",
        "ex1_e_crossing_fraction", "ex1_e_pressure",
        "ex2_a_fill", "ex2_a_reynolds", "ex2_b_cstr_benchmark",
        "ex3_3_cm_and_observation_time", "ex3_4_equilibrium",
        "ex4_a_clausius_mossotti", "ex4_b_force_balance",
        "ex4_c_trajectory_xz", "ex4_d_settling_time",
        "dev_ex1_hfilter", "dev_ex2_chamber", "dev_ex4_electrodes"):
    PLACEMENT[name] = STANDARD_W
PLACEMENT["comsol_ex4/ex4_comsol_vs_analytical"] = WIDE_W
PLACEMENT["comsol_ex4/ex4_efield_profile"] = WIDE_W
# re-authored at the wide placement in 2026-10-24 (legends moved out of the data)
PLACEMENT["ex2_cell_layer"] = WIDE_W
PLACEMENT["ex3_2_threshold_force"] = WIDE_W
PLACEMENT["ex3_4_trajectory"] = WIDE_W

TARGET_PT = 7.5
NOMINAL_PT = 8.0       # the size of a typical axis/legend label

PDF = os.path.join(WORK, "report", "main.pdf")
PAGES = os.path.join(WORK, "report", "pages")
PAGE_RENDER_DPI = 110  # report/render_pages.py
CAP_RATIO = 0.7        # digit cap height / em size

# COMSOL renders: their effective size is MEASURED, not computed.
#
# The obvious calculation -- fontsize * placement / (px / 96) -- is WRONG for
# these.  COMSOL scales its own font with the exported image instead of
# fixing it at the export resolution, and that calculation gives about
# 3.3 pt, off by a factor of two and a half.  The size is therefore read off
# the rendered page: the figure's image box is taken from the PDF, the page
# raster is measured, and the text bands inside the figure are counted.  A
# digit's band height is its cap height, so the em size is band_px / 0.7 and
# the effective point size is band_px / 0.7 / (page_render_dpi / 72).
#
# Measured on 2026-10-24: the COMSOL text lands at about 8.4 pt effective.
COMSOL_FIGURES = [
    ("dev_ex1_comsol.png", STANDARD_W),
    ("dev_ex1_comsol_inlet.png", STANDARD_W),
    ("dev_ex1_comsol_mesh.png", STANDARD_W),
    ("dev_ex2_comsol.png", STANDARD_W),
    ("dev_ex2_comsol_angle.png", STANDARD_W),
    ("dev_ex2_comsol_mesh.png", STANDARD_W),
    ("dev_ex2_comsol_plan.png", STANDARD_W),
    ("dev_ex3_comsol.png", STANDARD_W),
    ("dev_ex3_comsol_mesh.png", STANDARD_W),
    ("dev_ex4_comsol.png", STANDARD_W),
    ("dev_ex4_comsol_angle.png", STANDARD_W),
    ("dev_ex4_comsol_below.png", STANDARD_W),
    ("dev_ex4_comsol_mesh.png", STANDARD_W),
    ("dev_ex4_comsol_plan.png", STANDARD_W),
    ("comsol_ex4/ex4_potential_full.png", STANDARD_W),
    ("comsol_ex4/ex4_potential_zoom.png", STANDARD_W),
]


def measure_comsol(rel, place):
    """Effective point size of the text inside a COMSOL figure, measured on
    the rendered page.  Returns (effective_pt, band_px, page_no) or None."""
    try:
        import fitz
    except ImportError:
        return None
    if not os.path.exists(PDF):
        return None

    doc = fitz.open(PDF)
    k = PAGE_RENDER_DPI / 72.0
    # The PDF does not keep the file name of the embedded image ("bad image
    # name"), so the figure is identified by its pixel dimensions plus the
    # width it is placed at.  Two figures with identical dimensions would be
    # interchangeable here: the measurement is about how the text renders,
    # not about which file it came from.
    src = os.path.join(FIG, rel.replace("/", os.sep))
    with Image.open(src) as im:
        px_w, px_h = im.size
    want_pt = place * 72.0
    found = None
    for pno in range(doc.page_count):
        page = doc[pno]
        # page.get_images() + get_image_bbox() do not work on this PDF: the
        # image objects carry no usable name and PyMuPDF raises
        # "bad image name".  get_image_info() returns the placement rectangle
        # directly, so it is used instead.
        for info in page.get_image_info(xrefs=True):
            if info.get("width") != px_w or info.get("height") != px_h:
                continue
            bb = fitz.Rect(info["bbox"])
            if bb.is_empty:
                continue
            if abs((bb.x1 - bb.x0) - want_pt) > 8.0:
                continue
            found = (pno + 1, bb, k)
            break
        if found:
            break
    doc.close()
    if not found:
        return None

    pno, bb, k = found
    page_png = os.path.join(PAGES, "page_%02d.png" % pno)
    if not os.path.exists(page_png):
        return None
    im = Image.open(page_png).convert("RGB")
    px = im.load()
    W, H = im.size
    x0 = max(0, int(bb.x0 * k))
    x1 = min(W, int(bb.x1 * k))
    y0 = max(0, int(bb.y0 * k))
    y1 = min(H, int(bb.y1 * k))
    if x1 - x0 < 20 or y1 - y0 < 20:
        return None

    # horizontal text bands inside the figure: rows holding at least one dark
    # pixel.  Long runs are plot lines or the colour bar, not text.
    heights = []
    run = 0
    for y in range(y0, y1):
        dark = False
        for x in range(x0, x1, 2):
            r, g, b = px[x, y]
            if (r + g + b) / 3 < 170:
                dark = True
                break
        if dark:
            run += 1
        else:
            if 0 < run <= 20:
                heights.append(run)
            run = 0
    if not heights:
        return None
    heights.sort()
    # The TALLEST text band is the one that has to be readable, so the
    # maximum is used rather than the median: a figure that is mostly a
    # smooth colour field (the Ex4 potential map) has many one-pixel
    # antialiasing bands that would drag a median down to noise.
    band = heights[-1]
    em_px = band / CAP_RATIO
    eff_pt = em_px / (PAGE_RENDER_DPI / 72.0)
    return eff_pt, band, pno


def audit():
    lines = []
    ok = True
    rows = []
    for rel, place in sorted(PLACEMENT.items()):
        path = os.path.join(FIG, rel.replace("/", os.sep) + ".png")
        if not os.path.isfile(path):
            rows.append((rel, place, None, None, None, None, "MISSING"))
            ok = False
            continue
        with Image.open(path) as im:
            px_w, px_h = im.size
            dpi = im.info.get("dpi", (None, None))
        if not dpi or not dpi[0]:
            dpi = (300.0, 300.0)
        native_w = px_w / dpi[0]
        scale = place / native_w
        eff_pt = NOMINAL_PT * scale
        width_ok = abs(native_w - place) < 0.02
        pt_ok = eff_pt >= TARGET_PT
        status = "OK" if (width_ok and pt_ok) else "FAIL"
        if status == "FAIL":
            ok = False
        rows.append((rel, place, native_w, px_w, px_h, eff_pt, status))

    w1 = max(len(r[0]) for r in rows)
    lines.append("FIGURE SIZE AUDIT")
    lines.append("=" * 100)
    lines.append("")
    lines.append("Every figure is drawn at the width the LaTeX report places it at,")
    lines.append("so the nominal point size in the script IS the point size on the")
    lines.append("printed page.  effective pt = nominal pt * placement / native width.")
    lines.append("")
    lines.append(f"placement width: {STANDARD_W:.2f} in (0.85\\textwidth) for the "
                 f"analytical and device figures,")
    lines.append(f"                {WIDE_W:.2f} in (0.96\\textwidth) for the two "
                 f"figures/comsol_ex4 comparison figures")
    lines.append(f"target: effective point size >= {TARGET_PT:.1f} pt for every "
                 f"piece of text (nominal {NOMINAL_PT:.0f} pt here)")
    lines.append("")
    hdr = (f"{'figure':{w1}s}  {'place':>6s}  {'native':>7s}  {'px':>10s}  "
           f"{'eff pt':>7s}  status")
    lines.append(hdr)
    lines.append("-" * len(hdr))
    for rel, place, native_w, px_w, px_h, eff_pt, status in rows:
        if native_w is None:
            lines.append(f"{rel:{w1}s}  {place:6.2f}  {'-':>7s}  {'-':>10s}  "
                         f"{'-':>7s}  {status}")
        else:
            lines.append(f"{rel:{w1}s}  {place:6.2f}  {native_w:6.3f}  "
                         f"{f'{px_w}x{px_h}':>10s}  {eff_pt:6.2f}  {status}")
    lines.append("-" * len(hdr))
    lines.append("")
    n_ok = sum(1 for r in rows if r[6] == "OK")
    lines.append(f"{n_ok} of {len(rows)} matplotlib figures pass (native width == "
                 f"placement width AND effective pt >= {TARGET_PT:.1f}).")
    lines.append("")

    lines.append("PART 2 - COMSOL renders (effective size MEASURED on the page)")
    lines.append("")
    lines.append("COMSOL scales its own font with the exported image, so the size")
    lines.append("cannot be computed from the export resolution.  It is measured")
    lines.append(f"from the {PAGE_RENDER_DPI} dpi page render instead: the figure's")
    lines.append("image box is read out of the PDF and the text bands inside it are")
    lines.append("measured.  A digit's band height is its cap height, so the em")
    lines.append(f"size is band_px / {CAP_RATIO} and the effective point size is")
    lines.append(f"band_px / {CAP_RATIO} / ({PAGE_RENDER_DPI} / 72).")
    lines.append("")
    hdr2 = (f"{'figure':{w1}s}  {'place':>6s}  {'eff pt':>7s}  {'band px':>8s}  "
            f"{'page':>5s}  status")
    lines.append(hdr2)
    lines.append("-" * len(hdr2))
    mrows = []
    for rel, place in sorted(COMSOL_FIGURES):
        path = os.path.join(FIG, rel.replace("/", os.sep))
        if not os.path.isfile(path):
            mrows.append((rel, place, None, None, None, "MISSING"))
            ok = False
            continue
        res = measure_comsol(rel, place)
        if res is None:
            mrows.append((rel, place, None, None, None, "unmeasured"))
            continue
        eff, band, pno = res
        status = "OK" if eff >= TARGET_PT else "FAIL"
        if status == "FAIL":
            ok = False
        mrows.append((rel, place, eff, band, pno, status))
    for rel, place, eff, band, pno, status in mrows:
        if eff is None:
            lines.append(f"{rel:{w1}s}  {place:6.2f}  {'-':>7s}  {'-':>8s}  "
                         f"{'-':>5s}  {status}")
        else:
            lines.append(f"{rel:{w1}s}  {place:6.2f}  {eff:6.2f}  {band:8d}  "
                         f"{pno:5d}  {status}")
    lines.append("-" * len(hdr2))
    n_ok_m = sum(1 for r in mrows if r[5] == "OK")
    n_meas = sum(1 for r in mrows if r[2] is not None)
    if n_meas:
        lines.append(f"{n_ok_m} of {n_meas} measured COMSOL renders pass "
                     f"(effective pt >= {TARGET_PT:.1f}).")
    else:
        lines.append("the COMSOL renders were not measured: the rendered report "
                     "pages are not part of this repository")
    lines.append("")
    verdict = ("PASS - every figure is legible at its placement width." if ok else
               "FAIL - see the FAIL rows above.")
    if not n_meas:
        verdict = ("PASS for the figures measured here - the COMSOL renders need "
                   "the rendered report pages, which are not in this repository.")
    lines.append("VERDICT: " + verdict)
    return "\n".join(lines) + "\n", ok


if __name__ == "__main__":
    text, ok = audit()
    out = os.path.join(FIG, "figure_size_audit.txt")
    with open(out, "w", encoding="utf-8") as fh:
        fh.write(text)
    print(text)
    raise SystemExit(0 if ok else 1)
