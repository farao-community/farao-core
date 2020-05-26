/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_impl.threshold;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_impl.SimpleCnecAdder;

import java.util.Objects;

/**
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
public class ThresholdAdderImpl implements ThresholdAdder {

    private SimpleCnecAdder parent;
    private Unit unit;
    private Double maxValue;
    private Side side;
    private Direction direction;

    public ThresholdAdderImpl(SimpleCnecAdder parent) {
        Objects.requireNonNull(parent);
        this.parent = parent;
    }

    @Override
    public ThresholdAdder setUnit(Unit unit) {
        unit.checkPhysicalParameter(PhysicalParameter.FLOW);
        this.unit = unit;
        return this;
    }

    @Override
    public ThresholdAdder setMaxValue(Double maxValue) {
        this.maxValue = maxValue;
        return this;
    }

    @Override
    public ThresholdAdder setSide(Side side) {
        this.side = side;
        return this;
    }

    @Override
    public ThresholdAdder setDirection(Direction direction) {
        this.direction = direction;
        return this;
    }

    @Override
    public CnecAdder add() {
        if (this.unit == null) {
            throw new FaraoException("Cannot add a threshold without a unit. Please use setUnit.");
        }
        if (this.maxValue == null) {
            throw new FaraoException("Cannot add a threshold without a value. Please use setMaxValue.");
        }
        if (this.side == null) {
            throw new FaraoException("Cannot add a threshold without a side. Please use setSide.");
        }
        if (this.direction == null) {
            throw new FaraoException("Cannot add a threshold without a direction. Please use setDirection.");
        }
        if (this.unit == Unit.PERCENT_IMAX) {
            parent.addThreshold(new RelativeFlowThreshold(this.side, this.direction, this.maxValue));
        } else {
            parent.addThreshold(new AbsoluteFlowThreshold(this.unit, this.side, this.direction, this.maxValue));
        }
        return parent;
    }
}