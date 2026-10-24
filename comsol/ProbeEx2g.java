// ProbeEx2g.java - third API probe for Exercise 2 (COMSOL 6.4).
//
// Remaining unknowns:
//   1) Probe selection API: Domain probe (domains) and Point probe (geometric
//      point entities) + how to create a Table and read the probe output.
//   2) Numerical features on a real transient solution: AvVolume getReal(),
//      CutPoint3D + EvalPoint getReal(), and a 1-D cut line through a thin domain.
//   3) Sweep mesh: explicit sourceface / targetface property values, and a
//      Distribution on a face (number of layers through a 100 um domain).
//   4) TDS "Fluid" domain node on the cell domain: u_src=userdef + u vector,
//      DiffusionCoefficientSource=mat, and per-domain material selection.
//   5) Does a TDS "ThinLayer" node exist (would avoid the thin 3-D domain)?
//   6) model.save() to .mph, and Image export to an absolute path.
//   7) Swept cell layer adjacent to a free-tet fluid domain: does COMSOL
//      mesh them conformally (the real production risk)?
//
// NOTE: no anonymous inner classes / lambdas (comsolcompile emits only the
// top-level .class file, so the batch loader cannot resolve "ProbeEx2g$1").

import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

public class ProbeEx2g {

    static void P(String s) { System.out.println("PROBE " + s); }

    public static void main(String[] args) {
        try { run(); } catch (Throwable t) { t.printStackTrace(); }
        P("PROBE_EX2G DONE");
    }

