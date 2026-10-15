/**
 * MySQL DATETIME values returned by the business service do not carry an
 * offset. The database stores them in UTC, so add the missing UTC marker
 * before asking the browser to display them in the user's local timezone.
 */
export function parseServerDate(value: string): Date {
  const normalized = value.trim().replace(" ", "T");
  const hasTimezone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(normalized);
  return new Date(hasTimezone ? normalized : `${normalized}Z`);
}
