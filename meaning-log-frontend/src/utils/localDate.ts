export const formatLocalDate = (date: Date): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export const getRecentLocalDateRange = (days: number, end = new Date()): [string, string] => {
  const start = new Date(end.getTime())
  start.setDate(start.getDate() - days + 1)
  return [formatLocalDate(start), formatLocalDate(end)]
}
