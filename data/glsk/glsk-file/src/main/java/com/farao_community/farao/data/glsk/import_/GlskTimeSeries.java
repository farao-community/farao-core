/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.data.glsk.import_;

import com.farao_community.farao.commons.FaraoException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * CIM type GLSK internal object: contains a list of GlskPeriod
 * @author Pengbo Wang {@literal <pengbo.wang@rte-international.com>}
 */
public class GlskTimeSeries {

    /**
     * mrid of time series
     */
    private String mRID;
    /**
     * country mrid
     */
    private String subjectDomainmRID;
    /**
     * curve type A01 or A03
     */
    private String curveType;
    /**
     * list of periods in the time series
     */
    private List<GlskPeriod> glskPeriods;

    /**
     * @param element Time series element
     */
    public GlskTimeSeries(Element element) {
        Objects.requireNonNull(element);
        this.mRID = element.getElementsByTagName("mRID").item(0).getTextContent();
        this.subjectDomainmRID = Objects.requireNonNull(element)
                .getElementsByTagName("subject_Domain.mRID")
                .item(0)
                .getTextContent();
        this.curveType = element.getElementsByTagName("curveType").getLength() == 0 ? "A03" :
                element.getElementsByTagName("curveType").item(0).getTextContent();
        if (!this.curveType.equals("A03") && !this.curveType.equals("A01")) {
            throw new FaraoException("CurveType not supported: " + this.curveType);
        }

        this.glskPeriods = new ArrayList<>();
        NodeList glskPeriodsElements = element.getElementsByTagName("Period");
        for (int i = 0; i < glskPeriodsElements.getLength(); i++) {
            glskPeriods.add(new GlskPeriod((Element) glskPeriodsElements.item(i), subjectDomainmRID, this.curveType));
        }
    }

    /**
     * @return get all glsk point in a time series
     */
    public List<GlskPoint> getGlskPointListInGlskTimeSeries() {
        List<GlskPoint> glskPointList = new ArrayList<>();
        for (GlskPeriod p : getGlskPeriods()) {
            List<GlskPoint> list = p.getGlskPoints();
            glskPointList.addAll(list);
        }
        return glskPointList;
    }

    /**
     * @return get all glsk periods
     */
    public List<GlskPeriod> getGlskPeriods() {
        return glskPeriods;
    }

    /**
     * @return getter mrid
     */
    public String getmRID() {
        return mRID;
    }

    /**
     * @param mRID setter mrid
     */
    public void setmRID(String mRID) {
        this.mRID = mRID;
    }

    /**
     * @return get curve type
     */
    public String getCurveType() {
        return curveType;
    }

    /**
     * @param curveType setter curve type
     */
    public void setCurveType(String curveType) {
        this.curveType = curveType;
    }
}
