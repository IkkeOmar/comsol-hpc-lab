/* Ex2Sweep.java -- validate the 3D SWEPT mesh recipe on the real Ex2 geometry.
 *
 * Geometry (mm):
 *   chamber : 60 x 60 x 1  at (-30,-30,0)   z in [0,1]
 *   duct W  : 45 x  2 x 1  at (-75,-1,0)    touches chamber at x=-30
 *   duct E  : 45 x  2 x 1  at ( 30,-1,0)    touches chamber at x=+30
 *   cellLyr : cyl r=25, h=0.1 at (0,0,-0.1)  z in [-0.1,0]   (EF variant only)
 *
 * Variants tested (each on its own mesh sequence):
 *   V1  Sweep(explicit source/target faces) + Distribution attribute on EDGES
 *   V2  Sweep(explicit source/target faces) + Distribution attribute on DOMAINS
 *   V3  Sweep(explicit source/target faces), no Distribution
 *   V4  top-level Distribution on vertical edges (no Sweep attribute)
 *   V5  V1 but with the cell layer present (conformity at z=0)
 *   V6  FreeTet fallback (coarse)
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2Sweep {

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

    static void report(Model model, boolean withCell) {
        System.out.println("  domains=" + model.component("comp1").geom("geom1").getNDomains());
        System.out.println("  inlet=" + Arrays.toString(model.selection("geom1_selIn").entities(2))
            + " outlet=" + Arrays.toString(model.selection("geom1_selOut").entities(2)));
        System.out.println("  chamber=" + Arrays.toString(model.selection("geom1_selCham").entities(3))
            + " ducts=" + Arrays.toString(model.selection("geom1_selDuct").entities(3)));
        System.out.println("  z0=" + Arrays.toString(model.selection("geom1_selZ0").entities(2))
            + " z1=" + Arrays.toString(model.selection("geom1_selZ1").entities(2))
            + " vertEdges=" + Arrays.toString(model.selection("geom1_selVert").entities(1)));
        if (withCell) {
            System.out.println("  cell=" + Arrays.toString(model.selection("geom1_selCell").entities(3))
                + " cellBot=" + Arrays.toString(model.selection("geom1_selCellBot").entities(2)));
        }
    }

    public static void main(String[] args) {
        try {
            v1(false, "edgeDist");
            v2(false, "domDist");
            v3(false, "noDist");
            v4(false, "topLevelDist");
            v1(true, "edgeDist+cell");
            v6(false);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println();
        System.out.println("EX2SWEEP DONE");
    }

    static void v1(boolean withCell, String label) {
        h("V1: " + label);
        Model model = build("Ex2SweepV1" + withCell, withCell);
        report(model, withCell);
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        int[] z1 = model.selection("geom1_selZ1").entities(2);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] ducts = model.selection("geom1_selDuct").entities(3);
        int[] vert = model.selection("geom1_selVert").entities(1);
        int[] cell = model.selection("geom1_selCell").entities(3);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.3");
            m.create("swe1", "Sweep");
            m.feature("swe1").selection().geom(3).set(cham);
            m.feature("swe1").set("sourceface", "userdef");
            m.feature("swe1").selection("sourceface").geom(2).set(z0);
            m.feature("swe1").set("targetface", "userdef");
            m.feature("swe1").selection("targetface").geom(2).set(z1);
            m.feature("swe1").create("dis1", "Distribution");
            m.feature("swe1").feature("dis1").selection().geom("geom1", 1).set(vert);
            m.feature("swe1").feature("dis1").set("numelem", "10");
            m.run();
            stats(m, "V1 " + label);
        } catch (Throwable e) {
            System.out.println("  V1 FAILED: " + e.getMessage());
        }
    }

    static void v2(boolean withCell, String label) {
        h("V2: " + label);
        Model model = build("Ex2SweepV2", withCell);
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        int[] z1 = model.selection("geom1_selZ1").entities(2);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] ducts = model.selection("geom1_selDuct").entities(3);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.3");
            m.create("swe1", "Sweep");
            m.feature("swe1").selection().geom(3).set(cham);
            m.feature("swe1").set("sourceface", "userdef");
            m.feature("swe1").selection("sourceface").geom(2).set(z0);
            m.feature("swe1").set("targetface", "userdef");
            m.feature("swe1").selection("targetface").geom(2).set(z1);
            m.feature("swe1").create("dis1", "Distribution");
            m.feature("swe1").feature("dis1").selection().geom("geom1", 3).set(cham);
            m.feature("swe1").feature("dis1").set("numelem", "10");
            m.run();
            stats(m, "V2 " + label);
        } catch (Throwable e) {
            System.out.println("  V2 FAILED: " + e.getMessage());
        }
    }

    static void v3(boolean withCell, String label) {
        h("V3: " + label);
        Model model = build("Ex2SweepV3", withCell);
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        int[] z1 = model.selection("geom1_selZ1").entities(2);
        int[] cham = model.selection("geom1_selCham").entities(3);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.3");
            m.create("swe1", "Sweep");
            m.feature("swe1").selection().geom(3).set(cham);
            m.feature("swe1").set("sourceface", "userdef");
            m.feature("swe1").selection("sourceface").geom(2).set(z0);
            m.feature("swe1").set("targetface", "userdef");
            m.feature("swe1").selection("targetface").geom(2).set(z1);
            m.run();
            stats(m, "V3 " + label);
        } catch (Throwable e) {
            System.out.println("  V3 FAILED: " + e.getMessage());
        }
    }

    static void v4(boolean withCell, String label) {
        h("V4: " + label);
        Model model = build("Ex2SweepV4", withCell);
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        int[] z1 = model.selection("geom1_selZ1").entities(2);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] vert = model.selection("geom1_selVert").entities(1);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.3");
            m.create("dis1", "Distribution");
            m.feature("dis1").selection().geom("geom1", 1).set(vert);
            m.feature("dis1").set("numelem", "10");
            m.create("swe1", "Sweep");
            m.feature("swe1").selection().geom(3).set(cham);
            m.feature("swe1").set("sourceface", "userdef");
            m.feature("swe1").selection("sourceface").geom(2).set(z0);
            m.feature("swe1").set("targetface", "userdef");
            m.feature("swe1").selection("targetface").geom(2).set(z1);
            m.run();
            stats(m, "V4 " + label);
        } catch (Throwable e) {
            System.out.println("  V4 FAILED: " + e.getMessage());
        }
    }

    static void v6(boolean withCell) {
        h("V6: FreeTet fallback");
        Model model = build("Ex2SweepV6", withCell);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] ducts = model.selection("geom1_selDuct").entities(3);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.3");
            m.create("ftet1", "FreeTet");
            m.feature("ftet1").selection().geom(3).set(cham);
            m.run();
            stats(m, "V6 FreeTet chamber only");
        } catch (Throwable e) {
            System.out.println("  V6 FAILED: " + e.getMessage());
        }
    }
}
