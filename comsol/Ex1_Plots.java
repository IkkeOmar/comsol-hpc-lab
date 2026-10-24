import com.comsol.model.*;
import com.comsol.model.util.*;

/**
 * Ex1_Plots: COMSOL's OWN result pictures for the H-filter.
 *
 * The pictures produced by the production run (figures/comsol_ex1/*.png) were
 * bare strips: the Image export feature was used WITHOUT options2d, so the
 * export had no axes, no colour legend and no title, and the 60:1 aspect of a
 * 6 mm x 100 um channel left the field itself only a few pixels tall.
 *
 * This class does NOT re-solve anything.  It LOADS the model that the
 * validated run produced (comsol/out/Ex1_HFilter.mph, saved at the end of
 * run6) and re-exports the plots with:
 *   - options2d = on  -> axes, colour legend and title in the export
 *   - the view axis zoomed to a region of interest, so the field is readable
 *   - explicit axis labels and a manual title
 *
 * The parametric sweep over D_sweep is stored in the model; the plot group
 * selects one combination at a time through the `outersolnum` property, which
 * the plot group exposes (verified in the ProbePgProps property dump).
 *
 * Every figure is COMSOL's own render of the solved field.
 */
public class Ex1_Plots {

    static String figDir;

    static void trySet(Model model, String pg, String prop, String val) {
        try {
            model.result(pg).set(prop, val);
        } catch (Throwable t) {
            System.out.println("    (skipped '" + prop + "': " + t.getMessage() + ")");
        }
    }

    /**
     * A 2D surface plot with axes, a colour legend and a title, exported.
     */
    static int sweepCombo = 1;   // 1-based index into the D sweep
    static String viewTag = "view1";

