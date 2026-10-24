/* Ex2ApiProbe3.java -- third ground-truth probe.
 *
 *  Q1  3D CONVECTION: does u_src="root.comp1.u" actually transport species in
 *      3D, or does it need u_src="userdef" + the 3-vector?  The old Ex2 run
 *      produced c ~ 1e-87 (no convection at all) with u_src="root.comp1.u".
 *      Test: a straight channel, measure c at a downstream point both ways.
 *
 *  Q2  PARAMETRIC SWEEP: with plistarr = "10,100,1000" (COMMA separated!),
 *      how many parameter values, which dataset holds the species solution,
 *      and how do the AvVolume columns map onto the Q values?
 *      Also: a SINGLE Stationary step solving spf+tds vs two coupled steps.
 *
 *  Q3  Do probe nodes produce a TABLE during a batch solve, and is there a
 *      "probesel" property on the study step?
 *
 *  Q4  EvalPoint on a TRANSIENT dataset: how many columns come back?
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2ApiProbe3 {

    static Model model;

    static void p(String s) { System.out.println(s); }

    public static void main(String[] args) {
        try {
            // ===================================================== Q1 ==========
            p("################ Q1: 3D CONVECTION TEST ################");
            for (int variant = 0; variant < 2; variant++) {
                String tag = "conv" + variant;
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

                model.component("comp1").material().create("mat1", "Common");
                model.component("comp1").material("mat1").propertyGroup("def")
                     .set("dynamicviscosity", "1e-3[Pa*s]").set("density", "1000[kg/m^3]");
                try { model.component("comp1").material("mat1").selection().all(); } catch (Throwable t) { }

                model.component("comp1").mesh().create("mesh1", "geom1");
                model.component("comp1").mesh("mesh1").create("ftet1", "FreeTet");
                model.component("comp1").mesh("mesh1").run();

                // inlet on the x=-20 face, outlet on the x=+20 face
                model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
                model.component("comp1").physics("spf").create("inl1", "Inlet", 2);
                model.component("comp1").physics("spf").feature("inl1").selection().set(1);
                model.component("comp1").physics("spf").feature("inl1")
                     .set("BoundaryCondition", "MassFlow");
                model.component("comp1").physics("spf").feature("inl1").set("MassFlowType", "MassFlowRate");
                model.component("comp1").physics("spf").feature("inl1").set("mfr", "rho*Q_in");
                model.component("comp1").physics("spf").create("out1", "Outlet", 2);
                model.component("comp1").physics("spf").feature("out1").selection().set(2);
                model.component("comp1").physics("spf").feature("out1").set("p0", "0");

                model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
                PhysicsFeature cdm1 = model.component("comp1").physics("tds").feature("cdm1");
                if (variant == 0) {
                    cdm1.set("u_src", "root.comp1.u");
                    p("Q1 variant 0: u_src = root.comp1.u (scalar tag)");
                    try { p("   readback u=" + Arrays.toString(cdm1.getStringArray("u"))); }
                    catch (Throwable t) { p("   u readback failed: " + t.getMessage()); }
                } else {
                    cdm1.set("u_src", "userdef");
                    cdm1.set("u", new String[]{"root.comp1.u", "root.comp1.v", "root.comp1.w"});
                    p("Q1 variant 1: u_src = userdef + 3-vector");
                }
                cdm1.set("D_c_mat", "userdef");
                cdm1.set("D_c", "D_sp");
                model.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
                model.component("comp1").physics("tds").feature("inflow1").selection().set(1);
                model.component("comp1").physics("tds").feature("inflow1").set("c0", "1");
                model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
                model.component("comp1").physics("tds").feature("outflow1").selection().set(2);

                // stationary spf, then transient tds (60 s) -> is c transported?
                model.study().create("std1");
                model.study("std1").create("stat", "Stationary");
                model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
                model.study("std1").feature("stat").setSolveFor("/physics/tds", false);
                model.study("std1").createAutoSequences("all");
                model.study("std1").run();

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
                p("Q1 variant " + variant + ": std2 solved");

                // concentration at the outlet end of the channel, x = +19 mm
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
                p("Q1 variant " + variant + " c at x=+19mm: rows=" + d.length
                  + " cols=" + (d.length > 0 ? d[0].length : 0)
                  + " vals=" + (d.length > 0 ? Arrays.toString(d[0]) : "none"));
                // average over the whole domain
                model.result().numerical().create("avAll", "AvVolume");
                model.result().numerical("avAll").selection().set(new int[]{1});
                model.result().numerical("avAll").setIndex("expr", "c", 0);
                model.result().numerical("avAll").set("data", "dset2");
                model.result().numerical("avAll").run();
                double[][] a = model.result().numerical("avAll").getReal();
                p("Q1 variant " + variant + " domain-average c: "
                  + (a.length > 0 ? Arrays.toString(a[0]) : "none"));
                model = null;
            }

            // ===================================================== Q2 ==========
            p("################ Q2: PARAMETRIC SWEEP ################");
            model = ModelUtil.create("psweep");
            model.component().create("comp1");
            model.param().set("D_sp", "200[um^2/s]");
            model.param().set("Q_in", "100[ul/min]");
            model.param().set("rho", "1000[kg/m^3]");
            model.param().set("k_r", "0.15[1/s]");

            GeomSequence g2 = model.component("comp1").geom().create("geom1", 3);
            g2.lengthUnit("mm");
            g2.create("b1", "Block");
            g2.feature("b1").set("size", new String[]{"10", "4", "1"});
            g2.feature("b1").set("pos", new String[]{"-5", "-2", "0"});
            g2.create("b2", "Block");
            g2.feature("b2").set("size", new String[]{"4", "4", "0.2"});
            g2.feature("b2").set("pos", new String[]{"-2", "-2", "-0.2"});
            g2.run();
            int[] cell = new int[]{2};

            model.component("comp1").material().create("mat1", "Common");
            model.component("comp1").material("mat1").propertyGroup("def")
                 .set("dynamicviscosity", "1e-3[Pa*s]").set("density", "1000[kg/m^3]");
            try { model.component("comp1").material("mat1").selection().all(); } catch (Throwable t) { }
            model.component("comp1").mesh().create("mesh1", "geom1");
            model.component("comp1").mesh("mesh1").create("ftet1", "FreeTet");
            model.component("comp1").mesh("mesh1").run();

            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            model.component("comp1").physics("spf").selection().geom("geom1", 3).set(new int[]{1});
            model.component("comp1").physics("spf").create("inl1", "Inlet", 2);
            model.component("comp1").physics("spf").feature("inl1").selection().set(1);
            model.component("comp1").physics("spf").feature("inl1").set("BoundaryCondition", "MassFlow");
            model.component("comp1").physics("spf").feature("inl1").set("MassFlowType", "MassFlowRate");
            model.component("comp1").physics("spf").feature("inl1").set("mfr", "rho*Q_in");
            model.component("comp1").physics("spf").create("out1", "Outlet", 2);
            model.component("comp1").physics("spf").feature("out1").selection().set(2);
            model.component("comp1").physics("spf").feature("out1").set("p0", "0");

            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            PhysicsFeature cdm1 = model.component("comp1").physics("tds").feature("cdm1");
            cdm1.set("u_src", "userdef");
            cdm1.set("u", new String[]{"root.comp1.u", "root.comp1.v", "root.comp1.w"});
            cdm1.set("D_c_mat", "userdef");
            cdm1.set("D_c", "D_sp");
            model.component("comp1").physics("tds").create("fluid2", "Fluid", 3);
            model.component("comp1").physics("tds").feature("fluid2").selection().set(cell);
            model.component("comp1").physics("tds").feature("fluid2").set("u_src", "userdef");
            model.component("comp1").physics("tds").feature("fluid2").set("u", new String[]{"0", "0", "0"});
            model.component("comp1").physics("tds").feature("fluid2").set("D_c_mat", "userdef");
            model.component("comp1").physics("tds").feature("fluid2").set("D_c", "0.7*D_sp");
            model.component("comp1").physics("tds").create("reac1", "Reactions", 3);
            model.component("comp1").physics("tds").feature("reac1").selection().set(cell);
            model.component("comp1").physics("tds").feature("reac1").set("R_c_src", "userdef");
            model.component("comp1").physics("tds").feature("reac1").set("R_c", "-k_r*c");
            model.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
            model.component("comp1").physics("tds").feature("inflow1").selection().set(1);
            model.component("comp1").physics("tds").feature("inflow1").set("c0", "1");
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
            model.component("comp1").physics("tds").feature("outflow1").selection().set(2);

            // ---- Q3: probe nodes + probesel -----------------------------------
            p("################ Q3: PROBES ################");
            try {
                model.probe().create("domAvg", "Domain");
                model.probe("domAvg").model("comp1");
                model.probe("domAvg").set("expr", "c");
                model.probe("domAvg").set("type", "average");
                model.probe("domAvg").selection().set(cell);
                p("domAvg probe created");
            } catch (Throwable t) { p("domAvg FAILED: " + t.getMessage()); }
            try {
                model.probe().create("pP1", "DomainPoint");
                model.probe("pP1").model("comp1");
                model.probe("pP1").set("coords", new double[][]{{0.0, 0.0, -0.0001}});
                model.probe("pP1").create("pe", "PointExpr");
                model.probe("pP1").feature("pe").set("expr", "c");
                p("pP1 probe created");
            } catch (Throwable t) { p("pP1 FAILED: " + t.getMessage()); }
            try {
                model.study("std3").feature("stat").getAllowedPropertyValues("probesel");
            } catch (Throwable t) { }

            // ---- Q2: parametric sweep, ONE stationary step solving both -------
            model.study().create("std3");
            model.study("std3").create("param", "Parametric");
            model.study("std3").feature("param").set("pname", new String[]{"Q_in"});
            // COMMA separated = discrete list.  A SPACE separated list is a RANGE.
            model.study("std3").feature("param").set("plistarr", new String[]{"10,100,1000"});
            model.study("std3").feature("param").set("punit", new String[]{"ul/min"});
            model.study("std3").feature("param").set("sweeptype", "sparse");
            model.study("std3").create("stat", "Stationary");
            model.study("std3").feature("stat").setSolveFor("/physics/spf", true);
            model.study("std3").feature("stat").setSolveFor("/physics/tds", true);
            try {
                model.study("std3").feature("stat").getAllowedPropertyValues("probesel");
                p("stat probesel allowed = "
                  + Arrays.toString(model.study("std3").feature("stat").getAllowedPropertyValues("probesel")));
            } catch (Throwable t) { p("stat probesel probe failed: " + t.getMessage()); }
            model.study("std3").createAutoSequences("all");
            try {
                model.study("std3").run();
                p("std3 done");
            } catch (Throwable t) {
                p("std3 FAILED: " + t.getMessage());
                t.printStackTrace();
            }
            p("solvers = " + Arrays.toString(model.sol().tags()));
            p("datasets = " + Arrays.toString(model.result().dataset().tags()));
            p("tables = " + Arrays.toString(model.result().table().tags()));
            for (String s : model.sol().tags()) {
                try { p("  sol " + s + " pvals=" + Arrays.toString(model.sol(s).getPVals())); }
                catch (Throwable t) { p("  sol " + s + " pvals FAILED: " + t.getMessage()); }
            }
            for (String ds : model.result().dataset().tags()) {
                String solTag = "?";
                try { solTag = model.result().dataset(ds).getString("solution"); } catch (Throwable t) { }
                String dType = "?";
                try { dType = model.result().dataset(ds).getType(); } catch (Throwable t) { }
                try {
                    model.result().numerical().create("avX", "AvVolume");
                    model.result().numerical("avX").selection().set(cell);
                    model.result().numerical("avX").setIndex("expr", "c", 0);
                    model.result().numerical("avX").set("data", ds);
                    model.result().numerical("avX").run();
                    double[][] d = model.result().numerical("avX").getReal();
                    p("  AVDOM " + ds + " type=" + dType + " sol=" + solTag
                      + " cols=" + (d.length > 0 ? d[0].length : 0)
                      + " vals=" + (d.length > 0 ? Arrays.toString(d[0]) : "none"));
                    model.result().numerical().remove("avX");
                } catch (Throwable t) {
                    p("  AVDOM " + ds + " FAILED: " + t.getMessage());
                    try { model.result().numerical().remove("avX"); } catch (Throwable t2) { }
                }
            }
            // read the parametric dataset's own parameter list
            for (String ds : model.result().dataset().tags()) {
                try {
                    p("  dset " + ds + " labels = " + Arrays.toString(
                        model.result().dataset(ds).getStringArray("solutionparams")));
                } catch (Throwable t) { }
            }
            p("APIPROBE3 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("APIPROBE3 FAILED: " + e);
        }
        p("APIPROBE3 EXIT");
    }
}
