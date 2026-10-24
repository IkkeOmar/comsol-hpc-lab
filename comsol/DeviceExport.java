import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;

/**
 * DeviceExport: COMSOL's own picture of each device, for the report.
 *
 *  Why this class and not the old GeomExport: the Image export feature has
 *  a `sourcetype` property whose value `geometry` makes COMSOL render the
 *  GEOMETRY ITSELF (Programming Reference Manual p. 862: "The type of source
 *  for the image: A plot group, the geometry, or some other type (such as a
 *  selection or mesh)").  That is exactly the picture the report needs.
 *
 *  WHY THE EARLIER EXPORT LOOKED UNREADABLE: the Image export feature has
 *  `options2d` / `options3d`, and BOTH DEFAULT TO OFF.  With them off, COMSOL
 *  exports the bare canvas: no axes, no legend, no title - which is what the
 *  blank-looking images were.  Setting options2d/options3d to "on" turns the
 *  axes (axes2d/axes3d), the legend (legend2d/legend3d) and the title
 *  (title2d/title3d) on.  `zoomextents` frames the geometry, `fontsize`
 *  sets the text size.
 *
 *  Two variants are produced per device, both attempted in order and both
 *  guarded so a rejected property cannot kill the whole run:
 *    1. geometry source  - COMSOL's clean render of the built geometry
 *    2. mesh plot group  - the same geometry with the mesh drawn on it
 *
 *  Each geometry mirrors the geometry of the actual exercise model:
 *  Ex1 <- comsol/Ex1_HFilter.java, Ex2 <- comsol/ex2/Ex2Chamber.java,
 *  Ex3 <- the electrode/particle arrangement of assignment page 9,
 *  Ex4 <- comsol/Ex4_FFF_prod.java.
 */
public class DeviceExport {

    /** Keep the model itself: the .mph is what a reader opens to check the
     *  geometry, the selections and the mesh behind the pictures. */

    /**
     * Font size that makes the exported text legible on the printed page.
     *
     * The PNG is placed at 0.85\textwidth = 304.6 pt and COMSOL's image
     * export is 96 dpi, so a cap height of c px in the image is
     * c * 304.6 / width pt on the page.  A COMSOL font of f pt has a cap
     * height of f * (96/72) * 0.7 px, and the repo's audit convention counts
     * the EM size (cap / 0.7), so requiring 7.5 pt of effective em gives
     *
     *     f = 7.5 * width / ((96/72) * 304.6)
     *
     * With COMSOL's default 13 pt font and a 1600 px export that lands the
     * text at 2.9 pt on the page, which is unreadable in print.  Measured on
     * 2026-10-24: the colorbar digits are 12 px tall in the 1800 px export,
     * i.e. 2.03 pt on the page.
     */
    static int fontFor(int w) {
        return (int) Math.ceil(7.5 * w / ((96.0 / 72.0) * 304.6));
    }
    static void saveMph(Model model, String name) {
        try {
            String path = "<project>/comsol/out/"
                        + name + ".mph";
            model.save(path);
            System.out.println("  [mph] saved " + path);
        } catch (Throwable t) {
            System.out.println("  [mph] save failed for " + name + ": " + t);
        }
    }

    /** COMSOL's own render of the geometry from a chosen camera direction.
     *  The camera API (position/target/up) is verified in the shipped
     *  com.comsol.help.models.acdc.induction_heating_curie_movement example. */
    static boolean exportGeometryCamera(Model model, String tag, String fname,
                                         int w, int h,
                                         double[] campos, double[] target, double[] up) {
        try {
            String e = "gc_" + tag;
            model.result().export().create(e, "Image");
            model.result().export(e).set("sourcetype", "geometry");
            model.result().export(e).set("sourceobject", "geom1");
            model.result().export(e).set("imagetype", "png");
            model.result().export(e).set("pngfilename", fname);
            model.result().export(e).set("target", "file");
            model.result().export(e).set("size", "manualweb");
            model.result().export(e).set("width", String.valueOf(w));
            try {
                model.result().export(e).set("fontsize",
                        String.valueOf(fontFor(w)));
            } catch (Throwable t) {
                System.out.println("    (fontsize skipped: " + t.getMessage() + ")");
            }
            model.result().export(e).set("height", String.valueOf(h));
            model.result().export(e).set("lockratio", "off");
            trySet(model, e, "options2d", "on");
            trySet(model, e, "options3d", "on");
            trySet(model, e, "fontsize", "28");
            trySet(model, e, "background", "color");
            trySetColor(model, e, "customcolor", new double[]{1.0, 1.0, 1.0});
            trySet(model, e, "zoomextents", "on");
            String view = "view1";
            try {
                String[] vt = model.component("comp1").view().tags();
                if (vt.length > 0) view = vt[0];
            } catch (Throwable t) { }
            model.component("comp1").view(view).camera()
                 .set("position", new double[]{campos[0], campos[1], campos[2]});
            model.component("comp1").view(view).camera()
                 .set("target", new double[]{target[0], target[1], target[2]});
            model.component("comp1").view(view).camera()
                 .set("up", new double[]{up[0], up[1], up[2]});
            model.result().export(e).run();
            System.out.println("  [" + tag + "] camera geometry export OK -> " + fname);
            return true;
        } catch (Throwable t) {
            System.out.println("  [" + tag + "] camera geometry export FAILED: " + t);
            return false;
        }
    }

