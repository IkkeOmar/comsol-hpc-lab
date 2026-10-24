import com.comsol.model.*;
import com.comsol.model.util.*;
import java.util.Arrays;

/** Probe 12: which (physics, BC-type) combination actually solves a Frequency
 *  Domain step for the DEP problem?
 *    A) es + ElectricPotential + Ground
 *    B) ec + ElectricPotential + Ground
 *    C) ec + Terminal (TerminalType=Voltage) + Ground
 *  Each with the real water material and a frequency-domain study.
 */
public class ProbeAc2 {
    static void buildAndSolve(Model m, String phys, String bcType, String label) {
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
            if (bcType.equals("ElectricPotential")) {
                m.component("comp1").physics(phys).create("pot1", "ElectricPotential", 2);
                m.component("comp1").physics(phys).feature("pot1").selection().set(1);
                m.component("comp1").physics(phys).feature("pot1").set("V0", "10[V]");
            } else {
                m.component("comp1").physics(phys).create("term1", "Terminal", 2);
                m.component("comp1").physics(phys).feature("term1").selection().set(1);
                m.component("comp1").physics(phys).feature("term1").set("TerminalType", "Voltage");
                m.component("comp1").physics(phys).feature("term1").set("V0", "10[V]");
            }
            m.component("comp1").physics(phys).create("gnd1", "Ground", 2);
            m.component("comp1").physics(phys).feature("gnd1").selection().set(2);

            // an explicit mesh is required - without it the solver has nothing
            // to discretise and reports "failed to find a solution"
            m.component("comp1").mesh().create("mesh1");
            m.component("comp1").mesh("mesh1").create("ftet1", "FreeTet");
            m.component("comp1").mesh("mesh1").feature("ftet1").set("hmax", "10[um]");
            m.component("comp1").mesh("mesh1").run();
            System.out.println(label + " mesh elements: "
                    + m.component("comp1").mesh("mesh1").getNumElem());

            m.study().create("std1");
            m.study("std1").create("freq", "Frequency");
            m.study("std1").feature("freq").set("plist", "10[MHz]");
            m.study("std1").createAutoSequences("all");
            String[] tags = m.sol().tags();
            m.sol(tags[0]).runAll();
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
        buildAndSolve(ModelUtil.create("Pa_es_pot"), "es", "ElectricPotential", "A(es+ElectricPotential)");
        buildAndSolve(ModelUtil.create("Pa_ec_pot"), "ec", "ElectricPotential", "B(ec+ElectricPotential)");
        buildAndSolve(ModelUtil.create("Pa_ec_term"), "ec", "Terminal", "C(ec+Terminal)");
        System.out.println("PROBEAC2 DONE");
    }
}
