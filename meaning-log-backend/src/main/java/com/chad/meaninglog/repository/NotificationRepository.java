package com.chad.meaninglog.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chad.meaninglog.entity.Notification;

import java.util.List;

public interface NotificationRepository extends BaseMapper<Notification> {

    default List<Notification> findByReceiverId(Long receiverId, boolean unreadOnly, int offset, int size) {
        LambdaQueryWrapper<Notification> query = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, receiverId);
        if (unreadOnly) {
            query.eq(Notification::isReadFlag, false);
        }
        return selectList(query
                .orderByDesc(Notification::getCreatedAt)
                .last("LIMIT " + offset + ", " + size));
    }

    default long countUnread(Long receiverId) {
        return selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, receiverId)
                .eq(Notification::isReadFlag, false));
    }

    default int markAsRead(Long id, Long receiverId) {
        Notification update = new Notification();
        update.setId(id);
        update.setReadFlag(true);
        return update(update, new LambdaQueryWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getReceiverId, receiverId));
    }

    default int markAllAsRead(Long receiverId) {
        Notification update = new Notification();
        update.setReadFlag(true);
        return update(update, new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, receiverId)
                .eq(Notification::isReadFlag, false));
    }
}