    static String outDir;

    /** Set one optional export property; a name this build does not know is
     *  reported and skipped instead of aborting the whole export. */
    static void trySet(Model model, String exportTag, String prop, String val) {
        try {
            model.result().export(exportTag).set(prop, val);
        } catch (Throwable t) {
            System.out.println("    (skipped export property '" + prop
                             + "': " + t.getMessage() + ")");
        }
    }

    static void trySetColor(Model model, String exportTag, String prop, double[] rgb) {
        try {
            model.result().export(exportTag).set(prop, rgb);
        } catch (Throwable t) {
            System.out.println("    (skipped export property '" + prop
                             + "': " + t.getMessage() + ")");
        }
    }

    // -----------------------------------------------------------------
    // geometry-source export (COMSOL renders the geometry itself)
    // -----------------------------------------------------------------
    static boolean exportGeometrySource(Model model, String tag, String fname,
                                        int w, int h, double[] zoom) {
        try {
            String e = "ge_" + tag;
            model.result().export().create(e, "Image");
            model.result().export(e).set("sourcetype", "geometry");
            model.result().export(e).set("sourceobject", "geom1");
            model.result().export(e).set("imagetype", "png");
            model.result().export(e).set("pngfilename", fname);
            model.result().export(e).set("target", "file");
            model.result().export(e).set("size", "manualweb");
            model.result().export(e).set("width", String.valueOf(w));
            try {
                model.result().export(e).set("fontsize",
                        String.valueOf(fontFor(w)));
            } catch (Throwable t) {
                System.out.println("    (fontsize skipped: " + t.getMessage() + ")");
            }
            model.result().export(e).set("height", String.valueOf(h));
            model.result().export(e).set("lockratio", "off");
            // Each optional property is set through trySet: the Image export
            // feature accepts a different subset on this build (there is no
            // "axes3d"), and one unknown name aborts the whole export.
            trySet(model, e, "options2d", "on");
            trySet(model, e, "options3d", "on");
            trySet(model, e, "axes2d", "on");
            trySet(model, e, "legend2d", "on");
            trySet(model, e, "legend3d", "on");
            trySet(model, e, "title2d", "on");
            trySet(model, e, "title3d", "on");
            trySet(model, e, "fontsize", "28");
            model.result().export(e).set("background", "color");
            model.result().export(e).set("customcolor",
                new double[]{1.0, 1.0, 1.0});
            if (zoom != null) {
                try {
                    model.component("comp1").view("view1").axis().set("xmin", zoom[0]);
                    model.component("comp1").view("view1").axis().set("xmax", zoom[1]);
                    model.component("comp1").view("view1").axis().set("ymin", zoom[2]);
                    model.component("comp1").view("view1").axis().set("ymax", zoom[3]);
                    trySet(model, e, "zoomextents", "off");
                } catch (Throwable t) {
                    System.out.println("    (view zoom skipped: " + t.getMessage() + ")");
                }
            } else {
                trySet(model, e, "zoomextents", "on");
            }
            model.result().export(e).run();
            System.out.println("  [" + tag + "] geometry-source export OK -> " + fname);
            return true;
        } catch (Throwable t) {
            System.out.println("  [" + tag + "] geometry-source export FAILED: " + t);
            return false;
        }
    }

