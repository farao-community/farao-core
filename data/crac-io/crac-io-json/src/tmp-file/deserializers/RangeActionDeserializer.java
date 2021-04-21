/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_io_json.deserializers;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_api.range_action.Range;
import com.farao_community.farao.data.crac_api.range_action.RangeAction;
import com.farao_community.farao.data.crac_api.range_action.TapRange;
import com.farao_community.farao.data.crac_api.usage_rule.UsageRule;
import com.farao_community.farao.data.crac_impl.CracImpl;
import com.farao_community.farao.data.crac_impl.TapRangeImpl;
import com.farao_community.farao.data.crac_impl.PstRangeActionImpl;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.powsybl.commons.extensions.Extension;
import com.powsybl.commons.json.JsonUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.farao_community.farao.data.crac_io_json.JsonSerializationNames.*;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
final class RangeActionDeserializer {

    private RangeActionDeserializer() { }

    static void deserialize(JsonParser jsonParser, CracImpl simpleCrac, DeserializationContext deserializationContext) throws IOException {
        // cannot be done in a standard RangeAction deserializer as it requires the simpleCrac to compare
        // the networkElement ids of the RangeAction with the NetworkElements of the Crac

        while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
            // first json Token should be the type of the range action
            jsonParser.nextToken();
            if (!jsonParser.getCurrentName().equals(TYPE)) {
                throw new FaraoException("Type of range action is missing");
            }

            // use the deserializer suited to range action type
            String type = jsonParser.nextTextValue();
            RangeAction rangeAction = deserializeRangeAction(type, jsonParser, simpleCrac, deserializationContext);

            simpleCrac.addRangeAction(rangeAction);
        }
    }

    private static RangeAction deserializeRangeAction(String type, JsonParser jsonParser, CracImpl simpleCrac, DeserializationContext deserializationContext) throws IOException {
        String id = null;
        String name = null;
        String operator = null;
        List<UsageRule> usageRules = new ArrayList<>();
        List<Range> ranges = new ArrayList<>();
        Set<String> networkElementsIds = new HashSet<>();
        List <Extension < RangeAction > > extensions = new ArrayList<>();
        String groupId = null;

        while (!jsonParser.nextToken().isStructEnd()) {

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

                case RANGES:
                    jsonParser.nextToken();
                    if (type.equals(PST_RANGE_ACTION_IMPL_TYPE)) {
                        ranges = jsonParser.readValueAs(new TypeReference<List<TapRangeImpl>>() {
                        });
                    } else {
                        ranges = jsonParser.readValueAs(new TypeReference<List<Range>>() {
                        });
                    }
                    break;

                case NETWORK_ELEMENTS:
                    jsonParser.nextToken();
                    networkElementsIds = jsonParser.readValueAs(new TypeReference<HashSet<String>>() {
                    });
                    break;

                case GROUP_ID:
                    groupId = jsonParser.nextTextValue();
                    break;

                case EXTENSIONS:
                    jsonParser.nextToken();
                    extensions = JsonUtil.readExtensions(jsonParser, deserializationContext, ExtensionsHandler.getExtensionsSerializers());
                    break;

                default:
                    throw new FaraoException(UNEXPECTED_FIELD + jsonParser.getCurrentName());
            }

        }

        Set<NetworkElement> networkElements = DeserializerUtils.getNetworkElementsFromIds(networkElementsIds, simpleCrac);
        RangeAction rangeAction;
        switch (type) {
            case PST_RANGE_ACTION_IMPL_TYPE:
                List<TapRange> pstRanges = new ArrayList<>();
                for (Range range : ranges) {
                    if (range instanceof TapRange) {
                        pstRanges.add((TapRange) range);
                    } else {
                        throw new FaraoException(String.format("Type of range action [%s] should have ranges of type TapRange.", type));
                    }
                }
                rangeAction = new PstRangeActionImpl(id, name, operator, usageRules, pstRanges, networkElements.iterator().next(), groupId);
                break;
            default:
                throw new FaraoException(String.format("Type of range action [%s] not handled by CracImpl deserializer.", type));
        }
        ExtensionsHandler.getExtensionsSerializers().addExtensions(rangeAction, extensions);
        return rangeAction;
    }
}