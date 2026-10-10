/**
 * 响应取值帮助函数
 *
 * 背景：
 *   1) utils/request.js 的响应拦截器在 code === 200 时返回 payload.data，
 *      失败时 reject(payload)。所以 axios 拿到的 res 已经是 payload.data。
 *   2) 但部分页面（特别是 IoT 列表 / 详情）之前误用 `res.data.xxx`，相当于
 *      `payload.data.data.xxx`，结果为 undefined。
 *   3) Mock 数据的分页结构契约是 `{ records, total, size, current, pages }`
 *      （docs/agent-guides/04_API_CONTRACT.md §2），不是 `list`。
 *
 * 目的：所有页面统一通过本文件取值，避免重复写 `res.data || res` 兜底。
 */

export function getListData(res) {
  if (!res) return { records: [], total: 0 }
  const payload = res.records !== undefined || res.list !== undefined ? res : (res.data || res)
  return {
    records: payload.records ?? payload.list ?? [],
    total: payload.total ?? (payload.records?.length ?? payload.list?.length ?? 0)
  }
}

export function getDetailData(res) {
  if (!res) return {}
  if (res.data && res.data.id !== undefined) return res.data
  return res
}

export function getMetricsData(res) {
  const payload = res?.data || res
  return payload?.metrics || []
}

export function getHistoryPoints(res) {
  const payload = res?.data || res
  return payload?.points || []
}
