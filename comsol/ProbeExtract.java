import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.io.*;
import java.util.Arrays;

/**
 * ProbeExtract: load the ALREADY SOLVED reduced model (ProbeField_PF.mph)
 * and work out how to extract
 *   (a) the vertical |E|^2 profile  (Interp derived value)
 *   (b) the particle trajectories  (Particle derived value / table)
 * writing the numbers to CSV with plain Java, so that no COMSOL export
 * feature is involved (those have been the flaky part).
 *
 * Nothing is re-solved here, so this runs in seconds.
 */
public class ProbeExtract {

    static final String SRC = "<project>/comsol/ProbeField_PF.mph";
    static final String OUT = "<project>/comsol/";

    public static void main(String[] args) {
        try {
            Model m = ModelUtil.load("PF", SRC);
            System.out.println("X0 loaded. sol tags = " + Arrays.toString(m.sol().tags()));
            System.out.println("X0 dataset tags = " + Arrays.toString(m.result().dataset().tags()));

            // ---------- (a) |E|^2 profile via Interp ----------------------
            try {
                m.result().numerical().create("intE", "Interp");
                m.result().numerical("intE").set("data", "dset1");
                m.result().numerical("intE").set("expr", "es.Ex^2+es.Ey^2+es.Ez^2");
                int N = 41;
                String[][] coord = new String[3][N];
                for (int i = 0; i < N; i++) {
                    double z = i * 1.5;                     // um
                    coord[0][i] = (65 + z * 0) + "[um]";    // x = 65 um (gap centre)
                    coord[1][i] = "50[um]";                // y = 50 um
                    coord[2][i] = z + "[um]";               // z
                }
                m.result().numerical("intE").set("coord", coord);
                m.result().numerical("intE").run();
                double[][] v = m.result().numerical("intE").getReal();
                System.out.println("X1 Interp |E|^2: rows=" + v.length
                        + " cols=" + (v.length > 0 ? v[0].length : 0));
                for (int r = 0; r < v.length; r++) {
                    StringBuilder sb = new StringBuilder("X1 r" + r + ":");
                    for (int c = 0; c < Math.min(v[r].length, 8); c++) {
                        sb.append(String.format(" %.6g", v[r][c]));
                    }
                    System.out.println(sb);
                }
            } catch (Throwable t) {
                System.out.println("X1 Interp FAILED: " + t.getMessage());
            }

            // ---------- (b) particle trajectories via Particle eval --------
            // find the transient solution: it is the one whose dataset is a
            // Particle dataset; try each sol tag
            for (String st : m.sol().tags()) {
                try {
                    String ds = "pdat_" + st;
                    m.result().dataset().create(ds, "Particle");
                    m.result().dataset(ds).set("solution", st);
                    m.result().dataset(ds).set("posdof",
                        new String[]{"comp1.qx", "comp1.qy", "comp1.qz"});
                    m.result().dataset(ds).set("geom", "geom1");
                    m.result().dataset(ds).set("pgeom", "pgeom_fpt");
                    m.result().dataset(ds).set("pgeomspec", "fromphysics");
                    m.result().dataset(ds).set("physicsinterface", "fpt");

                    m.result().numerical().create("pev_" + st, "Particle");
                    m.result().numerical("pev_" + st).set("data", ds);
                    m.result().numerical("pev_" + st).set("expr", "comp1.qx");
                    m.result().numerical("pev_" + st).run();
                    double[][] p = m.result().numerical("pev_" + st).getReal();
                    System.out.println("X2 sol=" + st + " Particle qx: rows=" + p.length
                            + " cols=" + (p.length > 0 ? p[0].length : 0));
                    for (int r = 0; r < Math.min(p.length, 6); r++) {
                        StringBuilder sb = new StringBuilder("X2 r" + r + ":");
                        for (int c = 0; c < Math.min(p[r].length, 12); c++) {
                            sb.append(String.format(" %.6g", p[r][c]));
                        }
                        System.out.println(sb);
                    }
                } catch (Throwable t) {
                    System.out.println("X2 sol=" + st + " FAILED: " + t.getMessage());
                }
            }

            // ---------- (c) full trajectory: 3 expressions, all times -------
            try {
                m.result().dataset().create("pdatX", "Particle");
                m.result().dataset("pdatX").set("solution", "sol3");
                m.result().dataset("pdatX").set("posdof",
                    new String[]{"comp1.qx", "comp1.qy", "comp1.qz"});
                m.result().dataset("pdatX").set("geom", "geom1");
                m.result().dataset("pdatX").set("pgeom", "pgeom_fpt");
                m.result().dataset("pdatX").set("pgeomspec", "fromphysics");
                m.result().dataset("pdatX").set("physicsinterface", "fpt");

                m.result().numerical().create("pevX", "Particle");
                m.result().numerical("pevX").set("data", "pdatX");
                m.result().numerical("pevX").set("expr",
                    new String[]{"comp1.qx", "comp1.qy", "comp1.qz"});
                m.result().table().create("tblX", "Table");
                m.result().numerical("pevX").set("table", "tblX");
                m.result().numerical("pevX").setResult();
                double[][] t3 = m.result().table("tblX").getReal();
                System.out.println("X3 table rows=" + t3.length
                        + " cols=" + (t3.length > 0 ? t3[0].length : 0));
                for (int r = 0; r < Math.min(t3.length, 8); r++) {
                    StringBuilder sb = new StringBuilder("X3 r" + r + ":");
                    for (int c = 0; c < Math.min(t3[r].length, 12); c++) {
                        sb.append(String.format(" %.6g", t3[r][c]));
                    }
                    System.out.println(sb);
                }
            } catch (Throwable t) {
                System.out.println("X3 multi-expr Particle FAILED: " + t.getMessage());
            }

            System.out.println("PROBEEXTRACT DONE");
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("PROBEEXTRACT ABORTED: " + t);
        }
    }
}
