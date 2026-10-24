import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** Probe 14: enumerate the MESH feature's real property names, and test
 *  mesh creation with and without the geometry argument.
 */
public class ProbeMeshApi {
    public static void main(String[] args) {
        try {
            Model m = ModelUtil.create("PMeshApi");
            m.component().create("comp1");
            m.component("comp1").geom().create("geom1", 3);
            m.component("comp1").geom("geom1").lengthUnit("um");
            m.component("comp1").geom("geom1").create("blk1", "Block");
            m.component("comp1").geom("geom1").feature("blk1")
                .set("size", new String[]{"200", "100", "100"});
            m.component("comp1").geom("geom1").run();

            // --- variant 1: mesh WITHOUT the geometry argument ---
            try {
                m.component("comp1").mesh().create("m1");
                System.out.println("m1 created (no geom arg)");
                System.out.println("m1 tags: " + Arrays.toString(
                        m.component("comp1").mesh("m1").feature().tags()));
                m.component("comp1").mesh("m1").autoMeshSize(5);
                System.out.println("m1 tags after autoMeshSize: " + Arrays.toString(
                        m.component("comp1").mesh("m1").feature().tags()));
                m.component("comp1").mesh("m1").run();
                System.out.println("m1 elements: " + m.component("comp1").mesh("m1").getNumElem());
            } catch (Throwable e) {
                System.out.println("m1 FAIL: " + e.getMessage());
            }

            // --- variant 2: mesh WITH the geometry argument ---
            try {
                m.component("comp1").mesh().create("m2", "geom1");
                System.out.println("m2 created (with geom arg)");
                System.out.println("m2 tags: " + Arrays.toString(
                        m.component("comp1").mesh("m2").feature().tags()));
                m.component("comp1").mesh("m2").create("ftet1", "FreeTet");
                System.out.println("ftet1 props: " + Arrays.toString(
                        m.component("comp1").mesh("m2").feature("ftet1").properties()));
                m.component("comp1").mesh("m2").feature("ftet1").set("hmax", "10[um]");
                System.out.println("ftet1 hmax set OK");
                m.component("comp1").mesh("m2").run();
                System.out.println("m2 elements: " + m.component("comp1").mesh("m2").getNumElem());
            } catch (Throwable e) {
                System.out.println("m2 FAIL: " + e.getMessage());
            }

            // --- variant 3: Size feature property names ---
            try {
                m.component("comp1").mesh().create("m3", "geom1");
                m.component("comp1").mesh("m3").create("size1", "Size");
                System.out.println("size1 props: " + Arrays.toString(
                        m.component("comp1").mesh("m3").feature("size1").properties()));
                m.component("comp1").mesh("m3").create("ftet1", "FreeTet");
                m.component("comp1").mesh("m3").feature("size1").set("hmax", "8[um]");
                m.component("comp1").mesh("m3").feature("size1").set("hmin", "0.5[um]");
                m.component("comp1").mesh("m3").run();
                System.out.println("m3 elements: " + m.component("comp1").mesh("m3").getNumElem());
            } catch (Throwable e) {
                System.out.println("m3 FAIL: " + e.getMessage());
            }

            System.out.println("PROBEMESHAPI DONE");
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
