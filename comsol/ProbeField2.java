import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/**
 * ProbeField2: ELECTROSTATICS ONLY (no flow, no transient) - cheap.
 *
 * Purpose: pick the mesh and verify the electrostatics setup against an
 * independent 1D Fourier reference for a periodic coplanar electrode array.
 *
 * The reference (course Ex-3 / textbook "Manipulation by electrical fields"):
 * an infinite array of coplanar electrodes, electrode width d, gap d,
 * period 2d, potentials alternating 0 / V0.  With
 *      u = x/d  in [0,2)  (period 2d)
 * the potential in the fluid is the Fourier series
 *      V(x,z) = sum_{n odd}  (4 V0/(n pi)) sin(n pi x/d) exp(-n pi z/d)
 * so at the GAP CENTRE  u = 1  (x = d) the field is purely vertical with
 *      E_z(x=d,z) = -(4 V0/(pi d)) sum_{n odd} (n) exp(-n pi z/d) / n ...
 * and the quantity the DEP force uses is  d|E_peak|^2/dz.
 *
 * The probe extracts, from the COMSOL solution, the vertical profile of
 * |E|^2 at the gap centre and at an electrode centre, for several mesh
 * candidates, and writes them as CSV for the Python comparison.
 *
 * The export uses a CutLine3D on the frequency-domain dataset.  To avoid the
 * dataset-tag ambiguity of a two-step study, THIS probe uses a study with a
 * SINGLE Frequency step, so the dataset is unambiguous.
 */
public class ProbeField2 {

    static final String OUT = "<project>/comsol/";

