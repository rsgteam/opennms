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

import java.util.List;
import java.util.Objects;

import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.events.api.EventIpcManager;
import org.opennms.netmgt.events.api.EventListener;
import org.opennms.netmgt.xml.event.Event;

import com.google.common.collect.Lists;

public class ClassificationEventListener implements EventListener {
    // The UEIs this listener is interested in
    private static final List<String> UEI_LIST =
            Lists.newArrayList(EventConstants.RELOAD_TOPOLOGY_UEI, EventConstants.RELOAD_DAEMON_CONFIG_SUCCESSFUL_UEI);

    private final ClassificationEngine classificationEngine;

    private final EventIpcManager eventIpcManager;

    public ClassificationEventListener(ClassificationEngine classificationEngine, EventIpcManager eventIpcManager) {
        this.classificationEngine = Objects.requireNonNull(classificationEngine);
        this.eventIpcManager = Objects.requireNonNull(eventIpcManager);
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public void onEvent(Event e) {
        // Reload given Topology or all
        if (e.getUei().equals(EventConstants.RELOAD_DAEMON_CONFIG_UEI)) {
            // TODO MVR implement proper reload event handling
            classificationEngine.reload();
        }
    }

    public void init() {
        eventIpcManager.addEventListener(this, UEI_LIST);
    }

    public void destroy() {
        eventIpcManager.removeEventListener(this, UEI_LIST);
    }
}