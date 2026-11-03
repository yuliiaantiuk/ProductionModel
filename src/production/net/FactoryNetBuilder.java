package production.net;

import production.config.ModelConfig;
import ua.stetsenkoinna.petriobj.ArcIn;
import ua.stetsenkoinna.petriobj.ArcOut;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class FactoryNetBuilder {

    private final ArrayList<PetriP> places = new ArrayList<>();
    private final ArrayList<PetriT> transitions = new ArrayList<>();
    private final ArrayList<ArcIn> arcsIn = new ArrayList<>();
    private final ArrayList<ArcOut> arcsOut = new ArrayList<>();
    private final Set<String> names = new HashSet<>();
    private final NetIndex idx = new NetIndex();

    private FactoryNetBuilder() { }

    public static NetIndex build(ModelConfig cfg) throws ExceptionInvalidTimeDelay {
        cfg.validate();
        return new FactoryNetBuilder().buildNet(cfg);
    }

    private NetIndex buildNet(ModelConfig cfg) throws ExceptionInvalidTimeDelay {
        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();

        final int K = ModelConfig.TYPES;
        final int M = ModelConfig.WORKSTATIONS;
        final int N = cfg.queuePlaces();
        idx.queuePlaces = N;

        idx.tRoute = new PetriT[K + 1];
        idx.pRes = new PetriP[M + 1];
        idx.pFree = new PetriP[M + 1][N + 1];
        idx.pIn = new PetriP[K + 1][M + 1];
        idx.pQueue = new PetriP[K + 1][M + 1][];
        idx.pBusy = new PetriP[K + 1][M + 1];
        idx.pOut = new PetriP[K + 1];
        idx.tIn = new PetriT[K + 1][M + 1];
        idx.tMove = new PetriT[K + 1][M + 1][];
        idx.tStart = new PetriT[K + 1][M + 1];
        idx.tComplete = new PetriT[K + 1][M + 1];

        idx.p0 = place("P0", 1);
        idx.pFork = place("P_fork", 0);
        idx.tGen = trans("Tgen", cfg.meanInterval(), new TransitionInfo(TransitionRole.GENERATE, 0, 0, 0, false));
        idx.tGen.setDistribution("exp", cfg.meanInterval());
        arcIn(idx.p0, idx.tGen);
        arcOut(idx.tGen, idx.p0);
        arcOut(idx.tGen, idx.pFork);

        for (int m = 1; m <= M; m++) {
            idx.pRes[m] = place("P_res" + m, cfg.machines(m));
            for (int n = 1; n <= N; n++) {
                idx.pFree[m][n] = place("P_free_" + m + "_" + n, 1);
            }
        }

        for (int k = 1; k <= K; k++) {
            for (int m : cfg.route(k)) {
                idx.pIn[k][m] = place("P_in_" + k + "_" + m, 0);
            }
            idx.pOut[k] = place("P_out" + k, 0);
        }

        for (int k = 1; k <= K; k++) {
            PetriT route = trans("Troute" + k, 0.0, new TransitionInfo(TransitionRole.ROUTE, k, 0, 0, false));
            route.setPriority(1);
            route.setProbability(cfg.typeProb(k));
            arcIn(idx.pFork, route);
            arcOut(route, idx.pIn[k][cfg.route(k)[0]]);
            idx.tRoute[k] = route;
        }

        for (int k = 1; k <= K; k++) {
            int[] route = cfg.route(k);
            for (int s = 0; s < route.length; s++) {
                int m = route[s];
                boolean last = (s == route.length - 1);
                buildFragment(cfg, k, m, s, last ? idx.pOut[k] : idx.pIn[k][route[s + 1]], last);
            }
        }

        idx.net = new PetriNet("Factory_N" + N, places, transitions, arcsIn, arcsOut);
        idx.net.validateStructure();

        PetriP.initNext();
        PetriT.initNext();
        ArcIn.initNext();
        ArcOut.initNext();
        return idx;
    }

    private void buildFragment(ModelConfig cfg, int k, int m, int step, PetriP next, boolean last) {
        int N = idx.queuePlaces;
        String km = k + "_" + m;

        PetriP[] q = new PetriP[N + 1];
        for (int n = 1; n <= N; n++) {
            q[n] = place("P_q" + km + "_" + n, 0);
        }
        PetriP busy = place("P_b" + km, 0);

        PetriT in = trans("T_in" + km, 0.0, new TransitionInfo(TransitionRole.ENTER, k, m, N, false));
        arcIn(idx.pIn[k][m], in);
        arcIn(idx.pFree[m][N], in);
        arcOut(in, q[N]);

        PetriT[] mv = new PetriT[N + 1];
        for (int n = N; n >= 2; n--) {
            PetriT t = trans("T_mv" + km + "_" + n, 0.0, new TransitionInfo(TransitionRole.MOVE, k, m, n, false));
            arcIn(q[n], t);
            arcIn(idx.pFree[m][n - 1], t);
            arcOut(t, q[n - 1]);
            arcOut(t, idx.pFree[m][n]);
            mv[n] = t;
        }

        PetriT start = trans("T_st" + km, 0.0, new TransitionInfo(TransitionRole.START, k, m, 1, false));
        arcIn(q[1], start);
        arcIn(idx.pRes[m], start);
        arcOut(start, busy);
        arcOut(start, idx.pFree[m][1]);

        double tau = cfg.tau(k, step);
        PetriT complete = trans("Complete" + km, tau, new TransitionInfo(TransitionRole.COMPLETE, k, m, 0, last));
        complete.setDistribution("erlang2", tau);
        arcIn(busy, complete);
        arcOut(complete, idx.pRes[m]);
        arcOut(complete, next);

        idx.pQueue[k][m] = q;
        idx.pBusy[k][m] = busy;
        idx.tIn[k][m] = in;
        idx.tMove[k][m] = mv;
        idx.tStart[k][m] = start;
        idx.tComplete[k][m] = complete;
    }

    private PetriP place(String name, int mark) {
        checkUnique(name);
        PetriP p = new PetriP(name, mark);
        places.add(p);
        return p;
    }

    private PetriT trans(String name, double delay, TransitionInfo info) {
        checkUnique(name);
        PetriT t = new PetriT(name, delay);
        transitions.add(t);
        idx.info.put(t.getNumber(), info);
        return t;
    }

    private void arcIn(PetriP p, PetriT t) {
        arcsIn.add(new ArcIn(p, t, 1));
    }

    private void arcOut(PetriT t, PetriP p) {
        arcsOut.add(new ArcOut(t, p, 1));
    }

    private void checkUnique(String name) {
        if (!names.add(name)) {
            throw new IllegalStateException("Назва елемента повторюється: " + name);
        }
    }
}