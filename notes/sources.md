# Sources used in the report

Everything the report cites, with the exact place it was verified and what it
was used for.  Local COMSOL documentation is authoritative for API and
physics statements; web sources are used for the textbook physics and are
listed with the page that was actually read.

---

## A. COMSOL 6.4 documentation (shipped with the installation)

These are on this machine, so every claim taken from them can be re-checked
without a network connection.

### A1. AC/DC Module User's Guide — "The Electrostatics Interface in Time
Dependent or Frequency Domain Studies"
* File: `C:/Program Files/COMSOL/COMSOL64/Multiphysics/doc/pdf/ACDC_Module/ACDCModuleUsersGuide.pdf` (3 586 980 B, 540 pages). The section is listed in the contents on PDF page 64 (printed page 358) and begins on PDF page 68 (printed page 362). Re-verified 2026-10-24 by reading those pages out of the PDF.
* Also online: <http://hpc.mtech.edu/comsol/pdf/ACDC_Module/ACDCModuleUsersGuide.pdf>
* **What it settles:** the Electrostatics interface *can* be solved in a
  Frequency Domain study. The quote: "The Electrostatics Interface can also
  be solved in a dynamic study (Time Dependent or Frequency Domain). The
  equation system solved, however, is always the one presented in the
  previous section for the stationary case, in which no transient
  electromagnetic effects are taken into account. The difference is that the
  sources of the problem (charge densities, electric potential) are assumed
  to be time-varying."
