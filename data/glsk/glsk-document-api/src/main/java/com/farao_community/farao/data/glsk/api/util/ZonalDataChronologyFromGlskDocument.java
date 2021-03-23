/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.glsk.api.util;

import com.farao_community.farao.commons.ZonalData;
import com.farao_community.farao.commons.ZonalDataChronology;
import com.farao_community.farao.commons.ZonalDataImpl;
import com.farao_community.farao.commons.chronology.Chronology;
import com.farao_community.farao.commons.chronology.ChronologyImpl;
import com.farao_community.farao.data.glsk.api.AbstractGlskPoint;
import com.farao_community.farao.data.glsk.api.GlskDocument;
import com.farao_community.farao.data.glsk.api.GlskException;
import com.farao_community.farao.data.glsk.api.util.converters.GlskPointToLinearDataConverter;
import com.powsybl.iidm.network.Network;
import org.threeten.extra.Interval;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class ZonalDataChronologyFromGlskDocument<I> implements ZonalDataChronology<I> {

    private static final String UNMODIFIABLE = "ZonalDataChronologyFromGlskDocument objects are unmodifiable.";

    private final Map<String, Chronology<I>> dataChronologyPerZone;

    public ZonalDataChronologyFromGlskDocument(GlskDocument glskDocument, Network network, GlskPointToLinearDataConverter<I> converter) {
        dataChronologyPerZone = new HashMap<>();

        for (String zone : glskDocument.getZones()) {
            Chronology<I> dataChronology = ChronologyImpl.create();

            //mapping with DataChronology
            List<AbstractGlskPoint> glskPointList = glskDocument.getGlskPoints(zone);
            for (AbstractGlskPoint point : glskPointList) {
                I linearData = converter.convert(network, point, point.getGlskShiftKeys().get(0).getOrderInHybridCseGlsk());
                dataChronology.storeDataOnInterval(linearData, point.getPointInterval());
            }
            dataChronologyPerZone.put(zone, dataChronology);
        }
    }

    @Override
    public ZonalData<I> selectInstant(Instant instant) {
        return selectInstant(instant, ReplacementStrategy.NO_REPLACEMENT);
    }

    @Override
    public ZonalData<I> selectInstant(Instant instant, ReplacementStrategy replacementStrategy) {
        Objects.requireNonNull(instant, "Unable to return data if no instant are selected.");
        return new ZonalDataImpl<>(dataChronologyPerZone.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().selectInstant(instant, replacementStrategy)
            )));
    }

    @Override
    public void storeDataAtInstant(ZonalData<I> data, Instant instant) {
        throw new GlskException(UNMODIFIABLE);
    }

    @Override
    public void storeDataAtInstant(ZonalData<I> data, Instant instant, Duration duration) {
        throw new GlskException(UNMODIFIABLE);
    }

    @Override
    public void storeDataAtInstant(ZonalData<I> data, Instant instant, Period period) {
        throw new GlskException(UNMODIFIABLE);
    }

    @Override
    public void storeDataOnInterval(ZonalData<I> data, Interval interval) {
        throw new GlskException(UNMODIFIABLE);
    }

    @Override
    public void storeDataBetweenInstants(ZonalData<I> data, Instant from, Instant to) {
        throw new GlskException(UNMODIFIABLE);
    }
}