    // -----------------------------------------------------------------
    // mesh-plot-group export (geometry + the mesh COMSOL built on it)
    // -----------------------------------------------------------------
    static boolean exportMeshPlot(Model model, String tag, String fname,
                                   String title, boolean threeD, int w, int h,
                                   double[] zoom) {
        try {
            model.component("comp1").mesh().create("mesh1", "geom1");
            model.component("comp1").mesh("mesh1").autoMeshSize(threeD ? 7 : 6);
            model.component("comp1").mesh("mesh1").run();
            if (zoom != null) {
                try {
                    model.component("comp1").view("view1").axis().set("xmin", zoom[0]);
                    model.component("comp1").view("view1").axis().set("xmax", zoom[1]);
                    model.component("comp1").view("view1").axis().set("ymin", zoom[2]);
                    model.component("comp1").view("view1").axis().set("ymax", zoom[3]);
                    System.out.println("  [" + tag + "] view axis set to "
                                     + java.util.Arrays.toString(zoom));
                } catch (Throwable t) {
                    System.out.println("    (view axis set failed: " + t.getMessage() + ")");
                }
            }
            System.out.println("  [" + tag + "] mesh elements = "
                             + model.component("comp1").mesh("mesh1").getNumElem());

            model.result().dataset().create("dsmesh", "Mesh");
            model.result().dataset("dsmesh").set("mesh", "mesh1");

            String pg = "pg_" + tag;
            model.result().create(pg, threeD ? "PlotGroup3D" : "PlotGroup2D");
            model.result(pg).label(title);
            model.result(pg).set("data", "dsmesh");
            model.result(pg).create("msh1", "Mesh");
            // Tie the plot group to the component view: without this COMSOL
            // auto-fits the axes and any axis limit we set is ignored.
            try {
                model.result(pg).set("view", "view1");
            } catch (Throwable t) {
                System.out.println("    (pg view not settable: " + t.getMessage() + ")");
            }
            model.result(pg).set("titletype", "manual");
            model.result(pg).set("title", title);
            model.result(pg).run();

            String e = "me_" + tag;
            model.result().export().create(e, pg, "Image");
            model.result().export(e).set("pngfilename", fname);
            model.result().export(e).set("target", "file");
            model.result().export(e).set("size", "manualweb");
            model.result().export(e).set("width", String.valueOf(w));
            try {
                model.result().export(e).set("fontsize",
                        String.valueOf(fontFor(w)));
            } catch (Throwable t) {
                System.out.println("    (fontsize skipped: " + t.getMessage() + ")");
            }
            model.result().export(e).set("height", String.valueOf(h));
            model.result().export(e).set("lockratio", "off");
            trySet(model, e, "options2d", "on");
            trySet(model, e, "options3d", "on");
            trySet(model, e, "fontsize", "28");
            model.result().export(e).set("background", "color");
            model.result().export(e).set("customcolor",
                new double[]{1.0, 1.0, 1.0});
            trySet(model, e, "zoomextents", zoom == null ? "on" : "off");
            model.result().export(e).run();
            System.out.println("  [" + tag + "] mesh-plot export OK -> " + fname);
            return true;
        } catch (Throwable t) {
            System.out.println("  [" + tag + "] mesh-plot export FAILED: " + t);
            return false;
        }
    }

    // -----------------------------------------------------------------
    // EX1: straight 6 mm x 100 um channel with the mesh-control mid-line
    // -----------------------------------------------------------------
    static void ex1() {
        System.out.println("=== EX1 device ===");
        Model model = ModelUtil.create("DevEx1");
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
        System.out.println("  [ex1] domains=" + g.getNDomains()
                         + " boundaries=" + g.getNBoundaries());
        System.out.println("  [ex1] bbox_raw=" + java.util.Arrays.toString(g.getBoundingBox()));
        // full channel: the true 60:1 aspect of a 6 mm x 100 um channel
        exportGeometrySource(model, "ex1", outDir + "/dev_ex1_comsol.png", 1600, 300, null);
        // inlet detail: the first 600 um, where the mid-line is legible
        exportGeometrySource(model, "ex1in", outDir + "/dev_ex1_comsol_inlet.png",
                             1600, 620, new double[]{0.0, 600.0, -60.0, 60.0});
        // The mesh-control edge is a VIRTUAL edge: it has no geometry, so the
        // geometry-source render cannot show it. The mesh render does - COMSOL
        // meshes the two halves separately, so the mid-line appears as the
        // seam between the two element rows.
        exportMeshPlot(model, "ex1", outDir + "/dev_ex1_comsol_mesh.png",
                       "Ex1 device in COMSOL: 6000 x 100 um channel, mesh-control "
                       + "edge on the mid-line (zoomed to the first 600 um)",
                       false, 1600, 700, new double[]{0.0, 600.0, -60.0, 60.0});
        saveMph(model, "DeviceExport_DevEx1");
    }

