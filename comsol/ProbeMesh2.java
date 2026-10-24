import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

/**
 * Probe M2: WHY does the mesh come out empty?
 *
 * Measured facts going in:
 *   - autoMeshSize(5) on the same geometry gives 5059 elements.
 *   - A manual Size (+/- Distribution) sequence gives 0 elements.
 *   -> Hypothesis: a hand-built mesh sequence has NO mesher feature, so
 *      mesh.run() has nothing to run.
 * Test it directly: print the mesh sequence's feature tags in each case.
 */
public class ProbeMesh2 {

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("PM2");
            model.param().set("L_ch", "1000[um]");
            model.param().set("W_ch", "100[um]");
            model.param().set("H_ch", "100[um]");

            model.component().create("comp1");
            GeomSequence g = model.component("comp1").geom().create("geom1", 3);
            g.lengthUnit("um");
            g.create("blk1", "Block");
            g.feature("blk1").set("size", new String[]{"L_ch", "W_ch", "H_ch"});
            g.feature("blk1").set("pos",  new String[]{"0", "0", "0"});

            g.create("wp1", "WorkPlane");
            g.feature("wp1").set("planetype", "quick");
            g.feature("wp1").set("quickplane", "xy");
            GeomSequence wp = g.feature("wp1").geom();
            for (int i = 0; i < 45; i++) {
                wp.create("r" + i, "Rectangle");
                wp.feature("r" + i).set("size", new String[]{"10[um]", "W_ch"});
                wp.feature("r" + i).set("pos",  new String[]{"50[um] + " + i + "*20[um]", "0"});
            }
            g.run("wp1");

            g.create("selFloorE", "BoxSelection");
            g.feature("selFloorE").set("entitydim", 1);
            g.feature("selFloorE").set("condition", "allvertices");
            g.feature("selFloorE").set("xmin", "-1[um]"); g.feature("selFloorE").set("xmax", "L_ch+1[um]");
            g.feature("selFloorE").set("ymin", "-1[um]"); g.feature("selFloorE").set("ymax", "W_ch+1[um]");
            g.feature("selFloorE").set("zmin", "-1[um]"); g.feature("selFloorE").set("zmax", "1[um]");
            g.run();
            int[] floorE = model.selection("geom1_selFloorE").entities(1);
            System.out.println("floor edges = " + floorE.length);

            // ---------- A: autoMeshSize(5) ---------------------------------
            System.out.println("\n=== A: autoMeshSize(5) ===");
            try {
                MeshSequence mA = model.component("comp1").mesh().create("mA", "geom1");
                System.out.println("A tags BEFORE autoMeshSize: " + Arrays.toString(mA.feature().tags()));
                mA.autoMeshSize(5);
                System.out.println("A tags AFTER  autoMeshSize: " + Arrays.toString(mA.feature().tags()));
                long t0 = System.currentTimeMillis();
                mA.run();
                System.out.println("A ELEMENTS = " + mA.stat().getNumElem()
                        + "  minQ=" + mA.stat().getMinQuality()
                        + "  [" + (System.currentTimeMillis()-t0) + " ms]");
            } catch (Throwable t) {
                System.out.println("A FAILED: " + t.getMessage());
            }

            // ---------- B: manual Size only, NO explicit mesher ------------
            System.out.println("\n=== B: manual Size, no mesher ===");
            try {
                MeshSequence mB = model.component("comp1").mesh().create("mB", "geom1");
                System.out.println("B tags at creation: " + Arrays.toString(mB.feature().tags()));
                mB.create("size1", "Size");
                mB.feature("size1").set("hmax", "20[um]");
                mB.feature("size1").set("hmin", "0.8[um]");
                mB.feature("size1").set("hgrad", 1.3);
                System.out.println("B tags after Size: " + Arrays.toString(mB.feature().tags()));
                mB.run();
                System.out.println("B ELEMENTS = " + mB.stat().getNumElem());
            } catch (Throwable t) {
                System.out.println("B FAILED: " + t.getMessage());
            }

            // ---------- C: manual Size + explicit FreeTet ------------------
            System.out.println("\n=== C: manual Size + explicit FreeTet ===");
            try {
                MeshSequence mC = model.component("comp1").mesh().create("mC", "geom1");
                mC.create("size1", "Size");
                mC.feature("size1").set("hmax", "20[um]");
                mC.feature("size1").set("hmin", "0.8[um]");
                mC.feature("size1").set("hgrad", 1.3);
                mC.create("ftet1", "FreeTet");
                System.out.println("C tags: " + Arrays.toString(mC.feature().tags()));
                long t0 = System.currentTimeMillis();
                mC.run();
                System.out.println("C ELEMENTS = " + mC.stat().getNumElem()
                        + "  minQ=" + mC.stat().getMinQuality()
                        + "  types=" + Arrays.toString(mC.stat().getTypes())
                        + "  [" + (System.currentTimeMillis()-t0) + " ms]");
            } catch (Throwable t) {
                System.out.println("C FAILED: " + t.getMessage());
            }

            // ---------- D: Size + Distribution + explicit FreeTet ---------
            System.out.println("\n=== D: Size + Distribution + FreeTet ===");
            try {
                MeshSequence mD = model.component("comp1").mesh().create("mD", "geom1");
                mD.create("size1", "Size");
                mD.feature("size1").set("hmax", "20[um]");
                mD.feature("size1").set("hmin", "0.8[um]");
                mD.feature("size1").set("hgrad", 1.3);
                mD.create("dist1", "Distribution");
                mD.feature("dist1").selection().geom(1).set(floorE);
                mD.feature("dist1").set("numelem", 5);
                mD.create("ftet1", "FreeTet");
                long t0 = System.currentTimeMillis();
                mD.run();
                System.out.println("D ELEMENTS = " + mD.stat().getNumElem()
                        + "  minQ=" + mD.stat().getMinQuality()
                        + "  types=" + Arrays.toString(mD.stat().getTypes())
                        + "  [" + (System.currentTimeMillis()-t0) + " ms]");
            } catch (Throwable t) {
                System.out.println("D FAILED: " + t.getMessage());
            }

            // ---------- E: does a plain Box (no electrodes) mesh fine? -----
            System.out.println("\n=== E: plain box, manual Size + FreeTet ===");
            try {
                Model m2 = ModelUtil.create("PM2b");
                m2.component().create("comp1");
                GeomSequence g2 = m2.component("comp1").geom().create("geom1", 3);
                g2.lengthUnit("um");
                g2.create("blk1", "Block");
                g2.feature("blk1").set("size", new String[]{"1000", "100", "100"});
                g2.run();
                MeshSequence mE = m2.component("comp1").mesh().create("mE", "geom1");
                mE.create("size1", "Size");
                mE.feature("size1").set("hmax", "20[um]");
                mE.create("ftet1", "FreeTet");
                mE.run();
                System.out.println("E ELEMENTS = " + mE.stat().getNumElem()
                        + "  minQ=" + mE.stat().getMinQuality());
            } catch (Throwable t) {
                System.out.println("E FAILED: " + t.getMessage());
            }

            System.out.println("\nPROBEMESH2 DONE");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
