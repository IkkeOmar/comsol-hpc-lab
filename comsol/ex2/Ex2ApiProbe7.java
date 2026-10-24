/* Ex2ApiProbe7.java -- the decisive matrix test.
 *
 * Symptom: with std2 = Transient solving only tds, taking the velocity from
 * std1 (stationary spf), the species transport is PURE DIFFUSION.  The
 * u_src="root.comp1.u" and u_src="userdef"+3-vector forms give bit-identical
 * results, so the velocity expression evaluates to 0 inside the transient.
 *
 * Variants tested (all on the same 40 mm channel, c0 = 1 at the inlet):
 *   1  two STUDIES, transient solves tds only, notsolmethod=sol (the old code)
 *   2  ONE STUDY with two STEPS (stat spf, then transient tds)
 *   3  one study, one TRANSIENT step solving spf AND tds
 *   4  like 2 but with D_c as the 9-element tensor array (the shipped 3D form)
 *
 * Also: does D_c need 9 entries in 3D?
 *
 * Pass/fail: c at x = +19 mm after 60 s must be ~1 (advected), not ~1e-200.
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2ApiProbe7 {

    static Model model;

    static void p(String s) { System.out.println(s); }

    /** build the channel; returns the spf/tds interface tags already set up */
    static void buildChannel(String tag, boolean tensorD, boolean userdefU) {
        model = ModelUtil.create(tag);
        model.component().create("comp1");
        model.param().set("D_sp", "200[um^2/s]");
        model.param().set("Q_in", "100[ul/min]");
        model.param().set("rho", "1000[kg/m^3]");

        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("b1", "Block");
        g.feature("b1").set("size", new String[]{"40", "2", "1"});
        g.feature("b1").set("pos", new String[]{"-20", "-1", "0"});
        g.run();
        g.create("selIn", "BoxSelection");
        g.feature("selIn").set("entitydim", 2);
        g.feature("selIn").set("condition", "allvertices");
        g.feature("selIn").set("xmin", "-20.1"); g.feature("selIn").set("xmax", "-19.9");
        g.feature("selIn").set("ymin", "-1.1");  g.feature("selIn").set("ymax", "1.1");
        g.feature("selIn").set("zmin", "-0.1");  g.feature("selIn").set("zmax", "1.1");
        g.create("selOut", "BoxSelection");
        g.feature("selOut").set("entitydim", 2);
        g.feature("selOut").set("condition", "allvertices");
        g.feature("selOut").set("xmin", "19.9"); g.feature("selOut").set("xmax", "20.1");
        g.feature("selOut").set("ymin", "-1.1"); g.feature("selOut").set("ymax", "1.1");
        g.feature("selOut").set("zmin", "-0.1"); g.feature("selOut").set("zmax", "1.1");
        g.run();
        int[] inF = model.selection("geom1_selIn").entities(2);
        int[] outF = model.selection("geom1_selOut").entities(2);

        model.component("comp1").material().create("mat1", "Common");
        model.component("comp1").material("mat1").propertyGroup("def")
             .set("dynamicviscosity", "1e-3[Pa*s]").set("density", "1000[kg/m^3]");
        try { model.component("comp1").material("mat1").selection().all(); } catch (Throwable t) { }

        model.component("comp1").mesh().create("mesh1", "geom1");
        model.component("comp1").mesh("mesh1").create("size1", "Size");
        model.component("comp1").mesh("mesh1").feature("size1").set("custom", "on");
        model.component("comp1").mesh("mesh1").feature("size1").set("hmax", "0.5");
        model.component("comp1").mesh("mesh1").feature("size1").set("hmin", "0.15");
        model.component("comp1").mesh("mesh1").feature("size1").set("hgrad", "1.2");
        model.component("comp1").mesh("mesh1").create("ftet1", "FreeTet");
        model.component("comp1").mesh("mesh1").run();
        p(tag + " MESH numElem=" + model.component("comp1").mesh("mesh1").stat().getNumElem());

        model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
        model.component("comp1").physics("spf").create("inl1", "Inlet", 2);
        model.component("comp1").physics("spf").feature("inl1").selection().set(inF);
        model.component("comp1").physics("spf").feature("inl1").set("BoundaryCondition", "MassFlow");
        model.component("comp1").physics("spf").feature("inl1").set("MassFlowType", "MassFlowRate");
        model.component("comp1").physics("spf").feature("inl1").set("mfr", "rho*Q_in");
        model.component("comp1").physics("spf").create("out1", "Outlet", 2);
        model.component("comp1").physics("spf").feature("out1").selection().set(outF);
        model.component("comp1").physics("spf").feature("out1").set("p0", "0");

        model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
        PhysicsFeature cdm1 = model.component("comp1").physics("tds").feature("cdm1");
        if (userdefU) {
            cdm1.set("u_src", "userdef");
            cdm1.set("u", new String[]{"root.comp1.u", "root.comp1.v", "root.comp1.w"});
        } else {
            cdm1.set("u_src", "root.comp1.u");
        }
        cdm1.set("D_c_mat", "userdef");
        if (tensorD) {
            cdm1.set("D_c", new String[]{"D_sp", "0", "0", "0", "D_sp", "0", "0", "0", "D_sp"});
        } else {
            cdm1.set("D_c", "D_sp");
        }
        model.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
        model.component("comp1").physics("tds").feature("inflow1").selection().set(inF);
        model.component("comp1").physics("tds").feature("inflow1").set("c0", "1");
        model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
        model.component("comp1").physics("tds").feature("outflow1").selection().set(outF);
    }

    /** evaluate c and spf.U at x = +19 mm on a dataset */
    static void evalAt(String dset, String label) {
        try {
            model.result().dataset().create("cptX", "CutPoint3D");
            model.result().dataset("cptX").set("pointx", "19");
            model.result().dataset("cptX").set("pointy", "0");
            model.result().dataset("cptX").set("pointz", "0.5");
            model.result().dataset("cptX").set("data", dset);
            model.result().numerical().create("pevX", "EvalPoint");
            model.result().numerical("pevX").setIndex("expr", "c", 0);
            model.result().numerical("pevX").setIndex("expr", "spf.U", 1);
            model.result().numerical("pevX").set("data", "cptX");
            model.result().numerical("pevX").run();
            double[][] d = model.result().numerical("pevX").getReal();
            p("  " + label + " c(x=19mm)     = " + (d.length > 0 ? Arrays.toString(d[0]) : "none"));
            p("  " + label + " spf.U(x=19mm) = " + (d.length > 1 ? Arrays.toString(d[1]) : "none"));
        } catch (Throwable t) {
            p("  " + label + " eval FAILED: " + t.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            // ============ variant 1: two studies, old coupling ================
            p("############ V1: two studies, notsolmethod=sol ############");
            buildChannel("V1", false, false);
            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
            model.study("std1").feature("stat").setSolveFor("/physics/tds", false);
            model.study("std1").createAutoSequences("all");
            model.study("std1").run();
            model.study().create("std2");
            model.study("std2").create("time", "Transient");
            model.study("std2").feature("time").set("tlist", "range(0,30,60)");
            model.study("std2").feature("time").set("notsolmethod", "sol");
            model.study("std2").feature("time").set("notstudy", "std1");
            model.study("std2").feature("time").set("notstudystep", "stat");
            model.study("std2").feature("time").set("notsolnum", "auto");
            model.study("std2").feature("time").setSolveFor("/physics/spf", false);
            model.study("std2").feature("time").setSolveFor("/physics/tds", true);
            model.study("std2").createAutoSequences("all");
            try { model.study("std2").run(); } catch (Throwable t) { p("  V1 std2 FAILED: " + t.getMessage()); }
            evalAt("dset2", "V1");
            model = null;

            // ============ variant 2: ONE study, two steps ====================
            p("############ V2: one study, stat step + transient step ############");
            buildChannel("V2", false, false);
            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
            model.study("std1").feature("stat").setSolveFor("/physics/tds", false);
            model.study("std1").create("time", "Transient");
            model.study("std1").feature("time").set("tlist", "range(0,30,60)");
            model.study("std1").feature("time").setSolveFor("/physics/spf", false);
            model.study("std1").feature("time").setSolveFor("/physics/tds", true);
            model.study("std1").createAutoSequences("all");
            try { model.study("std1").run(); } catch (Throwable t) { p("  V2 FAILED: " + t.getMessage()); }
            p("  V2 solvers=" + Arrays.toString(model.sol().tags())
              + " datasets=" + Arrays.toString(model.result().dataset().tags()));
            evalAt("dset2", "V2");
            model = null;

            // ============ variant 3: one transient step, spf + tds =============
            p("############ V3: one transient step solving spf AND tds ############");
            buildChannel("V3", false, false);
            model.study().create("std1");
            model.study("std1").create("time", "Transient");
            model.study("std1").feature("time").set("tlist", "range(0,30,60)");
            model.study("std1").feature("time").setSolveFor("/physics/spf", true);
            model.study("std1").feature("time").setSolveFor("/physics/tds", true);
            model.study("std1").createAutoSequences("all");
            try { model.study("std1").run(); } catch (Throwable t) { p("  V3 FAILED: " + t.getMessage()); }
            p("  V3 solvers=" + Arrays.toString(model.sol().tags())
              + " datasets=" + Arrays.toString(model.result().dataset().tags()));
            evalAt("dset1", "V3");
            model = null;

            // ============ variant 4: one study two steps + tensor D + userdef ==
            p("############ V4: one study two steps, tensor D_c, userdef u ############");
            buildChannel("V4", true, true);
            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
            model.study("std1").feature("stat").setSolveFor("/physics/tds", false);
            model.study("std1").create("time", "Transient");
            model.study("std1").feature("time").set("tlist", "range(0,30,60)");
            model.study("std1").feature("time").setSolveFor("/physics/spf", false);
            model.study("std1").feature("time").setSolveFor("/physics/tds", true);
            model.study("std1").createAutoSequences("all");
            try { model.study("std1").run(); } catch (Throwable t) { p("  V4 FAILED: " + t.getMessage()); }
            evalAt("dset2", "V4");
            model = null;

            p("APIPROBE7 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("APIPROBE7 FAILED: " + e);
        }
        p("APIPROBE7 EXIT");
    }
}
