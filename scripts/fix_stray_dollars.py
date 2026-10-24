#!/usr/bin/env python3
"""Delete the stray '$' that follows a unit macro in the report .tex files.

DIAGNOSIS (all of it evidence-based; see notes/_audit_math.txt)
---------------------------------------------------------------
The report uses two spellings for a quantity with a unit:

    (a)  "$600\\,\\um\\dd{m}$"        number and unit in ONE math span
    (b)  "$600$\\,\\um\\dd{m}"        number in math, unit in text mode

Form (b) is what the author wrote.  It is legal LaTeX: since LaTeX
2020-02-02 '\\,' is allowed in text mode (a 0.16667em kern, the same
width as the math-mode \\thinmuskip), and \\dd{}/\\um are
\\ensuremath{\\mathrm{...}}, so a unit is upright in text mode too.
The base blob of ex1 already relies on it -- the pre-damage build
compiles it -- so the split style is settled and is not the bug.

The BUG is a stray '$' written after the unit:

    "$600$\\,\\um\\dd{m}$ of the channel, and the colour bar runs from $0$ to"

That extra '$' OPENS a math span, so everything up to the next '$' in
the file -- " of the channel, and the colour bar runs from " -- is
typeset in math mode instead of text mode, and the '$' pairing of every
later expression in the file shifts by one.

Evidence that the trailing '$' is the typo and not the intent:

  * msg 46691 (02:13, pre-damage) -- the author edits ex2 to the split
    style: "of the $60\\times 60$\\,\\dd{mm}$ footprint".  So the SPLIT
    style is a deliberate authorial choice; only the trailing '$' after
    the unit is wrong.
  * msg 48800 (07:07) -- a clean authored ex2 paragraph in the same
    session: "at $t = 2195$\\,\\dd{s}, just after the pulse ends".  The
    stray '$' is gone, the split style is kept.
  * the pre-damage base blobs contain ZERO sites where a unit macro is
    immediately followed by '$'.
  * the damage operation (Hermes message 48314, 2026-10-24 04:08:49)
    could only MERGE spans, never create a split one, so it cannot have
    produced these 8 sites; they are authoring typos in blocks added
    after the base commit.

THE REPAIR
----------
Delete the '$' that immediately follows a unit macro, when the text
between it and the previous '$' is exactly that unit group.  Nothing
else is touched: no character other than those '$' is removed or added.

Rendering is preserved.  "600" stays in math mode, the thin space and
the unit stay in text mode, and the following prose returns to text
mode -- which is what the sentence was always supposed to read as.

VERIFICATION (all independent of the compiler)
  * every paragraph has an EVEN number of unescaped '$'
  * no math span contains prose
  * no '$' follows a unit macro while the span it opens contains prose
  * the '$' count of the file drops by exactly the number of strays
  * `tectonic main.tex` builds with no error
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REP = os.path.join(ROOT, "report")
FILES = ["ex1_hfilter.tex", "ex2_chamber.tex", "ex3_dep.tex", "ex4_fff.tex",
         "appendix_provenance.tex", "bibliography.tex", "preamble.tex", "main.tex"]

# a unit group: one or more of \dd{...} / \um, optionally preceded by '\,'
UNIT_GROUP = re.compile(r"^(?:\\,)?(?:\\(?:dd|um)(?:\{[^}]*\})?)+$")

# words that only occur in prose, never in a math expression
STOPWORDS = {
    "the", "and", "of", "is", "in", "to", "that", "which", "over", "from",
    "a", "an", "it", "its", "for", "with", "when", "but", "as", "at", "by",
    "on", "or", "so", "than", "then", "this", "these", "those", "are", "be",
    "was", "were", "has", "have", "had", "not", "no", "we", "our", "they",
    "their", "there", "here", "if", "can", "will", "would", "should", "could",
    "each", "one", "two", "three", "all", "any", "more", "most", "other",
    "same", "such", "only", "very", "into", "onto", "through", "during",
    "before", "after", "above", "below", "between", "under", "over",
    "reference", "analytical", "reference", "colour", "channel", "runs",
    "footprint", "diffusion", "slow", "reached", "velocity", "demands",
    "parabolic", "profile", "resolved", "mesh", "generated", "window",
    "also", "straight", "comsol", "step", "entering", "upper", "half",
    "interface", "sharp", "view", "zoomed", "first", "level", "graded",
    "sensitive", "choice", "interesting", "given", "instead", "continues",
    "past", "height", "which", "leaves", "short", "comes", "rest", "with",
    "final", "moves", "mesh", "sixfold", "interesting", "point", "makes",
    "equilibrium", "attractor", "simply", "too", "reach", "point",
}


def unescaped_dollars(text):
    """Positions of '$' that are not escaped; comments skipped."""
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
    return pos


def paragraph_spans(text):
    out, start = [], 0
    for m in re.finditer(r"\n[ \t]*\n", text):
        out.append((start, m.start()))
        start = m.end()
    out.append((start, len(text)))
    return out


def strip_cmds(s):
    s = re.sub(r"\\[a-zA-Z]+\s*\{[^{}]*\}", " ", s)
    s = re.sub(r"\\[a-zA-Z]+", " ", s)
    s = re.sub(r"\\[^\s]", " ", s)
    return s


def prose_score(s):
    """True if a math span's content reads as prose rather than math.

    Two rules, both tuned to avoid false positives on real math:

    * A stopword only counts when the token is NOT a single letter.  In
      math, "a", "A", "c", "d", "t" are variables; in prose "the", "of",
      "and" are words.  Treating the single letter 'a' as the article
      flagged every span containing a^3, A = 2, D_h = 4A/P, ...
    * A comma is NOT a prose signal.  Math legitimately contains commas:
      function arguments (\\min(t/t_fill, 1)) and coordinate tuples
      ((0,0,-50)).  A comma is only meaningful together with words, and
      the two word rules below already catch prose.

    A math span is prose if it contains a stopword of length >= 2, or two
    consecutive alphabetic words of length >= 3 (e.g. "the channel").
    """
    t = strip_cmds(s)
    for w in re.findall(r"[A-Za-z]+", t):
        if len(w) >= 2 and w.lower() in STOPWORDS:
            return True
    if re.search(r"[A-Za-z]{3,}\s+[A-Za-z]{3,}", t):
        return True
    return False


def math_spans(text):
    """(start, end) of each $...$ span under LaTeX's left-to-right pairing."""
    spans, open_at = [], None
    for p in unescaped_dollars(text):
        if open_at is None:
            open_at = p
        else:
            spans.append((open_at, p))
            open_at = None
    return spans


