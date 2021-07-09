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
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.crac_api.network_action.NetworkAction;
import com.farao_community.farao.data.crac_api.range_action.PstRangeAction;
import com.farao_community.farao.data.crac_api.range_action.RangeAction;
import com.farao_community.farao.data.rao_result_api.ComputationStatus;
import com.farao_community.farao.rao_commons.result_api.OptimizationResult;
import com.farao_community.farao.rao_commons.result_api.PrePerimeterResult;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.farao_community.farao.data.rao_result_api.OptimizationState.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
public class PreventiveAndCurativesRaoOutputTest {
    private static final double DOUBLE_TOLERANCE = 1e-3;
    PrePerimeterResult initialResult;
    PerimeterResult postPrevResult;
    PrePerimeterResult preCurativeResult;
    Map<State, OptimizationResult> postCurativeResults;
    PstRangeAction pstRangeAction = mock(PstRangeAction.class);
    RangeAction rangeAction;
    NetworkAction networkAction;
    FlowCnec cnec1;
    FlowCnec cnec2;
    FlowCnec cnec3;
    FlowCnec cnec4;
    State state1;
    State state2;
    State state3;
    State preventiveState;
    PreventiveAndCurativesRaoOutput output;
    OptimizationResult curativeResult1;
    OptimizationResult curativeResult2;

