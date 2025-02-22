package io.supertokens.webserver.api.totp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.supertokens.AppIdentifierWithStorageAndUserIdMapping;
import io.supertokens.Main;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.totp.TOTPDevice;
import io.supertokens.totp.Totp;
import io.supertokens.useridmapping.UserIdType;
import io.supertokens.webserver.InputParser;
import io.supertokens.webserver.WebserverAPI;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class GetTotpDevicesAPI extends WebserverAPI {
    private static final long serialVersionUID = -4641988458637882374L;

    public GetTotpDevicesAPI(Main main) {
        super(main, RECIPE_ID.TOTP.toString());
    }

    @Override
    public String getPath() {
        return "/recipe/totp/device/list";
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        // API is app specific
        String userId = InputParser.getQueryParamOrThrowError(req, "userId", false);

        if (userId.isEmpty()) {
            throw new ServletException(new BadRequestException("userId cannot be empty"));
        }

        JsonObject result = new JsonObject();

        try {
            AppIdentifierWithStorage appIdentifierWithStorage;
            try {
                // This step is required only because user_last_active table stores supertokens internal user id.
                // While sending the usage stats we do a join, so totp tables also must use internal user id.

                // Try to find the appIdentifier with right storage based on the userId
                AppIdentifierWithStorageAndUserIdMapping mappingAndStorage = getAppIdentifierWithStorageAndUserIdMappingFromRequest(
                        req, userId, UserIdType.ANY);

                if (mappingAndStorage.userIdMapping != null) {
                    userId = mappingAndStorage.userIdMapping.superTokensUserId;
                }
                appIdentifierWithStorage = mappingAndStorage.appIdentifierWithStorage;
            } catch (UnknownUserIdException e) {
                // if the user is not found, just use the storage of the tenant of interest
                appIdentifierWithStorage = getAppIdentifierWithStorage(req);
            }

            TOTPDevice[] devices = Totp.getDevices(appIdentifierWithStorage,
                    userId);
            JsonArray devicesArray = new JsonArray();

            for (TOTPDevice d : devices) {
                JsonObject item = new JsonObject();
                item.addProperty("name", d.deviceName);
                item.addProperty("period", d.period);
                item.addProperty("skew", d.skew);
                item.addProperty("verified", d.verified);

                devicesArray.add(item);
            }

            result.addProperty("status", "OK");
            result.add("devices", devicesArray);
            super.sendJsonResponse(200, result, resp);
        } catch (StorageQueryException | TenantOrAppNotFoundException e) {
            throw new ServletException(e);
        }
    }
}
