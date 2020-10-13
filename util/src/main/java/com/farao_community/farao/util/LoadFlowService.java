/*
 * Copyright (c) 2018, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.util;

import com.powsybl.computation.DefaultComputationManagerConfig;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
public final class LoadFlowService {

    private LoadFlowService() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    public static LoadFlowResult runLoadFlow(Network network,
                                             String workingStateId,
                                             LoadFlowParameters loadFlowParameters) {
        return LoadFlow.run(network, workingStateId, DefaultComputationManagerConfig.load().createShortTimeExecutionComputationManager(), loadFlowParameters);
    }

    public static LoadFlowResult runLoadFlow(Network network,
                                             String workingStateId) {

        return runLoadFlow(network, workingStateId, LoadFlowParameters.load());
    }
}
