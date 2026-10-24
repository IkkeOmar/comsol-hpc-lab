/* Ex2ApiProbe6.java -- decisive probe on the STUDY COUPLING.
 *
 * Evidence so far: with std2 = Transient solving ONLY tds and taking the
 * velocity from std1 (stationary spf), the species transport is PURE
 * DIFFUSION -- identical numbers for u_src="root.comp1.u" and for
 * u_src="userdef"+3-vector.  Both forms give exactly the same result, which
 * means root.comp1.u evaluates to 0 inside std2, i.e. the "values of
 * dependent variables from another study" coupling is NOT active.
 *
 * Q1  Dump the study-step property list and find the CORRECT property names
 *     for "Values of Dependent Variables -> Solution: std1".
 *     (The old code used notsolmethod/notstudy/notstudystep/notsolnum.)
 *
 * Q2  Three transient variants on the same channel, c at the downstream end:
 *     A) tds only, coupled from std1 with the OLD property names
 *     B) tds only, coupled from std1 with the NEW property names found in Q1
 *     C) spf AND tds both solved inside the transient step
 *     Whichever gives c ~ 1 at x=+19 mm is the one that convects.
 *
 * Q3  Same question for the stationary coupled sweep.
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2ApiProbe6 {

    static Model model;

    static void p(String s) { System.out.println(s); }

    static void buildChannel(String tag) {
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
        model.component("comp1").mesh("mesh1").feature("size1").set("hmax", "0.4");
        model.component("comp1").mesh("mesh1").feature("size1").set("hmin", "0.1");
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
        cdm1.set("u_src", "userdef");
        cdm1.set("u", new String[]{"root.comp1.u", "root.comp1.v", "root.comp1.w"});
        cdm1.set("D_c_mat", "userdef");
        cdm1.set("D_c", new String[]{"D_sp", "0", "0", "0", "D_sp", "0", "0", "0", "D_sp"});
        model.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
        model.component("comp1").physics("tds").feature("inflow1").selection().set(inF);
        model.component("comp1").physics("tds").feature("inflow1").set("c0", "1");
        model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
        model.component("comp1").physics("tds").feature("outflow1").selection().set(outF);
    }

    /** evaluate c at x = +19 mm on a dataset */
    static void evalPoint(String dset, String label) {
        try {
            model.result().dataset().create("cptX", "CutPoint3D");
            model.result().dataset("cptX").set("pointx", "19");
            model.result().dataset("cptX").set("pointy", "0");
            model.result().dataset("cptX").set("pointz", "0.5");
            model.result().dataset("cptX").set("data", dset);
            model.result().numerical().create("pevX", "EvalPoint");
            model.result().numerical("pevX").setIndex("expr", "c", 0);
            model.result().numerical("pevX").set("data", "cptX");
            model.result().numerical("pevX").run();
            double[][] d = model.result().numerical("pevX").getReal();
            p("  " + label + " c(x=+19mm) = " + (d.length > 0 ? Arrays.toString(d[0]) : "none"));
            // also the velocity magnitude there, to see if the flow is visible
            model.result().numerical("pevX").setIndex("expr", "spf.U", 1);
            model.result().numerical("pevX").run();
            double[][] v = model.result().numerical("pevX").getReal();
            p("  " + label + " spf.U       = " + (v.length > 1 ? Arrays.toString(v[1]) : "none"));
        } catch (Throwable t) {
            p("  " + label + " eval FAILED: " + t.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            p("############ Q1: study step property list ############");
            buildChannel("prop");
            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
            model.study("std1").feature("stat").setSolveFor("/physics/tds", false);
            model.study().create("std2");
            model.study("std2").create("time", "Transient");
            p("std2 time properties = " + Arrays.toString(
                model.study("std2").feature("time").properties()).replace(", ", ",\n  "));
            for (String prop : new String[]{"notsolmethod", "notstudy", "notstudystep", "notsolnum",
                                           "usesol", "initstudyhide", "useinitsol", "initmethod",
                                           "initstudy", "initsol", "solnum", "usesolnum",
                                           "notsolvertype", "notsolnumtype"}) {
                try {
                    p("  allowed(" + prop + ") = " + Arrays.toString(
                        model.study("std2").feature("time").getAllowedPropertyValues(prop)));
                } catch (Throwable t) {
                    p("  allowed(" + prop + ") FAILED: " + t.getMessage());
                }
            }
            model = null;

            // ---------- variant A: OLD property names ----------
            p("############ Q2 variant A: old notstudy/notsolmethod ############");
            buildChannel("varA");
            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
            model.study("std1").feature("stat").setSolveFor("/physics/tds", false);
            model.study("std1").createAutoSequences("all");
            model.study("std1").run();
            p("  std1 done");
            evalPoint("dset1", "A std1(spf)");
            model.study().create("std2");
            model.study("std2").create("time", "Transient");
            model.study("std2").feature("time").set("tlist", "range(0,20,60)");
            model.study("std2").feature("time").set("notsolmethod", "sol");
            model.study("std2").feature("time").set("notstudy", "std1");
            model.study("std2").feature("time").set("notstudystep", "stat");
            model.study("std2").feature("time").set("notsolnum", "auto");
            model.study("std2").feature("time").setSolveFor("/physics/spf", false);
            model.study("std2").feature("time").setSolveFor("/physics/tds", true);
            model.study("std2").createAutoSequences("all");
            try { model.study("std2").run(); p("  std2 done"); }
            catch (Throwable t) { p("  std2 FAILED: " + t.getMessage()); }
            evalPoint("dset2", "A std2(tds only)");
            model = null;

            // ---------- variant C: spf AND tds in the transient ----------
            p("############ Q2 variant C: spf + tds in the transient step ############");
            buildChannel("varC");
            model.study().create("std2");
            model.study("std2").create("time", "Transient");
            model.study("std2").feature("time").set("tlist", "range(0,20,60)");
            model.study("std2").feature("time").setSolveFor("/physics/spf", true);
            model.study("std2").feature("time").setSolveFor("/physics/tds", true);
            model.study("std2").createAutoSequences("all");
            try { model.study("std2").run(); p("  std2 done"); }
            catch (Throwable t) { p("  std2 FAILED: " + t.getMessage()); }
            evalPoint("dset1", "C std2(spf+tds)");
            model = null;

            // ---------- Q3: stationary coupled sweep ----------
            p("############ Q3: stationary sweep, one step, spf+tds ############");
            buildChannel("sweep");
            model.study().create("std3");
            model.study("std3").create("param", "Parametric");
            model.study("std3").feature("param").set("pname", new String[]{"Q_in"});
            model.study("std3").feature("param").set("plistarr", new String[]{"10,100,1000"});
            model.study("std3").feature("param").set("punit", new String[]{"ul/min"});
            model.study("std3").feature("param").set("sweeptype", "sparse");
            model.study("std3").create("stat", "Stationary");
            model.study("std3").feature("stat").setSolveFor("/physics/spf", true);
            model.study("std3").feature("stat").setSolveFor("/physics/tds", true);
            model.study("std3").createAutoSequences("all");
            try { model.study("std3").run(); p("  std3 done"); }
            catch (Throwable t) { p("  std3 FAILED: " + t.getMessage()); }
            p("  solvers = " + Arrays.toString(model.sol().tags()));
            p("  datasets = " + Arrays.toString(model.result().dataset().tags()));
            for (String s : model.sol().tags()) {
                try { p("    sol " + s + " pvals=" + Arrays.toString(model.sol(s).getPVals())); }
                catch (Throwable t) { p("    sol " + s + " pvals FAILED: " + t.getMessage()); }
            }
            for (String ds : model.result().dataset().tags()) {
                String solTag = "?", dType = "?";
                try { solTag = model.result().dataset(ds).getString("solution"); } catch (Throwable t) { }
                try { dType = model.result().dataset(ds).getType(); } catch (Throwable t) { }
                try {
                    model.result().numerical().create("avX", "AvVolume");
                    model.result().numerical("avX").selection().set(new int[]{1});
                    model.result().numerical("avX").setIndex("expr", "c", 0);
                    model.result().numerical("avX").set("data", ds);
                    model.result().numerical("avX").run();
                    double[][] d = model.result().numerical("avX").getReal();
                    p("    AVDOM " + ds + " type=" + dType + " sol=" + solTag
                      + " cols=" + (d.length > 0 ? d[0].length : 0)
                      + " vals=" + (d.length > 0 ? Arrays.toString(d[0]) : "none"));
                    model.result().numerical().remove("avX");
                } catch (Throwable t) {
                    p("    AVDOM " + ds + " FAILED: " + t.getMessage());
                    try { model.result().numerical().remove("avX"); } catch (Throwable t2) { }
                }
            }
            p("APIPROBE6 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("APIPROBE6 FAILED: " + e);
        }
        p("APIPROBE6 EXIT");
    }
}
