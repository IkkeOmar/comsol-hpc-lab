/* Probe17: diagnose the steady-state H-filter solution.
 *
 * Question: the Probe15 outlet profile was NOT antisymmetric
 * (c(+w/2)+c(-w/2) = 0.83 instead of 1, c(0) = 0.386 instead of 0.5),
 * even though the geometry, the inlet step BC and the flow are symmetric.
 * For plug flow the Graetz problem is *identical* to the transient 1D
 * diffusion problem, so the outlet profile must equal the analytical
 * Eq.(1) at t = t_res = 1.8 s (mirrored: sugar in the upper half).
 *
 * Two meshes are compared, both with the same physics:
 *   V1: Mesh Control Edge + Map mesher          (the assignment recipe)
 *   V2: no Mesh Control Edge + Map mesher       (reference)
 *
 * Diagnostics printed for each variant:
 *   - u(y) at the outlet and at mid-channel   (is the profile plug? symmetric?)
 *   - c(y) at the outlet, 21 points           (shape of the profile)
 *   - c(y) at x = 0.01 mm, 1 mm, 3 mm         (development along the channel)
 *   - molar flow in / out                      (conservation)
 *   - comparison with the analytical series: max|sim-ana| and the best-fit
 *     effective diffusion time t_eff
 *   - upper-half line average vs the analytical at t_res
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class Probe17 {

    static final double PI = Math.PI;
    static final double W = 100e-6;      // channel width  [m]
    static final double D = 2e-10;       // sugar D         [m^2/s]
    static final double C0 = 1.0;        // c0              [mol/m^3]

    static void p(String s) { System.out.println(s); }

    /* analytical Eq.(1) in the COMSOL convention (sugar in the UPPER half) */
    static double cUpper(double y, double t) {
        double s = C0 * 0.5;
        for (int n = 1; n <= 400; n++) {
            double m = 2 * n - 1;
            s += 2.0 * C0 / (m * PI) * Math.sin(m * PI * y / W)
                 * Math.exp(-Math.pow(m * PI / W, 2) * D * t);
        }
        return s;
    }

    /* area (line) average of the analytical profile over [y0, y1] */
    static double cUpperAvg(double y0, double y1, double t, int npts) {
        double h = (y1 - y0) / (npts - 1), s = 0;
        for (int i = 0; i < npts; i++) {
            double y = y0 + i * h;
            double wgt = (i == 0 || i == npts - 1) ? 0.5 : 1.0;
            s += wgt * cUpper(y, t);
        }
        return s * h / (y1 - y0);
    }

    static void report(MeshSequence m, String label) {
        p(label + ": numElem=" + m.stat().getNumElem()
          + "  minQ=" + m.stat().getMinQuality()
          + "  meanQ=" + m.stat().getMeanQuality());
    }

    /* evaluate a scalar field at a list of points */
    static double[] evalAt(Model model, String dataTag, String expr,
                            double xs, double[] ys, String label) {
        int n = ys.length;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            String tag = "cp" + label + i;
            model.result().dataset().create(tag, "CutPoint2D");
            model.result().dataset(tag).set("pointx", Double.toString(xs));
            model.result().dataset(tag).set("pointy", Double.toString(ys[i]));
            String nt = "ev" + label + i;
            model.result().numerical().create(nt, "EvalPoint");
            model.result().numerical(nt).set("data", tag);
            model.result().numerical(nt).setIndex("expr", expr, 0);
            model.result().numerical(nt).run();
            out[i] = model.result().numerical(nt).getReal()[0][0];
        }
        return out;
    }

    static void printVec(String label, double[] ys, double[] v) {
        StringBuilder sb = new StringBuilder(label + ": ");
        for (int i = 0; i < ys.length; i++) {
            sb.append(String.format("y=%+7.2fum c=%.5f", ys[i] * 1e6, v[i]));
            if (i < ys.length - 1) sb.append(" | ");
        }
        p(sb.toString());
    }

    public static void main(String[] args) {
        try {
            double[] ys = new double[21];
            for (int i = 0; i < ys.length; i++) ys[i] = -50e-6 + i * 5e-6;

            for (int variant = 0; variant < 2; variant++) {
                final boolean useMCE = (variant == 0);
                String tag = useMCE ? "MCE" : "NOMCE";

                Model model = ModelUtil.create("P17" + tag);
                model.component().create("comp1");
                model.param().set("c0", "1[mol/m^3]");
                model.param().set("w_ch", "100[um]");
                model.param().set("L_ch", "6000[um]");
                model.param().set("h_ch", "5[um]");
                model.param().set("D_sp", "2e-10[m^2/s]");
                model.param().set("Q_in", "1.666667e-12[m^3/s]");
                model.param().set("mu_f", "1e-3[Pa*s]");
                model.param().set("rho_f", "1000[kg/m^3]");
                model.param().set("t_res", "L_ch*w_ch*h_ch/Q_in");

                GeomSequence g = model.component("comp1").geom().create("geom1", 2);
                g.lengthUnit("um");
                g.create("r1", "Rectangle");
                g.feature("r1").set("size", new String[]{"L_ch", "w_ch"});
                g.feature("r1").set("pos", new String[]{"0", "-w_ch/2"});
                if (useMCE) {
                    g.create("ln1", "LineSegment");
                    g.feature("ln1").set("specify1", "coord");
                    g.feature("ln1").set("specify2", "coord");
                    g.feature("ln1").set("coord1", new String[]{"0[um]", "0[um]"});
                    g.feature("ln1").set("coord2", new String[]{"L_ch", "0[um]"});
                    g.run();
                    g.create("mce1", "MeshControlEdges");
                    g.feature("mce1").selection("input").set("ln1", 1);
                    g.run("mce1");
                } else {
                    g.run();
                }
                g.create("selIn", "BoxSelection");
                g.feature("selIn").set("entitydim", 1);
                g.feature("selIn").set("condition", "allvertices");
                g.feature("selIn").set("xmin", "-1[um]"); g.feature("selIn").set("xmax", "1[um]");
                g.feature("selIn").set("ymin", "-w_ch/2-1[um]"); g.feature("selIn").set("ymax", "w_ch/2+1[um]");
                g.create("selOut", "BoxSelection");
                g.feature("selOut").set("entitydim", 1);
                g.feature("selOut").set("condition", "allvertices");
                g.feature("selOut").set("xmin", "L_ch-1[um]"); g.feature("selOut").set("xmax", "L_ch+1[um]");
                g.feature("selOut").set("ymin", "-w_ch/2-1[um]"); g.feature("selOut").set("ymax", "w_ch/2+1[um]");
                g.create("selTop", "BoxSelection");
                g.feature("selTop").set("entitydim", 1);
                g.feature("selTop").set("condition", "allvertices");
                g.feature("selTop").set("xmin", "-1[um]"); g.feature("selTop").set("xmax", "L_ch+1[um]");
                g.feature("selTop").set("ymin", "w_ch/2-1[um]"); g.feature("selTop").set("ymax", "w_ch/2+1[um]");
                g.create("selBot", "BoxSelection");
                g.feature("selBot").set("entitydim", 1);
                g.feature("selBot").set("condition", "allvertices");
                g.feature("selBot").set("xmin", "-1[um]"); g.feature("selBot").set("xmax", "L_ch+1[um]");
                g.feature("selBot").set("ymin", "-w_ch/2-1[um]"); g.feature("selBot").set("ymax", "-w_ch/2+1[um]");
                g.run();
                int[] inE  = model.selection("geom1_selIn").entities(1);
                int[] outE = model.selection("geom1_selOut").entities(1);
                int[] topE = model.selection("geom1_selTop").entities(1);
                int[] botE = model.selection("geom1_selBot").entities(1);
                p("### VARIANT " + tag + "  IN=" + Arrays.toString(inE) + " OUT=" + Arrays.toString(outE)
                  + " TOP=" + Arrays.toString(topE) + " BOT=" + Arrays.toString(botE));

                model.component("comp1").material().create("mat1", "Common");
                model.component("comp1").material("mat1").label("Water");
                model.component("comp1").material("mat1").propertyGroup("def")
                     .set("dynamicviscosity", "mu_f").set("density", "rho_f");

                MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");
                mesh.create("size1", "Size");
                mesh.feature("size1").set("hmax", "20[um]");
                mesh.feature("size1").set("hmin", "0.5[um]");
                mesh.feature("size1").set("hgrad", 1.2);
                mesh.create("distIn", "Distribution");
                mesh.feature("distIn").selection().geom(1).set(inE);
                mesh.feature("distIn").set("numelem", "80");
                mesh.create("distTop", "Distribution");
                mesh.feature("distTop").selection().geom(1).set(topE);
                mesh.feature("distTop").set("numelem", "120");
                mesh.create("distBot", "Distribution");
                mesh.feature("distBot").selection().geom(1).set(botE);
                mesh.feature("distBot").set("numelem", "120");
                mesh.create("map1", "Map");
                mesh.run();
                report(mesh, tag + " MESH");

                model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
                model.component("comp1").physics("spf").prop("PhysicalModelProperty")
                     .set("ShallowChannelProperty", "1");
                model.component("comp1").physics("spf").prop("PhysicalModelProperty")
                     .set("dz", "h_ch");
                model.component("comp1").physics("spf").create("inl1", "Inlet", 1);
                model.component("comp1").physics("spf").feature("inl1").selection().set(inE);
                model.component("comp1").physics("spf").feature("inl1")
                     .set("BoundaryCondition", "MassFlow");
                model.component("comp1").physics("spf").feature("inl1")
                     .set("MassFlowType", "MassFlowRate");
                model.component("comp1").physics("spf").feature("inl1")
                     .set("mfr", "rho_f*Q_in");
                model.component("comp1").physics("spf").create("out1", "Outlet", 1);
                model.component("comp1").physics("spf").feature("out1").selection().set(outE);
                model.component("comp1").physics("spf").feature("out1").set("p0", "0");

                model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
                model.component("comp1").physics("tds").feature("cdm1").set("D_c", "D_sp");
                model.component("comp1").physics("tds").feature("cdm1").set("u_src", "root.comp1.u");
                model.func().create("step1", "Step");
                model.func("step1").set("funcname", "step1");
                model.func("step1").set("location", "0");
                model.func("step1").set("from", 0);
                model.func("step1").set("to", 1);
                model.func("step1").set("smooth", "0[mm]");
                model.component("comp1").physics("tds").create("inflow1", "Inflow", 1);
                model.component("comp1").physics("tds").feature("inflow1").selection().set(inE);
                model.component("comp1").physics("tds").feature("inflow1")
                     .set("c0", "c0*step1(y[1/mm])");
                model.component("comp1").physics("tds").create("outflow1", "Outflow", 1);
                model.component("comp1").physics("tds").feature("outflow1").selection().set(outE);

                model.study().create("std1");
                model.study("std1").create("stat", "Stationary");
                model.study("std1").createAutoSequences("all");
                model.study("std1").run();
                p("### " + tag + " SOLVE OK");

                /* --- flow: velocity profile at the outlet and mid-channel --- */
                double[] uOut = evalAt(model, "d", "u", 6000e-6, ys, "uO" + tag);
                printVec(tag + " u(x=L)", ys, uOut);
                double[] uMid = evalAt(model, "d", "u", 3000e-6, ys, "uM" + tag);
                printVec(tag + " u(x=3mm)", ys, uMid);

                /* --- species profile at the outlet --- */
                double[] cOut = evalAt(model, "d", "c", 6000e-6, ys, "cO" + tag);
                printVec(tag + " c(x=L)", ys, cOut);

                /* --- development along the channel --- */
                double[] yc = {0.0, 25e-6, 50e-6, -25e-6, -50e-6};
                for (double xs : new double[]{1e-6, 100e-6, 1000e-6, 3000e-6}) {
                    double[] cD = evalAt(model, "d", "c", xs, yc, "cD" + tag + "_" + (int)(xs*1e6));
                    printVec(tag + " c(x=" + (xs*1e6) + "um) y=+50,+25,0,-25,-50", yc, cD);
                }

                /* --- conservation: molar flow in and out --- */
                model.result().numerical().create("iQc", "IntLine");
                model.result().numerical("iQc").selection().set(inE);
                model.result().numerical("iQc").setIndex("expr", "u*c", 0);
                model.result().numerical("iQc").run();
                double fluxIn = model.result().numerical("iQc").getReal()[0][0];
                model.result().numerical("iQc").selection().set(outE);
                model.result().numerical("iQc").run();
                double fluxOut = model.result().numerical("iQc").getReal()[0][0];
                model.result().numerical("iQc").selection().set(inE);
                model.result().numerical("iQc").setIndex("expr", "u", 0);
                model.result().numerical("iQc").run();
                double intU = model.result().numerical("iQc").getReal()[0][0];
                p(tag + " Q = dz*int(u dl) = " + (5e-6 * intU) + " m^3/s (target 1.666667e-12)");
                p(tag + " molar flow in = " + (5e-6 * fluxIn) + " out = " + (5e-6 * fluxOut)
                  + "  (mol/s)");

                /* --- upper-half line average (COMSOL) --- */
                model.result().dataset().create("clnUp", "CutLine2D");
                model.result().dataset("clnUp").setIndex("genpoints", "L_ch", 0, 0);
                model.result().dataset("clnUp").setIndex("genpoints", "0", 0, 1);
                model.result().dataset("clnUp").setIndex("genpoints", "L_ch", 1, 0);
                model.result().dataset("clnUp").setIndex("genpoints", "w_ch/2", 1, 1);
                model.result().numerical().create("avUp", "AvLine");
                model.result().numerical("avUp").set("data", "clnUp");
                model.result().numerical("avUp").setIndex("expr", "c", 0);
                model.result().numerical("avUp").run();
                double avUp = model.result().numerical("avUp").getReal()[0][0];

                double tRes = 1.8;
                p(tag + " upper-half line average (COMSOL) = " + avUp);
                p(tag + " analytical upper-half area avg at t_res=1.8s = "
                  + cUpperAvg(0, 50e-6, tRes, 4001));

                /* --- comparison with the analytical series: max|sim-ana| and best t_eff --- */
                double bestErr = 1e9, bestT = -1;
                for (double t = 0.05; t <= 20.0; t += 0.05) {
                    double err = 0;
                    for (int i = 0; i < ys.length; i++) {
                        err = Math.max(err, Math.abs(cOut[i] - cUpper(ys[i], t)));
                    }
                    if (err < bestErr) { bestErr = err; bestT = t; }
                }
                double errAtRes = 0;
                for (int i = 0; i < ys.length; i++) {
                    errAtRes = Math.max(errAtRes, Math.abs(cOut[i] - cUpper(ys[i], tRes)));
                }
                p(tag + " max|sim-ana| at t_res=1.8s = " + errAtRes);
                p(tag + " best fit t_eff = " + bestT + " s   max|sim-ana| = " + bestErr);
                p(tag + " analytical c(y=0)=" + cUpper(0, tRes) + "  c(+50um)=" + cUpper(50e-6, tRes)
                  + "  c(-50um)=" + cUpper(-50e-6, tRes));
                p(tag + " COMSOL    c(y=0)=" + cOut[10] + "  c(+50um)=" + cOut[20]
                  + "  c(-50um)=" + cOut[0]);
                p(tag + " antisymmetry check c(+50)+c(-50) = " + (cOut[20] + cOut[0])
                  + " (should be 1.0)");

                /* --- figure pipeline test: 2D surface plot exported to PNG --- */
                try {
                    model.result().create("pg1", "PlotGroup2D");
                    model.result("pg1").create("surf1", "Surface");
                    model.result("pg1").feature("surf1").set("expr", "c");
                    model.result("pg1").feature("surf1").set("colortable", "Prism");
                    model.result().export().create("img1", "Image2D");
                    model.result().export("img1").set("plotgroup", "pg1");
                    model.result().export("img1").set("sourceobject", "pg1");
                    model.result().export("img1").set("pngfilename",
                        "<project>/figures/probe17_" + tag + ".png");
                    model.result().export("img1").set("imagetype", "png");
                    model.result().export("img1").set("width", "1400");
                    model.result().export("img1").set("height", "800");
                    model.result().export("img1").run();
                    p(tag + " PNG export OK");
                } catch (Throwable e) { p(tag + " PNG export FAIL: " + e.getMessage()); }

                p("### VARIANT " + tag + " DONE");
            }
            p("PROBE17 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("PROBE17 ABORTED: " + e);
        }
    }
}
