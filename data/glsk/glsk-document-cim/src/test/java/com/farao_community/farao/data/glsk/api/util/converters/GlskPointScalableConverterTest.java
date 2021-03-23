/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.data.glsk.api.util.converters;

import com.farao_community.farao.data.glsk.api.AbstractGlskPoint;
import com.farao_community.farao.data.glsk.cim.CimGlskDocument;
import com.google.common.math.DoubleMath;
import com.powsybl.action.util.Scalable;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;

/**
 * @author Pengbo Wang {@literal <pengbo.wang@rte-international.com>}
 */
public class GlskPointScalableConverterTest {
    private static final String GLSKB45TEST = "/GlskB45test.xml";
    private static final String GLSKB42COUNTRYIIDM = "/GlskB42CountryIIDM.xml";
    private static final String GLSKB42COUNTRYGSKLSK = "/GlskB42CountryGskLsk.xml";
    private static final String GLSKB42EXPLICITIIDM = "/GlskB42ExplicitIIDM.xml";
    private static final String GLSKB42EXPLICITGSKLSK = "/GlskB42ExplicitGskLsk.xml";
    private static final String GLSKB43 = "/GlskB43ParticipationFactorIIDM.xml";
    private static final String GLSKB43GSKLSK = "/GlskB43ParticipationFactorGskLsk.xml";

    private Network testNetwork;
    private AbstractGlskPoint glskPointCountry;
    private AbstractGlskPoint glskPointCountryGskLsk;
    private AbstractGlskPoint glskPointExplicit;
    private AbstractGlskPoint glskPointExplicitGskLsk;
    private AbstractGlskPoint glskPointParticipationFactor;
    private AbstractGlskPoint glskPointParticipationFactorGskLsk;
    private AbstractGlskPoint glskMeritOrder;

    private InputStream getResourceAsStream(String resource) {
        return getClass().getResourceAsStream(resource);
    }

    @Before
    public void setUp() {
        testNetwork = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));

        glskPointCountry = CimGlskDocument.importGlsk(getResourceAsStream(GLSKB42COUNTRYIIDM)).getGlskPoints().get(0);
        glskPointCountryGskLsk = CimGlskDocument.importGlsk(getResourceAsStream(GLSKB42COUNTRYGSKLSK)).getGlskPoints().get(0);
        glskPointExplicit = CimGlskDocument.importGlsk(getResourceAsStream(GLSKB42EXPLICITIIDM)).getGlskPoints().get(0);
        glskPointExplicitGskLsk = CimGlskDocument.importGlsk(getResourceAsStream(GLSKB42EXPLICITGSKLSK)).getGlskPoints().get(0);
        glskPointParticipationFactor = CimGlskDocument.importGlsk(getResourceAsStream(GLSKB43)).getGlskPoints().get(0);
        glskPointParticipationFactorGskLsk = CimGlskDocument.importGlsk(getResourceAsStream(GLSKB43GSKLSK)).getGlskPoints().get(0);
        glskMeritOrder = CimGlskDocument.importGlsk(getResourceAsStream(GLSKB45TEST)).getGlskPoints().get(0);

    }

    /**
     *  test for Scalable
     */
    @Test
    public void testConvertGlskPointToScalableB45MeritOrder() {
        Scalable scalable = GlskPointScalableConverter.convert(testNetwork, glskMeritOrder, 0);
        double done = scalable.scale(testNetwork, 100.0);
        Assert.assertTrue(DoubleMath.fuzzyEquals(6, done, 0.0001));
    }

    @Test
    public void testConvertGlskPointToScalableB42Country() {
        Scalable scalable = GlskPointScalableConverter.convert(testNetwork, glskPointCountry, 0);
        double done = scalable.scale(testNetwork, 100.0);
        Assert.assertTrue(DoubleMath.fuzzyEquals(100, done, 0.0001));
    }

    @Test
    public void testConvertGlskPointToScalableB42CountryGskLsk() {
        Scalable scalable = GlskPointScalableConverter.convert(testNetwork, glskPointCountryGskLsk, 0);
        double done = scalable.scale(testNetwork, 100.0);
        Assert.assertTrue(DoubleMath.fuzzyEquals(100, done, 0.0001));
    }

    @Test
    public void testConvertGlskPointToScalableB42ExplicitGskLsk() {
        Scalable scalable = GlskPointScalableConverter.convert(testNetwork, glskPointExplicitGskLsk, 0);
        double done = scalable.scale(testNetwork, 100.0);
        Assert.assertTrue(DoubleMath.fuzzyEquals(100, done, 0.0001));
    }

    @Test
    public void testConvertGlskPointToScalableB43GskLsk() {
        Scalable scalable = GlskPointScalableConverter.convert(testNetwork, glskPointParticipationFactorGskLsk, 0);
        double done = scalable.scale(testNetwork, 100.0);
        Assert.assertTrue(DoubleMath.fuzzyEquals(100, done, 0.0001));
    }

    @Test
    public void testConvertGlskPointToScalableB42Explicit() {
        Scalable scalable = GlskPointScalableConverter.convert(testNetwork, glskPointExplicit, 0);
        double done = scalable.scale(testNetwork, 100.0);
        Assert.assertTrue(DoubleMath.fuzzyEquals(100, done, 0.0001));
    }

    @Test
    public void testConvertGlskPointToScalableB43() {
        Scalable scalable = GlskPointScalableConverter.convert(testNetwork, glskPointParticipationFactor, 0);
        double done = scalable.scale(testNetwork, 100.0);
        Assert.assertTrue(DoubleMath.fuzzyEquals(100, done, 0.0001));
    }
}
