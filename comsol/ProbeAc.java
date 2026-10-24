import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

/** Decisive test: can Electrostatics (es) solve a Frequency Domain study step,
 *  or must the AC physics be ConductiveMedia (ec)?  Full stack traces.
 */
public class ProbeAc {
    static void buildAndSolve(Model m, String phys, String label) {
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
            m.component("comp1").physics(phys).create("term1", "Terminal", 2);
            m.component("comp1").physics(phys).feature("term1").selection().set(1);
            m.component("comp1").physics(phys).feature("term1").set("V0", "10[V]");
            m.component("comp1").physics(phys).create("gnd1", "Ground", 2);
            m.component("comp1").physics(phys).feature("gnd1").selection().set(2);

            m.study().create("std1");
            m.study("std1").create("freq", "Frequency");
            m.study("std1").feature("freq").set("plist", "10[MHz]");
            m.study("std1").createAutoSequences("all");
            String[] tags = m.sol().tags();
            System.out.println(label + " solver tags: " + Arrays.toString(tags));
            m.sol(tags[0]).runAll();
            System.out.println(label + " FREQUENCY SOLVE OK");

            // read a value back so we know the solution is real
            m.result().numerical().create("pev", "EvalPoint");
            m.result().numerical("pev").set("data", "dset1");
            m.result().numerical("pev").set("expr", new String[]{"abs(" + phys + ".Ex)"});
            m.result().numerical("pev").set("coord", new double[]{100, 50, 50});
            double[][] v = m.result().numerical("pev").getReal();
            System.out.println(label + " |Ex| at (100,50,50) = " + v[0][0]);
        } catch (Throwable e) {
            System.out.println(label + " FAILED");
            e.printStackTrace(System.out);
        }
    }

    public static void main(String[] args) {
        buildAndSolve(ModelUtil.create("Pac_es"), "es", "ES");
        buildAndSolve(ModelUtil.create("Pac_ec"), "ec", "EC");
        System.out.println("PROBEAC DONE");
    }
}
