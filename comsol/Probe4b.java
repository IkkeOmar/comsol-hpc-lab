import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.PhysicsFeature;
import java.util.Arrays;

/** Probe 2: enumerate the ACTUAL property names and allowed values via
 *  feature.properties() / getAllowedPropertyValues().  This replaces guessing.
 *  Also enumerates the available physics feature TYPE strings for fpt.
 */
public class Probe4b {
    static void dumpProps(String label, PhysicsFeature f) {
        try {
            String[] p = f.properties();
            System.out.println("PROPS " + label + " (" + p.length + "): " + Arrays.toString(p));
        } catch (Throwable e) {
            System.out.println("PROPS FAIL " + label + " : " + e.getMessage());
        }
    }
    static void dumpAllowed(String label, com.comsol.model.PhysicsFeature f, String prop) {
        try {
            String[] v = f.getAllowedPropertyValues(prop);
            System.out.println("ALLOWED " + label + "." + prop + " : " + Arrays.toString(v));
        } catch (Throwable e) {
            System.out.println("ALLOWED FAIL " + label + "." + prop + " : " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe4b");
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

            System.out.println("=== FPT existing feature tags ===");
            System.out.println(Arrays.toString(model.component("comp1").physics("fpt").feature().tags()));

            dumpProps("fpt", model.component("comp1").physics("fpt"));
            for (String tag : model.component("comp1").physics("fpt").feature().tags()) {
                dumpProps("fpt." + tag, model.component("comp1").physics("fpt").feature(tag));
            }

            // DEP force node
            model.component("comp1").physics("fpt").create("deff1", "DielectrophoreticForce", 3);
            model.component("comp1").physics("fpt").feature("deff1").selection().all();
            dumpProps("fpt.deff1", model.component("comp1").physics("fpt").feature("deff1"));
            for (String cand : new String[]{"E_src", "UsePPR", "epsr", "epso", "epsf",
                                           "sigma", "sigmam", "sigmap", "epsilon",
                                           "ParticlesToAffect", "AffectedParticleProperties"}) {
                dumpAllowed("deff1", model.component("comp1").physics("fpt").feature("deff1"), cand);
            }

            // release feature candidates
            String[] cands = {"ReleaseGrid", "ReleaseFromGrid", "Grid", "ReleaseFromBoundary",
                              "Release", "Inlet", "ReleaseFromPoint", "ReleaseFromEdge"};
            for (String c : cands) {
                try {
                    model.component("comp1").physics("fpt").create("rel_" + c, c, 0);
                    System.out.println("RELFEAT OK (dim0): " + c);
                    dumpProps("fpt.rel_" + c, model.component("comp1").physics("fpt").feature("rel_" + c));
                } catch (Throwable e) {
                    System.out.println("RELFEAT FAIL (dim0): " + c + " : " + e.getMessage());
                }
            }
            // try the same candidates at boundary dimension
            for (String c : new String[]{"ReleaseGrid", "ReleaseFromGrid", "Grid",
                                         "ReleaseFromBoundary", "Release", "Inlet"}) {
                try {
                    model.component("comp1").physics("fpt").create("relb_" + c, c, 2);
                    System.out.println("RELFEAT OK (dim2): " + c);
                    dumpProps("fpt.relb_" + c, model.component("comp1").physics("fpt").feature("relb_" + c));
                } catch (Throwable e) {
                    System.out.println("RELFEAT FAIL (dim2): " + c + " : " + e.getMessage());
                }
            }

            // Drag force + gravity props
            model.component("comp1").physics("fpt").create("df1", "DragForce", 3);
            model.component("comp1").physics("fpt").feature("df1").selection().all();
            dumpProps("fpt.df1", model.component("comp1").physics("fpt").feature("df1"));
            dumpAllowed("df1", model.component("comp1").physics("fpt").feature("df1"), "u_src");

            model.component("comp1").physics("fpt").create("gf1", "GravityForce", 3);
            model.component("comp1").physics("fpt").feature("gf1").selection().all();
            dumpProps("fpt.gf1", model.component("comp1").physics("fpt").feature("gf1"));

            // particle properties node
            dumpProps("fpt.pp1", model.component("comp1").physics("fpt").feature("pp1"));

            // es terminal/ground
            model.component("comp1").physics("es").create("term1", "Terminal", 2);
            model.component("comp1").physics("es").feature("term1").selection().set(1);
            dumpProps("es.term1", model.component("comp1").physics("es").feature("term1"));

            System.out.println("PROBE2 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE2 ABORTED: " + e);
        }
    }
}
