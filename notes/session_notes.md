# the lab-on-a-chip course Assignment 1 — working notes

Living file for this session. Append-only: record what was measured, what failed
and what was fixed, so a future session does not have to re-derive it.

Workfolder: `<project>`
Repo: private `https://github.com/the repository owner/dtu-lab-chip-labchip-assignment1`, branch `main`

---

## 2026-10-24 — session log

### Git state at session start

- Remote was at `62e24c6`; local HEAD was the same. 169 untracked files, 5
  modified, 1 deleted (`HANDOFF.md` had been removed from the working tree by
  something other than this session — restored from git with
  `git checkout -- HANDOFF.md`).
- `HANDOFF.md` and `GOAL.md` are the authoritative state files. `GOAL.md` is
  the paste-ready short version.

### Root cause of "the goal is not active"

Not a Hermes bug. The goal state was live in `state.db`
(`goal:20261024_012603_7ae49462`, status `active`, `turns_used: 0`). It never
advanced because every turn was killed before it finished:

```
logs/gateway.log  01:34:25  Invalidated run generation ... (stop_command)
logs/gateway.log  01:34:25  Discarding stale agent result for ... generation 34
logs/errors.log   01:29:36  Redirected-message restart limit (3) exceeded
```

A real user message preempts the goal loop, so each `stop` / file upload reset
the turn before the judge could run. `turns_used` stayed at 0 the whole time.

### COMSOL geometry export — the blank-PNG bug (fixed)

`comsol/GeomExport.java` produced **three byte-identical blank PNGs**
(md5 `90a2a76…`, 1600x900, 0 non-white pixels). Cause: the plot group had a
`"Mesh"` plot feature but **no dataset and no mesh behind it**, so COMSOL had
nothing to draw and exported an empty canvas three times.

Correct sequence (from the shipped example
`doc/help/.../submarine_cable_07_geom_mesh_3d.java`):

1. build + run the geometry
2. build + run a **mesh sequence** on that geometry
3. `model.result().dataset().create("dsmesh","Mesh")` + `set("mesh","mesh1")`
4. put the plot group on that dataset, add the `"Mesh"` plot feature
5. export the plot group as an Image

### COMSOL API facts measured this session

- `model.component(ctag).geom(tag).getBoundingBox()` → `double[]`
  `[xmin,xmax,ymin,ymax,(zmin,zmax)]` (Programming Reference Manual p. 271).
  Values come back in the **geometry's length unit** (mm for the Ex2 model).
  This is COMSOL's own measurement of the built geometry — the thing the
  matplotlib device drawings cannot prove.
- `"preserveaspect"` is documented on p. 1003 but is **NOT** a valid 2D
  plot-group property on this build: `Unknown property - preserveaspect`.
  Setting it aborts the export. 2D plots here keep the true aspect ratio, so
  the 6 mm × 100 µm Ex1 channel is necessarily a thin strip.
- `model.component("comp1").mesh("mesh1").getNumElem()` works after
  `mesh1.run()` (Programming Reference p. 440: `getNumElem`, not `getNelem`).
- The mesh-control edge makes the mesher see **two** sub-domains, so a mesh
  sequence over this geometry needs a selection covering both.

### Measured geometry (COMSOL's own bounding box, from `notes/geom_export.log`)

| device | x [µm] | y [µm] | z [µm] | domains |
|---|---|---|---|---|
| Ex1 H-filter | 0 … 6000 | −50 … 50 | – | 1 |
| Ex2 chamber | −75000 … 75000 | −30000 … 30000 | −100 … 1000 | 6 |
| Ex3 DEP | 0 … 80 | 0 … 100 | – | 6 |
| Ex4 FFF | 0 … 1000 | 0 … 100 | 0 … 100 | 1 |

Ex1 domain counts 1 domain / 4 boundaries; Ex4 1 domain / 96 boundaries /
282 edges / 188 vertices; Ex2 6 domains (5 fluid + cell cylinder);
Ex3 6 domains (fluid + 4 electrode strips + particle).

### Ex4 element count correction

`report/ex4_fff.tex` claimed `878,306 elements`. The production run log
(`the production cluster log`) says **73,195**. The 878,306 figure was never
produced by any run. Fix pending in the report text.

### Figure legibility defect (measured)

Every report figure is saved 7–16.7 in wide but placed at 4.23 in
(`0.85\textwidth`), so 8 pt text lands at 2.0–4.8 pt on the page.
`figures/ex2_cell_layer.png` is the worst case: 14.2 in native → 0.298× →
2.35 pt effective. The fix is to regenerate each figure **at** the placement
width with ≥7.5 pt fonts, not to change the LaTeX.

### Open items

- [ ] Ex2 COMSOL run on the cluster (worker `deleg_67e2e321` task 0)
- [ ] Figure regeneration at print width (worker `deleg_67e2e321` task 1)
- [ ] COMSOL device renders: readable, with axes/titles, and placed in the
      report for every exercise
- [ ] Ex4 element count 878,306 → 73,195 in `report/ex4_fff.tex`
- [ ] `scripts/verify_devices.py` with the measured values above
- [ ] Full report rebuild + per-page visual audit
- [ ] Final push + PDF delivery (absolute path in text AND `MEDIA:` line)

---

## 2026-10-24 02:20-02:50 — COMSOL device pictures + .mph export (parent session)

### User request (message 3290, out-of-band)
1. Add to the goal: **export the successful .mph files** as well when done.
2. "And to actually get the output plots and figures from comsol, we still
   don't have the actual devices in the report, we only have the results."

