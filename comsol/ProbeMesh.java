import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/**
 * Probe M: settle the Ex-4 mesh strategy and the remaining physics-API
 * questions, all in ONE local batch run.
 *
 *  M1  Electrostatics default features (the "Free Space" node overrides the
 *      material permittivity - must be switched to "From material").
 *  M2  LaminarFlow default features + Inlet options.
 *  M3  DEP node: allowed E_src values, and whether user-defined
 *      E = {es.Ex, es.Ey, es.Ez} is accepted.
 *  M4  Mesh candidates on the real 45-electrode geometry:
 *      print element count, element quality and wall-clock for each.
 *
 * Mesh reasoning (why these candidates):
 *   The DEP field gradient decays as exp(-pi*y/d) with d = 10 um, i.e. a
 *   decay length of d/pi = 3.2 um, and the electrode array is periodic in x
 *   with a 20 um period.  A uniform 2 um mesh over the whole
 *   1000 x 100 x 100 um box is ~1.25 M cells and the COMSOL mesher gave up
 *   on it (measured: 0 elements).  The candidates below therefore grade
 *   from a fine FLOOR BOUNDARY mesh (2 um in x via a Distribution on the
 *   floor edges, which also gives 100/5 = 20 um in y for free) up to a
 *   coarse bulk.
 */
public class ProbeMesh {