    @Before
    public void setUp() {
        cnec1 = mock(FlowCnec.class);
        cnec2 = mock(FlowCnec.class);
        cnec3 = mock(FlowCnec.class);
        cnec4 = mock(FlowCnec.class);
        state1 = mock(State.class);
        state2 = mock(State.class);
        state3 = mock(State.class);
        preventiveState = mock(State.class);
        when(cnec1.getState()).thenReturn(state1);
        when(cnec2.getState()).thenReturn(state2);
        when(preventiveState.getInstant()).thenReturn(Instant.PREVENTIVE);
        when(state1.getInstant()).thenReturn(Instant.CURATIVE);
        when(state2.getInstant()).thenReturn(Instant.CURATIVE);

        initialResult = mock(PrePerimeterResult.class);
        postPrevResult = mock(PerimeterResult.class);
        preCurativeResult = mock(PrePerimeterResult.class);

        curativeResult1 = mock(OptimizationResult.class);
        curativeResult2 = mock(OptimizationResult.class);
        postCurativeResults = Map.of(state1, curativeResult1, state2, curativeResult2);

        pstRangeAction = mock(PstRangeAction.class);
        rangeAction = mock(RangeAction.class);
        networkAction = mock(NetworkAction.class);

        when(initialResult.getFunctionalCost()).thenReturn(1000.);
        when(initialResult.getMostLimitingElements(anyInt())).thenReturn(List.of(cnec1));
        when(initialResult.getVirtualCost()).thenReturn(100.);
        when(initialResult.getVirtualCost("mnec")).thenReturn(20.);
        when(initialResult.getVirtualCost("lf")).thenReturn(80.);
        when(initialResult.getVirtualCostNames()).thenReturn(Set.of("mnec", "lf"));
        when(initialResult.getCostlyElements(eq("mnec"), anyInt())).thenReturn(List.of(cnec2));
        when(initialResult.getCostlyElements(eq("lf"), anyInt())).thenReturn(List.of(cnec1));
        when(initialResult.getOptimizedSetPoint(pstRangeAction)).thenReturn(6.7);
        when(initialResult.getOptimizedSetPoint(rangeAction)).thenReturn(5.6);
        when(initialResult.getOptimizedTap(pstRangeAction)).thenReturn(1);
        when(initialResult.getRangeActions()).thenReturn(Set.of(rangeAction, pstRangeAction));
        when(initialResult.getOptimizedTaps()).thenReturn(Map.of(pstRangeAction, 1));
        when(initialResult.getOptimizedSetPoints()).thenReturn(Map.of(pstRangeAction, 6.7, rangeAction, 5.6));
        when(initialResult.getMargin(cnec1, Unit.MEGAWATT)).thenReturn(-1000.);
        when(initialResult.getMargin(cnec1, Unit.AMPERE)).thenReturn(-500.);
        when(initialResult.getRelativeMargin(cnec1, Unit.MEGAWATT)).thenReturn(-2000.);
        when(initialResult.getRelativeMargin(cnec1, Unit.AMPERE)).thenReturn(-1000.);
        when(initialResult.getMargin(cnec2, Unit.MEGAWATT)).thenReturn(-500.);
        when(initialResult.getMargin(cnec2, Unit.AMPERE)).thenReturn(-250.);
        when(initialResult.getRelativeMargin(cnec2, Unit.MEGAWATT)).thenReturn(-1500.);
        when(initialResult.getRelativeMargin(cnec2, Unit.AMPERE)).thenReturn(-750.);

        when(postPrevResult.getFunctionalCost()).thenReturn(-1020.);
        when(postPrevResult.getMostLimitingElements(anyInt())).thenReturn(List.of(cnec2));
        when(postPrevResult.getVirtualCost()).thenReturn(-120.);
        when(postPrevResult.getVirtualCost("mnec")).thenReturn(-40.);
        when(postPrevResult.getVirtualCost("lf")).thenReturn(-100.);
        when(postPrevResult.getCostlyElements(eq("mnec"), anyInt())).thenReturn(List.of(cnec1));
        when(postPrevResult.getCostlyElements(eq("lf"), anyInt())).thenReturn(List.of(cnec2));
        when(postPrevResult.isActivated(networkAction)).thenReturn(true);
        when(postPrevResult.getActivatedNetworkActions()).thenReturn(Set.of(networkAction));
        when(postPrevResult.getActivatedRangeActions()).thenReturn(Set.of(rangeAction));
        when(postPrevResult.getOptimizedSetPoint(pstRangeAction)).thenReturn(28.9);
        when(postPrevResult.getOptimizedSetPoint(rangeAction)).thenReturn(25.6);
        when(postPrevResult.getOptimizedTap(pstRangeAction)).thenReturn(22);
        when(postPrevResult.getRangeActions()).thenReturn(Set.of(rangeAction, pstRangeAction));
        when(postPrevResult.getOptimizedTaps()).thenReturn(Map.of(pstRangeAction, 22));
        when(postPrevResult.getOptimizedSetPoints()).thenReturn(Map.of(pstRangeAction, 28.9, rangeAction, 25.6));
        when(postPrevResult.getMargin(cnec2, Unit.MEGAWATT)).thenReturn(1020.);
        when(postPrevResult.getMargin(cnec2, Unit.AMPERE)).thenReturn(520.);
        when(postPrevResult.getRelativeMargin(cnec2, Unit.MEGAWATT)).thenReturn(2020.);
        when(postPrevResult.getRelativeMargin(cnec2, Unit.AMPERE)).thenReturn(1020.);
        when(postPrevResult.getMargin(cnec1, Unit.MEGAWATT)).thenReturn(520.);
        when(postPrevResult.getMargin(cnec1, Unit.AMPERE)).thenReturn(270.);
        when(postPrevResult.getRelativeMargin(cnec1, Unit.MEGAWATT)).thenReturn(1520.);
        when(postPrevResult.getRelativeMargin(cnec1, Unit.AMPERE)).thenReturn(770.);

        when(curativeResult1.getFunctionalCost()).thenReturn(-1030.);
        when(curativeResult1.getMostLimitingElements(anyInt())).thenReturn(List.of(cnec2));
        when(curativeResult1.getVirtualCost()).thenReturn(-130.);
        when(curativeResult1.getVirtualCost("mnec")).thenReturn(-50.);
        when(curativeResult1.getVirtualCost("lf")).thenReturn(-110.);
        when(curativeResult1.getCostlyElements(eq("mnec"), anyInt())).thenReturn(List.of(cnec1));
        when(curativeResult1.getCostlyElements(eq("lf"), anyInt())).thenReturn(List.of(cnec2));
        when(curativeResult1.isActivated(networkAction)).thenReturn(false);
        when(curativeResult1.getActivatedNetworkActions()).thenReturn(Set.of());
        when(curativeResult1.getOptimizedSetPoint(pstRangeAction)).thenReturn(28.9);
        when(curativeResult1.getOptimizedSetPoint(rangeAction)).thenReturn(25.6);
        when(curativeResult1.getOptimizedTap(pstRangeAction)).thenReturn(22);
        when(curativeResult1.getRangeActions()).thenReturn(Set.of(rangeAction, pstRangeAction));
        when(curativeResult1.getOptimizedTaps()).thenReturn(Map.of(pstRangeAction, 22));
        when(curativeResult1.getOptimizedSetPoints()).thenReturn(Map.of(pstRangeAction, 28.9, rangeAction, 25.6));
        when(curativeResult1.getMargin(cnec2, Unit.MEGAWATT)).thenReturn(1030.);
        when(curativeResult1.getMargin(cnec2, Unit.AMPERE)).thenReturn(530.);
        when(curativeResult1.getRelativeMargin(cnec2, Unit.MEGAWATT)).thenReturn(2030.);
        when(curativeResult1.getRelativeMargin(cnec2, Unit.AMPERE)).thenReturn(1030.);
        when(curativeResult1.getMargin(cnec1, Unit.MEGAWATT)).thenReturn(530.);
        when(curativeResult1.getMargin(cnec1, Unit.AMPERE)).thenReturn(280.);
        when(curativeResult1.getRelativeMargin(cnec1, Unit.MEGAWATT)).thenReturn(1530.);
        when(curativeResult1.getRelativeMargin(cnec1, Unit.AMPERE)).thenReturn(780.);

        when(curativeResult2.getFunctionalCost()).thenReturn(-1040.);
        when(curativeResult2.getMostLimitingElements(anyInt())).thenReturn(List.of(cnec2));
        when(curativeResult2.getVirtualCost()).thenReturn(-140.);
        when(curativeResult2.getVirtualCost("mnec")).thenReturn(-60.);
        when(curativeResult2.getVirtualCost("lf")).thenReturn(-120.);
        when(curativeResult2.getCostlyElements(eq("mnec"), anyInt())).thenReturn(List.of(cnec1));
        when(curativeResult2.getCostlyElements(eq("lf"), anyInt())).thenReturn(List.of(cnec2));
        when(curativeResult2.isActivated(networkAction)).thenReturn(false);
        when(curativeResult2.getActivatedNetworkActions()).thenReturn(Set.of());
        when(curativeResult2.getOptimizedSetPoint(pstRangeAction)).thenReturn(48.9);
        when(curativeResult2.getOptimizedSetPoint(rangeAction)).thenReturn(25.6);
        when(curativeResult2.getOptimizedTap(pstRangeAction)).thenReturn(42);
        when(curativeResult2.getRangeActions()).thenReturn(Set.of(rangeAction, pstRangeAction));
        when(curativeResult2.getOptimizedTaps()).thenReturn(Map.of(pstRangeAction, 42));
        when(curativeResult2.getOptimizedSetPoints()).thenReturn(Map.of(pstRangeAction, 28.9, rangeAction, 25.6));
        when(curativeResult2.getMargin(cnec2, Unit.MEGAWATT)).thenReturn(1040.);
        when(curativeResult2.getMargin(cnec2, Unit.AMPERE)).thenReturn(540.);
        when(curativeResult2.getRelativeMargin(cnec2, Unit.MEGAWATT)).thenReturn(2040.);
        when(curativeResult2.getRelativeMargin(cnec2, Unit.AMPERE)).thenReturn(1040.);
        when(curativeResult2.getMargin(cnec1, Unit.MEGAWATT)).thenReturn(540.);
        when(curativeResult2.getMargin(cnec1, Unit.AMPERE)).thenReturn(290.);
        when(curativeResult2.getRelativeMargin(cnec1, Unit.MEGAWATT)).thenReturn(1540.);
        when(curativeResult2.getRelativeMargin(cnec1, Unit.AMPERE)).thenReturn(790.);

        when(preCurativeResult.getFunctionalCost()).thenReturn(-1050.);
        when(preCurativeResult.getMostLimitingElements(anyInt())).thenReturn(List.of(cnec3));
        when(preCurativeResult.getVirtualCost()).thenReturn(-150.);
        when(preCurativeResult.getVirtualCost("mnec")).thenReturn(-70.);
        when(preCurativeResult.getVirtualCost("lf")).thenReturn(-130.);
        when(preCurativeResult.getCostlyElements(eq("mnec"), anyInt())).thenReturn(List.of(cnec3, cnec2));
        when(preCurativeResult.getCostlyElements(eq("lf"), anyInt())).thenReturn(List.of(cnec1, cnec4));
        when(preCurativeResult.getOptimizedSetPoint(pstRangeAction)).thenReturn(58.9);
        when(preCurativeResult.getOptimizedSetPoint(rangeAction)).thenReturn(55.6);
        when(preCurativeResult.getOptimizedTap(pstRangeAction)).thenReturn(52);
        when(preCurativeResult.getRangeActions()).thenReturn(Set.of(rangeAction, pstRangeAction));
        when(preCurativeResult.getOptimizedTaps()).thenReturn(Map.of(pstRangeAction, 52));
        when(preCurativeResult.getOptimizedSetPoints()).thenReturn(Map.of(pstRangeAction, 58.9, rangeAction, 55.6));
        when(preCurativeResult.getMargin(cnec2, Unit.MEGAWATT)).thenReturn(1050.);
        when(preCurativeResult.getMargin(cnec2, Unit.AMPERE)).thenReturn(550.);
        when(preCurativeResult.getRelativeMargin(cnec2, Unit.MEGAWATT)).thenReturn(2050.);
        when(preCurativeResult.getRelativeMargin(cnec2, Unit.AMPERE)).thenReturn(1050.);
        when(preCurativeResult.getMargin(cnec1, Unit.MEGAWATT)).thenReturn(550.);
        when(preCurativeResult.getMargin(cnec1, Unit.AMPERE)).thenReturn(300.);
        when(preCurativeResult.getRelativeMargin(cnec1, Unit.MEGAWATT)).thenReturn(1550.);
        when(preCurativeResult.getRelativeMargin(cnec1, Unit.AMPERE)).thenReturn(800.);

        output = new PreventiveAndCurativesRaoOutput(initialResult,
            postPrevResult,
                preCurativeResult,
                Map.of(state1, curativeResult1, state2, curativeResult2));
    }

