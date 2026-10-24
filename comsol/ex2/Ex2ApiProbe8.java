/* Ex2ApiProbe8.java -- confirm the canonical study coupling.
 *
 * VERIFIED FACTS SO FAR
 *   - Q3 (probe6): a Parametric step + ONE Stationary step solving spf AND
 *     tds gives sol1.pvals = [1.6667e-10, 1.6667e-9, 1.6667e-8] m^3/s
 *     (= 10, 100, 1000 uL/min) and an AvVolume with 3 columns, one per
 *     flow rate, in sweep order.  plistarr MUST be comma separated.
 *   - V3 (probe7): a Transient step solving spf AND tds convects correctly
 *     (c at the downstream end goes 5e-63 -> 0.014 -> 0.918).
 *   - V1 (probe7): two studies with notsolmethod=sol but WITHOUT usesol=true
 *     gives spf.U = 0 inside the transient -> pure diffusion.  THE BUG.
 *
 * This probe tests the canonical shipped pattern:
 *   usesol=true  +  notsolmethod=sol  +  notstudy=std1  [+ notstudystep]
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2ApiProbe8 {

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

    static void evalAt(String dset, String label) {
        try {
            model.result().dataset().create("cptY", "CutPoint3D");
            model.result().dataset("cptY").set("pointx", "19");
            model.result().dataset("cptY").set("pointy", "0");
            model.result().dataset("cptY").set("pointz", "0.5");
            model.result().dataset("cptY").set("data", dset);
            model.result().numerical().create("pevY", "EvalPoint");
            model.result().numerical("pevY").setIndex("expr", "c", 0);
            model.result().numerical("pevY").setIndex("expr", "spf.U", 1);
            model.result().numerical("pevY").set("data", "cptY");
            model.result().numerical("pevY").run();
            double[][] d = model.result().numerical("pevY").getReal();
            p("  " + label + " c(x=19mm)     = " + (d.length > 0 ? Arrays.toString(d[0]) : "none"));
            p("  " + label + " spf.U(x=19mm) = " + (d.length > 1 ? Arrays.toString(d[1]) : "none"));
        } catch (Throwable t) {
            p("  " + label + " eval FAILED: " + t.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            // ===== V5: two studies, usesol=true =====================
            p("############ V5: two studies + usesol=true ############");
            buildChannel("V5");
            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
            model.study("std1").feature("stat").setSolveFor("/physics/tds", false);
            model.study("std1").createAutoSequences("all");
            model.study("std1").run();
            model.study().create("std2");
            model.study("std2").create("time", "Transient");
            model.study("std2").feature("time").set("tlist", "range(0,30,60)");
            try {
                model.study("std2").feature("time").set("usesol", true);
                model.study("std2").feature("time").set("notsolmethod", "sol");
                model.study("std2").feature("time").set("notstudy", "std1");
                model.study("std2").feature("time").set("notsolnum", "auto");
                p("  V5 usesol/notsolmethod/notstudy set OK");
            } catch (Throwable t) { p("  V5 coupling set FAILED: " + t.getMessage()); }
            model.study("std2").feature("time").setSolveFor("/physics/spf", false);
            model.study("std2").feature("time").setSolveFor("/physics/tds", true);
            model.study("std2").createAutoSequences("all");
            try { model.study("std2").run(); p("  V5 std2 done"); }
            catch (Throwable t) { p("  V5 std2 FAILED: " + t.getMessage()); }
            evalAt("dset2", "V5");
            model = null;

            // ===== V6: one study, two steps, usesol=true ==============
            p("############ V6: one study two steps + usesol=true ############");
            buildChannel("V6");
            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
            model.study("std1").feature("stat").setSolveFor("/physics/tds", false);
            model.study("std1").create("time", "Transient");
            model.study("std1").feature("time").set("tlist", "range(0,30,60)");
            try {
                model.study("std1").feature("time").set("usesol", true);
                model.study("std1").feature("time").set("notsolmethod", "sol");
                model.study("std1").feature("time").set("notstudy", "std1");
                model.study("std1").feature("time").set("notstudystep", "stat");
                model.study("std1").feature("time").set("notsolnum", "auto");
                p("  V6 coupling set OK");
            } catch (Throwable t) { p("  V6 coupling set FAILED: " + t.getMessage()); }
            model.study("std1").feature("time").setSolveFor("/physics/spf", false);
            model.study("std1").feature("time").setSolveFor("/physics/tds", true);
            model.study("std1").createAutoSequences("all");
            try { model.study("std1").run(); p("  V6 std1 done"); }
            catch (Throwable t) { p("  V6 std1 FAILED: " + t.getMessage()); }
            p("  V6 solvers=" + Arrays.toString(model.sol().tags())
              + " datasets=" + Arrays.toString(model.result().dataset().tags()));
            evalAt("dset2", "V6");
            model = null;

            p("APIPROBE8 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("APIPROBE8 FAILED: " + e);
        }
        p("APIPROBE8 EXIT");
    }
}
