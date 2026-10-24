/* Ex2ArgProbe.java -- determine how comsolbatch passes arguments to a
 * Java main method in COMSOL 6.4.
 *
 * The COMSOL batch security manager denies file reads and environment
 * variables inside the model class, so the configuration has to arrive as
 * method arguments.  Candidate syntaxes are printed by the caller and the
 * first non-empty one wins.
 */
import com.comsol.model.*;
import com.comsol.model.util.*;

public class Ex2ArgProbe {

    public static void main(String[] args) {
        int n = (args == null) ? -1 : args.length;
        System.out.println("ARGPROBE argc=" + n);
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                System.out.println("ARGPROBE arg[" + i + "]=[" + args[i] + "]");
            }
        }
        // also try reading a system property, which is NOT an env var and may
        // survive the sandbox
        try {
            System.out.println("ARGPROBE prop=" + System.getProperty("ex2.mode", "<none>"));
        } catch (Throwable t) {
            System.out.println("ARGPROBE prop FAILED: " + t.getMessage());
        }
        try {
            System.out.println("ARGPROBE env=" + System.getenv("EX2_MODE"));
        } catch (Throwable t) {
            System.out.println("ARGPROBE env FAILED: " + t.getMessage());
        }
        System.out.println("ARGPROBE DONE");
    }
}
