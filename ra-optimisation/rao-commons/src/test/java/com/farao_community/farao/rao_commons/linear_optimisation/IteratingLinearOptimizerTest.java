/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.rao_commons.linear_optimisation;

import com.farao_community.farao.data.crac_api.range_action.RangeAction;
import com.farao_community.farao.rao_api.results.*;
import com.farao_community.farao.rao_commons.SensitivityComputer;
import com.farao_community.farao.rao_commons.adapter.BranchResultAdapter;
import com.farao_community.farao.rao_commons.adapter.SensitivityResultAdapter;
import com.farao_community.farao.rao_commons.objective_function_evaluator.ObjectiveFunction;
import com.farao_community.farao.rao_commons.result.RangeActionResultImpl;
import com.farao_community.farao.sensitivity_analysis.SensitivityAnalysisException;
import com.farao_community.farao.sensitivity_analysis.SystematicSensitivityInterface;
import com.farao_community.farao.sensitivity_analysis.SystematicSensitivityResult;
import com.powsybl.iidm.network.Network;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class IteratingLinearOptimizerTest {
    private static final double DOUBLE_TOLERANCE = 0.1;

    private RangeAction rangeAction;

    private ObjectiveFunction objectiveFunction;
    private SystematicSensitivityInterface systematicSensitivityInterface;

    private LinearProblem linearProblem;
    private Network network;
    private FlowResult flowResult;
    private SensitivityResult sensitivityResult;
    private RangeActionResult rangeActionResult;
    private BranchResultAdapter branchResultAdapter;
    private SensitivityComputer sensitivityComputer;
    private IteratingLinearOptimizer optimizer;

    @Before
    public void setUp() {
        rangeAction = Mockito.mock(RangeAction.class);

        objectiveFunction = Mockito.mock(ObjectiveFunction.class);
        systematicSensitivityInterface = Mockito.mock(SystematicSensitivityInterface.class);
        SensitivityResultAdapter sensitivityResultAdapter = Mockito.mock(SensitivityResultAdapter.class);
        optimizer = new IteratingLinearOptimizer(
            objectiveFunction,
            5
        );

        linearProblem = Mockito.mock(LinearProblem.class);
        network = Mockito.mock(Network.class);
        this.flowResult = Mockito.mock(FlowResult.class);
        sensitivityResult = Mockito.mock(SensitivityResult.class);
        rangeActionResult = new RangeActionResultImpl(Map.of(rangeAction, 0.));
        branchResultAdapter = Mockito.mock(BranchResultAdapter.class);
        sensitivityComputer = Mockito.mock(SensitivityComputer.class);

        SystematicSensitivityResult sensi = Mockito.mock(SystematicSensitivityResult.class, "only sensi computation");
        when(systematicSensitivityInterface.run(network)).thenReturn(sensi);
        FlowResult flowResult = Mockito.mock(FlowResult.class);
        when(branchResultAdapter.getResult(sensi)).thenReturn(flowResult);
        when(sensitivityComputer.getBranchResult()).thenReturn(flowResult);
        when(sensitivityComputer.getSensitivityResult()).thenReturn(sensitivityResult);
        SensitivityResult sensitivityResult = Mockito.mock(SensitivityResult.class);
        when(sensitivityResult.getSensitivityStatus()).thenReturn(SensitivityStatus.DEFAULT);
        when(sensitivityResultAdapter.getResult(sensi)).thenReturn(sensitivityResult);

    }

    private void mockFunctionalCost(Double initialFunctionalCost, Double... iterationFunctionalCosts) {
        ObjectiveFunctionResult initialObjectiveFunctionResult = Mockito.mock(ObjectiveFunctionResult.class);
        when(initialObjectiveFunctionResult.getFunctionalCost()).thenReturn(initialFunctionalCost);
        if (iterationFunctionalCosts.length == 0) {
            when(objectiveFunction.evaluate(any(), any())).thenReturn(initialObjectiveFunctionResult);
        } else {
            ObjectiveFunctionResult[] objectiveFunctionResults = new ObjectiveFunctionResult[iterationFunctionalCosts.length];
            for (int i = 0; i < iterationFunctionalCosts.length; i++) {
                ObjectiveFunctionResult objectiveFunctionResult = Mockito.mock(ObjectiveFunctionResult.class);
                when(objectiveFunctionResult.getFunctionalCost()).thenReturn(iterationFunctionalCosts[i]);
                objectiveFunctionResults[i] = objectiveFunctionResult;
            }
            when(objectiveFunction.evaluate(any(), any())).thenReturn(
                    initialObjectiveFunctionResult,
                    objectiveFunctionResults
            );
        }
    }

    private void mockLinearProblem(List<LinearProblemStatus> statuses, List<Double> setPoints) {
        doAnswer(new Answer() {
            private int count = 0;
            public Object answer(InvocationOnMock invocation) {
                count += 1;
                if (statuses.get(count - 1) == LinearProblemStatus.OPTIMAL) {
                    when(linearProblem.getResults()).thenReturn(new RangeActionResultImpl(Map.of(rangeAction, setPoints.get(count - 1))));
                }
                when(linearProblem.getStatus()).thenReturn(statuses.get(count - 1));
                return statuses.get(count - 1);
            }
        }).when(linearProblem).solve();
    }

    private LinearOptimizationResult optimize() {
        return optimizer.optimize(
                linearProblem,
                network,
                flowResult,
                sensitivityResult,
                rangeActionResult,
                sensitivityComputer
        );
    }

    @Test
    public void firstOptimizationFails() {
        mockLinearProblem(List.of(LinearProblemStatus.INFEASIBLE), Collections.emptyList());
        mockFunctionalCost(100.);

        LinearOptimizationResult result = optimize();

        assertEquals(LinearProblemStatus.ABNORMAL, result.getStatus());
        assertTrue(result instanceof FailedLinearOptimizationResult);
    }

    @Test
    public void firstLinearProblemDoesNotChangeSetPoint() {
        mockLinearProblem(List.of(LinearProblemStatus.OPTIMAL), List.of(0.));
        mockFunctionalCost(100.);

        LinearOptimizationResult result = optimize();

        assertEquals(LinearProblemStatus.OPTIMAL, result.getStatus());
        assertEquals(0, ((IteratingLinearOptimizerResult) result).getNbOfIteration());
        assertEquals(100, result.getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(0, result.getOptimizedSetPoint(rangeAction), DOUBLE_TOLERANCE);
    }

    @Test
    public void secondLinearProblemDoesNotChangeSetPoint() {
        mockLinearProblem(Collections.nCopies(2, LinearProblemStatus.OPTIMAL), List.of(1., 1.));
        mockFunctionalCost(100., 50.);

        LinearOptimizationResult result = optimize();

        assertEquals(LinearProblemStatus.OPTIMAL, result.getStatus());
        assertEquals(1, ((IteratingLinearOptimizerResult) result).getNbOfIteration());
        assertEquals(50, result.getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(1, result.getOptimizedSetPoint(rangeAction), DOUBLE_TOLERANCE);
    }

    @Test
    public void firstLinearProblemChangesSetPointButWorsenFunctionalCost() {
        mockLinearProblem(List.of(LinearProblemStatus.OPTIMAL), List.of(1.));
        mockFunctionalCost(100., 140.);

        LinearOptimizationResult result = optimize();

        assertEquals(LinearProblemStatus.OPTIMAL, result.getStatus());
        assertEquals(0, ((IteratingLinearOptimizerResult) result).getNbOfIteration());
        assertEquals(100, result.getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(0, result.getOptimizedSetPoint(rangeAction), DOUBLE_TOLERANCE);
    }

    @Test
    public void reachMaxIterations() {
        mockLinearProblem(Collections.nCopies(5, LinearProblemStatus.OPTIMAL), List.of(1., 2., 3., 4., 5.));
        mockFunctionalCost(100., 90., 80., 70., 60., 50.);

        LinearOptimizationResult result = optimize();

        assertEquals(LinearProblemStatus.MAX_ITERATION_REACHED, result.getStatus());
        assertEquals(5, ((IteratingLinearOptimizerResult) result).getNbOfIteration());
        assertEquals(50, result.getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(5, result.getOptimizedSetPoint(rangeAction), DOUBLE_TOLERANCE);
    }

    @Test
    public void optimizeWithInfeasibility() {
        mockLinearProblem(List.of(LinearProblemStatus.OPTIMAL, LinearProblemStatus.INFEASIBLE), List.of(1.));
        mockFunctionalCost(100., 50.);

        LinearOptimizationResult result = optimize();

        assertEquals(LinearProblemStatus.FEASIBLE, result.getStatus());
        assertEquals(1, ((IteratingLinearOptimizerResult) result).getNbOfIteration());
        assertEquals(50, result.getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(1, result.getOptimizedSetPoint(rangeAction), DOUBLE_TOLERANCE);
    }

    @Test
    public void optimizeWithSensitivityComputationFailure() {
        Mockito.doAnswer(invocationOnMock -> {
            network = invocationOnMock.getArgument(0);
            throw new SensitivityAnalysisException("Sensi computation failed");
        }).when(sensitivityComputer).compute(network);
        mockLinearProblem(List.of(LinearProblemStatus.OPTIMAL), List.of(1.));
        mockFunctionalCost(100.);

        LinearOptimizationResult result = optimize();

        assertEquals(LinearProblemStatus.SENSITIVITY_COMPUTATION_FAILED, result.getStatus());
        assertEquals(0, ((IteratingLinearOptimizerResult) result).getNbOfIteration());
        assertEquals(100, result.getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(0, result.getOptimizedSetPoint(rangeAction), DOUBLE_TOLERANCE);

    }

    // TODO: check what to do with these commented tests
    /*@Test
    public void testRemoveRangeActionsIfMaxNumberReached() {
        PstRangeActionImpl rangeActionToRemove = new PstRangeActionImpl("PRA_PST_BE_2", "PRA_PST_BE_2", "BE", new NetworkElement("BBE2AA1  BBE3AA1  1"));
        rangeActionToRemove.addRange(new PstRangeImpl(5, 10, RangeType.ABSOLUTE, RangeDefinition.CENTERED_ON_ZERO));
        rangeActionToRemove.addUsageRule(new OnStateImpl(UsageMethod.AVAILABLE, crac.getPreventiveState()));
        ((SimpleCrac) crac).addRangeAction(rangeActionToRemove);
        Network network = iteratingLinearOptimizerInput.getNetwork();
        crac.desynchronize();
        crac.synchronize(network);

        Map<String, Integer> maxPstPerTso = new HashMap<>();
        maxPstPerTso.put("BE", 1);

        BranchCnec mostLimitingCnec = crac.getBranchCnec("NNL1AA1  NNL2AA1  1");
        SystematicSensitivityResult sensitivityResult = Mockito.mock(SystematicSensitivityResult.class);
        Mockito.when(sensitivityResult.getSensitivityOnFlow(crac.getRangeAction("PRA_PST_BE"), mostLimitingCnec)).thenReturn(5.);
        Mockito.when(sensitivityResult.getSensitivityOnFlow(crac.getRangeAction("PRA_PST_BE_2"), mostLimitingCnec)).thenReturn(1.);

        Map<RangeAction, Double> prePerimeterSetPoints = new HashMap<>();
        crac.getRangeActions().forEach(rangeAction -> prePerimeterSetPoints.put(rangeAction, 0.));

        Set<RangeAction> rangeActions = crac.getRangeActions();
        assertEquals(2, rangeActions.size());
        assertEquals(2, prePerimeterSetPoints.size());
        IteratingLinearOptimizer.removeRangeActionsIfMaxNumberReached(rangeActions, prePerimeterSetPoints, maxPstPerTso, mostLimitingCnec, sensitivityResult);

        assertEquals(1, rangeActions.size());
        assertTrue(rangeActions.contains(crac.getRangeAction("PRA_PST_BE")));
        assertFalse(rangeActions.contains(crac.getRangeAction("PRA_PST_BE_2")));

        assertEquals(1, prePerimeterSetPoints.size());
        assertTrue(prePerimeterSetPoints.containsKey(crac.getRangeAction("PRA_PST_BE")));
        assertFalse(prePerimeterSetPoints.containsKey(crac.getRangeAction("PRA_PST_BE_2")));
    }

    @Test
    public void testRemoveRangeActionsWithWrongInitialSetpoint() {
        PstRangeActionImpl rangeActionToRemove = new PstRangeActionImpl("PRA_PST_BE_2", "PRA_PST_BE_2", "BE", new NetworkElement("BBE2AA1  BBE3AA1  1"));
        rangeActionToRemove.addRange(new PstRangeImpl(5, 10, RangeType.ABSOLUTE, RangeDefinition.CENTERED_ON_ZERO));
        rangeActionToRemove.addUsageRule(new OnStateImpl(UsageMethod.AVAILABLE, crac.getPreventiveState()));
        ((SimpleCrac) crac).addRangeAction(rangeActionToRemove);
        Network network = iteratingLinearOptimizerInput.getNetwork();
        crac.desynchronize();
        crac.synchronize(network);

        Map<RangeAction, Double> initialSetpoints = new HashMap<>();
        Set<RangeAction> rangeActions = crac.getRangeActions();
        rangeActions.forEach(rangeAction -> initialSetpoints.put(rangeAction, 0.));
        assertEquals(2, rangeActions.size());
        assertEquals(2, initialSetpoints.size());
        IteratingLinearOptimizer.removeRangeActionsWithWrongInitialSetpoint(rangeActions, initialSetpoints, network);

        assertEquals(1, rangeActions.size());
        assertTrue(rangeActions.contains(crac.getRangeAction("PRA_PST_BE")));
        assertFalse(rangeActions.contains(crac.getRangeAction("PRA_PST_BE_2")));
        assertEquals(1, initialSetpoints.size());
        assertTrue(initialSetpoints.containsKey(crac.getRangeAction("PRA_PST_BE")));
        assertFalse(initialSetpoints.containsKey(crac.getRangeAction("PRA_PST_BE_2")));
     }*/
}
