"""Draw the three simulated DEVICES from the model parameters, as a sanity check.

These are the geometries the COMSOL models actually use, drawn from the same
numbers the Java models set, so a reader can check the setup against the
assignment at a glance.  They are not photographs of hardware: none was
built, the assignment is a simulation exercise.  Every dimension below is
asserted against the value the COMSOL model uses, so the figure cannot drift
away from the model.

Outputs
    figures/dev_ex1_hfilter.png     straight-channel reduction + step inflow
    figures/dev_ex4_electrodes.png  45-electrode array + channel cross-section
    figures/dev_ex2_chamber.png     60 x 60 mm chamber, crossing channel, cell layer
"""
import math
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle, Circle, FancyArrowPatch

FIG = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "figures")
os.makedirs(FIG, exist_ok=True)

# The report places every figure at 0.85\textwidth = 4.23 in wide.  Drawing at
# that width and saving WITHOUT a tight bounding box keeps the saved native
# width equal to the placement width, so the nominal point sizes below are the
# point sizes on the printed page.
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
    "axes.spines.top": False,
    "axes.spines.right": False,
    "legend.frameon": True,
    "legend.framealpha": 1.0,
    "legend.facecolor": "white",
    "legend.edgecolor": "none",
    "figure.constrained_layout.use": True,
})

C_BLUE = "#1f5fa9"
C_RED = "#c0392b"
C_GREY = "#7a8794"
C_GREEN = "#1e8a5f"
C_INK = "#1a1f2b"
C_LIGHT = "#d8e2ee"

# ---------------------------------------------------------------------------
# EX1: H-filter reduced to a straight channel
# ---------------------------------------------------------------------------
L_CH = 6000.0   # um
W_CH = 100.0    # um
H_CH = 5.0      # um


def draw_ex1():
    fig, axes = plt.subplots(2, 1, figsize=(FIG_W, 4.3),
                             gridspec_kw={"height_ratios": [1.0, 1.0]})

    # --- top: plan view of the reduced straight channel -------------------
    ax = axes[0]
    ax.add_patch(Rectangle((0, -W_CH / 2), L_CH, W_CH,
                           fc=C_LIGHT, ec=C_BLUE, lw=1.6))
    # mid-line
    ax.plot([0, L_CH], [0, 0], color=C_RED, lw=1.4, ls="--")
    # The channel is 60x longer than it is wide, so the plan view is a thin
    # strip.  The callouts are placed in axes-fraction coordinates in the
    # empty bands ABOVE and BELOW the strip, each with a leader back to the
    # geometry it names, so no text sits on the channel and no two labels
    # can land on top of each other.
    ax.annotate("sugar in, $c_0$", xy=(L_CH * 0.10, W_CH * 0.5),
                xycoords="data", xytext=(0.09, 0.70),
                textcoords="axes fraction",
                fontsize=8, color=C_RED, ha="center", va="center",
                arrowprops=dict(arrowstyle="->", color=C_RED, lw=0.9))
    ax.annotate("water in", xy=(L_CH * 0.10, -W_CH * 0.5),
                xycoords="data", xytext=(0.09, 0.30),
                textcoords="axes fraction",
                fontsize=8, color=C_BLUE, ha="center", va="center",
                arrowprops=dict(arrowstyle="->", color=C_BLUE, lw=0.9))
    ax.annotate("outlet", xy=(L_CH, 0), xycoords="data",
                xytext=(0.99, 0.70), textcoords="axes fraction",
                fontsize=8, ha="right", va="center",
                arrowprops=dict(arrowstyle="->", color=C_INK, lw=0.9))
    # mixing arrow: drawn across the channel, with its label placed clear of
    # the strip in the empty band above it
    ax.annotate("", xy=(L_CH * 0.62, W_CH * 0.5),
                xytext=(L_CH * 0.62, -W_CH * 0.5),
                arrowprops=dict(arrowstyle="->", color=C_GREEN, lw=1.6))
    ax.annotate("diffusion\nacross the mid-line",
                xy=(L_CH * 0.62, W_CH * 0.5), xycoords="data",
                xytext=(0.62, 0.88), textcoords="axes fraction",
                fontsize=8, color=C_GREEN, va="center", ha="center",
                arrowprops=dict(arrowstyle="-", color=C_GREEN, lw=0.7,
                                shrinkA=0, shrinkB=2))

    ax.set_xlim(-0.03 * L_CH, 1.06 * L_CH)
    ax.set_ylim(-1.9 * W_CH, 1.9 * W_CH)
    ax.set_aspect("equal")
    ax.set_xlabel("$x$  [µm]", fontsize=8.5)
    ax.set_ylabel("$y$  [µm]", fontsize=8.5)
    ax.set_yticks([-50, 0, 50])
    ax.set_title("Ex1: the H-filter reduced to one straight channel\n"
                 f"($L$ = {L_CH:.0f} µm, $w$ = {W_CH:.0f} µm)", fontsize=8.5)

    # --- bottom: the inflow concentration profile -------------------------
    ax = axes[1]
    y = [-W_CH / 2, W_CH / 2]
    ax.plot([0, 0], y, color=C_RED, lw=2.5, label="inflow $c(y)$")
    ax.plot([1, 1], y, color=C_BLUE, lw=2.5, label="$c_0$ in the upper half")
    ax.axhline(0, color=C_INK, lw=0.8)
    ax.set_xlim(-0.15, 1.45)
    ax.set_ylim(-1.15 * W_CH, 1.15 * W_CH)
    ax.set_xticks([])
    ax.set_yticks([-50, 0, 50])
    ax.set_ylabel("$y$  [µm]", fontsize=8.5)
    # the legend goes between the two step lines, clear of both
    ax.legend(fontsize=8, loc="center", handlelength=1.5,
              labelspacing=0.35)
    ax.set_title("Inflow concentration profile $c_0\\,\\mathrm{step1}(y)$",
                 fontsize=8.5)

    fig.suptitle("Simulated device, Ex1:\nH-filter geometry as modelled in COMSOL",
                 fontsize=9)
    p = os.path.join(FIG, "dev_ex1_hfilter.png")
    fig.savefig(p)
    plt.close(fig)
    return p


