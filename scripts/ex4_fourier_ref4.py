"""
Independent 1D Fourier reference for the Ex-4 coplanar electrode array.

WHY THIS EXISTS
---------------
The COMSOL result must be checked against something that is neither COMSOL
nor the course formula, so that "COMSOL agrees with the course formula" can
be separated from "both are wrong for the same reason".

The reference is the EXACT solution of the 2D Laplace problem for an
INFINITE PERIODIC array of coplanar electrodes, which is the idealisation
that the course/Ex-3 formula assumes.

SETUP (matching the assignment)
    Electrodes of width d = 10 um with gaps of width d = 10 um, so the
    electrode PITCH is p = 2d = 20 um.  The first electrode's LEFT edge is at
    x = 50 um, so electrode 0 spans x = 50..60 um, the first gap spans
    60..70 um, electrode 1 spans 70..80 um, and so on.
    Potentials ALTERNATE 10 V / 0 V from electrode to electrode.

    CONSEQUENCE FOR THE FOURIER SERIES: because the potential alternates
    between neighbouring electrodes, the pattern has period 2*p = 40 um
    (electrode 0 at 10 V, electrode 1 at 0 V, electrode 2 at 10 V, ...).
    A single 20 um cell contains one 10 V electrode and one 0 V electrode
    and is NOT self-repeating, so the harmonics are those of a 40 um
    period: wavenumbers k_n = 2*pi*n/40 um = n*pi/20 um.

    The natural coordinate is x' measured from the LEFT EDGE of electrode 0
    (x = 50 um).  Over one 40 um period the potential on the electrode
    plane is the square wave
        V = V0   for x' in [0, 10)      (electrode 0, at 10 V)
        V = 0   for x' in [10, 30)     (gap 10..20, electrode 1 at 20..30)
        V = V0  for x' in [30, 40)     (gap 30..40)
    i.e. a 50 % duty square wave of amplitude V0/2 about the mean V0/2,
    which is exactly the standard "alternating electrodes" series.

THE SOLUTION
    With period L = 40 um and wavenumbers k_n = n*pi/20 um, the potential on
    the electrode plane has the Fourier series (relative to the mean V0/2)

        V(x', 0) = V0/2 + sum_{n odd} (2 V0/(n pi)) sin(n pi x'/(2d))   [n odd]

    (the square wave is odd about x' = 0 shifted to the electrode edge) and
    each harmonic decays into the fluid as exp(-k_n z):

        V(x', z) = V0/2 + sum_{n odd} (2 V0/(n pi)) sin(n pi x'/(2d))
                                        exp(-n pi z/(2d))
        E = -grad V

    PEAK-AMPLITUDE CONVENTION
        The course formula uses E = PEAK amplitude; COMSOL's DEP node uses
        |E_rms|^2 = |E_peak|^2 / 2.  Everything below is PEAK amplitude;
        the 1/2 is applied when the force is compared.

VERIFICATION (self-contained, in this script)
    (1) the series reproduces the boundary potential (V0 on the electrodes,
        0 on the grounded electrode) to < 5 %;
    (2) the field is curl-free and divergence-free in the fluid to 1e-5
        relative;
    (3) the leading harmonic decays as exp(-pi z/(2d)), matching the decay
        rate in the course formula exp(-pi y/d) with d = 10 um when the
        DEP force |grad E|^2 ~ exp(-2*pi*z/(2d)) ... this is checked against
        the course formula numerically below.
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
EL_P = 20e-6         # electrode PITCH (electrode + gap)

N_MAX = 401          # number of odd harmonics summed


def _series(xp, z, V0=V0, d=D, n_max=N_MAX):
    """Harmonic sum S(x', z) and its x,z derivatives.

    xp : distance from the LEFT EDGE of electrode 0 [m]
    z  : height above the electrode plane [m]

    V(x',z) = V0/2 + sum_{n odd} (2 V0/(n pi)) sin(n pi xp/(2d)) exp(-n pi z/(2d))
    """
    xp = np.asarray(xp, dtype=float)
    z = np.asarray(z, dtype=float)
    shape = np.broadcast(xp, z).shape
    S = np.zeros(shape)
    dSdx = np.zeros(shape)
    dSdz = np.zeros(shape)
    for n in range(1, n_max + 1, 2):
        k = n * np.pi / (2.0 * d)
        c = 2.0 * V0 / (n * np.pi)
        e = np.exp(-k * z)
        S += c * np.sin(k * xp) * e
        dSdx += c * k * np.cos(k * xp) * e
        dSdz += -c * k * np.sin(k * xp) * e
    return S, dSdx, dSdz


def fourier_potential(x, z, V0=V0, d=D, n_max=N_MAX):
    """Potential of the infinite periodic electrode array (peak amplitude).
    x is the ABSOLUTE x coordinate (electrode 0 left edge at EL_X0)."""
    x = np.asarray(x, dtype=float)
    z = np.asarray(z, dtype=float)
    S, _, _ = _series(x - EL_X0, z, V0, d, n_max)
    return 0.5 * V0 + S


def fourier_field(x, z, V0=V0, d=D, n_max=N_MAX):
    """(Ex, Ez) of the infinite periodic electrode array (peak amplitude)."""
    x = np.asarray(x, dtype=float)
    z = np.asarray(z, dtype=float)
    _, dSdx, dSdz = _series(x - EL_X0, z, V0, d, n_max)
    return -dSdx, -dSdz


def verify_reference():
    """Assert the Fourier solution really solves the problem it claims to."""
    # (1) boundary potential at z = 0.
    #     electrode 0 (10 V) spans x = 50..60, electrode 1 (0 V) spans 70..80
    xp = np.linspace(0.0, 40e-6, 8001)
    V = fourier_potential(EL_X0 + xp, np.zeros_like(xp))
    on_el0 = (xp >= 0.0) & (xp <= D)
    on_el1 = (xp >= EL_P) & (xp <= EL_P + D)
    err_el0 = np.max(np.abs(V[on_el0] - V0))
    err_el1 = np.max(np.abs(V[on_el1] - 0.0))
    assert err_el0 < 0.05 * V0, f"electrode-0 BC error {err_el0:.4g} V"
    assert err_el1 < 0.05 * V0, f"electrode-1 BC error {err_el1:.4g} V"

    # (2) curl-free and divergence-free in the fluid, away from the electrodes
    h = 1e-8
    xg, zg = np.meshgrid(np.linspace(0.25 * EL_P, 3.75 * EL_P, 31),
                         np.linspace(0.10 * D, 2.0 * D, 31))
    dEx_dz = (fourier_field(xg, zg + h)[0] - fourier_field(xg, zg - h)[0]) / (2 * h)
    dEz_dx = (fourier_field(xg + h, zg)[1] - fourier_field(xg - h, zg)[1]) / (2 * h)
    dEx_dx = (fourier_field(xg + h, zg)[0] - fourier_field(xg - h, zg)[0]) / (2 * h)
    dEz_dz = (fourier_field(xg, zg + h)[1] - fourier_field(xg, zg - h)[1]) / (2 * h)
    curl = np.max(np.abs(dEx_dz - dEz_dx)) / np.max(np.abs(dEx_dz))
    div = np.max(np.abs(dEx_dx + dEz_dz)) / np.max(np.abs(dEx_dx))
    assert curl < 1e-5, f"curl not zero: {curl}"
    assert div < 1e-5, f"divergence not zero: {div}"

    # (3) the leading harmonic of |E|^2 decays as exp(-pi z/d)
    z1, z2 = 5e-6, 6e-6
    e1 = fourier_field(np.array([EL_X0 + D]), np.array([z1]))
    e2 = fourier_field(np.array([EL_X0 + D]), np.array([z2]))
    r = ((e2[0][0] ** 2) / (e1[0][0] ** 2))
    rate = -math.log(r) / (z2 - z1)
    # |E|^2 ~ exp(-2*k1*z) with k1 = pi/(2d)  ->  rate = pi/d
    assert abs(rate - math.pi / D) / (math.pi / D) < 0.20, \
        f"|E|^2 decay rate {rate} vs pi/d {math.pi/D}"
    return err_el0, err_el1, curl, div, rate


def course_formula(y, V0=V0, d=D):
    """The course/Ex-3 formula: |grad E|^2 = (32/pi)(V0^2/d^3)exp(-pi y/d)."""
    return (32.0 / math.pi) * (V0 ** 2 / d ** 3) * np.exp(-np.pi * np.asarray(y) / d)


def dE2_dz(xabs, y, h=1e-9):
    """d|E|^2/dz at absolute x (peak amplitude)."""
    y = np.asarray(y, dtype=float)
    E1 = fourier_field(np.full_like(y, xabs), y + h)
    E0 = fourier_field(np.full_like(y, xabs), y - h)
    return ((E1[0] ** 2 + E1[1] ** 2) - (E0[0] ** 2 + E0[1] ** 2)) / (2 * h)


def main():
    print("=" * 78)
    print("EXERCISE 4 - 1D FOURIER REFERENCE FOR THE ELECTRODE ARRAY")
    print("=" * 78)
    print(f"  electrode width d = {D*1e6:.0f} um, pitch = {EL_P*1e6:.0f} um,")
    print(f"  pattern period (alternating 10V/0V) = {2*EL_P*1e6:.0f} um")
    err0, err1, curl, div, rate = verify_reference()
    print(f"  electrode-0 (10 V) BC error = {err0:.4g} V")
    print(f"  electrode-1 (0 V)  BC error = {err1:.4g} V")
    print(f"  relative curl        = {curl:.3e}")
    print(f"  relative divergence  = {div:.3e}")
    print(f"  |E|^2 decay rate     = {rate:.4g} /m   (pi/d = {math.pi/D:.4g} /m)")
    print()

    # gap centre (x = 60..70 um, centre 65 um) and electrode-0 centre (55 um)
    xg = EL_X0 + 1.5 * D
    xe = EL_X0 + 0.5 * D

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
    print("  X-AVERAGED d|E|^2/dz OVER ONE 40 um PERIOD (peak amplitude)")
    xp = np.linspace(0.0, 2 * EL_P, 4001)[:-1]
    ys = np.array([0.5e-6, 1e-6, 2e-6, 3e-6, 5e-6, 10e-6, 20e-6, 30e-6])
    avg_rows = []
    for yy in ys:
        vals = dE2_dz(EL_X0 + xp, np.full_like(xp, yy))
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
        "d": D, "V0": V0, "EL_X0": EL_X0, "N_EL": N_EL, "EL_P": EL_P,
        "pattern_period_um": 2 * EL_P * 1e6,
        "bc_error_el0_V": err0, "bc_error_el1_V": err1,
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
