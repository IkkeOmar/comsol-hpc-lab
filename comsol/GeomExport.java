import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/**
 * GeomExport: render the ACTUAL geometry COMSOL builds, for each of the four
 *  devices, and export it as a PNG, plus measure the built geometry.
 *
 *  Why this exists: the matplotlib device figures in scripts/draw_devices.py
 *  are drawn from the same PARAMETER VALUES as the models, which proves the
 *  numbers agree with each other but does NOT prove that COMSOL built the
 *  geometry those numbers describe.  These images are COMSOL's own render of
 *  its own geometry, so the two can be compared side by side, and the
 *  bounding box printed here is COMSOL's own measurement of it.
 *
 *  Each geometry below mirrors the geometry of the actual exercise model:
 *  Ex1  <- comsol/Ex1_HFilter.java
 *  Ex2  <- comsol/ex2/Ex2Chamber.java (same block decomposition + cell cylinder)
 *  Ex3  <- the electrode/particle arrangement of assignment page 9
 *  Ex4  <- comsol/Ex4_FFF_prod.java
 *
 *  WHY THE FIRST VERSION OF THIS FILE PRODUCED THREE IDENTICAL BLANK PNGs:
 *  the plot group had a "Mesh" plot feature but NO DATASET and NO MESH, so
 *  COMSOL had nothing to draw and exported an empty 1600x900 canvas - three
 *  times, byte for byte.  The working sequence (verified against the shipped
 *  example doc/help/.../submarine_cable_07_geom_mesh_3d.java) is:
 *      1. build the geometry and run it
 *      2. build and run a MESH sequence on that geometry
 *      3. create a "Mesh" DATASET pointing at that mesh
 *      4. put the plot group on that dataset and add the "Mesh" plot feature
 *      5. export the plot group as an Image
 *  Steps 2 and 3 are what was missing.
 *
 *  The bounding box comes from the geometry object itself
 *  (model.component(ctag).geom(tag).getBoundingBox(); Programming Reference
 *  Manual p. 271: "xmin, xmax, ymin, ymax, zmin, zmax"), not from the
 *  parameter values, so it is an independent measurement of what COMSOL built.
 */
public class GeomExport {

    static String outDir;

    // -----------------------------------------------------------------
    // measurement
    // -----------------------------------------------------------------
    static void reportGeom(String tag, GeomSequence g, double unitToUm) {
        System.out.println("  [" + tag + "] domains=" + g.getNDomains()
                         + " boundaries=" + g.getNBoundaries()
                         + " edges=" + g.getNEdges()
                         + " vertices=" + g.getNVertices());
        try {
            double[] bb = g.getBoundingBox();
            System.out.println("  [" + tag + "] bbox_raw=" + Arrays.toString(bb));
            StringBuilder sb = new StringBuilder();
            sb.append("  [").append(tag).append("] bbox_um");
            String[] ax = {"x", "y", "z"};
            for (int i = 0; i + 1 < bb.length && i / 2 < ax.length; i += 2) {
                sb.append(" ").append(ax[i / 2]).append("[")
                  .append(bb[i] * unitToUm).append(", ")
                  .append(bb[i + 1] * unitToUm).append("]");
            }
            System.out.println(sb.toString());
        } catch (Throwable e) {
            System.out.println("  [" + tag + "] bbox FAILED: " + e);
        }
    }

