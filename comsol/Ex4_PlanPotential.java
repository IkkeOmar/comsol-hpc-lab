import com.comsol.model.*;
import com.comsol.model.util.*;

/**
 * Ex4 electrode-array plan view, taken from the REAL solved production model.
 *
 * Why this exists
 * ---------------
 * The device figure "dev_ex4_comsol_plan.png" is a COMSOL GEOMETRY render
 * (comsol/DeviceExport.java), so every electrode is drawn in the same grey.
 * It shows the 45 strips, their 10 um width, their 20 um pitch and their
 * first position at x = 50 um -- but it cannot show WHICH electrodes are at
 * 10 V and which are grounded, because a geometry render has no physics.
 *
 * This class renders the same view from the SOLVED production model
 * comsol/out/Ex4_FFF_prod.mph: a horizontal cut plane through the electrode
 * array, coloured by the electric potential.  The 23 Terminal electrodes sit
 * at 10 V and the 22 Ground electrodes at 0 V, so the potential on that
 * plane shows the alternation directly.  A real measurement, not a drawing.
 *
 * API facts, all MEASURED on 2026-10-24:
 *   * the potential variable on this model is plain "V"; both "es.V" and
 *     "ec.V" are rejected by COMSOL with Undefined_post_expression.
 *   * the cut-plane dataset type is "CutPlane", NOT "CutPlane3D".
 *     "CutPlane3D" fails with "Operation cannot be created in this context"
 *     (COMSOL's own shipped models use "CutPlane" - see
 *     doc/.../models.acdc.capacitor_dc/capacitor_dc.java).
 *   * a 2D plot group on a cut plane with a filled Contour is the pattern
 *     COMSOL's own AC/DC capacitor example uses for exactly this figure.
 *   * comsolbatch calls main() with a NULL args array, so the model path is
 *     hardcoded; the batch SecurityManager denies raw java.io reads, so no
 *     File.exists() guard may be used; ModelUtil.load and result().export()
 *     are exempt.
 *
 * Output: figures/comsol_ex4/ex4_potential_z*.png
 */
public class Ex4_PlanPotential {

    static Model model;
    static String outDir;


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
    static void log(String s) {
        System.out.println("PPOT " + s);
    }

    /** Build the cut-plane dataset at height z (model length unit: um). */
    static String makeCutPlane(String tag, String z) {
        String ds = "cpl_" + tag;
        try {
            model.result().dataset().create(ds, "CutPlane");
            log("dataset " + ds + " created (CutPlane)");
        } catch (Throwable t) {
            log("FAILED dataset create: " + t);
            return null;
        }
        try {
            model.result().dataset(ds).set("data", "dset1");
            log("dataset bound to dset1");
        } catch (Throwable t) {
            log("FAILED dataset.set(data): " + t.getMessage());
        }
        // Only "quickplane" and "quickz" exist on a CutPlane dataset.
        // "quickxmethod"/"quickzmethod" do NOT (COMSOL's own examples use
        // set("quickplane", "xy") and set("quickz", ...)), and because a
        // single unknown property throws, setting the method flags first
        // silently aborted the whole block and left the plane at its
        // default position - which is why the first export came out
        // uniformly red.
        try {
            model.result().dataset(ds).set("quickplane", "xy");
            log("quickplane = xy");
        } catch (Throwable t) {
            log("FAILED quickplane: " + t.getMessage());
        }
        try {
            model.result().dataset(ds).set("quickz", z);
            log("quickz = " + z);
        } catch (Throwable t) {
            log("FAILED quickz: " + t.getMessage());
        }
        return ds;
    }

