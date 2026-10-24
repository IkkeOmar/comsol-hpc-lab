/* Probe13: final API consolidation before writing Ex1_HFilter.java.
 *
 *  (1) spf MassFlow inlet: what does mfr mean -> measure the real Q
 *  (2) material with rho/mu (probe12 failed: 'rho' undefined)
 *  (3) tds coupling: u_src = root.comp1.u  (GUI: Velocity field (spf))
 *  (4) TDS Inflow vs Concentration inlet, and how well the outlet profile
 *      matches the analytical Eq.(1) at t = t_res = 1.8 s
 *  (5) CutPoint2D with a VALID identifier tag; EvalPoint at a corner
 *  (6) LineGraph x-axis control on a vertical CutLine2D
 *  (7) Analytic function cSeries(y) + EvalGlobal
 *  (8) Data export of a cut line to a text file
 *  (9) comp2: transient pure transverse diffusion (Cauchy problem) -> time
 *      series of c at the far wall, and profile at t = t1
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class Probe13 {

    static void p(String s) { System.out.println(s); }

    static void dumpProfile(Model model, String label) {
        model.result().numerical().create("intP_" + label, "IntLine");
        model.result().numerical("intP_" + label).set("data", "clnOut");
        model.result().numerical("intP_" + label).setIndex("expr", "c", 0);
        model.result().numerical("intP_" + label).run();
        p(label + " int c dl = " + model.result().numerical("intP_" + label).getReal()[0][0]);
    }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe13");
            model.component().create("comp1");
            model.param().set("c0", "1[mol/m^3]");
            model.param().set("w_ch", "100[um]");
            model.param().set("L_ch", "6000[um]");
            model.param().set("h_ch", "5[um]");
            model.param().set("D_sp", "2e-10[m^2/s]");
            model.param().set("mu", "1e-3[Pa*s]");
            model.param().set("rho", "1000[kg/m^3]");
            model.param().set("Q_in", "1.666667e-12[m^3/s]");
            model.param().set("t_res", "L_ch*w_ch*h_ch/Q_in");
            model.param().set("t_probe", "1.8[s]");
            p("t_res = " + model.param().evaluate("t_res"));

            GeomSequence g = model.component("comp1").geom().create("geom1", 2);
            g.lengthUnit("um");
            g.create("r1", "Rectangle");
            g.feature("r1").set("size", new String[]{"L_ch", "w_ch"});
            g.feature("r1").set("pos", new String[]{"0", "-w_ch/2"});
            g.create("ln1", "LineSegment");
            g.feature("ln1").set("specify1", "coord");
            g.feature("ln1").set("specify2", "coord");
            g.feature("ln1").set("coord1", new String[]{"0", "0"});
            g.feature("ln1").set("coord2", new String[]{"L_ch", "0"});
            g.run();
            g.create("mce1", "MeshControlEdges");
            g.feature("mce1").selection("input").set("ln1", 1);
            g.run("mce1");

            g.create("selIn", "BoxSelection");
            g.feature("selIn").set("entitydim", 1);
            g.feature("selIn").set("condition", "allvertices");
            g.feature("selIn").set("xmin", "-1[um]"); g.feature("selIn").set("xmax", "1[um]");
            g.feature("selIn").set("ymin", "-w_ch/2-1[um]"); g.feature("selIn").set("ymax", "w_ch/2+1[um]");
            g.create("selOut", "BoxSelection");
            g.feature("selOut").set("entitydim", 1);
            g.feature("selOut").set("condition", "allvertices");
            g.feature("selOut").set("xmin", "L_ch-1[um]"); g.feature("selOut").set("xmax", "L_ch+1[um]");
            g.feature("selOut").set("ymin", "-w_ch/2-1[um]"); g.feature("selOut").set("ymax", "w_ch/2+1[um]");
            g.create("selBot", "BoxSelection");
            g.feature("selBot").set("entitydim", 1);
            g.feature("selBot").set("condition", "allvertices");
            g.feature("selBot").set("xmin", "-1[um]"); g.feature("selBot").set("xmax", "L_ch+1[um]");
            g.feature("selBot").set("ymin", "-w_ch/2-1[um]"); g.feature("selBot").set("ymax", "-w_ch/2+1[um]");
            g.run();

            int[] inE = model.selection("geom1_selIn").entities(1);
            int[] outE = model.selection("geom1_selOut").entities(1);
            int[] botE = model.selection("geom1_selBot").entities(1);
            p("IN=" + Arrays.toString(inE) + " OUT=" + Arrays.toString(outE)
              + " BOT=" + Arrays.toString(botE));

            /* material: water */
            model.component("comp1").material().create("mat1", "Common");
            model.component("comp1").material("mat1").label("Water");
            model.component("comp1").material("mat1").propertyGroup("def")
                 .set("dynamicviscosity", "mu")
                 .set("density", "rho");

            /* mesh */
            MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");
            mesh.create("size1", "Size");
            mesh.feature("size1").set("hmax", "20[um]");
            mesh.feature("size1").set("hmin", "0.5[um]");
            mesh.feature("size1").set("hgrad", 1.2);
            mesh.create("distIn", "Distribution");
            mesh.feature("distIn").selection().geom(1).set(inE);
            mesh.feature("distIn").set("numelem", "100");
            mesh.create("distBot", "Distribution");
            mesh.feature("distBot").selection().geom(1).set(botE);
            mesh.feature("distBot").set("numelem", "200");
            mesh.create("map1", "Map");
            mesh.run();
            p("MESH numElem=" + mesh.stat().getNumElem() + " minQ=" + mesh.stat().getMinQuality());

            /* spf: shallow channel + MassFlow inlet */
            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            model.component("comp1").physics("spf").prop("PhysicalModelProperty")
                 .set("ShallowChannelProperty", "1");
            model.component("comp1").physics("spf").prop("PhysicalModelProperty")
                 .set("dz", "h_ch");
            model.component("comp1").physics("spf").create("inl1", "Inlet", 1);
            model.component("comp1").physics("spf").feature("inl1").selection().set(inE);
            model.component("comp1").physics("spf").feature("inl1")
                 .set("BoundaryCondition", "MassFlow");
            model.component("comp1").physics("spf").feature("inl1")
                 .set("MassFlowType", "MassFlowRate");
            model.component("comp1").physics("spf").feature("inl1")
                 .set("mfr", "rho*Q_in");
            model.component("comp1").physics("spf").create("out1", "Outlet", 1);
            model.component("comp1").physics("spf").feature("out1").selection().set(outE);
            model.component("comp1").physics("spf").feature("out1").set("p0", "0");

            /* tds */
            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            try {
                model.component("comp1").physics("tds").feature("cdm1").set("u_src", "root.comp1.u");
                p("SET tds u_src=root.comp1.u OK");
            } catch (Throwable e) { p("SET u_src root.comp1.u FAIL: " + e.getMessage()); }
            model.component("comp1").physics("tds").feature("cdm1").set("D_c", "D_sp");

            model.func().create("step1", "Step");
            model.func("step1").set("funcname", "step1");
            model.func("step1").set("location", "0");
            model.func("step1").set("from", 1);
            model.func("step1").set("to", 0);
            model.func("step1").set("smooth", "0");
            p("step1 props: " + Arrays.toString(model.func("step1").properties()));

            model.component("comp1").physics("tds").create("inflow1", "Concentration", 1);
            model.component("comp1").physics("tds").feature("inflow1").selection().set(inE);
            model.component("comp1").physics("tds").feature("inflow1").set("c0", "c0*step1(y[1/mm])");
            p("tds inlet props: " + Arrays.toString(
                model.component("comp1").physics("tds").feature("inflow1").properties()));
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 1);
            model.component("comp1").physics("tds").feature("outflow1").selection().set(outE);

            /* analytic Eq.(1) */
            model.func().create("cSeries", "Analytic");
            model.func("cSeries").set("funcname", "cSeries");
            model.func("cSeries").set("args", "y");
            model.func("cSeries").set("expr",
                "c0*(1/2-sum(2/((2*n-1)*pi)*sin((2*n-1)*pi*y/w_ch)"
              + "*exp(-((2*n-1)*pi/w_ch)^2*D_sp*t_probe),n,1,100))");
            p("cSeries props: " + Arrays.toString(model.func("cSeries").properties()));

            /* study */
            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            model.study("std1").createAutoSequences("all");
            try {
                model.study("std1").run();
                p("=== STATIONARY SOLVE OK ===");
            } catch (Throwable e) {
                p("SOLVE FAIL: " + e.getMessage());
                e.printStackTrace();
            }

            /* measure Q and dp */
            model.result().numerical().create("intIn", "IntLine");
            model.result().numerical("intIn").selection().set(inE);
            model.result().numerical("intIn").setIndex("expr", "u", 0);
            model.result().numerical("intIn").setIndex("expr", "1", 1);
            model.result().numerical("intIn").run();
            double[][] dIn = model.result().numerical("intIn").getReal();
            p("int u dl = " + dIn[0][0] + " (m/s*um)  -> Q = " + (5e-6 * dIn[0][0] * 1e-6)
              + " m^3/s   target 1.666667e-12");
            model.result().numerical().create("avPIn", "AvLine");
            model.result().numerical("avPIn").selection().set(inE);
            model.result().numerical("avPIn").setIndex("expr", "p", 0);
            model.result().numerical("avPIn").run();
            model.result().numerical().create("avPOut", "AvLine");
            model.result().numerical("avPOut").selection().set(outE);
            model.result().numerical("avPOut").setIndex("expr", "p", 0);
            model.result().numerical("avPOut").run();
            p("p_in = " + model.result().numerical("avPIn").getReal()[0][0]
              + "  p_out = " + model.result().numerical("avPOut").getReal()[0][0]
              + "  dp = " + (model.result().numerical("avPIn").getReal()[0][0]
                          - model.result().numerical("avPOut").getReal()[0][0]));

            /* CutPoint2D with valid identifiers */
            double[] ys = {-50, -45, -22, 0, 22, 45, 50};
            String[] ytags = {"yA", "yB", "yC", "yD", "yE", "yF", "yG"};
            for (int i = 0; i < ys.length; i++) {
                model.result().dataset().create("cpt" + ytags[i], "CutPoint2D");
                model.result().dataset("cpt" + ytags[i]).set("pointx", "L_ch");
                model.result().dataset("cpt" + ytags[i]).set("pointy", "" + ys[i]);
                model.result().numerical().create("pt" + ytags[i], "EvalPoint");
                model.result().numerical("pt" + ytags[i]).set("data", "cpt" + ytags[i]);
                model.result().numerical("pt" + ytags[i]).setIndex("expr", "c", 0);
                model.result().numerical("pt" + ytags[i]).run();
                p("c at (L, " + ys[i] + ") = "
                  + model.result().numerical("pt" + ytags[i]).getReal()[0][0]);
            }

            /* transverse cut line at the outlet */
            model.result().dataset().create("clnOut", "CutLine2D");
            model.result().dataset("clnOut").setIndex("genpoints", "L_ch", 0, 0);
            model.result().dataset("clnOut").setIndex("genpoints", "-w_ch/2", 0, 1);
            model.result().dataset("clnOut").setIndex("genpoints", "L_ch", 1, 0);
            model.result().dataset("clnOut").setIndex("genpoints", "w_ch/2", 1, 1);
            model.result().numerical().create("intCln", "IntLine");
            model.result().numerical("intCln").set("data", "clnOut");
            model.result().numerical("intCln").setIndex("expr", "1", 0);
            model.result().numerical("intCln").run();
            p("cut line length = " + model.result().numerical("intCln").getReal()[0][0] + " um");

            /* LineGraph axis control */
            model.result().create("pg1", "PlotGroup1D");
            model.result("pg1").create("lngr1", "LineGraph");
            model.result("pg1").feature("lngr1").set("data", "clnOut");
            model.result("pg1").feature("lngr1").set("expr", "c");
            for (String prop : new String[]{"xdata", "xdataexpr", "xdataunit"}) {
                try {
                    p("LineGraph allowed " + prop + ": " + Arrays.toString(
                        model.result("pg1").feature("lngr1").getAllowedPropertyValues(prop)));
                } catch (Throwable e) { p("LineGraph " + prop + " FAIL: " + e.getMessage()); }
            }
            try {
                model.result("pg1").feature("lngr1").set("xdata", "expr");
                model.result("pg1").feature("lngr1").set("xdataexpr", "y");
                p("SET xdata=expr, xdataexpr=y OK");
            } catch (Throwable e) { p("SET xdata expr FAIL: " + e.getMessage()); }
            try {
                model.result("pg1").create("lngr2", "LineGraph");
                model.result("pg1").feature("lngr2").set("data", "clnOut");
                model.result("pg1").feature("lngr2").set("expr", "cSeries(y)");
                p("second graph with analytic series created");
                model.result("pg1").run();
                p("PLOT OK");
            } catch (Throwable e) { p("PLOT FAIL: " + e.getMessage()); }

            /* EvalGlobal on the analytic function */
            try {
                model.result().numerical().create("gev", "EvalGlobal");
                model.result().numerical("gev").setIndex("expr", "cSeries(50[um])", 0);
                model.result().numerical("gev").run();
                p("cSeries(+50um) = " + model.result().numerical("gev").getReal()[0][0]
                  + "   (expect ~0.0624 at t=1.8 s)");
                model.result().numerical("gev").setIndex("expr", "cSeries(0[um])", 0);
                model.result().numerical("gev").run();
                p("cSeries(0) = " + model.result().numerical("gev").getReal()[0][0]);
            } catch (Throwable e) { p("EvalGlobal FAIL: " + e.getMessage()); }

            /* Data export of the cut line */
            try {
                model.result().export().create("dprof", "Data");
                model.result().export("dprof").set("data", "clnOut");
                model.result().export("dprof").set("expr", "c");
                model.result().export("dprof").set("filename",
                    "<project>/comsol/probe13_profile.txt");
                model.result().export("dprof").set("location", "file");
                model.result().export("dprof").run();
                p("DATA EXPORT OK");
            } catch (Throwable e) { p("DATA EXPORT FAIL: " + e.getMessage()); }

            /* ---- comp2: transient pure transverse diffusion ---- */
            model.component().create("comp2");
            model.component("comp2").geom().create("geom2", 2);
            model.component("comp2").geom("geom2").lengthUnit("um");
            model.component("comp2").geom("geom2").create("r1", "Rectangle");
            model.component("comp2").geom("geom2").feature("r1")
                 .set("size", new String[]{"100", "100"});
            model.component("comp2").geom("geom2").feature("r1")
                 .set("pos", new String[]{"0", "-50"});
            model.component("comp2").geom("geom2").run();
            model.component("comp2").material().create("mat1", "Common");
            model.component("comp2").material("mat1").propertyGroup("def")
                 .set("dynamicviscosity", "mu")
                 .set("density", "rho")
                 .set("diffusioncoefficient", new String[]{"D_sp"});
            model.component("comp2").physics().create("tds", "DilutedSpecies", "geom2");
            model.component("comp2").physics("tds").feature("cdm1").set("D_c", "D_sp");
            model.component("comp2").physics("tds").feature("init1").set("initc", "c0*step1(y[1/mm])");
            MeshSequence m2 = model.component("comp2").mesh().create("mesh1", "geom2");
            m2.create("size1", "Size");
            m2.feature("size1").set("hmax", "0.5[um]");
            m2.feature("size1").set("hmin", "0.1[um]");
            m2.run();
            p("comp2 mesh numElem=" + m2.stat().getNumElem());
            model.study().create("std2");
            model.study("std2").create("td", "Transient");
            model.study("std2").feature("td").set("tlist", "range(0,0.25,6) 5.8656");
            model.study("std2").feature("td").setSolveFor("/physics/tds", true);
            model.study("std2").createAutoSequences("all");
            try {
                model.study("std2").run();
                p("=== TRANSIENT SOLVE OK ===");
            } catch (Throwable e) {
                p("TRANSIENT SOLVE FAIL: " + e.getMessage());
                e.printStackTrace();
            }
            try {
                model.result().dataset().create("cptWall2", "CutPoint2D");
                model.result().dataset("cptWall2").set("pointx", "50");
                model.result().dataset("cptWall2").set("pointy", "50");
                model.result().numerical().create("ptWall2", "EvalPoint");
                model.result().numerical("ptWall2").set("data", "cptWall2");
                model.result().numerical("ptWall2").setIndex("expr", "c", 0);
                model.result().numerical("ptWall2").run();
                double[][] wall = model.result().numerical("ptWall2").getReal();
                p("far-wall time series: npts=" + wall.length + " ntimes=" + wall[0].length);
                for (int i = 0; i < wall[0].length; i++) {
                    p("  c_farwall[" + i + "] = " + wall[0][i]);
                }
                try {
                    model.result().numerical("ptWall2").set("t", "5.8656");
                    model.result().numerical("ptWall2").run();
                    p("c_farwall at t=5.8656 via 't' property = "
                      + model.result().numerical("ptWall2").getReal()[0][0]);
                } catch (Throwable e) { p("set t FAIL: " + e.getMessage()); }
            } catch (Throwable e) { p("TRANSIENT EVAL FAIL: " + e.getMessage()); }

            p("PROBE13 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("PROBE13 ABORTED: " + e);
        }
    }
}