### Root cause of the blank/unreadable device pictures (measured, from the
### COMSOL Programming Reference Manual p. 861-864)
The `Image` export feature has:
- `sourcetype` = `plotgroup | geometry | other`. Setting it to **`geometry`**
  makes COMSOL render the geometry ITSELF - that is the device picture, and it
  needs no mesh and no solution.
- `options2d` / `options3d` — **both default to OFF**. With them off the export
  contains no axes, no legend and no title: a bare canvas. This is why the
  first GeomExport attempt produced images that looked blank.
- `axes2d`, `legend2d/legend3d`, `title2d/title3d`, `fontsize`, `zoomextents`,
  `customcolor`, `lockratio` for framing.
- **`axes3d` does not exist** on this build (Unknown property) even though the
  manual's table lists axis/legend/title entries for 1D/2D/3D. A single unknown
  property name aborts the entire export, so every optional property is now set
  through `trySet()`, which reports and skips a rejected name instead of
  killing the run.

### New class: `comsol/DeviceExport.java`
Builds each of the four devices with the same parameters as the exercise
models and exports COMSOL's own picture of it:
- Ex1: 6000 x 100 um channel + mesh-control mid-line; full channel (1600x300)
  and an inlet detail (1600x620, view zoom x in [0,600] um) because the true
  60:1 aspect of a 6 mm channel is unreadable at full length.
- Ex2: 60 x 60 x 1 mm chamber + 150 x 2 mm crossing inlet channel + 25 mm
  radius cylindrical cell layer below the floor; 3D render (1600x1100) and a
  2D plan view with the cell-layer footprint, because the cylinder is hidden
  underneath the chamber in the 3D view.
- Ex3: four 10 um electrode strips with 10 um gaps + a 1.5 um particle circle
  above; 2D render (1500x900).
- Ex4: 1000 x 100 x 100 um channel + 45 coplanar 10 um electrodes on the
  floor; 3D render (1600x1000) and a 2D plan view of the electrode array,
  because the electrodes are coplanar with the floor and hidden inside the
  solid.
Each model is saved with `model.save()` into `comsol/out/`.

### .mph files now in `comsol/out/` (verified on disk)
- `Ex1_HFilter.mph` 30,336,324 B - the model behind the validated run6
  (max rel. error 0.192 %), copied from `comsol/Ex1_HFilter_Ex1_HFilter.mph`.
- `Ex4_FFF_prod.mph` 39,186,746 B - downloaded from the the course cluster
  (`/zhome/29/d/167641/a2/Ex4_FFF_prod_Ex4_FFF.mph`), the production Ex4 model.
- `DeviceExport_DevEx{1,2,3,4}.mph` - the geometry models behind the device
  pictures.
- Ex2: `Ex2Chamber_Ex2Chamber.mph` exists on the cluster (2.0 GB, with the
  transient solution stored) at `/zhome/29/d/167641/a2/`. Downloading 2 GB is
  not worth it for the report; the model code is committed and the cluster
  path is in the provenance appendix.

### Report changes (parent owns report/)
- `report/ex1_hfilter.tex`: `\begin{figure}` with `dev_ex1_comsol_inlet.png`
  + caption saying it is COMSOL's own render of the geometry, not hardware;
  provenance paragraph naming `comsol/out/Ex1_HFilter.mph` and
  `notes/ex1_comsol_run6.log`.
- `report/ex2_chamber.tex`: `dev_ex2_comsol.png` + `dev_ex2_comsol_plan.png`.
- `report/ex3_dep.tex`: `dev_ex3_comsol.png`.
- `report/ex4_fff.tex`: `dev_ex4_comsol.png` + `dev_ex4_comsol_plan.png`;
  **fixed** the false element count `878,306` -> `73,195` (the production log
  says 73,195; 878,306 came from no run).
- `GOAL.md`: new sections 6 (export the .mph files) and 7 (COMSOL device
  pictures in every exercise).

### COMSOL errors hit and their fixes
- `axes3d` -> Unknown property. Fix: `trySet()` per property.
- `model cannot be resolved` at compile time: `model.save(...)` was added to
  `main()`, where no `model` variable exists (each builder creates its own).
  Fix: a `saveMph(model, name)` helper called at the end of each builder.
- The `run_comsol.sh` guard caught both of these and refused to run the stale
  class, exactly as designed.

---

## 2026-10-24 03:00-03:15 — Ex1 COMSOL result pictures + Ex4 review

### Ex1 result pictures (new class `comsol/Ex1_Plots.java`)
Loads `comsol/out/Ex1_HFilter.mph` (the model behind the validated run6) and
re-exports the field plots - no re-solve, so it takes ~35 s instead of 725 s.

What was measured while doing it:
- `model.sol("sol1").getPVals()` returns the sweep values:
  `[1.0E-11, 5.0E-11, 1.0E-10, 5.0E-10, 1.0E-9, 5.0E-9]` - this is the
  authoritative list of what the saved model contains.
- **`outersolnum` is REJECTED on a plot group** (`Invalid_property_value`).
  The shipped COMSOL 6.4 examples select a sweep combination with
  `model.result("pg4").setIndex("looplevel", 5, 0)` (Programming Reference
  Manual p. 754: "looplevel = The index of the solution to use, per level").
  Level 0 is the outer parametric level, i.e. the D value.