    /**
     * Filled contour of the potential on the cut plane, exported as PNG.
     * The Image export is created with TWO arguments and the source bound
     * afterwards with sourcetype/sourceobject, as COMSOL's own examples do.
     */
    static boolean exportContour(String tag, String z, String expr, String title,
                                  String fname, int w, int h) {
        String ds = makeCutPlane(tag, z);
        if (ds == null) return false;
        String pg = "pg2d_" + tag;
        try {
            model.result().create(pg, "PlotGroup2D");
            model.result(pg).set("data", ds);
            log("plot group " + pg + " created on " + ds);
        } catch (Throwable t) {
            log("FAILED plot group create: " + t.getMessage());
            return false;
        }
        // Measure the real range of the field on this plane, so the colour
        // scale can be checked against physics instead of guessed.
        try {
            model.result().numerical().create("mx_" + tag, "MaxSurface");
            model.result().numerical("mx_" + tag).set("data", ds);
            model.result().numerical("mx_" + tag).selection().all();
            model.result().numerical("mx_" + tag).set("expr", expr);
            model.result().numerical("mx_" + tag).run();
            double[][] mx = model.result().numerical("mx_" + tag).getReal();
            model.result().numerical().create("mn_" + tag, "MinSurface");
            model.result().numerical("mn_" + tag).set("data", ds);
            model.result().numerical("mn_" + tag).selection().all();
            model.result().numerical("mn_" + tag).set("expr", expr);
            model.result().numerical("mn_" + tag).run();
            double[][] mn = model.result().numerical("mn_" + tag).getReal();
            log("FIELD RANGE on z=" + z + " : min=" + mn[0][0] + "  max=" + mx[0][0]
                + "  (units: the model length unit is um, potential in V)");
        } catch (Throwable t) {
            log("(field range measurement failed: " + t.getMessage() + ")");
        }
        try {
            model.result(pg).create("con1", "Contour");
            model.result(pg).feature("con1").set("expr", expr);
            model.result(pg).feature("con1").set("evaluationsettings", "parent");
            model.result(pg).feature("con1").set("contourtype", "filled");
            model.result(pg).feature("con1").set("colortable", "RainbowLight");
            model.result(pg).feature("con1").set("coloring", "uniform");
            model.result(pg).feature("con1").set("smooth", "internal");
            log("contour configured");
        } catch (Throwable t) {
            log("FAILED contour config: " + t.getMessage());
        }
        try {
            model.result(pg).set("titletype", "manual");
            model.result(pg).set("title", title);
            model.result(pg).set("showlegendsmaxmin", "on");
            model.result(pg).set("showlegendstitle", "on");
            model.result(pg).set("showlegendsunit", "on");
        } catch (Throwable t) {
            log("(title/legend props skipped: " + t.getMessage() + ")");
        }
        try {
            model.result(pg).run();
            log("plot group ran");
        } catch (Throwable t) {
            log("FAILED plot group run: " + t.getMessage());
            return false;
        }
        String e = "img_" + tag;
        try {
            model.result().export().create(e, "Image");
            log("image export created");
        } catch (Throwable t) {
            log("FAILED export create: " + t.getMessage());
            return false;
        }
        try {
            model.result().export(e).set("sourcetype", "plotgroup");
            log("sourcetype=plotgroup set");
        } catch (Throwable t) {
            log("(sourcetype=plotgroup skipped: " + t.getMessage() + ")");
        }
        try {
            model.result().export(e).set("sourceobject", pg);
            log("sourceobject=" + pg + " set");
        } catch (Throwable t) {
            log("(sourceobject skipped: " + t.getMessage() + ")");
        }
        try {
            model.result().export(e).set("pngfilename", fname);
            model.result().export(e).set("target", "file");
            model.result().export(e).set("imagetype", "png");
            model.result().export(e).set("size", "manualweb");
            model.result().export(e).set("width", String.valueOf(w));
            model.result().export(e).set("height", String.valueOf(h));
            model.result().export(e).set("lockratio", "off");
            model.result().export(e).set("fontsize", String.valueOf(fontFor(w)));
            model.result().export(e).set("background", "color");
            model.result().export(e).set("customcolor", new double[]{1.0, 1.0, 1.0});
            model.result().export(e).set("options2d", "on");
            model.result().export(e).set("options3d", "on");
            model.result().export(e).set("title2d", "on");
            model.result().export(e).set("legend2d", "on");
            model.result().export(e).set("axes2d", "on");
            log("export configured");
        } catch (Throwable t) {
            log("(export props skipped: " + t.getMessage() + ")");
        }
        try {
            model.result().export(e).run();
            log("EXPORT OK -> " + fname);
            return true;
        } catch (Throwable t) {
            log("FAILED export run: " + t.getMessage());
            return false;
        }
    }


