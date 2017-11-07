/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.telemetry.adapters.netflow.ipfix.session;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;

public final class Template implements Iterable<Field> {

    public enum Type {
        TEMPLATE,
        OPTIONS_TEMPLATE,
    }

    public final Type type;

    public final List<Field> scopeFields;
    public final List<Field> valueFields;

    private Template(final Type type,
                     final List<Field> scopeFields,
                     final List<Field> valueFields) {
        this.type = type;
        this.scopeFields = scopeFields;
        this.valueFields = valueFields;
    }

    public int count() {
        return this.scopeFields.size() + this.valueFields.size();
    }

    @Override
    public Iterator<Field> iterator() {
        return Iterators.concat(this.scopeFields.iterator(), this.valueFields.iterator());
    }

    public static class Builder {
        private Type type;

        private List<Field> fields = new LinkedList<>();

        private int scopedCount = 0;

        private Builder() {
        }

        public Builder withType(final Type type) {
            this.type = type;
            return this;
        }

        public Builder withFields(final List<Field> fields) {
            this.fields = fields;
            return this;
        }

        public Builder withScopedCount(final int scopedCount) {
            this.scopedCount = scopedCount;
            return this;
        }

        public Template build() {
            Preconditions.checkNotNull(this.type);
            Preconditions.checkNotNull(this.fields);
            Preconditions.checkPositionIndex(this.scopedCount, this.fields.size());

            return new Template(
                    this.type,
                    this.fields.subList(0, this.scopedCount),
                    this.fields.subList(this.scopedCount, this.fields.size())
            );
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
