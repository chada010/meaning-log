<script setup lang="ts">
import type { Component } from 'vue'
import type { ReportMode } from '../../composables/useAiReport'

interface CompanionCard {
  mode: ReportMode
  icon: Component
  title: string
  description: string
  days: number
}

defineProps<{
  cards: CompanionCard[]
  activeMode: ReportMode
}>()

const emit = defineEmits<{
  select: [mode: ReportMode, days: number]
}>()
</script>

<template>
  <div class="companion-card-grid">
    <button
      v-for="card in cards"
      :key="card.mode"
      class="companion-card"
      :class="{ active: activeMode === card.mode }"
      type="button"
      @click="emit('select', card.mode, card.days)"
    >
      <component :is="card.icon" class="companion-card-icon" />
      <strong>{{ card.title }}</strong>
      <span>{{ card.description }}</span>
    </button>
  </div>
</template>
