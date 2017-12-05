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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.opennms.netmgt.flows.api.ConversationKey;
import org.opennms.netmgt.flows.api.FlowException;
import org.opennms.netmgt.flows.api.FlowRepository;
import org.opennms.netmgt.flows.api.IndexStrategy;
import org.opennms.netmgt.flows.api.NetflowDocument;
import org.opennms.netmgt.flows.api.QueryException;
import org.opennms.netmgt.flows.api.TopNAppTrafficSummary;
import org.opennms.netmgt.flows.api.TopNConversationTrafficSummary;
import org.opennms.netmgt.flows.api.model.Application;
import org.opennms.netmgt.flows.api.model.TrafficSummary;
import org.opennms.netmgt.flows.elastic.ext.ConversationClassification;
import org.opennms.netmgt.flows.elastic.ext.ConversationClassifier;
import org.opennms.netmgt.flows.elastic.ext.Direction;
import org.opennms.netmgt.flows.elastic.ext.FlowClassification;
import org.opennms.netmgt.flows.elastic.ext.FlowClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Table;

import io.searchbox.action.Action;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestResult;
import io.searchbox.client.JestResultHandler;
import io.searchbox.core.Bulk;
import io.searchbox.core.BulkResult;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.search.aggregation.MetricAggregation;
import io.searchbox.core.search.aggregation.SumAggregation;
import io.searchbox.core.search.aggregation.TermsAggregation;

