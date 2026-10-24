// Ex4_FFF.java - Exercise 4: field flow fractionation (the lab-on-a-chip course,
// Assignment 1).  3D COMSOL 6.4 model, run on the the course HPC cluster.
//
// Setup (assignment p.11-13):
//   channel      1000 x 100 x 100 um  (x = length, y = width, z = height)
//   electrodes   45 coplanar strips on the floor z = 0, each 10 um wide in x,
//                spanning the full 100 um width in y, 20 um pitch, first left
//                edge at x = 50 um.  Alternating 10 V / 0 V starting with
//                10 V on the leftmost electrode.
//   flow         Q = 1 uL/h
//   particle     d_p = 2.4 um, rho_p = 1200 kg/m^3, eps_r,p = 60,
//                sigma_p = 0.01 S/m
//   medium       water: eps_r,m = 80, sigma_m = 5.5 uS/m
//   AC           10 MHz
//   release      grid at x = 0, z = 50 um, y = 5..95 um in 1 um steps
//
// Physics: LaminarFlow + Electrostatics + FluidParticleTracing, with
// Stokes drag, gravity and the DEP force.  "Newtonian, ignore inertial
// terms" as the assignment requires.
//
// Studies:
//   std1  Stationary (spf) + Frequency (es, 10 MHz)
//   std2  Transient (fpt only), coupled to std1 via "values of variables not
//         solved for" -> 0..120 s, output every 1 s.
//
// API notes (all verified against COMSOL 6.4 shipped examples and the author's
// own working lab-chip_Lecture5/DEP.java - see notes/):
//   * ReleaseGrid is a GLOBAL feature: create(..., "ReleaseGrid", -1).
//     Its initial coordinates are set with setIndex("x0", <range>, <dim>)
//     where dim is 0 for qx0, 1 for qy0, 2 for qz0 (COMSOL stores all three
//     coordinate lists under the single property name "x0").
//   * The DEP force reads the particle permittivity/conductivity from the
//     PARTICLE PROPERTIES node (pp1): erp_mat/erp and sigmap_mat/sigmap.
//     The DEP node itself has no epsr/sigma property.
//   * E_src = "root.comp1.es.Ex"  (component-scoped field component)
//   * UsePPR = true  (piecewise polynomial recovery - REQUIRED by the
//     assignment, and without it the transient never finishes)
//   * The DEP force must have an explicit domain selection.

