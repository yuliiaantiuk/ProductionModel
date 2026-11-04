package production.experiments;

import production.Main;
import production.config.ModelConfig;
import production.stats.SimulationResult;

import java.util.Locale;

public class QueueCapacityStudy {

    static final int RUNS = 10;

    public static void main(String[] args) throws Exception {
        ModelConfig base = ModelConfig.defaults();

        System.out.println("=== Етап 1. Максимальна довжина черги (прогони з N = 10) ===");
        System.out.println("T_mod   прогін:  L_max по РМ1..РМ5        max_m L_max");
        for (double tMod : new double[]{1000, 5000, 10000}) {
            int worst = 0;
            double sum = 0;
            for (int run = 1; run <= RUNS; run++) {
                SimulationResult r = Main.runOnce(base.withQueuePlaces(10).withTime(tMod, 0));
                int mx = 0;
                StringBuilder sb = new StringBuilder();
                for (int m = 1; m <= 5; m++) {
                    sb.append(String.format("%5d", r.maxQueue[m]));
                    mx = Math.max(mx, r.maxQueue[m]);
                }
                System.out.printf(Locale.US, "%6.0f  %2d:  %s      %4d%n", tMod, run, sb, mx);
                worst = Math.max(worst, mx);
                sum += mx;
            }
            System.out.printf(Locale.US, "  T_mod = %.0f: середнє max L = %.1f, найбільше = %d%n%n", tMod, sum / RUNS, worst);
        }

        System.out.println("=== Етап 2. Кандидати N при T_mod = 1000 год ===");
        System.out.println("  N   max P_in (з 10 прогонів)  частка прогонів з P_in>0   W сер.   час 1 прогону, мс");
        for (int n : new int[]{25, 50, 100, 150, 200}) {
            double maxBuf = 0, w = 0;
            int bad = 0;
            long t0 = System.nanoTime();
            for (int run = 1; run <= RUNS; run++) {
                SimulationResult r = Main.runOnce(base.withQueuePlaces(n).withTime(1000, 0));
                maxBuf = Math.max(maxBuf, r.maxInBuffer);
                if (r.maxInBuffer > 0) bad++;
                w += r.avgWait;
            }
            long ms = (System.nanoTime() - t0) / 1_000_000 / RUNS;
            System.out.printf(Locale.US, "%4d   %10.0f               %2d / %d                 %6.2f   %6d%n",
                    n, maxBuf, bad, RUNS, w / RUNS, ms);
        }
    }
}