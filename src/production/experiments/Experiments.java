package production.experiments;

import production.Main;
import production.config.ModelConfig;
import production.net.FactoryNetBuilder;
import production.net.NetIndex;
import production.sim.FactorySimulator;
import production.stats.SimulationResult;
import production.stats.StatisticsCollector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Experiments {
    static final int BASE_RUNS = 5;
    static final double[] BASE_T_MOD = {1000, 10000, 20000};

    // Умови проведення експерименту
    static ModelConfig experimentBase() {
        return ModelConfig.defaults().withMachines(1, 4).withMachines(2, 3).withMachines(4, 4);
    }
    static final int TRANSIENT_RUNS = 6;
    static final double TRANSIENT_T_MAX = 50000;
    static final double TRACE_STEP = 500;
    static final double EPS_TIME = 0.1;
    static final double EPS_U = 0.02;
    static final double T_ROUND = 5000;
    static final double WARM_UP = 1000;
    static final int PILOT_RUNS = 20;
    static final double RUNS_EPS_TIME = 0.05;
    static final double RUNS_EPS_U = 0.005;

    // Оцінки відгуків і вплив факторів
    static final int EXPERIMENT_RUNS = 13;
    static final double EXPERIMENT_T_MOD = 30000;
    static final int[] LEVELS_C1 = {4, 5};
    static final int[] LEVELS_C2 = {3, 4};
    static final int[] LEVELS_C4 = {4, 5};
    static final int[] LEVELS_C5 = {1, 2};

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("results"));
        // Аналіз початкової конфігурації
        // baseAnalysis(BASE_T_MOD, BASE_RUNS, Path.of("results", "base_analysis.csv"));

        // Умови проведення експерименту
        // double tMod = transientStudy(experimentBase(), Path.of("results", "transient.csv"));
        // runsNumber(experimentBase().withTime(tMod, WARM_UP), Path.of("results", "runs_number.csv"));

        // Оцінки відгуків і вплив факторів
        factorialExperiment(Path.of("results", "factorial_runs.csv"), Path.of("results", "factorial_summary.csv"));
    }

    static List<List<double[]>> tracedRuns(ModelConfig cfg, int runs) throws Exception {
        int n = QueueCapacityStudy.chooseQueuePlaces(cfg);
        while (true) {
            List<List<double[]>> traces = new ArrayList<>();
            boolean refused = false;
            for (int run = 0; run < runs && !refused; run++) {
                ModelConfig c = cfg.withQueuePlaces(n);
                NetIndex idx = FactoryNetBuilder.build(c);
                FactorySimulator sim = new FactorySimulator(c, idx);
                StatisticsCollector stats = new StatisticsCollector(c);
                stats.setTraceStep(TRACE_STEP);
                sim.setListener(stats);
                sim.run();
                refused = stats.getResult().refusals > 0;
                traces.add(stats.getTrace());
                System.out.printf(Locale.US, "    спостереження %d з %d виконано (N = %d)%n", run + 1, runs, n);
            }
            if (!refused) return traces;
            n = (int) Math.ceil(n * 1.25);
        }
    }

    static double transientStudy(ModelConfig cfg, Path file) throws Exception {
        ModelConfig c = cfg.withTime(TRANSIENT_T_MAX, 0);
        System.out.println("4.4 Перехідний період, конфігурація C = " + machinesOf(c));
        List<List<double[]>> traces = tracedRuns(c, TRANSIENT_RUNS);

        StringBuilder csv = new StringBuilder("run;t;W;T;U1;U2;U3;U4;U5;completed;wait_sum;sys_sum\n");
        for (int i = 0; i < traces.size(); i++)
            for (double[] row : traces.get(i)) {
                csv.append(i + 1);
                for (double v : row) csv.append(';').append(num(v));
                csv.append('\n');
            }
        Files.writeString(file, csv.toString(), StandardCharsets.UTF_8);

        String[] names = {"W", "T", "U1", "U2", "U3", "U4", "U5"};
        int points = traces.get(0).size();
        double tTransient = 0;
        System.out.println("    Відгук   ε            T за (3.1), год   розкид y_i(T_max)");
        for (int col = 1; col <= names.length; col++) {
            double tCol = Double.NaN;
            double lastRange = 0, lastEps = 0;
            for (int j = points - 1; j >= 0; j--) {
                double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
                for (List<double[]> tr : traces) {
                    double y = tr.get(j)[col];
                    min = Math.min(min, y);
                    max = Math.max(max, y);
                }
                double eps = col <= 2 ? EPS_TIME : EPS_U;
                if (j == points - 1) { lastRange = max - min; lastEps = eps; }
                if (max - min < eps) tCol = traces.get(0).get(j)[0];
                else break;
            }
            String epsText = col <= 2 ? String.format(Locale.US, "%.2f год", EPS_TIME) : String.format(Locale.US, "%.3f", EPS_U);
            System.out.printf(Locale.US, "    %-6s  %-11s  %14s   %.4f (ε = %.4f)%n", names[col - 1], epsText,
                    Double.isNaN(tCol) ? "не досягнуто" : String.format(Locale.US, "%.0f", tCol), lastRange, lastEps);
            if (Double.isNaN(tCol)) tCol = TRANSIENT_T_MAX;
            tTransient = Math.max(tTransient, tCol);
        }
        double tMod = Math.ceil(tTransient / T_ROUND) * T_ROUND;
        System.out.printf(Locale.US, "    T = %.0f год → тривалість експерименту T_mod = %.0f год, T_w = %.0f год%n", tTransient, tMod, WARM_UP);
        System.out.println("    Динаміку записано у " + file.toAbsolutePath());
        System.out.println();
        return tMod;
    }

    static int runsNumber(ModelConfig cfg, Path file) throws Exception {
        System.out.printf(Locale.US, "4.4 Кількість прогонів: C = %s, T_mod = %.0f год, T_w = %.0f год, %d пробних прогонів%n",
                machinesOf(cfg), cfg.timeModeling(), cfg.warmUp(), PILOT_RUNS);
        QueueCapacityStudy.Series s = QueueCapacityStudy.runWithoutRefusals(cfg, PILOT_RUNS);

        String[] names = {"W", "T", "U1", "U2", "U3", "U4", "U5"};
        double[][] y = new double[names.length][PILOT_RUNS];
        StringBuilder csv = new StringBuilder("run;W;T;U1;U2;U3;U4;U5\n");
        for (int r = 0; r < PILOT_RUNS; r++) {
            SimulationResult res = s.results().get(r);
            y[0][r] = res.avgWait;
            y[1][r] = res.avgTotalTime;
            for (int m = 1; m <= 5; m++) y[1 + m][r] = res.utilization[m];
            csv.append(r + 1);
            for (double[] col : y) csv.append(';').append(num(col[r]));
            csv.append('\n');
        }
        Files.writeString(file, csv.toString(), StandardCharsets.UTF_8);

        int pMax = 0;
        System.out.println("    Відгук   середнє      σ          ε          p = 20(σ/ε)²");
        for (int i = 0; i < names.length; i++) {
            double mean = 0;
            for (double v : y[i]) mean += v / PILOT_RUNS;
            double ss = 0;
            for (double v : y[i]) ss += (v - mean) * (v - mean);
            double sigma = Math.sqrt(ss / (PILOT_RUNS - 1));
            double eps = i < 2 ? RUNS_EPS_TIME : RUNS_EPS_U;
            int p = (int) Math.ceil(20 * sigma * sigma / (eps * eps));
            pMax = Math.max(pMax, p);
            System.out.printf(Locale.US, "    %-6s  %9.4f  %9.4f  %9.4f   %6d%n", names[i], mean, sigma, eps, p);
        }
        System.out.printf(Locale.US, "    Кількість прогонів в одному експерименті p = %d (N = %d)%n", pMax, s.queuePlaces());
        System.out.println("    Значення пробних прогонів записано у " + file.toAbsolutePath());
        return pMax;
    }

    static void factorialExperiment(Path runsFile, Path summaryFile) throws Exception {
        ModelConfig initial = ModelConfig.defaults();
        int total = LEVELS_C1.length * LEVELS_C2.length * LEVELS_C4.length * LEVELS_C5.length;
        System.out.printf(Locale.US, "Факторний експеримент: %d прогонів, T_mod = %.0f год, T_w = %.0f год%n",
                 EXPERIMENT_RUNS, EXPERIMENT_T_MOD, WARM_UP);
        System.out.println("   №  конфігурація   додано    N     W, год   T, год     U1     U2     U3     U4     U5   час, с");

        StringBuilder runs = new StringBuilder("config;C1;C2;C3;C4;C5;run;N;W;T");
        for (int m = 1; m <= 5; m++) runs.append(";U").append(m);
        for (int m = 1; m <= 5; m++) runs.append(";W_ws").append(m);
        for (int k = 1; k <= 3; k++) runs.append(";W_type").append(k);
        runs.append(";refusals\n");

        StringBuilder summary = new StringBuilder("config;C1;C2;C3;C4;C5;added;N");
        for (int m = 1; m <= 5; m++) summary.append(";rho").append(m);
        summary.append(";W;T");
        for (int m = 1; m <= 5; m++) summary.append(";U").append(m);
        for (int m = 1; m <= 5; m++) summary.append(";W_ws").append(m);
        for (int k = 1; k <= 3; k++) summary.append(";W_type").append(k);
        summary.append('\n');

        int no = 0;
        for (int c1 : LEVELS_C1)
            for (int c2 : LEVELS_C2)
                for (int c4 : LEVELS_C4)
                    for (int c5 : LEVELS_C5) {
                        no++;
                        ModelConfig cfg = initial.withMachines(1, c1).withMachines(2, c2).withMachines(4, c4)
                                .withMachines(5, c5).withTime(EXPERIMENT_T_MOD, WARM_UP);
                        String name = machinesOf(cfg);
                        int added = 0;
                        for (int m = 1; m <= 5; m++) added += cfg.machines(m) - initial.machines(m);

                        long t0 = System.nanoTime();
                        QueueCapacityStudy.Series s = QueueCapacityStudy.runWithoutRefusals(cfg, EXPERIMENT_RUNS);
                        double sec = (System.nanoTime() - t0) / 1e9;

                        double w = 0, t = 0;
                        double[] u = new double[6], wws = new double[6], wk = new double[4];
                        int run = 0;
                        for (SimulationResult r : s.results()) {
                            run++;
                            w += r.avgWait / EXPERIMENT_RUNS;
                            t += r.avgTotalTime / EXPERIMENT_RUNS;
                            for (int m = 1; m <= 5; m++) { u[m] += r.utilization[m] / EXPERIMENT_RUNS; wws[m] += r.avgWaitWs[m] / EXPERIMENT_RUNS; }
                            for (int k = 1; k <= 3; k++) wk[k] += r.avgWaitByType[k] / EXPERIMENT_RUNS;

                            runs.append(name).append(';').append(machinesCsv(cfg)).append(';').append(run)
                                    .append(';').append(s.queuePlaces()).append(';').append(num(r.avgWait)).append(';').append(num(r.avgTotalTime));
                            for (int m = 1; m <= 5; m++) runs.append(';').append(num(r.utilization[m]));
                            for (int m = 1; m <= 5; m++) runs.append(';').append(num(r.avgWaitWs[m]));
                            for (int k = 1; k <= 3; k++) runs.append(';').append(num(r.avgWaitByType[k]));
                            runs.append(';').append(r.refusals).append('\n');
                        }

                        summary.append(name).append(';').append(machinesCsv(cfg)).append(';').append(added).append(';').append(s.queuePlaces());
                        for (int m = 1; m <= 5; m++) summary.append(';').append(num(cfg.theoreticalLoad(m)));
                        summary.append(';').append(num(w)).append(';').append(num(t));
                        for (int m = 1; m <= 5; m++) summary.append(';').append(num(u[m]));
                        for (int m = 1; m <= 5; m++) summary.append(';').append(num(wws[m]));
                        for (int k = 1; k <= 3; k++) summary.append(';').append(num(wk[k]));
                        summary.append('\n');

                        System.out.printf(Locale.US, "  %2d  %-13s  %4d   %4d   %7.3f  %7.3f   %.3f  %.3f  %.3f  %.3f  %.3f  %6.0f%n",
                                no, name, added, s.queuePlaces(), w, t, u[1], u[2], u[3], u[4], u[5], sec);

                        Files.writeString(runsFile, runs.toString(), StandardCharsets.UTF_8);
                        Files.writeString(summaryFile, summary.toString(), StandardCharsets.UTF_8);
                    }
        System.out.println("   Значення прогонів записано у " + runsFile.toAbsolutePath());
        System.out.println("   Оцінки відгуків записано у " + summaryFile.toAbsolutePath());
    }

    static String machinesCsv(ModelConfig cfg) {
        StringBuilder sb = new StringBuilder();
        for (int m = 1; m <= 5; m++) sb.append(cfg.machines(m)).append(m < 5 ? ";" : "");
        return sb.toString();
    }

    static String machinesOf(ModelConfig cfg) {
        StringBuilder sb = new StringBuilder("(");
        for (int m = 1; m <= 5; m++) sb.append(cfg.machines(m)).append(m < 5 ? "," : ")");
        return sb.toString();
    }

    static void baseAnalysis(double[] tMods, int runs, Path file) throws Exception {
        ModelConfig base = ModelConfig.defaults();

        System.out.println("Аналіз початкової конфігурації");
        System.out.print("    Теоретичне завантаження ρ_m:");
        for (int m = 1; m <= ModelConfig.WORKSTATIONS; m++) System.out.printf(Locale.US, "  ρ%d = %.3f", m, base.theoreticalLoad(m));
        System.out.println();
        System.out.println("    T_mod   прогін     W      T_sys     U1     U2     U3     U4     U5        N   відмов");

        StringBuilder csv = new StringBuilder(header()).append('\n');
        for (int i = 0; i < tMods.length; i++) {
            long t0 = System.nanoTime();
            QueueCapacityStudy.Series series = QueueCapacityStudy.runWithoutRefusals(base.withTime(tMods[i], 0), runs);
            double sec = (System.nanoTime() - t0) / 1e9;
            double sumW = 0, sumT = 0;
            double[] sumU = new double[6];

            for (int run = 1; run <= runs; run++) {
                SimulationResult r = series.results().get(run - 1);
                csv.append(row(r, run)).append('\n');
                sumW += r.avgWait;
                sumT += r.avgTotalTime;
                for (int m = 1; m <= 5; m++) sumU[m] += r.utilization[m];

                System.out.printf(Locale.US, "  %7.0f   %3d   %7.3f  %7.3f   %.3f  %.3f  %.3f  %.3f  %.3f   %6d   %5d%n",
                        tMods[i], run, r.avgWait, r.avgTotalTime, r.utilization[1], r.utilization[2],
                        r.utilization[3], r.utilization[4], r.utilization[5], r.cfg.queuePlaces(), r.refusals);
            }
            System.out.printf(Locale.US, "  середнє      %7.3f  %7.3f   %.3f  %.3f  %.3f  %.3f  %.3f%n",
                    sumW / runs, sumT / runs, sumU[1] / runs, sumU[2] / runs, sumU[3] / runs, sumU[4] / runs, sumU[5] / runs);
            System.out.printf(Locale.US, "  N = %d (спроб: %d), час серії %.0f с%n%n", series.queuePlaces(), series.attempts(), sec);
        }
        Files.writeString(file, csv.toString(), StandardCharsets.UTF_8);
        System.out.println("    Таблицю записано у " + file.toAbsolutePath());
    }

    static String header() {
        StringBuilder sb = new StringBuilder("T_mod;run;N;global_avg_wait;global_avg_total_time");
        for (int m = 1; m <= 5; m++) sb.append(";utilization_wm_").append(m);
        for (int m = 1; m <= 5; m++) sb.append(";avg_wait_wm_").append(m);
        for (int m = 1; m <= 5; m++) sb.append(";avg_queue_length_wm_").append(m);
        for (int m = 1; m <= 5; m++) sb.append(";max_queue_length_wm_").append(m);
        for (int k = 1; k <= 3; k++) sb.append(";avg_wait_by_type_").append(k);
        for (int k = 1; k <= 3; k++) sb.append(";avg_total_time_by_type_").append(k);
        for (int k = 1; k <= 3; k++) sb.append(";completed_count_by_type_").append(k);
        sb.append(";avg_in_system;max_in_system;refusals");
        return sb.toString();
    }

    static String row(SimulationResult r, int run) {
        StringBuilder sb = new StringBuilder();
        sb.append(num(r.cfg.timeModeling())).append(';').append(run).append(';').append(r.cfg.queuePlaces());
        sb.append(';').append(num(r.avgWait)).append(';').append(num(r.avgTotalTime));
        for (int m = 1; m <= 5; m++) sb.append(';').append(num(r.utilization[m]));
        for (int m = 1; m <= 5; m++) sb.append(';').append(num(r.avgWaitWs[m]));
        for (int m = 1; m <= 5; m++) sb.append(';').append(num(r.avgQueue[m]));
        for (int m = 1; m <= 5; m++) sb.append(';').append(r.maxQueue[m]);
        for (int k = 1; k <= 3; k++) sb.append(';').append(num(r.avgWaitByType[k]));
        for (int k = 1; k <= 3; k++) sb.append(';').append(num(r.avgTotalTimeByType[k]));
        for (int k = 1; k <= 3; k++) sb.append(';').append(r.completed[k]);
        sb.append(';').append(num(r.avgInSystem)).append(';').append(r.maxInSystem);
        sb.append(';').append(r.refusals);
        return sb.toString();
    }

    static String num(double v) {
        return String.format(Locale.US, "%.5f", v).replace('.', ',');
    }
}