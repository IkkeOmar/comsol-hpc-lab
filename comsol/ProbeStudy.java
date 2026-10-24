import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** ProbeStudy: MEASURE why the TDS physics was left out of the Ex1 solve.
 *
 * MEASURED FACTS SO FAR:
 *   - the Ex1 run solved 344,252 DOF
 *   - the FLOW is right: inlet pressure 9696 Pa (analytical 9600 Pa) and mean
 *     inlet velocity 0.003284 m/s (target 0.003333 m/s)
 *   - the CONCENTRATION is 0.0 everywhere
 *   - the string "tds" appears exactly once in the whole log, in my own
 *     print statement -> the TDS interface was never in the solved system
 *
 * So: what does the study step actually contain, and what does the generated
 * solver sequence contain?  This prints the ACTUAL property lists and values
 * instead of guessing which property name activates a physics interface.
 */
public class ProbeStudy {

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("ProbeStudy");
            model.component().create("comp1");
            GeomSequence g = model.component("comp1").geom().create("geom1", 2);
            g.lengthUnit("um");
            g.create("r1", "Rectangle");
            g.feature("r1").set("size", new String[]{"600[um]", "100[um]"});
            g.feature("r1").set("pos",  new String[]{"0", "-50[um]"});
            g.run();

            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");

            System.out.println("=== physics interfaces present ===");
            System.out.println(Arrays.toString(model.component("comp1").physics().tags()));

            // ---- study A: stationary + separate Parametric step ----------
            System.out.println("\n=== STUDY A: stat + Parametric step ===");
            model.study().create("stdA");
            model.study("stdA").create("stat", "Stationary");
            model.study("stdA").create("param", "Parametric");
            model.study("stdA").feature("param").set("pname", new String[]{"D_sweep"});
            model.study("stdA").feature("param").set("plist", "1e-10");
            model.study("stdA").feature("param").set("sweeptype", "sparse");
            model.study("stdA").createAutoSequences("all");

            System.out.println("stdA step tags: "
                + Arrays.toString(model.study("stdA").feature().tags()));
            for (String ft : model.study("stdA").feature().tags()) {
                System.out.println("  step '" + ft + "' props: "
                    + Arrays.toString(model.study("stdA").feature(ft).properties()));
            }
            for (String ft : model.study("stdA").feature().tags()) {
                // print anything that looks like a physics / variable selection
                for (String p : model.study("stdA").feature(ft).properties()) {
                    try {
                        String v = model.study("stdA").feature(ft).getString(p);
                        if (v != null && !v.isEmpty()
                                && (p.toLowerCase().contains("phys")
                                    || p.toLowerCase().contains("activ")
                                    || p.toLowerCase().contains("var")
                                    || v.toLowerCase().contains("spf")
                                    || v.toLowerCase().contains("tds"))) {
                            System.out.println("  stdA." + ft + "." + p + " = " + v);
                        }
                    } catch (Throwable e) { }
                }
            }

            // ---- study B: stationary only, then the sweep as a batch job ---
            System.out.println("\n=== STUDY B: stat only ===");
            model.study().create("stdB");
            model.study("stdB").create("stat", "Stationary");
            model.study("stdB").createAutoSequences("all");
            for (String ft : model.study("stdB").feature().tags()) {
                System.out.println("  step '" + ft + "' props: "
                    + Arrays.toString(model.study("stdB").feature(ft).properties()));
                for (String p : model.study("stdB").feature(ft).properties()) {
                    try {
                        String v = model.study("stdB").feature(ft).getString(p);
                        if (v != null && !v.isEmpty()
                                && (p.toLowerCase().contains("phys")
                                    || p.toLowerCase().contains("activ")
                                    || v.toLowerCase().contains("spf")
                                    || v.toLowerCase().contains("tds"))) {
                            System.out.println("  stdB." + ft + "." + p + " = " + v);
                        }
                    } catch (Throwable e) { }
                }
            }

            // ---- what does the SOLVER sequence contain? --------------------
            System.out.println("\n=== solver sequences ===");
            try {
                System.out.println("stdA sequences: "
                    + Arrays.toString(model.study("stdA").getSolverSequences("all")));
                for (String sq : model.study("stdA").getSolverSequences("all")) {
                    System.out.println("  seq " + sq + " features: "
                        + Arrays.toString(model.sol(sq).feature().tags()));
                }
            } catch (Throwable e) {
                System.out.println("  solver sequence listing failed: " + e.getMessage());
            }

            // ---- the physics activation, seen from the solver side ---------
            System.out.println("\n=== solver 'Stationary' step / v1 physics selection ===");
            try {
                String[] sqs = model.study("stdA").getSolverSequences("all");
                if (sqs.length > 0) {
                    String sq = sqs[0];
                    for (String ft : model.sol(sq).feature().tags()) {
                        System.out.println("  sol(" + sq + ")." + ft + " props: "
                            + Arrays.toString(model.sol(sq).feature(ft).properties()));
                    }
                }
            } catch (Throwable e) {
                System.out.println("  failed: " + e.getMessage());
            }

            System.out.println("\nPROBESTUDY DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBESTUDY ABORTED: " + e);
        }
    }
}
