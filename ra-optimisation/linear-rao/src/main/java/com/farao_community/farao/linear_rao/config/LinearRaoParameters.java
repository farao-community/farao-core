/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.linear_rao.config;

import com.farao_community.farao.rao_api.RaoParameters;
import com.powsybl.commons.extensions.AbstractExtension;
import com.powsybl.sensitivity.SensitivityComputationParameters;

import java.util.Objects;

import static java.lang.Math.max;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class LinearRaoParameters extends AbstractExtension<RaoParameters> {

    static final int DEFAULT_MAX_NUMBER_OF_ITERATIONS = 10;

    private SensitivityComputationParameters sensitivityComputationParameters = new SensitivityComputationParameters();
    private int maxIterations = DEFAULT_MAX_NUMBER_OF_ITERATIONS;

    @Override
    public String getName() {
        return "LinearRaoParameters";
    }

    public SensitivityComputationParameters getSensitivityComputationParameters() {
        return sensitivityComputationParameters;
    }

    public LinearRaoParameters setSensitivityComputationParameters(SensitivityComputationParameters sensiParameters) {
        this.sensitivityComputationParameters = Objects.requireNonNull(sensiParameters);
        return this;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public LinearRaoParameters setMaxIterations(int maxIterations) {
        this.maxIterations = max(1, maxIterations);
        return this;
    }
}
