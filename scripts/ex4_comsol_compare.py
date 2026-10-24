"""Exercise 4 -- COMSOL vs analytical comparison.

Reads the particle trajectories exported from the COMSOL transient
(notes/ex4_traj_comsol.csv, 91 particles x 121 output times) and compares
them with the quasi-steady analytical model of scripts/ex4_analytical.py.

The analytical model is re-implemented here rather than imported, so this
script stands alone and cannot be broken by an edit elsewhere.

    vertical:  6*pi*mu*a*v_z = F_DEP(y) + F_buoy          (quasi-steady Stokes)
    F_DEP      = pi a^3 eps0 eps_r,m |Re K| (32/pi) V0^2/d^3 exp(-pi y/d)
    F_buoy     = (4/3) pi a^3 (rho_p - rho_m) g
    downstream: u(z) = 1.5 U_avg (1 - (2z/H - 1)^2)        (2D profile in z)
"""
from __future__ import annotations

import csv
import json
import math
import re
from pathlib import Path

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

WORK = Path(r"<project>")
NOTES = WORK / "notes"
FIGS = WORK / "figures"
OUTFIG = FIGS / "comsol_ex4"
OUTFIG.mkdir(parents=True, exist_ok=True)

EPS0 = 8.8541878128e-12
ETA = 1.0e-3
G = 9.80665
MU = 1.0e-3

EPS_R_P, SIG_P = 60.0, 0.01
EPS_R_M, SIG_M = 80.0, 5.5e-6
RHO_P, RHO_M = 1200.0, 1000.0
F_OP, V0, D_EL = 10e6, 10.0, 10e-6
DP = 2.4e-6
A = DP / 2.0
L_CH, W_CH, H_CH = 1000e-6, 100e-6, 100e-6
Q = 1.0e-9 / 3600.0
Z0 = 50e-6

C_ACCENT, C_BLUE, C_GREEN, C_GREY = "#E50000", "#1f4e79", "#2e7d32", "#555555"

# The report places this figure at 0.96\textwidth = 4.78 in wide.  Drawing at
# that width and saving WITHOUT a tight bounding box keeps the saved native
# width equal to the placement width, so the nominal point sizes below are the
# point sizes on the printed page.
FIG_W = 4.78

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


def cm_factor(eps_r_p, eps_r_m, sig_p, sig_m, f):
    """Complex Clausius-Mossotti factor (same closed form as ex3/ex4)."""
    w = 2.0 * math.pi * f
    ep = eps_r_p - 1j * sig_p / (w * EPS0)
    em = eps_r_m - 1j * sig_m / (w * EPS0)
    k = (ep - em) / (ep + 2.0 * em)
    return k.real, k.imag


def read_trajectory(path: Path):
    """Parse the COMSOL particle CSV.

    Format quirks found by inspecting the file:
      * the COLUMN-HEADER row starts with "%" ("% Index,comp1.qx (um) @ t=0,...")
        while the data rows do not - so a naive "skip every % line" parser
        swallows particle 1 as if it were the header;
      * a particle that has left the domain is written as NaN for all later
        times, so every row ends in a NaN block that must be trimmed.

    Returns times[s], x[um], y[um], z[um], each of shape (n_time, n_part).
    """
    col_header = None
    rows = []
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.rstrip("\n")
            if not line:
                continue
            if line.startswith("% Index"):
                col_header = line.lstrip("% ").split(",")
                continue
            if line.startswith("%"):
                continue
            parts = line.split(",")
            try:
                rows.append([float(p) for p in parts[1:]])
            except ValueError:
                continue
    if col_header is None or not rows:
        raise RuntimeError("no data in " + str(path))

    cols = col_header[1:]
    n_time = len(cols) // 3
    n_part = len(rows)

    times = []
    for k in range(n_time):
        m = re.search(r"t\s*=\s*([0-9.eE+-]+)", cols[3 * k])
        times.append(float(m.group(1)) if m else float(k))
    times = np.array(times, dtype=float)

    data = np.array(rows, dtype=float)              # (n_part, 3*n_time)
    qx = data[:, 0::3]                              # (n_part, n_time)
    qy = data[:, 1::3]
    qz = data[:, 2::3]

    # trim the trailing NaN block of every particle (it left the domain)
    alive = np.isfinite(qx) & np.isfinite(qy) & np.isfinite(qz)
    last = np.where(alive.any(axis=1), alive.shape[1] - 1 - np.argmax(alive[:, ::-1], axis=1), -1)
    print(f"  particles: {n_part}  times: {n_time}  "
          f"alive at final time: {int((last == n_time - 1).sum())}")
    for k in range(n_time):
        for arr in (qx, qy, qz):
            arr[last < k, k] = np.nan
    return times, qx.T, qy.T, qz.T                  # -> (n_time, n_part)


