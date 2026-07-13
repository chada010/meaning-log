<script setup lang="ts">
import { Filter, Search } from '@element-plus/icons-vue'
import { computed, ref } from 'vue'

const props = defineProps<{
  favoriteOnly: boolean
  keyword: string
  selectedDate: string
  selectedTag: string
  tagOptions: string[]
}>()

const emit = defineEmits<{
  filter: []
  resetFilters: []
  'update:favoriteOnly': [value: boolean]
  'update:keyword': [value: string]
  'update:selectedDate': [value: string]
  'update:selectedTag': [value: string]
}>()

const drawerVisible = ref(false)
const activeFilterCount = computed(() =>
  Number(Boolean(props.selectedTag)) + Number(Boolean(props.selectedDate)) + Number(props.favoriteOnly),
)

const applyFilters = () => emit('filter')
const resetFilters = () => {
  emit('resetFilters')
  drawerVisible.value = false
}
</script>

<template>
  <div class="mobile-filter-shell mobile-only">
    <div class="mobile-filter-bar">
      <el-input
        :model-value="keyword"
        clearable
        :prefix-icon="Search"
        placeholder="搜索日志"
        @update:model-value="emit('update:keyword', $event)"
        @keyup.enter="applyFilters"
        @clear="applyFilters"
      />
      <el-badge :value="activeFilterCount" :hidden="activeFilterCount === 0">
        <el-button
          class="mobile-filter-trigger"
          circle
          :icon="Filter"
          title="筛选日志"
          @click="drawerVisible = true"
        />
      </el-badge>
    </div>

    <el-drawer
      v-model="drawerVisible"
      class="mobile-filter-drawer"
      direction="btt"
      size="auto"
      title="筛选日志"
    >
      <div class="mobile-filter-fields">
        <label>
          <span>标签</span>
          <el-select
            :model-value="selectedTag"
            clearable
            filterable
            placeholder="全部标签"
            @update:model-value="emit('update:selectedTag', $event ?? '')"
            @change="applyFilters"
          >
            <el-option v-for="tag in tagOptions" :key="tag" :label="tag" :value="tag" />
          </el-select>
        </label>
        <label>
          <span>日期</span>
          <el-date-picker
            :model-value="selectedDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="全部日期"
            @update:model-value="emit('update:selectedDate', $event ?? '')"
            @change="applyFilters"
          />
        </label>
        <div class="mobile-favorite-filter">
          <span>仅看收藏</span>
          <el-switch
            :model-value="favoriteOnly"
            @update:model-value="emit('update:favoriteOnly', $event)"
            @change="applyFilters"
          />
        </div>
        <el-button v-if="activeFilterCount" plain @click="resetFilters">清空筛选</el-button>
      </div>
    </el-drawer>
  </div>
</template>
