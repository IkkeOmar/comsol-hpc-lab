/* Ex2ApiProbe2.java -- second ground-truth probe: the part E/F physics
 * (TDS domain overrides for the cell layer), the probe nodes during a
 * transient solve, and the parametric stationary sweep result extraction.
 *
 * Facts still unknown:
 *   1. TDS "Fluid" domain feature: type name + the property names for
 *      u_src / u / D_c_mat / D_c.
 *   2. TDS "Reactions" domain feature: type name + R_c / R_c_src property.
 *   3. TDS "init" domain feature: type name + initc property.
 *   4. Do model.probe() Domain / DomainPoint nodes survive a transient solve?
 *   5. Parametric stationary sweep: how to read the per-parameter values
 *      (getPVals on which solver, and the column order of AvVolume).
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2ApiProbe2 {

    static Model model;

    static void p(String s) { System.out.println(s); }

    static void props(String label, PhysicsFeature f) {
        try { p(label + " properties = " + Arrays.toString(f.properties())); }
        catch (Throwable t) { p(label + " property dump failed: " + t.getMessage()); }
    }

    static void allowed(String label, PhysicsFeature f, String prop) {
        try { p(label + " " + prop + " allowed = " + Arrays.toString(f.getAllowedPropertyValues(prop))); }
        catch (Throwable t) { p(label + " " + prop + " allowed FAILED: " + t.getMessage()); }
    }

    public static void main(String[] args) {
        try {
            model = ModelUtil.create("ApiProbe2");
            model.component().create("comp1");
            model.param().set("D_sp", "200[um^2/s]");
            model.param().set("Q_in", "100[ul/min]");
            model.param().set("rho", "1000[kg/m^3]");
            model.param().set("k_r", "0.15[1/s]");

            GeomSequence g = model.component("comp1").geom().create("geom1", 3);
            g.lengthUnit("mm");
            g.create("b1", "Block");
            g.feature("b1").set("size", new String[]{"10", "4", "1"});
            g.feature("b1").set("pos", new String[]{"-5", "-2", "0"});
            g.create("b2", "Block");
            g.feature("b2").set("size", new String[]{"4", "4", "0.2"});
            g.feature("b2").set("pos", new String[]{"-2", "-2", "-0.2"});
            g.run();
            p("GEOM domains=" + g.getNDomains() + " boundaries=" + g.getNBoundaries());
            int[] all = new int[]{1, 2};
            int[] cell = new int[]{2};

            model.component("comp1").material().create("mat1", "Common");
            model.component("comp1").material("mat1").propertyGroup("def")
                 .set("dynamicviscosity", "1e-3[Pa*s]").set("density", "1000[kg/m^3]");
            try { model.component("comp1").material("mat1").selection().all(); }
            catch (Throwable t) { p("material all() failed: " + t.getMessage()); }

            model.component("comp1").mesh().create("mesh1", "geom1");
            model.component("comp1").mesh("mesh1").create("ftet1", "FreeTet");
            model.component("comp1").mesh("mesh1").run();
            p("MESH numElem=" + model.component("comp1").mesh("mesh1").stat().getNumElem());

            // ---------------- spf on the fluid domain only ---------------------
            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            model.component("comp1").physics("spf").selection().geom("geom1", 3).set(new int[]{1});
            model.component("comp1").physics("spf").create("inl1", "Inlet", 2);
            model.component("comp1").physics("spf").feature("inl1").selection().set(1);
            try {
                model.component("comp1").physics("spf").feature("inl1").set("BoundaryCondition", "MassFlow");
                model.component("comp1").physics("spf").feature("inl1").set("MassFlowType", "MassFlowRate");
                model.component("comp1").physics("spf").feature("inl1").set("mfr", "rho*Q_in");
                p("SPF inlet OK");
            } catch (Throwable t) { p("SPF inlet FAILED: " + t.getMessage()); }
            model.component("comp1").physics("spf").create("out1", "Outlet", 2);
            model.component("comp1").physics("spf").feature("out1").selection().set(2);
            try { model.component("comp1").physics("spf").feature("out1").set("p0", "0"); p("SPF outlet OK"); }
            catch (Throwable t) { p("SPF outlet FAILED: " + t.getMessage()); }

            // ---------------- tds + cell-layer overrides ------------------------
            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            PhysicsFeature cdm1 = model.component("comp1").physics("tds").feature("cdm1");
            try { cdm1.set("u_src", "userdef");
                  cdm1.set("u", new String[]{"root.comp1.u", "root.comp1.v", "root.comp1.w"});
                  p("cdm1 userdef u OK -> " + Arrays.toString(cdm1.getStringArray("u"))); }
            catch (Throwable t) { p("cdm1 userdef u FAILED: " + t.getMessage()); }
            try { cdm1.set("D_c_mat", "userdef"); cdm1.set("D_c", "D_sp");
                  p("cdm1 D OK -> " + cdm1.getString("D_c")); }
            catch (Throwable t) { p("cdm1 D FAILED: " + t.getMessage()); }

            // (1) TDS "Fluid" domain feature
            p("=========== TDS FLUID DOMAIN ===========");
            try {
                model.component("comp1").physics("tds").create("fluid2", "Fluid", 3);
                p("Fluid domain feature created");
                PhysicsFeature fl = model.component("comp1").physics("tds").feature("fluid2");
                props("fluid2", fl);
                allowed("fluid2", fl, "u_src");
                allowed("fluid2", fl, "D_c_mat");
                try { fl.selection().set(cell); p("fluid2 selection OK"); }
                catch (Throwable t) { p("fluid2 selection FAILED: " + t.getMessage()); }
                try { fl.set("u_src", "userdef"); p("fluid2 u_src=userdef OK"); }
                catch (Throwable t) { p("fluid2 u_src FAILED: " + t.getMessage()); }
                try { fl.set("u", new String[]{"0", "0", "0"}); p("fluid2 u=0 OK"); }
                catch (Throwable t) { p("fluid2 u FAILED: " + t.getMessage()); }
                try { fl.set("D_c_mat", "userdef"); fl.set("D_c", "0.7*D_sp");
                      p("fluid2 D OK -> " + fl.getString("D_c")); }
                catch (Throwable t) { p("fluid2 D FAILED: " + t.getMessage()); }
            } catch (Throwable t) { p("Fluid domain FAILED: " + t.getMessage()); }

            // (2) TDS "Reactions" domain feature
            p("=========== TDS REACTIONS ===========");
            try {
                model.component("comp1").physics("tds").create("reac1", "Reactions", 3);
                p("Reactions feature created");
                PhysicsFeature rc = model.component("comp1").physics("tds").feature("reac1");
                props("reac1", rc);
                allowed("reac1", rc, "R_c_src");
                try { rc.selection().set(cell); p("reac1 selection OK"); }
                catch (Throwable t) { p("reac1 selection FAILED: " + t.getMessage()); }
                try { rc.set("R_c_src", "userdef"); p("reac1 R_c_src=userdef OK"); }
                catch (Throwable t) { p("reac1 R_c_src FAILED: " + t.getMessage()); }
                try { rc.set("R_c", "-k_r*c"); p("reac1 R_c OK -> " + rc.getString("R_c")); }
                catch (Throwable t) { p("reac1 R_c FAILED: " + t.getMessage()); }
            } catch (Throwable t) { p("Reactions FAILED: " + t.getMessage()); }

            // (3) TDS "init" domain feature
            p("=========== TDS INIT ===========");
            try {
                model.component("comp1").physics("tds").create("init2", "init", 3);
                p("init feature created");
                PhysicsFeature in = model.component("comp1").physics("tds").feature("init2");
                props("init2", in);
                try { in.selection().set(cell); p("init2 selection OK"); }
                catch (Throwable t) { p("init2 selection FAILED: " + t.getMessage()); }
                try { in.set("initc", "1"); p("init2 initc OK -> " + in.getString("initc")); }
                catch (Throwable t) { p("init2 initc FAILED: " + t.getMessage()); }
            } catch (Throwable t) { p("init FAILED: " + t.getMessage()); }

            // inflow / outflow
            model.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
            model.component("comp1").physics("tds").feature("inflow1").selection().set(1);
            try { model.component("comp1").physics("tds").feature("inflow1").set("c0", "1");
                  p("inflow c0 OK"); }
            catch (Throwable t) { p("inflow FAILED: " + t.getMessage()); }
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
            model.component("comp1").physics("tds").feature("outflow1").selection().set(2);

            // ---------------- probe nodes BEFORE a transient solve -------------
            p("=========== PROBES IN A TRANSIENT ===========");
            try {
                model.probe().create("domAvg", "Domain");
                model.probe("domAvg").model("comp1");
                model.probe("domAvg").set("expr", "c");
                model.probe("domAvg").set("type", "average");
                model.probe("domAvg").selection().set(cell);
                p("domAvg probe OK");
            } catch (Throwable t) { p("domAvg probe FAILED: " + t.getMessage()); }
            try {
                model.probe().create("pP1", "DomainPoint");
                model.probe("pP1").model("comp1");
                model.probe("pP1").set("coords", new double[][]{{0.0, 0.0, -0.0001}});
                model.probe("pP1").create("pe", "PointExpr");
                model.probe("pP1").feature("pe").set("expr", "c");
                p("pP1 probe OK (no descr, no dimension)");
            } catch (Throwable t) { p("pP1 probe FAILED: " + t.getMessage()); }
            p("probe tags = " + Arrays.toString(model.probe().tags()));

            // ---------------- studies -------------------------------------------
            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
            model.study("std1").feature("stat").setSolveFor("/physics/tds", false);
            model.study("std1").createAutoSequences("all");
            model.study("std1").run();
            p("std1 done; solvers=" + Arrays.toString(model.sol().tags()));

            model.study().create("std2");
            model.study("std2").create("time", "Transient");
            model.study("std2").feature("time").set("tlist", "range(0,10,100)");
            try {
                model.study("std2").feature("time").set("notsolmethod", "sol");
                model.study("std2").feature("time").set("notstudy", "std1");
                model.study("std2").feature("time").set("notstudystep", "stat");
                model.study("std2").feature("time").set("notsolnum", "auto");
                p("std2 coupling OK");
            } catch (Throwable t) { p("std2 coupling FAILED: " + t.getMessage()); }
            model.study("std2").feature("time").setSolveFor("/physics/spf", false);
            model.study("std2").feature("time").setSolveFor("/physics/tds", true);
            model.study("std2").createAutoSequences("all");
            try {
                model.study("std2").run();
                p("std2 done WITH probes present");
            } catch (Throwable t) {
                p("std2 FAILED with probes present: " + t.getMessage());
            }
            p("solvers after std2 = " + Arrays.toString(model.sol().tags()));
            p("datasets = " + Arrays.toString(model.result().dataset().tags()));
            p("tables = " + Arrays.toString(model.result().table().tags()));

            // ---------------- read the probe tables -----------------------------
            try {
                String[] tabs = model.result().table().tags();
                for (int i = 0; i < tabs.length; i++) {
                    double[][] d = model.result().table(tabs[i]).getReal();
                    p("TABLE " + tabs[i] + " rows=" + d.length + " cols=" + (d.length > 0 ? d[0].length : 0));
                    if (d.length > 0) {
                        for (int r = 0; r < d.length; r++) p("   row" + r + ": " + Arrays.toString(d[r]));
                    }
                }
            } catch (Throwable t) { p("table read FAILED: " + t.getMessage()); }

            // ---------------- parametric stationary sweep -----------------------
            p("=========== PARAMETRIC SWEEP ===========");
            try {
                model.study().create("std3");
                model.study("std3").create("param", "Parametric");
                model.study("std3").feature("param").set("pname", new String[]{"Q_in"});
                model.study("std3").feature("param").set("plistarr", new String[]{"10 100"});
                model.study("std3").feature("param").set("punit", new String[]{"ul/min"});
                model.study("std3").feature("param").set("sweeptype", "sparse");
                model.study("std3").create("stat", "Stationary");
                model.study("std3").feature("stat").setSolveFor("/physics/spf", true);
                model.study("std3").feature("stat").setSolveFor("/physics/tds", false);
                model.study("std3").create("stat2", "Stationary");
                try {
                    model.study("std3").feature("stat2").set("notsolmethod", "sol");
                    model.study("std3").feature("stat2").set("notstudy", "std3");
                    model.study("std3").feature("stat2").set("notstudystep", "stat");
                    model.study("std3").feature("stat2").set("notsolnum", "auto");
                    p("std3 stat2 coupling OK");
                } catch (Throwable t) { p("std3 stat2 coupling FAILED: " + t.getMessage()); }
                model.study("std3").feature("stat2").setSolveFor("/physics/spf", false);
                model.study("std3").feature("stat2").setSolveFor("/physics/tds", true);
                model.study("std3").createAutoSequences("all");
                model.study("std3").run();
                p("std3 done");
            } catch (Throwable t) {
                p("std3 FAILED: " + t.getMessage());
                t.printStackTrace();
            }
            p("solvers after std3 = " + Arrays.toString(model.sol().tags()));
            p("datasets after std3 = " + Arrays.toString(model.result().dataset().tags()));
            for (String s : model.sol().tags()) {
                try { p("  sol " + s + " pvals=" + Arrays.toString(model.sol(s).getPVals())); }
                catch (Throwable t) { p("  sol " + s + " pvals FAILED: " + t.getMessage()); }
            }
            // evaluate the cell-domain average on every solution dataset
            for (String ds : model.result().dataset().tags()) {
                try {
                    model.result().numerical().create("avX", "AvVolume");
                    model.result().numerical("avX").selection().set(cell);
                    model.result().numerical("avX").setIndex("expr", "c", 0);
                    model.result().numerical("avX").set("data", ds);
                    model.result().numerical("avX").run();
                    double[][] d = model.result().numerical("avX").getReal();
                    p("AVCEL " + ds + " rows=" + d.length + " cols=" + (d.length > 0 ? d[0].length : 0)
                      + " vals=" + (d.length > 0 ? Arrays.toString(d[0]) : "none"));
                    model.result().numerical().remove("avX");
                } catch (Throwable t) {
                    p("AVCEL " + ds + " FAILED: " + t.getMessage());
                    try { model.result().numerical().remove("avX"); } catch (Throwable t2) { }
                }
            }
            p("APIPROBE2 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("APIPROBE2 FAILED: " + e);
        }
        p("APIPROBE2 EXIT");
    }
}
