// @vitest-environment jsdom

import { flushPromises, shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { getLogDetail, type MeaningLog } from '../api/logs'
import LogForm from '../components/LogForm.vue'
import LogEditView from './LogEditView.vue'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

vi.mock('../api/logs', () => ({
  getLogDetail: vi.fn(),
  updateLog: vi.fn(),
}))

const nullableTitleLog: MeaningLog = {
  id: 1,
  title: null,
  content: '正文',
  logDate: '2026-07-15',
  favorite: false,
  images: [],
  createdAt: '2026-07-15T00:30:00',
  updatedAt: '2026-07-15T00:30:00',
}

describe('LogEditView', () => {
  it('title 为 null 时转换成可选请求字段并正常渲染表单', async () => {
    type DetailResponse = Awaited<ReturnType<typeof getLogDetail>>
    vi.mocked(getLogDetail).mockResolvedValue({ data: nullableTitleLog } as DetailResponse)

    const wrapper = shallowMount(LogEditView, { props: { id: 1 } })
    await flushPromises()

    const form = wrapper.getComponent(LogForm)
    expect(form.props('initialValue')).toMatchObject({ title: undefined, content: '正文' })
  })
})
