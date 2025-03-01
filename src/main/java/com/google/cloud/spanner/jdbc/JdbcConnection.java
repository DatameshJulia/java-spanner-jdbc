/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.spanner.jdbc;

import com.google.api.client.util.Preconditions;
import com.google.cloud.spanner.CommitResponse;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.SpannerException;
import com.google.cloud.spanner.TimestampBound;
import com.google.cloud.spanner.connection.AutocommitDmlMode;
import com.google.cloud.spanner.connection.ConnectionOptions;
import com.google.cloud.spanner.connection.SavepointSupport;
import com.google.cloud.spanner.connection.TransactionMode;
import com.google.common.collect.Iterators;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Jdbc Connection class for Google Cloud Spanner */
class JdbcConnection extends AbstractJdbcConnection {
  private static final String ONLY_RS_FORWARD_ONLY =
      "Only result sets of type TYPE_FORWARD_ONLY are supported";
  private static final String ONLY_CONCUR_READ_ONLY =
      "Only result sets with concurrency CONCUR_READ_ONLY are supported";
  private static final String ONLY_CLOSE_CURSORS_AT_COMMIT =
      "Only result sets with holdability CLOSE_CURSORS_AT_COMMIT are supported";
  static final String ONLY_NO_GENERATED_KEYS = "Only NO_GENERATED_KEYS are supported";
  static final String IS_VALID_QUERY = "SELECT 1";

  private Map<String, Class<?>> typeMap = new HashMap<>();

  JdbcConnection(String connectionUrl, ConnectionOptions options) throws SQLException {
    super(connectionUrl, options);
  }

  @Override
  public Statement createStatement() throws SQLException {
    checkClosed();
    return new JdbcStatement(this);
  }

  @Override
  public JdbcPreparedStatement prepareStatement(String sql) throws SQLException {
    checkClosed();
    return new JdbcPreparedStatement(this, sql);
  }

  @Override
  public String nativeSQL(String sql) throws SQLException {
    checkClosed();
    return getParser()
        .convertPositionalParametersToNamedParameters('?', getParser().removeCommentsAndTrim(sql))
        .sqlWithNamedParameters;
  }

  @Override
  public String getStatementTag() throws SQLException {
    checkClosed();
    return getSpannerConnection().getStatementTag();
  }

