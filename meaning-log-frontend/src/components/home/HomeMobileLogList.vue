<script setup lang="ts">
import { MoreFilled, Star, StarFilled } from '@element-plus/icons-vue'
import type { MeaningLog } from '../../api/logs'
import { displayLogTitle, previewLogContent, splitLogTags } from '../../utils/logDisplay'

defineProps<{
  logs: MeaningLog[]
}>()

const emit = defineEmits<{
  delete: [log: MeaningLog]
  detail: [id: number]
  edit: [id: number]
  openChat: [log: MeaningLog]
  toggleFavorite: [log: MeaningLog]
}>()

const handleCommand = (command: string | number | object, log: MeaningLog) => {
  if (command === 'chat') emit('openChat', log)
  if (command === 'edit') emit('edit', log.id)
  if (command === 'delete') emit('delete', log)
}
</script>

<template>
  <div class="mobile-log-list">
    <p v-if="!logs.length" class="log-card-empty">
      还没有日志，先写下一件小小的好事吧。
    </p>
    <article
      v-for="log in logs"
      :key="log.id"
      class="mobile-log-card"
    >
      <button
        class="mobile-log-card-main"
        type="button"
        @click="emit('detail', log.id)"
      >
        <span class="mobile-log-meta">
          <span>{{ log.logDate }}</span>
          <span v-if="log.mood">{{ log.mood }}</span>
        </span>
        <strong class="mobile-log-title">{{ displayLogTitle(log) }}</strong>
        <span class="mobile-log-summary">{{ previewLogContent(log.content) }}</span>
        <span v-if="splitLogTags(log.aiTags).length" class="mobile-log-tags">
          <el-tag
            v-for="tag in splitLogTags(log.aiTags).slice(0, 2)"
            :key="tag"
            effect="light"
            size="small"
          >{{ tag }}</el-tag>
        </span>
      </button>
      <div class="mobile-log-card-tools">
        <el-button
          text
          circle
          :icon="log.favorite ? StarFilled : Star"
          :type="log.favorite ? 'warning' : 'default'"
          :title="log.favorite ? '取消收藏' : '收藏'"
          @click="emit('toggleFavorite', log)"
        />
        <el-dropdown trigger="click" @command="handleCommand($event, log)">
          <el-button text circle :icon="MoreFilled" title="更多操作" />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="chat">和小记聊聊</el-dropdown-item>
              <el-dropdown-item command="edit">编辑日志</el-dropdown-item>
              <el-dropdown-item command="delete" divided>删除日志</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </article>
  </div>
</template>
