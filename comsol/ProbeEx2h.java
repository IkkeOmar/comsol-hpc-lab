// ProbeEx2h.java - validation probe on the REAL Exercise-2 geometry.
//
// Everything here is a dry run of the production model:
//   (1) real geometry: 60x60x1 mm chamber + 150x2x1 mm channel (coplanar,
//       crossing) + r=25 mm x 100 um cell layer hanging below z=0
//   (2) MESH STRATEGY (the main risk): Swept prisms for the 100 um cell
//       layer + FreeTet for the fluid, with a graded Size
//   (3) material selection per domain, all physics nodes and BCs
//   (4) Rectangle pulse function, checked by evaluation
//   (5) study: stationary spf step + transient tds step (tds only)
//   (6) extraction: AvVolume (chamber average), EvalPoint on a vectorised
//       CutPoint3D (vertical profile in the cell layer), Domain probe and
//       Point probe selection APIs, CutLine3D property names
//
// NOTE: no anonymous inner classes / lambdas (comsolcompile emits only the
// top-level .class file, so the batch loader cannot resolve "ProbeEx2h$1").

import com.comsol.model.*;
import com.comsol.model.util.*;
import java.io.PrintWriter;
import java.util.Arrays;

public class ProbeEx2h {

    static Model model;

    static void P(String s) { System.out.println("PROBE " + s); }

    public static void main(String[] args) {
        try { run(); } catch (Throwable t) { t.printStackTrace(); }
        P("PROBE_EX2H DONE");
    }