def analytical(t_end=120.0, n=4001):
    """Quasi-steady analytical trajectory of the mid-channel particle."""
    re_k = cm_factor(EPS_R_P, EPS_R_M, SIG_P, SIG_M, F_OP)[0]
    drag = 6.0 * math.pi * ETA * A
    u_avg = Q / (W_CH * H_CH)

    def f_net(y):
        grad = (32.0 / math.pi) * (V0**2 / D_EL**3) * math.exp(-math.pi * y / D_EL)
        f_dep = math.pi * A**3 * EPS0 * EPS_R_M * abs(re_k) * grad
        f_buoy = -(4.0 / 3.0) * math.pi * A**3 * (RHO_P - RHO_M) * G
        return f_dep + f_buoy

    def u_of_z(z):
        return 1.5 * u_avg * (1.0 - (2.0 * z / H_CH - 1.0) ** 2)

    t = np.linspace(0.0, t_end, n)
    x = np.zeros(n)
    z = np.zeros(n)
    z[0] = Z0
    dt = t_end / (n - 1)
    for i in range(1, n):
        x[i] = x[i - 1] + u_of_z(z[i - 1]) * dt
        z[i] = z[i - 1] + f_net(z[i - 1]) / drag * dt

    num = 24.0 * EPS0 * EPS_R_M * abs(re_k) * V0**2
    den = math.pi * D_EL**3 * (RHO_P - RHO_M) * G
    y_eq = (D_EL / math.pi) * math.log(num / den)
    return t, x, z, y_eq, re_k


