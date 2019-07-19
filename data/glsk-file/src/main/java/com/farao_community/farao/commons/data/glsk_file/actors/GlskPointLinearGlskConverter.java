/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.commons.data.glsk_file.actors;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.commons.data.glsk_file.*;
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
public class GlskPointLinearGlskConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlskPointLinearGlskConverter.class);

    /**
     * @param network IIDM network
     * @param glskPoint GLSK Point
     * @return farao-core LinearGlsk
     */
    public LinearGlsk convertGlskPointToLinearGlsk(Network network, GlskPoint glskPoint, String typeGlskFile) {

        Map<String, Float> linearGlskMap = new HashMap<>();
        String linearGlskId = glskPoint.getSubjectDomainmRID() + ":" + glskPoint.getPointInterval().toString();
        String linearGlskName = linearGlskId; //what name for linear glsk? generate internal uuid?

        Objects.requireNonNull(glskPoint.getGlskShiftKeys());

        if (glskPoint.getGlskShiftKeys().size() > 2) {
            throw new FaraoException("Multi (GSK+LSK) shift keys not supported yet...");
        }

        for (GlskShiftKey glskShiftKey : glskPoint.getGlskShiftKeys()) {
            if (glskShiftKey.getBusinessType().equals("B42") && glskShiftKey.getRegisteredResourceArrayList().isEmpty()) {
                LOGGER.debug("GLSK Type B42, empty registered resources list --> country (proportional) GLSK");
                convertCountryProportionalGlskPointToLinearGlskMap(network, glskShiftKey, linearGlskMap);
            } else if (glskShiftKey.getBusinessType().equals("B42") && !glskShiftKey.getRegisteredResourceArrayList().isEmpty()) {
                LOGGER.debug("GLSK Type B42, not empty registered resources list --> (explicit/manual) proportional GSK");
                convertExplicitProportionalGlskPointToLinearGlskMap(network, glskShiftKey, linearGlskMap, typeGlskFile);
            } else if (glskShiftKey.getBusinessType().equals("B43")) {
                LOGGER.debug("GLSK Type B43 --> participation factor proportional GSK");
                if (glskShiftKey.getRegisteredResourceArrayList().isEmpty()) {
                    throw new FaraoException("Empty Registered Resources List in B43 type shift key.");
                } else {
                    convertParticipationFactorGlskPointToLinearGlskMap(network, glskShiftKey, linearGlskMap, typeGlskFile);
                }
            } else {
                throw new FaraoException("convertGlskPointToLinearGlsk not supported");
            }
        }

        return new LinearGlsk(linearGlskId, linearGlskName, linearGlskMap);
    }


    /**
     * @param network iidm network
     * @param glskShiftKey country type shiftkey
     * @param linearGlskMap linearGlsk to be filled
     */
    private void convertCountryProportionalGlskPointToLinearGlskMap(Network network, GlskShiftKey glskShiftKey, Map<String, Float> linearGlskMap) {
        Country country = new EICode(glskShiftKey.getSubjectDomainmRID()).getCountry();
        //Generator A04 or Load A05
        if (glskShiftKey.getPsrType().equals("A04")) {
            //Generator A04
            //calculate sum P of country's generators
            double totalCountryP = network.getGeneratorStream().filter(generator -> generator.getTerminal().getVoltageLevel().getSubstation().getCountry().orElse(null).equals(country))
                    .mapToDouble(Generator::getTargetP).sum();
            //calculate factor of each generator
            network.getGeneratorStream().filter(generator -> generator.getTerminal().getVoltageLevel().getSubstation().getCountry().orElse(null).equals(country))
                    .forEach(generator -> linearGlskMap.put(generator.getId(), glskShiftKey.getQuantity().floatValue() * (float) generator.getTargetP() / (float) totalCountryP));
        } else if (glskShiftKey.getPsrType().equals("A05")) {
            //Load A05
            double totalCountryLoad = network.getLoadStream().filter(load -> load.getTerminal().getVoltageLevel().getSubstation().getCountry().orElse(null).equals(country))
                    .mapToDouble(Load::getP0).sum();
            network.getLoadStream().filter(load -> load.getTerminal().getVoltageLevel().getSubstation().getCountry().orElse(null).equals(country))
                    .forEach(load -> linearGlskMap.put(load.getId(), glskShiftKey.getQuantity().floatValue() * (float) load.getP0() / (float) totalCountryLoad));
        } else {
            //unknown PsrType
            throw new FaraoException("convertCountryProportionalGlskPointToLinearGlskMap PsrType not supported");
        }

    }

    /**
     * @param network iidm network
     * @param glskShiftKey explicit type shiftkey
     * @param linearGlskMap linearGlsk to be filled
     */
    private void convertExplicitProportionalGlskPointToLinearGlskMap(Network network, GlskShiftKey glskShiftKey, Map<String, Float> linearGlskMap, String typeGlskFile) {
        //Generator A04 or Load A05
        if (glskShiftKey.getPsrType().equals("A04")) {
            //Generator A04
            List<String> generatorsList =  glskShiftKey.getRegisteredResourceArrayList().stream()
                    .map(generatorResource -> getGeneratorId(generatorResource, typeGlskFile)).collect(Collectors.toList());
            double totalP = network.getGeneratorStream().filter(generator -> generatorsList.contains(generator.getId()))
                    .mapToDouble(Generator::getTargetP).sum();
            network.getGeneratorStream().filter(generator -> generatorsList.contains(generator.getId()))
                    .forEach(generator -> linearGlskMap.put(generator.getId(), glskShiftKey.getQuantity().floatValue() * (float) generator.getTargetP() / (float) totalP));
        } else if (glskShiftKey.getPsrType().equals("A05")) {
            //Load A05
            List<String> loadsList = glskShiftKey.getRegisteredResourceArrayList().stream()
                    .map(loadResource -> getLoadId(loadResource, typeGlskFile)).collect(Collectors.toList());
            double totalLoad = network.getLoadStream().filter(load -> loadsList.contains(load.getId()))
                    .mapToDouble(Load::getP0).sum();
            network.getLoadStream().filter(load -> loadsList.contains(load.getId()))
                    .forEach(load -> linearGlskMap.put(load.getId(), glskShiftKey.getQuantity().floatValue() * (float) load.getP0() / (float) totalLoad));
        } else {
            //unknown PsrType
            throw new FaraoException("convertExplicitProportionalGlskPointToLinearGlskMap PsrType not supported");
        }

    }

    /**
     * @param network iidm network
     * @param glskShiftKey parcitipation factor type shiftkey
     * @param linearGlskMap linearGlsk to be filled
     */
    private void convertParticipationFactorGlskPointToLinearGlskMap(Network network, GlskShiftKey glskShiftKey, Map<String, Float> linearGlskMap, String typeGlskFile) {
        //Generator A04 or Load A05
        if (glskShiftKey.getPsrType().equals("A04")) {
            //Generator A04
            List<GlskRegisteredResource> generatorsResourceList = glskShiftKey.getRegisteredResourceArrayList();
            double totalFactor = glskShiftKey.getRegisteredResourceArrayList().stream()
                    .filter(generatorResource -> network.getGenerator(getGeneratorId(generatorResource, typeGlskFile)) != null)
                    .mapToDouble(GlskRegisteredResource::getParticipationFactor).sum();

            generatorsResourceList.stream().filter(generatorResource -> network.getGenerator(getGeneratorId(generatorResource, typeGlskFile)) != null)
                    .forEach(generatorResource -> linearGlskMap.put(generatorResource.getmRID(), glskShiftKey.getQuantity().floatValue() * (float) generatorResource.getParticipationFactor() / (float) totalFactor));
        } else if (glskShiftKey.getPsrType().equals("A05")) {
            //Load A05
            List<GlskRegisteredResource> loadsResourceList = glskShiftKey.getRegisteredResourceArrayList();
            double totalFactor = glskShiftKey.getRegisteredResourceArrayList().stream()
                    .filter(loadResource -> network.getLoad(getLoadId(loadResource, typeGlskFile)) != null)
                    .mapToDouble(GlskRegisteredResource::getParticipationFactor).sum();

            loadsResourceList.stream().filter(loadResource -> network.getLoad(getLoadId(loadResource, typeGlskFile)) != null)
                    .forEach(loadResource -> linearGlskMap.put(loadResource.getmRID(), glskShiftKey.getQuantity().floatValue() * (float) loadResource.getParticipationFactor() / (float) totalFactor));
        } else {
            //unknown PsrType
            throw new FaraoException("convertParticipationFactorGlskPointToLinearGlskMap PsrType not supported");
        }
    }

    private String getLoadId(GlskRegisteredResource loadResource, String typeGlskFile) {
        if (typeGlskFile.equals("UCTE")) {
            return loadResource.getmRID() + "_load";
        } else {
            return loadResource.getmRID();
        }
    }

    private String getGeneratorId(GlskRegisteredResource generatorResource, String typeGlskFile) {
        if (typeGlskFile.equals("UCTE")) {
            return generatorResource.getmRID() + "_generator";
        } else {
            return generatorResource.getmRID();
        }
    }
}
