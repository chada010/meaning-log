package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.LogImage;
import com.chad.meaninglog.entity.MeaningLog;
import com.chad.meaninglog.entity.UserAccount;

import java.util.List;
import java.util.Optional;

public interface LogImageRepository extends BaseMapper<LogImage> {

    default List<LogImage> findByMeaningLogOrderByDisplayOrderAscIdAsc(MeaningLog meaningLog) {
        return selectList(new LambdaQueryWrapper<LogImage>()
                .eq(LogImage::getMeaningLogId, meaningLog.getId())
                .orderByAsc(LogImage::getDisplayOrder)
                .orderByAsc(LogImage::getId));
    }

    default Optional<LogImage> findByIdAndMeaningLogUser(Long id, UserAccount user) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapper<LogImage>()
                .eq(LogImage::getId, id)
                .inSql(LogImage::getMeaningLogId, "select id from meaning_logs where user_id = " + user.getId())));
    }

    default void deleteByMeaningLog(MeaningLog meaningLog) {
        delete(new LambdaQueryWrapper<LogImage>().eq(LogImage::getMeaningLogId, meaningLog.getId()));
    }

    default LogImage save(LogImage image) {
        if (image.getId() == null) {
            insert(image);
        } else {
            updateById(image);
        }
        return image;
    }
}
