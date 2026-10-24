"""
Extract the vertical |E|^2 profile from COMSOL's own frequency-domain field
export (comsol/prod/Ex4_field_dset1.csv) and compare it with

  * the course/Ex-3 idealisation  |grad|E|^2| = (32/pi)(V0^2/d^3)exp(-pi y/d)
  * the equilibrium height implied by the analytical force balance

The export is a mesh-node export, so the nodes are scattered; the vertical
profile is therefore built by binning the nodes in (x, z) and taking the
mean of E2 in each bin.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np

WORK = Path(r"<project>")
sys.path.insert(0, str(WORK / "scripts"))
from ex4_parse_comsol import load_field_nodes, load_field_at_particles

NOTES = WORK / "notes"

# assignment / analytical parameters
D = 10e-6
V0 = 10.0
EL_X0 = 50e-6
P = 20e-6
EPS0 = 8.854187817e-12
EPS_R_M = 80.0
RHO_P = 1200.0
RHO_M = 1000.0
G = 9.80665
DP = 2.4e-6
A_P = DP / 2.0
RE_K = -0.08367


def f_buoy():
    return (4.0 / 3.0) * math.pi * A_P ** 3 * (RHO_P - RHO_M) * G


def course_grad_E2(y):
    return (32.0 / math.pi) * (V0 ** 2 / D ** 3) * np.exp(-math.pi * np.asarray(y) / D)


def dep_force_from_grad(grad_E2, re_k=RE_K):
    """|F_DEP| [N] from |grad|E|^2| using the peak-amplitude convention."""
    return math.pi * A_P ** 3 * EPS0 * EPS_R_M * abs(re_k) * np.asarray(grad_E2)


def equilibrium_from_profile(z, grad_E2):
    """Interpolate log|grad E^2| vs z and find where |F_DEP| = F_buoy."""
    fb = f_buoy()
    g = np.asarray(grad_E2)
    ok = np.isfinite(g) & (g > 0)
    z, g = np.asarray(z)[ok], g[ok]
    o = np.argsort(z)
    z, g = z[o], g[o]
    lg = np.log(g)
    z_eq = np.interp(np.log(fb / dep_force_from_grad(1.0)), lg[::-1], z[::-1])
    return float(z_eq)


def main():
    print("=" * 78)
    print("COMSOL FIELD EXTRACTED FROM THE FREQUENCY-DOMAIN SOLUTION")
    print("=" * 78)
    f = load_field_nodes()
    x, y, z, E2 = f["x"], f["y"], f["z"], f["E2"]
    print(f"  mesh nodes in the export: {x.size}")
    print(f"  x range: {x.min()*1e6:.2f} .. {x.max()*1e6:.2f} um")
    print(f"  y range: {y.min()*1e6:.2f} .. {y.max()*1e6:.2f} um")
    print(f"  z range: {z.min()*1e6:.2f} .. {z.max()*1e6:.2f} um")
    print(f"  max |E|^2 = {E2.max():.6e} (V/m)^2  -> |E|max = {math.sqrt(E2.max()):.6e} V/m")

    # restrict to the mid-plane y = 50 um (the field is y-invariant except
    # within ~10 um of the side walls)
    mid = np.abs(y - 50e-6) < 3e-6
    print(f"  nodes in the mid-plane band |y-50|<3 um: {mid.sum()}")

    out = {}

    def profile(xq, label, half_width=1.5e-6):
        sel = mid & (np.abs(x - xq) < half_width)
        if sel.sum() < 3:
            print(f"  {label}: only {sel.sum()} nodes, skipped")
            return None
        zz = z[sel] * 1e6
        ee = E2[sel]
        # bin in z
        bins = np.arange(0.0, 60.01, 0.5)
        idx = np.digitize(zz, bins)
        zc, gm, cnt = [], [], []
        for b in range(1, len(bins)):
            m = idx == b
            if m.sum() >= 1:
                zc.append(0.5 * (bins[b - 1] + bins[b]))
                gm.append(ee[m].mean())
                cnt.append(int(m.sum()))
        zc = np.array(zc)
        gm = np.array(gm)
        # d|E|^2/dz  (units (V/m)^2/m)
        g = np.gradient(gm, zc * 1e-6)
        print()
        print(f"  {label}  (x = {xq*1e6:.1f} um, {sel.sum()} nodes, {len(zc)} z-bins)")
        print("     z[um]   |E|^2 [(V/m)^2]    d|E|^2/dz [(V/m)^2/m]   "
              "course formula      ratio")
        rows = []
        for k in range(len(zc)):
            if zc[k] > 25 or k < 1:
                continue
            cf = float(course_grad_E2(zc[k] * 1e-6))
            r = g[k] / cf if cf != 0 else float("nan")
            print(f"   {zc[k]:7.2f}   {gm[k]:.6e}      {g[k]:+.6e}       "
                  f"{cf:+.6e}   {r:8.4f}")
            rows.append({"z_um": zc[k], "E2": gm[k], "dE2dz": g[k],
                         "course": cf, "ratio": r})
        return zc, gm, g, rows

    res = {}
    res["gap"] = profile(EL_X0 + 1.5 * D, "GAP CENTRE (between electrode 0 and 1)")
    res["elec"] = profile(EL_X0 + 0.5 * D, "ELECTRODE 0 CENTRE")

    print()
    print("=" * 78)
    print("EQUILIBRIUM HEIGHT IMPLIED BY COMSOL'S OWN FIELD")
    print("=" * 78)
    fb = f_buoy()
    print(f"  F_buoy (net weight, rho_p - rho_m = {RHO_P-RHO_M:.0f} kg/m^3) = {fb:.6e} N")
    print(f"  |F_DEP| = pi a^3 eps0 eps_r,m |ReK| |grad|E|^2| ,  a = {A_P*1e6:.2f} um,")
    print(f"           |ReK| = {abs(RE_K):.5f}")
    eqs = {}
    for key in ("gap", "elec"):
        if res[key] is None:
            continue
        zc, gm, g, rows = res[key]
        z_eq = equilibrium_from_profile(zc, g)
        eqs[key] = z_eq
        print(f"  from the {key} profile: y_eq = {z_eq*1e6:.3f} um above the electrode plane")
    # the course formula
    y_eq_course = (D / math.pi) * math.log(
        24 * EPS0 * EPS_R_M * abs(RE_K) * V0 ** 2 /
        (math.pi * D ** 3 * (RHO_P - RHO_M) * G))
    print(f"  from the COURSE formula:  y_eq = {y_eq_course*1e6:.3f} um")

    # also: what the course formula gives for the COMSOL equilibrium
    print()
    print("  For reference, the course formula's |grad|E|^2| at the COMSOL")
    print("  equilibrium height vs COMSOL's measured value at the same height:")
    if "gap" in eqs:
        zq = eqs["gap"] * 1e6
        zc, gm, g, rows = res["gap"]
        # interpolate COMSOL's dE2dz at zq
        gi = np.interp(zq, zc, g)
        cf = float(course_grad_E2(zq * 1e-6))
        print(f"    z = {zq:.2f} um : COMSOL {gi:.6e}   course {cf:.6e}   "
              f"ratio {gi/cf:.4f}")
        print(f"    -> the COMSOL field gradient is {gi/cf:.3f}x the course idealisation")
        print(f"    -> a {gi/cf:.3f}x larger |grad|E|^2| at the same height means the")
        print(f"       force balance is reached {math.log(gi/cf)/math.pi*D*1e6:.2f} um "
              f"LOWER than the course prediction")

    # decay rate of the COMSOL profile
    if res["gap"] is not None:
        zc, gm, g, rows = res["gap"]
        m = (zc > 2) & (zc < 12) & (g > 0)
        if m.sum() > 3:
            A = np.vstack([zc[m], np.ones(m.sum())]).T
            slope = np.linalg.lstsq(A, np.log(g[m]), rcond=None)[0]
            rate = -slope / 1e-6
            print()
            print(f"  COMSOL |grad|E|^2| decay rate (gap centre, 2-12 um): {rate:.4g} /m")
            print(f"    course formula decay rate pi/d                 = {math.pi/D:.4g} /m")
            print(f"    1D period-{2*P*1e6:.0f}um array rate 2pi/L          = "
                  f"{2*math.pi/(2*P):.4g} /m")

    out = {"y_eq_course_um": y_eq_course * 1e6,
           "y_eq_comsol_um": {k: v * 1e6 for k, v in eqs.items()},
           "f_buoy_N": fb,
           "gap_profile": res["gap"][3] if res["gap"] else None,
           "elec_profile": res["elec"][3] if res["elec"] else None}
    (NOTES / "ex4_comsol_field_profile.json").write_text(
        json.dumps(out, indent=2, default=float), encoding="utf-8")
    print()
    print("  wrote " + str(NOTES / "ex4_comsol_field_profile.json"))


if __name__ == "__main__":
    main()
