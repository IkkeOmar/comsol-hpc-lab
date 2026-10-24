import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;
import java.io.*;

/**
 * ProbeField: end-to-end validation of the Ex-4 physics chain on a REDUCED
 * geometry (400 um long, 9 electrodes) so it runs in minutes.
 *
 * What it checks, and why each check matters:
 *  F1  Electrostatics: the default domain node is "Free Space 1" (VACUUM -
 *      ignores the material permittivity).  Add a ChargeConservation node
 *      with epsilonr_mat = from_mat and check the solve succeeds.
 *  F2  The field profile |E|^2 above the gap centre must reproduce the
 *      course formula  |grad E|^2 = (32/pi)(V0^2/d^3) exp(-pi y/d).
 *      This validates the mesh AND the physics setup at once.
 *  F3  Laminar Flow with a "Laminar inflow" (fully developed) inlet:
 *      does the flow rate come out as Q = 1 uL/h ?
 *  F4  Particle release AT the inlet wall (x = 0): with wall1 = "Freeze" the
 *      particles may be frozen at t = 0.  Check x(t) actually moves.
 *  F5  z(t) of a released particle vs the overdamped analytical model.
 *      (z(t) is independent of the flow profile, so this is a sharp test of
 *      the DEP force, including the RMS-vs-peak convention.)
 *  F6  Study 2 coupled to Study 1 via "values of variables not solved for"
 *      (usesol / notsolmethod / notstudy) - the assignment's requirement.
 */
public class ProbeField {

    static final String OUT = "<project>/comsol/pf_";