public class ElasticFlowRepository implements FlowRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ElasticFlowRepository.class);

    private final JestClient client;

    private final IndexStrategy indexStrategy;

    private final FlowClassifier flowClassifier;
    private final ConversationClassifier conversationClassifier;

    public ElasticFlowRepository(JestClient jestClient, IndexStrategy indexStrategy,
                                 FlowClassifier flowClassifier,
                                 ConversationClassifier conversationClassifier) {
        this.client = Objects.requireNonNull(jestClient);
        this.indexStrategy = Objects.requireNonNull(indexStrategy);
        this.flowClassifier = Objects.requireNonNull(flowClassifier);
        this.conversationClassifier = Objects.requireNonNull(conversationClassifier);
    }

    @Override
    public void save(List<NetflowDocument> flowDocuments) throws FlowException {
        if (flowDocuments != null && !flowDocuments.isEmpty()) {
            final String index = indexStrategy.getIndex(new Date());
            final String type = "flow";

            ///// START ENRICH
            for (NetflowDocument document : flowDocuments) {
                // Application classification
                final FlowClassification classification = flowClassifier.classify(document);
                if (classification.getSrcProtocol() != null) {
                    document.setSourceApplication(classification.getSrcProtocol().getName());
                }
                if (classification.getDstProtocol() != null) {
                    document.setDestApplication(classification.getDstProtocol().getName());
                }

                // Conversation classification
                final ConversationClassification convoClassification = conversationClassifier.classify(document);
                document.setConvoInitiator(convoClassification.isInitiator());
                document.setConvoKey(convoClassification.getConversationKey().toKeyword());
            }
            ////// STOP ENRICH

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

    private CompletableFuture<Map<String, Long>> getTotalBytesFromTopN(int N, long start, long end, String groupByTerm, String extraFilters) {
        final String query = "{\n" +
                "  \"size\": 0,\n" +
                "  \"query\": {\n" +
                "    \"bool\": {\n" +
                "      \"filter\": [\n" +
                (extraFilters != null ? extraFilters : "") +
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
                "    \"grouped_by\": {\n" +
                "      \"terms\": {\n" +
                String.format("        \"field\": \"%s\",\n", groupByTerm) +
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
        return searchAsync(query).thenApply(res -> {
            final Map<String, Long> topN = new LinkedHashMap<>();
            final MetricAggregation aggs = res.getAggregations();
            final TermsAggregation groupedBy = aggs.getTermsAggregation("grouped_by");
            for (TermsAggregation.Entry bucket : groupedBy.getBuckets()) {
                final SumAggregation sumAgg = bucket.getSumAggregation("total_bytes");
                topN.put(bucket.getKey(), sumAgg.getSum().longValue());
            }
            return topN;
        });
    }

    private <K,V> void topNToSummary(Map<K, Long> topN, Map<K, V> summaries, Function<K,V> creator, BiFunction<Long, V, Void> fn) {
        for (Map.Entry<K, Long> entry : topN.entrySet()) {
            V summary = summaries.get(entry.getKey());
            if (summary == null) {
                summary = creator.apply(entry.getKey());
                summaries.put(entry.getKey(), summary);
            }
            fn.apply(entry.getValue(), summary);
        }
    }

    @Override
    public CompletableFuture<List<TrafficSummary<Application>>> getTopNApplications(int N, long start, long end) {
        // Increase the multiplier for increased accuracy
        // See https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-terms-aggregation.html#_size
        final int multiplier = 2;

        final CompletableFuture<Map<String, Long>> ingressFuture = getTotalBytesFromTopN(multiplier*N, start, end, "dest_application", null);
        final CompletableFuture<Map<String, Long>> egressFuture = getTotalBytesFromTopN(multiplier*N, start, end, "source_application", null);
        return CompletableFuture.allOf(ingressFuture, egressFuture).thenApply((val) -> {
            final Map<String, Long> topNIngress;
            final Map<String, Long> topNEgress;
            try {
                topNIngress = ingressFuture.get();
                topNEgress = egressFuture.get();
            } catch (Exception e) {
                // Shouldn't happen since these futures have already been resolved
                throw new RuntimeException(e);
            }

            final Map<String, TrafficSummary<Application>> summaries = new HashMap<>();
            topNToSummary(topNIngress, summaries, name -> new TrafficSummary<>(new Application(name)), (bytesIn, summary) -> {
                summary.setBytesIn(bytesIn);
                return null;
            });
            topNToSummary(topNEgress, summaries, name -> new TrafficSummary<>(new Application(name)), (bytesOut, summary) -> {
                summary.setBytesOut(bytesOut);
                return null;
            });

            return summaries.values().stream()
                    // Sort by total bytes reversed
                    .sorted(Comparator.comparingLong(a -> -(a.getBytesIn() + a.getBytesOut())))
                    .limit(N)
                    .collect(Collectors.toList());
        });
    }

    @Override
    public CompletableFuture<Table<Application, Long, Double>> getTopNApplicationsSeries(int N, long start, long end, long step) {
        return null;
    }

    @Override
    public CompletableFuture<List<TrafficSummary<ConversationKey>>> getTopNConversations(int N, long start, long end) {
        // Increase the multiplier for increased accuracy
        // See https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-terms-aggregation.html#_size
        final int multiplier = 2;

        // https://www.plixer.com/blog/netflow-and-ipfix-2/netflow-direction-and-its-value-to-troubleshooting/
        final CompletableFuture<Map<String, Long>> ingressFuture = getTotalBytesFromTopN(multiplier*N, start, end, "convo_key", "{\"term\":{\"convo_initiator\":\"true\"}},");
        final CompletableFuture<Map<String, Long>> egressFuture = getTotalBytesFromTopN(multiplier*N, start, end, "convo_key", "{\"term\":{\"convo_initiator\":\"false\"}},");
        return CompletableFuture.allOf(ingressFuture, egressFuture).thenApply((val) -> {
            final Map<String, Long> topNIngress;
            final Map<String, Long> topNEgress;
            try {
                topNIngress = ingressFuture.get();
                topNEgress = egressFuture.get();
            } catch (Exception e) {
                // Shouldn't happen since these futures have already been resolved
                throw new RuntimeException(e);
            }

            final Map<String, TrafficSummary<ConversationKey>> summaries = new HashMap<>();
            topNToSummary(topNIngress, summaries, key -> new TrafficSummary<>(ConversationKey.fromKeyword(key)), (bytesIn, summary) -> {
                summary.setBytesIn(bytesIn);
                return null;
            });
            topNToSummary(topNEgress, summaries, key -> new TrafficSummary<>(ConversationKey.fromKeyword(key)), (bytesOut, summary) -> {
                summary.setBytesOut(bytesOut);
                return null;
            });

            return summaries.values().stream()
                    // Sort by total bytes reversed
                    .sorted(Comparator.comparingLong(a -> -(a.getBytesIn() + a.getBytesOut())))
                    .limit(N)
                    .collect(Collectors.toList());
        });
    }

    @Override
    public CompletableFuture<Table<ConversationKey, Long, Double>> getTopNConversationsSeries(int N, long start, long end, long step) {
        return null;
    }

    private <T extends JestResult> T executeRequest(Action<T> clientRequest) throws FlowException {
        try {
            return client.execute(clientRequest);
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

    private CompletableFuture<SearchResult> searchAsync(String query) {
        final CompletableFuture<SearchResult> future = new CompletableFuture<>();
        client.executeAsync(new Search.Builder(query)
                .addType("flow")
                .build(), new JestResultHandler<SearchResult>() {

            @Override
            public void completed(SearchResult result) {
                future.complete(result);
            }

            @Override
            public void failed(Exception ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }
}
