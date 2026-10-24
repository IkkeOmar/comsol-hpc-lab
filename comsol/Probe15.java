/* Probe15: final consolidation, one solve.
 *
 *  (1) mesh: explicit mesher (Map + distributions) -> element count + quality
 *  (2) material: parameter names that do NOT collide with predefined
 *      material variables ("mu" is a predefined material symbol -> use mu_f)
 *  (3) spf MassFlow inlet: measure the achieved Q (dz * int u dl) to learn
 *      whether "mfr" is the total channel mass flow or per unit depth
 *  (4) TDS: Inflow node with c0*step1(y[1/mm]) (assignment p.3 wording),
 *      Outflow on the outlet, walls keep the default No-Flux
 *  (5) outlet probes: far wall (y=-w/2), near wall (y=+w/2),
 *      upper-half line average (AvLine on a CutLine2D) -> compare with the
 *      analytical series at t_res
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class Probe15 {

    static void p(String s) { System.out.println(s); }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe15");
            model.component().create("comp1");
            model.param().set("c0", "1[mol/m^3]");
            model.param().set("w_ch", "100[um]");
            model.param().set("L_ch", "6000[um]");
            model.param().set("h_ch", "5[um]");
            model.param().set("D_sp", "2e-10[m^2/s]");
            model.param().set("Q_in", "1.666667e-12[m^3/s]");
            model.param().set("mu_f", "1e-3[Pa*s]");
            model.param().set("rho_f", "1000[kg/m^3]");

            GeomSequence g = model.component("comp1").geom().create("geom1", 2);
            g.lengthUnit("um");
            g.create("r1", "Rectangle");
            g.feature("r1").set("size", new String[]{"L_ch", "w_ch"});
            g.feature("r1").set("pos", new String[]{"0", "-w_ch/2"});
            g.create("ln1", "LineSegment");
            g.feature("ln1").set("specify1", "coord");
            g.feature("ln1").set("specify2", "coord");
            g.feature("ln1").set("coord1", new String[]{"0[um]", "0[um]"});
            g.feature("ln1").set("coord2", new String[]{"L_ch", "0[um]"});
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
            g.create("selTop", "BoxSelection");
            g.feature("selTop").set("entitydim", 1);
            g.feature("selTop").set("condition", "allvertices");
            g.feature("selTop").set("xmin", "-1[um]"); g.feature("selTop").set("xmax", "L_ch+1[um]");
            g.feature("selTop").set("ymin", "w_ch/2-1[um]"); g.feature("selTop").set("ymax", "w_ch/2+1[um]");
            g.run();
            int[] inE  = model.selection("geom1_selIn").entities(1);
            int[] outE = model.selection("geom1_selOut").entities(1);
            int[] topE = model.selection("geom1_selTop").entities(1);
            p("IN=" + Arrays.toString(inE) + " OUT=" + Arrays.toString(outE)
              + " TOP=" + Arrays.toString(topE));

            model.component("comp1").material().create("mat1", "Common");
            model.component("comp1").material("mat1").label("Water");
            model.component("comp1").material("mat1").propertyGroup("def")
                 .set("dynamicviscosity", "mu_f")
                 .set("density", "rho_f");

            MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");
            mesh.create("size1", "Size");
            mesh.feature("size1").set("hmax", "20[um]");
            mesh.feature("size1").set("hmin", "0.5[um]");
            mesh.feature("size1").set("hgrad", 1.2);
            mesh.create("distIn", "Distribution");
            mesh.feature("distIn").selection().geom(1).set(inE);
            mesh.feature("distIn").set("numelem", "80");
            mesh.create("distTop", "Distribution");
            mesh.feature("distTop").selection().geom(1).set(topE);
            mesh.feature("distTop").set("numelem", "160");
            mesh.create("map1", "Map");
            mesh.run();
            p("MESH numElem=" + mesh.stat().getNumElem()
              + " minQ=" + mesh.stat().getMinQuality()
              + " meanQ=" + mesh.stat().getMeanQuality()
              + " types=" + Arrays.toString(mesh.stat().getTypes()));

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
                 .set("mfr", "rho_f*Q_in");     // candidate: TOTAL mass flow
            model.component("comp1").physics("spf").create("out1", "Outlet", 1);
            model.component("comp1").physics("spf").feature("out1").selection().set(outE);
            model.component("comp1").physics("spf").feature("out1").set("p0", "0");

            /* TDS */
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
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 1);
            model.component("comp1").physics("tds").feature("outflow1").selection().set(outE);
            try {
                p("inflow1 BoundaryConditionType = " + model.component("comp1").physics("tds")
                     .feature("inflow1").getString("BoundaryConditionType"));
            } catch (Throwable e) { p("BCT FAIL " + e.getMessage()); }

            /* analytical series in the COMSOL convention (sugar in the UPPER half):
             *   c(y,t) = c0/2 + sum_n 2c0/((2n-1)pi) sin((2n-1)pi y/w) exp(-...)
             */
            model.param().set("t_res", "L_ch*w_ch*h_ch/Q_in");
            model.func().create("cSeries", "Analytic");
            model.func("cSeries").set("funcname", "cSeries");
            model.func("cSeries").set("args", "y");
            model.func("cSeries").set("expr",
                "c0*(1/2+sum(2/((2*n-1)*pi)*sin((2*n-1)*pi*y/w_ch)"
              + "*exp(-((2*n-1)*pi/w_ch)^2*D_sp*t_res),n,1,200))");
            model.func("cSeries").set("argunit", "m");
            model.func("cSeries").set("fununit", "mol/m^3");

            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            model.study("std1").createAutoSequences("all");
            model.study("std1").run();
            p("=== SOLVE OK ===");

            model.result().numerical().create("intQ", "IntLine");
            model.result().numerical("intQ").selection().set(inE);
            model.result().numerical("intQ").setIndex("expr", "u", 0);
            model.result().numerical("intQ").run();
            double intU = model.result().numerical("intQ").getReal()[0][0];
            double Qmeas = 5e-6 * intU * 1e-6;
            p("int u dl = " + intU + " (m/s*um)");
            p("Q_measured = dz*int(u dl) = " + Qmeas + " m^3/s   target = "
              + model.param().evaluate("Q_in"));
            p("ratio Q_meas/Q_target = "
              + (Qmeas / (double) model.param().evaluate("Q_in"))
              + "   (1 => mfr is TOTAL mass flow; 5e-6 => per unit depth)");

            model.result().numerical().create("avPIn", "AvLine");
            model.result().numerical("avPIn").selection().set(inE);
            model.result().numerical("avPIn").setIndex("expr", "p", 0);
            model.result().numerical("avPIn").run();
            model.result().numerical().create("avPOut", "AvLine");
            model.result().numerical("avPOut").selection().set(outE);
            model.result().numerical("avPOut").setIndex("expr", "p", 0);
            model.result().numerical("avPOut").run();
            p("dp = " + (model.result().numerical("avPIn").getReal()[0][0]
                       - model.result().numerical("avPOut").getReal()[0][0]) + " Pa"
              + "   (Hagen-Poiseuille prediction 9600 Pa)");

            /* outlet wall concentrations */
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
                p("c at outlet (L, " + ys[i] + ") = "
                  + model.result().numerical("pt" + tg[i]).getReal()[0][0]);
            }

            /* upper-half line average on a CutLine2D dataset */
            model.result().dataset().create("clnUp", "CutLine2D");
            model.result().dataset("clnUp").setIndex("genpoints", "L_ch", 0, 0);
            model.result().dataset("clnUp").setIndex("genpoints", "0", 0, 1);
            model.result().dataset("clnUp").setIndex("genpoints", "L_ch", 1, 0);
            model.result().dataset("clnUp").setIndex("genpoints", "w_ch/2", 1, 1);
            model.result().numerical().create("avUp", "AvLine");
            model.result().numerical("avUp").set("data", "clnUp");
            model.result().numerical("avUp").setIndex("expr", "c", 0);
            model.result().numerical("avUp").run();
            p("upper-half outlet line average = "
              + model.result().numerical("avUp").getReal()[0][0]);

            /* analytical value of the same quantity via EvalGlobal */
            model.result().numerical().create("gev", "EvalGlobal");
            model.result().numerical("gev").setIndex("expr", "cSeries(w_ch/2)", 0);
            model.result().numerical("gev").run();
            p("cSeries(+w/2) = " + model.result().numerical("gev").getReal()[0][0]
              + "   (expect ~0.9376 for sugar in the upper half at t_res)");
            model.result().numerical("gev").setIndex("expr", "cSeries(-w_ch/2)", 0);
            model.result().numerical("gev").run();
            p("cSeries(-w/2) = " + model.result().numerical("gev").getReal()[0][0]
              + "   (expect ~0.0624)");

            p("PROBE15 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("PROBE15 ABORTED: " + e);
        }
    }
}
