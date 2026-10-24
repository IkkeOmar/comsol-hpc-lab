# COMSOL 6.4 API findings (measured, not recalled)

Everything below was established by **running** it against the installed
COMSOL 6.4 (build 293) on this machine and on the the course cluster.  Each item
names the failure it replaces.  Nothing here is from memory.

Source of truth for cross-checking: the shipped examples under
`C:/Program Files/COMSOL/COMSOL64/Multiphysics/doc/help/wtpwebapps/ROOT/doc/`
and the PDFs under `.../doc/pdf/`.  The shipped
`particle.dielectrophoretic_separation` model is the canonical DEP
implementation.

## 1. Particle release

* **Release from Grid is a GLOBAL feature**: `create(tag, "ReleaseGrid", -1)`.
  `-1` is the global entity level, not a dimension.  Creating it at dim 0..3
  fails with `Cannot_create_feature_in_the_specified_element_dimension`
  (misleading - it reads like a dimension error, it is a level error).
* Its three coordinate lists all live under the **single** property `x0`,
  indexed by space dimension:
  `setIndex("x0", <qx list>, 0)`, `setIndex("x0", <qy list>, 1)`,
  `setIndex("x0", <qz list>, 2)`.  There are no `qx0/qy0/qz0` properties.
* Release times: `setIndex("rt", "0", 0)`.
* Other release features and their levels: `Release` = domain (3),
  `Inlet` = boundary (2), `ReleaseFromPoint` = point (0),
  `ReleaseFromEdge` = edge (1).

## 2. The AC physics: `es` DOES work, `ec` is the reference implementation

