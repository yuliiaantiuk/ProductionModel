package production.experiments;

import production.Main;
import production.config.ModelConfig;
import production.net.FactoryNetBuilder;
import production.net.NetIndex;
import production.sim.FactorySimulator;
import production.stats.SimulationResult;
import production.stats.StatisticsCollector;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriSim;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Locale;

public class Verification {
    static final int REPS = 3;

    static ModelConfig stableBase() {
        return ModelConfig.defaults()
                .withMachines(1, 4).withMachines(2, 3).withMachines(4, 4)
                .withQueuePlaces(60).withTime(10000, 500);
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("results"));
        protocol();
        invariants();
        verificationTable();
        compareWithLibrary();
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

    static void invariants() throws Exception {
        System.out.println("2. Перевірка інваріантів (при кожному просуванні часу):");
        System.out.println("   (а) M(P_res_m) + кількість завдань на верстатах РМ m = C_m;");
        System.out.println("   (б) для кожного місця n черги РМ m: M(P_free_m_n) + Σ_k M(P_q_k_m_n) = 1.");
        ModelConfig[] cfgs = {
                stableBase().withTime(5000, 0),
                ModelConfig.defaults().withQueuePlaces(200).withTime(1000, 0),
                ModelConfig.defaults().withQueuePlaces(10).withTime(1000, 0)   // з переповненням черги
        };
        for (ModelConfig cfg : cfgs) {
            NetIndex idx = FactoryNetBuilder.build(cfg);
            FactorySimulator sim = new FactorySimulator(cfg, idx);
            sim.setCheckInvariants(true);
            String verdict;
            try {
                sim.run();
                verdict = "порушень немає";
            } catch (IllegalStateException e) {
                verdict = "ПОРУШЕННЯ: " + e.getMessage();
            }
            System.out.printf(Locale.US, "   C = %s, N = %d, T_mod = %.0f: %,d спрацювань — %s%n",
                    machinesOf(cfg), cfg.queuePlaces(), cfg.timeModeling(), sim.getFirings(), verdict);
        }
        System.out.println();
    }

    record Row(String change, String expected, ModelConfig cfg) { }

    static void verificationTable() throws Exception {
        ModelConfig b = stableBase();
        Row[] rows = {
                new Row("базова стійка конфігурація", "U_m ≈ ρ_m", b),
                new Row("інтервал надходження 0.25 → 0.30", "U↓, W↓, T↓", b.withMeanInterval(0.30)),
                new Row("інтервал надходження 0.25 → 0.22", "U↑, W↑, T↑", b.withMeanInterval(0.22)),
                new Row("час обробки ×0.8", "U↓, W↓, T↓", b.withServiceTimesScaled(0.8)),
                new Row("час обробки ×1.1", "U↑, W↑, T↑", b.withServiceTimesScaled(1.1)),
                new Row("РМ5: 1 → 2 верстати", "U5 удвічі↓, W↓", b.withMachines(5, 2)),
                new Row("РМ3: 4 → 3 верстати", "U3↑, W↑", b.withMachines(3, 3)),
                new Row("початкова (3,2,4,3,1), 1000 год", "U2 ≈ 1, W різко↑",
                        ModelConfig.defaults().withQueuePlaces(200).withTime(1000, 0)),
        };

        System.out.printf("3. Таблиця верифікації (середнє з %d прогонів; ρ_m — теоретичне завантаження)%n", REPS);
        System.out.println("  №  Зміна                              Очікування         W, год   T_sys, год   U1/ρ1       U2/ρ2       U3/ρ3       U4/ρ4       U5/ρ5      max|D_k/T−λ_k|/λ_k  Літтл N̄ / λ·T_sys");
        StringBuilder csv = new StringBuilder("№;Зміна;Очікування;W;T_sys;U1;rho1;U2;rho2;U3;rho3;U4;rho4;U5;rho5;max_rel_throughput_err;N_avg;lambda_T\n");
        int no = 1;
        for (Row row : rows) {
            double w = 0, ts = 0, nAvg = 0, little = 0, thrErr = 0;
            double[] u = new double[6];
            for (int rep = 0; rep < REPS; rep++) {
                SimulationResult r = Main.runOnce(row.cfg());
                w += r.avgWait / REPS;
                ts += r.avgTotalTime / REPS;
                nAvg += r.avgInSystem / REPS;
                // Формула Літтла з ТЕОРЕТИЧНОЮ інтенсивністю λ = 1 / середній інтервал
                little += r.avgTotalTime / row.cfg().meanInterval() / REPS;
                for (int m = 1; m <= 5; m++) u[m] += r.utilization[m] / REPS;
                for (int k = 1; k <= 3; k++) {
                    double rel = Math.abs(r.throughput[k] - row.cfg().lambda(k)) / row.cfg().lambda(k);
                    thrErr = Math.max(thrErr, rel);
                }
            }
            StringBuilder us = new StringBuilder();
            csv.append(no).append(';').append(row.change()).append(';').append(row.expected())
                    .append(';').append(n(w)).append(';').append(n(ts));
            for (int m = 1; m <= 5; m++) {
                double rho = row.cfg().theoreticalLoad(m);
                us.append(String.format(Locale.US, "  %.3f/%.3f", u[m], rho));
                csv.append(';').append(n(u[m])).append(';').append(n(rho));
            }
            csv.append(';').append(n(thrErr)).append(';').append(n(nAvg)).append(';').append(n(little)).append('\n');
            System.out.printf(Locale.US, "  %d  %-34s %-17s %7.3f   %7.3f   %s       %5.1f %%          %.3f / %.3f%n",
                    no++, row.change(), row.expected(), w, ts, us, 100 * thrErr, nAvg, little);
        }
        Path file = Path.of("results", "verification.csv");
        Files.writeString(file, csv.toString(), StandardCharsets.UTF_8);
        System.out.println("   Таблицю записано у " + file.toAbsolutePath());
        System.out.println();
    }

