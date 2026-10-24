/* Ex2M3.java -- mesh recipe test on the REAL Ex2 geometry (no solve).
 *
 * Geometry (mm), Form Union with default "keep interior boundaries":
 *   chamber : 60 x 60 x 1  at (-30,-30,0)   z in [0,1]
 *   duct W  : 45 x  2 x 1  at (-75,-1,0)    touches chamber at x=-30
 *   duct E  : 45 x  2 x 1  at ( 30,-1,0)    touches chamber at x=+30
 *   cellLyr : cyl r=25, h=0.1 at (0,0,-0.1)  z in [-0.1,0]   (EF jobs only)
 *
 * Recipes tested:
 *   R1  BC : FreeTri(all z=0 faces) + Sweep(chamber+ducts) up
 *   R2  BC : FreeTri + 3 separate Sweeps (chamber, ductW, ductE)
 *   R3  EF : FreeTri(chamberBottom+cellDisc) + Sweep(cell down) + FreeTet(chamber)
 *   R4  EF : FreeTri + Sweep(cell down) + Sweep(chamber) -> expected to FAIL (hole)
 *
 * NOTE: no inner classes / lambdas (comsolcompile does not emit them).
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2M3 {

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

    static int[] concat(int[] a, int[] b) {
        int[] r = new int[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    public static void main(String[] args) {
        try {
            r1(false);
            r2(false);
            r3(true);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println();
        System.out.println("EX2M3 DONE");
    }

    // ------------------------------------------------------------------
    /** build geometry + named selections; returns the model */
    static Model build(Model model, boolean withCell) {
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

        // ---- inlet / outlet faces (x = -75 / +75) --------------------
        box(g, "selIn", 2, "allvertices", -75.1, -74.9, -1.1, 1.1, -0.2, 1.2);
        box(g, "selOut", 2, "allvertices", 74.9, 75.1, -1.1, 1.1, -0.2, 1.2);
        // ---- domains -------------------------------------------------
        box(g, "selCham", 3, "inside", -30.1, 30.1, -30.1, 30.1, -0.05, 1.2);
        box(g, "selDuct", 3, "inside", -75.1, 75.1, -1.1, 1.1, -0.05, 1.2);
        box(g, "selDuctW", 3, "inside", -75.1, -30.0, -1.1, 1.1, -0.05, 1.2);
        box(g, "selDuctE", 3, "inside", 30.0, 75.1, -1.1, 1.1, -0.05, 1.2);
        // ---- z=0 faces and z=1 faces ---------------------------------
        box(g, "selZ0", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, -0.005, 0.005);
        box(g, "selZ1", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, 0.995, 1.005);
        // ---- cell layer ----------------------------------------------
        box(g, "selCell", 3, "inside", -25.1, 25.1, -25.1, 25.1, -0.15, 0.05);
        box(g, "selCellTop", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.005, 0.005);
        box(g, "selCellBot", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.105, -0.095);
        // ---- vertical edges (for Distribution) ------------------------
        box(g, "selVert", 1, "inside", -75.1, 75.1, -30.1, 30.1, -0.05, 1.05);
        g.run();
        return model;
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

    static void report(Model model, boolean withCell) {
        System.out.println("  domains=" + model.component("comp1").geom("geom1").getNDomains()
            + " boundaries=" + model.component("comp1").geom("geom1").getNBoundaries());
        System.out.println("  inlet=" + Arrays.toString(model.selection("geom1_selIn").entities(2))
            + " outlet=" + Arrays.toString(model.selection("geom1_selOut").entities(2)));
        System.out.println("  chamber=" + Arrays.toString(model.selection("geom1_selCham").entities(3))
            + " ducts=" + Arrays.toString(model.selection("geom1_selDuct").entities(3))
            + " ductW=" + Arrays.toString(model.selection("geom1_selDuctW").entities(3))
            + " ductE=" + Arrays.toString(model.selection("geom1_selDuctE").entities(3)));
        System.out.println("  z0=" + Arrays.toString(model.selection("geom1_selZ0").entities(2))
            + " z1=" + Arrays.toString(model.selection("geom1_selZ1").entities(2)));
        if (withCell) {
            System.out.println("  cell=" + Arrays.toString(model.selection("geom1_selCell").entities(3))
                + " cellTop=" + Arrays.toString(model.selection("geom1_selCellTop").entities(2))
                + " cellBot=" + Arrays.toString(model.selection("geom1_selCellBot").entities(2)));
        }
        System.out.println("  vertEdges=" + Arrays.toString(model.selection("geom1_selVert").entities(1)));
    }

    // ==================================================================
    static void r1(boolean withCell) {
        h("R1: BC mesh  FreeTri(z=0) + one Sweep(chamber+ducts) up");
        Model model = build(ModelUtil.create("Ex2M3R1"), false);
        report(model, false);
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        int[] z1 = model.selection("geom1_selZ1").entities(2);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] ducts = model.selection("geom1_selDuct").entities(3);
        int[] vert = model.selection("geom1_selVert").entities(1);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.3");
            m.create("ftri1", "FreeTri");
            m.feature("ftri1").selection().geom(2).set(z0);
            m.create("swe1", "Sweep");
            m.feature("swe1").selection().geom(3).set(concat(cham, ducts));
            System.out.println("  Sweep props = " + Arrays.toString(m.feature("swe1").properties()));
            m.create("dis1", "Distribution");
            m.feature("dis1").selection().geom("geom1", 1).set(vert);
            m.feature("dis1").set("numelem", "10");
            m.run();
            stats(m, "R1");
        } catch (Throwable e) {
            System.out.println("  R1 FAILED: " + e.getMessage());
        }
    }

    // ==================================================================
    static void r2(boolean withCell) {
        h("R2: BC mesh  FreeTri(z=0) + 3 Sweeps (chamber, ductW, ductE)");
        Model model = build(ModelUtil.create("Ex2M3R2"), false);
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        int[] z1 = model.selection("geom1_selZ1").entities(2);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] dw = model.selection("geom1_selDuctW").entities(3);
        int[] de = model.selection("geom1_selDuctE").entities(3);
        int[] vert = model.selection("geom1_selVert").entities(1);
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
            m.create("swe2", "Sweep");
            m.feature("swe2").selection().geom(3).set(dw);
            m.create("swe3", "Sweep");
            m.feature("swe3").selection().geom(3).set(de);
            for (String s : new String[]{"swe1", "swe2", "swe3"}) {
                m.feature(s).create("dis1", "Distribution");
                m.feature(s).feature("dis1").selection().geom("geom1", 1).set(vert);
                m.feature(s).feature("dis1").set("numelem", "10");
            }
            m.run();
            stats(m, "R2");
        } catch (Throwable e) {
            System.out.println("  R2 FAILED: " + e.getMessage());
        }
    }

    // ==================================================================
    static void r3(boolean withCell) {
        h("R3: EF mesh  FreeTri(chamberBottom+cellDisc) + Sweep(cell down) + FreeTet(chamber+ducts)");
        Model model = build(ModelUtil.create("Ex2M3R3"), true);
        report(model, true);
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        int[] z1 = model.selection("geom1_selZ1").entities(2);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] ducts = model.selection("geom1_selDuct").entities(3);
        int[] cell = model.selection("geom1_selCell").entities(3);
        int[] cellTop = model.selection("geom1_selCellTop").entities(2);
        int[] cellBot = model.selection("geom1_selCellBot").entities(2);
        int[] vert = model.selection("geom1_selVert").entities(1);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.3");
            m.create("ftri1", "FreeTri");
            m.feature("ftri1").selection().geom(2).set(concat(z0, cellTop));
            // sweep the cell layer downward
            m.create("sweCell", "Sweep");
            m.feature("sweCell").selection().geom(3).set(cell);
            m.feature("sweCell").create("dis1", "Distribution");
            m.feature("sweCell").feature("dis1").selection().geom("geom1", 1).set(cellBot);
            m.feature("sweCell").feature("dis1").set("numelem", "10");
            // tets for chamber + ducts
            m.create("ftet1", "FreeTet");
            m.feature("ftet1").selection().geom(3).set(concat(cham, ducts));
            m.run();
            stats(m, "R3");
        } catch (Throwable e) {
            System.out.println("  R3 FAILED: " + e.getMessage());
        }
    }
}
