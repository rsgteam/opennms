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

package org.opennms.netmgt.flows.classification;

import static org.opennms.netmgt.flows.classification.DefaultClassificationEngine.createClassificationRequest;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

import org.opennms.netmgt.flows.api.NetflowDocument;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class CachingClassificationEngine implements ClassificationEngine {

    private final LoadingCache<ClassificationRequest, String> cache;
    private final ClassificationEngine delegate;

    public CachingClassificationEngine(ClassificationEngine delegate) {
        this.delegate = Objects.requireNonNull(delegate);
        this.cache = CacheBuilder.newBuilder()
            .maximumSize(500) // TODO MVR make configurable
            .build(
                new CacheLoader<ClassificationRequest, String>() {
                    @Override
                    public String load(ClassificationRequest key) {
                        return delegate.classify(key);
                    }
                });
    }

    @Override
    public String classify(ClassificationRequest classificationRequest) {
        try {
            return cache.get(classificationRequest);
        } catch (ExecutionException e) {
            throw new RuntimeException("Error loading entry from cache", e);
        }
    }

    @Override
    public String classify(NetflowDocument document) {
        // Don't invoke delegate, otherwise it is not cached
        final ClassificationRequest classificationRequest = createClassificationRequest(document);
        return classify(classificationRequest);
    }

    @Override
    public void reload() {
        this.cache.invalidateAll();
        this.delegate.reload();
    }
}
