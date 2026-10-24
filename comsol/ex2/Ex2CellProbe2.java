/* Ex2CellProbe2.java -- find the vertical edges of the cell cylinder and get
 * a Distribution onto the cell Sweep.
 *
 * Facts established so far:
 *   - the cell Sweep in the ef model got NO vertical Distribution, because
 *     the BoxSelection "selCellVert" returned 0 edges.  Without it the
 *     100 um layer is one or two elements thick and the reaction-diffusion
 *     profile is far too flat (c_mid/c_surf = 0.425 instead of the analytic
 *     cosh(m*Lc/2)/cosh(m*Lc) = 0.201).
 *   - a wider BoxSelection (x,y = +/-25.2, z = -0.15 .. 0.05) DOES return
 *     16 edges.  This probe identifies which of them are the vertical seam
 *     edges of the cylinder and tests which Distribution call actually
 *     produces layers.
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2CellProbe2 {

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
        model = ModelUtil.create("Ex2CellProbe2");
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
          + " edges=" + g.getNEdges());
    }

    static void sel(String tag, int dim, String cond, double a, double b, double c, double d, double e, double f) {
        GeomSequence g = model.component("comp1").geom("geom1");
        g.create(tag, "BoxSelection");
        g.feature(tag).set("entitydim", dim);
        g.feature(tag).set("condition", cond);
        g.feature(tag).set("xmin", "" + a); g.feature(tag).set("xmax", "" + b);
        g.feature(tag).set("ymin", "" + c); g.feature(tag).set("ymax", "" + d);
        g.feature(tag).set("zmin", "" + e); g.feature(tag).set("zmax", "" + f);
        g.run();
    }

    /** number of distinct z levels of the prism elements of the cell domain,
     *  i.e. the number of layers through the 100 um layer. */
    static void reportLayers(String label) {
        try {
            MeshSequence m = model.component("comp1").mesh("mesh1");
            int[][] pr = m.getElem("prism");
            double[][] vx = m.getVertex();
            java.util.TreeSet<Double> zs = new java.util.TreeSet<Double>();
            int n = 0;
            for (int[] e : pr) {
                boolean inCell = true;
                double zmin = 1e9, zmax = -1e9;
                for (int v : e) {
                    double z = vx[v][2];
                    if (z < -0.09 || z > 0.01) { inCell = false; }
                    zmin = Math.min(zmin, z); zmax = Math.max(zmax, z);
                }
                if (inCell && (zmax - zmin) < 0.05) { n++; zs.add(Double.valueOf(Math.round(zmin * 1e6))); }
            }
            p(label + " numElem=" + m.stat().getNumElem()
              + "  cell prisms=" + n + "  distinct layer z [um]=" + zs);
        } catch (Throwable t) {
            p(label + " layer report failed: " + t.getMessage());
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
        m.feature("ftet1").selection().geom(3)
             .set(model.selection("geom1_selFluid").entities(3));
        m.create("sweCell", "Sweep");
        m.feature("sweCell").selection().geom(3).set(model.selection("geom1_selCell").entities(3));
        m.feature("sweCell").selection("sourceface").geom(2)
             .set(model.selection("geom1_selCellTop").entities(2));
        m.feature("sweCell").selection("targetface").geom(2)
             .set(model.selection("geom1_selCellBot").entities(2));

        int[] edges = model.selection("geom1_selVertE").entities(1);
        p(label0(variant) + " vertical candidate edges = " + Arrays.toString(edges));

        if (variant == 1) {
            // sub-feature Distribution with an explicit edge selection
            m.feature("sweCell").create("dis2", "Distribution");
            m.feature("sweCell").feature("dis2").selection().geom("geom1", 1).set(edges);
            m.feature("sweCell").feature("dis2").set("numelem", String.valueOf(num));
        } else if (variant == 2) {
            // sub-feature Distribution with NO explicit selection: COMSOL may
            // auto-fill it from the sweep source face
            m.feature("sweCell").create("dis2", "Distribution");
            m.feature("sweCell").feature("dis2").set("numelem", String.valueOf(num));
        } else if (variant == 3) {
            // top-level Distribution with an explicit edge selection
            m.create("dis2", "Distribution");
            m.feature("dis2").selection().geom("geom1", 1).set(edges);
            m.feature("dis2").set("numelem", String.valueOf(num));
        } else if (variant == 4) {
            // sub-feature, selection set via the integer-geometry overload
            m.feature("sweCell").create("dis2", "Distribution");
            m.feature("sweCell").feature("dis2").selection().geom(1).set(edges);
            m.feature("sweCell").feature("dis2").set("numelem", String.valueOf(num));
        }
        m.run();
    }

    static String label0(int v) {
        return "V" + v;
    }

    public static void main(String[] args) {
        try {
            buildGeom();
            // what conditions does a BoxSelection accept?
            GeomSequence gg = model.component("comp1").geom("geom1");
            gg.create("selT", "BoxSelection");
            gg.feature("selT").set("entitydim", 1);
            try {
                p("BoxSelection condition allowed = " + Arrays.toString(
                    gg.feature("selT").getAllowedPropertyValues("condition")));
            } catch (Throwable t) { p("condition read failed: " + t.getMessage()); }
            p("BoxSelection properties = " + Arrays.toString(gg.feature("selT").properties()).replace(", ", ",\n   "));

            // ---- identify the vertical seam edges of the cylinder ---------
            // A narrow box around (x=25, y=0) catches a vertical seam edge.
            sel("selSeamX", 1, "inside", 24.5, 25.5, -0.6, 0.6, -0.15, 0.05);
            p("edges near (25,0)   = " + Arrays.toString(model.selection("geom1_selSeamX").entities(1)));
            sel("selSeamY", 1, "inside", -0.6, 0.6, 24.5, 25.5, -0.15, 0.05);
            p("edges near (0,25)   = " + Arrays.toString(model.selection("geom1_selSeamY").entities(1)));
            // horizontal rims: an edge lying in the plane z = -0.1
            sel("selRimBot", 1, "inside", -25.2, 25.2, -25.2, 25.2, -0.105, -0.095);
            p("edges in the z=-0.1 plane = " + Arrays.toString(model.selection("geom1_selRimBot").entities(1)));
            sel("selRimTop", 1, "inside", -25.2, 25.2, -25.2, 25.2, -0.005, 0.005);
            p("edges in the z=0 plane = " + Arrays.toString(model.selection("geom1_selRimTop").entities(1)));
            // the candidate set used for the distribution: everything inside
            // the cell footprint
            sel("selVertE", 1, "inside", -25.2, 25.2, -25.2, 25.2, -0.15, 0.05);
            p("candidate set = " + Arrays.toString(model.selection("geom1_selVertE").entities(1)));

            // selections for the mesh
            sel("selCell", 3, "inside", -25.1, 25.1, -25.1, 25.1, -0.15, 0.05);
            sel("selCellBot", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.105, -0.095);
            sel("selCellTop", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.005, 0.005);
            sel("selFluid", 3, "inside", -75.1, 75.1, -30.1, 30.1, -0.05, 1.2);
            p("selCell=" + Arrays.toString(model.selection("geom1_selCell").entities(3))
              + " selCellBot=" + Arrays.toString(model.selection("geom1_selCellBot").entities(2))
              + " selCellTop=" + Arrays.toString(model.selection("geom1_selCellTop").entities(2))
              + " selFluid=" + Arrays.toString(model.selection("geom1_selFluid").entities(3)));

            for (int v = 1; v <= 4; v++) {
                p("######## variant " + v + " ########");
                try {
                    buildMesh(v, 20);
                    reportLayers("V" + v);
                } catch (Throwable t) {
                    p("V" + v + " FAILED: " + t.getMessage());
                }
            }
            p("EX2CELLPROBE2 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("EX2CELLPROBE2 FAILED: " + e);
        }
        p("EX2CELLPROBE2 EXIT");
    }
}
