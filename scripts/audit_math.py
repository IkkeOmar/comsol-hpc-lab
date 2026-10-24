#!/usr/bin/env python3
"""Audit the report .tex files for math-delimiter damage.

Independent checks, no compiler involved:
  1. every blank-line separated paragraph has an even number of unescaped '$'
  2. every $...$ span contains a math expression, not prose

Also replays the exact regex from Hermes message 48314 (the damage source)
over the pre-damage Git blobs, so the damaged sites can be attributed.
"""
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REP = os.path.join(ROOT, "report")
OUT = os.path.join(ROOT, "notes", "_audit_math.txt")

FILES = ["ex1_hfilter.tex", "ex2_chamber.tex", "ex3_dep.tex", "ex4_fff.tex"]
BLOBS = {
    "ex1_hfilter.tex": ("git", "40c4296:report/ex1_hfilter.tex"),
    "ex2_chamber.tex": ("blob", "3c66cd2b333eb4ebdfc5cdaa632c86a715e61973"),
    "ex3_dep.tex":     ("blob", "5a54d5d879c4fde265894754ce3451e1fd010a80"),
    "ex4_fff.tex":     ("blob", "bd76f75d16d863a7dbe432484f6ebee43c26c39a"),
}

# the damage regex, verbatim from Hermes message 48314
DAMAGE = re.compile(r'\$((?:[0-9]+(?:\.[0-9]+)?|\\[a-zA-Z]+))\$(\\,[^$]*?)\$')


def git_blob(kind, ref):
    if kind == "git":
        r = subprocess.run(["git", "show", ref], cwd=ROOT, capture_output=True)
    else:
        r = subprocess.run(["git", "cat-file", "-p", ref], cwd=ROOT, capture_output=True)
    return r.stdout.decode("utf-8", "replace")


def scan(text):
    """Return (unescaped dollar positions, math spans) honouring % comments and \\$."""
    pos = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == "\\":
            i += 2
            continue
        if c == "%":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if c == "$":
            pos.append(i)
        i += 1
    spans = []
    open_at = None
    for p in pos:
        if open_at is None:
            open_at = p
        else:
            spans.append((open_at, p))
            open_at = None
    return pos, spans


def strip_cmds(s):
    s = re.sub(r"\\[a-zA-Z]+\s*\{[^{}]*\}", " ", s)
    s = re.sub(r"\\[a-zA-Z]+", " ", s)
    s = re.sub(r"\\[^\s]", " ", s)
    return s


def prose_words(s):
    return [w for w in re.findall(r"[A-Za-z]{2,}", strip_cmds(s))]


def para_spans(text):
    out, start = [], 0
    for m in re.finditer(r"\n[ \t]*\n", text):
        out.append((start, m.start()))
        start = m.end()
    out.append((start, len(text)))
    return out


def main():
    lines = []
    for name in FILES:
        path = os.path.join(REP, name)
        text = open(path, encoding="utf-8", newline="").read()
        pos, spans = scan(text)

        lines.append("=" * 78)
        lines.append("FILE %s   bytes=%d   unescaped '$'=%d   math spans=%d"
                     % (name, len(text), len(pos), len(spans)))

        # ---- odd paragraphs
        lines.append("-- odd-dollar paragraphs (paragraph $-count must be even)")
        n_odd = 0
        for a, b in para_spans(text):
            k = len([p for p in pos if a <= p < b])
            if k % 2:
                n_odd += 1
                lines.append("   @%-6d $=%d  %r" % (a, k, text[a:min(b, a + 220)]))
        lines.append("   odd paragraphs: %d" % n_odd)

        # ---- prose inside math
        lines.append("-- math spans containing prose")
        n_prose = 0
        for a, b in spans:
            inner = text[a + 1:b]
            w = prose_words(inner)
            if w:
                n_prose += 1
                lines.append("   @%-6d words=%s" % (a, w[:8]))
                lines.append("      span : %r" % text[a:min(b + 1, a + 240)])
                lines.append("      after: %r" % text[b + 1:b + 121])
        lines.append("   prose-in-math spans: %d" % n_prose)

        # ---- attribute: what did 48314 do to the pre-damage blob?
        kind, ref = BLOBS[name]
        base = git_blob(kind, ref)
        hits = list(DAMAGE.finditer(base))
        lines.append("-- msg-48314 regex fired %d time(s) on the pre-damage blob %s"
                     % (len(hits), ref))
        for m in hits:
            lines.append("   @%-6d %r  ->  %r"
                         % (m.start(), m.group(0)[:120], m.group(0)[:120]))
        lines.append("")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    print("\n".join(lines))
    print("\nwrote", OUT)


if __name__ == "__main__":
    sys.exit(main())
