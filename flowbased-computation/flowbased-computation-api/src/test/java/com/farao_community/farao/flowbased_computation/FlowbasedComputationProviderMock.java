/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.flowbased_computation;

import com.farao_community.farao.commons.ZonalData;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.flowbased_domain.DataDomain;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.google.auto.service.AutoService;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

/**
 * @author Sebastien Murgey <sebastien.murgey at rte-france.com>
 */
@AutoService(FlowbasedComputationProvider.class)
public class FlowbasedComputationProviderMock implements FlowbasedComputationProvider {

    @Override
    public CompletableFuture<FlowbasedComputationResult> run(Network network, Crac crac, RaoResult raoResult, ZonalData<LinearGlsk> glsk, FlowbasedComputationParameters parameters) {
        return CompletableFuture.completedFuture(new FlowbasedComputationResultImpl(FlowbasedComputationResult.Status.SUCCESS, Mockito.mock(DataDomain.class)));
    }

    @Override
    public String getName() {
        return "FlowBasedComputationMock";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }
}
