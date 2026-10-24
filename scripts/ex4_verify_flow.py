"""
Verify the COMSOL flow field against the square-duct (3D Poiseuille) profile.

The channel is 100 um (y) x 100 um (z) with flow along x at
U_avg = Q/(W*H) = 27.778 um/s.  For a square duct the fully developed
profile is NOT the 1.5*U_avg*(1-(2z/H-1)^2) parabola of a 2D channel (that
is the assignment's own simplifying assumption, and it ignores the y
profile).  The exact square-duct solution is the series

    u(y,z) = (16 U_avg / pi^5) * sum_{n odd} sin(n pi y/W)/n^3 *
             [ 1 - cos(n pi z/H) ] / (1 + n^2)   (H = W)

which is what this script checks COMSOL's flow against.

The transverse velocity is zero, so the DEP drift is purely in x.
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np

WORK = Path(r"<project>")
sys.path.insert(0, str(WORK / "scripts"))

Q_ULH = 1.0
W = H = 100e-6
A = W * H
U_AVG = (Q_ULH * 1e-9 / 3600.0) / A


def square_duct_u(y, z, W=W, H=H, u_avg=U_AVG, n_max=401):
    """Fully developed square-duct axial velocity [m/s]."""
    y = np.asarray(y, dtype=float)
    z = np.asarray(z, dtype=float)
    shape = np.broadcast(y, z).shape
    u = np.zeros(shape)
    for n in range(1, n_max + 1, 2):
        u += (np.sin(n * np.pi * y / W) / n**3) * (1.0 - np.cos(n * np.pi * z / H))
    return 16.0 * u_avg / math.pi**5 * u


def main():
    print("=" * 78)
    print("FLOW VERIFICATION: COMSOL particle drift vs square-duct theory")
    print("=" * 78)
    print(f"  Q = {Q_ULH} uL/h, A = {A*1e12:.1f} um^2 -> U_avg = {U_AVG*1e6:.4f} um/s")

    # --- COMSOL measured drift of particle 0 (y0 = 5 um), from the production run
    from ex4_parse_comsol import load_particles
    p = load_particles()
    t = p["t"]
    x, y, z = p["x"], p["y"], p["z"]

    print()
    print("  COMSOL: instantaneous dx/dt for particle 0 (y0 = 5 um) vs the")
    print("  square-duct profile evaluated at the particle's own (y,z):")
    print()
    print("     t[s]   x[um]    y[um]    z[um]   dx/dt[um/s]   u_theory[um/s]  ratio")
    ok = 0
    ratios = []
    for k in list(range(1, 12)) + list(range(12, 34, 2)):
        if np.isnan(x[0, k]) or np.isnan(x[0, k - 1]):
            continue
        dt = t[k] - t[k - 1]
        dxdt = (x[0, k] - x[0, k - 1]) / dt
        ut = float(square_duct_u(y[0, k], z[0, k]))
        ratios.append(dxdt / ut)
        print(f"    {t[k]:5.1f} {x[0,k]:8.2f} {y[0,k]:8.3f} {z[0,k]:8.3f} "
              f"{dxdt*1e6:12.3f} {ut*1e6:14.3f} {dxdt/ut:8.4f}")
        ok += 1
    ratios = np.array(ratios)
    print()
    print(f"  ratio COMSOL/theory over {ok} samples: "
          f"mean = {ratios.mean():.4f}, min = {ratios.min():.4f}, max = {ratios.max():.4f}")

    # --- compare with the assignment's 2D parabolic profile (z only)
    print()
    print("  The assignment's own estimate uses the 2D profile")
    print("      u(z) = 1.5 U_avg (1 - (2z/H - 1)^2)   (ignores y entirely)")
    print("  COMSOL's particle 0 sits at y ~ 4.7 um, i.e. 5 um from the wall,")
    print("  where the true 3D profile is much slower than the 2D one:")
    for k in [1, 6, 13, 20]:
        if np.isnan(x[0, k]):
            continue
        dt = t[k] - t[k - 1]
        dxdt = (x[0, k] - x[0, k - 1]) / dt
        u2d = 1.5 * U_AVG * (1.0 - (2.0 * z[0, k] / H - 1.0) ** 2)
        u3d = float(square_duct_u(y[0, k], z[0, k]))
        print(f"    t={t[k]:5.1f} z={z[0,k]:7.3f} um : COMSOL {dxdt*1e6:7.3f} um/s | "
              f"2D parabola {u2d*1e6:7.3f} | 3D square duct {u3d*1e6:7.3f} um/s")


if __name__ == "__main__":
    main()
