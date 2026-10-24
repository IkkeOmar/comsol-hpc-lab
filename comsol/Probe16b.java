/* Probe16: (1) mesh-quality comparison for the H-filter channel,
 *          (2) nominal stationary solve with full diagnostics, including a
 *              Java-side evaluation of the analytical series Eq.(1) so that
 *              the COMSOL outlet profile can be compared directly,
 *          (3) API checks for the figure work (Interpolation function from a
 *              table, 1D-plot x-axis control).
 *
 * Sugar convention (assignment p.3):  inlet BC  c0*step1(y[1/mm])  -> sugar
 * in the UPPER half (y>0).  The analytical Eq.(1) has sugar in the LOWER
 * half, so the mirror is  c_comsol(y) = c_Eq1(-y)  with
 *   c_Eq1(y,t) = c0/2 - sum_n 2c0/((2n-1)pi) sin((2n-1)pi y/w) exp(-((2n-1)pi/w)^2 D t)
 * i.e.
 *   c_upper(y,t) = c0/2 + sum_n 2c0/((2n-1)pi) sin((2n-1)pi y/w) exp(-((2n-1)pi/w)^2 D t).
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class Probe16b {

    static final double PI = Math.PI;

    static void p(String s) { System.out.println(s); }

    /* Eq.(1) mirrored: sugar in the upper half of the channel */
    static double cUpper(double y, double t, double w, double D, double c0, int N) {
        double s = c0 * 0.5;
        for (int n = 1; n <= N; n++) {
            double m = 2 * n - 1;
            s += 2.0 * c0 / (m * PI) * Math.sin(m * PI * y / w) * Math.exp(-Math.pow(m * PI / w, 2) * D * t);
        }
        return s;
    }

    static void report(MeshSequence m, String label) {
        p(label + ": numElem=" + m.stat().getNumElem()
          + "  minQ=" + m.stat().getMinQuality()
          + "  meanQ=" + m.stat().getMeanQuality()
          + "  types=" + Arrays.toString(m.stat().getTypes()));
    }

    public static void main(String[] args) {
        try {
            /* ================= PART 1: MESH VARIANTS ================= */
            {
                Model m = ModelUtil.create("VA");
                m.component().create("comp1");
                m.param().set("L_ch", "6000[um]");
                m.param().set("w_ch", "100[um]");
                GeomSequence g = m.component("comp1").geom().create("geom1", 2);
                g.lengthUnit("um");
                g.create("r1", "Rectangle");
                g.feature("r1").set("size", new String[]{"L_ch", "w_ch"});
                g.feature("r1").set("pos", new String[]{"0", "-w_ch/2"});
                g.create("ln1", "LineSegment");
                g.feature("ln1").set("specify1", "coord");
                g.feature("ln1").set("specify2", "coord");
                g.feature("ln1").set("coord1", new String[]{"0[um]", "0[um]"});
                g.feature("ln1").set("coord2", new String[]{"L_ch", "0[um]"});
                g.run();
                g.create("mce1", "MeshControlEdges");
                g.feature("mce1").selection("input").set("ln1", 1);
                g.run("mce1");
                g.create("selIn", "BoxSelection");
                g.feature("selIn").set("entitydim", 1);
                g.feature("selIn").set("condition", "allvertices");
                g.feature("selIn").set("xmin", "-1[um]"); g.feature("selIn").set("xmax", "1[um]");
                g.feature("selIn").set("ymin", "-w_ch/2-1[um]"); g.feature("selIn").set("ymax", "w_ch/2+1[um]");
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
                p("edges: IN=" + Arrays.toString(m.selection("geom1_selIn").entities(1))
                  + " TOP=" + Arrays.toString(m.selection("geom1_selTop").entities(1))
                  + " BOT=" + Arrays.toString(m.selection("geom1_selBot").entities(1))
                  + "  domains=" + m.component("comp1").geom("geom1").getNDomains());

                /* A: MCE + Map, symmetric distributions on inlet/top/bottom */
                MeshSequence mesh = m.component("comp1").mesh().create("mesh1", "geom1");
                mesh.create("size1", "Size");
                mesh.feature("size1").set("hmax", "20[um]");
                mesh.feature("size1").set("hmin", "0.5[um]");
                mesh.feature("size1").set("hgrad", 1.2);
                mesh.create("distIn", "Distribution");
                mesh.feature("distIn").selection().geom(1).set(m.selection("geom1_selIn").entities(1));
                mesh.feature("distIn").set("numelem", "80");
                mesh.create("distTop", "Distribution");
                mesh.feature("distTop").selection().geom(1).set(m.selection("geom1_selTop").entities(1));
                mesh.feature("distTop").set("numelem", "160");
                mesh.create("distBot", "Distribution");
                mesh.feature("distBot").selection().geom(1).set(m.selection("geom1_selBot").entities(1));
                mesh.feature("distBot").set("numelem", "160");
                mesh.create("map1", "Map");
                try { mesh.run(); report(mesh, "A: MCE + Map + sym dist(in=80,top=160,bot=160)"); }
                catch (Throwable e) { p("A FAIL: " + e.getMessage()); }
            }

            /* C: no MCE, Map, inlet + bottom distributions (reference) */
            {
                Model m = ModelUtil.create("VC");
                m.component().create("comp1");
                m.param().set("L_ch", "6000[um]");
                m.param().set("w_ch", "100[um]");
                GeomSequence g = m.component("comp1").geom().create("geom1", 2);
                g.lengthUnit("um");
                g.create("r1", "Rectangle");
                g.feature("r1").set("size", new String[]{"L_ch", "w_ch"});
                g.feature("r1").set("pos", new String[]{"0", "-w_ch/2"});
                g.run();
                g.create("selIn", "BoxSelection");
                g.feature("selIn").set("entitydim", 1);
                g.feature("selIn").set("condition", "allvertices");
                g.feature("selIn").set("xmin", "-1[um]"); g.feature("selIn").set("xmax", "1[um]");
                g.feature("selIn").set("ymin", "-w_ch/2-1[um]"); g.feature("selIn").set("ymax", "w_ch/2+1[um]");
                g.create("selBot", "BoxSelection");
                g.feature("selBot").set("entitydim", 1);
                g.feature("selBot").set("condition", "allvertices");
                g.feature("selBot").set("xmin", "-1[um]"); g.feature("selBot").set("xmax", "L_ch+1[um]");
                g.feature("selBot").set("ymin", "-w_ch/2-1[um]"); g.feature("selBot").set("ymax", "-w_ch/2+1[um]");
                g.run();
                MeshSequence mesh = m.component("comp1").mesh().create("mesh1", "geom1");
                mesh.create("size1", "Size");
                mesh.feature("size1").set("hmax", "20[um]");
                mesh.feature("size1").set("hmin", "0.5[um]");
                mesh.feature("size1").set("hgrad", 1.2);
                mesh.create("distIn", "Distribution");
                mesh.feature("distIn").selection().geom(1).set(m.selection("geom1_selIn").entities(1));
                mesh.feature("distIn").set("numelem", "80");
                mesh.create("distBot", "Distribution");
                mesh.feature("distBot").selection().geom(1).set(m.selection("geom1_selBot").entities(1));
                mesh.feature("distBot").set("numelem", "60");
                mesh.create("map1", "Map");
                try { mesh.run(); report(mesh, "C: no MCE + Map + dist(in=80,bot=60)"); }
                catch (Throwable e) { p("C FAIL: " + e.getMessage()); }
            }

            /* D: MCE + FreeTri, moderately fine */
            {
                Model m = ModelUtil.create("VD");
                m.component().create("comp1");
                m.param().set("L_ch", "6000[um]");
                m.param().set("w_ch", "100[um]");
                GeomSequence g = m.component("comp1").geom().create("geom1", 2);
                g.lengthUnit("um");
                g.create("r1", "Rectangle");
                g.feature("r1").set("size", new String[]{"L_ch", "w_ch"});
                g.feature("r1").set("pos", new String[]{"0", "-w_ch/2"});
                g.create("ln1", "LineSegment");
                g.feature("ln1").set("specify1", "coord");
                g.feature("ln1").set("specify2", "coord");
                g.feature("ln1").set("coord1", new String[]{"0[um]", "0[um]"});
                g.feature("ln1").set("coord2", new String[]{"L_ch", "0[um]"});
                g.run();
                g.create("mce1", "MeshControlEdges");
                g.feature("mce1").selection("input").set("ln1", 1);
                g.run("mce1");
                MeshSequence mesh = m.component("comp1").mesh().create("mesh1", "geom1");
                mesh.create("size1", "Size");
                mesh.feature("size1").set("hmax", "4[um]");
                mesh.feature("size1").set("hmin", "0.5[um]");
                mesh.feature("size1").set("hgrad", 1.3);
                mesh.create("ftri1", "FreeTri");
                try { mesh.run(); report(mesh, "D: MCE + FreeTri hmax=4um"); }
                catch (Throwable e) { p("D FAIL: " + e.getMessage()); }
            }

            /* ================= PART 3: API CHECKS (no solve) ================= */
            {
                Model m = ModelUtil.create("VAPI");
                m.component().create("comp1");
                m.param().set("L_ch", "6000[um]");
                m.param().set("w_ch", "100[um]");
                m.param().set("c0", "1[mol/m^3]");
                GeomSequence g = m.component("comp1").geom().create("geom1", 2);
                g.lengthUnit("um");
                g.create("r1", "Rectangle");
                g.feature("r1").set("size", new String[]{"L_ch", "w_ch"});
                g.feature("r1").set("pos", new String[]{"0", "-w_ch/2"});
                g.run();

                m.func().create("cTab", "Interpolation");
                p("Interpolation props: " + Arrays.toString(m.func("cTab").properties()));
                for (String pr : new String[]{"source", "interp", "extrap", "argunit",
                                             "fununit", "funcname", "nargs"}) {
                    try {
                        p("  allowed " + pr + ": " + Arrays.toString(
                            m.func("cTab").getAllowedPropertyValues(pr)));
                    } catch (Throwable e) { p("  allowed " + pr + " FAIL: " + e.getMessage()); }
                }
                try {
                    m.func("cTab").set("source", "table");
                    m.func("cTab").set("funcname", "cTab");
                    m.func("cTab").set("nargs", "1");
                    m.func("cTab").set("table", new String[][]{
                        {"-5e-5", "0.0"}, {"0", "0.5"}, {"5e-5", "1.0"}});
                    m.func("cTab").set("interp", "linear");
                    m.func("cTab").set("argunit", "m");
                    m.func("cTab").set("fununit", "mol/m^3]");
                    p("SET interpolation table OK");
                } catch (Throwable e) {
                    p("interpolation table FAIL: " + e.getMessage());
                }
                try {
                    m.result().numerical().create("gevTab", "EvalGlobal");
                    m.result().numerical("gevTab").setIndex("expr", "cTab(0[um])", 0);
                    m.result().numerical("gevTab").run();
                    p("cTab(0) = " + m.result().numerical("gevTab").getReal()[0][0]);
                } catch (Throwable e) { p("cTab evaluate FAIL: " + e.getMessage()); }

                /* 1D plot x-axis control */
                m.result().dataset().create("cln", "CutLine2D");
                m.result().dataset("cln").setIndex("genpoints", "L_ch", 0, 0);
                m.result().dataset("cln").setIndex("genpoints", "-w_ch/2", 0, 1);
                m.result().dataset("cln").setIndex("genpoints", "L_ch", 1, 0);
                m.result().dataset("cln").setIndex("genpoints", "w_ch/2", 1, 1);
                m.result().create("pg1", "PlotGroup1D");
                m.result("pg1").create("lngr1", "LineGraph");
                m.result("pg1").feature("lngr1").set("data", "cln");
                m.result("pg1").feature("lngr1").set("expr", "c");
                for (String pr : new String[]{"xdata", "xdataexpr", "legend", "legendmethod"}) {
                    try {
                        p("LineGraph allowed " + pr + ": " + Arrays.toString(
                            m.result("pg1").feature("lngr1").getAllowedPropertyValues(pr)));
                    } catch (Throwable e) { p("LineGraph " + pr + " FAIL: " + e.getMessage()); }
                }
                for (String v : new String[]{"expr", "default"}) {
                    try {
                        m.result("pg1").feature("lngr1").set("xdata", v);
                        p("  SET xdata=" + v + " OK");
                    } catch (Throwable e) { p("  SET xdata=" + v + " FAIL: " + e.getMessage()); }
                }
                try {
                    m.result("pg1").feature("lngr1").set("xdata", "expr");
                    m.result("pg1").feature("lngr1").set("xdataexpr", "y");
                    p("  SET xdata=expr xdataexpr=y OK");
                } catch (Throwable e) { p("  SET xdataexpr FAIL: " + e.getMessage()); }
                /* Global plot of the interpolation function over y */
                m.result("pg1").create("glob1", "Global");
                try {
                    m.result("pg1").feature("glob1").set("expr", new String[]{"cTab(y)"});
                    m.result("pg1").feature("glob1").set("unit", new String[]{"mol/m^3"});
                    m.result("pg1").feature("glob1").set("xdata", "expr");
                    m.result("pg1").feature("glob1").set("xdataexpr", "y");
                    m.result("pg1").feature("glob1").set("xmin", "-50[um]");
                    m.result("pg1").feature("glob1").set("xmax", "50[um]");
                    p("Global plot of cTab(y) configured OK");
                } catch (Throwable e) { p("Global plot FAIL: " + e.getMessage()); }
            }

            p("PROBE16 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("PROBE16 ABORTED: " + e);
        }
    }
}
