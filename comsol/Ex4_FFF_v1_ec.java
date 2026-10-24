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
// ---------------------------------------------------------------------------
// API FACTS - all measured on THIS build (COMSOL 6.4.0.429) or read from the
// shipped examples; none of them are guessed.  See notes/ex4_comsol_README.md.
//
//  * ReleaseGrid is a GLOBAL feature: create(..., "ReleaseGrid", -1).
//    Its initial coordinates live in the SINGLE property "x0", a vector of
//    length 3 (one entry per space dimension).  ProbeAPI3 measured:
//      set("qx0", ...)            -> Unknown_parameter_X#qx0
//      set("x0", "1[um]")        -> A_vector_of_length_X_expected#3
//    and ProbeAPI2 read back
//      x0 = [0[um], range(5[um],1[um],95[um]), 50[um]]
//    so setIndex("x0", <expr>, 0|1|2) is the correct call, and the three
//    entries are qx0, qy0, qz0.  GridType = "AllCombinations" (default)
//    with x0 = {0}, y0 = {5..95}, z0 = {50} gives 91 particles.
//
//  * The DEP force reads the PARTICLE permittivity/conductivity from the
//    Particle Properties node (pp1: erp_mat/erp, sigmap_mat/sigmap), and
//    the FLUID permittivity/conductivity from its own epsilonr_mat /
//    sigma_mat properties (both "from_mat" -> the water material).
//
//  * E_src = "root.comp1.ec.Ex" is ACCEPTED and auto-fills deff1.E with
//    the field vector (measured: deff1.E = [0,0,0] immediately after the
//    call, and the shipped dielectrophoretic_separation.java uses the
//    same pattern with ec: set("E_src", "root.comp1.ec.EX")).
//
//  * UsePPR = true  (piecewise polynomial recovery - REQUIRED by the
//    assignment, and it makes the coarse-mesh gradient usable).
//
//  * The DEP node must have an explicit domain selection.
//
//  * GravityForce: the manual gives Fg = mp*g*(rho_p - rho_f)/rho_p, i.e.
//    the fluid density IS the buoyancy reference.  ALLOWED rho_mat =
//    [from_mat, root.comp1.spf.rho, userdef]  -> use root.comp1.spf.rho.
//
//  * "values of variables not solved for":  use "usesol"=true,
//    "notsolmethod"="sol", "notstudy"="std1"  (from the shipped example).
//
//  * Mesh: a hand-built mesh sequence MUST contain an explicit mesher
//    feature (FreeTet); Size+Distribution alone yields 0 elements.  A
//    uniform hmax = 2 um over the whole 1000x100x100 um block is hopeless
//    (measured: 0 elements).  The graded mesh below is measured to give
//    72 953 elements with min quality 0.095.
//
//  * Extraction: the "Particle" numerical feature works with
//    set("expr", "<string>") - setIndex("expr", ...) fails because expr is
//    a STRING property, not a vector.  The transient solution is the one
//    whose solver carries a "t1" (Time) feature.

import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.io.*;
import java.util.Arrays;

public class Ex4_FFF_v1_ec {

    // ---- run-time configuration -------------------------------------------
    // The COMSOL security manager blocks System.getenv, so the run mode is a
    // COMPILE-TIME constant.  build_ex4.py rewrites the __QUICK_MARKER__
    // line to produce the "quick" (API smoke test) and "full" (production)
    // variants.

    static final boolean QUICK = false;  // __QUICK_MARKER__

    static final int    N_EL    = QUICK ? 9  : 45;
    static final double L_CH_UM = QUICK ? 300.0 : 1000.0;
    static final String TLIST   = QUICK ? "range(0,1,4)"   : "range(0,1,120)";
    // y positions of the released particles: the assignment asks for
    // 5..95 um in 1 um steps (91 particles).  The QUICK variant uses
    // 5,15,...,95 (9 particles) to keep the smoke test fast.
    static final String Y_REL   = QUICK ? "range(5[um],10[um],95[um])"
                                        : "range(5[um],1[um],95[um])";
    static final String FIELD_EXPR = "ec.normE";
    static final String OUT     = "./v1_ec";

