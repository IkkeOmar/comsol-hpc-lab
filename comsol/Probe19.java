/* Probe19: mesh-only. How to give the Mesh Control Edge a Distribution so
 * that the mapped mesh is consistent (the MCE edge must have the same number
 * of elements as the top/bottom edges, otherwise the quads next to y=0 are
 * skewed and the concentration field is corrupted).
 *
 * Also tests the Interpolation-function route for plotting the analytical
 * series inside COMSOL.
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class Probe19 {

    static void p(String s) { System.out.println(s); }

    static void report(MeshSequence m, String label) {
        p(label + ": numElem=" + m.stat().getNumElem()
          + "  minQ=" + m.stat().getMinQuality()
          + "  meanQ=" + m.stat().getMeanQuality());
    }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("P19");
            model.component().create("comp1");
            model.param().set("w_ch", "100[um]");
            model.param().set("L_ch", "6000[um]");

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
            p("model selection tags: " + Arrays.toString(model.selection().tags()));

            /* --- try selecting the MCE edge in a mesh Distribution --- */
            String[] attempts = {"geom1_mce1", "mce1", "geom1_ln1", "ln1"};
            for (String nm : attempts) {
                MeshSequence mesh = model.component("comp1").mesh().create("mesh_" + nm, "geom1");
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
                mesh.create("distMCE", "Distribution");
                boolean ok = false;
                try {
                    mesh.feature("distMCE").selection().named(nm);
                    ok = true;
                } catch (Throwable e) {
                    p("  named('" + nm + "') FAIL: " + e.getMessage());
                }
                if (ok) {
                    mesh.feature("distMCE").set("numelem", "120");
                    mesh.create("map1", "Map");
                    try {
                        mesh.run();
                        report(mesh, "MCE + Map + named('" + nm + "') dist");
                    } catch (Throwable e) {
                        p("  named('" + nm + "') mesh run FAIL: " + e.getMessage());
                    }
                }
                try { model.component("comp1").mesh().remove("mesh_" + nm); } catch (Throwable e) { }
            }

            /* --- FreeTri with MCE at moderate resolution --- */
            for (String hmax : new String[]{"10[um]", "6[um]"}) {
                MeshSequence mesh = model.component("comp1").mesh().create("mesh_ft" + hmax.charAt(0), "geom1");
                mesh.create("size1", "Size");
                mesh.feature("size1").set("hmax", hmax);
                mesh.feature("size1").set("hmin", "0.5[um]");
                mesh.feature("size1").set("hgrad", 1.3);
                mesh.create("ftri1", "FreeTri");
                try { mesh.run(); report(mesh, "MCE + FreeTri hmax=" + hmax); }
                catch (Throwable e) { p("FreeTri " + hmax + " FAIL: " + e.getMessage()); }
                try { model.component("comp1").mesh().remove("mesh_ft" + hmax.charAt(0)); } catch (Throwable e) { }
            }

            /* --- Interpolation function: check evaluation --- */
            model.func().create("cTab", "Interpolation");
            model.func("cTab").set("source", "table");
            model.func("cTab").set("funcname", "cTab");
            model.func("cTab").set("table", new String[][]{
                {"-50", "0.0"}, {"0", "0.5"}, {"50", "1.0"}});
            model.func("cTab").set("interp", "linear");
            model.func("cTab").set("argunit", "um");
            model.func("cTab").set("fununit", "mol/m^3");
            p("interpolation set OK");
            try {
                model.result().numerical().create("gevTab", "EvalGlobal");
                model.result().numerical("gevTab").setIndex("expr", "cTab(0)", 0);
                model.result().numerical("gevTab").run();
                p("cTab(0) = " + model.result().numerical("gevTab").getReal()[0][0]);
            } catch (Throwable e) { p("EvalGlobal cTab FAIL: " + e.getMessage()); }
            try {
                model.result().numerical().create("gevTab2", "EvalGlobal");
                model.result().numerical("gevTab2").setIndex("expr", "cTab(25)", 0);
                model.result().numerical("gevTab2").run();
                p("cTab(25) = " + model.result().numerical("gevTab2").getReal()[0][0] + " (expect 0.75)");
            } catch (Throwable e) { p("EvalGlobal cTab(25) FAIL: " + e.getMessage()); }

            p("PROBE19 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("PROBE19 ABORTED: " + e);
        }
    }
}