    static void safe(String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            System.out.println("!! " + what + " FAILED: " + t.getMessage());
        }
    }

    static void allowed(PhysicsFeature f, String[] props) {
        for (String p : props) {
            try {
                System.out.println("    ALLOWED " + p + " : "
                        + Arrays.toString(f.getAllowedPropertyValues(p)));
            } catch (Throwable t) {
                System.out.println("    ALLOWED " + p + " : (not enumerable)");
            }
        }
    }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("PM");

            // ---------------- geometry -------------------------------------
            model.param().set("L_ch", "1000[um]");
            model.param().set("W_ch", "100[um]");
            model.param().set("H_ch", "100[um]");
            model.param().set("elW",  "10[um]");
            model.param().set("elP",  "20[um]");
            model.param().set("elX0", "50[um]");

            model.component().create("comp1");
            GeomSequence g = model.component("comp1").geom().create("geom1", 3);
            g.lengthUnit("um");
            g.create("blk1", "Block");
            g.feature("blk1").set("size", new String[]{"L_ch", "W_ch", "H_ch"});
            g.feature("blk1").set("pos",  new String[]{"0", "0", "0"});

            g.create("wp1", "WorkPlane");
            g.feature("wp1").set("planetype", "quick");
            g.feature("wp1").set("quickplane", "xy");
            GeomSequence wp = g.feature("wp1").geom();
            for (int i = 0; i < 45; i++) {
                String tag = "r" + i;
                wp.create(tag, "Rectangle");
                wp.feature(tag).set("size", new String[]{"elW", "W_ch"});
                wp.feature(tag).set("pos",  new String[]{"elX0 + " + i + "*elP", "0"});
            }
            g.run("wp1");

            // floor faces (z = 0) and floor edges (z = 0)
            g.create("selFloorF", "BoxSelection");
            g.feature("selFloorF").set("entitydim", 2);
            g.feature("selFloorF").set("condition", "allvertices");
            g.feature("selFloorF").set("xmin", "-1[um]"); g.feature("selFloorF").set("xmax", "L_ch+1[um]");
            g.feature("selFloorF").set("ymin", "-1[um]"); g.feature("selFloorF").set("ymax", "W_ch+1[um]");
            g.feature("selFloorF").set("zmin", "-1[um]"); g.feature("selFloorF").set("zmax", "1[um]");

            g.create("selFloorE", "BoxSelection");
            g.feature("selFloorE").set("entitydim", 1);
            g.feature("selFloorE").set("condition", "allvertices");
            g.feature("selFloorE").set("xmin", "-1[um]"); g.feature("selFloorE").set("xmax", "L_ch+1[um]");
            g.feature("selFloorE").set("ymin", "-1[um]"); g.feature("selFloorE").set("ymax", "W_ch+1[um]");
            g.feature("selFloorE").set("zmin", "-1[um]"); g.feature("selFloorE").set("zmax", "1[um]");
            g.run();

            int[] floorF = model.selection("geom1_selFloorF").entities(2);
            int[] floorE = model.selection("geom1_selFloorE").entities(1);
            System.out.println("M0 floor faces = " + floorF.length
                             + "   floor edges = " + floorE.length);
            System.out.println("M0 total faces = " + g.getNFaces()
                             + "  edges = " + g.getNEdges()
                             + "  domains = " + g.getNDomains());

            // ---------------- material -------------------------------------
            model.component("comp1").material().create("mat1", "Common");
            model.component("comp1").material("mat1").label("Water");
            model.component("comp1").material("mat1").propertyGroup("def")
                .set("dynamicviscosity", "1e-3[Pa*s]")
                .set("density", "1000[kg/m^3]")
                .set("electricconductivity", "5.5[us/m]")
                .set("relpermittivity", "80");

            // ---------------- physics: probes ------------------------------
            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            model.component("comp1").physics().create("es", "Electrostatics", "geom1");
            model.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1");

            System.out.println("M1 es default features: "
                    + Arrays.toString(model.component("comp1").physics("es").feature().tags()));
            for (String t : model.component("comp1").physics("es").feature().tags()) {
                PhysicsFeature f = model.component("comp1").physics("es").feature(t);
                System.out.println("    es." + t + " type=" + f.getType()
                        + " props=" + Arrays.toString(f.properties()));
                allowed(f, new String[]{"epsilonr_mat", "sigma_mat", "epsilonr", "sigma"});
            }

            System.out.println("M2 spf default features: "
                    + Arrays.toString(model.component("comp1").physics("spf").feature().tags()));
            for (String t : model.component("comp1").physics("spf").feature().tags()) {
                PhysicsFeature f = model.component("comp1").physics("spf").feature(t);
                System.out.println("    spf." + t + " type=" + f.getType()
                        + " props=" + Arrays.toString(f.properties()));
            }

            System.out.println("M3 fpt default features: "
                    + Arrays.toString(model.component("comp1").physics("fpt").feature().tags()));

            model.component("comp1").physics("fpt").create("deff1", "DielectrophoreticForce", 3);
            PhysicsFeature dep = model.component("comp1").physics("fpt").feature("deff1");
            dep.selection().all();
            System.out.println("M3 deff1 E_src allowed: "
                    + Arrays.toString(dep.getAllowedPropertyValues("E_src")));
            try {
                dep.set("E_src", "userdef");
                dep.set("E", new String[]{"es.Ex", "es.Ey", "es.Ez"});
                System.out.println("M3 userdef E = {es.Ex,es.Ey,es.Ez} ACCEPTED");
                dep.set("E_src", "root.comp1.es.Ex");
                System.out.println("M3 E_src = root.comp1.es.Ex ACCEPTED");
            } catch (Throwable t) {
                System.out.println("M3 E_src set FAILED: " + t.getMessage());
            }
            allowed(dep, new String[]{"epsilonr_mat", "sigma_mat", "epsilonr", "sigma",
                                      "UsePPR", "AngularFrequency"});

            // ---------------- M4: mesh candidates ---------------------------
            // candidate list: {tag, hmax, hmin, hgrad, distNumElem (0 = none),
            //                   floorSizeHmax ("-" = none)}
            String[][] cand = {
                {"V1", "20[um]", "0.8[um]", "1.30", "5", "-"},
                {"V2", "30[um]", "0.8[um]", "1.40", "5", "-"},
                {"V3", "10[um]", "0.8[um]", "1.25", "5", "-"},
                {"V4", "20[um]", "0.8[um]", "1.30", "5", "2.5[um]"},
                {"V5", "20[um]", "0.8[um]", "1.30", "10", "-"},
            };

            for (String[] c : cand) {
                String tag = "m_" + c[0];
                long t0 = System.currentTimeMillis();
                try {
                    MeshSequence m = model.component("comp1").mesh().create(tag, "geom1");
                    m.create("size1", "Size");
                    m.feature("size1").set("hmax", c[1]);
                    m.feature("size1").set("hmin", c[2]);
                    m.feature("size1").set("hgrad", c[3]);
                    if (!c[5].equals("-")) {
                        m.create("sizeF", "Size");
                        m.feature("sizeF").selection().geom(2).set(floorF);
                        m.feature("sizeF").set("hmax", c[5]);
                    }
                    if (!c[4].equals("0")) {
                        m.create("dist1", "Distribution");
                        m.feature("dist1").selection().geom(1).set(floorE);
                        m.feature("dist1").set("numelem", c[4]);
                    }
                    m.run();
                    long dt = System.currentTimeMillis() - t0;
                    System.out.println("M4 " + c[0] + " hmax=" + c[1] + " hgrad=" + c[3]
                            + " dist=" + c[4] + " floorSize=" + c[5]
                            + " -> ELEMENTS = " + m.stat().getNumElem()
                            + "  minQual = " + m.stat().getMinQuality()
                            + "  meanQual = " + m.stat().getMeanQuality()
                            + "  types = " + Arrays.toString(m.stat().getTypes())
                            + "  [build " + dt + " ms]");
                } catch (Throwable t) {
                    System.out.println("M4 " + c[0] + " FAILED after "
                            + (System.currentTimeMillis() - t0) + " ms: " + t.getMessage());
                }
            }

            System.out.println("PROBEMESH DONE");
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("PROBEMESH ABORTED: " + t);
        }
    }
}