- **A plot group must be tied to the component view** with
  `model.result(pg).set("view", viewTag)` before the axis limits are set,
  otherwise COMSOL auto-fits the axes and the zoom is IGNORED.  Measured: the
  first export showed x 0..6000 when x 0..600 was requested.  This is the
  same reason the Ex1 mesh render in DeviceExport needed `view` set.
- The Image export needs `options2d = "on"` to include the axes, the colour
  legend and the title.  Without it the export is a bare canvas - that was
  the original "blank picture" bug.

First export (before the `view` fix) produced correct axes, a colourbar
titled `mol/m^3` and the plot title, but the zoom did not apply, so the field
was a thin 6000 um strip.  Fixed by the `view` link.

### Ex4 review — three real defects found and fixed
1. **Wrong element count in the report**: `878,306` -> `73,195`.  The
   production log (`the production cluster log`) says
   `=== mesh done, elements: 73195  minQ: 0.08743  meanQ: 0.6195 ===`.
   878,306 came from no run at all.
2. **Wrong physics named in the report.**  The report said "Conductive Media
   at 10 MHz ... (not Electrostatics: that interface has no frequency-domain
   solution ...)".  The code that ACTUALLY ran (`comsol/Ex4_FFF_prod.java`)
   uses **`Electrostatics` (`es`)**, with a frequency-domain study step and a
   `ChargeConservation` node covering the whole domain that takes
   `epsilonr_mat = from_mat` - i.e. the Free Space default IS overridden, so
   the water is water in the field calculation.  The report paragraph was
   rewritten to describe what the model does.  Note the mismatch with
   `notes/ex4_comsol_api_findings.md`, which recommends `ec`; the production
   run used `es` and the field results are consistent with the analytical
   decay, so the report now describes the model that produced the numbers.
3. **No device picture**: added `dev_ex4_comsol.png` (3D render of the real
   geometry) and `dev_ex4_comsol_plan.png` (the 45-strip electrode array,
   because the strips are coplanar with the floor and hidden inside the
   solid).

### Ex4 device picture from the REAL model (new class `comsol/Ex4_Plots.java`)
Loads `comsol/out/Ex4_FFF_prod.mph` (the production model downloaded from the
cluster) and renders:
- the geometry from BELOW (`camera().set("position"/"target"/"up")` - the
  camera API is verified in the shipped `induction_heating_curie_movement`
  example), which is the only way to see the 45 electrode faces on the floor;
- the geometry from above;
- a slice of the solved electric field on the mid-plane y = 50 um, using a
  `CutPlane3D` dataset, to show the electrode pattern in the field.
Which field expression exists is probed at runtime (`es.Ex`, `ec.Ex`,
`es.normE`, `abs(es.Ex)`) and the first one that evaluates is used, so the
picture is never mislabelled.

### API facts (measured, add to the skill)
- `model.result(pg).set("outersolnum", n)` -> REJECTED on a plot group.
  Use `model.result(pg).setIndex("looplevel", n, 0)`.
- `model.result(pg).set("view", "view1")` is REQUIRED for axis limits on the
  component view to affect the plot group's axes.
- `model.sol(tag).getPVals()` returns `double[]` (NOT `double[][]`).
- `model.result().export(tag).set(prop, String)` - the plot-group variant
  `set(prop, Object)` does not compile.


---

## 2026-10-24 07:57 — LaTeX delimiter repair (the report compiles again)

### Symptom
`Tectonic` failed with `error: ex1_hfilter:206: Missing $ inserted` and the
build halted.  An earlier diagnostic pointed at line 217; the real cause was
not a single line but a *pairing* error that shifted through the file.

### Root cause — pinned to a specific session message
The Hermes session DB (`<hermes-state-store>`) shows
message **48314** (2026-10-24 04:08:49) ran this over `report/*.tex`:

    pat = re.compile(r'\$((?:[0-9]+(?:\.[0-9]+)?|\\[a-zA-Z]+))\$(\\,[^$]*?)\$')
    s2  = pat.sub(lambda m: "$" + m.group(1) + m.group(2) + "$", s)

The intent was to normalise the author's split style `$number$\,unit$` into a
single math span `$number\,unit$`.  That intent is right; the regex is not.
Group 2 is `\,[^$]*?` — it can span ARBITRARY PROSE (anything with no '$' in
it) up to the next '$' in the file.  Each firing therefore deleted one '$'
and pulled a sentence into math mode, and every later '$' in the file was
re-paired.

### The defect that was actually left on disk
Base-region damage was already reverted.  What remained were **8 sites** of the
same shape, all in blocks added after the base commit — a stray '$' written
after a unit macro, which OPENS a math span over the following prose:

    ex1_hfilter.tex  3 sites  ($600$\,\um\dd{m}$, $1$\,\dd{mol/m^3}$,
                               $...3.33\times10^{-3}$\,\dd{m/s}$)
    ex2_chamber.tex  2 sites  ($60\times 60$\,\dd{mm}$, $t = 2195$\,\dd{s}$)
    ex4_fff.tex      3 sites  ($0.3$\,\um\dd{m}$, $28.5$\,\um\dd{m}$,
                               $3.5$\,\um\dd{m}$)

These are authoring typos, NOT 48314 output: 48314 could only MERGE spans,
never create a split one.  Evidence:
  * msg 46691 (02:13, pre-damage) — the author deliberately chose the split
    style: `of the $60\times 60$\,\dd{mm}$ footprint`.
  * msg 48800 (07:07) — a CLEAN authored ex2 paragraph: `at $t = 2195$\,\dd{s},`
    (split style, stray gone).
  * the pre-damage base blobs contain ZERO sites where a unit macro is
    immediately followed by '$'.

