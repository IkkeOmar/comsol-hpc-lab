#!/usr/bin/env python3
"""Build the "which figures are COMSOL's own" table for the provenance appendix.

The report mixes three kinds of figure:

  * analytical figures drawn by the Python scripts in scripts/,
  * COMSOL DEVICE drawings - renders of the geometry COMSOL itself built,
  * COMSOL SOLUTION outputs - renders of a solved field.

The table in the appendix has to be right, so it is generated from the report
sources rather than typed by hand:

  * the figure environments are parsed out of each exercise's .tex file, in
    order, which fixes the figure numbers (the exercises are included in the
    order ex1, ex2, ex3, ex4);
  * the page numbers come from the built PDF, by finding each caption on the
    page it renders on.

Run after the report is compiled:
    python
        scripts/build_comsol_inventory.py
"""
import os
import re

import fitz

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPORT = os.path.join(ROOT, "report")
PDF = os.path.join(REPORT, "main.pdf")
TEX = os.path.join(REPORT, "appendix_provenance.tex")

SECTIONS = [
    ("ex1_hfilter.tex", "Ex1"),
    ("ex2_chamber.tex", "Ex2"),
    ("ex3_dep.tex", "Ex3"),
    ("ex4_fff.tex", "Ex4"),
]

FIG_RE = re.compile(r"\\begin\{figure\}.*?\\end\{figure\}", re.S)
INC_RE = re.compile(r"\\includegraphics(?:\[[^\]]*\])?\{([^}]+)\}")
CAP_RE = re.compile(r"\\caption\{(.*?)\}\s*$", re.S)


def first_sentence(text):
    """The caption up to its first full stop, as PLAIN TEXT.

    The report's captions carry LaTeX (unit macros like \\um\\dd{m}, \\times,
    math in $...$).  A naive "strip every backslash command" pass mangles them
    into undefined control sequences (\\umm), so the macros the report
    actually uses are replaced by their rendered text first and only then is
    any leftover command removed.
    """
    # the report's own macros, rendered the way the preamble renders them
    t = text.replace("\\um\\dd{m}", " um").replace("\\um", " um")
    t = t.replace("\\dd{", " ").replace("\\mathrm{", " ")
    t = t.replace("\\text{", " ").replace("\\mu", " u").replace("\\times", "x")
    t = t.replace("\\approx", "~").replace("\\,", " ").replace("\\!", "")
    t = t.replace("\\%", "%").replace("\\$", "").replace("$", "")
    t = t.replace("\\&", "&").replace("\\#", "#")
    # inline and display math delimiters: the caption text has to end up as
    # PLAIN text, and a leftover \( ... \) or a stray ^ or _ in text mode
    # makes LaTeX stop with "Missing $ inserted".
    t = t.replace("\\(", " ").replace("\\)", " ").replace("\\[", " ").replace("\\]", " ")
    # any remaining command: drop the command, keep its braces' content
    t = re.sub(r"\\[a-zA-Z]+\*?(\[[^\]]*\])?\{([^{}]*)\}", r"\2", t)
    t = re.sub(r"\\[a-zA-Z]+\*?", "", t)
    t = t.replace("{", "").replace("}", "")
    # ^ and _ are math-mode characters; in running text they are a LaTeX error
    t = t.replace("^", "").replace("_", " ")
    t = re.sub(r"\s+", " ", t).strip()
    for stop in (". ", "? "):
        i = t.find(stop)
        if i > 20:
            t = t[:i + 1]
            break
    return t


def short_descriptor(caption):
    """A one-line description for the inventory table.

    The caption's first clause is enough: the full caption is right above it
    in the report.  Truncating with an ellipsis read as unfinished text, so
    the clause is cut at a natural boundary instead.
    """
    c = caption.strip()
    for stop in (": ", "; ", ", "):
        i = c.find(stop)
        if 20 < i <= 78:
            c = c[:i]
            break
    else:
        if len(c) > 78:
            c = c[:75].rsplit(" ", 1)[0]
    return c.strip().rstrip(".")


def classify(path):
    p = path.lower()
    if p.startswith("dev_ex"):
        return "device drawing"
    if "comsol" in p:
        return "solution output"
    return "analytical"


def page_of_caption(doc, number):
    """Which page a figure lands on.

    Located by the figure NUMBER, not by the caption text: matching on the
    caption's first words picked the wrong page whenever two figures shared
    wording (the page numbers came out wrong for five rows).
    """
    pat = re.compile(r"Figure\s+%d\s*:" % number)
    for pno in range(doc.page_count):
        if pat.search(re.sub(r"\s+", " ", doc[pno].get_text())):
            return pno + 1
    return None


