/* Ex2M2.java -- decisive API + MESH probe for the courselab-chip Ex2.
 *
 *  Real geometry (mm):
 *    chamber  : block 60 x 60 x 1   at (-30,-30, 0)   z in [0,1]
 *    duct W   : block 45 x 2 x 1    at (-75,-1,  0)   touches chamber at x=-30
 *    duct E   : block 45 x 2 x 1    at ( 30,-1,  0)   touches chamber at x=+30
 *    cellLyr  : cylinder r=25, h=0.1 at (0,0,-0.1)     z in [-0.1,0]  (only for E/F)
 *
 *  Mesh plan under test:
 *    FreeTri on all z=0 faces  ->  Sweep (chamber+ducts) upward  ->  Sweep (cell) downward
 *
 *  NOTE: no inner classes / lambdas (comsolcompile does not emit them).
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2M2 {

    static void h(String s) {
        System.out.println();
        System.out.println("################ " + s + " ################");
    }

    static void stats(MeshSequence m, String label) {
        try {
            System.out.println("  [" + label + "] numElem=" + m.stat().getNumElem()
                + " types=" + Arrays.toString(m.stat().getTypes())
                + " minQ=" + m.stat().getMinQuality()
                + " meanQ=" + m.stat().getMeanQuality());
        } catch (Throwable e) {
            System.out.println("  [" + label + "] STATS FAIL: " + e.getMessage());
        }
    }



    public static void main(String[] args) {
        try {
            meshRecipe();
            probeApi();
            physApi();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println();
        System.out.println("EX2M2 DONE");
    }

    // =====================================================================
    static void meshRecipe() {
        h("A: MESH RECIPE ON REAL GEOMETRY (with cell layer)");

        Model model = ModelUtil.create("Ex2M2");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blkCh", "Block");
        g.feature("blkCh").set("size", new String[]{"60", "60", "1"});
        g.feature("blkCh").set("pos", new String[]{"-30", "-30", "0"});
        g.create("blkW", "Block");
        g.feature("blkW").set("size", new String[]{"45", "2", "1"});
        g.feature("blkW").set("pos", new String[]{"-75", "-1", "0"});
        g.create("blkE", "Block");
        g.feature("blkE").set("size", new String[]{"45", "2", "1"});
        g.feature("blkE").set("pos", new String[]{"30", "-1", "0"});
        g.create("cylCell", "Cylinder");
        g.feature("cylCell").set("r", "25");
        g.feature("cylCell").set("h", "0.1");
        g.feature("cylCell").set("pos", new String[]{"0", "0", "-0.1"});
        g.run();
        System.out.println("  domains=" + g.getNDomains() + " boundaries=" + g.getNBoundaries()
            + " edges=" + g.getNEdges() + " verts=" + g.getNVertices());

        // ---- named selections -------------------------------------------
        g.create("selIn", "BoxSelection");
        g.feature("selIn").set("entitydim", 2);
        g.feature("selIn").set("condition", "allvertices");
        g.feature("selIn").set("xmin", "-75.1"); g.feature("selIn").set("xmax", "-74.9");
        g.feature("selIn").set("ymin", "-1.1");  g.feature("selIn").set("ymax", "1.1");
        g.feature("selIn").set("zmin", "-0.2");  g.feature("selIn").set("zmax", "1.2");
        g.create("selOut", "BoxSelection");
        g.feature("selOut").set("entitydim", 2);
        g.feature("selOut").set("condition", "allvertices");
        g.feature("selOut").set("xmin", "74.9"); g.feature("selOut").set("xmax", "75.1");
        g.feature("selOut").set("ymin", "-1.1");  g.feature("selOut").set("ymax", "1.1");
        g.feature("selOut").set("zmin", "-0.2");  g.feature("selOut").set("zmax", "1.2");
        g.create("selCham", "BoxSelection");
        g.feature("selCham").set("entitydim", 3);
        g.feature("selCham").set("condition", "inside");
        g.feature("selCham").set("xmin", "-30.1"); g.feature("selCham").set("xmax", "30.1");
        g.feature("selCham").set("ymin", "-30.1"); g.feature("selCham").set("ymax", "30.1");
        g.feature("selCham").set("zmin", "-0.05"); g.feature("selCham").set("zmax", "1.2");
        g.create("selDuct", "BoxSelection");
        g.feature("selDuct").set("entitydim", 3);
        g.feature("selDuct").set("condition", "inside");
        g.feature("selDuct").set("xmin", "-75.1"); g.feature("selDuct").set("xmax", "75.1");
        g.feature("selDuct").set("ymin", "-1.1");  g.feature("selDuct").set("ymax", "1.1");
        g.feature("selDuct").set("zmin", "-0.2");  g.feature("selDuct").set("zmax", "1.2");
        g.create("selDuctW", "BoxSelection");
        g.feature("selDuctW").set("entitydim", 3);
        g.feature("selDuctW").set("condition", "inside");
        g.feature("selDuctW").set("xmin", "-75.1"); g.feature("selDuctW").set("xmax", "-30");
        g.feature("selDuctW").set("ymin", "-1.1");  g.feature("selDuctW").set("ymax", "1.1");
        g.feature("selDuctW").set("zmin", "-0.2");  g.feature("selDuctW").set("zmax", "1.2");
        g.create("selCell", "BoxSelection");
        g.feature("selCell").set("entitydim", 3);
        g.feature("selCell").set("condition", "center");
        g.feature("selCell").set("xmin", "-25.1"); g.feature("selCell").set("xmax", "25.1");
        g.feature("selCell").set("ymin", "-25.1"); g.feature("selCell").set("ymax", "25.1");
        g.feature("selCell").set("zmin", "-0.15"); g.feature("selCell").set("zmax", "0.05");
        g.create("selZ0", "BoxSelection");
        g.feature("selZ0").set("entitydim", 2);
        g.feature("selZ0").set("condition", "allvertices");
        g.feature("selZ0").set("xmin", "-75.1"); g.feature("selZ0").set("xmax", "75.1");
        g.feature("selZ0").set("ymin", "-30.1"); g.feature("selZ0").set("ymax", "30.1");
        g.feature("selZ0").set("zmin", "-0.005"); g.feature("selZ0").set("zmax", "0.005");
        g.create("selZ1", "BoxSelection");
        g.feature("selZ1").set("entitydim", 2);
        g.feature("selZ1").set("condition", "allvertices");
        g.feature("selZ1").set("xmin", "-75.1"); g.feature("selZ1").set("xmax", "75.1");
        g.feature("selZ1").set("ymin", "-30.1"); g.feature("selZ1").set("ymax", "30.1");
        g.feature("selZ1").set("zmin", "0.995"); g.feature("selZ1").set("zmax", "1.005");
        g.create("selCellBot", "BoxSelection");
        g.feature("selCellBot").set("entitydim", 2);
        g.feature("selCellBot").set("condition", "allvertices");
        g.feature("selCellBot").set("xmin", "-25.1"); g.feature("selCellBot").set("xmax", "25.1");
        g.feature("selCellBot").set("ymin", "-25.1"); g.feature("selCellBot").set("ymax", "25.1");
        g.feature("selCellBot").set("zmin", "-0.105"); g.feature("selCellBot").set("zmax", "-0.095");
        g.run();

        int[] inF = model.selection("geom1_selIn").entities(2);
        int[] outF = model.selection("geom1_selOut").entities(2);
        int[] chamD = model.selection("geom1_selCham").entities(3);
        int[] ductD = model.selection("geom1_selDuct").entities(3);
        int[] ductWD = model.selection("geom1_selDuctW").entities(3);
        int[] cellD = model.selection("geom1_selCell").entities(3);
        int[] z0F = model.selection("geom1_selZ0").entities(2);
        int[] z1F = model.selection("geom1_selZ1").entities(2);
        int[] cellBotF = model.selection("geom1_selCellBot").entities(2);
        System.out.println("  inlet=" + Arrays.toString(inF) + " outlet=" + Arrays.toString(outF));
        System.out.println("  chamberDom=" + Arrays.toString(chamD) + " ductDom=" + Arrays.toString(ductD)
            + " ductW=" + Arrays.toString(ductWD) + " cellDom=" + Arrays.toString(cellD));
        System.out.println("  z0Faces=" + Arrays.toString(z0F) + " z1Faces=" + Arrays.toString(z1F)
            + " cellBotFaces=" + Arrays.toString(cellBotF));

        // ================================================================
        // RECIPE 1: FreeTri on z=0 faces + Sweep(chamber+ducts) up + Sweep(cell) down
        // ================================================================
        h("B: RECIPE 1  FreeTri(z=0) + Sweep up + Sweep down");
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.3");
            m.create("sizeD", "Size");
            m.feature("sizeD").selection().geom(2).set(ductBotFaces(model, ductD, z0F));
            m.feature("sizeD").set("hmax", "0.15");
            m.feature("sizeD").set("hmin", "0.05");
            m.feature("sizeD").set("hgrad", "1.3");
            m.create("ftri1", "FreeTri");
            m.feature("ftri1").selection().geom(2).set(z0F);
            m.create("swe1", "Sweep");
            m.feature("swe1").selection().geom(3).set(concat(chamD, ductD));
            System.out.println("    Sweep node props = " + Arrays.toString(m.feature("swe1").properties()));
            m.create("swe2", "Sweep");
            m.feature("swe2").selection().geom(3).set(cellD);
            m.run();
            stats(m, "R1");
        } catch (Throwable e) {
            System.out.println("  R1 FAILED: " + e.getMessage());
            e.printStackTrace();
        }

        // ================================================================
        // RECIPE 2: same but Sweep nodes with explicit source/destination faces
        // ================================================================
        h("C: RECIPE 2  explicit sourceface/destface on Sweep");
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh2", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "2.5");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.3");
            m.create("ftri1", "FreeTri");
            m.feature("ftri1").selection().geom(2).set(z0F);
            m.create("swe1", "Sweep");
            m.feature("swe1").selection().geom(3).set(concat(chamD, ductD));
            m.feature("swe1").set("srcface", "userdef");
            m.feature("swe1").selection("srcface").geom(2).set(z0F);
            m.feature("swe1").set("dstface", "userdef");
            m.feature("swe1").selection("dstface").geom(2).set(z1F);
            m.run();
            stats(m, "R2");
        } catch (Throwable e) {
            System.out.println("  R2 FAILED: " + e.getMessage());
        }
    }

    /** bottom faces belonging to the duct domains */
    static int[] ductBotFaces(Model model, int[] ductD, int[] z0F) {
        // crude: all z=0 faces that are NOT in the chamber footprint
        int[] keep = new int[z0F.length];
        int n = 0;
        for (int i = 0; i < z0F.length; i++) {
            keep[n++] = z0F[i];
        }
        return keep;
    }

    static int[] concat(int[] a, int[] b) {
        int[] r = new int[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    // =====================================================================
    static void probeApi() {
        h("D: PROBE API (selection + domain point + expression)");
        Model model = ModelUtil.create("Ex2M2p");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blkCh", "Block");
        g.feature("blkCh").set("size", new String[]{"60", "60", "1"});
        g.feature("blkCh").set("pos", new String[]{"-30", "-30", "0"});
        g.create("blkW", "Block");
        g.feature("blkW").set("size", new String[]{"45", "2", "1"});
        g.feature("blkW").set("pos", new String[]{"-75", "-1", "0"});
        g.run();
        g.create("selCham", "BoxSelection");
        g.feature("selCham").set("entitydim", 3);
        g.feature("selCham").set("condition", "inside");
        g.feature("selCham").set("xmin", "-30.1"); g.feature("selCham").set("xmax", "30.1");
        g.feature("selCham").set("ymin", "-30.1"); g.feature("selCham").set("ymax", "30.1");
        g.feature("selCham").set("zmin", "-0.05"); g.feature("selCham").set("zmax", "1.2");
        g.run();
        int[] chamD = model.selection("geom1_selCham").entities(3);
        System.out.println("  chamberDom=" + Arrays.toString(chamD));

        // ---- Domain probe -------------------------------------------
        model.probe().create("domP", "Domain");
        model.probe("domP").set("expr", "c");
        model.probe("domP").set("type", "average");
        try { model.probe("domP").set("edim", 3); System.out.println("  domP.edim=3 OK"); }
        catch (Throwable e) { System.out.println("  domP.edim FAIL: " + e.getMessage()); }
        try {
            model.probe("domP").selection().geom("geom1", 3).set(chamD);
            System.out.println("  domP.selection().geom(geom1,3).set OK -> "
                + Arrays.toString(model.probe("domP").selection().entities()));
        } catch (Throwable e) { System.out.println("  domP.selection geom FAIL: " + e.getMessage()); }
        try {
            model.probe("domP").selection().named("geom1_selCham");
            System.out.println("  domP.selection().named() OK");
        } catch (Throwable e) { System.out.println("  domP.selection named FAIL: " + e.getMessage()); }
        try {
            model.probe("domP").set("table", "tblCham");
            System.out.println("  domP.table OK");
        } catch (Throwable e) { System.out.println("  domP.table FAIL: " + e.getMessage()); }

        // ---- DomainPoint (global point probe) ------------------------
        try {
            model.probe().create("dp1", "DomainPoint");
            model.probe("dp1").set("coords", new String[]{"0.0295", "0", "0.0005"});
            System.out.println("  dp1.coords OK -> " + Arrays.deepToString(model.probe("dp1").getStringArray("coords")));
            System.out.println("  dp1 props = " + Arrays.toString(model.probe("dp1").properties()));
            model.probe("dp1").create("pe1", "PointExpr");
            model.probe("dp1").feature("pe1").set("expr", "c");
            System.out.println("  dp1/PointExpr expr=c OK");
            System.out.println("  dp1 feature tags = " + Arrays.toString(model.probe("dp1").feature().tags()));
        } catch (Throwable e) {
            System.out.println("  DomainPoint FAIL: " + e.getMessage());
            e.printStackTrace();
        }
        // ---- Point probe with points ----------------------------------
        try {
            model.probe().create("ptP", "Point");
            model.probe("ptP").set("expr", "c");
            model.probe("ptP").set("points", new String[]{"0.0295", "0", "0.0005"});
            System.out.println("  ptP.points OK");
        } catch (Throwable e) {
            System.out.println("  ptP.points FAIL: " + e.getMessage());
        }
    }

    // =====================================================================
    static void physApi() {
        h("E: PHYSICS API (fluid2 / D_c_mat / notstudystep)");
        Model model = ModelUtil.create("Ex2M2t");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blkCh", "Block");
        g.feature("blkCh").set("size", new String[]{"60", "60", "1"});
        g.feature("blkCh").set("pos", new String[]{"-30", "-30", "0"});
        g.run();
        model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
        model.component("comp1").physics("tds").feature("cdm1").set("D_c", "D_sp");
        // allowed values for the user-defined diffusion switch
        for (String pr : new String[]{"D_c_mat", "DiffusionCoefficientSource", "u_src", "uc_src"}) {
            try {
                System.out.println("  cdm1." + pr + " allowed = "
                    + Arrays.toString(model.component("comp1").physics("tds").feature("cdm1").getAllowedPropertyValues(pr)));
            } catch (Throwable e) { System.out.println("  cdm1." + pr + " : " + e.getMessage()); }
        }
        // Fluid domain condition with user-defined velocity + D
        try {
            model.component("comp1").physics("tds").create("fluid2", "Fluid", 3);
            System.out.println("  fluid2 created");
        } catch (Throwable e) { System.out.println("  fluid2 create FAIL: " + e.getMessage()); }
        try {
            model.component("comp1").physics("tds").feature("fluid2").set("u_src", "userdef");
            System.out.println("  fluid2.u_src=userdef OK");
        } catch (Throwable e) { System.out.println("  fluid2.u_src FAIL: " + e.getMessage()); }
        try {
            model.component("comp1").physics("tds").feature("fluid2").set("u", new String[]{"0", "0", "0"});
            System.out.println("  fluid2.u=0 OK");
        } catch (Throwable e) { System.out.println("  fluid2.u FAIL: " + e.getMessage()); }
        for (String pr : new String[]{"D_c_mat", "DiffusionCoefficientSource"}) {
            try {
                System.out.println("  fluid2." + pr + " allowed = "
                    + Arrays.toString(model.component("comp1").physics("tds").feature("fluid2").getAllowedPropertyValues(pr)));
            } catch (Throwable e) { System.out.println("  fluid2." + pr + " : " + e.getMessage()); }
        }
        try {
            model.component("comp1").physics("tds").feature("fluid2").set("D_c_mat", "userdef");
            model.component("comp1").physics("tds").feature("fluid2").set("D_c", "0.7*D_sp");
            System.out.println("  fluid2 D_c_mat=userdef + D_c=0.7*D_sp OK");
        } catch (Throwable e) { System.out.println("  fluid2 D_c_mat FAIL: " + e.getMessage()); }
        // Reactions
        try {
            model.component("comp1").physics("tds").create("reac1", "Reactions", 3);
            model.component("comp1").physics("tds").feature("reac1").set("R_c", "-k_r*c");
            System.out.println("  reac1 R_c OK");
        } catch (Throwable e) { System.out.println("  reac1 FAIL: " + e.getMessage()); }

        // study coupling
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").create("stat2", "Stationary");
        for (String pr : new String[]{"notstudystep", "notsolmethod", "notstudy", "notsolnum"}) {
            try {
                System.out.println("  stat." + pr + " allowed = "
                    + Arrays.toString(model.study("std1").feature("stat").getAllowedPropertyValues(pr)));
            } catch (Throwable e) { System.out.println("  stat." + pr + " : " + e.getMessage()); }
        }
        try {
            model.study("std1").feature("stat2").set("notsolmethod", "sol");
            model.study("std1").feature("stat2").set("notstudy", "std1");
            model.study("std1").feature("stat2").set("notsolnum", "auto");
            model.study("std1").feature("stat2").set("notstudystep", "stat");
            System.out.println("  stat2 coupling OK");
        } catch (Throwable e) { System.out.println("  stat2 coupling FAIL: " + e.getMessage()); }
        try {
            model.study("std1").feature("stat2").setSolveFor("/physics/spf", false);
            System.out.println("  stat2 setSolveFor(spf,false) OK");
        } catch (Throwable e) { System.out.println("  setSolveFor FAIL: " + e.getMessage()); }
    }
}
