// ProbeEx2e.java - second API probe for Exercise 2 (COMSOL 6.4).
//
// Everything below is RUNTIME-ENUMERATED (properties() / getAllowedPropertyValues),
// never guessed.  Questions:
//   A) Mesh: "Sweep" (the real op string - "Swept" does not exist) with a
//      Distribution child, source/destination face selection, element types.
//   B) Probe API: "Domain" probe method enum, "Point" probe coordinate API,
//      table output.
//   C) Study step: does "disabledphysics" accept "tds/inflow1" once the
//      interface really exists?
//   D) Physics property VALUES: tds Reactions R_c_src / ReactingVolumeType,
//      tds Inflow BoundaryConditionType, spf Inlet MassFlowType,
//      spf gravity switch-off, spf Material density/viscosity for a material.
//   E) CutLine3D dataset: real property names for the two end points.
//   F) Parametric sweep + "values of variables not solved for" enums.

import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class ProbeEx2e {

    static void P(String s) { System.out.println("PROBE " + s); }

    static void tryIt(String label, Runnable r) {
        try { r.run(); P("OK   " + label); }
        catch (Throwable t) { P("FAIL " + label + " :: " + t.getMessage()); }
    }

    static void dumpProps(Model model, String phys, String tag, String[] props) {
        try {
            com.comsol.model.physics.PhysicsFeature f =
                model.component("comp1").physics(phys).feature(tag);
            P(phys + "/" + tag + " props = " + Arrays.toString(f.properties()));
            for (String pr : props) {
                try {
                    String[] av = f.getAllowedPropertyValues(pr);
                    if (av != null) P("   " + phys + "/" + tag + "." + pr + " allowed = " + Arrays.toString(av));
                } catch (Throwable t) { /* not an enum */ }
            }
        } catch (Throwable t) { P("dumpProps failed for " + phys + "/" + tag); }
    }

    public static void main(String[] args) {
        try { run(); } catch (Throwable t) { t.printStackTrace(); }
        P("PROBE_EX2E DONE");
    }

    public static void run() {
        Model model = ModelUtil.create("ProbeEx2e");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blk_ch", "Block");
        g.feature("blk_ch").set("size", new String[]{"20", "20", "2"});
        g.feature("blk_ch").set("pos",  new String[]{"-10", "-10", "0"});
        g.create("blk_chn", "Block");
        g.feature("blk_chn").set("size", new String[]{"50", "2", "2"});
        g.feature("blk_chn").set("pos",  new String[]{"-25", "-1", "0"});

        // bottom faces (z = 0) of every domain, by vertex position
        g.create("selBot", "BoxSelection");
        g.feature("selBot").set("entitydim", 2);
        g.feature("selBot").set("condition", "allvertices");
        g.feature("selBot").set("xmin", "-30"); g.feature("selBot").set("xmax", "30");
        g.feature("selBot").set("ymin", "-30"); g.feature("selBot").set("ymax", "30");
        g.feature("selBot").set("zmin", "-1");  g.feature("selBot").set("zmax", "0");
        g.run();
        int[] bot = model.selection("geom1_selBot").entities(2);
        P("bottom faces = " + Arrays.toString(bot));
        P("nDomains = " + g.getNDomains());

        // ---------------------------------------------------------------- A
        P("=== A: SWEEP MESH ===");
        MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");
        tryIt("create Sweep", new Runnable() { public void run() {
            mesh.create("swe1", "Sweep");
            System.out.println("PROBE swe1 props = " + Arrays.toString(mesh.feature("swe1").properties()));
        }});
        tryIt("swe1 element=prism", new Runnable() { public void run() {
            mesh.feature("swe1").set("element", "prism");
        }});
        tryIt("swe1 domain selection", new Runnable() { public void run() {
            mesh.feature("swe1").selection().geom("geom1", 3);
            mesh.feature("swe1").selection().all();
        }});
        // source / destination face selection attempts
        for (String[] cand : new String[][]{{"swe1_src", "srcface"}, {"swe1_dst", "dstface"},
                                           {"src", "dst"}, {"swe1_source", "swe1_destination"}}) {
            tryIt("swe1 sel geom " + cand[0], new Runnable() { public void run() {
                mesh.feature("swe1").selection().geom(cand[0]);
                mesh.feature("swe1").selection().set(bot);
            }});
        }
        for (String pr : new String[]{"srcface", "dstface", "srcfaceauto", "dstfaceauto",
                                     "smoothing", "smoothmaxiter", "elemtype", "element",
                                     "sourcefaces", "destinationfaces"}) {
            try {
                System.out.println("PROBE swe1 allowed " + pr + " = "
                    + Arrays.toString(mesh.feature("swe1").getAllowedPropertyValues(pr)));
            } catch (Throwable t) { P("swe1." + pr + " : " + t.getMessage()); }
        }
        tryIt("swe1 Distribution predefined", new Runnable() { public void run() {
            mesh.feature("swe1").create("dis1", "Distribution");
            System.out.println("PROBE dis1 props = " + Arrays.toString(mesh.feature("swe1").feature("dis1").properties()));
            mesh.feature("swe1").feature("dis1").set("type", "predefined");
            mesh.feature("swe1").feature("dis1").set("elemcount", "5");
        }});
        tryIt("mesh.run()", new Runnable() { public void run() {
            mesh.run();
            P("mesh elements = " + mesh.stat().getNumElem());
            for (String ty : new String[]{"prism", "hex", "tet", "tri", "edg"}) {
                try { P("   " + ty + " = " + mesh.stat().getNumElem(ty)); }
                catch (Throwable t) { P("   " + ty + " : " + t.getMessage()); }
            }
        }});

        // ---------------------------------------------------------------- D
        P("=== D: PHYSICS PROPERTY VALUES ===");
        model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
        model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
        model.component("comp1").physics("spf").create("inl1", "Inlet", 2);
        model.component("comp1").physics("spf").create("out1", "Outlet", 2);
        model.component("comp1").physics("tds").create("reac1", "Reactions", 3);
        model.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
        model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);

        dumpProps(model, "spf", "grav1", new String[]{"projectGravity"});
        dumpProps(model, "spf", "inl1", new String[]{"BoundaryCondition", "MassFlowType", "StandardFlowRateDefinedBy"});
        dumpProps(model, "spf", "out1", new String[]{"BoundaryCondition"});
        dumpProps(model, "tds", "reac1", new String[]{"R_c_src", "ReactingVolumeType", "Chemistry"});
        dumpProps(model, "tds", "inflow1", new String[]{"BoundaryConditionType"});
        dumpProps(model, "tds", "cdm1", new String[]{"u_src", "DiffusionCoefficientSource"});

        tryIt("spf grav1 projectGravity=false", new Runnable() { public void run() {
            model.component("comp1").physics("spf").feature("grav1").set("projectGravity", false);
        }});
        tryIt("spf inl1 MassFlow", new Runnable() { public void run() {
            model.component("comp1").physics("spf").feature("inl1").set("BoundaryCondition", "MassFlow");
            model.component("comp1").physics("spf").feature("inl1").set("mfr", "1.6667e-6[kg/s]");
        }});
        tryIt("tds reac1 R_c", new Runnable() { public void run() {
            model.component("comp1").physics("tds").feature("reac1").set("R_c", "-k_react*c");
        }});
        tryIt("tds inflow1 c0", new Runnable() { public void run() {
            model.component("comp1").physics("tds").feature("inflow1").set("c0", "c_in");
        }});

        // material with density / viscosity / diffusion
        tryIt("material", new Runnable() { public void run() {
            model.component("comp1").material().create("mat1", "Common");
            model.component("comp1").material("mat1").propertyGroup("def")
                 .set("density", "1000[kg/m^3]")
                 .set("dynamicviscosity", "1e-3[Pa*s]")
                 .set("diffusioncoefficient", new String[]{"D_sp"});
        }});

        // ---------------------------------------------------------------- C
        P("=== C: STUDY DISABLING ===");
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").create("td", "Transient");
        for (String v : new String[]{"tds/inflow1", "inflow1", "tds", "spf/inl1"}) {
            tryIt("disabledphysics=" + v, new Runnable() { public void run() {
                model.study("std1").feature("td").set("useadvanceddisable", true);
                model.study("std1").feature("td").set("disabledphysics", new String[]{v});
                P("   -> " + Arrays.toString(model.study("std1").feature("td").getStringArray("disabledphysics")));
            }});
        }
        System.out.println("PROBE td props = " + Arrays.toString(model.study("std1").feature("td").properties()));
        System.out.println("PROBE stat props = " + Arrays.toString(model.study("std1").feature("stat").properties()));
        for (String pr : new String[]{"notsolmethod", "notstudy", "notsolnum", "useinitsol",
                                     "initmethod", "initstudy", "solnum", "tlist"}) {
            for (String st : new String[]{"stat", "td"}) {
                try {
                    String[] av = model.study("std1").feature(st).getAllowedPropertyValues(pr);
                    if (av != null) P("   " + st + "." + pr + " allowed = " + Arrays.toString(av));
                } catch (Throwable t) { /* not present */ }
            }
        }

        // ---------------------------------------------------------------- B
        P("=== B: PROBES ===");
        tryIt("Domain probe", new Runnable() { public void run() {
            model.probe().create("pdom", "Domain");
            model.probe("pdom").set("edim", 3);
            model.probe("pdom").set("expr", "c");
            for (String pr : new String[]{"method", "intorder", "intvolume", "intsurface"}) {
                try {
                    String[] av = model.probe("pdom").getAllowedPropertyValues(pr);
                    if (av != null) P("   pdom." + pr + " allowed = " + Arrays.toString(av));
                } catch (Throwable t) { P("   pdom." + pr + " : " + t.getMessage()); }
            }
            try {
                model.probe("pdom").selection().geom("geom1", 3);
                model.probe("pdom").selection().all();
                P("   pdom domain selection OK");
            } catch (Throwable t) { P("   pdom selection FAIL :: " + t.getMessage()); }
            try {
                model.probe("pdom").set("table", "tbl1");
                P("   pdom table set OK");
            } catch (Throwable t) { P("   pdom table FAIL :: " + t.getMessage()); }
        }});
        tryIt("Point probe", new Runnable() { public void run() {
            model.probe().create("ppoint", "Point");
            System.out.println("PROBE Point props = " + Arrays.toString(model.probe("ppoint").properties()));
            model.probe("ppoint").set("edim", 3);
            model.probe("ppoint").set("expr", "c");
            for (String cand : new String[]{"coords", "coord", "points", "p1", "point"}) {
                try {
                    model.probe("ppoint").set(cand, new double[][]{{1.0, 0.0, 0.5}});
                    P("   Point." + cand + " accepted -> "
                        + Arrays.deepToString(model.probe("ppoint").getDoubleMatrix(cand)));
                } catch (Throwable t) { P("   Point." + cand + " FAIL :: " + t.getMessage()); }
            }
        }});

        // ---------------------------------------------------------------- E
        P("=== E: CUTLINE3D ===");
        tryIt("CutLine3D", new Runnable() { public void run() {
            model.result().dataset().create("cln1", "CutLine3D");
            for (String cand : new String[]{"point1", "point2", "p1", "p2", "genpoints",
                                            "genparadist", "method"}) {
                try {
                    if (cand.equals("method")) {
                        model.result().dataset("cln1").set("method", "line");
                    } else if (cand.startsWith("gen")) {
                        model.result().dataset("cln1").set(cand, true);
                    } else {
                        model.result().dataset("cln1").set(cand, new String[]{"0", "0", "0"});
                    }
                    P("   cln1." + cand + " accepted");
                } catch (Throwable t) { P("   cln1." + cand + " FAIL :: " + t.getMessage()); }
            }
        }});

        // ---------------------------------------------------------------- F
        P("=== F: PARAMETRIC ===");
        tryIt("Parametric", new Runnable() { public void run() {
            model.study("std1").create("param", "Parametric");
            for (String pr : new String[]{"pname", "plist", "plistarr", "sweeptype", "keepsol", "punit"}) {
                try {
                    String[] av = model.study("std1").feature("param").getAllowedPropertyValues(pr);
                    if (av != null) P("   param." + pr + " allowed = " + Arrays.toString(av));
                } catch (Throwable t) { /* not enum */ }
            }
            model.study("std1").feature("param").set("pname", new String[]{"Q_vol"});
            model.study("std1").feature("param").set("plistarr", new String[]{"10 100 1000"});
            model.study("std1").feature("param").set("punit", new String[]{"uL/min"});
            P("   param set OK");
        }});
    }
}