    static void compareWithLibrary() throws Exception {
        final int runs = 5;
        ModelConfig cfg = stableBase().withQueuePlaces(40).withTime(2000, 0);  // бібліотека рахує від t = 0
        double[] uOwn = new double[6], uLib = new double[6], lOwn = new double[6], lLib = new double[6];
        double[] dOwn = new double[4], dLib = new double[4];
        long msOwn = 0, msLib = 0;

        for (int run = 0; run < runs; run++) {
            long t0 = System.nanoTime();
            SimulationResult r = Main.runOnce(cfg);
            msOwn += (System.nanoTime() - t0) / 1_000_000;
            for (int m = 1; m <= 5; m++) { uOwn[m] += r.utilization[m] / runs; lOwn[m] += r.avgQueue[m] / runs; }
            for (int k = 1; k <= 3; k++) dOwn[k] += (double) r.completed[k] / runs;

            NetIndex idx = FactoryNetBuilder.build(cfg);
            ArrayList<PetriSim> list = new ArrayList<>();
            list.add(new PetriSim(idx.net));
            PetriObjModel model = new PetriObjModel(list);
            model.setIsProtokol(false);
            t0 = System.nanoTime();
            model.go(cfg.timeModeling());
            msLib += (System.nanoTime() - t0) / 1_000_000;
            for (int m = 1; m <= 5; m++) {
                uLib[m] += (cfg.machines(m) - idx.pRes[m].getMean()) / cfg.machines(m) / runs;
                double q = 0;
                for (int k = 1; k <= 3; k++) {
                    if (idx.pIn[k][m] == null) continue;
                    q += idx.pIn[k][m].getMean();
                    for (int nn = 1; nn <= cfg.queuePlaces(); nn++) q += idx.pQueue[k][m][nn].getMean();
                }
                lLib[m] += q / runs;
            }
            for (int k = 1; k <= 3; k++) dLib[k] += (double) idx.pOut[k].getMark() / runs;
        }

        System.out.printf(Locale.US, "4. Власний алгоритм vs PetriObjModel.go(): C = %s, N = %d, T_mod = %.0f, по %d прогонів%n",
                machinesOf(cfg), cfg.queuePlaces(), cfg.timeModeling(), runs);
        System.out.println("   Показник        власний    бібліотека   різниця, %");
        for (int m = 1; m <= 5; m++) line("U" + m, uOwn[m], uLib[m]);
        for (int m = 1; m <= 5; m++) line("L̄" + m, lOwn[m], lLib[m]);
        for (int k = 1; k <= 3; k++) line("D" + k, dOwn[k], dLib[k]);
        System.out.printf(Locale.US, "   Час одного прогону: власний %d мс, бібліотека %d мс (у %.1f раза швидше)%n",
                msOwn / runs, msLib / runs, (double) msLib / Math.max(1, msOwn));
    }

    static void line(String name, double own, double lib) {
        System.out.printf(Locale.US, "   %-12s %10.3f %12.3f %10.1f%n", name, own, lib, 100 * (own - lib) / lib);
    }

    static String machinesOf(ModelConfig cfg) {
        StringBuilder sb = new StringBuilder("(");
        for (int m = 1; m <= 5; m++) sb.append(cfg.machines(m)).append(m < 5 ? "," : ")");
        return sb.toString();
    }

    static String n(double v) {
        return String.format(Locale.US, "%.4f", v).replace('.', ',');
    }
}