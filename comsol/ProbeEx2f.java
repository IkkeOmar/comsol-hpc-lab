// ProbeEx2f.java - second API probe for Exercise 2 (COMSOL 6.4).
//
// NOTE: no anonymous inner classes / lambdas anywhere in this file.  The
// comsolcompile.exe step emits ONLY the top-level .class file, so the
// COMSOL batch class loader cannot resolve "ProbeEx2e$1" at run time.
//
// Everything below is RUNTIME-ENUMERATED (properties() / getAllowedPropertyValues),
// never guessed.  Questions:
//   A) Mesh: "Sweep" (the real op string - "Swept" does not exist) with a
//      Distribution child, source/destination face selection, element types.
//   B) Probe API: "Domain" probe method enum, "Point" probe coordinate API.
//   C) Study step: does "disabledphysics" accept "tds/inflow1" once the
//      interface really exists?
//   D) Physics property VALUES: tds Reactions R_c_src / ReactingVolumeType,
//      tds Inflow BoundaryConditionType, spf Inlet MassFlowType, gravity switch.
//   E) CutLine3D dataset: real property names for the two end points.
//   F) Parametric sweep + "values of variables not solved for" enums.

import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class ProbeEx2f {

    static void P(String s) { System.out.println("PROBE " + s); }

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
        P("PROBE_EX2F DONE");
    }

    public static void run() throws Exception {
        Model model = ModelUtil.create("ProbeEx2f");
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
        try {
            mesh.create("swe1", "Sweep");
            P("Sweep created");
            P("swe1 props = " + Arrays.toString(mesh.feature("swe1").properties()));
        } catch (Throwable t) { P("create Sweep FAILED :: " + t.getMessage()); }

        try { mesh.feature("swe1").set("element", "prism"); P("element=prism OK"); }
        catch (Throwable t) { P("element=prism FAILED :: " + t.getMessage()); }

        try {
            mesh.feature("swe1").selection().geom("geom1", 3);
            mesh.feature("swe1").selection().all();
            P("swe1 domain selection OK");
        } catch (Throwable t) { P("swe1 domain selection FAILED :: " + t.getMessage()); }

        String[] geoCands = {"swe1_src", "swe1_dst", "src", "dst", "swe1_source", "swe1_destination"};
        for (int i = 0; i < geoCands.length; i++) {
            String cand = geoCands[i];
            try {
                mesh.feature("swe1").selection().geom(cand);
                mesh.feature("swe1").selection().set(bot);
                P("swe1 sel geom " + cand + " OK");
            } catch (Throwable t) { P("swe1 sel geom " + cand + " FAIL :: " + t.getMessage()); }
        }

        String[] sweepProps = {"srcface", "dstface", "srcfaceauto", "dstfaceauto",
                               "smoothing", "smoothmaxiter", "elemtype", "element",
                               "sourcefaces", "destinationfaces"};
        for (int i = 0; i < sweepProps.length; i++) {
            String pr = sweepProps[i];
            try {
                String[] av = mesh.feature("swe1").getAllowedPropertyValues(pr);
                P("swe1 allowed " + pr + " = " + Arrays.toString(av));
            } catch (Throwable t) { P("swe1." + pr + " : " + t.getMessage()); }
        }

        try {
            mesh.feature("swe1").create("dis1", "Distribution");
            P("dis1 props = " + Arrays.toString(mesh.feature("swe1").feature("dis1").properties()));
            mesh.feature("swe1").feature("dis1").set("type", "predefined");
            mesh.feature("swe1").feature("dis1").set("elemcount", "5");
            P("Distribution predefined/elemcount=5 OK");
        } catch (Throwable t) { P("Distribution FAILED :: " + t.getMessage()); }

        try {
            mesh.run();
            P("mesh elements = " + mesh.stat().getNumElem());
            String[] types = {"prism", "hex", "tet", "tri", "edg"};
            for (int i = 0; i < types.length; i++) {
                try { P("   " + types[i] + " = " + mesh.stat().getNumElem(types[i])); }
                catch (Throwable t) { P("   " + types[i] + " : " + t.getMessage()); }
            }
        } catch (Throwable t) { P("mesh.run FAILED :: " + t.getMessage()); }

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
        dumpProps(model, "spf", "inl1", new String[]{"BoundaryCondition", "MassFlowType",
                                                    "StandardFlowRateDefinedBy"});
        dumpProps(model, "spf", "out1", new String[]{"BoundaryCondition"});
        dumpProps(model, "tds", "reac1", new String[]{"R_c_src", "ReactingVolumeType", "Chemistry"});
        dumpProps(model, "tds", "inflow1", new String[]{"BoundaryConditionType"});
        dumpProps(model, "tds", "cdm1", new String[]{"u_src", "DiffusionCoefficientSource"});

        try {
            model.component("comp1").physics("spf").feature("grav1").set("projectGravity", false);
            P("grav1 projectGravity=false OK");
        } catch (Throwable t) { P("grav1 projectGravity FAILED :: " + t.getMessage()); }

        try {
            model.component("comp1").physics("spf").feature("inl1").set("BoundaryCondition", "MassFlow");
            model.component("comp1").physics("spf").feature("inl1").set("mfr", "1.6667e-6[kg/s]");
            P("inl1 MassFlow + mfr OK");
        } catch (Throwable t) { P("inl1 MassFlow FAILED :: " + t.getMessage()); }

        try {
            model.component("comp1").physics("tds").feature("reac1").set("R_c", "-k_react*c");
            P("reac1 R_c OK");
        } catch (Throwable t) { P("reac1 R_c FAILED :: " + t.getMessage()); }

        try {
            model.component("comp1").physics("tds").feature("inflow1").set("c0", "c_in");
            P("inflow1 c0 OK");
        } catch (Throwable t) { P("inflow1 c0 FAILED :: " + t.getMessage()); }

        try {
            model.component("comp1").material().create("mat1", "Common");
            model.component("comp1").material("mat1").propertyGroup("def")
                 .set("density", "1000[kg/m^3]")
                 .set("dynamicviscosity", "1e-3[Pa*s]")
                 .set("diffusioncoefficient", new String[]{"D_sp"});
            P("material OK");
        } catch (Throwable t) { P("material FAILED :: " + t.getMessage()); }

        // ---------------------------------------------------------------- C
        P("=== C: STUDY DISABLING ===");
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").create("td", "Transient");
        String[] disVals = {"tds/inflow1", "inflow1", "tds", "spf/inl1"};
        for (int i = 0; i < disVals.length; i++) {
            String v = disVals[i];
            try {
                model.study("std1").feature("td").set("useadvanceddisable", true);
                model.study("std1").feature("td").set("disabledphysics", new String[]{v});
                P("disabledphysics=" + v + " OK -> " + Arrays.toString(
                    model.study("std1").feature("td").getStringArray("disabledphysics")));
            } catch (Throwable t) { P("disabledphysics=" + v + " FAIL :: " + t.getMessage()); }
        }
        P("td props = " + Arrays.toString(model.study("std1").feature("td").properties()));
        P("stat props = " + Arrays.toString(model.study("std1").feature("stat").properties()));
        String[] notSol = {"notsolmethod", "notstudy", "notsolnum", "useinitsol",
                           "initmethod", "initstudy", "solnum", "tlist"};
        for (int i = 0; i < notSol.length; i++) {
            String pr = notSol[i];
            String[] steps = {"stat", "td"};
            for (int j = 0; j < steps.length; j++) {
                try {
                    String[] av = model.study("std1").feature(steps[j]).getAllowedPropertyValues(pr);
                    if (av != null) P("   " + steps[j] + "." + pr + " allowed = " + Arrays.toString(av));
                } catch (Throwable t) { /* not present */ }
            }
        }

        // ---------------------------------------------------------------- B
        P("=== B: PROBES ===");
        try {
            model.probe().create("pdom", "Domain");
            model.probe("pdom").set("edim", 3);
            model.probe("pdom").set("expr", "c");
            String[] pmeth = {"method", "intorder", "intvolume", "intsurface"};
            for (int i = 0; i < pmeth.length; i++) {
                try {
                    String[] av = model.probe("pdom").getAllowedPropertyValues(pmeth[i]);
                    if (av != null) P("   pdom." + pmeth[i] + " allowed = " + Arrays.toString(av));
                } catch (Throwable t) { P("   pdom." + pmeth[i] + " : " + t.getMessage()); }
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
        } catch (Throwable t) { P("Domain probe FAILED :: " + t.getMessage()); }

        try {
            model.probe().create("ppoint", "Point");
            P("Point props = " + Arrays.toString(model.probe("ppoint").properties()));
            model.probe("ppoint").set("edim", 3);
            model.probe("ppoint").set("expr", "c");
            String[] pcand = {"coords", "coord", "points", "p1", "point"};
            for (int i = 0; i < pcand.length; i++) {
                String cand = pcand[i];
                try {
                    model.probe("ppoint").set(cand, new double[][]{{1.0, 0.0, 0.5}});
                    P("   Point." + cand + " accepted -> "
                        + Arrays.deepToString(model.probe("ppoint").getDoubleMatrix(cand)));
                } catch (Throwable t) { P("   Point." + cand + " FAIL :: " + t.getMessage()); }
            }
        } catch (Throwable t) { P("Point probe FAILED :: " + t.getMessage()); }

        // ---------------------------------------------------------------- E
        P("=== E: CUTLINE3D ===");
        try {
            model.result().dataset().create("cln1", "CutLine3D");
            String[] ccand = {"point1", "point2", "p1", "p2", "genpoints", "method"};
            for (int i = 0; i < ccand.length; i++) {
                String cand = ccand[i];
                try {
                    if (cand.equals("method")) {
                        model.result().dataset("cln1").set("method", "line");
                    } else if (cand.equals("genpoints")) {
                        model.result().dataset("cln1").set(cand, true);
                    } else {
                        model.result().dataset("cln1").set(cand, new String[]{"0", "0", "0"});
                    }
                    P("   cln1." + cand + " accepted");
                } catch (Throwable t) { P("   cln1." + cand + " FAIL :: " + t.getMessage()); }
            }
        } catch (Throwable t) { P("CutLine3D FAILED :: " + t.getMessage()); }

        // ---------------------------------------------------------------- F
        P("=== F: PARAMETRIC ===");
        try {
            model.study("std1").create("param", "Parametric");
            String[] pprops = {"pname", "plist", "plistarr", "sweeptype", "keepsol", "punit"};
            for (int i = 0; i < pprops.length; i++) {
                String pr = pprops[i];
                try {
                    String[] av = model.study("std1").feature("param").getAllowedPropertyValues(pr);
                    if (av != null) P("   param." + pr + " allowed = " + Arrays.toString(av));
                } catch (Throwable t) { /* not enum */ }
            }
            model.study("std1").feature("param").set("pname", new String[]{"Q_vol"});
            model.study("std1").feature("param").set("plistarr", new String[]{"10 100 1000"});
            model.study("std1").feature("param").set("punit", new String[]{"uL/min"});
            P("   param set OK");
        } catch (Throwable t) { P("Parametric FAILED :: " + t.getMessage()); }
    }
}
