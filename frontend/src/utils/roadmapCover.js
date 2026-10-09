/**
 * 学习路线确定性默认封面
 *
 * 规则：取路线标题首字 + 稳定色相（标题哈希推导）。
 * 调色板限定紫色/靛蓝色系（UI_SPEC §4：紫色仅用于知识/学习小面积识别色），
 * 与项目封面（projectCover.js 多彩色板）形成视觉区分。
 */

const PALETTE = [
  { bg: '#f3e8ff', fg: '#6b21a8' },
  { bg: '#ede9fe', fg: '#5b21b6' },
  { bg: '#e0e7ff', fg: '#4338ca' },
  { bg: '#fae8ff', fg: '#86198f' },
  { bg: '#f5f3ff', fg: '#6d28d9' }
]

function hashCode(str) {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i)
    hash |= 0
  }
  return Math.abs(hash)
}

export function roadmapCoverMeta(title) {
  const safe = String(title || '学').trim() || '学'
  const initial = safe.charAt(0)
  const idx = hashCode(safe) % PALETTE.length
  return {
    initial,
    bg: PALETTE[idx].bg,
    fg: PALETTE[idx].fg
  }
}