def main():
    csv_path = NOTES / "ex4_traj_comsol.csv"
    times, qx, qy, qz = read_trajectory(csv_path)
    n_t, n_p = qx.shape
    print(f"COMSOL: {n_p} particles x {n_t} output times "
          f"({times[0]:.0f}..{times[-1]:.0f} s)")

    t_a, x_a, z_a, y_eq, re_k = analytical()
    print(f"analytical Re{{K}}(10 MHz) = {re_k:+.6f}   y_eq = {y_eq*1e6:.3f} um")

    # the particle released at the channel centre (y0 = 50 um)
    y0 = qy[0]
    mid = int(np.argmin(np.abs(y0 - 50.0)))
    print(f"mid-channel particle index = {mid} (released at y = {y0[mid]:.1f} um)")

    xc, zc = qx[:, mid], qz[:, mid]

    # ---- comparison over the time the particle stays in the channel -------
    # the particle leaves when x passes the 1 mm channel end
    finite = np.isfinite(xc) & np.isfinite(zc)
    alive_idx = np.where(finite)[0]
    out_idx = np.where(finite & (xc >= L_CH * 1e6))[0]
    if out_idx.size:
        t_out = times[out_idx[0]]
        x_out = xc[out_idx[0]]
    else:
        t_out = times[alive_idx[-1]] if alive_idx.size else times[-1]
        x_out = xc[alive_idx[-1]] if alive_idx.size else float("nan")
    # last time the particle was still inside the domain
    last_in = alive_idx[-1] if alive_idx.size else 0
    z_last = zc[last_in]
    x_last = xc[last_in]
    print(f"particle's last point inside the domain: t = {t_out:.1f} s, "
          f"x = {x_last:.1f} um, z = {z_last:.2f} um  (removed at the outlet)")

    # match the analytical trajectory to the COMSOL times
    xa_i = np.interp(times, t_a, x_a) * 1e6
    za_i = np.interp(times, t_a, z_a) * 1e6
    zc_um = zc

    in_ch = (times <= t_out) & np.isfinite(xc) & np.isfinite(zc_um)
    dz = zc_um[in_ch] - za_i[in_ch]
    dx = xc[in_ch] - xa_i[in_ch]

    # --- how fast does the particle actually move? ----------------------
    # A Stokes particle has no inertia, so dx/dt IS the local fluid velocity.
    u_avg = Q / (W_CH * H_CH)
    u_2d_peak = 1.5 * u_avg                      # analytical 2D profile
    v_comsol = float(xc[1] - xc[0]) if n_t > 1 else float("nan")
    v_2d = float(1.5 * u_avg * (1.0 - (2.0 * zc[0] / (H_CH * 1e6) - 1.0) ** 2)) * 1e6
    u_square_centre = 1.36 * u_avg * 1e6      # square-duct centreline peak
    v_ratio = v_comsol / v_2d if v_2d else float("nan")
    # the y-drift shows the particle is NOT in a 2D z-profile at all
    v_y_mid = float(qy[1, mid] - qy[0, mid]) if n_t > 1 else float("nan")
    dy_all = (qy[1] - qy[0]) if n_t > 1 else np.full(n_p, np.nan)
    v_y = float(np.nanmax(np.abs(dy_all)))
    y_sign = float(np.nanmedian(np.sign(dy_all[dy_all != 0]))) if np.any(dy_all != 0) else 0.0

    # ---- figures ----------------------------------------------------------
    fig, ax = plt.subplots(1, 3, figsize=(FIG_W, 3.2))

    ax[0].plot(xc, zc_um, color=C_ACCENT, lw=2.0, label="COMSOL (3D)")
    ax[0].plot(xa_i, za_i, color=C_BLUE, lw=1.8, ls="--", label="analytical")
    ax[0].axvline(L_CH * 1e6, color=C_GREY, ls="-.", lw=1.3, label="channel end")
    ax[0].axhline(y_eq * 1e6, color=C_GREEN, ls=":", lw=1.6,
                  label=rf"$y_{{eq}}$ = {y_eq*1e6:.1f} $\mu$m")
    ax[0].set_xlabel("downstream x [um]", fontsize=8)
    ax[0].set_ylabel("height above\nelectrodes [$\\mu$m]", fontsize=8)
    ax[0].set_title("Trajectory:\nCOMSOL vs analytical", fontsize=8)
    h0, l0 = ax[0].get_legend_handles_labels()
    ax[1].plot(times, zc_um, color=C_ACCENT, lw=2.0, label="COMSOL")
    ax[1].plot(times, za_i, color=C_BLUE, lw=1.8, ls="--", label="analytical")
    ax[1].axhline(y_eq * 1e6, color=C_GREEN, ls=":", lw=1.6,
                  label=rf"$y_{{eq}}$ = {y_eq*1e6:.1f} $\mu$m")
    ax[1].set_xlabel("time [s]", fontsize=8)
    ax[1].set_ylabel("height above\nelectrodes [$\\mu$m]", fontsize=8)
    ax[1].set_title("Vertical\napproach", fontsize=8)
    h1, l1 = ax[1].get_legend_handles_labels()


    # the x-difference is ~200x larger than the z-difference, so it needs its
    # own axis or it flattens the vertical comparison
    ax[2].plot(times[in_ch], dz, color=C_ACCENT, lw=2.0,
               label=r"$\Delta z$ (left axis)")
    ax[2].axhline(0, color=C_GREY, lw=0.9)
    ax[2].set_xlabel("time [s]", fontsize=8)
    ax[2].set_ylabel(r"$\Delta z$ [$\mu$m]", color=C_ACCENT, fontsize=8)
    ax[2].tick_params(axis="y", labelcolor=C_ACCENT, labelsize=8)
    ax2 = ax[2].twinx()
    ax2.plot(times[in_ch], dx, color=C_BLUE, lw=2.0, ls="--",
             label=r"$\Delta x$ (right axis)")
    ax2.set_ylabel(r"$\Delta x$ [$\mu$m]", color=C_BLUE, fontsize=8)
    ax2.tick_params(axis="y", labelcolor=C_BLUE, labelsize=8)
    ax2.grid(False)
    ax[2].set_title("Deviation in\nthe channel", fontsize=8)
    h2, l2 = ax[2].get_legend_handles_labels()
    h3, l3 = ax2.get_legend_handles_labels()
    # One shared key for the whole row.  The panels are only ~1.2 in wide,
    # so an in-axes legend would sit on the curves; the key goes underneath
    # the row instead.  Each entry is prefixed with the panel it belongs to
    # (the report caption calls them left / middle / right), so the shared
    # key stays unambiguous.
    keys: list = []
    labels: list = []
    for tag, hh, ll in (("left", h0, l0), ("middle", h1, l1),
                        ("right", h2, l2), ("right", h3, l3)):
        for h, lab in zip(hh, ll):
            keys.append(h)
            labels.append(f"{tag}: {lab}")

    fig.suptitle("Exercise 4 - COMSOL transient vs the analytical model",
                 fontsize=9)
    fig.legend(keys, labels, loc="outside lower center", ncol=2,
               fontsize=7.5, handlelength=1.4, labelspacing=0.5,
               columnspacing=1.0, frameon=True, facecolor="white",
               edgecolor="none", framealpha=1.0)
    fig.savefig(OUTFIG / "ex4_comsol_vs_analytical.png")
    plt.close(fig)

    # spread across all particles (they start at different y, so the y-profile
    # makes them differ slightly)
    spread_z = qz[-1] - qz[0]

    res = {
        "comsol": {
            "n_particles": int(n_p),
            "n_output_times": int(n_t),
            "t_range_s": [float(times[0]), float(times[-1])],
            "release_positions_um": {
                "x": [float(qx[0, 0]), float(qx[0, -1])],
                "y": [float(qy[0, 0]), float(qy[0, -1])],
                "z": [float(qz[0, 0]), float(qz[0, -1])],
            },
            "mid_particle_index": mid,
            "t_leaves_channel_s": float(t_out),
            "x_at_leave_um": float(x_out),
            "z_at_leave_um": float(z_last),
            "x_last_finite_um": float(x_last),
            "z_at_120s_um": float(zc[-1]),
            "x_at_120s_um": float(xc[-1]),
            "z_spread_at_120s_um": [float(spread_z.min()), float(spread_z.max())],
        },
        "analytical": {
            "ReK_10MHz": float(re_k),
            "y_eq_um": float(y_eq * 1e6),
        },
        "velocities": {
            "comsol_dxdt_um_s": v_comsol,
            "analytical_2d_profile_um_s": v_2d,
            "ratio_comsol_over_2d": v_ratio,
            "comsol_dydt_max_um_s": v_y,
            "comsol_dydt_mid_um_s": v_y_mid,
            "u_avg_um_s": u_avg * 1e6,
        },
        "agreement_inside_channel": {
            "max_abs_dz_um": float(np.abs(dz).max()),
            "max_abs_dx_um": float(np.abs(dx).max()),
            "mean_abs_dz_um": float(np.abs(dz).mean()),
            "dz_at_leave_um": float(dz[-1]),
            "dx_at_leave_um": float(dx[-1]),
        },
    }

    lines = [
        "EXERCISE 4 - COMSOL vs ANALYTICAL",
        "=" * 68,
        "",
        "COMSOL run (the course cluster, comsol/ex4_desktop/Ex4_FFF.java):",
        f"  3D channel 1000 x 100 x 100 um, 45 electrodes (23 x 10 V, 22 x GND)",
        f"  {n_p} particles released on the y = 5..95 um grid at x = 0, z = 50 um",
        f"  {n_t} output times, {times[0]:.0f}..{times[-1]:.0f} s",
        f"  mid-channel particle (released at y = {y0[mid]:.0f} um) is particle #{mid}",
        "",
        "Release check (t = 0):",
        f"  x = {qx[0,0]:.1f} .. {qx[0,-1]:.1f} um   (expected 0)",
        f"  y = {qy[0,0]:.1f} .. {qy[0,-1]:.1f} um   (expected 5 .. 95)",
        f"  z = {qz[0,0]:.1f} .. {qz[0,-1]:.1f} um   (expected 50)",
        "",
        "Analytical reference:",
        f"  Re{{K}}(10 MHz) = {re_k:+.6f}  -> negative DEP",
        f"  y_eq = {y_eq*1e6:.3f} um",
        "",
        "Key comparison numbers:",
        f"  particle leaves the 1 mm channel at t = {t_out:.1f} s",
        f"     x = {x_out:.1f} um, z = {zc[out_idx[0]] if out_idx.size else zc[-1]:.2f} um",
        f"  at t = 120 s (extrapolated beyond the channel):",
        f"     x = {xc[-1]:.1f} um, z = {zc[-1]:.2f} um",
        f"  max |z_COMSOL - z_analytical| while inside the channel = {np.abs(dz).max():.3f} um",
        f"  max |x_COMSOL - x_analytical| while inside the channel = {np.abs(dx).max():.3f} um",
        f"  mean |dz| = {np.abs(dz).mean():.3f} um",
        "",
        "Vertical motion (the physics that sets the equilibrium):",
        f"  max |z_COMSOL - z_analytical| inside the channel = {np.abs(dz).max():.3f} um",
        f"  mean |dz| = {np.abs(dz).mean():.3f} um",
        f"  z when the particle leaves the channel = {z_last:.2f} um",
        f"  (equilibrium y_eq = {y_eq*1e6:.2f} um, NOT reached)",
        "",
        "Downstream motion:",
        f"  COMSOL dx/dt at release = {v_comsol:.2f} um/s",
        f"  analytical 2D profile   = {v_2d:.2f} um/s   (ratio {v_ratio:.3f})",
        f"  square-duct centre peak = {u_square_centre:.2f} um/s",
        f"  largest |dy/dt| at release = {v_y:.2f} um/s (wall particles drift "
        "towards the",
        f"  centreline; the centre particle's own drift is {v_y_mid:.2f} um/s).  The",
        "  COMSOL channel is 3D, the analytical reference assumes a 2D profile",
        "  in z only.",
        f"  max |x_COMSOL - x_analytical| inside the channel = {np.abs(dx).max():.1f} um",
        "",
        "Interpretation:",
        "  The vertical physics - the part that actually sets the equilibrium -",
        f"  agrees to ~{np.abs(dz).max():.1f} um over the whole in-channel travel.",
        "  The downstream distance differs because the analytical reference uses a",
        "  2D parallel-plate profile while COMSOL solves the 3D square duct, so the",
        "  particle is carried faster and drifts in y as well.  Both agree on the",
        "  headline point: the particle leaves the 1 mm channel long before it",
        f"  reaches y_eq = {y_eq*1e6:.1f} um (COMSOL z at the outlet {z_last:.1f} um).",
        "",
        "FIGURES",
        f"  {OUTFIG / 'ex4_comsol_vs_analytical.png'}",
    ]
    (NOTES / "ex4_comsol_results.txt").write_text("\n".join(lines), encoding="utf-8")
    (NOTES / "ex4_comsol_results.json").write_text(json.dumps(res, indent=2), encoding="utf-8")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
