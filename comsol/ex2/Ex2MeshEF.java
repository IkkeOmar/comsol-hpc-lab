/* Ex2MeshEF.java -- find a working mesh recipe for the EF case (cell layer present).
 *
 * Geometry: chamber 60x60x1 (z 0..1) + duct W 45x2x1 (x -75..-30) + duct E (x 30..75)
 *           + cell cylinder r=25, h=0.1 (z -0.1..0) under the chamber floor.
 * With Form Union the cell cylinder's top disc becomes an INTERIOR BOUNDARY, so the
 * chamber's bottom face has a circular hole -> a z-sweep of the chamber alone cannot
 * map that hole onto the (full) top face. Recipes tested:
 *
 *  E1  FreeTri(all z=0 faces incl. the cell disc) + Sweep(cell: src=disc z=0,
 *      tgt=disc z=-0.1) + FreeTet(chamber+ducts).  Chamber bottom pre-meshed by the
 *      same FreeTri op as the cell disc -> shared circle nodes.
 *  E2  FreeTri(cell bottom disc) + Sweep(cell: src=bottom disc, tgt=top disc)
 *      + FreeTri(chamber bottom + duct bottoms) + Sweep(chamber+ducts in z)
 *  E3  FreeTet(chamber+ducts) + FreeTet(cell) with isotropic fine size  -> count only
 *  E4  z-sweep of chamber+ducts but source = FULL z=0 cross section (annulus+disc)
 *  E5  x-sweep of chamber (src x=-30 wall, tgt x=+30 wall) + z-sweep of cell
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2MeshEF {

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

    static Model build(String tag) {
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
        g.create("cylCell", "Cylinder");
        g.feature("cylCell").set("r", "25");
        g.feature("cylCell").set("h", "0.1");
        g.feature("cylCell").set("pos", new String[]{"0", "0", "-0.1"});
        g.run();
        // domains
        box(g, "selCham", 3, "inside", -30.1, 30.1, -30.1, 30.1, -0.05, 1.2);
        box(g, "selDuct", 3, "inside", -75.1, 75.1, -30.1, 30.1, -0.05, 1.2);
        box(g, "selCell", 3, "inside", -25.1, 25.1, -25.1, 25.1, -0.15, 0.05);
        // faces
        box(g, "selZ0", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, -0.005, 0.005);
        box(g, "selZ1", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, 0.995, 1.005);
        box(g, "selCellTop", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.005, 0.005);
        box(g, "selCellBot", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.105, -0.095);
        box(g, "selXm", 2, "allvertices", -30.1, -29.9, -30.1, 30.1, -0.05, 1.05);
        box(g, "selXp", 2, "allvertices", 29.9, 30.1, -30.1, 30.1, -0.05, 1.05);
        // vertical edges
        box(g, "selVert", 1, "inside", -75.1, 75.1, -30.1, 30.1, -0.05, 1.05);
        g.run();
        System.out.println("  domains=" + g.getNDomains() + " boundaries=" + g.getNBoundaries());
        System.out.println("  chamber=" + Arrays.toString(model.selection("geom1_selCham").entities(3))
            + " ducts=" + Arrays.toString(model.selection("geom1_selDuct").entities(3))
            + " cell=" + Arrays.toString(model.selection("geom1_selCell").entities(3)));
        System.out.println("  z0=" + Arrays.toString(model.selection("geom1_selZ0").entities(2))
            + " z1=" + Arrays.toString(model.selection("geom1_selZ1").entities(2)));
        System.out.println("  cellTop=" + Arrays.toString(model.selection("geom1_selCellTop").entities(2))
            + " cellBot=" + Arrays.toString(model.selection("geom1_selCellBot").entities(2)));
        System.out.println("  x-=" + Arrays.toString(model.selection("geom1_selXm").entities(2))
            + " x+=" + Arrays.toString(model.selection("geom1_selXp").entities(2)));
        System.out.println("  vertE=" + Arrays.toString(model.selection("geom1_selVert").entities(1)));
        return model;
    }

    public static void main(String[] args) {
        try {
            e1();
            e2();
            e4();
            e5();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println();
        System.out.println("EX2MESHEF DONE");
    }

    static void e1() {
        h("E1: FreeTri(all z=0 incl cell disc) + Sweep(cell down) + FreeTet(fluid)");
        Model model = build("Ex2MeshEFE1");
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] ducts = model.selection("geom1_selDuct").entities(3);
        int[] cell = model.selection("geom1_selCell").entities(3);
        int[] cellTop = model.selection("geom1_selCellTop").entities(2);
        int[] cellBot = model.selection("geom1_selCellBot").entities(2);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.3");
            m.create("ftri1", "FreeTri");
            m.feature("ftri1").selection().geom(2).set(z0);
            m.create("sweCell", "Sweep");
            m.feature("sweCell").selection().geom(3).set(cell);
            m.feature("sweCell").selection("sourceface").geom(2).set(cellTop);
            m.feature("sweCell").selection("targetface").geom(2).set(cellBot);
            m.feature("sweCell").create("dis2", "Distribution");
            m.feature("sweCell").feature("dis2").selection().geom("geom1", 3).set(cell);
            m.feature("sweCell").feature("dis2").set("numelem", "5");
            m.create("ftet1", "FreeTet");
            m.feature("ftet1").selection().geom(3).set(concat(cham, ducts));
            m.run();
            stats(m, "E1");
        } catch (Throwable e) {
            System.out.println("  E1 FAILED: " + e.getMessage());
        }
    }

    static void e2() {
        h("E2: Sweep(cell down from bottom disc) + z-sweep of fluid");
        Model model = build("Ex2MeshEFE2");
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        int[] z1 = model.selection("geom1_selZ1").entities(2);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] ducts = model.selection("geom1_selDuct").entities(3);
        int[] cell = model.selection("geom1_selCell").entities(3);
        int[] cellBot = model.selection("geom1_selCellBot").entities(2);
        int[] vert = model.selection("geom1_selVert").entities(1);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.3");
            m.create("ftriC", "FreeTri");
            m.feature("ftriC").selection().geom(2).set(cellBot);
            m.create("sweCell", "Sweep");
            m.feature("sweCell").selection().geom(3).set(cell);
            m.feature("sweCell").selection("sourceface").geom(2).set(cellBot);
            m.create("ftri1", "FreeTri");
            m.feature("ftri1").selection().geom(2).set(z0);
            m.create("swe1", "Sweep");
            m.feature("swe1").selection().geom(3).set(concat(cham, ducts));
            m.feature("swe1").selection("sourceface").geom(2).set(z0);
            m.feature("swe1").selection("targetface").geom(2).set(z1);
            m.create("dis1", "Distribution");
            m.feature("dis1").selection().geom("geom1", 1).set(vert);
            m.feature("dis1").set("numelem", "10");
            m.run();
            stats(m, "E2");
        } catch (Throwable e) {
            System.out.println("  E2 FAILED: " + e.getMessage());
        }
    }

    static void e4() {
        h("E4: z-sweep of fluid with source = FULL z=0 section (annulus + cell disc)");
        Model model = build("Ex2MeshEFE4");
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        int[] z1 = model.selection("geom1_selZ1").entities(2);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] ducts = model.selection("geom1_selDuct").entities(3);
        int[] cell = model.selection("geom1_selCell").entities(3);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.create("ftri1", "FreeTri");
            m.feature("ftri1").selection().geom(2).set(z0);
            m.create("swe1", "Sweep");
            m.feature("swe1").selection().geom(3).set(concat(cham, ducts));
            m.feature("swe1").selection("sourceface").geom(2).set(z0);
            m.feature("swe1").selection("targetface").geom(2).set(z1);
            m.create("sweCell", "Sweep");
            m.feature("sweCell").selection().geom(3).set(cell);
            m.run();
            stats(m, "E4");
        } catch (Throwable e) {
            System.out.println("  E4 FAILED: " + e.getMessage());
        }
    }

    static void e5() {
        h("E5: x-sweep of chamber (src x=-30 wall, tgt x=+30 wall) + z-sweep of cell");
        Model model = build("Ex2MeshEFE5");
        int[] xm = model.selection("geom1_selXm").entities(2);
        int[] xp = model.selection("geom1_selXp").entities(2);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] ducts = model.selection("geom1_selDuct").entities(3);
        int[] cell = model.selection("geom1_selCell").entities(3);
        int[] cellBot = model.selection("geom1_selCellBot").entities(2);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.3");
            m.create("ftriC", "FreeTri");
            m.feature("ftriC").selection().geom(2).set(cellBot);
            m.create("sweCell", "Sweep");
            m.feature("sweCell").selection().geom(3).set(cell);
            m.feature("sweCell").selection("sourceface").geom(2).set(cellBot);
            m.create("ftri1", "FreeTri");
            m.feature("ftri1").selection().geom(2).set(xm);
            m.create("swe1", "Sweep");
            m.feature("swe1").selection().geom(3).set(cham);
            m.feature("swe1").selection("sourceface").geom(2).set(xm);
            m.feature("swe1").selection("targetface").geom(2).set(xp);
            m.create("ftetD", "FreeTet");
            m.feature("ftetD").selection().geom(3).set(ducts);
            m.run();
            stats(m, "E5");
        } catch (Throwable e) {
            System.out.println("  E5 FAILED: " + e.getMessage());
        }
    }

    static int[] concat(int[] a, int[] b) {
        int[] r = new int[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
