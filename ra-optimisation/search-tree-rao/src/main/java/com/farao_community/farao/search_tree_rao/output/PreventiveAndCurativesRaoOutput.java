/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.search_tree_rao.output;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.crac_api.network_action.NetworkAction;
import com.farao_community.farao.data.crac_api.range_action.PstRangeAction;
import com.farao_community.farao.data.crac_api.range_action.RangeAction;
import com.farao_community.farao.data.rao_result_api.ComputationStatus;
import com.farao_community.farao.data.rao_result_api.OptimizationState;
import com.farao_community.farao.rao_commons.result_api.OptimizationResult;
import com.farao_community.farao.rao_commons.result_api.PrePerimeterResult;
import com.farao_community.farao.search_tree_rao.PerimeterOutput;

import java.util.*;
import java.util.stream.Collectors;

import static com.farao_community.farao.data.rao_result_api.ComputationStatus.FAILURE;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class PreventiveAndCurativesRaoOutput implements SearchTreeRaoResult {
    private PrePerimeterResult initialResult;
    private PerimeterResult postPreventiveResult;
    private Map<State, PerimeterResult> postCurativeResults;

    public PreventiveAndCurativesRaoOutput(PrePerimeterResult initialResult, PerimeterResult postPreventiveResult, PrePerimeterResult preCurativeResult, Map<State, OptimizationResult> postCurativeResults) {
        this.initialResult = initialResult;
        this.postPreventiveResult = postPreventiveResult;
        this.postCurativeResults = postCurativeResults.entrySet().stream().collect(
                Collectors.toMap(Map.Entry::getKey, entry -> new PerimeterOutput(preCurativeResult, entry.getValue())));
    }

    @Override
    public ComputationStatus getComputationStatus() {
        if (initialResult.getSensitivityStatus() == FAILURE
                || postPreventiveResult.getSensitivityStatus() == FAILURE
                || postCurativeResults.values().stream().anyMatch(perimeterResult -> perimeterResult.getSensitivityStatus() == FAILURE)) {
            return FAILURE;
        }
        // TODO: specify the behavior in case some perimeter are FALLBACK and other ones DEFAULT
        return ComputationStatus.DEFAULT;
    }

    public PerimeterResult getPerimeterResult(OptimizationState optimizationState, State state) {

        if (optimizationState == OptimizationState.INITIAL) {
            if (state.getInstant() == Instant.PREVENTIVE) {
                return null;
            } else {
                return postPreventiveResult;
            }
        }
        if (optimizationState == OptimizationState.AFTER_PRA) {
            return postPreventiveResult;
        }
        if (optimizationState == OptimizationState.AFTER_CRA) {
            if (state.getInstant() == Instant.PREVENTIVE) {
                throw new FaraoException("Trying to access preventive results after cra is forbidden. Either get the preventive results after PRA, or the curative results after CRA.");
            } else {
                return postCurativeResults.get(state);
            }
        }
        throw new FaraoException("OptimizationState was not recognized");
    }

    @Override
    public PerimeterResult getPostPreventivePerimeterResult() {
        return postPreventiveResult;
    }

    @Override
    public PrePerimeterResult getInitialResult() {
        return initialResult;
    }

    @Override
    public double getFunctionalCost(OptimizationState optimizationState) {
        if (optimizationState == OptimizationState.INITIAL) {
            return initialResult.getFunctionalCost();
        }
        if (optimizationState == OptimizationState.AFTER_PRA) {
            return postPreventiveResult.getFunctionalCost();
        }
        double highestFunctionalCost = postPreventiveResult.getFunctionalCost();
        highestFunctionalCost = Math.max(
                highestFunctionalCost,
                postCurativeResults.values().stream()
                        .map(PerimeterResult::getFunctionalCost)
                        .max(Double::compareTo)
                        .orElseThrow(() -> new FaraoException("Should not happen"))
        );
        return highestFunctionalCost;
    }

    @Override
    public List<FlowCnec> getMostLimitingElements(OptimizationState optimizationState, int number) {
        //TODO : store values to be able to merge easily
        return null;
    }

    @Override
    public double getVirtualCost(OptimizationState optimizationState) {
        if (optimizationState == OptimizationState.INITIAL) {
            return initialResult.getVirtualCost();
        }
        if (optimizationState == OptimizationState.AFTER_PRA) {
            return postPreventiveResult.getVirtualCost();
        }
        double virtualCostSum = 0;
        for (PerimeterResult postCurativeResult : postCurativeResults.values()) {
            virtualCostSum += postCurativeResult.getVirtualCost();
        }
        return virtualCostSum;
    }

    @Override
    public Set<String> getVirtualCostNames() {
        Set<String> virtualCostNames = new HashSet<>();
        if (initialResult.getVirtualCostNames() != null) {
            virtualCostNames.addAll(initialResult.getVirtualCostNames());
        }
        if (postPreventiveResult.getVirtualCostNames() != null) {
            virtualCostNames.addAll(postPreventiveResult.getVirtualCostNames());
        }
        postCurativeResults.values().stream()
            .filter(perimeterResult -> perimeterResult.getVirtualCostNames() != null)
            .forEach(perimeterResult -> virtualCostNames.addAll(perimeterResult.getVirtualCostNames()));

        return virtualCostNames;
    }

    @Override
    public double getVirtualCost(OptimizationState optimizationState, String virtualCostName) {
        if (optimizationState == OptimizationState.INITIAL) {
            return initialResult.getVirtualCost(virtualCostName);
        }
        if (optimizationState == OptimizationState.AFTER_PRA) {
            return postPreventiveResult.getVirtualCost(virtualCostName);
        }
        double virtualCostSum = 0;
        for (PerimeterResult postCurativeResult : postCurativeResults.values()) {
            virtualCostSum += postCurativeResult.getVirtualCost(virtualCostName);
        }
        return virtualCostSum;
    }

    @Override
    public List<FlowCnec> getCostlyElements(OptimizationState optimizationState, String virtualCostName, int number) {
        //TODO : store values to be able to merge easily
        return null;
    }

    @Override
    public boolean wasActivatedBeforeState(State state, NetworkAction networkAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return false;
        } else {
            return postPreventiveResult.getActivatedNetworkActions().contains(networkAction);
        }
    }

    @Override
    public boolean isActivatedDuringState(State state, NetworkAction networkAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return postPreventiveResult.getActivatedNetworkActions().contains(networkAction);
        } else if (postCurativeResults.containsKey(state)) {
            return postCurativeResults.get(state).getActivatedNetworkActions().contains(networkAction);
        } else {
            return false;
        }
    }

    @Override
    public Set<NetworkAction> getActivatedNetworkActionsDuringState(State state) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return postPreventiveResult.getActivatedNetworkActions();
        } else if (postCurativeResults.containsKey(state)) {
            return postCurativeResults.get(state).getActivatedNetworkActions();
        } else {
            return new HashSet<>();
        }
    }

    @Override
    public boolean isActivatedDuringState(State state, RangeAction rangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return postPreventiveResult.getActivatedRangeActions().contains(rangeAction);
        } else if (postCurativeResults.containsKey(state)) {
            return postCurativeResults.get(state).getActivatedRangeActions().contains(rangeAction);
        } else {
            return false;
        }
    }

    @Override
    public int getPreOptimizationTapOnState(State state, PstRangeAction pstRangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return initialResult.getOptimizedTap(pstRangeAction);
        } else if (postCurativeResults.containsKey(state)) {
            return postPreventiveResult.getOptimizedTap(pstRangeAction);
        } else {
            throw new FaraoException(String.format("State %s was not optimized and does not have pre-optim values", state.getId()));
        }
    }

    @Override
    public int getOptimizedTapOnState(State state, PstRangeAction pstRangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE || !postCurativeResults.containsKey(state)) {
            return postPreventiveResult.getOptimizedTap(pstRangeAction);
        } else {
            return postCurativeResults.get(state).getOptimizedTap(pstRangeAction);
        }
    }

    @Override
    public double getPreOptimizationSetPointOnState(State state, RangeAction rangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return initialResult.getOptimizedSetPoint(rangeAction);
        } else if (postCurativeResults.containsKey(state)) {
            return postPreventiveResult.getOptimizedSetPoint(rangeAction);
        } else {
            throw new FaraoException(String.format("State %s was not optimized and does not have pre-optim values", state.getId()));
        }
    }

    @Override
    public double getOptimizedSetPointOnState(State state, RangeAction rangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE || !postCurativeResults.containsKey(state)) {
            return postPreventiveResult.getOptimizedSetPoint(rangeAction);
        } else {
            return postCurativeResults.get(state).getOptimizedSetPoint(rangeAction);
        }
    }

    @Override
    public Set<RangeAction> getActivatedRangeActionsDuringState(State state) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return postPreventiveResult.getActivatedRangeActions();
        } else if (postCurativeResults.containsKey(state)) {
            return postCurativeResults.get(state).getActivatedRangeActions();
        } else {
            return new HashSet<>();
        }
    }

    @Override
    public Map<PstRangeAction, Integer> getOptimizedTapsOnState(State state) {
        if (state.getInstant() == Instant.PREVENTIVE || !postCurativeResults.containsKey(state)) {
            return postPreventiveResult.getOptimizedTaps();
        } else {
            return postCurativeResults.get(state).getOptimizedTaps();
        }
    }

    @Override
    public Map<RangeAction, Double> getOptimizedSetPointsOnState(State state) {
        if (state.getInstant() == Instant.PREVENTIVE || !postCurativeResults.containsKey(state)) {
            return postPreventiveResult.getOptimizedSetPoints();
        } else {
            return postCurativeResults.get(state).getOptimizedSetPoints();
        }
    }
}
