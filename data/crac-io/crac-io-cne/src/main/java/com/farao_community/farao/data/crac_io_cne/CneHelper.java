/*
 *  Copyright (c) 2020, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_io_cne;

import com.farao_community.farao.data.crac_api.Contingency;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.Instant;
import com.farao_community.farao.data.crac_impl.ComplexContingency;
import com.farao_community.farao.data.crac_result_extensions.CracResultExtension;
import com.powsybl.iidm.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.farao_community.farao.data.crac_io_cne.CneConstants.*;

/**
 * @author Viktor Terrier {@literal <viktor.terrier at rte-france.com>}
 */
public class CneHelper {

    private Crac crac;
    private Network network;
    private List<Instant> instants;
    private String preOptimVariantId;
    private String postOptimVariantId;
    private Map<Contingency, ConstraintSeries> constraintSeriesMap;
    private Contingency basecase;

    public CneHelper(Crac crac, Network network) {

        this.crac = crac;
        this.network = network;
        instants = new ArrayList<>();
        preOptimVariantId = "";
        postOptimVariantId = "";
        constraintSeriesMap = new HashMap<>();
        basecase = new ComplexContingency("BASECASE");
    }

    public Contingency getBasecase() {
        return basecase;
    }

    public Network getNetwork() {
        return network;
    }

    public Map<Contingency, ConstraintSeries> getConstraintSeriesMap() {
        return constraintSeriesMap;
    }

    public void addToConstraintSeriesMap(Contingency contingency, ConstraintSeries constraintSeries) {
        constraintSeriesMap.put(contingency, constraintSeries);
    }

    public List<Instant> getInstants() {
        return instants;
    }

    public String getPreOptimVariantId() {
        return preOptimVariantId;
    }

    public String getPostOptimVariantId() {
        return postOptimVariantId;
    }

    public Crac getCrac() {
        return crac;
    }

    public void initializeAttributes(Crac crac, CracResultExtension cracExtension, List<String> variants) {
        // sort the instants in order to determine which one is preventive, after outage, after auto RA and after CRA
        instants = crac.getInstants().stream().sorted(Comparator.comparing(Instant::getSeconds)).collect(Collectors.toList());

        // TODO: store the information on preOptim/postOptim Variant in the ResultVariantManager
        preOptimVariantId = variants.get(0);
        postOptimVariantId = variants.get(0);

        double minCost = cracExtension.getVariant(variants.get(0)).getCost();
        double maxCost = cracExtension.getVariant(variants.get(0)).getCost();
        for (String variant : variants) {
            if (cracExtension.getVariant(variant).getCost() < minCost) {
                minCost = cracExtension.getVariant(variant).getCost();
                postOptimVariantId = variant;
            } else if (cracExtension.getVariant(variant).getCost() > maxCost) {
                maxCost = cracExtension.getVariant(variant).getCost();
                preOptimVariantId = variant;
            }
        }
    }

    public void checkSynchronize() {
        if (!crac.isSynchronized()) {
            crac.synchronize(network);
        }
    }
}
