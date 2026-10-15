export type GoogleHealthSetupProgress = {
  activeStep: number;
  completedSteps: number[];
  lastProposedRedirectUri: string;
};

const STORAGE_KEY = 'mtl.google-health-setup.v4';
const V3_STORAGE_KEY = 'mtl.google-health-setup.v3';
const V2_STORAGE_KEY = 'mtl.google-health-setup.v2';
const FIRST_STEP = 0;
const LAST_STEP = 7;
const TEST_USER_STEP = 3;
const REDIRECT_STEP = 4;
const V3_COMPLETED_STEP_MAP = new Map([
  [0, 0],
  [1, 1],
  [2, 2],
  [3, 5],
  [4, 4],
  [5, 6],
  [6, 7],
]);
const V2_COMPLETED_STEP_MAP = new Map([
  [0, 0],
  [1, 1],
  [3, 2],
  [4, 5],
  [5, 4],
  [6, 6],
  [7, 7],
]);

export function loadGoogleHealthSetupProgress(storage: Storage): GoogleHealthSetupProgress {
  try {
    const stored = storage.getItem(STORAGE_KEY);
    if (stored !== null) return parseProgress(stored);

    const v3 = storage.getItem(V3_STORAGE_KEY);
    if (v3 !== null) return migrateProgress(v3, V3_COMPLETED_STEP_MAP);

    const v2 = storage.getItem(V2_STORAGE_KEY);
    if (v2 !== null) return migrateProgress(v2, V2_COMPLETED_STEP_MAP);
    return emptyProgress();
  } catch {
    return emptyProgress();
  }
}

export function saveGoogleHealthSetupProgress(storage: Storage, progress: GoogleHealthSetupProgress): void {
  storage.setItem(STORAGE_KEY, JSON.stringify(progress));
  storage.removeItem(V3_STORAGE_KEY);
  storage.removeItem(V2_STORAGE_KEY);
}

export function reconcileGoogleHealthSetupRedirect(
  progress: GoogleHealthSetupProgress,
  proposedRedirectUri: string
): GoogleHealthSetupProgress {
  if (!progress.lastProposedRedirectUri || progress.lastProposedRedirectUri === proposedRedirectUri) {
    return { ...progress, lastProposedRedirectUri: proposedRedirectUri };
  }
  return {
    ...progress,
    completedSteps: progress.completedSteps.filter((step) => step !== REDIRECT_STEP),
    lastProposedRedirectUri: proposedRedirectUri,
  };
}

function emptyProgress(): GoogleHealthSetupProgress {
  return { activeStep: FIRST_STEP, completedSteps: [], lastProposedRedirectUri: '' };
}

function parseProgress(value: string): GoogleHealthSetupProgress {
  const stored = JSON.parse(value) as Partial<GoogleHealthSetupProgress>;
  return {
    activeStep: clampStep(stored.activeStep),
    completedSteps: Array.isArray(stored.completedSteps)
      ? [...new Set(stored.completedSteps.filter(isStep))].sort((left, right) => left - right)
      : [],
    lastProposedRedirectUri: typeof stored.lastProposedRedirectUri === 'string' ? stored.lastProposedRedirectUri : '',
  };
}

function migrateProgress(value: string, completedStepMap: Map<number, number>): GoogleHealthSetupProgress {
  const stored = JSON.parse(value) as Partial<GoogleHealthSetupProgress>;
  return {
    activeStep: migratedActiveStep(stored.activeStep),
    completedSteps: Array.isArray(stored.completedSteps)
      ? [...new Set(stored.completedSteps.map((step) => completedStepMap.get(step)).filter(isStep))].sort(
          (left, right) => left - right
        )
      : [],
    lastProposedRedirectUri: typeof stored.lastProposedRedirectUri === 'string' ? stored.lastProposedRedirectUri : '',
  };
}

function migratedActiveStep(value: unknown): number {
  if (value === FIRST_STEP || value === 1) return value;
  return TEST_USER_STEP;
}

function clampStep(value: unknown): number {
  return typeof value === 'number' && Number.isInteger(value) ? Math.max(FIRST_STEP, Math.min(LAST_STEP, value)) : 0;
}

function isStep(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= FIRST_STEP && value <= LAST_STEP;
}
