/* Probe18: (1) how to select the Mesh Control Edge for a mesh Distribution,
 *          (2) mesh quality of several MCE-compatible strategies,
 *          (3) API for parametric sweeps (parameter selection 'All',
 *              outersolnum, getPVals) needed for the D-sweep and the
 *              residence-time sweep.
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class Probe18 {

    static void p(String s) { System.out.println(s); }

    static void report(MeshSequence m, String label) {
        p(label + ": numElem=" + m.stat().getNumElem()
          + "  minQ=" + m.stat().getMinQuality()
          + "  meanQ=" + m.stat().getMeanQuality());
    }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("P18");
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
            p("edges before MCE: nEdges=" + g.getNEdges() + " nDomains=" + g.getNDomains());
            g.create("mce1", "MeshControlEdges");
            g.feature("mce1").selection("input").set("ln1", 1);
            g.run("mce1");
            p("edges after MCE: nEdges=" + g.getNEdges() + " nDomains=" + g.getNDomains());

            g.create("selIn", "BoxSelection");
            g.feature("selIn").set("entitydim", 1);
            g.feature("selIn").set("condition", "allvertices");
            g.feature("selIn").set("xmin", "-1[um]"); g.feature("selIn").set("xmax", "1[um]");
            g.feature("selIn").set("ymin", "-w_ch/2-1[um]"); g.feature("selIn").set("ymax", "w_ch/2+1[um]");
            g.create("selTop", "BoxSelection");
            g.feature("selTop").set("entitydim", 1);
            g.feature("selTop").set("condition", "allvertices");
            g.feature("selTop").set("xmin", "-1[um]"); g.feature("selTop").set("xmax", "L_ch+1[um]");
            g.feature("selTop").set("ymin", "w_ch/2-1[um]"); g.feature("selTop").set("ymax", "w_ch/2+1[um]");
            g.create("selBot", "BoxSelection");
            g.feature("selBot").set("entitydim", 1);
            g.feature("selBot").set("condition", "allvertices");
            g.feature("selBot").set("xmin", "-1[um]"); g.feature("selBot").set("xmax", "L_ch+1[um]");
            g.feature("selBot").set("ymin", "-w_ch/2-1[um]"); g.feature("selBot").set("ymax", "-w_ch/2+1[um]");
            g.run();
            int[] inE  = model.selection("geom1_selIn").entities(1);
            int[] topE = model.selection("geom1_selTop").entities(1);
            int[] botE = model.selection("geom1_selBot").entities(1);
            p("IN=" + Arrays.toString(inE) + " TOP=" + Arrays.toString(topE) + " BOT=" + Arrays.toString(botE));

            /* --- MCE + Map, trying to put a Distribution on the MCE edge --- */
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
            mesh.feature("distTop").set("numelem", "120");
            mesh.create("distBot", "Distribution");
            mesh.feature("distBot").selection().geom(1).set(botE);
            mesh.feature("distBot").set("numelem", "120");
            /* the MCE edge: try to select it */
            for (int cand = 1; cand <= g.getNEdges(); cand++) {
                mesh.create("distMCE" + cand, "Distribution");
                try {
                    mesh.feature("distMCE" + cand).selection().geom(1).set(cand);
                    mesh.feature("distMCE" + cand).set("numelem", "120");
                    p("  MCE edge candidate " + cand + ": selection accepted");
                } catch (Throwable e) {
                    p("  MCE edge candidate " + cand + ": REJECTED (" + e.getMessage() + ")");
                    try { mesh.feature().remove("distMCE" + cand); } catch (Throwable e2) { }
                }
            }
            mesh.create("map1", "Map");
            try { mesh.run(); report(mesh, "MCE + Map + dist(in,top,bot,+MCEcands)"); }
            catch (Throwable e) { p("mesh run FAIL: " + e.getMessage()); }

            /* --- how many elements does each edge get? --- */
            try {
                p("mesh statistics: " + mesh.stat().getNumElem() + " elements");
            } catch (Throwable e) { p("mesh stat FAIL: " + e.getMessage()); }

            /* --- parametric sweep API: what does a numerical feature offer? --- */
            model.result().numerical().create("avTest", "AvLine");
            p("AvLine props: " + Arrays.toString(model.result().numerical("avTest").properties()));
            for (String pr : new String[]{"data", "outersolnum", "solnum", "paramselection",
                                          "intsurface", "intvolume"}) {
                try {
                    p("  AvLine allowed " + pr + ": " + Arrays.toString(
                        model.result().numerical("avTest").getAllowedPropertyValues(pr)));
                } catch (Throwable e) { p("  AvLine " + pr + " FAIL: " + e.getMessage()); }
            }
            model.result().numerical().create("evTest", "EvalPoint");
            p("EvalPoint props: " + Arrays.toString(model.result().numerical("evTest").properties()));
            for (String pr : new String[]{"data", "outersolnum", "solnum", "paramselection"}) {
                try {
                    p("  EvalPoint allowed " + pr + ": " + Arrays.toString(
                        model.result().numerical("evTest").getAllowedPropertyValues(pr)));
                } catch (Throwable e) { p("  EvalPoint " + pr + " FAIL: " + e.getMessage()); }
            }
            /* study parametric property names */
            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            p("Stationary study props: " + Arrays.toString(model.study("std1").feature("stat").properties()));
            for (String pr : new String[]{"pname", "plistarr", "punit", "sweeptype", "useparam", "notlistsolnum"}) {
                try {
                    p("  study allowed " + pr + ": " + Arrays.toString(
                        model.study("std1").feature("stat").getAllowedPropertyValues(pr)));
                } catch (Throwable e) { p("  study " + pr + " FAIL: " + e.getMessage()); }
            }
            /* solution API for parameter values */
            p("PROBE18 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("PROBE18 ABORTED: " + e);
        }
    }
}
