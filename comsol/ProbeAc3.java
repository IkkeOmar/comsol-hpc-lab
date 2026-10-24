import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** Probe 13 (DECISIVE): es (Electrostatics) vs ec (ConductiveMedia) in a
 *  Frequency Domain step, WITH a valid mesh.
 *  Earlier failures were harness artifacts: no mesh at all, or a mesh
 *  sequence created without the geometry argument (Unknown_property).
 */
public class ProbeAc3 {
    static void run(Model m, String phys, String label, boolean addCC) {
        try {
            m.component().create("comp1");
            m.component("comp1").geom().create("geom1", 3);
            m.component("comp1").geom("geom1").lengthUnit("um");
            m.component("comp1").geom("geom1").create("blk1", "Block");
            m.component("comp1").geom("geom1").feature("blk1")
                .set("size", new String[]{"200", "100", "100"});
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

            m.component("comp1").physics().create(phys, phys.equals("es") ? "Electrostatics"
                                                                        : "ConductiveMedia", "geom1");
            String[] tags = m.component("comp1").physics(phys).feature().tags();
            System.out.println(label + " default nodes: " + Arrays.toString(tags));
            for (String t : tags) {
                PhysicsFeature f = m.component("comp1").physics(phys).feature(t);
                System.out.println("   " + t + " type=" + f.getType()
                        + " props=" + Arrays.toString(f.properties()));
            }
            if (addCC) {
                try {
                    m.component("comp1").physics(phys).create("cc1", "ChargeConservation", 3);
                    m.component("comp1").physics(phys).feature("cc1").selection().all();
                    System.out.println(label + " ChargeConservation added");
                } catch (Throwable e) {
                    System.out.println(label + " ChargeConservation FAIL: " + e.getMessage());
                }
            }

            m.component("comp1").physics(phys).create("pot1", "ElectricPotential", 2);
            m.component("comp1").physics(phys).feature("pot1").selection().set(1);
            m.component("comp1").physics(phys).feature("pot1").set("V0", "10[V]");
            m.component("comp1").physics(phys).create("gnd1", "Ground", 2);
            m.component("comp1").physics(phys).feature("gnd1").selection().set(2);

            // mesh WITH the geometry argument; element-size properties live on
            // the Size feature, NOT on the FreeTet mesher feature.
            m.component("comp1").mesh().create("mesh1", "geom1");
            m.component("comp1").mesh("mesh1").create("size1", "Size");
            m.component("comp1").mesh("mesh1").feature("size1").set("hmax", "10[um]");
            m.component("comp1").mesh("mesh1").feature("size1").set("hmin", "1[um]");
            m.component("comp1").mesh("mesh1").create("ftet1", "FreeTet");
            m.component("comp1").mesh("mesh1").run();
            System.out.println(label + " mesh elements: "
                    + m.component("comp1").mesh("mesh1").getNumElem());

            m.study().create("std1");
            m.study("std1").create("freq", "Frequency");
            m.study("std1").feature("freq").set("plist", "10[MHz]");
            m.study("std1").createAutoSequences("all");
            String[] st = m.sol().tags();
            m.sol(st[0]).runAll();
            System.out.println(label + " SOLVE OK");

            m.result().numerical().create("pev", "EvalPoint");
            m.result().numerical("pev").set("data", "dset1");
            m.result().numerical("pev").set("expr", new String[]{"abs(" + phys + ".Ex)"});
            m.result().numerical("pev").set("coord", new double[]{100, 50, 50});
            double[][] v = m.result().numerical("pev").getReal();
            System.out.println(label + " |Ex|(100,50,50) = " + v[0][0]);
        } catch (Throwable e) {
            System.out.println(label + " FAILED: " + e.getMessage());
            Throwable c = e.getCause();
            while (c != null) { System.out.println(label + "   cause: " + c.getMessage()); c = c.getCause(); }
        }
    }

    public static void main(String[] args) {
        run(ModelUtil.create("P13ec"), "ec", "EC", false);
        run(ModelUtil.create("P13es"), "es", "ES-default", false);
        run(ModelUtil.create("P13es2"), "es", "ES+ChargeConservation", true);
        System.out.println("PROBEAC3 DONE");
    }
}