    /** Set a property but do not abort the run if the build rejects it. */
    static void setSoft(PhysicsFeature f, String prop, String val) {
        try {
            f.set(prop, val);
            System.out.println("SET OK " + prop + " = " + val);
        } catch (Throwable t) {
            System.out.println("SET FAIL " + prop + " = " + val + " : " + t.getMessage());
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
        System.out.println("=== VARIANT V1_EC_CONDUCTIVE_MEDIA ===");
        System.out.println("=== Ex4_FFF config: quick=" + QUICK + " N_EL=" + N_EL
                + " L_ch=" + L_CH_UM + "um  OUT=" + OUT + " ===");
        Model model = ModelUtil.create("Ex4_FFF");

        // ------------------------------------------------------------------
        // parameters
        // ------------------------------------------------------------------
        model.param().set("L_ch",   L_CH_UM + "[um]");
        model.param().set("W_ch",   "100[um]");
        model.param().set("H_ch",   "100[um]");
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
        geomSeq.feature("wp1").set("unite", true);
        GeomSequence wp = geomSeq.feature("wp1").geom();

        for (int i = 0; i < N_EL; i++) {
            String tag = "r" + i;
            wp.create(tag, "Rectangle");
            wp.feature(tag).set("size", new String[]{"elW", "W_ch"});
            wp.feature(tag).set("pos",  new String[]{"elX0 + " + i + "*elP", "0"});
        }
        geomSeq.run("wp1");
        geomSeq.run();

        // --- boundary selections -------------------------------------------
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

        for (int i = 0; i < N_EL; i++) {
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
        // The floor edges (z = 0).  Used to force the x-resolution on the
        // electrode plane, where the DEP field varies on the 20 um pitch.
        geomSeq.create("selFloor", "BoxSelection");
        geomSeq.feature("selFloor").set("entitydim", 1);
        geomSeq.feature("selFloor").set("condition", "allvertices");
        geomSeq.feature("selFloor").set("xmin", "-1[um]"); geomSeq.feature("selFloor").set("xmax", "L_ch+1[um]");
        geomSeq.feature("selFloor").set("ymin", "-1[um]"); geomSeq.feature("selFloor").set("ymax", "W_ch+1[um]");
        geomSeq.feature("selFloor").set("zmin", "-1[um]"); geomSeq.feature("selFloor").set("zmax", "1[um]");

        geomSeq.run();

        int[] inletBnd = model.selection("geom1_selIn").entities(2);
        int[] outletBnd = model.selection("geom1_selOut").entities(2);
        int[] floorEdges = model.selection("geom1_selFloor").entities(1);
        System.out.println("inlet  faces: " + Arrays.toString(inletBnd));
        System.out.println("outlet faces: " + Arrays.toString(outletBnd));
        System.out.println("floor  edges: " + floorEdges.length);

        java.util.List<Integer> termFaces = new java.util.ArrayList<Integer>();
        java.util.List<Integer> gndFaces = new java.util.ArrayList<Integer>();
        for (int i = 0; i < N_EL; i++) {
            int[] sel = model.selection("geom1_selE" + i).entities(2);
            if (sel.length == 0) {
                System.out.println("WARNING: electrode " + i + " selected no face");
                continue;
            }
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
        // Fully developed flow with the MEAN velocity U_avg = Q/(W*H): the
        // developed Poiseuille profile of a square duct then carries exactly
        // the requested volume flow.  (A plain velocity inlet with U_avg
        // would also carry Q, but the profile would start as a plug and
        // develop downstream - the fully developed inlet is what the
        // analytical comparison assumes.)
        model.component("comp1").physics("spf").feature("inl1")
            .set("BoundaryCondition", "LaminarInflow");
        model.component("comp1").physics("spf").feature("inl1")
            .set("LaminarInflowOption", "Uav");
        model.component("comp1").physics("spf").feature("inl1")
            .set("Uav", "U_avg");

        model.component("comp1").physics("spf").create("out1", "Outlet", 2);
        model.component("comp1").physics("spf").feature("out1").selection().set(outletBnd);
        model.component("comp1").physics("spf").feature("out1").set("p0", "0");
        System.out.println("--- spf BCs done ---");

        // ------------------------------------------------------------------
        // physics 2: electrostatics
        // ------------------------------------------------------------------
        // The default "Free Space" domain node of the Electrostatics
        // interface assumes eps_r = 1 (vacuum), which is physically wrong
        // for DEP in water, so it is replaced by a ChargeConservation node
        // that takes eps_r from the material.  (Removing the default node
        // raises "Object_cannot_be_removed" - measured - so it is left in
        // place and deactivated by the ChargeConservation node covering the
        // same domain.)
        model.component("comp1").physics().create("ec", "ConductiveMedia", "geom1");
        // The default domain node of ConductiveMedia (cucns1) already takes
        // eps_r and sigma from the material.  Print its properties so the
        // variant documents that it really is reading the water material.
        try {
            String[] dn = model.component("comp1").physics("ec").feature().tags();
            System.out.println("EC default features: " + java.util.Arrays.toString(dn));
            for (String d : dn) {
                System.out.println("EC feature " + d + " props: "
                    + java.util.Arrays.toString(
                        model.component("comp1").physics("ec").feature(d).properties()));
            }
        } catch (Throwable t) {
            System.out.println("EC feature probe failed: " + t.getMessage());
        }

        model.component("comp1").physics("ec").create("gnd1", "Ground", 2);
        model.component("comp1").physics("ec").feature("gnd1").selection().set(gndBnd);

        model.component("comp1").physics("ec").create("term1", "Terminal", 2);
        model.component("comp1").physics("ec").feature("term1").selection().set(termBnd);
        model.component("comp1").physics("ec").feature("term1").set("TerminalType", "Voltage");
        model.component("comp1").physics("ec").feature("term1").set("V0", "V_el");
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

        // Stokes drag against the local fluid velocity
        model.component("comp1").physics("fpt").create("df1", "DragForce", 3);
        model.component("comp1").physics("fpt").feature("df1").selection().all();
        model.component("comp1").physics("fpt").feature("df1").set("DragLaw", "Stokes");
        model.component("comp1").physics("fpt").feature("df1").set("u_src", "root.comp1.u");

        // Gravity with buoyancy.  The manual gives
        //   Fg = mp*g*(rho_p - rho_f)/rho_p
        // so the fluid density is what makes the net force (rho_p - rho_m)*V*g
        // that the analytical equilibrium uses.
        model.component("comp1").physics("fpt").create("gf1", "GravityForce", 3);
        model.component("comp1").physics("fpt").feature("gf1").selection().all();
        model.component("comp1").physics("fpt").feature("gf1")
             .set("g", new String[]{"0", "0", "-g_c"});
        model.component("comp1").physics("fpt").feature("gf1")
             .set("rho_mat", "root.comp1.spf.rho");

        // Dielectrophoretic force.
        model.component("comp1").physics("fpt").create("deff1", "DielectrophoreticForce", 3);
        model.component("comp1").physics("fpt").feature("deff1").selection().all();
        // The shipped example (com.comsol.help.models.particle.dielectrophoretic_separation)
        // uses root.comp1.ec.EX with a capital X; the Electrostatics production
        // model used root.comp1.es.Ex.  Try both and report which one works.
        try {
            model.component("comp1").physics("fpt").feature("deff1")
                 .set("E_src", "root.comp1.ec.Ex");
            System.out.println("SET OK E_src = root.comp1.ec.Ex");
        } catch (Throwable t) {
            System.out.println("SET FAIL E_src = root.comp1.ec.Ex : " + t.getMessage());
            model.component("comp1").physics("fpt").feature("deff1")
                 .set("E_src", "root.comp1.ec.EX");
            System.out.println("SET OK E_src = root.comp1.ec.EX (capital X fallback)");
        }
        model.component("comp1").physics("fpt").feature("deff1").set("UsePPR", true);
        // omega is set by hand: the field comes from the frequency-domain step
        // of study 1, but study 2 only sees it through the "variables not
        // solved for" coupling, so "From solution" would look in the wrong
        // place.  2*pi*10 MHz.
        model.component("comp1").physics("fpt").feature("deff1").set("AngularFrequency", "UserDefined");
        model.component("comp1").physics("fpt").feature("deff1").set("omega", "2*pi*f_AC");
        // fluid permittivity / conductivity: from the water material
        setSoft(model.component("comp1").physics("fpt").feature("deff1"), "epsilonr_mat", "from_mat");
        setSoft(model.component("comp1").physics("fpt").feature("deff1"), "sigma_mat", "from_mat");
        System.out.println("--- fpt forces done ---");

        // Wall.  On this build the FluidParticleTracing wall only offers
        // "Freeze", "Stick" and "Disappear" (measured), because the
        // "Newtonian, ignore inertial terms" formulation drops the particle
        // momentum a bounce would need.  "Freeze" keeps the particle in the
        // domain so it can still be counted.
        model.component("comp1").physics("fpt").feature("wall1").set("WallCondition", "Freeze");

        // Particles that reach the outlet are removed from the simulation.
        model.component("comp1").physics("fpt").create("outfpt1", "Outlet", 2);
        model.component("comp1").physics("fpt").feature("outfpt1").selection().set(outletBnd);
        model.component("comp1").physics("fpt").feature("outfpt1").set("WallCondition", "Disappear");

        // Release from Grid - GLOBAL feature, created with dimension -1.
        // The three coordinate lists live in the single property "x0",
        // indexed by space dimension: 0 -> qx0, 1 -> qy0, 2 -> qz0.
        model.component("comp1").physics("fpt").create("relg1", "ReleaseGrid", -1);
        model.component("comp1").physics("fpt").feature("relg1")
             .setIndex("x0", "0[um]", 0);            // qx0
        model.component("comp1").physics("fpt").feature("relg1")
             .setIndex("x0", Y_REL, 1);               // qy0 = 5..95 um
        model.component("comp1").physics("fpt").feature("relg1")
             .setIndex("x0", "z_rel", 2);             // qz0 = 50 um
        model.component("comp1").physics("fpt").feature("relg1").set("GridType", "AllCombinations");
        // release at t = 0
        model.component("comp1").physics("fpt").feature("relg1").setIndex("rt", "0", 0);
        // particles start at rest; the Stokes drag accelerates them to the
        // local fluid velocity (an "Expression" velocity with spf.u cannot
        // be evaluated at particle positions during the transient solve)
        model.component("comp1").physics("fpt").feature("relg1")
             .set("InitialVelocity", "ConstantSpeedLambertian");
        model.component("comp1").physics("fpt").feature("relg1")
             .set("v0", new String[]{"0", "0", "0"});
        System.out.println("--- fpt release done ---");

        // ------------------------------------------------------------------
        // mesh
        // ------------------------------------------------------------------
        // The DEP field gradient |grad E|^2 decays as exp(-pi y/d) with
        // d = 10 um, i.e. a decay length of d/pi = 3.2 um, and the electrode
        // pitch is 20 um.  A uniform "Normal" mesh would smear out exactly the
        // gradient that sets the equilibrium height.
        //
        // Strategy (measured):
        //   * global Size: coarse in the flow-only bulk
        //   * Distribution on the floor edges: 5 elements per floor edge
        //     gives 2 um spacing along x on the electrode plane (the floor
        //     edges are 10 um long in x) and 20 um along y - the field is
        //     essentially y-invariant, so y can stay coarse
        //   * FreeTet: a hand-built sequence with no mesher feature produces
        //     0 elements (measured)
        MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");

        mesh.create("size1", "Size");
        mesh.feature("size1").set("hmax", "20[um]");
        mesh.feature("size1").set("hmin", "0.5[um]");
        mesh.feature("size1").set("hgrad", 1.30);
        mesh.feature("size1").set("hcurve", 0.4);
        mesh.feature("size1").set("hnarrow", 1);

        mesh.create("dist1", "Distribution");
        mesh.feature("dist1").selection().geom(1).set(floorEdges);
        mesh.feature("dist1").set("numelem", 5);

        mesh.create("ftet1", "FreeTet");
        long t0 = System.currentTimeMillis();
        mesh.run();
        System.out.println("=== mesh done, elements: " + mesh.stat().getNumElem()
                + "  minQ: " + mesh.stat().getMinQuality()
                + "  meanQ: " + mesh.stat().getMeanQuality()
                + "  [" + (System.currentTimeMillis() - t0) + " ms] ===");
        if (mesh.stat().getNumElem() == 0) {
            System.out.println("=== EMPTY MESH - aborting ===");
            return model;
        }

        // ------------------------------------------------------------------
        // studies
        // ------------------------------------------------------------------
        // std1: stationary flow + frequency-domain electrostatics
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").feature("stat").setSolveFor("/physics/ec", false);
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
        model.study("std2").feature("td").setSolveFor("/physics/ec", false);
        model.study("std2").feature("td").set("tlist", TLIST);
        // "values of variables not solved for" -> Solution, Study 1
        model.study("std2").feature("td").set("usesol", true);
        model.study("std2").feature("td").set("notsolmethod", "sol");
        model.study("std2").feature("td").set("notstudy", "std1");
        // ONE createAutoSequences call only: calling it twice created a DECOY
        // solver (tags became [sol1, sol3, sol2]; the transient ran in sol3
        // while the particle dataset picked sol2).
        model.study("std2").createAutoSequences("all");

        // ------------------------------------------------------------------
        // solve
        // ------------------------------------------------------------------
        try {
            t0 = System.currentTimeMillis();
            model.study("std1").run();
            System.out.println("=== Study 1 (spf + es) DONE [" + (System.currentTimeMillis()-t0) + " ms] ===");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("=== Study 1 FAILED ===");
            try { model.save(OUT + "/Ex4_std1.mph"); } catch (Exception se) { se.printStackTrace(); }
            return model;
        }

        // flow rate check: the volumetric flux through the outlet must be
        // the requested Q = 1 uL/h.
        try {
            model.result().numerical().create("intQ", "IntSurface");
            model.result().numerical("intQ").selection().set(outletBnd);
            model.result().numerical("intQ").setIndex("expr", "spf.U*(-nx)", 0);
            model.result().numerical("intQ").run();
            double[][] q = model.result().numerical("intQ").getReal();
            double Q = q[0][0];
            System.out.println("FLOWCHECK outlet flux = " + Q + " m^3/s = "
                    + (Q * 3.6e9) + " uL/h   (target 1 uL/h)");
            System.out.println("FLOWCHECK U_avg nominal = " + model.param().evaluate("U_avg")
                    + " m/s");
        } catch (Throwable e) {
            System.out.println("=== flow check failed: " + e.getMessage() + " ===");
        }

        try {
            t0 = System.currentTimeMillis();
            model.study("std2").run();
            System.out.println("=== Study 2 (fpt transient) DONE [" + (System.currentTimeMillis()-t0) + " ms] ===");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("=== Study 2 FAILED ===");
        }

        // ------------------------------------------------------------------
        // results: particle trajectories
        // ------------------------------------------------------------------
        // The solution tag must be the one COMSOL actually created for study
        // 2.  A time-dependent solver carries a "t1" (Time) feature; a
        // stationary one carries "s1".  This identifies study 2's solver
        // unambiguously.
        String[] solTags = model.sol().tags();
        String std2Sol = "";
        for (String t : solTags) {
            if (Arrays.asList(model.sol(t).feature().tags()).contains("t1")) {
                std2Sol = t;
                break;
            }
        }
        if (std2Sol.isEmpty()) std2Sol = solTags[solTags.length - 1];
        System.out.println("solver tags = " + Arrays.toString(solTags)
                         + "  -> particle dataset uses " + std2Sol);

        model.result().dataset().create("pdat", "Particle");
        model.result().dataset("pdat").set("solution", std2Sol);
        model.result().dataset("pdat").set("posdof",
            new String[]{"comp1.qx", "comp1.qy", "comp1.qz"});
        model.result().dataset("pdat").set("geom", "geom1");
        model.result().dataset("pdat").set("pgeom", "pgeom_fpt");
        model.result().dataset("pdat").set("pgeomspec", "fromphysics");
        model.result().dataset("pdat").set("physicsinterface", "fpt");

        // The "Particle" numerical feature evaluates an expression on the
        // particle dataset.  expr is a STRING property (setIndex fails).
        // The result comes back as [nParticles][nTimes].
        // The particle CSV is written by COMSOL's Data export: java.io
        // writes are BLOCKED by the COMSOL security manager (measured:
        // "access denied (java.io.FilePermission ...)").
        try {
            model.result().export().create("ePart", "pdat", "Data");
            model.result().export("ePart").set("expr",
                new String[]{"comp1.qx", "comp1.qy", "comp1.qz"});
            model.result().export("ePart").set("descr",
                new String[]{"x_um", "y_um", "z_um"});
            model.result().export("ePart").set("filename", OUT + "/Ex4_particles.csv");
            model.result().export("ePart").set("fullprec", true);
            model.result().export("ePart").run();
            System.out.println("=== particle CSV exported ===");
        } catch (Throwable e) {
            System.out.println("=== particle export failed: " + e.getMessage() + " ===");
        }

        // Numerical cross-check of the particle extraction.  The rows are
        // printed because the CSV export does not label the columns.
        try {
            String[] comps = {"comp1.qx", "comp1.qy", "comp1.qz"};
            String[] names = {"qx", "qy", "qz"};
            double[][][] all = new double[3][][];
            for (int k = 0; k < 3; k++) {
                String tag = "pev_" + names[k];
                model.result().numerical().create(tag, "Particle");
                model.result().numerical(tag).set("data", "pdat");
                model.result().numerical(tag).set("expr", comps[k]);
                model.result().numerical(tag).run();
                all[k] = model.result().numerical(tag).getReal();
                System.out.println("PART " + names[k] + ": rows=" + all[k].length
                        + " (particles)  cols=" + (all[k].length > 0 ? all[k][0].length : 0)
                        + " (times)");
            }
            int nPart = all[0].length;
            int nTime = (nPart > 0) ? all[0][0].length : 0;
            System.out.println("PARTDATA nPart=" + nPart + " nTime=" + nTime);
            if (nPart > 0) {
                // first particle's trajectory, one line per time step
                for (int c = 0; c < nTime; c++) {
                    System.out.println("PARTROW " + 0 + " " + c + " "
                            + (all[0][0][c] * 1e6) + " "
                            + (all[1][0][c] * 1e6) + " "
                            + (all[2][0][c] * 1e6));
                }
                System.out.println("PARTLAST " + (nPart - 1) + " "
                        + (all[0][nPart-1][nTime-1] * 1e6) + " "
                        + (all[1][nPart-1][nTime-1] * 1e6) + " "
                        + (all[2][nPart-1][nTime-1] * 1e6));

                // the mid-channel particle: the one the report analyses
                int mid = (nPart - 1) / 2;
                System.out.println("PARTMID " + mid);
                for (int c = 0; c < nTime; c++) {
                    System.out.println("PARTMIDROW " + mid + " " + c + " "
                            + (all[0][mid][c] * 1e6) + " "
                            + (all[1][mid][c] * 1e6) + " "
                            + (all[2][mid][c] * 1e6));
                }


            }
        } catch (Throwable e) {
            System.out.println("=== particle numerical failed: " + e + " ===");
        }

        // ------------------------------------------------------------------
        // results: the electric field (for the independent validation)
        // ------------------------------------------------------------------
        // Export the frequency-domain solution with |E|^2 and the field
        // components, so the vertical profile at the first gap centre and
        // the first electrode centre can be extracted in Python.  One export
        // per dataset; the frequency-domain solution is the one that carries
        // the es variables.
        for (String ds : model.result().dataset().tags()) {
            try {
                model.result().export().create("eF_" + ds, ds, "Data");
                model.result().export("eF_" + ds).set("expr",
                    new String[]{"ec.Ex^2+ec.Ey^2+ec.Ez^2", "ec.Ex", "ec.Ey", "ec.Ez"});
                model.result().export("eF_" + ds).set("descr",
                    new String[]{"E2", "Ex", "Ey", "Ez"});
                model.result().export("eF_" + ds).set("filename",
                    OUT + "/Ex4_field_" + ds + ".csv");
                model.result().export("eF_" + ds).set("fullprec", true);
                model.result().export("eF_" + ds).run();
                System.out.println("=== field export OK for dataset " + ds + " ===");
            } catch (Throwable e) {
                System.out.println("=== field export failed for " + ds + ": "
                        + e.getMessage() + " ===");
            }
        }


        // ------------------------------------------------------------------
        // COMSOL's own picture of the device: the geometry rendered from
        // BELOW, so the 45 coplanar electrode faces on the floor become
        // visible (a plain 3D render shows a featureless block).
        // The camera API is verified in the shipped example
        // com.comsol.help.models.acdc.induction_heating_curie_movement.
        // ------------------------------------------------------------------
        try {
            String vt = "view1";
            try {
                String[] vtags = model.component("comp1").view().tags();
                System.out.println("VIEW TAGS: " + java.util.Arrays.toString(vtags));
                if (vtags.length > 0) vt = vtags[0];
            } catch (Throwable t) { }
            String ge = "geompic";
            model.result().export().create(ge, "Image");
            model.result().export(ge).set("sourcetype", "geometry");
            model.result().export(ge).set("sourceobject", "geom1");
            model.result().export(ge).set("imagetype", "png");
            model.result().export(ge).set("pngfilename", OUT + "/ex4_device_from_below.png");
            model.result().export(ge).set("target", "file");
            model.result().export(ge).set("size", "manualweb");
            model.result().export(ge).set("width", "1600");
            model.result().export(ge).set("height", "1000");
            model.result().export(ge).set("lockratio", "off");
            try { model.result().export(ge).set("options3d", "on"); }
            catch (Throwable t) { System.out.println("SET FAIL options3d: " + t.getMessage()); }
            try { model.result().export(ge).set("fontsize", "13"); }
            catch (Throwable t) { System.out.println("SET FAIL fontsize: " + t.getMessage()); }
            try { model.result().export(ge).set("background", "color"); }
            catch (Throwable t) { System.out.println("SET FAIL background: " + t.getMessage()); }
            try { model.result().export(ge).set("customcolor", new double[]{1.0, 1.0, 1.0}); }
            catch (Throwable t) { System.out.println("SET FAIL customcolor: " + t.getMessage()); }
            try { model.result().export(ge).set("zoomextents", "on"); }
            catch (Throwable t) { System.out.println("SET FAIL zoomextents: " + t.getMessage()); }
            try {
                model.component("comp1").view(vt).camera()
                     .set("position", new double[]{500, 50, -700});
                model.component("comp1").view(vt).camera()
                     .set("target", new double[]{500, 50, 0});
                model.component("comp1").view(vt).camera()
                     .set("up", new double[]{0, 1, 0});
                System.out.println("CAMERA SET OK on view " + vt);
            } catch (Throwable t) {
                System.out.println("CAMERA SET FAIL: " + t.getMessage());
            }
            model.result().export(ge).run();
            System.out.println("=== device picture from below exported ===");
        } catch (Throwable t) {
            System.out.println("=== device picture export failed: " + t.getMessage() + " ===");
        }

        // ------------------------------------------------------------------
        // The solved electric field on the mid-plane y = 50 um, so the
        // electrode pattern shows up in the field.
        // ------------------------------------------------------------------
        try {
            String pg = "pgSlice";
            model.result().create(pg, "PlotGroup3D");
            model.result(pg).set("data", "dset1");
            model.result(pg).create("sl1", "Slice");
            model.result(pg).feature("sl1").set("expr", FIELD_EXPR);
            try { model.result(pg).feature("sl1").set("quickplane", "yz"); }
            catch (Throwable t) { System.out.println("SET FAIL quickplane: " + t.getMessage()); }
            try { model.result(pg).feature("sl1").set("quickxnumber", 1); }
            catch (Throwable t) { System.out.println("SET FAIL quickxnumber: " + t.getMessage()); }
            try { model.result(pg).feature("sl1").set("quickx", "50[um]"); }
            catch (Throwable t) { System.out.println("SET FAIL quickx: " + t.getMessage()); }
            try { model.result(pg).set("titletype", "manual"); }
            catch (Throwable t) { System.out.println("SET FAIL titletype: " + t.getMessage()); }
            try { model.result(pg).set("title", "Ex4 in COMSOL: " + FIELD_EXPR + " on the mid-plane y = 50 um"); }
            catch (Throwable t) { System.out.println("SET FAIL title: " + t.getMessage()); }
            model.result(pg).run();
            String se = "eslice";
            model.result().export().create(se, pg, "Image");
            model.result().export(se).set("pngfilename", OUT + "/ex4_efield_slice.png");
            model.result().export(se).set("target", "file");
            model.result().export(se).set("size", "manualweb");
            model.result().export(se).set("width", "1600");
            model.result().export(se).set("height", "1000");
            model.result().export(se).set("lockratio", "off");
            try { model.result().export(se).set("options3d", "on"); }
            catch (Throwable t) { System.out.println("SET FAIL options3d: " + t.getMessage()); }
            try { model.result().export(se).set("fontsize", "13"); }
            catch (Throwable t) { System.out.println("SET FAIL fontsize: " + t.getMessage()); }
            model.result().export(se).run();
            System.out.println("=== field slice exported ===");
        } catch (Throwable t) {
            System.out.println("=== field slice export failed: " + t.getMessage() + " ===");
        }
        try {
            model.save(OUT + "/Ex4_FFF.mph");
            System.out.println("=== Model saved ===");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return model;
    }
}
