import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/**
 * Probe M3: (a) Electrostatics material handling, (b) graded mesh candidates.
 *
 * (a) The Electrostatics interface's default domain node is "Free Space 1"
 *     (fsp1), which ASSUMES VACUUM and therefore ignores the material's
 *     relative permittivity (measured: probe4k warned exactly that).  The
 *     medium must be eps_r = 80, sigma = 5.5 uS/m.  Probe the available
 *     node types and the fsp1 property values.
 *
 * (b) Mesh candidates on the real 45-electrode geometry.  Measured already:
 *     a hand-built sequence needs an explicit FreeTet or it meshes 0 elements.
 */
public class ProbeMesh3 {

    static void show(Model m, String phys, String tag) {
        PhysicsFeature f = m.component("comp1").physics(phys).feature(tag);
        System.out.println("  " + phys + "." + tag + " type=" + f.getType()
                + " props=" + Arrays.toString(f.properties()));
        for (String p : new String[]{"materialType", "ConstitutiveRelationDE",
                "epsilonr_mat", "sigma_mat", "epsilonr", "sigma", "useMat"}) {
            try {
                String[] a = f.getAllowedPropertyValues(p);
                if (a != null) System.out.println("      ALLOWED " + p + " = " + Arrays.toString(a));
            } catch (Throwable t) { /* not an enum */ }
        }
    }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("PM3");
            model.component().create("comp1");
            GeomSequence g = model.component("comp1").geom().create("geom1", 3);
            g.lengthUnit("um");
            g.create("blk1", "Block");
            g.feature("blk1").set("size", new String[]{"1000", "100", "100"});
            g.run();

            model.component("comp1").material().create("mat1", "Common");
            model.component("comp1").material("mat1").propertyGroup("def")
                .set("relpermittivity", "80")
                .set("electricconductivity", "5.5[us/m]");

            // ---------------- (a) es material node --------------------------
            System.out.println("=== A: Electrostatics domain node ===");
            model.component("comp1").physics().create("es", "Electrostatics", "geom1");
            show(model, "es", "fsp1");
            for (String t : new String[]{"ChargeConservation", "FreeSpace",
                    "DielectricModel", "ElectrodeSurface"}) {
                try {
                    model.component("comp1").physics("es").create("try_" + t, t, 3);
                    System.out.println("  CREATE OK: " + t);
                    show(model, "es", "try_" + t);
                } catch (Throwable e) {
                    System.out.println("  CREATE FAIL: " + t + " : " + e.getMessage());
                }
            }

            System.out.println("\n=== A2: ConductiveMedia (ec) for comparison ===");
            model.component("comp1").physics().create("ec", "ConductiveMedia", "geom1");
            for (String t : model.component("comp1").physics("ec").feature().tags()) {
                show(model, "ec", t);
            }

            System.out.println("\nPROBEMESH3-A DONE");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
