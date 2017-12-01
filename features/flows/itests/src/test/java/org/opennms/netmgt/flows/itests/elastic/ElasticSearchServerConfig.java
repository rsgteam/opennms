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

package org.opennms.netmgt.flows.itests.elastic;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.common.network.NetworkModule;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.node.Node;

public class ElasticSearchServerConfig {

    private static final String DEFAULT_HOME_DIRECTORY = "target/elasticsearch-home";

    private final Settings.Builder builder = Settings.builder();
    private long startDelay; // in ms
    private boolean manualStartup;

    public static File createTempDirectory()
            throws IOException
    {
        final File temp;

        temp = File.createTempFile("temp", Long.toString(System.nanoTime()));

        if(!(temp.delete()))
        {
            throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
        }

        if(!(temp.mkdir()))
        {
            throw new IOException("Could not create temp directory: " + temp.getAbsolutePath());
        }

        return (temp);
    }

    public ElasticSearchServerConfig withDefaults() {
        final File dir;
        try {
            dir = createTempDirectory();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        withNodeName("testNode");
        withTransportType("local");
        withClusterName("testCluster");
        withHomeDirectory(dir.getAbsolutePath());
        withHttpEnabled(false);
        // make startup faster
        withSetting("discovery.zen.ping_timeout", 0);

        return this;
    }

    public ElasticSearchServerConfig withHttpEnabled(boolean httpEnabled) {
        builder.put(NetworkModule.HTTP_ENABLED.getKey(), httpEnabled);
        return this;
    }

    public ElasticSearchServerConfig withNodeName(String name) {
        Objects.requireNonNull(name);
        builder.put(Node.NODE_NAME_SETTING.getKey(), name);
        return this;
    }

    public ElasticSearchServerConfig withTransportType(String transportType) {
        Objects.requireNonNull(transportType);
        builder.put(NetworkModule.TRANSPORT_TYPE_KEY, transportType);
        return this;
    }

    public ElasticSearchServerConfig withClusterName(String clusterName) {
        Objects.requireNonNull(clusterName);
        builder.put(ClusterName.CLUSTER_NAME_SETTING.getKey(), clusterName);
        return this;
    }

    public ElasticSearchServerConfig withHomeDirectory(String homeDirectory) {
        Objects.requireNonNull(homeDirectory);
        builder.put(Environment.PATH_HOME_SETTING.getKey(), homeDirectory);
        return this;
    }

    public ElasticSearchServerConfig withSetting(String key, Object value) {
        Objects.requireNonNull(key);
        builder.put(key, value);
        return this;
    }

    public ElasticSearchServerConfig enableCors() {
        this.withSetting("http.cors.allow-headers", "X-Requested-With,X-Auth-Token,Content-Type, Content-Length, Authorization")
                .withSetting("http.cors.allow-methods", "OPTIONS, HEAD, GET, POST, PUT, DELETE")
                .withSetting("http.cors.allow-origin", "/.*/")
                .withSetting("http.cors.enabled", "true");
        return this;
    }

    public ElasticSearchServerConfig withStartDelay(long elasticStartDelay) {
        this.startDelay = elasticStartDelay;
        return this;
    }

    public ElasticSearchServerConfig withManualStartup() {
        this.manualStartup = true;
        return this;
    }

    public long getStartDelay() {
        return startDelay;
    }

    public boolean isManualStartup() {
        return manualStartup;
    }

    public Settings build() {
        return builder.build();
    }

}
