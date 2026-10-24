/* Ex2Mesh.java -- validate the 3D swept-mesh recipe on the REAL Ex2 geometry.
 *
 * Key API facts (verified against COMSOL 6.4 Programming Reference Manual):
 *   3D swept mesh op  : mesh.create("swe1", "Sweep")
 *   source/target faces: selection("sourceface") / selection("targetface")  (Selection props,
 *                        NOT enum-valued -> do NOT .set("sourceface","userdef"))
 *   Distribution under Sweep controls layers in the sweep direction.
 *
 * Geometry (mm):
 *   chamber : 60 x 60 x 1  at (-30,-30,0)   z in [0,1]
 *   duct W  : 45 x  2 x 1  at (-75,-1,0)    touches chamber at x=-30
 *   duct E  : 45 x  2 x 1  at ( 30,-1,0)    touches chamber at x=+30
 *   cellLyr : cyl r=25, h=0.1 at (0,0,-0.1)  z in [-0.1,0]  (EF variant only)
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2Mesh {

    static void h(String s) {
        System.out.println();
        System.out.println("################ " + s + " ################");
    }

    static void stats(MeshSequence m, String label) {
        try {
            System.out.println("  [" + label + "] numElem=" + m.stat().getNumElem()
                + " types=" + Arrays.toString(m.stat().getTypes())
                + " minQ=" + m.stat().getMinQuality()
                + " meanQ=" + m.stat().getMeanQuality());
        } catch (Throwable e) {
            System.out.println("  [" + label + "] STATS FAIL: " + e.getMessage());
        }
    }

    static void box(GeomSequence g, String tag, int dim, String cond,
                    double xmin, double xmax, double ymin, double ymax, double zmin, double zmax) {
        g.create(tag, "BoxSelection");
        g.feature(tag).set("entitydim", dim);
        g.feature(tag).set("condition", cond);
        g.feature(tag).set("xmin", "" + xmin); g.feature(tag).set("xmax", "" + xmax);
        g.feature(tag).set("ymin", "" + ymin); g.feature(tag).set("ymax", "" + ymax);
        g.feature(tag).set("zmin", "" + zmin); g.feature(tag).set("zmax", "" + zmax);
    }

    static Model build(String tag, boolean withCell) {
        Model model = ModelUtil.create(tag);
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blkCh", "Block");
        g.feature("blkCh").set("size", new String[]{"60", "60", "1"});
        g.feature("blkCh").set("pos", new String[]{"-30", "-30", "0"});
        g.create("blkW", "Block");
        g.feature("blkW").set("size", new String[]{"45", "2", "1"});
        g.feature("blkW").set("pos", new String[]{"-75", "-1", "0"});
        g.create("blkE", "Block");
        g.feature("blkE").set("size", new String[]{"45", "2", "1"});
        g.feature("blkE").set("pos", new String[]{"30", "-1", "0"});
        if (withCell) {
            g.create("cylCell", "Cylinder");
            g.feature("cylCell").set("r", "25");
            g.feature("cylCell").set("h", "0.1");
            g.feature("cylCell").set("pos", new String[]{"0", "0", "-0.1"});
        }
        g.run();
        box(g, "selIn", 2, "allvertices", -75.1, -74.9, -1.1, 1.1, -0.2, 1.2);
        box(g, "selOut", 2, "allvertices", 74.9, 75.1, -1.1, 1.1, -0.2, 1.2);
        box(g, "selCham", 3, "inside", -30.1, 30.1, -30.1, 30.1, -0.05, 1.2);
        box(g, "selDuct", 3, "inside", -75.1, 75.1, -1.1, 1.1, -0.05, 1.2);
        box(g, "selCell", 3, "inside", -25.1, 25.1, -25.1, 25.1, -0.15, 0.05);
        box(g, "selZ0", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, -0.005, 0.005);
        box(g, "selZ1", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, 0.995, 1.005);
        box(g, "selCellBot", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.105, -0.095);
        box(g, "selVert", 1, "inside", -75.1, 75.1, -30.1, 30.1, -0.05, 1.05);
        g.run();
        return model;
    }

    public static void main(String[] args) {
        try {
            run(false, "base");
            run(true, "cell");
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println();
        System.out.println("EX2MESH DONE");
    }

    static void run(boolean withCell, String label) {
        h("MESH: " + label);
        Model model = build("Ex2Mesh_" + label, withCell);
        System.out.println("  domains=" + model.component("comp1").geom("geom1").getNDomains());
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        int[] z1 = model.selection("geom1_selZ1").entities(2);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] ducts = model.selection("geom1_selDuct").entities(3);
        int[] cell = model.selection("geom1_selCell").entities(3);
        int[] cellBot = model.selection("geom1_selCellBot").entities(2);
        int[] vert = model.selection("geom1_selVert").entities(1);
        System.out.println("  z0=" + Arrays.toString(z0) + " z1=" + Arrays.toString(z1)
            + " cham=" + Arrays.toString(cham) + " ducts=" + Arrays.toString(ducts)
            + " cell=" + Arrays.toString(cell));
        System.out.println("  vertEdges=" + Arrays.toString(vert));

        // ---- sweep the fluid (chamber + ducts) upward -------------------
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.3");
            m.create("ftri1", "FreeTri");
            m.feature("ftri1").selection().geom(2).set(z0);
            m.create("swe1", "Sweep");
            m.feature("swe1").selection().geom(3).set(cham);
            System.out.println("  Sweep props = " + Arrays.toString(m.feature("swe1").properties()));
            try {
                m.feature("swe1").selection("sourceface").geom(2).set(z0);
                m.feature("swe1").selection("targetface").geom(2).set(z1);
                System.out.println("  selection(sourceface/targetface) OK");
            } catch (Throwable e) {
                System.out.println("  selection(sourceface) FAIL: " + e.getMessage());
            }
            // Distribution under Sweep: try edge selection
            try {
                m.feature("swe1").create("dis1", "Distribution");
                System.out.println("  Distribution props = "
                    + Arrays.toString(m.feature("swe1").feature("dis1").properties()));
                m.feature("swe1").feature("dis1").selection().geom("geom1", 1).set(vert);
                m.feature("swe1").feature("dis1").set("numelem", "10");
                System.out.println("  Distribution on vertical edges OK");
            } catch (Throwable e) {
                System.out.println("  Distribution FAIL: " + e.getMessage());
            }
            m.run();
            stats(m, "swept fluid");
        } catch (Throwable e) {
            System.out.println("  FLUID SWEEP FAILED: " + e.getMessage());
            e.printStackTrace();
        }

        // ---- sweep the cell layer downward ------------------------------
        if (withCell) {
            try {
                MeshSequence m = model.component("comp1").mesh().create("mesh2", "geom1");
                m.create("size1", "Size");
                m.feature("size1").set("hmax", "1.0");
                m.feature("size1").set("hmin", "0.02");
                m.feature("size1").set("hgrad", "1.3");
                m.create("ftri1", "FreeTri");
                m.feature("ftri1").selection().geom(2).set(cellBot);
                m.create("sweCell", "Sweep");
                m.feature("sweCell").selection().geom(3).set(cell);
                m.feature("sweCell").selection("sourceface").geom(2).set(cellBot);
                m.feature("sweCell").create("dis1", "Distribution");
                m.feature("sweCell").feature("dis1").selection().geom("geom1", 1);
                System.out.println("  cell sweep created");
                m.run();
                stats(m, "swept cell");
            } catch (Throwable e) {
                System.out.println("  CELL SWEEP FAILED: " + e.getMessage());
            }
        }
    }
}