    /** Surface (not contour) of the field on a cut plane. */
    static boolean exportSurface(String tag, String z, String expr, String title,
                                 String fname, int w, int h,
                                 String xmin, String xmax) {
        String ds = makeCutPlane(tag, z);
        if (ds == null) return false;
        String pg = "pgs_" + tag;
        try {
            model.result().create(pg, "PlotGroup2D");
            model.result(pg).set("data", ds);
            model.result(pg).create("surf1", "Surface");
            model.result(pg).feature("surf1").set("expr", expr);
            model.result(pg).set("titletype", "manual");
            model.result(pg).set("title", title);
            model.result(pg).set("showlegendsmaxmin", "on");
            model.result(pg).set("showlegendstitle", "on");
            model.result(pg).set("showlegendsunit", "on");
            // Axis limits belong to the PLOT GROUP, not to the component
            // view: set("axislimits", true) then xmin/xmax/ymin/ymax.  This
            // is the pattern in COMSOL's own shipped models (331 uses of
            // "axislimits" there).  Setting them on view("view1").axis()
            // instead does nothing for a 2D plot group.
            // Each property is set in its OWN try block: one unknown
            // property throws, and a single try block around all of them
            // would silently discard the ones that come after it.
            //   (a) "axislimits" is NOT valid on a 2D plot group -> unknown
            //   (b) the limits themselves are accepted
            //   (c) the component view can also carry them, but only once
            //       viewscaletype is set to manual
            try {
                model.result(pg).set("axislimits", true);
                log("pg axislimits = true");
            } catch (Throwable t) {
                log("(pg axislimits skipped: " + t.getMessage() + ")");
            }
            try {
                model.result(pg).set("xmin", xmin);
                log("pg xmin = " + xmin);
            } catch (Throwable t) {
                log("(pg xmin skipped: " + t.getMessage() + ")");
            }
            try {
                model.result(pg).set("xmax", xmax);
                log("pg xmax = " + xmax);
            } catch (Throwable t) {
                log("(pg xmax skipped: " + t.getMessage() + ")");
            }
            try {
                model.result(pg).set("ymin", "0");
                model.result(pg).set("ymax", "100");
                log("pg ymin/ymax = 0/100");
            } catch (Throwable t) {
                log("(pg ymin/ymax skipped: " + t.getMessage() + ")");
            }
            try {
                String[] vt = model.component("comp1").view().tags();
                String view = (vt.length > 0) ? vt[0] : "view1";
                model.component("comp1").view(view).axis().set("viewscaletype", "manual");
                model.component("comp1").view(view).axis().set("xmin", xmin);
                model.component("comp1").view(view).axis().set("xmax", xmax);
                model.component("comp1").view(view).axis().set("ymin", "0");
                model.component("comp1").view(view).axis().set("ymax", "100");
                log("view axis limits set to x " + xmin + ".." + xmax + ", y 0..100 um");
            } catch (Throwable t) {
                log("(view axis limits skipped: " + t.getMessage() + ")");
            }
            // The export's "fontsize" property only reaches the text of a
            // GEOMETRY-source export (that is why the device figures now have
            // legible axes).  A plot-group export takes its font from the plot
            // group itself, so the size is set there as well.
            try {
                model.result(pg).set("fontsize", String.valueOf(fontFor(w)));
                log("plot group fontsize = " + fontFor(w));
            } catch (Throwable t) {
                log("(pg fontsize skipped: " + t.getMessage() + ")");
            }
            model.result(pg).run();
        } catch (Throwable t) {
            log("FAILED surface plot group: " + t.getMessage());
            return false;
        }
        String e = "imgs_" + tag;
        try {
            model.result().export().create(e, "Image");
            model.result().export(e).set("sourcetype", "plotgroup");
            model.result().export(e).set("sourceobject", pg);
            model.result().export(e).set("pngfilename", fname);
            model.result().export(e).set("target", "file");
            model.result().export(e).set("imagetype", "png");
            model.result().export(e).set("size", "manualweb");
            model.result().export(e).set("width", String.valueOf(w));
            model.result().export(e).set("height", String.valueOf(h));
            model.result().export(e).set("lockratio", "off");
            model.result().export(e).set("fontsize", String.valueOf(fontFor(w)));
            model.result().export(e).set("background", "color");
            model.result().export(e).set("customcolor", new double[]{1.0, 1.0, 1.0});
            model.result().export(e).set("options2d", "on");
            // The LaTeX caption carries the same information, and the
            // COMSOL-internal title is set in a font far too small to read at
            // the printed size, so it is turned off.
            try {
                model.result().export(e).set("title2d", "off");
                log("internal plot title turned off");
            } catch (Throwable t) {
                log("(title2d skipped: " + t.getMessage() + ")");
            }
            model.result().export(e).run();
            log("SURFACE EXPORT OK -> " + fname);
            return true;
        } catch (Throwable t) {
            log("FAILED surface export: " + t.getMessage());
            return false;
        }
    }

