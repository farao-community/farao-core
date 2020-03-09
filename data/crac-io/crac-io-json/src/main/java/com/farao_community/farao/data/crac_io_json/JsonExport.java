/*
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_io_json;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.NetworkAction;
import com.farao_community.farao.data.crac_api.RangeAction;
import com.farao_community.farao.data.crac_impl.json.serializers.network_action.NetworkActionSerializer;
import com.farao_community.farao.data.crac_impl.json.serializers.range_action.RangeActionSerializer;
import com.farao_community.farao.data.crac_impl.json.serializers.usage_rule.FreeToUseSerializer;
import com.farao_community.farao.data.crac_impl.json.serializers.usage_rule.OnConstraintSerializer;
import com.farao_community.farao.data.crac_impl.json.serializers.usage_rule.OnContingencySerializer;
import com.farao_community.farao.data.crac_impl.usage_rule.FreeToUse;
import com.farao_community.farao.data.crac_impl.usage_rule.OnConstraint;
import com.farao_community.farao.data.crac_impl.usage_rule.OnContingency;
import com.farao_community.farao.data.crac_io_api.CracExporter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.auto.service.AutoService;

import java.io.*;

import static com.powsybl.commons.json.JsonUtil.createObjectMapper;

/**
 * CRAC object export in json format
 *
 * @author Viktor Terrier {@literal <viktor.terrier at rte-france.com>}
 */
@AutoService(CracExporter.class)
public class JsonExport implements CracExporter {

    private static final String JSON_FORMAT = "Json";

    @Override
    public String getFormat() {
        return JSON_FORMAT;
    }

    @Override
    public void exportCrac(Crac crac, OutputStream outputStream) {

        try {
            ObjectMapper objectMapper = createObjectMapper();
            objectMapper.registerModule(new Jdk8Module());
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            SimpleModule module = new SimpleModule();
            module.addSerializer(FreeToUse.class, new FreeToUseSerializer());
            module.addSerializer(OnConstraint.class, new OnConstraintSerializer());
            module.addSerializer(OnContingency.class, new OnContingencySerializer());
            module.addSerializer(RangeAction.class, new RangeActionSerializer());
            module.addSerializer(NetworkAction.class, new NetworkActionSerializer<>());
            objectMapper.registerModule(module);
            ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();
            writer.writeValue(outputStream, crac);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