    @Test
    public void testGetComputationStatus() {
        when(initialResult.getSensitivityStatus()).thenReturn(ComputationStatus.DEFAULT);
        when(curativeResult1.getSensitivityStatus()).thenReturn(ComputationStatus.DEFAULT);
        when(curativeResult2.getSensitivityStatus()).thenReturn(ComputationStatus.DEFAULT);
        assertEquals(ComputationStatus.DEFAULT, output.getComputationStatus());

        when(initialResult.getSensitivityStatus()).thenReturn(ComputationStatus.FAILURE);
        assertEquals(ComputationStatus.FAILURE, output.getComputationStatus());

        when(initialResult.getSensitivityStatus()).thenReturn(ComputationStatus.DEFAULT);
        when(postPrevResult.getSensitivityStatus()).thenReturn(ComputationStatus.FAILURE);
        assertEquals(ComputationStatus.FAILURE, output.getComputationStatus());

        when(postPrevResult.getSensitivityStatus()).thenReturn(ComputationStatus.DEFAULT);
        when(curativeResult2.getSensitivityStatus()).thenReturn(ComputationStatus.FAILURE);
        assertEquals(ComputationStatus.FAILURE, output.getComputationStatus());
    }

    @Test
    public void testGetFunctionalCost() {
        assertEquals(1000., output.getFunctionalCost(INITIAL), DOUBLE_TOLERANCE);
        assertEquals(-1020., output.getFunctionalCost(AFTER_PRA), DOUBLE_TOLERANCE);
        assertEquals(-1020., output.getFunctionalCost(AFTER_CRA), DOUBLE_TOLERANCE);

        when(postPrevResult.getFunctionalCost()).thenReturn(-2020.);
        assertEquals(-1030., output.getFunctionalCost(AFTER_CRA), DOUBLE_TOLERANCE);
    }

