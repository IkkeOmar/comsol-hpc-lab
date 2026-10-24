# Visual audit — one entry per page/section

> **CURRENT STATE (2026-10-24, end of the "are we done" round).**  Read this
> first; the item-by-item list below is the audit as it was found, with the
> findings kept in the order they were made.
>
> **The assignment is complete.**  The report builds clean (44 pages,
> 0 stray `$`, no blank pages, `scripts/verify_pdf.py` ALL CHECKS PASSED),
> every figure was validated on its own before it went into the report, the
> finished report was audited afterwards, and the whole thing is pushed to
> the private repo.
>
> **Closed in this round:** Figure 1's caption (both the 6 mm and the 25 mm
> panel), Ex3's threshold-force equation (now the overdamped displacement
> criterion the script and the figure actually use, with the evaluated value
> 4.54e-13 N quoted next to it), Figure 18's caption (one bar per particle
> in the left panel, separation against the voltage in the right one),
> Figure 26's x-axis (labelled [mm] while the data are in um; the channel-end
> marker sat at 1.0 instead of 1000 um), the Ex4 variant table (it now says
> which run it tabulates and that Figure 26 plots a different run of the
> same model), and the two Ex1 captions.
>
> **Added 2026-10-24:** a conversational section, *What the simulations
> assume, and where they leave reality*, now sits as the second section of
> the report (after Exercise 1).  It states, for every simulation, what the
> physical model assumes, where the assumption diverges from reality, and
> why the numerics are what they are.  It inserted three pages, so the page
> numbers of everything after it moved by +3 in this build.
>
> **One item is still open, and it is not part of the assignment:** the bonus
> COMSOL check for Exercise 3.  The model runs to completion but the
> particle trace returns NaN.  Two diagnostic runs narrow it down: the DEP
> force switched off gives the same NaN, so the force expression is not the
> cause; released at the Ex4 position (x = 0) the particle survives one step
> and then dies, and in that run the first stored position is 59.24 um
> along the channel after one second while the flow is 28 nm/s.  So the
> particle's integration is broken, not the solver.  The next probe is to
> print the fluid velocity and the particle's initial acceleration at the
> release point and compare them with U = 28 nm/s.  Exercise 3 in the
> assignment (pages 8-9) is a theory exercise with no COMSOL part, and the
> COMSOL validation of the same physics is already in the report through
> Ex4 (40.3 um measured against 40.96 um analytical).


The audit lives HERE, on disk, so the next session does not have to trust
context.  Append a line to every page as it is checked.  A finding is
"closed" only when the source was checked and the fix verified in the
rebuilt PDF.

Status legend: `open` = confirmed against the source, not yet fixed.
`checked` = looked at, nothing wrong.  `fixed` = source changed and the
rebuild verified.

## Method

Every page is rendered from the CURRENT `report/main.pdf`
(`report/render_pages.py`) and looked at.  A caption is only "correct" if the
embedded bitmap was compared with the caption, not if it reads plausibly.
A claim about a number is only "correct" if it was recomputed from the
script or the run log that produced it.

## The exercises

| exercise | COMSOL model | COMSOL device figures in the report | COMSOL result figures | analytical figures |
|---|---|---|---|---|
| Ex1 | local, `comsol/out/Ex1_HFilter.mph` | inlet zoom | 3 (c in, c out, u in) | 4 |
| Ex2 | the course cluster, 11 jobs | 3 (3D, angle, plan) | 1 (exchange) | 4 |
| Ex3 | none (analytical) | 2 (device, mesh) | 0 | 4 |
| Ex4 | local + cluster variants | 3 (3D, below, plan) | 3 (potential, 2 comparisons) | 4 |

## Status (2026-10-24, after the legend/device round)

CLOSED in the last two rounds: C1 (the raw micro signs), C3 and C4 (the two
Ex1 captions), C6 (Figure 10's caption), C9 (the cell-layer caption), C10
(the Ex1 pressure unit), C11 (the thermal velocity), C12 (the inverted Ex1
conclusion), C16 (the volume dimensions), C17/C18 (the two CSTR numbers),
C19 (the Ex4 |E|^2 reference and legend), the Figure 19 caption, the Ex3
device geometry, and Figures 13, 15 and 19 (legends out of the data).

DISMISSED after checking: C5 (the "clipped annotation" is COMSOL's own
logo), C6's original reading (Figure 10 really is a bar chart - the caption
was wrong, not the figure), C10's original reading, C13 (the file name is
right), C15 (the number is not split across the page in the current build)
and C19's original reading (h^2 is correct for a full-height gap).