    /** Average of the field over a cut plane, for a height scan. */
    static void avgAt(String tag, String z, String expr) {
        try {
            String ds = makeCutPlane("av" + tag, z);
            if (ds == null) return;
            model.result().numerical().create("avg" + tag, "AvSurface");
            model.result().numerical("avg" + tag).set("data", ds);
            model.result().numerical("avg" + tag).selection().all();
            model.result().numerical("avg" + tag).set("expr", expr);
            model.result().numerical("avg" + tag).run();
            double[][] v = model.result().numerical("avg" + tag).getReal();
            log("AVG V on z=" + z + " : " + v[0][0]);
        } catch (Throwable t) {
            log("(avg at z=" + z + " failed: " + t.getMessage() + ")");
        }
    }

    public static void main(String[] args) {
        try {
            String root = "<project>/";
            outDir = root + "figures/comsol_ex4";
            log("loading model");
            model = ModelUtil.load("Ex4", root + "comsol/out/Ex4_FFF_prod.mph");
            log("physics = " + java.util.Arrays.toString(
                model.component("comp1").physics().tags()));
            log("datasets = " + java.util.Arrays.toString(
                model.result().dataset().tags()));

            // The potential variable on this model is plain "V"; es.V and ec.V
            // are rejected by COMSOL with Undefined_post_expression.
            String expr = "V";

            // Height scan of the mean potential: the plane that contains the
            // electrodes must show a mean well below the 10 V terminal value.
            for (int i = 0; i < 5; i++) {
                avgAt("s" + i, new String[]{"0", "1", "10", "50", "100"}[i], expr);
            }

            // (a) the whole 1 mm array: all 45 electrodes
            exportSurface("full", "0", expr,
                "Ex4 in COMSOL: electric potential V on the channel floor z = 0 \u00b5m "
                + "(23 electrodes at 10 V, 22 grounded)",
                outDir + "/ex4_potential_full.png", 1800, 520, "0", "1000");
            // (b) a 400 um zoom, so the individual electrodes are legible
            exportSurface("zoom", "0", expr,
                "Ex4 in COMSOL: electric potential V on the channel floor z = 0 \u00b5m, "
                + "400 \u00b5m zoom (20 of the 45 electrodes)",
                outDir + "/ex4_potential_zoom.png", 1700, 560, "100", "500");
            log("=== EX4PLANPOT DONE ===");
        } catch (Throwable e) {
            e.printStackTrace();
            log("ABORTED: " + e);
        }
    }
}