* **Why this matters for Ex4:** the note in
  `notes/ex4_comsol_api_findings.md` says `es` "has no frequency-domain
  solution". That is wrong for COMSOL 6.4 — the production run used `es`
  with a Frequency Domain step and it solved (cluster log
  `the production cluster log`: "Compile Equations: Frequency Domain in
  Study 1/Solution 1", 112 286 DOF). The physical caveat is the one that
  matters: the *electrostatic* equations are solved, with time-varying
  sources, and the validity condition is that the frequency is low enough
  that electromagnetic-wave effects can be neglected. At 10 MHz in a 1 mm
  channel that condition holds comfortably (wavelength in water ≈ 14 cm at
  10 MHz, i.e. 140× the channel length).
* Online version of the same statement:
  <https://doc.comsol.com/6.4/doc/com.comsol.help.acdc/acdc_ug_theory.05.31.html>
  ("Theory for the Electrostatics Interface", COMSOL 6.4).

### A2. Shipped example `particle.dielectrophoretic_separation`
* File: `C:/Program Files/COMSOL/COMSOL64/Multiphysics/doc/help/wtpwebapps/ROOT/doc/com.comsol.help.models.particle.dielectrophoretic_separation/dielectrophoretic_separation.java`
* **What it settles:** COMSOL's own canonical DEP implementation uses
  * `physics().create("ec", "ConductiveMedia", "geom1")` — Conductive Media,
    not Electrostatics (line 31);
  * `E_src = "root.comp1.ec.EX"` on the dielectrophoretic-force node
    (line 254) — note the capital `EX`;
  * `UsePPR = true` (line 259);
  * `Formulation = NewtonianIgnoreInertialTerms` (lines 133-134);
  * a Frequency Domain study step for the AC field (line 379) with the
    stationary flow solved separately (lines 366-369).
* **Used for:** the Ex4 variant `v1` that repeats the production run with
  Conductive Media instead of Electrostatics, so the two can be compared
  directly.

### A3. COMSOL Multiphysics Programming Reference Manual (6.4, build 429)
* File: `C:/Program Files/COMSOL/COMSOL64/Multiphysics/doc/pdf/COMSOL_Multiphysics/COMSOL_ProgrammingReferenceManual.pdf`
* **Pages actually used:** p. 861-864 (the `Image (Export)` feature:
  `sourcetype`, `options2d`/`options3d`, `axes2d`, `legend2d`, `title2d`,
  `fontsize`, `zoomextents`); p. 754 (plot group `looplevel`); p. 549-551
  (`SolutionInfo`, `getIndices`); p. 728-730 (`outersolnum` on numerical
  features); p. 760 (`outersolnum` on average/integral features).
* **Used for:** every API statement in `notes/ex4_comsol_api_findings.md`
  and in the "A note on the COMSOL API" appendix.

### A4. AC/DC Module User's Guide — Electrostatics interface, default domain node
* Same file as A1; the section "Charge Conservation, Electrostatics" and the
  default-node behaviour (Free Space, `eps_r = 1`).
* **Used for:** why the Ex4 model adds a Charge Conservation node with
  `epsilonr_mat = from_mat` on top of the default: the default assumes
  vacuum, and for DEP in water `eps_r = 80` matters.

---

## B. Physics references (web)

### B1. Dielectrophoresis — Wikipedia
* <https://en.wikipedia.org/wiki/Dielectrophoresis>
* **Used for:** the complex Clausius–Mossotti factor
  K(ω) = (ε_p* − ε_m*)/(ε_p* + 2ε_m*), the definition of negative DEP
  (particle repelled from high-field regions) and positive DEP, and the
  statement that the sign of the force follows the frequency dependence of
  the relative polarisabilities.

### B2. "Exploring Dielectrophoresis through Computational Modeling: Parameter Relations in COMSOL and MATLAB" (eScholarship)
* <https://escholarship.org/content/qt7wj7f154/qt7wj7f154_noSplash_7d71caba5ad1b6dc5f5fbadbb3303184.pdf>
* **Used for:** the explicit frequency-dependent Clausius–Mossotti factor
  with the complex permittivities, written out as
  K(ω) = [ε_p − ε_m + i(σ_p − σ_m)/ω] / [ε_p + 2ε_m + i(σ_p + 2σ_m)/ω],
  which is the formula the analytical scripts implement.

### B3. COMSOL paper — "Frequency-Controlled Manipulation of Particles in a
Liquid Column Based on AC Dielectrophoresis"
* <https://www.comsol.com/paper/frequency-controlled-manipulation-of-particles-in-a-liquid-column-based-on-ac-dielectrophoresis-122241>
* **Used for:** the Clausius–Mossotti factor as the quantity that decides
  whether the particle follows or avoids the high-field gaps, i.e. the
  physical reading of the Ex3 turnover at 1.8 MHz and the Ex4 turnover at
  2.64 MHz.

---

## C. Where each claim in the report is checked

| claim | checked against |
|---|---|
| Ex1 COMSOL concentrations vs the instructor's table | `notes/ex1_comsol_run6.log` (max rel. error 0.192 %) |
| Ex1 flow: inlet pressure and velocity | `notes/ex1_comsol_run6.log` vs the Hagen-Poiseuille value |
| Ex4 mesh size and quality | `the production cluster log` ("mesh done, elements: 73195 minQ: 0.08743 meanQ: 0.6195") |
| Ex4 electrode pattern and boundary selection | `comsol/Ex4_FFF_prod.java` + the `Terminal faces` / `Ground faces` printout in the cluster log |
| Ex4 particle trajectory | `notes/ex4_traj_comsol.csv`, exported by COMSOL's own Data export |
| Ex4 electric-field decay length | `notes/ex4_Eline_*.csv`, four vertical lines exported from the solved field |
| Ex4 geometry as COMSOL built it | `notes/geom_export.log` (COMSOL's own bounding box) + `scripts/verify_devices.py` |
| Ex2 chamber exchange | `notes/ex2_comsol_results.txt` + `notes/ex2_comsol_series.csv` from the cluster run |
| every analytical number | `notes/ex*_analytical_results.txt`, the raw script output |

---

## Verification pass, 2026-10-24

Every entry in the report's reference list was re-checked against its source
rather than against this file:

| reference | check | result |
|---|---|---|
| AC/DC Module User's Guide 6.4 | the file exists in the installation; the section "The Electrostatics Interface in Time Dependent or Frequency Domain Studies" is in the contents on PDF p.64 and the section itself starts on PDF p.68 | confirmed, and the page reference in the report was sharpened to printed pp.358--363 |
| COMSOL Multiphysics Programming Reference Manual 6.4 | the file exists (14 150 505 B, 1290 pages); `looplevel` occurs on printed p.754, `sourcetype` on p.863, `SolutionInfo` on pp.545--549 | confirmed, the page numbers in the report are correct |
| shipped example `particle.dielectrophoretic_separation` | `Multiphysics/applications/Particle_Tracing_Module/Fluid_Flow/dielectrophoretic_separation.mph` and its documentation tree both exist | confirmed |
| Wikipedia, *Dielectrophoresis* | the URL resolves and the page carries the complex Clausius-Mossotti factor and the pDEP/nDEP distinction | confirmed |
| the eScholarship DEP thesis | the URL resolves, but the citation in the report named the wrong author, the wrong university and a shortened title | **corrected**: the author is Timothy Clifford Morse (2025), M.S. in Mechanical and Aerospace Engineering, University of California, Irvine, and the full title is "Exploring Dielectrophoresis through Computational Modeling: Parameter Relations in COMSOL and MATLAB". The text carries the Clausius-Mossotti factor (25 mentions) and the permittivity relations (64 mentions), so the use stated in the report is correct |
| the COMSOL paper on frequency-controlled DEP | the URL resolves and the page title matches the citation | confirmed |

No entry in the reference list turned out to be a fabricated source; the one
error was an attribution error, and it is fixed in both the report and this
file.
