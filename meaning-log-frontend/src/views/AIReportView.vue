<script setup lang="ts">
import { ref } from 'vue'
import AiReportChatDrawer from '../components/reports/AiReportChatDrawer.vue'
import AiReportCompanionCards from '../components/reports/AiReportCompanionCards.vue'
import AiReportForm from '../components/reports/AiReportForm.vue'
import AiReportHeroSection from '../components/reports/AiReportHeroSection.vue'
import AiReportHistoryDrawer from '../components/reports/AiReportHistoryDrawer.vue'
import AiReportHistoryList from '../components/reports/AiReportHistoryList.vue'
import AiReportResult from '../components/reports/AiReportResult.vue'
import { useAiReport } from '../composables/useAiReport'

const {
  applyLoading, applyPreview, chatInput, chatLoading, chatMessages, chatVisible, companionCards,
  form, formatTime, generate, handleModeChange, historyLoading, lastReportSnapshot, loading,
  openChat, previewReport, previewTagList, report, reports, rules, selectCompanionCard, selectReport,
  sendChatMessage, setChatBody, setFormRef, tagList, undoReportApply,
} = useAiReport()

const historyDrawerRef = ref<InstanceType<typeof AiReportHistoryDrawer>>()

const openHistoryDrawer = () => {
  historyDrawerRef.value?.openDrawer()
}
</script>

<template>
  <section class="report-workspace">
    <aside class="report-history-panel desktop-only" v-loading="historyLoading">
      <div class="report-history-heading">
        <div>
          <p class="eyebrow">History</p>
          <h2>历史报告</h2>
        </div>
      </div>
      <AiReportHistoryList
        :reports="reports"
        :active-id="report?.id"
        :format-time="formatTime"
        @select="selectReport"
      />
    </aside>
    <section class="page-panel report-page">
      <AiReportHeroSection
        :history-count="reports.length"
        @open-history="openHistoryDrawer"
      />
      <AiReportCompanionCards
        :cards="companionCards"
        :active-mode="form.mode"
        @select="selectCompanionCard"
      />
      <AiReportForm
        :form="form"
        :rules="rules"
        :loading="loading"
        :set-form-ref="setFormRef"
        @mode-change="handleModeChange"
        @generate="generate"
      />
      <AiReportResult
        :report="report"
        :tag-list="tagList"
        :apply-loading="applyLoading"
        :can-undo="Boolean(lastReportSnapshot)"
        @open-chat="openChat"
        @undo="undoReportApply"
      />
    </section>
  </section>

  <AiReportHistoryDrawer
    ref="historyDrawerRef"
    :reports="reports"
    :active-id="report?.id"
    :format-time="formatTime"
    @select="selectReport"
  />

  <AiReportChatDrawer
    v-model:visible="chatVisible"
    v-model:chat-input="chatInput"
    :apply-loading="applyLoading"
    :set-chat-body="setChatBody"
    :chat-loading="chatLoading"
    :chat-messages="chatMessages"
    :last-report-snapshot="lastReportSnapshot"
    :preview-report="previewReport"
    :preview-tag-list="previewTagList"
    :report="report"
    @apply="applyPreview"
    @send="sendChatMessage"
    @undo="undoReportApply"
  />
</template>
