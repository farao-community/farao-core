/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.data.crac_api.json.serializers;

import com.farao_community.farao.data.crac_api.NetworkElement;
import com.farao_community.farao.data.crac_api.RemedialAction;
import com.farao_community.farao.data.crac_api.usage_rule.UsageRule;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

import java.io.IOException;

import static com.farao_community.farao.data.crac_api.json.JsonSerializationNames.*;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class RemedialActionSerializer<I extends RemedialAction<?>> extends JsonSerializer<I> {
    @Override
    public void serialize(I remedialAction, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        serializeCommon(remedialAction, jsonGenerator);
        jsonGenerator.writeFieldName(NETWORK_ELEMENTS);
        jsonGenerator.writeStartArray();
        for (NetworkElement networkElement: remedialAction.getNetworkElements()) {
            jsonGenerator.writeString(networkElement.getId());
        }
        jsonGenerator.writeEndArray();
    }

    @Override
    public void serializeWithType(I remedialAction, JsonGenerator jsonGenerator, SerializerProvider serializerProvider, TypeSerializer typeSerializer) throws IOException {
        WritableTypeId writableTypeId = typeSerializer.typeId(remedialAction, JsonToken.START_OBJECT);
        typeSerializer.writeTypePrefix(jsonGenerator, writableTypeId);
        serialize(remedialAction, jsonGenerator, serializerProvider);
        typeSerializer.writeTypeSuffix(jsonGenerator, writableTypeId);
    }

    protected void serializeCommon(I remedialAction, JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeStringField(ID, remedialAction.getId());
        jsonGenerator.writeStringField(NAME, remedialAction.getName());
        jsonGenerator.writeStringField(OPERATOR, remedialAction.getOperator());
        jsonGenerator.writeFieldName(USAGE_RULES);
        jsonGenerator.writeStartArray();
        for (UsageRule usageRule: remedialAction.getUsageRules()) {
            jsonGenerator.writeObject(usageRule);
        }
        jsonGenerator.writeEndArray();
    }
}
