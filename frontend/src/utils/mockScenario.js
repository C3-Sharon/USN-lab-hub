/**
 * W43 mock 状态注入（仅在 mock 构建中生效）
 *
 * 用途：契约 §11.3 要求完整覆盖 loading/empty/error/permission/success/conflict/immutable。
 * 真实后端无法稳定复现这些边界，故 mock 分支读取 `?mockState=` 返回对应状态，
 * 供三视口证据与人工验收使用；真实接口（VITE_USE_MOCK=false）完全忽略该参数。
 */

const STATES = Object.freeze(['loading', 'empty', 'error', 'permission', 'conflict', 'immutable'])

export function currentMockScenario() {
  if (typeof window === 'undefined') return ''
  const raw = new URLSearchParams(window.location.search).get('mockState') || ''
  return STATES.includes(raw) ? raw : ''
}

export function isMockScenario(state) {
  return currentMockScenario() === state
}

export function mockDelay(ms = 2200) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

export function mockReject(code, msg, reason, data = null) {
  return Promise.reject({ code, msg, reason, data })
}

/** 契约 §10.5：错误响应必须包含 traceId，前端错误态展示该值便于排查 */
export function mockTraceId(scope = 'w43') {
  return `mock-${scope}-${Date.now().toString(36)}`
}

export const MOCK_STATE_META = Object.freeze({
  loading: { label: '加载中', desc: '骨架屏与按钮局部 loading' },
  empty: { label: '空数据', desc: '无记录/无计划/无批次/无反馈' },
  error: { label: '加载失败', desc: 'reason 文案、traceId 与重试' },
  permission: { label: '无权限', desc: '隐藏写操作并显示只读原因' },
  conflict: { label: '版本冲突', desc: '409 VERSION_CONFLICT 提示刷新对比' },
  immutable: { label: '不可变', desc: '发布修订/完成批次/快照只读' }
})
