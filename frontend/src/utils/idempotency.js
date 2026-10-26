let counter = 0

/** 生成幂等键：时间戳 + 自增序号 + 随机串，满足同一操作重试可复用的格式要求 */
export function createIdempotencyKey(prefix = 'w43') {
  const ts = Date.now().toString(36)
  counter = (counter + 1) % 1_000_000
  const rand = Math.random().toString(36).slice(2, 10)
  return `${prefix}-${ts}-${counter.toString(36)}-${rand}`
}

export function idempotencyHeaders(key) {
  return key ? { 'Idempotency-Key': key } : {}
}

function normalize(value) {
  if (Array.isArray(value)) return value.map(normalize)
  if (value && typeof value === 'object') {
    return Object.keys(value)
      .sort()
      .reduce((acc, key) => {
        acc[key] = normalize(value[key])
        return acc
      }, {})
  }
  return value
}

/** 请求摘要：用于判断同 key 是否对应同一请求体 */
export function requestDigest(payload) {
  return JSON.stringify(normalize(payload ?? {}))
}
