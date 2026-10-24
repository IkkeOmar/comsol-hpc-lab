import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

/** Probe 11: minimal, fast checks.
 *  (a) Does Electrostatics (es) solve a Frequency Domain study? (with material)
 *  (b) ConductiveMedia (ec) feature names: Terminal / ElectricPotential / Ground.
 *  (c) ReleaseGrid property names + ability to set a 3-axis coordinate grid.
 */
public class Probe4k {
    public static void main(String[] args) {
        // ---------- (c) ReleaseGrid ----------
        try {
            Model m = ModelUtil.create("PRel");
            m.component().create("comp1");
            m.component("comp1").geom().create("geom1", 3);
            m.component("comp1").geom("geom1").lengthUnit("um");
            m.component("comp1").geom("geom1").create("blk1", "Block");
            m.component("comp1").geom("geom1").feature("blk1")
                .set("size", new String[]{"1000", "100", "100"});
            m.component("comp1").geom("geom1").run();
            m.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1");
            m.component("comp1").physics("fpt").create("relg1", "ReleaseGrid", -1);
            System.out.println("RG props: " + Arrays.toString(
                m.component("comp1").physics("fpt").feature("relg1").properties()));
            // set the grid: x fixed 0, y 5..95, z fixed 50
            m.component("comp1").physics("fpt").feature("relg1").setIndex("x0", "0", 0);
            m.component("comp1").physics("fpt").feature("relg1").setIndex("x0", "range(5,1,95)", 1);
            m.component("comp1").physics("fpt").feature("relg1").setIndex("x0", "50", 2);
            m.component("comp1").physics("fpt").feature("relg1").setIndex("rt", "0", 0);
            System.out.println("RG SET OK");
            System.out.println("RG x0 now: " + Arrays.toString(
                m.component("comp1").physics("fpt").feature("relg1").getStringArray("x0")));
        } catch (Throwable e) {
            System.out.println("RG FAIL: " + e);
        }

        // ---------- (b) ec feature names ----------
        try {
            Model m = ModelUtil.create("PEc");
            m.component().create("comp1");
            m.component("comp1").geom().create("geom1", 3);
            m.component("comp1").geom("geom1").lengthUnit("um");
            m.component("comp1").geom("geom1").create("blk1", "Block");
            m.component("comp1").geom("geom1").feature("blk1")
                .set("size", new String[]{"100", "100", "100"});
            m.component("comp1").geom("geom1").run();
            m.component("comp1").physics().create("ec", "ConductiveMedia", "geom1");
            for (String f : new String[]{"Terminal", "ElectricPotential", "Ground",
                                        "ElectricPotential"}) {
                try {
                    m.component("comp1").physics("ec").create("x" + f, f, 2);
                    System.out.println("EC OK: " + f);
                } catch (Throwable e) {
                    System.out.println("EC FAIL: " + f + " : " + e.getMessage());
                }
            }
        } catch (Throwable e) {
            System.out.println("EC PROBE FAIL: " + e);
        }

        // ---------- (a) es in frequency domain ----------
        try {
            Model m = ModelUtil.create("PEs");
            m.component().create("comp1");
            m.component("comp1").geom().create("geom1", 3);
            m.component("comp1").geom("geom1").lengthUnit("um");
            m.component("comp1").geom("geom1").create("blk1", "Block");
            m.component("comp1").geom("geom1").feature("blk1")
                .set("size", new String[]{"100", "100", "100"});
            m.component("comp1").geom("geom1").run();
            m.component("comp1").material().create("mat1", "Common");
            m.component("comp1").material("mat1").propertyGroup("def")
                .set("relpermittivity", new String[]{"80"});
            m.component("comp1").material("mat1").propertyGroup("def")
                .set("electricconductivity", new String[]{"5.5e-6[S/m]"});
            m.component("comp1").material("mat1").propertyGroup("def")
                .set("density", new String[]{"1000[kg/m^3]"});
            m.component("comp1").material("mat1").propertyGroup("def")
                .set("dynamicviscosity", new String[]{"1e-3[Pa*s]"});
            m.component("comp1").physics().create("es", "Electrostatics", "geom1");
            m.component("comp1").physics("es").create("term1", "Terminal", 2);
            m.component("comp1").physics("es").feature("term1").selection().set(1);
            m.component("comp1").physics("es").feature("term1").set("V0", "10[V]");
            m.component("comp1").physics("es").create("gnd1", "Ground", 2);
            m.component("comp1").physics("es").feature("gnd1").selection().set(2);
            m.study().create("std1");
            m.study("std1").create("freq", "Frequency");
            m.study("std1").feature("freq").set("plist", "10[MHz]");
            m.study("std1").createAutoSequences("all");
            m.sol("sol1").runAll();
            System.out.println("ES FREQ SOLVE OK");
        } catch (Throwable e) {
            System.out.println("ES FREQ SOLVE FAIL: " + e);
        }

        System.out.println("PROBE11 DONE");
    }
}
