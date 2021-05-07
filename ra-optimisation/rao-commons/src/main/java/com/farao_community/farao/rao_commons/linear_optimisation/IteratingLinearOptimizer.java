/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.rao_commons.linear_optimisation;

import com.farao_community.farao.rao_api.results.*;
import com.farao_community.farao.rao_commons.adapter.BranchResultAdapter;
import com.farao_community.farao.rao_commons.adapter.SensitivityResultAdapter;
import com.farao_community.farao.rao_commons.objective_function_evaluator.ObjectiveFunction;
import com.farao_community.farao.sensitivity_analysis.SensitivityAnalysisException;
import com.farao_community.farao.sensitivity_analysis.SystematicSensitivityInterface;
import com.farao_community.farao.sensitivity_analysis.SystematicSensitivityResult;
import com.powsybl.iidm.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class IteratingLinearOptimizer {
    protected static final Logger LOGGER = LoggerFactory.getLogger(IteratingLinearOptimizer.class);
    private static final String BETTER_RESULT = "Iteration {} - Better solution found with a functional cost of {} (optimisation criterion : {})";
    private static final String WORSE_RESULT = "Iteration {} - Linear Optimization found a worse result than previous iteration, with a functional cost from {} to {} (optimisation criterion : from {} to {})";
    private static final String LINEAR_OPTIMIZATION_FAILED = "Linear optimization failed at iteration {}";

    private final ObjectiveFunction objectiveFunction;
    private final SensitivityResultAdapter sensitivityResultAdapter;
    private final SystematicSensitivityInterface systematicSensitivityInterface;
    private final int maxIterations;

    public IteratingLinearOptimizer(ObjectiveFunction objectiveFunction,
                                    SystematicSensitivityInterface systematicSensitivityInterface,
                                    SensitivityResultAdapter sensitivityResultAdapter,
                                    int maxIterations) {
        this.objectiveFunction = objectiveFunction;
        this.sensitivityResultAdapter = sensitivityResultAdapter;
        this.systematicSensitivityInterface = systematicSensitivityInterface;
        this.maxIterations = maxIterations;
    }

    public LinearOptimizationResult optimize(LinearProblem linearProblem,
                                             Network network,
                                             BranchResult initialBranchResult,
                                             SensitivityResult initialSensitivityResult,
                                             RangeActionResult initialRangeActionResult,
                                             BranchResultAdapter branchResultAdapter) {
        IteratingLinearOptimizerResult bestResult = createResult(initialBranchResult, initialSensitivityResult, initialRangeActionResult, 0);

        for (int iteration = 0; iteration <= maxIterations; iteration++) {
            solveLinearProblem(linearProblem, iteration);
            if (linearProblem.getStatus() != LinearProblemStatus.OPTIMAL) {
                LOGGER.error(LINEAR_OPTIMIZATION_FAILED, iteration);
                if (iteration == 0) {
                    return new FailedLinearOptimizationResult();
                }
                bestResult.setStatus(LinearProblemStatus.FEASIBLE);
                return bestResult;
            }

            RangeActionResult currentRangeActionResult = roundResult(linearProblem.getResults(), network, bestResult);

            if (!hasRemedialActionsChanged(currentRangeActionResult, bestResult)) {
                // If the solution has not changed, no need to run a new sensitivity computation and iteration can stop
                LOGGER.info("Iteration {} - same results as previous iterations, optimal solution found", iteration);
                return bestResult;
            }

            SystematicSensitivityResult sensi;
            try {
                sensi = applyRangeActionsAndRunSensitivityAnalysis(currentRangeActionResult, network, iteration);
            } catch (SensitivityAnalysisException e) {
                bestResult.setStatus(LinearProblemStatus.SENSITIVITY_COMPUTATION_FAILED);
                return bestResult;
            }

            IteratingLinearOptimizerResult currentResult = createResult(branchResultAdapter, sensi, currentRangeActionResult, iteration);
            if (currentResult.getCost() >= bestResult.getCost()) {
                logWorseResult(iteration, bestResult, currentResult);
                return bestResult;
            }

            logBetterResult(iteration, currentResult);
            bestResult = currentResult;
            linearProblem.update(bestResult.getBranchResult(), bestResult.getSensitivityResult());
        }
        bestResult.setStatus(LinearProblemStatus.MAX_ITERATION_REACHED);
        return bestResult;
    }

    private static void solveLinearProblem(LinearProblem linearProblem, int iteration) {
        LOGGER.debug("Iteration {} - linear optimization [start]", iteration);
        linearProblem.solve();
        LOGGER.debug("Iteration {} - linear optimization [end]", iteration);
    }

    static boolean hasRemedialActionsChanged(RangeActionResult newRangeActionResult, RangeActionResult oldRangeActionResult) {
        return !newRangeActionResult.getOptimizedSetPoints().equals(oldRangeActionResult.getOptimizedSetPoints());
    }

    private static void logBetterResult(int iteration, ObjectiveFunctionResult currentObjectiveFunctionResult) {
        LOGGER.info(
                BETTER_RESULT,
                iteration,
                currentObjectiveFunctionResult.getFunctionalCost(),
                currentObjectiveFunctionResult.getCost());
    }

    private static void logWorseResult(int iteration, ObjectiveFunctionResult bestResult, ObjectiveFunctionResult currentResult) {
        LOGGER.info(
                WORSE_RESULT,
                iteration,
                bestResult.getFunctionalCost(),
                currentResult.getFunctionalCost(),
                bestResult.getCost(),
                currentResult.getCost());
    }

    private SystematicSensitivityResult applyRangeActionsAndRunSensitivityAnalysis(RangeActionResult rangeActionResult,
                                                                                   Network network,
                                                                                   int iteration) {
        rangeActionResult.getActivatedRangeActions().forEach(rangeAction ->
                rangeAction.apply(network, rangeActionResult.getOptimizedSetPoint(rangeAction)));

        LOGGER.debug("Iteration {} - systematic analysis [start]", iteration);
        try {
            SystematicSensitivityResult updatedSensiResult = systematicSensitivityInterface.run(network);
            LOGGER.debug("Iteration {} - systematic analysis [end]", iteration);
            return updatedSensiResult;
        } catch (SensitivityAnalysisException e) {
            LOGGER.error("Sensitivity computation failed at iteration {} on {} mode: {}", iteration, systematicSensitivityInterface.isFallback() ? "Fallback" : "Default", e.getMessage());
            throw e;
        }
    }

    private IteratingLinearOptimizerResult createResult(BranchResultAdapter branchResultAdapter,
                                                        SystematicSensitivityResult systematicSensitivityResult,
                                                        RangeActionResult rangeActionResult,
                                                        int nbOfIterations) {
        BranchResult branchResult = branchResultAdapter.getResult(systematicSensitivityResult);
        SensitivityResult sensitivityResult = sensitivityResultAdapter.getResult(systematicSensitivityResult);
        return createResult(branchResult, sensitivityResult, rangeActionResult, nbOfIterations);
    }

    private IteratingLinearOptimizerResult createResult(BranchResult branchResult,
                                                        SensitivityResult sensitivityResult,
                                                        RangeActionResult rangeActionResult,
                                                        int nbOfIterations) {
        return new IteratingLinearOptimizerResult(LinearProblemStatus.OPTIMAL, nbOfIterations, rangeActionResult, branchResult,
                objectiveFunction.evaluate(branchResult, sensitivityResult.getStatus()), sensitivityResult);
    }

    private RangeActionResult roundResult(RangeActionResult rangeActionResult, Network network, IteratingLinearOptimizerResult previousResult) {
        return BestTapFinder.find(
                rangeActionResult,
                network,
                previousResult.getObjectiveFunctionResult().getMostLimitingElements(10),
                previousResult.getBranchResult(),
                previousResult.getSensitivityResult()
        );
    }
}