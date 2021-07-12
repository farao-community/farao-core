/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.loopflow_computation;

import com.farao_community.farao.commons.ZonalData;
import com.farao_community.farao.data.crac_api.Contingency;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.virtual_hubs.network_extension.AssignedVirtualHub;
import com.powsybl.iidm.network.Injection;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
class XnodeGlskHandler {

    /*
    This class enables to filter LinearGlsk who acts on a virtual hub which has already been disconnected
    by a Contingency.

    It has initially been developed to fix an issue related to CORE Alegro hubs
    - Alegro Hubs are in the RefProg file (as virtual hubs), with a GLSK in one Xnode
    - Alegro Hubs are disconnected in one contingency, the N-1 on the HVDC Alegro

    For the Cnec monitored in states after this contingency, the net position of the Alegro virtual hubs should
    not be shifted to the amount defined in the refProg, as the N-1 on the HVDC already implicitly shifts to zero
    the net positions on these hubs.

    Warn:
    - this class only works for UCTE data !
    */

    private static final Logger LOGGER = LoggerFactory.getLogger(XnodeGlskHandler.class);

    private Map<Contingency, List<String>> invalidGlskPerContingency;
    private ZonalData<LinearGlsk> glskZonalData;
    private Set<Contingency> contingencies;
    private Network network;

    public Network getNetwork() {
        return network;
    }

    XnodeGlskHandler(ZonalData<LinearGlsk> glskZonalData, Set<Contingency> contingencies, Network network) {
        this.glskZonalData = glskZonalData;
        this.contingencies = contingencies;
        this.network = network;
        this.invalidGlskPerContingency = buildInvalidGlskPerContingency();
    }

    boolean isLinearGlskValidForCnec(FlowCnec cnec, LinearGlsk linearGlsk) {

        Optional<Contingency> optContingency = cnec.getState().getContingency();
        if (optContingency.isEmpty()) {
            return true;
        }
        return !invalidGlskPerContingency.get(optContingency.get()).contains(linearGlsk.getId());
    }

    private Map<Contingency, List<String>> buildInvalidGlskPerContingency() {

        Map<Contingency, List<String>> outputMap = new HashMap<>();

        contingencies.forEach(contingency -> outputMap.put(contingency, getInvalidGlsksForContingency(contingency)));

        return outputMap;
    }

    private List<String> getInvalidGlsksForContingency(Contingency contingency) {
        List<String> xNodesInContingency = getXNodeInContingency(contingency);
        List<String> invalidGlsk = new ArrayList<>();

        glskZonalData.getDataPerZone().forEach((k, linearGlsk) -> {
            if (!isGlskValid(linearGlsk, xNodesInContingency)) {
                LOGGER.info("NetPosition of zone {} will not be shifted after contingency {}, as it acts on a Xnode which has already been disconnected by the contingency", linearGlsk.getId(), contingency.getId());
                invalidGlsk.add(linearGlsk.getId());
            }
        });

        return invalidGlsk;
    }

    private boolean isGlskValid(LinearGlsk linearGlsk, List<String> xNodesInContingency) {

        // if the linearGlsk is not related to only one, the linearGlsk is considered valid
        if (linearGlsk.getGLSKs().size() > 1) {
            return true;
        }

        // if the linearGlsk is on a virtualHub present in the contingency, the linearGlsk is invalid
        String glskInjectionId = linearGlsk.getGLSKs().keySet().iterator().next();

        if (network.getIdentifiable(glskInjectionId) instanceof Injection<?>) {
            Injection<?> injection = (Injection) network.getIdentifiable(glskInjectionId);
            AssignedVirtualHub virtualHub = injection.getExtension(AssignedVirtualHub.class);

            if (virtualHub != null && xNodesInContingency.contains(virtualHub.getNodeName())) {
                return false;
            }

        }
        return true;
    }

    private List<String> getXNodeInContingency(Contingency contingency) {
        // Warn: only works with UCTE data
        List<String> xNodeInContingency = new ArrayList<>();
        contingency.getNetworkElements().forEach(ne -> xNodeInContingency.addAll(getXnodeInId(ne.getId())));
        return xNodeInContingency;
    }

    private List<String> getXnodeInId(String id) {
        List<String> output = new ArrayList<>();
        Pattern xNodeBeginningOfString = Pattern.compile("^X.......");
        Pattern xNodeInString = Pattern.compile(" X.......");
        xNodeBeginningOfString.matcher(id).results().forEach(re -> output.add(re.group()));
        xNodeInString.matcher(id).results().forEach(re -> output.add(re.group().substring(1)));
        return output;
    }
}
