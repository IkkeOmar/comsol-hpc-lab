"""
Parse the COMSOL 6.4 Data exports of the Exercise-4 production run.

Files (produced by comsol/Ex4_FFF_new.java on the the course HPC cluster):
  comsol/prod/Ex4_particles.csv    - particle positions, 91 particles x 121 times
  comsol/prod/Ex4_field_dset1.csv  - E field on the mesh nodes (frequency domain)
  comsol/prod/Ex4_field_pdat.csv   - E field at the particle positions, per time

The COMSOL Data export writes a '%'-prefixed header block, then one
comma-separated data row per record.  The header block declares the number of
nodes/rows and the column layout, so the parser reads the layout from the
file instead of hard-coding it.
"""
from __future__ import annotations

from pathlib import Path

import numpy as np

WORK = Path(r"<project>")
PROD = WORK / "comsol" / "prod"


def read_comsol_csv(path: Path):
    """Return (header_dict, index_line, numpy array of the data rows)."""
    meta = {}
    index_line = None
    data_lines = []
    with path.open("r", encoding="utf-8", errors="replace") as fh:
        for raw in fh:
            line = raw.rstrip("\n").rstrip("\r")
            if line.startswith("%"):
                body = line[1:]
                if "," in body:
                    k, v = body.split(",", 1)
                    k = k.strip()
                    v = v.strip().strip('"')
                    if k.lower() == "index":
                        index_line = v
                    else:
                        meta[k] = v
                continue
            if not line.strip():
                continue
            data_lines.append(line)
    arr = np.loadtxt(data_lines, delimiter=",")
    return meta, index_line, arr


def _times_from_index(index_line: str, n_cols: int, n_components: int):
    """Recover the time stamps from the '% Index,' header line.

    The header lists one column per (component, time) pair, so the time
    stamps appear n_components times each; keep one of every n_components.
    """
    if not index_line:
        return None
    times = []
    for col in index_line.split(","):
        col = col.strip()
        if "@ t=" in col:
            times.append(float(col.split("@ t=")[1].strip()))
    if len(times) != n_cols:
        return None
    return np.array(times[::n_components], dtype=float)


def load_particles(path: Path = PROD / "Ex4_particles.csv"):
    """Return dict with t (s) and x, y, z arrays of shape (n_particles, n_times).

    The particle export columns are interleaved: qx(t0), qy(t0), qz(t0),
    qx(t1), ...
    """
    meta, index_line, arr = read_comsol_csv(path)
    n_rows, n_cols = arr.shape
    # The Data export writes the record index as the FIRST data column; the
    # '% Index,' header describes only the remaining columns.  Align the two.
    n_declared = len(index_line.split(",")) if index_line else n_cols - 1
    if n_cols == n_declared + 1:
        body = arr[:, 1:]
    else:
        body = arr
    n_times = body.shape[1] // 3
    times = _times_from_index(index_line, body.shape[1], 3)
    if times is None:
        times = np.arange(n_times, dtype=float)
    qx = body[:, 0::3]
    qy = body[:, 1::3]
    qz = body[:, 2::3]
    return {"t": times, "x": qx, "y": qy, "z": qz,
            "meta": meta, "n_particles": n_rows, "n_times": n_times}


def load_field_nodes(path: Path = PROD / "Ex4_field_dset1.csv"):
    """Return (x, y, z, E2, Ex, Ey, Ez) in SI units for the mesh-node export.

    The export declares '% Length unit,um', so the coordinate columns are
    micrometres and must be converted; the field columns are already SI.
    """
    meta, index_line, arr = read_comsol_csv(path)
    unit = meta.get("Length unit", "m").strip()
    scale = 1e-6 if unit.lower() in ("um", "µm", "micron", "micrometer") else 1.0
    x = arr[:, 0] * scale
    y = arr[:, 1] * scale
    z = arr[:, 2] * scale
    E2 = arr[:, 3]
    Ex = arr[:, 4]
    Ey = arr[:, 5]
    Ez = arr[:, 6]
    return {"x": x, "y": y, "z": z, "E2": E2, "Ex": Ex, "Ey": Ey, "Ez": Ez,
            "meta": meta, "unit": unit}


def load_field_at_particles(path: Path = PROD / "Ex4_field_pdat.csv"):
    """Return (t, E2, Ex, Ey, Ez) of shape (n_particles, n_times)."""
    meta, index_line, arr = read_comsol_csv(path)
    n_rows, n_cols = arr.shape
    n_declared = len(index_line.split(",")) if index_line else n_cols - 1
    body = arr[:, 1:] if n_cols == n_declared + 1 else arr
    # columns: E2(t0), Ex(t0), Ey(t0), Ez(t0), E2(t1), ...
    n_times = body.shape[1] // 4
    times = _times_from_index(index_line, body.shape[1], 4)
    if times is None:
        times = np.arange(n_times, dtype=float)
    return {"t": times, "E2": body[:, 0::4], "Ex": body[:, 1::4],
            "Ey": body[:, 2::4], "Ez": body[:, 3::4], "meta": meta}


if __name__ == "__main__":
    p = load_particles()
    print("particles:", p["n_particles"], "times:", p["n_times"])
    print("t range:", p["t"][0], "->", p["t"][-1])
    print("x[0,:6] um:", p["x"][0, :6])
    print("y[0,:6] um:", p["y"][0, :6])
    print("z[0,:6] um:", p["z"][0, :6])
    print("z at t=120, first 5 particles (um):", p["z"][:5, -1])
    print("x at t=120, first 5 particles (um):", p["x"][:5, -1])

    f = load_field_nodes()
    print()
    print("field nodes:", f["x"].size, "unit:", f["unit"])
    print("E2 range:", f["E2"].min(), f["E2"].max())

    fp = load_field_at_particles()
    print()
    print("field-at-particles:", fp["E2"].shape, "times:", fp["t"][0], "->", fp["t"][-1])
