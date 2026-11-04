package production.sim;

import production.config.ModelConfig;
import production.net.NetIndex;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriT;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class FactorySimulator {
    private record Event(double time, long seq, PetriT transition) { }

    private final ModelConfig cfg;
    private final NetIndex idx;
    private final PetriP[] places;
    private final PetriT[] transitions;
    private final int[][] consumers;

    private final PriorityQueue<Event> events = new PriorityQueue<>(
            (a, b) -> a.time != b.time ? Double.compare(a.time, b.time) : Long.compare(a.seq, b.seq));
    private long seq = 0;

    private final List<PetriT> candidates = new ArrayList<>();
    private final boolean[] isCandidate;

    private final Random random;
    private SimulationListener listener = new SimulationListener() { };

    private PrintStream protocol = null;
    private double protocolUntil = 0;
    private boolean checkInvariants = false;

    private double time = 0;
    private long firings = 0;

    public FactorySimulator(ModelConfig cfg, NetIndex idx) {
        this(cfg, idx, new Random());
    }

    public FactorySimulator(ModelConfig cfg, NetIndex idx, Random random) {
        this.cfg = cfg;
        this.idx = idx;
        this.places = idx.net.getListP();
        this.transitions = idx.net.getListT();
        this.random = random;
        this.isCandidate = new boolean[transitions.length];
        this.consumers = buildConsumers();
    }

    private int[][] buildConsumers() {
        List<List<Integer>> lists = new ArrayList<>();
        for (int p = 0; p < places.length; p++) lists.add(new ArrayList<>());
        for (PetriT t : transitions) {
            for (int p : t.getInP()) lists.get(p).add(t.getNumber());
        }
        int[][] result = new int[places.length][];
        for (int p = 0; p < places.length; p++) {
            result[p] = lists.get(p).stream().mapToInt(Integer::intValue).toArray();
        }
        return result;
    }

    public void setListener(SimulationListener listener) { this.listener = listener; }

    public void setProtocol(PrintStream out, double until) {
        this.protocol = out;
        this.protocolUntil = until;
    }

    public void setCheckInvariants(boolean check) { this.checkInvariants = check; }

    public long getFirings() { return firings; }

    public void run() {
        double tMod = cfg.timeModeling();
        time = 0;
        listener.onStart(idx);
        if (protocol != null) protocol.printf("--- t = %.4f ---%n", time);

        for (PetriT t : transitions) addCandidate(t);
        inputPhase();

        while (true) {
            double next = events.isEmpty() ? Double.POSITIVE_INFINITY : events.peek().time();
            if (next > tMod) {
                listener.onTimeAdvance(time, tMod);
                time = tMod;
                break;
            }
            if (next > time) {
                if (checkInvariants) checkInvariants();
                listener.onTimeAdvance(time, next);
                time = next;
                if (protocol != null && time <= protocolUntil) {
                    protocol.printf("%n--- t = %.4f ---%n", time);
                }
            }
            outputPhase();
            inputPhase();
        }
        listener.onFinish(time);
    }

    private void outputPhase() {
        List<PetriT> due = new ArrayList<>();
        while (!events.isEmpty() && events.peek().time() == time) {
            due.add(events.poll().transition());
        }
        Collections.shuffle(due, random);
        for (PetriT t : due) {
            t.minEvent();
            t.actOut(places, time);
            firings++;
            listener.onOutput(t, time);
            if (protocol != null && time <= protocolUntil) printOutput(t);
            for (int p : t.getOutP()) {
                for (int c : consumers[p]) addCandidate(transitions[c]);
            }
        }
    }

    private void inputPhase() {
        while (true) {
            List<PetriT> enabled = collectEnabled();
            if (enabled.isEmpty()) return;
            PetriT t = resolveConflict(enabled);
            fireInput(t);
        }
    }

    private List<PetriT> collectEnabled() {
        List<PetriT> enabled = new ArrayList<>();
        List<PetriT> keep = new ArrayList<>();
        for (PetriT t : candidates) {
            if (t.getProbability() > 0 && t.condition(places)) {
                enabled.add(t);
                keep.add(t);
            } else {
                isCandidate[t.getNumber()] = false;
            }
        }
        candidates.clear();
        candidates.addAll(keep);
        return enabled;
    }

    private PetriT resolveConflict(List<PetriT> enabled) {
        if (enabled.size() == 1) return enabled.get(0);
        int maxPriority = Integer.MIN_VALUE;
        for (PetriT t : enabled) maxPriority = Math.max(maxPriority, t.getPriority());
        List<PetriT> group = new ArrayList<>();
        double sum = 0;
        for (PetriT t : enabled) {
            if (t.getPriority() == maxPriority) {
                group.add(t);
                sum += t.getProbability();
            }
        }
        if (group.size() == 1) return group.get(0);
        double r = random.nextDouble() * sum;
        for (PetriT t : group) {
            r -= t.getProbability();
            if (r < 0) return t;
        }
        return group.get(group.size() - 1);
    }

    private void fireInput(PetriT t) {
        int bufferBefore = t.getBuffer();
        t.actIn(places, time);
        List<Double> out = t.getTimeOut();
        double exitTime = (bufferBefore == 0) ? out.get(0) : out.get(out.size() - 1);
        events.add(new Event(exitTime, seq++, t));
        firings++;
        listener.onInput(t, time);
        if (protocol != null && time <= protocolUntil) printInput(t, exitTime);
    }

    private void addCandidate(PetriT t) {
        if (!isCandidate[t.getNumber()]) {
            isCandidate[t.getNumber()] = true;
            candidates.add(t);
        }
    }

    private void printInput(PetriT t, double exitTime) {
        protocol.printf("  ВХІД   %-16s вихід заплановано на %.4f; %s%n",
                t.getName(), exitTime, marks(t.getInP()));
    }

    private void printOutput(PetriT t) {
        protocol.printf("  ВИХІД  %-16s %s%n", t.getName(), marks(t.getOutP()));
    }

    private String marks(List<Integer> placeNumbers) {
        StringBuilder sb = new StringBuilder();
        for (int p : placeNumbers) {
            sb.append(places[p].getName()).append('=').append(places[p].getMark()).append(' ');
        }
        return sb.toString().trim();
    }

    private void checkInvariants() {
        int N = idx.queuePlaces;
        for (int m = 1; m <= ModelConfig.WORKSTATIONS; m++) {
            int busy = 0;
            for (int k = 1; k <= ModelConfig.TYPES; k++) {
                if (idx.tComplete[k][m] != null) busy += idx.tComplete[k][m].getBuffer();
            }
            if (idx.pRes[m].getMark() + busy != cfg.machines(m)) {
                throw new IllegalStateException("Порушено інваріант верстатів РМ" + m + " у момент " + time);
            }
            for (int n = 1; n <= N; n++) {
                int occupied = idx.pFree[m][n].getMark();
                for (int k = 1; k <= ModelConfig.TYPES; k++) {
                    if (idx.pQueue[k][m] != null) occupied += idx.pQueue[k][m][n].getMark();
                }
                if (occupied != 1) {
                    throw new IllegalStateException("Порушено інваріант місця " + n + " черги РМ" + m + " у момент " + time);
                }
            }
        }
    }
}