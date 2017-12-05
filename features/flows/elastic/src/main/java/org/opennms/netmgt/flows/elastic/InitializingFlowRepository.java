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

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import org.opennms.netmgt.flows.api.ConversationKey;
import org.opennms.netmgt.flows.api.FlowException;
import org.opennms.netmgt.flows.api.FlowRepository;
import org.opennms.netmgt.flows.api.NetflowDocument;
import org.opennms.netmgt.flows.api.model.Application;
import org.opennms.netmgt.flows.api.model.TrafficSummary;
import org.opennms.netmgt.flows.elastic.template.IndexSettings;

import com.google.common.collect.Table;

import io.searchbox.client.JestClient;

public class InitializingFlowRepository implements FlowRepository {

    private final ElasticFlowRepositoryInitializer initializer;
    private final FlowRepository delegate;

    public InitializingFlowRepository(final FlowRepository delegate, final JestClient client, final IndexSettings indexSettings) {
        Objects.requireNonNull(client);
        Objects.requireNonNull(delegate);
        Objects.requireNonNull(indexSettings);

        this.initializer = new ElasticFlowRepositoryInitializer(client, indexSettings);
        this.delegate = delegate;
    }

    @Override
    public void save(List<NetflowDocument> document) throws FlowException {
        ensureInitialized();
        delegate.save(document);
    }

    @Override
    public List<NetflowDocument> findAll(String query) throws FlowException {
        ensureInitialized();
        return delegate.findAll(query);
    }

    @Override
    public String rawQuery(String query) throws FlowException {
        ensureInitialized();
        return delegate.rawQuery(query);
    }

    @Override
    public CompletableFuture<List<TrafficSummary<Application>>> getTopNApplications(int N, long start, long end) {
        return ensureInitializedAsync(() -> delegate.getTopNApplications(N, start, end));
    }

    @Override
    public CompletableFuture<Table<Application, Long, Double>> getTopNApplicationsSeries(int N, long start, long end, long step) {
        return ensureInitializedAsync(() -> delegate.getTopNApplicationsSeries(N, start, end, step));
    }

    @Override
    public CompletableFuture<List<TrafficSummary<ConversationKey>>> getTopNConversations(int N, long start, long end) {
        return ensureInitializedAsync(() -> delegate.getTopNConversations(N, start, end));
    }

    @Override
    public CompletableFuture<Table<ConversationKey, Long, Double>> getTopNConversationsSeries(int N, long start, long end, long step) {
        return ensureInitializedAsync(() -> delegate.getTopNConversationsSeries(N, start, end, step));
    }

    private void ensureInitialized() {
        if (!initializer.isInitialized()) {
            initializer.initialize();
        }
    }

    private <U> CompletableFuture<U> ensureInitializedAsync(Callable<CompletableFuture<U>> callable) {
        final CompletableFuture<U> future = new CompletableFuture<>();
        // TODO: Actually make sure we're initialized in some async fashion
        CompletableFuture.completedFuture(null).thenRun(() -> {
            try {
                callable.call().whenComplete((res, ex) -> {
                    if (ex != null) {
                        future.completeExceptionally(ex);
                    } else {
                        future.complete(res);
                    }
                });
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
