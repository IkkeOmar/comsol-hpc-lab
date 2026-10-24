import com.comsol.model.*;
import com.comsol.model.util.*;

/**
 * Probe: what can a COMSOL batch class actually do under the batch
 * SecurityManager?
 *
 * Facts established before writing this (2026-10-24):
 *   - comsolbatch runs the class under a java SecurityManager that denies
 *     java.io.File reads.  The stack trace of the aborted Ex4_Plots run was
 *       at java.io.File.exists(Unknown Source)
 *       at Ex4_Plots.main(Ex4_Plots.class:...)
 *     so the denial was the `new File(mph).exists()` GUARD, not ModelUtil.load.
 *   - so the open questions are:
 *       1) does comsolbatch forward command-line arguments to main()?
 *       2) does ModelUtil.load() accept an ABSOLUTE path?
 *       3) does ModelUtil.load() accept a RELATIVE path?
 *
 * This class answers all three and prints the answer.  It guesses nothing.
 */
public class Ex4_ProbeAccess {

    static void report(String what, boolean ok, String detail) {
        System.out.println((ok ? "PROBE_OK   " : "PROBE_FAIL ") + what + " :: " + detail);
    }

    static boolean tryLoad(String tag, String path) {
        try {
            Model m = ModelUtil.load(tag, path);
            String phys = java.util.Arrays.toString(m.component("comp1").physics().tags());
            report("ModelUtil.load(" + path + ")", true, "loaded, physics=" + phys);
            return true;
        } catch (Throwable t) {
            report("ModelUtil.load(" + path + ")", false, String.valueOf(t.getMessage()));
            return false;
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("PROBE args is null: " + (args == null));
            // 1) absolute path, the form the previous class used
            tryLoad("pAbs", "<project>/"
                + "comsol/out/Ex4_FFF_prod.mph");
            // 2) relative path from the batch working directory (comsol/)
            tryLoad("pRel", "out/Ex4_FFF_prod.mph");
            // 3) is an ordinary java read allowed, i.e. is the manager live?
            try {
                java.nio.file.Files.readString(java.nio.file.Path.of(
                    "<project>/comsol/out/Ex4_FFF_prod.mph"),
                    java.nio.charset.StandardCharsets.ISO_8859_1);
                report("java.nio read of the .mph", true, "ALLOWED");
            } catch (Throwable t) {
                report("java.nio read of the .mph", false, String.valueOf(t.getMessage()));
            }

            System.out.println("=== PROBE DONE ===");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE ABORTED: " + e);
        }
    }
}
