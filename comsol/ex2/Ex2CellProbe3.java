/* Ex2CellProbe3.java -- how to put a vertical Distribution on the cell Sweep.
 *
 * Established:
 *   - BoxSelection conditions in 6.4: intersects | inside | somevertex | allvertices
 *   - the cell cylinder has vertical seam edges; a "somevertex" box around
 *     (x=25,y=0) picks them
 *   - a Distribution as a SUB-feature of a Sweep CANNOT take an edge
 *     selection ("Unsupported_geometric_entity_level") but CAN be created
 *     and given "numelem" without any selection (that variant ran)
 *   - a TOP-LEVEL Distribution with an edge selection runs but does NOT
 *     drive the Sweep: the cell layer still came out 1 layer thick
 *
 * This probe tries the remaining forms and counts the prism layers.
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2CellProbe3 {

    static Model model;

    static void p(String s) { System.out.println(s); }

    static void box(GeomSequence g, String tag, int dim, String cond,
                    double xmin, double xmax, double ymin, double ymax, double zmin, double zmax) {
        g.create(tag, "BoxSelection");
        g.feature(tag).set("entitydim", dim);
        g.feature(tag).set("condition", cond);
        g.feature(tag).set("xmin", "" + xmin); g.feature(tag).set("xmax", "" + xmax);
        g.feature(tag).set("ymin", "" + ymin); g.feature(tag).set("ymax", "" + ymax);
        g.feature(tag).set("zmin", "" + zmin); g.feature(tag).set("zmax", "" + zmax);
    }

    static void buildGeom() {
        model = ModelUtil.create("Ex2CellProbe3");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blkStrip", "Block");
        g.feature("blkStrip").set("size", new String[]{"60", "2", "1"});
        g.feature("blkStrip").set("pos", new String[]{"-30", "-1", "0"});
        g.create("blkSideA", "Block");
        g.feature("blkSideA").set("size", new String[]{"60", "29", "1"});
        g.feature("blkSideA").set("pos", new String[]{"-30", "-30", "0"});
        g.create("blkSideB", "Block");
        g.feature("blkSideB").set("size", new String[]{"60", "29", "1"});
        g.feature("blkSideB").set("pos", new String[]{"-30", "1", "0"});
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

        box(g, "selCell", 3, "inside", -25.1, 25.1, -25.1, 25.1, -0.15, 0.05);
        box(g, "selCellBot", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.105, -0.095);
        box(g, "selCellTop", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.005, 0.005);
        box(g, "selFluid", 3, "inside", -75.1, 75.1, -30.1, 30.1, -0.05, 1.2);
        box(g, "selVertA", 1, "somevertex", 24.9, 25.1, -0.1, 0.1, -0.15, 0.05);
        box(g, "selVertB", 1, "somevertex", -0.1, 0.1, 24.9, 25.1, -0.15, 0.05);
        g.run();
        p("cell=" + Arrays.toString(model.selection("geom1_selCell").entities(3))
          + " bot=" + Arrays.toString(model.selection("geom1_selCellBot").entities(2))
          + " top=" + Arrays.toString(model.selection("geom1_selCellTop").entities(2))
          + " vert=" + Arrays.toString(model.selection("geom1_selVertA").entities(1))
          + "+" + Arrays.toString(model.selection("geom1_selVertB").entities(1)));
    }

    static int countLayers(MeshSequence m) {
        try {
            int[][] pr = m.getElem("prism");
            double[][] vx = m.getVertex();
            java.util.TreeSet<Integer> zs = new java.util.TreeSet<Integer>();
            for (int[] e : pr) {
                double zmin = 1e9, zmax = -1e9;
                boolean inCell = true;
                for (int v : e) {
                    double z = vx[2][v];
                    if (z < -0.091 || z > 0.011) inCell = false;
                    zmin = Math.min(zmin, z); zmax = Math.max(zmax, z);
                }
                if (inCell && (zmax - zmin) < 0.05) zs.add(Integer.valueOf((int) Math.round(zmin * 1e6)));
            }
            p("   raw: prism count=" + pr.length + " vertex rows=" + vx.length
              + " vx[0].length=" + (vx.length > 0 ? vx[0].length : -1));
            int shown = 0;
            for (int[] e : pr) {
                StringBuilder sb = new StringBuilder();
                for (int v : e) sb.append(String.format("%.6f ", vx[2][v]));
                if (shown++ < 6) p("      prism z: " + sb.toString());
            }
            return zs.size();
        } catch (Throwable t) {
            p("   layer count failed: " + t.getMessage());
            return -1;
        }
    }

    static void buildMesh(int variant, int num) {
        try { model.component("comp1").mesh().remove("mesh1"); } catch (Throwable t) { }
        MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
        m.create("sizeG", "Size");
        m.feature("sizeG").set("hmax", "2.5");
        m.feature("sizeG").set("hmin", "0.05");
        m.feature("sizeG").set("hgrad", 1.25);
        m.create("ftet1", "FreeTet");
        m.feature("ftet1").selection().geom(3).set(model.selection("geom1_selFluid").entities(3));
        m.create("sweCell", "Sweep");
        m.feature("sweCell").selection().geom(3).set(model.selection("geom1_selCell").entities(3));
        m.feature("sweCell").selection("sourceface").geom(2)
             .set(model.selection("geom1_selCellTop").entities(2));
        m.feature("sweCell").selection("targetface").geom(2)
             .set(model.selection("geom1_selCellBot").entities(2));

        switch (variant) {
            case 1:  // sub-feature, NO selection, numelem only
                m.feature("sweCell").create("dis2", "Distribution");
                m.feature("sweCell").feature("dis2").set("numelem", String.valueOf(num));
                break;
            case 2:  // sub-feature, selection at FACE level (the source face)
                m.feature("sweCell").create("dis2", "Distribution");
                m.feature("sweCell").feature("dis2").selection().geom("geom1", 2)
                     .set(model.selection("geom1_selCellTop").entities(2));
                m.feature("sweCell").feature("dis2").set("numelem", String.valueOf(num));
                break;
            case 3:  // sub-feature, named selection of the vertical edges
                m.feature("sweCell").create("dis2", "Distribution");
                m.feature("sweCell").feature("dis2").selection().named("geom1_selVertA");
                m.feature("sweCell").feature("dis2").set("numelem", String.valueOf(num));
                break;
            case 4:  // sub-feature, selection at DOMAIN level (the swept domain)
                m.feature("sweCell").create("dis2", "Distribution");
                m.feature("sweCell").feature("dis2").selection().geom("geom1", 3)
                     .set(model.selection("geom1_selCell").entities(3));
                m.feature("sweCell").feature("dis2").set("numelem", String.valueOf(num));
                break;
            case 5:  // sub-feature with type=predefined and numelem
                m.feature("sweCell").create("dis2", "Distribution");
                try { m.feature("sweCell").feature("dis2").set("type", "predefined"); } catch (Throwable t) { }
                m.feature("sweCell").feature("dis2").set("numelem", String.valueOf(num));
                break;
            case 6:  // no distribution at all (reference)
                break;
            case 7:  // sub-feature, domain selection, numelem, with type=explicit
                m.feature("sweCell").create("dis2", "Distribution");
                m.feature("sweCell").feature("dis2").selection().geom("geom1", 3)
                     .set(model.selection("geom1_selCell").entities(3));
                try { m.feature("sweCell").feature("dis2").set("type", "explicit"); }
                catch (Throwable t) { p("   type=explicit failed: " + t.getMessage()); }
                m.feature("sweCell").feature("dis2").set("numelem", String.valueOf(num));
                break;
        }
        m.run();
    }

    public static void main(String[] args) {
        try {
            buildGeom();
            String[] names = {"sub/no-selection", "sub/face-selection", "sub/named-edge-selection",
                              "sub/domain-selection", "sub/type-predefined", "NO DISTRIBUTION",
                              "sub/domain/explicit"};
            for (int v = 1; v <= 7; v++) {
                p("######## variant " + v + " (" + names[v - 1] + ") ########");
                try {
                    buildMesh(v, 20);
                    MeshSequence m = model.component("comp1").mesh("mesh1");
                    p("V" + v + " numElem=" + m.stat().getNumElem()
                      + "  cell layers=" + countLayers(m));
                } catch (Throwable t) {
                    p("V" + v + " FAILED: " + t.getMessage());
                }
            }
            p("EX2CELLPROBE3 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("EX2CELLPROBE3 FAILED: " + e);
        }
        p("EX2CELLPROBE3 EXIT");
    }
}
