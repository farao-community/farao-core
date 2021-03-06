/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_impl;

import com.farao_community.farao.data.crac_api.NetworkElement;
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_api.cnec.Cnec;

/**
 * @author Joris Mancini {@literal <joris.mancini at rte-france.com>}
 */
public abstract class AbstractCnec<I extends Cnec<I>> extends AbstractIdentifiable<I> implements Cnec<I> {

    protected final NetworkElement networkElement;
    protected final State state;
    protected boolean optimized;
    protected boolean monitored;
    protected String operator = null;
    protected double frm = 0;

    protected AbstractCnec(String id,
                           String name,
                           NetworkElement networkElement,
                           String operator,
                           State state,
                           boolean optimized,
                           boolean monitored,
                           double frm) {
        super(id, name);
        this.networkElement = networkElement;
        this.operator = operator;
        this.state = state;
        this.optimized = optimized;
        this.monitored = monitored;
        this.frm = frm;
    }

    @Override
    public final State getState() {
        return state;
    }

    @Override
    public final NetworkElement getNetworkElement() {
        return networkElement;
    }

    @Override
    public boolean isOptimized() {
        return optimized;
    }

    @Override
    @Deprecated
    public void setOptimized(boolean optimized) {
        this.optimized = optimized;
    }

    @Override
    public boolean isMonitored() {
        return monitored;
    }

    @Override
    @Deprecated
    public void setMonitored(boolean monitored) {
        this.monitored = monitored;
    }

    @Override
    public String getOperator() {
        return this.operator;
    }

    @Override
    public double getReliabilityMargin() {
        return frm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractCnec<?> cnec = (AbstractCnec<?>) o;
        return super.equals(cnec)
            && networkElement.equals(cnec.getNetworkElement())
            && state.equals(cnec.getState())
            && optimized == cnec.isOptimized()
            && monitored == cnec.isMonitored();
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + networkElement.hashCode();
        result = 31 * result + state.hashCode();
        return result;
    }
}