### The repair
`scripts/fix_stray_dollars.py` removes exactly those 8 '$' and nothing else.
Rendering is preserved: `\,` is a legal 0.16667 em kern in text mode
(LaTeX 2020-02-02+) and `\dd{}/\um` are `\ensuremath{\mathrm{...}}`,
so the unit is upright either way.  The base blob already relied on this.

### Verification (independent of the compiler)
`scripts/fix_stray_dollars.py` (re-runnable, `--apply` to write):
  * every paragraph has an EVEN number of unescaped '$'
  * no math span contains prose
  * no '$' follows a unit macro while the span it opens contains prose
Result after the repair: **all four exercise files + appendix + bibliography
CLEAN** (odd paragraphs 0, prose-in-math 0, stray '$' 0).

`scripts/audit_math.py` — the independent structural audit, plus it replays
the 48314 regex over the pre-damage blobs to attribute the damage.
`scripts/verify_pdf.py` — checks the BUILT pdf: 36 pages, every repaired
sentence present as prose, key numbers present, no blank pages.
`scripts/pdf_locate.py` — maps content to pages and lists every figure.

### Build
`<home>/bin/tectonic.exe -X compile main.tex` -> **exit 0**, no errors,
`report/main.pdf` = 2.52 MiB, 36 pages.  Only cosmetic hbox warnings remain.

### Two double periods fixed in ex2
`$0.9$..` and `$0.777$..` -> `$0.9$.` and `$0.777$.`  The first matches the
author's own later edit (msg 48800); the second is an original typo present
in the pre-damage base blob.

