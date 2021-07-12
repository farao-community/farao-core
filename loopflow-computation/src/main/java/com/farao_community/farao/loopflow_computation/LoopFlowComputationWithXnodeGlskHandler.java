/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.loopflow_computation;

import com.farao_community.farao.commons.EICode;
import com.farao_community.farao.commons.ZonalData;
import com.farao_community.farao.data.crac_api.Contingency;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.refprog.reference_program.ReferenceProgram;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class LoopFlowComputationWithXnodeGlskHandler extends LoopFlowComputationImpl {

    private final XnodeGlskHandler xnodeGlskHandler;

    public LoopFlowComputationWithXnodeGlskHandler(ZonalData<LinearGlsk> glsk, ReferenceProgram referenceProgram, Set<Contingency> contingencies, Network network) {
        super(glsk, referenceProgram, network);
        xnodeGlskHandler = new XnodeGlskHandler(glsk, contingencies, network);
    }

    LoopFlowComputationWithXnodeGlskHandler(ZonalData<LinearGlsk> glsk, ReferenceProgram referenceProgram, XnodeGlskHandler xnodeGlskHandler) {
        super(glsk, referenceProgram, xnodeGlskHandler.getNetwork());
        this.xnodeGlskHandler = xnodeGlskHandler;
    }

    @Override
    protected Stream<Map.Entry<EICode, LinearGlsk>> getGlskStream(FlowCnec flowCnec) {
        return super.getGlskStream(flowCnec)
                .filter(entry -> xnodeGlskHandler.isLinearGlskValidForCnec(flowCnec, entry.getValue()));
    }
}
