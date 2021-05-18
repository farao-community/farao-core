/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.search_tree_rao;

import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_api.cnec.BranchCnec;
import com.farao_community.farao.data.crac_api.threshold.BranchThresholdRule;
import com.farao_community.farao.data.crac_api.usage_rule.UsageMethod;
import com.farao_community.farao.data.crac_impl.SimpleCrac;
import com.farao_community.farao.data.crac_impl.remedial_action.network_action.NetworkActionImpl;
import com.farao_community.farao.data.crac_impl.remedial_action.network_action.TopologicalActionImpl;
import com.farao_community.farao.data.crac_impl.usage_rule.OnStateImpl;
import com.farao_community.farao.data.crac_impl.utils.CommonCracCreation;
import com.farao_community.farao.data.crac_result_extensions.*;
import com.farao_community.farao.rao_api.RaoResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class SearchTreeRaoProviderTest {

    private static final double DOUBLE_TOLERANCE = 1e-3;

    private SimpleCrac crac;
    private String initialVariantId;
    private String postOptimPrevVariantId;
    private String postOptimCurVariantId;

    @Before
    public void setUp() {
        crac = CommonCracCreation.createWithPstRange();

        State curativeState = crac.getState("Contingency FR1 FR3", Instant.CURATIVE);

        TopologicalActionImpl topologicalAction = new TopologicalActionImpl(crac.getNetworkElement("BBE2AA1  FFR3AA1  1"), ActionType.OPEN);

        crac.addNetworkAction(new NetworkActionImpl("open BE2-FR3", "open BE2-FR3", "FR",
                List.of(new OnStateImpl(UsageMethod.AVAILABLE, curativeState)),
                Collections.singleton(topologicalAction)));

        ResultVariantManager resultVariantManager = new ResultVariantManager();
        crac.addExtension(ResultVariantManager.class, resultVariantManager);
        initialVariantId = resultVariantManager.createNewUniqueVariantId("initial");
        postOptimPrevVariantId = resultVariantManager.createNewUniqueVariantId("postOptim-prev");
        postOptimCurVariantId = resultVariantManager.createNewUniqueVariantId("postOptim-cur");
        resultVariantManager.setInitialVariantId(initialVariantId);

        crac.getBranchCnec("cnec1basecase").getExtension(CnecResultExtension.class).getVariant(initialVariantId).setFlowInMW(600);
        crac.getBranchCnec("cnec1basecase").getExtension(CnecResultExtension.class).getVariant(postOptimPrevVariantId).setFlowInMW(300);

        crac.getBranchCnec("cnec1stateCurativeContingency1").getExtension(CnecResultExtension.class)
                .getVariant(postOptimPrevVariantId).setFlowInMW(400);
        crac.getBranchCnec("cnec1stateCurativeContingency1").getExtension(CnecResultExtension.class)
                .getVariant(postOptimCurVariantId).setFlowInMW(200);

        ((PstRangeResult) crac.getRangeAction("pst").getExtension(RangeActionResultExtension.class)
                .getVariant(initialVariantId)).setTap(crac.getPreventiveState().getId(), 0);
        ((PstRangeResult) crac.getRangeAction("pst").getExtension(RangeActionResultExtension.class)
                .getVariant(postOptimPrevVariantId)).setTap(crac.getPreventiveState().getId(), 5);
        ((PstRangeResult) crac.getRangeAction("pst").getExtension(RangeActionResultExtension.class)
                .getVariant(postOptimCurVariantId)).setTap(curativeState.getId(), -10);

        crac.getNetworkAction("open BE2-FR3").getExtension(NetworkActionResultExtension.class)
                .getVariant(postOptimCurVariantId).activate(curativeState.getId());

        crac.getExtension(CracResultExtension.class).getVariant(initialVariantId).setFunctionalCost(1000.);
        crac.getExtension(CracResultExtension.class).getVariant(initialVariantId).setVirtualCost(0.);
        crac.getExtension(CracResultExtension.class).getVariant(postOptimPrevVariantId).setFunctionalCost(0.);
        crac.getExtension(CracResultExtension.class).getVariant(postOptimPrevVariantId).setVirtualCost(0.);
        crac.getExtension(CracResultExtension.class).getVariant(postOptimCurVariantId).setFunctionalCost(-1000.);
        crac.getExtension(CracResultExtension.class).getVariant(postOptimCurVariantId).setVirtualCost(0.);
    }

    /* Creates simple state tree with :
     *  - preventive perimeter being {preventive state (optimized), curative state after FR1-FR2co}
     *  - a curative perimeer being {curative state after FR1-FR3co (optimized)}
     */
    private StateTree mockedStateTree(Crac crac) {
        State curativeState = crac.getState("Contingency FR1 FR3", Instant.CURATIVE);
        StateTree stateTree = Mockito.mock(StateTree.class);
        Mockito.when(stateTree.getOptimizedState(crac.getPreventiveState())).thenReturn(crac.getPreventiveState());
        Mockito.when(stateTree.getOptimizedState(crac.getState("Contingency FR1 FR2", Instant.CURATIVE))).thenReturn(crac.getPreventiveState());
        Mockito.when(stateTree.getOptimizedState(curativeState)).thenReturn(curativeState);
        Mockito.when(stateTree.getOptimizedStates()).thenReturn(Set.of(crac.getPreventiveState(), curativeState));
        Mockito.when(stateTree.getPerimeter(crac.getPreventiveState())).thenReturn(Set.of(crac.getPreventiveState(), crac.getState("Contingency FR1 FR2", Instant.CURATIVE)));
        Mockito.when(stateTree.getPerimeter(curativeState)).thenReturn(Set.of(curativeState));
        return stateTree;
    }

    @Test
    public void mergeRaoResults() {
        RaoResult preventiveRaoResult = new RaoResult(RaoResult.Status.SUCCESS);
        preventiveRaoResult.setPreOptimVariantId(initialVariantId);
        preventiveRaoResult.setPostOptimVariantId(postOptimPrevVariantId);

        RaoResult curativeRaoResult = new RaoResult(RaoResult.Status.SUCCESS);
        curativeRaoResult.setPreOptimVariantId(postOptimPrevVariantId);
        curativeRaoResult.setPostOptimVariantId(postOptimCurVariantId);

        StateTree stateTree = mockedStateTree(crac);

        State curativeState = crac.getState("Contingency FR1 FR3", Instant.CURATIVE);
        RaoResult mergedRaoResult = new SearchTreeRaoProvider(stateTree).mergeRaoResults(crac, preventiveRaoResult,
                Map.of(curativeState, curativeRaoResult), 1000.);

        assertEquals(RaoResult.Status.SUCCESS, mergedRaoResult.getStatus());
        assertEquals(postOptimPrevVariantId, mergedRaoResult.getPostOptimVariantId());
        assertEquals(300, crac.getBranchCnec("cnec1basecase").getExtension(CnecResultExtension.class)
                .getVariant(postOptimPrevVariantId).getFlowInMW(), 0.1);
        assertEquals(200, crac.getBranchCnec("cnec1stateCurativeContingency1").getExtension(CnecResultExtension.class)
                .getVariant(postOptimPrevVariantId).getFlowInMW(), 0.1);
        assertEquals(Integer.valueOf(5), ((PstRangeResult) crac.getRangeAction("pst").getExtension(RangeActionResultExtension.class)
                .getVariant(postOptimPrevVariantId)).getTap(crac.getPreventiveState().getId()));
        assertEquals(Integer.valueOf(-10), ((PstRangeResult) crac.getRangeAction("pst").getExtension(RangeActionResultExtension.class)
                .getVariant(postOptimPrevVariantId)).getTap(curativeState.getId()));
        assertTrue(crac.getNetworkAction("open BE2-FR3").getExtension(NetworkActionResultExtension.class)
                .getVariant(postOptimPrevVariantId).isActivated(curativeState.getId()));
        assertEquals(2, crac.getExtension(ResultVariantManager.class).getVariants().size());
    }

    @Test
    public void mergeRaoResultsWithFailure() {

        RaoResult preventiveRaoResult = new RaoResult(RaoResult.Status.SUCCESS);
        preventiveRaoResult.setPreOptimVariantId(initialVariantId);
        preventiveRaoResult.setPostOptimVariantId(postOptimPrevVariantId);

        RaoResult curativeRaoResult = new RaoResult(RaoResult.Status.FAILURE);
        curativeRaoResult.setPreOptimVariantId(postOptimPrevVariantId);
        curativeRaoResult.setPostOptimVariantId(postOptimCurVariantId);

        StateTree stateTree = mockedStateTree(crac);

        State curativeState = crac.getState("Contingency FR1 FR3", Instant.CURATIVE);
        RaoResult mergedRaoResult = new SearchTreeRaoProvider(stateTree).mergeRaoResults(crac, preventiveRaoResult,
                Map.of(curativeState, curativeRaoResult), 1000.);

        assertEquals(RaoResult.Status.FAILURE, mergedRaoResult.getStatus());
    }

    @Test
    public void mergeRaoResultsWithNoOptimizationInCurative() {

        RaoResult preventiveRaoResult = new RaoResult(RaoResult.Status.SUCCESS);
        preventiveRaoResult.setPreOptimVariantId(initialVariantId);
        preventiveRaoResult.setPostOptimVariantId(postOptimPrevVariantId);

        RaoResult curativeRaoResult = new RaoResult(RaoResult.Status.SUCCESS);
        curativeRaoResult.setPreOptimVariantId(postOptimPrevVariantId);
        curativeRaoResult.setPostOptimVariantId(postOptimPrevVariantId);

        StateTree stateTree = mockedStateTree(crac);

        State curativeState = crac.getState("Contingency FR1 FR3", Instant.CURATIVE);
        new SearchTreeRaoProvider(stateTree).mergeRaoResults(crac, preventiveRaoResult,
                Map.of(curativeState, curativeRaoResult), 1000.);

        assertNotNull(crac.getExtension(CracResultExtension.class).getVariant(postOptimPrevVariantId));
    }

    @Test
    public void testMergeObjectiveFunctionCostWorstIsCurative() {
        RaoResult preventiveRaoResult = new RaoResult(RaoResult.Status.SUCCESS);
        preventiveRaoResult.setPreOptimVariantId(initialVariantId);
        preventiveRaoResult.setPostOptimVariantId(postOptimPrevVariantId);

        RaoResult curativeRaoResult1 = new RaoResult(RaoResult.Status.SUCCESS);
        curativeRaoResult1.setPreOptimVariantId(postOptimPrevVariantId);
        curativeRaoResult1.setPostOptimVariantId(postOptimCurVariantId);

        RaoResult curativeRaoResult2 = new RaoResult(RaoResult.Status.SUCCESS);
        curativeRaoResult2.setPreOptimVariantId(postOptimPrevVariantId);
        String postOptimCur2VariantId = crac.getExtension(ResultVariantManager.class).createNewUniqueVariantId("postOptim-cur-2");
        curativeRaoResult2.setPostOptimVariantId(postOptimCur2VariantId);

        StateTree stateTree = mockedStateTree(crac);

        State curativeState1 = crac.getState("Contingency FR1 FR3", Instant.CURATIVE);
        State curativeState2 = crac.getState("Contingency FR1 FR2", Instant.CURATIVE);

        CracResultExtension resultExtension = crac.getExtension(CracResultExtension.class);

        resultExtension.getVariant(postOptimPrevVariantId).setFunctionalCost(-1000);
        resultExtension.getVariant(postOptimPrevVariantId).setVirtualCost(10);

        resultExtension.getVariant(postOptimCurVariantId).setFunctionalCost(50);
        resultExtension.getVariant(postOptimCurVariantId).setVirtualCost(0);

        resultExtension.getVariant(postOptimCur2VariantId).setFunctionalCost(0);
        resultExtension.getVariant(postOptimCur2VariantId).setVirtualCost(100);

        RaoResult mergedRaoResult = new SearchTreeRaoProvider(stateTree).mergeRaoResults(crac, preventiveRaoResult,
                Map.of(curativeState1, curativeRaoResult1, curativeState2, curativeRaoResult2), 1000.);
        assertEquals(0, resultExtension.getVariant(mergedRaoResult.getPostOptimVariantId()).getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(100, resultExtension.getVariant(mergedRaoResult.getPostOptimVariantId()).getVirtualCost(), DOUBLE_TOLERANCE);
    }

    @Test
    public void testMergeObjectiveFunctionCostWorstIsPreventive() {
        RaoResult preventiveRaoResult = new RaoResult(RaoResult.Status.SUCCESS);
        preventiveRaoResult.setPreOptimVariantId(initialVariantId);
        preventiveRaoResult.setPostOptimVariantId(postOptimPrevVariantId);

        RaoResult curativeRaoResult1 = new RaoResult(RaoResult.Status.SUCCESS);
        curativeRaoResult1.setPreOptimVariantId(postOptimPrevVariantId);
        curativeRaoResult1.setPostOptimVariantId(postOptimCurVariantId);

        RaoResult curativeRaoResult2 = new RaoResult(RaoResult.Status.SUCCESS);
        curativeRaoResult2.setPreOptimVariantId(postOptimPrevVariantId);
        String postOptimCur2VariantId = crac.getExtension(ResultVariantManager.class).createNewUniqueVariantId("postOptim-cur-2");
        curativeRaoResult2.setPostOptimVariantId(postOptimCur2VariantId);

        StateTree stateTree = mockedStateTree(crac);

        State curativeState1 = crac.getState("Contingency FR1 FR3", Instant.CURATIVE);
        State curativeState2 = crac.getState("Contingency FR1 FR2", Instant.CURATIVE);

        CracResultExtension resultExtension = crac.getExtension(CracResultExtension.class);

        resultExtension.getVariant(postOptimPrevVariantId).setFunctionalCost(90);
        resultExtension.getVariant(postOptimPrevVariantId).setVirtualCost(10);

        resultExtension.getVariant(postOptimCurVariantId).setFunctionalCost(50);
        resultExtension.getVariant(postOptimCurVariantId).setVirtualCost(0);

        resultExtension.getVariant(postOptimCur2VariantId).setFunctionalCost(0);
        resultExtension.getVariant(postOptimCur2VariantId).setVirtualCost(100);

        RaoResult mergedRaoResult = new SearchTreeRaoProvider(stateTree).mergeRaoResults(crac, preventiveRaoResult,
                Map.of(curativeState1, curativeRaoResult1, curativeState2, curativeRaoResult2), 1000.);
        assertEquals(90, resultExtension.getVariant(mergedRaoResult.getPostOptimVariantId()).getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(10, resultExtension.getVariant(mergedRaoResult.getPostOptimVariantId()).getVirtualCost(), DOUBLE_TOLERANCE);
    }

    @Test
    public void testMergeObjectiveFunctionCostIgnorePerimetersWithPureMnecs() {
        Contingency contingency = crac.addContingency("pure_mnecs_cont", "BBE2AA1  FFR3AA1  1");
        BranchCnec mnec = crac.newBranchCnec().setId("pure_mnec")
                .setContingency(contingency).setInstant(Instant.CURATIVE)
                .newNetworkElement().setId("BBE2AA1  FFR3AA1  1").add()
                .newThreshold().setMax(1000.).setUnit(Unit.MEGAWATT).setRule(BranchThresholdRule.ON_LEFT_SIDE).add()
                .monitored().add();

        RaoResult preventiveRaoResult = new RaoResult(RaoResult.Status.SUCCESS);
        preventiveRaoResult.setPreOptimVariantId(initialVariantId);
        preventiveRaoResult.setPostOptimVariantId(postOptimPrevVariantId);

        RaoResult curativeRaoResult = new RaoResult(RaoResult.Status.SUCCESS);
        curativeRaoResult.setPreOptimVariantId(postOptimPrevVariantId);
        curativeRaoResult.setPostOptimVariantId(postOptimCurVariantId);

        State curativeState = crac.getState(contingency, Instant.CURATIVE);
        State curativeState2 = crac.getState("Contingency FR1 FR3", Instant.CURATIVE);

        StateTree stateTree = mockedStateTree(crac);
        Mockito.when(stateTree.getOptimizedState(curativeState)).thenReturn(curativeState);

        CracResultExtension resultExtension = crac.getExtension(CracResultExtension.class);

        resultExtension.getVariant(postOptimPrevVariantId).setFunctionalCost(-1000);
        resultExtension.getVariant(postOptimPrevVariantId).setVirtualCost(10);

        resultExtension.getVariant(postOptimCurVariantId).setFunctionalCost(0);
        resultExtension.getVariant(postOptimCurVariantId).setVirtualCost(0);

        CnecResultExtension mockCnecResultExtension = Mockito.mock(CnecResultExtension.class);
        CnecResult cnecResult = new CnecResult();
        Mockito.when(mockCnecResultExtension.getVariant(Mockito.anyString())).thenReturn(cnecResult);
        mnec.addExtension(CnecResultExtension.class, mockCnecResultExtension); // just to avoid null pointer

        RaoResult mergedRaoResult = new SearchTreeRaoProvider(stateTree).mergeRaoResults(crac, preventiveRaoResult,
                Map.of(curativeState, curativeRaoResult, curativeState2, preventiveRaoResult), 1000.);
        assertEquals(-1000, resultExtension.getVariant(mergedRaoResult.getPostOptimVariantId()).getFunctionalCost(), DOUBLE_TOLERANCE);
        assertEquals(10, resultExtension.getVariant(mergedRaoResult.getPostOptimVariantId()).getVirtualCost(), DOUBLE_TOLERANCE);
    }

    @Test
    public void testMergeDegradedResult1() {
        crac.getExtension(CracResultExtension.class).getVariant(postOptimPrevVariantId).setFunctionalCost(1010.);
        crac.getExtension(CracResultExtension.class).getVariant(postOptimPrevVariantId).setVirtualCost(0.);

        RaoResult preventiveRaoResult = new RaoResult(RaoResult.Status.SUCCESS);
        preventiveRaoResult.setPreOptimVariantId(initialVariantId);
        preventiveRaoResult.setPostOptimVariantId(postOptimPrevVariantId);

        RaoResult curativeRaoResult = new RaoResult(RaoResult.Status.SUCCESS);
        curativeRaoResult.setPreOptimVariantId(postOptimPrevVariantId);
        curativeRaoResult.setPostOptimVariantId(postOptimCurVariantId);

        StateTree stateTree = mockedStateTree(crac);

        State curativeState = crac.getState("Contingency FR1 FR3", Instant.CURATIVE);
        RaoResult mergedRaoResult = new SearchTreeRaoProvider(stateTree).mergeRaoResults(crac, preventiveRaoResult,
                Map.of(curativeState, curativeRaoResult), 1000.);

        assertEquals(RaoResult.Status.SUCCESS, mergedRaoResult.getStatus());
        assertEquals(initialVariantId, mergedRaoResult.getPreOptimVariantId());
        assertEquals(initialVariantId, mergedRaoResult.getPostOptimVariantId());
        assertNotNull(crac.getExtension(CracResultExtension.class).getVariant(initialVariantId));
        assertNull(crac.getExtension(CracResultExtension.class).getVariant(postOptimPrevVariantId));
        assertNull(crac.getExtension(CracResultExtension.class).getVariant(postOptimCurVariantId));
    }

    @Test
    public void testMergeDegradedResult2() {
        crac.getExtension(CracResultExtension.class).getVariant(postOptimCurVariantId).setFunctionalCost(0.);
        crac.getExtension(CracResultExtension.class).getVariant(postOptimCurVariantId).setVirtualCost(1001.);

        RaoResult preventiveRaoResult = new RaoResult(RaoResult.Status.SUCCESS);
        preventiveRaoResult.setPreOptimVariantId(initialVariantId);
        preventiveRaoResult.setPostOptimVariantId(postOptimPrevVariantId);

        RaoResult curativeRaoResult = new RaoResult(RaoResult.Status.SUCCESS);
        curativeRaoResult.setPreOptimVariantId(postOptimPrevVariantId);
        curativeRaoResult.setPostOptimVariantId(postOptimCurVariantId);

        StateTree stateTree = mockedStateTree(crac);

        State curativeState = crac.getState("Contingency FR1 FR3", Instant.CURATIVE);
        RaoResult mergedRaoResult = new SearchTreeRaoProvider(stateTree).mergeRaoResults(crac, preventiveRaoResult,
                Map.of(curativeState, curativeRaoResult), 1000.);

        assertEquals(RaoResult.Status.SUCCESS, mergedRaoResult.getStatus());
        assertEquals(initialVariantId, mergedRaoResult.getPreOptimVariantId());
        assertEquals(initialVariantId, mergedRaoResult.getPostOptimVariantId());
        assertNotNull(crac.getExtension(CracResultExtension.class).getVariant(initialVariantId));
        assertNull(crac.getExtension(CracResultExtension.class).getVariant(postOptimPrevVariantId));
        assertNull(crac.getExtension(CracResultExtension.class).getVariant(postOptimCurVariantId));
    }
}
