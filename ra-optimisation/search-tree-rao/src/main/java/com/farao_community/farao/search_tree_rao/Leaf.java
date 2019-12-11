/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.search_tree_rao;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.NetworkAction;
import com.farao_community.farao.linear_range_action_rao.LinearRangeActionRaoResult;
import com.farao_community.farao.ra_optimisation.RaoComputationResult;
import com.farao_community.farao.rao_api.Rao;
import com.farao_community.farao.rao_api.RaoParameters;
import com.powsybl.computation.ComputationManager;
import com.powsybl.iidm.network.Network;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A "leaf" is a junction of the search tree
 * Each leaf contains a Network Action, which should be tested in combination with
 * it's parent Leaves' Network Actions
 *
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-international.com>}
 */
class Leaf {

    /**
     * Parent Leaf or null for initial Leaf
     */
    private final Leaf parentLeaf;

    /**
     * Network Action which will be tested, can be null
     */
    private final NetworkAction networkAction;

    /**
     * Name of the network variant associated with this Leaf
     */
    private String networkVariant;

    /**
     * Impact of the network action
     */
    private LinearRangeActionRaoResult linearRaoResult;

    /**
     * Status of the leaf's Network Action evaluation
     */
    Status status;

    enum Status {
        CREATED,
        EVALUATION_RUNNING,
        EVALUATION_SUCCESS,
        EVALUATION_ERROR
    }

    /**
     * Initial Leaf constructor
     */
    Leaf() {
        this.parentLeaf = null;
        this.networkAction = null;
        this.status = Status.CREATED;
    }

    /**
     * Leaf constructor
     */
    private Leaf(Leaf parentLeaf, NetworkAction networkAction) {
        this.parentLeaf = parentLeaf;
        this.networkAction = networkAction;
        this.status = Status.CREATED;
    }

    /**
     * Parent Leaf getter
     */
    Leaf getParent() {
        return parentLeaf;
    }

    NetworkAction getNetworkAction() {
        return networkAction;
    }

    /**
     * Action impact getter
     */
    LinearRangeActionRaoResult getLinearRaoResult() {
        return linearRaoResult;
    }

    /**
     * Leaf status getter
     */
    Status getStatus() {
        return status;
    }

    /**
     * Leaf Variant getter
     */
    String getNetworkVariant() {
        return networkVariant;
    }

    /**
     * Is this Leaf the initial one of the tree
     */
    boolean isRoot() {
        return parentLeaf == null;
    }

    /**
     * Extend the tree from the current Leaf with N new children Leaves
     * for the N Network Actions given in argument
     */
    List<Leaf> bloom(List<NetworkAction> availableNetworkActions) {
        //TODO: remove network actions of current leaf and its parents
        return availableNetworkActions.stream().map(na -> new Leaf(this, na)).collect(Collectors.toList());
    }

    /**
     * Evaluate the impact of Network Actions (from the current Leaf and
     * its parents)
     */
    void evaluate(Network network, Crac crac, ComputationManager computationManager, RaoParameters parameters) {
        if (isRoot()) {
            throw new FaraoException("When evaluating the root leaf, a network variant must be specified.");
        }
        evaluate(network, crac, getParent().getNetworkVariant(), computationManager, parameters);
    }

    /**
     * Evaluate the impact of Network Actions (from the current Leaf and
     * its parents)
     */
    void evaluate(Network network, Crac crac, String referenceNetworkVariant, ComputationManager computationManager, RaoParameters parameters) {

        this.status = Status.EVALUATION_RUNNING;

        // get network variant and apply network action
        this.networkVariant = createVariant(network, referenceNetworkVariant);
        network.getVariantManager().setWorkingVariant(this.networkVariant);

        RaoComputationResult results = Rao.find("Linear Range Action Rao").run(network, crac, networkVariant, computationManager, parameters);
        linearRaoResult = results.getExtension(LinearRangeActionRaoResult.class);
        //todo : run RangeActionRao and update RaoComputationResult
    }

    private String getUniqueVariantId(Network network) {
        String uniqueId;
        do {
            uniqueId = UUID.randomUUID().toString();
        } while (network.getVariantManager().getVariantIds().contains(uniqueId));
        return uniqueId;
    }

    private String createVariant(Network network, String referenceNetworkVariant) {
        String uniqueId = getUniqueVariantId(network);

        if (isRoot()) {
            network.getVariantManager().cloneVariant(referenceNetworkVariant, this.networkVariant);
        } else {
            network.getVariantManager().cloneVariant(referenceNetworkVariant, this.networkVariant);
        }
        return uniqueId;
    }

}
