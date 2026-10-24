import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** Probe 8: measure the two GEOMETRIES and their MESHES directly, locally.
 *
 *  Why: the Ex4 cluster run failed with "mesh done, elements: 0" and every
 *  downstream quantity NaN.  The mesh must be built for the real geometry
 *  before spending a cluster slot on the transient.
 *
 *  Part A - Ex1 geometry: how to select the Mesh Control Edge (the mid-line)
 *            for the mesh Distribution.
 *  Part B - Ex4 geometry: domain/boundary counts, and which mesh recipe
 *            actually produces elements.
 */
public class Probe8 {

    static void header(String s) {
        System.out.println();
        System.out.println("################ " + s + " ################");
    }

    public static void main(String[] args) {
        try {
            partA();
            partB();
            System.out.println("PROBE8 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE8 ABORTED: " + e);
        }
    }

    // ------------------------------------------------------------------ A
    static void partA() {
        header("A: EX1 GEOMETRY - mesh control edge selection");
        Model model = ModelUtil.create("Probe8A");
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

        System.out.println("edges in finalized geometry: " + g.getNEdges());
        System.out.println("available selection tags:");
        for (String t : model.selection().tags()) {
            System.out.println("   " + t);
        }
        // what does geom1_mce1 contain?
        try {
            System.out.println("geom1_mce1 edges: "
                + Arrays.toString(model.selection("geom1_mce1").entities(1)));
        } catch (Throwable e) {
            System.out.println("geom1_mce1 FAIL: " + e.getMessage());
        }
        // try a plain box selection for the mid-line
        g.create("selMid", "BoxSelection");
        g.feature("selMid").set("entitydim", 1);
        g.feature("selMid").set("condition", "allvertices");
        g.feature("selMid").set("xmin", "-1[um]");
        g.feature("selMid").set("xmax", "6001[um]");
        g.feature("selMid").set("ymin", "-1[um]");
        g.feature("selMid").set("ymax", "1[um]");
        g.run();
        try {
            System.out.println("selMid edges: "
                + Arrays.toString(model.selection("geom1_selMid").entities(1)));
        } catch (Throwable e) {
            System.out.println("selMid FAIL: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ B
    static void partB() {
        header("B: EX4 GEOMETRY + MESH RECIPES");
        Model model = ModelUtil.create("Probe8B");
        model.component().create("comp1");
        GeomSequence geomSeq = model.component("comp1").geom().create("geom1", 3);
        geomSeq.lengthUnit("um");
        geomSeq.create("blk1", "Block");
        geomSeq.feature("blk1").set("size", new String[]{"1000[um]", "100[um]", "100[um]"});
        geomSeq.feature("blk1").set("pos",  new String[]{"0", "0", "0"});

        geomSeq.create("wp1", "WorkPlane");
        geomSeq.feature("wp1").set("planetype", "quick");
        geomSeq.feature("wp1").set("quickplane", "xy");
        GeomSequence wp = geomSeq.feature("wp1").geom();
        for (int i = 0; i < 45; i++) {
            String tag = "r" + i;
            wp.create(tag, "Rectangle");
            wp.feature(tag).set("size", new String[]{"10[um]", "100[um]"});
            wp.feature(tag).set("pos",  new String[]{"(50 + " + i + "*20)[um]", "0"});
        }
        geomSeq.run("wp1");
        geomSeq.run();

        System.out.println("domains  : " + geomSeq.getNDomains());
        System.out.println("boundaries: " + geomSeq.getNBoundaries());
        System.out.println("edges    : " + geomSeq.getNEdges());

        // --- recipe 1: the one that produced 0 elements -------------------
        try {
            MeshSequence m1 = model.component("comp1").mesh().create("mesh1", "geom1");
            m1.create("size1", "Size");
            m1.feature("size1").set("hmax", "2[um]");
            m1.feature("size1").set("hmin", "0.5[um]");
            m1.feature("size1").set("hgrad", 1.25);
            m1.create("dist1", "Distribution");
            m1.feature("dist1").selection().geom(1).set(new int[]{1, 2, 3});
            m1.feature("dist1").set("numelem", 100);
            m1.run();
            System.out.println("RECIPE1 (hmax 2um + Distribution on 3 edges) elements = "
                             + m1.stat().getNumElem());
        } catch (Throwable e) {
            System.out.println("RECIPE1 FAILED: " + e.getMessage());
        }

        // --- recipe 2: swept mesh, coarse x, fine in the floor layer -------
        try {
            MeshSequence m2 = model.component("comp1").mesh().create("mesh2", "geom1");
            m2.create("ftri1", "FreeTri");
            m2.feature("ftri1").selection().geom(3).set(1);
            m2.create("swe1", "Sweep");
            m2.feature("swe1").selection().geom("geom1").set(1, 2);
            m2.run();
            System.out.println("RECIPE2 (FreeTri + Sweep) elements = "
                             + m2.stat().getNumElem()
                             + "  types=" + Arrays.toString(m2.stat().getTypes()));
        } catch (Throwable e) {
            System.out.println("RECIPE2 FAILED: " + e.getMessage());
        }

        // --- recipe 3: swept mesh with a Distribution along the sweep ------
        try {
            MeshSequence m3 = model.component("comp1").mesh().create("mesh3", "geom1");
            m3.create("ftri1", "FreeTri");
            m3.feature("ftri1").selection().geom(3).set(1);
            m3.create("swe1", "Sweep");
            m3.feature("swe1").selection().geom("geom1").set(1, 2);
            m3.feature("swe1").create("dis1", "Distribution");
            m3.feature("swe1").feature("dis1").set("numelem", 200);
            m3.run();
            System.out.println("RECIPE3 (Sweep + Distribution 200) elements = "
                             + m3.stat().getNumElem());
        } catch (Throwable e) {
            System.out.println("RECIPE3 FAILED: " + e.getMessage());
        }

        // --- recipe 4: plain auto mesh, for scale reference ----------------
        try {
            MeshSequence m4 = model.component("comp1").mesh().create("mesh4", "geom1");
            m4.autoMeshSize(5);
            m4.run();
            System.out.println("RECIPE4 (autoMeshSize 5) elements = "
                             + m4.stat().getNumElem());
        } catch (Throwable e) {
            System.out.println("RECIPE4 FAILED: " + e.getMessage());
        }
    }
}
