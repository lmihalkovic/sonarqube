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
package org.sonar.db;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.api.utils.log.Loggers;
import org.sonar.db.dialect.Oracle;
import org.sonar.test.DbTests;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.sonar.db.DatabaseUtils.buildLikeValue;
import static org.sonar.db.DatabaseUtils.toUniqueAndSortedList;
import static org.sonar.db.WildcardPosition.AFTER;
import static org.sonar.db.WildcardPosition.BEFORE;
import static org.sonar.db.WildcardPosition.BEFORE_AND_AFTER;

@Category(DbTests.class)
public class DatabaseUtilsTest {

  @Rule
  public DbTester dbTester = DbTester.create(System2.INSTANCE);
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public LogTester logTester = new LogTester();

  @Test
  public void should_close_connection() throws Exception {
    Connection connection = dbTester.openConnection();
    assertThat(isClosed(connection)).isFalse();

    DatabaseUtils.closeQuietly(connection);
    assertThat(isClosed(connection)).isTrue();
  }

  @Test
  public void should_support_null_connection() {
    DatabaseUtils.closeQuietly((Connection) null);
    // no failure
  }

  @Test
  public void should_close_statement_and_resultset() throws Exception {
    Connection connection = dbTester.openConnection();
    try {
      PreparedStatement statement = connection.prepareStatement(selectDual());
      ResultSet rs = statement.executeQuery();

      DatabaseUtils.closeQuietly(rs);
      DatabaseUtils.closeQuietly(statement);

      assertThat(isClosed(statement)).isTrue();
      assertThat(isClosed(rs)).isTrue();
    } finally {
      DatabaseUtils.closeQuietly(connection);
    }
  }

  @Test
  public void should_not_fail_on_connection_errors() throws SQLException {
    Connection connection = mock(Connection.class);
    doThrow(new SQLException()).when(connection).close();

    DatabaseUtils.closeQuietly(connection);

    // no failure
    verify(connection).close(); // just to be sure
  }

  @Test
  public void should_not_fail_on_statement_errors() throws SQLException {
    Statement statement = mock(Statement.class);
    doThrow(new SQLException()).when(statement).close();

    DatabaseUtils.closeQuietly(statement);

    // no failure
    verify(statement).close(); // just to be sure
  }

  @Test
  public void should_not_fail_on_resulset_errors() throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    doThrow(new SQLException()).when(rs).close();

    DatabaseUtils.closeQuietly(rs);