    @Test
    public void testGetMostLimitingElements() {
        assertNull(output.getMostLimitingElements(INITIAL, 5));
        assertNull(output.getMostLimitingElements(AFTER_PRA, 15));
        assertNull(output.getMostLimitingElements(AFTER_CRA, 445));
    }

    @Test
    public void testGetVirtualCost() {
        assertEquals(100., output.getVirtualCost(INITIAL), DOUBLE_TOLERANCE);
        assertEquals(-120., output.getVirtualCost(AFTER_PRA), DOUBLE_TOLERANCE);
        assertEquals(-270., output.getVirtualCost(AFTER_CRA), DOUBLE_TOLERANCE);
    }

    @Test
    public void testGetVirtualCostNames() {
        assertEquals(Set.of("mnec", "lf"), output.getVirtualCostNames());
    }

    @Test
    public void testGetVirtualCostByName() {
        assertEquals(20., output.getVirtualCost(INITIAL, "mnec"), DOUBLE_TOLERANCE);
        assertEquals(80., output.getVirtualCost(INITIAL, "lf"), DOUBLE_TOLERANCE);
        assertEquals(-40., output.getVirtualCost(AFTER_PRA, "mnec"), DOUBLE_TOLERANCE);
        assertEquals(-100., output.getVirtualCost(AFTER_PRA, "lf"), DOUBLE_TOLERANCE);
        assertEquals(-110., output.getVirtualCost(AFTER_CRA, "mnec"), DOUBLE_TOLERANCE);
        assertEquals(-230., output.getVirtualCost(AFTER_CRA, "lf"), DOUBLE_TOLERANCE);
    }

