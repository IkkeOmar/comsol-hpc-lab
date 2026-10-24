import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/**
 * ProbeField4: ELECTROSTATICS ONLY (no flow, no transient).
 *
 * 1. Measures mesh candidates on the FULL production geometry
 *    (1000 x 100 x 100 um, 45 coplanar electrodes).
 * 2. Solves the frequency-domain electrostatics once and exports the
 *    vertical |E|^2 profile at the first gap centre (x = 65 um) and at the
 *    first electrode centre (x = 55 um).
 *
 * The exported profiles are compared in Python against
 *   (a) the exact 1D Fourier solution of a periodic coplanar electrode
 *       array (independent reference), and
 *   (b) the course/Ex-3 formula |grad E|^2 = (32/pi)(V0^2/d^3)exp(-pi y/d).
 *
 * Mesh note: the floor of the channel is split into 91 faces (45 electrode
 * faces + 44 gap faces + 2 end regions) and 274 edges (182 running in x,
 * 92 running in y).  A Distribution with numelem = 5 on ALL floor edges
 * gives 2 um spacing in x (5 per 10 um electrode) and 20 um in y (5 per
 * 100 um) - the field is y-invariant away from the side walls, so a coarse
 * y is physically fine and keeps the element count affordable.
 */
public class ProbeField4 {

    static final String OUT = "<project>/comsol/";

    public static void main(String[] args) {
        try {
            int nEl = 45;
            Model m = ModelUtil.create("PF4");

            m.param().set("L_ch", "1000[um]");
            m.param().set("W_ch", "100[um]");
            m.param().set("H_ch", "100[um]");
            m.param().set("elW",  "10[um]");
            m.param().set("elP",  "20[um]");
            m.param().set("elX0", "50[um]");
            m.param().set("V_el", "10[V]");

            m.component().create("comp1");
            GeomSequence g = m.component("comp1").geom().create("geom1", 3);
            g.lengthUnit("um");
            g.create("blk1", "Block");
            g.feature("blk1").set("size", new String[]{"L_ch", "W_ch", "H_ch"});
            g.feature("blk1").set("pos",  new String[]{"0", "0", "0"});
            g.create("wp1", "WorkPlane");
            g.feature("wp1").set("planetype", "quick");
            g.feature("wp1").set("quickplane", "xy");
            GeomSequence wp = g.feature("wp1").geom();
            for (int i = 0; i < nEl; i++) {
                wp.create("r" + i, "Rectangle");
                wp.feature("r" + i).set("size", new String[]{"elW", "W_ch"});
                wp.feature("r" + i).set("pos",  new String[]{"elX0 + " + i + "*elP", "0"});
            }
            g.run("wp1");

            for (int i = 0; i < nEl; i++) {
                String s = "selE" + i;
                g.create(s, "BoxSelection");
                g.feature(s).set("entitydim", 2);
                g.feature(s).set("condition", "allvertices");
                g.feature(s).set("xmin", "elX0 + " + i + "*elP - 1[um]");
                g.feature(s).set("xmax", "elX0 + " + i + "*elP + elW + 1[um]");
                g.feature(s).set("ymin", "-1[um]");
                g.feature(s).set("ymax", "W_ch+1[um]");
                g.feature(s).set("zmin", "-1[um]");
                g.feature(s).set("zmax", "1[um]");
            }
            g.create("selFloorE", "BoxSelection");
            g.feature("selFloorE").set("entitydim", 1);
            g.feature("selFloorE").set("condition", "allvertices");
            g.feature("selFloorE").set("xmin", "-1[um]"); g.feature("selFloorE").set("xmax", "L_ch+1[um]");
            g.feature("selFloorE").set("ymin", "-1[um]"); g.feature("selFloorE").set("ymax", "W_ch+1[um]");
            g.feature("selFloorE").set("zmin", "-1[um]"); g.feature("selFloorE").set("zmax", "1[um]");
            g.run();

            int[] floorE = m.selection("geom1_selFloorE").entities(1);
            System.out.println("G0 floor edges=" + floorE.length);

            java.util.List<Integer> termF = new java.util.ArrayList<Integer>();
            java.util.List<Integer> gndF = new java.util.ArrayList<Integer>();
            for (int i = 0; i < nEl; i++) {
                int[] s = m.selection("geom1_selE" + i).entities(2);
                if (s.length == 0) { System.out.println("G0 WARNING electrode " + i + " empty"); continue; }
                if (i % 2 == 0) { for (int v : s) termF.add(v); }
                else           { for (int v : s) gndF.add(v); }
            }
            int[] termB = new int[termF.size()];
            for (int i = 0; i < termB.length; i++) termB[i] = termF.get(i);
            int[] gndB = new int[gndF.size()];
            for (int i = 0; i < gndB.length; i++) gndB[i] = gndF.get(i);
            System.out.println("G0 Terminal=" + termB.length + " Ground=" + gndB.length);

            m.component("comp1").material().create("mat1", "Common");
            m.component("comp1").material("mat1").label("Water");
            m.component("comp1").material("mat1").propertyGroup("def")
                .set("dynamicviscosity", "1e-3[Pa*s]")
                .set("density", "1000[kg/m^3]")
                .set("electricconductivity", "5.5[us/m]")
                .set("relpermittivity", "80");

            m.component("comp1").physics().create("es", "Electrostatics", "geom1");
            m.component("comp1").physics("es").create("cc1", "ChargeConservation", 3);
            m.component("comp1").physics("es").feature("cc1").selection().all();
            m.component("comp1").physics("es").feature("cc1").set("epsilonr_mat", "from_mat");
            m.component("comp1").physics("es").feature("cc1").set("materialType", "from_mat");
            m.component("comp1").physics("es").create("gnd1", "Ground", 2);
            m.component("comp1").physics("es").feature("gnd1").selection().set(gndB);
            m.component("comp1").physics("es").create("term1", "Terminal", 2);
            m.component("comp1").physics("es").feature("term1").selection().set(termB);
            m.component("comp1").physics("es").feature("term1").set("TerminalType", "Voltage");
            m.component("comp1").physics("es").feature("term1").set("V0", "V_el");

            // ---------------- mesh candidates -------------------------------
            // {tag, hmax, hmin, hgrad, numElemPerFloorEdge}
            String[][] cand = {
                {"A", "20[um]", "0.5[um]", "1.30", "5"},
                {"B", "15[um]", "0.4[um]", "1.25", "5"},
                {"C", "10[um]", "0.3[um]", "1.20", "5"},
            };
            MeshSequence mesh = m.component("comp1").mesh().create("mesh1", "geom1");
            String best = "A";
            for (String[] c : cand) {
                long t0 = System.currentTimeMillis();
                try {
                    mesh.create("size1", "Size");
                    mesh.feature("size1").set("hmax", c[1]);
                    mesh.feature("size1").set("hmin", c[2]);
                    mesh.feature("size1").set("hgrad", c[3]);
                    mesh.create("dist1", "Distribution");
                    mesh.feature("dist1").selection().geom(1).set(floorE);
                    mesh.feature("dist1").set("numelem", c[4]);
                    mesh.create("ftet1", "FreeTet");
                    mesh.run();
                    System.out.println("G1 mesh " + c[0] + " hmax=" + c[1] + " hgrad=" + c[3]
                            + " n=" + c[4]
                            + " -> ELEMENTS=" + mesh.stat().getNumElem()
                            + " minQ=" + mesh.stat().getMinQuality()
                            + " meanQ=" + mesh.stat().getMeanQuality()
                            + " [" + (System.currentTimeMillis()-t0) + " ms]");
                    // keep only the LAST candidate's mesh in the tree
                    if (!c[0].equals("A")) {
                        mesh.feature().remove("size1");
                        mesh.feature().remove("dist1");
                    }
                    best = c[0];
                } catch (Throwable t) {
                    System.out.println("G1 mesh " + c[0] + " FAILED: " + t.getMessage());
                }
            }
            System.out.println("G1 last candidate = " + best);

            // ---------------- solve frequency domain ------------------------
            m.study().create("std1");
            m.study("std1").create("freq", "Frequency");
            m.study("std1").feature("freq").set("plist", "10[MHz]");
            m.study("std1").createAutoSequences("all");
            long t0 = System.currentTimeMillis();
            m.study("std1").run();
            System.out.println("=== ES FREQUENCY SOLVE DONE [" + (System.currentTimeMillis()-t0) + " ms] ===");
            System.out.println("G2 sol tags = " + Arrays.toString(m.sol().tags()));
            System.out.println("G2 dataset tags = " + Arrays.toString(m.result().dataset().tags()));

            exportProfile(m, "clnGap", "65[um]", "gap");
            exportProfile(m, "clnEle", "55[um]", "ele");
            System.out.println("PROBEFIELD4 DONE");
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("PROBEFIELD4 ABORTED: " + t);
        }
    }

