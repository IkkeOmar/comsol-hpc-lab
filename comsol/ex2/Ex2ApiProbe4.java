/* Ex2ApiProbe4.java -- decisive probe.
 *
 * Q1  Does the TDS velocity field actually convect in 3D?
 *     variant A: u_src = "root.comp1.u"            (the canonical COMSOL form,
 *                used by all 32 shipped DilutedSpecies+LaminarFlow models)
 *     variant B: u_src = "userdef" + u = {root.comp1.u, root.comp1.v, root.comp1.w}
 *     Measure c at the downstream end of a straight channel after 60 s.
 *
 * Q2  How do I read the per-parameter values out of a Parametric study?
 *     - plistarr "10,100,1000" (comma = discrete list) vs "10 100" (space)
 *     - a single Stationary step solving spf AND tds inside the sweep
 *     - enumerate every dataset: type, solution, number of solution columns
 *     - model.sol(...).getPVals() for every solver
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2ApiProbe4 {

    static Model model;

    static void p(String s) { System.out.println(s); }

    /** build a straight channel with proper BoxSelection inlet/outlet */
    static void buildChannel(String tag, boolean withCell) {
        model = ModelUtil.create(tag);
        model.component().create("comp1");
        model.param().set("D_sp", "200[um^2/s]");
        model.param().set("Q_in", "100[ul/min]");
        model.param().set("rho", "1000[kg/m^3]");
        model.param().set("k_r", "0.15[1/s]");

        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("b1", "Block");
        g.feature("b1").set("size", new String[]{"40", "2", "1"});
        g.feature("b1").set("pos", new String[]{"-20", "-1", "0"});
        if (withCell) {
            g.create("b2", "Block");
            g.feature("b2").set("size", new String[]{"4", "2", "0.2"});
            g.feature("b2").set("pos", new String[]{"-2", "-1", "-0.2"});
        }
        g.run();
        // inlet face at x = -20, outlet face at x = +20
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
        p(tag + " inlet faces=" + Arrays.toString(inF) + " outlet faces=" + Arrays.toString(outF));

        model.component("comp1").material().create("mat1", "Common");
        model.component("comp1").material("mat1").propertyGroup("def")
             .set("dynamicviscosity", "1e-3[Pa*s]").set("density", "1000[kg/m^3]");
        try { model.component("comp1").material("mat1").selection().all(); } catch (Throwable t) { }

        model.component("comp1").mesh().create("mesh1", "geom1");
        model.component("comp1").mesh("mesh1").create("ftet1", "FreeTet");
        model.component("comp1").mesh("mesh1").run();

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
        model.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
        model.component("comp1").physics("tds").feature("inflow1").selection().set(inF);
        model.component("comp1").physics("tds").feature("inflow1").set("c0", "1");
        model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
        model.component("comp1").physics("tds").feature("outflow1").selection().set(outF);
        if (withCell) {
            int[] cell = new int[]{2};
            model.component("comp1").physics("tds").create("fluid2", "Fluid", 3);
            model.component("comp1").physics("tds").feature("fluid2").selection().set(cell);
            model.component("comp1").physics("tds").feature("fluid2").set("u_src", "userdef");
            model.component("comp1").physics("tds").feature("fluid2").set("u", new String[]{"0", "0", "0"});
            model.component("comp1").physics("tds").feature("fluid2").set("D_c_mat", "userdef");
            model.component("comp1").physics("tds").feature("fluid2")
                 .set("D_c", new String[]{"0.7*D_sp", "0", "0", "0", "0.7*D_sp", "0", "0", "0", "0.7*D_sp"});
            model.component("comp1").physics("tds").create("reac1", "Reactions", 3);
            model.component("comp1").physics("tds").feature("reac1").selection().set(cell);
            model.component("comp1").physics("tds").feature("reac1").set("R_c_src", "userdef");
            model.component("comp1").physics("tds").feature("reac1").set("R_c", "-k_r*c");
        }
    }

    static void setVelocity(int variant) {
        PhysicsFeature cdm1 = model.component("comp1").physics("tds").feature("cdm1");
        if (variant == 0) {
            cdm1.set("u_src", "root.comp1.u");
            p("  u_src = root.comp1.u");
        } else {
            cdm1.set("u_src", "userdef");
            cdm1.set("u", new String[]{"root.comp1.u", "root.comp1.v", "root.comp1.w"});
            p("  u_src = userdef + 3-vector");
        }
        cdm1.set("D_c_mat", "userdef");
        cdm1.set("D_c", new String[]{"D_sp", "0", "0", "0", "D_sp", "0", "0", "0", "D_sp"});
        p("  D_c readback = " + cdm1.getString("D_c"));
    }

    public static void main(String[] args) {
        try {
            // ================= Q1 ============================================
            for (int variant = 0; variant < 2; variant++) {
                p("############ Q1 variant " + variant + " ############");
                buildChannel("conv" + variant, false);
                setVelocity(variant);

                model.study().create("std1");
                model.study("std1").create("stat", "Stationary");
                model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
                model.study("std1").feature("stat").setSolveFor("/physics/tds", false);
                model.study("std1").createAutoSequences("all");
                model.study("std1").run();
                p("  std1 done");

                // how fast is the flow really?
                try {
                    model.result().numerical().create("intQ", "IntSurface");
                    model.result().numerical("intQ").selection().set(model.selection("geom1_selIn").entities(2));
                    model.result().numerical("intQ").setIndex("expr", "u*nx+v*ny+w*nz", 0);
                    model.result().numerical("intQ").run();
                    p("  Q through inlet = " + model.result().numerical("intQ").getReal()[0][0] + " m^3/s");
                } catch (Throwable t) { p("  Q int failed: " + t.getMessage()); }

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
                model.study("std2").run();
                p("  std2 done");

                // c at the downstream end (x = +19 mm) and the domain average
                model.result().dataset().create("cptOut", "CutPoint3D");
                model.result().dataset("cptOut").set("pointx", "19");
                model.result().dataset("cptOut").set("pointy", "0");
                model.result().dataset("cptOut").set("pointz", "0.5");
                model.result().dataset("cptOut").set("data", "dset2");
                model.result().numerical().create("pevOut", "EvalPoint");
                model.result().numerical("pevOut").setIndex("expr", "c", 0);
                model.result().numerical("pevOut").set("data", "cptOut");
                model.result().numerical("pevOut").run();
                double[][] d = model.result().numerical("pevOut").getReal();
                p("  Q1v" + variant + " c at x=+19mm: " + (d.length > 0 ? Arrays.toString(d[0]) : "none"));

                model.result().numerical().create("avAll", "AvVolume");
                model.result().numerical("avAll").selection().set(new int[]{1});
                model.result().numerical("avAll").setIndex("expr", "c", 0);
                model.result().numerical("avAll").set("data", "dset2");
                model.result().numerical("avAll").run();
                double[][] a = model.result().numerical("avAll").getReal();
                p("  Q1v" + variant + " domain-avg c: " + (a.length > 0 ? Arrays.toString(a[0]) : "none"));
                model = null;
            }

            // ================= Q2 ============================================
            p("############ Q2 parametric sweep ############");
            buildChannel("psweep", true);
            setVelocity(1);
            int[] cell = new int[]{2};

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
            try {
                model.study("std3").run();
                p("  std3 done");
            } catch (Throwable t) {
                p("  std3 FAILED: " + t.getMessage());
            }
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
                    model.result().numerical("avX").selection().set(cell);
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
                try {
                    p("    dset " + ds + " solutionparams = "
                      + Arrays.toString(model.result().dataset(ds).getStringArray("solutionparams")));
                } catch (Throwable t) { }
                // does the dataset expose a parametrisation list?
                try {
                    p("    dset " + ds + " parametrix = "
                      + Arrays.toString(model.result().dataset(ds).getStringArray("parametrix")));
                } catch (Throwable t) { }
            }
            p("APIPROBE4 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("APIPROBE4 FAILED: " + e);
        }
        p("APIPROBE4 EXIT");
    }
}