    @Test
    public void testGetCostlyElements() {
        assertNull(output.getCostlyElements(INITIAL, "mnec", 5));
        assertNull(output.getCostlyElements(INITIAL, "lf", 15));

        assertNull(output.getCostlyElements(AFTER_PRA, "mnec", 5));
        assertNull(output.getCostlyElements(AFTER_PRA, "lf", 15));

        assertNull(output.getCostlyElements(AFTER_CRA, "mnec", 5));
        assertNull(output.getCostlyElements(AFTER_CRA, "lf", 15));
    }

    @Test
    public void testWasNetworkActionActivatedBeforeState() {
        assertFalse(output.wasActivatedBeforeState(preventiveState, networkAction));
        assertTrue(output.wasActivatedBeforeState(state1, networkAction));
        assertTrue(output.wasActivatedBeforeState(state2, networkAction));
    }

    @Test
    public void testIsNetworkActionActivatedDuringState() {
        assertTrue(output.isActivatedDuringState(preventiveState, networkAction));
        assertFalse(output.isActivatedDuringState(state1, networkAction));
        assertFalse(output.isActivatedDuringState(state2, networkAction));
        assertFalse(output.isActivatedDuringState(state3, networkAction));
    }

    @Test
    public void testGetActivatedNetworkActionsDuringState() {
        assertEquals(Set.of(networkAction), output.getActivatedNetworkActionsDuringState(preventiveState));
        assertEquals(Set.of(), output.getActivatedNetworkActionsDuringState(state3));
        assertEquals(Set.of(), output.getActivatedNetworkActionsDuringState(state1));
        assertEquals(Set.of(), output.getActivatedNetworkActionsDuringState(state2));

        when(curativeResult2.getActivatedNetworkActions()).thenReturn(Set.of(networkAction));
        assertEquals(Set.of(networkAction), output.getActivatedNetworkActionsDuringState(preventiveState));
        assertEquals(Set.of(), output.getActivatedNetworkActionsDuringState(state3));
        assertEquals(Set.of(), output.getActivatedNetworkActionsDuringState(state1));
        assertEquals(Set.of(networkAction), output.getActivatedNetworkActionsDuringState(state2));

        when(postPrevResult.getActivatedNetworkActions()).thenReturn(Set.of());
        assertEquals(Set.of(), output.getActivatedNetworkActionsDuringState(preventiveState));
        assertEquals(Set.of(), output.getActivatedNetworkActionsDuringState(state3));
    }

