import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** ProbeEx2c — remaining unknowns for Exercise 2:
 *   A) 3D mesh feature op names (FreeTri / Swept / Distribution / Size) and a working recipe
 *   B) Domain probe: how to give it a domain selection (component-scoped?)
 *   C) TDS "Fluid" domain condition: user-defined D and zero velocity
 *   D) study-step coupling ("values of variables not solved for")
 *   E) Parametric sweep node ordering
 *   F) AvVolume over a domain selection, and probe table access
 */
public class ProbeEx2c {

    static void h(String s) {
        System.out.println();
        System.out.println("################ " + s + " ################");
    }

    static void tryMesh(String label, Runnable r) {
        try { r.run(); System.out.println("  [" + label + "] OK"); }
        catch (Throwable e) { System.out.println("  [" + label + "] FAILED: " + e.getMessage()); }
    }

    public static void main(String[] args) {
        try {
            meshApi();
            probeSelection();
            fluidDomain();
            studyCoupling();
            numericApi();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println();
        System.out.println("PROBE_EX2C DONE");
    }

    static Model chamberModel(String tag) {
        Model model = ModelUtil.create(tag);
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blkCh", "Block");
        g.feature("blkCh").set("size", new String[]{"60", "60", "1"});
        g.feature("blkCh").set("pos", new String[]{"-30", "-30", "0"});
        g.create("blkDw", "Block");
        g.feature("blkDw").set("size", new String[]{"45", "2", "1"});
        g.feature("blkDw").set("pos", new String[]{"-75", "-1", "0"});
        g.create("blkDe", "Block");
        g.feature("blkDe").set("size", new String[]{"45", "2", "1"});
        g.feature("blkDe").set("pos", new String[]{"30", "-1", "0"});
        g.run();
        return model;
    }

    // ------------------------------------------------------------------ A
    static void meshApi() {
        h("A: 3D MESH API");
        Model model = chamberModel("ProbeEx2cA");
        final MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
        tryMesh("create Size", new Runnable() { public void run() { m.create("size1", "Size"); }});
        tryMesh("create FreeTet", new Runnable() { public void run() { m.create("ftet1", "FreeTet"); }});
        tryMesh("create FreeTri", new Runnable() { public void run() { m.create("ftri1", "FreeTri"); }});
        tryMesh("create Swept", new Runnable() { public void run() { m.create("swe1", "Swept"); }});
        tryMesh("create Map", new Runnable() { public void run() { m.create("map1", "Map"); }});
        tryMesh("create BoundaryLayer", new Runnable() { public void run() { m.create("bl1", "BoundaryLayer"); }});
        tryMesh("Swept properties", new Runnable() { public void run() {
            System.out.println("     Swept props = " + Arrays.toString(m.feature("swe1").properties()));
            for (String pr : new String[]{"sourceface", "destface", "smoothtransition", "srcface", "dstface"}) {
                try { System.out.println("     swe1." + pr + " allowed = "
                    + Arrays.toString(m.feature("swe1").getAllowedPropertyValues(pr))); }
                catch (Throwable e) { /* not present */ }
            }
        }});
        tryMesh("Distribution under Swept", new Runnable() { public void run() {
            m.feature("swe1").create("dis1", "Distribution");
            System.out.println("     Distribution props = " + Arrays.toString(m.feature("swe1").feature("dis1").properties()));
        }});
        // FreeTri selection on faces
        tryMesh("FreeTri face selection + run", new Runnable() { public void run() {
            m.feature("ftri1").selection().geom(2).set(new int[]{3, 8, 16});
            System.out.println("     ftri1 selection OK");
        }});
        tryMesh("Size face selection", new Runnable() { public void run() {
            m.feature("size1").selection().geom(2).set(new int[]{9});
            System.out.println("     size1 face selection OK");
        }});
    }

    // ------------------------------------------------------------------ B
    static void probeSelection() {
        h("B: DOMAIN PROBE SELECTION");
        Model model = chamberModel("ProbeEx2cB");
        model.probe().create("domP", "Domain");
        model.probe("domP").set("expr", "c");
        model.probe("domP").set("type", "average");
        tryMesh("model.probe().model(comp1)", new Runnable() { public void run() {
            model.probe("domP").model("comp1");
            System.out.println("     model set OK");
        }});
        tryMesh("model.probe selection", new Runnable() { public void run() {
            model.probe("domP").selection().set(new int[]{2});
            System.out.println("     entities = " + Arrays.toString(model.probe("domP").selection().entities()));
        }});
        tryMesh("component-scoped probe selection", new Runnable() { public void run() {
            model.component("comp1").probe("domP").selection().set(new int[]{2});
            System.out.println("     entities = " + Arrays.toString(model.component("comp1").probe("domP").selection().entities()));
        }});
    }

    // ------------------------------------------------------------------ C
    static void fluidDomain() {
        h("C: TDS FLUID DOMAIN CONDITION (userdef D, zero velocity)");
        Model model = chamberModel("ProbeEx2cC");
        model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
        model.component("comp1").physics("tds").create("fluid2", "Fluid", 3);
        for (String pr : new String[]{"D_c_mat", "u_src", "DiffusionCoefficientSource"}) {
            tryMesh("fluid2." + pr + " enum", new Runnable() { public void run() {
                System.out.println("     fluid2." + pr + " allowed = "
                    + Arrays.toString(model.component("comp1").physics("tds").feature("fluid2").getAllowedPropertyValues(pr)));
            }});
        }
        tryMesh("fluid2 D_c_mat=userdef + D_c", new Runnable() { public void run() {
            model.component("comp1").physics("tds").feature("fluid2").set("D_c_mat", "userdef");
            model.component("comp1").physics("tds").feature("fluid2").set("D_c", "0.7*200[um^2/s]");
            System.out.println("     D_c_mat/D_c set OK");
        }});
        tryMesh("fluid2 u_src=userdef + u=0", new Runnable() { public void run() {
            model.component("comp1").physics("tds").feature("fluid2").set("u_src", "userdef");
            model.component("comp1").physics("tds").feature("fluid2").set("u", new String[]{"0", "0", "0"});
            System.out.println("     u_src/u set OK");
        }});
        // default Fluid 1: couple velocity to spf + set D
        tryMesh("cdm1 D_c_mat=userdef + D_c", new Runnable() { public void run() {
            model.component("comp1").physics("tds").feature("cdm1").set("D_c_mat", "userdef");
            model.component("comp1").physics("tds").feature("cdm1").set("D_c", "200[um^2/s]");
            model.component("comp1").physics("tds").feature("cdm1").set("u_src", "root.comp1.u");
            System.out.println("     cdm1 D_c_mat/D_c/u_src set OK");
        }});
    }

    // ------------------------------------------------------------------ D
    static void studyCoupling() {
        h("D: STUDY COUPLING");
        Model model = chamberModel("ProbeEx2cD");
        model.study().create("std1");
        model.study("std1").create("param", "Parametric");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").create("td", "Transient");
        System.out.println("  study step order = " + Arrays.toString(model.study("std1").feature().tags()));
        for (String pr : new String[]{"notstudystep", "notsoluse", "notsolvertype", "nottimeinterp", "initstudystep"}) {
            try {
                System.out.println("  stat." + pr + " allowed = "
                    + Arrays.toString(model.study("std1").feature("stat").getAllowedPropertyValues(pr)));
            } catch (Throwable e) { /* absent */ }
        }
        tryMesh("td notsolmethod=sol", new Runnable() { public void run() {
            model.study("std1").feature("td").set("notsolmethod", "sol");
            model.study("std1").feature("td").set("notstudy", "std1");
            model.study("std1").feature("td").set("notsolnum", "auto");
            model.study("std1").feature("td").set("usesol", "on");
            System.out.println("     td coupling set OK");
        }});
        tryMesh("td setSolveFor spf=false", new Runnable() { public void run() {
            model.study("std1").feature("td").setSolveFor("/physics/spf", false);
            System.out.println("     setSolveFor OK");
        }});
        tryMesh("param sweep fields", new Runnable() { public void run() {
            model.param().set("Q", "100[ul/min]");
            model.study("std1").feature("param").set("pname", new String[]{"Q"});
            model.study("std1").feature("param").set("plistarr", new String[]{"10[ul/min] 100[ul/min] 1[ml/min]"});
            model.study("std1").feature("param").set("sweeptype", "sparse");
            System.out.println("     param sweep set OK");
        }});
        tryMesh("createAutoSequences", new Runnable() { public void run() {
            model.study("std1").createAutoSequences("all");
            System.out.println("     autoSequences OK; sol tags = " + Arrays.toString(model.sol().tags()));
        }});
    }

    // ------------------------------------------------------------------ E
    static void numericApi() {
        h("E: NUMERICAL API");
        Model model = chamberModel("ProbeEx2cE");
        tryMesh("AvVolume on domain selection", new Runnable() { public void run() {
            model.result().numerical().create("av1", "AvVolume");
            model.result().numerical("av1").selection().set(new int[]{2});
            model.result().numerical("av1").set("expr", new String[]{"1"});
            System.out.println("     AvVolume selection+expr OK");
        }});
        tryMesh("IntVolume on domain selection", new Runnable() { public void run() {
            model.result().numerical().create("iv1", "IntVolume");
            model.result().numerical("iv1").selection().set(new int[]{2});
            model.result().numerical("iv1").set("expr", new String[]{"1"});
            System.out.println("     IntVolume selection+expr OK");
        }});
        tryMesh("MaxVolume on domain selection", new Runnable() { public void run() {
            model.result().numerical().create("mx1", "MaxVolume");
            model.result().numerical("mx1").selection().set(new int[]{2});
            model.result().numerical("mx1").set("expr", new String[]{"1"});
            System.out.println("     MaxVolume selection+expr OK");
        }});
        tryMesh("EvalGlobal", new Runnable() { public void run() {
            model.result().numerical().create("ev1", "EvalGlobal");
            model.result().numerical("ev1").set("expr", new String[]{"1"});
            System.out.println("     EvalGlobal OK");
        }});
        tryMesh("Table export feature names", new Runnable() { public void run() {
            System.out.println("     export tags = " + Arrays.toString(model.result().export().tags()));
        }});
    }
}
