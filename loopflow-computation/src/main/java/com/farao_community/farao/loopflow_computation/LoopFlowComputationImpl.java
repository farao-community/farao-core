/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.loopflow_computation;

import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.commons.ZonalData;
import com.farao_community.farao.data.crac_api.cnec.BranchCnec;

import com.farao_community.farao.data.refprog.reference_program.ReferenceProgram;
import com.farao_community.farao.commons.EICode;
import com.farao_community.farao.sensitivity_analysis.SystematicSensitivityInterface;
import com.farao_community.farao.sensitivity_analysis.SystematicSensitivityResult;
import com.powsybl.iidm.network.Network;

import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * @author Pengbo Wang {@literal <pengbo.wang at rte-international.com>}
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public class LoopFlowComputationImpl implements LoopFlowComputation {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoopFlowComputationImpl.class);

    protected ZonalData<LinearGlsk> glsk;
    protected ReferenceProgram referenceProgram;

    public LoopFlowComputationImpl(ZonalData<LinearGlsk> glsk, ReferenceProgram referenceProgram) {
        this.glsk = requireNonNull(glsk, "glskProvider should not be null");
        this.referenceProgram = requireNonNull(referenceProgram, "referenceProgram should not be null");
    }

    @Override
    public LoopFlowResult calculateLoopFlows(Network network, SensitivityAnalysisParameters sensitivityAnalysisParameters, Set<BranchCnec> cnecs) {
        SystematicSensitivityInterface systematicSensitivityInterface = SystematicSensitivityInterface.builder()
            .withDefaultParameters(sensitivityAnalysisParameters)
            .withPtdfSensitivities(glsk, cnecs, Collections.singleton(Unit.MEGAWATT))
            .build();

        SystematicSensitivityResult ptdfsAndRefFlows = systematicSensitivityInterface.run(network);

        return buildLoopFlowsFromReferenceFlowAndPtdf(ptdfsAndRefFlows, cnecs);
    }

    @Override
    public LoopFlowResult buildLoopFlowsFromReferenceFlowAndPtdf(SystematicSensitivityResult alreadyCalculatedPtdfAndFlows, Set<BranchCnec> cnecs) {
        LoopFlowResult results = new LoopFlowResult();

        for (BranchCnec cnec : cnecs) {
            double refFlow = alreadyCalculatedPtdfAndFlows.getReferenceFlow(cnec);
            double commercialFLow = getGlskStream(cnec)
                .mapToDouble(entry -> alreadyCalculatedPtdfAndFlows.getSensitivityOnFlow(entry.getValue(), cnec) * referenceProgram.getGlobalNetPosition(entry.getKey()))
                .sum();
            results.addCnecResult(cnec, refFlow - commercialFLow, commercialFLow, refFlow);
        }
        return results;
    }

    protected Stream<Map.Entry<EICode, LinearGlsk>> getGlskStream(BranchCnec cnec) {
        return buildRefProgGlskMap().entrySet().stream();
    }

    protected Map<EICode, LinearGlsk> buildRefProgGlskMap() {

        Map<EICode, LinearGlsk> refProgGlskMap = new HashMap<>();

        for (EICode area : referenceProgram.getListOfAreas()) {
            LinearGlsk glskForArea = glsk.getData(area.getAreaCode());
            if (glskForArea == null) {
                LOGGER.warn("No GLSK found for reference area {}", area.getAreaCode());
            } else {
                refProgGlskMap.put(area, glskForArea);
            }
        }
        return refProgGlskMap;
    }
}