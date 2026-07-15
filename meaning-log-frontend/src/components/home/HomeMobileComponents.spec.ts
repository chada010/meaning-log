import { defineComponent } from 'vue'
import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { MeaningLog } from '../../api/logs'
import HomeMobileFilters from './HomeMobileFilters.vue'
import HomeMobileLogList from './HomeMobileLogList.vue'

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
})

const log: MeaningLog = {
  id: 7,
  title: '今天心情不错',
  content: '完成了一件拖了很久的事情。',
  logDate: '2026-07-13',
  aiTags: '生活,开心',
  favorite: false,
  images: [],
  createdAt: '2026-07-13T10:00:00',
  updatedAt: '2026-07-13T10:00:00',
}

describe('HomeMobileFilters', () => {
  it('使用唯一且仅在移动端显示的根节点', () => {
    const wrapper = shallowMount(HomeMobileFilters, {
      props: {
        favoriteOnly: false,
        keyword: '',
        selectedDate: '',
        selectedTag: '',
        tagOptions: [],
      },
    })

    expect(wrapper.classes()).toEqual(expect.arrayContaining(['mobile-filter-shell', 'mobile-only']))
  })
})

describe('HomeMobileLogList', () => {
  it('日志主体使用合法按钮内容，详情与收藏操作互不触发', async () => {
    const wrapper = shallowMount(HomeMobileLogList, {
      props: { logs: [log] },
      global: {
        stubs: {
          ElButton: ButtonStub,
          ElDropdown: { template: '<span><slot /></span>' },
          ElTag: { template: '<span><slot /></span>' },
        },
      },
    })

    const mainButton = wrapper.get('.mobile-log-card-main')
    expect(mainButton.find('div,h1,h2,h3,h4,h5,h6,p').exists()).toBe(false)

    await wrapper.get('.mobile-log-card-tools button').trigger('click')
    expect(wrapper.emitted('toggleFavorite')).toEqual([[log]])
    expect(wrapper.emitted('detail')).toBeUndefined()

    await mainButton.trigger('click')
    expect(wrapper.emitted('detail')).toEqual([[log.id]])
  })

  it('title 为 null 时显示统一占位文案', () => {
    const wrapper = shallowMount(HomeMobileLogList, {
      props: { logs: [{ ...log, title: null, aiTitle: null }] },
      global: {
        stubs: {
          ElButton: ButtonStub,
          ElDropdown: { template: '<span><slot /></span>' },
          ElTag: { template: '<span><slot /></span>' },
        },
      },
    })

    expect(wrapper.get('.mobile-log-title').text()).toBe('未命名的一天')
  })
})
