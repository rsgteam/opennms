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

package org.opennms.web.rest.v1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.opennms.core.soa.lookup.ServiceLookup;
import org.opennms.core.soa.lookup.ServiceLookupBuilder;
import org.opennms.core.soa.support.DefaultServiceRegistry;
import org.opennms.netmgt.flows.api.ConversationKey;
import org.opennms.netmgt.flows.api.FlowRepository;
import org.opennms.netmgt.flows.api.model.Application;
import org.opennms.web.rest.v1.model.FlowSeriesRequest;
import org.opennms.web.rest.v1.model.FlowSeriesResponse;
import org.opennms.web.rest.v1.model.FlowSummaryRequest;
import org.opennms.web.rest.v1.model.FlowSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.google.common.collect.Table;

@Component
@Scope("prototype")
public class FlowRestServiceImpl implements FlowRestService {

    private static final Logger LOG = LoggerFactory.getLogger(FlowRestService.class);

    private ServiceLookup serviceLookup = new ServiceLookupBuilder(DefaultServiceRegistry.INSTANCE)
            .blocking()
            .build();

    @Override
    public FlowSeriesResponse getTimeSeries(FlowSeriesRequest request) {
        final CompletableFuture<FlowSeriesResponse> future;
        switch(request.getReport()) {
            case TopNApplications:
                future = getFlowRepository().getTopNApplicationsSeries(request.getN(), request.getStart(), request.getEnd(), request.getStep()).thenApply(res -> {
                    final FlowSeriesResponse response = new FlowSeriesResponse();
                    response.setStart(request.getStart());
                    response.setEnd(request.getEnd());
                    response.setLabels(res.rowKeySet().stream()
                        .map(Application::getName)
                        .collect(Collectors.toList()));
                    populateValues(res, response);
                    return response;
                });
                break;
            case TopNConversations:
                future = getFlowRepository().getTopNConversationsSeries(request.getN(), request.getStart(), request.getEnd(), request.getStep()).thenApply(res -> {
                    final FlowSeriesResponse response = new FlowSeriesResponse();
                    response.setStart(request.getStart());
                    response.setEnd(request.getEnd());
                    response.setLabels(res.rowKeySet().stream()
                            .map(k -> String.format("%s:%d <-> %s:%d", k.getSourceIp(), k.getSourcePort(), k.getDestIp(), k.getDestPort()))
                            .collect(Collectors.toList()));
                    populateValues(res, response);
                    return response;
                });
                break;
            default:
                throw new WebApplicationException("Fail.");
        }

        try {
            return future.get();
        } catch (Exception ex) {
            throw new WebApplicationException("Fail.", Response.status(500)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Error while fetching flows from repository: " + ex.getMessage())
                    .build());
        }
    }

    private static void populateValues(Table<?, Long, Double> table, FlowSeriesResponse response) {
        final List<Long> timestamps = table.columnKeySet().stream()
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());

        final List<List<Double>> columns = new LinkedList<>();
        for (Object rowKey : table.rowKeySet()) {
            final List<Double> column = new ArrayList<>(timestamps.size());
            for (Long ts : timestamps) {
                Double val = table.get(rowKey, ts);
                if (val == null) {
                    val = Double.NaN;
                }
                column.add(val);
            }
        }

        response.setTimestamps(timestamps);
        response.setColumns(columns);
    }

    @Override
    public FlowSummaryResponse getSummary(FlowSummaryRequest request) {
        final CompletableFuture<FlowSummaryResponse> future;
        switch(request.getReport()) {
            case TopNApplications:
                future = getFlowRepository().getTopNApplications(request.getN(), request.getStart(), request.getEnd()).thenApply(res -> {
                    final FlowSummaryResponse response = new FlowSummaryResponse();
                    response.setStart(request.getStart());
                    response.setEnd(request.getEnd());
                    response.setHeaders(Lists.newArrayList("Application", "Bytes In", "Bytes Out"));
                    response.setRows(res.stream()
                            .map(sum -> Arrays.asList((Object)sum.getKey().getName(), sum.getBytesIn(), sum.getBytesOut()))
                            .collect(Collectors.toList()));
                    return response;
                });
                break;
            case TopNConversations:
                future = getFlowRepository().getTopNConversations(request.getN(), request.getStart(), request.getEnd()).thenApply(res -> {
                    final FlowSummaryResponse response = new FlowSummaryResponse();
                    response.setStart(request.getStart());
                    response.setEnd(request.getEnd());
                    response.setHeaders(Lists.newArrayList("Source IP", "Source Port", "Dest IP", "Dest Port", "Bytes In", "Bytes Out"));
                    response.setRows(res.stream()
                            .map(sum -> {
                                final ConversationKey key = sum.getKey();
                                return Lists.newArrayList((Object)key.getSourceIp(), key.getSourcePort(), key.getDestIp(), key.getDestPort(),
                                        sum.getBytesIn(), sum.getBytesOut());
                            })
                            .collect(Collectors.toList()));
                    return response;
                });
                break;
            default:
                throw new WebApplicationException("Fail.");
        }

        try {
            return future.get();
        } catch (Exception ex) {
            throw new WebApplicationException("Fail.", Response.status(500)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("Error while fetching flows from repository: " + ex.getMessage())
                    .build());
        }
    }

    private FlowRepository getFlowRepository() {
        return serviceLookup.lookup(FlowRepository.class);
    }

}
