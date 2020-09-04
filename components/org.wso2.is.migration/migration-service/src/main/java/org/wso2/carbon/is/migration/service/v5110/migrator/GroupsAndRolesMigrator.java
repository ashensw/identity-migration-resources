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
import org.wso2.carbon.identity.core.migrate.MigrationClientException;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.role.mgt.core.IdentityRoleManagementException;
import org.wso2.carbon.is.migration.internal.ISMigrationServiceDataHolder;
import org.wso2.carbon.is.migration.service.Migrator;
import org.wso2.carbon.is.migration.service.v5110.bean.RoleInfo;
import org.wso2.carbon.is.migration.service.v5110.dao.RoleDAO;
import org.wso2.carbon.is.migration.util.Schema;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Groups And Roles separation migrator.
 */
public class GroupsAndRolesMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(GroupsAndRolesMigrator.class);

    @Override
    public void dryRun() throws MigrationClientException {

        log.info("Dry run capability not implemented in {} migrator.", this.getClass().getName());
    }

    @Override
    public void migrate() throws MigrationClientException {

        List<RoleInfo> externalRoles;
        try (Connection connection = getDataSource(Schema.UM.getName()).getConnection()) {

            try {
                // Retrieve external role data which has permissions assigned.
                externalRoles = RoleDAO.getInstance().getExternalRoleData(connection);

                for (RoleInfo roleInfo : externalRoles) {
                    // Create a new internal role corresponding to the external role.
                    ISMigrationServiceDataHolder.getRoleManagementService()
                            .addRole(roleInfo.getInternalRoleName(), null, null, null,
                                    IdentityTenantUtil.getTenantDomain(roleInfo.getTenantID()));

                    // Assign the external role to the newly created role.
                    RoleDAO.getInstance().updateGroupListOfRole(connection, roleInfo.getInternalRoleName(),
                            roleInfo.getDomainQualifiedRoleName(), roleInfo.getTenantID());

                    // Transfer permissions to the newly created role.
                    RoleDAO.getInstance().transferPermissionsOfRole(connection, roleInfo);
                }
            } catch (SQLException | IdentityRoleManagementException e) {
                connection.rollback();
                String error = "SQL error while migrating external role data.";
                throw new MigrationClientException(error, e);
            }

        } catch (SQLException e) {
            String error = "SQL error while migrating external role data.";
            throw new MigrationClientException(error, e);
        }
    }
}
