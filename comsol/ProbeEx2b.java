import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** ProbeEx2b — the remaining unknowns for Exercise 2:
 *   D: study-step API (Stationary / TimeDependent / Parametric), and the
 *      "values of variables not solved from" coupling property.
 *   E: how a Point probe is given its coordinates in 3D.
 *   F: the feature ID for a SECOND initial-values node in tds.
 */
public class ProbeEx2b {

    static void h(String s) {
        System.out.println();
        System.out.println("################ " + s + " ################");
    }

    public static void main(String[] args) {
        try {
            studies();
            pointProbe();
            secondInit();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println();
        System.out.println("PROBE_EX2B DONE");
    }

    static Model base(String tag) {
        Model model = ModelUtil.create(tag);
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blk_ch", "Block");
        g.feature("blk_ch").set("size", new String[]{"60", "60", "1"});
        g.feature("blk_ch").set("pos", new String[]{"-30", "-30", "0"});
        g.run();
        return model;
    }

    static void studies() {
        h("D: STUDY STEP API");
        Model model = base("ProbeEx2bD");

        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        System.out.println("Stationary step properties: " + Arrays.toString(model.study("std1").feature("stat").properties()));

        String[] tdOps = {"TimeDependent", "Transient"};
        for (String op : tdOps) {
            try {
                model.study("std1").create("td", op);
                System.out.println("time step op '" + op + "' OK; properties = "
                    + Arrays.toString(model.study("std1").feature("td").properties()));
                break;
            } catch (Throwable e) {
                System.out.println("time step op '" + op + "' FAILED: " + e.getMessage());
            }
        }

        try {
            model.study("std1").create("param", "Parametric");
            System.out.println("Parametric step properties: " + Arrays.toString(model.study("std1").feature("param").properties()));
            for (String pr : new String[]{"pname", "plist", "plistarr", "sweeptype", "keepsol", "punit", "control", "uselsqdata"}) {
                try {
                    String[] av = model.study("std1").feature("param").getAllowedPropertyValues(pr);
                    System.out.println("   param." + pr + " allowed = " + Arrays.toString(av));
                } catch (Throwable e) { /* not an enum */ }
            }
        } catch (Throwable e) { System.out.println("Parametric step FAILED: " + e); }

        // enums for the "values of variables not solved for" on the Stationary step
        for (String pr : new String[]{"notsolmethod", "notstudy", "notsolnum", "useinitsol",
                                      "initmethod", "initstudy", "solnum", "usesol"}) {
            try {
                String[] av = model.study("std1").feature("stat").getAllowedPropertyValues(pr);
                System.out.println("   stat." + pr + " allowed = " + Arrays.toString(av));
            } catch (Throwable e) { /* not present */ }
        }
        // same on the time step
        try {
            for (String pr : new String[]{"notsolmethod", "notstudy", "notsolnum", "useinitsol", "initmethod", "initstudy"}) {
                try {
                    String[] av = model.study("std1").feature("td").getAllowedPropertyValues(pr);
                    System.out.println("   td." + pr + " allowed = " + Arrays.toString(av));
                } catch (Throwable e) { /* not present */ }
            }
        } catch (Throwable e) { /* no td */ }

        // What does the Stationary step expose for stepping? try setting the coupling.
        try {
            model.study("std1").feature("stat").set("notsolmethod", "sol");
            model.study("std1").feature("stat").set("notstudy", "std1");
            System.out.println("stat notsolmethod=sol / notstudy=std1 SET OK");
        } catch (Throwable e) {
            System.out.println("stat coupling set FAILED: " + e.getMessage());
        }
    }

    static void pointProbe() {
        h("E: POINT PROBE COORDINATES");
        Model model = base("ProbeEx2bE");
        model.probe().create("pp1", "Point");
        model.probe("pp1").set("expr", "c");
        System.out.println("Point probe properties: " + Arrays.toString(model.probe("pp1").properties()));
        // try the known candidate property names
        String[][] cand = {
            {"coords", "0.0295"},
            {"pointx", "0.0295"},
            {"x", "0.0295"},
        };
        for (String[] c : cand) {
            try {
                model.probe("pp1").set(c[0], c[1]);
                System.out.println("   set " + c[0] + " = " + c[1] + "  -> OK");
                System.out.println("   get " + c[0] + " = " + model.probe("pp1").getString(c[0]));
            } catch (Throwable e) {
                System.out.println("   set " + c[0] + " FAILED: " + e.getMessage());
            }
        }
        // set the number of points and frame
        try { model.probe("pp1").set("points", "1"); System.out.println("   set points=1 OK"); }
        catch (Throwable e) { System.out.println("   set points FAILED: " + e.getMessage()); }
        try { model.probe("pp1").set("frame", "spatial"); System.out.println("   set frame=spatial OK"); }
        catch (Throwable e) { System.out.println("   set frame FAILED: " + e.getMessage()); }

        // DomainPoint probe for reference (coords is a double matrix)
        model.probe().create("pdp1", "DomainPoint");
        model.probe("pdp1").set("coords", new double[][]{{0.0295, 0.0, 0.0}});
        System.out.println("DomainPoint coords set OK");
        System.out.println("DomainPoint get coords = " + Arrays.deepToString(model.probe("pdp1").getDoubleMatrix("coords")));
    }

    static void secondInit() {
        h("F: SECOND INITIAL-VALUES NODE IN tds");
        Model model = base("ProbeEx2bF");
        model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
        String[] cand = {"init", "init2", "InitialValues2", "initValues"};
        for (String op : cand) {
            try {
                model.component("comp1").physics("tds").create("myinit", op, 3);
                System.out.println("tds.create(\"myinit\", \"" + op + "\", 3) OK; type="
                    + model.component("comp1").physics("tds").feature("myinit").getType()
                    + " props=" + Arrays.toString(model.component("comp1").physics("tds").feature("myinit").properties()));
                break;
            } catch (Throwable e) {
                System.out.println("tds.create(\"myinit\", \"" + op + "\", 3) FAILED: " + e.getMessage());
            }
        }
        // also check what the default init1 node looks like
        System.out.println("tds/init1 type = " + model.component("comp1").physics("tds").feature("init1").getType());
    }
}
