/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.data.glsk.api.util.converters;

import com.farao_community.farao.commons.CountryEICode;
import com.farao_community.farao.data.glsk.api.AbstractGlskPoint;
import com.farao_community.farao.data.glsk.api.AbstractGlskRegisteredResource;
import com.farao_community.farao.data.glsk.api.AbstractGlskShiftKey;
import com.farao_community.farao.data.glsk.api.GlskException;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Generator;
import com.powsybl.iidm.network.Load;
import com.powsybl.iidm.network.Network;
import com.powsybl.sensitivity.factors.variables.LinearGlsk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Convert a single GlskPoint to LinearGlsk
 * @author Pengbo Wang {@literal <pengbo.wang@rte-international.com>}
 */
public final class GlskPointLinearGlskConverter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlskPointLinearGlskConverter.class);

    private GlskPointLinearGlskConverter() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * @param network IIDM network
     * @param glskPoint GLSK Point
     * @return farao-core LinearGlsk
     */
    public static LinearGlsk convert(Network network, AbstractGlskPoint glskPoint, int order) {

        Map<String, Float> linearGlskMap = new HashMap<>();
        String linearGlskId = glskPoint.getSubjectDomainmRID() + ":" + glskPoint.getPointInterval().toString();

        /* Linear GLSK is used as sensitivityVariable in FlowBasedComputation
         * When it is added into sensivitityFactors, we should be able to find out LinearGlsk's country or NetWorkArea
         * For the moment, LinearGlsk's name is used to trace LinearGlsk's country or NetworkArea.
         * We could also added another attribute in LinearGlsk to mark this information,
         * but this change need to be in Powsybl-core
         */
        String linearGlskName = glskPoint.getSubjectDomainmRID(); // Name of LinearGlsk is country's EIC code; or NetworkArea's ID in the future

        Objects.requireNonNull(glskPoint.getGlskShiftKeys());

        if (glskPoint.getGlskShiftKeys().size() > 2) {
            throw new GlskException("Multi (GSK+LSK) shift keys not supported yet...");
        }

        for (AbstractGlskShiftKey glskShiftKey : glskPoint.getGlskShiftKeys()) {
            if (glskShiftKey.getBusinessType().equals("B42") && glskShiftKey.getRegisteredResourceArrayList().isEmpty()) {
                LOGGER.debug("GLSK Type B42, empty registered resources list --> country (proportional) GLSK");
                convertCountryProportional(network, glskShiftKey, linearGlskMap);
            } else if (glskShiftKey.getBusinessType().equals("B42") && !glskShiftKey.getRegisteredResourceArrayList().isEmpty()) {
                LOGGER.debug("GLSK Type B42, not empty registered resources list --> (explicit/manual) proportional GSK");
                convertExplicitProportional(network, glskShiftKey, linearGlskMap);
            } else if (glskShiftKey.getBusinessType().equals("B43")) {
                LOGGER.debug("GLSK Type B43 --> participation factor proportional GSK");
                if (glskShiftKey.getRegisteredResourceArrayList().isEmpty()) {
                    throw new GlskException("Empty Registered Resources List in B43 type shift key.");
                } else {
                    convertParticipationFactor(network, glskShiftKey, linearGlskMap);
                }
            } else {
                throw new GlskException("convert not supported");
            }
        }

        return new LinearGlsk(linearGlskId, linearGlskName, linearGlskMap);
    }

    /**
     * @param network iidm network
     * @param glskShiftKey country type shiftkey
     * @param linearGlskMap linearGlsk to be filled
     */
    private static void convertCountryProportional(Network network, AbstractGlskShiftKey glskShiftKey, Map<String, Float> linearGlskMap) {
        Country country = new CountryEICode(glskShiftKey.getSubjectDomainmRID()).getCountry();
        //Generator A04 or Load A05
        if (glskShiftKey.getPsrType().equals("A04")) {
            //Generator A04
            List<Generator> generators = network.getGeneratorStream()
                    .filter(generator -> country.equals(generator.getTerminal().getVoltageLevel().getSubstation().getNullableCountry()))
                    .filter(NetworkUtil::isCorrectGenerator)
                    .collect(Collectors.toList());
            //calculate sum P of country's generators
            double totalCountryP = generators.stream().mapToDouble(NetworkUtil::pseudoTargetP).sum();
            //calculate factor of each generator
            generators.forEach(generator -> linearGlskMap.put(generator.getId(), glskShiftKey.getQuantity().floatValue() * (float) NetworkUtil.pseudoTargetP(generator) / (float) totalCountryP));
        } else if (glskShiftKey.getPsrType().equals("A05")) {
            //Load A05
            List<Load> loads = network.getLoadStream()
                    .filter(load -> country.equals(load.getTerminal().getVoltageLevel().getSubstation().getNullableCountry()))
                    .filter(NetworkUtil::isCorrectLoad)
                    .collect(Collectors.toList());
            double totalCountryLoad = loads.stream().mapToDouble(NetworkUtil::pseudoP0).sum();
            loads.forEach(load -> linearGlskMap.put(load.getId(), glskShiftKey.getQuantity().floatValue() * (float) NetworkUtil.pseudoP0(load) / (float) totalCountryLoad));
        } else {
            //unknown PsrType
            throw new GlskException("convertCountryProportional PsrType not supported");
        }
    }

    /**
     * @param network iidm network
     * @param glskShiftKey explicit type shiftkey
     * @param linearGlskMap linearGlsk to be filled
     */
    private static void convertExplicitProportional(Network network, AbstractGlskShiftKey glskShiftKey, Map<String, Float> linearGlskMap) {
        //Generator A04 or Load A05
        if (glskShiftKey.getPsrType().equals("A04")) {
            //Generator A04
            List<Generator> generators = glskShiftKey.getRegisteredResourceArrayList().stream()
                    .map(AbstractGlskRegisteredResource::getGeneratorId)
                    .filter(generatorId -> network.getGenerator(generatorId) != null)
                    .map(network::getGenerator)
                    .filter(NetworkUtil::isCorrectGenerator)
                    .collect(Collectors.toList());
            double totalP = generators.stream().mapToDouble(NetworkUtil::pseudoTargetP).sum();
            generators.forEach(generator -> linearGlskMap.put(generator.getId(), glskShiftKey.getQuantity().floatValue() * (float) NetworkUtil.pseudoTargetP(generator) / (float) totalP));
        } else if (glskShiftKey.getPsrType().equals("A05")) {
            //Load A05
            List<Load> loads = glskShiftKey.getRegisteredResourceArrayList().stream()
                    .map(AbstractGlskRegisteredResource::getLoadId)
                    .filter(loadId -> network.getLoad(loadId) != null)
                    .map(network::getLoad)
                    .filter(NetworkUtil::isCorrectLoad)
                    .collect(Collectors.toList());
            double totalLoad = loads.stream().mapToDouble(NetworkUtil::pseudoP0).sum();
            loads.forEach(load -> linearGlskMap.put(load.getId(), glskShiftKey.getQuantity().floatValue() * (float) NetworkUtil.pseudoP0(load) / (float) totalLoad));
        } else {
            //unknown PsrType
            throw new GlskException("convertExplicitProportional PsrType not supported");
        }
    }

    /**
     * @param network iidm network
     * @param glskShiftKey parcitipation factor type shiftkey
     * @param linearGlskMap linearGlsk to be filled
     */
    private static void convertParticipationFactor(Network network, AbstractGlskShiftKey glskShiftKey, Map<String, Float> linearGlskMap) {
        //Generator A04 or Load A05
        if (glskShiftKey.getPsrType().equals("A04")) {
            //Generator A04
            List<AbstractGlskRegisteredResource> generatorResources = glskShiftKey.getRegisteredResourceArrayList().stream()
                    .filter(generatorResource -> network.getGenerator(generatorResource.getGeneratorId()) != null)
                    .filter(generatorResource -> NetworkUtil.isCorrectGenerator(network.getGenerator(generatorResource.getGeneratorId())))
                    .collect(Collectors.toList());
            double totalFactor = generatorResources.stream().mapToDouble(AbstractGlskRegisteredResource::getParticipationFactor).sum();

            generatorResources.forEach(generatorResource -> linearGlskMap.put(generatorResource.getGeneratorId(), glskShiftKey.getQuantity().floatValue() * (float) generatorResource.getParticipationFactor() / (float) totalFactor));
        } else if (glskShiftKey.getPsrType().equals("A05")) {
            //Load A05
            List<AbstractGlskRegisteredResource> loadResources = glskShiftKey.getRegisteredResourceArrayList().stream()
                    .filter(loadResource -> network.getLoad(loadResource.getLoadId()) != null)
                    .filter(loadResource -> NetworkUtil.isCorrectLoad(network.getLoad(loadResource.getLoadId())))
                    .collect(Collectors.toList());
            double totalFactor = loadResources.stream().mapToDouble(AbstractGlskRegisteredResource::getParticipationFactor).sum();

            loadResources.forEach(loadResource -> linearGlskMap.put(loadResource.getLoadId(), glskShiftKey.getQuantity().floatValue() * (float) loadResource.getParticipationFactor() / (float) totalFactor));
        } else {
            //unknown PsrType
            throw new GlskException("convertParticipationFactor PsrType not supported");
        }
    }
}
