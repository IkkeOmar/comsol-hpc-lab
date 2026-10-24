import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** ProbeEx2 — enumerate the EXACT COMSOL 6.4 API for the Exercise-2 cell growth chamber.
 *
 *  Why: the assignment needs a Rectangle pulse function, a Transport-of-Diluted-Species
 *  interface with a "Fluid" domain condition and a "Reactions" domain condition, domain
 *  and global-point probes, a mass-flow inlet, and a stationary step coupled to a previous
 *  step.  Every one of those names is version specific, so we probe the installed build
 *  (per comsol-java-api rule 8) instead of guessing.
 */
public class ProbeEx2 {

    static void h(String s) {
        System.out.println();
        System.out.println("################ " + s + " ################");
    }

    static void dumpFeature(PhysicsFeature f, String label) {
        try {
            System.out.println("  [" + label + "] type=" + f.getType());
            String[] p = f.properties();
            System.out.println("     properties: " + Arrays.toString(p));
        } catch (Throwable e) {
            System.out.println("  [" + label + "] DUMP FAILED: " + e);
        }
    }

    public static void main(String[] args) {
        try {
            geometry();
            physics();
            probes();
            studies();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println();
        System.out.println("PROBE_EX2 DONE");
    }

    // ------------------------------------------------------------------ geometry
    static void geometry() {
        h("A: GEOMETRY (chamber 60x60x1 mm + channel 150x2x1 mm, both footprint-centred on origin)");
        Model model = ModelUtil.create("ProbeEx2");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blk_ch", "Block");
        g.feature("blk_ch").set("size", new String[]{"60", "60", "1"});
        g.feature("blk_ch").set("pos", new String[]{"-30", "-30", "0"});
        g.create("blk_chn", "Block");
        g.feature("blk_chn").set("size", new String[]{"150", "2", "1"});
        g.feature("blk_chn").set("pos", new String[]{"-75", "-1", "0"});
        g.run();
        System.out.println("domains     = " + g.getNDomains());
        System.out.println("boundaries  = " + g.getNBoundaries());
        System.out.println("edges       = " + g.getNEdges());

        // inlet face: x = -75, |y| <= 1, 0 <= z <= 1
        g.create("selIn", "BoxSelection");
        g.feature("selIn").set("entitydim", 2);
        g.feature("selIn").set("condition", "allvertices");
        g.feature("selIn").set("xmin", "-75.1"); g.feature("selIn").set("xmax", "-74.9");
        g.feature("selIn").set("ymin", "-1.1");  g.feature("selIn").set("ymax", "1.1");
        g.feature("selIn").set("zmin", "-0.1");  g.feature("selIn").set("zmax", "1.1");
        // outlet face: x = +75
        g.create("selOut", "BoxSelection");
        g.feature("selOut").set("entitydim", 2);
        g.feature("selOut").set("condition", "allvertices");
        g.feature("selOut").set("xmin", "74.9"); g.feature("selOut").set("xmax", "75.1");
        g.feature("selOut").set("ymin", "-1.1");  g.feature("selOut").set("ymax", "1.1");
        g.feature("selOut").set("zmin", "-0.1");  g.feature("selOut").set("zmax", "1.1");
        // chamber region: everything with |x|<=30 and |y|<=30  (domain selection)
        g.create("selCham", "BoxSelection");
        g.feature("selCham").set("entitydim", 3);
        g.feature("selCham").set("condition", "inside");
        g.feature("selCham").set("xmin", "-30.1"); g.feature("selCham").set("xmax", "30.1");
        g.feature("selCham").set("ymin", "-30.1"); g.feature("selCham").set("ymax", "30.1");
        g.feature("selCham").set("zmin", "-0.1");  g.feature("selCham").set("zmax", "1.1");
        g.run();
        System.out.println("inlet faces    = " + Arrays.toString(model.selection("geom1_selIn").entities(2)));
        System.out.println("outlet faces   = " + Arrays.toString(model.selection("geom1_selOut").entities(2)));
        System.out.println("chamber domains= " + Arrays.toString(model.selection("geom1_selCham").entities(3)));
        System.out.println("all domains    = " + Arrays.toString(model.selection("geom1_selCham").entities(3)));
    }

    // ------------------------------------------------------------------ physics
    static void physics() {
        h("B: PHYSICS API");
        Model model = ModelUtil.create("ProbeEx2B");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blk_ch", "Block");
        g.feature("blk_ch").set("size", new String[]{"60", "60", "1"});
        g.feature("blk_ch").set("pos", new String[]{"-30", "-30", "0"});
        g.run();

        // ---- Laminar Flow
        model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
        System.out.println("spf default features: " + Arrays.toString(model.component("comp1").physics("spf").feature().tags()));
        for (String t : model.component("comp1").physics("spf").feature().tags()) {
            dumpFeature(model.component("comp1").physics("spf").feature(t), "spf/" + t);
        }
        model.component("comp1").physics("spf").create("inl1", "Inlet", 2);
        dumpFeature(model.component("comp1").physics("spf").feature("inl1"), "spf/inl1");
        try {
            System.out.println("  Inlet.BoundaryCondition allowed = "
                + Arrays.toString(model.component("comp1").physics("spf").feature("inl1")
                        .getAllowedPropertyValues("BoundaryCondition")));
        } catch (Throwable e) { System.out.println("  BoundaryCondition enum probe failed: " + e); }
        try {
            model.component("comp1").physics("spf").feature("inl1").set("BoundaryCondition", "MassFlow");
            System.out.println("  set MassFlow OK");
            dumpFeature(model.component("comp1").physics("spf").feature("inl1"), "spf/inl1 MassFlow");
        } catch (Throwable e) { System.out.println("  set MassFlow FAILED: " + e); }
        model.component("comp1").physics("spf").create("out1", "Outlet", 2);
        dumpFeature(model.component("comp1").physics("spf").feature("out1"), "spf/out1");

        // ---- Transport of Diluted Species
        try {
            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            System.out.println("TDS created with op DilutedSpecies");
        } catch (Throwable e) {
            System.out.println("DilutedSpecies FAILED: " + e);
            model.component("comp1").physics().create("tds", "TransportDilutedSpecies", "geom1");
            System.out.println("TDS created with op TransportDilutedSpecies");
        }
        System.out.println("tds default features: " + Arrays.toString(model.component("comp1").physics("tds").feature().tags()));
        for (String t : model.component("comp1").physics("tds").feature().tags()) {
            dumpFeature(model.component("comp1").physics("tds").feature(t), "tds/" + t);
        }
        // velocity source enum on the convection/diffusion node
        for (String t : model.component("comp1").physics("tds").feature().tags()) {
            try {
                String[] av = model.component("comp1").physics("tds").feature(t).getAllowedPropertyValues("u_src");
                System.out.println("  tds/" + t + ".u_src allowed = " + Arrays.toString(av));
            } catch (Throwable e) { /* not present on this node */ }
        }
        // "Fluid" domain condition (cell domain: zero velocity, 0.7*D)
        try {
            model.component("comp1").physics("tds").create("fluid2", "Fluid", 3);
            dumpFeature(model.component("comp1").physics("tds").feature("fluid2"), "tds/fluid2");
            try {
                System.out.println("  fluid2.u_src allowed = "
                    + Arrays.toString(model.component("comp1").physics("tds").feature("fluid2").getAllowedPropertyValues("u_src")));
            } catch (Throwable e) { System.out.println("  fluid2.u_src enum probe failed: " + e); }
        } catch (Throwable e) { System.out.println("tds Fluid domain condition FAILED: " + e); }
        // "Reactions" domain condition
        try {
            model.component("comp1").physics("tds").create("reac1", "Reactions", 3);
            dumpFeature(model.component("comp1").physics("tds").feature("reac1"), "tds/reac1");
        } catch (Throwable e) { System.out.println("tds Reactions domain condition FAILED: " + e); }
        // Inflow / Outflow species BCs
        try {
            model.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
            dumpFeature(model.component("comp1").physics("tds").feature("inflow1"), "tds/inflow1");
        } catch (Throwable e) { System.out.println("tds Inflow FAILED: " + e); }
        try {
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
            dumpFeature(model.component("comp1").physics("tds").feature("outflow1"), "tds/outflow1");
        } catch (Throwable e) { System.out.println("tds Outflow FAILED: " + e); }
        // InitialValues domain condition (second one for the channel fill)
        try {
            model.component("comp1").physics("tds").create("init2", "InitialValues", 3);
            dumpFeature(model.component("comp1").physics("tds").feature("init2"), "tds/init2");
        } catch (Throwable e) { System.out.println("tds InitialValues FAILED: " + e); }
    }

    // ------------------------------------------------------------------ probes
    static void probes() {
        h("C: PROBE API");
        Model model = ModelUtil.create("ProbeEx2C");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blk_ch", "Block");
        g.feature("blk_ch").set("size", new String[]{"60", "60", "1"});
        g.feature("blk_ch").set("pos", new String[]{"-30", "-30", "0"});
        g.run();
        String[] types = {"Domain", "Point", "Global", "Boundary", "DomainPoint", "GlobalPointProbe"};
        for (String ty : types) {
            String tag = "p" + ty;
            try {
                model.probe().create(tag, ty);
                System.out.println("  probe type '" + ty + "' OK; properties = "
                    + Arrays.toString(model.probe(tag).properties()));
            } catch (Throwable e) {
                System.out.println("  probe type '" + ty + "' FAILED: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ studies
    static void studies() {
        h("D: STUDY STEP API");
        Model model = ModelUtil.create("ProbeEx2D");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blk_ch", "Block");
        g.feature("blk_ch").set("size", new String[]{"60", "60", "1"});
        g.feature("blk_ch").set("pos", new String[]{"-30", "-30", "0"});
        g.run();

        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        System.out.println("Stationary step properties: " + Arrays.toString(model.study("std1").feature("stat").properties()));
        String[] tdOps = {"TimeDependent", "Transient"};
        boolean made = false;
        for (String op : tdOps) {
            try {
                model.study("std1").create("td", op);
                System.out.println("time-dependent step op '" + op + "' OK; properties = "
                    + Arrays.toString(model.study("std1").feature("td").properties()));
                made = true;
                break;
            } catch (Throwable e) {
                System.out.println("time step op '" + op + "' FAILED: " + e.getMessage());
            }
        }
        if (!made) System.out.println("NO TIME STEP CREATED");

        // Parametric sweep node
        try {
            model.study("std1").create("param", "Parametric");
            System.out.println("Parametric step properties: " + Arrays.toString(model.study("std1").feature("param").properties()));
            for (String pr : new String[]{"pname", "plist", "plistarr", "sweeptype", "keepsol", "punit"}) {
                try {
                    System.out.println("   param." + pr + " allowed = "
                        + Arrays.toString(model.study("std1").feature("param").getAllowedPropertyValues(pr)));
                } catch (Throwable e) { /* not an enum */ }
            }
        } catch (Throwable e) { System.out.println("Parametric step FAILED: " + e); }

        // "values of variables not solved for" — probe the Stationary step's enums
        for (String pr : new String[]{"notsolmethod", "notstudy", "notsolnum", "useinitsol", "initmethod", "initstudy", "solnum"}) {
            try {
                System.out.println("   stat." + pr + " allowed = "
                    + Arrays.toString(model.study("std1").feature("stat").getAllowedPropertyValues(pr)));
            } catch (Throwable e) { /* not present */ }
        }
        // Rectangle function
        try {
            model.func().create("rect1", "Rectangle");
            System.out.println("Rectangle function properties: " + Arrays.toString(model.func("rect1").properties()));
            model.func("rect1").set("lower", "-5");
            model.func("rect1").set("upper", "2160");
            model.func("rect1").set("smoothzone", "5");
            model.func("rect1").set("ncontder", "2");
            model.func("rect1").set("smooth", true);
            System.out.println("Rectangle set OK: lower=" + model.func("rect1").getString("lower")
                + " upper=" + model.func("rect1").getString("upper")
                + " smoothzone=" + model.func("rect1").getString("smoothzone")
                + " ncontder=" + model.func("rect1").getString("ncontder"));
        } catch (Throwable e) { System.out.println("Rectangle function FAILED: " + e); }
    }
}