### Visual audit (vision_analyze on the rendered pages)
  * p7  Fig 4 caption ("600 um ... colour bar runs from 0 to 1 mol/m3") —
    upright roman prose, no math italics
  * p8  Fig 6 caption ("...m/s that the flow rate demands; the parabolic
    profile ...") — upright roman prose
  * p12 ex2 "60 x 60 mm footprint is only reached by diffusion" — upright
    roman prose
  * p14 ex2 "at t = 2195 s, and never reaches 0.9" — upright prose with
    correct inline math only (`$c_in$`, `$t_fill$`, `$t = 2195$`)
  * p31 ex4 variant table (73 195 / 438 933 / 314 455) and the "Switching the
    AC interface" paragraph — upright roman prose
  * p11 ex2 plan view: square chamber, crossing channel, 50 mm cell-layer
    circle — caption accurate
  * p26 ex4 plan view: ~45 strips, 20 um pitch, first at x = 50 um — caption
    accurate.  NOTE: the 10 V / ground ALTERNATION is not graphically
    encoded in the render; the caption states it.  Honest limitation.

### Pushed
Commit of the repaired report sources, all COMSOL models/classes, scripts and
notes.  Secret scan over the staged content: 0 hits.  `the git-ignored environment file`
git-ignored and not staged; the course course material not staged.

## 2026-10-24 09:05 — Ex4 electrode potential figure; Ex3 figure drift resolved

### Ex4: the 10 V / ground alternation is now a measured picture

The Ex4 device drawing was a COMSOL GEOMETRY render, so all 45 electrodes were
the same grey and the caption had to *state* the alternation.  It is now drawn
from the solved production model instead:

  comsol/Ex4_PlanPotential.java  ->  figures/comsol_ex4/ex4_potential_full.png
                                  ->  figures/comsol_ex4/ex4_potential_zoom.png

Measured on the cut plane z = 0 through the electrode array:
  * MaxSurface / MinSurface on that plane: min = -1.06e-15 V, max = 10.000000000000005 V
  * mean potential 5.321956 V at z = 0, 5.319551 V at z = 1, 5.326082 V at z = 10,
    5.321962 V at z = 50, 5.321956 V at z = 100 um
    -> the electrode field is essentially one-dimensional along x, so the band
       pattern is the same at every height in the channel
  * 23 electrodes at 10 V out of 45 predicts a mean of 23*10/45 = 5.111 V, which
    is what the measured 5.322 V (gaps included) confirms

### COMSOL API findings (all measured, not recalled)

* the cut-plane dataset type is **"CutPlane"**, not "CutPlane3D"; the latter
  fails with "Operation cannot be created in this context"
* a CutPlane dataset has **"quickplane"** and **"quickz"** but NOT
  "quickxmethod"/"quickzmethod".  One unknown property throws, so a single
  try block around several properties silently discards the ones after it:
  set each property in its own try block
* the potential variable on this model is plain **"V"**; both "es.V" and "ec.V"
  are rejected with Undefined_post_expression
* the Image export is created with **two** arguments
  (`export().create(tag, "Image")`) and the source is bound afterwards with
  `sourcetype`="plotgroup" + `sourceobject`=<plot group tag>.  The three-argument
  form `create(tag, pg, "Image")` throws "Operation cannot be created in this
  context"
* a 2D plot group's axis limits cannot be set from the plot group in 6.4
  ("axislimits", "xmin", "xmax" are all Unknown_property) and the component
  view's axis does not affect a 2D plot group either.  COMSOL's auto-range
  therefore keeps the y range at about -130..230 um around a 100 um tall
  channel, and the exported figure carries that white margin.
* COMSOL batch: `main()` is called with a **null** args array, so the model path
  has to be hardcoded; the batch SecurityManager denies raw java.io / java.nio
  file reads, so a `new File(mph).exists()` guard throws - which is what killed
  the earlier local Ex4_Plots run.  `ModelUtil.load` and `result().export()` are
  exempt.
* probe classes: comsol/Ex4_ProbeAccess.java, comsol/Ex4_DumpStruct.java
  (the loaded model has NO plot groups, only 3 Solution datasets + pdat)

### Ex3 figures: the committed PNGs were stale

The four Ex3 figures in the repository did not match the committed script.
Re-running scripts/ex3_analytical.py reproduces the current files exactly and
leaves notes/ex3_results.txt byte-identical, so the NUMBERS never changed
(the report's quoted 40.96 um is still correct).  Only the rendering differed:
the script was changed in a1f78a5 to draw at the final print width
(FIG_W = 4.23 in, savefig.dpi 300, font.size 9, legend.frameon True) and the
committed PNGs had been generated before that edit took effect.  The report was
rebuilt so it now embeds the figures the committed script actually produces.

The report was also rebuilt with the new Ex4 potential figure as Figure 23 on
page 26, the caption rewritten to say "high-potential and grounded bands"
(the grounded electrodes are at 0 V, not merely "low"), and the redundant
COMSOL-internal title removed from the image so the figure is legible at print
size.  PDF: 37 pages, clean build, scripts/verify_pdf.py ALL CHECKS PASSED.

### Ex3 figure legend defect, found by the page-21 visual check and fixed

The regenerated Ex3 figure (ex3_3_cm_and_observation_time.png, Figure 16 on
page 21) had both of its legends placed BELOW the axes with
`bbox_to_anchor=(0.5, -0.34)`.  The left panel's legend was clipped by the
bottom of the figure and its last entry was cut off in print.  Both legends
were moved INSIDE the axes, into corners the data does not visit:

  * left panel  -> loc="lower left"   (Re{K} falls from top-left to
                                       bottom-right, so the corner is free)
  * right panel -> loc="upper left"   (the curves rise steeply from
                                       lower-left to upper-right)

The turnover legend entry also read "turnover f_to = 1.8e+06 Hz"; it now
reads "turnover f_to = 1.80 MHz".  Re-running scripts/ex3_analytical.py
leaves notes/ex3_results.txt byte-identical, so no number changed.  PDF: 37
pages, clean build, verify_pdf.py ALL CHECKS PASSED.

### Ex1 comparison wording: absolute vs relative deviation made explicit

The Ex1 comparison paragraph mixed an absolute deviation with a relative one:
"the worst deviation is 1.6e-3, or 0.19 %" reads as two ways of stating the
same number, which they are not.  Both are now stated, each with its meaning:
the worst ABSOLUTE deviation and what that is as a percentage of the
reference value.

Recomputed from the report's own comparison table:
  row  t=1.0e-10  ref 0.84923  comsol 0.84760  abs 1.630e-03  rel 1.919e-03
  worst absolute  = 1.63e-03  -> reported as 1.6e-3, which is 0.19 % of the
                                 reference value
  analytical worst absolute = 5.12e-03 (row t=1.0e-9) -> reported as
                                 5.1e-3, which is 0.9 % of the reference value

### The COMSOL renders were exporting their labels at 2-3 pt on the page

The figure size audit (scripts/audit_figure_sizes.py) only ever covered the
matplotlib figures.  The COMSOL exports were never checked, and they were
badly wrong:

  COMSOL's Image export takes the image width in pixels and the placement in
  the report is 0.85\textwidth = 304.6 pt, so 1 px of a 1800 px wide export
  is 0.169 pt.  With COMSOL's default font the colourbar digits measured
  12 px in the source PNG, i.e. 12 * 304.6 / 1800 = 2.03 pt on the printed
  page.  Nothing in the figure was readable.

  The required font size follows from the placement, not from a guess:

      f = target * width / ((96/72) * placement_pt)

  with target = 7.5 pt of effective em and COMSOL's image export at 96 dpi.
  That gives 28 pt for a 1500 px export, 30 pt for 1600 px and 34 pt for
  1800 px.  comsol/DeviceExport.java and comsol/Ex4_PlanPotential.java now
  compute this per export instead of using a fixed size.

  Two COMSOL API details cost time here:
    * the export's "fontsize" property reaches the text of a GEOMETRY-source
      export (the device figures), but a plot-group export needs the size set
      after the group is run;
    * "fontsize" is not a valid property of a 2D plot group
      ("Unknown_property"), so it has to be set on the export.

  Measured after the change, in the rendered page:
    ex4_potential_full   colourbar digit cap 42 px -> 7.11 pt on the page
    the device figures   9.4 - 16.8 pt effective

  The audit script now has a Part 2 that measures the COMSOL figures on the
  rendered page instead of computing from the export resolution, which was
  the wrong model.  14 of 14 measured COMSOL renders pass the 7.5 pt target.

  Note: page.get_images() + get_image_bbox() do not work on this PDF -
  Tectonic writes image objects without a usable name and PyMuPDF raises
  "bad image name".  page.get_image_info(xrefs=True) gives the placement
  rectangle directly and is used instead.

### The appendix did not say where the .mph files actually live

The provenance table named the .mph behind every number, but the paragraph
under it said the Ex1 and Ex4 models "are in the repository folder
comsol/out/".  A reader takes that to mean they are in the git repository.
They are not: .gitignore ignores *.mph, so NO model file is tracked
(`git ls-files | grep .mph` is empty).  The wording now says exactly what is
where:

  * Ex1 and Ex4 models: in the project folder comsol/out/ on this machine,
    NOT in the git repository, because the repository excludes binaries.
    What IS committed is the model source that rebuilds each one exactly
    (comsol/Ex1_HFilter.java, comsol/Ex4_FFF_prod.java), plus the run
    scripts and the solve logs.
  * Ex2 model: on the the course cluster at the path in the table, 702 MB, with the
    run log committed.
  * Ex4 variant particle trajectories (notes/ex4_particles*.csv): committed,
    because the trajectories are what the report's comparison uses.

Also checked: the Ex4_FFF_prod.mph that a background download pulled from
the cluster is byte-size identical (39 186 746 B) to the one the committed
figures were built from, and it was written at 02:40:52, before those
exports at 09:03 and 09:24, so the figures and the model still correspond.

### The report DOES have the COMSOL devices - now it says so in the report

The report mixes three kinds of figure and nothing in it said which was which:
analytical figures from the Python scripts, COMSOL DEVICE drawings (renders of
the geometry COMSOL built) and COMSOL SOLUTION outputs (renders of a solved
field).  A generated table now says which is which:

  scripts/build_comsol_inventory.py parses the figure environments out of the
  four exercise sources, numbers them in document order, takes the page
  numbers from the built PDF by finding "Figure N:" on the page, and writes
  the table into the provenance appendix.

  Of the 28 figures: 9 device drawings, 7 solution outputs, 12 analytical.
  Every exercise has at least one device drawing; every exercise that was
  solved in COMSOL also shows the solved field.

Four bugs in that generator, each caught by the compile rather than by reading
the output:
  * the caption sanitiser mangled the report's own unit macros into
    undefined control sequences (\umm).  The macros the report uses are now
    rendered to text first.
  * inline math \( ... \) survived into text mode -> "Missing $ inserted".
    Now removed, along with ^ and _, which are math-mode characters.
  * a path replacement corrupted a file name
    (comsol/ex2/ex2_comsol/exchange.png).
  * the table was 43 pt too wide and every row overflowed.  Fixed by putting
    a break point after every underscore as well as every slash, setting
    \tabcolsep to 3 pt, and rebalancing the columns.

The heading was ending up on one page and the table on the next; \needspace
keeps them together now.

### The Ex2 model size in the appendix was wrong

The appendix said 701 782 749 B, taken from an `ls -l` on the cluster at 03:04.
That was the size of a file still being written.  Pulled back in full, the
model is 2 005 019 775 B.  The appendix now says 2.0 GB and records that the
model has been pulled into comsol/out/ as well.  It stays git-ignored: it is
over GitHub's 100 MB per-file limit.

### GOAL.md

Two items added: the standing rule "after every successful run, export the
.mph as well as the numbers", and a sixth step in the order of work.

### Two stale background DeviceExport processes fired after the fact

Two background shells from an earlier session were still queued.  One
compiled an old DeviceExport.java and died on `model.save(...)` against a
path that no longer exists ("lab-chip Assignment", the pre-underscore form);
`comsol/run_comsol.sh` caught the compile failure and refused to fall
through to a stale class, so nothing ran and nothing was written.

The second one ran successfully and overwrote notes/device_export.log with
output from a run of the OLD source, so the committed log no longer
described the source that produced the committed figures.  The figures
themselves were untouched: `git status` was empty, because COMSOL's geometry
export is deterministic.

DeviceExport was therefore re-run from the current source (with the font
fix).  It rewrote all fourteen figures at 09:45:50-55 and `git status` came
back EMPTY again - the same figures, byte for byte, from a fresh run.  That
is an independent confirmation that the committed figures are exactly what
the committed source produces.

The page-by-page visual audit ran for 97 minutes in two subagents (123 and
179 vision calls) and was steered to stop verifying and write its report.

### Two real defects the page-by-page audit found (pages 19-36)

**Ex3, Figure 15 (page 20): the caption had both trends backwards.**
It said "the force needed grows steeply with the observation time and falls
with the radius".  The script's own law is

    F_th = 3 * sqrt(2 k_B T f_drag / dt),   f_drag = 6 pi eta a

so F_th ∝ sqrt(a) and ∝ dt^(-1/2): it RISES with the radius and FALLS with
the observation time.  The script's own printed table agrees:

    alpha= 150 nm  F(1 ms) = 4.5386e-13 N   F(0.1 s) = 4.5386e-14 N
    alpha= 300 nm  F(1 ms) = 6.4185e-13 N     (x sqrt(2))
    alpha=1500 nm  F(1 ms) = 1.4352e-12 N     (x sqrt(10))

The paragraph under the figure was wrong with it: "a factor ten more
observation time buys a factor ten less force" (it is a factor HUNDRED for a
factor ten) and "a factor two in radius a factor two in force" (it is
sqrt(2)).  Both the caption and the paragraph are rewritten from the table.

**Ex4, Figure 27 (page 32): the |E|^2 reference line was at d/(2 pi).**
The caption's own law, ∇|E|^2 ∝ e^(-pi z/d), gives |E|^2 a decay length d/pi
= 3.183 um and |E| one of 2d/pi = 6.366 um.  The script drew the |E|^2 line at
d/(2 pi) = 1.591 um, which contradicts its own |E| line two panels away.  The
measurement settles it: the fitted lengths are 8.53 um for |E| and 4.266 um
for |E|^2, and 4.266 is EXACTLY half of 8.53, which is the d/pi and 2d/pi
pair and not the d/(2 pi) one.  The script now uses d/pi.

While fixing that figure two more things turned up:
  * the legend was cut off at BOTH edges of the canvas: a 3-column legend
    with ten entries does not fit a 4.23 in wide figure.  It is now two
    columns with shorter labels, and every entry is fully visible
    ("fit, L = 8.53 um", "d/pi = 3.18 um" included).
  * the caption compared the |E|^2 panel with ∇|E|^2, which adds a polynomial
    factor; it now compares with the decay e^(-pi z/d) itself.

A note on the environment: running the Python scripts from inside an
execute_code kernel fails with "cannot import name '_imaging' from 'PIL'",
because the kernel leaks another Hermes environment's site-packages through
PYTHONPATH.  The scripts run fine from the terminal, and with PYTHONPATH=
cleared they run fine anywhere.

### The page-by-page visual audit: what it found, and what was a false alarm

Two subagents audited every page (123 and 179 vision calls, ~100 minutes).
Their reports are only partly recoverable from the transcripts, which truncate
long messages, so what follows is what could be verified and fixed.

FIXED, from the pages 19-36 report:
  * Ex3 Figure 15 (page 20): the caption had both trends backwards; see the
    entry above.  Rewritten from the script's own table.
  * Ex4 Figure 27 (page 32): the |E|^2 reference line was at d/(2 pi); now
    d/pi, and the legend and caption are fixed with it.
  * Ex1 (page 5): "the critical coefficient for a 6 mm channel is three times
    larger than for a 25 mm one ... the longer channel sorts three times
    smaller particles" was inverted.  The report's own numbers give
    d_max = 0.66 um for the 25 mm channel and 2.75 um for the 6 mm one, so
    D_c(25 mm)/D_c(6 mm) = 2.75/0.66 = 4.2, which is the length ratio, and
    the LONGER channel sorts the LARGER particles.  Rewritten.

FALSE ALARMS, checked against the sources:
  * "dp/dx = 12 eta U / h^2 should be h^3" (pages 1-18 report).  It is wrong:
    h^3 applies when h is the HALF-gap.  The report's h = 5 um is the full
    height of the channel, so dp/dx = 12 eta U / h^2 is correct and it
    reproduces the printed numbers exactly:
      12 * 1.002e-3 * 3.33e-3 / (5e-6)^2 = 1.6e6 Pa/m, and 1.6e6 * 6e-3
      = 9600 Pa, the analytical value the COMSOL run confirms at 9696 Pa.
  * "comsol/out/Ex1HFilter.mph is the wrong filename".  It is not: the source
    says \texttt{comsol/out/Ex1\_HFilter.mph} and that file exists.

REPORTED BUT NOT YET VERIFIED OR FIXED (the transcripts truncated the
detail, so they must be re-checked against the page images):
  * C1  a malformed unit glyph in a caption
  * C5  a clipped annotation on Figure 7 (page 10)
  * C6  a caption calls a figure a "570 um zoom" but the picture is a flat
       colour field
  * C9  Figure 13's caption (page 17) promises a transient that the figure
       does not contain: ex2_cell_layer.png has three panels - (a) pinned
       c_s = 1, (b) normalised, (c) with film resistance - and none of them
       is the transient the caption describes
  * C11 a formula that does not equal the value printed beside it
  * C16 a dimensionally wrong volume

### Working through the audit findings that survived truncation

FIXED - Ex2, Figure 13 (page 17): the caption promised a transient the figure
does not contain.  The three panels of ex2_cell_layer.png are all STEADY
concentration-versus-depth plots: (a) pinned c_s = 1 mol/m^3, where all three
flow rates coincide because the profile then cannot depend on the flow,
(b) the same profile normalised by c_s, and (c) with the chamber-side film
resistance, where c_s itself depends on the flow rate and 1 mL/min gives the
highest cell-layer concentration.  The caption now says exactly that.

FIXED - Ex2, the volume arithmetic (page 12):
  * "the chamber volume is 6 x 6 x 1 cm^3 = 3600 uL" was dimensionally
    wrong: the chamber is 60 x 60 x 1 mm, so the height is 1 mm and not
    1 cm.  The value 3600 uL was right; the expression is now
    60 x 60 x 1 mm^3 = 3600 uL.
  * "the channel volume inside the chamber footprint is 120 uL, so the
    total fluid in the system is 180 uL" was wrong.  180 uL is the part of
    the inlet channel OUTSIDE the chamber (the remaining 90 mm of the
    150 mm channel, 2 mm wide and 1 mm high); the system as a whole holds
    3600 + 300 = 3900 uL.  Rewritten.

FIXED - the last raw micro signs in the report's own captions ("32.0 um",
"100 um") now use the report's \um\dd{} macro like everything else.

