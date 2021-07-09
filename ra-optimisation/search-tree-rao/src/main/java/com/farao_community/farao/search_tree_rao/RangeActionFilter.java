/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.search_tree_rao;

import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.RemedialAction;
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.crac_api.range_action.PstRangeAction;
import com.farao_community.farao.data.crac_api.range_action.RangeAction;
import com.farao_community.farao.rao_commons.result_api.SensitivityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidParameterException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Philippe Edwards {@literal <philippe.edwards at rte-france.com>}
 */
class RangeActionFilter {
    static final Logger LOGGER = LoggerFactory.getLogger(RangeActionFilter.class);

    private final Leaf leaf;
    private Set<RangeAction> rangeActionsToOptimize;
    private final State optimizedState;
    private final TreeParameters treeParameters;
    private final Map<RangeAction, Double> prePerimeterSetPoints;
    private final Set<RangeAction> leastPriorityRangeActions;

    public RangeActionFilter(Leaf leaf, Set<RangeAction> availableRangeActions, State optimizedState, TreeParameters treeParameters, Map<RangeAction, Double> prePerimeterSetPoints, boolean deprioritizeIgnoredRangeActions) {
        this.leaf = leaf;
        this.rangeActionsToOptimize = new HashSet<>(availableRangeActions);
        this.optimizedState = optimizedState;
        this.treeParameters = treeParameters;
        this.prePerimeterSetPoints = prePerimeterSetPoints;
        if (deprioritizeIgnoredRangeActions) {
            // define a lesser priority on range actions that have been ignored in previous optim
            this.leastPriorityRangeActions = availableRangeActions.stream().filter(ra -> leaf.getRangeActions().contains(ra) && leaf.getOptimizedSetPoint(ra) == prePerimeterSetPoints.get(ra)).collect(Collectors.toSet());
        } else {
            this.leastPriorityRangeActions = new HashSet<>();
        }
    }

    public Set<RangeAction> getRangeActionsToOptimize() {
        return rangeActionsToOptimize;
    }

    public void filterUnavailableRangeActions() {
        rangeActionsToOptimize.removeIf(ra -> !isRangeActionUsed(ra, leaf) && !SearchTree.isRemedialActionAvailable(ra, optimizedState, leaf));
    }

    public void filterPstPerTso() {
        Map<String, Integer> maxPstPerTso = recomputeMaxPstPerTso(leaf);
        if (maxPstPerTso.isEmpty()) {
            return;
        }
        // Filter the psts from Tso present in the map depending on their sensitivity
        maxPstPerTso.forEach((tso, maxPst) -> {
            Set<RangeAction> pstsForTso = rangeActionsToOptimize.stream()
                    .filter(rangeAction -> (rangeAction instanceof PstRangeAction) && rangeAction.getOperator().equals(tso))
                    .collect(Collectors.toSet());
            if (pstsForTso.size() > maxPst) {
                Set<RangeAction> rangeActionsToRemove = computeRangeActionsToRemove(pstsForTso, maxPst);
                if (!rangeActionsToRemove.isEmpty()) {
                    LOGGER.info("{} range actions have been filtered out in order to respect the maximum allowed number of pst for tso {}", rangeActionsToRemove.size(), tso);
                    rangeActionsToOptimize.removeAll(rangeActionsToRemove);
                }
            }
        });
    }

    /**
     * Create an updated version of maxPstPerTso map so as to deduce the number of applied network actions from the
     * total number of ra per tso and compare this value to max number of pst per Tso set in the treeParameters.
     */
    private Map<String, Integer> recomputeMaxPstPerTso(Leaf leaf) {

        Map<String, Integer> maxPstPerTso = new HashMap<>(treeParameters.getMaxPstPerTso());
        treeParameters.getMaxRaPerTso().forEach((tso, raLimit) -> {
            int appliedNetworkActionsForTso = (int) leaf.getActivatedNetworkActions().stream().filter(networkAction -> networkAction.getOperator().equals(tso)).count();
            int pstLimit = raLimit - appliedNetworkActionsForTso;
            maxPstPerTso.put(tso, Math.min(pstLimit, maxPstPerTso.getOrDefault(tso, Integer.MAX_VALUE)));
        });
        return maxPstPerTso;
    }

