/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_impl;

import com.farao_community.farao.commons.FaraoException;

/**
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 */
public abstract class AbstractIdentifiableAdder<T extends AbstractIdentifiableAdder<T>> {

    protected String id;
    protected String name;

    protected void checkId() {
        if (this.id == null) {
            throw new FaraoException("Cannot add an identifiable object with no specified id. Please use setId.");
        } else if (this.name == null) {
            this.name = this.id;
        }
    }

    /**
     * Set the ID of the identifiable to add
     * @param id: ID to set
     * @return the identifiable adder instance
     */
    public T setId(String id) {
        this.id = id;
        return (T) this;
    }

    /**
     * Set the name of the identifiable to add
     * @param name: NAME to set
     * @return the identifiable adder instance
     */
    public T setName(String name) {
        this.name = name;
        return (T) this;
    }

}