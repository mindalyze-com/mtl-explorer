export function localDateBoundary(value: string, endExclusive: boolean): Date | undefined {
  if (!value) return undefined;
  const boundary = new Date(`${value}T00:00:00`);
  if (endExclusive) boundary.setDate(boundary.getDate() + 1);
  return boundary;
}
