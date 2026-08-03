package com.example.demo.club.mapper;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Binds a {@code UNIX_TIMESTAMP(...)} SQL expression (whole seconds since the epoch) to an
 * {@link Instant}, deliberately bypassing the JDBC driver's own TIMESTAMP-to-LocalDateTime
 * conversion machinery. That conversion is what made {@code PublicClubPost#createdAt} ambiguous
 * (see ClubPostMapper.xml's {@code PublicPostColumnList}): MySQL's TIMESTAMP columns are always
 * stored as a true UTC instant internally, and {@code UNIX_TIMESTAMP(...)} is the exact inverse
 * of whatever {@code CURRENT_TIMESTAMP} wrote, regardless of the connection's session
 * {@code time_zone} or the JDBC URL's {@code serverTimezone} parameter (documented in
 * application.yaml as {@code Asia/Shanghai}) -- neither has to be known, correct, or even
 * consistent with the other for this value to come out right. H2 (used by the test suite)
 * round-trips the same way using the JVM's own default zone for both {@code CURRENT_TIMESTAMP}
 * and {@code UNIX_TIMESTAMP}, so this is correct there too without any test-only special-casing.
 *
 * <p>Only bound explicitly on {@code PublicClubPost#createdAt} and
 * {@code PublicClubPostComment#createdAt} (see the {@code typeHandler} attribute on those two
 * {@code <result>} mappings) rather than registered globally, so every other
 * {@code LocalDateTime}-mapped timestamp in this codebase -- including the internal
 * {@code ClubPost}/{@code ClubPostComment} models these two DTOs project from -- is untouched.
 */
@MappedTypes(Instant.class)
public class EpochSecondsInstantTypeHandler extends BaseTypeHandler<Instant> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Instant parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setLong(i, parameter.getEpochSecond());
    }

    @Override
    public Instant getNullableResult(ResultSet rs, String columnName) throws SQLException {
        long epochSeconds = rs.getLong(columnName);
        return rs.wasNull() ? null : Instant.ofEpochSecond(epochSeconds);
    }

    @Override
    public Instant getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        long epochSeconds = rs.getLong(columnIndex);
        return rs.wasNull() ? null : Instant.ofEpochSecond(epochSeconds);
    }

    @Override
    public Instant getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        long epochSeconds = cs.getLong(columnIndex);
        return cs.wasNull() ? null : Instant.ofEpochSecond(epochSeconds);
    }
}
