import com.comsol.model.*;
import com.comsol.model.util.*;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Probe 2 (reflection build): enumerate the ACTUAL property names and allowed
 *  values via properties() / getAllowedPropertyValues().  Reflection is used
 *  because the COMSOL API return types differ between physics() and
 *  physics().feature() and differ again across releases.
 */
public class Probe4c {

    static Method meth(Object o, String name) {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        for (Class<?> i : o.getClass().getInterfaces()) {
            try {
                return i.getMethod(name);
            } catch (NoSuchMethodException e) { /* keep looking */ }
        }
        return null;
    }

    static void dumpProps(String label, Object f) {
        try {
            Method m = meth(f, "properties");
            if (m == null) { System.out.println("PROPS " + label + " : NO properties() METHOD"); return; }
            String[] p = (String[]) m.invoke(f);
            System.out.println("PROPS " + label + " (" + (p == null ? 0 : p.length) + "): "
                    + Arrays.toString(p));
        } catch (Throwable e) {
            System.out.println("PROPS FAIL " + label + " : " + e);
        }
    }

    static void dumpAllowed(String label, Object f, String prop) {
        try {
            Method m = meth(f, "getAllowedPropertyValues");
            if (m == null) { System.out.println("ALLOWED " + label + "." + prop + " : NO METHOD"); return; }
            String[] v = (String[]) m.invoke(f, prop);
            System.out.println("ALLOWED " + label + "." + prop + " : " + Arrays.toString(v));
        } catch (Throwable e) {
            System.out.println("ALLOWED FAIL " + label + "." + prop + " : " + e);
        }
    }

    static void dumpTags(String label, Object list) {
        try {
            Method m = meth(list, "tags");
            if (m == null) { System.out.println("TAGS " + label + " : NO METHOD"); return; }
            Object r = m.invoke(list);
            System.out.println("TAGS " + label + " : " + (r instanceof String[]
                    ? Arrays.toString((String[]) r) : String.valueOf(r)));
        } catch (Throwable e) {
            System.out.println("TAGS FAIL " + label + " : " + e);
        }
    }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("Probe4c");
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

            System.out.println("=== FPT feature tags ===");
            dumpTags("fpt", model.component("comp1").physics("fpt").feature());

            dumpProps("fpt", model.component("comp1").physics("fpt"));
            for (String tag : model.component("comp1").physics("fpt").feature().tags()) {
                dumpProps("fpt." + tag, model.component("comp1").physics("fpt").feature(tag));
            }

            model.component("comp1").physics("fpt").create("deff1", "DielectrophoreticForce", 3);
            model.component("comp1").physics("fpt").feature("deff1").selection().all();
            dumpProps("fpt.deff1", model.component("comp1").physics("fpt").feature("deff1"));
            for (String cand : new String[]{"E_src", "UsePPR", "epsr", "epso", "epsf",
                                           "sigma", "sigmam", "sigmap", "epsilon",
                                           "ParticlesToAffect", "AffectedParticleProperties",
                                           "Epso", "Epsr", "Sigmap", "Sigmam"}) {
                dumpAllowed("deff1", model.component("comp1").physics("fpt").feature("deff1"), cand);
            }

            String[] cands = {"ReleaseGrid", "ReleaseFromGrid", "Grid", "ReleaseFromBoundary",
                              "Release", "Inlet", "ReleaseFromPoint", "ReleaseFromEdge"};
            for (String c : cands) {
                try {
                    model.component("comp1").physics("fpt").create("rel_" + c, c, 0);
                    System.out.println("RELFEAT OK (dim0): " + c);
                    dumpProps("fpt.rel_" + c, model.component("comp1").physics("fpt").feature("rel_" + c));
                } catch (Throwable e) {
                    System.out.println("RELFEAT FAIL (dim0): " + c + " : " + e.getMessage());
                }
            }
            for (String c : new String[]{"ReleaseGrid", "ReleaseFromGrid", "Grid",
                                         "ReleaseFromBoundary", "Release", "Inlet"}) {
                try {
                    model.component("comp1").physics("fpt").create("relb_" + c, c, 2);
                    System.out.println("RELFEAT OK (dim2): " + c);
                    dumpProps("fpt.relb_" + c, model.component("comp1").physics("fpt").feature("relb_" + c));
                } catch (Throwable e) {
                    System.out.println("RELFEAT FAIL (dim2): " + c + " : " + e.getMessage());
                }
            }

            model.component("comp1").physics("fpt").create("df1", "DragForce", 3);
            model.component("comp1").physics("fpt").feature("df1").selection().all();
            dumpProps("fpt.df1", model.component("comp1").physics("fpt").feature("df1"));
            dumpAllowed("df1", model.component("comp1").physics("fpt").feature("df1"), "u_src");

            model.component("comp1").physics("fpt").create("gf1", "GravityForce", 3);
            model.component("comp1").physics("fpt").feature("gf1").selection().all();
            dumpProps("fpt.gf1", model.component("comp1").physics("fpt").feature("gf1"));

            model.component("comp1").physics("es").create("term1", "Terminal", 2);
            model.component("comp1").physics("es").feature("term1").selection().set(1);
            dumpProps("es.term1", model.component("comp1").physics("es").feature("term1"));

            System.out.println("PROBE2 DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            System.out.println("PROBE2 ABORTED: " + e);
        }
    }
}