    public static void main(String[] args) {
        try {
            // ---------------- parameters ----------------------------------
            // Full-length geometry (1000 um, 45 electrodes) so the mesh
            // candidate that matters for the production run is measured.
            int nEl = 45;
            String L = "1000[um]";
            String tag = (args.length > 0) ? args[0] : "A";

            Model m = ModelUtil.create("PF2" + tag);
            m.param().set("L_ch", L);
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
            // floor edges: all edges lying in the z = 0 plane
            g.create("selFloorE", "BoxSelection");
            g.feature("selFloorE").set("entitydim", 1);
            g.feature("selFloorE").set("condition", "allvertices");
            g.feature("selFloorE").set("xmin", "-1[um]"); g.feature("selFloorE").set("xmax", "L_ch+1[um]");
            g.feature("selFloorE").set("ymin", "-1[um]"); g.feature("selFloorE").set("ymax", "W_ch+1[um]");
            g.feature("selFloorE").set("zmin", "-1[um]"); g.feature("selFloorE").set("zmax", "1[um]");
            // x-running floor edges (both endpoints at y = 0 or at y = W_ch)
            g.create("selEx0", "BoxSelection");
            g.feature("selEx0").set("entitydim", 1);
            g.feature("selEx0").set("condition", "allvertices");
            g.feature("selEx0").set("xmin", "-1[um]"); g.feature("selEx0").set("xmax", "L_ch+1[um]");
            g.feature("selEx0").set("ymin", "-1[um]"); g.feature("selEx0").set("ymax", "1[um]");
            g.feature("selEx0").set("zmin", "-1[um]"); g.feature("selEx0").set("zmax", "1[um]");
            g.create("selEx1", "BoxSelection");
            g.feature("selEx1").set("entitydim", 1);
            g.feature("selEx1").set("condition", "allvertices");
            g.feature("selEx1").set("xmin", "-1[um]"); g.feature("selEx1").set("xmax", "L_ch+1[um]");
            g.feature("selEx1").set("ymin", "W_ch-1[um]"); g.feature("selEx1").set("ymax", "W_ch+1[um]");
            g.feature("selEx1").set("zmin", "-1[um]"); g.feature("selEx1").set("zmax", "1[um]");
            // y-running floor edges = all floor edges MINUS the x-running ones
            g.create("selEy", "DifferenceSelection");
            g.feature("selEy").set("entitydim", 1);
            g.feature("selEy").set("add", "selFloorE");
            g.feature("selEy").set("subtract", new String[]{"selEx0", "selEx1"});
            g.run();

            int[] exE = m.selection("geom1_selEx0").entities(1);
            int[] exE1 = m.selection("geom1_selEx1").entities(1);
            int[] eyE = m.selection("geom1_selEy").entities(1);
            int[] floorE = m.selection("geom1_selFloorE").entities(1);
            System.out.println("G0 floor edges=" + floorE.length
                    + "  x-running=" + (exE.length + exE1.length)
                    + "  y-running=" + eyE.length);
            java.util.List<Integer> exAll = new java.util.ArrayList<Integer>();
            for (int v : exE) exAll.add(v);
            for (int v : exE1) exAll.add(v);
            int[] exAllA = new int[exAll.size()];
            for (int i = 0; i < exAllA.length; i++) exAllA[i] = exAll.get(i);

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
            System.out.println("G0 Terminal faces=" + termB.length + " Ground faces=" + gndB.length);

            // ---------------- material -------------------------------------
            m.component("comp1").material().create("mat1", "Common");
            m.component("comp1").material("mat1").label("Water");
            m.component("comp1").material("mat1").propertyGroup("def")
                .set("dynamicviscosity", "1e-3[Pa*s]")
                .set("density", "1000[kg/m^3]")
                .set("electricconductivity", "5.5[us/m]")
                .set("relpermittivity", "80");

            // ---------------- electrostatics --------------------------------
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
            // Each candidate: {hmax, hmin, hgrad, nx (elements per x-edge),
            //                  ny (elements per y-edge)}
            String[][] cand = {
                {"A", "15[um]", "0.4[um]", "1.30", "5",  "10"},
                {"B", "10[um]", "0.3[um]", "1.20", "5",  "10"},
                {"C", "8[um]",  "0.3[um]", "1.15", "4",  "10"},
                {"D", "6[um]",  "0.25[um]", "1.12", "4", "8"},
            };
            for (String[] c : cand) {
                String ctag = "m_" + c[0];
                long t0 = System.currentTimeMillis();
                try {
                    MeshSequence mesh = m.component("comp1").mesh().create(ctag, "geom1");
                    mesh.create("size1", "Size");
                    mesh.feature("size1").set("hmax", c[1]);
                    mesh.feature("size1").set("hmin", c[2]);
                    mesh.feature("size1").set("hgrad", c[3]);
                    mesh.create("distX", "Distribution");
                    mesh.feature("distX").selection().geom(1).set(exAllA);
                    mesh.feature("distX").set("numelem", c[4]);
                    mesh.create("distY", "Distribution");
                    mesh.feature("distY").selection().geom(1).set(eyE);
                    mesh.feature("distY").set("numelem", c[5]);
                    mesh.create("ftet1", "FreeTet");
                    mesh.run();
                    long dt = System.currentTimeMillis() - t0;
                    System.out.println("G1 mesh " + c[0] + " hmax=" + c[1] + " hgrad=" + c[3]
                            + " nx=" + c[4] + " ny=" + c[5]
                            + " -> ELEMENTS=" + mesh.stat().getNumElem()
                            + " minQ=" + mesh.stat().getMinQuality()
                            + " meanQ=" + mesh.stat().getMeanQuality()
                            + " [" + dt + " ms]");
                } catch (Throwable t) {
                    System.out.println("G1 mesh " + c[0] + " FAILED: " + t.getMessage());
                }
            }
            System.out.println("G1 meshes done");

            // ---------------- solve frequency domain once ------------------
            m.study().create("std1");
            m.study("std1").create("freq", "Frequency");
            m.study("std1").feature("freq").set("plist", "10[MHz]");
            m.study("std1").createAutoSequences("all");
            t0 = System.currentTimeMillis();
            m.study("std1").run();
            System.out.println("=== ES FREQUENCY SOLVE DONE [" + (System.currentTimeMillis()-t0) + " ms] ===");
            System.out.println("G2 sol tags = " + Arrays.toString(m.sol().tags()));
            System.out.println("G2 dataset tags = " + Arrays.toString(m.result().dataset().tags()));

            // ---------------- E-field profiles -------------------------------
            // gap centre:  electrode 0 spans x = 50..60 um, electrode 1 spans
            // 70..80 um  ->  the first gap is 60..70 um, centre x = 65 um.
            // electrode centre: x = 55 um.
            exportProfile(m, "clnGap", "65[um]", "gap");
            exportProfile(m, "clnEle", "55[um]", "ele");

            System.out.println("PROBEFIELD2 DONE");
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("PROBEFIELD2 ABORTED: " + t);
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
            System.out.println("G3 exported E profile (" + label + ") to E_" + label + ".csv");
        } catch (Throwable t) {
            System.out.println("G3 E profile " + label + " FAILED: " + t.getMessage());
        }
    }
}
