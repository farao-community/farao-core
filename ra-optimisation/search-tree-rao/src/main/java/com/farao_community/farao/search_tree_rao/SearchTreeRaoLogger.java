/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.search_tree_rao;

import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.range_action.PstRangeAction;
import com.farao_community.farao.data.crac_api.cnec.BranchCnec;
import com.farao_community.farao.data.crac_api.range_action.RangeAction;

import java.text.DecimalFormat;
import java.util.*;

import static java.lang.String.format;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
final class SearchTreeRaoLogger {

    private SearchTreeRaoLogger() {
    }

    static void logRangeActions(Leaf leaf, Set<RangeAction> rangeActions) {
        logRangeActions(leaf, rangeActions, null);
    }

    static void logRangeActions(Leaf leaf, Set<RangeAction> rangeActions, String prefix) {
        StringBuilder rangeActionMsg = new StringBuilder();
        if (prefix != null) {
            rangeActionMsg.append(prefix).append(" - ");
        }
        rangeActionMsg.append("Range action(s): ");
        rangeActions.forEach(rangeAction -> {
            String rangeActionName = rangeAction.getName();
            int rangeActionTap = leaf.getOptimizedTap((PstRangeAction) rangeAction);
            rangeActionMsg
                    .append(format("%s: %d", rangeActionName, rangeActionTap))
                    .append(" , ");
        });
        String rangeActionsLog = rangeActionMsg.toString();
        SearchTree.LOGGER.info(rangeActionsLog);
    }

    static void logMostLimitingElementsResults(Leaf leaf, Unit unit, boolean relativePositiveMargins, int numberOfLoggedElements) {
        List<BranchCnec> sortedCnecs = leaf.getMostLimitingElements(numberOfLoggedElements);

        for (int i = 0; i < sortedCnecs.size(); i++) {
            BranchCnec cnec = sortedCnecs.get(i);
            String cnecNetworkElementName = cnec.getNetworkElement().getName();
            String cnecStateId = cnec.getState().getId();
            double cnecMargin = relativePositiveMargins ? leaf.getRelativeMargin(cnec, unit) : leaf.getMargin(cnec, unit);

            String margin = new DecimalFormat("#0.00").format(cnecMargin);
            String isRelativeMargin = (relativePositiveMargins && cnecMargin > 0) ? "relative " : "";
            String ptdfIfRelative = (relativePositiveMargins && cnecMargin > 0) ? format("(PTDF %f)", leaf.getPtdfZonalSum(cnec)) : "";
            SearchTree.LOGGER.info("Limiting element #{}: element {} at state {} with a {}margin of {} {} {}",
                    i + 1,
                    cnecNetworkElementName,
                    cnecStateId,
                    isRelativeMargin,
                    margin,
                    unit,
                    ptdfIfRelative);
        }
    }
}
