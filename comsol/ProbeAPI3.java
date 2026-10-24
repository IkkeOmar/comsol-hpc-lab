import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/**
 * ProbeAPI3 - nail down the four remaining API unknowns, fast.
 *
 * Q1  Release from Grid: which property names hold the initial coordinates?
 *     (docs say qx0/qy0/qz0; an earlier probe used x0/y0/z0) and what does
 *     GridType have to be for 91 particles at (0, 5..95 um, 50 um)?
 * Q2  Numerical "Interp" derived value: does it exist, and what is the
 *     property that holds the evaluation coordinates?
 * Q3  DielectrophoreticForce: after setting E_src to the Electrostatics
 *     interface, does COMSOL fill the 3-vector E with es.Ex/es.Ey/es.Ez
 *     (full field) or only with the x component?
 * Q4  What does the Particle numerical evaluation return for the released
 *     particles (row = particle, column = time)?
 *
 * The model is a bare 200 x 100 x 100 um box with no electrodes - the
 * particle release and the post-processing do not need them, so this runs
 * in well under a minute.
 */
public class ProbeAPI3 {

    public static void main(String[] args) {
        try {
            Model m = ModelUtil.create("PA3");
            m.param().set("L", "200[um]");
            m.param().set("W", "100[um]");
            m.param().set("H", "100[um]");

            m.component().create("comp1");
            GeomSequence g = m.component("comp1").geom().create("geom1", 3);
            g.lengthUnit("um");
            g.create("blk1", "Block");
            g.feature("blk1").set("size", new String[]{"L", "W", "H"});
            g.run();

            m.component("comp1").material().create("mat1", "Common");
            m.component("comp1").material("mat1").propertyGroup("def")
                .set("dynamicviscosity", "1e-3[Pa*s]")
                .set("density", "1000[kg/m^3]")
                .set("electricconductivity", "5.5[us/m]")
                .set("relpermittivity", "80");

            // ---------------- Q3: DEP E vector ----------------------------
            m.component("comp1").physics().create("es", "Electrostatics", "geom1");
            m.component("comp1").physics("es").create("cc1", "ChargeConservation", 3);
            m.component("comp1").physics("es").feature("cc1").set("epsilonr_mat", "from_mat");
            m.component("comp1").physics("es").feature("cc1").set("materialType", "from_mat");

            m.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1");
            m.component("comp1").physics("fpt").feature("pp1").set("rhop_mat", "userdef");
            m.component("comp1").physics("fpt").feature("pp1").set("rhop", "1200[kg/m^3]");
            m.component("comp1").physics("fpt").feature("pp1").set("dp", "2.4[um]");
            m.component("comp1").physics("fpt").feature("pp1").set("erp_mat", "userdef");
            m.component("comp1").physics("fpt").feature("pp1").set("erp", "60");
            m.component("comp1").physics("fpt").feature("pp1").set("sigmap_mat", "userdef");
            m.component("comp1").physics("fpt").feature("pp1").set("sigmap", "0.01[S/m]");

            m.component("comp1").physics("fpt").create("deff1", "DielectrophoreticForce", 3);
            m.component("comp1").physics("fpt").feature("deff1").selection().all();
            try {
                m.component("comp1").physics("fpt").feature("deff1").set("E_src", "root.comp1.es.Ex");
                System.out.println("Q3 E_src=root.comp1.es.Ex OK");
            } catch (Throwable t) {
                System.out.println("Q3 E_src es.Ex FAILED: " + t.getMessage());
            }
            try {
                String[] ev = m.component("comp1").physics("fpt").feature("deff1")
                        .getStringArray("E");
                System.out.println("Q3 deff1.E (auto-filled) = " + Arrays.toString(ev));
            } catch (Throwable t) {
                System.out.println("Q3 could not read deff1.E: " + t.getMessage());
            }
            try {
                System.out.println("Q3 deff1.epsilonr = " + m.component("comp1").physics("fpt")
                        .feature("deff1").getString("epsilonr")
                        + "  sigma = " + m.component("comp1").physics("fpt")
                        .feature("deff1").getString("sigma"));
            } catch (Throwable t) {
                System.out.println("Q3 read epsilonr/sigma failed: " + t.getMessage());
            }

            // ---------------- Q1: Release from Grid ------------------------
            m.component("comp1").physics("fpt").create("relg1", "ReleaseGrid", -1);
            String[][] cand = {
                {"qx0", "0[um]"}, {"qy0", "range(5[um],1[um],95[um])"}, {"qz0", "50[um]"}
            };
            for (String[] c : cand) {
                try {
                    m.component("comp1").physics("fpt").feature("relg1")
                        .set(c[0], new String[]{c[1]});
                    System.out.println("Q1 set " + c[0] + " = {" + c[1] + "} OK");
                } catch (Throwable t) {
                    System.out.println("Q1 set " + c[0] + " FAILED: " + t.getMessage());
                }
            }
            try {
                m.component("comp1").physics("fpt").feature("relg1").set("GridType", "AllCombinations");
                System.out.println("Q1 GridType=AllCombinations OK");
            } catch (Throwable t) {
                System.out.println("Q1 GridType FAILED: " + t.getMessage());
            }
            try {
                System.out.println("Q1 qx0 back = " + Arrays.toString(m.component("comp1")
                        .physics("fpt").feature("relg1").getStringArray("qx0")));
                System.out.println("Q1 qy0 back = " + Arrays.toString(m.component("comp1")
                        .physics("fpt").feature("relg1").getStringArray("qy0")));
                System.out.println("Q1 qz0 back = " + Arrays.toString(m.component("comp1")
                        .physics("fpt").feature("relg1").getStringArray("qz0")));
            } catch (Throwable t) {
                System.out.println("Q1 readback failed: " + t.getMessage());
            }
            // also test the old names to see whether they are silently accepted
            for (String nm : new String[]{"x0", "y0", "z0"}) {
                try {
                    m.component("comp1").physics("fpt").feature("relg1").set(nm, "1[um]");
                    System.out.println("Q1 legacy " + nm + " accepted (SILENT?)");
                } catch (Throwable t) {
                    System.out.println("Q1 legacy " + nm + " rejected: " + t.getMessage());
                }
            }

            // ---------------- Q2: Interp numerical ------------------------
            try {
                m.result().numerical().create("intp", "Interp");
                System.out.println("Q2 numerical Interp created OK");
                java.util.List<String> pn = new java.util.ArrayList<String>();
                for (String p : m.result().numerical("intp").properties()) pn.add(p);
                System.out.println("Q2 Interp props = " + pn);
            } catch (Throwable t) {
                System.out.println("Q2 Interp create FAILED: " + t.getMessage());
            }
            for (String tn : new String[]{"Interp", "EvalPoint", "Global", "EvalGlobal"}) {
                try {
                    m.result().numerical().create("try_" + tn, tn);
                    java.util.List<String> pn = new java.util.ArrayList<String>();
                    for (String p : m.result().numerical("try_" + tn).properties()) pn.add(p);
                    System.out.println("Q2 type " + tn + " OK, props=" + pn);
                } catch (Throwable t) {
                    System.out.println("Q2 type " + tn + " FAILED: " + t.getMessage());
                }
            }

            // ---------------- solve + Q4 -----------------------------------
            m.study().create("std1");
            m.study("std1").create("time", "Transient");
            m.study("std1").feature("time").set("tlist", "range(0,0.05,0.2)");
            m.study("std1").createAutoSequences("all");
            m.study("std1").run();
            System.out.println("Q0 transient done, sol tags = "
                    + Arrays.toString(m.sol().tags()));

            // find the transient solution
            String tsol = null;
            for (String st : m.sol().tags()) tsol = st;
            System.out.println("Q4 using solution " + tsol);

            m.result().dataset().create("pdat", "Particle");
            m.result().dataset("pdat").set("solution", tsol);
            m.result().dataset("pdat").set("posdof",
                new String[]{"comp1.qx", "comp1.qy", "comp1.qz"});
            m.result().dataset("pdat").set("geom", "geom1");
            m.result().dataset("pdat").set("pgeom", "pgeom_fpt");
            m.result().dataset("pdat").set("pgeomspec", "fromphysics");
            m.result().dataset("pdat").set("physicsinterface", "fpt");

            for (String[] c : new String[][]{{"qx", "comp1.qx"}, {"qy", "comp1.qy"},
                                            {"qz", "comp1.qz"}}) {
                m.result().numerical().create("pev" + c[0], "Particle");
                m.result().numerical("pev" + c[0]).set("data", "pdat");
                m.result().numerical("pev" + c[0]).set("expr", c[1]);
                m.result().numerical("pev" + c[0]).run();
                double[][] v = m.result().numerical("pev" + c[0]).getReal();
                System.out.println("Q4 " + c[1] + " rows=" + v.length
                        + " cols=" + (v.length > 0 ? v[0].length : 0));
                for (int r = 0; r < Math.min(v.length, 3); r++) {
                    StringBuilder sb = new StringBuilder("Q4 " + c[1] + " r" + r + ":");
                    for (int cc = 0; cc < Math.min(v[r].length, 10); cc++) {
                        sb.append(String.format(" %.6g", v[r][cc]));
                    }
                    System.out.println(sb);
                }
                // last row -> the particle released at the largest y
                if (v.length > 1) {
                    StringBuilder sb = new StringBuilder("Q4 " + c[1] + " LAST r" + (v.length-1) + ":");
                    for (int cc = 0; cc < Math.min(v[v.length-1].length, 10); cc++) {
                        sb.append(String.format(" %.6g", v[v.length-1][cc]));
                    }
                    System.out.println(sb);
                }
            }

            System.out.println("PROBEAPI3 DONE");
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("PROBEAPI3 ABORTED: " + t);
        }
    }
}
