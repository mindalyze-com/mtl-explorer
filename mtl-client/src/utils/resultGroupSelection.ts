import type { FilterResultGroupKey } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupKey';
import type { FilterResultGroupSelection } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/FilterResultGroupSelection';

export function availableResultGroupKeys(
  summaries: ReadonlyArray<{ key?: FilterResultGroupKey | null }>
): FilterResultGroupKey[] {
  return summaries.map((summary) => summary.key).filter((key): key is FilterResultGroupKey => key != null);
}

export function resultGroupKeyId(key: FilterResultGroupKey): string {
  return key.value === null ? 'ungrouped' : `grouped:${JSON.stringify(key.value)}`;
}

export function cloneResultGroupKey(key: FilterResultGroupKey): FilterResultGroupKey {
  return { value: key.value };
}

export function selectedResultGroupIds(selection?: FilterResultGroupSelection | null): Set<string> | null {
  if (selection == null) return null;
  return new Set((selection.includedGroups ?? []).map(resultGroupKeyId));
}

export function isResultGroupSelected(
  key: FilterResultGroupKey,
  selection?: FilterResultGroupSelection | null
): boolean {
  const selectedIds = selectedResultGroupIds(selection);
  return selectedIds == null || selectedIds.has(resultGroupKeyId(key));
}

export function updateResultGroupSelection(
  selection: FilterResultGroupSelection | null | undefined,
  availableKeys: FilterResultGroupKey[],
  changedKeys: FilterResultGroupKey[],
  checked: boolean
): FilterResultGroupSelection | undefined {
  const keysById = new Map<string, FilterResultGroupKey>();
  for (const key of availableKeys) keysById.set(resultGroupKeyId(key), cloneResultGroupKey(key));
  for (const key of selection?.includedGroups ?? []) keysById.set(resultGroupKeyId(key), cloneResultGroupKey(key));

  const selectedIds =
    selection == null
      ? new Set(availableKeys.map(resultGroupKeyId))
      : new Set((selection.includedGroups ?? []).map(resultGroupKeyId));
  for (const key of changedKeys) {
    const id = resultGroupKeyId(key);
    keysById.set(id, cloneResultGroupKey(key));
    if (checked) selectedIds.add(id);
    else selectedIds.delete(id);
  }

  return canonicalResultGroupSelection(selectedIds, keysById, availableKeys);
}

export function selectNoResultGroups(): FilterResultGroupSelection {
  return { includedGroups: [] };
}

export function selectAllAvailableResultGroups(
  selection: FilterResultGroupSelection | null | undefined,
  availableKeys: FilterResultGroupKey[]
): FilterResultGroupSelection | undefined {
  return updateResultGroupSelection(selection, availableKeys, availableKeys, true);
}

export function unavailableSelectedResultGroups(
  selection: FilterResultGroupSelection | null | undefined,
  availableKeys: FilterResultGroupKey[]
): FilterResultGroupKey[] {
  if (selection == null) return [];
  const availableIds = new Set(availableKeys.map(resultGroupKeyId));
  return (selection.includedGroups ?? [])
    .filter((key) => !availableIds.has(resultGroupKeyId(key)))
    .map(cloneResultGroupKey);
}

function canonicalResultGroupSelection(
  selectedIds: Set<string>,
  keysById: Map<string, FilterResultGroupKey>,
  availableKeys: FilterResultGroupKey[]
): FilterResultGroupSelection | undefined {
  // `undefined` is the durable "All categories" state: groups discovered later
  // are included automatically. Normalize an exact match with the complete
  // available catalog, while preserving explicit selections that also contain
  // a currently unavailable group.
  const availableIds = new Set(availableKeys.map(resultGroupKeyId));
  const selectsExactlyAllAvailable =
    availableIds.size > 0 &&
    selectedIds.size === availableIds.size &&
    Array.from(availableIds).every((id) => selectedIds.has(id));
  if (selectsExactlyAllAvailable) return undefined;

  return {
    includedGroups: Array.from(selectedIds)
      .map((id) => keysById.get(id))
      .filter((key): key is FilterResultGroupKey => key != null)
      .map(cloneResultGroupKey),
  };
}
