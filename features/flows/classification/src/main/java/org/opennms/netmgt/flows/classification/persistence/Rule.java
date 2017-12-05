/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.flows.classification.persistence;

import java.util.ArrayList;
import java.util.List;

import org.opennms.netmgt.flows.classification.classifier.Classifier;
import org.opennms.netmgt.flows.classification.classifier.CombinedClassifier;
import org.opennms.netmgt.flows.classification.matcher.IpMatcher;
import org.opennms.netmgt.flows.classification.matcher.Matcher;
import org.opennms.netmgt.flows.classification.matcher.PortMatcher;
import org.opennms.netmgt.flows.classification.matcher.ProtocolMatcher;

import com.google.common.base.Strings;

/**
 * A rule defines how a flow should be mapped.
 * From each rule a classifier is created, which allows to classify a flow by this rule.
 *
 * @author mvrueden
 */
public class Rule {
    /**
     * The name to map to.
     * Must not be null.
     */
    private String name;

    /**
     * The ip address to map.
     * May contain wildcards, e.g. 192.168.1.*. 192.168.*.*.
     * May be null.
     */
    private String ipAddress;

    /**
     * The port to map.
     * May define ranges, e.g.
     * 80,8980,8000-9000
     * Must always be provided.
     */
    private String port;

    /**
     * The protocol to map.
     * May define ranges, e.g. 17-20; 2,7,17
     */
    private String protocol;

    public Rule(String name, String ipAddress, String port) {
        this.name = name;
        this.port = port;
        this.ipAddress = ipAddress;
    }

    public Rule(String name, String port) {
        this(name, null, port);
    }

    public String getName() {
        return name;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getPort() {
        return port;
    }

    public Classifier toClassifier() {
        final List<Matcher> matchers = new ArrayList<>();

        if (!Strings.isNullOrEmpty(protocol)) {
            matchers.add(new ProtocolMatcher(protocol));
        }
        if (!Strings.isNullOrEmpty(ipAddress)) {
            matchers.add(new IpMatcher(ipAddress));
        }
        matchers.add(new PortMatcher(port));

        return new CombinedClassifier(name, matchers);
    }
}