    @Test
    public void testIsRangeActionActivatedDuringState() {
        assertTrue(output.isActivatedDuringState(preventiveState, rangeAction));
        assertTrue(output.isActivatedDuringState(state1, rangeAction));
        assertTrue(output.isActivatedDuringState(state2, rangeAction));
        assertFalse(output.isActivatedDuringState(state3, rangeAction));

        when(curativeResult2.getOptimizedSetPoint(rangeAction)).thenReturn(55.6);
        assertTrue(output.isActivatedDuringState(preventiveState, rangeAction));
        assertTrue(output.isActivatedDuringState(state1, rangeAction));
        assertFalse(output.isActivatedDuringState(state2, rangeAction));
        assertFalse(output.isActivatedDuringState(state3, rangeAction));
    }

    @Test
    public void testGetPreOptimizationTapOnState() {
        assertEquals(1, output.getPreOptimizationTapOnState(preventiveState, pstRangeAction));
        assertEquals(22, output.getPreOptimizationTapOnState(state1, pstRangeAction));
        assertEquals(22, output.getPreOptimizationTapOnState(state2, pstRangeAction));
        assertThrows(FaraoException.class, () -> output.getPreOptimizationTapOnState(state3, pstRangeAction));
    }

    @Test
    public void testGetOptimizedTapOnState() {
        when(postPrevResult.getOptimizedTap(pstRangeAction)).thenReturn(202);
        assertEquals(202, output.getOptimizedTapOnState(preventiveState, pstRangeAction));
        assertEquals(22, output.getOptimizedTapOnState(state1, pstRangeAction));
        assertEquals(42, output.getOptimizedTapOnState(state2, pstRangeAction));
        assertEquals(202, output.getOptimizedTapOnState(state3, pstRangeAction));
    }

    @Test
    public void testGetPreOptimizationSetPointOnState() {
        assertEquals(6.7, output.getPreOptimizationSetPointOnState(preventiveState, pstRangeAction), DOUBLE_TOLERANCE);
        assertEquals(28.9, output.getPreOptimizationSetPointOnState(state1, pstRangeAction), DOUBLE_TOLERANCE);
        assertEquals(28.9, output.getPreOptimizationSetPointOnState(state2, pstRangeAction), DOUBLE_TOLERANCE);
        assertThrows(FaraoException.class, () -> output.getPreOptimizationSetPointOnState(state3, pstRangeAction));
    }

    @Test
    public void testGetOptimizedSetPointOnState() {
        when(postPrevResult.getOptimizedSetPoint(pstRangeAction)).thenReturn(567.);
        assertEquals(567, output.getOptimizedSetPointOnState(preventiveState, pstRangeAction), DOUBLE_TOLERANCE);
        assertEquals(28.9, output.getOptimizedSetPointOnState(state1, pstRangeAction), DOUBLE_TOLERANCE);
        assertEquals(48.9, output.getOptimizedSetPointOnState(state2, pstRangeAction), DOUBLE_TOLERANCE);
        assertEquals(567, output.getOptimizedSetPointOnState(state3, pstRangeAction), DOUBLE_TOLERANCE);
    }

    @Test
    public void testGetActivatedRangeActionsDuringState() {
        assertEquals(Set.of(rangeAction), output.getActivatedRangeActionsDuringState(preventiveState));
        assertEquals(Set.of(pstRangeAction, rangeAction), output.getActivatedRangeActionsDuringState(state1));
        assertEquals(Set.of(pstRangeAction, rangeAction), output.getActivatedRangeActionsDuringState(state2));
        assertEquals(Set.of(), output.getActivatedRangeActionsDuringState(state3));

        when(postPrevResult.getActivatedRangeActions()).thenReturn(Set.of(pstRangeAction));
        assertEquals(Set.of(pstRangeAction), output.getActivatedRangeActionsDuringState(preventiveState));
    }

