/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_io_json.deserializers;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.data.crac_api.ActionType;
import com.farao_community.farao.data.crac_api.NetworkAction;
import com.farao_community.farao.data.crac_api.NetworkElement;
import com.farao_community.farao.data.crac_api.UsageRule;
import com.farao_community.farao.data.crac_impl.SimpleCrac;
import com.farao_community.farao.data.crac_impl.remedial_action.network_action.AbstractElementaryNetworkAction;
import com.farao_community.farao.data.crac_impl.remedial_action.network_action.ComplexNetworkAction;
import com.farao_community.farao.data.crac_impl.remedial_action.network_action.PstSetpoint;
import com.farao_community.farao.data.crac_impl.remedial_action.network_action.Topology;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.farao_community.farao.data.crac_io_json.deserializers.DeserializerNames.*;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
final class NetworkActionDeserializer {

    private NetworkActionDeserializer() { }

    static Set<NetworkAction> deserialize(JsonParser jsonParser, SimpleCrac simpleCrac) throws IOException {
        // cannot be done in a standard NetworkAction deserializer as it requires the simpleCrac to compare
        // the networkElement ids of the NetworkAction with the NetworkElements of the Crac

        Set<NetworkAction> networkActions = new HashSet<>();

        while (jsonParser.nextToken() != JsonToken.END_ARRAY) {

            NetworkAction networkAction;

            // first json Token should be the type of the range action
            jsonParser.nextToken();
            if (!jsonParser.getCurrentName().equals(TYPE)) {
                throw new FaraoException("Type of range action is missing");
            }

            // use the deserializer suited to range action type
            String type = jsonParser.nextTextValue();
            switch (type) {
                case TOPOLOGY_TYPE:
                    networkAction = deserializeTopology(jsonParser, simpleCrac);
                    break;

                case PST_SETPOINT_TYPE:
                    networkAction = deserializePstSetPoint(jsonParser, simpleCrac);
                    break;

                case COMPLEX_NETWORK_ACTION_TYPE:
                    networkAction = deserializeComplexNetworkAction(jsonParser, simpleCrac);
                    break;

                default:
                    throw new FaraoException(String.format("Type of range action [%s] not handled by SimpleCrac deserializer.", type));

            }

            networkActions.add(networkAction);
        }

        return networkActions;
    }

    private static Topology deserializeTopology(JsonParser jsonParser, SimpleCrac simpleCrac) throws IOException {
        // cannot be done in a standard Topology deserializer as it requires the simpleCrac to compare
        // the networkElement ids of the Topology with the NetworkElements of the Crac

        String id = null;
        String name = null;
        String operator = null;
        List<UsageRule> usageRules = new ArrayList<>();
        String networkElementId = null;
        ActionType actionType = null;

        while (!jsonParser.nextToken().isStructEnd()) {
            {
                switch (jsonParser.getCurrentName()) {

                    case ID:
                        id = jsonParser.nextTextValue();
                        break;

                    case NAME:
                        name = jsonParser.nextTextValue();
                        break;

                    case OPERATOR:
                        operator = jsonParser.nextTextValue();
                        break;

                    case USAGE_RULES:
                        jsonParser.nextToken();
                        usageRules = UsageRuleDeserializer.deserialize(jsonParser, simpleCrac);
                        break;

                    case NETWORK_ELEMENT:
                        networkElementId = jsonParser.nextTextValue();
                        break;

                    case ACTION_TYPE:
                        jsonParser.nextToken();
                        actionType = jsonParser.readValueAs(ActionType.class);
                        break;

                    default:
                        throw new FaraoException("Unexpected field: " + jsonParser.getCurrentName());

                }
            }
        }

        NetworkElement ne = simpleCrac.getNetworkElement(networkElementId);
        if (ne == null) {
            throw new FaraoException(String.format("The network element [%s] mentioned in the topology is not defined", networkElementId));
        }

        return new Topology(id, name, operator, usageRules, ne, actionType);
    }

    private static PstSetpoint deserializePstSetPoint(JsonParser jsonParser, SimpleCrac simpleCrac) throws IOException {
        // cannot be done in a standard PstSetPoint deserializer as it requires the simpleCrac to compare
        // the networkElement ids of the PstSetPoint with the NetworkElements of the Crac

        String id = null;
        String name = null;
        String operator = null;
        List<UsageRule> usageRules = new ArrayList<>();
        String networkElementId = null;
        double setPoint = 0;

        while (!jsonParser.nextToken().isStructEnd()) {
            {
                switch (jsonParser.getCurrentName()) {

                    case ID:
                        id = jsonParser.nextTextValue();
                        break;

                    case NAME:
                        name = jsonParser.nextTextValue();
                        break;

                    case OPERATOR:
                        operator = jsonParser.nextTextValue();
                        break;

                    case USAGE_RULES:
                        jsonParser.nextToken();
                        usageRules = UsageRuleDeserializer.deserialize(jsonParser, simpleCrac);
                        break;

                    case NETWORK_ELEMENT:
                        networkElementId = jsonParser.nextTextValue();
                        break;

                    case SETPOINT:
                        jsonParser.nextToken();
                        setPoint = jsonParser.getDoubleValue();
                        break;

                    default:
                        throw new FaraoException("Unexpected field: " + jsonParser.getCurrentName());
                }
            }
        }

        NetworkElement ne = simpleCrac.getNetworkElement(networkElementId);
        if (ne == null) {
            throw new FaraoException(String.format("The network element [%s] mentioned in the topology is not defined", networkElementId));
        }

        return new PstSetpoint(id, name, operator, usageRules, ne, setPoint);
    }

    private static ComplexNetworkAction deserializeComplexNetworkAction(JsonParser jsonParser, SimpleCrac simpleCrac) throws IOException {
        // cannot be done in a standard ComplexNetworkAction deserializer as it requires the simpleCrac to compare
        // the networkElement ids of the ComplexNetworkAction with the NetworkElements of the SimpleCrac

        String id = null;
        String name = null;
        String operator = null;
        List<UsageRule> usageRules = new ArrayList<>();
        Set<AbstractElementaryNetworkAction> elementaryNetworkActions = new HashSet<>();

        while (!jsonParser.nextToken().isStructEnd()) {
            {
                switch (jsonParser.getCurrentName()) {

                    case ID:
                        id = jsonParser.nextTextValue();
                        break;

                    case NAME:
                        name = jsonParser.nextTextValue();
                        break;

                    case OPERATOR:
                        operator = jsonParser.nextTextValue();
                        break;

                    case USAGE_RULES:
                        jsonParser.nextToken();
                        usageRules = UsageRuleDeserializer.deserialize(jsonParser, simpleCrac);
                        break;

                    case ELEMENTARY_NETWORK_ACTIONS:
                        jsonParser.nextToken();
                        Set<NetworkAction> networkActions = NetworkActionDeserializer.deserialize(jsonParser, simpleCrac);
                        networkActions.forEach(na -> {
                            if (!(na instanceof AbstractElementaryNetworkAction)) {
                                throw new FaraoException("A complex network action can only contain elementary network actions");
                            }
                            elementaryNetworkActions.add((AbstractElementaryNetworkAction) na);
                        });
                        break;

                    default:
                        throw new FaraoException("Unexpected field: " + jsonParser.getCurrentName());
                }
            }
        }

        return new ComplexNetworkAction(id, name, operator, usageRules, elementaryNetworkActions);
    }
}
