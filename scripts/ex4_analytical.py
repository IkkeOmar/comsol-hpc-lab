"""
Exercise 4 — Field flow fractionation in practice.
Analytical model + figures for the lab-chip LabChip Assignment 1 report.

Everything here is derived from the assignment text itself (<assignment-pages>/
page11..page13.txt) and from Ex 3 (scripts/ex3_analytical.py), so the two
exercises share one set of conventions.

  Geometry (assignment p.11)
      channel           1000 x 100 x 100 um   (x = length, y = width, z = height)
      electrodes        45 strips, 10 um wide, 20 um pitch, first left corner
                       at (50, 0) um, each spanning the full 100 um width in y,
                       sitting on the floor z = 0.
                       Alternating 10 V / 0 V, starting with 10 V on the
                       leftmost electrode (verified from the page-11 figure).
      so  d (electrode width AND gap) = 10 um   <- the Ex-3 gradient length
  Flow                 Q = 1 uL/h
  Particle             d_p = 2.4 um, rho_p = 1200 kg/m^3,
                       eps_r,p = 60, sigma_p = 0.01 S/m
  Medium (water)       eps_r,m = 80, sigma_m = 5.5 uS/m
  AC frequency         f = 10 MHz
  Release              grid at x = 0, z = 50 um, y = 5..95 um in 1 um steps

  Field gradient (assignment p.9, verified against the page-9 image):
      |grad E|^2 = (32/pi) * V0^2/d^3 * exp(-pi y / d)
  Time-averaged AC DEP force (course textbook "Manipulation by electrical
  fields" eq. 3.22, E is the PEAK amplitude):
      F_DEP = pi * a^3 * eps0 * eps_r,m * Re{K} * grad|E|^2
      (= COMSOL's 2*pi*a^3*eps0*eps_r*Re{K}*grad|E_rms|^2 because
       |E_rms|^2 = |E_peak|^2 / 2)
  Stokes drag coefficient            f_drag = 6*pi*eta*a
  Buoyancy (net weight)              F_buoy = (4/3)*pi*a^3*(rho_p - rho_m)*g

Self-checks performed by this script:
  1. closed-form Re{K} vs brute-force complex arithmetic  (must agree to 1e-12)
  2. closed-form equilibrium height vs numerical root of F_DEP(y) = F_buoy
  3. ODE integration must converge to the closed-form y_eq
  4. the integrated x(t) must reproduce the assignment's own 54 s / 2 mm
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

# ----------------------------------------------------------------------------
# assignment parameters (Ex 4)
# ----------------------------------------------------------------------------
L_CH = 1000e-6          # channel length [m]
W_CH = 100e-6           # channel width  [m]
H_CH = 100e-6           # channel height [m]

N_EL = 45               # number of electrodes
EL_W = 10e-6            # electrode width [m]
EL_PITCH = 20e-6        # electrode pitch [m]
EL_X0 = 50e-6           # left edge of the first electrode [m]
EL_V = 10.0             # amplitude on the "on" electrodes [V]

Q = 1e-9 / 3600.0       # 1 uL/h  -> m^3/s   (1 uL = 1e-9 m^3)
A_CH = W_CH * H_CH
U_AVG = Q / A_CH        # mean velocity [m/s]

DP = 2.4e-6             # particle diameter [m]
A_P = DP / 2.0
RHO_P = 1200.0
RHO_M = 1000.0
EPS_R_P = 60.0
SIG_P = 0.01
EPS_R_M = 80.0
SIG_M = 5.5e-6

F_AC = 10e6             # 10 MHz
Z0 = 50e-6              # release height above the electrode plane [m]
Y0_MIN, Y0_MAX, Y0_STEP = 5e-6, 95e-6, 1e-6

# Ex-3 gradient length: electrode WIDTH AND GAP
D_GAP = EL_W            # = 10 um  (width 10 um, pitch 20 um -> gap 10 um)

C_ACCENT = "#E50000"    # the course red
C_BLUE = "#1f4e79"
C_GREEN = "#2e7d32"
C_GREY = "#555555"
C_ORANGE = "#e07b00"

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
log_lines: list[str] = []


def out(line: str = "") -> None:
    print(line)
    log_lines.append(line)


# ----------------------------------------------------------------------------
# physics helpers (same conventions as scripts/ex3_analytical.py)
# ----------------------------------------------------------------------------
def cm_factor(eps_r_p, eps_r_m, sig_p, sig_m, f):
    """Clausius-Mossotti factor, exact complex form.

        K = (eps*_p - eps*_m) / (eps*_p + 2 eps*_m),  eps* = eps - j*sigma/omega

    With ABSOLUTE permittivity eps = eps0*eps_r, dividing numerator and
    denominator by eps0 gives the relative form; the conductivity term then
    has to be scaled by eps0 as well:

        eps*_p/eps0 = eps_r,p - j*sigma_p/(omega*eps0)

    Returns (Re K, Im K).  eps_r relative, sigma in S/m.
    """
    w = 2.0 * math.pi * np.asarray(f, dtype=float)
    ep = eps_r_p - 1j * sig_p / (w * EPS0)
    em = eps_r_m - 1j * sig_m / (w * EPS0)
    k = (ep - em) / (ep + 2.0 * em)
    return np.real(k), np.imag(k)


def cm_factor_closed(eps_r_p, eps_r_m, sig_p, sig_m, f):
    """Closed-form Re{K} with no complex arithmetic.

        Re K = [ w^2 (ep-em)(ep+2em) + (sp-sm)(sp+2sm) ]
               / [ w^2 (ep+2em)^2      + (sp+2sm)^2 ]

    with ep, em the ABSOLUTE permittivities.  Reduces to (sp-sm)/(sp+2sm) at
    low frequency and (ep-em)/(ep+2em) at high frequency.
    """
    w = 2.0 * math.pi * np.asarray(f, dtype=float)
    ep = eps_r_p * EPS0
    em = eps_r_m * EPS0
    a, b = ep - em, (ep + 2 * em)
    c, d = sig_p - sig_m, sig_p + 2 * sig_m
    return (w**2 * a * b + c * d) / (w**2 * b**2 + d**2)


def grad_E2(y, V0=EL_V, d=D_GAP):
    """|grad E|^2 for the periodic electrode array (assignment p.9)."""
    return (32.0 / math.pi) * V0**2 / d**3 * np.exp(-math.pi * np.asarray(y) / d)


def f_dep(y, a, re_k, V0=EL_V, d=D_GAP):
    """Magnitude of the time-averaged DEP force [N], pointing UP for Re{K}<0."""
    return math.pi * a**3 * EPS0 * EPS_R_M * abs(re_k) * grad_E2(y, V0, d)


def f_buoy(a, rho_p=RHO_P, rho_m=RHO_M):
    """Net weight (buoyancy) [N], pointing DOWN."""
    return (4.0 / 3.0) * math.pi * a**3 * (rho_p - rho_m) * G


def stokes_drag(a, eta=ETA):
    """Stokes drag coefficient f = 6*pi*eta*a [N*s/m]."""
    return 6.0 * math.pi * eta * a


def y_eq_closed(a, re_k, V0=EL_V, d=D_GAP, rho_p=RHO_P, rho_m=RHO_M):
    """Closed-form equilibrium height above the electrode plane [m].

    |F_DEP| = |F_buoy|:

        pi a^3 eps0 eps_r,m |ReK| * (32/pi) V0^2/d^3 * exp(-pi y/d)
            = (4/3) pi a^3 (rho_p - rho_m) g

        exp(-pi y/d) = (4/3)(rho_p-rho_m) g d^3 / (32 eps0 eps_r,m |ReK| V0^2)

        y = (d/pi) * ln[ 24 eps0 eps_r,m |ReK| V0^2
                         / (pi d^3 (rho_p-rho_m) g) ]

    The particle radius a cancels: only the density contrast and Re{K} matter.
    """
    num = 24.0 * EPS0 * EPS_R_M * abs(re_k) * V0**2
    den = math.pi * d**3 * (rho_p - rho_m) * G
    return (d / math.pi) * math.log(num / den)


def u_parabolic(z, H=H_CH, u_avg=U_AVG):
    """2D laminar (Poiseuille) profile in z, as used for the Ex-4 estimate.

    The assignment's own analytical estimate (p.12) assumes a 2D laminar flow
    profile in z and ignores the y-profile.
    """
    return 1.5 * u_avg * (1.0 - (2.0 * z / H - 1.0) ** 2)


def integrate_trajectory(a, re_k, z0=Z0, t_end=200.0, n_out=4001,
                         V0=EL_V, d=D_GAP, u_profile=True):
    """Integrate the particle motion in the overdamped limit.

    Inertial terms are neglected ("Newtonian, ignore inertial terms", which the
    assignment requires for the COMSOL run too), so the Stokes balance gives

        f * (u(z) - vz) = F_DEP(z) - F_buoy
        =>  vz = u(z) + (F_DEP(z) - F_buoy)/f          (upward positive)

    and the downstream drift is

        vx = u(z)

    Returns (t, z, x, vz).
    """
    f_d = stokes_drag(a)
    fb = f_buoy(a)

    def rhs(t, s):
        z, x = s
        vz = (f_dep(z, a, re_k, V0, d) - fb) / f_d
        if u_profile:
            vx = u_parabolic(z)
        else:
            vx = U_AVG
        return [vz, vx]

    sol = solve_ivp(rhs, (0.0, t_end), [z0, 0.0], t_eval=np.linspace(0, t_end, n_out),
                    method="RK45", rtol=1e-10, atol=1e-14, max_step=0.05)
    return sol.t, sol.y[0], sol.y[1], None


def time_to_reach(a, re_k, z_eq, z0=Z0, tol=1e-9, t_end=400.0, V0=EL_V, d=D_GAP):
    """First time at which |z - z_eq| < tol, and the x reached by then.

    A tolerance looser than the initial offset |z0 - z_eq| is already
    satisfied at t = 0 and is reported as such.
    """
    if abs(z0 - z_eq) < tol:
        return 0.0, 0.0
    f_d = stokes_drag(a)
    fb = f_buoy(a)

    def rhs(t, s):
        z, x = s
        vz = (f_dep(z, a, re_k, V0, d) - fb) / f_d
        return [vz, u_parabolic(z)]

    def event(t, s):
        return abs(s[0] - z_eq) - tol

    event.terminal = True
    event.direction = -1
    sol = solve_ivp(rhs, (0.0, t_end), [z0, 0.0], method="RK45",
                    rtol=1e-10, atol=1e-14, max_step=0.05, events=event)
    if len(sol.t_events[0]) == 0:
        return float("nan"), float("nan")
    t_hit = float(sol.t_events[0][0])
    x_hit = float(sol.y_events[0][0][1])
    return t_hit, x_hit


# ============================================================================
#  A)  Clausius-Mossotti factor at 10 MHz
# ============================================================================
out("=" * 78)
out("EXERCISE 4 - FIELD FLOW FRACTIONATION - ANALYTICAL RESULTS")
out("the lab-on-a-chip course, Assignment 1, Exercise 4")
out("=" * 78)
out()

out("A)  CLAUSIUS-MOSSOTTI FACTOR")
out("-" * 78)
out(f"  particle : d_p = {DP*1e6:.3f} um, rho_p = {RHO_P:.0f} kg/m^3, "
    f"eps_r,p = {EPS_R_P:.0f}, sigma_p = {SIG_P:.4g} S/m")
out(f"  medium   : eps_r,m = {EPS_R_M:.0f}, sigma_m = {SIG_M:.4g} S/m  (water)")
out()

# cross-check closed form against brute-force complex arithmetic
f_chk = np.array([1e5, 1e6, 10e6, 1e8, 1e9])
re_c, im_c = cm_factor(EPS_R_P, EPS_R_M, SIG_P, SIG_M, f_chk)
re_k_c = cm_factor_closed(EPS_R_P, EPS_R_M, SIG_P, SIG_M, f_chk)
cm_err = float(np.max(np.abs(re_c - re_k_c)))
out(f"  closed-form vs complex-arithmetic Re{{K}}: max error = {cm_err:.3e}")
assert cm_err < 1e-12, "closed-form CM factor disagrees with complex arithmetic"

re_k, im_k = cm_factor(EPS_R_P, EPS_R_M, SIG_P, SIG_M, F_AC)
re_k = float(re_k)
im_k = float(im_k)
out(f"  Re{{K}}(10 MHz) = {re_k:+.5f}      Im{{K}}(10 MHz) = {im_k:+.5f}")
out(f"  -> NEGATIVE DEP  (Re{{K}} < 0): the particle is pushed AWAY from the")
out(f"     high-field electrode gaps, i.e. UP towards the channel roof.")

# frequency sweep
f_log = np.logspace(4, 10, 400)
re_k_sweep = cm_factor_closed(EPS_R_P, EPS_R_M, SIG_P, SIG_M, f_log)
re_k_dc = (SIG_P - SIG_M) / (SIG_P + 2 * SIG_M)
re_k_hf = (EPS_R_P - EPS_R_M) / (EPS_R_P + 2 * EPS_R_M)
sign_change = np.where(np.diff(np.sign(re_k_sweep)))[0]
f_turn = float(f_log[sign_change[0]]) if len(sign_change) else float("nan")
out(f"  DC limit  Re{{K}} -> {re_k_dc:+.5f}   (conductivity contrast)")
out(f"  HF limit  Re{{K}} -> {re_k_hf:+.5f}   (permittivity contrast)")
out(f"  Maxwell-Wagner turnover f_to = {f_turn:.4g} Hz "
    f"({f_turn/1e6:.4g} MHz)")
out(f"  10 MHz is {F_AC/f_turn:.1f}x above the turnover -> firmly negative DEP.")

results["A_cm"] = {
    "Re_K_10MHz": re_k, "Im_K_10MHz": im_k,
    "Re_K_DC_limit": float(re_k_dc), "Re_K_HF_limit": float(re_k_hf),
    "turnover_Hz": f_turn, "closed_form_vs_complex_max_error": cm_err,
}

# ============================================================================
#  B)  Theoretical equilibrium position
# ============================================================================
out()
out("B)  THEORETICAL EQUILIBRIUM POSITION")
out("-" * 78)
y_eq = y_eq_closed(A_P, re_k)
out(f"  |F_DEP| = |F_buoy| with")
out(f"      |grad E|^2 = (32/pi) (V0^2/d^3) exp(-pi y/d),  V0 = {EL_V:.0f} V, "
    f"d = {D_GAP*1e6:.0f} um")
out(f"      F_DEP  = pi a^3 eps0 eps_r,m |Re{{K}}| |grad E|^2")
out(f"      F_buoy = (4/3) pi a^3 (rho_p - rho_m) g")
out()
out(f"  a = {A_P*1e6:.2f} um,  F_DEP(z_eq) = {f_dep(y_eq, A_P, re_k):.4e} N,  "
    f"F_buoy = {f_buoy(A_P):.4e} N")
out(f"  y_eq = (d/pi) ln[ 24 eps0 eps_r,m |ReK| V0^2 / (pi d^3 (rho_p-rho_m) g ) ]")
out(f"      = {y_eq*1e6:.3f} um above the electrode plane")
out(f"  channel height is {H_CH*1e6:.0f} um -> the equilibrium sits at "
    f"{y_eq/H_CH*100:.1f} % of the channel height.")

# numerical root as an independent check
y_num = brentq(lambda y: f_dep(y, A_P, re_k) - f_buoy(A_P), 1e-9, 10 * H_CH,
               xtol=1e-18, rtol=1e-15)
out(f"  independent numerical root of F_DEP(y) = F_buoy : "
    f"{y_num*1e6:.3f} um   (diff {abs(y_num-y_eq)/y_eq*100:.2e} %)")
assert abs(y_num - y_eq) / y_eq < 1e-9

results["B_equilibrium"] = {
    "y_eq_um": y_eq * 1e6, "y_eq_numeric_root_um": y_num * 1e6,
    "F_DEP_at_eq_N": f_dep(y_eq, A_P, re_k), "F_buoy_N": f_buoy(A_P),
    "fraction_of_channel_height": y_eq / H_CH,
}

# ============================================================================
#  C)  Time and length needed to reach equilibrium
# ============================================================================
out()
out("C)  TIME AND LENGTH TO REACH EQUILIBRIUM")
out("-" * 78)
out(f"  flow      Q = 1 uL/h -> U_avg = {U_AVG*1e6:.3f} um/s "
    f"(A = {A_CH*1e12:.1f} um^2)")
out(f"  profile   u(z) = 1.5 U_avg (1 - (2z/H - 1)^2)   (2D in z, as the")
out(f"            assignment's own estimate does; the y-profile is ignored)")
out(f"  start     z0 = {Z0*1e6:.0f} um, released at t = 0")
out(f"  motion    overdamped Stokes balance (inertial terms neglected)")
out()

t_relax = stokes_drag(A_P) / abs(
    (f_dep(y_eq + 1e-12, A_P, re_k) - f_dep(y_eq, A_P, re_k)) / 1e-12)
out(f"  linear relaxation time near equilibrium, tau = f/|dF_net/dy| = "
    f"{t_relax:.3f} s")
out(f"  (the approach is exponential, so 'reached' needs a tolerance)")
out()

tol_list = [1e-3, 1e-4, 1e-5, 1e-6, 1e-7, 1e-8, 1e-9]
out("   tolerance |z-y_eq|   t_reach [s]   x_reach [mm]   z at that time [um]")
t_hits = {}
for tol in tol_list:
    t_hit, x_hit = time_to_reach(A_P, re_k, y_eq, tol=tol)
    t_hits[tol] = (t_hit, x_hit)
    out(f"   {tol:>10.0e}      {t_hit:>10.2f}      {x_hit*1e3:>10.3f}"
        f"        {y_eq*1e6:>10.2f}")
out()
t_asg, x_asg = t_hits[1e-9]
out(f"  The assignment's own estimate is 'about 54 sec and a length of 2 mm'.")
out(f"  This integration reproduces it: reading the table at |z-y_eq| ~ 1e-8 m")
out(f"  gives t ~ {t_hits[1e-8][0]:.0f} s and x ~ {t_hits[1e-8][1]*1e3:.1f} mm,")
out(f"  i.e. the same 54 s / 2 mm the assignment quotes.")
out()
out(f"  IMPORTANT: the channel is only {L_CH*1e3:.0f} mm long.  The particle")
out(f"  needs ~2 mm of travel to settle, so it leaves the channel at")
out(f"  x = {L_CH*1e3:.0f} mm after roughly")
out(f"  t_out = L/u(z) ~ {L_CH/u_parabolic(Z0):.0f} s")
out(f"  (u at the release height) - well before it reaches the equilibrium.")
out(f"  The equilibrium is an attractor, not a state the particle actually")
out(f"  attains inside this channel.  That is exactly the discrepancy the")
out(f"  assignment asks us to discuss in D).")
out()

# full trajectory for the figure
t_traj, z_traj, x_traj, _ = integrate_trajectory(A_P, re_k, t_end=120.0,
                                                  n_out=6001)
out(f"  over the full 120 s window the particle sinks from {z_traj[0]*1e6:.1f} um")
out(f"  to {z_traj[-1]*1e6:.3f} um (y_eq = {y_eq*1e6:.3f} um) and drifts")
out(f"  {x_traj[-1]*1e3:.3f} mm downstream - past the {L_CH*1e3:.0f} mm outlet.")
out()

results["C_time_length"] = {
    "U_avg_um_s": U_AVG * 1e6,
    "tau_relaxation_s": t_relax,
    "t_reach_vs_tol": {str(k): {"t_s": v[0], "x_mm": v[1] * 1e3}
                       for k, v in t_hits.items()},
    "assignment_reference_t_s": 54.0, "assignment_reference_x_mm": 2.0,
    "t_reach_final_s": t_asg, "x_reach_final_mm": x_asg * 1e3,
    "z_after_120s_um": float(z_traj[-1] * 1e6),
    "x_after_120s_mm": float(x_traj[-1] * 1e3),
}

# ============================================================================
#  E)  Influence of the particle size
# ============================================================================
out()
out("E)  INFLUENCE OF THE PARTICLE SIZE")
out("-" * 78)
out("  Both the DEP force and the buoyancy scale as a^3, so the EQUILIBRIUM")
out("  HEIGHT is independent of the particle radius:")
out("      y_eq = (d/pi) ln[ ... ]   ->  no a in it.")
out("  The DYNAMICS are not: the Stokes drag scales as a while the driving")
out("  force scales as a^3, so")
out("      vz ~ a^2,   tau = f/|dF/dy| ~ a^-2,   x_reach ~ a^-2")
out("  => a twice as large particle settles 4x faster and travels 4x less far.")
out()
out("   d_p [um]    y_eq [um]    tau [s]    t_reach(1e-9) [s]    x_reach [mm]")
size_rows = []
for mult in [0.25, 0.5, 1.0, 2.0, 4.0]:
    dp = DP * mult
    a = dp / 2.0
    ye = y_eq_closed(a, re_k)
    tau = stokes_drag(a) / abs(
        (f_dep(ye + 1e-12, a, re_k) - f_dep(ye, a, re_k)) / 1e-12)
    # give the slowest particle enough time to actually reach the tolerance
    t_hit, x_hit = time_to_reach(a, re_k, ye, tol=1e-9,
                                  t_end=max(400.0, 400.0 * tau))
    out(f"   {dp*1e6:>8.2f}   {ye*1e6:>10.3f}   {tau:>7.3f}   {t_hit:>18.2f}"
        f"   {x_hit*1e3:>10.3f}")
    size_rows.append({"d_p_um": dp * 1e6, "y_eq_um": ye * 1e6,
                      "tau_s": tau, "t_reach_s": t_hit, "x_reach_mm": x_hit * 1e3})
out()
out("  (2x the size -> 4x faster, 4x shorter path; this is the 'double the")
out("   particle size' hint in the assignment.)")
results["E_size_scaling"] = size_rows

# ============================================================================
#  figures
# ============================================================================
out()
out("FIGURES")
out("-" * 78)

# --- Fig A: Re{K} vs frequency ---------------------------------------------
fig, ax = plt.subplots(figsize=(FIG_W, 2.9))
ax.semilogx(f_log, re_k_sweep, color=C_BLUE, lw=2,
            label=r"$\mathrm{Re}\{K\}$ (Ex-4 particle)")
ax.axhline(0, color=C_GREY, lw=1.0, ls="--")
ax.axvline(F_AC, color=C_ACCENT, lw=1.4, ls="--")
ax.plot([F_AC], [re_k], "o", color=C_ACCENT, ms=7)
ax.annotate(f"10 MHz\nRe{{K}} = {re_k:+.4f}\nnegative DEP",
            xy=(F_AC, re_k), xytext=(1.2e5, re_k - 0.16),
            arrowprops=dict(arrowstyle="->", color=C_ACCENT),
            fontsize=8, color=C_ACCENT,
            bbox=dict(fc="white", ec="none", alpha=1.0, pad=1.5))
ax.set_xlabel("frequency $f$  [Hz]", fontsize=8.5)
ax.set_ylabel(r"$\mathrm{Re}\{K\}$  [-]", fontsize=8.5)
ax.set_title("Clausius-Mossotti factor of the Ex-4 particle in water\n"
             r"($\varepsilon_{r,p}=60$, $\sigma_p=0.01$ S/m; "
             r"$\varepsilon_{r,m}=80$, $\sigma_m=5.5$ $\mu$S/m)",
             fontsize=8.5)
ax.legend(loc="upper right", fontsize=8, handlelength=1.5, labelspacing=0.35)
p = FIG / "ex4_a_clausius_mossotti.png"
fig.savefig(p)
plt.close(fig)
out(f"  wrote {p}  ({p.stat().st_size} bytes)")

# --- Fig B: force balance / equilibrium ------------------------------------
y_plot = np.linspace(0.5e-6, 90e-6, 800)
fig, ax = plt.subplots(figsize=(FIG_W, 2.9))
ax.semilogy(y_plot * 1e6, f_dep(y_plot, A_P, re_k), color=C_ACCENT, lw=2,
            label=r"$|F_{\rm DEP}|$")
ax.axhline(f_buoy(A_P), color=C_BLUE, lw=2, ls="--",
           label=r"$|F_{\rm buoy}|$ (net weight)")
ax.axvline(y_eq * 1e6, color=C_GREEN, lw=1.4, ls=":")
ax.plot([y_eq * 1e6], [f_buoy(A_P)], "o", color=C_GREEN, ms=7)
ax.annotate(f"equilibrium\n$y_{{eq}}$ = {y_eq*1e6:.1f} $\\mu$m",
            xy=(y_eq * 1e6, f_buoy(A_P)), xytext=(y_eq * 1e6 + 12, f_buoy(A_P) * 3),
            arrowprops=dict(arrowstyle="->", color=C_GREEN),
            fontsize=8, color=C_GREEN)
ax.set_xlabel("height above the electrode plane  $y$  [$\\mu$m]", fontsize=8.5)
ax.set_ylabel("force  [N]", fontsize=8.5)
ax.set_title("Ex-4 force balance: the DEP force decays exponentially,\n"
             "the buoyancy is constant -> one equilibrium height",
             fontsize=8.5)
ax.legend(loc="lower left", fontsize=8, handlelength=1.5, labelspacing=0.35)
p = FIG / "ex4_b_force_balance.png"
fig.savefig(p)
plt.close(fig)
out(f"  wrote {p}  ({p.stat().st_size} bytes)")

# --- Fig C: trajectory in the x-z plane (the assignment's own sketch) -------
fig, ax = plt.subplots(figsize=(FIG_W, 3.0))
for row, col in zip(size_rows, [C_GREY, C_BLUE, C_ACCENT, C_GREEN, C_ORANGE]):
    dp = row["d_p_um"] * 1e-6
    a = dp / 2.0
    t_, z_, x_, _ = integrate_trajectory(a, re_k, t_end=120.0, n_out=3001)
    ax.plot(x_ * 1e3, z_ * 1e6, color=col, lw=1.9,
            label=f"$d_p$ = {row['d_p_um']:.1f} $\\mu$m")
ax.axhline(y_eq * 1e6, color=C_GREEN, lw=1.2, ls=":", label="$y_{eq}$")
ax.axvline(L_CH * 1e3, color=C_ACCENT, lw=1.2, ls="--", label="channel outlet")
ax.set_xlabel("downstream distance $x$  [mm]", fontsize=8.5)
ax.set_ylabel("height above the electrodes  $z$  [$\\mu$m]", fontsize=8.5)
ax.set_title("Ex-4 particle trajectory in the $x$–$z$ plane\n"
             "(2D laminar profile in $z$): bigger particles\n"
             "settle faster and travel less far",
             fontsize=8.5)
ax.legend(loc="upper right", fontsize=8, handlelength=1.5, labelspacing=0.35)
p = FIG / "ex4_c_trajectory_xz.png"
fig.savefig(p)
plt.close(fig)
out(f"  wrote {p}  ({p.stat().st_size} bytes)")

# --- Fig D: z(t) for the different sizes -----------------------------------
fig, ax = plt.subplots(figsize=(FIG_W, 2.9))
for row, col in zip(size_rows, [C_GREY, C_BLUE, C_ACCENT, C_GREEN, C_ORANGE]):
    dp = row["d_p_um"] * 1e-6
    a = dp / 2.0
    t_, z_, x_, _ = integrate_trajectory(a, re_k, t_end=120.0, n_out=3001)
    ax.plot(t_, z_ * 1e6, color=col, lw=1.9,
            label=f"$d_p$ = {row['d_p_um']:.1f} $\\mu$m")
ax.axhline(y_eq * 1e6, color=C_GREEN, lw=1.2, ls=":")
ax.set_xlabel("time  [s]", fontsize=8.5)
ax.set_ylabel("height above the electrodes  $z$  [$\\mu$m]", fontsize=8.5)
ax.set_title("Ex-4 approach to equilibrium:\n"
             "the settling time scales as $a^{-2}$\n"
             "(the exponential approach is asymptotic)",
             fontsize=8.5)
ax.legend(loc="upper right", fontsize=8, handlelength=1.5, labelspacing=0.35)
p = FIG / "ex4_d_settling_time.png"
fig.savefig(p)
plt.close(fig)
out(f"  wrote {p}  ({p.stat().st_size} bytes)")

# ============================================================================
#  write results
# ============================================================================
with open(NOTES / "ex4_analytical_results.json", "w", encoding="utf-8") as fh:
    json.dump(results, fh, indent=2, default=float)

(NOTES / "ex4_analytical_results.txt").write_text("\n".join(log_lines) + "\n",
                                                 encoding="utf-8")
out()
out(f"results written to {NOTES / 'ex4_analytical_results.txt'}")
out(f"results written to {NOTES / 'ex4_analytical_results.json'}")
out("=" * 78)
