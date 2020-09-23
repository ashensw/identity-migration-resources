/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.is.migration.service.v5110.migrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.is.migration.internal.ISMigrationServiceDataHolder;
import org.wso2.carbon.is.migration.service.Migrator;
import org.wso2.carbon.is.migration.service.v5110.bean.RoleInfo;
import org.wso2.carbon.is.migration.service.v5110.dao.RoleDAO;
import org.wso2.carbon.is.migration.util.Constant;
import org.wso2.carbon.is.migration.util.ReportUtil;
import org.wso2.carbon.is.migration.util.Schema;
import org.wso2.carbon.is.migration.util.Utility;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.UserCoreConstants;
import org.wso2.carbon.user.core.common.AbstractUserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.wso2.carbon.is.migration.util.Constant.REPORT_PATH;

/**
 * Groups And Roles separation migrator.
 */
public class GroupsAndRolesMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(GroupsAndRolesMigrator.class);

    private RealmService realmService = ISMigrationServiceDataHolder.getRealmService();
    private ReportUtil reportUtil;

    @Override
    public void dryRun() throws MigrationClientException {

        log.info(Constant.MIGRATION_LOG + "Executing dry run for {}", this.getClass().getName());
        Properties migrationProperties = getMigratorConfig().getParameters();
        String reportPath = (String) migrationProperties.get(REPORT_PATH);
        try {
            reportUtil = new ReportUtil(reportPath);
            reportUtil.writeMessage("\n--- Summery of the report ---\n");
            reportUtil.writeMessage("External roles data to be migrated..\n");
            reportUtil.writeMessage(
                    String.format("%20s | %20s | %20s", "External role name", "Domain " + "name", "Tenant ID"));

            log.info(Constant.MIGRATION_LOG + "Started the dry run of groups and roles migration.");
            List<RoleInfo> externalRoles;
            try (Connection connection = getDataSource(Schema.UM.getName()).getConnection()) {
                try {
                    // Super tenant dry run.
                    // Retrieve external role data of super tenant which has permissions assigned.
                    externalRoles = RoleDAO.getInstance()
                            .getExternalRoleData(connection, MultitenantConstants.SUPER_TENANT_ID);
                    // Migrating super tenant groups.
                    for (RoleInfo roleInfo : externalRoles) {
                        reportUtil.writeMessage(
                                String.format("%20s | %20s | %20d ", roleInfo.getRoleName(), roleInfo.getDomainName(),
                                        roleInfo.getTenantID()));
                    }

                    // Tenant dry run.
                    Set<Tenant> tenants;
                    tenants = Utility.getTenants();
                    for (Tenant tenant : tenants) {
                        log.info(Constant.MIGRATION_LOG + "Started the dry run for tenant: " + tenant.getDomain());
                        if (isIgnoreForInactiveTenants() && !tenant.isActive()) {
                            log.info(Constant.MIGRATION_LOG + "Tenant " + tenant.getDomain()
                                    + " is inactive. Skipping the dry run.");
                            continue;
                        }
                        // Retrieve external role data of tenants which has permissions assigned.
                        externalRoles = RoleDAO.getInstance().getExternalRoleData(connection, tenant.getId());
                        for (RoleInfo roleInfo : externalRoles) {
                            reportUtil.writeMessage(String.format("%20s | %20s | %20d ", roleInfo.getRoleName(),
                                    roleInfo.getDomainName(), roleInfo.getTenantID()));
                        }
                    }
                    reportUtil.commit();
                } catch (SQLException | MigrationClientException e) {
                    connection.rollback();
                    String message = Constant.MIGRATION_LOG + "Error occurred while running the dry run.";
                    if (isContinueOnError()) {
                        log.error(message, e);
                    } else {
                        throw new MigrationClientException(message, e);
                    }
                }
            } catch (SQLException e) {
                String message = Constant.MIGRATION_LOG + "Error occurred while running the dry run.";
                if (isContinueOnError()) {
                    log.error(message, e);
                } else {
                    throw new MigrationClientException(message, e);
                }
            }

        } catch (IOException ex) {
            throw new MigrationClientException("Error while writing the dry run report.", ex);
        }
    }

    @Override
    public void migrate() throws MigrationClientException {

        log.info(Constant.MIGRATION_LOG + "Started the groups and roles migration.");
        List<RoleInfo> externalRoles;
        try (Connection connection = getDataSource(Schema.UM.getName()).getConnection()) {
            try {
                // Super tenant migration.
                // Retrieve external role data of super tenant which has permissions assigned.
                externalRoles = RoleDAO.getInstance()
                        .getExternalRoleData(connection, MultitenantConstants.SUPER_TENANT_ID);
                UserRealm userRealm = realmService.getTenantUserRealm(MultitenantConstants.SUPER_TENANT_ID);
                UserStoreManager userStoreManager = userRealm.getUserStoreManager();
                // Migrating super tenant groups.
                for (RoleInfo roleInfo : externalRoles) {
                    // Create a new internal role corresponding to the external role.
                    userStoreManager.addRole(
                            UserCoreConstants.INTERNAL_DOMAIN + UserCoreConstants.DOMAIN_SEPARATOR + roleInfo
                                    .getInternalRoleName(), null, null);
                    // Assign the external role to the newly created role.
                    ((AbstractUserStoreManager) userStoreManager)
                            .updateGroupListOfHybridRole(roleInfo.getInternalRoleName(), null,
                                    new String[] { roleInfo.getDomainQualifiedRoleName() });
                    // Transfer permissions to the newly created role.
                    RoleDAO.getInstance().transferPermissionsOfRole(connection, roleInfo);
                }

                // Tenant migration.
                Set<Tenant> tenants;
                tenants = Utility.getTenants();
                for (Tenant tenant : tenants) {
                    log.info(Constant.MIGRATION_LOG + "Started to migrate external role permissions for tenant: "
                            + tenant.getDomain());
                    if (isIgnoreForInactiveTenants() && !tenant.isActive()) {
                        log.info(Constant.MIGRATION_LOG + "Tenant " + tenant.getDomain()
                                + " is inactive. Skipping external role permissions migration. ");
                        continue;
                    }
                    // Retrieve external role data of tenants which has permissions assigned.
                    externalRoles = RoleDAO.getInstance().getExternalRoleData(connection, tenant.getId());
                    userRealm = realmService.getTenantUserRealm(tenant.getId());
                    userStoreManager = userRealm.getUserStoreManager();

                    for (RoleInfo roleInfo : externalRoles) {
                        // Create a new internal role corresponding to the external role.
                        userStoreManager.addRole(
                                UserCoreConstants.INTERNAL_DOMAIN + UserCoreConstants.DOMAIN_SEPARATOR + roleInfo
                                        .getInternalRoleName(), null, null);
                        // Assign the external role to the newly created role.
                        ((AbstractUserStoreManager) userStoreManager)
                                .updateGroupListOfHybridRole(roleInfo.getInternalRoleName(), null,
                                        new String[] { roleInfo.getDomainQualifiedRoleName() });
                        // Transfer permissions to the newly created role.
                        RoleDAO.getInstance().transferPermissionsOfRole(connection, roleInfo);
                    }
                }
            } catch (SQLException | UserStoreException | MigrationClientException e) {
                connection.rollback();
                String message = Constant.MIGRATION_LOG + "Error while migrating external role permissions.";
                if (isContinueOnError()) {
                    log.error(message, e);
                } else {
                    throw new MigrationClientException(message, e);
                }
            }
        } catch (SQLException e) {
            String message = Constant.MIGRATION_LOG + "Error while migrating external role permissions.";
            if (isContinueOnError()) {
                log.error(message, e);
            } else {
                throw new MigrationClientException(message, e);
            }
        }
    }
}
