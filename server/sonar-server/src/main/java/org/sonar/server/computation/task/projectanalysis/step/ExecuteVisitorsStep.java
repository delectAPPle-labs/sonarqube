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
package org.sonar.server.computation.task.projectanalysis.step;

import java.util.List;
import java.util.Map;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.ce.log.CeLogging;
import org.sonar.server.computation.task.projectanalysis.component.ComponentVisitor;
import org.sonar.server.computation.task.projectanalysis.component.TreeRootHolder;
import org.sonar.server.computation.task.projectanalysis.component.VisitorsCrawler;
import org.sonar.server.computation.task.step.ComputationStep;

public class ExecuteVisitorsStep implements ComputationStep {

  private static final Logger LOGGER = Loggers.get(ExecuteVisitorsStep.class);

  private final TreeRootHolder treeRootHolder;
  private final List<ComponentVisitor> visitors;
  private final CeLogging ceLogging;

  public ExecuteVisitorsStep(TreeRootHolder treeRootHolder, List<ComponentVisitor> visitors, CeLogging ceLogging) {
    this.treeRootHolder = treeRootHolder;
    this.visitors = visitors;
    this.ceLogging = ceLogging;
  }

  @Override
  public String getDescription() {
    return "Execute component visitors";
  }

  @Override
  public void execute() {
    VisitorsCrawler visitorsCrawler = new VisitorsCrawler(visitors);
    visitorsCrawler.visit(treeRootHolder.getRoot());
    ceLogging.logCeActivity(LOGGER, () -> logVisitorExecutionDurations(visitors, visitorsCrawler));
  }

  private static void logVisitorExecutionDurations(List<ComponentVisitor> visitors, VisitorsCrawler visitorsCrawler) {
    LOGGER.info("  Execution time for each component visitor:");
    Map<ComponentVisitor, Long> cumulativeDurations = visitorsCrawler.getCumulativeDurations();
    for (ComponentVisitor visitor : visitors) {
      LOGGER.info("  - {} | time={}ms", visitor.getClass().getSimpleName(), cumulativeDurations.get(visitor));
    }
  }
}
