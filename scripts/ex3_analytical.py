"""
Exercise 3 — Particle motion under the influence of a deterministic force.
Analytical model + figures for the lab-chip LabChip Assignment 1 report.

Conventions (all derived, see notes/ex3_derivations.md):
  * k_B      = 1.380649e-23 J/K            T = 293.15 K (20 degC)
  * eps0     = 8.8541878128e-12 F/m         eta = 1e-3 Pa*s
  * E is the PEAK amplitude; the time-averaged AC DEP force is
        F_DEP = pi * a^3 * eps0 * eps_r,m * Re{K} * grad|E|^2
    (equivalent to COMSOL's  2*pi*a^3*eps0*eps_r*Re{K}*grad|E_rms|^2
     because |E_rms|^2 = |E_peak|^2 / 2).  This is the convention of the
     course textbook "Manipulation by electrical fields" eq. (3.22).
  * Stokes drag coefficient f = 3*pi*eta*d_p = 6*pi*eta*a
  * Stokes-Einstein D = k_B*T / f

Every formula used here is re-derived independently of the textbook and the
closed forms are cross-checked against brute-force complex arithmetic.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
from scipy.optimize import brentq
from scipy.integrate import solve_ivp
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import (LogLocator, LogFormatterSciNotation,
                                AutoLocator, NullLocator, NullFormatter,
                                ScalarFormatter)

# ----------------------------------------------------------------------------
# constants
# ----------------------------------------------------------------------------
K_B = 1.380649e-23
EPS0 = 8.8541878128e-12
ETA = 1.0e-3
T = 293.15
G = 9.80665

WORK = Path(r"<project>")
FIG = WORK / "figures"
NOTES = WORK / "notes"
FIG.mkdir(parents=True, exist_ok=True)
NOTES.mkdir(parents=True, exist_ok=True)

# The report places every figure at 0.85\textwidth = 4.23 in wide.  Drawing at
# that width and saving WITHOUT a tight bounding box keeps the saved native
# width equal to the placement width, so the nominal point sizes below are the
# point sizes on the printed page (effective pt = size * 4.23 / 4.23).
FIG_W = 4.23

# the course-inspired but restrained palette
C_ACCENT = "#E50000"   # the course red
C_BLUE = "#1f4e79"
C_GREEN = "#2e7d32"
C_GREY = "#555555"

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

results: dict = {}


# ----------------------------------------------------------------------------
# physics helpers
# ----------------------------------------------------------------------------
def cm_factor(eps_r_p, eps_r_m, sig_p, sig_m, f):
    """Clausius-Mossotti factor, exact complex form.

    K = (eps*_p - eps*_m)/(eps*_p + 2 eps*_m),  eps* = eps - j*sigma/omega
    with ABSOLUTE permittivity eps = eps0 * eps_r.  Dividing numerator and
    denominator by eps0 gives the relative form used here, but the
    conductivity term must then be scaled by eps0 as well:

        eps*_p/eps0 = eps_r,p - j*sigma_p/(omega*eps0)

    Returned as (Re K, Im K).  eps_* are RELATIVE permittivities, sigma in S/m.
    """
    w = 2.0 * math.pi * np.asarray(f, dtype=float)
    ep = eps_r_p - 1j * sig_p / (w * EPS0)
    em = eps_r_m - 1j * sig_m / (w * EPS0)
    k = (ep - em) / (ep + 2.0 * em)
    return np.real(k), np.imag(k)


def cm_factor_closed(eps_r_p, eps_r_m, sig_p, sig_m, f):
    """Closed-form Re{K} derived from scratch (no complex arithmetic).

        Re K = [ w^2 (ep-em)(ep+2em) + (sp-sm)(sp+2sm) ]
               / [ w^2 (ep+2em)^2      + (sp+2sm)^2 ]

    with ep, em the ABSOLUTE permittivities.  Reduces to (sp-sm)/(sp+2sm) at
    low frequency and (ep-em)/(ep+2em) at high frequency.
    """
    w = 2.0 * math.pi * np.asarray(f, dtype=float)
    ep = eps_r_p * EPS0
    em = eps_r_m * EPS0
    a, b = ep - em, eps_r_p * 0 + (ep + 2 * em)
    c, d = sig_p - sig_m, sig_p + 2 * sig_m
    return (w**2 * a * b + c * d) / (w**2 * b**2 + d**2)


def stokes_drag(a: float) -> float:
    """Stokes drag coefficient for a sphere of radius a [N s/m]."""
    return 3.0 * math.pi * ETA * (2.0 * a)


def stokes_einstein(a: float) -> float:
    """Diffusion coefficient of a sphere of radius a [m^2/s]."""
    return K_B * T / stokes_drag(a)


def dep_force(a: float, eps_r_p, eps_r_m, sig_p, sig_m, f_hz, grad_E2) -> float:
    """Time-averaged AC dielectrophoretic force magnitude [N].

    F = pi a^3 eps0 eps_r,m Re{K} |grad|E|^2|   (peak-amplitude convention)
    """
    re_k = float(cm_factor(eps_r_p, eps_r_m, sig_p, sig_m, f_hz)[0])
    return math.pi * a**3 * EPS0 * eps_r_m * re_k * grad_E2


def rms_step_1d(D: float, dt: float) -> float:
    """Einstein RMS displacement per degree of freedom."""
    return math.sqrt(2.0 * D * dt)


def threshold_force(f_drag: float, D: float, dt: float) -> float:
    """Force needed to displace a particle by 3 standard deviations in dt.

    Overdamped Stokes response: the particle reaches terminal velocity
    v_t = F/f almost instantly (relaxation time m/f ~ 1e-8 s << dt), so
        x(t) = v_t * t = (F/f) * t.
    Require (F/f)*dt = 3*Delta,  Delta = sqrt(2 D dt):
        F = 3 f sqrt(2 D) / sqrt(dt) = 3 sqrt(2 k_B T f / dt)
    """
    return 3.0 * math.sqrt(2.0 * K_B * T * f_drag / dt)


# ============================================================================
# 3.1  thermal velocity and the time to diffuse 5 um
# ============================================================================
def exercise_3_1():
    a = 150e-9                     # m
    rho_p = 1050.0                 # kg/m^3
    m = rho_p * (4.0 / 3.0) * math.pi * a**3
    v_1d = math.sqrt(K_B * T / m)
    v_3d = math.sqrt(3.0 * K_B * T / m)
    D = stokes_einstein(a)
    d_target = 5e-6

    # ballistic (wrong) estimate and the correct diffusive ones
    t_ballistic = d_target / v_3d
    t_1d_rms = d_target**2 / (2.0 * D)          # Delta = d_target, 1 DOF
    t_3d_rms = d_target**2 / (6.0 * D)          # |Delta| = d_target, 3 DOF
    t_1d_95 = (d_target / 2.0) ** 2 / (2.0 * D)  # 2-sigma (95%) in 1 DOF
    t_3d_95 = (d_target / 2.0) ** 2 / (6.0 * D)

    results["ex3_1"] = {
        "radius_m": a, "mass_kg": m,
        "v_th_1d_m_s": v_1d, "v_th_3d_m_s": v_3d,
        "D_particle_m2_s": D,
        "t_ballistic_s": t_ballistic,
        "t_rms_1d_s": t_1d_rms, "t_rms_3d_s": t_3d_rms,
        "t_95pct_1d_s": t_1d_95, "t_95pct_3d_s": t_3d_95,
    }
    return results["ex3_1"]


# ============================================================================
# 3.2  threshold force vs particle radius and observation time
# ============================================================================
def exercise_3_2():
    radii = np.logspace(np.log10(50e-9), np.log10(2e-6), 60)
    dt_arr = np.logspace(-4, 1, 60)

    fig, ax = plt.subplots(1, 2, figsize=(4.78, 3.35))
    fig.subplots_adjust(left=0.095, right=0.985, top=0.94, bottom=0.20,
                        wspace=0.30)

    for dt, col in zip([1e-3, 1e-2, 1e-1], [C_BLUE, C_ACCENT, C_GREEN]):
        F = np.array([threshold_force(stokes_drag(a), stokes_einstein(a), dt) for a in radii])
        ax[0].loglog(radii * 1e9, F, color=col, lw=1.8, label=rf"$\delta t$ = {dt:g} s")
    ax[0].loglog(radii * 1e9,
                 threshold_force(stokes_drag(radii[0]), stokes_einstein(radii[0]), 1e-3)
                 * np.sqrt(radii / radii[0]), color="0.55", ls="--", lw=1.0,
                 label=r"$\propto \sqrt{\alpha}$")
    ax[0].set_xlabel(r"particle radius $\alpha$  [nm]")
    ax[0].set_ylabel(r"threshold force $F_{\rm th}$  [N]")
    ax[0].set_title("Threshold force vs.\nparticle radius", fontsize=8.5)
    ax[0].legend(fontsize=7.5, loc="upper center", bbox_to_anchor=(0.5, -0.20),
                 ncol=2, handlelength=1.4, columnspacing=1.2, frameon=False)

    for a, col in zip([75e-9, 150e-9, 300e-9, 1500e-9],
                      [C_GREY, C_BLUE, C_ACCENT, C_GREEN]):
        f_d = stokes_drag(a)
        D = stokes_einstein(a)
        F = np.array([threshold_force(f_d, D, dt) for dt in dt_arr])
        ax[1].loglog(dt_arr, F, color=col, lw=1.8,
                     label=rf"$\alpha$ = {a*1e9:g} nm")
    a0 = 150e-9
    F0 = threshold_force(stokes_drag(a0), stokes_einstein(a0), 1e-3)
    ax[1].loglog(dt_arr, F0 * np.sqrt(1e-3 / dt_arr), color="0.55", ls="--", lw=1.0,
                 label=r"$\propto \delta t^{-1/2}$")
    ax[1].set_xlabel(r"observation time $\delta t$  [s]")
    ax[1].set_ylabel(r"threshold force $F_{\rm th}$  [N]")
    ax[1].set_title("Threshold force vs.\nobservation time", fontsize=8.5)
    ax[1].legend(fontsize=7.5, loc="upper center", bbox_to_anchor=(0.5, -0.20),
                 ncol=3, handlelength=1.4, columnspacing=1.0, frameon=False)

    fig.savefig(FIG / "ex3_2_threshold_force.png")
    plt.close(fig)

    # representative numbers
    tab = []
    for a in [150e-9, 300e-9, 1500e-9]:
        f_d = stokes_drag(a)
        D = stokes_einstein(a)
        for dt in [1e-3, 1e-2, 1e-1]:
            tab.append({"alpha_nm": a * 1e9, "dt_s": dt,
                        "f_drag_Ns_m": f_d, "D_m2_s": D,
                        "F_threshold_N": threshold_force(f_d, D, dt),
                        "v_terminal_m_s": threshold_force(f_d, D, dt) / f_d})
    results["ex3_2"] = {"table": tab,
                         "scaling": "F_th ∝ sqrt(alpha) * delta_t^(-1/2)"}
    return results["ex3_2"]


# ============================================================================
# 3.3  Clausius-Mossotti vs frequency, and required observation time
# ============================================================================
def exercise_3_3():
    eps_r_p, sig_p = 2.5, 0.0185
    eps_r_m, sig_m = 80.0, 0.016
    a = 150e-9
    rho_p = 1050.0

    f = np.logspace(2, 9, 400)
    re_closed = cm_factor_closed(eps_r_p, eps_r_m, sig_p, sig_m, f)
    re_exact, im_exact = cm_factor(eps_r_p, eps_r_m, sig_p, sig_m, f)
    err = np.max(np.abs(re_closed - re_exact))
    assert err < 1e-10, f"closed-form Re(K) mismatch: {err}"

    # Maxwell-Wagner turnover (Re K = 0).
    # From the closed form: w^2 (ep-em)(ep+2em) + (sp-sm)(sp+2sm) = 0
    #   => w_to = sqrt( (sp-sm)(sp+2sm) / [eps0^2 (em-ep)(ep+2em)] )
    # The denominator is written with (em-ep) so it is positive whenever the
    # conductivity and permittivity contrasts have opposite signs.
    omega_to = math.sqrt((sig_p - sig_m) * (sig_p + 2.0 * sig_m) /
                          (EPS0**2 * (eps_r_m - eps_r_p) * (eps_r_p + 2.0 * eps_r_m)))
    f_to = omega_to / (2.0 * math.pi)
    # independent numerical check: sign change of Re{K} in the scanned array
    sign_change = np.where(np.sign(re_exact[:-1]) != np.sign(re_exact[1:]))[0]
    assert len(sign_change) == 1, f"expected one Re{{K}} sign change, got {len(sign_change)}"
    i_sc = sign_change[0]
    f_to_num = math.sqrt(f[i_sc] * f[i_sc + 1])
    assert abs(f_to_num - f_to) / f_to < 0.02, (f_to, f_to_num)
    # limits
    re_dc = (sig_p - sig_m) / (sig_p + 2 * sig_m)
    re_hf = (eps_r_p - eps_r_m) / (eps_r_p + 2 * eps_r_m)

    fig, ax = plt.subplots(1, 2, figsize=(FIG_W, 3.7))
    # x is logarithmic (the frequency spans decades) but y MUST be linear:
    # Re{K} changes sign at the turnover, and a logarithmic y-axis cannot
    # show a negative value, so the sign change - the whole point of the
    # panel - was invisible and the curve looked positive throughout.
    ax[0].semilogx(f, re_exact, color=C_BLUE, lw=2.0, label=r"$\mathrm{Re}\{K\}$")
    ax[0].axhline(re_dc, color=C_GREY, ls=":", lw=1.2,
                  label=rf"DC limit = {re_dc:.3f}")
    ax[0].axhline(re_hf, color=C_GREY, ls="--", lw=1.2,
                  label=rf"HF limit = {re_hf:.3f}")
    ax[0].axhline(0.0, color="black", lw=0.8, alpha=0.6)
    ax[0].axvline(f_to, color=C_ACCENT, ls="-.", lw=1.2,
                  label=rf"turnover $f_{{to}}$ = {f_to/1e6:.2f} MHz")
    ax[0].axvline(1e6, color=C_GREEN, ls=":", lw=1.5, label="1 MHz")
    # mark the zero crossing explicitly
    ax[0].plot([f_to], [0.0], "o", color=C_ACCENT, ms=7, zorder=5)
    ax[0].set_xlabel("frequency $f$  [Hz]", fontsize=8.5)
    ax[0].set_ylabel(r"$\mathrm{Re}\{K\}$", fontsize=8.5)
    ax[0].set_title("Clausius-Mossotti factor\nvs. frequency", fontsize=8.5)
    # white frame: the 1 MHz and turnover markers run vertically through the
    # whole panel, so the legend needs an opaque background
    # The legend used to sit BELOW the axes (bbox_to_anchor=(0.5, -0.34)),
    # where it was clipped by the bottom of the figure and its last entry was
    # cut off in print.  It now sits INSIDE the axes, in the corner the curve
    # does not visit: Re{K} falls from the top-left to the bottom-right, so
    # the lower-left corner is free.
    ax[0].legend(fontsize=7.5, loc="lower left",
                 handlelength=1.5, labelspacing=0.3, frameon=True,
                 facecolor="white", edgecolor="none", framealpha=1.0)
    # linear y axis (see the comment above): a log locator/formatter would
    # leave the axis blank now that the curve goes negative.
    ax[0].yaxis.set_major_locator(AutoLocator())
    ax[0].yaxis.set_minor_locator(NullLocator())
    ax[0].yaxis.set_major_formatter(ScalarFormatter())
    # the turnover is the headline of this panel, so call it out on the plot
    ax[0].annotate(f"zero crossing\n{f_to/1e6:.2f} MHz",
                   xy=(f_to, 0.0),
                   xytext=(0.97, 0.12), textcoords="axes fraction",
                   fontsize=8, color=C_ACCENT, ha="right", va="center",
                   arrowprops=dict(arrowstyle="->", color=C_ACCENT, lw=0.9,
                                   shrinkB=3),
                   bbox=dict(fc="white", ec="none", alpha=1.0, pad=1.5))

    # required observation time vs distance from the electrodes (f = 1 MHz)
    f_op = 1e6
    re_k_1mhz = float(cm_factor(eps_r_p, eps_r_m, sig_p, sig_m, f_op)[0])
    grad_list = [(0.0, 1e19), (15e-6, 1e15), (50e-6, 1e12), (100e-6, 1e9)]
    f_drag = stokes_drag(a)
    D = stokes_einstein(a)

    rows = []
    for dist, grad in grad_list:
        F = dep_force(a, eps_r_p, eps_r_m, sig_p, sig_m, f_op, grad)
        dt = 18.0 * K_B * T * f_drag / F**2
        # 10x larger particle
        a10 = 10 * a
        f10 = stokes_drag(a10)
        F10 = dep_force(a10, eps_r_p, eps_r_m, sig_p, sig_m, f_op, grad)
        dt10 = 18.0 * K_B * T * f10 / F10**2
        rows.append({"distance_um": dist * 1e6, "grad_E2_V2_m3": grad,
                     "F_DEP_N": F, "dt_s": dt, "dt_10x_s": dt10,
                     "F_DEP_10x_N": F10})

    ax[1].loglog([r["distance_um"] for r in rows if r["distance_um"] > 0],
                 [r["dt_s"] for r in rows if r["distance_um"] > 0],
                 "o-", color=C_BLUE, lw=1.8, ms=6, label=r"$\alpha$ = 150 nm")
    ax[1].loglog([r["distance_um"] for r in rows if r["distance_um"] > 0],
                 [r["dt_10x_s"] for r in rows if r["distance_um"] > 0],
                 "s-", color=C_ACCENT, lw=1.8, ms=6, label=r"$\alpha$ = 1.5 $\mu$m")
    ax[1].set_xlabel("distance from electrode\nplane  [$\\mu$m]", fontsize=8.5)
    ax[1].set_ylabel("required observation\ntime $\\delta t$  [s]", fontsize=8.5)
    ax[1].set_title("Observable DEP\nmotion at $f$ = 1 MHz", fontsize=8.5)
    # only three distances are plotted: label exactly those, otherwise the
    # log locator crowds the narrow axis with minor tick labels
    dists = [r["distance_um"] for r in rows if r["distance_um"] > 0]
    ax[1].set_xticks(dists)
    ax[1].set_xticklabels([f"{d:g}" for d in dists], fontsize=8)
    ax[1].xaxis.set_minor_formatter(NullFormatter())
    ax[1].legend(fontsize=7.5, loc="upper left",
                 handlelength=1.5, labelspacing=0.3, frameon=True,
                 facecolor="white", edgecolor="none", framealpha=1.0)

    fig.savefig(FIG / "ex3_3_cm_and_observation_time.png")
    plt.close(fig)

    results["ex3_3"] = {
        "eps_r_p": eps_r_p, "sig_p": sig_p, "eps_r_m": eps_r_m, "sig_m": sig_m,
        "ReK_closed_vs_exact_maxerr": err,
        "turnover_frequency_Hz": f_to, "ReK_DC_limit": re_dc, "ReK_HF_limit": re_hf,
        "ReK_at_1MHz": re_k_1mhz,
        "ReK_at_10MHz": float(cm_factor(eps_r_p, eps_r_m, sig_p, sig_m, 1e7)[0]),
        "rows": rows,
    }
    return results["ex3_3"]


# ============================================================================
# 3.4  equilibrium height of a particle in the DEP-FFF electrode geometry
# ============================================================================
def exercise_3_4():
    eps_r_p, sig_p = 2.5, 0.0185
    eps_r_m, sig_m = 80.0, 0.016
    rho_m = 1000.0
    f_op = 10e6

    particles = [
        (1, 150e-9, 1050.0),
        (2, 300e-9, 1050.0),
        (3, 150e-9, 1200.0),
        (4, 300e-9, 1200.0),
        (5, 1500e-9, 1050.0),
    ]

    def y_eq(V0: float, d: float, rho_p: float, re_k: float) -> float:
        """Equilibrium height above the electrode plane [m].

        From |F_DEP| = |F_buoy| with
            grad|E|^2 = (32/pi) (V0^2/d^3) exp(-pi y / d)
            F_DEP     = pi a^3 eps0 eps_r,m Re{K} grad|E|^2
            F_buoy    = (4/3) pi a^3 (rho_p - rho_m) g
        The particle radius cancels; only the density contrast and the
        particle's dielectric properties survive.
        """
        num = 24.0 * EPS0 * eps_r_m * abs(re_k) * V0**2
        den = math.pi * d**3 * (rho_p - rho_m) * G
        return (d / math.pi) * math.log(num / den)

    re_k_10mhz = float(cm_factor(eps_r_p, eps_r_m, sig_p, sig_m, f_op)[0])

    # (c) V0 = 10 V, d = 10 um
    V0, d = 10.0, 10e-6
    rows = []
    for pid, a, rho_p in particles:
        rows.append({
            "particle": pid, "radius_nm": a * 1e9, "rho_p": rho_p,
            "y_eq_um": y_eq(V0, d, rho_p, re_k_10mhz) * 1e6,
        })

    # (d) separation between the two density groups
    y_lo = rows[0]["y_eq_um"]           # rho_p = 1050
    y_hi = rows[2]["y_eq_um"]           # rho_p = 1200
    sep = y_lo - y_hi

    # separation as a function of V0 and d (explicit expression)
    def sep_expr(V0, d):
        r = rho_m + 50.0
        return y_eq(V0, d, r, re_k_10mhz) - y_eq(V0, d, r + 150.0, re_k_10mhz)

    # The left panel gets more than half the width: five bars in a narrow
    # panel cannot carry a two-line tick label without the labels touching.
    fig, ax = plt.subplots(1, 2, figsize=(FIG_W, 2.9),
                           gridspec_kw={"width_ratios": [1.45, 1]})
    # 5 bars: the tick label stays narrow, so only the particle number and
    # the radius go on the axis.  The density is encoded by the bar colour
    # and spelled out in the y-label.
    labels = [f"#{r['particle']}\n{int(r['radius_nm'])} nm" for r in rows]
    ax[0].bar(range(len(rows)), [r["y_eq_um"] for r in rows],
              color=[C_BLUE if r["rho_p"] == 1050.0 else C_ACCENT for r in rows])
    ax[0].set_xticks(range(len(rows)))
    ax[0].set_xticklabels(labels, fontsize=7.5)
    ax[0].set_ylabel("equilibrium height $y_{\\rm eq}$  [$\\mu$m]\n"
                     "blue: $\\rho_p$ = 1050, red: 1200 kg/m$^3$", fontsize=8.5)
    ax[0].set_title("Equilibrium height\n($V_0$ = 10 V, $d$ = 10 $\\mu$m)",
                    fontsize=8.5)
    for i, r in enumerate(rows):
        ax[0].text(i, r["y_eq_um"], f"{r['y_eq_um']:.1f}", ha="center",
                   va="bottom", fontsize=8)

    V0s = np.linspace(1.0, 30.0, 200)
    for dd, col in zip([5e-6, 10e-6, 20e-6], [C_GREEN, C_BLUE, C_ACCENT]):
        ax[1].plot(V0s, [sep_expr(V, dd) * 1e6 for V in V0s], color=col, lw=1.8,
                   label=rf"$d$ = {dd*1e6:g} $\mu$m")
    ax[1].set_xlabel(r"voltage $V_0$  [V]", fontsize=8.5)
    ax[1].set_ylabel(r"separation $\Delta y$  [$\mu$m]", fontsize=8.5)
    ax[1].set_title("Separation vs. voltage\nand electrode size", fontsize=8)
    ax[1].legend(fontsize=8, loc="center right", handlelength=1.5,
                 labelspacing=0.35)

    fig.savefig(FIG / "ex3_4_equilibrium.png")
    plt.close(fig)

    # (e) trajectory: integrate the vertical + downstream motion for particle 1
    traj = particle_trajectory(re_k_10mhz, eps_r_m, V0, d, rho_p=1050.0,
                               a=150e-9, y0=50e-6, T_end=200.0)
    fig, ax = plt.subplots(1, 2, figsize=(4.78, 3.05))
    fig.subplots_adjust(left=0.095, right=0.985, top=0.93, bottom=0.17,
                        wspace=0.32)
    ax[0].plot(traj["t"], traj["y"] * 1e6, color=C_BLUE, lw=2.0)
    ax[0].axhline(traj["y_eq"] * 1e6, color=C_ACCENT, ls="--", lw=1.2,
                  label=rf"$y_{{eq}}$ = {traj['y_eq']*1e6:.1f} $\mu$m")
    ax[0].set_xlabel("time  [s]", fontsize=8.5)
    ax[0].set_ylabel("height above\nelectrode plane  [$\\mu$m]", fontsize=8.5)
    ax[0].set_title("Vertical approach\nto equilibrium", fontsize=8.5)
    ax[0].annotate(rf"$y_{{eq}}$ = {traj['y_eq']*1e6:.1f} $\mu$m",
                    xy=(traj["t"][-1], traj["y_eq"] * 1e6),
                    xytext=(0.52, 0.13), textcoords="axes fraction",
                    fontsize=8, color=C_ACCENT, ha="left", va="center",
                    arrowprops=dict(arrowstyle="-", color=C_ACCENT, lw=0.8,
                                    shrinkA=2, shrinkB=3),
                    bbox=dict(fc="white", ec="none", alpha=1.0, pad=1.5))

    ax[1].plot(traj["x"] * 1e3, traj["y"] * 1e6, color=C_ACCENT, lw=2.0)
    ax[1].set_xlabel("downstream distance\n$x$  [mm]", fontsize=8.5)
    ax[1].set_ylabel(r"height  [$\mu$m]", fontsize=8.5)
    ax[1].set_title("Trajectory in the\n$x$--$y$ plane", fontsize=8.5)
    fig.savefig(FIG / "ex3_4_trajectory.png")
    plt.close(fig)

    results["ex3_4"] = {
        "ReK_at_10MHz": re_k_10mhz,
        "V0": V0, "d_um": d * 1e6,
        "rows": rows,
        "separation_um": sep,
        "separation_expression":
            "dy = (d/pi) * ln[ (rho_p1 - rho_m)/(rho_p2 - rho_m) ]",
        "trajectory": {k: (v if not isinstance(v, np.ndarray) else v.tolist())
                       for k, v in traj.items() if k in ("t", "y", "x", "y_eq")},
        "trajectory_summary": {k: traj[k] for k in
                               ("y_eq_um", "tau_relax_s", "t_to_99pct_s",
                                "x_at_99pct_mm", "u_avg_m_s")},
    }
    return results["ex3_4"]


def particle_trajectory(re_k, eps_r_m, V0, d, rho_p, a, y0, T_end=200.0,
                        n_out=400):
    """Integrate the vertical Stokes balance and the downstream drift.

    Vertical (z is height above the electrode plane):
        m dz/dt = F_DEP(y) - F_buoy - f (dz/dt - u_par(y))
    In the overdamped limit and with the DEP force decaying exponentially:
        dz/dt = (F_DEP(y) - F_buoy)/f  (relative to the local fluid velocity)
    Downstream: dx/dt = u(y) = 1.5 * U_avg * (1 - (2y/H - 1)^2)
    """
    H = 100e-6                     # channel height (m)
    Q = 1e-9 / 3600.0              # 1 uL/h   (1 uL = 1e-9 m^3)
    A = (100e-6) * (100e-6)        # cross-section
    U_avg = Q / A
    f_drag = stokes_drag(a)

    def grad_E2(y):
        return (32.0 / math.pi) * (V0**2 / d**3) * math.exp(-math.pi * y / d)

    def F_DEP(y):
        return math.pi * a**3 * EPS0 * eps_r_m * abs(re_k) * grad_E2(y)

    F_buoy = (4.0 / 3.0) * math.pi * a**3 * (rho_p - 1000.0) * G

    def dy(y):
        return (F_DEP(y) - F_buoy) / f_drag

    def u_par(y):
        return 1.5 * U_avg * (1.0 - (2.0 * y / H - 1.0) ** 2)

    # equilibrium: solve F_DEP(y) = F_buoy
    def bal(y):
        return F_DEP(y) - F_buoy
    y_eq = brentq(bal, 1e-9, 50e-6)

    # relaxation time  tau = f / |dF/dy|_eq  (dF/dy = -(pi/d) F_DEP)
    tau = f_drag / abs(-math.pi / d * F_DEP(y_eq))

    # The approach to equilibrium is exponential with time constant tau, so the
    # window has to span several tau for the 99 % criterion to be reached.
    T_end = max(T_end, 15.0 * tau)

    t_eval = np.linspace(0.0, T_end, n_out)
    # x is the DOWNSTREAM DRIFT, i.e. the integral of u(y(t)):
    #     dx/dt = u(y(t))   ->   x(t) = int_0^t u(y(t')) dt'
    # It is NOT u(y(t))*t: the velocity changes as the particle sinks.
    sol = solve_ivp(lambda t, s: [dy(s[0]), u_par(s[0])], [0.0, T_end], [y0, 0.0],
                    t_eval=t_eval, method="RK45", rtol=1e-8, atol=1e-12)
    y = sol.y[0]
    x = sol.y[1]

    # time to close 99% of the gap between the release height and equilibrium
    span = y0 - y_eq
    t99 = float("nan")
    if abs(span) > 1e-15:
        for i in range(len(sol.t)):
            if abs(y[i] - y_eq) <= 0.01 * abs(span):
                t99 = float(sol.t[i])
                break
    idx99 = int(np.argmin(np.abs(y - y_eq))) if t99 == t99 else len(sol.t) - 1
    x99 = float(x[idx99])

    return {"t": sol.t, "y": y, "x": x, "y_eq": y_eq,
            "y_eq_um": y_eq * 1e6, "tau_relax_s": tau, "t_to_99pct_s": t99,
            "x_at_99pct_mm": x99 * 1e3, "u_avg_m_s": U_avg,
            "y0_um": y0 * 1e6, "F_buoy_N": F_buoy,
            "F_DEP_at_y0_N": F_DEP(y0)}


# ============================================================================
if __name__ == "__main__":
    r31 = exercise_3_1()
    r32 = exercise_3_2()
    r33 = exercise_3_3()
    r34 = exercise_3_4()

    with open(NOTES / "ex3_results.json", "w", encoding="utf-8") as fh:
        json.dump(results, fh, indent=2, default=float)

    # ---- console summary (also written to a text file for the report) ----
    L = []
    add = L.append
    add("EXERCISE 3 — ANALYTICAL RESULTS")
    add("=" * 72)
    add("")
    add("3.1  thermal velocity / diffusion time")
    add(f"  particle radius            a = {r31['radius_m']*1e9:.0f} nm")
    add(f"  particle mass              m = {r31['mass_kg']:.4e} kg")
    add(f"  thermal velocity (1 DOF)   v = {r31['v_th_1d_m_s']:.4f} m/s")
    add(f"  thermal velocity (3 DOF)   v = {r31['v_th_3d_m_s']:.4f} m/s")
    add(f"  diffusion coefficient      D = {r31['D_particle_m2_s']:.4e} m^2/s")
    add(f"  ballistic time for 5 um    t = {r31['t_ballistic_s']:.3e} s   (INVALID: random walk)")
    add(f"  diffusive time, 1 DOF rms  t = {r31['t_rms_1d_s']:.3f} s")
    add(f"  diffusive time, 3 DOF rms  t = {r31['t_rms_3d_s']:.3f} s")
    add(f"  diffusive time, 1 DOF 95% t = {r31['t_95pct_1d_s']:.3f} s")
    add(f"  diffusive time, 3 DOF 95% t = {r31['t_95pct_3d_s']:.3f} s")
    add("")
    add("3.2  threshold force")
    add(f"  {'alpha[nm]':>10} {'dt[s]':>8} {'f[N s/m]':>12} {'F_th[N]':>12} {'v_t[m/s]':>12}")
    for row in r32["table"]:
        add(f"  {row['alpha_nm']:>10.0f} {row['dt_s']:>8.3g} {row['f_drag_Ns_m']:>12.4e} "
            f"{row['F_threshold_N']:>12.4e} {row['v_terminal_m_s']:>12.4e}")
    add("")
    add("3.3  Clausius-Mossotti")
    add(f"  Re{{K}} closed-form vs complex-arithmetic max error: {r33['ReK_closed_vs_exact_maxerr']:.3e}")
    add(f"  turnover frequency f_to = {r33['turnover_frequency_Hz']:.4g} Hz")
    add(f"  DC limit  Re{{K}} = {r33['ReK_DC_limit']:.5f}")
    add(f"  HF limit  Re{{K}} = {r33['ReK_HF_limit']:.5f}")
    add(f"  Re{{K}}(1 MHz)  = {r33['ReK_at_1MHz']:.5f}")
    add(f"  Re{{K}}(10 MHz) = {r33['ReK_at_10MHz']:.5f}")
    add(f"  {'dist[um]':>9} {'grad|E|^2':>12} {'F_DEP[N]':>12} {'dt[s]':>12} {'dt(10x)[s]':>12}")
    for row in r33["rows"]:
        add(f"  {row['distance_um']:>9.0f} {row['grad_E2_V2_m3']:>12.3g} "
            f"{row['F_DEP_N']:>12.4e} {row['dt_s']:>12.4e} {row['dt_10x_s']:>12.4e}")
    add("")
    add("3.4  equilibrium height (V0 = 10 V, d = 10 um, f = 10 MHz)")
    add(f"  Re{{K}}(10 MHz) = {r34['ReK_at_10MHz']:.5f}")
    add(f"  {'particle':>9} {'a[nm]':>7} {'rho_p':>7} {'y_eq[um]':>11}")
    for row in r34["rows"]:
        add(f"  {row['particle']:>9d} {row['radius_nm']:>7.0f} {row['rho_p']:>7.0f} {row['y_eq_um']:>11.3f}")
    add(f"  separation (1050 vs 1200 kg/m^3): {r34['separation_um']:.3f} um")
    ts = r34["trajectory_summary"]
    add(f"  trajectory: y_eq = {ts['y_eq_um']:.2f} um, tau_relax = {ts['tau_relax_s']:.3e} s, "
        f"t99 = {ts['t_to_99pct_s']:.3e} s, x99 = {ts['x_at_99pct_mm']:.4g} mm, "
        f"U_avg = {ts['u_avg_m_s']:.5f} m/s")
    text = "\n".join(L)
    print(text)
    (NOTES / "ex3_results.txt").write_text(text, encoding="utf-8")
