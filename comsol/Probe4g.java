import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

/** Probe 7: Release-node grid properties (direct API calls, no cast), the fpt
 *  physics-level prop groups (for "ignore inertial terms"), and whether
 *  Electrostatics supports a Frequency Domain study step.
 */
public class Probe4g {
    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe4g");
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

            // ---- fpt physics-level property groups ----
            try {
                System.out.println("FPT prop groups: " + Arrays.toString(
                    model.component("comp1").physics("fpt").prop().tags()));
                for (String t : model.component("comp1").physics("fpt").prop().tags()) {
                    System.out.println("FPTPROP " + t + " : " + Arrays.toString(
                        model.component("comp1").physics("fpt").prop(t).properties()));
                }
            } catch (Throwable e) {
                System.out.println("FPT prop FAIL: " + e);
            }
            try {
                System.out.println("FPT physics props: " + Arrays.toString(
                    model.component("comp1").physics("fpt").feature("dpcon1").properties()));
            } catch (Throwable e) {
                System.out.println("FPT physics properties() FAIL: " + e);
            }

            // ---- Release node allowed values (direct calls) ----
            model.component("comp1").physics("fpt").create("rel1", "Release", 3);
            model.component("comp1").physics("fpt").feature("rel1").selection().all();
            for (String p : new String[]{"InitialPosition", "dpro", "DpSpecify",
                                         "CMD", "GSD", "dsm", "ReleasedParticleProperties",
                                         "VelocitySpecification", "N", "tstart", "tlast",
                                         "rt", "SamplingFromTimeDistribution",
                                         "TimeDistributionFunction"}) {
                try {
                    System.out.println("ALLOWED rel1." + p + " : " + Arrays.toString(
                        model.component("comp1").physics("fpt").feature("rel1")
                             .getAllowedPropertyValues(p)));
                } catch (Throwable e) {
                    System.out.println("ALLOWED FAIL rel1." + p + " : " + e.getMessage());
                }
            }

            // ---- does the Release node expose grid coordinate properties? ----
            try {
                model.component("comp1").physics("fpt").feature("rel1").set("InitialPosition", "Grid");
                System.out.println("SET InitialPosition=Grid OK");
                System.out.println("REL1 props after Grid: " + Arrays.toString(
                    model.component("comp1").physics("fpt").feature("rel1").properties()));
            } catch (Throwable e) {
                System.out.println("SET InitialPosition=Grid FAIL: " + e.getMessage());
            }

            // ---- Electrostatics + Frequency study step? ----
            model.study().create("std1");
            try {
                model.study("std1").create("freq", "Frequency");
                model.study("std1").feature("freq").set("plist", "10[MHz]");
                System.out.println("STUDY Frequency step OK");
                System.out.println("freq props: " + Arrays.toString(
                    model.study("std1").feature("freq").properties()));
            } catch (Throwable e) {
                System.out.println("STUDY Frequency FAIL: " + e.getMessage());
            }

            // ---- test that es actually solves in frequency domain ----
            try {
                model.component("comp1").physics("es").create("term1", "Terminal", 2);
                model.component("comp1").physics("es").feature("term1").selection().set(1);
                model.component("comp1").physics("es").feature("term1").set("V0", "10[V]");
                model.component("comp1").physics("es").create("gnd1", "Ground", 2);
                model.component("comp1").physics("es").feature("gnd1").selection().set(2);
                model.sol().create("sol1");
                model.sol("sol1").study("std1");
                model.sol("sol1").attach("std1");
                model.sol("sol1").create("st1", "StudyStep");
                model.sol("sol1").feature("st1").set("study", "std1");
                model.sol("sol1").feature("st1").set("studystep", "freq");
                model.sol("sol1").create("v1", "Variables");
                model.sol("sol1").create("s1", "Stationary");
                model.sol("sol1").feature("s1").create("fc1", "FullyCoupled");
                model.sol("sol1").feature("s1").create("d1", "Direct");
                model.sol("sol1").runAll();
                System.out.println("ES FREQUENCY SOLVE OK");
            } catch (Throwable e) {
                System.out.println("ES FREQUENCY SOLVE FAIL: " + e);
            }

            System.out.println("PROBE4G DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE4G ABORTED: " + e);
        }
    }
}
