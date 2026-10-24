/* Probe11: settle the remaining API questions with ONE batch run.
 *  (1) geometry entity counts with/without the Mesh Control Edge
 *  (2) mapped mesh with distributions (does it produce elements?)
 *  (3) spf MassFlow inlet: allowed enum values + units of the flow-rate props
 *  (4) default u_src on the TDS transport-properties node
 *  (5) Analytic function + sum() for the analytical series
 *  (6) CutPoint2D / EvalPoint on a boundary corner, CutLine2D along the outlet
 *  (7) one stationary spf solve -> measured flow rate and dp (Hagen-Poiseuille)
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class Probe11 {

    static void p(String s) { System.out.println(s); }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe11");
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

            // entity counts via box selections
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

            p("DOMAINS : " + Arrays.toString(model.selection("geom1_selDom").entities(2)));
            p("IN     : " + Arrays.toString(model.selection("geom1_selIn").entities(1)));
            p("OUT    : " + Arrays.toString(model.selection("geom1_selOut").entities(1)));
            p("BOTTOM : " + Arrays.toString(model.selection("geom1_selBot").entities(1)));

            /* ---- mesh: mapped + distributions ---- */
            MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");
            mesh.create("size1", "Size");
            mesh.feature("size1").set("hmax", "25");
            mesh.feature("size1").set("hmin", "1");
            mesh.feature("size1").set("hgrad", 1.25);
            mesh.create("distIn", "Distribution");
            mesh.feature("distIn").selection().geom(1).set(model.selection("geom1_selIn").entities(1));
            mesh.feature("distIn").set("numelem", "100");
            mesh.create("distBot", "Distribution");
            mesh.feature("distBot").selection().geom(1).set(model.selection("geom1_selBot").entities(1));
            mesh.feature("distBot").set("numelem", "200");
            mesh.create("map1", "Map");
            try {
                mesh.run();
                p("MESH OK numElem=" + mesh.stat().getNumElem()
                  + " minQ=" + mesh.stat().getMinQuality()
                  + " types=" + Arrays.toString(mesh.stat().getTypes()));
            } catch (Throwable e) { p("MESH FAIL: " + e.getMessage()); }

            /* ---- spf: shallow channel + MassFlow inlet ---- */
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
            model.component("comp1").physics("spf").create("out1", "Outlet", 1);
            model.component("comp1").physics("spf").feature("out1")
                 .selection().set(model.selection("geom1_selOut").entities(1));
            model.component("comp1").physics("spf").feature("out1").set("p0", "0");

            /* ---- tds default u_src ---- */
            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            try {
                p("tds.cdm1 default u_src = " + model.component("comp1").physics("tds")
                        .feature("cdm1").getString("u_src"));
            } catch (Throwable e) { p("getString u_src FAIL: " + e.getMessage()); }
            try {
                model.component("comp1").physics("tds").feature("cdm1").set("u_src", "fromCommonDef");
                p("SET u_src=fromCommonDef OK");
            } catch (Throwable e) { p("SET u_src FAIL: " + e.getMessage()); }

            /* ---- analytic function with sum() ---- */
            try {
                model.func().create("fa", "Analytic");
                model.func("fa").set("funcname", "fa");
                model.func("fa").set("args", "y");
                model.func("fa").set("expr",
                    "1/2-sum(2/((2*n-1)*pi)*sin((2*n-1)*pi*y/100[um])*exp(-((2*n-1)*pi/100[um])^2*2e-10[m^2/s]*5.8656[s]),n,1,50)");
                model.func("fa").set("argunit", "m");
                model.func("fa").set("fununit", "1");
                p("ANALYTIC FUNC OK");
                p("fa(50[um]) = " + model.func("fa").evaluate("50[um]")[0]);
            } catch (Throwable e) {
                p("ANALYTIC FUNC FAIL: " + e.getMessage());
                e.printStackTrace();
            }

            /* ---- stationary flow solve + measurement ---- */
            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            model.study("std1").feature("stat").setSolveFor("/physics/tds", false);
            model.study("std1").createAutoSequences("all");
            try {
                model.study("std1").run();
                p("=== STATIONARY FLOW SOLVE OK ===");
            } catch (Throwable e) { p("SOLVE FAIL: " + e.getMessage()); e.printStackTrace(); }

            // measured flow rate: int(u) dl along the inlet * depth h
            model.result().numerical().create("intIn", "IntLine");
            model.result().numerical("intIn").selection()
                 .set(model.selection("geom1_selIn").entities(1));
            model.result().numerical("intIn").setIndex("expr", "u", 0);
            model.result().numerical("intIn").setIndex("expr", "1", 1);
            model.result().numerical("intIn").run();
            double[][] dIn = model.result().numerical("intIn").getReal();
            double ul = dIn[0][0], len = dIn[1][0];
            p("int u dl = " + ul + " (geometry unit um)   int 1 dl = " + len + " um");
            p("=> Q_measured = h * int(u dl) [m^3/s] = "
              + (5e-6 * ul * 1e-6));

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
            p("p_in  = " + model.result().numerical("avPIn").getReal()[0][0]);
            p("p_out = " + model.result().numerical("avPOut").getReal()[0][0]);
            p("dp    = " + (model.result().numerical("avPIn").getReal()[0][0]
                          - model.result().numerical("avPOut").getReal()[0][0]));

            /* ---- CutPoint2D on a corner + CutLine2D along the outlet ---- */
            try {
                model.result().dataset().create("cpt", "CutPoint2D");
                model.result().dataset("cpt").set("pointx", "6000");
                model.result().dataset("cpt").set("pointy", "50");
                model.result().numerical().create("pt", "EvalPoint");
                model.result().numerical("pt").set("data", "cpt");
                model.result().numerical("pt").setIndex("expr", "u", 0);
                model.result().numerical("pt").run();
                p("CutPoint2D EvalPoint OK: u = " + model.result().numerical("pt").getReal()[0][0]);
            } catch (Throwable e) { p("CutPoint2D FAIL: " + e.getMessage()); }

            try {
                model.result().dataset().create("cln", "CutLine2D");
                model.result().dataset("cln").setIndex("genpoints", "6000", 0, 0);
                model.result().dataset("cln").setIndex("genpoints", "-50", 0, 1);
                model.result().dataset("cln").setIndex("genpoints", "6000", 1, 0);
                model.result().dataset("cln").setIndex("genpoints", "50", 1, 1);
                model.result().numerical().create("intCln", "IntLine");
                model.result().numerical("intCln").set("data", "cln");
                model.result().numerical("intCln").setIndex("expr", "1", 0);
                model.result().numerical("intCln").run();
                p("CutLine2D IntLine OK: length = "
                  + model.result().numerical("intCln").getReal()[0][0]);
            } catch (Throwable e) { p("CutLine2D FAIL: " + e.getMessage()); }

            p("PROBE11 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("PROBE11 ABORTED: " + e);
        }
    }
}
