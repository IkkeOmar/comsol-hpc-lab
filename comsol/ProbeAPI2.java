import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/**
 * Probe API2: last API details for Ex-4, all cheap (no mesh, no solve).
 *
 *  R1  LaminarFlow Inlet: is "Fully developed flow" available?  (It would
 *      give the exact flow rate AND a developed profile; a flat velocity
 *      inlet of U_avg gives the right flow rate but a flat profile.)
 *  R2  fpt GravityForce: does it take a fluid density (buoyancy)?  The
 *      analytical model uses the NET weight (rho_p - rho_m)*V*g, so if the
 *      node computes rho_p*V*g only, the equilibrium height would be wrong.
 *  R3  es Terminal property names.
 *  R4  fpt "Particle" numerical feature: what does it evaluate?
 *  R5  fpt Inlet on the inlet face: does it override the default Wall node's
 *      selection there?  (The Release Grid puts particles exactly ON the
 *      inlet face at x = 0, and the only legal wall condition under the
 *      "ignore inertial terms" formulation is Freeze/Stick/Disappear.)
 */
public class ProbeAPI2 {

    public static void main(String[] args) {
        try {
            Model m = ModelUtil.create("PA2");
            m.component().create("comp1");
            GeomSequence g = m.component("comp1").geom().create("geom1", 3);
            g.lengthUnit("um");
            g.create("blk1", "Block");
            g.feature("blk1").set("size", new String[]{"100", "100", "100"});
            g.run();

            // ---------------- R1: spf inlet --------------------------------
            System.out.println("=== R1: LaminarFlow Inlet ===");
            m.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            m.component("comp1").physics("spf").create("inl1", "Inlet", 2);
            PhysicsFeature inl = m.component("comp1").physics("spf").feature("inl1");
            System.out.println("  inl1 props=" + Arrays.toString(inl.properties()));
            for (String p : new String[]{"BoundaryCondition", "FullyDevelopedFlowType",
                                         "Uavfdf", "Qavfdf", "U0in", "LaminarInflow"}) {
                try {
                    String[] a = inl.getAllowedPropertyValues(p);
                    System.out.println("      ALLOWED " + p + " = " + Arrays.toString(a));
                } catch (Throwable t) {
                    System.out.println("      " + p + " : not enumerable");
                }
            }

            // ---------------- R2: fpt gravity ------------------------------
            System.out.println("\n=== R2: fpt GravityForce ===");
            m.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1");
            m.component("comp1").physics("fpt").prop("Formulation")
             .setIndex("Formulation", "NewtonianIgnoreInertialTerms", 0);
            m.component("comp1").physics("fpt").create("gf1", "GravityForce", 3);
            PhysicsFeature gf = m.component("comp1").physics("fpt").feature("gf1");
            System.out.println("  gf1 props=" + Arrays.toString(gf.properties()));
            for (String p : new String[]{"rho_mat", "rho", "g", "IncludeBuoyancy",
                                         "Buoyancy", "rho_fluid"}) {
                try {
                    String[] a = gf.getAllowedPropertyValues(p);
                    System.out.println("      ALLOWED " + p + " = " + Arrays.toString(a));
                } catch (Throwable t) {
                    System.out.println("      " + p + " : not enumerable");
                }
            }

            // ---------------- R3: es Terminal ------------------------------
            System.out.println("\n=== R3: es Terminal / Ground ===");
            m.component("comp1").physics().create("es", "Electrostatics", "geom1");
            m.component("comp1").physics("es").create("term1", "Terminal", 2);
            PhysicsFeature tm = m.component("comp1").physics("es").feature("term1");
            System.out.println("  term1 props=" + Arrays.toString(tm.properties()));
            for (String p : new String[]{"TerminalType", "V0", "TerminalName"}) {
                try {
                    String[] a = tm.getAllowedPropertyValues(p);
                    System.out.println("      ALLOWED " + p + " = " + Arrays.toString(a));
                } catch (Throwable t) {
                    System.out.println("      " + p + " : not enumerable");
                }
            }

            // ---------------- R5: fpt Inlet vs default Wall ---------------
            System.out.println("\n=== R5: fpt Inlet overrides Wall? ===");
            m.component("comp1").physics("fpt").create("inl1", "Inlet", 2);
            m.component("comp1").physics("fpt").feature("inl1").selection().set(1);
            m.component("comp1").physics("fpt").create("out1", "Outlet", 2);
            m.component("comp1").physics("fpt").feature("out1").selection().set(2);
            System.out.println("  fpt features now: "
                    + Arrays.toString(m.component("comp1").physics("fpt").feature().tags()));
            System.out.println("  wall1 allowed WallCondition: " + Arrays.toString(
                    m.component("comp1").physics("fpt").feature("wall1")
                     .getAllowedPropertyValues("WallCondition")));
            System.out.println("  inl1 allowed WallCondition: " + Arrays.toString(
                    m.component("comp1").physics("fpt").feature("inl1")
                     .getAllowedPropertyValues("WallCondition")));
            System.out.println("  out1 allowed WallCondition: " + Arrays.toString(
                    m.component("comp1").physics("fpt").feature("out1")
                     .getAllowedPropertyValues("WallCondition")));

            // ---------------- R6: ReleaseGrid global / dims ----------------
            System.out.println("\n=== R6: ReleaseGrid on a 3D geom ===");
            m.component("comp1").physics("fpt").create("relg1", "ReleaseGrid", -1);
            m.component("comp1").physics("fpt").feature("relg1")
             .setIndex("x0", "0[um]", 0);
            m.component("comp1").physics("fpt").feature("relg1")
             .setIndex("x0", "range(5[um],1[um],95[um])", 1);
            m.component("comp1").physics("fpt").feature("relg1")
             .setIndex("x0", "50[um]", 2);
            m.component("comp1").physics("fpt").feature("relg1").setIndex("rt", "0", 0);
            System.out.println("  x0 = " + Arrays.toString(
                    m.component("comp1").physics("fpt").feature("relg1").getStringArray("x0")));
            System.out.println("  GridType allowed: " + Arrays.toString(
                    m.component("comp1").physics("fpt").feature("relg1")
                     .getAllowedPropertyValues("GridType")));

            System.out.println("\nPROBEAPI2 DONE");
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("PROBEAPI2 ABORTED: " + t);
        }
    }
}
