import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** Probe 5: enumerate the EXACT property names and allowed values of the
 *  fpt (FluidParticleTracing) nodes we need for Exercise 4.
 *
 *  Probe4b failed to compile because it used the unqualified type
 *  "PhysicsFeature"; the real type is com.comsol.model.physics.PhysicsFeature
 *  (skill pitfall #72).  This probe therefore enumerates instead of guessing:
 *  for every node we will touch, print properties() and the allowed values of
 *  every property we care about.
 */
public class Probe5 {

    static void dumpProps(String label, PhysicsFeature f) {
        try {
            String[] p = f.properties();
            System.out.println("PROPS " + label + " (" + p.length + "): " + Arrays.toString(p));
        } catch (Throwable e) {
            System.out.println("PROPS FAIL " + label + " : " + e.getMessage());
        }
    }

    static void dumpAllowed(String label, PhysicsFeature f, String prop) {
        try {
            String[] v = f.getAllowedPropertyValues(prop);
            System.out.println("ALLOWED " + label + "." + prop + " : " + Arrays.toString(v));
        } catch (Throwable e) {
            System.out.println("ALLOWED FAIL " + label + "." + prop + " : " + e.getMessage());
        }
    }

    static void trySet(String label, PhysicsFeature f, String prop, Object val) {
        try {
            if (val instanceof String) f.set(prop, (String) val);
            else if (val instanceof String[]) f.set(prop, (String[]) val);
            else if (val instanceof Boolean) f.set(prop, ((Boolean) val).booleanValue());
            else if (val instanceof Integer) f.set(prop, ((Integer) val).intValue());
            System.out.println("SET OK " + label + "." + prop + " = " + val);
        } catch (Throwable e) {
            System.out.println("SET FAIL " + label + "." + prop + " = " + val + " : " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe5");
            model.component().create("comp1");
            model.component("comp1").geom().create("geom1", 3);
            model.component("comp1").geom("geom1").lengthUnit("um");
            model.component("comp1").geom("geom1").create("blk1", "Block");
            model.component("comp1").geom("geom1").feature("blk1")
                 .set("size", new String[]{"200", "100", "100"});
            model.component("comp1").geom("geom1").run();

            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            model.component("comp1").physics().create("es", "Electrostatics", "geom1");
            model.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1");

            // ---- formulation ------------------------------------------------
            Physics fpt = model.component("comp1").physics("fpt");
            System.out.println("=== fpt default features ===");
            System.out.println(Arrays.toString(fpt.feature().tags()));
            for (String tag : fpt.feature().tags()) {
                dumpProps("fpt." + tag, fpt.feature(tag));
            }

            // allowed formulations
            try {
                model.component("comp1").physics("fpt").prop("Formulation")
                     .setIndex("Formulation", "NewtonianIgnoreInertialTerms", 0);
                System.out.println("SET OK fpt.Formulation = NewtonianIgnoreInertialTerms");
            } catch (Throwable e) {
                System.out.println("SET FAIL fpt.Formulation : " + e.getMessage());
            }

            // ---- wall -------------------------------------------------------
            dumpProps("fpt.wall1", fpt.feature("wall1"));
            dumpAllowed("fpt.wall1", fpt.feature("wall1"), "WallCondition");

            // ---- gravity force ---------------------------------------------
            try {
                fpt.create("gf1", "GravityForce", 3);
                fpt.feature("gf1").selection().all();
                System.out.println("CREATE OK gf1");
            } catch (Throwable e) {
                System.out.println("CREATE FAIL gf1 : " + e.getMessage());
            }
            if (Arrays.asList(fpt.feature().tags()).contains("gf1")) {
                dumpProps("fpt.gf1", fpt.feature("gf1"));
                for (String cand : new String[]{"g", "rho", "rhof", "rho_f", "rho_fluid",
                                               "rho_surrounding", "rho_m", "Rho",
                                               "IncludeBuoyancy", "Buoyancy"}) {
                    dumpAllowed("fpt.gf1", fpt.feature("gf1"), cand);
                }
                trySet("fpt.gf1", fpt.feature("gf1"), "g", new String[]{"0", "0", "-9.80665[m/s^2]"});
            }

            // ---- drag force -------------------------------------------------
            try {
                fpt.create("df1", "DragForce", 3);
                fpt.feature("df1").selection().all();
                System.out.println("CREATE OK df1");
            } catch (Throwable e) {
                System.out.println("CREATE FAIL df1 : " + e.getMessage());
            }
            if (Arrays.asList(fpt.feature().tags()).contains("df1")) {
                dumpProps("fpt.df1", fpt.feature("df1"));
                dumpAllowed("fpt.df1", fpt.feature("df1"), "DragLaw");
                dumpAllowed("fpt.df1", fpt.feature("df1"), "u_src");
            }

            // ---- DEP force ---------------------------------------------------
            try {
                fpt.create("deff1", "DielectrophoreticForce", 3);
                fpt.feature("deff1").selection().all();
                System.out.println("CREATE OK deff1");
            } catch (Throwable e) {
                System.out.println("CREATE FAIL deff1 : " + e.getMessage());
            }
            if (Arrays.asList(fpt.feature().tags()).contains("deff1")) {
                dumpProps("fpt.deff1", fpt.feature("deff1"));
                for (String cand : new String[]{"E_src", "UsePPR", "AngularFrequency",
                                               "ParticlesToAffect", "epsr", "erp",
                                               "sigma", "sigmap"}) {
                    dumpAllowed("fpt.deff1", fpt.feature("deff1"), cand);
                }
            }

            // ---- particle properties ----------------------------------------
            dumpProps("fpt.pp1", fpt.feature("pp1"));
            for (String cand : new String[]{"rhop_mat", "erp_mat", "sigmap_mat"}) {
                dumpAllowed("fpt.pp1", fpt.feature("pp1"), cand);
            }
            trySet("fpt.pp1", fpt.feature("pp1"), "rhop_mat", "userdef");
            trySet("fpt.pp1", fpt.feature("pp1"), "rhop", "1200[kg/m^3]");
            trySet("fpt.pp1", fpt.feature("pp1"), "dp", "2.4[um]");
            trySet("fpt.pp1", fpt.feature("pp1"), "erp_mat", "userdef");
            trySet("fpt.pp1", fpt.feature("pp1"), "erp", "60");
            trySet("fpt.pp1", fpt.feature("pp1"), "sigmap_mat", "userdef");
            trySet("fpt.pp1", fpt.feature("pp1"), "sigmap", "0.01[S/m]");

            // ---- RELEASE FROM GRID (the key unknown) -------------------------
            try {
                fpt.create("relg1", "ReleaseGrid", -1);
                System.out.println("CREATE OK relg1 (dim -1)");
            } catch (Throwable e) {
                System.out.println("CREATE FAIL relg1 : " + e.getMessage());
            }
            if (Arrays.asList(fpt.feature().tags()).contains("relg1")) {
                dumpProps("fpt.relg1", fpt.feature("relg1"));
                for (String cand : new String[]{"x0", "y0", "z0", "qx0", "qy0", "qz0",
                                               "GridType", "gridtype", "rt", "trelease",
                                               "InitialVelocity", "VelocitySpecification",
                                               "v0", "InitialPosition", "NumberOfParticles",
                                               "ParticleReleaseSpecification"}) {
                    dumpAllowed("fpt.relg1", fpt.feature("relg1"), cand);
                }
                // try the three coordinate dimensions
                trySet("fpt.relg1", fpt.feature("relg1"), "x0", new String[]{"0[um]"});
                try {
                    fpt.feature("relg1").setIndex("x0", "0[um]", 0);
                    System.out.println("SET OK relg1.x0[0] = 0[um]");
                } catch (Throwable e) {
                    System.out.println("SET FAIL relg1.x0[0] : " + e.getMessage());
                }
                try {
                    fpt.feature("relg1").setIndex("x0", "range(5[um],1[um],95[um])", 1);
                    System.out.println("SET OK relg1.x0[1] = range(5,1,95)um");
                } catch (Throwable e) {
                    System.out.println("SET FAIL relg1.x0[1] : " + e.getMessage());
                }
                try {
                    fpt.feature("relg1").setIndex("x0", "50[um]", 2);
                    System.out.println("SET OK relg1.x0[2] = 50[um]");
                } catch (Throwable e) {
                    System.out.println("SET FAIL relg1.x0[2] : " + e.getMessage());
                }
                try {
                    fpt.feature("relg1").setIndex("rt", "0", 0);
                    System.out.println("SET OK relg1.rt[0] = 0");
                } catch (Throwable e) {
                    System.out.println("SET FAIL relg1.rt[0] : " + e.getMessage());
                }
                trySet("fpt.relg1", fpt.feature("relg1"), "InitialVelocity", "ConstantSpeedLambertian");
                trySet("fpt.relg1", fpt.feature("relg1"), "v0", new String[]{"0", "0", "0"});
            }

            // ---- outlet ------------------------------------------------------
            try {
                fpt.create("outfpt1", "Outlet", 2);
                fpt.feature("outfpt1").selection().set(1);
                System.out.println("CREATE OK outfpt1");
            } catch (Throwable e) {
                System.out.println("CREATE FAIL outfpt1 : " + e.getMessage());
            }
            if (Arrays.asList(fpt.feature().tags()).contains("outfpt1")) {
                dumpProps("fpt.outfpt1", fpt.feature("outfpt1"));
                dumpAllowed("fpt.outfpt1", fpt.feature("outfpt1"), "WallCondition");
            }

            System.out.println("PROBE5 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE5 ABORTED: " + e);
        }
    }
}
