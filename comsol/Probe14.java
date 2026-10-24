/* Probe14 (fast): the last three unknowns, each isolated.
 *  (A) MassFlow inlet semantics: is "mfr" the TOTAL mass flow of the
 *      shallow channel or per unit depth?  -> solve and measure Q.
 *  (B) TDS "Inflow" node (assignment wording) vs "Concentration":
 *      does it solve, and what is its default BoundaryConditionType?
 *  (C) Data export of a CutLine2D -> text file (format for the comparison
 *      script), and the LineGraph x-axis control.
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class Probe14 {

    static void p(String s) { System.out.println(s); }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe14");
            model.component().create("comp1");
            model.param().set("c0", "1[mol/m^3]");
            model.param().set("w_ch", "100[um]");
            model.param().set("L_ch", "6000[um]");
            model.param().set("D_sp", "2e-10[m^2/s]");
            model.param().set("Q_in", "1.666667e-12[m^3/s]");
            model.param().set("t_res", "L_ch*w_ch*h_ch/Q_in");
            model.param().set("h_ch", "5[um]");

            GeomSequence g = model.component("comp1").geom().create("geom1", 2);
            g.lengthUnit("um");
            g.create("r1", "Rectangle");
            g.feature("r1").set("size", new String[]{"L_ch", "w_ch"});
            g.feature("r1").set("pos", new String[]{"0", "-w_ch/2"});
            g.run();
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
            g.run();
            int[] inE = model.selection("geom1_selIn").entities(1);
            int[] outE = model.selection("geom1_selOut").entities(1);
            p("IN=" + Arrays.toString(inE) + " OUT=" + Arrays.toString(outE));

            model.component("comp1").material().create("mat1", "Common");
            model.component("comp1").material("mat1").label("Water");
            model.component("comp1").material("mat1").propertyGroup("def")
                 .set("dynamicviscosity", "1e-3[Pa*s]")
                 .set("density", "1000[kg/m^3]");

            MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");
            mesh.create("size1", "Size");
            mesh.feature("size1").set("hmax", "50[um]");
            mesh.feature("size1").set("hmin", "2[um]");
            mesh.run();
            p("MESH numElem=" + mesh.stat().getNumElem());

            /* ---- spf ---- */
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
                 .set("mfr", "1000[kg/m^3]*Q_in");     /* candidate: TOTAL flow */
            model.component("comp1").physics("spf").create("out1", "Outlet", 1);
            model.component("comp1").physics("spf").feature("out1").selection().set(outE);
            model.component("comp1").physics("spf").feature("out1").set("p0", "0");

            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            model.study("std1").createAutoSequences("all");
            model.study("std1").run();
            p("=== FLOW SOLVE OK (mfr = rho*Q_in) ===");

            model.result().numerical().create("intQ", "IntLine");
            model.result().numerical("intQ").selection().set(inE);
            model.result().numerical("intQ").setIndex("expr", "u", 0);
            model.result().numerical("intQ").setIndex("expr", "1", 1);
            model.result().numerical("intQ").run();
            double[][] dQ = model.result().numerical("intQ").getReal();
            double intU = dQ[0][0];
            p("int u dl = " + intU + "  (m/s * um)");
            p("Q_total = dz * int(u dl) = " + (5e-6 * intU * 1e-6) + " m^3/s"
              + "   target = " + model.param().evaluate("Q_in"));
            p("=> ratio Q_meas/Q_target = "
              + (5e-6 * intU * 1e-6 / (double) model.param().evaluate("Q_in")));
            p("(if ratio == 1, mfr is the TOTAL channel mass flow;"
              + " if ratio == 5e-6, mfr is per unit depth)");
            model.result().numerical().create("avPIn", "AvLine");
            model.result().numerical("avPIn").selection().set(inE);
            model.result().numerical("avPIn").setIndex("expr", "p", 0);
            model.result().numerical("avPIn").run();
            model.result().numerical().create("avPOut", "AvLine");
            model.result().numerical("avPOut").selection().set(outE);
            model.result().numerical("avPOut").setIndex("expr", "p", 0);
            model.result().numerical("avPOut").run();
            p("dp = " + (model.result().numerical("avPIn").getReal()[0][0]
                       - model.result().numerical("avPOut").getReal()[0][0]) + " Pa");

            /* ---- TDS: Inflow node (assignment wording) ---- */
            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            model.component("comp1").physics("tds").feature("cdm1").set("D_c", "D_sp");
            model.component("comp1").physics("tds").feature("cdm1")
                 .set("u_src", "root.comp1.u");
            model.func().create("step1", "Step");
            model.func("step1").set("funcname", "step1");
            model.func("step1").set("location", "0");
            model.func("step1").set("from", 0);
            model.func("step1").set("to", 1);
            model.func("step1").set("smooth", "0[mm]");
            model.component("comp1").physics("tds").create("inflow1", "Inflow", 1);
            model.component("comp1").physics("tds").feature("inflow1").selection().set(inE);
            model.component("comp1").physics("tds").feature("inflow1")
                 .set("c0", "c0*step1(y[1/mm])");
            try {
                p("inflow1 default BoundaryConditionType = "
                  + model.component("comp1").physics("tds").feature("inflow1")
                       .getString("BoundaryConditionType"));
            } catch (Throwable e) { p("getString BCT FAIL: " + e.getMessage()); }
            p("inflow1 props: " + Arrays.toString(
                model.component("comp1").physics("tds").feature("inflow1").properties()));
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 1);
            model.component("comp1").physics("tds").feature("outflow1").selection().set(outE);
            /* walls keep the default No-Flux node (nflx1) */

            model.study("std1").feature("stat").setSolveFor("/physics/tds", true);
            model.study("std1").run();
            p("=== FLOW + TDS SOLVE OK (Inflow inlet) ===");

            double[] ys = {-50, -25, 0, 25, 50};
            String[] tg = {"yA", "yB", "yC", "yD", "yE"};
            for (int i = 0; i < ys.length; i++) {
                model.result().dataset().create("cpt" + tg[i], "CutPoint2D");
                model.result().dataset("cpt" + tg[i]).set("pointx", "L_ch");
                model.result().dataset("cpt" + tg[i]).set("pointy", "" + ys[i]);
                model.result().numerical().create("pt" + tg[i], "EvalPoint");
                model.result().numerical("pt" + tg[i]).set("data", "cpt" + tg[i]);
                model.result().numerical("pt" + tg[i]).setIndex("expr", "c", 0);
                model.result().numerical("pt" + tg[i]).run();
                p("c at outlet (L_ch, " + ys[i] + ") = "
                  + model.result().numerical("pt" + tg[i]).getReal()[0][0]);
            }

            /* ---- CutLine2D + Data export ---- */
            model.result().dataset().create("clnOut", "CutLine2D");
            model.result().dataset("clnOut").setIndex("genpoints", "L_ch", 0, 0);
            model.result().dataset("clnOut").setIndex("genpoints", "-w_ch/2", 0, 1);
            model.result().dataset("clnOut").setIndex("genpoints", "L_ch", 1, 0);
            model.result().dataset("clnOut").setIndex("genpoints", "w_ch/2", 1, 1);
            try {
                model.result().export().create("dprof", "Data");
                model.result().export("dprof").set("data", "clnOut");
                model.result().export("dprof").set("expr", "c");
                model.result().export("dprof").set("includecoords", true);
                model.result().export("dprof").set("filename",
                    "<project>/comsol/probe14_profile.txt");
                model.result().export("dprof").run();
                p("DATA EXPORT OK");
            } catch (Throwable e) { p("DATA EXPORT FAIL: " + e.getMessage()); }

            /* ---- LineGraph x-axis on the vertical cut line ---- */
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
                p("SET xdata=expr / xdataexpr=y OK");
                model.result("pg1").run();
                p("PLOT OK");
            } catch (Throwable e) { p("SET xdata FAIL: " + e.getMessage()); }

            p("PROBE14 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("PROBE14 ABORTED: " + e);
        }
    }
}
