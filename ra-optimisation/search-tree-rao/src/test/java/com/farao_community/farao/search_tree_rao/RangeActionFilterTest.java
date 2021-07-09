/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.search_tree_rao;

import com.farao_community.farao.data.crac_api.Instant;
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.crac_api.network_action.NetworkAction;
import com.farao_community.farao.data.crac_api.range_action.PstRangeAction;
import com.farao_community.farao.data.crac_api.range_action.RangeAction;
import com.farao_community.farao.data.crac_api.usage_rule.OnFlowConstraint;
import com.farao_community.farao.data.crac_api.usage_rule.UsageMethod;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class RangeActionFilterTest {

    private Leaf leaf;
    private Set<RangeAction> availableRangeActions;
    private TreeParameters treeParameters;
    private Map<RangeAction, Double> prePerimeterSetPoints;

    private Set<NetworkAction> appliedNetworkActions;
    private Set<RangeAction> leafRangeActions;

    RangeActionFilter rangeActionFilter;

    @Before
    public void setUp() {
        treeParameters = Mockito.mock(TreeParameters.class);
        setTreeParameters(Integer.MAX_VALUE, Integer.MAX_VALUE, new HashMap<>(), new HashMap<>());

        leaf = Mockito.mock(Leaf.class);
        Mockito.when(leaf.getMostLimitingElements(Mockito.anyInt())).thenReturn(Collections.singletonList(Mockito.mock(FlowCnec.class)));
        appliedNetworkActions = new HashSet<>();
        leafRangeActions = new HashSet<>();
        Mockito.when(leaf.getActivatedNetworkActions()).thenReturn(appliedNetworkActions);
        Mockito.when(leaf.getRangeActions()).thenReturn(leafRangeActions);

        availableRangeActions = new HashSet<>();
        prePerimeterSetPoints = new HashMap<>();
    }

    private void setTreeParameters(int maxRa, int maxTso, Map<String, Integer> maxPstPerTso, Map<String, Integer> maxRaPerTso) {
        Mockito.when(treeParameters.getMaxRa()).thenReturn(maxRa);
        Mockito.when(treeParameters.getMaxTso()).thenReturn(maxTso);
        Mockito.when(treeParameters.getMaxPstPerTso()).thenReturn(maxPstPerTso);
        Mockito.when(treeParameters.getMaxRaPerTso()).thenReturn(maxRaPerTso);
    }

    private PstRangeAction addPstRangeAction(String operator, double prePerimeterSetPoint, double optimizedSetPoint, double sensitivity) {
        PstRangeAction rangeAction = Mockito.mock(PstRangeAction.class);
        Mockito.when(rangeAction.getOperator()).thenReturn(operator);
        Mockito.when(leaf.getOptimizedSetPoint(rangeAction)).thenReturn(optimizedSetPoint);
        prePerimeterSetPoints.put(rangeAction, prePerimeterSetPoint);
        availableRangeActions.add(rangeAction);
        leafRangeActions.add(rangeAction);
        Mockito.when(leaf.getSensitivityValue(Mockito.any(), Mockito.eq(rangeAction), Mockito.any())).thenReturn(sensitivity);
        return rangeAction;
    }

    private NetworkAction addAppliedNetworkAction(String operator) {
        NetworkAction networkAction = Mockito.mock(NetworkAction.class);
        Mockito.when(networkAction.getOperator()).thenReturn(operator);
        appliedNetworkActions.add(networkAction);
        return networkAction;
    }

    @Test
    public void testIsRangeActionUsed() {
        PstRangeAction pstfr = addPstRangeAction("fr", 0, 0, 0);
        PstRangeAction pstbe = addPstRangeAction("be", 0, 3, 0);
        PstRangeAction pstnl = addPstRangeAction("nl", 0, 3, 0);
        leafRangeActions.remove(pstnl);

        rangeActionFilter = new RangeActionFilter(leaf, availableRangeActions, Mockito.mock(State.class), treeParameters, prePerimeterSetPoints, false);

        assertFalse(rangeActionFilter.isRangeActionUsed(pstfr, leaf));
        assertTrue(rangeActionFilter.isRangeActionUsed(pstbe, leaf));
        assertFalse(rangeActionFilter.isRangeActionUsed(pstnl, leaf));
    }

    @Test
    public void testFilterPstPerTsoAllPstAllowed() {
        PstRangeAction pstfr1 = addPstRangeAction("fr", 0, 0, 1);
        PstRangeAction pstfr2 = addPstRangeAction("fr", 0, 0, 2);
        PstRangeAction pstfr3 = addPstRangeAction("fr", 0, 0, 3);

        Map<String, Integer> maxPstPerTso = new HashMap<>();
        maxPstPerTso.put("fr", 5);
        setTreeParameters(Integer.MAX_VALUE, Integer.MAX_VALUE, maxPstPerTso, new HashMap<>());

        rangeActionFilter = new RangeActionFilter(leaf, availableRangeActions, Mockito.mock(State.class), treeParameters, prePerimeterSetPoints, false);
        rangeActionFilter.filterPstPerTso();
        Set<RangeAction> filteredRangeActions = rangeActionFilter.getRangeActionsToOptimize();

        assertEquals(3, filteredRangeActions.size());
        assertTrue(filteredRangeActions.contains(pstfr1));
        assertTrue(filteredRangeActions.contains(pstfr2));
        assertTrue(filteredRangeActions.contains(pstfr3));
    }

    @Test
    public void testFilterPstPerTsoNotInConfig() {
        PstRangeAction pstfr1 = addPstRangeAction("fr", 0, 0, 1);
        PstRangeAction pstfr2 = addPstRangeAction("fr", 0, 0, 2);
        PstRangeAction pstfr3 = addPstRangeAction("fr", 0, 0, 3);

        setTreeParameters(Integer.MAX_VALUE, Integer.MAX_VALUE, new HashMap<>(), new HashMap<>());

        rangeActionFilter = new RangeActionFilter(leaf, availableRangeActions, Mockito.mock(State.class), treeParameters, prePerimeterSetPoints, false);
        rangeActionFilter.filterPstPerTso();
        Set<RangeAction> filteredRangeActions = rangeActionFilter.getRangeActionsToOptimize();

        assertEquals(3, filteredRangeActions.size());
        assertTrue(filteredRangeActions.contains(pstfr1));
        assertTrue(filteredRangeActions.contains(pstfr2));
        assertTrue(filteredRangeActions.contains(pstfr3));
    }

    @Test
    public void testFilterPstPerTsoKeepAppliedAndHighestAbsSensi() {
        PstRangeAction pstfr1 = addPstRangeAction("fr", 0, 0, 1);
        PstRangeAction pstfr2 = addPstRangeAction("fr", 0, 3, 2);
        PstRangeAction pstfr3 = addPstRangeAction("fr", 0, 0, 3);
        PstRangeAction pstfr4 = addPstRangeAction("fr", 0, 0, -4);

        Map<String, Integer> maxPstPerTso = new HashMap<>();
        maxPstPerTso.put("fr", 2);
        setTreeParameters(Integer.MAX_VALUE, Integer.MAX_VALUE, maxPstPerTso, new HashMap<>());

        rangeActionFilter = new RangeActionFilter(leaf, availableRangeActions, Mockito.mock(State.class), treeParameters, prePerimeterSetPoints, false);
        rangeActionFilter.filterPstPerTso();
        Set<RangeAction> filteredRangeActions = rangeActionFilter.getRangeActionsToOptimize();

        assertEquals(2, filteredRangeActions.size());
        assertTrue(filteredRangeActions.contains(pstfr2));
        assertTrue(filteredRangeActions.contains(pstfr4));
    }

    @Test
    public void testFilterPstPerTsoNetworkActionApplied() {
        PstRangeAction pstfr1 = addPstRangeAction("fr", 0, 0, 1);
        PstRangeAction pstfr2 = addPstRangeAction("fr", 0, 3, 2);
        PstRangeAction pstfr3 = addPstRangeAction("fr", 0, 0, 3);
        PstRangeAction pstfr4 = addPstRangeAction("fr", 0, 0, -4);

        addAppliedNetworkAction("fr");

        Map<String, Integer> maxPstPerTso = new HashMap<>();
        maxPstPerTso.put("fr", 4);
        Map<String, Integer> maxRaPerTso = new HashMap<>();
        maxRaPerTso.put("fr", 3);
        setTreeParameters(Integer.MAX_VALUE, Integer.MAX_VALUE, maxPstPerTso, maxRaPerTso);

        rangeActionFilter = new RangeActionFilter(leaf, availableRangeActions, Mockito.mock(State.class), treeParameters, prePerimeterSetPoints, false);
        rangeActionFilter.filterPstPerTso();
        Set<RangeAction> filteredRangeActions = rangeActionFilter.getRangeActionsToOptimize();

        assertEquals(2, filteredRangeActions.size());
        assertTrue(filteredRangeActions.contains(pstfr2));
        assertTrue(filteredRangeActions.contains(pstfr4));
    }

    @Test
    public void testFilterTsos() {
        // fr psts are kept because one pst is activated
        // be psts are kept because one network action is activated
        // de psts are kept because between de and nl, de has the highest sensitivity pst
        PstRangeAction pstfr1 = addPstRangeAction("fr", 0, 3, 1);
        PstRangeAction pstfr2 = addPstRangeAction("fr", 0, 0, 2);
        PstRangeAction pstbe1 = addPstRangeAction("be", 0, 0, 3);
        PstRangeAction pstbe2 = addPstRangeAction("be", 0, 0, 4);
        PstRangeAction pstnl1 = addPstRangeAction("nl", 0, 0, 5);
        PstRangeAction pstnl2 = addPstRangeAction("nl", 0, 0, 6);
        PstRangeAction pstde1 = addPstRangeAction("de", 0, 0, 0);
        PstRangeAction pstde2 = addPstRangeAction("de", 0, 0, 7);

        addAppliedNetworkAction("be");

        setTreeParameters(Integer.MAX_VALUE, 3, new HashMap<>(), new HashMap<>());

        rangeActionFilter = new RangeActionFilter(leaf, availableRangeActions, Mockito.mock(State.class), treeParameters, prePerimeterSetPoints, false);
        rangeActionFilter.filterTsos();
        Set<RangeAction> filteredRangeActions = rangeActionFilter.getRangeActionsToOptimize();

        assertEquals(6, filteredRangeActions.size());
        assertTrue(filteredRangeActions.contains(pstfr1));
        assertTrue(filteredRangeActions.contains(pstfr2));
        assertTrue(filteredRangeActions.contains(pstbe1));
        assertTrue(filteredRangeActions.contains(pstbe2));
        assertTrue(filteredRangeActions.contains(pstde1));
        assertTrue(filteredRangeActions.contains(pstde2));
    }

    @Test
    public void testFilterMaxRas() {
        // We can only keep 2 psts because one network action was activated
        // pst1 is kept because it was activated
        // pst3 is kept because it has the highest sensi
        PstRangeAction pstfr1 = addPstRangeAction("fr", 0, 3, 1);
        PstRangeAction pstfr2 = addPstRangeAction("fr", 0, 0, 2);
        PstRangeAction pstfr3 = addPstRangeAction("fr", 0, 0, 3);

        addAppliedNetworkAction("fr");

        setTreeParameters(3, Integer.MAX_VALUE, new HashMap<>(), new HashMap<>());

        rangeActionFilter = new RangeActionFilter(leaf, availableRangeActions, Mockito.mock(State.class), treeParameters, prePerimeterSetPoints, false);
        rangeActionFilter.filterMaxRas();
        Set<RangeAction> filteredRangeActions = rangeActionFilter.getRangeActionsToOptimize();

        assertEquals(2, filteredRangeActions.size());
        assertTrue(filteredRangeActions.contains(pstfr1));
        assertTrue(filteredRangeActions.contains(pstfr3));
    }

    @Test
    public void testFilterWithPriorities() {
        PstRangeAction pstfr1 = addPstRangeAction("fr", 0, 0, 1);
        PstRangeAction pstfr2 = addPstRangeAction("fr", 0, 0, 2);
        PstRangeAction pstfr3 = addPstRangeAction("fr", 0, 0, 3);
        Mockito.when(leaf.getRangeActions()).thenReturn(Set.of(pstfr2, pstfr3));
        setTreeParameters(2, Integer.MAX_VALUE, new HashMap<>(), new HashMap<>());

        // initially pstfr2 and pstfr3 were available but not used
        // the filter should prioritize pstfr1 even though it has the smallest sensi
        rangeActionFilter = new RangeActionFilter(leaf, availableRangeActions, Mockito.mock(State.class), treeParameters, prePerimeterSetPoints, true);
        rangeActionFilter.filterMaxRas();
        assertEquals(Set.of(pstfr1, pstfr3), rangeActionFilter.getRangeActionsToOptimize());

        // no priority, compare sensi
        rangeActionFilter = new RangeActionFilter(leaf, availableRangeActions, Mockito.mock(State.class), treeParameters, prePerimeterSetPoints, false);
        rangeActionFilter.filterMaxRas();
        assertEquals(Set.of(pstfr2, pstfr3), rangeActionFilter.getRangeActionsToOptimize());
    }

    @Test
    public void testFilterUnavailableRangeActions() {
        State optimizedState = mock(State.class);
        when(optimizedState.getInstant()).thenReturn(Instant.CURATIVE);

        RangeActionFilter filter;

        FlowCnec flowCnec = mock(FlowCnec.class);

        RangeAction ra1 = mock(RangeAction.class);
        when(ra1.getUsageMethod(optimizedState)).thenReturn(UsageMethod.AVAILABLE);

        OnFlowConstraint onFlowConstraint = mock(OnFlowConstraint.class);
        when(onFlowConstraint.getInstant()).thenReturn(Instant.CURATIVE);
        when(onFlowConstraint.getFlowCnec()).thenReturn(flowCnec);
        when(onFlowConstraint.getUsageMethod(optimizedState)).thenReturn(UsageMethod.TO_BE_EVALUATED);
        RangeAction ra2 = mock(RangeAction.class);
        when(ra2.getUsageMethod(optimizedState)).thenReturn(UsageMethod.TO_BE_EVALUATED);
        when(ra2.getUsageRules()).thenReturn(List.of(onFlowConstraint));

        Leaf leaf = mock(Leaf.class);
        when(leaf.getRangeActions()).thenReturn(Set.of(ra1, ra2));
        when(leaf.getOptimizedSetPoint(ra1)).thenReturn(0.);
        when(leaf.getOptimizedSetPoint(ra2)).thenReturn(0.);

        // only ra1 should be available if margin on cnec is positive
        when(leaf.getMargin(eq(flowCnec), any())).thenReturn(10.);
        filter = new RangeActionFilter(leaf, Set.of(ra1, ra2), optimizedState, mock(TreeParameters.class), Map.of(ra1, 0., ra2, 0.), false);
        filter.filterUnavailableRangeActions();
        assertEquals(Set.of(ra1), filter.getRangeActionsToOptimize());

        // unless ra2 was used previously
        when(leaf.getOptimizedSetPoint(ra2)).thenReturn(10.);
        filter = new RangeActionFilter(leaf, Set.of(ra1, ra2), optimizedState, mock(TreeParameters.class), Map.of(ra1, 0., ra2, 0.), false);
        filter.filterUnavailableRangeActions();
        assertEquals(Set.of(ra1, ra2), filter.getRangeActionsToOptimize());

        // now both ra1 and ra2 should be available because margin on cnec becomes negative
        when(leaf.getOptimizedSetPoint(ra2)).thenReturn(0.);
        when(leaf.getMargin(eq(flowCnec), any())).thenReturn(0.);
        filter = new RangeActionFilter(leaf, Set.of(ra1, ra2), optimizedState, mock(TreeParameters.class), Map.of(ra1, 0., ra2, 0.), false);
        filter.filterUnavailableRangeActions();
    }

    @Test
    public void testDontFailIfAllRangeActionsUsed() {
        Mockito.when(treeParameters.getMaxRa()).thenReturn(3);
        PstRangeAction pst1 = addPstRangeAction("op", 0, 3, 0);
        PstRangeAction pst2 = addPstRangeAction("op", 0, 3, 0);
        when(leaf.getRangeActions()).thenReturn(Set.of(pst1, pst2));
        rangeActionFilter = new RangeActionFilter(leaf, availableRangeActions, Mockito.mock(State.class), treeParameters, prePerimeterSetPoints, false);
        rangeActionFilter.filterMaxRas();
        assertEquals(availableRangeActions, rangeActionFilter.getRangeActionsToOptimize());
    }

}
