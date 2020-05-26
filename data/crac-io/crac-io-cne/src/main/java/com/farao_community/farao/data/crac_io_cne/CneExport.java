/*
 *  Copyright (c) 2020, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_io_cne;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.Unit;
import com.farao_community.farao.data.crac_io_api.CracExporter;
import com.google.auto.service.AutoService;
import com.powsybl.iidm.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.*;
import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;

import static com.farao_community.farao.data.crac_io_cne.CneConstants.*;

/**
 * Xml export of the CNE file
 *
 * @author Viktor Terrier {@literal <viktor.terrier at rte-france.com>}
 */
@AutoService(CracExporter.class)
public class CneExport implements CracExporter {

    private static final String CNE_FORMAT = "CNE";
    private static final String XSD_LOCATION = "src/main/resources/xsd/";
    private static final String CNE_XSD_LOCATION = XSD_LOCATION + CNE_XSD_2_4;
    private static final String LOCAL_EXTENSION_LOCATION = XSD_LOCATION + "urn-entsoe-eu-local-extension-types.xsd";
    private static final String CODELISTS_LOCATION = XSD_LOCATION + "urn-entsoe-eu-wgedi-codelists.xsd";

    private static final Logger LOGGER = LoggerFactory.getLogger(CneExport.class);

    @Override
    public String getFormat() {
        return CNE_FORMAT;
    }

    @Override
    public void exportCrac(Crac crac, OutputStream outputStream) {
        throw new FaraoException("Network is missing!");
    }

    @Override
    public void exportCrac(Crac crac, Network network, OutputStream outputStream) {
        // TODO: Future feature: parametrize the choice of the unit (not useful for now)
        Cne cne = new Cne();
        cne.generate(crac, network, Unit.MEGAWATT);
        CriticalNetworkElementMarketDocument marketDocument = cne.getMarketDocument();
        StringWriter stringWriter = new StringWriter();

        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(CriticalNetworkElementMarketDocument.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

            // format the XML output
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            jaxbMarshaller.setProperty(Marshaller.JAXB_SCHEMA_LOCATION, CNE_XSD_2_4);

            QName qName = new QName(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, CNE_TAG);
            JAXBElement<CriticalNetworkElementMarketDocument> root = new JAXBElement<>(qName, CriticalNetworkElementMarketDocument.class, marketDocument);

            jaxbMarshaller.marshal(root, stringWriter);

            String result = stringWriter.toString().replace("xsi:" + CNE_TAG, CNE_TAG);

            if (!validateCNESchema(result)) {
                LOGGER.warn("CNE output doesn't fit the xsd.");
            }

            outputStream.write(result.getBytes());

        } catch (JAXBException | IOException e) {
            throw new FaraoException();
        }
    }

    public static boolean validateCNESchema(String xmlContent) {
        return validateXMLSchema(CNE_XSD_LOCATION, xmlContent);
    }

    private static boolean validateXMLSchema(String xsdPath, String xmlContent) {

        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");

            Source[] source = {new StreamSource(new File(xsdPath)),
                new StreamSource(new File(CODELISTS_LOCATION)),
                new StreamSource(new File(LOCAL_EXTENSION_LOCATION))};
            Schema schema = factory.newSchema(source);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xmlContent)));
        } catch (IOException | SAXException e) {
            LOGGER.warn(String.format("Exception: %s", e.getMessage()));
            return false;
        }
        return true;
    }
}