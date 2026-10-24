/* Ex2CellProbe.java -- why is the 100 um cell layer under-resolved?
 *
 * In the ef run the cell profile was wrong: c_midplane / c_surface = 0.425
 * while the reaction-diffusion shape requires cosh(m Lc/2)/cosh(m Lc) = 0.201
 * with m*Lc = 3.2733.  The suspicion is that the Sweep of the cell layer got
 * NO vertical Distribution, because the BoxSelection "selCellVert" returned
 * 0 edges.
 *
 * The probe:
 *   1. builds the ef geometry,
 *   2. lists every edge with its end points and flags the ones that are
 *      vertical and touch the cell cylinder,
 *   3. tries several ways of putting a Distribution on the cell Sweep and
 *      reports the resulting element count and the number of layers through
 *      the 100 um layer.
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2CellProbe {

    static Model model;

    static void p(String s) { System.out.println(s); }

    static void buildGeom() {
        model = ModelUtil.create("Ex2CellProbe");
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
        p("GEOM domains=" + g.getNDomains() + " boundaries=" + g.getNBoundaries()
          + " edges=" + g.getNEdges() + " verts=" + g.getNVertices());

        // Dump the EDGES of the cell cylinder using the Selection API, which
        // is verified to work (geometry.getEdgeVertices / getAdj(int,int,int[])
        // do not exist in 6.4).
        p("---- CELL CYLINDER EDGES ----");
        g.create("selCellEdge", "BoxSelection");
        g.feature("selCellEdge").set("entitydim", 1);
        g.feature("selCellEdge").set("condition", "inside");
        g.feature("selCellEdge").set("xmin", "-25.2"); g.feature("selCellEdge").set("xmax", "25.2");
        g.feature("selCellEdge").set("ymin", "-25.2"); g.feature("selCellEdge").set("ymax", "25.2");
        g.feature("selCellEdge").set("zmin", "-0.15"); g.feature("selCellEdge").set("zmax", "0.05");
        g.run();
        int[] ce = model.selection("geom1_selCellEdge").entities(1);
        p("edges inside the cell footprint = " + Arrays.toString(ce) + "  (n=" + ce.length + ")");

        // ALL edges of the geometry, to see what shapes exist
        g.create("selAllEdge", "BoxSelection");
        g.feature("selAllEdge").set("entitydim", 1);
        g.feature("selAllEdge").set("condition", "inside");
        g.feature("selAllEdge").set("xmin", "-100"); g.feature("selAllEdge").set("xmax", "100");
        g.feature("selAllEdge").set("ymin", "-100"); g.feature("selAllEdge").set("ymax", "100");
        g.feature("selAllEdge").set("zmin", "-1"); g.feature("selAllEdge").set("zmax", "2");
        g.run();
        int[] ae = model.selection("geom1_selAllEdge").entities(1);
        p("ALL edges = " + Arrays.toString(ae) + "  (n=" + ae.length + ")");
    }

    /** count how many prism layers the cell sweep produced: number of
     *  elements in the cell domain divided by the number of triangles on its
     *  bottom face. */
    static void reportMesh(String label) {
        try {
            MeshSequence m = model.component("comp1").mesh("mesh1");
            p(label + " numElem=" + m.stat().getNumElem()
              + " types=" + Arrays.toString(m.stat().getTypes()));
            // elements of the cell domain: use the mesh statistics per entity
            int[] cellD = model.selection("geom1_selCell").entities(3);
            p(label + " cell domains = " + Arrays.toString(cellD));
            try {
                int[][] pr = m.getElem("prism");
                int[][] te = m.getElem("tet");
                p(label + "   prism elements = " + (pr == null ? 0 : pr.length)
                  + "  tet elements = " + (te == null ? 0 : te.length));
            } catch (Throwable t) {
                p(label + "   elem count failed: " + t.getMessage());
            }
        } catch (Throwable t) {
            p(label + " mesh report failed: " + t.getMessage());
        }
    }

    static void mesh(boolean withDist, int num, boolean topLevel) {
        model.component("comp1").mesh().create("mesh1", "geom1");
        MeshSequence m = model.component("comp1").mesh("mesh1");
        m.create("sizeG", "Size");
        m.feature("sizeG").set("hmax", "2.5");
        m.feature("sizeG").set("hmin", "0.05");
        m.feature("sizeG").set("hgrad", 1.25);
        m.create("sizeDuct", "Size");
        m.feature("sizeDuct").selection().geom("geom1", 3)
             .set(model.selection("geom1_selDuctW").entities(3));
        m.feature("sizeDuct").set("hmax", "0.4");
        m.feature("sizeDuct").set("hmin", "0.05");
        m.create("ftet1", "FreeTet");
        m.feature("ftet1").selection().geom(3)
             .set(model.selection("geom1_selFluid").entities(3));
        m.create("sweCell", "Sweep");
        m.feature("sweCell").selection().geom(3).set(model.selection("geom1_selCell").entities(3));
        m.feature("sweCell").selection("sourceface").geom(2)
             .set(model.selection("geom1_selCellTop").entities(2));
        m.feature("sweCell").selection("targetface").geom(2)
             .set(model.selection("geom1_selCellBot").entities(2));
        if (withDist) {
            if (topLevel) {
                m.create("dis2", "Distribution");
                m.feature("dis2").selection().geom("geom1", 1).set(cellVerticalEdges());
                m.feature("dis2").set("numelem", String.valueOf(num));
            } else {
                m.feature("sweCell").create("dis2", "Distribution");
                m.feature("sweCell").feature("dis2").selection().geom("geom1", 1).set(cellVerticalEdges());
                m.feature("sweCell").feature("dis2").set("numelem", String.valueOf(num));
            }
        }
        m.run();
    }

    static int[] cellVerticalEdges() {
        int[] r = model.selection("geom1_selCellEdge").entities(1);
        p("cellVerticalEdges() -> " + Arrays.toString(r));
        return r;
    }

    public static void main(String[] args) {
        try {
            buildGeom();

            // selections used by the mesh
            GeomSequence g = model.component("comp1").geom("geom1");
            box(g, "selCell", 3, "inside", -25.1, 25.1, -25.1, 25.1, -0.15, 0.05);
            box(g, "selCellBot", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.105, -0.095);
            box(g, "selCellTop", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.005, 0.005);
            box(g, "selDuctW", 3, "inside", -75.1, -29.9, -1.1, 1.1, -0.05, 1.2);
            box(g, "selFluid", 3, "inside", -75.1, 75.1, -30.1, 30.1, -0.05, 1.2);
            g.run();
            p("selCell      = " + Arrays.toString(model.selection("geom1_selCell").entities(3)));
            p("selCellBot   = " + Arrays.toString(model.selection("geom1_selCellBot").entities(2)));
            p("selCellTop   = " + Arrays.toString(model.selection("geom1_selCellTop").entities(2)));
            p("selFluid     = " + Arrays.toString(model.selection("geom1_selFluid").entities(3)));

            p("######## A: sweep with NO distribution ########");
            try { mesh(false, 0, false); reportMesh("A"); }
            catch (Throwable t) { p("A FAILED: " + t.getMessage()); }

            p("######## B: sub-feature Distribution, 20 layers ########");
            try { mesh(true, 20, false); reportMesh("B"); }
            catch (Throwable t) { p("B FAILED: " + t.getMessage()); }

            p("######## C: top-level Distribution, 20 layers ########");
            try { mesh(true, 20, true); reportMesh("C"); }
            catch (Throwable t) { p("C FAILED: " + t.getMessage()); }

            p("EX2CELLPROBE DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("EX2CELLPROBE FAILED: " + e);
        }
        p("EX2CELLPROBE EXIT");
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
}
