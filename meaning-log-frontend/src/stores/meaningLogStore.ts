import { defineStore } from 'pinia'
import type { MeaningLog, MeaningLogForm } from '../types/meaningLog'

const storageKey = 'meaning-log-items'

const loadLogs = (): MeaningLog[] => {
  const raw = localStorage.getItem(storageKey)

  if (!raw) {
    return []
  }

  try {
    return JSON.parse(raw) as MeaningLog[]
  } catch {
    return []
  }
}

const saveLogs = (logs: MeaningLog[]) => {
  localStorage.setItem(storageKey, JSON.stringify(logs))
}

export const useMeaningLogStore = defineStore('meaningLog', {
  state: () => ({
    logs: loadLogs(),
  }),
  getters: {
    sortedLogs: (state) =>
      [...state.logs].sort((a, b) => b.logDate.localeCompare(a.logDate) || b.id - a.id),
  },
  actions: {
    createLog(form: MeaningLogForm) {
      const now = new Date().toISOString()
      const log: MeaningLog = {
        id: Date.now(),
        ...form,
        createdAt: now,
        updatedAt: now,
      }

      this.logs.unshift(log)
      saveLogs(this.logs)
      return log
    },
    updateLog(id: number, form: MeaningLogForm) {
      const target = this.logs.find((log) => log.id === id)

      if (!target) {
        return undefined
      }

      target.logDate = form.logDate
      target.title = form.title
      target.content = form.content
      target.updatedAt = new Date().toISOString()
      saveLogs(this.logs)
      return target
    },
    deleteLog(id: number) {
      this.logs = this.logs.filter((log) => log.id !== id)
      saveLogs(this.logs)
    },
    getLogById(id: number) {
      return this.logs.find((log) => log.id === id)
    },
  },
})
