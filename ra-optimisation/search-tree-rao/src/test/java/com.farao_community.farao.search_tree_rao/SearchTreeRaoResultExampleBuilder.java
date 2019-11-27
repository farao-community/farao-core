/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.search_tree_rao;

import com.farao_community.farao.ra_optimisation.RaoComputationResult;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 */
public final class SearchTreeRaoResultExampleBuilder {
    private SearchTreeRaoResultExampleBuilder() {
        throw new AssertionError("No empty constructor for utility class");
    }

    public static SearchTreeRaoResult buildResult() {
        RaoComputationResult raoComputationResult = new RaoComputationResult(RaoComputationResult.Status.SUCCESS);
        return new SearchTreeRaoResult(SearchTreeRaoResult.ComputationStatus.SECURE, SearchTreeRaoResult.StopCriterion.NO_COMPUTATION, raoComputationResult);
    }
}
