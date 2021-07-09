/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.search_tree_rao.output;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.Instant;
import com.farao_community.farao.data.crac_api.RemedialAction;
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.crac_api.network_action.NetworkAction;
import com.farao_community.farao.data.crac_api.range_action.PstRangeAction;
import com.farao_community.farao.data.crac_api.range_action.RangeAction;
import com.farao_community.farao.data.rao_result_api.ComputationStatus;
import com.farao_community.farao.data.rao_result_api.OptimizationState;
import com.farao_community.farao.rao_commons.result_api.*;
import org.apache.commons.lang3.NotImplementedException;

import java.util.*;
import java.util.stream.Collectors;

import static com.farao_community.farao.data.rao_result_api.ComputationStatus.FAILURE;

/**
 * A RaoResult implementation that uses curative RAO results and second preventive RAO results
 * It also needs a sensitivity computation result with 2nd preventive PRAs, without CRAs (pre-curative)
 * It is assumed that all CNECs are present in post-second preventive results, as well as in
 * pre-curative sensi results
 * If there are RAs that were excluded from the 2nd preventive and optimized only during the
 * 1st preventive RAO, then we need this set of excluded RAs as well as the 1st preventive
 * result, in order to correctly detect the usage of the RAs.
 *
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
public class SecondPreventiveAndCurativesRaoOutput implements SearchTreeRaoResult {
    private PrePerimeterResult initialResult;
    private PerimeterResult postFirstPreventiveResult; // used for RAs optimized only during 1st preventive (excluded from 2nd)
    private PerimeterResult postSecondPreventiveResult; // flows computed using PRA + CRA
    private PrePerimeterResult preCurativeResult; // flows computed using PRA only
    private Map<State, OptimizationResult> postCurativeResults;
    private Set<RemedialAction<?>> remedialActionsExcludedFromSecondPreventive; // RAs only in 1st preventive, not in 2nd
    private static final String UNKNOWN_OPTIM_STATE = "Unknown OptimizationState: %s";

    public SecondPreventiveAndCurativesRaoOutput(PrePerimeterResult initialResult,
                                                 PerimeterResult postFirstPreventiveResult,
                                                 PerimeterResult postSecondPreventiveResult,
                                                 PrePerimeterResult preCurativeResult,
                                                 Map<State, OptimizationResult> postCurativeResults,
                                                 Set<RemedialAction<?>> remedialActionsExcludedFromSecondPreventive) {
        this.initialResult = initialResult;
        this.postFirstPreventiveResult = postFirstPreventiveResult;
        this.postSecondPreventiveResult = postSecondPreventiveResult;
        this.preCurativeResult = preCurativeResult;
        this.remedialActionsExcludedFromSecondPreventive = remedialActionsExcludedFromSecondPreventive;
        this.postCurativeResults = postCurativeResults;
    }

    private boolean isRangeActionActivatedInCurative(State state, RangeAction rangeAction) {
        if (!postCurativeResults.containsKey(state) || !postCurativeResults.get(state).getRangeActions().contains(rangeAction)) {
            return false;
        } else if (postFirstPreventiveResult.getRangeActions().contains(rangeAction)) {
            return postCurativeResults.get(state).getOptimizedSetPoint(rangeAction) != postFirstPreventiveResult.getOptimizedSetPoint(rangeAction);
        } else {
            return postCurativeResults.get(state).getOptimizedSetPoint(rangeAction) != initialResult.getOptimizedSetPoint(rangeAction);
        }
    }

    @Override
    public ComputationStatus getComputationStatus() {
        if (initialResult.getSensitivityStatus() == FAILURE
                || postSecondPreventiveResult.getSensitivityStatus() == FAILURE
                || postCurativeResults.values().stream().anyMatch(perimeterResult -> perimeterResult.getSensitivityStatus() == FAILURE)) {
            return FAILURE;
        }
        // TODO: specify the behavior in case some perimeter are FALLBACK and other ones DEFAULT
        return ComputationStatus.DEFAULT;
    }

    @Override
    public PerimeterResult getPerimeterResult(OptimizationState optimizationState, State state) {
        throw new NotImplementedException("getPerimeterResult is not implemented in SecondPreventiveAndCurativesRaoOutput");
    }

    @Override
    public PerimeterResult getPostPreventivePerimeterResult() {
        throw new NotImplementedException("getPostPreventivePerimeterResult is not implemented in SecondPreventiveAndCurativesRaoOutput");
    }

    @Override
    public PrePerimeterResult getInitialResult() {
        throw new NotImplementedException("getInitialResult is not implemented in SecondPreventiveAndCurativesRaoOutput");
    }

    @Override
    public double getFunctionalCost(OptimizationState optimizationState) {
        switch (optimizationState) {
            case INITIAL:
                return initialResult.getFunctionalCost();
            case AFTER_PRA:
                return preCurativeResult.getFunctionalCost();
            case AFTER_CRA:
                return postSecondPreventiveResult.getFunctionalCost();
            default:
                throw new FaraoException(String.format(UNKNOWN_OPTIM_STATE, optimizationState));
        }
    }

    @Override
    public List<FlowCnec> getMostLimitingElements(OptimizationState optimizationState, int number) {
        switch (optimizationState) {
            case INITIAL:
                return initialResult.getMostLimitingElements(number);
            case AFTER_PRA:
                return preCurativeResult.getMostLimitingElements(number);
            case AFTER_CRA:
                return postSecondPreventiveResult.getMostLimitingElements(number);
            default:
                throw new FaraoException(String.format(UNKNOWN_OPTIM_STATE, optimizationState));
        }
    }

    @Override
    public double getVirtualCost(OptimizationState optimizationState) {
        switch (optimizationState) {
            case INITIAL:
                return initialResult.getVirtualCost();
            case AFTER_PRA:
                return preCurativeResult.getVirtualCost();
            case AFTER_CRA:
                return postSecondPreventiveResult.getVirtualCost();
            default:
                throw new FaraoException(String.format(UNKNOWN_OPTIM_STATE, optimizationState));
        }
    }

    @Override
    public Set<String> getVirtualCostNames() {
        return initialResult.getVirtualCostNames();
    }

    @Override
    public double getVirtualCost(OptimizationState optimizationState, String virtualCostName) {
        switch (optimizationState) {
            case INITIAL:
                return initialResult.getVirtualCost(virtualCostName);
            case AFTER_PRA:
                return preCurativeResult.getVirtualCost(virtualCostName);
            case AFTER_CRA:
                return postSecondPreventiveResult.getVirtualCost(virtualCostName);
            default:
                throw new FaraoException(String.format(UNKNOWN_OPTIM_STATE, optimizationState));
        }
    }

    @Override
    public List<FlowCnec> getCostlyElements(OptimizationState optimizationState, String virtualCostName, int number) {
        switch (optimizationState) {
            case INITIAL:
                return initialResult.getCostlyElements(virtualCostName, number);
            case AFTER_PRA:
                return preCurativeResult.getCostlyElements(virtualCostName, number);
            case AFTER_CRA:
                return postSecondPreventiveResult.getCostlyElements(virtualCostName, number);
            default:
                throw new FaraoException(String.format(UNKNOWN_OPTIM_STATE, optimizationState));
        }
    }

    @Override
    public boolean wasActivatedBeforeState(State state, NetworkAction networkAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return false;
        } else if (remedialActionsExcludedFromSecondPreventive.contains(networkAction)) {
            return postFirstPreventiveResult.isActivated(networkAction);
        } else {
            return postSecondPreventiveResult.isActivated(networkAction);
        }
    }

    @Override
    public boolean isActivatedDuringState(State state, NetworkAction networkAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            if (remedialActionsExcludedFromSecondPreventive.contains(networkAction)) {
                return postFirstPreventiveResult.getActivatedNetworkActions().contains(networkAction);
            } else {
                return postSecondPreventiveResult.getActivatedNetworkActions().contains(networkAction);
            }
        } else if (postCurativeResults.containsKey(state)) {
            return postCurativeResults.get(state).getActivatedNetworkActions().contains(networkAction);
        } else {
            return false;
        }
    }

    @Override
    public Set<NetworkAction> getActivatedNetworkActionsDuringState(State state) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            Set<NetworkAction> activatedNetworkActions = postFirstPreventiveResult.getActivatedNetworkActions().stream()
                    .filter(networkAction -> remedialActionsExcludedFromSecondPreventive.contains(networkAction)).collect(Collectors.toSet());
            activatedNetworkActions.addAll(postSecondPreventiveResult.getActivatedNetworkActions());
            return activatedNetworkActions;
        } else if (postCurativeResults.containsKey(state)) {
            return postCurativeResults.get(state).getActivatedNetworkActions();
        } else {
            return new HashSet<>();
        }
    }

    @Override
    public boolean isActivatedDuringState(State state, RangeAction rangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            if (remedialActionsExcludedFromSecondPreventive.contains(rangeAction)) {
                return postFirstPreventiveResult.getActivatedRangeActions().contains(rangeAction);
            } else {
                return postSecondPreventiveResult.getActivatedRangeActions().contains(rangeAction);
            }
        } else {
            return isRangeActionActivatedInCurative(state, rangeAction);
        }
    }

    @Override
    public int getPreOptimizationTapOnState(State state, PstRangeAction pstRangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return initialResult.getOptimizedTap(pstRangeAction);
        } else if (postCurativeResults.containsKey(state)) {
            return postSecondPreventiveResult.getOptimizedTap(pstRangeAction);
        } else {
            throw new FaraoException(String.format("State %s was not optimized and does not have pre-optim values", state.getId()));
        }
    }

    @Override
    public int getOptimizedTapOnState(State state, PstRangeAction pstRangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE
                || !postCurativeResults.containsKey(state)
                || !isRangeActionActivatedInCurative(state, pstRangeAction)) {
            return postSecondPreventiveResult.getOptimizedTap(pstRangeAction);
        } else {
            return postCurativeResults.get(state).getOptimizedTap(pstRangeAction);
        }
    }

    @Override
    public double getPreOptimizationSetPointOnState(State state, RangeAction rangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            return initialResult.getOptimizedSetPoint(rangeAction);
        } else if (postCurativeResults.containsKey(state)) {
            return postSecondPreventiveResult.getOptimizedSetPoint(rangeAction);
        } else {
            throw new FaraoException(String.format("State %s was not optimized and does not have pre-optim values", state.getId()));
        }
    }

    @Override
    public double getOptimizedSetPointOnState(State state, RangeAction rangeAction) {
        if (state.getInstant() == Instant.PREVENTIVE
                || !postCurativeResults.containsKey(state)
                || !isRangeActionActivatedInCurative(state, rangeAction)) {
            return postSecondPreventiveResult.getOptimizedSetPoint(rangeAction);
        } else {
            return postCurativeResults.get(state).getOptimizedSetPoint(rangeAction);
        }
    }

    @Override
    public Set<RangeAction> getActivatedRangeActionsDuringState(State state) {
        if (state.getInstant() == Instant.PREVENTIVE) {
            Set<RangeAction> activatedRangeActions = postFirstPreventiveResult.getActivatedRangeActions().stream()
                    .filter(networkAction -> remedialActionsExcludedFromSecondPreventive.contains(networkAction)).collect(Collectors.toSet());
            activatedRangeActions.addAll(postSecondPreventiveResult.getActivatedRangeActions());
            return activatedRangeActions;
        } else if (postCurativeResults.containsKey(state)) {
            return initialResult.getRangeActions().stream().filter(rangeAction -> isRangeActionActivatedInCurative(state, rangeAction)).collect(Collectors.toSet());
        } else {
            return new HashSet<>();
        }
    }

    @Override
    public Map<PstRangeAction, Integer> getOptimizedTapsOnState(State state) {
        if (state.getInstant() == Instant.PREVENTIVE || !postCurativeResults.containsKey(state)) {
            return postSecondPreventiveResult.getOptimizedTaps();
        } else {
            Map<PstRangeAction, Integer> optimizedTapsOnState = new HashMap<>();
            initialResult.getRangeActions().stream().filter(PstRangeAction.class::isInstance)
                    .forEach(rangeAction -> optimizedTapsOnState.put((PstRangeAction) rangeAction, getOptimizedTapOnState(state, (PstRangeAction) rangeAction)));
            return optimizedTapsOnState;
        }
    }

    @Override
    public Map<RangeAction, Double> getOptimizedSetPointsOnState(State state) {
        if (state.getInstant() == Instant.PREVENTIVE || !postCurativeResults.containsKey(state)) {
            return postSecondPreventiveResult.getOptimizedSetPoints();
        } else {
            Map<RangeAction, Double> optimizedSetpointsOnState = new HashMap<>();
            initialResult.getRangeActions()
                    .forEach(rangeAction -> optimizedSetpointsOnState.put(rangeAction, getOptimizedSetPointOnState(state, rangeAction)));
            return optimizedSetpointsOnState;
        }
    }

    @Override
    public double getFlow(OptimizationState optimizationState, FlowCnec flowCnec, Unit unit) {
        if (optimizationState.equals(OptimizationState.INITIAL)) {
            return initialResult.getFlow(flowCnec, unit);
        } else if (optimizationState.equals(OptimizationState.AFTER_PRA)) {
            return preCurativeResult.getFlow(flowCnec, unit);
        } else {
            return postSecondPreventiveResult.getFlow(flowCnec, unit);
        }
    }

    @Override
    public double getMargin(OptimizationState optimizationState, FlowCnec flowCnec, Unit unit) {
        if (optimizationState.equals(OptimizationState.INITIAL)) {
            return initialResult.getMargin(flowCnec, unit);
        } else if (optimizationState.equals(OptimizationState.AFTER_PRA)) {
            return preCurativeResult.getMargin(flowCnec, unit);
        } else {
            return postSecondPreventiveResult.getMargin(flowCnec, unit);
        }
    }

    @Override
    public double getRelativeMargin(OptimizationState optimizationState, FlowCnec flowCnec, Unit unit) {
        if (optimizationState.equals(OptimizationState.INITIAL)) {
            return initialResult.getRelativeMargin(flowCnec, unit);
        } else if (optimizationState.equals(OptimizationState.AFTER_PRA)) {
            return preCurativeResult.getRelativeMargin(flowCnec, unit);
        } else {
            return postSecondPreventiveResult.getRelativeMargin(flowCnec, unit);
        }
    }

    @Override
    public double getCommercialFlow(OptimizationState optimizationState, FlowCnec flowCnec, Unit unit) {
        if (optimizationState.equals(OptimizationState.INITIAL)) {
            return initialResult.getCommercialFlow(flowCnec, unit);
        } else if (optimizationState.equals(OptimizationState.AFTER_PRA)) {
            return preCurativeResult.getCommercialFlow(flowCnec, unit);
        } else {
            return postSecondPreventiveResult.getCommercialFlow(flowCnec, unit);
        }
    }

    @Override
    public double getLoopFlow(OptimizationState optimizationState, FlowCnec flowCnec, Unit unit) {
        if (optimizationState.equals(OptimizationState.INITIAL)) {
            return initialResult.getLoopFlow(flowCnec, unit);
        } else if (optimizationState.equals(OptimizationState.AFTER_PRA)) {
            return preCurativeResult.getLoopFlow(flowCnec, unit);
        } else {
            return postSecondPreventiveResult.getLoopFlow(flowCnec, unit);
        }
    }

    @Override
    public double getPtdfZonalSum(OptimizationState optimizationState, FlowCnec flowCnec) {
        if (optimizationState.equals(OptimizationState.INITIAL)) {
            return initialResult.getPtdfZonalSum(flowCnec);
        } else if (optimizationState.equals(OptimizationState.AFTER_PRA)) {
            return preCurativeResult.getPtdfZonalSum(flowCnec);
        } else {
            return postSecondPreventiveResult.getPtdfZonalSum(flowCnec);
        }
    }
}
