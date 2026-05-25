/* eslint-disable @typescript-eslint/no-explicit-any -- JS controller shim; migrate with useMainMapController.js. */

export function useMainMapController(
  props: { fromLogin?: boolean },
  emit: any,
  toast: { add: (options: { severity: string; summary: string; detail?: string; life?: number }) => void },
  templateRefs: Record<string, unknown>
): any;