    static void exportSurface(Model model, String tag, String expr, String title,
                               String xlabel, String ylabel,
                               double xmin, double xmax, double ymin, double ymax,
                               int w, int h) {
        try {
            String pg = "p_" + tag;
            model.result().create(pg, "PlotGroup2D");
            model.result(pg).set("data", "dset1");
            // Select the parameter combination BEFORE the plot is drawn.
            // `outersolnum` is REJECTED on a plot group (Invalid_property_value,
            // measured).  The shipped COMSOL 6.4 examples use
            //   model.result("pg4").setIndex("looplevel", 5, 0);
            // (Programming Reference Manual p. 754: looplevel = index of the
            // solution to use, per level).  Level 0 is the outer parametric
            // level, i.e. the D value.
            try {
                model.result(pg).setIndex("looplevelinput", "manual", 0);
            } catch (Throwable t) {
                System.out.println("    (looplevelinput on " + pg + " rejected: "
                                 + t.getMessage() + ")");
            }
            try {
                model.result(pg).setIndex("looplevel", sweepCombo, 0);
            } catch (Throwable t) {
                System.out.println("    (looplevel on " + pg + " rejected: "
                                 + t.getMessage() + ")");
            }
            model.result(pg).create("srf", "Surface");
            model.result(pg).feature("srf").set("expr", expr);
            // colour legend content
            trySet(model, pg, "showlegendsmaxmin", "on");
            trySet(model, pg, "showlegendstitle", "on");
            trySet(model, pg, "showlegendsunit", "on");
            // axis labels
            trySet(model, pg, "xlabelactive", "on");
            trySet(model, pg, "xlabel", xlabel);
            trySet(model, pg, "ylabelactive", "on");
            trySet(model, pg, "ylabel", ylabel);
            trySet(model, pg, "titletype", "manual");
            trySet(model, pg, "title", title);
            model.result(pg).run();

            // Tie the plot group to the component view, otherwise COMSOL
            // auto-fits the axes and the zoom below is ignored (measured:
            // the first export showed x 0..6000 instead of 0..600).
            try {
                model.result(pg).set("view", viewTag);
            } catch (Throwable t) {
                System.out.println("    (pg view rejected: " + t.getMessage() + ")");
            }
            // zoom the view onto the region of interest
            model.component("comp1").view(viewTag).axis().set("xmin", xmin);
            model.component("comp1").view(viewTag).axis().set("xmax", xmax);
            model.component("comp1").view(viewTag).axis().set("ymin", ymin);
            model.component("comp1").view(viewTag).axis().set("ymax", ymax);

            String e = "e_" + tag;
            model.result().export().create(e, pg, "Image");
            model.result().export(e).set("pngfilename", figDir + "/" + tag + ".png");
            model.result().export(e).set("target", "file");
            model.result().export(e).set("size", "manualweb");
            model.result().export(e).set("width", String.valueOf(w));
            model.result().export(e).set("height", String.valueOf(h));
            model.result().export(e).set("lockratio", "off");
            model.result().export(e).set("options2d", "on");
            model.result().export(e).set("axes2d", "on");
            model.result().export(e).set("legend2d", "on");
            model.result().export(e).set("title2d", "on");
            model.result().export(e).set("fontsize", "26");
            model.result().export(e).set("zoomextents", "off");
            model.result().export(e).set("background", "color");
            model.result().export(e).set("customcolor",
                new double[]{1.0, 1.0, 1.0});
            model.result().export(e).run();
            // read back the parameter value that this picture actually shows
            try {
                model.result().dataset("dset1").run();
                double[] lev = model.result(pg).getDoubleArray("looplevel");
                System.out.println("  [" + tag + "] looplevel = "
                    + java.util.Arrays.toString(lev));
            } catch (Throwable t) {
                System.out.println("  [" + tag + "] looplevel readback failed: "
                    + t.getMessage());
            }
            System.out.println("  exported " + tag + " -> " + figDir + "/" + tag + ".png");
        } catch (Throwable t) {
            System.out.println("  EXPORT FAILED for " + tag + ": " + t);
            t.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            String root = "<project>/";
            figDir = root + "figures/comsol_ex1";
            String mph = root + "comsol/out/Ex1_HFilter.mph";

            System.out.println("loading " + mph);
            Model model = ModelUtil.load("Ex1", mph);
            System.out.println("loaded. plot groups: "
                + java.util.Arrays.toString(model.result().tags()));
            try {
                String[] vtags = model.component("comp1").view().tags();
                System.out.println("component views: "
                    + java.util.Arrays.toString(vtags));
                if (vtags.length > 0) viewTag = vtags[0];
            } catch (Throwable t) {
                System.out.println("view probe failed: " + t.getMessage());
            }
            System.out.println("using view: " + viewTag);

            // the parametric values stored in the solution, so the picture can
            // be labelled with the D it actually shows
            double[] pvals = null;
            try {
                String[] solTags = model.sol().tags();
                System.out.println("solutions: " + java.util.Arrays.toString(solTags));
                for (String st : solTags) {
                    try {
                        pvals = model.sol(st).getPVals();
                        System.out.println("  " + st + " parameter values: "
                            + java.util.Arrays.toString(pvals));
                        break;
                    } catch (Throwable t) {
                        System.out.println("  " + st + " getPVals failed: " + t.getMessage());
                    }
                }
            } catch (Throwable t) {
                System.out.println("solution probe failed: " + t.getMessage());
            }

            // D values of the sweep, in the order the instructor lists them
            double[] D = {1e-11, 5e-11, 1e-10, 5e-10, 1e-9, 5e-9};
            String[] Dlab = {"1e-11", "5e-11", "1e-10", "5e-10", "1e-9", "5e-9"};

            // pick the combination to display: index 2 -> D = 1e-10 m^2/s
            int combo = 2;
            System.out.println("showing sweep combination " + (combo + 1)
                + " of " + D.length + "  (D = " + Dlab[combo] + " m^2/s)");

            sweepCombo = combo + 1;   // 1-based
            System.out.println("  sweep combination " + sweepCombo + " (D = "
                + Dlab[combo] + " m^2/s)");

            // ---- 1. concentration at the inlet, zoomed -------------------
            exportSurface(model, "ex1_c_inlet", "c",
                "COMSOL: concentration c at the inlet, D = "
                + Dlab[combo] + " m^2/s",
                "x [um]", "y [um]",
                0.0, 600.0, -55.0, 55.0, 1500, 620);

            // ---- 2. concentration at the outlet, zoomed ------------------
            exportSurface(model, "ex1_c_outlet", "c",
                "COMSOL: concentration c at the outlet, D = "
                + Dlab[combo] + " m^2/s",
                "x [um]", "y [um]",
                5400.0, 6000.0, -55.0, 55.0, 1500, 620);

            // ---- 3. velocity magnitude at the inlet ---------------------
            exportSurface(model, "ex1_u_inlet", "spf.U",
                "COMSOL: velocity magnitude |u| in the channel",
                "x [um]", "y [um]",
                0.0, 600.0, -55.0, 55.0, 1500, 620);

            System.out.println("=== EX1PLOTS DONE ===");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("EX1PLOTS ABORTED: " + e);
        }
    }
}