    // -----------------------------------------------------------------
    // render: mesh -> Mesh dataset -> plot group -> Image export
    // -----------------------------------------------------------------
    static void exportGeom(Model model, String tag, String fname,
                            String title, boolean threeD, int w, int h,
                            String xlabel, String ylabel) {
        try {
            // 2. mesh the geometry (coarse - this is a picture, not a solution)
            model.component("comp1").mesh().create("mesh1", "geom1");
            model.component("comp1").mesh("mesh1").autoMeshSize(threeD ? 7 : 6);
            model.component("comp1").mesh("mesh1").run();
            System.out.println("  [" + tag + "] mesh elements = "
                             + model.component("comp1").mesh("mesh1").getNumElem());

            // 3. a Mesh DATASET that points at that mesh
            model.result().dataset().create("dsmesh", "Mesh");
            model.result().dataset("dsmesh").set("mesh", "mesh1");

            // 4. plot group on that dataset + the Mesh plot feature
            String pg = "pg_" + tag;
            model.result().create(pg, threeD ? "PlotGroup3D" : "PlotGroup2D");
            model.result(pg).label(title);
            model.result(pg).set("data", "dsmesh");
            model.result(pg).create("msh1", "Mesh");
            model.result(pg).set("titletype", "manual");
            model.result(pg).set("title", title);
            if (!threeD) {
                // NOTE: "preserveaspect" is NOT a 2D plot-group property on this
                // build (Programming Reference p. 1003 lists it for 3D views);
                // setting it throws "Unknown property" and aborts the export.
                model.result(pg).set("xlabelactive", "on");
                model.result(pg).set("ylabelactive", "on");
                model.result(pg).set("xlabel", xlabel);
                model.result(pg).set("ylabel", ylabel);
            }
            model.result(pg).run();

            // 5. image export
            model.result().export().create("img_" + tag, pg, "Image");
            model.result().export("img_" + tag).set("pngfilename", fname);
            model.result().export("img_" + tag).set("size", "manualweb");
            model.result().export("img_" + tag).set("width", String.valueOf(w));
            model.result().export("img_" + tag).set("height", String.valueOf(h));
            model.result().export("img_" + tag).set("lockratio", "off");
            model.result().export("img_" + tag).run();
            System.out.println("  exported " + fname);
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("  [" + tag + "] EXPORT FAILED: " + e);
        }
    }