    static void exportProfile(Model m, String dsTag, String xpos, String label) {
        try {
            m.result().dataset().create(dsTag, "CutLine3D");
            m.result().dataset(dsTag).set("data", "dset1");
            m.result().dataset(dsTag).setIndex("genpoints", xpos, 0, 0);
            m.result().dataset(dsTag).setIndex("genpoints", "50[um]", 0, 1);
            m.result().dataset(dsTag).setIndex("genpoints", "0[um]",  0, 2);
            m.result().dataset(dsTag).setIndex("genpoints", xpos, 1, 0);
            m.result().dataset(dsTag).setIndex("genpoints", "50[um]", 1, 1);
            m.result().dataset(dsTag).setIndex("genpoints", "100[um]", 1, 2);
            m.result().create("pg" + dsTag, "PlotGroup1D");
            m.result("pg" + dsTag).create("lg", "LineGraph");
            m.result("pg" + dsTag).feature("lg").set("data", dsTag);
            m.result("pg" + dsTag).feature("lg").set("expr", "es.Ex^2+es.Ey^2+es.Ez^2");
            m.result("pg" + dsTag).feature("lg").set("xdata", "arc");
            m.result("pg" + dsTag).run();
            m.result().export().create("exp" + dsTag, "pg" + dsTag, "Data");
            m.result().export("exp" + dsTag).set("filename", OUT + "E_" + label + ".csv");
            m.result().export("exp" + dsTag).run();
            System.out.println("G3 exported E profile (" + label + ") -> E_" + label + ".csv");
        } catch (Throwable t) {
            System.out.println("G3 E profile " + label + " FAILED: " + t.getMessage());
        }
    }
}