CLOSED in the last round (2026-10-24, the "are we done" round): C2 (the
Figure 1 caption now covers both the 6 mm and the 25 mm panel), the Ex3
threshold-force equation (it is now the overdamped displacement criterion
the script and the figure actually use, F_th = 3 sqrt(2 k_B T f / dt),
with the evaluated value 4.54e-13 N quoted next to it), Figure 18's
caption (it now says one bar per particle in the left panel and separation
against the voltage in the right one), and Figure 26's x-axis (it was
labelled [mm] while the data are in um, and the channel-end marker sat at
1.0 instead of 1000 um).  The regenerated Figure 26 was re-validated on
the rendered page 31: three panels, the shared key inside the canvas, the
marker at 1000 um.

STILL OPEN, in the order it should be taken next:

### report/ex1_hfilter.tex
- `open` C10 — the pressure table labels `dp * 1e-3` as mbar; that is kPa.
  9600 Pa is printed "9.6 mbar" instead of 96 mbar.  Script:
  `scripts/ex1_analytical.py` (`dp_mbar` keys and the printed strings).
- `open` C2 — Figure 1's caption describes only the left panel of the
  two-panel bar chart; the right panel is the 25 mm case.
- `open` C3 — the inlet device figure's caption claims the full 6 mm channel
  and a mid-line edge; the picture is the first 600 µm with no mid-line
  drawn (verified by vision on `dev_ex1_comsol_inlet.png`).
- `open` C4 — the inlet velocity caption claims a resolved parabolic
  profile; the plot is uniform (verified by vision on `ex1_u_inlet.png`).

### report/ex2_chamber.tex
- `fixed` C16 — the chamber volume's dimensions (now 60 x 60 x 1 mm) and the
  "total fluid" sentence (3780 uL, matching COMSOL's 3.78e-6 m3).
- `fixed` C9 — the cell-layer caption no longer promises a transient.
- `open` C6 — Figure 10's caption describes the tank/plug-flow curves; that
  figure is the time-scale bar chart.  The caption belongs to Figure 11.
- `open` C17/C18 — the "tank" column: "time to c_avg = 0.9 -> 3714 s" is
  impossible (the tank curve peaks at 0.632) and "below 0.1 after the pulse
  -> 5707 s" should be 6143 s.
- `open` — the cell-layer figure's legends block the data (user request
  2026-10-24): drop the redundant legends in (a) and (b), shrink (c)'s.

### report/ex3_dep.tex
- `fixed` C11 — the thermal-velocity formula now matches the value beside it.
- `fixed` — Figure 15's caption and the sentence after it now state the
  trends the script actually computes (F ~ sqrt(radius), F ~ dt^-1/2).
- `open` — Figure 15's legends block the plot (user request 2026-10-24).
- `open` — Figure 19's legend blocks part of the plot (user request
  2026-10-24).
