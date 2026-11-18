package production.stats;

import production.config.ModelConfig;
import production.net.NetIndex;
import production.net.TransitionInfo;
import production.net.TransitionRole;
import production.sim.SimulationListener;
import ua.stetsenkoinna.petriobj.PetriT;

import java.util.ArrayList;
import java.util.List;

public class StatisticsCollector implements SimulationListener {

    private static final int K = ModelConfig.TYPES;
    private static final int M = ModelConfig.WORKSTATIONS;

    private final ModelConfig cfg;
    private final double tWarm;
    private final double tMod;
    private NetIndex idx;

    private final int[][] queue = new int[K + 1][M + 1];   // Q_k,m
    private final int[] inSystem = new int[K + 1];         // S_k

    private final double[][] queueTime = new double[K + 1][M + 1]; // Σ Q_k,m,i · Δt_i
    private final double[] busyTime = new double[M + 1];           // Σ B_m,i · Δt_i
    private final double[] inSystemTime = new double[K + 1];       // Σ S_k,i · Δt_i
    private double inBufferTime = 0;                               // Σ (маркери в P_in) · Δt_i

    private final long[][] started = new long[K + 1][M + 1];   // A_k,m — запуски T_st_k_m
    private final long[] completed = new long[K + 1];          // D_k
    private final long[] arrived = new long[K + 1];

    private final int[] maxQueue = new int[M + 1];
    private final long[] refusals = new long[M + 1];
    private int maxInSystem = 0;
    private int maxInBuffer = 0;

    private double traceStep = 0;
    private double nextTrace = Double.POSITIVE_INFINITY;
    private final List<double[]> trace = new ArrayList<>();

    public StatisticsCollector(ModelConfig cfg) {
        this.cfg = cfg;
        this.tWarm = cfg.warmUp();
        this.tMod = cfg.timeModeling();
    }

    public void setTraceStep(double step) {
        this.traceStep = step;
    }

    @Override
    public void onStart(NetIndex idx) {
        this.idx = idx;
        if (traceStep > 0) nextTrace = tWarm + traceStep;
    }

    @Override
    public void onOutput(PetriT t, double time) {
        TransitionInfo info = idx.info(t);
        int k = info.type();
        switch (info.role()) {
            case ROUTE -> {
                int first = cfg.route(k)[0];
                countRefusal(first);
                queue[k][first]++;
                inSystem[k]++;
                if (time >= tWarm) arrived[k]++;
            }
            case COMPLETE -> {
                if (info.lastOperation()) {
                    inSystem[k]--;
                    if (time >= tWarm) completed[k]++;
                } else {
                    int next = nextWorkstation(k, info.workstation());
                    countRefusal(next);
                    queue[k][next]++;
                }
            }
            default -> { }
        }
    }

    private void countRefusal(int m) {
        if (idx.pFree[m][idx.queuePlaces].getMark() == 0) refusals[m]++;
    }

    @Override
    public void onInput(PetriT t, double time) {
        TransitionInfo info = idx.info(t);
        if (info.role() == TransitionRole.START) {
            queue[info.type()][info.workstation()]--;
            if (time >= tWarm) started[info.type()][info.workstation()]++;
        }
    }

    @Override
    public void onTimeAdvance(double from, double to) {
        while (nextTrace <= to && nextTrace <= tMod) {
            integrate(Math.max(from, tWarm), nextTrace);
            recordTrace(nextTrace);
            from = Math.max(from, nextTrace);
            nextTrace += traceStep;
        }
        integrate(Math.max(from, tWarm), Math.min(to, tMod));
    }

