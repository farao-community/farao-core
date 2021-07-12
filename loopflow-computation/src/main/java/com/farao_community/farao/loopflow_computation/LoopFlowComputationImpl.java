/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.loopflow_computation;

import com.farao_community.farao.commons.EICode;
import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.commons.ZonalData;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.refprog.reference_program.ReferenceProgram;
import com.farao_community.farao.sensitivity_analysis.SystematicSensitivityInterface;
import com.farao_community.farao.sensitivity_analysis.SystematicSensitivityResult;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
    protected Network network;

    public LoopFlowComputationImpl(ZonalData<LinearGlsk> glsk, ReferenceProgram referenceProgram, Network network) {
        this.glsk = requireNonNull(glsk, "glskProvider should not be null");
        this.referenceProgram = requireNonNull(referenceProgram, "referenceProgram should not be null");
        this.network = network;
    }

    @Override
    public LoopFlowResult calculateLoopFlows(Network network, SensitivityAnalysisParameters sensitivityAnalysisParameters, Set<FlowCnec> flowCnecs) {
        this.network = network;

        SystematicSensitivityInterface systematicSensitivityInterface = SystematicSensitivityInterface.builder()
            .withDefaultParameters(sensitivityAnalysisParameters)
            .withPtdfSensitivities(glsk, flowCnecs, Collections.singleton(Unit.MEGAWATT))
            .build();

        SystematicSensitivityResult ptdfsAndRefFlows = systematicSensitivityInterface.run(network);

        return buildLoopFlowsFromReferenceFlowAndPtdf(ptdfsAndRefFlows, flowCnecs);
    }

    @Override
    public LoopFlowResult buildLoopFlowsFromReferenceFlowAndPtdf(SystematicSensitivityResult alreadyCalculatedPtdfAndFlows, Set<FlowCnec> flowCnecs) {
        LoopFlowResult results = new LoopFlowResult();
        for (FlowCnec flowCnec : flowCnecs) {
            double refFlow = alreadyCalculatedPtdfAndFlows.getReferenceFlow(flowCnec);
            double commercialFLow = getGlskStream(flowCnec).filter(entry -> isInMainComponent(entry.getValue(), network))
                .mapToDouble(entry -> alreadyCalculatedPtdfAndFlows.getSensitivityOnFlow(entry.getValue(), flowCnec) * referenceProgram.getGlobalNetPosition(entry.getKey()))
                .sum();
            results.addCnecResult(flowCnec, refFlow - commercialFLow, commercialFLow, refFlow);
        }
        return results;
    }

    static boolean isInMainComponent(LinearGlsk linearGlsk, Network network) {
        for (String glsk : linearGlsk.getGLSKs().keySet()) {
            Generator generator = network.getGenerator(glsk);
            if (generator != null) {
                if (generator.getTerminal().getBusView().getBus().isInMainConnectedComponent()) {
                    return true;
                }
            } else {
                Load load = network.getLoad(glsk);
                if (load != null && load.getTerminal().getBusView().getBus().isInMainConnectedComponent()) {
                    return true;
                }
            }
        }
        return false;
    }

    protected Stream<Map.Entry<EICode, LinearGlsk>> getGlskStream(FlowCnec flowCnec) {
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
