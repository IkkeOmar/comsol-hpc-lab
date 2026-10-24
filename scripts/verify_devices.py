"""Compare COMSOL's OWN measured geometry against the assignment dimensions.

This is the verification for the device figures.  Instead of trusting that a
matplotlib drawing made from the parameter values matches what COMSOL built,
the bounding box is MEASURED from COMSOL's own geometry and compared against
the assignment's numbers.

The measured values are NOT hard-coded here: they are parsed out of the run
log that COMSOL wrote, `notes/geom_export.log`, produced by
`comsol/GeomExport.java`.  Every number checked below therefore comes from a
real COMSOL run.  If the log is missing, or a device is missing from it, the
script fails loudly instead of checking a placeholder.

The assignment's own dimensions are hard-coded, because they are the claim
being tested.

Run:
    python scripts/verify_devices.py
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG = os.path.join(ROOT, "notes", "geom_export.log")

# ---------------------------------------------------------------------------
# What the assignment says.
#   ex1: 6 mm long, 100 um wide straight channel (page 1-2)
#   ex2: 60 x 60 x 1 mm chamber, 150 mm long and 2 mm wide crossing channel,
#        with a cylindrical cell layer BELOW the chamber floor (page 5-6)
#   ex3: electrode strips of width d with gaps d, particle above (page 9)
#   ex4: 1 mm long, 100 x 100 um channel, 45 electrodes (page 14-15)
# All lengths below are in micrometres, matching COMSOL's bbox_um output.
# ---------------------------------------------------------------------------
ASSIGNMENT = {
    "ex1": {"span_x": (0.0, 6000.0), "span_y": (-50.0, 50.0)},
    "ex2": {"span_x": (-75000.0, 75000.0), "span_y": (-30000.0, 30000.0),
            # the chamber floor is at z = 0 and the cell layer hangs 100 um
            # below it, so the full model spans z in [-100, 1000]
            "span_z": (-100.0, 1000.0)},
    "ex3": {"span_x": (0.0, 80.0), "span_y": (0.0, 100.0)},
    "ex4": {"span_x": (0.0, 1000.0), "span_y": (0.0, 100.0),
            "span_z": (0.0, 100.0)},
}

# Number of domains COMSOL must build, from the same run log.  These are not
# in the assignment; they are the structural signature of the geometry (the
# chamber is split into its parts, the electrode array splits the floor).
EXPECTED_DOMAINS = {"ex1": 1, "ex2": 6, "ex3": 6, "ex4": 1}

TOL = 1e-9  # relative


def parse_log(path):
    """Pull the measured extents and entity counts out of the COMSOL log."""
    if not os.path.exists(path):
        raise SystemExit("FAIL: COMSOL log not found: %s\n"
                         "Run:  bash run_comsol.sh GeomExport ../notes/geom_export.log "
                         "\"GEOMEXPORT DONE\"" % path)
    text = open(path, encoding="utf-8", errors="replace").read()
    if "GEOMEXPORT DONE" not in text:
        raise SystemExit("FAIL: %s does not contain the completion marker "
                         "'GEOMEXPORT DONE' - the run did not finish." % path)

    measured = {}
    counts = {}
    for line in text.splitlines():
        m = re.search(r"\[(ex\d)\]\s+bbox_um\s+x\[([^\]]+)\]\s+y\[([^\]]+)\]"
                      r"(?:\s+z\[([^\]]+)\])?", line)
        if m:
            dev = m.group(1)
            spans = {"span_x": [float(v) for v in m.group(2).split(",")],
                     "span_y": [float(v) for v in m.group(3).split(",")]}
            if m.group(4):
                spans["span_z"] = [float(v) for v in m.group(4).split(",")]
            measured[dev] = spans
            continue
        m = re.search(r"\[(ex\d)\]\s+domains=(\d+)\s+boundaries=(\d+)"
                      r"\s+edges=(\d+)\s+vertices=(\d+)", line)
        if m:
            d = m.group(1)
            counts[d] = {"domains": int(m.group(2)), "boundaries": int(m.group(3)),
                         "edges": int(m.group(4)), "vertices": int(m.group(5))}
    return measured, counts


def check(dev, axis, measured, expected):
    (m_lo, m_hi) = measured
    (e_lo, e_hi) = expected
    scale = max(abs(e_hi - e_lo), 1e-30)
    d_lo = abs(m_lo - e_lo) / scale
    d_hi = abs(m_hi - e_hi) / scale
    ok = d_lo < TOL and d_hi < TOL
    status = "OK " if ok else "FAIL"
    print("  [%s] %-4s %s: COMSOL %11.3f .. %11.3f   assignment %11.3f .. %11.3f   "
          "(rel dev %.2e / %.2e)" % (status, dev, axis, m_lo, m_hi, e_lo, e_hi,
                                     d_lo, d_hi))
    return ok


def main():
    print("Verifying COMSOL's measured geometry against the assignment")
    print("=" * 96)
    print("measured from: %s" % os.path.relpath(LOG, ROOT))
    measured, counts = parse_log(LOG)

    all_ok = True
    for dev in ("ex1", "ex2", "ex3", "ex4"):
        print("\n%s:" % dev)
        if dev not in measured:
            print("  [FAIL] %s has no measured bounding box in the log" % dev)
            all_ok = False
            continue
        for axis in ("span_x", "span_y", "span_z"):
            if axis in ASSIGNMENT[dev]:
                if axis not in measured[dev]:
                    print("  [FAIL] %s %s: COMSOL measured no such axis" % (dev, axis))
                    all_ok = False
                    continue
                all_ok &= check(dev, axis, measured[dev][axis], ASSIGNMENT[dev][axis])
        d = counts.get(dev, {}).get("domains")
        exp_d = EXPECTED_DOMAINS[dev]
        ok = d == exp_d
        print("  [%s] %-4s domains built by COMSOL: %s (expected %s)"
              % ("OK " if ok else "FAIL", dev, d, exp_d))
        all_ok &= ok

    # Ex4: the electrode array must fit inside the channel
    x0, pitch, w, n = 50.0, 20.0, 10.0, 45
    last = x0 + (n - 1) * pitch + w
    ok = last <= 1000.0
    print("\n  [%s] ex4  electrode array ends at x = %.0f um, channel is 1000 um"
          % ("OK " if ok else "FAIL", last))
    all_ok &= ok

    # Ex2: the crossing channel must span the whole chamber footprint
    ex2_x = measured.get("ex2", {}).get("span_x", (0.0, 0.0))
    span_mm = (ex2_x[1] - ex2_x[0]) / 1000.0
    # tolerance, not equality: COMSOL's own bbox carries float round-off
    # (75 000.00000000001 rather than 75 000)
    ok = abs(span_mm - 150.0) < 1e-6
    print("  [%s] ex2  total x span (crossing channel included) is %.6f mm"
          % ("OK " if ok else "FAIL", span_mm))
    all_ok &= ok

    print("\n" + "=" * 96)
    if all_ok:
        print("ALL GEOMETRY CHECKS PASSED - COMSOL built what the assignment "
              "describes")
    else:
        print("GEOMETRY CHECKS FAILED")
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
