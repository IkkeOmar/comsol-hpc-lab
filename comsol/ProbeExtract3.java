import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.io.*;
import java.util.Arrays;

/**
 * ProbeExtract3: load the ALREADY SOLVED full-geometry electrostatics model
 * (ProbeField4_PF4.mph) and find a method that extracts the vertical |E|^2
 * profile.  The dataset names clnGap/clnEle already exist in that file, so
 * new ones are used here.
 *
 * Methods tried:
 *   M1  numerical "Global"     on a CutLine3D dataset
 *   M2  numerical "EvalGlobal" on a CutLine3D dataset
 *   M3  export create(tag, DATASET, "Data")   <- the documented 3-arg form
 *   M4  export create(tag, "Data") + set("data", DATASET)
 *   M5  numerical "Interp"     with a coordinate table
 */
public class ProbeExtract3 {

    static final String SRC = "<project>/comsol/ProbeField4_PF4.mph";
    static final String OUT = "<project>/comsol/";

    public static void main(String[] args) {
        try {
            Model m = ModelUtil.load("PF4", SRC);
            System.out.println("Z0 sol tags = " + Arrays.toString(m.sol().tags()));
            System.out.println("Z0 dataset tags = " + Arrays.toString(m.result().dataset().tags()));

            final String EXPR = "es.Ex^2+es.Ey^2+es.Ez^2";

            // vertical cut line at the first gap centre (x = 65 um), y = 50 um
            m.result().dataset().create("clnZ", "CutLine3D");
            m.result().dataset("clnZ").set("data", "dset1");
            m.result().dataset("clnZ").setIndex("genpoints", "65[um]", 0, 0);
            m.result().dataset("clnZ").setIndex("genpoints", "50[um]", 0, 1);
            m.result().dataset("clnZ").setIndex("genpoints", "0[um]",  0, 2);
            m.result().dataset("clnZ").setIndex("genpoints", "65[um]", 1, 0);
            m.result().dataset("clnZ").setIndex("genpoints", "50[um]", 1, 1);
            m.result().dataset("clnZ").setIndex("genpoints", "100[um]", 1, 2);
            System.out.println("Z0 cutline clnZ created");

            // ---- M1: Global on the cut line ------------------------------
            try {
                m.result().numerical().create("z1", "Global");
                m.result().numerical("z1").set("data", "clnZ");
                m.result().numerical("z1").set("expr", EXPR);
                m.result().numerical("z1").run();
                double[][] v = m.result().numerical("z1").getReal();
                System.out.println("M1 Global(cutline): rows=" + v.length
                        + " cols=" + (v.length > 0 ? v[0].length : 0));
                for (int r = 0; r < Math.min(v.length, 5); r++) {
                    StringBuilder sb = new StringBuilder("M1 r" + r + ":");
                    for (int c = 0; c < Math.min(v[r].length, 8); c++) {
                        sb.append(String.format(" %.6g", v[r][c]));
                    }
                    System.out.println(sb);
                }
            } catch (Throwable t) {
                System.out.println("M1 Global FAILED: " + t.getMessage());
            }

            // ---- M2: EvalGlobal on the cut line --------------------------
            try {
                m.result().numerical().create("z2", "EvalGlobal");
                m.result().numerical("z2").set("data", "clnZ");
                m.result().numerical("z2").set("expr", EXPR);
                m.result().numerical("z2").run();
                double[][] v = m.result().numerical("z2").getReal();
                System.out.println("M2 EvalGlobal(cutline): rows=" + v.length
                        + " cols=" + (v.length > 0 ? v[0].length : 0));
            } catch (Throwable t) {
                System.out.println("M2 EvalGlobal FAILED: " + t.getMessage());
            }

            // ---- M3: export with the DATASET tag (documented 3-arg form) --
            try {
                m.result().export().create("eZ", "clnZ", "Data");
                m.result().export("eZ").set("filename", OUT + "Eprofile_M3.csv");
                m.result().export("eZ").run();
                System.out.println("M3 export(dataset) OK -> Eprofile_M3.csv");
            } catch (Throwable t) {
                System.out.println("M3 export(dataset) FAILED: " + t.getMessage());
            }

            // ---- M4: export with set("data", dataset) --------------------
            try {
                m.result().export().create("eZ2", "Data");
                m.result().export("eZ2").set("data", "clnZ");
                m.result().export("eZ2").set("filename", OUT + "Eprofile_M4.csv");
                m.result().export("eZ2").run();
                System.out.println("M4 export(set data) OK -> Eprofile_M4.csv");
            } catch (Throwable t) {
                System.out.println("M4 export(set data) FAILED: " + t.getMessage());
            }

            // ---- M5: Interp with a coordinate table -----------------------
            try {
                m.result().numerical().create("z5", "Interp");
                m.result().numerical("z5").set("data", "dset1");
                m.result().numerical("z5").set("expr", EXPR);
                m.result().numerical("z5").set("edim", 3);
                m.result().table().create("tblC", "Table");
                m.result().table("tblC").set("tablemethod", "spreadsheet");
                int N = 21;
                double[][] rows = new double[N][3];
                for (int i = 0; i < N; i++) {
                    rows[i][0] = 65e-6;
                    rows[i][1] = 50e-6;
                    rows[i][2] = i * 2.5e-6;
                }
                // try the documented "table" property of the Interp feature
                for (String key : new String[]{"coord", "coords", "table", "point"}) {
                    try {
                        if (key.equals("table")) {
                            m.result().table("tblC").set("data", rows);
                            m.result().numerical("z5").set("table", "tblC");
                        } else {
                            String[][] c = new String[3][N];
                            for (int i = 0; i < N; i++) {
                                c[0][i] = (rows[i][0] * 1e6) + "[um]";
                                c[1][i] = (rows[i][1] * 1e6) + "[um]";
                                c[2][i] = (rows[i][2] * 1e6) + "[um]";
                            }
                            m.result().numerical("z5").set(key, c);
                        }
                        m.result().numerical("z5").run();
                        double[][] v = m.result().numerical("z5").getReal();
                        System.out.println("M5 Interp '" + key + "': rows=" + v.length
                                + " cols=" + (v.length > 0 ? v[0].length : 0));
                        for (int r = 0; r < Math.min(v.length, 3); r++) {
                            StringBuilder sb = new StringBuilder("M5 r" + r + ":");
                            for (int c2 = 0; c2 < Math.min(v[r].length, 8); c2++) {
                                sb.append(String.format(" %.6g", v[r][c2]));
                            }
                            System.out.println(sb);
                        }
                        break;
                    } catch (Throwable t2) {
                        System.out.println("M5 Interp '" + key + "' failed: " + t2.getMessage());
                    }
                }
            } catch (Throwable t) {
                System.out.println("M5 Interp FAILED: " + t.getMessage());
            }

            // ---- M6: write the numbers straight to CSV from Java ----------
            // Uses the Global evaluation if it worked (M1); otherwise gives up.
            try {
                m.result().numerical("z1").run();
                double[][] v = m.result().numerical("z1").getReal();
                PrintWriter pw = new PrintWriter(new FileWriter(OUT + "Eprofile_java.csv"));
                pw.println("row,col,value");
                for (int r = 0; r < v.length; r++) {
                    for (int c = 0; c < v[r].length; c++) {
                        pw.printf("%d,%d,%.10e%n", r, c, v[r][c]);
                    }
                }
                pw.close();
                System.out.println("M6 wrote Eprofile_java.csv (" + v.length + " rows)");
            } catch (Throwable t) {
                System.out.println("M6 write FAILED: " + t.getMessage());
            }

            System.out.println("PROBEEXTRACT3 DONE");
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("PROBEEXTRACT3 ABORTED: " + t);
        }
    }
}
