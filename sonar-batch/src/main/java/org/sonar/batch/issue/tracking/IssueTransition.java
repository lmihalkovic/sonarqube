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
package org.sonar.batch.issue.tracking;

import org.sonar.batch.util.ProgressReport;
import org.sonar.batch.issue.IssueTransformer;
import org.sonar.api.batch.BatchSide;
import org.sonar.api.resources.Project;
import org.sonar.batch.index.BatchComponent;
import org.sonar.batch.index.BatchComponentCache;
import org.sonar.batch.issue.IssueCache;
import org.sonar.batch.protocol.output.BatchReport;
import org.sonar.batch.protocol.output.BatchReportReader;
import org.sonar.batch.report.ReportPublisher;
import org.sonar.core.util.CloseableIterator;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BatchSide
public class IssueTransition {
  private final IssueCache issueCache;
  private final BatchComponentCache componentCache;
  private final ReportPublisher reportPublisher;
  private final Date analysisDate;
  @Nullable
  private final LocalIssueTracking localIssueTracking;

  public IssueTransition(BatchComponentCache componentCache, IssueCache issueCache, ReportPublisher reportPublisher,
    @Nullable LocalIssueTracking localIssueTracking) {
    this.componentCache = componentCache;
    this.issueCache = issueCache;
    this.reportPublisher = reportPublisher;
    this.localIssueTracking = localIssueTracking;
    this.analysisDate = ((Project) componentCache.getRoot().resource()).getAnalysisDate();
  }

  public IssueTransition(BatchComponentCache componentCache, IssueCache issueCache, ReportPublisher reportPublisher) {
    this(componentCache, issueCache, reportPublisher, null);
  }

  public void execute() {
    if (localIssueTracking != null) {
      localIssueTracking.init();
    }

    BatchReportReader reader = new BatchReportReader(reportPublisher.getReportDir());
    int nbComponents = componentCache.all().size();

    if (nbComponents == 0) {
      return;
    }

    ProgressReport progressReport = new ProgressReport("issue-tracking-report", TimeUnit.SECONDS.toMillis(10));
    progressReport.start("Performing issue tracking");
    int count = 0;

    try {
      for (BatchComponent component : componentCache.all()) {
        trackIssues(reader, component);
        count++;
        progressReport.message(count + "/" + nbComponents + " components tracked");
      }
    } finally {
      progressReport.stop(count + "/" + nbComponents + " components tracked");
    }
  }

  public void trackIssues(BatchReportReader reader, BatchComponent component) {
    // raw issues = all the issues created by rule engines during this module scan and not excluded by filters
    List<BatchReport.Issue> rawIssues = new LinkedList<>();
    try (CloseableIterator<BatchReport.Issue> it = reader.readComponentIssues(component.batchId())) {
      while (it.hasNext()) {
        rawIssues.add(it.next());
      }
    } catch (Exception e) {
      throw new IllegalStateException("Can't read issues for " + component.key(), e);
    }

    List<TrackedIssue> trackedIssues;
    if (localIssueTracking != null) {
      trackedIssues = localIssueTracking.trackIssues(component, rawIssues, analysisDate);
    } else {
      trackedIssues = doTransition(rawIssues, component);
    }

    for (TrackedIssue issue : trackedIssues) {
      issueCache.put(issue);
    }
  }

  private static List<TrackedIssue> doTransition(List<BatchReport.Issue> rawIssues, BatchComponent component) {
    List<TrackedIssue> issues = new ArrayList<>(rawIssues.size());

    for (BatchReport.Issue issue : rawIssues) {
      issues.add(IssueTransformer.toTrackedIssue(component, issue, null));
    }

    return issues;
  }

}
