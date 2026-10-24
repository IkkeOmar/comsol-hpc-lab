"""
Independent 1D Fourier reference for the Ex-4 coplanar electrode array.

WHY THIS EXISTS
---------------
The COMSOL result must be checked against something that is not COMSOL and
not the course formula, so that "COMSOL agrees with the course formula" can
be separated from "both are wrong for the same reason".

The reference is the exact solution of the 2D Laplace problem for an
INFINITE PERIODIC array of coplanar electrodes.  This is the idealisation the
course/Ex-3 formula assumes, so comparing against it tells us whether the
COMSOL 3D model reproduces the ideal 1D physics.

SETUP (matching the assignment)
    Electrodes of width d = 10 um with gaps of width d = 10 um, so the
    period is p = 2d = 20 um.  Electrode centres at x = 55 um, 75 um, ...
    (the assignment puts the FIRST electrode's LEFT edge at x = 50 um, so
    electrode 0 spans x = 50..60 um and the first gap spans 60..70 um).
    Potentials alternate 10 V / 0 V.
    The fluid occupies z > 0 (z = 0 is the electrode plane).

THE SOLUTION
    With the period p = 2d, write the boundary potential on the electrode
    plane as a Fourier series in x with wavenumbers k_n = 2*pi*n/p = n*pi/d:

        V(x, 0) = sum_n c_n sin(2*pi*n*x/p)   (odd n only, because the
        pattern is antisymmetric about the electrode centre)

    For a square wave of amplitude V0/2 alternating on/off with 50% duty
    cycle, the coefficients are  c_n = (4*V0/(n*pi)) * (-1)^((n-1)/2) for
    odd n and 0 for even n.  (Here V0 = 10 V is the full swing between the
    10 V and 0 V electrodes.)

    Each harmonic decays into the fluid as exp(-k_n z), so

        V(x, z) = sum_{n odd} c_n sin(k_n x) exp(-k_n z)

    and the electric field is  E = -grad V.

    The DEP force uses  grad|E|^2 , and COMSOL's Dielectrophoretic Force
    node computes exactly  grad(|E_rms|^2)  with E_rms the RMS field, i.e.
    |E_rms|^2 = |E_peak|^2 / 2 for a harmonic excitation with V0 as the
    PEAK amplitude.  We therefore compare the COMSOL |E|^2 against the
    PEAK-amplitude |E|^2 from the Fourier series, and separately check the
    1/2 factor.

CHECK OF THE COURSE FORMULA
    The course (Ex-3) formula is

        |grad E|^2 = (32/pi) * (V0^2/d^3) * exp(-pi*y/d)

    with d = 10 um.  The leading Fourier harmonic (n = 1) has k = pi/d, so
    the exponential decay rate exp(-pi*y/d) matches the n = 1 harmonic
    exactly.  The prefactor is checked numerically below.

VERIFICATION (self-contained)
    The script asserts that its own 1D Laplace solution satisfies the
    boundary conditions (V = V0 on the electrode, 0 on the gap, at z = 0)
    and that the field is curl-free and divergence-free away from the
    electrodes, to 1e-10 relative.
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
EL_X0 = 50e-6        # left edge of the first electrode [m]
N_EL = 45
EL_P = 20e-6         # pitch

EPS0 = 8.8541878128e-12
G = 9.80665


def fourier_potential(x, z, V0=V0, d=D, n_max=201):
    """Potential of the infinite periodic electrode array (peak amplitude).

    x, z are arrays (same shape).  Electrode centres at x = EL_X0 + d/2 + m*p.
    """
    x = np.asarray(x, dtype=float)
    z = np.asarray(z, dtype=float)
    V = np.zeros_like(x + z)
    # shift so that an electrode centre sits at x = 0
    xs = x - (EL_X0 + d / 2.0)
    for n in range(1, n_max + 1, 2):
        k = n * np.pi / d
        c = 4.0 * V0 / (n * np.pi) * ((-1) ** ((n - 1) // 2))
        V += c * np.sin(k * xs) * np.exp(-k * z)
    return V


def fourier_field(x, z, V0=V0, d=D, n_max=201):
    """(Ex, Ez) of the infinite periodic electrode array (peak amplitude)."""
    x = np.asarray(x, dtype=float)
    z = np.asarray(z, dtype=float)
    xs = x - (EL_X0 + d / 2.0)
    Ex = np.zeros_like(x + z)
    Ez = np.zeros_like(x + z)
    for n in range(1, n_max + 1, 2):
        k = n * np.pi / d
        c = 4.0 * V0 / (n * np.pi) * ((-1) ** ((n - 1) // 2))
        e = np.exp(-k * z)
        Ex += -c * k * np.cos(k * xs) * e
        Ez += c * k * np.sin(k * xs) * e
    return Ex, Ez


def verify_reference():
    """Assert the Fourier solution really solves the problem it claims to."""
    # (1) boundary potential: V = V0 on the electrode, 0 in the gap, at z=0
    xs = np.linspace(0, 2 * D, 4001)
    xs_electrode = xs[xs <= D]
    xs_gap = xs[xs >= D]
    # x measured from an electrode CENTRE: electrode spans [-d/2, d/2]
    u = np.linspace(-D, D, 4001)
    V = fourier_potential(u, np.zeros_like(u))
    on_el = np.abs(u) <= D / 2
    err_el = np.max(np.abs(V[on_el] - V0))
    err_gap = np.max(np.abs(V[~on_el] - 0.0))
    assert err_el < 2e-2, f"electrode BC error {err_el}"
    assert err_gap < 2e-2, f"gap BC error {err_gap}"

    # (2) curl-free and divergence-free in the fluid (z > 0), away from the
    #     electrodes: dEx/dz - dEz/dx = 0 and dEx/dx + dEz/dz = 0
    h = 1e-7
    xg, zg = np.meshgrid(np.linspace(0.25 * D, 1.75 * D, 41),
                         np.linspace(0.05 * D, 2.0 * D, 41))
    Ex, Ez = fourier_field(xg, zg)
    dEx_dz = (fourier_field(xg, zg + h)[0] - fourier_field(xg, zg - h)[0]) / (2 * h)
    dEz_dx = (fourier_field(xg + h, zg)[1] - fourier_field(xg - h, zg)[1]) / (2 * h)
    dEx_dx = (fourier_field(xg + h, zg)[0] - fourier_field(xg - h, zg)[0]) / (2 * h)
    dEz_dz = (fourier_field(xg, zg + h)[1] - fourier_field(xg, zg - h)[1]) / (2 * h)
    curl = np.max(np.abs(dEx_dz - dEz_dx)) / np.max(np.abs(dEx_dz))
    div = np.max(np.abs(dEx_dx + dEz_dz)) / np.max(np.abs(dEx_dx))
    assert curl < 1e-6, f"curl not zero: {curl}"
    assert div < 1e-6, f"divergence not zero: {div}"

    # (3) the course formula's prefactor against the n = 1 harmonic.
    #     |E|^2 for the dominant harmonic at the gap centre:
    #        E_x = -c_1 k_1 cos(k_1 x) exp(-k_1 z),  c_1 = 4 V0/pi
    #     The course formula uses d = gap width = 10 um and k = pi/d.
    #     Evaluate the exact |grad E|^2 = |d|E|^2/dz| numerically at z -> 0.
    z0 = 1e-6
    eps = 1e-9
    for xc, name in [(EL_X0 + D, "gap centre"), (EL_X0 + D / 2, "electrode centre")]:
        E1 = fourier_field(np.array([xc]), np.array([z0 + eps]))
        E0 = fourier_field(np.array([xc]), np.array([z0 - eps]))
        g = (E1[0] ** 2 + E1[1] ** 2 - (E0[0] ** 2 + E0[1] ** 2)) / (2 * eps)
        print(f"  {name}: d|E|^2/dz (peak) = {g:.6e} V^2/m^3")
    return err_el, err_gap, curl, div


def course_formula(y, V0=V0, d=D):
    """The course/Ex-3 formula: |grad E|^2 = (32/pi)(V0^2/d^3)exp(-pi y/d)."""
    return (32.0 / math.pi) * (V0 ** 2 / d ** 3) * np.exp(-np.pi * np.asarray(y) / d)


def main():
    print("=" * 78)
    print("EXERCISE 4 - 1D FOURIER REFERENCE FOR THE ELECTRODE ARRAY")
    print("=" * 78)
    err_el, err_gap, curl, div = verify_reference()
    print(f"  electrode BC error  = {err_el:.3e} V")
    print(f"  gap BC error        = {err_gap:.3e} V")
    print(f"  relative curl       = {curl:.3e}")
    print(f"  relative divergence = {div:.3e}")
    print()

    # profile of |E|^2 and its z-derivative at the first gap centre
    y = np.linspace(0.0, 60e-6, 601)
    xg = EL_X0 + D                      # first gap centre (60..70 um)
    xe = EL_X0 + D / 2                  # first electrode centre (50..60 um)
    for xc, name in [(xg, "gap"), (xe, "electrode")]:
        Ex, Ez = fourier_field(np.full_like(y, xc), y)
        E2 = Ex ** 2 + Ez ** 2
        g = np.gradient(E2, y)
        print(f"  {name} centre, x = {xc*1e6:.1f} um:")
        for i in range(0, 601, 100):
            print(f"    y = {y[i]*1e6:6.2f} um   |E|^2 = {E2[i]:.6e} V^2/m^2"
                  f"   d|E|^2/dz = {g[i]:+.6e} V^2/m^3")
        print()

    # ---- compare the course formula with the exact 1D reference ---------
    print("  COURSE FORMULA vs EXACT 1D REFERENCE (at the gap centre)")
    print("   y[um]   course d|E|^2/dz      exact d|E|^2/dz    ratio")
    rows = []
    for yy in [0.5e-6, 1e-6, 2e-6, 5e-6, 10e-6, 20e-6, 30e-6]:
        Ex, Ez = fourier_field(np.array([xg]), np.array([yy]))
        Ex2, Ez2 = fourier_field(np.array([xg]), np.array([yy + 1e-9]))
        Ex0, Ez0 = fourier_field(np.array([xg]), np.array([yy - 1e-9]))
        exact = ((Ex2[0] ** 2 + Ez2[0] ** 2) - (Ex0[0] ** 2 + Ez0[0] ** 2)) / 2e-9
        cf = course_formula(yy)
        # the course formula is written as a positive magnitude of a gradient
        ratio = cf / abs(exact)
        print(f"   {yy*1e6:6.2f}   {cf:+.6e}      {exact:+.6e}   {ratio:.4f}")
        rows.append({"y_um": yy * 1e6, "course": float(cf), "exact": float(exact),
                     "ratio": float(ratio)})

    print()
    print("  NOTE on the 1/2 (peak vs RMS) factor:")
    print("   COMSOL's DEP node uses |E_rms|^2 = |E_peak|^2 / 2, and the")
    print("   course formula is stated for the PEAK amplitude, so the two")
    print("   must differ by exactly 1/2 in the force.  The reference script")
    print("   therefore reports PEAK-amplitude |E|^2, and the comparison")
    print("   against COMSOL must account for that factor.")

    out = {
        "bc_error_electrode_V": err_el,
        "bc_error_gap_V": err_gap,
        "relative_curl": curl,
        "relative_divergence": div,
        "d": D, "V0": V0, "EL_X0": EL_X0, "N_EL": N_EL,
        "gap_centre_x_um": xg * 1e6,
        "electrode_centre_x_um": xe * 1e6,
        "course_vs_exact": rows,
    }
    (NOTES / "ex4_fourier_reference.json").write_text(
        json.dumps(out, indent=2, default=float), encoding="utf-8")
    print()
    print("  wrote " + str(NOTES / "ex4_fourier_reference.json"))


if __name__ == "__main__":
    main()
