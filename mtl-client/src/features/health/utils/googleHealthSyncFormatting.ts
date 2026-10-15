export function formatWorkState(value?: string): string {
  const labels: Record<string, string> = {
    ACTIVE: 'In progress',
    WAITING: 'Waiting',
    SKIPPED: 'Skipped',
    PARKED: 'Parked',
    COMPLETED: 'Complete',
    COMPLETED_WITH_ERRORS: 'Complete with skipped ranges',
  };
  return value ? (labels[value] ?? value) : 'Waiting';
}
export function formatDataType(value?: string): string {
  if (!value) return '';
  const labels: Record<string, string> = {
    exercise: 'Exercise',
    'calories-in-heart-rate-zone': 'Heart-rate-zone calories',
  };
  if (labels[value]) return labels[value];
  return value
    .split('-')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function formatWindow(start?: Date, end?: Date): string {
  if (!(start instanceof Date) || !(end instanceof Date)) return '';
  return `${formatDate(start)}–${formatDate(end)}`;
}

export function formatRepresentation(value?: string): string {
  const labels: Record<string, string> = {
    RAW: 'Records',
    HEART_RATE_SAMPLE: 'Samples',
    RECONCILED_INTERVAL: 'Intervals',
    DAILY_ROLLUP: 'Daily summaries',
  };
  return value ? (labels[value] ?? formatDataType(value.toLowerCase().replaceAll('_', '-'))) : '';
}

export function formatDateTime(value: Date): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(value);
}
export function formatNativeTime(value: Date, domain?: string): string {
  if (domain === 'PHYSICAL' || !domain) return formatDateTime(value);
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeZone: 'UTC',
    ...(domain === 'CIVIL' ? { timeStyle: 'short' as const } : {}),
  }).format(value);
}
export function formatDate(value: Date): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(value);
}

export function formatCount(value: number): string {
  return new Intl.NumberFormat().format(value);
}