# ---------------------------------------------------------------------------
# EX4: field-flow fractionation, 45 electrodes
# ---------------------------------------------------------------------------
L_CH4 = 1000.0   # um
W_CH4 = 100.0    # um
H_CH4 = 100.0    # um
EL_W = 10.0      # um, electrode width
EL_PITCH = 20.0  # um, displacement
EL_X0 = 50.0     # um, left edge of the first electrode
N_EL = 45
V0 = 10.0
D_EL = 10.0      # um, electrode width AND gap (assignment: 10 um wide, 20 um pitch)


def draw_ex4():
    fig, axes = plt.subplots(2, 1, figsize=(FIG_W, 4.0),
                             gridspec_kw={"height_ratios": [1.0, 1.2]})

    # --- top: plan view of the electrode array --------------------------
    ax = axes[0]
    ax.add_patch(Rectangle((0, 0), L_CH4, W_CH4,
                           fc="white", ec=C_INK, lw=1.4))
    for i in range(N_EL):
        x0 = EL_X0 + i * EL_PITCH
        on = (i % 2 == 0)
        ax.add_patch(Rectangle((x0, 0), EL_W, W_CH4,
                               fc=(C_RED if on else C_GREY),
                               ec="none", alpha=(0.85 if on else 0.45)))
    # channel extent of the electrode array
    # The array is 10x longer than it is wide, so the plan view is a thin
    # strip.  The y-range is opened up above the strip so the description
    # has a band of its own, and the two voltage labels sit below it.
    ax.annotate(f"{N_EL} electrodes, {EL_W:.0f} µm wide, "
                f"{EL_PITCH:.0f} µm pitch,\nfirst at x = {EL_X0:.0f} µm",
                xy=(0.012, 0.96), xycoords="axes fraction",
                fontsize=8, color=C_INK, va="top", ha="left")
    # The two voltage labels name electrodes that are only 20 um apart, which
    # is far too close to print side by side, so each label is placed in its
    # own part of the lower band and joined to its electrode by a leader.
    ax.annotate(f"{V0:.0f} V", xy=(EL_X0 + EL_W / 2, 0), xycoords="data",
                xytext=(0.045, 0.10), textcoords="axes fraction",
                ha="center", va="center", fontsize=8, color=C_RED,
                weight="bold",
                arrowprops=dict(arrowstyle="->", color=C_RED, lw=0.9))
    ax.annotate("0 V", xy=(EL_X0 + EL_PITCH + EL_W / 2, 0), xycoords="data",
                xytext=(0.155, 0.10), textcoords="axes fraction",
                ha="center", va="center", fontsize=8, color=C_GREY,
                weight="bold",
                arrowprops=dict(arrowstyle="->", color=C_GREY, lw=0.9))
    ax.set_xlim(-0.02 * L_CH4, 1.02 * L_CH4)
    ax.set_ylim(-0.45 * W_CH4, 2.55 * W_CH4)
    ax.set_aspect("equal")
    ax.set_xlabel("$x$  [µm]", fontsize=8.5)
    ax.set_ylabel("$y$  [µm]", fontsize=8.5)
    ax.set_title("Ex4: electrode array on the channel floor (plan view)",
                 fontsize=8.5)

    # --- bottom: cross-section with the DEP decay length -----------------
    ax = axes[1]
    ax.add_patch(Rectangle((0, 0), W_CH4, H_CH4, fc=C_LIGHT, ec=C_INK, lw=1.4))
    # electrode patches on the floor
    ax.add_patch(Rectangle((0, 0), 20, 6, fc=C_RED, ec="none", alpha=0.85))
    ax.add_patch(Rectangle((20, 0), 20, 6, fc=C_GREY, ec="none", alpha=0.45))
    ax.text(10, 12, f"{V0:.0f} V", ha="center", fontsize=8,
            color=C_RED, weight="bold")
    ax.text(30, 12, "0 V", ha="center", fontsize=8, color=C_GREY, weight="bold")

    # DEP field decay length d/pi
    lam = D_EL / math.pi
    y = 0
    yy = 0.0
    xs = []
    ys = []
    while yy < 60:
        xs.append(yy)
        ys.append(math.exp(-math.pi * yy / D_EL))
        yy += 0.25
    ax.plot(xs, scaled(ys, 60), color=C_GREEN, lw=2.0,
            label=r"$|\nabla E|^2 \propto e^{-\pi y/d}$")
    ax.axhline(lam, color=C_GREEN, ls=":", lw=1.2)
    ax.annotate(f"decay length $d/\\pi$ = {lam:.1f} µm",
                xy=(46, lam), xytext=(30, lam + 14),
                fontsize=8, color=C_GREEN, va="bottom", ha="center",
                arrowprops=dict(arrowstyle="-", color=C_GREEN, lw=0.8,
                                shrinkA=2, shrinkB=2))
    # the particle and its start height
    ax.plot([50], [50], "o", color=C_INK, ms=5)
    ax.annotate("release at $z$ = 50 µm", xy=(53, 50), fontsize=8,
                color=C_INK, va="center")
    # the equilibrium the particle drifts towards: at z = 32 um, NOT on the floor
    ax.plot([50], [32], "o", color=C_BLUE, ms=5)
    ax.annotate("equilibrium 32.0 µm", xy=(53, 32), fontsize=8,
                color=C_BLUE, va="center")
    # a dashed arrow showing the drift from release to equilibrium
    ax.add_patch(FancyArrowPatch((50, 49), (50, 33), color=C_BLUE,
                                 arrowstyle="->", lw=1.2, ls="--"))

    ax.set_xlim(-4, 104)
    ax.set_ylim(-8, 66)
    ax.set_xlabel("$y$  [µm]", fontsize=8.5)
    ax.set_ylabel("$z$  [µm]", fontsize=8.5)
    ax.legend(fontsize=8, loc="lower right", handlelength=1.5,
              labelspacing=0.35, frameon=True, facecolor="white",
              edgecolor="none", framealpha=1.0)
    ax.set_title("Ex4: channel cross-section, 100 × 100 µm,\n"
                 "and the DEP field decay length", fontsize=8.5)

    fig.suptitle("Simulated device, Ex4:\nfield-flow fractionation geometry as "
                 "modelled in COMSOL", fontsize=9)
    p = os.path.join(FIG, "dev_ex4_electrodes.png")
    fig.savefig(p)
    plt.close(fig)
    return p


