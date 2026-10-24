import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;

/**
 * ProbePgProps: enumerate the REAL properties of a 2D/3D plot group, its
 *  "Mesh" plot feature and the component view on this COMSOL build, so the
 *  geometry export can be written against measured names instead of guesses.
 *
 *  Why: "preserveaspect" is documented (Programming Reference p. 1003) but is
 *  NOT accepted on a 2D plot group on this build - it threw
 *  "Unknown property" and killed the Ex1/Ex3 image exports.  Every other
 *  property name used below is checked the same way before it is used.
 */
public class ProbePgProps {

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("ProbePg");
            model.component().create("comp1");
            GeomSequence g = model.component("comp1").geom().create("geom1", 2);
            g.lengthUnit("um");
            g.create("r1", "Rectangle");
            g.feature("r1").set("size", new String[]{"6000[um]", "100[um]"});
            g.feature("r1").set("pos", new String[]{"0", "-50[um]"});
            g.run();
            model.component("comp1").mesh().create("mesh1", "geom1");
            model.component("comp1").mesh("mesh1").autoMeshSize(6);
            model.component("comp1").mesh("mesh1").run();
            model.result().dataset().create("dsmesh", "Mesh");
            model.result().dataset("dsmesh").set("mesh", "mesh1");

            model.result().create("pg", "PlotGroup2D");
            model.result("pg").set("data", "dsmesh");
            model.result("pg").create("msh1", "Mesh");

            System.out.println("=== PlotGroup2D properties ===");
            String[] pprops = model.result("pg").properties();
            for (int i = 0; i < pprops.length; i++) {
                System.out.println("  PG " + pprops[i]);
            }

            System.out.println("=== Mesh plot feature properties ===");
            String[] fprops = model.result("pg").feature("msh1").properties();
            for (int i = 0; i < fprops.length; i++) {
                System.out.println("  FEAT " + fprops[i]);
            }

            System.out.println("=== candidate settings, each in its own try ===");
            String[] tries = {"xlabelactive", "ylabelactive", "xlabel", "ylabel",
                              "titletype", "title", "showlegendsmaxmin",
                              "showlegendstitle", "showlegendsunit", "edges",
                              "view", "axis"};
            for (int i = 0; i < tries.length; i++) {
                String p = tries[i];
                try {
                    if (p.equals("xlabel") || p.equals("ylabel")) {
                        model.result("pg").set(p, "x (um)");
                    } else if (p.equals("title")) {
                        model.result("pg").set(p, "probe");
                    } else if (p.equals("view")) {
                        model.result("pg").set(p, "view1");
                    } else if (p.equals("axis")) {
                        model.result("pg").set(p, "on");
                    } else {
                        model.result("pg").set(p, "on");
                    }
                    System.out.println("  ACCEPTED: " + p);
                } catch (Throwable e) {
                    System.out.println("  REJECTED: " + p + " -> " + e.getMessage());
                }
            }

            System.out.println("=== view / axis properties ===");
            try {
                String[] vprops = model.component("comp1").view("view1").properties();
                for (int i = 0; i < vprops.length; i++) {
                    System.out.println("  VIEW " + vprops[i]);
                }
            } catch (Throwable e) {
                System.out.println("  VIEW FAILED: " + e);
            }
            try {
                String[] aprops = model.component("comp1").view("view1").axis().properties();
                for (int i = 0; i < aprops.length; i++) {
                    System.out.println("  AXIS " + aprops[i]);
                }
            } catch (Throwable e) {
                System.out.println("  AXIS FAILED: " + e);
            }

            System.out.println("PROBEPG DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBEPG ABORTED: " + e);
        }
    }
}
