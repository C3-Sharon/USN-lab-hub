/**
 * 确定性默认封面
 * 基于项目 code 生成稳定的视觉占位，不依赖外部图片。
 * 规则：取 code 首字母 + 稳定色相（从 code 哈希推导）。
 */

const PALETTE = [
  { bg: '#e0f2fe', fg: '#0369a1' },
  { bg: '#fef3c7', fg: '#92400e' },
  { bg: '#fee2e2', fg: '#991b1b' },
  { bg: '#dcfce7', fg: '#166534' },
  { bg: '#f3e8ff', fg: '#6b21a8' },
  { bg: '#cffafe', fg: '#155e75' },
  { bg: '#ffedd5', fg: '#9a3412' },
  { bg: '#e2e8f0', fg: '#334155' }
]

function hashCode(str) {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i)
    hash |= 0
  }
  return Math.abs(hash)
}

export function projectCoverMeta(code) {
  const safe = (code || 'P').toUpperCase()
  const initial = safe.charAt(0)
  const idx = hashCode(safe) % PALETTE.length
  return {
    initial,
    bg: PALETTE[idx].bg,
    fg: PALETTE[idx].fg
  }
}
