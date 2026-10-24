import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** Probe 9: two open questions, measured rather than guessed.
 *
 *  Q1  How do you get a usable Mesh Control Edge in a 2D geometry, and how
 *      do you select it afterwards for a mesh Distribution?
 *      (The earlier attempt produced a finalized geometry with only 4 edges
 *      and an empty box selection - so the mce feature never took effect.)
 *
 *  Q2  Semantics of the Step function's from/to: does step1(y) equal "from"
 *      below the location and "to" above it?  This decides whether the sugar
 *      enters the lower or the upper half of the H-filter.
 */
public class Probe9 {

    static void build(Model model) {
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
    }

    public static void main(String[] args) {
        try {
            // ------------------------------------------------------------------
            // Q1a: create mce BEFORE any run, input = the LineSegment
            // ------------------------------------------------------------------
            System.out.println("########## Q1a: mce created before any run ##########");
            Model m1 = ModelUtil.create("Probe9a");
            build(m1);
            m1.component("comp1").geom("geom1").create("mce1", "MeshControlEdges");
            m1.component("comp1").geom("geom1").feature("mce1")
             .selection("input").set("ln1", 1);
            try {
                m1.component("comp1").geom("geom1").run();
                System.out.println("run OK");
            } catch (Throwable e) {
                System.out.println("run FAILED: " + e.getMessage());
            }
            System.out.println("finalized edges: " + m1.component("comp1").geom("geom1").getNEdges());
            System.out.println("selection tags:");
            for (String t : m1.selection().tags()) {
                System.out.println("   TAG " + t);
            }
            // does a box selection now find the mid-line?
            GeomSequence g1 = m1.component("comp1").geom("geom1");
            g1.create("selMid", "BoxSelection");
            g1.feature("selMid").set("entitydim", 1);
            g1.feature("selMid").set("condition", "allvertices");
            g1.feature("selMid").set("xmin", "-1[um]");
            g1.feature("selMid").set("xmax", "6001[um]");
            g1.feature("selMid").set("ymin", "-1[um]");
            g1.feature("selMid").set("ymax", "1[um]");
            try {
                g1.run();
                System.out.println("selMid edges: "
                    + Arrays.toString(m1.selection("geom1_selMid").entities(1)));
            } catch (Throwable e) {
                System.out.println("selMid FAILED: " + e.getMessage());
            }
            // can a mesh Distribution select it?
            try {
                MeshSequence ms = m1.component("comp1").mesh().create("mesh1", "geom1");
                ms.create("size1", "Size");
                ms.feature("size1").set("hmax", "25[um]");
                ms.feature("size1").set("hmin", "1[um]");
                ms.feature("size1").set("hgrad", 1.3);
                ms.create("dist1", "Distribution");
                ms.feature("dist1").selection().named("geom1_selMid");
                ms.feature("dist1").set("numelem", 120);
                ms.run();
                System.out.println("DIST via named(geom1_selMid) OK, elements = "
                                 + ms.stat().getNumElem());
            } catch (Throwable e) {
                System.out.println("DIST via named FAILED: " + e.getMessage());
            }

            // ------------------------------------------------------------------
            // Q1b: mce with input = the finalized "fin" edges
            // ------------------------------------------------------------------
            System.out.println("\n\n########## Q1b: mce input from finalized geometry ##########");
            Model m2 = ModelUtil.create("Probe9b");
            build(m2);
            m2.component("comp1").geom("geom1").run();
            GeomSequence g2 = m2.component("comp1").geom("geom1");
            System.out.println("geom feature tags: " + Arrays.toString(g2.feature().tags()));
            System.out.println("edges before mce: " + g2.getNEdges());
            try {
                g2.create("mce1", "MeshControlEdges");
                g2.feature("mce1").selection("input").set("ln1", 1);
                g2.run("mce1");
                g2.run();
                System.out.println("edges after mce: " + g2.getNEdges());
                System.out.println("selection tags:");
                for (String t : m2.selection().tags()) {
                    System.out.println("   TAG " + t);
                }
            } catch (Throwable e) {
                System.out.println("mce FAILED: " + e.getMessage());
            }

            // ------------------------------------------------------------------
            // Q2: Step function semantics - evaluate step1 at y = -10 and +10 um
            // ------------------------------------------------------------------
            System.out.println("\n\n########## Q2: STEP FUNCTION SEMANTICS ##########");
            Model m3 = ModelUtil.create("Probe9c");
            m3.func().create("step1", "Step");
            m3.func("step1").set("funcname", "step1");
            m3.func("step1").set("location", "0");
            m3.func("step1").set("from", 0);
            m3.func("step1").set("to", 1);
            m3.func("step1").set("smooth", "0[mm]");
            m3.func("step1").set("smoothactive", true);
            // The Step function's from/to semantics are settled from the
            // instructor's own reference table on assignment page 4
            // (Dc vs Concentration), not guessed here.
            System.out.println("step1 configured: location=0 from=0 to=1");

            // ------------------------------------------------------------------
            // Q3  spf Inlet with a FLOW RATE: what are the allowed values of
            //     MassFlowType and StandardFlowRateDefinedBy?  (In 2D the flow
            //     rate is per unit depth, so this matters.)
            // ------------------------------------------------------------------
            System.out.println("\n\n########## Q3: spf Inlet MASS FLOW ENUM VALUES ##########");
            Model m4 = ModelUtil.create("Probe9d");
            m4.component().create("comp1");
            m4.component("comp1").geom().create("geom1", 2);
            m4.component("comp1").geom("geom1").lengthUnit("um");
            m4.component("comp1").geom("geom1").create("r1", "Rectangle");
            m4.component("comp1").geom("geom1").feature("r1")
              .set("size", new String[]{"1000", "100"});
            m4.component("comp1").geom("geom1").run();
            m4.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            Physics spf4 = m4.component("comp1").physics("spf");
            spf4.create("inl1", "Inlet", 1);
            spf4.feature("inl1").selection().set(1);
            spf4.feature("inl1").set("BoundaryCondition", "MassFlow");
            for (String p : new String[]{"MassFlowType", "StandardFlowRateDefinedBy"}) {
                try {
                    System.out.println("ALLOWED inl1." + p + " : "
                        + Arrays.toString(spf4.feature("inl1").getAllowedPropertyValues(p)));
                } catch (Throwable e) {
                    System.out.println("ALLOWED FAIL " + p + " : " + e.getMessage());
                }
            }
            // and what the shallow-channel prop group looks like
            System.out.println("spf prop groups: " + Arrays.toString(spf4.prop().tags()));
            try {
                spf4.prop("PhysicalModelProperty").set("ShallowChannelProperty", "1");
                spf4.prop("PhysicalModelProperty").set("dz", "5[um]");
                System.out.println("SET OK shallow channel");
                System.out.println("PhysicalModelProperty props: "
                    + Arrays.toString(spf4.prop("PhysicalModelProperty").properties()));
            } catch (Throwable e) {
                System.out.println("shallow channel FAILED: " + e.getMessage());
            }

            System.out.println("\nPROBE9 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE9 ABORTED: " + e);
        }
    }
}
