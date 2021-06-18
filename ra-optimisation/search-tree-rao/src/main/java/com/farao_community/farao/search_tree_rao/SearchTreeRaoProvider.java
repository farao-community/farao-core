/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.search_tree_rao;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.RemedialAction;
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_api.cnec.Cnec;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.crac_api.cnec.Side;
import com.farao_community.farao.data.crac_api.range_action.RangeAction;
import com.farao_community.farao.data.crac_api.usage_rule.UsageMethod;
import com.farao_community.farao.loopflow_computation.LoopFlowComputationWithXnodeGlskHandler;
import com.farao_community.farao.rao_api.RaoInput;
import com.farao_community.farao.rao_api.RaoProvider;
import com.farao_community.farao.rao_api.parameters.*;
import com.farao_community.farao.rao_api.results.*;
import com.farao_community.farao.rao_commons.AbsolutePtdfSumsComputation;
import com.farao_community.farao.rao_commons.PrePerimeterSensitivityAnalysis;
import com.farao_community.farao.rao_commons.RaoUtil;
import com.farao_community.farao.rao_commons.ToolProvider;
import com.farao_community.farao.rao_commons.linear_optimisation.IteratingLinearOptimizer;
import com.farao_community.farao.rao_commons.objective_function_evaluator.LoopFlowViolationCostEvaluator;
import com.farao_community.farao.rao_commons.objective_function_evaluator.MnecViolationCostEvaluator;
import com.farao_community.farao.rao_commons.objective_function_evaluator.ObjectiveFunction;
import com.farao_community.farao.rao_commons.objective_function_evaluator.SensitivityFallbackOvercostEvaluator;
import com.farao_community.farao.search_tree_rao.output.FailedRaoOutput;
import com.farao_community.farao.search_tree_rao.output.OneStateOnlyRaoOutput;
import com.farao_community.farao.search_tree_rao.output.PreventiveAndCurativesRaoOutput;
import com.farao_community.farao.search_tree_rao.output.SecondPreventiveAndCurativesRaoOutput;
import com.farao_community.farao.sensitivity_analysis.AppliedRemedialActions;
import com.farao_community.farao.sensitivity_analysis.SensitivityAnalysisException;
import com.farao_community.farao.util.FaraoNetworkPool;
import com.google.auto.service.AutoService;
import com.powsybl.iidm.network.Network;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.farao_community.farao.commons.Unit.MEGAWATT;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@AutoService(RaoProvider.class)
public class SearchTreeRaoProvider implements RaoProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchTreeRaoProvider.class);
    private static final String SEARCH_TREE_RAO = "SearchTreeRao";
    private static final String PREVENTIVE_STATE = "PreventiveState";
    private static final String SECOND_PREVENTIVE_STATE = "SecondPreventiveState";
    private static final String CURATIVE_STATE = "CurativeState";
    private static final int NUMBER_LOGGED_ELEMENTS_END_RAO = 10;

    private StateTree stateTree;
    private ToolProvider toolProvider;

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

    @Override
    public CompletableFuture<RaoResult> run(RaoInput raoInput, RaoParameters parameters) {
        RaoUtil.initData(raoInput, parameters);

        stateTree = new StateTree(raoInput.getCrac(), raoInput.getCrac().getPreventiveState());
        ToolProvider.ToolProviderBuilder toolProviderBuilder = ToolProvider.create()
                .withNetwork(raoInput.getNetwork())
                .withRaoParameters(parameters);
        if (raoInput.getReferenceProgram() != null) {
            toolProviderBuilder.withLoopFlowComputation(
                    raoInput.getReferenceProgram(),
                    raoInput.getGlskProvider(),
                    new LoopFlowComputationWithXnodeGlskHandler(
                            raoInput.getGlskProvider(),
                            raoInput.getReferenceProgram(),
                            raoInput.getCrac().getContingencies(),
                            raoInput.getNetwork()
                    )
            );
        }
        if (parameters.getObjectiveFunction().relativePositiveMargins()) {
            toolProviderBuilder.withAbsolutePtdfSumsComputation(
                    raoInput.getGlskProvider(),
                    new AbsolutePtdfSumsComputation(
                            raoInput.getGlskProvider(),
                            parameters.getRelativeMarginPtdfBoundaries()
                    )
            );
        }
        this.toolProvider = toolProviderBuilder.build();

        // optimization is made on one given state only
        if (raoInput.getOptimizedState() != null) {
            return optimizeOneStateOnly(raoInput, parameters);
        }

        // compute initial sensitivity on all CNECs
        // this is necessary to have initial flows for MNEC and loopflow constraints on CNECs, in preventive and curative perimeters
        PrePerimeterSensitivityAnalysis prePerimeterSensitivityAnalysis = new PrePerimeterSensitivityAnalysis(
                raoInput.getCrac().getRangeActions(),
                raoInput.getCrac().getFlowCnecs(),
                toolProvider,
                parameters
        );

        PrePerimeterResult initialOutput;
        try {
            initialOutput = prePerimeterSensitivityAnalysis.run(raoInput.getNetwork());
        } catch (SensitivityAnalysisException e) {
            LOGGER.error("Initial sensitivity analysis failed :", e);
            return CompletableFuture.completedFuture(new FailedRaoOutput());
        }

        // optimize preventive perimeter
        LOGGER.info("Preventive perimeter optimization [start]");

        Network network = raoInput.getNetwork();
        network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), PREVENTIVE_STATE);
        network.getVariantManager().cloneVariant(network.getVariantManager().getWorkingVariantId(), SECOND_PREVENTIVE_STATE);
        network.getVariantManager().setWorkingVariant(PREVENTIVE_STATE);

        if (stateTree.getOptimizedStates().size() == 1) {
            return optimizePreventivePerimeter(raoInput, parameters, initialOutput);
        }

        PerimeterResult preventiveResult = optimizePreventivePerimeter(raoInput, parameters, initialOutput).join().getPerimeterResult(OptimizationState.AFTER_PRA, raoInput.getCrac().getPreventiveState());
        LOGGER.info("Preventive perimeter optimization [end]");

        // optimize curative perimeters
        double preventiveOptimalCost = preventiveResult.getCost();
        TreeParameters curativeTreeParameters = TreeParameters.buildForCurativePerimeter(parameters.getExtension(SearchTreeRaoParameters.class), preventiveOptimalCost);
        applyRemedialActions(network, preventiveResult);

        PrePerimeterResult preCurativeSensitivityAnalysisOutput = prePerimeterSensitivityAnalysis.runBasedOn(network, preventiveResult);

        Map<State, OptimizationResult> curativeResults = optimizeCurativePerimeters(raoInput, parameters, curativeTreeParameters, network, initialOutput, preCurativeSensitivityAnalysisOutput);

        RaoResult<?> mergedRaoResults;

        // second preventive RAO
        if (shouldRunSecondPreventiveRao(parameters, curativeResults)) {
            mergedRaoResults = runSecondPreventiveRao(raoInput, parameters, prePerimeterSensitivityAnalysis, initialOutput, preventiveResult, preCurativeSensitivityAnalysisOutput, curativeResults);
        } else {
            LOGGER.info("Merging preventive and curative RAO results.");
            mergedRaoResults = new PreventiveAndCurativesRaoOutput(initialOutput, preventiveResult, preCurativeSensitivityAnalysisOutput, curativeResults);
        }

        // log results
        SearchTreeRaoLogger.logMostLimitingElementsResults(mergedRaoResults, OptimizationState.AFTER_CRA, parameters.getObjectiveFunction().getUnit(), parameters.getObjectiveFunction().relativePositiveMargins(), NUMBER_LOGGED_ELEMENTS_END_RAO);
        // TODO: find a way to display merged limiting elements from a RaoResult
        return CompletableFuture.completedFuture(mergedRaoResults);
    }

    private void applyRemedialActions(Network network, PerimeterResult perimeterResult) {
        perimeterResult.getActivatedNetworkActions().forEach(networkAction -> networkAction.apply(network));
        perimeterResult.getActivatedRangeActions().forEach(rangeAction -> rangeAction.apply(network, perimeterResult.getOptimizedSetPoint(rangeAction)));
    }

    /**
     * This method applies range action results on the network, for range actions that are curative
     * It is used for second preventive optimization along with 1st preventive results in order to keep the result
     * of 1st preventive for range actions that are both preventive and curative
     */
    static void applyPreventiveResultsForCurativeRangeActions(Network network, PerimeterResult perimeterResult, Crac crac) {
        perimeterResult.getActivatedRangeActions().stream()
                .filter(rangeAction -> isRangeActionCurative(rangeAction, crac))
                .forEach(rangeAction -> rangeAction.apply(network, perimeterResult.getOptimizedSetPoint(rangeAction)));
    }

    private static LinearOptimizerParameters.LinearOptimizerParametersBuilder basicLinearOptimizerBuilder(RaoParameters raoParameters) {
        LinearOptimizerParameters.LinearOptimizerParametersBuilder builder = LinearOptimizerParameters.create()
                .withObjectiveFunction(raoParameters.getObjectiveFunction())
                .withPstSensitivityThreshold(raoParameters.getPstSensitivityThreshold());
        if (raoParameters.getObjectiveFunction() == RaoParameters.ObjectiveFunction.MAX_MIN_MARGIN_IN_AMPERE
                || raoParameters.getObjectiveFunction() == RaoParameters.ObjectiveFunction.MAX_MIN_MARGIN_IN_MEGAWATT) {
            builder.withMaxMinMarginParameters(new MaxMinMarginParameters(raoParameters.getPstPenaltyCost()));
        } else if (raoParameters.getObjectiveFunction() == RaoParameters.ObjectiveFunction.MAX_MIN_RELATIVE_MARGIN_IN_AMPERE
                || raoParameters.getObjectiveFunction() == RaoParameters.ObjectiveFunction.MAX_MIN_RELATIVE_MARGIN_IN_MEGAWATT) {
            MaxMinRelativeMarginParameters maxMinRelativeMarginParameters = new MaxMinRelativeMarginParameters(
                    raoParameters.getPstPenaltyCost(),
                    raoParameters.getNegativeMarginObjectiveCoefficient(),
                    raoParameters.getPtdfSumLowerBound());
            builder.withMaxMinRelativeMarginParameters(maxMinRelativeMarginParameters);
        } else {
            throw new FaraoException(String.format("Unhandled objective function %s", raoParameters.getObjectiveFunction()));
        }

        if (raoParameters.getMnecViolationCost() != 0) {
            MnecParameters mnecParameters = new MnecParameters(
                    raoParameters.getMnecAcceptableMarginDiminution(),
                    raoParameters.getMnecViolationCost(),
                    raoParameters.getMnecConstraintAdjustmentCoefficient());
            builder.withMnecParameters(mnecParameters);
        }

        if (raoParameters.isRaoWithLoopFlowLimitation()) {
            LoopFlowParameters loopFlowParameters = new LoopFlowParameters(
                    raoParameters.getLoopFlowApproximationLevel(),
                    raoParameters.getLoopFlowAcceptableAugmentation(),
                    raoParameters.getLoopFlowViolationCost(),
                    raoParameters.getLoopFlowConstraintAdjustmentCoefficient());
            builder.withLoopFlowParameters(loopFlowParameters);
        }
        return builder;
    }

    static LinearOptimizerParameters createPreventiveLinearOptimizerParameters(RaoParameters raoParameters) {
        return basicLinearOptimizerBuilder(raoParameters).build();
    }

    static LinearOptimizerParameters createCurativeLinearOptimizerParameters(RaoParameters raoParameters, StateTree stateTree, Set<FlowCnec> cnecs) {
        LinearOptimizerParameters.LinearOptimizerParametersBuilder builder = basicLinearOptimizerBuilder(raoParameters);
        SearchTreeRaoParameters parameters = raoParameters.getExtension(SearchTreeRaoParameters.class);
        if (parameters != null && !parameters.getCurativeRaoOptimizeOperatorsNotSharingCras()) {
            UnoptimizedCnecParameters unoptimizedCnecParameters = new UnoptimizedCnecParameters(
                    stateTree.getOperatorsNotSharingCras(),
                    getLargestCnecThreshold(cnecs));
            builder.withUnoptimizedCnecParameters(unoptimizedCnecParameters);
        }
        return builder.build();
    }

    static double getLargestCnecThreshold(Set<FlowCnec> flowCnecs) {
        double max = 0;
        for (FlowCnec flowCnec : flowCnecs) {
            if (flowCnec.isOptimized()) {
                Optional<Double> minFlow = flowCnec.getLowerBound(Side.LEFT, MEGAWATT);
                if (minFlow.isPresent() && Math.abs(minFlow.get()) > max) {
                    max = Math.abs(minFlow.get());
                }
                Optional<Double> maxFlow = flowCnec.getUpperBound(Side.LEFT, MEGAWATT);
                if (maxFlow.isPresent() && Math.abs(maxFlow.get()) > max) {
                    max = Math.abs(maxFlow.get());
                }
            }
        }
        return max;
    }

    CompletableFuture<RaoResult> optimizeOneStateOnly(RaoInput raoInput, RaoParameters raoParameters) {
        Set<FlowCnec> perimeterCnecs = computePerimeterCnecs(raoInput.getCrac(), raoInput.getPerimeter());
        TreeParameters treeParameters = raoInput.getOptimizedState().equals(raoInput.getCrac().getPreventiveState()) ?
                TreeParameters.buildForPreventivePerimeter(raoParameters.getExtension(SearchTreeRaoParameters.class)) :
                TreeParameters.buildForCurativePerimeter(raoParameters.getExtension(SearchTreeRaoParameters.class), -Double.MAX_VALUE);
        LinearOptimizerParameters linearOptimizerParameters = createCurativeLinearOptimizerParameters(raoParameters, stateTree, perimeterCnecs);

        PrePerimeterSensitivityAnalysis prePerimeterSensitivityAnalysis = new PrePerimeterSensitivityAnalysis(
                raoInput.getCrac().getRangeActions(raoInput.getOptimizedState(), UsageMethod.AVAILABLE),
                perimeterCnecs,
                toolProvider,
                raoParameters
        );
        PrePerimeterResult prePerimeterResult = prePerimeterSensitivityAnalysis.run(raoInput.getNetwork());

        SearchTreeInput searchTreeInput = buildSearchTreeInput(
                raoInput.getCrac(),
                raoInput.getNetwork(),
                raoInput.getOptimizedState(),
                raoInput.getPerimeter(),
                prePerimeterResult,
                prePerimeterResult,
                treeParameters,
                raoParameters,
                linearOptimizerParameters,
                toolProvider,
                false,
                null
        );

        OptimizationResult optimizationResult = new SearchTree().run(searchTreeInput, treeParameters, linearOptimizerParameters).join();

        optimizationResult.getRangeActions().forEach(rangeAction -> rangeAction.apply(raoInput.getNetwork(), optimizationResult.getOptimizedSetPoint(rangeAction)));
        optimizationResult.getActivatedNetworkActions().forEach(networkAction -> networkAction.apply(raoInput.getNetwork()));

        return CompletableFuture.completedFuture(new OneStateOnlyRaoOutput(raoInput.getOptimizedState(), prePerimeterResult, optimizationResult));
    }

    private CompletableFuture<RaoResult> optimizePreventivePerimeter(RaoInput raoInput, RaoParameters raoParameters, PrePerimeterResult prePerimeterResult) {
        TreeParameters preventiveTreeParameters = TreeParameters.buildForPreventivePerimeter(raoParameters.getExtension(SearchTreeRaoParameters.class));
        LinearOptimizerParameters linearOptimizerParameters = createPreventiveLinearOptimizerParameters(raoParameters);
        SearchTreeInput searchTreeInput = buildSearchTreeInput(
                raoInput.getCrac(),
                raoInput.getNetwork(),
                raoInput.getCrac().getPreventiveState(),
                stateTree.getPerimeter(raoInput.getCrac().getPreventiveState()),
                prePerimeterResult,
                prePerimeterResult,
                preventiveTreeParameters,
                raoParameters,
                linearOptimizerParameters,
                toolProvider,
                false,
                null
        );

        OptimizationResult perimeterResult = new SearchTree().run(searchTreeInput, preventiveTreeParameters, linearOptimizerParameters).join();

        perimeterResult.getRangeActions().forEach(rangeAction -> rangeAction.apply(raoInput.getNetwork(), perimeterResult.getOptimizedSetPoint(rangeAction)));
        perimeterResult.getActivatedNetworkActions().forEach(networkAction -> networkAction.apply(raoInput.getNetwork()));

        return CompletableFuture.completedFuture(new OneStateOnlyRaoOutput(raoInput.getCrac().getPreventiveState(), prePerimeterResult, perimeterResult));
    }

    private Map<State, OptimizationResult> optimizeCurativePerimeters(RaoInput raoInput,
                                                                      RaoParameters raoParameters,
                                                                      TreeParameters curativeTreeParameters,
                                                                      Network network,
                                                                      PrePerimeterResult initialSensitivityOutput,
                                                                      PrePerimeterResult prePerimeterSensitivityOutput) {
        Map<State, OptimizationResult> curativeResults = new ConcurrentHashMap<>();
        network.getVariantManager().setWorkingVariant(PREVENTIVE_STATE);
        network.getVariantManager().cloneVariant(PREVENTIVE_STATE, CURATIVE_STATE);
        network.getVariantManager().setWorkingVariant(CURATIVE_STATE);
        try (FaraoNetworkPool networkPool = new FaraoNetworkPool(network, CURATIVE_STATE, raoParameters.getPerimetersInParallel())) {
            stateTree.getOptimizedStates().forEach(optimizedState -> {
                if (!optimizedState.equals(raoInput.getCrac().getPreventiveState())) {
                    networkPool.submit(() -> {
                        try {
                            LOGGER.info("Optimizing curative state {}.", optimizedState.getId());
                            Network networkClone = networkPool.getAvailableNetwork();
                            Set<FlowCnec> cnecs = computePerimeterCnecs(raoInput.getCrac(), stateTree.getPerimeter(optimizedState));
                            LinearOptimizerParameters linearOptimizerParameters = createCurativeLinearOptimizerParameters(raoParameters, stateTree, cnecs);

                            SearchTreeInput searchTreeInput = buildSearchTreeInput(
                                    raoInput.getCrac(),
                                    raoInput.getNetwork(),
                                    optimizedState,
                                    stateTree.getPerimeter(optimizedState),
                                    initialSensitivityOutput,
                                    prePerimeterSensitivityOutput,
                                    curativeTreeParameters,
                                    raoParameters,
                                    linearOptimizerParameters,
                                    toolProvider,
                                    false,
                                    null
                            );

                            OptimizationResult curativeResult = new SearchTree().run(searchTreeInput, curativeTreeParameters, linearOptimizerParameters).join();
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

    static SearchTreeInput buildSearchTreeInput(Crac crac,
                                                Network network,
                                                State optimizedState,
                                                Set<State> perimeter,
                                                PrePerimeterResult initialOutput,
                                                PrePerimeterResult prePerimeterOutput,
                                                TreeParameters treeParameters,
                                                RaoParameters raoParameters,
                                                LinearOptimizerParameters linearOptimizerParameters,
                                                ToolProvider toolProvider,
                                                boolean isSecondPreventiveRao,
                                                AppliedRemedialActions appliedRemedialActions) {
        if (isSecondPreventiveRao) {
            Objects.requireNonNull(appliedRemedialActions);
        }
        SearchTreeInput searchTreeInput = new SearchTreeInput();

        searchTreeInput.setNetwork(network);
        Set<FlowCnec> cnecs;
        cnecs = isSecondPreventiveRao ? crac.getFlowCnecs() : computePerimeterCnecs(crac, perimeter);
        searchTreeInput.setFlowCnecs(cnecs);
        searchTreeInput.setNetworkActions(crac.getNetworkActions(optimizedState, UsageMethod.AVAILABLE));

        Set<RangeAction> rangeActions = crac.getRangeActions(optimizedState, UsageMethod.AVAILABLE);
        removeRangeActionsWithWrongInitialSetpoint(rangeActions, prePerimeterOutput);
        removeAlignedRangeActionsWithDifferentInitialSetpoints(rangeActions, prePerimeterOutput);
        if (isSecondPreventiveRao) {
            removeRangeActionsExcludedFromSecondPreventive(rangeActions, crac);
        }
        searchTreeInput.setRangeActions(rangeActions);

        ObjectiveFunction objectiveFunction = createObjectiveFunction(
                cnecs,
                initialOutput,
                prePerimeterOutput,
                raoParameters,
                linearOptimizerParameters,
                toolProvider
        );
        searchTreeInput.setObjectiveFunction(objectiveFunction);
        searchTreeInput.setIteratingLinearOptimizer(new IteratingLinearOptimizer(objectiveFunction, raoParameters.getMaxIterations()));

        searchTreeInput.setSearchTreeProblem(new SearchTreeProblem(
                initialOutput,
                prePerimeterOutput,
                prePerimeterOutput,
                cnecs,
                toolProvider.getLoopFlowCnecs(cnecs),
                linearOptimizerParameters
        ));

        SearchTreeComputer.SearchTreeComputerBuilder searchTreeComputerBuilder = SearchTreeComputer.create()
                .withToolProvider(toolProvider)
                .withCnecs(cnecs)
                .withAppliedRemedialActions(appliedRemedialActions);
        if (linearOptimizerParameters.hasRelativeMargins()) {
            searchTreeComputerBuilder.withPtdfsResults(initialOutput);
        }
        searchTreeInput.setSearchTreeComputer(searchTreeComputerBuilder.build());

        searchTreeInput.setSearchTreeBloomer(new SearchTreeBloomer(
                network,
                prePerimeterOutput,
                treeParameters.getMaxRa(),
                treeParameters.getMaxTopoPerTso(),
                treeParameters.getMaxRaPerTso(),
                treeParameters.getSkipNetworkActionsFarFromMostLimitingElement(),
                raoParameters.getExtension(SearchTreeRaoParameters.class).getMaxNumberOfBoundariesForSkippingNetworkActions()
        ));
        searchTreeInput.setPrePerimeterOutput(prePerimeterOutput);

        return searchTreeInput;
    }

    static ObjectiveFunction createObjectiveFunction(Set<FlowCnec> cnecs,
                                                     FlowResult initialFlowResult,
                                                     FlowResult prePerimeterFlowResult,
                                                     RaoParameters raoParameters,
                                                     LinearOptimizerParameters linearOptimizerParameters,
                                                     ToolProvider toolProvider) {
        ObjectiveFunction.ObjectiveFunctionBuilder objectiveFunctionBuilder = ObjectiveFunction.create();
        ObjectiveFunctionHelper.addMinMarginObjectiveFunction(cnecs, prePerimeterFlowResult, objectiveFunctionBuilder, linearOptimizerParameters);
        // TODO : replace this test with a dedicated parameter in RaoParameters
        if (raoParameters.getMnecParameters().getMnecViolationCost() != 0) {
            objectiveFunctionBuilder.withVirtualCostEvaluator(new MnecViolationCostEvaluator(
                    cnecs.stream().filter(Cnec::isMonitored).collect(Collectors.toSet()),
                    initialFlowResult,
                    raoParameters.getMnecParameters()
            ));
        }
        if (raoParameters.isRaoWithLoopFlowLimitation()) {
            objectiveFunctionBuilder.withVirtualCostEvaluator(new LoopFlowViolationCostEvaluator(
                    toolProvider.getLoopFlowCnecs(cnecs),
                    initialFlowResult,
                    raoParameters.getLoopFlowParameters()
            ));
        }
        objectiveFunctionBuilder.withVirtualCostEvaluator(new SensitivityFallbackOvercostEvaluator(
                raoParameters.getFallbackOverCost()
        ));
        return objectiveFunctionBuilder.build();
    }

    public static Set<FlowCnec> computePerimeterCnecs(Crac crac, Set<State> perimeter) {
        if (perimeter != null) {
            Set<FlowCnec> cnecs = new HashSet<>();
            perimeter.forEach(state -> cnecs.addAll(crac.getFlowCnecs(state)));
            return cnecs;
        } else {
            return crac.getFlowCnecs();
        }
    }

    /**
     * If range action's initial setpoint does not respect its allowed range, this function filters it out
     */
    static void removeRangeActionsWithWrongInitialSetpoint(Set<RangeAction> rangeActions, RangeActionResult prePerimeterSetPoints) {
        //a temp set is needed to avoid ConcurrentModificationExceptions when trying to remove a range action from a set we are looping on
        Set<RangeAction> rangeActionsToRemove = new HashSet<>();
        for (RangeAction rangeAction : rangeActions) {
            double preperimeterSetPoint = prePerimeterSetPoints.getOptimizedSetPoint(rangeAction);
            double minSetPoint = rangeAction.getMinAdmissibleSetpoint(preperimeterSetPoint);
            double maxSetPoint = rangeAction.getMaxAdmissibleSetpoint(preperimeterSetPoint);
            if (preperimeterSetPoint < minSetPoint || preperimeterSetPoint > maxSetPoint) {
                LOGGER.warn("Range action {} has an initial setpoint of {} that does not respect its allowed range [{} {}]. It will be filtered out of the linear problem.",
                        rangeAction.getId(), preperimeterSetPoint, minSetPoint, maxSetPoint);
                rangeActionsToRemove.add(rangeAction);
            }
        }
        rangeActionsToRemove.forEach(rangeActions::remove);
    }

    /**
     * If aligned range actionsé initial setpoint are different, this function filters them out
     */
    static void removeAlignedRangeActionsWithDifferentInitialSetpoints(Set<RangeAction> rangeActions, RangeActionResult prePerimeterSetPoints) {
        Set<String> groups = rangeActions.stream().map(RangeAction::getGroupId)
                .filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet());
        for (String group : groups) {
            Set<RangeAction> groupRangeActions = rangeActions.stream().filter(rangeAction -> rangeAction.getGroupId().isPresent() && rangeAction.getGroupId().get().equals(group)).collect(Collectors.toSet());
            double preperimeterSetPoint = prePerimeterSetPoints.getOptimizedSetPoint(groupRangeActions.iterator().next());
            if (groupRangeActions.stream().anyMatch(rangeAction -> Math.abs(prePerimeterSetPoints.getOptimizedSetPoint(rangeAction) - preperimeterSetPoint) > 1e-6)) {
                LOGGER.warn("Range actions of group {} do not have the same initial setpoint. They will be filtered out of the linear problem.", group);
                rangeActions.removeAll(groupRangeActions);
            }
        }
    }

    // ========================================
    // region Second preventive RAO
    // ========================================

    /**
     * This function decides if a 2nd preventive RAO should be run. It checks the user parameter first, then takes the
     * decision depending on the curative RAO results and the curative RAO stop criterion.
     */
    static boolean shouldRunSecondPreventiveRao(RaoParameters raoParameters, Map<State, OptimizationResult> curativeRaoResults) {
        if (raoParameters.getExtension(SearchTreeRaoParameters.class) == null
                || !raoParameters.getExtension(SearchTreeRaoParameters.class).getWithSecondPreventiveOptimization()) {
            return false;
        }
        SearchTreeRaoParameters.CurativeRaoStopCriterion curativeRaoStopCriterion = raoParameters.getExtension(SearchTreeRaoParameters.class).getCurativeRaoStopCriterion();
        switch (curativeRaoStopCriterion) {
            case MIN_OBJECTIVE:
                // Run 2nd preventive RAO in all cases
                return true;
            case SECURE:
                // Run 2nd preventive RAO if one perimeter of the curative optimization is unsecure
                return curativeRaoResults.values().stream().anyMatch(optimizationResult -> optimizationResult.getFunctionalCost() >= 0);
            case PREVENTIVE_OBJECTIVE:
                // TODO : Run 2nd preventive RAO if one perimeter of the curative optimization has a worst cost than the preventive perimeter
                return false;
            case PREVENTIVE_OBJECTIVE_AND_SECURE:
                // TODO : Run 2nd preventive RAO if one perimeter of the curative optimization has a worst cost than the preventive perimeter or is unsecure
                return false;
            default:
                throw new FaraoException(String.format("Unknown curative RAO stop criterion: %s", curativeRaoStopCriterion));
        }
    }

    /**
     * Main function to run 2nd preventive RAO
     * Using 1st preventive and curative results, it ets up network and range action contexts, then calls the optimizer
     * It finally merges the three results into one RaoResult object
     */
    private RaoResult runSecondPreventiveRao(RaoInput raoInput,
                                             RaoParameters parameters,
                                             PrePerimeterSensitivityAnalysis prePerimeterSensitivityAnalysis,
                                             PrePerimeterResult initialOutput,
                                             PerimeterResult firstPreventiveResult,
                                             PrePerimeterResult preCurativeSensitivityAnalysisOutput,
                                             Map<State, OptimizationResult> curativeResults) {
        LOGGER.info("Second preventive perimeter optimization [start]");
        Network network = raoInput.getNetwork();
        // Go back to the initial state of the network, saved in the SECOND_PREVENTIVE_STATE variant
        network.getVariantManager().setWorkingVariant(SECOND_PREVENTIVE_STATE);
        // Apply 1st preventive results for range actions that are both preventive and curative. This way we are sure
        // that the optimal setpoints of the curative results stay coherent with their allowed range and close to
        // optimality in their perimeters. These range actions will be excluded from 2nd preventive RAO.
        applyPreventiveResultsForCurativeRangeActions(network, firstPreventiveResult, raoInput.getCrac());
        // Get the applied remedial actions for every curative perimeter
        AppliedRemedialActions appliedRemedialActions = getAppliedRemedialActionsInCurative(curativeResults, preCurativeSensitivityAnalysisOutput);
        // Run a first sensitivity computation using initial network and applied CRAs
        PrePerimeterResult sensiWithCurativeRemedialActions = prePerimeterSensitivityAnalysis.run(network, appliedRemedialActions);
        // Run second preventive RAO
        PerimeterResult secondPreventiveResult = optimizeSecondPreventivePerimeter(raoInput, parameters, sensiWithCurativeRemedialActions, appliedRemedialActions)
                .join().getPerimeterResult(OptimizationState.AFTER_CRA, raoInput.getCrac().getPreventiveState());
        // Re-run sensitivity computation based on PRAs without CRAs, to access OptimizationState.AFTER_PRA results
        PrePerimeterResult updatedPreCurativeSensitivityAnalysisOutput = prePerimeterSensitivityAnalysis.runBasedOn(network, secondPreventiveResult);
        LOGGER.info("Second preventive perimeter optimization [end]");

        LOGGER.info("Merging first, second preventive and curative RAO results.");
        Set<RemedialAction<?>> remedialActionsExcluded = new HashSet<>(getRangeActionsExcludedFromSecondPreventive(raoInput.getCrac()));
        return new SecondPreventiveAndCurativesRaoOutput(initialOutput, firstPreventiveResult, secondPreventiveResult, updatedPreCurativeSensitivityAnalysisOutput, curativeResults, remedialActionsExcluded);
    }

    static AppliedRemedialActions getAppliedRemedialActionsInCurative(Map<State, OptimizationResult> curativeResults, PrePerimeterResult preCurativeResults) {
        AppliedRemedialActions appliedRemedialActions = new AppliedRemedialActions();
        curativeResults.forEach((state, optimizationResult) -> appliedRemedialActions.addAppliedNetworkActions(state, optimizationResult.getActivatedNetworkActions()));
        // Add all range actions that were activated in curative, even if they are also preventive (they will be excluded from 2nd preventive)
        curativeResults.forEach((state, optimizationResult) ->
                (new PerimeterOutput(preCurativeResults, optimizationResult)).getActivatedRangeActions()
                        .forEach(rangeAction -> appliedRemedialActions.addAppliedRangeAction(state, rangeAction, optimizationResult.getOptimizedSetPoint(rangeAction)))
        );
        return appliedRemedialActions;
    }

    private CompletableFuture<RaoResult> optimizeSecondPreventivePerimeter(RaoInput raoInput, RaoParameters raoParameters, PrePerimeterResult prePerimeterResult, AppliedRemedialActions appliedRemedialActions) {
        TreeParameters preventiveTreeParameters = TreeParameters.buildForPreventivePerimeter(raoParameters.getExtension(SearchTreeRaoParameters.class));
        LinearOptimizerParameters linearOptimizerParameters = createPreventiveLinearOptimizerParameters(raoParameters);
        SearchTreeInput searchTreeInput = buildSearchTreeInput(
                raoInput.getCrac(),
                raoInput.getNetwork(),
                raoInput.getCrac().getPreventiveState(),
                stateTree.getPerimeter(raoInput.getCrac().getPreventiveState()),
                prePerimeterResult,
                prePerimeterResult,
                preventiveTreeParameters,
                raoParameters,
                linearOptimizerParameters,
                toolProvider,
                true,
                appliedRemedialActions
        );

        OptimizationResult perimeterResult = new SearchTree().run(searchTreeInput, preventiveTreeParameters, linearOptimizerParameters).join();

        perimeterResult.getRangeActions().forEach(rangeAction -> rangeAction.apply(raoInput.getNetwork(), perimeterResult.getOptimizedSetPoint(rangeAction)));
        perimeterResult.getActivatedNetworkActions().forEach(networkAction -> networkAction.apply(raoInput.getNetwork()));

        return CompletableFuture.completedFuture(new OneStateOnlyRaoOutput(raoInput.getCrac().getPreventiveState(), prePerimeterResult, perimeterResult));
    }

    /**
     * For second preventive optimization, we shouldn't re-optimize range actions that are also curative
     */
    static void removeRangeActionsExcludedFromSecondPreventive(Set<RangeAction> rangeActions, Crac crac) {
        Set<RangeAction> rangeActionsToRemove = new HashSet<>(rangeActions);
        rangeActionsToRemove.retainAll(getRangeActionsExcludedFromSecondPreventive(crac));
        rangeActionsToRemove.forEach(rangeAction ->
                LOGGER.info("Range action {} will not be considered in 2nd preventive RAO as it is also curative (or its network element has an associated CRA)", rangeAction.getId())
        );
        rangeActionsToRemove.forEach(rangeActions::remove);
    }

    /**
     * Returns the set of range actions that were excluded from the 2nd preventive RAO.
     * It consists of range actions that are both preventive and curative, since they mustn't be re-optimized during 2nd preventive.
     */
    static Set<RangeAction> getRangeActionsExcludedFromSecondPreventive(Crac crac) {
        // TODO :  we can avoid excluding (PRA+CRA) range actions that were not activated in any curative perimeter
        return crac.getRangeActions().stream().filter(rangeAction -> isRangeActionPreventive(rangeAction, crac) && isRangeActionCurative(rangeAction, crac)).collect(Collectors.toSet());
    }

    static boolean isRangeActionPreventive(RangeAction rangeAction, Crac crac) {
        return isRangeActionAvailableInState(rangeAction, crac.getPreventiveState(), crac);
    }

    static boolean isRangeActionCurative(RangeAction rangeAction, Crac crac) {
        return crac.getStates().stream()
                .filter(state -> !state.equals(crac.getPreventiveState()))
                .anyMatch(state -> isRangeActionAvailableInState(rangeAction, state, crac));
    }

    static boolean isRangeActionAvailableInState(RangeAction rangeAction, State state, Crac crac) {
        Set<RangeAction> rangeActionsForState = crac.getRangeActions(state, UsageMethod.AVAILABLE);
        if (rangeActionsForState.contains(rangeAction)) {
            return true;
        } else {
            return rangeActionsForState.stream()
                    .anyMatch(otherRangeAction -> otherRangeAction.getNetworkElements().equals(rangeAction.getNetworkElements()));
        }
    }

    // ========================================
    // endregion
    // ========================================
}
