import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/**
 * Probe API: settle the remaining API questions for Ex-4 in ONE run.
 *
 *  Q1  Which numerical feature types can export PARTICLE data (positions vs
 *      time)?  The deliverable needs the trajectory as numbers.
 *  Q2  fpt "Inlet" node properties: the assignment releases the particles at
 *      x = 0, which IS the inlet face.  The default fpt "wall1" covers every
 *      boundary, and with WallCondition = "Freeze" a particle sitting exactly
 *      on the inlet face would be frozen at t = 0.  Need an fpt Inlet on that
 *      face to override the wall (with a release time far in the future so it
 *      releases nothing).
 *  Q3  Geometry selection features (DifferenceSelection etc.) so the
 *      x-running floor edges can be separated from the y-running ones.
 *  Q4  Electrostatics: replace the default "Free Space 1" (which assumes
 *      vacuum and ignores eps_r = 80) with a ChargeConservation node.
 */
public class ProbeAPI {

    static void props(String what, PhysicsFeature f) {
        System.out.println("  " + what + " type=" + f.getType()
                + " props=" + Arrays.toString(f.properties()));
    }

    public static void main(String[] args) {
        try {
            Model m = ModelUtil.create("PA");
            m.component().create("comp1");
            GeomSequence g = m.component("comp1").geom().create("geom1", 3);
            g.lengthUnit("um");
            g.create("blk1", "Block");
            g.feature("blk1").set("size", new String[]{"100", "100", "100"});
            g.run();

            // ---------------- Q3: selection features -----------------------
            System.out.println("=== Q3: geometry selection features ===");
            for (String t : new String[]{"DifferenceSelection", "UnionSelection",
                    "IntersectionSelection", "ComplementSelection", "ExplicitSelection",
                    "AdjacentSelection"}) {
                try {
                    g.create("sel_" + t, t);
                    System.out.println("  CREATE OK: " + t + "  props="
                            + Arrays.toString(g.feature("sel_" + t).properties()));
                } catch (Throwable e) {
                    System.out.println("  CREATE FAIL: " + t + " : " + e.getMessage());
                }
            }

            // ---------------- Q2: fpt Inlet --------------------------------
            System.out.println("\n=== Q2: fpt Inlet node ===");
            m.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1");
            try {
                m.component("comp1").physics("fpt").create("inl1", "Inlet", 2);
                props("fpt.inl1", m.component("comp1").physics("fpt").feature("inl1"));
                for (String p : new String[]{"InitialVelocity", "VelocitySpecification",
                        "ParticleReleaseSpecification", "WallCondition"}) {
                    try {
                        System.out.println("      ALLOWED " + p + " = " + Arrays.toString(
                                m.component("comp1").physics("fpt").feature("inl1")
                                 .getAllowedPropertyValues(p)));
                    } catch (Throwable t) { System.out.println("      " + p + " : n/a"); }
                }
            } catch (Throwable e) {
                System.out.println("  fpt Inlet FAIL: " + e.getMessage());
            }
            // what does the default wall1 select?
            System.out.println("  fpt.wall1 props: " + Arrays.toString(
                    m.component("comp1").physics("fpt").feature("wall1").properties()));
            System.out.println("  fpt.wall1 allowed WallCondition: " + Arrays.toString(
                    m.component("comp1").physics("fpt").feature("wall1")
                     .getAllowedPropertyValues("WallCondition")));

            // ---------------- Q1: numerical feature types -----------------
            System.out.println("\n=== Q1: numerical feature types for particle data ===");
            for (String t : new String[]{"Particle", "ParticleEval", "ParticleCount",
                                         "EvalGlobal", "Global", "EvalPoint",
                                         "AvSurface", "IntSurface", "MaxSurface",
                                         "ParticleEvalPoint", "ParticleStatistics"}) {
                try {
                    m.result().numerical().create("n_" + t, t);
                    System.out.println("  CREATE OK: " + t);
                    try {
                        System.out.println("      props=" + Arrays.toString(
                                m.result().numerical("n_" + t).properties()));
                    } catch (Throwable e) { System.out.println("      (no props)"); }
                } catch (Throwable e) {
                    System.out.println("  CREATE FAIL: " + t + " : " + e.getMessage());
                }
            }

            // ---------------- Q4: es material node ------------------------
            System.out.println("\n=== Q4: Electrostatics without Free Space ===");
            m.component("comp1").material().create("mat1", "Common");
            m.component("comp1").material("mat1").propertyGroup("def")
                .set("relpermittivity", "80")
                .set("electricconductivity", "5.5[us/m]");
            m.component("comp1").physics().create("es", "Electrostatics", "geom1");
            try {
                m.component("comp1").physics("es").feature().remove("fsp1");
                System.out.println("  removed fsp1; es features now: "
                        + Arrays.toString(m.component("comp1").physics("es").feature().tags()));
                m.component("comp1").physics("es").create("cc1", "ChargeConservation", 3);
                m.component("comp1").physics("es").feature("cc1").selection().all();
                m.component("comp1").physics("es").feature("cc1").set("epsilonr_mat", "from_mat");
                m.component("comp1").physics("es").feature("cc1").set("materialType", "from_mat");
                System.out.println("  ChargeConservation cc1 created with eps_r from material");
            } catch (Throwable e) {
                System.out.println("  es node surgery FAILED: " + e.getMessage());
            }
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
            System.out.println("  ES FREQUENCY SOLVE OK (no vacuum warning expected above)");
            m.result().numerical().create("gev", "EvalGlobal");
            m.result().numerical("gev").setIndex("expr", "es.V0_1", 0);
            try {
                m.result().numerical("gev").run();
                System.out.println("  es.V0_1 = " + m.result().numerical("gev").getReal()[0][0]);
            } catch (Throwable e) {
                System.out.println("  eval failed: " + e.getMessage());
            }

            System.out.println("\nPROBEAPI DONE");
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("PROBEAPI ABORTED: " + t);
        }
    }
}