    public static void run() throws Exception {
        model = ModelUtil.create("ProbeEx2h");
        model.component().create("comp1");

        // ------------------------------------------------------------------
        // (1) GEOMETRY  (mm units)
        // ------------------------------------------------------------------
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blk_ch", "Block");
        g.feature("blk_ch").set("size", new String[]{"60", "60", "1"});
        g.feature("blk_ch").set("pos",  new String[]{"-30", "-30", "0"});
        g.create("blk_chn", "Block");
        g.feature("blk_chn").set("size", new String[]{"150", "2", "1"});
        g.feature("blk_chn").set("pos",  new String[]{"-75", "-1", "0"});
        // cell layer: r = 25 mm, h = 100 um, centre (0,0,-50 um)
        g.create("cyl_cell", "Cylinder");
        g.feature("cyl_cell").set("r", "25");
        g.feature("cyl_cell").set("h", "0.1");
        g.feature("cyl_cell").set("pos", new String[]{"0", "0", "-0.1"});
        // probe points
        g.create("ptP1", "Point");
        g.feature("ptP1").set("p", new String[]{"-29.5", "0", "0.05"});
        g.create("ptP2", "Point");
        g.feature("ptP2").set("p", new String[]{"0", "29.5", "0.05"});
        g.create("ptP3", "Point");
        g.feature("ptP3").set("p", new String[]{"29.5", "0", "0.05"});
        g.run();

        boxDom("selCham", 3, "inside", "-30.1", "30.1", "-30.1", "30.1", "-0.1", "1.1");
        boxDom("selCell", 3, "inside", "-25.1", "25.1", "-25.1", "25.1", "-0.2", "-0.001");
        boxDom("selChan", 3, "inside", "-75.1", "75.1", "-1.1", "1.1", "-0.1", "1.1");
        boxDom("selFloor", 2, "allvertices", "-80", "80", "-35", "35", "-0.001", "0.001");
        boxDom("selCellTop", 2, "inside", "-25.1", "25.1", "-25.1", "25.1", "-0.001", "0.001");
        boxDom("selCellBot", 2, "inside", "-25.1", "25.1", "-25.1", "25.1", "-0.11", "-0.09");
        boxDom("selIn", 2, "allvertices", "-75.1", "-74.9", "-1.1", "1.1", "-0.1", "1.1");
        boxDom("selOut", 2, "allvertices", "74.9", "75.1", "-1.1", "1.1", "-0.1", "1.1");
        boxDom("selWallL", 2, "allvertices", "-75.1", "-29.9", "0.9", "1.1", "-0.1", "1.1");
        boxDom("selWallR", 2, "allvertices", "29.9", "75.1", "0.9", "1.1", "-0.1", "1.1");
        g.run();

        P("nDomains=" + g.getNDomains() + " nBoundaries=" + g.getNBoundaries()
          + " nEdges=" + g.getNEdges());
        P("cham  = " + Arrays.toString(sel("selCham", 3)));
        P("cell  = " + Arrays.toString(sel("selCell", 3)));
        P("chan  = " + Arrays.toString(sel("selChan", 3)));
        P("floor = " + Arrays.toString(sel("selFloor", 2)));
        P("cellT = " + Arrays.toString(sel("selCellTop", 2)));
        P("cellB = " + Arrays.toString(sel("selCellBot", 2)));
        P("in    = " + Arrays.toString(sel("selIn", 2)));
        P("out   = " + Arrays.toString(sel("selOut", 2)));
        P("wallL = " + Arrays.toString(sel("selWallL", 2)));
        P("wallR = " + Arrays.toString(sel("selWallR", 2)));
        P("selection tags: " + Arrays.toString(model.selection().tags()));

        int[] chamDom = sel("selCham", 3);
        int[] cellDom = sel("selCell", 3);
        int[] fluidDom = new int[chamDom.length + 2 + 1];
        {
            // fluid = every domain except the cell layer
            int k = 0;
            for (int i = 1; i <= g.getNDomains(); i++) {
                boolean isCell = false;
                for (int j = 0; j < cellDom.length; j++) if (cellDom[j] == i) isCell = true;
                if (!isCell) fluidDom[k++] = i;
            }
            fluidDom = Arrays.copyOf(fluidDom, k);
        }
        P("fluid = " + Arrays.toString(fluidDom));
        int[] inFace = sel("selIn", 2);
        int[] outFace = sel("selOut", 2);
        int[] cellTop = sel("selCellTop", 2);
        int[] cellBot = sel("selCellBot", 2);
        int[] wallL = sel("selWallL", 2);
        int[] wallR = sel("selWallR", 2);
        int[] floorAll = sel("selFloor", 2);

        // ------------------------------------------------------------------
        // (2) MESH
        // ------------------------------------------------------------------
        P("=== MESH ===");
        MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");
        try {
            mesh.create("swe_cell", "Sweep");
            P("swe_cell children: " + Arrays.toString(mesh.feature("swe_cell").feature().tags()));
            mesh.feature("swe_cell").selection().geom("geom1", 3);
            mesh.feature("swe_cell").selection().set(cellDom);
            mesh.feature("swe_cell").set("element", "prism");
            try {
                mesh.feature("swe_cell").set("sourceface", cellTop);
                P("sourceface set OK");
            } catch (Throwable t) { P("sourceface set FAILED :: " + t.getMessage()); }
            try {
                mesh.feature("swe_cell").set("targetface", cellBot);
                P("targetface set OK");
            } catch (Throwable t) { P("targetface set FAILED :: " + t.getMessage()); }
            mesh.feature("swe_cell").create("dis1", "Distribution");
            mesh.feature("swe_cell").feature("dis1").set("type", "predefined");
            mesh.feature("swe_cell").feature("dis1").set("elemcount", "16");
            P("sweep + Distribution(16) set OK");
        } catch (Throwable t) { P("SWEEP SETUP FAILED :: " + t.getMessage()); }

        try {
            mesh.create("ftet1", "FreeTet");
            mesh.feature("ftet1").selection().geom("geom1", 3);
            mesh.feature("ftet1").selection().set(fluidDom);
            mesh.feature("ftet1").create("size1", "Size");
            mesh.feature("ftet1").feature("size1").set("custom", true);
            mesh.feature("ftet1").feature("size1").set("hmaxactive", true);
            mesh.feature("ftet1").feature("size1").set("hmax", "1.5[mm]");
            mesh.feature("ftet1").feature("size1").set("hminactive", true);
            mesh.feature("ftet1").feature("size1").set("hmin", "0.05[mm]");
            mesh.feature("ftet1").feature("size1").set("hgradactive", true);
            mesh.feature("ftet1").feature("size1").set("hgrad", 1.35);
            // refine along the channel walls
            int[] both = new int[wallL.length + wallR.length];
            System.arraycopy(wallL, 0, both, 0, wallL.length);
            System.arraycopy(wallR, 0, both, wallL.length, wallR.length);
            mesh.feature("ftet1").create("size2", "Size");
            mesh.feature("ftet1").feature("size2").selection().geom("geom1", 2);
            mesh.feature("ftet1").feature("size2").selection().set(both);
            mesh.feature("ftet1").feature("size2").set("custom", true);
            mesh.feature("ftet1").feature("size2").set("hmaxactive", true);
            mesh.feature("ftet1").feature("size2").set("hmax", "0.35[mm]");
            mesh.feature("ftet1").feature("size2").set("hminactive", true);
            mesh.feature("ftet1").feature("size2").set("hmin", "0.08[mm]");
            mesh.feature("ftet1").feature("size2").set("hgradactive", true);
            mesh.feature("ftet1").feature("size2").set("hgrad", 1.25);
            P("ftet1 + graded sizes set OK");
        } catch (Throwable t) { P("FTET SETUP FAILED :: " + t.getMessage()); }

        long t0 = System.currentTimeMillis();
        try {
            mesh.run();
            P("MESH OK in " + (System.currentTimeMillis() - t0) / 1000 + " s, elements = "
              + mesh.stat().getNumElem());
            String[] types = {"prism", "hex", "tet", "tri", "edg"};
            for (int i = 0; i < types.length; i++) {
                try { P("   " + types[i] + " = " + mesh.stat().getNumElem(types[i])); }
                catch (Throwable t) { P("   " + types[i] + " : " + t.getMessage()); }
            }
        } catch (Throwable t) {
            P("MESH FAILED :: " + t.getMessage());
        }

        // ------------------------------------------------------------------
        // (3) MATERIALS + PHYSICS
        // ------------------------------------------------------------------
        P("=== PHYSICS ===");
        model.param().set("Q_m3s", "1.6666667e-9[m^3/s]");
        model.param().set("t_fill", "2160[s]");
        model.param().set("D_sp", "200[um^2/s]");
        model.param().set("D_eff", "0.7*D_sp");
        model.param().set("k_react", "0.15[1/s]");
        model.param().set("c_in", "1[mol/m^3]");
        model.param().set("rho_f", "1000[kg/m^3]");
        model.param().set("mu_f", "1e-3[Pa*s]");

        try {
            model.component("comp1").material().create("mat_fluid", "Common");
            model.component("comp1").material("mat_fluid").propertyGroup("def")
                 .set("density", "rho_f")
                 .set("dynamicviscosity", "mu_f")
                 .set("diffusioncoefficient", new String[]{"D_sp"});
            P("mat_fluid created");
        } catch (Throwable t) { P("mat_fluid FAILED :: " + t.getMessage()); }
        try {
            model.component("comp1").material().create("mat_cell", "Common");
            model.component("comp1").material("mat_cell").propertyGroup("def")
                 .set("diffusioncoefficient", new String[]{"D_eff"});
            model.component("comp1").material("mat_cell").selection().geom("geom1", 3);
            model.component("comp1").material("mat_cell").selection().set(cellDom);
            P("mat_cell + domain selection OK");
        } catch (Throwable t) { P("mat_cell FAILED :: " + t.getMessage()); }

        try {
            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            model.component("comp1").physics("spf").selection().geom("geom1", 3);
            model.component("comp1").physics("spf").selection().set(fluidDom);
            model.component("comp1").physics("spf").feature("grav1").set("projectGravity", false);
            model.component("comp1").physics("spf").create("inl1", "Inlet", 2);
            model.component("comp1").physics("spf").feature("inl1").selection().geom("geom1", 2);
            model.component("comp1").physics("spf").feature("inl1").selection().set(inFace);
            model.component("comp1").physics("spf").feature("inl1").set("BoundaryCondition", "MassFlow");
            model.component("comp1").physics("spf").feature("inl1").set("mfr", "rho_f*Q_m3s");
            model.component("comp1").physics("spf").create("out1", "Outlet", 2);
            model.component("comp1").physics("spf").feature("out1").selection().geom("geom1", 2);
            model.component("comp1").physics("spf").feature("out1").selection().set(outFace);
            model.component("comp1").physics("spf").feature("out1").set("p0", "0");
            P("spf set OK");
        } catch (Throwable t) { P("spf FAILED :: " + t.getMessage()); }

        try {
            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            model.component("comp1").physics("tds").feature("cdm1").selection().geom("geom1", 3);
            model.component("comp1").physics("tds").feature("cdm1").selection().set(fluidDom);
            model.component("comp1").physics("tds").feature("cdm1").set("u_src", "root.comp1.u");
            model.component("comp1").physics("tds").feature("cdm1").set("DiffusionCoefficientSource", "mat");
            model.component("comp1").physics("tds").create("fluid2", "Fluid", 3);
            model.component("comp1").physics("tds").feature("fluid2").selection().geom("geom1", 3);
            model.component("comp1").physics("tds").feature("fluid2").selection().set(cellDom);
            model.component("comp1").physics("tds").feature("fluid2").set("u_src", "userdef");
            model.component("comp1").physics("tds").feature("fluid2").set("u", new String[]{"0", "0", "0"});
            model.component("comp1").physics("tds").feature("fluid2").set("DiffusionCoefficientSource", "mat");
            model.component("comp1").physics("tds").create("reac1", "Reactions", 3);
            model.component("comp1").physics("tds").feature("reac1").selection().geom("geom1", 3);
            model.component("comp1").physics("tds").feature("reac1").selection().set(cellDom);
            model.component("comp1").physics("tds").feature("reac1").set("R_c", "-k_react*c");
            model.component("comp1").physics("tds").feature("reac1").set("ReactingVolumeType", "TotalVolume");
            model.component("comp1").physics("tds").create("inflowPulse", "Inflow", 2);
            model.component("comp1").physics("tds").feature("inflowPulse").selection().geom("geom1", 2);
            model.component("comp1").physics("tds").feature("inflowPulse").selection().set(inFace);
            model.component("comp1").physics("tds").feature("inflowPulse").set("c0", "rect1(t)");
            model.component("comp1").physics("tds").create("inflowConst", "Inflow", 2);
            model.component("comp1").physics("tds").feature("inflowConst").selection().geom("geom1", 2);
            model.component("comp1").physics("tds").feature("inflowConst").selection().set(inFace);
            model.component("comp1").physics("tds").feature("inflowConst").set("c0", "c_in");
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
            model.component("comp1").physics("tds").feature("outflow1").selection().geom("geom1", 2);
            model.component("comp1").physics("tds").feature("outflow1").selection().set(outFace);
            model.component("comp1").physics("tds").create("init2", "init", 3);
            model.component("comp1").physics("tds").feature("init2").selection().geom("geom1", 3);
            model.component("comp1").physics("tds").feature("init2").selection().set(sel("selChan", 3));
            model.component("comp1").physics("tds").feature("init2").set("initc", "c_in");
            P("tds set OK");
        } catch (Throwable t) { P("tds FAILED :: " + t.getMessage()); }

        // ------------------------------------------------------------------
        // (4) RECTANGLE FUNCTION
        // ------------------------------------------------------------------
        P("=== RECT FUNCTION ===");
        try {
            model.func().create("rect1", "Rectangle");
            model.func("rect1").set("lower", "-5[s]");
            model.func("rect1").set("upper", "t_fill");
            model.func("rect1").set("smooth", "5");
            model.func("rect1").set("ncontder", "2");
            P("rect1 set OK, props=" + Arrays.toString(model.func("rect1").properties()));
        } catch (Throwable t) { P("rect1 FAILED :: " + t.getMessage()); }

        // ------------------------------------------------------------------
        // (5) STUDY
        // ------------------------------------------------------------------
        P("=== STUDY ===");
        try {
            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            try {
                model.study("std1").feature("stat").set("physselection", new String[]{"spf"});
                P("stat physselection=spf OK");
            } catch (Throwable t) { P("stat physselection FAILED :: " + t.getMessage()); }
            model.study("std1").create("time", "Transient");
            model.study("std1").feature("time").set("tlist", "range(0,20,60)");
            try {
                model.study("std1").feature("time").set("physselection", new String[]{"tds"});
                P("time physselection=tds OK");
            } catch (Throwable t) { P("time physselection FAILED :: " + t.getMessage()); }
            try {
                model.study("std1").feature("time").set("useadvanceddisable", true);
                model.study("std1").feature("time").set("disabledphysics",
                    new String[]{"tds/inflowConst"});
                P("disabledphysics OK");
            } catch (Throwable t) { P("disabledphysics FAILED :: " + t.getMessage()); }
            model.study("std1").createAutoSequences("all");
            P("createAutoSequences OK; sol tags = " + Arrays.toString(model.sol().tags()));
        } catch (Throwable t) { P("STUDY SETUP FAILED :: " + t.getMessage()); }

        long t1 = System.currentTimeMillis();
        try {
            model.sol("sol1").runAll();
            P("SOLVE OK in " + (System.currentTimeMillis() - t1) / 1000 + " s");
        } catch (Throwable t) { P("SOLVE FAILED :: " + t.getMessage()); }

        // ------------------------------------------------------------------
        // (6) EXTRACTION
        // ------------------------------------------------------------------
        P("=== EXTRACTION ===");
        try {
            model.result().numerical().create("av1", "AvVolume");
            model.result().numerical("av1").selection().geom("geom1", 3);
            model.result().numerical("av1").selection().set(chamDom);
            model.result().numerical("av1").set("expr", new String[]{"c"});
            double[][] r = model.result().numerical("av1").getReal();
            P("AvVolume dims " + r.length + "x" + (r.length > 0 ? r[0].length : 0)
              + " -> " + Arrays.toString(r[0]));
        } catch (Throwable t) { P("AvVolume FAILED :: " + t.getMessage()); }

        try {
            model.result().dataset().create("cpt1", "CutPoint3D");
            model.result().dataset("cpt1").set("pointx", "0");
            model.result().dataset("cpt1").set("pointy", "0");
            model.result().dataset("cpt1").set("pointz", "0");
            model.result().dataset("cpt1").set("pointz_vector_method", "range");
            model.result().dataset("cpt1").set("pointz_vector_start", "-0.1[mm]");
            model.result().dataset("cpt1").set("pointz_vector_stop", "0");
            model.result().dataset("cpt1").set("pointz_vector_numvalues", "11");
            model.result().numerical().create("pev1", "EvalPoint");
            model.result().numerical("pev1").set("data", "cpt1");
            model.result().numerical("pev1").set("expr", new String[]{"c", "z"});
            double[][] r = model.result().numerical("pev1").getReal();
            P("EvalPoint dims " + r.length + "x" + (r.length > 0 ? r[0].length : 0));
            for (int i = 0; i < r.length; i++) {
                StringBuilder sb = new StringBuilder("   row" + i + ": ");
                for (int j = 0; j < r[i].length; j++) sb.append(String.format("%.6g ", r[i][j]));
                P(sb.toString());
            }
        } catch (Throwable t) { P("EvalPoint FAILED :: " + t.getMessage()); }

        try {
            model.result().dataset().create("cln1", "CutLine3D");
            System.out.println("PROBE CutLine3D props = "
                + Arrays.toString(model.result().dataset("cln1").properties()));
            String[] cands = {"pdpoint", "pddir", "genpoints", "method", "point1", "point2",
                              "genpnpoint", "bounded"};
            for (int i = 0; i < cands.length; i++) {
                String c = cands[i];
                try {
                    if (c.equals("pdpoint")) {
                        model.result().dataset("cln1").set(c, new String[]{"0", "0", "-0.1[mm]"});
                    } else if (c.equals("pddir")) {
                        model.result().dataset("cln1").set(c, new String[]{"0", "0", "1"});
                    } else if (c.equals("genpoints")) {
                        model.result().dataset("cln1").set(c, "on");
                    } else if (c.equals("method")) {
                        model.result().dataset("cln1").set(c, "general");
                    } else if (c.equals("bounded")) {
                        model.result().dataset("cln1").set(c, "on");
                    } else {
                        model.result().dataset("cln1").set(c, new String[]{"0", "0", "0"});
                    }
                    P("   cln1." + c + " accepted");
                } catch (Throwable t) { P("   cln1." + c + " FAIL :: " + t.getMessage()); }
            }
        } catch (Throwable t) { P("CutLine3D FAILED :: " + t.getMessage()); }

        try {
            model.probe().create("pdom", "Domain");
            model.probe("pdom").set("edim", 3);
            model.probe("pdom").set("expr", "c");
            model.probe("pdom").set("method", "integration");
            model.probe("pdom").set("intvolume", "on");
            try {
                model.probe("pdom").selection().named("geom1_selCham");
                P("Domain probe named() OK");
            } catch (Throwable t) { P("Domain probe named() FAILED :: " + t.getMessage()); }
        } catch (Throwable t) { P("Domain probe FAILED :: " + t.getMessage()); }

        try {
            model.probe().create("ppoint", "Point");
            model.probe("ppoint").set("edim", 3);
            model.probe("ppoint").set("expr", "c");
            try {
                model.probe("ppoint").selection().named("geom1_ptP1");
                P("Point probe named() OK");
            } catch (Throwable t) { P("Point probe named() FAILED :: " + t.getMessage()); }
        } catch (Throwable t) { P("Point probe FAILED :: " + t.getMessage()); }

        // global evaluation of the pulse function
        try {
            model.result().numerical().create("gev1", "EvalGlobal");
            model.result().numerical("gev1").set("expr", new String[]{
                "rect1(-10[s])", "rect1(0[s])", "rect1(1080[s])",
                "rect1(2159[s])", "rect1(2160[s])", "rect1(3000[s])"});
            double[][] r = model.result().numerical("gev1").getReal();
            P("rect1 samples: " + Arrays.toString(r[0]));
        } catch (Throwable t) { P("EvalGlobal rect FAILED :: " + t.getMessage()); }

        // write a small CSV to prove file output works
        try {
            PrintWriter pw = new PrintWriter("<project>/"
                + "<project>/probe_ex2h_out.csv");
            pw.println("hello,from,comsol");
            pw.close();
            P("CSV write OK");
        } catch (Throwable t) { P("CSV write FAILED :: " + t.getMessage()); }
    }

    // ------------------------------------------------------------------
    static void boxDom(String tag, int dim, String cond,
                        String xmin, String xmax, String ymin, String ymax,
                        String zmin, String zmax) {
        GeomSequence g = model.component("comp1").geom("geom1");
        g.create(tag, "BoxSelection");
        g.feature(tag).set("entitydim", dim);
        g.feature(tag).set("condition", cond);
        g.feature(tag).set("xmin", xmin); g.feature(tag).set("xmax", xmax);
        g.feature(tag).set("ymin", ymin); g.feature(tag).set("ymax", ymax);
        g.feature(tag).set("zmin", zmin); g.feature(tag).set("zmax", zmax);
    }

    static int[] sel(String tag, int dim) {
        try {
            return model.selection("geom1_" + tag).entities(dim);
        } catch (Throwable t) {
            P("selection geom1_" + tag + " FAILED :: " + t.getMessage());
            return new int[0];
        }
    }
}
