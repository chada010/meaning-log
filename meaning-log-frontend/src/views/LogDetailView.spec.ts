// @vitest-environment jsdom

import { shallowMount } from '@vue/test-utils'
import { computed, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { MeaningLog } from '../api/logs'
import LogDetailView from './LogDetailView.vue'

const { useLogDetailMock } = vi.hoisted(() => ({ useLogDetailMock: vi.fn() }))

vi.mock('../composables/useLogDetail', () => ({ useLogDetail: useLogDetailMock }))

const nullableTitleLog: MeaningLog = {
  id: 1,
  title: null,
  content: '正文',
  logDate: '2026-07-15',
  aiTitle: null,
  aiSummary: null,
  aiTags: null,
  favorite: false,
  images: [],
  createdAt: '2026-07-15T00:30:00',
  updatedAt: '2026-07-15T00:30:00',
}

beforeEach(() => {
  useLogDetailMock.mockReturnValue({
    aiLoading: ref(false),
    applyLoading: ref(false),
    applyPreview: vi.fn(),
    chatInput: ref(''),
    chatLoading: ref(false),
    chatMessages: ref([]),
    chatVisible: ref(false),
    formatTime: vi.fn((value: string) => value),
    generateGentleTitle: vi.fn(),
    goEdit: vi.fn(),
    goLog: vi.fn(),
    handleDelete: vi.fn(),
    handleGenerateAi: vi.fn(),
    lastAiSnapshot: ref(),
    loading: ref(false),
    log: ref(nullableTitleLog),
    navigation: ref(),
    openChat: vi.fn(),
    organizeMessyLog: vi.fn(),
    previewImage: ref(),
    previewSuggestion: ref(),
    previewTagList: computed(() => []),
    renderedLogContent: computed(() => '<p>正文</p>'),
    router: { push: vi.fn() },
    sendChatMessage: vi.fn(),
    setChatBody: vi.fn(),
    streamingText: ref(''),
    tagList: computed(() => []),
    toggleFavorite: vi.fn(),
    undoAiApply: vi.fn(),
  })
})

describe('LogDetailView', () => {
  it('title 为 null 时不报错并显示统一占位文案', () => {
    const wrapper = shallowMount(LogDetailView, { props: { id: 1 } })

    expect(wrapper.get('.page-heading h2').text()).toBe('未命名的一天')
  })
})
