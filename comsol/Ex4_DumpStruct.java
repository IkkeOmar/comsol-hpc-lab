import com.comsol.model.*;
import com.comsol.model.util.*;

/**
 * Dump the plot/dataset structure that COMSOL itself built in the loaded
 * Ex4 production model, so a new figure can be built by CLONING a structure
 * COMSOL already accepts instead of inventing one.
 */
public class Ex4_DumpStruct {
    public static void main(String[] args) {
        try {
            String root = "<project>/";
            Model model = ModelUtil.load("Ex4", root + "comsol/out/Ex4_FFF_prod.mph");

            System.out.println("DATASETS:");
            for (String d : model.result().dataset().tags()) {
                String type = "?";
                try { type = model.result().dataset(d).getType(); } catch (Throwable t) {}
                System.out.println("  " + d + "  type=" + type);
            }
            System.out.println("PLOT GROUPS:");
            for (String pg : model.result().tags()) {
                String type = "?";
                try { type = model.result(pg).getType(); } catch (Throwable t) {}
                System.out.println("  " + pg + "  type=" + type);
                try {
                    String data = "?";
                    try { data = model.result(pg).getString("data"); } catch (Throwable t) {}
                    System.out.println("      data=" + data);
                } catch (Throwable t) {}
                try {
                    for (String f : model.result(pg).feature().tags()) {
                        String ft = "?";
                        try { ft = model.result(pg).feature(f).getType(); } catch (Throwable t) {}
                        String expr = "";
                        try { expr = model.result(pg).feature(f).getString("expr"); } catch (Throwable t) {}
                        System.out.println("      feature " + f + "  type=" + ft + "  expr=" + expr);
                    }
                } catch (Throwable t) {
                    System.out.println("      (features unavailable: " + t.getMessage() + ")");
                }
            }
            System.out.println("EXPORTS:");
            for (String e : model.result().export().tags()) {
                String type = "?";
                try { type = model.result().export(e).getType(); } catch (Throwable t) {}
                System.out.println("  " + e + "  type=" + type);
            }
            System.out.println("=== DUMP DONE ===");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("DUMP ABORTED: " + e);
        }
    }
}
