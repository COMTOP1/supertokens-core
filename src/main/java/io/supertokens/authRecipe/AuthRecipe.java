/*
 *    Copyright (c) 2021, VRAI Labs and/or its affiliates. All rights reserved.
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

package io.supertokens.authRecipe;

import io.supertokens.Main;
import io.supertokens.multitenancy.exception.BadPermissionException;
import io.supertokens.pluginInterface.RECIPE_ID;
import io.supertokens.pluginInterface.Storage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeStorage;
import io.supertokens.pluginInterface.authRecipe.AuthRecipeUserInfo;
import io.supertokens.pluginInterface.emailpassword.sqlStorage.EmailPasswordSQLStorage;
import io.supertokens.pluginInterface.emailverification.sqlStorage.EmailVerificationSQLStorage;
import io.supertokens.pluginInterface.exceptions.StorageQueryException;
import io.supertokens.pluginInterface.multitenancy.AppIdentifier;
import io.supertokens.pluginInterface.multitenancy.AppIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.TenantIdentifierWithStorage;
import io.supertokens.pluginInterface.multitenancy.exceptions.TenantOrAppNotFoundException;
import io.supertokens.pluginInterface.passwordless.sqlStorage.PasswordlessSQLStorage;
import io.supertokens.pluginInterface.session.SessionStorage;
import io.supertokens.pluginInterface.thirdparty.sqlStorage.ThirdPartySQLStorage;
import io.supertokens.pluginInterface.useridmapping.UserIdMapping;
import io.supertokens.pluginInterface.usermetadata.sqlStorage.UserMetadataSQLStorage;
import io.supertokens.pluginInterface.userroles.sqlStorage.UserRolesSQLStorage;
import io.supertokens.storageLayer.StorageLayer;
import io.supertokens.useridmapping.UserIdType;
import org.jetbrains.annotations.TestOnly;

import javax.annotation.Nullable;

/*This files contains functions that are common for all auth recipes*/

public class AuthRecipe {

    public static final int USER_PAGINATION_LIMIT = 500;

    public static long getUsersCountForTenant(TenantIdentifierWithStorage tenantIdentifier,
                                              RECIPE_ID[] includeRecipeIds)
            throws StorageQueryException,
            TenantOrAppNotFoundException, BadPermissionException {
            return ((AuthRecipeStorage) tenantIdentifier.getStorage()).getUsersCount(
                    tenantIdentifier, includeRecipeIds);
    }

    public static long getUsersCountAcrossAllTenants(AppIdentifierWithStorage appIdentifierWithStorage,
                                              RECIPE_ID[] includeRecipeIds)
            throws StorageQueryException,
            TenantOrAppNotFoundException, BadPermissionException {
        long count = 0;

        for (Storage storage : appIdentifierWithStorage.getStorages()) {
            count += ((AuthRecipeStorage) storage).getUsersCount(
                    appIdentifierWithStorage, includeRecipeIds);
        }

        return count;
    }