- `open` — the device differs from the assignment figure (user request
  2026-10-24): the first strip starts flush at the lower-left corner, while
  the assignment's array is inset.  Measured from `<assignment-pages>/
  page9.png`: strips at 10 um pitch, the first inset ~2.4 um from the floor's
  left edge, the particle above the gap between strips 3 and 4.  Model has
  strips at x = 0, 20, 40, 60 and the particle at x = 35.

### report/ex4_fff.tex
- `fixed` — Figure 27's fit now uses the |E|^2 decay length and its reference
  line is d/pi; the legend was moved and the caption corrected.
- `open` — the appendix variant table presents a different production run
  (17.0 s / 39.79 um) than the main text (18 s / 40.3 um) without saying so.
- `open` — Ex3's displayed Eq. (9) is the assignment's approximation while
  the script and the figure use F = 3 sqrt(2 k_B T f / dt); the printed
  equation does not reproduce the quoted numbers.

## Page-by-page log

(appended as each page is re-checked against the rebuilt PDF)

## 2026-10-24 — the figure-legends and Ex3-device round (the author's request)

### Figures reworked, each validated BEFORE it went into the report

* **Figure 13 (`ex2_cell_layer.png`)** — the three legends sat on the data.
  Now: no legend inside any panel; the one key for the whole figure is a
  header row above the panels; the reference line in (b) is labelled
  directly; the three surface concentrations (0.0098 / 0.0467 / 0.1591) are
  written at the ends of the curves in (c).  The figure is authored at
  4.78 in and placed at 0.96\textwidth, so it is 13 % larger on the page.
  Validated by vision: nothing clipped, nothing on top of a curve, the key
  row clear of everything, the x-axis label clear of the canvas edge.
* **Figure 15 (`ex3_2_threshold_force.png`)** — both legends moved below
  their axes (4 and 5 entries, 7.5 pt, no frame), authored at 4.78 in and
  placed at 0.96\textwidth.  Validated by vision: the legends are outside
  the data, not clipped, and the curves are clear.
* **Figure 19 (`ex3_4_trajectory.png`)** — the single legend replaced by a
  direct annotation of the equilibrium line with a leader, authored at
  4.78 in and placed at 0.96\textwidth.  Validated by vision: clean.

### The Ex3 device now matches the assignment's figure

The assignment's figure (<assignment-pages>/page9.png) was measured
rather than eyeballed: four strips on a 20 um pitch, each ~10 um wide, the
first one inset ~2.5 um from the left end of the drawn electrode plane, and
the particle above the gap between the third and the fourth strip.  The COMSOL
model had the first strip flush at the corner and the particle above the gap
between strips 2 and 3.  `comsol/DeviceExport.java` now insets the array by
2.5 um and puts the particle at x = 55 um; the render was regenerated and the
gap measured at 2.17 um (pixel measurement of the PNG) and confirmed by
vision as roughly a quarter of a strip width.  The caption now says the
circle is drawn ten times oversize (the assignment's particle has a radius of
150 nm and the assignment's own drawing is not to scale either).

### The Ex3 COMSOL sanity check — built, run, and still open

`comsol/Ex3_Check.java` is the Ex4 production model with the assignment's
Ex3 geometry: four 10 um strips on a 20 um pitch inset 2.5 um, particle 1 of
the assignment (radius 150 nm, density 1050 kg/m^3), released at x = 55 um
(above the gap between strips 3 and 4) and 50 um above the electrode plane,
10 MHz, 10 V.  The study structure follows COMSOL's own shipped example
`models.particle.dielectrophoretic_separation`: stationary flow + frequency
domain AC field, then a time-dependent particle trace with the DEP force.

What works: the mesh (145 179 elements, min quality 0.19), the stationary
flow, the frequency-domain electric field, and the transient solver, which
steps through all 121 output times.

What does not: the particle position is NaN from t = 1 s on, so the
trajectory cannot be compared with the analytical equilibrium (40.96 um).
The first attempt used the Ex4 model's graded mesh, which on an 80 um
channel gave 7 079 elements at a minimum quality of 8.5e-4 and the same NaN;
the mesh was re-sized for the Ex3 geometry and the NaN survived, so the mesh
was not the cause.  The model, its log and the exported CSV are committed so
the next session can pick this up.

Two things are already established and do not depend on that run:
* the Ex3 analytical outputs are byte-identical before and after the device
  edit — the assignment's field law depends on the height only, not on where
  the array sits in x, so the device change cannot move a number;
* the COMSOL validation of the same physics exists in the Ex4 model: the
  45-electrode array puts the particle at 40.3 um against the analytical
  40.96 um, 1.6 % apart, which is already in the report.

### More defects closed in the same round (all checked against the source)

* **C10 — the Ex1 pressure unit was wrong by 10×.**  The script computed
  `dp * 1e-3` and labelled it mbar; that is kPa.  1 Pa = 0.01 mbar, so the
  script now uses `dp * 0.01` in all ten places, and the report's table
  (29.5 / 48.3 / 68.7 mbar) and its sentence (9600 Pa = 96 mbar) were
  corrected.  The Ex1 results file and JSON were regenerated with it.
* **C6 — Figure 10's caption described Figure 11.**  Figure 10 is the
  time-scale bar chart; the caption described the tank and plug-flow
  curves, which are Figure 11.  Rewritten to describe the bar chart.
* **C17/C18 — two unsupported numbers in the Ex2 "tank" column.**  The tank
  curve is `1 - exp(-t/t_fill)` up to t_fill = 2160 s and
  `0.632 exp(-(t-t_fill)/t_fill)` after, so it peaks at 0.632 and never
  reaches 0.9: the "3714 s" is replaced by "never (the tank peaks at
  0.632)", and the fall through 0.1 is at 2160 + 2160 ln(6.321) = 6143 s,
  not 5707 s.
* **Figure 19's caption said x-z plane** while the figure's own title says
  x-y plane, and it claimed a relaxation-time annotation the figure does
  not carry.  Both corrected, in the caption and in the body text.

### Page audit log, appended

* p17 (Figure 13) — validated on the rendered page: the key is a header row
  above the panels, nothing is clipped, the end labels are legible.
* p23 (Figure 17, the Ex3 device) — validated: the four strips, the
  visible left inset, the particle above the third/fourth-strip gap, and a
  caption that matches the picture.  The caption was then reworded so that
  "inset" cannot be read as describing the inter-strip gaps.

### The Ex3 COMSOL check: two diagnostics, and what they pin down

Both diagnostics are separate model classes so the production one stays
untouched: `comsol/Ex3_Diag.java` (the DEP force switched off) and
`comsol/Ex3_Diag2.java` (the particle released at x = 0, the Ex4 position,
with the DEP force on).

| run | release | DEP force | result |
|---|---|---|---|
| `Ex3_Check` | x = 55 um | on | NaN from t = 1 s |
| `Ex3_Diag`  | x = 55 um | **off** | NaN from t = 1 s |
| `Ex3_Diag2` | x = 0 um | on | one valid step, then NaN |

What that rules out and leaves:

* **The DEP force is not the cause.**  With the force switched off the
  particle is still NaN at the first step.
* **The release position matters.**  Released at x = 0 the particle survives
  one step and then dies; at x = 55 um it dies at once.
* **The particle's integration is broken, not the solver.**  In the x = 0 run
  the first stored position is x = 59.24 um after one second, with y and z
  essentially unchanged (49.99 and 50.004 um).  The flow in this channel is
  1 uL/h through a 100 x 100 um cross-section, i.e. 28 nm/s, so a 59 um jump
  in one second is not a force balance - it is a wrong velocity or a wrong
  drag coefficient at the release point.
* A side observation: the frequency-domain solve takes 328 s with the DEP
  force on and 23-29 s with it off, so the piecewise-polynomial recovery of
  the field gradient is expensive on this mesh.

The next probe for whoever continues this (it is a few lines in the Java):
print the fluid velocity and the particle's initial acceleration at the
release point, and compare them with the hand value U = 28 nm/s.  A
mismatch there explains both the NaN and the 59 um jump.  The other open
possibility is the release grid itself: the Ex4 model releases at the inlet
boundary (x = 0) and that is the only position that has ever worked in this
model family.

The assignment's Ex3 is a theory exercise - pages 8 and 9 of the assignment
ask for the thermal velocity, the Clausius-Mossotti factor, the observation
time and the equilibrium position, with no COMSOL part - so this check is
an extra, and the COMSOL validation of the same physics is already in the
report through the Ex4 model (40.3 um measured against 40.96 um
analytical).

### 2026-10-24 — the assumptions section and the rebuilt Figure 13

* p9-p11 (the new section *What the simulations assume, and where they leave
  reality*) — validated on the rendered pages: the heading renders as
  section 2, the prose is set cleanly with no missing math, and the four
  assumption blocks (H-filter, cell-growth chamber, particle motion,
  field-flow fractionation, plus the meshes-and-solvers paragraph) run
  across the three pages without a widow, an overflow or a stray delimiter.
  The section's cross-reference to Figure 13 resolves.
* p19 (the rebuilt Figure 13) — validated on the rendered page: the three
  panels are stacked, each the full width of the figure, and the key, the
  panel titles, the x-axis labels and the end-value annotations are all
  separated.  Three defects were found and fixed in the process, each
  measured on the PNG rather than judged by eye: the key sat on top of the
  (a) title, panel (a)'s two-line y-axis label was clipped by the canvas
  edge (ink at column 0), and the two lines of the (b) title were 2 pt
  apart, which read as one overlapping block of text.  The panels are now
  placed with explicit axes boxes and the titles carry an explicit
  `linespacing=1.5`, which is what a gridspec with a height ratio will not
  give you.  A second pass then opened the bands further: the canvas is
  7.20 in tall, the three panels sit in bands separated by 163 px and
  176 px of white space, and panel (b)'s title is a single line so that no
  two-line title block can read as one merged row.  Re-checked on the
  rendered page 19: every text element is inside the page and legible.
