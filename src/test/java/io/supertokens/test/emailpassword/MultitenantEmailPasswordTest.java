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

package io.supertokens.test.emailpassword;

import com.google.gson.JsonObject;
import io.supertokens.ProcessState;
import io.supertokens.emailpassword.EmailPassword;
import io.supertokens.emailpassword.exceptions.WrongCredentialsException;
import io.supertokens.featureflag.EE_FEATURES;
import io.supertokens.featureflag.FeatureFlagTestContent;
import io.supertokens.featureflag.exceptions.FeatureNotEnabledException;
import io.supertokens.multitenancy.Multitenancy;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.multitenancy.exception.CannotModifyBaseConfigException;
import io.supertokens.multitenancy.exception.DeletionInProgressException;
import io.supertokens.pluginInterface.emailpassword.UserInfo;
import io.supertokens.pluginInterface.emailpassword.exceptions.DuplicateEmailException;
import io.supertokens.pluginInterface.emailpassword.exceptions.UnknownUserIdException;
import io.supertokens.pluginInterface.exceptions.InvalidConfigException;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.exceptions.StorageTransactionLogicException;
import io.supertokens.pluginInterface.multitenancy.*;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.test.TestingProcessManager;
import io.supertokens.test.Utils;
import io.supertokens.thirdparty.InvalidProviderConfigException;
import io.supertokens.useridmapping.UserIdType;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class MultitenantEmailPasswordTest {
    @AfterClass
    public static void afterTesting() {
        Utils.afterTesting();
    }

    @Before
    public void beforeEach() {
        Utils.reset();
    }

    private void createTenants(TestingProcessManager.TestingProcess process)
            throws StorageQueryException, TenantOrAppNotFoundException, InvalidProviderConfigException,
            DeletionInProgressException, FeatureNotEnabledException, IOException, InvalidConfigException,
            CannotModifyBaseConfigException, BadPermissionException {
        // User pool 1 - (null, a1, null)
        // User pool 2 - (null, a1, t1), (null, a1, t2)

        { // tenant 1
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", null);

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 1);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, null, null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
                            config
                    )
            );
        }

        { // tenant 2
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t1");

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 2);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
                            config
                    )
            );
        }

        { // tenant 3
            JsonObject config = new JsonObject();
            TenantIdentifier tenantIdentifier = new TenantIdentifier(null, "a1", "t2");

            StorageLayer.getStorage(new TenantIdentifier(null, null, null), process.getProcess())
                    .modifyConfigToAddANewUserPoolForTesting(config, 2);

            Multitenancy.addNewOrUpdateAppOrTenant(
                    process.getProcess(),
                    new TenantIdentifier(null, "a1", null),
                    new TenantConfig(
                            tenantIdentifier,
                            new EmailPasswordConfig(true),
                            new ThirdPartyConfig(false, null),
                            new PasswordlessConfig(false),
                            config
                    )
            );
        }
    }

    @Test
    public void testSignUpAndLoginInDifferentTenants()
            throws InterruptedException, StorageQueryException, InvalidProviderConfigException,
            DeletionInProgressException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException,
            WrongCredentialsException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        createTenants(process);

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        TenantIdentifierWithStorage t1storage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifierWithStorage t2storage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");
        TenantIdentifierWithStorage t3storage = t3.withStorage(StorageLayer.getStorage(t3, process.getProcess()));

        {
            EmailPassword.signUp(t1storage, process.getProcess(), "user1@example.com", "password1");
            UserInfo userInfo = EmailPassword.signIn(t1storage, process.getProcess(), "user1@example.com", "password1");
            assertEquals("user1@example.com", userInfo.email);
        }

        {
            EmailPassword.signUp(t2storage, process.getProcess(), "user2@example.com", "password2");
            UserInfo userInfo = EmailPassword.signIn(t2storage, process.getProcess(), "user2@example.com", "password2");
            assertEquals("user2@example.com", userInfo.email);
        }

        {
            EmailPassword.signUp(t3storage, process.getProcess(), "user3@example.com", "password3");
            UserInfo userInfo = EmailPassword.signIn(t3storage, process.getProcess(), "user3@example.com", "password3");
            assertEquals("user3@example.com", userInfo.email);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testSameEmailWithDifferentPasswordsOnDifferentTenantsWorksCorrectly()
            throws InterruptedException, InvalidProviderConfigException, DeletionInProgressException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException,
            WrongCredentialsException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        createTenants(process);

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        TenantIdentifierWithStorage t1storage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifierWithStorage t2storage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");
        TenantIdentifierWithStorage t3storage = t3.withStorage(StorageLayer.getStorage(t3, process.getProcess()));


        EmailPassword.signUp(t1storage, process.getProcess(), "user@example.com", "password1");
        EmailPassword.signUp(t2storage, process.getProcess(), "user@example.com", "password2");
        EmailPassword.signUp(t3storage, process.getProcess(), "user@example.com", "password3");

        {
            UserInfo userInfo = EmailPassword.signIn(t1storage, process.getProcess(), "user@example.com", "password1");
            assertEquals("user@example.com", userInfo.email);
        }

        {
            UserInfo userInfo = EmailPassword.signIn(t2storage, process.getProcess(), "user@example.com", "password2");
            assertEquals("user@example.com", userInfo.email);
        }

        {
            UserInfo userInfo = EmailPassword.signIn(t3storage, process.getProcess(), "user@example.com", "password3");
            assertEquals("user@example.com", userInfo.email);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetUserUsingIdReturnsCorrectUser()
            throws InterruptedException, InvalidProviderConfigException, DeletionInProgressException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException,
            UnknownUserIdException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        createTenants(process);

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        TenantIdentifierWithStorage t1storage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifierWithStorage t2storage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");
        TenantIdentifierWithStorage t3storage = t3.withStorage(StorageLayer.getStorage(t3, process.getProcess()));

        UserInfo user1 = EmailPassword.signUp(t1storage, process.getProcess(), "user1@example.com", "password1");
        UserInfo user2 = EmailPassword.signUp(t2storage, process.getProcess(), "user2@example.com", "password2");
        UserInfo user3 = EmailPassword.signUp(t3storage, process.getProcess(), "user3@example.com", "password3");

        {
            UserInfo userInfo = EmailPassword.getUserUsingId(
                    StorageLayer.getAppIdentifierWithStorageAndUserIdMappingForUser(
                            process.getProcess(), new AppIdentifier(null, "a1"), user1.id,
                            UserIdType.SUPERTOKENS).appIdentifierWithStorage, user1.id);
            assertEquals(user1, userInfo);
        }

        {
            UserInfo userInfo = EmailPassword.getUserUsingId(
                    StorageLayer.getAppIdentifierWithStorageAndUserIdMappingForUser(
                            process.getProcess(), new AppIdentifier(null, "a1"), user2.id,
                            UserIdType.SUPERTOKENS).appIdentifierWithStorage, user2.id);
            assertEquals(user2, userInfo);
        }

        {
            UserInfo userInfo = EmailPassword.getUserUsingId(
                    StorageLayer.getAppIdentifierWithStorageAndUserIdMappingForUser(
                            process.getProcess(), new AppIdentifier(null, "a1"), user3.id,
                            UserIdType.SUPERTOKENS).appIdentifierWithStorage, user3.id);
            assertEquals(user3, userInfo);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testGetUserUsingEmailReturnsTheUserFromTheSpecificTenant()
            throws InterruptedException, InvalidProviderConfigException, DeletionInProgressException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        createTenants(process);

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        TenantIdentifierWithStorage t1storage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifierWithStorage t2storage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");
        TenantIdentifierWithStorage t3storage = t3.withStorage(StorageLayer.getStorage(t3, process.getProcess()));

        UserInfo user1 = EmailPassword.signUp(t1storage, process.getProcess(), "user@example.com", "password1");
        UserInfo user2 = EmailPassword.signUp(t2storage, process.getProcess(), "user@example.com", "password2");
        UserInfo user3 = EmailPassword.signUp(t3storage, process.getProcess(), "user@example.com", "password3");

        {
            UserInfo userInfo = EmailPassword.getUserUsingEmail(t1storage, user1.email);
            assertEquals(user1, userInfo);
        }

        {
            UserInfo userInfo = EmailPassword.getUserUsingEmail(t2storage, user2.email);
            assertEquals(user2, userInfo);
        }

        {
            UserInfo userInfo = EmailPassword.getUserUsingEmail(t3storage, user3.email);
            assertEquals(user3, userInfo);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }

    @Test
    public void testUpdatePasswordWorksCorrectlyAcrossAllTenants()
            throws InterruptedException, InvalidProviderConfigException, DeletionInProgressException,
            StorageQueryException, FeatureNotEnabledException, TenantOrAppNotFoundException, IOException,
            InvalidConfigException, CannotModifyBaseConfigException, BadPermissionException, DuplicateEmailException,
            UnknownUserIdException, StorageTransactionLogicException, WrongCredentialsException {
        String[] args = {"../"};

        TestingProcessManager.TestingProcess process = TestingProcessManager.start(args);
        FeatureFlagTestContent.getInstance(process.getProcess())
                .setKeyValue(FeatureFlagTestContent.ENABLED_FEATURES, new EE_FEATURES[]{EE_FEATURES.MULTI_TENANCY});
        process.startProcess();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STARTED));

        createTenants(process);

        TenantIdentifier t1 = new TenantIdentifier(null, "a1", null);
        TenantIdentifierWithStorage t1storage = t1.withStorage(StorageLayer.getStorage(t1, process.getProcess()));
        TenantIdentifier t2 = new TenantIdentifier(null, "a1", "t1");
        TenantIdentifierWithStorage t2storage = t2.withStorage(StorageLayer.getStorage(t2, process.getProcess()));
        TenantIdentifier t3 = new TenantIdentifier(null, "a1", "t2");
        TenantIdentifierWithStorage t3storage = t3.withStorage(StorageLayer.getStorage(t3, process.getProcess()));

        UserInfo user1 = EmailPassword.signUp(t1storage, process.getProcess(), "user@example.com", "password1");
        UserInfo user2 = EmailPassword.signUp(t2storage, process.getProcess(), "user@example.com", "password2");
        UserInfo user3 = EmailPassword.signUp(t3storage, process.getProcess(), "user@example.com", "password3");

        EmailPassword.updateUsersEmailOrPassword(
                StorageLayer.getAppIdentifierWithStorageAndUserIdMappingForUser(
                        process.getProcess(), new AppIdentifier(null, "a1"), user1.id,
                        UserIdType.SUPERTOKENS).appIdentifierWithStorage,
                process.getProcess(), user1.id, null, "newpassword1");
        EmailPassword.updateUsersEmailOrPassword(
                StorageLayer.getAppIdentifierWithStorageAndUserIdMappingForUser(
                        process.getProcess(), new AppIdentifier(null, "a1"), user2.id,
                        UserIdType.SUPERTOKENS).appIdentifierWithStorage,
                process.getProcess(), user2.id, null, "newpassword2");
        EmailPassword.updateUsersEmailOrPassword(
                StorageLayer.getAppIdentifierWithStorageAndUserIdMappingForUser(
                        process.getProcess(), new AppIdentifier(null, "a1"), user3.id,
                        UserIdType.SUPERTOKENS).appIdentifierWithStorage,
                process.getProcess(), user3.id, null, "newpassword3");

        {
            t1 = StorageLayer.getTenantIdentifierWithStorageAndUserIdMappingForUser(process.getProcess(), t1, user1.id,
                    UserIdType.SUPERTOKENS).tenantIdentifierWithStorage;
            UserInfo userInfo = EmailPassword.signIn(t1storage, process.getProcess(), "user@example.com", "newpassword1");
            assertEquals(user1.id, userInfo.id);
        }

        {
            t2 = StorageLayer.getTenantIdentifierWithStorageAndUserIdMappingForUser(process.getProcess(), t2, user2.id,
                    UserIdType.SUPERTOKENS).tenantIdentifierWithStorage;
            UserInfo userInfo = EmailPassword.signIn(t2storage, process.getProcess(), "user@example.com", "newpassword2");
            assertEquals(user2.id, userInfo.id);
        }

        {
            t3 = StorageLayer.getTenantIdentifierWithStorageAndUserIdMappingForUser(process.getProcess(), t3, user3.id,
                    UserIdType.SUPERTOKENS).tenantIdentifierWithStorage;
            UserInfo userInfo = EmailPassword.signIn(t3storage, process.getProcess(), "user@example.com", "newpassword3");
            assertEquals(user3.id, userInfo.id);
        }

        process.kill();
        assertNotNull(process.checkOrWaitForEvent(ProcessState.PROCESS_STATE.STOPPED));
    }
}
