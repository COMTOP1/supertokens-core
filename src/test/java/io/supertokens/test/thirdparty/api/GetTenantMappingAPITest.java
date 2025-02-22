package io.supertokens.test.thirdparty.api;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.JsonObject;

import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.test.httpRequest.HttpRequestForTesting;
import io.supertokens.ProcessState;
import io.supertokens.pluginInterface.STORAGE_TYPE;
import io.supertokens.pluginInterface.thirdparty.ThirdPartyTenantConfig;
import io.supertokens.storageLayer.StorageLayer;

import static org.junit.Assert.*;

import java.util.HashMap;

public class GetTenantMappingAPITest {

    @Rule
    public TestRule watchman = Utils.getOnFailure();

    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    @Test
    public void testGoodInput() throws Exception {

        String[] args = { "../" };

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        if (StorageLayer.getStorage(process.getProcess()).getType() != STORAGE_TYPE.SQL) {
            return;
        }

        String supertokensTenantId = "stId";
        String thirdPartyId = "tpId";
        JsonObject config = new JsonObject();
        config.addProperty("someKey", "someValue");

        {
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("supertokensTenantId", supertokensTenantId);
            requestBody.addProperty("thirdPartyId", thirdPartyId);
            requestBody.add("config", config);

            JsonObject response = HttpRequestForTesting.sendJsonPOSTRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/thirdparty/tenant/config", requestBody, 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "thirdparty");

            assertEquals("OK", response.get("status").getAsString());
            assertTrue(response.get("created").getAsBoolean());
            assertFalse(response.get("update").getAsBoolean());
        }

        // retrieve data
        {
            HashMap<String, String> QUERY_PARAMS = new HashMap<>();
            QUERY_PARAMS.put("supertokensTenantId", supertokensTenantId);
            QUERY_PARAMS.put("thirdPartyId", thirdPartyId);

            JsonObject response = HttpRequestForTesting.sendGETRequest(process.getProcess(), "",
                    "http://localhost:3567/recipe/thirdparty/tenant/config", QUERY_PARAMS, 1000, 1000, null,
                    Utils.getCdiVersionStringLatestForTests(), "thirdparty");
            assertEquals("OK", response.get("status").getAsString());
            assertEquals(config, response.get("config").getAsJsonObject());
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

}
