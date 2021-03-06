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
package org.sonar.db.version.v53;

public class Component {

  private long id;
  private String uuid;
  private String projectUuid;
  private boolean enabled;

  public long getId() {
    return id;
  }

  public Component setId(long id) {
    this.id = id;
    return this;
  }

  public String getUuid() {
    return uuid;
  }

  public Component setUuid(String uuid) {
    this.uuid = uuid;
    return this;
  }

  public String getProjectUuid() {
    return projectUuid;
  }

  public Component setProjectUuid(String projectUuid) {
    this.projectUuid = projectUuid;
    return this;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Component setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }
}
