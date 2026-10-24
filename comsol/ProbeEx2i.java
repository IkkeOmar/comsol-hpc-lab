// ProbeEx2i.java - settle the MESH STRATEGY and the remaining API on the real
// Exercise-2 geometry.
//
// Everything is runtime-enumerated; no guessed property names.
//
// Background (established by the earlier probes):
//   * Form Union of chamber(60x60x1) + channel(150x2x1) + cell cylinder
//     (r=25, h=0.1, z=-0.1..0) gives several domains; the disc punches a
//     circular hole in the fluid domains' z=0 floor, so the fluid can NO
//     LONGER be swept in z (source != target).  The cell domain's top face is
//     split into several faces (one per adjacent fluid domain) while its
//     bottom is a single disc -> a plain Sweep has no 1:1 source/target.
//
// Strategy under test:
//   V1  FreeTet on the fluid domains only (graded)         -> baseline
//   V2  FreeTet(fluid) + Sweep(cell)                        -> 3:1 face mismatch?
//   V3  FreeTet(fluid) + BndLayer(cell, 20 layers)          -> prisms in the layer
//   V4  FreeTet on everything (coarse)                       -> fallback
//
// Also probed: TDS default-node deactivation (active(false)), material
// selection by domain, second "init" node, Domain/Point probe selections,
// AvVolume time series, CutPoint3D vectorised point line, and a short solve.

