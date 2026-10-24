import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

/** Probe 4: find the Release-from-Grid feature dimension, and enumerate
 *  property names by INLINING properties() (no reflection -> no SecurityManager
 *  interference).  The compiler tells us which type declares properties().
 */
public class Probe4d {

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe4d");
            model.component().create("comp1");
            model.component("comp1").geom().create("geom1", 3);
            model.component("comp1").geom("geom1").lengthUnit("um");
            model.component("comp1").geom("geom1").create("blk1", "Block");
            model.component("comp1").geom("geom1").feature("blk1")
                 .set("size", new String[]{"200", "100", "100"});
            model.component("comp1").geom("geom1").run();

            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            model.component("comp1").physics().create("es", "Electrostatics", "geom1");
            model.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1");

            System.out.println("=== FPT feature tags ===");
            System.out.println(Arrays.toString(model.component("comp1").physics("fpt").feature().tags()));

            // ---- dimension sweep for the release feature ----
            String[] cands = {"ReleaseGrid", "Release", "Inlet"};
            int[] dims = {0, 1, 2, 3};
            for (String c : cands) {
                for (int d : dims) {
                    try {
                        model.component("comp1").physics("fpt").create("t_" + c + d, c, d);
                        System.out.println("RELFEAT OK: type=" + c + " dim=" + d);
                    } catch (Throwable e) {
                        System.out.println("RELFEAT FAIL: type=" + c + " dim=" + d
                                + " : " + e.getMessage());
                    }
                }
            }

            // ---- inline property enumeration (compiler proves the declaring type) ----
            model.component("comp1").physics("fpt").create("deff1", "DielectrophoreticForce", 3);
            model.component("comp1").physics("fpt").feature("deff1").selection().all();
            try {
                String[] p = model.component("comp1").physics("fpt").feature("deff1").properties();
                System.out.println("PROPS deff1: " + Arrays.toString(p));
            } catch (Throwable e) {
                System.out.println("PROPS FAIL deff1: " + e);
            }
            try {
                String[] p = model.component("comp1").physics("fpt").feature("pp1").properties();
                System.out.println("PROPS pp1: " + Arrays.toString(p));
            } catch (Throwable e) {
                System.out.println("PROPS FAIL pp1: " + e);
            }

            System.out.println("PROBE4D DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE4D ABORTED: " + e);
        }
    }
}