    private void integrate(double a, double b) {
        double dt = b - a;
        if (dt <= 0) return;

        int total = 0;
        for (int m = 1; m <= M; m++) {
            int length = 0;
            for (int k = 1; k <= K; k++) {
                queueTime[k][m] += queue[k][m] * dt;
                length += queue[k][m];
            }
            if (length > maxQueue[m]) maxQueue[m] = length;
            busyTime[m] += (cfg.machines(m) - idx.pRes[m].getMark()) * dt;
        }
        int buffer = 0;
        for (int k = 1; k <= K; k++) {
            inSystemTime[k] += inSystem[k] * dt;
            total += inSystem[k];
            for (int m : cfg.route(k)) buffer += idx.pIn[k][m].getMark();
        }
        if (total > maxInSystem) maxInSystem = total;
        if (buffer > maxInBuffer) maxInBuffer = buffer;
        inBufferTime += buffer * dt;
    }

    private void recordTrace(double time) {
        double obs = time - tWarm;
        double[] row = new double[6 + M];
        row[0] = time;
        long done = 0;
        double waitSum = 0, sysSum = 0;
        for (int k = 1; k <= K; k++) {
            done += completed[k];
            sysSum += inSystemTime[k];
            for (int m = 1; m <= M; m++) waitSum += queueTime[k][m];
        }
        row[1] = done > 0 ? waitSum / done : Double.NaN;
        row[2] = done > 0 ? sysSum / done : Double.NaN;
        for (int m = 1; m <= M; m++) row[2 + m] = busyTime[m] / (cfg.machines(m) * obs);
        row[3 + M] = done;
        row[4 + M] = waitSum;
        row[5 + M] = sysSum;
        trace.add(row);
    }

    public List<double[]> getTrace() { return trace; }

    private int nextWorkstation(int k, int m) {
        int[] r = cfg.route(k);
        for (int s = 0; s < r.length - 1; s++) if (r[s] == m) return r[s + 1];
        throw new IllegalStateException("РМ" + m + " не є проміжним у маршруті типу " + k);
    }

    public SimulationResult getResult() {
        SimulationResult r = new SimulationResult(cfg);
        double tObs = tMod - tWarm;
        r.observationTime = tObs;

        long totalDone = 0;
        double totalWait = 0, totalSys = 0;
        for (int k = 1; k <= K; k++) {
            double waitK = 0;
            for (int m : cfg.route(k)) {
                waitK += queueTime[k][m];
                r.avgWaitTypeWs[k][m] = started[k][m] > 0 ? queueTime[k][m] / started[k][m] : Double.NaN;
            }
            r.completed[k] = completed[k];                                         // (2.7)
            r.arrived[k] = arrived[k];
            r.throughput[k] = completed[k] / tObs;
            r.avgWaitByType[k] = completed[k] > 0 ? waitK / completed[k] : Double.NaN;            // (2.8)
            r.avgTotalTimeByType[k] = completed[k] > 0 ? inSystemTime[k] / completed[k] : Double.NaN; // (2.9)
            totalDone += completed[k];
            totalWait += waitK;
            totalSys += inSystemTime[k];
        }
        r.avgWait = totalDone > 0 ? totalWait / totalDone : Double.NaN;          // (2.1)
        r.avgTotalTime = totalDone > 0 ? totalSys / totalDone : Double.NaN;      // (2.2)
        r.avgInSystem = totalSys / tObs;                                         // (2.10)
        r.maxInSystem = maxInSystem;                                             // (2.11)

        for (int m = 1; m <= M; m++) {
            double lengthTime = 0;
            long starts = 0;
            for (int k = 1; k <= K; k++) {
                lengthTime += queueTime[k][m];
                starts += started[k][m];
            }
            r.utilization[m] = busyTime[m] / (cfg.machines(m) * tObs);           // (2.3)
            r.avgWaitWs[m] = starts > 0 ? lengthTime / starts : Double.NaN;      // (2.4)
            r.avgQueue[m] = lengthTime / tObs;                                   // (2.5)
            r.maxQueue[m] = maxQueue[m];                                         // (2.6)
        }
        long totalRefusals = 0;
        for (int m = 1; m <= M; m++) { r.refusalsWs[m] = refusals[m]; totalRefusals += refusals[m]; }
        r.refusals = totalRefusals;
        r.maxInBuffer = maxInBuffer;
        r.avgInBuffer = inBufferTime / tObs;
        return r;
    }
}