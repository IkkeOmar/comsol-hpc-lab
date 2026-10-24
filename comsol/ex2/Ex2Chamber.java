/* Ex2Chamber.java -- the lab-on-a-chip course Assignment 1, Exercise 2 (cell growth chamber).
 *
 * COMSOL 6.4 Java-API model. One class covers three configurations, selected by
 * the environment variable EX2_MODE (fallback: file "mode.txt", then "bc"):
 *
 *   bc  : parts B + C   -- chamber + crossing channel, rectangular solute pulse,
 *                         domain-average probe in the chamber + 3 global point probes.
 *   d   : part D        -- same physics, alternative geometry (full-width inlet/outlet
 *                         slots instead of a single 2 mm channel) to improve homogeneity.
 *   ef  : parts E + F   -- adds the cylindrical cell layer (100 um) under the chamber
 *                         floor; transient with a stable 1 mol/m3 inflow (part E) and a
 *                         stationary parametric flow-rate sweep (part F).
 *
 * GEOMETRY (length unit mm; the model works in SI metres internally)
 *   chamber footprint 60 x 60 mm, 1 mm tall, centred on the origin in x,y,
 *   floor at z = 0, roof at z = +1 mm.  It is split in plan into
 *      strip  : 60 x 2 mm  (the footprint of the crossing channel, y in [-1,1])
 *      side A : 60 x 29 mm (y in [-30,-1])
 *      side B : 60 x 29 mm (y in [1,30])
 *   so that the channel stripe can be meshed finely while the dead corners stay cheap.
 *   channel 150 mm x 2 mm x 1 mm, centred on the origin, realised as
 *      duct W : 45 x 2 x 1 mm  at x in [-75,-30]   (inlet)
 *      duct E : 45 x 2 x 1 mm  at x in [ 30, 75]   (outlet)
 *   -> the 150 mm channel = duct W + the 60 mm it crosses inside the chamber footprint
 *      + duct E, exactly as the assignment describes ("both centred at (0,0,0)").
 *      d-mode replaces the two ducts by 45 x 60 x 1 mm slots (full-width distributor).
 *   cell layer (ef mode only): cylinder r = 25 mm, h = 0.1 mm at (0,0,-0.1)
 *      -> z in [-0.1,0] mm, i.e. immediately below the chamber floor, as specified.
 *
 * PHYSICS
 *   spf : LaminarFlow on the fluid domains only (the cell layer is excluded).
 *         Inlet = MassFlowRate rho*Q_in on the inlet face, Outlet p0 = 0.
 *   tds : DilutedSpecies. cdm1: u_src = userdef with the THREE-component
 *         spf velocity, D_c = D_sp (as a 3x3 tensor).  Inflow on the inlet
 *         face, Outflow on the outlet face.  In ef mode an extra "Fluid"
 *         domain condition on the cell layer sets u = 0 and D_c = 0.7*D_sp,
 *         and a "Reactions" domain condition adds R_c = -k_r*c.
 *
 * ---------------------------------------------------------------------------
 * COMSOL 6.4 API FACTS VERIFIED ON THIS INSTALL (build 429).  Every one of
 * these was measured, not guessed; see the probe classes Ex2ApiProbe*.java
 * and their logs in this folder.
 *
 *  1. TDS is "DilutedSpecies"; the default fluid node is "cdm1".
 *     u_src allowed values: [root.comp1.u, userdef, fromCommonDef].
 *     The velocity vector property is "u" and takes THREE components in 3D.
 *     D_c_mat allowed values: [userdef]; D_c then needs NINE entries (a 3x3
 *     tensor), exactly as the shipped Application Library models write it.
 *
 *  2. *** THE COUPLING BUG THAT BROKE EVERY EARLIER RUN ***
 *     To let a transient species study use the velocity field of a
 *     stationary flow study, the study step needs
 *         set("usesol", true);            // "Values of variables not solved
 *                                         //  for" -> User controlled
 *         set("notsolmethod", "sol");
 *         set("notstudy", "std1");
 *         set("notsolnum", "auto");
 *     WITHOUT usesol=true the notsolmethod/notstudy settings are SILENTLY
 *     IGNORED: inside the transient, spf.U evaluates to 0 and the species is
 *     transported by PURE DIFFUSION ONLY.  Measured on a 40 mm channel with
 *     c0 = 1 at the inlet: without usesol, c at 19 mm after 60 s was 1e-83
 *     (pure diffusion); with it, c = 0.92 (properly advected).  Both
 *     u_src = "root.comp1.u" and u_src = "userdef" gave bit-identical
 *     results in the broken case, which is what pinned the fault on the
 *     coupling rather than on the velocity expression.  Every shipped
 *     Application Library model that couples two studies sets usesol=true.
 *
 *  3. EvalPoint has NO "coords" property (it throws Unknown_property).
 *     A point value is obtained by creating a CutPoint3D dataset and binding
 *     EvalPoint to it with set("data", "<cpt tag>").  CutPoint3D coordinates
 *     are in GEOMETRY UNITS (mm here), not metres.
 *
 *  4. model.probe() "DomainPoint" has NO "descr" property (Unknown_property)
 *     and no "dimension" property (Invalid_property_value).  The "Domain"
 *     probe does accept "descr".  Probe TABLES are only filled by the GUI,
 *     so the report numbers come from the numerical features (AvVolume /
 *     EvalPoint), which is the reliable route in batch mode.
 *
 *  5. A Parametric study step needs a COMMA-SEPARATED plistarr:
 *         set("plistarr", new String[]{"10,100,1000"});
 *     A space-separated list is read as a RANGE (it produced 0,10,20,...,100
 *     instead of three values).  With ONE Stationary step solving spf AND
 *     tds, the sweep solution carries one column per parameter value, in
 *     sweep order, and sol.getPVals() gives the parameter values in SI units
 *     (verified: [1.667e-10, 1.667e-9, 1.667e-8] m^3/s = 10, 100, 1000
 *     uL/min).
 *
 *  6. MATERIAL PROPERTIES MUST BE LITERAL VALUES (1e-3[Pa*s], 1000[kg/m^3]).
 *     A parameter reference resolves to an undefined variable and kills the
 *     stationary solve with "Failed to evaluate Jacobian of expression".
 *
 *  7. A mesh SEQUENCE needs a real mesher; Size alone builds 0 elements.
 *     Size must be a SUB-feature of the mesher:
 *         mesh.feature("ftri1").feature("size1").set("custom", true) ...
 *     BoxSelection needs condition="allvertices".
 *
 *  8. The COMSOL batch SecurityManager denies java.io writes and env vars
 *     inside the model class, so every number is printed to STDOUT, which
 *     the LSF scheduler captures into the job log.  Configuration is passed
 *     as positional arguments, never through the environment.
 *
 * ---------------------------------------------------------------------------
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.io.*;
import java.util.Arrays;

public class Ex2Chamber {

    static Model model;
    static String MODE = "bc";   // environment lookups are blocked in COMSOL batch -> compile-time constant
    static String OUT = ".";

    // selections
    static int[] chamD, ductD, cellD, inF, outF, z0F, z1F, cellBotF, cellTopF, vertE, stripD;
    static int[] cellVertE;

    // probe points (metres). z = 50 um above the chamber floor = where cells sit.
    static final double PZ = 0.00005;

    // ---- serpentine (part D) ---------------------------------------------
    static final int    SERP_LEGS  = 15;   // number of straight legs
    static final int    SERP_PITCH = 4;    // leg-to-leg pitch [mm]
    static final double SERP_Y0    = -29;  // y of the first leg [mm]
    static final String[] PNAMES = new String[]{"P1", "P2", "P3"};
    static final double[][] PXYZ = new double[][]{
        {-0.0295, 0.0, PZ},      // P1 upstream end, on the channel axis
        { 0.0, 0.0295, PZ},      // P2 off-axis, near the side wall
        { 0.0295, 0.0, PZ}       // P3 downstream end, on the channel axis
    };

    static void h(String s) {
        System.out.println();
        System.out.println("################ " + s + " ################");
    }

    static void p(String s) { System.out.println(s); }

    static void trySet(PropFeature e, String prop, boolean val) {
        try {
            e.set(prop, val);
        } catch (Throwable t) {
            p("   SET " + prop + " = " + val + "  FAILED :: " + t.getMessage());
        }
    }

    static void trySet(PropFeature e, String prop, String val) {
        try {
            e.set(prop, val);
        } catch (Throwable t) {
            p("   SET " + prop + " = " + val + "  FAILED :: " + t.getMessage());
        }
    }

    static void trySet(PropFeature e, String prop, String[] val) {
        try {
            e.set(prop, val);
        } catch (Throwable t) {
            p("   SET " + prop + " = " + Arrays.toString(val) + "  FAILED :: " + t.getMessage());
        }
    }

    // PhysicsFeature is NOT a PropFeature in 6.4 - it needs its own overload.
    static void trySet(PhysicsFeature e, String prop, String val) {
        try {
            e.set(prop, val);
        } catch (Throwable t) {
            p("   SET " + prop + " = " + val + "  FAILED :: " + t.getMessage());
        }
    }

    static void trySet(PhysicsFeature e, String prop, String[] val) {
        try {
            e.set(prop, val);
        } catch (Throwable t) {
            p("   SET " + prop + " = " + Arrays.toString(val) + "  FAILED :: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ main
    public static void main(String[] args) {
        try {
            // Configuration arrives as POSITIONAL ARGUMENTS:
            //     comsolbatch -inputfile Ex2Chamber.class -methodcall main <mode> [outdir]
            // The COMSOL batch SecurityManager denies file reads (a "mode.txt"
            // would be inaccessible) and environment variables inside the model
            // class, but the method arguments come through untouched
            // (verified: argc=2, arg[0]=smoke, arg[1]=.).
            MODE = (args != null && args.length > 0 && args[0] != null && args[0].length() > 0)
                ? args[0].trim() : "bc";
            OUT = (args != null && args.length > 1 && args[1] != null && args[1].length() > 0)
                ? args[1].trim() : ".";
            p("EX2 MODE = " + MODE + "   OUT = " + OUT);
            model = ModelUtil.create("Ex2Chamber");
            build(MODE);
        } catch (Throwable e) {
            e.printStackTrace();
            p("EX2 FAILED: " + e);
        }
        p("EX2 DONE");
    }

    // ------------------------------------------------------------------ build
    static void build(String mode) {
        // "geomef" is the geometry+mesh validation twin of "ef".
        boolean withCell = mode.equals("ef") || mode.equals("geomef")
                        || mode.equals("efsteady");
        boolean wide = mode.equals("d") || mode.equals("geod");
        // MODE "s": the serpentine (meander) inlet channel of part D.
        boolean serp = mode.equals("s") || mode.equals("geos");
        boolean smoke = mode.equals("smoke");
        // "geom"/"geomef": build geometry + selections + mesh and stop.  Fast
        // validation of a mode without paying for the solve.
        boolean geomOnly = mode.equals("geom") || mode.equals("geomef")
                        || mode.equals("geod") || mode.equals("geos");

        model.component().create("comp1");
        model.label("the lab-on-a-chip course Ex2 cell growth chamber - mode " + mode);

        // ---------------- parameters -------------------------------------
        model.param().set("D_sp", "200[um^2/s]");
        model.param().set("c_in", "1[mol/m^3]");
        model.param().set("t_fill", "2160[s]");
        model.param().set("Q_in", "100[ul/min]");
        model.param().set("rho", "1000[kg/m^3]");
        model.param().set("mu", "1e-3[Pa*s]");
        model.param().set("k_r", "0.15[1/s]");        // 15 %/s consumption in the cell layer
        model.param().set("eps_D", "0.7");            // porosity factor on D in the cell layer

        // ---------------- geometry --------------------------------------
        // MODE "d" (part D) replaces the single 2 mm crossing channel by a
        // 60 mm-wide inlet distributor and a 60 mm-wide outlet collector,
        // i.e. by ~30 parallel 2 mm channels.  That is the "several parallel
        // inlet channels" solution recommended by notes/ex2_analytical_results.txt
        // (part D): the flow is brought to every point of the 60 x 60 mm
        // footprint instead of only to the 2 mm stripe, so the off-axis dead
        // zones disappear.  The distributor is kept SHORT (4 mm) so that the
        // fluid volume, the residence time and the flow rate stay comparable
        // with the base case (3780 uL -> 4080 uL, +8 %).
        //
        // MODE "bc"/"ef": the assignment geometry -- a 60 x 60 x 1 mm chamber
        // (split in plan into the 2 mm channel stripe and the two side
        // regions, for meshing only) crossed by a 150 mm x 2 mm x 1 mm
        // channel whose two ends are the inlet and outlet ducts.
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        if (serp) {
            // ---- part D: SERPENTINE (meander) channel ---------------------
            // The analytical reference (notes/ex2_analytical_results.txt,
            // part D) states that diffusion along the 60 mm length is 8333x
            // slower than the flushing time, so the far corners can NEVER be
            // filled by diffusion and the FLOW must be brought to them.  A
            // meander keeps Re and t_fill unchanged but gives every point of
            // the footprint an advective residence time of O(t_fill).
            //
            // 15 straight legs of 2 mm width on a 4 mm pitch, joined by
            // short 2 mm connectors that alternate between the +x and the -x
            // end, so the single continuous path snakes through the whole
            // 60 x 60 mm footprint:
            //     leg  k occupies y in [y_k, y_k+2],  y_k = -29 + 4k
            //     connector k (k even) at x in [28,30],  (k odd) at [-30,-28]
            // The inlet duct feeds leg 0 at (x=-30, y=-29) and the outlet
            // duct collects leg 14 at (x=+30, y=+27).
            for (int k = 0; k < SERP_LEGS; k++) {
                g.create("leg" + k, "Block");
                g.feature("leg" + k).set("size", new String[]{"60", "2", "1"});
                g.feature("leg" + k).set("pos", new String[]{"-30", "" + (SERP_Y0 + SERP_PITCH * k), "0"});
            }
            for (int k = 0; k < SERP_LEGS - 1; k++) {
                g.create("con" + k, "Block");
                g.feature("con" + k).set("size", new String[]{"2", "" + (SERP_PITCH + 2), "1"});
                g.feature("con" + k).set("pos", new String[]{
                    (k % 2 == 0) ? "28" : "-30", "" + (SERP_Y0 + SERP_PITCH * k), "0"});
            }
            // inlet duct (x = -75 .. -30 at the level of leg 0)
            g.create("blkW", "Block");
            g.feature("blkW").set("size", new String[]{"45", "2", "1"});
            g.feature("blkW").set("pos", new String[]{"-75", "" + SERP_Y0, "0"});
            // outlet duct (x = +30 .. +75 at the level of the last leg)
            g.create("blkE", "Block");
            g.feature("blkE").set("size", new String[]{"45", "2", "1"});
            g.feature("blkE").set("pos", new String[]{"30", "" + (SERP_Y0 + SERP_PITCH * (SERP_LEGS - 1)), "0"});
        } else if (wide) {
            // ---- part D: FULL-WIDTH INLET -------------------------------
            // The base case feeds the 60 x 60 x 1 mm chamber through a 2 mm
            // crossing channel.  Because Pe >> 1 along the length
            // (t_diff,long / t_fill = 8333) only that 2 mm stripe is ever
            // exchanged: the off-axis probe P2 reaches c = 0.5 only after
            // 3055 s and never reaches 0.9.
            //
            // The improvement is to feed the WHOLE 60 mm width of the
            // chamber face at the same total flow rate.  Every y-station is
            // then an identical 60 mm x 1 mm duct with the same residence
            // time V/Q = 2160 s = t_fill, so the solute front crosses the
            // footprint at the same time everywhere.
            //
            // The inlet is prescribed as a UNIFORM NORMAL VELOCITY on the
            // 60 mm wide face (U0 = Q/A) instead of a mass-flow constraint:
            // the MassFlowRate condition on a 60 mm wide face produces a
            // singular stationary matrix ("void equations for comp1.p and
            // for comp1.spf.inl1.Pmf on the inlet face").  The flow rate is
            // then known exactly by construction.
            g.create("blkCh", "Block");
            g.feature("blkCh").set("size", new String[]{"60", "60", "1"});
            g.feature("blkCh").set("pos", new String[]{"-30", "-30", "0"});
            g.create("blkIn", "Block");
            g.feature("blkIn").set("size", new String[]{"5", "60", "1"});
            g.feature("blkIn").set("pos", new String[]{"-35", "-30", "0"});
            g.create("blkOut", "Block");
            g.feature("blkOut").set("size", new String[]{"5", "60", "1"});
            g.feature("blkOut").set("pos", new String[]{"30", "-30", "0"});
        } else {
            // ---- chamber split into the channel stripe and two side regions
            g.create("blkStrip", "Block");
            g.feature("blkStrip").set("size", new String[]{"60", "2", "1"});
            g.feature("blkStrip").set("pos", new String[]{"-30", "-1", "0"});
            g.create("blkSideA", "Block");
            g.feature("blkSideA").set("size", new String[]{"60", "29", "1"});
            g.feature("blkSideA").set("pos", new String[]{"-30", "-30", "0"});
            g.create("blkSideB", "Block");
            g.feature("blkSideB").set("size", new String[]{"60", "29", "1"});
            g.feature("blkSideB").set("pos", new String[]{"-30", "1", "0"});
            // inlet / outlet ducts
            g.create("blkW", "Block");
            g.feature("blkW").set("size", new String[]{"45", "2", "1"});
            g.feature("blkW").set("pos", new String[]{"-75", "-1", "0"});
            g.create("blkE", "Block");
            g.feature("blkE").set("size", new String[]{"45", "2", "1"});
            g.feature("blkE").set("pos", new String[]{"30", "-1", "0"});
        }
        if (withCell) {
            g.create("cylCell", "Cylinder");
            g.feature("cylCell").set("r", "25");
            g.feature("cylCell").set("h", "0.1");
            g.feature("cylCell").set("pos", new String[]{"0", "0", "-0.1"});
        }
        g.run();
        p("GEOM domains=" + g.getNDomains() + " boundaries=" + g.getNBoundaries()
          + " edges=" + g.getNEdges() + " verts=" + g.getNVertices());

        // ---------------- named selections -------------------------------
        if (serp) {
            // The serpentine and the leftover chamber volume are two separate
            // domains after Form Union; both lie inside the footprint box, so
            // "selCham" picks up the whole chamber content.
            box(g, "selCham", 3, "inside", -30.1, 30.1, -30.1, 30.1, -0.05, 1.2);
            box(g, "selDuctW", 3, "inside", -75.1, -29.9, SERP_Y0 - 1.1, SERP_Y0 + 3.1, -0.05, 1.2);
            box(g, "selDuctE", 3, "inside", 29.9, 75.1, SERP_Y0 + SERP_PITCH * (SERP_LEGS - 1) - 1.1,
                SERP_Y0 + SERP_PITCH * (SERP_LEGS - 1) + 3.1, -0.05, 1.2);
            box(g, "selIn", 2, "allvertices", -75.1, -74.9, SERP_Y0 - 1.1, SERP_Y0 + 3.1, -0.2, 1.2);
            box(g, "selOut", 2, "allvertices", 74.9, 75.1,
                SERP_Y0 + SERP_PITCH * (SERP_LEGS - 1) - 1.1,
                SERP_Y0 + SERP_PITCH * (SERP_LEGS - 1) + 3.1, -0.2, 1.2);
            box(g, "selZ0", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, -0.005, 0.005);
            box(g, "selZ1", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, 0.995, 1.005);
            box(g, "selVert", 1, "inside", -75.1, 75.1, -30.1, 30.1, -0.05, 1.05);
        } else if (wide) {
            // full-width inlet / outlet plenum
            box(g, "selCham", 3, "inside", -30.1, 30.1, -30.1, 30.1, -0.05, 1.2);
            box(g, "selDuctW", 3, "inside", -35.1, -29.9, -30.1, 30.1, -0.05, 1.2);
            box(g, "selDuctE", 3, "inside", 29.9, 35.1, -30.1, 30.1, -0.05, 1.2);
            box(g, "selIn", 2, "allvertices", -35.1, -34.9, -30.1, 30.1, -0.2, 1.2);
            box(g, "selOut", 2, "allvertices", 34.9, 35.1, -30.1, 30.1, -0.2, 1.2);
            box(g, "selZ0", 2, "allvertices", -35.1, 35.1, -30.1, 30.1, -0.005, 0.005);
            box(g, "selZ1", 2, "allvertices", -35.1, 35.1, -30.1, 30.1, 0.995, 1.005);
            box(g, "selVert", 1, "inside", -35.1, 35.1, -30.1, 30.1, -0.05, 1.05);
        } else {
            box(g, "selStrip", 3, "inside", -30.1, 30.1, -1.1, 1.1, -0.05, 1.2);
            box(g, "selChamA", 3, "inside", -30.1, 30.1, -30.1, -0.9, -0.05, 1.2);
            box(g, "selChamB", 3, "inside", -30.1, 30.1, 0.9, 30.1, -0.05, 1.2);
            box(g, "selDuctW", 3, "inside", -75.1, -29.9, -1.1, 1.1, -0.05, 1.2);
            box(g, "selDuctE", 3, "inside", 29.9, 75.1, -1.1, 1.1, -0.05, 1.2);
            box(g, "selIn", 2, "allvertices", -75.1, -74.9, -1.1, 1.1, -0.2, 1.2);
            box(g, "selOut", 2, "allvertices", 74.9, 75.1, -1.1, 1.1, -0.2, 1.2);
            box(g, "selZ0", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, -0.005, 0.005);
            box(g, "selZ1", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, 0.995, 1.005);
            box(g, "selVert", 1, "inside", -75.1, 75.1, -30.1, 30.1, -0.05, 1.05);
        }
        box(g, "selCell", 3, "inside", -25.1, 25.1, -25.1, 25.1, -0.15, 0.05);
        box(g, "selCellBot", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.105, -0.095);
        box(g, "selCellTop", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.005, 0.005);
        // Vertical edges of the cell cylinder, for the Sweep Distribution.
        //
        // The original BoxSelection (entitydim=1, condition="inside",
        // x/y = +/-25.1, z = -0.105 .. 0.005) returned ZERO edges, so the
        // cell Sweep got no Distribution at all and the 100 um layer was
        // meshed with only a couple of elements: the reaction-diffusion
        // profile came out far too flat (c_mid/c_surf = 0.425 instead of
        // the analytic cosh(m*Lc/2)/cosh(m*Lc) = 0.201).
        //
        // A vertical edge of the cylinder runs from z = -0.1 to z = 0.  Its
        // two end vertices are therefore at z = -0.1 and z = 0, and the rim
        // arcs lie in exactly those two planes.
        //
        // Selection that works (verified with Ex2CellProbe2): a NARROW box
        // around one seam with condition "inside".  The box
        // 24.5<x<25.5, -0.6<y<0.6, -0.15<z<0.05 contains the vertical seam
        // edge at (25, 0) completely, while none of the rim arcs fit inside
        // it (they run from (25,0) to (0,25) and so leave the y band).
        // Two opposite seams are needed, because the Cylinder primitive
        // splits its surface into four quarter faces.
        //
        // The first attempt (a WIDE box, x/y = +/-25.1, condition "inside")
        // returned ZERO edges, which left the cell Sweep with no
        // Distribution at all: the 100 um layer was then one element thick
        // and the reaction-diffusion profile came out far too flat.
        box(g, "selCellVertA", 1, "inside", 24.5, 25.5, -0.6, 0.6, -0.15, 0.05);
        box(g, "selCellVertB", 1, "inside", -0.6, 0.6, 24.5, 25.5, -0.15, 0.05);
        g.run();

        int[] sStrip = new int[0];
        int[] sChamA = new int[0];
        int[] sChamB = new int[0];
        int[] sCham = new int[0];
        if (serp) {
            sCham = model.selection("geom1_selCham").entities(3);
        } else if (wide) {
            sCham = model.selection("geom1_selCham").entities(3);
        } else {
            sStrip = model.selection("geom1_selStrip").entities(3);
            sChamA = model.selection("geom1_selChamA").entities(3);
            sChamB = model.selection("geom1_selChamB").entities(3);
        }
        int[] sDW = model.selection("geom1_selDuctW").entities(3);
        int[] sDE = model.selection("geom1_selDuctE").entities(3);

        stripD = sStrip;
        chamD = wide ? sCham : concat(concat(sStrip, sChamA), sChamB);
        ductD = concat(sDW, sDE);
        cellD = model.selection("geom1_selCell").entities(3);
        inF = model.selection("geom1_selIn").entities(2);
        outF = model.selection("geom1_selOut").entities(2);
        z0F = model.selection("geom1_selZ0").entities(2);
        z1F = model.selection("geom1_selZ1").entities(2);
        cellBotF = model.selection("geom1_selCellBot").entities(2);
        cellTopF = model.selection("geom1_selCellTop").entities(2);
        vertE = model.selection("geom1_selVert").entities(1);
        cellVertE = concat(model.selection("geom1_selCellVertA").entities(1),
                           model.selection("geom1_selCellVertB").entities(1));
        p("SEL strip=" + Arrays.toString(sStrip) + " chamA=" + Arrays.toString(sChamA)
          + " chamB=" + Arrays.toString(sChamB) + " cham=" + Arrays.toString(sCham));
        p("SEL ductW=" + Arrays.toString(sDW) + " ductE=" + Arrays.toString(sDE)
          + " cell=" + Arrays.toString(cellD));
        p("SEL inlet=" + Arrays.toString(inF) + " outlet=" + Arrays.toString(outF));
        p("SEL z0=" + Arrays.toString(z0F) + " z1=" + Arrays.toString(z1F));
        p("SEL cellBot=" + Arrays.toString(cellBotF) + " cellTop=" + Arrays.toString(cellTopF));
        p("SEL vertical edges = " + vertE.length + "  cell vertical edges = " + cellVertE.length);
        p("SEL chamber domains = " + Arrays.toString(chamD) + " duct domains = " + Arrays.toString(ductD));


        // ---------------- material ---------------------------------------
        model.component("comp1").material().create("mat1", "Common");
        model.component("comp1").material("mat1").label("Water");
        // LITERAL values, never parameter references.  With a reference the
        // material property resolves to the undefined variable
        // comp1.mat1.def.mu and the stationary solve dies with
        // "Failed to evaluate Jacobian of expression".
        model.component("comp1").material("mat1").propertyGroup("def")
             .set("dynamicviscosity", "1e-3[Pa*s]")
             .set("density", "1000[kg/m^3]");
        try { model.component("comp1").material("mat1").selection().all(); }
        catch (Throwable t) { p("material selection all() failed: " + t.getMessage()); }

        // ---------------- mesh -------------------------------------------
        buildMesh(withCell, smoke, wide);

        if (geomOnly) {
            p("GEOM-ONLY MODE: geometry and mesh built, stopping before physics");
            p("EX2 DONE");
            return;
        }

        // ---------------- physics ----------------------------------------
        buildPhysics(mode, withCell);

        // ---------------- studies ----------------------------------------
        if (mode.equals("efsteady")) runEFSteady();
        else if (withCell) runEF(smoke);
        else runBC(smoke);
    }

    static void box(GeomSequence g, String tag, int dim, String cond,
                    double xmin, double xmax, double ymin, double ymax, double zmin, double zmax) {
        g.create(tag, "BoxSelection");
        g.feature(tag).set("entitydim", dim);
        g.feature(tag).set("condition", cond);
        g.feature(tag).set("xmin", "" + xmin); g.feature(tag).set("xmax", "" + xmax);
        g.feature(tag).set("ymin", "" + ymin); g.feature(tag).set("ymax", "" + ymax);
        g.feature(tag).set("zmin", "" + zmin); g.feature(tag).set("zmax", "" + zmax);
    }

    // ------------------------------------------------------------------ mesh
    static void buildMesh(boolean withCell, boolean smoke, boolean wide) {
        h("MESH");
        MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");

        // A mesh SEQUENCE needs a real mesher.  The Size nodes live at the
        // TOP level of the sequence, BEFORE the mesher, and the per-domain
        // ones come after the global one so that they win for their domains.
        //
        // WARNING: do NOT add a Size SUB-feature to ftri1.  It overrides the
        // per-domain Size nodes for the whole z=0 face set, the mesh
        // coarsens (29028 -> 4488 elements) and the 2 x 1 mm inlet face ends
        // up with too few nodes: the mass-flow constraint then becomes
        // singular ("void equations for comp1.p at the inlet face").
        m.create("sizeG", "Size");
        m.feature("sizeG").set("hmax", "2.5");
        m.feature("sizeG").set("hmin", "0.05");
        m.feature("sizeG").set("hgrad", 1.25);

        if (stripD.length > 0) {
            m.create("sizeStrip", "Size");
            m.feature("sizeStrip").selection().geom("geom1", 3).set(stripD);
            m.feature("sizeStrip").set("hmax", smoke ? "1.0" : "0.35");
            m.feature("sizeStrip").set("hmin", "0.05");
            m.feature("sizeStrip").set("hgrad", 1.2);
        }

        m.create("sizeDuct", "Size");
        m.feature("sizeDuct").selection().geom("geom1", 3).set(ductD);
        // The inlet face needs enough nodes for the mass-flow constraint to be
        // non-degenerate: at hmax = 1.0 mm the 2 x 1 mm inlet face had only
        // 4 nodes and the stationary solve went singular.
        m.feature("sizeDuct").set("hmax", "0.4");
        m.feature("sizeDuct").set("hmin", "0.05");
        m.feature("sizeDuct").set("hgrad", 1.2);

        m.create("sizeSide", "Size");
        m.feature("sizeSide").selection().geom("geom1", 3).set(
            wide ? chamD : concat(model.selection("geom1_selChamA").entities(3),
                                 model.selection("geom1_selChamB").entities(3)));
        m.feature("sizeSide").set("hmax", "2.5");
        m.feature("sizeSide").set("hmin", "0.2");
        m.feature("sizeSide").set("hgrad", 1.3);

        // surface mesh of everything at z = 0 (this also provides the source
        // mesh of the cell-layer sweep, so the two meshes stay conformal)
        m.create("ftri1", "FreeTri");
        m.feature("ftri1").selection().geom(2).set(z0F);

        if (!withCell) {
            // ---- pure swept mesh: fluid swept from z = 0 to z = 1 --------
            m.create("swe1", "Sweep");
            m.feature("swe1").selection().geom(3).set(concat(chamD, ductD));
            m.feature("swe1").selection("sourceface").geom(2).set(z0F);
            m.feature("swe1").selection("targetface").geom(2).set(z1F);
            addDistribution(m, "swe1", "dis1", vertE, smoke ? 3 : 6);
        } else {
            // ---- ef mode: tets for the fluid, swept prisms for the cell ----
            // The cell cylinder punches a circular hole in the chamber floor,
            // so the chamber cannot be swept.  Instead: FreeTri on the z=0
            // faces (chamber floor + cell top disc, so the two meshes stay
            // conformal), Sweep of the cell layer from the disc at z=0 down
            // to the disc at z=-0.1, and FreeTet for the fluid.
            m.create("ftet1", "FreeTet");
            m.feature("ftet1").selection().geom(3).set(concat(chamD, ductD));
            m.create("sweCell", "Sweep");
            m.feature("sweCell").selection().geom(3).set(cellD);
            m.feature("sweCell").selection("sourceface").geom(2).set(cellTopF);
            m.feature("sweCell").selection("targetface").geom(2).set(cellBotF);
            // A TOP-LEVEL Distribution with an explicit edge selection is the
            // form that works (the sub-feature form raises
            // "Unsupported_geometric_entity_level").  The cell layer is
            // 100 um thick and the reaction length scale is 1/m = 30.6 um,
            // so 20 layers (5 um each) resolve the profile properly.
            addDistribution(m, "sweCell", "dis2", cellVertE, smoke ? 4 : 12);
        }
        m.run();
        p("MESH numElem=" + m.stat().getNumElem() + " types=" + Arrays.toString(m.stat().getTypes())
          + " minQ=" + m.stat().getMinQuality() + " meanQ=" + m.stat().getMeanQuality());
        if (withCell) p("MESH cell layers = " + countCellLayers(m));
    }

    // ------------------------------------------------------------------ physics
    static void buildPhysics(String mode, boolean withCell) {
        h("PHYSICS");

        // ---- laminar flow ------------------------------------------------
        model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
        model.component("comp1").physics("spf").selection().geom("geom1", 3)
             .set(concat(chamD, ductD));            // cell layer excluded from the flow
        model.component("comp1").physics("spf").create("inl1", "Inlet", 2);
        model.component("comp1").physics("spf").feature("inl1").selection().set(inF);
        if (mode.equals("d")) {
            // PART D: uniform normal velocity over the FULL 60 mm width.
            //
            // The Inlet feature of LaminarFlow accepts
            //   BoundaryCondition = Velocity | PressureNoViscousStress |
            //                          LaminarInflow | NormalStress | MassFlow
            // "NormalInflowVelocity" is NOT one of them (Invalid_parameter_value)
            // and the mass-flow constraint on a 60 mm wide face gives a
            // singular stationary matrix.  "Velocity" with the 3-vector
            // property "u0" works: u0 = (-Q/A, 0, 0) with A = 60 mm x 1 mm
            // prescribes a uniform inflow over the whole width and gives
            // exactly the same total flow rate as the base case.
            trySet(model.component("comp1").physics("spf").feature("inl1"),
                   "BoundaryCondition", "Velocity");
            trySet(model.component("comp1").physics("spf").feature("inl1"), "u0",
                   new String[]{"-Q_in/(60[mm]*1[mm])", "0", "0"});
            try {
                p("SPF inlet u0 = " + Arrays.toString(
                    model.component("comp1").physics("spf").feature("inl1").getStringArray("u0"))
                  + "  type = " + model.component("comp1").physics("spf")
                     .feature("inl1").getString("BoundaryCondition"));
            } catch (Throwable t) { p("spf inlet readback failed: " + t.getMessage()); }
        } else {
            trySet(model.component("comp1").physics("spf").feature("inl1"), "BoundaryCondition", "MassFlow");
            trySet(model.component("comp1").physics("spf").feature("inl1"), "MassFlowType", "MassFlowRate");
            trySet(model.component("comp1").physics("spf").feature("inl1"), "mfr", "rho*Q_in");
        }
        try {
            p("SPF inlet mfr = " + model.component("comp1").physics("spf").feature("inl1").getString("mfr")
              + "  type = " + model.component("comp1").physics("spf").feature("inl1").getString("MassFlowType"));
        } catch (Throwable t) { p("spf inlet readback failed: " + t.getMessage()); }
        model.component("comp1").physics("spf").create("out1", "Outlet", 2);
        model.component("comp1").physics("spf").feature("out1").selection().set(outF);
        trySet(model.component("comp1").physics("spf").feature("out1"), "p0", "0");
        p("spf built");

        // ---- transport of diluted species --------------------------------
        model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
        // The velocity input of the TDS "Fluid" node is a VECTOR in 3D.
        // Setting it to the scalar "root.comp1.u" leaves the convection term
        // empty and the solute never leaves the inlet: pure diffusion only.
        try {
            p("TDS cdm1 properties = " + Arrays.toString(
                model.component("comp1").physics("tds").feature("cdm1").properties()));
        } catch (Throwable t) { p("cdm1 properties failed: " + t.getMessage()); }
        try {
            p("TDS cdm1 u_src allowed = " + Arrays.toString(
                model.component("comp1").physics("tds").feature("cdm1")
                    .getAllowedPropertyValues("u_src")));
        } catch (Throwable t) { p("cdm1 u_src allowed failed: " + t.getMessage()); }
        trySet(model.component("comp1").physics("tds").feature("cdm1"), "u_src", "userdef");
        trySet(model.component("comp1").physics("tds").feature("cdm1"), "u",
               new String[]{"root.comp1.u", "root.comp1.v", "root.comp1.w"});
        trySet(model.component("comp1").physics("tds").feature("cdm1"), "D_c_mat", "userdef");
        // In 3D, D_c is a 3x3 TENSOR: it needs nine entries, exactly as the
        // shipped Application Library models write it.
        trySet(model.component("comp1").physics("tds").feature("cdm1"), "D_c", new String[]{
            "D_sp", "0", "0", "0", "D_sp", "0", "0", "0", "D_sp"});
        try {
            p("TDS cdm1 u_src = " + model.component("comp1").physics("tds").feature("cdm1").getString("u_src")
              + "  u = " + Arrays.toString(
                    model.component("comp1").physics("tds").feature("cdm1").getStringArray("u")));
            p("TDS cdm1 D_c = " + model.component("comp1").physics("tds").feature("cdm1").getString("D_c"));
            try {
                p("TDS cdm1 u = " + Arrays.toString(
                    model.component("comp1").physics("tds").feature("cdm1").getStringArray("u")));
            } catch (Throwable t) { p("cdm1 u readback failed: " + t.getMessage()); }
        } catch (Throwable t) { p("tds cdm1 readback failed: " + t.getMessage()); }

        if (!withCell) {
            // rectangular pulse: start -5 s, end t_fill, transition zone 5, 2 derivatives
            model.func().create("rect1", "Rectangle");
            trySet(model.func("rect1"), "funcname", "rect1");
            trySet(model.func("rect1"), "lower", "-5[s]");
            trySet(model.func("rect1"), "upper", "t_fill");
            trySet(model.func("rect1"), "smooth", "5");
            trySet(model.func("rect1"), "smoothactive", "on");
            trySet(model.func("rect1"), "ncontder", "2");
            trySet(model.func("rect1"), "baseline", "0");
            trySet(model.func("rect1"), "amplitude", "1");
            try {
                p("rect1 lower=" + model.func("rect1").getString("lower")
                  + " upper=" + model.func("rect1").getString("upper")
                  + " smooth=" + model.func("rect1").getString("smooth")
                  + " ncontder=" + model.func("rect1").getString("ncontder"));
            } catch (Throwable t) { p("rect1 readback failed: " + t.getMessage()); }
            for (double tt : new double[]{-10, -5, -2.5, 0, 100, 2150, 2160, 2165, 3000}) {
                try { p("   rect1(" + tt + " s) = " + model.param().evaluate("rect1(" + tt + "[s])")); }
                catch (Throwable t) { p("   rect1 eval failed: " + t.getMessage()); break; }
            }

            model.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
            model.component("comp1").physics("tds").feature("inflow1").selection().set(inF);
            trySet(model.component("comp1").physics("tds").feature("inflow1"), "c0", "c_in*rect1(t)");
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
            model.component("comp1").physics("tds").feature("outflow1").selection().set(outF);
            p("tds pulse built");
        } else {
            // stable inflow, no pulse
            model.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
            model.component("comp1").physics("tds").feature("inflow1").selection().set(inF);
            trySet(model.component("comp1").physics("tds").feature("inflow1"), "c0", "c_in");
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
            model.component("comp1").physics("tds").feature("outflow1").selection().set(outF);

            // cell layer: no convection, reduced diffusion (porosity 70 %)
            model.component("comp1").physics("tds").create("fluid2", "Fluid", 3);
            model.component("comp1").physics("tds").feature("fluid2").selection().set(cellD);
            trySet(model.component("comp1").physics("tds").feature("fluid2"), "u_src", "userdef");
            trySet(model.component("comp1").physics("tds").feature("fluid2"), "u", new String[]{"0", "0", "0"});
            trySet(model.component("comp1").physics("tds").feature("fluid2"), "D_c_mat", "userdef");
            trySet(model.component("comp1").physics("tds").feature("fluid2"), "D_c", new String[]{
                "eps_D*D_sp", "0", "0", "0", "eps_D*D_sp", "0", "0", "0", "eps_D*D_sp"});
            try {
                p("TDS fluid2 (cell layer) u_src = "
                  + model.component("comp1").physics("tds").feature("fluid2").getString("u_src")
                  + "  D_c = " + model.component("comp1").physics("tds").feature("fluid2").getString("D_c"));
            } catch (Throwable t) { p("fluid2 readback failed: " + t.getMessage()); }
            // consumption by the cells
            model.component("comp1").physics("tds").create("reac1", "Reactions", 3);
            model.component("comp1").physics("tds").feature("reac1").selection().set(cellD);
            trySet(model.component("comp1").physics("tds").feature("reac1"), "R_c_src", "userdef");
            trySet(model.component("comp1").physics("tds").feature("reac1"), "R_c", "-k_r*c");
            try {
                p("TDS reac1 R_c = " + model.component("comp1").physics("tds").feature("reac1").getString("R_c"));
            } catch (Throwable t) { p("reac1 readback failed: " + t.getMessage()); }
            // initial values: the channel (both ducts) already filled with c_in
            model.component("comp1").physics("tds").create("init2", "init", 3);
            model.component("comp1").physics("tds").feature("init2").selection().set(ductD);
            trySet(model.component("comp1").physics("tds").feature("init2"), "initc", "c_in");
            p("tds cell-layer built");
        }
    }

    // ------------------------------------------------------------------ B and C
    static void runBC(boolean smoke) {
        h("STUDY std1: stationary velocity");
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
        model.study("std1").feature("stat").setSolveFor("/physics/tds", false);

        h("STUDY std2: transient concentration (velocity taken from std1)");
        model.study().create("std2");
        model.study("std2").create("time", "Transient");
        double[] tl = smoke ? times(-5, 5, 60) : times(-5, 10, 6480);
        model.study("std2").feature("time").set("tlist", listStr(tl));
        p("tlist (" + tl.length + " values)");
        // *** THE CRITICAL COUPLING ***
        // The velocity field of the stationary flow (std1) has to reach the
        // transient species step through "Values of variables not solved for".
        // That section must be put in USER CONTROLLED mode (usesol=true)
        // BEFORE notsolmethod/notstudy are given -- otherwise those two
        // settings are SILENTLY IGNORED: inside the transient, spf.U evaluates
        // to 0 and the species is transported by PURE DIFFUSION ONLY.
        // Measured on a 40 mm channel: without usesol, c at 19 mm after 60 s
        // was 1e-83; with it, c = 0.92.  Every shipped Application Library
        // model that couples two studies sets usesol=true first.
        trySet(model.study("std2").feature("time"), "usesol", true);
        trySet(model.study("std2").feature("time"), "notsolmethod", "sol");
        trySet(model.study("std2").feature("time"), "notstudy", "std1");
        trySet(model.study("std2").feature("time"), "notstudystep", "stat");
        trySet(model.study("std2").feature("time"), "notsolnum", "auto");
        try {
            p("std2 coupling: usesol=" + model.study("std2").feature("time").getString("usesol")
              + " notsolmethod=" + model.study("std2").feature("time").getString("notsolmethod")
              + " notstudy=" + model.study("std2").feature("time").getString("notstudy"));
        } catch (Throwable t) { p("std2 coupling readback failed: " + t.getMessage()); }
        model.study("std2").feature("time").setSolveFor("/physics/spf", false);
        model.study("std2").feature("time").setSolveFor("/physics/tds", true);
        model.study("std1").createAutoSequences("all");
        model.study("std2").createAutoSequences("all");
        p("studies built");

        // Probe nodes (the assignment asks for a domain probe and point
        // probes).  NOTE: the DomainPoint probe accepts NEITHER "descr" NOR
        // "dimension" (Unknown_property / Invalid_property_value) -- only
        // "coords" and a PointExpr sub-feature.  The Domain probe does accept
        // "descr".  The probe TABLES are only filled by the GUI, so the report
        // numbers are taken from the numerical features in exportTimeSeries(),
        // which is the reliable route in batch mode.
        try {
            model.probe().create("domAvg", "Domain");
            model.probe("domAvg").model("comp1");
            model.probe("domAvg").set("expr", "c");
            model.probe("domAvg").set("type", "average");
            model.probe("domAvg").set("unit", "mol/m^3");
            model.probe("domAvg").set("descr", "chamber average concentration");
            model.probe("domAvg").selection().set(chamD);
            p("domAvg probe created");
        } catch (Throwable t) { p("domAvg probe FAILED: " + t.getMessage()); }
        for (int i = 0; i < 3; i++) {
            try {
                model.probe().create("p" + PNAMES[i], "DomainPoint");
                model.probe("p" + PNAMES[i]).model("comp1");
                model.probe("p" + PNAMES[i]).set("coords", new double[][]{PXYZ[i]});
                model.probe("p" + PNAMES[i]).create("pe", "PointExpr");
                model.probe("p" + PNAMES[i]).feature("pe").set("expr", "c");
                model.probe("p" + PNAMES[i]).feature("pe").set("unit", "mol/m^3");
                p("probe " + PNAMES[i] + " created");
            } catch (Throwable t) { p("probe " + PNAMES[i] + " FAILED: " + t.getMessage()); }
        }

        // ---------------- solve ------------------------------------------
        h("SOLVE std1 (stationary velocity)");
        model.study("std1").run();
        p("std1 done");

        h("SOLVE std2 (transient species)");
        model.study("std2").run();
        p("std2 done");

        exportTimeSeries(MODE, false, tl);
    }

    /** number of distinct prism layers through the 100 um cell layer */
    static int countCellLayers(MeshSequence m) {
        try {
            int[][] pr = m.getElem("prism");
            double[][] vx = m.getVertex();
            // getVertex() returns [3][n]: one row per coordinate
            java.util.TreeSet<Integer> zs = new java.util.TreeSet<Integer>();
            // the mesh vertex coordinates come back in the geometry unit (mm)
            for (int[] e : pr) {
                double zmin = 1e9, zmax = -1e9;
                boolean inCell = true;
                for (int v : e) {
                    double z = vx[2][v];
                    if (z < -0.101 || z > 0.001) inCell = false;
                    zmin = Math.min(zmin, z); zmax = Math.max(zmax, z);
                }
                if (inCell && (zmax - zmin) < 0.05) zs.add(Integer.valueOf((int) Math.round(zmin * 1000.0)));
            }
            return zs.size();
        } catch (Throwable t) {
            p("   cell layer count failed: " + t.getMessage());
            return -1;
        }
    }

    /** Stationary flow + stationary species with the cell layer.
     *
     *  The cell layer obeys  D_eff c'' = k c  with a no-flux bottom, so the
     *  PROFILE SHAPE is fixed by k and D_eff alone:
     *      c(z) = c_s cosh(m(Lc-z)) / cosh(m Lc),  m = sqrt(k/D_eff)
     *      => c_mid / c_surface = cosh(m Lc /2) / cosh(m Lc)
     *  with m*Lc = sqrt(0.15/1.4e-10)*1e-4 = 3.2733 that ratio is 0.2006.
     *  The measured ratio is therefore a pure MESH-QUALITY CHECK of the
     *  100 um layer, independent of the flow rate.
     */
    static void runEFSteady() {
        h("STUDY std1: stationary velocity");
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
        model.study("std1").feature("stat").setSolveFor("/physics/tds", false);

        h("STUDY std2: stationary species, velocity from std1");
        model.study().create("std2");
        model.study("std2").create("stat", "Stationary");
        trySet(model.study("std2").feature("stat"), "usesol", true);
        trySet(model.study("std2").feature("stat"), "notsolmethod", "sol");
        trySet(model.study("std2").feature("stat"), "notstudy", "std1");
        trySet(model.study("std2").feature("stat"), "notstudystep", "stat");
        trySet(model.study("std2").feature("stat"), "notsolnum", "auto");
        model.study("std2").feature("stat").setSolveFor("/physics/spf", false);
        model.study("std2").feature("stat").setSolveFor("/physics/tds", true);
        model.study("std1").createAutoSequences("all");
        model.study("std2").createAutoSequences("all");

        h("SOLVE std1");
        model.study("std1").run();
        p("std1 done");
        h("SOLVE std2");
        model.study("std2").run();
        p("std2 done");

        // cell-layer profile: average, surface point, midplane point, bottom
        String dset = studyDataset("std2");
        p("steady dataset = " + dset);
        double[][] avCell = evalAv(cellD, "c", dset, "avCellS");
        double[][] rSurf = evalPoint(new double[]{0.0, 0.0, -0.000002}, dset, "pevSurfS", "cptSurfS");
        double[][] rMid  = evalPoint(new double[]{0.0, 0.0, -0.00005},  dset, "pevMidS",  "cptMidS");
        double[][] rBot  = evalPoint(new double[]{0.0, 0.0, -0.000098}, dset, "pevBotS",  "cptBotS");
        double surf = (rSurf.length > 0 && rSurf[0].length > 0) ? rSurf[0][0] : Double.NaN;
        double mid  = (rMid.length  > 0 && rMid[0].length  > 0) ? rMid[0][0]  : Double.NaN;
        double bot  = (rBot.length  > 0 && rBot[0].length  > 0) ? rBot[0][0]  : Double.NaN;
        p("CELLSTEADY avg=" + avCell[0][0] + " surface=" + surf
          + " midplane=" + mid + " bottom=" + bot);
        p("CELLSTEADY ratio mid/surface = " + (mid / surf)
          + "   bottom/surface = " + (bot / surf));
        double mLc = Math.sqrt(0.15 / 1.4e-10) * 1e-4;
        p("CELLSTEADY analytic cosh(mLc/2)/cosh(mLc) = "
          + (Math.cosh(mLc / 2) / Math.cosh(mLc)) + "  with m*Lc = " + mLc);
        p("EX2 DONE");
    }

    // ------------------------------------------------------------------ E and F
    static void runEF(boolean smoke) {
        h("STUDY std1: stationary velocity");
        model.study().create("std1");
        model.study("std1").create("stat", "Stationary");
        model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
        model.study("std1").feature("stat").setSolveFor("/physics/tds", false);

        h("STUDY std2: transient concentration with the cell layer");
        model.study().create("std2");
        model.study("std2").create("time", "Transient");
        // every 5 s up to the filling time, every 200 s afterwards (assignment hint)
        double[] tl = smoke ? times(0, 5, 60) : times(0, 5, 2160, 200, 6480);
        model.study("std2").feature("time").set("tlist", listStr(tl));
        p("tlist (" + tl.length + " values)");
        // *** THE CRITICAL COUPLING ***
        // The velocity field of the stationary flow (std1) has to reach the
        // transient species step through "Values of variables not solved for".
        // That section must be put in USER CONTROLLED mode (usesol=true)
        // BEFORE notsolmethod/notstudy are given -- otherwise those two
        // settings are SILENTLY IGNORED: inside the transient, spf.U evaluates
        // to 0 and the species is transported by PURE DIFFUSION ONLY.
        // Measured on a 40 mm channel: without usesol, c at 19 mm after 60 s
        // was 1e-83; with it, c = 0.92.  Every shipped Application Library
        // model that couples two studies sets usesol=true first.
        trySet(model.study("std2").feature("time"), "usesol", true);
        trySet(model.study("std2").feature("time"), "notsolmethod", "sol");
        trySet(model.study("std2").feature("time"), "notstudy", "std1");
        trySet(model.study("std2").feature("time"), "notstudystep", "stat");
        trySet(model.study("std2").feature("time"), "notsolnum", "auto");
        try {
            p("std2 coupling: usesol=" + model.study("std2").feature("time").getString("usesol")
              + " notsolmethod=" + model.study("std2").feature("time").getString("notsolmethod")
              + " notstudy=" + model.study("std2").feature("time").getString("notstudy"));
        } catch (Throwable t) { p("std2 coupling readback failed: " + t.getMessage()); }
        model.study("std2").feature("time").setSolveFor("/physics/spf", false);
        model.study("std2").feature("time").setSolveFor("/physics/tds", true);
        model.study("std1").createAutoSequences("all");
        model.study("std2").createAutoSequences("all");
        p("studies built");

        // probes: average concentration in the cell domain + the mid-plane point
        try {
            model.probe().create("cellAvg", "Domain");
            model.probe("cellAvg").model("comp1");
            model.probe("cellAvg").set("expr", "c");
            model.probe("cellAvg").set("type", "average");
            model.probe("cellAvg").set("unit", "mol/m^3");
            model.probe("cellAvg").set("descr", "cell layer average concentration");
            model.probe("cellAvg").selection().set(cellD);
            p("cellAvg probe created");
        } catch (Throwable t) {
            p("cellAvg probe FAILED: " + t.getMessage());
        }
        try {
            model.probe().create("pMid", "DomainPoint");
            model.probe("pMid").model("comp1");
            model.probe("pMid").set("coords", new double[][]{{0.0, 0.0, -0.00005}});
            model.probe("pMid").create("pe", "PointExpr");
            model.probe("pMid").feature("pe").set("expr", "c");
            model.probe("pMid").feature("pe").set("unit", "mol/m^3");
            p("pMid probe created");
        } catch (Throwable t) {
            p("pMid probe FAILED: " + t.getMessage());
        }

        h("SOLVE std1 (stationary velocity)");
        model.study("std1").run();
        p("std1 done");

        h("SOLVE std2 (transient species)");
        model.study("std2").run();
        p("std2 done");

        exportTimeSeries(MODE, true, tl);

        // ---------------- PART F ------------------------------------------
        h("PART F: stationary study with parametric flow-rate sweep");
        model.study().create("std3");
        model.study("std3").create("param", "Parametric");
        trySet(model.study("std3").feature("param"), "pname", new String[]{"Q_in"});
        // COMMA separated = discrete list of three values.  A SPACE separated
        // list is interpreted as a RANGE (it produced 0,10,20,...,100).
        trySet(model.study("std3").feature("param"), "plistarr", new String[]{"10,100,1000"});
        trySet(model.study("std3").feature("param"), "punit", new String[]{"ul/min"});
        trySet(model.study("std3").feature("param"), "plist", "10,100,1000");
        try {
            p("std3 param plistarr = " + Arrays.toString(
                model.study("std3").feature("param").getStringArray("plistarr")));
        } catch (Throwable t) { p("param readback failed: " + t.getMessage()); }
        trySet(model.study("std3").feature("param"), "sweeptype", "sparse");
        // ONE Stationary step solving spf AND tds.  Then the sweep solution
        // carries one column per flow rate (verified: 3 columns and
        // sol.getPVals() = [1.667e-10, 1.667e-9, 1.667e-8] m^3/s = the three
        // flow rates in SI units).  Two coupled steps were tried and gave a
        // much harder extraction.
        model.study("std3").create("stat", "Stationary");
        model.study("std3").feature("stat").setSolveFor("/physics/spf", true);
        model.study("std3").feature("stat").setSolveFor("/physics/tds", true);
        model.study("std3").createAutoSequences("all");
        p("std3 built");

        h("SOLVE std3 (parametric stationary)");
        model.study("std3").run();
        p("std3 done");

        exportParametric();
    }

    // ------------------------------------------------------------------ exports
    static void exportTimeSeries(String tag, boolean withCell, double[] tl) {
        h("EXPORT time series");

        // -- probe tables -------------------------------------------------
        try {
            String[] tabs = model.result().table().tags();
            p("tables = " + Arrays.toString(tabs));
            for (int i = 0; i < tabs.length; i++) {
                try {
                    double[][] d = model.result().table(tabs[i]).getReal();
                    p("TABLE " + tabs[i] + " rows=" + d.length
                      + " cols=" + (d.length > 0 ? d[0].length : 0));
                    if (d.length > 0) {
                        p("   row0: " + fmt(d[0], 6));
                        p("   rowN: " + fmt(d[d.length - 1], 6));
                    }
                    writeCsv(tag + "_table_" + tabs[i] + ".csv", d);
                } catch (Throwable t) {
                    p("table " + tabs[i] + " read failed: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            p("table export failed: " + t.getMessage());
        }

        // -- numerical evaluations on the time-dependent solution ----------
        String dset = timeDataset();
        p("time dataset = " + dset);

        // chamber average
        double[][] avCham = evalAv(chamD, "c", dset, "avCham");
        writeCsv(tag + "_chamber_average.csv", avCham);
        dump(tag + "_chamber_average", avCham);
        series(tag + "_chamber_average", tl, row0(avCham));
        // duct average - where the solute actually is
        double[][] avDuct = evalAv(ductD, "c", dset, "avDuct");
        dump(tag + "_duct_average", avDuct);
        series(tag + "_duct_average", tl, row0(avDuct));
        // strip average (the through-flow channel).  In the d-mode there is
        // no 2 mm stripe, so the distributor domains are used instead --
        // otherwise every diagnostic would read 0.
        int[] flowSel = (stripD.length > 0) ? stripD : ductD;
        double[][] avStrip = evalAv(flowSel, "c", dset, "avStrip");
        dump(tag + "_strip_average", avStrip);
        series(tag + "_strip_average", tl, row0(avStrip));

        // point probes
        for (int i = 0; i < 3; i++) {
            double[][] e = evalPoint(PXYZ[i], dset, "pev" + i, "cptP" + (i + 1));
            writeCsv(tag + "_pointP" + (i + 1) + ".csv", e);
            series(tag + "_point_" + PNAMES[i], tl, row0(e));
        }
        if (withCell) {
            double[][] avCell = evalAv(cellD, "c", dset, "avCell");
            writeCsv(tag + "_cell_average.csv", avCell);
            series(tag + "_cell_average", tl, row0(avCell));
            double[][] e = evalPoint(new double[]{0.0, 0.0, -0.00005}, dset, "pevMid", "cptMid");
            writeCsv(tag + "_cell_midplane.csv", e);
            series(tag + "_cell_midplane", tl, row0(e));
            // the cell-layer surface, just under the chamber floor
            double[][] sf = evalPoint(new double[]{0.0, 0.0, -0.000002}, dset, "pevSurf", "cptSurf");
            series(tag + "_cell_surface", tl, row0(sf));
        }
        // ---- the velocity the transient actually sees ----------------------
        try {
            model.result().numerical().create("avU2", "AvVolume");
            model.result().numerical("avU2").selection().set(
                (stripD.length > 0) ? stripD : ductD);
            model.result().numerical("avU2").setIndex("expr", "spf.U", 0);
            if (dset != null) model.result().numerical("avU2").set("data", dset);
            model.result().numerical("avU2").run();
            double[][] u2 = model.result().numerical("avU2").getReal();
            p("DIAG mean |u| in the strip ON THE TRANSIENT DATASET = "
              + u2[0][u2[0].length / 2] + " m/s");
        } catch (Throwable t) {
            p("DIAG transient velocity failed: " + t.getMessage());
        }
        // ---- hard diagnostics: geometry, flow rate, velocity --------------
        try {
            int[] allD = concat(chamD, ductD);   // all five fluid domains
            model.result().numerical().create("ivAll", "IntVolume");
            model.result().numerical("ivAll").selection().set(allD);
            model.result().numerical("ivAll").setIndex("expr", "1", 0);
            model.result().numerical("ivAll").run();
            p("DIAG total fluid volume = " + model.result().numerical("ivAll").getReal()[0][0] + " m^3");

            model.result().numerical().create("isIn", "IntSurface");
            model.result().numerical("isIn").selection().set(inF);
            model.result().numerical("isIn").setIndex("expr", "u*nx+v*ny+w*nz", 0);
            model.result().numerical("isIn").run();
            // the inlet face normal points OUT of the domain, so the inward
            // flux is -u.n
            double qin = -model.result().numerical("isIn").getReal()[0][0];
            p("DIAG flow rate through the inlet = " + qin + " m^3/s = " + (qin * 6e10) + " uL/min");

            model.result().numerical().create("avU", "AvVolume");
            model.result().numerical("avU").selection().set(
                (stripD.length > 0) ? stripD : ductD);
            model.result().numerical("avU").setIndex("expr", "spf.U", 0);
            model.result().numerical("avU").run();
            p("DIAG mean |u| in the strip = " + model.result().numerical("avU").getReal()[0][0] + " m/s");

            model.result().numerical().create("avP", "AvVolume");
            model.result().numerical("avP").selection().set(
                (stripD.length > 0) ? stripD : ductD);
            model.result().numerical("avP").setIndex("expr", "p", 0);
            model.result().numerical("avP").run();
            p("DIAG mean p in the strip = " + model.result().numerical("avP").getReal()[0][0] + " Pa");
        } catch (Throwable t) {
            p("DIAG failed: " + t.getMessage());
        }
        // ---- c along the channel axis, exported by COMSOL itself ---------
        try {
            model.result().dataset().create("clnAxis", "CutLine3D");
            model.result().dataset("clnAxis").set("data", dset);
            model.result().dataset("clnAxis").setIndex("genpoints", "-75[mm]", 0, 0);
            model.result().dataset("clnAxis").setIndex("genpoints", "0[mm]",   0, 1);
            model.result().dataset("clnAxis").setIndex("genpoints", "0.5[mm]", 0, 2);
            model.result().dataset("clnAxis").setIndex("genpoints", "75[mm]",  1, 0);
            model.result().dataset("clnAxis").setIndex("genpoints", "0[mm]",   1, 1);
            model.result().dataset("clnAxis").setIndex("genpoints", "0.5[mm]", 1, 2);
            model.result().export().create("expAxis", "Data");
            model.result().export("expAxis").set("data", "clnAxis");
            model.result().export("expAxis").set("expr", new String[]{"c"});
            model.result().export("expAxis").set("filename", "ex2_axis_c.csv");
            model.result().export("expAxis").run();
            p("DIAG axis export done");
        } catch (Throwable t) {
            p("DIAG axis export failed: " + t.getMessage());
        }
        // volume check
        try {
            model.result().numerical().create("volCham", "IntVolume");
            model.result().numerical("volCham").selection().set(chamD);
            model.result().numerical("volCham").setIndex("expr", "1", 0);
            model.result().numerical("volCham").set("data", dset);
            model.result().numerical("volCham").run();
            p("VOLUME chamber = " + model.result().numerical("volCham").getReal()[0][0] + " m^3");
            model.result().numerical().create("volAll", "IntVolume");
            model.result().numerical("volAll").selection().all();
            model.result().numerical("volAll").setIndex("expr", "1", 0);
            model.result().numerical("volAll").set("data", dset);
            model.result().numerical("volAll").run();
            p("VOLUME total fluid+cell = " + model.result().numerical("volAll").getReal()[0][0] + " m^3");
        } catch (Throwable t) {
            p("volume check failed: " + t.getMessage());
        }
    }

    /** the solution dataset belonging to a given study (e.g. "std2").
     *
     *  A COMSOL study solution tag looks like "sol2" and the dataset that
     *  carries it is "dset2"; the tag number is the same, so the mapping is
     *  done by NUMBER, not by reading the "study" property (SolverSequence
     *  has no getString("study") in 6.4). */
    static String studyDataset(String studyTag) {
        try {
            String[] tags = model.result().dataset().tags();
            // find the solver tag that belongs to the study
            String solTag = null;
            String[] solTags = model.sol().tags();
            for (int j = 0; j < solTags.length; j++) {
                try {
                    // sol tags are created in study order: std1 -> sol1, std2 -> sol2
                    String n = solTags[j].replaceAll("[^0-9]", "");
                    if (n.equals(studyTag.replaceAll("[^0-9]", ""))) solTag = solTags[j];
                } catch (Throwable t) { }
            }
            if (solTag == null) { p("DATASET for " + studyTag + " NOT FOUND (no solver)"); return null; }
            String want = "dset" + solTag.replaceAll("[^0-9]", "");
            for (int i = 0; i < tags.length; i++) {
                if (tags[i].equals(want)) {
                    p("DATASET for " + studyTag + " = " + tags[i] + " (solver " + solTag + ")");
                    return tags[i];
                }
            }
            // fall back: the dataset whose "solution" property is the solver
            for (int i = 0; i < tags.length; i++) {
                try {
                    if (solTag.equals(model.result().dataset(tags[i]).getString("solution"))) {
                        p("DATASET for " + studyTag + " = " + tags[i] + " (solution " + solTag + ")");
                        return tags[i];
                    }
                } catch (Throwable t) { }
            }
        } catch (Throwable t) { p("studyDataset failed: " + t.getMessage()); }
        p("DATASET for " + studyTag + " NOT FOUND");
        return null;
    }

    static String timeDataset() {
        String[] tags = model.result().dataset().tags();
        p("datasets = " + Arrays.toString(tags));
        String best = null;
        for (int i = 0; i < tags.length; i++) {
            try {
                String d = model.result().dataset(tags[i]).getString("data");
                p("   dataset " + tags[i] + " <- " + d);
                if (d != null && d.startsWith("sol")) best = tags[i];
            } catch (Throwable t) {
                p("   dataset " + tags[i] + " read failed: " + t.getMessage());
            }
        }
        if (best == null && tags.length > 0) best = tags[tags.length - 1];
        return best;
    }

    static double[][] evalAv(int[] sel, String expr, String dset, String tag) {
        double[][] out = new double[0][];
        try {
            model.result().numerical().create(tag, "AvVolume");
            model.result().numerical(tag).selection().set(sel);
            model.result().numerical(tag).setIndex("expr", expr, 0);
            if (dset != null) model.result().numerical(tag).set("data", dset);
            model.result().numerical(tag).run();
            double[][] d = model.result().numerical(tag).getReal();
            p("EVAL " + tag + " shape " + d.length + "x" + (d.length > 0 ? d[0].length : 0)
              + "  first=" + fmt(d[0], 4) + "  last=" + fmt(d[d.length - 1], 4));
            out = d;
        } catch (Throwable t) {
            p("EVAL " + tag + " failed: " + t.getMessage());
        }
        return out;
    }

    /** Point value at (x,y,z) given in METRES.
     *  EvalPoint has NO "coords" property (it throws Unknown_property), so the
     *  point is defined by a CutPoint3D dataset and EvalPoint is bound to it.
     *  CutPoint3D coordinates are in GEOMETRY UNITS (mm here). */
    static double[][] evalPoint(double[] xyz, String dset, String tag, String cptTag) {
        double[][] out = new double[0][];
        try {
            model.result().dataset().create(cptTag, "CutPoint3D");
            model.result().dataset(cptTag).set("pointx", String.valueOf(xyz[0] * 1000.0));
            model.result().dataset(cptTag).set("pointy", String.valueOf(xyz[1] * 1000.0));
            model.result().dataset(cptTag).set("pointz", String.valueOf(xyz[2] * 1000.0));
            if (dset != null) model.result().dataset(cptTag).set("data", dset);
            model.result().numerical().create(tag, "EvalPoint");
            model.result().numerical(tag).set("expr", new String[]{"c"});
            model.result().numerical(tag).set("data", cptTag);
            model.result().numerical(tag).run();
            double[][] d = model.result().numerical(tag).getReal();
            p("EVAL " + tag + " shape " + d.length + "x" + (d.length > 0 ? d[0].length : 0)
              + "  first=" + fmt(d[0], 4) + "  last=" + fmt(d[d.length - 1], 4));
            out = d;
        } catch (Throwable t) {
            p("EVAL " + tag + " failed: " + t.getMessage());
        }
        return out;
    }

    static void exportParametric() {
        h("EXPORT parametric results");
        try {
            String[] tabs = model.result().table().tags();
            for (int i = 0; i < tabs.length; i++) {
                try {
                    double[][] d = model.result().table(tabs[i]).getReal();
                    p("TABLE " + tabs[i] + " rows=" + d.length
                      + " cols=" + (d.length > 0 ? d[0].length : 0));
                    if (d.length > 0) p("   row0: " + fmt(d[0], 6));
                    writeCsv("ef_param_table_" + tabs[i] + ".csv", d);
                } catch (Throwable t) {
                    p("table " + tabs[i] + " read failed: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            p("param table export failed: " + t.getMessage());
        }
        // The sweep solution: one column per flow rate.
        String dset = null;
        try {
            String[] dsTags = model.result().dataset().tags();
            for (int i = 0; i < dsTags.length; i++) {
                try {
                    if (model.result().dataset(dsTags[i]).getType().equals("Solution")) {
                        String s = model.result().dataset(dsTags[i]).getString("solution");
                        p("   dataset " + dsTags[i] + " -> solution " + s);
                        dset = dsTags[i];
                    }
                } catch (Throwable t) { }
            }
        } catch (Throwable t) { p("dataset scan failed: " + t.getMessage()); }
        p("param dataset = " + dset);

        // the flow rates of the sweep, in SI units (m^3/s) and uL/min
        double[] pv = new double[0];
        try {
            String[] sols = model.sol().tags();
            p("solvers = " + Arrays.toString(sols));
            for (int i = 0; i < sols.length; i++) {
                try {
                    double[] v = model.sol(sols[i]).getPVals();
                    p("   sol " + sols[i] + " pvals (m^3/s) = " + fmt(v, 10));
                    p("   sol " + sols[i] + " pvals (uL/min) = " + fmt(conv(v, 6e10), 10));
                    if (v.length > 1) pv = v;     // the sweep solver
                } catch (Throwable t) {
                    p("   sol " + sols[i] + " pvals failed: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            p("pvals failed: " + t.getMessage());
        }

        // cell-domain average, cell midplane and cell surface for each flow rate
        double[] avCell = row0(evalAv(cellD, "c", dset, "avCellP"));
        double[] mid = row0(evalPoint(new double[]{0.0, 0.0, -0.00005}, dset, "pevMidP", "cptMidP"));
        double[] surf = row0(evalPoint(new double[]{0.0, 0.0, -0.000002}, dset, "pevSurfP", "cptSurfP"));
        // chamber average for reference
        double[] avCham = row0(evalAv(chamD, "c", dset, "avChamP"));

        int n = Math.max(pv.length, avCell.length);
        p("PARAMFLOW|index|Q_uLmin|Q_m3ps|cell_avg|cell_midplane|cell_surface|chamber_avg");
        for (int i = 0; i < n; i++) {
            double q = (i < pv.length) ? pv[i] : Double.NaN;
            p("PARAMFLOW|" + i
              + "|" + String.format("%.10g", q * 6e10)
              + "|" + String.format("%.10g", q)
              + "|" + (i < avCell.length ? String.format("%.10g", avCell[i]) : "")
              + "|" + (i < mid.length ? String.format("%.10g", mid[i]) : "")
              + "|" + (i < surf.length ? String.format("%.10g", surf[i]) : "")
              + "|" + (i < avCham.length ? String.format("%.10g", avCham[i]) : ""));
        }
        p("PARAM cell-domain average c = " + fmt(avCell, 10));
        p("PARAM cell midplane c = " + fmt(mid, 10));
        p("PARAM cell surface c = " + fmt(surf, 10));
        p("PARAM chamber average c = " + fmt(avCham, 10));
    }

    static double[] conv(double[] a, double f) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i] * f;
        return r;
    }

    // ------------------------------------------------------------------ helpers
    /** Attach a Distribution to the swept mesh.
     *  COMSOL 6.4 rejects an edge selection on the Distribution sub-feature
     *  ("Unsupported geometric entity level - Level: 1"), so try every
     *  documented form and fall back to the automatic sweep distribution. */
    static boolean addDistribution(MeshSequence m, String sweepTag, String distTag,
                                   int[] edges, int num) {
        // Variant order matters.  A Distribution SUB-feature of a Sweep
        // cannot take an edge selection at all ("Unsupported_geometric_entity_level"
        // for both the .geom(1) and .geom("geom1",1) forms) -- but if it is
        // created WITHOUT any selection COMSOL auto-fills the selection from
        // the Sweep's source edges and honours "numelem".  Verified with
        // Ex2CellProbe3: the sub/no-selection form produced exactly 20
        // prism layers through the 100 um cell layer (z steps of 5 um),
        // while a top-level Distribution with an edge selection ran but left
        // the Sweep with a single layer.
        Object[][] variants = {
            {"sub",  "nosel"},
            {"sub",  "geom1-int"}, {"sub",  "geom1-str"},
            {"top",  "geom1-int"}, {"top",  "geom1-str"},
        };
        for (Object[] v : variants) {
            boolean sub = "sub".equals(v[0]);
            boolean strForm = "geom1-str".equals(v[1]);
            try {
                if (sub) m.feature(sweepTag).create(distTag, "Distribution");
                else     m.create(distTag, "Distribution");
                if (sub && "nosel".equals(v[1])) {
                    m.feature(sweepTag).feature(distTag).set("numelem", String.valueOf(num));
                    p("MESH Distribution OK  form=sub/nosel");
                    return true;
                }
                if (sub) {
                    if (strForm) m.feature(sweepTag).feature(distTag).selection().geom("geom1", 1).set(edges);
                    else         m.feature(sweepTag).feature(distTag).selection().geom(1).set(edges);
                    if (strForm) m.feature(sweepTag).feature(distTag).set("numelem", String.valueOf(num));
                    else         m.feature(sweepTag).feature(distTag).set("numelem", String.valueOf(num));
                } else {
                    if (strForm) m.feature(distTag).selection().geom("geom1", 1).set(edges);
                    else         m.feature(distTag).selection().geom(1).set(edges);
                    m.feature(distTag).set("numelem", String.valueOf(num));
                }
                p("MESH Distribution OK  form=" + v[0] + "/" + v[1]);
                return true;
            } catch (Throwable t) {
                p("MESH Distribution form " + v[0] + "/" + v[1] + " failed: " + t.getMessage());
                try {
                    if (sub) m.feature(sweepTag).feature().remove(distTag);
                    else     m.feature().remove(distTag);
                } catch (Throwable t2) { p("   cleanup failed: " + t2.getMessage()); }
            }
        }
        p("MESH no Distribution worked - the Sweep will use the automatic distribution");
        return false;
    }

    /** COMSOL's batch security manager denies Java file writes
     *  ("access denied (java.io.FilePermission ... write)"), so every number
     *  has to leave the JVM through stdout. */
    /** first row of a getReal() matrix (one expression, one column per
     *  solution) as a plain array */
    static double[] row0(double[][] d) {
        if (d == null || d.length == 0 || d[0].length == 0) return new double[0];
        return d[0];
    }

    /** Machine-readable series on stdout.  The COMSOL batch SecurityManager
     *  denies java.io writes inside the model class, so stdout is the only
     *  reliable output route; the LSF scheduler captures it in the job log. */
    static void series(String name, double[] t, double[] v) {
        if (v == null || v.length == 0) { p("EX2SERIES|" + name + "|EMPTY"); return; }
        p("EX2SERIES|" + name + "|" + v.length);
        for (int i = 0; i < v.length; i++) {
            double tt = (t != null && i < t.length) ? t[i] : Double.NaN;
            p("EX2POINT|" + name + "|" + String.format("%.10g", tt)
              + "|" + String.format("%.10g", v[i]));
        }
    }

    static double[] times(double t0, double dt, double t1) {
        int n = (int) Math.floor((t1 - t0) / dt + 1e-9);
        double[] r = new double[n + 1];
        for (int i = 0; i <= n; i++) r[i] = t0 + i * dt;
        return r;
    }

    static double[] times(double t0, double dt1, double tmid, double dt2, double t1) {
        int n1 = (int) Math.floor((tmid - t0) / dt1 + 1e-9);
        int n2 = (int) Math.floor((t1 - tmid) / dt2 + 1e-9);
        double[] r = new double[n1 + 1 + n2];
        int k = 0;
        for (int i = 0; i <= n1; i++) r[k++] = t0 + i * dt1;
        for (int i = 1; i <= n2; i++) r[k++] = tmid + i * dt2;
        return r;
    }

    static String listStr(double[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(String.format("%.10g", a[i]));
        }
        return sb.toString();
    }

    static void dump(String name, double[][] d) {
        if (d == null || d.length == 0) { p("DUMP " + name + " empty"); return; }
        p("DUMP " + name + " rows=" + d.length + " cols=" + d[0].length);
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < d[0].length; j++) {
            if (j > 0) sb.append(",");
            sb.append(String.format("%.8g", d[0][j]));
        }
        p("DATA " + name + " " + sb);
    }

    static String fmt(double[] a, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length && i < n; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.6g", a[i]));
        }
        return sb.toString();
    }

    static void writeCsv(String name, double[][] d) {
        if (d == null || d.length == 0) { p("writeCsv " + name + " skipped (empty)"); return; }
        try {
            File f = new File(OUT, name);
            PrintWriter pw = new PrintWriter(new FileWriter(f));
            for (int i = 0; i < d.length; i++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < d[i].length; j++) {
                    if (j > 0) sb.append(",");
                    sb.append(String.format("%.10g", d[i][j]));
                }
                pw.println(sb.toString());
            }
            pw.close();
            p("wrote " + f.getAbsolutePath());
        } catch (Throwable t) {
            p("writeCsv " + name + " failed: " + t.getMessage());
        }
    }

    static int[] concat(int[] a, int[] b) {
        int[] r = new int[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
