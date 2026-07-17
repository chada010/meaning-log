// @vitest-environment jsdom

import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useNotificationStore } from '../../stores/notificationStore'
import NotificationDrawer from './NotificationDrawer.vue'

const { routerPushMock } = vi.hoisted(() => ({ routerPushMock: vi.fn() }))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPushMock }),
}))

const DrawerStub = defineComponent({
  template: '<aside><slot /></aside>',
})

const IconStub = defineComponent({
  template: '<i><slot /></i>',
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('NotificationDrawer', () => {
  it('点击 Header 关闭按钮时关闭 Drawer', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useNotificationStore()
    store.drawerOpen = true
    const closeDrawer = vi.spyOn(store, 'closeDrawer')
    const wrapper = mount(NotificationDrawer, {
      global: {
        plugins: [pinia],
        stubs: {
          ElDrawer: DrawerStub,
          ElIcon: IconStub,
          ElSkeleton: true,
        },
      },
    })

    const closeButton = wrapper.get('button[aria-label="关闭通知"]')
    expect(closeButton.attributes('title')).toBe('关闭通知')

    await closeButton.trigger('click')

    expect(closeDrawer).toHaveBeenCalledOnce()
    expect(store.drawerOpen).toBe(false)
  })
})