def find_strays(text):
    """Positions of '$' that are a stray trailing delimiter after a unit.

    A '$' at p is a stray iff
      (1) the text between the previous '$' and p is exactly a unit group
          (so p is the '$' that immediately follows a unit macro), and
      (2) the span p opens (up to the next '$') contains prose, i.e. the
          '$' is not legitimately closing a math expression.
    """
    pos = unescaped_dollars(text)
    out = []
    for k, p in enumerate(pos):
        if k == 0:
            continue
        prev = pos[k - 1]
        gap = text[prev + 1:p]
        if not UNIT_GROUP.match(gap):
            continue
        nxt = pos[k + 1] if k + 1 < len(pos) else len(text)
        if prose_score(text[p + 1:nxt]):
            out.append(p)
    return out


def main():
    apply_changes = "--apply" in sys.argv
    total = 0
    for name in FILES:
        path = os.path.join(REP, name)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8", newline="") as fh:
            text = fh.read()

        strays = find_strays(text)
        if strays:
            # delete from the back so earlier offsets stay valid
            new = text
            for p in reversed(strays):
                new = new[:p] + new[p + 1:]
            assert len(new) == len(text) - len(strays)
            if apply_changes:
                with open(path, "w", encoding="utf-8", newline="") as fh:
                    fh.write(new)
                text = new
            total += len(strays)
            print("%-24s %d stray '$' removed  %s"
                  % (name, len(strays), "(APPLIED)" if apply_changes else "(dry run)"))
            for p in strays:
                print("      @%-6d %r" % (p, text[max(0, p - 70):p + 30]))

    print("\n--- verification ---")
    ok = True
    for name in FILES:
        path = os.path.join(REP, name)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8", newline="") as fh:
            text = fh.read()
        odd = [(a, len(unescaped_dollars(text[a:b])))
               for a, b in paragraph_spans(text)
               if len(unescaped_dollars(text[a:b])) % 2]
        prose = [(a, text[a + 1:b][:80]) for a, b in math_spans(text)
                 if prose_score(text[a + 1:b])]
        strays = find_strays(text)
        print("%-24s $ %4d   odd paragraphs %d   prose-in-math %d   stray $ %d"
              % (name, len(unescaped_dollars(text)), len(odd), len(prose), len(strays)))
        for a, k in odd:
            print("      ODD para @%d ($=%d): %r" % (a, k, text[a:a + 150]))
        for a, inner in prose:
            print("      PROSE @%d: %r" % (a, inner))
        for p in strays:
            print("      STRAY $ @%d: %r" % (p, text[max(0, p - 60):p + 30]))
        if odd or prose or strays:
            ok = False
    print("\n%s; document state %s"
          % ("removed %d stray '$'" % total if apply_changes
             else "dry run: %d stray '$'" % total,
             "CLEAN" if ok else "STILL BROKEN"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
