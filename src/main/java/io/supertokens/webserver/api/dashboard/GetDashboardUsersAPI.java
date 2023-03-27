/*
 *    Copyright (c) 2023, VRAI Labs and/or its affiliates. All rights reserved.
 *
 *    This software is licensed under the Apache License, Version 2.0 (the
 *    "License") as published by the Apache Software Foundation.
 *
 *    You may not use this file except in compliance with the License. You may
 *    obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */
package io.supertokens.webserver.api.dashboard;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.Main;
import io.supertokens.dashboard.Dashboard;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class GetDashboardUsersAPI extends WebserverAPI {

    private static final long serialVersionUID = -3243982612116134273L;

    public GetDashboardUsersAPI(Main main) {
        super(main, RECIPE_ID.DASHBOARD.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/dashboard/users";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {

        try {

            JsonArray arr = new com.google.gson.JsonParser().parse(new Gson().toJson(
                            Dashboard.getAllDashboardUsers(super.getTenantIdentifierWithStorageFromRequest(req).toAppIdentifier(), main)))
                    .getAsJsonArray();
            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.add("users", arr);
            super.sendJsonResponse(200, response, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }
}
