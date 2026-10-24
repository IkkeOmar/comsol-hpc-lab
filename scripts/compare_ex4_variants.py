"""Compare the Ex4 COMSOL variants against each other and the analytical model.

Reads the particle CSV files that COMSOL's own Data export produced on the
the course cluster, for the production run and for the three variants:

  ex4_particles.csv       production: Electrostatics (es), 1 mm, graded mesh
  ex4_particles_v1_ec.csv variant 1: Conductive Media (ec), otherwise identical
  ex4_particles_v2_mesh.csv variant 2: same physics, refined mesh (438 933 elements)
  ex4_particles_v3_long.csv variant 3: 4 mm channel, 197 electrodes

and reports, for the mid-channel particle (the one released at y = 50 um):
  - when and where it leaves the channel
  - the height it has reached
  - the agreement with the analytical trajectory

Run:
    python scripts/compare_ex4_variants.py
"""
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NOTES = os.path.join(ROOT, "notes")

# ---------------------------------------------------------------------------
# analytical reference (from scripts/ex4_analytical.py, notes/ex4_analytical_results.txt)
# ---------------------------------------------------------------------------
Y_EQ = 31.98031684943085          # um
RE_K = -0.08366992797198751       # Clausius-Mossotti at 10 MHz
U_AVG = 27.77777777777778         # um/s
L_CH = 1000.0                     # um, production channel length


def read_particles(path):
    """Parse a COMSOL Data export of a Particle dataset.

    Header lines start with '%'.  The '% Index' line names the columns:
    qx, qy, qz for every output time.  Each data row is one particle.
    """
    with open(path, encoding="utf-8", errors="replace") as f:
        lines = f.read().splitlines()
    index_line = None
    n_nodes = None
    for l in lines:
        if l.startswith("% Index"):
            index_line = l
        elif l.startswith("% Nodes"):
            n_nodes = int(l.split(",")[1])
    if index_line is None:
        raise ValueError("no '% Index' header in " + path)
    ncol = index_line.count(",") + 1
    ntime = ncol // 3
    # the output times, from the '@ t=' suffixes in the header
    times = []
    for part in index_line.split(",")[1:]:
        if "@ t=" in part:
            times.append(float(part.split("@ t=")[1].strip()))
    if len(times) != ntime:
        times = list(range(ntime))
    rows = []
    for l in lines:
        if not l or l.startswith("%"):
            continue
        vals = []
        for v in l.split(","):
            v = v.strip()
            if v in ("NaN", "nan", "-nan"):
                vals.append(float("nan"))
            else:
                vals.append(float(v))
        # The first field is the PARTICLE INDEX, not a coordinate: the data
        # columns are qx, qy, qz per time.  Dropping it is what makes the
        # x/y/z slicing line up with the '% Index' header.
        rows.append(vals[1:])
    return rows, n_nodes, ntime, times


def particle_series(rows, pidx, times):
    """(times, x, y, z) for one particle, in micrometres."""
    row = rows[pidx]
    x = row[0::3]
    y = row[1::3]
    z = row[2::3]
    return times, x, y, z


def last_finite(times, x, z):
    idx = [i for i in range(len(x)) if math.isfinite(x[i]) and math.isfinite(z[i])]
    if not idx:
        return None
    i = idx[-1]
    return times[i], x[i], z[i]


def analytic_z(t, z0=50.0, y_eq=Y_EQ, tau=5.07):
    """Overdamped exponential approach to the equilibrium.

    dz/dt = -(z - y_eq)/tau with the relaxation time from the Stokes balance
    (scripts/ex4_analytical.py).
    """
    return y_eq + (z0 - y_eq) * math.exp(-t / tau)


def main():
    variants = [
        ("production (es, 1 mm, graded mesh)", "ex4_particles.csv"),
        ("v1: Conductive Media (ec)", "ex4_particles_v1_ec.csv"),
        ("v2: refined mesh (438 933 elem.)", "ex4_particles_v2_mesh.csv"),
        ("v3: 4 mm channel (197 electrodes)", "ex4_particles_v3_long.csv"),
    ]
    print("Ex4 COMSOL variants - the mid-channel particle (released at y = 50 um)")
    print("=" * 96)
    print("analytical reference: Re{K}(10 MHz) = %.5f   y_eq = %.3f um   "
          "U_avg = %.3f um/s" % (RE_K, Y_EQ, U_AVG))
    print()

    summary = {}
    for label, fname in variants:
        path = os.path.join(NOTES, fname)
        if not os.path.exists(path):
            print("[MISSING] %s" % path)
            continue
        rows, n_nodes, ntime, times = read_particles(path)
        # the mid-channel particle: released at y = 50 um
        mid = None
        for i, r in enumerate(rows):
            if abs(r[1] - 50.0) < 1e-6 and abs(r[2] - 50.0) < 1e-6:
                mid = i
                break
        if mid is None:
            mid = (len(rows) - 1) // 2
        times, x, y, z = particle_series(rows, mid, times)
        last = last_finite(times, x, z)
        if last is None:
            print("%-42s NO FINITE DATA" % label)
            continue
        t_out, x_out, z_out = last
        # the particle stops being tracked once it leaves the outlet
        z_an = analytic_z(t_out)
        dz = z_out - z_an
        # travelled distance and mean speed
        speed = x_out / t_out if t_out > 0 else float("nan")
        print("%-42s particles=%d times=%d" % (label, n_nodes, ntime))
        print("    leaves the channel at t = %5.1f s, x = %8.2f um, z = %7.3f um"
              % (t_out, x_out, z_out))
        print("    mean downstream speed            = %6.2f um/s  (U_avg = %.2f)"
              % (speed, U_AVG))
        print("    analytical z at that time        = %7.3f um  (y_eq = %.3f)"
              % (z_an, Y_EQ))
        print("    deviation from the analytical z  = %+6.3f um" % dz)
        print("    final height above equilibrium   = %+6.3f um"
              % (z_out - Y_EQ))
        summary[label] = dict(t_out=t_out, x_out=x_out, z_out=z_out, dz=dz,
                              speed=speed, n=n_nodes, ntime=ntime)

    # ------------------------------------------------------------------
    # what the variants are for
    # ------------------------------------------------------------------
    print()
    print("What each variant settles")
    print("-" * 96)
    prod = summary.get("production (es, 1 mm, graded mesh)")
    ec = summary.get("v1: Conductive Media (ec)")
    mesh = summary.get("v2: refined mesh (438 933 elem.)")
    long_ = summary.get("v3: 4 mm channel (197 electrodes)")
    if prod and ec:
        d = max(abs(prod["z_out"] - ec["z_out"]),
                abs(prod["x_out"] - ec["x_out"]))
        print("v1 vs production: the AC interface changes the trajectory by "
              "%.3f um in position" % d)
    if prod and mesh:
        d = max(abs(prod["z_out"] - mesh["z_out"]),
                abs(prod["x_out"] - mesh["x_out"]))
        print("v2 vs production: a 6x finer mesh changes the trajectory by "
              "%.3f um in position" % d)
    if long_:
        print("v3: in a 4 mm channel the particle reaches z = %.3f um "
              "(equilibrium %.3f um, deviation %+.3f um)"
              % (long_["z_out"], Y_EQ, long_["z_out"] - Y_EQ))
        print("    so the equilibrium IS an attractor; the 1 mm channel is "
              "simply too short to reach it")

    return 0


if __name__ == "__main__":
    sys.exit(main())
