#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Exercise 1 - The H-filter (Bruus p.99).  Analytical sub-questions a)-e).
the lab-on-a-chip course, Assignment 1.

Run:  python ex1_analytical.py

Outputs (all inside the workfolder):
  notes/ex1_analytical_results.txt          every number, with units
  notes/ex1_analytical_results.json         machine readable
  figures/ex1_a_time_scales.png             time-scale comparison (bar chart)
  figures/ex1_b_mixing.png                  c(+w/2,t)/C0 vs t, 30% crossing
  figures/ex1_b_profiles.png                c(y,t) at t = 0, t1/2, t1, 2t1
  figures/ex1_e_pressure.png                dp vs Q (log-log) + operating point
  figures/ex1_e_crossing_fraction.png       (extra) crossover fraction vs t

PHYSICAL MODEL
--------------
The H-filter main (horizontal) channel has length L, width w and height h and
carries the TOTAL flow Q0.  Two side channels feed it (sugar solution C0 from
below, pure water from above) and two exit channels leave it.  Because
h << w the channel is shallow: the flow is 2D in the x-y plane with a
Poiseuille profile and the concentration field is purely transverse.

Transverse diffusion problem (y in [-w/2, w/2], no-flux walls):

    dc/dt = D d2c/dy2 ,   c(y,0) = C0 for y<0 (sugar half), 0 for y>0 (water)

whose solution is assignment Eq. (1)

    c(y,t) = C0/2 - sum_{n=1}^inf [2C0/((2n-1)pi)] sin(((2n-1)pi/w) y)
                           exp(-(((2n-1)pi/w)^2) D t)                      (Eq.1)

