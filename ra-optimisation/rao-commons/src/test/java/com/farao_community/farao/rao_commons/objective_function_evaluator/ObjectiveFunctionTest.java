/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.rao_commons.objective_function_evaluator;

import com.farao_community.farao.data.crac_api.cnec.BranchCnec;
import com.farao_community.farao.rao_api.results.BranchResult;
import com.farao_community.farao.rao_api.results.ObjectiveFunctionResult;
import com.farao_community.farao.rao_api.results.SensitivityStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
public class ObjectiveFunctionTest {
    private static final double DOUBLE_TOLERANCE = 0.01;

    MinMarginEvaluator minMarginEvaluator;
    MnecViolationCostEvaluator mnecViolationCostEvaluator;
    LoopFlowViolationCostEvaluator loopFlowViolationCostEvaluator;
    BranchResult branchResult;
    SensitivityStatus sensitivityStatus;
    BranchCnec cnec1;
    BranchCnec cnec2;

    @Before
    public void setUp() {
        branchResult = Mockito.mock(BranchResult.class);
        sensitivityStatus = Mockito.mock(SensitivityStatus.class);
        cnec1 = Mockito.mock(BranchCnec.class);
        cnec2 = Mockito.mock(BranchCnec.class);

        minMarginEvaluator = Mockito.mock(MinMarginEvaluator.class);
        when(minMarginEvaluator.computeCost(branchResult, sensitivityStatus)).thenReturn(-300.);
        when(minMarginEvaluator.getCostlyElements(branchResult, 10)).thenReturn(List.of(cnec1, cnec2));

        mnecViolationCostEvaluator = Mockito.mock(MnecViolationCostEvaluator.class);
        when(mnecViolationCostEvaluator.getName()).thenReturn("mnec-cost");
        when(mnecViolationCostEvaluator.computeCost(branchResult, sensitivityStatus)).thenReturn(1000.);
        when(mnecViolationCostEvaluator.getCostlyElements(branchResult, 10)).thenReturn(List.of(cnec1));

        loopFlowViolationCostEvaluator = Mockito.mock(LoopFlowViolationCostEvaluator.class);
        when(loopFlowViolationCostEvaluator.getName()).thenReturn("loop-flow-cost");
        when(loopFlowViolationCostEvaluator.computeCost(branchResult, sensitivityStatus)).thenReturn(100.);
        when(loopFlowViolationCostEvaluator.getCostlyElements(branchResult, 10)).thenReturn(List.of(cnec2));
    }

    @Test
    public void testWithFunctionalCostOnly() {
        ObjectiveFunction objectiveFunction = ObjectiveFunction.create()
                .withFunctionalCostEvaluator(minMarginEvaluator)
                .build();

        // functional cost
        assertEquals(-300., objectiveFunction.getFunctionalCost(branchResult, sensitivityStatus), DOUBLE_TOLERANCE);
        assertEquals(List.of(cnec1, cnec2), objectiveFunction.getMostLimitingElements(branchResult, 10));

        // virtual cost
        assertTrue(objectiveFunction.getVirtualCostNames().isEmpty());
        assertEquals(0., objectiveFunction.getVirtualCost(branchResult, sensitivityStatus), DOUBLE_TOLERANCE);
        assertTrue(Double.isNaN(objectiveFunction.getVirtualCost(branchResult, sensitivityStatus, "mnec-cost")));
        assertTrue(objectiveFunction.getCostlyElements(branchResult, "mnec-cost", 10).isEmpty());

        // ObjectiveFunctionResult
        ObjectiveFunctionResult result = objectiveFunction.evaluate(branchResult, sensitivityStatus);
        assertEquals(-300., result.getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(0., result.getVirtualCost(), DOUBLE_TOLERANCE);
        assertEquals(-300., result.getCost(), DOUBLE_TOLERANCE);
        assertEquals(List.of(cnec1, cnec2), result.getMostLimitingElements(10));
        assertTrue(result.getVirtualCostNames().isEmpty());
    }

    @Test(expected = NullPointerException.class)
    public void testWithVirtualCostOnly() {
        ObjectiveFunction.create()
                .withVirtualCostEvaluator(mnecViolationCostEvaluator)
                .build();
    }

    @Test
    public void testWithFunctionalAndVirtualCost() {
        ObjectiveFunction objectiveFunction = ObjectiveFunction.create()
                .withFunctionalCostEvaluator(minMarginEvaluator)
                .withVirtualCostEvaluator(mnecViolationCostEvaluator)
                .withVirtualCostEvaluator(loopFlowViolationCostEvaluator)
                .build();

        // functional cost
        assertEquals(-300., objectiveFunction.getFunctionalCost(branchResult, sensitivityStatus), DOUBLE_TOLERANCE);
        assertEquals(List.of(cnec1, cnec2), objectiveFunction.getMostLimitingElements(branchResult, 10));

        // virtual cost sum
        assertEquals(2, objectiveFunction.getVirtualCostNames().size());
        assertTrue(objectiveFunction.getVirtualCostNames().containsAll(Set.of("mnec-cost", "loop-flow-cost")));
        assertEquals(1100., objectiveFunction.getVirtualCost(branchResult, sensitivityStatus), DOUBLE_TOLERANCE);

        // mnec virtual cost
        assertEquals(1000., objectiveFunction.getVirtualCost(branchResult, sensitivityStatus, "mnec-cost"), DOUBLE_TOLERANCE);
        assertEquals(List.of(cnec1), objectiveFunction.getCostlyElements(branchResult, "mnec-cost", 10));

        // loopflow virtual cost
        assertEquals(100., objectiveFunction.getVirtualCost(branchResult, sensitivityStatus, "loop-flow-cost"), DOUBLE_TOLERANCE);
        assertEquals(List.of(cnec2), objectiveFunction.getCostlyElements(branchResult, "loop-flow-cost", 10));

        // ObjectiveFunctionResult
        ObjectiveFunctionResult result = objectiveFunction.evaluate(branchResult, sensitivityStatus);
        assertEquals(-300., result.getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(1100., result.getVirtualCost(), DOUBLE_TOLERANCE);
        assertEquals(800., result.getCost(), DOUBLE_TOLERANCE);
        assertEquals(List.of(cnec1, cnec2), result.getMostLimitingElements(10));
        assertEquals(2, result.getVirtualCostNames().size());
        assertTrue(result.getVirtualCostNames().containsAll(Set.of("mnec-cost", "loop-flow-cost")));
        assertEquals(1000., result.getVirtualCost("mnec-cost"), DOUBLE_TOLERANCE);
        assertEquals(List.of(cnec1), result.getCostlyElements("mnec-cost", 10));
        assertEquals(100., result.getVirtualCost("loop-flow-cost"), DOUBLE_TOLERANCE);
        assertEquals(List.of(cnec2), result.getCostlyElements("loop-flow-cost", 10));
    }
}