    @Test
    public void testGetOptimizedTapsOnState() {
        when(curativeResult1.getOptimizedTaps()).thenReturn(Map.of(pstRangeAction, 333));
        when(curativeResult1.getOptimizedTap(pstRangeAction)).thenReturn(333);
        // with next line, pstRangeAction should be detected as activated in state1
        when(curativeResult1.getOptimizedSetPoint(pstRangeAction)).thenReturn(3330.);

        when(curativeResult2.getOptimizedTaps()).thenReturn(Map.of(pstRangeAction, 444));
        when(curativeResult2.getOptimizedTap(pstRangeAction)).thenReturn(444);
        // with next line, pstRangeAction should not be detected as activated in state2
        when(curativeResult2.getOptimizedSetPoint(pstRangeAction)).thenReturn(18.9);

        assertEquals(Map.of(pstRangeAction, 22), output.getOptimizedTapsOnState(preventiveState));
        assertEquals(Map.of(pstRangeAction, 333), output.getOptimizedTapsOnState(state1));
        assertEquals(Map.of(pstRangeAction, 444), output.getOptimizedTapsOnState(state2));
        assertEquals(Map.of(pstRangeAction, 22), output.getOptimizedTapsOnState(state3));
    }

    @Test
    public void testGetOptimizedSetPointsOnState() {
        when(postPrevResult.getOptimizedSetPoints()).thenReturn(Map.of(pstRangeAction, 222., rangeAction, 111.));

        when(curativeResult1.getOptimizedSetPoints()).thenReturn(Map.of(pstRangeAction, 333.));
        // with next line, pstRangeAction should be detected as activated in state1
        when(curativeResult1.getOptimizedSetPoint(pstRangeAction)).thenReturn(333.);

        when(curativeResult2.getOptimizedSetPoints()).thenReturn(Map.of(pstRangeAction, 444.));
        // with next line, pstRangeAction should not be detected as activated in state2
        when(curativeResult2.getOptimizedSetPoint(pstRangeAction)).thenReturn(18.9);

        assertEquals(Map.of(pstRangeAction, 222., rangeAction, 111.), output.getOptimizedSetPointsOnState(preventiveState));
        assertEquals(Map.of(pstRangeAction, 333.), output.getOptimizedSetPointsOnState(state1));
        assertEquals(Map.of(pstRangeAction, 444.), output.getOptimizedSetPointsOnState(state2));
        assertEquals(Map.of(pstRangeAction, 222., rangeAction, 111.), output.getOptimizedSetPointsOnState(state3));
    }

    @Test
    public void testGetFlow() {
        when(initialResult.getFlow(cnec1, Unit.MEGAWATT)).thenReturn(10.);
        when(postPrevResult.getFlow(cnec2, Unit.AMPERE)).thenReturn(20.);
        when(postPrevResult.getFlow(cnec3, Unit.MEGAWATT)).thenReturn(30.);
        when(cnec3.getState()).thenReturn(state3);
        assertEquals(10., output.getFlow(INITIAL, cnec1, Unit.MEGAWATT), DOUBLE_TOLERANCE);
        assertEquals(20., output.getFlow(AFTER_PRA, cnec2, Unit.AMPERE), DOUBLE_TOLERANCE);
        assertEquals(30., output.getFlow(AFTER_CRA, cnec3, Unit.MEGAWATT), DOUBLE_TOLERANCE);
    }

    @Test
    public void testGetMargin() {
        when(initialResult.getMargin(cnec1, Unit.MEGAWATT)).thenReturn(10.);
        when(postPrevResult.getMargin(cnec2, Unit.AMPERE)).thenReturn(20.);
        when(postPrevResult.getMargin(cnec3, Unit.MEGAWATT)).thenReturn(30.);
        when(cnec3.getState()).thenReturn(state3);
        assertEquals(10., output.getMargin(INITIAL, cnec1, Unit.MEGAWATT), DOUBLE_TOLERANCE);
        assertEquals(20., output.getMargin(AFTER_PRA, cnec2, Unit.AMPERE), DOUBLE_TOLERANCE);
        assertEquals(30., output.getMargin(AFTER_CRA, cnec3, Unit.MEGAWATT), DOUBLE_TOLERANCE);
    }

