/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.linear_rao.fillers;

import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.linear_rao.LinearRaoProblem;
import com.farao_community.farao.linear_rao.mocks.CnecMock;
import com.farao_community.farao.linear_rao.mocks.RangeActionMock;
import com.farao_community.farao.linear_rao.mocks.TwoWindingsTransformerMock;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPVariable;
import com.powsybl.iidm.network.Identifiable;
import com.powsybl.iidm.network.TwoWindingsTransformer;
import com.powsybl.sensitivity.SensitivityComputationResults;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
@RunWith(PowerMockRunner.class)
public class CoreProblemFillerTest extends FillerTest {

    @Before
    public void setUp() {
        init();
        coreProblemFiller = new CoreProblemFiller(linearRaoProblem, linearRaoData);
    }

    @Test
    public void fillWithRangeAction() {
        final String rangeActionId = "range-action-id";
        final String networkElementId = "network-element-id";
        final double minAlpha = -0.5;
        final double maxAlpha = 0.8;
        final int currentTap = 5;
        final double referenceFlow1 = 500.;
        final double referenceFlow2 = 300.;
        final double cnec1toRangeSensitivity = 0.2;
        final double cnec2toRangeSensitivity = 0.5;
        Cnec cnec1 = new CnecMock("cnec1-id", 0, 800);
        Cnec cnec2 = new CnecMock("cnec2-id", 0, 800);
        when(linearRaoData.getReferenceFlow(cnec1)).thenReturn(referenceFlow1);
        when(linearRaoData.getReferenceFlow(cnec2)).thenReturn(referenceFlow2);

        SensitivityComputationResults sensitivityComputationResults = mock(SensitivityComputationResults.class);
        when(linearRaoData.getSensitivityComputationResults(preventiveState)).thenReturn(sensitivityComputationResults);
        Map<Cnec, Double> sensitivities = new HashMap<>();
        sensitivities.put(cnec1, cnec1toRangeSensitivity);
        sensitivities.put(cnec2, cnec2toRangeSensitivity);

        cnecs.add(cnec1);
        cnecs.add(cnec2);
        RangeAction rangeAction = new RangeActionMock(rangeActionId, networkElementId, minAlpha, maxAlpha, sensitivities);
        rangeActions.add(rangeAction);

        coreProblemFiller.fill();

        MPVariable variableRangeNegative = linearRaoProblem.getNegativeRangeActionVariable(rangeAction.getId());
        assertNotNull(variableRangeNegative);
        assertEquals(0, variableRangeNegative.lb(), 0.01);
        assertEquals(minAlpha, variableRangeNegative.ub(), 0.01);

        MPVariable variableRangePositive = linearRaoProblem.getPositiveRangeActionVariable(rangeAction.getId());
        assertNotNull(variableRangePositive);
        assertEquals(0, variableRangePositive.lb(), 0.01);
        assertEquals(Math.abs(maxAlpha), variableRangePositive.ub(), 0.01);

        MPVariable flowVariable = linearRaoProblem.getFlowVariable(cnec1.getId());
        assertNotNull(flowVariable);
        assertEquals(-LinearRaoProblem.infinity(), flowVariable.lb(), 0.01);
        assertEquals(LinearRaoProblem.infinity(), flowVariable.ub(), 0.01);

        MPConstraint flowConstraint = linearRaoProblem.getFlowConstraint(cnec1.getId());
        assertNotNull(flowConstraint);
        assertEquals(referenceFlow1, flowConstraint.lb(), 0.1);
        assertEquals(referenceFlow1, flowConstraint.ub(), 0.1);
        assertEquals(1, flowConstraint.getCoefficient(flowVariable), 0.1);
        assertEquals(cnec1toRangeSensitivity, flowConstraint.getCoefficient(variableRangeNegative), 0.1);
        assertEquals(-cnec1toRangeSensitivity, flowConstraint.getCoefficient(variableRangePositive), 0.1);

        MPVariable flowVariable2 = linearRaoProblem.getFlowVariable(cnec2.getId());
        assertNotNull(flowVariable2);
        assertEquals(-LinearRaoProblem.infinity(), flowVariable2.lb(), 0.01);
        assertEquals(LinearRaoProblem.infinity(), flowVariable2.ub(), 0.01);

        MPConstraint flowConstraint2 = linearRaoProblem.getFlowConstraint(cnec2.getId());
        assertNotNull(flowConstraint2);
        assertEquals(referenceFlow2, flowConstraint2.lb(), 0.1);
        assertEquals(referenceFlow2, flowConstraint2.ub(), 0.1);
        assertEquals(1, flowConstraint2.getCoefficient(flowVariable2), 0.1);
        assertEquals(cnec2toRangeSensitivity, flowConstraint2.getCoefficient(variableRangeNegative), 0.1);
        assertEquals(-cnec2toRangeSensitivity, flowConstraint2.getCoefficient(variableRangePositive), 0.1);
    }
}
