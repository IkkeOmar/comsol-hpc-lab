import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** Probe 7: measure the exact enum strings and mesher property names that the
 *  shipped documentation describes only in prose.  One measurement, not a
 *  guess-and-check loop:
 *    - spf Inlet: BoundaryCondition options (which one imposes a flow rate)
 *    - spf PhysicalModelProperty: the shallow-channel property names
 *    - TDS: Inflow node properties, transport-properties node tags
 *    - Mesher: Distribution + Swept property names
 */
public class Probe7 {
    static void props(String label, PhysicsFeature f) {
        try {
            System.out.println("PROPS " + label + ": " + Arrays.toString(f.properties()));
        } catch (Throwable e) {
            System.out.println("PROPS FAIL " + label + " : " + e.getMessage());
        }
    }
    static void allowed(String label, PhysicsFeature f, String p) {
        try {
            System.out.println("ALLOWED " + label + "." + p + " : "
                             + Arrays.toString(f.getAllowedPropertyValues(p)));
        } catch (Throwable e) {
            System.out.println("ALLOWED FAIL " + label + "." + p + " : " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe7");
            model.component().create("comp1");
            model.component("comp1").geom().create("geom1", 2);
            model.component("comp1").geom("geom1").lengthUnit("um");
            model.component("comp1").geom("geom1").create("r1", "Rectangle");
            model.component("comp1").geom("geom1").feature("r1")
                 .set("size", new String[]{"1000", "100"});
            model.component("comp1").geom("geom1").run();

            // ---- spf Inlet -------------------------------------------------
            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            Physics spf = model.component("comp1").physics("spf");
            spf.create("inl1", "Inlet", 1);
            spf.feature("inl1").selection().set(1);
            props("spf.inl1", spf.feature("inl1"));
            allowed("spf.inl1", spf.feature("inl1"), "BoundaryCondition");
            // try the flow-rate variants and report which sticks
            for (String v : new String[]{"MassFlow", "MassFlowRate", "VolumetricFlow",
                                         "FullyDevelopedFlow", "Pressure", "Velocity"}) {
                try {
                    spf.feature("inl1").set("BoundaryCondition", v);
                    System.out.println("SET OK BoundaryCondition = " + v);
                    props("spf.inl1." + v, spf.feature("inl1"));
                } catch (Throwable e) {
                    System.out.println("SET FAIL BoundaryCondition = " + v
                                     + " : " + e.getMessage());
                }
            }

            // ---- spf shallow channel prop group ---------------------------
            System.out.println("=== spf prop groups ===");
            System.out.println(Arrays.toString(spf.prop().tags()));
            try {
                spf.prop("PhysicalModelProperty").set("ShallowChannelProperty", "1");
                spf.prop("PhysicalModelProperty").set("dz", "5[um]");
                System.out.println("SET OK shallow channel");
            } catch (Throwable e) {
                System.out.println("SET FAIL shallow channel : " + e.getMessage());
            }

            // ---- TDS nodes ---------------------------------------------------
            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            Physics tds = model.component("comp1").physics("tds");
            System.out.println("=== TDS default tags ===");
            System.out.println(Arrays.toString(tds.feature().tags()));
            tds.create("inflow1", "Inflow", 1);
            tds.feature("inflow1").selection().set(1);
            props("tds.inflow1", tds.feature("inflow1"));
            tds.create("outflow1", "Outflow", 1);
            props("tds.outflow1", tds.feature("outflow1"));
            // the transport-properties node holding D_c
            for (String tag : tds.feature().tags()) {
                try {
                    String[] pp = tds.feature(tag).properties();
                    if (Arrays.asList(pp).contains("D_c")) {
                        System.out.println("D_c lives on tds." + tag);
                    }
                    if (Arrays.asList(pp).contains("initc")) {
                        System.out.println("initc lives on tds." + tag);
                    }
                } catch (Throwable e) { }
            }

            // ---- mesher property names -------------------------------------
            MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");
            mesh.create("dist1", "Distribution");
            System.out.println("=== mesh.dist1 props ===");
            System.out.println(Arrays.toString(mesh.feature("dist1").properties()));
            try {
                mesh.feature("dist1").set("type", "predefined");
                mesh.feature("dist1").set("elemcount", "20");
                mesh.feature("dist1").set("elemratio", "2");
                System.out.println("SET OK distribution predefined");
            } catch (Throwable e) {
                System.out.println("SET FAIL distribution predefined : " + e.getMessage());
            }
            mesh.create("swe1", "Sweep");
            System.out.println("=== mesh.swe1 props ===");
            System.out.println(Arrays.toString(mesh.feature("swe1").properties()));
            try {
                mesh.feature("swe1").selection().geom("geom1").set(1, 2);
                mesh.feature("swe1").selection().set(1);
                System.out.println("SET OK sweep");
            } catch (Throwable e) {
                System.out.println("SET FAIL sweep : " + e.getMessage());
            }

            System.out.println("PROBE7 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE7 ABORTED: " + e);
        }
    }
}