    public static void run() throws Exception {
        Model model = ModelUtil.create("ProbeEx2g");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blk_ch", "Block");
        g.feature("blk_ch").set("size", new String[]{"20", "20", "2"});
        g.feature("blk_ch").set("pos",  new String[]{"-10", "-10", "0"});
        g.create("blk_chn", "Block");
        g.feature("blk_chn").set("size", new String[]{"30", "2", "2"});
        g.feature("blk_chn").set("pos",  new String[]{"-15", "-1", "0"});
        g.create("cyl_cell", "Cylinder");
        g.feature("cyl_cell").set("r", "6");
        g.feature("cyl_cell").set("h", "0.1");
        g.feature("cyl_cell").set("pos", new String[]{"0", "0", "-0.1"});
        // a geometric POINT at (0,0,-0.05) for the Point probe
        g.create("pt1", "Point");
        g.feature("pt1").set("p", new String[]{"0", "0", "-0.05"});
        g.run();
        P("nDomains = " + g.getNDomains() + "  nBoundaries = " + g.getNBoundaries());

        g.create("selCell", "BoxSelection");
        g.feature("selCell").set("entitydim", 3);
        g.feature("selCell").set("condition", "inside");
        g.feature("selCell").set("xmin", "-6.1"); g.feature("selCell").set("xmax", "6.1");
        g.feature("selCell").set("ymin", "-6.1"); g.feature("selCell").set("ymax", "6.1");
        g.feature("selCell").set("zmin", "-0.2"); g.feature("selCell").set("zmax", "-0.01");
        g.create("selFluid", "BoxSelection");
        g.feature("selFluid").set("entitydim", 3);
        g.feature("selFluid").set("condition", "inside");
        g.feature("selFluid").set("xmin", "-16"); g.feature("selFluid").set("xmax", "16");
        g.feature("selFluid").set("ymin", "-11"); g.feature("selFluid").set("ymax", "11");
        g.feature("selFluid").set("zmin", "-0.01"); g.feature("selFluid").set("zmax", "2.1");
        g.create("selCellTop", "BoxSelection");
        g.feature("selCellTop").set("entitydim", 2);
        g.feature("selCellTop").set("condition", "inside");
        g.feature("selCellTop").set("xmin", "-6.1"); g.feature("selCellTop").set("xmax", "6.1");
        g.feature("selCellTop").set("ymin", "-6.1"); g.feature("selCellTop").set("ymax", "6.1");
        g.feature("selCellTop").set("zmin", "-0.01"); g.feature("selCellTop").set("zmax", "0.01");
        g.create("selCellBot", "BoxSelection");
        g.feature("selCellBot").set("entitydim", 2);
        g.feature("selCellBot").set("condition", "inside");
        g.feature("selCellBot").set("xmin", "-6.1"); g.feature("selCellBot").set("xmax", "6.1");
        g.feature("selCellBot").set("ymin", "-6.1"); g.feature("selCellBot").set("ymax", "6.1");
        g.feature("selCellBot").set("zmin", "-0.11"); g.feature("selCellBot").set("zmax", "-0.09");
        g.create("selIn", "BoxSelection");
        g.feature("selIn").set("entitydim", 2);
        g.feature("selIn").set("condition", "allvertices");
        g.feature("selIn").set("xmin", "-15.1"); g.feature("selIn").set("xmax", "-14.9");
        g.feature("selIn").set("ymin", "-1.1");  g.feature("selIn").set("ymax", "1.1");
        g.feature("selIn").set("zmin", "-0.1");  g.feature("selIn").set("zmax", "2.1");
        g.create("selOut", "BoxSelection");
        g.feature("selOut").set("entitydim", 2);
        g.feature("selOut").set("condition", "allvertices");
        g.feature("selOut").set("xmin", "14.9");  g.feature("selOut").set("xmax", "15.1");
        g.feature("selOut").set("ymin", "-1.1");  g.feature("selOut").set("ymax", "1.1");
        g.feature("selOut").set("zmin", "-0.1");  g.feature("selOut").set("zmax", "2.1");
        g.run();

        int[] cellDom = model.selection("geom1_selCell").entities(3);
        int[] fluidDom = model.selection("geom1_selFluid").entities(3);
        int[] cellTop = model.selection("geom1_selCellTop").entities(2);
        int[] cellBot = model.selection("geom1_selCellBot").entities(2);
        int[] inFace = model.selection("geom1_selIn").entities(2);
        int[] outFace = model.selection("geom1_selOut").entities(2);
        P("cellDom=" + Arrays.toString(cellDom) + " fluidDom=" + Arrays.toString(fluidDom));
        P("cellTop=" + Arrays.toString(cellTop) + " cellBot=" + Arrays.toString(cellBot));
        P("in=" + Arrays.toString(inFace) + " out=" + Arrays.toString(outFace));
        int[] ptIds = model.selection("geom1_pt1").entities(0);
        P("point entities = " + Arrays.toString(ptIds));

        // ---------------------------------------------------------------- 3
        P("=== 3: SWEEP with explicit sourceface/targetface ===");
        MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");
        try {
            mesh.create("swe_cell", "Sweep");
            mesh.feature("swe_cell").selection().geom("geom1", 3);
            mesh.feature("swe_cell").selection().set(cellDom);
            mesh.feature("swe_cell").set("element", "prism");
            mesh.feature("swe_cell").set("sourceface", cellTop);
            mesh.feature("swe_cell").set("targetface", cellBot);
            P("sweep sourceface/targetface set OK");
        } catch (Throwable t) { P("sweep source/target FAILED :: " + t.getMessage()); }
        try {
            mesh.feature("swe_cell").create("dis1", "Distribution");
            mesh.feature("swe_cell").feature("dis1").set("type", "predefined");
            mesh.feature("swe_cell").feature("dis1").set("elemcount", "10");
            P("sweep Distribution elemcount=10 OK");
        } catch (Throwable t) { P("sweep Distribution FAILED :: " + t.getMessage()); }

        // ---------------------------------------------------------------- 7
        P("=== 7: FREE TET on fluid next to the swept cell layer ===");
        try {
            mesh.create("ftet1", "FreeTet");
            mesh.feature("ftet1").selection().geom("geom1", 3);
            mesh.feature("ftet1").selection().set(fluidDom);
            mesh.feature("ftet1").create("size1", "Size");
            mesh.feature("ftet1").feature("size1").set("custom", true);
            mesh.feature("ftet1").feature("size1").set("hmaxactive", true);
            mesh.feature("ftet1").feature("size1").set("hmax", "1[mm]");
            mesh.feature("ftet1").feature("size1").set("hminactive", true);
            mesh.feature("ftet1").feature("size1").set("hmin", "0.05[mm]");
            mesh.feature("ftet1").feature("size1").set("hgradactive", true);
            mesh.feature("ftet1").feature("size1").set("hgrad", 1.3);
            P("ftet1 size set OK");
        } catch (Throwable t) { P("ftet1 FAILED :: " + t.getMessage()); }
        try {
            mesh.run();
            P("MESH elements = " + mesh.stat().getNumElem());
            String[] types = {"prism", "hex", "tet", "tri"};
            for (int i = 0; i < types.length; i++) {
                try { P("   " + types[i] + " = " + mesh.stat().getNumElem(types[i])); }
                catch (Throwable t) { P("   " + types[i] + " : " + t.getMessage()); }
            }
        } catch (Throwable t) { P("mesh.run FAILED :: " + t.getMessage()); }

        // ---------------------------------------------------------------- 4
        P("=== 4: TDS FLUID NODE ON CELL DOMAIN ===");
        model.component("comp1").material().create("mat_f", "Common");
        model.component("comp1").material("mat_f").propertyGroup("def")
             .set("density", "1000[kg/m^3]")
             .set("dynamicviscosity", "1e-3[Pa*s]")
             .set("diffusioncoefficient", new String[]{"D_f"});
        model.component("comp1").material().create("mat_c", "Common");
        model.component("comp1").material("mat_c").propertyGroup("def")
             .set("diffusioncoefficient", new String[]{"D_c"});
        try {
            model.component("comp1").material("mat_c").selection().geom("geom1", 3);
            model.component("comp1").material("mat_c").selection().set(cellDom);
            P("material selection OK");
        } catch (Throwable t) { P("material selection FAILED :: " + t.getMessage()); }

        model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
        try {
            model.component("comp1").physics("tds").feature("cdm1").selection().geom("geom1", 3);
            model.component("comp1").physics("tds").feature("cdm1").selection().set(fluidDom);
            P("cdm1 restricted to fluid OK");
        } catch (Throwable t) { P("cdm1 selection FAILED :: " + t.getMessage()); }
        try {
            model.component("comp1").physics("tds").create("fluid2", "Fluid", 3);
            model.component("comp1").physics("tds").feature("fluid2").selection().geom("geom1", 3);
            model.component("comp1").physics("tds").feature("fluid2").selection().set(cellDom);
            model.component("comp1").physics("tds").feature("fluid2").set("u_src", "userdef");
            model.component("comp1").physics("tds").feature("fluid2").set("u", new String[]{"0", "0", "0"});
            model.component("comp1").physics("tds").feature("fluid2").set("DiffusionCoefficientSource", "mat");
            P("fluid2 (cell) set OK");
        } catch (Throwable t) { P("fluid2 FAILED :: " + t.getMessage()); }
        try {
            model.component("comp1").physics("tds").create("reac1", "Reactions", 3);
            model.component("comp1").physics("tds").feature("reac1").selection().geom("geom1", 3);
            model.component("comp1").physics("tds").feature("reac1").selection().set(cellDom);
            model.component("comp1").physics("tds").feature("reac1").set("R_c", "-k_react*c");
            model.component("comp1").physics("tds").feature("reac1").set("ReactingVolumeType", "TotalVolume");
            P("reac1 set OK");
        } catch (Throwable t) { P("reac1 FAILED :: " + t.getMessage()); }
        try {
            model.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
            model.component("comp1").physics("tds").feature("inflow1").selection().geom("geom1", 2);
            model.component("comp1").physics("tds").feature("inflow1").selection().set(inFace);
            model.component("comp1").physics("tds").feature("inflow1").set("c0", "c_in");
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
            model.component("comp1").physics("tds").feature("outflow1").selection().geom("geom1", 2);
            model.component("comp1").physics("tds").feature("outflow1").selection().set(outFace);
            P("inflow/outflow set OK");
        } catch (Throwable t) { P("inflow/outflow FAILED :: " + t.getMessage()); }

        // ---------------------------------------------------------------- 5
        P("=== 5: THINLAYER NODE? ===");
        String[] tl = {"ThinLayer", "ThinLayerDomain", "ThinPorousMedium", "ThinLayerWall"};
        for (int i = 0; i < tl.length; i++) {
            try {
                model.component("comp1").physics("tds").create("try_" + tl[i], tl[i], 2);
                P("   CREATE OK: " + tl[i]);
            } catch (Throwable t) { P("   CREATE FAIL: " + tl[i] + " : " + t.getMessage()); }
        }

        // ---------------------------------------------------------------- 1
        P("=== 1: PROBE SELECTIONS AND TABLE ===");
        try {
            model.probe().create("pdom", "Domain");
            model.probe("pdom").set("edim", 3);
            model.probe("pdom").set("expr", "c");
            model.probe("pdom").set("method", "integration");
            model.probe("pdom").set("intvolume", "on");
            model.probe("pdom").selection().geom("geom1");
            model.probe("pdom").selection().set(fluidDom);
            P("Domain probe + selection OK");
        } catch (Throwable t) { P("Domain probe FAILED :: " + t.getMessage()); }
        try {
            model.probe().create("ppoint", "Point");
            model.probe("ppoint").set("edim", 3);
            model.probe("ppoint").set("expr", "c");
            model.probe("ppoint").set("points", 1);
            model.probe("ppoint").selection().geom("geom1", 0);
            model.probe("ppoint").selection().set(ptIds);
            P("Point probe + geometric-point selection OK");
        } catch (Throwable t) { P("Point probe FAILED :: " + t.getMessage()); }
        try {
            model.result().table().create("tbl1", "Table");
            model.result().table("tbl1").label("ProbeData");
            model.probe("pdom").set("table", "tbl1");
            P("Table created and linked to probe OK");
        } catch (Throwable t) { P("Table FAILED :: " + t.getMessage()); }

        // ---------------------------------------------------------------- 2
        P("=== 2: NUMERICAL EVALUATION ON A SOLUTION ===");
        model.param().set("D_f", "2e-10[m^2/s]");
        model.param().set("D_c", "1.4e-10[m^2/s]");
        model.param().set("k_react", "0.15[1/s]");
        model.param().set("c_in", "1[mol/m^3]");

        model.study().create("std1");
        model.study("std1").create("time", "Transient");
        model.study("std1").feature("time").set("tlist", "range(0,10,100)");
        model.study("std1").createAutoSequences("all");
        try {
            model.sol("sol1").runAll();
            P("solve OK");
        } catch (Throwable t) { P("solve FAILED :: " + t.getMessage()); }

        try {
            model.result().numerical().create("av1", "AvVolume");
            model.result().numerical("av1").selection().geom("geom1", 3);
            model.result().numerical("av1").selection().set(fluidDom);
            model.result().numerical("av1").set("expr", new String[]{"c"});
            double[][] r = model.result().numerical("av1").getReal();
            P("AvVolume getReal dims = " + r.length + " x " + (r.length > 0 ? r[0].length : 0));
            for (int i = 0; i < r.length; i++) {
                StringBuilder sb = new StringBuilder("   row " + i + ": ");
                for (int j = 0; j < r[i].length; j++) sb.append(String.format("%.6g ", r[i][j]));
                P(sb.toString());
            }
        } catch (Throwable t) { P("AvVolume FAILED :: " + t.getMessage()); }

        try {
            model.result().dataset().create("cpt1", "CutPoint3D");
            model.result().dataset("cpt1").set("pointx", "0");
            model.result().dataset("cpt1").set("pointy", "0");
            model.result().dataset("cpt1").set("pointz", "-0.05");
            model.result().numerical().create("pev1", "EvalPoint");
            model.result().numerical("pev1").set("data", "cpt1");
            model.result().numerical("pev1").set("expr", new String[]{"c"});
            double[][] r = model.result().numerical("pev1").getReal();
            P("EvalPoint getReal dims = " + r.length + " x " + (r.length > 0 ? r[0].length : 0));
            for (int i = 0; i < r.length; i++) {
                StringBuilder sb = new StringBuilder("   row " + i + ": ");
                for (int j = 0; j < r[i].length; j++) sb.append(String.format("%.6g ", r[i][j]));
                P(sb.toString());
            }
        } catch (Throwable t) { P("CutPoint3D/EvalPoint FAILED :: " + t.getMessage()); }

        try {
            model.result().dataset().create("cln1", "CutLine3D");
            model.result().dataset("cln1").set("genpoints", "on");
            model.result().dataset("cln1").set("pointx", "0");
            model.result().dataset("cln1").set("pointy", "0");
            model.result().dataset("cln1").set("pointz", "-0.1");
            P("CutLine3D genpoints+pointx set OK");
        } catch (Throwable t) { P("CutLine3D FAILED :: " + t.getMessage()); }

        // ---------------------------------------------------------------- 6
        P("=== 6: SAVE AND EXPORT ===");
        try {
            model.save("<project>/comsol/probe_ex2g.mph");
            P("model.save OK");
        } catch (Throwable t) { P("model.save FAILED :: " + t.getMessage()); }
    }
}
