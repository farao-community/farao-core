/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.linear_range_action_rao;

import com.farao_community.farao.ra_optimisation.RaoComputationResult;
import com.powsybl.commons.extensions.AbstractExtension;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class LinearRangeActionRaoResult extends AbstractExtension<RaoComputationResult> {

    public enum SecurityStatus {
        SECURED,
        UNSECURED
    }

    private final double cost;
    private SecurityStatus securityStatus;

    @Override
    public String getName() {
        return "LinearRangeActionRaoResult";
    }

    public LinearRangeActionRaoResult(double cost) {
        this.cost = cost;
    }

    public LinearRangeActionRaoResult(SecurityStatus securityStatus) {
        this.securityStatus = securityStatus;
        cost = 0;
    }

    public double getCost() {
        return cost;
    }

    public void setSecurityStatus(SecurityStatus securityStatus) {
        this.securityStatus = securityStatus;
    }
}
