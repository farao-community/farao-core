/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.rao_commons;

import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.PstRangeAction;
import com.farao_community.farao.data.crac_api.RangeAction;
import com.farao_community.farao.data.crac_api.cnec.BranchCnec;
import com.farao_community.farao.rao_api.results.BranchResult;
import com.farao_community.farao.rao_api.results.RangeActionResult;
import com.farao_community.farao.rao_api.results.SensitivityResult;
import com.farao_community.farao.rao_api.results.SensitivityStatus;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
public class PrePerimeterSensitivityOutputTest {
    private static final double DOUBLE_TOLERANCE = 0.01;

    @Test
    public void testBasicReturns() {
        BranchCnec cnec1 = Mockito.mock(BranchCnec.class);
        BranchCnec cnec2 = Mockito.mock(BranchCnec.class);

        PstRangeAction ra1 = Mockito.mock(PstRangeAction.class);
        RangeAction ra2 = Mockito.mock(RangeAction.class);

        LinearGlsk linearGlsk = Mockito.mock(LinearGlsk.class);
        BranchResult branchResult = Mockito.mock(BranchResult.class);
        SensitivityResult sensitivityResult = Mockito.mock(SensitivityResult.class);
        RangeActionResult rangeActionResult = Mockito.mock(RangeActionResult.class);

        PrePerimeterSensitivityOutput output = new PrePerimeterSensitivityOutput(branchResult, sensitivityResult, rangeActionResult);

        when(sensitivityResult.getSensitivityStatus()).thenReturn(SensitivityStatus.DEFAULT);
        assertEquals(SensitivityStatus.DEFAULT, output.getSensitivityStatus());
        when(sensitivityResult.getSensitivityStatus()).thenReturn(SensitivityStatus.FALLBACK);
        assertEquals(SensitivityStatus.FALLBACK, output.getSensitivityStatus());

        when(sensitivityResult.getSensitivityValue(cnec1, ra1, Unit.MEGAWATT)).thenReturn(0.5);
        when(sensitivityResult.getSensitivityValue(cnec2, ra1, Unit.AMPERE)).thenReturn(0.1);
        assertEquals(0.5, output.getSensitivityValue(cnec1, ra1, Unit.MEGAWATT), DOUBLE_TOLERANCE);
        assertEquals(0.1, output.getSensitivityValue(cnec2, ra1, Unit.AMPERE), DOUBLE_TOLERANCE);

        when(sensitivityResult.getSensitivityValue(cnec2, linearGlsk, Unit.MEGAWATT)).thenReturn(51.);
        assertEquals(51., output.getSensitivityValue(cnec2, linearGlsk, Unit.MEGAWATT), DOUBLE_TOLERANCE);

        when(branchResult.getFlow(cnec1, Unit.MEGAWATT)).thenReturn(10.);
        when(branchResult.getFlow(cnec2, Unit.AMPERE)).thenReturn(117.);
        assertEquals(10., output.getFlow(cnec1, Unit.MEGAWATT), DOUBLE_TOLERANCE);
        assertEquals(117., output.getFlow(cnec2, Unit.AMPERE), DOUBLE_TOLERANCE);

        when(branchResult.getRelativeMargin(cnec1, Unit.MEGAWATT)).thenReturn(564.);
        when(branchResult.getRelativeMargin(cnec2, Unit.AMPERE)).thenReturn(-451.);
        assertEquals(564., output.getRelativeMargin(cnec1, Unit.MEGAWATT), DOUBLE_TOLERANCE);
        assertEquals(-451., output.getRelativeMargin(cnec2, Unit.AMPERE), DOUBLE_TOLERANCE);

        when(branchResult.getLoopFlow(cnec1, Unit.MEGAWATT)).thenReturn(5064.);
        when(branchResult.getLoopFlow(cnec2, Unit.AMPERE)).thenReturn(-4510.);
        assertEquals(5064., output.getLoopFlow(cnec1, Unit.MEGAWATT), DOUBLE_TOLERANCE);
        assertEquals(-4510., output.getLoopFlow(cnec2, Unit.AMPERE), DOUBLE_TOLERANCE);

        when(branchResult.getCommercialFlow(cnec1, Unit.MEGAWATT)).thenReturn(50464.);
        when(branchResult.getCommercialFlow(cnec2, Unit.AMPERE)).thenReturn(-45104.);
        assertEquals(50464., output.getCommercialFlow(cnec1, Unit.MEGAWATT), DOUBLE_TOLERANCE);
        assertEquals(-45104., output.getCommercialFlow(cnec2, Unit.AMPERE), DOUBLE_TOLERANCE);

        when(branchResult.getPtdfZonalSum(cnec1)).thenReturn(0.4);
        when(branchResult.getPtdfZonalSum(cnec2)).thenReturn(0.75);
        assertEquals(0.4, output.getPtdfZonalSum(cnec1), DOUBLE_TOLERANCE);
        assertEquals(0.75, output.getPtdfZonalSum(cnec2), DOUBLE_TOLERANCE);

        when(branchResult.getPtdfZonalSums()).thenReturn(Map.of(cnec1, 0.1, cnec2, 0.2));
        assertEquals(Map.of(cnec1, 0.1, cnec2, 0.2), output.getPtdfZonalSums());

        when(rangeActionResult.getRangeActions()).thenReturn(Set.of(ra1, ra2));
        assertEquals(Set.of(ra1, ra2), output.getRangeActions());

        when(rangeActionResult.getOptimizedTap(ra1)).thenReturn(3);
        assertEquals(3, output.getOptimizedTap(ra1), DOUBLE_TOLERANCE);

        when(rangeActionResult.getOptimizedSetPoint(ra1)).thenReturn(15.6);
        assertEquals(15.6, output.getOptimizedSetPoint(ra1), DOUBLE_TOLERANCE);

        when(rangeActionResult.getOptimizedTaps()).thenReturn(Map.of(ra1, 1));
        assertEquals(Map.of(ra1, 1), output.getOptimizedTaps());

        when(rangeActionResult.getOptimizedSetPoints()).thenReturn(Map.of(ra1, 5.3, ra2, 6.7));
        assertEquals(Map.of(ra1, 5.3, ra2, 6.7), output.getOptimizedSetPoints());

        assertEquals(branchResult, output.getBranchResult());
        assertEquals(sensitivityResult, output.getSensitivityResult());
        assertEquals(0, output.getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(0, output.getVirtualCost(), DOUBLE_TOLERANCE);
        assertEquals(0, output.getVirtualCost("mock"), DOUBLE_TOLERANCE);
        assertNull(output.getMostLimitingElements(10));
        assertNull(output.getVirtualCostNames());
        assertNull(output.getCostlyElements("mock", 10));
    }

}