package production.experiments;

import production.Main;
import production.config.ModelConfig;
import production.net.FactoryNetBuilder;
import production.net.NetIndex;
import production.sim.FactorySimulator;
import production.stats.SimulationResult;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class Verification {

    static final int REPS = 20;
    static final double T_MOD = 1000;
    static final int PILOT_N = 10;

    record Row(String variable, String value, String expected, ModelConfig cfg) { }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("results"));
        protocol();
        verificationTable();
    }

    static void protocol() throws Exception {
        ModelConfig cfg = ModelConfig.defaults().withQueuePlaces(3).withTime(3, 0);
        Path file = Path.of("results", "protocol.txt");
        try (PrintStream out = new PrintStream(Files.newOutputStream(file), true, StandardCharsets.UTF_8)) {
            out.println("Протокол подій: " + cfg);
            NetIndex idx = FactoryNetBuilder.build(cfg);
            FactorySimulator sim = new FactorySimulator(cfg, idx);
            sim.setProtocol(out, cfg.timeModeling());
            sim.run();
        }
        System.out.println("1. Протокол подій (N = 3, перші 3 год) записано у " + file.toAbsolutePath());
        System.out.println();
    }

    static Row[] rows() {
        ModelConfig b = ModelConfig.defaults().withQueuePlaces(PILOT_N).withTime(T_MOD, 0);
        return new Row[]{
                new Row("—", "значення з умови завдання", "базовий варіант", b),

                new Row("Середній інтервал надходження, год", "0.25 → 0.50", "U_m удвічі ↓, W ↓↓", b.withMeanInterval(0.50)),
                new Row("Середній інтервал надходження, год", "0.25 → 0.20", "U_m ↑ (не більше 1), W ↑↑", b.withMeanInterval(0.20)),

                new Row("Ймовірності типів (p1, p2, p3)", "(0.3,0.5,0.2) → (0.2,0.6,0.2)", "U2 ↓, U4 → 1, W_type2 ↑", b.withTypeProbabilities(0.2, 0.6, 0.2)),
                new Row("Ймовірності типів (p1, p2, p3)", "(0.3,0.5,0.2) → (0.4,0.4,0.2)", "U4 ↓, U2 = 1, W_type1 ↑", b.withTypeProbabilities(0.4, 0.4, 0.2)),

                new Row("Час обробки на всіх РМ", "× 0.5", "U_m удвічі ↓, W ↓↓", b.withServiceTimesScaled(0.5)),
                new Row("Час обробки на всіх РМ", "× 1.5", "U_m ↑ (не більше 1), W ↑↑", b.withServiceTimesScaled(1.5)),
                new Row("Час обробки на РМ2", "× 0.7", "лише U2 ↓, W ↓↓", b.withServiceTimesScaledAt(2, 0.7)),
                new Row("Час обробки на РМ3", "× 1.3", "лише U3 ↑, W ↑ мало", b.withServiceTimesScaledAt(3, 1.3)),

                new Row("Кількість верстатів РМ2", "2 → 3", "U2 ↓ у 1.5 раза, W ↓↓", b.withMachines(2, 3)),
                new Row("Кількість верстатів РМ2", "2 → 1", "U2 = 1, W ↑↑", b.withMachines(2, 1)),
                new Row("Кількість верстатів РМ3", "4 → 3", "U3 ↑ (ρ3 = 0.967), W ↑", b.withMachines(3, 3)),
                new Row("Кількість верстатів РМ5", "1 → 2", "U5 удвічі ↓, W ↓", b.withMachines(5, 2)),
        };
    }

    static void verificationTable() throws Exception {
        Row[] rows = rows();
        System.out.printf(Locale.US, "2. Таблиця верифікації: T_mod = %.0f год, T_w = 0, по %d прогонів на рядок;%n", T_MOD, REPS);
        System.out.println("   N обирається для кожного рядка за методикою QueueCapacityStudy (без відмов).");
        System.out.println("   Незмінні змінні: усі, крім зазначеної в рядку (маршрути, розподіли — з умови завдання).");
        System.out.println("  №  Змінна / значення                                    W, год  ΔW,%    T, год  ΔT,%    U1/ρ1        U2/ρ2        U3/ρ3        U4/ρ4        U5/ρ5        N  спроб");

        StringBuilder csv = new StringBuilder("№;Вхідна змінна;Значення;Очікувана зміна;Інтервал, год;p1;p2;p3;C1;C2;C3;C4;C5;"
                + "W, год;σ(W), год;ΔW, %;T, год;σ(T), год;ΔT, %;U1;U2;U3;U4;U5;rho1;rho2;rho3;rho4;rho5;"
                + "W_ws1;W_ws2;W_ws3;W_ws4;W_ws5;W_type1;W_type2;W_type3;D1;D2;D3;N_avg;N;refusals\n");

        double baseW = Double.NaN, baseT = Double.NaN;
        for (int i = 0; i < rows.length; i++) {
            Row row = rows[i];
            ModelConfig cfg = row.cfg();
            double[] w = new double[REPS], t = new double[REPS];
            double[] u = new double[6], wws = new double[6], wk = new double[4], d = new double[4];
            double nAvg = 0;
            long refusals = 0;

            QueueCapacityStudy.Series series = QueueCapacityStudy.runWithoutRefusals(cfg, REPS);
            for (int rep = 0; rep < REPS; rep++) {
                SimulationResult r = series.results().get(rep);
                w[rep] = r.avgWait;
                t[rep] = r.avgTotalTime;
                for (int m = 1; m <= 5; m++) { u[m] += r.utilization[m] / REPS; wws[m] += r.avgWaitWs[m] / REPS; }
                for (int k = 1; k <= 3; k++) { wk[k] += r.avgWaitByType[k] / REPS; d[k] += (double) r.completed[k] / REPS; }
                nAvg += r.avgInSystem / REPS;
                refusals += r.refusals;
            }
            double mw = mean(w), mt = mean(t);
            if (i == 0) { baseW = mw; baseT = mt; }
            double dW = 100 * (mw - baseW) / baseW, dT = 100 * (mt - baseT) / baseT;

            StringBuilder us = new StringBuilder();
            for (int m = 1; m <= 5; m++) us.append(String.format(Locale.US, "  %.3f/%.3f", u[m], cfg.theoreticalLoad(m)));
            System.out.printf(Locale.US, "  %-2d %-52s %7.2f %6.0f   %7.2f %6.0f %s   %5d  %3d%n",
                    i + 1, shorten(row.variable() + ": " + row.value(), 52), mw, dW, mt, dT, us,
                    series.queuePlaces(), series.attempts());

            csv.append(i + 1).append(';').append(row.variable()).append(';').append(row.value()).append(';').append(row.expected())
                    .append(';').append(n(cfg.meanInterval()));
            for (int k = 1; k <= 3; k++) csv.append(';').append(n(cfg.typeProb(k)));
            for (int m = 1; m <= 5; m++) csv.append(';').append(cfg.machines(m));
            csv.append(';').append(n(mw)).append(';').append(n(std(w, mw))).append(';').append(n(dW))
                    .append(';').append(n(mt)).append(';').append(n(std(t, mt))).append(';').append(n(dT));
            for (int m = 1; m <= 5; m++) csv.append(';').append(n(u[m]));
            for (int m = 1; m <= 5; m++) csv.append(';').append(n(cfg.theoreticalLoad(m)));
            for (int m = 1; m <= 5; m++) csv.append(';').append(n(wws[m]));
            for (int k = 1; k <= 3; k++) csv.append(';').append(n(wk[k]));
            for (int k = 1; k <= 3; k++) csv.append(';').append(n(d[k]));
            csv.append(';').append(n(nAvg)).append(';').append(series.queuePlaces()).append(';').append(refusals).append('\n');
        }
        Path file = Path.of("results", "verification.csv");
        Files.writeString(file, csv.toString(), StandardCharsets.UTF_8);
        System.out.println("   ΔW, ΔT — зміна відносно базового варіанта (рядок 1); N — обрана кількість місць у черзі;");
        System.out.println("   спроб — скільки разів серію повторено зі збільшеним N через відмови. В усіх прогонах відмов 0.");
        System.out.println("   Таблицю записано у " + file.toAbsolutePath());
    }

    static double mean(double[] x) {
        double s = 0;
        for (double v : x) s += v;
        return s / x.length;
    }

    static double std(double[] x, double mean) {
        double s = 0;
        for (double v : x) s += (v - mean) * (v - mean);
        return Math.sqrt(s / (x.length - 1));
    }

    static String shorten(String s, int len) {
        return s.length() <= len ? s : s.substring(0, len - 1) + "…";
    }

    static String n(double v) {
        return String.format(Locale.US, "%.4f", v).replace('.', ',');
    }
}