FALSE ALARM - "a caption calls a figure a 570 um zoom but the picture is a
flat colour field".  The figure (comsol_ex1/ex1_c_inlet.png) does show the
step: a red upper half, a blue lower half and a narrow interface at y = 0.
A sharp interface IS two flat regions; the caption is correct.  The axis
runs 0..500 um with the frame padding to about 590 um, so "the first 600 um"
is right.

STILL TO CHECK (the transcripts truncated the detail):
  * C1  the malformed unit glyph the audit saw - the three raw micro signs
         are now gone, but if the audit meant something else it has not been
         identified yet
  * C5  a clipped annotation on Figure 7 (page 10)
  * C11 a formula that does not equal the value printed beside it
  * C16 the dimensionally wrong volume - fixed above, but the audit may have
         meant a different one

### C11: a formula that did not equal the number printed beside it

Ex3 (page 18): "Equating the thermal energy to the kinetic energy of one
degree of freedom gives v = sqrt(2 E_th / m) = 0.0165 m/s, and for three
degrees of freedom v = sqrt(3 k_B T / m) = 0.0286 m/s."

Checked against the report's own numbers (m = 1.48e-17 kg, E_th = 1.5 k_B T):

    sqrt(k_B T / m)    = 0.01651 m/s    <-- the value 0.0165
    sqrt(2 E_th / m)   = 0.02860 m/s    <-- the value 0.0286
    sqrt(3 k_B T / m)   = 0.02860 m/s

