/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012-2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
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

package org.opennms.features.topology.api;

import com.vaadin.ui.UI;

/**
 * Context object to allow access to the underlying UI and GraphContainer.
 *
 * @author Donald Desloge
 * @author Markus von Rüden
 *
 */
public interface OperationContext {
	
	enum DisplayLocation { MENUBAR, CONTEXTMENU, SEARCH }

    /**
     * Returns the current UI, the operation was triggered from
     *
     * @return the current UI, the operation was triggered from
     */
    UI getMainWindow();

    /**
     * Returns the current {@link GraphContainer}
     *
     * @return The current {@link GraphContainer}
     */
    GraphContainer getGraphContainer();

    /**
     * Returns the {@link DisplayLocation} the operation was triggered from
     *
     * @return the {@link DisplayLocation} the operation was triggered from
     */
    DisplayLocation getDisplayLocation();
}