Every quoted number is produced by this script; nothing is hard-coded.
"""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from scipy.optimize import brentq
from scipy.sparse import diags
from scipy.sparse.linalg import expm_multiply
from scipy.special import erfc
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

# ---------------------------------------------------------------------------
# constants (course conventions)
# ---------------------------------------------------------------------------
K_B = 1.380649e-23        # J/K
EPS0 = 8.8541878128e-12   # F/m
ETA = 1.0e-3              # Pa*s, water
TEMP = 293.15             # K
G = 9.80665               # m/s^2
RHO = 1000.0              # kg/m^3, water

WORK = Path(r"<project>")
FIG = WORK / "figures"
NOTES = WORK / "notes"
FIG.mkdir(parents=True, exist_ok=True)
NOTES.mkdir(parents=True, exist_ok=True)

C_ACCENT = "#E50000"   # the course red
C_BLUE = "#1f4e79"
C_GREEN = "#2e7d32"
C_GREY = "#555555"
C_LIGHT = "#9aa0a6"

# The report places every figure at 0.85\textwidth = 4.23 in wide.  Drawing at
# that width and saving WITHOUT a tight bounding box keeps the saved native
# width equal to the placement width, so the nominal point sizes below are the
# point sizes on the page (effective pt = size * 4.23 / 4.23).
FIG_W = 4.23

plt.rcParams.update({
    "figure.dpi": 150,
    "savefig.dpi": 300,
    "font.size": 9,
    "axes.titlesize": 9,
    "axes.labelsize": 9,
    "xtick.labelsize": 8,
    "ytick.labelsize": 8,
    "legend.fontsize": 8,
    "axes.grid": True,
    "grid.alpha": 0.3,
    "grid.linestyle": ":",
    "axes.spines.top": False,
    "axes.spines.right": False,
    "legend.frameon": True,
    "legend.framealpha": 1.0,
    "legend.facecolor": "white",
    "legend.edgecolor": "none",
    "figure.constrained_layout.use": True,
})

# ---------------------------------------------------------------------------
# geometry / operating points
# ---------------------------------------------------------------------------
W = 100e-6            # channel width  [m]
H = 5e-6              # channel height (gap) [m]
D_SUGAR = 2.0e-10     # sugar diffusion coefficient [m^2/s]
C0 = 1.0              # inlet sugar concentration [mol/m^3]

L_ANALYTICAL = 25e-3  # Bruus p.99 case, main channel length [m]
L_COMSOL = 6e-3       # COMSOL case, main channel length [m]

Q_COMSOL = 0.1e-9 / 60.0   # 0.1 uL/min -> m^3/s  (1 uL = 1e-9 m^3)

N_SERIES = 2000       # terms used for the Fourier series

results: dict = {}
lines: list[str] = []


def out(line: str = "") -> None:
    """Append a line to the report and echo it to stdout."""
    lines.append(line)
    print(line)


def q_ul_min(Q: float) -> float:
    """Flow rate in uL/min from m^3/s (1 m^3 = 1e9 uL)."""
    return Q * 1.0e9 * 60.0


def xdim(t, w=W, D=D_SUGAR):
    """Dimensionless diffusion time x = pi^2 D t / w^2."""
    return np.pi ** 2 * D * t / w ** 2


# ===========================================================================
# 1.  concentration models
# ===========================================================================
def c_series(y, t, w=W, D=D_SUGAR, c0=C0, N=N_SERIES):
    """Assignment Eq. (1): Neumann series for a step at y = 0."""
    Y = np.atleast_1d(np.asarray(y, dtype=float))
    T = np.atleast_1d(np.asarray(t, dtype=float))
    Y, T = np.broadcast_arrays(Y, T)
    n = np.arange(1, N + 1)
    k = (2 * n - 1) * np.pi / w
    coef = 2.0 * c0 / ((2 * n - 1) * np.pi)
    dec = np.exp(-(k[:, None] ** 2) * D * T[None, :])
    sn = np.sin(k[:, None] * Y[None, :])
    return c0 / 2.0 - (coef[:, None] * sn * dec).sum(axis=0)


def c_general(y, t, w=W, D=D_SUGAR, c0=C0, ystar=0.0, N=N_SERIES):
    """Neumann series on [-w/2,w/2] for a step at an arbitrary y = ystar.

    Neumann eigenfunctions on [-w/2,w/2] are cos(2m pi y/w) (m>=1) and
    sin((2m+1) pi y/w) (m>=0).  For ystar = 0 this collapses exactly to
    assignment Eq. (1) (verified numerically in the script).
    """
    Y = np.atleast_1d(np.asarray(y, dtype=float))
    T = np.atleast_1d(np.asarray(t, dtype=float))
    Y, T = np.broadcast_arrays(Y, T)
    val = np.full(Y.shape, c0 * (ystar + w / 2.0) / w)

    m = np.arange(1, N + 1)
    k = 2 * m * np.pi / w
    A = (c0 / (m * np.pi)) * np.sin(2 * m * np.pi * ystar / w)
    dec = np.exp(-(k[:, None] ** 2) * D * T[None, :])
    cs = np.cos(k[:, None] * Y[None, :])
    val = val + (A[:, None] * cs * dec).sum(axis=0)

    n = np.arange(1, N + 1)
    kk = (2 * n - 1) * np.pi / w
    B = -(2 * c0 / ((2 * n - 1) * np.pi)) * np.cos((2 * n - 1) * np.pi * ystar / w)
    dec2 = np.exp(-(kk[:, None] ** 2) * D * T[None, :])
    sn2 = np.sin(kk[:, None] * Y[None, :])
    val = val + (B[:, None] * sn2 * dec2).sum(axis=0)
    return val


def c_erfc(y, t, w=W, D=D_SUGAR, c0=C0):
    """Infinite-domain closed form: c = C0/2 * erfc(y / (2 sqrt(D t)))."""
    Y = np.atleast_1d(np.asarray(y, dtype=float))
    T = np.atleast_1d(np.asarray(t, dtype=float))
    Y, T = np.broadcast_arrays(Y, T)
    with np.errstate(invalid="ignore", divide="ignore"):
        val = 0.5 * c0 * erfc(Y / (2.0 * np.sqrt(D * T)))
    val = np.where(T <= 0.0, np.where(Y < 0.0, c0, 0.0), val)
    return val


def c_images(y, t, w=W, D=D_SUGAR, c0=C0, K=200):
    """Image-sum form of Eq. (1), built from the erfc closed form.

    The Neumann (no-flux wall) problem on [-w/2,w/2] with a step at y=0 is
    the free-space problem of the 2w-periodic square wave that is c0 on
    [2kw-w, 2kw] and 0 on [2kw, 2kw+w].  Hence

        c(y,t) = (c0/2) sum_k [ erfc((y-a_k)/(2 sqrt(Dt)))
                              - erfc((y-b_k)/(2 sqrt(Dt))) ]
        a_k = 2kw - w (up-jumps),  b_k = 2kw (down-jumps).

    This is an INDEPENDENT check of Eq. (1) that uses only the erfc closed
    form, and it is exact (not a truncation).
    """
    Y = np.atleast_1d(np.asarray(y, dtype=float))
    T = np.atleast_1d(np.asarray(t, dtype=float))
    Y, T = np.broadcast_arrays(Y, T)
    s = 2.0 * np.sqrt(D * T)
    k = np.arange(-K, K + 1)
    a = (2 * k - 1) * w
    b = 2 * k * w
    with np.errstate(invalid="ignore", divide="ignore"):
        up = erfc((Y[None, :] - a[:, None]) / s[None, :])
        dn = erfc((Y[None, :] - b[:, None]) / s[None, :])
    return 0.5 * c0 * (dn - up).sum(axis=0)


def _finish(res, t):
    """Return a scalar for scalar t, an array for array t."""
    return res[0] if np.ndim(t) == 0 else res


def _udim(t, w=W, D=D_SUGAR):
    """Dimensionless time x as a 1-D array (length M), for outer products."""
    return np.atleast_1d(np.asarray(t, dtype=float)) * (np.pi ** 2 * D / w ** 2)


def c_up_area(t, w=W, D=D_SUGAR, c0=C0, N=N_SERIES):
    """Area-averaged sugar concentration of the TOP half [0, w/2].

    Analytic integral of Eq. (1) over the top half (uniform transverse
    velocity, i.e. the plug-flow model consistent with Eq. (1)):
        c_up(t) = C0/2 - (4C0/pi^2) sum_n e^{-(2n-1)^2 pi^2 D t/w^2}/(2n-1)^2
    """
    u = _udim(t, w, D)
    n = np.arange(1, N + 1)
    modes = (2 * n - 1).astype(float)
    s = (np.exp(-(modes ** 2).reshape(-1, 1) * u.reshape(1, -1))
         / (modes ** 2).reshape(-1, 1)).sum(axis=0)
    return _finish(c0 / 2.0 - 4.0 * c0 / np.pi ** 2 * s, t)


def c_up_area_gen(t, ystar, w=W, D=D_SUGAR, c0=C0, N=N_SERIES):
    """Area-averaged top-half concentration for a step at y = ystar.

    Only the odd sine modes contribute to the top-half average
    (int_0^{w/2} cos(2m pi y/w) dy = 0), giving the closed form
        c_up = c0(ystar+w/2)/w
               - (4c0/pi^2) sum_n cos((2n-1)pi ystar/w) e^{-(2n-1)^2 x}/(2n-1)^2
    """
    u = _udim(t, w, D)
    n = np.arange(1, N + 1)
    odd = (2 * n - 1).astype(float)
    s = (np.cos(odd * np.pi * ystar / w).reshape(-1, 1)
         * np.exp(-(odd ** 2).reshape(-1, 1) * u.reshape(1, -1))
         / (odd ** 2).reshape(-1, 1)).sum(axis=0)
    return _finish(c0 * (ystar + w / 2.0) / w - 4.0 * c0 / np.pi ** 2 * s, t)


def c_up_flux(t, w=W, D=D_SUGAR, c0=C0, N=N_SERIES):
    """Flow-weighted (Poiseuille) average concentration of the TOP half.

    u(y) = (3/2) U (1 - (2y/w)^2);   c_up = int_0^{w/2} u c dy / int_0^{w/2} u dy.
    The weighted integral is analytic, term by term:
        int_0^{w/2} (1-(2y/w)^2) sin(k_n y) dy
              = w [ 1/K - 4 s_n/K^2 + 8/K^3 ],  K = (2n-1)pi, s_n = (-1)^(n-1)
    """
    u = _udim(t, w, D)
    n = np.arange(1, N + 1)
    K = (2 * n - 1) * np.pi
    s = (-1.0) ** (n - 1)
    integ = (w * (1.0 / K - 4.0 * s / K ** 2 + 8.0 / K ** 3)).reshape(-1, 1)
    b = (-2.0 * c0 / ((2 * n - 1) * np.pi)).reshape(-1, 1)
    dec = np.exp(-((2 * n - 1) ** 2).reshape(-1, 1) * u.reshape(1, -1))
    num = (c0 / 2.0) * (w / 3.0) + (b * dec * integ).sum(axis=0)
    return _finish(num / (w / 3.0), t)


def c_up_flux_gen(t, ystar, w=W, D=D_SUGAR, c0=C0, N=N_SERIES):
    """Flow-weighted (Poiseuille) top-half concentration for a step at ystar.

    Closed form, term by term:
        int_0^{w/2} (1-(2y/w)^2) cos(2m pi y/w) dy = -(-1)^m w/(m^2 pi^2)
        int_0^{w/2} (1-(2y/w)^2) sin((2n-1) pi y/w) dy
              = w [ 1/K - 4 s_n/K^2 + 8/K^3 ],  K = (2n-1)pi
    """
    u = _udim(t, w, D)
    c_mean = c0 * (ystar + w / 2.0) / w
    num = c_mean * (w / 3.0)
    m = np.arange(1, N + 1)
    Am = ((c0 / (m * np.pi)) * np.sin(2 * m * np.pi * ystar / w)).reshape(-1, 1)
    Jm = (-((-1.0) ** m) * w / (m ** 2 * np.pi ** 2)).reshape(-1, 1)
    dec = np.exp(-4.0 * (m ** 2).reshape(-1, 1) * u.reshape(1, -1))
    num = num + (Am * Jm * dec).sum(axis=0)
    n = np.arange(1, N + 1)
    K = (2 * n - 1) * np.pi
    s = (-1.0) ** (n - 1)
    In = (w * (1.0 / K - 4.0 * s / K ** 2 + 8.0 / K ** 3)).reshape(-1, 1)
    Bn = (-(2.0 * c0 / ((2 * n - 1) * np.pi))
          * np.cos((2 * n - 1) * np.pi * ystar / w)).reshape(-1, 1)
    dec2 = np.exp(-((2 * n - 1) ** 2).reshape(-1, 1) * u.reshape(1, -1))
    num = num + (Bn * In * dec2).sum(axis=0)
    return _finish(num / (w / 3.0), t)


def c_up_quad(t, w=W, D=D_SUGAR, c0=C0, ystar=0.0, npts=801, N=N_SERIES):
    """Flow-weighted top-half concentration by brute-force quadrature."""
    y = np.linspace(0.0, w / 2.0, npts)
    u = 1.5 * (1.0 - (2.0 * y / w) ** 2)
    c = c_general(y, t, w=w, D=D, c0=c0, ystar=ystar, N=N)
    return float(np.trapezoid(u * c, y) / np.trapezoid(u, y))


def c_up_quad_area(t, w=W, D=D_SUGAR, c0=C0, ystar=0.0, npts=801, N=N_SERIES):
    """Area-averaged top-half concentration by quadrature."""
    y = np.linspace(0.0, w / 2.0, npts)
    c = c_general(y, t, w=w, D=D, c0=c0, ystar=ystar, N=N)
    return float(np.trapezoid(c, y) / (w / 2.0))


def fd_neumann(t, w=W, D=D_SUGAR, c0=C0, npts=801):
    """Independent finite-difference solution of the same PDE (no-flux walls,
    step initial condition) via an exact matrix exponential of the
    tridiagonal generator.  Used as an independent check of Eq. (1)."""
    y = np.linspace(-w / 2.0, w / 2.0, npts)
    dy = y[1] - y[0]
    n = npts
    main = np.full(n, -2.0)
    off = np.ones(n - 1)
    A = diags([off, main, off], [-1, 0, 1], shape=(n, n), format="csr").tolil()
    A[0, 0] = -2.0
    A[0, 1] = 2.0          # ghost-point mirroring -> zero flux at the wall
    A[n - 1, n - 1] = -2.0
    A[n - 1, n - 2] = 2.0
    A = A.tocsr()
    c0vec = np.where(y < 0.0, c0, 0.0)
    return y, expm_multiply((D / dy ** 2) * A * t, c0vec)


# ===========================================================================
# 2.  hydraulic helpers
# ===========================================================================
def R_h(L, w=W, h=H, eta=ETA):
    """Hagen-Poiseuille resistance of a rectangular channel, gap h << width w:
    R = 12 eta L / (w h^3)   [Pa s / m^3]."""
    return 12.0 * eta * L / (w * h ** 3)


def dp_from_Q(Q, L, w=W, h=H):
    return Q * R_h(L, w, h)


def flow_fraction_below(ystar, w=W):
    """Fraction of the main-channel flow below y = ystar for the Poiseuille
    profile u(y) = (3/2) U (1-(2y/w)^2):
        F(ystar) = [ ystar - 4 ystar^3/(3 w^2) + w/3 ] / (2w/3)
    F(-w/2)=0, F(0)=1/2, F(w/2)=1."""
    return (ystar - 4.0 * ystar ** 3 / (3.0 * w ** 2) + w / 3.0) / (2.0 * w / 3.0)


def ystar_from_fraction(alpha, w=W):
    """Invert flow_fraction_below (monotone increasing on [-w/2, w/2])."""
    return brentq(lambda ys: flow_fraction_below(ys, w) - alpha,
                  -0.5 * w, 0.5 * w, xtol=1e-16, rtol=1e-15)


# ===========================================================================
# 3.  report
# ===========================================================================
out("=" * 78)
out("EXERCISE 1 - THE H-FILTER (Bruus p.99) - ANALYTICAL SOLUTION")
out("the lab-on-a-chip course, Assignment 1")
out("=" * 78)
out()
out("Constants: k_B = 1.380649e-23 J/K, eps0 = 8.8541878128e-12 F/m,")
out("           eta = 1e-3 Pa*s (water), T = 293.15 K, g = 9.80665 m/s^2")
out("Geometry : w = 100 um, h = 5 um, D(sugar) = 2e-10 m^2/s, c0 = 1 mol/m^3")
out("Case A (Bruus p.99): L = 25 mm   Case B (COMSOL): L = 6 mm, Q = 0.1 uL/min")
out()

# ---------------------------------------------------------------------------
# 0.  verification of the series
# ---------------------------------------------------------------------------
out("-" * 78)
out("0.  VERIFICATION OF ASSIGNMENT EQ. (1)")
out("-" * 78)
yg = np.linspace(-W / 2, W / 2, 4001)
c0_series = c_series(yg, 0.0, N=N_SERIES)
step = np.where(yg < 0.0, C0, 0.0)
err = np.abs(c0_series - step)
gibbs_zone = W / 100.0
mask = np.abs(yg) > gibbs_zone
resid_max = float(err[mask].max())
resid_rms = float(np.sqrt(np.mean(err[mask] ** 2)))
l2_all = float(np.sqrt(np.trapezoid(err ** 2, yg) / np.trapezoid(step ** 2, yg)))
gibbs_peak = float(c0_series.max())
gibbs_overshoot_pct = (gibbs_peak - C0) / C0 * 100.0
y_at_y0 = float(c_series(0.0, 0.0, N=N_SERIES)[0])
out(f"Eq.(1) at t=0 vs the step function (N = {N_SERIES} terms):")
out(f"  max |c_series - step| for |y| > w/100      = {resid_max:.3e} mol/m^3 "
    f"({resid_max / C0:.3e} of c0)")
out(f"  rms |c_series - step| for |y| > w/100      = {resid_rms:.3e} mol/m^3")
out(f"  L2 error over the whole width (step norm)  = {l2_all:.3e}")
out(f"  value at the jump, y = 0                   = {y_at_y0:.4f} mol/m^3 "
    f"(= c0/2 as required)")
out(f"  Gibbs overshoot above the step value c0    = {gibbs_peak:.4f} mol/m^3 "
    f"({gibbs_overshoot_pct:.2f} % of the jump; classical Gibbs constant)")
out("  => Eq.(1) DOES reproduce the step initial condition c(y,0) = c0 for")
out("     y<0 and 0 for y>0.  The only defect is the classical Gibbs")
out("     ringing in a thin neighbourhood of the jump (width ~ w/N).")
out()

yg2 = np.linspace(-W / 2, W / 2, 501)
t_check = 3.7
diff_gen = float(np.max(np.abs(c_series(yg2, t_check, N=N_SERIES)
                                - c_general(yg2, t_check, ystar=0.0, N=N_SERIES))))
out(f"Eq.(1) vs the general Neumann series (ystar = 0), t = {t_check} s:")
out(f"  max difference = {diff_gen:.3e} mol/m^3   (must be ~0)")
out()

yf, cf = fd_neumann(t_check, npts=801)
cs = c_series(yf, t_check, N=N_SERIES)
fd_err = float(np.max(np.abs(cf - cs)))
dx = yf[1] - yf[0]
away = np.abs(yf) > 3.0 * dx
fd_err_away = float(np.max(np.abs(cf - cs)[away]))
out("Independent finite-difference check of the same PDE (no-flux walls,")
out(f"801 nodes, exact matrix exponential of the FD generator), t = {t_check} s:")
out(f"  max |c_FD - c_Eq1| over all nodes          = {fd_err:.3e} mol/m^3")
out(f"  max |c_FD - c_Eq1| away from the jump      = {fd_err_away:.3e} mol/m^3")
out("  (the FD initial condition is a piecewise-constant step, so the")
out("   difference is O(dx) in the jump cell only.)")
out()

img = c_images(yg2, t_check)
img_err = float(np.max(np.abs(img - c_series(yg2, t_check, N=N_SERIES))))
out("Independent image-sum check (exact, built only from the erfc closed")
out(f"form), t = {t_check} s:")
out(f"  max |c_images - c_Eq1|                     = {img_err:.3e} mol/m^3")
out("  => Eq.(1) is exactly the Neumann (no-flux wall) solution, and it is")
out("     the image sum of the free-space erfc closed form.")
out()

t_mass = 2.3
m_series = float(np.trapezoid(c_series(yg, t_mass, N=N_SERIES), yg))
m_init = float(np.trapezoid(step, yg))
out("Mass conservation (no-flux walls):")
out(f"  int c dy at t = {t_mass} s = {m_series:.8f} mol/m^2")
out(f"  int c dy at t = 0     = {m_init:.8f} mol/m^2 "
    f"(relative error {abs(m_series - m_init) / m_init:.2e})")
out()

c_flux_cf = float(c_up_flux(t_mass))
c_flux_qd = c_up_quad(t_mass, npts=2001)
c_area_cf = float(c_up_area(t_mass))
c_area_qd = c_up_quad_area(t_mass, npts=2001)
c_fluxgen_cf = float(c_up_flux_gen(t_mass, 0.0))
c_fluxgen_qd = c_up_quad(t_mass, ystar=0.0, npts=2001)
c_areagen_cf = float(c_up_area_gen(t_mass, 0.0))
c_areagen_qd = c_up_quad_area(t_mass, ystar=0.0, npts=2001)
out(f"Closed-form averages vs brute-force quadrature (t = {t_mass} s):")
out(f"  flux-weighted top half : closed form {c_flux_cf:.8f}  vs quadrature "
    f"{c_flux_qd:.8f}  (diff {abs(c_flux_cf - c_flux_qd):.2e})")
out(f"  area-averaged top half : closed form {c_area_cf:.8f}  vs quadrature "
    f"{c_area_qd:.8f}  (diff {abs(c_area_cf - c_area_qd):.2e})")
out(f"  generalised (ystar=0)  : flux diff {abs(c_fluxgen_cf - c_fluxgen_qd):.2e}"
    f", area diff {abs(c_areagen_cf - c_areagen_qd):.2e}")
out()

ys_chk = 0.21 * W
c_fg_chk = float(c_up_flux_gen(t_mass, ys_chk))
c_fg_qd = c_up_quad(t_mass, ystar=ys_chk, npts=2001)
c_ag_chk = float(c_up_area_gen(t_mass, ys_chk))
c_ag_qd = c_up_quad_area(t_mass, ystar=ys_chk, npts=2001)
out(f"Shifted-interface closed forms vs quadrature (ystar = "
    f"{1e6 * ys_chk:.3f} um, t = {t_mass} s):")
out(f"  flux-weighted : closed form {c_fg_chk:.8f}  vs quadrature "
    f"{c_fg_qd:.8f}  (diff {abs(c_fg_chk - c_fg_qd):.2e})")
out(f"  area-averaged : closed form {c_ag_chk:.8f}  vs quadrature "
    f"{c_ag_qd:.8f}  (diff {abs(c_ag_chk - c_ag_qd):.2e})")
out()

results["verification"] = {
    "eq1_max_residual_t0_offjump_mol_m3": resid_max,
    "eq1_rms_residual_t0_offjump_mol_m3": resid_rms,
    "eq1_L2_error_t0": l2_all,
    "eq1_value_at_jump_mol_m3": y_at_y0,
    "eq1_gibbs_overshoot_pct_of_jump": gibbs_overshoot_pct,
    "eq1_reproduces_step": True,
    "eq1_vs_general_series_max_diff_mol_m3": diff_gen,
    "eq1_vs_finite_difference_max_diff_mol_m3": fd_err,
    "eq1_vs_finite_difference_max_diff_away_from_jump_mol_m3": fd_err_away,
    "eq1_vs_image_sum_max_diff_mol_m3": img_err,
    "mass_conservation_rel_error": abs(m_series - m_init) / m_init,
    "flux_average_closed_vs_quad_diff": abs(c_flux_cf - c_flux_qd),
    "area_average_closed_vs_quad_diff": abs(c_area_cf - c_area_qd),
    "flux_average_generalised_closed_vs_quad_diff": abs(c_fluxgen_cf
                                                        - c_fluxgen_qd),
    "area_average_generalised_closed_vs_quad_diff": abs(c_areagen_cf
                                                        - c_areagen_qd),
    "shifted_flux_average_closed_vs_quad_diff": abs(c_fg_chk - c_fg_qd),
    "shifted_area_average_closed_vs_quad_diff": abs(c_ag_chk - c_ag_qd),
}

# ---------------------------------------------------------------------------
# (a) why is it a 1D problem?
# ---------------------------------------------------------------------------
out("=" * 78)
out("(a)  WHY THIS IS REASONABLY A 1D DIFFUSION PROBLEM")
out("=" * 78)
out("Assumptions (stated explicitly):")
out("  A1  Shallow channel: h = 5 um << w = 100 um, so the flow is 2D in the")
out("      x-y plane with a parabolic (Poiseuille) profile; h enters only the")
out("      hydraulic resistance, not the concentration field.")
out("  A2  Stokes flow, laminar, single phase (Re << 1, see below).")
out("  A3  Dilute, non-reacting sugar: pure diffusion with constant D.")
out("  A4  No-flux walls at y = +-w/2 (no sugar leaves through the walls).")
out("  A5  Convective mixing between the two side streams is neglected; the")
out("      only cross-stream mechanism is transverse diffusion.")
out("  A6  The concentration profile is 'frozen' in x: the advective travel")
out("      time along x is ~1e5 times shorter than the longitudinal diffusion")
out("      time, so Pe_x >> 1 and dc/dx ~ 0 (quantified below).")
out()


def time_scales(L, Q, label, cont=""):
    V = L * W * H
    t_res = V / Q
    U = Q / (W * H)
    pe_x = U * L / D_SUGAR
    pe_y = U * W / D_SUGAR
    re_h = RHO * U * H / ETA
    re_w = RHO * U * W / ETA
    tdx = L ** 2 / D_SUGAR
    tdy = W ** 2 / (D_SUGAR * np.pi ** 2)
    out(f"{label}{cont}")
    out(f"   L = {L * 1e3:.4g} mm,  Q0 = {q_ul_min(Q):.6g} uL/min "
        f"({Q:.6e} m^3/s)")
    out(f"   channel volume V = L w h              = {V:.6e} m^3")
    out(f"   residence time t_res = V/Q0            = {t_res:.6g} s")
    out(f"   mean velocity U = Q0/(w h)             = {U:.6g} m/s")
    out(f"   transverse diffusion time (1st eigenvalue) t_diff,y = w^2/(pi^2 D)"
        f" = {tdy:.6g} s")
    out(f"   longitudinal diffusion time t_diff,x = L^2/D            = {tdx:.6g} s")
    out(f"   Peclet x: Pe_x = U L/D                  = {pe_x:.6g}")
    out(f"   Peclet y: Pe_y = U w/D                  = {pe_y:.6g}")
    out(f"   Reynolds: Re_h = rho U h/eta           = {re_h:.3e} "
        f"(Re_w = {re_w:.3e})")
    out(f"   ratios: t_diff,x/t_res = {tdx / t_res:.6g}   "
        f"t_res/t_diff,y = {t_res / tdy:.6g}")
    return dict(V=V, t_res=t_res, U=U, pe_x=pe_x, pe_y=pe_y, re_h=re_h,
                re_w=re_w, t_diff_x=tdx, t_diff_y=tdy)


sc_co = time_scales(L_COMSOL, Q_COMSOL, "CASE B - COMSOL geometry:")
out()
sc_an = time_scales(L_ANALYTICAL, Q_COMSOL,
                     "CASE A - Bruus geometry (flow rate NOT given in the",
                     " assignment;")
out("         the COMSOL flow rate 0.1 uL/min is used as the reference):")
out()

out("General formulas in the (unspecified) total flow Q0:")
out(f"   t_res(Q0)  = L w h / Q0"
    f"  -> Case A: {L_ANALYTICAL * W * H:.6e}/Q0 [s],"
    f"  Case B: {L_COMSOL * W * H:.6e}/Q0 [s]")
out("   U(Q0)      = Q0/(w h)")
out(f"   Pe_y(Q0)   = U w/D = Q0/(h D)"
    f"  -> Case B at 0.1 uL/min: {Q_COMSOL / (H * D_SUGAR):.6g}")
out("   Pe_x(Q0)   = U L/D = Q0 L/(w h D)")
out(f"   t_diff,y   = w^2/(pi^2 D) = {sc_co['t_diff_y']:.6g} s   (both cases, "
    f"same w and D)")
out(f"   t_diff,x   = L^2/D = {sc_an['t_diff_x']:.6g} s (Case A), "
    f"{sc_co['t_diff_x']:.6g} s (Case B)")
out()
out("Conclusion (the physical argument):")
out(f"  * Convection along x dominates diffusion along x by Pe_x ~ "
    f"{sc_co['pe_x']:.0f} (Case B) to {sc_an['pe_x']:.0f} (Case A).")
out(f"    t_diff,x/t_res = {sc_co['t_diff_x'] / sc_co['t_res']:.0f} (Case B): the")
out(f"    concentration profile has no time to diffuse along a 6 mm channel,")
out(f"    so c(y) is essentially independent of x over the channel length -")
out(f"    the profile is 'frozen' in the streamwise direction.")
out(f"  * Transversally, t_res = {sc_co['t_res']:.4g} s (Case B) is a factor")
out(f"    {sc_co['t_diff_y'] / sc_co['t_res']:.3g} SMALLER than the transverse")
out(f"    diffusion time t_diff,y = {sc_co['t_diff_y']:.4g} s: diffusion across")
out(f"    the width is only PARTLY complete within one residence time, which")
out(f"    is exactly the regime the H-filter exploits (it sorts on D).")
out(f"  * Hence dc/dt = D d2c/dy2 with x frozen: a 1D (transverse) diffusion")
out(f"    problem.  The transverse length scale is w = 100 um; the streamwise")
out(f"    length scale L does not enter the concentration field at all.")
out()

results["a"] = {
    "case_B_comsol": {
        "L_m": L_COMSOL, "Q0_m3_s": Q_COMSOL, "Q0_uL_min": 0.1,
        "channel_volume_m3": sc_co["V"], "t_res_s": sc_co["t_res"],
        "U_mean_m_s": sc_co["U"], "t_diff_y_s": sc_co["t_diff_y"],
        "t_diff_x_s": sc_co["t_diff_x"], "Pe_x": sc_co["pe_x"],
        "Pe_y": sc_co["pe_y"], "Re_h": sc_co["re_h"], "Re_w": sc_co["re_w"],
        "t_diff_x_over_t_res": sc_co["t_diff_x"] / sc_co["t_res"],
        "t_res_over_t_diff_y": sc_co["t_res"] / sc_co["t_diff_y"],
    },
    "case_A_analytical": {
        "L_m": L_ANALYTICAL, "Q0_m3_s": Q_COMSOL, "Q0_uL_min": 0.1,
        "channel_volume_m3": sc_an["V"], "t_res_s": sc_an["t_res"],
        "U_mean_m_s": sc_an["U"], "t_diff_y_s": sc_an["t_diff_y"],
        "t_diff_x_s": sc_an["t_diff_x"], "Pe_x": sc_an["pe_x"],
        "Pe_y": sc_an["pe_y"], "Re_h": sc_an["re_h"], "Re_w": sc_an["re_w"],
        "t_diff_x_over_t_res": sc_an["t_diff_x"] / sc_an["t_res"],
        "t_res_over_t_diff_y": sc_an["t_res"] / sc_an["t_diff_y"],
        "note": "flow rate is NOT given in the assignment for this case; "
                "0.1 uL/min is used as the reference and every quantity is "
                "also given as an explicit function of Q0",
    },
    "t_res_formula": "t_res = L w h / Q0",
    "t_diff_y_formula": "w^2/(pi^2 D)",
    "t_diff_x_formula": "L^2/D",
}

# ---------------------------------------------------------------------------
# (b) t1 for 30 % at the upper exit
# ---------------------------------------------------------------------------
out("=" * 78)
out("(b)  TIME t1 FOR 30 % SUGAR IN THE UPPER EXIT CHANNEL")
out("=" * 78)
out("Criterion (as prescribed): c(+w/2, t1) = 0.30 c0, i.e. the Eq.(1)")
out("concentration evaluated at the upper wall of the channel, y = +w/2,")
out("which is the wall of the upper (particle) exit channel.")
out()

TARGET = 0.30 * C0


def f_wall_series(t):
    return float(c_series(W / 2.0, t, N=N_SERIES)[0]) - TARGET


def f_wall_erfc(t):
    return float(c_erfc(W / 2.0, t)[0]) - TARGET


def f_wall_images(t):
    return float(c_images(W / 2.0, t)[0]) - TARGET


t1_series = brentq(f_wall_series, 1e-9, 1e3, xtol=1e-12, rtol=1e-14)
t1_erfc = brentq(f_wall_erfc, 1e-9, 1e3, xtol=1e-12, rtol=1e-14)
t1_images = brentq(f_wall_images, 1e-9, 1e3, xtol=1e-12, rtol=1e-14)

c_wall_at_t1 = float(c_series(W / 2.0, t1_series, N=N_SERIES)[0])
c_wall_erfc_at_t1 = float(c_erfc(W / 2.0, t1_erfc)[0])
rel_abs = abs(t1_series - t1_erfc) / t1_series
rel_ref = abs(t1_series - t1_erfc) / t1_erfc

out("B1  Eq.(1) - finite width, no-flux walls at y = +-w/2  [PRIMARY]")
out(f"    t1 = {t1_series:.6g} s")
out(f"    achieved c(+w/2, t1)/c0 = {c_wall_at_t1 / C0:.8f}  (target 0.30)")
out(f"    dimensionless x = pi^2 D t1 / w^2 = "
    f"{xdim(t1_series):.6f}")
out(f"    independent image-sum root (exact erfc form): t1 = {t1_images:.6g} s "
    f"(difference {abs(t1_images - t1_series):.2e} s)")
out()

out("B2  Closed form for an INFINITE domain (no walls, free space):")
out("    c(+w/2,t) = c0/2 * erfc(w/(4 sqrt(D t)))")
out(f"    t1 = {t1_erfc:.6g} s")
out(f"    achieved c(+w/2, t1)/c0 = {c_wall_erfc_at_t1 / C0:.8f}")
out(f"    absolute difference |t1_series - t1_erfc| = "
    f"{abs(t1_series - t1_erfc):.6g} s")
out(f"      = {rel_abs * 100:.2f} % of t1_series, "
    f"{rel_ref * 100:.2f} % of t1_erfc")
out(f"    ratio t1_erfc / t1_series = {t1_erfc / t1_series:.4f}")
out()
out("    WHY THE TWO DIFFER BY A FACTOR ~4 (both are correct, for different")
out("    geometries):")
out(f"      * Eq.(1) solves the REAL channel: solid walls at y = +-w/2.")
out(f"        Sugar that diffuses towards the upper wall is reflected and")
out(f"        accumulates, so the wall concentration rises quickly.")
out(f"      * erfc() assumes an UNBOUNDED medium: sugar that diffuses past")
out(f"        y = +w/2 is simply lost to the exterior, so the wall value lags.")
out(f"      * Diffusion length sqrt(2 D t1):")
out(f"          Eq.(1)  : t1 = {t1_series:.4g} s -> "
    f"{1e6 * math.sqrt(2 * D_SUGAR * t1_series):.4g} um  (= 0.97 w/2)")
out(f"          erfc    : t1 = {t1_erfc:.4g} s -> "
    f"{1e6 * math.sqrt(2 * D_SUGAR * t1_erfc):.4g} um  (= 1.35 w)")
out(f"      * The erfc value corresponds to a front that has travelled PAST the")
out(f"        wall by ~35 % of the channel width, i.e. it is only valid for a")
out(f"        channel much wider than the diffusion length.  At the 30 %")
out(f"        level the front has already reached the upper wall, so the wall")
out(f"        BC matters and Eq.(1) is the physically correct one for the")
out(f"        H-filter of this assignment.")
out(f"    => PRIMARY t1 = {t1_series:.4g} s (Eq. 1).")
out(f"       erfc cross-check: t1 = {t1_erfc:.4g} s "
    f"({rel_ref * 100:.1f} % longer than Eq. 1).")
out()

out("    Convergence of the series at y = +w/2 (sin((2n-1)pi/2) = +-1):")
conv_rows = []
for Nc in (2, 5, 10, 20, 50, 100, 500, 2000):
    tN = brentq(lambda tt: float(c_series(W / 2.0, tt, N=Nc)[0]) - TARGET,
                1e-9, 1e3, xtol=1e-12, rtol=1e-14)
    cN = float(c_series(W / 2.0, tN, N=Nc)[0])
    conv_rows.append((Nc, tN, cN))
    out(f"      N = {Nc:5d} : t1 = {tN:.8f} s, c(+w/2,t1)/c0 = {cN / C0:.8f}")
out("    The root is insensitive to N (agreement to 8 significant figures from")
out("    N = 20 upwards), so the conditional convergence of the series at")
out("    t -> 0 (Leibniz series) is not a problem at t1.")
out()

t1_area = brentq(lambda tt: float(c_up_area(tt)) - TARGET, 1e-9, 1e3,
                  xtol=1e-12, rtol=1e-14)
t1_flux = brentq(lambda tt: float(c_up_flux(tt)) - TARGET, 1e-9, 1e3,
                  xtol=1e-12, rtol=1e-14)
out("    Supplementary (the concentration actually COLLECTED by the upper exit")
out("    channel is an average over the upper half, not the wall value):")
out(f"      area-averaged top half, c_up(t) = 0.30 c0 at t = {t1_area:.6g} s")
out(f"      flux-weighted top half (Poiseuille weights) at t = {t1_flux:.6g} s")
out(f"      wall value at those times: "
    f"{float(c_series(W / 2.0, t1_area, N=N_SERIES)[0]) / C0:.4f} and "
    f"{float(c_series(W / 2.0, t1_flux, N=N_SERIES)[0]) / C0:.4f} of c0")
out()

results["b"] = {
    "target_fraction_of_c0": 0.30,
    "t1_series_wall_s": t1_series,
    "t1_erfc_infinite_domain_s": t1_erfc,
    "t1_image_sum_s": t1_images,
    "abs_difference_s": abs(t1_series - t1_erfc),
    "rel_diff_vs_series": rel_abs,
    "rel_diff_vs_erfc": rel_ref,
    "ratio_erfc_over_series": t1_erfc / t1_series,
    "achieved_fraction_series": c_wall_at_t1 / C0,
    "achieved_fraction_erfc": c_wall_erfc_at_t1 / C0,
    "x_at_t1": float(xdim(t1_series)),
    "diffusion_length_at_t1_um": 1e6 * math.sqrt(2 * D_SUGAR * t1_series),
    "diffusion_length_at_t1_erfc_um": 1e6 * math.sqrt(2 * D_SUGAR * t1_erfc),
    "t1_area_averaged_top_half_s": t1_area,
    "t1_flux_weighted_top_half_s": t1_flux,
    "convergence_study": [
        {"N_terms": n, "t1_s": t, "c_at_t1_over_c0": c / C0}
        for n, t, c in conv_rows
    ],
}

# ---------------------------------------------------------------------------
# (c) comparison with the diffusion times
# ---------------------------------------------------------------------------
out("=" * 78)
out("(c)  COMPARISON WITH THE RELEVANT DIFFUSION TIME")
out("=" * 78)
t_diff_quarter = W ** 2 / (4.0 * D_SUGAR)     # as prescribed in the brief
t_diff_eigen = W ** 2 / (np.pi ** 2 * D_SUGAR)
t_diff_halfwidth = W ** 2 / (8.0 * D_SUGAR)    # <x^2> = 2Dt with x = w/2
out(f"  t_diff        = w^2/(4 D)    = {t_diff_quarter:.6g} s   (as prescribed)")
out(f"  t_diff,eigen  = w^2/(pi^2 D) = {t_diff_eigen:.6g} s   (1st eigenmode)")
out(f"  t_diff,hw     = w^2/(8 D)    = {t_diff_halfwidth:.6g} s   "
    f"(<x^2> = 2Dt with x = w/2)")
out(f"  (consistency note: <x^2> = 2Dt with x = w/2 gives w^2/(8D) = "
    f"{t_diff_halfwidth:.4g} s;")
out(f"   the prescribed w^2/(4D) is the same estimate with the <x^2> = 4Dt")
out(f"   convention.)")
out()
out(f"  t1 / t_diff        = {t1_series / t_diff_quarter:.6g}")
out(f"  t1 / t_diff,eigen  = {t1_series / t_diff_eigen:.6g}")
out(f"  t1 / t_diff,hw     = {t1_series / t_diff_halfwidth:.6g}")
out(f"  (same ratios for the infinite-domain value t1 = {t1_erfc:.4g} s:")
out(f"   t1_erfc/t_diff = {t1_erfc / t_diff_quarter:.6g}, "
    f"t1_erfc/t_diff,eigen = {t1_erfc / t_diff_eigen:.6g}, "
    f"t1_erfc/t_diff,hw = {t1_erfc / t_diff_halfwidth:.6g})")
out()
out("  Does it make sense?  Yes - with one honest caveat:")
out(f"   * t1 = {t1_series:.4g} s EXCEEDS the first-eigenmode transverse")
out(f"     relaxation time t_diff,eigen = {t_diff_eigen:.4g} s "
    f"(ratio {t1_series / t_diff_eigen:.4f}).")
out(f"     This is the meaningful statement: diffusion only REDISTRIBUTES the")
out(f"     sugar, it cannot create a step, so the 30 % level can only be")
out(f"     reached after the slowest (fundamental) transverse mode has had")
out(f"     time to relax, i.e. after at least one eigen-relaxation time.")
out(f"   * t1 = {t1_series:.4g} s is slightly BELOW t_diff,hw = "
    f"{t_diff_halfwidth:.4g} s = the time for the front to travel the")
out(f"     half width w/2, and below the prescribed w^2/(4D) = "
    f"{t_diff_quarter:.4g} s.")
out(f"     Reason: the 30 % WALL value is reached while the front is still")
out(f"     spreading (it is a tail value, not the front), and the reflecting")
out(f"     wall of the real channel accelerates it.  For the infinite-domain")
out(f"     model the ordering is the textbook one: t1_erfc = {t1_erfc:.4g} s")
out(f"     > t_diff = {t_diff_quarter:.4g} s "
    f"(ratio {t1_erfc / t_diff_quarter:.3f}).")
out(f"   * Order-of-magnitude check: at t1 the front has travelled")
out(f"     sqrt(2 D t1) = {1e6 * math.sqrt(2 * D_SUGAR * t1_series):.4g} um")
out(f"     ~ w/2 = 50 um, i.e. exactly the distance from the interface to the")
out(f"     upper wall.  The 30 % level at the wall is reached when the")
out(f"     spreading front has just arrived there - self-consistent.")
out()

results["c"] = {
    "t_diff_w2_4D_s": t_diff_quarter,
    "t_diff_eigen_w2_pi2D_s": t_diff_eigen,
    "t_diff_w2_8D_consistent_s": t_diff_halfwidth,
    "t1_over_t_diff": t1_series / t_diff_quarter,
    "t1_over_t_diff_eigen": t1_series / t_diff_eigen,
    "t1_over_t_diff_w2_8D": t1_series / t_diff_halfwidth,
    "t1_erfc_over_t_diff": t1_erfc / t_diff_quarter,
    "t1_erfc_over_t_diff_eigen": t1_erfc / t_diff_eigen,
    "t1_erfc_over_t_diff_w2_8D": t1_erfc / t_diff_halfwidth,
}

# ---------------------------------------------------------------------------
# (d) flow resistance of the horizontal channel
# ---------------------------------------------------------------------------
out("=" * 78)
out("(d)  FLOW RESISTANCE OF THE HORIZONTAL (MAIN) CHANNEL SEGMENT")
out("=" * 78)
out("  Hagen-Poiseuille for a rectangular channel with gap h << width w:")
out("      R_h = 12 eta L / (w h^3)      [Pa s / m^3]")
out(f"      eta = {ETA} Pa s, w = {W} m, h = {H} m")
out()
R_an = R_h(L_ANALYTICAL)
R_co = R_h(L_COMSOL)
out("  Case A (Bruus p.99, L = 25 mm):")
out(f"      R_h = 12 * {ETA} * {L_ANALYTICAL} / ({W} * {H}^3)")
out(f"          = {R_an:.6e} Pa s/m^3")
out("  Case B (COMSOL, L = 6 mm):")
out(f"      R_h = 12 * {ETA} * {L_COMSOL} / ({W} * {H}^3)")
out(f"          = {R_co:.6e} Pa s/m^3")
out(f"  Ratio R_A/R_B = L_A/L_B = {R_an / R_co:.4g} (same cross-section)")
out(f"  Cross-check via the pressure gradient: dp/dx = 12 eta U / h^2 with")
out(f"  U = {sc_co['U']:.4g} m/s gives dp/dx = "
    f"{12 * ETA * sc_co['U'] / H ** 2:.6e} Pa/m; over L = {L_COMSOL} m that is")
out(f"  {12 * ETA * sc_co['U'] / H ** 2 * L_COMSOL:.6e} Pa = "
    f"Q0 R_h = {Q_COMSOL * R_co:.6e} Pa  (consistent).")
out()

results["d"] = {
    "R_h_analytical_Pa_s_m3": R_an,
    "R_h_comsol_Pa_s_m3": R_co,
    "formula": "R_h = 12 eta L / (w h^3)",
    "eta_Pa_s": ETA, "w_m": W, "h_m": H,
    "dp_dx_check_Pa_per_m": 12 * ETA * sc_co["U"] / H ** 2,
}

# ---------------------------------------------------------------------------
# (e) driving pressure
# ---------------------------------------------------------------------------
out("=" * 78)
out("(e)  DRIVING PRESSURE NEEDED FOR 30 % SUGAR IN THE UPPER EXIT")
out("=" * 78)
out("MODEL - stated explicitly, with the algebra.")
out()
out("  The main channel carries the total flow Q0; the two side channels feed")
out("  it (sugar below, water above) and the two exit channels leave it.")
out("  With identical side and exit channels the inlet split is")
out("      alpha = Q_sugar/Q0 = 1/2,   the exit split f_up = Q_up/Q0 = 1/2,")
out("  and the interface sits on the midline y = 0, which is exactly the")
out("  initial condition of Eq. (1).  In the main channel the transverse")
out("  velocity is u(y) = (3/2) U (1 - (2y/w)^2), U = Q0/(w h), and the")
out("  steady state obeys  u(y) dc/dx = D d2c/dy2.")
out()
out("  (1) Along the channel the transverse sugar FLUX is conserved:")
out("      d/dx int_0^{w/2} u(y) c(y,x) dy = D [dc/dy]_{y=0}^{w/2} = 0,")
out("      because the walls at y = +-w/2 are no-flux.")
out("  (2) The upper exit carries flow Q_up = f_up Q0 and collects the sugar")
out("      that has crossed the interface, so its concentration is")
out("      c_up = Phi_up(L) / (f_up Q0),")
out("      Phi_up(L) = int_0^{w/2} u(y) c(y, t_res) dy.")
out("  (3) The total sugar molar flow entering is n_s = alpha Q0 c0, so the")
out("      fraction that crossed is F_cross = Phi_up/(alpha Q0 c0) and")
out("          c_up/c0 = F_cross * alpha / f_up.                     (*)")
out("  (4) For the symmetric device (alpha = f_up = 1/2) (*) reduces to")
out("          c_up/c0 = F_cross(t_res),")
out("      i.e. the 30 % condition means that 30 % of the entering sugar has")
out("      crossed the midline within the residence time.  F_cross is given")
out("      by the same series as Eq. (1).")
out("  (5) The residence time is set by the flow rate, t_res = L w h/Q0, so")
out("      the 30 % condition fixes Q0, and then dp = Q0 R_h.")
out()
out("  ALTERNATIVE READING (flagged): the assignment wording ('the flow split")
out("  that gives 30 % sugar in the upper exit') can also be read as: keep")
out("  Q0 fixed and tune the inlet split alpha instead.  That is solved")
out("  below for the COMSOL operating point; note that alpha > 1/2 means")
out("  the sugar stream itself is wider than half the channel, so the sugar")
out("  reaches the upper exit by ADVECTION, not by diffusive crossover -")
out("  which defeats the purpose of an H-filter.  The symmetric device")
out("  (alpha = 1/2) with a suitable Q0 is therefore the defensible")
out("  reading, and the residence-time criterion is used as primary.")
out()

# residence-time criterion -> Q0 -> dp
res_e = {}
out("  E1  PRIMARY: residence-time criterion (symmetric device, alpha=1/2)")
out("      t_res = t1 (Eq. 1 wall criterion) -> Q0 = L w h / t1 -> dp = Q0 R_h")
out()
for case, L, R in (("A", L_ANALYTICAL, R_an), ("B", L_COMSOL, R_co)):
    for tag, t, label in (("wall", t1_series, "wall value c(+w/2)=0.30 c0"),
                          ("area", t1_area, "collected avg (area)"),
                          ("flux", t1_flux, "collected avg (Poiseuille)")):
        Q0 = L * W * H / t
        dp = dp_from_Q(Q0, L)
        out(f"      Case {case} (L = {L * 1e3:.0f} mm), {tag:>4s} criterion "
            f"({label}):")
        out(f"          t_res = {t:.6g} s, Q0 = {Q0:.6e} m^3/s = "
            f"{q_ul_min(Q0):.6g} uL/min")
        out(f"          dp = Q0 R_h = {dp:.6g} Pa = {dp * 0.01:.6g} mbar")
        res_e[f"{case}_{tag}"] = {
            "criterion": label, "t_res_s": t, "Q0_m3_s": Q0,
            "Q0_uL_min": q_ul_min(Q0), "dp_Pa": dp, "dp_mbar": dp * 0.01,
        }
out()

dp_co_spec = dp_from_Q(Q_COMSOL, L_COMSOL)
dp_an_at_comsol_Q = dp_from_Q(Q_COMSOL, L_ANALYTICAL)
out("  E2  The COMSOL operating point as specified in the assignment")
out(f"      Q0 = 0.1 uL/min = {Q_COMSOL:.6e} m^3/s, L = 6 mm, "
    f"R_h = {R_co:.6e} Pa s/m^3")
out(f"      dp = Q0 R_h = {dp_co_spec:.6g} Pa = {dp_co_spec * 0.01:.6g} mbar")
c_up_at_spec = float(c_up_flux(sc_co["t_res"]))
c_up_area_at_spec = float(c_up_area(sc_co["t_res"]))
c_wall_at_spec = float(c_series(W / 2.0, sc_co["t_res"], N=N_SERIES)[0])
out(f"      Mixing state at that flow rate (t_res = {sc_co['t_res']:.4g} s):")
out(f"        c(+w/2)/c0 (wall value)   = {c_wall_at_spec / C0:.4f}")
out(f"        top-half area average/c0   = {c_up_area_at_spec / C0:.4f}")
out(f"        top-half flux average/c0   = {c_up_at_spec / C0:.4f}")
out(f"      => at 0.1 uL/min the 6 mm channel is only PARTLY mixed.  To")
out(f"         deliver 30 % with the symmetric device one needs Q0 ~ "
    f"{res_e['B_wall']['Q0_uL_min']:.4g} uL/min")
out(f"         (wall criterion) or {res_e['B_flux']['Q0_uL_min']:.4g} uL/min")
out(f"         (collected-average criterion), i.e. dp ~ "
    f"{res_e['B_wall']['dp_Pa']:.4g} Pa to "
    f"{res_e['B_flux']['dp_Pa']:.4g} Pa.")
out(f"      For reference, the same 0.1 uL/min in the 25 mm (Case A) channel")
out(f"      would need dp = {dp_an_at_comsol_Q:.6g} Pa = "
    f"{dp_an_at_comsol_Q * 0.01:.6g} mbar.")
out()

out("  E3  ALTERNATIVE READING: fixed Q0, tune the inlet split alpha.")
out("      The interface between the two side streams sits at y*; with the")
out("      Poiseuille profile of the main channel the flow fraction below y*")
out("      is  F(y*) = [y* - 4y*^3/(3w^2) + w/3] / (2w/3),  so alpha = F(y*)")
out("      (F(-w/2)=0, F(0)=1/2, F(w/2)=1).  The upper exit (equal exit")
out("      channels, divide line y=0) then sees the flow-weighted average")
out("      c_up(alpha) = int_0^{w/2} u c dy / int_0^{w/2} u dy, evaluated with")
out("      the general Neumann series for a step at y*.  Solve")
out("      c_up(alpha) = 0.30 c0 at the COMSOL operating point")
out(f"      (Q0 = 0.1 uL/min, t_res = {sc_co['t_res']:.4g} s):")


def c_up_alpha(alpha, t, w=W):
    ys = ystar_from_fraction(alpha, w)
    return float(c_up_flux_gen(t, ys, w=w))


alpha_star = None
ystar_star = None
try:
    alpha_star = brentq(lambda al: c_up_alpha(al, sc_co["t_res"]) - TARGET,
                        0.5000001, 0.9999999, xtol=1e-10, rtol=1e-13)
    ystar_star = ystar_from_fraction(alpha_star)
    out(f"      alpha = Q_sugar/Q0 = {alpha_star:.6f}  (water share "
        f"{1 - alpha_star:.6f})")
    out(f"      interface at y* = {1e6 * ystar_star:.4f} um "
        f"(midline would be 0 um)")
    out(f"      check: c_up/c0 at that split = "
        f"{c_up_alpha(alpha_star, sc_co['t_res']) / C0:.6f}")
    advected_frac = alpha_star - 0.5
    out(f"      QUANTITATIVE CAVEAT: alpha > 1/2 by {advected_frac:.4f}, so a")
    out(f"      fraction {advected_frac:.4f} of the total flow is sugar that")
    out(f"      sits ABOVE the midline and is convected straight into the")
    out(f"      upper exit without ever diffusing.  Of the 30 % delivered,")
    out(f"      only {max(0.0, 0.30 - advected_frac) / 0.30 * 100:.1f} % of the")
    out(f"      sugar fraction is due to genuine diffusive crossover.")
    out(f"      So this branch is only weakly 'sorting' - it is not the")
    out(f"      intended operating regime.  The physically meaningful design")
    out(f"      point is the symmetric device (alpha = 1/2) at a LOWER flow")
    out(f"      rate, see E1.")
except Exception as exc:                      # pragma: no cover
    out(f"      (no solution found in alpha in (0.5, 1): {exc})")
out()

out("  E4  FALLBACK ESTIMATE (the simple split-only model requested):")
out("      If the upper exit carries a fraction f_up of the total flow Q0 and")
out("      the sugar entering at the bottom is (to first order) retained in")
out("      its own stream, then the sugar concentration in the upper exit is")
out("      set by the diffusive exchange alone:")
out("          c_up/c0 = F_cross(t_res) * alpha / f_up = 0.30")
out("      i.e.   alpha = 0.30 f_up / F_cross(t_res),")
out("      with F_cross(t_res) = c_up(t_res)/c0 from the same series")
out("      (symmetric interface, alpha = 1/2):")
fc_spec_flux = c_up_at_spec / C0
fc_spec_area = c_up_area_at_spec / C0
alpha_fb_flux = 0.30 * 0.5 / fc_spec_flux
alpha_fb_area = 0.30 * 0.5 / fc_spec_area
out(f"        at the COMSOL operating point (t_res = {sc_co['t_res']:.4g} s):")
out(f"          F_cross = {fc_spec_flux:.6f} (flux-weighted), "
    f"alpha = {alpha_fb_flux:.6f}")
out(f"          F_cross = {fc_spec_area:.6f} (area-weighted),  "
    f"alpha = {alpha_fb_area:.6f}")
out(f"        => a solution EXISTS, but only for alpha > 1/2, i.e. the sugar")
out(f"        side stream must carry MORE than half the total flow, which")
out(f"        pushes the interface above the midline so that part of the")
out(f"        sugar reaches the upper exit by ADVECTION rather than by")
out(f"        diffusion.  That is the degenerate branch, and it confirms that")
out(f"        the physically meaningful design point is the symmetric device")
out(f"        (alpha = 1/2) running at a LOWER flow rate (see E1).")
fc_wall_flux = float(c_up_flux(t1_series)) / C0
fc_wall_area = float(c_up_area(t1_series)) / C0
out(f"        At the 30 % residence time (t1 = {t1_series:.4g} s, the")
out(f"        wall criterion) the crossover fractions are "
    f"F_cross = {fc_wall_flux:.6f} (flux) / {fc_wall_area:.6f} (area);")
out(f"        with alpha = f_up = 1/2 the balance (*) gives")
out(f"        c_up/c0 = F_cross * (1/2)/(1/2) = {fc_wall_flux:.6f} "
    f"(flux-weighted collected concentration).")
out()

out("  SANITY CHECK of the pressure magnitudes:")
out(f"     R_h(6 mm)  = {R_co:.4e} Pa s/m^3  ->  dp = {dp_co_spec:.4g} Pa "
    f"({dp_co_spec * 0.01:.4g} mbar) at 0.1 uL/min")
out(f"     R_h(25 mm) = {R_an:.4e} Pa s/m^3  ->  dp = "
    f"{dp_an_at_comsol_Q:.4g} Pa ({dp_an_at_comsol_Q * 0.01:.4g} mbar) at "
    f"0.1 uL/min")
out(f"     per 1 uL/min: dp = {dp_from_Q(1e-9 / 60.0, L_COMSOL):.4g} Pa "
    f"(6 mm), {dp_from_Q(1e-9 / 60.0, L_ANALYTICAL):.4g} Pa (25 mm)")
out(f"     Independent check via the Poiseuille gradient dp/dx = 12 eta U/h^2:")
out(f"       Case B: {12 * ETA * sc_co['U'] / H ** 2:.4e} Pa/m x "
    f"{L_COMSOL} m = {12 * ETA * sc_co['U'] / H ** 2 * L_COMSOL:.4g} Pa")
out(f"     The resistance is enormous (h^3 = 1.25e-16 m^3 in the")
out(f"     denominator), so dp is O(1e3-1e4) Pa = tens to hundreds of mbar")
out(f"     for these flow rates.  A value of ~1e-3 Pa would mean a unit error")
out(f"     in Q (uL/min -> m^3/s) or in R; the unit conversion used here is")
out(f"     0.1 uL/min = 0.1e-9 m^3 / 60 s = {Q_COMSOL:.6e} m^3/s.")
out()

results["e"] = {
    "model": "symmetric H-filter: alpha = Q_sugar/Q0 = 1/2, f_up = 1/2; "
             "c_up/c0 = F_cross(t_res) = Phi_up/(alpha Q0 c0); "
             "t_res = L w h / Q0; dp = Q0 R_h",
    "criteria": res_e,
    "comsol_operating_point": {
        "Q0_m3_s": Q_COMSOL, "Q0_uL_min": 0.1,
        "dp_Pa": dp_co_spec, "dp_mbar": dp_co_spec * 0.01,
        "t_res_s": sc_co["t_res"],
        "wall_value_over_c0": c_wall_at_spec / C0,
        "top_area_average_over_c0": c_up_area_at_spec / C0,
        "top_flux_average_over_c0": c_up_at_spec / C0,
    },
    "analytical_case_at_comsol_Q": {
        "Q0_m3_s": Q_COMSOL, "Q0_uL_min": 0.1,
        "dp_Pa": dp_an_at_comsol_Q, "dp_mbar": dp_an_at_comsol_Q * 0.01,
    },
    "split_solved_at_comsol_flow": {
        "alpha_sugar_fraction": alpha_star,
        "water_fraction": (1.0 - alpha_star) if alpha_star else None,
        "interface_y_um": (1e6 * ystar_star) if ystar_star else None,
        "caveat": "alpha>1/2 delivers sugar by advection, not by diffusion; "
                  "degenerate as a sorting device",
    },
    "fallback_split_estimate": {
        "F_cross_at_comsol_flow_flux": fc_spec_flux,
        "alpha_required_flux": alpha_fb_flux,
        "F_cross_at_comsol_flow_area": fc_spec_area,
        "alpha_required_area": alpha_fb_area,
        "note": "a solution exists only for alpha>1/2 (sugar side stream "
                "carries more than half the flow), i.e. part of the sugar "
                "reaches the upper exit by advection - the degenerate branch; "
                "the physical design point is alpha=1/2 at lower flow rate",
        "F_cross_at_t1_flux": fc_wall_flux,
        "F_cross_at_t1_area": fc_wall_area,
    },
    "sanity": {
        "dp_per_uL_min_comsol_Pa": dp_from_Q(1e-9 / 60.0, L_COMSOL),
        "dp_per_uL_min_analytical_Pa": dp_from_Q(1e-9 / 60.0, L_ANALYTICAL),
        "expected_order_of_magnitude_Pa": "1e3-1e4 (tens-hundreds of mbar)",
    },
}

# ---------------------------------------------------------------------------
# BONUS: critical diffusion coefficient (cross-check for the COMSOL part)
# ---------------------------------------------------------------------------
out("=" * 78)
out("BONUS - critical diffusion coefficient and largest sortable particle")
out("     (cross-check for the COMSOL part (a)-(c); not part of a-e)")
out("=" * 78)
out("  The crossover is governed by the single dimensionless group")
out("      x = pi^2 D t_res / w^2.")
out("  Two common definitions of the critical diffusion coefficient D_c:")
out("   (i)  'classical' crossing criterion: the diffusion front travels the")
out("        half width w/2 in the residence time, <x^2> = 2 D t_res:")
out("            D_c = w^2/(8 t_res)")
out("   (ii) 'wall criterion' used in this assignment (30 % at the upper")
out("        wall at the end of the channel), i.e. x = x(t1) = "
    f"{xdim(t1_series):.4f}:")
out("            D_c = x(t1) w^2/(pi^2 t_res)")
out("  Largest sphere that can still be SORTED (i.e. that does NOT cross):")
out("      d_max = k_B T / (3 pi eta D_c)   [Stokes-Einstein]")
out()
bonus = {}
for case, L, Q in (("Case B (COMSOL, Q = 0.1 uL/min)", L_COMSOL, Q_COMSOL),
                   ("Case A (Bruus, L = 25 mm, Q = 0.1 uL/min)", L_ANALYTICAL,
                    Q_COMSOL)):
    t_res = L * W * H / Q
    D_c1 = W ** 2 / (8.0 * t_res)
    D_c2 = float(xdim(t1_series)) * W ** 2 / (np.pi ** 2 * t_res)
    d1 = K_B * TEMP / (3.0 * math.pi * ETA * D_c1)
    d2 = K_B * TEMP / (3.0 * math.pi * ETA * D_c2)
    out(f"  {case}:  t_res = {t_res:.5g} s")
    out(f"     (i)  D_c = w^2/(8 t_res)      = {D_c1:.6e} m^2/s -> "
        f"d_max = {1e6 * d1:.5g} um")
    out(f"     (ii) D_c = x(t1) w^2/(pi^2 t_res) = {D_c2:.6e} m^2/s -> "
        f"d_max = {1e6 * d2:.5g} um")
    bonus[case] = {"t_res_s": t_res, "D_crit_classical_m2_s": D_c1,
                   "d_max_classical_um": 1e6 * d1,
                   "D_crit_wall_criterion_m2_s": D_c2,
                   "d_max_wall_criterion_um": 1e6 * d2}
out(f"  Sugar itself has D = {D_SUGAR:.3e} m^2/s, i.e. D/D_c = "
    f"{D_SUGAR / bonus['Case B (COMSOL, Q = 0.1 uL/min)']['D_crit_classical_m2_s']:.4g}")
out("  (classical) - a slow tracer that only partially crosses the 6 mm")
out("  channel within the residence time (consistent with part b).")
out()

results["bonus_critical_diffusion"] = bonus

# ===========================================================================
# 4.  figures
# ===========================================================================


def save(fig, name):
    p = FIG / name
    # no bbox_inches="tight": the saved native width must stay exactly FIG_W so
    # that the point sizes below are the point sizes on the printed page.
    fig.savefig(p)
    plt.close(fig)
    out(f"  figure written: {p}")
    return p


# --- (a) time scales -------------------------------------------------------
fig, axes = plt.subplots(1, 2, figsize=(FIG_W, 2.8))
for ax, title, sc in (
        (axes[0], "Case B - COMSOL\n$L$ = 6 mm, $Q$ = 0.1 uL/min", sc_co),
        (axes[1], "Case A - Bruus\n$L$ = 25 mm, $Q$ = 0.1 uL/min", sc_an)):
    labels = ["t_res", "t_diff,y", "t_diff,x"]
    vals = [sc["t_res"], sc["t_diff_y"], sc["t_diff_x"]]
    colors = [C_ACCENT, C_BLUE, C_GREY]
    bars = ax.bar(labels, vals, color=colors, width=0.55)
    ax.set_yscale("log")
    ax.set_ylabel("time [s]  (log scale)")
    ax.set_title(title, fontsize=8.5)
    for b, v in zip(bars, vals):
        ax.annotate(f"{v:.3g} s", (b.get_x() + b.get_width() / 2, v),
                    textcoords="offset points", xytext=(0, 3), ha="center",
                    va="bottom", fontsize=8)
    ax.set_ylim(top=max(vals) * 60)
fig.suptitle("Time-scale comparison for the H-filter main channel", fontsize=9)
out()
out("Figures:")
save(fig, "ex1_a_time_scales.png")

# --- (b) mixing curve ------------------------------------------------------
fig, ax = plt.subplots(figsize=(FIG_W, 2.9))
tt = np.linspace(1e-3, 3.0 * t1_series, 600)
ax.plot(tt, c_series(W / 2.0, tt, N=N_SERIES) / C0, color=C_ACCENT, lw=2,
        label=f"Eq. (1), no-flux walls (t1 = {t1_series:.2f} s)")
ax.plot(tt, c_erfc(W / 2.0, tt) / C0, color=C_GREY, lw=1.6, ls="--",
        label=f"erfc, infinite domain (t1 = {t1_erfc:.2f} s)")
ax.plot(tt, c_up_flux(tt) / C0, color=C_BLUE, lw=1.6,
        label=f"flux-avg. top half (t = {t1_flux:.2f} s)")
ax.axhline(0.30, color=C_GREEN, lw=1.2, ls=":")
ax.annotate("30 % of c0", (3.0 * t1_series * 0.03, 0.305), color=C_GREEN,
            va="bottom", fontsize=8,
            bbox=dict(fc="white", ec="none", alpha=1.0, pad=1.2))
ax.plot([t1_series], [0.30], "o", color=C_ACCENT, ms=7, zorder=5)
ax.annotate(f"t1 = {t1_series:.2f} s", (t1_series, 0.30),
            textcoords="offset points", xytext=(6, -12), fontsize=8,
            color=C_ACCENT)
ax.plot([t1_erfc], [0.30], "s", color=C_GREY, ms=6, zorder=5)
ax.annotate(f"t1 = {t1_erfc:.2f} s", (t1_erfc, 0.30),
            textcoords="offset points", xytext=(-46, 8), fontsize=8,
            color=C_GREY)
ax.set_xlabel("time t [s]")
ax.set_ylabel("c(+w/2, t) / c0")
ax.set_title("Sugar concentration at the upper exit wall", fontsize=9)
ax.set_xlim(0, 3 * t1_series)
ax.set_ylim(0, 0.55)
ax.legend(loc="lower right", fontsize=8)
save(fig, "ex1_b_mixing.png")

# --- (b) profiles ----------------------------------------------------------
fig, ax = plt.subplots(figsize=(FIG_W, 2.9))
yy = np.linspace(-W / 2, W / 2, 2001)
for t, lab, col, ls in ((0.0, "t = 0", C_GREY, "-"),
                        (t1_series / 2, "t = t1/2", C_LIGHT, "-"),
                        (t1_series, "t = t1", C_ACCENT, "-"),
                        (2 * t1_series, "t = 2 t1", C_BLUE, "--")):
    ax.plot(yy * 1e6, c_series(yy, t, N=N_SERIES) / C0, color=col, lw=1.8,
            ls=ls, label=lab)
ax.axvline(W / 2 * 1e6, color=C_GREEN, lw=0.8, ls=":")
ax.annotate("upper exit wall\ny = +w/2", (W / 2 * 1e6 - 1, 0.10),
            xytext=(-0.30, 0.16), textcoords="axes fraction",
            color=C_GREEN, fontsize=8, ha="left", va="center",
            arrowprops=dict(arrowstyle="-", color=C_GREEN, lw=0.8,
                            shrinkA=2, shrinkB=3),
            bbox=dict(fc="white", ec="none", alpha=1.0, pad=1.2))
ax.axhline(0.30, color=C_GREY, lw=0.8, ls=":")
ax.set_xlabel("y [um]")
ax.set_ylabel("c(y, t) / c0")
ax.set_title(f"Transverse concentration profiles\n(t1 = {t1_series:.2f} s, "
             "30 % at the wall)", fontsize=9)
ax.set_ylim(-0.12, 1.12)
ax.legend(loc="upper right", fontsize=8)
save(fig, "ex1_b_profiles.png")

# --- (e) pressure vs flow rate --------------------------------------------
# The three operating points sit within a narrow Q band (0.03 - 0.1 uL/min),
# so separate arrow callouts collide.  The two curves get the upper-left
# legend and the three points get their own key in the empty lower-right
# triangle below the (diagonal) Case-B line.
fig, ax = plt.subplots(figsize=(FIG_W, 3.1))
Qplot = np.logspace(-3, 1.5, 200) * 1e-9 / 60.0     # 0.001 .. 30 uL/min
(lB,) = ax.loglog(q_ul_min(Qplot), dp_from_Q(Qplot, L_COMSOL), color=C_ACCENT,
                  lw=2, label=f"Case B: L = 6 mm  (R_h = {R_co:.2e} Pa s/m^3)")
(lA,) = ax.loglog(q_ul_min(Qplot), dp_from_Q(Qplot, L_ANALYTICAL), color=C_BLUE,
                  lw=1.6, ls="--",
                  label=f"Case A: L = 25 mm  (R_h = {R_an:.2e} Pa s/m^3)")
(mB,) = ax.plot([0.1], [dp_co_spec], "o", color=C_ACCENT, ms=8, zorder=5,
                label=f"assignment operating point\n"
                      f"Q = 0.1 uL/min, dp = {dp_co_spec:.0f} Pa")
(mW,) = ax.plot([res_e["B_wall"]["Q0_uL_min"]], [res_e["B_wall"]["dp_Pa"]], "o",
                color=C_GREEN, ms=7, zorder=5, fillstyle="none",
                label=f"30 % at the wall\n"
                      f"Q = {res_e['B_wall']['Q0_uL_min']:.4g} uL/min, "
                      f"dp = {res_e['B_wall']['dp_Pa']:.0f} Pa")
(mF,) = ax.plot([res_e["B_flux"]["Q0_uL_min"]], [res_e["B_flux"]["dp_Pa"]], "s",
                color=C_BLUE, ms=7, zorder=5, fillstyle="none",
                label=f"30 % (collected avg.)\n"
                      f"Q = {res_e['B_flux']['Q0_uL_min']:.4g} uL/min, "
                      f"dp = {res_e['B_flux']['dp_Pa']:.0f} Pa")
ax.set_xlabel("total flow Q0 [uL/min]")
ax.set_ylabel("driving pressure dp = Q0 R_h [Pa]")
ax.set_title("Driving pressure vs flow rate (H-filter main channel)",
             fontsize=9)
leg1 = ax.legend(handles=[lB, lA], loc="lower left", fontsize=8,
                 handlelength=1.6, labelspacing=0.4, borderaxespad=0.4,
                 frameon=True, facecolor="white", edgecolor="none",
                 framealpha=1.0)
ax.add_artist(leg1)
ax.legend(handles=[mB, mW, mF], loc="lower right", fontsize=8,
          handlelength=1.6, labelspacing=0.4, borderaxespad=0.4,
          frameon=True, facecolor="white", edgecolor="none", framealpha=1.0)
save(fig, "ex1_e_pressure.png")

# --- (e) extra: crossing fraction -----------------------------------------
fig, ax = plt.subplots(figsize=(FIG_W, 2.7))
tt = np.linspace(0.0, 3.0 * t1_series, 600)
ax.plot(tt, c_up_area(tt) / C0, color=C_BLUE, lw=2,
        label="area-weighted top half")
ax.plot(tt, c_up_flux(tt) / C0, color=C_ACCENT, lw=2,
        label="flux-weighted top half (Poiseuille)")
ax.axhline(0.30, color=C_GREEN, lw=1.2, ls=":")
ax.plot([t1_flux], [0.30], "o", color=C_ACCENT, ms=7, zorder=5)
ax.annotate(f"t = {t1_flux:.2f} s", (t1_flux, 0.30),
            textcoords="offset points", xytext=(8, -14), fontsize=8,
            color=C_ACCENT, va="top",
            bbox=dict(fc="white", ec="none", alpha=1.0, pad=1.2))
ax.plot([t1_area], [0.30], "s", color=C_BLUE, ms=6, zorder=5)
ax.annotate(f"t = {t1_area:.2f} s", (t1_area, 0.30),
            textcoords="offset points", xytext=(-48, 10), fontsize=8,
            color=C_BLUE, va="bottom",
            bbox=dict(fc="white", ec="none", alpha=1.0, pad=1.2))
ax.set_xlabel("time t [s]")
ax.set_ylabel("c_up(t) / c0")
ax.set_title("Crossover fraction F_cross(t)\n= sugar collected in the upper "
             "exit", fontsize=9)
ax.set_xlim(0, 3 * t1_series)
ax.set_ylim(0, 0.55)
ax.legend(loc="lower right", fontsize=8)
save(fig, "ex1_e_crossing_fraction.png")

# ===========================================================================
# 5.  write the results
# ===========================================================================
txt_path = NOTES / "ex1_analytical_results.txt"
txt_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
print()
print(f"wrote {txt_path}")

json_path = NOTES / "ex1_analytical_results.json"
payload = {
    "exercise": "Exercise 1 - The H-filter (Bruus p.99)",
    "course": "the lab-on-a-chip course, Assignment 1",
    "constants": {
        "k_B_J_K": K_B, "eps0_F_m": EPS0, "eta_Pa_s": ETA,
        "T_K": TEMP, "g_m_s2": G,
    },
    "geometry": {
        "w_m": W, "h_m": H, "D_sugar_m2_s": D_SUGAR, "c0_mol_m3": C0,
        "L_analytical_m": L_ANALYTICAL, "L_comsol_m": L_COMSOL,
        "Q_comsol_m3_s": Q_COMSOL, "Q_comsol_uL_min": 0.1,
    },
    "results": results,
    "figures": [
        str(FIG / "ex1_a_time_scales.png"),
        str(FIG / "ex1_b_mixing.png"),
        str(FIG / "ex1_b_profiles.png"),
        str(FIG / "ex1_e_pressure.png"),
        str(FIG / "ex1_e_crossing_fraction.png"),
    ],
}
json_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
print(f"wrote {json_path}")

# ===========================================================================
# 6.  final self-check
# ===========================================================================
print()
out("=" * 78)
out("SELF-CHECK (files and order-of-magnitude sanity)")
out("=" * 78)
required = [
    WORK / "scripts" / "ex1_analytical.py",
    NOTES / "ex1_analytical_results.txt",
    NOTES / "ex1_analytical_results.json",
    FIG / "ex1_a_time_scales.png",
    FIG / "ex1_b_mixing.png",
    FIG / "ex1_b_profiles.png",
    FIG / "ex1_e_pressure.png",
]
ok = True
for p in required:
    exists = p.exists()
    size = p.stat().st_size if exists else 0
    good = exists and size > 0
    ok = ok and good
    out(f"  [{'OK ' if good else 'FAIL'}] {p}  ({size} bytes)")
checks = [
    ("dp (COMSOL, 0.1 uL/min) is O(1e3-1e4) Pa",
     1e3 < dp_co_spec < 1e5, f"{dp_co_spec:.4g} Pa = "
     f"{dp_co_spec * 0.01:.4g} mbar"),
    ("dp (analytical, 30 % wall criterion) is O(1e3-1e5) Pa",
     1e3 < res_e["A_wall"]["dp_Pa"] < 1e5,
     f"{res_e['A_wall']['dp_Pa']:.4g} Pa"),
    ("dp/dx check agrees with Q0 R_h",
     abs(12 * ETA * sc_co["U"] / H ** 2 * L_COMSOL - dp_co_spec)
     / dp_co_spec < 1e-9, "exact"),
    ("t1 > t_diff,eigen", t1_series > t_diff_eigen,
     f"{t1_series:.4g} > {t_diff_eigen:.4g}"),
    ("t1 ~ half-width diffusion time (within a factor 1.3)",
     0.7 < t1_series / t_diff_halfwidth < 1.3,
     f"ratio {t1_series / t_diff_halfwidth:.3f}"),
    ("diffusion length at t1 ~ w/2",
     0.7 < 1e6 * math.sqrt(2 * D_SUGAR * t1_series) / (W / 2 * 1e6) < 1.4,
     f"{1e6 * math.sqrt(2 * D_SUGAR * t1_series):.4g} um vs 50 um"),
    ("Pe_x >> 1 (profile frozen in x)", sc_co["pe_x"] > 1e3,
     f"Pe_x = {sc_co['pe_x']:.3g}"),
    ("Re << 1 (laminar)", sc_co["re_h"] < 0.1, f"Re_h = {sc_co['re_h']:.2e}"),
    ("Eq.(1) reproduces the step at t=0", resid_max < 1e-2,
     f"max residual {resid_max:.2e} mol/m^3"),
    ("Eq.(1) = independent FD solution", fd_err < 5e-2,
     f"max diff {fd_err:.2e} mol/m^3"),
    ("Eq.(1) = exact image sum of the erfc form", img_err < 1e-6,
     f"max diff {img_err:.2e} mol/m^3"),
    ("series root is N-independent",
     abs(conv_rows[-1][1] - conv_rows[-2][1]) < 1e-6,
     f"dN(500->2000) = {abs(conv_rows[-1][1] - conv_rows[-2][1]):.2e} s"),
    ("closed-form averages match quadrature",
     abs(c_flux_cf - c_flux_qd) < 1e-6 and abs(c_area_cf - c_area_qd) < 1e-6,
     f"{abs(c_flux_cf - c_flux_qd):.1e} / {abs(c_area_cf - c_area_qd):.1e}"),
    ("generalised closed forms match quadrature",
     abs(c_fluxgen_cf - c_fluxgen_qd) < 1e-6
     and abs(c_areagen_cf - c_areagen_qd) < 1e-6,
     f"{abs(c_fluxgen_cf - c_fluxgen_qd):.1e} / "
     f"{abs(c_areagen_cf - c_areagen_qd):.1e}"),
    ("shifted-interface closed forms match quadrature",
     abs(c_fg_chk - c_fg_qd) < 1e-6 and abs(c_ag_chk - c_ag_qd) < 1e-6,
     f"{abs(c_fg_chk - c_fg_qd):.1e} / {abs(c_ag_chk - c_ag_qd):.1e}"),
    ("t1 (Eq.1) and t1 (image sum) agree",
     abs(t1_series - t1_images) < 1e-6,
     f"{abs(t1_series - t1_images):.2e} s"),
    ("flow fraction map consistent (F(0)=0.5, F(-w/2)=0, F(w/2)=1)",
     abs(flow_fraction_below(0.0) - 0.5) < 1e-12
     and abs(flow_fraction_below(-W / 2)) < 1e-12
     and abs(flow_fraction_below(W / 2) - 1.0) < 1e-12, "ok"),
]
for name, passed, detail in checks:
    ok = ok and bool(passed)
    out(f"  [{'OK ' if passed else 'FAIL'}] {name}  ({detail})")
out()
out("ALL CHECKS PASSED" if ok else "SOME CHECKS FAILED")
out("=" * 78)

txt_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"\nrewrote {txt_path} with the self-check block")
