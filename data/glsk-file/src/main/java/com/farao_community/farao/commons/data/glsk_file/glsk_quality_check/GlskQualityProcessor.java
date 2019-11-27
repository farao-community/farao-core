package com.farao_community.farao.commons.data.glsk_file.glsk_quality_check;

import com.farao_community.farao.commons.data.glsk_file.UcteGlskDocument;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

/**
 * @author Marc Erkol {@literal <marc.erkol at rte-france.com>}
 */
public class GlskQualityProcessor {

    public static QualityReport process(String cgmName, InputStream cgmIs, InputStream glskIs, Instant localDate) throws IOException, SAXException, ParserConfigurationException {
        return process(UcteGlskDocument.importGlskFromFile(glskIs), Importers.loadNetwork(cgmName, cgmIs), localDate);
    }

    public static QualityReport process(UcteGlskDocument ucteGlskDocument, Network network, Instant instant) {
        GlskQualityCheckInput input = new GlskQualityCheckInput(ucteGlskDocument, network, instant);
        return GlskQualityCheck.gskQualityCheck(input);
    }
}
