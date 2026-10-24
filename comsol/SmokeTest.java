import com.comsol.model.*;
import com.comsol.model.util.*;

/** Minimal cluster smoke test: exercises the license checkout AND the three
 *  physics interfaces this assignment needs (LaminarFlow, Electrostatics,
 *  FluidParticleTracing). If this runs, the cluster can do Exercise 4. */
public class SmokeTest {
    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("SmokeTest");
            model.component().create("comp1");
            model.component("comp1").geom().create("geom1", 3);
            model.component("comp1").geom("geom1").lengthUnit("um");
            model.component("comp1").geom("geom1").create("blk1", "Block");
            model.component("comp1").geom("geom1").feature("blk1")
                 .set("size", new String[]{"200", "100", "100"});
            model.component("comp1").geom("geom1").run();

            // 1) LaminarFlow  (core COMSOL)
            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            System.out.println("SMOKE: LaminarFlow created OK");

            // 2) Electrostatics (AC/DC module)
            try {
                model.component("comp1").physics().create("es", "Electrostatics", "geom1");
                System.out.println("SMOKE: Electrostatics created OK (AC/DC module available)");
            } catch (Exception e) {
                System.out.println("SMOKE: Electrostatics FAILED: " + e.getMessage());
            }

            // 3) FluidParticleTracing (Particle Tracing module)
            try {
                model.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1");
                System.out.println("SMOKE: FluidParticleTracing created OK (Particle Tracing module available)");
            } catch (Exception e) {
                System.out.println("SMOKE: FluidParticleTracing FAILED: " + e.getMessage());
            }

            model.save("<project>/comsol/SmokeTest.mph");
            System.out.println("SMOKE: DONE");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("SMOKE: FAILED " + e.getMessage());
        }
    }
}
