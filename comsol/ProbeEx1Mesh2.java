import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** ProbeEx1Mesh2: fast measurement of the two Ex1 mesh questions.
 *
 *  The domain is deliberately SMALL (600 x 100 um instead of 6000 x 100 um)
 *  so this runs in seconds.  The element COUNT scales with the domain but
 *  the two questions asked here do not depend on its size:
 *
 *  (1) Does the Size attribute apply at all?  Measured: the previous run gave
 *      172 elements with hmax = 2 um over 6000 x 100 um, which is far too
 *      few - COMSOL's Size node uses a PREDEFINED size unless "custom" is
 *      switched on, so hmax/hmin/hgrad are silently ignored.
 *  (2) The mesh control edge partitions the domain for the mesher (2
 *      sub-domains) while the physics still sees 1 domain, so a FreeTri
 *      selection that covers only domain 1 leaves half the domain unmeshed:
 *      "All geometric entities adjacent to a mesh control entity need to be
 *      meshed."  The fix is to select ALL domains.
 */
public class ProbeEx1Mesh2 {

    static GeomSequence build(Model model) {
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 2);
        g.lengthUnit("um");
        g.create("r1", "Rectangle");
        g.feature("r1").set("size", new String[]{"600[um]", "100[um]"});
        g.feature("r1").set("pos",  new String[]{"0", "-50[um]"});
        g.create("ln1", "LineSegment");
        g.feature("ln1").set("specify1", "coord");
        g.feature("ln1").set("specify2", "coord");
        g.feature("ln1").set("coord1", new String[]{"0[um]", "0[um]"});
        g.feature("ln1").set("coord2", new String[]{"600[um]", "0[um]"});
        g.run();
        g.create("mce1", "MeshControlEdges");
        g.feature("mce1").selection("input").set("ln1", 1);
        g.run("mce1");
        g.run();
        return g;
    }

    static int mesh(Model model, String label, boolean useCustom, int selMode) {
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("ftri1", "FreeTri");
            if (selMode == 0) {
                m.feature("ftri1").selection().geom("geom1", 2).set(1);
            } else if (selMode == 1) {
                m.feature("ftri1").selection().geom("geom1", 2).set(1, 2);
            } else {
                m.feature("ftri1").selection().all();
            }
            m.create("size1", "Size");
            if (useCustom) {
                m.feature("size1").set("custom", "on");
            }
            m.feature("size1").set("hmax", "2[um]");
            m.feature("size1").set("hmin", "0.3[um]");
            m.feature("size1").set("hgrad", 1.3);
            m.run();
            int n = m.stat().getNumElem();
            System.out.println(label + " : elements = " + n
                             + "  types=" + Arrays.toString(m.stat().getTypes()));
            return n;
        } catch (Throwable e) {
            System.out.println(label + " : FAILED - " + e.getMessage());
            return -1;
        }
    }

    public static void main(String[] args) {
        try {
            // Size node properties
            Model m0 = ModelUtil.create("ProbeEx1Mesh2A");
            build(m0);
            MeshSequence m0s = m0.component("comp1").mesh().create("mesh1", "geom1");
            m0s.create("size1", "Size");
            System.out.println("=== Size node properties ===");
            System.out.println(Arrays.toString(m0s.feature("size1").properties()));
            for (String p : new String[]{"custom", "hauto", "hmax"}) {
                try {
                    System.out.println("  default " + p + " = "
                                     + m0s.feature("size1").getString(p));
                } catch (Throwable e) {
                    System.out.println("  getString " + p + " failed: " + e.getMessage());
                }
            }

            System.out.println("\n=== mesh variants on the 600 x 100 um domain ===");
            // A: predefined size (custom off), domain 1 only
            Model a = ModelUtil.create("PX1M2A"); build(a);
            mesh(a, "A predefined size, sel domain 1     ", false, 0);
            // B: custom size, domain 1 only
            Model b = ModelUtil.create("PX1M2B"); build(b);
            mesh(b, "B custom size,    sel domain 1     ", true, 0);
            // C: custom size, all domains
            Model c = ModelUtil.create("PX1M2C"); build(c);
            mesh(c, "C custom size,    sel all domains  ", true, 2);
            // D: custom size, explicit 1,2
            Model d = ModelUtil.create("PX1M2D"); build(d);
            mesh(d, "D custom size,    sel domains 1,2   ", true, 1);

            System.out.println("\nPROBEEX1MESH2 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBEEX1MESH2 ABORTED: " + e);
        }
    }
}
