package production;

import production.config.ModelConfig;
import production.net.FactoryNetBuilder;
import production.net.NetIndex;
import production.sim.FactorySimulator;
import production.sim.SimulationListener;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriSim;

import java.util.ArrayList;

public class Main {

    /** Тимчасовий спостерігач: завантаження РМ і кількість завершених (до появи StatisticsCollector). */
    static class QuickStats implements SimulationListener {
        NetIndex idx; ModelConfig cfg;
        double[] busyTime = new double[ModelConfig.WORKSTATIONS + 1];
        QuickStats(ModelConfig cfg) { this.cfg = cfg; }
        public void onStart(NetIndex idx) { this.idx = idx; }
        public void onTimeAdvance(double from, double to) {
            for (int m = 1; m <= ModelConfig.WORKSTATIONS; m++)
                busyTime[m] += (cfg.machines(m) - idx.pRes[m].getMark()) * (to - from);
        }
        double util(int m) { return busyTime[m] / (cfg.machines(m) * cfg.timeModeling()); }
    }

    public static void main(String[] args) throws Exception {
        // ---- 1. Протокол подій: N = 3, перші 3 год ----
        ModelConfig small = ModelConfig.defaults().withQueuePlaces(3);
        NetIndex idx0 = FactoryNetBuilder.build(small);
        FactorySimulator sim0 = new FactorySimulator(small.withTime(3.0, 0), idx0);
        sim0.setProtocol(System.out, 3.0);
        sim0.run();

        // ---- 2. Порівняння з бібліотекою: N = 30, T_mod = 1000, по 20 прогонів ----
        ModelConfig cfg = ModelConfig.defaults().withQueuePlaces(30);
        int runs = 20;
        double[] uOwn = new double[6], uLib = new double[6];
        double[] dOwn = new double[4], dLib = new double[4];
        long msOwn = 0, msLib = 0;
        for (int r = 0; r < runs; r++) {
            NetIndex a = FactoryNetBuilder.build(cfg);
            FactorySimulator sim = new FactorySimulator(cfg, a);
            QuickStats qs = new QuickStats(cfg);
            sim.setListener(qs);
            sim.setCheckInvariants(true);
            long t0 = System.currentTimeMillis();
            sim.run();
            msOwn += System.currentTimeMillis() - t0;
            for (int m = 1; m <= 5; m++) uOwn[m] += qs.util(m) / runs;
            for (int k = 1; k <= 3; k++) dOwn[k] += a.pOut[k].getMark() / (double) runs;

            NetIndex b = FactoryNetBuilder.build(cfg);
            ArrayList<PetriSim> list = new ArrayList<>();
            list.add(new PetriSim(b.net));
            PetriObjModel model = new PetriObjModel(list);
            model.setIsProtokol(false);
            t0 = System.currentTimeMillis();
            model.go(cfg.timeModeling());
            msLib += System.currentTimeMillis() - t0;
            for (int m = 1; m <= 5; m++) uLib[m] += (cfg.machines(m) - b.pRes[m].getMean()) / cfg.machines(m) / runs;
            for (int k = 1; k <= 3; k++) dLib[k] += b.pOut[k].getMark() / (double) runs;
        }
        System.out.printf("%nПорівняння (N = 30, T_mod = 1000 год, середнє з %d прогонів)%n", runs);
        System.out.println("            власний   бібліотека   теоретичне ρ");
        for (int m = 1; m <= 5; m++)
            System.out.printf("  U РМ%d     %.3f     %.3f        %.3f%n", m, uOwn[m], uLib[m], cfg.theoreticalLoad(m));
        for (int k = 1; k <= 3; k++)
            System.out.printf("  D тип %d   %.0f      %.0f         %.0f (λ·T_mod)%n", k, dOwn[k], dLib[k], cfg.lambda(k) * cfg.timeModeling());
        System.out.printf("  Час на прогін: власний %d мс, бібліотека %d мс%n", msOwn / runs, msLib / runs);

        // ---- 3. Швидкодія власного алгоритму для великого N ----
        for (int n : new int[]{120, 300}) {
            ModelConfig big = ModelConfig.defaults().withQueuePlaces(n).withTime(10000, 0);
            NetIndex c = FactoryNetBuilder.build(big);
            FactorySimulator sim = new FactorySimulator(big, c);
            long t0 = System.currentTimeMillis();
            sim.run();
            System.out.printf("Власний алгоритм: N = %d, T_mod = 10000 год — %d мс, %d спрацювань%n",
                    n, System.currentTimeMillis() - t0, sim.getFirings());
        }
    }
}