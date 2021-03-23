/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.data.glsk.cse;

import com.farao_community.farao.data.glsk.api.AbstractGlskRegisteredResource;
import com.farao_community.farao.data.glsk.api.AbstractGlskShiftKey;
import com.farao_community.farao.data.glsk.api.GlskException;
import org.threeten.extra.Interval;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Optional;

/**
 * @author Sebastien Murgey {@literal <sebastien.murgey at rte-france.com>}
 */
public class CseGlskShiftKey extends AbstractGlskShiftKey {

    public CseGlskShiftKey(Element glskBlockElement, String businessType, Interval pointInterval, String subjectDomainmRID) {
        initCommonMemberVariables(glskBlockElement, businessType, pointInterval, subjectDomainmRID);

        if ("ManualGSKBlock".equals(glskBlockElement.getTagName())) {
            this.businessType = "B43";
            NodeList nodesList = glskBlockElement.getElementsByTagName("Node");
            double currentFactorsSum = 0;
            for (int i = 0; i < nodesList.getLength(); i++) {
                Element nodeElement = (Element) nodesList.item(i);
                CseGlskRegisteredResource cseRegisteredResource = new CseGlskRegisteredResource(nodeElement);
                registeredResourceArrayList.add(cseRegisteredResource);
                Optional<Double> initialFactor = cseRegisteredResource.getInitialFactor();
                if (initialFactor.isPresent()) {
                    currentFactorsSum += initialFactor.get();
                }
            }

            if (currentFactorsSum == 0) {
                throw new GlskException("Factors sum should not be 0");
            }

            for (AbstractGlskRegisteredResource registeredResource : registeredResourceArrayList) {
                CseGlskRegisteredResource cseRegisteredResource = (CseGlskRegisteredResource) registeredResource;
                Optional<Double> intialFactor = cseRegisteredResource.getInitialFactor();
                if (intialFactor.isPresent()) {
                    cseRegisteredResource.setParticipationFactor(intialFactor.get() / currentFactorsSum);
                }
            }
        } else if ("PropGSKBlock".equals(glskBlockElement.getTagName())) {
            importImplicitProportionalBlock(glskBlockElement, "B42");
        } else if ("PropLSKBlock".equals(glskBlockElement.getTagName())) {
            this.psrType = "A05"; // Enforce psrType that does not respect "official" format specification
            importImplicitProportionalBlock(glskBlockElement, "B42");
        } else if ("ReserveGSKBlock".equals(glskBlockElement.getTagName())) {
            importImplicitProportionalBlock(glskBlockElement, "B44");
        } else {
            throw new GlskException("Unknown UCTE Block type");
        }
    }

    public CseGlskShiftKey(Element glskBlockElement, String businessType, Interval pointInterval, String subjectDomainmRID, int position) {
        initCommonMemberVariables(glskBlockElement, businessType, pointInterval, subjectDomainmRID);
        this.meritOrderPosition = position;

        if ("MeritOrderGSKBlock".equals(glskBlockElement.getTagName())) {
            this.businessType = "B45";
            Element nodeElement = getNodeElement(glskBlockElement, position);
            CseGlskRegisteredResource cseRegisteredResource = new CseGlskRegisteredResource(nodeElement);
            registeredResourceArrayList.add(cseRegisteredResource);
        } else {
            throw new GlskException("Unknown UCTE Block type");
        }
    }

    private Element getNodeElement(Element glskBlockElement, int position) {
        if (position > 0) {
            // Up scalable element
            // Position is 1 to N for up scalable
            // Though, in XML file, we have to get child position - 1
            Element upBlockElement = (Element) glskBlockElement.getElementsByTagName("Up").item(0);
            return (Element) upBlockElement.getElementsByTagName("Node").item(position - 1);
        } else {
            // Down scalable element
            // Position is -1 to -N for down scalable
            // Though, in XML file, we have to get child -position - 1
            Element downBlockElement = (Element) glskBlockElement.getElementsByTagName("Down").item(0);
            return (Element) downBlockElement.getElementsByTagName("Node").item(-position - 1);
        }
    }

    private void initCommonMemberVariables(Element glskBlockElement, String businessType, Interval pointInterval, String subjectDomainmRID) {
        if (businessType.equals("Z02")) {
            this.psrType = "A04";
        } else if (businessType.equals("Z05")) {
            this.psrType = "A05";
        } else {
            throw new GlskException("in GlskShiftKey UCTE constructor: unknown ucteBusinessType: " + businessType);
        }
        this.quantity  = (0 == glskBlockElement.getElementsByTagName("Factor").getLength()) ? 1.0 :
                Double.valueOf(((Element) glskBlockElement.getElementsByTagName("Factor").item(0)).getAttribute("v")); //"factor" is optional
        this.glskShiftKeyInterval = pointInterval;
        this.subjectDomainmRID = subjectDomainmRID;
        this.registeredResourceArrayList = new ArrayList<>();
        this.orderInHybridCseGlsk  = (0 == glskBlockElement.getElementsByTagName("Order").getLength()) ? DEFAULT_ORDER :
                Integer.parseInt((glskBlockElement.getElementsByTagName("Order").item(0)).getTextContent()); //order in hybrid cse glsk
        this.maximumShift = (0 == glskBlockElement.getElementsByTagName("MaximumShift").getLength()) ? DEFAULT_MAXIMUM_SHIFT :
                Double.parseDouble(((Element) glskBlockElement.getElementsByTagName("MaximumShift").item(0)).getAttribute("v")); //maximum shift in hybrid cse glsk
    }

    private void importImplicitProportionalBlock(Element glskBlockElement, String businessType) {
        this.businessType = businessType;

        NodeList nodesList = glskBlockElement.getElementsByTagName("Node");
        for (int i = 0; i < nodesList.getLength(); i++) {
            Element nodeElement = (Element) nodesList.item(i);
            CseGlskRegisteredResource cseRegisteredResource = new CseGlskRegisteredResource(nodeElement);
            registeredResourceArrayList.add(cseRegisteredResource);
        }
    }
}
