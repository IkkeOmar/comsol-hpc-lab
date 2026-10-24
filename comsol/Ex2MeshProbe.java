import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

/** Ex2MeshProbe — test the risky parts of the Exercise-2 model on the REAL geometry:
 *   1. geometry + domain/face selections (chamber, 2 inlet ducts, cell cylinder)
 *   2. mesh recipes (FreeTri + Swept, with and without the cell layer)
 *   3. the Swept mesher's own property names
 *   4. Inlet MassFlow property semantics
 *   5. TDS Reactions / Fluid domain-condition property semantics
 *   6. probe API: Domain probe selection, DomainPoint + PointExpr, probe tables
 */
public class Ex2MeshProbe {

    static void h(String s) {
        System.out.println();
        System.out.println("################ " + s + " ################");
    }

    static void meshStats(MeshSequence m, String label) {
        try {
            System.out.println("  [" + label + "] elements=" + m.stat().getNumElem()
                + " types=" + Arrays.toString(m.stat().getTypes())
                + " minQ=" + m.stat().getMinQuality()
                + " meanQ=" + m.stat().getMeanQuality());
        } catch (Throwable e) {
            System.out.println("  [" + label + "] STATS FAILED: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            geoAndMesh(false);
            geoAndMesh(true);
            physicsBits();
            probeBits();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println();
        System.out.println("EX2MESHPROBE DONE");
    }

    // ------------------------------------------------------------------ geometry + mesh
    static void geoAndMesh(boolean withCell) {
        h("GEOMETRY + MESH, cellLayer=" + withCell);
        Model model = ModelUtil.create("Ex2MeshProbe" + (withCell ? "C" : "N"));
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        // chamber 60 x 60 x 1 mm, floor at z = 0
        g.create("blkCh", "Block");
        g.feature("blkCh").set("size", new String[]{"60", "60", "1"});
        g.feature("blkCh").set("pos", new String[]{"-30", "-30", "0"});
        // west duct 45 x 2 x 1 mm, x in [-75,-30]
        g.create("blkDw", "Block");
        g.feature("blkDw").set("size", new String[]{"45", "2", "1"});
        g.feature("blkDw").set("pos", new String[]{"-75", "-1", "0"});
        // east duct 45 x 2 x 1 mm, x in [30,75]
        g.create("blkDe", "Block");
        g.feature("blkDe").set("size", new String[]{"45", "2", "1"});
        g.feature("blkDe").set("pos", new String[]{"30", "-1", "0"});
        if (withCell) {
            // cell layer: cylinder r = 25 mm, h = 0.1 mm, top face at z = 0
            g.create("cylCell", "Cylinder");
            g.feature("cylCell").set("r", "25");
            g.feature("cylCell").set("h", "0.1");
            g.feature("cylCell").set("pos", new String[]{"0", "0", "-0.1"});
        }
        g.run();
        System.out.println("domains=" + g.getNDomains() + " boundaries=" + g.getNBoundaries()
            + " edges=" + g.getNEdges() + " vertices=" + g.getNVertices());

        // ---- selections -------------------------------------------------
        g.create("selIn", "BoxSelection");
        g.feature("selIn").set("entitydim", 2);
        g.feature("selIn").set("condition", "allvertices");
        g.feature("selIn").set("xmin", "-75.1"); g.feature("selIn").set("xmax", "-74.9");
        g.feature("selIn").set("ymin", "-1.1");  g.feature("selIn").set("ymax", "1.1");
        g.feature("selIn").set("zmin", "-0.1");  g.feature("selIn").set("zmax", "1.1");

        g.create("selOut", "BoxSelection");
        g.feature("selOut").set("entitydim", 2);
        g.feature("selOut").set("condition", "allvertices");
        g.feature("selOut").set("xmin", "74.9"); g.feature("selOut").set("xmax", "75.1");
        g.feature("selOut").set("ymin", "-1.1");  g.feature("selOut").set("ymax", "1.1");
        g.feature("selOut").set("zmin", "-0.1");  g.feature("selOut").set("zmax", "1.1");

        g.create("selCham", "BoxSelection");
        g.feature("selCham").set("entitydim", 3);
        g.feature("selCham").set("condition", "inside");
        g.feature("selCham").set("xmin", "-30.1"); g.feature("selCham").set("xmax", "30.1");
        g.feature("selCham").set("ymin", "-30.1"); g.feature("selCham").set("ymax", "30.1");
        g.feature("selCham").set("zmin", "-0.1");  g.feature("selCham").set("zmax", "1.1");

        g.create("selDuct", "BoxSelection");
        g.feature("selDuct").set("entitydim", 3);
        g.feature("selDuct").set("condition", "inside");
        g.feature("selDuct").set("xmin", "-75.1"); g.feature("selDuct").set("xmax", "75.1");
        g.feature("selDuct").set("ymin", "-1.1");  g.feature("selDuct").set("ymax", "1.1");
        g.feature("selDuct").set("zmin", "-0.1");  g.feature("selDuct").set("zmax", "1.1");

        g.create("selBot", "BoxSelection");
        g.feature("selBot").set("entitydim", 2);
        g.feature("selBot").set("condition", "allvertices");
        g.feature("selBot").set("xmin", "-75.1"); g.feature("selBot").set("xmax", "75.1");
        g.feature("selBot").set("ymin", "-30.1"); g.feature("selBot").set("ymax", "30.1");
        g.feature("selBot").set("zmin", "-0.05"); g.feature("selBot").set("zmax", "0.05");

        g.create("selTop", "BoxSelection");
        g.feature("selTop").set("entitydim", 2);
        g.feature("selTop").set("condition", "allvertices");
        g.feature("selTop").set("xmin", "-75.1"); g.feature("selTop").set("xmax", "75.1");
        g.feature("selTop").set("ymin", "-30.1"); g.feature("selTop").set("ymax", "30.1");
        g.feature("selTop").set("zmin", "0.95"); g.feature("selTop").set("zmax", "1.05");

        g.create("selCellTop", "BoxSelection");
        g.feature("selCellTop").set("entitydim", 2);
        g.feature("selCellTop").set("condition", "allvertices");
        g.feature("selCellTop").set("xmin", "-25.1"); g.feature("selCellTop").set("xmax", "25.1");
        g.feature("selCellTop").set("ymin", "-25.1"); g.feature("selCellTop").set("ymax", "25.1");
        g.feature("selCellTop").set("zmin", "-0.05"); g.feature("selCellTop").set("zmax", "0.05");

        g.create("selCellBot", "BoxSelection");
        g.feature("selCellBot").set("entitydim", 2);
        g.feature("selCellBot").set("condition", "allvertices");
        g.feature("selCellBot").set("xmin", "-25.1"); g.feature("selCellBot").set("xmax", "25.1");
        g.feature("selCellBot").set("ymin", "-25.1"); g.feature("selCellBot").set("ymax", "25.1");
        g.feature("selCellBot").set("zmin", "-0.15"); g.feature("selCellBot").set("zmax", "-0.05");

        g.run();
        System.out.println("inlet faces   = " + Arrays.toString(model.selection("geom1_selIn").entities(2)));
        System.out.println("outlet faces  = " + Arrays.toString(model.selection("geom1_selOut").entities(2)));
        System.out.println("chamber doms  = " + Arrays.toString(model.selection("geom1_selCham").entities(3)));
        System.out.println("duct doms     = " + Arrays.toString(model.selection("geom1_selDuct").entities(3)));
        System.out.println("bottom faces  = " + Arrays.toString(model.selection("geom1_selBot").entities(2)));
        System.out.println("top faces     = " + Arrays.toString(model.selection("geom1_selTop").entities(2)));
        System.out.println("cellTop faces = " + Arrays.toString(model.selection("geom1_selCellTop").entities(2)));
        System.out.println("cellBot faces = " + Arrays.toString(model.selection("geom1_selCellBot").entities(2)));

        // ---- mesh recipe A: FreeTri on bottom faces + Swept --------------
        try {
            MeshSequence m = model.component("comp1").mesh().create("meshA", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "3");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.35");
            m.create("ftri1", "FreeTri");
            m.feature("ftri1").selection().geom("geom1").set(model.selection("geom1_selBot").entities(2));
            m.create("swe1", "Swept");
            m.run();
            meshStats(m, "A: FreeTri(bottom)+Swept");
            System.out.println("     Swept properties: " + Arrays.toString(m.feature("swe1").properties()));
        } catch (Throwable e) {
            System.out.println("  RECIPE A FAILED: " + e.getMessage());
        }

        // ---- mesh recipe B: FreeTri + Swept with Distribution ------------
        try {
            MeshSequence m = model.component("comp1").mesh().create("meshB", "geom1");
            m.create("size1", "Size");
            m.feature("size1").set("hmax", "3");
            m.feature("size1").set("hmin", "0.05");
            m.feature("size1").set("hgrad", "1.35");
            m.create("ftri1", "FreeTri");
            m.feature("ftri1").selection().geom("geom1").set(model.selection("geom1_selBot").entities(2));
            m.create("swe1", "Swept");
            m.feature("swe1").create("dis1", "Distribution");
            m.feature("swe1").feature("dis1").set("numelem", "10");
            m.run();
            meshStats(m, "B: FreeTri(bottom)+Swept+Distribution(10)");
        } catch (Throwable e) {
            System.out.println("  RECIPE B FAILED: " + e.getMessage());
        }

        if (withCell) {
            // ---- recipe C: cell layer swept separately -------------------
            try {
                MeshSequence m = model.component("comp1").mesh().create("meshC", "geom1");
                m.create("size1", "Size");
                m.feature("size1").set("hmax", "3");
                m.feature("size1").set("hmin", "0.02");
                m.feature("size1").set("hgrad", "1.35");
                // surface mesh on the whole z=0 level (annulus + cell disc)
                m.create("ftri1", "FreeTri");
                m.feature("ftri1").selection().geom("geom1").set(model.selection("geom1_selBot").entities(2));
                m.create("swe1", "Swept");
                m.feature("swe1").create("dis1", "Distribution");
                m.feature("swe1").feature("dis1").set("numelem", "10");
                m.run();
                meshStats(m, "C: full fluid sweep");
            } catch (Throwable e) {
                System.out.println("  RECIPE C FAILED: " + e.getMessage());
            }
            // ---- recipe D: explicit source/destination sweeps -----------
            try {
                MeshSequence m = model.component("comp1").mesh().create("meshD", "geom1");
                m.create("size1", "Size");
                m.feature("size1").set("hmax", "3");
                m.feature("size1").set("hmin", "0.02");
                m.feature("size1").set("hgrad", "1.35");
                m.create("ftri1", "FreeTri");
                m.feature("ftri1").selection().geom("geom1").set(model.selection("geom1_selBot").entities(2));
                // fluid sweep: source = bottom, destination = top
                m.create("swe1", "Swept");
                m.feature("swe1").set("sourceface", "userdef");
                m.feature("swe1").selection("sourceface").set(model.selection("geom1_selBot").entities(2));
                m.feature("swe1").set("destface", "userdef");
                m.feature("swe1").selection("destface").set(model.selection("geom1_selTop").entities(2));
                m.feature("swe1").create("dis1", "Distribution");
                m.feature("swe1").feature("dis1").set("numelem", "10");
                // cell sweep: source = cell top disc, destination = cell bottom disc
                m.create("swe2", "Swept");
                m.feature("swe2").selection().geom("geom1").set(new int[]{4});
                m.feature("swe2").set("sourceface", "userdef");
                m.feature("swe2").selection("sourceface").set(model.selection("geom1_selCellTop").entities(2));
                m.feature("swe2").set("destface", "userdef");
                m.feature("swe2").selection("destface").set(model.selection("geom1_selCellBot").entities(2));
                m.feature("swe2").create("dis1", "Distribution");
                m.feature("swe2").feature("dis1").set("numelem", "10");
                m.run();
                meshStats(m, "D: explicit source/dest, 2 sweeps");
            } catch (Throwable e) {
                System.out.println("  RECIPE D FAILED: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ physics details
    static void physicsBits() {
        h("PHYSICS PROPERTY SEMANTICS");
        Model model = ModelUtil.create("Ex2MeshProbeP");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blkCh", "Block");
        g.feature("blkCh").set("size", new String[]{"60", "60", "1"});
        g.feature("blkCh").set("pos", new String[]{"-30", "-30", "0"});
        g.run();

        model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
        model.component("comp1").physics("spf").create("inl1", "Inlet", 2);
        model.component("comp1").physics("spf").feature("inl1").set("BoundaryCondition", "MassFlow");
        for (String pr : new String[]{"MassFlowType", "StandardFlowRateDefinedBy"}) {
            try {
                System.out.println("  Inlet." + pr + " allowed = "
                    + Arrays.toString(model.component("comp1").physics("spf").feature("inl1").getAllowedPropertyValues(pr)));
            } catch (Throwable e) { System.out.println("  Inlet." + pr + " enum probe failed: " + e.getMessage()); }
        }
        try {
            model.component("comp1").physics("spf").feature("inl1").set("MassFlowType", "MassFlowRate");
            model.component("comp1").physics("spf").feature("inl1").set("mfr", "1.6667e-6[kg/s]");
            System.out.println("  MassFlowRate/mfr set OK");
        } catch (Throwable e) { System.out.println("  MassFlowRate set FAILED: " + e.getMessage()); }

        model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
        for (String pr : new String[]{"DiffusionCoefficientSource"}) {
            try {
                System.out.println("  cdm1." + pr + " allowed = "
                    + Arrays.toString(model.component("comp1").physics("tds").feature("cdm1").getAllowedPropertyValues(pr)));
            } catch (Throwable e) { System.out.println("  cdm1." + pr + " enum probe failed: " + e.getMessage()); }
        }
        try {
            model.component("comp1").physics("tds").feature("cdm1").set("u_src", "root.comp1.u");
            model.component("comp1").physics("tds").feature("cdm1").set("D_c", "200[um^2/s]");
            System.out.println("  cdm1 u_src=root.comp1.u, D_c set OK");
        } catch (Throwable e) { System.out.println("  cdm1 set FAILED: " + e.getMessage()); }

        model.component("comp1").physics("tds").create("fluid2", "Fluid", 3);
        try {
            model.component("comp1").physics("tds").feature("fluid2").set("u_src", "userdef");
            model.component("comp1").physics("tds").feature("fluid2").set("u", new String[]{"0", "0", "0"});
            model.component("comp1").physics("tds").feature("fluid2").set("DiffusionCoefficientSource", "userdef");
            model.component("comp1").physics("tds").feature("fluid2").set("D_c", "0.7*200[um^2/s]");
            System.out.println("  fluid2 (zero velocity, 0.7D) set OK");
        } catch (Throwable e) { System.out.println("  fluid2 set FAILED: " + e.getMessage()); }

        model.component("comp1").physics("tds").create("reac1", "Reactions", 3);
        try {
            System.out.println("  reac1.R_c_src allowed = "
                + Arrays.toString(model.component("comp1").physics("tds").feature("reac1").getAllowedPropertyValues("R_c_src")));
        } catch (Throwable e) { System.out.println("  reac1.R_c_src enum probe failed: " + e.getMessage()); }
        try {
            model.component("comp1").physics("tds").feature("reac1").set("R_c", "-0.15[mol/m^3/s]*c");
            System.out.println("  reac1 R_c set OK");
        } catch (Throwable e) { System.out.println("  reac1 R_c set FAILED: " + e.getMessage()); }

        // numeric features available for a domain average in 3D
        for (String ty : new String[]{"AvVolume", "IntVolume", "AvSurface", "IntSurface", "EvalGlobal", "EvalPoint", "MaxVolume", "MinVolume"}) {
            try {
                model.result().numerical().create("t_" + ty, ty);
                System.out.println("  numerical type '" + ty + "' EXISTS");
            } catch (Throwable e) {
                System.out.println("  numerical type '" + ty + "' MISSING (" + e.getMessage() + ")");
            }
        }
    }

    // ------------------------------------------------------------------ probe API
    static void probeBits() {
        h("PROBE API");
        Model model = ModelUtil.create("Ex2MeshProbeQ");
        model.component().create("comp1");
        GeomSequence g = model.component("comp1").geom().create("geom1", 3);
        g.lengthUnit("mm");
        g.create("blkCh", "Block");
        g.feature("blkCh").set("size", new String[]{"60", "60", "1"});
        g.feature("blkCh").set("pos", new String[]{"-30", "-30", "0"});
        g.run();

        model.probe().create("domP", "Domain");
        model.probe("domP").set("expr", "c");
        model.probe("domP").set("type", "average");
        try {
            model.probe("domP").selection().set(new int[]{1});
            System.out.println("  Domain probe selection().set OK");
            System.out.println("  selection entities = "
                + Arrays.toString(model.probe("domP").selection().entities()));
        } catch (Throwable e) { System.out.println("  Domain probe selection FAILED: " + e.getMessage()); }

        model.probe().create("dpP", "DomainPoint");
        model.probe("dpP").set("coords3", new double[][]{{-29.5, 0.0, 0.05}});
        try {
            model.probe("dpP").create("pe1", "PointExpr");
            System.out.println("  PointExpr properties = " + Arrays.toString(model.probe("dpP").feature("pe1").properties()));
            model.probe("dpP").feature("pe1").set("expr", "c");
            System.out.println("  PointExpr expr=c OK");
        } catch (Throwable e) { System.out.println("  PointExpr FAILED: " + e.getMessage()); }
        try {
            model.probe("dpP").selection().set(new int[]{1});
            System.out.println("  DomainPoint selection OK");
        } catch (Throwable e) { System.out.println("  DomainPoint selection FAILED: " + e.getMessage()); }

        // plain Point probe with a geometric point selection
        System.out.println("  result().table().tags() = "
            + Arrays.toString(model.result().table().tags()));
    }
}
