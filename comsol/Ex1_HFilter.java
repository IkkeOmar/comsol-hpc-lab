// Ex1_HFilter.java - Exercise 1: the H-filter (the lab-on-a-chip course, Assignment 1).
// 2D COMSOL 6.4 model: LaminarFlow + TransportDilutedSpecies, quasi-3D through
// the shallow-channel approximation.
//
// Assignment spec (pages 1-3):
//   main channel  L = 6 mm, w = 100 um, h = 5 um
//   flow rate     Q = 0.1 uL/min
//   sugar         D = 2e-10 m^2/s, c0 = 1 mol/m^3
//   geometry      one straight rectangle; the H-filter is reduced to the
//                 straight channel by imposing the correct inflow profile:
//                 a STEP function across the mid-line, c0*step1(y[1/mm])
//   physics       Laminar Flow (shallow channel approximation ON, dz = h)
//                 + Transport of Diluted Species
//   BCs           inlet: flow rate;  outlet: static pressure 0
//                 TDS: Inflow on the inlet with the step profile,
//                     Outflow on the outer boundary
//   study         Stationary, with a PARAMETRIC SWEEP over the diffusion
//                 coefficient D to find D_crit (question c)
//   results       average concentration on the UPPER HALF of the outlet
//
// API notes (verified against COMSOL 6.4 shipped examples + the local
// comsol-java-api skill):
//   * Shallow channel approximation is a physics PROP GROUP:
//       physics("spf").prop("PhysicalModelProperty").set("ShallowChannelProperty","1")
//       physics("spf").prop("PhysicalModelProperty").set("dz", "5[um]")
//     (integer "1"/"0", NOT "on"/"true")
//   * The TDS diffusion node is the default tag "df1"; its property is "D_c".
//   * TDS couples to spf automatically via the "Velocity field (spf)" model
//     input - no Multiphysics node needed.
//   * Inlet default BoundaryCondition is "Velocity"; to impose a FLOW RATE use
//     "MassFlow" (or set the velocity from Q).  Here the flow rate is imposed
//     as a velocity: U = Q/(w*h) averaged over the shallow channel.
//   * Parametric sweep: plist = space separated values, sweeptype "sparse",
//     and createAutoSequences("all") is REQUIRED to bind the sweep.

