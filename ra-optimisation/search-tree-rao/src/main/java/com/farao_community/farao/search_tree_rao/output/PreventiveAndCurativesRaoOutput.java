/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.search_tree_rao.output;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.crac_api.network_action.NetworkAction;
import com.farao_community.farao.data.crac_api.range_action.PstRangeAction;
import com.farao_community.farao.data.crac_api.range_action.RangeAction;
import com.farao_community.farao.rao_api.results.*;
import com.farao_community.farao.search_tree_rao.PerimeterOutput;
import com.powsybl.commons.extensions.Extension;

import java.util.*;
import java.util.stream.Collectors;

import static com.farao_community.farao.rao_api.results.SensitivityStatus.FAILURE;

public class PreventiveAndCurativesRaoOutput implements RaoResult {
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
    public SensitivityStatus getComputationStatus() {
        if (initialResult.getSensitivityStatus() == FAILURE
                || postPreventiveResult.getSensitivityStatus() == FAILURE
                || postCurativeResults.values().stream().anyMatch(perimeterResult -> perimeterResult.getSensitivityStatus() == FAILURE)) {
            return FAILURE;
        }
        // TODO: specify the behavior in case some perimeter are FALLBACK and other ones DEFAULT
        return SensitivityStatus.DEFAULT;
    }

    @Override
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
        // temporary workaround
        Map<FlowCnec, Double> worstMargins = new HashMap<>();
        postPreventiveResult.getMostLimitingElements(number).forEach(cnec -> worstMargins.put(cnec, postPreventiveResult.getMargin(cnec, Unit.AMPERE)));
        postCurativeResults.forEach((state, perimeterResult) ->
                perimeterResult.getMostLimitingElements(number).forEach(cnec -> worstMargins.put(cnec, perimeterResult.getMargin(cnec, Unit.AMPERE))));
        return worstMargins.keySet().stream().sorted(Comparator.comparingDouble(worstMargins::get))
                .collect(Collectors.toList()).subList(0, Math.min(number, worstMargins.size()));
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
        return initialResult.getVirtualCostNames();
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
        } else {
            return postCurativeResults.get(state).getActivatedNetworkActions().contains(networkAction);
        }
    }

    @Override
    public Set<NetworkAction> getActivatedNetworkActionsDuringState(State state) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return postPreventiveResult.getActivatedNetworkActions();
        } else {
            return postCurativeResults.get(state).getActivatedNetworkActions();
        }
    }

    @Override
    public boolean isActivatedDuringState(State state, RangeAction rangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return postPreventiveResult.getActivatedRangeActions().contains(rangeAction);
        } else {
            return postCurativeResults.get(state).getActivatedRangeActions().contains(rangeAction);
        }
    }

    @Override
    public int getPreOptimizationTapOnState(State state, PstRangeAction pstRangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return initialResult.getOptimizedTap(pstRangeAction);
        } else {
            return postPreventiveResult.getOptimizedTap(pstRangeAction);
        }
    }

    @Override
    public int getOptimizedTapOnState(State state, PstRangeAction pstRangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return postPreventiveResult.getOptimizedTap(pstRangeAction);
        } else {
            return postCurativeResults.get(state).getOptimizedTap(pstRangeAction);
        }
    }

    @Override
    public double getPreOptimizationSetPointOnState(State state, RangeAction rangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return initialResult.getOptimizedSetPoint(rangeAction);
        } else {
            return postPreventiveResult.getOptimizedSetPoint(rangeAction);
        }
    }

    @Override
    public double getOptimizedSetPointOnState(State state, RangeAction rangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return postPreventiveResult.getOptimizedSetPoint(rangeAction);
        } else {
            return postCurativeResults.get(state).getOptimizedSetPoint(rangeAction);
        }
    }

    @Override
    public Set<RangeAction> getActivatedRangeActionsDuringState(State state) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return postPreventiveResult.getActivatedRangeActions();
        } else {
            return postCurativeResults.get(state).getActivatedRangeActions();
        }
    }

    @Override
    public Map<PstRangeAction, Integer> getOptimizedTapsOnState(State state) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return postPreventiveResult.getOptimizedTaps();
        } else {
            return postCurativeResults.get(state).getOptimizedTaps();
        }
    }

    @Override
    public Map<RangeAction, Double> getOptimizedSetPointsOnState(State state) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return postPreventiveResult.getOptimizedSetPoints();
        } else {
            return postCurativeResults.get(state).getOptimizedSetPoints();
        }
    }

    @Override
    public void addExtension(Class aClass, Extension extension) {

    }

    @Override
    public Extension getExtension(Class aClass) {
        return null;
    }

    @Override
    public Extension getExtensionByName(String s) {
        return null;
    }

    @Override
    public boolean removeExtension(Class aClass) {
        return false;
    }

    @Override
    public Collection getExtensions() {
        return null;
    }
}
