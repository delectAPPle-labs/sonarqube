/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonar.server.settings.ws;

import java.net.HttpURLConnection;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.server.ws.WebService.Param;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.property.PropertyDbTester;
import org.sonar.db.property.PropertyDto;
import org.sonar.db.property.PropertyQuery;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.exceptions.BadRequestException;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.i18n.I18nRule;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.TestRequest;
import org.sonar.server.ws.TestResponse;
import org.sonar.server.ws.WsActionTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.sonar.db.component.ComponentTesting.newView;
import static org.sonar.db.property.PropertyTesting.newComponentPropertyDto;
import static org.sonar.db.property.PropertyTesting.newGlobalPropertyDto;

public class SetActionTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public UserSessionRule userSession = UserSessionRule.standalone()
    .setGlobalPermissions(GlobalPermissions.SYSTEM_ADMIN);
  @Rule
  public DbTester db = DbTester.create(System2.INSTANCE);
  PropertyDbTester propertyDb = new PropertyDbTester(db);
  ComponentDbTester componentDb = new ComponentDbTester(db);
  DbClient dbClient = db.getDbClient();
  DbSession dbSession = db.getSession();
  ComponentFinder componentFinder = new ComponentFinder(dbClient);

  I18nRule i18n = new I18nRule();
  PropertyDefinitions propertyDefinitions = new PropertyDefinitions();

  SetAction underTest = new SetAction(propertyDefinitions, i18n, dbClient, componentFinder, userSession);

  WsActionTester ws = new WsActionTester(underTest);

  @Test
  public void empty_204_response() {
    TestResponse result = ws.newRequest()
      .setParam("key", "my.key")
      .setParam("value", "my value")
      .execute();

    assertThat(result.getStatus()).isEqualTo(HttpURLConnection.HTTP_NO_CONTENT);
    assertThat(result.getInput()).isEmpty();
  }

  @Test
  public void persist_new_global_property() {
    callForGlobalProperty("my.key", "my value");

    assertGlobalProperty("my.key", "my value");
  }

  @Test
  public void update_existing_global_property() {
    propertyDb.insertProperty(newGlobalPropertyDto("my.key", "my value"));
    assertGlobalProperty("my.key", "my value");

    callForGlobalProperty("my.key", "my new value");

    assertGlobalProperty("my.key", "my new value");
  }

  @Test
  public void persist_new_project_property() {
    propertyDb.insertProperty(newGlobalPropertyDto("my.key", "my global value"));
    ComponentDto project = componentDb.insertProject();

    callForProjectPropertyByUuid("my.key", "my project value", project.uuid());

    assertGlobalProperty("my.key", "my global value");
    assertProjectProperty("my.key", "my project value", project.getId());
  }

  @Test
  public void persist_project_property_with_project_admin_permission() {
    ComponentDto project = componentDb.insertProject();
    userSession.anonymous().addProjectUuidPermissions(UserRole.ADMIN, project.uuid());

    callForProjectPropertyByUuid("my.key", "my value", project.uuid());

    assertProjectProperty("my.key", "my value", project.getId());
  }

  @Test
  public void update_existing_project_property() {
    propertyDb.insertProperty(newGlobalPropertyDto("my.key", "my global value"));
    ComponentDto project = componentDb.insertProject();
    propertyDb.insertProperty(newComponentPropertyDto("my.key", "my project value", project));
    assertProjectProperty("my.key", "my project value", project.getId());

    callForProjectPropertyByKey("my.key", "my new project value", project.key());

    assertProjectProperty("my.key", "my new project value", project.getId());
  }

  @Test
  public void user_property_is_not_updated() {
    propertyDb.insertProperty(newGlobalPropertyDto("my.key", "my user value").setUserId(42L));
    propertyDb.insertProperty(newGlobalPropertyDto("my.key", "my global value"));

    callForGlobalProperty("my.key", "my new global value");

    assertGlobalProperty("my.key", "my new global value");
    assertUserProperty("my.key", "my user value", 42L);
  }

  @Test
  public void persist_global_property_with_deprecated_key() {
    propertyDefinitions.addComponent(PropertyDefinition
      .builder("my.key")
      .deprecatedKey("my.old.key")
      .name("foo")
      .description("desc")
      .category("cat")
      .subCategory("subCat")
      .type(PropertyType.STRING)
      .defaultValue("default")
      .build());

    callForGlobalProperty("my.old.key", "My Value");

    assertGlobalProperty("my.key", "My Value");
  }

  @Test
  public void fail_when_no_key() {
    expectedException.expect(IllegalArgumentException.class);

    callForGlobalProperty(null, "my value");
  }

  @Test
  public void fail_when_empty_key_value() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Setting key is mandatory and must not be empty.");

    callForGlobalProperty("  ", "my value");
  }

  @Test
  public void fail_when_no_value() {
    expectedException.expect(IllegalArgumentException.class);

    callForGlobalProperty("my.key", null);
  }

  @Test
  public void fail_when_empty_value() {
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Setting value is mandatory and must not be empty.");

    callForGlobalProperty("my.key", "");
  }

  @Test
  public void fail_when_insufficient_privileges() {
    userSession.anonymous().setGlobalPermissions(GlobalPermissions.QUALITY_PROFILE_ADMIN);
    expectedException.expect(ForbiddenException.class);

    callForGlobalProperty("my.key", "my value");
  }

  @Test
  public void fail_when_data_and_type_do_not_match() {
    propertyDefinitions.addComponent(PropertyDefinition
      .builder("my.key")
      .name("foo")
      .description("desc")
      .category("cat")
      .subCategory("subCat")
      .type(PropertyType.INTEGER)
      .defaultValue("default")
      .build());
    i18n.put("property.error.notInteger", "Not an integer error message");
    expectedException.expect(BadRequestException.class);
    expectedException.expectMessage("Not an integer error message");

    callForGlobalProperty("my.key", "My Value");
  }

  @Test
  public void fail_when_data_and_type_do_not_match_with_unknown_error_key() {
    propertyDefinitions.addComponent(PropertyDefinition
      .builder("my.key")
      .name("foo")
      .description("desc")
      .category("cat")
      .subCategory("subCat")
      .type(PropertyType.INTEGER)
      .defaultValue("default")
      .build());
    expectedException.expect(BadRequestException.class);
    expectedException.expectMessage("Error when validating setting with key 'my.key' and value 'My Value'");

    callForGlobalProperty("my.key", "My Value");
  }

  @Test
  public void fail_when_global_with_property_only_on_projects() {
    propertyDefinitions.addComponent(PropertyDefinition
      .builder("my.key")
      .name("foo")
      .description("desc")
      .category("cat")
      .subCategory("subCat")
      .type(PropertyType.INTEGER)
      .defaultValue("default")
      .onlyOnQualifiers(Qualifiers.PROJECT)
      .build());
    expectedException.expect(BadRequestException.class);
    expectedException.expectMessage("Setting 'my.key' cannot be global");

    callForGlobalProperty("my.key", "42");
  }

  @Test
  public void fail_when_view_property_when_on_projects_only() {
    propertyDefinitions.addComponent(PropertyDefinition
      .builder("my.key")
      .name("foo")
      .description("desc")
      .category("cat")
      .subCategory("subCat")
      .type(PropertyType.STRING)
      .defaultValue("default")
      .onQualifiers(Qualifiers.PROJECT)
      .build());
    ComponentDto view = componentDb.insertComponent(newView("view-uuid"));
    i18n.put("qualifier." + Qualifiers.VIEW, "View");
    expectedException.expect(BadRequestException.class);
    expectedException.expectMessage("Setting 'my.key' cannot be set on a View");

    callForProjectPropertyByUuid("my.key", "My Value", view.uuid());
  }

  @Test
  public void definition() {
    WebService.Action definition = ws.getDef();

    assertThat(definition.key()).isEqualTo("set");
    assertThat(definition.isPost()).isTrue();
    assertThat(definition.since()).isEqualTo("6.1");
    assertThat(definition.params()).extracting(Param::key)
      .containsOnlyOnce("key", "value");
  }

  private void assertGlobalProperty(String key, String value) {
    PropertyDto result = dbClient.propertiesDao().selectGlobalProperty(key);

    assertThat(result)
      .extracting(PropertyDto::getKey, PropertyDto::getValue, PropertyDto::getResourceId)
      .containsExactly(key, value, null);
  }

  private void assertUserProperty(String key, String value, long userId) {
    List<PropertyDto> result = dbClient.propertiesDao().selectByQuery(PropertyQuery.builder().setKey(key).setUserId((int) userId).build(), dbSession);

    assertThat(result).hasSize(1)
      .extracting(PropertyDto::getKey, PropertyDto::getValue, PropertyDto::getUserId)
      .containsExactly(tuple(key, value, userId));
  }

  private void assertProjectProperty(String key, String value, long componentId) {
    PropertyDto result = dbClient.propertiesDao().selectProjectProperty(componentId, key);

    assertThat(result)
      .extracting(PropertyDto::getKey, PropertyDto::getValue, PropertyDto::getResourceId)
      .containsExactly(key, value, componentId);
  }

  private void callForGlobalProperty(@Nullable String key, @Nullable String value) {
    call(key, value, null, null);
  }

  private void callForProjectPropertyByUuid(@Nullable String key, @Nullable String value, @Nullable String componentUuid) {
    call(key, value, componentUuid, null);
  }

  private void callForProjectPropertyByKey(@Nullable String key, @Nullable String value, @Nullable String componentKey) {
    call(key, value, null, componentKey);
  }

  private void call(@Nullable String key, @Nullable String value, @Nullable String componentUuid, @Nullable String componentKey) {
    TestRequest request = ws.newRequest();

    if (key != null) {
      request.setParam("key", key);
    }

    if (value != null) {
      request.setParam("value", value);
    }

    if (componentUuid != null) {
      request.setParam("componentId", componentUuid);
    }

    if (componentKey != null) {
      request.setParam("componentKey", componentKey);
    }

    request.execute();
  }
}