    // -----------------------------------------------------------------
    // EX4: 1 mm channel with 45 coplanar electrodes on the floor
    // -----------------------------------------------------------------
    static void ex4() {
        System.out.println("=== EX4 device ===");
        Model model = ModelUtil.create("DevEx4");
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
        System.out.println("  [ex4] domains=" + g.getNDomains()
                         + " boundaries=" + g.getNBoundaries()
                         + " edges=" + g.getNEdges()
                         + " vertices=" + g.getNVertices());
        System.out.println("  [ex4] bbox_raw=" + java.util.Arrays.toString(g.getBoundingBox()));
        exportGeometrySource(model, "ex4", outDir + "/dev_ex4_comsol.png", 1600, 1000, null);
        // from BELOW: the 45 electrodes are coplanar with the floor, so this
        // is the view in which they are visible at all
        exportGeometryCamera(model, "ex4below", outDir + "/dev_ex4_comsol_below.png",
            1600, 1000,
            new double[]{500, 50, -700}, new double[]{500, 50, 0},
            new double[]{0, 1, 0});
        // and a three-quarter view that shows the channel and the electrode row
        exportGeometryCamera(model, "ex4ang", outDir + "/dev_ex4_comsol_angle.png",
            1600, 1000,
            new double[]{1400, -700, 500}, new double[]{450, 50, 50},
            new double[]{0, 0, 1});
        exportMeshPlot(model, "ex4", outDir + "/dev_ex4_comsol_mesh.png",
                       "Ex4 device in COMSOL: 1000 x 100 x 100 um channel, "
                       + "45 coplanar electrodes on the floor", true, 1600, 1000, null);
        saveMph(model, "DeviceExport_DevEx4");
    }

    // -----------------------------------------------------------------
    // EX2: chamber + crossing inlet channel + cylindrical cell layer
    // -----------------------------------------------------------------
    static void ex2() {
        System.out.println("=== EX2 device ===");
        Model model = ModelUtil.create("DevEx2");
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
        System.out.println("  [ex2] domains=" + g.getNDomains()
                         + " boundaries=" + g.getNBoundaries()
                         + " edges=" + g.getNEdges()
                         + " vertices=" + g.getNVertices());
        System.out.println("  [ex2] bbox_raw=" + java.util.Arrays.toString(g.getBoundingBox()));
        exportGeometrySource(model, "ex2", outDir + "/dev_ex2_comsol.png", 1600, 1100, null);
        exportGeometryCamera(model, "ex2ang", outDir + "/dev_ex2_comsol_angle.png",
            1600, 1100,
            new double[]{220000, -160000, 60000}, new double[]{0, 0, 400},
            new double[]{0, 0, 1});
        exportMeshPlot(model, "ex2", outDir + "/dev_ex2_comsol_mesh.png",
                       "Ex2 device in COMSOL: 60 x 60 x 1 mm chamber, 150 x 2 mm "
                       + "inlet channel, cylindrical cell layer below",
                       true, 1600, 1100, null);
        saveMph(model, "DeviceExport_DevEx2");
    }