    public void filterTsos() {
        int maxTso = treeParameters.getMaxTso();
        if (maxTso == Integer.MAX_VALUE) {
            return;
        }
        Set<RangeAction> appliedRangeActions = rangeActionsToOptimize.stream().filter(rangeAction -> isRangeActionUsed(rangeAction, leaf)).collect(Collectors.toSet());
        Set<String> activatedTsos = leaf.getActivatedNetworkActions().stream().map(RemedialAction::getOperator).collect(Collectors.toSet());
        activatedTsos.addAll(appliedRangeActions.stream().map(RemedialAction::getOperator).collect(Collectors.toSet()));

        Set<String> tsosToKeep = new HashSet<>(activatedTsos);

        List<RangeAction> rangeActionsSortedBySensitivity = rangeActionsToOptimize.stream()
                .sorted((ra1, ra2) -> -compareAbsoluteSensitivities(ra1, ra2, leaf.getMostLimitingElements(1).get(0), leaf))
                .collect(Collectors.toList());
        for (RangeAction rangeAction : rangeActionsSortedBySensitivity) {
            if (tsosToKeep.size() >= maxTso) {
                break;
            }
            tsosToKeep.add(rangeAction.getOperator());
        }
        Set<RangeAction> rangeActionsToRemove = rangeActionsToOptimize.stream().filter(rangeAction -> !tsosToKeep.contains(rangeAction.getOperator())).collect(Collectors.toSet());
        if (!rangeActionsToRemove.isEmpty()) {
            LOGGER.info("{} range actions have been filtered out in order to respect the maximum allowed number of tsos", rangeActionsToRemove.size());
            rangeActionsToOptimize.removeAll(rangeActionsToRemove);
        }
    }

    public void filterMaxRas() {
        if (treeParameters.getMaxRa() == Integer.MAX_VALUE) {
            return;
        }
        int numberOfNetworkActionsAlreadyApplied = leaf.getActivatedNetworkActions().size();
        Set<RangeAction> rangeActionsToRemove = computeRangeActionsToRemove(rangeActionsToOptimize, treeParameters.getMaxRa() - numberOfNetworkActionsAlreadyApplied);
        if (!rangeActionsToRemove.isEmpty()) {
            LOGGER.info("{} range actions have been filtered out in order to respect the maximum allowed number of remedial actions", rangeActionsToRemove.size());
            rangeActionsToOptimize.removeAll(rangeActionsToRemove);
        }

    }

    private Set<RangeAction> computeRangeActionsToRemove(Set<RangeAction> rangeActionsToFilter, int numberOfRangeActionsToKeep) {
        if (numberOfRangeActionsToKeep < 0) {
            throw new InvalidParameterException("Trying to keep a negative number of remedial actions");
        }
        // If in previous depth some RangeActions were activated, consider them optimizable and decrement the allowed number of PSTs
        // We have to do this because at the end of every depth, we apply optimal RangeActions for the next depth
        Set<RangeAction> rangeActionsToRemove = new HashSet<>(rangeActionsToFilter);
        Set<RangeAction> appliedRangeActions = rangeActionsToFilter.stream().filter(rangeAction -> isRangeActionUsed(rangeAction, leaf)).collect(Collectors.toSet());
        int updatedNumberOfRangeActionsToKeep = numberOfRangeActionsToKeep - appliedRangeActions.size();
        if (updatedNumberOfRangeActionsToKeep > rangeActionsToFilter.size()) {
            return new HashSet<>();
        } else if (updatedNumberOfRangeActionsToKeep < 0) {
            return rangeActionsToFilter;
        } else {
            rangeActionsToRemove.removeAll(appliedRangeActions);
            updatedNumberOfRangeActionsToKeep = Math.min(updatedNumberOfRangeActionsToKeep, rangeActionsToRemove.size());
            List<RangeAction> rangeActionsSortedBySensitivity = rangeActionsToRemove.stream()
                .sorted((ra1, ra2) -> comparePrioritiesAndSensitivities(ra1, ra2, leaf.getMostLimitingElements(1).get(0), leaf))
                .collect(Collectors.toList());
            rangeActionsToRemove.removeAll(rangeActionsSortedBySensitivity.subList(0, updatedNumberOfRangeActionsToKeep));
            return rangeActionsToRemove;
        }
    }

    /**
     * First compares priority then sensi
     * If a range action has more priority (depending on the contents of leastPriorityRangeActions) than the other, then
     * it will be considered greater.
     * If both RAs have the same priority, then absolute sensitivities will be compared.
     */
    private int comparePrioritiesAndSensitivities(RangeAction ra1, RangeAction ra2, FlowCnec cnec, SensitivityResult sensitivityResult) {
        if (!leastPriorityRangeActions.contains(ra1) && leastPriorityRangeActions.contains(ra2)) {
            return -1;
        } else if (leastPriorityRangeActions.contains(ra1) && !leastPriorityRangeActions.contains(ra2)) {
            return 1;
        } else {
            return -compareAbsoluteSensitivities(ra1, ra2, cnec, sensitivityResult);
        }
    }

    private int compareAbsoluteSensitivities(RangeAction ra1, RangeAction ra2, FlowCnec cnec, SensitivityResult sensitivityResult) {
        Double sensi1 = Math.abs(sensitivityResult.getSensitivityValue(cnec, ra1, Unit.MEGAWATT));
        Double sensi2 = Math.abs(sensitivityResult.getSensitivityValue(cnec, ra2, Unit.MEGAWATT));
        return sensi1.compareTo(sensi2);
    }

    boolean isRangeActionUsed(RangeAction rangeAction, Leaf leaf) {
        return leaf.getRangeActions().contains(rangeAction) && leaf.getOptimizedSetPoint(rangeAction) != prePerimeterSetPoints.get(rangeAction);
    }
}
