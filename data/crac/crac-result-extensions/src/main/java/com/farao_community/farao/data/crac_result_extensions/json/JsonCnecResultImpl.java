/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.data.crac_result_extensions.json;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.data.crac_impl.json.ExtensionsHandler;
import com.farao_community.farao.data.crac_result_extensions.CnecResultImpl;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.auto.service.AutoService;

import java.io.IOException;

/**
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
@AutoService(ExtensionsHandler.CnecExtensionSerializer.class)
public class JsonCnecResultImpl implements ExtensionsHandler.CnecExtensionSerializer<CnecResultImpl> {

    @Override
    public void serialize(CnecResultImpl cnecResults, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeNumberField("flowInMW", cnecResults.getFlowInMW());
        jsonGenerator.writeNumberField("flowInA", cnecResults.getFlowInA());
        jsonGenerator.writeEndObject();

    }

    @Override
    public CnecResultImpl deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {

        double flowInMW = Double.NaN;
        double flowInA = Double.NaN;

        while (!jsonParser.nextToken().isStructEnd()) {
            switch (jsonParser.getCurrentName()) {
                case "flow-in-MW":
                    jsonParser.nextToken();
                    flowInMW = jsonParser.getDoubleValue();
                    break;

                case "flow-in-A":
                    jsonParser.nextToken();
                    flowInA = jsonParser.getDoubleValue();
                    break;

                default:
                    throw new FaraoException("Unexpected field: " + jsonParser.getCurrentName());
            }
        }

        return new CnecResultImpl(flowInMW, flowInA);
    }

    @Override
    public String getExtensionName() {
        return "CnecResult";
    }

    @Override
    public String getCategoryName() {
        return "cnec";
    }

    @Override
    public Class<? super CnecResultImpl> getExtensionClass() {
        return CnecResultImpl.class;
    }

}
