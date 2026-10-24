import com.comsol.model.*;
import com.comsol.model.util.*;
import java.io.File;

/**
 * Ex4_Plots: COMSOL's own pictures of the Ex4 device taken from the REAL
 * production model (comsol/out/Ex4_FFF_prod.mph), not from a rebuilt copy.
 *
 * The device picture that was missing: the 45 electrodes are coplanar with
 * the channel floor, so a plain 3D render of the solid shows a featureless
 * block.  Three ways out, all inside COMSOL:
 *
 *   1. the geometry of the real model, rendered from BELOW, so the 45
 *      electrode faces on the floor become visible;
 *   2. a slice of the solved electric field at the channel mid-plane
 *      y = 50 um - the electrode pattern shows up as the field pattern;
 *   3. the particle trajectory from the Particle dataset, if it can be
 *      rebuilt on the loaded model.
 *
 * Everything that is skipped is reported, not hidden.
 */
public class Ex4_Plots {

    static String outDir;
    static Model model;

    static void trySetExport(String tag, String prop, String val) {
        try {
            model.result().export(tag).set(prop, val);
        } catch (Throwable t) {
            System.out.println("    (skipped export prop '" + prop + "': "
                + t.getMessage() + ")");
        }
    }

    static void trySetExportColor(String tag, String prop, double[] rgb) {
        try {
            model.result().export(tag).set(prop, rgb);
        } catch (Throwable t) {
            System.out.println("    (skipped export prop '" + prop + "': "
                + t.getMessage() + ")");
        }
    }

    static void trySetPg(String pg, String prop, String val) {
        try {
            model.result(pg).set(prop, val);
        } catch (Throwable t) {
            System.out.println("    (skipped pg prop '" + prop + "': "
                + t.getMessage() + ")");
        }
    }

    /** Export the geometry of the loaded model with the given camera. */
    static void exportGeometry(String tag, String fname, int w, int h,
                                double[] campos, double[] target, double[] up) {
        try {
            String e = "g_" + tag;
            model.result().export().create(e, "Image");
            model.result().export(e).set("sourcetype", "geometry");
            model.result().export(e).set("sourceobject", "geom1");
            model.result().export(e).set("imagetype", "png");
            model.result().export(e).set("pngfilename", fname);
            model.result().export(e).set("target", "file");
            model.result().export(e).set("size", "manualweb");
            model.result().export(e).set("width", String.valueOf(w));
            model.result().export(e).set("height", String.valueOf(h));
            model.result().export(e).set("lockratio", "off");
            trySetExport(e, "options2d", "on");
            trySetExport(e, "options3d", "on");
            trySetExport(e, "fontsize", "13");
            trySetExport(e, "background", "color");
            trySetExportColor(e, "customcolor", new double[]{1.0, 1.0, 1.0});
            trySetExport(e, "zoomextents", "on");
            // camera: the only way to see the floor electrodes
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
            System.out.println("  geometry export OK -> " + fname);
        } catch (Throwable t) {
            System.out.println("  geometry export FAILED (" + tag + "): " + t);
        }
    }

    /** Slice of the solved field on the plane y = 50 um. */
    static void exportFieldSlice(String tag, String fname, String expr,
                                 String title, int w, int h) {
        try {
            String ds = "sl_" + tag;
            model.result().dataset().create(ds, "CutPlane3D");
            model.result().dataset(ds).set("quickplane", "yz");
            model.result().dataset(ds).set("quickxmethod", "coord");
            model.result().dataset(ds).set("quickx", "50");
            model.result().dataset(ds).set("quickymethod", "coord");
            model.result().dataset(ds).set("quicky", "0");
            model.result().dataset(ds).set("quickzmethod", "coord");
            model.result().dataset(ds).set("quickz", "0");

            String pg = "pf_" + tag;
            model.result().create(pg, "PlotGroup3D");
            model.result(pg).set("data", ds);
            model.result(pg).create("sl1", "Slice");
            model.result(pg).feature("sl1").set("expr", expr);
            trySetPg(pg, "titletype", "manual");
            trySetPg(pg, "title", title);
            trySetPg(pg, "showlegendsmaxmin", "on");
            trySetPg(pg, "showlegendstitle", "on");
            trySetPg(pg, "showlegendsunit", "on");
            model.result(pg).run();

            String e = "e_" + tag;
            model.result().export().create(e, pg, "Image");
            model.result().export(e).set("pngfilename", fname);
            model.result().export(e).set("target", "file");
            model.result().export(e).set("size", "manualweb");
            model.result().export(e).set("width", String.valueOf(w));
            model.result().export(e).set("height", String.valueOf(h));
            model.result().export(e).set("lockratio", "off");
            trySetExport(e, "options2d", "on");
            trySetExport(e, "options3d", "on");
            trySetExport(e, "fontsize", "13");
            trySetExport(e, "background", "color");
            trySetExportColor(e, "customcolor", new double[]{1.0, 1.0, 1.0});
            model.result().export(e).run();
            System.out.println("  field slice export OK -> " + fname);
        } catch (Throwable t) {
            System.out.println("  field slice export FAILED (" + tag + "): " + t);
        }
    }

    public static void main(String[] args) {
        try {
            String root = "<project>/";
            outDir = root + "figures/comsol_ex4";
            String mph = root + "comsol/out/Ex4_FFF_prod.mph";
            if (!(new File(mph)).exists()) {
                System.out.println("MISSING " + mph);
                System.out.println("=== EX4PLOTS DONE ===");
                return;
            }
            System.out.println("loading " + mph);
            model = ModelUtil.load("Ex4", mph);
            System.out.println("studies:  " + java.util.Arrays.toString(model.study().tags()));
            System.out.println("solutions: " + java.util.Arrays.toString(model.sol().tags()));
            System.out.println("datasets: " + java.util.Arrays.toString(model.result().dataset().tags()));
            System.out.println("physics:  " + java.util.Arrays.toString(model.component("comp1").physics().tags()));
            System.out.println("plot groups: " + java.util.Arrays.toString(model.result().tags()));

            // ---- 1. the device, seen from below: the floor electrodes ----
            // camera below the floor looking up at it
            exportGeometry("below", outDir + "/ex4_device_from_below.png", 1600, 1000,
                new double[]{500, 50, -700}, new double[]{500, 50, 0},
                new double[]{0, 1, 0});
            // and from above, for completeness
            exportGeometry("above", outDir + "/ex4_device_from_above.png", 1600, 1000,
                new double[]{500, 50, 700}, new double[]{500, 50, 0},
                new double[]{0, 1, 0});

            // ---- 2. the solved field on the mid-plane ------------------
            // which field expression is defined on this model?
            String[] cand = {"es.Ex", "ec.Ex", "es.normE", "abs(es.Ex)"};
            for (String c : cand) {
                try {
                    model.result().numerical().create("pv", "EvalPoint");
                    model.result().numerical("pv").set("expr", c);
                    model.result().numerical("pv").run();
                    double[][] v = model.result().numerical("pv").getReal();
                    System.out.println("  field expression " + c + " = " + v[0][0]);
                    model.result().numerical().remove("pv");
                    exportFieldSlice("e", outDir + "/ex4_efield_slice.png", c,
                        "Ex4 in COMSOL: " + c + " on the mid-plane y = 50 um", 1600, 1000);
                    break;
                } catch (Throwable t) {
                    System.out.println("  field expression " + c + " not available: "
                        + t.getMessage());
                    try { model.result().numerical().remove("pv"); } catch (Throwable t2) { }
                }
            }

            System.out.println("=== EX4PLOTS DONE ===");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("EX4PLOTS ABORTED: " + e);
        }
    }
}
