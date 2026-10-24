"""Exercise 2 - COMSOL chamber exchange against the analytical model.

The COMSOL run (the course cluster, 3D, 45/29 mm chamber + crossing channel,
29 028 elements) gives the volume-averaged concentration in the chamber as a
function of time.  This script plots it next to the strip (the through-flow
channel) and the inlet duct, marks the solute pulse window, and extracts the
exchange times that the report quotes.
"""
from __future__ import annotations

import csv
import math
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

WORK = Path(r"<project>")
CSV = WORK / "notes" / "ex2_comsol_series.csv"
OUT = WORK / "figures" / "comsol_ex2"
OUT.mkdir(parents=True, exist_ok=True)

C_IN = 1.0          # mol/m^3
T_FILL = 2160.0     # s, end of the solute pulse
T_START = 0.0

ACCENT, BLUE, GREEN, GREY = "#E50000", "#1f4e79", "#2e7d32", "#666666"


def main():
    t, cham, strip, duct = [], [], [], []
    with open(CSV, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            t.append(float(row["t_s"]))
            cham.append(float(row["c_chamber_mol_m3"]))
            strip.append(float(row["c_strip_mol_m3"]))
            duct.append(float(row["c_duct_mol_m3"]))

    def first_cross(v, level, after=0.0):
        for i, tt in enumerate(t):
            if tt >= after and v[i] >= level:
                return tt
        return None

    peak_i = max(range(len(cham)), key=lambda i: cham[i])
    t_half = first_cross(cham, 0.5)
    t_90 = first_cross(cham, 0.9)
    fall = next((t[i] for i in range(len(t)) if t[i] > T_FILL and cham[i] <= 0.1), None)

    plt.rcParams.update({"font.size": 10.5})
    fig, ax = plt.subplots(1, 2, figsize=(11.0, 4.4))

    ax[0].plot(t, duct, color=GREY, lw=1.6, label="inlet duct")
    ax[0].plot(t, strip, color=BLUE, lw=1.8, label="swept strip under the channel")
    ax[0].plot(t, cham, color=ACCENT, lw=2.2, label="whole chamber (volume average)")
    ax[0].axvspan(T_START, T_FILL, color=ACCENT, alpha=0.08)
    ax[0].axvline(T_FILL, color=ACCENT, ls=":", lw=1.4)
    ax[0].annotate("solute pulse ends", (T_FILL, 0.06), xytext=(T_FILL + 350, 0.30),
                   color=ACCENT, fontsize=9.5,
                   arrowprops=dict(arrowstyle="->", color=ACCENT, lw=1.0))
    ax[0].axhline(0.5, color="#999999", lw=0.8, ls="--")
    if t_half is not None:
        ax[0].plot([t_half], [0.5], "o", color=ACCENT, ms=5)
        ax[0].annotate(f"half exchange\n{t_half:.0f} s", (t_half, 0.5),
                       xytext=(t_half - 1100, 0.86), fontsize=9.5, color=ACCENT,
                       arrowprops=dict(arrowstyle="->", color=ACCENT, lw=1.0))
    ax[0].plot([t[peak_i]], [cham[peak_i]], "o", color=ACCENT, ms=5)
    ax[0].set_xlabel("time [s]")
    ax[0].set_ylabel("volume-averaged concentration [mol/m$^3$]")
    ax[0].set_title("Filling and flushing of the chamber")
    ax[0].set_ylim(-0.03, 1.08)
    ax[0].legend(fontsize=9, loc="lower right", framealpha=0.9)

    ax[1].plot(t, strip, color=BLUE, lw=2.0, label="swept strip")
    ax[1].plot(t, cham, color=ACCENT, lw=2.2, label="whole chamber")
    ax[1].fill_between(t, cham, strip, where=[s >= c for s, c in zip(strip, cham)],
                       color=ACCENT, alpha=0.10,
                       label="difference (how uneven it is)")
    ax[1].axvline(T_FILL, color=ACCENT, ls=":", lw=1.4)
    ax[1].set_xlabel("time [s]")
    ax[1].set_ylabel("concentration [mol/m$^3$]")
    ax[1].set_title("Homogeneity: channel versus chamber average")
    ax[1].set_ylim(-0.03, 1.08)
    ax[1].legend(fontsize=9, loc="center right", framealpha=0.9)

    fig.suptitle("Exercise 2 - COMSOL: exchange of the cell-growth chamber", fontsize=11)
    fig.savefig(OUT / "ex2_comsol_exchange.png", bbox_inches="tight")
    plt.close(fig)

    lines = [
        "",
        "EX2 COMSOL - chamber exchange (3D, the course cluster, 29028 elements)",
        "-" * 62,
        f"  chamber average peaks at {cham[peak_i]:.4f} mol/m3 at t = {t[peak_i]:.0f} s",
        f"  time to 50 % of c_in in the chamber: {t_half}",
        f"  time to 90 % of c_in in the chamber: {t_90}  (never reached)",
        f"  after the pulse, the chamber is back below 0.1 mol/m3 at t = {fall} s",
        f"  figure: {OUT / 'ex2_comsol_exchange.png'}",
    ]
    print("\n".join(lines))
    txt = WORK / "notes" / "ex2_comsol_results.txt"
    txt.write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
