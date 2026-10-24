#!/usr/bin/env python
"""
Compare the COMSOL H-filter concentration profile with the analytical
Eq.(1) series solution.

Reads the COMSOL-exported cut-line profile (x = L, transverse coordinate y)
and compares it with

    c_Eq1(y,t) = c0/2 - sum_n 2c0/((2n-1)pi) sin((2n-1)pi y/w) exp(-((2n-1)pi/w)^2 D t)

mirrored to the COMSOL inlet convention (sugar in the UPPER half, i.e.
c_comsol(y) = c_Eq1(-y,t)) and evaluated at the residence time t_res.

Usage:
    python scripts/ex1_comsol_compare.py [--profile <file>] [--out <file>]
"""
import argparse
import math
import os
import sys

# ----------------------------------------------------------------------------
# parameters (must match comsol/Ex1_HFilter.java)
# ----------------------------------------------------------------------------
W = 100e-6          # channel width [m]
H = 5e-6            # channel height [m]
L = 6e-3            # channel length [m]
D_SUGAR = 2.0e-10   # sugar diffusion coefficient [m^2/s]
C0 = 1.0            # inlet sugar concentration [mol/m^3]
Q_IN = 1.666667e-12  # 0.1 uL/min [m^3/s]
T_RES = L * W * H / Q_IN     # residence time [s]
N_SERIES = 2000


def c_eq1(y, t, w=W, D=D_SUGAR, c0=C0, n=N_SERIES):
    """Analytical Eq.(1): sugar in the LOWER half (y<0)."""
    s = 0.5 * c0
    for n_ in range(1, n + 1):
        m = 2 * n_ - 1
        s -= 2.0 * c0 / (m * math.pi) * math.sin(m * math.pi * y / w) \
             * math.exp(-((m * math.pi / w) ** 2) * D * t)
    return s


def c_comsol(y, t, **kw):
    """COMSOL convention: sugar in the UPPER half -> mirror of Eq.(1)."""
    return c_eq1(-y, t, **kw)


def read_comsol_profile(path):
    """Read a COMSOL cut-line export: whitespace separated x y c (metres, mol/m^3)."""
    xs, ys, cs = [], [], []
    with open(path) as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("%") or line.startswith("#"):
                continue
            parts = line.replace(",", " ").split()
            if len(parts) < 3:
                continue
            try:
                x = float(parts[0]); y = float(parts[1]); c = float(parts[2])
            except ValueError:
                continue
            xs.append(x); ys.append(y); cs.append(c)
    return xs, ys, cs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--profile", default=None, help="COMSOL cut-line export")
    ap.add_argument("--out", default=None, help="write a comparison table here")
    args = ap.parse_args()

    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.dirname(here)
    profile = args.profile or os.path.join(root, "data", "ex1_outlet_profile.txt")

    print(f"residence time t_res = L*w*h/Q = {T_RES:.6f} s")
    print(f"w = {W*1e6:.1f} um, h = {H*1e6:.1f} um, L = {L*1e3:.1f} mm")
    print(f"D = {D_SUGAR:.3e} m^2/s, c0 = {C0} mol/m^3")

    if not os.path.exists(profile):
        print(f"\nprofile file not found: {profile}")
        print("run the COMSOL model first (it writes the export), then re-run this script")
        # still print the analytical profile for reference
        print("\nanalytical profile (COMSOL convention) at t_res:")
        for y in [-50e-6, -25e-6, 0.0, 25e-6, 50e-6]:
            print(f"  y = {y*1e6:+6.1f} um   c = {c_comsol(y, T_RES):.6f}")
        return 1

    xs, ys, cs = read_comsol_profile(profile)
    print(f"\nread {len(ys)} points from {profile}")

    rows = []
    max_err = 0.0
    max_y = 0.0
    for x, y, c in zip(xs, ys, cs):
        ca = c_comsol(y, T_RES)
        err = c - ca
        rows.append((x, y, c, ca, err))
        if abs(err) > max_err:
            max_err = abs(err); max_y = y

    print(f"\n{'y [um]':>10} {'c_sim [mol/m^3]':>18} {'c_analytical':>14} {'diff':>12}")
    for x, y, c, ca, err in rows:
        print(f"{y*1e6:10.2f} {c:18.8f} {ca:14.8f} {err:12.2e}")
    print(f"\nmax |sim - analytical| over the outlet profile = {max_err:.6e} "
          f"mol/m^3 (at y = {max_y*1e6:+.2f} um)")
    print(f"max |sim - analytical| relative to c0 = {max_err/C0:.4%}")

    if args.out:
        with open(args.out, "w") as fh:
            fh.write("# x [m]  y [m]  c_comsol [mol/m^3]  c_analytical [mol/m^3]  diff\n")
            for x, y, c, ca, err in rows:
                fh.write(f"{x:.9e} {y:.9e} {c:.8f} {ca:.8f} {err:.3e}\n")
        print(f"comparison table written to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
