# COMSOL + HPC lab — a scripted microfluidics workflow

This is the public part of a lab-on-a-chip project: the analytical models,
the COMSOL 6.4 model sources, the comparison scripts, the run logs and the
figures they produce — plus a description of the agent infrastructure that
built all of it.

The written assignment report and the course material it is based on are
deliberately **not** in this repository; they stay private.

Everything here follows one rule: **an analytical model first, a COMSOL
model second, and a scripted comparison between them third.** No number in
this repository was typed by hand. Every number is produced by a script that
is in this repository, and the scripts write their raw output to `notes/`.

## The agent framework

Hermes is the agent framework this workflow runs on. It is not a simulator
and it does not compute anything itself: it is the layer that *plans* the
work, *reads* the COMSOL documentation and the shipped examples, *writes*
the model classes and the analytical scripts, *drives* COMSOL head-less and
the cluster, and then *verifies* what came out.

What it adds on top of a shell:

- **A project-scoped knowledge base.** Each project carries a set of skills
  holding the measured API facts, the pitfalls and the workflow, so a later
  session starts from what the first one learned instead of from scratch.
- **On-disk memory.** The working notes, the session log and the page-audit
  ledger live in the repository, so the state of the work survives a context
  reset, a new machine or a new session.
- **Long-running compute without supervision.** The cluster jobs are
  submitted, monitored and collected automatically, and the job outputs are
  archived as provenance rather than trusted.
- **Independent checking.** A separate pass re-derives the numbers, measures
  every figure at the width it is printed at, and audits the rendered pages.
  The checkers are scripts, so a reader can run them as well.

The division of labour is simple: **Hermes plans and drives, COMSOL solves,
the cluster computes, and Python checks.**

---

## The COMSOL agent

The COMSOL side is driven entirely from the Java API, head-less, with no
GUI and no clicking. The workflow has four parts.

**1. The models are code.** Each model is a single Java class that builds
the geometry, sets the physics, sets the study sequence, solves, and prints
the quantities the assignment asks for as plain text. `comsol/Ex1_HFilter.java`
is the 2D H-filter (laminar flow plus transport of diluted species, a graded
mesh, and a line average across the outlet). `comsol/ex2/Ex2Chamber.java` is
the 3D cell-growth chamber (a rectangular pulse, a chamber mesh and a jet
mesh, and a cell layer with its own reaction). `comsol/Ex4_FFF_prod.java` is
the 3D field-flow fractionation channel (45 coplanar electrodes, a
frequency-domain AC field, and a Lagrangian particle trace). The three
`Ex4_FFF_v*` classes are the variants used to answer three separate
questions about the same model.

**2. The API is probed, not guessed.** COMSOL's API rejects unknown property
names at runtime, and the GUI names do not predict them. The agent therefore
writes small probe classes that ask the model what a property is called
before the real model uses it, and the answers are recorded in
`notes/ex4_comsol_api_findings.md`. The measured facts that came out of this
are not folklore: `looplevel` (not `outersolnum`) selects a stored solution
on a plot group; the dataset type for a cut plane is `CutPlane` (not
`CutPlane3D`); the potential of an Electrostatics model is the plain
variable `V`; `sourcetype=geometry` is what turns an image export into a
render of the geometry itself; and an unknown property throws and silently
skips the rest of a shared try block, so every property assignment gets its
own.

**3. The batch runner is guarded.** `comsol/run_comsol.sh` compiles the
class, checks that the compile actually produced a fresh class, refuses to
run a stale one, runs `comsolbatch`, and only reports success if the class
printed its own completion marker. It also captures the log, because a batch
job that exits zero can still have produced nothing.

**4. The figures come out of COMSOL, not out of Python.** `comsol/DeviceExport.java`
renders every device picture from COMSOL's own geometry and
`comsol/Ex1_Plots.java` and `comsol/Ex4_PlanPotential.java` export the
result fields. Python is never used to fake a simulation output.

## The HPC agent

The models that are too heavy for a workstation are compiled and run on the
university's LSF cluster, and the agent drives that end to end: the model
source is uploaded, submitted with `bsub`, monitored, and the job output is
downloaded, parsed and archived. The point is not only the compute — it is
the **provenance**. Every job that produced a number in the final report is
recorded with its job id, host, exit code and output file, and the outputs
themselves are kept in `notes/ex2_jobs/`. A number whose job output is not
in the repository is a number nobody can check.

The variant study in `notes/ex4_comsol_results.json` is the clearest
example: the same model was run four times on the cluster with one thing
changed each time — the AC interface, the mesh, the channel length — and the
four answers were compared against each other and against the analytical
model.

Credentials never enter the repository: the cluster password lives in a
git-ignored environment file and is read from it at submission time.

## The verification agent

The last part of the workflow is the part that decides whether anything is
finished.

- `scripts/verify_pdf.py` checks the compiled report: the page count, the
  sentences that were repaired, the key numbers, the unit glyphs, and that
  no page is blank.
- `scripts/audit_figure_sizes.py` measures every figure at the width it is
  actually printed at, because a figure authored at 14 inch and placed at
  4.7 inch puts its 8 pt labels on the page at 2.7 pt.
- `scripts/verify_devices.py` compares every device figure against COMSOL's
  own measured bounding box.
- `scripts/audit_math.py` checks the math delimiters of the report sources,
  and `scripts/fix_stray_dollars.py` is a bounded repair that can only
  remove a specific, audited kind of stray delimiter.
- `notes/page_audit.md` is the page-by-page visual audit ledger: one entry
  per page, every finding marked open, checked or fixed.

The workflow that these enforce is: **validate every figure on its own
before it goes into the document, render the document once, then audit the
finished document once** — and re-audit only the pages that changed.

## Layout

```
scripts/    the analytical models (Python), the COMSOL-vs-analytical
            comparisons, and the verification scripts
comsol/     the COMSOL 6.4 Java models, the export classes, the batch
            runner and the cluster submission scripts
notes/      the raw output of every run: analytical results, COMSOL logs,
            cluster job outputs, particle traces, and the audit ledgers
figures/    every figure the scripts produce
```

## Reproducing it

The analytical models need numpy, scipy and matplotlib:

```bash
python scripts/ex1_analytical.py
python scripts/ex2_analytical.py
python scripts/ex3_analytical.py
python scripts/ex4_analytical.py
```

The COMSOL models need COMSOL 6.4 and a license server:

```bash
cd comsol
"/c/Program Files/COMSOL/COMSOL64/Multiphysics/bin/win64/comsolcompile.exe" Ex1_HFilter.java
"/c/Program Files/COMSOL/COMSOL64/Multiphysics/bin/win64/comsolbatch.exe" \
    -inputfile Ex1_HFilter.class -methodcall run
```

`comsol/run_comsol.sh` wraps the compile, the run and the log capture, and
is the recommended way to launch a model.

The verification scripts run against a compiled report and a rendered page
set; `report/render_pages.py` in the private repository is the page
renderer, and `scripts/audit_figure_sizes.py` measures the result.

## What is deliberately not here

- the written assignment report and the course material it is based on;
- the compiled `.mph` models (they are large and they are reproducible from
  the Java sources here — the exact paths and byte sizes of every accepted
  model are in the private appendix);
- the manual page extracts that were used as the documentation source;
- anything identifying the author or the course staff.
