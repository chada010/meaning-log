<script setup lang="ts">
import { onMounted, watch } from 'vue'
import HomeAiDrawer from '../components/home/HomeAiDrawer.vue'
import HomeHeroSection from '../components/home/HomeHeroSection.vue'
import HomeLogPanel from '../components/home/HomeLogPanel.vue'
import HomeStatsGrid from '../components/home/HomeStatsGrid.vue'
import { useHomeAiChat } from '../composables/useHomeAiChat'
import { useHomeLogs } from '../composables/useHomeLogs'

const homeLogs = useHomeLogs()
const homeAiChat = useHomeAiChat(homeLogs.logs)

const handleDelete = async (logId: number) => {
  const targetLog = homeLogs.logs.value.find((log) => log.id === logId)
  if (!targetLog) {
    return
  }

  await homeLogs.deleteEntry(targetLog)
  homeAiChat.handleDeletedLog(logId)
}

watch(homeLogs.logs, (nextLogs) => {
  homeAiChat.syncSelectedLog(nextLogs)
})

onMounted(async () => {
  await Promise.all([homeLogs.loadLogs(), homeLogs.loadTagOptions()])
})
</script>

<template>
  <HomeHeroSection
    v-model:quick-content="homeLogs.quickContent.value"
    :last-quick-log="homeLogs.lastQuickLog.value"
    :quick-can-save="homeLogs.quickCanSave.value"
    :quick-saving="homeLogs.quickSaving.value"
    @create-log="homeLogs.goCreateLog"
    @edit-quick-log="homeLogs.goEdit"
    @open-chat="homeAiChat.openChat"
    @save-quick-log="homeLogs.saveQuickLog"
  />

  <HomeStatsGrid
    :ai-count="homeLogs.aiCount.value"
    :latest-date="homeLogs.latestDate.value"
    :total-count="homeLogs.totalCount.value"
  />

  <HomeLogPanel
    v-model:favorite-only="homeLogs.favoriteOnly.value"
    v-model:keyword="homeLogs.keyword.value"
    v-model:selected-date="homeLogs.selectedDate.value"
    v-model:selected-tag="homeLogs.selectedTag.value"
    :loading="homeLogs.loading.value"
    :logs="homeLogs.logs.value"
    :tag-options="homeLogs.tagOptions.value"
    @create-log="homeLogs.goCreateLog"
    @delete="handleDelete($event.id)"
    @detail="homeLogs.goDetail"
    @edit="homeLogs.goEdit"
    @filter="homeLogs.loadLogs"
    @open-chat="homeAiChat.openChat"
    @reset-filters="homeLogs.resetFilters"
    @row-dbl-click="homeLogs.handleRowDblClick"
    @toggle-favorite="homeLogs.toggleFavorite"
  />

  <HomeAiDrawer
    v-model:chat-input="homeAiChat.chatInput.value"
    v-model:visible="homeAiChat.chatVisible.value"
    :apply-loading="homeAiChat.applyLoading.value"
    :chat-loading="homeAiChat.chatLoading.value"
    :messages="homeAiChat.chatMessages.value"
    :preview-suggestion="homeAiChat.previewSuggestion.value"
    :preview-tag-list="homeAiChat.previewTagList.value"
    :selected-log="homeAiChat.selectedLog.value"
    :undo-available="Boolean(homeAiChat.lastAiSnapshot.value)"
    @apply="homeAiChat.applyPreview"
    @send="homeAiChat.sendChatMessage"
    @undo="homeAiChat.undoAiApply"
  />
</template>
