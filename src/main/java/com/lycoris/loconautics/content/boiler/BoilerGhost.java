package com.lycoris.loconautics.content.boiler;

import com.lycoris.loconautics.trainphysics.SableTrain;

// server only, the unloaded step of a boiler, called from the ghost consist step with the leading car
public final class BoilerGhost {

    private BoilerGhost() {
    }

    public static void step(SableTrain leader) {
        BoilerSummary summary = leader.boilerSummary();
        // no schedule, no message, the step only runs for a train a schedule is driving
        if (summary == null || !leader.isScheduleDriven()) return;
        summary.spendTick();
        String problem = summary.problem();
        if (problem == null) return;
        leader.setTargetSpeed(0.0);
        if (summary.exhausted()) return;
        summary.markExhausted();
        BoilerOwnerMessages.tell(leader, problem);
    }
}
