import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** Probe 6: enumerate the default feature TAGS of the TDS interface
 *  (DilutedSpecies) in 2D, and the properties of each, so we bind the
 *  diffusion coefficient to the right node instead of guessing "df1".
 */
public class Probe6 {
    static void dumpProps(String label, PhysicsFeature f) {
        try {
            System.out.println("PROPS " + label + " (" + f.properties().length + "): "
                             + Arrays.toString(f.properties()));
        } catch (Throwable e) {
            System.out.println("PROPS FAIL " + label + " : " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe6");
            model.component().create("comp1");
            model.component("comp1").geom().create("geom1", 2);
            model.component("comp1").geom("geom1").lengthUnit("um");
            model.component("comp1").geom("geom1").create("r1", "Rectangle");
            model.component("comp1").geom("geom1").feature("r1")
                 .set("size", new String[]{"1000", "100"});
            model.component("comp1").geom("geom1").run();

            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            Physics tds = model.component("comp1").physics("tds");
            System.out.println("=== TDS default features ===");
            System.out.println(Arrays.toString(tds.feature().tags()));
            for (String tag : tds.feature().tags()) {
                dumpProps("tds." + tag, tds.feature(tag));
            }

            // also the shallow-channel prop group on spf
            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            System.out.println("=== spf prop groups ===");
            System.out.println(Arrays.toString(
                model.component("comp1").physics("spf").prop().tags()));

            // and the Step function properties
            model.func().create("step1", "Step");
            System.out.println("=== step1 props ===");
            System.out.println(Arrays.toString(model.func("step1").properties()));

            System.out.println("PROBE6 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE6 ABORTED: " + e);
        }
    }
}
