#!/usr/bin/env python3
"""For each odd-dollar paragraph in the report, find the authored pre-damage
text in the Hermes session DB and show the difference.

The Hermes session DB (<hermes-state-store>) stores
every message this session produced, including the exact strings written to
report/*.tex.  Messages dated BEFORE 2026-10-24 04:08:49 (the damage
operation) contain the authored, pre-damage form.
"""
import os
import re
import sqlite3
import sys
import datetime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REP = os.path.join(ROOT, "report")
DB = r"<hermes-state-store>"
CUTOFF = "2026-10-24T04:08:49"

FILES = ["ex1_hfilter.tex", "ex2_chamber.tex", "ex3_dep.tex", "ex4_fff.tex"]


def unescaped_dollars(text):
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


def para_spans(text):
    out, start = [], 0
    for m in re.finditer(r"\n[ \t]*\n", text):
        out.append((start, m.start()))
        start = m.end()
    out.append((start, len(text)))
    return out


def norm(s):
    """Normalise for comparison: collapse all whitespace runs to one space."""
    return re.sub(r"\s+", " ", s).strip()


def main():
    con = sqlite3.connect(DB)
    cur = con.cursor()
    # collect every message body
    rows = cur.execute(
        "SELECT id, timestamp, content FROM messages WHERE content IS NOT NULL "
        "ORDER BY id"
    ).fetchall()
    print("messages in db: %d" % len(rows))

    # index by normalised paragraph text
    for name in FILES:
        path = os.path.join(REP, name)
        text = open(path, encoding="utf-8", newline="").read()
        pos = unescaped_dollars(text)
        print("\n" + "=" * 78)
        print("FILE", name)
        for a, b in para_spans(text):
            k = len([p for p in pos if a <= p < b])
            if k % 2 == 0:
                continue
            para = text[a:b]
            print("\n  --- ODD PARA @%d  $=%d" % (a, k))
            # show the dollar positions relative to the paragraph
            rel = [p - a for p in pos if a <= p < b]
            print("      dollar offsets:", rel)
            # mark dollars inline
            marked = para
            for r in reversed(rel):
                marked = marked[:r] + "¤" + marked[r + 1:]
            print("      MARKED: %r" % marked[:600])

            # look for this paragraph (or its first 120 chars) in the DB
            key = norm(para)[:120]
            cands = []
            for mid, ts, content in rows:
                if content is None:
                    continue
                if key[:60] and key[:60] in norm(content):
                    stamp = datetime.datetime.fromtimestamp(ts).isoformat()
                    cands.append((mid, stamp, content))
            print("      DB candidates: %d" % len(cands))
            for mid, stamp, content in cands[:6]:
                tag = "PRE " if stamp < CUTOFF else "POST"
                # extract the matching region from the message
                cn = norm(content)
                i = cn.find(key[:60])
                seg = cn[i:i + 420] if i >= 0 else cn[:200]
                print("      %s msg %s  %s" % (tag, mid, stamp))
                print("          %r" % seg)
    con.close()


if __name__ == "__main__":
    sys.exit(main())