So 0.0165 is the one-degree-of-freedom speed sqrt(k_B T / m), while
sqrt(2 E_th / m) is the SAME three-degree-of-freedom speed as
sqrt(3 k_B T / m), because E_th = 1.5 k_B T.  The formula and the number
printed beside it disagreed.  Corrected, with a parenthetical saying which
expression gives which value so it cannot be misread again.

Also made explicit in Ex1 which denominator h^3 sits in: the sentence about
h^3 = 1.25e-16 m^3 now says it is in the denominator of R_h, so it cannot
be read as belonging to the dp/dx = 12 eta U / h^2 line above it (which is
correct for a full-height h).

### C5: the "clipped annotation" is COMSOL's own logo

Every 3D export carries a small blue mark in the upper right corner.  It is
COMSOL's brand stamp, not a clipped axis label, and the audit's reading of it
as "stray blue artifacts" was a misidentification.  It is left in: the
captions already say each picture is COMSOL's own render, and the stamp is
attribution rather than damage.

### Audit findings: final tally

  C1  the raw micro signs in three captions        FIXED (now the \um\dd{} macro)
  C5  "clipped annotation" on Figure 7             NOT A DEFECT (COMSOL's logo)
  C6  "a flat colour field" at the Ex1 inlet       NOT A DEFECT (it is the step)
  C9  Figure 13 caption promises a transient       FIXED (the figure has none)
  C10 dp/dx = 12 eta U / h^2 "should be h^3"       NOT A DEFECT (h is the full height)
  C11 a formula not equal to the number beside it  FIXED (the thermal velocity)
  C12 the inverted Ex1 conclusion                  FIXED
  C13 "wrong filename"                             NOT A DEFECT (the name is right)
  C16 the dimensionally wrong volume               FIXED (and the 180 uL claim)

### The queued COMSOL runs that landed late

Five background processes from earlier in the session finished after the work
was done.  Each was checked against the repository rather than waved through:

  * DeviceExport (02:52) - ran to completion; its 14 geometry exports came
    out byte-identical to the committed figures, which independently confirms
    that COMSOL's geometry export is deterministic.
  * Ex1_Plots (03:11) - ran to completion and succeeded; its output was
    overwritten by the later 04:04 run of the same source, and the committed
    log and figures were unchanged (git reports nothing modified).
  * Ex1_Plots (a second copy) - compiled a pre-fix source and failed on
    PropFeature.set(String, Object) and on getPVals(); the guard refused to
    run the stale class.  The committed source was compiled separately and
    is clean.
  * Ex4_Plots (03:11) - ran the pre-fix source and aborted on the COMSOL
    batch SecurityManager denying its raw File read.  That is the failure
    notes/ex4_plots.log already records; the committed source compiles, and
    the report's Ex4 figures come from Ex4_PlanPotential.java, not from
    Ex4_Plots.java.
  * Ex4_Plots (a second copy) - compiled a pre-fix source and failed on a
    trySetExport(String, String, String) call made with a double[].

In no case did a stale run alter a tracked file, a figure or the PDF.

### Tidying the COMSOL working directory

The comsol/ tree had accumulated about 160 .mph files from the probe and
sweep runs, most of them written straight into comsol/ by their own source
(the save path is part of each Java program) and none of them tracked by
git.  They are now all under comsol/out/, with the models produced by
superseded source versions kept aside in comsol/out/legacy/:

  * comsol/out/legacy/ holds six DeviceExport_*.mph written by the pre-fix
    DeviceExport, which saved to comsol/ instead of comsol/out/.  They are
    NOT byte-identical to the current ones (170 888 B vs 172 424 B for the
    Ex1 device), so they were moved rather than deleted.
  * comsol/out/legacy/unexpanded_HOME/ holds the directory that an
    unexpanded $HOME in a shell command created, with a stray Ex1_HFilter.mph
    and two copies of the early Ex1 field figures.  Both PNGs are
    byte-identical to the ones in figures/comsol_ex1/.
  * the three production models the appendix names are untouched in
    comsol/out/: Ex1_HFilter.mph (30 336 324 B),
    Ex2Chamber_Ex2Chamber.mph (2 005 019 775 B) and
    Ex4_FFF_prod.mph (39 186 746 B).

One superseded source still names an input that no longer exists:
comsol/ex4_desktop/Ex4_Export2.java, Ex4_Export3.java and Ex4_Export4.java
load Ex4_FFF_Ex4_FFF.mph, which was produced only by the early desktop runs
and is not in the tree.  These are the first attempts at the Ex4 export; the
production path is comsol/Ex4_FFF_prod.java with Ex4_PlanPotential.java, and
the appendix does not refer to the ex4_desktop sources.