import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class ProbeEx2i {

    static void P(String s) { System.out.println("PROBE " + s); }

    // ------------------------------------------------------------------
    // geometry + selections, shared by all variants
    // ------------------------------------------------------------------
    static int[] chamDom, cellDom, fluidDom, inFace, outFace, cellTopF, cellBotF;

    static Model build(String name, boolean withCell) {
        Model m = ModelUtil.create(name);
        m.component().create("comp1");
        GeomSequence g = m.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blk_ch", "Block");
        g.feature("blk_ch").set("size", new String[]{"60", "60", "1"});
        g.feature("blk_ch").set("pos",  new String[]{"-30", "-30", "0"});
        g.create("blk_chn", "Block");
        g.feature("blk_chn").set("size", new String[]{"150", "2", "1"});
        g.feature("blk_chn").set("pos",  new String[]{"-75", "-1", "0"});
        if (withCell) {
            g.create("cyl_cell", "Cylinder");
            g.feature("cyl_cell").set("r", "25");
            g.feature("cyl_cell").set("h", "0.1");
            g.feature("cyl_cell").set("pos", new String[]{"0", "0", "-0.1"});
        }
        g.run();

        box(m, "selCham",  3, "inside",      "-30.1", "30.1", "-30.1", "30.1", "-0.001", "1.1");
        box(m, "selCell",  3, "inside",      "-25.1", "25.1", "-25.1", "25.1", "-0.2",   "0.0001");
        box(m, "selIn",    2, "allvertices", "-75.1", "-74.9", "-1.1",  "1.1",  "-0.1",   "1.1");
        box(m, "selOut",   2, "allvertices", "74.9",  "75.1",  "-1.1",  "1.1",  "-0.1",   "1.1");
        box(m, "selCellTop", 2, "inside",    "-25.1", "25.1", "-25.1", "25.1", "-0.001", "0.001");
        box(m, "selCellBot", 2, "inside",    "-25.1", "25.1", "-25.1", "25.1", "-0.101", "-0.099");
        g.run();

        chamDom = ent(m, "selCham", 3);
        cellDom = ent(m, "selCell", 3);
        inFace  = ent(m, "selIn", 2);
        outFace = ent(m, "selOut", 2);
        cellTopF = ent(m, "selCellTop", 2);
        cellBotF = ent(m, "selCellBot", 2);

        int nd = g.getNDomains();
        int[] all = new int[nd];
        for (int i = 0; i < nd; i++) all[i] = i + 1;
        int k = 0;
        int[] fl = new int[nd];
        for (int i = 0; i < nd; i++) {
            boolean isCell = false;
            for (int j = 0; j < cellDom.length; j++) if (cellDom[j] == all[i]) isCell = true;
            if (!isCell) fl[k++] = all[i];
        }
        fluidDom = Arrays.copyOf(fl, k);
        return m;
    }

    static void box(Model m, String tag, int dim, String cond,
                    String xmin, String xmax, String ymin, String ymax,
                    String zmin, String zmax) {
        GeomSequence g = m.component("comp1").geom("geom1");
        g.create(tag, "BoxSelection");
        g.feature(tag).set("entitydim", dim);
        g.feature(tag).set("condition", cond);
        g.feature(tag).set("xmin", xmin); g.feature(tag).set("xmax", xmax);
        g.feature(tag).set("ymin", ymin); g.feature(tag).set("ymax", ymax);
        g.feature(tag).set("zmin", zmin); g.feature(tag).set("zmax", zmax);
    }

    static int[] ent(Model m, String tag, int dim) {
        try { return m.selection("geom1_" + tag).entities(dim); }
        catch (Throwable t) { P("sel " + tag + " FAILED :: " + t.getMessage()); return new int[0]; }
    }

    static void stats(MeshSequence mesh, String label, long t0) {
        try {
            P(label + ": elements = " + mesh.stat().getNumElem()
              + "  (" + (System.currentTimeMillis() - t0) / 1000 + " s)");
            String[] types = {"prism", "hex", "tet", "tri"};
            for (int i = 0; i < types.length; i++) {
                try { P("      " + types[i] + " = " + mesh.stat().getNumElem(types[i])); }
                catch (Throwable t) { /* absent */ }
            }
        } catch (Throwable t) { P(label + ": stats failed :: " + t.getMessage()); }
    }

    // ------------------------------------------------------------------
    public static void main(String[] args) {
        try { run(); } catch (Throwable t) { t.printStackTrace(); }
        P("PROBE_EX2I DONE");
    }

    public static void run() throws Exception {

        // ---------------- V1: FreeTet on fluid only ----------------
        P("################ V1: FreeTet(fluid) ################");
        Model m1 = build("V1", true);
        P("cham=" + Arrays.toString(chamDom) + " cell=" + Arrays.toString(cellDom)
          + " fluid=" + Arrays.toString(fluidDom));
        P("cellTop=" + Arrays.toString(cellTopF) + " cellBot=" + Arrays.toString(cellBotF)
          + " in=" + Arrays.toString(inFace) + " out=" + Arrays.toString(outFace));
        {
            MeshSequence mesh = m1.component("comp1").mesh().create("mesh1", "geom1");
            mesh.create("ftet1", "FreeTet");
            mesh.feature("ftet1").selection().geom("geom1", 3);
            mesh.feature("ftet1").selection().set(fluidDom);
            mesh.feature("ftet1").create("size1", "Size");
            mesh.feature("ftet1").feature("size1").set("custom", true);
            mesh.feature("ftet1").feature("size1").set("hmaxactive", true);
            mesh.feature("ftet1").feature("size1").set("hmax", "3[mm]");
            mesh.feature("ftet1").feature("size1").set("hminactive", true);
            mesh.feature("ftet1").feature("size1").set("hmin", "0.3[mm]");
            mesh.feature("ftet1").feature("size1").set("hgradactive", true);
            mesh.feature("ftet1").feature("size1").set("hgrad", 1.25);
            long t0 = System.currentTimeMillis();
            try { mesh.run(); stats(mesh, "V1", t0); }
            catch (Throwable t) { P("V1 MESH FAILED :: " + t.getMessage()); }
        }

        // ---------------- V2: FreeTet(fluid) + Sweep(cell) ----------------
        P("################ V2: FreeTet(fluid) + Sweep(cell) ################");
        Model m2 = build("V2", true);
        {
            MeshSequence mesh = m2.component("comp1").mesh().create("mesh1", "geom1");
            mesh.create("swe_cell", "Sweep");
            mesh.feature("swe_cell").selection().geom("geom1", 3);
            mesh.feature("swe_cell").selection().set(cellDom);
            mesh.feature("swe_cell").set("element", "prism");
            P("swe_cell children: " + Arrays.toString(mesh.feature("swe_cell").feature().tags()));
            try {
                mesh.feature("swe_cell").create("dis1", "Distribution");
                mesh.feature("swe_cell").feature("dis1").set("type", "predefined");
                mesh.feature("swe_cell").feature("dis1").set("elemcount", "20");
                P("Distribution created OK");
            } catch (Throwable t) { P("Distribution FAILED :: " + t.getMessage()); }
            // what do sourceface / targetface accept?
            for (String pr : new String[]{"sourceface", "targetface", "sweeppath", "targetmesh"}) {
                try {
                    String[] av = mesh.feature("swe_cell").getAllowedPropertyValues(pr);
                    P("   swe_cell." + pr + " allowed = " + Arrays.toString(av));
                } catch (Throwable t) {
                    P("   swe_cell." + pr + " : " + t.getMessage());
                }
            }
            mesh.create("ftet1", "FreeTet");
            mesh.feature("ftet1").selection().geom("geom1", 3);
            mesh.feature("ftet1").selection().set(fluidDom);
            mesh.feature("ftet1").create("size1", "Size");
            mesh.feature("ftet1").feature("size1").set("custom", true);
            mesh.feature("ftet1").feature("size1").set("hmaxactive", true);
            mesh.feature("ftet1").feature("size1").set("hmax", "3[mm]");
            mesh.feature("ftet1").feature("size1").set("hminactive", true);
            mesh.feature("ftet1").feature("size1").set("hmin", "0.3[mm]");
            mesh.feature("ftet1").feature("size1").set("hgradactive", true);
            mesh.feature("ftet1").feature("size1").set("hgrad", 1.25);
            long t0 = System.currentTimeMillis();
            try { mesh.run(); stats(mesh, "V2", t0); }
            catch (Throwable t) { P("V2 MESH FAILED :: " + t.getMessage()); }
        }

        // ---------------- V3: FreeTet(fluid) + BndLayer(cell) ----------------
        P("################ V3: FreeTet(fluid) + BndLayer(cell) ################");
        Model m3 = build("V3", true);
        {
            MeshSequence mesh = m3.component("comp1").mesh().create("mesh1", "geom1");
            mesh.create("ftet1", "FreeTet");
            mesh.feature("ftet1").selection().geom("geom1", 3);
            mesh.feature("ftet1").selection().set(fluidDom);
            mesh.feature("ftet1").create("size1", "Size");
            mesh.feature("ftet1").feature("size1").set("custom", true);
            mesh.feature("ftet1").feature("size1").set("hmaxactive", true);
            mesh.feature("ftet1").feature("size1").set("hmax", "3[mm]");
            mesh.feature("ftet1").feature("size1").set("hminactive", true);
            mesh.feature("ftet1").feature("size1").set("hmin", "0.3[mm]");
            mesh.feature("ftet1").feature("size1").set("hgradactive", true);
            mesh.feature("ftet1").feature("size1").set("hgrad", 1.25);
            try {
                mesh.create("bl1", "BndLayer");
                mesh.feature("bl1").create("blp", "BndLayerProp");
                mesh.feature("bl1").selection().geom("geom1", 2);
                mesh.feature("bl1").selection().set(cellTopF);
                mesh.feature("bl1").feature("blp").selection().geom("geom1", 3);
                mesh.feature("bl1").feature("blp").selection().set(cellDom);
                P("BndLayerProp props = " + Arrays.toString(mesh.feature("bl1").feature("blp").properties()));
                mesh.feature("bl1").feature("blp").set("blnlayers", "20");
                mesh.feature("bl1").feature("blp").set("inittype", "blhmin");
                mesh.feature("bl1").feature("blp").set("blhmin", "5[um]");
                P("BndLayer set OK");
            } catch (Throwable t) { P("BndLayer FAILED :: " + t.getMessage()); }
            long t0 = System.currentTimeMillis();
            try { mesh.run(); stats(mesh, "V3", t0); }
            catch (Throwable t) { P("V3 MESH FAILED :: " + t.getMessage()); }
        }

        // ---------------- V4: FreeTet on everything (coarse) ----------------
        P("################ V4: FreeTet(all) coarse ################");
        Model m4 = build("V4", true);
        {
            MeshSequence mesh = m4.component("comp1").mesh().create("mesh1", "geom1");
            mesh.create("ftet1", "FreeTet");
            mesh.feature("ftet1").selection().geom("geom1", 3);
            int nd = mesh.feature("ftet1").selection().geom("geom1", 3) != null
                   ? m4.component("comp1").geom("geom1").getNDomains() : 0;
            int[] all = new int[nd];
            for (int i = 0; i < nd; i++) all[i] = i + 1;
            mesh.feature("ftet1").selection().set(all);
            mesh.feature("ftet1").create("size1", "Size");
            mesh.feature("ftet1").feature("size1").set("custom", true);
            mesh.feature("ftet1").feature("size1").set("hmaxactive", true);
            mesh.feature("ftet1").feature("size1").set("hmax", "3[mm]");
            mesh.feature("ftet1").feature("size1").set("hminactive", true);
            mesh.feature("ftet1").feature("size1").set("hmin", "0.05[mm]");
            mesh.feature("ftet1").feature("size1").set("hgradactive", true);
            mesh.feature("ftet1").feature("size1").set("hgrad", 1.3);
            long t0 = System.currentTimeMillis();
            try { mesh.run(); stats(mesh, "V4", t0); }
            catch (Throwable t) { P("V4 MESH FAILED :: " + t.getMessage()); }
        }

        // ---------------- physics / study / extraction on V1 ----------------
        P("################ PHYSICS + STUDY + EXTRACTION (on V1 mesh) ################");
        Model m5 = ModelUtil.create("V5");
        build("V5geom", true);   // rebuild geometry in its own model
        Model mm = m5;
        // copy the geometry from the V1 model is not possible, so rebuild here
        m5 = build("V5", true);
        {
            // materials
            m5.param().set("Q_m3s", "1.6666667e-9[m^3/s]");
            m5.param().set("D_sp", "200[um^2/s]");
            m5.param().set("D_eff", "0.7*D_sp");
            m5.param().set("k_react", "0.15[1/s]");
            m5.param().set("c_in", "1[mol/m^3]");
            m5.param().set("rho_f", "1000[kg/m^3]");
            m5.param().set("mu_f", "1e-3[Pa*s]");
            m5.component("comp1").material().create("mat_fluid", "Common");
            m5.component("comp1").material("mat_fluid").propertyGroup("def")
                .set("density", "rho_f")
                .set("dynamicviscosity", "mu_f")
                .set("diffusioncoefficient", new String[]{"D_sp"});
            m5.component("comp1").material().create("mat_cell", "Common");
            m5.component("comp1").material("mat_cell").propertyGroup("def")
                .set("diffusioncoefficient", new String[]{"D_eff"});
            m5.component("comp1").material("mat_cell").selection().geom("geom1", 3);
            m5.component("comp1").material("mat_cell").selection().set(cellDom);
            P("materials OK");

            // spf on the fluid domains only
            m5.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            m5.component("comp1").physics("spf").selection().geom("geom1", 3);
            m5.component("comp1").physics("spf").selection().set(fluidDom);
            m5.component("comp1").physics("spf").feature("grav1").set("projectGravity", false);
            m5.component("comp1").physics("spf").create("inl1", "Inlet", 2);
            m5.component("comp1").physics("spf").feature("inl1").selection().geom("geom1", 2);
            m5.component("comp1").physics("spf").feature("inl1").selection().set(inFace);
            m5.component("comp1").physics("spf").feature("inl1").set("BoundaryCondition", "MassFlow");
            m5.component("comp1").physics("spf").feature("inl1").set("mfr", "rho_f*Q_m3s");
            m5.component("comp1").physics("spf").create("out1", "Outlet", 2);
            m5.component("comp1").physics("spf").feature("out1").selection().geom("geom1", 2);
            m5.component("comp1").physics("spf").feature("out1").selection().set(outFace);
            m5.component("comp1").physics("spf").feature("out1").set("p0", "0");
            P("spf OK");

            // tds: deactivate the default Fluid 1 node and make two explicit ones
            m5.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            try {
                m5.component("comp1").physics("tds").feature("cdm1").active(false);
                P("cdm1.active(false) OK");
            } catch (Throwable t) { P("cdm1 active(false) FAILED :: " + t.getMessage()); }
            try {
                m5.component("comp1").physics("tds").create("fluidF", "Fluid", 3);
                m5.component("comp1").physics("tds").feature("fluidF").selection().geom("geom1", 3);
                m5.component("comp1").physics("tds").feature("fluidF").selection().set(fluidDom);
                m5.component("comp1").physics("tds").feature("fluidF").set("u_src", "root.comp1.u");
                m5.component("comp1").physics("tds").feature("fluidF").set("DiffusionCoefficientSource", "mat");
                P("fluidF OK");
            } catch (Throwable t) { P("fluidF FAILED :: " + t.getMessage()); }
            try {
                m5.component("comp1").physics("tds").create("fluidC", "Fluid", 3);
                m5.component("comp1").physics("tds").feature("fluidC").selection().geom("geom1", 3);
                m5.component("comp1").physics("tds").feature("fluidC").selection().set(cellDom);
                m5.component("comp1").physics("tds").feature("fluidC").set("u_src", "userdef");
                m5.component("comp1").physics("tds").feature("fluidC").set("u", new String[]{"0", "0", "0"});
                m5.component("comp1").physics("tds").feature("fluidC").set("DiffusionCoefficientSource", "mat");
                P("fluidC OK");
            } catch (Throwable t) { P("fluidC FAILED :: " + t.getMessage()); }
            try {
                m5.component("comp1").physics("tds").create("reac1", "Reactions", 3);
                m5.component("comp1").physics("tds").feature("reac1").selection().geom("geom1", 3);
                m5.component("comp1").physics("tds").feature("reac1").selection().set(cellDom);
                m5.component("comp1").physics("tds").feature("reac1").set("R_c", "-k_react*c");
                P("reac1 OK");
            } catch (Throwable t) { P("reac1 FAILED :: " + t.getMessage()); }
            try {
                m5.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
                m5.component("comp1").physics("tds").feature("inflow1").selection().geom("geom1", 2);
                m5.component("comp1").physics("tds").feature("inflow1").selection().set(inFace);
                m5.component("comp1").physics("tds").feature("inflow1").set("c0", "c_in");
                m5.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
                m5.component("comp1").physics("tds").feature("outflow1").selection().geom("geom1", 2);
                m5.component("comp1").physics("tds").feature("outflow1").selection().set(outFace);
                P("inflow/outflow OK");
            } catch (Throwable t) { P("inflow/outflow FAILED :: " + t.getMessage()); }
            try {
                m5.component("comp1").physics("tds").create("init2", "init", 3);
                m5.component("comp1").physics("tds").feature("init2").selection().geom("geom1", 3);
                m5.component("comp1").physics("tds").feature("init2").selection().set(chamDom);
                m5.component("comp1").physics("tds").feature("init2").set("initc", "c_in");
                P("init2 (2nd initial values on chamber) OK");
            } catch (Throwable t) { P("init2 FAILED :: " + t.getMessage()); }

            // pulse function
            m5.func().create("rect1", "Rectangle");
            m5.func("rect1").set("lower", "-5[s]");
            m5.func("rect1").set("upper", "2160[s]");
            m5.func("rect1").set("smooth", "5");
            m5.func("rect1").set("ncontder", "2");
            P("rect1 OK");

            // mesh: reuse the V1 strategy
            MeshSequence mesh = m5.component("comp1").mesh().create("mesh1", "geom1");
            mesh.create("ftet1", "FreeTet");
            mesh.feature("ftet1").selection().geom("geom1", 3);
            mesh.feature("ftet1").selection().set(fluidDom);
            mesh.feature("ftet1").create("size1", "Size");
            mesh.feature("ftet1").feature("size1").set("custom", true);
            mesh.feature("ftet1").feature("size1").set("hmaxactive", true);
            mesh.feature("ftet1").feature("size1").set("hmax", "3[mm]");
            mesh.feature("ftet1").feature("size1").set("hminactive", true);
            mesh.feature("ftet1").feature("size1").set("hmin", "0.3[mm]");
            mesh.feature("ftet1").feature("size1").set("hgradactive", true);
            mesh.feature("ftet1").feature("size1").set("hgrad", 1.25);
            long tm = System.currentTimeMillis();
            try { mesh.run(); stats(mesh, "V5mesh", tm); }
            catch (Throwable t) { P("V5 MESH FAILED :: " + t.getMessage()); }

            // study
            m5.study().create("std1");
            m5.study("std1").create("stat", "Stationary");
            m5.study("std1").feature("stat").set("physselection", new String[]{"spf"});
            m5.study("std1").create("time", "Transient");
            m5.study("std1").feature("time").set("tlist", "range(0,20,60)");
            m5.study("std1").feature("time").set("physselection", new String[]{"tds"});
            m5.study("std1").createAutoSequences("all");
            long ts = System.currentTimeMillis();
            try { m5.sol("sol1").runAll(); P("V5 SOLVE OK in " + (System.currentTimeMillis() - ts) / 1000 + " s"); }
            catch (Throwable t) { P("V5 SOLVE FAILED :: " + t.getMessage()); }

            // extraction: chamber-average time series
            try {
                m5.result().numerical().create("av1", "AvVolume");
                m5.result().numerical("av1").selection().geom("geom1", 3);
                m5.result().numerical("av1").selection().set(chamDom);
                m5.result().numerical("av1").set("expr", new String[]{"c"});
                double[][] r = m5.result().numerical("av1").getReal();
                P("AvVolume dims " + r.length + "x" + (r.length > 0 ? r[0].length : 0));
                if (r.length > 0) P("   row0 = " + Arrays.toString(r[0]));
            } catch (Throwable t) { P("AvVolume FAILED :: " + t.getMessage()); }

            // probe selection variants
            try {
                m5.probe().create("pdom", "Domain");
                m5.probe("pdom").set("edim", 3);
                m5.probe("pdom").set("expr", "c");
                m5.probe("pdom").set("method", "integration");
                m5.probe("pdom").set("intvolume", "on");
                try { m5.probe("pdom").selection().set(chamDom); P("Domain probe selection().set(int[]) OK"); }
                catch (Throwable t) { P("Domain probe set(int[]) FAILED :: " + t.getMessage()); }
            } catch (Throwable t) { P("Domain probe FAILED :: " + t.getMessage()); }
            try {
                m5.probe().create("ppoint", "Point");
                m5.probe("ppoint").set("edim", 3);
                m5.probe("ppoint").set("expr", "c");
                try {
                    m5.probe("ppoint").selection().geom("geom1", 0);
                    P("Point probe geom(...,0) accepted");
                } catch (Throwable t) { P("Point probe geom0 FAILED :: " + t.getMessage()); }
                try { m5.probe("ppoint").set("points", "1"); P("Point probe points=1 OK"); }
                catch (Throwable t) { P("Point probe points FAILED :: " + t.getMessage()); }
            } catch (Throwable t) { P("Point probe FAILED :: " + t.getMessage()); }

            // vertical profile in the cell layer via a vectorised CutPoint3D
            try {
                m5.result().dataset().create("cpt1", "CutPoint3D");
                m5.result().dataset("cpt1").set("pointx", "0");
                m5.result().dataset("cpt1").set("pointy", "0");
                m5.result().dataset("cpt1").set("pointz", "0");
                m5.result().dataset("cpt1").set("pointz_vector_method", "range");
                m5.result().dataset("cpt1").set("pointz_vector_start", "-0.1[mm]");
                m5.result().dataset("cpt1").set("pointz_vector_stop", "0");
                m5.result().dataset("cpt1").set("pointz_vector_numvalues", "11");
                m5.result().numerical().create("pev1", "EvalPoint");
                m5.result().numerical("pev1").set("data", "cpt1");
                m5.result().numerical("pev1").set("expr", new String[]{"c", "z"});
                double[][] r = m5.result().numerical("pev1").getReal();
                P("EvalPoint dims " + r.length + "x" + (r.length > 0 ? r[0].length : 0));
                for (int i = 0; i < r.length; i++) {
                    StringBuilder sb = new StringBuilder("   row" + i + ": ");
                    for (int j = 0; j < r[i].length; j++) sb.append(String.format("%.5g ", r[i][j]));
                    P(sb.toString());
                }
            } catch (Throwable t) { P("EvalPoint FAILED :: " + t.getMessage()); }
        }
    }
}