import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex4_FFF {

    /** Set a property but do not abort the run if the build rejects it. */
    static void setSoft(PhysicsFeature f, String prop, String val) {
        try {
            f.set(prop, val);
            System.out.println("SET OK " + prop + " = " + val);
        } catch (Throwable t) {
            System.out.println("SET FAIL " + prop + " = " + val + " : " + t.getMessage());
        }
    }

    /** Print the allowed values of a property, so the run documents itself. */
    static void reportAllowed(PhysicsFeature f, String[] props) {
        for (String p : props) {
            try {
                System.out.println("ALLOWED " + p + " : "
                                 + Arrays.toString(f.getAllowedPropertyValues(p)));
            } catch (Throwable t) {
                System.out.println("ALLOWED FAIL " + p + " : " + t.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Model run() {
        Model model = ModelUtil.create("Ex4_FFF");

        // ------------------------------------------------------------------
        // parameters
        // ------------------------------------------------------------------
        model.param().set("L_ch",   "1000[um]");
        model.param().set("W_ch",   "100[um]");
        model.param().set("H_ch",   "100[um]");
        model.param().set("N_el",   "45");
        model.param().set("elW",    "10[um]");
        model.param().set("elP",    "20[um]");
        model.param().set("elX0",   "50[um]");
        model.param().set("V_el",   "10[V]");

        model.param().set("Q",      "1[ul/h]");
        model.param().set("U_avg",  "Q/(W_ch*H_ch)");

        model.param().set("dp",     "2.4[um]");
        model.param().set("rho_p",  "1200[kg/m^3]");
        model.param().set("epsr_p", "60");
        model.param().set("sigma_p", "0.01[S/m]");
        model.param().set("epsr_m", "80");
        model.param().set("sigma_m", "5.5[us/m]");

        model.param().set("f_AC",   "10[MHz]");
        model.param().set("g_c",    "9.80665[m/s^2]");

        // release grid
        model.param().set("y_rel0", "5[um]");
        model.param().set("y_rel1", "95[um]");
        model.param().set("y_step", "1[um]");
        model.param().set("z_rel",  "50[um]");

        // ------------------------------------------------------------------
        // geometry
        // ------------------------------------------------------------------
        model.component().create("comp1");
        GeomSequence geomSeq = model.component("comp1").geom().create("geom1", 3);
        geomSeq.lengthUnit("um");

        geomSeq.create("blk1", "Block");
        geomSeq.feature("blk1").set("size", new String[]{"L_ch", "W_ch", "H_ch"});
        geomSeq.feature("blk1").set("pos",  new String[]{"0", "0", "0"});

        // The electrodes live on the floor z = 0 and are coplanar with it, so
        // they are modelled as 2D rectangles in a workplane at z = 0.  Each
        // rectangle splits the floor into separate faces, which is what lets
        // us put a Terminal on the "on" electrodes and a Ground on the rest.
        geomSeq.create("wp1", "WorkPlane");
        geomSeq.feature("wp1").set("planetype", "quick");
        geomSeq.feature("wp1").set("quickplane", "xy");
        GeomSequence wp = geomSeq.feature("wp1").geom();

        // 45 electrodes, 10 um wide, 20 um pitch, first left edge at x = 50 um
        for (int i = 0; i < 45; i++) {
            String tag = "r" + i;
            wp.create(tag, "Rectangle");
            wp.feature(tag).set("size", new String[]{"elW", "W_ch"});
            wp.feature(tag).set("pos",  new String[]{"elX0 + " + i + "*elP", "0"});
        }
        geomSeq.run("wp1");
        geomSeq.run();

        // --- boundary selections -------------------------------------------
        // inlet / outlet faces (x = 0 and x = L_ch)
        geomSeq.create("selIn", "BoxSelection");
        geomSeq.feature("selIn").set("entitydim", 2);
        geomSeq.feature("selIn").set("condition", "allvertices");
        geomSeq.feature("selIn").set("xmin", "-1[um]"); geomSeq.feature("selIn").set("xmax", "1[um]");
        geomSeq.feature("selIn").set("ymin", "-1[um]"); geomSeq.feature("selIn").set("ymax", "W_ch+1[um]");
        geomSeq.feature("selIn").set("zmin", "-1[um]"); geomSeq.feature("selIn").set("zmax", "H_ch+1[um]");

        geomSeq.create("selOut", "BoxSelection");
        geomSeq.feature("selOut").set("entitydim", 2);
        geomSeq.feature("selOut").set("condition", "allvertices");
        geomSeq.feature("selOut").set("xmin", "L_ch-1[um]"); geomSeq.feature("selOut").set("xmax", "L_ch+1[um]");
        geomSeq.feature("selOut").set("ymin", "-1[um]"); geomSeq.feature("selOut").set("ymax", "W_ch+1[um]");
        geomSeq.feature("selOut").set("zmin", "-1[um]"); geomSeq.feature("selOut").set("zmax", "H_ch+1[um]");

        // The 45 electrode faces sit on the floor (z = 0) and are separated by
        // 10 um gaps.  A box around each electrode's x-range picks out the
        // face under it; the floor is z = 0, so the z window is [0, 1] um.
        for (int i = 0; i < 45; i++) {
            String tag = "selE" + i;
            geomSeq.create(tag, "BoxSelection");
            geomSeq.feature(tag).set("entitydim", 2);
            geomSeq.feature(tag).set("condition", "allvertices");
            geomSeq.feature(tag).set("xmin", "elX0 + " + i + "*elP - 1[um]");
            geomSeq.feature(tag).set("xmax", "elX0 + " + i + "*elP + elW + 1[um]");
            geomSeq.feature(tag).set("ymin", "-1[um]");
            geomSeq.feature(tag).set("ymax", "W_ch+1[um]");
            geomSeq.feature(tag).set("zmin", "-1[um]");
            geomSeq.feature(tag).set("zmax", "1[um]");
        }
        // The floor edges (z = 0) - used to refine the mesh along x in the
        // electrode region.
        geomSeq.create("selFloor", "BoxSelection");
        geomSeq.feature("selFloor").set("entitydim", 1);
        geomSeq.feature("selFloor").set("condition", "allvertices");
        geomSeq.feature("selFloor").set("xmin", "-1[um]");
        geomSeq.feature("selFloor").set("xmax", "L_ch+1[um]");
        geomSeq.feature("selFloor").set("ymin", "-1[um]");
        geomSeq.feature("selFloor").set("ymax", "W_ch+1[um]");
        geomSeq.feature("selFloor").set("zmin", "-1[um]");
        geomSeq.feature("selFloor").set("zmax", "1[um]");

        geomSeq.run();

        int[] inletBnd = model.selection("geom1_selIn").entities(2);
        int[] outletBnd = model.selection("geom1_selOut").entities(2);
        int[] floorEdges = model.selection("geom1_selFloor").entities(1);
        System.out.println("inlet  faces: " + Arrays.toString(inletBnd));
        System.out.println("outlet faces: " + Arrays.toString(outletBnd));
        System.out.println("floor  edges: " + Arrays.toString(floorEdges));

        // Collect the electrode faces: even index -> 10 V, odd index -> ground.
        java.util.List<Integer> termFaces = new java.util.ArrayList<Integer>();
        java.util.List<Integer> gndFaces = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 45; i++) {
            int[] sel = model.selection("geom1_selE" + i).entities(2);
            if (sel.length == 0) {
                System.out.println("WARNING: electrode " + i + " selected no face");
                continue;
            }
            System.out.println("electrode " + i + " faces: " + Arrays.toString(sel)
                             + "  -> " + ((i % 2 == 0) ? "10 V" : "GND"));
            if (i % 2 == 0) {
                for (int s : sel) termFaces.add(s);
            } else {
                for (int s : sel) gndFaces.add(s);
            }
        }
        if (termFaces.isEmpty() || gndFaces.isEmpty()) {
            System.out.println("=== Empty electrode selection - aborting ===");
            return model;
        }
        int[] termBnd = new int[termFaces.size()];
        for (int i = 0; i < termBnd.length; i++) termBnd[i] = termFaces.get(i);
        int[] gndBnd = new int[gndFaces.size()];
        for (int i = 0; i < gndBnd.length; i++) gndBnd[i] = gndFaces.get(i);
        System.out.println("Terminal faces: " + termBnd.length
                         + "   Ground faces: " + gndBnd.length);

        // ------------------------------------------------------------------
        // material: water
        // ------------------------------------------------------------------
        model.component("comp1").material().create("mat1", "Common");
        model.component("comp1").material("mat1").label("Water");
        model.component("comp1").material("mat1").propertyGroup("def")
            .set("dynamicviscosity",     "1e-3[Pa*s]")
            .set("density",              "1000[kg/m^3]")
            .set("electricconductivity", "sigma_m")
            .set("relpermittivity",      "epsr_m");

        // ------------------------------------------------------------------
        // physics 1: laminar flow
        // ------------------------------------------------------------------
        model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
        model.component("comp1").physics("spf").selection().all();

        model.component("comp1").physics("spf").create("inl1", "Inlet", 2);
        model.component("comp1").physics("spf").feature("inl1").selection().set(inletBnd);
        // velocity inlet: U_avg = Q/(W*H) is the MEAN velocity, so use the
        // fully-developed profile factor 1.5 for a Poiseuille flow.
        model.component("comp1").physics("spf").feature("inl1")
            .set("U0in", "1.5*U_avg");

        model.component("comp1").physics("spf").create("out1", "Outlet", 2);
        model.component("comp1").physics("spf").feature("out1").selection().set(outletBnd);
        model.component("comp1").physics("spf").feature("out1").set("p0", "0");
        System.out.println("--- spf BCs done ---");

        // ------------------------------------------------------------------
        // physics 2: electrostatics
        // ------------------------------------------------------------------
        model.component("comp1").physics().create("es", "Electrostatics", "geom1");
        model.component("comp1").physics("es").selection().all();

        model.component("comp1").physics("es").create("gnd1", "Ground", 2);
        model.component("comp1").physics("es").feature("gnd1").selection().set(gndBnd);

        model.component("comp1").physics("es").create("term1", "Terminal", 2);
        model.component("comp1").physics("es").feature("term1").selection().set(termBnd);
        model.component("comp1").physics("es").feature("term1").set("TerminalType", "Voltage");
        model.component("comp1").physics("es").feature("term1").set("V0", "V_el");
        System.out.println("--- es BCs done ---");

        // ------------------------------------------------------------------
        // physics 3: particle tracing for fluid flow
        // ------------------------------------------------------------------
        model.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1");
        model.component("comp1").physics("fpt").selection().all();

        // Formulation: Newtonian, ignore inertial terms (assignment requirement)
        model.component("comp1").physics("fpt").prop("Formulation")
             .setIndex("Formulation", "NewtonianIgnoreInertialTerms", 0);

        // Particle properties (pp1 is GLOBAL - entity dim 0, NO selection call).
        // The DEP force reads the particle permittivity and conductivity from
        // HERE, not from the DEP node.
        model.component("comp1").physics("fpt").feature("pp1").set("rhop_mat", "userdef");
        model.component("comp1").physics("fpt").feature("pp1").set("rhop", "rho_p");
        model.component("comp1").physics("fpt").feature("pp1").set("dp", "dp");
        model.component("comp1").physics("fpt").feature("pp1").set("erp_mat", "userdef");
        model.component("comp1").physics("fpt").feature("pp1").set("erp", "epsr_p");
        model.component("comp1").physics("fpt").feature("pp1").set("sigmap_mat", "userdef");
        model.component("comp1").physics("fpt").feature("pp1").set("sigmap", "sigma_p");

        // Stokes drag
        model.component("comp1").physics("fpt").create("df1", "DragForce", 3);
        model.component("comp1").physics("fpt").feature("df1").selection().all();
        model.component("comp1").physics("fpt").feature("df1").set("DragLaw", "Stokes");
        model.component("comp1").physics("fpt").feature("df1").set("u_src", "fromCommonDef");

        // Gravity (buoyancy is handled by the drag force using the fluid
        // density from the material; the explicit gravity force is what the
        // assignment asks for).
        model.component("comp1").physics("fpt").create("gf1", "GravityForce", 3);
        model.component("comp1").physics("fpt").feature("gf1").selection().all();
        model.component("comp1").physics("fpt").feature("gf1")
             .set("g", new String[]{"0", "0", "-g_c"});

        // Dielectrophoretic force.
        // The DEP node takes the FLUID's relative permittivity and
        // conductivity (epsilonr_mat / sigma_mat -> "From material", i.e.
        // epsr_m and sigma_m from the water material), while the PARTICLE's
        // complex permittivity comes from the Particle Properties node
        // (pp1: erp / sigmap).  This is what the shipped
        // dielectrophoretic_separation.java example does.
        model.component("comp1").physics("fpt").create("deff1", "DielectrophoreticForce", 3);
        model.component("comp1").physics("fpt").feature("deff1").selection().all();
        model.component("comp1").physics("fpt").feature("deff1").set("E_src", "root.comp1.es.Ex");
        model.component("comp1").physics("fpt").feature("deff1").set("UsePPR", true);
        model.component("comp1").physics("fpt").feature("deff1").set("AngularFrequency", "FromSolution");
        // fluid permittivity / conductivity: from the water material
        setSoft(model.component("comp1").physics("fpt").feature("deff1"), "epsilonr_mat", "from_mat");
        setSoft(model.component("comp1").physics("fpt").feature("deff1"), "sigma_mat", "from_mat");
        // report what the build actually allows, so the run documents itself
        reportAllowed(model.component("comp1").physics("fpt").feature("deff1"),
                      new String[]{"epsilonr_mat", "sigma_mat", "epsilonr", "sigma"});
        System.out.println("--- fpt forces done ---");

        // Wall.  On this build (6.4 build 293) the FluidParticleTracing wall
        // only offers "Freeze", "Stick" and "Disappear" - there is no
        // "Bounce", because the "Newtonian, ignore inertial terms"
        // formulation drops the particle momentum that a bounce would need.
        // "Freeze" stops a particle that reaches a wall; it stays in the
        // domain and can still be counted.
        model.component("comp1").physics("fpt").feature("wall1").set("WallCondition", "Freeze");

        // Release from Grid - GLOBAL feature, created with dimension -1.
        // The initial coordinates are set through the single property "x0",
        // indexed by space dimension: 0 -> qx0, 1 -> qy0, 2 -> qz0.
        model.component("comp1").physics("fpt").create("relg1", "ReleaseGrid", -1);
        model.component("comp1").physics("fpt").feature("relg1")
             .setIndex("x0", "0[um]", 0);                       // qx0
        model.component("comp1").physics("fpt").feature("relg1")
             .setIndex("x0", "range(y_rel0, y_step, y_rel1)", 1);  // qy0
        model.component("comp1").physics("fpt").feature("relg1")
             .setIndex("x0", "z_rel", 2);                      // qz0
        // release at t = 0
        model.component("comp1").physics("fpt").feature("relg1").setIndex("rt", "0", 0);
        // particles start at rest; the Stokes drag accelerates them to the
        // local fluid velocity (an "Expression" velocity with spf.u cannot be
        // evaluated at particle positions during the transient solve)
        model.component("comp1").physics("fpt").feature("relg1")
             .set("InitialVelocity", "ConstantSpeedLambertian");
        model.component("comp1").physics("fpt").feature("relg1")
             .set("v0", new String[]{"0", "0", "0"});
        System.out.println("--- fpt release done ---");

        // Particles that reach the outlet are removed from the simulation.
        // The Outlet node's WallCondition offers only "Freeze" and "Disappear"
        // on this build; "Disappear" removes the particle, which is what we
        // want at an outlet.
        model.component("comp1").physics("fpt").create("outfpt1", "Outlet", 2);
        model.component("comp1").physics("fpt").feature("outfpt1").selection().set(outletBnd);
        model.component("comp1").physics("fpt").feature("outfpt1").set("WallCondition", "Disappear");

        // ------------------------------------------------------------------
        // mesh
        // The DEP field gradient |grad E|^2 decays as exp(-pi y/d) with
        // d = 10 um, i.e. a decay length of d/pi = 3.2 um, and the electrode
        // pitch is 20 um.  A uniform "Normal" mesh would smear out exactly the
        // gradient that sets the equilibrium height, so the floor region is
        // refined and the mesh is swept along x.
        // ------------------------------------------------------------------
        MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");

        // global size: 4 um in the floor region is ~3 elements per decay length
        mesh.create("size1", "Size");
        mesh.feature("size1").set("hmax", "2[um]");
        mesh.feature("size1").set("hmin", "0.5[um]");
        mesh.feature("size1").set("hgrad", 1.25);

        // refine along x in the electrode region so the 20 um pitch is resolved
        mesh.create("dist1", "Distribution");
        mesh.feature("dist1").selection().geom(1).set(floorEdges);
        mesh.feature("dist1").set("numelem", 100);

        mesh.run();
        System.out.println("--- mesh done, elements: " + mesh.stat().getNumElem() + " ---");

        // ------------------------------------------------------------------
        // studies
        // ------------------------------------------------------------------
        // std1: stationary flow + frequency-domain electrostatics
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").feature("stat").setSolveFor("/physics/es", false);
        model.study("std1").feature("stat").setSolveFor("/physics/fpt", false);
        model.study("std1").create("freq", "Frequency");
        model.study("std1").feature("freq").setSolveFor("/physics/spf", false);
        model.study("std1").feature("freq").setSolveFor("/physics/fpt", false);
        model.study("std1").feature("freq").set("plist", "f_AC");
        model.study("std1").createAutoSequences("all");

        // std2: transient particle tracing, coupled to std1
        model.study().create("std2");
        model.study("std2").create("td", "Transient");
        model.study("std2").feature("td").setSolveFor("/physics/spf", false);
        model.study("std2").feature("td").setSolveFor("/physics/es", false);
        model.study("std2").feature("td").set("tlist", "range(0,1,120)");
        // "values of variables not solved for" -> Solution, Study 1
        model.study("std2").feature("td").set("usesol", true);
        model.study("std2").feature("td").set("notsolmethod", "sol");
        model.study("std2").feature("td").set("notstudy", "std1");
        model.study("std2").createAutoSequences("sol");
        model.study("std2").createAutoSequences("all");

        // ------------------------------------------------------------------
        // solve
        // ------------------------------------------------------------------
        try {
            model.study("std1").run();
            System.out.println("=== Study 1 (spf + es) DONE ===");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("=== Study 1 FAILED ===");
            try { model.save("$HOME/Ex4_std1.mph"); } catch (Exception se) { se.printStackTrace(); }
            return model;
        }
        try {
            model.study("std2").run();
            System.out.println("=== Study 2 (fpt transient) DONE ===");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("=== Study 2 FAILED ===");
        }

        // ------------------------------------------------------------------
        // results
        // ------------------------------------------------------------------
        // mean velocity in the channel, to verify the flow rate
        model.result().numerical().create("avU", "AvSurface");
        model.result().numerical("avU").selection().set(inletBnd);
        model.result().numerical("avU").setIndex("expr", "spf.U", 0);
        model.result().numerical("avU").run();
        double[][] uIn = model.result().numerical("avU").getReal();
        System.out.println("Ave |U| at inlet = " + uIn[0][0] + " m/s");
        System.out.println("Expected U_avg  = " + model.param().evaluate("U_avg") + " m/s");
        System.out.println("Implied Q = " + uIn[0][0] * 1e-8 * 3.6e9 + " uL/h"
                         + "  (1.5*U_avg*2/3 = mean over the section)");

        // particle trajectories
        model.result().dataset().create("pdat", "Particle");
        model.result().dataset("pdat").set("solution", "sol2");
        model.result().dataset("pdat").set("posdof",
            new String[]{"comp1.qx", "comp1.qy", "comp1.qz"});
        model.result().dataset("pdat").set("geom", "geom1");
        model.result().dataset("pdat").set("pgeom", "pgeom_fpt");
        model.result().dataset("pdat").set("pgeomspec", "fromphysics");
        model.result().dataset("pdat").set("physicsinterface", "fpt");

        model.result().create("pgTraj", "PlotGroup3D");
        model.result("pgTraj").set("data", "pdat");
        model.result("pgTraj").create("traj1", "ParticleTrajectories");
        model.result("pgTraj").feature("traj1").set("pointtype", "point");
        model.result("pgTraj").feature("traj1").set("linetype", "line");
        model.result("pgTraj").run();

        // x-z projection of the trajectories
        model.result().dataset().create("cplXZ", "CutPlane3D");
        model.result().dataset("cplXZ").set("data", "pdat");
        model.result().dataset("cplXZ").set("quickplane", "zx");
        model.result().dataset("cplXZ").set("quickslice", "y=50[um]");
        model.result().create("pgXZ", "PlotGroup2D");
        model.result("pgXZ").create("surfXZ", "Surface");
        model.result("pgXZ").feature("surfXZ").set("data", "cplXZ");
        model.result("pgXZ").feature("surfXZ").set("expr", "comp1.qz");
        model.result("pgXZ").run();

        // electric field magnitude
        model.result().create("pgE", "PlotGroup3D");
        model.result("pgE").create("slc1", "Slice");
        model.result("pgE").feature("slc1").set("expr", "es.normE");
        model.result("pgE").feature("slc1").set("quickplane", "zx");
        model.result("pgE").feature("slc1").set("quickslice", "y=50[um]");
        model.result("pgE").run();

        // vertical profile of |E|^2 at the gap centre, to compare with the
        // analytical (32/pi) V0^2/d^3 exp(-pi y/d)
        model.result().dataset().create("clnE", "CutLine3D");
        model.result().dataset("clnE").set("data", "dset1");
        model.result().dataset("clnE").setIndex("genpoints", "60[um]", 0, 0);
        model.result().dataset("clnE").setIndex("genpoints", "50[um]", 0, 1);
        model.result().dataset("clnE").setIndex("genpoints", "0[um]",  0, 2);
        model.result().dataset("clnE").setIndex("genpoints", "60[um]", 1, 0);
        model.result().dataset("clnE").setIndex("genpoints", "50[um]", 1, 1);
        model.result().dataset("clnE").setIndex("genpoints", "100[um]", 1, 2);
        model.result().create("pgEprof", "PlotGroup1D");
        model.result("pgEprof").create("lngrE", "LineGraph");
        model.result("pgEprof").feature("lngrE").set("data", "clnE");
        model.result("pgEprof").feature("lngrE").set("expr", "es.normE");
        model.result("pgEprof").feature("lngrE").set("xdata", "arc");
        model.result("pgEprof").run();

        // ------------------------------------------------------------------
        // export
        // ------------------------------------------------------------------
        try {
            model.result().export().create("e_traj", "pgXZ", "Image");
            model.result().export("e_traj").set("pngfilename", "$HOME/Ex4_trajectories_xz.png");
            model.result().export("e_traj").set("size", "manualweb");
            model.result().export("e_traj").set("width", "1400");
            model.result().export("e_traj").set("height", "800");
            model.result().export("e_traj").run();

            model.result().export().create("e_3d", "pgTraj", "Image");
            model.result().export("e_3d").set("pngfilename", "$HOME/Ex4_trajectories_3d.png");
            model.result().export("e_3d").set("size", "manualweb");
            model.result().export("e_3d").set("width", "1400");
            model.result().export("e_3d").set("height", "800");
            model.result().export("e_3d").run();

            model.result().export().create("e_E", "pgE", "Image");
            model.result().export("e_E").set("pngfilename", "$HOME/Ex4_Efield.png");
            model.result().export("e_E").set("size", "manualweb");
            model.result().export("e_E").set("width", "1400");
            model.result().export("e_E").set("height", "800");
            model.result().export("e_E").run();

            model.result().export().create("e_Eprof", "pgEprof", "Image");
            model.result().export("e_Eprof").set("pngfilename", "$HOME/Ex4_Eprofile.png");
            model.result().export("e_Eprof").set("size", "manualweb");
            model.result().export("e_Eprof").set("width", "1200");
            model.result().export("e_Eprof").set("height", "600");
            model.result().export("e_Eprof").run();

            // particle positions -> CSV for the Python analysis
            model.result().numerical().create("ptab", "Particle");
            model.result().numerical("ptab").set("data", "pdat");
            model.result().numerical("ptab").setIndex("expr", "comp1.qx", 0);
            model.result().numerical("ptab").setIndex("expr", "comp1.qy", 1);
            model.result().numerical("ptab").setIndex("expr", "comp1.qz", 2);
            model.result().export().create("csv1", "ptab", "Data");
            model.result().export("csv1").set("data", "ptab");
            model.result().export("csv1").set("filename", "$HOME/Ex4_particles.csv");
            model.result().export("csv1").run();
            System.out.println("=== Particle CSV exported ===");
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            model.save("$HOME/Ex4_FFF.mph");
            System.out.println("=== Model saved ===");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return model;
    }
}
