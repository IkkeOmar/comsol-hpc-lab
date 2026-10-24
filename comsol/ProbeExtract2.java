import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/**
 * ProbeExtract2: load the ALREADY SOLVED full-geometry electrostatics model
 * (ProbeField4_PF4.mph: 45 electrodes, 1000 x 100 x 100 um, 10 MHz, ES
 * frequency-domain solution present as dset1/sol1) and find a method that
 * actually extracts the vertical |E|^2 profile.
 *
 * Methods tried (all on the same model, so only the extraction differs):
 *   M1  numerical "Global"    on a CutLine3D dataset
 *   M2  numerical "EvalGlobal" on a CutLine3D dataset
 *   M3  numerical "Interp"    with explicit coordinates
 *   M4  export create(tag,"Data") + set("plotgroup", pg)
 *   M5  export create(tag, pg, "Data")   (the 3-arg form that failed before)
 *   M6  numerical "EvalPoint" on a CutPoint3D dataset
 *
 * The first method that returns a non-empty array wins; the profile is then
 * written to CSV with plain Java (no COMSOL export feature involved).
 */
public class ProbeExtract2 {

    static final String SRC = "<project>/comsol/ProbeField4_PF4.mph";
    static final String OUT = "<project>/comsol/";

    public static void main(String[] args) {
        try {
            Model m = ModelUtil.load("PF4", SRC);
            System.out.println("Y0 sol tags = " + Arrays.toString(m.sol().tags()));
            System.out.println("Y0 dataset tags = " + Arrays.toString(m.result().dataset().tags()));

            // vertical cut lines: first gap centre x=65um, first electrode centre x=55um
            m.result().dataset().create("clnGap", "CutLine3D");
            m.result().dataset("clnGap").set("data", "dset1");
            m.result().dataset("clnGap").setIndex("genpoints", "65[um]", 0, 0);
            m.result().dataset("clnGap").setIndex("genpoints", "50[um]", 0, 1);
            m.result().dataset("clnGap").setIndex("genpoints", "0[um]",  0, 2);
            m.result().dataset("clnGap").setIndex("genpoints", "65[um]", 1, 0);
            m.result().dataset("clnGap").setIndex("genpoints", "50[um]", 1, 1);
            m.result().dataset("clnGap").setIndex("genpoints", "100[um]", 1, 2);
            System.out.println("Y0 cutline created");

            String EXPR = "es.Ex^2+es.Ey^2+es.Ez^2";

            // ---- M1: Global on the cut line --------------------------------
            try {
                m.result().numerical().create("g1", "Global");
                m.result().numerical("g1").set("data", "clnGap");
                m.result().numerical("g1").set("expr", EXPR);
                m.result().numerical("g1").run();
                double[][] v = m.result().numerical("g1").getReal();
                System.out.println("M1 Global(cutline): rows=" + v.length
                        + " cols=" + (v.length > 0 ? v[0].length : 0));
                for (int r = 0; r < Math.min(v.length, 6); r++) {
                    StringBuilder sb = new StringBuilder("M1 r" + r + ":");
                    for (int c = 0; c < Math.min(v[r].length, 8); c++) {
                        sb.append(String.format(" %.6g", v[r][c]));
                    }
                    System.out.println(sb);
                }
            } catch (Throwable t) {
                System.out.println("M1 Global FAILED: " + t.getMessage());
            }

            // ---- M2: EvalGlobal on the cut line ---------------------------
            try {
                m.result().numerical().create("g2", "EvalGlobal");
                m.result().numerical("g2").set("data", "clnGap");
                m.result().numerical("g2").set("expr", EXPR);
                m.result().numerical("g2").run();
                double[][] v = m.result().numerical("g2").getReal();
                System.out.println("M2 EvalGlobal(cutline): rows=" + v.length
                        + " cols=" + (v.length > 0 ? v[0].length : 0));
                for (int r = 0; r < Math.min(v.length, 6); r++) {
                    StringBuilder sb = new StringBuilder("M2 r" + r + ":");
                    for (int c = 0; c < Math.min(v[r].length, 8); c++) {
                        sb.append(String.format(" %.6g", v[r][c]));
                    }
                    System.out.println(sb);
                }
            } catch (Throwable t) {
                System.out.println("M2 EvalGlobal FAILED: " + t.getMessage());
            }

            // ---- M3: Interp with explicit coordinates ----------------------
            try {
                m.result().numerical().create("ip1", "Interp");
                m.result().numerical("ip1").set("data", "dset1");
                m.result().numerical("ip1").set("expr", EXPR);
                m.result().numerical("ip1").set("edim", 3);
                int N = 21;
                String[][] coord = new String[3][N];
                for (int i = 0; i < N; i++) {
                    coord[0][i] = "65[um]";
                    coord[1][i] = "50[um]";
                    coord[2][i] = (i * 2.5) + "[um]";
                }
                for (String[] key : new String[][]{{"coord"}, {"coords"}, {"coordarr"},
                                                 {"table"}, {"point"}, {"points"}}) {
                    try {
                        m.result().numerical("ip1").set(key[0], coord);
                        m.result().numerical("ip1").run();
                        double[][] v = m.result().numerical("ip1").getReal();
                        System.out.println("M3 Interp with '" + key[0] + "': rows=" + v.length
                                + " cols=" + (v.length > 0 ? v[0].length : 0));
                        for (int r = 0; r < Math.min(v.length, 3); r++) {
                            StringBuilder sb = new StringBuilder("M3 r" + r + ":");
                            for (int c = 0; c < Math.min(v[r].length, 8); c++) {
                                sb.append(String.format(" %.6g", v[r][c]));
                            }
                            System.out.println(sb);
                        }
                        break;
                    } catch (Throwable t2) {
                        System.out.println("M3 Interp '" + key[0] + "' failed: " + t2.getMessage());
                    }
                }
            } catch (Throwable t) {
                System.out.println("M3 Interp FAILED: " + t.getMessage());
            }

            // ---- M4/M5: plot group + Data export --------------------------
            try {
                m.result().create("pgE", "PlotGroup1D");
                m.result("pgE").create("lg1", "LineGraph");
                m.result("pgE").feature("lg1").set("data", "clnGap");
                m.result("pgE").feature("lg1").set("expr", EXPR);
                m.result("pgE").feature("lg1").set("xdata", "arc");
                m.result("pgE").run();
                System.out.println("M4 pgE plot OK");
            } catch (Throwable t) {
                System.out.println("M4 plot group FAILED: " + t.getMessage());
            }
            try {
                m.result().export().create("e1", "Data");
                m.result().export("e1").set("plotgroup", "pgE");
                m.result().export("e1").set("filename", OUT + "Eprofile_M4.csv");
                m.result().export("e1").run();
                System.out.println("M4 export OK -> Eprofile_M4.csv");
            } catch (Throwable t) {
                System.out.println("M4 export FAILED: " + t.getMessage());
            }
            try {
                m.result().export().create("e2", "pgE", "Data");
                m.result().export("e2").set("filename", OUT + "Eprofile_M5.csv");
                m.result().export("e2").run();
                System.out.println("M5 export OK -> Eprofile_M5.csv");
            } catch (Throwable t) {
                System.out.println("M5 export FAILED: " + t.getMessage());
            }

            // ---- M6: EvalPoint on a CutPoint3D ---------------------------
            try {
                m.result().dataset().create("cpt", "CutPoint3D");
                m.result().dataset("cpt").set("data", "dset1");
                m.result().dataset("cpt").setIndex("pointx", "65[um]", 0);
                m.result().dataset("cpt").setIndex("pointy", "50[um]", 0);
                m.result().dataset("cpt").setIndex("pointz", "5[um]", 0);
                m.result().numerical().create("pev", "EvalPoint");
                m.result().numerical("pev").set("data", "cpt");
                m.result().numerical("pev").set("expr", EXPR);
                m.result().numerical("pev").run();
                double[][] v = m.result().numerical("pev").getReal();
                System.out.println("M6 EvalPoint: rows=" + v.length
                        + " cols=" + (v.length > 0 ? v[0].length : 0)
                        + "  first=" + (v.length > 0 ? v[0][0] : Double.NaN));
            } catch (Throwable t) {
                System.out.println("M6 EvalPoint FAILED: " + t.getMessage());
            }

            System.out.println("PROBEEXTRACT2 DONE");
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("PROBEEXTRACT2 ABORTED: " + t);
        }
    }
}
