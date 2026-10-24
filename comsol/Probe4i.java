import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

/** Probe 9: (a) ReleaseGrid's true dimension, (b) Box + Union selection API,
 *  (c) an ec-only frequency solve with the FULL error text.
 */
public class Probe4i {
    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe4i");
            model.component().create("comp1");
            model.component("comp1").geom().create("geom1", 3);
            model.component("comp1").geom("geom1").lengthUnit("um");
            model.component("comp1").geom("geom1").create("blk1", "Block");
            model.component("comp1").geom("geom1").feature("blk1")
                 .set("size", new String[]{"200", "100", "100"});
            model.component("comp1").geom("geom1").run();

            model.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1");

            // (a) ReleaseGrid at odd dims
            for (int d : new int[]{-1, 4, 5}) {
                try {
                    model.component("comp1").physics("fpt").create("rg" + d, "ReleaseGrid", d);
                    System.out.println("RELEASEGRID OK dim=" + d);
                    System.out.println("  props: " + Arrays.toString(
                        model.component("comp1").physics("fpt").feature("rg" + d).properties()));
                } catch (Throwable e) {
                    System.out.println("RELEASEGRID FAIL dim=" + d + " : " + e.getMessage());
                }
            }
            // also try as a child of a domain-level Release node
            model.component("comp1").physics("fpt").create("rel1", "Release", 3);
            model.component("comp1").physics("fpt").feature("rel1").selection().all();
            for (int d : new int[]{-1, 0, 1, 2, 3}) {
                try {
                    model.component("comp1").physics("fpt").feature("rel1")
                         .create("rgc" + d, "ReleaseGrid", d);
                    System.out.println("RELEASEGRID child-of-rel OK dim=" + d);
                    System.out.println("  props: " + Arrays.toString(
                        model.component("comp1").physics("fpt").feature("rel1")
                             .feature("rgc" + d).properties()));
                } catch (Throwable e) {
                    System.out.println("RELEASEGRID child-of-rel FAIL dim=" + d + " : " + e.getMessage());
                }
            }
            // Release node's own coordinate-ish properties
            try {
                System.out.println("rel1 props: " + Arrays.toString(
                    model.component("comp1").physics("fpt").feature("rel1").properties()));
            } catch (Throwable e) { /* ignore */ }

            // (b) Box selection + Union selection
            try {
                model.component("comp1").selection().create("box1", "Box");
                model.component("comp1").selection("box1").set("entitydim", 2);
                model.component("comp1").selection("box1").set("xmin", "10");
                model.component("comp1").selection("box1").set("xmax", "20");
                model.component("comp1").selection("box1").set("ymin", "-1");
                model.component("comp1").selection("box1").set("ymax", "101");
                model.component("comp1").selection("box1").set("zmin", "-1");
                model.component("comp1").selection("box1").set("zmax", "1");
                model.component("comp1").selection("box1").set("condition", "inside");
                System.out.println("BOX SELECTION OK, n=" +
                    model.component("comp1").selection("box1").entities(2).length);
            } catch (Throwable e) {
                System.out.println("BOX SELECTION FAIL: " + e.getMessage());
            }
            try {
                model.component("comp1").selection().create("un1", "Union");
                model.component("comp1").selection("un1").set("input", new String[]{"box1"});
                System.out.println("UNION SELECTION OK, n=" +
                    model.component("comp1").selection("un1").entities(2).length);
            } catch (Throwable e) {
                System.out.println("UNION SELECTION FAIL: " + e.getMessage());
            }

            System.out.println("PROBE9 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE9 ABORTED: " + e);
        }
    }
}
