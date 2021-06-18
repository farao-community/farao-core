/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.rao_api.results;

import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.rao_api.parameters.RaoParameters;

import java.util.List;
import java.util.Set;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public interface ObjectiveFunctionResult {

    /**
     * It gives the global cost of the situation according to the objective function defined in
     * the {@link RaoParameters}.
     *
     * @return The global cost of the situation.
     */
    default double getCost() {
        return getFunctionalCost() + getVirtualCost();
    }

    /**
     * It gives the functional cost of the situation according to the objective function defined in the
     * {@link RaoParameters}. It represents the main part of the objective function.
     *
     * @return The functional cost of the situation.
     */
    double getFunctionalCost();

    List<FlowCnec> getMostLimitingElements(int number);

    /**
     * It gives the sum of virtual costs of the situation according to the objective function defined in
     * the {@link RaoParameters}. It represents the secondary parts of the objective function.
     *
     * @return The global virtual cost of the situation.
     */
    double getVirtualCost();

    /**
     * It gives the names of the different virtual cost implied in the objective function defined in
     * the {@link RaoParameters}.
     *
     * @return The set of virtual cost names.
     */
    Set<String> getVirtualCostNames();

    /**
     * It gives the specified virtual cost of the situation. It represents the secondary parts of the objective. If the
     * specified name is not part of the virtual costs defined in the objective function, this method could
     * return {@code Double.NaN} values.
     *
     * @param virtualCostName: The name of the virtual cost.
     * @return The specific virtual cost of the situation.
     */
    double getVirtualCost(String virtualCostName);

    /**
     * It gives an ordered list of the costly {@link FlowCnec} according to the specified virtual cost. If the
     * virtual is null the list would be empty. If the specified virtual cost does not imply any branch in its
     * computation the list would be empty. Elements with a null virtual cost are not present in the list.
     *
     * @param virtualCostName: The name of the virtual cost.
     * @param number: The size of the list to be studied, so the number of costly elements to be retrieved.
     * @return The ordered list of the n first costly elements according to the given virtual cost.
     */
    List<FlowCnec> getCostlyElements(String virtualCostName, int number);
}
