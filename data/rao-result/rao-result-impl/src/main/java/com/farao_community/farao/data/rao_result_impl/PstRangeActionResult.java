/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.data.rao_result_impl;

import com.farao_community.farao.data.crac_api.Instant;
import com.farao_community.farao.data.crac_api.State;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public class PstRangeActionResult {

    private String pstNetworkElementId;
    private Integer preOptimTap;
    private double preOptimSetpoint;
    private Map<State, PstRangeActionResultPerState> activationPerState;

    private static class PstRangeActionResultPerState {
        private Integer tap = null;
        private double setpoint = Double.NaN;
    }

    public PstRangeActionResult(String pstNetworkElementId) {
        this.pstNetworkElementId = pstNetworkElementId;
        preOptimTap = null;
        preOptimSetpoint = Double.NaN;
        activationPerState = new HashMap<>();
    }

    public String getPstNetworkElementId() {
        return pstNetworkElementId;
    }

    public int getPreOptimTap() {
        return preOptimTap;
    }

    public double getPreOptimSetpoint() {
        return preOptimSetpoint;
    }

    public int getOptimizedTapOnState(State state) {

        // does not handle RA applicable on OUTAGE instant
        // does only handle RA applicable in PREVENTIVE and CURATIVE instant

        if (activationPerState.containsKey(state)) {
            return activationPerState.get(state).tap;
        } else if (!state.getInstant().equals(Instant.PREVENTIVE)) {
            Optional<PstRangeActionResultPerState> resultForPreventiveState = findActivationForPreventiveState();

            if (resultForPreventiveState.isPresent()) {
                return resultForPreventiveState.get().tap;
            }
        }

        return preOptimTap;
    }

    public double getOptimizedSetpointOnState(State state) {

        // does not handle RA applicable on OUTAGE instant
        // does only handle RA applicable in PREVENTIVE and CURATIVE instant

        if (activationPerState.containsKey(state)) {
            return activationPerState.get(state).setpoint;
        } else if (!state.getInstant().equals(Instant.PREVENTIVE)) {
            Optional<PstRangeActionResultPerState> resultForPreventiveState = findActivationForPreventiveState();

            if (resultForPreventiveState.isPresent()) {
                return resultForPreventiveState.get().setpoint;
            }
        }

        return preOptimSetpoint;
    }

    public int getPreOptimizedTapOnState(State state) {

        // does not handle RA applicable on OUTAGE instant
        // does only handle RA applicable in PREVENTIVE and CURATIVE instant

        if (!state.getInstant().equals(Instant.PREVENTIVE)) {
            Optional<PstRangeActionResultPerState> resultForPreventiveState = findActivationForPreventiveState();

            if (resultForPreventiveState.isPresent()) {
                return resultForPreventiveState.get().tap;
            }
        }

        return preOptimTap;
    }

    public double getPreOptimizedSetpointOnState(State state) {

        // does not handle RA applicable on OUTAGE instant
        // does only handle RA applicable in PREVENTIVE and CURATIVE instant

        if (!state.getInstant().equals(Instant.PREVENTIVE)) {
            Optional<PstRangeActionResultPerState> resultForPreventiveState = findActivationForPreventiveState();

            if (resultForPreventiveState.isPresent()) {
                return resultForPreventiveState.get().setpoint;
            }
        }

        return preOptimSetpoint;
    }

    public boolean isActivatedDuringState(State state) {
        return activationPerState.containsKey(state);
    }

    public Set<State> getStatesWithActivation() {
        return activationPerState.keySet();
    }

    public void setPstNetworkElementId(String pstNetworkElementId) {
        this.pstNetworkElementId = pstNetworkElementId;
    }

    public void setPreOptimTap(int preOptimTap) {
        this.preOptimTap = preOptimTap;
    }

    public void setPreOptimSetPoint(double setpoint) {
        this.preOptimSetpoint = setpoint;
    }

    public void addActivationForState(State state, int tap, double setpoint) {
        PstRangeActionResultPerState pstRangeActionResultPerState = new PstRangeActionResultPerState();
        pstRangeActionResultPerState.tap = tap;
        pstRangeActionResultPerState.setpoint = setpoint;
        activationPerState.put(state, pstRangeActionResultPerState);
    }

    private Optional<PstRangeActionResultPerState> findActivationForPreventiveState() {
        return activationPerState.entrySet().stream()
            .filter(e -> e.getKey().isPreventive())
            .map(Map.Entry::getValue)
            .findAny();
    }
}