    // =====================================================================
    // EX1: 2D straight channel, symmetric about y = 0, with the mid-line
    //      (mirrors comsol/Ex1_HFilter.java: L_ch = 6000 um, w_ch = 100 um)
    // =====================================================================
    static void ex1() {
        System.out.println("=== EX1 geometry ===");
        Model model = ModelUtil.create("GeomEx1");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 2);
        g.lengthUnit("um");
        g.create("r1", "Rectangle");
        g.feature("r1").set("size", new String[]{"6000[um]", "100[um]"});
        g.feature("r1").set("pos",  new String[]{"0", "-50[um]"});
        g.create("ln1", "LineSegment");
        g.feature("ln1").set("specify1", "coord");
        g.feature("ln1").set("specify2", "coord");
        g.feature("ln1").set("coord1", new String[]{"0[um]", "0[um]"});
        g.feature("ln1").set("coord2", new String[]{"6000[um]", "0[um]"});
        g.run();
        g.create("mce1", "MeshControlEdges");
        g.feature("mce1").selection("input").set("ln1", 1);
        g.run("mce1");
        g.run();
        reportGeom("ex1", g, 1.0);
        exportGeom(model, "ex1", outDir + "/dev_ex1_comsol.png",
                   "Ex1 device as built in COMSOL: 6 mm x 100 um channel with the "
                   + "mesh control edge on the mid-line", false, 1600, 420,
                   "x (um)", "y (um)");
    }

    // =====================================================================
    // EX4: 3D channel with 45 coplanar electrodes on the floor
    //      (mirrors comsol/Ex4_FFF_prod.java: 1000 x 100 x 100 um,
    //       45 electrodes, 10 um wide, first at x = 50 um, pitch 20 um)
    // =====================================================================
    static void ex4() {
        System.out.println("=== EX4 geometry ===");
        Model model = ModelUtil.create("GeomEx4");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("um");
        g.create("blk1", "Block");
        g.feature("blk1").set("size", new String[]{"1000[um]", "100[um]", "100[um]"});
        g.feature("blk1").set("pos",  new String[]{"0", "0", "0"});
        g.create("wp1", "WorkPlane");
        g.feature("wp1").set("planetype", "quick");
        g.feature("wp1").set("quickplane", "xy");
        GeomSequence wp = g.feature("wp1").geom();
        for (int i = 0; i < 45; i++) {
            String tag = "r" + i;
            wp.create(tag, "Rectangle");
            wp.feature(tag).set("size", new String[]{"10[um]", "100[um]"});
            wp.feature(tag).set("pos", new String[]{"(50 + " + i + "*20)[um]", "0"});
        }
        g.run("wp1");
        g.run();
        reportGeom("ex4", g, 1.0);
        exportGeom(model, "ex4", outDir + "/dev_ex4_comsol.png",
                   "Ex4 device as built in COMSOL: 1000 x 100 x 100 um channel "
                   + "with 45 coplanar electrodes on the floor", true, 1600, 1000,
                   "x (um)", "y (um)");
    }

    // =====================================================================
    // EX2: 3D chamber, crossing inlet channel, cylindrical cell layer
    //      (mirrors comsol/ex2/Ex2Chamber.java: the chamber is split into the
    //       channel stripe and the two side regions so the ducts form their
    //       own domains; the cell cylinder sits below the chamber floor)
    // =====================================================================
    static void ex2() {
        System.out.println("=== EX2 geometry ===");
        Model model = ModelUtil.create("GeomEx2");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blkStrip", "Block");
        g.feature("blkStrip").set("size", new String[]{"60", "2", "1"});
        g.feature("blkStrip").set("pos",  new String[]{"-30", "-1", "0"});
        g.create("blkSideA", "Block");
        g.feature("blkSideA").set("size", new String[]{"60", "29", "1"});
        g.feature("blkSideA").set("pos",  new String[]{"-30", "-30", "0"});
        g.create("blkSideB", "Block");
        g.feature("blkSideB").set("size", new String[]{"60", "29", "1"});
        g.feature("blkSideB").set("pos",  new String[]{"-30", "1", "0"});
        g.create("blkW", "Block");
        g.feature("blkW").set("size", new String[]{"45", "2", "1"});
        g.feature("blkW").set("pos",  new String[]{"-75", "-1", "0"});
        g.create("blkE", "Block");
        g.feature("blkE").set("size", new String[]{"45", "2", "1"});
        g.feature("blkE").set("pos",  new String[]{"30", "-1", "0"});
        g.create("cylCell", "Cylinder");
        g.feature("cylCell").set("r", "25");
        g.feature("cylCell").set("h", "0.1");
        g.feature("cylCell").set("pos", new String[]{"0", "0", "-0.1"});
        g.run();
        reportGeom("ex2", g, 1000.0);
        exportGeom(model, "ex2", outDir + "/dev_ex2_comsol.png",
                   "Ex2 device as built in COMSOL: 60 x 60 x 1 mm chamber, "
                   + "150 x 2 mm inlet channel, cylindrical cell layer below",
                   true, 1600, 1000, "x (mm)", "y (mm)");
    }

    // =====================================================================
    // EX3: 2D cross-section of the DEP arrangement of assignment page 9:
    //      four coplanar electrode strips of width d = 10 um separated by
    //      gaps d = 10 um on the channel floor, a particle above them, and
    //      the flow along +x.
    // =====================================================================
    static void ex3() {
        System.out.println("=== EX3 geometry ===");
        Model model = ModelUtil.create("GeomEx3");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 2);
        g.lengthUnit("um");
        // fluid region above the electrode plane
        g.create("fluid", "Rectangle");
        g.feature("fluid").set("size", new String[]{"80[um]", "95[um]"});
        g.feature("fluid").set("pos",  new String[]{"0", "5[um]"});
        // four electrode strips on the floor: 10 um wide, 10 um gap
        for (int i = 0; i < 4; i++) {
            String tag = "e" + i;
            g.create(tag, "Rectangle");
            g.feature(tag).set("size", new String[]{"10[um]", "5[um]"});
            g.feature(tag).set("pos",  new String[]{(20 * i) + "[um]", "0"});
        }
        // particle 5 of the assignment table: radius 1500 nm
        g.create("part", "Circle");
        g.feature("part").set("r", "1.5[um]");
        g.feature("part").set("pos", new String[]{"35[um]", "55[um]"});
        g.run();
        reportGeom("ex3", g, 1.0);
        exportGeom(model, "ex3", outDir + "/dev_ex3_comsol.png",
                   "Ex3 device as built in COMSOL: electrode strips of width "
                   + "d = 10 um with gaps d = 10 um, particle of radius 1.5 um "
                   + "above the electrode plane", false, 1400, 800,
                   "x (um)", "y (um)");
    }

    public static void main(String[] args) {
        try {
            // NOTE: with `comsolbatch -methodcall run` and NO extra arguments,
            // the args array is NULL, not empty.  A plain `args.length` throws
            // NullPointerException ("Cannot read the array length because
            // "args" is null") and the whole export aborts before any geometry
            // is built.  Guard for null explicitly.
            String dir = (args != null && args.length > 0) ? args[0]
                       : "<project>/figures";
            outDir = dir;
            System.out.println("output dir: " + outDir);
            ex1();
            ex4();
            ex2();
            ex3();
            System.out.println("\nGEOMEXPORT DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("GEOMEXPORT ABORTED: " + e);
        }
    }
}