    // no failure
    verify(rs).close(); // just to be sure
  }

  @Test
  public void toUniqueAndSortedList_throws_NPE_if_arg_is_null() {
    expectedException.expect(NullPointerException.class);

    toUniqueAndSortedList(null);
  }

  @Test
  public void toUniqueAndSortedList_throws_NPE_if_arg_contains_a_null() {
    expectedException.expect(NullPointerException.class);

    toUniqueAndSortedList(asList("A", null, "C"));
  }

  @Test
  public void toUniqueAndSortedList_throws_NPE_if_arg_is_a_set_containing_a_null() {
    expectedException.expect(NullPointerException.class);

    toUniqueAndSortedList(new HashSet<Comparable>(asList("A", null, "C")));
  }

  @Test
  public void toUniqueAndSortedList_enforces_natural_order() {
    assertThat(toUniqueAndSortedList(asList("A", "B", "C"))).containsExactly("A", "B", "C");
    assertThat(toUniqueAndSortedList(asList("B", "A", "C"))).containsExactly("A", "B", "C");
    assertThat(toUniqueAndSortedList(asList("B", "C", "A"))).containsExactly("A", "B", "C");
  }

  @Test
  public void toUniqueAndSortedList_removes_duplicates() {
    assertThat(toUniqueAndSortedList(asList("A", "A", "A"))).containsExactly("A");
    assertThat(toUniqueAndSortedList(asList("A", "C", "A"))).containsExactly("A", "C");
    assertThat(toUniqueAndSortedList(asList("C", "C", "B", "B", "A", "N", "C", "A"))).containsExactly("A", "B", "C", "N");
  }

  @Test
  public void toUniqueAndSortedList_removes_duplicates_and_apply_natural_order_of_any_Comparable() {
    assertThat(
      toUniqueAndSortedList(asList(myComparable(2), myComparable(5), myComparable(2), myComparable(4), myComparable(-1), myComparable(10))))
      .containsExactly(
        myComparable(-1), myComparable(2), myComparable(4), myComparable(5), myComparable(10));
  }

  private static DatabaseUtilsTest.MyComparable myComparable(int ordinal) {
    return new DatabaseUtilsTest.MyComparable(ordinal);
  }

  private static final class MyComparable implements Comparable<MyComparable> {
    private final int ordinal;

    private MyComparable(int ordinal) {
      this.ordinal = ordinal;
    }

    @Override
    public int compareTo(MyComparable o) {
      return ordinal - o.ordinal;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      MyComparable that = (MyComparable) o;
      return ordinal == that.ordinal;
    }

    @Override
    public int hashCode() {
      return Objects.hash(ordinal);
    }
  }

  /**
   * Connection.isClosed() has been introduced in java 1.6
   */
  private boolean isClosed(Connection c) {
    try {
      c.createStatement().execute(selectDual());
      return false;
    } catch (Exception e) {
      return true;
    }
  }

  /**
   * Statement.isClosed() has been introduced in java 1.6
   */
  private boolean isClosed(Statement s) {
    try {
      s.execute("SELECT 1");
      return false;
    } catch (Exception e) {
      return true;
    }
  }

  /**
   * ResultSet.isClosed() has been introduced in java 1.6
   */
  private boolean isClosed(ResultSet rs) {
    try {
      rs.next();
      return false;
    } catch (Exception e) {
      return true;
    }
  }

  private String selectDual() {
    String sql = "SELECT 1";
    if (Oracle.ID.equals(dbTester.database().getDialect().getId())) {
      sql = "SELECT 1 FROM DUAL";
    }
    return sql;
  }

  @Test
  public void repeatCondition() {
    assertThat(DatabaseUtils.repeatCondition("uuid=?", 1, "or")).isEqualTo("uuid=?");
    assertThat(DatabaseUtils.repeatCondition("uuid=?", 3, "or")).isEqualTo("uuid=? or uuid=? or uuid=?");
  }

  @Test
  public void execute_large_inputs() {
    List<Integer> inputs = newArrayList();
    List<String> expectedOutputs = newArrayList();
    for (int i = 0; i < 2010; i++) {
      inputs.add(i);
      expectedOutputs.add(Integer.toString(i));
    }

    List<String> outputs = DatabaseUtils.executeLargeInputs(inputs, new Function<List<Integer>, List<String>>() {
      @Override
      public List<String> apply(List<Integer> input) {
        // Check that each partition is only done on 1000 elements max
        assertThat(input.size()).isLessThanOrEqualTo(1000);
        return newArrayList(Iterables.transform(input, new Function<Integer, String>() {
          @Override
          public String apply(Integer input) {
            return Integer.toString(input);
          }
        }));
      }
    });

    assertThat(outputs).isEqualTo(expectedOutputs);
  }

  @Test
  public void execute_large_inputs_on_empty_list() {
    List<String> outputs = DatabaseUtils.executeLargeInputs(Collections.<Integer>emptyList(), new Function<List<Integer>, List<String>>() {
      @Override
      public List<String> apply(List<Integer> input) {
        fail("No partition should be made on empty list");
        return Collections.emptyList();
      }
    });

    assertThat(outputs).isEmpty();
  }

  @Test
  public void log_all_sql_exceptions() {
    SQLException root = new SQLException("this is root", "123");
    SQLException next = new SQLException("this is next", "456");
    root.setNextException(next);

    DatabaseUtils.log(Loggers.get(getClass()), root);

    assertThat(logTester.logs(LoggerLevel.ERROR)).contains("SQL error: 456. Message: this is next");
  }

  @Test
  public void buildLikeValue_with_special_characters() {
    String escapedValue = "like-\\/_/%//-value";
    String wildcard = "%";

    assertThat(buildLikeValue("like-\\_%/-value", BEFORE)).isEqualTo(wildcard + escapedValue);
    assertThat(buildLikeValue("like-\\_%/-value", AFTER)).isEqualTo(escapedValue + wildcard);
    assertThat(buildLikeValue("like-\\_%/-value", BEFORE_AND_AFTER)).isEqualTo(wildcard + escapedValue + wildcard);
  }
}
