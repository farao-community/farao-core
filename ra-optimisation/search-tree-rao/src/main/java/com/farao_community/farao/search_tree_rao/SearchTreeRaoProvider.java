/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.search_tree_rao;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.Side;
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_api.cnec.BranchCnec;
import com.farao_community.farao.data.crac_api.cnec.Cnec;
import com.farao_community.farao.data.crac_result_extensions.*;
import com.farao_community.farao.rao_api.RaoInput;
import com.farao_community.farao.rao_api.RaoParameters;
import com.farao_community.farao.rao_api.RaoProvider;
import com.farao_community.farao.rao_api.RaoResult;
import com.farao_community.farao.rao_commons.InitialSensitivityAnalysis;
import com.farao_community.farao.rao_commons.RaoData;
import com.farao_community.farao.rao_commons.RaoUtil;
import com.farao_community.farao.sensitivity_analysis.SensitivityAnalysisException;
import com.farao_community.farao.sensitivity_analysis.SystematicSensitivityResult;
import com.farao_community.farao.util.FaraoNetworkPool;
import com.google.auto.service.AutoService;
import com.powsybl.iidm.network.Network;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@AutoService(RaoProvider.class)
public class SearchTreeRaoProvider implements RaoProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchTreeRaoProvider.class);
    private static final String SEARCH_TREE_RAO = "SearchTreeRao";
    private static final String PREVENTIVE_STATE = "PreventiveState";
    private static final String CURATIVE_STATE = "CurativeState";
    private static final int NUMBER_LOGGED_ELEMENTS_END_RAO = 10;

    private RaoData preventiveRaoData;

    private StateTree stateTree;

    @Override
    public String getName() {
        return SEARCH_TREE_RAO;
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    public SearchTreeRaoProvider() {
    }

    // Useful for tests
    SearchTreeRaoProvider(StateTree stateTree) {
        this.stateTree = stateTree;
    }

    @Override
    public CompletableFuture<RaoResult> run(RaoInput raoInput, RaoParameters parameters) {
        RaoUtil.initData(raoInput, parameters);

        stateTree = new StateTree(raoInput.getCrac(), raoInput.getNetwork(), raoInput.getCrac().getPreventiveState());

        // optimization is made on one given state only
        if (raoInput.getOptimizedState() != null) {
            return optimizeOneStateOnly(raoInput, parameters);
        }

        // compute initial sensitivity on all CNECs
        // this is necessary to have initial flows for MNEC and loopflow constraints on CNECs, in preventive and curative perimeters
        SystematicSensitivityResult initialSensitivityResult;
        try {
            initialSensitivityResult = initialSensitivityAnalysisOnAllPerimeters(raoInput, parameters);
        } catch (SensitivityAnalysisException e) {
            LOGGER.error("Initial sensitivity analysis failed :", e);
            return CompletableFuture.completedFuture(new RaoResult(RaoResult.Status.FAILURE));
        }

        // optimize preventive perimeter
        LOGGER.info("Preventive perimeter optimization [start]");

        Network network = raoInput.getNetwork();
        network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), PREVENTIVE_STATE);
        network.getVariantManager().setWorkingVariant(PREVENTIVE_STATE);

        if (stateTree.getOptimizedStates().size() == 1) {
            return optimizePreventivePerimeter(raoInput, parameters, initialSensitivityResult);
        }

        RaoResult preventiveRaoResult = optimizePreventivePerimeter(raoInput, parameters, initialSensitivityResult).join();
        LOGGER.info("Preventive perimeter optimization [end]");

        // optimize curative perimeters
        double preventiveOptimalCost = raoInput.getCrac().getExtension(CracResultExtension.class).getVariant(preventiveRaoResult.getPostOptimVariantId()).getCost();
        raoInput.getCrac().getExtension(ResultVariantManager.class).setPrePerimeterVariantId(preventiveRaoResult.getPostOptimVariantId());
        TreeParameters curativeTreeParameters = TreeParameters.buildForCurativePerimeter(parameters.getExtension(SearchTreeRaoParameters.class), preventiveOptimalCost, stateTree.getOperatorsNotSharingCras());
        CracResultUtil.applyRemedialActionsForState(raoInput.getNetwork(), raoInput.getCrac(), preventiveRaoResult.getPostOptimVariantId(), raoInput.getCrac().getPreventiveState());
        Map<State, RaoResult> curativeResults = optimizeCurativePerimeters(raoInput, parameters, curativeTreeParameters, network);

        // merge variants
        LOGGER.info("Merging preventive and curative RAO results.");

        // Compute what would have been the objective function value if no PRA and no CRA were applied
        // This is different from the preventive perimeter's preoptim cost, since the preventive perimeter does not consider curative CNECs
        Double preOptimCost = parameters.isPostCheckRaoResults() ? computePreOptimCost(initialSensitivityResult, raoInput.getCrac().getBranchCnecs(), parameters, preventiveRaoResult.getPreOptimVariantId()) : null;
        RaoResult mergedRaoResults = mergeRaoResults(raoInput.getCrac(), preventiveRaoResult, curativeResults, preOptimCost);

        // log results
        if (mergedRaoResults.isSuccessful()) {
            SearchTreeRaoLogger.logMostLimitingElementsResults(raoInput.getCrac().getBranchCnecs(), mergedRaoResults.getPostOptimVariantId(), parameters.getObjectiveFunction().getUnit(), parameters.getObjectiveFunction().relativePositiveMargins(), NUMBER_LOGGED_ELEMENTS_END_RAO);
        }
        return CompletableFuture.completedFuture(mergedRaoResults);

    }

    private CompletableFuture<RaoResult> optimizeOneStateOnly(RaoInput raoInput, RaoParameters raoParameters) {
        RaoData raoData = new  RaoData(
            raoInput.getNetwork(),
                raoInput.getCrac(),
                raoInput.getOptimizedState(),
                raoInput.getPerimeter(),
                raoInput.getReferenceProgram(),
                raoInput.getGlskProvider(),
                raoInput.getBaseCracVariantId(),
                raoParameters);
        TreeParameters treeParameters = raoInput.getOptimizedState().equals(raoInput.getCrac().getPreventiveState()) ?
                TreeParameters.buildForPreventivePerimeter(raoParameters.getExtension(SearchTreeRaoParameters.class)) :
                TreeParameters.buildForCurativePerimeter(raoParameters.getExtension(SearchTreeRaoParameters.class), -Double.MAX_VALUE, stateTree.getOperatorsNotSharingCras());
        new InitialSensitivityAnalysis(raoData).run();
        RaoResult raoResult = new SearchTree().run(raoData, raoParameters, treeParameters).join();
        SearchTreeRaoLogger.logMostLimitingElementsResults(raoInput.getCrac().getBranchCnecs(), raoResult.getPostOptimVariantId(), raoParameters.getObjectiveFunction().getUnit(), raoParameters.getObjectiveFunction().relativePositiveMargins(), NUMBER_LOGGED_ELEMENTS_END_RAO);
        return CompletableFuture.completedFuture(raoResult);
    }

    private SystematicSensitivityResult initialSensitivityAnalysisOnAllPerimeters(RaoInput raoInput, RaoParameters parameters) {
        RaoData raoData = new RaoData(
            raoInput.getNetwork(),
            raoInput.getCrac(),
            raoInput.getCrac().getPreventiveState(),
            raoInput.getCrac().getStates(),
            raoInput.getReferenceProgram(),
            raoInput.getGlskProvider(),
            raoInput.getBaseCracVariantId(),
            parameters);
        return new InitialSensitivityAnalysis(raoData).run();
    }

    private CompletableFuture<RaoResult> optimizePreventivePerimeter(RaoInput raoInput, RaoParameters parameters, SystematicSensitivityResult initialSensitivityResult) {
        String baseVariantId = raoInput.getCrac().getExtension(ResultVariantManager.class).getInitialVariantId();
        preventiveRaoData = new RaoData(
            raoInput.getNetwork(),
            raoInput.getCrac(),
            raoInput.getCrac().getPreventiveState(),
            stateTree.getPerimeter(raoInput.getCrac().getPreventiveState()),
            raoInput.getReferenceProgram(),
            raoInput.getGlskProvider(),
            baseVariantId,
            parameters);
        preventiveRaoData.setSystematicSensitivityResult(initialSensitivityResult);
        TreeParameters preventiveTreeParameters = TreeParameters.buildForPreventivePerimeter(parameters.getExtension(SearchTreeRaoParameters.class));
        return new SearchTree().run(preventiveRaoData, parameters, preventiveTreeParameters);
    }

    private Map<State, RaoResult> optimizeCurativePerimeters(RaoInput raoInput, RaoParameters parameters, TreeParameters curativeTreeParameters, Network network) {
        String initialVariantId = raoInput.getCrac().getExtension(ResultVariantManager.class).getInitialVariantId();
        Map<State, RaoResult> curativeResults = new ConcurrentHashMap<>();
        network.getVariantManager().setWorkingVariant(PREVENTIVE_STATE);
        network.getVariantManager().cloneVariant(PREVENTIVE_STATE, CURATIVE_STATE);
        network.getVariantManager().setWorkingVariant(CURATIVE_STATE);
        Map<String, String> initialVariantIdPerOptimizedStateId = new ConcurrentHashMap<>();
        stateTree.getOptimizedStates().forEach(optimizedState -> {
            if (!optimizedState.equals(raoInput.getCrac().getPreventiveState())) {
                initialVariantIdPerOptimizedStateId.put(optimizedState.getId(), preventiveRaoData.getCracVariantManager().cloneWorkingVariant());
            }
        });
        try (FaraoNetworkPool networkPool = new FaraoNetworkPool(network, CURATIVE_STATE, parameters.getPerimetersInParallel())) {
            stateTree.getOptimizedStates().forEach(optimizedState -> {
                if (!optimizedState.equals(raoInput.getCrac().getPreventiveState())) {
                    networkPool.submit(() -> {
                        try {
                            LOGGER.info("Optimizing curative state {}.", optimizedState.getId());
                            Network networkClone = networkPool.getAvailableNetwork();
                            RaoData curativeRaoData = new RaoData(
                                networkClone,
                                raoInput.getCrac(),
                                optimizedState,
                                stateTree.getPerimeter(optimizedState),
                                raoInput.getReferenceProgram(),
                                raoInput.getGlskProvider(),
                                initialVariantIdPerOptimizedStateId.get(optimizedState.getId()),
                                parameters);
                            curativeRaoData.getCracResultManager().copyAbsolutePtdfSumsBetweenVariants(initialVariantId, curativeRaoData.getWorkingVariantId());
                            curativeRaoData.getCracResultManager().copyAbsolutePtdfSumsBetweenVariants(initialVariantId, curativeRaoData.getCrac().getExtension(ResultVariantManager.class).getPrePerimeterVariantId());
                            if (!parameters.getLoopFlowApproximationLevel().shouldUpdatePtdfWithTopologicalChange()) {
                                curativeRaoData.getCracResultManager().copyCommercialFlowsBetweenVariants(initialVariantId, curativeRaoData.getWorkingVariantId());
                            }
                            RaoResult curativeResult = new SearchTree().run(curativeRaoData, parameters, curativeTreeParameters).join();
                            curativeResults.put(optimizedState, curativeResult);
                            networkPool.releaseUsedNetwork(networkClone);
                            LOGGER.info("Curative state {} has been optimized.", optimizedState.getId());
                        } catch (InterruptedException | NotImplementedException | FaraoException | NullPointerException e) {
                            LOGGER.error("Curative state {} could not be optimized.", optimizedState.getId());
                            Thread.currentThread().interrupt();
                        }
                    });
                }
            });
            networkPool.shutdown();
            networkPool.awaitTermination(24, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return curativeResults;
    }

    RaoResult mergeRaoResults(Crac crac, RaoResult preventiveRaoResult, Map<State, RaoResult> curativeRaoResults, Double preOptimCost) {
        mergeRaoResultStatus(preventiveRaoResult, curativeRaoResults);
        if (preOptimCost == null || hasCostImproved(crac, preventiveRaoResult, curativeRaoResults, preOptimCost)) {
            mergeCnecResults(crac, preventiveRaoResult, curativeRaoResults);
            mergeRemedialActionsResults(crac, preventiveRaoResult, curativeRaoResults);
            mergeObjectiveFunctionValues(crac, preventiveRaoResult, curativeRaoResults);
            deleteCurativeVariants(crac, preventiveRaoResult.getPostOptimVariantId());
        } else {
            LOGGER.warn("The RAO degraded the minimum margin. The initial variant will be kept as the optimal one.");
            String postOptimVariantId = preventiveRaoResult.getPreOptimVariantId();
            preventiveRaoResult.setPostOptimVariantId(postOptimVariantId);
            deleteCurativeVariants(crac, postOptimVariantId);
        }
        return preventiveRaoResult;
    }

    private double computePreOptimCost(SystematicSensitivityResult systematicSensitivityResult, Set<BranchCnec> cnecs, RaoParameters raoParameters, String initialVariantId) {
        // compute only functional cost (min margin)
        // do not consider virtual costs since they're supposed to be null
        boolean relative = raoParameters.getObjectiveFunction().relativePositiveMargins();
        return -cnecs.stream()
                .filter(BranchCnec::isOptimized)
                .map(cnec -> {
                    double margin = 0;
                    if (raoParameters.getObjectiveFunction().getUnit().equals(Unit.MEGAWATT)) {
                        margin = cnec.computeMargin(systematicSensitivityResult.getReferenceFlow(cnec), Side.LEFT, Unit.MEGAWATT);
                    } else if (raoParameters.getObjectiveFunction().getUnit().equals(Unit.AMPERE)) {
                        margin = cnec.computeMargin(systematicSensitivityResult.getReferenceIntensity(cnec), Side.LEFT, Unit.AMPERE);
                    } else {
                        throw new FaraoException(String.format("Unhandled objective function unit %s", raoParameters.getObjectiveFunction().getUnit()));
                    }
                    if (relative && margin > 0) {
                        margin = margin / cnec.getExtension(CnecResultExtension.class).getVariant(initialVariantId).getAbsolutePtdfSum();
                    }
                    return margin;
                }).min(Double::compareTo).orElseThrow();
    }

    private boolean hasCostImproved(Crac crac, RaoResult preventiveRaoResult, Map<State, RaoResult> curativeRaoResults, double preOptimCost) {
        CracResultExtension cracResultMap = crac.getExtension(CracResultExtension.class);
        return cracResultMap.getVariant(preventiveRaoResult.getPostOptimVariantId()).getCost() <= preOptimCost
                && getWorstCurativePostOptimCost(crac, curativeRaoResults) <= preOptimCost;
    }

    private void mergeRaoResultStatus(RaoResult preventiveRaoResult, Map<State, RaoResult> curativeRaoResults) {
        if (curativeRaoResults.values().stream().anyMatch(curativeRaoResult -> curativeRaoResult.getStatus().equals(RaoResult.Status.FAILURE))) {
            preventiveRaoResult.setStatus(RaoResult.Status.FAILURE);
        }
    }

    private void mergeCnecResults(Crac crac, RaoResult preventiveRaoResult, Map<State, RaoResult> curativeRaoResults) {
        crac.getBranchCnecs().forEach(cnec -> {
            State optimizedState = stateTree.getOptimizedState(cnec.getState());
            if (!optimizedState.equals(crac.getPreventiveState())) {
                String optimizedVariantId = curativeRaoResults.get(optimizedState).getPostOptimVariantId();
                CnecResult optimizedCnecResult = cnec.getExtension(CnecResultExtension.class).getVariant(optimizedVariantId);
                CnecResult targetResult = cnec.getExtension(CnecResultExtension.class).getVariant(preventiveRaoResult.getPostOptimVariantId());
                targetResult.setAbsolutePtdfSum(optimizedCnecResult.getAbsolutePtdfSum());
                targetResult.setFlowInA(optimizedCnecResult.getFlowInA());
                targetResult.setFlowInMW(optimizedCnecResult.getFlowInMW());
                targetResult.setLoopflowInMW(optimizedCnecResult.getLoopflowInMW());
                targetResult.setLoopflowThresholdInMW(optimizedCnecResult.getLoopflowThresholdInMW());
                targetResult.setMaxThresholdInA(optimizedCnecResult.getMaxThresholdInA());
                targetResult.setMaxThresholdInMW(optimizedCnecResult.getMaxThresholdInMW());
                targetResult.setMinThresholdInA(optimizedCnecResult.getMinThresholdInA());
                targetResult.setMinThresholdInMW(optimizedCnecResult.getMinThresholdInMW());
                targetResult.setAbsolutePtdfSum(optimizedCnecResult.getAbsolutePtdfSum());
            }
        });
    }

    private void mergeRemedialActionsResults(Crac crac, RaoResult preventiveRaoResult, Map<State, RaoResult> curativeRaoResults) {
        stateTree.getOptimizedStates().forEach(optimizedState -> {
            if (!optimizedState.equals(crac.getPreventiveState())) {
                String optimizedVariantId = curativeRaoResults.get(optimizedState).getPostOptimVariantId();
                crac.getNetworkActions().forEach(networkAction -> {
                    NetworkActionResult naResult = networkAction.getExtension(NetworkActionResultExtension.class).getVariant(optimizedVariantId);
                    NetworkActionResult targetNaResult = networkAction.getExtension(NetworkActionResultExtension.class).getVariant(preventiveRaoResult.getPostOptimVariantId());
                    if (naResult.isActivated(optimizedState.getId())) {
                        targetNaResult.activate(optimizedState.getId());
                    }
                });
                crac.getRangeActions().forEach(rangeAction -> {
                    RangeActionResult raResult = rangeAction.getExtension(RangeActionResultExtension.class).getVariant(optimizedVariantId);
                    RangeActionResult targetRaResult = rangeAction.getExtension(RangeActionResultExtension.class).getVariant(preventiveRaoResult.getPostOptimVariantId());
                    stateTree.getPerimeter(optimizedState).forEach(state -> {
                        targetRaResult.setSetPoint(state.getId(), raResult.getSetPoint(state.getId()));
                        if (raResult instanceof PstRangeResult && targetRaResult instanceof PstRangeResult
                            && ((PstRangeResult) raResult).getTap(state.getId()) != null) {
                            ((PstRangeResult) targetRaResult).setTap(state.getId(), ((PstRangeResult) raResult).getTap(state.getId()));
                        }
                    });
                });
            }
        });
    }

    private double getPerimeterCost(Crac crac, State state, RaoResult result) {
        double cost = crac.getExtension(CracResultExtension.class).getVariant(result.getPostOptimVariantId()).getCost();
        if (crac.getBranchCnecs(state).stream().anyMatch(Cnec::isOptimized)) {
            // If there are CNECs, simply get the cost
            return cost;
        } else {
            // Pure MNECs
            // If there is a virtual cost, it should be considered
            // If not, it's as if the perimeter has a -infinity cost
            if (cost > 0) {
                return cost;
            } else {
                return -Double.MAX_VALUE;
            }
        }
    }

    private Pair<State, RaoResult> getWorstCurativeResult(Crac crac, Map<State, RaoResult> curativeRaoResults) {
        List<Map.Entry<State, RaoResult>> curativeCosts = curativeRaoResults.entrySet().stream()
                .sorted(Comparator.comparingDouble(entry -> -getPerimeterCost(crac, entry.getKey(), entry.getValue())))
                .collect(Collectors.toList());
        if (curativeCosts.isEmpty()) {
            return null;
        } else {
            return Pair.of(curativeCosts.get(0).getKey(), curativeCosts.get(0).getValue());
        }
    }

    private double getWorstCurativePostOptimCost(Crac crac, Map<State, RaoResult> curativeRaoResults) {
        Pair<State, RaoResult> worstResult = getWorstCurativeResult(crac, curativeRaoResults);
        if (worstResult == null) {
            return -Double.MAX_VALUE;
        } else {
            return getPerimeterCost(crac, worstResult.getLeft(), worstResult.getRight());
        }
    }

    private void mergeObjectiveFunctionValues(Crac crac, RaoResult preventiveRaoResult, Map<State, RaoResult> curativeRaoResults) {
        // Save the objective function value of the "worst" perimeter (maximum obj function value)
        Pair<State, RaoResult> worstResult = getWorstCurativeResult(crac, curativeRaoResults);
        if (worstResult != null) {
            CracResultExtension cracResultMap = crac.getExtension(CracResultExtension.class);
            if (getPerimeterCost(crac, worstResult.getLeft(), worstResult.getRight()) > cracResultMap.getVariant(preventiveRaoResult.getPostOptimVariantId()).getCost()) {
                cracResultMap.getVariant(preventiveRaoResult.getPostOptimVariantId()).setFunctionalCost(cracResultMap.getVariant(worstResult.getRight().getPostOptimVariantId()).getFunctionalCost());
                cracResultMap.getVariant(preventiveRaoResult.getPostOptimVariantId()).setVirtualCost(cracResultMap.getVariant(worstResult.getRight().getPostOptimVariantId()).getVirtualCost());
            }
        }
    }

    private void deleteCurativeVariants(Crac crac, String postOptimVariantId) {
        ResultVariantManager resultVariantManager = crac.getExtension(ResultVariantManager.class);
        List<String> variantToDelete = resultVariantManager.getVariants().stream().
            filter(name -> !name.equals(resultVariantManager.getInitialVariantId())).
            filter(name -> !name.equals(postOptimVariantId)).
            collect(Collectors.toList());
        variantToDelete.forEach(variantId -> crac.getExtension(ResultVariantManager.class).deleteVariant(variantId));
    }
}
