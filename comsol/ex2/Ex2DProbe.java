/* Ex2DProbe.java -- why does the part-D geometry (60 mm wide inlet face)
 * fail with a Singular matrix in the stationary flow solve?
 *
 * The base-case geometry (2 mm wide inlet face) solves fine with the same
 * MassFlowRate inlet.  The wide distributor face (60 mm x 1 mm) gives
 * "void equations for comp1.p at the inlet face" and "void equations for
 * comp1.spf.inl1.Pmf" -- i.e. the global mass-flow constraint equation
 * comes out empty.
 *
 * Variants tested (all on the part-D geometry unless stated):
 *   A  MassFlowRate on the 60 mm wide face                       (baseline)
 *   B  MassFlowRate on a 2 mm wide neck in front of the distributor
 *   C  Normal inflow velocity on the 60 mm wide face
 *   D  MassFlowRate on the 60 mm wide face, but with the "fully developed
 *      flow" option switched off / on
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2DProbe {

    static Model model;

    static void p(String s) { System.out.println(s); }

    static int[] cat(int[] a, int[] b) {
        int[] r = new int[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    /** part-D geometry; neckLen > 0 adds a 2 mm wide neck in front of the
     *  distributor and puts the inlet on the neck. */
    static void buildD(String tag, double neckLen) {
        model = ModelUtil.create(tag);
        model.component().create("comp1");
        model.param().set("D_sp", "200[um^2/s]");
        model.param().set("Q_in", "100[ul/min]");
        model.param().set("rho", "1000[kg/m^3]");

        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blkCh", "Block");
        g.feature("blkCh").set("size", new String[]{"60", "60", "1"});
        g.feature("blkCh").set("pos", new String[]{"-30", "-30", "0"});
        g.create("blkWD", "Block");
        g.feature("blkWD").set("size", new String[]{"4", "60", "1"});
        g.feature("blkWD").set("pos", new String[]{"-34", "-30", "0"});
        g.create("blkED", "Block");
        g.feature("blkED").set("size", new String[]{"4", "60", "1"});
        g.feature("blkED").set("pos", new String[]{"30", "-30", "0"});
        double inX = -34.0;
        if (neckLen > 0) {
            g.create("blkNeck", "Block");
            g.feature("blkNeck").set("size", new String[]{"" + neckLen, "2", "1"});
            g.feature("blkNeck").set("pos", new String[]{"" + (-34.0 - neckLen), "-1", "0"});
            inX = -34.0 - neckLen;
        }
        g.run();
        g.create("selCham", "BoxSelection");
        g.feature("selCham").set("entitydim", 3);
        g.feature("selCham").set("condition", "inside");
        g.feature("selCham").set("xmin", "-30.1"); g.feature("selCham").set("xmax", "30.1");
        g.feature("selCham").set("ymin", "-30.1"); g.feature("selCham").set("ymax", "30.1");
        g.feature("selCham").set("zmin", "-0.05"); g.feature("selCham").set("zmax", "1.2");
        g.create("selDuctW", "BoxSelection");
        g.feature("selDuctW").set("entitydim", 3);
        g.feature("selDuctW").set("condition", "inside");
        g.feature("selDuctW").set("xmin", "-34.1"); g.feature("selDuctW").set("xmax", "-29.9");
        g.feature("selDuctW").set("ymin", "-30.1"); g.feature("selDuctW").set("ymax", "30.1");
        g.feature("selDuctW").set("zmin", "-0.05"); g.feature("selDuctW").set("zmax", "1.2");
        g.create("selDuctE", "BoxSelection");
        g.feature("selDuctE").set("entitydim", 3);
        g.feature("selDuctE").set("condition", "inside");
        g.feature("selDuctE").set("xmin", "29.9"); g.feature("selDuctE").set("xmax", "34.1");
        g.feature("selDuctE").set("ymin", "-30.1"); g.feature("selDuctE").set("ymax", "30.1");
        g.feature("selDuctE").set("zmin", "-0.05"); g.feature("selDuctE").set("zmax", "1.2");
        g.create("selIn", "BoxSelection");
        g.feature("selIn").set("entitydim", 2);
        g.feature("selIn").set("condition", "allvertices");
        g.feature("selIn").set("xmin", "" + (inX - 0.1)); g.feature("selIn").set("xmax", "" + (inX + 0.1));
        g.feature("selIn").set("ymin", "-30.1"); g.feature("selIn").set("ymax", "30.1");
        g.feature("selIn").set("zmin", "-0.2"); g.feature("selIn").set("zmax", "1.2");
        g.create("selOut", "BoxSelection");
        g.feature("selOut").set("entitydim", 2);
        g.feature("selOut").set("condition", "allvertices");
        g.feature("selOut").set("xmin", "33.9"); g.feature("selOut").set("xmax", "34.1");
        g.feature("selOut").set("ymin", "-30.1"); g.feature("selOut").set("ymax", "30.1");
        g.feature("selOut").set("zmin", "-0.2"); g.feature("selOut").set("zmax", "1.2");
        g.create("selZ0", "BoxSelection");
        g.feature("selZ0").set("entitydim", 2);
        g.feature("selZ0").set("condition", "allvertices");
        g.feature("selZ0").set("xmin", "-75.1"); g.feature("selZ0").set("xmax", "75.1");
        g.feature("selZ0").set("ymin", "-30.1"); g.feature("selZ0").set("ymax", "30.1");
        g.feature("selZ0").set("zmin", "-0.005"); g.feature("selZ0").set("zmax", "0.005");
        g.create("selZ1", "BoxSelection");
        g.feature("selZ1").set("entitydim", 2);
        g.feature("selZ1").set("condition", "allvertices");
        g.feature("selZ1").set("xmin", "-75.1"); g.feature("selZ1").set("xmax", "75.1");
        g.feature("selZ1").set("ymin", "-30.1"); g.feature("selZ1").set("ymax", "30.1");
        g.feature("selZ1").set("zmin", "0.995"); g.feature("selZ1").set("zmax", "1.005");
        g.create("selVert", "BoxSelection");
        g.feature("selVert").set("entitydim", 1);
        g.feature("selVert").set("condition", "inside");
        g.feature("selVert").set("xmin", "-75.1"); g.feature("selVert").set("xmax", "75.1");
        g.feature("selVert").set("ymin", "-30.1"); g.feature("selVert").set("ymax", "30.1");
        g.feature("selVert").set("zmin", "-0.05"); g.feature("selVert").set("zmax", "1.05");
        g.run();

        int[] cham = model.selection("geom1_selCham").entities(3);
        int[] dw = model.selection("geom1_selDuctW").entities(3);
        int[] de = model.selection("geom1_selDuctE").entities(3);
        int[] inF = model.selection("geom1_selIn").entities(2);
        int[] outF = model.selection("geom1_selOut").entities(2);
        int[] z0 = model.selection("geom1_selZ0").entities(2);
        int[] z1 = model.selection("geom1_selZ1").entities(2);
        int[] vert = model.selection("geom1_selVert").entities(1);
        p(tag + " domains=" + g.getNDomains() + " cham=" + Arrays.toString(cham)
          + " ductW=" + Arrays.toString(dw) + " ductE=" + Arrays.toString(de)
          + " in=" + Arrays.toString(inF) + " out=" + Arrays.toString(outF)
          + " z0=" + Arrays.toString(z0) + " z1=" + Arrays.toString(z1));

        model.component("comp1").material().create("mat1", "Common");
        model.component("comp1").material("mat1").propertyGroup("def")
             .set("dynamicviscosity", "1e-3[Pa*s]").set("density", "1000[kg/m^3]");
        try { model.component("comp1").material("mat1").selection().all(); } catch (Throwable t) { }

        model.component("comp1").mesh().create("mesh1", "geom1");
        model.component("comp1").mesh("mesh1").create("sizeG", "Size");
        model.component("comp1").mesh("mesh1").feature("sizeG").set("hmax", "2.5");
        model.component("comp1").mesh("mesh1").feature("sizeG").set("hmin", "0.05");
        model.component("comp1").mesh("mesh1").feature("sizeG").set("hgrad", 1.25);
        model.component("comp1").mesh("mesh1").create("sizeDuct", "Size");
        model.component("comp1").mesh("mesh1").feature("sizeDuct").selection().geom("geom1", 3)
             .set(cat(dw, de));
        model.component("comp1").mesh("mesh1").feature("sizeDuct").set("hmax", "0.4");
        model.component("comp1").mesh("mesh1").feature("sizeDuct").set("hmin", "0.05");
        model.component("comp1").mesh("mesh1").create("ftri1", "FreeTri");
        model.component("comp1").mesh("mesh1").feature("ftri1").selection().geom(2).set(z0);
        model.component("comp1").mesh("mesh1").create("swe1", "Sweep");
        model.component("comp1").mesh("mesh1").feature("swe1").selection().geom(3)
             .set(cat(cham, cat(dw, de)));
        model.component("comp1").mesh("mesh1").feature("swe1").selection("sourceface").geom(2).set(z0);
        model.component("comp1").mesh("mesh1").feature("swe1").selection("targetface").geom(2).set(z1);
        model.component("comp1").mesh("mesh1").create("dis1", "Distribution");
        model.component("comp1").mesh("mesh1").feature("dis1").selection().geom("geom1", 1).set(vert);
        model.component("comp1").mesh("mesh1").feature("dis1").set("numelem", "6");
        model.component("comp1").mesh("mesh1").run();
        p(tag + " MESH numElem="
          + model.component("comp1").mesh("mesh1").stat().getNumElem());

        model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
        model.component("comp1").physics("spf").selection().geom("geom1", 3)
             .set(cat(cham, cat(dw, de)));
        model.component("comp1").physics("spf").create("inl1", "Inlet", 2);
        model.component("comp1").physics("spf").feature("inl1").selection().set(inF);
        model.component("comp1").physics("spf").create("out1", "Outlet", 2);
        model.component("comp1").physics("spf").feature("out1").selection().set(outF);
        model.component("comp1").physics("spf").feature("out1").set("p0", "0");
    }


    public static void main(String[] args) {
        try {
            buildD("PROBE", 0.0);
            PhysicsFeature inl = model.component("comp1").physics("spf").feature("inl1");
            p("PROBE inl1 properties = " + Arrays.toString(inl.properties()).replace(", ", ",\n   "));
            for (String prop : new String[]{"BoundaryCondition", "MassFlowType", "U0", "u0",
                                           "Uav", "u0f", "NormalFlow", "FullyDevelopedFlow",
                                           "InletType", "flowType"}) {
                try {
                    p("PROBE allowed[" + prop + "] = " + Arrays.toString(inl.getAllowedPropertyValues(prop)));
                } catch (Throwable t) {
                    p("PROBE allowed[" + prop + "] -> " + t.getMessage());
                }
            }
            // try setting a uniform velocity in different ways
            for (String bc : new String[]{"NormalInflowVelocity", "NormalInflow", "Velocity",
                                          "MassFlow", "UniformVelocity", "InflowVelocity"}) {
                try {
                    inl.set("BoundaryCondition", bc);
                    p("PROBE set BoundaryCondition=" + bc + " OK");
                } catch (Throwable t) {
                    p("PROBE set BoundaryCondition=" + bc + " FAILED: " + t.getMessage());
                }
            }
            // with MassFlow, what are the MassFlowType options?
            try {
                inl.set("BoundaryCondition", "MassFlow");
                p("PROBE MassFlowType allowed = " + Arrays.toString(inl.getAllowedPropertyValues("MassFlowType")));
                p("PROBE MassFlowType current = " + inl.getString("MassFlowType"));
            } catch (Throwable t) { p("PROBE MassFlowType failed: " + t.getMessage()); }
            // with BoundaryCondition = Velocity: which property carries the
            // velocity vector?
            try {
                inl.set("BoundaryCondition", "Velocity");
                p("PROBE Velocity properties = " + Arrays.toString(inl.properties()).replace(", ", ",\n   "));
                for (String prop : new String[]{"u0", "U0", "u", "Uin", "u0f", "Ut", "Un",
                                               "VelocityType", "SpecifyVelocity", "FlowType",
                                               "BoundaryCondition", "FullyDevelopedFlow"}) {
                    try {
                        p("PROBE Velocity allowed[" + prop + "] = " + Arrays.toString(inl.getAllowedPropertyValues(prop)));
                    } catch (Throwable t) {
                        p("PROBE Velocity allowed[" + prop + "] -> " + t.getMessage());
                    }
                }
                // u0 is a 3-vector; set a uniform -x velocity (inflow)
                try {
                    inl.set("u0", new String[]{"-Q_in/(60[mm]*1[mm])", "0", "0"});
                    p("PROBE Velocity set u0 (vector) OK");
                    p("PROBE readback u0 = " + Arrays.toString(inl.getStringArray("u0")));
                } catch (Throwable t) {
                    p("PROBE Velocity set u0 (vector) FAILED: " + t.getMessage());
                }
                // solve the stationary flow and read back the total flow rate
                try {
                    model.study().create("std1");
                    model.study("std1").create("stat", "Stationary");
                    model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
                    model.study("std1").feature("stat").setSolveFor("/physics/tds", false);
                    model.study("std1").createAutoSequences("all");
                    model.study("std1").run();
                    p("PROBE stationary flow SOLVED");
                    model.result().numerical().create("isIn", "IntSurface");
                    model.result().numerical("isIn").selection()
                         .set(model.selection("geom1_selIn").entities(2));
                    model.result().numerical("isIn").setIndex("expr", "u*nx+v*ny+w*nz", 0);
                    model.result().numerical("isIn").run();
                    double q = -model.result().numerical("isIn").getReal()[0][0];
                    p("PROBE Q_in through the 60 mm face = " + q + " m^3/s = " + (q * 6e10) + " uL/min");
                    // mean |u| over the chamber
                    model.result().numerical().create("avU", "AvVolume");
                    model.result().numerical("avU").selection()
                         .set(model.selection("geom1_selCham").entities(3));
                    model.result().numerical("avU").setIndex("expr", "spf.U", 0);
                    model.result().numerical("avU").run();
                    p("PROBE mean |u| in the chamber = "
                      + model.result().numerical("avU").getReal()[0][0] + " m/s");
                    // uniformity across the width: mean |u| in the y=25 mm strip
                    model.result().numerical().create("avU2", "AvVolume");
                    model.result().numerical("avU2").selection()
                         .set(model.selection("geom1_selStrip").entities(3));
                    model.result().numerical("avU2").setIndex("expr", "spf.U", 0);
                    model.result().numerical("avU2").run();
                    p("PROBE mean |u| in the y-strip = "
                      + model.result().numerical("avU2").getReal()[0][0] + " m/s");
                } catch (Throwable t) {
                    p("PROBE solve failed: " + t.getMessage());
                }
            } catch (Throwable t) { p("PROBE Velocity block failed: " + t.getMessage()); }
            p("EX2DPROBE DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("EX2DPROBE FAILED: " + e);
        }
        p("EX2DPROBE EXIT");
    }
}
