/* Probe12: settle the remaining API questions for the H-filter model.
 *
 *  (1) Mesh Control Edge: does it split the domain? how many domains/edges?
 *  (2) Mesh: Size + Distribution + Map -> element count / quality
 *  (3) spf shallow channel + MassFlow inlet: what do the enums mean?
 *      -> solve once, integrate u over the inlet to MEASURE the real Q
 *  (4) tds coupling: default u_src, Inflow/Outflow property names
 *  (5) Analytic function with sum() -> evaluate cSeries(y) via EvalGlobal
 *  (6) LineGraph axis control for a transverse CutLine2D (x = y ?)
 *  (7) Data export of a cut line to a text file
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class Probe12 {

    static void p(String s) { System.out.println(s); }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe12");
            model.component().create("comp1");
            GeomSequence g = model.component("comp1").geom().create("geom1", 2);
            g.lengthUnit("um");
            g.create("r1", "Rectangle");
            g.feature("r1").set("size", new String[]{"6000", "100"});
            g.feature("r1").set("pos", new String[]{"0", "-50"});
            g.create("ln1", "LineSegment");
            g.feature("ln1").set("specify1", "coord");
            g.feature("ln1").set("specify2", "coord");
            g.feature("ln1").set("coord1", new String[]{"0", "0"});
            g.feature("ln1").set("coord2", new String[]{"6000", "0"});
            g.run();
            g.create("mce1", "MeshControlEdges");
            g.feature("mce1").selection("input").set("ln1", 1);
            g.run("mce1");

            g.create("selDom", "BoxSelection");
            g.feature("selDom").set("entitydim", 2);
            g.feature("selDom").set("condition", "allvertices");
            g.feature("selDom").set("xmin", "-1"); g.feature("selDom").set("xmax", "6001");
            g.feature("selDom").set("ymin", "-51"); g.feature("selDom").set("ymax", "51");
            g.create("selIn", "BoxSelection");
            g.feature("selIn").set("entitydim", 1);
            g.feature("selIn").set("condition", "allvertices");
            g.feature("selIn").set("xmin", "-1"); g.feature("selIn").set("xmax", "1");
            g.feature("selIn").set("ymin", "-51"); g.feature("selIn").set("ymax", "51");
            g.create("selOut", "BoxSelection");
            g.feature("selOut").set("entitydim", 1);
            g.feature("selOut").set("condition", "allvertices");
            g.feature("selOut").set("xmin", "5999"); g.feature("selOut").set("xmax", "6001");
            g.feature("selOut").set("ymin", "-51"); g.feature("selOut").set("ymax", "51");
            g.create("selBot", "BoxSelection");
            g.feature("selBot").set("entitydim", 1);
            g.feature("selBot").set("condition", "allvertices");
            g.feature("selBot").set("xmin", "-1"); g.feature("selBot").set("xmax", "6001");
            g.feature("selBot").set("ymin", "-51"); g.feature("selBot").set("ymax", "-49");
            g.run();

            p("=== WITH MESH CONTROL EDGE ===");
            p("DOMAINS : " + Arrays.toString(model.selection("geom1_selDom").entities(2)));
            p("IN      : " + Arrays.toString(model.selection("geom1_selIn").entities(1)));
            p("OUT     : " + Arrays.toString(model.selection("geom1_selOut").entities(1)));
            p("BOTTOM  : " + Arrays.toString(model.selection("geom1_selBot").entities(1)));

            /* ---- mesh ---- */
            MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");
            mesh.create("size1", "Size");
            mesh.feature("size1").set("hmax", "10");
            mesh.feature("size1").set("hmin", "0.5");
            mesh.feature("size1").set("hgrad", 1.2);
            mesh.create("distIn", "Distribution");
            mesh.feature("distIn").selection().geom(1)
                 .set(model.selection("geom1_selIn").entities(1));
            mesh.feature("distIn").set("numelem", "100");
            mesh.create("distBot", "Distribution");
            mesh.feature("distBot").selection().geom(1)
                 .set(model.selection("geom1_selBot").entities(1));
            mesh.feature("distBot").set("numelem", "300");
            mesh.create("map1", "Map");
            try {
                mesh.run();
                p("MESH OK numElem=" + mesh.stat().getNumElem()
                  + " minQ=" + mesh.stat().getMinQuality()
                  + " types=" + Arrays.toString(mesh.stat().getTypes()));
            } catch (Throwable e) {
                p("MESH FAIL: " + e.getMessage());
                try {
                    mesh.feature().remove("map1");
                    mesh.create("ftri1", "FreeTri");
                    mesh.run();
                    p("MESH FreeTri OK numElem=" + mesh.stat().getNumElem());
                } catch (Throwable e2) { p("MESH FreeTri FAIL: " + e2.getMessage()); }
            }

            /* ---- spf ---- */
            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            model.component("comp1").physics("spf").prop("PhysicalModelProperty")
                 .set("ShallowChannelProperty", "1");
            model.component("comp1").physics("spf").prop("PhysicalModelProperty")
                 .set("dz", "5[um]");
            model.component("comp1").physics("spf").create("inl1", "Inlet", 1);
            model.component("comp1").physics("spf").feature("inl1")
                 .selection().set(model.selection("geom1_selIn").entities(1));
            model.component("comp1").physics("spf").feature("inl1")
                 .set("BoundaryCondition", "MassFlow");
            for (String prop : new String[]{"MassFlowType", "StandardFlowRateDefinedBy",
                                           "Mf_src", "Mn_src"}) {
                try {
                    p("inl1 allowed " + prop + ": " + Arrays.toString(
                        model.component("comp1").physics("spf").feature("inl1")
                             .getAllowedPropertyValues(prop)));
                } catch (Throwable e) { p("inl1 allowed " + prop + " FAIL: " + e.getMessage()); }
            }
            // candidate A: mass flow rate treated as TOTAL mass flow of the channel
            try {
                model.component("comp1").physics("spf").feature("inl1").set("mfr", "1.666667e-9[kg/s]");
                p("SET mfr OK (total kg/s reading)");
            } catch (Throwable e) { p("SET mfr FAIL: " + e.getMessage()); }
            model.component("comp1").physics("spf").create("out1", "Outlet", 1);
            model.component("comp1").physics("spf").feature("out1")
                 .selection().set(model.selection("geom1_selOut").entities(1));
            model.component("comp1").physics("spf").feature("out1").set("p0", "0");

            /* ---- tds ---- */
            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            try {
                p("tds.cdm1 default u_src = " + model.component("comp1").physics("tds")
                        .feature("cdm1").getString("u_src"));
            } catch (Throwable e) { p("getString u_src FAIL: " + e.getMessage()); }
            try {
                model.component("comp1").physics("tds").feature("cdm1").set("u_src", "fromCommonDef");
                p("SET tds u_src=fromCommonDef OK");
            } catch (Throwable e) { p("SET u_src FAIL: " + e.getMessage()); }
            model.component("comp1").physics("tds").feature("cdm1").set("D_c", "2e-10[m^2/s]");

            model.func().create("step1", "Step");
            model.func("step1").set("funcname", "step1");
            model.func("step1").set("location", "0");
            model.func("step1").set("from", 1);
            model.func("step1").set("to", 0);
            model.func("step1").set("smooth", "0");

            model.component("comp1").physics("tds").create("inflow1", "Inflow", 1);
            model.component("comp1").physics("tds").feature("inflow1")
                 .selection().set(model.selection("geom1_selIn").entities(1));
            model.component("comp1").physics("tds").feature("inflow1").set("c0", "c0*step1(y)");
            try {
                p("inflow1 allowed BoundaryConditionType: " + Arrays.toString(
                    model.component("comp1").physics("tds").feature("inflow1")
                         .getAllowedPropertyValues("BoundaryConditionType")));
            } catch (Throwable e) { p("inflow1 BoundaryConditionType FAIL: " + e.getMessage()); }
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 1);
            model.component("comp1").physics("tds").feature("outflow1")
                 .selection().set(model.selection("geom1_selOut").entities(1));

            /* ---- analytic function with the Eq.(1) series ---- */
            model.param().set("c0", "1[mol/m^3]");
            model.param().set("w_ch", "100[um]");
            model.param().set("D_sp", "2e-10[m^2/s]");
            model.param().set("t_probe", "1.8[s]");
            model.func().create("cSeries", "Analytic");
            model.func("cSeries").set("funcname", "cSeries");
            model.func("cSeries").set("args", "y");
            model.func("cSeries").set("expr",
                "c0*(1/2-sum(2/((2*n-1)*pi)*sin((2*n-1)*pi*y/w_ch)"
              + "*exp(-((2*n-1)*pi/w_ch)^2*D_sp*t_probe),n,1,100))");
            model.func("cSeries").set("argunit", "m");
            model.func("cSeries").set("fununit", "mol/m^3");
            p("ANALYTIC FUNC created");

            /* ---- solve ---- */
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

            /* ---- measure Q and dp ---- */
            model.result().numerical().create("intIn", "IntLine");
            model.result().numerical("intIn").selection()
                 .set(model.selection("geom1_selIn").entities(1));
            model.result().numerical("intIn").setIndex("expr", "u", 0);
            model.result().numerical("intIn").setIndex("expr", "1", 1);
            model.result().numerical("intIn").run();
            double[][] dIn = model.result().numerical("intIn").getReal();
            p("int u dl = " + dIn[0][0] + " (um)   int 1 dl = " + dIn[1][0] + " um");
            p("=> Q_measured (dz=5um) = " + (5e-6 * dIn[0][0] * 1e-6) + " m^3/s"
              + "   target = 1.666667e-12 m^3/s");

            model.result().numerical().create("avPIn", "AvLine");
            model.result().numerical("avPIn").selection()
                 .set(model.selection("geom1_selIn").entities(1));
            model.result().numerical("avPIn").setIndex("expr", "p", 0);
            model.result().numerical("avPIn").run();
            model.result().numerical().create("avPOut", "AvLine");
            model.result().numerical("avPOut").selection()
                 .set(model.selection("geom1_selOut").entities(1));
            model.result().numerical("avPOut").setIndex("expr", "p", 0);
            model.result().numerical("avPOut").run();
            p("p_in = " + model.result().numerical("avPIn").getReal()[0][0]
              + "   p_out = " + model.result().numerical("avPOut").getReal()[0][0]);

            /* ---- velocity profile across the width (plug-flow check) ---- */
            double[] ys = {-45, -22, 0, 22, 45};
            for (double yv : ys) {
                model.result().dataset().create("cpt" + (int) yv, "CutPoint2D");
                model.result().dataset("cpt" + (int) yv).set("pointx", "6000");
                model.result().dataset("cpt" + (int) yv).set("pointy", "" + yv);
                model.result().numerical().create("pt" + (int) yv, "EvalPoint");
                model.result().numerical("pt" + (int) yv).set("data", "cpt" + (int) yv);
                model.result().numerical("pt" + (int) yv).setIndex("expr", "u", 0);
                model.result().numerical("pt" + (int) yv).run();
                p("u at (6000, " + yv + ") = "
                  + model.result().numerical("pt" + (int) yv).getReal()[0][0]);
            }

            /* ---- concentration at the outlet walls ---- */
            for (double yv : new double[]{-50, 50}) {
                model.result().dataset().create("cw" + (int) yv, "CutPoint2D");
                model.result().dataset("cw" + (int) yv).set("pointx", "6000");
                model.result().dataset("cw" + (int) yv).set("pointy", "" + yv);
                model.result().numerical().create("ncw" + (int) yv, "EvalPoint");
                model.result().numerical("ncw" + (int) yv).set("data", "cw" + (int) yv);
                model.result().numerical("ncw" + (int) yv).setIndex("expr", "c", 0);
                model.result().numerical("ncw" + (int) yv).run();
                p("c at (6000, " + yv + ") = "
                  + model.result().numerical("ncw" + (int) yv).getReal()[0][0]);
            }

            /* ---- EvalGlobal on the analytic function ---- */
            try {
                model.result().numerical().create("gev", "EvalGlobal");
                model.result().numerical("gev").setIndex("expr", "cSeries(50[um])", 0);
                model.result().numerical("gev").run();
                p("cSeries(50um) = " + model.result().numerical("gev").getReal()[0][0]);
                model.result().numerical("gev").setIndex("expr", "cSeries(-50[um])", 0);
                model.result().numerical("gev").run();
                p("cSeries(-50um) = " + model.result().numerical("gev").getReal()[0][0]);
                model.result().numerical("gev").setIndex("expr", "cSeries(0[um])", 0);
                model.result().numerical("gev").run();
                p("cSeries(0um) = " + model.result().numerical("gev").getReal()[0][0]);
            } catch (Throwable e) { p("EvalGlobal FAIL: " + e.getMessage()); }

            /* ---- transverse cut line at the outlet + LineGraph axis ---- */
            model.result().dataset().create("clnOut", "CutLine2D");
            model.result().dataset("clnOut").setIndex("genpoints", "6000", 0, 0);
            model.result().dataset("clnOut").setIndex("genpoints", "-50", 0, 1);
            model.result().dataset("clnOut").setIndex("genpoints", "6000", 1, 0);
            model.result().dataset("clnOut").setIndex("genpoints", "50", 1, 1);
            model.result().create("pg1", "PlotGroup1D");
            model.result("pg1").create("lngr1", "LineGraph");
            model.result("pg1").feature("lngr1").set("data", "clnOut");
            model.result("pg1").feature("lngr1").set("expr", "c");
            p("=== LineGraph props ===");
            p(Arrays.toString(model.result("pg1").feature("lngr1").properties()));
            try {
                p("LineGraph allowed xdata: " + Arrays.toString(
                    model.result("pg1").feature("lngr1").getAllowedPropertyValues("xdata")));
            } catch (Throwable e) { p("xdata FAIL: " + e.getMessage()); }
            try {
                model.result("pg1").feature("lngr1").set("xdata", "expr");
                model.result("pg1").feature("lngr1").set("xdataexpr", "y");
                p("SET xdata=expr/xdataexpr=y OK");
            } catch (Throwable e) { p("SET xdata FAIL: " + e.getMessage()); }
            try {
                model.result("pg1").feature("lngr1").set("xdata", "expr");
                model.result("pg1").feature("lngr1").set("xdataexpr", "y[1/um]");
                p("SET xdata=y[1/um] OK");
            } catch (Throwable e) { p("SET xdata y[1/um] FAIL: " + e.getMessage()); }
            try {
                model.result("pg1").feature("lngr1").set("expr", "cSeries(y)");
                model.result("pg1").run();
                p("PLOT with analytic series OK");
            } catch (Throwable e) { p("PLOT analytic FAIL: " + e.getMessage()); }

            /* ---- Data export of the cut line ---- */
            try {
                model.result().export().create("dprof", "Data");
                model.result().export("dprof").set("data", "clnOut");
                model.result().export("dprof").set("expr", "c");
                model.result().export("dprof").set("filename",
                    "<project>/comsol/probe12_profile.txt");
                model.result().export("dprof").set("location", "file");
                model.result().export("dprof").run();
                p("DATA EXPORT OK");
            } catch (Throwable e) { p("DATA EXPORT FAIL: " + e.getMessage()); }

            /* ---- image export of a 2D plot ---- */
            try {
                model.result().create("pg2", "PlotGroup2D");
                model.result("pg2").create("surf1", "Surface");
                model.result("pg2").feature("surf1").set("expr", "c");
                model.result("pg2").run();
                model.result().export().create("img1", "pg2", "Image");
                model.result().export("img1").set("pngfilename",
                    "<project>/comsol/probe12_field.png");
                model.result().export("img1").set("size", "manualweb");
                model.result().export("img1").set("width", "1400");
                model.result().export("img1").set("height", "500");
                model.result().export("img1").run();
                p("IMAGE EXPORT OK");
            } catch (Throwable e) { p("IMAGE EXPORT FAIL: " + e.getMessage()); }

            p("PROBE12 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("PROBE12 ABORTED: " + e);
        }
    }
}