    @TestOnly
    public static long getUsersCount(Main main,
                                     RECIPE_ID[] includeRecipeIds) throws StorageQueryException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return getUsersCountForTenant(new TenantIdentifierWithStorage(
                    null, null, null, storage),
                    includeRecipeIds);
        } catch (TenantOrAppNotFoundException | BadPermissionException e) {
            throw new IllegalStateException(e);
        }
    }

    public static UserPaginationContainer getUsers(TenantIdentifierWithStorage tenantIdentifierWithStorage,
                                                   Integer limit, String timeJoinedOrder,
                                                   @Nullable String paginationToken,
                                                   @Nullable RECIPE_ID[] includeRecipeIds)
            throws StorageQueryException, UserPaginationToken.InvalidTokenException, TenantOrAppNotFoundException {
        AuthRecipeUserInfo[] users;
        if (paginationToken == null) {
            users = ((AuthRecipeStorage) tenantIdentifierWithStorage.getStorage())
                    .getUsers(tenantIdentifierWithStorage, limit + 1, timeJoinedOrder, includeRecipeIds, null,
                            null);
        } else {
            UserPaginationToken tokenInfo = UserPaginationToken.extractTokenInfo(paginationToken);
            users = ((AuthRecipeStorage) tenantIdentifierWithStorage.getStorage())
                    .getUsers(tenantIdentifierWithStorage, limit + 1, timeJoinedOrder, includeRecipeIds,
                            tokenInfo.userId, tokenInfo.timeJoined);
        }
        String nextPaginationToken = null;
        int maxLoop = users.length;
        if (users.length == limit + 1) {
            maxLoop = limit;
            nextPaginationToken = new UserPaginationToken(users[limit].id, users[limit].timeJoined).generateToken();
        }
        AuthRecipeUserInfo[] resultUsers = new AuthRecipeUserInfo[maxLoop];
        System.arraycopy(users, 0, resultUsers, 0, maxLoop);
        return new UserPaginationContainer(resultUsers, nextPaginationToken);
    }

    @TestOnly
    public static UserPaginationContainer getUsers(Main main,
                                                   Integer limit, String timeJoinedOrder,
                                                   @Nullable String paginationToken,
                                                   @Nullable RECIPE_ID[] includeRecipeIds)
            throws StorageQueryException, UserPaginationToken.InvalidTokenException {
        try {
            Storage storage = StorageLayer.getStorage(main);
            return getUsers(new TenantIdentifierWithStorage(
                    null, null, null, storage),
                    limit, timeJoinedOrder, paginationToken, includeRecipeIds);
        } catch (TenantOrAppNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void deleteUser(AppIdentifierWithStorage appIdentifierWithStorage, String userId,
                                  UserIdMapping userIdMapping)
            throws StorageQueryException {
        // We clean up the user last so that if anything before that throws an error, then that will throw a 500 to the
        // developer. In this case, they expect that the user has not been deleted (which will be true). This is as
        // opposed to deleting the user first, in which case if something later throws an error, then the user has
        // actually been deleted already (which is not expected by the dev)

        // For things created after the intial cleanup and before finishing the operation:
        // - session: the session will expire anyway
        // - email verification: email verification tokens can be created for any userId anyway

        // If userId mapping exists then delete entries with superTokensUserId from auth related tables and
        // externalUserid from non-auth tables
        if (userIdMapping != null) {
            // We check if the mapped externalId is another SuperTokens UserId, this could come up when migrating
            // recipes.
            // in reference to
            // https://docs.google.com/spreadsheets/d/17hYV32B0aDCeLnSxbZhfRN2Y9b0LC2xUF44vV88RNAA/edit?usp=sharing
            // we want to check which state the db is in
            if (((AuthRecipeStorage) appIdentifierWithStorage.getStorage())
                    .doesUserIdExist(appIdentifierWithStorage, userIdMapping.externalUserId)) {
                // db is in state A4
                // delete only from auth tables
                deleteAuthRecipeUser(appIdentifierWithStorage, userId);
            } else {
                // db is in state A3
                // delete user from non-auth tables with externalUserId
                deleteNonAuthRecipeUser(appIdentifierWithStorage, userIdMapping.externalUserId);
                // delete user from auth tables with superTokensUserId
                deleteAuthRecipeUser(appIdentifierWithStorage, userIdMapping.superTokensUserId);
            }
        } else {
            deleteNonAuthRecipeUser(appIdentifierWithStorage, userId);
            deleteAuthRecipeUser(appIdentifierWithStorage, userId);
        }
    }

    @TestOnly
    public static void deleteUser(Main main, String userId)
            throws StorageQueryException {
        Storage storage = StorageLayer.getStorage(main);
        AppIdentifierWithStorage appIdentifier = new AppIdentifierWithStorage(
                null, null, storage);
        UserIdMapping mapping = io.supertokens.useridmapping.UserIdMapping.getUserIdMapping(appIdentifier,
                userId, UserIdType.ANY);

        deleteUser(appIdentifier, userId, mapping);
    }

    private static void deleteNonAuthRecipeUser(AppIdentifierWithStorage appIdentifierWithStorage, String userId)
            throws StorageQueryException {
        ((UserMetadataSQLStorage) appIdentifierWithStorage.getStorage())
                .deleteUserMetadata(appIdentifierWithStorage, userId);
        ((SessionStorage) appIdentifierWithStorage.getStorage())
                .deleteSessionsOfUser(appIdentifierWithStorage, userId);
        ((EmailVerificationSQLStorage) appIdentifierWithStorage.getStorage())
                .deleteEmailVerificationUserInfo(appIdentifierWithStorage, userId);
        ((UserRolesSQLStorage) appIdentifierWithStorage.getStorage())
                .deleteAllRolesForUser(appIdentifierWithStorage, userId);
    }

    private static void deleteAuthRecipeUser(AppIdentifierWithStorage appIdentifierWithStorage, String userId)
            throws StorageQueryException {
        // auth recipe deletions here only
        Storage storage = appIdentifierWithStorage.getStorage();
        ((EmailPasswordSQLStorage) storage).deleteEmailPasswordUser(appIdentifierWithStorage, userId);
        ((ThirdPartySQLStorage) storage).deleteThirdPartyUser(appIdentifierWithStorage, userId);
        ((PasswordlessSQLStorage) storage).deletePasswordlessUser(appIdentifierWithStorage, userId);
    }
}
