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

package org.opennms.netmgt.flows.api;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConversationKey {
    private static final Pattern s_pattern = Pattern.compile("^(?<protocol>\\d+),(?<sourceip>[\\d\\.]+),(?<sourceport>\\d+),(?<destip>[\\d\\.]+),(?<destport>\\d+)$");

    private final int protocol;
    private final String sourceIp;
    private final String destIp;
    private final int sourcePort;
    private final int destPort;

    public ConversationKey(int protocol, String sourceIp, int sourcePort, String destIp, int destPort) {
        this.protocol = protocol;
        this.sourceIp = Objects.requireNonNull(sourceIp);
        this.sourcePort = sourcePort;
        this.destIp = Objects.requireNonNull(destIp);
        this.destPort = destPort;
    }

    public int getProtocol() {
        return protocol;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getDestIp() {
        return destIp;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public int getDestPort() {
        return destPort;
    }

    public String toKeyword() {
        return String.format("%d,%s,%d,%s,%d", protocol, sourceIp, sourcePort, destIp, destPort);
    }

    public static ConversationKey fromKeyword(String keyword) {
        final Matcher m = s_pattern.matcher(keyword);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid keyword: " + keyword);
        }
        return new ConversationKey(Integer.parseInt(m.group(1)),
                m.group(2), Integer.parseInt(m.group(3)),
                m.group(4), Integer.parseInt(m.group(5)));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConversationKey that = (ConversationKey) o;
        return protocol == that.protocol &&
                sourcePort == that.sourcePort &&
                destPort == that.destPort &&
                Objects.equals(sourceIp, that.sourceIp) &&
                Objects.equals(destIp, that.destIp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(protocol, sourceIp, destIp, sourcePort, destPort);
    }

    public static String egressKeyFor(NetflowDocument document) {
        return new ConversationKey(document.getIpProtocol(),
                document.getIpv4SourceAddress(), document.getSourcePort(),
                document.getIpv4DestAddress(), document.getDestPort()).toKeyword();
    }

    public static String ingressKeyFor(NetflowDocument document) {
        return new ConversationKey(document.getIpProtocol(),
                document.getIpv4DestAddress(), document.getDestPort(),
                document.getIpv4SourceAddress(), document.getSourcePort()).toKeyword();
    }

    public static String keyFor(NetflowDocument document, boolean isInitiator) {
        if (isInitiator) {
            return new ConversationKey(document.getIpProtocol(),
                    document.getIpv4SourceAddress(), document.getSourcePort(),
                    document.getIpv4DestAddress(), document.getDestPort()).toKeyword();
        } else {
            return new ConversationKey(document.getIpProtocol(),
                    document.getIpv4DestAddress(), document.getDestPort(),
                    document.getIpv4SourceAddress(), document.getSourcePort()).toKeyword();
        }
    }
}
