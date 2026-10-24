"""
Independent 2D Laplace solver for the Ex-4 electrode array.

PURPOSE
-------
The course/Ex-3 formula
        |grad |E|^2| = (32/pi) (V0^2/d^3) exp(-pi z/d)
is an IDEALISED 1D result.  The COMSOL model solves the REAL 2D problem,
so before comparing trajectories one has to know what the real field looks
like.  This script solves that 2D problem directly, from scratch, with a
finite-difference Laplace solver, and compares it with (a) the course
formula and (b) the field COMSOL computed.

THE PHYSICAL PROBLEM
--------------------
Floor plane z = 0.  Electrode strips of width d = 10 um at pitch p = 20 um,
alternating between V0 = 10 V and 0 V.  The strips therefore repeat every
L = 2p = 40 um.

TWO possible models for the non-electrode floor (the 10 um gaps):

  (A) "DIRICHLET SQUARE WAVE" - the whole plane z = 0 is held at the
      electrode potential profile, i.e. the gap is part of the electrode.
      This is the model behind the usual Fourier formula for a periodic
      electrode array.

  (B) "INSULATING GAPS" - only the metal strips carry a fixed potential;
      the gaps are insulating (Zero Charge), which is what COMSOL's
      Electrostatics interface does by default on a boundary that is not
      a Terminal or a Ground.

The COMSOL model uses (B).  The solver below implements (B) exactly and
also (A) for comparison.

SETUP OF THE FD PROBLEM (model B)
---------------------------------
x is periodic with period L = 40 um.  Unknowns V_ij on a uniform grid.
  * interior: 5-point Laplace stencil
  * z = 0 on an electrode strip: Dirichlet V = 10 V or 0 V
  * z = 0 in a gap: Neumann, dV/dn = 0, i.e. the normal flux vanishes.
    Implemented with a ghost node: V(-dz) = V(+dz).
  * z = Zmax: V = Vbar (the mean of the electrode potentials) - the field
    has decayed by exp(-k1*Zmax) with k1 = 2*pi/L = pi/(2d), so this is
    effectively "field = 0" and the reference level is irrelevant.
  * x: periodic wrap.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
import scipy.sparse as sp
import scipy.sparse.linalg as spla

WORK = Path(r"<project>")
NOTES = WORK / "notes"
NOTES.mkdir(parents=True, exist_ok=True)

D = 10e-6          # electrode width [m]
P = 20e-6          # electrode pitch [m]
L = 2 * P          # potential pattern period (alternating 10 V / 0 V) [m]
V0 = 10.0          # electrode potential [V]
EL_X0 = 50e-6      # left edge of electrode 0 (absolute x) [m]

NZ = 700           # points in z
NX = 600           # points in one period in x


def solve_periodic(dx, dz, zmax, model="B", n_x=NX, n_z=NZ):
    """Solve the periodic 2D Laplace problem; return (x, z, V)."""
    Lx = L
    x = np.linspace(0.0, Lx, n_x, endpoint=False)
    z = np.linspace(0.0, zmax, n_z)
    nx, nz = len(x), len(z)
    N = nx * nz

    def idx(i, j):
        return (j % nz) * nx + (i % nx)

    # electrode index at a given x: electrode i covers [elX0+i*P, elX0+i*P+D)
    # relative to the cell origin, electrode 0 is at [0, D) within the cell
    # (the cell starts at x = EL_X0).
    xin = (x - EL_X0) % Lx
    k = np.floor(xin / P).astype(int)
    in_strip = (xin - k * P) < D
    is_high = (k % 2 == 0)          # electrode 0,2,4,... at V0

    rows, cols, vals = [], [], []
    rhs = np.zeros(N)

    def add(r, c, v):
        rows.append(r)
        cols.append(c)
        vals.append(v)

    for j in range(nz):
        for i in range(nx):
            p = idx(i, j)
            if j == 0:
                # bottom boundary
                if model == "A":
                    # Dirichlet everywhere: gap inherits the electrode value
                    v = V0 if is_high[i] else 0.0
                    add(p, p, 1.0)
                    rhs[p] = v
                else:
                    if in_strip[i]:
                        v = V0 if is_high[i] else 0.0
                        add(p, p, 1.0)
                        rhs[p] = v
                    else:
                        # Neumann: V(-dz) = V(+dz)  ->  2*V(0) - V(dz) = 0
                        add(p, p, 2.0)
                        add(p, idx(i, 1), -1.0)
                continue
            if j == nz - 1:
                add(p, p, 1.0)
                rhs[p] = 0.5 * V0      # mean level, irrelevant for the field
                continue
            add(p, p, -4.0)
            add(p, idx(i + 1, j), 1.0)
            add(p, idx(i - 1, j), 1.0)
            add(p, idx(i, j + 1), 1.0)
            add(p, idx(i, j - 1), 1.0)

    A = sp.csr_matrix((vals, (rows, cols)), shape=(N, N))
    V = spla.spsolve(A.tocsc(), rhs)
    return x, z, V.reshape(nz, nx).T      # shape (nx, nz)


def field_from_V(x, z, V):
    """Central-difference E = -grad V on the interior; returns (Ex, Ez)."""
    Ex = np.zeros_like(V)
    Ez = np.zeros_like(V)
    dx = x[1] - x[0]
    dz = z[1] - z[0]
    Ex[:, 1:-1] = -(V[:, 2:] - V[:, :-2]) / (2 * dx)
    Ex[:, 0] = -(V[:, 1] - V[:, 0]) / dx
    Ex[:, -1] = -(V[:, -1] - V[:, -2]) / dx
    Ez[1:-1, :] = -(V[2:, :] - V[:-2, :]) / (2 * dz)
    Ez[0, :] = -(V[1, :] - V[0, :]) / dz
    Ez[-1, :] = -(V[-1, :] - V[-2, :]) / dz
    return Ex, Ez


def dE2_dz(x, z, V, x_query, z_query, h=None):
    """d|E|^2/dz at (x_query, z_query) by finite differences of |E|^2."""
    Ex, Ez = field_from_V(x, z, V)
    E2 = Ex ** 2 + Ez ** 2
    j = int(np.argmin(np.abs(z - z_query)))
    i = int(np.argmin(np.abs(x - (x_query % L))))
    if h is None:
        h = max(1, j // 6)
    lo = max(0, j - h)
    hi = min(len(z) - 1, j + h)
    # dE2/dz from a least-squares line through the window
    zz = z[lo:hi + 1]
    ee = E2[i, lo:hi + 1]
    A = np.vstack([zz, np.ones_like(zz)]).T
    slope, _ = np.linalg.lstsq(A, ee, rcond=None)[0]
    return float(slope), float(E2[i, j]), float(z[j])


def main():
    print("=" * 78)
    print("INDEPENDENT 2D LAPLACE SOLVER - Ex-4 electrode array")
    print("=" * 78)
    print(f"  electrode width d = {D*1e6:.0f} um, pitch = {P*1e6:.0f} um,")
    print(f"  pattern period L  = {L*1e6:.0f} um (alternating {V0:.0f} V / 0 V)")
    print()

    zmax = 40e-6
    dx = L / NX
    dz = zmax / (NZ - 1)
    print(f"  grid: dx = {dx*1e6:.4f} um, dz = {dz*1e6:.4f} um, "
          f"zmax = {zmax*1e6:.0f} um  ({NX} x {NZ} nodes)")

    results = {}
    for model, name in (("B", "INSULATING GAPS (COMSOL)"), ("A", "DIRICHLET SQUARE WAVE")):
        x, z, V = solve_periodic(dx, dz, zmax, model=model)
        Ex, Ez = field_from_V(x, z, V)
        E2 = Ex ** 2 + Ez ** 2

        # sanity: check the Dirichlet electrodes really are at their potential
        xin = (x - EL_X0) % L
        k = np.floor(xin / P).astype(int)
        in_strip = (xin - k * P) < D
        err = 0.0
        for i in range(len(x)):
            if in_strip[i]:
                want = V0 if (k[i] % 2 == 0) else 0.0
                err = max(err, abs(V[i, 0] - want))
        print()
        print(f"  MODEL {model}: {name}")
        print(f"    max |V - V_electrode| on the strips : {err:.4e} V")
        print(f"    max |E| in the cell                : {np.sqrt(E2.max()):.6e} V/m")
        ie = int(np.argmin(np.abs(x - (EL_X0 + D / 2))))
        print(f"    |E| at the electrode centre (x={EL_X0*1e6+D/2*1e6:.0f} um, z=0): "
              f"{np.sqrt(E2[ie, 0]):.6e} V/m")
        results[model] = {"err": err, "E2": E2, "x": x, "z": z, "V": V}

    # ---- compare with the course formula ----------------------------------
    print()
    print("  d|E|^2/dz  AT THE GAP CENTRE (x = EL_X0 + 1.5d = 65 um)")
    print("   z[um]    model B (COMSOL)   model A (square wave)   course formula")
    xg = EL_X0 + 1.5 * D
    xe = EL_X0 + 0.5 * D
    rows = []
    for zz in [0.5e-6, 1e-6, 2e-6, 3e-6, 5e-6, 10e-6, 20e-6, 30e-6]:
        sB, eB, zB = dE2_dz(results["B"]["x"], results["B"]["z"], results["B"]["V"], xg, zz)
        sA, eA, zA = dE2_dz(results["A"]["x"], results["A"]["z"], results["A"]["V"], xg, zz)
        cf = (32.0 / math.pi) * (V0 ** 2 / D ** 3) * math.exp(-math.pi * zz / D)
        print(f"  {zz*1e6:6.2f}   {sB:+.6e}       {sA:+.6e}        {cf:+.6e}")
        rows.append({"z_um": zz * 1e6, "B": sB, "A": sA, "course": cf})
    print()
    print("  d|E|^2/dz  AT THE ELECTRODE CENTRE (x = 55 um)")
    for zz in [0.5e-6, 1e-6, 2e-6, 3e-6, 5e-6, 10e-6, 20e-6, 30e-6]:
        sB, eB, zB = dE2_dz(results["B"]["x"], results["B"]["z"], results["B"]["V"], xe, zz)
        sA, eA, zA = dE2_dz(results["A"]["x"], results["A"]["z"], results["A"]["V"], xe, zz)
        cf = (32.0 / math.pi) * (V0 ** 2 / D ** 3) * math.exp(-math.pi * zz / D)
        print(f"  {zz*1e6:6.2f}   {sB:+.6e}       {sA:+.6e}        {cf:+.6e}")

    # ---- decay rate of model B --------------------------------------------
    print()
    print("  DECAY RATE CHECK (model B, gap centre)")
    s1, _, _ = dE2_dz(results["B"]["x"], results["B"]["z"], results["B"]["V"], xg, 5e-6)
    s2, _, _ = dE2_dz(results["B"]["x"], results["B"]["z"], results["B"]["V"], xg, 6e-6)
    rate = -math.log(abs(s2 / s1)) / 1e-6
    print(f"    measured -d(ln|dE2/dz|)/dz = {rate:.4g} /m")
    print(f"    course formula decay rate   = pi/d = {math.pi/D:.4g} /m")
    print(f"    1D period-L decay rate     = 2*pi/L = {2*math.pi/L:.4g} /m")

    out = {"D": D, "P": P, "L": L, "V0": V0, "EL_X0": EL_X0,
           "zmax": zmax, "dx": dx, "dz": dz,
           "gap_centre_x_um": xg * 1e6, "electrode_centre_x_um": xe * 1e6,
           "gap_profile": rows,
           "measured_decay_rate": rate,
           "modelB_bc_error_V": results["B"]["err"],
           "modelA_bc_error_V": results["A"]["err"]}
    np.savez(NOTES / "ex4_laplace2d.npz",
             xB=results["B"]["x"], zB=results["B"]["z"], VB=results["B"]["V"],
             xA=results["A"]["x"], zA=results["A"]["z"], VA=results["A"]["V"])
    (NOTES / "ex4_laplace2d.json").write_text(json.dumps(out, indent=2, default=float),
                                               encoding="utf-8")
    print()
    print("  wrote " + str(NOTES / "ex4_laplace2d.json"))
    print("  wrote " + str(NOTES / "ex4_laplace2d.npz"))


if __name__ == "__main__":
    main()
