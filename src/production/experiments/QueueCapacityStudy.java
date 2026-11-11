package production.experiments;

import production.Main;
import production.config.ModelConfig;
import production.stats.SimulationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QueueCapacityStudy {
    static final int PILOT_RUNS = 10;
    static final int PILOT_N = 10;
    static final double MARGIN = 1.2;
    static final double STEP = 1.25;
    static final int MIN_N = 5;

    public record Series(int queuePlaces, int attempts, List<SimulationResult> results) { }

    public static int chooseQueuePlaces(ModelConfig cfg) throws Exception {
        int lMax = 0;
        for (int run = 0; run < PILOT_RUNS; run++) {
            SimulationResult r = Main.runOnce(cfg.withQueuePlaces(PILOT_N));
            for (int m = 1; m <= ModelConfig.WORKSTATIONS; m++) lMax = Math.max(lMax, r.maxQueue[m]);
        }
        return Math.max(MIN_N, (int) Math.ceil(MARGIN * lMax));
    }

    public static Series runWithoutRefusals(ModelConfig cfg, int runs) throws Exception {
        int n = chooseQueuePlaces(cfg);
        for (int attempt = 1; ; attempt++) {
            List<SimulationResult> results = new ArrayList<>();
            boolean refused = false;
            for (int run = 0; run < runs && !refused; run++) {
                SimulationResult r = Main.runOnce(cfg.withQueuePlaces(n));
                results.add(r);
                refused = r.refusals > 0;
            }
            if (!refused) return new Series(n, attempt, results);
            n = (int) Math.ceil(n * STEP);
        }
    }

    public static void main(String[] args) throws Exception {
        ModelConfig base = ModelConfig.defaults();

        System.out.printf("=== Етап 1. Максимальна довжина черги L_max (пробні прогони з N = %d) ===%n", PILOT_N);
        System.out.println("  T_mod   прогін   L_max по РМ1..РМ5          max");
        for (double tMod : new double[]{1000, 10000, 20000}) {
            int worst = 0;
            for (int run = 1; run <= PILOT_RUNS; run++) {
                SimulationResult r = Main.runOnce(base.withQueuePlaces(PILOT_N).withTime(tMod, 0));
                StringBuilder sb = new StringBuilder();
                int mx = 0;
                for (int m = 1; m <= 5; m++) {
                    sb.append(String.format("%6d", r.maxQueue[m]));
                    mx = Math.max(mx, r.maxQueue[m]);
                }
                worst = Math.max(worst, mx);
                System.out.printf(Locale.US, "  %6.0f   %3d  %s   %5d%n", tMod, run, sb, mx);
            }
            System.out.printf(Locale.US, "  T_mod = %.0f год: найбільше L_max = %d → N = ⌈%.1f · %d⌉ = %d%n%n",
                    tMod, worst, MARGIN, worst, (int) Math.ceil(MARGIN * worst));
        }

        System.out.println("=== Етап 2. Вплив N при T_mod = 1000 год (по " + PILOT_RUNS + " прогонів) ===");
        System.out.println("     N   позицій  переходів   прогонів з відмовами   відмов сер.   частка відмов, %   час прогону, мс");
        for (int n : new int[]{25, 50, 100, 150, 200, 300, 500}) {
            int bad = 0;
            double refusals = 0, arrived = 0;
            long t0 = System.nanoTime();
            for (int run = 0; run < PILOT_RUNS; run++) {
                SimulationResult r = Main.runOnce(base.withQueuePlaces(n).withTime(1000, 0));
                if (r.refusals > 0) bad++;
                refusals += r.refusals;
                for (int k = 1; k <= 3; k++) arrived += r.arrived[k] * base.route(k).length;  // надходжень на РМ
            }
            long ms = (System.nanoTime() - t0) / 1_000_000 / PILOT_RUNS;
            System.out.printf(Locale.US, "  %4d   %6d   %7d          %2d / %d          %8.1f        %8.3f          %6d%n",
                    n, 34 + 17 * n, 28 + 12 * n, bad, PILOT_RUNS, refusals / PILOT_RUNS, 100 * refusals / arrived, ms);
        }
    }
}