  @Override
  public void setStatementTag(String tag) throws SQLException {
    checkClosed();
    try {
      getSpannerConnection().setStatementTag(tag);
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public String getTransactionTag() throws SQLException {
    checkClosed();
    return getSpannerConnection().getTransactionTag();
  }

  @Override
  public void setTransactionTag(String tag) throws SQLException {
    checkClosed();
    try {
      getSpannerConnection().setTransactionTag(tag);
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public void setTransactionMode(TransactionMode mode) throws SQLException {
    checkClosed();
    getSpannerConnection().setTransactionMode(mode);
  }

  @Override
  public TransactionMode getTransactionMode() throws SQLException {
    checkClosed();
    return getSpannerConnection().getTransactionMode();
  }

  @Override
  public void setAutocommitDmlMode(AutocommitDmlMode mode) throws SQLException {
    checkClosed();
    getSpannerConnection().setAutocommitDmlMode(mode);
  }

  @Override
  public AutocommitDmlMode getAutocommitDmlMode() throws SQLException {
    checkClosed();
    return getSpannerConnection().getAutocommitDmlMode();
  }

  @Override
  public void setReadOnlyStaleness(TimestampBound staleness) throws SQLException {
    checkClosed();
    getSpannerConnection().setReadOnlyStaleness(staleness);
  }

  @Override
  public TimestampBound getReadOnlyStaleness() throws SQLException {
    checkClosed();
    return getSpannerConnection().getReadOnlyStaleness();
  }

  @Override
  public void setOptimizerVersion(String optimizerVersion) throws SQLException {
    checkClosed();
    getSpannerConnection().setOptimizerVersion(optimizerVersion);
  }

  @Override
  public String getOptimizerVersion() throws SQLException {
    checkClosed();
    return getSpannerConnection().getOptimizerVersion();
  }

  @Override
  public boolean isInTransaction() throws SQLException {
    checkClosed();
    return getSpannerConnection().isInTransaction();
  }

  @Override
  public boolean isTransactionStarted() throws SQLException {
    checkClosed();
    return getSpannerConnection().isTransactionStarted();
  }

  @Override
  public void setAutoCommit(boolean autoCommit) throws SQLException {
    checkClosed();
    try {
      // According to the JDBC spec's we need to commit the current transaction when changing
      // autocommit mode.
      if (getSpannerConnection().isAutocommit() != autoCommit
          && getSpannerConnection().isTransactionStarted()) {
        commit();
      }
      getSpannerConnection().setAutocommit(autoCommit);
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public boolean getAutoCommit() throws SQLException {
    checkClosed();
    return getSpannerConnection().isAutocommit();
  }

  @Override
  public void commit() throws SQLException {
    checkClosed();
    try {
      getSpannerConnection().commit();
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public void rollback() throws SQLException {
    checkClosed();
    try {
      getSpannerConnection().rollback();
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public void close() throws SQLException {
    try {
      getSpannerConnection().close();
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public boolean isClosed() {
    return getSpannerConnection().isClosed();
  }

  @Override
  public DatabaseMetaData getMetaData() throws SQLException {
    checkClosed();
    return new JdbcDatabaseMetaData(this);
  }

  @Override
  public void setReadOnly(boolean readOnly) throws SQLException {
    checkClosed();
    try {
      getSpannerConnection().setReadOnly(readOnly);
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public boolean isReadOnly() throws SQLException {
    checkClosed();
    return getSpannerConnection().isReadOnly();
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency)
      throws SQLException {
    checkClosed();
    JdbcPreconditions.checkSqlFeatureSupported(
        resultSetType == ResultSet.TYPE_FORWARD_ONLY, ONLY_RS_FORWARD_ONLY);
    JdbcPreconditions.checkSqlFeatureSupported(
        resultSetConcurrency == ResultSet.CONCUR_READ_ONLY, ONLY_CONCUR_READ_ONLY);
    return createStatement();
  }

  @Override
  public Statement createStatement(
      int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
    checkClosed();
    JdbcPreconditions.checkSqlFeatureSupported(
        resultSetType == ResultSet.TYPE_FORWARD_ONLY, ONLY_RS_FORWARD_ONLY);
    JdbcPreconditions.checkSqlFeatureSupported(
        resultSetConcurrency == ResultSet.CONCUR_READ_ONLY, ONLY_CONCUR_READ_ONLY);
    JdbcPreconditions.checkSqlFeatureSupported(
        resultSetHoldability == ResultSet.CLOSE_CURSORS_AT_COMMIT, ONLY_CLOSE_CURSORS_AT_COMMIT);
    return createStatement();
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
      throws SQLException {
    checkClosed();
    JdbcPreconditions.checkSqlFeatureSupported(
        resultSetType == ResultSet.TYPE_FORWARD_ONLY, ONLY_RS_FORWARD_ONLY);
    JdbcPreconditions.checkSqlFeatureSupported(
        resultSetConcurrency == ResultSet.CONCUR_READ_ONLY, ONLY_CONCUR_READ_ONLY);
    return prepareStatement(sql);
  }

  @Override
  public PreparedStatement prepareStatement(
      String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
      throws SQLException {
    checkClosed();
    JdbcPreconditions.checkSqlFeatureSupported(
        resultSetType == ResultSet.TYPE_FORWARD_ONLY, ONLY_RS_FORWARD_ONLY);
    JdbcPreconditions.checkSqlFeatureSupported(
        resultSetConcurrency == ResultSet.CONCUR_READ_ONLY, ONLY_CONCUR_READ_ONLY);
    JdbcPreconditions.checkSqlFeatureSupported(
        resultSetHoldability == ResultSet.CLOSE_CURSORS_AT_COMMIT, ONLY_CLOSE_CURSORS_AT_COMMIT);
    return prepareStatement(sql);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
    checkClosed();
    JdbcPreconditions.checkSqlFeatureSupported(
        autoGeneratedKeys == Statement.NO_GENERATED_KEYS, ONLY_NO_GENERATED_KEYS);
    return prepareStatement(sql);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
    checkClosed();
    return prepareStatement(sql);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
    checkClosed();
    return prepareStatement(sql);
  }

  @Override
  public Map<String, Class<?>> getTypeMap() throws SQLException {
    checkClosed();
    return new HashMap<>(typeMap);
  }

  @Override
  public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
    checkClosed();
    this.typeMap = new HashMap<>(map);
  }

  @Override
  public boolean isValid(int timeout) throws SQLException {
    JdbcPreconditions.checkArgument(timeout >= 0, "timeout must be >= 0");
    if (!isClosed()) {
      try {
        Statement statement = createStatement();
        statement.setQueryTimeout(timeout);
        try (ResultSet rs = statement.executeQuery(IS_VALID_QUERY)) {
          if (rs.next()) {
            if (rs.getLong(1) == 1L) {
              return true;
            }
          }
        }
      } catch (SQLException e) {
        // ignore
      }
    }
    return false;
  }

  @Override
  public Blob createBlob() throws SQLException {
    checkClosed();
    return new JdbcBlob();
  }

  @Override
  public Clob createClob() throws SQLException {
    checkClosed();
    return new JdbcClob();
  }

  @Override
  public NClob createNClob() throws SQLException {
    checkClosed();
    return new JdbcClob();
  }

  @Override
  public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
    checkClosed();
    return JdbcArray.createArray(typeName, elements);
  }

  @Override
  public void setCatalog(String catalog) throws SQLException {
    // This method could be changed to allow the user to change to another database.
    // For now we only support setting an empty string in order to support frameworks
    // and applications that set this when no catalog has been specified in the connection
    // URL.
    checkClosed();
    JdbcPreconditions.checkArgument("".equals(catalog), "Only catalog \"\" is supported");
  }

  @Override
  public String getCatalog() throws SQLException {
    checkClosed();
    return "";
  }

  @Override
  public void setSchema(String schema) throws SQLException {
    checkClosed();
    // Cloud Spanner does not support schemas, but does contain a pseudo 'empty string' schema that
    // might be set by frameworks and applications that read the database metadata.
    JdbcPreconditions.checkArgument("".equals(schema), "Only schema \"\" is supported");
  }

  @Override
  public String getSchema() throws SQLException {
    checkClosed();
    return "";
  }

  @Override
  public SavepointSupport getSavepointSupport() throws SQLException {
    checkClosed();
    return getSpannerConnection().getSavepointSupport();
  }

  @Override
  public void setSavepointSupport(SavepointSupport savepointSupport) throws SQLException {
    checkClosed();
    try {
      getSpannerConnection().setSavepointSupport(savepointSupport);
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public Savepoint setSavepoint() throws SQLException {
    checkClosed();
    try {
      JdbcSavepoint savepoint = JdbcSavepoint.unnamed();
      getSpannerConnection().savepoint(savepoint.internalGetSavepointName());
      return savepoint;
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public Savepoint setSavepoint(String name) throws SQLException {
    checkClosed();
    try {
      JdbcSavepoint savepoint = JdbcSavepoint.named(name);
      getSpannerConnection().savepoint(savepoint.internalGetSavepointName());
      return savepoint;
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public void rollback(Savepoint savepoint) throws SQLException {
    checkClosed();
    JdbcPreconditions.checkArgument(savepoint instanceof JdbcSavepoint, savepoint);
    JdbcSavepoint jdbcSavepoint = (JdbcSavepoint) savepoint;
    try {
      getSpannerConnection().rollbackToSavepoint(jdbcSavepoint.internalGetSavepointName());
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public void releaseSavepoint(Savepoint savepoint) throws SQLException {
    checkClosed();
    JdbcPreconditions.checkArgument(savepoint instanceof JdbcSavepoint, savepoint);
    JdbcSavepoint jdbcSavepoint = (JdbcSavepoint) savepoint;
    try {
      getSpannerConnection().releaseSavepoint(jdbcSavepoint.internalGetSavepointName());
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public Timestamp getCommitTimestamp() throws SQLException {
    checkClosed();
    try {
      return getSpannerConnection().getCommitTimestamp().toSqlTimestamp();
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public CommitResponse getCommitResponse() throws SQLException {
    checkClosed();
    try {
      return getSpannerConnection().getCommitResponse();
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public void setReturnCommitStats(boolean returnCommitStats) throws SQLException {
    checkClosed();
    try {
      getSpannerConnection().setReturnCommitStats(returnCommitStats);
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public boolean isReturnCommitStats() throws SQLException {
    checkClosed();
    try {
      return getSpannerConnection().isReturnCommitStats();
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public Timestamp getReadTimestamp() throws SQLException {
    checkClosed();
    try {
      return getSpannerConnection().getReadTimestamp().toSqlTimestamp();
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public boolean isRetryAbortsInternally() throws SQLException {
    checkClosed();
    try {
      return getSpannerConnection().isRetryAbortsInternally();
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public void setRetryAbortsInternally(boolean retryAbortsInternally) throws SQLException {
    checkClosed();
    try {
      getSpannerConnection().setRetryAbortsInternally(retryAbortsInternally);
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public void write(Mutation mutation) throws SQLException {
    checkClosed();
    try {
      getSpannerConnection().write(mutation);
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public void write(Iterable<Mutation> mutations) throws SQLException {
    checkClosed();
    try {
      getSpannerConnection().write(mutations);
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public void bufferedWrite(Mutation mutation) throws SQLException {
    checkClosed();
    try {
      getSpannerConnection().bufferedWrite(mutation);
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @Override
  public void bufferedWrite(Iterable<Mutation> mutations) throws SQLException {
    checkClosed();
    try {
      getSpannerConnection().bufferedWrite(mutations);
    } catch (SpannerException e) {
      throw JdbcSqlExceptionFactory.of(e);
    }
  }

  @SuppressWarnings("deprecation")
  private static final class JdbcToSpannerTransactionRetryListener
      implements com.google.cloud.spanner.connection.TransactionRetryListener {
    private final TransactionRetryListener delegate;

    JdbcToSpannerTransactionRetryListener(TransactionRetryListener delegate) {
      this.delegate = Preconditions.checkNotNull(delegate);
    }

    @Override
    public void retryStarting(
        com.google.cloud.Timestamp transactionStarted, long transactionId, int retryAttempt) {
      delegate.retryStarting(transactionStarted, transactionId, retryAttempt);
    }

    @Override
    public void retryFinished(
        com.google.cloud.Timestamp transactionStarted,
        long transactionId,
        int retryAttempt,
        RetryResult result) {
      delegate.retryFinished(
          transactionStarted,
          transactionId,
          retryAttempt,
          TransactionRetryListener.RetryResult.valueOf(result.name()));
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof JdbcToSpannerTransactionRetryListener)) {
        return false;
      }
      JdbcToSpannerTransactionRetryListener other = (JdbcToSpannerTransactionRetryListener) o;
      return this.delegate.equals(other.delegate);
    }

    @Override
    public int hashCode() {
      return delegate.hashCode();
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void addTransactionRetryListener(TransactionRetryListener listener) throws SQLException {
    checkClosed();
    getSpannerConnection()
        .addTransactionRetryListener(new JdbcToSpannerTransactionRetryListener(listener));
  }

  @Override
  public void addTransactionRetryListener(
      com.google.cloud.spanner.connection.TransactionRetryListener listener) throws SQLException {
    checkClosed();
    getSpannerConnection().addTransactionRetryListener(listener);
  }

  @SuppressWarnings("deprecation")
  @Override
  public boolean removeTransactionRetryListener(TransactionRetryListener listener)
      throws SQLException {
    checkClosed();
    return getSpannerConnection()
        .removeTransactionRetryListener(new JdbcToSpannerTransactionRetryListener(listener));
  }

  @Override
  public boolean removeTransactionRetryListener(
      com.google.cloud.spanner.connection.TransactionRetryListener listener) throws SQLException {
    checkClosed();
    return getSpannerConnection().removeTransactionRetryListener(listener);
  }

  @SuppressWarnings("deprecation")
  @Override
  public Iterator<TransactionRetryListener> getTransactionRetryListeners() throws SQLException {
    checkClosed();
    return Iterators.transform(
        getSpannerConnection().getTransactionRetryListeners(),
        input -> {
          if (input instanceof JdbcToSpannerTransactionRetryListener) {
            return ((JdbcToSpannerTransactionRetryListener) input).delegate;
          }
          return null;
        });
  }

  @Override
  public Iterator<com.google.cloud.spanner.connection.TransactionRetryListener>
      getTransactionRetryListenersFromConnection() throws SQLException {
    checkClosed();
    return getSpannerConnection().getTransactionRetryListeners();
  }
}
