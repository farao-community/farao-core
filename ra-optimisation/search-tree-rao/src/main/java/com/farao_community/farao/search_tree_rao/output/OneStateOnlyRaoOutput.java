package com.farao_community.farao.search_tree_rao.output;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_api.cnec.BranchCnec;
import com.farao_community.farao.rao_api.results.OptimizationState;
import com.farao_community.farao.rao_api.results.PerimeterResult;
import com.farao_community.farao.rao_api.results.RaoResult;
import com.powsybl.commons.extensions.Extension;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OneStateOnlyRaoOutput implements RaoResult {
    private State optimizedState;
    private PerimeterResult initialResult;
    private PerimeterResult postOptimizationResult;

    public OneStateOnlyRaoOutput(State optimizedState, PerimeterResult initialResult, PerimeterResult postOptimizationResult) {
        this.optimizedState = optimizedState;
        this.initialResult = initialResult;
        this.postOptimizationResult = postOptimizationResult;
    }

    @Override
    public PerimeterResult getPerimeterResult(OptimizationState optimizationState, State state) {
        if (!state.equals(optimizedState)) {
            return null;
        }
        if (optimizationState == OptimizationState.INITIAL) {
            return initialResult;
        } else {
            return postOptimizationResult;
        }
    }

    @Override
    public PerimeterResult getPostPreventivePerimeterResult() {
        if (!optimizedState.getInstant().equals(Instant.PREVENTIVE)) {
            return null;
        }
        return postOptimizationResult;
    }

    @Override
    public PerimeterResult getInitialResult() {
        return initialResult;
    }

    @Override
    public double getFunctionalCost(OptimizationState optimizationState) {
        if (optimizationState == OptimizationState.INITIAL) {
            return initialResult.getFunctionalCost();
        } else {
            return postOptimizationResult.getFunctionalCost();
        }
    }

    @Override
    public List<BranchCnec> getMostLimitingElements(OptimizationState optimizationState, int number) {
        if (optimizationState == OptimizationState.INITIAL) {
            return initialResult.getMostLimitingElements(number);
        } else {
            return postOptimizationResult.getMostLimitingElements(number);
        }
    }

    @Override
    public double getVirtualCost(OptimizationState optimizationState) {
        if (optimizationState == OptimizationState.INITIAL) {
            return initialResult.getVirtualCost();
        } else {
            return postOptimizationResult.getVirtualCost();
        }
    }

    @Override
    public Set<String> getVirtualCostNames() {
        return initialResult.getVirtualCostNames();
    }

    @Override
    public double getVirtualCost(OptimizationState optimizationState, String virtualCostName) {
        if (optimizationState == OptimizationState.INITIAL) {
            return initialResult.getVirtualCost(virtualCostName);
        } else {
            return postOptimizationResult.getVirtualCost(virtualCostName);
        }
    }

    @Override
    public List<BranchCnec> getCostlyElements(OptimizationState optimizationState, String virtualCostName, int number) {
        if (optimizationState == OptimizationState.INITIAL) {
            return initialResult.getCostlyElements(virtualCostName, number);
        } else {
            return postOptimizationResult.getCostlyElements(virtualCostName, number);
        }
    }

    @Override
    public boolean wasActivatedBeforeState(State state, NetworkAction networkAction) {
        if (!state.equals(optimizedState)) {
            throw new FaraoException("Trying to access perimeter result for the wrong state.");
        }
        return initialResult.isActivated(networkAction);
    }

    @Override
    public boolean isActivatedDuringState(State state, NetworkAction networkAction) {
        if (!state.equals(optimizedState)) {
            throw new FaraoException("Trying to access perimeter result for the wrong state.");
        }
        return postOptimizationResult.isActivated(networkAction);
    }

    @Override
    public Set<NetworkAction> getActivatedNetworkActionsDuringState(State state) {
        if (!state.equals(optimizedState)) {
            throw new FaraoException("Trying to access perimeter result for the wrong state.");
        }
        return postOptimizationResult.getActivatedNetworkActions();
    }

    @Override
    public boolean isActivatedDuringState(State state, RangeAction rangeAction) {
        if (!state.equals(optimizedState)) {
            throw new FaraoException("Trying to access perimeter result for the wrong state.");
        }
        return postOptimizationResult.getOptimizedSetPoint(rangeAction) != initialResult.getOptimizedSetPoint(rangeAction);
    }

    @Override
    public int getPreOptimizationTapOnState(State state, PstRangeAction pstRangeAction) {
        if (!state.equals(optimizedState)) {
            throw new FaraoException("Trying to access perimeter result for the wrong state.");
        }
        return initialResult.getOptimizedTap(pstRangeAction);
    }

    @Override
    public int getOptimizedTapOnState(State state, PstRangeAction pstRangeAction) {
        if (!state.equals(optimizedState)) {
            throw new FaraoException("Trying to access perimeter result for the wrong state.");
        }
        return postOptimizationResult.getOptimizedTap(pstRangeAction);
    }

    @Override
    public double getPreOptimizationSetPointOnState(State state, RangeAction rangeAction) {
        if (!state.equals(optimizedState)) {
            throw new FaraoException("Trying to access perimeter result for the wrong state.");
        }
        return initialResult.getOptimizedSetPoint(rangeAction);
    }

    @Override
    public double getOptimizedSetPointOnState(State state, RangeAction rangeAction) {
        if (!state.equals(optimizedState)) {
            throw new FaraoException("Trying to access perimeter result for the wrong state.");
        }
        return postOptimizationResult.getOptimizedSetPoint(rangeAction);
    }

    @Override
    public Set<RangeAction> getActivatedRangeActionsDuringState(State state) {
        if (!state.equals(optimizedState)) {
            throw new FaraoException("Trying to access perimeter result for the wrong state.");
        }
        return postOptimizationResult.getActivatedRangeActions().stream().filter(rangeAction -> isActivatedDuringState(state, rangeAction)).collect(Collectors.toSet());
    }

    @Override
    public Map<PstRangeAction, Integer> getOptimizedTapsOnState(State state) {
        if (!state.equals(optimizedState)) {
            throw new FaraoException("Trying to access perimeter result for the wrong state.");
        }
        return postOptimizationResult.getOptimizedTaps();
    }

    @Override
    public Map<RangeAction, Double> getOptimizedSetPointsOnState(State state) {
        if (!state.equals(optimizedState)) {
            throw new FaraoException("Trying to access perimeter result for the wrong state.");
        }
        return postOptimizationResult.getOptimizedSetPoints();
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