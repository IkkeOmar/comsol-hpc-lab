import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** Probe 10: how do you select a MESH CONTROL EDGE from a mesh feature?
 *
 *  Measured facts so far:
 *   - MeshControlEdges must be created AFTER the geometry has been run, and
 *     its input must name the geometry feature that owns the edge.
 *   - After the mce feature runs, the mid-line is NOT an ordinary geometry
 *     edge any more (a BoxSelection for it returns an empty set), because
 *     the mesh control edge is a virtual, mesh-only entity.
 *   - The model-level selection "geom1_mce1" does not exist.
 *
 *  So: which selection syntax does a mesh Distribution use to pick it up?
 *  Four candidates are tested here.  Each is tried in isolation and the
 *  resulting element count is printed, so a working syntax is identified by
 *  evidence rather than by guessing.
 */
public class ProbeMce {

    static GeomSequence build(Model model) {
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 2);
        g.lengthUnit("um");
        g.create("r1", "Rectangle");
        g.feature("r1").set("size", new String[]{"6000[um]", "100[um]"});
        g.feature("r1").set("pos",  new String[]{"0", "-50[um]"});
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
        return g;
    }

    static int trySyntax(Model model, String label, int variant) {
        try {
            MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "8[um]");
            m.feature("size1").set("hmin", "0.5[um]");
            m.feature("size1").set("hgrad", 1.3);
            m.create("dist1", "Distribution");
            switch (variant) {
                case 1:
                    m.feature("dist1").selection().geom("geom1_mce1", 1);
                    break;
                case 2:
                    m.feature("dist1").selection().named("geom1_mce1");
                    break;
                case 3:
                    m.feature("dist1").selection().geom("geom1_mce1", 1);
                    break;
                case 4:
                    // per the COMSOL 6.4 reference: a mesh control edge is
                    // picked up by the MESH through the geometry's own
                    // "Edges to include" list, so the Distribution is applied
                    // to the ordinary edge selection of the finalized
                    // geometry that the mce node consumed.
                    m.feature("dist1").selection().geom(1).set(new int[]{1});
                    break;
                default:
                    return -2;
            }
            m.feature("dist1").set("numelem", 120);
            m.run();
            int n = m.stat().getNumElem();
            System.out.println("VARIANT " + variant + " (" + label + ") : OK, elements = " + n);
            return n;
        } catch (Throwable e) {
            System.out.println("VARIANT " + variant + " (" + label + ") : FAILED - "
                             + e.getMessage());
            return -1;
        }
    }

    public static void main(String[] args) {
        try {
            // what selections exist at all after the mce?
            Model m0 = ModelUtil.create("ProbeMce0");
            GeomSequence g0 = build(m0);
            System.out.println("=== after mce ===");
            System.out.println("domains = " + g0.getNDomains() + ", edges = " + g0.getNEdges());
            System.out.println("model selection tags:");
            for (String t : m0.selection().tags()) {
                System.out.println("   TAG " + t);
            }
            System.out.println("geometry feature tags: " + Arrays.toString(g0.feature().tags()));

            for (int v = 1; v <= 4; v++) {
                Model m = ModelUtil.create("ProbeMce" + v);
                build(m);
                trySyntax(m, "candidate", v);
            }

            // baseline: no distribution at all
            Model mb = ModelUtil.create("ProbeMceBase");
            build(mb);
            try {
                MeshSequence m = mb.component("comp1").mesh().create("mesh1", "geom1");
                m.create("size1", "Size");
                m.feature("size1").set("hmax", "8[um]");
                m.feature("size1").set("hmin", "0.5[um]");
                m.feature("size1").set("hgrad", 1.3);
                m.run();
                System.out.println("BASELINE (no distribution) elements = " + m.stat().getNumElem());
            } catch (Throwable e) {
                System.out.println("BASELINE FAILED: " + e.getMessage());
            }

            System.out.println("\nPROBEMCE DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBEMCE ABORTED: " + e);
        }
    }
}
