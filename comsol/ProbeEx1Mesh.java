import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** ProbeEx1Mesh: why did the Ex1 mesh come out with only 172 elements, and how
 *  does the mesh control edge have to be handled?
 *
 *  Two measured problems in Ex1_HFilter:
 *   (1) "mesh done, elements: 172" with hmax = 2 um over a 6000 x 100 um
 *       rectangle.  Far too few, so the Size attribute was probably NOT
 *       applied: COMSOL's Size node uses a predefined size unless "custom"
 *       is turned on.  The Size node's properties are printed to confirm.
 *   (2) "All geometric entities adjacent to a mesh control entity need to be
 *       meshed."  The mesh control edge partitions the domain for the mesher
 *       (2 sub-domains) even though the physics still sees 1 domain, so the
 *       FreeTri selection must cover BOTH sides.  Three selections are tried.
 */
public class ProbeEx1Mesh {

    static void build(Model model, boolean withMce) {
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 2);
        g.lengthUnit("um");
        g.create("r1", "Rectangle");
        g.feature("r1").set("size", new String[]{"6000[um]", "100[um]"});
        g.feature("r1").set("pos",  new String[]{"0", "-50[um]"});
        if (withMce) {
            g.create("ln1", "LineSegment");
            g.feature("ln1").set("specify1", "coord");
            g.feature("ln1").set("specify2", "coord");
            g.feature("ln1").set("coord1", new String[]{"0[um]", "0[um]"});
            g.feature("ln1").set("coord2", new String[]{"6000[um]", "0[um]"});
            g.run();
            g.create("mce1", "MeshControlEdges");
            g.feature("mce1").selection("input").set("ln1", 1);
            g.run("mce1");
            g.run();
        } else {
            g.run();
        }
    }

    public static void main(String[] args) {
        try {
            // ---- (1) what are the Size node's properties? -----------------
            Model m0 = ModelUtil.create("ProbeEx1MeshA");
            build(m0, false);
            MeshSequence m0s = m0.component("comp1").mesh().create("mesh1", "geom1");
            m0s.create("size1", "Size");
            System.out.println("=== Size node properties ===");
            System.out.println(Arrays.toString(m0s.feature("size1").properties()));
            for (String p : new String[]{"hmax", "custom", "hauto", "hgrad"}) {
                try {
                    System.out.println("  current " + p + " = " + m0s.feature("size1").getString(p));
                } catch (Throwable e) {
                    System.out.println("  getString " + p + " failed: " + e.getMessage());
                }
            }

            // ---- (2) mesh with custom size, with and without the mce ------
            System.out.println("\n=== (2) custom size + FreeTri ===");
            for (int variant = 0; variant < 3; variant++) {
                Model m = ModelUtil.create("ProbeEx1MeshB" + variant);
                build(m, true);
                try {
                    MeshSequence ms = m.component("comp1").mesh().create("mesh1", "geom1");
                    ms.create("ftri1", "FreeTri");
                    if (variant == 0) {
                        ms.feature("ftri1").selection().geom("geom1", 2).set(1);
                    } else if (variant == 1) {
                        ms.feature("ftri1").selection().geom("geom1", 2).set(1, 2);
                    } else {
                        ms.feature("ftri1").selection().all();
                    }
                    ms.create("size1", "Size");
                    ms.feature("size1").set("custom", "on");
                    ms.feature("size1").set("hmax", "2[um]");
                    ms.feature("size1").set("hmin", "0.3[um]");
                    ms.feature("size1").set("hgrad", 1.3);
                    ms.run();
                    System.out.println("VARIANT " + variant + " : elements = "
                                     + ms.stat().getNumElem()
                                     + "  types=" + Arrays.toString(ms.stat().getTypes()));
                } catch (Throwable e) {
                    System.out.println("VARIANT " + variant + " FAILED: " + e.getMessage());
                }
            }

            // ---- (3) same mesh, geometry WITHOUT the mesh control edge ---
            System.out.println("\n=== (3) same mesh, geometry WITHOUT mce1 ===");
            try {
                Model m3 = ModelUtil.create("ProbeEx1MeshC");
                build(m3, false);
                MeshSequence ms3 = m3.component("comp1").mesh().create("mesh1", "geom1");
                ms3.create("ftri1", "FreeTri");
                ms3.feature("ftri1").selection().geom("geom1", 2).set(1);
                ms3.create("size1", "Size");
                ms3.feature("size1").set("custom", "on");
                ms3.feature("size1").set("hmax", "2[um]");
                ms3.feature("size1").set("hmin", "0.3[um]");
                ms3.feature("size1").set("hgrad", 1.3);
                ms3.run();
                System.out.println("NO-MCE elements = " + ms3.stat().getNumElem());
            } catch (Throwable e) {
                System.out.println("NO-MCE FAILED: " + e.getMessage());
            }

            System.out.println("\nPROBEEX1MESH DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBEEX1MESH ABORTED: " + e);
        }
    }
}
