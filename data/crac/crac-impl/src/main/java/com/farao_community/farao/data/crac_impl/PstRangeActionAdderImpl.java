package com.farao_community.farao.data.crac_impl;

import com.farao_community.farao.commons.FaraoException;
import com.farao_community.farao.data.crac_api.*;
import com.farao_community.farao.data.crac_impl.remedial_action.range_action.PstWithRange;
import java.util.Objects;

/**
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
public class PstRangeActionAdderImpl implements PstRangeActionAdder {
    private SimpleCrac parent;
    private String id;
    private Unit unit;
    private Double minValue;
    private Double maxValue;
    private NetworkElement networkElement;

    public PstRangeActionAdderImpl(SimpleCrac parent) {
        Objects.requireNonNull(parent);
        this.parent = parent;
    }

    @Override
    public PstRangeActionAdder setId(String id) {
        Objects.requireNonNull(id);
        this.id = id;
        return this;
    }

    @Override
    public PstRangeActionAdder setUnit(Unit unit) {
        Objects.requireNonNull(unit);
        this.unit = unit;
        return this;
    }

    @Override
    public PstRangeActionAdder setMinValue(Double minValue) {
        Objects.requireNonNull(minValue);
        this.minValue = minValue;
        return this;
    }

    @Override
    public PstRangeActionAdder setMaxValue(Double maxValue) {
        Objects.requireNonNull(maxValue);
        this.maxValue = maxValue;
        return this;
    }

    @Override
    public NetworkElement addNetworkElement(NetworkElement networkElement) {
        Objects.requireNonNull(networkElement);
        this.networkElement = networkElement;
        return networkElement;
    }

    @Override
    public NetworkElementAdder newNetworkElement() {
        if (networkElement == null) {
            return new NetworkElementAdderImpl<PstRangeActionAdder>(this);
        } else {
            throw new FaraoException("You can only add one network element to a PstRangeAction.");
        }
    }

    @Override
    public Crac add() {
        if (this.id == null) {
            throw new FaraoException("Cannot add a PstRangeAction without an id. Please use setId.");
        }
        if (this.unit == null) {
            throw new FaraoException("Cannot add a PstRangeAction without a unit. Please use setUnit.");
        }
        if (this.minValue == null) {
            throw new FaraoException("Cannot add a PstRangeAction without a minimum value. Please use setMinValue.");
        }
        if (this.maxValue == null) {
            throw new FaraoException("Cannot add a PstRangeAction without a maximum value. Please use setMaxValue.");
        }
        if (this.networkElement == null) {
            throw new FaraoException("Cannot add a PstRangeAction without a network element. Please use newNetworkElement.");
        }
        // TO DO : use unit, minValue, maxValue
        this.parent.addRangeAction(new PstWithRange(this.id, networkElement));
        return parent;
    }
}