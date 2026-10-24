# Exercise 2 — COMSOL on the the course cluster: how every number was produced

This is the provenance record for the COMSOL part of Exercise 2.  Every
number in the report's §C2 and §F2 comes from one of the jobs listed here.

## The model

`comsol/ex2/Ex2Chamber.java` — the assignment geometry in 3D: a
60 x 60 x 1 mm chamber split into the swept strip under the crossing channel
and the two side regions so the mesh can be graded, with 45 mm inlet and
outlet ducts, Laminar Flow plus Transport of Diluted Species, a mass-flow
inlet at 100 uL/min, and the solute pulse as a rectangle function from
-5 s to t_fill = 2160 s with a 5 s transition zone and two continuous
derivatives.

Compiled locally with `comsolcompile.exe`; the bytecode runs unmodified on
the cluster's Linux JVM.

## The three modes

The mode is **baked into each batch script**, never passed through the
environment or as a trailing argument:

* `comsol/ex2/ex2_bsub_bc.sh` — mode `bc`: the base case. Chamber, duct and
  swept-strip averages against time, three point probes, mesh and volume
  diagnostics, and the axis export behind the exchange figure.
* `comsol/ex2/ex2_bsub_ef.sh` — mode `ef`: adds the cell layer (100 um on
  the chamber floor, reaction -k c with k = 0.15 mol/m^3/s,
  D_eff = 0.7 D_sp = 1.4e-10 m^2/s) on a twelve-layer prism mesh, and the
  stationary flow-rate sweep at 10, 100 and 1000 uL/min.
* `comsol/ex2/ex2_bsub_d.sh` — mode `d`: the part-D distributor variant.
  Its numbers are **not** used in the report.

## The commands

```bash
# from $HOME/a2, with Ex2Chamber.class already uploaded there
bsub < ex2_bsub_bc.sh     # parts B + C
bsub < ex2_bsub_d.sh      # part D
bsub < ex2_bsub_ef.sh     # parts E + F
```

`bsub` prints `Job <id> is submitted to queue <queue>`.  The job id is the
only handle on the run, so it was written down immediately — the eleven ids
below were read back from the cluster, not from memory:

```
Job <id> is submitted to queue hpc
```

Retrieval:

```bash
python scripts/hpc_ssh.py run 'ls -la $HOME/a2/ex2_*.out'
python scripts/hpc_ssh.py run 'cd $HOME/a2 && tar czf ex2jobs.tgz ex2_*.out'
python scripts/hpc_ssh.py get /zhome/29/d/167641/a2/ex2jobs.tgz notes/ex2_jobs/ex2jobs.tgz
```

The eleven batch outputs are archived in `notes/ex2_jobs/ex2_<jobid>.out`.

## What each job produced

| job | mode | elements | what it produced |
|---|---|---|---|
| 29804058 | bc | 29,028 | first base-case run |
| 29804082 | bc | 29,028 | base case with diagnostics |
| 29804083 | bc | 29,028 | base case, duplicate |
| 29804199 | bc | 4,488 | coarse mesh, mesh iteration |
| 29804200 | ef | 6,961 | first cell-layer run, mesh iteration |
| 29804201 | d | 1,572 | first distributor variant, mesh iteration |
| **29804212** | bc | 29,028 | **the run behind the exchange figure** |
| 29804213 | ef | 121,543 | cell layer, twelve-layer mesh |
| 29804214 | d | 11,386 | distributor variant |
| 29804324 | d | 41,400 | 60 mm distributor variant |
| **29804448** | ef | 183,847 | **the cell-layer sweep in the report** |

## The numbers the report uses

From job 29804212 (bc, 29,028 elements):

* mesh: 29,028 elements (prisms, triangles, quads), min quality 0.2536,
  mean quality 0.8364
* total fluid volume 3.78e-6 m^3 (chamber 3.60e-6 plus ducts 0.18e-6)
* flow through the inlet 1.666666666666667e-9 m^3/s = 100.00000000000001 uL/min
* mean |u| in the swept strip 9.647863970304331e-5 m/s
* mean pressure in the swept strip 0.7929790935059836 Pa
* chamber average peaks at 0.7767 mol/m^3 at t = 2195 s
* time to 50 % of c_in in the chamber: 1125.0 s
* time to 90 %: never reached in the 6480 s window
* back below 0.1 mol/m^3 at t = 5065 s

From job 29804448 (ef, 183,847 elements), the stationary sweep:

| | 10 uL/min | 100 uL/min | 1000 uL/min |
|---|---|---|---|
| cell domain, average | 0.00168678 | 0.0102964 | 0.0401321 |
| cell mid-depth probe (50 um in) | 0.00108280 | 0.0202795 | 0.111966 |
| cell surface probe (2 um below the floor) | 0.00508248 | 0.0951104 | 0.526376 |
| chamber average | 0.0500312 | 0.0841767 | 0.128647 |

The same sweep on the 121,543 element mesh (job 29804213) gives
0.00170895 / 0.0108772 / 0.0474620 for the cell-domain average, and
0.0306306 / 0.196585 for the mid-depth probe at 100 and 1000 uL/min.  The
chamber average agrees between the two meshes to within 6 %; the point
probes do not, so only the domain averages are quoted in the report.

## The two traps this cost time

* `MODE=ef bsub < script` silently ran every job in `bc` mode: LSF does not
  propagate the submitting shell's exported variables into the job.  The
  mode is baked into the script.
* `bsub < script ef` does not work either: `bsub` treats `ef` as a target
  file and stops parsing the `#BSUB` directives, so the job runs with the
  default 15 min and 1 GB.
