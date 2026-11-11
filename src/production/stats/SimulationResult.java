package production.stats;

import production.config.ModelConfig;
import java.util.Locale;

public class SimulationResult {
    public final ModelConfig cfg;
    public double observationTime;        // T_obs = T_mod - T_w

    // 2.3.1 Основні вихідні змінні
    public double avgWait;                // W, (2.1)
    public double avgTotalTime;           // T_sys, (2.2)
    public double[] utilization = new double[6];       // U_m, (2.3)

    // 2.3.2 Характеристики РМ
    public double[] avgWaitWs = new double[6];         // W^(m), (2.4)
    public double[] avgQueue = new double[6];          // L̄_m, (2.5)
    public int[] maxQueue = new int[6];                // L^max_m, (2.6)

    // 2.3.3 За типами
    public long[] completed = new long[4];             // D_k, (2.7)
    public double[] avgWaitByType = new double[4];     // W_k, (2.8)
    public double[] avgTotalTimeByType = new double[4];// T_k, (2.9)

    // 2.3.4 Загальні
    public double avgInSystem;            // N̄, (2.10)
    public int maxInSystem;               // N^max, (2.11)

    // Для верифікації
    public double[][] avgWaitTypeWs = new double[4][6]; // W_k,m: очікування типу k на РМ m (перевірка FIFO)
    public long[] arrived = new long[4];                // надійшло завдань типу k за T_obs
    public double[] throughput = new double[4];         // D_k / T_obs
    public double maxInBuffer;            // максимум маркерів у P_in (чи вистачає N)
    public double avgInBuffer;            // середня сумарна кількість маркерів у P_in
    public long refusals;
    public long[] refusalsWs = new long[6];


    public SimulationResult(ModelConfig cfg) { this.cfg = cfg; }

    public String toTable() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "Результати: N = %d, T_mod = %.0f год, T_w = %.0f год, T_obs = %.0f год%n",
                cfg.queuePlaces(), cfg.timeModeling(), cfg.warmUp(), observationTime));
        sb.append(String.format(Locale.US, "  (2.1)  Середній час очікування W            = %.3f год%n", avgWait));
        sb.append(String.format(Locale.US, "  (2.2)  Середній час виконання T_sys         = %.3f год%n", avgTotalTime));
        sb.append("  РМ   (2.3) U_m   ρ_m теор.  (2.4) W^(m), год  (2.5) L̄_m  (2.6) L_max\n");
        for (int m = 1; m <= ModelConfig.WORKSTATIONS; m++) {
            sb.append(String.format(Locale.US, "  %d    %.3f       %.3f      %8.3f          %7.2f     %5d%n",
                    m, utilization[m], cfg.theoreticalLoad(m), avgWaitWs[m], avgQueue[m], maxQueue[m]));
        }
        sb.append("  Тип  (2.7) D_k  надійшло  D_k/T_obs  λ_k   (2.8) W_k, год  (2.9) T_k, год\n");
        for (int k = 1; k <= ModelConfig.TYPES; k++) {
            sb.append(String.format(Locale.US, "  %d    %6d     %6d    %.3f     %.2f   %8.3f        %8.3f%n",
                    k, completed[k], arrived[k], throughput[k], cfg.lambda(k), avgWaitByType[k], avgTotalTimeByType[k]));
        }
        sb.append(String.format(Locale.US, "  (2.10) Середня кількість завдань у системі = %.2f%n", avgInSystem));
        sb.append(String.format(Locale.US, "  (2.11) Максимальна кількість у системі    = %d%n", maxInSystem));
        sb.append("  Перевірка FIFO — очікування W_k,m за типами на кожному РМ, год:\n");
        for (int m = 1; m <= ModelConfig.WORKSTATIONS; m++) {
            sb.append("    РМ").append(m).append(':');
            for (int k = 1; k <= ModelConfig.TYPES; k++) {
                if (cfg.tauAt(k, m) > 0) sb.append(String.format(Locale.US, "  тип %d = %.3f", k, avgWaitTypeWs[k][m]));
            }
            sb.append('\n');
        }
        sb.append(String.format(Locale.US, "  Контроль N: відмов = %d, максимум у P_in = %.0f%n", refusals, maxInBuffer));
        return sb.toString();
    }

    public static String csvHeader() {
        StringBuilder sb = new StringBuilder("N;T_mod;T_w;C1;C2;C3;C4;C5;W;T_sys");
        for (int m = 1; m <= 5; m++) sb.append(";U").append(m);
        for (int m = 1; m <= 5; m++) sb.append(";W_ws").append(m);
        for (int m = 1; m <= 5; m++) sb.append(";L_avg").append(m);
        for (int m = 1; m <= 5; m++) sb.append(";L_max").append(m);
        for (int k = 1; k <= 3; k++) sb.append(";D").append(k);
        for (int k = 1; k <= 3; k++) sb.append(";W_type").append(k);
        for (int k = 1; k <= 3; k++) sb.append(";T_type").append(k);
        sb.append(";N_avg;N_max");
        return sb.toString();
    }

    public String csvRow() {
        StringBuilder sb = new StringBuilder();
        sb.append(cfg.queuePlaces()).append(';').append(num(cfg.timeModeling())).append(';').append(num(cfg.warmUp()));
        for (int m = 1; m <= 5; m++) sb.append(';').append(cfg.machines(m));
        sb.append(';').append(num(avgWait)).append(';').append(num(avgTotalTime));
        for (int m = 1; m <= 5; m++) sb.append(';').append(num(utilization[m]));
        for (int m = 1; m <= 5; m++) sb.append(';').append(num(avgWaitWs[m]));
        for (int m = 1; m <= 5; m++) sb.append(';').append(num(avgQueue[m]));
        for (int m = 1; m <= 5; m++) sb.append(';').append(maxQueue[m]);
        for (int k = 1; k <= 3; k++) sb.append(';').append(completed[k]);
        for (int k = 1; k <= 3; k++) sb.append(';').append(num(avgWaitByType[k]));
        for (int k = 1; k <= 3; k++) sb.append(';').append(num(avgTotalTimeByType[k]));
        sb.append(';').append(num(avgInSystem)).append(';').append(maxInSystem);
        return sb.toString();
    }

    private static String num(double v) {
        return String.format(Locale.US, "%.5f", v).replace('.', ',');
    }
}