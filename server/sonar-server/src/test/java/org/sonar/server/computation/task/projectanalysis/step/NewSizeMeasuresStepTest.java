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

import java.util.Arrays;
import javax.annotation.Nullable;
import org.assertj.core.data.Offset;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.server.computation.task.projectanalysis.component.TreeRootHolderRule;
import org.sonar.server.computation.task.projectanalysis.duplication.DuplicationRepositoryRule;
import org.sonar.server.computation.task.projectanalysis.duplication.TextBlock;
import org.sonar.server.computation.task.projectanalysis.measure.MeasureRepositoryRule;
import org.sonar.server.computation.task.projectanalysis.measure.MeasureVariations;
import org.sonar.server.computation.task.projectanalysis.metric.MetricRepositoryRule;
import org.sonar.server.computation.task.projectanalysis.period.Period;
import org.sonar.server.computation.task.projectanalysis.period.PeriodsHolderRule;
import org.sonar.server.computation.task.projectanalysis.scm.Changeset;
import org.sonar.server.computation.task.projectanalysis.scm.ScmInfoRepositoryRule;

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.guava.api.Assertions.assertThat;
import static org.sonar.api.measures.CoreMetrics.NEW_BLOCKS_DUPLICATED;
import static org.sonar.api.measures.CoreMetrics.NEW_BLOCKS_DUPLICATED_KEY;
import static org.sonar.api.measures.CoreMetrics.NEW_DUPLICATED_LINES;
import static org.sonar.api.measures.CoreMetrics.NEW_DUPLICATED_LINES_DENSITY;
import static org.sonar.api.measures.CoreMetrics.NEW_DUPLICATED_LINES_DENSITY_KEY;
import static org.sonar.api.measures.CoreMetrics.NEW_DUPLICATED_LINES_KEY;
import static org.sonar.api.measures.CoreMetrics.NEW_LINES;
import static org.sonar.api.measures.CoreMetrics.NEW_LINES_KEY;
import static org.sonar.api.utils.DateUtils.parseDate;
import static org.sonar.server.computation.task.projectanalysis.component.Component.Type.DIRECTORY;
import static org.sonar.server.computation.task.projectanalysis.component.Component.Type.FILE;
import static org.sonar.server.computation.task.projectanalysis.component.Component.Type.MODULE;
import static org.sonar.server.computation.task.projectanalysis.component.Component.Type.PROJECT;
import static org.sonar.server.computation.task.projectanalysis.component.ReportComponent.builder;

public class NewSizeMeasuresStepTest {

  private static final Offset<Double> DEFAULT_OFFSET = Offset.offset(0.1d);

  private static final int ROOT_REF = 1;
  private static final int MODULE_REF = 12;
  private static final int SUB_MODULE_1_REF = 123;
  private static final int SUB_MODULE_2_REF = 126;
  private static final int DIRECTORY_REF = 1234;
  private static final int DIRECTORY_2_REF = 1235;
  private static final int FILE_1_REF = 12341;
  private static final int FILE_2_REF = 12342;
  private static final int FILE_3_REF = 1261;
  private static final int FILE_4_REF = 1262;
  private static final String SOME_FILE_KEY = "some file key";

  @Rule
  public TreeRootHolderRule treeRootHolder = new TreeRootHolderRule()
    .setRoot(
      builder(PROJECT, ROOT_REF)
        .addChildren(
          builder(MODULE, MODULE_REF)
            .addChildren(
              builder(MODULE, SUB_MODULE_1_REF)
                .addChildren(
                  builder(DIRECTORY, DIRECTORY_REF)
                    .addChildren(
                      builder(FILE, FILE_1_REF).build(),
                      builder(FILE, FILE_2_REF).build())
                    .build(),
                  builder(DIRECTORY, DIRECTORY_2_REF).build())
                .build(),
              builder(MODULE, SUB_MODULE_2_REF)
                .addChildren(
                  builder(FILE, FILE_3_REF).build(),
                  builder(FILE, FILE_4_REF).build())
                .build())
            .build())
        .build());

  @Rule
  public PeriodsHolderRule periodsHolder = new PeriodsHolderRule().setPeriods(
    new Period(2, "mode_p_1", null, parseDate("2009-12-25").getTime(), "u1"),
    new Period(5, "mode_p_5", null, parseDate("2011-02-18").getTime(), "u2"));

