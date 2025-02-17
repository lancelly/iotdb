/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.auth.role;

import org.apache.iotdb.commons.auth.AuthException;
import org.apache.iotdb.commons.auth.entity.PathPrivilege;
import org.apache.iotdb.commons.auth.entity.PrivilegeType;
import org.apache.iotdb.commons.auth.entity.Role;
import org.apache.iotdb.commons.auth.role.LocalFileRoleManager;
import org.apache.iotdb.commons.exception.IllegalPathException;
import org.apache.iotdb.commons.path.PartialPath;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.db.utils.constant.TestConstant;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LocalFileRoleManagerTest {

  private File testFolder;
  private LocalFileRoleManager manager;

  @Before
  public void setUp() {
    EnvironmentUtils.envSetUp();
    testFolder = new File(TestConstant.BASE_OUTPUT_PATH.concat("test"));
    testFolder.mkdirs();
    manager = new LocalFileRoleManager(testFolder.getPath());
  }

  @After
  public void tearDown() throws Exception {
    FileUtils.deleteDirectory(testFolder);
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void test() throws AuthException, IllegalPathException {
    Role[] roles = new Role[4];
    for (int i = 0; i < roles.length; i++) {
      roles[i] = new Role("role" + i);
      for (int j = 0; j <= i; j++) {
        PathPrivilege pathPrivilege = new PathPrivilege(new PartialPath("root.a.b.c" + j));
        pathPrivilege.getPrivileges().add(j);
        roles[i].getPathPrivilegeList().add(pathPrivilege);
        roles[i].getSysPrivilege().add(j + 4);
      }
    }

    // create
    Role role = manager.getRole(roles[0].getName());
    assertNull(role);
    for (Role role1 : roles) {
      assertTrue(manager.createRole(role1.getName()));
    }
    for (Role role1 : roles) {
      role = manager.getRole(role1.getName());
      assertEquals(role1.getName(), role.getName());
    }

    assertFalse(manager.createRole(roles[0].getName()));
    boolean caught = false;
    try {
      manager.createRole("too");
    } catch (AuthException e) {
      caught = true;
    }
    assertTrue(caught);
    try {
      manager.createRole("rolename ");
    } catch (AuthException e) {
      caught = true;
    }
    assertTrue(caught);

    // delete
    assertFalse(manager.deleteRole("not a role"));
    assertTrue(manager.deleteRole(roles[roles.length - 1].getName()));
    assertNull(manager.getRole(roles[roles.length - 1].getName()));
    assertFalse(manager.deleteRole(roles[roles.length - 1].getName()));

    // grant privilege
    role = manager.getRole(roles[0].getName());
    PartialPath path = new PartialPath("root.a.b.c");
    int privilegeId = 0;
    assertFalse(role.hasPrivilegeToRevoke(path, privilegeId));
    manager.grantPrivilegeToRole(role.getName(), path, privilegeId, false);
    manager.grantPrivilegeToRole(role.getName(), path, privilegeId + 1, false);
    // grant again will success
    manager.grantPrivilegeToRole(role.getName(), path, privilegeId, false);
    role = manager.getRole(roles[0].getName());
    assertTrue(role.hasPrivilegeToRevoke(path, privilegeId));
    manager.grantPrivilegeToRole(role.getName(), null, PrivilegeType.MAINTAIN.ordinal(), true);
    manager.grantPrivilegeToRole(role.getName(), null, PrivilegeType.MAINTAIN.ordinal(), true);
    caught = false;
    try {
      manager.grantPrivilegeToRole("not a role", path, privilegeId, false);
    } catch (AuthException e) {
      caught = true;
    }
    assertTrue(caught);

    // revoke privilege
    role = manager.getRole(roles[0].getName());
    assertTrue(manager.revokePrivilegeFromRole(role.getName(), path, privilegeId));
    assertFalse(manager.revokePrivilegeFromRole(role.getName(), path, privilegeId));
    assertFalse(
        manager.revokePrivilegeFromRole(role.getName(), null, PrivilegeType.USE_PIPE.ordinal()));
    assertTrue(
        manager.revokePrivilegeFromRole(role.getName(), null, PrivilegeType.MAINTAIN.ordinal()));
    assertEquals(manager.getRole(role.getName()).getSysPriGrantOpt().size(), 0);
    caught = false;
    try {
      manager.revokePrivilegeFromRole("not a role", path, privilegeId);
    } catch (AuthException e) {
      caught = true;
    }
    assertTrue(caught);

    // list roles
    List<String> rolenames = manager.listAllRoles();
    rolenames.sort(null);
    for (int i = 0; i < roles.length - 1; i++) {
      assertEquals(roles[i].getName(), rolenames.get(i));
    }
  }
}
