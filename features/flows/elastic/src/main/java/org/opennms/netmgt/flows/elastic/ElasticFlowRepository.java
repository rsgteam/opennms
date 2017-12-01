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

package org.opennms.netmgt.flows.elastic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.opennms.netmgt.flows.api.FlowException;
import org.opennms.netmgt.flows.api.FlowRepository;
import org.opennms.netmgt.flows.api.IndexStrategy;
import org.opennms.netmgt.flows.api.NetflowDocument;
import org.opennms.netmgt.flows.api.QueryException;
import org.opennms.netmgt.flows.api.TopNAppTrafficSummary;
import org.opennms.netmgt.flows.api.TopNConversationTrafficSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.searchbox.action.Action;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.core.Bulk;
import io.searchbox.core.BulkResult;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.search.aggregation.DateHistogramAggregation;
import io.searchbox.core.search.aggregation.MetricAggregation;
import io.searchbox.core.search.aggregation.SumAggregation;
import io.searchbox.core.search.aggregation.TermsAggregation;

public class ElasticFlowRepository implements FlowRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticFlowRepository.class);

    private final JestClient client;

    private final IndexStrategy indexStrategy;

    public ElasticFlowRepository(JestClient jestClient, IndexStrategy indexStrategy) {
        this.client = Objects.requireNonNull(jestClient);
        this.indexStrategy = Objects.requireNonNull(indexStrategy);
    }

    @Override
    public void save(List<NetflowDocument> flowDocuments) throws FlowException {
        if (flowDocuments != null && !flowDocuments.isEmpty()) {
            final String index = indexStrategy.getIndex(new Date());
            final String type = "flow";

            if (flowDocuments != null && !flowDocuments.isEmpty()) {
                final Bulk.Builder bulkBuilder = new Bulk.Builder();
                for (NetflowDocument document : flowDocuments) {
                    final Index.Builder indexBuilder = new Index.Builder(document)
                            .index(index)
                            .type(type);
                    bulkBuilder.addAction(indexBuilder.build());
                }
                final Bulk bulk = bulkBuilder.build();
                final BulkResult result = executeRequest(bulk);
                if (!result.isSucceeded()) {
                    LOG.error("Error while writing flows: {}", result.getErrorMessage());
                }
            }
        } else {
            LOG.warn("Received empty or null flows. Nothing to do.");
        }
    }

    @Override
    public String rawQuery(String query) throws FlowException {
        final SearchResult result = search(query);
        return result.getJsonString();
    }

    @Override
    public List<NetflowDocument> findAll(String query) throws FlowException {
        final SearchResult result = search(query);
        final List<SearchResult.Hit<NetflowDocument, Void>> hits = result.getHits(NetflowDocument.class);
        final List<NetflowDocument> data = hits.stream().map(hit -> hit.source).collect(Collectors.toList());
        return data;
    }

    @Override
    public List<TopNAppTrafficSummary> getTopNApplications(int N, long start, long end) throws FlowException {
        final String query = "{\n" +
                "  \"size\": 0,\n" +
                "  \"query\": {\n" +
                "    \"bool\": {\n" +
                "      \"filter\": [\n" +
                "        {\n" +
                "          \"range\": {\n" +
                "            \"timestamp\": {\n" +
                String.format("              \"gte\": %d,\n", start) +
                String.format("              \"lte\": %d,\n", end) +
                "              \"format\": \"epoch_millis\"\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  },\n" +
                "  \"aggs\": {\n" +
                "    \"apps\": {\n" +
                "      \"terms\": {\n" +
                "        \"field\": \"application\",\n" +
                String.format("        \"size\": %d\n", N) +
                "      },\n" +
                "      \"aggs\": {\n" +
                "          \"total_bytes\": {\n" +
                "            \"sum\": {\n" +
                "              \"field\": \"in_bytes\"\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n";

        final List<TopNAppTrafficSummary> topN = new ArrayList<>(N);
        final SearchResult result = search(query);
        final MetricAggregation aggs = result.getAggregations();
        final TermsAggregation apps = aggs.getTermsAggregation("apps");
        for (TermsAggregation.Entry app : apps.getBuckets()) {
            final SumAggregation sumAgg = app.getSumAggregation("total_bytes");

            TopNAppTrafficSummary summary = new TopNAppTrafficSummary();
            summary.setName(app.getKey());
            summary.setBytesIn(sumAgg.getSum().longValue());
            topN.add(summary);
        }
        return topN;
    }

    @Override
    public List<TopNConversationTrafficSummary> getTopNConversations(int N, long start, long end) throws FlowException {
        return Collections.emptyList();
    }

    private <T extends JestResult> T executeRequest(Action<T> clientRequest) throws FlowException {
        try {
            final T result = client.execute(clientRequest);
            return result;
        } catch (IOException e) {
            throw new FlowException("Error executing query", e);
        }
    }

    private SearchResult search(String query) throws FlowException {
        final SearchResult result = executeRequest(new Search.Builder(query)
                .addType("flow")
                .build());
        if (!result.isSucceeded()) {
            LOG.error("Error reading flows {}", result.getErrorMessage());
            throw new QueryException("Could not read flows from repository. " + result.getErrorMessage());
        }
        return result;
    }
}
