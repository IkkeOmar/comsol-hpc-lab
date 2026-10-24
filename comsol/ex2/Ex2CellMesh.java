/* Ex2CellMesh.java -- find a working mesh recipe for the 100 um cell layer.
 *
 * The cell layer is a cylinder r=25 mm, h=0.1 mm at (0,0,-0.1) i.e. z in [-0.1, 0],
 * directly under the chamber floor (z=0).
 *
 * Tried recipes (each on its own mesh sequence, all on the FULL fluid+cell geometry):
 *  C1  FreeTet on the cell domain only (fluid meshed by Sweep)  -> needs a separate sequence
 *  C2  FreeTet on the whole geometry, hmax on the cell domain = 0.1 mm
 *  C3  Sweep downward on the cell domain, source = cell BOTTOM face (z=-0.1), target = z=0
 *  C4  Sweep downward on the cell domain, source = cell bottom, Distribution on the
 *      cell domain (not edges)
 *  C5  FreeTet on the cell domain with a Size feature giving hmax=0.05 mm
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2CellMesh {

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
        box(g, "selCham", 3, "inside", -30.1, 30.1, -30.1, 30.1, -0.05, 1.2);
        box(g, "selDuct", 3, "inside", -75.1, 75.1, -30.1, 30.1, -0.05, 1.2);
        box(g, "selCell", 3, "inside", -25.1, 25.1, -25.1, 25.1, -0.15, 0.05);
        box(g, "selZ0", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, -0.005, 0.005);
        box(g, "selZ1", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, 0.995, 1.005);
        box(g, "selCellBot", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.105, -0.095);
        g.run();
        return model;
    }

    public static void main(String[] args) {
        try {
            c3();
            c4();
            c5();
            c1();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println();
        System.out.println("EX2CELLMESH DONE");
    }

    // fluid sweep is the same in all recipes; the cell layer mesh differs
    static void prepFluid(MeshSequence m, Model model) {
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        int[] z1 = model.selection("geom1_selZ1").entities(2);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] ducts = model.selection("geom1_selDuct").entities(3);
        int[] vert = model.selection("geom1_selVert").entities(1);
        m.create("size1", "Size");
        m.feature("size1").set("hmax", "2.5");
        m.feature("size1").set("hmin", "0.05");
        m.feature("size1").set("hgrad", "1.3");
        m.create("ftri1", "FreeTri");
        m.feature("ftri1").selection().geom(2).set(z0);
        m.create("swe1", "Sweep");
        m.feature("swe1").selection().geom(3).set(concat(cham, ducts));
        m.feature("swe1").selection("sourceface").geom(2).set(z0);
        m.feature("swe1").selection("targetface").geom(2).set(z1);
        m.create("dis1", "Distribution");
        m.feature("dis1").selection().geom("geom1", 1).set(vert);
        m.feature("dis1").set("numelem", "10");
    }

    static void c3() {
        h("C3: Sweep cell downward, source = cell BOTTOM (z=-0.1), target = z=0");
        Model model = build("Ex2CellMeshC3");
        int[] cell = model.selection("geom1_selCell").entities(3);
        int[] cellBot = model.selection("geom1_selCellBot").entities(2);
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            prepFluid(m, model);
            m.create("ftri2", "FreeTri");
            m.feature("ftri2").selection().geom(2).set(cellBot);
            m.create("swe2", "Sweep");
            m.feature("swe2").selection().geom(3).set(cell);
            m.feature("swe2").selection("sourceface").geom(2).set(cellBot);
            m.run();
            stats(m, "C3");
        } catch (Throwable e) {
            System.out.println("  C3 FAILED: " + e.getMessage());
        }
    }

    static void c4() {
        h("C4: Sweep cell downward + Distribution on the CELL DOMAIN");
        Model model = build("Ex2CellMeshC4");
        int[] cell = model.selection("geom1_selCell").entities(3);
        int[] cellBot = model.selection("geom1_selCellBot").entities(2);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            prepFluid(m, model);
            m.create("ftri2", "FreeTri");
            m.feature("ftri2").selection().geom(2).set(cellBot);
            m.create("swe2", "Sweep");
            m.feature("swe2").selection().geom(3).set(cell);
            m.feature("swe2").selection("sourceface").geom(2).set(cellBot);
            m.feature("swe2").create("dis2", "Distribution");
            m.feature("swe2").feature("dis2").selection().geom("geom1", 3).set(cell);
            m.feature("swe2").feature("dis2").set("numelem", "5");
            m.run();
            stats(m, "C4");
        } catch (Throwable e) {
            System.out.println("  C4 FAILED: " + e.getMessage());
        }
    }

    static void c5() {
        h("C5: FreeTet on the whole geometry with a fine Size on the cell domain");
        Model model = build("Ex2CellMeshC5");
        int[] cell = model.selection("geom1_selCell").entities(3);
        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] ducts = model.selection("geom1_selDuct").entities(3);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.02");
            m.feature("size1").set("hgrad", "1.3");
            m.create("sizeC", "Size");
            m.feature("sizeC").selection().geom(3).set(cell);
            m.feature("sizeC").set("hmax", "0.2");
            m.feature("sizeC").set("hmin", "0.02");
            m.create("ftet1", "FreeTet");
            m.feature("ftet1").selection().geom(3).set(concat(concat(cham, ducts), cell));
            m.run();
            stats(m, "C5 FreeTet whole");
        } catch (Throwable e) {
            System.out.println("  C5 FAILED: " + e.getMessage());
        }
    }

    static void c1() {
        h("C1: Sweep fluid + separate FreeTet mesh sequence for the cell");
        Model model = build("Ex2CellMeshC1");
        int[] cell = model.selection("geom1_selCell").entities(3);
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            prepFluid(m, model);
            m.run();
            stats(m, "C1 fluid only");
            MeshSequence m2 = model.component("comp1").mesh().create("mesh2", "geom1");
            m2.create("sizeC", "Size");
            m2.feature("sizeC").selection().geom(3).set(cell);
            m2.feature("sizeC").set("hmax", "0.2");
            m2.feature("sizeC").set("hmin", "0.02");
            m2.create("ftet1", "FreeTet");
            m2.feature("ftet1").selection().geom(3).set(cell);
            m2.run();
            stats(m2, "C1 cell FreeTet");
        } catch (Throwable e) {
            System.out.println("  C1 FAILED: " + e.getMessage());
        }
    }

    static int[] concat(int[] a, int[] b) {
        int[] r = new int[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
