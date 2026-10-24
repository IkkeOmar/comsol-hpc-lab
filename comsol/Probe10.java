/* Probe10: enumerate the real property names / tags on this COMSOL 6.4 build.
 * Every call is inlined (no cast to PhysicsFeature, no reflection).
 * Blocks are independent; each failure is reported, not fatal.
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class Probe10 {

    static void p(String s) { System.out.println(s); }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe10");
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
            g.create("selMid", "BoxSelection");
            g.feature("selMid").set("entitydim", 1);
            g.feature("selMid").set("condition", "allvertices");
            g.feature("selMid").set("xmin", "-1"); g.feature("selMid").set("xmax", "6001");
            g.feature("selMid").set("ymin", "-1"); g.feature("selMid").set("ymax", "1");
            g.run();

            int[] inEdges = model.selection("geom1_selIn").entities(1);
            int[] outEdges = model.selection("geom1_selOut").entities(1);
            int[] midEdges = model.selection("geom1_selMid").entities(1);
            p("IN EDGES   : " + Arrays.toString(inEdges));
            p("OUT EDGES  : " + Arrays.toString(outEdges));
            p("MID EDGES  : " + Arrays.toString(midEdges));

            /* ---------------- spf : shallow channel + inlet ---------------- */
            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            p("=== spf prop groups ===");
            p(Arrays.toString(model.component("comp1").physics("spf").prop().tags()));
            try {
                p("PhysicalModelProperty props: " + Arrays.toString(
                        model.component("comp1").physics("spf").prop("PhysicalModelProperty").properties()));
                p("allowed ShallowChannelProperty: " + Arrays.toString(
                        model.component("comp1").physics("spf").prop("PhysicalModelProperty")
                             .getAllowedPropertyValues("ShallowChannelProperty")));
                p("allowed dz: " + Arrays.toString(
                        model.component("comp1").physics("spf").prop("PhysicalModelProperty")
                             .getAllowedPropertyValues("dz")));
            } catch (Throwable e) { p("PMP FAIL: " + e.getMessage()); }

            model.component("comp1").physics("spf").create("inl1", "Inlet", 1);
            model.component("comp1").physics("spf").feature("inl1").selection().set(inEdges);
            p("=== spf.inl1 props ===");
            p(Arrays.toString(model.component("comp1").physics("spf").feature("inl1").properties()));
            String[] bcs = null;
            try {
                bcs = model.component("comp1").physics("spf").feature("inl1")
                        .getAllowedPropertyValues("BoundaryCondition");
                p("spf.inl1 allowed BoundaryCondition: " + Arrays.toString(bcs));
            } catch (Throwable e) { p("BC FAIL: " + e.getMessage()); }
            if (bcs != null) {
                for (String bc : bcs) {
                    try {
                        model.component("comp1").physics("spf").feature("inl1")
                             .set("BoundaryCondition", bc);
                        p("SET BC OK: " + bc + " -> props: " + Arrays.toString(
                                model.component("comp1").physics("spf").feature("inl1").properties()));
                    } catch (Throwable e) {
                        p("SET BC FAIL: " + bc + " : " + e.getMessage());
                    }
                }
            }
            model.component("comp1").physics("spf").create("out1", "Outlet", 1);
            model.component("comp1").physics("spf").feature("out1").selection().set(outEdges);
            p("=== spf.out1 props ===");
            p(Arrays.toString(model.component("comp1").physics("spf").feature("out1").properties()));

            /* ---------------- tds ------------------------------------------ */
            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            String[] tags = model.component("comp1").physics("tds").feature().tags();
            p("=== TDS default tags: " + Arrays.toString(tags));
            for (String tag : tags) {
                try {
                    String[] pp = model.component("comp1").physics("tds").feature(tag).properties();
                    p("tds." + tag + " props: " + Arrays.toString(pp));
                    if (Arrays.asList(pp).contains("D_c")) p("   >>> D_c lives on tds." + tag);
                    if (Arrays.asList(pp).contains("initc")) p("   >>> initc lives on tds." + tag);
                    if (Arrays.asList(pp).contains("u")) {
                        try {
                            p("   >>> tds." + tag + " allowed u: " + Arrays.toString(
                                model.component("comp1").physics("tds").feature(tag)
                                     .getAllowedPropertyValues("u")));
                        } catch (Throwable e) { p("   >>> allowed u FAIL: " + e.getMessage()); }
                    }
                    for (String cand : new String[]{"u_src", "Convection", "c", "c0", "N0"}) {
                        if (Arrays.asList(pp).contains(cand)) {
                            try {
                                p("   >>> tds." + tag + " allowed " + cand + ": " + Arrays.toString(
                                    model.component("comp1").physics("tds").feature(tag)
                                         .getAllowedPropertyValues(cand)));
                            } catch (Throwable e) {
                                p("   >>> allowed " + cand + " FAIL: " + e.getMessage());
                            }
                        }
                    }
                } catch (Throwable e) {
                    p("tds." + tag + " props FAIL: " + e.getMessage());
                }
            }
            model.component("comp1").physics("tds").create("inflow1", "Inflow", 1);
            model.component("comp1").physics("tds").feature("inflow1").selection().set(inEdges);
            p("=== tds.inflow1 props ===");
            p(Arrays.toString(model.component("comp1").physics("tds").feature("inflow1").properties()));
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 1);
            p("=== tds.outflow1 props ===");
            p(Arrays.toString(model.component("comp1").physics("tds").feature("outflow1").properties()));

            /* ---------------- step function ---------------------------------- */
            model.func().create("step1", "Step");
            p("=== step props ===");
            p(Arrays.toString(model.func("step1").properties()));
            try {
                model.func("step1").set("location", "0");
                model.func("step1").set("from", 0);
                model.func("step1").set("to", 1);
                p("step set OK");
            } catch (Throwable e) { p("step set FAIL: " + e.getMessage()); }

            /* ---------------- mesh ------------------------------------------ */
            MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");
            mesh.create("size1", "Size");
            mesh.feature("size1").set("hmax", "20");
            mesh.feature("size1").set("hmin", "0.5");
            mesh.feature("size1").set("hgrad", 1.3);
            mesh.create("dist1", "Distribution");
            try {
                mesh.feature("dist1").selection().geom("geom1_mce1").set(1);
                p("MCE distribution selection OK");
            } catch (Throwable e) {
                p("MCE distribution selection FAIL: " + e.getMessage());
                try {
                    mesh.feature("dist1").selection().geom(1).set(midEdges);
                    p("plain-edge distribution selection OK");
                } catch (Throwable e2) { p("plain-edge distribution FAIL: " + e2.getMessage()); }
            }
            try {
                mesh.feature("dist1").set("numelem", "120");
                p("numelem OK");
            } catch (Throwable e) { p("numelem FAIL: " + e.getMessage()); }
            try {
                mesh.run();
                p("MESH OK numElem=" + mesh.stat().getNumElem()
                  + " minQ=" + mesh.stat().getMinQuality());
            } catch (Throwable e) { p("MESH FAIL: " + e.getMessage()); }

            /* ---------------- numerical + export API ------------------------ */
            model.result().numerical().create("pt1", "EvalPoint");
            p("=== EvalPoint props ===");
            p(Arrays.toString(model.result().numerical("pt1").properties()));
            model.result().numerical().create("int1", "IntLine");
            p("=== IntLine props ===");
            p(Arrays.toString(model.result().numerical("int1").properties()));
            model.result().numerical().create("av1", "AvLine");
            p("=== AvLine props ===");
            p(Arrays.toString(model.result().numerical("av1").properties()));

            String[] exTypes = new String[]{"Data", "Plot", "Table", "Image", "Animation", "PlotData"};
            for (String t : exTypes) {
                try {
                    model.result().export().create("ex_" + t, t);
                    p("EXPORT TYPE " + t + " OK props: " + Arrays.toString(
                            model.result().export("ex_" + t).properties()));
                } catch (Throwable e) {
                    p("EXPORT TYPE " + t + " FAIL: " + e.getMessage());
                }
            }

            p("PROBE10 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("PROBE10 ABORTED: " + e);
        }
    }
}