def collect():
    figs = []
    number = 0
    for tex, ex in SECTIONS:
        t = open(os.path.join(REPORT, tex), encoding="utf-8").read()
        for block in FIG_RE.findall(t):
            inc = INC_RE.search(block)
            cap = CAP_RE.search(block.replace("\n", " ").strip())
            if not inc:
                continue
            number += 1
            captext = first_sentence(cap.group(1)) if cap else ""
            figs.append({
                "num": number,
                "ex": ex,
                "path": inc.group(1),
                "kind": classify(inc.group(1)),
                "caption": captext,
                "page": None,
            })
    return figs


def build_table(figs):
    lines = []
    lines.append(r"\needspace{26\baselineskip}")
    lines.append(r"\subsection*{Which figures are COMSOL's own}")
    lines.append(r"")
    lines.append(r"The report mixes figures drawn by the analytical Python scripts with")
    lines.append(r"pictures that COMSOL itself produced.  The table below lists every")
    lines.append(r"figure that comes out of COMSOL, so it is clear what was simulated")
    lines.append(r"and what was only computed in Python.  The device drawings are renders")
    lines.append(r"of the geometry COMSOL built; they are not photographs of hardware.")
    lines.append(r"")
    lines.append(r"\begin{center}")
    lines.append(r"\footnotesize")
    lines.append(r"\setlength{\tabcolsep}{3pt}")
    lines.append(r"\begin{tabular}{@{}>{\raggedright\arraybackslash}p{0.055\textwidth}"
                 r">{\raggedright\arraybackslash}p{0.055\textwidth}"
                 r">{\raggedright\arraybackslash}p{0.225\textwidth}"
                 r">{\raggedright\arraybackslash}p{0.495\textwidth}@{}}")
    lines.append(r"\toprule")
    lines.append(r"figure & page & source file & what it shows \\")
    lines.append(r"\midrule")
    for f in figs:
        if f["kind"] == "analytical":
            continue
        # the file names are long and typewriter text does not break on its
        # own, so a break point goes after every slash AND every underscore
        src = f["path"].replace("_", r"\_\allowbreak ")
        src = src.replace("/", r"/\allowbreak ")
        # a SHORT one-line descriptor: the full caption ends in the body
        # text, and a truncated caption in a table reads as unfinished.
        cap = short_descriptor(f["caption"])
        kind = "Device drawing" if f["kind"] == "device drawing" else "Solution output"
        lines.append("Fig.~%d & %s & \\texttt{%s} & %s: %s \\\\"
                     % (f["num"],
                        f["page"] if f["page"] else "--",
                        src,
                        kind,
                        cap))
    lines.append(r"\bottomrule")
    lines.append(r"\end{tabular}")
    lines.append(r"\end{center}")
    lines.append(r"")
    n_dev = sum(1 for f in figs if f["kind"] == "device drawing")
    n_sol = sum(1 for f in figs if f["kind"] == "solution output")
    n_ana = sum(1 for f in figs if f["kind"] == "analytical")
    lines.append(r"Of the %d figures in the report, %d are COMSOL device drawings and %d"
                 % (len(figs), n_dev, n_sol))
    lines.append(r"are COMSOL solution outputs; the remaining %d are drawn by the"
                 % n_ana)
    lines.append(r"analytical scripts.  Every exercise has at least one device drawing,")
    lines.append(r"and every exercise that was solved in COMSOL also shows the solved")
    lines.append(r"field itself.")
    lines.append(r"")
    return "\n".join(lines)


def main():
    figs = collect()

    # The page numbers come from the built PDF.  The PDF is often deleted
    # just before a rebuild, so a missing one is not an error: the table is
    # still generated and the page column is filled in on the next run.
    if os.path.exists(PDF):
        doc = fitz.open(PDF)
        for f in figs:
            f["page"] = page_of_caption(doc, f["num"])
        doc.close()
    else:
        print("NOTE: %s not built yet; page numbers left blank." % PDF)

    table = build_table(figs)

    tex = open(TEX, encoding="utf-8").read()
    # drop a previously generated block, then insert the fresh one
    start = tex.find(r"\subsection*{Which figures are COMSOL's own}")
    if start >= 0:
        end = tex.find(r"\subsection*{A note on the COMSOL API}", start)
        if end < 0:
            raise SystemExit("cannot find the end of the generated block")
        tex = tex[:start] + tex[end:]
    anchor = r"\subsection*{A note on the COMSOL API}"
    if anchor not in tex:
        raise SystemExit("anchor not found")
    tex = tex.replace(anchor, table + "\n" + anchor, 1)
    open(TEX, "w", encoding="utf-8", newline="").write(tex)

    print("figures found: %d" % len(figs))
    for f in figs:
        print("  Fig %2d  p%-4s %-16s %s" % (f["num"], f["page"], f["kind"], f["path"]))
    print("\nappendix updated: %s (%d B)" % (TEX, os.path.getsize(TEX)))


if __name__ == "__main__":
    main()
