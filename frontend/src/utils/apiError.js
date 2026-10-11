import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * API 错误归一化
 *
 * 背景：错误有三种形态，页面需要统一处理
 *   1) request.js 拦截器 reject(payload) → { code, msg, reason, data }
 *   2) HTTP 层错误（axios error） → err.response.data = { code, msg, reason }
 *   3) mock 分支直接 reject({ code, msg, reason })
 *
 * 契约来源：docs/contracts/PROJECT_WORKSPACE_API.md §6 / §10
 */

export function normalizeApiError(err) {
  if (!err) return { code: null, reason: null, msg: '', data: null }
  const payload = err.response?.data || err
  return {
    code: payload?.code ?? err.response?.status ?? null,
    reason: payload?.reason ?? null,
    msg: payload?.msg || err.message || '请求失败',
    data: payload?.data ?? null
  }
}

export function isReason(err, reason) {
  return normalizeApiError(err).reason === reason
}

export function isVersionConflict(err) {
  return isReason(err, 'VERSION_CONFLICT')
}

export function isProjectNotFound(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 404 || reason === 'PROJECT_NOT_FOUND' || reason === 'PROJECT_ACCESS_DENIED'
}

/**
 * mock 分支的错误不经 axios 拦截器，需要页面兜底提示；
 * 真实模式下 axios 错误已由 request.js 统一提示，此处跳过避免重复 toast。
 */
export function notifyApiError(err) {
  if (err?.response) return
  const { reason, msg } = normalizeApiError(err)
  if (reason === 'VERSION_CONFLICT') return
  if (msg) ElMessage.error(msg)
}

// ========== 学习实验域错误码（契约 §7） ==========

const LEARNING_NOT_FOUND_REASONS = [
  'LEARNING_ROADMAP_NOT_FOUND',
  'LEARNING_STAGE_NOT_FOUND',
  'LEARNING_UNIT_NOT_FOUND'
]

/** 路线/阶段/单元不存在或不可见（404） */
export function isLearningNotFound(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 404 || LEARNING_NOT_FOUND_REASONS.includes(reason)
}

/** 路线已归档（409） */
export function isLearningArchived(err) {
  return isReason(err, 'LEARNING_ARCHIVED')
}

/** 尚未开始学习该路线（400） */
export function isLearningNotEnrolled(err) {
  return isReason(err, 'LEARNING_NOT_ENROLLED')
}

/** 无学习路线管理权限 / GUEST 不可访问（403） */
export function isLearningDenied(err) {
  const { code, reason } = normalizeApiError(err)
  return code === 403 || reason === 'LEARNING_OPERATION_DENIED' || reason === 'ACCESS_DENIED'
}

/** 空路线无法标记完成（409） */
export function isLearningEmptyRoadmap(err) {
  return isReason(err, 'LEARNING_EMPTY_ROADMAP')
}

/** 非法路线状态转换（409） */
export function isLearningInvalidTransition(err) {
  return isReason(err, 'LEARNING_INVALID_TRANSITION')
}

/**
 * 乐观锁冲突统一提示（契约 §9.6 / §10.3）。
 * 拦截器对 VERSION_CONFLICT 静默，避免通用 toast；此处由页面给出可操作提示。
 * 用户点击「刷新数据」后回调 onRefresh 重新拉取最新数据。
 */
export function promptVersionConflict(onRefresh) {
  return ElMessageBox.confirm('该任务已被他人更新，请刷新后重试。', '版本冲突（409）', {
    confirmButtonText: '刷新数据',
    cancelButtonText: '关闭',
    type: 'warning'
  })
    .then(() => {
      if (typeof onRefresh === 'function') onRefresh()
    })
    .catch(() => {})
}