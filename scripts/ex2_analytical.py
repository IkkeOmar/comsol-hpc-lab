"""
Exercise 2 - Cell growth chamber (the lab-on-a-chip course, Assignment 1).

Analytical sub-questions A + the quantitative setup quantities needed for the
COMSOL sub-questions B-G.  Pure Python (numpy / scipy / matplotlib), NO COMSOL.

Geometry and data (verbatim from Assignment_1.pdf, Exercise 2):
  * rectangular chamber, side 6 cm, centred at (0,0,0), height 1 mm
  * microfluidic inlet channel, 150 mm long x 2 mm wide x 1 mm high, centred
    at (0,0,0), running along one horizontal axis through the chamber
  * inlet concentration 1 mol/m^3 at Q = 100 uL/min for a pulse of duration
    "t_fill"; pulse = Rect1, start -5 s, end t_fill, transition zone 5,
    2 continuous derivatives
  * solute diffusion coefficient D = 200 um^2/s
  * cell layer: cylinder, radius 2.5 cm, 100 um high, centre (0,0,-50 um),
    below the chamber; no flow, diffusion coefficient 0.7*D, reaction -k*c
    with k = 0.15 mol/m^3/s

Unit convention:
  1 uL = 1e-9 m^3  ->  1 uL/min = 1e-9/60 m^3/s
  1 mL = 1e-6 m^3  ->  1 mL/min = 1e-6/60 m^3/s
  (the task brief wrote "1 uL/min = 1e-6/60 m^3/s", which is the mL/min
   value; the required sanity check t_fill = 2160 s confirms 1e-9/60.)

Outputs:
  figures/ex2_a_fill.png, figures/ex2_a_reynolds.png,
  figures/ex2_cell_layer.png, figures/ex2_b_cstr_benchmark.png
  notes/ex2_analytical_results.txt, notes/ex2_analytical_results.json
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
from scipy.integrate import solve_bvp, solve_ivp
from scipy.linalg import expm
from scipy.optimize import brentq
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

# ----------------------------------------------------------------------------
# constants and geometry (SI)
# ----------------------------------------------------------------------------
K_B = 1.380649e-23          # J/K
EPS0 = 8.8541878128e-12     # F/m
ETA = 1.0e-3                # Pa*s, water at 293.15 K
T = 293.15                  # K
G = 9.80665                 # m/s^2
RHO = 1000.0                # kg/m^3, water
NU = ETA / RHO              # m^2/s, kinematic viscosity

# chamber
CH_SIDE = 0.060             # m, 6 cm
CH_HEIGHT = 1.0e-3          # m, 1 mm
CH_AREA_PLAN = CH_SIDE ** 2                 # m^2, plan area (60 x 60 mm)
CH_AREA_FLOW = CH_SIDE * CH_HEIGHT          # m^2, cross-section normal to flow
CH_VOL = CH_AREA_PLAN * CH_HEIGHT

# inlet channel
IN_LEN = 0.150              # m, 150 mm
IN_WIDTH = 2.0e-3           # m, 2 mm
IN_HEIGHT = 1.0e-3          # m, 1 mm
IN_AREA = IN_WIDTH * IN_HEIGHT             # m^2
IN_VOL = IN_LEN * IN_AREA                  # m^3
IN_PERIM = 2.0 * (IN_WIDTH + IN_HEIGHT)    # m
IN_DH = 4.0 * IN_AREA / IN_PERIM            # m, hydraulic diameter

# solute / cell layer
D_SOLUTE = 200.0e-12        # m^2/s
POROSITY = 0.70
D_EFF = POROSITY * D_SOLUTE               # m^2/s, effective in cell layer
K_REACT = 0.15               # mol/m^3/s
C_IN = 1.0                  # mol/m^3, inflow concentration
C_SURFACE = 1.0             # mol/m^3, pinned surface concentration
CELL_R = 0.025               # m, 2.5 cm
CELL_H = 1.0e-4              # m, 100 um
CELL_AREA = math.pi * CELL_R ** 2          # m^2, cell-layer footprint

# flow rates
Q_LIST_ULMIN = [10.0, 100.0, 1000.0]   # 10 uL/min, 100 uL/min, 1 mL/min
Q_NOMINAL_ULMIN = 100.0
RE_LAMINAR_THRESHOLD = 2000.0

WORK = Path(r"<project>")
FIG = WORK / "figures"
NOTES = WORK / "notes"
FIG.mkdir(parents=True, exist_ok=True)
NOTES.mkdir(parents=True, exist_ok=True)

C_ACCENT = "#E50000"   # the course red
C_BLUE = "#1f4e79"
C_GREEN = "#2e7d32"
C_GREY = "#555555"

# The report places every figure at 0.85\textwidth = 4.23 in wide.  Drawing at
# that width and saving WITHOUT a tight bounding box keeps the saved native
# width equal to the placement width, so the nominal point sizes below are the
# point sizes on the printed page (effective pt = size * 4.23 / 4.23).
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

results: dict = {}
LOG: list[str] = []


def say(line: str = "") -> None:
    """Print to stdout and keep a copy for the text report."""
    print(line)
    LOG.append(line)


def q_m3_per_s(q_ul_per_min: float) -> float:
    """Convert uL/min to m^3/s.  1 uL = 1e-9 m^3."""
    return q_ul_per_min * 1e-9 / 60.0


def reynolds(q_ul_per_min: float) -> float:
    """Inlet-channel Reynolds number, Re = rho*U*Dh/eta."""
    u = q_m3_per_s(q_ul_per_min) / IN_AREA
    return RHO * u * IN_DH / ETA


def q_label(q_ul_per_min: float) -> str:
    return f"{q_ul_per_min:g} uL/min" if q_ul_per_min < 1000 else "1 mL/min"


# ----------------------------------------------------------------------------
# cell layer: analytic slab, BVP verification, transient relaxation
# ----------------------------------------------------------------------------
def cell_layer_steady_state(c_s: float = C_SURFACE) -> dict:
    """
    1D reaction-diffusion in the cell layer (z = 0 impermeable bottom,
    z = L_c the surface):

        D_eff d2c/dz2 = k c,   0 <= z <= L_c
        c'(0)  = 0              (impermeable bottom)
        c(L_c) = c_s           (surface concentration)

    Analytic solution (as given in the assignment):
        c(z) = c_s cosh(m z)/cosh(m L_c),   m = sqrt(k/D_eff)
    """
    L_c = CELL_H
    m = math.sqrt(K_REACT / D_EFF)

    def c_analytic(z):
        return c_s * np.cosh(m * np.asarray(z, dtype=float)) / np.cosh(m * L_c)

    # --- verification 1: scipy.integrate.solve_bvp -------------------------
    def fun(z, y):
        return np.vstack((y[1], (K_REACT / D_EFF) * y[0]))

    def bc(ya, yb):
        # ya = (c, c') at z = 0 ; yb = (c, c') at z = L_c
        return np.array([ya[1], yb[0] - c_s])

    z_mesh = np.linspace(0.0, L_c, 201)
    y_guess = np.vstack((c_analytic(z_mesh),
                         m * c_s * np.sinh(m * z_mesh) / np.cosh(m * L_c)))
    # tol=1e-6 is the working point: the coefficient k/D_eff ~ 1e9 m^-2 makes
    # the relative residual very demanding, so tighter tolerances only blow
    # up the node count (verified: tol=1e-6 -> 204 nodes, max error
    # 1.8e-10 mol/m^3; tol=1e-8..1e-10 -> node limit exceeded).
    sol = solve_bvp(fun, bc, z_mesh, y_guess, tol=1e-6, max_nodes=20000)
    if sol.status != 0:
        raise RuntimeError(f"solve_bvp failed: {sol.message}")

    z_chk = np.linspace(0.0, L_c, 401)
    c_bvp = sol.sol(z_chk)[0]
    c_exa = c_analytic(z_chk)
    bvp_abs_err = float(np.max(np.abs(c_bvp - c_exa)))
    bvp_rel_err = float(np.max(np.abs(c_bvp - c_exa) /
                                np.maximum(np.abs(c_exa), 1e-300)))

    # --- verification 2: analytic solution residual ------------------------
    c_pp = c_s * m ** 2 * np.cosh(m * z_chk) / np.cosh(m * L_c)
    ode_residual = float(np.max(np.abs(D_EFF * c_pp - K_REACT * c_exa)))

    # --- verification 3: same ODE integrated with solve_ivp ----------------
    def ivp_rhs(z, y):
        return [y[1], (K_REACT / D_EFF) * y[0]]

    ivp2 = solve_ivp(ivp_rhs, [L_c, 0.0],
                     [c_s, c_s * m * math.tanh(m * L_c)], t_eval=[0.0],
                     rtol=1e-11, atol=1e-13, method="DOP853")
    ivp_c_mid = float(ivp2.y[0, -1])
    ivp_err = abs(ivp_c_mid - float(c_analytic(0.0)))

    # --- transient: FD + matrix exponential vs eigen-expansion -------------
    n_fd = 200
    dz = L_c / n_fd
    d = D_EFF
    A = np.zeros((n_fd, n_fd))
    b = np.zeros(n_fd)
    # unknowns c_0 .. c_{n_fd-1}; c_{n_fd} = c_s (Dirichlet at z = L_c),
    # ghost node below z = 0 gives the Neumann condition at z = 0
    A[0, 0] = -2.0 * d / dz ** 2 - K_REACT
    A[0, 1] = 2.0 * d / dz ** 2
    for i in range(1, n_fd - 1):
        A[i, i - 1] = d / dz ** 2
        A[i, i] = -2.0 * d / dz ** 2 - K_REACT
        A[i, i + 1] = d / dz ** 2
    A[n_fd - 1, n_fd - 2] = d / dz ** 2
    A[n_fd - 1, n_fd - 1] = -2.0 * d / dz ** 2 - K_REACT
    b[n_fd - 1] = d / dz ** 2 * c_s

    aug = np.zeros((n_fd + 1, n_fd + 1))
    aug[:n_fd, :n_fd] = A
    aug[:n_fd, n_fd] = b

    def fd_c(t):
        e = expm(aug * t)
        return e[:n_fd, n_fd]          # c0 = 0 everywhere

    # eigen-expansion (analytic coefficients, exact for the linear problem):
    # c(z,t) = c_ss(z) + sum_n a_n cos(mu_n z) exp(-lambda_n t)
    # a_n = -(2 c_s / L_c) mu_n (-1)^n / (m^2 + mu_n^2)
    n_modes = 200000
    n_idx = np.arange(n_modes)
    mu = (n_idx + 0.5) * math.pi / L_c
    lam = K_REACT + D_EFF * mu ** 2
    a_coef = -(2.0 * c_s / L_c) * mu * ((-1.0) ** n_idx) / (m ** 2 + mu ** 2)
    c_mid_ss = float(c_analytic(0.0))

    def c_mid_eigen(t):
        return c_mid_ss + float(np.sum(a_coef * np.exp(-lam * t)))

    fd_mid_1 = float(fd_c(1.0)[0])
    eig_mid_1 = c_mid_eigen(1.0)
    fd_mid_10 = float(fd_c(10.0)[0])
    eig_mid_10 = c_mid_eigen(10.0)
    fd_mid_100 = float(fd_c(100.0)[0])
    eig_mid_100 = c_mid_eigen(100.0)

    def fd_mid(t):
        return float(fd_c(t)[0])

    t99 = brentq(lambda t: fd_mid(t) - 0.99 * c_mid_ss, 1e-6, 1e4)
    t90 = brentq(lambda t: fd_mid(t) - 0.90 * c_mid_ss, 1e-6, 1e4)

    # slowest eigen-mode of the transient (Neumann at 0, Dirichlet at L_c)
    mu1 = math.pi / (2.0 * L_c)
    lam1 = K_REACT + D_EFF * mu1 ** 2
    tau_slow = 1.0 / lam1

    # surface reaction coefficient (an effective velocity)
    K_r = math.sqrt(K_REACT * D_EFF) * math.tanh(m * L_c)

    return {
        "L_c_m": L_c,
        "m_inv": m,
        "m_Lc": m * L_c,
        "c_surface_mol_m3": c_s,
        "c_mid_mol_m3": c_mid_ss,
        "drop_mol_m3": c_s - c_mid_ss,
        "drop_fraction": (c_s - c_mid_ss) / c_s,
        "c_mid_over_c_s": c_mid_ss / c_s,
        "bvp_max_abs_err": bvp_abs_err,
        "bvp_max_rel_err": bvp_rel_err,
        "bvp_nodes": int(sol.x.size),
        "bvp_rms_residual": float(sol.rms_residuals.max()),
        "ode_residual_mol_m3": ode_residual,
        "ivp_c_mid_mol_m3": ivp_c_mid,
        "ivp_abs_err": ivp_err,
        "transient_fd_vs_eigen_t1": abs(fd_mid_1 - eig_mid_1),
        "transient_fd_vs_eigen_t10": abs(fd_mid_10 - eig_mid_10),
        "transient_fd_vs_eigen_t100": abs(fd_mid_100 - eig_mid_100),
        "transient_c_mid_t1": fd_mid_1,
        "transient_c_mid_t10": fd_mid_10,
        "transient_c_mid_t100": fd_mid_100,
        "transient_tau_slowest_s": tau_slow,
        "transient_t90_s": float(t90),
        "transient_t99_s": float(t99),
        "surface_reaction_coeff_m_s": K_r,
        "z_profile_m": np.linspace(0.0, L_c, 201).tolist(),
        "c_profile_mol_m3": c_analytic(np.linspace(0.0, L_c, 201)).tolist(),
    }


# ----------------------------------------------------------------------------
# flow-rate dependence: pinned surface vs. film-resistance model
# ----------------------------------------------------------------------------
def flow_rate_sweep() -> list:
    """
    Steady state in the cell layer for Q = 10 uL/min, 100 uL/min, 1 mL/min.

    (i) Pinned-surface limit (c_s = 1 mol/m^3 fixed): the cell-layer profile
        is flow-rate independent - it isolates the reaction-diffusion
        kinetics from the supply of nutrient.
    (ii) Film-resistance model (idealised, clearly flagged in the report):
         - chamber-side diffusion film of thickness delta = sqrt(D_f*t_res),
           giving k_m = D_f/delta (choice stated in the report);
         - film balance  k_m (c_bulk - c_s) = K_r c_s,
           with K_r = sqrt(k D_eff) tanh(m L_c) the surface reaction
           coefficient (an effective velocity);
         - chamber balance  Q (c_in - c_bulk) = K_r c_s A_cell
           (bulk chamber treated as well mixed - an upper-bound estimate,
           since the real chamber is NOT well mixed, see part C).
    """
    L_c = CELL_H
    m = math.sqrt(K_REACT / D_EFF)
    cosh_mL = math.cosh(m * L_c)
    K_r = math.sqrt(K_REACT * D_EFF) * math.tanh(m * L_c)

    rows = []
    for q_ul in Q_LIST_ULMIN:
        q = q_m3_per_s(q_ul)
        u_ch = q / IN_AREA
        re = RHO * u_ch * IN_DH / ETA
        t_res = CH_VOL / q
        delta = math.sqrt(D_SOLUTE * t_res)
        k_m = D_SOLUTE / delta
        # Leveque-type (laminar, linear velocity profile) alternative scaling
        k_m_lev = (D_SOLUTE ** 2 * u_ch / (IN_HEIGHT * IN_LEN)) ** (1.0 / 3.0)
        # solve the two coupled balances for (c_s, c_bulk)
        c_s = q * C_IN / (q * (1.0 + K_r / k_m) + K_r * CELL_AREA)
        c_bulk = c_s * (1.0 + K_r / k_m)
        rows.append({
            "Q_uL_per_min": q_ul,
            "Q_label": q_label(q_ul),
            "Q_m3_per_s": q,
            "Re": re,
            "U_channel_m_s": u_ch,
            "t_res_chamber_s": t_res,
            "delta_film_m": delta,
            "delta_over_chamber_height": delta / CH_HEIGHT,
            "k_m_m_s": k_m,
            "k_m_leveque_m_s": k_m_lev,
            "c_surface_pinned_mol_m3": C_SURFACE,
            "c_mid_pinned_mol_m3": C_SURFACE / cosh_mL,
            "c_surface_film_mol_m3": c_s,
            "c_bulk_mol_m3": c_bulk,
            "c_mid_film_mol_m3": c_s / cosh_mL,
        })
    return rows


# ----------------------------------------------------------------------------
# setup quantities for B-G
# ----------------------------------------------------------------------------
def setup_quantities_b_to_g(t_fill: float, t_d_tr: float,
                            cell: dict) -> tuple[list[str], dict]:
    """Return (report lines, machine-readable data) for the B-G setup."""
    L = []
    add = L.append
    u_ch = q_m3_per_s(Q_NOMINAL_ULMIN) / IN_AREA
    t_adv = CH_VOL / q_m3_per_s(Q_NOMINAL_ULMIN)
    tau_cstr = t_adv
    tau_relax = 1.0 / (K_REACT + D_EFF * (math.pi / (2.0 * CELL_H)) ** 2)
    c_mid = cell["c_mid_mol_m3"]
    K_r = cell["surface_reaction_coeff_m_s"]

    add("")
    add("=" * 78)
    add("SETUP QUANTITIES FOR THE COMSOL SUB-QUESTIONS B-G")
    add("=" * 78)

    add("")
    add("PART B - DOMAIN-AVERAGE CONCENTRATION VS TIME (fluid exchange)")
    add("-" * 78)
    add(f"  Pulse (Rect1): start = -5 s, end = t_fill = {t_fill:.0f} s,")
    add("    transition-zone size = 5, 2 continuous derivatives.")
    add("    NB: with a smoothing zone of 5 the effective ON/OFF edges sit")
    add("    at -5+2.5 = -2.5 s and t_fill-2.5 s, so the delivered dose is")
    add(f"    ~ (t_fill+2.5) s of full-strength inflow, i.e. ~"
        f"{(t_fill+2.5)/t_fill:.3f} x V_chamber of solute.")
    add("  Study: stationary step 1 (velocity) + time-dependent step 2")
    add("  (species, velocity taken from step 1), t from -5 s to")
    add(f"  3*t_fill = {3*t_fill:.0f} s, output step 5-10 s (use 5 s).")
    add("  Analytical brackets for the domain probe (chamber-average c):")
    add(f"    well-mixed (CSTR): tau = V/Q = {tau_cstr:.0f} s,")
    add(f"      c_avg(t_fill) = 1-exp(-1) = {1-math.exp(-1):.4f} mol/m^3,")
    add(f"      c_avg(3*t_fill) = {1-math.exp(-1):.4f}*exp(-2) = "
        f"{(1-math.exp(-1))*math.exp(-2):.4f} mol/m^3;")
    add("    plug-flow limit: c_avg = min(t/t_fill, 1) -> 1.0 mol/m^3 at")
    add("      t_fill (upper bracket).")
    add("    The COMSOL probe should fall between the two brackets; a value")
    add("    well below the CSTR curve means the chamber corners are dead")
    add("    zones that are not flushed at all.")
    add(f"  Solute mass balance: Q*c*t_fill = "
        f"{C_IN*q_m3_per_s(Q_NOMINAL_ULMIN)*t_fill:.4e} mol = V_chamber*c")
    add("    -> at best the chamber reaches c = 1 mol/m^3 if nothing is")
    add("    lost to the outlet and the mixing is perfect.")

    add("")
    add("PART C - THREE POINT PROBES (homogeneity)")
    add("-" * 78)
    add("  Suggested probe points (chamber footprint 60 x 60 mm, channel along")
    add("  x, centred on y = 0):")
    add("    P1 = (-29.5, 0, 0) mm  - upstream end, on the channel axis")
    add("    P2 = (0, 29.5, 0) mm   - off-axis, next to a side wall")
    add("    P3 = (29.5, 0, 0) mm   - downstream end, on the channel axis")
    add(f"  On-axis advective arrival (jet speed {u_ch*1e3:.3f} mm/s):")
    add(f"    P1: 45.5 mm from the inlet -> {45.5e-3/u_ch:.1f} s")
    add(f"    P3: 105.5 mm from the inlet -> {105.5e-3/u_ch:.1f} s")
    add("  Off-axis P2: lateral gap from the channel edge (1 mm) = 28.5 mm,")
    add(f"    diffusion time = gap^2/D = {28.5e-3**2/D_SOLUTE:.3e} s = "
        f"{28.5e-3**2/D_SOLUTE/86400:.1f} days")
    add("    -> utterly outside the 3*t_fill study window: the off-axis")
    add("       corner stays near c = 0 unless the FLOW reaches it.")
    add(f"  Transverse diffusion across the 1 mm height: {t_d_tr:.0f} s, i.e.")
    add(f"  {t_adv/t_d_tr:.2f} x SHORTER than the chamber residence time "
        f"{t_adv:.0f} s:")
    add("    the column above the floor homogenises fast, but only the narrow")
    add("    strip along the channel axis is actually flushed -> large")
    add("    in-plane inhomogeneity, which is what part C should show.")
    add("  Meaning for cells on the floor: cells under the channel get the")
    add("  drug first and most; cells far from the axis get almost none.")

    add("")
    add("PART D - HOW TO IMPROVE HOMOGENEITY (simulate one)")
    add("-" * 78)
    add("  (1) Serpentine / meander inlet that sweeps the whole 60 x 60 mm")
    add("      footprint, or several parallel inlet channels.")
    add("  (2) Shrink the chamber footprint until L^2/D ~ t_fill.")
    add("  (3) Raise Q (t_fill scales as 1/Q; Re stays << 2000).")
    add("  Recommended: option (1). t_diff,along = L^2/D = "
        f"{CH_SIDE**2/D_SOLUTE:.2e} s is")
    add(f"  {CH_SIDE**2/D_SOLUTE/t_adv:.0f} x the residence time, so diffusion")
    add("  can never fill the far corners in this study - the flow must be")
    add("  brought to them. A meander keeps Re and t_fill unchanged but")
    add("  gives every point an advective residence time of O(t_fill).")

    add("")
    add("PART E - TRANSIENT IN THE CELL DOMAIN")
    add("-" * 78)
    add("  Cell-domain physics: zero velocity (Fluid domain condition),")
    add(f"  D = 0.7*Dsp = {D_EFF:.4e} m^2/s, reaction -k*c with")
    add(f"  k = {K_REACT} mol/m^3/s (15 %/s of the local concentration).")
    add("  Initial Values: c = 1 mol/m^3 in the inlet channel only (chamber")
    add("  starts at 0), inflow c = 1 mol/m^3 (no pulse), study from t = 0.")
    add("  Cell-layer relaxation (pinned surface): slowest mode")
    add(f"  tau = 1/(k + D_eff*(pi/2L_c)^2) = {tau_relax:.2f} s,")
    add(f"  t_90 = {cell['transient_t90_s']:.1f} s, "
        f"t_99 = {cell['transient_t99_s']:.1f} s")
    add(f"  (1/k = {1/K_REACT:.2f} s dominates: reaction is much faster than")
    add(f"  diffusion across the layer, L_c^2/D_eff = "
        f"{CELL_H**2/D_EFF:.1f} s).")
    add("  => the cell layer itself equilibrates within tens of seconds,")
    add("     but its SURFACE concentration only rises as the chamber fills")
    add(f"     (O(t_fill) = {t_fill:.0f} s), so the cell-domain curve in the")
    add("     COMSOL run follows the chamber flush and is flat only after")
    add(f"     roughly 2-3 t_fill = {2*t_fill:.0f}-{3*t_fill:.0f} s.")
    add("  Output saving: every 5 s up to t_fill, every 200 s afterwards")
    add("  (as suggested in the assignment).")

    add("")
    add("PART F - STATIONARY STUDY, FLOW-RATE SWEEP")
    add("-" * 78)
    add("  Stationary step 2 (species) coupled to the stationary velocity of")
    add("  step 1; parametric sweep Q = 10 uL/min, 100 uL/min, 1 mL/min.")
    add("  Re over the sweep: " +
        ", ".join(f"{reynolds(q):.3f}" for q in Q_LIST_ULMIN) +
        "  (all << 2000, laminar).")
    add(f"  Pinned-surface expectation: c_mid = {c_mid:.6e} mol/m^3 for ALL")
    add("  three flow rates - the cell-layer profile is set by reaction and")
    add("  diffusion alone, not by the flow rate.")
    add("  How Q enters (the point of the comparison): through the")
    add("  chamber-side mass-transfer film. I use k_m ~ D_fluid/delta with")
    add("  the diffusion boundary-layer thickness estimated from the")
    add("  residence time, delta = sqrt(D_fluid * t_res) and t_res = V/Q,")
    add("  so k_m = sqrt(D_fluid*Q/V) and k_m ~ Q^(1/2).")
    add("  (The classical Leveque/Levich scaling for laminar channel flow is")
    add("   k_m ~ (D_f^2 U/(Dh*L))^(1/3) ~ U^(1/3) ~ Q^(1/3); it gives the")
    add("   same monotone trend. NOTE: the form quoted in the task brief,")
    add("   (D_f^2 U/(nu*L_ch))^(-1/3), is dimensionally inconsistent - the")
    add("   correct Leveque exponent is +1/3 and nu does not appear.)")
    add("  Qualitative statement: a HIGHER flow rate gives a thinner film,")
    add("  a larger k_m, a smaller film drop, hence a HIGHER steady-state")
    add("  concentration in the cell layer - 1 mL/min > 100 uL/min >")
    add("  10 uL/min (with the caveat that the chamber is not well mixed,")
    add("  so the real trend is steeper than the idealised model).")

    add("")
    add("PART G - ARE ALL CELLS IN IDENTICAL CONDITIONS?")
    add("-" * 78)
    add("  Inside one cell layer c varies from 1 mol/m^3 at the surface to")
    add(f"  {c_mid:.4f} mol/m^3 at the impermeable base (factor {1/c_mid:.1f}):")
    add("  cells at different depths are NOT identical, and this gradient is")
    add("  intrinsic (reaction-limited), not a flow artefact.")
    add("  Laterally, the part-C inhomogeneity relaxes only by diffusion:")
    add(f"  sqrt(2 D t) = {math.sqrt(2*D_SOLUTE*86400)*1e3:.1f} mm per day, so")
    add(f"  a 60 mm dead zone needs ~{CH_SIDE**2/(2*D_SOLUTE)/86400:.0f} days.")
    add("  Over a multi-day culture the lateral gradient is therefore only")
    add("  washed out if the flow keeps renewing the boundary layer; the")
    add("  reaction (1/k = %.1f s) makes the supply chronically marginal." %
        (1.0 / K_REACT))
    add("  Verdict: cells growing on the floor over the whole 60 x 60 mm")
    add("  footprint are NOT in identical conditions; only cells within the")
    add("  swept strip (or under a meander inlet) see the same nutrient.")

    data = {
        "pulse": {"start_s": -5.0, "end_s": t_fill,
                  "transition_zone": 5.0, "derivative_continuity": 2,
                  "effective_on_edge_s": -2.5,
                  "effective_off_edge_s": t_fill - 2.5},
        "study_window_s": [-5.0, 3.0 * t_fill],
        "recommended_output_step_s": 5.0,
        "cstr_reference": {
            "tau_s": tau_cstr,
            "c_avg_at_t_fill": 1 - math.exp(-1),
            "c_avg_at_3_t_fill": (1 - math.exp(-1)) * math.exp(-2),
            "plug_flow_c_avg_at_t_fill": 1.0,
        },
        "solute_mass_pulse_mol": C_IN * q_m3_per_s(Q_NOMINAL_ULMIN) * t_fill,
        "probe_points_m": {
            "P1_upstream_axis": [-0.0295, 0.0, 0.0],
            "P2_off_axis_side": [0.0, 0.0295, 0.0],
            "P3_downstream_axis": [0.0295, 0.0, 0.0],
        },
        "probe_arrival_times_s": {
            "P1_advective": 45.5e-3 / u_ch,
            "P3_advective": 105.5e-3 / u_ch,
            "P2_diffusive_from_channel_edge": 28.5e-3 ** 2 / D_SOLUTE,
        },
        "cell_layer": {
            "tau_relax_s": tau_relax,
            "t90_s": cell["transient_t90_s"],
            "t99_s": cell["transient_t99_s"],
            "output_interval_s": [5.0, 200.0],
            "surface_reaction_coeff_m_s": K_r,
        },
        "flow_sweep_uL_per_min": Q_LIST_ULMIN,
    }
    return L, data


# ----------------------------------------------------------------------------
# sanity checks
# ----------------------------------------------------------------------------
def sanity_checks(t_fill: float, re: float, cell: dict) -> tuple[list[str], dict]:
    L = []
    add = L.append

    v_expected = 3.6e-6
    ok1 = abs(CH_VOL - v_expected) / v_expected < 1e-6
    ok2 = abs(t_fill - 2160.0) / 2160.0 < 1e-6
    add("  1) t_fill for the 6 x 6 x 1 mm chamber at 100 uL/min:")
    add(f"     V_chamber = {CH_VOL:.4e} m^3 = {CH_VOL*1e9:.1f} uL "
        f"(expected 3.6e-6 m^3 = 3600 uL)  -> {'OK' if ok1 else 'FAIL'}")
    add(f"     t_fill = V/Q = {t_fill:.2f} s = {t_fill/60:.3f} min "
        f"(expected ~36 min = 2160 s)    -> {'OK' if ok2 else 'FAIL'}")

    ok3 = 0.1 <= re <= 10.0
    ok3b = re < RE_LAMINAR_THRESHOLD
    add(f"  2) Re in the inlet channel = {re:.4f} "
        f"(expected ~1-10)                   -> {'OK' if ok3 else 'FAIL'}")
    add(f"     laminar (Re << 2000): {'OK' if ok3b else 'FAIL'}")

    t_diff_cell = CELL_H ** 2 / D_EFF
    t_react = 1.0 / K_REACT
    c_ratio = cell["c_mid_over_c_s"]
    ok4 = c_ratio < 0.2
    add(f"  3) cell layer: diffusion time L_c^2/D_eff = {t_diff_cell:.1f} s,")
    add(f"     reaction time 1/k = {t_react:.2f} s -> reaction dominates")
    add(f"     c(base)/c_surface = {c_ratio:.4f} "
        f"(c_mid = {cell['c_mid_mol_m3']:.4e} mol/m^3 vs 1 mol/m^3)")
    add(f"     -> base MUCH lower than surface: {'OK' if ok4 else 'FAIL'}")

    data = {
        "1_volume_3.6e-6_m3": bool(ok1),
        "1_t_fill_2160_s": bool(ok2),
        "2_Re_in_1_to_10": bool(ok3),
        "2_laminar": bool(ok3b),
        "3_cell_reaction_dominated": bool(ok4),
        "t_diff_cell_layer_s": t_diff_cell,
        "t_reaction_s": t_react,
        "c_mid_over_c_s": c_ratio,
        "all_passed": bool(ok1 and ok2 and ok3 and ok3b and ok4),
    }
    return L, data


# ----------------------------------------------------------------------------
# main: part A
# ----------------------------------------------------------------------------
def part_a() -> dict:
    say("=" * 78)
    say("EXERCISE 2 - CELL GROWTH CHAMBER - ANALYTICAL RESULTS")
    say("the lab-on-a-chip course, Assignment 1, Exercise 2 (pure analytical, no COMSOL)")
    say("=" * 78)
    say("")

    # --- A1: volumes -------------------------------------------------------
    say("A1  VOLUMES")
    say(f"    chamber   6 cm x 6 cm x 1 mm  : {CH_VOL:.6e} m^3 = "
        f"{CH_VOL*1e9:.1f} uL")
    say(f"    inlet ch. 150 mm x 2 mm x 1 mm: {IN_VOL:.6e} m^3 = "
        f"{IN_VOL*1e9:.1f} uL")
    say(f"    inlet channel: A = {IN_AREA:.3e} m^2, P = {IN_PERIM:.3e} m, "
        f"D_h = 4A/P = {IN_DH*1e3:.4f} mm")
    say("    NOTE: the 150 mm channel crosses the 60 mm chamber, so the")
    inside_vol = IN_WIDTH * CH_SIDE * CH_HEIGHT
    outside_vol = IN_VOL - inside_vol
    total_vol = CH_VOL + outside_vol
    say(f"    channel volume inside the chamber footprint is "
        f"{inside_vol*1e9:.0f} uL, but that is part of the chamber volume, not extra; "
        f"the remaining {1000*(IN_LEN-CH_SIDE):.0f} mm of channel (the 45 mm inlet and 45 mm "
        f"outlet ducts) holds another {outside_vol*1e9:.0f} uL, so the system holds "
        f"{total_vol*1e9:.0f} uL of fluid in all. The exercise (and the")
    say("    required sanity check) uses the CHAMBER volume alone.")

    # --- A2: filling time --------------------------------------------------
    q_nom = q_m3_per_s(Q_NOMINAL_ULMIN)
    t_fill = CH_VOL / q_nom
    say("")
    say("A2  FILLING TIME AT Q = 100 uL/min")
    say(f"    Q = {Q_NOMINAL_ULMIN:g} uL/min = {q_nom:.6e} m^3/s")
    say(f"    t_fill = V_chamber/Q = {t_fill:.4f} s = {t_fill/60:.4f} min")
    say(f"    solute mass in the pulse = c*Q*t_fill = "
        f"{C_IN*q_nom*t_fill:.6e} mol = V_chamber*c exactly")
    say("    => choose the pulse 'filling time' parameter = t_fill = "
        f"{t_fill:.0f} s:")
    say("       the chamber is just filled when the pulse ends. (Shorter")
    say("       pulses under-dose the far corners; longer ones only add")
    say("       solute that leaves through the outlet.)")
    say(f"    time-dependent study window: -5 s .. 3*t_fill = {3*t_fill:.0f} s")

    # --- A3: Reynolds ------------------------------------------------------
    re = reynolds(Q_NOMINAL_ULMIN)
    u_in = q_nom / IN_AREA
    say("")
    say("A3  REYNOLDS NUMBER, INLET CHANNEL")
    say(f"    U = Q/A = {u_in:.6e} m/s")
    say(f"    Re = rho*U*D_h/eta = {RHO:.0f}*{u_in:.4e}*{IN_DH:.4e}/{ETA:.0e}"
        f" = {re:.4f}")
    say(f"    -> Re = {re:.3f} << 2000: the flow is firmly LAMINAR.")

    # --- A4: velocity and residence time in the inlet channel ---------------
    t_res_ch = IN_LEN / u_in
    say("")
    say("A4  INLET-CHANNEL VELOCITY AND RESIDENCE TIME")
    say(f"    U_avg = {u_in:.6e} m/s = {u_in*1e3:.4f} mm/s")
    say(f"    t_res = L/U = {t_res_ch:.4f} s = {t_res_ch/60:.3f} min")
    say(f"    cross-check V_channel/Q = {IN_VOL/q_nom:.4f} s")

    # --- A5: diffusion times ----------------------------------------------
    h = CH_HEIGHT
    t_d_tr = h ** 2 / (math.pi ** 2 * D_SOLUTE)
    t_d_half = h ** 2 / (4.0 * D_SOLUTE)
    t_d_long = CH_SIDE ** 2 / D_SOLUTE
    say("")
    say("A5  DIFFUSION TIMES (D = 200 um^2/s)")
    say(f"    across the chamber height, h^2/(pi^2 D) : {t_d_tr:.4e} s "
        f"({t_d_tr/60:.2f} min)")
    say(f"    across half the height,   h^2/(4 D)   : {t_d_half:.4e} s "
        f"({t_d_half/60:.2f} min)")
    say(f"    along the chamber, L^2/D (L = 6 cm)   : {t_d_long:.4e} s "
        f"({t_d_long/3600:.1f} h = {t_d_long/86400:.1f} days)")
    say(f"    vs t_fill = {t_fill:.0f} s:  t_fill/t_diff,perp = "
        f"{t_fill/t_d_tr:.2f},  t_diff,long/t_fill = {t_d_long/t_fill:.0f}")
    say("    -> transverse (1 mm) homogenisation is ~4x FASTER than the")
    say("       flushing time, so the chamber mixes across its height;")
    say("       but diffusion along the 60 mm length is ~8000x SLOWER than")
    say("       the flushing time, so only the flow path is exchanged.")

    # --- A6: Peclet in the chamber ----------------------------------------
    u_ch_flow = q_nom / CH_AREA_FLOW
    t_adv = CH_SIDE / u_ch_flow
    pe_h = u_ch_flow * CH_HEIGHT / D_SOLUTE
    u_ch_plan = q_nom / CH_AREA_PLAN
    pe_plan = u_ch_plan * CH_HEIGHT / D_SOLUTE
    say("")
    say("A6  EFFECTIVE PECLET NUMBER IN THE CHAMBER")
    say("    primary: flow cross-section A = 60 mm x 1 mm = "
        f"{CH_AREA_FLOW:.4e} m^2")
    say(f"      U_chamber = Q/A = {u_ch_flow:.6e} m/s ({u_ch_flow*1e6:.2f} um/s)")
    say(f"      advective traverse L/U = {t_adv:.4f} s (= V_chamber/Q = t_fill)")
    say(f"      Pe = U_chamber*h/D = {pe_h:.3f}   (h = 1 mm)")
    say(f"      t_adv / t_diff,perp = {t_adv/t_d_tr:.3f}")
    say("    variant: with the plan area 60 x 60 mm:")
    say(f"      U = {u_ch_plan:.4e} m/s, Pe = {pe_plan:.3f}")
    say(f"    jet Pe in the 2 mm channel: U*h/D = {u_in*CH_HEIGHT/D_SOLUTE:.0f}")
    say("    -> Pe >> 1: transport is advection dominated, i.e. the")
    say("       concentration is set by what ENTERS, not by diffusion.")
    say("       Two consequences:")
    say(f"       (i)  across the 1 mm height: t_diff,perp = {t_d_tr:.0f} s is")
    say(f"           still {t_adv/t_d_tr:.1f}x shorter than the residence time")
    say(f"           {t_adv:.0f} s, so the flushed column stays vertically")
    say("           uniform (the inflow is already uniform at c = 1);")
    say(f"       (ii) along the 60 cm: t_diff,long = {t_d_long:.1e} s =")
    say(f"           {t_d_long/t_adv:.0f}x the residence time, so lateral")
    say("           homogenisation by diffusion is hopeless - only the 2 mm")
    say("           wide flow path is exchanged and the rest of the")
    say("           footprint is a dead zone (this is the part-C effect).")

    # --- A7: cell layer ----------------------------------------------------
    say("")
    say("A7  CELL LAYER (cylinder r = 2.5 cm, h = 100 um)")
    say(f"    V_cell = pi r^2 h = {CELL_AREA*CELL_H:.6e} m^3 = "
        f"{CELL_AREA*CELL_H*1e9:.2f} uL")
    say(f"    D_eff = 0.7*D = {D_EFF:.6e} m^2/s ({D_EFF*1e12:.1f} um^2/s)")

    cell = cell_layer_steady_state(C_SURFACE)
    say(f"    m = sqrt(k/D_eff) = {cell['m_inv']:.4f} 1/m,  m*L_c = "
        f"{cell['m_Lc']:.4f}")
    say(f"    c(base) = c_s/cosh(m L_c) = {cell['c_mid_mol_m3']:.6e} mol/m^3")
    say(f"    surface-to-base drop = {cell['drop_mol_m3']:.6e} mol/m^3 "
        f"({100*cell['drop_fraction']:.2f} % of c_s)")
    say("    verify 1 - scipy.integrate.solve_bvp: max |c_bvp - c_analytic| = "
        f"{cell['bvp_max_abs_err']:.3e} mol/m^3 "
        f"(rel. {cell['bvp_max_rel_err']:.3e}), BVP rms residual "
        f"{cell['bvp_rms_residual']:.2e}")
    say("    verify 2 - ODE residual D_eff c'' - k c = "
        f"{cell['ode_residual_mol_m3']:.3e} mol/m^3")
    say("    verify 3 - integrate the same ODE from the surface downwards")
    say("             (solve_ivp):")
    say(f"             c_mid = {cell['ivp_c_mid_mol_m3']:.8e} mol/m^3, "
        f"|error| = {cell['ivp_abs_err']:.3e}")
    say("    verify 4 - transient: finite-difference + matrix exponential vs")
    say("             eigen-expansion, max |difference| at t = 1/10/100 s = "
        f"{max(cell['transient_fd_vs_eigen_t1'], cell['transient_fd_vs_eigen_t10'], cell['transient_fd_vs_eigen_t100']):.3e} mol/m^3")
    say(f"             cell-layer relaxation tau = "
        f"{cell['transient_tau_slowest_s']:.3f} s, t_90 = "
        f"{cell['transient_t90_s']:.2f} s, t_99 = "
        f"{cell['transient_t99_s']:.2f} s (pinned surface)")

    # --- setup for B-G -----------------------------------------------------
    setup_lines, setup_data = setup_quantities_b_to_g(t_fill, t_d_tr, cell)
    for line in setup_lines:
        say(line)

    # --- flow-rate sweep ----------------------------------------------------
    sweep = flow_rate_sweep()
    say("")
    say("FLOW-RATE SWEEP (part F)")
    say("-" * 78)
    say(f"  {'Q':>12} {'Re':>9} {'U_ch[m/s]':>12} {'t_res[s]':>10} "
        f"{'c_mid pinned':>13}")
    for row in sweep:
        say(f"  {row['Q_label']:>12} {row['Re']:>9.4f} "
            f"{row['U_channel_m_s']:>12.4e} {row['t_res_chamber_s']:>10.1f} "
            f"{row['c_mid_pinned_mol_m3']:>13.6e}")
    say("  -> pinned-surface cell-layer steady state is IDENTICAL for all")
    say("     three flow rates (it is set by k and D_eff only).")
    say("")
    say("  Film-resistance model (chamber film + reaction layer + flow")
    say("  balance; idealised well-mixed chamber, c_in = 1 mol/m^3):")
    say(f"  {'Q':>12} {'k_m[m/s]':>12} {'delta[um]':>11} {'k_m(Lev)[m/s]':>14} "
        f"{'c_surface':>10} {'c_mid':>11} {'c_bulk':>8}")
    for row in sweep:
        say(f"  {row['Q_label']:>12} {row['k_m_m_s']:>12.4e} "
            f"{row['delta_film_m']*1e6:>11.2f} "
            f"{row['k_m_leveque_m_s']:>14.4e} "
            f"{row['c_surface_film_mol_m3']:>10.5f} "
            f"{row['c_mid_film_mol_m3']:>11.6e} "
            f"{row['c_bulk_mol_m3']:>8.5f}")
    say("  -> 1 mL/min gives the highest cell-layer concentration.")
    say("  CAVEAT: this film model is a first-order estimate. The estimated")
    say("  film thickness delta is 0.66-2.08 mm, i.e. of the same order as")
    say("  the 1 mm chamber height (ratio shown in the JSON), so the linear")
    say("  gradient across the film and the perfectly-mixed-bulk assumption")
    say("  are marginal; the numbers should be read as a trend, not as")
    say("  predictions of the COMSOL result. The pinned-surface column")
    say("  above is the defensible quantity.")

    # --- sanity checks -----------------------------------------------------
    say("")
    say("SANITY CHECKS")
    say("-" * 78)
    sc_lines, sc_data = sanity_checks(t_fill, re, cell)
    for line in sc_lines:
        say(line)

    results["A"] = {
        "geometry": {
            "chamber_side_m": CH_SIDE,
            "chamber_height_m": CH_HEIGHT,
            "chamber_volume_m3": CH_VOL,
            "chamber_volume_uL": CH_VOL * 1e9,
            "chamber_plan_area_m2": CH_AREA_PLAN,
            "chamber_flow_cross_section_m2": CH_AREA_FLOW,
            "inlet_length_m": IN_LEN,
            "inlet_width_m": IN_WIDTH,
            "inlet_height_m": IN_HEIGHT,
            "inlet_volume_m3": IN_VOL,
            "inlet_volume_uL": IN_VOL * 1e9,
            "inlet_area_m2": IN_AREA,
            "inlet_perimeter_m": IN_PERIM,
            "inlet_hydraulic_diameter_m": IN_DH,
            "total_fluid_volume_m3": total_vol,
            "total_fluid_volume_uL": total_vol * 1e9,
        },
        "A2_filling": {
            "Q_uL_per_min": Q_NOMINAL_ULMIN,
            "Q_m3_per_s": q_nom,
            "t_fill_s": t_fill,
            "t_fill_min": t_fill / 60.0,
            "pulse_solute_mol": C_IN * q_nom * t_fill,
            "study_window_s": [-5.0, 3.0 * t_fill],
        },
        "A3_reynolds": {
            "U_channel_m_s": u_in,
            "Re": re,
            "laminar_threshold": RE_LAMINAR_THRESHOLD,
            "is_laminar": bool(re < RE_LAMINAR_THRESHOLD),
        },
        "A4_residence": {
            "U_avg_channel_m_s": u_in,
            "t_res_channel_s": t_res_ch,
            "t_res_channel_min": t_res_ch / 60.0,
        },
        "A5_diffusion": {
            "t_diff_transverse_pi2_s": t_d_tr,
            "t_diff_transverse_half_s": t_d_half,
            "t_diff_longitudinal_s": t_d_long,
            "t_fill_over_t_diff_transverse": t_fill / t_d_tr,
            "t_diff_longitudinal_over_t_fill": t_d_long / t_fill,
        },
        "A6_peclet": {
            "U_chamber_flow_section_m_s": u_ch_flow,
            "Pe_h": pe_h,
            "t_adv_chamber_s": t_adv,
            "t_adv_over_t_diff_transverse": t_adv / t_d_tr,
            "variant_plan_area_U_m_s": u_ch_plan,
            "variant_plan_area_Pe": pe_plan,
            "Pe_channel_jet": u_in * CH_HEIGHT / D_SOLUTE,
        },
        "A7_cell_layer": {k: v for k, v in cell.items()
                          if not isinstance(v, np.ndarray)},
        "setup_B_G": setup_data,
        "flow_sweep": sweep,
        "sanity_checks": sc_data,
    }
    return results["A"]


# ----------------------------------------------------------------------------
# figures
# ----------------------------------------------------------------------------
def make_figures(a: dict) -> None:
    say("")
    say("FIGURES")
    say("-" * 78)

    # ---- ex2_a_fill.png ---------------------------------------------------
    labels = ["t_fill\nchamber flushing", "t_diff,perp\nh^2/(pi^2 D)",
              "t_diff,perp/2\nh^2/(4 D)", "t_diff,long\nL^2/D (6 cm)",
              "t_res,chamber\nL/U"]
    vals = [a["A2_filling"]["t_fill_s"],
            a["A5_diffusion"]["t_diff_transverse_pi2_s"],
            a["A5_diffusion"]["t_diff_transverse_half_s"],
            a["A5_diffusion"]["t_diff_longitudinal_s"],
            a["A6_peclet"]["t_adv_chamber_s"]]
    colors = [C_ACCENT, C_BLUE, "#4f81bd", C_GREEN, C_GREY]

    fig, ax = plt.subplots(figsize=(FIG_W, 2.9))
    x = np.arange(len(vals))
    bars = ax.bar(x, vals, color=colors, width=0.62, zorder=3)
    ax.set_yscale("log")
    ax.set_ylim(1e2, 1e8)
    ax.set_xticks(x)
    ax.set_xticklabels(labels, fontsize=8, rotation=30, ha="right",
                       rotation_mode="anchor")
    ax.set_ylabel("time  [s]   (log scale)")
    ax.set_title("Exercise 2A - flushing time vs. diffusion times\n"
                 "Q = 100 uL/min, D = 200 um^2/s,\nchamber 60 x 60 x 1 mm",
                 fontsize=8.5)
    for b, v in zip(bars, vals):
        ax.text(b.get_x() + b.get_width() / 2, v * 1.4, f"{v:.3g}",
                ha="center", va="bottom", fontsize=8)
    ax.axhline(a["A2_filling"]["t_fill_s"], color=C_ACCENT, ls="--", lw=1.0,
               alpha=0.7)
    # The line label goes in the empty right margin: bar 3 (1.8e7 s) is at
    # x = 3 and bar 4 tops out at 2160 s, so x > 4.4 is completely free.
    # It sits just ABOVE the dashed line so it does not collide with the
    # value label that bar 4 prints on the same height.
    ax.set_xlim(-0.6, 6.1)
    ax.annotate("t_fill = %.0f s" % a["A2_filling"]["t_fill_s"],
                xy=(4.55, a["A2_filling"]["t_fill_s"]),
                xytext=(4.55, a["A2_filling"]["t_fill_s"] * 1.25),
                fontsize=8, color=C_ACCENT, va="bottom", ha="left")
    p_fill = FIG / "ex2_a_fill.png"
    fig.savefig(p_fill)
    plt.close(fig)
    say(f"  wrote {p_fill}  ({p_fill.stat().st_size} bytes)")

    # ---- ex2_a_reynolds.png ----------------------------------------------
    q = np.logspace(np.log10(5.0), np.log10(5e5), 200)   # uL/min
    re_arr = np.array([reynolds(xq) for xq in q])
    re_op = reynolds(Q_NOMINAL_ULMIN)
    q_thresh = Q_NOMINAL_ULMIN * RE_LAMINAR_THRESHOLD / re_op

    fig, ax = plt.subplots(figsize=(FIG_W, 3.4))
    ax.loglog(q, re_arr, color="#9db8d0", lw=2.0, zorder=2)
    m_sweep = (q >= Q_LIST_ULMIN[0]) & (q <= Q_LIST_ULMIN[-1])
    ax.loglog(q[m_sweep], re_arr[m_sweep], color=C_BLUE, lw=2.6, zorder=3,
              label=r"Re $\propto Q$  (inlet channel)")
    ax.axvspan(Q_LIST_ULMIN[0], Q_LIST_ULMIN[-1], color=C_BLUE, alpha=0.07,
               zorder=1)
    ax.axhline(RE_LAMINAR_THRESHOLD, color=C_ACCENT, ls="--", lw=1.4,
               label="laminar threshold, Re = 2000")
    ax.axvline(q_thresh, color=C_GREY, ls=":", lw=1.2)
    # The grey threshold caption goes in the empty upper-right corner, above
    # the Re = 2000 threshold line and clear of the diagonal.
    ax.annotate(f"Re = 2000 at\nQ = {q_thresh/1000:.0f} mL/min",
                xy=(0.97, 0.03), xycoords="axes fraction",
                fontsize=8, color=C_GREY, ha="right", va="bottom",
                bbox=dict(fc="white", ec="none", alpha=1.0, pad=1.5))
    # The three part-F sweep points all sit on the same diagonal, and the
    # operating point (100 uL/min) is one of them, so the sweep points are
    # keyed in the legend instead of by individual callouts - one annotation
    # per point would have to cross the diagonal.
    ax.plot([qv for qv in Q_LIST_ULMIN], [reynolds(qv) for qv in Q_LIST_ULMIN],
            "s", color=C_GREEN, ms=6, zorder=5,
            label="part-F sweep: 10, 100, 1000 uL/min")
    ax.plot([Q_NOMINAL_ULMIN], [re_op], "o", color=C_ACCENT, ms=10, zorder=6,
            label="assignment operating point")
    # the one callout that is still needed, placed in the empty region BELOW
    # the diagonal and to the right of the marker
    ax.annotate(f"operating point:\n{Q_NOMINAL_ULMIN:g} uL/min, "
                f"Re = {re_op:.3f}",
                (Q_NOMINAL_ULMIN, re_op), textcoords="offset points",
                xytext=(12, 26), fontsize=8, color=C_ACCENT, ha="left",
                va="bottom",
                arrowprops=dict(arrowstyle="->", color=C_ACCENT, lw=1.0),
                bbox=dict(fc="white", ec="none", alpha=1.0, pad=1.5))
    ax.set_xlabel(r"flow rate  $Q$  [uL/min]")
    ax.set_ylabel("Reynolds number  [-]")
    ax.set_title("Exercise 2A - inlet-channel Reynolds number vs. flow rate\n"
                 "D_h = 1.333 mm, water at 293.15 K\n(shaded: part-F sweep)",
                 fontsize=8.5)
    # The diagonal Re-proportional line runs through the whole panel, so the
    # legend goes underneath the axes where it cannot cover anything.
    ax.legend(fontsize=8, loc="upper center", bbox_to_anchor=(0.5, -0.26),
              handlelength=1.6, labelspacing=0.4, frameon=True,
              facecolor="white", edgecolor="none", framealpha=1.0)
    p_re = FIG / "ex2_a_reynolds.png"
    fig.savefig(p_re)
    plt.close(fig)
    say(f"  wrote {p_re}  ({p_re.stat().st_size} bytes)")

    # ---- ex2_cell_layer.png ----------------------------------------------
    # Three panels stacked, each the full width of the figure.  (a) and (b)
    # used to sit side by side and were therefore only ~2.1 in wide at the
    # placement width, which is too narrow to read; stacking them gives (a),
    # (b) and (c) the same width, and the figure grows taller instead.
    cell = a["A7_cell_layer"]
    z = np.array(cell["z_profile_m"])
    c_pin = np.array(cell["c_profile_mol_m3"])
    sweep = a["flow_sweep"]
    styles = [("-", C_BLUE), ("--", C_GREEN), (":", C_ACCENT)]

    fig = plt.figure(figsize=(4.78, 7.20))
    # Explicit axes boxes, in figure fractions: the three panels get exactly
    # the same width and the same height, and the bands between them are
    # sized for the title block of the panel below and the x-axis label of
    # the panel above.  A gridspec with a height_ratio does not give this -
    # it stretches the gaps instead and squeezes the two-line titles until
    # they read as one block of overlapping text.
    ax_a = fig.add_axes([0.135, 0.745, 0.850, 0.155])
    ax_b = fig.add_axes([0.135, 0.440, 0.850, 0.155])
    ax_c = fig.add_axes([0.135, 0.105, 0.850, 0.155])

    # (a) absolute, pinned surface (all three curves coincide exactly)
    for row, (ls, col) in zip(sweep, styles):
        ax_a.plot(z * 1e6, c_pin, ls, color=col, lw=2.0,
                  label=row["Q_label"])
    ax_a.plot([0], [cell["c_mid_mol_m3"]], "o", color=C_ACCENT, ms=8, zorder=6)
    ax_a.annotate(f"base {cell['c_mid_mol_m3']:.4f}",
                  xy=(0, cell["c_mid_mol_m3"]), xycoords="data",
                  xytext=(0.06, 0.86), textcoords="axes fraction",
                  fontsize=8, color=C_ACCENT, ha="left", va="center",
                  arrowprops=dict(arrowstyle="-", color=C_ACCENT, lw=0.8,
                                  shrinkA=2, shrinkB=4),
                  bbox=dict(fc="white", ec="none", alpha=1.0, pad=1.5))
    ax_a.set_xlabel("z [um] from the base", fontsize=8.5)
    ax_a.set_ylabel(r"$c$  [mol/m$^3$]", fontsize=8.5)
    ax_a.set_title("(a) pinned $c_s$ = 1 mol/m$^3$, all $Q$ coincide", fontsize=8.5,
                 linespacing=1.5)
    ax_a.set_ylim(0, 1.12)
    ax_a.set_xticks([0, 25, 50, 75, 100])

    # (b) normalised
    for row, (ls, col) in zip(sweep, styles):
        ax_b.plot(z * 1e6, c_pin / c_pin.max(), ls, color=col, lw=2.0,
                  label=row["Q_label"])
    h = ax_b.axhline(cell["c_mid_over_c_s"], color=C_GREY, ls="--", lw=1.0)
    ax_b.set_xlabel("z [um] from the base", fontsize=8.5)
    ax_b.set_ylabel(r"$c/c_s$", fontsize=8.5)
    ax_b.set_title("(b) normalised profile, identical for all $Q$",
                   fontsize=8.5)
    ax_b.set_ylim(0, 1.12)
    ax_b.set_xticks([0, 25, 50, 75, 100])
    ax_b.text(5, 0.72, f"c/c$_s$ = {cell['c_mid_over_c_s']:.4f}", fontsize=7.5,
              color=C_GREY, va="center", ha="left")

    # (c) with the chamber-side film resistance -> flow-rate dependence.
    # The three curves span two decades of concentration, so this panel uses a
    # logarithmic y-axis.  The legend goes in the empty lower-left corner
    # (under the lowest curve), and each curve is labelled at its LEFT end,
    # where the three curves are far apart on the log scale, so no label
    # ever has to cross a curve.
    for row, (ls, col) in zip(sweep, styles):
        cs = row["c_surface_film_mol_m3"]
        c_mid_f = row["c_mid_film_mol_m3"]
        ax_c.plot(z * 1e6, cs * c_pin / c_pin.max(), ls, color=col, lw=2.0)
        ax_c.plot([0], [c_mid_f], "o", color=col, ms=7, zorder=6)
        # label the curves on the curve itself: a legend box either sits on
        # the data or falls off the canvas, and the three curves are far
        # apart on the log axis, so a direct label is unambiguous.

    ax_c.set_xlabel("depth into cell layer  z  [um]   (0 = base)",
                    fontsize=8.5)
    ax_c.set_ylabel(r"$c$  [mol/m$^3$]", fontsize=8.5)
    ax_c.set_title("(c) with the chamber-side film resistance:\n"
                   "1 mL/min gives the highest cell-layer concentration",
                   fontsize=8.5, linespacing=1.5)
    ax_c.set_yscale("log")
    ax_c.set_xlim(-6, 116)
    ax_c.set_ylim(4e-4, 0.45)
    ax_c.set_xticks([0, 25, 50, 75, 100])
    # the curves rise to the right, so the legend goes in the lower-left
    # corner, under the blue (lowest) curve

    for row, (ls, col) in zip(sweep, styles):
        ax_c.annotate(f"{row['c_surface_film_mol_m3']:.4f}",
                      xy=(100, row["c_surface_film_mol_m3"]),
                      xytext=(3, 0), textcoords="offset points",
                      fontsize=7, color=col, ha="left", va="center")

    # One key for the whole figure, below the panels and outside every axes:
    # a legend inside a panel covers the data, and one below a single axes
    # falls off the canvas.  The three colours mean the same thing in all
    # three panels.
    handles = [plt.Line2D([], [], color=col, ls=ls, lw=2.0)
               for ls, col in styles]
    fig.legend(handles, [r["Q_label"] for r in sweep],
               loc="upper center", bbox_to_anchor=(0.5, 0.995), ncol=3,
               fontsize=8, frameon=False, handlelength=2.2,
               columnspacing=2.4)

    p_cell = FIG / "ex2_cell_layer.png"
    fig.savefig(p_cell)
    plt.close(fig)
    say(f"  wrote {p_cell}  ({p_cell.stat().st_size} bytes)")

    # ---- ex2_b_cstr_benchmark.png -----------------------------------------
    t_fill = a["A2_filling"]["t_fill_s"]
    t = np.linspace(-5.0, 3.0 * t_fill, 800)
    tau = a["setup_B_G"]["cstr_reference"]["tau_s"]
    c_cstr = np.where(t <= t_fill, 1.0 - np.exp(-np.maximum(t, 0.0) / tau),
                      (1 - math.exp(-1)) * np.exp(-(t - t_fill) / tau))
    c_plug = np.clip(t / t_fill, 0.0, 1.0)
    fig, ax = plt.subplots(figsize=(FIG_W, 2.9))
    ax.plot(t, c_cstr, color=C_BLUE, lw=2.2,
            label=r"well-mixed chamber (CSTR): $1-e^{-t/\tau}$")
    ax.plot(t, c_plug, color=C_GREEN, lw=2.2, ls="--",
            label=r"plug-flow limit: $c_{avg}=\min(t/t_{fill},1)$")
    ax.axvline(t_fill, color=C_GREY, ls=":", lw=1.2)
    ax.text(t_fill + 40, 0.08, f"pulse ends\nt = {t_fill:.0f} s", fontsize=8,
            color=C_GREY)
    ax.set_xlabel("time  [s]")
    ax.set_ylabel("chamber-average concentration  [mol/m$^3$]")
    ax.set_title("Exercise 2B - analytical brackets for the\n"
                 "chamber-average concentration\n"
                 "(the COMSOL probe should lie between the curves)",
                 fontsize=8.5)
    ax.legend(fontsize=8, loc="upper right", handlelength=1.6,
              labelspacing=0.4, frameon=True, facecolor="white",
              edgecolor="none", framealpha=1.0)
    p_b = FIG / "ex2_b_cstr_benchmark.png"
    fig.savefig(p_b)
    plt.close(fig)
    say(f"  wrote {p_b}  ({p_b.stat().st_size} bytes)")


# ============================================================================
if __name__ == "__main__":
    a = part_a()
    make_figures(a)

    with open(NOTES / "ex2_analytical_results.json", "w", encoding="utf-8") as fh:
        json.dump(results, fh, indent=2, default=float)

    text = "\n".join(LOG)
    (NOTES / "ex2_analytical_results.txt").write_text(text, encoding="utf-8")
    print("")
    print(f"results written to {NOTES / 'ex2_analytical_results.txt'}")
    print(f"results written to {NOTES / 'ex2_analytical_results.json'}")