* **CORRECTION (2026-10-24, measured):** the earlier claim in this file -
  "`Electrostatics` (`es`) has no frequency-domain solution" - is **wrong**
  for COMSOL 6.4.  The AC/DC Module User's Guide (p. 64, "The Electrostatics
  Interface in Time Dependent or Frequency Domain Studies") states that the
  Electrostatics interface *can* be solved in a Frequency Domain study: the
  equation system is the stationary one, with time-varying sources, and the
  validity condition is that electromagnetic-wave effects are negligible.
  The production run used `es` with a Frequency Domain step and it solved
  (cluster log `the production cluster log`: "Compile Equations: Frequency
  Domain in Study 1/Solution 1", 112 286 DOF, "Study 1 (spf + es) DONE").
* The physics caveat that *does* matter is the one the manual states: the
  electrostatic equations are solved, so the result is valid only while the
  frequency is low enough for wave effects to be negligible.  At 10 MHz in
  water the wavelength is about 14 cm, against a 1 mm channel - a factor 140
  of margin.
* The real problem with `es` is its **default domain node** `fsp1` of type
  **FreeSpace**, which assumes **vacuum**: the material's
  `relpermittivity` is silently ignored.  For DEP in water (eps_r = 80) that
  would use eps_r = 1.  The fix is what the production model does: a
  `ChargeConservation` node covering the whole domain with
  `epsilonr_mat = from_mat`, which overrides the default.
* COMSOL's own shipped DEP example (`particle.dielectrophoretic_separation`)
  uses `ConductiveMedia` (`ec`) instead, whose default domain node
  (`cucns1`) takes eps_r and sigma from the material.  `ec` also writes
  `E_src = "root.comp1.ec.EX"` with a **capital X**, while the `es`
  variant used `root.comp1.es.Ex`.
* So both are usable, with the material handled explicitly.  The comparison
  run is cluster job `ex4v1ec` (variant `Ex4_FFF_v1_ec`): the same model
  with `ec` instead of `es`, everything else identical, so the two runs can
  be compared number by number.
* Conductive Media is still the *right* default choice for a new model,
  because it does the material handling by itself and it is what COMSOL's
  own example uses.

## 3. The mesh API

* **Element sizes live on the `Size` feature, not on the mesher.**  Setting
  `hmax` on a `FreeTet` feature raises `Unknown_property`; the mesher's own
  properties are `method`, `numlayer`, `optlevel`, `xscale`...  The size
  properties (`hmax`, `hmin`, `hgrad`, `hcurve`, `hnarrow`, `custom`, and the
  `*active` flags) belong to a `Size` node.
* A `Size` node can be **global** (no selection) or attached to a selection.
* **A mesh sequence needs an actual mesher feature.**  A sequence with only
  `Size` and `Distribution` nodes meshes **0 elements** and every solve then
  fails with `Failed to find a solution` or `Failed to evaluate Jacobian` -
  neither message mentions the mesh.  Create `ftet1`/`ftri1`/`map1` as well.
* `mesh().create(tag)` and `mesh().create(tag, "geom1")` both work; the
  second is explicit and safe.
* **A mesh feature's selection needs an explicit entity dimension**:
  `mesh.feature("sizeF").selection().geom(2).set(faces)`.  Without
  `.geom(2)` it throws `No entity dimension specified`.  (The `Distribution`
  feature used the same call with `.geom(1)` for edges.)

## 4. Choosing the right solver tag

* `model.sol().tags()` is **not** in creation order, and Study 2 can end up
  with more than one solver.  In the Ex4 run the tags came back as
  `[sol1, sol3, sol2]` - the transient was in `sol3` while "last tag" gave
  `sol2`.
* Calling `createAutoSequences("sol")` and then `createAutoSequences("all")`
  creates a **decoy** solver.  One call is enough.
* Robust way to find Study 2's solver: the time-dependent one has a `t1`
  (Time) feature, a stationary one has `s1`:
  `Arrays.asList(model.sol(t).feature().tags()).contains("t1")`.
* Pointing a particle dataset at the wrong solver gives
  `Undefined_post_expression` from the plot, with no mention of the dataset.

## 5. Particle properties and the DEP force

* The particle's dielectric data lives on the **Particle Properties** node
  (`pp1`): `erp_mat`/`erp` (relative permittivity) and
  `sigmap_mat`/`sigmap` (conductivity), both set to `userdef`.
* The DEP force node (`DielectrophoreticForce`) takes the **medium's**
  `epsilonr_mat` and `sigma_mat` (each `from_mat` or `userdef`).
* `UsePPR = true` (piecewise polynomial recovery on the field) is not
  optional for a transient with a DEP force - the assignment says so too.
* `Formulation` = `NewtonianIgnoreInertialTerms`:
  `physics("fpt").prop("Formulation").setIndex("Formulation", "...", 0)`.

## 6. The COMSOL security manager

* `System.getenv("X")` is **blocked**:
  `AccessControlException: access denied ("java.lang.RuntimePermission" "getenv.X")`.
* Reading a config **file** is **blocked**:
  `AccessControlException: access denied ("java.io.FilePermission" "file" "read")`.
* Reflection is **blocked**:
  `AccessControlException: access denied ("java.lang.RuntimePermission" "accessDeclaredMembers")`.
  So `getDeclaredMethod` + `setAccessible` does not work; call
  `properties()` / `getAllowedPropertyValues()` directly on the feature.
* Consequence: any runtime switch (quick vs full run) has to be a
  **compile-time constant** in the Java source.

## 7. Finding property names without guessing

`model.component("comp1").physics("tag").feature("tag").properties()` returns
the full `String[]` of settable properties, and
`.getAllowedPropertyValues("prop")` returns the permitted values.  This is
the reliable way to write a model - several of the failures above were
avoided by enumerating instead of guessing.

## 8. Model building / solving gotchas

* `model.result().export().create(tag, plotGroup, "Image")` with
  `set("pngfilename", ...)` is the image-export form.  `set("filename", ...)`
  is the data-export form.
* **Put every export in its own try/catch.**  A single failing export inside
  one try block silently skips every export after it.
* A `CutPlane3D` dataset **cannot** take a `Particle` dataset:
  `Operation_cannot_be_created_in_this_context`.  Project particle
  trajectories to a plane from the exported CSV instead.
* `model.result().numerical("avU").setIndex("expr", "spf.Ux", 0)` and
  `"spf.u"` both fail with `Undefined_variable`; the plain variable name
  **`"u"`** is what the numerical feature accepts.
* `model.save("$HOME/x.mph")` does **not** expand `$HOME` on Windows - it
  creates a literal `$HOME` directory.  Use a real absolute path, or write
  relative to the process working directory.

## 9. Cluster recipe that works

```sh
#BSUB -q hpc
#BSUB -n 8
#BSUB -R "span[hosts=1]"
#BSUB -R "rusage[mem=32GB]"
source /usr/share/Modules/init/bash
module load comsol/6.4
unset JAVA_TOOL_OPTIONS
mkdir -p "$HOME/comsoltmp" "$HOME/comsolrecovery"
export TMPDIR="$HOME/comsoltmp"
comsol batch -np 8 -tmpdir "$HOME/comsoltmp" -recoverydir "$HOME/comsolrecovery" \
    -inputfile Model.class -methodcall main
```

* The batch script **must** have LF line endings.  `bsub` refuses a script
  with DOS endings: `Command script has DOS line endings!`.
* `bsub < script.sh` from the directory holding the `.class`, so that
  relative output paths land where you expect.
* A `.class` compiled by `comsolcompile.exe` on Windows runs unmodified on the
  cluster's Linux JVM - compile locally, run remotely.
* The `module: command not found` / `python3/3.8.4` lines in stderr come from
  the login shell's own profile and are harmless; the batch script sources the
  module init itself.

## 10. Ex4 status

See `notes/DESKTOP_AGENT_HANDOFF.md` for the exact state of the Ex4 run and
for the two remaining export problems (batch-mode image exports write no
file; the particle CSV has no coordinate columns).  The solved model itself is
on the cluster at
`/zhome/29/d/167641/a1/Ex4_FFF_Ex4_FFF.mph` (349 MB, both studies solved).
