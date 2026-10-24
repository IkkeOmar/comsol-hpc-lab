import com.comsol.model.*;
import com.comsol.model.util.*;

/** Probe: establish the EXACT API names for the Ex4 FFF model before writing it.
 *  Every try/catch prints the outcome so the real error text is captured.
 *  Run on the cluster:  comsol batch -inputfile Probe4.class -methodcall main
 */
public class Probe4 {
    static void t(String label, Runnable r) {
        try {
            r.run();
            System.out.println("PROBE OK   : " + label);
        } catch (Throwable e) {
            String m = e.getMessage();
            if (m == null) m = e.toString();
            System.out.println("PROBE FAIL : " + label + "  ==>  " + m);
        }
    }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe4");
            model.component().create("comp1");
            model.component("comp1").geom().create("geom1", 3);
            model.component("comp1").geom("geom1").lengthUnit("um");
            model.component("comp1").geom("geom1").create("blk1", "Block");
            model.component("comp1").geom("geom1").feature("blk1")
                 .set("size", new String[]{"200", "100", "100"});
            model.component("comp1").geom("geom1").run();

            System.out.println("PROBE domcount : " +
                model.component("comp1").geom("geom1").getNDomains());
            System.out.println("PROBE bndcount : " +
                model.component("comp1").geom("geom1").getNBoundaries());

            // ---- physics interfaces ----
            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            System.out.println("PROBE OK   : create LaminarFlow");

            t("create Electrostatics", () -> model.component("comp1").physics().create("es", "Electrostatics", "geom1"));
            t("create ElectricCurrents", () -> model.component("comp1").physics().create("cucn", "ElectricCurrents", "geom1"));
            t("create ConductiveMedia", () -> model.component("comp1").physics().create("ec", "ConductiveMedia", "geom1"));
            t("create FluidParticleTracing", () -> model.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1"));

            // ---- Electrostatics terminal/ground on a boundary ----
            t("es.create Terminal(bnd)", () -> {
                model.component("comp1").physics("es").create("term1", "Terminal", 2);
                model.component("comp1").physics("es").feature("term1").selection().set(1);
                model.component("comp1").physics("es").feature("term1").set("V0", "10[V]");
            });
            t("es.create Ground(bnd)", () -> {
                model.component("comp1").physics("es").create("gnd1", "Ground", 2);
                model.component("comp1").physics("es").feature("gnd1").selection().set(2);
            });
            t("es.create ElectricPotential(bnd) + V0", () -> {
                model.component("comp1").physics("es").create("pot1", "ElectricPotential", 2);
                model.component("comp1").physics("es").feature("pot1").selection().set(3);
                model.component("comp1").physics("es").feature("pot1").set("V0", "0[V]");
            });

            // ---- fpt nodes ----
            t("fpt prop Formulation tags", () -> {
                System.out.println("   fpt prop groups: " +
                    java.util.Arrays.toString(model.component("comp1").physics("fpt").prop().tags()));
            });
            t("fpt feature tags", () -> {
                System.out.println("   fpt features: " +
                    java.util.Arrays.toString(model.component("comp1").physics("fpt").feature().tags()));
            });
            t("fpt pp1 set rhop/dp", () -> {
                model.component("comp1").physics("fpt").feature("pp1").set("rhop_mat", "userdef");
                model.component("comp1").physics("fpt").feature("pp1").set("rhop", "1200[kg/m^3]");
                model.component("comp1").physics("fpt").feature("pp1").set("dp", "2.4[um]");
            });
            t("fpt create DragForce", () -> {
                model.component("comp1").physics("fpt").create("df1", "DragForce", 3);
                model.component("comp1").physics("fpt").feature("df1").selection().all();
            });
            t("fpt df1 u_src fromCommonDef", () ->
                model.component("comp1").physics("fpt").feature("df1").set("u_src", "fromCommonDef"));
            t("fpt create GravityForce", () -> {
                model.component("comp1").physics("fpt").create("gf1", "GravityForce", 3);
                model.component("comp1").physics("fpt").feature("gf1").selection().all();
            });
            t("fpt create DielectrophoreticForce", () -> {
                model.component("comp1").physics("fpt").create("deff1", "DielectrophoreticForce", 3);
                model.component("comp1").physics("fpt").feature("deff1").selection().all();
            });
            t("deff1 E_src root.comp1.es.Ex", () ->
                model.component("comp1").physics("fpt").feature("deff1").set("E_src", "root.comp1.es.Ex"));
            t("deff1 UsePPR true", () ->
                model.component("comp1").physics("fpt").feature("deff1").set("UsePPR", true));
            t("deff1 epsr/epsrm/sig props", () -> {
                model.component("comp1").physics("fpt").feature("deff1").set("epsr", "60");
                model.component("comp1").physics("fpt").feature("deff1").set("epsrm", "80");
                model.component("comp1").physics("fpt").feature("deff1").set("sigma", "0.01[S/m]");
                model.component("comp1").physics("fpt").feature("deff1").set("sigmam", "5.5e-6[S/m]");
            });

            // ---- release from grid ----
            t("fpt create ReleaseFromGrid (global, dim 0)", () -> {
                model.component("comp1").physics("fpt").create("rel1", "ReleaseFromGrid", 0);
            });
            t("rel1 GridType AllCombinations", () ->
                model.component("comp1").physics("fpt").feature("rel1").set("GridType", "AllCombinations"));
            t("rel1 qx0 array", () ->
                model.component("comp1").physics("fpt").feature("rel1").set("qx0", new String[]{"0"}));
            t("rel1 qy0 array", () ->
                model.component("comp1").physics("fpt").feature("rel1").set("qy0", new String[]{"5", "50"}));
            t("rel1 qz0 array", () ->
                model.component("comp1").physics("fpt").feature("rel1").set("qz0", new String[]{"50"}));
            t("rel1 releaseTime t0", () ->
                model.component("comp1").physics("fpt").feature("rel1").set("trelease", "0[s]"));

            // ---- study steps ----
            model.study().create("std1");
            t("std1 Stationary", () -> model.study("std1").create("stat", "Stationary"));
            t("std1 Frequency", () -> model.study("std1").create("freq", "Frequency"));
            t("std1 Transient", () -> model.study("std1").create("td", "Transient"));
            t("std1 Transient tlist", () ->
                model.study("std1").feature("td").set("tlist", "range(0,1,120)"));

            System.out.println("PROBE DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE ABORTED: " + e);
        }
    }
}
