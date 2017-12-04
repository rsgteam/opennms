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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.opennms.netmgt.flows.api.NetflowDocument;

import com.google.common.collect.Maps;

public class FlowClassifierImpl implements FlowClassifier {

    private final List<ProtocolDefinition> definitions;

    private final Map<Integer, ProtocolDefinition> portToDef = Maps.newHashMap();

    public FlowClassifierImpl(List<ProtocolDefinition> definitions) {
        this.definitions = definitions;
        for (ProtocolDefinition def : definitions) {
            final String portString = def.getPorts();
            final String[] portEntries = portString.split(",");
            for (String portEntry : portEntries) {
                for (Integer port : getPortsFromEntry(portEntry)) {
                    portToDef.putIfAbsent(port, def);
                }
            }
        }
    }

    @Override
    public FlowClassification classify(NetflowDocument flow) {
        final ProtocolDefinition srcProto = portToDef.get(flow.getSourcePort());
        final ProtocolDefinition dstProto = portToDef.get(flow.getDestPort());
        return new FlowClassificationImpl(srcProto, dstProto);
    }

    private static List<Integer> getPortsFromEntry(String portString) {
        final String[] ports = portString.split("-");
        if (ports.length == 1) {
            return Collections.singletonList(Integer.parseInt(ports[0]));
        } else if (ports.length == 2) {
            final Integer firstPort = Integer.parseInt(ports[0]);
            final Integer lastPort = Integer.parseInt(ports[1]);
            if (lastPort < firstPort) {
                throw new IllegalStateException("Invalid range: " + portString);
            }
            final List<Integer> portEntries = new ArrayList<>(lastPort - firstPort + 1);
            for (int i = firstPort; i <= lastPort; i++) {
                portEntries.add(i);
            }
            return portEntries;
        } else {
            throw new IllegalStateException("Invalid range: " + portString);
        }
    }

}
