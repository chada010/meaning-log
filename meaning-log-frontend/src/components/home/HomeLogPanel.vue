<script setup lang="ts">
import { Calendar, ChatDotRound, Delete, Edit, Plus, Search, Star, View } from '@element-plus/icons-vue'
import { computed } from 'vue'
import type { MeaningLog } from '../../api/logs'

const props = defineProps<{
  favoriteOnly: boolean
  keyword: string
  loading: boolean
  logs: MeaningLog[]
  selectedDate: string
  selectedTag: string
  tagOptions: string[]
}>()

const emit = defineEmits<{
  createLog: []
  delete: [log: MeaningLog]
  detail: [id: number]
  edit: [id: number]
  filter: []
  openChat: [log?: MeaningLog]
  resetFilters: []
  rowDblClick: [log: MeaningLog]
  toggleFavorite: [log: MeaningLog]
  'update:favoriteOnly': [value: boolean]
  'update:keyword': [value: string]
  'update:selectedDate': [value: string]
  'update:selectedTag': [value: string]
}>()

const keywordModel = computed({
  get: () => props.keyword,
  set: (value: string) => emit('update:keyword', value),
})

const selectedTagModel = computed({
  get: () => props.selectedTag,
  set: (value: string) => emit('update:selectedTag', value),
})

const selectedDateModel = computed({
  get: () => props.selectedDate,
  set: (value: string) => emit('update:selectedDate', value),
})

const favoriteOnlyModel = computed({
  get: () => props.favoriteOnly,
  set: (value: boolean) => emit('update:favoriteOnly', value),
})

const formatTime = (value: string) => value?.replace('T', ' ').slice(0, 16)

const previewContent = (value: string) => {
  const text = value.replace(/\s+/g, ' ').trim()
  return text.length > 96 ? `${text.slice(0, 96)}...` : text
}

const splitTags = (value?: string) => value?.split(',').map((tag) => tag.trim()).filter(Boolean) ?? []
</script>

<template>
  <section class="page-panel journal-panel">
    <div class="page-heading journal-heading">
      <div>
        <p class="eyebrow">Journal</p>
        <h2>日志列表</h2>
      </div>
      <el-button type="primary" :icon="Plus" @click="emit('createLog')">新日志</el-button>
    </div>

    <div class="toolbar journal-toolbar">
      <el-input
        v-model="keywordModel"
        class="search-input"
        clearable
        :prefix-icon="Search"
        placeholder="搜索标题、正文、AI 总结"
        @keyup.enter="emit('filter')"
        @clear="emit('filter')"
      />
      <el-select
        v-model="selectedTagModel"
        class="tag-filter"
        clearable
        filterable
        placeholder="标签"
        @change="emit('filter')"
        @clear="emit('filter')"
      >
        <el-option v-for="tag in tagOptions" :key="tag" :label="tag" :value="tag" />
      </el-select>
      <el-date-picker
        v-model="selectedDateModel"
        class="date-filter"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="日期"
      />
      <el-switch
        v-model="favoriteOnlyModel"
        active-text="收藏"
        @change="emit('filter')"
      />
      <el-button :icon="Calendar" @click="emit('filter')">筛选</el-button>
      <el-button @click="emit('resetFilters')">清空</el-button>
    </div>

    <div class="desktop-only">
      <el-table
        v-loading="loading"
        class="journal-table"
        :data="logs"
        empty-text="还没有日志，先写下一件小小的好事吧。"
        @row-dblclick="emit('rowDblClick', $event)"
      >
        <el-table-column label="日志" min-width="360">
          <template #default="{ row }">
            <div class="journal-title-cell">
              <div class="journal-title-line">
                <button type="button" @click="emit('detail', row.id)">{{ row.title }}</button>
                <el-tag v-if="row.favorite" type="warning" effect="light">收藏</el-tag>
              </div>
              <p>{{ previewContent(row.content) }}</p>
              <div class="journal-row-meta">
                <span>{{ row.logDate }}</span>
                <span v-if="row.mood">{{ row.mood }}</span>
                <span v-if="row.images?.length">{{ row.images.length }} 张图片</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="AI 标签" min-width="170">
          <template #default="{ row }">
            <div v-if="splitTags(row.aiTags).length" class="compact-tag-row">
              <el-tag v-for="tag in splitTags(row.aiTags).slice(0, 3)" :key="tag" effect="light">{{ tag }}</el-tag>
            </div>
            <span v-else class="muted">待整理</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新" width="150">
          <template #default="{ row }">
            <span class="muted">{{ formatTime(row.updatedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="245" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button
                size="small"
                text
                :title="row.favorite ? '取消收藏' : '收藏'"
                :icon="Star"
                :type="row.favorite ? 'warning' : 'default'"
                @click="emit('toggleFavorite', row)"
              />
              <el-button size="small" text :icon="View" @click="emit('detail', row.id)">详情</el-button>
              <el-button size="small" text type="primary" :icon="ChatDotRound" @click="emit('openChat', row)">小记</el-button>
              <el-button size="small" text :icon="Edit" @click="emit('edit', row.id)">编辑</el-button>
              <el-button size="small" text title="删除" type="danger" :icon="Delete" @click="emit('delete', row)" />
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="!loading" class="mobile-only log-card-list">
      <p v-if="!logs.length" class="log-card-empty">还没有日志，先写下一件小小的好事吧。</p>
      <div v-for="log in logs" :key="log.id" class="log-card">
        <div class="log-card-header">
          <span class="log-card-date">{{ log.logDate }}</span>
          <div class="log-card-tags">
            <el-tag v-if="log.favorite" type="warning" effect="light" size="small">收藏</el-tag>
            <el-tag
              v-for="tag in splitTags(log.aiTags).slice(0, 2)"
              :key="tag"
              effect="light"
              size="small"
            >{{ tag }}</el-tag>
          </div>
        </div>
        <button class="log-card-title" type="button" @click="emit('detail', log.id)">{{ log.title }}</button>
        <p class="log-card-summary">{{ previewContent(log.content) }}</p>
        <div class="log-card-actions">
          <el-button size="small" text :icon="View" @click="emit('detail', log.id)">详情</el-button>
          <el-button size="small" text type="primary" :icon="ChatDotRound" @click="emit('openChat', log)">小记</el-button>
          <el-button size="small" text :icon="Edit" @click="emit('edit', log.id)">编辑</el-button>
          <el-button
            size="small"
            text
            :icon="Star"
            :type="log.favorite ? 'warning' : 'default'"
            @click="emit('toggleFavorite', log)"
          />
          <el-button size="small" text type="danger" :icon="Delete" @click="emit('delete', log)" />
        </div>
      </div>
    </div>
  </section>
</template>
