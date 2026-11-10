/**
 * 全局时间格式化：ISO（含 T）转 "YYYY-MM-DD HH:mm:ss"，空值统一返回 '-'。
 * 后端 LocalDateTime 由 Jackson 序列化为 ISO 字符串，所有展示时间字段一律走本函数。
 */
export function fmtDateTime(value?: string | null): string {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}
