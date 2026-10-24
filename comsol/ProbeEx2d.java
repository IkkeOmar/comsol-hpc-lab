// ProbeEx2d.java - probe the remaining COMSOL 6.4 API needed for Exercise 2.
//
// QUESTIONS ANSWERED (all runtime-enumerated, none guessed):
//   A) Swept-mesh API for a 100 um thin domain hanging under a slab:
//      feature type string, how to select source/destination faces, and how to
//      set the number of layers (Distribution).
//   B) Study-step feature disabling (needed: pulse inflow for the fill study,
//      steady inflow for the cell-layer study).
//   C) Rectangle function: real property names for lower/upper/transition zone
//      /number of continuous derivatives, and whether SI units are accepted.
//   D) Numerical features available for a 3D domain average, a vertical cut
//      line through the cell layer, and a point value.
//   E) Probe types for the domain-average probe and the three point probes.
//   F) Image export API with an absolute path.
//
// API facts inherited from the sibling probes (already verified here):
//   * TDS op string is "DilutedSpecies"; default features are
//     sp1 (SpeciesProperties), cdm1 (Fluid), nflx1 (NoFlux), init1 (init),
//     dcont1 (Continuity).
//   * tds "Fluid" node has u_src in {root.comp1.u, userdef, fromCommonDef}
//     and D_c (9 entries, diagonal) / D_c_mat.
//   * tds "Reactions" node (domain) has R_c_src / R_c.
//   * tds "Inflow" boundary node has c0 / BoundaryConditionType.
//   * spf "Inlet" BoundaryCondition in {Velocity, PressureNoViscousStress,
//     LaminarInflow, NormalStress, MassFlow}; mass flow property is "mfr".
//   * Study step op for time dependent is "Transient" (not "TimeDependent").
//   * Stationary step has notsolmethod/notstudy/notsolnum to take the velocity
//     field from a previous study step.
//   * probe type "DomainPoint" accepts set("coords", double[][]).

