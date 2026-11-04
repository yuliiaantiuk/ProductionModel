package production.sim;

import production.net.NetIndex;
import ua.stetsenkoinna.petriobj.PetriT;

public interface SimulationListener {
    default void onStart(NetIndex idx) { }

    default void onTimeAdvance(double from, double to) { }

    default void onInput(PetriT t, double time) { }

    default void onOutput(PetriT t, double time) { }

    default void onFinish(double time) { }
}