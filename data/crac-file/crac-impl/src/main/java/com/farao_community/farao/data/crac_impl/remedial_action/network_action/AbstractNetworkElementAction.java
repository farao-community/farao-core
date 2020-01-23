/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_impl.remedial_action.network_action;

import com.farao_community.farao.data.crac_api.NetworkAction;
import com.farao_community.farao.data.crac_api.NetworkElement;
import com.farao_community.farao.data.crac_api.UsageRule;
import com.farao_community.farao.data.crac_impl.AbstractRemedialAction;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Generic object to define any simple action on a network element (setpoint, open/close, etc.).
 *
 * @author Viktor Terrier {@literal <viktor.terrier at rte-france.com>}
 */
public abstract class AbstractNetworkElementAction extends AbstractRemedialAction implements NetworkAction {
    protected NetworkElement networkElement;

    @JsonCreator
    public AbstractNetworkElementAction(@JsonProperty("id") String id,
                                        @JsonProperty("name") String name,
                                        @JsonProperty("operator") String operator,
                                        @JsonProperty("usageRules") List<UsageRule> usageRules,
                                        @JsonProperty("networkElement") NetworkElement networkElement) {
        super(id, name, operator, usageRules);
        this.networkElement = networkElement;
    }

    public AbstractNetworkElementAction() {
        super();
    }

    public NetworkElement getNetworkElement() {
        return networkElement;
    }

    public void setNetworkElement(NetworkElement networkElement) {
        this.networkElement = networkElement;
    }

    @Override
    public Set<NetworkElement> getNetworkElements() {
        return Collections.singleton(networkElement);
    }
}
