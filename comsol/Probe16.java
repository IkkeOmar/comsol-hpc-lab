/* Probe16: mesh-only (no solve) comparison of three meshing strategies for
 * the H-filter channel, reporting element count and quality.
 *
 *  A) one rectangle + Mesh Control Edge at y=0 + Map mesher + distributions
 *     (the assignment's recipe)
 *  B) one rectangle + Mesh Control Edge at y=0 + FreeQuad mesher
 *  C) two stacked rectangles (upper/lower half meeting at y = 0) + Map mesher
 *     with distributions on every edge -> perfectly symmetric structured mesh
 *
 * Quality matters: the profile comparison against the analytical series is
 * only meaningful if the mesh does not smear the interface.
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class Probe16 {

    static void p(String s) { System.out.println(s); }

    static void report(MeshSequence m, String label) {
        p(label + ": numElem=" + m.stat().getNumElem()
          + "  minQ=" + m.stat().getMinQuality()
          + "  meanQ=" + m.stat().getMeanQuality()
          + "  types=" + Arrays.toString(m.stat().getTypes()));
    }

    public static void main(String[] args) {
        try {
            /* ---------------- variant A ---------------- */
            {
                Model m = ModelUtil.create("VA");
                m.component().create("comp1");
                m.param().set("L_ch", "6000[um]");
                m.param().set("w_ch", "100[um]");
                GeomSequence g = m.component("comp1").geom().create("geom1", 2);
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
                MeshSequence mesh = m.component("comp1").mesh().create("mesh1", "geom1");
                mesh.create("size1", "Size");
                mesh.feature("size1").set("hmax", "20[um]");
                mesh.feature("size1").set("hmin", "0.5[um]");
                mesh.feature("size1").set("hgrad", 1.2);
                mesh.create("distIn", "Distribution");
                mesh.feature("distIn").selection().geom(1)
                     .set(m.selection("geom1_selIn").entities(1));
                mesh.feature("distIn").set("numelem", "80");
                mesh.create("distTop", "Distribution");
                mesh.feature("distTop").selection().geom(1)
                     .set(m.selection("geom1_selTop").entities(1));
                mesh.feature("distTop").set("numelem", "160");
                mesh.create("distBot", "Distribution");
                mesh.feature("distBot").selection().geom(1)
                     .set(m.selection("geom1_selBot").entities(1));
                mesh.feature("distBot").set("numelem", "160");
                mesh.create("map1", "Map");
                try { mesh.run(); report(mesh, "A: MCE + Map + dist(in,top,bot)"); }
                catch (Throwable e) { p("A FAIL: " + e.getMessage()); }
                ModelUtil.remove(m);
            }

            /* ---------------- variant B ---------------- */
            {
                Model m = ModelUtil.create("VB");
                m.component().create("comp1");
                m.param().set("L_ch", "6000[um]");
                m.param().set("w_ch", "100[um]");
                GeomSequence g = m.component("comp1").geom().create("geom1", 2);
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
                MeshSequence mesh = m.component("comp1").mesh().create("mesh1", "geom1");
                mesh.create("size1", "Size");
                mesh.feature("size1").set("hmax", "2[um]");
                mesh.feature("size1").set("hmin", "0.3[um]");
                mesh.feature("size1").set("hgrad", 1.3);
                mesh.create("ftri1", "FreeTri");
                try { mesh.run(); report(mesh, "B: MCE + FreeTri hmax=2um"); }
                catch (Throwable e) { p("B FAIL: " + e.getMessage()); }
                ModelUtil.remove(m);
            }

            /* ---------------- variant C ---------------- */
            {
                Model m = ModelUtil.create("VC");
                m.component().create("comp1");
                m.param().set("L_ch", "6000[um]");
                m.param().set("w_ch", "100[um]");
                GeomSequence g = m.component("comp1").geom().create("geom1", 2);
                g.lengthUnit("um");
                g.create("r1", "Rectangle");
                g.feature("r1").set("size", new String[]{"L_ch", "w_ch/2"});
                g.feature("r1").set("pos", new String[]{"0", "0"});
                g.create("r2", "Rectangle");
                g.feature("r2").set("size", new String[]{"L_ch", "w_ch/2"});
                g.feature("r2").set("pos", new String[]{"0", "-w_ch/2"});
                g.run();
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
                p("C: domains = " + Arrays.toString(
                    m.selection("geom1_selIn").entities(1)));
                MeshSequence mesh = m.component("comp1").mesh().create("mesh1", "geom1");
                mesh.create("size1", "Size");
                mesh.feature("size1").set("hmax", "20[um]");
                mesh.feature("size1").set("hmin", "0.5[um]");
                mesh.feature("size1").set("hgrad", 1.2);
                mesh.create("distIn", "Distribution");
                mesh.feature("distIn").selection().geom(1)
                     .set(m.selection("geom1_selIn").entities(1));
                mesh.feature("distIn").set("numelem", "80");
                mesh.create("distTop", "Distribution");
                mesh.feature("distTop").selection().geom(1)
                     .set(m.selection("geom1_selTop").entities(1));
                mesh.feature("distTop").set("numelem", "160");
                mesh.create("distBot", "Distribution");
                mesh.feature("distBot").selection().geom(1)
                     .set(m.selection("geom1_selBot").entities(1));
                mesh.feature("distBot").set("numelem", "160");
                mesh.create("map1", "Map");
                try { mesh.run(); report(mesh, "C: 2 rects + Map + dist(in,top,bot)"); }
                catch (Throwable e) { p("C FAIL: " + e.getMessage()); }
                ModelUtil.remove(m);
            }

            p("PROBE16 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("PROBE16 ABORTED: " + e);
        }
    }
}
