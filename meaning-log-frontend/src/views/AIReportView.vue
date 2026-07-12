<script setup lang="ts">
import { ChatDotRound, MagicStick, RefreshLeft } from '@element-plus/icons-vue'
import AiReportChatDrawer from '../components/reports/AiReportChatDrawer.vue'
import { useAiReport } from '../composables/useAiReport'

const {
  applyLoading, applyPreview, chatInput, chatLoading, chatMessages, chatVisible, companionCards,
  form, formRef, formatTime, generate, handleModeChange, historyLoading, lastReportSnapshot, loading,
  openChat, previewReport, previewTagList, report, reports, rules, selectCompanionCard, selectReport,
  sendChatMessage, setChatBody, tagList, undoReportApply,
} = useAiReport()
</script>

<template>
  <section class="report-workspace">
    <aside class="report-history-panel" v-loading="historyLoading">
      <div class="report-history-heading"><div><p class="eyebrow">History</p><h2>历史报告</h2></div></div>
      <div class="report-history-list">
        <button v-for="item in reports" :key="item.id" class="report-history-item" :class="{ active: item.id === report?.id }" @click="selectReport(item)">
          <strong>{{ item.title }}</strong><span>{{ item.period }}</span><small>{{ formatTime(item.createdAt) }}</small>
        </button>
      </div>
    </aside>
    <section class="page-panel report-page">
      <div class="page-heading"><div><p class="eyebrow">Xiaoji Review</p><h2>小记陪你回看最近</h2></div></div>
      <div class="companion-card-grid">
        <button v-for="card in companionCards" :key="card.mode" class="companion-card" :class="{ active: form.mode === card.mode }" type="button" @click="selectCompanionCard(card.mode, card.days)">
          <component :is="card.icon" class="companion-card-icon" /><strong>{{ card.title }}</strong><span>{{ card.description }}</span>
        </button>
      </div>
      <el-form :ref="formRef" class="report-form" :model="form" :rules="rules" label-position="top">
        <el-form-item label="也可以自己选">
          <el-radio-group v-model="form.mode" @change="handleModeChange">
            <el-radio-button label="weekly">周总结</el-radio-button><el-radio-button label="mood">情绪趋势</el-radio-button>
            <el-radio-button label="themes">反复在意</el-radio-button><el-radio-button label="daily">当天</el-radio-button>
            <el-radio-button label="monthly">月度</el-radio-button><el-radio-button label="custom">自定义</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.mode === 'daily'" label="日期" prop="date">
          <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" />
        </el-form-item>
        <el-form-item v-else label="日期范围" prop="range">
          <el-date-picker v-model="form.range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
        <el-button type="primary" :icon="MagicStick" :loading="loading" @click="generate">让小记陪我看一看</el-button>
      </el-form>
      <el-empty v-if="!report" description="选一个方向，小记会从多篇日志里帮你看见最近的自己。" />
      <section v-else class="report-result">
        <div class="report-result-heading">
          <div><p class="eyebrow">{{ report.period }}</p><h2>{{ report.title }}</h2></div>
          <div class="button-row">
            <el-button type="primary" plain :icon="ChatDotRound" @click="openChat">和小记修改</el-button>
            <el-button v-if="lastReportSnapshot" plain :icon="RefreshLeft" :loading="applyLoading" @click="undoReportApply">撤销</el-button>
          </div>
        </div>
        <p class="report-summary">{{ report.summary }}</p>
        <div v-if="tagList.length" class="tag-row"><el-tag v-for="tag in tagList" :key="tag" effect="light">{{ tag }}</el-tag></div>
      </section>
    </section>
  </section>
  <AiReportChatDrawer
    v-model:visible="chatVisible" v-model:chat-input="chatInput" :apply-loading="applyLoading" :set-chat-body="setChatBody"
    :chat-loading="chatLoading" :chat-messages="chatMessages" :last-report-snapshot="lastReportSnapshot"
    :preview-report="previewReport" :preview-tag-list="previewTagList" :report="report" @apply="applyPreview"
    @send="sendChatMessage" @undo="undoReportApply"
  />
</template>
