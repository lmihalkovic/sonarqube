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
package org.sonar.batch.repository;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sonar.api.batch.bootstrap.ProjectKey;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.batch.analysis.AnalysisProperties;
import org.sonar.batch.analysis.DefaultAnalysisMode;
import org.sonar.batch.rule.ModuleQProfiles;
import org.sonarqube.ws.QualityProfiles.SearchWsResponse.QualityProfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class QualityProfileProviderTest {

  @Rule
  public LogTester logTester = new LogTester();

  private QualityProfileProvider qualityProfileProvider;

  @Mock
  private QualityProfileLoader loader;
  @Mock
  private DefaultAnalysisMode mode;
  @Mock
  private AnalysisProperties props;
  @Mock
  private ProjectKey key;
  @Mock
  private ProjectRepositories projectRepo;

  private List<QualityProfile> response;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    qualityProfileProvider = new QualityProfileProvider();

    when(key.get()).thenReturn("project");
    when(projectRepo.exists()).thenReturn(true);

    response = new ArrayList<>(1);
    response.add(QualityProfile.newBuilder().setKey("profile").setName("profile").setLanguage("lang").build());
  }

  @Test
  public void testProvide() {
    when(mode.isNotAssociated()).thenReturn(false);
    when(loader.load(eq("project"), isNull(String.class), any(MutableBoolean.class))).thenReturn(response);
    ModuleQProfiles qps = qualityProfileProvider.provide(key, loader, projectRepo, props, mode);
    assertResponse(qps);

    verify(loader).load(eq("project"), isNull(String.class), any(MutableBoolean.class));
    verifyNoMoreInteractions(loader);
  }

  @Test
  public void testNonAssociated() {
    when(mode.isNotAssociated()).thenReturn(true);
    when(loader.loadDefault(anyString(), any(MutableBoolean.class))).thenReturn(response);
    ModuleQProfiles qps = qualityProfileProvider.provide(key, loader, projectRepo, props, mode);
    assertResponse(qps);

    verify(loader).loadDefault(anyString(), any(MutableBoolean.class));
    verifyNoMoreInteractions(loader);
  }

  @Test
  public void testProjectDoesntExist() {
    when(mode.isNotAssociated()).thenReturn(false);
    when(projectRepo.exists()).thenReturn(false);
    when(loader.loadDefault(anyString(), any(MutableBoolean.class))).thenReturn(response);
    ModuleQProfiles qps = qualityProfileProvider.provide(key, loader, projectRepo, props, mode);
    assertResponse(qps);

    verify(loader).loadDefault(anyString(), any(MutableBoolean.class));
    verifyNoMoreInteractions(loader);
  }

  @Test
  public void testProfileProp() {
    when(mode.isNotAssociated()).thenReturn(false);
    when(loader.load(eq("project"), eq("custom"), any(MutableBoolean.class))).thenReturn(response);
    when(props.property(ModuleQProfiles.SONAR_PROFILE_PROP)).thenReturn("custom");
    when(props.properties()).thenReturn(ImmutableMap.of(ModuleQProfiles.SONAR_PROFILE_PROP, "custom"));

    ModuleQProfiles qps = qualityProfileProvider.provide(key, loader, projectRepo, props, mode);
    assertResponse(qps);

    verify(loader).load(eq("project"), eq("custom"), any(MutableBoolean.class));
    verifyNoMoreInteractions(loader);
    assertThat(logTester.logs(LoggerLevel.WARN)).contains("Ability to set quality profile from command line using '" + ModuleQProfiles.SONAR_PROFILE_PROP
      + "' is deprecated and will be dropped in a future SonarQube version. Please configure quality profile used by your project on SonarQube server.");
  }

  @Test
  public void testIgnoreSonarProfileIssuesMode() {
    when(mode.isNotAssociated()).thenReturn(false);
    when(mode.isIssues()).thenReturn(true);
    when(loader.load(eq("project"), (String) eq(null), any(MutableBoolean.class))).thenReturn(response);
    when(props.property(ModuleQProfiles.SONAR_PROFILE_PROP)).thenReturn("custom");

    ModuleQProfiles qps = qualityProfileProvider.provide(key, loader, projectRepo, props, mode);
    assertResponse(qps);

    verify(loader).load(eq("project"), (String) eq(null), any(MutableBoolean.class));
    verifyNoMoreInteractions(loader);
  }

  @Test
  public void testProfilePropDefault() {
    when(mode.isNotAssociated()).thenReturn(true);
    when(loader.loadDefault(eq("custom"), any(MutableBoolean.class))).thenReturn(response);
    when(props.property(ModuleQProfiles.SONAR_PROFILE_PROP)).thenReturn("custom");
    when(props.properties()).thenReturn(ImmutableMap.of(ModuleQProfiles.SONAR_PROFILE_PROP, "custom"));

    ModuleQProfiles qps = qualityProfileProvider.provide(key, loader, projectRepo, props, mode);
    assertResponse(qps);

    verify(loader).loadDefault(eq("custom"), any(MutableBoolean.class));
    verifyNoMoreInteractions(loader);
    assertThat(logTester.logs(LoggerLevel.WARN)).contains("Ability to set quality profile from command line using '" + ModuleQProfiles.SONAR_PROFILE_PROP
      + "' is deprecated and will be dropped in a future SonarQube version. Please configure quality profile used by your project on SonarQube server.");
  }

  private void assertResponse(ModuleQProfiles qps) {
    assertThat(qps.findAll()).hasSize(1);
    assertThat(qps.findAll()).extracting("key").containsExactly("profile");

  }
}
