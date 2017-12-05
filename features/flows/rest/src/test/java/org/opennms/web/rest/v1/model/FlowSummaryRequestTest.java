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

package org.opennms.web.rest.v1.model;

import java.io.IOException;

import org.junit.Test;
import org.opennms.core.test.xml.JsonTest;

public class FlowSummaryRequestTest {

    @Test
    public void testMarshalJson() throws IOException {
        FlowSummaryRequest request = new FlowSummaryRequest();
        request.setReport(ReportType.TopNApplications);
        request.setStart(1);
        request.setEnd(100);

        FlowRequestFilter filter = new FlowRequestFilter();
        filter.setLocation("location-x");
        filter.setNode("FS:FID");
        filter.setIfIndex(62);
        request.setFilter(filter);

        request.setN(5);

        String requestString = JsonTest.marshalToJson(request);
        JsonTest.assertJsonEquals("{\n" +
                "  \"report\" : \"TopNApplications\",\n" +
                "  \"start\" : 1,\n" +
                "  \"end\" : 100,\n" +
                "  \"filter\" : {\n" +
                "    \"location\" : \"location-x\",\n" +
                "    \"node\" : \"FS:FID\",\n" +
                "    \"ifIndex\" : 62\n" +
                "  },\n" +
                "  \"N\" : 5\n" +
                "}", requestString);
    }
}
