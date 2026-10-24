/* Ex2ApiProbe.java -- ground-truth probe of the COMSOL 6.4 API calls that
 * Ex2Chamber needs.  Runs a TINY model (one block) so it is cheap; prints the
 * property lists and the allowed values of every call that the previous
 * attempts guessed at.
 *
 *   - TDS "Fluid 1" (cdm1): how to attach the spf velocity field
 *   - CutPoint3D dataset + EvalPoint numerical (the point probes)
 *   - model.probe() types (Domain / DomainPoint / Global)
 *   - Solution dataset -> solution tag mapping
 *   - Parametric study property names
 */
import com.comsol.model.*;
import com.comsol.model.util.*;
import com.comsol.model.physics.*;
import java.util.Arrays;

public class Ex2ApiProbe {

    static Model model;

    static void p(String s) { System.out.println(s); }

    static void dumpProps(String label, Object node) {
        try {
            if (node instanceof PhysicsFeature) {
                p(label + " properties = " + Arrays.toString(((PhysicsFeature) node).properties()));
            } else if (node instanceof com.comsol.model.PropFeature) {
                p(label + " properties = " + Arrays.toString(((com.comsol.model.PropFeature) node).properties()));
            } else if (node instanceof com.comsol.model.ResultFeature) {
                p(label + " properties = " + Arrays.toString(((com.comsol.model.ResultFeature) node).properties()));
            } else if (node instanceof com.comsol.model.DatasetFeature) {
                p(label + " properties = " + Arrays.toString(((com.comsol.model.DatasetFeature) node).properties()));
            }
        } catch (Throwable t) {
            p(label + " property dump failed: " + t);
        }
    }