  @Rule
  public ScmInfoRepositoryRule scmInfoRepository = new ScmInfoRepositoryRule();

  @Rule
  public MetricRepositoryRule metricRepository = new MetricRepositoryRule()
    .add(NEW_LINES)
    .add(NEW_DUPLICATED_LINES)
    .add(NEW_DUPLICATED_LINES_DENSITY)
    .add(NEW_BLOCKS_DUPLICATED);

  @Rule
  public MeasureRepositoryRule measureRepository = MeasureRepositoryRule.create(treeRootHolder, metricRepository);

  @Rule
  public DuplicationRepositoryRule duplicationRepository = DuplicationRepositoryRule.create(treeRootHolder);

  NewSizeMeasuresStep underTest = new NewSizeMeasuresStep(treeRootHolder, periodsHolder, metricRepository, measureRepository, scmInfoRepository,
    duplicationRepository);

  @Test
  public void compute_new_lines() {
    setChangesets(FILE_1_REF, FILE_2_REF, FILE_4_REF);

    underTest.execute();

    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_1_REF, NEW_LINES_KEY, 11);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_2_REF, NEW_LINES_KEY, 11);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_3_REF, NEW_LINES_KEY, 0);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_4_REF, NEW_LINES_KEY, 11);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(DIRECTORY_REF, NEW_LINES_KEY, 22);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(DIRECTORY_2_REF, NEW_LINES_KEY, 0);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(SUB_MODULE_1_REF, NEW_LINES_KEY, 22);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(SUB_MODULE_2_REF, NEW_LINES_KEY, 11);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(MODULE_REF, NEW_LINES_KEY, 33);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(ROOT_REF, NEW_LINES_KEY, 33);
  }

  @Test
  public void compute_new_lines_with_no_changeset() {
    underTest.execute();

    assertComputedAndAggregatedToZeroInt(NEW_LINES_KEY);
  }

  @Test
  public void compute_new_lines_with_no_ncloc_data() {
    underTest.execute();

    assertComputedAndAggregatedToZeroInt(NEW_LINES_KEY);
  }

  @Test
  public void compute_duplicated_lines_counts_lines_from_original_and_InnerDuplicate_of_a_single_line() {
    duplicationRepository.addDuplication(FILE_1_REF, new TextBlock(1, 1), new TextBlock(2, 2));
    setChangesets(FILE_1_REF);

    underTest.execute();

    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_1_REF, NEW_DUPLICATED_LINES_KEY, 2d);
  }

  @Test
  public void compute_duplicated_lines_counts_lines_from_original_and_ignores_InProjectDuplicate() {
    TextBlock original = new TextBlock(1, 1);
    duplicationRepository.addDuplication(FILE_1_REF, original, FILE_2_REF, new TextBlock(2, 2));
    setChangesets(FILE_1_REF);

    underTest.execute();

    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_1_REF, NEW_DUPLICATED_LINES_KEY, 1d);
  }

  @Test
  public void compute_duplicated_lines_counts_lines_from_original_and_ignores_CrossProjectDuplicate() {
    TextBlock original = new TextBlock(1, 1);
    duplicationRepository.addDuplication(FILE_1_REF, original, SOME_FILE_KEY, new TextBlock(2, 2));
    setChangesets(FILE_1_REF);

    underTest.execute();

    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_1_REF, NEW_DUPLICATED_LINES_KEY, 1d);
  }

  @Test
  public void compute_duplicated_lines_counts_lines_from_original_and_InnerDuplicate() {
    TextBlock original = new TextBlock(1, 5);
    duplicationRepository.addDuplication(FILE_1_REF, original, new TextBlock(10, 11));
    setChangesets(FILE_1_REF);

    underTest.execute();

    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_1_REF, NEW_DUPLICATED_LINES_KEY, 6d);
  }

  @Test
  public void compute_duplicated_lines_counts_lines_from_original_and_InnerDuplicate_only_once() {
    TextBlock original = new TextBlock(1, 10);
    duplicationRepository.addDuplication(FILE_1_REF, original, new TextBlock(10, 11), new TextBlock(11, 12));
    duplicationRepository.addDuplication(FILE_1_REF, new TextBlock(2, 2), new TextBlock(4, 4));
    setChangesets(FILE_1_REF);

    underTest.execute();

    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_1_REF, NEW_DUPLICATED_LINES_KEY, 11d);
  }

  @Test
  public void compute_new_duplicated_lines_on_different_periods() {
    TextBlock original = new TextBlock(1, 1);
    duplicationRepository.addDuplication(FILE_1_REF, original, new TextBlock(2, 2));
    scmInfoRepository.setScmInfo(FILE_1_REF,
      Changeset.newChangesetBuilder().setDate(parseDate("2012-01-01").getTime()).setRevision("rev-1").build(),
      Changeset.newChangesetBuilder().setDate(parseDate("2011-01-01").getTime()).setRevision("rev-2").build());

    underTest.execute();

    assertRawMeasureValue(FILE_1_REF, NEW_DUPLICATED_LINES_KEY, 2d, 1d);
  }

  @Test
  public void compute_and_aggregate_duplicated_lines() {
    addDuplicatedBlock(FILE_1_REF, 2);
    addDuplicatedBlock(FILE_3_REF, 10);
    addDuplicatedBlock(FILE_4_REF, 12);
    setChangesets(FILE_1_REF);
    setChangesets(FILE_2_REF);
    setChangesets(FILE_3_REF);
    setChangesets(FILE_4_REF);

    underTest.execute();

    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_1_REF, NEW_DUPLICATED_LINES_KEY, 2d);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_2_REF, NEW_DUPLICATED_LINES_KEY, 0d);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_3_REF, NEW_DUPLICATED_LINES_KEY, 9d);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_4_REF, NEW_DUPLICATED_LINES_KEY, 11d);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(DIRECTORY_REF, NEW_DUPLICATED_LINES_KEY, 2d);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(DIRECTORY_2_REF, NEW_DUPLICATED_LINES_KEY, 0d);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(SUB_MODULE_1_REF, NEW_DUPLICATED_LINES_KEY, 2d);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(SUB_MODULE_2_REF, NEW_DUPLICATED_LINES_KEY, 20d);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(MODULE_REF, NEW_DUPLICATED_LINES_KEY, 22d);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(ROOT_REF, NEW_DUPLICATED_LINES_KEY, 22d);
  }

  @Test
  public void compute_and_aggregate_zero_duplicated_line_when_no_duplication() {
    setChangesets(FILE_1_REF);
    setChangesets(FILE_2_REF);
    setChangesets(FILE_3_REF);
    setChangesets(FILE_4_REF);

    underTest.execute();

    assertComputedAndAggregatedToZeroInt(NEW_DUPLICATED_LINES_KEY);
  }

  @Test
  public void compute_duplicated_blocks_one_for_original_one_for_each_InnerDuplicate() {
    TextBlock original = new TextBlock(1, 1);
    duplicationRepository.addDuplication(FILE_1_REF, original, new TextBlock(2, 2), new TextBlock(4, 4), new TextBlock(3, 4));
    setChangesets(FILE_1_REF);

    underTest.execute();

    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_1_REF, NEW_BLOCKS_DUPLICATED_KEY, 4);
  }

  @Test
  public void compute_duplicated_blocks_does_not_count_blocks_only_once_it_assumes_consistency_from_duplication_data() {
    duplicationRepository.addDuplication(FILE_1_REF, new TextBlock(1, 1), new TextBlock(4, 4));
    duplicationRepository.addDuplication(FILE_1_REF, new TextBlock(2, 2), new TextBlock(4, 4));
    setChangesets(FILE_1_REF);

    underTest.execute();

    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_1_REF, NEW_BLOCKS_DUPLICATED_KEY, 4);
  }

  @Test
  public void compute_duplicated_blocks_one_for_original_and_ignores_InProjectDuplicate() {
    duplicationRepository.addDuplication(FILE_1_REF, new TextBlock(1, 1), FILE_2_REF, new TextBlock(2, 2));
    setChangesets(FILE_1_REF);

    underTest.execute();

    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_1_REF, NEW_BLOCKS_DUPLICATED_KEY, 1);
  }

  @Test
  public void compute_duplicated_blocks_one_for_original_and_ignores_CrossProjectDuplicate() {
    duplicationRepository.addDuplication(FILE_1_REF, new TextBlock(1, 1), SOME_FILE_KEY, new TextBlock(2, 2));
    setChangesets(FILE_1_REF);

    underTest.execute();

    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_1_REF, NEW_BLOCKS_DUPLICATED_KEY, 1);
  }

  @Test
  public void compute_and_aggregate_duplicated_blocks_from_single_duplication() {
    addDuplicatedBlock(FILE_1_REF, 11);
    addDuplicatedBlock(FILE_2_REF, 2);
    addDuplicatedBlock(FILE_4_REF, 7);
    setChangesets(FILE_1_REF, FILE_2_REF, FILE_3_REF, FILE_4_REF);

    underTest.execute();

    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_1_REF, NEW_BLOCKS_DUPLICATED_KEY, 10);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_2_REF, NEW_BLOCKS_DUPLICATED_KEY, 2);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_3_REF, NEW_BLOCKS_DUPLICATED_KEY, 0);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_4_REF, NEW_BLOCKS_DUPLICATED_KEY, 6);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(DIRECTORY_REF, NEW_BLOCKS_DUPLICATED_KEY, 12);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(SUB_MODULE_1_REF, NEW_BLOCKS_DUPLICATED_KEY, 12);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(SUB_MODULE_2_REF, NEW_BLOCKS_DUPLICATED_KEY, 6);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(MODULE_REF, NEW_BLOCKS_DUPLICATED_KEY, 18);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(ROOT_REF, NEW_BLOCKS_DUPLICATED_KEY, 18);
  }

  @Test
  public void compute_and_aggregate_duplicated_blocks_to_zero_when_no_duplication() {
    setChangesets(FILE_1_REF, FILE_2_REF, FILE_3_REF, FILE_4_REF);

    underTest.execute();

    assertComputedAndAggregatedToZeroInt(NEW_BLOCKS_DUPLICATED_KEY);
  }

  @Test
  public void compute_new_duplicated_lines_density() {
    setChangesets(FILE_1_REF, FILE_2_REF, FILE_4_REF);
    addDuplicatedBlock(FILE_1_REF, 2);
    addDuplicatedBlock(FILE_3_REF, 10);
    addDuplicatedBlock(FILE_4_REF, 12);

    underTest.execute();

    assertRawMeasureValue(FILE_1_REF, NEW_DUPLICATED_LINES_DENSITY_KEY, 18.2d, null);
    assertRawMeasureValue(FILE_2_REF, NEW_DUPLICATED_LINES_DENSITY_KEY, 0d, null);
    assertNoRawMeasure(FILE_3_REF, NEW_DUPLICATED_LINES_DENSITY_KEY);
    assertRawMeasureValue(FILE_4_REF, NEW_DUPLICATED_LINES_DENSITY_KEY, 100d, null);
    assertRawMeasureValue(DIRECTORY_REF, NEW_DUPLICATED_LINES_DENSITY_KEY, 9.1d, null);
    assertNoRawMeasure(DIRECTORY_2_REF, NEW_DUPLICATED_LINES_DENSITY_KEY);
    assertRawMeasureValue(SUB_MODULE_1_REF, NEW_DUPLICATED_LINES_DENSITY_KEY, 9.1d, null);
    assertRawMeasureValue(SUB_MODULE_2_REF, NEW_DUPLICATED_LINES_DENSITY_KEY, 100d, null);
    assertRawMeasureValue(MODULE_REF, NEW_DUPLICATED_LINES_DENSITY_KEY, 39.4d, null);
    assertRawMeasureValue(ROOT_REF, NEW_DUPLICATED_LINES_DENSITY_KEY, 39.4d, null);
  }

  @Test
  public void compute_no_new_duplicated_lines_density_when_no_lines() {
    underTest.execute();

    assertNoRawMeasures(NEW_DUPLICATED_LINES_DENSITY_KEY);
  }

  /**
   * Adds duplication blocks of a single line (each line is specific to its block).
   *
   * This is a very simple use case, convenient for unit tests but more realistic and complex use cases must be tested separately.
   */
  private void addDuplicatedBlock(int fileRef, int blockCount) {
    checkArgument(blockCount > 1, "BlockCount can not be less than 2");
    TextBlock original = new TextBlock(1, 1);
    TextBlock[] duplicates = new TextBlock[blockCount - 1];
    for (int i = 2; i < blockCount + 1; i++) {
      duplicates[i - 2] = new TextBlock(i, i);
    }
    duplicationRepository.addDuplication(fileRef, original, duplicates);
  }

  private void setChangesets(int... componentRefs) {
    Arrays.stream(componentRefs)
      .forEach(componentRef -> scmInfoRepository.setScmInfo(componentRef,
        Changeset.newChangesetBuilder().setDate(parseDate("2011-01-01").getTime()).setRevision("rev-1").build(),
        Changeset.newChangesetBuilder().setDate(parseDate("2011-01-01").getTime()).setRevision("rev-1").build(),
        // line 3 is older, part of no period
        Changeset.newChangesetBuilder().setDate(parseDate("2007-01-15").getTime()).setRevision("rev-2").build(),
        Changeset.newChangesetBuilder().setDate(parseDate("2011-01-01").getTime()).setRevision("rev-1").build(),
        Changeset.newChangesetBuilder().setDate(parseDate("2011-01-01").getTime()).setRevision("rev-1").build(),
        Changeset.newChangesetBuilder().setDate(parseDate("2011-01-01").getTime()).setRevision("rev-1").build(),
        Changeset.newChangesetBuilder().setDate(parseDate("2011-01-01").getTime()).setRevision("rev-1").build(),
        Changeset.newChangesetBuilder().setDate(parseDate("2011-01-01").getTime()).setRevision("rev-1").build(),
        Changeset.newChangesetBuilder().setDate(parseDate("2011-01-01").getTime()).setRevision("rev-1").build(),
        Changeset.newChangesetBuilder().setDate(parseDate("2011-01-01").getTime()).setRevision("rev-1").build(),
        Changeset.newChangesetBuilder().setDate(parseDate("2011-01-01").getTime()).setRevision("rev-1").build(),
        Changeset.newChangesetBuilder().setDate(parseDate("2011-01-01").getTime()).setRevision("rev-1").build()));
  }

  private void assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(int componentRef, String metricKey, double period2Value) {
    assertRawMeasureValue(componentRef, metricKey, period2Value, 0d);
  }

  private void assertRawMeasureValue(int componentRef, String metricKey, double period2Value, @Nullable Double period5Value) {
    MeasureVariations variations = measureRepository.getAddedRawMeasure(componentRef, metricKey).get().getVariations();
    assertThat(variations.getVariation2()).isEqualTo(period2Value, DEFAULT_OFFSET);
    if (period5Value != null) {
      assertThat(variations.getVariation5()).isEqualTo(period5Value, DEFAULT_OFFSET);
    } else {
      assertThat(variations.hasVariation5()).isFalse();
    }
  }

  private void assertComputedAndAggregatedToZeroInt(String metricKey) {
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_1_REF, metricKey, 0);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_2_REF, metricKey, 0);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_3_REF, metricKey, 0);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(FILE_4_REF, metricKey, 0);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(DIRECTORY_REF, metricKey, 0);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(SUB_MODULE_1_REF, metricKey, 0);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(SUB_MODULE_2_REF, metricKey, 0);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(MODULE_REF, metricKey, 0);
    assertRawMeasureValueOnPeriod2AndZeroOnPeriod5(ROOT_REF, metricKey, 0);
  }

  private void assertNoRawMeasure(int componentRef, String metricKey) {
    assertThat(measureRepository.getAddedRawMeasure(componentRef, metricKey)).isAbsent();
  }

  private void assertNoRawMeasures(String metricKey) {
    assertThat(measureRepository.getAddedRawMeasures(FILE_1_REF).get(metricKey)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(FILE_2_REF).get(metricKey)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(FILE_3_REF).get(metricKey)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(FILE_4_REF).get(metricKey)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(DIRECTORY_REF).get(metricKey)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(SUB_MODULE_1_REF).get(metricKey)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(SUB_MODULE_2_REF).get(metricKey)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(MODULE_REF).get(metricKey)).isEmpty();
    assertThat(measureRepository.getAddedRawMeasures(ROOT_REF).get(metricKey)).isEmpty();
  }
}