    // -----------------------------------------------------------------
    // EX3: DEP electrode arrangement of assignment page 9
    // -----------------------------------------------------------------
    static void ex3() {
        System.out.println("=== EX3 device ===");
        Model model = ModelUtil.create("DevEx3");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 2);
        g.lengthUnit("um");
        g.create("fluid", "Rectangle");
        g.feature("fluid").set("size", new String[]{"80[um]", "95[um]"});
        g.feature("fluid").set("pos",  new String[]{"0", "5[um]"});
        // The assignment figure (page 9) draws the electrode array INSET from
        // the left end of the electrode plane: the first strip does not start
        // at the corner.  Measured on the assignment page image, the
        // strips sit on a 20 um pitch and the first one starts ~2.5 um from
        // the left edge of the drawn floor.  The particle in the assignment
        // sits above the gap between the third and the fourth strip.
        for (int i = 0; i < 4; i++) {
            String tag = "e" + i;
            g.create(tag, "Rectangle");
            g.feature(tag).set("size", new String[]{"10[um]", "5[um]"});
            g.feature(tag).set("pos",  new String[]{(2.5 + 20 * i) + "[um]", "0"});
        }
        g.create("part", "Circle");
        g.feature("part").set("r", "1.5[um]");
        g.feature("part").set("pos", new String[]{"55[um]", "55[um]"});
        g.run();
        System.out.println("  [ex3] domains=" + g.getNDomains()
                         + " boundaries=" + g.getNBoundaries());
        System.out.println("  [ex3] bbox_raw=" + java.util.Arrays.toString(g.getBoundingBox()));
        exportGeometrySource(model, "ex3", outDir + "/dev_ex3_comsol.png", 1500, 900, null);
        exportMeshPlot(model, "ex3", outDir + "/dev_ex3_comsol_mesh.png",
                       "Ex3 device in COMSOL: electrode strips of width d = 10 um, "
                       + "gaps d = 10 um, particle of radius 1.5 um above",
                       false, 1500, 900, null);
        saveMph(model, "DeviceExport_DevEx3");
    }


    // -----------------------------------------------------------------
    // 2D plan views: the parts of the 3D devices that the solid hides
    //   Ex2: the cell layer is BELOW the chamber floor, so it is invisible
    //        in the 3D render - its footprint is a 50 mm circle
    //   Ex4: the electrodes are coplanar with the channel floor, so they are
    //        hidden inside the solid - the workplane layout is the array
    // -----------------------------------------------------------------
    static void ex2Plan() {
        System.out.println("=== EX2 plan view ===");
        Model model = ModelUtil.create("DevEx2Plan");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 2);
        g.lengthUnit("mm");
        g.create("cham", "Rectangle");
        g.feature("cham").set("size", new String[]{"60", "60"});
        g.feature("cham").set("pos",  new String[]{"-30", "-30"});
        g.create("duct", "Rectangle");
        g.feature("duct").set("size", new String[]{"150", "2"});
        g.feature("duct").set("pos",  new String[]{"-75", "-1"});
        g.create("cell", "Circle");
        g.feature("cell").set("r", "25");
        g.feature("cell").set("pos", new String[]{"0", "0"});
        g.run();
        System.out.println("  [ex2plan] domains=" + g.getNDomains()
                         + " boundaries=" + g.getNBoundaries());
        System.out.println("  [ex2plan] bbox_raw=" + java.util.Arrays.toString(g.getBoundingBox()));
        exportGeometrySource(model, "ex2plan", outDir + "/dev_ex2_comsol_plan.png",
                             1500, 800, null);
        saveMph(model, "DeviceExport_DevEx2Plan");
    }

    static void ex4Plan() {
        System.out.println("=== EX4 plan view (workplane electrode layout) ===");
        Model model = ModelUtil.create("DevEx4Plan");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 2);
        g.lengthUnit("um");
        for (int i = 0; i < 45; i++) {
            String tag = "r" + i;
            g.create(tag, "Rectangle");
            g.feature(tag).set("size", new String[]{"10[um]", "100[um]"});
            g.feature(tag).set("pos", new String[]{"(50 + " + i + "*20)[um]", "0"});
        }
        g.run();
        System.out.println("  [ex4plan] domains=" + g.getNDomains()
                         + " boundaries=" + g.getNBoundaries());
        System.out.println("  [ex4plan] bbox_raw=" + java.util.Arrays.toString(g.getBoundingBox()));
        exportGeometrySource(model, "ex4plan", outDir + "/dev_ex4_comsol_plan.png",
                             1600, 700, null);
        saveMph(model, "DeviceExport_DevEx4Plan");
    }

    public static void main(String[] args) {
        try {
            // With `comsolbatch -methodcall run` and NO extra arguments the
            // args array is NULL, not empty: a plain args.length throws NPE.
            String dir = (args != null && args.length > 0) ? args[0]
                       : "<project>/figures";
            outDir = dir;
            System.out.println("output dir: " + outDir);
            ex1();
            ex2();
            ex2Plan();
            ex3();
            ex4();
            ex4Plan();
            System.out.println("\nDEVICEEXPORT DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("DEVICEEXPORT ABORTED: " + e);
        }
    }
}