    public static void main(String[] args) {
        try {
            model = ModelUtil.create("ApiProbe");
            model.component().create("comp1");
            model.param().set("D_sp", "200[um^2/s]");
            model.param().set("Q_in", "100[ul/min]");
            model.param().set("rho", "1000[kg/m^3]");

            // ---- tiny geometry --------------------------------------------
            GeomSequence g = model.component("comp1").geom().create("geom1", 3);
            g.lengthUnit("mm");
            g.create("b1", "Block");
            g.feature("b1").set("size", new String[]{"10", "4", "1"});
            g.feature("b1").set("pos", new String[]{"-5", "-2", "0"});
            g.run();
            p("GEOM domains=" + g.getNDomains() + " boundaries=" + g.getNBoundaries());

            // ---- material --------------------------------------------------
            model.component("comp1").material().create("mat1", "Common");
            model.component("comp1").material("mat1").propertyGroup("def")
                 .set("dynamicviscosity", "1e-3[Pa*s]").set("density", "1000[kg/m^3]");
            try { model.component("comp1").material("mat1").selection().all(); }
            catch (Throwable t) { p("material all() failed: " + t.getMessage()); }

            // ---- mesh ------------------------------------------------------
            model.component("comp1").mesh().create("mesh1", "geom1");
            model.component("comp1").mesh("mesh1").create("ftet1", "FreeTet");
            model.component("comp1").mesh("mesh1").run();
            p("MESH numElem=" + model.component("comp1").mesh("mesh1").stat().getNumElem());

            // ---- spf --------------------------------------------------------
            model.component("comp1").physics().create("spf", "LaminarFlow", "geom1");
            int[] all = new int[]{1};
            p("SEL dom = " + Arrays.toString(all));
            model.component("comp1").physics("spf").create("inl1", "Inlet", 2);
            model.component("comp1").physics("spf").feature("inl1").selection().set(1);
            try {
                model.component("comp1").physics("spf").feature("inl1").set("BoundaryCondition", "MassFlow");
                model.component("comp1").physics("spf").feature("inl1").set("MassFlowType", "MassFlowRate");
                model.component("comp1").physics("spf").feature("inl1").set("mfr", "rho*Q_in");
                p("SPF inlet OK mfr=" + model.component("comp1").physics("spf").feature("inl1").getString("mfr"));
            } catch (Throwable t) { p("SPF inlet FAILED: " + t.getMessage()); }
            model.component("comp1").physics("spf").create("out1", "Outlet", 2);
            model.component("comp1").physics("spf").feature("out1").selection().set(2);
            try { model.component("comp1").physics("spf").feature("out1").set("p0", "0"); }
            catch (Throwable t) { p("SPF outlet FAILED: " + t.getMessage()); }

            // ---- tds --------------------------------------------------------
            model.component("comp1").physics().create("tds", "DilutedSpecies", "geom1");
            PhysicsFeature cdm1 = model.component("comp1").physics("tds").feature("cdm1");
            p("=========== TDS cdm1 ===========");
            dumpProps("cdm1", cdm1);
            try { p("cdm1 u_src allowed = " + Arrays.toString(cdm1.getAllowedPropertyValues("u_src"))); }
            catch (Throwable t) { p("cdm1 u_src allowed FAILED: " + t.getMessage()); }
            try { p("cdm1 u allowed = " + Arrays.toString(cdm1.getAllowedPropertyValues("u"))); }
            catch (Throwable t) { p("cdm1 u allowed FAILED: " + t.getMessage()); }
            try { p("cdm1 D_c_mat allowed = " + Arrays.toString(cdm1.getAllowedPropertyValues("D_c_mat"))); }
            catch (Throwable t) { p("cdm1 D_c_mat allowed FAILED: " + t.getMessage()); }

            // try the three candidate couplings
            try { cdm1.set("u_src", "userdef");
                  p("SET u_src=userdef OK -> getString=" + cdm1.getString("u_src")); }
            catch (Throwable t) { p("SET u_src=userdef FAILED: " + t.getMessage()); }
            try { cdm1.set("u", new String[]{"root.comp1.u", "root.comp1.v", "root.comp1.w"});
                  p("SET u=3-vector OK -> getStringArray=" + Arrays.toString(cdm1.getStringArray("u"))); }
            catch (Throwable t) { p("SET u 3-vector FAILED: " + t.getMessage()); }
            try { cdm1.set("D_c_mat", "userdef");
                  p("SET D_c_mat=userdef OK -> " + cdm1.getString("D_c_mat")); }
            catch (Throwable t) { p("SET D_c_mat FAILED: " + t.getMessage()); }
            try { cdm1.set("D_c", "D_sp");
                  p("SET D_c=D_sp OK -> " + cdm1.getString("D_c")); }
            catch (Throwable t) { p("SET D_c FAILED: " + t.getMessage()); }

            // ---- probes ------------------------------------------------------
            p("=========== PROBES ===========");
            try {
                model.probe().create("domAvg", "Domain");
                p("probe Domain created");
                model.probe("domAvg").model("comp1");
                model.probe("domAvg").set("expr", "c");
                model.probe("domAvg").set("type", "average");
                p("probe Domain expr/type OK");
                try { model.probe("domAvg").set("descr", "chamber avg"); p("probe Domain descr OK"); }
                catch (Throwable t) { p("probe Domain descr FAILED: " + t.getMessage()); }
                try { model.probe("domAvg").set("unit", "mol/m^3"); p("probe Domain unit OK"); }
                catch (Throwable t) { p("probe Domain unit FAILED: " + t.getMessage()); }
            } catch (Throwable t) { p("probe Domain FAILED: " + t.getMessage()); }
            try {
                model.probe().create("pP1", "DomainPoint");
                p("probe DomainPoint created");
                model.probe("pP1").model("comp1");
                try { model.probe("pP1").set("coords", new double[][]{{0.001, 0.0, 0.0005}}); p("DomainPoint coords OK"); }
                catch (Throwable t) { p("DomainPoint coords FAILED: " + t.getMessage()); }
                try { model.probe("pP1").set("dimension", 3); p("DomainPoint dimension OK"); }
                catch (Throwable t) { p("DomainPoint dimension FAILED: " + t.getMessage()); }
                try { model.probe("pP1").create("pe", "PointExpr"); p("DomainPoint PointExpr created"); }
                catch (Throwable t) { p("DomainPoint PointExpr FAILED: " + t.getMessage()); }
                try { model.probe("pP1").feature("pe").set("expr", "c"); p("PointExpr expr OK"); }
                catch (Throwable t) { p("PointExpr expr FAILED: " + t.getMessage()); }
                try { model.probe("pP1").set("descr", "P1"); p("DomainPoint descr OK"); }
                catch (Throwable t) { p("DomainPoint descr FAILED: " + t.getMessage()); }
            } catch (Throwable t) { p("probe DomainPoint FAILED: " + t.getMessage()); }
            try {
                model.probe().create("gP1", "Global");
                p("probe Global created");
                model.probe("gP1").model("comp1");
                try { model.probe("gP1").create("pe", "PointExpr"); p("Global PointExpr created"); }
                catch (Throwable t) { p("Global PointExpr FAILED: " + t.getMessage()); }
            } catch (Throwable t) { p("probe Global FAILED: " + t.getMessage()); }
            p("probe tags = " + Arrays.toString(model.probe().tags()));

            // ---- studies ------------------------------------------------------
            p("=========== STUDY ===========");
            model.study().create("std1");
            model.study("std1").create("stat", "Stationary");
            model.study("std1").feature("stat").setSolveFor("/physics/spf", true);
            model.study("std1").feature("stat").setSolveFor("/physics/tds", false);
            model.study("std1").createAutoSequences("all");
            model.study("std1").run();
            p("std1 done; solvers=" + Arrays.toString(model.sol().tags()));
            p("datasets=" + Arrays.toString(model.result().dataset().tags()));
            for (String t : model.result().dataset().tags()) {
                try { p("  dset " + t + " type=" + model.result().dataset(t).getType()); }
                catch (Throwable e) { p("  dset " + t + " type failed: " + e.getMessage()); }
                try { p("  dset " + t + " solution=" + model.result().dataset(t).getString("solution")); }
                catch (Throwable e) { p("  dset " + t + " solution prop failed: " + e.getMessage()); }
            }

            // ---- CutPoint3D + EvalPoint ----------------------------------------
            p("=========== CUTPOINT / EVALPOINT ===========");
            try {
                model.result().dataset().create("cpt1", "CutPoint3D");
                p("CutPoint3D created");
                dumpProps("cpt1", model.result().dataset("cpt1"));
                try { model.result().dataset("cpt1").set("pointx", "0.001"); p("cpt1 pointx OK"); }
                catch (Throwable t) { p("cpt1 pointx FAILED: " + t.getMessage()); }
                try { model.result().dataset("cpt1").set("pointy", "0"); p("cpt1 pointy OK"); }
                catch (Throwable t) { p("cpt1 pointy FAILED: " + t.getMessage()); }
                try { model.result().dataset("cpt1").set("pointz", "0.0005"); p("cpt1 pointz OK"); }
                catch (Throwable t) { p("cpt1 pointz FAILED: " + t.getMessage()); }
                try { model.result().dataset("cpt1").set("data", "dset1"); p("cpt1 data=dset1 OK"); }
                catch (Throwable t) { p("cpt1 data FAILED: " + t.getMessage()); }
            } catch (Throwable t) { p("CutPoint3D FAILED: " + t.getMessage()); }
            try {
                model.result().numerical().create("pev1", "EvalPoint");
                p("EvalPoint created");
                try { model.result().numerical("pev1").setIndex("expr", "c", 0); p("EvalPoint expr OK"); }
                catch (Throwable t) { p("EvalPoint expr FAILED: " + t.getMessage()); }
                try { model.result().numerical("pev1").set("data", "cpt1"); p("EvalPoint data=cpt1 OK"); }
                catch (Throwable t) { p("EvalPoint data FAILED: " + t.getMessage()); }
                try { model.result().numerical("pev1").set("descr", "point c"); p("EvalPoint descr OK"); }
                catch (Throwable t) { p("EvalPoint descr FAILED: " + t.getMessage()); }
                model.result().numerical("pev1").run();
                double[][] d = model.result().numerical("pev1").getReal();
                p("EvalPoint getReal rows=" + d.length + " cols=" + (d.length > 0 ? d[0].length : 0));
                if (d.length > 0) p("EvalPoint row0 = " + Arrays.toString(d[0]));
            } catch (Throwable t) { p("EvalPoint FAILED: " + t.getMessage()); }
            // what happened with the plain "coords" property on EvalPoint?
            try {
                model.result().numerical().create("pev2", "EvalPoint");
                model.result().numerical("pev2").set("coords", new double[][]{{0.001, 0.0, 0.0005}});
                p("EvalPoint coords OK (has coords property)");
            } catch (Throwable t) { p("EvalPoint coords FAILED: " + t.getMessage()); }

            // ---- parametric study property names --------------------------------
            p("=========== PARAMETRIC ===========");
            try {
                model.study().create("std3");
                model.study("std3").create("param", "Parametric");
                dumpProps("param", model.study("std3").feature("param"));
                try { model.study("std3").feature("param").set("pname", new String[]{"Q_in"}); p("pname OK"); }
                catch (Throwable t) { p("pname FAILED: " + t.getMessage()); }
                try { model.study("std3").feature("param").set("plistarr", new String[]{"10 100 1000"}); p("plistarr OK"); }
                catch (Throwable t) { p("plistarr FAILED: " + t.getMessage()); }
                try { model.study("std3").feature("param").set("punit", new String[]{"ul/min"}); p("punit OK"); }
                catch (Throwable t) { p("punit FAILED: " + t.getMessage()); }
                try { model.study("std3").feature("param").set("plist", "10 100 1000"); p("plist OK"); }
                catch (Throwable t) { p("plist FAILED: " + t.getMessage()); }
                try { model.study("std3").feature("param").set("sweeptype", "sparse"); p("sweeptype OK"); }
                catch (Throwable t) { p("sweeptype FAILED: " + t.getMessage()); }
            } catch (Throwable t) { p("parametric FAILED: " + t.getMessage()); }

            // ---- numeric eval on a domain (AvVolume) with a data set ------------
            p("=========== AVVOLUME ===========");
            try {
                model.result().numerical().create("av1", "AvVolume");
                model.result().numerical("av1").selection().set(all);
                model.result().numerical("av1").setIndex("expr", "c", 0);
                model.result().numerical("av1").set("data", "dset1");
                model.result().numerical("av1").run();
                double[][] d = model.result().numerical("av1").getReal();
                p("AvVolume rows=" + d.length + " cols=" + (d.length > 0 ? d[0].length : 0));
            } catch (Throwable t) { p("AvVolume FAILED: " + t.getMessage()); }
            // solver pvals API
            try {
                String[] sols = model.sol().tags();
                for (String s : sols) {
                    try { p("sol " + s + " pvals=" + Arrays.toString(model.sol(s).getPVals())); }
                    catch (Throwable t) { p("sol " + s + " pvals failed: " + t.getMessage()); }
                }
            } catch (Throwable t) { p("pvals outer FAILED: " + t.getMessage()); }

            p("APIPROBE DONE");
        } catch (Throwable e) {
            e.printStackTrace();
            p("APIPROBE FAILED: " + e);
        }
        p("APIPROBE EXIT");
    }
}
