"""Exercise 4 - check the COMSOL electric field against the analytical decay.

The course model gives the field gradient above the electrode array as

    |grad|E|^2 = (32/pi) (V0^2/d^3) exp(-pi y / d)

so |E|^2 decays with a characteristic length d/pi = 3.18 um, and |E|
itself with 2d/pi = 6.37 um  (d = 10 um).  The law is defined at the GAP
CENTRE, which for this geometry (first electrode 50-60 um at 10 V, second
70-80 um at ground) is x = 65 um.

This script fits the decay length of |E| at four x positions and compares
the gap-centre value with the analytical prediction.
"""
from __future__ import annotations

import math
import re
from pathlib import Path

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

WORK = Path(r"<project>")
NOTES = WORK / "notes"
OUTFIG = WORK / "figures" / "comsol_ex4"
OUTFIG.mkdir(parents=True, exist_ok=True)

D_EL = 10e-6
V0 = 10.0

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

LINES = [
    ("gap_centre",   65.0, "gap centre (65 um)", C_ACCENT, "-"),
    ("elec_edge",    60.0, "10 V electrode edge (60 um)", C_BLUE, "--"),
    ("elec_centre",  55.0, "10 V electrode centre (55 um)", C_GREEN, "-."),
    ("gnd_edge",     70.0, "ground electrode edge (70 um)", C_GREY, ":"),
]


def read_eline(path: Path):
    rows = []
    for line in open(path, encoding="utf-8", errors="replace"):
        line = line.strip()
        if not line or line.startswith("%"):
            continue
        try:
            rows.append([float(v) for v in line.split(",")])
        except ValueError:
            continue
    a = np.array(rows, dtype=float)
    return a[:, 2], a[:, 3]                      # z [um], |E| [V/m]


def fit_decay(z, e, lo=2.0, hi=20.0):
    m = (z >= lo) & (z <= hi) & (e > 0)
    if m.sum() < 4:
        return None, None, None
    coef = np.polyfit(z[m], np.log(e[m]), 1)
    return -1.0 / coef[0], math.exp(coef[1]), m.sum()


def main():
    L_E_theory = 2.0 * D_EL * 1e6 / math.pi        # |E| decay length, 6.366 um
    # |E|^2 ~ exp(-pi z / d) has its e-folding length d/pi, so |E| has
    # 2d/pi.  The |E|^2 reference line used to be drawn at d/(2pi), which
    # contradicts the |E| line above it and the caption: the measured
    # lengths are 8.53 um for |E| and 4.266 um for |E|^2, exactly half.
    L_E2_theory = D_EL * 1e6 / math.pi              # |E|^2 decay length, 3.183 um
    print(f"analytical: |E| decay length = 2d/pi = {L_E_theory:.3f} um")
    print(f"            |E|^2 decay length = d/pi = {L_E2_theory:.3f} um")

    fig, ax = plt.subplots(1, 2, figsize=(FIG_W, 3.1))
    results = {}

    for name, x0, label, col, ls in LINES:
        p = NOTES / f"ex4_Eline_{name}.csv"
        if not p.is_file():
            print(f"  {name}: MISSING")
            continue
        z, e = read_eline(p)
        L, e0, nfit = fit_decay(z, e)
        if L is None:
            print(f"  {name}: fit failed")
            continue
        results[name] = {"x_um": x0, "L_E_um": L, "E0_V_m": e0, "n_fit": int(nfit)}
        ratio = L / L_E_theory
        print(f"  {label:34} L(|E|) = {L:6.3f} um   ratio to 2d/pi = {ratio:5.3f}"
              f"   |E|(0) = {e0:.4g} V/m")
        ax[0].semilogy(z, e, color=col, lw=1.7, ls=ls, label=label)

    if "gap_centre" in results:
        gc = results["gap_centre"]
        ax[0].semilogy(z, gc["E0_V_m"] * np.exp(-z / L_E_theory),
                       color="black", lw=1.4, ls="--",
                       label=rf"analytical $L=2d/\pi$ = {L_E_theory:.2f} $\mu$m")
    ax[0].set_xlabel("height above electrode plane z [$\\mu$m]", fontsize=8)
    ax[0].set_ylabel("electric field |E| [V/m]", fontsize=8)
    ax[0].set_title("Field decay above\nthe electrode array", fontsize=8)
    # The curves fill the lower-left of both panels, so the keys are drawn
    # as one shared legend underneath the whole row of panels.
    h0, l0 = ax[0].get_legend_handles_labels()

    if "gap_centre" in results:
        zg, eg = read_eline(NOTES / "ex4_Eline_gap_centre.csv")
        ax[1].semilogy(zg, eg ** 2, color=C_ACCENT, lw=1.8, label=r"COMSOL $|E|^2$")
        L2, E02, _ = fit_decay(zg, eg)
        if L2:
            ax[1].semilogy(zg, (E02 ** 2) * np.exp(-zg / L2), color=C_BLUE, lw=1.5,
                           ls="--", label=rf"fit, $L$ = {L2:.2f} $\mu$m")
        ax[1].axvline(L_E2_theory, color=C_GREEN, ls=":", lw=1.5,
                      label=rf"$d/\pi$ = {L_E2_theory:.2f} $\mu$m")
    ax[1].set_xlabel("height above electrode plane z [$\\mu$m]", fontsize=8)
    ax[1].set_ylabel(r"$|E|^2$ [V$^2$/m$^2$]", fontsize=8)
    ax[1].set_title(r"$|E|^2$ at the gap centre vs" "\n"
                    r"$\nabla|E|^2\propto e^{-\pi z/d}$", fontsize=8)
    h1, l1 = ax[1].get_legend_handles_labels()

    fig.suptitle("Exercise 4 - COMSOL electric field vs the analytical decay",
                 fontsize=9)
    keys, labels = [], []
    for hh, ll in (h0, l0), (h1, l1):
        for h, lab in zip(hh, ll):
            if lab not in labels:
                keys.append(h)
                labels.append(lab)
    fig.legend(keys, labels, loc="outside lower center", ncol=2,
               fontsize=7.5, handlelength=1.4, labelspacing=0.5,
               columnspacing=1.4, frameon=True, facecolor="white",
               edgecolor="none", framealpha=1.0)
    fig.savefig(OUTFIG / "ex4_efield_profile.png")
    plt.close(fig)

    lines = [
        "",
        "ELECTRIC FIELD CHECK (COMSOL vs the analytical decay)",
        "-" * 62,
        "  the analytical law |grad|E|^2 = (32/pi)(V0^2/d^3) exp(-pi z/d) means",
        f"  |E| decays with length 2d/pi = {L_E_theory:.3f} um and |E|^2 with d/pi = {L_E2_theory:.3f} um",
        "  (d = 10 um).  The law is defined at the gap centre, x = 65 um for this",
        "  electrode layout.",
        "",
    ]
    for name, x0, label, col, ls in LINES:
        if name in results:
            r = results[name]
            lines.append(f"  {label:34} L(|E|) = {r['L_E_um']:6.3f} um"
                         f"   ratio to 2d/pi = {r['L_E_um']/L_E_theory:5.3f}")
    lines.append(f"  figure: {OUTFIG / 'ex4_efield_profile.png'}")

    txt = NOTES / "ex4_comsol_results.txt"
    body = txt.read_text(encoding="utf-8")
    body = body.split("\nELECTRIC FIELD CHECK")[0].rstrip() + "\n"
    txt.write_text(body + "\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
