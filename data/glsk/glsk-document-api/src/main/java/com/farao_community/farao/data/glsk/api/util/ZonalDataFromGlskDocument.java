/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.glsk.api.util;

import com.farao_community.farao.commons.ZonalDataImpl;
import com.farao_community.farao.data.glsk.api.AbstractGlskPoint;
import com.farao_community.farao.data.glsk.api.AbstractGlskShiftKey;
import com.farao_community.farao.data.glsk.api.GlskDocument;
import com.farao_community.farao.data.glsk.api.GlskException;
import com.farao_community.farao.data.glsk.api.util.converters.GlskPointToLinearDataConverter;
import com.powsybl.iidm.network.Network;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public class ZonalDataFromGlskDocument<I> extends ZonalDataImpl<I> {

    public ZonalDataFromGlskDocument(GlskDocument glskDocument, Network network, GlskPointToLinearDataConverter<I> converter, Instant instant) {
        super(new HashMap<>());
        for (String zone : glskDocument.getZones()) {
            List<AbstractGlskPoint> glskPointList = glskDocument.getGlskPoints(zone).stream()
                .filter(glskPoint -> glskPoint.getPointInterval().contains(instant))
                .collect(Collectors.toList());
            addLinearDataFromList(network, converter, glskPointList, zone);
        }
    }

    public ZonalDataFromGlskDocument(GlskDocument glskDocument, Network network, GlskPointToLinearDataConverter<I> converter) {
        super(new HashMap<>());
        for (String zone : glskDocument.getZones()) {
            List<AbstractGlskPoint> glskPointList = glskDocument.getGlskPoints(zone);
            if (!isHybridCseGlskPoint(glskPointList)) {
                //this is the normal case for single scalable glsk point, both in CIM format and in CSE format (except Swiss's ID CSE)
                addLinearDataFromList(network, converter, glskPointList, zone);
            } else {
                //Special case for Swiss's ID CSE hybrid gsk. There can be 2 shift keys in Swiss's ID CSE GLSK block:
                // - a PropGSKBlock with "order = 1" and "MaximumShift = ... ", representing German's generators
                // - a ReserveGSKBlock with "order = 2", representing Swiss's generators
                //
                // or, there can be only one shift key:
                // - a ReserveGSKBlock with "order = 1", representing Swiss's generators
                //
                // business requirement from Swissgrid is that when 2 blocks' hybrid gsk is used, German's generators are
                // applied first until "MaximumShift", then Swiss's generators are applied if necessary. Swissgrid assumes
                // that the German's generators will absorb all "MaximumShift" provided in their Gsk; there is no saturation
                // check in Convergence workflow.
                //
                // during import, we rename the zone name "10YCH-SWISSGRIDZ" with a suffix "@" + "order number":
                // - 10YCH-SWISSGRIDZ@1
                // - 10YCH-SWISSGRIDZ@2
                for (AbstractGlskShiftKey abstractGlskShiftKey : glskPointList.get(0).getGlskShiftKeys()) {
                    I linearData = converter.convert(network, glskPointList.get(0), abstractGlskShiftKey.getOrderInHybridCseGlsk());
                    dataPerZone.put(zone + "@" + abstractGlskShiftKey.getOrderInHybridCseGlsk(), linearData);
                }
            }
        }
    }

    private void addLinearDataFromList(Network network, GlskPointToLinearDataConverter<I> converter, List<AbstractGlskPoint> glskPointList, String country) {
        if (glskPointList.size() > 1) {
            throw new GlskException("Cannot instantiate simple linear data because several glsk point match given instant");
        } else if (!glskPointList.isEmpty()) {
            I linearData = converter.convert(network, glskPointList.get(0), glskPointList.get(0).getGlskShiftKeys().get(0).getOrderInHybridCseGlsk());
            dataPerZone.put(country, linearData);
        }
    }

    private boolean isHybridCseGlskPoint(List<AbstractGlskPoint> glskPointList) {
        // if 2 shift keys have different orders, this is a hybrid glsk for Swiss's ID CSE GSK.
        // Note: in CIM glsk format, there can be 2 shift keys, a GSK and a LSK, defined for a same zone,
        // these 2 shift keys (GSK + LSK) should be merged into one single Scalable
        return glskPointList.get(0).getGlskShiftKeys().size() == 2 &&
                glskPointList.get(0).getGlskShiftKeys().get(0).getOrderInHybridCseGlsk() != glskPointList.get(0).getGlskShiftKeys().get(1).getOrderInHybridCseGlsk();
    }
}
