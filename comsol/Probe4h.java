import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

/** Probe 8: definitive sweep for the "Release from Grid" feature type + the
 *  release-specification prop group + AC physics E_src options.
 */
public class Probe4h {
    static String[] cands = {
        "ReleaseGrid", "GridRelease", "ReleaseFromGrid", "Grid",
        "Inlet", "Release", "ReleaseOnAxis", "ReleaseGridOnAxis",
        "ReleaseFromPoints", "PointRelease", "ParticleRelease",
        "ReleasePoint", "ReleasePoints", "ReleaseSurface", "ReleaseFromSurface",
        "ReleaseFromDomain", "ReleaseFromBoundary", "ReleaseFromEdge",
        "ReleaseFromPoint", "ReleaseFromLine"
    };

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe4h");
            model.component().create("comp1");
            model.component("comp1").geom().create("geom1", 3);
            model.component("comp1").geom("geom1").lengthUnit("um");
            model.component("comp1").geom("geom1").create("blk1", "Block");
            model.component("comp1").geom("geom1").feature("blk1")
                 .set("size", new String[]{"200", "100", "100"});
            model.component("comp1").geom("geom1").run();
            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            model.component("comp1").physics().create("ec", "ConductiveMedia", "geom1");
            model.component("comp1").physics().create("es", "Electrostatics", "geom1");
            model.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1");

            // ---- prop-group selectors ----
            String[][] pg = {
                {"ParticleReleaseSpecification", "ParticleReleaseSpecification"},
                {"Formulation", "Formulation"},
                {"ParticleSizeDistribution", "ParticleSizeDistribution"},
                {"EquationForm", "form"},
                {"StoreExtra", "StoreExtra"}
            };
            for (String[] pair : pg) {
                try {
                    System.out.println("PGALLOWED " + pair[0] + "." + pair[1] + " : "
                        + Arrays.toString(model.component("comp1").physics("fpt")
                              .prop(pair[0]).getAllowedPropertyValues(pair[1])));
                } catch (Throwable e) {
                    System.out.println("PGALLOWED FAIL " + pair[0] + " : " + e.getMessage());
                }
            }

            // ---- feature-type sweep, all dims ----
            for (String c : cands) {
                StringBuilder sb = new StringBuilder();
                for (int d = 0; d <= 3; d++) {
                    try {
                        model.component("comp1").physics("fpt").create("x" + c + d, c, d);
                        sb.append(" OK(dim=").append(d).append(")");
                    } catch (Throwable e) {
                        String m = e.getMessage();
                        if (m == null) m = "";
                        if (!m.contains("Unknown_feature_ID")) {
                            sb.append(" WRONGDIM(").append(d).append(")");
                        }
                    }
                }
                if (sb.length() > 0) System.out.println("TYPE " + c + " :" + sb);
            }

            // ---- Release node: does setting a release-spec change its options? ----
            model.component("comp1").physics("fpt").create("rel1", "Release", 3);
            model.component("comp1").physics("fpt").feature("rel1").selection().all();
            try {
                model.component("comp1").physics("fpt").prop("ParticleReleaseSpecification")
                     .set("ParticleReleaseSpecification", "Grid");
                System.out.println("SET release-spec=Grid OK");
            } catch (Throwable e) {
                System.out.println("SET release-spec=Grid FAIL: " + e.getMessage());
            }
            try {
                System.out.println("rel1.InitialPosition after : " + Arrays.toString(
                    model.component("comp1").physics("fpt").feature("rel1")
                         .getAllowedPropertyValues("InitialPosition")));
            } catch (Throwable e) {
                System.out.println("rel1.InitialPosition FAIL: " + e.getMessage());
            }

            // ---- E_src options with ec present ----
            model.component("comp1").physics("fpt").create("deff1", "DielectrophoreticForce", 3);
            model.component("comp1").physics("fpt").feature("deff1").selection().all();
            try {
                System.out.println("ALLOWED deff1.E_src : " + Arrays.toString(
                    model.component("comp1").physics("fpt").feature("deff1")
                         .getAllowedPropertyValues("E_src")));
            } catch (Throwable e) {
                System.out.println("ALLOWED deff1.E_src FAIL: " + e.getMessage());
            }

            // ---- does es solve in frequency domain? ----
            model.study().create("std1");
            model.study("std1").create("freq", "Frequency");
            model.study("std1").feature("freq").set("plist", "10[MHz]");
            try {
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
                System.out.println("FREQ SOLVE OK (es+ec present)");
            } catch (Throwable e) {
                System.out.println("FREQ SOLVE FAIL: " + e);
            }

            System.out.println("PROBE4H DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE4H ABORTED: " + e);
        }
    }
}
