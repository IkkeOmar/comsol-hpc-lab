/* ApiProbe2.java -- settle the remaining COMSOL 6.4 API details needed by Ex2Chamber.
 * Geometry is created FIRST so the physics interfaces have a dimension.
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class ApiProbe2 {

    static void P(String s) { System.out.println(s); }

    public static void main(String[] args) {
        try {
            Model model = ModelUtil.create("ApiProbe2");
            model.component().create("comp1");

            // ---------- geometry first ------------------------------------
            GeomSequence g = model.component("comp1").geom().create("geom1", 3);
            g.lengthUnit("mm");
            g.create("blk", "Block");
            g.feature("blk").set("size", new String[]{"60", "60", "1"});
            g.feature("blk").set("pos", new String[]{"-30", "-30", "0"});
            g.create("cyl", "Cylinder");
            g.feature("cyl").set("r", "25");
            g.feature("cyl").set("h", "0.1");
            g.feature("cyl").set("pos", new String[]{"0", "0", "-0.1"});
            g.run();
            P("geom domains=" + g.getNDomains() + " edges=" + g.getNEdges()
              + " boundaries=" + g.getNBoundaries());
            g.create("selCellVert", "BoxSelection");
            g.feature("selCellVert").set("entitydim", 1);
            g.feature("selCellVert").set("condition", "inside");
            g.feature("selCellVert").set("xmin", "-25.1"); g.feature("selCellVert").set("xmax", "25.1");
            g.feature("selCellVert").set("ymin", "-25.1"); g.feature("selCellVert").set("ymax", "25.1");
            g.feature("selCellVert").set("zmin", "-0.101"); g.feature("selCellVert").set("zmax", "0.001");
            g.create("selCellDom", "BoxSelection");
            g.feature("selCellDom").set("entitydim", 3);
            g.feature("selCellDom").set("condition", "inside");
            g.feature("selCellDom").set("xmin", "-25.1"); g.feature("selCellDom").set("xmax", "25.1");
            g.feature("selCellDom").set("ymin", "-25.1"); g.feature("selCellDom").set("ymax", "25.1");
            g.feature("selCellDom").set("zmin", "-0.15"); g.feature("selCellDom").set("zmax", "0.05");
            g.run();
            int[] cv = model.selection("geom1_selCellVert").entities(1);
            P("cell vertical edges (inside box) = " + Arrays.toString(cv) + " count=" + cv.length);
            int[] cd = model.selection("geom1_selCellDom").entities(3);
            P("cell domain = " + Arrays.toString(cd));
            int[] cf = model.selection("geom1_selCellVert").entities(2);
            P("faces in cell box = " + Arrays.toString(cf));

            // ---------- 1/2/3: TDS feature properties ----------------------
            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            PhysicsFeature cdm1 = model.component("comp1").physics("tds").feature("cdm1");
            P("cdm1 class = " + cdm1.getClass().getName());
            P("cdm1.u_src allowed = " + Arrays.toString(cdm1.getAllowedPropertyValues("u_src")));
            try { P("cdm1.D_c_mat allowed = " + Arrays.toString(cdm1.getAllowedPropertyValues("D_c_mat"))); }
            catch (Throwable t) { P("cdm1.D_c_mat allowed FAILED :: " + t.getMessage()); }
            try { P("cdm1.DCS allowed = "
                + Arrays.toString(cdm1.getAllowedPropertyValues("DiffusionCoefficientSource"))); }
            catch (Throwable t) { P("cdm1.DCS allowed FAILED :: " + t.getMessage()); }
            try { P("cdm1.D_c_mat get = " + cdm1.getString("D_c_mat")); }
            catch (Throwable t) { P("cdm1.D_c_mat get FAILED :: " + t.getMessage()); }
            try { P("cdm1.DCS get = " + cdm1.getString("DiffusionCoefficientSource")); }
            catch (Throwable t) { P("cdm1.DCS get FAILED :: " + t.getMessage()); }
            try { P("cdm1.D_c get = " + cdm1.getString("D_c")); }
            catch (Throwable t) { P("cdm1.D_c get FAILED :: " + t.getMessage()); }

            model.component("comp1").physics("tds").create("fluid2", "Fluid", 3);
            PhysicsFeature fluid2 = model.component("comp1").physics("tds").feature("fluid2");
            P("fluid2.u_src allowed = " + Arrays.toString(fluid2.getAllowedPropertyValues("u_src")));
            try { P("fluid2.D_c_mat allowed = " + Arrays.toString(fluid2.getAllowedPropertyValues("D_c_mat"))); }
            catch (Throwable t) { P("fluid2.D_c_mat allowed FAILED :: " + t.getMessage()); }
            try { P("fluid2.DCS allowed = "
                + Arrays.toString(fluid2.getAllowedPropertyValues("DiffusionCoefficientSource"))); }
            catch (Throwable t) { P("fluid2.DCS allowed FAILED :: " + t.getMessage()); }

            model.component("comp1").physics("tds").create("reac1", "Reactions", 3);
            PhysicsFeature reac1 = model.component("comp1").physics("tds").feature("reac1");
            try { P("reac1.R_c_src allowed = " + Arrays.toString(reac1.getAllowedPropertyValues("R_c_src"))); }
            catch (Throwable t) { P("reac1.R_c_src allowed FAILED :: " + t.getMessage()); }
            try { P("reac1.R_c_src get = " + reac1.getString("R_c_src")); }
            catch (Throwable t) { P("reac1.R_c_src get FAILED :: " + t.getMessage()); }
            try { P("reac1.R_c get = " + reac1.getString("R_c")); }
            catch (Throwable t) { P("reac1.R_c get FAILED :: " + t.getMessage()); }

            // ---------- material diffusion property ------------------------
            model.component("comp1").material().create("mat1", "Common");
            try {
                String[] pg = model.component("comp1").material("mat1").propertyGroup().tags();
                P("material property groups = " + Arrays.toString(pg));
                String[] props = model.component("comp1").material("mat1").propertyGroup("def").properties();
                P("mat1 def properties = " + Arrays.toString(props));
            } catch (Throwable t) { P("material props FAILED :: " + t.getMessage()); }

            // ---------- 4: numerical features ------------------------------
            model.result().numerical().create("pev", "EvalPoint");
            P("EvalPoint props = " + Arrays.toString(model.result().numerical("pev").properties()));
            model.result().numerical().create("avv", "AvVolume");
            P("AvVolume props = " + Arrays.toString(model.result().numerical("avv").properties()));
            model.result().numerical().create("gev", "EvalGlobal");
            P("EvalGlobal props = " + Arrays.toString(model.result().numerical("gev").properties()));
            model.result().dataset().create("cpt1", "CutPoint3D");
            P("CutPoint3D props = " + Arrays.toString(model.result().dataset("cpt1").properties()));

            // ---------- 5: domain probe selection --------------------------
            model.probe().create("dom1", "Domain");
            ProbeFeature pr = model.probe("dom1");
            P("Domain probe props = " + Arrays.toString(pr.properties()));
            try {
                pr.selection().set(cd);
                P("Domain probe selection().set OK");
            } catch (Throwable t) { P("Domain probe selection().set FAILED :: " + t.getMessage()); }
            model.probe().create("gv1", "GlobalVariable");
            P("GlobalVariable props = " + Arrays.toString(model.probe("gv1").properties()));
            try { model.probe("gv1").set("expr", "rect1(t)"); P("GlobalVariable expr OK"); }
            catch (Throwable t) { P("GlobalVariable expr FAILED :: " + t.getMessage()); }

            // ---------- 6: mesh the cell cylinder by sweeping ---------------
            try {
                MeshSequence m = model.component("comp1").mesh().create("mesh1", "geom1");
                m.create("size1", "Size");
                m.feature("size1").set("hmax", "2.0");
                m.create("ftri1", "FreeTri");
                m.feature("ftri1").selection().geom(2).set(cf);
                m.create("swe1", "Sweep");
                m.feature("swe1").selection().geom(3).set(cd);
                m.create("dis1", "Distribution");
                m.feature("dis1").selection().geom("geom1", 1).set(cv);
                m.feature("dis1").set("numelem", "5");
                m.run();
                P("SWEEP numElem=" + m.stat().getNumElem()
                  + " types=" + Arrays.toString(m.stat().getTypes()));
            } catch (Throwable t) {
                P("sweep test FAILED :: " + t.getMessage());
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        System.out.println("APIPROBE2 DONE");
    }
}
