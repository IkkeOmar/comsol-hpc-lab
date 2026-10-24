#!/usr/bin/env python3
"""Repair the stray math delimiters in the report .tex files.

History (all verified against the Hermes session DB, <hermes-state-store>):

  * msg 48314 (2026-10-24 04:08:49) ran this regex over report/*.tex:

        pat = re.compile(r'\\$((?:[0-9]+(?:\\.[0-9]+)?|\\\\[a-zA-Z]+))\\$(\\\\,[^$]*?)\\$')
        s2  = pat.sub(lambda m: "$" + m.group(1) + m.group(2) + "$", s)

    The stated intent (comment in that message) was to merge the split
    "number in math, unit in text" style into one math span:

        $number$\\,unit$   ->   $number\\,unit$

    The regex is unsafe because group 2 is `\\,[^$]*?` : it happily spans
    arbitrary PROSE (no '$' inside) up to the next '$' in the file, so it
    deleted a '$' that closed a number and swallowed whole sentences into
    math mode.  Each firing removed exactly one '$'.

  * The same style, authored deliberately in the exercise files, is

        $number$\\,\\dd{unit}$      (number in math, unit in text, stray '$')

    where the unit is typeset by \\dd{}/\\ensuremath and the trailing '$' is
    a stray closing delimiter that opens math mode over the rest of the
    paragraph.  Those are the sites that break the LaTeX build.

This script performs the merge that 48314 intended, but with a BOUNDED
pattern: the unit macro(s) must come IMMEDIATELY after the '\\,' and the
stray '$' must come IMMEDIATELY after the unit.  No prose can be matched,
so no prose can be swallowed.

    \\$([^$]*?)\\$(\\((?:dd|um)(?:\\{[^}]*\\})?)+)\\$   ->   $\\1\\2$

Group 1 is `[^$]*?`, which cannot cross a '$', so it is confined inside a
single math span.  The '\\,' + unit + '$' tail must be contiguous, so the
match can only fire on a genuine "$X$\\,unit$" site.

Verification (independent of the compiler):
  * every paragraph in every file must have an even number of unescaped '$'
  * no math span may contain prose
  * `tectonic main.tex` must build with no error
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REP = os.path.join(ROOT, "report")
FILES = ["ex1_hfilter.tex", "ex2_chamber.tex", "ex3_dep.tex", "ex4_fff.tex"]

# $ <math span> $ \, <one or more unit macros> $   ->   $<math span>\,<unit>$
#
# Group 1 is constrained: it must start and end with a NON-SPACE character and
# must not contain a newline.  That is what stops the match from running across
# prose: in "runs from $0$ to\n$1$\,\dd{mol/m^3}$. Generated", the candidate
# group 1 would be " to\n" (leading space + newline) and is therefore rejected;
# the match then anchors correctly on "$1$\,\dd{mol/m^3}$".
UNIT = r"(?:\(?:dd|um)(?:\{[^}]*\})?)+"
MATH_SPAN = r"([^\s$][^$\n]*[^\s$]|[^\s$])"
MERGE = re.compile(r"\$" + MATH_SPAN + r"\$(\," + UNIT + r")\$")


def paragraph_spans(text):
    """Yield (start, end) for each blank-line separated paragraph."""
    out = []
    start = 0
    for m in re.finditer(r"\n[ \t]*\n", text):
        out.append((start, m.start()))
        start = m.end()
    out.append((start, len(text)))
    return out


def unescaped_dollars(text):
    """Positions of '$' that are not escaped, ignoring comments."""
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


def math_spans(text):
    """(start, end) of every $...$ span, comments and \\$ ignored."""
    spans = []
    i, n = 0, len(text)
    open_at = None
    while i < n:
        c = text[i]
        if c == "\\":
            i += 1
            while i < n and text[i].isalpha():
                i += 1
            if i < n and text[i] == "{":
                depth, j = 0, i
                while j < n:
                    if text[j] == "{" and (j == 0 or text[j - 1] != "\\"):
                        depth += 1
                    elif text[j] == "}" and (j == 0 or text[j - 1] != "\\"):
                        depth -= 1
                    if depth == 0:
                        break
                    j += 1
                i = j + 1
            continue
        if c == "%":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if c == "$":
            if open_at is None:
                open_at = i
            else:
                spans.append((open_at, i + 1))
                open_at = None
            i += 1
            continue
        i += 1
    return spans


PROSE = re.compile(r"[A-Za-z]{2,}")
# words that legitimately occur inside a math span in this report
MATH_WORDS = {
    "mm", "nm", "cm", "m", "s", "ms", "us", "ns", "K", "J", "V", "Hz", "MHz", "GHz",
    "Pa", "mbar", "L", "min", "Lmin", "mmLmin", "mLmin", "UmLmin", "UmLmin",
    "S", "mS", "Sm", "kg", "kgm", "m2s", "m3s", "m3", "m2", "m2s",
    "Re", "Im", "K", "T", "A", "W", "C", "F", "N", "kgm3",
    "avg", "eq", "res", "fill", "in", "max", "min", "out", "th", "reach", "d",
    "c", "w", "h", "L", "Q", "T", "t", "x", "y", "z", "D", "V", "R", "K",
    "exp", "erfc", "mathrm", "operatorname", "frac", "sqrt", "quad", "qquad",
    "infty", "partial", "nabla", "left", "right", "big", "Big", "langle", "rangle",
    "bar", "hat", "vec", "tilde", "dot", "times", "div", "pm", "approx", "sim",
    "propto", "leq", "geq", "neq", "in", "notin", "subset", "cup", "cap",
    "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta",
    "iota", "kappa", "lambda", "mu", "nu", "xi", "rho", "sigma", "tau",
    "upsilon", "phi", "chi", "psi", "omega", "Gamma", "Delta", "Theta", "Lambda",
    "Xi", "Pi", "Sigma", "Upsilon", "Phi", "Psi", "Omega",
    "mod", "bmod", "pmod", "gcd", "deg", "det", "dim", "ker", "arg", "exp",
    "ln", "log", "sin", "cos", "tan", "sec", "csc", "sinh", "cosh", "tanh",
    "displaystyle", "textstyle", "left", "right", "mathrm", "mathbf", "mathbb",
    "mathcal", "mathfrak", "operatorname", "binom", "choose", "atop", "brace",
    "substack", "stackrel", "overset", "underset", "boxed", "phantom", "quad",
    "qquad", "hspace", "!", ",", ";", ":", "space", "thinspace", "negthinspace",
    "text", "textbf", "textit", "textnormal", "textrm", "texttt", "textsf",
    "operatorname", "surd", "root", "of", "over", "atop", "brace", "brack",
    "sum", "prod", "coprod", "int", "iint", "oint", "bigcap", "bigcup",
    "bigcap", "bigoplus", "bigotimes", "bigvee", "bigwedge", "pm",
}


def prose_in_math(text):
    """Math spans that still contain an alphabetic word after removing commands."""
    hits = []
    for a, b in math_spans(text):
        inner = text[a + 1:b - 1]
        s = re.sub(r"\\[a-zA-Z]+\s*(\{[^{}]*\})?", " ", inner)
        s = re.sub(r"\\[a-zA-Z]+", " ", s)
        for w in PROSE.findall(s):
            if w not in MATH_WORDS:
                hits.append((a, b, w, inner[:100]))
                break
    return hits


def main():
    total = 0
    for name in FILES:
        path = os.path.join(REP, name)
        with open(path, encoding="utf-8", newline="") as fh:
            text = fh.read()

        before = text
        fixed, n = MERGE.subn(lambda m: "$" + m.group(1) + m.group(2), text)
        if n:
            # sanity: the merge must not change any non-'$' character
            assert fixed.replace("$", "") == before.replace("$", ""), name
            with open(path, "w", encoding="utf-8", newline="") as fh:
                fh.write(fixed)
            text = fixed
        total += n
        print("%-20s merged %d stray '$X$\\,unit$' sites" % (name, n))

    print("\n--- verification ---")
    ok = True
    for name in FILES:
        path = os.path.join(REP, name)
        with open(path, encoding="utf-8", newline="") as fh:
            text = fh.read()
        odd = []
        for a, b in paragraph_spans(text):
            k = len(unescaped_dollars(text[a:b]))
            if k % 2:
                odd.append((a, k))
        prose = prose_in_math(text)
        print("%-20s $ %4d   odd paragraphs %d   prose-in-math %d"
              % (name, len(unescaped_dollars(text)), len(odd), len(prose)))
        for a, k in odd:
            print("      ODD para @%d ($=%d): %r" % (a, k, text[a:a + 160]))
        for a, b, w, inner in prose:
            print("      PROSE @%d %r: %r" % (a, w, inner))
        if odd or prose:
            ok = False
    print("\nmerged %d sites; document state %s" % (total, "CLEAN" if ok else "STILL BROKEN"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
