import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

/** Probe 6: the Release node's InitialPosition options and its sub-features,
 *  plus the DEP force permittivity/conductivity/omega semantics.
 */
public class Probe4f {
    static void al(String tag, Object f, String p) {
        try {
            System.out.println("ALLOWED " + tag + "." + p + " : "
                + Arrays.toString(((com.comsol.model.PropFeature) f).getAllowedPropertyValues(p)));
        } catch (Throwable e) {
            System.out.println("ALLOWED FAIL " + tag + "." + p + " : " + e.getMessage());
        }
    }
    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe4f");
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

            model.component("comp1").physics("fpt").create("rel1", "Release", 3);
            model.component("comp1").physics("fpt").feature("rel1").selection().all();

            System.out.println("REL1 subfeature tags: "
                + Arrays.toString(model.component("comp1").physics("fpt").feature("rel1").feature().tags()));

            for (String p : new String[]{"InitialPosition", "ReleasedParticleProperties",
                                         "DpSpecify", "CMD", "GSD", "dsm", "dpro",
                                         "VelocitySpecification", "N", "SamplingFromDistribution"}) {
                al("rel1", model.component("comp1").physics("fpt").feature("rel1"), p);
            }

            // try creating the grid-release SUBFEATURE under rel1
            for (String c : new String[]{"ReleaseFromGrid", "ReleaseGrid", "Grid",
                                         "ReleaseFromPoint", "ReleaseFromBoundary"}) {
                try {
                    model.component("comp1").physics("fpt").feature("rel1")
                         .create("grid_" + c, c, -1);
                    System.out.println("SUBFEAT OK under rel1: " + c);
                    System.out.println("   props: " + Arrays.toString(
                        model.component("comp1").physics("fpt").feature("rel1")
                             .feature("grid_" + c).properties()));
                } catch (Throwable e) {
                    System.out.println("SUBFEAT FAIL under rel1: " + c + " : " + e.getMessage());
                }
            }

            // DEP force: exact property semantics
            model.component("comp1").physics("fpt").create("deff1", "DielectrophoreticForce", 3);
            model.component("comp1").physics("fpt").feature("deff1").selection().all();
            for (String p : new String[]{"epsilonr", "sigma", "omega", "AngularFrequency",
                                         "E", "V", "coordinateSystem"}) {
                al("deff1", model.component("comp1").physics("fpt").feature("deff1"), p);
            }
            // particle props
            System.out.println("pp1 allowed erp_mat : "
                + Arrays.toString(model.component("comp1").physics("fpt").feature("pp1")
                    .getAllowedPropertyValues("erp_mat")));
            System.out.println("pp1 allowed sigmap_mat : "
                + Arrays.toString(model.component("comp1").physics("fpt").feature("pp1")
                    .getAllowedPropertyValues("sigmap_mat")));

            System.out.println("PROBE4F DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE4F ABORTED: " + e);
        }
    }
}