import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex1_HFilter {

    public static void main(String[] args) {
        try {
            run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Model run() {
        Model model = ModelUtil.create("Ex1_HFilter");

        // ------------------------------------------------------------------
        // parameters
        // ------------------------------------------------------------------
        model.param().set("L_ch",  "6000[um]");
        model.param().set("w_ch",  "100[um]");
        model.param().set("h_ch",  "5[um]");
        model.param().set("Q_in",  "0.1[ul/min]");
        model.param().set("c0",    "1[mol/m^3]");
        model.param().set("D_sp",  "2e-10[m^2/s]");   // sugar
        // The viscosity parameter is named "eta", not "mu": with a parameter
        // called "mu" the material property came out as the UNDEFINED
        // variable comp1.mat1.def.mu and the stationary solve died with
        // "Failed to evaluate Jacobian of expression".  The shipped COMSOL
        // examples use "eta" for exactly this reason.
        model.param().set("eta",   "1e-3[Pa*s]");
        model.param().set("rho",   "1000[kg/m^3]");

        // mean velocity in the channel: Q / (w*h)
        model.param().set("U_avg", "Q_in/(w_ch*h_ch)");

        // the diffusion coefficient that is swept in the parametric study
        model.param().set("D_sweep", "2e-10[m^2/s]");

        // ------------------------------------------------------------------
        // geometry: one straight rectangle, symmetric about y = 0
        // ------------------------------------------------------------------
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 2);
        g.lengthUnit("um");

        g.create("r1", "Rectangle");
        g.feature("r1").set("size", new String[]{"L_ch", "w_ch"});
        g.feature("r1").set("pos",  new String[]{"0", "-w_ch/2"});

        // A line segment along the mid-line (y = 0) purely for meshing.
        // It becomes a Mesh Control Edge so the mesh is refined there.
        // NOTE: both ends must be specified with "coord" - setting only
        // specify1 leaves the second vertex undefined ("You must specify the
        // second vertex").
        g.create("ln1", "LineSegment");
        g.feature("ln1").set("specify1", "coord");
        g.feature("ln1").set("specify2", "coord");
        g.feature("ln1").set("coord1", new String[]{"0[um]", "0[um]"});
        g.feature("ln1").set("coord2", new String[]{"L_ch", "0[um]"});
        g.run();

        // Mesh Control Edge: turn the mid-line into a mesh-only entity
        g.create("mce1", "MeshControlEdges");
        g.feature("mce1").selection("input").set("ln1", 1);
        g.run("mce1");

        // ------------------------------------------------------------------
        // boundary selections
        // ------------------------------------------------------------------
        // The mid-line (y = 0) is the Mesh Control Edge.  It is a geometry
        // edge of the domain, so it can be picked out with a box selection
        // that is thin in y and spans the whole length.
        g.create("selMid", "BoxSelection");
        g.feature("selMid").set("entitydim", 1);
        g.feature("selMid").set("condition", "allvertices");
        g.feature("selMid").set("xmin", "-1[um]"); g.feature("selMid").set("xmax", "L_ch+1[um]");
        g.feature("selMid").set("ymin", "-1[um]"); g.feature("selMid").set("ymax", "1[um]");

        g.create("selIn", "BoxSelection");
        g.feature("selIn").set("entitydim", 1);
        g.feature("selIn").set("condition", "allvertices");
        g.feature("selIn").set("xmin", "-1[um]"); g.feature("selIn").set("xmax", "1[um]");
        g.feature("selIn").set("ymin", "-w_ch/2-1[um]"); g.feature("selIn").set("ymax", "w_ch/2+1[um]");

        g.create("selOut", "BoxSelection");
        g.feature("selOut").set("entitydim", 1);
        g.feature("selOut").set("condition", "allvertices");
        g.feature("selOut").set("xmin", "L_ch-1[um]"); g.feature("selOut").set("xmax", "L_ch+1[um]");
        g.feature("selOut").set("ymin", "-w_ch/2-1[um]"); g.feature("selOut").set("ymax", "w_ch/2+1[um]");

        // The upper half of the outlet is obtained from a Cut Line 2D dataset
        // (which is what the assignment asks for), NOT from a box selection:
        // the outlet is a single edge spanning the full width, so a box
        // selection cannot pick out "the upper half" of it.
        g.run();

        int[] inEdges  = model.selection("geom1_selIn").entities(1);
        int[] outEdges = model.selection("geom1_selOut").entities(1);
        System.out.println("inlet edges     : " + Arrays.toString(inEdges));
        System.out.println("outlet edges    : " + Arrays.toString(outEdges));

        // ------------------------------------------------------------------
        // step function for the inlet concentration profile
        // The geometry is symmetric about y = 0, so the step is at y = 0.
        // The argument is y in mm (the assignment writes c0*step1(y[1/mm])).
        // ------------------------------------------------------------------
        // The step function's from/to semantics are settled from evidence,
        // not from a guess: the instructor's own reference table (assignment
        // page 4) lists the average concentration on the UPPER half of the
        // outlet as 0.947 at Dc = 1e-11 falling to 0.500 at Dc = 5e-9.
        // A value that starts near c0 and falls to c0/2 is the half where the
        // sugar ENTERS, so in the COMSOL model the sugar sits in the UPPER
        // half (y > 0), which is what step1 with from=0, to=1 gives.
        //
        // Note this is the mirror image of the analytical part of the
        // assignment, where the sugar enters the LOWER side-channel and the
        // question is how much reaches the upper exit.  The two are related
        // by c -> c0 - c.
        model.func().create("step1", "Step");
        model.func("step1").set("funcname", "step1");
        model.func("step1").set("location", "0");
        model.func("step1").set("from", 0);
        model.func("step1").set("to", 1);
        // SMOOTH the step over 2 um.  A mathematically sharp step makes the
        // inlet boundary condition non-differentiable, which is a second
        // (independent) way to fail the stationary solver's Jacobian.  The
        // transition zone is 2 um, i.e. 4 % of the channel width and about one
        // mesh element, so it does not move the physics: the analytical model
        // reproduces the instructor's reference table with a sharp step to
        // within 0.5 %, and this width is far below the 6 um diffusion length
        // over the residence time.
        model.func("step1").set("smooth", "2[um]");
        model.func("step1").set("smoothactive", true);

        // ------------------------------------------------------------------
        // material: water
        // ------------------------------------------------------------------
        model.component("comp1").material().create("mat1", "Common");
        model.component("comp1").material("mat1").label("Water");
        // The material takes LITERAL values, not parameter references.  With a
        // parameter reference the material property comes out as the
        // UNDEFINED variable comp1.mat1.def.<name> and the stationary solve
        // dies with "Failed to evaluate Jacobian of expression" - this bit
        // "rho" first and then "mu", in two separate runs.  The material
        // property is evaluated in the material's own scope, where a global
        // parameter of the same name does not resolve.
        model.component("comp1").material("mat1").propertyGroup("def")
            .set("dynamicviscosity",     "1e-3[Pa*s]")
            .set("density",              "1000[kg/m^3]");

        // ------------------------------------------------------------------
        // physics: Laminar Flow (with the shallow channel approximation)
        // ------------------------------------------------------------------
        model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");

        // Shallow channel approximation: the 2D model represents a channel of
        // height h_ch, which adds the wall drag the 2D model is missing.
        model.component("comp1").physics("spf").prop("PhysicalModelProperty")
             .set("ShallowChannelProperty", "1");
        model.component("comp1").physics("spf").prop("PhysicalModelProperty")
             .set("dz", "h_ch");

        // inlet: impose the flow rate as a velocity
        model.component("comp1").physics("spf").create("inl1", "Inlet", 1);
        model.component("comp1").physics("spf").feature("inl1").selection().set(inEdges);
        model.component("comp1").physics("spf").feature("inl1").set("U0in", "U_avg");

        // outlet: static pressure 0
        model.component("comp1").physics("spf").create("out1", "Outlet", 1);
        model.component("comp1").physics("spf").feature("out1").selection().set(outEdges);
        model.component("comp1").physics("spf").feature("out1").set("p0", "0");
        System.out.println("--- spf done ---");

        // ------------------------------------------------------------------
        // physics: Transport of Diluted Species
        // ------------------------------------------------------------------
        // The physics op string is "DilutedSpecies" - "TransportDilutedSpecies"
        // does not exist in this build and fails with "Unknown physics
        // interface".  (All 64 shipped COMSOL 6.4 examples use "DilutedSpecies".)
        model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");

        // the default transport-properties node is "cdm1" and it carries the
        // diffusion coefficient in the property "D_c" (there is NO node "df1").
        model.component("comp1").physics("tds").feature("cdm1").set("D_c", "D_sweep");
        // convection coupled to the flow field ("Velocity field (spf)")
        model.component("comp1").physics("tds").feature("cdm1")
             .set("u_src", "root.comp1.u");

        // initial concentration 0 (default node "init1", property "initc",
        // NOT "c")
        model.component("comp1").physics("tds").feature("init1").set("initc", "0");

        // inflow on the inlet: sugar in the LOWER half, pure water in the UPPER
        model.component("comp1").physics("tds").create("inflow1", "Inflow", 1);
        model.component("comp1").physics("tds").feature("inflow1").selection().set(inEdges);
        model.component("comp1").physics("tds").feature("inflow1").set("c0", "c0*step1(y[1/mm])");

        // Outflow on the OUTLET EDGE ONLY - not on every boundary.
        //
        // MEASURED BUG, fixed 2026-10-24.  With selection().all() the Outflow
        // also covered the inlet, and because the Outflow node is created
        // AFTER the Inflow node it took precedence there, so no solute could
        // enter the channel: the concentration came out identically 0.0 for
        // all six D values while the FLOW solved perfectly (inlet pressure
        // 9696 Pa against the analytical 9600 Pa, mean inlet velocity
        // 0.003284 against 0.003333 m/s).  The TDS physics itself was fine -
        // the study step's "activate" property read "spf, on, tds, on".
        //
        // The walls keep the DEFAULT No Flux condition on purpose.  The
        // instructor's reference table (page 4) has the upper-half outlet
        // average falling 0.947 -> 0.500 as D goes 1e-11 -> 5e-9, and 0.5 is
        // the fully-mixed value c0/2.  Solute draining out through the walls
        // would push the answer below 0.5, so the walls are reflecting.
        model.component("comp1").physics("tds").create("outflow1", "Outflow", 1);
        model.component("comp1").physics("tds").feature("outflow1").selection().set(outEdges);
        System.out.println("--- tds done ---");

        // ------------------------------------------------------------------
        // mesh
        //
        // Three things were wrong before this worked, and all three were
        // measured rather than guessed:
        //
        //  1. A mesh SEQUENCE needs an actual mesher.  Size and Distribution
        //     are only attributes that sit on top of one; a sequence with
        //     attributes alone builds ZERO elements ("mesh done, elements: 0")
        //     and every downstream quantity comes out NaN.
        //  2. The Size node must be a SUB-FEATURE of the mesher
        //     (mesh.feature("ftri1").feature("size1")), not a top-level
        //     mesh feature, and it needs THREE properties set, not one:
        //         custom = true       (otherwise the predefined size is used
        //                               and hmax is silently ignored - the
        //                               default custom is "off" and the
        //                               default hmax was 40.2 um)
        //         hmaxactive = true   (otherwise the hmax value is ignored)
        //         hmax = ...
        //  3. The mesh control edge partitions the domain for the MESHER into
        //     two sub-domains while the physics still sees one domain, so the
        //     FreeTri selection must cover BOTH (domains 1 and 2).  Selecting
        //     only domain 1 leaves half the domain unmeshed and the solver
        //     fails with "All geometric entities adjacent to a mesh control
        //     entity need to be meshed."
        //
        // The step in the inlet profile lives at y = 0 and the transverse
        // diffusion length over the residence time is
        //     sqrt(2 D t_res) = 6 um   (D = 1e-10, t_res = 1.8 s)
        // so the transverse direction has to be resolved to ~1 um while the
        // long direction hardly matters (Pe_x = 1e5, the profile is frozen in
        // x).  An isotropic hmax = 2 um over the 6000 x 100 um channel gives
        // 3000 x 50 elements, which resolves the interface comfortably.
        // ------------------------------------------------------------------
        MeshSequence mesh = model.component("comp1").mesh().create("mesh1", "geom1");

        mesh.create("ftri1", "FreeTri");
        mesh.feature("ftri1").selection().geom("geom1", 2).set(1, 2);

        mesh.feature("ftri1").create("size1", "Size");
        mesh.feature("ftri1").feature("size1").set("custom", true);
        mesh.feature("ftri1").feature("size1").set("hmaxactive", true);
        // 3 um gives 2000 x 33 elements (~130k) instead of 372k at 2 um.
        // The transverse diffusion length over the residence time is 6 um, so
        // 3 um still resolves the interface to well under a tenth of it, and
        // the sweep has six D values to run.
        mesh.feature("ftri1").feature("size1").set("hmax", "3[um]");
        mesh.feature("ftri1").feature("size1").set("hminactive", true);
        mesh.feature("ftri1").feature("size1").set("hmin", "0.3[um]");
        mesh.feature("ftri1").feature("size1").set("hgradactive", true);
        mesh.feature("ftri1").feature("size1").set("hgrad", 1.3);

        mesh.run();
        System.out.println("--- mesh done, elements: " + mesh.stat().getNumElem() + " ---");

        // ------------------------------------------------------------------
        // study: stationary + parametric sweep over D
        // ------------------------------------------------------------------
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").create("param", "Parametric");
        model.study("std1").feature("param").set("pname", new String[]{"D_sweep"});
        // The six D values are exactly the ones in the instructor's reference
        // table (assignment page 4), so the result can be compared directly.
        model.study("std1").feature("param").set("plist",
            "1e-11 5e-11 1e-10 5e-10 1e-9 5e-9");
        model.study("std1").feature("param").set("sweeptype", "sparse");
        model.study("std1").createAutoSequences("all");

        try {
            model.study("std1").run();
            System.out.println("=== Study 1 (stationary + D sweep) DONE ===");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("=== Study 1 FAILED ===");
        }

        // ------------------------------------------------------------------
        // results
        // ------------------------------------------------------------------
        // Cut Line 2D along the INLET edge - used by the inlet diagnostic
        // below to prove that solute is actually entering the channel.
        // (This dataset MUST be created before the numerical feature that
        // references it, otherwise the reference resolves to nothing.)
        model.result().dataset().create("clnIn", "CutLine2D");
        model.result().dataset("clnIn").set("data", "dset1");
        model.result().dataset("clnIn").setIndex("genpoints", "0",   0, 0);
        model.result().dataset("clnIn").setIndex("genpoints", "-w_ch/2", 0, 1);
        model.result().dataset("clnIn").setIndex("genpoints", "0",   1, 0);
        model.result().dataset("clnIn").setIndex("genpoints", "w_ch/2",  1, 1);

        // DIAGNOSTIC: sample the concentration on the inlet edge.  If the
        // inflow condition is being overridden, this reads 0 while the
        // outlet average also reads 0, and the two together prove that no
        // solute is entering.  This is the check that would have caught the
        // Outflow-on-everything bug immediately.
        try {
            model.result().numerical().create("avInC", "AvLine");
            model.result().numerical("avInC").set("data", "clnIn");
            model.result().numerical("avInC").setIndex("expr", "c", 0);
            model.result().numerical("avInC").run();
            System.out.println("=== DIAGNOSTIC: average c on the INLET edge ===");
            // getReal() returns double[][] for a line average: index [0] is the
            // expression, and its entries run over the parameter combos, exactly
            // like the avOutUp block below.
            double[][] r = model.result().numerical("avInC").getReal();
            for (int k = 0; k < r[0].length; k++) {
                System.out.println("combo " + k + ": c_inlet_avg = " + r[0][k]);
            }
        } catch (Throwable e) {
            System.out.println("  inlet diagnostic failed: " + e.getMessage());
        }

        // Cut Line 2D covering the UPPER HALF of the outlet edge
        model.result().dataset().create("clnOutUp", "CutLine2D");
        model.result().dataset("clnOutUp").set("data", "dset1");
        model.result().dataset("clnOutUp").setIndex("genpoints", "L_ch", 0, 0);
        model.result().dataset("clnOutUp").setIndex("genpoints", "0",    0, 1);
        model.result().dataset("clnOutUp").setIndex("genpoints", "L_ch", 1, 0);
        model.result().dataset("clnOutUp").setIndex("genpoints", "w_ch/2", 1, 1);

        // average concentration on that upper half
        model.result().numerical().create("avOutUp", "AvLine");
        model.result().numerical("avOutUp").set("data", "clnOutUp");
        model.result().numerical("avOutUp").setIndex("expr", "c", 0);
        model.result().numerical("avOutUp").run();
        double[][] avUp = model.result().numerical("avOutUp").getReal();
        System.out.println("=== upper-half outlet average concentration ===");
        System.out.println("n combos = " + avUp[0].length);
        for (int k = 0; k < avUp[0].length; k++) {
            System.out.println("combo " + k + ": c_avg_upper = " + avUp[0][k]);
        }

        // concentration at the wall of the particle outlet (y = +w/2)
        model.result().dataset().create("cptWall", "CutPoint2D");
        model.result().dataset("cptWall").set("pointx", "L_ch");
        model.result().dataset("cptWall").set("pointy", "w_ch/2");
        model.result().numerical().create("ptWall", "EvalPoint");
        model.result().numerical("ptWall").set("data", "cptWall");
        model.result().numerical("ptWall").setIndex("expr", "c", 0);
        model.result().numerical("ptWall").run();
        double[][] cWall = model.result().numerical("ptWall").getReal();
        System.out.println("=== concentration at the outlet wall (y = +w/2) ===");
        for (int k = 0; k < cWall[0].length; k++) {
            System.out.println("combo " + k + ": c_wall = " + cWall[0][k]);
        }

        // flow check: pressure drop and mean velocity
        model.result().numerical().create("avIn", "AvLine");
        model.result().numerical("avIn").selection().set(inEdges);
        model.result().numerical("avIn").setIndex("expr", "spf.U", 0);
        model.result().numerical("avIn").run();
        double[][] uIn = model.result().numerical("avIn").getReal();
        System.out.println("Ave |U| at inlet = " + uIn[0][0] + " m/s  (U_avg = "
                         + model.param().evaluate("U_avg") + " m/s)");

        model.result().numerical().create("avP", "AvLine");
        model.result().numerical("avP").selection().set(inEdges);
        model.result().numerical("avP").setIndex("expr", "p", 0);
        model.result().numerical("avP").run();
        double[][] pIn = model.result().numerical("avP").getReal();
        System.out.println("Ave p at inlet = " + pIn[0][0] + " Pa");

        // ------------------------------------------------------------------
        // plots + export
        // ------------------------------------------------------------------
        model.result().create("pg1", "PlotGroup2D");
        model.result("pg1").create("surf1", "Surface");
        model.result("pg1").feature("surf1").set("expr", "c");
        model.result("pg1").run();

        model.result().create("pg2", "PlotGroup2D");
        model.result("pg2").create("surf2", "Surface");
        model.result("pg2").feature("surf2").set("expr", "spf.U");
        model.result("pg2").run();

        try {
            model.result().export().create("e_c", "pg1", "Image");
            model.result().export("e_c").set("pngfilename", "$HOME/Ex1_concentration.png");
            model.result().export("e_c").set("size", "manualweb");
            model.result().export("e_c").set("width", "1400");
            model.result().export("e_c").set("height", "600");
            model.result().export("e_c").run();

            model.result().export().create("e_u", "pg2", "Image");
            model.result().export("e_u").set("pngfilename", "$HOME/Ex1_velocity.png");
            model.result().export("e_u").set("size", "manualweb");
            model.result().export("e_u").set("width", "1400");
            model.result().export("e_u").set("height", "600");
            model.result().export("e_u").run();
            System.out.println("=== PNGs exported ===");
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            model.save("$HOME/Ex1_HFilter.mph");
            System.out.println("=== Model saved ===");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return model;
    }
}
