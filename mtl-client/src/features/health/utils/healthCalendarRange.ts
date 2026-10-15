import { subMonths, subYears } from 'date-fns';
import { localDateBoundary } from './dateBoundary';

export type HealthPreset = '7D' | '30D' | '90D' | '6M' | '1Y' | 'THIS_MONTH' | 'PREVIOUS_MONTH' | 'THIS_YEAR';

export const HEALTH_DATE_SHORTCUTS: { id: HealthPreset; label: string }[] = [
  { id: '7D', label: 'Last 7 days' },
  { id: '30D', label: 'Last 30 days' },
  { id: '90D', label: 'Last 90 days' },
  { id: '1Y', label: 'Last 12 months' },
  { id: 'THIS_MONTH', label: 'This month' },
  { id: 'PREVIOUS_MONTH', label: 'Previous month' },
  { id: 'THIS_YEAR', label: 'This year' },
];

export function formatCalendarDate(value: Date): string {
  return `${String(value.getFullYear()).padStart(4, '0')}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`;
}

export function healthCalendarRange(range: HealthPreset, now = new Date()): { startDate: string; endDate: string } {
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  let start: Date;
  if (range === '6M') start = subMonths(end, 6);
  else if (range === '1Y') start = subYears(end, 1);
  else if (range === 'THIS_MONTH') start = new Date(now.getFullYear(), now.getMonth(), 1);
  else if (range === 'THIS_YEAR') start = new Date(now.getFullYear(), 0, 1);
  else if (range === 'PREVIOUS_MONTH') {
    start = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    end.setFullYear(now.getFullYear(), now.getMonth(), 1);
  } else {
    start = new Date(end);
    const days = { '7D': 7, '30D': 30, '90D': 90 }[range];
    start.setDate(start.getDate() - days);
  }
  return { startDate: formatCalendarDate(start), endDate: formatCalendarDate(end) };
}

export function customHealthCalendarRange(start: string, inclusiveEnd: string) {
  return { startDate: start, endDate: formatCalendarDate(localDateBoundary(inclusiveEnd, true)!) };
}
