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

package org.opennms.netmgt.flows.itests;

import static com.jayway.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.net.MalformedURLException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.opennms.core.soa.support.DefaultServiceRegistry;
import org.opennms.netmgt.flows.api.FlowException;
import org.opennms.netmgt.flows.api.FlowRepository;
import org.opennms.netmgt.flows.api.IndexStrategy;
import org.opennms.netmgt.flows.api.NetflowDocument;
import org.opennms.netmgt.flows.api.TopNAppTrafficSummary;
import org.opennms.netmgt.flows.api.TopNConversationTrafficSummary;
import org.opennms.netmgt.flows.elastic.ElasticFlowRepository;
import org.opennms.netmgt.flows.elastic.InitializingFlowRepository;
import org.opennms.netmgt.flows.elastic.ext.FlowClassifier;
import org.opennms.netmgt.flows.elastic.ext.FlowClassifierImpl;
import org.opennms.netmgt.flows.elastic.ext.ProtocolDefinition;
import org.opennms.netmgt.flows.elastic.ext.ProtocolType;
import org.opennms.netmgt.flows.elastic.template.IndexSettings;
import org.opennms.netmgt.flows.itests.elastic.ElasticSearchRule;
import org.opennms.netmgt.flows.itests.elastic.ElasticSearchServerConfig;
import org.opennms.plugins.elasticsearch.rest.RestClientFactory;

import com.google.common.collect.Lists;

import io.searchbox.client.JestClient;

public class FlowQueryIT {

    private static final String HTTP_PORT = "9205";
    private static final String HTTP_TRANSPORT_PORT = "9305";

    @Rule
    public ElasticSearchRule elasticServerRule = new ElasticSearchRule(
            new ElasticSearchServerConfig()
                    .withDefaults()
                    .withSetting("http.enabled", true)
                    .withSetting("http.port", HTTP_PORT)
                    .withSetting("http.type", "netty4")
                    .withSetting("transport.type", "netty4")
                    .withSetting("transport.tcp.port", HTTP_TRANSPORT_PORT)
    );

    private final static List<ProtocolDefinition> protocolDefinitions = Lists.newArrayList(
            new ProtocolDefinition("HTTP", "80,8080", ProtocolType.TCP),
            new ProtocolDefinition("HTTPS", "443", ProtocolType.TCP)
    );

    private FlowRepository flowRepository;

    @Before
    public void setUp() throws MalformedURLException, FlowException {
        final FlowClassifier flowClassifier = new FlowClassifierImpl(protocolDefinitions);
        final RestClientFactory restClientFactory = new RestClientFactory("http://localhost:" + HTTP_PORT, null, null);
        final JestClient client = restClientFactory.createClient();
        final ElasticFlowRepository esFlowRepository = new ElasticFlowRepository(client, IndexStrategy.MONTHLY, flowClassifier);
        final IndexSettings settings = new IndexSettings();
        flowRepository = new InitializingFlowRepository(esFlowRepository, client, settings);
        DefaultServiceRegistry.INSTANCE.register(flowRepository, FlowRepository.class);

        // Ensure we can communicate with the repository, and that it starts empty
        assertThat(flowRepository.findAll(null), hasSize(0));

        // Load the default set of flows
        loadDefaultFlows();
    }

    @Test
    public void canRetrieveTopNApps() throws ExecutionException, InterruptedException {
        final List<TopNAppTrafficSummary> appTrafficSummary = flowRepository.getTopNApplications(10, 0, 100).get();
        assertThat(appTrafficSummary, hasSize(1));
        assertThat(appTrafficSummary.get(0).getBytesIn(), equalTo(10L));
        assertThat(appTrafficSummary.get(0).getBytesOut(), equalTo(100L));
    }

    @Test
    public void canRetrieveTopNConversations() throws ExecutionException, InterruptedException {
        final List<TopNConversationTrafficSummary> convoTrafficSummary = flowRepository.getTopNConversations(10, 0, 100).get();
        assertThat(convoTrafficSummary, hasSize(1));
        final TopNConversationTrafficSummary convo = convoTrafficSummary.get(0);

        assertThat(convo.getKey().getSourceIp(), equalTo("192.168.1.100"));
        assertThat(convo.getKey().getDestIp(), equalTo("10.1.1.11"));
        assertThat(convo.getBytesIn(), equalTo(10L));
        assertThat(convo.getBytesOut(), equalTo(100L));
    }

    private void loadDefaultFlows() throws FlowException {
        final List<NetflowDocument> flows = new FlowBuilder()
                .withFlow(new Date(0), "192.168.1.100", 43444, "10.1.1.11", 80, 10)
                .withFlow(new Date(0), "10.1.1.11", 80, "192.168.1.100", 43444, 100)
                .build();
        flowRepository.save(flows);

        // Retrieve all the flows we just persisted
        await().atMost(30, TimeUnit.SECONDS).until(() -> flowRepository.findAll("{}"), hasSize(2));
    }

}