    public static void main(String[] args) {
        try {
            Model m = ModelUtil.create("PF");

            // ---------------- parameters ----------------------------------
            m.param().set("L_ch", "400[um]");
            m.param().set("W_ch", "100[um]");
            m.param().set("H_ch", "100[um]");
            m.param().set("N_el", "9");
            m.param().set("elW",  "10[um]");
            m.param().set("elP",  "20[um]");
            m.param().set("elX0", "50[um]");
            m.param().set("V_el", "10[V]");
            m.param().set("Q",    "1[ul/h]");
            m.param().set("U_avg", "Q/(W_ch*H_ch)");
            m.param().set("dp",     "2.4[um]");
            m.param().set("rho_p",  "1200[kg/m^3]");
            m.param().set("epsr_p", "60");
            m.param().set("sigma_p", "0.01[S/m]");
            m.param().set("epsr_m", "80");
            m.param().set("sigma_m", "5.5[us/m]");
            m.param().set("f_AC",  "10[MHz]");
            m.param().set("g_c",   "9.80665[m/s^2]");

            // ---------------- geometry -------------------------------------
            m.component().create("comp1");
            GeomSequence g = m.component("comp1").geom().create("geom1", 3);
            g.lengthUnit("um");
            g.create("blk1", "Block");
            g.feature("blk1").set("size", new String[]{"L_ch", "W_ch", "H_ch"});
            g.feature("blk1").set("pos",  new String[]{"0", "0", "0"});
            g.create("wp1", "WorkPlane");
            g.feature("wp1").set("planetype", "quick");
            g.feature("wp1").set("quickplane", "xy");
            GeomSequence wp = g.feature("wp1").geom();
            for (int i = 0; i < 9; i++) {
                wp.create("r" + i, "Rectangle");
                wp.feature("r" + i).set("size", new String[]{"elW", "W_ch"});
                wp.feature("r" + i).set("pos",  new String[]{"elX0 + " + i + "*elP", "0"});
            }
            g.run("wp1");

            g.create("selIn", "BoxSelection");
            g.feature("selIn").set("entitydim", 2);
            g.feature("selIn").set("condition", "allvertices");
            g.feature("selIn").set("xmin", "-1[um]"); g.feature("selIn").set("xmax", "1[um]");
            g.feature("selIn").set("ymin", "-1[um]"); g.feature("selIn").set("ymax", "W_ch+1[um]");
            g.feature("selIn").set("zmin", "-1[um]"); g.feature("selIn").set("zmax", "H_ch+1[um]");

            g.create("selOut", "BoxSelection");
            g.feature("selOut").set("entitydim", 2);
            g.feature("selOut").set("condition", "allvertices");
            g.feature("selOut").set("xmin", "L_ch-1[um]"); g.feature("selOut").set("xmax", "L_ch+1[um]");
            g.feature("selOut").set("ymin", "-1[um]"); g.feature("selOut").set("ymax", "W_ch+1[um]");
            g.feature("selOut").set("zmin", "-1[um]"); g.feature("selOut").set("zmax", "H_ch+1[um]");

            for (int i = 0; i < 9; i++) {
                String tag = "selE" + i;
                g.create(tag, "BoxSelection");
                g.feature(tag).set("entitydim", 2);
                g.feature(tag).set("condition", "allvertices");
                g.feature(tag).set("xmin", "elX0 + " + i + "*elP - 1[um]");
                g.feature(tag).set("xmax", "elX0 + " + i + "*elP + elW + 1[um]");
                g.feature(tag).set("ymin", "-1[um]");
                g.feature(tag).set("ymax", "W_ch+1[um]");
                g.feature(tag).set("zmin", "-1[um]");
                g.feature(tag).set("zmax", "1[um]");
            }
            g.create("selFloorE", "BoxSelection");
            g.feature("selFloorE").set("entitydim", 1);
            g.feature("selFloorE").set("condition", "allvertices");
            g.feature("selFloorE").set("xmin", "-1[um]"); g.feature("selFloorE").set("xmax", "L_ch+1[um]");
            g.feature("selFloorE").set("ymin", "-1[um]"); g.feature("selFloorE").set("ymax", "W_ch+1[um]");
            g.feature("selFloorE").set("zmin", "-1[um]"); g.feature("selFloorE").set("zmax", "1[um]");
            g.run();

            int[] inB = m.selection("geom1_selIn").entities(2);
            int[] outB = m.selection("geom1_selOut").entities(2);
            int[] floorE = m.selection("geom1_selFloorE").entities(1);
            java.util.List<Integer> termF = new java.util.ArrayList<Integer>();
            java.util.List<Integer> gndF = new java.util.ArrayList<Integer>();
            for (int i = 0; i < 9; i++) {
                int[] s = m.selection("geom1_selE" + i).entities(2);
                System.out.println("F0 electrode " + i + " faces " + Arrays.toString(s));
                if (i % 2 == 0) { for (int v : s) termF.add(v); }
                else           { for (int v : s) gndF.add(v); }
            }
            int[] termB = new int[termF.size()];
            for (int i = 0; i < termB.length; i++) termB[i] = termF.get(i);
            int[] gndB = new int[gndF.size()];
            for (int i = 0; i < gndB.length; i++) gndB[i] = gndF.get(i);
            System.out.println("F0 inlet " + Arrays.toString(inB) + " outlet " + Arrays.toString(outB)
                             + " Terminal " + Arrays.toString(termB) + " Ground " + Arrays.toString(gndB));

            // ---------------- material -------------------------------------
            m.component("comp1").material().create("mat1", "Common");
            m.component("comp1").material("mat1").label("Water");
            m.component("comp1").material("mat1").propertyGroup("def")
                .set("dynamicviscosity", "1e-3[Pa*s]")
                .set("density", "1000[kg/m^3]")
                .set("electricconductivity", "sigma_m")
                .set("relpermittivity", "epsr_m");

            // ---------------- physics: flow --------------------------------
            m.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            m.component("comp1").physics("spf").create("inl1", "Inlet", 2);
            m.component("comp1").physics("spf").feature("inl1").selection().set(inB);
            try {
                m.component("comp1").physics("spf").feature("inl1").set("BoundaryCondition", "LaminarInflow");
                m.component("comp1").physics("spf").feature("inl1").set("Uav", "U_avg");
                System.out.println("F3 LaminarInflow + Uav ACCEPTED");
            } catch (Throwable t) {
                System.out.println("F3 LaminarInflow FAILED: " + t.getMessage()
                        + " -> falling back to velocity inlet");
                m.component("comp1").physics("spf").feature("inl1").set("U0in", "U_avg");
            }
            System.out.println("F3 LaminarInflowOption allowed: " + Arrays.toString(
                    m.component("comp1").physics("spf").feature("inl1")
                     .getAllowedPropertyValues("LaminarInflowOption")));
            m.component("comp1").physics("spf").create("out1", "Outlet", 2);
            m.component("comp1").physics("spf").feature("out1").selection().set(outB);
            m.component("comp1").physics("spf").feature("out1").set("p0", "0");

            // ---------------- physics: electrostatics ----------------------
            m.component("comp1").physics().create("es", "Electrostatics", "geom1");
            try {
                m.component("comp1").physics("es").create("cc1", "ChargeConservation", 3);
                m.component("comp1").physics("es").feature("cc1").selection().all();
                m.component("comp1").physics("es").feature("cc1").set("epsilonr_mat", "from_mat");
                m.component("comp1").physics("es").feature("cc1").set("materialType", "from_mat");
                System.out.println("F1 ChargeConservation cc1 created (eps_r from material)");
            } catch (Throwable t) {
                System.out.println("F1 ChargeConservation FAILED: " + t.getMessage());
            }
            m.component("comp1").physics("es").create("gnd1", "Ground", 2);
            m.component("comp1").physics("es").feature("gnd1").selection().set(gndB);
            m.component("comp1").physics("es").create("term1", "Terminal", 2);
            m.component("comp1").physics("es").feature("term1").selection().set(termB);
            m.component("comp1").physics("es").feature("term1").set("TerminalType", "Voltage");
            m.component("comp1").physics("es").feature("term1").set("V0", "V_el");

            // ---------------- physics: particle tracing ---------------------
            m.component("comp1").physics().create("fpt", "FluidParticleTracing", "geom1");
            m.component("comp1").physics("fpt").prop("Formulation")
             .setIndex("Formulation", "NewtonianIgnoreInertialTerms", 0);
            m.component("comp1").physics("fpt").feature("pp1").set("rhop_mat", "userdef");
            m.component("comp1").physics("fpt").feature("pp1").set("rhop", "rho_p");
            m.component("comp1").physics("fpt").feature("pp1").set("dp", "dp");
            m.component("comp1").physics("fpt").feature("pp1").set("erp_mat", "userdef");
            m.component("comp1").physics("fpt").feature("pp1").set("erp", "epsr_p");
            m.component("comp1").physics("fpt").feature("pp1").set("sigmap_mat", "userdef");
            m.component("comp1").physics("fpt").feature("pp1").set("sigmap", "sigma_p");

            m.component("comp1").physics("fpt").create("df1", "DragForce", 3);
            m.component("comp1").physics("fpt").feature("df1").selection().all();
            m.component("comp1").physics("fpt").feature("df1").set("DragLaw", "Stokes");
            m.component("comp1").physics("fpt").feature("df1").set("u_src", "root.comp1.u");

            m.component("comp1").physics("fpt").create("gf1", "GravityForce", 3);
            m.component("comp1").physics("fpt").feature("gf1").selection().all();
            m.component("comp1").physics("fpt").feature("gf1").set("g", new String[]{"0", "0", "-g_c"});
            m.component("comp1").physics("fpt").feature("gf1").set("rho_mat", "root.comp1.spf.rho");

            m.component("comp1").physics("fpt").create("deff1", "DielectrophoreticForce", 3);
            m.component("comp1").physics("fpt").feature("deff1").selection().all();
            try {
                m.component("comp1").physics("fpt").feature("deff1").set("E_src", "root.comp1.es.Ex");
                System.out.println("F2 E_src = root.comp1.es.Ex ACCEPTED");
            } catch (Throwable t) {
                System.out.println("F2 E_src es.Ex FAILED: " + t.getMessage());
                m.component("comp1").physics("fpt").feature("deff1").set("E_src", "userdef");
                m.component("comp1").physics("fpt").feature("deff1")
                    .set("E", new String[]{"es.Ex", "es.Ey", "es.Ez"});
            }
            m.component("comp1").physics("fpt").feature("deff1").set("UsePPR", true);
            m.component("comp1").physics("fpt").feature("deff1").set("AngularFrequency", "UserDefined");
            m.component("comp1").physics("fpt").feature("deff1").set("omega", "2*pi*f_AC");
            try {
                m.component("comp1").physics("fpt").feature("deff1").set("epsilonr_mat", "from_mat");
                m.component("comp1").physics("fpt").feature("deff1").set("sigma_mat", "from_mat");
                System.out.println("F2 DEP fluid props from material ACCEPTED");
            } catch (Throwable t) {
                System.out.println("F2 DEP fluid props FAILED: " + t.getMessage());
            }

            m.component("comp1").physics("fpt").feature("wall1").set("WallCondition", "Freeze");
            m.component("comp1").physics("fpt").create("outfpt1", "Outlet", 2);
            m.component("comp1").physics("fpt").feature("outfpt1").selection().set(outB);
            m.component("comp1").physics("fpt").feature("outfpt1").set("WallCondition", "Disappear");

            m.component("comp1").physics("fpt").create("relg1", "ReleaseGrid", -1);
            m.component("comp1").physics("fpt").feature("relg1")
             .setIndex("x0", "0[um]", 0);
            m.component("comp1").physics("fpt").feature("relg1")
             .setIndex("x0", "range(46[um],4[um],54[um])", 1);
            m.component("comp1").physics("fpt").feature("relg1")
             .setIndex("x0", "50[um]", 2);
            m.component("comp1").physics("fpt").feature("relg1").setIndex("rt", "0", 0);
            m.component("comp1").physics("fpt").feature("relg1")
             .set("InitialVelocity", "ConstantSpeedLambertian");
            m.component("comp1").physics("fpt").feature("relg1")
             .set("v0", new String[]{"0", "0", "0"});

            // ---------------- mesh ------------------------------------------
            MeshSequence mesh = m.component("comp1").mesh().create("mesh1", "geom1");
            mesh.create("size1", "Size");
            mesh.feature("size1").set("hmax", "15[um]");
            mesh.feature("size1").set("hmin", "0.4[um]");
            mesh.feature("size1").set("hgrad", 1.35);
            mesh.create("dist1", "Distribution");
            mesh.feature("dist1").selection().geom(1).set(floorE);
            mesh.feature("dist1").set("numelem", 5);
            mesh.create("ftet1", "FreeTet");
            long t0 = System.currentTimeMillis();
            mesh.run();
            System.out.println("MESH elements = " + mesh.stat().getNumElem()
                             + "  minQ = " + mesh.stat().getMinQuality()
                             + "  meanQ = " + mesh.stat().getMeanQuality()
                             + "  [" + (System.currentTimeMillis()-t0) + " ms]");

            // ---------------- studies ---------------------------------------
            m.study().create("std1");
            m.study("std1").create("stat", "Stationary");
            m.study("std1").feature("stat").setSolveFor("/physics/es", false);
            m.study("std1").feature("stat").setSolveFor("/physics/fpt", false);
            m.study("std1").create("freq", "Frequency");
            m.study("std1").feature("freq").setSolveFor("/physics/spf", false);
            m.study("std1").feature("freq").setSolveFor("/physics/fpt", false);
            m.study("std1").feature("freq").set("plist", "f_AC");
            m.study("std1").createAutoSequences("all");
            m.study("std1").run();
            System.out.println("=== STUDY 1 DONE ===");

            // flow rate check
            m.result().numerical().create("intQ", "IntSurface");
            m.result().numerical("intQ").selection().set(outB);
            m.result().numerical("intQ").setIndex("expr", "spf.U*(-nx)", 0);
            m.result().numerical("intQ").run();
            double[][] q = m.result().numerical("intQ").getReal();
            System.out.println("F3 outlet flux = " + q[0][0] + " m^3/s  ->  "
                             + (q[0][0] * 3.6e9) + " uL/h   (target 1 uL/h)");

            // E-field profile on a vertical line above the first gap centre
            // (electrode 0 spans x=50..60 um, electrode 1 spans 70..80 um, so
            //  the gap centre is at x = 65 um)
            m.result().dataset().create("clnE", "CutLine3D");
            m.result().dataset("clnE").set("data", "dset1");
            m.result().dataset("clnE").setIndex("genpoints", "65[um]", 0, 0);
            m.result().dataset("clnE").setIndex("genpoints", "50[um]", 0, 1);
            m.result().dataset("clnE").setIndex("genpoints", "0[um]",  0, 2);
            m.result().dataset("clnE").setIndex("genpoints", "65[um]", 1, 0);
            m.result().dataset("clnE").setIndex("genpoints", "50[um]", 1, 1);
            m.result().dataset("clnE").setIndex("genpoints", "100[um]", 1, 2);
            m.result().create("pgEprof", "PlotGroup1D");
            m.result("pgEprof").create("lngrE", "LineGraph");
            m.result("pgEprof").feature("lngrE").set("data", "clnE");
            m.result("pgEprof").feature("lngrE").set("expr", "es.Ex^2+es.Ey^2+es.Ez^2");
            m.result("pgEprof").feature("lngrE").set("xdata", "arc");
            m.result("pgEprof").run();
            try {
                m.result().export().create("expE", "pgEprof", "Data");
                m.result().export("expE").set("filename", OUT + "Eprof.csv");
                m.result().export("expE").run();
                System.out.println("F2 exported E profile to " + OUT + "Eprof.csv");
            } catch (Throwable t) {
                System.out.println("F2 E profile export FAILED: " + t.getMessage());
            }
            // also dump |E| and its z-derivative as numbers
            try {
                m.result().numerical().create("gev", "EvalGlobal");
                m.result().numerical("gev").set("data", "clnE");
                for (String e : new String[]{"es.Ex^2+es.Ey^2+es.Ez^2",
                        "d(es.Ex^2+es.Ey^2+es.Ez^2,z)", "es.normE"}) {
                    m.result().numerical("gev").setIndex("expr", e, 0);
                    m.result().numerical("gev").run();
                    double[][] v = m.result().numerical("gev").getReal();
                    System.out.println("F2 " + e + " -> rows=" + v.length
                            + " cols=" + (v.length > 0 ? v[0].length : 0));
                }
            } catch (Throwable t) {
                System.out.println("F2 EvalGlobal on cutline failed: " + t.getMessage());
            }

            // ---------------- study 2: transient ------------------------------
            m.study().create("std2");
            m.study("std2").create("time", "Transient");
            m.study("std2").feature("time").setSolveFor("/physics/spf", false);
            m.study("std2").feature("time").setSolveFor("/physics/es", false);
            m.study("std2").feature("time").set("tlist", "range(0,1,20)");
            m.study("std2").feature("time").set("usesol", true);
            m.study("std2").feature("time").set("notsolmethod", "sol");
            m.study("std2").feature("time").set("notstudy", "std1");
            m.study("std2").createAutoSequences("all");
            t0 = System.currentTimeMillis();
            m.study("std2").run();
            System.out.println("=== STUDY 2 DONE [" + (System.currentTimeMillis()-t0) + " ms] ===");
            System.out.println("F6 solution tags: " + Arrays.toString(m.sol().tags()));

            // particle data
            m.result().dataset().create("pdat", "Particle");
            m.result().dataset("pdat").set("solution", "sol2");
            m.result().dataset("pdat").set("posdof",
                new String[]{"comp1.qx", "comp1.qy", "comp1.qz"});
            m.result().dataset("pdat").set("geom", "geom1");
            m.result().dataset("pdat").set("pgeom", "pgeom_fpt");
            m.result().dataset("pdat").set("pgeomspec", "fromphysics");
            m.result().dataset("pdat").set("physicsinterface", "fpt");

            try {
                m.result().numerical().create("ptab", "Particle");
                m.result().numerical("ptab").set("data", "pdat");
                m.result().numerical("ptab").setIndex("expr", "comp1.qx", 0);
                m.result().numerical("ptab").setIndex("expr", "comp1.qy", 1);
                m.result().numerical("ptab").setIndex("expr", "comp1.qz", 2);
                m.result().numerical("ptab").run();
                double[][] p = m.result().numerical("ptab").getReal();
                System.out.println("F5 Particle numerical: rows=" + p.length
                        + " cols=" + (p.length > 0 ? p[0].length : 0));
                for (int r = 0; r < p.length; r++) {
                    StringBuilder sb = new StringBuilder("F5 row" + r + ":");
                    for (int c = 0; c < Math.min(p[r].length, 60); c++) {
                        sb.append(String.format(" %.6g", p[r][c]));
                    }
                    System.out.println(sb);
                }
            } catch (Throwable t) {
                System.out.println("F5 Particle numerical FAILED: " + t);
            }

            // trajectory plot + CSV export
            try {
                m.result().create("pgTraj", "PlotGroup3D");
                m.result("pgTraj").set("data", "pdat");
                m.result("pgTraj").create("traj1", "ParticleTrajectories");
                m.result("pgTraj").feature("traj1").set("pointtype", "point");
                m.result("pgTraj").feature("traj1").set("linetype", "line");
                m.result("pgTraj").run();
                m.result().export().create("expT", "pgTraj", "Data");
                m.result().export("expT").set("filename", OUT + "traj.csv");
                m.result().export("expT").run();
                System.out.println("F4 trajectory exported to " + OUT + "traj.csv");
            } catch (Throwable t) {
                System.out.println("F4 trajectory export FAILED: " + t);
            }

            m.save(OUT + "ProbeField.mph");
            System.out.println("PROBEFIELD DONE");
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("PROBEFIELD ABORTED: " + t);
        }
    }
}
