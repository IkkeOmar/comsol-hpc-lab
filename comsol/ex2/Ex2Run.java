/* Ex2Run.java -- the lab-on-a-chip course Assignment 1, Exercise 2 (cell growth chamber).
 *
 * COMSOL 6.4 Java-API model.  The configuration is chosen by the first positional
 * argument (comsolbatch -methodcall main <mode> <outdir>):
 *
 *   bc    : parts B + C  -- chamber + crossing channel, rectangular solute pulse,
 *                         chamber-average probe + 3 global point probes.
 *   d     : part D       -- alternative inlet/outlet geometry (full-width distributor
 *                          slots instead of the single 2 mm channel).
 *   ef    : parts E + F  -- adds the cylindrical cell layer (100 um) below the
 *                          chamber floor; transient with a stable 1 mol/m3 inflow
 *                          (part E) and a stationary parametric flow-rate sweep (F).
 *   smoke : like bc but a coarse mesh and a 60 s window (pipeline validation).
 *
 * NOTE ON FILE OUTPUT: the COMSOL batch sandbox denies java.io access to the
 * class, so every number is printed to stdout (captured in the batch log) in a
 * machine-readable form, and additionally written to CSV through COMSOL's own
 * table/export API.  The stdout stream is the primary artefact.
 *
 * GEOMETRY (length unit mm; the model works in SI metres internally)
 *   chamber footprint 60 x 60 mm, 1 mm tall, centred on the origin in x,y,
 *   floor at z = 0, roof at z = +1 mm.  In bc/ef the chamber is split in plan into
 *      strip  : 60 x 2 mm  (the footprint of the crossing channel, y in [-1,1])
 *      side A : 60 x 29 mm (y in [-30,-1])
 *      side B : 60 x 29 mm (y in [1,30])
 *   so the channel stripe can be meshed finely while the dead corners stay cheap.
 *   channel 150 mm x 2 mm x 1 mm, centred on the origin, realised as
 *      duct W : 45 x 2 x 1 mm  at x in [-75,-30]   (inlet)
 *      duct E : 45 x 2 x 1 mm  at x in [ 30, 75]   (outlet)
 *   -> the 150 mm channel = duct W + the 60 mm it crosses inside the chamber
 *      footprint + duct E, exactly as the assignment describes ("both centred at
 *      (0,0,0)").  Total fluid volume 3600 + 90 + 90 = 3780 uL.
 *   d-mode replaces the two ducts by 5 x 60 x 1 mm full-width distributor slots.
 *   cell layer (ef mode only): cylinder r = 25 mm, h = 0.1 mm at (0,0,-0.1)
 *      -> z in [-0.1,0] mm, immediately below the chamber floor, as specified.
 *
 * PHYSICS
 *   spf : LaminarFlow on the fluid domains only (the cell layer is excluded).
 *         Inlet = MassFlowRate rho*Q_in on the inlet face, Outlet p0 = 0.
 *   tds : DilutedSpecies. cdm1 (default "Fluid 1"): u_src = root.comp1.u (coupled
 *         to the laminar flow), D_c = D_sp.  Inflow on the inlet face, Outflow on
 *         the outlet face.  In ef mode an extra "Fluid" domain condition on the
 *         cell layer sets u = 0 (not coupled) and D_c = 0.7*D_sp, and a
 *         "Reactions" domain condition adds R_c = -k_r*c.
 *
 * STUDIES
 *   bc/d : std1 Stationary (spf) -> std2 Transient (tds, velocity taken from std1),
 *          t from -5 s to 3*t_fill, output every 10 s.
 *   ef   : std1 Stationary (spf) -> std2 Transient (tds), t in [0, 3*t_fill],
 *          output every 5 s up to t_fill then every 200 s;
 *          std3 Parametric sweep over Q_in + Stationary(spf) + Stationary(tds,
 *          coupled to step 1)  -> part F.
 *
 * MESH
 *   bc/d : FreeTri on the z=0 faces + one Sweep of the whole fluid from z=0 to
 *          z=1 with a Distribution of 6 layers in z.  Graded in-plane size:
 *          0.3 mm in the channel stripe, 3 mm in the corners.
 *   ef   : the cell cylinder punches a circular hole in the chamber floor, so the
 *          chamber cannot be swept.  Instead: FreeTri on the z=0 faces (chamber
 *          floor + cell top disc, so the two meshes stay conformal), Sweep of the
 *          cell layer from the disc at z=0 down to the disc at z=-0.1, and
 *          FreeTet for the fluid.
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2Run {

    static Model model;
    static String MODE = "bc";
    static String OUT = ".";

    // selections
    static int[] chamD, ductD, cellD, inF, outF, z0F, z1F, cellBotF, cellTopF, vertE, stripD;

    // probe points (metres). z = 50 um above the chamber floor = where cells sit.
    static final double PZ = 0.00005;
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

    /** print a data series in a machine-readable form */
    static void series(String name, double[] t, double[] v) {
        if (v == null || v.length == 0) {
            System.out.println("EX2SERIES|" + name + "|EMPTY");
            return;
        }
        System.out.println("EX2SERIES|" + name + "|" + v.length);
        for (int i = 0; i < v.length; i++) {
            double tt = (t != null && i < t.length) ? t[i] : Double.NaN;
            System.out.println("EX2POINT|" + name + "|" + String.format("%.10g", tt)
                + "|" + String.format("%.10g", v[i]));
        }
    }

    // ------------------------------------------------------------------ main
    public static void main(String[] args) {
        try {
            MODE = (args != null && args.length > 0 && args[0] != null && args[0].length() > 0)
                ? args[0].trim() : "bc";
            OUT = (args != null && args.length > 1 && args[1] != null && args[1].length() > 0)
                ? args[1].trim() : ".";
            p("EX2 MODE = " + MODE + "   OUT = " + OUT);
            model = ModelUtil.create("Ex2Run");
            build(MODE);
        } catch (Throwable e) {
            e.printStackTrace();
            p("EX2 FAILED: " + e);
        }
        p("EX2 DONE");
    }

    // ------------------------------------------------------------------ build
    static void build(String mode) {
        boolean withCell = mode.equals("ef");
        boolean wide = mode.equals("d");
        boolean smoke = mode.equals("smoke");

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
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        if (wide) {
            // part D: full-width distributor slots at both ends
            g.create("blkCh", "Block");
            g.feature("blkCh").set("size", new String[]{"60", "60", "1"});
            g.feature("blkCh").set("pos", new String[]{"-30", "-30", "0"});
            g.create("blkW", "Block");
            g.feature("blkW").set("size", new String[]{"5", "60", "1"});
            g.feature("blkW").set("pos", new String[]{"-35", "-30", "0"});
            g.create("blkE", "Block");
            g.feature("blkE").set("size", new String[]{"5", "60", "1"});
            g.feature("blkE").set("pos", new String[]{"30", "-30", "0"});
        } else {
            g.create("blkStrip", "Block");
            g.feature("blkStrip").set("size", new String[]{"60", "2", "1"});
            g.feature("blkStrip").set("pos", new String[]{"-30", "-1", "0"});
            g.create("blkSideA", "Block");
            g.feature("blkSideA").set("size", new String[]{"60", "29", "1"});
            g.feature("blkSideA").set("pos", new String[]{"-30", "-30", "0"});
            g.create("blkSideB", "Block");
            g.feature("blkSideB").set("size", new String[]{"60", "29", "1"});
            g.feature("blkSideB").set("pos", new String[]{"-30", "1", "0"});
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
        double yl = wide ? -30.1 : -1.1;
        double yh = wide ? 30.1 : 1.1;
        if (!wide) {
            box(g, "selStrip", 3, "inside", -30.1, 30.1, -1.1, 1.1, -0.05, 1.2);
            box(g, "selChamA", 3, "inside", -30.1, 30.1, -30.1, -0.9, -0.05, 1.2);
            box(g, "selChamB", 3, "inside", -30.1, 30.1, 0.9, 30.1, -0.05, 1.2);
        }
        box(g, "selDuctW", 3, "inside", -75.1, -29.9, yl, yh, -0.05, 1.2);
        box(g, "selDuctE", 3, "inside", 29.9, 75.1, yl, yh, -0.05, 1.2);
        box(g, "selCell", 3, "inside", -25.1, 25.1, -25.1, 25.1, -0.15, 0.05);
        box(g, "selIn", 2, "allvertices", -75.1, -74.9, yl, yh, -0.2, 1.2);
        box(g, "selOut", 2, "allvertices", 74.9, 75.1, yl, yh, -0.2, 1.2);
        box(g, "selZ0", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, -0.005, 0.005);
        box(g, "selZ1", 2, "allvertices", -75.1, 75.1, -30.1, 30.1, 0.995, 1.005);
        box(g, "selCellBot", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.105, -0.095);
        box(g, "selCellTop", 2, "allvertices", -25.1, 25.1, -25.1, 25.1, -0.005, 0.005);
        box(g, "selVert", 1, "inside", -75.1, 75.1, -30.1, 30.1, -0.05, 1.05);
        box(g, "selCellVert", 1, "inside", -25.1, 25.1, -25.1, 25.1, -0.101, 0.001);
        g.run();

        int[] sStrip = new int[0];
        if (!wide) sStrip = model.selection("geom1_selStrip").entities(3);
        int[] sChamA = wide ? new int[0] : model.selection("geom1_selChamA").entities(3);
        int[] sChamB = wide ? new int[0] : model.selection("geom1_selChamB").entities(3);
        int[] sDW = model.selection("geom1_selDuctW").entities(3);
        int[] sDE = model.selection("geom1_selDuctE").entities(3);
        stripD = sStrip;
        if (wide) {
            chamD = concat(sDW, sDE);
            chamD = concat(chamD, new int[0]);
            // chamber domain = the one that is neither duct
            chamD = chamberDomains(sDW, sDE);
            ductD = concat(sDW, sDE);
        } else {
            chamD = concat(concat(sStrip, sChamA), sChamB);
            ductD = concat(sDW, sDE);
        }
        cellD = model.selection("geom1_selCell").entities(3);
        inF = model.selection("geom1_selIn").entities(2);
        outF = model.selection("geom1_selOut").entities(2);
        z0F = model.selection("geom1_selZ0").entities(2);
        z1F = model.selection("geom1_selZ1").entities(2);
        cellBotF = model.selection("geom1_selCellBot").entities(2);
        cellTopF = model.selection("geom1_selCellTop").entities(2);
        vertE = model.selection("geom1_selVert").entities(1);
        p("SEL chamber=" + Arrays.toString(chamD) + " ducts=" + Arrays.toString(ductD)
          + " cell=" + Arrays.toString(cellD));
        p("SEL inlet=" + Arrays.toString(inF) + " outlet=" + Arrays.toString(outF));
        p("SEL z0=" + Arrays.toString(z0F) + " z1=" + Arrays.toString(z1F));
        p("SEL cellBot=" + Arrays.toString(cellBotF) + " cellTop=" + Arrays.toString(cellTopF));
        p("SEL vertEdges=" + vertE.length);

        // ---------------- material ---------------------------------------
        model.component("comp1").material().create("mat1", "Common");
        model.component("comp1").material("mat1").label("Water");
        model.component("comp1").material("mat1").propertyGroup("def")
             .set("dynamicviscosity", "mu")
             .set("density", "rho");
        try { model.component("comp1").material("mat1").selection().all(); }
        catch (Throwable t) { p("material selection all() failed: " + t.getMessage()); }

        // ---------------- mesh -------------------------------------------
        buildMesh(withCell, wide, smoke);

        // ---------------- physics ----------------------------------------
        buildPhysics(mode, withCell);

        // ---------------- studies ----------------------------------------
        if (withCell) runEF(smoke); else runBC(smoke);
    }

    /** in wide (d) mode the chamber is the domain that is neither duct */
    static int[] chamberDomains(int[] dW, int[] dE) {
        int[] all = model.component("comp1").geom("geom1").getNDomains() > 0
            ? domainList() : new int[0];
        int[] r = new int[all.length];
        int k = 0;
        for (int i = 0; i < all.length; i++) {
            boolean isDuct = false;
            for (int j = 0; j < dW.length; j++) if (dW[j] == all[i]) isDuct = true;
            for (int j = 0; j < dE.length; j++) if (dE[j] == all[i]) isDuct = true;
            if (!isDuct) r[k++] = all[i];
        }
        int[] out = new int[k];
        System.arraycopy(r, 0, out, 0, k);
        return out;
    }

    static int[] domainList() {
        int n = model.component("comp1").geom("geom1").getNDomains();
        int[] a = new int[n];
        for (int i = 0; i < n; i++) a[i] = i + 1;
        return a;
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
    static void buildMesh(boolean withCell, boolean wide, boolean smoke) {
        h("MESH");
        MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");

        m.create("sizeG", "Size");
        m.feature("sizeG").set("hmax", smoke ? "6" : (wide ? "1" : "3"));
        m.feature("sizeG").set("hmin", "0.05");
        m.feature("sizeG").set("hgrad", "1.3");

        if (!wide) {
            m.create("sizeStrip", "Size");
            m.feature("sizeStrip").selection().geom("geom1", 3).set(stripD);
            m.feature("sizeStrip").set("hmax", smoke ? "1.2" : "0.3");
            m.feature("sizeStrip").set("hmin", "0.05");
            m.feature("sizeStrip").set("hgrad", "1.2");

            m.create("sizeSide", "Size");
            m.feature("sizeSide").selection().geom("geom1", 3).set(
                concat(model.selection("geom1_selChamA").entities(3),
                       model.selection("geom1_selChamB").entities(3)));
            m.feature("sizeSide").set("hmax", "3");
            m.feature("sizeSide").set("hmin", "0.2");
            m.feature("sizeSide").set("hgrad", "1.3");
        }

        m.create("sizeDuct", "Size");
        m.feature("sizeDuct").selection().geom("geom1", 3).set(ductD);
        m.feature("sizeDuct").set("hmax", smoke ? "1.2" : "0.3");
        m.feature("sizeDuct").set("hmin", "0.05");
        m.feature("sizeDuct").set("hgrad", "1.2");

        if (withCell) {
            m.create("sizeCell", "Size");
            m.feature("sizeCell").selection().geom("geom1", 3).set(cellD);
            m.feature("sizeCell").set("hmax", "1.5");
            m.feature("sizeCell").set("hmin", "0.1");
            m.feature("sizeCell").set("hgrad", "1.3");
        }

        // surface mesh of everything at z = 0 (this also provides the source mesh
        // of the cell-layer sweep, so the two domains stay conformal)
        m.create("ftri1", "FreeTri");
        m.feature("ftri1").selection().geom(2).set(z0F);

        if (!withCell) {
            // ---- pure swept mesh: fluid swept from z = 0 to z = 1 --------
            m.create("swe1", "Sweep");
            m.feature("swe1").selection().geom(3).set(concat(chamD, ductD));
            m.feature("swe1").selection("sourceface").geom(2).set(z0F);
            m.feature("swe1").selection("targetface").geom(2).set(z1F);
            m.create("dis1", "Distribution");
            m.feature("dis1").selection().geom("geom1", 1).set(vertE);
            m.feature("dis1").set("numelem", smoke ? "3" : "6");
        } else {
            // ---- ef mode: tets for the fluid, swept prisms for the cell layer ----
            m.create("ftet1", "FreeTet");
            m.feature("ftet1").selection().geom(3).set(concat(chamD, ductD));
            m.create("sweCell", "Sweep");
            m.feature("sweCell").selection().geom(3).set(cellD);
            m.feature("sweCell").selection("sourceface").geom(2).set(cellTopF);
            m.feature("sweCell").selection("targetface").geom(2).set(cellBotF);
            m.create("dis2", "Distribution");
            m.feature("dis2").selection().geom("geom1", 1)
             .set(model.selection("geom1_selCellVert").entities(1));
            m.feature("dis2").set("numelem", smoke ? "2" : "8");
        }
        m.run();
        p("MESH numElem=" + m.stat().getNumElem() + " types=" + Arrays.toString(m.stat().getTypes())
          + " minQ=" + m.stat().getMinQuality() + " meanQ=" + m.stat().getMeanQuality());
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
        try {
            model.component("comp1").physics("spf").feature("inl1")
                 .set("BoundaryCondition", "MassFlow");
            model.component("comp1").physics("spf").feature("inl1")
                 .set("MassFlowType", "MassFlowRate");
            model.component("comp1").physics("spf").feature("inl1")
                 .set("mfr", "rho*Q_in");
            p("spf inlet mfr = " + model.component("comp1").physics("spf").feature("inl1").getString("mfr")
              + " type = " + model.component("comp1").physics("spf").feature("inl1").getString("MassFlowType"));
        } catch (Throwable t) { p("spf inlet FAILED: " + t.getMessage()); }
        model.component("comp1").physics("spf").create("out1", "Outlet", 2);
        model.component("comp1").physics("spf").feature("out1").selection().set(outF);
        try {
            model.component("comp1").physics("spf").feature("out1").set("p0", "0");
            p("spf outlet p0 OK");
        } catch (Throwable t) { p("spf outlet FAILED: " + t.getMessage()); }
        p("spf built");

        // ---- transport of diluted species --------------------------------
        model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
        try {
            model.component("comp1").physics("tds").feature("cdm1").set("u_src", "root.comp1.u");
            model.component("comp1").physics("tds").feature("cdm1").set("D_c", "D_sp");
            p("tds cdm1 u_src = " + model.component("comp1").physics("tds").feature("cdm1").getString("u_src")
              + "  D_c = " + model.component("comp1").physics("tds").feature("cdm1").getString("D_c"));
        } catch (Throwable t) { p("tds cdm1 FAILED: " + t.getMessage()); }

        if (!withCell) {
            // rectangular pulse: start -5 s, end t_fill, transition zone 5, 2 derivatives
            model.func().create("rect1", "Rectangle");
            try {
                model.func("rect1").set("funcname", "rect1");
                model.func("rect1").set("lower", "-5[s]");
                model.func("rect1").set("upper", "t_fill");
                model.func("rect1").set("smooth", "5");
                model.func("rect1").set("smoothactive", "on");
                model.func("rect1").set("ncontder", "2");
                p("rect1 lower=" + model.func("rect1").getString("lower")
                  + " upper=" + model.func("rect1").getString("upper")
                  + " smooth=" + model.func("rect1").getString("smooth")
                  + " smoothactive=" + model.func("rect1").getString("smoothactive")
                  + " ncontder=" + model.func("rect1").getString("ncontder"));
            } catch (Throwable t) { p("rect1 FAILED: " + t.getMessage()); }

            model.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
            model.component("comp1").physics("tds").feature("inflow1").selection().set(inF);
            try {
                model.component("comp1").physics("tds").feature("inflow1").set("c0", "c_in*rect1(t)");
                p("tds inflow c0 = " + model.component("comp1").physics("tds").feature("inflow1").getString("c0"));
            } catch (Throwable t) { p("tds inflow FAILED: " + t.getMessage()); }
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
            model.component("comp1").physics("tds").feature("outflow1").selection().set(outF);
            p("tds pulse built");
        } else {
            // stable inflow, no pulse
            model.component("comp1").physics("tds").create("inflow1", "Inflow", 2);
            model.component("comp1").physics("tds").feature("inflow1").selection().set(inF);
            try {
                model.component("comp1").physics("tds").feature("inflow1").set("c0", "c_in");
                p("tds inflow c0 = " + model.component("comp1").physics("tds").feature("inflow1").getString("c0"));
            } catch (Throwable t) { p("tds inflow FAILED: " + t.getMessage()); }
            model.component("comp1").physics("tds").create("outflow1", "Outflow", 2);
            model.component("comp1").physics("tds").feature("outflow1").selection().set(outF);

            // cell layer: no convection, reduced diffusion (porosity 70 %)
            model.component("comp1").physics("tds").create("fluid2", "Fluid", 3);
            model.component("comp1").physics("tds").feature("fluid2").selection().set(cellD);
            try {
                model.component("comp1").physics("tds").feature("fluid2").set("u_src", "userdef");
                model.component("comp1").physics("tds").feature("fluid2").set("u", new String[]{"0", "0", "0"});
                model.component("comp1").physics("tds").feature("fluid2").set("D_c_mat", "userdef");
                model.component("comp1").physics("tds").feature("fluid2").set("D_c", "eps_D*D_sp");
                p("tds fluid2 u_src = "
                  + model.component("comp1").physics("tds").feature("fluid2").getString("u_src")
                  + "  D_c = " + model.component("comp1").physics("tds").feature("fluid2").getString("D_c"));
            } catch (Throwable t) { p("tds fluid2 FAILED: " + t.getMessage()); }
            // consumption by the cells
            model.component("comp1").physics("tds").create("reac1", "Reactions", 3);
            model.component("comp1").physics("tds").feature("reac1").selection().set(cellD);
            try {
                model.component("comp1").physics("tds").feature("reac1").set("R_c_src", "userdef");
                model.component("comp1").physics("tds").feature("reac1").set("R_c", "-k_r*c");
                p("tds reac1 R_c = " + model.component("comp1").physics("tds").feature("reac1").getString("R_c"));
            } catch (Throwable t) { p("tds reac1 FAILED: " + t.getMessage()); }
            // initial values: the channel (both ducts) already filled with c_in
            model.component("comp1").physics("tds").create("init2", "init", 3);
            model.component("comp1").physics("tds").feature("init2").selection().set(ductD);
            try {
                model.component("comp1").physics("tds").feature("init2").set("initc", "c_in");
                p("tds init2 initc = " + model.component("comp1").physics("tds").feature("init2").getString("initc"));
            } catch (Throwable t) { p("tds init2 FAILED: " + t.getMessage()); }
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
        p("tlist (" + tl.length + " values): " + listStr(tl));
        try {
            model.study("std2").feature("time").set("notsolmethod", "sol");
            model.study("std2").feature("time").set("notstudy", "std1");
            model.study("std2").feature("time").set("notsolnum", "auto");
            p("std2 coupling to std1 set");
        } catch (Throwable t) { p("std2 coupling FAILED: " + t.getMessage()); }
        model.study("std2").feature("time").setSolveFor("/physics/spf", false);
        model.study("std2").feature("time").setSolveFor("/physics/tds", true);
        model.study("std1").createAutoSequences("all");
        model.study("std2").createAutoSequences("all");
        p("studies built; solvers = " + Arrays.toString(model.sol().tags()));

        // ---------------- probes -----------------------------------------
        try {
            model.probe().create("domAvg", "Domain");
            model.probe("domAvg").model("comp1");
            model.probe("domAvg").set("expr", "c");
            model.probe("domAvg").set("type", "average");
            model.probe("domAvg").set("unit", "mol/m^3");
            model.probe("domAvg").set("descr", "chamber average concentration");
            model.probe("domAvg").selection().set(chamD);
            p("domAvg probe OK, selection set");
        } catch (Throwable t) { p("domAvg probe FAILED: " + t.getMessage()); }
        for (int i = 0; i < 3; i++) {
            try {
                model.probe().create("p" + PNAMES[i], "DomainPoint");
                model.probe("p" + PNAMES[i]).model("comp1");
                model.probe("p" + PNAMES[i]).set("coords", new double[][]{PXYZ[i]});
                model.probe("p" + PNAMES[i]).set("dimension", 3);
                model.probe("p" + PNAMES[i]).create("pe", "PointExpr");
                model.probe("p" + PNAMES[i]).feature("pe").set("expr", "c");
                model.probe("p" + PNAMES[i]).feature("pe").set("unit", "mol/m^3");
                model.probe("p" + PNAMES[i]).set("descr", PNAMES[i]);
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
        double[] tl = smoke ? times(0, 5, 60) : times(0, 5, 2160, 200, 6480);
        model.study("std2").feature("time").set("tlist", listStr(tl));
        p("tlist (" + tl.length + " values): " + listStr(tl));
        try {
            model.study("std2").feature("time").set("notsolmethod", "sol");
            model.study("std2").feature("time").set("notstudy", "std1");
            model.study("std2").feature("time").set("notsolnum", "auto");
            p("std2 coupling to std1 set");
        } catch (Throwable t) { p("std2 coupling FAILED: " + t.getMessage()); }
        model.study("std2").feature("time").setSolveFor("/physics/spf", false);
        model.study("std2").feature("time").setSolveFor("/physics/tds", true);
        model.study("std1").createAutoSequences("all");
        model.study("std2").createAutoSequences("all");
        p("studies built; solvers = " + Arrays.toString(model.sol().tags()));

        // probes: average concentration in the cell domain + the mid-plane point
        try {
            model.probe().create("cellAvg", "Domain");
            model.probe("cellAvg").model("comp1");
            model.probe("cellAvg").set("expr", "c");
            model.probe("cellAvg").set("type", "average");
            model.probe("cellAvg").set("unit", "mol/m^3");
            model.probe("cellAvg").set("descr", "cell layer average concentration");
            model.probe("cellAvg").selection().set(cellD);
            p("cellAvg probe OK");
        } catch (Throwable t) { p("cellAvg probe FAILED: " + t.getMessage()); }
        try {
            model.probe().create("pMid", "DomainPoint");
            model.probe("pMid").model("comp1");
            model.probe("pMid").set("coords", new double[][]{{0.0, 0.0, -0.00005}});
            model.probe("pMid").set("dimension", 3);
            model.probe("pMid").create("pe", "PointExpr");
            model.probe("pMid").feature("pe").set("expr", "c");
            model.probe("pMid").feature("pe").set("unit", "mol/m^3");
            model.probe("pMid").set("descr", "cell layer midplane");
            p("pMid probe OK");
        } catch (Throwable t) { p("pMid probe FAILED: " + t.getMessage()); }

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
        try {
            model.study("std3").create("param", "Parametric");
            model.study("std3").feature("param").set("pname", new String[]{"Q_in"});
            model.study("std3").feature("param").set("plist", "10 100 1000");
            model.study("std3").feature("param").set("punit", new String[]{"ul/min"});
            model.study("std3").feature("param").set("sweeptype", "sparse");
            p("std3 param sweep configured");
        } catch (Throwable t) { p("std3 param FAILED: " + t.getMessage()); }
        model.study("std3").create("stat", "Stationary");
        model.study("std3").feature("stat").setSolveFor("/physics/spf", true);
        model.study("std3").feature("stat").setSolveFor("/physics/tds", false);
        model.study("std3").create("stat2", "Stationary");
        try {
            model.study("std3").feature("stat2").set("notsolmethod", "sol");
            model.study("std3").feature("stat2").set("notstudy", "std3");
            model.study("std3").feature("stat2").set("notstudystep", "stat");
            model.study("std3").feature("stat2").set("notsolnum", "auto");
            p("std3 stat2 coupled to std3/stat");
        } catch (Throwable t) { p("std3 stat2 coupling FAILED: " + t.getMessage()); }
        model.study("std3").feature("stat2").setSolveFor("/physics/spf", false);
        model.study("std3").feature("stat2").setSolveFor("/physics/tds", true);
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
                    saveTable(tag + "_table_" + tabs[i] + ".csv", d);
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

        double[] avCham = evalAv(chamD, "c", dset, "avCham");
        series(tag + "_chamber_average", tl, avCham);

        for (int i = 0; i < 3; i++) {
            double[] e = evalPoint(PXYZ[i], dset, "pev" + i, "cptP" + (i + 1));
            series(tag + "_point_" + PNAMES[i], tl, e);
        }
        if (withCell) {
            double[] avCell = evalAv(cellD, "c", dset, "avCell");
            series(tag + "_cell_average", tl, avCell);
            double[] e = evalPoint(new double[]{0.0, 0.0, -0.00005}, dset, "pevMid", "cptMid");
            series(tag + "_cell_midplane", tl, e);
        }
        // volume check
        try {
            model.result().numerical().create("volCham", "AvVolume");
            model.result().numerical("volCham").selection().set(chamD);
            model.result().numerical("volCham").setIndex("expr", "1", 0);
            if (dset != null) model.result().numerical("volCham").set("data", dset);
            model.result().numerical("volCham").run();
            p("VOLUME chamber = " + model.result().numerical("volCham").getReal()[0][0] + " m^3");
            model.result().numerical().create("volAll", "AvVolume");
            model.result().numerical("volAll").selection().all();
            model.result().numerical("volAll").setIndex("expr", "1", 0);
            if (dset != null) model.result().numerical("volAll").set("data", dset);
            model.result().numerical("volAll").run();
            p("VOLUME total (fluid + cell) = " + model.result().numerical("volAll").getReal()[0][0] + " m^3");
        } catch (Throwable t) {
            p("volume check failed: " + t.getMessage());
        }
        // delivered flow rate (integral of u.n over the inlet) - sanity check
        try {
            model.result().numerical().create("intQ", "Integration");
            model.result().numerical("intQ").selection().set(inF);
            model.result().numerical("intQ").setIndex("expr", "u*nx+v*ny+w*nz", 0);
            model.result().numerical("intQ").run();
            p("Q_INTEGRAL over inlet face = "
              + model.result().numerical("intQ").getReal()[0][0] + " m^3/s");
        } catch (Throwable t) {
            p("Q integral failed: " + t.getMessage());
        }
        // pulse shape from COMSOL itself
        try {
            model.result().numerical().create("gPulse", "EvalGlobal");
            model.result().numerical("gPulse").setIndex("expr", "rect1(t)", 0);
            if (dset != null) model.result().numerical("gPulse").set("data", dset);
            model.result().numerical("gPulse").run();
            double[][] d = model.result().numerical("gPulse").getReal();
            p("PULSE rect1(t) first values: " + fmt(d[0], 12));
            p("PULSE rect1(t) last  values: " + fmt(d[0], 12));
            series(tag + "_pulse_rect1", tl, d[0]);
        } catch (Throwable t) {
            p("pulse eval failed: " + t.getMessage());
        }
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

    static double[] evalAv(int[] sel, String expr, String dset, String tag) {
        try {
            model.result().numerical().create(tag, "AvVolume");
            model.result().numerical(tag).selection().set(sel);
            model.result().numerical(tag).setIndex("expr", expr, 0);
            if (dset != null) model.result().numerical(tag).set("data", dset);
            model.result().numerical(tag).run();
            double[][] d = model.result().numerical(tag).getReal();
            p("EVAL " + tag + " shape " + d.length + "x" + (d.length > 0 ? d[0].length : 0)
              + "  first=" + fmt(d[0], 4) + "  last=" + fmt(d[d.length - 1], 4));
            return d[0];
        } catch (Throwable t) {
            p("EVAL " + tag + " failed: " + t.getMessage());
            return new double[0];
        }
    }

    static double[] evalPoint(double[] xyz, String dset, String tag, String cptTag) {
        try {
            model.result().dataset().create(cptTag, "CutPoint3D");
            model.result().dataset(cptTag).set("pointx", "" + xyz[0]);
            model.result().dataset(cptTag).set("pointy", "" + xyz[1]);
            model.result().dataset(cptTag).set("pointz", "" + xyz[2]);
            model.result().numerical().create(tag, "EvalPoint");
            model.result().numerical(tag).set("data", cptTag);
            model.result().numerical(tag).setIndex("expr", "c", 0);
            model.result().numerical(tag).run();
            double[][] d = model.result().numerical(tag).getReal();
            p("EVAL " + tag + " shape " + d.length + "x" + (d.length > 0 ? d[0].length : 0)
              + "  first=" + fmt(d[0], 4) + "  last=" + fmt(d[d.length - 1], 4));
            return d[0];
        } catch (Throwable t) {
            p("EVAL " + tag + " failed: " + t.getMessage());
            return new double[0];
        }
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
                    saveTable("ef_param_table_" + tabs[i] + ".csv", d);
                } catch (Throwable t) {
                    p("table " + tabs[i] + " read failed: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            p("param table export failed: " + t.getMessage());
        }
        // cell-domain average for each flow rate
        try {
            String dset = lastParamDataset();
            p("param dataset = " + dset);
            model.result().numerical().create("avCellP", "AvVolume");
            model.result().numerical("avCellP").selection().set(cellD);
            model.result().numerical("avCellP").setIndex("expr", "c", 0);
            if (dset != null) model.result().numerical("avCellP").set("data", dset);
            model.result().numerical("avCellP").run();
            double[][] d = model.result().numerical("avCellP").getReal();
            p("PARAM cell-domain average c: " + fmt(d[0], 10));
            saveTable("ef_param_cell_average.csv", d);
        } catch (Throwable t) {
            p("avCellP failed: " + t.getMessage());
        }
        // parametric values (flow rates) of the solutions
        try {
            String[] sols = model.sol().tags();
            p("solvers = " + Arrays.toString(sols));
            for (int i = 0; i < sols.length; i++) {
                try {
                    double[] pv = model.sol(sols[i]).getPVals();
                    p("   sol " + sols[i] + " pvals = " + fmt(pv, 10));
                    saveTable("ef_param_pvals_" + sols[i] + ".csv", new double[][]{pv});
                } catch (Throwable t) {
                    p("   sol " + sols[i] + " pvals failed: " + t.getMessage());
                }
            }
        } catch (Throwable t) {
            p("pvals failed: " + t.getMessage());
        }
    }

    static String lastParamDataset() {
        String[] tags = model.result().dataset().tags();
        String best = null;
        for (int i = 0; i < tags.length; i++) {
            try {
                String d = model.result().dataset(tags[i]).getString("data");
                p("   dataset " + tags[i] + " <- " + d);
                if (d != null && d.startsWith("sol")) best = tags[i];
            } catch (Throwable t) { /* ignore */ }
        }
        return best;
    }

    /** write a table to CSV through COMSOL's own API (bypasses the java.io sandbox) */
    static void saveTable(String name, double[][] d) {
        if (d == null || d.length == 0) { p("saveTable " + name + " skipped (empty)"); return; }
        try {
            String tag = "tblOut" + Math.abs(name.hashCode());
            model.result().table().create(tag, "Table");
            model.result().table(tag).setTableData(d, null);
            model.result().table(tag).save(OUT + "/" + name);
            p("SAVED " + OUT + "/" + name);
        } catch (Throwable t) {
            p("saveTable " + name + " failed: " + t.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers
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

    static String fmt(double[] a, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length && i < n; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.6g", a[i]));
        }
        return sb.toString();
    }

    static int[] concat(int[] a, int[] b) {
        int[] r = new int[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
