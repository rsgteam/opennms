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

package org.opennms.netmgt.flows.elastic.ext;

import java.util.Objects;

public class ProtocolDefinition implements Protocol {
    private String ports;
    private String name;
    private ProtocolType type;
    private String ipMatch;

    public ProtocolDefinition(String name, String ports, ProtocolType type) {
        this(name, ports, type, null);
    }

    public ProtocolDefinition(String name, String ports, ProtocolType type, String ipMatch) {
        this.name = Objects.requireNonNull(name);
        this.ports = Objects.requireNonNull(ports);
        this.type = Objects.requireNonNull(type);
        this.ipMatch = ipMatch;
    }

    public String getPorts() {
        return ports;
    }

    public void setPorts(String ports) {
        this.ports = ports;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProtocolType getType() {
        return type;
    }

    public void setType(ProtocolType type) {
        this.type = type;
    }

    public String getIpMatch() {
        return ipMatch;
    }

    public void setIpMatch(String ipMatch) {
        this.ipMatch = ipMatch;
    }
}
