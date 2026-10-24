import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

/** Probe 10: (a) electrode-array geometry that yields 45 selectable faces on
 *  the channel floor, (b) ReleaseGrid's real property names, (c) ec feature
 *  names for Terminal/Ground.
 */
public class Probe4j {

    static void buildBlockAndWorkplane(Model model, boolean usePartition) {
        model.component("comp1").geom("geom1").create("blk1", "Block");
        model.component("comp1").geom("geom1").feature("blk1")
             .set("size", new String[]{"1000", "100", "100"});
        model.component("comp1").geom("geom1").feature("blk1")
             .set("pos", new String[]{"0", "0", "0"});

        model.component("comp1").geom("geom1").create("wp1", "WorkPlane");
        model.component("comp1").geom("geom1").feature("wp1").set("quickplane", "xy");
        model.component("comp1").geom("geom1").feature("wp1").set("unite", true);
        for (int i = 0; i < 45; i++) {
            String tag = "r" + (i + 1);
            model.component("comp1").geom("geom1").feature("wp1").geom().create(tag, "Rectangle");
            model.component("comp1").geom("geom1").feature("wp1").geom().feature(tag)
                 .set("size", new String[]{"10", "100"});
            model.component("comp1").geom("geom1").feature("wp1").geom().feature(tag)
                 .set("pos", new String[]{String.valueOf(50 + 20 * i), "0"});
        }
    }

    /** count faces lying in the z=0 plane, and the ones inside each electrode x-window */
    static void reportFloor(Model model, String label) {
        try {
            model.component("comp1").selection().create("floorBox", "Box");
            model.component("comp1").selection("floorBox").set("entitydim", 2);
            model.component("comp1").selection("floorBox").set("xmin", "-1");
            model.component("comp1").selection("floorBox").set("xmax", "1001");
            model.component("comp1").selection("floorBox").set("ymin", "-1");
            model.component("comp1").selection("floorBox").set("ymax", "101");
            model.component("comp1").selection("floorBox").set("zmin", "-0.001");
            model.component("comp1").selection("floorBox").set("zmax", "0.001");
            model.component("comp1").selection("floorBox").set("condition", "inside");
            int n = model.component("comp1").selection("floorBox").entities(2).length;
            System.out.println(label + " FLOOR-FACES(n)=" + n);

            int firstCount = -1, ok = 0;
            for (int i = 0; i < 45; i++) {
                String tag = "el" + i;
                model.component("comp1").selection().create(tag, "Box");
                model.component("comp1").selection(tag).set("entitydim", 2);
                model.component("comp1").selection(tag).set("xmin", String.valueOf(50 + 20 * i - 0.001));
                model.component("comp1").selection(tag).set("xmax", String.valueOf(60 + 20 * i + 0.001));
                model.component("comp1").selection(tag).set("ymin", "-1");
                model.component("comp1").selection(tag).set("ymax", "101");
                model.component("comp1").selection(tag).set("zmin", "-0.001");
                model.component("comp1").selection(tag).set("zmax", "0.001");
                model.component("comp1").selection(tag).set("condition", "inside");
                int c = model.component("comp1").selection(tag).entities(2).length;
                if (i == 0) firstCount = c;
                if (c == 1) ok++;
                if (i < 3 || c != 1) {
                    System.out.println(label + " electrode[" + i + "] count=" + c);
                }
            }
            System.out.println(label + " ELECTRODES-WITH-EXACTLY-1-FACE=" + ok + "/45"
                    + " (first count=" + firstCount + ")");
            System.out.println(label + " TOTAL-BOUNDARIES=" + model.component("comp1").geom("geom1").getNBoundaries());
        } catch (Throwable e) {
            System.out.println(label + " REPORT FAIL: " + e);
        }
    }

    public static void main(String[] args) {
        // ---------- variant A: workplane united (no PartitionFaces) ----------
        try {
            Model mA = ModelUtil.create("PA");
            mA.component().create("comp1");
            mA.component("comp1").geom().create("geom1", 3);
            mA.component("comp1").geom("geom1").lengthUnit("um");
            buildBlockAndWorkplane(mA, false);
            mA.component("comp1").geom("geom1").run();
            reportFloor(mA, "A(unite-workplane)");
        } catch (Throwable e) {
            System.out.println("VARIANT A FAIL: " + e);
        }

        // ---------- variant B: workplane + PartitionFaces ----------
        try {
            Model mB = ModelUtil.create("PB");
            mB.component().create("comp1");
            mB.component("comp1").geom().create("geom1", 3);
            mB.component("comp1").geom("geom1").lengthUnit("um");
            buildBlockAndWorkplane(mB, true);
            mB.component("comp1").geom("geom1").run();
            reportFloor(mB, "B(partition)");
        } catch (Throwable e) {
            System.out.println("VARIANT B FAIL: " + e);
        }

        // ---------- ReleaseGrid property names ----------
        try {
            Model mR = ModelUtil.create("PR");
            mR.component().create("comp1");
            mR.component("comp1").geom().create("geom1", 3);
            mR.component("comp1").geom("geom1").lengthUnit("um");
            mR.component("comp1").geom("geom1").create("blk1", "Block");
            mR.component("comp1").geom("geom1").feature("blk1")
                .set("size", new String[]{"1000", "100", "100"});
            mR.component("comp1").geom("geom1").run();
            mR.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1");
            mR.component("comp1").physics("fpt").create("relg1", "ReleaseGrid", -1);
            System.out.println("RELEASEGRID props: " + Arrays.toString(
                mR.component("comp1").physics("fpt").feature("relg1").properties()));
            for (String p : new String[]{"x0", "y0", "z0", "rt", "InitialPosition",
                                         "ReleasedParticleProperties", "DpSpecify", "dp0"}) {
                try {
                    System.out.println("  ALLOWED relg1." + p + " : " + Arrays.toString(
                        mR.component("comp1").physics("fpt").feature("relg1")
                             .getAllowedPropertyValues(p)));
                } catch (Throwable e) {
                    System.out.println("  ALLOWED FAIL relg1." + p + " : " + e.getMessage());
                }
            }
        } catch (Throwable e) {
            System.out.println("RELEASEGRID PROBE FAIL: " + e);
        }

        // ---------- ec feature names ----------
        try {
            Model mE = ModelUtil.create("PE");
            mE.component().create("comp1");
            mE.component("comp1").geom().create("geom1", 3);
            mE.component("comp1").geom("geom1").lengthUnit("um");
            mE.component("comp1").geom("geom1").create("blk1", "Block");
            mE.component("comp1").geom("geom1").feature("blk1")
                .set("size", new String[]{"100", "100", "100"});
            mE.component("comp1").geom("geom1").run();
            mE.component("comp1").physics().create("ec", "ConductiveMedia", "geom1");
            for (String[] ft : new String[][]{{"Terminal", "2"}, {"ElectricPotential", "2"},
                                              {"Ground", "2"}, {"Terminal", "3"}, {"Ground", "3"}}) {
                try {
                    mE.component("comp1").physics("ec").create("t_" + ft[0] + ft[1], ft[0], Integer.parseInt(ft[1]));
                    System.out.println("EC FEATURE OK: " + ft[0] + " dim=" + ft[1]);
                } catch (Throwable e) {
                    System.out.println("EC FEATURE FAIL: " + ft[0] + " dim=" + ft[1] + " : " + e.getMessage());
                }
            }
        } catch (Throwable e) {
            System.out.println("EC PROBE FAIL: " + e);
        }

        System.out.println("PROBE10 DONE");
    }
}
