package com.chad.meaninglog.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CommunityCounterBatchRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock businessClock;

    public int updateViews(List<ViewSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("UPDATE public_logs SET view_count = CASE id ");
        List<Object> args = new ArrayList<>(snapshots.size() * 4 + 1);
        for (ViewSnapshot snapshot : snapshots) {
            sql.append("WHEN ? THEN GREATEST(view_count, ?) ");
            args.add(snapshot.publicLogId());
            args.add(snapshot.viewCount());
        }
        sql.append("ELSE view_count END, cache_version = cache_version + 1, updated_at = ? WHERE id IN (");
        args.add(LocalDateTime.now(businessClock));
        appendIds(sql, args, snapshots.stream().map(ViewSnapshot::publicLogId).toList());
        sql.append(')');
        return jdbcTemplate.update(sql.toString(), args.toArray());
    }

    public int updateCountsIfVersion(List<CountSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder("UPDATE public_logs SET like_count = CASE id ");
        List<Object> args = new ArrayList<>(snapshots.size() * 8 + 1);
        appendCountCase(sql, args, snapshots, true);
        sql.append("ELSE like_count END, comment_count = CASE id ");
        appendCountCase(sql, args, snapshots, false);
        sql.append("ELSE comment_count END, cache_version = cache_version + 1, updated_at = ? WHERE ");
        args.add(LocalDateTime.now(businessClock));
        for (int i = 0; i < snapshots.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("(id = ? AND cache_version = ?)");
            CountSnapshot snapshot = snapshots.get(i);
            args.add(snapshot.publicLogId());
            args.add(snapshot.expectedVersion());
        }
        return jdbcTemplate.update(sql.toString(), args.toArray());
    }

    private void appendCountCase(StringBuilder sql,
                                 List<Object> args,
                                 List<CountSnapshot> snapshots,
                                 boolean likes) {
        for (CountSnapshot snapshot : snapshots) {
            sql.append("WHEN ? THEN ? ");
            args.add(snapshot.publicLogId());
            args.add(likes ? snapshot.likeCount() : snapshot.commentCount());
        }
    }

    private void appendIds(StringBuilder sql, List<Object> args, List<Long> ids) {
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sql.append(',');
            }
            sql.append('?');
            args.add(ids.get(i));
        }
    }

    public record ViewSnapshot(Long publicLogId, long viewCount) {
    }

    public record CountSnapshot(Long publicLogId,
                                long likeCount,
                                long commentCount,
                                long expectedVersion) {
    }
}