    @Test
    public void testGetRelativeMargin() {
        when(initialResult.getRelativeMargin(cnec1, Unit.MEGAWATT)).thenReturn(10.);
        when(postPrevResult.getRelativeMargin(cnec2, Unit.AMPERE)).thenReturn(20.);
        when(postPrevResult.getRelativeMargin(cnec3, Unit.MEGAWATT)).thenReturn(30.);
        when(cnec3.getState()).thenReturn(state3);
        assertEquals(10., output.getRelativeMargin(INITIAL, cnec1, Unit.MEGAWATT), DOUBLE_TOLERANCE);
        assertEquals(20., output.getRelativeMargin(AFTER_PRA, cnec2, Unit.AMPERE), DOUBLE_TOLERANCE);
        assertEquals(30., output.getRelativeMargin(AFTER_CRA, cnec3, Unit.MEGAWATT), DOUBLE_TOLERANCE);
    }

    @Test
    public void testGetCommercialFlow() {
        when(initialResult.getCommercialFlow(cnec1, Unit.MEGAWATT)).thenReturn(10.);
        when(postPrevResult.getCommercialFlow(cnec2, Unit.AMPERE)).thenReturn(20.);
        when(curativeResult1.getCommercialFlow(cnec3, Unit.MEGAWATT)).thenReturn(30.);
        when(cnec3.getState()).thenReturn(state1);
        assertEquals(10., output.getCommercialFlow(INITIAL, cnec1, Unit.MEGAWATT), DOUBLE_TOLERANCE);
        assertEquals(20., output.getCommercialFlow(AFTER_PRA, cnec2, Unit.AMPERE), DOUBLE_TOLERANCE);
        assertEquals(30., output.getCommercialFlow(AFTER_CRA, cnec3, Unit.MEGAWATT), DOUBLE_TOLERANCE);
    }

    @Test
    public void testGetLoopFlow() {
        when(initialResult.getLoopFlow(cnec1, Unit.MEGAWATT)).thenReturn(10.);
        when(postPrevResult.getLoopFlow(cnec2, Unit.AMPERE)).thenReturn(20.);
        when(curativeResult2.getFlow(cnec3, Unit.MEGAWATT)).thenReturn(90.);
        when(curativeResult2.getCommercialFlow(cnec3, Unit.MEGAWATT)).thenReturn(60.);
        when(cnec3.getState()).thenReturn(state2);
        assertEquals(10., output.getLoopFlow(INITIAL, cnec1, Unit.MEGAWATT), DOUBLE_TOLERANCE);
        assertEquals(20., output.getLoopFlow(AFTER_PRA, cnec2, Unit.AMPERE), DOUBLE_TOLERANCE);
        assertEquals(30., output.getLoopFlow(AFTER_CRA, cnec3, Unit.MEGAWATT), DOUBLE_TOLERANCE);
    }

    @Test
    public void testGetPtdfZonalSum() {
        when(initialResult.getPtdfZonalSum(cnec1)).thenReturn(10.);
        when(postPrevResult.getPtdfZonalSum(cnec2)).thenReturn(20.);
        when(curativeResult2.getPtdfZonalSum(cnec3)).thenReturn(30.);
        when(cnec3.getState()).thenReturn(state2);
        assertEquals(10., output.getPtdfZonalSum(INITIAL, cnec1), DOUBLE_TOLERANCE);
        assertEquals(20., output.getPtdfZonalSum(AFTER_PRA, cnec2), DOUBLE_TOLERANCE);
        assertEquals(30., output.getPtdfZonalSum(AFTER_CRA, cnec3), DOUBLE_TOLERANCE);
    }
}
