"""
Independent 1D Fourier reference for the Ex-4 coplanar electrode array.

WHY THIS EXISTS
---------------
The COMSOL result must be checked against something that is neither COMSOL
nor the course formula, so that "COMSOL agrees with the course formula" can
be separated from "both are wrong for the same reason".

The reference is the EXACT solution of the 2D Laplace problem for an
INFINITE PERIODIC array of coplanar electrodes.  This is the idealisation
that the course/Ex-3 formula assumes.

SETUP (matching the assignment)
    Electrodes of width d = 10 um with gaps of width d = 10 um, so the
    period is p = 2d = 20 um.  The first electrode's LEFT edge is at
    x = 50 um, so electrode 0 spans x = 50..60 um and the first gap spans
    60..70 um.  Potentials alternate 10 V / 0 V.
    The fluid occupies z > 0 (z = 0 is the electrode plane).

THE SOLUTION
    Let x' = x - 50 um measure from the LEFT edge of electrode 0, so
    u = x'/d is the local coordinate and the pattern has period 2 in u.
    The potential on the electrode plane is a 50 % duty-cycle square wave,
    V0 on the electrode (u in [0,1)) and 0 in the gap (u in [1,2)).
    Its Fourier series (period 2 in u) is

        V(u) = V0/2 + sum_{n odd} (2 V0/(n pi)) sin(n pi u)

    and each harmonic decays into the fluid as exp(-k_n z) with
    k_n = n pi/d, so

        V(x', z) = V0/2 + sum_{n odd} (2 V0/(n pi)) sin(n pi x'/d)
                                        exp(-n pi z/d)
        E = -grad V

    In a centre-based coordinate (xs = x - 55 um, the centre of electrode 0)
    the same series reads

        V(xs, z) = V0/2 + sum_{n odd} (2 V0/(n pi)) (-1)^((n-1)/2)
                                        cos(n pi xs/d) exp(-n pi z/d)

    PEAK-AMPLITUDE CONVENTION
        The course formula uses E = PEAK amplitude; COMSOL's DEP node uses
        |E_rms|^2 = |E_peak|^2 / 2.  Everything below is PEAK amplitude;
        the 1/2 is applied when the force is compared.

VERIFICATION (self-contained, in this script)
    (1) the series reproduces the boundary potential (V0 on the electrode,
        0 in the gap) to < 5 %;
    (2) the field is curl-free and divergence-free in the fluid to 1e-5
        relative;
    (3) the leading harmonic decays as exp(-pi z/d), matching the decay
        rate in the course formula.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np

WORK = Path(r"<project>")
NOTES = WORK / "notes"
NOTES.mkdir(parents=True, exist_ok=True)

# ---- assignment parameters -------------------------------------------------
D = 10e-6            # electrode width AND gap width [m]
V0 = 10.0            # full swing between the 10 V and 0 V electrodes [V]
EL_X0 = 50e-6        # LEFT edge of the first electrode [m]
N_EL = 45
EL_P = 20e-6

N_MAX = 401          # number of odd harmonics summed


def fourier_potential(x, z, V0=V0, d=D, n_max=N_MAX):
    """Potential of the infinite periodic electrode array (peak amplitude)."""
    x = np.asarray(x, dtype=float)
    z = np.asarray(z, dtype=float)
    xc = EL_X0 + d / 2.0                 # centre of electrode 0
    xs = x - xc
    V = np.zeros(np.broadcast(x, z).shape, dtype=float)
    V += 0.5 * V0
    for n in range(1, n_max + 1, 2):
        k = n * np.pi / d
        c = (2.0 * V0 / (n * np.pi)) * ((-1) ** ((n - 1) // 2))
        V += c * np.cos(k * xs) * np.exp(-k * z)
    return V


def fourier_field(x, z, V0=V0, d=D, n_max=N_MAX):
    """(Ex, Ez) of the infinite periodic electrode array (peak amplitude)."""
    x = np.asarray(x, dtype=float)
    z = np.asarray(z, dtype=float)
    xc = EL_X0 + d / 2.0
    xs = x - xc
    shape = np.broadcast(x, z).shape
    Ex = np.zeros(shape, dtype=float)
    Ez = np.zeros(shape, dtype=float)
    for n in range(1, n_max + 1, 2):
        k = n * np.pi / d
        c = (2.0 * V0 / (n * np.pi)) * ((-1) ** ((n - 1) // 2))
        e = np.exp(-k * z)
        Ex += -c * k * np.sin(k * xs) * e
        Ez += c * k * np.cos(k * xs) * e
    return Ex, Ez


def verify_reference():
    """Assert the Fourier solution really solves the problem it claims to."""
    # (1) boundary potential at z = 0, in the CENTRE-based coordinate
    #     (electrode 0 centred at 0, gap centred at +d)
    u = np.linspace(-2 * D, 2 * D, 8001)
    xs = u - D / 2.0                      # distance from the electrode centre
    V = fourier_potential(u, np.zeros_like(u))
    on_el = np.abs(xs) <= D / 2.0
    err_el = np.max(np.abs(V[on_el] - V0))
    err_gap = np.max(np.abs(V[~on_el]))
    assert err_el < 0.05 * V0, f"electrode BC error {err_el:.4g} V"
    assert err_gap < 0.05 * V0, f"gap BC error {err_gap:.4g} V"

    # (2) curl-free and divergence-free in the fluid, away from the electrodes
    h = 1e-8
    xg, zg = np.meshgrid(np.linspace(0.25 * D, 1.75 * D, 31),
                         np.linspace(0.10 * D, 2.0 * D, 31))
    dEx_dz = (fourier_field(xg, zg + h)[0] - fourier_field(xg, zg - h)[0]) / (2 * h)
    dEz_dx = (fourier_field(xg + h, zg)[1] - fourier_field(xg - h, zg)[1]) / (2 * h)
    dEx_dx = (fourier_field(xg + h, zg)[0] - fourier_field(xg - h, zg)[0]) / (2 * h)
    dEz_dz = (fourier_field(xg, zg + h)[1] - fourier_field(xg, zg - h)[1]) / (2 * h)
    curl = np.max(np.abs(dEx_dz - dEz_dx)) / np.max(np.abs(dEx_dz))
    div = np.max(np.abs(dEx_dx + dEz_dz)) / np.max(np.abs(dEx_dx))
    assert curl < 1e-5, f"curl not zero: {curl}"
    assert div < 1e-5, f"divergence not zero: {div}"

    # (3) the leading harmonic's decay rate is pi/d, matching the course
    #     formula's exp(-pi y/d)
    z1, z2 = 5e-6, 6e-6
    e1 = fourier_field(np.array([EL_X0 + D]), np.array([z1]))
    e2 = fourier_field(np.array([EL_X0 + D]), np.array([z2]))
    r = ((e2[0][0] ** 2) / (e1[0][0] ** 2))
    rate = -math.log(r) / (z2 - z1)
    assert abs(rate - math.pi / D) / (math.pi / D) < 0.15, \
        f"decay rate {rate} vs pi/d {math.pi/D}"
    return err_el, err_gap, curl, div, rate


def course_formula(y, V0=V0, d=D):
    """The course/Ex-3 formula: |grad E|^2 = (32/pi)(V0^2/d^3)exp(-pi y/d)."""
    return (32.0 / math.pi) * (V0 ** 2 / d ** 3) * np.exp(-np.pi * np.asarray(y) / d)


def dE2_dz(xc, y, h=1e-9):
    """d|E|^2/dz at x = xc (peak amplitude)."""
    y = np.asarray(y, dtype=float)
    E1 = fourier_field(np.full_like(y, xc), y + h)
    E0 = fourier_field(np.full_like(y, xc), y - h)
    return ((E1[0] ** 2 + E1[1] ** 2) - (E0[0] ** 2 + E0[1] ** 2)) / (2 * h)


def main():
    print("=" * 78)
    print("EXERCISE 4 - 1D FOURIER REFERENCE FOR THE ELECTRODE ARRAY")
    print("=" * 78)
    err_el, err_gap, curl, div, rate = verify_reference()
    print(f"  electrode BC error   = {err_el:.4g} V  (of {V0:.0f} V)")
    print(f"  gap BC error         = {err_gap:.4g} V")
    print(f"  relative curl        = {curl:.3e}")
    print(f"  relative divergence  = {div:.3e}")
    print(f"  |E|^2 decay rate     = {rate:.4g} /m   (pi/d = {math.pi/D:.4g} /m)")
    print()

    xg = EL_X0 + D                      # first gap centre  (60..70 um)
    xe = EL_X0 + D / 2                  # first electrode centre (50..60 um)

    print("  VERTICAL PROFILE OF d|E|^2/dz (PEAK amplitude, V^2/m^3)")
    print("    y[um]   gap centre        electrode centre     course formula")
    rows = []
    for yy in [0.5e-6, 1e-6, 2e-6, 3e-6, 5e-6, 10e-6, 20e-6, 30e-6]:
        gg = float(dE2_dz(xg, yy))
        ge = float(dE2_dz(xe, yy))
        cf = float(course_formula(yy))
        print(f"   {yy*1e6:6.2f}   {gg:+.6e}    {ge:+.6e}      {cf:+.6e}")
        rows.append({"y_um": yy * 1e6, "exact_gap": gg, "exact_electrode": ge,
                     "course": cf})

    print()
    print("  X-AVERAGED d|E|^2/dz OVER ONE PERIOD (peak amplitude)")
    xs = EL_X0 + D / 2 + np.linspace(0, EL_P, 4001)[:-1]
    ys = np.array([0.5e-6, 1e-6, 2e-6, 3e-6, 5e-6, 10e-6, 20e-6, 30e-6])
    avg_rows = []
    for yy in ys:
        vals = dE2_dz(xs, np.full_like(xs, yy))
        avg = float(np.mean(vals))
        cf = float(course_formula(yy))
        print(f"   y = {yy*1e6:6.2f} um   x-avg = {avg:+.6e}   course = {cf:+.6e}"
              f"   ratio = {avg/cf:.4f}")
        avg_rows.append({"y_um": yy * 1e6, "x_avg": avg, "course": cf,
                         "ratio": avg / cf})

    print()
    print("  NOTE on conventions:")
    print("   * the values above are PEAK-amplitude |E|^2.")
    print("   * COMSOL's DEP node uses |E_rms|^2 = |E_peak|^2/2, so the")
    print("     COMSOL force is 2*pi*a^3*eps0*eps_r*Re{K}*grad|E_rms|^2")
    print("       = pi*a^3*eps0*eps_r*Re{K}*grad|E_peak|^2 ,")
    print("     which is exactly the convention of the course formula.")

    out = {
        "d": D, "V0": V0, "EL_X0": EL_X0, "N_EL": N_EL,
        "bc_error_electrode_V": err_el, "bc_error_gap_V": err_gap,
        "relative_curl": curl, "relative_divergence": div,
        "decay_rate_per_m": rate,
        "gap_centre_x_um": xg * 1e6, "electrode_centre_x_um": xe * 1e6,
        "dE2dz_profiles": rows, "x_averaged": avg_rows,
    }
    (NOTES / "ex4_fourier_reference.json").write_text(
        json.dumps(out, indent=2, default=float), encoding="utf-8")
    print()
    print("  wrote " + str(NOTES / "ex4_fourier_reference.json"))


if __name__ == "__main__":
    main()
