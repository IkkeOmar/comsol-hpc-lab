"""Check which quantity the instructor's reference table (assignment page 4)
corresponds to, using the analytical Neumann series of Eq. (1).

Table (assignment page 4):
    Dc (m^2/s)   Concentration (mol/m^3)
    1.0000E-11   0.94730
    5.0000E-11   0.89042
    1.0000E-10   0.84923
    5.0000E-10   0.66211
    1.0000E-9    0.56346
    5.0000E-9    0.50009

Assignment part (c) asks for "the average concentration on the UPPER HALF of
the outlet boundary" as a function of D.  A value that FALLS from ~0.95 to
0.50 as D grows means the UPPER half is the one that contains the sugar.

Two candidate readings are tested:
  A) upper-half AREA average, sugar in the upper half (y>0),
     profile = Eq.(1) mirrored in y, evaluated at the residence time.
  B) upper-half FLUX average (Poiseuille weights), same.
  C) wall value at y = +w/2 (particle-outlet wall), same.
  D) wall value at y = -w/2 (far wall), same.

For plug flow the steady-state outlet profile of the reduced H-filter equals
the 1D transient series at t = t_res = L*w*h/Q = 1.8 s, so the series can be
used to predict the table.
"""
import numpy as np

W = 100e-6
H = 5e-6
L = 6e-3
Q = 1.666667e-12
C0 = 1.0
N = 2000


def c_low(y, t, D, w=W, c0=C0, N=N):
    """Assignment Eq. (1): sugar in the LOWER half (y<0), 1 <= y <= w."""
    Y = np.atleast_1d(np.asarray(y, dtype=float))
    n = np.arange(1, N + 1)
    k = (2 * n - 1) * np.pi / w
    coef = 2.0 * c0 / ((2 * n - 1) * np.pi)
    dec = np.exp(-(k[:, None] ** 2) * D * t)
    sn = np.sin(k[:, None] * Y[None, :])
    return c0 / 2.0 - (coef[:, None] * sn * dec).sum(axis=0)


def c_up(y, t, D, w=W, c0=C0, N=N):
    """Mirror of Eq. (1): sugar in the UPPER half (y>0)."""
    return c0 - c_low(-np.asarray(y, dtype=float), t, D, w, c0, N)


def avg_area(y, c):
    return float(np.trapezoid(c, y) / (y[-1] - y[0]))


def avg_flux(y, c):
    u = 1.5 * (1.0 - (2.0 * y / W) ** 2)
    return float(np.trapezoid(u * c, y) / np.trapezoid(u, y))


table_D = [1e-11, 5e-11, 1e-10, 5e-10, 1e-9, 5e-9]
table_c = [0.94730, 0.89042, 0.84923, 0.66211, 0.56346, 0.50009]

y_up = np.linspace(0.0, W / 2, 4001)
t_res = L * W * H / Q
print(f"t_res = {t_res:.4f} s   (Q = {Q:.6e} m^3/s)")
print()
print(f"{'D':>10} {'table':>9} {'A area_up':>10} {'B flux_up':>10} "
      f"{'C wall+':>9} {'D wall-':>9}")
for D, tc in zip(table_D, table_c):
    cu = c_up(y_up, t_res, D)
    a = avg_area(y_up, cu)
    b = avg_flux(y_up, cu)
    cwall = float(c_up(np.array([W / 2]), t_res, D)[0])
    dwall = float(c_up(np.array([-W / 2]), t_res, D)[0])
    print(f"{D:10.1e} {tc:9.5f} {a:10.5f} {b:10.5f} {cwall:9.5f} {dwall:9.5f}")

print()
print("Same, but for the MIRRORED convention (sugar in the LOWER half):")
y_lo = np.linspace(-W / 2, 0.0, 4001)
for D, tc in zip(table_D, table_c):
    cl = c_low(y_lo, t_res, D)
    a = avg_area(y_lo, cl)
    b = avg_flux(y_lo, cl)
    print(f"{D:10.1e} {tc:9.5f} {a:10.5f} {b:10.5f}   "
          f"(lower-half area avg / flux avg)")

print()
print("--- t1 cross-check: 30 % at the FAR wall (opposite the sugar) ---")
for D in (2e-10,):
    for label, fn, yy in (("sugar lower, far wall y=+w/2", c_low, W / 2),
                          ("sugar upper, far wall y=-w/2", c_up, -W / 2)):
        lo, hi = 1e-9, 1e3
        for _ in range(300):
            mid = 0.5 * (lo + hi)
            if float(fn(np.array([yy]), mid, D)[0]) < 0.30:
                lo = mid
            else:
                hi = mid
        t = 0.5 * (lo + hi)
        print(f"  D={D:.1e}  {label}: t1 = {t:.6f} s  "
              f"(value {float(fn(np.array([yy]), t, D)[0]):.6f})")
