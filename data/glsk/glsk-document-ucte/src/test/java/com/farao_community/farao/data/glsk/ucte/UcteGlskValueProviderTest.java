/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.data.glsk.ucte;

import com.farao_community.farao.data.glsk.api.GlskProvider;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.time.Instant;

import static org.junit.Assert.*;

/**
 * FlowBased Glsk Values Provider Test for Ucte format
 *
 * @author Baptiste Seguinot {@literal <baptiste.seguinot at rte-france.com>}
 */
public class UcteGlskValueProviderTest {

    private static final double EPSILON = 0.0001;

    @Test
    public void testProvideOkUcteGlsk() throws IOException, SAXException, ParserConfigurationException {
        Network network = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        Instant instant = Instant.parse("2016-07-29T10:00:00Z");

        GlskProvider ucteGlskProvider = UcteGlskDocument.importGlsk(getClass().getResourceAsStream("/20170322_1844_SN3_FR2_GLSK_test.xml"))
            .getGlskProvider(network, instant);
        assertEquals(3, ucteGlskProvider.getLinearGlsk("10YFR-RTE------C").getGLSKs().size());
        assertEquals(0.3, ucteGlskProvider.getLinearGlsk("10YFR-RTE------C").getGLSKs().get("FFR1AA1 _generator"), EPSILON);
    }

    @Test
    public void testProvideUcteGlskEmptyInstant() throws IOException, SAXException, ParserConfigurationException {
        Network network = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        Instant instant = Instant.parse("2020-07-29T10:00:00Z");

        GlskProvider ucteGlskProvider = UcteGlskDocument.importGlsk(getClass().getResourceAsStream("/20170322_1844_SN3_FR2_GLSK_test.xml"))
            .getGlskProvider(network, instant);

        assertTrue(ucteGlskProvider.getLinearGlskPerArea().isEmpty());
    }

    @Test
    public void testProvideUcteGlskUnknownCountry() throws IOException, SAXException, ParserConfigurationException {
        Network network = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        Instant instant = Instant.parse("2016-07-29T10:00:00Z");

        GlskProvider ucteGlskProvider = UcteGlskDocument.importGlsk(getClass().getResourceAsStream("/20170322_1844_SN3_FR2_GLSK_test.xml"))
            .getGlskProvider(network, instant);

        assertNull(ucteGlskProvider.getLinearGlsk("unknowncountry"));
    }

    @Test
    public void testProvideUcteGlskWithWrongFormat() throws IOException, SAXException, ParserConfigurationException {
        Network network = Importers.loadNetwork("testCase.xiidm", getClass().getResourceAsStream("/testCase.xiidm"));
        Instant instant = Instant.parse("2016-07-29T10:00:00Z");
        GlskProvider ucteGlskProvider = UcteGlskDocument.importGlsk(getClass().getResourceAsStream("/GlskCountry.xml"))
            .getGlskProvider(network, instant);
        assertTrue(ucteGlskProvider.getLinearGlskPerArea().isEmpty());
    }
}