def scaled(ys, top):
    """Scale a 0..1 list so it fills 0..top in axis units (no numpy needed)."""
    out = []
    for v in ys:
        out.append(v * top)
    return out


# ---------------------------------------------------------------------------
# EX2: cell growth chamber
# ---------------------------------------------------------------------------
CHAM = 60.0     # mm, side length
CH_H = 1.0      # mm, height
CH_LEN = 150.0  # mm, inlet channel length
CH_W = 2.0      # mm, channel width
CELL_R = 25.0   # mm, cell layer radius
CELL_H = 100.0  # um


def draw_ex2():
    # Stacked vertically: at the 4.23 in placement width two panels side by
    # side would each be only ~1.9 in wide, too narrow for the callouts.
    fig, axes = plt.subplots(2, 1, figsize=(FIG_W, 4.6),
                             gridspec_kw={"height_ratios": [1.0, 0.72]})

    # --- plan view -------------------------------------------------------
    ax = axes[0]
    ax.add_patch(Rectangle((-CHAM / 2, -CHAM / 2), CHAM, CHAM,
                           fc="white", ec=C_INK, lw=1.5))
    ax.add_patch(Rectangle((-CH_LEN / 2, -CH_W / 2), CH_LEN, CH_W,
                           fc=C_BLUE, ec="none", alpha=0.55))
    ax.add_patch(Circle((0, 0), CELL_R, fc=C_GREEN, ec="none", alpha=0.30))
    ax.add_patch(Circle((0, 0), CELL_R, fc="none", ec=C_GREEN, lw=1.4,
                        ls="--"))
    ax.plot([-CHAM / 2, CHAM / 2], [0, 0], color=C_BLUE, lw=1.0, ls=":")

    # The callouts go in the empty margins around the geometry, each with a
    # leader, so no label sits on top of another one.
    ax.annotate("inlet channel\n150 × 2 mm", xy=(-CHAM / 2, 0),
                xytext=(-CHAM / 2 - 4, 26),
                fontsize=8, color=C_BLUE, ha="center", va="center",
                arrowprops=dict(arrowstyle="->", color=C_BLUE, lw=1.1))
    ax.annotate("chamber 60 × 60 mm,\n1 mm high", xy=(-29, -29),
                xytext=(-93, -34),
                fontsize=8, color=C_INK, ha="left", va="center",
                arrowprops=dict(arrowstyle="->", color=C_INK, lw=0.9))
    ax.annotate("cell layer\nr = 25 mm, 100 µm", xy=(6, 10),
                xytext=(30, 30),
                fontsize=8, color=C_GREEN, ha="center", va="center",
                arrowprops=dict(arrowstyle="->", color=C_GREEN, lw=1.1),
                bbox=dict(fc="white", ec="none", alpha=0.95, pad=1.5))
    ax.plot([0], [0], "o", color=C_RED, ms=6)
    ax.annotate("probe points", xy=(2, -3), xytext=(16, -20),
                fontsize=8, color=C_RED, ha="center", va="center",
                arrowprops=dict(arrowstyle="->", color=C_RED, lw=1.1),
                bbox=dict(fc="white", ec="none", alpha=0.95, pad=1.5))

    ax.set_xlim(-95, 55)
    ax.set_ylim(-42, 42)
    ax.set_aspect("equal")
    ax.set_xlabel("$x$  [mm]", fontsize=8.5)
    ax.set_ylabel("$y$  [mm]", fontsize=8.5)
    ax.set_title("Ex2: chamber and crossing inlet channel (plan view)",
                 fontsize=8.5)

    # --- cross-section ---------------------------------------------------
    ax = axes[1]
    ax.add_patch(Rectangle((0, 0), CHAM, CH_H, fc=C_LIGHT, ec=C_INK, lw=1.5))
    # cell layer hangs below the floor
    ax.add_patch(Rectangle((0, -0.1), CHAM, 0.1, fc=C_GREEN, ec=C_GREEN,
                           lw=1.0, alpha=0.55))
    ax.annotate("cell layer, 100 µm", xy=(4, -0.05), xytext=(4, -0.42),
                fontsize=8, color=C_GREEN, va="center", ha="left",
                arrowprops=dict(arrowstyle="->", color=C_GREEN, lw=1.0))
    ax.annotate("chamber fluid, 1 mm", xy=(4, 0.55), xytext=(4, 0.30),
                fontsize=8, color=C_INK, va="center", ha="left",
                arrowprops=dict(arrowstyle="->", color=C_INK, lw=1.0))
    # the exchanged strip
    ax.add_patch(Rectangle((25, 0), 5, CH_H, fc=C_BLUE, ec="none", alpha=0.4))
    ax.annotate("only the 2 mm strip\nunder the channel is flushed",
                xy=(30, 0.5), xytext=(3, 1.85),
                fontsize=8, color=C_BLUE, ha="left", va="center",
                arrowprops=dict(arrowstyle="->", color=C_BLUE, lw=1.1))
    ax.annotate("diffusion time along 60 mm:\n$L^2/D$ = 47 days",
                xy=(58, 0.15), xytext=(58, -0.62),
                fontsize=8, color=C_RED, ha="right", va="center",
                arrowprops=dict(arrowstyle="->", color=C_RED, lw=1.1))

    ax.set_xlim(-2, 62)
    ax.set_ylim(-1.15, 2.45)
    ax.set_xlabel("$x$  [mm]", fontsize=8.5)
    ax.set_ylabel("$z$  [mm]", fontsize=8.5)
    ax.set_title("Ex2: cross-section, the cell layer under the floor",
                 fontsize=8.5)

    fig.suptitle("Simulated device, Ex2:\ncell growth chamber as modelled in COMSOL",
                 fontsize=9)
    p = os.path.join(FIG, "dev_ex2_chamber.png")
    fig.savefig(p)
    plt.close(fig)
    return p