import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class ProbeEx2d {

    static void P(String s) { System.out.println("PROBE " + s); }

    // A slab 20 x 20 x 2 mm with a 100 um disc of radius 8 mm hanging below it.
    static Model build(Model model) {
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blk", "Block");
        g.feature("blk").set("size", new String[]{"20", "20", "2"});
        g.feature("blk").set("pos",  new String[]{"-10", "-10", "0"});
        g.create("cyl", "Cylinder");
        g.feature("cyl").set("r", "8");
        g.feature("cyl").set("h", "0.1");
        g.feature("cyl").set("pos", new String[]{"0", "0", "-0.1"});
        g.run();

        g.create("selSlab", "BoxSelection");
        g.feature("selSlab").set("entitydim", 3);
        g.feature("selSlab").set("condition", "inside");
        g.feature("selSlab").set("xmin", "-11"); g.feature("selSlab").set("xmax", "11");
        g.feature("selSlab").set("ymin", "-11"); g.feature("selSlab").set("ymax", "11");
        g.feature("selSlab").set("zmin", "-0.001"); g.feature("selSlab").set("zmax", "2.001");

        g.create("selDisc", "BoxSelection");
        g.feature("selDisc").set("entitydim", 3);
        g.feature("selDisc").set("condition", "inside");
        g.feature("selDisc").set("xmin", "-8.1"); g.feature("selDisc").set("xmax", "8.1");
        g.feature("selDisc").set("ymin", "-8.1"); g.feature("selDisc").set("ymax", "8.1");
        g.feature("selDisc").set("zmin", "-0.2");  g.feature("selDisc").set("zmax", "-0.001");

        // the horizontal interface face at z = 0 inside the disc
        g.create("selIface", "BoxSelection");
        g.feature("selIface").set("entitydim", 2);
        g.feature("selIface").set("condition", "inside");
        g.feature("selIface").set("xmin", "-8.1"); g.feature("selIface").set("xmax", "8.1");
        g.feature("selIface").set("ymin", "-8.1"); g.feature("selIface").set("ymax", "8.1");
        g.feature("selIface").set("zmin", "-0.01"); g.feature("selIface").set("zmax", "0.01");

        // the bottom face of the disc at z = -0.1
        g.create("selCBot", "BoxSelection");
        g.feature("selCBot").set("entitydim", 2);
        g.feature("selCBot").set("condition", "inside");
        g.feature("selCBot").set("xmin", "-8.1"); g.feature("selCBot").set("xmax", "8.1");
        g.feature("selCBot").set("ymin", "-8.1"); g.feature("selCBot").set("ymax", "8.1");
        g.feature("selCBot").set("zmin", "-0.11"); g.feature("selCBot").set("zmax", "-0.09");

        g.run();
        P("slab domains : " + Arrays.toString(model.selection("geom1_selSlab").entities(3)));
        P("disc domains : " + Arrays.toString(model.selection("geom1_selDisc").entities(3)));
        P("iface faces  : " + Arrays.toString(model.selection("geom1_selIface").entities(2)));
        P("cbot faces   : " + Arrays.toString(model.selection("geom1_selCBot").entities(2)));
        return model;
    }

    public static void main(String[] args) {
        try { run(); } catch (Throwable t) { t.printStackTrace(); }
        P("PROBE_EX2D DONE");
    }

    public static void run() {
        Model model = ModelUtil.create("ProbeEx2d");
        build(model);

        // ---------------------------------------------------------------- A
        P("=== A: SWEPT MESH API ===");
        MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");
        try {
            mesh.create("swe1", "Swept");
            P("Swept feature created");
            System.out.println("PROBE swe1 props: " + Arrays.toString(mesh.feature("swe1").properties()));
            for (String pr : new String[]{"srcface", "dstface", "smoothing", "smoothmaxiter",
                                        "srcfaceauto", "dstfaceauto", "facemethod", "elemtype"}) {
                try {
                    System.out.println("PROBE swe1 allowed " + pr + ": "
                        + Arrays.toString(mesh.feature("swe1").getAllowedPropertyValues(pr)));
                } catch (Throwable t) { P("swe1." + pr + " not an enum"); }
            }
            // try selecting the source / destination faces
            try {
                mesh.feature("swe1").selection().geom("swe1_src").set(
                    model.selection("geom1_selIface").entities(2));
                P("swe1 src selection via geom(\"swe1_src\") OK");
            } catch (Throwable t) { P("swe1 src selection FAILED :: " + t); }
            try {
                mesh.feature("swe1").selection().geom("swe1_dst").set(
                    model.selection("geom1_selCBot").entities(2));
                P("swe1 dst selection via geom(\"swe1_dst\") OK");
            } catch (Throwable t) { P("swe1 dst selection FAILED :: " + t); }
            // Distribution for the number of layers
            try {
                mesh.feature("swe1").create("dis1", "Distribution");
                mesh.feature("swe1").feature("dis1").selection().geom(2).set(
                    model.selection("geom1_selCBot").entities(2));
                mesh.feature("swe1").feature("dis1").set("numelem", 20);
                P("Distribution numelem=20 OK");
            } catch (Throwable t) { P("Distribution FAILED :: " + t); }
            mesh.feature("swe1").selection().geom(3).set(
                    model.selection("geom1_selDisc").entities(3));
            mesh.run();
            P("mesh elements after swept disc: " + mesh.stat().getNumElem());
            for (String ty : new String[]{"prism", "hex", "tet", "tri"}) {
                try { P("  " + ty + " = " + mesh.stat().getNumElem(ty)); }
                catch (Throwable t) { /* absent */ }
            }
        } catch (Throwable t) { P("SWEPT SETUP FAILED :: " + t); }

        // ---------------------------------------------------------------- B
        P("=== B: STUDY-STEP FEATURE DISABLING ===");
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").create("td", "Transient");
        try {
            model.study("std1").feature("td").set("useadvanceddisable", true);
            P("useadvanceddisable set OK");
        } catch (Throwable t) { P("useadvanceddisable FAILED :: " + t); }
        try {
            model.study("std1").feature("td").set("disabledphysics",
                new String[]{"tds/inflow_pulse"});
            P("disabledphysics set OK; get = " + Arrays.toString(
                model.study("std1").feature("td").getStringArray("disabledphysics")));
        } catch (Throwable t) { P("disabledphysics FAILED :: " + t); }

        // ---------------------------------------------------------------- C
        P("=== C: RECTANGLE FUNCTION ===");
        model.func().create("rect1", "Rectangle");
        try {
            System.out.println("PROBE rect1 props: " + Arrays.toString(model.func("rect1").properties()));
        } catch (Throwable t) { P("rect props FAILED :: " + t); }
        try {
            model.func("rect1").set("lower", "-5[s]");
            model.func("rect1").set("upper", "2160[s]");
            P("lower/upper with units OK -> " + model.func("rect1").getString("lower")
              + " / " + model.func("rect1").getString("upper"));
        } catch (Throwable t) { P("rect lower/upper with units FAILED :: " + t); }
        for (String pr : new String[]{"smooth", "smoothzone", "ncontder", "dercont",
                                     "smooth_transition", "numderiv"}) {
            try {
                model.func("rect1").set(pr,
                    (pr.equals("ncontder") || pr.equals("dercont")) ? "2" : "5");
                P("rect1." + pr + " accepted; get = " + model.func("rect1").getString(pr));
            } catch (Throwable t) { P("rect1." + pr + " FAILED :: " + t.getMessage()); }
        }

        // ---------------------------------------------------------------- D
        P("=== D: NUMERICAL FEATURES ===");
        for (String ty : new String[]{"AvVolume", "IntVolume", "AvSurface", "IntSurface",
                                      "EvalPoint", "EvalGlobal", "MaxVolume", "AvLine"}) {
            try {
                model.result().numerical().create("nf_" + ty, ty);
                P("numerical " + ty + " OK");
            } catch (Throwable t) { P("numerical " + ty + " FAILED :: " + t.getMessage()); }
        }
        for (String ty : new String[]{"CutLine3D", "CutPoint3D", "CutPlane3D", "Grid3D"}) {
            try {
                model.result().dataset().create("ds_" + ty, ty);
                P("dataset " + ty + " OK; props = " + Arrays.toString(
                    model.result().dataset("ds_" + ty).properties()));
            } catch (Throwable t) { P("dataset " + ty + " FAILED :: " + t.getMessage()); }
        }

        // ---------------------------------------------------------------- E
        P("=== E: PROBES ===");
        try {
            model.probe().create("pdom", "Domain");
            model.probe("pdom").set("expr", "c");
            P("Domain probe OK; props = " + Arrays.toString(model.probe("pdom").properties()));
        } catch (Throwable t) { P("Domain probe FAILED :: " + t); }
        try {
            model.probe().create("pdp", "DomainPoint");
            model.probe("pdp").set("coords", new double[][]{{0.0295, 0.0, 0.0}});
            P("DomainPoint coords OK -> " + Arrays.deepToString(
                model.probe("pdp").getDoubleMatrix("coords")));
            try {
                model.probe("pdp").set("expr", "c");
                P("DomainPoint expr OK");
            } catch (Throwable t) { P("DomainPoint expr FAILED :: " + t.getMessage()); }
        } catch (Throwable t) { P("DomainPoint FAILED :: " + t); }

        // ---------------------------------------------------------------- F
        P("=== F: IMAGE EXPORT ===");
        try {
            model.result().create("pg1", "PlotGroup3D");
            model.result("pg1").create("sl1", "Slice");
            model.result("pg1").feature("sl1").set("expr", "c");
            model.result("pg1").run();
            model.result().export().create("img1", "pg1", "Image");
            model.result().export("img1").set("pngfilename", "<home>/probe_img.png");
            model.result().export("img1").set("size", "manualweb");
            model.result().export("img1").set("width", "1000");
            model.result().export("img1").set("height", "700");
            model.result().export("img1").run();
            P("image export OK");
        } catch (Throwable t) { P("image export FAILED :: " + t); }

        // also: does a 3D plot group support Multislice / Slice with a solution?
        try {
            model.result().create("pg2", "PlotGroup3D");
            model.result("pg2").create("ms1", "Multislice");
            model.result("pg2").feature("ms1").set("expr", "c");
            P("Multislice OK");
        } catch (Throwable t) { P("Multislice FAILED :: " + t); }
    }
}
