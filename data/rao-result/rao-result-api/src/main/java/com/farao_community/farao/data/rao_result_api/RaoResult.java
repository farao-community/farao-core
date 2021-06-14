/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.rao_result_api;

import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.crac_api.network_action.NetworkAction;
import com.farao_community.farao.data.crac_api.range_action.PstRangeAction;
import com.farao_community.farao.data.crac_api.range_action.RangeAction;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This interface will provide complete results that a user could expect after a RAO. It enables to access physical
 * and computational values along different {@link OptimizationState} which represents the different states of the
 * optimization (initial situation, after PRA, after CRA).
 *
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public interface RaoResult {

    ComputationStatus getComputationStatus();

    //todo : javadoc
    Set<FlowCnec> getFlowCnecs();

    /**
     * It gives the flow on a {@link FlowCnec} at a given {@link OptimizationState} and in a
     * given {@link Unit}.
     *
     * @param optimizationState: The state of optimization to be studied.
     * @param flowCnec: The branch to be studied.
     * @param unit: The unit in which the flow is queried. Only accepted values are MEGAWATT or AMPERE.
     * @return The flow on the branch at the optimization state in the given unit.
     */
    double getFlow(OptimizationState optimizationState, FlowCnec flowCnec, Unit unit);

    /**
     * It gives the margin on a {@link FlowCnec} at a given {@link OptimizationState} and in a
     * given {@link Unit}. It is basically the difference between the flow and the most constraining threshold in the
     * flow direction of the given branch. If it is negative the branch is under constraint.
     *
     * @param optimizationState: The state of optimization to be studied.
     * @param flowCnec: The branch to be studied.
     * @param unit: The unit in which the margin is queried. Only accepted values are MEGAWATT or AMPERE.
     * @return The margin on the branch at the optimization state in the given unit.
     */
    double getMargin(OptimizationState optimizationState, FlowCnec flowCnec, Unit unit);

    /**
     * It gives the relative margin (according to CORE D-2 CC methodology) on a {@link FlowCnec} at a given
     * {@link OptimizationState} and in a given {@link Unit}. If the margin is negative it gives it directly (same
     * value as {@code getMargin} method. If the margin is positive it gives this value divided by the sum of the zonal
     * PTDFs on this branch of the studied zone. Zones to include in this computation are defined in the
     * RAO. If it is negative the branch is under constraint. If the PTDFs are not defined in the
     * computation or the sum of them is null, this method could return {@code Double.NaN} values.
     *
     * @param optimizationState: The state of optimization to be studied.
     * @param flowCnec: The branch to be studied.
     * @param unit: The unit in which the relative margin is queried. Only accepted values are MEGAWATT or AMPERE.
     * @return The relative margin on the branch at the optimization state in the given unit.
     */
    double getRelativeMargin(OptimizationState optimizationState, FlowCnec flowCnec, Unit unit);

    /**
     * It gives the value of commercial flow (according to CORE D-2 CC methodology) on a {@link FlowCnec} at a given
     * {@link OptimizationState} and in a given {@link Unit}. If the branch is not considered as a branch on which the
     * loop flows are monitored, this method could return {@code Double.NaN} values.
     *
     * @param optimizationState: The state of optimization to be studied.
     * @param flowCnec: The branch to be studied.
     * @param unit: The unit in which the commercial flow is queried. Only accepted values are MEGAWATT or AMPERE.
     * @return The commercial flow on the branch at the optimization state in the given unit.
     */
    double getCommercialFlow(OptimizationState optimizationState, FlowCnec flowCnec, Unit unit);

    /**
     * It gives the value of loop flow (according to CORE D-2 CC methodology) on a {@link FlowCnec} at a given
     * {@link OptimizationState} and in a given {@link Unit}. If the branch is not considered as a branch on which the
     * loop flows are monitored, this method could return {@code Double.NaN} values.
     *
     * @param optimizationState: The state of optimization to be studied.
     * @param flowCnec: The branch to be studied.
     * @param unit: The unit in which the loop flow is queried. Only accepted values are MEGAWATT or AMPERE.
     * @return The loop flow on the branch at the optimization state in the given unit.
     */
    double getLoopFlow(OptimizationState optimizationState, FlowCnec flowCnec, Unit unit);

    /**
     * It gives the sum of the computation areas' zonal PTDFs on a {@link FlowCnec} at a given
     * {@link OptimizationState}. If the computation does not consider PTDF values or if the RAO does
     * not define any list of considered areas, this method could return {@code Double.NaN} values.
     *
     * @param optimizationState: The state of optimization to be studied.
     * @param flowCnec: The branch to be studied.
     * @return The sum of the computation areas' zonal PTDFs on the branch at the optimization state.
     */
    double getPtdfZonalSum(OptimizationState optimizationState, FlowCnec flowCnec);

    /**
     * It gives the global cost of the situation at a given {@link OptimizationState} according to the objective
     * function defined in the RAO.
     *
     * @param optimizationState: The state of optimization to be studied.
     * @return The global cost of the situation state.
     */
    double getCost(OptimizationState optimizationState);

    /**
     * It gives the functional cost of the situation at a given {@link OptimizationState} according to the objective
     * function defined in the RAO. It represents the main part of the objective function.
     *
     * @param optimizationState: The state of optimization to be studied.
     * @return The functional cost of the situation state.
     */
    double getFunctionalCost(OptimizationState optimizationState);

    /**
     * It gives an ordered list of the most constraining elements at a given {@link OptimizationState} according to the
     * objective function defined in the RAO. They are evaluated and ordered from the functional
     * costs of the different CNECs.
     *
     * @param optimizationState: The state of optimization to be studied.
     * @param number: The size of the list to be studied, so the number of limiting elements to be retrieved.
     * @return The ordered list of the n first limiting elements.
     */
    List<FlowCnec> getMostLimitingElements(OptimizationState optimizationState, int number);

    /**
     * It gives the sum of virtual costs of the situation at a given {@link OptimizationState} according to the
     * objective function defined in the RAO. It represents the secondary parts of the objective
     * function.
     *
     * @param optimizationState: The state of optimization to be studied.
     * @return The global virtual cost of the situation state.
     */
    double getVirtualCost(OptimizationState optimizationState);

    /**
     * It gives the names of the different virtual cost implied in the objective function defined in
     * the RAO.
     *
     * @return The set of virtual cost names.
     */
    Set<String> getVirtualCostNames();

    /**
     * It gives the specified virtual cost of the situation at a given {@link OptimizationState}. It represents the
     * secondary parts of the objective. If the specified name is not part of the virtual costs defined in the
     * objective function, this method could return {@code Double.NaN} values.
     *
     * @param optimizationState: The state of optimization to be studied.
     * @param virtualCostName: The name of the virtual cost.
     * @return The specific virtual cost of the situation state.
     */
    double getVirtualCost(OptimizationState optimizationState, String virtualCostName);

    /**
     * It gives an ordered list of the costly {@link FlowCnec} according to the specified virtual cost at a given
     * {@link OptimizationState}. If the virtual is null the list would be empty. If the specified virtual cost does
     * not imply any branch in its computation the list would be empty.
     *
     * @param optimizationState: The state of optimization to be studied.
     * @param virtualCostName: The name of the virtual cost.
     * @param number: The size of the list to be studied, so the number of costly elements to be retrieved.
     * @return The ordered list of the n first costly elements according to the given virtual cost.
     */
    List<FlowCnec> getCostlyElements(OptimizationState optimizationState, String virtualCostName, int number);


    //todo: javadoc
    Set<NetworkAction> getNetworkActions();

    /**
     * It states if the {@link NetworkAction} was already activated when a specific {@link State} is studied. Meaning
     * the network action has not been chosen by the optimizer on this state, but this action is already effective in
     * the network due to previous optimizations.
     *
     * @param state: The state of the state tree to be studied.
     * @param networkAction: The network action to be studied.
     * @return True if the network action is already active but has not been activated during the specified state.
     */
    boolean wasActivatedBeforeState(State state, NetworkAction networkAction);

    /**
     * It states if the {@link NetworkAction} is activated on a specific {@link State}.
     *
     * @param state: The state of the state tree to be studied.
     * @param networkAction: The network action to be studied.
     * @return True if the network action is chosen by the optimizer during the specified state.
     */
    boolean isActivatedDuringState(State state, NetworkAction networkAction);

    /**
     * It states if the {@link NetworkAction} is or was activated when a specific {@link State} is studied.
     *
     * @param state: The state of the state tree to be studied.
     * @param networkAction: The network action to be studied.
     * @return True if the network action is active during the specified state.
     */
    default boolean isActivated(State state, NetworkAction networkAction) {
        return wasActivatedBeforeState(state, networkAction) || isActivatedDuringState(state, networkAction);
    }

    /**
     * It gathers the {@link NetworkAction} that are activated during the specified {@link State}.
     *
     * @param state: The state of the state tree to be studied.
     * @return The set of activated network action during the specified state.
     */
    Set<NetworkAction> getActivatedNetworkActionsDuringState(State state);

    /**
     * It states if a {@link RangeAction} is activated during a specified {@link State}. It is the case only if the set
     * point of the range action is different in the specified state compared to the previous state. The previous
     * "state" is the initial situation in the case of the preventive state.
     *
     * @param state: The state of the state tree to be studied.
     * @param rangeAction: The range action to be studied.
     * @return True if the set point of the range action has been changed during the specified state.
     */
    boolean isActivatedDuringState(State state, RangeAction rangeAction);

    /**
     * It gives the tap position of the PST on which the {@link PstRangeAction} is pointing at before it is optimized
     * on the specified {@link State}. So, in the specific case of a PST range action that would be defined several
     * times for the same PST (but available on different states), the final result would always be the situation of
     * the PST on the state before its optimization. For example, if two PST range actions are defined :
     *  - RA1 : on "pst-element" only available in preventive state
     *  - RA2 : on "pst-element" only available on curative state after contingency "co-example"
     *
     * Let's say tap of "pst-element" is initially at 0 in the network. During preventive optimization RA1 is activated
     * and the PST tap goes to 5. During curative optimization RA2 is activated and the PST tap goes to 10. So when the
     * method is called, we would get the following results :
     *  - getPreOptimizationTapOnState(preventiveState, RA1) = getPreOptimizationTapOnState(preventiveState, RA2) = 0
     *  - getPreOptimizationTapOnState(curativeState, RA1) = getPreOptimizationTapOnState(curativeState, RA2) = 5
     * So we will still get 0 in preventive even if RA2 has not been activated during preventive optimization. And we
     * will still get 5 in curative even if RA1 has not been activated during curative optimization.
     *
     * @param state: The state of the state tree to be studied.
     * @param pstRangeAction: The PST range action to be studied.
     * @return The tap of the PST defined in the PST range action at the specified state before its optimization.
     */
    int getPreOptimizationTapOnState(State state, PstRangeAction pstRangeAction);

    /**
     * It gives the tap position of the PST on which the {@link PstRangeAction} is pointing at after it is optimized
     * on the specified {@link State}. So, in the specific case of a PST range action that would be defined several
     * times for the same PST (but available on different states), the final result would always be the optimized
     * situation of the PST on the state. For example, if two range actions are defined :
     *  - RA1 : on "pst-element" only available in preventive state
     *  - RA2 : on "pst-element" only available on curative state after contingency "co-example"
     *
     * Let's say tap of "pst-element" is initially at 0 in the network. During preventive optimization RA1 is activated
     * and the PST tap goes to 5. During curative optimization RA2 is activated and the PST tap goes to 10. So when the
     * method is called, we would get the following results :
     *  - getOptimizedTapOnState(preventiveState, RA1) = getOptimizedTapOnState(preventiveState, RA2) = 5
     *  - getOptimizedTapOnState(curativeState, RA1) = getOptimizedTapOnState(curativeState, RA2) = 10
     * So we will still get 5 in preventive even if RA2 has not been activated during preventive optimization. And we
     * will still get 10 in curative even if RA1 has not been activated during curative optimization.
     *
     * @param state: The state of the state tree to be studied.
     * @param pstRangeAction: The PST range action to be studied.
     * @return The tap of the PST defined in the PST range action at the specified state after its optimization.
     */
    int getOptimizedTapOnState(State state, PstRangeAction pstRangeAction);

    /**
     * It gives the set point of the element on which the {@link RangeAction} is pointing at before it is optimized
     * on the specified {@link State}. So, in the specific case of a range action that would be defined several
     * times for the same network element (but available on different states), the final result would always be the
     * set point of the network element on the state before its optimization. For example, if two range actions are
     * defined :
     *  - RA1 : on "pst-element" only available in preventive state
     *  - RA2 : on "pst-element" only available on curative state after contingency "co-example"
     *
     * Let's say the set point of "pst-element" is initially at 0. in the network. During preventive optimization RA1
     * is activated and the PST set point goes to 3.2. During curative optimization RA2 is activated and the PST tap
     * goes to 5.6. So when the  method is called, we would get the following results :
     *  - getOptimizedSetPointOnState(preventiveState, RA1) = getOptimizedSetPointOnState(preventiveState, RA2) = 0.
     *  - getOptimizedSetPointOnState(curativeState, RA1) = getOptimizedSetPointOnState(curativeState, RA2) = 3.2
     * So we will still get 0. in preventive even if RA2 has not been activated during preventive optimization. And we
     * will still get 3.2 in curative even if RA1 has not been activated during curative optimization.
     *
     * @param state: The state of the state tree to be studied.
     * @param rangeAction: The range action to be studied.
     * @return The set point of the network element defined in the range action at the specified state before its
     * optimization.
     */
    double getPreOptimizationSetPointOnState(State state, RangeAction rangeAction);

    /**
     * It gives the set point of the element on which the {@link RangeAction} is pointing at after it is optimized
     * on the specified {@link State}. So, in the specific case of a range action that would be defined several
     * times for the same network element (but available on different states), the final result would always be the
     * optimized situation of the network element on the state. For example, if two PST range actions are defined :
     *  - RA1 : on "pst-element" only available in preventive state
     *  - RA2 : on "pst-element" only available on curative state after contingency "co-example"
     *
     * Let's say the set point of "pst-element" is initially at 0. in the network. During preventive optimization RA1
     * is activated and the PST set point goes to 3.2. During curative optimization RA2 is activated and the PST tap
     * goes to 5.6. So when the  method is called, we would get the following results :
     *  - getOptimizedSetPointOnState(preventiveState, RA1) = getOptimizedSetPointOnState(preventiveState, RA2) = 3.2
     *  - getOptimizedSetPointOnState(curativeState, RA1) = getOptimizedSetPointOnState(curativeState, RA2) = 5.6
     * So we will still get 3.2 in preventive even if RA2 has not been activated during preventive optimization. And we
     * will still get 5.6 in curative even if RA1 has not been activated during curative optimization.
     *
     * @param state: The state of the state tree to be studied.
     * @param rangeAction: The range action to be studied.
     * @return The set point of the network element defined in the range action at the specified state after its
     * optimization.
     */
    double getOptimizedSetPointOnState(State state, RangeAction rangeAction);

    /**
     * It gathers the {@link RangeAction} that are activated during the specified {@link State}.
     *
     * @param state: The state of the state tree to be studied.
     * @return The set of activated range action during the specified state.
     */
    Set<RangeAction> getActivatedRangeActionsDuringState(State state);

    /**
     * It gives a summary of all the optimized taps of the {@link PstRangeAction} present in the {@link Crac} for a
     * specific {@link State}.
     *
     * @param state: The state of the state tree to be studied.
     * @return The map of the PST range actions associated to their optimized tap of the specified state.
     */
    Map<PstRangeAction, Integer> getOptimizedTapsOnState(State state);

    /**
     * It gives a summary of all the optimized set points of the {@link RangeAction} present in the {@link Crac} for a
     * specific {@link State}.
     *
     * @param state: The state of the state tree to be studied.
     * @return The map of the range actions associated to their optimized set points of the specified state.
     */
    Map<RangeAction, Double> getOptimizedSetPointsOnState(State state);
}