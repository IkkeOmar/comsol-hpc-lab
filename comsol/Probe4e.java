import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

/** Probe 5: enumerate the Release(domain) node's grid properties, the
 *  DielectrophoreticForce allowed values, and the es Terminal properties.
 */
public class Probe4e {

    static void props(String label, String[] p) {
        System.out.println("PROPS " + label + " (" + (p == null ? 0 : p.length) + "): " + Arrays.toString(p));
    }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe4e");
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

            // Release node (domain level)
            model.component("comp1").physics("fpt").create("rel1", "Release", 3);
            model.component("comp1").physics("fpt").feature("rel1").selection().all();
            props("fpt.rel1", model.component("comp1").physics("fpt").feature("rel1").properties());

            // DEP force
            model.component("comp1").physics("fpt").create("deff1", "DielectrophoreticForce", 3);
            model.component("comp1").physics("fpt").feature("deff1").selection().all();
            for (String cand : new String[]{"SpecifyForceUsing", "UsePPR", "ParticlesToAffect",
                                           "AffectedParticleProperties", "epsilonr_mat",
                                           "sigma_mat", "E_src", "omega", "AngularFrequency"}) {
                try {
                    System.out.println("ALLOWED deff1." + cand + " : "
                            + Arrays.toString(model.component("comp1").physics("fpt")
                                    .feature("deff1").getAllowedPropertyValues(cand)));
                } catch (Throwable e) {
                    System.out.println("ALLOWED FAIL deff1." + cand + " : " + e.getMessage());
                }
            }

            // Drag force
            model.component("comp1").physics("fpt").create("df1", "DragForce", 3);
            model.component("comp1").physics("fpt").feature("df1").selection().all();
            props("fpt.df1", model.component("comp1").physics("fpt").feature("df1").properties());
            try {
                System.out.println("ALLOWED df1.u_src : " + Arrays.toString(
                        model.component("comp1").physics("fpt").feature("df1")
                                .getAllowedPropertyValues("u_src")));
            } catch (Throwable e) {
                System.out.println("ALLOWED FAIL df1.u_src : " + e.getMessage());
            }

            // Gravity
            model.component("comp1").physics("fpt").create("gf1", "GravityForce", 3);
            model.component("comp1").physics("fpt").feature("gf1").selection().all();
            props("fpt.gf1", model.component("comp1").physics("fpt").feature("gf1").properties());

            // es terminal / ground
            model.component("comp1").physics("es").create("term1", "Terminal", 2);
            model.component("comp1").physics("es").feature("term1").selection().set(1);
            props("es.term1", model.component("comp1").physics("es").feature("term1").properties());

            // spf inlet
            for (String tag : model.component("comp1").physics("spf").feature().tags()) {
                props("spf." + tag, model.component("comp1").physics("spf").feature(tag).properties());
            }

            System.out.println("PROBE4E DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE4E ABORTED: " + e);
        }
    }
}