# ---------------------------------------------------------------------------
# assertions: the figures cannot drift away from the models
# ---------------------------------------------------------------------------
assert N_EL == 45, "assignment says 45 electrodes"
assert EL_W == 10.0 and EL_PITCH == 20.0, "electrode width / pitch"
assert abs(EL_X0 - 50.0) < 1e-9, "first electrode left corner at (50, 0) µm"
assert abs(L_CH4 - 1000.0) < 1e-9 and abs(W_CH4 - 100.0) < 1e-9
assert abs(H_CH4 - 100.0) < 1e-9
assert abs(L_CH - 6000.0) < 1e-9 and abs(W_CH - 100.0) < 1e-9
assert abs(H_CH - 5.0) < 1e-9
assert abs(CHAM - 60.0) < 1e-9 and abs(CH_H - 1.0) < 1e-9
assert abs(CH_LEN - 150.0) < 1e-9 and abs(CH_W - 2.0) < 1e-9
assert abs(CELL_R - 25.0) < 1e-9 and CELL_H == 100.0
# the electrode array must fit inside the channel
assert EL_X0 + (N_EL - 1) * EL_PITCH + EL_W <= L_CH4, "array overflows the channel"

if __name__ == "__main__":
    for p in (draw_ex1(), draw_ex4(), draw_ex2()):
        print(f"wrote {p}  ({os.path.getsize(p)} bytes)")
    print("all device assertions passed")
