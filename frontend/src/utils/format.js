export function formatMinutes(minutes) {
  const value = Number(minutes || 0)
  if (value < 60) {
    return `${value} 分钟`
  }
  const hours = Math.floor(value / 60)
  const remainMinutes = value % 60
  return remainMinutes ? `${hours} 小时 ${remainMinutes} 分钟` : `${hours} 小时`
}

export function formatHours(hours) {
  return `${Number(hours || 0).toFixed(1)} h`
}

export function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ')
}

export function buildQueryWithoutEmpty(query) {
  return Object.fromEntries(
    Object.entries(query).filter(([, value]) => value !== '' && value !== null && typeof value !== 'undefined')
  )
}
