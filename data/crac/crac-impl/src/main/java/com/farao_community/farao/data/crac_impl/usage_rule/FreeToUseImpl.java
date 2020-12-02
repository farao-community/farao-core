/*
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.farao_community.farao.data.crac_impl.usage_rule;

import com.farao_community.farao.data.crac_api.Instant;
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_api.usage_rule.FreeToUse;
import com.farao_community.farao.data.crac_api.usage_rule.UsageMethod;
import com.farao_community.farao.data.crac_impl.json.serializers.usage_rule.FreeToUseSerializer;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * The remedial action is free to use with a given method and in a given state.
 *
 * @author Viktor Terrier {@literal <viktor.terrier at rte-france.com>}
 */
@JsonTypeName("free-to-use")
@JsonSerialize(using = FreeToUseSerializer.class)
public final class FreeToUseImpl extends AbstractUsageRule implements FreeToUse {

    private Instant instant;

    public FreeToUseImpl(UsageMethod usageMethod, Instant instant) {
        super(usageMethod);
        this.instant = instant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FreeToUseImpl rule = (FreeToUseImpl) o;
        return super.equals(o) && rule.getInstant().equals(instant);
    }

    @Override
    public int hashCode() {
        return usageMethod.hashCode() * 19 + instant.hashCode() * 47;
    }

    @Override
    public UsageMethod getUsageMethod(State state) {
        return state.getInstant().equals(instant) ? usageMethod : UsageMethod.UNDEFINED;
    }

    @Override
    public Instant getInstant() {
        return instant;
    }

    public void setIntant(Instant instant) {
        this.instant = instant;
    }
}
