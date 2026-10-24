"""Quick exploration of the COMSOL production trajectory data."""
import sys
from pathlib import Path
import numpy as np

sys.path.insert(0, str(Path(r"<project>\scripts")))
from ex4_parse_comsol import load_particles, load_field_nodes, load_field_at_particles

p = load_particles()
t = p["t"]
x, y, z = p["x"], p["y"], p["z"]

print("=== shape:", x.shape, " nan count per column (first/last) ===")
nans = np.isnan(x).sum(axis=0)
print("nan at t=0..5:", nans[:6], " at t=118..120:", nans[-3:])

print()
print("=== per-particle exit time (first NaN) and x at exit ===")
for i in [0, 1, 45, 90]:
    idx = np.where(np.isnan(x[i]))[0]
    if len(idx):
        j = idx[0]
        print(f"  particle {i:2d} (y0={y[i,0]:5.1f}): exits at t={t[j-1]:6.1f} s, "
              f"x={x[i,j-1]:8.1f} um, z={z[i,j-1]:6.2f} um")
    else:
        print(f"  particle {i:2d}: never exits; final z={z[i,-1]:.3f} um x={x[i,-1]:.1f} um")

print()
print("=== z(t) for particle 0 (released z=50, y=5) ===")
for k in range(0, 121, 10):
    print(f"  t={t[k]:6.1f}  x={x[0,k]:9.2f}  y={y[0,k]:7.3f}  z={z[0,k]:8.4f}")

print()
print("=== z(t) for particle 45 (middle, y=50) ===")
for k in range(0, 121, 10):
    print(f"  t={t[k]:6.1f}  x={x[45,k]:9.2f}  y={y[45,k]:7.3f}  z={z[45,k]:8.4f}")

print()
print("=== final z for all particles that did NOT exit ===")
alive = ~np.isnan(x[:, -1])
print("alive at t=120:", alive.sum())
if alive.any():
    zf = z[alive, -1]
    print(f"  z final: min={zf.min():.4f} max={zf.max():.4f} mean={zf.mean():.4f}")
print()
print("=== z at exit for particles that left ===")
for i in [0, 1, 45, 90]:
    idx = np.where(np.isnan(x[i]))[0]
    if len(idx):
        j = idx[0]
        print(f"  particle {i:2d}: z at exit = {z[i,j-1]:.4f} um")
